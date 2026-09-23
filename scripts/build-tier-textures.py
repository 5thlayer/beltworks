# /// script
# dependencies = ["pillow"]
# ///
"""Recolour the belt textures to Factorio's yellow, red, blue and green tiers, from SimpleBelts'
original art in `scripts/belt-art/`, and draw the loader's housing and its tier band. Run with
`uv run scripts/build-tier-textures.py`; `--check` fails on drift."""
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
# The loader is a slate housing whose mouth is framed by a band of its tier's colour (#408).
SLATE = (84, 90, 100)
SLATE_EDGE = (56, 60, 68)
SLATE_LIGHT = (112, 118, 128)
MOUTH = (0, 0, 0)


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


def png(image) -> bytes:
    out = io.BytesIO()
    image.save(out, format="PNG")
    return out.getvalue()


def slate() -> bytes:
    image = Image.new("RGBA", (16, 16), (*SLATE, 255))
    for i in range(16):
        for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
            image.putpixel((x, y), (*SLATE_EDGE, 255))
    for x in range(1, 15):
        image.putpixel((x, 1), (*SLATE_LIGHT, 255))
    for x, y in ((2, 3), (13, 3), (2, 13), (13, 13)):
        image.putpixel((x, y), (*SLATE_LIGHT, 255))
    return png(image)


def mouth() -> bytes:
    return png(Image.new("RGBA", (16, 16), (*MOUTH, 255)))


def band(colour) -> bytes:
    h, s, v = colorsys.rgb_to_hsv(*(c / 255 for c in colour))
    image = Image.new("RGBA", (16, 16))
    for y in range(16):
        value = min(1.0, v * 1.2) if y == 0 else v * 0.7 if y == 15 else v
        shade = tuple(round(c * 255) for c in colorsys.hsv_to_rgb(h, s, value))
        for x in range(16):
            image.putpixel((x, y), (*shade, 255))
    return png(image)


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
    yield TEXTURES / "block/loader_slate.png", slate()
    yield TEXTURES / "block/loader_mouth.png", mouth()
    for step, (prefix, art, hue) in enumerate(TIERS.values(), start=1):
        yield TEXTURES / f"item/{prefix}belt.png", recolour(ART / art / "item.png", hue)
        frames = sorted(FRAMES.glob("frame_*.png"))
        colour = tier_colour(recolour(frames[0], hue))
        yield TEXTURES / f"block/{prefix}chute.png", band(colour)
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
