# empee3 — Claude working instructions, progress and pending work

_Last updated: 2026-10-17. Branch `kshitij`; animation source at `bd9b861`, followed by these handoff notes._

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
| `bd9b861` | Expanded-player swipe feedback and track handoff; see 3.6 for the subsequent installed-version correction |
| `0f910f2` | Shared tactile spring feedback and bounded selection jiggles |
| `e553558` | Exact player artwork endpoint handoff; remove residual expand/collapse resize |
| `ebb23c3` | Library/player motion continuity and consistent measured artwork bounds |
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

### 3.6 Animation work by Codex (2026-10-14–16)
- **Library/player continuity (`ebb23c3`):** crossfade library sections while retaining scroll
  state and preventing outgoing content from receiving input. Keep search/icon bounds stable;
  remove competing album artwork overlays. Measure expanded artwork after its square sizing
  (`PlayerArtworkLayout.kt`) so the travelling artwork targets the actual final image bounds.
  Carousel skip commands run immediately and distinguish duplicate queue occurrences.
- **Residual artwork resize (`e553558`):** `PlayerTransitionState` hands artwork over only at
  the exact expanded endpoint; shared spring progress tolerance is subpixel. This addresses the
  remaining size correction near the settled endpoint rather than hiding it with a slower fade.
  Four regression tests cover expansion, collapse, gesture release and early hero reveal.
- **Spring feedback (`0f910f2`):** shared press responses handle quick taps and cancellation;
  confirmed favourite/playback-mode changes use bounded jiggles. Library artwork press feedback
  preserves touch bounds and player transition geometry. No repeated unbounded jiggle impulses.
- **Expanded-player swiping (`bd9b861`):** `TrackCarouselState.dragBy` now writes the draw-phase
  position synchronously instead of launching a coroutine for every pointer event. Track
  acknowledgements preserve an already-correct settling spring; coalesced skips clear pending
  acknowledgements, avoiding stale watchdog corrections. Batched finger-up horizontal swipes
  are recognised without claiming taps, vertical movement or consumed gestures. Position
  rebasing does not subscribe composition to per-frame movement.
- **Related swipe rendering work:** `MainScaffold` observes only song availability for shell
  composition; lyrics are read inside the visible sheet and waveform/bookmark updates inside
  `PlaybackProgress`. `ExpandingPlayer` drops the invisible travelling artwork at fully open;
  `MiniPlayer` updates its covered content without a second hidden `AnimatedContent` transition.
  `PlayerMotion.AnimatedTrack` supports that scoped opt-out. Approved glass visuals are unchanged.
  `SongArtworkBitmapLoader` uses the existing 512px size-keyed cache/executor instead of returning
  an arbitrary full-size latest UI bitmap to Media3; oversized embedded artwork is sampled on
  the worker. No new dependency, artwork loader, playback delay or audio/DSP changes.
- **Validation:** latest approved source passed 167 unit tests across `app`, `feature:library`
  and `core:playback`, and `:app:assembleDebug`. App/playback ktlint and detekt passed; library
  module-wide checks retain unrelated existing findings. Earlier continuity work passed two
  S23 Compose geometry tests; spring feedback had 74 unit tests and S23 visual checks.
  Expanded-player next/previous, repeated swipes, reversals and expand/collapse were exercised
  on the S23. The user said the installed version was "plenty smooth" and asked to keep it,
  but two packages were installed: see the correction below before treating either as accepted.
- **Measurement limits:** debug A/B frame results varied with queue/thermal conditions; do not
  claim a universal measured improvement or zero jank. Optimized baseline release comparison
  was blocked by an existing R8/PlaybackStateProto field crash. Deliberately uncached/missing
  artwork, differing aspect ratios and VYBE-loaded cases were not conclusively verified.
- **Scope boundary:** no Smoothness Engine was built. Queue-cache, shared-transition relocation
  and the final uninstalled mini-player snapshot/marquee experiments were removed. Do not
  reintroduce them as if user-approved.
- **2026-10-17 correction:** after the profiling clone was removed, the user reported that the
  retained real app still lagged and believed the other copy was the smooth one. The earlier
  assumption that their approval referred to the real package was wrong. Recover the saved
  `D:\android-toolchain\swipe-investigation\final-scoped-debug.apk` (`.perf` package), which
  matches the last installed profiling candidate. Its last diagnostic configuration used
  `default_dark`, while the real app used artwork-driven `dominant_color`; package and theme
  differences must be considered before claiming the source alone explains the perceived result.
  The user then confirmed the restored copy and requested committing/pushing this version.
- **Source verification after confirmation:** rebuilt current `bd9b861` source using
  `:app:assembleDebug -PperfApp`; build passed. All 1,207 non-signature ZIP entries, including
  every DEX file and resource, exactly match saved `final-scoped-debug.apk` (SHA-256 per entry).
  Thus the recovered APK's application code is already committed in `bd9b861`; no missing
  animation patch needs recreating. The fixed-dark preference is device DataStore state, not
  a different animation implementation. Preserve that confirmed configuration; this comparison
  does not establish a measured causal link between the theme and jank. No global theme-default
  change was requested or made. Both packages remain installed; do not remove one by guessing.

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
- Animation application source is `bd9b861`, pushed to `origin/kshitij`; this handoff update
  is being committed/pushed separately at the user's request. Check HEAD for the latest notes.
- Original swipe APK remains in `com.lochanpthakke.empee3`, installed 2026-10-16 at 23:34.
  Its installed APK SHA-256 matched the earlier saved real-package APK:
  `90f4c9ead024f4725c35983090f9dd5350961a308d055cb41c8ebc9b383200c1`.
  Automatic rotation was restored after the portrait-locked profiling runs.
- **Subsequent correction (2026-10-17):** the user reported that the retained copy still lagged.
  The saved `final-scoped-debug.apk` profiling copy has been reinstalled. With the phone idle,
  current real-app data was backed up to
  `D:\android-toolchain\dbbackup\pre-other-player-20261017-000230\appdata.tar`;
  its databases and playback proto were copied to the recovered profiling package, then only
  the clone's theme was set to `default_dark` to match its prior diagnostic configuration.
  Real-app data was not overwritten. The user confirmed this recovered copy and requested
  committing/pushing it. Its source-to-APK verification is recorded in 3.6. Keep it installed
  with `default_dark`; keep the original real package as a data-safe fallback for now.
- Test-setting follow-up: repeat was temporarily changed from Off to All during profiling.
  The user subsequently changed tracks/queue; do not overwrite their current preferences
  or restore an old data snapshot. Confirm their desired repeat mode before changing it.
- Earlier pre-install app-data backup:
  `D:\android-toolchain\dbbackup\pre-swipe-last-20261016-233415\appdata.tar`.
  Do not restore older profiling backups over the user's newer queue/settings.
