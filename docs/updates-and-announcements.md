# Update checker and remote announcements

## Update-checking cause and fix

The official repository is `lochanpthakke/erhu` and was verified PUBLIC. Its only published Release during investigation was `v1.0.2-beta.1`, containing a signed APK, checksum and `verification.json` (APK version 1.0.2/code 3). GitHub's `/releases/latest` excludes prereleases and returned 404. The public release-list endpoint returned 200. Installed 1.0.3/code 4 came from Actions, not a published Release.

The checker now reads the public release list without credentials, explicitly includes betas for erhu's present beta distribution, rejects drafts/unpublished entries and compares numeric versions. Release verification metadata supplies the actual APK versionName/versionCode, so a beta tag can correctly describe an APK with a numeric versionName. Installed code 4 is correctly up to date against published code 3. No newer published Release is invented. APK identity, CPU/SDK compatibility, checksum and signing validation, FileProvider installer, permission/confirmation flow and monthly/manual request coordination are retained.

The release workflow supports explicit beta/stable publication. Manual workflow dispatch defaults to publishing; ordinary branch pushes still produce artifacts only. Publishing validates source/checksum and increasing versionCode, creates a draft, attaches/verifies APK assets, then exposes the Release. Failed uploads remain drafts. No workflow or publication was triggered for this task. Before distributing this changed client, increment versionCode beyond the installed 4 and choose the intended versionName. Future publication requires explicit owner approval and pushing the reviewed workflow changes.

## Announcement implementation

Android uses FCM data-only messages containing a stable announcement ID and event, then retrieves authoritative content from a configurable HTTPS API. Firebase client options and API URL are external build inputs. Without configuration, messaging is disabled safely. FCM manages registration tokens; startup/token refresh subscribes to `erhu-announcements`. Tokens are not logged. One-device tests use an explicit test registration token, not the broadcast topic.

The backend lives in `backend/announcements`. Firebase Functions uses managed service credentials, Firebase Authentication ID-token verification with revocation checking and an `admin` custom claim. Firestore denies all direct client access. The Hosting admin page signs in via Firebase Auth, keeps its ID token in memory, and supports title/body/type/image/action, publication/expiry, version-code/name restrictions, active status and scheduling. Its default audience is one test device; broadcast requires an explicit selection and confirmation. Test notices are excluded from the public feed. Deactivation invalidates the content and its notification.

The app persists cached content and read/dismissed/notified IDs using DataStore. It provides one custom dark/glass popup at a time and an active-announcements inbox in Diagnostics & About. Read/dismissed notices do not automatically reappear; users may reopen them explicitly in the inbox. Expiry and version eligibility are checked on device. Background workers retry and synchronize; normal foreground refresh is throttled to 15 minutes and periodic refresh runs every 12 hours. Notification permission denial leaves in-app delivery usable. A notification opens the specific ID without starting playback actions.

FCM is best-effort, not guaranteed delivery. Devices recover through foreground/periodic feed retrieval. Offline devices cannot learn revocations immediately; cached content still respects expiry. Group updates use stable IDs; publish a new ID for a genuinely new notice instead of expecting a dismissed ID to resurface.

## Firebase configuration and deployment

### Free Spark deployment (current default, 2026-11-05)

The owner authorized project creation and accepted Firebase terms. The actual project is `erhu-app-25b513`; Android package `com.lochanpthakke.empee3` is registered. Its generated client configuration stays in untracked local setup files and GitHub Actions variables. No billing account or private server key is required by this path.

`firebase.spark.json` deploys only static Firebase Hosting JSON. The app uses the same `/api/feed` and `/api/announcements/<UUID>` contract, material, persistence, expiry/version checks and FCM worker. `functions/spark.mjs` is the trusted local administrator/publisher, sharing the existing validation and message model. No Functions, Firestore, Scheduler, public admin login or server is deployed. Hosting content is public: never put secrets or confidential messages in notices. Test notices are omitted from the feed, but their opaque direct URLs are not access control.

From `backend/announcements`, after `npm ci --prefix functions` and `firebase login`:

```powershell
$env:ERHU_ANNOUNCEMENTS_STATE = 'C:\private\erhu-announcements.json'
# Use a private, real location outside Git; the path above is an example.
# Authenticate FCM with Application Default Credentials, or supply a short-lived
# GOOGLE_OAUTH_ACCESS_TOKEN authorized for this project. Never commit credentials.
$env:ERHU_TEST_FCM_TOKEN = '<actual device registration token>'
node functions/spark.mjs put erhu-app-25b513 notice.json
```

The notice JSON requires `title`, `body`, `type` (`general`, `feature`, `important_update` or `maintenance`), and ISO `expiresAt`. Audience defaults to `test`. Optional fields match the existing domain model. Keep the returned ID in later edits. `audience: "all"` additionally requires the explicit `--all` argument; it is never inferred from a missing device token. The token is passed only through the environment and never written to state, Hosting or logs.

The publisher validates, writes durable local state, deploys content, then sends ID-only pushes through the official FCM HTTP v1 API. Failed deployment sends nothing. Successful sends record the revision so repeating `sync` does not resend unchanged notices. Failures can be retried with `node functions/spark.mjs sync erhu-app-25b513`. An interrupted process between FCM acceptance and recording the receipt can redeliver the same ID; Android's persistent IDs prevent duplicate presentation. Back up this local state securely; it is the publisher's source of truth. Do not run concurrent publishers against the same site/state.

To deactivate, put the same ID with `active: false`; static content updates before an invalidation push. Expired notices are rejected on device even if Hosting has not been synchronized. Future publication requires running `sync` when due (manually or using an existing trusted workstation scheduler); there is no always-running cloud scheduler on Spark. No all-user test has been authorized.

Actual Hosting URL is obtained from `firebase hosting:sites:list`, not guessed. Set Android `ERHU_ANNOUNCEMENTS_URL` to that URL plus `/api`. The release workflow now imports the five public client configuration fields below from repository Actions variables. An APK built without them remains safely disabled. The original signing certificate and production application ID are preserved.

Setup verified: Spark-only Hosting deployment succeeded at `https://erhu-app-25b513.web.app`; `/api/feed` returned HTTP 200 with an empty JSON feed. All five generated public client fields were set as repository Actions variables. Eleven backend tests passed, including Spark audience filtering, expiry/scheduling boundaries, one-token targeting, HTTP v1 payloads and failed delivery. Actions run 42057716869 succeeded for commit `faddb77`; signed 1.0.5 (6) was installed in place on S23 RZCXA1H43GW. The installed SHA-256 matched the verified artifact and all five actual client fields were verified in DEX. FCM accepted test announcement `63cfb1e9-1d54-4dc4-934c-81d650bebd10`; after owner unlock, the actual erhu popup visibly displayed body `hello`. The public feed excludes it. No broadcast, billing upgrade or user-data clearing occurred. Public release publication followed after the owner explicitly requested it (see below). A temporary same-certificate read-only registration probe was removed. Actual background notification, scheduling and remote deactivation remain outside this single foreground delivery test.

### Optional Cloud Functions backend (requires Blaze; not deployed)

The original Functions/admin implementation remains available for a future explicitly approved billing upgrade. The following steps apply only to that optional deployment, not the Spark setup above.

1. Create/select your Firebase project. Enable Firestore, Firebase Authentication Email/Password, Cloud Messaging and Hosting. Cloud Functions/Scheduler may require the Blaze billing plan; review billing before deployment.
2. Register a Firebase Web app for the admin Hosting configuration and authorize the Hosting domain in Firebase Authentication. Register the existing Android package `com.lochanpthakke.empee3`. For isolated device tests, register the separate `com.lochanpthakke.empee3.bench` Android client in a test project. Obtain that client's public Firebase application ID, project ID, sender number and restricted client API key. These are client identifiers, not server credentials.
3. Use Node 22 and an authenticated Firebase CLI on a trusted workstation. From `backend/announcements`, run `npm ci --prefix functions`, then `firebase deploy --project <YOUR_PROJECT_ID> --only functions,firestore,hosting`. The placeholder is not a real project identifier. Default region is us-central1; change both the function region configuration and Hosting rewrite consistently if selecting another region. Inspect generated HTTPS function/Hosting URLs rather than inventing an endpoint.
4. Create the owner account in Firebase Authentication. In a trusted environment with Application Default Credentials and appropriate IAM permissions, set `GCLOUD_PROJECT` to the actual Firebase project ID, then run `node functions/grant-admin.mjs <EXISTING_OWNER_UID>`. Never put service-account credentials in Android, frontend configuration or Git. Sign in again after setting the claim. Normal signed-in users cannot publish.
5. Open the deployed Hosting admin site. It obtains public Firebase web configuration from Hosting's `/__/firebase/init.json`. No admin password or ID token is embedded in its source. Ensure Firebase Auth permits the Hosting domain.
6. Supply Android public build configuration through environment variables or untracked user Gradle properties:

| Environment variable | Gradle property |
| --- | --- |
| ERHU_FIREBASE_APP_ID | erhu.firebase.appId |
| ERHU_FIREBASE_API_KEY | erhu.firebase.apiKey |
| ERHU_FIREBASE_PROJECT_ID | erhu.firebase.projectId |
| ERHU_FIREBASE_SENDER_ID | erhu.firebase.senderId |
| ERHU_ANNOUNCEMENTS_URL | erhu.announcements.url |

The API URL is the actual deployed `announcementApi` function base, or the actual Hosting URL followed by `/api`; the client appends `/feed` and `/announcements/<id>`. Keep private admin/service credentials entirely server-side. Build inputs must be configured on GitHub Actions before building a configured production APK; nothing is hardcoded into the workflow. The current implementation manually initializes FirebaseOptions, so merely adding google-services.json does not configure these build fields.

7. Install a configured isolated test build only after confirming its `.bench` application ID. Obtain its FCM registration token through a trusted debug test session without publishing/logging the token. Publish using ONE TEST DEVICE and that token. Do not use the all-subscribers audience for tests.
8. Verify foreground popup (no duplicate system notification), background notification, tap routing, dismissal across restarts, denied notification permission, offline recovery, expiry, version bounds, multiple notices and remote deactivation. Only after this passes should the owner consider production broadcasting.

## Local validation and limits

Backend tests: `npm test --prefix backend/announcements/functions` uses a real local HTTP server with mocked Firebase auth, storage and push dispatch. It checks protected publishing, one-device targeting, scheduling, expiry/deactivation, invalid content and version constraints. It does not establish Firebase delivery or actual Firebase token verification end-to-end.

Release scripts: `python -m unittest discover -s scripts/ci -p 'test_*.py'`.

Android: targeted network update/announcement tests, settings installer tests, app tests and isolated debug/Android-test builds. `UpdateLiveIntegrationTest` is opt-in with `ERHU_LIVE_UPDATE_CHECK=1` and checks the actual public repository and verification metadata. `AnnouncementPersistenceTest` requires the isolated S23 test app and verifies DataStore recreation; it must not run against production.

No Firebase deployment, real registered-device delivery, production announcement, new public Release, commit or push has been performed. Production delivery checks remain pending Firebase configuration/deployment.

### Executed results (2026-11-05)

- 364 JVM tests passed: core/network 354, settings 2, app 8. Two opt-in live tests were skipped in the ordinary run; the GitHub updater live check was executed separately and passed on retry after an initial transient connection error. The artwork live test is unrelated and was not rerun.
- The actual public API check offered v1.0.2-beta.1 / APK 1.0.2 code 3 to an older installation and returned UpToDate for the real installed 1.0.3 code 4 identity. No credentials were sent.
- Eight backend HTTP/domain tests and six Python release-script tests passed. The Firebase Functions module loaded successfully without deployment or sending a message.
- Isolated debug and Android-test APK builds, scoped Kotlin lint and whitespace checks passed.
- Galaxy S23: one Android persistence test passed (0.117 seconds), verifying cached items plus read/dismissed/notified IDs across DataStore recreation. The Diagnostics inbox opened safely without Firebase configuration; the OS notification permission prompt opened, and the isolated app remained usable after POST_NOTIFICATIONS was explicitly revoked.
- Temporary benchmark packages were removed and screen-awake restored to 0. The production app stayed 1.0.3, last updated 2026-11-04 22:32:25. No user music files were modified.
- Not verified: deployed admin authentication against an actual Firebase project, real FCM foreground/background delivery, notification-tap-to-content delivery, scheduled cloud dispatch or actual production broadcast. These require the owner-provided configuration and the test-device procedure above. Local HTTP tests use mocked auth/storage/FCM; they are not evidence of production delivery.

Key changes: core/network update checker/API/version validation and announcements models/client; app announcements persistence/controller/FCM workers/custom UI/notification routing; Diagnostics navigation; release workflow and CI publisher; Firebase Functions, Firestore rules/indexes and Hosting admin under backend/announcements. No playback, metadata, library-scanning or database schema implementation was changed.

### Current public APK distribution (2026-11-05)

The verified 1.0.5/code 6 APK from Actions run 42057716869 was published with owner authorization under [`erhu-v1.0.5-beta.1`](https://github.com/lochanpthakke/erhu/releases/tag/erhu-v1.0.5-beta.1). Uploaded APK, checksum and verification.json were downloaded again and byte/hash checked before publication. The superseded 1.0.2 APK/checksum/metadata were backed up locally, then removed from the older release; its two VYBE model assets remain unchanged. APK source remains `faddb77`; documentation-only commits do not change that build identity. The original application ID/certificate are preserved. The older beta tag and VYBE model assets remain intact; its old APK is superseded by the current release. Release tag numbers and verification.json must agree so the update checker can validate them. No repeat compilation is needed for this documentation/publication step.

Post-publication validation: the opt-in real GitHub update-check integration passed against the published release (1 test, no failures/skips): an older app receives an available update and the published version is up to date. The test now derives current version metadata from the release instead of hardcoding the superseded beta. The automatic tag-triggered duplicate build was cancelled; the published bytes remain the successful run 42057716869 artifact.
