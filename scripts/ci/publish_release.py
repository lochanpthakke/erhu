"""Publish only verified artifacts, attaching assets to a draft before exposing the release."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile


def release_plan(version, ref, channel, run_number):
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise ValueError("Expected the APK's numeric release version")
    if ref.startswith("refs/tags/"):
        tag = ref.removeprefix("refs/tags/")
        if not re.fullmatch(r"erhu-v" + re.escape(version) + r"(?:-(?:alpha|beta|rc)\.[1-9]\d*)?", tag):
            raise ValueError("Release tag must match the APK version")
        return tag, "-" in tag.removeprefix("erhu-v")
    if channel not in ("beta", "stable"):
        raise ValueError("Choose beta or stable publication")
    if channel == "beta":
        if not str(run_number).isdigit() or int(run_number) < 1:
            raise ValueError("Invalid workflow run number")
        return f"erhu-v{version}-beta.{run_number}", True
    return f"erhu-v{version}", False


def validate_dist(directory, commit):
    metadata = json.loads((directory / "verification.json").read_text())
    if metadata.get("applicationId") != "com.lochanpthakke.empee3" or metadata.get("debuggable") is not False:
        raise ValueError("Expected a verified erhu release APK")
    if metadata.get("commit") != commit or not isinstance(metadata.get("versionCode"), int) or metadata["versionCode"] < 1:
        raise ValueError("Artifact commit/version mismatch")
    apks = list(directory.glob("*.apk"))
    if len(apks) != 1:
        raise ValueError("Expected exactly one APK")
    apk = apks[0]
    checksum = directory / (apk.name + ".sha256")
    if not apk.stat().st_size or hashlib.sha256(apk.read_bytes()).hexdigest() != checksum.read_text().split()[0]:
        raise ValueError("APK checksum mismatch")
    return metadata, [apk, checksum, directory / "verification.json"]


def require_increasing_code(current, previous):
    if previous.get("applicationId") != current["applicationId"] or not isinstance(previous.get("versionCode"), int):
        raise ValueError("Published version metadata is invalid; inspect the existing release before publishing")
    if current["versionCode"] <= previous["versionCode"]:
        raise ValueError("Increment versionCode before publishing another release")


def main():
    directory = Path("dist")
    commit = os.environ["GITHUB_SHA"]
    metadata, assets = validate_dist(directory, commit)
    tag, beta = release_plan(metadata["versionName"], os.environ.get("GITHUB_REF", ""), os.environ.get("RELEASE_CHANNEL", "beta"), os.environ["GITHUB_RUN_NUMBER"])
    # Fail on duplicate published tags instead of mutating released assets/history.
    existing = subprocess.run(["gh", "release", "view", tag], capture_output=True)
    if existing.returncode == 0:
        raise SystemExit("Release tag already exists. Do not overwrite published APKs; choose a new version/code.")
    with tempfile.TemporaryDirectory() as temporary:
        releases = json.loads(subprocess.check_output(["gh", "api", f"repos/{os.environ['GH_REPO']}/releases?per_page=100"], text=True))
        published = [r for r in releases if not r.get("draft") and any(a["name"].endswith(".apk") for a in r.get("assets", []))]
        for release in published:
            if not any(a["name"] == "verification.json" for a in release.get("assets", [])):
                raise SystemExit("Existing APK release lacks version metadata; inspect it before publishing")
            prior = Path(temporary) / "previous.json"
            subprocess.run(["gh", "release", "download", release["tag_name"], "--pattern", "verification.json", "--output", str(prior), "--clobber"], check=True)
            require_increasing_code(metadata, json.loads(prior.read_text()))
        notes = Path(temporary) / "notes.md"
        notes_content = os.environ.get("RELEASE_NOTES", "").strip()
        if not notes_content:
            repo_notes = Path(__file__).resolve().parents[2] / "release_notes.md"
            if repo_notes.is_file():
                notes_content = repo_notes.read_text(encoding="utf-8").strip()
        notes.write_text(notes_content or "Optimized ARM64 APK. Installation identity and original signing certificate preserved.", encoding="utf-8")

        # Ensure VYBE model pack is included in release assets
        vybe_tag = None
        for r in releases:
            asset_names = [a["name"] for a in r.get("assets", [])]
            if "vybe-embeddinggemma-300m-int4int8.zip" in asset_names and "vybe-embeddinggemma-300m-int4int8.zip.sha256" in asset_names:
                vybe_tag = r["tag_name"]
                break
        if not vybe_tag:
            vybe_tag = "erhu-v1.0.17-beta.31"

        print(f"Fetching VYBE model pack from {vybe_tag}...")
        subprocess.run(
            ["gh", "release", "download", vybe_tag, "--pattern", "vybe-embeddinggemma-300m-int4int8.zip*", "--dir", str(temporary), "--clobber"],
            check=True
        )
        vybe_zip = Path(temporary) / "vybe-embeddinggemma-300m-int4int8.zip"
        vybe_sha = Path(temporary) / "vybe-embeddinggemma-300m-int4int8.zip.sha256"
        if not vybe_zip.is_file() or not vybe_sha.is_file():
            raise SystemExit("Failed to download VYBE model pack assets")
        if hashlib.sha256(vybe_zip.read_bytes()).hexdigest() != vybe_sha.read_text(encoding="utf-8").split()[0]:
            raise SystemExit("Downloaded VYBE model pack failed sha256 checksum verification")
        assets.extend([vybe_zip, vybe_sha])

        subprocess.run(["gh", "release", "create", tag, "--draft", "--target", commit, "--title", f"erhu {metadata['versionName']}" + (" beta" if beta else ""), "--notes-file", str(notes)], check=True)
        subprocess.run(["gh", "release", "upload", tag, *map(str, assets)], check=True)
        uploaded = json.loads(subprocess.check_output(["gh", "release", "view", tag, "--json", "assets"], text=True))["assets"]
        expected = {a.name for a in assets}
        if {a['name'] for a in uploaded if a.get('size', 0) > 0} != expected:
            raise SystemExit("Draft asset verification failed. Release remains unpublished.")
        subprocess.run(["gh", "release", "edit", tag, "--draft=false", "--prerelease=" + str(beta).lower(), "--latest=" + str(not beta).lower()], check=True)
    print("Published verified release: " + tag)


if __name__ == "__main__":
    main()
