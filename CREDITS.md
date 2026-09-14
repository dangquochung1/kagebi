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
| Top-Down Character Home | *unknown — pack shipped no licence or attribution* | Unknown | The village map: the player's house, its garden and interior, and the animated trees, smoke, birds and cat on it |

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
