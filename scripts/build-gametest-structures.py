"""Writes the structure the game tests stand on: a stone floor with air above it.

A game test is placed into a structure template, and its helper's coordinates are relative to
the template's corner. The tests place every block they need themselves, so all the template
holds is the floor. It is generated so the committed .nbt has a source.

Run it from anywhere: python3 scripts/build-gametest-structures.py
"""

import gzip
import io
import os
import struct

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
PATH = os.path.join(ROOT, "src", "main", "resources", "data", "beltworks", "structure", "gametest", "platform.nbt")

DATA_VERSION = 4790  # 26.1.2
# Chest, loader, three tiles, loader, chest, with a block of margin at each end and side.
SIZE = (9, 3, 3)

TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 3, 8, 9, 10


def _string(out, value):
    data = value.encode("utf-8")
    out.write(struct.pack(">H", len(data)))
    out.write(data)


def _tag_type(value):
    if isinstance(value, int):
        return TAG_INT
    if isinstance(value, str):
        return TAG_STRING
    if isinstance(value, list):
        return TAG_LIST
    return TAG_COMPOUND


def _payload(out, value):
    kind = _tag_type(value)
    if kind == TAG_INT:
        out.write(struct.pack(">i", value))
    elif kind == TAG_STRING:
        _string(out, value)
    elif kind == TAG_LIST:
        element = _tag_type(value[0]) if value else TAG_COMPOUND
        out.write(struct.pack(">bi", element, len(value)))
        for item in value:
            _payload(out, item)
    else:
        for key, item in value.items():
            out.write(struct.pack(">b", _tag_type(item)))
            _string(out, key)
            _payload(out, item)
        out.write(b"\x00")


def platform():
    width, _, depth = SIZE
    # Air is left out: the runner clears the test's box before placing the template.
    blocks = [{"pos": [x, 0, z], "state": 0} for x in range(width) for z in range(depth)]
    return {
        "DataVersion": DATA_VERSION,
        "size": list(SIZE),
        "palette": [{"Name": "minecraft:stone"}],
        "blocks": blocks,
        "entities": [],
    }


def main():
    out = io.BytesIO()
    out.write(struct.pack(">b", TAG_COMPOUND))
    _string(out, "")
    _payload(out, platform())
    os.makedirs(os.path.dirname(PATH), exist_ok=True)
    # mtime 0, so the same input always writes the same bytes.
    with open(PATH, "wb") as handle, gzip.GzipFile(fileobj=handle, mode="wb", mtime=0) as zipped:
        zipped.write(out.getvalue())
    print("wrote " + os.path.relpath(PATH, ROOT))


if __name__ == "__main__":
    main()
