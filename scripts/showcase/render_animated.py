import os, sys, subprocess, shutil
from PIL import Image

EDGE_PATH = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
FFMPEG_PATH = r"C:\Users\kshit\AppData\Local\Programs\Python\Python312\Lib\site-packages\imageio_ffmpeg\binaries\ffmpeg-win-x86_64-v7.1.exe"
ROOT_DIR = r"D:\Personal Projects\empee3-localization"
ARTIFACT_DIR = r"C:\Users\kshit\.gemini\antigravity\brain\a46fd51e-42dd-489f-88a1-16aee62a7a44"
SCRATCH_DIR = os.path.join(ARTIFACT_DIR, "scratch")
OUT_DIR = os.path.join(ROOT_DIR, "images", "showcase")
OUT_LIGHT_DIR = os.path.join(OUT_DIR, "light")

themes = {
    'dark': {'text': '#f0f6fc', 'muted': '#9198a1', 'faint': '#656c76', 'rule': '#3d444d', 'frame': '#151b23', 'edge': '#3d444d'},
    'light': {'text': '#1f2328', 'muted': '#59636e', 'faint': '#818b98', 'rule': '#d1d9e0', 'frame': '#e6eaef', 'edge': '#c8d1da'},
}

def get_css(t):
    return f"""
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap');
* {{ margin: 0; box-sizing: border-box; }}
html, body {{ background: transparent; color: {t['text']}; font-family: 'Inter', -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif; }}
.kicker {{ font-size: 20px; letter-spacing: 0.22em; text-transform: uppercase; color: {t['muted']}; font-weight: 500; }}
.rule {{ height: 1px; background: {t['rule']}; }}
.foot {{ font-size: 16px; letter-spacing: 0.22em; text-transform: uppercase; color: {t['faint']}; font-weight: 500; }}
.phone {{ border-radius: 40px; padding: 10px; background: {t['frame']}; border: 2px solid {t['edge']}; }}
.sub {{ color: {t['muted']}; }}
"""

# Clips for animated artwork
# [filename, label]
clips = [
    (os.path.join(ARTIFACT_DIR, 'live_art_album.mp4'), 'Album'),
    (os.path.join(ARTIFACT_DIR, 'live_art_nowplaying.mp4'), 'Now Playing'),
    (os.path.join(ARTIFACT_DIR, 'synced_lyrics.mp4'), 'Synced lyrics'),
]

def render_animated_card_html(t):
    cols_html = ""
    for i, (_, label) in enumerate(clips):
        left = 72 + i * 508
        cols_html += f"""<div class="col" style="left:{left}px">
  <div class="phone"><div class="screen" id="screen-{i}"></div></div>
  <div class="label">{label}</div>
</div>\n"""
    
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>{get_css(t)}
body {{ width: 1600px; height: 1400px; position: relative; overflow: hidden; }}
.head {{ position: absolute; left: 72px; top: 72px; }}
.head h1 {{ font-weight: 600; font-size: 64px; letter-spacing: -0.02em; margin-top: 24px; line-height: 1.1; }}
.head p {{ font-size: 26px; margin-top: 18px; }}
.col {{ position: absolute; top: 330px; width: 440px; }}
.screen {{ width: 416px; height: 849px; }}
.label {{ margin-top: 28px; font-size: 16px; letter-spacing: 0.22em; text-transform: uppercase; color: {t['faint']}; font-weight: 500; }}
.rule {{ position: absolute; left: 72px; right: 72px; top: 1300px; }}
.foot {{ position: absolute; left: 72px; top: 1330px; }}
</style></head><body>
<div class="head">
  <div class="kicker">Animated artwork</div>
  <h1>Covers that move.</h1>
  <p class="sub">Album pages, Now Playing and lyrics views. Motion that brings your collection to life.</p>
</div>
{cols_html}
<div class="rule"></div>
<div class="foot">erhu &nbsp;/&nbsp; Galaxy S23 captures</div>
</body></html>"""

# Generate mask image: 416x849 rounded rectangle with border-radius: 30px
mask_html = """<!doctype html><html><head><meta charset="utf-8"><style>
body { margin: 0; background: transparent; overflow: hidden; }
.m { width: 416px; height: 849px; border-radius: 30px; background: #fff; }
</style></head><body><div class="m"></div></body></html>"""

mask_html_path = os.path.join(SCRATCH_DIR, "mask.html")
mask_png_path = os.path.join(SCRATCH_DIR, "mask.png")
with open(mask_html_path, "w", encoding="utf-8") as f:
    f.write(mask_html)

subprocess.run([
    EDGE_PATH, "--headless", "--default-background-color=00000000",
    f"--screenshot={mask_png_path}", "--window-size=416,849",
    f"file:///{mask_html_path.replace(os.sep, '/')}"
], capture_output=True)

# Positions of screens inside the 1600x1400 canvas:
# Each col is at top: 330px, width: 440px
# Inside col, .phone has padding: 10px, border: 2px -> screen is at (left + 12, 330 + 12) = (left + 12, 342)
slots = [
    (72 + 0 * 508 + 12, 342),
    (72 + 1 * 508 + 12, 342),
    (72 + 2 * 508 + 12, 342),
]

for theme_name, t in themes.items():
    base_html_path = os.path.join(SCRATCH_DIR, f"animated_base_{theme_name}.html")
    base_png_path = os.path.join(SCRATCH_DIR, f"animated_base_{theme_name}.png")
    with open(base_html_path, "w", encoding="utf-8") as f:
        f.write(render_animated_card_html(t))
    
    subprocess.run([
        EDGE_PATH, "--headless", "--default-background-color=00000000",
        f"--screenshot={base_png_path}", "--window-size=1600,1400",
        f"file:///{base_html_path.replace(os.sep, '/')}"
    ], capture_output=True)
    
    target_webp = os.path.join(OUT_DIR if theme_name == 'dark' else OUT_LIGHT_DIR, 'animated-artwork.webp')
    target_gif = os.path.join(OUT_DIR if theme_name == 'dark' else OUT_LIGHT_DIR, 'animated-artwork.gif')
    
    # Filter graph:
    # Crop status bar (0..90) and nav bar (2295..2340): crop=1080:2205:0:90
    # Then fps=15, scale to 416:849, format=rgba
    # Mask [1] alphaextract, split=3
    # Overlay onto base [0]
    # Scale final to 1200:-1
    graph = [
        "[1]alphaextract,split=3[m0][m1][m2]",
        f"[2]crop=1080:2205:0:90,fps=15,scale=416:849,format=rgba[c0]",
        f"[c0][m0]alphamerge[r0]",
        f"[3]crop=1080:2205:0:90,fps=15,scale=416:849,format=rgba[c1]",
        f"[c1][m1]alphamerge[r1]",
        f"[4]crop=1080:2205:0:90,fps=15,scale=416:849,format=rgba[c2]",
        f"[c2][m2]alphamerge[r2]",
        f"[0][r0]overlay={slots[0][0]}:{slots[0][1]}[b0]",
        f"[b0][r1]overlay={slots[1][0]}:{slots[1][1]}[b1]",
        f"[b1][r2]overlay={slots[2][0]}:{slots[2][1]},scale=1200:-1:flags=lanczos,format=yuva420p"
    ]
    
    cmd_webp = [
        FFMPEG_PATH, "-v", "error", "-y",
        "-loop", "1", "-i", base_png_path,
        "-loop", "1", "-i", mask_png_path,
        "-stream_loop", "-1", "-i", clips[0][0],
        "-stream_loop", "-1", "-i", clips[1][0],
        "-stream_loop", "-1", "-i", clips[2][0],
        "-filter_complex", ";".join(graph),
        "-t", "5.5",
        "-c:v", "libwebp_anim", "-quality", "75", "-loop", "0",
        target_webp
    ]
    print(f"Building {target_webp}...")
    subprocess.run(cmd_webp, check=True)
    print(f"Built {target_webp}: {os.path.getsize(target_webp)} bytes")

# Now build animated synced lyrics card: lyrics.webp
# Uses synced_lyrics_track.mp4 (Kanye West track) inside the 1200x2200 card frame!
print("Building animated synced lyrics preview (lyrics.webp)...")
for theme_name, t in themes.items():
    # Base card with empty screen for lyrics
    target_lyrics_webp = os.path.join(OUT_DIR if theme_name == 'dark' else OUT_LIGHT_DIR, 'lyrics.webp')
    target_lyrics_gif = os.path.join(OUT_DIR if theme_name == 'dark' else OUT_LIGHT_DIR, 'lyrics.gif')
    
    card_html = f"""<!doctype html><html><head><meta charset="utf-8"><style>{get_css(t)}
body {{ width: 1200px; height: 2200px; position: relative; overflow: hidden; }}
.head {{ position: absolute; left: 96px; top: 110px; right: 96px; }}
.head h1 {{ font-weight: 600; font-size: 76px; letter-spacing: -0.02em; margin-top: 30px; line-height: 1.1; }}
.head p {{ font-size: 30px; margin-top: 22px; }}
.phone {{ position: absolute; left: 50%; top: 520px; width: 720px; transform: translateX(-50%); }}
.screen {{ width: 700px; height: 1429px; }}
.rule {{ position: absolute; left: 96px; right: 96px; bottom: 120px; }}
.foot {{ position: absolute; left: 96px; bottom: 62px; }}
</style></head><body>
<div class="head">
  <div class="kicker">The listening experience</div>
  <h1>Sing along.</h1>
  <p class="sub">Synced lyrics, saved for offline listening.</p>
</div>
<div class="phone"><div class="screen"></div></div>
<div class="rule"></div>
<div class="foot">erhu &nbsp;/&nbsp; Android</div>
</body></html>"""
    
    card_html_path = os.path.join(SCRATCH_DIR, f"lyrics_base_{theme_name}.html")
    card_png_path = os.path.join(SCRATCH_DIR, f"lyrics_base_{theme_name}.png")
    with open(card_html_path, "w", encoding="utf-8") as f:
        f.write(card_html)
    
    subprocess.run([
        EDGE_PATH, "--headless", "--default-background-color=00000000",
        f"--screenshot={card_png_path}", "--window-size=1200,2200",
        f"file:///{card_html_path.replace(os.sep, '/')}"
    ], capture_output=True)
    
    # Mask for 700x1429 with 54px border-radius
    lmask_html = """<!doctype html><html><head><meta charset="utf-8"><style>
body { margin: 0; background: transparent; overflow: hidden; }
.m { width: 700px; height: 1429px; border-radius: 54px; background: #fff; }
</style></head><body><div class="m"></div></body></html>"""
    lmask_html_path = os.path.join(SCRATCH_DIR, "lmask.html")
    lmask_png_path = os.path.join(SCRATCH_DIR, "lmask.png")
    with open(lmask_html_path, "w", encoding="utf-8") as f:
        f.write(lmask_html)
    subprocess.run([
        EDGE_PATH, "--headless", "--default-background-color=00000000",
        f"--screenshot={lmask_png_path}", "--window-size=700,1429",
        f"file:///{lmask_html_path.replace(os.sep, '/')}"
    ], capture_output=True)
    
    # Phone left is 50% = 600px - 360px = 240px. Inside phone (padding 10px + border 2px) screen is at x = 252, y = 520 + 12 = 532
    track_mp4 = os.path.join(ARTIFACT_DIR, 'synced_lyrics_track.mp4')
    lgraph = [
        "[1]alphaextract[m0]",
        f"[2]crop=1080:2205:0:90,fps=15,scale=700:1429,format=rgba[c0]",
        f"[c0][m0]alphamerge[r0]",
        f"[0][r0]overlay=252:532,format=yuva420p"
    ]
    cmd_lwebp = [
        FFMPEG_PATH, "-v", "error", "-y",
        "-loop", "1", "-i", card_png_path,
        "-loop", "1", "-i", lmask_png_path,
        "-stream_loop", "-1", "-i", track_mp4,
        "-filter_complex", ";".join(lgraph),
        "-t", "5.5",
        "-c:v", "libwebp_anim", "-quality", "75", "-loop", "0",
        target_lyrics_webp
    ]
    print(f"Building {target_lyrics_webp}...")
    subprocess.run(cmd_lwebp, check=True)
    print(f"Built {target_lyrics_webp}: {os.path.getsize(target_lyrics_webp)} bytes")

print("All animated assets built successfully!")
