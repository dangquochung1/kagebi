#!/usr/bin/env python3
"""Read the Sunnyside World pack the way its own GameMaker project draws it.

The pack ships its showcase - the island village of
Sunnyside_World_ExampleScene.png - not only as a picture but as the GameMaker
room the picture was rendered from, Room1.yy: every tile, and every sprite with
its position, mirror, scale, rotation and starting frame. tools/make_island.py
builds the village from that room, so the village is the scene itself rather
than an impression of it.

Three things in the project are easy to read wrongly, and each was found by
rendering the room and holding the render against the pack's own screenshot:

  * A tile's flags are applied MIRROR, then FLIP, then a quarter turn
    CLOCKWISE. Every other order still looks right almost everywhere - and
    turns the diagonal shorelines into a checkerboard.
  * The PNG of a sprite frame in the sprite's folder is a flattened preview.
    The frame is really its layers, and the props draw their shadow on a layer
    at 25% (the ground shadow at 20%). The preview draws every shadow solid.
  * An erased tile is bit 31 over index 0. Index 0 is never drawn.

This module only reads the pack. The one thing it writes is images, into
whatever folder a caller names.

    python tools/sunnyside.py review/room1.png     # the room as GameMaker draws it
"""
import json
import math
import os
import re
import sys
from functools import lru_cache

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.join(ROOT, "assets", "packs", "miniworld")
PROJECT = os.path.join(PACK, "Sunnyside_World_Gamemaker")
LOOSE = os.path.join(PACK, "Sunnyside_World_Assets")
EXAMPLE = os.path.join(LOOSE, "Sunnyside_World_ExampleScene.png")
ROOM = os.path.join(PROJECT, "rooms", "Room1", "Room1.yy")

TILE = 16

# GameMaker's tile bits.
INDEX = 0x7FFFF
MIRROR = 0x10000000
FLIP = 0x20000000
ROTATE = 0x40000000

# Tiled's. The same three ideas, applied in a different order - see tiled_flags.
TILED_H = 0x80000000
TILED_V = 0x40000000
TILED_D = 0x20000000


def load_yy(path):
    """A GameMaker resource file: JSON, except that it allows trailing commas."""
    with open(path, encoding="utf-8") as fh:
        text = fh.read()
    return json.loads(re.sub(r",(\s*[}\]])", r"\1", text))


def _project(*parts):
    return os.path.join(PROJECT, *parts)


# --------------------------------------------------------------------------
# The room
# --------------------------------------------------------------------------

def _cells(tiles):
    """A tile layer's values, unpacked from GameMaker's run-length encoding.

    A negative count repeats the one value after it; a positive count copies
    that many values as they are. A layer saved by an older IDE has no
    compression at all, which is how the sea layer comes.
    """
    if "TileCompressedData" not in tiles:
        return list(tiles["TileSerialiseData"])
    data, out, i = tiles["TileCompressedData"], [], 0
    while i < len(data):
        count = data[i]
        i += 1
        if count < 0:
            out.extend([data[i]] * -count)
            i += 1
        else:
            out.extend(data[i:i + count])
            i += count
    return out


class TileLayer:
    def __init__(self, node):
        self.name = node["name"]
        self.depth = node["depth"]
        self.tileset = node["tilesetId"]["name"]
        tiles = node["tiles"]
        self.width = tiles["SerialiseWidth"]
        self.height = tiles["SerialiseHeight"]
        self.cells = [v & 0xFFFFFFFF for v in _cells(tiles)]
        if len(self.cells) != self.width * self.height:
            raise SystemExit("%s: %d tiles for a %dx%d layer"
                             % (self.name, len(self.cells), self.width, self.height))

    def raw(self, x, y):
        return self.cells[y * self.width + x]


class Placement:
    """One sprite as the room places it, in GameMaker's terms."""

    def __init__(self, node, layer, order):
        self.sprite = node["spriteId"]["name"]
        self.x = float(node["x"])
        self.y = float(node["y"])
        self.scale_x = float(node["scaleX"])
        self.scale_y = float(node["scaleY"])
        # Degrees, counter-clockwise on screen.
        self.rotation = float(node["rotation"])
        # The frame the animation starts on. A float in the file; whole numbers
        # in practice.
        self.frame = int(math.floor(node["headPosition"]))
        self.speed = float(node["animationSpeed"])
        self.layer = layer
        self.order = order


class Room:
    """Room1: its tile layers and sprite layers, deepest (drawn first) first."""

    def __init__(self, path=ROOM):
        node = load_yy(path)
        self.layers = []
        order = 0
        for layer in node["layers"]:
            kind = layer["resourceType"]
            if kind == "GMRTileLayer":
                content = TileLayer(layer)
            elif kind == "GMRAssetLayer":
                content = [Placement(a, layer["name"], order + i)
                           for i, a in enumerate(layer.get("assets", []))]
                order += len(content)
            else:
                # The instance layer is empty and the effect layer is a shader
                # over the sea, which the game does in code.
                continue
            self.layers.append((layer["depth"], layer["name"], content))
        self.layers.sort(key=lambda t: -t[0])
        grid = self.tile_layer("land")
        self.width, self.height = grid.width, grid.height

    def tile_layer(self, name):
        for _, layer_name, content in self.layers:
            if layer_name == name:
                if not isinstance(content, TileLayer):
                    raise SystemExit("%s is not a tile layer" % name)
                return content
        raise SystemExit("the room has no layer %s" % name)

    def placements(self, name):
        for _, layer_name, content in self.layers:
            if layer_name == name:
                return content
        raise SystemExit("the room has no layer %s" % name)


# --------------------------------------------------------------------------
# Sprites and tilesets
# --------------------------------------------------------------------------

class Sprite:
    """A sprite's frames, flattened from their layers at each layer's opacity."""

    def __init__(self, name):
        node = load_yy(_project("sprites", name, name + ".yy"))
        self.name = name
        self.width = node["width"]
        self.height = node["height"]
        sequence = node["sequence"]
        self.origin = (sequence["xorigin"], sequence["yorigin"])
        if sequence.get("playbackSpeedType", 0) != 0:
            raise SystemExit("%s: speed is in frames per game frame, which nothing "
                             "here converts" % name)
        self.fps = float(sequence["playbackSpeed"])
        # The IDE lists a sprite's layers top first.
        layers = [l for l in reversed(node["layers"]) if l.get("visible", True)]
        self.frames = []
        for frame in node["frames"]:
            image = Image.new("RGBA", (self.width, self.height))
            for layer in layers:
                path = _project("sprites", name, "layers", frame["name"],
                                layer["name"] + ".png")
                if not os.path.isfile(path):
                    continue
                art = Image.open(path).convert("RGBA")
                opacity = layer.get("opacity", 100.0) / 100.0
                if opacity < 1.0:
                    art.putalpha(art.getchannel("A").point(
                        lambda a, o=opacity: int(round(a * o))))
                image.alpha_composite(art)
            self.frames.append(image)

    def strip(self):
        """Every frame side by side, left to right."""
        out = Image.new("RGBA", (self.width * len(self.frames), self.height))
        for i, frame in enumerate(self.frames):
            out.alpha_composite(frame, (i * self.width, 0))
        return out

    def frame_ms(self, speed=1.0):
        """How long one frame shows, for an animation played at `speed`."""
        if self.fps <= 0 or speed <= 0:
            return 0
        return int(round(1000.0 / (self.fps * speed)))


@lru_cache(maxsize=None)
def sprite(name):
    return Sprite(name)


class TileSheet:
    """A tileset: its image, its tile size and which of its tiles animate."""

    def __init__(self, name):
        node = load_yy(_project("tilesets", name, name + ".yy"))
        self.name = name
        self.size = node["tileWidth"]
        self.image = sprite(node["spriteId"]["name"]).frames[0]
        self.columns = self.image.width // self.size
        self.rows = self.image.height // self.size
        # Tile index -> [(tile index, milliseconds), ...]. GameMaker stores a
        # fixed-length frame list for every tile in the set; a tile animates
        # when its list is not one index repeated.
        self.animations = {}
        animation = node.get("tileAnimation") or {}
        count = animation.get("SerialiseFrameCount", 1)
        speed = float(node.get("tileAnimationSpeed", 0) or 0)
        data = animation.get("FrameData", [])
        if count > 1 and speed > 0:
            ms = int(round(1000.0 / speed))
            for index in range(len(data) // count):
                frames = data[index * count:(index + 1) * count]
                if len(set(frames)) < 2:
                    continue
                runs = []
                for f in frames:
                    if runs and runs[-1][0] == f:
                        runs[-1][1] += ms
                    else:
                        runs.append([f, ms])
                self.animations[index] = [tuple(r) for r in runs]

    def tile(self, index):
        x, y = index % self.columns, index // self.columns
        return self.image.crop((x * self.size, y * self.size,
                                (x + 1) * self.size, (y + 1) * self.size))


@lru_cache(maxsize=None)
def tilesheet(name):
    return TileSheet(name)


def gm_tile(sheet, raw):
    """A placed tile's picture: mirrored, then flipped, then turned clockwise."""
    t = sheet.tile(raw & INDEX)
    if raw & MIRROR:
        t = t.transpose(Image.FLIP_LEFT_RIGHT)
    if raw & FLIP:
        t = t.transpose(Image.FLIP_TOP_BOTTOM)
    if raw & ROTATE:
        t = t.transpose(Image.ROTATE_270)
    return t


def tiled_flags(raw):
    """The Tiled flip bits that place a tile the way these GameMaker bits do.

    Tiled applies its anti-diagonal flip first, then horizontal, then vertical.
    A clockwise quarter turn is that diagonal flip followed by a horizontal
    one, and moving the mirror and the flip past the diagonal swaps their axes,
    which is where the (not flip, mirror) comes from. Checked against all eight
    combinations on an asymmetric image.
    """
    mirror, flip, rotate = bool(raw & MIRROR), bool(raw & FLIP), bool(raw & ROTATE)
    if rotate:
        h, v, d = not flip, mirror, True
    else:
        h, v, d = mirror, flip, False
    return (TILED_H if h else 0) | (TILED_V if v else 0) | (TILED_D if d else 0)


# --------------------------------------------------------------------------
# Drawing
# --------------------------------------------------------------------------

def paste_clipped(canvas, image, x, y):
    """alpha_composite that tolerates an image hanging off any edge."""
    left, top = max(0, -x), max(0, -y)
    right = min(image.width, canvas.width - x)
    bottom = min(image.height, canvas.height - y)
    if right <= left or bottom <= top:
        return
    part = image if (left, top, right, bottom) == (0, 0, image.width, image.height) \
        else image.crop((left, top, right, bottom))
    canvas.alpha_composite(part, (x + left, y + top))


def draw_transformed(canvas, image, anchor, at, scale, degrees):
    """Draw `image` with its pixel `anchor` at world point `at`.

    Scaled by `scale` about the anchor and turned `degrees` counter-clockwise
    on screen about it - GameMaker's draw_sprite_ext. A negative scale mirrors.
    An image neither scaled nor turned is placed on whole pixels, rounding its
    top-left corner, which is what the game does too.
    """
    ax, ay = anchor
    x, y = at
    sx, sy = scale
    if degrees == 0 and sx in (1.0, -1.0) and sy in (1.0, -1.0):
        art = image
        left = x - ax
        top = y - ay
        if sx < 0:
            art = art.transpose(Image.FLIP_LEFT_RIGHT)
            left = x - (image.width - ax)
        if sy < 0:
            art = art.transpose(Image.FLIP_TOP_BOTTOM)
            top = y - (image.height - ay)
        paste_clipped(canvas, art, int(math.floor(left + 0.5)), int(math.floor(top + 0.5)))
        return
    t = math.radians(degrees)
    c, s = math.cos(t), math.sin(t)
    corners = []
    for u, v in ((0, 0), (image.width, 0), (0, image.height), (image.width, image.height)):
        dx, dy = (u - ax) * sx, (v - ay) * sy
        corners.append((x + dx * c + dy * s, y - dx * s + dy * c))
    x0 = int(math.floor(min(p[0] for p in corners)))
    y0 = int(math.floor(min(p[1] for p in corners)))
    x1 = int(math.ceil(max(p[0] for p in corners)))
    y1 = int(math.ceil(max(p[1] for p in corners)))
    coeffs = (c / sx, -s / sx, ax + ((x0 - x) * c - (y0 - y) * s) / sx,
              s / sy, c / sy, ay + ((x0 - x) * s + (y0 - y) * c) / sy)
    part = image.transform((max(1, x1 - x0), max(1, y1 - y0)), Image.AFFINE, coeffs,
                           resample=Image.NEAREST)
    paste_clipped(canvas, part, x0, y0)


def reference(room=None, skip=()):
    """The room as GameMaker draws it: every layer, deepest first, frame 0 of
    every tile and each sprite's starting frame. `skip` names layers to leave out.
    """
    room = room or Room()
    canvas = Image.new("RGBA", (room.width * TILE, room.height * TILE), (0, 0, 0, 255))
    for _, name, content in room.layers:
        if name in skip:
            continue
        if isinstance(content, TileLayer):
            sheet = tilesheet(content.tileset)
            layer = Image.new("RGBA", canvas.size)
            for i, raw in enumerate(content.cells):
                if raw & INDEX:
                    paste_clipped(layer, gm_tile(sheet, raw),
                                  (i % content.width) * sheet.size,
                                  (i // content.width) * sheet.size)
            canvas.alpha_composite(layer)
        else:
            for p in content:
                spr = sprite(p.sprite)
                draw_transformed(canvas, spr.frames[p.frame % len(spr.frames)], spr.origin,
                                 (p.x, p.y), (p.scale_x, p.scale_y), p.rotation)
    return canvas


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "review", "room1.png")
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    reference().save(out)
    print("  Room1 -> %s" % out)
