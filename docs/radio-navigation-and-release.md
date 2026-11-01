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

## Completed release and device validation — 2026-11-01

[GitHub Actions run 41302362949](https://github.com/lochanpthakke/empee3/actions/runs/41302362949) succeeded. All 1,009 JVM tests passed with zero failures/errors/skips, release lint passed, and GitHub compiled and verified the signed non-debuggable ARM64 APK. Three host release-validation tests also passed. The SDK package request was corrected to android-37.0, and JVM tasks use this project's available testDebugUnitTest variants; APK assembly remains release-only.

[Verified release artifact](https://github.com/lochanpthakke/empee3/actions/runs/41302362949/artifacts/13083155808). APK SHA-256: `c20c893e328ff55c234f84a5b1d0b22dab108dcc03b6a72e927f24409486e747`. The existing compatible key was provisioned into repository Actions Secrets with explicit user approval. No signing values were committed. Public release publication was skipped for this branch build.

The verified erhu 1.0.1 (2) APK was installed on the S23 as an in-place update after an app-data backup. Installed version and non-debuggable status were verified. No uninstall or data clearing occurred. The previous debug install was the confirmed packaging regression; no desired glass or animations were disabled.

Short same-device gfxinfo observations after warm-up:

| Scenario | Debug jank | Release jank | Debug p95 | Release p95 |
|---|---:|---:|---:|---:|
| Home scrolling | 11.75% | 4.05% | 44 ms | 20 ms |
| Home/Albums navigation | 2.85% | 1.56% | 25 ms | 18 ms |
| Queue presentation | 11.05% | 4.18% | 29 ms | 11 ms |
| Popup presentation | 10.48% | 11.15% | 48 ms | 32 ms |

Warmed PSS fell from 431,988 to 303,066 KiB. Popup p95 improved but its jank percentage did not. These were short representative checks, not a sustained controlled benchmark: the user changed the song/Home content between runs, and battery temperatures were 37.7 C versus 38.5 C.

On-device radio details displayed the correct Her Loss station, artwork/description, 31 generated songs and Play Radio. Scrolling, track menus, favorite toggle/restoration and Back worked. Opening and leaving retained PAUSED playback, position 0 and current item 1, with no app toast observed. Play Radio/individual-row playback were not invoked on the user's queue; their existing playback path remains covered by the implementation and unit suites. No queue/settings changes remain from the device checks. The app was left on Home, paused.
