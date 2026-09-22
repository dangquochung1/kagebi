#!/usr/bin/env python3
"""Bring the Frostwindz lightning pack down to this game's scale.

The pack ships six effects as loose frames at 128 or 256 pixels. This game is
320x180 with a 32px player, so a frame drawn as shipped would be most of the
screen. Three decisions are load-bearing:

* **Square cells, always.** `gfx.Anim.strip` takes the cell size from the
  region's *height* and divides the width by it, so a strip of 64x32 frames is
  read as twice as many 32px ones. Only VFX5 of the six is square as shipped,
  and VFX6's sheet is a 5x2 grid rather than a row - so the strips are built
  here from the loose frames and padded to a square cell, and the packer never
  sees the pack's own sheets.

* **One bounding box per effect, measured across all of its frames.** The ink
  in VFX2 travels left to right, VFX3 falls from the top, VFX6 arcs across the
  frame and dissipates. Cropping each frame to its own ink would centre every
  one of those and throw the movement away - the bolt would flicker in place
  instead of travelling. This is the same rule `build_cove.py` follows for the
  same reason.

* **Lanczos, then a hard alpha cut.** A resampled edge leaves a halo of
  half-transparent pixels, which on lightning reads as fog. The cut restores
  the hard edge the art is drawn with.

The icons are cut from whichever frame reads most clearly on its own and sat on
an opaque plate: a transparent icon over the HUD's cell art is unreadable, and
these are drawn over whatever the dungeon floor happens to be.

Usage:  python tools/make_skillfx.py [--report]
"""
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.join(ROOT, "assets", "packs", "Pixel Art Skill Animations - Lightning")
OUT_FX = os.path.join(ROOT, "assets", "gfx", "fx", "skill")
OUT_ICON = os.path.join(ROOT, "assets", "gfx", "ui", "skill")

#: Below this, a resampled edge pixel is background rather than bolt.
ALPHA_CUT = 100

#: The plate an icon sits on: the skin's own ink and a wood-light rim, so a
#: skill cell looks like the rest of the interface rather than a sticker.
ICON_CELL = 24
ICON_INK = (26, 16, 22, 255)
ICON_RIM = (155, 81, 60, 255)

#: our name -> (pack folder, divisor, square cell, icon frame or None)
#:
#: The divisors are chosen so the ink lands at a size that reads next to a 32px
#: player: a bolt about 50px long, a nova about 60 across, an aura about 30.
#: VFX5 is halved rather than quartered because it is a ring around the player
#: and a quarter of it would sit inside him.
#:
#: Only three of the six become icons, because only three of them are skills -
#: the trail, the chain strike and the head shock are things skills do rather
#: than things on the bar. The frame each icon is cut from is the one that
#: reads on its own: a bolt mid-arc, the nova at full ring, and for the
#: ultimate the thickest frame of the shock, which is the only one of these
#: that looks like a large amount of electricity.
EFFECTS = [
    ("bolt",   "VFX1", 4, 64, 1),
    ("trail",  "VFX2", 4, 64, None),
    ("strike", "VFX3", 4, 64, None),
    ("shock",  "VFX4", 4, 64, 2),
    ("nova",   "VFX5", 2, 64, 3),
    ("aura",   "VFX6", 4, 32, None),
]


def frames(folder):
    """The pack's loose frames, in numeric order rather than string order."""
    path = os.path.join(PACK, folder, "Frames")
    names = [n for n in os.listdir(path) if n.lower().endswith(".png")]
    names.sort(key=lambda n: int("".join(c for c in n if c.isdigit())[-1:]))
    return [Image.open(os.path.join(path, n)).convert("RGBA") for n in names]


def union_box(images):
    """One box covering the ink of every frame, so they share an origin."""
    box = None
    for im in images:
        b = im.getbbox()
        if b is None:
            continue
        box = b if box is None else (min(box[0], b[0]), min(box[1], b[1]),
                                     max(box[2], b[2]), max(box[3], b[3]))
    return box


def shrink(im, divisor):
    out = im.resize((max(1, im.width // divisor), max(1, im.height // divisor)),
                    Image.LANCZOS)
    alpha = out.split()[3].point(lambda v: 255 if v >= ALPHA_CUT else 0)
    out.putalpha(alpha)
    return out


def build_one(name, folder, divisor, cell, icon_frame, report):
    src = frames(folder)
    box = union_box(src)
    if box is None:
        raise SystemExit("%s: every frame is empty" % folder)

    small = [shrink(im.crop(box), divisor) for im in src]
    w = max(im.width for im in small)
    h = max(im.height for im in small)
    if w > cell or h > cell:
        raise SystemExit("%s: ink is %dx%d, which does not fit a %d cell"
                         % (folder, w, h, cell))

    strip = Image.new("RGBA", (cell * len(small), cell), (0, 0, 0, 0))
    for i, im in enumerate(small):
        strip.paste(im, (i * cell + (cell - im.width) // 2,
                         (cell - im.height) // 2), im)
    os.makedirs(OUT_FX, exist_ok=True)
    strip.save(os.path.join(OUT_FX, name + ".png"))

    if icon_frame is not None:
        icon = plate(src[min(icon_frame, len(src) - 1)].crop(box))
        os.makedirs(OUT_ICON, exist_ok=True)
        icon.save(os.path.join(OUT_ICON, name + ".png"))

    if report:
        print("  %-7s %d frames  cell %d  ink %dx%d  strip %dx%d"
              % (name, len(small), cell, w, h, strip.width, strip.height))


def plate(ink):
    """One frame, fitted onto an opaque tile with a one-pixel rim.

    The ink is cut to a centred square first. Fitting the whole of it instead
    turns a 211x55 bolt into a 20x5 line - technically the icon, and unreadable
    at the size it is shown. A square out of the middle of the same bolt is a
    legible piece of lightning, which is what an icon is for.
    """
    side = min(ink.width, ink.height)
    ink = ink.crop(((ink.width - side) // 2, (ink.height - side) // 2,
                    (ink.width - side) // 2 + side, (ink.height - side) // 2 + side))
    inner = ICON_CELL - 4
    scale = min(inner / ink.width, inner / ink.height)
    art = ink.resize((max(1, int(ink.width * scale)), max(1, int(ink.height * scale))),
                     Image.LANCZOS)
    alpha = art.split()[3].point(lambda v: 255 if v >= ALPHA_CUT else 0)
    art.putalpha(alpha)

    out = Image.new("RGBA", (ICON_CELL, ICON_CELL), ICON_INK)
    for x in range(ICON_CELL):
        out.putpixel((x, 0), ICON_RIM)
        out.putpixel((x, ICON_CELL - 1), ICON_RIM)
    for y in range(ICON_CELL):
        out.putpixel((0, y), ICON_RIM)
        out.putpixel((ICON_CELL - 1, y), ICON_RIM)
    out.paste(art, ((ICON_CELL - art.width) // 2, (ICON_CELL - art.height) // 2), art)
    return out


def build(report=False):
    """Entry point for tools/build_assets.py, which drives every pack."""
    for name, folder, divisor, cell, icon_frame in EFFECTS:
        build_one(name, folder, divisor, cell, icon_frame, report)
    print("skill fx: %d strips -> %s" % (len(EFFECTS), OUT_FX))


def main():
    report = "--report" in sys.argv
    if not os.path.isdir(PACK):
        print("ERROR: %s not found" % PACK)
        return 1
    if report:
        print("skill fx:")
    build(report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
