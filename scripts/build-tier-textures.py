# /// script
# dependencies = ["pillow"]
# ///
"""Recolour the belt and loader textures to Factorio's yellow, red, blue and green tiers, from
SimpleBelts' original art in `scripts/belt-art/`. Run with `uv run scripts/build-tier-textures.py`; `--check`
fails on drift."""
import colorsys
import io
import json
import math
import sys
from pathlib import Path

from PIL import Image

TEXTURES = Path(__file__).resolve().parent.parent / "common/src/main/resources/assets/belts/textures"
ART = Path(__file__).resolve().parent / "belt-art"
# Tier -> (texture prefix, source art, hue). The written textures are not read back: recolouring
# a recoloured image drifts by rounding, so every tier starts from upstream's art.
TIERS = {
    "belt": ("", "belt", 0.14),
    "improved": ("improved_", "improved", 0.0),
    "express": ("express_", "improved", 0.58),
    "turbo": ("turbo_", "improved", 0.33),
}
# The accent stripes are the only saturated pixels; the rubber and frame are grey.
MIN_SATURATION = 0.3
# The loader's body is blue and its trim yellow; only the body takes the tier's colour.
LOADER_BODY_HUES = (0.5, 0.75)


def recolour(source: Path, hue: float, only_hues=(0.0, 1.0)) -> bytes:
    image = Image.open(source).convert("RGBA")
    pixels = image.load()
    for x in range(image.width):
        for y in range(image.height):
            r, g, b, a = pixels[x, y]
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if a and s >= MIN_SATURATION and only_hues[0] <= h <= only_hues[1]:
                r, g, b = (round(c * 255) for c in colorsys.hsv_to_rgb(hue, s, v))
                pixels[x, y] = (r, g, b, a)
    out = io.BytesIO()
    image.save(out, format="PNG")
    return out.getvalue()


def splitter_belt(prefix, frames, step):
    """The tier's belt frames as one animated strip, since a block model cannot pick a frame the way
    the belt renderer does. A tier-n belt advances n frames a tick there, so the strip does too."""
    images = [Image.open(io.BytesIO(frame)) for frame in frames]
    strip = Image.new("RGBA", (16, 16 * len(images)))
    for index, image in enumerate(images):
        strip.paste(image, (0, 16 * index))
    out = io.BytesIO()
    strip.save(out, format="PNG")
    yield TEXTURES / f"block/{prefix}splitter_belt.png", out.getvalue()
    order = [(index * step) % len(images) for index in range(len(images) // math.gcd(step, len(images)))]
    meta = {"animation": {"frametime": 1, "frames": order}}
    yield TEXTURES / f"block/{prefix}splitter_belt.png.mcmeta", (json.dumps(meta, indent=2) + "\n").encode()


def outputs():
    for step, (prefix, art, hue) in enumerate(TIERS.values(), start=1):
        yield TEXTURES / f"item/{prefix}belt.png", recolour(ART / art / "item.png", hue)
        yield TEXTURES / f"block/{prefix}chute.png", recolour(ART / "chute/block.png", hue, LOADER_BODY_HUES)
        frames = sorted((ART / art / "frames").glob("frame_*.png"))
        for frame in frames:
            yield TEXTURES / f"block/{prefix}conveyorbelt/{frame.name}", recolour(frame, hue)
        yield from splitter_belt(prefix, [recolour(frame, hue) for frame in frames], step)


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
