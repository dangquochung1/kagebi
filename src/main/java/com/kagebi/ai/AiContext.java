package com.kagebi.ai;

import java.util.Random;

import com.kagebi.combat.AttackState;
import com.kagebi.combat.Hitbox;
import com.kagebi.entity.Enemy;
import com.kagebi.gen.CollisionGrid;

/**
 * The room, as a brain is allowed to see it.
 *
 * <p>None of it touches libGDX. {@code EntityWorld} implements it for real; a
 * unit test implements it in thirty lines, which is why every brain in this
 * package can be driven for thousands of steps in plain JUnit and asserted not
 * to wedge.
 *
 * <p>What is absent matters as much. A brain cannot see the entity list, cannot
 * move anything but itself, and has no pathfinder: rooms are 20x11 and convex,
 * so chase-and-slide arrives everywhere A* would, at none of the cost.
 */
public interface AiContext {

    float playerX();

    float playerY();

    boolean playerAlive();

    /** Solid tiles, or null in a test with no room around the fight. */
    CollisionGrid collision();

    /** Seeded from the run and the room, so a fight replays identically. */
    Random rng();

    /**
     * Offers a hitbox to the player.
     *
     * @param swing the attack it belongs to, so one swing cannot hit twice
     * @return true if it actually connected
     */
    boolean strike(Hitbox box, AttackState swing);

    /**
     * @param speed virtual pixels per second
     * @param lifeSteps how long before it expires, in fixed steps
     */
    void fireProjectile(Enemy from, float dirX, float dirY, float speed,
                        int damage, int lifeSteps);

    /**
     * Leaves a damaging area on the floor.
     *
     * @param armSteps harmless steps before it bites, so an area placed under
     *                 the player is a warning rather than an unavoidable hit
     */
    void placeHazard(Enemy from, float x, float y, int damage, int armSteps, int lifeSteps);

    /** A copy of {@code parent} with the given hit points and one generation older. */
    void spawnCopy(Enemy parent, float x, float y, int hp);
}
