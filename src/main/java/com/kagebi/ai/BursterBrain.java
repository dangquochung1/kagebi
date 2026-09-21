package com.kagebi.ai;

import com.kagebi.entity.Enemy;

/**
 * Swells, then bursts a ring of droplets and keeps going.
 *
 * <p>The cove's answer to the octopus, which aims one shot at where the player
 * is. A burst aims at nothing: it fills a circle, so the counter is not to
 * stand out of the line but to be outside the ring when it goes off, or inside
 * it and already moving. That makes it the first enemy in the game a player
 * beats by choosing a distance rather than a direction.
 *
 * <p>It fires once, at the moment the active window opens, the way
 * {@link ShooterBrain} does. Firing every step of the window would empty a
 * room's worth of projectiles out of one slime.
 */
public final class BursterBrain extends BaseBrain {

    /** Droplets per burst, spread evenly around the whole circle. */
    public static final int SHOTS = 6;
    public static final float SPEED = 62f;
    /** Long enough to clear the ring at walking pace, short enough to dodge. */
    public static final int LIFE = 100;
    /** Inside this fraction of its trigger range it backs off before swelling. */
    public static final float COMFORT = 0.55f;

    @Override
    public String id() {
        return "burster";
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
            // Away, not towards: a burster that walks into contact range has
            // thrown away the only thing its ring of droplets is good for.
            self.moveToward(ctx.collision(),
                self.x - (ctx.playerX() - self.x), self.y - (ctx.playerY() - self.y), speed);
            return;
        }
        approach(self, ctx, speed);
    }

    /** Rooted while it swells. The pulse is the whole telegraph. */
    @Override
    protected void windupStep(Enemy self, AiContext ctx) {
        self.halt();
        if (self.stateSteps() % 8 < 4) {
            self.flashSteps = 2;
        }
    }

    @Override
    protected void onAttackStart(Enemy self, AiContext ctx) {
        int damage = Math.max(1, Math.round(self.def.attackDamage * self.damageMult));
        // Phase from the body's position so two bursters in one room do not
        // lay their rings down on exactly the same spokes.
        float offset = (self.x + self.y) * 0.01f;
        for (int i = 0; i < SHOTS; i++) {
            double a = offset + i * (Math.PI * 2 / SHOTS);
            ctx.fireProjectile(self, (float) Math.cos(a), (float) Math.sin(a),
                SPEED, damage, LIFE);
        }
    }

    @Override
    protected void onAttackStep(Enemy self, AiContext ctx) {
        self.halt();
    }
}
