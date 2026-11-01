# Synced lyrics providers

Lookup keeps embedded synced LRC first (unless the user explicitly selects another result), then LRCLIB's existing exact/search matching, then Musixmatch. Plain embedded text remains available while searching for synced lyrics. Musixmatch only supplies validated, unrestricted LRC; ordinary text is never accepted from it. The existing parser, highlighting and scrolling render both providers.

Provider timeouts, exceptions, quota limits and unavailable results cannot stop the next provider. Exact and search requests have a 15-second bound; Musixmatch HTTP calls have a 12-second bound. Title, artist, album and duration matching rejects live/remix/alternate edit/extended mismatches. Concurrent identical lookups share work. A good LRCLIB synced match skips Musixmatch.

Successful automatic matches use the existing JSON cache implementation in `cacheDir/online-lyrics`, separate from explicit downloads in `filesDir/online-lyrics`. They survive process restarts and work offline until Android evicts cache. Metadata/URI identity checks prevent reuse for another recording. Explicit downloads remain durable and their removal flow is unchanged. Failed cache writes do not discard usable lyrics. No schema or audio tag changes.

## Musixmatch provisioning

Set `musixmatch.apiKey` in ignored `local.properties`, or `MUSIXMATCH_API_KEY` in the build environment. Blank disables the provider without requests. The key must have official subtitle endpoint access and a licence permitting this application's display and caching/offline use; a generic metadata/free API key may not provide that access. Confirm applicable attribution/reporting requirements with Musixmatch before distributing a configured build. No credentials were supplied, so live authenticated availability and account entitlements have not been validated. The integration does not use consumer tokens, scrape pages or bypass restrictions.

The build-time credential follows the project's existing Last.fm provisioning approach. It is not committed or logged (the shared client redacts `apikey`), but a key packaged in an APK is extractable; production distribution should use provider-approved mobile credentials or a controlled backend. Do not publish private account secrets in an APK.

Official API references:
- [Musixmatch OpenAPI](https://github.com/musixmatch/musixmatch-sdk/blob/master/swagger/swagger.json): HTTPS API root, `track.search`, response metadata and subtitle model.
- [Official subtitle request](https://www.postman.com/musixmatch-dev/musixmatch-apis/request/j136ay9/track-subtitle-get): `track.subtitle.get`, `subtitle_format=lrc`, duration/deviation parameters.
- [Authentication](https://www.postman.com/musixmatch-dev/musixmatch-apis/collection/pqm8o6w/lyrics-api): provisioned API key required.

Home now presents Smart Playlists, Featured Radio Stations, Featured Albums, then Recently Played Albums. The existing selection adapter, artwork cards, radio seeds, album actions and history remain unchanged. Empty featured shelves are hidden. Existing recently played playlists and tracks remain below these shelves.

## Validation

327 network tests and 196 library tests passed (523 total, zero failures/skips). `:app:assembleDebug` passed. Targeted production-source/Kotlin build-script formatting and whitespace checks passed. Tests cover LRCLIB regressions, provider fallback after errors/timeouts/limits, malformed LRC, recording/version rejection, real JSON disk-cache reuse after repository recreation, embedded priority and parser timing. Authenticated Musixmatch and on-device UI checks remain unperformed. No commits or pushes.

Main changes: `LibraryHome.kt`; `MusixmatchApi.kt`, `MusixmatchProvider.kt`; shared `OnlineLyricsRepository.kt`, `LyricsMatcher.kt`, `SyncedLyricsValidation.kt` and `LyricsModule.kt`; embedded/online selection in `LibraryViewModel.kt` and `OnlineLyricsState.kt`; generic rate-limit messaging in `OnlineLyricsUi.kt`; API-key build provisioning and regression tests.
