package com.kagebi.ai;

import com.kagebi.Cfg;
import com.kagebi.entity.Enemy;

/**
 * Lines up, then commits to a straight dash.
 *
 * <p>This is the brain the telegraph exists for. The windup is long, the dash
 * is fast, and its direction is locked the moment it starts - so the player who
 * read the windup and stepped aside is rewarded, and the one who did not is
 * hit. A charger that steered mid-dash would make the telegraph decorative,
 * which is worse than having none.
 *
 * <p>It commits from further out than it can swing: the trigger is its attack
 * range plus the distance the dash covers, so a dash started at the edge of
 * that range ends where the player was standing. It also stops dead on the
 * first thing it cannot move through and is stunned through its recovery.
 * Slamming into a wall has to cost something, or there is no reason to dodge
 * rather than tank.
 */
public final class ChargerBrain extends BaseBrain {

    /**
     * Multiplier on the def's walking speed while dashing. 2.6 is the number
     * enemies.json was written against: kappared walks at 30 and "charges at
     * roughly 2.6x, so 78 - just fast enough to matter and just slow enough to
     * roll out of".
     */
    public static final float DASH_SPEED = 2.6f;

    /** Extra steps of stun after hitting a wall, on top of the def's recovery. */
    public static final int WALL_STUN = 20;

    @Override
    public String id() {
        return "charger";
    }

    /** How far one dash travels, in pixels. */
    public static float dashDistance(Enemy self) {
        return self.def.moveSpeed * DASH_SPEED * self.speedMult
            * Math.max(1, self.def.activeSteps) * Cfg.STEP;
    }

    @Override
    protected float triggerRange(Enemy self) {
        return Math.max(self.def.attackRange, LUNGE_RANGE) + dashDistance(self);
    }

    @Override
    protected void onAttackStart(Enemy self, AiContext ctx) {
        // Locked here and never recomputed. That is the whole contract with the
        // player: what the windup showed is what the dash does.
        lockAim(self, ctx.playerX(), ctx.playerY());
        self.aiTimer = 0;
    }

    @Override
    protected void onAttackStep(Enemy self, AiContext ctx) {
        boolean moved = self.moveDir(ctx.collision(), self.aimX, self.aimY,
            self.def.moveSpeed * DASH_SPEED * self.speedMult);
        if (!moved) {
            self.aiTimer = WALL_STUN;
            self.attack().cancel();
            self.cooldown = self.def.cooldownSteps + WALL_STUN;
            self.setState(AiState.RECOVER);
            return;
        }
        ctx.strike(melee(self) ? meleeBox(self) : bodyBox(self, self.def.contactDamage),
            self.attack());
    }

    @Override
    protected void onRecoverStep(Enemy self, AiContext ctx) {
        self.halt();
        if (self.aiTimer > 0) {
            self.aiTimer--;
            // The stun pulses so the player can see the opening rather than
            // having to count frames to find it.
            if (self.aiTimer % 8 < 4) {
                self.flashSteps = 2;
            }
        }
    }
}
