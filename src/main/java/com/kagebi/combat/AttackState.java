package com.kagebi.combat;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * One swing, as a three-beat machine: windup, active, recover.
 *
 * <p>Only the active beat has a hitbox. That single rule is the difference
 * between combat that can be read and a damage aura: the windup is the promise,
 * the active window is the payment, and the recovery is what the attack cost.
 * An attack whose hitbox lives for its whole animation cannot be dodged on
 * reaction by anybody, because there is nothing to react to.
 *
 * <p>It also owns the set of things this swing has already hit. Without it an
 * active window of four steps deals its damage four times, and every weapon in
 * the game is silently worth its {@code activeSteps} in extra damage.
 */
public final class AttackState {

    public enum Phase {
        IDLE,
        /** Telegraph. No hitbox. */
        WINDUP,
        /** The only phase with a hitbox. */
        ACTIVE,
        /** The cost. Cancellable, so a dodge out of a whiff is possible. */
        RECOVER
    }

    private Phase phase = Phase.IDLE;
    private int windup;
    private int active;
    private int recover;
    private int root;
    private int inPhase;
    private int elapsed;

    /**
     * Identity, not equality: two enemies of the same kind are different
     * targets even if some future {@code equals} says otherwise.
     */
    private final Set<Object> alreadyHit =
        Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Starts a swing.
     *
     * @param rootSteps steps from the start during which the attacker cannot
     *                  move. A hammer commits; a katana barely does.
     */
    public void begin(int windupSteps, int activeSteps, int recoverSteps, int rootSteps) {
        this.windup = Math.max(0, windupSteps);
        this.active = Math.max(0, activeSteps);
        this.recover = Math.max(0, recoverSteps);
        this.root = Math.max(0, rootSteps);
        this.inPhase = 0;
        this.elapsed = 0;
        alreadyHit.clear();
        phase = firstNonEmpty(Phase.WINDUP);
    }

    /** Advances one fixed step. */
    public void step() {
        if (phase == Phase.IDLE) {
            return;
        }
        inPhase++;
        elapsed++;
        while (phase != Phase.IDLE && inPhase >= durationOf(phase)) {
            inPhase -= durationOf(phase);
            phase = firstNonEmpty(next(phase));
        }
    }

    public Phase phase() {
        return phase;
    }

    /** True only during the active window, and therefore the only time a hitbox exists. */
    public boolean active() {
        return phase == Phase.ACTIVE;
    }

    public boolean busy() {
        return phase != Phase.IDLE;
    }

    /** Total steps since {@link #begin}, for driving an animation. */
    public int elapsed() {
        return elapsed;
    }

    public int stepsInPhase() {
        return inPhase;
    }

    /** True while the attacker is committed and cannot move. */
    public boolean rooted() {
        return phase != Phase.IDLE && elapsed < root;
    }

    /**
     * Whether this swing may be interrupted right now.
     *
     * <p>Recovery only. Cancelling out of the windup makes the attack free -
     * press and release costs nothing and the telegraph stops meaning anything.
     * Cancelling out of the active window erases the hitbox the player already
     * committed to. Recovery is the beat that is supposed to be the cost, and
     * paying part of it before rolling away is exactly the skill expression a
     * dodge button is for.
     */
    public boolean cancellable() {
        return phase == Phase.RECOVER;
    }

    public void cancel() {
        phase = Phase.IDLE;
        inPhase = 0;
        elapsed = 0;
        alreadyHit.clear();
    }

    /**
     * Claims a target for this swing.
     *
     * @return true the first time this target is offered, false afterwards
     */
    public boolean markHit(Object target) {
        return alreadyHit.add(target);
    }

    public boolean alreadyHit(Object target) {
        return alreadyHit.contains(target);
    }

    /** Whole length in steps, for the animation that has to cover it. */
    public int totalSteps() {
        return windup + active + recover;
    }

    private int durationOf(Phase p) {
        switch (p) {
            case WINDUP: return windup;
            case ACTIVE: return active;
            case RECOVER: return recover;
            default: return Integer.MAX_VALUE;
        }
    }

    private static Phase next(Phase p) {
        switch (p) {
            case WINDUP: return Phase.ACTIVE;
            case ACTIVE: return Phase.RECOVER;
            default: return Phase.IDLE;
        }
    }

    /** A phase of zero steps is skipped rather than lingering for one step. */
    private Phase firstNonEmpty(Phase from) {
        Phase p = from;
        while (p != Phase.IDLE && durationOf(p) <= 0) {
            p = next(p);
        }
        return p;
    }
}
