# Agent C (procgen): notes for the other three

## Biome names are load-bearing

`FloorDef.biome` has to spell one of these room folders exactly:

| biome          | art pack | colour  | meant for |
|----------------|----------|---------|-----------|
| `ruins`        | ruins    | brown   | floor 1   |
| `ruins_green`  | ruins    | green   | floor 2   |
| `ruins_orange` | ruins    | orange  | floor 3   |
| `depths`       | depths   | purple  | floors 4, 5 |

The three ruins folders hold the same 26 layouts in three colours. If you get a
name wrong, the game still runs, but it borrows rooms from another biome, so
all you see is the wrong tileset. `RoomCatalogTest.theBiomesAreExactlyTheFoldersFloorsJsonNames`
pins the set.

The plan's "blue-green" colour does not exist. The ruins pack's fourth colour
is sage green, and nothing in its four sheets is blue. A fourth cream variant
also exists and isn't used: add `("ruins_cream", "ruins", "cream")` to
`BIOMES` in `tools/make_maps.py` if a floor wants it.

## For the screen agent

- **Door geometry:** `RoomTemplate.DOOR_SPAN` (3), `DOOR_X` (8, top and bottom
  walls, columns 8-10) and `DOOR_Y` (4, left and right walls, rows 4-6). Row 4
  reads the same from the top or the bottom, so there's no y-flip question.
  Every room has all four openings. To seal a door that leads nowhere, cover
  those three tiles and make them solid. `RoomCatalogTest` holds the `.tmx`
  files to these constants.
- **`ENTRY` tags** name the side of *this* room the player arrives at. Leaving
  a room to the east means arriving at the next room's `LEFT` entry. There is
  also one untagged `ENTRY` in the middle, for the start room and debug warps.
- **Layers:** `ground`, `decor`, `walls`, `props`, `overhead`, as in
  `TiledRooms`. Rooms leave `overhead` empty. In depths rooms, lit torches are
  painted as static tiles in `props` on top of top-wall cells. Those cells are
  already solid, so collision doesn't change.

## For the entity / content agents

Spawn tags (the object `name`) used in the rooms:

| type         | tag                  | where |
|--------------|----------------------|-------|
| `ENEMY`      | *(none)*             | 5 per normal room: roll from the floor pool |
| `ENEMY`      | `boss`               | exactly one per boss room, near centre |
| `CHEST`      | *(none)* / `locked` / `secret` / `stock` | treasure / locked / secret / shop (3 in a shop) |
| `SHOPKEEPER` | *(none)*             | one per shop |
| `EXIT`       | *(none)*             | one per exit room, centre |
| `PROP`       | `torch`              | 0-2 per room (none in shops), near corners. Ignore it or put an animated torch there |

## For whoever calls `FloorGenerator`

- `generate(def, seed)` is safe to call with **one run seed for every floor**.
  The floor number is folded into the RNG stream. Before that, floors 4 and 5
  came out identical on 150 of 1000 shared seeds.
- On a boss floor the boss room *is* the stairs: `layout.exit()` returns it.
  A floor with `boss == null` gets a separate `EXIT` room at its deepest dead end.
- Every floor gets 1 locked and 1 secret room when its room budget allows.
  These are constants in `FloorGenerator` (`LOCKED_ROOMS`, `SECRET_ROOMS`). If
  designers want them per floor, they belong in `FloorDef`, and that's a data
  agent change.
- `FloorGenerator.check(layout, def)` is public and throws on any broken floor,
  which may help for re-validating a floor restored from a save.
- `Room.template` is only null if the catalog is empty.

## Editing rooms in Tiled

`decor` is yours. `tools/make_maps.py` carries it across every regeneration,
flip bits and hand-added tilesets included. **Everything else is regenerated.**
To change an obstacle or spawn permanently, edit `ROOM_PLAN` / `LAYOUTS` in the
script, not the `.tmx`. `RoomCatalogTest` fails on the edits that break a room
(something in a doorway, a sealed-off pocket of floor, a spawn inside a wall, a
renamed layer), so run `mvn test` after a Tiled session.

## Asides

- libGDX `Array` hands out cached iterators and throws if the same array is
  for-each'd inside its own loop. `FloorGenerator` uses indexed loops over the
  shared catalog for this reason.
- The baseline was 30 tests, not the 31 the brief expected. It was green
  either way.
- Nothing needed regenerating in `assets/gfx/`.
