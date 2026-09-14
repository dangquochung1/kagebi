package com.kagebi.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.kagebi.Dir;
import com.kagebi.ai.AiState;
import com.kagebi.combat.Damage;
import com.kagebi.combat.Faction;
import com.kagebi.combat.HitResolver;
import com.kagebi.combat.Hitbox;
import com.kagebi.data.ContentRegistry;
import com.kagebi.data.def.EnemyDef;
import com.kagebi.gen.CollisionGrid;
import com.kagebi.gen.RoomKind;
import com.kagebi.input.GameAction;
import com.kagebi.run.RunState;

/**
 * The player's feel, asserted step by step through the real world.
 *
 * <p>Every test runs {@link EntityWorld} with a null atlas at the real fixed
 * step, driven by {@link ScriptedInput}, so what is proved here is what the
 * game does - not what a helper method would do if something called it.
 */
class PlayerTest {

    private RunState run;
    private EntityWorld world;
    private ScriptedInput in;
    private Player p;

    @BeforeEach
    void setUp() {
        run = TestDefs.run();
        world = new EntityWorld(null, new ContentRegistry(), run, null);
        world.enterRoom(TestDefs.room(RoomKind.NORMAL), TestDefs.walled(), null);
        in = new ScriptedInput();
        p = world.player();
    }

    /** A target that never wakes, never hurts, and has no i-frames of its own. */
    private Enemy dummyAt(float x, float y) {
        EnemyDef def = TestDefs.enemy("dummy").brain("stationary").hp(500).contact(0)
            .aggro(0).invuln(0).build();
        return world.spawnEnemy(def, x, y);
    }

    /** Counts swings started, by the elapsed counter reading 1 after a step. */
    private static boolean swingJustStarted(Player p) {
        return p.swing().busy() && p.swing().elapsed() == 1;
    }

    // ---- the swing ------------------------------------------------------------

    @Test
    void theSwingOnlyHurtsDuringItsActiveWindow() {
        Enemy target = dummyAt(p.x + 14f, p.y);
        p.facing = Dir.RIGHT;
        in.tap(GameAction.ATTACK);

        List<Integer> hpByTick = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            in.tick(world);
            hpByTick.add(target.hp);
        }
        // The katana is 4 windup / 3 active / 11 recover. Ticks 1-4 are the
        // windup: nothing. Tick 5 is the first active step.
        for (int t = 0; t < 4; t++) {
            assertEquals(500, hpByTick.get(t), "no hitbox during windup, tick " + (t + 1));
        }
        // The amount is a range, not a number: the katana's 7 is rolled within
        // Damage.SPREAD, so this asserts that ONE hit landed rather than
        // pinning its size. Pinning it made this test fail the day damage
        // gained a roll, which is the test complaining about the wrong thing.
        int landed = 500 - hpByTick.get(4);
        int low = Math.round(7 * (1f - Damage.SPREAD));
        int high = Math.round(7 * (1f + Damage.SPREAD));
        assertTrue(landed >= low && landed <= high,
            "hit lands on the first active step, for " + low + " to " + high
            + " damage; took " + landed);
        for (int t = 5; t < 30; t++) {
            assertEquals(hpByTick.get(4), hpByTick.get(t),
                "the target has no i-frames; only the swing's memory stops tick " + (t + 1));
        }
    }

    @Test
    void landingAHitFreezesTheRoomForAMoment() {
        dummyAt(p.x + 14f, p.y);
        p.facing = Dir.RIGHT;
        in.tap(GameAction.ATTACK);
        in.ticks(world, 5);
        assertEquals(EntityWorld.HITSTOP_LAND, world.hitstop(), "hit-stop starts on the hit");
        float x = p.x;
        int elapsed = p.swing().elapsed();
        in.ticks(world, EntityWorld.HITSTOP_LAND);
        assertEquals(elapsed, p.swing().elapsed(), "the swing is frozen during hit-stop");
        assertEquals(x, p.x, 0f);
        in.tick(world);
        assertEquals(elapsed + 1, p.swing().elapsed(), "and resumes after it");
    }

    // ---- buffering ------------------------------------------------------------

    @Test
    void anAttackPressedDuringRecoveryQueuesAndFiresWithNoGap() {
        in.tap(GameAction.ATTACK);
        int firstEnded = -1;
        int secondStarted = -1;
        for (int t = 1; t <= 60; t++) {
            // Three steps before the katana's 18-step swing finishes.
            if (t == 15) {
                assertEquals(com.kagebi.combat.AttackState.Phase.RECOVER, p.swing().phase());
                in.tap(GameAction.ATTACK);
            }
            boolean wasBusy = p.attacking();
            in.tick(world);
            if (wasBusy && firstEnded < 0 && !p.attacking()) {
                firstEnded = t;
            }
            if (t > 1 && secondStarted < 0 && swingJustStarted(p)) {
                secondStarted = t;
            }
        }
        // The katana's 18-step swing ends on tick 18; the queued one starts on
        // tick 19. No step is spent idle between them.
        assertEquals(18, firstEnded);
        assertEquals(firstEnded + 1, secondStarted, "the queued swing starts with no gap");
        assertEquals(2, in.consumes(GameAction.ATTACK));
    }

    @Test
    void aPressTooEarlyInTheSwingIsDroppedRatherThanFiringLate() {
        in.tap(GameAction.ATTACK);
        in.tick(world);
        in.ticks(world, 4);
        in.tap(GameAction.ATTACK);  // 13 steps before the swing can take it
        int starts = 0;
        for (int t = 0; t < 60; t++) {
            in.tick(world);
            if (swingJustStarted(p)) {
                starts++;
            }
        }
        assertEquals(0, starts, "outside the 6-step buffer the press is gone");
    }

    @Test
    void aBufferedPressIsConsumedExactlyOnce() {
        in.tap(GameAction.ATTACK);
        int starts = 0;
        for (int t = 0; t < 120; t++) {
            in.tick(world);
            if (swingJustStarted(p)) {
                starts++;
            }
        }
        assertEquals(1, starts);
        assertEquals(1, in.consumes(GameAction.ATTACK));
    }

    @Test
    void holdingAttackIsOnePressNotAMachineGun() {
        in.press(GameAction.ATTACK);
        int starts = 0;
        for (int t = 0; t < 120; t++) {
            in.tick(world);
            if (swingJustStarted(p)) {
                starts++;
            }
        }
        assertEquals(1, starts);
    }

    // ---- the roll -------------------------------------------------------------

    @Test
    void theRollIsInvulnerableOnItsStepsTwoToThirteenOnly() {
        in.tap(GameAction.ROLL);
        List<Boolean> invuln = new ArrayList<>();
        for (int t = 0; t < Player.ROLL_STEPS + 4; t++) {
            in.tick(world);
            invuln.add(p.invulnerable());
        }
        int count = 0;
        int first = -1;
        for (int t = 0; t < invuln.size(); t++) {
            if (invuln.get(t)) {
                count++;
                if (first < 0) {
                    first = t;
                }
            }
        }
        assertEquals(Player.ROLL_IFRAME_TO - Player.ROLL_IFRAME_FROM, count);
        assertEquals(Player.ROLL_IFRAME_FROM, first, "no i-frames on the first two steps");
        assertFalse(invuln.get(Player.ROLL_STEPS - 1), "the end of the roll is vulnerable");
    }

    @Test
    void theRollCoversThreeTilesAndThenCoolsDown() {
        float x0 = p.x;
        in.press(GameAction.MOVE_RIGHT);
        in.tap(GameAction.ROLL);
        in.ticks(world, Player.ROLL_STEPS);
        in.release(GameAction.MOVE_RIGHT);
        assertEquals(Player.ROLL_SPEED * Player.ROLL_STEPS / 60f, p.x - x0, 0.5f);
        assertFalse(p.rolling());

        in.tap(GameAction.ROLL);
        in.tick(world);
        assertFalse(p.rolling(), "cooldown refuses an immediate second roll");
    }

    @Test
    void rollingThroughAnEnemySwingTakesNoDamage() {
        assertEquals(100, fightAStationarySwordsman(true), "rolled through it");
        assertEquals(90, fightAStationarySwordsman(false), "stood in it: one 10-damage hit");
    }

    /**
     * A rooted enemy 18px to the right swings a 10-damage box across the
     * player. Rolling right, through its body, two steps before the swing goes
     * active, must avoid every point of it; standing still must not.
     */
    private int fightAStationarySwordsman(boolean roll) {
        setUp();
        EnemyDef def = TestDefs.enemy("swordsman").brain("stationary").hp(100).contact(4)
            .attack(10, 20).aggro(60).timing(20, 12, 20, 200).build();
        Enemy e = world.spawnEnemy(def, p.x + 18f, p.y);
        boolean rolled = false;
        for (int t = 0; t < 90; t++) {
            if (roll && !rolled && e.state() == AiState.WINDUP
                    && e.stateSteps() == def.windupSteps - 2) {
                in.press(GameAction.MOVE_RIGHT);
                in.tap(GameAction.ROLL);
                rolled = true;
            }
            in.tick(world);
            if (rolled && !p.rolling()) {
                in.release(GameAction.MOVE_RIGHT);
            }
        }
        assertTrue(!roll || rolled, "the enemy never wound up");
        return run.hp;
    }

    @Test
    void aSwingCancelsIntoARollInRecoveryAndNotBefore() {
        in.tap(GameAction.ATTACK);
        in.tick(world);                     // tick 1: swing begins
        in.tick(world);                     // tick 2: windup
        in.tap(GameAction.ROLL);            // pressed during the windup
        for (int t = 3; t <= 7; t++) {
            in.tick(world);
            assertFalse(p.rolling(), "no roll during windup or active, tick " + t);
            assertTrue(p.attacking());
        }
        in.tick(world);                     // tick 8: first step of recovery
        assertTrue(p.rolling(), "the buffered roll cancels in at the first cancellable step");
        assertFalse(p.attacking(), "and the swing is abandoned");
    }

    // ---- movement -------------------------------------------------------------

    @Test
    void movementIsLockedForTheWeaponsRootSteps() {
        p.setWeapon(TestDefs.hammer());     // rootSteps 24
        float x0 = p.x;
        in.press(GameAction.MOVE_RIGHT);
        in.tap(GameAction.ATTACK);
        for (int t = 1; t <= 24; t++) {
            in.tick(world);
            assertEquals(x0, p.x, 0f, "rooted at tick " + t);
        }
        in.tick(world);
        assertTrue(p.x > x0, "free, at reduced speed, once the root ends");
        assertEquals(Player.SPEED * Player.ATTACK_MOVE_SCALE / 60f, p.x - x0, 1e-3f);
    }

    @Test
    void diagonalsAreNotFaster() {
        float x0 = p.x;
        float y0 = p.y;
        in.press(GameAction.MOVE_RIGHT);
        in.press(GameAction.MOVE_DOWN);
        in.ticks(world, 30);
        float d = (float) Math.hypot(p.x - x0, p.y - y0);
        assertEquals(Player.SPEED * 30 / 60f, d, 0.05f);
    }

    @Test
    void walkingDiagonallyIntoAWallSlidesAlongIt() {
        // The top wall is row 10, starting at y = 160. CollisionGrid tests a
        // body's last pixel, so a 12px body's centre stops just short of 155.
        // Start a hair below that, pressing up and right.
        p.placeAt(100f, 153.5f, Dir.UP);
        in.press(GameAction.MOVE_UP);
        in.press(GameAction.MOVE_RIGHT);
        in.ticks(world, 30);
        assertTrue(p.y < 155f, "did not enter the wall: y=" + p.y);
        float expected = Player.SPEED * 0.70710678f * 30 / 60f;
        assertEquals(expected, p.x - 100f, 0.05f,
            "the blocked axis is dropped, the free one keeps its full share");
    }

    /**
     * Walking straight at a gap a few pixels off its line finds the gap rather
     * than stopping dead on its edge. The garden gate is eighteen pixels
     * between its posts and the ninja is twelve wide, and before this a player
     * walking down the path at it had to line up to within six.
     */
    @Test
    void walkingStraightAtAGapJustOffItsLineSlidesIntoIt() {
        CollisionGrid grid = TestDefs.walled();
        for (int tx = 0; tx < grid.width(); tx++) {
            if (tx != 8) {
                grid.set(tx, 5, true);
            }
        }
        world.enterRoom(TestDefs.room(RoomKind.NORMAL), grid, null);
        p = world.player();
        // The gap is x 128 to 144, so a 12px body fits centred from 134 to 138.
        p.placeAt(142f, 60f, Dir.UP);
        in.press(GameAction.MOVE_UP);
        in.ticks(world, 90);
        assertTrue(p.y > 102f, "through the gap and out the far side: y=" + p.y);
    }

    /** A wall with no way past it is still a wall. */
    @Test
    void walkingStraightIntoAWallDoesNotSlideAlongIt() {
        p.placeAt(100f, 153.5f, Dir.UP);
        in.press(GameAction.MOVE_UP);
        in.ticks(world, 30);
        assertEquals(100f, p.x, 0.001f, "slid along a wall with nothing to slide towards");
        assertTrue(p.y < 155f, "did not enter the wall: y=" + p.y);
    }

    // ---- being hit ------------------------------------------------------------

    @Test
    void aHitKnocksThePlayerBackFarEnoughToSee() {
        float x0 = p.x;
        // A box over the player's left half, thrown by something 8px to the left.
        Hitbox hit = new Hitbox(p.x - 14f, p.y - 6f, 12f, 12f, 10, 70f, Faction.ENEMY,
            p.x - 8f, p.y);
        assertTrue(HitResolver.hit(hit, p, null));
        in.ticks(world, 30);
        // 70 px/s decaying linearly over 12 steps is 70 x 13/120 = 7.58px:
        // half a tile, a visible shove at 320x180 that does not throw the
        // player out of position. 6.42 here would mean the first step was lost.
        assertEquals(7.58f, p.x - x0, 0.01f);
        assertEquals(90, run.hp);
    }

    @Test
    void aHitInterruptsTheSwingAndGrantsMercyFrames() {
        in.tap(GameAction.ATTACK);
        in.ticks(world, 2);
        assertTrue(p.attacking());
        p.takeHit(5, p.x + 5f, p.y, 50f);
        assertFalse(p.attacking(), "trading blows does not pay out");
        assertEquals(Player.HURT_IFRAMES, p.iframes.remaining());
    }

    @Test
    void deathIsReportedThroughTheRun() {
        p.takeHit(1000, p.x, p.y, 0f);
        assertEquals(0, run.hp);
        assertTrue(world.playerDead());
        in.tap(GameAction.ATTACK);
        in.ticks(world, 10);
        assertFalse(p.attacking(), "the dead do not swing");
    }
}
