package com.kagebi.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.kagebi.data.def.EnemyDef;
import com.kagebi.entity.Boss;
import com.kagebi.entity.Enemy;
import com.kagebi.entity.TestDefs;

/**
 * The one state machine, run hard against every brain.
 *
 * <p>Each brain gets four thousand steps - over a minute of game time - with
 * the player sweeping back and forth through its range and a hit landing every
 * few seconds from whatever state it happens to be in. What is asserted is
 * structural: no transient state outlives the number the def gave it, every
 * brain actually attacks rather than circling forever, a hitbox only ever
 * appears in ATTACK, and every windup is visible.
 */
class AiStateMachineTest {

    private static final int STEPS = 4000;
    private static final int HIT_EVERY = 250;

    /** Every brain id the content agent's enemies.json names, by grep, plus our own two. */
    private static final String[] CONTENT_BRAINS = {
        "ambusher", "boss_frog", "boss_tengu", "caster", "charger", "chaser",
        "flyer", "hopper", "orbiter", "shooter", "splitter", "wanderer",
    };

    /** A def with the content agent's numbers for the enemy that uses each brain. */
    static EnemyDef defFor(String brain) {
        switch (brain) {
            case "chaser": return TestDefs.larva();
            case "hopper": return TestDefs.slime();
            case "charger": return TestDefs.kappared();
            case "shooter": return TestDefs.octopus();
            case "splitter": return TestDefs.mushroom();
            case "boss_tengu": return TestDefs.tengured();
            case "wanderer":
                return TestDefs.enemy("mouse").brain("wanderer").hp(9).contact(3).speed(52)
                    .aggro(100).timing(6, 6, 10, 20).invuln(8).build();
            case "flyer":
                return TestDefs.enemy("bluebat").brain("flyer").hp(8).contact(4).speed(62)
                    .aggro(120).timing(4, 6, 8, 24).invuln(8).build();
            case "orbiter":
                return TestDefs.enemy("kappagreen").brain("orbiter").hp(28).contact(5)
                    .attack(9, 26).speed(34).aggro(130).timing(18, 6, 18, 40).resist(0.25f)
                    .invuln(12).build();
            case "ambusher":
                return TestDefs.enemy("mollusc").brain("ambusher").hp(30).contact(6).speed(18)
                    .aggro(40).timing(12, 10, 26, 40).resist(0.6f).invuln(14).build();
            case "caster":
                return TestDefs.enemy("mushroom2").brain("caster").hp(30).contact(6)
                    .attack(10, 80).speed(26).aggro(140).timing(34, 8, 24, 80).resist(0.2f)
                    .invuln(12).build();
            case "stationary":
                return TestDefs.enemy("statue").brain("stationary").hp(40).contact(0)
                    .attack(8, 20).aggro(60).timing(14, 6, 14, 30).build();
            case "boss_frog":
                return TestDefs.enemy("giantfrog2").brain("boss_frog").hp(900).contact(12)
                    .attack(22, 72).speed(46).aggro(999).timing(30, 18, 36, 60).resist(1f)
                    .invuln(6).boss(1, "bosses/giantfrog2/idle", 40).build();
            case "burster":
                return TestDefs.enemy("slimetide").brain("burster").hp(36).contact(4)
                    .attack(7, 84).speed(34).aggro(150).timing(26, 4, 22, 70)
                    .invuln(10).build();
            case "bomber":
                return TestDefs.enemy("slimeember").brain("bomber").hp(26).contact(3)
                    .attack(16, 18).speed(62).aggro(160).timing(34, 6, 10, 40)
                    .invuln(8).build();
            // The three bodies of stage 6, with their real active windows: the
            // combo and the barrage both lay their blows out across one, so a
            // shorter one here would test a move that does not exist.
            case "boss_pirateleader":
                return TestDefs.enemy("pirateleader").brain("boss_pirateleader").hp(200)
                    .contact(12).attack(24, 26).speed(58).aggro(999)
                    .timing(28, 190, 34, 54).resist(1f).invuln(5)
                    .boss(1, "bosses6/pirateleader/idle", 64).build();
            case "boss_piratezombie":
                return TestDefs.enemy("piratezombie").brain("boss_piratezombie").hp(180)
                    .contact(10).attack(20, 30).speed(52).aggro(999)
                    .timing(26, 96, 32, 50).resist(1f).invuln(5)
                    .boss(1, "bosses6/piratezombie/idle", 64).build();
            case "boss_squidman":
                return TestDefs.enemy("squidman").brain("boss_squidman").hp(180)
                    .contact(12).attack(22, 28).speed(56).aggro(999)
                    .timing(26, 96, 30, 46).resist(1f).invuln(5)
                    .boss(1, "bosses6/squidman/idle", 64).build();
            default:
                return TestDefs.enemy("generic").brain(brain).hp(600).contact(10)
                    .attack(16, 40).speed(40).aggro(999).timing(24, 12, 24, 40).resist(1f)
                    .invuln(6).boss(2, "bosses/giantslime/idle", 32).build();
        }
    }

    static Enemy spawn(EnemyDef def, float x, float y) {
        AiBrain brain = AiBrains.create(def.brain);
        Enemy e = def.boss ? new Boss(def, brain, null, null) : new Enemy(def, brain, null);
        e.x = x;
        e.y = y;
        brain.onSpawn(e);
        return e;
    }

    /** Sweeps through the middle of the room so every range is crossed often. */
    static void movePlayer(FakeArena arena, int t) {
        arena.px = 160f + 70f * (float) Math.sin(t / 80.0);
        arena.py = 88f + 22f * (float) Math.sin(t / 53.0);
    }

    // ---- the registry ---------------------------------------------------------

    @Test
    void everyBrainTheContentNamesExists() {
        for (String id : CONTENT_BRAINS) {
            assertTrue(AiBrains.knows(id), "enemies.json names '" + id + "'");
            assertEquals(id, AiBrains.create(id).id());
        }
    }

    @Test
    void anUnknownBrainThrowsForTheLoaderButFallsBackForTheSpawner() {
        IllegalArgumentException ex =
            assertThrows(IllegalArgumentException.class, () -> AiBrains.create("teleporter"));
        assertTrue(ex.getMessage().contains("teleporter"), "the bad id is in the message");
        assertEquals(AiBrains.FALLBACK, AiBrains.createOrFallback("teleporter").id());
        assertEquals(AiBrains.FALLBACK, AiBrains.createOrFallback(null).id());
    }

    // ---- structure, for every brain ---------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
        "chaser", "wanderer", "shooter", "charger", "stationary", "hopper", "flyer",
        "orbiter", "ambusher", "splitter", "caster", "boss", "boss_frog", "boss_tengu",
        "burster", "bomber",
        "boss_pirateleader", "boss_piratezombie", "boss_squidman",
    })
    void noStateOutlivesItsDefAndEveryBrainFights(String id) {
        EnemyDef def = defFor(id);
        FakeArena arena = new FakeArena();
        Enemy e = spawn(def, 160f, 88f);
        // Enough health to soak the periodic hits without dying or transforming.
        e.maxHp = 100000;
        e.hp = 100000;

        Map<AiState, Integer> longest = new EnumMap<>(AiState.class);
        int attacks = 0;
        int windupSteps = 0;
        int windupFlashing = 0;
        AiState previous = e.state();
        for (int t = 0; t < STEPS; t++) {
            movePlayer(arena, t);
            if (t % HIT_EVERY == HIT_EVERY - 1) {
                e.takeHit(1, arena.px, arena.py, 40f);
            }
            arena.step(e);
            AiState s = e.state();
            longest.merge(s, e.stateSteps(), Math::max);
            if (s == AiState.ATTACK && previous != AiState.ATTACK) {
                attacks++;
            }
            if (s == AiState.WINDUP) {
                windupSteps++;
                if (e.flashSteps > 0) {
                    windupFlashing++;
                }
            }
            previous = s;
        }

        assertTrue(longest.getOrDefault(AiState.WINDUP, 0) <= def.windupSteps,
            id + ": windup outlived " + def.windupSteps + ": " + longest);
        // Against what the brain declares, not against the def's one number.
        // A boss sizes each move for itself - a swing and a three-blow combo
        // cannot share a length - and says so in longestActiveSteps; every
        // other brain returns def.activeSteps, so this is unchanged for them.
        int ceiling = AiBrains.createOrFallback(def.brain).longestActiveSteps(def);
        assertTrue(longest.getOrDefault(AiState.ATTACK, 0) <= ceiling,
            id + ": attack outlived the " + ceiling + " its brain declares: " + longest);
        assertTrue(longest.getOrDefault(AiState.RECOVER, 0) <= def.recoverSteps,
            id + ": recovery outlived " + def.recoverSteps + ": " + longest);
        assertTrue(longest.getOrDefault(AiState.HURT, 0) <= 12,
            id + ": stagger outlived its 12-step cap: " + longest);

        assertTrue(attacks >= 3, id + " attacked only " + attacks + " times in "
            + STEPS + " steps - wedged in " + longest);
        for (AiState s : arena.strikeStates) {
            assertEquals(AiState.ATTACK, s, id + ": a hitbox appeared outside ATTACK");
        }
        assertTrue(windupFlashing * 3 >= windupSteps,
            id + ": windup visible on only " + windupFlashing + " of " + windupSteps + " steps");
    }

    @ParameterizedTest
    @ValueSource(strings = {"chaser", "hopper", "charger", "caster", "ambusher", "boss_tengu"})
    void deadIsAbsorbing(String id) {
        FakeArena arena = new FakeArena();
        Enemy e = spawn(defFor(id), 160f, 88f);
        for (int t = 0; t < 120; t++) {
            movePlayer(arena, t);
            arena.step(e);
        }
        e.takeHit(1000000, arena.px, arena.py, 0f);
        assertEquals(AiState.DEAD, e.state());
        float x = e.x;
        float y = e.y;
        int strikes = arena.strikeStates.size();
        for (int t = 0; t < 300; t++) {
            arena.step(e);
            e.setState(AiState.CHASE);          // nothing may resurrect it
            e.takeHit(5, arena.px, arena.py, 90f);
        }
        assertEquals(AiState.DEAD, e.state());
        assertEquals(x, e.x, 0f, "a corpse does not move");
        assertEquals(y, e.y, 0f);
        assertEquals(strikes, arena.strikeStates.size(), "a corpse does not strike");
        assertFalse(e.brain().harmfulOnContact(e), "a corpse does not hurt to touch");
        assertTrue(e.removed, "and it is eventually dropped from the room");
    }

    @Test
    void theWedgeBackstopReturnsAnythingStuckToIdle() {
        // A def whose windup is three times the backstop: the backstop wins.
        EnemyDef silly = TestDefs.enemy("silly").brain("chaser").attack(5, 20)
            .timing(BaseBrain.WEDGE_LIMIT * 3, 4, 4, 4).build();
        FakeArena arena = new FakeArena();
        Enemy e = spawn(silly, 160f, 88f);
        arena.px = 170f;
        int t = 0;
        while (e.state() != AiState.WINDUP && t++ < 100) {
            arena.step(e);
        }
        assertEquals(AiState.WINDUP, e.state());
        int inWindup = 0;
        while (e.state() == AiState.WINDUP && inWindup < BaseBrain.WEDGE_LIMIT * 3) {
            arena.step(e);
            inWindup++;
        }
        assertEquals(AiState.IDLE, e.state(), "rescued rather than left standing forever");
        assertEquals(BaseBrain.WEDGE_LIMIT, inWindup, "and rescued at the limit, not before");
        assertFalse(e.attack().busy());
    }

    /**
     * The orb is left out of the sweep above on purpose, and checked here.
     *
     * <p>It has no windup, no active window and no hitbox: it is an
     * intermission, and the sweep asserts that a brain attacks at least three
     * times in four thousand steps, which this one never does. What it
     * promises instead is exactly this - untouchable the whole way, raining,
     * and then over on time and not a step later, because a fight that waited
     * for an orb that never expired would never end at all.
     */
    @Test
    void theOrbIsUntouchableRainsAndEndsOnTime() {
        EnemyDef def = TestDefs.enemy("fireorb").brain("boss_orb").hp(1).contact(0)
            .attack(14, 1).speed(0).aggro(999).timing(1, 1, 1, 1).resist(1f)
            .boss(1, "bosses6/fireorb/idle", 32).build();
        FakeArena arena = new FakeArena();
        Enemy orb = spawn(def, 160f, 88f);

        for (int t = 0; t < OrbBrain.LIFE_STEPS - 1; t++) {
            arena.step(orb);
            assertTrue(orb.invulnerable(), "touchable on step " + t);
            assertTrue(orb.alive(), "died early on step " + t);
        }
        assertTrue(arena.hazards.size() >= 8,
            "only " + arena.hazards.size() + " spells fell in eight seconds");
        assertEquals(List.of(), arena.strikeStates, "an orb never swings at anything");

        // One more step past its life, and it should be gone.
        arena.step(orb);
        arena.step(orb);
        assertFalse(orb.alive(), "the orb outlived its eight seconds");
    }

    /**
     * A barrage chases; a volley does not.
     *
     * <p>The difference is the whole of the move. A line of arrows down one
     * fixed heading is answered once, by stepping aside, and then ignored -
     * which is what was reported as it looking stiff. A volley is a fan and
     * has to stay straight or the three arrows converge into one.
     */
    @Test
    void aBarrageHomesAndAVolleyFliesStraight() {
        EnemyDef def = TestDefs.enemy("piratezombie").brain("boss_piratezombie")
            .hp(180).contact(10).attack(20, 30).speed(52).aggro(999)
            .timing(26, 14, 32, 50).resist(1f)
            .boss(1, "bosses6/piratezombie/idle", 64).build();
        FakeArena arena = new FakeArena();
        Enemy boss = spawn(def, 160f, 88f);

        boolean sawBarrage = false;
        boolean sawVolley = false;
        for (int t = 0; t < 60 * 60 && !(sawBarrage && sawVolley); t++) {
            int homedBefore = arena.homing;
            int shotsBefore = arena.shots;
            movePlayer(arena, t);
            arena.step(boss);
            if (arena.homing > homedBefore) {
                sawBarrage = true;
            } else if (arena.shots > shotsBefore) {
                sawVolley = true;
            }
        }
        assertTrue(sawBarrage, "the zombie never fired a homing barrage");
        assertTrue(sawVolley, "the zombie never fired a straight volley");
    }

    /**
     * The two intermissions ask opposite questions, and do it differently.
     *
     * <p>Worth pinning because the difference is invisible in a screenshot and
     * easy to lose: both are a ball hanging in the middle of a room throwing
     * water or fire about. One aims at the player, so the answer is to keep
     * moving; the other throws the same ring outward whatever the player does,
     * so the answer is to stand still somewhere it has been. If the second
     * ever quietly became the first, the fight would still work and would stop
     * being two ideas.
     */
    @Test
    void theFireOrbAimsAndTheWaterOrbDoesNot() {
        FakeArena rain = new FakeArena();
        Enemy fire = spawn(orbDef("boss_orb"), 160f, 88f);
        for (int t = 0; t < OrbBrain.DROP_INTERVAL * 4; t++) {
            rain.step(fire);
        }
        assertTrue(rain.hazards.size() >= 3, "the fire orb dropped nothing");
        assertEquals(0, rain.lobs, "the fire orb should drop, not throw");
        // Out of the sky, not out of the floor. placeHazard puts burning
        // ground down where it is asked and blinks it as a warning first,
        // which is right for a wall of fire laid along a facing and wrong for
        // rain: on screen it read as something climbing up out of the stone.
        // rainSpell drops it from a height and the fall is the warning.
        assertEquals(rain.hazards.size(), rain.rained,
            "every spell of a rain should fall, not be placed");

        FakeArena tide = new FakeArena();
        Enemy water = spawn(orbDef("boss_orb_tide"), 160f, 88f);
        for (int t = 0; t < OrbBrain.DROP_INTERVAL * 4; t++) {
            tide.step(water);
        }
        assertTrue(tide.lobs >= OrbBrain.FOUNTAIN_ARMS * 3,
            "the water orb threw only " + tide.lobs + " arcs");

        // Aimed where the player is, against thrown around itself.
        for (float[] at : rain.hazards) {
            assertTrue(Math.hypot(at[0] - rain.px, at[1] - rain.py) <= OrbBrain.DROP_RADIUS + 1,
                "a fire spell fell nowhere near the player");
        }
        for (float[] at : tide.hazards) {
            assertTrue(Math.hypot(at[0] - water.x, at[1] - water.y) <= OrbBrain.FOUNTAIN_MAX + 1,
                "a water arc landed outside its own fountain");
        }
    }

    private static EnemyDef orbDef(String brain) {
        return TestDefs.enemy("orb").brain(brain).hp(1).contact(0)
            .attack(14, 1).aggro(999).timing(1, 1, 1, 1).resist(1f)
            .boss(1, "bosses6/fireorb/idle", 32).build();
    }

    @Test
    void trashFlinchesAndBossesDoNot() {
        Enemy larva = spawn(TestDefs.larva(), 160f, 88f);
        larva.setState(AiState.WINDUP);
        larva.takeHit(1, 150f, 88f, 40f);
        assertEquals(AiState.HURT, larva.state(), "a hit interrupts a trash windup");

        Enemy boss = spawn(TestDefs.tengured(), 160f, 88f);
        boss.setState(AiState.WINDUP);
        boss.takeHit(1, 150f, 88f, 40f);
        assertEquals(AiState.WINDUP, boss.state(), "a boss commits through a hit");
    }
}
