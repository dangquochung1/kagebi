package com.kagebi.entity;

import com.kagebi.input.GameAction;

/**
 * An {@link ActionSource} that swings on a timer, for screenshots.
 *
 * <p>Art direction is the one thing no test can check, and a swing lasts
 * eighteen steps out of every run - so the held weapon, the slash arc and the
 * hit flash were all unreviewable. A screenshot of an idle player says nothing
 * about them, and the only alternative was to press the key by hand while the
 * capture ran, which is both unrepeatable and the sort of thing that leaks into
 * whatever window has focus.
 *
 * <p>Deliberately not a bot. It presses one button on a fixed cadence and
 * cannot move, so it proves nothing about play and is useless as a test. Its
 * whole job is to make one frame of the game reachable by a camera.
 */
public final class DemoInput implements ActionSource {

    /** Steps between swings. Longer than any weapon's recovery, so none is cut. */
    public static final int PERIOD = 48;

    /** Stop this far short of the target, inside the starting katana's reach. */
    private static final float STRIKE = 16f;
    /**
     * Where to stand to throw. Well outside melee, because a kunai spawned on
     * top of its target is a projectile nobody can photograph in flight.
     */
    private static final float THROW_FROM = 90f;

    private final GameAction action;
    private final float strike;
    private int steps;
    private int moveX;
    private int moveY;

    public DemoInput(GameAction action) {
        this.action = action;
        this.strike = action == GameAction.THROW ? THROW_FROM : STRIKE;
    }

    /**
     * Walks at the nearest living enemy and stops in reach of it.
     *
     * <p>Without this the demo swings at empty air, which shows the blade and
     * nothing else - no hit flash, no damage number, no health bar. Those are
     * the things worth photographing, and every one of them needs a swing that
     * actually connects.
     */
    public void aimAt(Player player, Iterable<Enemy> enemies) {
        moveX = 0;
        moveY = 0;
        if (player == null) {
            return;
        }
        Enemy best = null;
        float bestD = Float.MAX_VALUE;
        for (Enemy e : enemies) {
            if (!e.alive()) {
                continue;
            }
            float dx = e.x - player.x;
            float dy = e.y - player.y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        if (best == null) {
            return;
        }
        float dx = best.x - player.x;
        float dy = best.y - player.y;
        // Face it whether or not we still need to close, so the swing lands on
        // the side the target is actually on.
        boolean far = bestD > strike;
        if (Math.abs(dx) > Math.abs(dy)) {
            moveX = dx > 0 ? 1 : -1;
            moveY = far && Math.abs(dy) > 4f ? (dy > 0 ? 1 : -1) : 0;
        } else {
            moveY = dy > 0 ? 1 : -1;
            moveX = far && Math.abs(dx) > 4f ? (dx > 0 ? 1 : -1) : 0;
        }
        if (!far) {
            // In reach: keep the facing key, drop the other, and stop closing.
            if (Math.abs(dx) > Math.abs(dy)) {
                moveY = 0;
            } else {
                moveX = 0;
            }
        }
    }

    /** Advances the clock. Call once per fixed step, before stepping the world. */
    public void tick() {
        steps++;
    }

    /** Steps since the last press began, which is where in the swing we are. */
    public int sincePress() {
        return steps % PERIOD;
    }

    @Override
    public boolean isDown(GameAction a) {
        return switch (a) {
            case MOVE_RIGHT -> moveX > 0;
            case MOVE_LEFT -> moveX < 0;
            case MOVE_UP -> moveY > 0;
            case MOVE_DOWN -> moveY < 0;
            default -> false;
        };
    }

    @Override
    public boolean buffered(GameAction a, int window) {
        return a == action && sincePress() < window;
    }

    @Override
    public void consume(GameAction a) {
    }
}
