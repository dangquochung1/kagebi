#!/usr/bin/env python3
"""Generate assets/maps/village.tmx and assets/maps/home.tmx.

The village is two places sharing one map. Left of a fence, in a column, are
the three villagers' houses and the torii that leads to the world map - the old
village, rearranged. Everything right of the fence is the player's own home:
the exterior scene from the Top-Down Character Home pack, tile for tile, with
its falling leaves, its chimney smoke, its birds and its cat still moving.
home.tmx is that pack's interior, which is what the front door opens onto.

WHY THIS IS A SCRIPT AND NOT A HAND-DRAWN FILE. The pack ships both scenes as
Tiled maps already, and they are better than anything that would be drawn
here - but they cannot be used as they are:

  * they are INFINITE maps. libGDX's TmxMapLoader has no notion of a <chunk>;
    it reads <data>, finds nothing, and draws an empty map. The scene has to be
    flattened onto a fixed grid first.
  * they are twenty-two layers with the pack's own names, and the game draws by
    role - ground, dressing, decor, walls, props, overhead. See
    gen/TiledRooms.java.

WHAT DRAWS OVER THE PLAYER. Every layer of the pack keeps its own place in the
stacking order, under the player, except for three things: the roof with its
smoke, the birds, and the canopy of each tree - every row of a tree but the one
its trunk stands on. Nothing else is lifted. The first version of this script
lifted the top of every object in a layer and left its bottom row under the
player, and that re-stacked the pack: the upper rows of the rug were drawn over
the table standing on it, and over the player walking across it.

WHAT BLOCKS. Not layers, and not whole tiles. The pack draws a partition as a
three-pixel line along the edge of a tile, its garden gate as two posts at the
edges of two tiles, and a rug in the same layer as the furniture on it, so
nothing that works by layer name or by tile can say what a player walks into.
It is read off the pixels instead: each tile is cut into 4px cells, a cell is
solid where there is art in it, and the solid cells go into the map as the
rectangles of an object layer called `collision` - which gen/TiledRooms.java
reads, and which Tiled shows once that layer is made visible. Which art blocks
at all is the tables below: walls, fences and furniture do; ground, grass,
rugs, the windows painted on a wall and the roof do not; a tree blocks at the
foot of its trunk; a thing smaller than SMALL pixels, like a pebble, does not.

    python tools/make_village.py

Like every generated map here, `decor` is yours: draw into it in Tiled and
write_tmx carries it across a regeneration, tilesets you added included.
Everything else in these two files is overwritten, `collision` included - to
change what blocks, change the rules here rather than the rectangles.
"""

import collections
import os
import random
import re
import shutil
import sys
import xml.etree.ElementTree as ET

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from make_maps import (  # noqa: E402
    OUT, ROOT, TILE, Layer, Tileset, build_tilesets, write_tmx, HOUSES, TORII,
)

PACK = os.path.join(ROOT, "homeassets", "Tiled_files")
HOME_TILES = os.path.join(ROOT, "assets", "gfx", "tiles", "home")
HOME_REL = "../gfx/tiles/home/"
OVERWORLD_REL = "../gfx/tiles/overworld/"

# Tiled keeps flips in the top four bits of a gid. They are part of the art -
# half the pack's bushes are mirrored - so they are masked off to find which
# tileset a gid belongs to, put straight back afterwards, and applied to a
# tile's collision shape as well as to its picture.
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
# is worse than a bush that stops them a pixel early.
SOLID_PIXELS = 4
# Alpha from which a pixel counts as art. The pack is nearly all 0 or 255; the
# few hundred pixels in between are soft edges.
OPAQUE = 128
# A placed object with less art than this does not block: a pebble, or a tile
# the pack left in an object layer with nothing drawn on it - two of which sat
# in front of the house as an invisible wall until this rule existed.
SMALL = 100

# The player's collision box (entity/Player.BODY) and the distances HubScreen
# answers a key press within. The checks at the bottom walk this body to these
# distances, so a marker that passes them is one the game can use.
BODY = 12
GATE_RANGE = 24
DOOR_RANGE = 26
TALK_RANGE = 22
# How much of the house a player must be able to walk, in tiles of floor. The
# first version of this script let them reach 24, most of the hall being
# blocked by the rug; the rooms the pack actually leaves open come to more.
HALL_TILES = 40

# What a pack layer is.
FLAT = "flat"            # drawn under the player, blocks nothing
SOLID = "solid"          # drawn under the player, blocks where it is drawn
OBJECTS = "objects"      # sorted object by object; see place_objects
FURNITURE = "furniture"  # solid, unless it is a rug

# ---- the village ---------------------------------------------------------------

# The village column, left of the fence. Nine tiles: a house is four wide and
# the villager standing at its door needs room either side.
VILLAGE_W = 9
FENCE_X = VILLAGE_W
HOME_X = VILLAGE_W + 1
HOME_Y = 1
# The home scene is 27x20; one spare row above and below it.
WIDTH, HEIGHT = HOME_X + 27, 22

HOUSE_ROWS = [1, 7, 13]
TORII_ROW = 18
# Where the fence between the villagers and the home opens: three rows, level
# with the middle house.
GAP_ROW = HOUSE_ROWS[1] + 2

# The pack's fence, by tile column and row in exterior.png. It drew a closed
# run round the garden, so it has a left side and a right side and four
# corners; the divider down the middle of the village is the home's west wall,
# which is the left side with a cap at each end. Picking the commonest fence
# tile instead gives a column of horizontal rails, which reads as a ladder.
FENCE_TOP = (11, 2)
FENCE_SIDE = (11, 3)
FENCE_FOOT = (11, 4)
# The garden's open gate is two posts, and this is the left one on
# exterior.png. The gateway marker goes between them.
GATE_POST = (14, 1)
# The front door: the only door the pack drew, animated, in its House_wall
# layer. Read off the map rather than written down as a position.
DOOR_SHEET = "Doors_windows_animation.png"

# The trees on exterior.png, as (column, row, width, height) on the sheet. A
# tile from one of these, or from any of Trees_animation.png, is part of a tree.
TREES = [(12, 14, 4, 4), (14, 21, 2, 3), (1, 23, 8, 5), (9, 24, 4, 4), (12, 43, 4, 5)]
ANIMATED_TREES = "Trees_animation.png"

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
    this script produces a village that loads, which is what anyone iterating
    on the map is actually doing.
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

    def gid(self, image, column, row):
        """A raw gid for a tile picked off one of the sheets by where it sits."""
        for first, _, name, columns in self.sets:
            if name == image:
                return first + row * columns + column
        raise SystemExit("the pack never uses %s" % image)

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

    def shape(self, raw, trunk=False):
        """The 4px cells of a tile that block, as placed: PER rows of PER, y down.

        A trunk keeps only its lower half. The row a tree stands on carries the
        bottom of the canopy as well as the trunk, and a player walking round a
        tree should be stopped by the trunk rather than by leaves over their head.
        """
        image, local, flags = self.resolve(raw)
        a = self.alpha(image, local)
        cells = [[sum(1 for y in range(cy * CELL, (cy + 1) * CELL)
                      for x in range(cx * CELL, (cx + 1) * CELL)
                      if a[y][x] >= OPAQUE) >= SOLID_PIXELS
                  for cx in range(PER)]
                 for cy in range(PER)]
        cells = flip(cells, flags)
        if trunk:
            cells[:PER // 2] = [[False] * PER for _ in range(PER // 2)]
        return cells


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
        # What the collision layer will say, and what blocks by whole tiles
        # anyway - a props layer, or a villager standing on one. Only the first
        # is written out; both are walked.
        self.cells = bytearray(self.cw * self.ch)
        self.whole = bytearray(self.cw * self.ch)

    def stamp(self, tx, ty, shape):
        if not (0 <= tx < self.width and 0 <= ty < self.height):
            return
        for cy in range(PER):
            for cx in range(PER):
                if shape[cy][cx]:
                    self.cells[(ty * PER + cy) * self.cw + tx * PER + cx] = 1

    def block(self, tx, ty):
        """A whole tile the game blocks without being told here."""
        if not (0 <= tx < self.width and 0 <= ty < self.height):
            return
        for cy in range(PER):
            for cx in range(PER):
                self.whole[(ty * PER + cy) * self.cw + tx * PER + cx] = 1

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
                i = y * self.cw + x
                run += 1 if (solid.cells[i] or solid.whole[i]) else 0
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

    def snap(self, point, radius=24):
        """The nearest point to this one a body fits at."""
        px, py = point
        bx, by = px - px % self.STEP, py - py % self.STEP
        best = None
        for dy in range(-radius, radius + 1, self.STEP):
            for dx in range(-radius, radius + 1, self.STEP):
                x, y = bx + dx, by + dy
                if self.fits(x, y):
                    d = (x - px) ** 2 + (y - py) ** 2
                    if best is None or d < best[0]:
                        best = (d, x, y)
        if best is None:
            raise SystemExit("no room for a player within %dpx of %s" % (radius, point))
        return best[1], best[2]

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


def near(points, target, radius):
    tx, ty = target
    return any((x - tx) ** 2 + (y - ty) ** 2 < radius * radius for x, y in points)


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
        # Noticed while placing, for the markers and the scatter.
        self.door_cells = []
        self.gate_post = None
        self.singles = []
        self.rug_cells = []
        self.table_cells = []

    def layer(self, name):
        if name not in self.made:
            self.made[name] = Layer(name, self.width, self.height)
        return self.made[name]

    def put(self, name, x, y, raw, shape=None):
        """A pack tile at its place in the pack's own scene. Returns where it landed."""
        return self.place(name, self.dx + x - self.minx, self.dy + y - self.miny,
                          raw, shape)

    def place(self, name, tx, ty, raw, shape=None):
        """A pack tile at a tile of this map, blocking as `shape` says."""
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
# The exterior
# --------------------------------------------------------------------------

# Which of the game's layers each of the pack's goes into, and what it is. A
# pack name listed twice is the pack's own duplicate; both entries are kept,
# because they hold different tiles and merging them would silently drop one.
EXTERIOR_PLAN = [
    ("Ground", "ground", FLAT),
    ("Spots", "ground_spots", FLAT),
    ("Road", "ground_road", FLAT),
    ("Plates", "ground_plates", FLAT),
    ("Grass", "dressing", FLAT),
    ("Grass_detail6", "dressing_detail6", FLAT),
    ("Grass_details3", "dressing_details3a", FLAT),
    ("Grass_details3", "dressing_details3b", FLAT),
    ("Grass_details4", "dressing_details4", FLAT),
    ("Grass_details5", "dressing_details5", FLAT),
    ("Objects4", "objects4", OBJECTS),
    ("Objects1", "objects1", OBJECTS),
    ("Objects2", "objects2", OBJECTS),
    ("Fence", "dressing_fence", SOLID),
    # The door is not a hole in the wall: HubScreen treats it the way it
    # treats the torii, as somewhere to stand and press a key.
    ("House_wall", "dressing_house", SOLID),
    # Painted on the wall, which already blocks. Two of these tiles hang below
    # the house with nothing drawn on them at all.
    ("windows1", "dressing_windows1", FLAT),
    ("windows2", "dressing_windows2", FLAT),
    ("House_roof", "overhead_roof", FLAT),
    ("Objects3", "objects3", OBJECTS),
    ("Grass_top_details", "dressing_top", FLAT),
    ("Birds", "overhead_birds", FLAT),
    ("cat", "dressing_cat", FLAT),
]


def exterior_layers(pack, tilesets):
    scene = Scene(pack, tilesets, WIDTH, HEIGHT, HOME_X, HOME_Y)
    used = collections.Counter()
    for pack_name, cells in pack.layers:
        matches = [p for p in EXTERIOR_PLAN if p[0] == pack_name]
        if not matches:
            raise SystemExit("exterior layer %r is in no plan, so it would "
                             "vanish without a word" % pack_name)
        name, kind = matches[min(used[pack_name], len(matches) - 1)][1:]
        used[pack_name] += 1
        if kind == OBJECTS:
            place_objects(scene, pack, name, cells)
            continue
        for (x, y), raw in cells.items():
            spot = scene.put(name, x, y, raw, pack.shape(raw) if kind == SOLID else None)
            image, column, row = pack.sheet_position(raw)
            if pack_name == "House_wall" and image == DOOR_SHEET:
                scene.door_cells.append(spot)
            if pack_name == "Fence" and image == "exterior.png" and (column, row) == GATE_POST:
                scene.gate_post = spot
    return scene


def objects(pack, cells):
    """A layer's tiles, gathered into the things the artist placed.

    Two neighbouring cells belong to one thing when their tiles are neighbours
    on the sheet in the same direction - mirrored, if the thing is. A tree is a
    block of the sheet stamped down whole, and two different things stamped
    side by side almost never line up like that by accident.
    """
    info = {p: pack.resolve(raw) for p, raw in cells.items()}
    seen, groups = set(), []
    for start in sorted(cells, key=lambda p: (p[1], p[0])):
        if start in seen:
            continue
        group, queue = [start], [start]
        seen.add(start)
        while queue:
            x, y = queue.pop()
            image, local, flags = info[(x, y)]
            columns = pack.columns(image)
            across = -1 if flags & FLIP_H else 1
            down = -1 if flags & FLIP_V else 1
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = (x + dx, y + dy)
                if n in seen or n not in info:
                    continue
                image2, local2, flags2 = info[n]
                if (image2 == image and flags2 == flags
                        and local2 // columns == local // columns + down * dy
                        and local2 % columns == local % columns + across * dx):
                    seen.add(n)
                    group.append(n)
                    queue.append(n)
        groups.append(group)
    return groups


def is_tree(pack, raw):
    image, column, row = pack.sheet_position(raw)
    if image == ANIMATED_TREES:
        return True
    return image == "exterior.png" and any(
        x <= column < x + w and y <= row < y + h for x, y, w, h in TREES)


def place_objects(scene, pack, name, cells):
    """An object layer, one placed thing at a time.

    A tree's canopy goes over the player and only the foot of its trunk
    blocks. Something too small to walk into blocks nothing. Everything else -
    a rock, a crate, a well, a stump, a bush - stays under the player and
    blocks wherever it is drawn.
    """
    for group in objects(pack, cells):
        raws = [cells[p] for p in group]
        if sum(pack.art(raw) for raw in raws) < SMALL:
            for x, y in group:
                scene.put("dressing_" + name, x, y, cells[(x, y)])
            continue
        if is_tree(pack, raws[0]):
            foot = max(y for _, y in group)
            for x, y in group:
                raw = cells[(x, y)]
                if y == foot:
                    scene.put("dressing_" + name, x, y, raw, pack.shape(raw, trunk=True))
                else:
                    scene.put("overhead_" + name, x, y, raw)
            continue
        for x, y in group:
            raw = cells[(x, y)]
            scene.put("dressing_" + name, x, y, raw, pack.shape(raw))
        if len(group) == 1:
            scene.singles.append(raws[0])


# --------------------------------------------------------------------------
# The village column
# --------------------------------------------------------------------------

def add_village(scene, pack):
    """Three houses, a torii and a fence, on the same grass as the home.

    Every position here is in pixels with y running down, which is how Tiled
    writes an object; HubScreen gets them the right way up because libGDX flips
    object coordinates on load.
    """
    turf(scene, pack)
    house = scene.overworld["house"]
    props = scene.layer("props_village")
    spawns = []
    for i, row in enumerate(HOUSE_ROWS):
        hx, hy = HOUSES[i]
        props.stamp(house, 2, row, hx, hy, 4, 3)
        block(scene, 2, row, 4, 3)
        # On the ground in front of the door, which is the middle of a
        # four-wide house. A row lower than the house so the villager is
        # standing outside it rather than in the dark of its own doorway;
        # HubScreen blocks the tile they stand on.
        spawns.append(("villager%d" % (i + 1), 4 * TILE, (row + 4) * TILE))
        scene.solid.block(4, row + 3)

    tx, ty = TORII
    props.stamp(house, 3, TORII_ROW, tx, ty, 3, 2)
    block(scene, 3, TORII_ROW, 3, 2)
    spawns.append(("gate", 4 * TILE + TILE // 2, (TORII_ROW + 2) * TILE - 8))

    # The fence runs the height of the map with one gap to walk through, level
    # with the middle house. The pack's own fence tiles, reused from the run it
    # drew round the player's garden, and blocking where they are drawn.
    for y in range(HEIGHT):
        if GAP_ROW <= y <= GAP_ROW + 2:
            continue
        top = y == 0 or y == GAP_ROW + 3
        foot = y == HEIGHT - 1 or y == GAP_ROW - 1
        column, row = FENCE_TOP if top else FENCE_FOOT if foot else FENCE_SIDE
        raw = pack.gid("exterior.png", column, row)
        scene.place("dressing_fence_divider", FENCE_X, y, raw, pack.shape(raw))
    scatter(scene, pack)
    return spawns


def block(scene, x, y, w, h):
    for dy in range(h):
        for dx in range(w):
            scene.solid.block(x + dx, y + dy)


def scatter(scene, pack, seed=11):
    """A handful of the pack's own bushes over the village column.

    Bare lawn beside a garden as dense as the pack's reads as unfinished
    ground rather than as open space, and anything drawn here that is not from
    this pack would show as a second artist. So the scatter is sampled from the
    single-tile things the home half already has.
    """
    singles = sorted(set(scene.singles))
    if not singles:
        return
    rng = random.Random(seed)
    houses = scene.made["props_village"]
    placed = scene.layer("dressing_scatter")
    for _ in range(60):
        x = rng.randrange(0, FENCE_X)
        y = rng.randrange(0, HEIGHT)
        i = y * WIDTH + x
        # Clear of the houses and the torii, of the tile below each of them
        # where a villager stands, and of the way through the fence.
        by_house = any(houses.data[j] for j in neighbourhood(x, y))
        by_gap = x >= FENCE_X - 2 and GAP_ROW - 1 <= y <= GAP_ROW + 3
        if houses.data[i] or by_house or by_gap or placed.data[i]:
            continue
        raw = rng.choice(singles)
        scene.place("dressing_scatter", x, y, raw, pack.shape(raw))


def neighbourhood(x, y):
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            nx, ny = x + dx, y + dy
            if 0 <= nx < WIDTH and 0 <= ny < HEIGHT:
                yield ny * WIDTH + nx


def turf(scene, pack):
    """Ground and grass over every cell that has neither.

    A cell rather than a rectangle, and that is the whole subtlety. The pack
    leaves grass off its roads on purpose so the dirt underneath shows through,
    so a fill over any region that includes a road paves it over with lawn -
    while a fill over only the village column leaves the home's own top and
    bottom rows bare, because the scene's outermost rows carry nothing but a
    few blades of scattered detail. Filling what is empty gets both right.

    The grass is a ground layer, so that it lies under everything the pack
    stands on it rather than being painted over the edge of a bush.
    """
    dirt = base_tile(scene, pack, "Ground")
    grass = base_tile(scene, pack, "Grass")
    ground = scene.layer("ground")
    under = scene.layer("dressing")
    turfed = scene.layer("ground_turf")
    for y in range(HEIGHT):
        for x in range(WIDTH):
            i = y * WIDTH + x
            if ground.data[i] or under.data[i]:
                continue
            ground.put(x, y, dirt)
            turfed.put(x, y, grass)


def base_tile(scene, pack, layer_name):
    """The commonest tile of one of the pack's layers, as an output gid."""
    cells = next(c for name, c in pack.layers if name == layer_name)
    raw = collections.Counter(cells.values()).most_common(1)[0][0]
    image, local, flags = pack.resolve(raw)
    return flags | (scene.tilesets[image].firstgid + local)


def front_door(scene, walker):
    """The doormat, and where a player coming home arrives.

    The mat is the first place below the door a player fits, found by walking
    a body down from the door's bottom edge. A door is part of a wall by
    nature, so a marker on the door itself would be inside solid art, and "are
    you close enough to knock" would be measuring a distance nobody can close.
    """
    if not scene.door_cells:
        raise SystemExit("the pack's house has no door tile in House_wall")
    xs = [x for x, _ in scene.door_cells]
    ys = [y for _, y in scene.door_cells]
    x = (min(xs) + max(xs) + 1) * TILE // 2
    y = (max(ys) + 1) * TILE + BODY // 2
    while not walker.fits(x, y):
        y += 1
        if y > walker.hpx:
            raise SystemExit("nowhere below the front door fits a player")
    mat = walker.snap((x, y))
    return mat, walker.snap((mat[0], mat[1] + 24))


def gateway(scene, walker):
    """The middle of the garden's open gate, which must have room for a player."""
    if scene.gate_post is None:
        raise SystemExit("the pack's fence has no open gate in it any more")
    tx, ty = scene.gate_post
    return walker.snap(((tx + 1) * TILE, ty * TILE + TILE // 2), radius=6)


def check_village(walker, spawns):
    """Every marker must be somewhere a player can walk up to, from the door.

    A map that seals the gate, or that puts an invisible wall across the path,
    looks perfectly correct in a render: the art is all there and the mistake
    is in what does not draw. So this runs before the file is written, walking
    a body the size of the player's to every place the village needs reaching.
    """
    at = {name: (x, y) for name, x, y in spawns}
    reach = walker.reach(at["entry"])
    door_x, door_y = at["door"]
    wanted = [
        ("the garden gateway", at["gateway"], 3),
        ("the torii", at["gate"], GATE_RANGE),
        ("the front door", at["door"], DOOR_RANGE),
        ("the front of the house, left of the door", (door_x - 3 * TILE, door_y), 3),
    ]
    for i in (1, 2, 3):
        vx, vy = at["villager%d" % i]
        wanted.append(("villager %d" % i, (vx, vy - 8), TALK_RANGE))
    missing = [what for what, target, radius in wanted if not near(reach, target, radius)]
    if missing:
        raise SystemExit("from the front door a player cannot reach " + "; ".join(missing))
    print("    %.0f tiles of floor reachable from the front door, every marker reachable"
          % walker.floor(reach))


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

def village():
    pack = Pack(os.path.join(PACK, "Exterior.tmx"))
    overworld = build_tilesets()
    for ts in overworld.values():
        ts.rel = OVERWORLD_REL + ts.filename
    firstgid = max(ts.firstgid + ts.count for ts in overworld.values())
    tilesets = pack.tilesets(firstgid)

    scene = exterior_layers(pack, tilesets)
    scene.overworld = overworld
    spawns = add_village(scene, pack)
    layers = scene.layers()
    layers.append(Layer("decor", WIDTH, HEIGHT))
    layers.sort(key=order)

    walker = Walker(scene.solid)
    mat, entry = front_door(scene, walker)
    spawns.append(("door",) + mat)
    spawns.append(("entry",) + entry)
    spawns.append(("gateway",) + gateway(scene, walker))
    check_village(walker, spawns)

    sets = list(overworld.values()) + list(tilesets.values())
    shapes = scene.solid.rectangles()
    write_tmx(os.path.join(OUT, "village.tmx"), WIDTH, HEIGHT, sets, layers,
              tiles_rel=HOME_REL,
              objects=[("spawn", x, y, name) for name, x, y in spawns],
              shapes=shapes)
    report("village.tmx", WIDTH, HEIGHT, sets, layers, shapes)


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
    print("generating the village and the home")
    install_tilesets()
    village()
    home()
