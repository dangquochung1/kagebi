package com.kagebi.ai;

import com.kagebi.Cfg;
import com.kagebi.Dir;
import com.kagebi.entity.Enemy;

/**
 * Circles at a distance, then darts in to strike.
 *
 * <p>An orbiter is a chaser that refuses to be a chaser. It holds a ring around
 * the player just outside sword reach, so the player cannot simply swing at it
 * as it arrives; they have to either chase it round the ring or wait for the
 * dart and punish the recovery. That waiting is the lesson - floor 2 is where
 * the game starts rewarding patience.
 */
public final class OrbiterBrain extends BaseBrain {

    /** Ring radius as a multiple of attack range: kappagreen's 26 gives 44px. */
    public static final float RING = 1.7f;
    /** Steps on the ring before it may dart, so the orbit is seen before the attack. */
    public static final int ORBIT_MIN = 45;

    @Override
    public String id() {
        return "orbiter";
    }

    @Override
    public void onSpawn(Enemy self) {
        // Deterministic from the spawn point, so a room replays identically.
        self.aiSign = ((((int) self.x) * 31 + (int) self.y) & 1) == 0 ? 1f : -1f;
    }

    private static float ring(Enemy self) {
        return Math.max(LUNGE_RANGE, self.def.attackRange * RING);
    }

    @Override
    protected void chase(Enemy self, AiContext ctx) {
        float dist = self.distanceTo(ctx.playerX(), ctx.playerY());
        if (dist > self.def.aggroRange * 1.5f) {
            self.halt();
            self.aiTimer = 0;
            self.setState(AiState.IDLE);
            return;
        }
        float r = ring(self);
        float speed = self.def.moveSpeed * self.speedMult;
        if (dist > r + 12f) {
            approach(self, ctx, speed);
            return;
        }
        if (self.aiTimer >= ORBIT_MIN && self.cooldown == 0) {
            beginWindup(self, ctx);
            return;
        }
        self.aiTimer++;
        float dx = self.x - ctx.playerX();
        float dy = self.y - ctx.playerY();
        float len = Math.max(0.001f, dist);
        float rx = dx / len;
        float ry = dy / len;
        // Tangent plus a radial term pulling back to the ring. Without the
        // radial correction the orbit spirals outward, because each tangent
        // step is taken from the previous point on the circle.
        float radial = (r - dist) / r;
        float mx = -ry * self.aiSign + rx * radial;
        float my = rx * self.aiSign + ry * radial;
        float ml = (float) Math.sqrt(mx * mx + my * my);
        if (ml > 0.001f && !self.moveDir(ctx.collision(), mx / ml, my / ml, speed)) {
            self.aiSign = -self.aiSign;     // hit a wall: go the other way round
        }
        self.facing = Dir.of(-dx, -dy);
    }

    @Override
    protected void onAttackStart(Enemy self, AiContext ctx) {
        lockAim(self, ctx.playerX(), ctx.playerY());
    }

    @Override
    protected void onAttackStep(Enemy self, AiContext ctx) {
        // The dart covers the ring's width over the active window, so it ends
        // where the player was standing when it left.
        float speed = ring(self) / (Math.max(1, self.def.activeSteps) * Cfg.STEP);
        self.moveDir(ctx.collision(), self.aimX, self.aimY, speed);
        ctx.strike(melee(self) ? meleeBox(self) : bodyBox(self, self.def.contactDamage),
            self.attack());
    }

    @Override
    protected void onRecoverStep(Enemy self, AiContext ctx) {
        self.halt();
        self.aiTimer = 0;
    }
}
