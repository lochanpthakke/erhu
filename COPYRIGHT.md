# erhu Android copyright and attribution

Review date: 10 November 2026. Android repository: `lochanpthakke/erhu`.

## Confirmed arrangement and limits of the evidence

The requester separately reported approval from **Kshitij H Kekunnaya** and **Lochan Thakke** for preparing future proprietary Android releases, then confirmed that each retains copyright in their own original Android contributions. No blanket assignment, joint ownership of every file, or copyright percentage is asserted.

The requester reports an agreed **50/50 Android commercial revenue split**. That is a revenue arrangement, not a copyright allocation. This document records the supplied arrangement; it is not a signed revenue-sharing contract or copyright assignment. None was found in tracked documentation. Git access, commit counts, repository ownership and technical leadership do not substitute for an assignment.

Kshitij is identified by the requester as the primary Android technical decision-maker. Lochan is the original project initiator and Android collaborator. Neither role proves ownership of another person's contributions.

## Authorship evidence

- Current Android history at source `69be688c19cd06987e77fa0bf9be4632fbcb986b` records Kshitij under three author identities and Lochan under one. No additional human commit author was identified in the current reachable history. This does not establish the provenance of all copied code or assets.
- Twelve first-party Kotlin files have existing notices naming Kshitij H Kekunnaya: `AudioUtilities.kt`, `PlaybackUtilities.kt`, `PlaybackUtilitiesTest.kt`, `ReplayGainMetadataTest.kt`, `AudioUtilityDsp.kt`, `AudioUtilityDspTest.kt`, `MetadataRepairOperation.kt`, `MetadataWriteAccess.kt`, `PendingRepairStore.kt`, `MetadataBackgroundTest.kt`, `RepairCheckpointTest.kt`, and `AudioUtilityControls.kt`. Their copyright holder is retained; only prospective licensing language changes. Lochan is not added to those headers without file-specific evidence.
- Other original contributions remain attributed through their existing notices and history. No mass replacement or exclusive whole-project ownership claim is made.
- Codex/Claude co-author labels appear in commit messages. They are preserved. Tool attribution does not establish a separate human contributor, an assignment, or copyrightability. Human authorship, review, provenance and enforceability of substantially AI-assisted portions require assessment under the governing law, which has not been selected.

## External code and resources: no project ownership claim

| Material | Evidence / ownership status | Required action before proprietary distribution |
| --- | --- | --- |
| Resolved Java/Kotlin/AAR dependencies | Upstream holders retain their rights; see [282-coordinate inventory](THIRD_PARTY_LICENSES.md). | Preserve applicable licences, notices and source/relinking obligations; complete outstanding binary/native notice verification. |
| jaudiotagger 3.0.1 | Main source headers identify Entagged contributors and LGPL-2.1-or-later. Nine tree sources retain Oracle proprietary/confidential notices. | Obtain upstream clarification for the mixed notices and establish a compliant LGPL distribution route. No Oracle code is claimed by either Android collaborator. |
| OkHttp public-suffix resource | Its embedded notice identifies MPL-2.0, separately from OkHttp's Apache licence. | Preserve the notice and identify/provide corresponding covered source. |
| EmbeddingGemma model | Separate download under Google's Gemma terms; not owned or relicensed by the Android collaborators. | Preserve the model terms, NOTICE and usage restrictions. |
| Album/artist images and lyrics shown in onboarding, widget previews, README and `docs/images/` | Android capture records identify real device-library artwork and lyrics, including SOS and Flower Boy. Capturing the app UI does not transfer rights in this content. | Obtain permission or replace with owned/licensed material before a commercial release. No fair-use, fair-dealing or redistribution clearance is assumed. Images are unchanged in this task. |
| `erhu_logo.png`, launcher variants and branding cube | Supplied by the requester as a temporary icon; creator, original licence and assignment were not established. | Confirm creator and rights to use, adapt, distribute and commercially display the icon. |
| Raster wordmark / other showcase compositions | `images/README.md` describes a typographic rendering and app captures. The exact source font and creator rights are not established by the PNG itself. | Verify the rendering font's licence and any external visual elements. |
| Fonts | No tracked `.ttf` or `.otf` was found. App typography uses platform/default families in the inspected source. | No bundled-font ownership claim; raster wordmarks still need separate provenance. |
| Apple test fixtures | `core/network/src/test/resources/apple/` includes captured HTML, responses and playlists, plus a synthetic-looking JS wrapper containing a token-shaped string. | Establish fixture provenance and permission; replace with minimal synthetic equivalents in a separate task where appropriate. Do not copy tokens into public notices. `catalog_with_motion_sza.json` has a direct reference in `AppleCatalogSerializationTest`; test usage of all other captured files was not established by the literal-reference check. Fixtures remain unchanged here. |
| Code/assets based on external implementation references | Android history and task records show external design/feature references, but are not a file-by-file assignment or permission record. | Identify any actual copied expression or assets and obtain the relevant rights. Merely implementing a similar feature does not establish that copying occurred. |
| Gradle wrapper | Existing wrapper licences/notices remain untouched. | Preserve those notices with redistributed build infrastructure. |

## Independent implementations

Android and iOS are independently operated. Azeem's work on the independent iOS implementation does not automatically confer Android ownership, revenue, administration or relicensing authority. Equally, this Android arrangement grants no rights in iOS material. If any actual reuse is identified, permission must come from its relevant rights holder.

No iOS repository, code, assets, accounts or financial arrangements were accessed or modified during this task. This document states the requester's Android arrangement, not findings about that separate implementation.

## Historical rights and release boundary

The latest verified GPL Android release is **1.0.17**, tag [`erhu-v1.0.17-beta.31`](https://github.com/lochanpthakke/erhu/releases/tag/erhu-v1.0.17-beta.31), source `69be688c19cd06987e77fa0bf9be4632fbcb986b`. It and previous GPL distributions retain their GPL rights. The original GPL text is preserved in [LICENSES/GPL-3.0-historical.txt](LICENSES/GPL-3.0-historical.txt).

The requester selected **1.0.18** as the intended first proprietary version. It is not published or cleared for proprietary distribution by this branch. See [LICENSING.md](LICENSING.md) for the release gates. Prior GPL grants for code also present in later versions remain available to their recipients; the new terms do not make previously GPL-distributed code exclusive.
