# App size and storage forensics — 2026-10-28

**Current installation:** the real S23 app was updated in place to the ARM64 release on 2026-10-28 at 22:26:39 IST. The original forensics measurements below remain historical; see the installation verification at the end. Report completed 2026-10-29.

Current `kshitij`, HEAD `84579ad`, including preserved uncommitted Home/Audio Details work. Measurements: Samsung S23, Android16, arm64-v8a. MB means decimal million bytes; KiB means1024 bytes. No commit/push, real-app reinstall, signing change, model migration or production data deletion.

## Safe change and usage

`app/build.gradle.kts` now accepts optional `-PtargetAbi`. Omitted: existing universal packaging. Allowed: arm64-v8a, armeabi-v7a, x86, x86_64; invalid values fail configuration. Every selected-ABI library is retained. No runtime logic/dependency/signing/version/R8 change.

```powershell
$env:JAVA_HOME='D:\android-toolchain\jdk21'
.\gradlew.bat :app:assembleRelease -PtargetAbi=arm64-v8a '-Pkotlin.compiler.execution.strategy=in-process'
# Omit -PtargetAbi for universal. ARM64 artifact is only for compatible devices.
```

Variant builds overwrite the same APK filenames: archive before building another. `-PbenchApp` is a separate temporary package, never the user's app. The normal debug output was rebuilt without that property after testing.

## APK measurements

| Bytes | Installed debug universal | Fresh release universal | Release ARM64 |
|---|---:|---:|---:|
| APK file |163,034,332|136,723,989|37,165,991|
| DEX logical |92,327,608 (30)|12,491,788 (2)|12,491,788 (2)|
| DEX ZIP |30,381,984|5,533,308|5,533,308|
| Native ZIP/logical |129,468,148|129,468,148|30,030,008|
| Resources ZIP |1,646,771|1,280,707|1,280,707|
| Assets ZIP |0|14,873|14,873|
| Other ZIP |110,089|110,211|110,211|
| Whole-file gzip9 simulation |88,442,391|63,745,301|20,129,426|

ZIP alignment/headers/signatures explain remaining bytes. Gzip is a transfer simulation, **not measured Play download size**. No AAB size/Play delivery was measured. Fresh release baseline was built October28 21:47; optimized artifact21:52. The older October26 artifact was not used.

Release saving **99,557,998 bytes (72.82%)**. Versus installed debug, APK saving125,868,341 combines release/R8 and ABI effects. R8/resource shrinking were already enabled. Their broad compatibility keep rules were preserved (prior MediaPipe/Flogger release crash).

ZIP comparison: no changed common non-signature entries; exactly12 non-ARM64 native entries removed. Code/resources/assets/ARM64 libraries identical. No `.debug*` or `.symtab` sections in retained ELF files. Tiny duplicate icon/licence entries were preserved. Release assets are baseline-profile files, not models. No bundled VYBE weights or accidental large debug assets.

| ABI | tasks-core1.1.0 JNI | tasks-text1.1.0 textgenai JNI | All native |
|---|---:|---:|---:|
| ARM64 |14,365,544|15,647,256|30,030,008|
| ARMv7 |10,115,520|10,622,108|20,749,296|
| x86 |20,212,356|21,645,152|41,871,940|
| x86_64 |17,589,568|19,210,352|36,816,904|

Remainder: tiny AndroidX graphics-path/DataStore libraries. VYBE uses MediaPipe TextEmbedder directly. CPU test mapped core JNI; this single path does not prove every upstream backend/model can safely omit textgenai, so neither library was removed. Cached tasks-audio AARs are not packaged dependencies.

## Installed app: exact before/after

Real package `com.lochanpthakke.empee3`, version1/1.0, min26/target37, **debug**, updated2026-10-28 21:05:57. One base.apk, no splits, ARM64 primary ABI, no extracted `.so` directory contents. Installed APK SHA256 `d296d1944da35b2e94222bd13de0d017cc0c9b08b7ab4fb1d15051891ae43212`. Signer SHA256 `5bb0fc457b96d9fb6cbc606009de44d633bee95b58fec2dc633a7a4eb54a9ce5`. No keystore.properties exists; existing release configuration uses its debug-key fallback. Signing was not changed.

StorageStatsManager real-app values were identical before/after:

| Category | Before bytes | After bytes |
|---|---:|---:|
| App |163,215,872|163,215,872|
| Data including cache |293,220,352|293,220,352|
| Cache subset |1,900,544|1,900,544|
| Non-cache user data |291,319,808|291,319,808|
| App + all data |456,436,224|456,436,224|

The user's earlier452MB figure is a rounded/earlier snapshot. [StorageStats](https://developer.android.com/reference/android/app/usage/StorageStats) data includes cache; never add cache twice. App accounting exceeds APK bytes by181,540 bytes (installation/allocation accounting). Real app remains unchanged; artifact savings have not been installed there.

Physical-device isolated `.bench` test, same seeded package updated in place:

| StorageStats App bytes | Universal release | ARM64 release | Saving |
|---|---:|---:|---:|
| S23 |149,710,336|50,054,656|99,655,680|

Compiled/install artifacts make absolute installed App bytes exceed APK length. The measured device difference, not an AAB assumption, demonstrates actual installed savings. Temporary data was185,491,456 bytes before first launch; after universal search/ARM64 update186,863,616 including729,088 cache bytes. This test copy omits saved videos/lyrics and is not a production-data reduction. [Android ABI packaging guidance](https://developer.android.com/build/configure-apk-splits) describes device-specific distribution; this task measures flat sideloaded APKs only.

## Real private data: unchanged before/after

| Category | Logical bytes | Allocated file bytes |
|---|---:|---:|
| Model |183,816,181|184,000,512|
| Saved animated artwork (3 videos) |104,266,525|104,386,560|
| DBs/WAL/SHM (Room/WorkManager/transport) |1,804,640|1,806,336|
| VYBE index |642,355|643,072|
| Lyrics |316,336|409,600|
| CE cache files |143,352|163,840|
| DataStore/preferences |5,106|16,384|
| Other small files/logs |1,345|16,384|
| Total |290,995,840|291,442,688|

CE du including directories291,511,296. Excluding CE cache/code-cache directories gives291,321,856, within2KiB of framework non-cache data. DE root1721KiB includes OpenGL1196KiB and Skia500KiB shader caches; external app directory15KiB. Directory/quota attribution differs between du and framework accounting; shader caches explain most CE/framework residual. No cache cleared.

Model `files/vybe/embedding_gemma.task`; vectors `files/vybe/index.bin`. `no_backup/artwork/animated/` contains SOS tall57,963,625, SOS square28,189,727 and Graduation square18,113,173 bytes. These are saved content, not disposable cache. Model/index/video hashes unchanged. No duplicate model, abandoned large .part, large logs or bloated duplicate index found. **Applied persistent-data saving:0 bytes.**

## Current delivery

`VybeEngine.importModel` takes SAF-selected weights, copies via `.part`, renames and loads locally. There is no network downloader/versioned download recovery. ModelID includes embeddinggemma-300m-int4int8/document version; normalized768d retrieval vectors, serial background processing, local208-record index. Runtime unloads after leaving VYBE; cached vectors remain usable. Offline model/index behaviour was preserved.

## Candidate measurements and tradeoffs

S23 standalone process per model, four synthetic English docs/four queries/12 warm queries. **Not representative music-quality evaluation.** MediaPipe defaults; ORT4 intra-op/1 inter-op threads. MiniLM inputs pretokenized on PC from downloaded WordPiece vocabulary: timings exclude tokenizer latency, include inference/pooling/normalization. No matched thermal/thread comparison. Sampled PSS includes process/runtime/shared mappings and is **not exact peak RAM**.

| Model | Weights bytes | Dimensions | Load+first query ms | Warm median ms | Sampled max PSS KiB | Expected top matches |
|---|---:|---:|---:|---:|---:|---:|
| Current Gemma int4/int8 |183,816,181|768|1335|621.5|293,069|4/4|
| USE QA float32 |6,120,274|100|91|16|71,215|3/4|
| MiniLM L6v2 ARM64 INT8 |23,026,053|384|277|2|95,108|4/4|

**A. Keep external model, improve delivery.** APK/data saving0; initial download183.82MB plus temporary replacement space. Offline/current quality retained. Future pinned length/hash, versioned IDs, atomic validation, cancellation/recovery and index invalidation are moderate work. No downloader was implemented, so no new recovery path exists to test. Original weights use [Gemma terms](https://ai.google.dev/gemma/terms).

**B. Smaller embeddings.** USE saves177,695,907 weight bytes plus about0.556MB of this index; APK runtime could remain MediaPipe. It missed even the night-driving fixture: no migration recommendation. [MediaPipe guide](https://developers.google.com/edge/mediapipe/solutions/text/text_embedder) supports current mixed-quantized Gemma and USE. [Chromium's upstream model record](https://chromium.googlesource.com/chromium/src/+/refs/tags/124.0.6367.150/third_party/tfhub_models/README.chromium) records Apache2 for QA-ondevice; archive exact artefact licence before redistribution.

MiniLM INT8 saves160,790,128 weight bytes; tokenizer adds466,247. Official FP32 ONNX is90,405,214, so INT8 saves67,379,161 against FP32 (FP32 not benchmarked). [Owner's card](https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2) specifies Apache2, English384d,256-wordpiece truncation, mean pooling and normalization. Android was tested using ORT1.31.1 (MIT). Its ARM64 natives total34,331,256, **4,318,456 larger** than current MediaPipe natives; net APK delta also needs Java/tokenizer/R8 measurement. [ORT Android/custom builds](https://onnxruntime.ai/docs/tutorials/mobile/) are supported, but example runtime sizes are not MiniLM measurements. Medium/high work: tokenizer parity, new identity/reindex, release compatibility, offline setup and music/multilingual evaluation. No app dependency/migration applied.

**C. System-managed embeddings.** [ML Kit Embedding](https://developers.google.com/ml-kit/genai/embedding/android) really supplies semantic sentence embeddings: developer preview `genai-embedding:1.0.0-alpha1`, AICore-managed EmbeddingGemma2,768d/256-token input. Requires eligible device, preview account/APK and latest Private Compute Services. S23 had no installed AICore; availability not demonstrated. New weights require reindex/quality testing; quotas/background/version changes complicate guarantees. Moving bytes to system/shared storage does not eliminate physical storage. Download/APK delta/latency/RAM unmeasured; high compatibility/maintenance risk. No account enrollment/install attempted; preserve current fallback.

**D. Quantization/dimensions/runtime.** Current weights already int4/int8. Output scalar quantization does not shrink weights. [Original Gemma card](https://ai.google.dev/gemma/docs/embeddinggemma/model_card) supports768→512/256/128 Matryoshka output with renormalization. At208 records,128d saves532,480 vector bytes; INT8 storage at768d saves479,232, excluding format changes. Neither removes184MB weights. Both need format/version/scoring changes and relevance validation; not applied. Custom native-runtime slimming remains unmeasured, higher maintenance.

**E. Metadata-only/hybrid.** Offline metadata needs no model download. A toy PC token-overlap baseline matched4/4 trivial fixtures in0.109ms: evidence this fixture is weak, not semantic equivalence or Android latency. Production synonyms/moods/multilingual relevance and RAM remain unmeasured. Removing model/index could save184,458,536 logical data bytes and possibly MediaPipe packaging but changes features. Hybrid retaining semantic search saves no stored model bytes; may avoid loads for exact matches. No substitution/ranking change made.

Future savings should prioritize optional saved-artwork management (104.27MB here, removal changes offline content) and a representative judged model benchmark, not tiny DB/vector cleanups. No quality-preserving model replacement has been established.

## Evidence and commands

Raw output root: `C:\Users\kshit\Documents\Codex\2026-10-14\d-personal-projects-empee3\outputs\size-forensics`. Includes APKs; release/debug/build logs; `measure.py`, `commands.txt`, `*-before/after.json`, `package-equivalence.json`, `native-dependencies.json`, `native-debug-sections.json`, `model-*.json`, `metadata-fallback-smoke.json`, download sizes/URLs, probe source and research fixtures. APK models downloaded only to research tooling, never production. Duplicate scanner skipped sensitive-name entries; token contents were not printed.

```powershell
adb shell pm path com.lochanpthakke.empee3
adb shell dumpsys package com.lochanpthakke.empee3
adb shell run-as com.lochanpthakke.empee3 du -ak .
adb shell run-as com.lochanpthakke.empee3 du -ak /data/user_de/0/com.lochanpthakke.empee3
adb shell run-as com.lochanpthakke.empee3 sha256sum files/vybe/embedding_gemma.task files/vybe/index.bin no_backup/artwork/animated/*.mp4
python measure.py apk release-universal-before.apk release-before
python measure.py apk release-arm64-after.apk release-after
python measure.py device data-before
python measure.py device data-after
```

StorageStats used a temporary no-INTERNET helper with GET_USAGE_STATS app-op, no empee3 permission changes. Temporary `.bench` received a selected backup copy of DB/DataStore/model/index and media-read access; real app was not force-stopped/reinstalled/opened. Exact research download URLs/lengths and model harness are retained for reproducibility. No full-library candidate quality, sustained indexing, energy, exact peak RAM or real Play download benchmark was performed.

## Build/test and device verification results

- `:app:assembleRelease` universal: PASS,16m34s; ARM64 release: PASS,1m17s.
- `:app:assembleDebug -PbenchApp -PtargetAbi=arm64-v8a`: PASS,1m19s; used only to seed separate `.bench` data.
- `:app:assembleBenchmark` universal: PASS,7m25s; ARM64 benchmark: PASS,26s. Benchmark APKs136,724,033 and37,166,035 bytes, same four→one ABI change; extra44 bytes versus real APKs reflect separate package packaging.
- `:core:vybe:test :core:vybe-mediapipe:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` (normal universal package, in-process Kotlin): PASS,35s. VYBE34 tests, app1 test, zero failures/errors; MediaPipe unit task NO-SOURCE. Actual native model tests ran in physical-device helper, not claimed as this Gradle unit task.
- `help -PtargetAbi=mips`: expected configuration rejection in3s, explicit unsupported-ABI error. `git diff --check`: PASS (line-ending notices only). No new test mimics the Gradle implementation; package byte equivalence and actual install/native operation are the meaningful regression checks.
- Physical S23 universal release: preserved208-song index, 30 results, search639ms (typed query was `ate night music`; first character was lost during IME startup). ARM64 release: predefined `Dark, atmospheric hip-hop`,30 results,622ms. Full force-stop/restart then re-enter VYBE:208 songs, same query30 results,609ms. These are functional checks, not a claim of performance improvement.
- ARM64 playback in `.bench`: Addiction, active item6 (queue position7/208), Media3 PLAYING, buffered audio, then PAUSED with no error; position advanced1921→12340ms. Home and VYBE navigation/core playback were exercised, not an exhaustive full-app regression run. No audio-quality listening test claimed.
- The model copy used by the harness has the same SHA256 as the real app. Model/index/three video hashes match the pre-task backup (`production-content-integrity.json`); file sizes and real StorageStats remained identical. Initial shell wildcard hashing of videos failed; corrected comparison used explicit file names, not a missing wildcard result.
- Both temporary packages (`.bench`, `.storageprobe`) uninstalled after tests, deleting their copied models/data. Task-owned `/data/local/tmp/empee3-size-seed.tar` and `/sdcard/empee3-size-ui.xml` removed. Final package inventory contains only the real empee3. Phone returned to Home. Real app lastUpdateTime remains21:05:57; no real-app install/data restore or cache clearing. No global settings changed.
- Optimized real APK SHA256: `398141e81d8735efc93dac881fe8dbdf2e2900d0c21460f5c66ff6c12d184f97`; universal baseline `81a6c2bacc2d5e41635362fbe49c6c2aacbdea7f9353ee6fe882a46ccf9279b5`.

Remaining limitations: exact peak memory, representative music/multilingual quality, full indexing throughput, production tokenizer performance for MiniLM, system-managed-model availability on S23, AAB/Play transfer and distribution signing are not established by these tests. No model replacement is recommended without that evaluation. At the end of the initial forensics task, the main app was still the debug build. The separately authorized installation below supersedes that installation status.

## Authorized installation on the real S23 — 2026-10-28

Completed installation at **22:26:39 IST**; report finished October 29. This follows the user's explicit request to update the daily app and idle confirmation. Repository instructions/status/diffs were inspected first. Pre-existing tracked Home, Audio Details and Gradle source diffs were compared before/after and are unchanged. No application source edits, dependency/model/signing changes, commit or push were performed for installation.

### Identity and backup

Rebuilt using `:app:assembleRelease -PtargetAbi=arm64-v8a '-Pkotlin.compiler.execution.strategy=in-process'`: **PASS, 6 seconds**, 480 tasks (one executed, 479 up to date). Archived and inspected the fresh output, rather than relying on a previous artifact name.

Installed-before, built release and pulled installed-after APKs all verify with certificate SHA256:
`5bb0fc457b96d9fb6cbc606009de44d633bee95b58fec2dc633a7a4eb54a9ce5`.
Package remains `com.lochanpthakke.empee3`, versionCode1/versionName1.0. Installed-after has only `arm64-v8a`, is not debuggable, and matches release SHA256 `398141e81d8735efc93dac881fe8dbdf2e2900d0c21460f5c66ff6c12d184f97`. The same hash and update timestamp were reconfirmed read-only October29. Original firstInstallTime remains October14, proving this was not an uninstall/reinstall.

Current base APK path:
`/data/app/~~sV3tq-7zxwq_odEGsk5qyg==/com.lochanpthakke.empee3-TGFMjALiOmgcSNeoWlJfQw==/base.apk`.
No split APKs. Install command was `adb install -r release-arm64.apk`: **Success**.

Before updating, playback was confirmed paused at1921ms; the process was stopped for a stable backup. Full CE backup and original APK saved to `D:\android-toolchain\dbbackup\pre-arm64-release-20261028\`. Tar is **291,389,952 bytes**, 77 files, every member read to validate completeness. SHA256 `2b3294aa580d385af6c956891976010a476d24bb65747818132450e82dff1ddf`. No data restore, clearing or user-content deletion was needed.

### Exact StorageStats results (bytes)

| Category | Before update | Immediately after update | After requested tests |
|---|---:|---:|---:|
| App |163,215,872|50,054,656|50,054,656|
| Data including cache |293,560,320|293,523,456|293,810,176|
| Cache subset |1,908,736|1,908,736|2,805,760|
| Non-cache data |291,651,584|291,614,720|291,004,416|
| App + all data |456,776,192|343,578,112|343,864,832|

**Installed app saving:113,161,216 bytes (113.16 MB). Final total saving:112,911,360 bytes (112.91 MB).** This is a debug-universal → release-ARM64 update, combining existing release shrinking and removal of other CPU libraries. It is distinct from the 99.56MB release-universal → release-ARM64 comparison in the original report.

APK length37,165,991 versus App accounting50,054,656 leaves **12,888,665 bytes** of platform compiled/installation/allocation accounting. `pm art dump com.lochanpthakke.empee3` confirms an ARM64 `oat/arm64/base.odex` artifact with install verification status. Android App bytes include optimized code and installation overhead, not just the base.apk. Exact per-file attribution of all platform-managed bytes was not available; no fabricated breakdown. The table uses framework accounting consistently and does not add cache twice.

The small data changes are not model savings. Playback/search writes normal state/history and cache; database WAL changed976,472→341,992 bytes during testing/checkpointing. No manual cleanup was performed. The immediately-after-update comparison found no changed raw files in the protected snapshot.

### Data integrity and functional checks

A temporary no-network instrumentation helper, signed with the same pre-existing key, queried StorageStats and read retained files under the real app UID. This avoided changing the release back to debug merely to use run-as. It hashed model/index/artwork/DB/preferences and opened the main DB read-only for quick_check, version, row counts and deterministic per-table row digests. No app dependency/permission was added. This was a read-only test runner, not a Macrobenchmark/Perfetto trace.

Immediately after update: **all eight model/index/artwork/preferences files and all database files were byte-identical**; all database table digests matched. SQLite quick_check=`ok`, version14,208 songs. In particular the playback/theme/settings proto was identical before any UI tests. The backup-to-baseline comparison also passed, including after the helper correction described below.

After tests: model, index and all three animated artworks remain byte-identical; songs and every table other than listening_stats/recently_played retain identical logical digests. Those two tables changed through the requested playback test. Playback DataStore and Android ActivityThread IDs changed during normal use; no preference reset or migration was performed. SQLite quick_check remains `ok`. No claim that a playback test leaves playback state/history byte-identical.

Actually exercised on the real app:
- Home loaded the preserved Featured Setlists; Home → Albums → Home navigation passed.
- VYBE loaded the existing208-song index; `Dark, atmospheric hip-hop` returned30 results,659ms.
- Full process stop/relaunch, re-enter VYBE: model loaded again, same query30 results,653ms.
- Play/Pause on Addiction, same active queue item6 (position7/208). MediaSession showed PLAYING, buffered audio and no player error; pause advanced1921→14576ms. Left paused; no queue replacement or song skip. Audio listening quality was not assessed.
- Per-process Logcat from normal startup/search/restart/playback contains no fatal exception, UnsatisfiedLinkError, MediaPipe loading failure or ExoPlaybackException. It does contain a nonfatal `libpenguin.so` dlopen warning during device surface setup on both launches. Neither original nor optimized APK contains that library; its absence was not introduced by filtering APK ABIs. No unsupported claim that every native warning is absent.

**Checker failure and correction:** its first version suppressed Application.onCreate to avoid startup writes. WorkManager's RescheduleReceiver then failed because Hilt's workerFactory was uninitialized. The original debug app was still installed and the validated backup already existed. The checker was corrected to permit normal initialization and rerun successfully before installing the release. Protected baseline bytes matched the backup afterward. This was a test-harness failure, not an ARM64 release startup failure; no app code/signing/data workaround was made.

Cleanup: the temporary `com.lochanpthakke.empee3.installcheck` package was uninstalled; task-owned phone tar/XML removed. Only the real empee3 package remains. No user-content deletion, uninstall of real app, signing change, model migration or unrelated refactor. App was reopened after verification, paused.

Evidence folder: `C:\Users\kshit\Documents\Codex\2026-10-14\d-personal-projects-empee3\outputs\arm64-install`. Contains before/release/after APKs and certificates, backup validation, integrity JSON/raw reports, per-table comparisons, UI dumps, playback session dumps, scoped Logcat, ART report, build/install logs and source-preservation check. The full backup remains outside the repository. Original source work remains uncommitted. No extra unit tests were rerun for this installation-only task; relevant 35 tests and device release smoke tests from forensics are recorded above.
