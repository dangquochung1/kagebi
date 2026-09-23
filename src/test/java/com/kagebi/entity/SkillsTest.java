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
import com.kagebi.combat.Modifiers;
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

    /**
     * One skill, spelled out. Nineteen positional arguments repeated four
     * times is a test that fails to compile every time the def grows a field,
     * and reads as noise in between.
     */
    private static SkillDef def(String id, int slot, SkillDef.Kind kind,
                                float duration, float damageMult, float range,
                                float knockback, float strikeMult,
                                SkillDef.Fx fx, String[] effects, float[] mags,
                                float hpCost, float hpFloor) {
        return new SkillDef(id, null, "n", "d", "bolt", slot, kind,
            6f, duration, damageMult, range, knockback, strikeMult, "bolt", fx,
            effects, mags, hpCost, hpFloor);
    }

    private static SkillDef lunge() {
        return def("lunge", 1, SkillDef.Kind.LUNGE, 0f, 1.2f, 40f, 60f, 0f,
            null, new String[0], new float[0], 0f, 0f);
    }

    private static SkillDef nova() {
        return def("nova", 2, SkillDef.Kind.NOVA, 0f, 1f, 36f, 140f, 0f,
            null, new String[0], new float[0], 0f, 0f);
    }

    /** The storm: a dash under it is a weapon, which is what dashVfx says. */
    private static SkillDef avatar() {
        return def("avatar", 3, SkillDef.Kind.AVATAR, 8f, 1f, 48f, 40f, 0.35f,
            dash("trail"), new String[] {"damage_mult", "armour_mult"},
            new float[] {1.2f, 0.9f}, 0.1f, 0.3f);
    }

    /** The same ultimate with no dash strip named: speed and nothing else. */
    private static SkillDef quietAvatar() {
        return def("quiet", 3, SkillDef.Kind.AVATAR, 8f, 1f, 48f, 40f, 0.35f,
            null, new String[] {"damage_mult"}, new float[] {1.2f}, 0f, 0f);
    }

    private static SkillDef.Fx dash(String name) {
        return new SkillDef.Fx(null, null, null, null, null, name);
    }

    /** Three seconds of charge, at half again the walking speed. */
    private static SkillDef charge() {
        return chargeOn(2);
    }

    private static SkillDef chargeOn(int slot) {
        return def("charge", slot, SkillDef.Kind.CHARGE, 3f, 0.9f, 14f, 120f, 0f,
            null, new String[] {"move_speed_mult"}, new float[] {1.5f}, 0f, 0f);
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

    // ---- the charge -------------------------------------------------------------

    @Test
    void theChargeRunsForItsWholeDurationAndThenStops() {
        bind(charge());
        in.tap(GameAction.SKILL_2);
        in.tick(world);
        assertTrue(p.charging(), "the charge did not start");
        in.ticks(world, SkillDef.steps(3f) - 2);
        assertTrue(p.charging(), "the charge ended early");
        in.ticks(world, 3);
        assertFalse(p.charging(), "the charge never ended");
    }

    @Test
    void theWeaponsArePutAwayWhileTheChargeRuns() {
        bind(charge());
        Enemy target = dummyAt(p.x + 6f, p.y);
        in.tap(GameAction.SKILL_2);
        in.tick(world);
        assertTrue(p.charging());
        int after = target.hp;

        // Attack and throw for the rest of it. Neither may produce a swing:
        // giving the hands up is the whole price of the skill.
        for (int i = 0; i < 20; i++) {
            in.press(GameAction.ATTACK);
            in.press(GameAction.THROW);
            in.tick(world);
        }
        assertTrue(p.charging(), "the test ran past the end of the charge");
        assertFalse(p.attacking(), "a weapon came out mid-charge");
        assertTrue(target.hp <= after, "sanity: the dummy is where the charge is");
    }

    @Test
    void theChargeBurnsWhatItRunsIntoAndNotSixtyTimesASecond() {
        bind(charge());
        Enemy target = dummyAt(p.x + 6f, p.y);
        in.tap(GameAction.SKILL_2);
        // Half a second of standing on it: one contact, then the re-arm.
        in.ticks(world, Player.CHARGE_REARM_STEPS - 4);
        int lost = 500 - target.hp;
        assertTrue(lost > 0, "the charge ran through it and did nothing");
        // A charge that hit every step would take hundreds off a 500 pool.
        assertTrue(lost < 120, "the charge hit " + lost + ", which is every step");
    }

    @Test
    void aSecondSkillIsRefusedWhileTheChargeRuns() {
        // The charge moved to slot 3 so the nova can keep its own, which is 2.
        bind(nova(), chargeOn(3));
        Enemy target = dummyAt(p.x + 20f, p.y);
        in.tap(GameAction.SKILL_3);
        in.tick(world);
        assertTrue(p.charging());
        int after = target.hp;
        in.tap(GameAction.SKILL_2);
        in.tick(world);
        assertEquals(after, target.hp, "a nova went off mid-charge");
    }

    // ---- a skill that hurts something says so ------------------------------------

    /**
     * Every skill reports its hits, which is what puts a number over them.
     *
     * <p>The bug this is here for: {@code popDamage} was only ever reached
     * through {@code applyOnHit}, and only the main hand and the off hand
     * called that - so a nova, a thrust and a dash arc all hurt things in
     * complete silence. No number, no health bar, and no on-hit relic.
     *
     * <p>The bar is what is checked rather than the number, because the number
     * needs a font and these tests run without one. Both come out of the same
     * call, so the bar standing up is the number having been asked for.
     */
    @Test
    void theRingSaysWhatItDid() {
        bind(nova());
        Enemy target = dummyAt(p.x + 20f, p.y);
        assertEquals(0f, target.healthBarFade(), 0.001f, "sanity: untouched");
        in.tap(GameAction.SKILL_2);
        in.ticks(world, 3);
        assertTrue(target.hp < 500, "the ring did not hurt it");
        assertTrue(target.healthBarFade() > 0f, "the ring hurt it in silence");
    }

    @Test
    void theThrustSaysWhatItDid() {
        bind(lunge());
        Enemy target = dummyAt(p.x + 16f, p.y);
        p.facing = Dir.RIGHT;
        in.tap(GameAction.SKILL_1);
        in.ticks(world, Player.LUNGE_STEPS + 2);
        assertTrue(target.hp < 500, "the thrust did not hurt it");
        assertTrue(target.healthBarFade() > 0f, "the thrust hurt it in silence");
    }

    // ---- what a fire ultimate leaves behind --------------------------------------

    /** The ultimate Hoả Tâm has: no blood paid, and everything it touches burns. */
    private static SkillDef burner() {
        return def("burner", 3, SkillDef.Kind.AVATAR, 10f, 1f, 40f, 150f, 0f,
            null, new String[] {"burn_on_hit"}, new float[] {0.04f}, 0f, 0f);
    }

    @Test
    void aBurnTakesEightTicksAndThenLetsGo() {
        bind(burner());
        Enemy target = dummyAt(p.x + 8f, p.y);
        in.tap(GameAction.SKILL_3);
        in.tick(world);
        assertTrue(p.transformed());

        // Casting it rebuilt the modifiers, and rebuilding the modifiers
        // rebinds the bar from a registry this test deliberately leaves empty
        // - see theBarIsRebuiltWithTheModifiers. The transformation survives
        // that; the bar has to be put back by hand.
        bind(lunge());
        p.facing = Dir.RIGHT;
        in.tap(GameAction.SKILL_1);
        in.ticks(world, Player.LUNGE_STEPS + 2);
        assertTrue(target.burning(), "the lunge did not set it alight");

        int lit = target.hp;
        in.ticks(world, Modifiers.TICK_STEPS * 8 + 4);
        assertFalse(target.burning(), "the burn never went out");
        int burned = lit - target.hp;
        // 4% of 500 is 20 a tick, capped at 15, eight times. Eight rather than
        // the three it was: three seconds was too short for a player mid-fight
        // to see a burn start or stop, so it read as a flicker on the swing.
        assertEquals(Modifiers.BURN_TICK_CAP * 8, burned,
            "eight ticks of a capped burn, and nothing else");
    }

    /**
     * The regression this whole field exists for.
     *
     * <p>Every ultimate used to arc on a dash, from one hard-coded lightning
     * strip, so the fire set shipped with a blue streak and a second weapon
     * nobody had balanced. An ultimate that names no dash strip is now only
     * what its numbers say it is.
     */
    @Test
    void anUltimateThatNamesNoDashStripLeavesTheDashAlone() {
        bind(quietAvatar());
        Enemy target = dummyAt(p.x + 30f, p.y);
        in.tap(GameAction.SKILL_3);
        in.tick(world);
        assertTrue(p.transformed(), "the ultimate did not start");
        int before = target.hp;

        p.facing = Dir.LEFT;
        in.tap(GameAction.ROLL);
        in.ticks(world, Player.ROLL_STEPS + 2);
        assertEquals(before, target.hp, "the dash arced anyway");
    }

    @Test
    void aBurnRefreshesRatherThanStacking() {
        Enemy e = dummyAt(p.x + 40f, p.y);
        e.burn(5, Modifiers.BURN_STEPS);
        e.burn(5, Modifiers.BURN_STEPS);
        int before = e.hp;
        in.ticks(world, Modifiers.TICK_STEPS + 2);
        assertEquals(5, before - e.hp, "two burns stacked into one bigger one");
    }

    // ---- bosses take it and do not move ------------------------------------------

    @Test
    void aBossIsHurtByAKnockbackSkillAndNotShovedByIt() {
        bind(nova());
        EnemyDef def = TestDefs.enemy("lump").brain("stationary").hp(900).contact(0)
            .aggro(0).invuln(0).resist(1f).build();
        Enemy boss = world.spawnEnemy(def, p.x + 20f, p.y);
        float from = boss.x;
        in.tap(GameAction.SKILL_2);
        in.ticks(world, 3);
        assertTrue(boss.hp < 900, "the ring did not hurt it");
        assertEquals(from, boss.x, 0.001f, "the ring shoved something that fully resists");
    }

    // ---- shift reads instead of casting -----------------------------------------

    @Test
    void holdingShiftAsksAboutASkillRatherThanCastingIt() {
        bind(nova());
        Enemy target = dummyAt(p.x + 20f, p.y);
        in.holdInfo(true);
        in.press(GameAction.SKILL_2);
        in.tick(world);
        assertEquals(500, target.hp, "shift and the key still cast it");
        assertEquals(1, world.intent().asking, "the panel was not asked for");
    }

    @Test
    void lettingGoOfShiftPutsThePanelAwayAndGivesTheKeyBack() {
        bind(nova());
        Enemy target = dummyAt(p.x + 20f, p.y);
        in.holdInfo(true);
        in.press(GameAction.SKILL_2);
        in.tick(world);
        assertEquals(1, world.intent().asking);
        assertEquals(500, target.hp);

        // Letting go of both puts the panel away without casting. The press
        // stays buffered for six steps whether or not anything used it, so
        // reading a skill used to fire it the moment shift came off.
        in.holdInfo(false);
        in.release(GameAction.SKILL_2);
        in.tick(world);
        assertEquals(-1, world.intent().asking, "the panel stayed up");
        in.ticks(world, 8);
        assertEquals(500, target.hp, "letting go of shift cast the skill it read");

        in.tap(GameAction.SKILL_2);
        in.tick(world);
        assertTrue(target.hp < 500, "the key never came back");
    }    // ---- venom counts, and the third one bursts ----------------------------------

    /**
     * An ultimate whose only job is to put venom on what the player hits.
     *
     * <p>A minute long, which no shipped ultimate is. Three stacks take three
     * separate hits, and a ten-second window that also has to outlast three
     * cooldowns is a test measuring the clock rather than the mechanic.
     */
    private static SkillDef venomer() {
        return def("venomer", 3, SkillDef.Kind.AVATAR, 60f, 1f, 40f, 0f, 0f,
            null, new String[] {"venom_on_hit"}, new float[] {0.02f}, 0f, 0f);
    }

    /** Half a second of cooldown: three stacks inside one venom's lifetime. */
    private static final int QUICK_STEPS = SkillDef.steps(0.5f);

    private static SkillDef quickNova() {
        return new SkillDef("quick", null, "n", "d", "bolt", 1,
            SkillDef.Kind.NOVA, 0.5f, 0f, 1f, 36f, 0f, 0f, "bolt", null,
            new String[0], new float[0], 0f, 0f);
    }

    /**
     * Three stacks, one burst, and then nothing left.
     *
     * <p>The reset is the part under test. Without it a fast weapon runs the
     * count away and every swing after the third detonates; with it, the third
     * blow is the one worth landing and the fourth starts again.
     */
    @Test
    void threeStacksOfVenomBurstAndLeaveNothingBehind() {
        bind(venomer());
        Enemy target = dummyAt(p.x + 8f, p.y);
        in.tap(GameAction.SKILL_3);
        in.tick(world);
        assertTrue(p.transformed());
        // Casting the ultimate rebuilt the modifiers, which rebinds the bar
        // from this test's deliberately empty registry - see
        // theBarIsRebuiltWithTheModifiers. Nothing below rebuilds them again.
        bind(quickNova());

        for (int i = 1; i <= 2; i++) {
            ringOnce();
            assertEquals(i, target.venomStacks(), "stack " + i + " did not land");
        }
        int before = target.hp;
        ringOnce();
        assertEquals(0, target.venomStacks(), "the third stack did not burst");
        // A fifth of 500 is 100, over the cap of 60 - so 60 is what it takes,
        // and the ring's own damage is on top of that.
        assertTrue(before - target.hp > Modifiers.VENOM_BURST_CAP,
            "the burst did not land on top of the blow that caused it");
    }

    /** One ring, and the wait for it to come back. */
    private void ringOnce() {
        in.tap(GameAction.SKILL_1);
        in.ticks(world, QUICK_STEPS + 2);
    }

    /**
     * A boss is worth poisoning and is not killed by having a big health bar.
     *
     * <p>The same bargain the burn strikes: a share of maximum health is the
     * only shape that reads the same on a goblin and on a boss, and a cap is
     * the price of using that shape at all.
     */
    @Test
    void aBossTakesACappedBurstRatherThanAShareOfItsOwnHealthBar() {
        bind(venomer());
        EnemyDef def = TestDefs.enemy("lump").brain("stationary").hp(900).contact(0)
            .aggro(0).invuln(0).resist(1f).build();
        Enemy boss = world.spawnEnemy(def, p.x + 8f, p.y);
        in.tap(GameAction.SKILL_3);
        in.tick(world);
        bind(quickNova());

        ringOnce();
        ringOnce();
        int before = boss.hp;
        ringOnce();
        assertEquals(0, boss.venomStacks(), "the burst did not go off on a boss");
        // A fifth of 900 is 180. What it is allowed to take is 60.
        int taken = before - boss.hp;
        assertTrue(taken < 180, "a fifth of the boss's bar went in one blow: " + taken);
    }

    // ---- the ward pays out for being hit ----------------------------------------

    /** An ultimate that answers a blow rather than avoiding one. */
    private static SkillDef warder() {
        return def("warder", 3, SkillDef.Kind.AVATAR, 60f, 1f, 40f, 0f, 0f,
            null, new String[] {"heal_on_hurt", "damage_taken_mult"},
            new float[] {12f, 0.8f}, 0f, 0f);
    }

    /**
     * Healing and biting, off one number and with no rule of its own.
     *
     * <p>The bite is the part worth a test. It is the player's own damage
     * through the ordinary on-hit path, which is why a character whose blows
     * carry venom poisons with it too and why nothing here mentions venom.
     */
    @Test
    void theWardHealsWhatItAbsorbsAndBitesWhatCausedIt() {
        bind(warder());
        in.tap(GameAction.SKILL_3);
        in.tick(world);
        assertTrue(p.transformed(), "the ward did not go up");

        // Close enough to be bitten, and biting: contact damage is the only
        // thing in this test that can start the exchange.
        EnemyDef def = TestDefs.enemy("biter").brain("stationary").hp(500).contact(6)
            .aggro(0).invuln(0).build();
        Enemy biter = world.spawnEnemy(def, p.x + 6f, p.y);
        run.hp = run.maxHp - 40;
        int hpBefore = run.hp;
        int biterBefore = biter.hp;

        in.ticks(world, 3);
        assertTrue(biter.hp < biterBefore, "the ward did not bite what hit it");
        // Healing more than the blow costs, so that health going *up* is the
        // assertion. "Lost less than it should have" would pass on the damage
        // reduction alone and say nothing about the heal - which is the half
        // of this that is new code.
        assertTrue(run.hp > hpBefore, "the ward healed nothing: " + hpBefore
            + " -> " + run.hp);
    }

    // ---- the charge is a tumble, and a short one is invulnerable -----------------

    /**
     * Half a second of mercy at the start of a three-second run.
     *
     * <p>Both halves matter. A tumble that can be hit reads as broken, and
     * three seconds of one is a nine-second cooldown on being mortal.
     */
    @Test
    void aChargeIsUntouchableAtTheStartAndMortalForTheRestOfIt() {
        bind(charge());
        in.tap(GameAction.SKILL_2);
        in.tick(world);
        assertTrue(p.charging(), "the charge did not start");
        assertTrue(p.invulnerable(), "the charge began without its mercy window");

        in.ticks(world, Player.CHARGE_IFRAMES + 2);
        assertTrue(p.charging(), "the charge ended early");
        assertFalse(p.invulnerable(), "the charge stayed invulnerable for its whole run");
    }
}
