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
# Tier -> (texture prefix, item art, hue). Every tier's belt frames are the tier-1 frames, so the
# tiers differ only in colour. The written textures are not read back: recolouring a recoloured
# image drifts by rounding, so every tier starts from the source art.
TIERS = {
    "belt": ("", "belt", 0.14),
    "improved": ("improved_", "improved", 0.0),
    "express": ("express_", "improved", 0.58),
    "turbo": ("turbo_", "improved", 0.33),
}
FRAMES = ART / "belt/frames"
# The accent stripes are the only saturated pixels; the rubber and frame are grey.
MIN_SATURATION = 0.3
# The loader's body is blue and its trim yellow; only the body takes the tier's colour.
LOADER_BODY_HUES = (0.5, 0.75)
# The body's dominant shade, painted the tier's colour exactly; the other body shades keep their
# saturation and brightness relative to it (#400).
LOADER_BODY = (63, 71, 92)
# The panes share the body's hue at almost no brightness, and keep the plain hue rotation.
LOADER_BODY_MIN_VALUE = 0.2
LOADER_BODY_MIN_SATURATION = 0.2


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


def tier_colour(frame: bytes):
    """The belt's stripe colour, so the loader cannot drift from the belt it feeds."""
    image = Image.open(io.BytesIO(frame)).convert("RGBA")
    counts = {}
    for x in range(image.width):
        for y in range(image.height):
            r, g, b, a = image.getpixel((x, y))
            if a and colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)[1] >= MIN_SATURATION:
                counts[(r, g, b)] = counts.get((r, g, b), 0) + 1
    return max(counts, key=counts.get)


def recolour_loader(source: Path, hue: float, colour) -> bytes:
    image = Image.open(io.BytesIO(recolour(source, hue, LOADER_BODY_HUES))).convert("RGBA")
    original = Image.open(source).convert("RGBA")
    th, ts, tv = colorsys.rgb_to_hsv(*(c / 255 for c in colour))
    _, bs, bv = colorsys.rgb_to_hsv(*(c / 255 for c in LOADER_BODY))
    pixels = image.load()
    for x in range(image.width):
        for y in range(image.height):
            r, g, b, a = original.getpixel((x, y))
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if (a == 255 and LOADER_BODY_HUES[0] <= h <= LOADER_BODY_HUES[1]
                    and s >= LOADER_BODY_MIN_SATURATION and v >= LOADER_BODY_MIN_VALUE):
                shade = colorsys.hsv_to_rgb(th, min(1.0, ts * s / bs), min(1.0, tv * v / bv))
                pixels[x, y] = (*(round(c * 255) for c in shade), a)
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
        frames = sorted(FRAMES.glob("frame_*.png"))
        colour = tier_colour(recolour(frames[0], hue))
        yield TEXTURES / f"block/{prefix}chute.png", recolour_loader(ART / "chute/block.png", hue, colour)
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
