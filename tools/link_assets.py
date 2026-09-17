#!/usr/bin/env python3
"""Point a git worktree at the main checkout's generated assets.

Three asset trees are gitignored on purpose - `assets/gfx`, `assets/audio` and
`assets/atlas` are 117 MB of derived files, and `_raw` holds packs that forbid
redistribution. A fresh worktree therefore has no art, no sound and no atlases,
so the game will not start and half the test suite skips.

Rebuilding them per worktree would take minutes and burn a gigabyte. Linking
them costs nothing: every worktree reads the same bytes, and nothing writes to
them except `tools/build_assets.py`, which is only ever run in the main
checkout.

On Windows these are directory junctions rather than symlinks, because a
junction needs no elevation and no developer mode. On anything else they are
ordinary symlinks.

Usage (from inside the worktree):
    python tools/link_assets.py <path to the main checkout>
"""
import os
import subprocess
import sys

LINKED = [
    os.path.join("assets", "gfx"),
    os.path.join("assets", "audio"),
    os.path.join("assets", "atlas"),
    os.path.join("assets", "packs"),
    "_raw",
]


def link(source, dest):
    if os.path.exists(dest) or os.path.islink(dest):
        print("  %-16s already there" % dest)
        return
    os.makedirs(os.path.dirname(dest) or ".", exist_ok=True)
    if os.name == "nt":
        result = subprocess.run(["cmd", "/c", "mklink", "/J", dest, source],
                                capture_output=True, text=True)
        if result.returncode != 0:
            raise SystemExit("mklink failed for %s: %s"
                             % (dest, result.stderr.strip() or result.stdout.strip()))
    else:
        os.symlink(source, dest, target_is_directory=True)
    print("  %-16s -> %s" % (dest, source))


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    main_checkout = os.path.abspath(sys.argv[1])
    here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    if os.path.normcase(main_checkout) == os.path.normcase(here):
        raise SystemExit("that is this checkout; run this from inside a worktree")
    if not os.path.isdir(os.path.join(main_checkout, "assets", "atlas")):
        raise SystemExit("%s has no built assets; run tools/build_assets.py and "
                         "tools/pack_atlas.py there first" % main_checkout)

    print("linking assets from %s" % main_checkout)
    for rel in LINKED:
        source = os.path.join(main_checkout, rel)
        if not os.path.isdir(source):
            print("  %-16s missing in the main checkout, skipped" % rel)
            continue
        link(source, os.path.join(here, rel))


if __name__ == "__main__":
    main()
