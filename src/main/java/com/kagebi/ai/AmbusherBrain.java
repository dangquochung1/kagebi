package com.kagebi.ai;

import com.kagebi.Cfg;
import com.kagebi.entity.Enemy;

/**
 * Sits as scenery until the player is close, then pounces.
 *
 * <p>Dormant means dormant: not moving, not animating, not hurting on contact.
 * An ambusher that bobbed gently in place would give itself away, and one that
 * hurt to brush past while still pretending to be a rock would be a trap rather
 * than an enemy. The reveal is its own telegraph - the flash as it wakes is the
 * warning - and the pounce is aimed at where the player stood when it woke.
 *
 * <p>After the first pounce it fights as an ordinary chaser. The ambush is the
 * interesting part; repeating it would make it a hopper with extra steps.
 */
public final class AmbusherBrain extends BaseBrain {

    /** The furthest a pounce carries. From further out it lands short. */
    public static final float POUNCE_MAX = 56f;

    /** {@code aiTimer} value meaning the opening pounce has not happened yet. */
    private static final int POUNCE_PENDING = 1;

    @Override
    public String id() {
        return "ambusher";
    }

    @Override
    public void onSpawn(Enemy self) {
        self.dormant = true;
        self.aiTimer = POUNCE_PENDING;
    }

    private static boolean pouncing(Enemy self) {
        return self.aiTimer == POUNCE_PENDING;
    }

    @Override
    protected void idle(Enemy self, AiContext ctx) {
        if (!self.dormant) {
            super.idle(self, ctx);
            return;
        }
        self.halt();
        if (ctx.playerAlive()
                && self.distanceTo(ctx.playerX(), ctx.playerY()) <= self.def.aggroRange) {
            self.dormant = false;
            beginWindup(self, ctx);
        }
    }

    @Override
    protected void windupStep(Enemy self, AiContext ctx) {
        if (pouncing(self)) {
            self.halt();
            if (self.stateSteps() % 4 < 2) {
                self.flashSteps = 2;
            }
            return;
        }
        super.windupStep(self, ctx);
    }

    @Override
    protected void onAttackStart(Enemy self, AiContext ctx) {
        if (!pouncing(self)) {
            super.onAttackStart(self, ctx);
            return;
        }
        lockAim(self, self.targetX, self.targetY);
        if (self.distanceTo(self.targetX, self.targetY) > POUNCE_MAX) {
            self.targetX = self.x + self.aimX * POUNCE_MAX;
            self.targetY = self.y + self.aimY * POUNCE_MAX;
        }
    }

    @Override
    protected void onAttackStep(Enemy self, AiContext ctx) {
        if (!pouncing(self)) {
            super.onAttackStep(self, ctx);
            return;
        }
        int left = Math.max(1, self.def.activeSteps - self.attack().elapsed());
        float remaining = self.distanceTo(self.targetX, self.targetY);
        if (remaining > 0.5f) {
            self.moveToward(ctx.collision(), self.targetX, self.targetY,
                remaining / (left * Cfg.STEP));
        }
        ctx.strike(melee(self) ? meleeBox(self) : bodyBox(self, self.def.contactDamage),
            self.attack());
    }

    @Override
    protected void onRecoverStep(Enemy self, AiContext ctx) {
        self.halt();
        self.aiTimer = 0;
    }
}
