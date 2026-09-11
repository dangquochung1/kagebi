package com.kagebi.ai;

import com.kagebi.Dir;
import com.kagebi.entity.Enemy;

/**
 * Keeps its distance and throws things.
 *
 * <p>A shooter that walks into melee range is just a slow chaser, so the chase
 * here is two-sided: it closes to {@code attackRange} and backs off if the
 * player gets inside {@link #COMFORT} of it. That is what forces the player to
 * commit to closing the gap instead of standing still and trading.
 *
 * <p>It fires exactly once, on the step the active window opens, rather than on
 * every step of it. Firing per step turns {@code activeSteps} into a magazine
 * size nobody wrote down.
 */
public class ShooterBrain extends BaseBrain {

    /** Inside this fraction of attack range the shooter retreats. */
    public static final float COMFORT = 0.6f;

    /** Virtual pixels per second: slow enough to sidestep, fast enough to respect. */
    public static final float PROJECTILE_SPEED = 90f;

    /** 2 seconds: long enough to cross a room, short enough not to pile up. */
    public static final int PROJECTILE_LIFE = 120;

    @Override
    public String id() {
        return "shooter";
    }

    @Override
    protected void chase(Enemy self, AiContext ctx) {
        float dist = self.distanceTo(ctx.playerX(), ctx.playerY());
        if (dist > self.def.aggroRange * 1.5f) {
            self.halt();
            self.setState(AiState.IDLE);
            return;
        }
        if (dist <= self.def.attackRange && self.cooldown == 0) {
            beginWindup(self, ctx);
            return;
        }
        float speed = self.def.moveSpeed * self.speedMult;
        if (dist < self.def.attackRange * COMFORT) {
            // Backing away still faces the player, so the shot that follows
            // goes where the player expects it to.
            self.moveToward(ctx.collision(),
                self.x - (ctx.playerX() - self.x), self.y - (ctx.playerY() - self.y), speed);
            self.facing = Dir.of(ctx.playerX() - self.x, ctx.playerY() - self.y);
            return;
        }
        if (dist > self.def.attackRange) {
            approach(self, ctx, speed);
        } else {
            self.halt();
        }
    }

    @Override
    protected void onAttackStart(Enemy self, AiContext ctx) {
        lockAim(self, ctx.playerX(), ctx.playerY());
        ctx.fireProjectile(self, self.aimX, self.aimY, PROJECTILE_SPEED,
            Math.max(1, Math.round(self.def.attackDamage * self.damageMult)),
            PROJECTILE_LIFE);
    }

    /** No melee box: the projectile is the attack. */
    @Override
    protected void onAttackStep(Enemy self, AiContext ctx) {
        self.halt();
    }
}
