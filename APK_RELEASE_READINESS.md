# Erhu Android 1.0.18 candidate

**NOT CLEARED FOR PROPRIETARY DISTRIBUTION. GITHUB CANDIDATE BUILD REQUESTED.**

The dedicated `proprietary-candidate.yml` builds and verifies a disposable
**debug-signed** optimized release variant on this working branch, with
`-PproprietaryCandidate=true`. It runs targeted metadata/policy tests and Android
release lint; generates the matching recipient ZIP; checks manifest, signature,
configuration, excluded classes/native/image payloads and bundled notices.
No production signing secrets, release publication or merge are configured.
The resulting run-specific BUILD_REPORT.md and verification.json are authoritative
for actual build results. Earlier deferred-build statements below remain historical
until superseded by that run. A successful build is not legal clearance.

## Implemented

- Version 1.0.18 / code 19; application ID and existing signing configuration preserved.
- `proprietaryCandidate` defaults to `true`. Both network and database modules use
  the new source-based `third-party/jaudiotagger` module. It retains 537 upstream
  Java files, removes the nine Oracle-noticed tree implementations and five
  dependent MP4 I/O implementations, and removes their reader/writer registrations.
  Full removal list and source hashes: `third-party/jaudiotagger/MODIFICATIONS.json`.
- Candidate MP4/M4A/M4P/M4B tag writes fail before backup/copy/write. Playback,
  MediaStore metadata, other-format tag operations, local artwork selection,
  library, EQ and audio processing paths are retained, not newly runtime-verified.
- Candidate excludes the MediaPipe dependency and substitutes an explicit unavailable
  embedder. Effective VYBE visibility is false without overwriting the stored setting.
  Original implementation, model files and previous evidence remain untouched.
- Shared OkHttp transport rejects Apple/iTunes, Deezer, Musixmatch, TheAudioDB,
  MusicBrainz/CAA and LRCLIB requests. No provider credentials enter candidate
  BuildConfig. MusicBrainz core CC0 data is not claimed proprietary: public-service
  access and supplementary content permissions are separate, still pending.
  Online lyric providers are disabled; embedded/user-saved lyrics and local caches
  remain. Previously saved user content is not deleted or retroactively cleared.
- 29 build-type resource overrides replace only identified uncertain app assets:
  13 commercial onboarding previews are transparent and not displayed; original
  path-only Stage 4 logo, wordmark and turntable proposals supply other overrides.
  Six clean resource images and existing code-drawn onboarding motifs remain.
  Originals and all earlier inventories/evidence are preserved unchanged.
- Existing LGPL/MPL notices, matching OkHttp public-suffix source and Roboto
  attribution retained. Added modified-library and AndroidX native notices.
- Source-visible LICENSE scoped to controlled original expression, with historical
  GPL, third-party, GitHub/platform and LGPL rebuild exceptions preserved. No
  ownership, assignment, revenue split or historical grant was changed.

## Recipient materials / next GitHub build

`tools/package_proprietary_candidate.py` prepares the accompanying
`build/proprietary-recipient/erhu-1.0.18-corresponding-source.zip` using an explicit
source allowlist. It excludes private work/evidence, signing files and overridden
original images, includes modified LGPL source, app source and required build
files. It is not yet executed or validated. Deliver this exact matching kit with
any future candidate APK, not an old object package or the original source JAR.

The recipient may modify `third-party/jaudiotagger/src/main/java`, then rebuild
from the kit with JDK 21, the repository Gradle wrapper, SDK platform matching
compileSdk 37 and the required Android build tools. Example disposable build:
`./gradlew :app:assembleDebug -PproprietaryCandidate=true -PperfApp`.
`perfApp` uses a separate application ID; Android debug signing creates a disposable
local key. Do not use production keys or uninstall the production app. Required
LGPL modification/replacement/debugging rights survive the proprietary licence.

For Erhu's own build, use GitHub Actions only after explicit upload authorization
and renewed credits. No workflow was triggered. Build the candidate explicitly
with `-PproprietaryCandidate=true`, run targeted metadata/provider tests and lint,
inspect resolved dependencies and APK DEX/native/resources, and upload the
matching source kit and notices alongside the APK. A false property value is
historical/full-feature development mode, **not a cleared proprietary release**.
Verify application ID, code 19/version 1.0.18 and intended signing certificate.
A disposable key cannot perform an in-place update of the production app.

## Evidence and validation limits

No new compilation, APK assembly, signing, installation, unit tests, lint or
runtime checks were run, as requested. Reused Stage 1's 472 tests and Stage 4's
20 targeted metadata tests/nine host cases are historical evidence only, not
validation of this new source fork or candidate profile. Static source inspection
found no references to removed tree/MP4 I/O classes in retained Java sources.
Original asset bytes were checked against the existing 63-row hash inventory.
No source-kit reproducibility, dependency resolution or APK packaging success is
claimed. No commit, push, merge, publication, supplier contact or iOS access.

## Remaining blockers / minimum next actions

1. **Technical:** GitHub compilation and candidate tests are pending. Verify the
   filtered module compiles, controls fail safely, MediaPipe/Oracle classes and
   original overridden image payloads are absent from the actual APK.
2. **LGPL:** generate and inspect the exact recipient source kit; demonstrate a
   modified-library rebuild and Android metadata/storage/runtime checks for this
   fork. Previous-library results do not prove this candidate works or complies.
3. **Native/legal:** retained AndroidX graphics/datastore exact constituent/runtime
   notices still need completion or review using existing evidence. Their
   Apache-2.0 grants are not replaced by Erhu's licence. Do not infer an exact
   linked BOM from the retained broad MediaPipe notice.
4. **Rights/legal review:** confirm the prospective licence and controlled original
   contributions cover the intended combined release; validate the replacement
   assets' provenance. All earlier supplier requests remain prepared and unsent.
   Unresolved optional services stay disabled. Preserved original artwork in the
   repository is not cleared for a new source/media redistribution merely because
   candidate APK overlays exclude it. Review the exact public upload separately.

Historical GPL through 1.0.17 remains effective. No production clearance or
revocation of existing grants is claimed. Android's agreed 50/50 arrangement is
unchanged; iOS is outside this work.
