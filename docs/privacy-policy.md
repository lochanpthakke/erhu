# erhu Privacy Policy

Last updated: 9 November 2026

## Introduction and scope

This policy describes how the Android erhu application handles information, based on the implementation in [lochanpthakke/erhu](https://github.com/lochanpthakke/erhu). It covers local playback, library tools, online lookups, updates and announcements. It does not describe the separate iOS application or independently modified builds.

You do not need an erhu account to play local music. The inspected application does not include advertising, an analytics SDK, Firebase Analytics or Firebase Crashlytics. Online services still receive information needed to answer requests, as described below. Network features may run automatically, including artist artwork lookup, announcement refresh and scheduled update checks; not every request requires a separate tap.

## Information processed by erhu

With music access, erhu reads Android MediaStore entries and embedded file metadata to build your library. This includes track titles, artists, albums, album artists, genres, track numbers, dates, durations, file locations and identifiers, artwork, lyrics and available audio-format information. It may read audio locally for playback, waveforms and tempo analysis. Manual editing, Fix Tags and Library Builder can update tags, covers, names or file locations after the applicable review and Android authorization.

The application maintains favourites, playlists, listening history, queue and playback position, settings, transition configurations and lyrics timing adjustments. VYBE uses local metadata, search text and listening information to rank your own tracks.

**The inspected application does not upload your music files, audio samples, microphone recordings, full library database, listening history or VYBE queries to metadata, lyrics or AI services.** It has no microphone recording permission. Online lookups transmit selected text metadata and identifiers, not the audio. The separate Wi-Fi transfer feature receives files from a device you pair on your local network; this is not a cloud upload service.

## Information stored locally

- Room databases hold library records and related user state. DataStore and private files hold preferences, playback state, saved configuration and announcement state.
- Private files and caches hold downloaded artwork, custom artist images, static or animated artwork, waveforms, lyrics responses and explicitly downloaded lyrics. Selected edits can also be written into your music files.
- VYBE uses a locally selected model and keeps metadata-derived embeddings and index information on the device.
- Background metadata repair saves pending plans, artwork and checkpoints in private, backup-excluded storage so interrupted work can be reviewed or resumed.
- Downloaded update APKs are held in the app's cache. Local crash reports and diagnostic information are described below.

App-private data is separate from your shared music files. The source enables Android backup and does not broadly exclude its databases and preferences in the backup rules. Depending on Android, your device settings and backup provider, eligible app data may therefore be backed up to a cloud account or transferred to another device. Cache and explicitly backup-excluded storage are distinct from those eligible files. This policy does not promise that all local data stays exclusively on one physical device.

## Information shared with external services

Online requests expose your network IP address and ordinary connection/request information to the receiving service and its infrastructure. Requests may include an erhu or browser-style User-Agent. A provider or image CDN can infer which item you requested from its search terms, identifier or image URL. erhu does not control those providers' logging or retention.

| Feature / recipient | Information sent and purpose |
| --- | --- |
| Fix Tags: MusicBrainz, Apple iTunes Search, Deezer | Search terms derived from titles, artist names and, where used, album names or cleaned filenames. Album, recording and artist identifiers can be used for follow-up details and genre requests. Duration may be used locally to compare candidates; it is not transmitted by every provider. |
| Album artwork: MusicBrainz, Cover Art Archive / Internet Archive, Deezer, Apple iTunes and their image hosts | Album/artist queries or release identifiers, followed by requests to candidate artwork URLs. Existing artwork comparison is performed locally; the original image is not uploaded for reverse-image matching. |
| Optional TheAudioDB artwork lookup | Artist and album names with a build-configured API key. Release lookup is skipped when no key is configured; the public development key is a debug-only fallback. |
| Artist portraits: Deezer and returned image hosts | Artist names and artist identifiers. Automatic artist-artwork refresh can make these requests in the background. Custom local image selection does not upload that image. |
| Optional animated artwork: Apple Music web/catalog services and media hosts | Album/artist search terms, catalog IDs, storefront selection and artwork/playlist/segment URLs. The implementation can request public Apple web pages/scripts to obtain a catalog authorization token; it does not ask for your Apple account login. Artwork media is downloaded to the device. |
| Tempo enrichment: AcousticBrainz | MusicBrainz recording identifiers and a request for stored tempo features. Local tempo analysis does not transmit audio. Availability of this external dataset is not guaranteed. |
| Synced lyrics: LRCLIB | Track title, artist, album and duration where available, or text you enter for a manual lyrics search. The application reads embedded/saved lyrics and uses local response caching to avoid unnecessary requests. |
| Optional synced-lyrics fallback: Musixmatch | Title/artist search terms, a matched provider track ID and duration for timestamped subtitles, together with a configured API key. This provider is inactive without a provisioned licensed key. |
| Search images in browser: Google | An explicit action opens Google Images with an artist/photo or album/artist/cover query already filled in. Your browser and Google then govern the browsing session, including cookies and any signed-in account. erhu does not automatically import the results. |
| Updates: GitHub API, release download hosts and their infrastructure | Requests for the configured official repository's published releases, version-verification assets and the APK you choose to download. Installed version information is read and compared locally; the checker does not submit your library or need a personal access token for public releases. |
| Announcements: configured feed host and Google Firebase Cloud Messaging (FCM) | Requests for the public feed or a specific notice, plus Firebase's installation/registration information and topic subscription needed for push delivery. See the next section. |

The currently active metadata provider list does not contain Last.fm. A legacy DNS fallback entry is not an active Last.fm integration. TheAudioDB and Musixmatch are conditional build integrations, not promises that every distributed APK has those services enabled.

## Notifications and updates

When Firebase project values are configured, erhu initializes Firebase Messaging at application startup and subscribes to the `erhu-announcements` topic. The SDK manages installation IDs and registration tokens. The Android client does not submit that token to a custom erhu device-registration endpoint or attach it to feed requests. Push messages identify a notice or invalidation event; the application retrieves the notice from its configured endpoint.

The supplied free-hosting publisher publishes announcement content and sends broadcasts through FCM. It also supports an explicitly supplied test-device token for targeted testing; that token is not written into the public feed or the publisher's saved notice state. An alternative Functions/Firestore/admin implementation exists in the repository. These alternatives do not establish what is deployed on a particular server.

Opening the app refreshes announcements, and a background refresh is scheduled about every 12 hours subject to Android scheduling and connectivity. Notice contents, read/dismissed/notified IDs and the last refresh time are saved locally. Eligibility, including app-version targeting, is evaluated locally. Reading or dismissing a notice is not reported to the feed by the inspected client.

**Android notification permission controls notification-shade alerts, not in-app announcements or Firebase registration.** Denying permission does not disable feed refresh, registration or Home-page notices. The current app does not expose a complete FCM opt-out or installation-ID deletion control. No server-side deletion is implied by dismissing a notice, disabling notifications or clearing the app's local state.

Manual update checks run independently of the existing monthly automatic schedule. Downloading an APK is an explicit action. erhu checks the downloaded package, version and signing identity before offering Android's package installer through a content URI. Android may require permission to install from erhu; installation requires user interaction and can be cancelled. Updates are not silently installed.

## AI and on-device processing

VYBE's MediaPipe text embedder loads a model supplied locally by the user. Metadata descriptions, queries, embeddings and ranking are processed on-device; there is no remote inference endpoint in the inspected VYBE implementation. Obtaining the model from GitHub or another source is a separate download subject to that host's practices and the model's terms. Disabling VYBE hides access to the feature; it is not a general switch for other network features.

## Permissions and their purposes

| Permission / access | Purpose |
| --- | --- |
| Music/audio access (`READ_MEDIA_AUDIO`, or older storage read access) | Scan and play local music and read embedded metadata. |
| Legacy storage write, all-files access where granted, document/folder grants and per-file MediaStore consent | Supported tag edits, cover changes, file renaming and Library Builder operations. The granted scope depends on Android and the operation. |
| Internet and network-state access | Online lookups, images, lyrics, announcements, updates and connectivity-aware work. |
| Notifications | Media, repair/transfer progress and optional announcement alerts. Android applies its own foreground-service and media-notification rules. |
| Foreground services and wake locks | Keep playback, user-started repairs and transfers working as permitted by Android. |
| Install unknown apps (`REQUEST_INSTALL_PACKAGES`) | Hand a verified update to Android's installer with user approval. |
| Local-network access on supported Android versions | User-started Wi-Fi music transfer. |

The inspected manifests do not request contacts, camera, microphone or location access. Granting file access does not authorize erhu to upload your library.

## Wi-Fi transfer

When you start Wi-Fi transfer, erhu runs a local HTTP upload server bound to the Wi-Fi interface. A browser on your network uses a pairing code and session token to send selected music files to the phone. Filenames, file contents and transfer status pass between those devices; received files are published locally through MediaStore. The session has inactivity limits and stops when closed. This local HTTP transport is not encrypted HTTPS: use a trusted network. There is no erhu cloud relay in this implementation.

## Diagnostics, logging and error reporting

Crash reports are written to private local files with timestamps, device manufacturer/model, Android API level and exception stack traces. The crash logger retains at most 20 reports and offers clearing from Diagnostics & About. Its app-version label is currently hardcoded and may not identify the installed release correctly.

The shared HTTP logger records basic request URLs and response information in Android logs. It redacts recognized secret parameters, but search metadata or identifiers in URLs can still appear. Exceptions and other diagnostics can contain file paths, track information or device/audio-route details. Copying a report places it on the Android clipboard; sharing or posting it makes it available to the destination you select. No automatic crash-report upload or remote diagnostic collector was found. Review reports before sharing them.

## Data retention and deletion

- Library state and preferences generally remain until changed, removed through the applicable feature or cleared in Android app settings. There is no single fixed retention period for all app-private records.
- Automatic lyrics responses are evictable cache data. Explicit downloads and retained legacy saved lyrics use durable private storage and can be removed through the lyrics download controls; removing a saved download does not edit embedded lyrics in the music file.
- Artwork caches have their own size/count limits and pruning. The album lookup cache caps image storage at approximately 64 MiB and retained query records at 256; these are not limits for all artwork or all app data.
- Announcement entries are filtered for eligibility/expiry and refreshed from the feed; local read/dismissed markers do not have a separately verified time-based expiry. Their removal is not a request to delete server logs.
- Incomplete update downloads are deleted on failure/cancellation. The prepared APK is cached until reset, replaced or removed with cache/storage cleanup; installation alone does not guarantee immediate deletion.
- Android's **Clear cache** removes evictable cache data, not all library settings or saved downloads. **Clear storage** or uninstalling normally removes app-private data. Backups, selected model files and exported/shared files may remain elsewhere; restored backups may restore eligible app data.
- Changes already saved into shared music files, Library Builder moves and received Wi-Fi files are not undone by uninstalling erhu. Manage those files separately.

External services retain their own connection records and identifiers according to their policies. This repository does not establish their exact retention periods, deployed logging settings, data locations or deletion handling. Clearing local data does not delete information already received by those services. Firebase installation-ID deletion requires separate SDK/service action; this app does not currently provide that action.

## Third-party services

External providers govern their own processing. These are their official policy or service references, not guarantees about erhu's deployed configuration:

- MusicBrainz and AcousticBrainz: [MetaBrainz Privacy Policy](https://metabrainz.org/privacy).
- Cover Art Archive and its image infrastructure: [Cover Art Archive overview](https://musicbrainz.org/doc/Cover_Art_Archive) and [Internet Archive terms and privacy information](https://archive.org/about/terms).
- Apple iTunes / Apple Music: [Apple Privacy Policy](https://www.apple.com/legal/privacy/).
- Deezer: [Deezer personal-data policy](https://www.deezer.com/legal/personal-datas).
- Optional TheAudioDB: [official terms and privacy reference](https://www.theaudiodb.com/docs_terms_of_use.php).
- LRCLIB: [official service documentation](https://lrclib.net/docs). A dedicated official privacy policy could not be verified during this review.
- Optional Musixmatch: [official website](https://www.musixmatch.com/). Its current privacy-policy page could not be retrieved and verified during this review; consult the provider before enabling the licensed integration.
- Updates, downloads and repository links: [GitHub General Privacy Statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).
- Firebase Messaging / Hosting and Google browser search: [Firebase Privacy and Security](https://firebase.google.com/support/privacy) and [Google Privacy Policy](https://policies.google.com/privacy).

Firebase documents installation identifiers for Cloud Messaging and IP-address processing for Hosting. This does not mean erhu includes Firebase Analytics or Crashlytics. Provider availability, terms and policies can change; image downloads may also contact hosts returned by the provider.

## Children's privacy

The application does not ask for a date of birth or implement age verification, and no child-specific profiling or advertising integration was found. This is not a claim of children's-privacy certification or legal compliance. Parents and guardians should supervise file access, external searches, downloads and online services on a child's device. Third-party services apply their own age and privacy rules.

## Security

App-private storage and Android permissions limit ordinary access to local records. Remote service URLs inspected use HTTPS; local Wi-Fi transfer is the HTTP exception described above. The update installer validates packages and signing identity. The source does not establish application-level encryption of the Room database or every private file, and no system can guarantee absolute security. Android security, device access, backups, browsers and external services also affect protection.

## Changes to this policy

The date at the top identifies this revision. Changes to features, integrations or data handling should be reflected here. A modified or differently configured build may behave differently; this policy does not verify private deployment configuration or future code changes.

## Contact and privacy enquiries

Use the [official repository's issue tracker](https://github.com/lochanpthakke/erhu/issues) for privacy questions or to request a private contact route. GitHub issues are public: do not include registration tokens, credentials, private library listings or sensitive crash reports. No dedicated private privacy email or legal entity address has been verified for this project; none is invented here.

## Implementation references

These references record the source reviewed for this policy, rather than independent guarantees of runtime or third-party behaviour:

- [MediaStore scanner](../core/database/src/main/java/com/erhu/erhu/core/database/scanner/MediaStoreScanner.kt), [metadata repository](../core/network/src/main/java/com/erhu/erhu/core/network/repository/MetadataRepositoryImpl.kt), [artwork finder](../core/network/src/main/java/com/erhu/erhu/core/network/artwork/AlbumArtworkFinder.kt), [artwork browser search](../feature/library/src/main/java/com/erhu/erhu/feature/library/ArtworkWebSearch.kt).
- [Network configuration and logging](../core/network/src/main/java/com/erhu/erhu/core/network/di/NetworkModule.kt), [lyrics configuration](../core/network/src/main/java/com/erhu/erhu/core/network/di/LyricsModule.kt), [lyrics repository](../core/network/src/main/java/com/erhu/erhu/core/network/lyrics/OnlineLyricsRepository.kt), [saved lyrics](../core/network/src/main/java/com/erhu/erhu/core/network/lyrics/OnlineLyricsCache.kt), [animated artwork](../core/network/src/main/java/com/erhu/erhu/core/network/apple/AnimatedArtworkNetworkDataSourceImpl.kt).
- [Update checker](../core/network/src/main/java/com/erhu/erhu/core/network/repository/UpdateCheckerRepositoryImpl.kt), [APK downloads](../core/network/src/main/java/com/erhu/erhu/core/network/update/UpdateDownloadManager.kt), [announcement client](../core/network/src/main/java/com/erhu/erhu/core/network/announcements/AnnouncementClient.kt), [FCM setup](../app/src/main/java/com/erhu/erhu/announcements/AnnouncementMessaging.kt), [announcement state](../app/src/main/java/com/erhu/erhu/announcements/AnnouncementStore.kt), [publisher](../backend/announcements/functions/spark.mjs).
- [VYBE embedder](../core/vybe-mediapipe/src/main/java/com/erhu/erhu/core/vybe/mediapipe/MediaPipeTextEmbedder.kt), [local crashes](../core/common/src/main/java/com/erhu/erhu/core/common/logging/LocalCrashLogger.kt), [transfer coordinator](../feature/transfer/src/main/java/com/erhu/erhu/feature/transfer/WifiTransferCoordinator.kt), [repair checkpoints](../feature/library/src/main/java/com/erhu/erhu/feature/library/PendingRepairStore.kt).
- [App manifest](../app/src/main/AndroidManifest.xml), [playback manifest](../core/playback/src/main/AndroidManifest.xml), [transfer manifest](../feature/transfer/src/main/AndroidManifest.xml), [backup rules](../app/src/main/res/xml/backup_rules.xml), [data-extraction rules](../app/src/main/res/xml/data_extraction_rules.xml).
