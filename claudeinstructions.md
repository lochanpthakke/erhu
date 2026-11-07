# erhu — Claude working instructions, progress and pending work

_Last updated: 2026-11-07. Current public release is erhu 1.0.6 (7), source `4670c40`, built and verified by Actions run 42074317364, then promoted to master. Only Lochan's missing application-source updates through `5c18a91` were imported. Last verified S23 installation is 1.0.9 (10) with the station artist-portrait header and artwork-derived radio colours, installed on 7 November at 16:29 IST. See the current build record below. Free Firebase Hosting/FCM and the prior device-only hello test remain configured. Older notes are historical where superseded._

Read this first in a new session. It records how this project is worked on, what is done, and what
is still open. Verify anything specific (file names, functions) against the code before relying on it.

---

### Latest verified build/install: radio artist header (2026-11-07)
- User requested installation; reused the approved temporary GitHub branch/direct remote-commit workflow. No local commit was created, local HEAD remains `e38a2e3`, and intended work remains uncommitted locally. Master/public release assets are unchanged.
- Source `c1cbea2c7678bb1543d185120ea96f0ae2be9077`, branch `build/radio-artist-header-20261107-160357`, parent verified artwork-upgrade source `07105c2de1eb100ad6b9fd1ba59ae9b9aff8064e`. Uploaded only 12 intended radio/model/query/test/version files. Private work/, instruction notes and unrelated JAudioTaggerWriterTest changes were excluded. Version is **1.0.9 (10)**.
- Actions https://github.com/lochanpthakke/erhu/actions/runs/42364495952 succeeded: full unit tests, release lint, optimized signed ARM64 release, identity/signature verification. Publication was disabled. No APK was compiled locally.
- Installed on S23 `RZCXA1H43GW` using `adb install -r` at 2026-11-07 16:29:46 IST. Playback was paused before installation; no UI taps or navigation were performed. Installed APK hash exactly matches CI: `6e08acb0e470340e0d3e93998db380db7b170cd8ccf1ece57d527897d69e589d`; original signing certificate retained. The new artist-credit query is verified in installed APK DEX.
- APK/checksum/verification/installed.json: desktop chat `outputs/erhu-radio-header-ci-42364495952`. Previous 1.0.8 APK remains in `outputs/erhu-artwork-upgrades-ci-42360496640`; its source branch is retained. App data preserved; actual phone radio-page visual validation remains pending.

### Previous verified build/install: artwork upgrades (2026-11-07)
- User requested installation after implementation; reused the already approved temporary-branch / GitHub-only commit approach. No local Git commit was created. Local HEAD remains `e38a2e3`, with intended changes uncommitted; master and public release assets are unchanged.
- Remote branch `build/artwork-upgrades-20261107-150702`, source `07105c2de1eb100ad6b9fd1ba59ae9b9aff8064e`, parent `e03733f2a00dcace5b78676833a43ddc5d3bb57f` (verified Google-search source). Uploaded only ten intended feature/test/build files; excluded private work/, unrelated JAudioTaggerWriterTest changes and local instruction notes. Version incremented to **1.0.8 (9)**.
- Actions https://github.com/lochanpthakke/erhu/actions/runs/42360496640 succeeded: full unit tests (including native artwork tests), release lint, optimized ARM64 APK, identity and original-certificate verification. Public release publication was disabled. No APK was compiled locally.
- Installed on S23 `RZCXA1H43GW` with `adb install -r` at 2026-11-07 15:31:13 IST. Actual installed APK SHA-256 matches CI artifact: `1fa4af9e504b3c47f2098aa25e136e43f40d64d2a6c2e161308fb95f94819d83`. Existing signing certificate retained; installed APK DEX contains both upgrade tools, review dialog and Google search action.
- Saved APK/checksum/verification/installed.json in desktop chat `outputs/erhu-artwork-upgrades-ci-42360496640`. Previous Google-search APK remains in `outputs/erhu-google-search-ci-42356466313`, and its source branch is retained for rollback. App data was preserved. No UI navigation or changes to music/artwork were performed on the user's phone; live upgrade-flow validation remains pending.

### Previous verified build/install: Google search (2026-11-07)
- User explicitly approved a temporary GitHub build branch and a commit created directly on GitHub, with no local Git commit and no changes to master or previous release assets.
- Branch `build/google-images-20261107-141016`, source `e03733f2a00dcace5b78676833a43ddc5d3bb57f`; only the six album/artist Google Images link implementation/test files were uploaded over base `e38a2e3`. Local HEAD remains `e38a2e3`; source changes are intentionally still uncommitted locally. Do not duplicate this remote change or assume it has been merged.
- Actions run https://github.com/lochanpthakke/erhu/actions/runs/42356466313 succeeded: full unit tests, release lint, optimized signed ARM64 APK and verification. Publication was disabled; master/public release remain unchanged.
- Installed compatible erhu 1.0.7 (8) on S23 `RZCXA1H43GW` with `adb install -r`, preserving app data. Actual installed APK hash matches CI artifact: `775aec8feba0640a5633e0b349910d97746ec25745824a8e452d414916631b87`. Original certificate verified. Artifacts and `installed.json` are in desktop chat `outputs/erhu-google-search-ci-42356466313`; previous onboarding APK in `outputs/erhu-onboarding-ci-42095225371` is retained for rollback.
- Manual action "Search images in browser" opens Google Images with album title + artist + "album cover image", or artist name + "artist photo". Available in detail menus, artist image dialog and Artwork Fetcher queues/results. It is not a scraper/provider and does not automatically import images. Existing keyless providers and local selection remain unchanged. Four targeted URL tests and scoped ktlint/detekt passed; device browser interaction has not been tested.

## Radio station portrait header implementation (2026-11-07)
- Radio station detail pages now show the main artist in a large centre circle and up to two distinct supporting artists actually credited on the station's generated tracks. Reuses the iOS RadioHero reference at `e56f29ef801e2a1ea7443c60dd3a56ec026d9577`. Home carousel cards and radio generation/playback algorithms are unchanged.
- Added a bounded Room artist-credit projection through ArtistDao/LibraryRepository: seed primary credits first, then number of distinct station tracks. Unresolved credits/artists and unrelated tracks are excluded; each artist carries one station track as an artwork fallback. No schema/migration change or parsing guessed artist names. Portrait changes update the existing immutable station snapshot without regenerating its plan.
- New RadioStationHero reuses LyricsPalette's artwork colour/contrast logic, cached by the lead portrait/station-track URI. One 64px software image sample is loaded off the main thread, reduced to 32px for colour extraction; derived palette cache is bounded. The station theme/gradient follows its artist image, independent of Now Playing; header fades into the same page background. Missing portraits use that artist's own track artwork; fewer artists leave fewer circles. No random/fixed peach colour or extra artist lookup was added.
- Validation: 11 targeted tests passed across verified-credit queries (2), station selection/fallbacks (3), existing/new station ViewModel regression cases (3), native bitmap palette/contrast (2) and a native Compose header rendering check (1). Visually inspected blue, orange and light test-artwork previews in untracked `work/radio-artist-header-20261107/rendered`. Scoped ktlint/Detekt and module compilation passed. Temporary Compose render dependencies/source were injected only via work/ init scripts, with no new production/test Gradle dependency changes for this task.
- The subsequent GitHub build and installation are verified in the latest build record above. No local commit was made, and live radio-page validation remains pending. Preserve the earlier uncommitted Google-search/artwork-upgrade work and unrelated JAudioTaggerWriterTest working-tree state.

## Artwork resolution upgrade implementation (2026-11-07)
- New Artwork Fetcher → Improve existing artwork actions: Upgrade album covers / Upgrade artist photos. Search is explicit, sequential and cancellable; previews are bounded to 100 matches / 64 MiB per search. Existing Google Images links and automatic/manual artwork workflows are preserved.
- Reuses MetadataRepository catalogues, artist artwork storage and LibraryViewModel's existing Android write-consent flow. Only actual larger, visually similar whole images are offered, with a 25% gain in both dimensions, close aspect ratio, spatial-colour/edge checks and a 1,440-pixel save cap. This follows the supplied video and MeezaUni/Erhu-iOS ArtworkUpgradeStore/ArtworkUpgradeMatch reference at `e56f29ef801e2a1ea7443c60dd3a56ec026d9577`; no reverse-image upload, scraping or new provider/key was added.
- Review current/replacement images before explicit apply. Revalidate original and replacement hashes at save, including after Android consent. Album saves change artwork only and skip different/unreadable/unwritable track covers, reporting partial counts. Artist upgrades use custom artwork storage. Album preview preparation respects the existing writer's JPEG output; artist PNG transparency is retained. Saving reports completion after preview cleanup.
- Code: new core/network ArtworkUpgradeMatch/ArtworkUpgradeImages and feature/library ArtworkUpgradeController/ArtworkUpgradeScreen; small wiring in ArtworkManagerScreen and LibraryViewModel. Added Robolectric as a feature/library test-only dependency. Changed-file ktlint and new-code Detekt passed. Thirteen targeted tests passed (3 matcher + 10 native-image/workflow), covering incorrect matches, decoding, save cap, transparency, cancellation/single-flight, provider/save failures, stale images, kind switching and album consent/artwork-only writes. Subsequent GitHub build and installation are verified in the latest build record above. Actual upgrade UI/provider/device validation remains pending; no APK was compiled locally and no local commit was made.

## 1. Project and environment

- **erhu**: Android local music player — Kotlin, Jetpack Compose, Hilt, Room (DB version 14),
  Media3/ExoPlayer 1.5.1, proto DataStore (`PlaybackStateProto`), WorkManager.
- Repo: `D:\Personal Projects\empee3`. Modules: `app`, `core:{common, designsystem, playback, model,
  datastore, datastore-proto, database, sound, network, vybe, vybe-mediapipe}`,
  `feature:{library, settings, transfer}`.
- No Android Studio. Portable toolchain in `D:\android-toolchain`:
  - Local JDK/SDK are available for inspection and targeted checks only; compile application APKs through GitHub Actions (see build policy below).
  - adb: `/d/android-toolchain/sdk/platform-tools/adb.exe`, device **Galaxy S23** `RZCXA1H43GW`, model **SM-S911B** (last verified Android 17).
- Run targeted checks for touched modules as appropriate. Application compilation and APK assembly run through GitHub Actions, not locally.

## 2. Rules the user has set (follow them)

### Build and rollback policy (user instruction, 2026-11-07)
- Build application APKs only through GitHub Actions. Do not compile APKs on this PC.
- Do not create local Git commits or automatically commit/push working-tree changes for a build. A build request alone does not authorize a local commit.
- Keep the previous verified version available for rollback; do not rewrite history or overwrite its release artifact without explicit authorization.
- GitHub Actions needs a source revision available on GitHub. If requested changes exist only locally, explain the required source publication step and obtain explicit direction rather than silently committing them.
- These instructions supersede older local-build commands and task-specific assumptions that a GitHub build request implicitly authorizes committing locally.

### Git
- Work on branch **`kshitij`** (tracks `origin/kshitij`). Push only `refs/heads/kshitij`
  (`git push origin refs/heads/kshitij:refs/heads/kshitij`). **Never push `master` or `lochan`.**
- **Commit or push only when the user explicitly asks.** Never stash, reset, rebase, force-push or
  discard changes unless explicitly told (the user did once say "rollback and scrap" — that was explicit).
- Pulling Lochan's work: `git fetch origin` then **`git merge --no-ff origin/lochan`** (never fast-forward).
- `refs/heads/kshitij` is ambiguous with an old remote also named `kshitij` (harmless warning; use full refs).
- Release builds stay off git until the user says "commit build version to git".
- Task-specific exception (2026-11-05): the user explicitly authorized importing only missing Lochan updates, building on GitHub, then fast-forwarding master and updating the public APK. Both branches now contain that verified source; no force push/history rewrite was used.
- Commit messages end with the Co-Authored-By / Claude-Session lines given by the session.

### Phone / installs
- Before installing: check the focused window (`dumpsys window | grep mCurrentFocus`); never tap
  while the user is using the phone (no taps have been needed for installs).
- Check erhu is not running (`pidof com.lochanpthakke.empee3`) so playback is not interrupted.
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

## Animated onboarding redesign (2026-11-06)

- The user supplied the premium onboarding brief and allowed the phone for 20 minutes. Saved 36 raw screenshots and 10 validated muted recordings in `work/onboarding-redesign-20261106`; all 12 baseline pages and actual feature surfaces were captured. Finished in 16m50s, restored screen-awake to 0, removed temporary phone captures and returned the phone to Home. No playback/EQ changes, repairs, file moves, data clearing or production reinstall were performed.
- UI changes are scoped to `app/.../onboarding/OnboardingScreen.kt`, new `OnboardingVisuals.kt`, `ToolGuideContent.kt` and the existing onboarding interaction regression test. Preserve the real 12-page order, conditional VYBE/widgets pages, tool explanations, permission handling, Skip/Back/Next, completion persistence and preview-selection chips. Do not modify audio, metadata providers, Transition Studio, scanning or other navigation for this redesign.
- Shared native Compose visuals provide procedural welcome waves, artwork/grid/document/lyrics/EQ/crossfade/widget motifs and a finish logo treatment. Existing preview images are reused. No packaged media assets, runtime dependencies or video players were added. One native clock per visible, resumed page drives draw/graphics layers with cached paths/strokes; hidden/offscreen pages stop their clock and retain their pose. Reduced motion freezes the current pose without snapping to zero. Back/Next can interrupt page motion; targets are clamped and completion remains explicit.
- Local checks passed: changed-file ktlint and Detekt, five page-order/restoration tests, three UI interaction tests, two rendered-page/motion tests and four onboarding preference-persistence tests. All 12 pages were rendered with actual Compose/Robolectric native graphics; real pixel changes while active and identical frames when hidden/reduced-motion were checked. The local emulator lacks a hypervisor, so validation reused the catalog's Robolectric tools through the untracked `work/onboarding-redesign-20261106/checks.gradle`; no test/runtime dependencies were added to production Gradle configuration. Android debug and Android-test APKs compiled for the isolated `.bench` ID. Saved previews and validation are in desktop `outputs/erhu-onboarding-redesign-20261106`.
- New-design normal-speed/performance checks on the S23 remain pending: phone access ended as requested after capture. Do not describe reference capture as device validation of the new UI. Do not install a production-ID debug APK, reset onboarding completion on production, or interrupt the user's phone.
- The initial brief prohibited commits/pushes; the user's later explicit instruction to build through GitHub authorizes the necessary onboarding-only commit/push to kshitij and signed Actions artifact build as **1.0.7 (8)**. It does not request master promotion, public release publication, a broadcast or phone installation. Check the actual Actions result and verification.json before claiming a successful signed build. Preserve unrelated JAudioTaggerWriterTest changes and all private scratch files; never stage work/ or capture recordings.
- GitHub build completed: source `894f3bfb408d574b67d6ecaf25643eaecb95641c`, [Actions run 42095225371](https://github.com/lochanpthakke/erhu/actions/runs/42095225371), **1.0.7 (8)**. All CI tests/release lint/optimized ARM64 build/signature checks passed. Downloaded artifact verified against its checksum and original certificate; SHA-256 `eb53d2105e49acf716d216787ac481076b47847bc9410c71b870b74fd2157339`. The new visual code was also verified in APK DEX. No video files are packaged. Signed artifact is saved in desktop `outputs/erhu-onboarding-ci-42095225371`. Only kshitij was pushed for this task; public release/master and the phone remain on the prior version.

## Update checker, free announcements and current release (2026-11-05)

- Last verified S23 installation: **1.0.6 (versionCode 7)**, source `4670c400e17f4c7f67cb464fcf2a16009657d382`, Actions run 42074317364. Installed in place on S23 `RZCXA1H43GW`, lastUpdateTime 2026-11-05 23:55:49 IST. Installed APK SHA-256: `2b41fd621b19ba60f67d7ddae9dca7b924dce2b74cc7ab5a5ce59c29eb008c33`. The onboarding 1.0.7 build has not been installed.
- Production package remains `com.lochanpthakke.empee3`; original signing certificate SHA-256 remains `5bb0fc457b96d9fb6cbc606009de44d633bee95b58fec2dc633a7a4eb54a9ce5`. Never install a production-ID debug build, uninstall production, clear data or overwrite music. Release app disallows run-as data backups; the prior installed APK was backed up and install -r preserved app data.
- Official public repository: `lochanpthakke/erhu`. Update checker reads published release lists, includes the current beta channel and compares verification.json versionCode/versionName with actual installed identity. Manual/monthly coordination, download/signature validation and Android installer confirmation remain intact. Keep release tag numbers consistent with the APK version; mismatched metadata is deliberately rejected.
- Current distribution: [erhu 1.0.6 beta](https://github.com/lochanpthakke/erhu/releases/tag/erhu-v1.0.6-beta.1), versionCode 7, source `4670c400e17f4c7f67cb464fcf2a16009657d382`, verified [Actions run 42074317364](https://github.com/lochanpthakke/erhu/actions/runs/42074317364). Uploaded APK SHA-256: `2b41fd621b19ba60f67d7ddae9dca7b924dce2b74cc7ab5a5ce59c29eb008c33`. Publish only the already verified Actions APK, its checksum and matching verification.json. Preserve the existing VYBE model pack under v1.0.2-beta.1; do not rewrite old tags or upload APK binaries into Git source history. The user authorized master promotion after successful CI and this release update; branch history rewrites and announcement broadcasts remain unauthorized.
- Free Firebase project `erhu-app-25b513` and production Android registration are provisioned; owner accepted Firebase terms. **Blaze was declined. Do not enable billing or deploy Functions.** Use `backend/announcements/firebase.spark.json` and trusted local `functions/spark.mjs`: static Hosting JSON plus FCM HTTP v1, no always-running cloud backend. Actual API base: `https://erhu-app-25b513.web.app/api`.
- Public Firebase client IDs/API URL are external Actions variables consumed by release.yml. Private OAuth/admin credentials and device registration tokens stay outside Git/APK/Hosting/logs. Never read or modify `jwt.txt`. Private local publisher state and setup records are under untracked `work/firebase-setup`; preserve them, never stage that directory. Publisher defaults to a single test token; broadcast additionally requires explicit --all and user authorization.
- Test notice `63cfb1e9-1d54-4dc4-934c-81d650bebd10` (body hello, versionCode 6 only) was accepted by FCM and visually verified in the actual erhu popup after owner unlock. It is excluded from the public feed. Temporary same-certificate read-only registration probe was removed. No broadcast, billing upgrade or user-data clearing occurred.
- Validation: 11 backend tests passed; Actions Android unit tests, release lint, optimized ARM64 build, identity and signature checks passed. All five actual configuration values were verified in APK DEX. Actual installed APK hash was checked. Earlier isolated persistence/permission-denied startup tests remain valid; background push/notification taps, cloud scheduling and remote deactivation were not end-to-end tested in this delivery session. Local sync must run when scheduled notices become due; Spark has no cloud scheduler here.
- Full implementation/deployment details: [docs/updates-and-announcements.md](docs/updates-and-announcements.md). Preserve unrelated JAudioTaggerWriterTest changes and scratch work. Future APK versions must increment code beyond 10. Reuse verified CI artifacts; no repeat local release compilation.

## Selective Lochan import (2026-11-05)

- Imported net missing application-source changes through `origin/lochan` commit `5c18a91fd6ec4f6375d83810ddce71d2a086ef36`: MetadataBatchWorker progress/completion/cancel notifications and pending-track selection, MetadataRepairService orchestration, ambiguous-match review entry points and revert-selection fixes. Existing keyless providers, artwork, update checker, announcements and unrelated local work were preserved.
- This was a path-limited source import, not a whole-branch history merge. IDE and local credential files/history were excluded. These source updates are already included; do not re-import them just because their original commits are not ancestors. Preserve the unrelated JAudioTaggerWriterTest working-tree state and private scratch directory.
- Unit tests, release lint, ARM64 APK compilation, identity/signature checks and uploaded-byte verification passed. Master was fast-forwarded only after successful CI. No local APK compilation or phone installation was performed. Newly imported background repair/review interactions still need on-device validation. The automatic tag-triggered duplicate build was cancelled; published bytes are the successful CI artifact above.

## Current metadata and artwork implementation (2026-11-04)

This section supersedes older artwork-provider notes. The user subsequently requested building the latest changes through GitHub Actions and installing them. That authorizes the necessary commit/push to `kshitij` for a signed 1.0.3 (4) artifact. It does not authorize a public release or changes to `master`/`main`. Do not discard unrelated working-tree changes. Build/installation confirmation must come from the actual CI and device results.

- Restored implementation report: [docs/artwork-retrieval.md](docs/artwork-retrieval.md). Detailed retained measurements: [docs/artwork-accuracy-results.json](docs/artwork-accuracy-results.json). The report was found empty and reconstructed from these records on 2026-11-04; do not treat reconstruction as a new test run.
- Album artwork uses one shared `MetadataRepository` / `AlbumArtworkFinder` pipeline: MusicBrainz release identification, CAA exact approved front covers, CAA release-group fallback, Deezer album search, public iTunes album search, then TheAudioDB.
- Removed artwork sources: Genius, DuckDuckGo, Discogs, Wikipedia/Wikimedia and the exclusive Wikidata resolver. The preceding uncommitted key cleanup also removed Last.fm, Fanart.tv and Google image search. Tag matching retains iTunes, MusicBrainz and Deezer; artist photos use Deezer separately. Do not restore obsolete provider options or user API-key fields.
- TheAudioDB public key `123` is for permitted debug/testing use. Release builds skip it unless the app owner configures `THEAUDIODB_API_KEY` or untracked `theaudiodb.apiKey`. No private key is hardcoded or committed; request-path credentials are redacted. Musixmatch lyrics configuration is separate and remains intact. The old Fanart proto field remains inert for compatibility.
- Matching uses album/artist, IDs, year, edition, release type and decoded dimensions. Promotional/box-set/alternate editions and uncertain matches require review. Release-group covers require edition confirmation and must not claim an unrelated exact release ID. Preserve a release's own artist credit when resolving a recording ID.
- Candidate grid/preview shows source, matched identity, edition/year, actual dimensions and recommended/review status. Applying a preview is explicit. Searches must never modify tags or overwrite existing custom covers. Preserve manual editing and local cover selection.
- Private durable cache: seven-day query freshness, 256 query records, 64 MiB image budget. Preserve bounded downloads, cancellable calls, shared MusicBrainz pacing, provider failure isolation and stale-UI protection. No playback, scanning or database-schema changes were made. Do not touch the already-fixed Transition Studio.
- Accuracy: 18 curated album-level cases, with observed local track tags recorded where available. Visually correct cover available in 16/18 (88.9%); correct album/artist 17/18 each (94.4%); exact edition independently verified in 9/18 (50.0%). Seven automatic recommendations were all visually correct in this sample, not proof of general accuracy. No usable expected cover in 2/18; zero candidate images in 1/18. Mean response 16.05 seconds with live metadata and previously validated image bytes cached.
- Known gaps: requested Nirvana deluxe artwork missing; contradictory Discovery/Queen query rejected; group/community candidates do not prove exact editions. Deezer returned no album candidates in the test environment; MusicBrainz/CAA had intermittent failures/timeouts and Apple missed known albums. TheAudioDB test availability is not a production guarantee.
- Validation already completed: 554 JVM tests passed (network 348, library 206); the opt-in live evaluator ran separately; isolated debug/app-test builds, changed-file ktlint and whitespace checks passed. S23 grid/preview/cancel/cache/source-menu checks passed. One Android test passed in 13.939 seconds, applying online artwork only to a generated private-cache WAV, preserving manual tags/custom artwork during search, and verifying cache access with networking blocked.
- Phone safety: test using `-PbenchApp -PtargetAbi=arm64-v8a` and confirm package `com.lochanpthakke.empee3.bench` before installing. Temporary `.bench`/`.bench.test` apps were removed; screen-awake setting restored to 0. Production app `com.lochanpthakke.empee3` stayed 1.0.2 (3), lastUpdateTime 2026-11-03 23:54:39. Do not install a production-ID debug APK or uninstall the real app for testing.

---

## 3. Completed and committed work (newest first)

### 3.17 erhu identity, Home/lyrics, radio details and CI release (2026-11-01)
- Implementation committed as `12bccfd`, SDK package correction `4033e81`, and CI unit-test task
  correction `a2d3cb2`, all pushed to `origin/kshitij`. The user now explicitly requests the
  current project state on both `origin/kshitij` and `origin/master`; this is an exception to the
  normal kshitij-only push rule. No force push or history rewrite is authorized.
- Display name/namespace are erhu / `com.erhu.erhu`; installed application ID remains
  `com.lochanpthakke.empee3`. Room 14, storage/wire identifiers, launcher/widget/service/worker
  compatibility and the original signing certificate remain preserved.
- Home separates Featured Radio Stations and Featured Albums, followed by Recently Played Albums.
  Synced lyrics retain embedded/LRCLIB support and add a configurable official Musixmatch fallback,
  failure isolation and separate automatic disk caching. Musixmatch requires a licensed subtitle
  API key and remains unconfigured; live provider availability has not been validated.
- Featured station cards now open details without playback/enqueueing/toast. Play Radio reuses
  the displayed generated plan; song rows/actions use existing playback/library infrastructure.
  Radio/VYBE ranking and Transition Studio functionality were not changed.
- GitHub Actions run 41302362949 succeeded: 1,009 JVM tests, release lint, optimized ARM64 APK,
  metadata and original-certificate verification. Three host workflow tests also passed.
  Signing secrets were provisioned with the user's explicit approval using the existing key;
  no key, password or token was committed. Public release publication was not triggered.
- S23 now has the CI-built **release** 1.0.1 (2), non-debuggable, installed in place with data
  preserved. APK SHA-256: `c20c893e328ff55c234f84a5b1d0b22dab108dcc03b6a72e927f24409486e747`.
  Do not reinstall an older local debug APK when asked to install the current version.
- The preceding install had accidentally used a debug/unminified APK with frame diagnostics.
  Short S23 checks: Home jank 11.75% -> 4.05%, p95 44 -> 20 ms; navigation p95 25 -> 18 ms;
  queue p95 29 -> 11 ms; popup p95 48 -> 32 ms, but popup jank 10.48% -> 11.15%.
  Warmed PSS 431,988 -> 303,066 KiB. Content/artwork changed between runs and battery temperatures
  were 37.7/38.5 C, so these are representative observations, not a controlled benchmark.
- Device radio checks passed for opening, scrolling, menus, favorite toggle/restoration and Back.
  Opening/closing retained paused playback, position and current item; no app toast was observed.
  Play Radio/row playback were not pressed on the user's real queue. No settings or queue edits
  remain from testing. See `docs/radio-navigation-and-release.md` for evidence and limits.


### 3.16 Opt-in lyrics, artwork compression and Liquid Glass menus (2026-10-30–31)
- At the user's explicit request, committed the completed work together as **`b25a22c`**,
  `Add opt-in lyrics, artwork compression and Liquid Glass menus`: 56 files, on `kshitij`.
  Working tree was clean after this commit. **Nothing pushed.** This instructions update is
  subsequent documentation work; do not assume it is committed or commit it without a request.
- **Lyrics:** online lookup is opt-in; explicit offline downloads are separate from transient
  cache, with saved-download removal confirmation. Existing embedded lyrics and playback behavior
  are retained. See the lifecycle details in `docs/storage-lifecycle-audit.md`.
- **Storage/artwork hardening:** Phase 3 HLS assembly, validation, staged publication and recovery
  are recorded in `docs/storage-lifecycle-audit.md`. Earlier user-authorized downloaded-artwork
  removal was a one-time action, not permission for future removal or app-data clearing.
- **Hardware experiment:** Phase 4B corrected explicit SAR 1:1 and top-left chroma signalling
  without changing coded picture samples. The user personally accepted the S23 full-motion
  comparison ("its good"). Do not repeat or request that approval again. Original Graduation
  SHA-256: `3101f2316857b74d772dac317cab718f9fce07a5b2e479e3bd6b8ab77eb8e416`.
  See `docs/android-artwork-encoder-experiment.md`; no 4 Mbps candidate was attempted.
- **Opt-in compression:** the existing album live-cover popup exposes compression only for a
  conservative eligible AVC 1080×1080/30 FPS subset. Uses HEVC Main 6 Mbps VBR, preserves explicit
  SAR/chroma and 8-bit BT.709 limited-range SDR, and handles actual YUV plane strides. No automatic
  or batch compression, dependency or schema change. Original remains until metadata, cadence,
  full decode and worthwhile savings pass; existing atomic publication then replaces it.
  Cancellation, source fingerprints, owned staging recovery and download/delete coordination
  are covered. Successful intentional compression does not retain an undo copy of the original.
- Actual isolated S23 encoder/transaction checks passed: cancellation preserves the original,
  disposable-copy publication succeeds, and HEVC recompression is rejected. Graduation result:
  **18,113,173 → 11,188,275 bytes (38.2313% saved)**; 1080×1080, 30 FPS, 450 frames, 15 seconds.
  PC full decode and independent bitstream validation passed. No production artwork was compressed
  by these probes. Phase 5 regression checks: **693 tests passed**; debug/benchmark/release builds
  completed. Detekt retains model 1, network 63, database 22 and library 87 findings, including a
  new method-count warning on the extended existing repository; no rules were weakened.
  Full settings, evidence and limitations: `docs/opt-in-artwork-compression.md`.
- **Unified menus:** shared `GlassMenu`, `GlassMenuItem` and `GlassMenuPositionProvider` replace
  eight overflow implementations: album detail, artist detail, shared Library song rows,
  Now Playing, online lyrics, playlist detail, Transition Studio and Transition Editor.
  Shared rows cover Home, search, tracks and Smart Playlists. VYBE/featured cards had no separate
  overflow implementation. Form selectors and the dedicated live-cover popup remain separate.
  Original actions, callbacks, enabled conditions and confirmation flows are preserved.
  Supports sections, icons, secondary/destructive/disabled states, bounded scrolling, RTL/edge
  placement, keyboard/back/outside dismissal and repeat-selection protection.
- **Liquid Glass refinement:** `LiquidMenuSurface` reuses existing `glassPanel` drawing for
  translucent graded fill, restrained upper/corner reflections, inner rim and soft shadow.
  Existing anchored spring scale, fade, settling/overshoot and reverse exit are retained;
  28 dp corners and comfortable rows remain. No continuous shader or animation, new dependency
  or true cross-window backdrop blur. Glass disabled uses a solid neutral fallback.
- `rememberPlaybackArtworkColors` / `LocalPlaybackArtworkColors` reuse `ArtworkLoader` and
  `DominantColorExtractor` for the **currently playing** track, independently of the browsed
  album. A stable shell-provided state avoids reading playback position; only open menus read
  the palette. Fill, reflection and rim tint animate over 300 ms. Missing artwork falls back
  smoothly to neutral. `LocalMenuGlassTokens` preserves menu glass inside Now Playing's existing
  `WithoutGlass` scope without redesigning the rest of the player.
- Final menu validation: **248 tests passed** (design system 52, library 195, app 1); ARM64 debug
  app and Android interaction-test APK assembly passed. KtLint on all nine changed Kotlin files
  and whitespace checks passed. Detekt retains existing design-system 7, library 87 and app 14
  findings, none in changed menu/palette/shell files. The eight screen menu files were unchanged
  by the final material refinement. See `docs/unified-overflow-menus.md` and
  `docs/liquid-glass-overflow-menus.md` for implementation and validation details.
- **Still unverified:** the user selected **Skip device checks** for menus. Interaction tests
  compiled but were not run on the S23. Gloss, live tint transitions, OEM positioning and
  scrolling/playback performance have no device visual/performance pass. Installation success
  is not UI validation. No release build was created for the final Liquid Glass refinement.

### 3.15 Lochan pull: widgets and artwork/builder fixes (2026-10-28)
- Pulled `origin/lochan` through `332f44f` into `kshitij` using a non-fast-forward merge.
  Includes `54d81a1` and `332f44f`: Android and glass/iOS-style home-screen widgets, artwork
  handling and Library Builder fixes, plus upstream formatting. No merge conflicts or local
  source workarounds. Existing current-version history through `f291c7d` is retained.
- `:app:assembleDebug` and app/common/database/network/library `testDebugUnitTest` pass:
  634 tests, zero failures/errors. `git diff --cached --check` passes. Release and on-device
  widget behaviour were not tested in this pull. No app installation or phone-data changes.
- Signing configuration/package identity are unchanged; the new debug APK verifies against
  the existing certificate SHA-256 recorded in 3.14. No key regeneration. Nothing pushed.

### 3.14 Lochan merge and update signing (2026-10-26)
- At the user's request, merged `origin/lochan` (`bd8c11f`) into `kshitij` with `--no-ff`.
  Retains current work through `0e81e71`; adds `310353a` (Library Builder UI, drawer tint control,
  metadata repair queue clear/select-all controls) and `bd8c11f` (named albums merge across folders).
  Merge was conflict-free; the existing local 2026-10-24 installed-build note below is preserved.
- Debug and optimized release builds pass; 347 tests pass (database 165, library 181, app 1;
  settings has no unit tests). Initial incremental compilation failed to resolve the unchanged
  `chooseDisplayName` helper; retry with `-Pkotlin.incremental=false` passed without source changes.
- Signing configuration, application ID and database schemas were unchanged. Both new APKs were
  verified with `apksigner` against `build-library-builder-theme/real-release.apk` (the documented
  last installed APK). All use certificate SHA-256
  `5bb0fc457b96d9fb6cbc606009de44d633bee95b58fec2dc633a7a4eb54a9ce5` and package
  `com.lochanpthakke.empee3`. Keep this local key; do not regenerate it or uninstall to update.
- Paired APKs saved outside Git: `D:\android-toolchain\build-merge-lochan-20261026\`.
  Phone was disconnected; no installation, data clearing or on-device validation was performed.
  The user requested merge + commit only; no push requested.

| Commit | What |
|---|---|
| `7ca30e7` | Tests follow Lochan's live cover resolution choices |
| `36da3e5` | Merge `origin/lochan` `527adbb`: live cover resolution choices on the album page (proto `preferred_live_cover_resolution = 35`) |
| `3f2ec02` | **Library Builder moves files**, **glass rollback with artwork tint**, **smoother drawer**, Animated Artwork out of the drawer, R8 route keep rule; see 3.13 |
| `99b203a` | **Album page: download the album's live cover** (check on open, glass button + popup, cancel/remove, duplicate-download and delete fixes); see 3.12 |
| `69fcd96` | Navigation: first tab reads **Home** with a house icon (enum still `TRACKS`) |
| `7954fbd` | **Live covers start at once** (Apple's 10 s timestamp offset rebased to 0); see 3.12 |
| `29ef0c1` | **Live album covers** in Now Playing and the album header (OOM fix, one shared cover player, buffering deadlock fix, TextureView); see 3.12 |
| `891b754` | **Animated artwork fetcher** fixes: explicit edition, other storefronts, phone-sized download, HTTP 429 pause; see 3.12 |
| `b84d0ac` | Merge `origin/lochan`: animated album artwork (Apple motion covers), Library Builder, matching fixes. Now Playing conflicts: kept ours, ported his artwork; see 3.12 |
| `9d3c3e8` / `41b88af` / `86f62a0` | Now Playing: **album stack toggle**; Queue button shows only Queue + History tabs; **control rows** (+ and Queue) and transparent top bar; see 3.12 |
| `d022cd4` | Merge `origin/lochan`: Library Organiser, colour mapping fix, frostiness sliders (proto: `ui_glassyness = 32`, `sidebar_glassyness = 33`) |
| `0189dc5` | Expanded Now Playing drawn **without Liquid Glass** (lagged, looked the same); everything else keeps glass |
| `d83dbf6` | Complete-card player expansion/collapse; see 3.11 |
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
- LRCLIB provider: `api/get` exact match then `api/search`; User-Agent `erhu/1.0.0 (…)`; one
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

### 3.11 Complete-card player expansion/collapse (2026-10-20)
- User requested one coherent transition: neighbours unfold/fold behind the central card, no
  whole-page jiggle, and the full card (not detached artwork) travels into the mini player.
- Removed `ExpandingPlayer.TravellingArtwork`. `TrackCarousel` now moves the existing full
  carousel in one uniform layer; `PlayerCardTransform.kt` maps the measured thumbnail to the
  exact expanded artwork bounds. Its layout measurement lives outside the moving layer, so
  drawing cannot retarget its own geometry. Final card sizes, perspective, colours and layout
  remain unchanged. Neighbours use a bounded reversible smoothstep of the same fraction and
  stay behind the current card while opening/closing. No new artwork loader or dependency.
- `PlayerTransitionState.fraction`: direct synchronous pointer feedback, bounded 0..1; release
  hands the exact drawing value to the existing spring. Thresholds/directions are unchanged.
  Back is owned while the player is visibly closing or dragged, via a derived Boolean.
- Removed the independent `arrival` impulses and their content/mini-player transforms. The
  critically damped journey spring now supplies all settling, with no second page kick or
  thumbnail squash. Other approved button/selection spring feedback remains intact.
- Removed the redundant whole-player opacity fade (it dimmed the travelling card before the
  opaque mini artwork took over). The reveal mask starts at the thumbnail and its own corner;
  backdrop/section reveals remain animated. Card metadata fades late into the normal mini
  representation; artwork corners match at handoff. Resting Liquid Glass mini/top-bar styling
  is unchanged. Playback/DSP, queue, lyrics timing, metadata, VYBE and schemas were not changed.
- 151 app/library unit tests pass, including geometry, high-velocity bounds, unchanged fling
  thresholds, direct/reversed dragging, mid-animation takeover and rapid command retargeting.
  Debug/release APKs build; app/library ktlint and app detekt pass. Library detekt retains 27
  existing findings, none in changed files. A transient Android lint internal analyzer crash
  was resolved by retry; no lint/build gates were weakened.
- S23 debug AND final release checks/recordings passed repeated cycles, short slow return-to-open, fast/long
  collapse, Back, reversal, cancellation and playback through collapse/reopen. Track/queue
  stayed Sweet Home, 2/30; playback was paused and seek restored to 0 (MediaSession verified).
  The release APK installed at 01:07 matches the saved release SHA-256:
  `992c0a13449b9bf8a55e9d113c66be156e0456c579ae597daad044af7bcd8f44`.
  Final verification and exact changed files are in the task's `outputs/player-transition-report.md`.
- Do not force short synthetic ADB strokes to count as flings: diagnostics measured 0.85/0
  screen-heights/sec for the initially failing assertion, below the existing 1.2 threshold.
  Temporary recognition/logging experiments were removed; genuine velocity overrides are
  unit-tested. No artificial velocity or new gesture threshold was introduced.
- No numerical frame-performance improvement is claimed. Other screen sizes, rotation,
  cold/missing artwork, both themes and reduced-motion device settings are not exhaustively
  verified. This is transition work, not the full Smoothness Engine. Do not commit/push unless asked.

### 3.12 Now Playing controls, album stack, Lochan merges, live covers (2026-10-21)
- **Controls** (`NowPlayingParts.TransportControls`): two rows on one five-column grid: shuffle,
  previous, play/pause, next, repeat; below: Add to playlist (under shuffle), album stack toggle
  (under play), Queue (under repeat). Add to playlist uses the existing `AddToPlaylistDialog` for the
  playing song; its message shows in Now Playing's snackbar (`PlaylistsViewModel` / dialog
  `onResult`). Queue opens `QueueSheet` with only Queue + History (`QUEUE_SHEET_QUEUE_PAGES`); the
  overflow menu keeps all four tabs. Top bar is transparent (the old band was the "legacy notch").
- **Album stack toggle** (`TrackCarousel.depthCard(stack)`): one 0..1 value (Motion.settle, read in
  draw only) gathers the neighbours behind the current card (next album at 106%, previous fades out;
  previous at the end of the queue). Neighbour taps are disabled while stacked. Default: three cards.
- **Merges with `origin/lochan`**: `d022cd4` (proto field 31 clash: our `hidden_from_recommendations`
  kept 31) and `b84d0ac` (his "kshitij UI sync" rewrote Now Playing from an old copy; we kept ours and
  ported his animated artwork). `b84d0ac` brought **`jwt.txt`** (likely a token) into the branch:
  ask the user / Lochan before anything else touches it; the auto-mode classifier blocks reading it.
- **Animated artwork fetcher** (Lochan's feature, `core/network/apple`): iTunes search may return only
  the clean edition, so the artist lookup supplies the explicit one; other storefronts are tried;
  manual fetch re-resolves; the Fetch screen and background path download the largest variant with
  short edge <= 1080 px (`HlsPlaylistParser.displayVariant`); HTTP 429 pauses Apple requests
  (`AppleRequestGate`, `RateLimitException`). "$ome $exy $ongs 4 U" has NO motion cover on Apple in
  any storefront checked (public pages confirm); SOS does.
- **Embedded artwork**: the user's library (`Music/MUSICtest`) has only JPEG covers (scanned every ID3
  frame / FLAC block on the S23). Live covers come only from the fetcher (`no_backup/artwork/animated`).
- **Live covers** (`components/AnimatedAlbumCoverPlayer.kt`): one `CoverVideoHost` per Now Playing (only
  the current card shows video; carries on across tracks of an album); the album header releases its
  player while Now Playing covers it. Lessons from device logs: default ExoPlayer buffer = ~130 MB per
  player (OOM with 2-3); fragmented-MP4 covers overstate the buffered position, so ANY read-ahead time
  limit shorter than the file deadlocks (keep ExoPlayer's 50 s, cap 64 MB bytes); TextureView must not be
  drawn at alpha 0 (no surface). Verified on the S23: album header + Now Playing animate, loop at 33 s,
  skips keep the same player, no dropped frames, no crashes.
- **10 s start delay (`7954fbd`, 2026-10-22):** Apple's cover fragments start at `tfdt` 240 / timescale 24
  = 10.0 s (Apple's segmenter offset; fragments 1-8 all present, nothing missing). Joined into one MP4,
  ExoPlayer's clock starts at 0, so the first frame was due at 10 s. `FragmentedMp4Timeline.startAtZero`
  (core:network) rewrites only the `tfdt` values in place (15 bytes per SOS file); called by
  `HlsMp4Assembler` and once before playback for covers downloaded earlier. APKs: `build-cover-start\`.
  The user confirmed on `.bench` that it works; installed as release on the main app 2026-10-22 20:33
  (SHA-256 `d91e831e…918b4`). Backup: `dbbackup\pre-cover-start-install-20261022-203252`. `.bench` removed.
- **Live cover download on the album page (`99b203a`, 2026-10-22; the user checked it on `.bench`;
  installed as release on the main app 23:04, SHA-256 `47c6ce8e…`, backup
  `dbbackup\pre-live-download-install-20261022-230347`; `.bench` removed):** source = the
  same Apple fetcher (nothing is embedded in the user's files). Opening an album runs
  `AnimatedArtworkRepository.checkLiveCover` (user chose "check on open, cached"): saved file → Saved;
  NO_MATCH (14 d) / NO_ANIMATION (60 d) → hidden; else Apple lookup without downloading → new status
  `FOUND` + session cache; network errors are not remembered. Size = the variant playlist's byte ranges
  (`HlsMediaPlaylist.statedBytes`), else none: `estimatedSizeMb` assumes 5 s and is wrong (SOS ≈ 28 MB).
  UI: `LiveCoverViewModel`, `components/LiveCoverControl.kt` (glass button under the header's end button,
  anchored popup). Download = `fetchAndStoreArtwork` (now one job per album+orientation via LAZY job +
  `putIfAbsent`; `cancelDownload` restores FOUND and deletes the .tmp; the assembler checks cancellation).
  `deleteArtwork` now also deletes the stored file names (the normalized names miss capitals on Android).
  Caveat: for albums with no motion cover, `resolveAlbum` tries ~10 storefronts (one Apple Music request each).
- Backups: `adb exec-out run-as … tar` TRUNCATED at 89 MB on 2026-10-22 (tar "Unexpected EOF"). Use
  `adb shell "run-as <pkg> tar -cf - . > /data/local/tmp/x.tar"`, `adb pull`, `tar -tf` check, then rm.

### 3.13 Library Builder, glass, drawer, Lochan merge (2026-10-23)
- **Library Builder (Lochan's organiser) did not move files.** Cause, proven on the S23 with logs: both folder pickers
  (`SettingsScreen`, `MainScaffold`) persisted READ-only tree access, so every SAF write failed silently after a
  restart/reinstall; without All Files Access (not granted on the S23) `File.renameTo` fails too. Pickers now persist
  read+write. **The user must re-pick the scan folder once** (Settings → Scan location) before organising MUSICtest.
  Verified with copies in `Music/LBTest`: 3/3 moved via `moveDocument` (+ rename to the title), checksums identical;
  Force Re-organize → "Already in place: 3".
- `SafFileMover`: one `OUTCOME=` per file in logcat tag `LibraryBuilderMover` (all-files flag, persisted grants,
  every physical/SAF step). Never deletes/overwrites an existing destination (even 0 bytes), deletes the source only
  after a size-verified copy, undoes its own copy if the source cannot be removed, renames in place when only the name
  differs. `ExecutionResult.failureReasons` shown on the finished screen. "Scan another folder…" scans a folder
  WITHOUT changing the scan location (changing it rescans the library: never test on the real scan folder).
- Empty-folder cleanup (`LibraryBuilderIntegrator`) confirms emptiness with a direct child query before deleting:
  `DocumentFile.delete()` is recursive and `listFiles()` returns empty on query errors.
- Tags are read by file path: without All Files Access that only works for files MediaStore has indexed (a
  `.nomedia` folder gives "Unknown" tags / MISSING_METADATA).
- **Glass rollback:** Lochan's frostiness merge made frosted fills use the full panel alpha (old: ×0.3) and 1.5× blur,
  ignoring explicit fills ("just blurring"). Reverted in `Glass.kt`; `frostFactor` now only softens the blur
  (1 = original; drawer slider default 0.85). The "UI Elements Frostiness" slider is not read anywhere.
- **Tint:** `GlassTokens.tint` = background colour at `TINT_ALPHA` 0x52 for `dominant_color` / `gradient_ui` only
  (`ErhuTheme`), composited into the fill colour (no extra draw). Explicit fills (Now Playing cards, drawer) untouched.
- **Drawer:** Animated Artwork entry removed (route + screen kept). Trace showed the shell re-records on every frame of
  the drawer slide (`GlassSource:shell` ×49/51; the menu button's press animation is one cause), so the drawer
  re-blurred every frame. Now `glassPanel(stillBackdrop = true)` renders ONE blurred bitmap of the shell per opening
  (`rememberStillFrost`, `GraphicsLayer.toImageBitmap`) and draws its region; no edge lens on the drawer.
  Baseline before: 41% janky, p50 28 ms, p99 65 ms (5 open/close, `drawer-loop.sh` in the session scratchpad).
  The final build was NOT cleanly measured (the user was using the phone during the run).
- **R8:** a `@Serializable` route object whose INSTANCE is no longer used (drawer entry removed) lost its serializer
  and crashed the release at startup ("Serializer for class 'wi'"). Keep rule for `com.erhu.erhu.navigation.**`.
- **Lesson:** debug builds feel laggy (gfxinfo 30% janky, p99 300 ms); after testing with a debug build, put the
  release back. Smoke-test every release on `.bench` (install, launch, check FATAL, uninstall) before the main app.
- Lochan's `527adbb` (merged): each album check now sizes every HLS variant (+ the chosen one again) — ~4 Apple
  requests per album instead of 1; his `AnimatedArtworkRepository.kt` fails ktlint. Left as his.

### 3.14 Smart Radio (2026-10-24)
- `mix/RadioEngine.kt` (pure, 17 tests in `RadioEngineTest`): seed = song / album / artist (`RadioSeed`); start song =
  the seed song or the album's / artist's most-played track. Score = VYBE similarity to the seed songs (stored vectors
  via `VybeEngine.tasteNeighbours`, never the model; skipped without an index) + genre overlap + trusted-tempo
  closeness (`Moods.tempo`, 70-175 only) + same artist (0.8 for an artist radio, else 0.35) / same album (0.15) +
  familiarity (lasting plays, favourites, liked artist) + 0.1 for never-played songs. Spread: ≤3 per artist, ≤2 per
  album, no artist twice in a row; caps relax in steps when the library is too small to fill 30, except the seed
  artist (artist radio ≤ a third) and the seed album (≤2 more). Hidden-from-Smart-Playlists songs excluded.
  `RadioPlan.basedOn` lists only signals that shaped a pick; the toast names them.
- `mix/RadioRepository.kt`: on demand only (one Room read, VYBE failure → metadata only), no background work.
- `replaceUpcoming(songs)` in `PlaybackQueue` / Controller / Repository (`player.replaceMediaItems(current + 1, …)`,
  like `shuffleUpcoming`): radio from Now Playing keeps the current song playing.
- UI: "Start radio" in every song menu (`SongActions.onStartRadio`), album and artist ⋮ menus, Now Playing ⋮
  ("Start radio", and "New radio picks" while the playing song is part of the radio: refresh avoids earlier picks
  while ≥10 remain). Saving = the Queue sheet's existing "Save as playlist". No Familiar/Discover toggle (follow-up).
- Installed as release 2026-10-24 00:39 (`build-smart-radio\`, SHA-256 `80dae802…`), backup
  `dbbackup\pre-smart-radio-20261024-003821`. Not yet tried by the user on the phone.

## 4. Scrapped (do not reintroduce without asking)
- **Word-by-word karaoke rework** (active-word-only highlight, LyricsClock smoothing with speed,
  manual "Time words" tool, local enhanced-LRC copies) — built 2026-10-11, the user scrapped it and
  it was removed before committing. The committed lyrics view still has the original basic
  enhanced-LRC word highlight, which online word-synced lyrics use.

## 5. Pending work and open items

### 5.12 ARM64 release installed on the real S23 (2026-10-28; report 2026-10-29)

Current daily installation: `com.lochanpthakke.empee3`, **ARM64 release**, versionCode `1`, versionName `1.0`.
The previous universal debug installation is historical. Future S23 updates should use
`:app:assembleRelease -PtargetAbi=arm64-v8a`, verify the installed/new signing certificate matches,
and update with `adb install -r`; do not accidentally replace it with the default universal debug build.
Existing certificate SHA-256:
`5bb0fc457b96d9fb6cbc606009de44d633bee95b58fec2dc633a7a4eb54a9ce5`.

| Android storage (decimal MB) | Before update | After verification |
|---|---:|---:|
| App | 163.22 | 50.05 |
| Data, including cache | 293.56 | 293.81 |
| Total | 456.78 | 343.86 |

**Total saving: 112.91 MB; app saving: 113.16 MB.** The APK itself is 37.17 MB; Android's
50.05 MB App figure also accounts for optimized code, filesystem allocation and installation overhead.
No model or saved artwork was removed. Data variation reflects normal cache, journal and playback activity.

- User explicitly authorized replacing the daily app with the optimized release; idle confirmation received.
  Built `:app:assembleRelease -PtargetAbi=arm64-v8a` (6s PASS), verified installed/built signing certificates
  match existing SHA256 `5bb0fc45...ce5`, then installed ONLY with `adb install -r`. No signing change/uninstall.
- Current REAL app: release, ARM64 only, package/version unchanged; update time **2026-10-28 22:26:39 IST**.
  APK SHA256 `398141e81d8735efc93dac881fe8dbdf2e2900d0c21460f5c66ff6c12d184f97`, reconfirmed October29.
  Supersedes the historical debug installation status in 5.11 and below. No `.bench`/checker app remains.
- Validated full pre-install backup + original APK: `D:\android-toolchain\dbbackup\pre-arm64-release-20261028\`.
  Tar291,389,952 bytes,77 files, every member validated. No restore/data clear/user-content removal.
- StorageStats before -> after tests: App163,215,872 ->50,054,656; Data INCLUDING cache293,560,320 ->293,810,176;
  Cache1,908,736 ->2,805,760; total456,776,192 ->343,864,832. App saving113.16MB; total112.91MB.
  Data has normal cache/WAL/playback/history variation; do not call it a model-data reduction.
- Same-key read-only instrumentation verified private data after release without reinstalling debug:
  immediately after update, model/index/3 videos/DB/preferences hashes and all DB logical digests identical;
  quick_check ok, schema14,208 songs. After playback only listening_stats/recently_played logical tables changed;
  songs/model/index/videos intact. Playback proto changed normally. No preference reset/migration.
- Checker first suppressed Application.onCreate, causing an uninitialized WorkManager workerFactory error
  on the original debug app. Corrected to normal startup; baseline still matched backup before release install.
  This was a harness failure, not an ARM64 release startup failure. Do not skip Hilt startup in future probes.
- Real-app Home->Albums->Home passed. VYBE208 songs/30 results659ms; full restart/query653ms.
  Addiction playback advanced1921->14576ms then paused, active queue item6 (7/208), no player error.
  No fatal/MediaPipe linker error in tested release processes; nonfatal device surface `libpenguin.so` warning
  recorded (library absent from both APKs). No audio-quality/full regression claim.
- Full report appended to `docs/app-size-and-storage.md`; evidence in task `outputs/arm64-install`.
  Checker uninstalled, own phone tar/XML deleted, real app reopened paused. Prior Home/Audio Details/Gradle
  source diffs unchanged. No application source edits, model/dependency/signing changes, commit or push.

### 5.11 App size / storage forensics (2026-10-28, uncommitted)
- Full measurements, commands, alternatives and limitations: `docs/app-size-and-storage.md`.
  Raw evidence/APKs in task workspace `outputs/size-forensics`; branch remains `kshitij`, HEAD `84579ad`.
  Prior Home/menu edits preserved. No commit/push, model migration, ranking/dependency change or data deletion.
- `app/build.gradle.kts`: optional `-PtargetAbi=arm64-v8a` for compatible-device sideloads;
  omitted keeps universal. All four existing ABI names accepted; invalid values fail configuration.
  No signing/version/package changes; no `keystore.properties`, so existing release debug-key fallback remains.
- Fresh universal release APK 136,723,989 bytes -> ARM64 37,165,991: saves 99,557,998 (72.82%).
  Common non-signature ZIP entries byte-identical; only 12 other-ABI libraries removed. R8/shrinking already on.
  Both MediaPipe libraries kept; no speculative JNI stripping. No bundled model. No large ELF debug sections.
  Download gzip simulation is not Play size; no AAB download measurement.
- Actual S23 isolated `.bench` StorageStats App: 149,710,336 -> 50,054,656, saving 99,655,680 bytes.
  Universal/ARM64 benchmark release builds and same-package updates passed. Seed was a selected copy of
  the latest backup; real package was never reinstalled/force-stopped/opened or restored during this task.
- At the end of initial forensics (before the separate 5.12 update), the app was universal DEBUG from 21:05:57, SHA d296d194...3212, same signer 5bb0fc45...ce5.
  Before/after StorageStats identical: App163,215,872; Data293,220,352 INCLUDING Cache1,900,544;
  non-cache data291,319,808. Data saving applied ZERO. Model183,816,181 + saved artwork104,266,525 dominate.
  Index642,355; databases/WAL1,804,640; lyrics316,336; prefs5,106. DE shader caches explain most cache residual.
  Model/index/video hashes match pre-task backup; no duplicate model/large abandoned temporary files found.
- Current delivery is SAF model import via .part/rename, NOT a network downloader. Already mixed int4/int8.
  128d vectors would save only532,480 bytes here; output INT8 only479,232; neither shrinks model weights.
- Research only: separate no-INTERNET helper executed Gemma, USE and MiniLM on S23. Four synthetic docs,
  four queries: expected top matches4/4,3/4,4/4. Warm medians621.5/16/2ms; sampled PSS293069/71215/95108KiB,
  NOT exact peak or representative search quality. MiniLM excludes PC-prepared tokenizer time; no thermal parity.
  USE6,120,274 bytes; MiniLM ARM64 INT8 23,026,053 + tokenizer466,247; ORT1.31.1 natives34,331,256 ARM64,
  actually larger than MediaPipe natives. No ORT app dependency added. All model migrations need quality/reindex work.
  ML Kit now has real EmbeddingGemma2 sentence embeddings, but developer preview/AICore/account/device
  prerequisites, quotas/versioning and no installed AICore on this S23 prevent assuming a reliable drop-in.
- Functional release tests: universal208 songs/30 results; ARM64 same index/results622ms, full restart609ms.
  `.bench` playback Addiction advanced1921->12340ms then paused; Media3 no error. Real user's playback untouched.
  Core VYBE34 + app1 unit tests pass; MediaPipe unit task NO-SOURCE; actual native tests used isolated helper.
  Normal universal debug rebuilt (real package, four ABIs) so standard debug path is NOT left pointing at `.bench`.
  Invalid ABI rejection verified; diff check passes. No full-app/audio-quality/peak-memory/Play-download claim.
- Cleanup done: `.bench` and `.storageprobe` uninstalled, own device tar/XML removed, phone Home;
  package inventory only `com.lochanpthakke.empee3`. Real app/data remains as before. Optimized real APK is
  task output `release-arm64-after.apk`, SHA398141e8...84f97, not yet installed at that stage; see 5.12 for the subsequent authorized update.

### 5.10 Now Playing menu / Audio Details presentation (2026-10-28, uncommitted)
- Only `NowPlayingMenu.kt`, `AudioDetailsScreen.kt`, `AudioDetailsParts.kt` changed for this task;
  pre-existing Home/Featured Setlists work is preserved. No playback, DSP, queue, radio, navigation
  destination, diagnostic ViewModel or data-source changes. Every field/action remains present.
- Cause: Audio Details was a translucent Box with no content-colour provider; uncoloured values,
  titles and icons inherited the default black LocalContentColor. Its translucent groups and
  three-way label/value/provenance rows compounded poor contrast and wrapping. The dropdown used
  default Material container/shape roles, rather than the player's custom theme surface.
- Details now establishes an opaque themed Surface with explicit onSurface content colour,
  opaque tonal section cards, a persistent close/copy/share header, wrapping label/value columns
  with attribution below values, and stacked rows on narrow widths or larger font scales.
  Unavailable is neutral; processing stages have text status badges and connectors that follow
  row height. Help explanations, EQ link, reset measurement and lifecycle tap ownership unchanged.
- Overflow remains an anchored Material dropdown with native scrolling/dismissal; rounded theme
  surface, hairline edge, muted headings/dividers, 20dp icons and >=48dp rows. Dynamic actions and
  the close-before-action callbacks are unchanged.
- Debug APK assembles; 293 tests pass (library 189 + playback 104). Focused KtLint on these three
  files passes. Module Detekt still fails on 87 findings, none in these files. `git diff --check`
  passes. Build/check log: task outputs `audio-menu-checks.log`; certificate remains unchanged.
- S23 installed/tested after the user's idle confirmation (2026-10-28). Verified all five
  sections, scrolling/persistent header, help expansion and Reset measurement. Verified menu
  bounds/grouping, Back dismissal (player stays open), Music information opens/closes without
  saving tags, Audio Details, EQ link, and share chooser (cancelled; nothing sent). Screenshots
  reviewed in Light and original Dominant Color; 150% font checked for menu and stacked detail
  rows. Original theme and font_scale=1.0 restored. Queue remained Addiction, 7/208, paused at
  1921 ms. No audio playback, EQ adjustments or queue-changing actions performed.
- Device review found a decoder-name orphan character and faint inactive dots: final value
  column is 65% (label 35%); inactive dots/connectors now use onSurfaceVariant. Rebuilt and
  reinstalled; final playback/chain screenshots confirm both refinements. Library tests 189
  and focused KtLint pass again; Detekt retains the same 87 unrelated findings. Latest build log:
  task outputs `audio-menu-final-device-build.log`. Screenshots `audio-final-*`, `audio-light-*`
  and `audio-large-*` are in task outputs. No AndroidRuntime errors for the final process.
- Not exhaustively tested: other devices/rotation, every font size, active audio peak values,
  clipboard Copy, conditional radio refresh and state-changing menu actions (callbacks unchanged).
  Nothing committed or pushed.

### 5.9 Home Featured Setlists (2026-10-28, uncommitted)
- Replaced the Featured Albums heading/row with Featured Setlists. Keeps the existing five
  ranked album picks (`FeaturedAlbums`), interleaves up to three album-seeded radio cards,
  and deduplicates stations by known artist. No stations for a one-song library or compilation
  albums; empty albums are omitted. Existing album cards/navigation and other Home shelves stay intact.
- `FeaturedSetlists.kt` is presentation-only; `LibraryScreen` passes the existing artist-art map
  and `LibraryViewModel.startRadio`. Taps call `RadioSeed.Album` for the displayed album, using
  the same RadioRepository/RadioEngine/playback path as the album menu. No new radio engine.
- `RadioArtwork.kt`: square editorial covers with RADIO, GraphicEq identity mark, circular
  artwork and bold station identity. Saved local artist portrait first, else local album art;
  supporting circles use distinct covers from the same known artist among the featured picks.
  Remote URLs are excluded from these radio covers; no new artwork download, palette decode,
  dependency, query, model load, background job or schema change. Theme container/on-container
  pairs provide deterministic background/foreground colours. Missing art uses the existing loader's fallback.
- Debug assemble passes; 189 library tests pass, including 8 new selection/artwork/seed tests.
  Focused KtLint on the four changed production files passes. Full library Detekt still fails
  on 87 findings outside changed files; initial new-file formatting/complexity findings were fixed.
  Final log: task outputs `featured-setlists-final.log`. Signing certificate unchanged.
- Installed on the S23 at the user's request at 20:39 on 2026-10-28 (debug, see section 6).
  Not visually verified: the earlier local emulator attempt failed because the acceleration
  driver is missing. Check actual cards, font scaling, scrolling, album navigation and station
  playback on the S23. Nothing committed or pushed.

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

- **Live covers (`29ef0c1`)**: other albums' covers (only SOS tested), Now Playing open/close while a
  cover plays, rapid skips across different albums, memory over a long session, battery use.
- **Album stack toggle (`9d3c3e8`)**: merge/split look, rapid taps, swiping while stacked (not yet
  looked at by the user on the phone).
- **Live cover download (`99b203a`)**: an album that has a cover but none saved yet (real Apple answers, whether
  playlists state byte ranges so a size shows), Cancel mid-download, Remove, Glass off, small screens.

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
- Bottom-navigation tab renamed to **Home** with a house icon at the user's request (`69fcd96`, 2026-10-22;
  enum is still `LibrarySection.TRACKS`; benchmark journeys use `openSection("Home")`). Installed as
  release 2026-10-22 21:01 (SHA-256 `7f11c2ee…0c3c8`), backup `dbbackup\pre-home-tab-20261022-210043`.
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
  for background apps (netpolicy UID 10485 blocked=BATTERY_SAVER|APP_BACKGROUND). Reproduced: run-as erhu
  can't resolve musicbrainz.org while the shell can. Wi-Fi also flapped ~19:50.
- Batch: RepairBatchRunner pauses on OFFLINE/BLOCKED/unreachable (track put back, not "no match"),
  NETWORK_FAILED + NO_MATCH statuses, preflight (network + DNS only), RetryPolicy (3 tries, backoff+jitter,
  Retry-After <= 30 s), queue persisted in proto field 28, one auto-resume when the network returns.
- Last.fm: old hard-coded key invalid (HTTP 403, error 10). Key now from local.properties `lastfm.apiKey`
  or env LASTFM_API_KEY -> BuildConfig; blank = NOT_CONFIGURED (no request). Auth failure disables it for the session.
- OkHttp request logs now redact api_key.

## 6. Last known state
- **2026-11-01, current:** CI-built erhu release 1.0.1 (2), non-debuggable, original signing key,
  installed in place on S23 `RZCXA1H43GW`. GitHub run 41302362949 succeeded. App left on Home,
  paused, after read-only navigation/performance checks and a favorite toggle that was restored.
  Release `run-as` is unavailable; do not assume the historical debug-backup commands work.
  The user explicitly requests the current code/notes pushed to both origin/kshitij and origin/master.
- **2026-10-31, historical:** implementation committed locally as **`b25a22c`** on `kshitij`, no push.
  User-requested Liquid Glass **ARM64 debug** APK installed in place on the real S23 with
  `adb install -r`; installed SHA-256 verified:
  `99d01e755881c5f1290c55e62ebb190c369e5a6349b41df90139086a876ca323`.
  Package remains `com.lochanpthakke.empee3`, same signing key, versionCode 1 / versionName 1.0.
  APK size **63,909,668 bytes**. Current debug installation supports direct `run-as` backups;
  do not install an old debug bridge unnecessarily.
- Fresh pre-install backup: **`D:\android-toolchain\dbbackup\pre-liquid-menus-20261031-010418`**,
  71 files; SQLite quick_check ok, schema 14, 208 songs and 3 artwork records. No uninstall,
  app-data clearing, artwork removal/compression or database changes during this update.
  App was left unopened; no phone menu, startup, playback or performance tests were performed.
- Current APK/evidence are outside Git in the chat workspace
  `C:\Users\kshit\Documents\Codex\2026-10-29\d-personal-projects-empee3\outputs\liquid-menus\`:
  `empee3-liquid-menus-debug-arm64.apk`, `report.md`, `verification.json` and `install-summary.json`.
  Signing certificate SHA-256 remains
  `5bb0fc457b96d9fb6cbc606009de44d633bee95b58fec2dc633a7a4eb54a9ce5`.
  Existing install scripts encode old expected hashes; inspect/adapt before reuse. Preserve all
  backups and user comparison videos; the Downloads Phase 4B comparison is not disposable cleanup.
  Earlier installed-release/debug statuses below are superseded by this entry.
- **2026-10-29:** user authorized committing and pushing all pending Featured Setlists, Now Playing
  menu/Audio Details, ARM64 packaging and storage documentation work together to `origin/kshitij` only.
  Validation and physical-device results remain recorded in 5.9-5.12. APKs, backups and keys stay outside Git.
- **2026-10-28 22:26:** optimized **ARM64 release** installed in place on the REAL S23, same key/package/version.
  SHA256 `398141e81d8735efc93dac881fe8dbdf2e2900d0c21460f5c66ff6c12d184f97` (reconfirmed October29).
  App storage now50,054,656 bytes; validated backup `dbbackup\pre-arm64-release-20261028`.
  Private-data integrity and real Home/VYBE/restart/playback checks passed; see5.12/full storage report.
  No extra app copy remains; left Addiction paused at14576ms, same queue7/208. No commit/push.
- **2026-10-28 21:05:** final menu/Audio Details **debug** build installed on S23, same key and
  package. Installed APK hash matches local/saved `D:\android-toolchain\build-audio-menu-20261028\real-debug.apk`:
  `d296d1944da35b2e94222bd13de0d017cc0c9b08b7ab4fb1d15051891ae43212`.
  Validated pre-final-install data backup:
  `D:\android-toolchain\dbbackup\pre-audio-menu-final-20261028-210541\appdata.tar`.
  Earlier backup before initial UI build: `dbbackup\pre-audio-menu-20261028-205742\appdata.tar`.
  No uninstall/data clearing; only main package used. Tested after idle approval; see 5.10.
  Left paused in Now Playing, Addiction 7/208 at 1921 ms; original theme/font scale restored.
- **2026-10-28 20:39:** installed the tested Featured Setlists **debug** APK in place on the S23,
  at the user's request. Installed APK SHA-256 matches the local build:
  `39db848bcd2e589017bc9ce9fca06296f9eb034b3c69ecd367a4b851b3e44eb4`.
  All signing certificates matched. The prior installed release exactly matched the saved
  `build-library-builder-theme/real-release.apk`; switched briefly to its same-code debug APK
  while playback was paused to back up data, then installed latest with `adb install -r`.
  Validated backup (291,422,720 bytes) plus original APK:
  `D:\android-toolchain\dbbackup\pre-featured-setlists-20261028-203751\`.
  Only the real package is installed; no uninstall or data clear. App not opened for UI checks.
- **2026-10-24 22:34:** the main app is the **release** build of `0e81e71` (Library Builder follows the app theme +
  detail-page nav motion; SHA-256 `f30614ab…`), smoke-tested on `.bench` first (no FATAL; `.bench` removed). Backup:
  `dbbackup\pre-library-builder-theme-20261024-223325`. APKs: `D:\android-toolchain\build-library-builder-theme\`.
  At the user's explicit request, `kshitij` was pushed to BOTH `origin/kshitij` and `origin/master` (fast-forward).
- **2026-10-23 23:29:** the main app is the **release** build of the merge (`7ca30e7` + these notes; SHA-256
  `f02114f9…`), smoke-tested on `.bench` first (no FATAL; `.bench` removed). Backup:
  `dbbackup\pre-merge-527adbb-20261023-232838` (taken via the same-key debug `build-glass-rollback\real-debug.apk`).
  APKs: `D:\android-toolchain\build-merge-527adbb\`. Only `com.lochanpthakke.empee3` is installed.
  At the user's explicit request (2026-10-23), `kshitij` was pushed to BOTH `origin/kshitij` and `origin/master`
  (fast-forward). The "never push master" rule still stands otherwise: ask each time.
  Still to verify on the phone: the drawer feel (one-picture frost), the tint strength on Dominant/Gradient,
  re-picking MUSICtest before the first real Library Builder run.
- The PC ran critically low on memory during builds (1.8 GB free of 13.9): use
  `-Pkotlin.compiler.execution.strategy=in-process`, build release and benchmark separately, `./gradlew --stop` first.
- **2026-10-22 23:04:** the main app is the **release** build of the live cover download work (SHA-256
  `47c6ce8e…`; same code as `99b203a`). Backup: `dbbackup\pre-live-download-install-20261022-230347`.
  APKs: `D:\android-toolchain\build-live-download\` (`real-release.apk`, `real-debug.apk` for backups).
  Only `com.lochanpthakke.empee3` is installed. `kshitij` is pushed to `origin/kshitij` through the notes commit.
  Lesson: a gradle run cut off by `timeout` left a STALE release APK that was then copied; always compare
  the new APK's hash with the installed one before installing.
- **2026-10-21 22:52:** the main app is the **release** build of `29ef0c1` (SHA-256 `bbb76a9b…a73a9fd`),
  launched once without crashes. Backup: `dbbackup\pre-live-artwork-release-20261021-225221\appdata.tar`
  (includes both SOS cover videos). APKs: `D:\android-toolchain\build-live-artwork-final\`. The library
  database is now version 14 (Lochan's `animated_artwork` table); it is wiped on a DOWNGRADE, so restore a
  backup if an older build is ever installed. `kshitij` is ahead of `origin/kshitij` from `891b754` on
  (not pushed); `origin/master` was not pushed (the push was blocked by the permission classifier).
  Final unit-test re-run after removing diagnostics was killed for low PC memory; the run just before
  passed (network 268, library 150); both APKs built from the final code.
- **2026-10-21 15:27:** `0189dc5` (expanded player drawn without Liquid Glass; everything else keeps
  glass) installed as **release** on the main app after the user confirmed it runs smooth on `.bench`.
  `.bench` code was verified identical to `build-0189dc5\real-release.apk` (all DEX/assets; only manifest
  and resources.arsc differ by package). Installed SHA-256 `f9f3beed…3166b` matches. `.bench` uninstalled;
  only `com.lochanpthakke.empee3` remains. Backup: `dbbackup\pre-0189dc5-install-20261021-152727\appdata.tar`.
  `kshitij` is 2 commits ahead of `origin/kshitij` (not pushed).
- **2026-10-20:** the current transition build contains the complete-card changes
  described in 3.11; the user requested committing these changes to `kshitij` (no push requested).
  Latest paired APKs: `D:\android-toolchain\build-card-transition\real-release.apk`
  and `real-debug.apk` (same code, debug is for future data backups). Only the real package is
  used; no `.perf`/`.bench` copy was installed for this task. Do not reinstall `7ff9564` thinking
  it contains the new complete-card transition. Final release is installed and visually checked;
  only `com.lochanpthakke.empee3` is installed. Latest pre-install data backup:
  `D:\android-toolchain\dbbackup\pre-card-release-20261020-010726\appdata.tar`.
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


## 2026-11-07 — Lyrics timing, album hero and collection transition switches (GitHub build ready)

- Completed in the requested order: lyrics timing editor, album artwork fade, then collection transition controls. Local changes remain uncommitted. GitHub Actions built the signed APK on the temporary branch below; it has not been installed.
- Lyrics timing uses the existing PlaybackState DataStore, proto field 39, per-song/per-source-text identity. Negative offsets show lyrics earlier, positive later. Save/Cancel/Reset, numeric/slider/50 ms steps, preview, line/word seeking and prior-source offsets are covered. Source lyrics/media files are never rewritten.
- AlbumHero keeps the existing image dimensions, layout, controls, scrolling and live-cover playback. Its fade follows the measured metadata plus a 48 dp lead instead of darkening from 35% of image height.
- Album/playlist Transition Studio rows have an independent collection switch. Proto field 40 stores disabled collections; switching off gates exact-pair overrides too, without deleting them. Re-enabling an unconfigured collection starts the existing Fade preset. Global master stays independent and authoritative.
- Enabled collections show between-track shortcuts into the existing pair editor. Playlist occurrence slots remain authoritative, including duplicate songs; missing/stale pairs cannot open a different pair. Studio controls and DSP choreography were preserved.
- Validation: 95 targeted tests passed across lyrics, disk restart/merge, header collapse, transition resolver/planner/coordinator/shapes/editing, and native Compose interaction/render checks. Scoped main-source ktlint/detekt and library compilation passed; existing album/playlist page-wide static-analysis debt was excluded. Evidence is in work/lyrics-timing-20261107, work/album-hero-20261107 and work/collection-transitions-20261107.
- S23 remains on the previously verified erhu 1.0.9 (10), CI 42364495952 / source c1cbea2c7678bb1543d185120ea96f0ae2be9077. The new build is ready for requested installation and on-device checks. Next compatible release must use versionCode greater than 11. Keep GitHub-only APK builds and no local commits.

- GitHub build complete: erhu 1.0.10 (11), source `660e88d895f115baf073400ac8deaeb5945f5b76`, branch `build/lyrics-transitions-20261107-183902`, run https://github.com/lochanpthakke/erhu/actions/runs/42375630351. Full CI tests, release lint, optimized compilation and original-certificate signing verification passed. APK/checksum/verification/provenance saved in workspace `outputs/erhu-lyrics-transitions-ci-42375630351`. Master/public releases and local Git history are unchanged.

- Installation update (2026-11-07): verified GitHub APK erhu 1.0.10 (11), CI 42375630351, installed successfully on S23 RZCXA1H43GW using `adb install -r`; data preserved. This supersedes the earlier 1.0.9 installed-version note. No launch or playback interaction was performed.


## 2026-11-07 — Advanced VYBE queries and precise transition points (local, pending build)

- Added multiple independently resolved song/album/artist references, balanced normalized reference ranking and seed exclusion. Missing/ambiguous/filtered references remain visible; recommendations wait for all selected reference vectors. Choices retain other selections and BPM/year/exclusion constraints; stale choices are rejected.
- VYBE now supports explicit BPM ranges/around/under/over, release-year/decade constraints and known artist/genre exclusions. Unknown numeric tags never satisfy active filters. Quoted and recognized catalog names (including Without Me / No Role Modelz) are protected from filter parsing.
- Added cached catalog matching with artist-qualified titles, partial names, conservative spelling correction, ambiguity choices, search-only Ye/Kanye aliases and existing approved database/manual aliases. No tag writes or artist-identity merges. Name/alias changes refresh searches without re-embedding unchanged descriptions. The existing EmbeddingGemma setup/model is unchanged.
- Transition Studio: Volume → Customize exposes Edit exact time & gain. The existing floating surface lets users select outgoing/incoming points, enter actual-overlap milliseconds and signed dB, or set exact silence. Apply records one existing undoable edit; Cancel changes nothing. Fixed endpoints/point ordering and the other curve/settings are preserved. No canvas gesture, playback engine or persistence redesign.
- Source reference: MeezaUni/Erhu-iOS default branch at e56f29ef801e2a1ea7443c60dd3a56ec026d9577. Validation evidence: work/vybe-advanced-20261107 and work/transition-precision-20261107. 61 unique targeted tests passed, including native pointer interactions and Room alias reads; scoped code checks/library compilation passed.
- These new changes have NOT been built into an APK or installed. S23 remains on GitHub-built erhu 1.0.10 (11), CI 42375630351. Next requested GitHub build must base on source 660e88d895f115baf073400ac8deaeb5945f5b76 and use versionCode >11. Preserve the no-local-commit/GitHub-only-APK policy; no commit or push was performed for this implementation.

## 2026-11-07 — Compact radio header and selective Lochan integration (local, pending build)

- Radio header task completed first: portraits/title are closer, spacing before count/Play is reduced, lower artwork edges fade into the page, and the track list retains the base theme instead of inheriting the entire artwork colour. Portrait selection and radio generation/playback are unchanged. Palette checks and native renders passed (work/radio-header-spacing-20261107).
- Then selectively integrated source fixes from lochan at 92fe6aa2f0f019d24821e6a9e9d0d0ceea16df06. This is a local file integration, not a Git-history merge/commit. Kept the pending VYBE, precision editor, lyrics and artwork improvements.
- Included local artist/playlist picking, bounded ImageDecoder plus stream crop fallback (including the old bounds-decode null-return bug), album/artist Artwork Fetcher redirects, utility-to-Library navigation cleanup, cover-write permission handling/resolved file paths/artwork-only writes, embedded-cover priority, genre propagation, iTunes genre data and guarded metadata fallbacks. Queue status updates use current album identity across permission callbacks.
- No new source/provider was added. Existing artist fetch methods and repository URL set were verified against build source 660e88d. Excluded the branch's Genius/Discogs/DuckDuckGo/Apple-page scraping and expanded artwork-source pipeline, .idea files, recovery-code file, unused BPM diagnostic fields, force-overwrite change and forced uncertain-tempo guesses. jwt.txt was untouched.
- Metadata fallback borrowing is restricted to confident same-track/same-edition candidates; conflicting years/BPM and missing/invalid facts remain guarded. Existing MusicBrainz/Deezer/iTunes/AcousticBrainz/on-device paths were retained.
- Evidence: work/lochan-selective-20261107/completion.json. 23 network tests and 26 feature tests passed across the targeted runs, plus 4 radio-header checks. Scoped code checks and library compilation passed. Large unrelated screen/common static-analysis debt was excluded. No local APK build or on-phone validation of these changes.
- Phone stays on erhu 1.0.10 (11), CI 42375630351. Next requested GitHub build should use base source 660e88d895f115baf073400ac8deaeb5945f5b76, include the pending VYBE/precision/radio changes plus this selective integration, and bump versionCode above 11. Do not create local commits, push master or publish a release without the user's request.


## 2026-11-07 — Requested GitHub beta build/publication in progress

- User requested current APK build/install, then explicitly authorized uploading the APK to Releases. Version bumped to erhu 1.0.11 (12). GitHub-only source upload on temporary branch `build/vybe-metadata-radio-20261107-220442`, source `a4d733436d140564461ce9591134edaec87d0c44`, based on previous installed source 660e88d. Includes advanced VYBE, precision points, radio spacing and guarded Lochan source fixes. No added metadata/artwork providers.
- Run https://github.com/lochanpthakke/erhu/actions/runs/42391998583 executes full tests, release lint, optimized signed build and verification, then beta publication if successful. Release/install are not yet confirmed. Build state and exact 45-file allowlist: work/vybe-metadata-release-20261107/github-build.json.
- Local HEAD remains e38a2e359bd50421dc23c0482aee6111c411b865; no local commits or master push. Phone disconnected at build start; installation pending connection.

- Build/publication confirmed: GitHub run 42391998583 succeeded, including full unit tests/release lint, optimized APK, original-certificate verification and publish job. Published beta: https://github.com/lochanpthakke/erhu/releases/tag/erhu-v1.0.11-beta.20 . APK erhu 1.0.11 (12), 39,021,449 bytes, SHA-256 f5e173686b3c8aa557844747cdd43a8dd6d2622b850fb46ea57726aa813d6d07. Downloaded artifact checksum matches published asset digest; local apksigner/aapt verification passed. Evidence/rollback APK in workspace outputs/erhu-vybe-metadata-ci-42391998583. Phone installation remains pending connection.

- Installation confirmed: erhu 1.0.11 (12), source a4d733436d140564461ce9591134edaec87d0c44 / CI 42391998583, installed successfully on S23 RZCXA1H43GW with adb install -r. Package version verified by dumpsys; app data retained. Supersedes the pending-install note. Installed evidence: workspace outputs/erhu-vybe-metadata-ci-42391998583/installed.json. No launch/playback interaction performed.

## 2026-11-08 — Authorized update announcement broadcast

- User explicitly requested an all-user announcement. Published ID 75104789-06f4-464e-91cd-ad4aa1e17d33, title App update available, exact requested body: "There is a new version of app available, please update through the github checker. If already done please ignore". Audience all, no version restrictions, expires seven days after publication.
- Existing free Spark Hosting/FCM publisher deployed successfully; FCM accepted the topic message and the public feed was independently checked for ID/body. This confirms publication/FCM acceptance, not delivery to every device. Receipt and idempotent input under private untracked work/firebase-setup/update-announcement-20261108*.json. Do not resend with a new ID unless requested. No billing, Functions deployment, app rebuild, local commit or push.

## 2026-11-08 — In-app announcement startup refresh

- User clarified announcement popups must appear on Home without POST_NOTIFICATIONS. Existing AnnouncementHost wraps MainScaffold, and foreground popup selection precedes the OS notification permission checks; permission applies only to notification-shade posting. Do not gate in-app display or feed retrieval on notification permission.
- Changed AnnouncementController.resumed to force the feed refresh on each process foreground session, bypassing the 15-minute background cache window so missed pushes are discovered on open. Existing read/dismissed suppression and refresh mutex are unchanged. No duplicate broadcast or reset of production announcement state.
- On S23 1.0.11, the actual update announcement is present and already marked Read. Returned phone to Home. This source change is not in the installed APK; next requested GitHub build must base on a4d733436d140564461ce9591134edaec87d0c44 and bump versionCode beyond 12. No local APK assembly, commit or push.
- Validation: app compileDebugKotlin and all four AnnouncementTest cases passed. No APK was assembled locally. Checks log: work/announcement-home-20261108/checks.log. Permission independence verified in code; an end-to-end unseen-popup test with permission denied was not performed on production data.
