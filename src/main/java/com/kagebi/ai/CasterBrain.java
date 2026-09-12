package com.kagebi.ai;

import com.kagebi.entity.Enemy;

/**
 * Stands still, casts for a long time, and leaves something nasty on the floor.
 *
 * <p>The first enemy that punishes standing still rather than moving badly, in
 * the words of enemies.json. The cloud goes where the player was when the cast
 * <em>began</em>, and arms a beat after it appears, so a player who reacts to
 * the glow has already moved and one who does not has a moment of warning -
 * but no more than a moment.
 *
 * <p>Casters are the one enemy whose windup the player is meant to interrupt:
 * the acolyte's 90-step cast is the longest telegraph in the game. They are
 * staggerable like any trash mob, and a hit during the cast cancels it, because
 * the cloud is only placed once the windup completes.
 */
public final class CasterBrain extends BaseBrain {

    /** Steps between the cloud appearing and it starting to hurt. */
    public static final int ARM_STEPS = 14;
    /** How long a cloud lingers once armed: 2.5 seconds. */
    public static final int LINGER_STEPS = 150;
    /** Casters that find the player too close step back before casting. */
    public static final float COMFORT = 0.5f;

    @Override
    public String id() {
        return "caster";
    }

    @Override
    protected float triggerRange(Enemy self) {
        return Math.max(LUNGE_RANGE, self.def.attackRange);
    }

    @Override
    protected void chase(Enemy self, AiContext ctx) {
        float dist = self.distanceTo(ctx.playerX(), ctx.playerY());
        if (dist > self.def.aggroRange * 1.5f) {
            self.halt();
            self.setState(AiState.IDLE);
            return;
        }
        if (dist <= triggerRange(self) && self.cooldown == 0) {
            beginWindup(self, ctx);
            return;
        }
        float speed = self.def.moveSpeed * self.speedMult;
        if (dist < triggerRange(self) * COMFORT) {
            self.moveToward(ctx.collision(),
                self.x - (ctx.playerX() - self.x), self.y - (ctx.playerY() - self.y), speed);
            return;
        }
        if (dist > triggerRange(self)) {
            approach(self, ctx, speed);
        } else {
            self.halt();
        }
    }

    /** Rooted while casting; the pulse is the whole telegraph. */
    @Override
    protected void windupStep(Enemy self, AiContext ctx) {
        self.halt();
        if (self.stateSteps() % 8 < 4) {
            self.flashSteps = 2;
        }
    }

    @Override
    protected void onAttackStart(Enemy self, AiContext ctx) {
        ctx.placeHazard(self, self.targetX, self.targetY,
            Math.max(1, Math.round(self.def.attackDamage * self.damageMult)),
            ARM_STEPS, ARM_STEPS + LINGER_STEPS);
    }

    @Override
    protected void onAttackStep(Enemy self, AiContext ctx) {
        self.halt();
    }
}
