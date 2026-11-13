import os, sys, subprocess, shutil, json
from PIL import Image

EDGE_PATH = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
FFMPEG_PATH = r"C:\Users\kshit\AppData\Local\Programs\Python\Python312\Lib\site-packages\imageio_ffmpeg\binaries\ffmpeg-win-x86_64-v7.1.exe"
ROOT_DIR = r"D:\Personal Projects\empee3-localization"
ARTIFACT_DIR = r"C:\Users\kshit\.gemini\antigravity\brain\a46fd51e-42dd-489f-88a1-16aee62a7a44"
SCRATCH_DIR = os.path.join(ARTIFACT_DIR, "scratch")
RAW_DIR = os.path.join(SCRATCH_DIR, "raw_cropped")
OUT_DIR = os.path.join(ROOT_DIR, "images", "showcase")
OUT_LIGHT_DIR = os.path.join(OUT_DIR, "light")

os.makedirs(RAW_DIR, exist_ok=True)
os.makedirs(OUT_DIR, exist_ok=True)
os.makedirs(OUT_LIGHT_DIR, exist_ok=True)

# 1. Prepare cropped screenshots (1080 x 2205)
crops = {
    'home': os.path.join(ARTIFACT_DIR, 'raw_home.png'),
    'radio': os.path.join(ARTIFACT_DIR, 'raw_radio.png'),
    'vybe': os.path.join(ARTIFACT_DIR, 'raw_vybe.png'),
    'now-playing': os.path.join(ARTIFACT_DIR, 'raw_np_kanye.png'),
    'lyrics': os.path.join(ARTIFACT_DIR, 'screen_lyrics2.png'),
    'queue': os.path.join(ARTIFACT_DIR, 'raw_queue.png'),
    'library': os.path.join(ARTIFACT_DIR, 'raw_library.png'),
    'albums': os.path.join(ARTIFACT_DIR, 'raw_albums.png'),
    'transition-editor': os.path.join(ARTIFACT_DIR, 'raw_transition_editor.png'),
    'artwork-fetcher': os.path.join(ROOT_DIR, 'docs', 'images', 'artwork-fetcher.png'),
    'library-builder': os.path.join(ROOT_DIR, 'docs', 'images', 'library-builder.png'),
    'auto-fix-tags': os.path.join(ROOT_DIR, 'docs', 'images', 'auto-fix-tags.png'),
    'transition-studio': os.path.join(ROOT_DIR, 'docs', 'images', 'transition-studio.png'),
}

for name, src_path in crops.items():
    if os.path.exists(src_path):
        im = Image.open(src_path)
        if im.size == (1080, 2340):
            # Crop 90px top status bar and 45px bottom nav bar
            cropped = im.crop((0, 90, 1080, 2295))
        elif im.size == (1080, 2205):
            cropped = im
        else:
            cropped = im
        dest = os.path.join(RAW_DIR, f"{name}.png")
        cropped.save(dest)

# For equalizer, extract from existing showcase
eq_raw = os.path.join(RAW_DIR, 'equalizer.png')
if not os.path.exists(eq_raw):
    old_eq = os.path.join(ROOT_DIR, 'images', 'showcase', 'equalizer.png')
    if os.path.exists(old_eq):
        im = Image.open(old_eq)
        screen = im.crop((192, 374, 1008, 2042))
        screen_res = screen.resize((1080, 2205), Image.Resampling.LANCZOS)
        screen_res.save(eq_raw)

shots = {
    'home': ['Home and discovery', 'Start with your library.', 'Smart playlists, radio stations and recent albums.'],
    'radio': ['Home and discovery', 'Radio from one song.', 'Stations built from the music you already have.'],
    'vybe': ['Home and discovery', 'Search by feeling.', 'VYBE runs on your device. Nothing is uploaded.'],
    'now-playing': ['The listening experience', 'Let the record lead.', 'Artwork, playback controls and lyrics in one view.'],
    'lyrics': ['The listening experience', 'Sing along.', 'Synced lyrics, saved for offline listening.'],
    'queue': ['The listening experience', 'Up next, your way.', 'Reorder, remove and loop what plays next.'],
    'library': ['Your library', 'Everything in one place.', 'Playlists, favourites and artists.'],
    'albums': ['Your library', 'Pick up where you left off.', 'Albums sorted by what you played last.'],
    'artwork-fetcher': ['Library tools', 'Find the right cover.', 'Album covers and artist photos, online or from your files.'],
    'library-builder': ['Library tools', 'Organise your library.', 'Sort audio files into clean artist and album folders.'],
    'auto-fix-tags': ['Library tools', 'Repair your tags.', 'Review uncertain matches, and revert any change.'],
    'equalizer': ['Audio controls', 'Tune the sound.', 'An 8-band equalizer with presets.'],
    'transition-studio': ['Transition Studio', 'Shape every handoff.', 'Album, playlist and track-pair crossfades.'],
    'transition-editor': ['Transition Studio', 'Draw the crossfade.', 'Volume, EQ and filter curves over the waveforms.'],
}

themes = {
    'dark': {'text': '#f0f6fc', 'muted': '#9198a1', 'faint': '#656c76', 'rule': '#3d444d', 'frame': '#151b23', 'edge': '#3d444d'},
    'light': {'text': '#1f2328', 'muted': '#59636e', 'faint': '#818b98', 'rule': '#d1d9e0', 'frame': '#e6eaef', 'edge': '#c8d1da'},
}

def get_css(t):
    return f"""
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap');
* {{ margin: 0; box-sizing: border-box; }}
html, body {{ background: transparent; color: {t['text']}; font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif; }}
.kicker {{ font-size: 22px; letter-spacing: 0.22em; text-transform: uppercase; color: {t['muted']}; font-weight: 500; }}
.rule {{ height: 1px; background: {t['rule']}; }}
.foot {{ font-size: 18px; letter-spacing: 0.22em; text-transform: uppercase; color: {t['faint']}; font-weight: 500; }}
.phone {{ border-radius: 64px; padding: 10px; background: {t['frame']}; border: 2px solid {t['edge']}; }}
.phone img {{ display: block; width: 100%; border-radius: 54px; }}
.sub {{ color: {t['muted']}; }}
"""

def render_page(t, name, shot):
    kicker, title, sub = shot
    img_url = os.path.join(RAW_DIR, f"{name}.png").replace('\\', '/')
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>{get_css(t)}
body {{ width: 1200px; height: 2200px; position: relative; overflow: hidden; }}
.head {{ position: absolute; left: 96px; top: 110px; right: 96px; }}
.head h1 {{ font-weight: 600; font-size: 76px; letter-spacing: -0.02em; margin-top: 30px; line-height: 1.1; }}
.head p {{ font-size: 30px; margin-top: 22px; }}
.phone {{ position: absolute; left: 50%; top: 520px; width: 720px; transform: translateX(-50%); }}
.rule {{ position: absolute; left: 96px; right: 96px; bottom: 120px; }}
.foot {{ position: absolute; left: 96px; bottom: 62px; }}
</style></head><body>
<div class="head"><div class="kicker">{kicker}</div><h1>{title}</h1><p class="sub">{sub}</p></div>
<div class="phone"><img src="file:///{img_url}"></div>
<div class="rule"></div><div class="foot">erhu &nbsp;/&nbsp; Android</div>
</body></html>"""

def render_hero(t):
    home_url = os.path.join(RAW_DIR, "home.png").replace('\\', '/')
    np_url = os.path.join(RAW_DIR, "now-playing.png").replace('\\', '/')
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>{get_css(t)}
body {{ width: 2400px; height: 1500px; position: relative; overflow: hidden; }}
.left {{ position: absolute; left: 120px; top: 120px; width: 760px; }}
.brand {{ font-weight: 600; font-size: 200px; letter-spacing: -0.05em; margin-top: 60px; line-height: 1; }}
h1 {{ font-weight: 600; font-size: 84px; line-height: 1.08; letter-spacing: -0.02em; margin-top: 110px; }}
p {{ font-size: 32px; line-height: 1.5; margin-top: 50px; }}
.rule {{ position: absolute; left: 120px; width: 600px; top: 1130px; }}
.foot {{ position: absolute; left: 120px; top: 1170px; }}
.phone {{ position: absolute; }}
.a {{ left: 1040px; top: 270px; width: 520px; }}
.b {{ left: 1640px; top: 130px; width: 600px; }}
</style></head><body>
<div class="left"><div class="kicker">Android music player</div><div class="brand">erhu</div>
  <h1>Your music.<br>Played locally.</h1>
  <p class="sub">Albums, lyrics and on-device discovery.<br>Built around your own music.</p></div>
<div class="rule"></div><div class="foot">Beta 1.0.19 &nbsp;/&nbsp; Galaxy S23 captures</div>
<div class="phone a"><img src="file:///{home_url}"></div>
<div class="phone b"><img src="file:///{np_url}"></div>
</body></html>"""

print("Rendering static showcase cards...")
for theme_name, t in themes.items():
    target_dir = OUT_DIR if theme_name == 'dark' else OUT_LIGHT_DIR
    
    # Render hero
    hero_html = os.path.join(SCRATCH_DIR, f"hero_{theme_name}.html")
    with open(hero_html, "w", encoding="utf-8") as f:
        f.write(render_hero(t))
    hero_out = os.path.join(target_dir, "hero.png")
    subprocess.run([
        EDGE_PATH, "--headless", "--default-background-color=00000000",
        f"--screenshot={hero_out}", "--window-size=2400,1500",
        f"file:///{hero_html.replace(os.sep, '/')}"
    ], capture_output=True)
    print(f"Rendered {hero_out}")

    # Render each card
    for name, shot in shots.items():
        card_html = os.path.join(SCRATCH_DIR, f"card_{name}_{theme_name}.html")
        with open(card_html, "w", encoding="utf-8") as f:
            f.write(render_page(t, name, shot))
        card_out = os.path.join(target_dir, f"{name}.png")
        subprocess.run([
            EDGE_PATH, "--headless", "--default-background-color=00000000",
            f"--screenshot={card_out}", "--window-size=1200,2200",
            f"file:///{card_html.replace(os.sep, '/')}"
        ], capture_output=True)
        print(f"Rendered {card_out}")

print("All static showcase cards rendered successfully.")
