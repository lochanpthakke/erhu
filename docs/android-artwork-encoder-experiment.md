# S23 hardware artwork encoder experiment — October 30, 2026

**Latest: Phase 4B metadata checks passed and the user has confirmed overall S23 visual acceptance.
The corrected Graduation candidate is approved for a later opt-in implementation phase only.** Earlier Phase 4 findings
below describe the unchanged, uncorrected hardware output and are retained as experiment history.

**6 Mb/s trial completed and reviewed. Further encoding stopped on a metadata-preservation discrepancy.**
One hardware candidate was produced; **4 Mb/s was not attempted**. Retain the original.
The earlier disconnected-device blocker is superseded by these resumed results.

## Input and device

- Source: `Graduation_Kanye_West_AnimatedArtwork_square.mp4`, **18,113,173 bytes**.
- SHA-256: `3101f2316857b74d772dac317cab718f9fce07a5b2e479e3bd6b8ab77eb8e416`.
  PC original and isolated phone copy both matched Phase 3's recorded source inventory before encoding.
- Original: AVC High, 1080 × 1080, 30 FPS, 450 frames, 15.000 s, square pixels, 8-bit YUV420,
  limited-range BT.709, no audio. It remains unchanged in the Phase 3 experiment originals.
- Samsung Galaxy S23 **SM-S911B**, serial **RZCXA1H43GW**, identity reconfirmed on resumption.
  Android 17 is recorded by the earlier device inspection and candidate container metadata.
- Available space before encoding: **18,265,972 KiB** (about 17.42 GiB).
- Reused the existing capability discovery; did not repeat the repository audit or encoder inventory.

## Capabilities and actual configuration

Prior queried hardware encoders: `c2.qti.avc.encoder` (Baseline/Constrained Baseline/Main/High/
Constrained High through level 6), `c2.qti.hevc.encoder` (Main/Main Still/Main10/HDR variants through
High Tier level 6.2). Both advertise flexible 8-bit YUV420 and planar/semi-planar input, two-pixel
alignment, VBR, and support for this exact 1080²/30 FPS configuration. Qualcomm OMX names are aliases.
HEVC ranges: 96–8192 per dimension, 1–160 Mb/s; AVC: 128–8192, 1–220 Mb/s. Advertised ranges are not
measured throughput. Full numeric profile/color/rate details remain in `capabilities.json`.

**Actual trial:** `c2.qti.hevc.encoder`, HEVC **Main**, VBR **6,000,000 bit/s**, 1080 × 1080,
30 FPS, one-second I-frame interval, max B-frames 0. MediaCodec output confirmed profile 1,
bitrate-mode 1, bitrate/max-bitrate 6,000,000, frame-rate 30, correct dimensions and color-standard 1
(BT.709), transfer 3 (SDR video), range 2 (limited). MP4 uses `hvc1`. No RGB conversion, scaling,
frame dropping, denoising or other image filter was requested. Only this candidate was encoded.

The AVC hardware decoder and HEVC encoder both exposed `YUV_420_888` Images (format 35), with
**1080 × 1080 crops**. Decoder Y/U/V row strides were **1152/1152/1152**, pixel strides **1/2/2**;
encoder row strides **1080/1080/1080**, pixel strides **1/2/2**. The tool copied each Y/U/V sample
using the actual row/pixel strides, excluding padding; it did not treat padded decoder rows as
packed 1080-wide data. Decoder and encoder negotiated the expected BT.709 limited/SDR tags.
This validates the exercised packing path, not universal encoder portability or chroma-siting preservation.

## Actual measurements

| Measurement | Original | Hardware HEVC 6 Mb/s |
|---|---:|---:|
| File bytes | 18,113,173 | **11,188,257** |
| Saved | — | **6,924,916 bytes / 38.23138%** |
| Total-file average bitrate | about 9.660 Mb/s | **5.96707 Mb/s** (video stream about 5.965 Mb/s) |
| Resolution / bit depth / color | 1080² / 8-bit / BT.709 limited | Same |
| Decoded frames / frame rate / duration | 450 / 30 FPS / 15.000 s | **450 / 30 FPS / 15.000 s** |
| Encoder input/output frame counts | — | **450 / 450** |
| Full PC decode | Pass | **Pass**, FFmpeg 7.1 |
| Full S23 decode | Source decoded during transcode | **Pass**, `c2.qti.hevc.decoder`, 450 frames |
| SSIM, all planes | — | **0.991968** |
| PSNR average / minimum / maximum frame | — | **48.525399 / 43.665699 / 51.868062 dB** |

Encoding/transcode wall time was **7.060 s**, including setup and muxer stop; process CPU time
**7.526 s**. These exclude the final resource-release block and are one diagnostic run, not a
controlled encoder-only, battery or speed benchmark. Separate S23 full-stream decode drained in
**1.238 s**; no frames were rendered in that check. Output PTS ran from 0 to 14,966,666 us at the
expected cadence. Source MediaExtractor already exposed the first PTS as zero, despite FFmpeg's
container-start report of 0.033333 s. No frames or animation duration were removed.

Candidate SHA-256: `6915fdb55e84bbc28c106c517f97d481389027847c029fcffd9b596d13a45611`.
Phone and PC copies matched. The PC x265 CRF18 result (81.45% saving) is a reference only.

## Metadata discrepancy — acceptance blocker

Bitstream-header inspection confirmed that the source explicitly signals **top-left chroma
positioning** (`chroma_loc_info_present_flag=1`, top/bottom sample location type 2). The HEVC
candidate **omits chroma-location signalling**; FFmpeg interprets its decoded chroma location
as **left**, versus source **topleft**. Copying sample values without accounting for that
positioning difference does not establish preserved rendered chroma geometry. The experiment
therefore stopped further encoding instead of silently converting, patching or re-encoding.

The candidate also omits explicit sample-aspect-ratio signalling (`aspect_ratio_info_present_flag=0`).
FFmpeg reports SAR 0/1 (unspecified), while the S23 decoder reports 1/1. The dimensions and sampled
square appearance are unchanged, but the source's explicit 1:1 SAR metadata was not retained.
Both streams' SPS headers confirm 8-bit luma/chroma, BT.709 primaries/transfer/matrix and limited range.
Thus the headline BT.709/bit-depth checks pass, but full image-characteristic preservation does not.

## Objective quality versus inspected visuals

SSIM/PSNR used all 450 timestamp-aligned decoded frames, resetting each stream's first PTS to zero.
They compare decoded YUV samples and do **not** correct/resolve the chroma-location discrepancy;
high scores cannot override the metadata failure or replace visual review.

Inspected paired stills at frames **0, 2, 90, 210, 330 and 449**, plus native 360-pixel crops and
amplified differences for gradients, dark cloud areas, bear outlines/eyes, crown, clothing detail
and early moving content. Frame **2** (about 0.067 s) is the worst PSNR frame. The samples look
close overall, with no obvious large hue shift or severe banding in the reviewed regions.
Fine texture is smoothed, and early moving-detail edges show larger residual differences than
later sampled frames. This is lossy; neither visually lossless nor full-motion acceptable quality
has been established. **No full-motion S23 A/B, flicker, loop-seam or seeking review was performed.**
The shell experiment has no isolated video viewer, and no app/UI was installed or opened to add one.

## Thermal observations and safety

The initial connection check showed battery about 39.1°C and thermal status 1 (light). By the
immediate pre-encode snapshots, thermal status was **0**, battery-service temperature **38.2°C**
(thermal BAT sensor 38.3°C), AP sensor **39.2°C**, skin **37.3°C**. Post-encode snapshots: status **0**,
battery **38.2°C**, AP **39.0°C**, skin **37.2°C**. USB charging and prior use were uncontrolled;
sensor snapshots do not prove the absence of transient throttling or measure energy/battery savings.

Only shell-owned copies under `/data/local/tmp/empee3-phase4-encoder-20261030` were used. After
pulling/validating the candidate and collecting all results, the exact `original.mp4`,
`hevc-6mbps.mp4`, and `experiment.jar` temporary files were removed, then their empty directory.
No recursive cleanup or production-path deletion was used. PC originals, candidate, logs and
comparison images remain outside Git in this chat's `work/artwork-phase4/` and `outputs/artwork-phase4/`.
Installed APK hash before/after matches Phase 3:
`d53c660ced14432db11ac70f81a78ab8fd05de745d0855557df36efec3475cb1`.
No production artwork, private app data or Room records were accessed/changed. No application build,
installation/reinstallation, app launch/force-stop, dependency, UI, automatic job, commit or push.

## Recommendation

**Option 3 for this workflow: retain the original.** Hardware speed and 38.23% savings are promising,
but this candidate fails strict chroma-positioning/signalling preservation and remains unreviewed
in full motion. Do not adopt it or proceed to a lower bitrate. A future experiment should first
establish a supported way to preserve chroma location and explicit square-pixel metadata, then
repeat only the 6 Mb/s validation and full-motion display review. This is a workflow limitation,
not evidence that S23 hardware compression cannot eventually be suitable.

API references: [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec),
[CodecCapabilities](https://developer.android.com/reference/android/media/MediaCodecInfo.CodecCapabilities).

## Phase 4B — metadata-only preservation result

### Investigation and selected method

The original AVC and prior HEVC SHA-256 values above were reverified. The original signals
chroma location type **2/2** (top-left for both fields), extended SAR **1:1**, primaries/transfer/
matrix **1/1/1** (BT.709), full-range flag **0**, chroma format **1** (YUV420), and luma/chroma
bit-depth-minus-8 **0/0**. The prior HEVC retained color/range/bit depth but omitted the two
requested VUI presence flags. These were checked again from the actual bitstreams, not inferred
from encoder names or container labels.

Android documents `KEY_PIXEL_ASPECT_RATIO_WIDTH/HEIGHT` (`sar-width/height`), color-standard,
transfer and range. The reviewed public MediaFormat API and local API-37 SDK expose **no public
chroma-location key**. This is a finding about the public API, not proof that no vendor control
exists. No undocumented vendor settings were guessed. MediaMuxer accepts codec-specific data
through `addTrack`; its documented API does not provide a chroma-location/SPS rewrite control.
Setting container display geometry alone would not establish explicit HEVC VUI preservation.
[MediaFormat](https://developer.android.com/reference/android/media/MediaFormat),
[MediaMuxer](https://developer.android.com/reference/android/media/MediaMuxer).

Selected the documented FFmpeg 7.1 **`hevc_metadata` bitstream filter**, operating on a new copy
of the previous 6 Mb/s hardware output. No repeat hardware encode, lower-bitrate trial or new
dependency was needed. The filter parses/writes coded-bitstream structures; it is not an arbitrary
SPS byte-offset patch. Its source sets both chroma-location types and their presence flag, and
sets the SAR presence flag/idc. For 1:1 it selects standard aspect-ratio idc **1**, semantically
equivalent to the source's extended 1:1 representation. The relevant syntax/semantics are in
H.265 clauses 7.3.2.2, E.2.1/E.3.1, table E.1 and figure E.1.
[FFmpeg filter documentation](https://ffmpeg.org/ffmpeg-bitstream-filters.html#hevc_005fmetadata),
[version-7.1 implementation](https://raw.githubusercontent.com/FFmpeg/FFmpeg/n7.1/libavcodec/bsf/h265_metadata.c),
[ITU-T H.265](https://www.itu.int/rec/dologin_pub.asp?id=T-REC-H.265-201304-S%21%21PDF-E&lang=e&type=items).

Exact stream-copy operation (absolute paths/FFmpeg binary are recorded in the JSON evidence):

```text
ffmpeg -nostdin -hide_banner -n -i hevc-6mbps.mp4 -map 0:v:0 -c:v copy
  -bsf:v hevc_metadata=sample_aspect_ratio=1/1:chroma_sample_loc_type=2
  -tag:v hvc1 -map_metadata 0 -video_track_timescale 90000 -movflags +faststart
  hevc-6mbps-explicit-metadata.mp4
```

### Compliance and no-picture-change evidence

Two separately implemented syntax readers agree: FFmpeg `trace_headers` and a dependency-free,
read-only Python MP4/NAL/SPS reader following the H.265 field order. The latter rejects unsupported
syntax branches; it is a focused validator for these files, not a general conformance suite.

- Explicit SAR flag **1**, aspect idc **1** (1:1); chroma-location flag **1**, top/bottom **2/2**.
- HEVC Main, 8-bit YUV420, BT.709 **1/1/1**, limited range **0**, 1080 × 1080 after the existing
  1088-coded-picture conformance crop. No crop/profile/level/color/bit-depth field was changed.
- VPS id **0**, SPS id **0**, PPS **0 → SPS 0**, all **450** coded slices reference PPS 0.
- VPS/PPS and all other non-SPS NAL bytes match exactly, including **435** ordinary coded-picture
  NALs and **15** IDR NALs. SPS NAL header and every RBSP bit outside the two selected VUI regions
  match (allowing the necessary final byte-alignment padding change).
- All **450 decoded raw YUV420 frame checksums**, sizes and presentation/duration rows match
  the previous HEVC candidate exactly. This proves no decoded Y/U/V sample changed in this PC
  decoder. RGB display reconstruction may legitimately differ when honoring corrected siting.
- Every MP4 sample still lasts **3000/90000 s**; no composition offsets were introduced. The
  muxer merged equivalent timing-table runs `[1,3000]+[449,3000]` into `[450,3000]`; expanded
  sample timing is identical. Remuxed container bytes are not claimed byte-identical.
- The corrected SPS is in the MP4 `hvcC` configuration; no stale in-band SPS remains. There is
  no `pasp` box in either MP4: explicit square pixels are signalled in the corrected SPS itself.

This combination establishes a standards-based, metadata-only correction for this stream, with
independent syntax inspection and two decoder implementations. It is not a certification of an
untested general-purpose metadata editor or every HEVC/player combination.

### Measurements and compatibility

| Measurement | Original AVC | Previous hardware HEVC | Corrected HEVC copy |
|---|---:|---:|---:|
| File bytes | 18,113,173 | 11,188,257 | **11,188,266** |
| Storage saved versus original | — | 38.23138% | **38.23133%** |
| Frames / FPS / duration | 450 / 30 / 15.000 s | Same | **Same** |
| Decoded format | 1080², 8-bit BT.709 limited | Same | **Same** |
| Explicit SAR / top-left chroma | Yes / Yes | No / No | **Yes / Yes** |
| SSIM, all 450 aligned frames | — | 0.991968 | **0.991968** |
| PSNR average / worst frame | — | 48.525399 / 43.665699 dB | **Same** |

The correction adds **9 bytes** and saves **6,924,907 bytes** versus the original. These are
experimental-file savings only; nothing was applied to production storage.
Corrected SHA-256: `8e43ffd99bdeccd0623551830c07114c6d80aab28b110d4356780d974a186a91`.

PC full decode passed. The S23's independent `c2.qti.hevc.decoder` also decoded all **450** frames,
1080² YUV420, correct color aspects, SAR **1:1**, rotation **0**, with PTS 0–14,966,666 us at
30 FPS. Its drain-loop wall time was **1.156 s**, a diagnostic compatibility observation only.
Android's public output format does not expose chroma location; that field was verified by the
two bitstream readers rather than falsely attributed to MediaCodec reporting.

### Visual review and remaining acceptance gate

Repeated full-sequence SSIM/PSNR against the original, plus per-frame raw checksums against the
uncorrected HEVC. Scores and the worst frame (**2**, about 0.067 s) are unchanged because the
encoded samples are unchanged. Average metrics do not establish perceptual acceptance.

Reviewed three-way stills at frames **0, 2, 90, 210, 330 and 449**, native crops of eyes/outlines,
crown, clothing, gradients and dark cloud detail. Default FFmpeg PNG conversion produced identical
previous/corrected crops on this build, so it cannot demonstrate chroma-edge correction. Additional
review images explicitly use the signalled positions: source/corrected (0,0), previous (0,128) in
luma-grid/256 units, with the same bilinear full-chroma RGB interpolation and BT.709 conversion.
These are display-review images only; no filter was applied to the candidate video.

In those siting-aware images, sampled color edges are closer to the original. For example, mean
absolute RGB error in the frame-210 crown crop falls **1.3268 → 0.8082** on a 0–255 scale;
the worst-frame clothing crop falls **2.6732 → 2.3217**. These local objective comparisons are
not a universal perceptual metric. Visually, fine outlines/crown color boundaries align more
closely; inherited texture smoothing and early-frame detail differences remain. No obvious new
banding or large hue shift was observed in the sampled gradients/dark regions.

**Continuous full-motion S23 A/B, flicker, seeking and loop-seam review remain unperformed.**
The shell decoder renders no frames and the available image inspection is still-based; no new
viewer/UI or application was installed/launched to manufacture that claim. Temporal artifacts
from the original lossy hardware encode therefore remain an unverified acceptance item.

### Decision and preservation

**Metadata requirements pass for this corrected experimental stream. Final candidate approval
is withheld; retain the original.** The earlier technical signalling blockers are resolved, but
every quality requirement has not passed: full-motion/flicker acceptance remains unverified.
No further bitrate or encoder experiment was attempted. This is evidence for a later opt-in
implementation investigation, not authorization to replace user artwork or ship a compressor.
A future Android implementation would still need a supported, tested metadata-preservation path;
the PC FFmpeg operation has not been integrated into the app.

Original AVC and previous HEVC hashes remain unchanged. Exact phone experiment files
`corrected.mp4` and `experiment.jar` under `/data/local/tmp/empee3-phase4b-metadata-20261030`
and that empty directory were removed after result collection. The installed APK hash remained
`d53c660ced14432db11ac70f81a78ab8fd05de745d0855557df36efec3475cb1` before/after. No empee3
build, launch, reinstall, app-data/artwork/Room access or write, dependency, UI change, automatic
job, commit or push. All experiment scripts, videos, logs, native crops and proof JSON are outside
Git in this chat's `work/artwork-phase4b/` and `outputs/artwork-phase4b/`; only this report changed.


## Personal S23 visual review — confirmed

Recorded at 2026-10-30T21:38:38+05:30. The verified original and corrected 6 Mb/s HEVC copy were prepared unchanged
in `Download/empee3-Phase4B-Comparison` with a two-item VLC playlist. Both phone-copy SHA-256 values
matched the verified PC files. The existing VLC player was used; no viewer was built or installed
and the agent did not launch a player or empee3. The user was instructed to compare motion, flicker,
color, fine detail and looping, then personally replied: **“its good”**.

This records the user's **overall visual acceptance**, not an agent-performed full-motion review
or separately itemized observations for each criterion. It supersedes the earlier pending-visual-
review/withheld-approval status for this corrected Graduation candidate. Combined with the recorded
metadata, sample-preservation, timing and decoder checks, it supports proceeding to a **later opt-in
implementation phase** for this tested workflow. No production implementation or replacement is
authorized by this confirmation alone; broader assets/devices are not validated by this single clip.

No new encoding, app build/install/launch, production artwork/app-data/Room change, commit or push
was performed when recording this feedback. The comparison files remain on the phone; none were
removed.
