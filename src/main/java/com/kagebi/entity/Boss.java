package com.kagebi.entity;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.kagebi.ai.AiBrain;
import com.kagebi.ai.AiState;
import com.kagebi.data.def.EnemyDef;
import com.kagebi.gfx.Anim;

/**
 * A floor boss: an {@link Enemy} that changes gear partway down its health bar.
 *
 * <p>{@code EnemyDef.phases} is the whole feature. Splitting the bar into equal
 * parts and stepping the numbers up at each boundary is a cheap trick and it
 * works, because what the player actually reads is the interruption: the boss
 * stops, becomes untouchable, plays something, and comes back faster. The
 * transformation is the message, not the multiplier.
 *
 * <p>{@code bosses/tengured/trans.png} is an eleven-frame strip drawn for
 * exactly this, and it is the reason the pause is animated rather than a fade.
 * Bosses without one - giantfrog2 among them - hold a flashing idle for the
 * same number of steps, so the beat still lands.
 */
public final class Boss extends Enemy {

    /** How long a transformation holds when the boss ships no {@code trans} strip. */
    public static final int DEFAULT_TRANSFORM_STEPS = 60;

    /** Per phase beyond the first. Modest on purpose: legibility over lethality. */
    public static final float PHASE_SPEED_STEP = 0.25f;
    public static final float PHASE_DAMAGE_STEP = 0.25f;

    private final Anim transformation;

    /** 1-based. Reaches {@code def.phases} and stops. */
    private int phase = 1;
    private boolean transforming;
    private int transformSteps;

    /** Which attack the brain chose for the current windup. */
    public int move;

    public Boss(EnemyDef def, AiBrain brain, ActorSprites sprites, Anim transformation) {
        super(def, brain, sprites);
        this.transformation = transformation;
    }

    public int phase() {
        return phase;
    }

    public boolean transforming() {
        return transforming;
    }

    /**
     * Health fraction at which the next phase begins.
     *
     * <p>Equal slices: a two-phase boss turns at half, a three-phase one at two
     * thirds and one third. Uneven thresholds are a balance decision and belong
     * in data, not in a constant here - see {@code notes/b.md}.
     */
    public float nextThreshold() {
        if (phase >= def.phases) {
            return -1f;
        }
        return (def.phases - phase) / (float) def.phases;
    }

    @Override
    public void step(EntityWorld world) {
        if (transforming) {
            stepTimers();
            // Untouchable and motionless. Letting the player keep hitting
            // through a transformation makes it a free damage window, which is
            // the opposite of the beat it is supposed to be.
            iframes.grant(2);
            flashSteps = transformSteps % 8 < 4 ? 2 : 0;
            transformSteps--;
            if (transformSteps <= 0) {
                transforming = false;
                enterNextPhase();
            }
            return;
        }
        if (alive() && nextThreshold() >= 0f
                && hp <= maxHp * nextThreshold()) {
            beginTransformation();
            return;
        }
        super.step(world);
    }

    private void beginTransformation() {
        transforming = true;
        transformSteps = transformation != null
            ? transformation.durationSteps() : DEFAULT_TRANSFORM_STEPS;
        attack().cancel();
        shove.clear();
        setState(AiState.IDLE);
        halt();
    }

    private void enterNextPhase() {
        phase++;
        speedMult = 1f + (phase - 1) * PHASE_SPEED_STEP;
        damageMult = 1f + (phase - 1) * PHASE_DAMAGE_STEP;
        cooldown = 0;
        setState(AiState.CHASE);
    }

    @Override
    public TextureRegion frame() {
        if (transforming && transformation != null) {
            int total = Math.max(1, transformation.durationSteps());
            return transformation.frame(facing, Math.max(0, total - transformSteps));
        }
        return super.frame();
    }
}
