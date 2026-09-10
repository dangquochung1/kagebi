#!/usr/bin/env python3
"""Build the game's asset tree from the original packs in _raw/.

The packs are a grab bag: names contain spaces and ampersands, promo art sits
next to real sprites, one tileset is a pixel too tall to divide by 16, and the
dungeon pack ships every animation as four separate PNGs instead of a strip.
This script turns all of that into a clean, lowercase, underscore-only tree that
the game and TexturePacker can consume without special cases.

It is idempotent and never writes to _raw/, so it is safe to re-run.

Usage:  python tools/build_assets.py [--clean] [--dry-run]
"""
import argparse
import os
import re
import shutil
import sys
from collections import defaultdict

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, "_raw")
OUT = os.path.join(ROOT, "assets")

NINJA = os.path.join(RAW, "Ninja Adventure - Asset Pack")
DUNGEON = os.path.join(RAW, "2D Pixel Dungeon Asset Pack")
ENEMIES = os.path.join(RAW, "Enemy_Animations_Set")
RAVEN = os.path.join(RAW, "Free - Raven Fantasy Icons")
SUNNY = os.path.join(RAW, "SunnyLand Music")

# Promo art, contact sheets and editor previews that ship alongside the real
# assets. Matched case-insensitively against the bare filename.
JUNK_PATTERNS = [
    r"^preview.*", r".*preview\d*\.(png|gif)$", r"^allpreview\.png$",
    r"^demonstration\.png$", r"^example.*", r"^musiccover\.png$",
    r"^dungeon_gif\.gif$", r"^dungeon_(character|tileset)_at\.png$",
    r"^preview-part-\d+\.png$", r"^themepreview\.gif$",
    r"^\._.*", r"^\.ds_store$", r"^thumbs\.db$",
]
JUNK_RE = [re.compile(p, re.I) for p in JUNK_PATTERNS]

# SunnyLand tracks that fit a feudal-fantasy game. The pack also contains
# synthwave and chiptune pieces which would clash badly, so they stay behind.
SUNNY_KEEP = {
    "exploration.ogg", "world wanderer.ogg", "magic cliffs.ogg",
    "dark-happy-world.ogg", "fantasy Dragon.ogg", "hurry_up_and_run.ogg",
}

stats = defaultdict(int)
DRY = False


def norm(name):
    """Lowercase, and collapse anything awkward in a path segment to '_'."""
    stem, ext = os.path.splitext(name)
    stem = stem.replace("&", "and")
    stem = re.sub(r"[^A-Za-z0-9]+", "_", stem).strip("_").lower()
    stem = re.sub(r"_+", "_", stem)
    return stem + ext.lower()


def is_junk(filename):
    return any(rx.match(filename) for rx in JUNK_RE)


def ensure(path):
    if not DRY:
        os.makedirs(path, exist_ok=True)


def copy_file(src, dst):
    if is_junk(os.path.basename(src)):
        stats["skipped_junk"] += 1
        return False
    ensure(os.path.dirname(dst))
    if not DRY:
        shutil.copy2(src, dst)
    stats["copied"] += 1
    return True


def copy_tree(src_dir, dst_dir, exts=(".png",), recurse=True, flatten=False):
    """Copy files under src_dir into dst_dir, normalising every path segment."""
    if not os.path.isdir(src_dir):
        print("  ! missing source: %s" % src_dir)
        stats["missing_sources"] += 1
        return
    for cur, dirs, files in os.walk(src_dir):
        dirs[:] = [d for d in dirs if not is_junk(d) and d != "__MACOSX"]
        if not recurse and cur != src_dir:
            continue
        rel = os.path.relpath(cur, src_dir)
        if flatten or rel == ".":
            sub = ""
        else:
            sub = os.path.join(*[norm(p) for p in rel.split(os.sep)])
        for f in sorted(files):
            if exts and not f.lower().endswith(tuple(exts)):
                continue
            copy_file(os.path.join(cur, f), os.path.join(dst_dir, sub, norm(f)))


# --------------------------------------------------------------------------
# Frame-strip assembly
# --------------------------------------------------------------------------

FRAME_SUFFIX = re.compile(r"^(?P<base>.+?)_(?P<idx>\d+)$")


def assemble_strips(src_dir, dst_dir, recurse=True):
    """Merge `name_1.png .. name_N.png` sets into one horizontal strip.

    The dungeon pack ships every 4-frame loop as four separate files, which is
    unusable as-is: TexturePacker would treat them as unrelated regions and the
    game would have to reassemble them at load time. Strips are what the rest of
    the pipeline expects, so we build them here, once.
    """
    if not os.path.isdir(src_dir):
        print("  ! missing source: %s" % src_dir)
        stats["missing_sources"] += 1
        return

    for cur, dirs, files in os.walk(src_dir):
        dirs[:] = [d for d in dirs if not is_junk(d)]
        if not recurse and cur != src_dir:
            continue
        groups = defaultdict(list)
        singles = []
        for f in sorted(files):
            if not f.lower().endswith(".png") or is_junk(f):
                continue
            stem = os.path.splitext(f)[0]
            m = FRAME_SUFFIX.match(stem)
            if m:
                groups[norm(m.group("base"))].append((int(m.group("idx")),
                                                      os.path.join(cur, f)))
            else:
                singles.append(os.path.join(cur, f))

        rel = os.path.relpath(cur, src_dir)
        sub = "" if rel == "." else os.path.join(*[norm(p) for p in rel.split(os.sep)])
        target = os.path.join(dst_dir, sub)

        for base, entries in sorted(groups.items()):
            entries.sort()
            if len(entries) == 1:                     # not a sequence after all
                singles.append(entries[0][1])
                continue
            frames = [Image.open(p).convert("RGBA") for _, p in entries]
            w = max(f.width for f in frames)
            h = max(f.height for f in frames)
            strip = Image.new("RGBA", (w * len(frames), h), (0, 0, 0, 0))
            for i, fr in enumerate(frames):
                strip.paste(fr, (i * w, 0))
            ensure(target)
            if not DRY:
                strip.save(os.path.join(target, base + ".png"))
            stats["strips_built"] += 1

        for p in singles:
            copy_file(p, os.path.join(target, norm(os.path.basename(p))))


# --------------------------------------------------------------------------
# Individual pack steps
# --------------------------------------------------------------------------

def build_player():
    print("player")
    copy_tree(os.path.join(NINJA, "Actor/CharacterAnimated/NinjaGreen/Separate"),
              os.path.join(OUT, "gfx/actors/player/ninjagreen"), flatten=True)
    copy_file(os.path.join(NINJA, "Actor/CharacterAnimated/NinjaGreen/SpriteSheet.png"),
              os.path.join(OUT, "gfx/actors/player/ninjagreen/spritesheet.png"))
    copy_tree(os.path.join(NINJA, "Actor/CharacterAnimated/Weapon"),
              os.path.join(OUT, "gfx/actors/player/weapons"), flatten=True)
    copy_file(os.path.join(NINJA, "Actor/Character/Shadow.png"),
              os.path.join(OUT, "gfx/actors/shadow.png"))


def build_actors():
    print("npcs, monsters, bosses")
    # The pack names monster sheets inconsistently (Slime/Slime.png,
    # Mushroom/mushroom.png, BlueBat/SpriteSheet.png), so glob rather than
    # assume, and normalise the result to a predictable name.
    for group, dst in (("Actor/Character", "gfx/actors/npc"),
                       ("Actor/Monster", "gfx/actors/monsters")):
        src_root = os.path.join(NINJA, group)
        if not os.path.isdir(src_root):
            print("  ! missing source: %s" % src_root)
            stats["missing_sources"] += 1
            continue
        for entry in sorted(os.listdir(src_root)):
            d = os.path.join(src_root, entry)
            if not os.path.isdir(d):
                continue
            target = os.path.join(OUT, dst, norm(entry))
            faceset, sheet = None, None
            for f in sorted(os.listdir(d)):
                if not f.lower().endswith(".png") or is_junk(f):
                    continue
                low = f.lower()
                if "faceset" in low and faceset is None:
                    faceset = f
                elif "faceset" not in low and sheet is None:
                    sheet = f
            if sheet:
                copy_file(os.path.join(d, sheet),
                          os.path.join(target, "spritesheet.png"))
            if faceset:
                copy_file(os.path.join(d, faceset), os.path.join(target, "faceset.png"))
            sep = os.path.join(d, "SeparateAnim")
            if os.path.isdir(sep):
                copy_tree(sep, os.path.join(target, "anim"), flatten=True)

    copy_tree(os.path.join(NINJA, "Actor/Boss"), os.path.join(OUT, "gfx/actors/bosses"))


def build_tiles():
    print("tilesets")
    dst_over = os.path.join(OUT, "gfx/tiles/overworld")
    copy_tree(os.path.join(NINJA, "Backgrounds/Tilesets"), dst_over, recurse=False)
    copy_tree(os.path.join(NINJA, "Backgrounds/Tilesets/Interior"),
              os.path.join(OUT, "gfx/tiles/ruins"), flatten=True)
    copy_tree(os.path.join(NINJA, "Backgrounds/Animated"),
              os.path.join(OUT, "gfx/tiles/animated"))
    copy_tree(os.path.join(NINJA, "Backgrounds/Vehicles"),
              os.path.join(OUT, "gfx/props/overworld"), flatten=True)

    # TilesetFloor.png is 352x417. 417 is not a multiple of 16, so auto-slicing
    # it would shift every tile row below the first. Drop the stray row.
    floor = os.path.join(dst_over, "tilesetfloor.png")
    if os.path.isfile(floor) and not DRY:
        im = Image.open(floor)
        if im.height % 16:
            fixed = im.crop((0, 0, im.width, im.height - (im.height % 16)))
            fixed.save(floor)
            print("  fixed tilesetfloor.png %dx%d -> %dx%d"
                  % (im.width, im.height, fixed.width, fixed.height))
            stats["tilesets_fixed"] += 1

    # The dark biome: a different pack, used exclusively on the deep floors.
    copy_file(os.path.join(DUNGEON, "character and tileset/Dungeon_Tileset.png"),
              os.path.join(OUT, "gfx/tiles/depths/dungeon_tileset.png"))
    assemble_strips(os.path.join(DUNGEON, "items and trap_animation"),
                    os.path.join(OUT, "gfx/props/depths"))


def build_depth_actors():
    print("depth-biome actors")
    assemble_strips(os.path.join(DUNGEON, "Character_animation"),
                    os.path.join(OUT, "gfx/actors/depths"))
    for name in ("Dungeon_Character.png", "Dungeon_Character_2.png"):
        copy_file(os.path.join(DUNGEON, "character and tileset", name),
                  os.path.join(OUT, "gfx/actors/depths", norm(name)))
    # Animated skeletons/vampire that fill the dungeon pack's idle-only gap.
    # One source filename is misspelled; fix it so the loader can be regular.
    if os.path.isdir(ENEMIES):
        for f in sorted(os.listdir(ENEMIES)):
            if not f.lower().endswith(".png") or is_junk(f):
                continue
            out_name = norm(f).replace("enemies_", "")
            out_name = out_name.replace("_movemen.png", "_movement.png")
            copy_file(os.path.join(ENEMIES, f),
                      os.path.join(OUT, "gfx/actors/depths", out_name))
    else:
        print("  ! missing source: %s" % ENEMIES)
        stats["missing_sources"] += 1


def build_ui_fx_items():
    print("ui, fx, items, icons")
    copy_tree(os.path.join(NINJA, "Ui"), os.path.join(OUT, "gfx/ui"))
    assemble_strips(os.path.join(DUNGEON, "interface"),
                    os.path.join(OUT, "gfx/ui/depths"))
    copy_tree(os.path.join(NINJA, "FX"), os.path.join(OUT, "gfx/fx"))
    copy_tree(os.path.join(NINJA, "Items"), os.path.join(OUT, "gfx/items"))
    # Only the 16x16 sheet: the 32 and 64 sets are pure nearest-neighbour
    # upscales of it, so they carry no extra detail and 4384 extra files.
    copy_file(os.path.join(RAVEN, "Full Spritesheet/16x16.png"),
              os.path.join(OUT, "gfx/icons/raven_icons_16.png"))


def build_audio():
    print("audio")
    copy_tree(os.path.join(NINJA, "Audio/Musics"),
              os.path.join(OUT, "audio/music"), exts=(".ogg",), flatten=True)
    copy_tree(os.path.join(NINJA, "Audio/Jingles"),
              os.path.join(OUT, "audio/jingles"), exts=(".wav",), flatten=True)
    copy_tree(os.path.join(NINJA, "Audio/Sounds"),
              os.path.join(OUT, "audio/sfx"), exts=(".wav",))
    if os.path.isdir(SUNNY):
        for cur, dirs, files in os.walk(SUNNY):
            dirs[:] = [d for d in dirs if d != "__MACOSX"]
            for f in files:
                if f in SUNNY_KEEP:
                    copy_file(os.path.join(cur, f),
                              os.path.join(OUT, "audio/music", "sl_" + norm(f)))
    else:
        print("  ! missing source: %s" % SUNNY)
        stats["missing_sources"] += 1


def build_licenses():
    print("licenses")
    dst = os.path.join(OUT, "..", "LICENSES")
    pairs = [
        (os.path.join(NINJA, "LICENSE.txt"), "NinjaAdventure-CC0.txt"),
        (os.path.join(NINJA, "README.md"), "NinjaAdventure-README.md"),
        (os.path.join(SUNNY, "public-license.pdf"), "SunnyLandMusic-CC0.pdf"),
        (os.path.join(SUNNY, "Adventure pack 1 ogg/public-license.txt"),
         "SunnyLandMusic-license.txt"),
        (os.path.join(RAW, "read_me.txt"), "MysticWoods-NonCommercial.txt"),
        (os.path.join(RAW, "Sprout Lands - Sprites - Basic pack/read_me.txt"),
         "SproutLands-NonCommercial.txt"),
        (os.path.join(RAW, "_fonts/Pixeloid_Font_1_0/License.txt"),
         "PixeloidFont-OFL.txt"),
    ]
    ensure(os.path.abspath(dst))
    for src, name in pairs:
        if os.path.isfile(src):
            if not DRY:
                shutil.copy2(src, os.path.join(os.path.abspath(dst), name))
            stats["licenses"] += 1
        else:
            print("  ! license not found: %s" % src)


def enforce_tile_grid():
    """Move anything under gfx/tiles/ that is not 16px-aligned into props/.

    Tiled slices a tileset image blindly on the grid, so a stray 20x8 sprite
    sitting in there would silently corrupt tile indices. The pack's own
    filenames cannot be trusted for this (Flower ships "SpriteSheet16x16.png"
    at 20x8), so check the actual pixels.
    """
    print("tile grid check")
    tiles_root = os.path.join(OUT, "gfx/tiles")
    if not os.path.isdir(tiles_root):
        return
    for cur, _dirs, files in os.walk(tiles_root):
        for f in sorted(files):
            if not f.lower().endswith(".png"):
                continue
            src = os.path.join(cur, f)
            with Image.open(src) as im:
                w, h = im.size
            if w % 16 == 0 and h % 16 == 0:
                continue
            rel = os.path.relpath(cur, tiles_root)
            dst_dir = os.path.join(OUT, "gfx/props", rel if rel != "." else "misc")
            ensure(dst_dir)
            if not DRY:
                shutil.move(src, os.path.join(dst_dir, f))
            print("  %s is %dx%d, not tile-aligned -> props/%s"
                  % (f, w, h, os.path.relpath(dst_dir, os.path.join(OUT, "gfx/props"))))
            stats["rerouted_to_props"] += 1


STEPS = [build_player, build_actors, build_tiles, build_depth_actors,
         build_ui_fx_items, build_audio, build_licenses, enforce_tile_grid]


def main():
    global DRY
    ap = argparse.ArgumentParser()
    ap.add_argument("--clean", action="store_true",
                    help="delete the generated asset trees before building")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()
    DRY = args.dry_run

    if not os.path.isdir(RAW):
        print("ERROR: %s not found. The original packs must live there." % RAW)
        return 1

    if args.clean and not DRY:
        for sub in ("gfx", "audio", "props"):
            path = os.path.join(OUT, sub)
            if os.path.isdir(path):
                shutil.rmtree(path)
        print("cleaned generated trees\n")

    for step in STEPS:
        step()

    print("\nsummary")
    for k in sorted(stats):
        print("  %-16s %d" % (k, stats[k]))
    if stats["missing_sources"]:
        print("\n%d source folder(s) missing - output is incomplete."
              % stats["missing_sources"])
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
