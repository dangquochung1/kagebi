package com.kagebi.ai;

import com.kagebi.Cfg;
import com.kagebi.combat.Damage;
import com.kagebi.combat.Faction;
import com.kagebi.combat.Hitbox;
import com.kagebi.entity.Boss;
import com.kagebi.entity.Enemy;

/**
 * A boss: the same seven states, with a choice of attack at each windup.
 *
 * <p>The move is picked at the moment the windup starts, so the telegraph is
 * telling the truth about which one is coming. The later-phase pool is what the
 * phase change is <em>for</em> - a boss whose second phase is only bigger
 * numbers reads as the fight getting longer rather than changing.
 *
 * <p>Deliberately not a scripted sequence. A fixed rotation is memorised in two
 * attempts and the fight becomes a dance recital; a weighted choice keeps the
 * player reading the telegraph, which is the skill the whole system is built
 * around.
 *
 * <p>One class, configured per boss, rather than a class per boss: giantfrog2
 * and tengured differ in which moves they have, not in how a move works.
 */
public final class BossBrain extends BaseBrain {

    public enum Move {
        /** A melee box along the facing, at the def's attack range. */
        SWING,
        /** A straight dash along a direction locked at the windup's end. */
        CHARGE,
        /** A hop onto the point the player stood on when the windup began. */
        SLAM,
        /** A fan of projectiles. */
        VOLLEY
    }

    /** Dash multiplier on walking speed. Lower than a charger's: it is bigger. */
    public static final float DASH_SPEED = 2.4f;
    /** The longest a slam carries, so a slam from across the arena lands short. */
    public static final float SLAM_MAX = 96f;
    /** Slam landing box as a multiple of the body: the shockwave is wider than the frog. */
    public static final float SLAM_SPREAD = 1.4f;

    public static final float PROJECTILE_SPEED = 80f;
    public static final int PROJECTILE_LIFE = 150;
    /** Shots in a volley, spread evenly across {@link #VOLLEY_ARC} radians. */
    public static final int VOLLEY_SHOTS = 5;
    public static final float VOLLEY_ARC = 1.0f;

    private final String id;
    private final Move[] opening;
    private final Move[] later;

    public BossBrain(String id, Move[] opening, Move[] later) {
        this.id = id;
        this.opening = opening;
        this.later = later;
    }

    /** Used when a boss names no brain of its own. */
    public static BossBrain generic() {
        return new BossBrain("boss",
            new Move[] {Move.CHARGE},
            new Move[] {Move.CHARGE, Move.VOLLEY});
    }

    /**
     * giantfrog2 ships jump and charge strips, and enemies.json describes the
     * fight as a hop-slam that "forces them to disengage". One phase.
     */
    public static BossBrain frog() {
        return new BossBrain("boss_frog",
            new Move[] {Move.SLAM, Move.SLAM, Move.CHARGE},
            new Move[] {Move.SLAM, Move.CHARGE, Move.VOLLEY});
    }

    /**
     * tengured: a swordsman first, and after the eleven-frame transformation
     * at half health, a swordsman who also throws.
     */
    public static BossBrain tengu() {
        return new BossBrain("boss_tengu",
            new Move[] {Move.CHARGE},
            new Move[] {Move.CHARGE, Move.VOLLEY, Move.VOLLEY});
    }

    @Override
    public String id() {
        return id;
    }

    private static Move moveOf(Enemy self) {
        return self instanceof Boss ? Move.values()[((Boss) self).move] : Move.SWING;
    }

    private static void setMove(Enemy self, Move m) {
        if (self instanceof Boss) {
            ((Boss) self).move = m.ordinal();
        }
    }

    @Override
    protected void chase(Enemy self, AiContext ctx) {
        // A boss never loses interest: there is nowhere in the arena to hide,
        // and a boss that wandered off would leave the room uncleared.
        if (self.cooldown == 0) {
            float dist = self.distanceTo(ctx.playerX(), ctx.playerY());
            if (dist <= self.def.attackRange) {
                setMove(self, Move.SWING);
                beginWindup(self, ctx);
                return;
            }
            if (dist <= self.def.aggroRange) {
                Move[] pool = self instanceof Boss && ((Boss) self).phase() >= 2 ? later : opening;
                setMove(self, pool[ctx.rng().nextInt(pool.length)]);
                beginWindup(self, ctx);
                return;
            }
        }
        approach(self, ctx, self.def.moveSpeed * self.speedMult);
    }

    @Override
    protected void windupStep(Enemy self, AiContext ctx) {
        if (moveOf(self) == Move.SLAM) {
            // A squat before a hop, not a lunge back.
            self.halt();
            if (self.stateSteps() % 6 < 3) {
                self.flashSteps = 2;
            }
            return;
        }
        super.windupStep(self, ctx);
    }

    @Override
    protected void onAttackStart(Enemy self, AiContext ctx) {
        Move move = moveOf(self);
        if (move == Move.SLAM) {
            lockAim(self, self.targetX, self.targetY);
            if (self.distanceTo(self.targetX, self.targetY) > SLAM_MAX) {
                self.targetX = self.x + self.aimX * SLAM_MAX;
                self.targetY = self.y + self.aimY * SLAM_MAX;
            }
            return;
        }
        lockAim(self, ctx.playerX(), ctx.playerY());
        if (move == Move.VOLLEY) {
            fireVolley(self, ctx);
        }
    }

    private void fireVolley(Enemy self, AiContext ctx) {
        int damage = Math.max(1, Math.round(self.def.attackDamage * self.damageMult));
        double base = Math.atan2(self.aimY, self.aimX);
        for (int i = 0; i < VOLLEY_SHOTS; i++) {
            double offset = VOLLEY_ARC * (i - (VOLLEY_SHOTS - 1) / 2.0)
                / Math.max(1, VOLLEY_SHOTS - 1);
            ctx.fireProjectile(self, (float) Math.cos(base + offset),
                (float) Math.sin(base + offset), PROJECTILE_SPEED, damage, PROJECTILE_LIFE);
        }
    }

    @Override
    protected void onAttackStep(Enemy self, AiContext ctx) {
        switch (moveOf(self)) {
            case VOLLEY:
                self.halt();
                return;
            case CHARGE:
                if (!self.moveDir(ctx.collision(), self.aimX, self.aimY,
                        self.def.moveSpeed * DASH_SPEED * self.speedMult)) {
                    self.attack().cancel();
                    self.cooldown = self.def.cooldownSteps;
                    self.setState(AiState.RECOVER);
                    return;
                }
                ctx.strike(bodyBox(self, self.def.attackDamage), self.attack());
                return;
            case SLAM:
                int left = Math.max(1, self.def.activeSteps - self.attack().elapsed());
                float remaining = self.distanceTo(self.targetX, self.targetY);
                if (remaining > 0.5f) {
                    self.moveToward(ctx.collision(), self.targetX, self.targetY,
                        remaining / (left * Cfg.STEP));
                }
                ctx.strike(slamBox(self), self.attack());
                return;
            default:
                self.halt();
                ctx.strike(meleeBox(self), self.attack());
        }
    }

    private static Hitbox slamBox(Enemy self) {
        int damage = Damage.outgoing(self.def.attackDamage, self.damageMult, false, 1f);
        return Hitbox.body(self.x, self.y, self.bodyW * SLAM_SPREAD, self.bodyH * SLAM_SPREAD,
            damage, ATTACK_KNOCKBACK * 1.5f, Faction.ENEMY);
    }

    /** A transforming boss is untouchable, so it is also harmless to touch. */
    @Override
    public boolean harmfulOnContact(Enemy self) {
        if (self instanceof Boss && ((Boss) self).transforming()) {
            return false;
        }
        return super.harmfulOnContact(self);
    }
}
