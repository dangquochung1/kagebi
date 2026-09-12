package com.kagebi.entity;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.kagebi.Cfg;
import com.kagebi.Dir;
import com.kagebi.ai.AiBrain;
import com.kagebi.ai.AiContext;
import com.kagebi.ai.AiState;
import com.kagebi.combat.AttackState;
import com.kagebi.combat.Faction;
import com.kagebi.combat.Modifiers;
import com.kagebi.data.def.EnemyDef;
import com.kagebi.gen.CollisionGrid;

/**
 * One trash mob: a definition, a brain, and the small amount of state a brain
 * needs to keep between steps.
 *
 * <p>The split is the point. {@link EnemyDef} is what a designer typed and is
 * shared by every slime in the game; {@link AiBrain} is behaviour and is shared
 * by every enemy with the same {@code brain} id; this object is the only thing
 * that is per-instance. Putting the state machine's counters here rather than
 * in the brain is what lets one brain object serve a whole room.
 *
 * <p>A death animation is one of the things the art does not provide: none of
 * the 66 monster sheets has one, they are 4x4 walk cycles and nothing else. So
 * death is a short white flash and a removal, which is at least honest, and the
 * windup telegraph has to be a flash and a lunge for the same reason.
 */
public class Enemy extends Entity {

    /** Steps a corpse flashes before it is dropped from the room. */
    public static final int DEATH_STEPS = 14;

    /** Backstop against a wedged chase; see {@link #stuckSteps}. */
    public static final int STUCK_LIMIT = 24;

    public final EnemyDef def;
    private final AiBrain brain;

    private AiState state = AiState.IDLE;
    private int stateSteps;
    private int deathSteps;

    /** Used for the active window and the hit-once set; timing outside it is the brain's. */
    private final AttackState attack = new AttackState();

    /** Steps before this enemy may start another attack. */
    public int cooldown;
    /**
     * How long the chase has been pressed against something solid. Rooms are
     * convex so this is rare, but an enemy grinding into a pillar forever is
     * the one AI failure a player definitely notices.
     */
    public int stuckSteps;

    /** Steps left of a sideways detour around whatever the chase walked into. */
    public int sidestepSteps;

    /** Free slots for a brain: a wander heading or a sidestep vector, and a timer. */
    public float aiX;
    public float aiY;
    public int aiTimer;

    /**
     * Unit vector locked when an attack starts. Dashes and lunges read this and
     * never recompute it - what the windup showed is what the attack does.
     */
    public float aimX;
    public float aimY;
    /** Which way round an orbiter circles: +1 or -1, fixed at spawn. */
    public float aiSign = 1f;

    /** A point locked at windup: where a hop lands or a cloud is placed. */
    public float targetX;
    public float targetY;

    /**
     * Sitting still, unanimated and harmless until the player comes near. An
     * ambusher that bobbed on the spot would give itself away, which is the
     * one thing it exists not to do.
     */
    public boolean dormant;

    /** 0 for a spawned enemy, 1 for a splitter's offspring, which does not split again. */
    public int generation;

    /** Boss phases scale these; 1 for everything else. */
    public float speedMult = 1f;
    public float damageMult = 1f;

    // Statuses the player's relics and items apply. All three are "longest
    // wins" rather than additive: a second application refreshes the timer
    // instead of stacking, which is what stops a fast weapon from making an
    // enemy permanently frozen and permanently dying.
    private int slowSteps;
    private int poisonSteps;
    private int poisonPerTick;
    private int poisonTick;
    private int distractSteps;

    /** Set by the world once this death has been counted into the run. */
    public boolean deathCounted;

    /** Steps a health bar stays up after a hit. 90 is a second and a half. */
    public static final int BAR_STEPS = 90;

    /**
     * Counts down the health bar over this enemy's head.
     *
     * <p>Its own counter rather than a reuse of {@link #flashSteps}, which
     * looks like the same signal and is not: flash is also set by a poison tick
     * and by the attack telegraph, so a bar riding on it would appear on an
     * enemy nobody has touched and blink on every windup.
     */
    private int barSteps;

    public void showHealthBar() {
        barSteps = BAR_STEPS;
    }

    /** 0 when the bar should be hidden, else how much of its life is left. */
    public float healthBarFade() {
        if (barSteps <= 0 || maxHp <= 0) {
            return 0f;
        }
        // Fades out over the last third, so it leaves rather than vanishing.
        float left = barSteps / (float) BAR_STEPS;
        return left > 0.33f ? 1f : left / 0.33f;
    }

    public Enemy(EnemyDef def, AiBrain brain, ActorSprites sprites) {
        this.def = def;
        this.brain = brain;
        this.sprites = sprites;
        this.maxHp = def.maxHp;
        this.hp = def.maxHp;
        // Scaled by the world that spawns it, which is the only thing that
        // knows the run's difficulty; see scaleHealth.
        // The body is smaller than the cell on purpose: a 16px sprite with a
        // 16px box cannot pass a 16px gap, and every corridor in the game is
        // exactly that wide.
        this.bodyW = 12f;
        this.bodyH = 12f;
        if (def.boss) {
            // A boss is sized from its measured figure, not its cell: tengured's
            // 82px cell holds a 53x33 figure, and a body cut from the cell would
            // be hit by swings that visibly passed over its head.
            float fw = sprites != null ? sprites.figureW : def.cell * 0.65f;
            float fh = sprites != null ? sprites.figureH : def.cell * 0.5f;
            this.bodyW = Math.max(12f, fw * 0.75f);
            this.bodyH = Math.max(12f, fh * 0.6f);
        }
    }

    public AiBrain brain() {
        return brain;
    }

    public AiState state() {
        return state;
    }

    public int stateSteps() {
        return stateSteps;
    }

    public AttackState attack() {
        return attack;
    }

    /** Moves to a new state and restarts its clock. Re-entering resets the clock too. */
    public void setState(AiState next) {
        if (state == AiState.DEAD) {
            return;
        }
        state = next;
        stateSteps = 0;
    }

    @Override
    public Faction faction() {
        return Faction.ENEMY;
    }

    @Override
    public float knockbackResist() {
        return def.knockbackResist;
    }

    @Override
    public boolean alive() {
        return hp > 0 && state != AiState.DEAD;
    }

    /** True while the corpse is still on screen, so the world can keep drawing it. */
    public boolean dying() {
        return state == AiState.DEAD && !removed;
    }

    // ---- simulation ------------------------------------------------------

    @Override
    public void step(EntityWorld world) {
        simulate(world);
    }

    /**
     * One step against any context. The world passes itself; an AI test
     * passes a thirty-line fake, which is the whole reason this is split out
     * of {@link #step}.
     */
    public void simulate(AiContext ctx) {
        stepTimers();
        stepStatus();
        if (cooldown > 0) {
            cooldown--;
        }

        if (state == AiState.DEAD) {
            deathSteps++;
            boolean animated = sprites != null && sprites.death != null;
            if (!animated) {
                flashSteps = 2;
            }
            if (deathSteps >= deathDuration()) {
                removed = true;
            }
            return;
        }

        applyShove(ctx.collision());
        if (distractSteps > 0) {
            // Skipped here rather than inside each brain, so a smoke bomb works
            // on all fourteen of them and on the fifteenth nobody has written.
            state = AiState.IDLE;
            stateSteps++;
            return;
        }
        brain.think(this, ctx);
        stateSteps++;
    }

    /** Slows this one by a fraction of its speed, for a while. */
    public void slow(float fraction, int steps) {
        if (fraction <= 0f || steps <= 0) {
            return;
        }
        speedMult = Math.min(speedMult, Math.max(0.1f, 1f - fraction));
        slowSteps = Math.max(slowSteps, steps);
    }

    public void poison(int perTick, int steps) {
        if (perTick <= 0 || steps <= 0) {
            return;
        }
        poisonPerTick = Math.max(poisonPerTick, perTick);
        poisonSteps = Math.max(poisonSteps, steps);
    }

    public void distract(int steps) {
        distractSteps = Math.max(distractSteps, steps);
    }

    public boolean distracted() {
        return distractSteps > 0;
    }

    /**
     * Damage over time bypasses i-frames on purpose. Poison that can be dodged
     * by being hit again is not poison, and at sixty ticks apart it cannot
     * stack into anything unfair.
     */
    private void stepStatus() {
        if (barSteps > 0) {
            barSteps--;
        }
        if (slowSteps > 0 && --slowSteps == 0) {
            speedMult = 1f;
        }
        if (distractSteps > 0) {
            distractSteps--;
        }
        if (poisonSteps > 0) {
            poisonSteps--;
            if (++poisonTick >= Modifiers.TICK_STEPS) {
                poisonTick = 0;
                hp = Math.max(0, hp - poisonPerTick);
                flashSteps = Math.max(flashSteps, 3);
                if (hp <= 0) {
                    setState(AiState.DEAD);
                }
            }
            if (poisonSteps == 0) {
                poisonPerTick = 0;
            }
        }
    }

    /** How long the corpse stays: the art's death strip when it has one. */
    public int deathDuration() {
        return sprites != null && sprites.death != null
            ? sprites.death.durationSteps() : DEATH_STEPS;
    }

    // ---- movement helpers used by brains ---------------------------------

    /** Walks straight at a point, sliding on whatever it scrapes. */
    public boolean moveToward(CollisionGrid grid, float tx, float ty, float speed) {
        float dx = tx - x;
        float dy = ty - y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) {
            return false;
        }
        return moveDir(grid, dx / len, dy / len, speed);
    }

    /** Walks along a unit vector. Tracks {@link #stuckSteps} so a brain can react. */
    public boolean moveDir(CollisionGrid grid, float ux, float uy, float speed) {
        moving = true;
        if (ux != 0f || uy != 0f) {
            facing = Dir.of(ux, uy);
        }
        boolean moved = moveBy(grid, ux * speed * Cfg.STEP, uy * speed * Cfg.STEP);
        if (moved) {
            stuckSteps = 0;
        } else {
            stuckSteps++;
        }
        return moved;
    }

    public void halt() {
        moving = false;
    }

    public float distanceTo(float px, float py) {
        float dx = px - x;
        float dy = py - y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * The telegraph, in the absence of a windup pose in the art.
     *
     * <p>A pulsing flash plus a short lunge <em>away</em> from the target. The
     * lunge is the half that carries: a flash alone is one frame of colour on a
     * 16px sprite, while movement in the wrong direction is legible at a glance
     * and is the cue every action game uses for "something is about to happen".
     */
    public void telegraph(CollisionGrid grid, float px, float py, int windupSteps) {
        if (stateSteps % 6 < 3) {
            flashSteps = 2;
        }
        if (stateSteps < windupSteps / 2) {
            moveToward(grid, x - (px - x), y - (py - y), def.moveSpeed * 0.35f);
        } else {
            halt();
        }
    }

    // ---- damage ----------------------------------------------------------

    @Override
    public void takeHit(int damage, float fromX, float fromY, float knockback) {
        if (state == AiState.DEAD) {
            return;
        }
        hp -= damage;
        iframes.grant(def.hurtInvulnSteps);
        flashSteps = 6;
        shove.apply(fromX, fromY, x, y, knockback, def.knockbackResist, facing.opposite());
        if (hp <= 0) {
            hp = 0;
            setState(AiState.DEAD);
            attack.cancel();
            shove.clear();
            return;
        }
        if (staggerable()) {
            setState(AiState.HURT);
            attack.cancel();
        }
    }

    /**
     * Bosses do not flinch. A boss that can be staggered out of every windup
     * turns the fight into a damage race with no reason to dodge, and every
     * telegraph the art provides goes unseen.
     */
    protected boolean staggerable() {
        return !def.boss;
    }

    // ---- rendering -------------------------------------------------------

    @Override
    protected boolean castsShadow() {
        return state != AiState.DEAD;
    }

    @Override
    public TextureRegion frame() {
        if (sprites == null) {
            return null;
        }
        if (state == AiState.DEAD) {
            return sprites.death != null
                ? sprites.death.frame(facing, deathSteps)
                : sprites.idle.frame(facing, animSteps);
        }
        if (dormant) {
            return sprites.idle.frame(facing, 0);
        }
        if (sprites.attack != null && (state == AiState.WINDUP || state == AiState.ATTACK)) {
            int total = Math.max(1, def.windupSteps + def.activeSteps);
            int elapsed = state == AiState.WINDUP ? stateSteps : def.windupSteps + stateSteps;
            return ActorSprites.frameOf(sprites.attack, facing, elapsed, total);
        }
        if (sprites.hurt != null && state == AiState.HURT) {
            return ActorSprites.frameOf(sprites.hurt, facing, stateSteps, 12);
        }
        return (moving ? sprites.walk : sprites.idle).frame(facing, animSteps);
    }
}
