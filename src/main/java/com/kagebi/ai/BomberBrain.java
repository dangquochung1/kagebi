package com.kagebi.ai;

import com.kagebi.entity.Enemy;

/**
 * Runs at the player, lights its fuse, and detonates. Dying sets it off too.
 *
 * <p>The third of the cove's three slimes, and the one that inverts the
 * bargain every other enemy offers. A chaser is safest at range and a burster
 * is safest in its face; a bomber is safe nowhere for long, because killing it
 * where it stands still leaves the blast behind. The counter is to kill it
 * early, at a distance, or to be moving when it goes.
 *
 * <p>It is therefore deliberately fragile and fast, and its blast is placed
 * rather than swung: a hazard with no arming delay, centred on the body, which
 * is the one thing in {@link AiContext} that can hurt a player who is already
 * standing on top of it.
 */
public final class BomberBrain extends BaseBrain {

    /** How long the blast lingers once it lands: a third of a second. */
    public static final int BLAST_STEPS = 20;
    /**
     * A blast from a killed bomber is worth less than one it chose to set off.
     * Otherwise the safest play against every bomber is to walk away and let
     * it come, and the whole enemy reduces to a slower chaser.
     */
    public static final float DEATH_BLAST = 0.6f;

    @Override
    public String id() {
        return "bomber";
    }

    /** Close enough that the blast will land on the player, not behind them. */
    @Override
    protected float triggerRange(Enemy self) {
        return Math.max(LUNGE_RANGE, self.def.attackRange);
    }

    /**
     * The fuse: it keeps closing while it flashes, rather than rooting the way
     * a caster does. A bomber that stops to telegraph is a bomber the player
     * simply walks away from.
     */
    @Override
    protected void windupStep(Enemy self, AiContext ctx) {
        if (self.stateSteps() % 6 < 3) {
            self.flashSteps = 2;
        }
        approach(self, ctx, self.def.moveSpeed * self.speedMult * 0.6f);
    }

    @Override
    protected void onAttackStart(Enemy self, AiContext ctx) {
        detonate(self, ctx, 1f);
    }

    @Override
    protected void onAttackStep(Enemy self, AiContext ctx) {
        self.halt();
    }

    @Override
    public void onDeath(Enemy self, AiContext ctx) {
        detonate(self, ctx, DEATH_BLAST);
    }

    private void detonate(Enemy self, AiContext ctx, float scale) {
        int damage = Math.max(1, Math.round(self.def.attackDamage * self.damageMult * scale));
        // No arming delay. The blast is the attack, not a warning about one -
        // the flashing approach was the warning.
        ctx.placeHazard(self, self.x, self.y, damage, 0, BLAST_STEPS);
    }
}
