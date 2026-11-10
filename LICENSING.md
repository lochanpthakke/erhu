## Current candidate implementation — 10 November 2026

See [APK_RELEASE_READINESS.md](APK_RELEASE_READINESS.md) for implemented 1.0.18
source gates, dependency/asset overrides and remaining blockers. The source-visible
licence is scoped to controlled original material; there is no distribution
clearance. APK generation/tests are deferred at the user's request. All Stage 1–5
evidence below is historical and preserved; statements that no dependency, version
or functionality changed describe those earlier stages, not this candidate.

# erhu Android licensing transition

## Wrap-up position — 10 November 2026

The concise current position is [WRAP_UP_REPORT.md](WRAP_UP_REPORT.md). Stages 1–5 remain preserved evidence; dated checks and proposals are not new release approval. The intended 1.0.18 licence is **source-visible and proprietary**, limited to rights actually controlled by the Android contributors. It reserves reuse of protected original expression while preserving independent implementations of general features, historical GPL, third-party and mandatory-law rights. No proprietary release is cleared.

The ownership exclusion is universal: bundling, repository inclusion, online retrieval or in-app display does not make third-party or user-owned material Erhu-owned. Its upstream terms/notices and applicable reused-GPL obligations remain; exclusion is not redistribution permission. Original application-specific UI/graphics are protected only where rights are established, without claiming ownership of common patterns, algorithms or general features. See [PUSH_READINESS.md](PUSH_READINESS.md) for documentation/push readiness separately from APK clearance.

Public hosting does not prevent cloning/forking or override [GitHub's Terms of Service, including section D](https://docs.github.com/en/site-policy/github-terms/github-terms-of-service). Its platform grants, including applicable processing/AI provisions, require review alongside the draft. No visibility or account setting is changed.

Unconfirmed permissions remain pending. Retain existing artwork; resolve identified rights issues rather than redesigning everything. Supplier requests remain unsent. Native investigation and unavailable runtime work are deferred, not their applicable obligations. Optional integrations must be permission-cleared or disabled/gated in the eventual release; Stage 5 configuration is still a proposal, not an implemented gate or verified blank-key build.

Review branch: `proprietary-licence`. Prepared: 10 November 2026.

## Status: reviewable transition, not release clearance

The requester reported separate approvals from Kshitij H Kekunnaya and Lochan Thakke, confirmed that each retains their own original contributions, and selected **1.0.18** as the intended first proprietary Android version. Their stated Android commercial revenue split remains **50/50**, independently of copyright ownership. No assignment or signed commercial agreement is manufactured by this documentation.

This branch prepares prospective terms for original Android project contributions. **Do not publish a proprietary APK from it until the release gates below are resolved.** The top-level [LICENSE](LICENSE) is a future-release candidate with unresolved jurisdiction/contact details; it does not certify third-party compliance or change an existing binary's licence. The application version remains 1.0.17; no version/signing/dependency/behaviour change is made here.

## Reason and scope

The participants intend to operate and commercially license future Android development independently. The draft permits source inspection and personal, non-commercial use of authorized binaries. Reuse of protected original expression requires permission, subject to historical GPL, third-party, platform and mandatory-law rights. Independent implementation of general features is not prohibited.

Android has no shared revenue pool, financial account, administration or automatic sublicensing arrangement with the independent iOS implementation through this document. No rights in either implementation are transferred to the other. Actual cross-platform reuse requires permission for the specific material.

## GPL history remains available

The latest verified GPL release—and intended last GPL release if no intervening GPL release is published—is **1.0.17**, [`erhu-v1.0.17-beta.31`](https://github.com/lochanpthakke/erhu/releases/tag/erhu-v1.0.17-beta.31). Its source is `69be688c19cd06987e77fa0bf9be4632fbcb986b`. The exact pre-transition GPL text follows a short historical explanation in [LICENSES/GPL-3.0-historical.txt](LICENSES/GPL-3.0-historical.txt).

Old commits, tags, releases, source and binaries are not rewritten, removed or relicensed. Existing recipients retain their GPL rights, including redistribution and modification under the GPL. Prior GPL grants are not withdrawn for unchanged portions that also appear in a future proprietary release. A recipient may continue using their GPL copy; this transition is not a technical copying restriction and does not make old source exclusively proprietary. [GNU FAQ](https://www.gnu.org/licenses/gpl-faq.en.html#HeardOtherLicense).

No first proprietary tag or binary is created by this task. If another GPL version is released before activation, update the boundary truthfully instead of treating 1.0.17 as permanently final.

## Distribution gates

1. **jaudiotagger:** resolve the Oracle-noticed source provenance and demonstrate a lawful LGPL replacement/relinking route for the actual optimized Android artifact. Retaining classes with R8 is not sufficient proof. A source link alone is not a completed compliance package.
2. **Third-party notice/source package:** the resolved inventory has 282 coordinates, but component-specific notices/copyrights and the complete native-component bill of materials are not all verified. Complete these items, including MediaPipe native libraries and OkHttp's MPL public-suffix data. A POM licence label is evidence, not a blanket clearance.
3. **Assets:** confirm rights for the temporary logo, raster wordmark/font, bundled album/artist imagery, lyrics in previews and any actually reused external code/assets. No commercial image permission is inferred from device ownership or taking a screenshot.
4. **Services:** verify the applicable Apple, Musixmatch, Deezer, TheAudioDB and MusicBrainz arrangements for the intended commercial use, features and data. Public accessibility is not permission to redistribute content commercially.
5. **Legal details:** both Android licensors must select governing law, jurisdiction, a private commercial/privacy contact, and finalize the candidate terms with appropriate professional review. The placeholders are not settled contractual facts.
6. **Release verification:** run the actual candidate debug/release APK checks through GitHub after an authorized source-upload path is available. This task forbids pushing or publication; the standing project rule forbids local APK compilation. No fresh APK result is claimed for this licensing branch.

These gates also identify issues worth reviewing for existing distributions; preserving historical GPL rights does not certify those releases' third-party compliance.

## Commercial enquiries and future contributions

Commercial permission must be obtained in writing from the rights holders for the material involved. For the original jointly developed Android application, coordinate with both Kshitij H Kekunnaya and Lochan Thakke. Private contact: **[TO BE CONFIRMED BY BOTH ANDROID LICENSORS]**. The [Android issue tracker](https://github.com/lochanpthakke/erhu/issues) can be used to request a private contact route; do not post credentials or confidential agreements there.

No CLA or assignment is introduced or assumed. Before accepting future contributions, record the contributor's identity, provenance, retained rights, licence grant and any required written permission for proprietary distribution. Do not assume a pull request, repository access or AI co-author label transfers copyright. Existing contributor credits stay intact. See [COPYRIGHT.md](COPYRIGHT.md).

## Additional technical and commercial risks — recommendations only

| Area | Finding and recommendation |
| --- | --- |
| Public GitHub updater | The app queries public releases in `lochanpthakke/erhu` without a personal access token. Making that repository private can produce unauthenticated failures. Before any visibility change, use a separate public release-only repository or an authenticated/signed public update manifest and APK host. Never embed a repository PAT in the app. No updater migration or visibility change is implemented. |
| In-app notices | Diagnostics & About has a privacy link but no dedicated third-party notices screen. Add an offline-accessible notices/source-offer presentation in a future product task. The Markdown inventory alone is not proof that shipped APK recipients receive all required notices. |
| Apple artwork | `NetworkModule`, `AppleMusicTokenProvider` and `AppleMusicCatalogApi` use `amp-api.music.apple.com` and public web-player-related token extraction. The official Apple Music API documentation does not by itself authorize this access method or content redistribution. Seek written permission/appropriate documented access; expect endpoint/token stability risks. No code or credentials are changed. |
| Musixmatch | Optional timestamped subtitle retrieval is disabled without a configured key. An API key or SDK licence does not establish rights to display/cache full synced lyrics in the intended commercial product. Obtain the relevant commercial agreement; do not bypass restrictions. |
| Deezer | Portraits, metadata and genre requests use its public API/CDNs. Review [developer terms](https://cdn-content.dzcdn.net/pdf/CGU-developers.pdf), commercial authorization, caching and attribution requirements. `ResilientDns` also hard-codes `188.165.234.195` for `api.deezer.com`; review endpoint ownership/stability and request behaviour separately. No DNS or provider change is made. |
| TheAudioDB | Optional release requests require a configured key; debug has a public development-key fallback. [Published terms](https://www.theaudiodb.com/docs_terms_of_use.php) distinguish development use and paid app-store distribution. Confirm subscription and intended distribution rights rather than assuming free access permits commercial redistribution. |
| MusicBrainz / AcousticBrainz | MusicBrainz distinguishes CC0 core data from supplementary data with different conditions. Genre/tag retrieval needs particular attention; review the exact fields used and any commercial data agreement. [Data-licence reference](https://musicbrainz.org/doc/About/Data_License). AcousticBrainz publishes its dataset as CC0, but that does not clear album art or lyrics from other services. |
| Captured Apple fixtures | The Apple test-resource folder contains large captured HTML/API data and playlists. Establish what expressions/data can be retained and replace them with minimal synthetic fixtures in a separately reviewed change where appropriate. Do not delete tests or fixtures blindly. |
| AI-assisted contributions | Preserve AI tool credits and verify human authorship/provenance of substantial portions. Copyright protection/enforceability depends on governing law and the actual human contribution; no automatic ownership or assignment is claimed. |
| Public source / platform terms | Making future source viewable is distinct from granting a broad code licence. Account/repository platform terms and existing GPL grants remain relevant; no public/private repository change is part of this task. |

## Verification and restrictions

Only licence documents, the relevant README/VYBE-setup phrases and the 12 existing first-party GPL headers are in scope. Header checks compare the remaining source bodies byte-for-byte. Historical GPL preservation is checked against its original hash. Existing external and Gradle wrapper notices are preserved.

No dependency is removed or replaced. No playback, DSP, metadata, artwork, lyrics, VYBE, updater, permissions or UI implementation is altered. This branch must remain local: no push, merge into master, release, visibility change, historical tag deletion or history rewrite is authorized by this task.

### Remaining licence-reference classification

The reference audit found expected references in the prospective terms and inventory, the preserved historical GPL, first-party headers explaining historical grants, third-party licence/NOTICE texts, Apache notices in Gradle launcher scripts, historical implementation notes, and the backend npm lockfile. The npm licence declarations are preserved and are outside the Android app runtime/compile inventory. No remaining reference was blindly deleted. Captured Apple fixtures remain unchanged with provenance pending.

### Checks performed on this review branch

- Selected module tests: **472 passed, zero failures/errors/skips**: DataStore 44, sound 58, playback 113 and library 257. The model module has no unit tests. The check run also included repository-wide KtLint and Detekt and completed successfully in 6m 24s. Existing compiler/deprecation warnings remain; no functional code was changed to silence them.
- All 12 modified Kotlin source bodies match their pre-task bytes after the initial comment; existing copyright names are preserved. Documentation links and the six extracted legal-text blocks were checked.
- Historical GPL content matches the exact original Git blob (its prior working-copy difference was CRLF only). Original Git-blob SHA-256: `961940cfd5d8506d57dbdd1db9ad0545c6ba47153dfbdef435e26bf9ec97f8c0`.
- The dependency inventory task succeeded after an initial AGP artifact-variant enumeration failure; no permanent dependency-report plugin was added. The resulting coordinate/evidence snapshot was reused rather than repeatedly resolving the graph.
- **Debug/release APK build not run for this branch:** project policy requires GitHub compilation, while this task expressly forbids uploading/pushing the branch. The existing 1.0.17 CI success is not presented as a new licensing-branch APK test. No local APK was compiled, no phone installation was performed and no signing configuration was changed.
- Third-party attribution/native provenance, practical LGPL compliance, Oracle-noticed sources, image/logo rights, external-service agreements and legal placeholders remain release gates; passing software checks does not resolve them.

- Diff whitespace checking passes for authored changes. The upstream MPL-2.0 text contains an existing trailing space on its line 38; it is retained verbatim and excluded from that whitespace-only check, rather than silently editing the third-party licence.
