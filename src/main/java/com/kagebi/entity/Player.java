package com.kagebi.entity;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.kagebi.Cfg;
import com.kagebi.Dir;
import com.kagebi.combat.AttackState;
import com.kagebi.combat.Damage;
import com.kagebi.combat.Faction;
import com.kagebi.combat.HitResolver;
import com.kagebi.combat.Hitbox;
import com.kagebi.data.def.WeaponDef;
import com.kagebi.gen.CollisionGrid;
import com.kagebi.run.RunState;

/**
 * The ninja. Everything the player feels is one of the numbers in this file.
 *
 * <p>Three rules earn their complexity, and all three are about not lying to
 * the player:
 *
 * <ul>
 * <li><b>The roll has invulnerable frames, and they start late and end early.</b>
 *     Rolling through an enemy is the core defensive move of the genre; a roll
 *     that is invulnerable end to end is instead a free teleport, and the run
 *     stops having a fail state.
 * <li><b>An attack pressed during the recovery of the last one queues.</b> The
 *     press is read from the buffer for several steps and spent only when the
 *     swing actually starts, so a player mashing faster than the weapon can
 *     swing still gets every swing they asked for, in order.
 * <li><b>A swing may be cancelled into a roll, but only during recovery.</b>
 *     That is the one beat that is supposed to be payable early, and it is
 *     where nearly all the skill in this kind of combat lives.
 * </ul>
 *
 * <p>Hit points live in {@link RunState}, not here. The HUD, the death screen
 * and the run summary all read that object; a second copy on the entity would
 * drift from it inside one fight.
 */
public final class Player extends Entity {

    /**
     * 78 px/s, just under five 16px tiles a second: a 20-tile room crosses in
     * four seconds, where the placeholder's 60 took five and a half and read as
     * wading. 78 rather than a round 80 because enemies.json was tuned against
     * it - anything faster cannot be outrun, anything under 40 can be kited.
     */
    public static final float SPEED = 78f;

    /** 165 * 0.30s covers 49px, three tiles: past an enemy, not across the room. */
    public static final float ROLL_SPEED = 165f;
    /** 18 steps is 0.30s, and divides evenly by the roll sheet's three frames. */
    public static final int ROLL_STEPS = 18;
    /**
     * The invulnerable window inside the roll: steps 2 to 13 inclusive.
     *
     * <p>The two steps of startup stop a roll pressed after a hit landed from
     * rewriting history. The four-step tail is the important half - a roll that
     * is invulnerable end to end is not a dodge, it is a teleport, and a run
     * with one of those has no fail state left.
     */
    public static final int ROLL_IFRAME_FROM = 2;
    public static final int ROLL_IFRAME_TO = 14;
    /** Enough that rolling stays a decision rather than a movement mode. */
    public static final int ROLL_COOLDOWN = 10;

    /** 0.66s of mercy after a hit: long enough to retreat, short enough to feel fair. */
    public static final int HURT_IFRAMES = 40;
    /** Steps of stagger. Any longer and a second enemy gets a free hit. */
    public static final int HURT_STUN = 6;

    /**
     * Speed while swinging but past the weapon's root window. Not zero - a
     * character nailed down for a whole animation reads as broken rather than
     * committed - and not full, or the swing costs nothing.
     */
    public static final float ATTACK_MOVE_SCALE = 0.55f;

    /** The ninja occupies roughly the middle 12x12 of its 32x32 cell. */
    public static final float BODY = 12f;

    private final RunState run;
    private final AttackState swing = new AttackState();

    private WeaponDef weapon;

    /** Recomputed from the run's relics on entering a room; see EntityWorld. */
    public float damageMult = 1f;
    public float critChance = 0.05f;
    public float critMult = 2f;
    public float speedMult = 1f;
    public int armour;

    private boolean rolling;
    private int rollElapsed;
    private int rollCooldown;
    private float rollDirX;
    private float rollDirY;

    private int hurtSteps;
    private int deadSteps;

    /** Rolled once when the swing starts, not once per target. */
    private int swingDamage;
    /** Hits landed this step, so the world can shake the camera and freeze a frame. */
    public int hitsLandedThisStep;

    private final java.util.Random rng;

    public Player(RunState run, WeaponDef weapon, ActorSprites sprites, java.util.Random rng) {
        this.run = run;
        this.weapon = weapon;
        this.sprites = sprites;
        this.rng = rng;
        this.bodyW = BODY;
        this.bodyH = BODY;
        this.maxHp = run.maxHp;
        this.hp = run.hp;
    }

    public WeaponDef weapon() {
        return weapon;
    }

    public void setWeapon(WeaponDef weapon) {
        this.weapon = weapon;
    }

    public boolean rolling() {
        return rolling;
    }

    public int rollElapsed() {
        return rollElapsed;
    }

    public int rollCooldown() {
        return rollCooldown;
    }

    public boolean attacking() {
        return swing.busy();
    }

    public AttackState swing() {
        return swing;
    }

    public boolean staggered() {
        return hurtSteps > 0;
    }

    @Override
    public Faction faction() {
        return Faction.PLAYER;
    }

    @Override
    public int armour() {
        return armour;
    }

    @Override
    public boolean alive() {
        return run.hp > 0;
    }

    @Override
    public int hp() {
        return run.hp;
    }

    @Override
    public int maxHp() {
        return run.maxHp;
    }

    // ---- simulation ------------------------------------------------------

    @Override
    public void step(EntityWorld world) {
        hitsLandedThisStep = 0;
        stepTimers();
        hp = run.hp;
        maxHp = run.maxHp;

        if (!alive()) {
            deadSteps++;
            moving = false;
            return;
        }

        applyShove(world.collision());
        if (hurtSteps > 0) {
            hurtSteps--;
        }
        if (rollCooldown > 0) {
            rollCooldown--;
        }

        Intent intent = world.intent();

        // Actions start before the swing is advanced, so a weapon with no
        // windup lands on the step the button was read rather than one step
        // later. One step of added latency at 60Hz is measurable by hand.
        if (canRoll(intent)) {
            beginRoll(intent);
        } else if (canAttack(intent)) {
            beginSwing(intent);
        }

        if (rolling) {
            advanceRoll(world.collision());
            return;
        }

        if (swing.busy()) {
            if (swing.active()) {
                resolveSwing(world);
            }
            if (!swing.rooted() && hurtSteps == 0) {
                walk(world.collision(), intent, SPEED * speedMult * ATTACK_MOVE_SCALE, false);
            } else {
                moving = false;
            }
            swing.step();
            return;
        }

        if (hurtSteps == 0) {
            walk(world.collision(), intent, SPEED * speedMult, true);
        } else {
            moving = false;
        }
    }

    private boolean canRoll(Intent intent) {
        return intent.roll && !rolling && rollCooldown == 0 && hurtSteps == 0
            && (!swing.busy() || swing.cancellable());
    }

    private boolean canAttack(Intent intent) {
        return intent.attack && !rolling && !swing.busy() && hurtSteps == 0;
    }

    private void beginRoll(Intent intent) {
        intent.consumeRoll();
        swing.cancel();
        rolling = true;
        rollElapsed = 0;
        if (intent.moving()) {
            facing = Dir.of(intent.moveX, intent.moveY);
            float scale = intent.moveScale();
            rollDirX = intent.moveX * scale;
            rollDirY = intent.moveY * scale;
        } else {
            // A standing roll goes forward. Rolling nowhere would turn the
            // button into a pure invulnerability toggle, which is a different
            // and much worse game.
            rollDirX = facing.dx;
            rollDirY = facing.dy;
        }
    }

    private void advanceRoll(CollisionGrid grid) {
        if (rollElapsed == ROLL_IFRAME_FROM) {
            iframes.grant(ROLL_IFRAME_TO - ROLL_IFRAME_FROM);
        }
        moveBy(grid, rollDirX * ROLL_SPEED * Cfg.STEP, rollDirY * ROLL_SPEED * Cfg.STEP);
        rollElapsed++;
        if (rollElapsed >= ROLL_STEPS) {
            rolling = false;
            rollCooldown = ROLL_COOLDOWN;
        }
    }

    private void beginSwing(Intent intent) {
        intent.consumeAttack();
        if (intent.moving()) {
            facing = Dir.of(intent.moveX, intent.moveY);
        }
        boolean crit = Damage.rollCrit(rng, critChance);
        swingDamage = Damage.outgoing(weapon.damage, damageMult, crit, critMult);
        swing.begin(weapon.windupSteps, weapon.activeSteps, weapon.recoverSteps,
            weapon.rootSteps);
    }

    private void resolveSwing(EntityWorld world) {
        if (weapon.thrown()) {
            // A thrown weapon leaves the hand once, on the first active step;
            // the rest of the window is follow-through with nothing attached.
            if (swing.stepsInPhase() == 0) {
                world.throwFrom(this, swingDamage);
            }
            return;
        }
        Hitbox box = Hitbox.swing(x, y, facing, weapon.reach, weapon.width,
            swingDamage, weapon.knockback, Faction.PLAYER);
        hitsLandedThisStep += HitResolver.resolve(box, world.hostiles(), swing);
    }

    private void walk(CollisionGrid grid, Intent intent, float speed, boolean turn) {
        moving = intent.moving();
        if (!moving) {
            return;
        }
        if (turn) {
            facing = Dir.of(intent.moveX, intent.moveY);
        }
        float scale = intent.moveScale();
        moveBy(grid, intent.moveX * speed * scale * Cfg.STEP,
            intent.moveY * speed * scale * Cfg.STEP);
    }

    @Override
    public void takeHit(int damage, float fromX, float fromY, float knockback) {
        run.hp = Math.max(0, run.hp - damage);
        hp = run.hp;
        iframes.grant(HURT_IFRAMES);
        flashSteps = 8;
        hurtSteps = HURT_STUN;
        shove.apply(fromX, fromY, x, y, knockback, 0f, facing.opposite());
        // A hit interrupts the swing outright. Letting it finish means a player
        // who traded blows still collects their damage, which removes every
        // reason not to trade.
        swing.cancel();
        rolling = false;
    }

    /** Heals, clamped to the run maximum. Used by pickups and by shrines. */
    public void heal(int amount) {
        run.hp = Math.min(run.maxHp, run.hp + amount);
        hp = run.hp;
    }

    /** Puts the player somewhere and cancels everything in flight. */
    public void placeAt(float px, float py, Dir face) {
        x = px;
        y = py;
        facing = face == null ? Dir.DOWN : face;
        swing.cancel();
        shove.clear();
        iframes.clear();
        rolling = false;
        rollElapsed = 0;
        rollCooldown = 0;
        hurtSteps = 0;
        moving = false;
    }

    // ---- rendering -------------------------------------------------------

    @Override
    protected int spriteFootOffset() {
        // The 32x32 cell holds the ninja standing two pixels off its bottom
        // edge, so the sprite sits two pixels low for the feet to meet the shadow.
        return -2;
    }

    @Override
    protected boolean castsShadow() {
        return alive();
    }

    @Override
    public TextureRegion frame() {
        if (sprites == null) {
            return null;
        }
        if (!alive()) {
            TextureRegion[] d = sprites.dead;
            return d == null ? null : d[Math.min(d.length - 1, deadSteps / 10)];
        }
        if (rolling) {
            return ActorSprites.frameOf(sprites.roll, facing, rollElapsed, ROLL_STEPS);
        }
        if (hurtSteps > 0) {
            return ActorSprites.frameOf(sprites.hurt, facing, HURT_STUN - hurtSteps, HURT_STUN);
        }
        if (swing.busy()) {
            return ActorSprites.frameOf(sprites.attack, facing,
                swing.elapsed(), Math.max(1, swing.totalSteps()));
        }
        return (moving ? sprites.walk : sprites.idle).frame(facing, animSteps);
    }
}
