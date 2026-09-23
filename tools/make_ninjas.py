#!/usr/bin/env python3
"""Recolour the one fully-animated ninja into the six playable characters.

The pack ships seventeen ninja variants, but only `CharacterAnimated/NinjaGreen`
has the twelve animations a player character needs - attack, roll, hit, dead and
the rest. Every other variant is a 16x16 walk-cycle sheet of the same kind the
NPCs use, so it cannot be played.

The way out is that the animated sheets use only eight colours, three of which
are cloth. Swapping those three against the ramp of another variant gives a
character that is genuinely in the pack's palette rather than a hue rotation of
it, and keeps the outline, skin, eyes and red accent identical across all six so
they read as the same village.

Ramps are read out of the 16x16 variants rather than written down here: whatever
the artist chose for NinjaRed is what NinjaRed should be. One is written down,
because the pack has no violet ninja and two of the six were the same blue -
see VARIANTS.

Usage:  python tools/make_ninjas.py [--report]
"""
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "_raw", "Ninja Adventure - Asset Pack",
                   "Actor", "Character")
PLAYER = os.path.join(ROOT, "assets", "gfx", "actors", "player")
SOURCE = "ninjagreen"

# The twelve animations exist, but a top-down dungeon crawler never climbs,
# swims or pushes, and the combined `spritesheet.png` only repeats what the
# separate files already hold. Packing the unused ones would not fit six
# characters onto one 2048 page.
USED = ("attack", "dead", "hit", "idle", "pickup", "roll", "walk")

# Shared across every variant, so never swapped: outline, skin, eye white, eye
# highlight, and the red sash that ties the whole cast together.
COMMON = {(0x14, 0x1B, 0x1B), (0xEF, 0x91, 0x4F), (0xF2, 0xEA, 0xF1),
          (0xFF, 0xFF, 0xFF), (0xD1, 0x4B, 0x34)}

# id -> (pack folder its ramp is taken from, or a ramp written out here).
#
# `ninjablue` is the written-out one. It is Tử Uyển, the violet ninja, and none
# of the pack's seventeen is violet - but the roster had a worse problem than a
# missing colour: NinjaBlue and NinjaWater share their lightest tone exactly,
# so two of the six read as the same character at roster size. The three
# colours below are still the pack's own, counted out of its Actor/Character
# sheets rather than invented, so the hero stays in the pack's palette instead
# of becoming a hue rotation of it. The dark tone is NinjaRed's darkest and the
# light one is NinjaMasked's highlight.
#
# The id stays `ninjablue`: it is the sprite folder and V4ToV5 rebuilds it by
# writing "ninja" in front of a saved colour, so renaming it would cost every
# v4 profile its characters to fix a word no player ever sees.
VIOLET = [(0x54, 0x3C, 0x52), (0xA5, 0x60, 0x8B), (0xD3, 0xA2, 0xC0)]

VARIANTS = [
    ("ninjared", "NinjaRed", None),
    ("ninjablue", None, VIOLET),
    ("ninjadark", "NinjaDark", None),
    ("ninjafire", "NinjaFire", None),
    ("ninjawater", "NinjaWater", None),
]


def luma(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def ramp_of(png):
    """The three cloth tones of a sheet, ordered dark to light.

    Ranked by pixel count first so that a stray anti-aliased pixel cannot
    displace a real cloth colour, then sorted by brightness so the three map
    onto the source ramp in the right order.
    """
    counts = {}
    with Image.open(png) as im:
        for px in im.convert("RGBA").getdata():
            if px[3] == 0 or px[:3] in COMMON:
                continue
            counts[px[:3]] = counts.get(px[:3], 0) + 1
    ranked = sorted(counts, key=counts.get, reverse=True)[:3]
    if not ranked:
        raise SystemExit("no cloth colours found in %s" % png)
    while len(ranked) < 3:
        # Fire only has two cloth tones. Mixing the darkest halfway to the
        # outline gives a third that sits in the same family.
        darkest = min(ranked, key=luma)
        outline = (0x14, 0x1B, 0x1B)
        ranked.append(tuple((darkest[i] + outline[i]) // 2 for i in range(3)))
    return sorted(ranked, key=luma)


def sheet_of(folder):
    for name in ("SpriteSheet.png", "spritesheet.png"):
        p = os.path.join(RAW, folder, name)
        if os.path.exists(p):
            return p
    raise SystemExit("no sprite sheet in %s" % folder)


def recolour(src_dir, dst_dir, mapping):
    os.makedirs(dst_dir, exist_ok=True)
    n = 0
    for name in sorted(os.listdir(src_dir)):
        if not name.endswith(".png") or name[:-4] not in USED:
            continue
        with Image.open(os.path.join(src_dir, name)) as im:
            im = im.convert("RGBA")
            pixels = list(im.getdata())
            im.putdata([(mapping.get(p[:3], p[:3]) + (p[3],)) for p in pixels])
            im.save(os.path.join(dst_dir, name))
        n += 1
    return n


def build(report=False):
    src_dir = os.path.join(PLAYER, SOURCE)
    if not os.path.isdir(src_dir):
        raise SystemExit("run tools/build_assets.py first: %s is missing" % src_dir)
    source_ramp = ramp_of(sheet_of("NinjaGreen"))
    if report:
        print("%-11s %s" % (SOURCE, " ".join("#%02X%02X%02X" % c for c in source_ramp)))
    for ident, folder, written in VARIANTS:
        ramp = written if written is not None else ramp_of(sheet_of(folder))
        mapping = dict(zip(source_ramp, ramp))
        count = recolour(src_dir, os.path.join(PLAYER, ident), mapping)
        if report:
            print("%-11s %s  (%d files)"
                  % (ident, " ".join("#%02X%02X%02X" % c for c in ramp), count))


if __name__ == "__main__":
    build(report="--report" in sys.argv or True)
