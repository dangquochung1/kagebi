#!/usr/bin/env python3
"""Cut the pieces this game uses out of the CraftPix RPG GUI pack.

The pack ships finished windows rather than nine-patch parts: `Craft.png` is a
three-by-three sheet of whole craft screens at different fill states, and the
same for equipment and inventory. That suits this game, whose screens are a
fixed 320x180 - a window that is already the right shape needs no stretching,
and the artist's own proportions survive.

So only a handful of regions are taken, and the rest of the UI stays on the
game's own skin. The pack's palette is the same family as `kagebi_skin.json`
already uses - `wood_light #ffad55`, `cream #eecf9b` - which is most of why it
was chosen over the other GUI packs on the drive.

Output goes to assets/gfx/ui/hero/, which pack_atlas.py's `ui` root picks up on
its own; nothing has to be added to its root list.

Usage:  python tools/slice_heroui.py [--quiet]
"""
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.join(ROOT, "assets", "packs", "heroui", "PNG")
OUT = os.path.join(ROOT, "assets", "gfx", "ui", "hero")

#: name -> (source file, box). Boxes measured off the sheets with an alpha
#: band scan, not guessed; see the tool's own history if one ever drifts.
PIECES = {
    # The three windows, in their "full" state: the craft screen with its
    # shelf, grid and result page; the equipment screen with its portrait
    # frame; the inventory grid. Each is one image, drawn at its own size.
    "craft":     ("Craft.png",     (208, 128, 416, 245)),
    "equipment": ("Equipment.png", (0, 96, 160, 181)),
    "inventory": ("Inventory.png", (112, 0, 222, 101)),

    # The CREATE button, up and pressed. The pack's four states are up, hover,
    # down and disabled, left to right.
    "button":      ("Craft.png", (2, 432, 45, 450)),
    "button_over": ("Craft.png", (50, 432, 93, 450)),
    "button_down": ("Craft.png", (98, 432, 141, 450)),
    "button_off":  ("Craft.png", (146, 432, 189, 450)),
}


def cut(name, source, box, quiet):
    path = os.path.join(PACK, source)
    if not os.path.isfile(path):
        raise SystemExit("missing %s - is the pack in assets/packs/heroui?" % path)
    with Image.open(path) as im:
        piece = im.convert("RGBA").crop(box)
    piece.save(os.path.join(OUT, name + ".png"))
    if not quiet:
        print("  %-12s %dx%d" % (name, piece.width, piece.height))


def build(quiet=False):
    os.makedirs(OUT, exist_ok=True)
    if not quiet:
        print("slicing the GUI pack")
    for name, (source, box) in sorted(PIECES.items()):
        cut(name, source, box, quiet)


if __name__ == "__main__":
    build("--quiet" in sys.argv)
