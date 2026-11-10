"""Verify a CI-only, debug-signed 1.0.18 candidate and its matching recipient kit."""
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "com.lochanpthakke.empee3"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def run(*args):
    return subprocess.check_output([str(a) for a in args], text=True, stderr=subprocess.STDOUT)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def defined_classes(data):
    require(data[:4] == b"dex\n", "Invalid DEX magic")
    string_count, string_offset = struct.unpack_from("<II", data, 56)
    type_count, type_offset = struct.unpack_from("<II", data, 64)
    class_count, class_offset = struct.unpack_from("<II", data, 96)
    strings = []
    for index in range(string_count):
        offset = struct.unpack_from("<I", data, string_offset + index * 4)[0]
        while data[offset] & 0x80:
            offset += 1
        offset += 1
        end = data.index(b"\0", offset)
        strings.append(data[offset:end].decode("utf-8", errors="replace"))
    types = [strings[struct.unpack_from("<I", data, type_offset + index * 4)[0]]
             for index in range(type_count)]
    return {types[struct.unpack_from("<I", data, class_offset + index * 32)[0]]
            for index in range(class_count)}


def main():
    require(not (ROOT / "keystore.properties").exists(), "Production signing configuration must not be present")
    tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/36.0.0"
    metadata = [(p, json.loads(p.read_text())) for p in
                (ROOT / "app/build/outputs/apk").rglob("output-metadata.json")]
    metadata = [(p, d) for p, d in metadata if d.get("variantName") == "release"]
    require(len(metadata) == 1, "Expected one release-variant candidate output")
    path, data = metadata[0]
    require(data["applicationId"] == PACKAGE and len(data["elements"]) == 1, "Unexpected candidate APK identity/count")
    element = data["elements"][0]
    require(element["versionName"] == "1.0.18" and element["versionCode"] == 19, "Wrong candidate version")
    apk = (path.parent / element["outputFile"]).resolve()
    require(apk.parent == path.parent.resolve() and apk.is_file() and apk.stat().st_size > 0, "Invalid APK output")
    badging = run(tools / "aapt", "dump", "badging", apk)
    require(f"package: name='{PACKAGE}' versionCode='19' versionName='1.0.18'" in badging, "APK manifest identity mismatch")
    require("application-label:'erhu'" in badging and "native-code: 'arm64-v8a'" in badging, "Wrong app label/ABI")
    require("application-debuggable" not in badging, "Expected optimized release variant, signed with a disposable debug key")
    cert_text = run(tools / "apksigner", "verify", "--verbose", "--print-certs", apk)
    cert = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)", cert_text)
    require(cert is not None, "APK signature is missing")
    key_der = subprocess.check_output([
        "keytool", "-exportcert", "-keystore", str(Path.home() / ".android/debug.keystore"),
        "-storepass", "android", "-alias", "androiddebugkey",
    ])
    require(cert.group(1).lower() == digest(key_der), "APK was not signed with this job's disposable key")
    for module in ("app", "core/network"):
        generated = list((ROOT / module / "build/generated").rglob("BuildConfig.java"))
        require(any(re.search(r"PROPRIETARY_CANDIDATE\s*=\s*true\s*;", p.read_text())
                    for p in generated if "release" in p.parts), f"Candidate configuration not true for {module}")
    dependencies = (ROOT / "candidate-dependencies.txt").read_text()
    require("com.google.mediapipe:" not in dependencies and "net.jthink:jaudiotagger:" not in dependencies,
            "Excluded upstream dependencies remain in release runtime graph")
    require("project :third-party:jaudiotagger" in dependencies, "Filtered LGPL source module is missing")
    excluded_mp4 = {"Mp4AtomTree", "Mp4TagWriter", "Mp4InfoReader", "Mp4FileReader", "Mp4FileWriter"}
    classes = set()
    with zipfile.ZipFile(apk) as packaged:
        names = packaged.namelist()
        for name in names:
            if re.fullmatch(r"classes\d*\.dex", name):
                classes.update(defined_classes(packaged.read(name)))
        require(not any(c.startswith(("Lorg/jaudiotagger/utils/tree/", "Lcom/google/mediapipe/")) for c in classes),
                "Excluded Oracle-dependent/MediaPipe class definitions are present")
        require(not any(f"Lorg/jaudiotagger/audio/mp4/{name};" in classes for name in excluded_mp4),
                "Excluded MP4 I/O definitions are present")
        require("Lorg/jaudiotagger/audio/AudioFileIO;" in classes, "Retained LGPL audio component is missing")
        require(not any("mediapipe" in name.lower() and name.endswith(".so") for name in names),
                "Excluded MediaPipe native payload is present")
        require(any(name.endswith("libandroidx.graphics.path.so") for name in names), "Expected AndroidX graphics native missing")
        required_notices = []
        assets = ROOT / "app/src/main/assets"
        for notice in assets.rglob("*"):
            if notice.is_file():
                name = "assets/" + notice.relative_to(assets).as_posix()
                require(name in names and packaged.read(name) == notice.read_bytes(), f"Missing/changed bundled notice: {name}")
                required_notices.append(name)
        replacements = {(p.parent.name, p.stem) for p in (ROOT / "app/src/proprietaryCandidate/res").rglob("*.png")}
        excluded_images = {digest(p.read_bytes()) for p in (ROOT / "app/src/main/res").rglob("*")
                           if p.is_file() and (p.parent.name, p.stem) in replacements}
        require(not any(digest(packaged.read(n)) in excluded_images for n in names if not n.endswith("/")),
                "An excluded original image payload remains in the APK")
    source = ROOT / "build/proprietary-recipient/erhu-1.0.18-corresponding-source.zip"
    require(source.is_file() and source.stat().st_size > 0, "Recipient source ZIP missing")
    with zipfile.ZipFile(source) as kit:
        manifest = json.loads(kit.read("RECIPIENT_SOURCE_MANIFEST.json"))
        for entry in manifest:
            require(digest(kit.read(entry["path"])) == entry["sha256"], "Recipient manifest mismatch")
            require(kit.read(entry["path"]) == (ROOT / entry["path"]).read_bytes(), "Recipient source differs from build checkout")
        for required in ("gradlew", "gradle/wrapper/gradle-wrapper.jar", "APK_RELEASE_READINESS.md",
                         "third-party/jaudiotagger/LICENSE-LGPL-2.1.txt", "third-party/jaudiotagger/MODIFICATIONS.json"):
            require(required in kit.namelist(), f"Missing recipient build material: {required}")
        fork = json.loads(kit.read("third-party/jaudiotagger/MODIFICATIONS.json"))
        for entry in fork["retained"]:
            folder = "java" if entry["path"].endswith(".java") else "resources"
            name = f"third-party/jaudiotagger/src/main/{folder}/{entry['path']}"
            require(digest(kit.read(name)) == entry["sha256"], "Modified LGPL source hash mismatch")
        require(not any("/utils/tree/" in n for n in kit.namelist()), "Excluded tree source leaked into recipient kit")
    output = ROOT / "dist/proprietary-candidate"
    output.mkdir(parents=True, exist_ok=True)
    target = output / "erhu-1.0.18-arm64-v8a-debug-signed-candidate.apk"
    shutil.copy2(apk, target)
    shutil.copy2(source, output / source.name)
    shutil.copy2(ROOT / "APK_RELEASE_READINESS.md", output / "APK_RELEASE_READINESS.md")
    verification = {
        "applicationId": PACKAGE, "versionName": "1.0.18", "versionCode": 19,
        "proprietaryCandidate": True, "signing": "disposable-debug-key-NOT-production-update-compatible",
        "certificateSha256": cert.group(1).lower(), "apkSha256": digest(target.read_bytes()),
        "sourceSha256": digest(source.read_bytes()), "commit": os.environ.get("GITHUB_SHA"),
        "runUrl": f"https://github.com/{os.environ.get('GITHUB_REPOSITORY')}/actions/runs/{os.environ.get('GITHUB_RUN_ID')}",
        "definedClassCount": len(classes), "bundledNotices": required_notices,
        "oracleAndMediaPipeExcluded": True, "legalClearance": False,
    }
    (output / "verification.json").write_text(json.dumps(verification, indent=2) + "\n")
    (output / "SHA256SUMS").write_text("".join(f"{digest(p.read_bytes())}  {p.name}\n"
                                              for p in sorted(output.iterdir()) if p.suffix in {".apk", ".zip"}))
    (output / "BUILD_REPORT.md").write_text(
        "# Erhu 1.0.18 candidate build\n\n"
        f"Source: `{verification['commit']}`\n\nRun: {verification['runUrl']}\n\n"
        "Optimized ARM64 release variant, signed with this job's disposable debug key. "
        "Not compatible with production-signed in-place updates. No production key was used.\n\n"
        "Targeted metadata/provider-gate tests and Android release lint passed. APK identity, "
        "candidate flags, signature, class/native exclusions, original-image exclusions, "
        "bundled notice bytes and matching recipient sources were verified.\n\n"
        "This is a build candidate, not proprietary distribution clearance. "
        "Modified-library Android runtime/rebuild validation and remaining legal/native rights "
        "review in APK_RELEASE_READINESS.md are not established by compilation.\n"
    )
    print(json.dumps({"apk": target.name, "source": source.name, "version": "1.0.18 (19)",
                      "signing": verification["signing"], "verified": True}, indent=2))


if __name__ == "__main__":
    main()
