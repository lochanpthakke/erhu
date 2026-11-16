# Remote Libraries: Navidrome/Subsonic and Google Drive

Remote Libraries (drawer → Remote Libraries) connects erhu to music that is not on the phone.
Remote tracks play through the same player, queue, Now Playing screen, notification and equalizer
as device files. Nothing remote is copied into the device library or MediaStore.

## Google Drive: setup you must do once

erhu contains **no** OAuth client ID, secret or key. Google decides whether to grant access by
looking up an OAuth client registered for the app's **package name + signing certificate**. Until
that exists, "Connect a Google account" fails with "Google sign-in is not set up for this build".

1. In the Google Cloud console pick a project (the existing Firebase project works; no billing
   is needed) and enable **Google Drive API**.
2. Configure the **OAuth consent screen** (Google Auth Platform): app name, support e-mail,
   audience **External**. Under Data access add the scope
   `https://www.googleapis.com/auth/drive.readonly`.
3. While the app is in **Testing**, add every Google account that will sign in as a **test user**.
4. Create an **OAuth client ID → Android** for each build you want to sign in with. Each needs the
   package name and the SHA-1 of the certificate that signed that APK:

   | Build | Package | Certificate |
   |---|---|---|
   | Local debug APK | `com.lochanpthakke.empee3` | this PC's debug keystore |
   | Release / CI build | `com.lochanpthakke.empee3` | the production signing certificate |
   | Bench build (`-PbenchApp`) | `com.lochanpthakke.empee3.bench` | whichever key signed it |

   Read a certificate's SHA-1 from an APK with:
   `apksigner verify --print-certs app-debug.apk`

5. Install the build and use Remote Libraries → Connect a Google account.

### Scope and verification (not done)

- `drive.readonly` is a Google **restricted** scope. It is required: `drive.file` only reaches
  files the user picks one at a time, which is not a Drive browser, and erhu does not use it.
- Unverified, the consent screen shows an "unverified app" warning and the project is limited to
  100 users. In Testing status only listed test users can sign in and their grant expires after
  7 days, after which erhu asks them to sign in again.
- A public release needs Google's restricted-scope verification (privacy policy, homepage, demo
  video, scope justification) and possibly an annual security assessment. **None of this has been
  submitted or completed.**

### What erhu stores and sends

- Stored: the account's e-mail address, and for each track seen its Drive file ID, name and the
  tags read from it. No Google token or password is stored; Google Play services holds the grant.
- Access tokens are kept in memory for at most five minutes and sent only to `googleapis.com`, in
  the `Authorization` header, never in a URL. Drive requests are not logged.
- Only `GET` requests are made (`about`, `drives`, `files`). erhu cannot change or delete Drive
  content with the scope it requests.
- Disconnecting removes the account from erhu. To withdraw the permission itself, the user removes
  erhu under Third-party access in their Google Account.

## Navidrome / Subsonic

- Add a server with its address and either user name + password or an OpenSubsonic API key.
  The server is pinged with those credentials before anything is saved.
- Password sign-in uses the salted-token scheme (a fresh salt per request); the password itself
  is never sent. API-key sign-in sends `apiKey` instead.
- The password or key is encrypted with an Android Keystore key and kept in `noBackupFilesDir`.
- `http://` addresses show a warning and need the per-server "Allow an unencrypted connection"
  switch. Cleartext is permitted app-wide in `network_security_config.xml` only because Android
  cannot scope it to an address typed at run time; the per-server switch is the actual gate.
- Streams ask for the original file (`format=raw`) so seeking works.

## How it fits together

- Identity: `(source id, provider's id)`. A remote track's local id starts at 2^40
  (`RemoteIds.FIRST`) and its URI is `erhu-remote://<provider>/<sourceId>/<remoteId>`.
- Tables (Room 15): `remote_sources`, `remote_tracks`. Device scans never touch them.
- Playback: `RemoteMediaSources` resolves a remote URI to the real request each time a stream is
  opened, for the main player, the crossfade tail and the transition preview.
- Providers plug in through two Hilt sets: `RemoteStreamResolver` and `RemoteArtworkSource`.
- A remote track that fails because the connection dropped pauses in place; a track the server
  refuses is skipped.

## Not implemented yet

- Offline downloads and a persistent stream cache.
- Android Auto browsing of remote tracks.
- Listening statistics for remote plays.
- Revoking the Google grant from inside erhu.
