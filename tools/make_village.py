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
  * they are twenty-two layers with the pack's own names. The game draws by
    role - ground, dressing, decor, walls, props, overhead - and works out what
    stops the player from two of those names. See gen/TiledRooms.java.

So this reads the pack's files and rewrites them into the game's contract. The
alternative was flattening twenty-two layers into six, which loses 256 tiles of
stacked ground detail, and that detail IS the look of the scene.

TREES. An object layer is split in two: the bottom tile of each vertical run
blocks the player and is drawn under them, everything above it is drawn over
them. That is a trunk and its canopy, a fence post and its rail, a well and its
roof - the split the old village did by hand for trees, done for everything the
pack put on the map.

WHAT BLOCKS INDOORS. The interior's `Walls` layer is not a wall layer: it is a
mask that paints the dark void over everywhere the house is not, and it also
carries some rooms' floors. Taking it whole would seal the building. The tiles
are classified by where they sit in walls_floor.png instead - two rows of that
sheet are floor and the rest is wall or void - which is a thing that can be
checked by looking at tools/preview_tiles.py's grid of it.

    python tools/make_village.py

Like every generated map here, `decor` is yours: draw into it in Tiled and
write_tmx carries it across a regeneration, tilesets you added included.
Everything else in these two files is overwritten.
"""

import collections
import os
import re
import shutil
import sys
import xml.etree.ElementTree as ET

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
# tileset a gid belongs to and put straight back afterwards.
FLIP_BITS = 0xF0000000

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

# The pack's fence, by tile column and row in exterior.png. It drew a closed
# run round the garden, so it has a left side and a right side and four
# corners; the divider down the middle of the village is the home's west wall,
# which is the left side with a cap at each end. Picking the commonest fence
# tile instead gives a column of horizontal rails, which reads as a ladder.
FENCE_TOP = (11, 2)
FENCE_SIDE = (11, 3)
FENCE_FOOT = (11, 4)

# Where the front door is, as a tile offset inside the home scene. The two
# animated Doors_windows tiles sitting in the pack's House_wall layer are the
# only door on it; read off, not guessed.
DOOR = (14, 8)

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
        # (firstgid, count, image) per tileset, in file order. The exterior
        # declares ground_grass_details twice at two firstgids, so these are
        # collapsed by image below rather than trusted one to one.
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

    def layer(self, name):
        if name not in self.made:
            self.made[name] = Layer(name, self.width, self.height)
        return self.made[name]

    def put(self, name, x, y, raw):
        image, local, flags = self.pack.resolve(raw)
        gid = flags | (self.tilesets[image].firstgid + local)
        self.layer(name).put(self.dx + x - self.minx, self.dy + y - self.miny, gid)

    def layers(self):
        return list(self.made.values())

    def pack_gid(self, image, column, row):
        """A tile picked off one of the pack's sheets by where it sits on it."""
        ts = self.tilesets[image]
        return ts.gid(column, row)


def order(layer):
    for i, role in enumerate(ROLE_ORDER):
        if layer.name == role or layer.name.startswith(role + "_"):
            return i
    raise SystemExit("layer %r plays no role the game draws, so it would be "
                     "loaded and never rendered" % layer.name)


# --------------------------------------------------------------------------
# The exterior
# --------------------------------------------------------------------------

# Which of the game's layers each of the pack's goes into. A pack name listed
# twice is the pack's own duplicate; both entries are kept, because they hold
# different tiles and merging them would silently drop one.
#
# `True` in the third column means an object layer: bottom row blocks and is
# drawn under the player, the rest is drawn over. See the module docstring.
EXTERIOR_PLAN = [
    ("Ground", "ground", False),
    ("Spots", "ground_spots", False),
    ("Road", "ground_road", False),
    ("Plates", "ground_plates", False),
    ("Grass", "dressing", False),
    ("Grass_detail6", "dressing_detail6", False),
    ("Grass_details3", "dressing_details3a", False),
    ("Grass_details3", "dressing_details3b", False),
    ("Grass_details4", "dressing_details4", False),
    ("Grass_details5", "dressing_details5", False),
    ("Grass_top_details", "dressing_top", False),
    ("cat", "dressing_cat", False),
    # The house is solid all the way up and its roof is drawn over the player,
    # so a character can stand behind the eaves. The door is not a hole in the
    # wall: HubScreen treats it the way it treats the torii, as somewhere to
    # stand and press a key, which does not depend on guessing the right tile.
    ("House_wall", "walls_house", False),
    ("windows1", "walls_windows1", False),
    ("windows2", "walls_windows2", False),
    ("House_roof", "overhead_roof", False),
    ("Birds", "overhead_birds", False),
    ("Objects4", "objects4", True),
    ("Objects1", "objects1", True),
    ("Objects2", "objects2", True),
    ("Fence", "fence", True),
    ("Objects3", "objects3", True),
]


def exterior_layers(pack, tilesets):
    scene = Scene(pack, tilesets, WIDTH, HEIGHT, HOME_X, HOME_Y)
    used = collections.Counter()
    for pack_name, cells in pack.layers:
        matches = [p for p in EXTERIOR_PLAN if p[0] == pack_name]
        if not matches:
            raise SystemExit("exterior layer %r is in no plan, so it would "
                             "vanish without a word" % pack_name)
        name, split = matches[min(used[pack_name], len(matches) - 1)][1:]
        used[pack_name] += 1
        for (x, y), raw in cells.items():
            if not split:
                scene.put(name, x, y, raw)
                continue
            # The bottom tile of a vertical run is the foot of whatever this
            # is; anything stacked on it is canopy, rail or eave.
            foot = (x, y + 1) not in cells
            scene.put(("props_" if foot else "overhead_") + name, x, y, raw)
    return scene


# --------------------------------------------------------------------------
# The village column
# --------------------------------------------------------------------------

def add_village(scene, pack):
    """Three houses, a torii and a fence, on the same grass as the home."""
    turf(scene, pack)
    house = scene.overworld["house"]
    props = scene.layer("props_village")
    spawns = []
    for i, row in enumerate(HOUSE_ROWS):
        hx, hy = HOUSES[i]
        props.stamp(house, 2, row, hx, hy, 4, 3)
        # On the ground in front of the door, which is the middle of a
        # four-wide house. A row lower than the house so the villager is
        # standing outside it rather than in the dark of its own doorway.
        spawns.append(("villager%d" % (i + 1), 4 * TILE, (HEIGHT - row - 4) * TILE))

    tx, ty = TORII
    props.stamp(house, 3, TORII_ROW, tx, ty, 3, 2)
    spawns.append(("gate", 4 * TILE + TILE // 2, (HEIGHT - TORII_ROW - 2) * TILE + 8))

    # The fence runs the height of the map with one gap to walk through, level
    # with the middle house. The pack's own fence tile, reused from the run it
    # drew round the player's garden.
    fence = scene.layer("props_fence")
    gap = HOUSE_ROWS[1] + 2
    for y in range(HEIGHT):
        if gap <= y <= gap + 2:
            continue
        top = y == 0 or y == gap + 3
        foot = y == HEIGHT - 1 or y == gap - 1
        cell = FENCE_TOP if top else FENCE_FOOT if foot else FENCE_SIDE
        fence.put(FENCE_X, y, scene.pack_gid("exterior.png", *cell))
    scatter(scene)
    return spawns


# Where nothing may be scattered: the houses, the torii, the fence and the
# strip in front of each door a villager stands on.
def scatter(scene, seed=11):
    """A handful of the pack's own bushes over the village column.

    Bare lawn beside a garden as dense as the pack's reads as unfinished
    ground rather than as open space, and anything drawn here that is not from
    this pack would show as a second artist. So the scatter is sampled from the
    tiles the home half already uses - single-tile ones only, found by asking
    which props have nothing stacked on them.
    """
    import random
    rng = random.Random(seed)
    singles = []
    for name in ("props_objects1", "props_objects2", "props_objects3"):
        below = scene.made.get(name)
        above = scene.made.get(name.replace("props_", "overhead_"))
        if below is None:
            continue
        for i, gid in enumerate(below.data):
            x, y = i % WIDTH, i // WIDTH
            if gid and (above is None or y == 0
                        or not above.data[(y - 1) * WIDTH + x]):
                singles.append(gid)
    if not singles:
        return
    props = scene.layer("props_scatter")
    blocked = scene.made["props_village"]
    for _ in range(60):
        x = rng.randrange(0, FENCE_X)
        y = rng.randrange(0, HEIGHT)
        i = y * WIDTH + x
        # Clear of the houses, of the tile below each of them, and of the gap
        # a player walks through.
        near = any(blocked.data[j] for j in neighbourhood(x, y))
        if blocked.data[i] or near or props.data[i]:
            continue
        props.put(x, y, rng.choice(singles))


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
    """
    dirt = base_tile(scene, pack, "Ground")
    grass = base_tile(scene, pack, "Grass")
    ground = scene.layer("ground")
    under = scene.layer("dressing")
    turfed = scene.layer("dressing_turf")
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


# --------------------------------------------------------------------------
# The interior
# --------------------------------------------------------------------------

# walls_floor.png is eleven columns by sixteen rows. Rows 9 and 10, columns 1
# to 7, are the two floors the pack draws rooms with; everything else on that
# sheet is a wall run, a doorway, or the dark void that fills the space around
# the building. Read off the labelled grid, which is why the rule is a pair of
# row numbers rather than a list of tile ids.
FLOOR_ROWS = (9, 10)
FLOOR_COLS = range(1, 8)

INTERIOR_PLAN = [
    ("Floor", "ground", False),
    ("Tile Layer 6", "ground_rugs", False),
    # The mask: floor tiles out into ground, everything else into walls.
    ("Walls", "walls", "mask"),
    ("Windows", "walls_windows", False),
    ("Boxes", "boxes", True),
    ("Objects1", "objects1", True),
    ("Objects2", "objects2", True),
]


def interior_layers(pack, tilesets, width, height):
    scene = Scene(pack, tilesets, width, height, 0, 0)
    plan = {name: (out, split) for name, out, split in INTERIOR_PLAN}
    columns = pack.columns("walls_floor.png")
    for pack_name, cells in pack.layers:
        if pack_name not in plan:
            raise SystemExit("interior layer %r is in no plan" % pack_name)
        name, split = plan[pack_name]
        for (x, y), raw in cells.items():
            if split == "mask":
                image, local, _ = pack.resolve(raw)
                floor = (image == "walls_floor.png"
                         and local // columns in FLOOR_ROWS
                         and local % columns in FLOOR_COLS)
                scene.put("ground_stone" if floor else name, x, y, raw)
            elif split:
                foot = (x, y + 1) not in cells
                scene.put(("props_" if foot else "overhead_") + name, x, y, raw)
            else:
                scene.put(name, x, y, raw)
    return scene


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

    # The door, and where the player stands when they arrive. Both in map
    # pixels with y running UP, which is how HubScreen reads them back: Tiled
    # writes object y down from the top and libGDX flips it on load.
    #
    # The marker goes on the doormat rather than on the door. A door is part of
    # a wall by nature, so a marker on the door itself is a marker inside solid
    # tiles, and the nearest a player can get to it is the cell in front - at
    # which point "are you close enough to knock" is measuring a distance the
    # player can never close. The first walkable cell below it is where they
    # will actually be standing.
    door_x = HOME_X + DOOR[0]
    blocked = solid(layers, WIDTH, HEIGHT)
    mat = HOME_Y + DOOR[1]
    while mat < HEIGHT - 1 and blocked[mat * WIDTH + door_x]:
        mat += 1
    spawns.append(("door", door_x * TILE + TILE, (HEIGHT - mat) * TILE - TILE // 2))
    spawns.append(("entry", door_x * TILE + TILE, (HEIGHT - mat - 2) * TILE))

    check_reachable(layers, WIDTH, HEIGHT,
                    [s for s in spawns if s[0] != "door"],
                    spawns[-1][1:])
    sets = list(overworld.values()) + list(tilesets.values())
    write_tmx(os.path.join(OUT, "village.tmx"), WIDTH, HEIGHT, sets, layers,
              tiles_rel=HOME_REL,
              objects=[("spawn", x, HEIGHT * TILE - y, name) for name, x, y in spawns])
    report("village.tmx", WIDTH, HEIGHT, sets, layers)


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

    # The way in and the way back out, both at the bottom of the biggest room
    # the house has. Found rather than written down: the pack's Walls layer is
    # a mask over everything that is not floor, so where the rooms actually
    # are is the answer to a flood fill, and a hand-picked tile here would be
    # a guess that only shows as wrong when a player walks into a wall.
    floor = largest_region(solid(layers, width, height), width, height)
    # Not under anything drawn over the player, either. The bottom of the room
    # is where a front door belongs, and the bottom of this room is under a
    # chair - which draws over whoever is standing there, so the character
    # arrives invisible and the house looks broken.
    covered = set()
    for layer in layers:
        if not layer.name.startswith("overhead"):
            continue
        for i, gid in enumerate(layer.data):
            if gid:
                covered.add((i % width, i // width))
    clear = [c for c in floor if c not in covered] or list(floor)
    bottom = max(y for _, y in clear)
    xs = sorted(x for x, y in clear if y == bottom)
    door = (xs[len(xs) // 2] * TILE + TILE // 2, (height - 1 - bottom) * TILE)
    check_reachable(layers, width, height, [("door", door[0], door[1])], door)
    write_tmx(os.path.join(OUT, "home.tmx"), width, height,
              list(tilesets.values()), layers, tiles_rel=HOME_REL,
              objects=[("spawn", door[0], height * TILE - door[1], "entry"),
                       ("spawn", door[0], height * TILE - door[1], "door")])
    report("home.tmx", width, height, list(tilesets.values()), layers)


# --------------------------------------------------------------------------
# Checking, because a sealed map looks fine in a preview
# --------------------------------------------------------------------------

def solid(layers, width, height):
    """The grid the game will build, by the same rule: walls and props block."""
    out = [False] * (width * height)
    for layer in layers:
        if not (order(layer) == ROLE_ORDER.index("walls")
                or order(layer) == ROLE_ORDER.index("props")):
            continue
        for i, gid in enumerate(layer.data):
            if gid:
                out[i] = True
    return out


def region(blocked, width, height, start):
    """Every cell reachable on foot from one, four ways."""
    seen = {start}
    queue = [start]
    while queue:
        x, y = queue.pop()
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if (0 <= nx < width and 0 <= ny < height and (nx, ny) not in seen
                    and not blocked[ny * width + nx]):
                seen.add((nx, ny))
                queue.append((nx, ny))
    return seen


def largest_region(blocked, width, height):
    best = set()
    for y in range(height):
        for x in range(width):
            if blocked[y * width + x] or (x, y) in best:
                continue
            here = region(blocked, width, height, (x, y))
            if len(here) > len(best):
                best = here
    return best


def check_reachable(layers, width, height, spawns, standing):
    """Every marker must be somewhere a player can walk up to.

    A map that seals the gate, or that starts the player inside a wall, looks
    perfectly correct in a render: the art is all there and the mistake is in
    the tiles that do not draw. So this runs before the file is written, the
    way make_maps.py flood-fills every room before writing it.

    `standing` is where the player begins. Markers are checked from the cell
    below them as well as their own, because a door is in a wall by nature and
    the player stands in front of it.
    """
    blocked = solid(layers, width, height)
    start = (standing[0] // TILE, height - 1 - (standing[1] // TILE))
    if blocked[start[1] * width + start[0]]:
        raise SystemExit("the player would start inside a wall at %s" % (start,))
    reach = region(blocked, width, height, start)
    for name, px, py in spawns:
        tile = (px // TILE, height - 1 - (py // TILE))
        near = [tile, (tile[0], tile[1] + 1), (tile[0], tile[1] - 1),
                (tile[0] + 1, tile[1]), (tile[0] - 1, tile[1])]
        if not any(c in reach for c in near):
            raise SystemExit("%r at %s cannot be walked up to from %s"
                             % (name, tile, start))
    print("    %d of %d tiles walkable, every marker reachable"
          % (len(reach), width * height))


def report(name, width, height, sets, layers):
    animated = sum(len(ts.tiles) for ts in sets)
    print("  %-12s %dx%d tiles (%dx%d px), %d tilesets, %d layers, %d animated"
          % (name, width, height, width * TILE, height * TILE,
             len(sets), len(layers), animated))


if __name__ == "__main__":
    print("generating the village and the home")
    install_tilesets()
    village()
    home()
