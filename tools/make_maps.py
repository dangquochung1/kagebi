#!/usr/bin/env python3
"""Generate Tiled .tmx maps for the game.

Maps are generated rather than drawn by hand so that changing a rule - the path
width, how dense the trees are - is an edit in one place instead of twenty
edits in an editor. The cost is that generated layouts look mechanical, so every
map gets an empty `decor` layer: open the .tmx in Tiled and scatter detail into
it by hand, and nothing here will overwrite it.

Tile coordinates below were read off the tilesets directly (see
tools/preview/*_grid.png), because the packs ship no metadata saying which tile
is a tree.

Usage:  python tools/make_maps.py
"""
import os
import random
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TILES = os.path.join(ROOT, "assets", "gfx", "tiles", "overworld")
OUT = os.path.join(ROOT, "assets", "maps")

TILE = 16


class Tileset:
    """One tileset image, and where its ids start in the map's global space."""

    def __init__(self, name, filename, firstgid, folder=None):
        self.name = name
        self.filename = filename
        self.firstgid = firstgid
        from PIL import Image
        with Image.open(os.path.join(folder or TILES, filename)) as im:
            self.width, self.height = im.size
        self.columns = self.width // TILE
        self.rows = self.height // TILE
        self.count = self.columns * self.rows

    def gid(self, tx, ty):
        """Global id for a tile at column tx, row ty."""
        if not (0 <= tx < self.columns and 0 <= ty < self.rows):
            raise ValueError("%s: tile (%d,%d) is outside %dx%d"
                             % (self.name, tx, ty, self.columns, self.rows))
        return self.firstgid + ty * self.columns + tx


def build_tilesets():
    sets, gid = {}, 1
    for name, filename in (("floor", "tilesetfloor.png"),
                           ("nature", "tilesetnature.png"),
                           ("house", "tilesethouse.png")):
        ts = Tileset(name, filename, gid)
        sets[name] = ts
        gid += ts.count
    return sets


class Layer:
    def __init__(self, name, width, height):
        self.name = name
        self.width = width
        self.height = height
        self.data = [0] * (width * height)

    def put(self, x, y, gid):
        if 0 <= x < self.width and 0 <= y < self.height:
            self.data[y * self.width + x] = gid

    def stamp(self, tileset, x, y, tx, ty, w, h):
        """Place a multi-tile object with its top-left at (x, y)."""
        for dy in range(h):
            for dx in range(w):
                self.put(x + dx, y + dy, tileset.gid(tx + dx, ty + dy))

    def free(self, x, y, w, h):
        for dy in range(h):
            for dx in range(w):
                px, py = x + dx, y + dy
                if not (0 <= px < self.width and 0 <= py < self.height):
                    return False
                if self.data[py * self.width + px] != 0:
                    return False
        return True


def write_tmx(path, width, height, tilesets, layers,
              tiles_rel="../gfx/tiles/overworld/", objects=None):
    m = ET.Element("map", {
        "version": "1.10", "tiledversion": "1.10.2",
        "orientation": "orthogonal", "renderorder": "right-down",
        "width": str(width), "height": str(height),
        "tilewidth": str(TILE), "tileheight": str(TILE),
        "infinite": "0",
        "nextlayerid": str(len(layers) + 1), "nextobjectid": "1",
    })
    for ts in tilesets:
        node = ET.SubElement(m, "tileset", {
            "firstgid": str(ts.firstgid), "name": ts.name,
            "tilewidth": str(TILE), "tileheight": str(TILE),
            "tilecount": str(ts.count), "columns": str(ts.columns),
        })
        # Tiled and libGDX both resolve this relative to the .tmx file.
        ET.SubElement(node, "image", {
            "source": tiles_rel + ts.filename,
            "width": str(ts.width), "height": str(ts.height),
        })
    for i, layer in enumerate(layers, start=1):
        node = ET.SubElement(m, "layer", {
            "id": str(i), "name": layer.name,
            "width": str(width), "height": str(height),
        })
        data = ET.SubElement(node, "data", {"encoding": "csv"})
        rows = []
        for y in range(height):
            rows.append(",".join(str(g) for g in
                                 layer.data[y * width:(y + 1) * width]))
        data.text = "\n" + ",\n".join(rows) + "\n"

    if objects:
        group = ET.SubElement(m, "objectgroup",
                              {"id": str(len(layers) + 1), "name": "spawns"})
        for i, (kind, ox, oy, tag) in enumerate(objects, start=1):
            attrs = {"id": str(i), "name": tag or "", "type": kind,
                     "x": str(ox), "y": str(oy)}
            ET.SubElement(group, "object", attrs)
        m.set("nextobjectid", str(len(objects) + 1))

    ET.indent(m, space=" ")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    ET.ElementTree(m).write(path, encoding="UTF-8", xml_declaration=True)


# Tile coordinates within each tileset, read off the grid previews.
# The floor tileset's only solid green is this one; every other green tile in
# it turned out to be an autotile corner with sand or dirt baked into it, so
# ground texture has to come from scattered decoration instead of tile variety.
GRASS = (0, 12)
TREES = [(4, 2), (6, 2), (16, 2), (18, 2)]   # 2x3 broadleaf
PINE = (1, 2)                        # 2x3 conifer; column 0 of that row is empty
BUSHES = [(0, 10), (1, 10), (2, 10), (3, 10), (5, 10)]
TUFTS = [(4, 10), (6, 10), (7, 10), (8, 10), (9, 10), (10, 10), (11, 10),
         (4, 11), (5, 11), (8, 11), (9, 11)]
FLOWERS = [(0, 11), (1, 11), (2, 11)]   # (3,11) is a red bloom that reads as a heart
ROCK = (16, 5)                       # 2x3 grey boulder
HOUSES = [(0, 0), (4, 0), (8, 0)]    # 4x3 each
TORII = (0, 5)                       # 3x2 shrine gate


def village(width=30, height=18, seed=7):
    """The menu backdrop and, later, the hub: a clearing with houses and a gate."""
    rng = random.Random(seed)
    sets = build_tilesets()
    floor, nature, house = sets["floor"], sets["nature"], sets["house"]

    ground = Layer("ground", width, height)
    decor = Layer("decor", width, height)       # left empty, for hand editing
    props = Layer("props", width, height)
    overhead = Layer("overhead", width, height)

    for y in range(height):
        for x in range(width):
            ground.put(x, y, floor.gid(*GRASS))

    # Houses along the top, spaced so the gate can sit between them.
    for i, (hx, hy) in enumerate(HOUSES):
        props.stamp(house, 3 + i * 9, 1, hx, hy, 4, 3)

    # The shrine gate anchors the centre and gives the eye somewhere to land.
    gate_x, gate_y = width // 2 - 1, height - 6
    props.stamp(house, gate_x, gate_y, *TORII, 3, 2)

    def in_clearing(x, y):
        """Inside the open middle, where nothing tall may stand.

        The menu panel sits here and a character walks through it later; a busy
        backdrop directly behind text is unreadable either way.
        """
        nx = (x - width / 2.0) / (width * 0.30)
        ny = (y - height / 2.0) / (height * 0.34)
        return nx * nx + ny * ny < 1.0

    # Trees ring the clearing and leave the middle open. The menu panel sits in
    # that gap, and a busy backdrop directly behind text is unreadable.
    cx, cy = width / 2.0, height / 2.0
    for _ in range(220):
        x = rng.randrange(0, width - 2)
        y = rng.randrange(4, height - 3)
        if in_clearing(x, y) or in_clearing(x + 1, y + 2):
            continue
        # Beyond the clearing, density still rises with distance so the treeline
        # thickens towards the edges instead of stopping abruptly.
        dist = max(abs(x - cx) / cx, abs(y - cy) / cy)
        if rng.random() > dist ** 2:
            continue
        tx, ty = rng.choice(TREES + [PINE, PINE])
        if props.free(x, y, 2, 3):
            props.stamp(nature, x, y, tx, ty, 2, 3)
            # Canopy top goes above the player, trunk below, so a character can
            # walk behind a tree.
            for dx in range(2):
                overhead.put(x + dx, y, nature.gid(tx + dx, ty))
                props.put(x + dx, y, 0)

    # Dense tufts: the ground is a single flat colour, so this is the only thing
    # breaking it up.
    for _ in range(260):
        x, y = rng.randrange(width), rng.randrange(4, height)
        if props.free(x, y, 1, 1):
            props.put(x, y, nature.gid(*rng.choice(TUFTS)))

    for _ in range(30):
        x, y = rng.randrange(width), rng.randrange(5, height)
        if props.free(x, y, 1, 1):
            props.put(x, y, nature.gid(*rng.choice(BUSHES + FLOWERS)))

    for _ in range(8):
        x, y = rng.randrange(1, width - 2), rng.randrange(6, height - 3)
        if props.free(x, y, 2, 3):
            props.stamp(nature, x, y, *ROCK, 2, 3)

    path = os.path.join(OUT, "village.tmx")
    write_tmx(path, width, height,
              [floor, nature, house], [ground, decor, props, overhead])
    print("  village.tmx  %dx%d tiles (%dx%d px), 3 tilesets, 4 layers"
          % (width, height, width * TILE, height * TILE))
    return path


# --------------------------------------------------------------------------
# Rooms
# --------------------------------------------------------------------------

# 20 x 11 tiles is 320 x 176 px: see gen/RoomTemplate for why the camera snaps
# per room rather than scrolling, and why this is the grid that fits.
ROOM_W, ROOM_H = 20, 11
DOOR_SPAN = 3                       # tiles of opening in the middle of a wall


def placeholder_room(name="ruins/placeholder"):
    """One structurally correct room, drawn in the wrong tileset on purpose.

    The dungeon screen needs a room to load before the real biome rooms exist,
    and a room it cannot load blocks that work entirely. So this uses the
    overworld tiles whose coordinates are already measured above rather than
    holding everything up for a tileset survey - it is the right SHAPE, with
    walls, four doorways and a spawn layer, and it is meant to be deleted the
    moment real rooms land.
    """
    sets, gid = {}, 1
    for key, filename in (("floor", "tilesetfloor.png"), ("nature", "tilesetnature.png")):
        ts = Tileset(key, filename, gid)
        sets[key] = ts
        gid += ts.count
    floor, nature = sets["floor"], sets["nature"]

    ground = Layer("ground", ROOM_W, ROOM_H)
    decor = Layer("decor", ROOM_W, ROOM_H)
    walls = Layer("walls", ROOM_W, ROOM_H)
    props = Layer("props", ROOM_W, ROOM_H)
    overhead = Layer("overhead", ROOM_W, ROOM_H)

    for y in range(ROOM_H):
        for x in range(ROOM_W):
            ground.put(x, y, floor.gid(*GRASS))

    # A solid border, then a gap in the middle of each side. Every room carries
    # all four doorways; the screen seals the ones that lead nowhere.
    gap_x = range((ROOM_W - DOOR_SPAN) // 2, (ROOM_W + DOOR_SPAN) // 2)
    gap_y = range((ROOM_H - DOOR_SPAN) // 2, (ROOM_H + DOOR_SPAN) // 2)
    wall = nature.gid(*ROCK)
    for x in range(ROOM_W):
        if x not in gap_x:
            walls.put(x, 0, wall)
            walls.put(x, ROOM_H - 1, wall)
    for y in range(ROOM_H):
        if y not in gap_y:
            walls.put(0, y, wall)
            walls.put(ROOM_W - 1, y, wall)

    # Tiled stores object y downwards from the top; the Java reader flips it.
    cx, cy = ROOM_W * TILE // 2, ROOM_H * TILE // 2
    objects = [("ENTRY", cx, cy, ""),
               ("ENEMY", cx - 64, cy, ""),
               ("ENEMY", cx + 64, cy, ""),
               ("CHEST", cx, cy - 32, "")]

    path = os.path.join(OUT, "rooms", name + ".tmx")
    write_tmx(path, ROOM_W, ROOM_H, [floor, nature],
              [ground, decor, walls, props, overhead],
              tiles_rel="../../../gfx/tiles/overworld/", objects=objects)
    print("  rooms/%s.tmx  %dx%d tiles, 4 doorways, %d spawns"
          % (name, ROOM_W, ROOM_H, len(objects)))
    return path


if __name__ == "__main__":
    print("generating maps")
    village()
    placeholder_room()
