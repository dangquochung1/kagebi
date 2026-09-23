#!/usr/bin/env python3
"""Bring Tu Uyen's three poison packs down to this game's scale.

The machinery is in tools/fxstrip.py, shared with the lightning and fire tools.
What is here is the table, and three decisions worth writing down.

**Two sources arrive crooked.** Starcaller's second spell is a comet falling
down and to the left, at a consistent forty-five degrees across all eight
frames. Everything in this game that draws a strip at an angle - a dash trail,
a lunge - rotates it by the player's heading and assumes the art points right,
so a comet that is already on a diagonal comes out pointing somewhere nobody
aimed. `turn` straightens it here, once, rather than leaving a correction
buried in the code that aims it.

**Two strips come from one folder at two sizes.** The ultimate's opening drops
a bolus of poison on every enemy in the room and wants to be bigger than the
ninja; the off hand throws the same thing and wants to be smaller than the
enemy it is aimed at. Same eight frames, two divisors, and the alternative was
scaling at draw time, which on pixel art is how a sprite stops being pixel art.

**The two Gigapack sheets are windowed hard.** They run at fifteen frames a
second for two seconds; this game runs a skill strip at twenty and needs the
absorb to fit inside a stagger and the detonation inside a hit. Twelve frames
each is the part that reads.

Usage:  python tools/make_poisonfx.py [--report]
"""
import os
import sys

from fxstrip import Effect, build_all, loose

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACKS = os.path.join(ROOT, "assets", "packs")
OUT_FX = os.path.join(ROOT, "assets", "gfx", "fx", "skill")
OUT_ICON = os.path.join(ROOT, "assets", "gfx", "ui", "skill")

POISON = os.path.join(PACKS, "Poison")
STAR = os.path.join(PACKS, "Pixel Art VFX - Starcaller - FREE Version")
GIGA = os.path.join(PACKS, "Super Pixel Effects Gigapack (Free Version)",
                    "PNG", "Fantasy Spells")

#: Every folder a source may come from, so a missing pack is one clear error.
SOURCE_DIRS = (POISON, STAR, GIGA)


def giga(name, colour):
    return loose(GIGA, name, "%s_%s" % (name, colour))


EFFECTS = [
    # The ultimate's opening, one on every enemy in the room. Turned a quarter
    # so the ring the pack drew flat on the ground stands up and the bolus
    # reads as arriving from above rather than as welling up out of the floor.
    Effect("venomfall", loose(POISON, "VFX1", "Frames"), cell=48, divisor=3, turn=90),
    # The same eight frames, thrown. A kunai is 8px and this replaces it.
    Effect("venombolt", loose(POISON, "VFX1", "Frames"), cell=16, divisor=8),
    # The dash trail. 64 to match fx/skill/trail.png, which is the strip this
    # one stands in for and the reason dash strips are stored lying along +x.
    Effect("venomdash", loose(POISON, "VFX2", "Frames"), cell=64, divisor=3),
    # What a venomed enemy carries, and the icon on the passive's line. Small:
    # three of these can be on one enemy and the enemy has to stay readable.
    Effect("venommark", loose(POISON, "VFX3", "Frames"), cell=16, divisor=5, icon=3),
    Effect("starfall", loose(STAR, "VFX 1", "Frames"), cell=64, divisor=2, icon=2),
    Effect("starcomet", loose(STAR, "VFX 2", "Frames"), cell=48, divisor=3,
           icon=3, turn=135),
    # Six frames of fifteen, and the window is the whole design. The pack
    # draws this side-on: it opens as a crescent, fills with light, and then
    # closes into a near-black sphere. It is drawn under the actors, so the
    # lit frames sit behind the ninja and read as a shell - but ten seconds of
    # the black ones behind a 16px figure on a sand floor reads as a hole in
    # the ground. The window ends where the light does. Eighteen steps against
    # an aura re-cast every fourteen, so it never gutters.
    Effect("starward", loose(STAR, "VFX 3", "Frames"), cell=64, divisor=2,
           icon=4, window=(0, 6), centred=True),
    # The ward flaring as it absorbs a blow: particles drawn inward to a point.
    # The window skips the four frames of empty approach before they arrive.
    Effect("venomdrain", giga("spell_absorb_001", "small_violet"),
           cell=32, divisor=2, window=(4, 16)),
    # Three stacks detonating. The window keeps the bloom and drops the long
    # dissipating tail, which at this size is four frames of green speckle.
    Effect("venomburst", giga("spell_poison_001", "small_green"),
           cell=32, divisor=2, window=(0, 12)),
]


def build(report=False):
    """Entry point for tools/build_assets.py, which drives every pack."""
    build_all(EFFECTS, OUT_FX, OUT_ICON, "poison fx", report)


def main():
    report = "--report" in sys.argv
    for path in SOURCE_DIRS:
        if not os.path.isdir(path):
            print("ERROR: %s not found" % path)
            return 1
    if report:
        print("poison fx:")
    build(report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
