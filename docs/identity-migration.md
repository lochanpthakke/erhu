# erhu identity migration

Branding, application labels, theme names, source packages, Gradle namespaces,
source directories, diagnostics, local transfer-page branding, project comments
and documentation now use **erhu**. Canonical source namespace: `com.erhu.erhu`.

## Upgrade compatibility

- Application ID stays `com.lochanpthakke.empee3`; signing configuration is unchanged.
  Changing the ID would create a separate Android app/UID and would not inherit
  the existing private library/settings. That requires a separate export/import plan.
- Room remains version 14 and uses `empee3.db`. Schema history is moved to the new
  database class directory with unchanged table definitions and identity hashes.
- DataStore filenames, protobuf field numbers, preference keys, table/column names,
  migrations, notification identifiers and transfer-protocol identifiers are retained.
- The old launcher component is a manifest activity alias targeting the renamed
  activity, preserving installed home-screen shortcuts.
- Existing widget, playback/transfer service and WorkManager class names remain in
  narrowly scoped compatibility packages. Android, Glance and WorkManager persist
  these names; renaming them would detach widgets or invalidate scheduled work.
- Explicit serialization names retain the old descriptors, including typed
  navigation routes and existing theme/skin document types.

These remaining old identifiers are technical upgrade contracts, not erhu branding.
Actual local workspace paths and historical APK/backup/evidence filenames remain
valid; the repository directory itself has not been moved.

## External identity requiring confirmation

No replacement username, repository, domain or email address was invented:

| Existing reference | Pending decision |
|---|---|
| `https://github.com/lochanpthakke/empee3` | Confirm the new public source repository URL. |
| `repos/lochanpthakke/empee3/releases/latest` | Confirm the replacement GitHub update endpoint. |
| Git remotes for `lochanpthakke/empee3` and `Kshitij1425/empee3` | Supply confirmed replacement remote URLs if these repositories move. |

The user plans to rename the existing repository. Existing external links remain
in place until that rename is complete and its destination is confirmed.
Third-party names, contributor history, copyright notices and Git history are retained.

## Validation

- Optimized ARM64 release assembly and Android release checks passed.
- **999 JVM tests passed**, zero failures/skips. Coverage includes historical Room
  migrations, populated legacy-database reopening/data retention, saved route
  descriptors/arguments, preferences, playback, network and VYBE.
- Android interaction-test APK assembly passed; device UI/upgrade smoke tests were not run.
- APK label is exactly `erhu`; application ID remains `com.lochanpthakke.empee3`.
- Signing certificate SHA-256 is unchanged:
  `5bb0fc457b96d9fb6cbc606009de44d633bee95b58fec2dc633a7a4eb54a9ce5`.
- All 10 exported schema identity hashes and table CREATE statements match the
  previous version. Protobuf wire declarations are unchanged.
- Post-migration search has no unclassified old branding. Remaining references
  are compatibility IDs/serialization, existing external links, and actual or
  historical workspace/artifact paths. Generated caches, Git history and
  unrelated untracked scratch work were excluded from branding edits.
- Full-repository formatting retains five pre-existing violations: long lines in
  WikipediaProvider, SettingsScreen and SafFileMover (two), plus a backing-property
  naming issue in AnimatedArtworkViewModel. No rule/baseline was weakened.
- The uncommitted instructions draft was preserved except for identity
  replacements; unrelated content was verified unchanged.

Important changes: `settings.gradle.kts`; module Gradle namespaces;
app/playback/transfer manifests; app-name/theme resources; source directories
under `com/erhu/erhu`; `ErhuApplication`, `ErhuDatabase`, `ErhuTheme`, `ErhuColors`,
`ErhuShapes`; Room schema directories; compatibility serialization annotations;
transfer-page/diagnostics branding; benchmark scripts; README and Markdown.

No commit, push, Git-history rewrite or phone installation was performed.


Release APK: `erhu-release-arm64.apk` (37,227,161 bytes). SHA-256 `9b98a581fc6adca67ce1baab3d4975579077a8248c3656db5629185c3d4528c3`.
