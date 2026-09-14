#!/usr/bin/env python3
"""Generate assets/maps/world.tmx - the world map the stages are chosen from.

Deliberately a separate script from make_maps.py, and deliberately not called
by it. make_maps.py regenerates the 104 room templates and the village on every
run; this one is run once to lay out a map that is then finished by hand in
Tiled, and re-running it is a decision rather than a side effect.

Like every generated map here, the `decor` layer is yours: write_tmx carries it
across a regeneration, tilesets you added in Tiled included. Everything else is
overwritten, so a landmark you want to keep goes in `decor`.

The three tilesets are the ones the village already uses, whose tile
coordinates are measured in make_maps.py rather than guessed at. The water and
relief sheets are unmeasured and would be a better vocabulary for a world map -
adding them is a good next step, and a hand pass in Tiled is a better one.

    python tools/make_world.py
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from make_maps import (  # noqa: E402
    OUT, TILE, Layer, build_tilesets, write_tmx,
    GRASS, TREES, PINE, BUSHES, TUFTS, FLOWERS, ROCK, TORII,
)

# One screen exactly. 20x11 at 16px is 320x176, which is the virtual screen
# less the four rows the focused stage's name is written into. No scrolling:
# at 320x180 the stage panel is most of the screen, and on a map that scrolled
# it would routinely cover the node it was describing.
WIDTH, HEIGHT = 20, 11

# Where the five stages sit, in TILES, left to right and roughly descending.
# Read as the centre of the node marker; the screen converts to pixels. Kept
# off the top and bottom rows so a focus ring and a name have room.
#
# The shape is a trail from the village side to the far corner, bending down
# through the middle so no two nodes share a row - two markers level with each
# other read as alternatives rather than as a sequence.
NODES = [(2, 4), (6, 6), (10, 3), (14, 7), (17, 4)]

# What stands beside each node, as (tileset, tile, w, h, dx, dy) from the node.
# One landmark each, because the node marker is the thing to look at and a
# cluttered map makes five small rings hard to find.
# Offsets clear the node's own reserved 3x3 by at least one tile, or place()
# rejects them and the map comes out with no landmarks at all - which is what
# the first run of this script did.
LANDMARKS = [
    ("nature", ROCK,  2, 3,  2, -1),   # 1 the old well: stones round a hole
    ("nature", PINE,  2, 3,  2, -1),   # 2 flooded catacombs: the treeline
    ("house",  TORII, 3, 2, -1,  2),   # 3 broken shrine: its gate still stands
    ("nature", ROCK,  2, 3,  2, -1),   # 4 cursed depths
    ("nature", ROCK,  2, 3, -4, -1),   # 5 flame core
]


def world(seed=11):
    import random
    rng = random.Random(seed)
    sets = build_tilesets()
    floor, nature, house = sets["floor"], sets["nature"], sets["house"]

    ground = Layer("ground", WIDTH, HEIGHT)
    decor = Layer("decor", WIDTH, HEIGHT)      # left empty, for hand editing
    props = Layer("props", WIDTH, HEIGHT)
    overhead = Layer("overhead", WIDTH, HEIGHT)

    for y in range(HEIGHT):
        for x in range(WIDTH):
            ground.put(x, y, floor.gid(*GRASS))

    # The node tiles and one tile around each are kept clear, so nothing is
    # ever planted under a marker or its focus ring.
    reserved = set()
    for nx, ny in NODES:
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                reserved.add((nx + dx, ny + dy))

    def place(tileset, tile, w, h, x, y):
        if any((x + dx, y + dy) in reserved for dy in range(h) for dx in range(w)):
            return False
        if not props.free(x, y, w, h):
            return False
        props.stamp(tileset, x, y, tile[0], tile[1], w, h)
        return True

    by_name = {"nature": nature, "house": house, "floor": floor}
    for (nx, ny), (which, tile, w, h, dx, dy) in zip(NODES, LANDMARKS):
        place(by_name[which], tile, w, h, nx + dx, ny + dy)

    # A treeline along the top and bottom edges, to frame the trail rather than
    # to fill the map. The middle band is left open: that is where the nodes
    # are, and where the player's eye has to travel.
    for _ in range(14):
        x = rng.randrange(0, WIDTH - 2)
        y = rng.choice([0, HEIGHT - 3])
        place(nature, rng.choice(TREES), 2, 3, x, y)

    # Single-tile scatter everywhere that is still empty, for texture.
    for _ in range(70):
        x, y = rng.randrange(WIDTH), rng.randrange(HEIGHT)
        place(nature, rng.choice(BUSHES + TUFTS + FLOWERS), 1, 1, x, y)

    # Tiled writes an object's y down from the top, and libGDX flips it to y-up
    # on load. So these are written in Tiled's convention, unflipped - NODES is
    # already rows-from-the-top, which is what the layers use too. Flipping
    # here as well was the first version, and it put every node on the wrong
    # side of the map in a way that looked deliberate.
    objects = []
    for i, (nx, ny) in enumerate(NODES, start=1):
        px = nx * TILE + TILE // 2
        py = ny * TILE + TILE // 2
        objects.append(("STAGE", px, py, str(i)))

    path = os.path.join(OUT, "world.tmx")
    write_tmx(path, WIDTH, HEIGHT,
              [floor, nature, house], [ground, decor, props, overhead],
              objects=objects)
    print("  world.tmx  %dx%d tiles (%dx%d px), 3 tilesets, 4 layers, %d nodes"
          % (WIDTH, HEIGHT, WIDTH * TILE, HEIGHT * TILE, len(objects)))
    return path


if __name__ == "__main__":
    world()
