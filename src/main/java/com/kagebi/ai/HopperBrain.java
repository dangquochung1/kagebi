package com.kagebi.ai;

import com.kagebi.Cfg;
import com.kagebi.entity.Enemy;

/**
 * Creeps, squats, and hops onto where the player was standing.
 *
 * <p>Written for the slime, whose entry in enemies.json is the clearest spec in
 * the file: "it telegraphs, it lands, and there is half a second afterwards in
 * which it cannot hurt you". All three beats are here. The landing point is
 * locked when the squat begins, so stepping aside during the squat is always
 * enough; the hop lands exactly on that point at the end of the active window,
 * however far it was; and the recovery that follows is harmless to touch.
 */
public final class HopperBrain extends BaseBrain {

    /** Distance at which it squats to hop. About three and a half tiles. */
    public static final float HOP_RANGE = 56f;
    /** The longest a single hop carries. A hop from further out falls short. */
    public static final float HOP_MAX = 44f;

    @Override
    public String id() {
        return "hopper";
    }

    @Override
    protected float triggerRange(Enemy self) {
        return Math.min(self.def.aggroRange, HOP_RANGE);
    }

    /** A squat, not a lunge back: the hop is the movement. */
    @Override
    protected void windupStep(Enemy self, AiContext ctx) {
        self.halt();
        if (self.stateSteps() % 6 < 3) {
            self.flashSteps = 2;
        }
    }

    @Override
    protected void onAttackStart(Enemy self, AiContext ctx) {
        lockAim(self, self.targetX, self.targetY);
        float dist = self.distanceTo(self.targetX, self.targetY);
        if (dist > HOP_MAX) {
            self.targetX = self.x + self.aimX * HOP_MAX;
            self.targetY = self.y + self.aimY * HOP_MAX;
        }
    }

    @Override
    protected void onAttackStep(Enemy self, AiContext ctx) {
        // Speed recomputed from what is left, so the hop lands on its point on
        // the last active step even after a wall has shaved some off it.
        int left = Math.max(1, self.def.activeSteps - self.attack().elapsed());
        float remaining = self.distanceTo(self.targetX, self.targetY);
        if (remaining > 0.5f) {
            self.moveToward(ctx.collision(), self.targetX, self.targetY,
                remaining / (left * Cfg.STEP));
        } else {
            self.halt();
        }
        ctx.strike(bodyBox(self, self.def.contactDamage), self.attack());
    }
}
