package com.kagebi.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.kagebi.combat.AttackState;
import com.kagebi.combat.Hitbox;
import com.kagebi.entity.Enemy;
import com.kagebi.entity.TestDefs;
import com.kagebi.gen.CollisionGrid;

/**
 * An {@link AiContext} that records everything a brain asks of the room.
 *
 * <p>The player is a point the test moves by hand, with the real 12x12 body
 * for overlap tests. Strikes are recorded with the state the striking enemy
 * was in at that moment, which is how "only the active window has a hitbox"
 * is checked for every brain rather than trusted.
 */
final class FakeArena implements AiContext {

    float px = 160f;
    float py = 88f;
    boolean alive = true;
    CollisionGrid grid = TestDefs.walled();
    final Random rng = new Random(99L);

    /** The enemy being stepped, so a strike can be attributed to its state. */
    Enemy current;

    final List<AiState> strikeStates = new ArrayList<>();
    int landed;
    int shots;
    final List<float[]> hazards = new ArrayList<>();
    int copies;

    private final Object playerToken = new Object();

    void step(Enemy e) {
        current = e;
        e.simulate(this);
    }

    @Override
    public float playerX() {
        return px;
    }

    @Override
    public float playerY() {
        return py;
    }

    @Override
    public boolean playerAlive() {
        return alive;
    }

    @Override
    public CollisionGrid collision() {
        return grid;
    }

    @Override
    public Random rng() {
        return rng;
    }

    @Override
    public boolean strike(Hitbox box, AttackState swing) {
        strikeStates.add(current == null ? null : current.state());
        if (!box.overlapsCentred(px, py, 12f, 12f)) {
            return false;
        }
        if (swing != null && !swing.markHit(playerToken)) {
            return false;
        }
        landed++;
        return true;
    }

    @Override
    public void fireProjectile(Enemy from, float dirX, float dirY, float speed,
                               int damage, int lifeSteps) {
        shots++;
    }

    @Override
    public void placeHazard(Enemy from, float x, float y, int damage, int armSteps,
                            int lifeSteps) {
        hazards.add(new float[] {x, y});
    }

    @Override
    public void spawnCopy(Enemy parent, float x, float y, int hp) {
        copies++;
    }
}
