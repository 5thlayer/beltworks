# /// script
# dependencies = ["pillow"]
# ///
"""Recolour the belt textures to Factorio's yellow, red, blue and green tiers, from Upstream's
original art in `scripts/belt-art/`, draw the family's slate (the loader's housing, the tiles'
sides and undersides) and the loader's tier band, and write the belt tiles' corner and slope models
and their blockstates. Run with
`uv run scripts/build-tier-textures.py`; `--check` fails on drift."""
import colorsys
import io
import json
import math
import sys
from pathlib import Path

from PIL import Image

TEXTURES = Path(__file__).resolve().parent.parent / "src/main/resources/assets/beltworks/textures"
ART = Path(__file__).resolve().parent / "belt-art"
# Tier -> (texture prefix, hue). Every tier's belt frames are the tier-1 frames, so the
# tiers differ only in colour. The written textures are not read back: recolouring a recoloured
# image drifts by rounding, so every tier starts from the source art.
TIERS = {
    "belt": ("", 0.14),
    "improved": ("improved_", 0.0),
    "express": ("express_", 0.58),
    "turbo": ("turbo_", 0.33),
}
FRAMES = ART / "belt/frames"
# The accent stripes are the only saturated pixels; the rubber and frame are grey.
MIN_SATURATION = 0.3
# The loader is a slate housing whose mouth is framed by a band of its tier's colour (#408).
SLATE = (84, 90, 100)
SLATE_EDGE = (56, 60, 68)
SLATE_LIGHT = (112, 118, 128)
MOUTH = (0, 0, 0)
# A tile's side, row by row from the top: models draw rows 2 to 7, one pixel each, from the belt's
# surface down. Rows only, no columns, so a slope's slices, which can only sample the art square to
# the block, line up with its band and with a level tile (#32). Opaque throughout, since a clear
# pixel on a tile's side shows through the tile.
SIDE = (SLATE_EDGE, SLATE_EDGE, SLATE_LIGHT, SLATE, SLATE, SLATE_EDGE, SLATE, SLATE_EDGE)
# A tile's rib: a band 1 px wide down each lateral edge, standing 1 px below the underside between
# them, which rises that pixel so the ribs stand on the block's floor and the surface stays at 6 px
# (#57). Measured upright, so on a slope the ribs keep the level tile's lines.
RIB = 1
# The side art's rows for a level tile's wall above the rib and for the rib.
WALL_ROWS = (4, 16 - 2 * RIB)
RIB_ROWS = (16 - 2 * RIB, 16)
SIDE_ROWS = (WALL_ROWS[0], RIB_ROWS[1])


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


def tile_side() -> bytes:
    image = Image.new("RGBA", (16, len(SIDE)))
    for y, colour in enumerate(SIDE):
        for x in range(16):
            image.putpixel((x, y), (*colour, 255))
    return png(image)


def underside() -> bytes:
    """Plain slate, with no border and no gradient: the tiles' undersides are cut into one-pixel
    slices, where a border or a rivet would read as noise, and a gradient restarts at every tile.
    The ribs, darker, give it depth instead (#57)."""
    return png(Image.new("RGBA", (16, 16), (*SLATE, 255)))


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
# The ribs' spans across the tile, left and right as items travel north.
RIBS = ((0, RIB), (16 - RIB, 16))


def wall(u0, u1, rows):
    """Side art across u0 to u1, over the rows given."""
    return {"uv": [u0, rows[0], u1, rows[1]], "texture": "#side"}


def frame(x0, z0, x1, z1):
    """The underside's slate, read off the face's own position."""
    return {"uv": [x0, z0, x1, z1], "texture": "#frame"}


def rib_bottom(x0, x1):
    """A rib's bottom, in the dark slate of the side art's last row, as its walls are, so the ribs
    read as two dark lines down a flat underside (#57)."""
    return wall(x0, x1, RIB_ROWS)


def element(box, faces):
    """An element whose faces on the block's boundary name it, so the preview, which draws unculled
    faces regardless, does not z-fight the ground or the next tile there (#410)."""
    (x0, y0, z0), (x1, y1, z1) = box
    bounds = {"down": y0 == 0, "north": z0 == 0, "south": z1 == 16, "west": x0 == 0, "east": x1 == 16}
    for side, face in faces.items():
        if bounds.get(side):
            face["cullface"] = side
    return {"from": box[0], "to": box[1], "faces": faces}


def model(textures, elements) -> bytes:
    return (json.dumps({"parent": "minecraft:block/block", "textures": textures, "elements": elements}, indent=2)
            + "\n").encode()


def level_elements(z0, z1, up, ends):
    """A level tile's 6 px from z0 to z1, travelling north: its body over a rib down each side and,
    between them, the underside a pixel up, each face on an element of its own so none runs on
    over another's (#57). {ends} names the ends that are the block's, which take a wall."""
    def closed(faces, x0, x1, rows):
        for end in ends:
            faces[end] = wall(16 - x1, 16 - x0, rows) if end == "north" else wall(x0, x1, rows)
        return faces

    elements = [
        element(([0, RIB, z0], [16, 6, z1]),
                closed({"up": up, "east": wall(z0, z1, WALL_ROWS), "west": wall(16 - z1, 16 - z0, WALL_ROWS)}, 0, 16, WALL_ROWS)),
        element(([RIB, RIB, z0], [16 - RIB, RIB, z1]), {"down": frame(RIB, z0, 16 - RIB, z1)}),
    ]
    for x0, x1 in RIBS:
        faces = {"down": rib_bottom(x0, x1), "east": wall(z0, z1, RIB_ROWS), "west": wall(16 - z1, 16 - z0, RIB_ROWS)}
        elements.append(element(([x0, 0, z0], [x1, RIB, z1]), closed(faces, x0, x1, RIB_ROWS)))
    return elements


# The straight tile's top face: the belt frames turned 180 degrees.
LEVEL_BELT = {"uv": [0, 0, 16, 16], "texture": "#belt", "rotation": 180}


def level_tile_model(prefix) -> bytes:
    """The level tile. Tiers past the first take its elements and change only the belt."""
    belt = f"beltworks:block/{prefix}splitter_belt"
    if prefix:
        return (json.dumps({"parent": "beltworks:block/belt_tile", "textures": {"belt": belt, "particle": belt}},
                           indent=2) + "\n").encode()
    return model({"belt": belt, "frame": "beltworks:block/belt_underside", "particle": belt,
                  "side": "beltworks:block/belt_tile_side"},
                 level_elements(0, 16, LEVEL_BELT, ["south", "north"]))


def splitter_half_model() -> bytes:
    """A splitter half: a level tile, ribs down both its sides, under its tier's divider along the
    splitter's midline. Each tier's splitter names the belt and the divider."""
    divider = {"uv": [0, 0, 16, 2], "texture": "#divider"}
    end = {"uv": [6.5, 0, 9.5, 2], "texture": "#divider"}
    top = {"uv": [0, 6.5, 16, 9.5], "texture": "#divider"}
    return model({"side": "beltworks:block/belt_tile_side", "frame": "beltworks:block/belt_underside"},
                 level_elements(0, 16, LEVEL_BELT, ["south", "north"]) + [
                     {"from": [0, 6, 6.5], "to": [16, 8, 9.5],
                      "faces": {"north": divider, "south": dict(divider), "east": end, "west": dict(end),
                                "up": top, "down": dict(top)}}])


def corner_model(prefix, from_left: bool) -> bytes:
    """The corner as 1px slices, each as deep as the arc at its middle, so the outer wall steps
    round the curve instead of standing square (#410). Its ribs follow the turn's edges (#57): the
    outer one round the arc, stepping with the wall, and the inner one a pixel at the turn's vertex,
    where the inner edge closes to a point. u is read off each face's own position."""
    def reach(start, radius):
        # How far from the exit edge the circle of {radius} about the vertex lies, at the slice's middle.
        middle = start + SLICE / 2
        return round(math.sqrt(max(0, radius * radius - middle * middle))) if start < 16 else 0

    inward, outward = ("west", "east") if from_left else ("east", "west")
    elements = []
    for start in range(0, 16, SLICE):
        x0, x1 = (start, start + SLICE) if from_left else (16 - start - SLICE, 16 - start)
        # How far each edge lies from the exit edge: the outer wall, the outer rib's inner side,
        # and the inner rib's, which only the slice at the vertex has.
        deep, outer_rib, inner_rib = reach(start, 16), reach(start, 16 - RIB), reach(start, RIB)
        faces = {"up": {"uv": [x0, 0, x1, deep], "texture": "#belt"},
                 "north": wall(16 - x1, 16 - x0, WALL_ROWS), "south": wall(x0, x1, WALL_ROWS)}
        if start == 0:
            faces[inward] = wall(0, 16, WALL_ROWS)
        elements.append(element(([x0, RIB, 0], [x1, 6, deep]), faces))
        if outer_rib > inner_rib:
            elements.append(element(([x0, RIB, inner_rib], [x1, RIB, outer_rib]), {"down": frame(x0, inner_rib, x1, outer_rib)}))
        faces = {"down": rib_bottom(x0, x1),
                 "north": wall(16 - x1, 16 - x0, RIB_ROWS), "south": wall(x0, x1, RIB_ROWS)}
        if start == 0:
            faces[inward] = wall(outer_rib, deep, RIB_ROWS)
        elements.append(element(([x0, 0, outer_rib], [x1, RIB, deep]), faces))
        # The outer rib steps out toward the vertex under the raised underside; outward, the wall's
        # riser below covers its step.
        edge = x0 if from_left else x1
        last_rib = reach(start - SLICE, 16 - RIB) if start else outer_rib
        if outer_rib < last_rib:
            elements.append(element(([edge, 0, outer_rib], [edge, RIB, last_rib]), {inward: wall(outer_rib, last_rib, RIB_ROWS)}))
        if inner_rib:
            elements.append(element(([x0, 0, 0], [x1, RIB, inner_rib]), {
                "down": rib_bottom(x0, x1),
                "north": wall(16 - x1, 16 - x0, RIB_ROWS), "south": wall(x0, x1, RIB_ROWS),
                inward: wall(0, inner_rib, RIB_ROWS), outward: wall(0, inner_rib, RIB_ROWS)}))
        # The step's riser is its own flat element, since a whole side face would run on inside
        # the next slice, where the translucent placement preview shows it. It stands the wall's
        # whole height, rib and all, as the outer rib always reaches as far as the next slice.
        rise = reach(start + SLICE, 16)
        if rise < deep:
            edge = x1 if from_left else x0
            elements.append(element(([edge, 0, rise], [edge, 6, deep]),
                                    {outward: wall(16 - deep, 16 - rise, SIDE_ROWS) if from_left else wall(rise, deep, SIDE_ROWS)}))
    return model({"belt": f"beltworks:block/{prefix}belt_corner_{'left' if from_left else 'right'}",
                  "frame": "beltworks:block/belt_underside", "particle": f"beltworks:block/{prefix}splitter_belt",
                  "side": "beltworks:block/belt_tile_side"}, elements)


# A slope's profile in its travel's 16 px, as runs (start, end, height at start, rise per px), level
# at 6 px, one straight 45-degree line through foot, middles and top (PlanetaryFactory #417). Drawn
# travelling north, the way the straight tile is, so its back edge is z = 16.
PITCHES = {
    "foot_up": [(0, 6, 6, 0), (6, 16, 6, 1)],
    "middle_up": [(0, 16, 0, 1)],
    "top_up": [(0, 6, 0, 1), (6, 16, 6, 0)],
    "foot_down": [(0, 10, 16, -1), (10, 16, 6, 0)],
    "middle_down": [(0, 16, 16, -1)],
    "top_down": [(0, 10, 6, 0), (10, 16, 6, -1)],
}
# The band is drawn on the block's side and whatever lies behind it sits a little further in, so
# no two walls z-fight where both are drawn (#417).
RIM_INSET = 0.01
SLICE_INSET = 0.02
# A level tile's depth, which a slope keeps measured upright; half of it measured square to a 45-degree slope.
BAND_DEPTH = 3
# The ribs' spans across a slice, which stands inset from the block's sides.
SLICE_RIBS = ((SLICE_INSET, RIB), (16 - RIB, 16 - SLICE_INSET))


def side_wall(u0, u1, below, height):
    """A wall of side art whose top stands {below} px under the belt's surface, a pixel a row."""
    top = 4 + 2 * below
    return {"uv": [u0, whole(top), u1, whole(min(16, top + 2 * height))], "texture": "#side"}


def whole(n):
    return int(n) if n == int(n) else n


def slope_model(prefix, pitch) -> bytes:
    """A slope as a plane along its surface over a band as deep as a level tile, its underside
    pitched with it and never below the block's floor, filled out by 1px slices where the band's
    square ends stop short, with a thin rim along the plane's edge to close the slices' corners.
    Band and slices carry the level tile's ribs, measured upright, so they run on from it (#57)."""
    runs = PITCHES[pitch]

    def surface(d):
        for start, end, height, rise in runs:
            if start <= d <= end:
                return height + rise * (d - start)
        raise ValueError(d)

    def underside(d, between=False):
        # Where the ribs stand, or the underside between them a pixel up; neither below the floor.
        return max(0, surface(d) - 6 + (RIB if between else 0))

    def belt(start, end):
        # The straight tile's top face turned 180 degrees, cut to this run's share of the travel.
        return {"uv": [0, start, 16, end], "texture": "#belt", "rotation": 180}

    def heights(d):
        # A slice's floor under its ribs and between them, each where its underside is highest, and
        # its top, where the surface is lowest.
        return (math.ceil(max(underside(d), underside(d + 1))), math.ceil(max(underside(d, True), underside(d + 1, True))),
                math.floor(min(surface(d), surface(d + 1))))

    elements = []
    for start, end, height, rise in runs:
        z0, z1 = 16 - end, 16 - start
        if rise == 0:
            ends = (["south"] if start == 0 else []) + (["north"] if end == 16 else [])
            elements += level_elements(z0, z1, belt(start, end), ends)
            # Where the level run meets the slope inside the block, the slice beside it may stand a
            # pixel higher, so its risers close the step under the ribs and between them.
            for joint, beside, facing in ((end, end, "north"), (start, start - 1, "south")):
                if joint in (0, 16):
                    continue
                ribs, floor, _ = heights(beside)
                z = 16 - joint
                if ribs > 0:
                    for x0, x1 in RIBS:
                        elements.append(element(([x0, 0, z], [x1, ribs, z]), {facing: wall(x0, x1, RIB_ROWS)}))
                if floor > RIB:
                    elements.append(element(([RIB, RIB, z], [16 - RIB, floor, z]), {facing: frame(RIB, RIB, 16 - RIB, floor)}))
            continue
        # The band: the level tile's 6 px of depth turned to the slope, its underside pitched too.
        # Its ends are square to the slope, so it stops where a corner would leave the block, and
        # the slices below fill what is left.
        if rise > 0:
            d0, d1 = max(start, start + BAND_DEPTH - height), end - BAND_DEPTH
        else:
            d0, d1 = start + BAND_DEPTH, min(end, start + height - BAND_DEPTH)
        if d1 > d0:
            top, z = surface(d0), 16 - d0
            length = round((d1 - d0) * math.sqrt(2), 4)
            deep = BAND_DEPTH * math.sqrt(2)
            # The rib's pixel upright, measured square to the slope.
            rib = RIB / math.sqrt(2)
            rotation = {"origin": [8, top, z], "axis": "x", "angle": 45 * rise}

            def band(x0, x1, y0, y1, faces):
                elements.append({"from": [x0, round(top - deep + y0, 4), round(z - length, 4)],
                                 "to": [x1, round(top - deep + y1, 4), z], "rotation": rotation, "faces": faces})

            def sides(rows):
                return {side: wall(0, 16, rows) for side in ("east", "west")}

            band(0, 16, rib, deep, sides(WALL_ROWS))
            band(RIB, 16 - RIB, rib, rib, {"down": frame(RIB, 0, 16 - RIB, 16)})
            for x0, x1 in RIBS:
                band(x0, x1, 0, rib, sides(RIB_ROWS) | {"down": rib_bottom(x0, x1)})
        for d in range(start, end):
            ribs, floor, tall = heights(d)
            if tall <= ribs:
                continue
            # Rows counted from the surface over the slice's middle, as the band's are.
            below = (surface(d) + surface(d + 1)) / 2 - tall

            def walls(below, height):
                return {"east": side_wall(15 - d, 16 - d, below, height), "west": side_wall(d, d + 1, below, height),
                        "north": side_wall(0, 16, below, height), "south": side_wall(0, 16, below, height)}

            def column(x0, x1, y0, y1, faces):
                elements.append(element(([x0, y0, 15 - d], [x1, y1, 16 - d]), faces))

            # Where the slope meets the floor its underside lies flat on it, ribs and all.
            if floor == ribs:
                column(SLICE_INSET, 16 - SLICE_INSET, ribs, tall, walls(below, tall - ribs) | {"down": frame(0, 15 - d, 16, 16 - d)})
                continue
            if tall > floor:
                column(SLICE_INSET, 16 - SLICE_INSET, floor, tall, walls(below, tall - floor))
            column(RIB, 16 - RIB, floor, floor, {"down": frame(RIB, 15 - d, 16 - RIB, 16 - d)})
            for (x0, x1), (u0, u1) in zip(SLICE_RIBS, RIBS):
                column(x0, x1, ribs, floor, walls(below + tall - floor, floor - ribs) | {"down": rib_bottom(u0, u1)})
        length = (end - start) * math.sqrt(2)
        angle = 45 if rise > 0 else -45
        origin = [8, height, z1]
        elements.append({
            "from": [0, height, round(z1 - length, 4)], "to": [16, height, z1],
            "rotation": {"origin": origin, "axis": "x", "angle": angle},
            "faces": {"up": belt(start, end)},
        })
        # A step along the plane at each end, so no corner of the rim leaves the block.
        elements.append({
            "from": [RIM_INSET, height - 1, round(z1 - length + 1, 4)], "to": [16 - RIM_INSET, height, z1 - 1],
            "rotation": {"origin": origin, "axis": "x", "angle": angle},
            "faces": {"east": {"uv": [0, 4, 16, 6], "texture": "#side"}, "west": {"uv": [0, 4, 16, 6], "texture": "#side"}},
        })
    return model({"belt": f"beltworks:block/{prefix}splitter_belt", "frame": "beltworks:block/belt_underside",
                  "particle": f"beltworks:block/{prefix}splitter_belt", "side": "beltworks:block/belt_slope_side"},
                 elements)


def wedge_model() -> bytes:
    """The wedge under a middle or top over air (PlanetaryFactory #420): the part of the slope's band
    its own block's floor cuts off, a 6 px triangle at the top of the block below, on its downhill
    edge, its underside continuing the band's, ribs and all (#57). Drawn rising north as the slopes
    are, so the downhill edge is z = 16. A slice's outer walls cull against the blocks beside it."""
    depth = 6
    elements = []
    for k in range(depth):
        z0, z1 = 16 - depth + k, 17 - depth + k
        ribs = 16 - k
        floor = min(16, ribs + RIB)
        if ribs >= 16:
            continue
        # Sampled as a slope's slices under its band are, its rows counted from the slope's surface
        # over the slice's middle, a band's depth above its underside, so the wedge's wall continues
        # the slope's.
        below = ribs - 0.5 + BAND_DEPTH * 2 - 16

        def walls(below, height, outer):
            faces = {"east": side_wall(16 - z1, 16 - z0, below, height), "west": side_wall(z0, z1, below, height)}
            for side in outer:
                faces[side]["cullface"] = side
            return faces

        if floor < 16:
            faces = walls(below, 16 - floor, ("east", "west"))
            if z1 == 16:
                faces["south"] = frame(0, 0, 16, 16 - floor)
            elements.append({"from": [SLICE_INSET, floor, z0], "to": [16 - SLICE_INSET, 16, z1], "faces": faces})
            elements.append({"from": [RIB, floor, z0], "to": [16 - RIB, floor, z1], "faces": {"down": frame(RIB, z0, 16 - RIB, z1)}})
        for (x0, x1), (u0, u1), outer in zip(SLICE_RIBS, RIBS, ("west", "east")):
            faces = walls(below + 16 - floor, floor - ribs, (outer,)) | {"down": rib_bottom(u0, u1)}
            if z1 == 16:
                faces["south"] = wall(u0, u1, RIB_ROWS)
            elements.append({"from": [x0, ribs, z0], "to": [x1, floor, z1], "faces": faces})
    # The pitched underside under the slices' steps: the ribs' along the band's line, and between
    # them a pixel up.
    for x0, x1, lift in ((0, RIB, 0), (RIB, 16 - RIB, RIB), (16 - RIB, 16, 0)):
        bottom = 16 - depth + lift
        length = round((depth - lift) * math.sqrt(2), 4)
        elements.append({
            "from": [x0, bottom, round(16 - length, 4)], "to": [x1, bottom, 16],
            "rotation": {"origin": [8, bottom, 16], "axis": "x", "angle": 45},
            "faces": {"down": frame(x0, 0, x1, depth - lift) if lift else rib_bottom(x0, x1)},
        })
    return model({"side": "beltworks:block/belt_slope_side", "frame": "beltworks:block/belt_underside",
                  "particle": "beltworks:block/belt_underside"}, elements)


BLOCKSTATES = TEXTURES.parent / "blockstates"
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}
SHAPES = {"straight": "", "from_left": "_corner_left", "from_right": "_corner_right"}


def tile_blockstate(prefix) -> bytes:
    """Every facing, shape and pitch. A slope is always straight (#417), so a sloped corner state,
    which the block never derives, draws as its corner."""
    variants = {}
    for facing, turn in FACINGS.items():
        for shape, suffix in SHAPES.items():
            for pitch in ["level", *PITCHES]:
                model = f"beltworks:block/{prefix}belt_tile{suffix}" if shape != "straight" or pitch == "level" \
                    else f"beltworks:block/{prefix}belt_tile_{pitch}"
                variant = {"model": model}
                if turn:
                    variant["y"] = turn
                variants[f"facing={facing},pitch={pitch},shape={shape}"] = variant
    return (json.dumps({"variants": variants}, indent=2) + "\n").encode()


def wedge_blockstate() -> bytes:
    variants = {}
    for facing, turn in FACINGS.items():
        variant = {"model": "beltworks:block/belt_wedge"}
        if turn:
            variant["y"] = turn
        variants[f"facing={facing}"] = variant
    return (json.dumps({"variants": variants}, indent=2) + "\n").encode()


def outputs():
    yield MODELS / "belt_wedge.json", wedge_model()
    yield MODELS / "splitter_half.json", splitter_half_model()
    yield BLOCKSTATES / "belt_wedge.json", wedge_blockstate()
    yield TEXTURES / "block/loader_slate.png", slate()
    yield TEXTURES / "block/loader_mouth.png", mouth()
    yield TEXTURES / "block/belt_underside.png", underside()
    yield TEXTURES / "block/belt_tile_side.png", tile_side()
    yield TEXTURES / "block/belt_slope_side.png", tile_side()
    for step, (prefix, hue) in enumerate(TIERS.values(), start=1):
        frames = sorted(FRAMES.glob("frame_*.png"))
        colour = tier_colour(recolour(frames[0], hue))
        yield TEXTURES / f"block/{prefix}loader_band.png", band(colour)
        for frame in frames:
            yield TEXTURES / f"block/{prefix}conveyorbelt/{frame.name}", recolour(frame, hue)
        yield from splitter_belt(prefix, [recolour(frame, hue) for frame in frames], step)
        yield from corner_strips(prefix, [recolour(frame, hue) for frame in frames], step)
        yield MODELS / f"{prefix}belt_tile.json", level_tile_model(prefix)
        for side in ("left", "right"):
            yield MODELS / f"{prefix}belt_tile_corner_{side}.json", corner_model(prefix, side == "left")
        for pitch in PITCHES:
            yield MODELS / f"{prefix}belt_tile_{pitch}.json", slope_model(prefix, pitch)
        yield BLOCKSTATES / f"{prefix}belt_tile.json", tile_blockstate(prefix)


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
        print(f"stale: {path.relative_to(TEXTURES.parent)}")
    return 1 if stale else 0


if __name__ == "__main__":
    sys.exit(main())
