# Album artwork retrieval

Restored on 2026-11-04 from the retained accuracy records and validation logs. The implementation and its saved measurements were not changed during restoration.

## Implementation

Artwork Fetcher uses one music-catalogue pipeline through `MetadataRepository`, in this order:

1. MusicBrainz identifies releases from embedded release/recording IDs where available, otherwise album, artist and release year. It uses the existing shared request gate and an identifying User-Agent.
2. Cover Art Archive retrieves approved front covers for identified releases, excluding back covers. Broken or unsuitable exact-release images trigger release-group fallback. Group covers require edition confirmation and do not inherit an unrelated exact release ID.
3. Deezer searches albums rather than artist portraits.
4. Apple's public iTunes Search API searches album titles and ranks album/artist matches. Artwork URL resolution is upgraded, then the actual decoded dimensions are checked.
5. TheAudioDB supplies album thumbnails; community artwork requires manual confirmation.

Genius, DuckDuckGo, Discogs, Wikipedia/Wikimedia and the exclusive Wikidata artwork resolver were removed, including unused provider/API/DTO code and source options. Last.fm, Fanart.tv and Google image search had been removed by the preceding API-key cleanup. Shared metadata matching with iTunes, MusicBrainz and Deezer remains. Artist-photo lookup stays separate and uses Deezer's artist catalogue.

Manual tag editing, embedded metadata reading/writing, custom cover selection and existing application callbacks remain. Searching never writes tags or replaces an existing custom cover. Applying the enlarged preview remains an explicit user action. Playback, scanning, database schemas and Transition Studio were not changed.

## Matching, UI and resource limits

Ranking normalises punctuation, accents and featuring credits, then considers album/artist similarity, release IDs, year, edition markers and album/single/EP distinctions. Wrong title/artist matches are rejected. Uncertain artists, alternate editions, remasters, promotional sleeves, box sets and differing release years require review. Resolution determines recommendation eligibility and breaks ranking ties. SHA-256 and a small perceptual fingerprint merge equivalent images while retaining distinct editions.

The existing selection grid shows source, matched album/artist, year/edition, actual resolution and recommendation/review status. Selection is highlighted, and the enlarged preview displays the complete image before confirmation. Low-confidence candidates remain available for manual choice.

Searches run off the main thread with serialized requests, duplicate-result caching, request cancellation, individual provider timeouts and a 45-second overall budget. Provider failures are isolated. MusicBrainz, Apple and TheAudioDB are paced; HTTP 429/503 applies a bounded cooldown. JSON/image responses are bounded to 2/3 MiB. Image geometry is checked before decoding a small fingerprint. Early exact-cover validation is followed by at most ten final image checks.

Validated images and candidates persist in private app storage: seven-day query freshness, at most 256 query records and a 64 MiB image cache. Identical searches reuse results after recreation/offline. Expired successful results may be returned when a refresh fails. Screen/tab changes cancel outstanding searches and discard stale UI results. The cache is separate from embedded/custom covers.

## Credentials and provider limitations

MusicBrainz/CAA, Deezer and public Apple search do not require a user-supplied key. Debug/testing builds may use TheAudioDB's public test key `123`. Release builds skip TheAudioDB unless the application owner configures `THEAUDIODB_API_KEY` or `theaudiodb.apiKey` in untracked local properties. There is no user-facing key entry. A production licence/key may be required; the public test key is not unrestricted production access. Credentials are not committed and key-bearing request paths are redacted from logs.

Official references: [MusicBrainz rate limits](https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting), [Cover Art Archive API](https://musicbrainz.org/doc/Cover_Art_Archive/API), [Apple Search API](https://performance-partners.apple.com/search-api), [TheAudioDB API](https://www.theaudiodb.com/free_music_api), [TheAudioDB test-key guidance](https://www.theaudiodb.com/forum_topic.php?t=2).

## Accuracy evaluation

The [18-case fixture](../core/network/src/test/resources/artwork-accuracy/dataset.json) covers mainstream/local-library albums, less popular artists, hip-hop/mixtapes, electronic music, an older release, a single, an EP, deluxe/remastered editions, similar album names and incomplete/incorrect artist tags.

S23 MediaStore metadata was read without changing music files. The first four album examples occur in that library; their observed track tags are recorded separately. Queries are curated/normalised album-level inputs, not an unchanged export of every file tag. For example, a local Her Loss track reports 2023 while its catalogue query uses the album's 2022 release year. Track title is not an album-artwork search parameter.

Covers were visually compared against separately inspected official Apple catalogue images; the single reference is an identified MusicBrainz/CAA release. The [complete results](artwork-accuracy-results.json) retain input metadata, expected references, reviewed candidates, dimensions, sources, all provider outcomes, edition assessments and elapsed times.

Initial visual inspection caught an Immersion box-set cover for the original Pink Floyd album and a promotional Get Lucky sleeve. These led to explicit box-set/promotion/year checks. Release-group covers require manual edition confirmation. Successful API responses alone were not counted as correct artwork.

### Measured results, 2026-11-04

The final run fetched catalogue metadata live and reused previously validated image bytes where available. The average below is therefore not a fully cold image-download benchmark.

| Metric | Result |
| --- | --- |
| Expected cover design available after visual inspection | 16/18 (88.9%) |
| Correct album identity in reviewed candidate | 17/18 (94.4%) |
| Correct artist identity in reviewed candidate | 17/18 (94.4%) |
| Exact edition independently verified | 9/18 (50.0%) |
| No usable expected cover | 2/18 (11.1%) |
| No decoded candidate images at all | 1/18 (5.6%) |
| Mean finder response time | 16.05 seconds |
| Automatic recommendations | 7/18; all 7 visually correct in this sample |

**88.9% is manually reviewed cover availability, not automatic-selection success.** All 18 inputs, including deliberately bad tags and abstentions, remain in the denominators. The reviewed candidate is the recommendation where present, otherwise the best visually inspected available candidate. Get Lucky's reference-matching sleeve was selected manually instead of its stickered alternative. Nirvana's available standard cover identifies the right album/artist but is the wrong requested deluxe artwork. Cases without adequate exact-edition evidence were not counted as edition successes. This small sample does not establish general-population accuracy.

| Case | Input album / artist | Reviewed source | Cover design | Edition | Resolution | Time |
| --- | --- | --- | --- | --- | --- | --- |
| 0 | SOS / SZA | iTunes / Apple Search | Yes | Verified | 1000x1000 | 13.77s |
| 1 | IGOR / Tyler, The Creator | TheAudioDB | Yes | Unverified | 700x700 | 12.50s |
| 2 | Flower Boy / Tyler, The Creator | Cover Art Archive release-group | Yes | Unverified | 1200x1200 | 16.68s |
| 3 | Her Loss / Drake & 21 Savage | iTunes / Apple Search | Yes | Verified | 1000x1000 | 28.51s |
| 4 | Discovery / Daft Punk | iTunes / Apple Search | Yes | Verified | 1000x1000 | 16.11s |
| 5 | Madvillainy / Madvillain | TheAudioDB | Yes | Unverified | 700x700 | 18.68s |
| 6 | Acid Rap / Chance the Rapper | TheAudioDB | Yes | Unverified | 700x700 | 13.80s |
| 7 | The Dark Side of the Moon / Pink Floyd | TheAudioDB | Yes | Unverified | 700x700 | 10.97s |
| 8 | EARS / Kaitlyn Aurelia Smith | iTunes / Apple Search | Yes | Verified | 1000x1000 | 20.02s |
| 9 | Come to Daddy - EP / Aphex Twin | Cover Art Archive release-group | Yes | Unverified | 1000x1000 | 18.75s |
| 10 | Get Lucky - Single / Daft Punk | Cover Art Archive release | Yes | Verified | 500x435 | 20.48s |
| 11 | Nevermind (Deluxe Edition) / Nirvana | Cover Art Archive release-group | No | Wrong | 1200x1200 | 8.67s |
| 12 | OK Computer OKNOTOK 1997 2017 / Radiohead | Cover Art Archive release | Yes | Verified | 500x500 | 19.81s |
| 13 | Greatest Hits / Queen | iTunes / Apple Search | Yes | Verified | 1000x1000 | 16.06s |
| 14 | Greatest Hits / Fleetwood Mac | iTunes / Apple Search | Yes | Verified | 1000x1000 | 17.77s |
| 15 | SOS / Unknown artist | iTunes / Apple Search | Yes | Verified | 1000x1000 | 19.64s |
| 16 | Flower Boy / Various Artists | Cover Art Archive release | Yes | Unverified | 600x600 | 10.96s |
| 17 | Discovery / Queen | None | No | No result | - | 5.70s |

### Known weaknesses

The requested Nirvana deluxe cover was not found. The contradictory Discovery/Queen query returned no candidate. Missing/incorrect artist tags required manual selection when candidates were available. Deezer returned no album candidates in this environment. MusicBrainz/CAA had intermittent failures/timeouts, and Apple omitted several known albums. TheAudioDB was evaluated with its test key; availability in an unconfigured release build is not assumed. Group/community artwork can match the visible design without establishing an exact edition.

To repeat the live evaluation, set `ERHU_ARTWORK_ACCURACY_DATASET` to the fixture's absolute path and `ERHU_ARTWORK_ACCURACY_OUTPUT` to a disposable output directory. Run `:core:network:testDebugUnitTest --tests '*ArtworkAccuracyRunTest'`. The opt-in harness invokes the production finder with a JVM image decoder and records every query's outcomes. Ordinary unit tests do not require live network access.

## Validation performed before report restoration

- 554 JVM tests passed: core/network 348 and feature/library 206. The opt-in live test was skipped in the ordinary run and executed separately for the 18-case evaluation.
- Isolated `assembleDebug` and `assembleDebugAndroidTest`, changed-file `ktlintCheck` and `git diff --check` passed.
- Regression coverage includes identity/edition scoring, promotional sleeves, wrong/missing artists, release type, low resolution, duplicate images, invalid responses, rate limits, cancellation, broken exact-cover fallback and persistent cache reuse. The recording-ID path preserves the release's own artist credit.
- Galaxy S23 UI checks passed for library lookup, candidate grid and metadata labels, recommended border, enlarged preview, cancellation, repeat-search cache results and the remaining metadata-source menu. No user-library Apply action was invoked.
- S23 instrumentation passed: **1 test, 13.939 seconds**. It retrieved live SOS candidates, proved searching preserved the generated file's existing custom cover, explicitly applied the selected image to a private-cache WAV, checked its visual fingerprint after existing JPEG conversion and verified manual title/artist tags survived. Recreating the cache/finder with a client rejecting all network requests returned the same cached candidates.
- Temporary benchmark packages were removed and the screen-awake setting restored. The installed production app remained 1.0.2, last updated 2026-11-03 23:54:39. User music was not changed.

These are retained validation results from the implementation session, not newly rerun build/device checks during this documentation-only restoration.

## Important files changed by the implementation

- `core/network/.../artwork/`: query/candidate models, ranking, provider pipeline, bounded persistent cache and Android image verification.
- `MetadataRepository.kt`, `MetadataRepositoryImpl.kt`, `NetworkModule.kt`, `MusicBrainzProvider.kt`, `EmbeddedTagReader.kt`: repository/DI wiring, shared request pacing, embedded identifiers and cached application downloads.
- `ArtworkManagerScreen.kt`, `LibraryViewModel.kt`, `MetadataSourceOption.kt`, `SettingsScreen.kt`: cancellable UI state, source cleanup and candidate metadata/selection.
- `core/network/build.gradle.kts`: optional application-owner TheAudioDB configuration. `app/build.gradle.kts`: existing OkHttp dependency exposed to instrumentation tests only.
- Exclusive obsolete provider/API/DTO files removed. Shared tag writing, playback and database functionality retained.
- `AlbumArtworkTest.kt`, `ArtworkAccuracyRunTest.kt`, `NetworkLoggingTest.kt`, `KeylessMetadataArtworkTest.kt`, the dataset and saved results provide regression/evaluation evidence.

The implementation and report restoration originally remained uncommitted, with unrelated working-tree changes preserved. The user subsequently requested a GitHub Actions build and installation; these changes are now being submitted on `kshitij` for a signed 1.0.3 (4) artifact. Public release publication is not requested. Build and installation status must be verified separately.
