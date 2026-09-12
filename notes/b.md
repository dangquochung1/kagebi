# Agent B (gameplay) - notes for integration

Branch `agent-b`. Everything below is something I could not do from inside
`entity/`, `combat/`, `ai/` and `assets/Assets.java`.

---

## 1. What the dungeon screen must do

**Call `input.beginStep()` before `world.step(input)`, once per fixed step.**
This is the one thing that will silently half-work if it is missed. `InputService`
promotes presses on `beginStep`; without it `justPressed` and the whole buffer
never advance, and attack and roll become unreliable in a way that reads as
"the game eats inputs". The world deliberately does *not* call it, because
calling it twice would be just as wrong. The loop is:

```java
while (accumulator >= Cfg.STEP) {      // capped at Cfg.MAX_STEPS_PER_FRAME
    input.beginStep();
    world.step(input);
    accumulator -= Cfg.STEP;
}
```

**`world.useSharedAtlases(uiAtlas, fxAtlas)` right after constructing it.**
The constructor signature I was given only passes the actors atlas, but
projectiles live in `fx.atlas` and coins and hearts in `ui.atlas`. Rather than
draw nothing, `EntityWorld` loads both itself - which costs a second copy of
each page, about 20MB of texture. One call hands over the ones the game already
has and frees mine. Safe to call at any time; safe never to call.

**Three things are on `EntityWorld` but not on `World`**, because the interface
predates them. Either poll them through the concrete type or add them to the
interface at integration:

- `descendRequested()` - the player used the stairs (set by `interact()`).
- `shopRequested()` - the player talked to a shopkeeper.
- `useSharedAtlases(ui, fx)` - above.

**`doorReached()` returns null until the room is cleared.** The screen owns the
doors; I gate it here as well so nothing can walk out of a fight that is going
badly. If you want the player to be able to retreat, say so and I will drop it.

**`shake()`** is already faded and already respects the screen-shake setting;
add it to the camera position as a random offset of that many virtual pixels.
Hit-stop is internal - the world simply does not advance on frozen steps.

### i18n keys `promptKey()` can return

Please add these three to both `en` and `vi` (they are gameplay strings, so
probably `content.<lang>.json`):

| key | English | when |
|---|---|---|
| `prompt.open_chest` | Open | near an unopened chest, room cleared |
| `prompt.descend` | Descend | near the stairs, room cleared |
| `prompt.shop` | Talk | near a shopkeeper |

---

## 2. Fields I wish the defs had

None of these block anything - each has a constant or a heuristic standing in,
named in the code with this file cited. In rough order of how much they matter:

### `EnemyDef`

| field | standing in as | why it should be data |
|---|---|---|
| `bodyW`, `bodyH`, `footInset` | measured from the atlas page at load for bosses; per-shape constants otherwise | tengured's 82px cell holds a 53x33 figure standing 24px up. A body cut from the cell is hit by swings that visibly pass over its head. I measure the page pixels to avoid guessing, which costs a PNG decode per boss and makes the hurtbox depend on the atlas - data would be better and cheaper |
| `armour` | 0 for every enemy | `Damage.incoming` takes armour and nothing supplies it. Floors 4-5 want heavies that shrug off small hits, and knockback resist is currently the only defensive stat |
| `attackKnockback` | `BaseBrain.ATTACK_KNOCKBACK = 70` | how hard *being hit* shoves the player is per-attack feel. A bonelord and a mouse should not shove alike |
| `lungeDistance` | `BaseBrain.LUNGE_DISTANCE = 18` | half the roster has `attackRange: 0` and attacks with its body. How far that lunge carries is the difference between a larva and a vampire |
| `projectile` | one orb sprite for every shooter | enemies.json names "inkball", "sporecloud" and "flamewave" in comments but there is no field for them, so every shot looks the same |
| `hazardLingerSteps` | `CasterBrain.LINGER_STEPS = 150` | how long a sporecloud stays is a balance lever |
| `phaseThresholds` | equal slices of the health bar | fine for two phases; a three-phase boss probably wants uneven ones |
| `splitCount`, `splitHpFraction` | 2 children at half max hp, one generation | the numbers that stop "a room of six becoming a room of ninety-six" are in my code, not in the file that explains them |

### `WeaponDef`

| field | standing in as | why |
|---|---|---|
| `critChance`, `critMult` | 5% / x2 on `Player` | crit is rolled once per swing (not per target - a hammer that crits the left enemy and not the right one reads as a bug). The numbers belong with the weapon |
| `projectileSpeed` | `EntityWorld.THROW_SPEED = 220` | kunai and shuriken should not fly at the same speed; `reach` is already the range and I derive the lifetime from it |
| `comboSteps` / next-swing window | none | there is no combo system; a queued attack simply starts a fresh identical swing |

`WeaponDef.icon` is an `int` (an index into the Raven grid) but weapons.json
writes strings like `"weapon_katana"`. One of the two has to move; not mine.

---

## 3. Findings in other people's files

Read-only observations, no edits made.

1. **`bluebat` cannot die to one katana swing.** Its comment says "Eight hit
   points because it dies to one katana swing", but the katana does 7. It needs
   7 hp, or the katana 8. `DamageTest` pins the arithmetic.

2. **`wraith` uses a sprite with targeting brackets drawn into it.** In
   `depths/monsters_idle/`, the *monster* `v1` sheets carry red corner brackets
   (#BC4C51) and the *priest* `v2` sheets carry blue ones (#62ABD4); the other
   variant of each pair is clean. wraith points at `skull/v1/skull_v1`, so it
   renders with four red brackets around it - I have a screenshot. Switching it
   to `skull/v2/skull_v2` fixes it. acolyte, zealot and bonelord already use
   clean variants. The bracketed sheets look like the pack's "targeted" state
   and would make a good lock-on indicator if anyone wants one.

3. **The two dragons cannot be drawn as bosses.** 18 of the 20 boss folders
   have an idle strip; `dragonblue` and `dragongreen` ship `head`, `body1`,
   `body2`, `bodyend` and `wing` instead - a segmented snake that needs its own
   entity. `ActorRegionsTest` asserts exactly that set, so if anyone adds one to
   a floor the test will say why it is invisible.

4. **`ContentRegistry` has no `hasFloor(int)`,** and `allFloors()` stops at the
   first missing number, so a floors.json with floor 5 but not floor 4 returns
   nothing and every boss silently vanishes. I catch the exception from
   `floor(n)` instead. A `hasFloor` would be nicer than a caught exception.

5. **`Anim.directional` accepts a 64x16 strip.** It is exactly four cells wide,
   so the column check passes and it reads four animation frames as four
   facings of a one-frame animation - an enemy that appears frozen and turns
   into a different pose when it faces another way. `ActorSprites` measures the
   height to tell them apart. If `gfx/Anim` ever grows a guard, this is the one
   worth adding, plus a `column()` mode for the player's 32x64 `dead` sheet,
   which is neither shape and is sliced by hand in `ActorSprites`.

6. **Player speed is 78 px/s**, matching enemies.json's "the player runs at 78",
   not the placeholder's 60.

---

## 4. Hooks left open

**Loot.** `EntityWorld.dropPickup(kind, amount, itemId, x, y)` is public and is
where a rolled loot table should hand its result back. Today an enemy death
drops `goldMin..goldMax` and a chest drops 3-6 coins; neither rolls a table,
because `loot/` is not mine. `Pickup` already handles GOLD, HEART, KEY and ITEM,
and writes into `RunState`.

**Relics.** `Player` carries `damageMult`, `critChance`, `critMult`, `speedMult`
and `armour`, all at their defaults, and nothing reads `run.relics` yet. I was
told the effect names are still being invented, so I have not guessed at them.
Give me the list and wiring them up is a switch in `EntityWorld.enterRoom`.

**Audio.** `EntityWorld` has no `AudioService` - it is not in the constructor I
was given. The moments that want a sound are: swing start, hit landed, enemy
death, player hurt, roll, pickup collected, boss transformation. Either pass the
service in or let me raise events; either is a small change.

**Weapon overlays.** The five held weapon sheets (`player/weapons/*`, 256x256)
are not drawn. The player's own attack animation reads fine without them, and I
did not want to guess at per-frame hand anchors.

---

## 5. What is not done

- **Projectile art is one orb for everything.** Thrown kunai use the single
  kunai region, rotated to its heading; every enemy shot and every caster cloud
  uses `fx/projectile/energyball`. Fine to look at, not final.
- **Flying enemies still collide with walls.** `EnemyDef.flying` is documented
  as ignoring hazards and pits, and `CollisionGrid` has neither yet, so the flag
  currently changes nothing.
- **No pathfinding, by design.** Chase-and-slide plus a sidestep when a chase
  jams. Rooms are 20x11 and convex; if a room template ever has a concave pocket
  an enemy can be walked around a corner and lost.
- **`SpawnPoint.Kind.PROP` is ignored.** Breakable pots and torches need an
  entity; say the word and it is a small one.
- **Chests have no sprite.** I assumed the room's tiles draw them; only the
  prompt and the drop are mine.
