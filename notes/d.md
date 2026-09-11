# Agent D - content, loot, saves

Everything here crosses a boundary to another agent. Read the sections that
name your package.

---

## 1. Effect names `combat` must implement

A relic whose effect name is not implemented is a relic that silently does
nothing, which is why `ContentValidator` refuses to load one that is not on
this list. **The list lives in code**, in `ContentValidator.RELIC_EFFECTS` -
this file is the prose version of it. Adding an effect means adding it in both
places, and `ContentValidatorTest` will tell you if you forget.

### Relic effects (23)

`magnitude` is the number beside the name in `relics.json`.

| effect | magnitude means | applied |
|---|---|---|
| `damage_mult` | multiplier on outgoing weapon damage | every hit |
| `attack_speed_mult` | divides `windupSteps` and `recoverSteps` | on swing start |
| `damage_taken_mult` | multiplier on incoming damage | every hit taken |
| `move_speed_mult` | multiplier on player speed | continuous |
| `reach_add` | pixels added to `WeaponDef.reach` | on swing |
| `max_hp_add` | flat hit points, and heals for the same | on pickup |
| `gold_mult` | multiplier on gold gained | on gold pickup |
| `luck_add` | fraction `nothingWeight` is reduced by before a roll | `LootRoller.roll(table, seed, luck)` |
| `invuln_steps_add` | steps added to the player's hurt i-frames | on hit taken |
| `heal_on_kill` | hit points | on enemy death |
| `crit_chance_add` | added probability, 0..1 | per hit |
| `crit_damage_mult` | multiplier when a hit crits | per crit |
| `lifesteal` | fraction of damage dealt returned as HP | per hit |
| `chain_lightning` | probability the hit arcs to a second enemy | per hit |
| `slow_on_hit` | fraction the target's `moveSpeed` drops, for 90 steps | per hit |
| `poison_on_hit` | damage per 60 steps, for 300 steps | per hit |
| `revive_once` | fraction of max HP revived at, once per run | on death |
| `throw_extra` | extra projectiles per throw | thrown weapons only |
| `roll_invuln_add` | steps added to the roll's i-frame window | on roll |
| `damage_mult_low_hp` | damage multiplier while HP is below 35% | every hit |
| `burn_aura` | damage per 60 steps to enemies within 24px | continuous |
| `glass_cannon` | **one number, both directions**: damage dealt and damage taken are both multiplied by it | every hit, both ways |
| `room_clear_heal` | hit points | when a room's last enemy dies |

### Item effects (10)

Used by `ItemDef.effect`. Pickup and inventory code interprets these.

`heal`, `cure_poison`, `max_hp_add`, `gold`, `key`,
`speed_buff`, `damage_buff`, `shield_buff`, `drop_aggro`, `reveal_map`.

`speed_buff`, `damage_buff` and `shield_buff` all last 600 steps (10s) so the
player only has to learn one duration. `shield_buff`'s magnitude is a flat
damage pool absorbed before HP. `drop_aggro`'s magnitude is the number of steps
every enemy in the room forgets the player for.

### Upgrade effects (8)

Used by `upgrades.json`, applied once at the start of a run from `Profile`.

`max_hp_add`, `melee_damage_mult`, `move_speed_mult`, `gold_mult`,
`potion_capacity_add`, `start_keys_add`, `revive_once`, `darkness_resist`.

`max_hp_add`, `move_speed_mult`, `gold_mult` and `revive_once` are spelled the
same as the relic effects on purpose - they stack multiplicatively with them
and combat can run one resolver over both.

---

## 2. Brain names `ai` must implement (12)

Also enforced by `ContentValidator`, from `ContentValidator.BRAINS`.

| brain | behaviour | who uses it |
|---|---|---|
| `chaser` | walks straight at the player; damage is contact | larva, axolot, skeleton1 |
| `hopper` | idles, then hops a fixed distance toward the player | slime |
| `wanderer` | random walk until `aggroRange`, then `chaser` | mouse |
| `flyer` | ignores walls and pits, approaches on a sine. If `attackDamage > 0` it also fires from `attackRange` | bluebat, spirit (fires `spiritbolt`), wraith |
| `shooter` | holds at `attackRange`, fires, retreats if closed on | octopus, zealot |
| `charger` | telegraphs for `windupSteps`, then rushes in a straight line | kappared, reptile, skeleton2, bonelord |
| `ambusher` | still and harmless until the player is inside `aggroRange`, then lunges | mollusc, vampire |
| `orbiter` | circles at `attackRange`, darts in on cooldown | kappagreen |
| `splitter` | `chaser` that spawns two half-HP copies on death | mushroom |
| `caster` | stationary, telegraphs, drops an area effect at range | mushroom2, acolyte |
| `boss_frog` | scripted: hop-slam, tongue lash, summon two larva | giantfrog2 |
| `boss_tengu` | scripted, two phases. P1 dash-slash. At half HP plays `bosses/tengured/trans`, then P2 adds flame waves | tengured |

`splitter` spawns copies of its own id, so cap the split at one generation or
`mushroom` recurses forever.

---

## 3. Projectile ids

`WeaponDef.projectile` and the `shooter`/`caster` brains name these. The regions
are in `fx.atlas`; the mapping is here rather than in JSON because house rule 1
keeps region names out of the content files that do not need them.

| projectile id | region |
|---|---|
| `kunai` | `fx/projectile/kunai/spritesheet` |
| `shuriken` | `fx/projectile/shuriken/spritesheet` |
| `inkball` | `fx/projectile/energyball` |
| `spiritbolt` | `fx/projectile/bigenergyball` |
| `bonebolt` | `fx/projectile/arrow` |
| `sporecloud` | `fx/elemental/plant/spritesheet` |
| `flamewave` | `fx/elemental/flam/spritesheet` |

---

## 4. Seams other agents call

```java
// content - Kagebi already calls the first; it throws listing every problem
ContentLoader.load()                              // Gdx.files.internal, parse + validate
ContentLoader.load(Function<String, FileHandle>)  // same checks, any file resolver
ContentLoader.parse(FileHandle dataDir)           // parse only, no cross-checks

// loot - pure, seeded; any distinct long per drop, consecutive seeds are fine
LootRoller.roll(LootTableDef table, long seed)              // Array<LootRoller.Drop>
LootRoller.roll(LootTableDef table, long seed, float luck)  // luck = summed luck_add
LootRoller.gold(EnemyDef enemy, long seed)                  // coin paid on a kill
// A Drop is (itemId, count). One Drop per successful roll, so a 3-roll chest
// can return the same item twice - spawn one pickup per Drop.

// village shop
ShopCatalog shop = ShopCatalog.load();
shop.upgrades(); shop.unlocks();                  // Array<Upgrade>, Array<Unlock>
ShopCatalog.cost(upgrade, profile)                // next level's price, -1 when maxed
ShopCatalog.canBuy(upgrade|unlock, profile)
ShopCatalog.buy(upgrade|unlock, profile)          // debits gold; false if it cannot
ShopCatalog.requirementMet(unlock, profile)       // pure
shop.upgradeValue("max_hp_add", profile)          // combined value at owned levels
shop.character("ninjared")                        // the Unlock carrying its perk, or null
shop.bank(profile, runSummary)                    // game-over / victory: banks the run

// saves
SaveManager.load()                                // as before
SaveManager.save(profile)                         // now returns boolean; false = old save kept
```

**`ShopCatalog` is not in `ContentRegistry`.** The registry was frozen for
this milestone, so the village upgrades and the character/weapon unlocks load
through `com.kagebi.data.ShopCatalog`. `ContentLoader.load()` validates them at
boot all the same.

`upgradeValue` compounds effects ending in `_mult` (two levels of 1.08 is
1.1664) and adds everything else. Run start should call it once per upgrade
effect, then apply the chosen character's perk (`shop.character(id)`), which is
spelt the same way and stacks the same way.

`Unlock.requirement` is one of `none`, `deepest_floor`, `wins`, `runs`,
`bestiary`, compared against `requirementValue`.

## 5. Fields I wish the schema had

None of these blocked me; each cost a workaround that is written down where it
happens. In the order I would add them.

1. **`FloorDef.hpScale` / `damageScale`.** Floor 5 is "the depths mix" and
   wants the floor-4 roster at floor-5 numbers. Without a scalar the only way
   to make a skeleton harder on floor 5 is a second def with a second id, so
   floor 5 got four enemies of its own (`wraith`, `acolyte`, `zealot`,
   `bonelord`) instead of reusing floor 4's. That is better art anyway, but on
   a floor 6 it would stop being a choice.
2. **`WeaponDef.onHit` (effect name + magnitude).** The net's whole identity is
   that it roots what it catches, and there is nowhere to say so. Combat has to
   special-case the id `net` until this field exists. The same gap stops the
   pickaxe having armour-pierce and the shuriken piercing enemies.
3. **`WeaponDef.projectileSpeed` and `pierce`.** `reach` is doing double duty as
   travel distance for a thrown weapon; how fast it gets there is unstated. I
   used 240 px/s for `kunai` and 300 for `shuriken` in the arithmetic.
4. **`RelicDef.weight`.** Rarity is the only signal, so the award code has to
   hard-code what a rarity is worth. Intended: **COMMON 60, RARE 30, EPIC 10**,
   and a relic already held is re-rolled rather than offered twice.
5. **`EnemyDef.projectile`.** The `shooter` and `caster` brains need to know
   what they fire and the def cannot say. Section 3 has the mapping, by hand,
   which is exactly the kind of table that rots.
6. **`ItemDef.priceBase`.** The in-run shop has to price items from somewhere.
   Suggested until the field exists: `potion_small` 40, `potion_large` 90,
   `antidote` 35, `draught_swift` 70, `draught_shadow` 85, `elixir_ward` 110,
   `smoke_bomb` 60, `key_iron` 50, a common relic 120, a rare 260, an epic 500.
7. **`LootTableDef.guaranteed`.** A boss chest that must contain at least one
   key is currently expressed as `nothingWeight: 0` plus enough rolls, which is
   a probability argument rather than a guarantee.
8. **`EnemyDef.eliteOf`.** There is no way to say "this is the same enemy with
   a gold outline and double HP", so an elite is a whole second def.

---

## 6. Things the other three should know

- **Enemy sprite cells are not uniform.** Ninja Adventure trash is `cell: 16`
  on a 64x64 four-by-four sheet. The depths skeletons and the vampire are
  `cell: 32` on a 192x32 horizontal strip of six. The small depths idles
  (`wraith`, `acolyte`, `zealot`, `bonelord`) are `cell: 16` on a 64x16 strip
  of four. `giantfrog2` is `cell: 40`, `tengured` is `cell: 82`. Read
  `EnemyDef.cell` rather than assuming.
- **Floor music is named, not pathed.** `floors.json` carries either an
  `Assets` constant name (`MUSIC_DUNGEON`) or a bare track filename
  (`30_ruins.ogg`). `ContentLoader` resolves the first by reflection over
  `Assets` and the second against `Assets.MUSIC_DIR`, so the directory still
  lives in exactly one place. `FloorDef.music` comes out as a full path ready
  for `AudioService.playMusic`. Ambience resolves the same way against
  `Assets.SFX_DIR + "ambient/"`.
- **`ItemDef.sprite` beats `ItemDef.icon`.** Items name a region in `ui.atlas`
  under `items/`, which is in the Ninja Adventure palette. The Raven icon index
  is the list-view fallback and is in a different palette; do not draw both in
  the same panel.
- **Relics only have Raven icons.** There is no pack art for them. They are for
  the relic panel and the pause screen, not for the world.
- **Gold is not in the trash loot tables.** `EnemyDef.goldMin/goldMax` is the
  purse and it pays on every kill. The tables carry the occasional physical
  pickup on top, which is why `nothingWeight` is around 70 there and 0 in a
  chest.
- **`RunSummary.gold` banks in full.** Nothing is taxed on death. Call
  `ShopCatalog.bank(profile, summary)` from the game-over and victory screens;
  it applies the flamekeeper upgrade and then `Progression.bank`. A
  first-timer dies on floor 3 with about 635 banked (after spending a fifth in
  the dungeon's shops) and the two cheapest upgrades are 350 and 380, so the
  first death buys exactly one. `BalanceTest` pins that.
- **`Progression.MAX_DARKNESS` is 10** - my guess at how many dimming steps the
  hub can show before the art is unreadable. It is the hub's number to change.
- **Biomes are `ruins`, `ruins_green`, `ruins_orange`, `depths`** - the folder
  names procgen writes under `assets/maps/rooms/`. Floors 1/2/3 take one ruins
  colourway each and 4/5 share `depths`. `ContentValidator.BIOMES` holds the
  four names; a fifth biome folder needs adding there too.
- **Floor 3 and floor 5 are the only floors with a boss.** `FloorDef.boss` is
  null on 1, 2 and 4, and `hasBoss()` already answers that.
- **`assets/data/icons.json` is a name-to-index map, not a def.** It is loaded
  into `ContentRegistry`'s item and relic icons at parse time - `icons.json`
  entries are referenced from the other files by name (`"icon": "potion_red"`)
  so that a shuffle of the Raven grid is one file to edit. A raw integer is
  also accepted anywhere an icon name is.
