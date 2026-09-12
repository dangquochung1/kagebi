package com.kagebi.integration;

import com.badlogic.gdx.utils.Array;
import com.kagebi.entity.Enemy;
import com.kagebi.gen.CollisionGrid;
import com.kagebi.entity.EntityWorld;
import com.kagebi.entity.ScriptedInput;
import com.kagebi.input.GameAction;

/**
 * A player that walks at the nearest enemy and swings at it.
 *
 * <p>Deliberately stupid. It does not dodge, it does not kite, it does not use
 * a potion, and it never rolls - so anything it can finish, a person can
 * finish, and a room it cannot finish is one worth looking at. The point is
 * not to play well; it is to be the one thing no unit test in this project is:
 * a thing that presses the same buttons a person presses, for a whole run.
 *
 * <p>It drives {@link EntityWorld} through {@link ScriptedInput}, which
 * reproduces the real input buffer, so the swings it lands are landed under
 * the rules the real player plays by.
 */
final class Bot {

    /** How close to stand before swinging. Inside the starting katana's 22px reach. */
    private static final float STRIKE = 18f;

    private final EntityWorld world;
    private final ScriptedInput input = new ScriptedInput();
    private String diagnosis = "";

    Bot(EntityWorld world) {
        this.world = world;
    }

    /**
     * Why the last attempt failed, in enough detail to tell a game bug from a
     * bot that walked into a pillar. Without this the assertion says only
     * "could not clear it", which is the same message for an unkillable enemy,
     * an enemy the bot cannot reach, and an enemy that is already dead but
     * still counted - three different bugs in three different files.
     */
    String diagnosis() {
        return diagnosis;
    }

    /**
     * Fights until the room is clear.
     *
     * @return steps taken, or -1 if the budget ran out or the player died
     */
    int clearRoom(int budget) {
        float closest = Float.MAX_VALUE;
        int noProgress = 0;
        for (int step = 0; step < budget; step++) {
            if (world.playerDead()) {
                diagnosis = "the bot died with " + world.hostilesAlive()
                    + " alive: " + describeHostiles();
                return -1;
            }
            if (world.hostilesAlive() == 0) {
                // One more step so the world can notice and mark it cleared.
                input.tick(world);
                return step;
            }
            Enemy target = nearest();
            if (target != null) {
                approach(target.x, target.y, STRIKE);
            }
            input.tick(world);

            // Progress is measured as getting CLOSER, not as moving at all.
            //
            // Walking straight at a target behind a pillar does not stop the
            // bot dead - it slides along the obstacle, and so does the enemy
            // coming the other way, so both keep moving and neither ever
            // arrives. A stuck-detector watching position saw movement and was
            // satisfied; ninety seconds later a larva at 22/22 health was
            // still circling the same pillar, which read as an unwinnable room
            // and was a bot with no idea how to walk around anything.
            float d = target == null ? Float.MAX_VALUE : dist(target.x, target.y);
            if (d < closest - 1f) {
                closest = d;
                noProgress = 0;
            } else if (++noProgress > 120) {
                sidestep(step);
                closest = Float.MAX_VALUE;
                noProgress = 0;
            }
        }
        diagnosis = "ran out of time with " + world.hostilesAlive()
            + " alive: " + describeHostiles();
        return -1;
    }

    /** Twenty steps of walking along one axis, to get round whatever is in the way. */
    private void sidestep(int step) {
        release();
        GameAction way = switch ((step / 60) % 4) {
            case 0 -> GameAction.MOVE_UP;
            case 1 -> GameAction.MOVE_RIGHT;
            case 2 -> GameAction.MOVE_DOWN;
            default -> GameAction.MOVE_LEFT;
        };
        input.press(way);
        for (int i = 0; i < 20; i++) {
            input.tick(world);
        }
        input.release(way);
    }

    private String describeHostiles() {
        StringBuilder sb = new StringBuilder();
        for (Enemy e : hostiles()) {
            if (!e.alive()) {
                continue;
            }
            sb.append(String.format("%n    %s hp %d/%d at %.0f,%.0f state %s"
                + " (bot at %.0f,%.0f, %.0f away)",
                e.def.id, e.hp, e.maxHp, e.x, e.y, e.state(),
                world.playerX(), world.playerY(), dist(e.x, e.y)));
        }
        return sb.toString();
    }

    /** Walks to a point and stops. Returns false if it never arrived. */
    boolean walkTo(float tx, float ty, float within, int budget) {
        for (int step = 0; step < budget; step++) {
            if (world.playerDead()) {
                return false;
            }
            if (dist(tx, ty) <= within) {
                release();
                input.tick(world);
                return true;
            }
            approach(tx, ty, within);
            input.tick(world);
        }
        return false;
    }

    ScriptedInput input() {
        return input;
    }

    private Enemy nearest() {
        Enemy best = null;
        float bestD = Float.MAX_VALUE;
        for (Enemy e : hostiles()) {
            if (!e.alive()) {
                continue;
            }
            float d = dist(e.x, e.y);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    private Array<Enemy> hostiles() {
        return world.hostiles();
    }

    private float dist(float tx, float ty) {
        float dx = tx - world.playerX();
        float dy = ty - world.playerY();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * Holds the direction keys that lead to the target, and taps attack once in
     * range. Holding rather than tapping is what a person does, and it is also
     * what makes the movement go through the same collision slide.
     */
    private void approach(float tx, float ty, float within) {
        release();
        float dx = tx - world.playerX();
        float dy = ty - world.playerY();
        if (dist(tx, ty) > within) {
            // Follow a route round the walls when there is one, and fall back
            // to walking straight at the target when there is not - the target
            // standing in a wall, or no grid at all in a unit test.
            int[] way = route(tx, ty);
            if (way != null) {
                dx = way[0];
                dy = way[1];
            }
            if (dx > 2f) {
                input.press(GameAction.MOVE_RIGHT);
            } else if (dx < -2f) {
                input.press(GameAction.MOVE_LEFT);
            }
            if (dy > 2f) {
                input.press(GameAction.MOVE_UP);
            } else if (dy < -2f) {
                input.press(GameAction.MOVE_DOWN);
            }
        } else {
            // Face it, then swing. Facing comes from the movement keys, so a
            // bot that stopped moving entirely would swing wherever it last
            // walked - which on a chaser that circles is the wrong way.
            if (Math.abs(dx) > Math.abs(dy)) {
                input.press(dx > 0 ? GameAction.MOVE_RIGHT : GameAction.MOVE_LEFT);
            } else {
                input.press(dy > 0 ? GameAction.MOVE_UP : GameAction.MOVE_DOWN);
            }
            input.tap(GameAction.ATTACK);
        }
    }

    /**
     * A step offset towards the target that goes round the walls, or null when
     * no route exists and walking straight at it is as good as anything.
     *
     * <p>A breadth-first flood over the room's own collision grid, which is 20
     * by 11 tiles - small enough to redo every step and not worth caching. The
     * bot needs this because rooms are not open boxes: {@code normal_05} is
     * split down the middle by a wall with one gap in it, and a bot that walks
     * straight at its target circles that wall until the clock runs out while
     * the enemy circles it from the other side. Both keep moving the whole
     * time, so no amount of stuck-detection notices.
     */
    private int[] route(float tx, float ty) {
        CollisionGrid grid = world.collision();
        if (grid == null) {
            return null;
        }
        int w = grid.width();
        int h = grid.height();
        int sx = (int) (world.playerX() / CollisionGrid.TILE);
        int sy = (int) (world.playerY() / CollisionGrid.TILE);
        int gx = (int) (tx / CollisionGrid.TILE);
        int gy = (int) (ty / CollisionGrid.TILE);
        if (sx < 0 || sy < 0 || sx >= w || sy >= h || gx < 0 || gy < 0 || gx >= w || gy >= h) {
            return null;
        }
        if (sx == gx && sy == gy) {
            return null;
        }

        // Flood from the GOAL, so every reachable tile ends up holding its
        // distance to it and the bot simply walks downhill.
        int[] far = new int[w * h];
        java.util.Arrays.fill(far, Integer.MAX_VALUE);
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        far[gy * w + gx] = 0;
        queue.add(gy * w + gx);
        while (!queue.isEmpty()) {
            int at = queue.poll();
            int ax = at % w;
            int ay = at / w;
            for (int[] d : STEPS) {
                int nx = ax + d[0];
                int ny = ay + d[1];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h || grid.solidTile(nx, ny)) {
                    continue;
                }
                if (far[ny * w + nx] > far[at] + 1) {
                    far[ny * w + nx] = far[at] + 1;
                    queue.add(ny * w + nx);
                }
            }
        }
        if (far[sy * w + sx] == Integer.MAX_VALUE) {
            return null;
        }
        int best = far[sy * w + sx];
        int[] chosen = null;
        for (int[] d : STEPS) {
            int nx = sx + d[0];
            int ny = sy + d[1];
            if (nx < 0 || ny < 0 || nx >= w || ny >= h || grid.solidTile(nx, ny)) {
                continue;
            }
            if (far[ny * w + nx] < best) {
                best = far[ny * w + nx];
                chosen = d;
            }
        }
        if (chosen == null) {
            return null;
        }
        // Aim at the middle of the next tile rather than at its edge, so the
        // 12px body clears the jamb of a 16px gap instead of scraping it.
        float nextX = (sx + chosen[0] + 0.5f) * CollisionGrid.TILE;
        float nextY = (sy + chosen[1] + 0.5f) * CollisionGrid.TILE;
        return new int[] {Math.round(nextX - world.playerX()),
                          Math.round(nextY - world.playerY())};
    }

    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private void release() {
        input.release(GameAction.MOVE_UP);
        input.release(GameAction.MOVE_DOWN);
        input.release(GameAction.MOVE_LEFT);
        input.release(GameAction.MOVE_RIGHT);
    }
}
