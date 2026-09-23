#!/usr/bin/env python3
"""Bring Hoa Tam's three fire packs down to this game's scale.

The machinery - square cells, one bounding box across every frame, Lanczos and
a hard alpha cut, the frame sort, the icon plate - is in tools/fxstrip.py and
is shared with the lightning and poison tools. What is here is the table: which
pack, which frames of it, and how much of it to keep.

The windows are the part worth reading. Explosion 2 holds two animations end to
end in eighteen 48px cells: a white sparkle in the first five, then a shadow,
then the fire dome from the ninth onward. `firerun` is the four frames of that
dome that loop without a seam, because it has to hold for three seconds;
`fireburst` is the whole of it played once, which is how the charge ends.

The divisors put the ink where it reads beside a 32px player and a 16 to 32px
monster: a ring sixty across, a bloom fifty, a lash forty, a burst on one enemy
twenty-five, a thrown fireball fifteen, and the mark a burning enemy carries
seven. Two are deliberately not shrunk at all - the charge aura has to wrap a
body, and it is 48px as drawn.

Usage:  python tools/make_firefx.py [--report]
"""
import os
import sys

from fxstrip import Effect, build_all, grid, loose, row

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACKS = os.path.join(ROOT, "assets", "packs")
OUT_FX = os.path.join(ROOT, "assets", "gfx", "fx", "skill")
OUT_ICON = os.path.join(ROOT, "assets", "gfx", "ui", "skill")

I_PACK = os.path.join(PACKS, "Ifireskill", "Fire Effect 2")
U_PACK = os.path.join(PACKS, "Ufireskill")
O_PACK = os.path.join(PACKS, "Ofireskill")
FREE = os.path.join(PACKS, "Free Pixel Effects Pack")

#: Every folder a source may come from, so a missing pack is one clear error.
SOURCE_DIRS = (I_PACK, U_PACK, O_PACK, FREE)

EXPLOSION2 = os.path.join(I_PACK, "Explosion 2 SpriteSheet.png")

EFFECTS = [
    Effect("firerun", row(EXPLOSION2, 48), cell=48, window=(8, 12)),
    Effect("fireburst", row(EXPLOSION2, 48), cell=48, window=(8, 16)),
    # The charge's icon is cut from the burst it leaves rather than from the
    # aura it runs in: a close square out of a dome that wraps a body is a
    # smear, and the burst is the part of the skill that has a shape.
    Effect("fireblast", row(os.path.join(I_PACK, "Explosion SpriteSheet.png"), 64),
           cell=32, divisor=2, window=(2, 12), icon=2),
    Effect("firering", loose(U_PACK, "VFX3", "frames"), cell=64, divisor=2, icon=6),
    Effect("firestar", loose(O_PACK, "VFX3", "Frames"), cell=64, divisor=2, icon=2),
    Effect("firebloom", loose(O_PACK, "VFX1", "Frames"), cell=64, divisor=2),
    Effect("firehit", loose(O_PACK, "VFX2", "Frames"), cell=32, divisor=4),
    # `flip` mirrors every frame top to bottom, and one effect needs it. The
    # lash ships as a crescent opening upwards, and EntityWorld.meleeTrail
    # draws it in front of a swing that chops downwards. That is a reflection,
    # and no rotation produces one: turning the arc to face left by 180 degrees
    # flipped it vertically as well, so the blade came up from below on one
    # facing and down from above on the other. The strip is stored chopping
    # downwards and the drawing code mirrors it sideways.
    Effect("flamelash", grid(os.path.join(FREE, "6_flamelash_spritesheet.png"), 100),
           cell=48, window=(2, 12), flip=True),
    Effect("sunburn", grid(os.path.join(FREE, "16_sunburn_spritesheet.png"), 100),
           cell=16, divisor=4, window=(0, 8)),
    Effect("brightfire", grid(os.path.join(FREE, "9_brightfire_spritesheet.png"), 100),
           cell=16, divisor=2, window=(0, 8)),
]


def build(report=False):
    """Entry point for tools/build_assets.py, which drives every pack."""
    build_all(EFFECTS, OUT_FX, OUT_ICON, "fire fx", report)


def main():
    report = "--report" in sys.argv
    for path in SOURCE_DIRS:
        if not os.path.isdir(path):
            print("ERROR: %s not found" % path)
            return 1
    if report:
        print("fire fx:")
    build(report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
