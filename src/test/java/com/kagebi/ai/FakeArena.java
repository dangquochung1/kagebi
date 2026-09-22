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
    int lobs;
    int homing;
    int rained;
    int effects;
    final List<String> summoned = new ArrayList<>();

    private final Object playerToken = new Object();

    /** Where the player was at the previous step, so their drift can be read. */
    private float lastPx = px;
    private float lastPy = py;
    private float velX;
    private float velY;

    void step(Enemy e) {
        // Differenced here for the same reason EntityWorld differences it
        // there: a test that sets px and py directly is exactly a player who
        // moved, and nothing else in this class would know by how much.
        velX = (px - lastPx) / com.kagebi.Cfg.STEP;
        velY = (py - lastPy) / com.kagebi.Cfg.STEP;
        lastPx = px;
        lastPy = py;
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
    public float playerVelX() {
        return velX;
    }

    @Override
    public float playerVelY() {
        return velY;
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
    public void fireProjectile(Enemy from, float dirX, float dirY, float speed,
                               int damage, int lifeSteps, String fx) {
        fireProjectile(from, dirX, dirY, speed, damage, lifeSteps);
    }

    @Override
    public void placeHazard(Enemy from, float x, float y, int damage, int armSteps,
                            int lifeSteps, String fx) {
        placeHazard(from, x, y, damage, armSteps, lifeSteps);
    }

    /**
     * Counted as a hazard where it lands, because that is what it becomes.
     *
     * <p>The arc itself is Projectile's business and is tested there; what a
     * brain is responsible for is choosing the point, so that is what the
     * arena records.
     */
    @Override
    public void lobProjectile(Enemy from, float toX, float toY, int damage,
                              int flightSteps, int lingerSteps, String fx) {
        lobs++;
        hazards.add(new float[] {toX, toY});
    }

    /**
     * Recorded as a shot, and separately as a homing one.
     *
     * <p>The steering itself belongs to Projectile and is asserted there. What
     * a brain decides is whether a shot homes at all, which is the only thing
     * worth pinning from here.
     */
    @Override
    public void homingProjectile(Enemy from, float dirX, float dirY, float speed,
                                 int damage, int lifeSteps, int homeSteps,
                                 float turnRate, String fx) {
        homing++;
        fireProjectile(from, dirX, dirY, speed, damage, lifeSteps);
    }

    /** Counted where it lands, as a hazard is: the fall is Projectile's business. */
    @Override
    public void rainSpell(Enemy from, float x, float y, int damage,
                          int fallSteps, int lingerSteps, String fx) {
        rained++;
        hazards.add(new float[] {x, y});
    }

    @Override
    public void spawnCopy(Enemy parent, float x, float y, int hp) {
        copies++;
    }

    @Override
    public Enemy summon(String enemyId, float x, float y, boolean dormant) {
        summoned.add(enemyId);
        return null;
    }

    @Override
    public void spawnFx(String fx, float x, float y, boolean overhead) {
        effects++;
    }
}
