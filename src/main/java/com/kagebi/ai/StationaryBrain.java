package com.kagebi.ai;

import com.kagebi.entity.Enemy;

/**
 * Never moves. Swings at whatever comes within reach.
 *
 * <p>For the things the art already draws as rooted - a plant, a statue, a
 * spirit anchored to its spot - and for turning a corridor into a decision.
 * Because it cannot follow, its whole contribution is denying a piece of floor,
 * which is a different pressure from anything that chases.
 *
 * <p>It still runs the full state machine. Its windup is the only warning the
 * player gets, and a stationary enemy that attacked without one would be a
 * trap, not an enemy.
 */
public final class StationaryBrain extends BaseBrain {

    @Override
    public String id() {
        return "stationary";
    }

    @Override
    protected void idle(Enemy self, AiContext ctx) {
        self.halt();
        if (ctx.playerAlive()
                && self.distanceTo(ctx.playerX(), ctx.playerY()) <= self.def.aggroRange) {
            self.setState(AiState.CHASE);
        }
    }

    @Override
    protected void chase(Enemy self, AiContext ctx) {
        self.halt();
        float dist = self.distanceTo(ctx.playerX(), ctx.playerY());
        if (dist > self.def.aggroRange) {
            self.setState(AiState.IDLE);
            return;
        }
        // Turning to face is the one thing it can do, and it matters: the swing
        // box is projected along the facing, so a rooted enemy that could not
        // turn would only ever threaten one quarter of the floor around it.
        self.facing = com.kagebi.Dir.of(ctx.playerX() - self.x, ctx.playerY() - self.y);
        if (dist <= triggerRange(self) && self.cooldown == 0) {
            beginWindup(self, ctx);
        }
    }

    @Override
    protected void approach(Enemy self, AiContext ctx, float speed) {
        self.halt();
    }

    /** Rooted: flashes to telegraph, but has nowhere to lunge back to. */
    @Override
    protected void windupStep(Enemy self, AiContext ctx) {
        self.halt();
        if (self.stateSteps() % 6 < 3) {
            self.flashSteps = 2;
        }
    }

    /** A body-attacker that cannot move strikes in place rather than lunging. */
    @Override
    protected void onAttackStep(Enemy self, AiContext ctx) {
        self.halt();
        ctx.strike(melee(self) ? meleeBox(self) : bodyBox(self, self.def.contactDamage),
            self.attack());
    }
}
