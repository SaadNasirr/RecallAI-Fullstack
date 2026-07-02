"""
Process recallai_logo.png: strip white/pink card, brain only on transparent PNG.
Regenerate launcher: plain white background, brain centered and clearly visible.
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image

SRC_DOWNLOADS = Path(r"C:\Users\Dell\Downloads\untitled design.png")
LOGO = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res" / "drawable" / "recallai_logo.png"
RES = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res"
SIZE = 1024
BRAND_SCALE = 0.82
# Flat home-screen icon: white square, brain large but with even padding.
LAUNCHER_FLAT_SCALE = 0.74
# Adaptive foreground safe zone (~66% of canvas) — no clipping in circle/squircle masks.
LAUNCHER_FG_SCALE = 0.62
LAUNCHER_BG = (0xFF, 0xFF, 0xFF)
MIPMAP_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def _saturation(r: int, g: int, b: int) -> int:
    return max(r, g, b) - min(r, g, b)


def is_background(r: int, g: int, b: int, a: int) -> bool:
    """White canvas, pink/lavender card panel, and pale fringe — not brain pixels."""
    if a < 128:
        return True
    if r > 245 and g > 245 and b > 245:
        return True

    sat = _saturation(r, g, b)
    lum = (r + g + b) // 3

    if sat < 22 and lum > 195:
        return True

    if r > 205 and b > 225 and r > g + 6 and sat < 62:
        return True
    if r > 235 and g > 195 and b > 235 and sat < 58:
        return True

    return False


def is_brain_pixel(r: int, g: int, b: int, a: int) -> bool:
    return not is_background(r, g, b, a)


def extract_brain_only(img: Image.Image) -> Image.Image:
    """Brain pixels only — transparent outside, no white or card box."""
    img = img.convert("RGBA")
    w, h = img.size
    px = img.load()

    xs, ys = [], []
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if is_brain_pixel(r, g, b, a):
                xs.append(x)
                ys.append(y)
    if not xs:
        raise RuntimeError("No brain pixels found")

    pad = int(max(max(xs) - min(xs), max(ys) - min(ys)) * 0.02)
    crop = img.crop(
        (
            max(0, min(xs) - pad),
            max(0, min(ys) - pad),
            min(w - 1, max(xs) + pad),
            min(h - 1, max(ys) + pad),
        )
    )
    out = Image.new("RGBA", crop.size, (0, 0, 0, 0))
    cpx = crop.load()
    op = out.load()
    for y in range(crop.height):
        for x in range(crop.width):
            r, g, b, a = cpx[x, y]
            if is_brain_pixel(r, g, b, a):
                op[x, y] = (r, g, b, a)
    return out


def paste_centered(brain: Image.Image, scale_ratio: float, bg: tuple[int, int, int] | None) -> Image.Image:
    target = int(SIZE * scale_ratio)
    scale = target / max(brain.width, brain.height)
    nw = max(1, int(brain.width * scale))
    nh = max(1, int(brain.height * scale))
    resized = brain.resize((nw, nh), Image.Resampling.LANCZOS)

    if bg is None:
        canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    else:
        canvas = Image.new("RGBA", (SIZE, SIZE), bg + (255,))

    x = (SIZE - nw) // 2
    y = (SIZE - nh) // 2
    canvas.paste(resized, (x, y), resized)
    return canvas


def main() -> None:
    source = SRC_DOWNLOADS if SRC_DOWNLOADS.is_file() else LOGO
    if not source.is_file():
        raise FileNotFoundError(f"Logo not found: {source}")

    brain = extract_brain_only(Image.open(source))
    brand = paste_centered(brain, BRAND_SCALE, bg=None)
    fg = paste_centered(brain, LAUNCHER_FG_SCALE, bg=None)
    flat = paste_centered(brain, LAUNCHER_FLAT_SCALE, bg=LAUNCHER_BG)

    drawable = LOGO.parent
    drawable.mkdir(parents=True, exist_ok=True)
    brand.save(LOGO, optimize=True)
    fg.save(drawable / "recallai_launcher_foreground.png", optimize=True)
    flat.save(drawable / "recallai_launcher_full.png", optimize=True)

    for folder, px in MIPMAP_SIZES.items():
        out = RES / folder
        out.mkdir(parents=True, exist_ok=True)
        icon = flat.resize((px, px), Image.Resampling.LANCZOS)
        icon.save(out / "ic_launcher.png", optimize=True)
        icon.save(out / "ic_launcher_round.png", optimize=True)

    a = brand.split()[3].getbbox()
    fa = fg.split()[3].getbbox()
    print(f"processed {source.name}")
    print(f"recallai_logo.png: transparent brain, bbox {a}")
    print(f"launcher foreground scale {LAUNCHER_FG_SCALE}, bbox {fa}")
    print(f"launcher flat scale {LAUNCHER_FLAT_SCALE}, white bg, mipmaps updated")


if __name__ == "__main__":
    main()
