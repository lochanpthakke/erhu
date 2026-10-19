# empee3 — Claude working instructions, progress and pending work

_Last updated: 2026-10-19. Branch `kshitij`, latest code commit `7ff9564` (pushed with these notes)._

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
| `7ff9564` | **Glass Now Playing cards**, **liquid seek bar**, **Home redesign** (Smart Playlist hero banner, new library concepts, five Featured Albums, page titled Home) and the **player-motion performance pass**; see 3.8–3.10 |
| `17ddedd` | **Smart Playlists 2.0** (real local recommendations, generative cover art) and **Now Playing redesign** (3D card carousel, scrolling page, artwork-palette lyrics panel, glass/gradient surfaces, linear seek bar, overflow menu); see 3.7 |
| `f76ec0d` | Theme unification: shared group components (`Groups.kt`, `GroupRows.kt`), Artwork Fetcher and Auto Fix Tags restyle, clearer drawer, collapsing titles on Settings / Wi-Fi Transfer / Sound Capsule |
| `2163e8c` | Spring motion pass, liquid navigation capsule (drag to navigate), titles collapsing into the header button |
| `e8cfef7` | Collapsing header for album and artist pages |
| `ba60260` | Merge `origin/lochan`: artist artwork fetcher, search fix and UI, fullscreen artists |
| `cef0c55` | Release build fixes (R8 keep rules for MediaPipe/Flogger), benchmark setup and frame diagnostics (Smoothness steps 1–2) |
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

### 3.7 Smart Playlists 2.0 + Now Playing redesign (`17ddedd`, 2026-10-19)
**Smart Playlists** (`feature/library/.../mix/`, `SmartPlaylists.kt`, `LibraryHome.kt`):
- `SmartMixEngine` (pure, unit-tested) builds mixes from real data only: monthly play counts per
  song with recency decay (21-day "recent", 120-day "lasting" half-lives), favourites, genre, BPM.
  There is **no skip data** in the app; nothing is inferred from skips.
- Mixes: In Your Rotation, Your Kind of Sound, Forgotten Favourites, Deep Cuts, High Energy /
  Chill / Late Night (`Moods.kt`: genre decides; BPM only supports it and is trusted only in
  70–175, outside that it may be half/double time), A Little Different. Fallbacks (Favourites,
  Fresh in Your Library) plus a "keep listening" card while history is short (< 5 songs / 10 plays).
- VYBE reuse **without loading the model**: `VybeEngine.tasteNeighbours` reads the stored index
  vectors (`VybeIndexFile.storedDimension`, `vectorsOf`; `core/vybe/VybeTaste.kt`), ranks songs
  against a weighted centroid of the most-played songs, cached until seeds / index / library change.
- `SmartMixSource` / `SmartMixRepository`: Room flows (new read-only queries
  `observeTrackFacts`, `observeMonthlyPlays`; no schema change) → debounced (1.5 s, first value
  immediate) → built off the main thread → app-wide cache. Detail page resolves the same ids.
- Generative covers (`MixArt.kt` spec + `MixArtwork.kt` drawer): seed = FNV hash of the mix key,
  12 palettes, 6 compositions, Compose primitives only. No album art, collages, images or network.
- Hide from Smart Playlists: proto field `hidden_from_recommendations = 31`
  (`HiddenTracksPersistence`), undo via snackbar / menu. Files, library and user playlists untouched.

**Now Playing** (`NowPlayingScreen.kt`, `NowPlayingParts.kt`, `NowPlayingLyrics.kt`,
`NowPlayingMenu*.kt`, `TrackCarousel.kt`, `LyricsPalette.kt`, `LinearSeekBar.kt`):
- The existing queue strip (`TrackCarouselState`) became a 3D depth carousel: cohesive cards
  (artwork + title, artist, album when there is room, favourite), card 80% of the width, neighbours
  scaled 0.78, turned 32° and dimmed; tap a side card to select it. `page` is measured from the
  current card (`onSizeChanged`), so cards follow the finger 1:1. Reduced motion: no rotation.
- One scrolling page: card + seek bar + controls on the first screen, lyrics heading peeking;
  the lyrics panel below (62% of the viewport, 300–560 dp). `playerDrag(canStart)` collapses the
  player only while the page is scrolled to the top.
- Lyrics: the existing `SongLyricsView` / `LyricsContent` with a `LyricsLook` (compact, left-aligned,
  19 sp) for the panel; expanded view over the player (`ExpandedLyrics`). Sync uses the player's
  position + `rememberSmoothPosition` (now shared); lookup is triggered once per song (`onShown`).
- `LyricsPalette`: strongest hue of the cover (hue-band vote, not an average), contrast-checked
  text (≥ 4.5:1 active, ≥ 3:1 secondary) across a three-stop gradient; cached per artwork, sampled
  32 px off the main thread, cross-faded. `nowPlayingSurface()`: **Glass Effect on** = tinted
  `glassPanel` (Strong) over the album backdrop; **off** = the opaque gradient.
- Overflow menu: Music information (`ViewMetadataScreen`), Audio details, Bookmark this moment,
  Bookmarks & A-B loop, Add to playlist, Hide from Smart Playlists, Queue, Visualizer, Equalizer.
- `LinearSeekBar` replaces the waveform in Now Playing (the old `WaveformSeekBar.kt` is unused but
  kept): artwork-accent played part, round head with glow (static half glow when paused), 48 dp
  touch row, same tap/drag seek and bookmark/A-B markers.

### 3.8 Now Playing cards + liquid seek bar (`7ff9564`)
- `NowPlayingCardSurface.kt` (`carouselCardSurface`): one card material for both Glass modes. Glass:
  `glassPanel` (frost, shadow) + the card gradient at 0.4 around the artwork, denser behind the text
  (`glassTextAlpha`: the alpha that keeps 4.5:1 against the brightest/darkest backdrop the glass could
  show), album-tinted rim, upper light, lower shade, soft step under the artwork. No glass: the same
  details over the opaque diagonal `cardGradient`. The horizontal bands are merged into ONE gradient
  (`VerticalBand`/`flatten`): three draws per card.
- `LyricsPalette` gained `cardGradient` (lighter hue top-start → surface → the artwork's SECOND colour,
  if it has a real one, bottom-end) and `rim`; secondary/accent are checked against both gradients.
  The lyrics panel still uses `nowPlayingSurface` (unchanged look).
- `LinearSeekBar`: the played part is one path with a liquid crest (`LiquidCrest`: two drifting swells,
  ≤ 3 dp, eased in from the start); drift advances only while playing, settles to 45% when paused,
  still with reduced motion; fill is the accent lifted 18% to pastel. A released seek is held
  (`rememberSeekSettle`) until the player reports a position within 750 ms (1.5 s timeout), so the head
  never springs back. No second playback timer: the drift is a frame-clock value read in draw only.

### 3.9 Home redesign (`7ff9564`)
- The Tracks section's page header says **Home** (`LibrarySection.pageTitle`); the bottom-navigation
  tab still says **Tracks** (the user asked not to rename the tab). Order: hero → Featured Albums →
  Recently played albums (empty-state note) → Recently played playlists → Tracks.
- Hero (`HomeHero.kt`): HorizontalPager of mixes, manual swipe (no timer), max 6, order fixed per app
  session (`HomeSession`, new on each launch, keyed by mix key so rebuilds keep it). Art:
  `MixArt.banner(kind, aspect)` = the cover's seed/palette/composition laid out for the banner (focus to
  the right, left `BANNER_TEXT_SIDE` = 0.46 keeps only backdrop + glow, shapes in shorter-side units so
  nothing stretches). Existing square covers were verified IDENTICAL (layer hashes vs `17ddedd`).
  `BannerLook`: light text on dark palettes, dark on light; a veil of the palette's own tone only as
  strong as needed for 6:1 against every text-side colour (≤ 0.7 for all palettes). Glass on: a light
  rim + sheen drawn over the art. Play = `LibraryViewModel.playSmartMix` (same as the mix page's Play).
- New mix kinds in `mix/LibraryMixes.kt` (library-only, offered only with enough songs): Spotlight
  ("Spotlight: <artist>", `SmartMix.title`), Throwbacks (year tags ≥ 10 years old; 1970/0 ignored —
  `year` added to the read-only `observeTrackFacts` query and `TrackFacts`), Quick Hits (< 2:40), Long
  Listens (≥ 5 min), Yet to Be Heard (unplayed; only with ≥ 10 counted plays). Mood concepts the
  tags cannot support (142/153 songs have no genre) are deliberately NOT offered. New palettes:
  Night drive, Soul, Vintage, Slate.
- Featured Albums (`FeaturedAlbums.kt`, `FeaturedAlbumsViewModel`): 5 distinct albums — most played,
  then favourite-holding, then a daily rotation (fuller albums, then singles, then Recently played
  albums only to fill). Fully offline; nothing labelled personal without data.

### 3.10 Player-motion performance pass (`7ff9564`, measured)
Measured with `JourneyBenchmarks#playerExpandCollapse` on the S23 (`.bench`, seeded from the user's data,
Glass on). Results in `D:\android-toolchain\smoothness\expand-*`; analysis scripts there
(`rt_breakdown.py`, `layers.py`, `glasscount.py`, `slices.py`). Named trace sections now exist:
`GlassSource:<name>`, `GlassFrost:<name>`, `GlassLens:<name>` (backdrops: `screens`, `shell`, `screen`,
`nowPlayingArtwork`).
- The closed drawer kept re-blurring a full-screen layer whenever the shell redrew: its backdrop is
  now given only while it is open or animating (`MainScaffold`, `drawerVisible`).
- `GlassBackdrop(preBlurred = true)` for Now Playing's artwork backdrop (already a 40 dp blur): glass
  over it draws the region directly + the frost's dim/lift tone, no second blur or offscreen layer
  (`FrostBacking.kt`). Lost: the frost's 1.35 saturation and edge light on those surfaces only.
- `GlassBackdrop.frozen`: set for `screens` and `shell` while the player is opening, open, closing or
  landing (`progress > 0 || arrival != 0`); their glass reuses its last rendering (taken at rest) and
  stops reading its position, so the sliding chrome does no lens/blur pass per frame.
- `ExpandingPlayer`'s fade uses `CompositingStrategy.ModulateAlpha` (no full-screen offscreen buffer).
- `AppScaffold(contentIsBackdrop = false)` for Now Playing (its top bar is not glass).
- Numbers (journey, warm phone): frame CPU p50 23.4 → 13.9 ms, p90 64 → 28, p99 114 → 45;
  RenderThread drawing 15.8 → 8.9 ms/frame; frames drawn 1933 → 3060. (`17ddedd` warm: p50 25.0,
  p99 108.) 62% of frames still exceed 8.33 ms (120 Hz): what remains is drawing Now Playing plus the
  screen under it each frame — next idea: stop drawing the covered library once the player is fully open.
- Note: early A/B numbers were skewed by phone temperature — always rerun the baseline warm.

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
- **Now Playing (`17ddedd`)**: card size and fit, swipe / tap-side-card feel, scroll into the lyrics
  and back, collapse from the top, glass-on vs glass-off surfaces on bright / dark / mono / missing
  covers, seek-bar head while dragging and paused, every overflow-menu action, hide + undo.
- **Smart Playlists (`17ddedd`)**: personal mixes once there is enough history (the user's history
  was too short on 2026-10-19: only High Energy and Fresh showed), VYBE-matched "Your Kind of Sound".
- **`7ff9564` (installed as release 2026-10-19 23:42, not yet looked at on the phone by Claude)**: the
  user confirmed the glass cards and liquid seek bar "all work"; still to check: banner art and text on
  every concept, banner swipe vs page scroll, banner Play starts the shown mix, Featured Albums shows
  five and opens the right album, last row clears the mini player, Glass on/off, and whether opening /
  closing the player now feels smooth (with music playing and paused).

### 5.2 Known limitations / deferred features
- Not implemented (shown as unavailable, not faked): transition **effects** (reverb/echo),
  **looping**, **beatmatching**; **BPM / musical key** (no reliable metadata in the library).
- Preview has stop, not pause/resume (offered to the user; not requested yet).
- Online lyrics: LRCLIB word-synced records are rare; `lyricsfile` is a newer field and may change
  (falls back to line timing). External `.lrc` sidecar files are not read (embedded tags only).
- Cue points apply to single pairs only (not album/global defaults).
- Now Playing carousel shows one neighbour per side (the player exposes only previous / next).
- The player still decodes each track's waveform in the background although Now Playing no
  longer draws it (`PlayerExtrasViewModel.waveform`); the queue sheet's lyrics tab still exists.
- The old `SmartPlaylist` enum and `SmartPlaylistRepository` are no longer used by the UI (kept, tested).
  `SmartMixTile` / `MoreListeningNote` are no longer used by Home (kept).
- Bottom-navigation tab still reads "Tracks" while its page is titled "Home" (user's instruction;
  offer to rename if they want them to match).
- Playlist occurrence identity relies on positions never being renumbered — if a playlist
  **reorder** feature is ever added, give entries a durable id (Room migration) first.

### 5.3 Pre-existing lint/static-analysis debt (not caused by recent work; left alone)
- detekt: `SoundSettingsMapper` (LongMethod), `SongArtworkBitmapLoader` (ReturnCount),
  `MediaStoreScanner`, `JAudioTaggerEnricher`, designsystem `DominantColorExtractor`; core:model
  `RepairDiagnostics` (detekt + ktlint); feature:library has 27 old findings (e.g. `LibraryViewModel`).
- Android lint: ~40 Media3 `UnsafeOptInUsageError` in `PlaybackModule`, `CustomEqAudioProcessor`,
  `PlayerMessageLoopScheduler`, `PlaybackControllerImpl` (audio session), `PlaybackServiceConnector`.
  Android lint is therefore not a clean gate; check new files only.

### 5.4 Coordination
- Lochan's metadata editor work was merged on 2026-10-13 (`4485552`); the user has not yet tested it
  on the phone in this log. Lochan's Deezer metadata/genre work is his — don't edit it.
- VYBE (`core:vybe`, `core:vybe-mediapipe`) exists from `084bc7f`; leave it unless asked.

_5.5–5.8 were labelled "uncommitted" in older notes; they are in git history (`6f32c31`, `dbfc792`, `d7e9ab2`)._

### 5.5 Metadata & Artwork audit + VYBE revisit (2026-10-13)
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

### 5.6 Year support (2026-10-13)
- Year Only action + year in All Fields (fills only blank / "1970" / unparseable years). Date from
  MusicBrainz: earliest official release of the chosen album, full precision (`ReleaseDate.kt`);
  bootleg/promo/compilation dates rejected; equally good matches with different years → no year.
- jaudiotagger 3.0.1 on ID3v2.3: `setField(YEAR)` over an existing TYER kept the old year
  ("1970-11-19"); the writer now deletes YEAR first and verifies the exact read-back. ID3v2.3 can't
  store year-month (it invents "-01"), so those are saved as the year with a note.

### 5.7 Mojibake + matching (2026-10-13)
- "IGORâ€™S THEME": the file's UTF-16 TIT2 is correct; Android's MediaStore misreads it (string-dependent
  charset guess; "WHAT’S GOOD" in an identical tag is fine). Scanner now keeps the file tag's title when
  MediaStore's value is exactly its Windows-1252 misreading (`MojibakeText.correctedFromTag`). No file rewrite.
- Matching: `TrackMatcher` searches tag title/artist first, file-name variants after, dedupes, and scores
  against the embedded identity (not the query text); ties that would write different tags -> NEEDS_REVIEW.
- Library audit (read-only, 153 songs): 1 misread title, 0 corrupted files, 75 year-1970, 142 no genre.

### 5.8 Batch network failures + Last.fm (2026-10-14)
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
- **2026-10-19 23:42:** the main app (`com.lochanpthakke.empee3`) is the **release** build of `7ff9564`
  (R8, not debuggable). Pre-install data backup:
  `D:\android-toolchain\dbbackup\pre-perf-pass-20261019-234223\appdata.tar`.
  Build outputs: `D:\android-toolchain\build-perf-pass\` (`real-release.apk`, `real-debug.apk`; the debug
  one is the same-code build to install for a data backup). Previous: `build-home-dynamic\`.
- Branch `kshitij`: `7ff9564` and these notes are pushed to `origin/kshitij` (2026-10-19).
- Only the main app is installed; `.bench` and the benchmark runner were removed after the runs.
  Re-seed when needed: build `:app:assembleDebug -PbenchApp`, install it, push a tar of
  `databases files shared_prefs no_backup` from the latest backup, `run-as … tar -xf`, grant
  READ_MEDIA_AUDIO + MANAGE_EXTERNAL_STORAGE, then `install -r` the `assembleBenchmark` APK and the
  `:benchmark` runner. Use `MSYS_NO_PATHCONV=1` and `D:/…` paths with adb in Git Bash.
- A worktree of `17ddedd` for A/B builds is at `D:\android-toolchain\ab-baseline-17ddedd` (it has
  copies of the card files in it from an experiment; safe to remove with `git worktree remove`).
- Backing up the release app: install the same-code debug build (same key), `run-as tar` the data,
  then reinstall the release. Never restore an old backup over the user's newer data.
- `ktlintFormat` formats the WHOLE module: snapshot `src` first and restore every file you did not
  touch (it once reformatted six unrelated files, which were then committed in `7ff9564` as
  formatting-only changes with the user's "commit everything").
- Phone automation: check the test app is focused before every input, tap only UI-dump-located
  targets, never fixed coordinates or BACK without confirmed state. The user may be using the phone
  at the same time: stop sending input as soon as the state changes unexpectedly.

## 7. Smoothness Engine (2026-10-17; steps 1–2 committed in `cef0c55`; player-motion fixes in 3.10)
Status 2026-10-19: steps 1–2 (benchmark module, `.bench` package, diagnostics) were committed in
`cef0c55`; the user said no more benchmarking until more animations are added. Step 3+ (fixes, e.g.
cheaper glass, the wasted waveform decode) still needs the user's approval. History below.
The user's brief: app-wide jank reduction in stages. Stage 1 audit was reported. The user approved
**steps 1–2 only** (measurement setup + diagnostics) and connected the phone. Steps 3+ (fixes) need approval.

**Step 1 (done, uncommitted):** `app/proguard-rules.pro` adds a protobuf-lite keep rule. This fixes the
release R8 crash, verified: the release-like build launches. The app has a `benchmark` build type that is
always the `.bench` package (release + R8, profileable via `app/src/benchmark/AndroidManifest.xml`,
debug-signed). Debug `-PbenchApp` gives the same `.bench` package. The new `:benchmark` module
(Macrobenchmark 1.4.1, UiAutomator 2.3.0) has 8 journeys in `JourneyBenchmarks.kt`; see
`benchmark/README.md`. Run via `adb shell am instrument` (never connected*Test, which uninstalls and
loses the seed). `.bench` is seeded from `.perf`'s data (backup
`D:\android-toolchain\dbbackup\bench-seed-from-perf-20261017-002646`). Glass is ON in `.bench`.
**Step 2 (done, uncommitted):** `core:common` `perf/Interactions.kt` (labels + `traced()`);
`core:designsystem` `component/InteractionReporting.kt` (`ReportInteraction`, `Modifier.reportScrolling()`).
Wired into MainScaffold (screen, player moving, scroll on root Box), LibraryScreen (scanning),
TrackCarousel (swipe), ArtworkLoader (decoding + trace), VybeEngine (model/index/search + traces).
The JankStats sink is `app/src/debug/.../perf/FrameDiagnostics.kt` (logcat tag `Smoothness`); no-ops
in src/release and src/benchmark. ktlint/detekt are clean for new code (feature:library and
designsystem have existing findings). Unit tests: app 1, common 7, designsystem 20, library 63, all pass.
**Measured (S23, 120 Hz, release-like, compilation partial):** results and traces are in
`D:\android-toolchain\smoothness\` (`summarize.py`, `analyze_traces.py`, `rt_breakdown.py`, perfetto venv).
- Shell journeys are RenderThread/GPU bound, not main-thread bound. The **glass-off A/B** proves the glass is the main cost:
  albums scroll CPU p50 7.8→3.9 ms, overrun p50 +6.4→−0.7; tracks scroll 11.2→5.4; player expand p99 46→14;
  RT drawing 6.3→2.5 ms/frame; frames over 8.3 ms 40%→1%. Glass is an approved design: make it cheaper, don't remove it.
- Track swipe: each swipe starts a full-song waveform decode that is cancelled (flatMapLatest + SingleFlight)
  before it can cache when the user swipes quickly, so it is wasted CPU. It does NOT correlate with missed frames (13% vs 17%).
- Startup: TTID median 597 ms, TTFD 793 ms. Lyrics open p99 overrun 109 ms; album detail p90 overrun 17 ms.
- Debug-build run done 2026-10-17 (`smoothness\debug-current\`, current code, battery 39%, 38.5→41.9 °C, hotter than
  the release baseline). Versus the release baseline: cold start 597→1731 ms, TTFD 793→2337 ms;
  p99 frame CPU: album detail 28→111, lyrics 77→217, track swipe 31→180, VYBE switch 43→124, tracks scroll 19→60 ms.
  Debug spikes are 2–6× worse. A same-conditions release run on the CURRENT code is still needed to confirm.
- **2026-10-17 13:31: the REAL app (`com.lochanpthakke.empee3`) is now the RELEASE build** (user's choice; R8, not
  debuggable). `run-as` NO LONGER WORKS on it, so data backups must use another route. The pre-install backup is
  `dbbackup\pre-release-install-20261017-133046\appdata.tar` (full, includes the VYBE model). The rollback APK (previous
  debug, sha 90f4c9…) is `D:\android-toolchain\rollback\real-app-debug-before-release-20261017.apk`; same key and
  versionCode, so `install -r` works.
  Before installing, a release smoke test on `.bench` found **VYBE crashed under R8**: MediaPipe's Flogger
  ("no caller found on the stack", Graph.<clinit>). It was fixed with keep rules for `com.google.mediapipe.**`,
  `com.google.common.flogger.**` and `org.snakeyaml.engine.**`, then verified: model load plus search returned 30 results in 642 ms.
  Any crash also triggers CrashThemeHandler, which RESETS THE USER'S THEME, so smoke-test release builds on `.bench`
  first. The user is testing the other screens themselves (the smoke pass was stopped at their request).
- **2026-10-17 13:33: only the release real app remains installed.** `.perf`, `.bench` and `.benchmark` were uninstalled
  at the user's request (".perf" data backed up to `dbbackup\perf-before-uninstall-20261017-133311`). Reinstall and
  re-seed them only when the user continues the Smoothness work. `.bench` can be seeded from that backup or
  `bench-seed-from-perf-20261017-122241`; the VYBE model copy is in `smoothness\vybe-copy\`.
- JankStats (debug) works: 18,024 frames / 347 janky, all labels recorded. It also showed `library=scanning`
  during startup in 51 janky frames.
**Storage incident (2026-10-17):** force-stopping the interrupted debug run left an orphaned
`perfetto --background-wait` session writing `/data/misc/perfetto-traces/trace_output.pb`. It reached
21 GB overnight and the phone was 97% full. The process was killed and the file deleted (about 19 GB freed).
The same run also left Macrobenchmark's `shellWrapper.sh` and its `trace_processor_shell --http-port 9001`
running for 11 h. **Rule now: run benchmarks ONLY via `benchmark/scripts/run-benchmarks.sh`, never raw `am instrument`.**
- The runner checks USB, idle phone and ≥10 GB free, and always cleans up (trap on EXIT/INT/TERM/HUP).
- Its `device-watchdog.sh` runs on the phone (setsid/nohup). It stops the run and deletes the trace if USB is
  unplugged, the test process dies, the trace passes 3 GB, free space falls below 6 GB, or 3 h pass.
- The phone's shell has 32-bit integers, so sizes are compared in KB.
- Safety tests passed on 2026-10-17: a simulated unplug (`dumpsys battery unplug`) stopped the run in 2 s; a
  force-stop with no PC cleanup (yesterday's failure) had its writer and file removed in about 11 s.
`.bench` was re-seeded from `.perf` (backup `dbbackup\bench-seed-from-perf-20261017-122241`).
The real app and `.perf` were not touched. The user's `.perf` copy and real app are debug builds; the debug-vs-release
gap is still unmeasured.
