#!/usr/bin/env python3
"""Generate Tiled .tmx maps for the game.

Maps are generated rather than drawn by hand so that changing a rule - the path
width, how dense the trees are - is an edit in one place instead of twenty
edits in an editor. The cost is that generated layouts look mechanical, so every
map gets an empty `decor` layer: open the .tmx in Tiled and scatter detail into
it by hand, and nothing here will overwrite it. That promise is kept by
carry_decor(), which lifts the decor layer out of each file before rewriting
it; every other layer, and the spawns, belong to this script.

Tile coordinates below were read off the tilesets directly, because the packs
ship no metadata saying which tile is a tree. tools/preview_tiles.py draws the
labelled grids they were read from.

Every room is flood-filled before it is written (check_room): all four doors
open, and every floor tile reachable from every door. A room that seals a door
still looks open in a preview, so this is not left to the eye.

Usage:  python tools/make_maps.py
        python tools/preview_map.py assets/maps/rooms/ruins review/ruins.png --spawns
"""
import os
import random
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TILES = os.path.join(ROOT, "assets", "gfx", "tiles", "overworld")
RUINS_TILES = os.path.join(ROOT, "assets", "gfx", "tiles", "ruins")
DEPTHS_TILES = os.path.join(ROOT, "assets", "gfx", "tiles", "depths")
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


# Tiled keeps horizontal, vertical, diagonal and hex-rotation flips in the top
# four bits of a gid; a hand-placed decor tile may well carry one.
FLIP_BITS = 0xF0000000


class ForeignTileset:
    """A tileset a human added in Tiled, written back as it was found."""

    def __init__(self, node, firstgid, count):
        self.node = node
        self.firstgid = firstgid
        self.count = count


def _tileset_key(tmx_dir, source):
    return os.path.normcase(os.path.normpath(os.path.join(tmx_dir, source)))


def carry_decor(path, tilesets, layers, tiles_rel):
    """Copy the decor layer out of the file about to be overwritten.

    `decor` is where a human finishes a generated map by hand, so a regenerate
    that wipes it destroys exactly the work the layer exists for - and before
    this function existed, that is what every run did: a tile planted in
    rooms/ruins/normal_01.tmx was gone after one run of this script.

    Tiles are matched by image and local index rather than by raw gid, because
    firstgids are this script's to renumber: add a tileset to a room and every
    gid after it shifts. A tileset the human added in Tiled for their decor is
    appended after the generated ones. Anything that cannot be carried across
    exactly stops the run instead of dropping the work silently.

    Returns the extra tilesets that must be written for the carried tiles.
    """
    decor = next((l for l in layers if l.name == "decor"), None)
    if decor is None or not os.path.exists(path):
        return []
    root = ET.parse(path).getroot()
    old = next((l for l in root.findall("layer") if l.get("name") == "decor"), None)
    if old is None:
        return []
    data = old.find("data")
    if data.get("encoding") != "csv" or data.get("compression"):
        raise SystemExit("%s: decor is not plain CSV, so it cannot be carried "
                         "over; re-save it in Tiled with Tile Layer Format: CSV"
                         % path)
    gids = [int(v) for v in data.text.replace("\n", "").split(",") if v.strip()]
    if not any(gids):
        return []
    if (int(old.get("width")), int(old.get("height"))) != (decor.width, decor.height):
        raise SystemExit("%s: hand-drawn decor is %sx%s but the map is now %dx%d; "
                         "move it by hand before regenerating"
                         % (path, old.get("width"), old.get("height"),
                            decor.width, decor.height))

    tmx_dir = os.path.dirname(os.path.abspath(path))
    old_sets = []
    for ts in root.findall("tileset"):
        if ts.get("source"):
            # An external .tsx: its tile count lives in that file, not here.
            tsx = ET.parse(os.path.join(tmx_dir, ts.get("source"))).getroot()
            count, key = int(tsx.get("tilecount")), _tileset_key(tmx_dir, ts.get("source"))
        else:
            count = int(ts.get("tilecount"))
            key = _tileset_key(tmx_dir, ts.find("image").get("source"))
        old_sets.append((int(ts.get("firstgid")), count, key, ts))

    first = {_tileset_key(tmx_dir, tiles_rel + ts.filename): ts.firstgid
             for ts in tilesets}
    next_gid = max(ts.firstgid + ts.count for ts in tilesets)
    extra = []
    for i, raw in enumerate(gids):
        if raw == 0:
            continue
        flags, gid = raw & FLIP_BITS, raw & ~FLIP_BITS & 0xFFFFFFFF
        owner = next((s for s in old_sets if s[0] <= gid < s[0] + s[1]), None)
        if owner is None:
            raise SystemExit("%s: decor tile %d belongs to no tileset in the file"
                             % (path, gid))
        old_first, count, key, node = owner
        if key not in first:
            first[key] = next_gid
            extra.append(ForeignTileset(node, next_gid, count))
            next_gid += count
        decor.data[i] = flags | (first[key] + gid - old_first)
    print("    kept %d hand-placed decor tiles in %s"
          % (sum(1 for g in gids if g), os.path.relpath(path, ROOT)))
    return extra


def write_tmx(path, width, height, tilesets, layers,
              tiles_rel="../gfx/tiles/overworld/", objects=None):
    extra = carry_decor(path, tilesets, layers, tiles_rel)
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
    for ts in extra:
        ts.node.set("firstgid", str(ts.firstgid))
        m.append(ts.node)
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

# The opening in each wall, in tile coordinates measured y-DOWN from the top,
# which is the space both Layer and Tiled's object layer use.
DOOR_X = range((ROOM_W - DOOR_SPAN) // 2, (ROOM_W + DOOR_SPAN) // 2)   # 8,9,10
DOOR_Y = range((ROOM_H - DOOR_SPAN) // 2, (ROOM_H + DOOR_SPAN) // 2)   # 4,5,6

# Two tiles of clear floor behind every doorway. Without it a room can be
# generated whose only path out of a door is diagonal, and the player snags on
# the jamb; two tiles is the shallowest depth that always leaves a straight step
# in from a 3-wide opening.
DOOR_APRON = 2


# --------------------------------------------------------------------------
# Measured tile coordinates
# --------------------------------------------------------------------------
#
# Every (x, y) below was read off tools/preview/*_grid.png and then checked a
# second way, because reading a coordinate off a contiguous sheet is exactly
# how the village generator ended up pointing at an empty column:
#
#   * tools/preview_tiles.py draws the labelled 16px grid.
#   * Candidate floors were tiled 5x4 into tools/preview/swatch_*_floor.png and
#     rejected on sight if a seam showed. Several obvious-looking floor tiles
#     are panel BORDERS and tile with a hard vertical line - ruins (2,1),
#     (13,1) and (13,13) all failed this way and are not used.
#   * Wall and prop tiles were re-rendered one per cell with gaps between them
#     (tools/preview/sep_*.png) so no tile could be credited to its neighbour.
#
# Two things the survey turned up that the project plan did not predict:
#
#   1. The ruins interior pack's fourth colour is a sage/olive GREEN, not the
#      blue-green the plan calls for. Nothing in these four sheets is blue.
#   2. elements.png (8,0) is a green slime with eyes - a creature, not scenery.
#      It is deliberately absent from every table below.

# ---- ruins ---------------------------------------------------------------
#
# tilesetwallsimple.png is 10x11 and holds FOUR 5x5 room frames with one blank
# spacer row between the top pair and the bottom pair. That is the whole reason
# this sheet is used for walls rather than tilesetinterior.png, which draws the
# same four colours as a 8x10 architectural mock-up whose pieces do not
# separate cleanly into an autotile set.
#
# Within a 5x5 frame, offsets from its origin:
#   (0,0) outer corner TL   (3,0) top run      (4,0) outer corner TR
#   (0,1) left run                             (4,1) right run
#   (0,4) outer corner BL   (3,4) bottom run   (4,4) outer corner BR
#   (2,1)..(3,2)            a 2x2 carved stone disc, raised, reads as an
#                           obstacle rather than as floor
# Offsets (1,0), (2,0), (0,2) and (0,3) carry a decorative pilaster tab that
# only lines up at the frame's own midpoint, so they are skipped: repeated
# along a 20-tile wall the tabs land at arbitrary places and read as damage.
RUINS_FRAME = {
    "tl": (0, 0), "top": (3, 0), "tr": (4, 0),
    "left": (0, 1), "right": (4, 1),
    "bl": (0, 4), "bottom": (3, 4), "br": (4, 4),
}
RUINS_DISC = (2, 1)                 # 2x2

# The four frame origins in tilesetwallsimple.png.
RUINS_WALL_ORIGIN = {
    "cream": (0, 0), "orange": (5, 0), "brown": (0, 6), "green": (5, 6),
}

# Floors from tilesetinteriorfloor.png (22x17), each pair verified seamless by
# tiling it 5x4 in tools/preview/swatch_ruins_floor.png.
#
# One base tile and one accent from the SAME colour block, at roughly one
# accent in eight. The first draft mixed the flat tan (12,1) into the yellow
# cobble floor because both are warm, and the result was a floor of two
# obviously different materials: scattered accents that differ in MATERIAL
# rather than in wear clump into visible patches, because neighbouring picks
# merge. Tone variation is free, material variation is not.
RUINS_FLOORS = {
    "cream": ((1, 1), (8, 2)),
    "orange": ((1, 7), (8, 8)),
    "brown": ((1, 13), (5, 13)),
    "green": ((12, 7), (17, 7)),
}
FLOOR_ACCENT = 0.12

# elements.png is 9x3. The 2x3 orange double door at (0,0) is the only art in
# the ruins set that reads as a way out, so it marks the stairs.
RUINS_DOOR = (0, 0)                 # 2x3 double door
RUINS_COFFIN = (2, 0)               # 3x2 sarcophagus; measured, unused - see ruins_pieces
RUINS_PILLARS = (5, 0)              # 2x3 twin columns; measured, unused - see ruins_pieces
RUINS_CRATE = (7, 0)                # 1x1 mossy block

# ---- depths --------------------------------------------------------------
#
# dungeon_tileset.png is a single 10x10 sheet - one palette, no variants. Its
# frame is drawn at (0,0)..(5,4) with the floor showing through the middle, so
# the wall pieces are the ring around it. The corner and side tiles carry the
# black OUTSIDE of the wall baked in, which is why they only work on a room's
# perimeter and never as a free-standing block in the middle of a floor.
DEPTHS_FRAME = {
    "tl": (0, 0), "top": [(1, 0), (2, 0), (3, 0), (4, 0)], "tr": (5, 0),
    "left": [(0, 1), (0, 2), (0, 3)], "right": [(5, 1), (5, 2), (5, 3)],
    "bl": (0, 4), "bottom": [(1, 4), (2, 4), (3, 4), (4, 4)], "br": (5, 4),
}

# Twelve floor tiles at (6..9, 0..2), every one of them seamless. The variety
# is the point: this pack's floor is a single flat purple and without scratch
# variation a 18x9 field of it reads as a solid colour block.
DEPTHS_FLOORS = [(x, y) for y in range(3) for x in range(6, 10)]

DEPTHS_BARRELS = [(6, 6), (7, 6)]           # 1x1 each
DEPTHS_CRATES = [(0, 8), (1, 8)]            # 1x1 shelves
DEPTHS_CHEST = (3, 8)
DEPTHS_TORCHES = [(0, 9), (1, 9)]           # lit wall torches
DEPTHS_STANDS = [(3, 9), (5, 9)]            # floor candelabra
DEPTHS_BONES = [(4, 6), (7, 7), (8, 6)]


# --------------------------------------------------------------------------
# Obstacle layouts
# --------------------------------------------------------------------------
#
# Every layout returns (blocks, singles):
#   blocks  - list of (x, y, w, h) rectangles, both sides even
#   singles - list of (x, y) one-tile props
#
# Even on both sides is not a style choice: every free-standing obstacle in the
# ruins set that fills its own footprint is 2x2 (see ruins_pieces), so a block
# is tiled out of those, and an odd side would need a piece that does not
# exist. The depths set draws blocks as a brick face over a cap and has no
# such limit, but one set of layouts serves both packs.


def _open(rng):
    """Nothing at all. Boss arenas and the start room.

    A boss the player cannot see coming across the whole room feels cheap, so
    the boss layouts stay readable end to end at 320x176.
    """
    return [], []


def _pillars4(rng):
    return [(4, 3, 2, 2), (14, 3, 2, 2), (4, 6, 2, 2), (14, 6, 2, 2)], []


def _pillars6(rng):
    return [(3, 3, 2, 2), (9, 3, 2, 2), (15, 3, 2, 2),
            (3, 6, 2, 2), (9, 6, 2, 2), (15, 6, 2, 2)], []


def _corners(rng):
    return [(1, 1, 4, 2), (15, 1, 4, 2), (1, 8, 4, 2), (15, 8, 4, 2)], []


def _pinch(rng):
    """Two part-walls split the room into three chambers.

    The gaps are offset from each other so crossing the room is an S rather
    than a straight line; a straight line lets a ranged enemy hold the whole
    room from one end.
    """
    return [(6, 1, 2, 4), (6, 6, 2, 4), (12, 3, 2, 4)], []


def _pinch_mirror(rng):
    return [(6, 3, 2, 4), (12, 1, 2, 4), (12, 6, 2, 4)], []


def _bar(rng):
    """A broken wall across the waist of the room.

    Three segments rather than two, so the ways through it are off-centre and
    a player crossing has to commit to a side instead of walking straight up
    the middle.
    """
    return [(3, 5, 4, 2), (9, 5, 4, 2), (15, 5, 2, 2)], []


def _chevron(rng):
    return [(5, 2, 2, 2), (7, 4, 2, 2), (5, 6, 2, 2),
            (13, 2, 2, 2), (11, 4, 2, 2), (13, 6, 2, 2)], []


def _alcoves(rng):
    """Niches along the long walls. Cover to break line of sight, no maze."""
    return [(2, 1, 4, 2), (14, 1, 4, 2), (2, 8, 4, 2), (14, 8, 4, 2),
            (8, 4, 4, 2)], []


def _ring(rng):
    """A hollow block in the middle; the fight orbits it."""
    return [(7, 3, 6, 2), (7, 6, 6, 2)], []


def _diagonal(rng):
    return [(3, 2, 2, 2), (6, 4, 2, 2), (9, 6, 2, 2), (12, 4, 2, 2),
            (15, 2, 2, 2)], []


def _scatter(rng):
    """Seeded clutter, for the rooms that should not look composed.

    Placement is rejection-sampled against a 2-tile gap so nothing forms an
    accidental corridor the player has to thread.
    """
    # The doorway aprons are claimed up front: rejection sampling that can
    # land on them would fail the room, and re-rolling a whole room is far
    # more annoying to debug than never generating the bad placement.
    blocks, singles, taken = [], [], set(apron_cells())

    def claim(x, y, w, h, pad=1):
        cells = [(px, py)
                 for py in range(y - pad, y + h + pad)
                 for px in range(x - pad, x + w + pad)]
        if any(c in taken for c in cells):
            return False
        taken.update((px, py) for py in range(y, y + h) for px in range(x, x + w))
        taken.update(cells)
        return True

    for _ in range(60):
        if len(blocks) >= 3:
            break
        w, h = rng.choice([(2, 2), (2, 4), (4, 2), (2, 6), (6, 2)])
        x = rng.randrange(1, ROOM_W - 1 - w)
        y = rng.randrange(1, ROOM_H - 1 - h)
        if claim(x, y, w, h):
            blocks.append((x, y, w, h))
    for _ in range(60):
        if len(singles) >= 6:
            break
        x = rng.randrange(1, ROOM_W - 1)
        y = rng.randrange(1, ROOM_H - 1)
        if claim(x, y, 1, 1):
            singles.append((x, y))
    return blocks, singles


def _shop(rng):
    """Two counters flanking the top door, stock laid out in front of them."""
    return [(3, 2, 4, 2), (13, 2, 4, 2)], []


def _vault(rng):
    """A treasure plinth: a short wall behind the chest, nothing else."""
    return [(7, 3, 6, 2)], []


LAYOUTS = {
    "open": _open, "pillars4": _pillars4, "pillars6": _pillars6,
    "corners": _corners, "pinch": _pinch, "pinch_mirror": _pinch_mirror,
    "bar": _bar, "chevron": _chevron, "alcoves": _alcoves, "ring": _ring,
    "diagonal": _diagonal, "scatter": _scatter, "shop": _shop, "vault": _vault,
}


# --------------------------------------------------------------------------
# Validation
# --------------------------------------------------------------------------

def solid_grid(blocks, singles):
    """Walls plus obstacles as a boolean grid, y-down."""
    solid = [[False] * ROOM_W for _ in range(ROOM_H)]
    for x in range(ROOM_W):
        if x not in DOOR_X:
            solid[0][x] = True
            solid[ROOM_H - 1][x] = True
    for y in range(ROOM_H):
        if y not in DOOR_Y:
            solid[y][0] = True
            solid[y][ROOM_W - 1] = True
    for (x, y, w, h) in blocks:
        for dy in range(h):
            for dx in range(w):
                solid[y + dy][x + dx] = True
    for (x, y) in singles:
        solid[y][x] = True
    return solid


def door_cells():
    """The tiles of the four openings themselves."""
    cells = []
    for x in DOOR_X:
        cells.append((x, 0))
        cells.append((x, ROOM_H - 1))
    for y in DOOR_Y:
        cells.append((0, y))
        cells.append((ROOM_W - 1, y))
    return cells


def apron_cells():
    """Door tiles plus the clear floor immediately behind each one."""
    cells = set(door_cells())
    for x in DOOR_X:
        for d in range(1, DOOR_APRON + 1):
            cells.add((x, d))
            cells.add((x, ROOM_H - 1 - d))
    for y in DOOR_Y:
        for d in range(1, DOOR_APRON + 1):
            cells.add((d, y))
            cells.add((ROOM_W - 1 - d, y))
    return cells


def check_room(name, solid):
    """Every walkable tile reachable from every door, and no blocked apron.

    Run on every room at generation time rather than trusted to inspection: a
    room that seals one of its four doors is invisible in a preview PNG - the
    doorway still looks open - and only shows up as a player stuck in a floor
    they cannot cross.
    """
    for (x, y) in apron_cells():
        if solid[y][x]:
            raise ValueError("%s: doorway approach blocked at %d,%d" % (name, x, y))

    start = next((x, y) for (x, y) in apron_cells() if not solid[y][x])
    seen, stack = {start}, [start]
    while stack:
        x, y = stack.pop()
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if not (0 <= nx < ROOM_W and 0 <= ny < ROOM_H):
                continue
            if solid[ny][nx] or (nx, ny) in seen:
                continue
            seen.add((nx, ny))
            stack.append((nx, ny))

    walkable = {(x, y) for y in range(ROOM_H) for x in range(ROOM_W)
                if not solid[y][x]}
    if seen != walkable:
        lost = sorted(walkable - seen)
        raise ValueError("%s: %d tiles walled off, first at %s"
                         % (name, len(lost), lost[0]))
    for cell in door_cells():
        if cell not in seen:
            raise ValueError("%s: door %s not reachable" % (name, cell))
    return walkable


# --------------------------------------------------------------------------
# Painting
# --------------------------------------------------------------------------

def paint_ruins(variant, blocks, singles, rng):
    """Ground, walls and props for one ruins room. Returns (tilesets, layers)."""
    sets, gid = {}, 1
    for key, filename in (("wall", "tilesetwallsimple.png"),
                          ("floor", "tilesetinteriorfloor.png"),
                          ("elements", "elements.png")):
        ts = Tileset(key, filename, gid, folder=RUINS_TILES)
        sets[key] = ts
        gid += ts.count
    wall, floor, elem = sets["wall"], sets["floor"], sets["elements"]
    ox, oy = RUINS_WALL_ORIGIN[variant]

    def frame(piece):
        fx, fy = RUINS_FRAME[piece]
        return wall.gid(ox + fx, oy + fy)

    ground, decor, walls, props, overhead = room_layers()

    # Floor goes under the walls too. The frame tiles have transparent pixels
    # at their outer rim, and with nothing behind them the screen's clear
    # colour shows through as a black fringe around the room.
    base, accent = RUINS_FLOORS[variant]
    for y in range(ROOM_H):
        for x in range(ROOM_W):
            pick = accent if rng.random() < FLOOR_ACCENT else base
            ground.put(x, y, floor.gid(*pick))

    perimeter(walls, frame)
    for (x, y, w, h) in blocks:
        for piece, px, py in ruins_pieces(x, y, w, h, rng):
            if piece == "disc":
                props.stamp(wall, px, py, ox + RUINS_DISC[0], oy + RUINS_DISC[1], 2, 2)
            else:
                for dy in range(2):
                    for dx in range(2):
                        props.put(px + dx, py + dy, elem.gid(*RUINS_CRATE))
    for (x, y) in singles:
        props.put(x, y, elem.gid(*RUINS_CRATE))
    return [wall, floor, elem], [ground, decor, walls, props, overhead], sets


def ruins_pieces(x, y, w, h, rng):
    """Split an obstacle rectangle into 2x2 objects the ruins art can draw.

    The frame's corner and edge tiles bake in the black OUTSIDE of the wall.
    On a room's perimeter that black is the edge of the world and looks right;
    used for a free-standing block in the middle of a floor it draws a black
    halo round every obstacle, which is what the first pass did and what the
    contact sheet caught. So interior obstacles come only from art drawn as a
    standalone object.

    That rules out two of the four candidates as well, for the opposite reason.
    RUINS_COFFIN is 3x2 and RUINS_PILLARS is 2x3, but both paint an object
    about 2x2 centred in that box with empty margin around it - so their
    collision would be up to twice the size of the thing the player can see,
    which is the worst kind of obstacle: an invisible wall. What is left is the
    carved disc and a square of four crates, both of which fill their own
    footprint, and every rectangle in LAYOUTS is therefore even on both sides.
    """
    if w % 2 or h % 2:
        raise ValueError("block %dx%d at %d,%d: the ruins obstacle set is all "
                         "2x2, so both sides must be even" % (w, h, x, y))
    return [("crates" if rng.random() < 0.3 else "disc", px, py)
            for py in range(y, y + h, 2) for px in range(x, x + w, 2)]


def paint_depths(blocks, singles, rng):
    sets = {"dungeon": Tileset("dungeon", "dungeon_tileset.png", 1,
                               folder=DEPTHS_TILES)}
    d = sets["dungeon"]
    ground, decor, walls, props, overhead = room_layers()

    for y in range(ROOM_H):
        for x in range(ROOM_W):
            ground.put(x, y, d.gid(*rng.choice(DEPTHS_FLOORS)))

    def frame(piece):
        v = DEPTHS_FRAME[piece]
        return d.gid(*(v if isinstance(v, tuple) else rng.choice(v)))

    perimeter(walls, frame)

    # Free-standing blocks use the top-wall tile for their upper row and the
    # bottom-wall cap for their lower row. The corner tiles cannot be used
    # here: they carry the black outside-the-room field, which mid-floor reads
    # as a hole rather than as a wall end.
    for (x, y, w, h) in blocks:
        for dy in range(h):
            for dx in range(w):
                piece = "top" if dy < h - 1 else "bottom"
                props.put(x + dx, y + dy, frame(piece))
    for (x, y) in singles:
        props.put(x, y, d.gid(*rng.choice(DEPTHS_BARRELS + DEPTHS_CRATES)))
    return [d], [ground, decor, walls, props, overhead], sets


def room_layers():
    """The five layers every room carries, in render order.

    `decor` is created and left empty on purpose: it is the layer a human opens
    in Tiled to scatter detail into, and nothing generated here writes to it.
    """
    return (Layer("ground", ROOM_W, ROOM_H),
            Layer("decor", ROOM_W, ROOM_H),
            Layer("walls", ROOM_W, ROOM_H),
            Layer("props", ROOM_W, ROOM_H),
            Layer("overhead", ROOM_W, ROOM_H))


def perimeter(layer, frame):
    """The room's four walls, with a gap in the middle of each side.

    Every room carries all four doorways whether or not the floor uses them;
    the screen seals the ones that lead nowhere. See gen/RoomTemplate for why
    that beats one template per door combination.
    """
    for x in range(ROOM_W):
        if x in DOOR_X:
            continue
        layer.put(x, 0, frame("tl" if x == 0 else "tr" if x == ROOM_W - 1 else "top"))
        layer.put(x, ROOM_H - 1,
                  frame("bl" if x == 0 else "br" if x == ROOM_W - 1 else "bottom"))
    for y in range(ROOM_H):
        if y in DOOR_Y or y in (0, ROOM_H - 1):
            continue
        layer.put(0, y, frame("left"))
        layer.put(ROOM_W - 1, y, frame("right"))


# --------------------------------------------------------------------------
# Spawns
# --------------------------------------------------------------------------
#
# Object `type` must spell a SpawnPoint.Kind exactly; the object `name` becomes
# the tag. Coordinates are written y-DOWN, the way Tiled stores them -
# RoomCatalog flips them once on the way in, and pre-flipping here would make
# the files wrong to open in Tiled for the sake of being right in one reader.

def at(tx, ty):
    """Centre of tile (tx, ty) in Tiled's pixel space."""
    return tx * TILE + TILE // 2, ty * TILE + TILE // 2


def nearest_free(walkable, tx, ty, used):
    """The free tile closest to (tx, ty) that nothing else has claimed."""
    best = None
    for (x, y) in walkable:
        if (x, y) in used:
            continue
        d = (x - tx) ** 2 + (y - ty) ** 2
        if best is None or d < best[0]:
            best = (d, x, y)
    if best is None:
        raise ValueError("no free tile left near %d,%d" % (tx, ty))
    used.add((best[1], best[2]))
    return best[1], best[2]


# Where the player stands after stepping through each of the four doors. The
# tag names the side of THIS room the player arrived at: coming out of the
# room to the west means arriving at this room's LEFT door.
ENTRIES = [
    ("UP", 9, 1), ("DOWN", 9, ROOM_H - 2), ("LEFT", 1, 5), ("RIGHT", ROOM_W - 2, 5),
]

# Enemies stand off the centre line so a room never opens with something
# directly on top of the door the player walked through.
ENEMY_ANCHORS = [(4, 3), (15, 3), (4, 7), (15, 7), (9, 3), (9, 7), (7, 5), (12, 5)]
PROP_ANCHORS = [(2, 2), (ROOM_W - 3, 2), (2, ROOM_H - 3), (ROOM_W - 3, ROOM_H - 3)]

SPAWN_RULES = {
    # kind: (enemies, chests, props, extra)
    "start": (0, 0, 2, None),
    "normal": (5, 0, 1, None),
    "treasure": (0, 1, 2, None),
    "locked": (0, 1, 2, None),
    "shop": (0, 3, 0, "SHOPKEEPER"),
    "secret": (0, 1, 1, None),
    "boss": (0, 0, 2, "BOSS"),
    "exit": (0, 0, 2, "EXIT"),
}


def spawns_for(kind, walkable, rng):
    out, used = [], set()

    for tag, tx, ty in ENTRIES:
        x, y = nearest_free(walkable, tx, ty, used)
        out.append(("ENTRY", *at(x, y), tag))
    # One untagged entry at the middle, used when there is no door to arrive
    # through at all - the start room, and a debug warp.
    x, y = nearest_free(walkable, ROOM_W // 2, ROOM_H // 2, used)
    out.append(("ENTRY", *at(x, y), ""))

    enemies, chests, props, extra = SPAWN_RULES[kind]

    if extra == "BOSS":
        x, y = nearest_free(walkable, ROOM_W // 2, ROOM_H // 2 + 1, used)
        out.append(("ENEMY", *at(x, y), "boss"))
    elif extra == "EXIT":
        x, y = nearest_free(walkable, ROOM_W // 2, ROOM_H // 2, used)
        out.append(("EXIT", *at(x, y), ""))
    elif extra == "SHOPKEEPER":
        x, y = nearest_free(walkable, ROOM_W // 2, 3, used)
        out.append(("SHOPKEEPER", *at(x, y), ""))

    anchors = list(ENEMY_ANCHORS)
    rng.shuffle(anchors)
    for tx, ty in anchors[:enemies]:
        x, y = nearest_free(walkable, tx, ty, used)
        out.append(("ENEMY", *at(x, y), ""))

    chest_anchors = ([(5, 6), (9, 7), (14, 6)] if kind == "shop"
                     else [(ROOM_W // 2, ROOM_H // 2)])
    tag = {"locked": "locked", "secret": "secret", "shop": "stock"}.get(kind, "")
    for tx, ty in chest_anchors[:chests]:
        x, y = nearest_free(walkable, tx, ty, used)
        out.append(("CHEST", *at(x, y), tag))

    for tx, ty in PROP_ANCHORS[:props]:
        x, y = nearest_free(walkable, tx, ty, used)
        out.append(("PROP", *at(x, y), "torch"))

    return out


# --------------------------------------------------------------------------
# The catalogue
# --------------------------------------------------------------------------
#
# Kind comes from the file name prefix - RoomCatalog.kindsFor reads everything
# before the first underscore - so the whole catalogue is legible from a
# directory listing and there is no index that can drift out of step.
ROOM_PLAN = [
    ("start", "open"), ("start", "corners"),
    ("normal", "pillars4"), ("normal", "pillars6"), ("normal", "corners"),
    ("normal", "pinch"), ("normal", "pinch_mirror"), ("normal", "bar"),
    ("normal", "chevron"), ("normal", "alcoves"), ("normal", "ring"),
    ("normal", "diagonal"), ("normal", "scatter"), ("normal", "scatter"),
    ("normal", "scatter"),
    ("treasure", "vault"), ("treasure", "corners"),
    ("locked", "vault"), ("locked", "ring"),
    ("shop", "shop"),
    ("secret", "open"), ("secret", "alcoves"),
    # Boss arenas stay open on purpose: a boss the player cannot see coming
    # across a 320x176 room feels cheap rather than hard.
    ("boss", "open"), ("boss", "corners"),
    ("exit", "open"), ("exit", "pillars4"),
]

# One folder per biome, because RoomCatalog takes the biome from the folder
# name. The three ruins folders are the same layouts in three of the pack's
# four colours, which is how floors 1 to 3 get three moods out of one tileset
# without a runtime tint. A floors.json that names plain "ruins" for all three
# still works - it just runs the brown mood three times.
BIOMES = [
    ("ruins", "ruins", "brown"),
    ("ruins_green", "ruins", "green"),
    ("ruins_orange", "ruins", "orange"),
    ("depths", "depths", None),
]


def make_room(folder, pack, variant, kind, layout, index, seed):
    rng = random.Random(seed)
    blocks, singles = LAYOUTS[layout](rng)
    name = "%s_%02d" % (kind, index)
    solid = solid_grid(blocks, singles)
    walkable = check_room("%s/%s" % (folder, name), solid)

    if pack == "ruins":
        tilesets, layers, sets = paint_ruins(variant, blocks, singles, rng)
    else:
        tilesets, layers, sets = paint_depths(blocks, singles, rng)
        decorate_depths(layers[3], sets["dungeon"], rng)

    objects = spawns_for(kind, sorted(walkable), rng)
    path = os.path.join(OUT, "rooms", folder, name + ".tmx")
    write_tmx(path, ROOM_W, ROOM_H, tilesets, layers,
              tiles_rel="../../../gfx/tiles/%s/" % pack, objects=objects)
    return path, len(objects)


def decorate_depths(props, d, rng):
    """Lit torches bracketed to the top wall.

    They sit on cells the wall already made solid, so this is pure decoration
    with no effect on collision - and the depths floor is one flat purple, so
    without a light source the room has nothing to read against.
    """
    for x in (3, 6, ROOM_W - 7, ROOM_W - 4):
        props.put(x, 0, d.gid(*rng.choice(DEPTHS_TORCHES)))


def rooms():
    total = 0
    for folder, pack, variant in BIOMES:
        counts = {}
        for i, (kind, layout) in enumerate(ROOM_PLAN):
            counts[kind] = counts.get(kind, 0) + 1
            # Seeded off the names so a room's clutter is stable across runs
            # and a re-generate produces no diff noise.
            seed = hash_seed(folder, kind, counts[kind], layout)
            make_room(folder, pack, variant, kind, layout, counts[kind], seed)
            total += 1
        print("  rooms/%-13s %2d rooms  (%s pack%s)"
              % (folder, len(ROOM_PLAN), pack,
                 ", %s" % variant if variant else ""))
    print("  %d room templates in %d biomes" % (total, len(BIOMES)))


def hash_seed(*parts):
    """A stable seed. Python's hash() is salted per process and would make
    every run of this script produce different clutter."""
    import zlib
    return zlib.crc32("|".join(str(p) for p in parts).encode()) & 0xFFFFFFFF


if __name__ == "__main__":
    print("generating maps")
    village()
    rooms()
