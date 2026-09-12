package com.kagebi.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.kagebi.Dir;
import com.kagebi.ai.AiState;
import com.kagebi.combat.Faction;
import com.kagebi.combat.HitResolver;
import com.kagebi.combat.Hitbox;
import com.kagebi.data.ContentRegistry;
import com.kagebi.data.ShopCatalog;
import com.kagebi.data.def.FloorDef;
import com.kagebi.gen.Room;
import com.kagebi.gen.RoomKind;
import com.kagebi.gen.SpawnPoint;
import com.kagebi.input.GameAction;
import com.kagebi.run.RunState;
import com.kagebi.save.Profile;
import com.kagebi.settings.Difficulty;

/**
 * A whole room, headless: what spawns, what survives bad content, what the run
 * is told, and when the doors may open.
 */
class EntityWorldTest {

    private static final SpawnPoint.Kind ENEMY = SpawnPoint.Kind.ENEMY;

    private static ContentRegistry registry() {
        ContentRegistry r = new ContentRegistry();
        r.put(TestDefs.slime());
        r.put(TestDefs.larva());
        r.put(TestDefs.kappared());
        r.put(TestDefs.octopus());
        r.put(TestDefs.mushroom());
        r.put(TestDefs.tengured());
        return r;
    }

    private static EntityWorld world(ContentRegistry content, RunState run) {
        return new EntityWorld(null, content, run, null);
    }

    private static void killAll(EntityWorld w) {
        for (Enemy e : w.hostiles()) {
            if (e.alive()) {
                e.takeHit(100000, e.x, e.y, 0f);
            }
        }
    }

    // ---- spawning -------------------------------------------------------------

    @Test
    void spawnsWhatTheTemplateNames() {
        EntityWorld w = world(registry(), TestDefs.run());
        Room room = TestDefs.room(RoomKind.NORMAL,
            TestDefs.at(ENEMY, 60, 60, "slime"),
            TestDefs.at(ENEMY, 260, 60, "larva"));
        w.enterRoom(room, TestDefs.walled(), null);
        assertEquals(2, w.hostilesAlive());
        assertEquals("slime", w.hostiles().get(0).def.id);
        assertEquals(60f, w.hostiles().get(0).x, 0f);
        assertEquals("hopper", w.hostiles().get(0).brain().id());
        assertFalse(room.cleared);
        assertFalse(w.roomCleared());
        assertTrue(room.visited);
    }

    @Test
    void aClearedRoomStaysEmptyOnReturn() {
        EntityWorld w = world(registry(), TestDefs.run());
        Room room = TestDefs.room(RoomKind.NORMAL, TestDefs.at(ENEMY, 60, 60, "slime"));
        room.cleared = true;
        w.enterRoom(room, TestDefs.walled(), Dir.LEFT);
        assertEquals(0, w.hostilesAlive());
        assertTrue(w.roomCleared());
    }

    @Test
    void untaggedSpawnsRollFromTheFloorPool() {
        ContentRegistry r = registry();
        r.put(new FloorDef(1, "floor.1.name", "well", null, null, null, 6, 8, 1, 1,
            new String[] {"slime", "not_written_yet"}, new int[] {5, 5}, 3, 5, null));
        EntityWorld w = world(r, TestDefs.run());
        w.enterRoom(TestDefs.room(RoomKind.NORMAL,
            TestDefs.at(ENEMY, 60, 60, null),
            TestDefs.at(ENEMY, 100, 60, null),
            TestDefs.at(ENEMY, 140, 60, null)), TestDefs.walled(), null);
        // The missing id is left out of the pool rather than rolled and dropped.
        assertEquals(3, w.hostilesAlive());
        for (Enemy e : w.hostiles()) {
            assertEquals("slime", e.def.id);
        }
    }

    // ---- surviving content that is not written yet ---------------------------

    @Test
    void anEmptyRegistryStillGivesAPlayableRoom() {
        EntityWorld w = world(new ContentRegistry(), TestDefs.run());
        Room room = TestDefs.room(RoomKind.NORMAL,
            TestDefs.at(ENEMY, 60, 60, "slime"),
            TestDefs.at(ENEMY, 80, 60, null));
        w.enterRoom(room, TestDefs.walled(), null);
        assertEquals(0, w.hostilesAlive(), "nothing resolved, nothing spawned, nothing thrown");
        assertTrue(room.cleared, "and the doors are not held shut by enemies that never came");
        assertEquals("katana", w.player().weapon().id, "the starter weapon stands in");
    }

    @Test
    void anUnknownBrainFallsBackToAChaser() {
        ContentRegistry r = new ContentRegistry();
        r.put(TestDefs.enemy("oddity").brain("teleporter").build());
        EntityWorld w = world(r, TestDefs.run());
        w.enterRoom(TestDefs.room(RoomKind.NORMAL, TestDefs.at(ENEMY, 60, 60, "oddity")),
            TestDefs.walled(), null);
        assertEquals("chaser", w.hostiles().get(0).brain().id());
    }

    @Test
    void theRunsWeaponIsUsedWhenItResolves() {
        ContentRegistry r = new ContentRegistry();
        r.put(TestDefs.hammer());
        RunState run = new RunState(1L, "ninjagreen", "hammer", 100);
        EntityWorld w = world(r, run);
        w.enterRoom(TestDefs.room(RoomKind.START), TestDefs.walled(), null);
        assertEquals("hammer", w.player().weapon().id);
    }

    // ---- what the run is told -------------------------------------------------

    @Test
    void theLastKillClearsTheRoomAndTheGoldReachesTheRun() {
        RunState run = TestDefs.run();
        EntityWorld w = world(registry(), run);
        Room room = TestDefs.room(RoomKind.NORMAL,
            TestDefs.at(ENEMY, 100, 60, "larva"),
            TestDefs.at(ENEMY, 200, 60, "larva"));
        w.enterRoom(room, TestDefs.walled(), null);
        ScriptedInput in = new ScriptedInput();

        w.hostiles().get(0).takeHit(1000, 0f, 0f, 0f);
        in.tick(w);
        assertEquals(1, run.kills);
        assertFalse(room.cleared, "one larva is still alive");

        w.hostiles().get(1).takeHit(1000, 0f, 0f, 0f);
        in.tick(w);
        assertEquals(2, run.kills);
        assertTrue(room.cleared, "cleared on the step the last hostile died");
        assertTrue(w.roomCleared());

        // Stand on the drops: the magnet should do the rest.
        int dropped = 0;
        for (Pickup p : w.pickups()) {
            dropped += p.amount;
        }
        assertTrue(dropped >= 6 && dropped <= 12, "two larvae at 3-6 gold each: " + dropped);
        for (int i = 0; i < 4; i++) {
            Pickup p = w.pickups().get(0);
            w.player().placeAt(p.x, p.y, Dir.DOWN);
            in.ticks(w, Pickup.SETTLE_STEPS + 30);
            if (w.pickups().size == 0) {
                break;
            }
        }
        assertEquals(dropped, run.gold);
    }

    // ---- the off hand ---------------------------------------------------------

    /**
     * The throw key throws the off-hand weapon, and does nothing without one.
     *
     * <p>For most of this project's life {@code GameAction.THROW} was declared,
     * bound to K, translated into both languages and listed on the controls
     * screen as rebindable - and read by no line of code. Throwing itself
     * worked, but only by equipping a kunai INSTEAD of a sword, which meant
     * giving up melee for the run. This is the test that would have caught it.
     */
    @Test
    void theThrowKeyThrowsTheOffHandWeapon() {
        ContentRegistry r = registry();
        r.put(TestDefs.katana());
        r.put(TestDefs.kunai());
        RunState run = TestDefs.run();
        run.throwWeaponId = "kunai";
        EntityWorld w = world(r, run);
        w.enterRoom(TestDefs.room(RoomKind.START), TestDefs.walled(), null);
        assertEquals("katana", w.player().weapon().id, "the sword stays in the main hand");
        assertEquals("kunai", w.player().throwWeapon().id);

        ScriptedInput in = new ScriptedInput();
        in.tap(GameAction.THROW);
        in.ticks(w, 10);
        assertTrue(w.projectiles().size > 0, "pressing throw should let something fly");
    }

    @Test
    void anEmptyOffHandMakesTheThrowKeyDoNothing() {
        ContentRegistry r = registry();
        r.put(TestDefs.katana());
        r.put(TestDefs.kunai());
        EntityWorld w = world(r, TestDefs.run());     // no throwWeaponId
        w.enterRoom(TestDefs.room(RoomKind.START), TestDefs.walled(), null);
        assertNull(w.player().throwWeapon());

        ScriptedInput in = new ScriptedInput();
        in.tap(GameAction.THROW);
        in.ticks(w, 20);
        assertEquals(0, w.projectiles().size);
        assertFalse(w.player().attacking(), "and it does not start a swing either");
    }

    /** A melee id in the off-hand slot is refused rather than thrown. */
    @Test
    void aSwordCannotBePutInTheThrowingSlot() {
        ContentRegistry r = registry();
        r.put(TestDefs.katana());
        r.put(TestDefs.hammer());
        RunState run = TestDefs.run();
        run.throwWeaponId = "hammer";
        EntityWorld w = world(r, run);
        w.enterRoom(TestDefs.room(RoomKind.START), TestDefs.walled(), null);
        assertNull(w.player().throwWeapon(),
            "a hammer in the off hand would fly across the room as a kunai sprite");
    }

    // ---- hit feedback ---------------------------------------------------------

    /**
     * A swing that lands shows the target's health bar, and it times out.
     *
     * <p>The bar is the only cue that answers "how much is left" - the damage
     * numbers cannot, because no player turns a stream of sevens into a sense
     * of an eighteen-hundred-health boss being nearly down.
     */
    @Test
    void aLandedHitRaisesTheHealthBarAndItFadesOnItsOwn() {
        EntityWorld w = world(registry(), TestDefs.run());
        Room room = TestDefs.room(RoomKind.NORMAL, TestDefs.at(ENEMY, 100, 60, "larva"));
        w.enterRoom(room, TestDefs.walled(), null);
        Enemy e = w.hostiles().get(0);
        assertEquals(0f, e.healthBarFade(), "no bar before anything touches it");

        w.popDamage(e, 4, false);
        assertEquals(1f, e.healthBarFade(), "up at once, and at full strength");

        ScriptedInput in = new ScriptedInput();
        in.ticks(w, Enemy.BAR_STEPS / 2);
        assertEquals(1f, e.healthBarFade(), "still solid halfway through");
        in.ticks(w, Enemy.BAR_STEPS);
        assertEquals(0f, e.healthBarFade(), "gone once its time is up");
    }

    /**
     * Poison does not raise the bar, and that is the point of it having its own
     * counter.
     *
     * <p>{@code flashSteps} looks like the same signal and is not: it is set by
     * a poison tick and by the attack telegraph as well as by a hit. A bar
     * riding on it would appear over an enemy nobody has touched and blink on
     * every windup, which is worse than no bar.
     */
    @Test
    void aPoisonTickDoesNotRaiseTheHealthBar() {
        EntityWorld w = world(registry(), TestDefs.run());
        Room room = TestDefs.room(RoomKind.NORMAL, TestDefs.at(ENEMY, 100, 60, "larva"));
        w.enterRoom(room, TestDefs.walled(), null);
        Enemy e = w.hostiles().get(0);
        e.poison(1, com.kagebi.combat.Modifiers.POISON_STEPS);

        int before = e.hp;
        new ScriptedInput().ticks(w, com.kagebi.combat.Modifiers.TICK_STEPS + 2);
        assertTrue(e.hp < before, "the poison should have ticked");
        assertEquals(0f, e.healthBarFade(), "and raised no bar doing it");
    }

    @Test
    void aSplitterSplitsOnceIntoTwoHalves() {
        RunState run = TestDefs.run();
        EntityWorld w = world(registry(), run);
        Room room = TestDefs.room(RoomKind.NORMAL, TestDefs.at(ENEMY, 100, 80, "mushroom"));
        w.enterRoom(room, TestDefs.walled(), null);
        ScriptedInput in = new ScriptedInput();

        killAll(w);
        in.tick(w);
        assertEquals(2, w.hostilesAlive(), "one mushroom became two");
        for (Enemy e : w.hostiles()) {
            if (e.alive()) {
                assertEquals(22, e.maxHp, "half of 44");
                assertEquals(1, e.generation);
            }
        }
        assertFalse(room.cleared, "the children hold the room");
        int goldAfterParent = w.pickups().size;

        killAll(w);
        in.tick(w);
        assertEquals(0, w.hostilesAlive(), "the children do not split again");
        assertTrue(room.cleared);
        assertEquals(3, run.kills);
        assertEquals(goldAfterParent, w.pickups().size, "and they drop no gold of their own");
    }

    @Test
    void theBossArrivesInABossRoomAndTransformsAtHalfHealth() {
        ContentRegistry r = registry();
        r.put(new FloorDef(5, "floor.5.name", "core", null, null, null, 6, 8, 1, 1,
            new String[] {"slime"}, new int[] {1}, 3, 5, "tengured"));
        RunState run = TestDefs.run();
        run.floor = 5;
        EntityWorld w = world(r, run);
        w.enterRoom(TestDefs.room(RoomKind.BOSS), TestDefs.walled(), null);
        assertEquals(1, w.hostilesAlive());
        Boss boss = (Boss) w.hostiles().get(0);
        assertEquals(1, boss.phase());

        ScriptedInput in = new ScriptedInput();
        boss.hp = 899;      // just under half of 1800
        in.tick(w);
        assertTrue(boss.transforming());
        assertTrue(boss.invulnerable(), "no free damage during the transformation");
        assertFalse(boss.brain().harmfulOnContact(boss), "and no free damage the other way");

        in.ticks(w, Boss.DEFAULT_TRANSFORM_STEPS + 1);
        assertFalse(boss.transforming());
        assertEquals(2, boss.phase());
        assertEquals(1f + Boss.PHASE_SPEED_STEP, boss.speedMult, 1e-6f);

        boss.hp = 10;
        in.tick(w);
        assertFalse(boss.transforming(), "the last phase does not transform again");
    }

    // ---- contact damage follows the state machine ------------------------------

    @Test
    void aSlimeInRecoveryIsSafeToTouch() {
        RunState run = TestDefs.run();
        EntityWorld w = world(registry(), run);
        w.enterRoom(TestDefs.room(RoomKind.NORMAL, TestDefs.at(ENEMY, 160, 88, "slime")),
            TestDefs.walled(), null);
        Enemy slime = w.hostiles().get(0);
        w.player().placeAt(slime.x, slime.y, Dir.DOWN);

        slime.setState(AiState.RECOVER);
        new ScriptedInput().tick(w);
        assertEquals(100, run.hp, "enemies.json: half a second in which it cannot hurt you");

        slime.setState(AiState.CHASE);
        new ScriptedInput().tick(w);
        assertEquals(96, run.hp, "outside recovery its 4 contact damage lands");
    }

    // ---- doors ----------------------------------------------------------------

    @Test
    void doorsOnlyReportOnceTheRoomIsCleared() {
        EntityWorld w = world(registry(), TestDefs.run());
        Room room = TestDefs.room(RoomKind.NORMAL, TestDefs.at(ENEMY, 260, 120, "larva"));
        room.link(Dir.LEFT, TestDefs.room(RoomKind.NORMAL));
        w.enterRoom(room, null, null);
        w.player().placeAt(4f, 88f, Dir.LEFT);
        assertNull(w.doorReached(), "no walking out of a fight");
        killAll(w);
        new ScriptedInput().tick(w);
        w.player().placeAt(4f, 88f, Dir.LEFT);
        assertEquals(Dir.LEFT, w.doorReached());
    }

    @Test
    void enteringFromADoorPlacesThePlayerJustInsideIt() {
        EntityWorld w = world(registry(), TestDefs.run());
        w.enterRoom(TestDefs.room(RoomKind.NORMAL), TestDefs.walled(), Dir.LEFT);
        assertEquals(32f, w.playerX(), 0f);
        assertEquals(88f, w.playerY(), 0f);
        assertEquals(Dir.RIGHT, w.playerFacing(), "facing into the room");
    }

    @Test
    void anEntrySpawnTaggedForTheDoorWins() {
        EntityWorld w = world(registry(), TestDefs.run());
        w.enterRoom(TestDefs.room(RoomKind.NORMAL,
            TestDefs.at(SpawnPoint.Kind.ENTRY, 150, 90, null),
            TestDefs.at(SpawnPoint.Kind.ENTRY, 40, 70, "left")), TestDefs.walled(), Dir.LEFT);
        assertEquals(40f, w.playerX(), 0f);
        assertEquals(70f, w.playerY(), 0f);
    }

    // ---- interaction ------------------------------------------------------------

    @Test
    void theStairsAreOfferedOnlyInAClearedRoom() {
        EntityWorld w = world(registry(), TestDefs.run());
        w.enterRoom(TestDefs.room(RoomKind.EXIT,
            TestDefs.at(SpawnPoint.Kind.EXIT, 160, 88, null),
            TestDefs.at(ENEMY, 280, 140, "larva")), TestDefs.walled(), null);
        w.player().placeAt(160f, 88f, Dir.DOWN);
        assertNull(w.promptKey());
        killAll(w);
        new ScriptedInput().tick(w);
        assertEquals(EntityWorld.PROMPT_DESCEND, w.promptKey());
        w.interact();
        assertTrue(w.descendRequested());
    }

    // ---- the village ------------------------------------------------------------

    /**
     * A run passes through two worlds before the first fight: the village builds
     * one, and the dungeon builds another over the same {@link RunState}. The
     * bought maximum-health upgrade must be worth the same either way.
     *
     * <p>It was not. Each world took the run's <em>current</em> maximum as the
     * base it added to, so the village raised 100 to 115 and the dungeon then
     * read 115 as the base and raised it to 130. Nobody could see it before the
     * shop screen existed, because until then {@code ShopCatalog.buy} was only
     * ever called from a test.
     */
    @Test
    void aBoughtUpgradeIsWorthTheSameHoweverManyWorldsTheRunPassesThrough() {
        ShopCatalog shop = new ShopCatalog();
        shop.add(new ShopCatalog.Upgrade("vigor", "n", "d", 0, 4,
            new int[] {350, 700, 1400, 2600}, "max_hp_add", 15f));
        Profile profile = new Profile();
        profile.upgrades.put("vigor", 1);

        RunState run = TestDefs.run();
        assertEquals(100, run.maxHp);

        EntityWorld village = world(registry(), run);
        village.useVillage(shop, profile);
        assertEquals(115, run.maxHp, "the village should apply one level of vigor");

        EntityWorld dungeon = world(registry(), run);
        dungeon.useVillage(shop, profile);
        assertEquals(115, run.maxHp, "descending must not apply it a second time");
    }

    // ---- difficulty -------------------------------------------------------------

    /**
     * The same blow, three settings, three numbers - and in the right order.
     *
     * <p>Measured through {@code HitResolver}, which is the path every hit the
     * player takes goes down, rather than by reading the multiplier back.
     */
    @Test
    void oneBlowCostsDifferentAmountsAtTheThreeSettings() {
        int[] lost = new int[Difficulty.values().length];
        for (Difficulty d : Difficulty.values()) {
            RunState run = TestDefs.run();
            run.difficulty = d;
            EntityWorld w = world(registry(), run);
            Player p = w.player();
            int before = run.hp;
            Hitbox box = new Hitbox(p.x - 4, p.y - 4, 8f, 8f, 20, 0f, Faction.ENEMY,
                                    p.x, p.y);
            HitResolver.hit(box, p, null);
            lost[d.ordinal()] = before - run.hp;
        }
        assertTrue(lost[Difficulty.HARD.ordinal()] > lost[Difficulty.NORMAL.ordinal()],
            "hard should hurt more than normal");
        assertTrue(lost[Difficulty.NORMAL.ordinal()] > lost[Difficulty.WEAK.ordinal()],
            "normal should hurt more than the gentle setting");
    }

    /**
     * Enemy health is scaled where the enemy is spawned, because that is the
     * only place that knows which run it belongs to. The def itself must come
     * through untouched, or the second room of the floor would scale twice.
     */
    @Test
    void enemyHealthFollowsTheRunsDifficultyWithoutTouchingTheContent() {
        ContentRegistry content = registry();
        int authored = content.enemy("slime").maxHp;
        for (Difficulty d : Difficulty.values()) {
            RunState run = TestDefs.run();
            run.difficulty = d;
            EntityWorld w = world(content, run);
            w.enterRoom(TestDefs.room(RoomKind.NORMAL,
                TestDefs.at(ENEMY, 60, 60, "slime")), TestDefs.walled(), null);
            Enemy e = w.hostiles().first();
            assertEquals(d.scaleHp(authored, false), e.maxHp(), "max health at " + d);
            assertEquals(e.maxHp(), e.hp(), "a scaled enemy should spawn at full health");
            assertEquals(authored, content.enemy("slime").maxHp,
                "the def itself must not be rewritten");
        }
    }

    /**
     * Poison is the one source of damage that never passes through
     * HitResolver. A difficulty that halved every blow but not the poison
     * would make a poison stack worth twice what a sword blow is.
     */
    @Test
    void poisonIsScaledToo() {
        int[] lost = new int[Difficulty.values().length];
        for (Difficulty d : Difficulty.values()) {
            RunState run = TestDefs.run();
            run.difficulty = d;
            EntityWorld w = world(registry(), run);
            w.enterRoom(TestDefs.room(RoomKind.NORMAL), TestDefs.walled(), null);
            w.player().poison(10, 600);
            int before = run.hp;
            ScriptedInput in = new ScriptedInput();
            for (int t = 0; t < 120; t++) {
                in.tick(w);
            }
            lost[d.ordinal()] = before - run.hp;
        }
        assertTrue(lost[Difficulty.HARD.ordinal()] > lost[Difficulty.WEAK.ordinal()],
            "poison ignored the difficulty: " + java.util.Arrays.toString(lost));
    }

    // ---- replayability ----------------------------------------------------------

    @Test
    void theSameSeedAndInputsReplayIdentically() {
        float[] a = playOut();
        float[] b = playOut();
        for (int i = 0; i < a.length; i++) {
            assertEquals(a[i], b[i], 0f, "value " + i + " diverged");
        }
    }

    private static float[] playOut() {
        RunState run = TestDefs.run();
        EntityWorld w = world(registry(), run);
        w.enterRoom(TestDefs.room(RoomKind.NORMAL,
            TestDefs.at(ENEMY, 60, 60, "slime"),
            TestDefs.at(ENEMY, 260, 130, "kappared"),
            TestDefs.at(ENEMY, 250, 40, "octopus"),
            TestDefs.at(ENEMY, 80, 130, "mushroom")), TestDefs.walled(), null);
        ScriptedInput in = new ScriptedInput();
        for (int t = 0; t < 400; t++) {
            if (t % 50 == 0) {
                in.tap(GameAction.ATTACK);
            }
            if (t % 90 == 0) {
                in.tap(GameAction.ROLL);
            }
            if (t == 100) {
                in.press(GameAction.MOVE_LEFT);
            }
            if (t == 220) {
                in.release(GameAction.MOVE_LEFT);
                in.press(GameAction.MOVE_UP);
            }
            in.tick(w);
        }
        float[] out = new float[3 + w.hostiles().size * 2];
        out[0] = w.playerX();
        out[1] = w.playerY();
        out[2] = run.hp;
        for (int i = 0; i < w.hostiles().size; i++) {
            out[3 + i * 2] = w.hostiles().get(i).x;
            out[4 + i * 2] = w.hostiles().get(i).y;
        }
        return out;
    }
}
