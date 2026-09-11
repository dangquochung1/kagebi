#!/usr/bin/env python3
"""Render a tileset PNG with a labelled 16px grid over it.

The tile packs ship no metadata saying which tile is a wall top and which is an
inner corner, so every coordinate in make_maps.py had to be read off the image
by eye. Guessing costs a full generate-render-look cycle per wrong guess; a
labelled grid turns that into one look. This is the tool that produced the
numbers in the TILES tables of make_maps.py.

Every fourth line is drawn brighter and the axes are labelled every second
column, because at 4x zoom an unlabelled 22-column grid is uncountable - which
is exactly how the village generator ended up pointing at an empty column 0.

A checkerboard goes behind the tiles so a fully transparent tile is visibly
empty rather than indistinguishable from a black one. That distinction matters:
several of these sheets pad their last row with transparent tiles.

Usage:  python tools/preview_tiles.py assets/gfx/tiles/ruins/tilesetinterior.png
        python tools/preview_tiles.py --all
"""
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TILE = 16
OUT_DIR = os.path.join(ROOT, "tools", "preview")


def grid(src_path, out_path, zoom=4):
    with Image.open(src_path) as im:
        sheet = im.convert("RGBA")
    cols, rows = sheet.width // TILE, sheet.height // TILE
    margin = 22

    # Checkerboard, so transparent padding tiles read as empty not as black.
    board = Image.new("RGBA", sheet.size, (60, 60, 68, 255))
    d = ImageDraw.Draw(board)
    for ty in range(rows + 1):
        for tx in range(cols + 1):
            if (tx + ty) % 2 == 0:
                d.rectangle([tx * TILE, ty * TILE,
                             tx * TILE + TILE - 1, ty * TILE + TILE - 1],
                            fill=(84, 84, 94, 255))
    board.alpha_composite(sheet)
    big = board.resize((board.width * zoom, board.height * zoom), Image.NEAREST)

    out = Image.new("RGBA", (big.width + margin, big.height + margin),
                    (24, 24, 28, 255))
    out.paste(big, (margin, margin))
    d = ImageDraw.Draw(out)

    step = TILE * zoom
    for tx in range(cols + 1):
        x = margin + tx * step
        bright = tx % 4 == 0
        d.line([(x, margin), (x, out.height)],
               fill=(255, 90, 90, 220) if bright else (255, 255, 255, 70))
        if tx < cols and tx % 2 == 0:
            d.text((x + 3, 6), str(tx), fill=(255, 210, 120, 255))
    for ty in range(rows + 1):
        y = margin + ty * step
        bright = ty % 4 == 0
        d.line([(margin, y), (out.width, y)],
               fill=(255, 90, 90, 220) if bright else (255, 255, 255, 70))
        if ty < rows:
            d.text((3, y + 3), str(ty), fill=(255, 210, 120, 255))

    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    out.convert("RGB").save(out_path)
    print("  %-30s %2d x %2d tiles -> %s"
          % (os.path.basename(src_path), cols, rows, out_path))


def name_for(path):
    parts = os.path.normpath(path).split(os.sep)
    stem = os.path.splitext(parts[-1])[0]
    return "%s_%s_grid.png" % (parts[-2], stem)


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    zoom = 4
    if "--zoom" in sys.argv:
        zoom = int(sys.argv[sys.argv.index("--zoom") + 1])

    if "--all" in sys.argv:
        for biome in ("ruins", "depths"):
            folder = os.path.join(ROOT, "assets", "gfx", "tiles", biome)
            for f in sorted(os.listdir(folder)):
                if f.endswith(".png"):
                    src = os.path.join(folder, f)
                    grid(src, os.path.join(OUT_DIR, name_for(src)), zoom)
    elif args:
        src = args[0]
        dst = args[1] if len(args) > 1 else os.path.join(OUT_DIR, name_for(src))
        grid(src, dst, zoom)
    else:
        print(__doc__)
        raise SystemExit(2)
