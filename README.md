# KAGEBI (影火)

A pixel-art roguelite dungeon crawler in Java, built on libGDX 1.14.2.

Five stages, chosen from a world map and played one at a time. A stage starts
at full health, banks its own gold, and opens the next one when it is cleared;
the difficulty is picked per stage, beside the button that starts it. The
village between them is where that gold is spent, and where the player's own
house is.

Non-commercial; see [CREDITS.md](CREDITS.md) for the asset packs and their
licences.

Requires **JDK 21** and **Maven 3.9+**, both of which are already on the
development machine. Python 3.11 with Pillow is needed only for the tools in
`tools/`.

## Play it

```powershell
mvn exec:java
```

## ⚠ Passing arguments: quote the whole thing in PowerShell

This is the one command in the project that is easy to get wrong, and the error
it gives does not say what is wrong.

```powershell
# WRONG in PowerShell - fails with "Unknown lifecycle phase .args=..."
mvn exec:java -Dexec.args="--screen fight --page 5"

# RIGHT in PowerShell - the quotes go round the whole argument
mvn exec:java "-Dexec.args=--screen fight --page 5"
```

**Why.** PowerShell strips the quotes and then splits on the space, so Maven is
handed two arguments - `-Dexec` and `.args=--screen fight --page 5` - and reads
the second as the name of a build phase. Nothing is broken; the argument never
reached the game. The form with the quotes round the whole thing survives the
split intact.

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
| `--screen <name>` | Which screen to open; see the table below |
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
select    --page 1-6   character select, with that ninja highlighted
loadout   --page 1-6   the same screen changing the run's kit, over the village
hub       --page 1-2   the village: arriving home, or in the garden gateway
home      --page 1-2   inside the house: on the doormat, or on the rug by the table
world     --page 1-5   the world map, open to that stage and focused on it
stage     --page 1-5   the same, with that stage's panel and its difficulty row
cleared   --page 1-5   the stage-clear screen, for that stage
talk      --page 1-3   the village, mid-conversation with that villager
store     --page 1-4   the herbalist's stall, over a profile N runs deep
unlocks   --page 1-4   the same, on its second tab
dungeon   --page 1-5   the start room of that stage
map       --page 1-5   the same, with the floor map expanded
fight     --page 1-5   the first room of that floor that has enemies in it
swing     --page 1-5   the same, swinging on a timer so a blade is visible
throw     --page 1-5   the same with a kunai in the off hand, throwing
treasure  --page 1-5   the first treasure room, for looking at a chest
shop      --page 1-5   the first shop room, for looking at the shopkeeper
trade     --page 1-5   the same, mid-purchase, with gold to spend
slide                  halfway through the first room transition
exit      --page 1-5   standing on that floor's way down
pause, inventory       those overlays over floor 1
victory, gameover      the end screens, over a sample run
credits   --page 1-4   the roll, starting at that section
style     --page 1-2   widget sheet, surface sheet
```

A name that matches nothing falls through to the main menu rather than failing,
so a typo during a screenshot run costs a glance, not a stack trace.

Examples:

```powershell
mvn exec:java "-Dexec.args=--screen fight --page 3 --scale 4"
mvn exec:java "-Dexec.args=--screen store --page 2 --frames 90 --screenshot review/shop.png"
mvn exec:java "-Dexec.args=--screen swing --page 1 --seed 7 --frames 120 --screenshot review/swing.png"
```

`--screenshot` exits through `Gdx.app.exit()`, so the shell may report a
non-zero exit code even though Maven prints `BUILD SUCCESS` and the PNG is
written. Read the `BUILD` line, not the exit code.

## Tests

```powershell
mvn test
```

All of it runs headless - no OpenGL context. `combat/`, `loot/` and
`save/migration/` are forbidden from touching `Gdx.` at all, and
`loot/PurityTest` enforces that by scanning the source rather than leaving it
to memory. `gen/` is written to the same rule but is not yet on that list.

## Build a standalone .exe

```powershell
python tools/make_exe.py
```

Output is `dist/KAGEBI/`, and `KAGEBI.exe` in it is the whole game - a trimmed
Java runtime is bundled, so the player needs no Java installed. Zip the folder
to hand it to someone. Add `--installer` for an `.msi` as well, which also
needs WiX.

Nothing else in the project depends on this, and it is the slowest thing here
(about four minutes).

## Other tools

All of these regenerate things that are already committed, so none is needed to
play or to build.

```powershell
python tools/make_maps.py      # regenerate the 104 room .tmx files
python tools/make_village.py   # rebuild the village and the house from the art pack
python tools/make_world.py     # lay out the world map again, keeping its decor layer
python tools/pack_atlas.py     # repack the texture atlases
python tools/make_font.py      # rebuild the bitmap font, Vietnamese marks included
python tools/preview_map.py    # render a .tmx to a PNG; --collision tints what blocks red
python tools/preview_tiles.py  # render a tileset with its grid, for measuring
```

## Where things are

```
assets/          art, audio, fonts, maps, and the JSON the game is balanced in
  maps/          village.tmx and home.tmx (make_village.py), world.tmx, rooms/
  data/          enemies, weapons, relics, items, floors, upgrades, loot tables
  i18n/          vi.json + en.json (interface), content.*.json (names and flavour)
src/main/java/com/kagebi/
  screen/        every screen, plus Screens.java which the --screen flag reads
  entity/        player, enemies, projectiles, the world that steps them
  combat/        damage, hitboxes, hit resolution - pure, no Gdx
  gen/           floor generation from the room templates - pure
  loot/          drop rolling and shop stock - pure
  data/          content loading and validation
  save/          the profile that survives death
tools/           Python: asset pipeline, map generation, packaging
_raw/            the original asset packs, untracked and never redistributed
```

`_raw/` is deliberately not in git. Two of the packs in it forbid
redistribution, and the licences are recorded in `CREDITS.md`.
