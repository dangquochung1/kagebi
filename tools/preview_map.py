#!/usr/bin/env python3
"""Render a .tmx to a PNG without launching the game.

Iterating on a map through the game means a compile, a launch and a screenshot
each time. This does the same job in under a second, which is the difference
between trying five layouts and trying one.

Usage:  python tools/preview_map.py assets/maps/village.tmx [out.png] [--zoom 2]
"""
import os
import sys
import xml.etree.ElementTree as ET

from PIL import Image


def render(tmx_path, out_path, zoom=2):
    tree = ET.parse(tmx_path)
    root = tree.getroot()
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
        for i, gid in enumerate(gids):
            if gid == 0:
                continue
            image, sx, sy = lookup(gid)
            if image is None:
                continue
            tile = image.crop((sx, sy, sx + tw, sy + th))
            out.paste(tile, ((i % mw) * tw, (i // mw) * th), tile)

    if zoom != 1:
        out = out.resize((out.width * zoom, out.height * zoom), Image.NEAREST)
    out.save(out_path)
    print("  %s -> %s  (%dx%d tiles, %d layers: %s)"
          % (os.path.basename(tmx_path), out_path, mw, mh, len(layers),
             ", ".join(l.get("name") for l in layers)))


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        raise SystemExit(2)
    src = sys.argv[1]
    dst = sys.argv[2] if len(sys.argv) > 2 else "tools/preview/map.png"
    zoom = 2
    if "--zoom" in sys.argv:
        zoom = int(sys.argv[sys.argv.index("--zoom") + 1])
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    render(src, dst, zoom)
