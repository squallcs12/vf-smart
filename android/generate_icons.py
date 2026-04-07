"""Generate Android launcher icons from ic_vf3_top.png."""
from PIL import Image, ImageOps
import os

SRC = r"app\src\main\res\drawable\ic_vf3_top.png"

# (folder, size_px)
ICONS = [
    (r"app\src\main\res\mipmap-mdpi",    48),
    (r"app\src\main\res\mipmap-hdpi",    72),
    (r"app\src\main\res\mipmap-xhdpi",   96),
    (r"app\src\main\res\mipmap-xxhdpi",  144),
    (r"app\src\main\res\mipmap-xxxhdpi", 192),
]

BG_COLOR = (10, 10, 10, 255)  # OdoBg #0A0A0A

src = Image.open(SRC).convert("RGBA")

for folder, size in ICONS:
    # Crop-to-fill: scale and center-crop source to exact square
    car = src.copy()
    # First trim transparent/white margins
    bbox = car.getbbox()
    if bbox:
        car = car.crop(bbox)
    # Crop-to-fill the square
    canvas = ImageOps.fit(car, (size, size), method=Image.LANCZOS)
    # Composite over dark background
    bg = Image.new("RGBA", (size, size), BG_COLOR)
    bg.paste(canvas, (0, 0), canvas)
    canvas = bg

    for name in ("ic_launcher.png", "ic_launcher_round.png"):
        out = os.path.join(folder, name)
        canvas.save(out, "PNG")
        print(f"Wrote {out}")

print("Done.")