# Storage lifecycle audit — 2026-10-29

Audit only. No application source/configuration changes, installation, cache clearing, production deletion, model/index regeneration, commit or push was performed. **Recommended next implementation phase: opt-in lyrics downloads with conservative handling of existing saved lyrics.** Automatic database/index cleanup must wait for scan-completeness and index-recovery safeguards.

## Evidence and preservation

- **SOURCE** means inspected at HEAD `b5fcd1308fcb9a655d638dd01c74dda0927bed66`, branch `kshitij`, tracking `origin/kshitij`. Initial staged diff, tracked diff and non-ignored untracked inventory were empty. Home/Featured Setlists, Audio Details and ARM64 Gradle changes are already committed in this HEAD; older notes describing them as uncommitted are historical.
- **DEVICE** means read on the connected SM-S911B/S23, serial `RZCXA1H43GW`, on October 29, approximately 20:30–20:45 IST. No app UI input, launch, force-stop, playback test or scan was initiated.
- **SNAPSHOT** means measured afresh from the existing PC backup `D:\android-toolchain\dbbackup\pre-arm64-release-20261028\appdata.tar`. It is a pre-update October 28 snapshot, **not a fresh inventory of today's private files**. SQLite analysis used an extracted PC copy with its WAL, never the phone database.
- **HYPOTHESIS / PROPOSAL** explicitly identifies inference or future work. `docs/app-size-and-storage.md` was used as historical context, not current proof. Neither that file nor `claudeinstructions.md` was edited.

Evidence is outside Git at `C:\Users\kshit\Documents\Codex\2026-10-29\d-personal-projects-empee3\outputs\storage-audit`: `current-stats.txt`, `package.txt`, `art.txt`, `device-accounting.txt`, `certificate.txt`, `current-audio-files.txt`, `historical-snapshot-analysis.json`, `baseline-category-bytes.json`, and `video-frame-counts.json`. Scratch tooling/media copies are under this chat's `work/storage-audit`, outside the application source tree. A shell-UID Java reader queried StorageStats through Android APIs without installing an APK or starting empee3. Its temporary `/data/local/tmp/empee3-storage-audit.dex` was removed.

## 1. Current storage and installed identity

### Current framework accounting — DEVICE

All MB below are decimal. Cache is a subset of Data; do not add it twice.

| Category | Exact bytes | Decimal MB |
|---|---:|---:|
| App | 61,285,888 | 61.286 |
| Data, including cache | 293,568,512 | 293.569 |
| Cache subset | 2,215,936 | 2.216 |
| Data excluding cache, calculated | 291,352,576 | 291.353 |
| App + Data, calculated | 354,854,400 | 354.854 |
| Base APK logical length | 37,165,991 | 37.166 |

This reproduces the reported 61.29 MB App, 2.22 MB Cache and approximately 355 MB Total. A Settings Data display around 292 MB can reflect its presentation/exclusion of cache and rounding; the exact API values above are the consistent baseline. Do not compare a rounded screenshot value with a different category as if it were a byte-exact measurement.

Installed package: `com.lochanpthakke.empee3`, versionCode `1`, versionName `1.0`, primary ABI `arm64-v8a`, no secondary ABI, only base APK, `extractNativeLibs=false`, not debuggable. Last update remains **2026-10-28 22:26:39 IST**.

- Pulled installed APK SHA-256: `398141e81d8735efc93dac881fe8dbdf2e2900d0c21460f5c66ff6c12d184f97` — exactly the recorded ARM64 release.
- `apksigner verify --print-certs` certificate SHA-256: `5bb0fc457b96d9fb6cbc606009de44d633bee95b58fec2dc633a7a4eb54a9ce5` — unchanged.
- Base APK `stat`: logical 37,165,991 bytes; 72,672 512-byte blocks = 37,208,064 allocated bytes.
- StorageStats APK subtype: 37,165,991 bytes; dex-metadata subtype: 0; extracted-native-library subtype: 0. APK-contained native libraries still occupy APK bytes; zero extracted libraries does not mean no native code.
- External app directory `du`: 15 KiB including directories, files subdirectory 8 KiB. Individual external file inventory was not accessible through this shell listing.

### Why 50 MB became 61.29 MB

**Verified facts:** Immediately-after-update App was historically 50,054,656 bytes; now it is 61,285,888: **+11,231,232 bytes**. The APK bytes, certificate and update time are unchanged. Historical saved `art-accounting.txt` says `status=verify`, `reason=install`. Current `pm art dump` says **`status=speed-profile`, `reason=bg-dexopt`**, with:

```text
.../oat/arm64/base.odex
.../oat/arm64/base.art (required)
```

The current App-minus-logical-APK residual is **24,119,897 bytes**, versus the historical 12,888,665. This residual includes platform artifacts/allocation accounting; it is not all proven to be one `.odex` file. Android documents that App accounting includes optimized code, while ART compiler filters determine compilation. [StorageStats API](https://developer.android.com/reference/android/app/usage/StorageStats), [ART configuration](https://source.android.com/docs/core/runtime/configure/art-service).

**Evidence-supported inference:** Background ART compilation is the leading explanation for the increase: its state changed, while the packaged application did not. **The exact 11,231,232-byte increase cannot yet be attributed file by file.** Shell `ls/stat` of `oat/arm64` returns Permission denied. `getAppBytesByDataType` for dexopt/current-profile/reference-profile triggers a separate ART accounting request and is denied to shell by usage-stats AppOps. Aggregate StorageStats and APK subtype succeed. The initial reader failures were shell-reader exceptions, not empee3 crashes; the final reader catches those denials. No AppOps/permissions were changed to bypass them.

Missing evidence: current and installation-time allocated/logical lengths for `.odex`, `.vdex`, `.art`, reference/current profiles and installation-directory residuals. There is no saved installation-time per-file inventory to reconstruct an exact growth delta. Do not force recompilation or delete runtime artifacts to manufacture an A/B test. Android manages those files.

### Private-data integrity limits

**DEVICE:** All **208 audio paths** from the backup's songs table still exist and are readable to shell; current combined logical size is **2,460,599,234 bytes**. This establishes existence/size, not byte-identical audio or current app-UID access. Package permissions include the media grant; no permissions were changed.

**Not currently verified:** today's model, index, three saved video hashes, private preferences, DB row digests, CE/DE cache inventory and private temporary files. Release `run-as` returns `package not debuggable`; private CE/DE paths are permission denied. Aggregate data remaining around 293.57 MB is not an integrity proof. Historical after-update integrity records say these assets survived that update, but cannot prove their state today. No fresh private backup or invasive instrumentation was performed: starting target-app instrumentation would run application initialization and could write state, violating the strict read-only baseline.

The release itself and all historical audio paths remain intact by the checks available. A claim that every private file is currently unchanged would exceed the evidence.

## 2. Private storage inventory and lifecycle

The following exact logical sizes are **SNAPSHOT**, freshly totaled from the pre-update backup. Today's private category sizes are blocked; only the aggregate table above is current. The backup's DB/WAL total differs from the older report's 1,804,640-byte measurement because it is a different snapshot/checkpoint moment.

| Category / snapshot size | Owner, purpose and classification | Growth, staleness and present policy | Safe management proposal |
|---|---|---|---|
| Model: 183,816,181 B | `VybeEngine`, user-imported essential offline model | Changes only on import; no TTL; import `.part` then rename, no pinned digest validation | Retain; validate identity/hash on explicit import; never automatic eviction |
| VYBE index: 642,355 B | `VybeIndexFile`, derived but valuable offline semantic vectors | Appends changed/new documents; conditional compaction; model/document header invalidation | Retain existing; harden recovery before event-driven reconciliation |
| Animated videos: 104,266,525 B | `ArtworkFileStore`, explicitly saved offline content | Downloads/replacements grow it; no total limit or expiry | Explicit per-orientation management; preserve until user removal |
| Online lyrics: 316,336 B, 48 JSON files | `FileOnlineLyricsCache`, presently permanent fetched content | FOUND/INSTRUMENTAL no TTL; NOT_FOUND expires logically after seven days but file stays; metadata mismatch ignored, not removed | Split new transient display from explicit downloads; preserve legacy offline content until deliberate disposition |
| Main DB + WAL + SHM: 1,480,280 B | Room `empee3.db`: metadata plus essential user/history data and derived rows | Scans, playback, bookmarks and waveform requests write; no general vacuum/expiry | Reconcile selected derived rows after complete scans; never delete DB/WAL by filename |
| WorkManager DB + WAL + SHM: 592,496 B | `no_backup/androidx.work.workdb`, framework work scheduling | Jobs/history grow and library prunes finished eligible work | Use framework policy; no manual DB deletion or routine `pruneWork()` without work-retention review |
| Transport DB: 57,344 B | `com.google.android.datatransport.events`, runtime/dependency event transport | Library-owned event queue; lifecycle not an app-owned cache contract | Do not delete by name; inspect active backend/event retention before altering dependency behavior |
| CE cache: 143,352 B | Image cache/journal and zero-byte SQLite lock files | Image loading; no custom application disk budget found | Keep image library eviction; observe effective library budget before changing it; do not treat lock files as stranded downloads |
| Preferences/DataStore: 5,106 B | Playback/settings proto 4,631 B; Glance 367 B; shared prefs 108 B | Overwrites queue/settings/widget state | Essential, retain; no TTL |
| Other: 1,345 B | Two crash logs 1,313 B plus profile-installer markers 32 B | Crash logger trims to newest 20 files, not a byte cap | Keep existing count cap; optional byte cap only after actual log-size measurement; retain runtime markers |
| Artist/playlist custom images, themes/skins | User-owned files under `files/artist_artwork`, `playlist_artwork`, `themes`, `skins` | Explicit create/import/change; not present in this backup file inventory | Retain referenced user assets; no current-zero claim |
| DE storage/shader caches | Android renderer/runtime | Not accessible now; earlier report recorded about 1,721 KiB, historical only | Android managed; never blind deletion |
| Shared music / transfer destination | User-owned audio, outside private App Data | Successful Wi-Fi uploads publish into `Music/empee3/` | Retain published audio; partial cleanup only with ownership/operation proof |

No abandoned large private temporary file was found **in the backup**. Current private leftovers remain unmeasured. No current stale record or disposable-byte total has been established. Applied storage saving throughout this audit: **0 bytes**.

### Room and duplicated representations — SOURCE + SNAPSHOT

`core/database/.../EmPee3Database.kt` is schema **14**, not the stale version 11 in the instructions' introduction. `di/DatabaseModule.kt` registers migrations 5→14 and destructive fallback for versions 1–4 and downgrades. A downgrade is not a cleanup technique.

Snapshot SQLite `quick_check=ok`, `foreign_key_check` has no findings, 115 pages, zero freelist pages. Table counts: songs 208; albums 15; artists 13; playlists 0; song credits 202; album credits 14; artist_artwork 26; aliases 0; unresolved credits 27; recently_played 15; listening_stats 69; listening_days 5; bookmarks 1; waveform_cache 28; metadata_processing 0; learned_watermarks 0; animated_artwork 4. All SQL/schema/index definitions are captured in the analysis JSON. No unnecessary index is demonstrated. The artist/album join indexes, playlist and bookmark song indexes, monthly statistics index, recent-play timestamp index and unique normalized animated-artwork key serve real query/identity paths.

- Room's song row is the app's local metadata model, not a second audio file. Albums, artists and credit cross-references support grouping and queries. MediaStore remains the system catalog. `ListeningHistoryRepository`, Smart Mixes and Radio read these rows; their in-memory projections are not extra persistent databases.
- `JAudioTaggerEnricher` copies embedded lyrics into `songs.lyrics`; `observeEmbeddedLyrics` renders that copy, and VYBE reads only an excerpt. Snapshot has 45 non-null lyric rows totaling 114,840 UTF-8 bytes. This intentional tag/Room duplication supports offline rendering; deleting it would change behavior and require rereading tags.
- `artist_artwork` persists across scans by canonical artist key, including automatic and custom selections; artists may disappear temporarily or from the current filter. Its 26 rows versus 13 current artists does **not** establish 13 disposable rows. `ArtistArtworkRepository`, `ArtistArtworkStore` and `ArtistArtworkBackupManager` use these records and custom images. Playlist custom artwork is removed on playlist deletion/replacement through `PlaylistRepository`/`PlaylistArtworkStore`.
- Favorites, playlists/positions, bookmarks, alias mappings, settings, listening statistics and listening-day history are user state. Monthly song stats and daily aggregate counters grow with listening; no retention TTL was found. A UI query `LIMIT` is not storage eviction.
- `WaveformRepository` decodes on demand, serialized with SingleFlight. `WaveformCacheStore` stores one byte per envelope bin in Room, keyed by song ID and validated against URI, size, modification time and format version. The 16-entry three-band window cache is memory-only. `waveform_cache` has no song foreign key/automatic eviction; its remove API alone does not prove scan cleanup is called. Potential orphan rows require a confirmed library removal before deletion.
- SQLite WAL/SHM are live transaction infrastructure. Room checkpointing/reuse can change their file lengths without deleting application data. Do not manually remove them or assume their bytes are duplicate songs. Snapshot freelist=0 offers no demonstrated vacuum saving.
- Artwork bitmaps use `ArtworkLoader` memory LRU; palettes and generated Home art also have memory caches. They are not persistent video copies. Coil `AsyncImage` is used for artist images; no application-owned explicit Coil disk-size configuration was found. The backup has a small `cache/image_cache` journal and three image pairs. Do not invent an effective disk quota without inspecting the runtime/library configuration.
- Tag editing stages full audio backup + edited copy under `cache/tag-edit`; a `finally` deletes both during normal completion/failure. Process death can bypass it, and those originals must be treated as possible recovery files until an operation manifest proves write-back completed. Theme imports use `theme_import_temp_*`, removed by normal success/error paths; abrupt death can leave them. No periodic stale-file scavenger was found for these paths.
- Wi-Fi transfer uses pending MediaStore records on modern Android, hidden `.part` files on legacy Android. `MediaStoreUploadSink` aborts unfinished uploads; `WifiTransferCoordinator` calls leftover cleanup on server startup and shutdown. Modern leftover query is scoped to `Music/empee3/` and pending state, but lacks an explicit owner/session/age predicate in the query. Add those checks before expanding cleanup; never delete published audio. Android 10 pending-expiry behavior is assumed by the source comment, not tested here.

## 3. Animated artwork: files, callers and compression feasibility

### Measured streams — SNAPSHOT, not current private-file proof

FFmpeg 7.1 metadata inspection and complete decode-to-null were run on extracted **unchanged** backup copies. No file was transcoded, remuxed, normalized or replaced. All three contain one video stream and **no audio stream**.

| Asset | Bytes | Container / codec | Resolution / pixel format | Frame rate / decoded frames | Decoded content duration | Reported stream bitrate |
|---|---:|---|---|---|---|---:|
| SOS tall | 57,963,625 | Fragmented ISO BMFF MP4, HEVC `hvc1` Main 10 | 2048×2732, 10-bit 4:2:0, BT.709 limited | 24 fps / 551 | about 22.958 s | 20,194 kb/s |
| SOS square | 28,189,727 | Fragmented MP4, AVC `avc1` High | 1080×1080, 8-bit 4:2:0, BT.709 limited | 24 fps / 551 | about 22.958 s | 9,819 kb/s |
| Graduation square | 18,113,173 | Fragmented MP4, AVC `avc1` High | 1080×1080, 8-bit 4:2:0, BT.709 limited | 30 fps / 450 | 15.000 s | 9,656 kb/s |

Important timestamp distinction: backup SOS tall advertises container duration 32.96 s with start 10.041667 s; its actual 551-frame sequence is about 22.958 s. Square starts 0.041667 s with container duration 22.96 s. Do not infer missing footage or a real 33-second content sequence from the tall container duration. Decode-to-null rounded SOS elapsed time to 22.95 s. File-total bitrate using 551/24 seconds is approximately 20.20 Mb/s tall and 9.82 Mb/s square; container-duration bitrate for tall is misleadingly lower because of the timestamp offset. Current files may have been timeline-rebased after the backup; their exact current hashes/timestamps are unavailable.

Tall and square have different geometry, codec, bit depth and bytes. Equal frame count does not prove equal framing/composition. **They are not duplicate files and neither should be automatically removed.** Both orientations are supported, with fallback to the other if only one is saved. Whether the user wants both is a content choice, independent of compressibility.

### Complete application pipeline — SOURCE

1. `feature/library/LiveCoverViewModel.kt` offers album-page check/download/cancel/remove. `AnimatedArtworkViewModel.kt` provides the fetcher/manual variant workflow; `AnimatedArtworkScreen.kt` displays selected/preview art. No standalone local-video import API was found in these artwork paths.
2. `core/database/.../AnimatedArtworkRepositoryImpl.kt`: `fetchAndStoreArtwork` and `fetchAndStoreArtworkByUrl` deduplicate by album+orientation through `singleDownload`; `downloadAndSaveVariant` is a separate direct path without that shared job guard. Both produce filename/state rows in `animated_artwork`. `checkLiveCover` first checks saved file existence, then negative cache (NO_MATCH 14 days / NO_ANIMATION 60 days), then in-memory offers, then network. These TTLs concern discovery responses, not downloaded videos.
3. `core/network/.../apple/AnimatedArtworkNetworkDataSourceImpl.kt` resolves Apple catalog/storefront URLs and variant playlists; standard automatic selection uses preferred short edge (default 1080, optional Maximum/720/480/360). Manual variants can retain a much larger source, explaining why the old tall copy can exceed current default dimensions. This does not establish which historical UI action created it.
4. `HlsPlaylistParser` supplies segment/byte-range data; `HlsMp4Assembler` streams the original encoded fragments into an album/orientation `.mp4.tmp`. It checks cancellation, the 64 MiB download limit and ftyp/minimum length; encrypted/TS HLS is rejected. It then rebases fragment decode timestamps with `FragmentedMp4Timeline.startAtZero`. **This is assembly/timestamp editing, not video compression.** There is one completed MP4 per saved orientation, not a retained full original plus a transcoded derivative.
5. `ArtworkFileStore` stores under `context.noBackupFilesDir/artwork/animated`. Descriptive filenames use sanitized album/artist/orientation; names can collide after normalization or change when album metadata changes. The DB also records the saved filenames. Replacement deletes the destination then renames, with copy fallback: interrupted replacement can lose the old file or expose a partial destination. DB/file publication is not one transaction.
6. `components/AnimatedAlbumCoverPlayer.kt` reads local paths through ExoPlayer/TextureView. Now Playing's `CoverVideoHost` shares a player for its current album; album header releases its player while covered. Audio selection is disabled; memory target is 64 MiB. No `SimpleCache`/`CacheDataSource`, decoded-frame disk store or persistent transcoding path was found. Codec/surface buffers are runtime memory. **There is one other write path:** the renderer calls `FragmentedMp4Timeline.startAtZero` on legacy saved files, changing only tfdt bytes in place when needed, not creating a second copy.
7. `deleteArtwork` removes both generated-name candidates and stored square/tall filenames, then deletes the row; removal is currently album-wide, not an independent orientation control. `deleteAllArtwork` recursively clears this store plus rows. Neither deletion path waits for active download completion; direct manual downloads can race management operations. Offers also need invalidation when downloaded content changes.
8. Normal assembler failure/cancellation removes the temporary file. `cancelDownload` cancels tracked album jobs; cancellation cleanup removes album-prefixed `.tmp` files and adjusts DOWNLOADING state. `cleanupOnStartup` resets DOWNLOADING rows and deletes all `.tmp` files, but its only production caller found is **AnimatedArtworkViewModel initialization**, not unconditional application startup. Opening that screen during another live job can race it. Abrupt process death before this screen opens can leave `.tmp`/DOWNLOADING state. The manual variant path shares temp names without sharing `singleDownload` ownership.

Backup: saved animated art is in `noBackupFilesDir`, excluded from Android automatic backup/device transfer; custom full `run-as tar` backups include it. App manifest enables backup; extraction rules currently have no exclusions for `files/vybe` or online lyrics. Thus model/index/lyrics are eligible by default, although the model alone exceeds Android's 25 MB cloud Auto Backup quota; actual backup execution/upload was not inspected. Custom artist-artwork ZIP backup covers artist images, not animated video. [Android backup rules and exclusions](https://developer.android.com/identity/data/autobackup).

### Proposed high-quality compression experiment — not performed

Keep all dimensions, all frames, frame timestamps/cadence, orientation, aspect ratio and color tags initially. Preserve tall's 10-bit precision; do not silently convert it to 8-bit. Try offline PC software HEVC encoding with a slow preset and a small conservative CRF ladder (for example 16/18/20), treating these as **experiment settings**, not a quality guarantee. Square AVC→HEVC is a candidate; tall is already HEVC, so a meaningful saving is not assured. No denoising, artificial sharpening, frame interpolation, grain synthesis, scaling or frame dropping. Preserve intentional source texture. A fallback AVC candidate can be evaluated if broader decoder compatibility requires it. Verify the actual S23 hardware decoder, first frame, seeking and loop seam for every accepted candidate.

Compare decoded reference/candidate frames at identical presentation timestamps. Use native-resolution PNG crops and A/B/blink playback for SOS dark water/sky and gradients, detailed texture and edges, and Graduation flat colors, fine illustration lines and moving details. Include start/middle/end, scene transitions, highest-motion portions and the seam. Keep comparison bit depth/color space consistent; compare 10-bit samples before display conversion. Log per-frame PSNR/SSIM and worst frames, optionally VMAF with an appropriate model, but do not accept an encoding solely on an average score. Review full-motion playback on the S23, plus paused native-resolution crops. [FFmpeg comparison filters](https://www.ffmpeg.org/ffmpeg-filters.html), [x265 rate control](https://x265.readthedocs.io/en/master/cli.html#quality-rate-control-and-rate-distortion-options).

“Visually lossless” means no visible degradation under agreed viewing tests and is still potentially lossy. “Mathematically lossless” means identical decoded samples; an explicit lossless codec mode and sample/hash comparison are needed, and smaller output is not guaranteed. CRF 16/18/20 is not mathematical losslessness.

Measure exact original/candidate bytes and allocated bytes, frame count, duration, color properties and decoder behavior. Report savings separately for each file; total saving = sum(original minus accepted candidate). **No compression percentage or MB saving has been established here.** Future publication must stage/validate a candidate, preserve the original until replacement succeeds and avoid permanently storing both. Any later resolution reduction is a separate user-approved tradeoff, not part of the initial experiment.

## 4. Lyrics retrieval, persistence and the smallest safe change

### Existing flow — SOURCE

`NowPlayingLyrics` / `SongLyricsView` → `LyricsActions.onShown` → `LibraryViewModel.startLyricsLookup`. It reads Room embedded lyrics first; readable embedded text wins unless a cached `userChosen` online result overrides it. `LyricsModule` registers exactly **one online provider: LRCLIB**. There is no second provider fallback in the current list.

`OnlineLyricsRepository.lookup` checks metadata-matching cache, then LRCLIB exact lookup when artist/duration are available, then search unless the exact match is confident and word-timed. Matcher chooses confident results, offers plausible ambiguous results for user choice and rejects unsupported identity/version differences. Concurrent same-song lookups share a repository-scope request; cancellation of the screen wait does not cancel it.

`LrclibProvider` sends title/artist/album/duration, not audio; serializes requests with 400 ms spacing and respects rate-limit pause. `LyricsCandidate.bestText`: genuine word-synced enhanced LRC derived from `lyricsfile` when `hasWordSync`, else line-timed `syncedLyrics`, else plain text. `LyricsParser`/`LyricsContent` retain LRC offsets, line/word highlight and tap-to-seek using playback position. Display does not invent timing.

All current online-persistence writers found:

| API / caller | Persistence |
|---|---|
| `OnlineLyricsRepository.resolve` → `store` | Automatically writes FOUND or INSTRUMENTAL through `cache.put` |
| `resolve` no plausible match | Writes NOT_FOUND through `cache.put` |
| `choose` ← `LibraryViewModel.chooseOnlineLyrics` | Writes the chosen candidate, `userChosen=true` |
| `forget` ← `LibraryViewModel.useFileLyrics` | Removes song JSON |
| `cached` ← `startLyricsLookup` | Read only, checks override flag |
| `FileOnlineLyricsCache.put` | JSON to `files/online-lyrics/<songId>.json.tmp`, rename with delete-and-retry fallback |

A fresh lookup is therefore saved even when the screen closes before completion. FOUND and INSTRUMENTAL have no expiry/size budget. Seven-day NOT_FOUND TTL only stops reusing the answer; it does not remove the old file. Fingerprint includes normalized title/artist/album and duration in seconds; mismatches become misses, not cleanup. Cache is keyed by MediaStore numeric ID, with no scan reconciliation. Temp writes can survive process death; delete-before-rename fallback can lose old content. In-flight requests keyed only by song ID also need identity checks when tags change or removal overlaps a request.

`OnlineLyricsState.chooseLyrics` merges Room embedded text with the online result and labels `fromCache` as `ONLINE_SAVED`. That is a disk-cache state, **not proof of explicit download**. `OnlineLyricsUi` provides Refresh, Search online and Use the file's lyrics, with no dedicated Download/downloaded/remove-download contract. Offline viewing currently works because lookup results were automatically persisted.

There is no Room write or tag write in the online display chain. `songs.lyrics` is populated by `JAudioTaggerEnricher.updateEnrichedData` from embedded tags. `JAudioTaggerWriter` can write requested lyrics during an explicit metadata edit, but no online display API calls it. `MetadataRepositoryImpl.fetchLyrics` delegates to LRCLIB for the metadata API; repository-wide search found no production caller initiating that method. Preserve the separate explicit tag-edit capability.

### Actual stored duplication — SNAPSHOT

48 JSON files total **316,336 bytes**: 47 FOUND, one INSTRUMENTAL, one `userChosen=true`. Candidate fields contain 129,406 plain-text characters and 163,944 line-timed characters; word-timed field length is zero in this snapshot. Multiple timing/text representations within a result contribute to storage; they are not necessarily interchangeable for fallback/export. Room's embedded lyrics are a different provenance and cannot be declared duplicate online downloads based only on song ID. No byte-equality deduplication saving was established. This sub-MB footprint is a behavior/lifecycle issue, not the explanation for the App-size increase.

### Minimal implementation boundary — PROPOSAL

1. Keep provider, matcher, parser and playback clock unchanged. In `OnlineLyricsRepository`, stop permanent writes from ordinary lookup and candidate selection. Retain only current-view/session data plus in-flight sharing by full track identity. The smallest transient cache is memory-only: current song/result, cleared on replacement/ViewModel disposal; no new disk cache is required. Keep rate limiting and session negative results. If cross-session negative caching is retained later, isolate it in `cacheDir` with the existing seven-day TTL and bounded records, not download storage.
2. Introduce a distinct `DownloadedLyricsStore` using the existing JSON shape plus explicit state/schema/provenance and an atomic publisher. Store under a persistent downloads directory; never `cacheDir`. Repository APIs: read downloaded, download current validated candidate, remove downloaded. A displayed manual choice is not automatically a download. Guard full identity and operation generation around lookup/download/removal so an old request cannot recreate removed content or mark another track downloaded.
3. Extend `LyricsActions`, `SongLyrics`/`OnlineLyricsView`, `LyricsOrigin` and `OnlineLyricsUi` with downloaded state, Download and deliberate Remove download. Propagate through `LibraryViewModel`; Now Playing, expanded lyrics and queue lyrics must see the same state. Download errors/disk full must not show a false success state. Removing download must immediately fall back to embedded lyrics or a transient online view, without rewriting tags. Separate “prefer file lyrics” from deleting a download.
4. Read explicit downloads before network for offline use; retain metadata identity checking but do not silently delete a user's download after a retag/move. Mark association uncertain and provide remapping/removal. Preserve the selected timed representation and provenance; do not change word/line timing.
5. **Legacy handling:** existing JSON has no explicit-download flag. `userChosen` records a match choice, not download consent. Do not delete 47 automatic entries or relabel the one chosen entry as explicit. Retain existing entries as “Previously saved lyrics” and offer explicit keep/download or remove, with an optional deliberate bulk action. New online views follow opt-in immediately. This protects previously available offline text without continuing automatic permanent storage.

Expected measurable result: opening/refreshing/selecting new online lyrics creates **zero new persistent download files**; an explicit Download creates one validated durable record; offline reads work after restart; Remove deletes only that record. No current-content space saving is assumed. Avoid a Room migration for this change unless implementation evidence requires one.

## 5. VYBE correctness and scan reconciliation

### Present behavior — SOURCE

- `VybeText.describe` embeds known artist, all genres, plausible BPM (20–400) and a processed lyrics excerpt. Title and album are intentionally excluded from the embedded description; exact title/artist/album routing uses `VybeQueryRouter`. Year is carried in `SongText` but not described. `SongDao.observeSongTags` reads at most the first 600 lyric characters; online JSON lyrics do not enter this query.
- Identity in `VybeIndexFile`: numeric song ID + FNV-1a description hash + normalized 768-dimensional float vector. No URI, path, audio copy, raw lyrics or full document text is serialized. Song ID/description hash are necessary for linking and invalidation, not gratuitous metadata duplication.
- MediaStore `_ID` is the Room primary key, URI is stored in Room. Renames/moves retaining ID and unchanged description need no new vector, but `planLibrarySync` considers path/modified-time changes grounds for reenrichment. ID-changing moves/reimports become a new song; old IDs can remain physically in the append log. IDs alone are not durable cross-provider/file identity. Scanner deduplicates numeric IDs across enumerated volumes, so different-volume collisions are a source-level risk, not a measured duplicate on this S23.
- On opening VYBE, the engine observes Room song tags, distinct/debounced 2 seconds, on one background executor. It embeds only missing/description-changed records, groups identical descriptions for one inference, appends batches of 8. A normal rescan still reads/rebuilds the library model and upserts all scanned songs, but **does not necessarily re-embed every vector**.
- Removed/changed entries are filtered against current library IDs+hashes before ranking; physical old records remain. `reindex` compacts only when record count exceeds `2 * wanted.size + 64`. At 208 wanted songs, trigger is **more than 480 records**. Repeated edits can grow the file before that threshold; compaction is not a fixed-time task.
- Model identity is the fixed string `embeddinggemma-300m-int4int8|doc1`, plus format version and dimension. Document-version/format/model-ID mismatch makes reads incompatible; the next nonempty append truncates/replaces incompatible data. **Actual model-file checksum/version is not part of identity.** Importing different compatible-dimension weights under the same filename can reuse semantically invalid vectors. `importModel` stages `.part`, renames then calls `start`; an already-active `started` job/model can prevent a reload. Model validation/replacement needs lifecycle work, not cache deletion.
- Releasing the model after leaving VYBE (60 seconds when not indexing) does not invalidate stored vectors. `tasteNeighbours` reads stored dimension and ranks existing vectors without loading weights; Smart Mixes/Radio depend on this offline reuse. Do not remove the index to reduce RAM or unload the model.

### Recovery risks — source-established, not observed production corruption

1. Reader ignores an incomplete final record, preserving prior complete records. **Append does not truncate that incomplete tail before writing new records.** New data then starts at a misaligned offset; subsequent fixed-size reads can combine tail/new bytes into bogus records. “Ignores truncated tail” is insufficient to claim resumable writes. Add a validated committed length, truncate only the uncommitted tail under exclusive ownership and validate records before reuse.
2. `compact` writes `.tmp`, then renames; if rename fails it deletes the old file before retrying. A second failure can lose the recoverable original. No fsync/backup generation/verified promotion protocol was found. Append batches likewise have no explicit durability marker/checksum; an interrupted rebuild is not one atomic transaction.
3. Failed model import can leave `.part`; no startup cleanup/validation policy was found. Failed compaction can leave `.tmp`; never scavenge it before checking whether it is the only recoverable generation.
4. Model import has no pinned hash/length and does not safely version the index against actual weights. Fix identity/atomic replacement before allowing a model change to discard the current offline index.

### What the backup actually contains — SNAPSHOT

Format 1, model ID `embeddinggemma-300m-int4int8|doc1`, dimension 768, **208 complete records, 208 unique IDs, zero tail bytes**, all IDs match the 208-song snapshot DB. No superseded-ID records or missing IDs were found. There are **187 distinct vector byte arrays**: identical descriptions can intentionally share vectors, copied into records for different songs. That is not duplicate audio or a demonstrated index fault. A shared-vector dictionary could save some bytes, but the entire index is only 642,355 bytes; it is not a priority over correctness. Current-device private index state remains unverified.

### Conservative incremental reconciliation — PROPOSAL

`MediaStoreScanner` already skips deletion when audio permission is absent, and exceptions abort before transactional `LibraryWriter.write`. However, a query returning null is skipped, an unavailable volume can be omitted, and a custom location/filter can yield an empty/partial successful scan. `LibraryWriter` then deletes all IDs absent from that result. Missing permission protection alone is not proof of complete coverage. Enricher failures also currently write null enriched fields and mark enrichment complete; that can transiently alter embedding documents during access problems.

Before adding any cleanup, produce a scan outcome with volume/scope identity, permissions, provider-query success, enumeration completion and generation. Separate **confirmed removed**, **temporarily inaccessible**, **excluded by user scope** and **unknown**. Retain essential metadata and dependent state for uncertain cases; do not translate one failed open or missing volume into a tombstone. Restrict confirmed deletion to successfully enumerated scopes, corroborate suspicious/empty results with a later successful event or explicit user decision, and persist pending reconciliation rather than launching repeated full rescans.

Use committed changed/added/confirmed-removed IDs from the scan transaction to reconcile derived data. Recompute document hashes for changed IDs, re-embed only changed hashes, remove confirmed stale index entries during safe compaction and drop orphan waveform cache rows. Keep vector/metadata associations for temporary failures. Preserve favorites/history/bookmarks/playlists and downloaded content across scope changes; remap ID-changing moves only with sufficient identity evidence. Alias, artwork and history ownership require their own policy, not a generic cascade. Keep a recoverable old index until a validated replacement is promoted. Tests must precede automatic reconciliation.

## 6. Proposed cleanup policy and event boundaries

| Category | Automatic deletion allowed? | Trigger / constraints |
|---|---|---|
| Songs, favorites, playlists, bookmarks, settings, aliases and listening history | No general purge | Explicit user action or narrowly specified retention policy; scans must preserve user associations |
| Imported VYBE model | No | Explicit validated replacement/removal only; keep working model until new one is ready |
| Existing VYBE index | Only confirmed stale derived records after safeguards | Successful committed scan delta + exclusive writer; compatible header, validated tail, atomic recoverable compaction; never access-failure purge |
| Downloaded videos and explicit/legacy saved lyrics | No automatic expiry | Deliberate user removal; orphan mapping is a review condition, not permission to delete |
| Future transient online lyrics | Yes | Default memory-only current view/session; disappear on view/session turnover. Persistent transient cache is optional and separate |
| NOT_FOUND discovery metadata | Yes, when expired/unreferenced | Existing lyrics 7-day / art 14- or 60-day semantics; do not delete positive downloads alongside negative-response metadata |
| Waveform rows | Yes, derived only | Confirmed song removal or invalid fingerprint; optional bounded eviction after observed usage; one 480-byte levels payload per song is already compact |
| Image disk cache | Yes through owner library | Existing LRU and storage-pressure mechanisms; capture effective library maximum before introducing another budget |
| In-memory images/palettes/mix/search caches | Memory eviction only | Existing LRU/session lifetimes; no filesystem cleanup service needed |
| Artwork `.tmp`, import `.part`, index `.tmp` | Conditional | Operation ownership/lease + inactive writer + validated published/recovery state; protect active and sole recoverable copies |
| Tag-edit original/edited copies | Conditional, more conservative | Completed/aborted recovery manifest proves original was restored or durable write-back succeeded; retain ambiguous originals |
| Transfer pending entries / legacy parts | Conditional | Own session/package/folder, inactive request, age/completion proof; retain published music |
| WorkManager/transport database | Owner-managed | Library pruning/retention; do not remove files or live WAL/SHM |
| Crash logs | Yes under documented bound | Existing newest-20 policy; preserve recent troubleshooting evidence |
| Android ART, profiles, shader caches | No application scavenging | Platform manages them; don't clear caches or recompile to make Settings smaller |

Do not invent large quotas: the backup's lyrics are 316 KB, its image cache 143 KB, index 642 KB; no measured GB-scale disposable cache exists. The video downloader already has a per-operation 64 MiB cap but no total downloaded-content cap. Saved videos/models should expose their sizes and explicit removal controls, not masquerade as evictable cache. Start with the existing memory bounds (16 waveform windows, 64 lyric palettes, artwork bitmap LRU of 16 MiB plus 64 latest-image references and 512 miss entries) and current-song transient lyrics. Add disk budgets only when measured effective usage/limits show a problem; never cap essential history or downloaded content as if it were cache.

**Startup:** one coordinated bounded inspection of owned operation manifests/known temp directories before starting new writes; recover/preserve candidates before cleanup. Do not scan the audio library to clean storage. **After a successful library scan:** reconcile committed deltas and confirmed tombstones only. **On failed/cancelled operation:** close handles, abort its own temp, retain original/download and publish an accurate state. **At cache capacity/storage pressure:** ask the owning cache to evict disposable entries; downloads fail clearly if safe staging space is unavailable. No timer service, repeated whole-library cleanup scans or global directory deletion.

## 7. Phased implementation and acceptance tests

| Order | Concrete scope / risk | Required tests | Expected measurable outcome |
|---|---|---|---|
| **1 — next** | Opt-in lyrics downloads and legacy preservation; small network/library UI boundary, moderate state-race risk | Lookup/refresh/manual choice creates no permanent file; exact/ambiguous/instrumental/offline/429 paths; explicit download survives restart/offline; removal while request runs cannot resurrect it; retag/track-change identity; disk-full/rename/cancel; embedded precedence and enhanced/line LRC timing; all lyrics surfaces share state; no tag-writer invocation | Zero new durable lyric files for display; one record per explicit download; removal only affects chosen record; no promised reduction of existing 316 KB |
| 2 — isolated experiment | Artwork compression on PC copies only, then user review; no production replacement yet | Preserve resolution/bit depth/color/cadence and every frame; decoded A/B dark/gradient/detail + full motion/seam; exact bytes and per-frame metrics; later isolated S23 release decode/loop check | Accepted per-file byte saving measured, possibly zero; no quality claim from CRF alone |
| 3 — before cleanup | Harden file publishing, artwork job ownership, model identity and VYBE append/compaction; moderate/high recovery risk | Failure injection before/after each write/rename/fsync; truncated-tail followed by append; corrupt headers/vectors; kill during compact; rename failure; same-dimension different model; failed import preserves old model/index; concurrent manual/auto downloads, cancel/delete/startup races; no duplicate playback/decoder regression | Existing offline index retained/recoverable; no successful state points to partial content; only owned abandoned files removed |
| 4 | Scan completeness + incremental derived reconciliation; highest data-association risk | Null cursor, permission revocation, volume unavailable, provider exception, empty scope/filter change; rename/move with same and changed ID; metadata edit, real deletion, duplicate IDs across volumes; enrichment I/O failure; interrupted scan/index; favorites/queue/playlist positions/bookmarks/history/downloads preserved | No records removed on incomplete scans; only changed documents embedded; confirmed stale derived rows/log entries reconciled; no repeated full scan needed |
| 5 | Bounded owner-managed caches and storage UI/diagnostics; low/moderate risk after ownership work | Actual cache growth/eviction under repeated use and low storage; active-temp lease protection; no shader/ART deletion; no essential-user-data purge; restore/backup policy | Measured limits and accurate category reporting, no fabricated savings; downloaded bytes remain visible and user-controlled |
| 6 | Backup configuration policy review, separate from storage cleanup | Restoring settings/history/download associations without model/index incompatibility; cloud vs device-transfer exclusions; explicit video export/restore if desired | No oversized derived model accidentally included in backup set; essential data restore verified; device storage saving is not assumed |

Each implementation phase requires focused unit tests plus a release build and isolated device checks appropriate to that change. Use the existing same certificate/ARM64 packaging; back up before any later authorized update. Module lint debt does not justify weakening gates. No app build or app test was necessary for this documentation-only audit; the actual checks here were read-only package verification, StorageStats, source tracing, snapshot SQLite validation, index parsing, video decoding and current audio-path inspection.

## 8. Reproduction commands and remaining evidence

Read-only commands used/available for the connected phone (PowerShell):

```powershell
$adb = 'D:\android-toolchain\sdk\platform-tools\adb.exe'
& $adb devices -l
& $adb shell pm path com.lochanpthakke.empee3
& $adb shell dumpsys package com.lochanpthakke.empee3
& $adb shell pm art dump com.lochanpthakke.empee3
# Use the path returned by pm path, not an assumed future install directory.
& $adb shell stat -c '%n %s %b %Y' '/data/app/~~sV3tq-7zxwq_odEGsk5qyg==/com.lochanpthakke.empee3-TGFMjALiOmgcSNeoWlJfQw==/base.apk'
& $adb shell ls -laZ '/data/app/~~sV3tq-7zxwq_odEGsk5qyg==/com.lochanpthakke.empee3-TGFMjALiOmgcSNeoWlJfQw==/oat/arm64'
& $adb shell run-as com.lochanpthakke.empee3 id
& $adb shell du -ak /sdcard/Android/data/com.lochanpthakke.empee3
```

The `oat` and `run-as` commands currently fail with permission errors. `dumpsys diskstats` contains cached aggregate package arrays and is not a substitute for the fresh query. `cmd storagestats` has no shell implementation on this device. The separate shell reader's `StorageStatsManager.queryStatsForPackage(UUID_DEFAULT, package, UserHandle(0))` supplies the exact current table; its source and successful/denied results are retained in chat evidence. Repeat it only as a shell process, without installing or launching the app. APK pull + local `Get-FileHash` and `apksigner verify --print-certs` verify package identity.

For a future authorized diagnostic facility running under the real app UID, collect these exact read-only operations without invoking scan/cleanup/model-load code:

```text
StorageStatsManager.queryStatsForPackage(UUID_DEFAULT, packageName, Process.myUserHandle())
StorageStats.getAppBytesByDataType(DEXOPT_ARTIFACT / CURRENT_PROFILE / REFERENCE_PROFILE)
stat allocated+logical size for base.apk and oat/arm64/base.{odex,vdex,art}
walk CE dataDir, createDeviceProtectedStorageContext().dataDir,
     getExternalFilesDirs(null), externalCacheDirs: names, sizes, blocks, mtimes only
sha256 files/vybe/embedding_gemma.task, files/vybe/index.bin,
       no_backup/artwork/animated/*.mp4, DataStore/preferences
SQLite read-only transaction: quick_check, user_version, row counts,
       schema/index SQL, deterministic logical table digests
index parser: header/model/dimension, complete records, trailing bytes,
       current song-ID/hash match, superseded/stale records and vector validity
```

Under genuinely available private read access, equivalent file commands are `du -ak .`, `find . -type f`, and `sha256sum` for the listed assets; shell cannot currently run them against this release. Do not make it debuggable, reinstall, grant new privileges or initialize target-app instrumentation during this audit just to collect them. An exporter or separately authorized diagnostic session can close the gaps later; its initialization writes must be acknowledged. There is no ordinary adb command that bypasses release sandboxing while preserving these constraints.

Remaining blockers are therefore precise: exact per-ART-file attribution and a fresh private-file/DB integrity snapshot. Video compression savings and perceptual quality are deliberately unmeasured until phase 2. No production corruption, current orphan-file saving or redundant database/index purge has been demonstrated. The next concrete product change is phase 1; keep the existing model, index and saved artwork intact.


## 9. Lyrics phase implementation outcome — 2026-10-29

The opt-in lyrics phase is implemented and verified locally. Sections 1–8 above remain the historical audit and proposals; this section records the subsequent code outcome. **The overall storage lifecycle is not complete.** Artwork compression, cache budgets, backup configuration and database/index reconciliation remain separate future work.

### Old and new data flow

Previously, a confident lookup, instrumental answer or manual selection could call `FileOnlineLyricsCache.put`, automatically creating durable `files/online-lyrics/<songId>.json`. FOUND/INSTRUMENTAL files then worked offline indefinitely. Manual match selection was also a permanent preference, and “Use the file's lyrics” deleted the saved JSON.

Now lookup and manual selection retain a response in memory only. The provider, matching/ranking, plain/line/word-timed representations, parser and playback synchronization are unchanged. Existing durable positive content is read for offline access; **only the explicit Download action calls the durable positive writer**. A successful download records `explicitDownload=true` and the current source URI in the existing JSON format. The current selected candidate, including its original timestamps, is saved without another network lookup.

The full lyrics source bar/options menu now provides:

- **Download lyrics** when an online selection is not already explicitly downloaded.
- A **downloaded** source state when the displayed candidate matches the saved explicit record.
- **Previously saved** for retained legacy content; a refreshed different selection can separately indicate that a downloaded version is available.
- **Remove downloaded lyrics**, or **Remove previously saved lyrics** for a legacy record, with a deliberate confirmation dialog.
- A progress indicator/“Saving…” state during persistence, and an inline error on failure. The old saved state remains until persistence/removal succeeds.

The compact Now Playing panel still expands into the existing full lyrics UI; there is no unrelated panel redesign. Full/expanded/queue lyrics use the shared actions and `SongLyrics` state. “Use the file's lyrics” now sets a session preference without deleting offline content. Embedded `songs.lyrics` remains untouched and still has priority except for the established explicit match-selection override. No Room entity, enrichment, VYBE excerpt, audio-tag writer, provider or playback code was modified.

### Legacy compatibility and identity

There is no bulk migration, rename or deletion of the historical positive JSON files. Missing `explicitDownload` defaults to false, meaning **legacy saved content**, not a deliberate user download. Missing URI remains legacy-compatible. The JSON schema number remains 1 because these are additive optional fields with defaults; the existing representation and serializer remain in use. No Room schema migration or new storage system/dependency is needed.

Choosing Download on a matching legacy record promotes/replaces that single record atomically; it does not add a second permanent copy. A user's original download intent still cannot be inferred from the old format. The old `userChosen` flag continues to mean match preference, not download intent. Legacy instrumental entries remain locally readable even though there is no textual lyric to download.

Operations check numeric song ID, the existing metadata fingerprint and, for new saves, source URI. Requests are shared by full track/query/URI identity. Download/removal is serialized; identical repeated downloads are idempotent. Removal compares the exact record captured by the confirmation dialog before deleting. A stale request or dialog cannot remove a newer saved record. Similar titles/filenames do not determine disk paths.

**Conservative limitation:** when saved metadata or a recorded URI no longer matches, the record stays on disk and is not silently remapped, overwritten or removed through the mismatched song. A separate association/recovery feature is not implemented. Legacy entries cannot provide URI identity they never recorded. Matching legacy offline behavior is preserved using their existing ID/fingerprint contract.

### Temporary response policy and file safety

The repository retains **one most-recent response/selection** for this process, keyed by the full track identity, plus currently in-flight requests. Another response evicts that memory entry; process death drops it. Successful transient responses are not persisted and have no cross-process offline guarantee. No arbitrary disk quota or timer was added.

The seven-day NOT_FOUND validity window is retained. Newly fetched negatives exist only in that one-response memory slot, so eviction/process death can cause an earlier retry; LRCLIB throttling/rate limiting remains unchanged. An accessed legacy negative file is physically removed only when its TTL has expired, its song ID is valid, it contains no candidate and it is not marked explicit. Unaccessed negative files are left alone: there is no scan of the lyrics directory/library, timer or background cleanup service. Positive legacy/explicit records never expire under this policy.

File saves use an operation-owned UUID temporary file in the same directory, flush and sync its bytes, check coroutine cancellation, then publish with an atomic replace. There is no delete-the-old-file fallback. If publication is unsupported or fails, saving reports failure and retains the original. Normal failure/cancellation removes only that operation's temporary file; pre-existing temporary files are not scavenged. Process death before publication may leave a small temporary file for future recovery policy. File errors propagate rather than being reported as a successful download/removal; unreadable/corrupt existing files are retained and refuse replacement/removal through an unverified read.

New lookups never write durable positive results, including when a network request completes after the view closes or after a download is removed. A removed lyric can remain visible as a transient current response; a fresh process has no offline copy unless embedded or other retained local content exists.

### Files changed

| File | Change |
|---|---|
| `core/network/.../lyrics/OnlineLyricsCache.kt` | Optional intent/URI metadata; testable existing file store; atomic publication and checked deletion |
| `core/network/.../lyrics/OnlineLyricsRepository.kt` | One-response memory cache, identity-based request sharing, explicit download/removal, legacy reads and lazy negative expiry |
| `feature/library/.../LibraryViewModel.kt` | Explicit persistence actions, guarded async state, progress/errors and non-destructive file preference |
| `feature/library/.../OnlineLyricsState.kt` | Saved/downloaded/progress/error state independent of transient cache hits |
| `feature/library/.../LyricsView.kt` | Shared `SongLyrics` persistence state |
| `feature/library/.../OnlineLyricsUi.kt` | Source states, download action, confirmed removal and progress/error presentation |
| `core/network/.../lyrics/OnlineLyricsTest.kt` | Existing lookup/provider tests updated for transient selection and non-persisting lookup |
| `core/network/.../lyrics/LyricsDownloadsTest.kt` | 15 new focused storage/lifecycle tests |
| `feature/library/.../OnlineLyricsChoiceTest.kt` | 3 new display-state/embedded-priority regression tests |
| `docs/storage-lifecycle-audit.md` | This verified implementation outcome |

### Verification and limits

| Check | Result |
|---|---|
| `:core:network:testDebugUnitTest` | **291 tests, zero failures/errors** |
| `:feature:library:testDebugUnitTest` | **192 tests, zero failures/errors** |
| `:app:testDebugUnitTest` | **1 test, zero failures/errors** |
| `:app:assembleDebug` | **PASS**, final source; confirmation invocation BUILD SUCCESSFUL in 16 s with all 318 tasks up to date |
| Focused KtLint 1.5.0 on all 9 changed/new Kotlin files | **PASS** |
| Module Detekt | Still fails on **63 existing network / 87 existing library findings**; no remaining new lyrics findings; existing unrelated `LibraryViewModel` findings remain |
| `git diff --check` | **PASS** (Git reports normal LF→CRLF notices) |

The combined tests/assemble/Detekt invocation exits with failure because of existing Detekt debt. The separate tests+assemble invocation succeeds. No lint rules/configuration were weakened. Intermediate new complexity/style findings were fixed and Detekt rerun. No requested unit-test task was missing or NO-SOURCE; Java-only compilation/Hilt steps with no Java sources are not missing Kotlin test coverage.

Focused regressions cover transient display/manual choice with zero durable writes; explicit disk persistence and fresh-repository offline reload; exact enhanced-LRC timestamp round trips; concurrent repeated saves; per-song removal despite identical titles; failed writes/removals retaining prior state; cancellation before save; byte-identical legacy reads and explicit promotion without duplicate files; recent/expired negative behavior and physical expiry; metadata/URI mismatch and stale-removal protection; late network completion after removal; corrupt/unreadable record preservation; and embedded priority/display state. Existing provider fallback, matching, HTTP error/rate-limit and lyric-parser tests also pass.

**Physical-device validation was not performed.** The S23's daily ARM64 release was not installed, replaced, uninstalled, launched, force-stopped or given new permissions for this task. A read-only package check reconfirmed last update `2026-10-28 22:26:39`, ARM64, version 1/1.0. The newly assembled debug APK's certificate remains SHA-256 `5bb0fc457b96d9fb6cbc606009de44d633bee95b58fec2dc633a7a4eb54a9ce5`. Release/R8 and actual S23 online→Download→offline reload→Remove UI flows remain unverified; no device claim is inferred from JVM tests.

No production cache/data/content was changed or deleted and no phone storage reduction was applied or measured. The historical 316,336-byte lyric footprint is not a claimed saving. No artwork, model/index, signing/package/permission, build configuration or unrelated code change was made. `claudeinstructions.md` and `docs/app-size-and-storage.md` remain byte-identical; the pre-existing audit is retained with this appended outcome. Nothing committed or pushed; HEAD remains `b5fcd1308fcb9a655d638dd01c74dda0927bed66` on `kshitij`.

Evidence: this chat's `outputs/lyrics-implementation/` contains final build/test and lint logs, test counts, Detekt summary, signing output and final source hashes. The earlier storage audit's private-integrity and ART-attribution evidence gaps remain; this phase does not close them. The next validation step for these lyrics changes is an explicitly authorized isolated/release device exercise; separate storage phases remain pending.


## Subsequent S23 installation — 2026-10-29 21:59:47 IST

The user explicitly requested installation. The current lyrics changes are installed **in place as the ARM64 optimized release**, package `com.lochanpthakke.empee3`, versionCode 1 / versionName 1.0. Installed APK SHA-256 is `d03fcac984e87bf09c8c5f5d9c9d8f29a984242671c8392559e7a488759cb9ab`; a pulled copy exactly matches the fresh release. Signing certificate remains `5bb0fc457b96d9fb6cbc606009de44d633bee95b58fec2dc633a7a4eb54a9ce5`. The package is not debuggable and has only the ARM64 ABI.

Fresh full backup: `D:\android-toolchain\dbbackup\pre-opt-in-lyrics-20261029-215928`. `appdata.tar` is 291,184,128 bytes; all 85 regular members were fully read and validated. The copied Room DB with its WAL passes `quick_check=ok`, schema 14, 208 songs. Backup contains the 183,816,181-byte model, 642,355-byte index, 104,266,525 bytes of saved animated videos and 341,056 bytes of online lyrics. These are current pre-update backup measurements, not reductions or post-update hash comparisons.

Because the prior daily app was a non-debuggable release, the documented same-code, same-key debug APK was briefly installed with `adb install -r`, then force-stopped for a quiescent `run-as tar` backup. No app data was restored, cleared or replaced from an old backup. The new ARM64 release was then installed with `adb install -r`. Original release APK and validated archive are retained in the backup folder. Paired current source APKs are archived outside Git in `D:\android-toolchain\build-opt-in-lyrics-20261029`.

Before updating the real package, the freshly built isolated `.bench` ARM64/R8 app was installed, launched successfully and checked for fatal errors; it was then uninstalled. A first log-collection attempt had an adb quoting error; rerun passed. After the real update, `am start -W` reports success and its scoped process log contains no fatal exception/signal during the startup check. Both checks ran while the phone was dozing; no taps, wake/unlock commands, visual review or online/download/offline/removal flow test was performed. The full lyrics flows remain covered by local tests, pending an interactive device exercise. Only the real empee3 package remains installed.

The release build passed in 13m15s; the isolated release-like build passed in 10m28s (including a Gradle cache-lock delay). No source changes were made during installation; the verified source hashes remain identical. No music files, artwork, model/index, signing configuration or settings were deliberately changed, and there was no uninstall of the real app. The task-owned phone tar was removed after local validation and installation. No commit or push.

Evidence is in this chat's `outputs/lyrics-install/`: backup validation, installed APK identity/certificate, package details, scoped startup logs and install results. This installation supersedes the earlier implementation section's installation status; it does not establish completion of the other storage lifecycle phases.


## Phase 3 artwork outcome — October 30, 2026

See `docs/artwork-compression-phase3.md` for measured experiments and limitations. This is a
safe subset: validated/atomic publication and download lifecycle/recovery hardening, **no
on-device encoder and no recompression of existing user artwork**. Original assets remain.
Graduation CRF18 copy: 18,113,173 -> 3,359,742 bytes (81.45% saved in the experiment only).
SOS square CRF18 grew 14.69% and was rejected; Main10 portrait encoding was stopped as too slow.
Actual production artwork savings applied: zero. Full-motion review and Android decode pending.

Regressions: network 297, database 169, library 192, all passing (658 total); six standalone
candidate-gate tests also pass. Touched Kotlin KtLint and `git diff --check` pass. Detekt retains
63 network / 21 database findings in existing code; no rules weakened. All three final APK
assembly tasks complete; the combined Gradle command exits nonzero for database Detekt only.
The prior nine lyrics source/test files and historical storage report/instructions are unchanged.

Final real ARM64/R8 release: 37,182,375 bytes, SHA-256
`d53c660ced14432db11ac70f81a78ab8fd05de745d0855557df36efec3475cb1`, package
`com.lochanpthakke.empee3`, versionCode 1. Unchanged signing certificate
`5bb0fc457b96d9fb6cbc606009de44d633bee95b58fec2dc633a7a4eb54a9ce5`.
Paired APKs: `D:\android-toolchain\build-artwork-phase3-20261030` (outside Git).

Device work was deliberately deferred until the end at the user's request. At that stage ADB
listed no devices. No phone tests, fresh backup or installation have run in this phase; no phone
data was changed. The October 29 daily installation above remains the last verified install.
Prepared decoder probe/isolated startup checks and a fresh full backup are required before the
final in-place update when the S23 reconnects. The report includes the exact install command.
No real-app uninstall, data clear, old backup restoration, commit or push.


## Subsequent installation and artwork removal

The user explicitly requested installation and removal of downloaded live artwork, with no testing. The S23 reconnected. A fresh full backup was saved to `D:\android-toolchain\dbbackup\pre-artwork-phase3-20261030-200735` before changes (85 files; database schema 14, 208 songs, quick_check ok). A same-code/same-key debug bridge enabled the backup and scoped artwork removal. Only the `animated_artwork` records (5 rows) and its 3 downloaded video files (104,266,525 bytes) were removed. Music, lyrics, model/index and other app data were not cleared.

The final ARM64 release was installed with `adb install -r`: **Success**. Installed APK hash matches `d53c660ced14432db11ac70f81a78ab8fd05de745d0855557df36efec3475cb1`. The real app was never uninstalled. No app launch, decoder, smoke, playback or visual tests were run, as requested. No compression was applied. This update supersedes the earlier disconnected-device and pending-installation status.

## Phase 5 — explicit compression (2026-10-30)

The accepted Phase 4B visual review is retained. A dependency-free Android SPS parser/writer now preserves explicit SAR 1:1 and top-left chroma location while validating BT.709 limited-range 8-bit YUV420 and parameter-set relationships. Its supported output matches the independent FFmpeg correction. The existing album live-cover controls expose opt-in 6 Mbps HEVC Main compression for a conservative AVC square 1080×1080/30 FPS subset; Main10, portrait, HEVC and unsupported formats remain ineligible. No automatic/batch compression, database migration or unrelated data processing was added.

The original remains until metadata, timing, worthwhile savings and full decode pass, followed by existing synced atomic publication. Owned staging cleanup/recovery, cancellation and shared download/delete coordination are covered by focused tests. All 693 module regression tests pass. Previous lyrics and Phase 3 hardening hashes are preserved, except the intentionally extended repository implementation.

Actual isolated S23 production encoder output: 18,113,173 → 11,188,275 bytes, saving 6,924,898 bytes (38.2313%); 1080×1080, 30 FPS, 450 frames, 15 seconds. Android full decode, independent PC bitstream validation/full decode, cancellation with original retained, successful Kotlin transaction publication on a disposable copy and recompression prevention passed. Coded pictures match the accepted Phase 4B candidate. No production artwork was compressed during these probes. Experimental outputs remain outside Git, and exact owned phone staging was removed after collection.

See `docs/opt-in-artwork-compression.md` for settings, test evidence, release/install status and limitations. Earlier Phase 3 installation/removal history above is preserved; no new visual approval is required. No commit or push.

Final Phase 5 ARM64 release: 37,198,759 bytes, SHA-256 `ed08bee19ecc92aff04bcd254de8e3de8eb56d648e148be54aa3653b20a669d9`; signature matches the existing certificate. Debug/benchmark/release assembly completed. Detekt retains model 1, network 63, library 87 findings; database has 22 (one new method-count warning on the existing repository, previously 21). No rules were weakened. The user selected **Prepare APK only**, so no startup check, fresh private-data backup or update was performed. The installed Phase 3 release and production artwork remain unchanged. Device verification above covers isolated production encoder/transaction code, not release UI/startup or existing production-artwork playback after an update.


## Subsequent installation

The user subsequently requested installation. The S23 identity and idle empee3 process state were checked; no UI taps or app launch were needed. A fresh full backup was validated at `D:\android-toolchain\dbbackup\pre-artwork-phase5-20261030-234416` (69 files, SQLite quick_check ok, schema 14, 208 songs and 1 animated-artwork record). A same-code, same-key Phase 3 debug bridge enabled the private-data backup without launching the app. The Phase 5 ARM64 release was then installed using `adb install -r`: **Success**. The installed APK SHA-256 matches `ed08bee19ecc92aff04bcd254de8e3de8eb56d648e148be54aa3653b20a669d9`.

This supersedes the earlier APK-only installation status. No app uninstall, data clear, artwork removal/compression, old-data restoration, startup/playback test, commit or push was performed during installation. The app was left unopened.
