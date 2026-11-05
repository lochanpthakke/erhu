# App update checker

The previous checker compared against hardcoded 1.0.0, discarded failures as a null success, and opened a release webpage instead of downloading an APK. Its monthly automatic-check label had no scheduled work behind it.

The existing GitHub API/repository is now shared by manual checks and one unique, network-constrained 30-day WorkManager job. Manual checks do not consult the monthly timestamp. Concurrent manual/background checks share an in-flight request. Rate-limit responses produce explicit cooldown/retry messages; network failures, malformed replies, absent public releases and missing compatible APKs stay errors instead of claiming the app is current. Automatic outcomes are persisted and shown in Diagnostics & About; automatic work never downloads or installs anything.

Installed versionName/versionCode come from PackageManager. Numeric SemVer parsing handles v/erhu-v prefixes, missing patch components, prerelease ordering and build metadata. Drafts/prereleases are not offered. The official release workflow already publishes verification.json: when present, its package/version/versionCode are checked before offering an update. Without it, a newer semantic tag identifies a candidate; the actual APK's versionCode must still be strictly greater than the installed package before installation is allowed. Every published update should increment Gradle versionCode and consistently tag its versionName.

Downloads select a supported ABI or unambiguous universal APK from the configured repository's HTTPS release assets. Debug/unsigned-named assets are excluded. A single app-wide transfer shows bounded-frequency progress, supports cancellation/socket closure, and removes partial files. Size and GitHub's SHA-256 asset digest (when provided) are checked. Android parses the APK and its signing information; package ID, actual versionCode/versionName, signing certificate/rotation history, minimum SDK, native ABI and debug/test-only flags are checked. The package installer performs the final installation/signature checks.

A scoped, non-exported FileProvider exposes only cacheDir/updates. REQUEST_INSTALL_PACKAGES and Android's unknown-source permission flow precede the user-confirmed package installer. Installer cancellation/permission denial retain useful retry states. Downloads do not silently install updates; the automatic scheduler cannot launch the installer. Downloads continue through page recreation; downloaded release details remain visible after reopening the page.

## Actual repository/service validation

Configuration remains the existing **lochanpthakke/empee3**, centralized in UpdateRepositoryConfig; no repository or credential was invented. The app sends no GitHub token. The exact anonymous request to `https://api.github.com/repos/lochanpthakke/empee3/releases/latest`, with the client's Accept/User-Agent/API-version headers, returned **404 Not Found** on 2026-11-01. Authenticated read-only inspection confirmed the repository is private and has **zero published releases**. Actions artifacts are not GitHub Releases.

A public release repository and published Release with a compatible signed APK are required for token-free production updates. Either make the configured repository public and publish a release, or provide the real public distribution repository and update UpdateRepositoryConfig. This task did not change repository visibility, publish a release, or put credentials into the application. Live available-update/download/installer verification is blocked until that distribution prerequisite exists.

## Validation

342 network tests and 2 settings tests passed, including existing regressions. New coverage includes numeric/prerelease versions, current/newer versions and versionCode metadata, HTTP/JSON API contract and public headers, no internet, 403/429 quotas and cooldown, no public release/no APK, shared requests, ABI selection, invalid/incompatible identity/signatures/SDK/debug flags, streamed downloads/checksums, invalid downloads, cancellation/partial cleanup, permission denial and installer cancellation policies. API success/download responses use local HTTP fixtures; they are not claimed as live success against the private repository.

App Kotlin/Java/Hilt compilation and current merged-manifest checks passed. The scoped provider, permission and XML path are verified. Targeted formatting/ktlint and whitespace checks passed. Android's actual permission/installer dialogs were not exercised on the phone, and no new APK was installed. No local release compilation, commit or push was performed.

## Release preparation — 2026-11-02

Authenticated GitHub inspection now confirms the official repository was renamed to **lochanpthakke/erhu** and is public. The update client and release-asset allowlist use that confirmed name. No published Releases existed at this inspection; Actions artifacts alone do not make an update available. Version 1.0.2 increments the installed versionCode to 3 while preserving the existing application ID and signing certificate.
