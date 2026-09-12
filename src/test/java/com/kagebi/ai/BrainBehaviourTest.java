package com.kagebi.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.kagebi.data.def.EnemyDef;
import com.kagebi.entity.Enemy;
import com.kagebi.entity.TestDefs;

/**
 * What each brain promises the player, one promise per test.
 *
 * <p>These are the behaviours a telegraph is a contract about: if the windup
 * shows a direction or a point, the attack must go there and nowhere else.
 */
class BrainBehaviourTest {

    private static Enemy spawn(EnemyDef def, float x, float y) {
        return AiStateMachineTest.spawn(def, x, y);
    }

    /** Steps until the enemy reaches a state, failing loudly if it never does. */
    private static int until(FakeArena arena, Enemy e, AiState target, int limit) {
        for (int t = 0; t < limit; t++) {
            if (e.state() == target) {
                return t;
            }
            arena.step(e);
        }
        throw new AssertionError(e.def.id + " never reached " + target + "; stuck in " + e.state());
    }

    @Test
    void theChargersDashIgnoresThePlayerMovingAfterLaunch() {
        FakeArena arena = new FakeArena();
        Enemy e = spawn(TestDefs.kappared(), 100f, 88f);
        arena.px = 130f;
        arena.py = 88f;
        until(arena, e, AiState.ATTACK, 200);
        float x0 = e.x;
        float y0 = e.y;
        // The player steps well off the line the instant the dash starts.
        arena.px = 130f;
        arena.py = 150f;
        while (e.state() == AiState.ATTACK) {
            arena.step(e);
        }
        assertTrue(e.x - x0 > 10f, "dashed toward where the player was");
        assertEquals(y0, e.y, 0.5f, "and did not bend toward where the player went");
    }

    @Test
    void theChargerIsStunnedLongerWhenItHitsAWall() {
        FakeArena arena = new FakeArena();
        EnemyDef def = TestDefs.kappared();
        // Close to the left wall with the player between: the windup's
        // backstep carries it about 2px out, and the 18px dash then has to end
        // in the wall, whose face stops a 12px body's centre at x = 22.
        Enemy e = spawn(def, 36f, 88f);
        arena.px = 23f;
        arena.py = 88f;
        until(arena, e, AiState.ATTACK, 200);
        until(arena, e, AiState.RECOVER, 200);
        assertEquals(def.cooldownSteps + ChargerBrain.WALL_STUN, e.cooldown,
            "slamming into the wall costs extra");
    }

    @Test
    void theHopperLandsWhereThePlayerStoodWhenTheSquatBegan() {
        FakeArena arena = new FakeArena();
        Enemy slime = spawn(TestDefs.slime(), 120f, 88f);
        arena.px = 150f;
        arena.py = 88f;
        until(arena, slime, AiState.WINDUP, 200);
        float tx = arena.px;
        float ty = arena.py;
        // The player walks off during the squat: the hop must not follow.
        arena.px = 150f;
        arena.py = 40f;
        until(arena, slime, AiState.RECOVER, 200);
        assertEquals(tx, slime.x, 1f, "landed on the locked point");
        assertEquals(ty, slime.y, 1f);
        assertEquals(0, arena.landed, "and so missed the player who read it");
    }

    @Test
    void aHopFromTooFarFallsShort() {
        FakeArena arena = new FakeArena();
        Enemy slime = spawn(TestDefs.slime(), 100f, 88f);
        arena.px = 100f + HopperBrain.HOP_RANGE - 1f;
        arena.py = 88f;
        until(arena, slime, AiState.WINDUP, 200);
        float startX = slime.x;
        until(arena, slime, AiState.RECOVER, 200);
        assertEquals(HopperBrain.HOP_MAX, slime.x - startX, 1f);
    }

    @Test
    void theShooterFiresExactlyOncePerAttack() {
        FakeArena arena = new FakeArena();
        Enemy octo = spawn(TestDefs.octopus(), 60f, 88f);
        arena.px = 140f;
        arena.py = 88f;
        int attacks = 0;
        boolean inAttack = false;
        for (int t = 0; t < 1200; t++) {
            arena.step(octo);
            if (octo.state() == AiState.ATTACK && !inAttack) {
                attacks++;
            }
            inAttack = octo.state() == AiState.ATTACK;
        }
        assertTrue(attacks >= 3);
        assertEquals(attacks, arena.shots, "one shot per attack, not one per active step");
        assertTrue(arena.strikeStates.isEmpty(), "the shot is the attack; no melee box");
    }

    @Test
    void theShooterBacksOffWhenCrowded() {
        FakeArena arena = new FakeArena();
        EnemyDef def = TestDefs.octopus();
        Enemy octo = spawn(def, 160f, 88f);
        octo.cooldown = 10000;      // hold its fire: only movement is under test
        arena.px = 170f;
        arena.py = 88f;
        for (int t = 0; t < 60; t++) {
            arena.step(octo);
        }
        assertTrue(octo.x < 150f, "retreated from a player inside its comfort range: x=" + octo.x);
    }

    @Test
    void theStationaryEnemyNeverMoves() {
        FakeArena arena = new FakeArena();
        Enemy statue = spawn(AiStateMachineTest.defFor("stationary"), 160f, 88f);
        for (int t = 0; t < 2000; t++) {
            AiStateMachineTest.movePlayer(arena, t);
            arena.step(statue);
            assertEquals(160f, statue.x, 0f);
            assertEquals(88f, statue.y, 0f);
        }
        assertTrue(arena.landed > 0, "but it still hits what walks into it");
    }

    @Test
    void theCasterPlacesItsCloudWhereTheCastBegan() {
        FakeArena arena = new FakeArena();
        Enemy caster = spawn(AiStateMachineTest.defFor("caster"), 60f, 88f);
        arena.px = 120f;
        arena.py = 88f;
        until(arena, caster, AiState.WINDUP, 200);
        arena.px = 120f;
        arena.py = 140f;            // moved away during the cast
        until(arena, caster, AiState.ATTACK, 200);
        assertEquals(1, arena.hazards.size());
        assertEquals(120f, arena.hazards.get(0)[0], 0f);
        assertEquals(88f, arena.hazards.get(0)[1], 0f, "on the old spot, not the new one");
    }

    @Test
    void anInterruptedCastPlacesNothing() {
        FakeArena arena = new FakeArena();
        Enemy caster = spawn(AiStateMachineTest.defFor("caster"), 60f, 88f);
        arena.px = 120f;
        arena.py = 88f;
        until(arena, caster, AiState.WINDUP, 200);
        for (int t = 0; t < 10; t++) {
            arena.step(caster);
        }
        caster.takeHit(1, arena.px, arena.py, 40f);
        assertEquals(AiState.HURT, caster.state());
        until(arena, caster, AiState.CHASE, 100);
        assertTrue(arena.hazards.isEmpty(), "the acolyte's 90-step cast is meant to be stopped");
    }

    @Test
    void theAmbusherIsStillAndHarmlessUntilWoken() {
        FakeArena arena = new FakeArena();
        EnemyDef def = AiStateMachineTest.defFor("ambusher");
        Enemy mollusc = spawn(def, 160f, 88f);
        arena.px = 160f + def.aggroRange + 20f;
        arena.py = 88f;
        for (int t = 0; t < 300; t++) {
            arena.step(mollusc);
            assertTrue(mollusc.dormant);
            assertEquals(160f, mollusc.x, 0f);
            assertFalse(mollusc.brain().harmfulOnContact(mollusc), "scenery does not bite");
        }
        arena.px = 160f + def.aggroRange - 5f;
        arena.step(mollusc);
        assertFalse(mollusc.dormant);
        assertEquals(AiState.WINDUP, mollusc.state(), "the reveal is the telegraph");
    }

    @Test
    void theOrbiterCirclesBeforeItDarts() {
        FakeArena arena = new FakeArena();
        EnemyDef def = AiStateMachineTest.defFor("orbiter");
        Enemy kappa = spawn(def, 160f + 44f, 88f);
        arena.px = 160f;
        arena.py = 88f;
        int orbitSteps = 0;
        float minDist = Float.MAX_VALUE;
        while (kappa.state() != AiState.WINDUP && orbitSteps < 400) {
            arena.step(kappa);
            if (kappa.state() == AiState.CHASE) {
                orbitSteps++;
                minDist = Math.min(minDist, kappa.distanceTo(arena.px, arena.py));
            }
        }
        assertTrue(orbitSteps >= OrbiterBrain.ORBIT_MIN, "circled for " + orbitSteps);
        assertTrue(minDist > def.attackRange + 5f,
            "held outside sword reach while circling: " + minDist);
    }

    @Test
    void theSplitterAsksForTwoCopiesOnceAndItsChildrenNone() {
        FakeArena arena = new FakeArena();
        Enemy parent = spawn(TestDefs.mushroom(), 160f, 88f);
        parent.brain().onDeath(parent, arena);
        assertEquals(2, arena.copies);
        Enemy child = spawn(TestDefs.mushroom(), 160f, 88f);
        child.generation = 1;
        child.brain().onDeath(child, arena);
        assertEquals(2, arena.copies, "one generation only");
    }
}
