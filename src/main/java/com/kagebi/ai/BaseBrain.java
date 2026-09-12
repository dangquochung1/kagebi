package com.kagebi.ai;

import com.kagebi.Dir;
import com.kagebi.combat.Damage;
import com.kagebi.combat.Faction;
import com.kagebi.combat.Hitbox;
import com.kagebi.entity.Enemy;
import com.kagebi.gen.CollisionGrid;

/**
 * The shared skeleton of {@link AiState}: the transient states and the
 * transitions between them, with hooks where a brain actually differs.
 *
 * <p>Writing WINDUP, ATTACK, RECOVER and HURT once rather than fourteen times is
 * not only less code. It is what keeps the timing honest between enemies - a
 * windup means the same thing on a slime and on a skeleton, so a player who has
 * learned to read one has learned to read the other, which is the entire reason
 * telegraphs work.
 *
 * <p><b>Two kinds of attack.</b> An enemy with an {@code attackRange} and an
 * {@code attackDamage} swings a box along its facing. One with an attack range
 * of zero - half the roster in enemies.json - attacks with its body: it lunges
 * a short, fixed distance along the direction it locked when the windup ended,
 * and its hitbox is its own body at {@code contactDamage}. Without the lunge
 * those enemies would never leave CHASE, their windup numbers would mean
 * nothing, and the only threat they posed would be walking into them.
 */
public abstract class BaseBrain implements AiBrain {

    /**
     * Flat knockback an enemy attack applies. A constant because
     * {@code EnemyDef} has no field for it: 70 px/s over the default 12 steps
     * moves the player about 7 pixels, half a tile, which reads as a hit
     * without stealing control.
     */
    public static final float ATTACK_KNOCKBACK = 70f;

    /** Extra reach on the hitbox over the range the enemy decided to attack from. */
    public static final float ATTACK_OVERREACH = 5f;

    /** Half-height of a melee swing box, before bosses scale it to their body. */
    public static final float ATTACK_HALF_WIDTH = 7f;

    /** Distance a body-attacker commits from. A little under two tiles. */
    public static final float LUNGE_RANGE = 28f;

    /** How far a lunge carries over its active window: just over one tile. */
    public static final float LUNGE_DISTANCE = 18f;

    /**
     * Hard ceiling on any transient state, as a backstop.
     *
     * <p>Every transition below is already bounded by a number from the def, so
     * this should never fire. It exists because a def with a nonsensical value,
     * or a subclass that forgets to leave a state it entered, produces an enemy
     * that stands still forever - and a room that never clears. Players report
     * that as "the game froze", four rooms later, with no way to reproduce it.
     */
    public static final int WEDGE_LIMIT = 600;

    /** Steps a sidestep lasts once a chase has been pressed into a wall. */
    private static final int SIDESTEP_STEPS = 20;

    @Override
    public final void think(Enemy self, AiContext ctx) {
        // First, not last: every transient case below returns early, so a
        // check after the switch would never see the states it exists for.
        if (self.stateSteps() >= WEDGE_LIMIT && self.state() != AiState.IDLE
                && self.state() != AiState.CHASE && self.state() != AiState.DEAD) {
            self.attack().cancel();
            self.halt();
            self.setState(AiState.IDLE);
            return;
        }
        switch (self.state()) {
            case DEAD:
                return;
            case HURT:
                self.halt();
                if (self.stateSteps() >= stunSteps(self)) {
                    self.setState(ctx.playerAlive() ? AiState.CHASE : AiState.IDLE);
                }
                return;
            case WINDUP:
                windupStep(self, ctx);
                if (self.stateSteps() >= self.def.windupSteps) {
                    // The active window is owned by an AttackState so that one
                    // swing cannot hit the same target on every step of it.
                    self.attack().begin(0, Math.max(1, self.def.activeSteps), 0, 0);
                    onAttackStart(self, ctx);
                    self.setState(AiState.ATTACK);
                }
                return;
            case ATTACK:
                onAttackStep(self, ctx);
                if (self.state() != AiState.ATTACK) {
                    return;     // the hook ended the attack itself: a charger hit a wall
                }
                self.attack().step();
                if (!self.attack().busy()) {
                    self.cooldown = self.def.cooldownSteps;
                    self.setState(AiState.RECOVER);
                }
                return;
            case RECOVER:
                onRecoverStep(self, ctx);
                if (self.stateSteps() >= self.def.recoverSteps) {
                    self.setState(ctx.playerAlive() ? AiState.CHASE : AiState.IDLE);
                }
                return;
            case CHASE:
                if (!ctx.playerAlive()) {
                    self.halt();
                    self.setState(AiState.IDLE);
                    return;
                }
                chase(self, ctx);
                break;
            default:
                idle(self, ctx);
                break;
        }
    }

    // ---- hooks -------------------------------------------------------------

    /** Waiting. The default wakes when the player comes inside aggro range. */
    protected void idle(Enemy self, AiContext ctx) {
        self.halt();
        if (ctx.playerAlive()
                && self.distanceTo(ctx.playerX(), ctx.playerY()) <= self.def.aggroRange) {
            self.setState(AiState.CHASE);
        }
    }

    /** Closing in. The default walks straight at the player and attacks in range. */
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
        approach(self, ctx, self.def.moveSpeed * self.speedMult);
    }

    /**
     * Commits to an attack. The target point is locked here, at the start of
     * the telegraph, not at its end: a hop or a cloud that tracks the player
     * through its windup cannot be dodged by reading it, and then the windup is
     * only a delay.
     */
    protected void beginWindup(Enemy self, AiContext ctx) {
        self.halt();
        self.targetX = ctx.playerX();
        self.targetY = ctx.playerY();
        self.facing = Dir.of(ctx.playerX() - self.x, ctx.playerY() - self.y);
        self.setState(AiState.WINDUP);
    }

    /** The telegraph. Default: flash, and lunge back before the strike. */
    protected void windupStep(Enemy self, AiContext ctx) {
        self.telegraph(ctx.collision(), ctx.playerX(), ctx.playerY(), self.def.windupSteps);
    }

    /** Once, as the active window opens. Default: lock the direction of the strike. */
    protected void onAttackStart(Enemy self, AiContext ctx) {
        lockAim(self, ctx.playerX(), ctx.playerY());
    }

    /** Every step of the active window. Default: a swing, or a body lunge. */
    protected void onAttackStep(Enemy self, AiContext ctx) {
        if (melee(self)) {
            self.halt();
            ctx.strike(meleeBox(self), self.attack());
            return;
        }
        float speed = LUNGE_DISTANCE / (Math.max(1, self.def.activeSteps) * com.kagebi.Cfg.STEP);
        self.moveDir(ctx.collision(), self.aimX, self.aimY, speed);
        ctx.strike(bodyBox(self, self.def.contactDamage), self.attack());
    }

    protected void onRecoverStep(Enemy self, AiContext ctx) {
        self.halt();
    }

    /**
     * Recovery and stagger are safe to touch; so is anything dormant or dead.
     * Everything else hurts on contact if the def gives it contact damage.
     */
    @Override
    public boolean harmfulOnContact(Enemy self) {
        if (self.def.contactDamage <= 0 || self.dormant) {
            return false;
        }
        switch (self.state()) {
            case RECOVER:
            case HURT:
            case DEAD:
                return false;
            default:
                return true;
        }
    }

    // ---- shared pieces -----------------------------------------------------

    /** Swings a box, as opposed to attacking with its body. */
    protected static boolean melee(Enemy self) {
        return self.def.attackRange > 0f && self.def.attackDamage > 0;
    }

    /** The distance this enemy commits to its attack from. */
    protected float triggerRange(Enemy self) {
        return melee(self) ? self.def.attackRange : LUNGE_RANGE;
    }

    protected static void lockAim(Enemy self, float tx, float ty) {
        float dx = tx - self.x;
        float dy = ty - self.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) {
            dx = self.facing.dx;
            dy = self.facing.dy;
            len = 1f;
        }
        self.aimX = dx / len;
        self.aimY = dy / len;
        self.facing = Dir.of(dx, dy);
    }

    /**
     * Walks at the player, stepping sideways if the walk jams.
     *
     * <p>No pathfinding: rooms are 20x11 and convex, so a straight line plus
     * the axis-at-a-time slide in {@code Entity.moveBy} reaches everywhere A*
     * would. The sidestep covers the one case a slide cannot - walking square
     * into a pillar, where both axes are blocked and nothing gives.
     */
    protected void approach(Enemy self, AiContext ctx, float speed) {
        CollisionGrid grid = ctx.collision();
        if (self.sidestepSteps > 0) {
            self.sidestepSteps--;
            self.moveDir(grid, self.aiX, self.aiY, speed);
            if (self.sidestepSteps == 0) {
                self.stuckSteps = 0;
            }
            return;
        }
        if (self.stuckSteps > Enemy.STUCK_LIMIT) {
            float dx = ctx.playerX() - self.x;
            float dy = ctx.playerY() - self.y;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 0.001f) {
                len = 1f;
                dx = 1f;
                dy = 0f;
            }
            // Perpendicular, side chosen at random so two enemies jammed on the
            // same pillar do not both go the same way round it.
            float sign = ctx.rng().nextBoolean() ? 1f : -1f;
            self.aiX = -dy / len * sign;
            self.aiY = dx / len * sign;
            self.sidestepSteps = SIDESTEP_STEPS;
            return;
        }
        self.moveToward(grid, ctx.playerX(), ctx.playerY(), speed);
    }

    protected Hitbox meleeBox(Enemy self) {
        int damage = Damage.outgoing(self.def.attackDamage, self.damageMult, false, 1f);
        // Bosses swing boxes as tall as they are; a 7px sliver from an 82px
        // tengu reads as the hit missing when it visibly connected.
        float half = Math.max(ATTACK_HALF_WIDTH, self.bodyH / 2f);
        return Hitbox.swing(self.x, self.y, self.facing,
            self.def.attackRange + ATTACK_OVERREACH, half,
            damage, ATTACK_KNOCKBACK, Faction.ENEMY);
    }

    protected static Hitbox bodyBox(Enemy self, int damage) {
        int dmg = Damage.outgoing(Math.max(1, damage), self.damageMult, false, 1f);
        return Hitbox.body(self.x, self.y, self.bodyW, self.bodyH, dmg,
            ATTACK_KNOCKBACK, Faction.ENEMY);
    }

    /**
     * Stagger length, capped at 12 steps whatever the def's invulnerability.
     * A stagger longer than the i-frames that came with it lets a fast weapon
     * hold an enemy still permanently, and a stunlocked enemy is not a fight.
     */
    protected int stunSteps(Enemy self) {
        return Math.max(4, Math.min(12, self.def.hurtInvulnSteps));
    }
}
