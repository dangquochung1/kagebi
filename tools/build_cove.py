#!/usr/bin/env python3
"""Build the Drowned Cove's art from the four Craftpix packs in assets/packs/.

Called by tools/build_assets.py as one step. Separate because none of these
four packs is shaped like anything already in the pipeline, and each needs a
conversion of its own:

  dungeon6   16px tilesets. Straight copy of the Tiled_files/ grid, which is
             not the PNG/ grid with padding as it first looks: walls_floor is
             17x29 tiles there against 13x23 in PNG/, a superset. It is what
             the pack's own Dungeon1.tmx indexes into, so the animations and
             tile numbers in that sample map are readable only against it.
             Both are tight 16px grids, which is what RoomCatalogTest needs.
  slimes6    64px cells laid out rows=direction, columns=frame. Anim.directional
             wants the transpose of that: four columns, one per Dir. Pixel art
             already at the right density, so it is transposed and not resized.
  bosses6    840x720 vector renders, one PNG per frame. Cropped, downscaled
             about 1:10 and posterised, or they read as smooth sprites pasted
             into a pixel game.
  bossfx6    flat frame sequences. Cropped, downscaled, assembled into strips.

Idempotent, and never writes to assets/packs/.
"""
import os
import re

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACKS = os.path.join(ROOT, "assets", "packs")
GFX = os.path.join(ROOT, "assets", "gfx")

DUNGEON6 = os.path.join(PACKS, "dungeon6")
SLIMES6 = os.path.join(PACKS, "slimes6")
BOSSES6 = os.path.join(PACKS, "bosses6")
BOSSFX6 = os.path.join(PACKS, "bossfx6")

# ---------------------------------------------------------------------------
# Shared
# ---------------------------------------------------------------------------

#: Anim.directional reads the column index as the Dir ordinal, and Dir is
#: DOWN, UP, LEFT, RIGHT in that order - "the ordinal is therefore the sheet
#: column, and nothing may reorder these". Both packs' row order happens to
#: match it exactly: row 0 faces the viewer, row 1 shows the back, rows 2 and
#: 3 face left and right. So the transpose below is a transpose and nothing
#: more. If a future pack differs, this is the constant to permute.
DIR_ROWS = (0, 1, 2, 3)
FACINGS = ("Front", "Back", "Left", "Right")


def _ensure(path):
    os.makedirs(path, exist_ok=True)


def _grow(box, other):
    if other is None:
        return box
    if box is None:
        return other
    return (min(box[0], other[0]), min(box[1], other[1]),
            max(box[2], other[2]), max(box[3], other[3]))


def _pixelate(im, cell, box, colours):
    """Crop to box, fit inside a square cell, harden the alpha, cut the palette.

    The box is not squared: `thumbnail` preserves the aspect ratio and the
    result is pasted centred, so a wide attack frame keeps its reach instead of
    being clipped to fit.
    """
    im = im.crop(box)
    im.thumbnail((cell, cell), Image.LANCZOS)
    out = Image.new("RGBA", (cell, cell), (0, 0, 0, 0))
    out.paste(im, ((cell - im.width) // 2, (cell - im.height) // 2))

    alpha = out.getchannel("A").point(lambda a: 255 if a >= ALPHA_CUT else 0)
    rgb = out.convert("RGB").quantize(colors=colours).convert("RGB")
    rgb.putalpha(alpha)
    return rgb


#: Anything under this alpha is cut away rather than left as a soft edge. A
#: vector render downscaled 1:10 is a halo of 1-40% alpha pixels, and those
#: read as blur against 16px art.
ALPHA_CUT = 110


# ---------------------------------------------------------------------------
# dungeon6 -> assets/gfx/tiles/cove/
# ---------------------------------------------------------------------------

#: The pack's names, shortened to what a .tmx tileset is called. Everything
#: here is 16px-aligned on both axes, so enforce_tile_grid() leaves it alone.
TILESETS = {
    "walls_floor.png": "walls_floor.png",
    "decorative_cracks_walls.png": "cracks_walls.png",
    "decorative_cracks_floor.png": "cracks_floor.png",
    "decorative_cracks_coasts_animation.png": "cracks_coasts.png",
    "Water_coasts_animation.png": "water_coasts.png",
    "water_details_animation.png": "water_details.png",
    "fire_animation.png": "fire.png",
    "fire_animation2.png": "fire2.png",
    "trap_animation.png": "traps.png",
    "doors_lever_chest_animation.png": "doors.png",
    "Objects.png": "objects.png",
}


def build_tiles(report):
    src = os.path.join(DUNGEON6, "Tiled_files")
    dst = os.path.join(GFX, "tiles", "cove")
    if not os.path.isdir(src):
        return "missing source: %s" % src
    _ensure(dst)
    for name, out in sorted(TILESETS.items()):
        with Image.open(os.path.join(src, name)) as im:
            if im.width % 16 or im.height % 16:
                return "%s is %dx%d, not a 16px grid" % (name, im.width, im.height)
            im.convert("RGBA").save(os.path.join(dst, out))
    report("  cove tiles: %d sheets" % len(TILESETS))
    return None


# ---------------------------------------------------------------------------
# slimes6 -> assets/gfx/actors/slimes/<id>/<anim>.png
# ---------------------------------------------------------------------------

SLIME_CELL = 64

#: Pack folder -> the enemies.json id, named for what each one does rather
#: than its colour: spike swings, tide bursts, ember detonates.
SLIMES = {"Slime1": "slimespike", "Slime2": "slimetide", "Slime3": "slimeember"}

SLIME_ANIMS = ("Idle", "Walk", "Run", "Attack", "Hurt", "Death")


def _transpose(sheet, cell):
    """rows=direction, columns=frame  ->  columns=direction, rows=frame."""
    cols = sheet.width // cell
    rows = sheet.height // cell
    if rows != len(DIR_ROWS):
        raise ValueError("expected %d direction rows, found %d"
                         % (len(DIR_ROWS), rows))
    out = Image.new("RGBA", (cell * len(DIR_ROWS), cell * cols), (0, 0, 0, 0))
    for d, row in enumerate(DIR_ROWS):
        for frame in range(cols):
            out.paste(sheet.crop((frame * cell, row * cell,
                                  (frame + 1) * cell, (row + 1) * cell)),
                      (d * cell, frame * cell))
    return out


def build_slimes(report):
    total = 0
    for folder, slime_id in sorted(SLIMES.items()):
        src = os.path.join(SLIMES6, "PNG", folder, "Without_shadow")
        if not os.path.isdir(src):
            return "missing source: %s" % src
        dst = os.path.join(GFX, "actors", "slimes", slime_id)
        _ensure(dst)
        for anim in SLIME_ANIMS:
            path = os.path.join(src, "%s_%s_without_shadow.png" % (folder, anim))
            if not os.path.isfile(path):
                return "missing %s" % path
            with Image.open(path) as im:
                _transpose(im.convert("RGBA"), SLIME_CELL).save(
                    os.path.join(dst, anim.lower() + ".png"))
            total += 1
    report("  cove slimes: %d sheets across %d bodies" % (total, len(SLIMES)))
    return None


# ---------------------------------------------------------------------------
# bosses6 -> assets/gfx/actors/bosses6/<id>/<anim>.png
# ---------------------------------------------------------------------------

BOSS_CELL = 64

#: Few enough colours to read as pixel art, enough to keep a pirate's coat,
#: skin and steel apart.
BOSS_COLOURS = 24

BOSSES = {
    "Pirate Leader": "pirateleader",
    "Pirate Zombie": "piratezombie",
    "Squidman": "squidman",
}

#: (pack folder suffix, output name, keep every Nth frame). The packs render
#: smoothly and the game holds each frame for 6-8 of its 60 steps, so every
#: second frame is plenty and it halves the atlas cost.
BOSS_DIR_ANIMS = (("Idle", "idle", 2), ("Walking", "walk", 2),
                  ("Running", "run", 2), ("Attacking", "attack", 2),
                  ("Hurt", "hurt", 2))

#: The pack ships one Dying/ folder, not one per facing, so death is a strip.
BOSS_DEATH = ("Dying", "death", 2)


def _frame_paths(folder, keep):
    names = sorted(p for p in os.listdir(folder) if p.lower().endswith(".png"))
    return [os.path.join(folder, p) for p in (names[::keep] or names[:1])]


def _bbox_of(paths):
    """Union bounding box, opening one file at a time: these are 2.4MB each."""
    box = None
    for p in paths:
        with Image.open(p) as im:
            box = _grow(box, im.convert("RGBA").getbbox())
    return box


def _boss_folders(seqs):
    """Every animation folder of one boss, as (paths, keep) pairs."""
    out = []
    for name, _o, keep in BOSS_DIR_ANIMS:
        for facing in FACINGS:
            out.append(os.path.join(seqs, "%s - %s" % (facing, name)))
    out.append(os.path.join(seqs, BOSS_DEATH[0]))
    return out


def build_bosses(report):
    total = 0
    for folder, boss_id in sorted(BOSSES.items()):
        seqs = os.path.join(BOSSES6, folder, "PNG", "PNG Sequences")
        if not os.path.isdir(seqs):
            return "missing source: %s" % seqs
        dst = os.path.join(GFX, "actors", "bosses6", boss_id)
        _ensure(dst)

        # One crop box for the whole boss, measured across every animation and
        # every facing. Per-animation boxes would make the figure jump between
        # states, because an attack reaches further than an idle does.
        box = None
        for anim_dir in _boss_folders(seqs):
            if not os.path.isdir(anim_dir):
                return "missing source: %s" % anim_dir
            box = _grow(box, _bbox_of(_frame_paths(anim_dir, 2)))

        for name, out, keep in BOSS_DIR_ANIMS:
            per_dir = [_frame_paths(os.path.join(seqs, "%s - %s" % (f, name)), keep)
                       for f in FACINGS]
            rows = min(len(p) for p in per_dir)
            sheet = Image.new("RGBA", (BOSS_CELL * len(FACINGS), BOSS_CELL * rows),
                              (0, 0, 0, 0))
            for d, paths in enumerate(per_dir):
                for r in range(rows):
                    with Image.open(paths[r]) as im:
                        sheet.paste(_pixelate(im.convert("RGBA"), BOSS_CELL,
                                              box, BOSS_COLOURS),
                                    (d * BOSS_CELL, r * BOSS_CELL))
            sheet.save(os.path.join(dst, out + ".png"))
            total += 1

        paths = _frame_paths(os.path.join(seqs, BOSS_DEATH[0]), BOSS_DEATH[2])
        strip = Image.new("RGBA", (BOSS_CELL * len(paths), BOSS_CELL), (0, 0, 0, 0))
        for i, p in enumerate(paths):
            with Image.open(p) as im:
                strip.paste(_pixelate(im.convert("RGBA"), BOSS_CELL,
                                      box, BOSS_COLOURS), (i * BOSS_CELL, 0))
        strip.save(os.path.join(dst, BOSS_DEATH[1] + ".png"))
        total += 1

    report("  cove bosses: %d sheets across %d bodies" % (total, len(BOSSES)))
    return None


# ---------------------------------------------------------------------------
# bossfx6 -> assets/gfx/fx/skill6/<name>.png
# ---------------------------------------------------------------------------

FX_CELL = 32

#: More than the bosses get: a fireball is mostly gradient, and 24 colours
#: bands it into rings.
FX_COLOURS = 40

#: folder -> (output name, mirror). The pack draws every directed skill
#: pointing LEFT, and Projectile rotates a sprite by atan2 of its heading,
#: which assumes it points right - the same convention fx/projectile/kunai is
#: drawn to. So the four directed ones are mirrored frame by frame. The two
#: balls are radially symmetric and are left alone.
SKILLS = {
    "Fire Ball": ("fire_ball", False),
    "Fire Spell": ("fire_spell", True),
    "Fire Arrow": ("fire_arrow", True),
    "Water Ball": ("water_ball", False),
    "Water Spell": ("water_spell", True),
    "Water Arrow": ("water_arrow", True),
}

FRAME_NUM = re.compile(r"_Frame_(\d+)\.png$", re.I)


def build_fx(report):
    dst = os.path.join(GFX, "fx", "skill6")
    _ensure(dst)
    for folder, (out, mirror) in sorted(SKILLS.items()):
        src = os.path.join(BOSSFX6, folder, "PNG")
        if not os.path.isdir(src):
            return "missing source: %s" % src
        paths = [os.path.join(src, f) for _n, f in
                 sorted((int(FRAME_NUM.search(f).group(1)), f)
                        for f in os.listdir(src) if FRAME_NUM.search(f))]
        if not paths:
            return "no frames in %s" % src
        box = _bbox_of(paths)
        strip = Image.new("RGBA", (FX_CELL * len(paths), FX_CELL), (0, 0, 0, 0))
        for i, p in enumerate(paths):
            with Image.open(p) as im:
                cell = _pixelate(im.convert("RGBA"), FX_CELL, box, FX_COLOURS)
            if mirror:
                cell = cell.transpose(Image.FLIP_LEFT_RIGHT)
            strip.paste(cell, (i * FX_CELL, 0))
        strip.save(os.path.join(dst, out + ".png"))
    report("  cove fx: %d skill strips" % len(SKILLS))
    return None


# ---------------------------------------------------------------------------
# Slime deaths, again, as impact effects
# ---------------------------------------------------------------------------

#: What a spell leaves on the floor where it lands.
#:
#: The ember slime's death strip is a body collapsing into a pool of fire that
#: burns down and goes out over ten frames, and the tide slime's is the same in
#: water. That is exactly what a spell landing should look like, and the two
#: sheets are already converted - so rather than inventing an impact out of the
#: spell's own frames, the facing-the-viewer column of each death sheet is
#: re-emitted as a plain fx strip.
#:
#: Column 0 because build_slimes writes direction across the columns in Dir
#: order, and DOWN is the one drawn face on.
BURSTS = {"slimeember": "fire_burst", "slimetide": "water_burst"}
#: Twice the spells' 32, because a puddle is wider than the comet that made it
#: and this is what Projectile draws a landed hazard at.
BURST_CELL = 48


def build_bursts(report):
    dst = os.path.join(GFX, "fx", "skill6")
    _ensure(dst)
    for slime_id, out in sorted(BURSTS.items()):
        path = os.path.join(GFX, "actors", "slimes", slime_id, "death.png")
        if not os.path.isfile(path):
            return "missing %s - build_slimes must run first" % path
        with Image.open(path) as im:
            sheet = im.convert("RGBA")
        frames = sheet.height // SLIME_CELL
        strip = Image.new("RGBA", (BURST_CELL * frames, BURST_CELL), (0, 0, 0, 0))
        for f in range(frames):
            cell = sheet.crop((0, f * SLIME_CELL, SLIME_CELL, (f + 1) * SLIME_CELL))
            strip.paste(cell.resize((BURST_CELL, BURST_CELL), Image.NEAREST),
                        (f * BURST_CELL, 0))
        strip.save(os.path.join(dst, out + ".png"))
    report("  cove bursts: %d impact strips" % len(BURSTS))
    return None


# ---------------------------------------------------------------------------
# The two balls again, as actors
# ---------------------------------------------------------------------------

#: The orb a boss becomes between bodies is an enemy, not an effect: it stands
#: in the room, holds it open, and has to be in the actors atlas because that
#: is the only one ActorSprites reads. So the same strips are written a second
#: time in the shape a directional set wants - four identical columns, because
#: a ball looks the same from every side and Anim.directional insists on one
#: column per facing whether the art has four or not.
ORBS = {"fire_ball": "fireorb", "water_ball": "waterorb"}


def build_orbs(report):
    src = os.path.join(GFX, "fx", "skill6")
    for strip_name, orb_id in sorted(ORBS.items()):
        path = os.path.join(src, strip_name + ".png")
        if not os.path.isfile(path):
            return "missing %s - build_fx must run first" % path
        dst = os.path.join(GFX, "actors", "bosses6", orb_id)
        _ensure(dst)
        with Image.open(path) as im:
            strip = im.convert("RGBA")
        frames = strip.width // FX_CELL
        sheet = Image.new("RGBA", (FX_CELL * len(DIR_ROWS), FX_CELL * frames),
                          (0, 0, 0, 0))
        for f in range(frames):
            cell = strip.crop((f * FX_CELL, 0, (f + 1) * FX_CELL, FX_CELL))
            for d in range(len(DIR_ROWS)):
                sheet.paste(cell, (d * FX_CELL, f * FX_CELL))
        sheet.save(os.path.join(dst, "idle.png"))
    report("  cove orbs: %d bodies" % len(ORBS))
    return None


# ---------------------------------------------------------------------------

def build(report=print):
    """Runs every conversion. Returns a list of problems, empty when clean."""
    problems = []
    for step in (build_tiles, build_slimes, build_bosses, build_fx,
                 build_bursts, build_orbs):
        problem = step(report)
        if problem:
            problems.append(problem)
            report("  ! %s" % problem)
    return problems


if __name__ == "__main__":
    import sys
    sys.exit(1 if build() else 0)
