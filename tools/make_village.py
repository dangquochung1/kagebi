#!/usr/bin/env python3
"""Generate assets/maps/home.tmx: the inside of the player's house.

The village outside it is the island of the Sunnyside World pack, built by
tools/make_island.py. This script used to build that map too, from the Top-Down
Character Home pack's garden; the island replaced it, and the house on the
island is that same pack's house, so what is left here is its interior - the
room the front door opens onto.

WHY THIS IS A SCRIPT AND NOT A HAND-DRAWN FILE. The pack ships the interior as
a Tiled map already, and it is better than anything that would be drawn here -
but it cannot be used as it is:

  * it is an INFINITE map. libGDX's TmxMapLoader has no notion of a <chunk>;
    it reads <data>, finds nothing, and draws an empty map. The scene has to be
    flattened onto a fixed grid first.
  * its layers carry the pack's own names, and the game draws by role -
    ground, dressing, decor, walls, props, overhead. See gen/TiledRooms.java.

WHAT BLOCKS. Not layers, and not whole tiles. The pack draws a partition as a
three-pixel line along the edge of a tile and a rug in the same layer as the
furniture on it, so nothing that works by layer name or by tile can say what a
player walks into. It is read off the pixels instead: each tile is cut into 4px
cells, a cell is solid where there is art in it, and the solid cells go into
the map as the rectangles of an object layer called `collision` - which
gen/TiledRooms.java reads, and which Tiled shows once that layer is made
visible. Walls and furniture block; floors, windows and rugs do not.

    python tools/make_village.py

Like every generated map here, `decor` is yours: draw into it in Tiled and
write_tmx carries it across a regeneration, tilesets you added included.
Everything else in the file is overwritten, `collision` included - to change
what blocks, change the rules here rather than the rectangles.

tools/make_island.py imports the pack reader below (Pack, norm and the paths)
to lift the house out of the pack's exterior scene.
"""

import collections
import os
import re
import shutil
import sys
import xml.etree.ElementTree as ET

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from make_maps import OUT, ROOT, TILE, Layer, Tileset, write_tmx  # noqa: E402

PACK = os.path.join(ROOT, "assets", "packs", "homeassets", "Tiled_files")
HOME_TILES = os.path.join(ROOT, "assets", "gfx", "tiles", "home")
HOME_REL = "../gfx/tiles/home/"

# Tiled keeps flips in the top four bits of a gid. They are part of the art, so
# they are masked off to find which tileset a gid belongs to, put straight back
# afterwards, and applied to a tile's collision shape as well as to its picture.
FLIP_H = 0x80000000
FLIP_V = 0x40000000
FLIP_D = 0x20000000
FLIP_BITS = 0xF0000000

# ---- what blocks -------------------------------------------------------------

# Collision cells, the size gen/CollisionGrid.java keeps them.
CELL = 4
PER = TILE // CELL
# A cell is solid when at least this many of its sixteen pixels are art. A
# quarter rather than a half, because the thinnest thing in the pack - a
# partition three pixels wide - must never leak: a wall a player slips through
# is worse than a table that stops them a pixel early.
SOLID_PIXELS = 4
# Alpha from which a pixel counts as art. The pack is nearly all 0 or 255; the
# few hundred pixels in between are soft edges.
OPAQUE = 128

# The player's collision box (entity/Player.BODY). The checks at the bottom
# walk this body through the house.
BODY = 12
# How much of the house a player must be able to walk, in tiles of floor. The
# first version of this script let them reach 24, most of the hall being
# blocked by the rug; the rooms the pack actually leaves open come to more.
HALL_TILES = 40

# What a pack layer is.
FLAT = "flat"            # drawn under the player, blocks nothing
SOLID = "solid"          # drawn under the player, blocks where it is drawn
FURNITURE = "furniture"  # solid, unless it is a rug

ROLE_ORDER = ["ground", "dressing", "decor", "walls", "props", "overhead"]


def norm(name):
    """The asset tree's filename rule, as tools/build_assets.py states it.

    Repeated here rather than imported because this script has to name the
    sheets the same way that one does - the .tmx points at a path - and the two
    must not be able to drift into calling the same file two things.
    """
    stem, ext = os.path.splitext(name)
    stem = re.sub(r"[^A-Za-z0-9]+", "_", stem).strip("_").lower()
    return re.sub(r"_+", "_", stem) + ext.lower()


def install_tilesets():
    """Copy the pack's sheets into the asset tree, under their tidy names.

    build_assets.py does this too, and must: assets/gfx/ is not in git and a
    clone rebuilds it from the packs. This is here as well so that running only
    this script produces a house that loads, which is what anyone iterating on
    the map is actually doing.
    """
    os.makedirs(HOME_TILES, exist_ok=True)
    for name in sorted(os.listdir(PACK)):
        if name.lower().endswith(".png"):
            shutil.copyfile(os.path.join(PACK, name),
                            os.path.join(HOME_TILES, norm(name)))


# --------------------------------------------------------------------------
# Reading the pack
# --------------------------------------------------------------------------

class Pack:
    """One of the art pack's .tmx files, read into plain dictionaries."""

    def __init__(self, path):
        root = ET.parse(path).getroot()
        # (firstgid, count, image, columns) per tileset, in file order. The
        # exterior declares ground_grass_details twice at two firstgids, so
        # these are collapsed by image below rather than trusted one to one.
        self.sets = []
        self.animations = collections.defaultdict(dict)
        for ts in root.findall("tileset"):
            image = os.path.basename(ts.find("image").get("source"))
            self.sets.append((int(ts.get("firstgid")), int(ts.get("tilecount")),
                              image, int(ts.get("columns"))))
            for tile in ts.findall("tile"):
                if tile.find("animation") is not None:
                    self.animations[image][int(tile.get("id"))] = tile
        self.layers = []
        for layer in root.findall("layer"):
            cells = {}
            for chunk in layer.find("data").findall("chunk"):
                cx = int(chunk.get("x"))
                cy = int(chunk.get("y"))
                cw = int(chunk.get("width"))
                for i, value in enumerate(chunk.text.replace("\n", "").split(",")):
                    value = value.strip()
                    if value and int(value):
                        cells[(cx + i % cw, cy + i // cw)] = int(value)
            self.layers.append((layer.get("name"), cells))
        self._sheets = {}
        self._alpha = {}

    def resolve(self, raw):
        """(image, local tile index, flip flags) for one raw gid."""
        flags = raw & FLIP_BITS
        gid = raw & ~FLIP_BITS
        for first, count, image, _ in self.sets:
            if first <= gid < first + count:
                return image, gid - first, flags
        raise SystemExit("gid %d belongs to no tileset in the pack" % gid)

    def bounds(self):
        xs = [p[0] for _, cells in self.layers for p in cells]
        ys = [p[1] for _, cells in self.layers for p in cells]
        return min(xs), min(ys), max(xs), max(ys)

    def tilesets(self, firstgid):
        """One output tileset per unique pack image, animations carried over."""
        out = collections.OrderedDict()
        for _, _, image, _ in self.sets:
            if image in out:
                continue
            filename = norm(image)
            ts = Tileset(os.path.splitext(filename)[0], filename, firstgid,
                         folder=HOME_TILES, rel=HOME_REL + filename,
                         tiles=[self.animations[image][k]
                                for k in sorted(self.animations[image])])
            out[image] = ts
            firstgid += ts.count
        return out

    def columns(self, image):
        """How many tiles wide that sheet is, which is what a row number means."""
        for _, _, name, columns in self.sets:
            if name == image:
                return columns
        raise SystemExit("the pack never uses %s" % image)

    def sheet_position(self, raw):
        """(image, column, row) of a gid's tile on its sheet."""
        image, local, _ = self.resolve(raw)
        columns = self.columns(image)
        return image, local % columns, local // columns

    def alpha(self, image, local):
        """One tile's alpha channel, sixteen rows of sixteen values."""
        key = (image, local)
        if key not in self._alpha:
            if image not in self._sheets:
                self._sheets[image] = Image.open(os.path.join(PACK, image)).convert("RGBA")
            columns = self.columns(image)
            tx, ty = local % columns, local // columns
            tile = self._sheets[image].crop((tx * TILE, ty * TILE,
                                             (tx + 1) * TILE, (ty + 1) * TILE))
            values = list(tile.getchannel("A").getdata())
            self._alpha[key] = [values[r * TILE:(r + 1) * TILE] for r in range(TILE)]
        return self._alpha[key]

    def art(self, raw):
        """How many pixels of a tile are drawn."""
        image, local, _ = self.resolve(raw)
        return sum(1 for row in self.alpha(image, local) for v in row if v >= OPAQUE)

    def shape(self, raw):
        """The 4px cells of a tile that block, as placed: PER rows of PER, y down."""
        image, local, flags = self.resolve(raw)
        a = self.alpha(image, local)
        cells = [[sum(1 for y in range(cy * CELL, (cy + 1) * CELL)
                      for x in range(cx * CELL, (cx + 1) * CELL)
                      if a[y][x] >= OPAQUE) >= SOLID_PIXELS
                  for cx in range(PER)]
                 for cy in range(PER)]
        return flip(cells, flags)


def flip(cells, flags):
    """A tile's cells the way Tiled places it: diagonal, then across, then down."""
    if flags & FLIP_D:
        cells = [list(row) for row in zip(*cells)]
    if flags & FLIP_H:
        cells = [list(reversed(row)) for row in cells]
    if flags & FLIP_V:
        cells = list(reversed(cells))
    return cells


# --------------------------------------------------------------------------
# What the game will build
# --------------------------------------------------------------------------

class Solid:
    """The collision grid the game builds from a map: 4px cells, y down."""

    def __init__(self, width, height):
        self.width = width
        self.height = height
        self.cw = width * PER
        self.ch = height * PER
        self.cells = bytearray(self.cw * self.ch)

    def stamp(self, tx, ty, shape):
        if not (0 <= tx < self.width and 0 <= ty < self.height):
            return
        for cy in range(PER):
            for cx in range(PER):
                if shape[cy][cx]:
                    self.cells[(ty * PER + cy) * self.cw + tx * PER + cx] = 1

    def rectangles(self):
        """The solid cells as few rectangles, in pixels, y down - Tiled's space."""
        rects, open_runs = [], {}
        for y in range(self.ch + 1):
            runs = set()
            if y < self.ch:
                x = 0
                while x < self.cw:
                    if self.cells[y * self.cw + x]:
                        start = x
                        while x < self.cw and self.cells[y * self.cw + x]:
                            x += 1
                        runs.add((start, x))
                    else:
                        x += 1
            for run in [r for r in open_runs if r not in runs]:
                top = open_runs.pop(run)
                rects.append((run[0] * CELL, top * CELL,
                              (run[1] - run[0]) * CELL, (y - top) * CELL))
            for run in runs:
                open_runs.setdefault(run, y)
        return sorted(rects, key=lambda r: (r[1], r[0]))


class Walker:
    """Where a player-sized body fits, and where it can get to on foot.

    The same test gen/CollisionGrid.overlaps makes, over the same cells: a box
    BODY pixels square around the point, its far edges a pixel short. Points
    are STEP pixels apart, which is finer than anything the pack draws.
    """

    STEP = 2

    def __init__(self, solid):
        self.cw, self.ch = solid.cw, solid.ch
        self.wpx, self.hpx = solid.cw * CELL, solid.ch * CELL
        w = self.cw + 1
        s = [0] * (w * (self.ch + 1))
        for y in range(self.ch):
            run = 0
            for x in range(self.cw):
                run += 1 if solid.cells[y * self.cw + x] else 0
                s[(y + 1) * w + x + 1] = s[y * w + x + 1] + run
        self.sums = s

    def fits(self, px, py):
        half = BODY // 2
        x0, x1 = (px - half) // CELL, (px + half - 1) // CELL
        y0, y1 = (py - half) // CELL, (py + half - 1) // CELL
        if x0 < 0 or y0 < 0 or x1 >= self.cw or y1 >= self.ch:
            return False
        w = self.cw + 1
        s = self.sums
        return (s[(y1 + 1) * w + x1 + 1] - s[y0 * w + x1 + 1]
                - s[(y1 + 1) * w + x0] + s[y0 * w + x0]) == 0

    def reach(self, start):
        """Every point a body can walk to from `start`, which it must fit at."""
        seen = {start}
        queue = [start]
        step = self.STEP
        while queue:
            x, y = queue.pop()
            for n in ((x + step, y), (x - step, y), (x, y + step), (x, y - step)):
                if n not in seen and self.fits(*n):
                    seen.add(n)
                    queue.append(n)
        return seen

    def largest(self):
        """The biggest area a body can walk around in."""
        best, seen = set(), set()
        for y in range(0, self.hpx + 1, self.STEP):
            for x in range(0, self.wpx + 1, self.STEP):
                if (x, y) in seen or not self.fits(x, y):
                    continue
                here = self.reach((x, y))
                seen |= here
                if len(here) > len(best):
                    best = here
        return best

    def floor(self, points):
        """How many tiles of floor a body covers, standing at every one of these."""
        half = BODY // 2
        covered = set()
        for px, py in points:
            for cy in range((py - half) // CELL, (py + half - 1) // CELL + 1):
                for cx in range((px - half) // CELL, (px + half - 1) // CELL + 1):
                    covered.add((cx, cy))
        return len(covered) / float(PER * PER)


class Scene:
    """A pack laid out on a fixed grid, as layers the game knows how to draw."""

    def __init__(self, pack, tilesets, width, height, dx, dy):
        self.pack = pack
        self.tilesets = tilesets
        self.width = width
        self.height = height
        self.minx, self.miny, _, _ = pack.bounds()
        self.dx = dx
        self.dy = dy
        self.made = collections.OrderedDict()
        self.solid = Solid(width, height)
        # Noticed while placing, for the markers.
        self.door_cells = []
        self.rug_cells = []
        self.table_cells = []

    def layer(self, name):
        if name not in self.made:
            self.made[name] = Layer(name, self.width, self.height)
        return self.made[name]

    def put(self, name, x, y, raw, shape=None):
        """A pack tile at its place in the pack's own scene. Returns where it landed."""
        tx, ty = self.dx + x - self.minx, self.dy + y - self.miny
        image, local, flags = self.pack.resolve(raw)
        self.layer(name).put(tx, ty, flags | (self.tilesets[image].firstgid + local))
        if shape is not None:
            self.solid.stamp(tx, ty, shape)
        return tx, ty

    def layers(self):
        return list(self.made.values())


def order(layer):
    for i, role in enumerate(ROLE_ORDER):
        if layer.name == role or layer.name.startswith(role + "_"):
            return i
    raise SystemExit("layer %r plays no role the game draws, so it would be "
                     "loaded and never rendered" % layer.name)


# --------------------------------------------------------------------------
# The interior
# --------------------------------------------------------------------------

# Interior.png rows 16 to 23 are the pack's rugs: the round one under the
# table, the red ones and the runner. Everything else on that sheet is
# furniture, and furniture blocks.
RUG_ROWS = range(16, 24)
# The round table and its four chairs on Interior.png, as (column, row, width,
# height): where --screen home --page 2 puts the player beside it.
TABLE = (8, 9, 5, 4)
# The house's way out: the gap the pack drew in the outline of the bottom wall,
# on walls_floor.png, behind the red doormat. A player arrives standing in
# front of it and leaves from there.
HOUSE_DOOR = {(7, 13), (8, 13), (9, 13)}

INTERIOR_PLAN = [
    ("Floor", "ground", FLAT),
    ("Tile Layer 6", "ground_rugs", FLAT),
    ("Boxes", "dressing_boxes", SOLID),
    # Floor, void and wall in one layer: the walls are the pixels, and the
    # floor tiles among them have none where a player walks.
    ("Walls", "dressing_walls", SOLID),
    ("Windows", "dressing_windows", FLAT),
    ("Objects1", "dressing_objects1", FURNITURE),
    ("Objects2", "dressing_objects2", SOLID),
]


def interior_layers(pack, tilesets, width, height):
    scene = Scene(pack, tilesets, width, height, 0, 0)
    plan = {name: (out, kind) for name, out, kind in INTERIOR_PLAN}
    for pack_name, cells in pack.layers:
        if pack_name not in plan:
            raise SystemExit("interior layer %r is in no plan" % pack_name)
        name, kind = plan[pack_name]
        for (x, y), raw in cells.items():
            image, column, row = pack.sheet_position(raw)
            rug = kind == FURNITURE and image == "Interior.png" and row in RUG_ROWS
            solid = kind == SOLID or (kind == FURNITURE and not rug)
            spot = scene.put(name, x, y, raw, pack.shape(raw) if solid else None)
            if rug:
                scene.rug_cells.append(spot)
            if (pack_name == "Walls" and image == "walls_floor.png"
                    and (column, row) in HOUSE_DOOR):
                scene.door_cells.append(spot)
            if (pack_name == "Objects2" and image == "Interior.png"
                    and TABLE[0] <= column < TABLE[0] + TABLE[2]
                    and TABLE[1] <= row < TABLE[1] + TABLE[3]):
                scene.table_cells.append(spot)
    return scene


def doormat(scene, hall):
    """Where a player stands to leave the house: right in front of its doorway."""
    if not scene.door_cells:
        raise SystemExit("the interior has lost the doorway in its bottom wall")
    xs = [x for x, _ in scene.door_cells]
    door_x = (min(xs) + max(xs) + 1) * TILE // 2
    in_line = [p for p in hall if abs(p[0] - door_x) <= Walker.STEP]
    if not in_line:
        raise SystemExit("nobody can walk up to the doorway in the bottom wall")
    return max(in_line, key=lambda p: (p[1], -abs(p[0] - door_x)))


def rug_spot(scene, hall):
    """Somewhere on the rug beside the table that a player can walk to."""
    if not scene.rug_cells or not scene.table_cells:
        raise SystemExit("the interior has lost its rug or its table")
    half = BODY // 2
    left = min(x for x, _ in scene.rug_cells) * TILE + half
    right = (max(x for x, _ in scene.rug_cells) + 1) * TILE - half
    top = min(y for _, y in scene.rug_cells) * TILE + half
    bottom = (max(y for _, y in scene.rug_cells) + 1) * TILE - half
    table_left = min(x for x, _ in scene.table_cells) * TILE
    table_top = min(y for _, y in scene.table_cells) * TILE
    table_bottom = (max(y for _, y in scene.table_cells) + 1) * TILE
    target = (table_left - half, (table_top + table_bottom) // 2)
    on_rug = [p for p in hall if left <= p[0] <= right and top <= p[1] <= bottom]
    if not on_rug:
        raise SystemExit("no part of the rug can be walked on")
    return min(on_rug, key=lambda p: (p[0] - target[0]) ** 2 + (p[1] - target[1]) ** 2)


# --------------------------------------------------------------------------

def home():
    """The inside of the house: somewhere to walk into and back out of."""
    pack = Pack(os.path.join(PACK, "Interior1.tmx"))
    minx, miny, maxx, maxy = pack.bounds()
    width, height = maxx - minx + 1, maxy - miny + 1
    tilesets = pack.tilesets(1)

    scene = interior_layers(pack, tilesets, width, height)
    layers = scene.layers()
    layers.append(Layer("decor", width, height))
    layers.sort(key=order)

    # The way in and the way back out: as near the doorway in the bottom wall
    # as a player can stand, in the biggest room the house has. Found rather
    # than written down - which rooms a player can walk is whatever the walls'
    # pixels leave open, and a hand-picked tile here is a guess that only shows
    # as wrong when a player walks into a wall.
    walker = Walker(scene.solid)
    hall = walker.largest()
    door = doormat(scene, hall)
    rug = rug_spot(scene, hall)
    floor = walker.floor(hall)
    if floor < HALL_TILES:
        raise SystemExit("only %.0f tiles of the house can be walked, fewer than "
                         "%d: something is blocking a room that is open in the art"
                         % (floor, HALL_TILES))
    print("    %.0f tiles of floor in the house, door and rug on it" % floor)

    shapes = scene.solid.rectangles()
    write_tmx(os.path.join(OUT, "home.tmx"), width, height,
              list(tilesets.values()), layers, tiles_rel=HOME_REL,
              objects=[("spawn", door[0], door[1], "entry"),
                       ("spawn", door[0], door[1], "door"),
                       ("spawn", rug[0], rug[1], "rug")],
              shapes=shapes)
    report("home.tmx", width, height, list(tilesets.values()), layers, shapes)


def report(name, width, height, sets, layers, shapes):
    animated = sum(len(ts.tiles) for ts in sets)
    print("  %-12s %dx%d tiles (%dx%d px), %d tilesets, %d layers, %d animated, "
          "%d collision rectangles"
          % (name, width, height, width * TILE, height * TILE,
             len(sets), len(layers), animated, len(shapes)))


if __name__ == "__main__":
    print("generating the house")
    install_tilesets()
    home()
