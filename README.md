<h1><img src="docs/branding/erhu-cube.png" alt="erhu app icon" width="64"> <img src="docs/branding/erhu-wordmark-on-dark.png#gh-dark-mode-only" alt="erhu" width="95"><img src="docs/branding/erhu-wordmark-on-light.png#gh-light-mode-only" alt="erhu" width="95"> <sub>for Android</sub></h1>

An Android music player for your own collection.

<a href="https://github.com/lochanpthakke/erhu/releases"><img src="docs/badges/download-android.svg" alt="Download erhu for Android"></a>
<a href="https://github.com/MeezaUni/Erhu-iOS/releases"><img src="docs/badges/download-ios.svg" alt="Download erhu for iOS"></a>
<a href="https://ko-fi.com/kshitij38758"><img src="docs/badges/support-ko-fi.svg" alt="Support erhu on Ko-fi"></a>

<img src="images/showcase/hero.png#gh-dark-mode-only" alt="erhu Home and Now Playing on a Galaxy S23" width="1200"><img src="images/showcase/light/hero.png#gh-light-mode-only" alt="erhu Home and Now Playing on a Galaxy S23" width="1200">

## Current beta — 1.0.19

The current beta is available as an ARM64 APK for Android 8.0 and newer. It has been tested on a Samsung Galaxy S23 running One UI. Compatibility with other devices is still being tested.

[Download erhu beta](https://github.com/lochanpthakke/erhu/releases)

Install the APK from the release page and grant music access when prompted. erhu plays music stored on your device; radio stations select tracks from your own library.

Existing installations can update in place. The application ID and signing certificate are unchanged, so an update preserves library data, preferences, favourites and playback history.

## Features

- Albums, artists, playlists, favourites, listening history and listening stats.
- Smart playlists and radio stations generated from your library.
- On-device VYBE search using local song metadata.
- Synced lyrics from embedded lyrics, cached results and LRCLIB, with offline caching.
- Queue management, Auto Fix Tags and online or local artwork selection.
- Higher-resolution cover and artist-photo upgrades, checked against your current image before saving.
- Library Builder with reviewed file moves into artist and album folders.
- Transition Studio with global, collection and track-pair crossfades, plus Volume, EQ and Filter controls.
- 8-band equalizer with presets.
- Floating navigation, adjustable glass surfaces and animated Now Playing artwork transitions.
- Three Home screen widget styles with playback controls and resizable layouts.
- Manual update checks and an automatic check for published releases, including the current beta channel.
- Remote announcements with an inbox and optional Android notifications.

The optional Musixmatch synced-lyrics provider requires a licensed API key. It is disabled when no key is configured. See [provider configuration](docs/synced-lyrics-providers.md).

## Screenshots

Captured on a Galaxy S23 from the current build. They show the real interfaces.

### Home and discovery

<p>
  <a href="images/showcase/home.png#gh-dark-mode-only"><img src="images/showcase/home.png#gh-dark-mode-only" alt="erhu Home with a smart playlist and featured radio stations, captured on a Galaxy S23" width="270"></a><a href="images/showcase/light/home.png#gh-light-mode-only"><img src="images/showcase/light/home.png#gh-light-mode-only" alt="erhu Home with a smart playlist and featured radio stations, captured on a Galaxy S23" width="270"></a>
  <a href="images/showcase/radio.png#gh-dark-mode-only"><img src="images/showcase/radio.png#gh-dark-mode-only" alt="An erhu radio station built from Kanye West, captured on a Galaxy S23" width="270"></a><a href="images/showcase/light/radio.png#gh-light-mode-only"><img src="images/showcase/light/radio.png#gh-light-mode-only" alt="An erhu radio station built from Kanye West, captured on a Galaxy S23" width="270"></a>
  <a href="images/showcase/vybe.png#gh-dark-mode-only"><img src="images/showcase/vybe.png#gh-dark-mode-only" alt="erhu VYBE search for late night, with the closest songs in the library, captured on a Galaxy S23" width="270"></a><a href="images/showcase/light/vybe.png#gh-light-mode-only"><img src="images/showcase/light/vybe.png#gh-light-mode-only" alt="erhu VYBE search for late night, with the closest songs in the library, captured on a Galaxy S23" width="270"></a>
</p>

### The listening experience

<p>
  <a href="images/showcase/now-playing.png#gh-dark-mode-only"><img src="images/showcase/now-playing.png#gh-dark-mode-only" alt="erhu Now Playing, captured on a Galaxy S23" width="270"></a><a href="images/showcase/light/now-playing.png#gh-light-mode-only"><img src="images/showcase/light/now-playing.png#gh-light-mode-only" alt="erhu Now Playing, captured on a Galaxy S23" width="270"></a>
  <a href="images/showcase/lyrics.webp#gh-dark-mode-only"><img src="images/showcase/lyrics.webp#gh-dark-mode-only" alt="erhu synced lyrics, captured on a Galaxy S23" width="270"></a><a href="images/showcase/light/lyrics.webp#gh-light-mode-only"><img src="images/showcase/light/lyrics.webp#gh-light-mode-only" alt="erhu synced lyrics, captured on a Galaxy S23" width="270"></a>
  <a href="images/showcase/queue.png#gh-dark-mode-only"><img src="images/showcase/queue.png#gh-dark-mode-only" alt="erhu queue, captured on a Galaxy S23" width="270"></a><a href="images/showcase/light/queue.png#gh-light-mode-only"><img src="images/showcase/light/queue.png#gh-light-mode-only" alt="erhu queue, captured on a Galaxy S23" width="270"></a>
</p>

### Your library

<p>
  <a href="images/showcase/library.png#gh-dark-mode-only"><img src="images/showcase/library.png#gh-dark-mode-only" alt="erhu library with playlists, favourites and artists, captured on a Galaxy S23" width="270"></a><a href="images/showcase/light/library.png#gh-light-mode-only"><img src="images/showcase/light/library.png#gh-light-mode-only" alt="erhu library with playlists, favourites and artists, captured on a Galaxy S23" width="270"></a>
  <a href="images/showcase/albums.png#gh-dark-mode-only"><img src="images/showcase/albums.png#gh-dark-mode-only" alt="erhu albums sorted by recently played, captured on a Galaxy S23" width="270"></a><a href="images/showcase/light/albums.png#gh-light-mode-only"><img src="images/showcase/light/albums.png#gh-light-mode-only" alt="erhu albums sorted by recently played, captured on a Galaxy S23" width="270"></a>
  <a href="images/showcase/glass.png#gh-dark-mode-only"><img src="images/showcase/glass.png#gh-dark-mode-only" alt="erhu glass effects and floating controls, captured on a Galaxy S23" width="270"></a><a href="images/showcase/light/glass.png#gh-light-mode-only"><img src="images/showcase/light/glass.png#gh-light-mode-only" alt="erhu glass effects and floating controls, captured on a Galaxy S23" width="270"></a>
</p>

### Library tools

<p>
  <a href="images/showcase/artwork-fetcher.png#gh-dark-mode-only"><img src="images/showcase/artwork-fetcher.png#gh-dark-mode-only" alt="Artwork Fetcher on a Galaxy S23, showing album and artist-photo checks" width="270"></a><a href="images/showcase/light/artwork-fetcher.png#gh-light-mode-only"><img src="images/showcase/light/artwork-fetcher.png#gh-light-mode-only" alt="Artwork Fetcher on a Galaxy S23, showing album and artist-photo checks" width="270"></a>
  <a href="images/showcase/library-builder.png#gh-dark-mode-only"><img src="images/showcase/library-builder.png#gh-dark-mode-only" alt="Library Builder on a Galaxy S23, organising tracks into folders" width="270"></a><a href="images/showcase/light/library-builder.png#gh-light-mode-only"><img src="images/showcase/light/library-builder.png#gh-light-mode-only" alt="Library Builder on a Galaxy S23, organising tracks into folders" width="270"></a>
  <a href="images/showcase/auto-fix-tags.png#gh-dark-mode-only"><img src="images/showcase/auto-fix-tags.png#gh-dark-mode-only" alt="Auto Fix Tags on a Galaxy S23, listing matched tracks and uncertain matches to review" width="270"></a><a href="images/showcase/light/auto-fix-tags.png#gh-light-mode-only"><img src="images/showcase/light/auto-fix-tags.png#gh-light-mode-only" alt="Auto Fix Tags on a Galaxy S23, listing matched tracks and uncertain matches to review" width="270"></a>
</p>

- **Artwork Fetcher:** find album covers and artist photos, choose a provider and resolution target, inspect candidates and apply your selection. Album menus also accept a local cover image.
- **Library Builder:** scans storage and reorganizes files into clean artist and album folder structures.
- **Auto Fix Tags:** add tracks, albums or artists to a repair queue, select the metadata fields to change, and use automatic matching or manual result selection.

### Audio controls and Transition Studio

<p>
  <a href="images/showcase/equalizer.png#gh-dark-mode-only"><img src="images/showcase/equalizer.png#gh-dark-mode-only" alt="erhu equalizer, captured on a Galaxy S23" width="270"></a><a href="images/showcase/light/equalizer.png#gh-light-mode-only"><img src="images/showcase/light/equalizer.png#gh-light-mode-only" alt="erhu equalizer, captured on a Galaxy S23" width="270"></a>
  <a href="images/showcase/transition-studio.png#gh-dark-mode-only"><img src="images/showcase/transition-studio.png#gh-dark-mode-only" alt="Transition Studio on a Galaxy S23, showing album settings and track pairs" width="270"></a><a href="images/showcase/light/transition-studio.png#gh-light-mode-only"><img src="images/showcase/light/transition-studio.png#gh-light-mode-only" alt="Transition Studio on a Galaxy S23, showing album settings and track pairs" width="270"></a>
  <a href="images/showcase/transition-editor.png#gh-dark-mode-only"><img src="images/showcase/transition-editor.png#gh-dark-mode-only" alt="The transition editor on a Galaxy S23, showing waveforms and editable curves" width="270"></a><a href="images/showcase/light/transition-editor.png#gh-light-mode-only"><img src="images/showcase/light/transition-editor.png#gh-light-mode-only" alt="The transition editor on a Galaxy S23, showing waveforms and editable curves" width="270"></a>
</p>

[Using Transition Studio](docs/transition-studio.md)

The in-app introduction includes these tools and a separate Transition Studio page. Reopen it from **Settings -> Diagnostics & About -> Introduction**.

### Animated artwork

<a href="images/showcase/animated-artwork.webp#gh-dark-mode-only"><img src="images/showcase/animated-artwork.webp#gh-dark-mode-only" alt="Animated album covers on an album page, Now Playing and lyrics, recorded on a Galaxy S23" width="1200"></a><a href="images/showcase/light/animated-artwork.webp#gh-light-mode-only"><img src="images/showcase/light/animated-artwork.webp#gh-light-mode-only" alt="Animated album covers on an album page, Now Playing and lyrics, recorded on a Galaxy S23" width="1200"></a>

Animated covers bring album pages and Now Playing to life with smooth motion graphics.

### Widgets

Music, Glass and Vinyl playback controls, shown with real artwork and track information.

<img src="images/showcase/widgets.png" alt="Working Music and Glass 4 by 2 widgets and the Vinyl 3 by 3 widget in One UI" width="960">

## VYBE setup

VYBE searches your library using descriptions of what you want to hear. It runs on the device and needs a separate EmbeddingGemma model.

Download the [VYBE model pack](https://github.com/lochanpthakke/erhu/releases/download/v1.0.19-beta.33/vybe-embeddinggemma-300m-int4int8.zip), read the included model terms, and extract `embedding_gemma.task`. Open **VYBE** in erhu, tap **Choose model file**, and select the extracted file. Leave the app open while it indexes your library.

The model is about 184 MB before compression. It is supplied under Google's Gemma Terms of Use, separately from erhu's GPL-3.0 licence. See [VYBE setup](docs/vybe-setup.md) for the model source, checksum and terms.

## Build

erhu uses Kotlin, Jetpack Compose and Media3.

Use JDK 21, the Android SDK configured by the project and the checked-in Gradle wrapper.

```sh
./gradlew :app:assembleDebug
```

The [release workflow](.github/workflows/release.yml) runs unit tests and release lint, builds the optimized ARM64 APK, and verifies its package, version and signing certificate. Signing material is supplied through GitHub Actions Secrets.

The source namespace is `com.erhu.erhu`. The installed application ID remains `com.lochanpthakke.empee3` for compatibility with existing installations.

## Feedback

For questions, issues, or suggestions, open an issue on GitHub or use the [feedback form](https://docs.google.com/forms/d/e/1FAIpQLSeRPwGZcDHx4tDDcez811ifS8dL5ktkqauz-I9OzipYdPpiEw/viewform).

## Contributors

- [Kshitij Kekunnaya](https://github.com/Kshitij1425)
- [Lochan Thakke](https://github.com/lochanpthakke)

## License

[GNU General Public License v3.0](LICENSE). Read the [Privacy Policy](docs/privacy-policy.md) for local storage, online services and permissions.
