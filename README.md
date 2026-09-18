# KAGEBI (影火)

A pixel-art roguelite dungeon crawler in Java, built on libGDX 1.14.2: five
stages beneath an island village that farms, fishes and cooks for whoever goes
down.

Non-commercial; see [CREDITS.md](CREDITS.md) for the asset packs and their
licences.

## The Last Flamekeeper

Under the island of Kagemura burns the *Kagebi*, the shadow flame. For as long as
anyone can remember, its keepers have gone down through the torii to tend it,
and for as long as it has burned the island has had good soil, full nets and
short winters.

One night every keeper went down, and none came back. Only the old Master is
left, the last of the Flamekeepers, with six apprentices who have never been
further than the Old Well: Green Shade, Crimson Flame, Blue Current, Black Mist,
Ember Heart and Water Moon.

The flame is guttering. The goblin miners have climbed up out of pits that
turned strange, and the skeleton guards who served the old keepers have come out
of the graveyard to watch the shore, as they did in life. The Elder keeps the
village fed. The Herbalist turns what the apprentices carry back into strength
that lasts. The Master teaches the one lesson he has left: roll through the blow,
then strike back.

Every descent goes a little deeper, through the Old Well, the Flooded Catacombs,
the Broken Shrine and the Cursed Depths. Every apprentice who comes back up
brings word of voices in the Broken Shrine, where the lost keepers are calling.
They also bring a name. The Red Tengu, who waits at the Flame Core, was the first
Flamekeeper of all: the one who would not let the flame go out when he did.

At the Flame Core, one of the six will have to choose.

## What you do

- **Go down.** Five stages, picked from a world map and played one at a time.
  A stage starts at full health, banks its own gold, and opens the next one when
  it is cleared. The difficulty is chosen per stage.
- **Keep the village.** Kagemura is an island of people, goblins and skeletons:
  - Plant and pick the farm's plots.
  - Collect what the woodcutter, the rancher, the fisher and the goblin miner
    make while you are away.
  - Sell it to the Herbalist for potions, tools, upgrades and new ninjas.
  - Have the cook turn it into meals to carry down.

  The village clock runs only while the game is open.
- **Look around.** Zoom out to the whole island with the mouse wheel.

## Controls

| Action | Keys |
|---|---|
| Move | W A S D, or the arrow keys |
| Attack | J / Z |
| Throw the off-hand weapon | K / X |
| Roll | Space / L |
| Interact, talk, buy | E / Enter |
| Use the quick item (a meal or a potion) | Q / C |
| Bag (village), inventory (dungeon) | Tab / I |
| Floor map | M |
| Zoom in / out (village) | `=` / `-`, the numpad `+` / `-`, or the mouse wheel |
| Pause, back | Esc |

Every key except Esc can be rebound under Settings → Controls. In the village,
the ninja's badge at the top right opens the kit, and the basket beside it opens
the bag.

## Getting started

You need:
- **JDK 21**
- **Maven 3.9+**
- **Python 3.11** with Pillow (`pip install pillow`), to build the art and sound
  once

### 1. Get the asset packs

The game's art and sound come from third-party packs that are **not in this
repository**: some forbid redistribution, and some shipped with no licence at
all. What the game loads is built from them into `assets/gfx/`, `assets/audio/`
and `assets/atlas/`, and git ignores those too. A fresh clone has no pictures and
no sound until the packs are in place and step 2 has run.

**Asset packs:** [Google Drive]([https://drive.google.com/drive/folders/1_bz4yVlaBx8Pcqx4OGdEqW91wBJRO0We?usp=sharing])
<!-- Replace PASTE_DRIVE_LINK_HERE above with the shared link to the packs. -->

Unpack them so these folders sit in the repository, with exactly these names:

```
_raw/
  Ninja Adventure - Asset Pack/
  2D Pixel Dungeon Asset Pack/
  Enemy_Animations_Set/
  Free - Raven Fantasy Icons/
  SunnyLand Music/
assets/packs/
  homeassets/        Top-Down Character Home
  miniworld/         Sunnyside World
```

Where a pack has a public page, that is where it comes from. Each keeps its
author's terms:

| Pack | Page | Licence |
|---|---|---|
| Ninja Adventure Asset Pack | [pixel-boy.itch.io](https://pixel-boy.itch.io/ninja-adventure-asset-pack) | CC0 |
| Dungeon Asset Pack | [pixel-poem.itch.io](https://pixel-poem.itch.io/dungeon-assetpuck) | see the page |
| Raven Fantasy Icons (Free) | [clockwork-raven.itch.io](https://clockwork-raven.itch.io/) | see the page |
| SunnyLand Music | [ansimuz.itch.io](https://ansimuz.itch.io/) | CC0 |
| Sunnyside World | [danieldiggle.itch.io](https://danieldiggle.itch.io/sunnyside) | see the page |
| Enemy Animations Set, Top-Down Character Home | unknown | unknown |

### 2. Build the art and sound

```powershell
python tools/build_assets.py   # slices the packs into assets/gfx and assets/audio, about a minute
python tools/pack_atlas.py     # packs the sprites into assets/atlas
```

`build_assets.py` lists any pack it could not find, and exits non-zero if one
is missing. It also warns about three licence files it cannot find, from Mystic
Woods, Sprout Lands and the Pixeloid font. Those only refresh `LICENSES/`, which
is already committed, and do not stop the build.

### 3. Play

```powershell
mvn compile exec:java
```

`exec:java` runs what is already compiled and does not compile anything itself.
After the first build, `mvn exec:java` is enough until the code changes.

## ⚠ Passing arguments: quote the whole thing in PowerShell

This is the one command in the project that is easy to get wrong, and the error
it gives does not say what is wrong.

```powershell
# WRONG in PowerShell - fails with "Unknown lifecycle phase .args=..."
mvn exec:java -Dexec.args="--screen fight --page 5"

# RIGHT in PowerShell - the quotes go round the whole argument
mvn exec:java "-Dexec.args=--screen fight --page 5"
```

**Why.** PowerShell strips the quotes and then splits on the space. Maven is
handed two arguments, `-Dexec` and `.args=--screen fight --page 5`, and reads the
second as the name of a build phase. Nothing is broken; the argument never
reached the game. With the quotes round the whole thing, it survives the split
intact.

In `cmd.exe` and in bash the original form works, because neither splits it:

```bat
mvn exec:java -Dexec.args="--screen fight --page 5"
```

## Launch flags

Every screen in the game can be opened directly. Art direction is the one thing
no test can check, so anything that can be looked at has to be reachable by a
camera without playing to it.

| Flag | What it does |
|---|---|
| `--screen <name>` | Which screen to open; see the list below |
| `--page <n>` | What the page means is the screen's business: a settings tab, a floor, a credits section |
| `--lang vi\|en` | Start in that language |
| `--scale <n>` | Window scale; 2 or more (at 1x a 12px UI row is unclickable) |
| `--frames <n>` | Render this many frames, then act |
| `--screenshot <path>` | Save a PNG after `--frames` and exit |
| `--seed <n>` | Pin the dungeon seed, so two screenshots of one feature are taken in the same room |

Screen names, with what `--page` means for each:

```
boot                   the loading bar, then the menu
menu                   (default)
settings  --page 1-3   audio, controls, video
style     --page 1-2   widget sheet, surface sheet
select    --page 1-6   character select, with that ninja highlighted
loadout   --page 1-6   the same screen changing the run's kit, over the village
hub       --page 1-11  the village: 1 at home, 2 the torii, 3 the shop, 4-9 each
                       region's worker, 10 zoomed out to the whole island,
                       11 zoomed out once at the torii
harvest   --page 1-3   the village mid-effect: goods flying from the woodcutter,
                       a fish over the fisher, a pumpkin picked
bag       --page 1-5   the bag over the village, on that tab
counter   --page 1-6   a trading counter: the herbalist's four tabs, the farmer, the cook
home      --page 1-2   inside the house: on the doormat, or on the rug by the table
world     --page 1-5   the world map, open to that stage and focused on it
stage     --page 1-5   the same, with that stage's panel and its difficulty row
cleared   --page 1-5   the stage-clear screen, for that stage
talk      --page 1-3   the village, mid-conversation with that villager
store     --page 1-4   the herbalist's upgrade shelf, over a profile N runs deep
unlocks   --page 1-4   the same, on its second tab
dungeon   --page 1-5   the start room of that stage
map       --page 1-5   the same, with the floor map expanded
fight     --page 1-5   the first room of that floor that has enemies in it
swing     --page 1-5   the same, swinging on a timer so a blade is visible
throw     --page 1-10  the same with a kunai in the off hand, throwing; 6-10 a shuriken
treasure  --page 1-5   the first treasure room, for looking at a chest
shop      --page 1-5   the first shop room, for looking at the shopkeeper
trade     --page 1-5   the same, mid-purchase, with gold to spend
slide                  halfway through the first room transition
exit      --page 1-5   standing on that floor's way down
pause, inventory       those overlays over floor 1
victory, gameover      the end screens, over a sample run
credits   --page 1-4   the roll, starting at that section
```

A name that matches nothing falls through to the main menu rather than failing,
so a typo during a screenshot run costs a glance, not a stack trace.

Examples:

```powershell
mvn exec:java "-Dexec.args=--screen fight --page 3 --scale 4"
mvn exec:java "-Dexec.args=--screen bag --page 3 --frames 240 --screenshot review/bag.png"
mvn exec:java "-Dexec.args=--screen swing --page 1 --seed 7 --frames 120 --screenshot review/swing.png"
```

In the village, take screenshots at `--frames 240`: the village's name card is
still fading out before that.

`--screenshot` exits through `Gdx.app.exit()`, so the shell may report a
non-zero exit code even though Maven prints `BUILD SUCCESS` and the PNG is
written. Read the `BUILD` line, not the exit code.

## Tests

```powershell
mvn test
```

All of it runs headless, with no OpenGL context. `combat/`, `loot/`,
`save/migration/` and `village/` are forbidden from touching `Gdx.` at all;
`loot/PurityTest` enforces that by scanning the source rather than leaving it to
memory. `gen/` is written to the same rule but is not yet on that list.

The tests read the built assets as well. Run step 2 of Getting started first;
without it, the content checks fail on every sprite they cannot find in an
atlas.

## Build a standalone .exe

```powershell
python tools/make_exe.py
```

The output is `dist/KAGEBI/`, and `KAGEBI.exe` in it is the whole game. A
trimmed Java runtime is bundled, so the player needs no Java installed. Zip the
folder to hand it to someone. Add `--installer` for an `.msi` as well, which
also needs WiX.

Nothing else in the project depends on this, and it is the slowest thing here
(about four minutes). The source packs in `assets/packs/` are left out of it.

## Other tools

Only `build_assets.py` and `pack_atlas.py` are needed to play from a fresh clone.
The rest regenerate files that are already committed, and are for changing them.

```powershell
python tools/make_maps.py      # regenerate the 104 room .tmx files
python tools/make_island.py    # rebuild the island village from the Sunnyside pack, keeping its decor layer
python tools/make_village.py   # rebuild the inside of the house from its art pack
python tools/make_world.py     # lay out the world map again, keeping its decor layer
python tools/make_font.py      # rebuild the bitmap font, Vietnamese marks included
python tools/preview_map.py    # render a .tmx to a PNG; --collision tints what blocks red
python tools/preview_tiles.py  # render a tileset with its grid, for measuring
python tools/link_assets.py    # point a git worktree at this checkout's built assets and packs
```

## Where things are

```
assets/          art, audio, fonts, maps, and the JSON the game is balanced in
  data/          enemies, weapons, relics, items, floors, upgrades, loot tables, village.json
  i18n/          vi.json + en.json (interface), content.*.json (names and flavour)
  maps/          village.tmx (make_island.py), home.tmx (make_village.py), world.tmx, rooms/
  packs/         Sunnyside World and Top-Down Character Home, untracked (step 1)
  gfx/ audio/ atlas/   built by the tools, untracked (step 2)
src/main/java/com/kagebi/
  screen/        every screen, plus Screens.java which the --screen flag reads
    island/      the village's props, clouds, sea and harvest effects
  entity/        player, enemies, projectiles, the world that steps them
  combat/        damage, hitboxes, hit resolution - pure, no Gdx
  gen/           floor generation from the room templates - pure
  loot/          drop rolling and shop stock - pure
  village/       the farm, the workers, the market, the kitchen, the pantry - pure
  data/          content loading and validation
  save/          the profile that survives death
tools/           Python: asset pipeline, map generation, packaging
_raw/            the older original asset packs, untracked (step 1)
```

`_raw/` and `assets/packs/` are deliberately kept out of git. Two of the packs
that were downloaded forbid redistribution, and the licences that are known are
recorded in `CREDITS.md`.
