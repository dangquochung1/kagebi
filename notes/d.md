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

### Relic effects (22)

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

`heal`, `cure_poison`, `max_hp_add`, `gold`, `diamond`, `key`,
`speed_buff`, `damage_buff`, `shield_buff`, `drop_aggro`, `reveal_map`.

`gold`, `diamond` and `key` are routed by `ItemDef.kind` as well as by effect,
and `ContentValidator` fails an item whose kind and effect disagree - a GOLD
item with a `heal` effect would pay into the purse and never heal. `diamond` is
the second currency: banked into `Profile.diamonds` like gold, found only in
chests, and deliberately **not** multiplied by `gold_mult`, because the fortune
upgrade track is priced against gold income.

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

## 2. Brain names `ai` must implement (19)

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
| `burster` | holds at `attackRange`, swells, then fires a ring of shots on every spoke at once | slimetide |
| `bomber` | closes while it flashes, then detonates an area effect on itself - and again, for less, when it is killed | slimeember |
| `boss_frog` | scripted: hop-slam, tongue lash, summon two larva | giantfrog2 |
| `boss_tengu` | scripted, two phases. P1 dash-slash. At half HP plays `bosses/tengured/trans`, then P2 adds flame waves | tengured |
| `boss_pirateleader` | melee only: swing, charge, and a combo that lands three shoving blows down one line while walking in behind them | pirateleader |
| `boss_orb` | untouchable and immobile for eight seconds, dropping spells on the player, then summons `evolvesInto` and kills itself | fireorb |
| `boss_orb_tide` | the same, but throwing arcs outward in a turning ring rather than aiming at anyone | waterorb |
| `boss_piratezombie` | three-arrow fan, a barrage down one line, and a wall of burning ground. At `enrageAt` rains a wave and calls in its `summons` | piratezombie |
| `boss_squidman` | the same fan, plus a ring of hazards around itself with one gap on the far side | squidman |

A boss sizes each of its moves for itself. `EnemyDef.activeSteps` is one
number and a boss has eight attacks: a swing is a sixth of a second and a
three-blow combo is nearly three, so one number shared between them is wrong
for at least one of them. `BossBrain.activeSteps` gives each move its own
window and `AiBrain.longestActiveSteps` declares the ceiling, which is what
`AiStateMachineTest` holds every brain to - the old invariant was "no state
outlives the number in the def", and this widens it in the open rather than
quietly. Before it existed, `pirateleader` carried the combo's 190 steps and
so every ordinary swing he threw stood open for over three seconds.

The last four are one fight: `pirateleader` -> `fireorb` -> `piratezombie` ->
`waterorb` -> `squidman`, each named in the one before's `evolvesInto` and
summoned by its brain on death. Three full health bars with two untouchable
intermissions between them, rather than one bar in slices - which is what
`phases` does, and what needs a `trans` strip these packs do not ship.

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

There were two more, `fireball` and `waterball`, which were Kitsune's off hand
and only hers. They went when she did: the rule that one character could hold
them and nobody else was enforced in four places, was enforced correctly in
two, and existed at all to give one roster entry a reason to be picked.

The cove's boss names its own art directly in `EnemyDef.projectile`, because
its six regions are its own and nothing else in the game shoots them:
`fx/skill6/{fire,water}_{ball,spell,arrow}`. Two more are derived rather than
named - `fx/skill6/fire_burst` and `water_burst`, which are what a spell
leaves on the floor where it lands. `Assets.Fx.burstOf` does the pairing, so
no def carries a second field that never varies independently of the first.
They are not from the spell pack at all: they are the death strips of the
ember and tide slimes, re-emitted as fx by `build_cove.py`, because a slime
dissolving into a burning puddle is exactly what a spell landing looks like
and the art was already converted.

Three shapes of enemy shot, and the difference is worth knowing before adding
a fourth:

- `fireProjectile` - straight, at a speed, until its life runs out.
- `homingProjectile` - steers at the player for `homeSteps` at up to
  `turnRate` radians a step, then flies straight. Both bounds are what make it
  fair: running outruns the turn, and waiting does not outlast the window.
- `lobProjectile` and `rainSpell` - airborne, so no walls and no hitbox until
  they land, and both leave a hazard where they do. A lob eases along its
  ground track (fast out, nearly still at the top, then over and down); a rain
  falls straight from a height that follows from how long it is given, and the
  fall is its own warning. `placeHazard` is the fourth thing and is not a
  shot: it puts burning ground down where it is asked, blinking first. Use it
  for a wall or a ring laid along the floor, never for rain - rain that
  appears on the floor reads as something climbing out of it.

---

## 3b. Gear and stone effects

`GearDef.effects` and `GemDef.effect` name these, and
`ContentValidator.GEAR_EFFECTS` is the list. A shorter list than the relics
get, on purpose: a relic is a run-long surprise out of a chest and may do
something strange, while gear is bought and forged between runs and is
deliberately dull - nine numbers that go up. Nothing here needs new combat
code. `Loadout.of` folds worn gear and set stones into the same `Modifiers`
that relics, village upgrades and character perks already write to.

| effect | what it does | was it already implemented |
|---|---|---|
| `max_hp_add` | maximum health | yes |
| `armour_add` | flat soak before percentages | **no - see below** |
| `damage_mult` | main hand and off hand | yes |
| `crit_chance_add` | crit roll | yes |
| `crit_damage_mult` | crit multiplier | yes |
| `throw_damage_mult` | off hand only | **no - see below** |
| `attack_speed_mult` | windup and recover | yes |
| `move_speed_mult` | walking | yes |
| `gold_mult` | coin picked up | yes |

Two of those had no source at all before gear existed:

* **`armour_add`.** `Combatant.armour()` and `Player.armour` have been in the
  code since combat was written and nothing ever wrote to them, so armour was
  permanently zero and `Damage.incoming` subtracted nothing on every hit in the
  game. `Modifiers.armourAdd` is what fills it. `Combatant.resist()` was the
  same story one step further along - `HitResolver` passed `Damage.incoming` a
  literal `0f` for it - and is now a real default method, still zero, ready for
  whatever wants a percentage rather than a flat soak.
* **`throw_damage_mult`.** The off hand used to share `outgoingMult` with the
  main hand, so `melee_damage_mult` - a name that says melee twice - was
  sharpening thrown kunai. `Modifiers.throwMult` is the off hand's own.

### Stone colours

A stone carries exactly one effect and its colour says which family, which is a
rule the player learns once. `ContentValidator.GEM_COLOURS` holds it and fails
the build when content breaks it, because a red stone that made you faster
would teach the player that the colours are decoration.

| colour | effects |
|---|---|
| RED | `damage_mult`, `crit_chance_add` |
| GREEN | `max_hp_add`, `armour_add` |
| BLUE | `attack_speed_mult`, `move_speed_mult` |
| YELLOW | `crit_damage_mult`, `throw_damage_mult` |
| PURPLE | `gold_mult` |

## 3c. Quest step kinds

`QuestDef.Step.kind` names these, and every one of them is a place the game
already counts something. Nothing a quest asks for needs new simulation code;
what was added is four one-line calls into `Quests.record`.

| kind | target | counted where |
|---|---|---|
| `KILL` | an enemy id | `EntityWorld.countDeaths`, beside the bestiary |
| `COLLECT` | an item id | `EntityWorld.collect`, on every pickup that has an id |
| `REACH` | a floor number, as text | `DungeonScreen.enterFloor` |
| `TALK` | a villager id | `HubScreen.talk` |

`REACH` compares as a number rather than for equality, so arriving on floor 5
satisfies a step asking for floor 3 - a player who went deeper has plainly been
there, and sending them back up would be asking for nothing.

Counted into the profile as it happens rather than banked at the end of the
run, which is the opposite of how the bestiary works and is deliberate: the
book is a record of the run and a quest is a job. A player who kills nine of
ten and dies has still killed nine.

`Quests` is pure - a registry, a profile and some numbers, like `Progression`
and `LootRoller`. The village and the dungeon call in with "this happened" and
neither of them is named anywhere in it.

## 3d. Skill effects, and what a skill is made of

`SkillDef.effects` names these and `ContentValidator.SKILL_EFFECTS` is the
list. An `AVATAR` or a `CHARGE` carries them: both are timed states, and these
are what they change while they are up. A short list on purpose - a state that
lasts eight seconds has to be legible in one line, and eight numbers moving at
once is not.

| effect | what it does | was it already implemented |
|---|---|---|
| `damage_mult` | main hand and off hand | yes |
| `crit_chance_add` | crit roll | yes |
| `crit_damage_mult` | crit multiplier | yes |
| `throw_damage_mult` | off hand only | yes |
| `attack_speed_mult` | windup and recover | yes |
| `move_speed_mult` | walking | yes |
| `armour_mult` | share of armour kept | **no - see below** |
| `damage_taken_mult` | share of an incoming blow that lands | yes, from the relics |
| `burn_aura` | damage a second to enemies within 24px | yes, from the relics |
| `burn_on_hit` | share of the target's maximum health, a second, for 8s | **no - see below** |
| `throw_reach_mult` | how far the off hand's weapon flies | **no - see below** |
| `venom_on_hit` | share of maximum health, a second, **per stack** | **no - see below** |
| `heal_on_hurt` | health recovered each time a blow lands on you | **no - see below** |

`armour_mult` is new to the game and is the only reducing effect there is.
Everything else in the content makes a number go up; an ultimate is a trade,
so it needed a name for the other direction. It folds into `Modifiers.armourAdd`
rather than being read on its own, so nothing outside `Modifiers` has to know
armour has two halves, and it compounds like every other `_mult`.

`burn_aura` is not new at all - it is the relic effect, borrowed whole, and
that is what these being names rather than code buys. The aura was written,
tested and already ticking once a second in `EntityWorld.stepBurnAura`; an
ultimate that burns whatever stands near it needed a magnitude, not a
mechanism.

`burn_on_hit` and `throw_reach_mult` are the first two skill effects that
point at something other than the caster. `burn_on_hit` is a **share of the
target's maximum health**, not a number: the thing it has to say is "this is on
fire", and that has to read the same on a 25 point goblin and a 900 point boss,
which a flat number cannot do. `Modifiers.BURN_TICK_CAP` is the other half of
that bargain, because a share of a boss's health bar is otherwise the best
damage in the game. It does not stack - a second application refreshes the
timer, like every other status on `Enemy`.

`venom_on_hit` is priced the same way and **is the exception to that last
sentence**: it is the first status in the game that counts. Three stacks
detonate for `Modifiers.VENOM_BURST_SHARE` of maximum health and reset to
nothing, and `VENOM_TICK_CAP` and `VENOM_BURST_CAP` are its two halves of the
same bargain the burn strikes.

The exception is worth it for one reason and only under one condition. The
reason is that a burn is reapplied by every blow while an ultimate is up, so a
counting burn would be a multiplication table by the third swing - while venom
belongs to a character rather than to a skill and is *meant* to be built. The
condition is that filling the meter empties it: without that, a fast weapon
would run the count away, and with it the third blow is the one worth landing.
The counting lives on `Enemy.venom`, which returns whether the meter filled
rather than acting on it, because acting on it means drawing and `Enemy` draws
nothing; `EntityWorld.envenom` is the other half.

`heal_on_hurt` is the only effect in the game that pays out for being hit, and
it is flat rather than a share of the blow on purpose - a share would pay most
against whatever hurts most, which is a ward that rewards standing in the worst
place on the floor. `EntityWorld.stepWard` spends it three ways at once: it
heals, it draws, and it hurts whatever is close enough to have been the reason.
That last part poisons nothing by itself. It does not need to - the bite is the
player's own damage through `onSkillHitLanded`, so a character whose every blow
carries venom poisons with it for free, which is the whole argument for the
venom being a modifier rather than a branch.

The rest of a skill is not an effect name. `SkillDef.Kind` is the one part
written in code, because it is a shape rather than a number:

| kind | what the player sees | where it lives |
|---|---|---|
| `LUNGE` | a thrust along the aim, through whatever is crossed | `Player.beginLunge` |
| `NOVA` | a ring around the caster that hurts and shoves | `EntityWorld.castNova` |
| `AVATAR` | a timed transformation that also changes the dash | `Player.beginAvatar` |
| `CHARGE` | a long, steered run that burns what it touches and cannot swing | `Player.beginCharge` |
| `PASSIVE` | no key and no cast: effects a character simply has | nowhere - `Loadout` |

`CHARGE` is the fourth and it had to be a shape rather than a lunge with longer
numbers: a lunge fixes its heading at the moment it starts and is over in a
fifth of a second, while a charge is steered for three seconds and takes the
weapons out of the player's hands while it runs. That last part is the whole
cost of the skill, and there was nowhere in a `LUNGE` to put it.

`PASSIVE` is the fifth and it passes the same test differently: it is not a
shape, it is the absence of one. A character whose element is "everything I do
poisons" has something that is never cast, never on cooldown and never on a
key. It sits on slot 0, which `Player.setSkills` drops because it indexes
`slot - 1`, so it cannot reach the bar without a line of code being written to
let it; `ContentRegistry.passiveFor` is the only reader, and `Loadout` folds it
in beside the ultimate. The two alternatives were both worse. A second `effect`
field on the shop row would have cost that character the perk they paid 900
gold for, and a branch per character is the thing section 3 records going wrong
the last time.

Everything else - cooldown, duration, damage, range, knockback, `strikeMult`
(the share of a blow that an avatar's own strike adds on top of it), the health
it costs and the health below which it is free - is a number in
`assets/data/skills.json`, in seconds and virtual pixels. Balance is argued
about for weeks and a rebuild per argument is how tuning stops happening.

`SkillDef.vfx` and `SkillDef.icon` name strips that `tools/make_skillfx.py`
and `tools/make_firefx.py` write; `SKILL_VFX` and `SKILL_ICONS` are the lists,
and a misspelt one fails at boot rather than drawing nothing.

`SkillDef.Fx` is five more of those names, and they are optional. They are what
a timed skill makes the *rest of the game* look like while it is up, which is
the part an element cannot express as a number:

| json field | what it replaces |
|---|---|
| `castVfx` | the ultimate's opening flash, and it shoves as well as draws |
| `meleeVfx` | an arc in front of the swing; the ordinary swing has no sprite at all |
| `hitVfx` | what lands on whatever the player's damage touches |
| `throwVfx` | the off hand's picture, not the off hand's weapon |
| `burnVfx` | the mark a burning enemy carries |
| `dashVfx` | the trail a dash leaves - **and whether a dash is a weapon** |

A skill that names none of them carries `Fx.NONE` and changes nothing, which is
why the lightning set needed no edit when the fire set arrived. The alternative
was an `element` field switched on in six places, which is the same magic
string with more of it.

`dashVfx` is the one that gates a mechanic rather than replacing a picture, and
it does because the mechanic and the picture were one decision. While an
ultimate was up a dash left a lightning streak and arced to three enemies - for
every ultimate, from a hard-coded strip - so the fire ultimate, which is meant
to be a little speed and nothing else, lit up in the wrong element's colours
and carried a whole extra weapon nobody had balanced. "What does a dash look
like now" and "is a dash a weapon now" have one answer, so they are one field,
and an ultimate that is only speed says so by leaving it out.

Six is as many names as this shape should hold. A seventh should turn `Fx`
into a map keyed by role with a vocabulary in `ContentValidator`, the way
effect names already work: six nullable fields each read in one place is
legible, and a dozen is a lookup table written out by hand.

Two things venom draws are **not** here and are constants in `EntityWorld`
instead - the detonation and the default stack mark. There is one poisoner, and
a detonation looks like a detonation whoever caused it; the mark is overridable
because a passive names its own through `vfx`. The day a second element stacks
something, the burst should follow the mark out of the constants.

## 3e. Skill sets, and who gets which

`SkillDef.character` is null for the set every character gets and a character
id for a set one character gets. `ContentRegistry.skillsFor` is the only place
it is read: a character's own three if it has any, the shared three otherwise.

It is all or nothing on purpose, and `ContentValidator.skillSets` enforces it.
A character given one skill of its own would silently lose the other two keys,
because `skillsFor` would return a set of one - and the one-skill-per-slot
check could not see it either, since it looks at one set at a time.

A `PASSIVE` row does not count towards the three. It is optional, at most one,
and `skillSets` counts the keyed rows separately so that a set of three keys
plus a passive is whole rather than one too many. `ContentRegistry.passiveFor`
reads it out of the same set `skillsFor` returns rather than out of a second
table, so a passive belongs to a character exactly the way the three keys do
and arrives and leaves with them.

Two skills may share a slot as long as no character can reach both. That is why
the slot check keys on the character rather than on the slot alone.

This is the second time a rule has given one character something nobody else
has. The first was a pair of spells only Kitsune could hold; it was written into
four places, was right in two, and in the end both the spells and the character
went rather than the rule being fixed. One field, one reader.

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
- **Biomes are `ruins`, `ruins_green`, `ruins_orange`, `depths`, `cove`,
  `cove_deep`** - the folder names procgen writes under `assets/maps/rooms/`.
  Floors 1/2/3 take one ruins colourway each, 4/5 share `depths`, 6 has `cove`
  and 7 has `cove_deep`. The last two are the same tile pack in two folders:
  a biome folder is a room *size* and a room *plan* as much as it is a set of
  tiles, and stage 7's rooms are 30x17. `ContentValidator.BIOMES` holds the
  six names; a seventh biome folder needs adding there too.
- **Floors 6 and 7 are side stages.** `FloorDef.side` means the world map opens it
  from the first run rather than one past the last stage cleared, and that
  clearing it gives the stage-clear screen rather than the ending. The win
  still belongs to the deepest floor with `side: false`.
- **Two room sizes are not 20x11: `cove/boss_01` is 40x22, and every room of
  `cove_deep` is 30x17.** `RoomTemplate.width` and `height` are per template;
  the constants are the default a .tmx gets for saying nothing. Read the
  template, never the constants, for anything that touches a wall or a door.
  A room that is not one screen fades rather than slides into its neighbours
  (`DungeonScreen.startSlide`), and one of two whole screens or more opens on
  a wide shot of itself (`WIDE_SHOT`) - the 30x17 rooms scroll instead, which
  is the point of them.
- **`FloorDef.hpScale` and `damageScale` exist now**, which is item 1 of the
  list above. Floor 7 fights floor 6's three slimes at 1.5x hit points and
  1.25x damage rather than through three more defs on the same three sprites.
  Applied in `EntityWorld.buildEnemy`, never baked into the `EnemyDef`, which
  is shared by every floor that names it.
- **The boss brains are `boss_frog`, `boss_tengu`, `boss_pirateleader`,
  `boss_piratezombie`, `boss_squidman`, `boss_squidlord` and `boss_orb` /
  `boss_orb_tide`.** `boss_squidlord` is stage 7's, and the only one that
  fights in two elements: it shoots what `EnemyDef.projectile` names and rains
  what the brain's own `rains()` names, so water comes at you level and fire
  comes down.
- **Floors 3, 5, 6 and 7 are the only floors with a boss.** `FloorDef.boss` is
  null on 1, 2 and 4, and `hasBoss()` already answers that.
- **`assets/data/icons.json` is a name-to-index map, not a def.** It is loaded
  into `ContentRegistry`'s item and relic icons at parse time - `icons.json`
  entries are referenced from the other files by name (`"icon": "potion_red"`)
  so that a shuffle of the Raven grid is one file to edit. A raw integer is
  also accepted anywhere an icon name is.
