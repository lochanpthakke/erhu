# Showcase assets

`showcase/` contains the hero, eleven screen presentations and the widget composition. `branding/erhu-wordmark.png` is a typographic rendering of the existing application name; it does not replace the launcher icon. `readme-preview.png` is a rendered desktop preview of the root README.

Home, discovery shelves, Now Playing, lyrics, Library, Albums, equalizer, VYBE results, radio and album controls were captured from the installed erhu 1.0.2 (version code 3) on a Galaxy S23 on 2026-11-02. Music, Glass and Vinyl widgets were captured from the installed release in One UI on the same date. No placeholder widget previews are used.

The app captures are 1080 x 2340 pixels. Their system status and navigation bars are cropped consistently, and the remaining 1080 x 2205 app images are scaled proportionally inside neutral frames. Widget surfaces are cropped from the launcher captures with their rendered backgrounds intact. No UI text, artwork, controls or settings have been replaced or reconstructed.

Album art and lyrics shown are from the device library. The equalizer is shown in its actual flat configuration. VYBE shows the real results of a `late night` search over the 208-song library. The current track remained paused at 1:10 with its 21-item queue intact during capture. Transition Studio was not changed.

All application functionality, identifiers, versions and signing configuration are unchanged. Existing source images in `docs/images/` are retained.

## Library tools and Transition Studio

`showcase/library-builder.png`, `artwork-fetcher.png`, `auto-fix-tags.png`, `transition-studio.png` and `transition-volume.png` use new captures from the installed erhu 1.0.2 build on the Galaxy S23 on 2026-11-03. Their unmodified 1080 × 2340 originals are retained under `docs/images/` with the same filenames. The same crop, proportions, neutral frame and typography as the earlier showcase are used.

The library-tool queues are shown in their actual empty state. No scans, repairs, artwork applications or file moves were executed to populate the screenshots. The Transition Studio overview shows Flower Boy with its existing No crossfade default. The floating Volume panel was opened in an unsaved pair editor and closed without saving. Playback remained paused on SOS by SZA. These are genuine app captures, not placeholders or reconstructed interfaces.

The onboarding previews use compressed, proportionally scaled crops of these same captures. The separate Transition Studio introduction explains the existing controls without modifying their implementation.

## Temporary app icon

`branding/erhu-cube.png` is the user's supplied rounded cube with the outer black backdrop removed using the built-in image tool. The dark tile and ivory instrument remain. It is used for the Android launcher icon and onboarding branding; the name, application ID and signing configuration are unchanged. Density exports and a monochrome emblem support Android launcher masks and themed icons.

Cutout prompt: “Isolate the rounded cube/tile; remove only the surrounding black background to transparency; preserve the dark tile, ivory instrument and proportions; no redesign, text or extra objects.”
