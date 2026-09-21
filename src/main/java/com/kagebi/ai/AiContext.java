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

    /**
     * As above, drawn with a named effect instead of the default cloud.
     *
     * @param fx an {@code Assets.Fx} region name, or null for the default. A
     *           name and not an {@code Anim} because a brain has no atlas and
     *           no GL context; the world resolves it, and one it does not
     *           recognise falls back to the cloud rather than failing.
     */
    void placeHazard(Enemy from, float x, float y, int damage, int armSteps,
                     int lifeSteps, String fx);

    /** As {@link #fireProjectile}, drawn with a named effect. */
    void fireProjectile(Enemy from, float dirX, float dirY, float speed,
                        int damage, int lifeSteps, String fx);

    /**
     * As above, but it steers at the player for a while before committing.
     *
     * <p>Bounded on purpose, and both bounds are what make it fair. It may
     * only turn {@code turnRate} radians a step, so running is an answer; and
     * after {@code homeSteps} it stops steering entirely and is an ordinary
     * shot, so standing still and waiting it out is not.
     *
     * @param homeSteps steps of steering before it flies straight
     * @param turnRate  radians it may turn per step
     */
    void homingProjectile(Enemy from, float dirX, float dirY, float speed,
                          int damage, int lifeSteps, int homeSteps, float turnRate,
                          String fx);

    /**
     * Throws something that arcs to a point and leaves an area where it lands.
     *
     * <p>The one attack in the game that ignores walls on the way, because it
     * is in the air. That is what lets a fountain of water spells read as a
     * fountain rather than as a volley that stops at the first pillar.
     *
     * @param flightSteps how long the arc takes, which also sets its height
     */
    void lobProjectile(Enemy from, float toX, float toY, int damage,
                       int flightSteps, int lingerSteps, String fx);

    /**
     * Drops a spell out of the sky onto a point, which burns where it lands.
     *
     * <p>Distinct from {@link #placeHazard}, which puts the burning straight
     * on the floor behind a blinking warning. That reads as something coming
     * up out of the ground; rain has to come down, and the descent is its own
     * warning - the player can see where every drop is going for the whole of
     * its fall.
     *
     * @param fallSteps how long the drop takes, which also sets how high it
     *                  starts and therefore how much warning it gives
     */
    void rainSpell(Enemy from, float x, float y, int damage,
                   int fallSteps, int lingerSteps, String fx);

    /** A copy of {@code parent} with the given hit points and one generation older. */
    void spawnCopy(Enemy parent, float x, float y, int hp);

    /**
     * Puts a different enemy in the room, now.
     *
     * <p>Distinct from {@link #spawnCopy}, which can only clone the caller.
     * This is what lets a boss turn into the next thing rather than merely
     * dying, and what lets one call for help.
     *
     * <p><b>There is deliberately no delay.</b> A summon owed to the room in
     * N steps is a summon the room does not know about, and the room latches
     * itself cleared the moment nothing hostile is left in it: the doors open,
     * the stairs light, and the rest of the chain never happens. Anything that
     * should appear later is spawned now, dormant, with an effect over it.
     *
     * @param dormant true to spawn it asleep, for a summon whose effect plays
     *                before it acts
     * @return the new enemy, or null if this build has no such id
     */
    Enemy summon(String enemyId, float x, float y, boolean dormant);

    /** Plays a one-shot effect at a point. Cosmetic; nothing depends on it. */
    void spawnFx(String fx, float x, float y, boolean overhead);
}
