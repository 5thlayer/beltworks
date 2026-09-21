# /// script
# dependencies = ["pillow"]
# ///
"""Derive the express and turbo belt textures from the improved belt's, recoloured to Factorio's
blue and green. Run with `uv run scripts/build-tier-textures.py`; `--check` fails on drift."""
import colorsys
import io
import sys
from pathlib import Path

from PIL import Image

TEXTURES = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/belts/textures"
TIERS = {"express": 0.58, "turbo": 0.33}
# The accent stripes are the only saturated pixels; the rubber and frame are grey.
MIN_SATURATION = 0.3


def recolour(source: Path, hue: float) -> bytes:
    image = Image.open(source).convert("RGBA")
    pixels = image.load()
    for x in range(image.width):
        for y in range(image.height):
            r, g, b, a = pixels[x, y]
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if a and s >= MIN_SATURATION:
                r, g, b = (round(c * 255) for c in colorsys.hsv_to_rgb(hue, s, v))
                pixels[x, y] = (r, g, b, a)
    out = io.BytesIO()
    image.save(out, format="PNG")
    return out.getvalue()


def outputs():
    for tier, hue in TIERS.items():
        yield TEXTURES / f"item/{tier}_belt.png", recolour(TEXTURES / "item/improved_belt.png", hue)
        for frame in sorted((TEXTURES / "block/improved_conveyorbelt").glob("frame_*.png")):
            yield (TEXTURES / f"block/{tier}_conveyorbelt/{frame.name}", recolour(frame, hue))


def main():
    check = "--check" in sys.argv
    stale = []
    for path, data in outputs():
        if check:
            if not path.exists() or path.read_bytes() != data:
                stale.append(path)
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
    for path in stale:
        print(f"stale: {path.relative_to(TEXTURES)}")
    return 1 if stale else 0


if __name__ == "__main__":
    sys.exit(main())
