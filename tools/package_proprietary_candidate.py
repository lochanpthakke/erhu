"""Prepare accompanying source without private work, credentials or uncleared artwork.
Run ONLY after reviewing the working tree. Does not build, sign or publish an APK.
"""
import hashlib
import json
import subprocess
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build" / "proprietary-recipient"


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    result = subprocess.run(
        ["git", "ls-files", "-z"], cwd=ROOT, check=True, capture_output=True,
    )
    paths = {Path(p) for p in result.stdout.decode("utf-8").split("\0") if p}
    # Deliberate additions only; do not sweep untracked evidence/private directories.
    for folder in ("third-party/jaudiotagger", "app/src/proprietaryCandidate",
                   "core/vybe-mediapipe/src/proprietaryCandidate", "app/src/main/assets/third-party",
                   "app/src/main/assets/third-party-addenda"):
        paths.update(p.relative_to(ROOT) for p in (ROOT / folder).rglob("*") if p.is_file())
    for name in ("core/network/src/main/java/com/erhu/erhu/core/network/di/DistributionNetworkPolicy.kt",
                 "tools/package_proprietary_candidate.py", "APK_RELEASE_READINESS.md"):
        paths.add(Path(name))
    overlays = ROOT / "app/src/proprietaryCandidate/res"
    replaced = {(p.parent.name, p.stem) for p in overlays.rglob("*") if p.is_file()}
    manifest = []
    archive = OUT / "erhu-1.0.18-corresponding-source.zip"
    with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED) as target:
        for path in sorted(paths):
            posix = path.as_posix()
            if posix.startswith(("work/", "compliance/", ".git/", ".codex", "docs/", "screenshots/")):
                continue
            if any(part in {"build", ".gradle", "node_modules"} for part in path.parts):
                continue
            if path.suffix.lower() in {".apk", ".jks", ".keystore", ".mp4", ".jpg", ".jpeg"}:
                continue
            if path.name in {"local.properties", "keystore.properties", "jwt.txt", "google-services.json"}:
                continue
            if posix.startswith("app/src/main/res/") and (path.parent.name, path.stem) in replaced:
                continue
            # Do not distribute saved product screenshots/art outside app resources.
            if path.suffix.lower() in {".png", ".webp", ".svg"} and not posix.startswith("app/src/"):
                continue
            source = ROOT / path
            if not source.is_file():
                continue
            data = source.read_bytes()
            info = zipfile.ZipInfo(posix)
            info.create_system = 3
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = (0o100755 if path.name == "gradlew" else 0o100644) << 16
            target.writestr(info, data)
            manifest.append({"path": posix, "sha256": hashlib.sha256(data).hexdigest()})
        target.writestr("RECIPIENT_SOURCE_MANIFEST.json", json.dumps(manifest, indent=2))
    print(archive)
    print("SHA-256:", hashlib.sha256(archive.read_bytes()).hexdigest())


if __name__ == "__main__":
    main()
