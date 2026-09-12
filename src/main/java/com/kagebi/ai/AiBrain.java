package com.kagebi.ai;

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
