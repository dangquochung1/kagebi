package com.kagebi.ai;

import com.kagebi.Dir;
import com.kagebi.entity.Enemy;

/**
 * Weaves in on a sine and either dives or shoots.
 *
 * <p>The weave is what makes a flyer a different problem from a chaser: its
 * lateral position is never where a straight swing is aimed, so the player has
 * to wait for it to commit. Bats and wraiths have no attack range in
 * enemies.json and dive with their body; the spirit has 110 pixels of range and
 * shoots from the edge of it.
 *
 * <p>{@code EnemyDef.flying} is documented as ignoring floor hazards and pits,
 * not walls, and {@code CollisionGrid} has no pits yet - so a flyer still
 * collides like anything else. See {@code notes/b.md}.
 */
public final class FlyerBrain extends BaseBrain {

    /** Lateral swing as a fraction of forward speed. */
    public static final float WEAVE = 0.8f;
    /** Steps per full weave. Under a second, so it reads as flight rather than drift. */
    public static final int WEAVE_PERIOD = 48;
    /** Divers commit from a little further out than a walker's lunge. */
    public static final float DIVE_RANGE = LUNGE_RANGE * 1.4f;

    @Override
    public String id() {
        return "flyer";
    }

    @Override
    public void onSpawn(Enemy self) {
        // Half the flock weaves out of phase, decided by spawn point so a room
        // replays identically. A flock weaving in step reads as one sprite.
        self.aiSign = ((((int) self.x) * 31 + (int) self.y) & 1) == 0 ? 1f : -1f;
    }

    /** Has a real ranged attack rather than a body. */
    private static boolean ranged(Enemy self) {
        return self.def.attackRange > DIVE_RANGE && self.def.attackDamage > 0;
    }

    @Override
    protected float triggerRange(Enemy self) {
        return ranged(self) ? self.def.attackRange : DIVE_RANGE;
    }

    @Override
    protected void approach(Enemy self, AiContext ctx, float speed) {
        float dx = ctx.playerX() - self.x;
        float dy = ctx.playerY() - self.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) {
            self.halt();
            return;
        }
        float ux = dx / len;
        float uy = dy / len;
        double phase = (self.animSteps + (self.aiSign > 0 ? 0 : WEAVE_PERIOD / 2))
            * (Math.PI * 2.0 / WEAVE_PERIOD);
        float side = (float) Math.sin(phase) * WEAVE;
        float mx = ux - uy * side;
        float my = uy + ux * side;
        float ml = (float) Math.sqrt(mx * mx + my * my);
        self.moveDir(ctx.collision(), mx / ml, my / ml, speed);
    }

    @Override
    protected void onAttackStart(Enemy self, AiContext ctx) {
        lockAim(self, ctx.playerX(), ctx.playerY());
        if (ranged(self)) {
            ctx.fireProjectile(self, self.aimX, self.aimY, ShooterBrain.PROJECTILE_SPEED,
                Math.max(1, Math.round(self.def.attackDamage * self.damageMult)),
                ShooterBrain.PROJECTILE_LIFE);
        }
    }

    @Override
    protected void onAttackStep(Enemy self, AiContext ctx) {
        if (ranged(self)) {
            self.halt();
            self.facing = Dir.of(ctx.playerX() - self.x, ctx.playerY() - self.y);
            return;
        }
        super.onAttackStep(self, ctx);
    }
}
