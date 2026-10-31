# Neutral Liquid Glass popups

Updated 2026-10-31. Supersedes the earlier playback-tinted menu material in this file. Reference: `D:/liquid glass/empee3-liquid-glass-spec/docs/LIQUID_GLASS_UI_SPEC.md`, both supplied videos and the newer maximum-clear screenshots. Existing `claudeinstructions.md` changes are preserved.

## Implementation

`PopupGlassSurface`, `Modifier.popupGlass` and `PopupGlassTheme` share the existing app backdrop and RenderEffect/AGSL lens. Retained GPU layers separate diffusion from the changing aperture. Compiled shaders are remembered; no CPU bitmap readback or per-frame background capture is introduced. Diffusion recording is held during the morph; Android may still propagate source RenderNode dirtiness. The first source recording now invalidates the popup draw even when the initial backdrop was unavailable.

Android 13+ uses a neutral, luminance-aware contrast shader: dark scene detail passes through unchanged, bright highlights are compressed for light labels, and the light profile lifts dark areas for dark labels. Fill, rim and reflection are neutral; saturation is 1, dispersion is 0, and no artwork palette selects material colour. Blue visible over the SOS cover is transmitted scene colour. Diffusion is 4–14 dp; fill alpha is 0.04–0.24 dark / 0.06–0.26 light. The thinner rim replaces the earlier heavy cloudy edge.

Android 12 retains diffusion with a conservative contrast fallback. Android 8–11, disabled glass and skins retain opaque neutral fallback. This implementation follows the supplied references and Apple's [material guidance](https://developer.apple.com/videos/play/wwdc2025/219/) and [morphing guidance](https://developer.apple.com/videos/play/wwdc2025/323/); it uses Android rendering rather than Apple's renderer. Permanent cards, navigation and mini-player material retain their existing treatment.

## Preference and motion

`PlaybackStateProto.popup_glass_opacity` remains optional field **36**, persisted by the existing ThemeRepository/DataStore. Absence defaults to **0.5**, explicit zero is preserved, nonfinite values reset to default and finite values clamp to 0–1. Appearance retains the preview, clear-to-higher-contrast slider and reset to 50%. Sidebar frostiness and Room schemas are unchanged.

Eight overflow trigger sites now provide measured window bounds through `GlassMenuAnchor`: album, artist, playlist, library, Now Playing, online lyrics, Transition Studio and transition editor. The original glyph hides while its shared popup glyph and aperture expand from the actual trigger bounds to the final panel. Other selectors use their measured parent anchor. Rows are measured at their final size; clipping and label visibility follow the same responsive spring, with a 150 ms reverse exit. Selection callbacks and confirmation opening wait until the reverse completes. Multi-selection remains open.

The native popup window is bounded to the union of trigger and panel plus optical margin. Transparent union padding dismisses the popup, while native Back/outside dismissal, keyboard focus and scrolling remain. Dynamic outline resolution occurs inside the graphics-layer observer so clipping follows the current aperture. Dialogs retain centred entrance, sheets retain native Material motion, and the sound preset field retains exposed-dropdown semantics. System animation duration scale applies.

## Popup inventory

| Surface | Previous implementation | Migration |
|---|---|---|
| Album, artist, playlist and Now Playing overflow | Shared GlassMenu | Neutral material and anchored morph |
| Shared song rows: Home, Library, search, Smart Playlists | Shared GlassMenu | Same shared implementation |
| Online lyrics, Transition Studio/editor overflow | Shared GlassMenu | Same shared implementation |
| Live-cover controls, resolution and compression panel | Custom Popup with tinted fill | Shared GlassMenu shell; specialised controls/callbacks retained |
| Artwork provider, animated-artwork resolution, metadata provider/field/mode selectors | Material DropdownMenu | Shared GlassMenu; multi-selection retained |
| Confirmations, playlist naming/editing, bookmarks, fix-tags, scan location, theme rollback, saved sound preset | GlassAlertDialog | Shared neutral material and centred entrance |
| Lyrics removal/search and animated-artwork notice/completion | Plain AlertDialog | GlassAlertDialog |
| Queue/history | ModalBottomSheet | QueueGlossSheet; native state/gestures retained |
| Transition option/duration panels | ModalBottomSheet | GlassFloatingPanel; dedicated scrollable native windows |
| Sound preset selector | ExposedDropdownMenu | Shared neutral optical modifier and 28 dp outline; native exposed-field semantics retained |
| Circular/square artwork crop dialogs | Dedicated editing canvas | Intentionally opaque to preserve image-editing contrast and colour judgement |
| Full-screen Transition Studio host / expanded player, lyrics and audio details | Full-screen editor/content surfaces | Excluded; retain navigation and permanent content presentation |
| VYBE / featured cards | No independent overflow implementation | Shared song menus where present; no VYBE redesign |

Permanent cards, album tiles, track rows, mini-player, navigation and ordinary Settings groups were not converted into popup glass. Playback, queue, lyrics, metadata and compression business logic are unchanged.

## Verification — correction pass

- **249 targeted JVM tests passed:** design system 54 and library 195. Prior DataStore 31 checks passed in the first pass; persistence implementation was unchanged in this correction.
- **8 Android interaction tests passed on Galaxy S23** (`OK (8 tests)`, 11.101 s): repeated dismissal, disabled actions, scrolling/confirmation, light/bottom placement, RTL/solid fallback, multi-selection and native light/dark contrast captures.
- Native screenshots show neutral material over neutral backgrounds. Representative material pixels yield 7.08:1 dark-profile and 8.36:1 light-profile label contrast; these are sampled fixture results, not a claim about every transmitted scene pixel.
- Optimized ARM64 release/R8 assembly and signature verification passed. Actual SOS artwork, underlying controls and refraction were visible in the running release. Settings reached 0% and 100%, reset to 50%, and retained 0% after process restart; sidebar frostiness remained 46% in the isolated copy.
- Two eight-cycle runs used the optimized isolated release while music played. Playback advanced from 0 to 13,634 ms during the recorded run and paused successfully afterward. Normal-time recording and sequential frames show forward/reverse morphing and repeated reopening.
- **Performance remains imperfect:** without recording, Android gfxinfo reported 536 frames, 75 deadline misses (13.99%), p50 6 ms / p90 17 ms / p95 38 ms / p99 48 ms. GPU p50/p90/p99 were 3/7/12 ms. The recorded run reported 548 frames, 12.41% deadline misses and p50/p90/p99 5/14/48 ms. These whole-process measurements include native window creation, backdrop and permanent chrome; they do not isolate menu shader cost. No zero-jank claim is made.
- Changed production Kotlin formatting passes. Whitespace checks pass. The latest design-system Detekt run retains three existing findings in `Glass.kt`; the selected library run retains 24 older findings. New anchor/layout/optics helpers have no findings. No rule or baseline was weakened.
- Validation covers one Galaxy S23 and static covers with active audio. The copied library contained no saved live-cover files, so live video/TextureView behaviour and older Android/OEM rendering remain unverified. No dependency, package identity, signing configuration, playback business logic or Room schema changes. Validation preceded the user-requested commit/push; pre-existing `claudeinstructions.md` edits preserved.

## Device isolation

The real `com.lochanpthakke.empee3` installation remains the earlier ARM64 debug APK, SHA-256 `57f50049d342fa6e06143074829e3cb8125a66ad79f216359f8c037875307f09`, installed on the user's earlier explicit request. Its media session remained paused at position 256 throughout correction validation. Its database/settings were read for an isolated seed and were not overwritten.

Correction tests used separately installed `com.lochanpthakke.empee3.perf` and `.perf.test`, seeded with a copy of the library/preferences. Clone-only scan settings allowed MediaStore scanning, so its album counts may differ from the real app. Metadata API credentials were excluded from the clone seed. Test installations, temporary phone files and the diagnostic log property were cleaned up after validation. The corrected base-package release APK was subsequently installed with `adb install -r` at the user's explicit request, preserving existing app data. The earlier paused-session observation applies to isolated validation before this installation.

## Files and modules

Affected modules: `app`, `core:designsystem`, `core:datastore`, `core:datastore-proto`, `feature:library`, `feature:settings`.

- `app/src/androidTest/java/com/lochanpthakke/empee3/GlassMenuInteractionTest.kt`
- `app/src/main/java/com/lochanpthakke/empee3/MainScaffold.kt`
- `core/datastore-proto/src/main/proto/playback_state.proto`
- `core/datastore/src/main/java/com/lochanpthakke/empee3/core/datastore/repository/PopupGlassPreference.kt`
- `core/datastore/src/main/java/com/lochanpthakke/empee3/core/datastore/repository/ThemeRepository.kt`
- `core/datastore/src/main/java/com/lochanpthakke/empee3/core/datastore/repository/ThemeRepositoryImpl.kt`
- `core/datastore/src/test/java/com/lochanpthakke/empee3/core/datastore/PopupGlassPreferenceTest.kt`
- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/component/GlassAlertDialog.kt`
- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/component/GlassMenu.kt`
- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/component/GlassModalBottomSheet.kt`
- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/component/LiquidMenuSurface.kt`
- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/component/PopupGlassPreview.kt`
- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/component/PopupMorph.kt`
- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/theme/EmPee3Theme.kt`
- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/theme/Glass.kt`
- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/theme/GlassBackdrop.kt`
- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/theme/GlassEnvironmentOptics.kt`
- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/theme/PopupGlass.kt`
- `core/designsystem/src/test/java/com/lochanpthakke/empee3/core/designsystem/component/GlassMenuTest.kt`
- `core/designsystem/src/test/java/com/lochanpthakke/empee3/core/designsystem/component/LiquidMenuMaterialTest.kt`
- `core/designsystem/src/test/java/com/lochanpthakke/empee3/core/designsystem/component/PopupMorphTest.kt`
- `docs/liquid-glass-overflow-menus.md`
- `feature/library/src/main/java/com/lochanpthakke/empee3/feature/library/AnimatedArtworkScreen.kt`
- `feature/library/src/main/java/com/lochanpthakke/empee3/feature/library/ArtworkManagerScreen.kt`
- `feature/library/src/main/java/com/lochanpthakke/empee3/feature/library/MetadataEditorScreen.kt`
- `feature/library/src/main/java/com/lochanpthakke/empee3/feature/library/OnlineLyricsUi.kt`
- `feature/library/src/main/java/com/lochanpthakke/empee3/feature/library/QueueSheet.kt`
- `feature/library/src/main/java/com/lochanpthakke/empee3/feature/library/components/LiveCoverControl.kt`
- `feature/library/src/main/java/com/lochanpthakke/empee3/feature/library/transition/TransitionOptionSheets.kt`
- `feature/settings/src/main/java/com/lochanpthakke/empee3/feature/settings/sound/SoundSettingsScreen.kt`
- `feature/settings/src/main/java/com/lochanpthakke/empee3/feature/settings/theme/PopupGlassControls.kt`
- `feature/settings/src/main/java/com/lochanpthakke/empee3/feature/settings/theme/ThemeSettingsScreen.kt`
- `feature/settings/src/main/java/com/lochanpthakke/empee3/feature/settings/theme/ThemeViewModel.kt`

- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/component/AnchoredMenuLayout.kt`

- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/component/GlassMenuAnchorState.kt`

- `core/designsystem/src/main/java/com/lochanpthakke/empee3/core/designsystem/theme/CachedPopupOptics.kt`

## Release artifact

ARM64-only optimized release APK: **37,215,203 bytes**, package `com.lochanpthakke.empee3`, versionCode 1 / versionName 1.0. SHA-256 `f07d71470844178c1f8e8e4702a9ec6f836cf3c4e119e084736ad98217ac68f7`. `apksigner verify --print-certs` passes with the existing certificate SHA-256 `5bb0fc457b96d9fb6cbc606009de44d633bee95b58fec2dc633a7a4eb54a9ce5` (existing Android Debug certificate used by this project's release configuration). APK, screenshot and normal-speed video accompany this report.

## Transition panels, glass strength and queue correction — 2026-10-31

- Volume, EQ, Filter and Duration now use content-sized `GlassFloatingPanel` windows with a bounded vertical scroll area, native outside/Back dismissal and explicit Close actions. The old option pager and bottom-sheet drag container are removed from these controls: horizontal sliders no longer compete with paging, and tall lists can scroll. Radio-option semantics reflect the actual selected state. Existing edit, customization, undo and duration callbacks are retained; unavailable audio-engine stages remain unavailable.
- Transition Studio provides a local screen-coordinate backdrop from its own window. Its panels sample the editor rather than the album behind the Studio host. These panels reuse the existing neutral popup material; contextual popup morphs are unchanged.
- Shared strength now maps 0/50/100% to 4/11/32 dp diffusion, dark fill 4/18/60%, and refraction 10/8.5/4 dp (token rounding applies). Luminance compression/lift also changes across the range. The light profile uses neutral white fill 6/20/62%. Default remains 50%. The preview and native popups share this configuration. Drag changes update the shared state immediately, cancel superseded preference writes, and persist the latest value through the same optional field 36. Sidebar frostiness remains independent.
- Queue/History uses `QueueGlossSheet`: an opaque dark backing, short restrained specular gradient, fine edge and soft shadow. It does not register a glass consumer or apply backdrop blur/refraction. Queue rows, tabs, playback, reordering, removal/undo and playlist callbacks are retained.

Validation: **280 targeted JVM tests passed** (design system 54, DataStore 31, library 195). ARM64 debug app and Android interaction-test APK assembly passed. Scoped production Kotlin formatting checks passed. Scoped Detekt passed for the design-system, library and Settings changes; DataStore retains four pre-existing magic-number warnings on unchanged lines. Native tests for panel slider drags, scrolling, nested menus, dismissal/reopening and live material-strength rendering are compiled but **not executed**. Device acceptance, actual Studio option selection, queue gestures/playback and restart persistence remain pending: ADB lists no device, Windows enumerates no Samsung/Android USB device, and restarting ADB did not restore the connection. No phone installation/data changes, commit or push for this correction.

Current correction artifact: optimized ARM64 release, `37,215,203` bytes; package `com.lochanpthakke.empee3`, existing signing certificate. Release assembly, Android release checks and `apksigner verify` passed. SHA-256 `5f350df6522896d0282a7b8e3a49cc484a84f67b056d3ed17fb27f8568ab236d`. No install was performed.

## Shared Glass Strength and navigation correction — device checks deferred

This pass supersedes the earlier opacity direction. The existing optional field 36 and default/reset 50% remain; **0% is Less Glass / more frost, 100% is More Glass / maximum clarity**. No preference field or second slider was added, and Main Sidebar Frostiness retains its independent state and renderer.

| Strength | Blur (dp) | Dark neutral fill | Refraction (dp, before token rounding) |
|---|---:|---:|---:|
| 0% | 32 | 60% | 3 |
| 25% | 20 | 46% | 4.75 |
| 50% | 11 | 32% | 6.5 |
| 75% | 6 | 18% | 8.25 |
| 100% | 4 | 4% | 10 |

Strength also changes the neutral luminance guard, reflection, lens highlight and edge definition. Text/control opacity is unchanged. The preview, contextual windows, floating navigation and Search now use the same cached optical renderer/configuration. Navigation samples the existing screen-content layer in root coordinates; native popups sample their existing screen-coordinate window layer. This avoids recursively sampling navigation itself. Configuration changes rebuild the optical draw cache; backdrop movement updates retained layers, with the existing frozen-backdrop optimization respected. Strength now uses a dynamic composition local so unrelated content need not recompose for each drag value.

The selected navigation capsule no longer mixes artwork/theme accent into its material. Its measured geometry, selection/drag springs and icon arrangement remain. The floating mini-player, navigation and Search use neutral Liquid Glass; queue uses an opaque neutral polished backing without backdrop blur. Their playback, drag, reorder, removal, tab and other callbacks are retained. No direct Transition Studio changes in this pass; the user's existing panel implementation is preserved.

Scoped Detekt and formatting passed on the selected changed source files. Five-position unit assertions and native screenshot checks were prepared for the same preview at 0/25/50/75/100%, plus an already-open native panel. **Tests and phone interaction/visual validation were deferred at the user's request.** No five-position device images have been captured for this pass, so material appearance and S23 performance are not claimed verified. No phone actions, commits or pushes were performed.

Release validation: optimized ARM64 release assembly and Android release checks passed; `apksigner verify` passed with the existing certificate. Final scoped KtLint checks passed. No unit or instrumentation tests were executed in this pass, following the user's testing deferral.

Changed in this pass: shared material/cache and neutral capsule (`PopupGlass.kt`, `CachedPopupOptics.kt`, `LiquidCapsule.kt`); shared gloss (`PolishedSurface.kt`, `QueueGlossSheet.kt`, `MiniPlayer.kt`); navigation/Search (`LibraryNavigationBar.kt`, `LibraryBottomControls.kt`); strength UI/help text (`PopupGlassControls.kt`, `ThemeSettingsScreen.kt`); legacy preference-field comment; future five-position assertions/capture fixtures and these implementation notes. Pre-existing changes, including Transition Studio, remain local.

Artifact: `empee3-shared-glass-strength-release-arm64.apk`, 37,215,235 bytes, package `com.lochanpthakke.empee3`. SHA-256 `d66330888c1df4468b7a5e3caaf2572c60d6e758f0701df1158375794bb6b6ce`. Not installed.

## Floating Now Playing bar — restore glass

The mini-player now uses `Modifier.liquidGlass` with the same screen-content backdrop and Glass Strength configuration as navigation/Search. Its opaque gloss backing is removed, and theme-aware foreground/transparent container colours preserve readability in light and dark themes. Position, sizing, playback callbacks and expansion/press animations are retained. Queue gloss and Transition Studio are unchanged. Scoped formatting/Detekt passed; runtime testing remains deferred as requested.

Mini-player follow-up release: optimized ARM64 assembly and signature verification passed. SHA-256 `c832ce01902d295992a0fa3e0274874c81a440a3b481fc9092cba65d2156c98f`. Installed on the connected S23 with `adb install -r` while playback was paused; existing data retained. No app launch or runtime interaction tests. No commit or push.
