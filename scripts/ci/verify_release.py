"""Validate AGP's actual APK outputs before uploading/publishing anything."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

PACKAGE = 'com.lochanpthakke.empee3'
# Existing installed certificate: a new, incompatible signing key must fail verification.
CERTIFICATE = '5bb0fc457b96d9fb6cbc606009de44d633bee95b58fec2dc633a7a4eb54a9ce5'


def run(*args):
    return subprocess.check_output([str(x) for x in args], text=True, stderr=subprocess.STDOUT)


def validate_badging(text, version, code):
    expected = f"package: name='{PACKAGE}' versionCode='{code}' versionName='{version}'"
    if expected not in text:
        raise ValueError('APK application ID or version does not match release configuration')
    if "application-label:'erhu'" not in text:
        raise ValueError('APK display name is not erhu')
    if 'application-debuggable' in text or 'testOnly' in text:
        raise ValueError('A debug/test APK cannot be released')
    if "native-code: 'arm64-v8a'" not in text:
        raise ValueError('Expected ARM64 release packaging')


def main():
    root = Path(__file__).resolve().parents[2]
    build = (root / 'app/build.gradle.kts').read_text(encoding='utf-8')
    version = re.search(r'versionName = "([^"\n]+)"', build).group(1)
    code = int(re.search(r'versionCode = (\d+)', build).group(1))
    tag = os.environ.get('GITHUB_REF', '')
    if tag.startswith('refs/tags/') and tag != f'refs/tags/erhu-v{version}':
        raise ValueError('Release tag must match the APK version')
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/36.0.0'
    metadata = []
    for path in (root / 'app/build/outputs/apk').rglob('output-metadata.json'):
        data = json.loads(path.read_text())
        if data.get('variantName') == 'release':
            metadata.append((path, data))
    if len(metadata) != 1:
        raise ValueError('Expected exactly one release APK metadata file')
    path, data = metadata[0]
    if data.get('applicationId') != PACKAGE or not data.get('elements'):
        raise ValueError('Invalid release output metadata')
    mapping = root / 'app/build/outputs/mapping/release/mapping.txt'
    if not mapping.is_file() or not mapping.stat().st_size:
        raise ValueError('R8 release mapping is missing; expected a minified build')
    output = root / 'dist'
    output.mkdir(exist_ok=True)
    for item in data['elements']:
        apk = (path.parent / item['outputFile']).resolve()
        if apk.parent != path.parent.resolve() or not apk.is_file() or not apk.stat().st_size:
            raise ValueError('Release APK is missing, empty or outside its output directory')
        if item['versionName'] != version or item['versionCode'] != code:
            raise ValueError('Release metadata version mismatch')
        validate_badging(run(tools / 'aapt', 'dump', 'badging', apk), version, code)
        manifest = run(tools / 'aapt', 'dump', 'xmltree', apk, 'AndroidManifest.xml')
        if re.search(r'android:(?:testOnly|debuggable)[^\n]*0xffffffff', manifest):
            raise ValueError('Debug or test-only manifest flags are enabled')
        certificate = run(tools / 'apksigner', 'verify', '--verbose', '--print-certs', apk)
        actual = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)', certificate)
        if actual is None or actual.group(1).lower() != CERTIFICATE:
            raise ValueError('Signing certificate is incompatible with existing erhu installations')
        with zipfile.ZipFile(apk) as archive:
            if any(b'Landroidx/metrics/performance/JankStats;' in archive.read(name)
                   for name in archive.namelist() if re.fullmatch(r'classes\d*\.dex', name)):
                raise ValueError('Debug frame diagnostics were packaged into the release')
        target = output / f'erhu-{version}-arm64-v8a.apk'
        shutil.copy2(apk, target)
        digest = hashlib.file_digest(target.open('rb'), 'sha256').hexdigest()
        (output / (target.name + '.sha256')).write_text(f'{digest}  {target.name}\n')
        print(f'Verified erhu {version} ({code}), signed with the existing compatible certificate.')
    (output / 'verification.json').write_text(json.dumps({'applicationId': PACKAGE, 'versionName': version,
        'versionCode': code, 'certificateSha256': CERTIFICATE, 'debuggable': False,
        'commit': os.environ.get('GITHUB_SHA')}, indent=2) + '\n')
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a') as stream:
            stream.write(f'version={version}\n')


if __name__ == '__main__':
    main()
