"""Prepare signing from step-scoped Actions secrets; never print secret contents."""
import base64
import os
from pathlib import Path
import sys


def escaped(value):
    return ''.join({"\\": "\\\\", "\n": "\\n", "\r": "\\r", "\t": "\\t", " ": "\\ ", "=": "\\=", ":": "\\:"}.get(c, c) for c in value)


def main():
    root = Path(os.environ['GITHUB_WORKSPACE']).resolve()
    key = Path(os.environ['RUNNER_TEMP']).resolve() / 'erhu-release.keystore'
    config = root / 'keystore.properties'
    if '--cleanup' in sys.argv:
        key.unlink(missing_ok=True)
        config.unlink(missing_ok=True)
        return
    names = ['ERHU_KEYSTORE_BASE64', 'ERHU_KEYSTORE_PASSWORD', 'ERHU_KEY_ALIAS', 'ERHU_KEY_PASSWORD']
    missing = [name for name in names if not os.environ.get(name)]
    if missing:
        raise SystemExit('Missing repository Actions secrets: ' + ', '.join(missing))
    if key.exists() or config.exists():
        raise SystemExit('Refusing to overwrite an existing signing configuration')
    try:
        binary = base64.b64decode(os.environ['ERHU_KEYSTORE_BASE64'], validate=True)
    except ValueError:
        raise SystemExit('ERHU_KEYSTORE_BASE64 is not valid base64') from None
    if not binary:
        raise SystemExit('Signing keystore is empty')
    key.write_bytes(binary)
    key.chmod(0o600)
    values = {'storeFile': str(key), 'storePassword': os.environ[names[1]],
              'keyAlias': os.environ[names[2]], 'keyPassword': os.environ[names[3]]}
    config.write_text(''.join(f'{name}={escaped(value)}\n' for name, value in values.items()), encoding='utf-8')
    config.chmod(0o600)
    print('Signing configuration prepared from Actions secrets.')


if __name__ == '__main__':
    main()
