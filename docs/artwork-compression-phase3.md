# Animated artwork: Phase 3 (October 30, 2026)

This phase delivers replacement/recovery hardening and measured compression experiments.
**There is no on-device encoder or automatic compression feature in this release.** Existing
artwork is never recompressed. Production storage savings applied: **zero**. A PC x265 result
cannot safely be assumed equivalent to a phone's hardware encoder. Retaining existing behavior
and a small verified subset takes precedence over adding an unverified encoder dependency.

## Actual copies measured

Sources were extracted from the validated October 29 pre-lyrics-install backup, not downloaded.
`source-inventory.json` records exact source SHA-256 and metadata. A fresh pre-install backup will
establish whether the phone's current artwork is still identical. MB below means decimal MB.

| Asset / trial | Original bytes | Candidate bytes | Saved | Decision |
|---|---:|---:|---:|---|
| Graduation square, HEVC CRF18 | 18,113,173 | 3,359,742 | 81.45% | Quality-first experimental candidate; retain production original |
| Graduation square, HEVC CRF20 | 18,113,173 | 2,338,045 | 87.09% | Smaller alternative; prefer CRF18's detail margin |
| SOS square, HEVC CRF18 | 28,189,727 | 32,331,231 | -14.69% | Reject: larger than original |
| SOS portrait, HEVC Main10 | 57,963,625 | Not completed | Not measured | Abort slow trial; retain original |

FFmpeg 7.1 with libx265, preset slow, CRF18 (one CRF20 alternative), two pools / one frame thread,
same pixel format, `hvc1`, passthrough frame timing, copied metadata, faststart. No resize or
frame-rate conversion; sources have no audio. Exact commands are in `compression-results.json`.
Graduation CRF18 took 91.90 s; SOS square 352.77 s on this PC. The 2048 x 2732 Main10 trial was
roughly 0.2 fps and was stopped after several minutes; its partial scratch file is excluded from
all deliverables and compatibility claims. Existing 10-bit SDR BT.709 is preserved by retaining
that original; Main10 does not itself mean HDR.

Graduation original and both complete candidates: 1080 x 1080, 30 fps, 450 decoded frames,
15.000 s, 8-bit limited-range BT.709, square pixels. Remuxing normalizes the 0.033333 s source
start to zero. SOS square: 1080 x 1080, 24 fps, 551 frames, approximately 22.958 s, same 8-bit
BT.709 metadata. Portrait has 551 content frames at 24 fps; its source container includes the
older approximately 10 s timestamp offset (32.96 s container duration, 22.958 s content).

## Quality and compatibility limits

Graduation CRF18 full-sequence SSIM 0.991980; PSNR average 48.774 dB (minimum per-frame 47.257).
CRF20: SSIM 0.991445; PSNR average 48.115 dB. These metrics are evidence, not a perceptual guarantee.
Decoded still sheets at frames 0, 90, 210, 330 and 449 and native-size crops were inspected:
no obvious new banding or hue shift in the sampled purple/orange gradients, and no major edge
softness in the face/crown/line detail. Amplified differences reveal fine texture changes.
The SOS sheet also compares frames 0, 240 and 550: sampled dark water, bright wave edges and
fine detail show no obvious large color shift at sheet scale, although this is a reduced-size
comparison. No full-motion human A/B, flicker assessment, phone display/color assessment, or
comprehensive dark-scene review has been performed. SOS square remains rejected on size.

At the final device stage on October 30, ADB listed **no connected devices**. Android full-stream
decoding, isolated release startup, fresh backup, main-package installation and existing-artwork
device smoke checks are therefore **pending**, not passed. Candidates have no Android
compatibility claim. The prepared isolated probe has not run. Its planned wall/process CPU
timings would be diagnostic samples,
not energy, battery, thermal-controlled performance, or playback-smoothness benchmarks.

The standalone `candidate_gate.py` requires size savings, complete decode, unchanged geometry,
timing/pixel/color/rotation metadata, Android decode, and explicit full-motion review. Its six
unit tests pass. It performs no writes and is an experiment review aid, **not app integration**.
The outstanding full-motion review keeps the selected copy ineligible for production promotion.

## Production changes

- `HlsMp4Assembler` now keeps a completed download in the existing same-directory temporary
  file, decodes it fully through Android MediaCodec, fsyncs it, then atomically replaces the
  destination. No delete-first or copy fallback. A failure leaves the previous destination
  intact and only the operation's own temporary file is removed.
- New `VerifiedArtworkPublisher` serializes validation/publication and checks cancellation
  before validation, after validation, and before atomic promotion. Unsupported atomic moves
  fail safely. This protects process interruption before promotion; it is not a claim of
  filesystem durability against every sudden power-loss scenario or an atomic file/Room transaction.
- New `ArtworkVideoValidator` drains one video track through the device decoder to EOS,
  requires frames, checks cancellation, caps the drain loop at 90 seconds, and releases resources.
  Codec setup calls can still be platform-dependent. Hardware decoders may conceal bitstream
  damage, so this is stronger validation rather than proof of absence of every corruption.
- All download entry paths share per-album/per-orientation jobs. Cancellation cleans only
  that orientation's staging file. Delete operations cancel/join matching work before removal;
  startup cleanup skips active work. Failed/interrupted refreshes retain availability of saved
  artwork. Square and portrait remain separate files. No batch encoder, new permissions,
  dependency upgrade, schema/version/signing change, VYBE change, or UI redesign.

## PC validation

Focused publisher/repository tests passed first. Full regressions: network 297, database 169,
library 192: **658 tests, zero failures/errors/skips**.
Six standalone decision-gate tests pass.
Publisher tests cover validation ordering, decode failure, cancellation, failed move/disk-full
simulation, invalid staging, and interrupted staging. Repository tests cover shared jobs, live
staging cleanup avoidance, failed refresh, startup recovery, and deleting an in-flight download.
Actual disk exhaustion and
process-kill fault injection on the phone were not performed.

Touched Kotlin files pass KtLint 1.5.0; `git diff --check` passes. Prior lyrics files were verified
byte-for-byte unchanged, as were project instructions and the historical storage report.
Detekt remains blocked by 63 network and 21 database findings in existing code. The repository
class's function count is back to its existing 30 (the rule flags the threshold itself).
No lint rules were weakened. The final combined command completes the tests and APK assembly
tasks but exits nonzero because of Detekt; this is not a clean module-wide lint result.

Actual `:app:assembleRelease -PtargetAbi=arm64-v8a` succeeded (R8 release, not debuggable).
APK: `D:\Personal Projects\empee3\app\build\outputs\apk\release\app-release.apk`.
Size **37,182,375 bytes**, SHA-256
`d53c660ced14432db11ac70f81a78ab8fd05de745d0855557df36efec3475cb1`.
ZIP/aapt verify native libraries only under `arm64-v8a`; package
`com.lochanpthakke.empee3`, versionCode 1/versionName 1.0. Signature verifies with unchanged
existing certificate SHA-256 `5bb0fc457b96d9fb6cbc606009de44d633bee95b58fec2dc633a7a4eb54a9ce5`.
The existing local debug-key fallback signs the actual release build; no key was generated.

No release was installed during this phase and no phone data was changed. Prior October 29
installation remains the last verified daily build; its current presence cannot be reconfirmed
without the device. Paired final APKs are archived outside Git at
`D:\android-toolchain\build-artwork-phase3-20261030`.

After reconnecting the intended S23, perform the prepared decoder/isolated smoke checks, verify
the installed package/signature, and take a fresh full backup using the project's documented
same-code debug bridge before the real in-place update. Exact final release installation command
(PowerShell, once those checks are complete):

```powershell
& 'D:\android-toolchain\sdk\platform-tools\adb.exe' -s RZCXA1H43GW install -r 'D:\Personal Projects\empee3\app\build\outputs\apk\release\app-release.apk'
```

The real app must not be uninstalled or have its data cleared. Full-motion visual A/B and
existing-artwork access on the installed release remain manual/device checks.

## Next phase

Validate an explicitly triggered, cancellable Android encoder for 8-bit SDR first, with strict
no-resize/fallback, metadata and worthwhile-size gates; retain Main10/HDR originals until supported.
Do full-motion phone A/B and measure thermal/battery effects before enabling compression. Media3
Transformer requires a separate dependency and device encoder behavior differs from x265 CRF;
[encoder fallback options](https://developer.android.com/reference/androidx/media3/transformer/DefaultEncoderFactory.Builder)
and [format/HDR constraints](https://developer.android.com/media/media3/transformer/supported-formats)
need explicit handling. No dependency was added during this phase. No commit or push.


## Subsequent installation and artwork removal

The user explicitly requested installation and removal of downloaded live artwork, with no testing. The S23 reconnected. A fresh full backup was saved to `D:\android-toolchain\dbbackup\pre-artwork-phase3-20261030-200735` before changes (85 files; database schema 14, 208 songs, quick_check ok). A same-code/same-key debug bridge enabled the backup and scoped artwork removal. Only the `animated_artwork` records (5 rows) and its 3 downloaded video files (104,266,525 bytes) were removed. Music, lyrics, model/index and other app data were not cleared.

The final ARM64 release was installed with `adb install -r`: **Success**. Installed APK hash matches `d53c660ced14432db11ac70f81a78ab8fd05de745d0855557df36efec3475cb1`. The real app was never uninstalled. No app launch, decoder, smoke, playback or visual tests were run, as requested. No compression was applied. This update supersedes the earlier disconnected-device and pending-installation status.
