package com.kagebi.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class AttackStateTest {

    /** Runs a whole swing and records the phase seen on each step before advancing. */
    private static List<AttackState.Phase> trace(AttackState a) {
        List<AttackState.Phase> seen = new ArrayList<>();
        int guard = 0;
        while (a.busy() && guard++ < 1000) {
            seen.add(a.phase());
            a.step();
        }
        return seen;
    }

    @Test
    void phasesRunInOrderForExactlyTheirSteps() {
        // The starting katana from weapons.json: 4 / 3 / 11.
        AttackState a = new AttackState();
        a.begin(4, 3, 11, 0);
        List<AttackState.Phase> seen = trace(a);
        assertEquals(18, seen.size(), "the katana's 18-step cycle");
        for (int i = 0; i < 18; i++) {
            AttackState.Phase expected = i < 4 ? AttackState.Phase.WINDUP
                : i < 7 ? AttackState.Phase.ACTIVE : AttackState.Phase.RECOVER;
            assertEquals(expected, seen.get(i), "step " + i);
        }
        assertEquals(AttackState.Phase.IDLE, a.phase());
    }

    @Test
    void onlyTheActiveWindowIsActive() {
        AttackState a = new AttackState();
        a.begin(16, 5, 25, 24);     // the hammer
        int activeSteps = 0;
        int firstActive = -1;
        for (int i = 0; a.busy(); i++) {
            if (a.active()) {
                activeSteps++;
                if (firstActive < 0) {
                    firstActive = i;
                }
            }
            a.step();
        }
        assertEquals(5, activeSteps);
        assertEquals(16, firstActive, "no hitbox during the 16-step windup");
    }

    @Test
    void emptyPhasesAreSkippedNotLingeredIn() {
        AttackState a = new AttackState();
        a.begin(0, 2, 0, 0);
        assertTrue(a.active(), "no windup: active on the step it began");
        a.step();
        assertTrue(a.active());
        a.step();
        assertFalse(a.busy(), "no recovery: idle the moment the active window ends");
    }

    @Test
    void cancellableInRecoveryAndNowhereElse() {
        AttackState a = new AttackState();
        a.begin(3, 2, 4, 0);
        for (int i = 0; a.busy(); i++) {
            boolean shouldCancel = a.phase() == AttackState.Phase.RECOVER;
            assertEquals(shouldCancel, a.cancellable(), "step " + i + " in " + a.phase());
            a.step();
        }
        assertFalse(a.cancellable(), "an idle attack is not a cancellable one");
    }

    @Test
    void rootedForExactlyRootSteps() {
        AttackState a = new AttackState();
        a.begin(8, 4, 16, 6);       // the axe: rootSteps 6
        int rooted = 0;
        while (a.busy()) {
            if (a.rooted()) {
                rooted++;
            }
            a.step();
        }
        assertEquals(6, rooted);
    }

    @Test
    void aTargetIsClaimedOncePerSwing() {
        Object slime = new Object();
        Object bat = new Object();
        AttackState a = new AttackState();
        a.begin(0, 4, 0, 0);
        assertTrue(a.markHit(slime));
        assertFalse(a.markHit(slime), "second claim in the same swing");
        assertTrue(a.markHit(bat), "a different target is still fair game");

        a.begin(0, 4, 0, 0);
        assertTrue(a.markHit(slime), "a new swing forgets the last one's targets");
    }

    @Test
    void cancelReturnsToIdleAndForgetsTargets() {
        Object slime = new Object();
        AttackState a = new AttackState();
        a.begin(2, 2, 2, 2);
        a.markHit(slime);
        a.cancel();
        assertFalse(a.busy());
        assertFalse(a.rooted());
        assertFalse(a.alreadyHit(slime));
    }
}
