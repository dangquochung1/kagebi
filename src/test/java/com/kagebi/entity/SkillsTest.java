package com.kagebi.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.utils.Array;
import com.kagebi.Cfg;
import com.kagebi.Dir;
import com.kagebi.data.ContentRegistry;
import com.kagebi.data.def.EnemyDef;
import com.kagebi.data.def.SkillDef;
import com.kagebi.gen.RoomKind;
import com.kagebi.input.GameAction;
import com.kagebi.run.RunState;

/**
 * The three skills, driven through the real world a step at a time.
 *
 * <p>The definitions here are written out rather than loaded from
 * {@code skills.json}, so tuning the game does not break these tests and a
 * failure means the mechanism is wrong rather than the numbers. Whether the
 * shipped file is sensible is {@code ContentValidator}'s business.
 */
class SkillsTest {

    private RunState run;
    private EntityWorld world;
    private ScriptedInput in;
    private Player p;

    private static final int COOLDOWN_STEPS = SkillDef.steps(6f);

    private static SkillDef lunge() {
        return new SkillDef("lunge", "n", "d", "bolt", 1, SkillDef.Kind.LUNGE,
            6f, 0f, 1.2f, 40f, 60f, "bolt", new String[0], new float[0], 0f, 0f);
    }

    private static SkillDef nova() {
        return new SkillDef("nova", "n", "d", "nova", 2, SkillDef.Kind.NOVA,
            6f, 0f, 1f, 36f, 140f, "nova", new String[0], new float[0], 0f, 0f);
    }

    private static SkillDef avatar() {
        return new SkillDef("avatar", "n", "d", "shock", 3, SkillDef.Kind.AVATAR,
            6f, 8f, 1f, 48f, 40f, "aura",
            new String[] {"damage_mult", "armour_mult"}, new float[] {1.2f, 0.9f},
            0.1f, 0.3f);
    }

    private void bind(SkillDef... defs) {
        Array<SkillDef> all = new Array<>();
        for (SkillDef d : defs) {
            all.add(d);
        }
        p.setSkills(all);
    }

    @BeforeEach
    void setUp() {
        run = TestDefs.run();
        world = new EntityWorld(null, new ContentRegistry(), run, null);
        world.enterRoom(TestDefs.room(RoomKind.NORMAL), TestDefs.walled(), null);
        in = new ScriptedInput();
        p = world.player();
    }

    private Enemy dummyAt(float x, float y) {
        EnemyDef def = TestDefs.enemy("dummy").brain("stationary").hp(500).contact(0)
            .aggro(0).invuln(0).build();
        return world.spawnEnemy(def, x, y);
    }

    // ---- the bar --------------------------------------------------------------

    @Test
    void skillsAreBoundBySlotAndNotByOrder() {
        bind(avatar(), lunge(), nova());
        assertEquals("lunge", p.skill(0).id, "slot 1 is the first key");
        assertEquals("nova", p.skill(1).id);
        assertEquals("avatar", p.skill(2).id);
    }

    @Test
    void anUnboundSlotDoesNothingAtAll() {
        bind(lunge());
        in.tap(GameAction.SKILL_3);
        for (int i = 0; i < 4; i++) {
            in.tick(world);
        }
        assertNull(p.skill(2));
        assertFalse(p.lunging(), "the empty third key fired the first");
    }

    // ---- the lunge ------------------------------------------------------------

    @Test
    void theLungeCarriesThePlayerForward() {
        bind(lunge());
        p.facing = Dir.RIGHT;
        float from = p.x;
        in.tap(GameAction.SKILL_1);
        for (int i = 0; i < Player.LUNGE_STEPS + 2; i++) {
            in.tick(world);
        }
        assertTrue(p.x - from > 30f, "moved only " + (p.x - from));
        assertFalse(p.lunging(), "and it ends on its own");
    }

    @Test
    void theLungeGoesDiagonallyWhenTheStickDoes() {
        // Dir has four values and the stick has eight directions. A lunge that
        // read facing would throw a player holding up-right straight right,
        // which is the thing specifically asked not to happen.
        bind(lunge());
        float fromX = p.x;
        float fromY = p.y;
        in.press(GameAction.MOVE_RIGHT);
        in.press(GameAction.MOVE_UP);
        in.tap(GameAction.SKILL_1);
        for (int i = 0; i < Player.LUNGE_STEPS + 2; i++) {
            in.tick(world);
        }
        assertTrue(p.x - fromX > 15f, "no sideways travel: " + (p.x - fromX));
        assertTrue(p.y - fromY > 15f, "no upward travel: " + (p.y - fromY));
    }

    @Test
    void theLungeHitsEachEnemyOnceAndNotEighteenTimes() {
        bind(lunge());
        p.facing = Dir.RIGHT;
        Enemy target = dummyAt(p.x + 20f, p.y);
        in.tap(GameAction.SKILL_1);
        for (int i = 0; i < Player.LUNGE_STEPS + 2; i++) {
            in.tick(world);
        }
        int lost = 500 - target.hp;
        assertTrue(lost > 0, "the thrust passed through and did nothing");
        // One hit of a 7-damage katana at 1.2x, spread included, is nowhere
        // near what twelve steps of contact would be.
        assertTrue(lost < 40, "hit more than once: lost " + lost);
    }

    // ---- the nova -------------------------------------------------------------

    @Test
    void theNovaHurtsEverythingInsideItAndNothingOutside() {
        bind(nova());
        Enemy near = dummyAt(p.x + 20f, p.y);
        Enemy far = dummyAt(p.x + 100f, p.y);
        in.tap(GameAction.SKILL_2);
        for (int i = 0; i < 3; i++) {
            in.tick(world);
        }
        assertTrue(near.hp < 500, "the one inside the ring was not hurt");
        assertEquals(500, far.hp, "the one outside it was");
    }

    @Test
    void theNovaThrowsWhatItHurtsAway() {
        bind(nova());
        Enemy near = dummyAt(p.x + 20f, p.y);
        float from = near.x;
        in.tap(GameAction.SKILL_2);
        for (int i = 0; i < 8; i++) {
            in.tick(world);
        }
        assertTrue(near.x > from, "not shoved outward: " + from + " -> " + near.x);
    }

    // ---- cooldowns ------------------------------------------------------------

    @Test
    void aSkillWillNotFireAgainUntilItsCooldownHasRun() {
        bind(nova());
        Enemy target = dummyAt(p.x + 20f, p.y);
        in.tap(GameAction.SKILL_2);
        in.tick(world);
        int afterFirst = target.hp;
        assertTrue(afterFirst < 500);

        // Pressed every step for a second: none of them may land.
        for (int i = 0; i < 60; i++) {
            in.tap(GameAction.SKILL_2);
            in.tick(world);
        }
        assertEquals(afterFirst, target.hp, "the ring fired again during its cooldown");
        assertTrue(p.cooldown(1) > 0);
    }

    @Test
    void theCooldownCountsDownAndTheSkillComesBack() {
        bind(nova());
        Enemy target = dummyAt(p.x + 20f, p.y);
        in.tap(GameAction.SKILL_2);
        in.tick(world);
        int afterFirst = target.hp;

        // Ticked until ready rather than exactly COOLDOWN_STEPS times: a hit
        // freezes the world for a few steps, and during a freeze the player
        // does not step, so world ticks and cooldown steps are not one to one.
        int waited = 0;
        while (p.cooldown(1) > 0 && waited < COOLDOWN_STEPS * 2) {
            in.tick(world);
            waited++;
        }
        assertEquals(0, p.cooldown(1), "still cooling after twice its cooldown");
        in.tap(GameAction.SKILL_2);
        in.tick(world);
        assertTrue(target.hp < afterFirst, "it did not come back");
    }

    @Test
    void aSkillThatCouldNotFireDoesNotStartItsCooldown() {
        // Pressing into a wall of unavailability must not silently eat the
        // skill for six seconds - that reads as the key being broken.
        bind(nova());
        p.takeHit(1, p.x - 10f, p.y, 0f);
        in.tap(GameAction.SKILL_2);
        in.tick(world);
        assertEquals(0, p.cooldown(1));
    }

    @Test
    void secondsInTheFileBecomeStepsInTheSimulation() {
        assertEquals(Math.round(6f / Cfg.STEP), SkillDef.steps(6f));
        assertEquals(6f, lunge().cooldownSeconds(), 0.02f);
        // Rounded up, so nothing is ever accidentally free.
        assertTrue(SkillDef.steps(0.001f) >= 1);
    }

    // ---- the ultimate ---------------------------------------------------------

    @Test
    void theUltimateChangesTheNumbersAndPutsThemBack() {
        bind(avatar());
        float before = p.mods().outgoingMult(1f);
        in.tap(GameAction.SKILL_3);
        in.tick(world);

        assertTrue(p.transformed());
        assertTrue(p.mods().outgoingMult(1f) > before, "the storm did not sharpen anything");
        assertTrue(p.mods().armourMult() < 1f, "nor thin the skin");

        for (int i = 0; i < SkillDef.steps(8f) + 2; i++) {
            in.tick(world);
        }
        assertFalse(p.transformed(), "it never ended");
        assertEquals(before, p.mods().outgoingMult(1f), 0.0001f, "and left something behind");
        assertEquals(1f, p.mods().armourMult(), 0.0001f);
    }

    @Test
    void theUltimateCostsHealth() {
        bind(avatar());
        run.maxHp = 100;
        run.hp = 100;
        in.tap(GameAction.SKILL_3);
        in.tick(world);
        assertEquals(90, run.hp, "a tenth of a hundred is ten");
    }

    @Test
    void theUltimateIsFreeWhenThereIsLittleHealthLeft() {
        // The floor is the whole design: an ultimate that cannot be cast while
        // losing cannot be cast, because losing is when anyone reaches for one.
        bind(avatar());
        run.maxHp = 100;
        run.hp = 20;
        in.tap(GameAction.SKILL_3);
        in.tick(world);
        assertTrue(p.transformed(), "refused at low health instead of being free");
        assertEquals(20, run.hp, "charged anyway");
    }

    @Test
    void theUltimateNeverCostsTheLastPointOfHealth() {
        bind(avatar());
        run.maxHp = 100;
        run.hp = 31;
        in.tap(GameAction.SKILL_3);
        in.tick(world);
        assertTrue(run.hp >= 1, "cast itself to death");
        assertTrue(p.alive());
    }

    @Test
    void dashingUnderTheStormArcsToWhatIsNear() {
        bind(avatar());
        Enemy target = dummyAt(p.x + 30f, p.y);
        in.tap(GameAction.SKILL_3);
        in.tick(world);
        int before = target.hp;

        p.facing = Dir.LEFT;
        in.tap(GameAction.ROLL);
        in.tick(world);
        assertTrue(target.hp < before, "the dash did not arc");
    }

    @Test
    void dashingWithoutTheStormDoesNot() {
        bind(avatar());
        Enemy target = dummyAt(p.x + 30f, p.y);
        p.facing = Dir.LEFT;
        in.tap(GameAction.ROLL);
        for (int i = 0; i < 4; i++) {
            in.tick(world);
        }
        assertEquals(500, target.hp, "an ordinary dash hurt something");
    }

    // ---- when a skill may not be cast -------------------------------------------

    @Test
    void nothingCastsWhileRolling() {
        bind(nova());
        Enemy target = dummyAt(p.x + 20f, p.y);
        in.tap(GameAction.ROLL);
        in.tick(world);
        assertTrue(p.rolling());
        in.tap(GameAction.SKILL_2);
        in.tick(world);
        assertEquals(500, target.hp, "cast mid-roll, which would waste the i-frames");
    }

    @Test
    void nothingCastsWhileDead() {
        bind(nova());
        Enemy target = dummyAt(p.x + 20f, p.y);
        run.hp = 0;
        in.tap(GameAction.SKILL_2);
        for (int i = 0; i < 4; i++) {
            in.tick(world);
        }
        assertEquals(500, target.hp);
    }

    @Test
    void theBarIsRebuiltWithTheModifiers() {
        // Both answer "what can this character do", and answering them in two
        // places is how one of them goes stale.
        bind(nova());
        world.refreshMods();
        assertNull(p.skill(1), "a registry with no skills must clear the bar");
    }
}
