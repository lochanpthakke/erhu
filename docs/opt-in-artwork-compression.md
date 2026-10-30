# Phase 5: opt-in animated artwork compression

Date: 2026-10-30. Existing Phase 4B user visual approval remains accepted; no new visual-review phase was requested or performed.

## Implementation

Android metadata preservation is implemented without an additional dependency. `ArtworkSps` parses the supported AVC/HEVC syntax, validates parameter-set references, and writes only the HEVC VUI aspect-ratio and chroma-location syntax. It regenerates RBSP trailing bits and emulation-prevention bytes; it does not patch fixed byte offsets or change encoded picture samples. Unsupported syntax fails closed. No undocumented Qualcomm controls are used.

The corrected Android CSD matches the independently generated Phase 4B FFmpeg result. An independent PC MP4/SPS parser also verified the actual Android output: explicit SAR 1:1, chroma location top/bottom 2 (top-left), primaries/transfer/matrix 1 (BT.709), limited range, 8-bit YUV420, valid VPS/SPS/PPS references, and unchanged coded picture data relative to the accepted candidate. Container-only color declarations are insufficient for acceptance.

The existing album live-cover popup offers **Compress square cover** only for eligible saved artwork. It shows progress, Cancel, original/result sizes and measured savings. The action is explicit; opening an album only checks eligibility. There is no automatic or batch compression.

The initial supported subset is Android API 29+, AVC square 1080×1080, constant 30 FPS, 1–30 seconds, at most 64 MiB, average source bitrate at least 8 Mbps, 8-bit BT.709 limited-range SDR, explicit square pixels and top-left chroma positioning, and supported bounded SPS syntax. HEVC inputs, Main10, portrait and unsupported metadata are excluded. Hardware capability and actual negotiated formats are checked for each operation. Settings are HEVC Main, 6,000,000 bps VBR, 30 FPS, one-second keyframe interval and zero B frames. Resolution, frame cadence and color are retained. Decoder YUV planes are copied with their row/pixel strides; there is no RGB conversion or resizing.

Encoding uses a unique same-directory staging file and retains the original. Metadata/sample timing, full end-of-stream decode, frame count and dimensions must pass. Savings must reach both 10% and 1 MiB. Source SHA-256 fingerprints protect against concurrent changes. The existing `VerifiedArtworkPublisher` syncs and atomically replaces the original only after validation. No database work follows the commit. Failure/cancellation removes only the owned staging file. Interrupted staging is recovered when preparing the control, only when no compression is active. This is atomic file publication, not a claim of guaranteed durability across arbitrary device power loss.

Repository lifecycle locking coordinates compression with downloads/deletes. Only one compression runs globally, and competing requests are rejected rather than queued. Delete waits for cancellation to complete. HEVC output becomes ineligible, preventing repeated compression. Result messages are held in memory; no schema change was needed.

## Scope

- `core:model`: compression state.
- `core:network`: eligibility policy, bounded SPS parser/writer, hardware encoder, transactional compressor and focused tests. Existing validator/publisher are reused unchanged.
- `core:database`: compression controller and repository coordination, with concurrency/recovery tests.
- `feature:library`: existing album/live-cover controls, view-model integration and tests.
- Documentation only elsewhere. No dependency, signing, database-schema, music, lyrics, VYBE, index or settings changes. Existing lyrics files and Phase 3 validator/publisher/HLS hardening retain their initial hashes, except the repository implementation intentionally extended for this feature.

## Verification

693 unit/regression tests pass: network 320, database 177, library 195, app 1; zero failures/errors/skips. Focused cases cover eligibility, exact metadata correction, malformed metadata rejection, insufficient space, encoding/validation/move failure, cancellation, source changes, atomic publication, concurrent operations, orphan recovery and repeated-compression prevention. Touched Kotlin formatting and new Java formatting pass. Final Detekt/build details are recorded below when complete; existing findings are not suppressed or baselined away.

S23 identity: SM-S911B, serial RZCXA1H43GW, Android 17. Tests ran as isolated DEX command-line probes, without installing or launching empee3. Source SHA-256 was verified before use:

`3101f2316857b74d772dac317cab718f9fce07a5b2e479e3bd6b8ab77eb8e416`

| Measurement | Result |
|---|---|
| Source | 18,113,173 bytes |
| Actual Android candidate | 11,188,275 bytes |
| Saved | 6,924,898 bytes / 38.2313% |
| Video | HEVC Main, 1080×1080, 30 FPS, 450 frames, 15 seconds |
| Metadata | Explicit SAR 1:1; top-left chroma; 8-bit YUV420 BT.709 limited SDR |
| Initial isolated encode wall time | 6.129 seconds (diagnostic, uncontrolled) |
| Initial candidate SHA-256 | `16e27c09edec98640fe06d4edd6064634cc3b35b4b29462a0d0cc1c086f52b5d` |
| Full transaction output SHA-256 | `cd92419ca2b301e88e4a1bd267b45a93250197b14d9e0fdd8cfe18b5019f9317` |

Actual production Kotlin transaction checks passed on the S23: eligibility, cancellation with original SHA unchanged and staging removed, successful metadata/full-decode validation and atomic publication on a disposable copy, completion reporting, and rejection of subsequent compression. The container hashes differ between runs; encoded non-SPS NALs and all SPS bits outside the selected metadata match the accepted Phase 4B output. PC full decoding and independent syntax parsing complement the Android validation. Prior accepted SSIM/PSNR and visual observations apply to identical coded picture data; no new visual pass is claimed.

Before the final transaction check, approximately 17.4 GiB was available, battery temperature was 40.7°C and Android thermal status was 0. Afterward battery was 40.9°C, AP 45.9°C and thermal status remained 0. These are observations during USB charging, not a controlled thermal or battery benchmark. No repeated bitrate search was performed.

Raw evidence and experimental videos are outside Git in the chat workspace's `outputs/artwork-phase5`. Only exact operation-owned temporary device files are cleaned after collection; the previous comparison videos in Downloads are retained.

## Release and remaining checks

Debug, benchmark and optimized release APK assembly all completed from the frozen source. The combined Gradle command exited nonzero solely for Detekt: model 1, network 63, database 22 and library 87 findings. Model's existing `RepairDiagnostics.equals` complexity warning is unrelated. Network/library counts are unchanged; database increased from 21 to 22 because extending the existing repository reaches its `TooManyFunctions` threshold. Existing repository size and live-cover method complexity findings remain. No Detekt rule was weakened, and no new compressor/controller file has a Detekt finding.

The release is ARM64-only, non-debuggable, package `com.lochanpthakke.empee3`, versionCode 1 / versionName 1.0. Signature verification passed with the unchanged certificate SHA-256 `5bb0fc457b96d9fb6cbc606009de44d633bee95b58fec2dc633a7a4eb54a9ce5`.

- APK: `outputs/artwork-phase5/empee3-opt-in-artwork-arm64.apk` in the chat workspace.
- Size: **37,198,759 bytes**.
- SHA-256: **`ed08bee19ecc92aff04bcd254de8e3de8eb56d648e148be54aa3653b20a669d9`**.
- Paired release/debug archive: `D:\android-toolchain\build-opt-in-artwork-20261030` (outside Git).

The user selected **Prepare APK only** after the isolated device checks. Accordingly, no isolated R8 application startup check, fresh app-data backup, installation or main-app launch was performed. The Phase 3 installed APK remains unchanged. The completed device verification covers the actual encoder, metadata validator, full decoder and Kotlin file transaction, not the optimized application's UI/startup. A safe backed-up in-place update and normal application startup check remain unperformed.

No production artwork was modified by Phase 5. Previous recorded production artwork was empty after the user's earlier explicit removal; this phase did not inspect private app data or invent an existing-artwork playback result. Accessible production artwork after an update therefore remains unverified. No main-app uninstall, data clear, database write, new visual phase, commit or push occurred.

Savings are asset-dependent. Devices or streams outside the verified subset retain the original. This feature does not recover the original after a successful intentional compression; the validated replacement becomes the artwork. Existing production artwork accessibility must be reported from the current backup/device inventory, without downloading or creating production artwork just to manufacture a test.


## Subsequent installation

The user subsequently requested installation. The S23 identity and idle empee3 process state were checked; no UI taps or app launch were needed. A fresh full backup was validated at `D:\android-toolchain\dbbackup\pre-artwork-phase5-20261030-234416` (69 files, SQLite quick_check ok, schema 14, 208 songs and 1 animated-artwork record). A same-code, same-key Phase 3 debug bridge enabled the private-data backup without launching the app. The Phase 5 ARM64 release was then installed using `adb install -r`: **Success**. The installed APK SHA-256 matches `ed08bee19ecc92aff04bcd254de8e3de8eb56d648e148be54aa3653b20a669d9`.

This supersedes the earlier APK-only installation status. No app uninstall, data clear, artwork removal/compression, old-data restoration, startup/playback test, commit or push was performed during installation. The app was left unopened.
