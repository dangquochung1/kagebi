package com.kagebi.ai;

import com.kagebi.data.def.EnemyDef;
import com.kagebi.entity.Enemy;

/**
 * Behaviour, shared by every enemy that names it.
 *
 * <p>Brains are stateless and held one per id, not one per enemy: every
 * per-instance counter lives on {@link Enemy}. That keeps a room of fifteen
 * slimes to one brain object, and it turns an accidentally-remembered field
 * into something visible in review rather than two slimes sharing a cooldown.
 */
public interface AiBrain {

    /** Matches {@code EnemyDef.brain}. */
    String id();

    /** Runs one fixed step of this enemy's state machine. */
    void think(Enemy self, AiContext ctx);

    /** Once, when the enemy is placed in the room. */
    default void onSpawn(Enemy self) {
    }

    /** Once, on the step hit points reach zero. Splitters split here. */
    default void onDeath(Enemy self, AiContext ctx) {
    }

    /**
     * Once, on the step a boss falls past {@code EnemyDef.enrageAt}.
     *
     * <p>Where the second half of a boss fight begins. {@link Boss} handles
     * the numbers - it is already faster and hitting harder by the time this
     * runs - and this is for the part that is not a multiplier: the wave of
     * fire that falls, the adds that are called in. A boss whose second half
     * is only bigger numbers reads as the fight getting longer rather than
     * changing, which is the same reason BossBrain keeps two move pools.
     */
    default void onEnrage(Enemy self, AiContext ctx) {
    }

    /**
     * The longest active window this brain will ever ask for, given a def.
     *
     * <p>Almost always {@code def.activeSteps}: one enemy, one attack, one
     * number. A boss has several attacks and they are not the same length - a
     * swing is a sixth of a second and a three-blow combo is nearly three - so
     * {@code BossBrain} sizes each move for itself and declares the ceiling
     * here.
     *
     * <p>It exists to be asserted. "No state outlives the number in the def"
     * was a real invariant and is how a brain that forgets to leave a state it
     * entered gets caught; widening it silently would have thrown that away,
     * so the widening is stated instead, and AiStateMachineTest holds every
     * brain to whatever it says here.
     */
    default int longestActiveSteps(EnemyDef def) {
        return Math.max(1, def.activeSteps);
    }

    /**
     * Whether touching this enemy hurts right now.
     *
     * <p>Not always. enemies.json describes the slime as "it telegraphs, it
     * lands, and there is half a second afterwards in which it cannot hurt
     * you" - and that promise only holds if contact damage respects the state
     * machine rather than being a permanent aura around every body.
     */
    /**
     * Makes this one lose track of the player for a while, as a smoke bomb
     * does. Default does nothing, because a brain that never chases has nothing
     * to forget.
     */
    default void forget(Enemy self, int steps) {
        self.distract(steps);
    }

    default boolean harmfulOnContact(Enemy self) {
        return true;
    }
}
