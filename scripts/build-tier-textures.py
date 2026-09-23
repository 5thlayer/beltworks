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


def corner(frame: bytes, from_left: bool) -> Image.Image:
    """A quarter turn of the straight belt about the corner's inner vertex, exit north and entry west
    (from the left) or east. Radius keeps the lateral position and angle the distance along, so
    each edge meets the straight tile beside it pixel for pixel (#410). The model's slices stop
just past the arc, so what lies outside it repeats the edge stripe."""
    # The straight tile's top face is the frame turned 180 degrees.
    straight = Image.open(io.BytesIO(frame)).convert("RGBA").rotate(180)
    image = Image.new("RGBA", (16, 16))
    for x in range(16):
        for z in range(16):
            dx = x + 0.5 if from_left else 16 - (x + 0.5)
            r = min(15.99, math.hypot(dx, z + 0.5))
            along = 16 * math.atan2(z + 0.5, dx) / (math.pi / 2)
            lateral = r if from_left else 16 - r
            image.putpixel((x, z), straight.getpixel((min(15, int(lateral)), min(15, int(along)))))
    return image


def corner_strips(prefix, frames, step):
    order = [(index * step) % len(frames) for index in range(len(frames) // math.gcd(step, len(frames)))]
    meta = (json.dumps({"animation": {"frametime": 1, "frames": order}}, indent=2) + "\n").encode()
    for side, from_left in (("left", True), ("right", False)):
        strip = Image.new("RGBA", (16, 16 * len(frames)))
        for index, frame in enumerate(frames):
            strip.paste(corner(frame, from_left), (0, 16 * index))
        yield TEXTURES / f"block/{prefix}belt_corner_{side}.png", png(strip)
        yield TEXTURES / f"block/{prefix}belt_corner_{side}.png.mcmeta", meta


MODELS = TEXTURES.parent / "models/block"
SLICE = 1


def corner_model(prefix, from_left: bool) -> bytes:
    """The corner as 1px slices, each as deep as the arc at its middle, so the outer wall steps
    round the curve instead of standing square (#410)."""
    def wall(u0, u1):
        # u is read off the face's own position, so the side art's legs run on round the curve
        # instead of every slice repeating its first column.
        return {"uv": [u0, 4, u1, 16], "texture": "#side"}

    def depth(start):
        middle = start + SLICE / 2
        return round(math.sqrt(256 - middle * middle)) if start < 16 else 0

    outward = "east" if from_left else "west"
    def culled(faces, box):
        # A face on the block's boundary names it, so the preview, which draws unculled faces
        # regardless, does not z-fight the ground or the next tile there (#410).
        (x0, y0, z0), (x1, y1, z1) = box
        bounds = {"down": y0 == 0, "north": z0 == 0, "south": z1 == 16, "west": x0 == 0, "east": x1 == 16}
        for side, face in faces.items():
            if bounds.get(side):
                face["cullface"] = side
        return faces

    elements = []
    for start in range(0, 16, SLICE):
        x0, x1 = (start, start + SLICE) if from_left else (16 - start - SLICE, 16 - start)
        faces = {
            "up": {"uv": [x0, 0, x1, depth(start)], "texture": "#belt"},
            "down": {"uv": [x0, 0, x1, depth(start)], "texture": "#frame"},
            "north": wall(16 - x1, 16 - x0),
            "south": wall(x0, x1),
        }
        if start == 0:
            faces["west" if from_left else "east"] = wall(0, 16)
        box = ([x0, 0, 0], [x1, 6, depth(start)])
        elements.append({"from": box[0], "to": box[1], "faces": culled(faces, box)})
        # The step's riser is its own flat element, since a whole side face would run on inside
        # the next slice, where the translucent placement preview shows it.
        rise = depth(start + SLICE)
        if rise < depth(start):
            edge = x1 if from_left else x0
            box = ([edge, 0, rise], [edge, 6, depth(start)])
            elements.append({"from": box[0], "to": box[1], "faces": culled({outward: wall(16 - depth(start), 16 - rise) if from_left else wall(rise, depth(start))}, box)})
    model = {
        "parent": "minecraft:block/block",
        "textures": {
            "belt": f"belts:block/{prefix}belt_corner_{'left' if from_left else 'right'}",
            "frame": "belts:block/conveyor_support",
            "particle": f"belts:block/{prefix}splitter_belt",
            "side": "belts:block/belt_tile_side",
        },
        "elements": elements,
    }
    return (json.dumps(model, indent=2) + "\n").encode()


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
        yield from corner_strips(prefix, [recolour(frame, hue) for frame in frames], step)
        for side in ("left", "right"):
            yield MODELS / f"{prefix}belt_tile_corner_{side}.json", corner_model(prefix, side == "left")


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
