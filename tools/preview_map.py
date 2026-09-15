#!/usr/bin/env python3
"""Render a .tmx to a PNG without launching the game.

Iterating on a map through the game means a compile, a launch and a screenshot
each time. This does the same job in under a second, which is the difference
between trying five layouts and trying one.

Point it at a directory and it renders a contact sheet instead, which is the
only practical way to look at a hundred generated rooms: a room that is subtly
wrong - a wall run using the tile with a pilaster tab on it, a chest sitting
inside an obstacle - is obvious beside its neighbours and invisible alone.

`--spawns` draws the object layer on top. Spawn markers are the half of a room
that never shows up in a plain render, and a chest buried in a wall looks
exactly like a correct room until someone plays it.

`--collision` tints everything that stops the player red: whole tiles in the
walls and props layers, and the rectangles of a `collision` layer. What blocks
is the other half that never draws, and in the village it is cut to the pixel.

Usage:  python tools/preview_map.py assets/maps/village.tmx [out.png] [--zoom 2]
        python tools/preview_map.py assets/maps/rooms/ruins review/ruins.png --spawns
        python tools/preview_map.py assets/maps/home.tmx review/home.png --collision
"""
import os
import sys
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from sunnyside import draw_transformed  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TILE = 16

FLIP_HORIZONTAL = 0x80000000
FLIP_VERTICAL = 0x40000000
FLIP_DIAGONAL = 0x20000000
FLIP_BITS = 0xF0000000

# One letter per SpawnPoint.Kind, and a colour that survives being drawn over
# both a cream floor and a near-black one.
SPAWN_STYLE = {
    "ENTRY": ("E", (90, 200, 255)),
    "ENEMY": ("X", (255, 70, 70)),
    "CHEST": ("C", (255, 210, 60)),
    "EXIT": ("S", (120, 255, 120)),
    "SHOPKEEPER": ("K", (255, 130, 240)),
    "PROP": ("P", (200, 200, 200)),
}


def load(tmx_path):
    """The layers and objects of a map, resolved against its tilesets.

    Tile layers and object layers are drawn in the order the file lists them,
    which is the order the game draws them in. The island village interleaves
    the two - sprites under a roof, sprites on it - so a render that laid every
    tile first and every sprite after would be a picture of a different map.
    """
    root = ET.parse(tmx_path).getroot()
    tw, th = int(root.get("tilewidth")), int(root.get("tileheight"))
    mw, mh = int(root.get("width")), int(root.get("height"))
    base = os.path.dirname(os.path.abspath(tmx_path))

    # firstgid -> the sheet, its grid and which of its tiles animate. The grid
    # is the tileset's own: a sprite strip's frames are not the map's 16px.
    sheets = []
    for ts in root.findall("tileset"):
        img = ts.find("image")
        path = os.path.normpath(os.path.join(base, img.get("source")))
        animations = {}
        for tile in ts.findall("tile"):
            animation = tile.find("animation")
            if animation is not None:
                animations[int(tile.get("id"))] = [int(f.get("tileid"))
                                                   for f in animation.findall("frame")]
        sheets.append((int(ts.get("firstgid")), Image.open(path).convert("RGBA"),
                       int(ts.get("columns")), int(ts.get("tilewidth", tw)),
                       int(ts.get("tileheight", th)), animations))
    sheets.sort(key=lambda s: s[0])

    def lookup(gid, frame=None):
        """One tile's picture; for an animated tile, `frame` picks the frame."""
        for firstgid, image, columns, stw, sth, animations in reversed(sheets):
            if gid >= firstgid:
                local = gid - firstgid
                if frame is not None and local in animations:
                    local = animations[local][frame % len(animations[local])]
                sx, sy = (local % columns) * stw, (local // columns) * sth
                return image.crop((sx, sy, sx + stw, sy + sth))
        return None

    out = Image.new("RGBA", (mw * tw, mh * th), (0, 0, 0, 255))
    names, objects, shapes = [], [], []
    for node in root:
        if node.tag == "layer":
            names.append(node.get("name"))
            draw_layer(out, node, lookup, (tw, th), mw, shapes)
        elif node.tag == "objectgroup":
            for obj in node.findall("object"):
                x, y = float(obj.get("x", 0)), float(obj.get("y", 0))
                if node.get("name") == "collision":
                    shapes.append((x, y, float(obj.get("width", 0)),
                                   float(obj.get("height", 0))))
                elif obj.get("gid"):
                    draw_tile_object(out, obj, lookup)
                else:
                    objects.append((obj.get("type", ""), x, y, obj.get("name", "")))
    return out, names, objects, shapes, (mw, mh)


def draw_layer(out, layer, lookup, size, mw, shapes):
    """One tile layer, onto `out`; whole-tile blockers are added to `shapes`."""
    tw, th = size
    data = layer.find("data").text.replace("\n", "")
    gids = [int(v) for v in data.split(",") if v.strip()]
    name = layer.get("name", "")
    blocking = any(name == role or name.startswith(role + "_")
                   for role in ("walls", "props"))
    for i, raw in enumerate(gids):
        if raw == 0:
            continue
        if blocking:
            shapes.append(((i % mw) * tw, (i // mw) * th, tw, th))
        # Tiled keeps horizontal, vertical and diagonal flips in the top
        # three bits. Without masking them off, lookup() is handed a gid of
        # two billion and Pillow is asked to crop past the end of the
        # world; half the village's bushes are mirrored, so this is not a
        # rare case. The flips are then applied, or the render is a picture
        # of a map nobody drew.
        flags, gid = raw & FLIP_BITS, raw & ~FLIP_BITS
        tile = lookup(gid)
        if tile is None:
            continue
        if flags & FLIP_DIAGONAL:
            tile = tile.transpose(Image.TRANSPOSE)
        if flags & FLIP_HORIZONTAL:
            tile = tile.transpose(Image.FLIP_LEFT_RIGHT)
        if flags & FLIP_VERTICAL:
            tile = tile.transpose(Image.FLIP_TOP_BOTTOM)
        out.alpha_composite(tile, ((i % mw) * tw, (i // mw) * th))


def draw_tile_object(out, obj, lookup):
    """A tile placed as an object - a sprite - the way Tiled draws one.

    Tiled anchors a tile object at its bottom-left corner, stretches the tile
    to the object's width and height, mirrors it inside that box for the flip
    bits and turns the box clockwise about the anchor. `frame` is this game's
    own property: which frame of an animated tile the object starts on, so a
    still render shows each sprite where the scene caught it rather than every
    windmill at the same angle.
    """
    raw = int(obj.get("gid"))
    flags, gid = raw & FLIP_BITS, raw & ~FLIP_BITS
    frame = 0
    properties = obj.find("properties")
    if properties is not None:
        for p in properties.findall("property"):
            if p.get("name") == "frame":
                frame = int(p.get("value"))
    art = lookup(gid, frame)
    if art is None:
        return
    if flags & FLIP_HORIZONTAL:
        art = art.transpose(Image.FLIP_LEFT_RIGHT)
    if flags & FLIP_VERTICAL:
        art = art.transpose(Image.FLIP_TOP_BOTTOM)
    width = float(obj.get("width", art.width))
    height = float(obj.get("height", art.height))
    draw_transformed(out, art, (0, art.height),
                     (float(obj.get("x", 0)), float(obj.get("y", 0))),
                     (width / art.width, height / art.height),
                     -float(obj.get("rotation", 0)))


def draw_shapes(image, shapes, zoom):
    """What blocks, as a translucent red over the art it belongs to."""
    overlay = Image.new("RGBA", image.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)
    for x, y, w, h in shapes:
        d.rectangle([x * zoom, y * zoom, (x + w) * zoom - 1, (y + h) * zoom - 1],
                    fill=(255, 40, 40, 115))
    image.alpha_composite(overlay)


# Wall dressing is a spawn marker rather than a tile - see WALL_TORCHES in
# make_maps.py - so a plain tile render leaves every room's walls bare and this
# sheet stops being a picture of the room. These are drawn as the art instead of
# as a letter, and the rest of the markers stay letters: a torch is scenery that
# is always there, while an enemy or a chest is a position, not a picture.
PROP_ART = {
    "torch": "assets/gfx/props/depths/torch/torch.png",
    "banner": "assets/gfx/props/depths/flag/flag.png",
}
_SIDE_TORCH = "assets/gfx/props/depths/torch/side_torch.png"


def prop_frame(tag, side):
    """Frame 0 of a prop's loop, matching EntityWorld.addDecor's choice."""
    path = _SIDE_TORCH if (tag == "torch" and side) else PROP_ART.get(tag)
    if path is None:
        return None
    src = Image.open(os.path.join(ROOT, path)).convert("RGBA")
    return src.crop((0, 0, src.height, src.height))


def draw_objects(image, objects, zoom, width, letters):
    """Object markers, drawn in the map's own y-down pixel space.

    No flip here on purpose: this renders what Tiled would show, so a marker
    that looks wrong in this preview is wrong in the file. RoomCatalog does the
    y flip on the way into the game and nowhere else.

    Wall dressing is drawn always, letters only when asked. A torch is part of
    what the room looks like; an enemy marker is a position, and drawing the
    monster there would claim the room ships with that monster in it.
    """
    d = ImageDraw.Draw(image, "RGBA")
    for kind, x, y, tag in objects:
        cx, cy = x * zoom, y * zoom
        if kind == "PROP":
            side = x < TILE or x > width - TILE
            art = prop_frame(tag, side)
            if art is None:
                continue
            art = art.resize((art.width * zoom, art.height * zoom), Image.NEAREST)
            if side and x > width / 2:
                art = art.transpose(Image.FLIP_LEFT_RIGHT)
            image.alpha_composite(art, (int(cx - art.width / 2),
                                        int(cy - art.height / 2)))
            continue
        if not letters:
            continue
        letter, colour = SPAWN_STYLE.get(kind, ("?", (255, 255, 255)))
        r = 4 * zoom // 2
        d.ellipse([cx - r, cy - r, cx + r, cy + r],
                  fill=colour + (170,), outline=(0, 0, 0, 220))
        d.text((cx - 2, cy - 5), letter, fill=(0, 0, 0, 255))


def render(tmx_path, out_path, zoom=2, spawns=False, collision=False):
    image, layer_names, objects, shapes, (mw, mh) = load(tmx_path)
    width = image.width
    if zoom != 1:
        image = image.resize((image.width * zoom, image.height * zoom),
                             Image.NEAREST)
    if collision:
        draw_shapes(image, shapes, zoom)
    draw_objects(image, objects, zoom, width, spawns)
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    image.convert("RGB").save(out_path)
    print("  %s -> %s  (%dx%d tiles, %d layers: %s, %d objects)"
          % (os.path.basename(tmx_path), out_path, mw, mh, len(layer_names),
             ", ".join(layer_names), len(objects)))


def contact(folder, out_path, zoom=2, spawns=False, columns=3):
    """Every .tmx in a folder on one sheet, captioned with its file name."""
    files = sorted(f for f in os.listdir(folder) if f.endswith(".tmx"))
    if not files:
        raise SystemExit("no .tmx files in " + folder)

    shots = []
    for f in files:
        image, _, objects, _, _ = load(os.path.join(folder, f))
        width = image.width
        image = image.resize((image.width * zoom, image.height * zoom),
                             Image.NEAREST)
        draw_objects(image, objects, zoom, width, spawns)
        shots.append((f, image))

    cw, ch = shots[0][1].size
    pad, caption = 10, 13
    rows = (len(shots) + columns - 1) // columns
    sheet = Image.new("RGBA", (columns * (cw + pad) + pad,
                               rows * (ch + pad + caption) + pad),
                      (18, 18, 22, 255))
    d = ImageDraw.Draw(sheet)
    for i, (name, image) in enumerate(shots):
        gx, gy = i % columns, i // columns
        x = pad + gx * (cw + pad)
        y = pad + gy * (ch + pad + caption)
        d.text((x, y), "%s  %s" % (os.path.basename(folder), name),
               fill=(255, 215, 130, 255))
        sheet.paste(image, (x, y + caption))
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    sheet.convert("RGB").save(out_path)
    print("  %s -> %s  (%d rooms, %dx%d)"
          % (folder, out_path, len(shots), sheet.width, sheet.height))


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if not args:
        print(__doc__)
        raise SystemExit(2)
    src = args[0]
    dst = args[1] if len(args) > 1 else "tools/preview/map.png"
    zoom = 2
    if "--zoom" in sys.argv:
        zoom = int(sys.argv[sys.argv.index("--zoom") + 1])
    spawns = "--spawns" in sys.argv
    collision = "--collision" in sys.argv
    if os.path.isdir(src):
        contact(src, dst, zoom, spawns)
    else:
        render(src, dst, zoom, spawns, collision)
