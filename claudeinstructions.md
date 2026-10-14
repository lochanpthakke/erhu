# empee3 — Claude working instructions, progress and pending work

_Last updated: 2026-10-13. Branch `kshitij` at `4485552` (pushed to `origin/kshitij`)._

Read this first in a new session. It records how this project is worked on, what is done, and what
is still open. Verify anything specific (file names, functions) against the code before relying on it.

---

## 1. Project and environment

- **empee3**: Android local music player — Kotlin, Jetpack Compose, Hilt, Room (DB version 11),
  Media3/ExoPlayer 1.5.1, proto DataStore (`PlaybackStateProto`), WorkManager.
- Repo: `D:\Personal Projects\empee3`. Modules: `app`, `core:{common, designsystem, playback, model,
  datastore, datastore-proto, database, sound, network, vybe, vybe-mediapipe}`,
  `feature:{library, settings, transfer}`.
- No Android Studio. Portable toolchain in `D:\android-toolchain`:
  - Build: `JAVA_HOME=/d/android-toolchain/jdk21/ ./gradlew …` (Git Bash).
  - adb: `/d/android-toolchain/sdk/platform-tools/adb.exe`, device **Galaxy S23** `RZCXA1H43GW` (Android 16).
- Checks normally run: `ktlintFormat`, `ktlintCheck`, `detekt`, `testDebugUnitTest` for touched
  modules, then `:app:assembleDebug`.

## 2. Rules the user has set (follow them)

### Git
- Work on branch **`kshitij`** (tracks `origin/kshitij`). Push only `refs/heads/kshitij`
  (`git push origin refs/heads/kshitij:refs/heads/kshitij`). **Never push `master` or `lochan`.**
- **Commit or push only when the user explicitly asks.** Never stash, reset, rebase, force-push or
  discard changes unless explicitly told (the user did once say "rollback and scrap" — that was explicit).
- Pulling Lochan's work: `git fetch origin` then **`git merge --no-ff origin/lochan`** (never fast-forward).
- `refs/heads/kshitij` is ambiguous with an old remote also named `kshitij` (harmless warning; use full refs).
- Release builds stay off git until the user says "commit build version to git".
- Commit messages end with the Co-Authored-By / Claude-Session lines given by the session.

### Phone / installs
- Before installing: check the focused window (`dumpsys window | grep mCurrentFocus`); never tap
  while the user is using the phone (no taps have been needed for installs).
- Check empee3 is not running (`pidof com.lochanpthakke.empee3`) so playback is not interrupted.
- Back up app data first:
  `adb exec-out run-as com.lochanpthakke.empee3 tar -cf - . > D:\android-toolchain\dbbackup\pre-<feature>-<timestamp>\appdata.tar`
- Install with `adb install -r app/build/outputs/apk/debug/app-debug.apk` (keeps data).
- Never uninstall the real app without consent. Restore any settings changed during tests.

### Product constraints (from the user's briefs)
- Never fabricate data: no invented DSP stages, lyrics, word timings, BPM/key, capabilities.
- No cloud inference / no uploading music. Network only where a feature needs it (online lyrics sends
  title/artist/album/duration only). No RECORD_AUDIO / microphone capture.
- Do not touch Lochan's pending **Deezer metadata/genre** work. Do not modify **VYBE** unless asked.
- Do not redesign the approved **Liquid Glass mini-player / top bar**; use glass selectively.
- Don't do broad performance audits or broad playback-engine refactors unless asked.
- The user tests everything on the S23 and may scrap features ("rollback to last version") — keep
  each feature isolated and easy to revert.

## 3. Completed and committed work (newest first)

| Commit | What |
|---|---|
| `4485552` | Merge `origin/lochan` (Lochan: metadata editor framework, tag viewer/writer, file renamer, filename cleaning, metadata scoring — `c99cda9`, `a6f2262`) |
| `0a53907` | Transition editor: preview control is just a ▶/■ symbol (no container) |
| `8f9700c` | **Online lyrics** (LRCLIB) with local cache |
| `bc59fd9` | Transition editor: Liquid Glass transition window |
| `40d29db` | **Audio inspector**, **crossfade transitions**, **Transition Studio** |
| `51a3054` | Audio-reactive **visualizer** (spectrum bars, circular spectrum, oscilloscope) |
| `9af7d4a` | **Wi-Fi Transfer** (send music from a computer's browser to the phone) |
| `084bc7f` | Settings redesign, **Queue Studio**, **waveform player**, VYBE foundation |

### 3.1 Audio Signal Chain Inspector (`core/playback/.../diagnostics`, `feature/library/AudioDetails*`)
- Now Playing → ⓘ "Audio details": source file (header-parsed FLAC/WAV/MP3 facts), player state,
  decoder, app processing chain (Transition fade → Equalizer → Visualizer tap → Android system),
  output devices (exact route on Android 13+), EQ headroom estimate, post-EQ sample peak meter.
- Every value is labelled by source (File header / Container / Library / Player / Platform /
  Measured / Estimate). Copy/share report is sanitized (no paths, no device names).

### 3.2 Crossfade transitions engine (`core/playback/.../transition`, `core/sound/.../dsp`)
- Real overlap: a private **tail player** (`ExoTailPlayer`) plays the outgoing track's last seconds
  with its own gain stage + EQ copy, while the main player moves to the next entry with an
  incoming fade (`TransitionGainProcessor`, first in the main audio chain, unity when idle).
- `TransitionCoordinator` schedules with player messages (media time), generation tokens, cancels on
  skip/seek/queue/settings changes, follows pause/volume/speed, releases a tail paused > 60 s.
- Automations executed per player: volume curves (built-in or custom points), **bass swap**
  (low-shelf −24 dB below 200 Hz, smoothed), **filter sweeps** (Butterworth high/low-pass, `PassFilter`).
- Settings hierarchy (resolver in `core/model/CollectionTransition.kt`): master switch → exact pair →
  album/playlist → global default; each level Inherit / Off / Custom; Off never falls through.
  Stored in the settings DataStore proto (fields 22–25, no Room migration).
- Queue entries carry their origin (`QueueOrigin`: collection + occurrence). Playlist occurrences are
  identified by playlist **position + song id** (positions are never renumbered). Persisted across restarts.
- **Cue points** per pair: slide the waveforms in the editor to set where the outgoing track hands
  over (`outgoingCutMs`) and where the incoming starts (`incomingStartMs`); used by playback and preview.

### 3.3 Transition Studio UI (`feature/library/.../transition`)
- Album/playlist page → "Transition Studio" row → master switch, default for all music, this
  collection's setting, timeline of tracks with a chip per adjacent pair.
- Editor (styled after the user's reference screenshots): track rows above/below a stacked timeline,
  three-band waveforms (lows blue, mids orange, highs cream; decoded per window, cached in memory),
  glass transition window, preview ▶ between lanes, duration pill + sheet, preset cards
  (Auto/Default, Off, Fade, Rise, Bass Swap, Blend, Melt, Slam, Custom), Volume/EQ/Filter rows opening
  a paged option sheet, "Effects, looping, beatmatching — Not available" row, ⋮ (Undo, Reset cue
  points, Reset to preset), Save. Isolated preview on two private players (queue untouched).
- Fixed: the timeline gesture loop spun when nothing was editable (froze "All music"); it now
  always awaits a touch first.

### 3.4 Online lyrics (`core/network/.../lyrics`, `feature/library/OnlineLyrics*`)
- LRCLIB provider: `api/get` exact match then `api/search`; User-Agent `empee3/1.0.0 (…)`; one
  request at a time (400 ms gap); 429 → no requests until Retry-After; offline/timeouts handled.
- Word timing comes only from LRCLIB's **`lyricsfile`** (YAML, `lines[].words[]` with `start_ms`)
  when `hasWordSync` is true — parsed with `snakeyaml-engine` 2.9 and converted to enhanced LRC so the
  existing lyrics parser/karaoke highlight uses it. `syncedLyrics` is line timing only.
- `LyricsMatcher`: normalizes harmless differences; live/remix/acoustic/cover/sped-up/… must match;
  duration > 10 s off rejected; a different artist containing the name is never confident.
  Low confidence → "Is it one of these?" list; nothing shown silently.
- Cache: one JSON per song in `filesDir/online-lyrics/`, keyed by song id + metadata fingerprint;
  "not found" remembered 7 days; failures not cached; concurrent lookups share one request.
- File's own lyrics take priority unless the user explicitly picks an online result; nothing is
  ever written into audio files. Lyrics page: source line + ⋮ (Refresh, Search online…, Use the file's lyrics).

### 3.5 Earlier features (already shipped before this log)
Visualizer, Wi-Fi Transfer, Queue Studio, waveform seek bar, timestamp bookmarks, A–B looping,
Settings redesign, Liquid Glass theme/mini-player, Sound Capsule, smart playlists, artwork manager.

## 4. Scrapped (do not reintroduce without asking)
- **Word-by-word karaoke rework** (active-word-only highlight, LyricsClock smoothing with speed,
  manual "Time words" tool, local enhanced-LRC copies) — built 2026-10-11, the user scrapped it and
  it was removed before committing. The committed lyrics view still has the original basic
  enhanced-LRC word highlight, which online word-synced lyrics use.

## 5. Pending work and open items

### 5.1 Needs testing on the S23 (not verifiable by unit tests)
- Crossfade **hand-over** quality (click / doubled sound when the tail player takes over).
  Tunables in `TransitionCoordinator`: `START_LEAD_MS` (120), `HANDOVER_DELAY_MS` (20), `ENTRY_RAMP_MS` (40).
- Bass swap and filter sweeps sound smooth; preview start offset (incoming started by a position message).
- Transition editor touch: lane slide (cue points) vs page scroll, point drag/long-press,
  glass window look in the pink theme, dark theme.
- Interruptions during a transition: calls, other apps ducking (tail player is not ducked),
  headphone unplug, lock-screen controls, Bluetooth vs speaker latency.
- Audio inspector on real outputs (wired/USB/Bluetooth/LE Audio), offload behaviour.
- Online lyrics on real songs: match quality, wrong-lyrics cases, rate limiting, offline reuse.

### 5.2 Known limitations / deferred features
- Not implemented (shown as unavailable, not faked): transition **effects** (reverb/echo),
  **looping**, **beatmatching**; **BPM / musical key** (no reliable metadata in the library).
- Preview has stop, not pause/resume (offered to the user; not requested yet).
- Online lyrics: LRCLIB word-synced records are rare; `lyricsfile` is a newer field and may change
  (falls back to line timing). External `.lrc` sidecar files are not read (embedded tags only).
- Cue points apply to single pairs only (not album/global defaults).
- Playlist occurrence identity relies on positions never being renumbered — if a playlist
  **reorder** feature is ever added, give entries a durable id (Room migration) first.

### 5.3 Pre-existing lint/static-analysis debt (not caused by recent work; left alone)
- detekt: `SoundSettingsMapper` (LongMethod), `SongArtworkBitmapLoader` (ReturnCount),
  `MediaStoreScanner`, `JAudioTaggerEnricher`, designsystem `Glass.kt`/`DominantColorExtractor`.
- Android lint: ~40 Media3 `UnsafeOptInUsageError` in `PlaybackModule`, `CustomEqAudioProcessor`,
  `PlayerMessageLoopScheduler`, `PlaybackControllerImpl` (audio session), `PlaybackServiceConnector`.
  Android lint is therefore not a clean gate; check new files only.

### 5.4 Coordination
- Lochan's metadata editor work was merged on 2026-10-13 (`4485552`); the user has not yet tested it
  on the phone in this log. Lochan's Deezer metadata/genre work is his — don't edit it.
- VYBE (`core:vybe`, `core:vybe-mediapipe`) exists from `084bc7f`; leave it unless asked.

### 5.5 Metadata & Artwork audit + VYBE revisit (2026-10-13, UNCOMMITTED, not installed)
- Deezer: `api.deezer.com/infos` → `country_iso IN, open false`. Track/album search returns `total>0`
  with empty `data` from India; artist search and `track/{id}`, `album/{id}` lookups work. Track BPM is 0.
  Now reported as FAILED ("isn't available in this country"), not "no results".
- Last.fm: the built-in key gets `error 10 Invalid API key`. Not queried (`LastFmProvider.IS_CONFIGURED`), not offered.
- MusicBrainz: best release chosen per recording (official / plain album / local album title first), 1 req/s
  throttle, Cover Art Archive cover, curated release-group genres (top-voted only, ties → none).
- Fixes: `FieldFixPlan.kt` decides fields per action (All Fields = title + fill-only artist/album/genre/cover);
  placeholders never written; version mismatch (live/remix/demo…) rejected in the scorer; "LastFm" key
  mismatch fixed; provider failures shown; BPM Only / Lyrics Only no longer offered; "UNCHANGED" status
  instead of false "fixed"; rename only when the title changed; settings persisted (proto 26/27).
- Not touched: `MetadataBatchWorker`/`ProcessSingleTrackUseCase` (never enqueued), `ArtworkManagerScreen`
  (no nav entry; its "apply" never writes artwork), Manual Selection mode (only a flag).
- VYBE: original spec recovered (on-device NL search, EmbeddingGemma; Exp. 3 decision: stop until a mood
  source exists). Added `VybeQueryRouter` (exact artist/album/title routing) and dropped album names from
  embedded text. No UI/indexing built: blocked on model delivery and a mood source (user decision).

### 5.6 Year support (2026-10-13, UNCOMMITTED, not installed)
- Year Only action + year in All Fields (fills only blank / "1970" / unparseable years). Date from
  MusicBrainz: earliest official release of the chosen album, full precision (`ReleaseDate.kt`);
  bootleg/promo/compilation dates rejected; equally good matches with different years → no year.
- jaudiotagger 3.0.1 on ID3v2.3: `setField(YEAR)` over an existing TYER kept the old year
  ("1970-11-19"); the writer now deletes YEAR first and verifies the exact read-back. ID3v2.3 can't
  store year-month (it invents "-01"), so those are saved as the year with a note.

### 5.7 Mojibake + matching (2026-10-13, UNCOMMITTED, not installed)
- "IGORâ€™S THEME": the file's UTF-16 TIT2 is correct; Android's MediaStore misreads it (string-dependent
  charset guess; "WHAT’S GOOD" in an identical tag is fine). Scanner now keeps the file tag's title when
  MediaStore's value is exactly its Windows-1252 misreading (`MojibakeText.correctedFromTag`). No file rewrite.
- Matching: `TrackMatcher` searches tag title/artist first, file-name variants after, dedupes, and scores
  against the embedded identity (not the query text); ties that would write different tags -> NEEDS_REVIEW.
- Library audit (read-only, 153 songs): 1 misread title, 0 corrupted files, 75 year-1970, 142 no genre.

### 5.8 Batch network failures + Last.fm (2026-10-14, UNCOMMITTED, not installed)
- Cause of "No address associated with hostname" x84: Battery Saver on (low_power=1); Android blocks network
  for background apps (netpolicy UID 10485 blocked=BATTERY_SAVER|APP_BACKGROUND). Reproduced: run-as empee3
  can't resolve musicbrainz.org while the shell can. Wi-Fi also flapped ~19:50.
- Batch: RepairBatchRunner pauses on OFFLINE/BLOCKED/unreachable (track put back, not "no match"),
  NETWORK_FAILED + NO_MATCH statuses, preflight (network + DNS only), RetryPolicy (3 tries, backoff+jitter,
  Retry-After <= 30 s), queue persisted in proto field 28, one auto-resume when the network returns.
- Last.fm: old hard-coded key invalid (HTTP 403, error 10). Key now from local.properties `lastfm.apiKey`
  or env LASTFM_API_KEY -> BuildConfig; blank = NOT_CONFIGURED (no request). Auth failure disables it for the session.
- OkHttp request logs now redact api_key.

## 6. Last known state
- `kshitij` == `origin/kshitij` == `4485552`, working tree clean (this file is new and untracked).
- Last APK installed on the S23: 2026-10-11 21:01 (preview-icon build). The merged build with
  Lochan's metadata editor has been built and tested (432 unit tests pass) but **not installed** yet.
- App-data backups: `D:\android-toolchain\dbbackup\pre-*` (latest `pre-preview-icon-20261011-210130`).
