## Remote Libraries

- New Remote Libraries page: connect a Navidrome or Subsonic server (password or API key) to browse and stream its music.
- Add a whole server, or a single artist, album, playlist or your favourites, to the Library. Remote tracks appear in Songs, Albums, Artists, search and favourites next to your device's music, and sync in the background.
- The server's genre and year are kept; missing ones come from a confident Fix Tags match, and lyrics from LRCLIB, so remote music also works with VYBE and Radio.
- Wi-Fi stays awake while a queue with streamed tracks plays, so streams no longer stall with the screen off.
- Google Drive is shown as **Currently Unavailable** in this release.

## Equalizer

- Parametric EQ alongside the Graphic EQ: peak, low/high shelf, low/high pass (12/24/48 dB/oct) and notch filters, 4–8 bands with per-band on/off and reset, precise frequency entry and Q from 0.1 to 10.
- Changes cross-fade smoothly, a safety limiter prevents clipping, and each mode keeps its own presets (with rename).
- Dragging a curve point no longer scrolls the page, and the Equalizer now leads Equalizer Settings.

## Onboarding

- Redesigned onboarding with smooth 60 fps previews of Now Playing, Home, Lyrics and the Queue, and an updated Radio preview.

## Now Playing

- Track changes turn the artwork cards in 3D with smoother, faster skips; buttons, swipes and external skips share the same motion.
- Lighter rendering during track changes: the blurred backdrop is prepared ahead, lyrics are parsed once, and the card no longer restarts database queries on every frame.
- Closing the player from the lyrics returns the page and artwork to the right place.
- Fixed Now Playing control colours.

## Library, Radio and artwork

- Custom song artwork.
- Artist photos from multiple providers, with an initial shown while a photo loads.
- Home shows 5 featured radio stations that rotate every 5 hours, banners rotate every 8 hours, and each station page recommends 5 more stations.
- Radio stations stay on topic: listening history only boosts songs related to the station's seed.
- Covers that fail to load for a moment are retried instead of being treated as missing.
- Library scans list the same track only once (same title, artist, album and length), keeping the copy already in your library. Your files are never changed.
- Faster launch scans when nothing changed.
- Search history is saved.
- Permanent navigation quick-access settings, auto-scroll and speed-scroll improvements.

## Sound Capsule

- Redesigned Sound Capsule screen with a recap editor and song and stats share cards.

## Other fixes

- Swiping erhu away from Recents stops playback.
- Floating UI, album upgrade and sync fixes.
- Database upgrades run automatically; installs that reached an intermediate development schema are repaired.

## Installation

Download `erhu-1.0.21-arm64-v8a.apk` for Android 8.0 and newer. The original application ID and signing certificate are preserved, so it updates 1.0.20 in place and keeps your library and settings.

The unchanged `vybe-embeddinggemma-300m-int4int8.zip` VYBE model pack and its checksum are attached below. Erhu downloads this exact release asset automatically. The model remains separately licensed under Google’s Gemma Terms of Use, with the original terms and notices included. Previous releases remain available for rollback.
