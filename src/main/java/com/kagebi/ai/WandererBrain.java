package com.kagebi.ai;

import com.kagebi.entity.Enemy;

/**
 * Drifts around the room and only notices the player up close.
 *
 * <p>The point of it is contrast. A room where every enemy beelines at the
 * player is one shape of fight repeated; a couple of wanderers turn the same
 * room into terrain the player has to route through, and they make the chasers
 * read as deliberately aggressive rather than as the only thing the AI does.
 *
 * <p>Its aggro range is deliberately a fraction of the def's: a wanderer that
 * aggros at the same distance as a chaser is a chaser with extra steps.
 */
public final class WandererBrain extends BaseBrain {

    /** Steps a heading is held before rerolling: 0.75 to 1.75s. */
    private static final int MIN_LEG = 45;
    private static final int LEG_SPREAD = 60;

    /** Fraction of {@code aggroRange} at which a wanderer actually looks up. */
    private static final float NOTICE = 0.45f;

    @Override
    public String id() {
        return "wanderer";
    }

    @Override
    protected void idle(Enemy self, AiContext ctx) {
        if (self.aiTimer <= 0) {
            double angle = ctx.rng().nextDouble() * Math.PI * 2.0;
            self.aiX = (float) Math.cos(angle);
            self.aiY = (float) Math.sin(angle);
            self.aiTimer = MIN_LEG + ctx.rng().nextInt(LEG_SPREAD);
        }
        self.aiTimer--;
        // Walking into a wall rerolls immediately rather than after the leg
        // runs out, or a wanderer spends a second grinding into the scenery.
        if (!self.moveDir(ctx.collision(), self.aiX, self.aiY,
                self.def.moveSpeed * 0.5f * self.speedMult)) {
            self.aiTimer = 0;
        }
        if (ctx.playerAlive() && self.distanceTo(ctx.playerX(), ctx.playerY())
                <= self.def.aggroRange * NOTICE) {
            self.setState(AiState.CHASE);
        }
    }

    @Override
    protected void chase(Enemy self, AiContext ctx) {
        // Loses interest at the range it would have noticed from, not at the
        // full aggro range, so a wanderer that gave chase can be walked away
        // from instead of being followed across the floor.
        if (self.distanceTo(ctx.playerX(), ctx.playerY()) > self.def.aggroRange) {
            self.aiTimer = 0;
            self.setState(AiState.IDLE);
            return;
        }
        super.chase(self, ctx);
    }
}
