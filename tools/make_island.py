#!/usr/bin/env python3
"""Generate assets/maps/village.tmx: the island village of Sunnyside World.

The village is the pack's own showcase scene. The pack ships it as the
GameMaker room it was rendered from - see tools/sunnyside.py - and this script
turns that room into a Tiled map the game can walk around in, in stages, each
checked before the next is built on it:

  1. CONVERT. Every tile layer becomes a tile layer, every sprite a tile
     object, in GameMaker's own drawing order. The map is then rendered the
     way Tiled draws it and held against GameMaker's render of the room:
     every tile must match exactly, and every sprite to within a few pixels of
     resampling where the room scales or turns one. A conversion that is wrong
     anywhere stops here, before anything is layered on top of it.

    python tools/make_island.py            # build, check, write
    python tools/make_island.py --exact    # stop after the conversion check

Image paths in the written map are relative to wherever the map is written,
so the exact conversion can be written into review/ and opened in Tiled there.
"""
import math
import os
import sys
import xml.etree.ElementTree as ET

from PIL import Image, ImageChops

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import preview_map  # noqa: E402
import sunnyside as S  # noqa: E402
from make_maps import OUT, ROOT, TILE  # noqa: E402

GFX = os.path.join(ROOT, "assets", "gfx", "sunnyside")
TILES_DIR = os.path.join(GFX, "tiles")
SPRITES_DIR = os.path.join(GFX, "sprites")
REVIEW = os.path.join(ROOT, "review")

SUNNY = "tileset_sunnysideworld"
FOREST = "tileset_forest"

# A sprite drawn scaled or turned is resampled, and GameMaker's sampling and
# Tiled's disagree by a pixel along an edge. Anything else that differs between
# the two renders is a mistake in the conversion.
RESAMPLED_PIXELS = 400


# --------------------------------------------------------------------------
# The map being built
# --------------------------------------------------------------------------

class Sheet:
    """A tileset of the output map: a grid of tiles, or a strip of frames."""

    def __init__(self, name, image_path, tile_w, tile_h, animations=None):
        self.name = name
        self.image_path = image_path
        self.tile_w = tile_w
        self.tile_h = tile_h
        with Image.open(image_path) as im:
            self.width, self.height = im.size
        self.columns = self.width // tile_w
        self.rows = self.height // tile_h
        self.count = self.columns * self.rows
        # local tile id -> [(local tile id, milliseconds), ...]
        self.animations = animations or {}
        self.firstgid = 0


class Grid:
    """A tile layer: Tiled gids, flip bits included, row by row."""

    def __init__(self, name, width, height):
        self.name = name
        self.width = width
        self.height = height
        self.data = [0] * (width * height)

    def put(self, x, y, gid):
        if 0 <= x < self.width and 0 <= y < self.height:
            self.data[y * self.width + x] = gid

    def get(self, x, y):
        if 0 <= x < self.width and 0 <= y < self.height:
            return self.data[y * self.width + x]
        return 0


class Thing:
    """A tile object: one placed sprite, in Tiled's terms.

    (x, y) is the bottom-left corner of the picture before it is turned;
    `rotation` is clockwise degrees about that corner; width and height are
    the size it is drawn at. `frame` is the frame of its animation it starts
    on, and `speed` multiplies how fast that animation plays.
    """

    def __init__(self, sheet, x, y, width, height, rotation=0.0, flip_h=False,
                 flip_v=False, frame=0, speed=1.0, name="", kind=""):
        self.sheet = sheet
        self.tile = 0
        self.x = x
        self.y = y
        self.width = width
        self.height = height
        self.rotation = rotation
        self.flip_h = flip_h
        self.flip_v = flip_v
        self.frame = frame
        self.speed = speed
        self.name = name
        self.kind = kind
        self.properties = {}


class Group:
    """An object layer."""

    def __init__(self, name, things=None, visible=True, color=None):
        self.name = name
        self.things = things or []
        self.visible = visible
        self.color = color


class Island:
    def __init__(self, width, height):
        self.width = width
        self.height = height
        self.sheets = []
        self.layers = []

    def sheet(self, sheet):
        if sheet not in self.sheets:
            self.sheets.append(sheet)
        return sheet

    def grid(self, name):
        for layer in self.layers:
            if isinstance(layer, Grid) and layer.name == name:
                return layer
        raise SystemExit("no tile layer %s" % name)

    def group(self, name):
        for layer in self.layers:
            if isinstance(layer, Group) and layer.name == name:
                return layer
        raise SystemExit("no object layer %s" % name)

    def number(self):
        """Hand out firstgids, in the order the tilesets were added."""
        gid = 1
        for sheet in self.sheets:
            sheet.firstgid = gid
            gid += sheet.count


# --------------------------------------------------------------------------
# Stage 1: the room, converted
# --------------------------------------------------------------------------

# GameMaker layer -> the name the game draws it by. The conversion keeps the
# room's order exactly; which rows of a building draw over the player is a
# later stage's business.
TILE_LAYER_NAMES = {
    "sea": "ground_sea",
    "clouds_02": "ground_clouds_low",
    "land": "ground_land",
    "paths": "ground_paths",
    "shadows": "ground_shadows",
    "decoration_01": "dressing_deco1",
    "forest": "dressing_forest",
    "building": "dressing_building",
    "walls": "dressing_walls",
    "decoration_02": "dressing_deco2",
    "decoration_03": "dressing_deco3",
    "cloud_shadow": "overhead_cloud_shadow",
    "clouds_01": "overhead_clouds",
}
SPRITE_LAYER_NAMES = {
    "Assets_2": "sprites_under",
    "Assets_1": "sprites",
}


def shortest_cycle(frames):
    """An animation's frames with any whole repeats of itself dropped.

    GameMaker keeps sixteen frames for every animated tile, so a four-frame
    ripple is stored four times over; Tiled only needs it once.
    """
    n = len(frames)
    for period in range(1, n + 1):
        if n % period == 0 and all(frames[i] == frames[i % period] for i in range(n)):
            return frames[:period]
    return frames


def export_tilesets():
    """The two tileset images, as the game will load them."""
    os.makedirs(TILES_DIR, exist_ok=True)
    sunny = S.tilesheet(SUNNY)
    sunny_path = os.path.join(TILES_DIR, "sunnyside.png")
    sunny.image.save(sunny_path)
    forest = S.tilesheet(FOREST)
    forest_path = os.path.join(TILES_DIR, "forest.png")
    forest.image.save(forest_path)
    animations = {index: shortest_cycle(frames) for index, frames in sunny.animations.items()}
    # The forest's trees are 32px tiles. The map grid is 16px, so the forest
    # sheet is declared as a 16px grid over the same image and each tree tile
    # is laid as the four quarters it is made of.
    return (Sheet("sunnyside", sunny_path, TILE, TILE, animations),
            Sheet("sunnyside_forest", forest_path, TILE, TILE))


_sprite_sheets = {}


def sprite_sheet(name):
    """A tileset for one of the pack's sprites: its frames in a strip, animated."""
    if name not in _sprite_sheets:
        spr = S.sprite(name)
        os.makedirs(SPRITES_DIR, exist_ok=True)
        path = os.path.join(SPRITES_DIR, name + ".png")
        spr.strip().save(path)
        animations = {}
        if len(spr.frames) > 1:
            ms = spr.frame_ms()
            animations[0] = [(i, ms) for i in range(len(spr.frames))]
        _sprite_sheets[name] = Sheet(name, path, spr.width, spr.height, animations)
    return _sprite_sheets[name]


def thing_from(placement, sheet):
    """A GameMaker placement as a Tiled tile object that draws the same picture.

    GameMaker pins a sprite's origin to (x, y), scales about it and turns it
    counter-clockwise about it. Tiled pins the bottom-left corner, stretches
    to a width and height, mirrors inside that box and turns clockwise about
    the corner. Turning a picture about one point is turning it about any
    other point of it plus a shift, so the corner is found by turning it about
    the origin, and the angle only changes sign.
    """
    spr = S.sprite(placement.sprite)
    w, h = spr.width, spr.height
    ox, oy = spr.origin
    sx, sy = placement.scale_x, placement.scale_y
    width, height = abs(sx) * w, abs(sy) * h
    left = -(w - ox) * abs(sx) if sx < 0 else -ox * sx
    top = -(h - oy) * abs(sy) if sy < 0 else -oy * sy
    dx, dy = left, top + height
    t = math.radians(placement.rotation)
    c, s = math.cos(t), math.sin(t)
    x = placement.x + dx * c + dy * s
    y = placement.y - dx * s + dy * c
    thing = Thing(sheet, x, y, width, height, rotation=-placement.rotation,
                  flip_h=sx < 0, flip_v=sy < 0, frame=placement.frame % len(spr.frames),
                  speed=placement.speed)
    # Kept so later stages can find a sprite by where the room put it, which
    # is a stable name for it; its Tiled corner moves with its size and turn.
    thing.placement = placement
    return thing


def convert(room):
    """Stage 1: the room as a Tiled map, layer for layer, in its own order."""
    island = Island(room.width, room.height)
    sunny, forest = export_tilesets()
    island.sheet(sunny)
    island.sheet(forest)
    forest_columns = S.tilesheet(FOREST).columns
    for _, name, content in room.layers:
        if isinstance(content, S.TileLayer):
            grid = Grid(TILE_LAYER_NAMES[name], room.width, room.height)
            if content.tileset == FOREST:
                convert_forest(content, grid, forest, forest_columns)
            else:
                for i, raw in enumerate(content.cells):
                    index = raw & S.INDEX
                    if index:
                        grid.data[i] = (sunny, index, S.tiled_flags(raw))
            island.layers.append(grid)
        else:
            group = Group(SPRITE_LAYER_NAMES[name])
            for placement in content:
                sheet = island.sheet(sprite_sheet(placement.sprite))
                group.things.append(thing_from(placement, sheet))
            island.layers.append(group)
    return island


def convert_forest(content, grid, forest, columns):
    """The 32px forest layer, laid out as quarters on the 16px grid."""
    quarter_columns = forest.columns
    for i, raw in enumerate(content.cells):
        index = raw & S.INDEX
        if not index:
            continue
        if raw & (S.MIRROR | S.FLIP | S.ROTATE):
            raise SystemExit("a forest tile is flipped, which the quartering "
                             "below does not handle")
        cx, cy = index % columns, index // columns
        x, y = (i % content.width) * 2, (i // content.width) * 2
        for dy in (0, 1):
            for dx in (0, 1):
                local = (2 * cy + dy) * quarter_columns + 2 * cx + dx
                grid.put(x + dx, y + dy, (forest, local, 0))


# --------------------------------------------------------------------------
# Stage 3: the village's own changes
# --------------------------------------------------------------------------

# How far the finished map reaches past the room on each side, in tiles. The
# room's picture stops mid-building at its left, right and bottom edges and
# mid-hill at the top; this is the room the missing halves are drawn into.
LEFT, TOP, RIGHT, BOTTOM = 8, 4, 8, 10

# The room's sea is this block of tiles, repeated.
SEA = [[1294, 1291, 1292, 1293],
       [1358, 1355, 1356, 1357],
       [1166, 1163, 1164, 1165],
       [1230, 1227, 1228, 1229]]

# Room tile coordinates, inclusive (x0, y0, x1, y1).
BLUE_HOUSE = (39, 24, 45, 29)        # the building, its eaves included
BLUE_FRONT = (39, 24, 45, 30)        # its benches, windows, door and sunflower
GREEN_HOUSE = (50, 25, 53, 28)
GREEN_BENCH = (52, 29, 54, 29)       # the porch bench in front of it
ORANGE_DOOR = (55, 28)               # tile 1764, the same door the green house had

# Where the player's house goes, as the room tile its top-left corner lands
# on. Its walls then stand exactly where the blue house's did, x39-46 and
# y24-29, and its roof reaches three rows up over the path behind.
HOME_AT = (38, 21)
# The house on the Top-Down Home pack's Exterior.tmx, as the corner of its
# roof in that scene's own coordinates (relative to the scene's top-left).
HOME_CORNER = (8, 1)
HOME_PARTS = [("House_wall", "dressing_house"),
              ("windows1", "dressing_house_windows"),
              ("windows2", "dressing_house_shutters"),
              ("House_roof", "overhead_house_roof")]

# The torii, from the Ninja Adventure house sheet, centred on the green
# house's front and standing on the row its porch stood on.
TORII_FOOT = (52 * TILE, 29 * TILE)

# The pack's goblins play twenty actions; its humans play the same twenty,
# frame for frame, as a body, a hairstyle and a tool drawn over each other.
HUMAN_ACTIONS = {
    "spr_attack": ("ATTACK", "attack", 10), "spr_axe": ("AXE", "axe", 10),
    "spr_carry": ("CARRY", "carry", 8), "spr_casting": ("CASTING", "casting", 15),
    "spr_caught": ("CAUGHT", "caught", 10), "spr_death": ("DEATH", "death", 13),
    "spr_dig": ("DIG", "dig", 13), "spr_doing": ("DOING", "doing", 8),
    "spr_hammering": ("HAMMERING", "hamering", 23), "spr_hurt": ("HURT", "hurt", 8),
    "spr_idle": ("IDLE", "idle", 9), "spr_jump": ("JUMP", "jump", 9),
    "spr_mining": ("MINING", "mining", 10), "spr_reeling": ("REELING", "reeling", 13),
    "spr_roll": ("ROLL", "roll", 10), "spr_run": ("RUN", "run", 8),
    "spr_swimming": ("SWIMMING", "swimming", 12), "spr_waiting": ("WAITING", "waiting", 9),
    "spr_walking": ("WALKING", "walk", 8), "spr_watering": ("WATERING", "watering", 5),
}
HAIRSTYLES = ["shorthair", "longhair", "curlyhair", "mophair", "bowlhair", "spikeyhair"]
# Where the room's goblins become people. The farm island and the two mines
# stay goblin country, and the graveyard and the beach keep their skeletons.
HUMAN_AREAS = [
    (18, 12, 70, 40),     # the forest, the village and the slopes round it
    (44, 0, 70, 12),      # the ranch on the hilltop
    (66, 10, 86, 40),     # the house on the right and its beach
    (0, 18, 16, 36),      # the stilt tower, the pier and the statue
    (20, 40, 32, 48),     # the fisherman on the little dock
]
HUMANS_DIR = os.path.join(GFX, "humans")


def expand(island):
    """Grow the map round the room, with open sea in everything new."""
    width, height = island.width + LEFT + RIGHT, island.height + TOP + BOTTOM
    for layer in island.layers:
        if isinstance(layer, Grid):
            old = layer
            data = [0] * (width * height)
            for y in range(old.height):
                for x in range(old.width):
                    data[(y + TOP) * width + x + LEFT] = old.data[y * old.width + x]
            layer.data, layer.width, layer.height = data, width, height
        else:
            for thing in layer.things:
                thing.x += LEFT * TILE
                thing.y += TOP * TILE
    sea = island.grid("ground_sea")
    sunny = island.sheets[0]
    for y in range(height):
        for x in range(width):
            if not sea.get(x, y):
                sea.put(x, y, (sunny, SEA[(y - TOP) % 4][(x - LEFT) % 4], 0))
    island.width, island.height = width, height


def room_box(box):
    """A room rectangle in map tiles."""
    x0, y0, x1, y1 = box
    return x0 + LEFT, y0 + TOP, x1 + LEFT, y1 + TOP


def clear(island, names, box):
    x0, y0, x1, y1 = room_box(box)
    for name in names:
        grid = island.grid(name)
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                grid.put(x, y, 0)


def remove_sprites(island, box, keep=()):
    """Drop every sprite the room placed inside a rectangle of room tiles."""
    x0, y0, x1, y1 = box
    gone = 0
    for layer in island.layers:
        if not isinstance(layer, Group):
            continue
        kept = []
        for thing in layer.things:
            p = getattr(thing, "placement", None)
            if (p is not None and p.sprite not in keep
                    and x0 * TILE <= p.x < (x1 + 1) * TILE
                    and y0 * TILE <= p.y < (y1 + 1) * TILE):
                gone += 1
                continue
            kept.append(thing)
        layer.things = kept
    return gone


# Sprites moved out of the way of what the village put where they stood, keyed
# by where the room placed them: two sheep from under the player's house to the
# nearest open grass, and a villager running on the spot - his shadow with him -
# from where the herbalist stands at the shop door to the street by the fish
# stall, where he no longer runs through her.
MOVED_SPRITES = {
    ("spr_deco_sheep_01", 649.0, 354.0): (618.0, 330.0),
    ("spr_deco_sheep_01", 663.0, 363.25): (632.0, 339.25),
    ("spr_run", 901.0, 480.0): (1024.0, 488.0),
    ("spr_deco_charactershadow", 902.0, 488.0): (1025.0, 496.0),
}


def move_sprites(island):
    for layer in island.layers:
        if not isinstance(layer, Group):
            continue
        for thing in layer.things:
            p = getattr(thing, "placement", None)
            key = p and (p.sprite, p.x, p.y)
            if key in MOVED_SPRITES:
                nx, ny = MOVED_SPRITES[key]
                thing.x += nx - p.x
                thing.y += ny - p.y


# The clouds round the room's edge are its frame. A cloud within this many
# tiles of an edge moves out with that edge, so the frame ends up round the
# map and what it used to hide is uncovered for the missing halves to be drawn.
EDGE_BAND = 6
CLOUD_LAYERS = ["overhead_clouds", "overhead_cloud_shadow", "ground_clouds_low"]


def blobs(cells):
    """Connected groups of cells, diagonals included."""
    seen, out = set(), []
    for start in sorted(cells):
        if start in seen:
            continue
        seen.add(start)
        group, queue = [], [start]
        while queue:
            x, y = queue.pop()
            group.append((x, y))
            for dx in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    n = (x + dx, y + dy)
                    if n in cells and n not in seen:
                        seen.add(n)
                        queue.append(n)
        out.append(group)
    return out


def shift_clouds(island, room_w, room_h, report=False):
    """Move each cloud that touches the room's frame out to the map's frame."""
    grids = [island.grid(n) for n in CLOUD_LAYERS]
    cells = set()
    for g in grids:
        cells.update((i % g.width, i // g.width) for i, c in enumerate(g.data) if c)
    left, top, right, bottom = LEFT, TOP, LEFT + room_w - 1, TOP + room_h - 1
    moves = []
    for group in blobs(cells):
        xs = [x for x, _ in group]
        ys = [y for _, y in group]
        near_l = min(xs) - left < EDGE_BAND
        near_r = right - max(xs) < EDGE_BAND
        near_t = min(ys) - top < EDGE_BAND
        near_b = bottom - max(ys) < EDGE_BAND
        dx = (-LEFT if near_l and not near_r else RIGHT if near_r and not near_l else 0)
        dy = (-TOP if near_t and not near_b else BOTTOM if near_b and not near_t else 0)
        if report:
            print("      cloud x%d-%d y%d-%d cells=%d -> (%d,%d)"
                  % (min(xs) - LEFT, max(xs) - LEFT, min(ys) - TOP, max(ys) - TOP,
                     len(group), dx, dy))
        if dx or dy:
            moves.append((group, dx, dy))
    for g in grids:
        lifted = []
        for group, dx, dy in moves:
            for x, y in group:
                cell = g.get(x, y)
                if cell:
                    lifted.append((x + dx, y + dy, cell))
                    g.put(x, y, 0)
        for x, y, cell in lifted:
            g.put(x, y, cell)
    return len(moves)


def insert_after(island, name, layers):
    at = [l.name for l in island.layers].index(name) + 1
    island.layers[at:at] = layers


def home_house(island):
    """The player's house from the Top-Down Home pack: walls, windows, roof, smoke."""
    import make_village as V
    V.install_tilesets()
    pack = V.Pack(os.path.join(V.PACK, "Exterior.tmx"))
    minx, miny, _, _ = pack.bounds()
    sheets = {}
    grids = []
    for pack_name, out_name in HOME_PARTS:
        cells = next(c for n, c in pack.layers if n == pack_name)
        grid = Grid(out_name, island.width, island.height)
        for (px, py), raw in sorted(cells.items()):
            if pack.art(raw) == 0:
                continue   # two window tiles that hang below the house with nothing on them
            image, local, flags = pack.resolve(raw)
            if image not in sheets:
                filename = V.norm(image)
                animations = {}
                for tile_id, node in pack.animations[image].items():
                    animations[tile_id] = [(int(f.get("tileid")), int(f.get("duration")))
                                           for f in node.find("animation").findall("frame")]
                sheets[image] = island.sheet(Sheet(os.path.splitext(filename)[0],
                                                   os.path.join(V.HOME_TILES, filename),
                                                   TILE, TILE, animations))
            rx, ry = px - minx - HOME_CORNER[0], py - miny - HOME_CORNER[1]
            grid.put(HOME_AT[0] + LEFT + rx, HOME_AT[1] + TOP + ry, (sheets[image], local, flags))
        grids.append(grid)
    return grids


def torii(island):
    """The torii the old village led to the world map through, as one sprite."""
    import make_maps
    house = make_maps.build_tilesets()["house"]
    sheet_path = os.path.join(ROOT, "assets", "gfx", "tiles", "overworld", house.filename)
    tx, ty = make_maps.TORII
    with Image.open(sheet_path) as im:
        art = im.convert("RGBA").crop((tx * TILE, ty * TILE, (tx + 3) * TILE, (ty + 2) * TILE))
    os.makedirs(SPRITES_DIR, exist_ok=True)
    path = os.path.join(SPRITES_DIR, "torii.png")
    art.save(path)
    sheet = island.sheet(Sheet("torii", path, art.width, art.height))
    fx, fy = TORII_FOOT
    thing = Thing(sheet, fx + LEFT * TILE - art.width / 2.0, fy + TOP * TILE,
                  art.width, art.height, name="gate", kind="gate")
    return thing


_human_sheets = {}


def human_sheet(island, hair, gm_name):
    """A strip of one person doing one of the goblins' twenty actions."""
    key = (hair, gm_name)
    if key not in _human_sheets:
        folder, action, frames = HUMAN_ACTIONS[gm_name]
        strip = Image.new("RGBA", (96 * frames, 64))
        for part in ("base", hair, "tools"):
            path = os.path.join(S.LOOSE, "Characters", "Human", folder,
                                "%s_%s_strip%d.png" % (part, action, frames))
            with Image.open(path) as im:
                layer = im.convert("RGBA")
            if layer.size != strip.size:
                raise SystemExit("%s is %dx%d, not %d frames of 96x64"
                                 % (path, layer.width, layer.height, frames))
            strip.alpha_composite(layer)
        os.makedirs(HUMANS_DIR, exist_ok=True)
        out = os.path.join(HUMANS_DIR, "%s_%s.png" % (hair, action))
        strip.save(out)
        ms = S.sprite(gm_name).frame_ms()
        _human_sheets[key] = Sheet("human_%s_%s" % (hair, action), out, 96, 64,
                                   {0: [(i, ms) for i in range(frames)]})
    return island.sheet(_human_sheets[key])


def people(island):
    """Tag every character, and turn the goblins of the human areas into people."""
    swapped = 0
    for layer in island.layers:
        if not isinstance(layer, Group):
            continue
        for thing in layer.things:
            p = getattr(thing, "placement", None)
            if p is None:
                continue
            if p.sprite.startswith("skeleton_"):
                thing.kind = "npc"
                thing.properties.update({"race": "skeleton", "action": p.sprite[len("skeleton_"):]})
                continue
            if p.sprite not in HUMAN_ACTIONS:
                continue
            thing.kind = "npc"
            action = HUMAN_ACTIONS[p.sprite][1]
            tx, ty = p.x / TILE, p.y / TILE
            if any(x0 <= tx <= x1 + 1 and y0 <= ty <= y1 + 1 for x0, y0, x1, y1 in HUMAN_AREAS):
                hair = HAIRSTYLES[swapped % len(HAIRSTYLES)]
                thing.sheet = human_sheet(island, hair, p.sprite)
                thing.properties.update({"race": "human", "hair": hair, "action": action})
                swapped += 1
            else:
                thing.properties.update({"race": "goblin", "action": action})
    return swapped


def village_changes(island):
    """Stage 3: the three houses, and who lives here."""
    room_w, room_h = island.width, island.height
    expand(island)
    moved = shift_clouds(island, room_w, room_h, report="--clouds" in sys.argv)
    print("  stage 3: %d clouds moved out to the map's edge" % moved)
    # The blue house goes, and the player's house stands on its ground.
    clear(island, ["dressing_building"], BLUE_HOUSE)
    clear(island, ["dressing_deco2"], BLUE_FRONT)
    removed = remove_sprites(island, (BLUE_HOUSE[0], BLUE_HOUSE[1] - 1, BLUE_HOUSE[2], BLUE_HOUSE[3] + 1))
    house = home_house(island)
    insert_after(island, "dressing_deco3", house[:-1])
    # The roof goes over every sprite, not just over the player: the house is
    # a size bigger than the one it replaces, and what now stands behind it -
    # the windmill's shadow - belongs under its eaves, not painted on them.
    insert_after(island, "sprites", house[-1:])
    move_sprites(island)
    # The green house goes, and the torii stands where its porch was.
    clear(island, ["dressing_building"], GREEN_HOUSE)
    clear(island, ["dressing_deco2"], (51, 28, 51, 28))
    clear(island, ["dressing_deco3"], GREEN_BENCH)
    island.group("sprites").things.append(torii(island))
    swapped = people(island)
    print("  stage 3: %dx%d tiles; blue house and %d sprites on it replaced by the home, "
          "green house by the torii; %d goblins became people"
          % (island.width, island.height, removed, swapped))
    missing_halves(island)


# --------------------------------------------------------------------------
# Stage 4: the missing halves
# --------------------------------------------------------------------------
#
# The room's picture stops mid-building and mid-island at its edges. What is
# drawn here finishes them, out of the pack's own tiles laid the way the room
# lays them: every corner and every run of cliff below comes from a place in
# the room where the pack closes an island itself.

PAINT_LAYERS = {
    "land": "ground_land", "shadows": "ground_shadows", "deco1": "dressing_deco1",
    "building": "dressing_building", "walls": "dressing_walls", "deco2": "dressing_deco2",
}


class Painter:
    """Tiles written in room coordinates, in the room's own codes: 198mf is
    tile 198 mirrored and flipped, 267r is tile 267 turned a quarter clockwise."""

    def __init__(self, island):
        self.island = island
        self.sunny = island.sheets[0]
        self.count = 0

    def cell(self, code):
        index = int("".join(ch for ch in code if ch.isdigit()))
        raw = (index | (S.MIRROR if "m" in code else 0) | (S.FLIP if "f" in code else 0)
               | (S.ROTATE if "r" in code else 0))
        return (self.sunny, index, S.tiled_flags(raw))

    def put(self, layer, x, y, code):
        grid = self.island.grid(PAINT_LAYERS[layer])
        grid.put(x + LEFT, y + TOP, 0 if code is None else self.cell(code))
        self.count += 1

    def column(self, layer, x, y, codes):
        for i, code in enumerate(codes):
            if code:
                self.put(layer, x, y + i, code)

    def copy(self, layer, sx, sy, dx, dy):
        grid = self.island.grid(PAINT_LAYERS[layer])
        grid.put(dx + LEFT, dy + TOP, grid.get(sx + LEFT, sy + TOP))
        self.count += 1

    def body(self, xa, xb, y):
        """One more row of an island's grass, between its left and right edges."""
        self.put("land", xa, y, "198")
        for x in range(xa + 1, xb):
            self.put("land", x, y, "193")
        self.put("land", xb, y, "259")

    def close_south(self, xa, xb, e, left=True, right=True):
        """An island's south cliff, from its left edge column to its right one.

        `e` is the row the grass ends on. The straight run alternates the two
        cliff faces the way the room alternates them; the corners are the ones
        the pack closes the farm island's northern neighbour with.
        """
        for x in range(xa + 1, xb):
            k = (x - xa) % 2
            self.column("land", x, e, ["261", "267r" if k else "203",
                                       "203" if k else "267r", "148"])
            self.put("shadows", x, e + 3, "76")
            self.put("shadows", x, e + 4, "76")
        if left:
            self.column("land", xa, e, ["262", "266", "147m", "403m"])
            self.column("land", xa - 1, e, ["403mf", "148r", "403m"])
            self.put("shadows", xa, e + 2, "77mf")
            self.put("shadows", xa, e + 3, "76")
            self.put("shadows", xa, e + 4, "77")
        if right:
            self.column("land", xb, e, ["263", "266m", "147", "403"])
            self.column("land", xb + 1, e, ["403f", "148fr", "403"])
            self.put("shadows", xb, e + 2, "77f")
            self.put("shadows", xb, e + 3, "76")
            self.put("shadows", xb - 1, e + 4, "77mfr")


def missing_halves(island):
    """Stage 4: finish what the room's frame cut through."""
    paint = Painter(island)

    # The top: the pine beside the blue house on the hill has lost its tip.
    paint.put("deco1", 54, -1, "244")

    # The left: the stilt tower is 32px wide, centred on its middle tile, and
    # the room drew its right three quarters.
    paint.column("building", -1, 31, ["608", "736", "800", "864", "924m"])

    # The right: the house on the island. Its floor gets its right-hand border,
    # its island the grass under that and an edge beyond it.
    paint.put("building", 86, 15, "584m")
    for y in range(16, 24):
        paint.put("building", 86, y, "582m")
    paint.put("building", 86, 24, "646m")
    for x in (86, 87, 88):
        for y in range(15, 27):
            paint.put("land", x, y, "193")
        for y in range(27, 32):
            paint.copy("land", 84 + (x - 86) % 2, y, x, y)
            paint.copy("shadows", 84 + (x - 86) % 2, y, x, y)
    # Its north-east corner cut on the diagonal, a step a row, the way the
    # rail island's is.
    paint.put("land", 86, 14, "196")
    paint.put("land", 87, 14, "196")
    paint.put("land", 88, 14, "260")
    paint.put("land", 88, 15, "195")
    paint.put("land", 89, 15, "260")
    for y in range(16, 27):
        paint.put("land", 89, y, "259")
    paint.close_south(88, 89, 27, left=False)
    pine(paint, 87, 15)
    bush(paint, 87, 20)
    paint.put("deco1", 88, 24, "227")

    # The bottom: the islet with the tree, and the long island the rails run
    # along. One more row of grass under the rails, then the cliff.
    for xa, xb in ((32, 35), (41, 64)):
        paint.body(xa, xb, 48)
        paint.close_south(xa, xb, 49)

    # The pit's island. The pit itself closes a column past the room's edge:
    # the room already drew its west wall down to row 44, and only its floor's
    # last three rows bulge out to x0. Its south wall steps down eastwards the
    # way its floor does - x0-2 on row 47, x3-6 on 48, x7-13 on 50.
    paint.put("land", -2, 39, "200")
    paint.put("land", -1, 39, "198r")
    for y in range(40, 52):
        paint.put("land", -2, y, "198")
    for y in range(40, 43):
        paint.put("land", -1, y, "193")
    paint.column("land", -1, 43, ["1802", "1929", "1929", "1993", "2058"])
    for y in range(48, 52):
        for x in range(-1, 7):
            paint.put("land", x, y, "193")
    paint.put("land", 3, 48, "2058")
    for x in (4, 5, 6):
        paint.put("land", x, 48, "2059")
    for x in range(7, 14):
        paint.copy("land", x, 47, x, 48)
    paint.put("land", 7, 49, "1994")
    for x in range(8, 13):
        paint.put("land", x, 49, "1995")
    paint.put("land", 13, 49, "1996")
    paint.put("land", 7, 50, "2058")
    for x in range(8, 14):
        paint.put("land", x, 50, "2059")
    paint.column("land", 14, 48, ["1929m", "1997", "2061"])
    for x in range(7, 15):
        paint.put("land", x, 51, "193")
    for y in range(48, 51):
        paint.put("land", 15, y, "2125mf")
    paint.put("land", 15, 51, "193")
    for y in range(48, 52):
        paint.put("land", 16, y, "198mf")
    paint.close_south(-2, 16, 52)
    pine(paint, -1, 48)
    bush(paint, 1, 49)
    tree(paint, 4, 49)
    paint.put("deco1", 0, 51, "227")
    paint.put("deco1", 9, 51, "93")
    paint.put("deco1", 12, 51, "226")

    # Ways the room never needed, since nobody in it walks anywhere: down the
    # statue island's cliff to the pit's island, down into the pit, and a
    # plank from the beach to the fisherman's dock.
    paint.column("deco1", 9, 36, ["746", "810", "810", "810", "874"])
    paint.column("deco1", 9, 42, ["746", "874"])
    paint.put("deco1", 27, 43, "360")
    print("  stage 4: %d tiles painted to finish the cut edges" % paint.count)


def pine(paint, x, y):
    """The pack's pine, three tiles tall, with its top at (x, y)."""
    paint.column("deco1", x, y, ["244", "308", "372"])


def bush(paint, x, y):
    for dy, row in enumerate((("113", "114"), ("177", "178"))):
        for dx, code in enumerate(row):
            paint.put("deco1", x + dx, y + dy, code)


def tree(paint, x, y):
    """The pack's round tree, two tiles wide and three tall."""
    for dy, row in enumerate((("435", "436"), ("499", "500"), ("563", "564"))):
        for dx, code in enumerate(row):
            paint.put("deco1", x + dx, y + dy, code)


# --------------------------------------------------------------------------
# Stage 2: layered for play
# --------------------------------------------------------------------------

# How many rows at the foot of each column of a building stand in the
# player's world: drawn under the player, and where it blocks. Everything above
# is roof, drawn over a player walking behind the building. Three rows is the
# front wall of the pack's houses, door and windows included.
BASE_ROWS = 3

# Buildings the pack draws cut away, their floors open to the sky with people
# standing on them. Nothing of these is lifted over the player. In the room's
# own tile coordinates, inclusive.
INTERIORS = [(74, 15, 86, 25)]

# The ground shadows the pack lays under its people and animals. They go in a
# layer of their own under every sprite, so that a shadow a player stands
# behind is not drawn over the player's feet.
SHADOWS = {"spr_deco_charactershadow", "spr_deco_shadow"}

_images = {}


def sheet_image(sheet):
    if sheet.image_path not in _images:
        _images[sheet.image_path] = Image.open(sheet.image_path).convert("RGBA")
    return _images[sheet.image_path]


def cell_art(cell):
    """A tile layer cell's picture, flipped the way Tiled flips it."""
    sheet, local, flags = cell
    tx, ty = local % sheet.columns, local // sheet.columns
    tile = sheet_image(sheet).crop((tx * sheet.tile_w, ty * sheet.tile_h,
                                    (tx + 1) * sheet.tile_w, (ty + 1) * sheet.tile_h))
    if flags & S.TILED_D:
        tile = tile.transpose(Image.TRANSPOSE)
    if flags & S.TILED_H:
        tile = tile.transpose(Image.FLIP_LEFT_RIGHT)
    if flags & S.TILED_V:
        tile = tile.transpose(Image.FLIP_TOP_BOTTOM)
    return tile


def frame_art(sheet, local, frame):
    """One frame of a tile, following its animation when it has one."""
    if local in sheet.animations:
        frames = sheet.animations[local]
        local = frames[frame % len(frames)][0]
    tx, ty = local % sheet.columns, local // sheet.columns
    return sheet_image(sheet).crop((tx * sheet.tile_w, ty * sheet.tile_h,
                                    (tx + 1) * sheet.tile_w, (ty + 1) * sheet.tile_h))


# Half the width of the strip down the middle of a person's frame that their
# feet are looked for in. The pack draws everyone standing on the frame's centre
# line, a body about eight pixels across.
PERSON_BAND = 4


class Art:
    """A tile object as it lands on the map: its pixels and where they are."""

    def __init__(self, thing):
        art = frame_art(thing.sheet, thing.tile, thing.frame)
        if thing.flip_h:
            art = art.transpose(Image.FLIP_LEFT_RIGHT)
        if thing.flip_v:
            art = art.transpose(Image.FLIP_TOP_BOTTOM)
        sx, sy = thing.width / art.width, thing.height / art.height
        degrees = -thing.rotation
        t = math.radians(degrees)
        c, s = math.cos(t), math.sin(t)
        xs, ys = [], []
        for u, v in ((0, 0), (art.width, 0), (0, art.height), (art.width, art.height)):
            dx, dy = u * sx, (v - art.height) * sy
            xs.append(thing.x + dx * c + dy * s)
            ys.append(thing.y - dx * s + dy * c)
        self.x0 = int(math.floor(min(xs))) - 1
        self.y0 = int(math.floor(min(ys))) - 1
        canvas = Image.new("RGBA", (int(math.ceil(max(xs))) + 2 - self.x0,
                                    int(math.ceil(max(ys))) + 2 - self.y0))
        S.draw_transformed(canvas, art, (0, art.height),
                           (thing.x - self.x0, thing.y - self.y0), (sx, sy), degrees)
        self.image = canvas
        self.alpha = canvas.getchannel("A").point(lambda a: 255 if a else 0)
        box = self.alpha.getbbox()
        self.box = None if box is None else (self.x0 + box[0], self.y0 + box[1],
                                             self.x0 + box[2], self.y0 + box[3])
        # Where it stands: the bottom row of its drawn pixels - or, for a
        # person, of the pixels down the middle of the frame, where the body is.
        # What people hold can reach lower than their feet: the fisher's line
        # ends in a splash half a tile below the dock he stands on, and his feet
        # measured by that splash put him behind a player standing beside him.
        self.foot = self.box[3] if self.box else self.y0
        if self.box and thing.kind == "npc" and not thing.rotation:
            middle = int(round(thing.x + thing.width / 2)) - self.x0
            body = self.alpha.crop((middle - PERSON_BAND, 0, middle + PERSON_BAND,
                                    self.alpha.height)).getbbox()
            if body:
                self.foot = self.y0 + body[3]

    def _window(self, other_box):
        l = max(self.box[0], other_box[0])
        t = max(self.box[1], other_box[1])
        r = min(self.box[2], other_box[2])
        b = min(self.box[3], other_box[3])
        return (l, t, r, b) if r > l and b > t else None

    def overlaps(self, other):
        """Whether any drawn pixel of one lies on a drawn pixel of the other."""
        if not self.box or not other.box:
            return False
        w = self._window(other.box)
        if w is None:
            return False
        l, t, r, b = w
        mine = self.alpha.crop((l - self.x0, t - self.y0, r - self.x0, b - self.y0))
        theirs = other.alpha.crop((l - other.x0, t - other.y0, r - other.x0, b - other.y0))
        return ImageChops.multiply(mine, theirs).getbbox() is not None

    def overlaps_mask(self, mask):
        """Whether any drawn pixel lies on a lit pixel of a map-sized mask."""
        if not self.box:
            return False
        w = self._window((0, 0, mask.width, mask.height))
        if w is None:
            return False
        l, t, r, b = w
        mine = self.alpha.crop((l - self.x0, t - self.y0, r - self.x0, b - self.y0))
        return ImageChops.multiply(mine, mask.crop((l, t, r, b))).getbbox() is not None


def canopy(grid):
    """Forest cells above the foot of their column: the leaves, not the trunks."""
    return {(x, y) for y in range(grid.height) for x in range(grid.width)
            if grid.get(x, y) and grid.get(x, y + 1)}


def roofs(grid, interiors):
    """Building cells above the BASE_ROWS at the foot of every column.

    Counted per column rather than from the bottom of the whole building, so
    a house with a tower beside it keeps the tower's foot and the house's front
    wall both on the ground, however far apart their bottom rows are.
    """
    out = set()
    for x in range(grid.width):
        run = 0
        for y in range(grid.height - 1, -1, -1):
            if not grid.get(x, y):
                run = 0
                continue
            run += 1
            if run > BASE_ROWS and not any(l <= x <= r and t <= y <= b
                                           for l, t, r, b in interiors):
                out.add((x, y))
    return out


def draw_order(indices, arts):
    """Sprites in the order the game walks them, the player merged in by foot.

    Sorted by where each stands, except that any two sprites whose pixels
    overlap keep the order the room drew them in - a crate on a crate, a goblin
    in front of his cart. The result is a picture identical to the room's with
    everything that does not overlap free to be passed by the player.
    """
    import heapq
    after = {i: [] for i in indices}
    waiting = {i: 0 for i in indices}
    for pos, i in enumerate(indices):
        for j in indices[pos + 1:]:
            if arts[i].overlaps(arts[j]):
                after[i].append(j)
                waiting[j] += 1
    ready = [(arts[i].foot, i) for i in indices if waiting[i] == 0]
    heapq.heapify(ready)
    out = []
    while ready:
        _, i = heapq.heappop(ready)
        out.append(i)
        for j in after[i]:
            waiting[j] -= 1
            if waiting[j] == 0:
                heapq.heappush(ready, (arts[j].foot, j))
    return out


def layer_for_play(island, interiors):
    """Stage 2: the same picture, split into what is under the player and over.

    Roofs and canopies are lifted into overhead layers drawn after the
    sprites. Once a cell is lifted, every later layer's tile in that cell comes
    with it, so a sign on a roof stays on the roof. A sprite that the lifted
    tiles would now cover is lifted too, into sprites_top, and so is any later
    sprite overlapping a lifted one. Nothing is drawn in a different order where
    two things overlap, so the map still draws exactly what the room drew.
    """
    names = [l.name for l in island.layers]
    start, end = names.index("sprites_under") + 1, names.index("sprites")
    lifted, overheads = set(), []
    for grid in island.layers[start:end]:
        if grid.name.startswith("overhead_"):
            # Placed over the player by whoever added it - the house's roof.
            overheads.append(grid)
            lifted.update((i % grid.width, i // grid.width)
                          for i, cell in enumerate(grid.data) if cell)
            continue
        seeds = set()
        if grid.name == "dressing_forest":
            seeds = canopy(grid)
        elif grid.name == "dressing_building":
            seeds = roofs(grid, interiors)
        elif grid.name.startswith("dressing_deco"):
            # The pines and round trees the pack paints into its decoration
            # layers: everything above the foot of the trunk.
            seeds = {(x, y) for y in range(grid.height) for x in range(grid.width)
                     if is_canopy(grid, x, y)}
        over = Grid("overhead_" + grid.name[len("dressing_"):], grid.width, grid.height)
        for y in range(grid.height):
            for x in range(grid.width):
                cell = grid.get(x, y)
                if cell and ((x, y) in lifted or (x, y) in seeds):
                    over.put(x, y, cell)
                    grid.put(x, y, 0)
                    lifted.add((x, y))
        overheads.append(over)

    mask = Image.new("L", (island.width * TILE, island.height * TILE))
    for over in overheads:
        for i, cell in enumerate(over.data):
            if cell:
                alpha = cell_art(cell).getchannel("A").point(lambda a: 255 if a else 0)
                mask.paste(alpha, ((i % over.width) * TILE, (i // over.width) * TILE), alpha)

    island.layers = [l for l in island.layers
                     if not (isinstance(l, Grid) and l.name.startswith("overhead_")
                             and l in overheads)]
    group = island.group("sprites")
    things = group.things
    arts = [Art(t) for t in things]
    top = []
    for i, art in enumerate(arts):
        top.append(art.overlaps_mask(mask)
                   or any(top[j] and arts[j].overlaps(art) for j in range(i)))
    ground = [i for i in range(len(things)) if not top[i]
              and things[i].sheet.name in SHADOWS
              and not any(not top[j] and things[j].sheet.name not in SHADOWS
                          and arts[j].overlaps(arts[i]) for j in range(i))]
    grounded = set(ground)
    rest = [i for i in range(len(things)) if not top[i] and i not in grounded]
    group.things = [things[i] for i in draw_order(rest, arts)]
    at = island.layers.index(group)
    island.layers[at:at] = [Group("sprites_ground", [things[i] for i in ground])]
    at = island.layers.index(group) + 1
    island.layers[at:at] = overheads + [
        Group("sprites_top", [things[i] for i in range(len(things)) if top[i]])]
    # Empty layers go, except decor: it is empty until somebody draws in it,
    # and a Tiled user finding it missing has nowhere to draw.
    island.layers = [l for l in island.layers
                     if not (isinstance(l, Grid) and not any(l.data) and l.name != "decor")]
    print("  stage 2: %d tiles lifted over the player; sprites: %d ground, %d sorted, %d on top"
          % (sum(1 for o in overheads for c in o.data if c), len(ground), len(rest),
             sum(top)))


def check_layering(before, after):
    """Stage 2's check: splitting the layers changed nothing that is drawn."""
    expected, _, _, _, _ = preview_map.load(before)
    per_tile, diff = compare(expected, after, "layered map against the room's order")
    if per_tile:
        diff.save(os.path.join(REVIEW, "island-layered-diff.png"))
        worst = sorted(per_tile.items(), key=lambda kv: -kv[1])[:12]
        raise SystemExit("layering the map changed what it draws, in %d tiles (worst: %s). "
                         "See review/island-layered-diff.png" % (len(per_tile), worst))


def overhead_preview(island, path, out):
    """The map with what draws over the player tinted: tiles pink, sprites blue."""
    image, _, _, _, _ = preview_map.load(path)
    tint = Image.new("RGBA", image.size)
    pink = Image.new("RGBA", (TILE, TILE), (255, 0, 160, 120))
    for layer in island.layers:
        if isinstance(layer, Grid) and layer.name.startswith("overhead_") \
                and "cloud" not in layer.name:
            for i, cell in enumerate(layer.data):
                if cell:
                    alpha = cell_art(cell).getchannel("A").point(lambda a: 255 if a else 0)
                    tint.paste(pink, ((i % layer.width) * TILE, (i // layer.width) * TILE), alpha)
        elif isinstance(layer, Group) and layer.name == "sprites_top":
            for thing in layer.things:
                art = Art(thing)
                blue = Image.new("RGBA", art.image.size, (0, 140, 255, 140))
                S.paste_clipped(tint, Image.composite(blue, Image.new("RGBA", art.image.size),
                                                      art.alpha), art.x0, art.y0)
    image.alpha_composite(tint)
    image.convert("RGB").save(out)


# --------------------------------------------------------------------------
# Stage 5: where a player can walk
# --------------------------------------------------------------------------
#
# Nothing in the pack says what is solid, so it is read off the pack's pixels
# and its sheet, in 4px cells - the grid gen/CollisionGrid.java keeps. Ground
# is open where its land tile is grass, sand or the floor of the mine pit, and
# wherever a path, a plank, a ladder or a floor is laid; everything else is sea,
# cliff face or pit wall. Over that ground, the dressing layers block where
# their art is, except for what a player walks across or through: planks and
# floors, flowers and tufts, and every tree but the foot of its trunk.

CELL = 4
PER = TILE // CELL
SOLID_PIXELS = 4
OPAQUE = 128
# entity/Player.BODY, and the distances HubScreen answers a key press within.
BODY = 12
TALK_RANGE = 22
GATE_RANGE = 24
DOOR_RANGE = 26
# How close a player comes to a region's worker to deal with them. Wider than
# TALK_RANGE: a cook stands behind a counter, a woodcutter among his logs.
WORK_RANGE = 32


def spans(*parts):
    out = set()
    for part in parts:
        if isinstance(part, int):
            out.add(part)
        else:
            out.update(range(part[0], part[1] + 1))
    return frozenset(out)


def sheet_block(r0, c0, r1, c1):
    return frozenset(r * 64 + c for r in range(r0, r1 + 1) for c in range(c0, c1 + 1))


# Tile ids on the Sunnyside sheet, read off its grid (see review/ for the
# labelled crops they were read from).
SURFACES = spans((357, 358), 360, 424, (743, 747), (807, 812), (871, 875), (935, 937),
                 (493, 496), (557, 560), (384, 399), (448, 463), (512, 527))
FLOORS = spans((577, 586), (641, 650), (705, 715), (769, 779), (833, 843),
               (961, 975), (1025, 1034), (1037, 1039))
QUIET = spans((91, 98), (155, 162), (219, 227), (283, 286), (288, 290), 176, 240, 304, 368,
              497, 498, 561, 562, (410, 417), (474, 491), (538, 555), (1265, 1277),
              # dirt spots and grass patches, laid over paths and bridge decks
              (333, 336), (397, 400), (141, 143), (205, 207), (269, 271),
              # the posts along a dock's edge, which stand in the sea anyway
              294, 359, 361, 422,
              # rugs, which the room keeps in its walls layer with the furniture
              (2280, 2287), (2344, 2351), (2408, 2415)) \
    | sheet_block(9, 47, 21, 63)
# The red door in the wall of a house the pack draws cut away: the way in.
DOORS = spans(716)
# Ladders: the rungs stop short of the top and bottom of their tiles, and a
# ladder that leaves four pixels of cliff at either end is not a way up.
LADDERS = spans(745, 746, 809, 810, 873, 874, 936)
TREES = spans(113, 114, 177, 178, 115, 116, 179, 180, (119, 122), (183, 186), (247, 252),
              (309, 316), (373, 380), (437, 444), (501, 506), (565, 570), 241, 242, 305, 351, 352,
              306, 369, 370, 433, 434, 243, 244, 307, 308, 372, 435, 436, 499, 500, 563, 564)
PIT = sheet_block(28, 9, 33, 13)

# Sprites that stand in nobody's way: shadows, smoke and sparkle, birds on
# roofs, things on tables and the small things lying about on the ground.
QUIET_SPRITES = ("spr_deco_charactershadow", "spr_deco_shadow", "chimneysmoke_", "spr_deco_fire_",
                 "spr_deco_glint_", "spr_deco_bird_", "spr_deco_duck_", "spr_deco_blinking",
                 "expression_", "happiness_", "spr_deco_coin", "spr_deco_mug_", "spr_deco_plate_",
                 "spr_deco_book_", "spr_deco_flowers_house_", "spr_deco_waterbowl", "spr_deco_beam",
                 "spr_deco_chinmney", "spr_deco_cook_chinmney", "spr_deco_windmill",
                 "spr_deco_coracle", "spr_deco_oar", "kale_", "cabbage_", "cauliflower_",
                 "beetroot_", "parsnip_", "pumpkin_", "wheat_", "fish", "egg", "milk", "rock",
                 "wood", "spr_deco_wool", "spr_deco_truffle", "spr_deco_acron", "spr_deco_ore_",
                 "spr_deco_mushroom_", "spr_deco_jar_", "spr_deco_bucket", "spr_deco_sword_floor")
ANIMALS = ("spr_deco_cow", "spr_deco_pig_01", "spr_deco_sheep_01", "spr_deco_chicken_01")
TREE_SPRITES = ("spr_deco_tree_01", "spr_deco_tree_02")

_cells_cache = {}


def _pixel_cells(cell, test, need):
    """PER rows of PER booleans: which 4px cells of a tile have `need` pixels passing `test`."""
    key = (cell[0].image_path, cell[1], cell[2], test.__name__, need)
    if key not in _cells_cache:
        px = cell_art(cell).load()
        _cells_cache[key] = [[sum(1 for y in range(cy * CELL, (cy + 1) * CELL)
                                  for x in range(cx * CELL, (cx + 1) * CELL)
                                  if test(px[x, y], cell[1])) >= need
                              for cx in range(PER)] for cy in range(PER)]
    return _cells_cache[key]


def colour(p):
    r, g, b = p[0], p[1], p[2]
    if b > r + 50 and b > 120:
        return "water"
    if r > 235 and g > 235 and b > 235:
        return "foam"
    if g > r + 25 and g > b + 15:
        return "grass"
    if r > 200 and g > 165 and b < 150:
        return "sand"
    if r + g + b < 260:
        return "dark"
    return "brown"


def ground_px(p, local):
    if p[3] < OPAQUE:
        return False
    c = colour(p)
    return c in ("grass", "sand") or (local in PIT and c == "brown" and p[0] < 170)


def path_px(p, local):
    return p[3] >= OPAQUE and colour(p) not in ("water", "dark", "foam")


def art_px(p, local):
    return p[3] >= OPAQUE


def is_canopy(grid, x, y):
    """A tree tile with the next tile of the same tree straight below it."""
    cell, below = grid.get(x, y), grid.get(x, y + 1)
    return (cell and below and cell[0] is below[0] and cell[0].name == "sunnyside"
            and cell[1] in TREES and below[1] == cell[1] + 64
            and not (cell[2] & S.TILED_D))


class Blocking:
    """The map's collision cells: open ground, and what stands on it."""

    def __init__(self, island):
        self.width = island.width * PER
        self.height = island.height * PER
        self.open = bytearray(self.width * self.height)
        self.solid = bytearray(self.width * self.height)
        self.island = island
        self.interiors = [room_box(box) for box in INTERIORS]

    def inside(self, tx, ty):
        return any(x0 <= tx <= x1 and y0 <= ty <= y1 for x0, y0, x1, y1 in self.interiors)

    def _mark(self, buf, tx, ty, cells, lower_half=False):
        for cy in range(PER):
            if lower_half and cy < PER // 2:
                continue
            row = (ty * PER + cy) * self.width + tx * PER
            for cx in range(PER):
                if cells[cy][cx]:
                    buf[row + cx] = 1

    def _mark_art(self, art, rows=None, band=None):
        """A sprite's own pixels: the lowest `rows` rows of them, within `band` px of its middle."""
        if not art.box:
            return
        x0, y0, x1, y1 = art.box
        top = y1 - rows if rows else y0
        mid = (x0 + x1) // 2
        px = art.alpha.load()
        counts = {}
        for y in range(max(top, y0), y1):
            for x in range(x0, x1):
                if band and abs(x - mid) > band:
                    continue
                if px[x - art.x0, y - art.y0]:
                    key = (x // CELL, y // CELL)
                    counts[key] = counts.get(key, 0) + 1
        for (cx, cy), n in counts.items():
            if n >= 2 and 0 <= cx < self.width and 0 <= cy < self.height:
                self.solid[cy * self.width + cx] = 1

    def _mark_body(self, cx_px, foot, w, h):
        for y in range(int(foot - h), int(foot)):
            for x in range(int(cx_px - w // 2), int(cx_px + w // 2)):
                cx, cy = x // CELL, y // CELL
                if 0 <= cx < self.width and 0 <= cy < self.height:
                    self.solid[cy * self.width + cx] = 1

    def build(self):
        island = self.island
        for layer in island.layers:
            if not isinstance(layer, Grid):
                continue
            name = layer.name
            for i, cell in enumerate(layer.data):
                if not cell:
                    continue
                tx, ty = i % layer.width, i // layer.width
                sunny = cell[0].name == "sunnyside"
                local = cell[1]
                if name == "ground_land":
                    if sunny and 270 <= local <= 273:
                        # The darker grass the pack draws as a slope from one
                        # terrace down to the next: where a path takes a step.
                        self._mark(self.open, tx, ty, [[True] * PER for _ in range(PER)])
                    else:
                        self._mark(self.open, tx, ty, _pixel_cells(cell, ground_px, 8))
                elif name == "ground_paths":
                    if sunny and local in LADDERS:
                        self._mark(self.open, tx, ty, [[True] * PER for _ in range(PER)])
                    elif sunny and local in SURFACES:
                        art = _pixel_cells(cell, art_px, SOLID_PIXELS)
                        self._mark(self.open, tx, ty, [[any(row)] * PER for row in art])
                    else:
                        self._mark(self.open, tx, ty, _pixel_cells(cell, path_px, 6))
                elif name.startswith("dressing_") or name == "decor":
                    # Decor drawn by hand blocks the way dressing does - by
                    # its pixels - so a tree put there in Tiled stands in the way.
                    art = _pixel_cells(cell, art_px, SOLID_PIXELS)
                    if name.startswith("dressing_house_"):
                        continue                  # painted on the walls, which block
                    if sunny and local in LADDERS:
                        # A ladder is narrower than a player and shorter than
                        # its tile: the whole tile is the way up.
                        self._mark(self.open, tx, ty, [[True] * PER for _ in range(PER)])
                    elif sunny and local in SURFACES:
                        self._mark(self.open, tx, ty, [[any(row)] * PER for row in art])
                    elif sunny and name == "dressing_building" and (
                            local in FLOORS or (local in DOORS and self.inside(tx, ty))):
                        self._mark(self.open, tx, ty, [[True] * PER for _ in range(PER)])
                    elif sunny and local in QUIET:
                        continue
                    elif name == "dressing_forest" or (sunny and local in TREES):
                        self._mark(self.solid, tx, ty, art, lower_half=True)
                    else:
                        self._mark(self.solid, tx, ty, art)
        for layer in island.layers:
            if not isinstance(layer, Group) or not layer.name.startswith("sprites"):
                continue
            for thing in layer.things:
                self.sprite(thing)

    def sprite(self, thing):
        name = thing.sheet.name
        art = Art(thing)
        if not art.box:
            return
        if thing.kind == "npc" or name.startswith(ANIMALS):
            # People and animals going about their business are walked past,
            # not into. The room stands them on its bridges and in its lanes -
            # a goblin rolling across the only bridge west is a wall across the
            # village. The one person a player deals with in each region stands
            # still enough to be solid.
            if "role" in thing.properties:
                self._mark_body(thing.placement.x + LEFT * TILE, art.foot, 10, 6)
            return
        if name.startswith(QUIET_SPRITES):
            return
        elif name.startswith(TREE_SPRITES):
            # Only the trunk where it meets the ground: the pack plants these
            # beside doors and along lanes, a pixel or two from passable.
            self._mark_art(art, rows=4, band=3)
        else:
            self._mark_art(art, rows=6)

    def blocked(self):
        return bytearray(1 if (not o) or s else 0 for o, s in zip(self.open, self.solid))

    def rectangles(self, blocked):
        """The blocked cells as few rectangles, in pixels, y down."""
        rects, open_runs = [], {}
        w, h = self.width, self.height
        for y in range(h + 1):
            runs = set()
            if y < h:
                row = blocked[y * w:(y + 1) * w]
                x = 0
                while x < w:
                    if row[x]:
                        start = x
                        while x < w and row[x]:
                            x += 1
                        runs.add((start, x))
                    else:
                        x += 1
            for run in [r for r in open_runs if r not in runs]:
                top = open_runs.pop(run)
                rects.append((run[0] * CELL, top * CELL, (run[1] - run[0]) * CELL, (y - top) * CELL))
            for run in runs:
                open_runs.setdefault(run, y)
        return sorted(rects, key=lambda r: (r[1], r[0]))


class Walker:
    """Where a player's body fits, and where it can walk to - on a 2px lattice."""

    STEP = 2

    def __init__(self, blocked, width, height):
        self.cw, self.ch = width, height
        w = width + 1
        sums = [0] * (w * (height + 1))
        for y in range(height):
            run = 0
            base = y * width
            for x in range(width):
                run += blocked[base + x]
                sums[(y + 1) * w + x + 1] = sums[y * w + x + 1] + run
        self.sums = sums

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

    def snap(self, point, radius=32):
        px, py = int(point[0]), int(point[1])
        best = None
        for dy in range(-radius, radius + 1, self.STEP):
            for dx in range(-radius, radius + 1, self.STEP):
                x, y = px - px % 2 + dx, py - py % 2 + dy
                if self.fits(x, y):
                    d = (x - px) ** 2 + (y - py) ** 2
                    if best is None or d < best[0]:
                        best = (d, x, y)
        return None if best is None else (best[1], best[2])

    def reach(self, start):
        from collections import deque
        lw = self.cw * CELL // self.STEP + 1
        lh = self.ch * CELL // self.STEP + 1
        seen = bytearray(lw * lh)
        sx, sy = start[0] // self.STEP, start[1] // self.STEP
        seen[sy * lw + sx] = 1
        queue = deque([(sx, sy)])
        while queue:
            x, y = queue.popleft()
            for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                if 0 <= nx < lw and 0 <= ny < lh and not seen[ny * lw + nx] \
                        and self.fits(nx * self.STEP, ny * self.STEP):
                    seen[ny * lw + nx] = 1
                    queue.append((nx, ny))
        self.lw = lw
        return seen

    def near(self, seen, target, radius):
        tx, ty = target
        r = int(radius)
        for dy in range(-r, r + 1, self.STEP):
            for dx in range(-r, r + 1, self.STEP):
                if dx * dx + dy * dy >= r * r:
                    continue
                x, y = (int(tx) + dx) // self.STEP, (int(ty) + dy) // self.STEP
                if 0 <= x < self.lw and 0 <= y * self.lw + x < len(seen) and seen[y * self.lw + x]:
                    return True
        return False


class Marker:
    """A named point, in pixels with y down, for HubScreen to read."""

    def __init__(self, name, x, y, kind="spawn"):
        self.name, self.x, self.y, self.kind = name, x, y, kind


class Shape:
    def __init__(self, x, y, w, h):
        self.x, self.y, self.w, self.h = x, y, w, h


# The one person in each part of the island the player deals with, found by
# where the room put them. Everyone else goes about their business.
WORKERS = {
    ("spr_watering", 168.0, 123.0): "farm",
    ("spr_axe", 477.0, 361.0): "forest",
    ("spr_doing", 889.0, 276.0): "ranch",
    ("spr_caught", 423.0, 724.0): "fishing",
    ("spr_mining", 72.0, 706.0): "mine",
    ("spr_doing", 1364.0, 255.5): "kitchen",
}


def tag_workers(island):
    found = {}
    for layer in island.layers:
        if not isinstance(layer, Group):
            continue
        for thing in layer.things:
            p = getattr(thing, "placement", None)
            key = p and (p.sprite, p.x, p.y)
            if key in WORKERS:
                role = WORKERS[key]
                thing.name = "worker_" + role
                thing.properties["role"] = role
                found[role] = thing
    missing = set(WORKERS.values()) - set(found)
    if missing:
        raise SystemExit("no worker found for " + ", ".join(sorted(missing)))
    return found


def place_markers(island, walker, workers):
    """door, entry, gate and shop, each snapped to where a player fits."""
    px = lambda tx: (tx + LEFT) * TILE
    py = lambda ty: (ty + TOP) * TILE
    door_x = px(HOME_AT[0] + 7)                     # between the door's two tiles
    door_y = py(HOME_AT[1] + 9) + BODY // 2         # just below the doorstep
    markers = {}

    def snap(name, point):
        spot = walker.snap(point)
        if spot is None:
            raise SystemExit("nowhere near %s (%s) fits a player" % (name, point))
        markers[name] = spot
    snap("door", (door_x, door_y))
    snap("entry", (door_x, markers["door"][1] + 24))
    snap("gate", (TORII_FOOT[0] + LEFT * TILE, TORII_FOOT[1] + TOP * TILE + 10))
    snap("shop", (px(ORANGE_DOOR[0]) + TILE // 2, py(ORANGE_DOOR[1] + 1) + BODY // 2))
    # The three villagers of the old village, where their words make sense:
    # the herbalist at her shop, the elder at the torii he speaks of, the
    # master by the house the player comes out of.
    snap("herbalist", (markers["shop"][0] + 18, markers["shop"][1] + 2))
    snap("elder", (markers["gate"][0] - 30, markers["gate"][1] + 4))
    snap("master", (markers["door"][0] - 56, markers["door"][1] + 18))
    return markers


def check_walks(walker, markers, workers):
    """Everything the village needs reached must be reachable from the front door."""
    seen = walker.reach(markers["entry"])
    wanted = [("the front door", markers["door"], DOOR_RANGE),
              ("the torii", markers["gate"], GATE_RANGE),
              ("the shop", markers["shop"], DOOR_RANGE),
              ("the herbalist", markers["herbalist"], TALK_RANGE),
              ("the elder", markers["elder"], TALK_RANGE),
              ("the master", markers["master"], TALK_RANGE)]
    for role, thing in sorted(workers.items()):
        art = Art(thing)
        p = thing.placement
        wanted.append(("the %s worker" % role, (p.x + LEFT * TILE, art.foot - 8), WORK_RANGE))
    missing = [(what, target) for what, target, radius in wanted
               if not walker.near(seen, target, radius)]
    return seen, missing


def stand_markers(walker, seen, workers, markers):
    """stand_<role>: where --screen hub --page 4 and on put a player, in front of each worker.

    A step below the worker's feet where that is ground a player walks to from
    the door, and otherwise the nearest such spot - the fisher works off the end
    of a dock, and what is a step below him is the sea. Always inside the range
    HubScreen deals with a worker in, less a margin, so the prompt is up on
    arrival.
    """
    step = walker.STEP
    reach = WORK_RANGE - 6
    for role, thing in sorted(workers.items()):
        x = thing.placement.x + LEFT * TILE
        foot = Art(thing).foot
        talk = (x, foot - 8)                        # HubScreen measures to here
        wish = (x, foot + 14)
        best = None
        for ly in range(int(talk[1] - reach) // step, int(talk[1] + reach) // step + 1):
            for lx in range(int(talk[0] - reach) // step, int(talk[0] + reach) // step + 1):
                i = ly * walker.lw + lx
                if lx < 0 or ly < 0 or lx >= walker.lw or i >= len(seen) or not seen[i]:
                    continue
                px, py = lx * step, ly * step
                if (px - talk[0]) ** 2 + (py - talk[1]) ** 2 >= reach * reach:
                    continue
                if abs(px - x) < 14 and foot - 26 < py < foot + 14:
                    # Not on top of them. A ninja is about sixteen pixels
                    # across and twenty tall, feet six below where it stands;
                    # a dock one tile deep put the fisher's stand in his lap.
                    continue
                d = (px - wish[0]) ** 2 + (py - wish[1]) ** 2
                if best is None or d < best[0]:
                    best = (d, px, py)
        if best is None:
            raise SystemExit("nowhere a player walks to is in reach of the %s worker" % role)
        markers["stand_" + role] = (best[1], best[2])


def check_arrivals(markers, workers):
    """Each place a player is put down offers what it is there for.

    The choice HubScreen.findInteraction makes - the nearest person in range,
    then the torii, then the door - made here for the entry, the torii, the
    shop and every stand. In range of the herbalist by less than a pixel too
    little, the shop was a street with no prompt in it; VillageLayoutTest makes
    the same check again against the written map.
    """
    people = [(name, (x, y - 8), TALK_RANGE) for name, (x, y) in markers.items()
              if name in ("elder", "master", "herbalist")]
    for role, thing in workers.items():
        people.append(("worker_" + role,
                       (thing.placement.x + LEFT * TILE, Art(thing).foot - 8), WORK_RANGE))

    def offered(point):
        best, nearest = None, None
        for name, (x, y), radius in people:
            d = math.hypot(point[0] - x, point[1] - y)
            if d < radius and (nearest is None or d < nearest):
                best, nearest = name, d
        if best:
            return best
        for name, radius in (("gate", GATE_RANGE), ("door", DOOR_RANGE)):
            if math.hypot(point[0] - markers[name][0], point[1] - markers[name][1]) < radius:
                return name
        return None

    wanted = {"entry": "door", "gate": "gate", "shop": "herbalist"}
    wanted.update(("stand_" + role, "worker_" + role) for role in workers)
    wrong = ["%s offers %s, not %s" % (marker, offered(markers[marker]), want)
             for marker, want in sorted(wanted.items()) if offered(markers[marker]) != want]
    if wrong:
        raise SystemExit("arrival prompts: " + "; ".join(wrong))


def walk_preview(island, path, blocked, blocking, walker, seen, markers, workers, out):
    image, _, _, _, _ = preview_map.load(path)
    over = Image.new("RGBA", image.size)
    red = (255, 40, 40, 90)
    px = over.load()
    for cy in range(blocking.height):
        for cx in range(blocking.width):
            if blocked[cy * blocking.width + cx]:
                for y in range(cy * CELL, cy * CELL + CELL):
                    for x in range(cx * CELL, cx * CELL + CELL):
                        px[x, y] = red
    for ly in range(0, len(seen) // walker.lw):
        for lx in range(walker.lw):
            if seen[ly * walker.lw + lx] and (lx + ly) % 3 == 0:
                x, y = lx * walker.STEP, ly * walker.STEP
                if x < image.width and y < image.height:
                    px[x, y] = (40, 255, 120, 200)
    image.alpha_composite(over)
    from PIL import ImageDraw
    d = ImageDraw.Draw(image)
    for name, (x, y) in markers.items():
        d.ellipse([x - 3, y - 3, x + 3, y + 3], fill=(255, 230, 0, 255))
        d.text((x + 4, y - 4), name, fill=(255, 255, 255, 255))
    for role, thing in workers.items():
        art = Art(thing)
        x = thing.placement.x + LEFT * TILE
        d.ellipse([x - 3, art.foot - 3, x + 3, art.foot + 3], fill=(0, 200, 255, 255))
        d.text((x + 4, art.foot - 4), role, fill=(200, 240, 255, 255))
    image.convert("RGB").save(out)


def set_feet(island):
    """Every sprite's `foot`: how far below its picture's bottom edge it stands.

    In pixels, as drawn. The game sorts sprites against the player by where
    they stand, and a sprite's frame is mostly air - a goblin's 96x64 frame
    has him in the middle of it - so the bottom of the frame is no answer. The
    number travels with the sprite rather than its tileset: moving one in
    Tiled moves its feet with it.
    """
    for layer in island.layers:
        if isinstance(layer, Group) and layer.name.startswith("sprites"):
            for thing in layer.things:
                thing.properties["foot"] = int(round(thing.y - Art(thing).foot))


def carry_decor(island, path):
    """The decor layer of the map about to be overwritten, carried into the new one.

    `decor` is where a person finishes a generated map by hand, and a
    regenerate that wiped it would destroy exactly the work the layer exists
    for. Tiles are matched by image, not by gid: gids are this script's to
    renumber. A tileset added in Tiled for the decor comes along with it.
    """
    decor = island.grid("decor")
    if not os.path.exists(path):
        return 0
    root = ET.parse(path).getroot()
    old = next((l for l in root.findall("layer") if l.get("name") == "decor"), None)
    if old is None:
        return 0
    data = old.find("data")
    if data is None or data.get("encoding") != "csv" or data.get("compression"):
        raise SystemExit("%s: decor is not plain CSV, so it cannot be carried over; re-save "
                         "it in Tiled with Tile Layer Format: CSV" % path)
    gids = [int(v) for v in data.text.replace("\n", "").split(",") if v.strip()]
    if not any(gids):
        return 0
    if (int(old.get("width")), int(old.get("height"))) != (island.width, island.height):
        raise SystemExit("%s: hand-drawn decor is %sx%s but the island is now %dx%d; move it "
                         "by hand before regenerating" % (path, old.get("width"),
                                                          old.get("height"), island.width,
                                                          island.height))
    here = os.path.dirname(os.path.abspath(path))
    sets = []
    for ts in root.findall("tileset"):
        if ts.get("source"):
            raise SystemExit("%s: an external tileset (%s) cannot be carried over"
                             % (path, ts.get("source")))
        image = os.path.normcase(os.path.normpath(os.path.join(here, ts.find("image").get("source"))))
        sets.append((int(ts.get("firstgid")), int(ts.get("tilecount")), image,
                     int(ts.get("tilewidth")), int(ts.get("tileheight"))))
    by_image = {os.path.normcase(os.path.normpath(sheet.image_path)): sheet
                for sheet in island.sheets}
    count = 0
    for i, raw in enumerate(gids):
        if not raw:
            continue
        flags, gid = raw & 0xF0000000, raw & 0x0FFFFFFF
        owner = next((t for t in sets if t[0] <= gid < t[0] + t[1]), None)
        if owner is None:
            raise SystemExit("%s: decor tile %d belongs to no tileset in the file" % (path, gid))
        first, _, image, tw, th = owner
        sheet = by_image.get(image)
        if sheet is None:
            sheet = island.sheet(Sheet(os.path.splitext(os.path.basename(image))[0], image, tw, th))
            by_image[image] = sheet
        decor.data[i] = (sheet, gid - first, flags)
        count += 1
    return count


# --------------------------------------------------------------------------
# Writing
# --------------------------------------------------------------------------

def _num(v):
    """A coordinate as Tiled writes one: no trailing .0, no float noise."""
    r = round(v, 4)
    return str(int(r)) if r == int(r) else repr(r)


def write(island, path):
    """The map as a finite, CSV-encoded .tmx, image paths relative to `path`."""
    island.number()
    here = os.path.dirname(os.path.abspath(path))
    m = ET.Element("map", {
        "version": "1.10", "tiledversion": "1.10.2",
        "orientation": "orthogonal", "renderorder": "right-down",
        "width": str(island.width), "height": str(island.height),
        "tilewidth": str(TILE), "tileheight": str(TILE), "infinite": "0",
    })
    for sheet in island.sheets:
        node = ET.SubElement(m, "tileset", {
            "firstgid": str(sheet.firstgid), "name": sheet.name,
            "tilewidth": str(sheet.tile_w), "tileheight": str(sheet.tile_h),
            "tilecount": str(sheet.count), "columns": str(sheet.columns),
        })
        ET.SubElement(node, "image", {
            "source": os.path.relpath(sheet.image_path, here).replace(os.sep, "/"),
            "width": str(sheet.width), "height": str(sheet.height),
        })
        for local in sorted(sheet.animations):
            tile = ET.SubElement(node, "tile", {"id": str(local)})
            animation = ET.SubElement(tile, "animation")
            for frame, ms in sheet.animations[local]:
                ET.SubElement(animation, "frame", {"tileid": str(frame), "duration": str(ms)})

    layer_id, object_id = 1, 1
    for layer in island.layers:
        if isinstance(layer, Grid):
            node = ET.SubElement(m, "layer", {
                "id": str(layer_id), "name": layer.name,
                "width": str(layer.width), "height": str(layer.height)})
            data = ET.SubElement(node, "data", {"encoding": "csv"})
            rows = []
            for y in range(layer.height):
                row = []
                for cell in layer.data[y * layer.width:(y + 1) * layer.width]:
                    if not cell:
                        row.append("0")
                    else:
                        sheet, local, flags = cell
                        row.append(str(flags | (sheet.firstgid + local)))
                rows.append(",".join(row))
            data.text = "\n" + ",\n".join(rows) + "\n"
        else:
            attrs = {"id": str(layer_id), "name": layer.name}
            if layer.color:
                attrs["color"] = layer.color
            if not layer.visible:
                attrs["visible"] = "0"
            node = ET.SubElement(m, "objectgroup", attrs)
            for thing in layer.things:
                if isinstance(thing, Marker):
                    ET.SubElement(node, "object", {
                        "id": str(object_id), "name": thing.name, "type": thing.kind,
                        "x": _num(thing.x), "y": _num(thing.y)})
                    object_id += 1
                    continue
                if isinstance(thing, Shape):
                    ET.SubElement(node, "object", {
                        "id": str(object_id), "x": _num(thing.x), "y": _num(thing.y),
                        "width": _num(thing.w), "height": _num(thing.h)})
                    object_id += 1
                    continue
                gid = thing.sheet.firstgid + thing.tile
                gid |= (S.TILED_H if thing.flip_h else 0) | (S.TILED_V if thing.flip_v else 0)
                attrs = {"id": str(object_id)}
                if thing.name:
                    attrs["name"] = thing.name
                if thing.kind:
                    attrs["type"] = thing.kind
                attrs.update({"gid": str(gid), "x": _num(thing.x), "y": _num(thing.y),
                              "width": _num(thing.width), "height": _num(thing.height)})
                if round(thing.rotation, 4):
                    attrs["rotation"] = _num(thing.rotation)
                obj = ET.SubElement(node, "object", attrs)
                properties = dict(thing.properties)
                if thing.frame:
                    properties["frame"] = thing.frame
                if thing.speed != 1.0:
                    properties["speed"] = thing.speed
                if properties:
                    props = ET.SubElement(obj, "properties")
                    for key in sorted(properties):
                        value = properties[key]
                        kind = ("int" if isinstance(value, int) and not isinstance(value, bool)
                                else "float" if isinstance(value, float)
                                else "bool" if isinstance(value, bool) else None)
                        pattrs = {"name": key, "value": (str(value).lower() if kind == "bool"
                                                         else _num(value) if kind == "float"
                                                         else str(value))}
                        if kind:
                            pattrs["type"] = kind
                        ET.SubElement(props, "property", pattrs)
                object_id += 1
        layer_id += 1
    m.set("nextlayerid", str(layer_id))
    m.set("nextobjectid", str(object_id))
    ET.indent(m, space=" ")
    os.makedirs(here, exist_ok=True)
    ET.ElementTree(m).write(path, encoding="UTF-8", xml_declaration=True)


# --------------------------------------------------------------------------
# Checking
# --------------------------------------------------------------------------

def compare(expected, path, label):
    """Render the written map and count where it differs from `expected`.

    Returns the per-tile count of differing pixels, so a caller can tell a
    stray pixel along a resampled edge from a tile that is simply wrong.
    """
    actual, _, _, _, _ = preview_map.load(path)
    diff = ImageChops.difference(actual.convert("RGB"), expected.convert("RGB"))
    mask = diff.convert("L").point(lambda v: 255 if v else 0)
    per_tile = {}
    pixels = mask.load()
    for y in range(mask.height):
        for x in range(mask.width):
            if pixels[x, y]:
                key = (x // TILE, y // TILE)
                per_tile[key] = per_tile.get(key, 0) + 1
    total = sum(per_tile.values())
    print("    %s: %d pixels differ, in %d tiles" % (label, total, len(per_tile)))
    return per_tile, diff


def resampled_tiles(island):
    """Tiles touched by a sprite that is drawn scaled or turned."""
    touched = set()
    for layer in island.layers:
        if not isinstance(layer, Group):
            continue
        for thing in layer.things:
            spr_w, spr_h = thing.sheet.tile_w, thing.sheet.tile_h
            if (round(thing.rotation, 4) == 0 and abs(thing.width - spr_w) < 1e-6
                    and abs(thing.height - spr_h) < 1e-6):
                continue
            reach = max(thing.width, thing.height) * 1.5
            for ty in range(int((thing.y - reach) // TILE), int((thing.y + reach) // TILE) + 1):
                for tx in range(int((thing.x - reach) // TILE), int((thing.x + reach) // TILE) + 1):
                    touched.add((tx, ty))
    return touched


def check_conversion(room, island):
    """Stage 1's check: the written map draws what GameMaker draws."""
    os.makedirs(REVIEW, exist_ok=True)
    path = os.path.join(REVIEW, "island-exact.tmx")
    write(island, path)
    expected = S.reference(room)
    per_tile, diff = compare(expected, path, "converted room against GameMaker's render")
    resampled = resampled_tiles(island)
    wrong = {t: n for t, n in per_tile.items() if t not in resampled}
    loose = sum(n for t, n in per_tile.items() if t in resampled)
    if wrong or loose > RESAMPLED_PIXELS:
        diff.save(os.path.join(REVIEW, "island-exact-diff.png"))
        worst = sorted(wrong.items(), key=lambda kv: -kv[1])[:12]
        raise SystemExit("the conversion does not draw the room: %d tiles differ away from "
                         "any resampled sprite (worst: %s), and %d pixels near resampled "
                         "ones (allowed %d). See review/island-exact-diff.png"
                         % (len(wrong), worst, loose, RESAMPLED_PIXELS))
    print("    every tile matches; %d pixels differ along scaled or turned sprites" % loose)
    return path


def main(argv=None):
    args = sys.argv[1:] if argv is None else argv
    exact_only = "--exact" in args
    # Only the images the map names, for tools/build_assets.py: the map itself,
    # and whatever a person has drawn into its decor layer, stay as they are.
    assets_only = "--assets" in args
    print("building the island village")
    room = S.Room()
    island = convert(room)
    print("  stage 1: %dx%d tiles, %d tilesets, %d layers, %d sprites"
          % (island.width, island.height, len(island.sheets), len(island.layers),
             sum(len(l.things) for l in island.layers if isinstance(l, Group))))
    check_conversion(room, island)
    if exact_only:
        return 0
    village_changes(island)
    if assets_only:
        print("  images written under assets/gfx/sunnyside/")
        return 0
    insert_after(island, "dressing_house_shutters", [Grid("decor", island.width, island.height)])
    carried = carry_decor(island, os.path.join(OUT, "village.tmx"))
    if carried:
        print("  kept %d hand-placed decor tiles" % carried)
    order_path = os.path.join(REVIEW, "island-order.tmx")
    write(island, order_path)
    layer_for_play(island, [room_box(box) for box in INTERIORS])
    workers = tag_workers(island)
    set_feet(island)
    blocking = Blocking(island)
    blocking.build()
    blocked = blocking.blocked()
    walker = Walker(blocked, blocking.width, blocking.height)
    markers = place_markers(island, walker, workers)
    seen, missing = check_walks(walker, markers, workers)
    stand_markers(walker, seen, workers, markers)
    check_arrivals(markers, workers)
    island.layers.append(Group("spawns", [Marker(n, x, y) for n, (x, y) in sorted(markers.items())]))
    shapes = blocking.rectangles(blocked)
    island.layers.append(Group("collision", [Shape(*r) for r in shapes], visible=False,
                               color="#ff3c3c"))
    layered_path = os.path.join(REVIEW, "island-layered.tmx")
    write(island, layered_path)
    check_layering(order_path, layered_path)
    overhead_preview(island, layered_path, os.path.join(REVIEW, "island-overhead.png"))
    walk_preview(island, layered_path, blocked, blocking, walker, seen, markers, workers,
                 os.path.join(REVIEW, "island-walk.png"))
    print("  stage 5: %d collision rectangles; markers: %s"
          % (len(shapes), ", ".join(sorted(markers))))
    if missing:
        raise SystemExit("from the front door a player cannot reach: "
                         + "; ".join("%s near %s" % m for m in missing)
                         + ". See review/island-walk.png")
    write(island, os.path.join(OUT, "village.tmx"))
    print("  wrote assets/maps/village.tmx")
    return 0


if __name__ == "__main__":
    sys.exit(main())
