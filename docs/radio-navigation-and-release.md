# Radio navigation and release delivery

Featured Radio Station cards now navigate to a typed station-details route. Loading uses the existing RadioRepository/RadioEngine/VYBE path once per navigation entry and does not touch playback, queue state or radioMessages. MainScaffold's existing radioMessages collector was the source of the Android toast: Home previously invoked startRadio directly. It remains available for deliberate playback actions.

The station displays the original card artwork/name/description, song count, Play Radio, and the existing library song rows/menus. Play Radio passes the exact loaded RadioPlan and displayed songs into the existing playback/session logic. Individual row taps start at that row using the normal collection playback path. Favorites and tag edits update a single bounded Room song-ID observer without regenerating the radio. Back clears the entry's ViewModel and observer. The radio ranking and VYBE algorithms are unchanged.

## Performance finding

The last installation replaced `erhu-release-arm64.apk` (optimized release, not debuggable) with `erhu-home-lyrics-debug.apk` (debuggable and unminified). AAPT confirms the variant difference. The former contains 2 dex files / 11.9 MiB; the latter 30 / 88.3 MiB. Debug also enables FrameDiagnostics/JankStats, including per-frame label sorting/string allocation and logging; release uses its existing no-op implementation. These are binary/configuration measurements, not frame-rate measurements. Current numerical scrolling/navigation improvement is not claimed: the S23 was disconnected during preparation.

Release delivery now requires the release variant, R8 mapping, non-debuggable/test-only manifest, correct app label/application ID/version, absence of debug JankStats classes, and the exact existing signing certificate. Glass effects and popup/navigation animations were not disabled or altered. No local release APK was compiled for this task.

## GitHub Actions

Workflow: `.github/workflows/release.yml`. Runs on GitHub-hosted Ubuntu with JDK 21, the checked-in Gradle wrapper, SDK package android-37.0/build-tools 36, and Gradle caching. It runs JVM tests and release lint, assembles ARM64 release, locates APKs through AGP output-metadata.json, verifies signing/identity, then uploads APK/checksum/verification data. Reports and R8 mapping are separate artifacts. A kshitij branch push builds artifacts only. Publishing requires an explicit workflow_dispatch publish input or an `erhu-v<version>` tag, and the verification job must succeed first. GitHub only exposes new manual workflows after they exist on the default branch; branch-push builds work immediately without modifying master.

Version: **1.0.1 (2)**. Application ID remains **com.lochanpthakke.empee3** for upgrades; namespace **com.erhu.erhu**, display name **erhu**. Existing release R8/resource shrinking, permissions, and local signing fallback are preserved. CI supplies `-PrequireReleaseSigning=true` so a missing key cannot silently use a runner-generated debug key. The new workflow builds actual release code; the existing public certificate belongs to the original Android Debug key and is retained for compatibility, not represented as a newly provisioned production signing identity.

Required repository Actions secrets:
- `ERHU_KEYSTORE_BASE64`: base64 of the existing compatible keystore.
- `ERHU_KEYSTORE_PASSWORD`
- `ERHU_KEY_ALIAS`
- `ERHU_KEY_PASSWORD`

The verifier requires SHA-256 `5bb0fc457b96d9fb6cbc606009de44d633bee95b58fec2dc633a7a4eb54a9ce5`. A different key will fail rather than produce an incompatible update. No keystore, credential or secret value is committed. At initial inspection GitHub had no signing secrets; compatible signing must be provisioned before a signed build can finish. Existing Musixmatch configuration remains inactive without its separately licensed key.
