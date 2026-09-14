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

Usage:  python tools/preview_map.py assets/maps/village.tmx [out.png] [--zoom 2]
        python tools/preview_map.py assets/maps/rooms/ruins review/ruins.png --spawns
"""
import os
import sys
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw

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
    """The tile layers and objects of a map, resolved against its tilesets."""
    root = ET.parse(tmx_path).getroot()
    tw, th = int(root.get("tilewidth")), int(root.get("tileheight"))
    mw, mh = int(root.get("width")), int(root.get("height"))
    base = os.path.dirname(os.path.abspath(tmx_path))

    # firstgid -> (image, columns), so a gid can be resolved to a source rect.
    sheets = []
    for ts in root.findall("tileset"):
        img = ts.find("image")
        path = os.path.normpath(os.path.join(base, img.get("source")))
        sheets.append((int(ts.get("firstgid")), Image.open(path).convert("RGBA"),
                       int(ts.get("columns")), ts.get("name")))
    sheets.sort()

    def lookup(gid):
        for firstgid, image, columns, _ in reversed(sheets):
            if gid >= firstgid:
                local = gid - firstgid
                return image, (local % columns) * tw, (local // columns) * th
        return None, 0, 0

    out = Image.new("RGBA", (mw * tw, mh * th), (0, 0, 0, 255))
    layers = root.findall("layer")
    for layer in layers:
        data = layer.find("data").text.replace("\n", "")
        gids = [int(v) for v in data.split(",") if v.strip()]
        for i, raw in enumerate(gids):
            if raw == 0:
                continue
            # Tiled keeps horizontal, vertical and diagonal flips in the top
            # three bits. Without masking them off, lookup() is handed a gid of
            # two billion and Pillow is asked to crop past the end of the
            # world; half the village's bushes are mirrored, so this is not a
            # rare case. The flips are then applied, or the render is a picture
            # of a map nobody drew.
            flags, gid = raw & FLIP_BITS, raw & ~FLIP_BITS
            image, sx, sy = lookup(gid)
            if image is None:
                continue
            tile = image.crop((sx, sy, sx + tw, sy + th))
            if flags & FLIP_DIAGONAL:
                tile = tile.transpose(Image.TRANSPOSE)
            if flags & FLIP_HORIZONTAL:
                tile = tile.transpose(Image.FLIP_LEFT_RIGHT)
            if flags & FLIP_VERTICAL:
                tile = tile.transpose(Image.FLIP_TOP_BOTTOM)
            out.paste(tile, ((i % mw) * tw, (i // mw) * th), tile)

    objects = []
    for group in root.findall("objectgroup"):
        for obj in group.findall("object"):
            objects.append((obj.get("type", ""),
                            float(obj.get("x", 0)), float(obj.get("y", 0)),
                            obj.get("name", "")))
    return out, [l.get("name") for l in layers], objects, (mw, mh)


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


def render(tmx_path, out_path, zoom=2, spawns=False):
    image, layer_names, objects, (mw, mh) = load(tmx_path)
    width = image.width
    if zoom != 1:
        image = image.resize((image.width * zoom, image.height * zoom),
                             Image.NEAREST)
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
        image, _, objects, _ = load(os.path.join(folder, f))
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
    if os.path.isdir(src):
        contact(src, dst, zoom, spawns)
    else:
        render(src, dst, zoom, spawns)
