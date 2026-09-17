# Credits

**KAGEBI (影火)** is a non-commercial learning/portfolio project. Every asset
below was obtained free. Licences are reproduced verbatim in `LICENSES/`.

## Art

| Pack | Author | Licence | Used for |
|---|---|---|---|
| [Ninja Adventure Asset Pack](https://pixel-boy.itch.io/ninja-adventure-asset-pack) | Pixel-Boy & AAA | **CC0 1.0** | Player, NPCs, monsters, bosses, tilesets, UI theme, FX, items, all audio |
| [Dungeon Asset Pack](https://pixel-poem.itch.io/dungeon-assetpuck) | Pixel_Poem | No licence file shipped — see the itch.io page | Deep-floor tileset and animated props |
| Enemy Animations Set | *unknown — pack shipped no licence or attribution* | Unknown | Deep-floor skeletons and vampire |
| [Raven Fantasy Icons (Free)](https://clockwork-raven.itch.io/) | Caio / Clockwork Raven Studios | No licence file shipped — see the itch.io page | Loot, relic and equipment icons |
| Top-Down Character Home | *unknown — pack shipped no licence or attribution* | Unknown | The player's house: its walls, windows and roof in the village, and the rooms inside |
| [Sunnyside World](https://danieldiggle.itch.io/sunnyside) | Daniel Diggle | No licence file shipped — see the itch.io page | The island village: its land, buildings, trees and animals, and the villagers and their work animations |

## Audio

| Pack | Author | Licence |
|---|---|---|
| Ninja Adventure Asset Pack | Pixel-Boy & AAA | **CC0 1.0** |
| [SunnyLand Music](https://ansimuz.itch.io/) | Luis Zuno (@ansimuz) | **CC0** |

## Font

| Font | Author | Licence |
|---|---|---|
| [Pixeloid](https://ggbot.itch.io/pixeloid-font) | GGBot | **OFL** |

The Top-Down Character Home pack was downloaded without a licence file or a
readme, so neither its author nor its terms are recorded here. Its art is in the
game and is credited as far as it can be; if the source turns up, this row and
the `credits.home.*` strings in `assets/i18n/` are the two places to correct.

The Sunnyside World pack came as a folder of art and the GameMaker project it
was shown off in, with no licence file. The village is built from that
project's own showcase room by `tools/make_island.py`; the pack itself stays
out of git (`assets/packs/miniworld/`, under `assets/packs/` in `.gitignore`), and only the images the game loads
are derived from it. As with the house, this row and the `credits.sunnyside.*`
strings are the places to correct if its terms turn out to need more.

Pixeloid ships without the 32 Vietnamese letters that stack a tone mark on a
circumflex or breve. `tools/make_font.py` synthesises them from the font's own
glyphs (see the script for how), so the Vietnamese text is derived from Pixeloid
and carries the same OFL terms.

## Not used

Three packs were downloaded but deliberately left out of the game because they
clash with the chosen art direction. They remain untouched in `_raw/`.

- **Mystic Woods** (GameEndeavor) — non-commercial; three sheets are watermarked
  "Prenium Version!"; 48x48 characters and pure-black outlines do not match.
- **Sprout Lands Basic** (Cup Nooble) — non-commercial, credit required; pastel
  high-key palette is two stops brighter than the dungeon art.
- **Pixel Food** (likely ghostpixxells) — 1,425 colours with antialiasing against
  28-53 colours and none, elsewhere.

Neither Mystic Woods nor Sprout Lands may be redistributed, which is one reason
`_raw/` is excluded from version control.
