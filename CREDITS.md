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
| [Dungeon Tileset](https://craftpix.net/) | CraftPix | [CraftPix file licence](https://craftpix.net/file-licenses/) | The Drowned Cove's stone, water, fire and traps |
| [Slime Enemy Sprites](https://craftpix.net/) | CraftPix | [CraftPix file licence](https://craftpix.net/file-licenses/) | The Drowned Cove's three slimes |
| [Pirate Characters](https://craftpix.net/) | CraftPix | [CraftPix file licence](https://craftpix.net/file-licenses/) | The Drowned Captain, and the two things he becomes |
| [Fire and Water Spell Effects](https://craftpix.net/) | CraftPix | [CraftPix file licence](https://craftpix.net/file-licenses/) | The cove boss's spells, balls and arrows |
| [RPG Fantasy GUI](https://craftpix.net/) | CraftPix | [CraftPix file licence](https://craftpix.net/file-licenses/) | The forge, equipment and bag windows on the character sheet |
| Pixel Art Skill Animations - Lightning | Frostwindz | Pack licence agreement, see `LICENSES/` | The three lightning skills, their bolts and their icons |
| Pixel Art Skill Animations - Fire | Frostwindz | Pack licence agreement, see `LICENSES/` | The fire ring, the bloom and the starburst of Hoả Tâm's three skills |
| Pixel Art VFX - Poison | Frostwindz | Pack licence agreement, see `LICENSES/` | Tử Uyển's poison: what falls on a room, what she throws, the trail of her dash and the mark a poisoned enemy carries |
| [Pixel Art VFX - Starcaller](https://frostwindz.itch.io/) | Frostwindz | Pack licence agreement, see `LICENSES/` | Tử Uyển's star: the slam, the comet she rides, and the ward she wears |
| [Super Pixel Effects Gigapack](https://untiedgames.itch.io/super-pixel-effects-gigapack) | Will Tice / unTied Games | [Pack licence](http://untiedgames.com/files/license.txt) | The ward drinking a blow, and three stacks of poison going off |
| [Free Pixel Effects Pack](https://codemanu.itch.io/pixel-effects-pack) | CodeManu & DavitMasia | **Public domain**, no credit required | The flame trail on a burning sword, the fireball it throws, and the mark a burning enemy carries |
| Fire Effect 2 | *unknown — pack shipped no licence or attribution* | Unknown | The fire around Hoả Tâm while she charges, and the burst where she lands |

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

Fire Effect 2 arrived the same way - two sprite sheets in a bare folder, no
readme and no author. It is credited as far as it can be, and if its source
turns up, that row and the `credits.fire2.*` strings are where to correct it.

The Sunnyside World pack came as a folder of art and the GameMaker project it
was shown off in, with no licence file. The village is built from that
project's own showcase room by `tools/make_island.py`; the pack itself stays
out of git (`assets/packs/miniworld/`, under `assets/packs/` in `.gitignore`), and only the images the game loads
are derived from it. As with the house, this row and the `credits.sunnyside.*`
strings are the places to correct if its terms turn out to need more.

The unTied Games gigapack allows its art in a game and forbids reuploading the
art itself, which is the same shape as the CraftPix terms - so it sits in
`assets/packs/`, which `.gitignore` excludes, and only the strips
`tools/make_poisonfx.py` derives from it reach `assets/gfx/`. Unlike the
CraftPix packs it also *requires* attribution, which is why it has a row above
and a block in the credits roll rather than only this paragraph.

The five CraftPix packs share one licence, and it is not CC0: it allows the
art in a game and forbids redistributing the art itself. So they sit in
`assets/packs/{dungeon6,slimes6,bosses6,bossfx6,heroui}/`, which `.gitignore`
already excludes, and only what `tools/build_cove.py` and
`tools/slice_heroui.py` derive from them reaches `assets/gfx/` - which is
itself derived and untracked. The licence text each one ships is copied into
`LICENSES/Craftpix-*.txt`.

There was a sixth, the Japanese Fantasy Characters, which three of the four
playable heroes came out of. They were drawn side-on in one facing at four
times this game's size, so they arrived as 48px single-row strips with no back
view - a second animation shape that every screen drawing a hero had to know
about, and three of them did not. The roster is six ninja now and the pack is
no longer used.

Pixeloid ships without the 32 Vietnamese letters that stack a tone mark on a
circumflex or breve. `tools/make_font.py` synthesises them from the font's own
glyphs (see the script for how), so the Vietnamese text is derived from Pixeloid
and carries the same OFL terms.

## Not used

Packs that are downloaded, tried and then cut are deleted from `_raw/` rather
than left sitting there. A pack that forbids redistribution is a pack this
repository must not be able to leak, and the surest way not to leak it is not
to have it. Nothing in `assets/` was ever derived from one.
