package com.kagebi.entity;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.kagebi.Cfg;
import com.kagebi.Dir;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.kagebi.combat.AttackState;
import com.kagebi.combat.Combatant;
import com.kagebi.combat.Damage;
import com.kagebi.combat.Faction;
import com.kagebi.combat.HitResolver;
import com.kagebi.combat.Hitbox;
import com.kagebi.combat.Modifiers;
import com.kagebi.data.def.SkillDef;
import com.kagebi.data.def.WeaponDef;
import com.kagebi.gen.CollisionGrid;
import com.kagebi.gfx.Anim;
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
    /**
     * How far off line a straight walk into an edge can be and still be slid
     * round it, in pixels. Half the body: enough to find a gate from the path
     * that leads to it. A flat wall never slides, however far along it the
     * player is, because no offset clears it.
     */
    static final int CORNER_SLIDE = 6;

    /** One cell of a held-weapon sheet: 256/4 across and 256/4 down. */
    public static final int WEAPON_CELL = 64;
    /** Where the 32px body cell sits inside it: (64 - 32) / 2, on both axes. */
    public static final int WEAPON_INSET = (WEAPON_CELL - 32) / 2;

    private final RunState run;
    private final AttackState swing = new AttackState();

    private WeaponDef weapon;
    private Anim weaponArt;
    private WeaponDef throwWeapon;

    /**
     * Base crit before relics. Five percent is low enough that a crit reads as
     * luck rather than as the normal case, which is what makes the relics that
     * raise it worth taking.
     */
    public static final float BASE_CRIT_CHANCE = 0.05f;
    public static final float BASE_CRIT_MULT = 2f;

    /**
     * Timed buffs from items, and anything else that wants to nudge a number
     * for a while. Relics do not live here - they come from {@link #mods}, so
     * that picking one up takes effect on the next step without anyone having
     * to remember to recompute.
     */
    public float damageMult = 1f;
    public float critChance = BASE_CRIT_CHANCE;
    public float critMult = BASE_CRIT_MULT;
    public float speedMult = 1f;
    public int armour;

    /** Relics, village upgrades and the character's perk, already combined. */
    private Modifiers mods = new Modifiers();
    /** A flat pool absorbed before hit points, from a shield potion. */
    private int shield;

    // Timed buffs from potions. One duration for all of them, so the player
    // learns it once; see Modifiers.BUFF_STEPS.
    private int speedBuffSteps;
    private int damageBuffSteps;
    private int poisonSteps;
    private int poisonPerTick;
    private int poisonTick;

    private boolean rolling;
    private int rollElapsed;
    private int rollCooldown;
    private float rollDirX;
    private float rollDirY;

    private int hurtSteps;
    private int deadSteps;
    /** The last left-or-right heading, for mirroring single-facing art. */
    private Dir lastHorizontal = Dir.RIGHT;

    /** Steps each frame of the ninjas' two-frame death column is held for. */
    private static final int DEAD_STEPS_PER_FRAME = 10;
    /**
     * How long a death lasts when the character has no death art at all.
     *
     * <p>Not zero. The screen waits this out before declaring the run over, and
     * a wait of nothing would put the game-over screen up on the same step the
     * last hit landed - which is the behaviour this replaced.
     */
    private static final int DEATH_FALLBACK_STEPS = 20;

    /** Rolled once when the swing starts, not once per target. */
    private int swingDamage;
    /**
     * Whether that roll crit. Kept rather than discarded because it is the one
     * thing about a hit the player most wants told: it was thrown away the
     * moment it was rolled, so a crit was a number nobody could see being
     * bigger than a number nobody could see.
     */
    private boolean swingCrit;
    /** Whether the busy swing is a throw. One pair of hands, one action. */
    private boolean swingIsThrow;
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
        setWeapon(weapon, null);
    }

    /**
     * The weapon, and the sheet of it being swung.
     *
     * <p>The art travels with the definition because the two have to change
     * together: a katana frame drawn over an axe swing is worse than no weapon
     * at all. Null art is the headless case and the case where the atlas has no
     * sheet for this weapon, and both draw the swing without it - which is
     * exactly what the game did for its whole life until now.
     */
    public void setWeapon(WeaponDef weapon, Anim art) {
        this.weapon = weapon;
        this.weaponArt = art;
    }

    /** The thrown weapon in the off hand, or null when there is none. */
    public WeaponDef throwWeapon() {
        return throwWeapon;
    }

    public void setThrowWeapon(WeaponDef weapon) {
        this.throwWeapon = weapon;
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

    public Modifiers mods() {
        return mods;
    }

    public void setMods(Modifiers mods) {
        this.mods = mods == null ? new Modifiers() : mods;
    }

    public int shield() {
        return shield;
    }

    public void addShield(int amount) {
        shield = Math.max(shield, amount);
    }

    /**
     * Armour and relics, then the difficulty setting.
     *
     * <p>Multiplied on here rather than folded into {@link Modifiers}, which is
     * documented as the sum of relics, upgrades and perks. Hiding a global
     * option inside it would make {@code ModifiersTest} assert something untrue
     * about what a relic is worth, and would hand the difficulty to anything
     * that reads a modifier for any other reason.
     *
     * <p>{@code HitResolver} is the only caller that matters, so this one line
     * covers every blow the player takes - except poison, which subtracts from
     * the run directly and is scaled where it is applied.
     */
    @Override
    public float damageTakenMult() {
        return mods.incomingMult() * run.difficulty.damageTaken;
    }

    /**
     * Flat soak, from worn armour and from anything buffing it for a while.
     *
     * <p>Summed here rather than written into {@link #armour} when the kit
     * changes, so the two sources cannot fall out of step: the field is what a
     * potion added and expires, the modifier is what is being worn and does
     * not. Before there was armour to wear, this returned a field nothing ever
     * wrote, and {@code Damage.incoming} subtracted zero on every hit in the
     * game.
     */
    @Override
    public int armour() {
        return armour + mods.armourAdd();
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

    /**
     * How long this character's death takes to play, in fixed steps.
     *
     * <p>The mirror of {@link Enemy#deathDuration()}, and it exists for the
     * same reason: the length belongs to the art, not to the screen that waits
     * for it. A ninja's two-frame column runs 20 steps; Karasu's six-frame
     * strip runs 48. Hard-coding either would cut one short or leave the other
     * staring at a corpse.
     */
    public int deathDuration() {
        if (sprites == null) {
            return DEATH_FALLBACK_STEPS;
        }
        if (sprites.death != null) {
            return sprites.death.durationSteps();
        }
        return sprites.dead == null ? DEATH_FALLBACK_STEPS
            : sprites.dead.length * DEAD_STEPS_PER_FRAME;
    }

    /** Steps since this player died; zero while alive. */
    public int deadSteps() {
        return deadSteps;
    }

    /**
     * Which way a side-view hero is mirrored.
     *
     * <p>The three tengu are drawn facing one way only, so up and down have no
     * art of their own and the sprite has to keep whichever side it was last
     * turned to. Without this, walking left and then up flips the character to
     * face east for as long as they walk north - which reads as the sprite
     * glitching rather than as a limitation of the art.
     */
    @Override
    protected boolean flipX() {
        return sprites != null && sprites.singleFacing && lastHorizontal == Dir.LEFT;
    }

    // ---- skills ----------------------------------------------------------------

    /**
     * What the three keys are bound to, and how long until each may fire again.
     *
     * <p>Held on the player rather than on the run, so a cooldown does not
     * survive a death - and rebuilt when the loadout is, beside the modifiers,
     * because the skills a character has is content and content can be
     * reloaded under a running game.
     */
    private final SkillDef[] skills = new SkillDef[Intent.SKILLS];
    private final int[] cooldown = new int[Intent.SKILLS];

    /** The lunge in progress: its definition, how far through, and its heading. */
    private SkillDef lunging;
    private int lungeElapsed;
    private float lungeDirX;
    private float lungeDirY;
    private int lungeDamage;
    /** Everything the current lunge has already hit, so nothing is hit twice. */
    private final java.util.List<Combatant> lungeStruck = new java.util.ArrayList<>();

    /** The ultimate: what is up, for how long, and when the aura is refreshed. */
    private SkillDef avatar;
    private int avatarSteps;
    private int auraTick;

    /**
     * Health the player spent on purpose this step.
     *
     * <p>The world watches the run's health across a step and treats any
     * fall as an injury - hit-stop, a shake and the hurt sound. That is right
     * for everything that had ever reduced it, and wrong for the ultimate,
     * which is bought with blood: casting it played the sound of being hit,
     * shook the screen and froze the next five steps, which ate the dash the
     * player pressed immediately afterwards.
     *
     * <p>Reported rather than special-cased in the world, so the world still
     * has one rule - a fall this did not account for is still an injury.
     */
    private int spentThisStep;

    /** The stick this step, so a lunge can aim at eight directions. */
    private float intentX;
    private float intentY;
    private boolean intentMoving;

    /** Steps a lunge takes. Short: it is a thrust, not a second dash. */
    public static final int LUNGE_STEPS = 12;
    /** How often the aura strip is re-cast while the ultimate is up. */
    private static final int AURA_EVERY = 14;

    // ---- simulation ------------------------------------------------------

    @Override
    public void step(EntityWorld world) {
        hitsLandedThisStep = 0;
        spentThisStep = 0;
        stepTimers();
        stepBuffs();
        hp = run.hp;
        maxHp = run.maxHp;

        if (!alive()) {
            deadSteps++;
            moving = false;
            return;
        }

        // Read before this step can turn the player, so the mirror of a
        // side-view hero always reflects a heading they actually held. Kept
        // here rather than at the four places facing is assigned, because a
        // fifth one added later would silently not update it.
        if (facing == Dir.LEFT || facing == Dir.RIGHT) {
            lastHorizontal = facing;
        }

        applyShove(world.collision());
        if (hurtSteps > 0) {
            hurtSteps--;
        }
        if (rollCooldown > 0) {
            rollCooldown--;
        }
        stepSkills(world);

        Intent intent = world.intent();
        // Kept so beginLunge can aim at eight directions without being handed
        // the intent: it is reached from cast(), which is reached from a loop
        // over the slots, and threading the intent through both for one read
        // is more plumbing than a field.
        intentX = intent.moveX * intent.moveScale();
        intentY = intent.moveY * intent.moveScale();
        intentMoving = intent.moving();

        // Actions start before the swing is advanced, so a weapon with no
        // windup lands on the step the button was read rather than one step
        // later. One step of added latency at 60Hz is measurable by hand.
        // Skills before the ordinary actions: a player who presses a skill and
        // attack in the same step meant the skill, which is the one on a
        // cooldown they have been waiting out.
        for (int i = 0; i < Intent.SKILLS; i++) {
            if (intent.skill[i] && cast(i, world)) {
                intent.consumeSkill(i);
            }
        }

        if (canRoll(intent)) {
            beginRoll(intent, world);
        } else if (canThrow(intent)) {
            beginThrow(intent, world);
        } else if (canAttack(intent)) {
            beginSwing(intent, world);
        }

        // The quick key, which until the village had a kitchen nothing read.
        // Not mid-roll: the press stays buffered for a few steps, so one made as
        // a roll ends still lands.
        if (intent.useItem && !rolling) {
            intent.consumeUseItem();
            world.useQuickItem();
        }

        if (lunging != null) {
            advanceLunge(world);
            return;
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
                walk(world.collision(), intent, speed() * ATTACK_MOVE_SCALE, false);
            } else {
                moving = false;
            }
            swing.step();
            return;
        }

        if (hurtSteps == 0) {
            walk(world.collision(), intent, speed(), true);
        } else {
            moving = false;
        }
    }

    /**
     * Binds the skills this character has, by slot.
     *
     * <p>Called wherever the modifiers are rebuilt, because both answer the
     * same question - what is this character able to do - and letting them
     * drift apart is how a skill outlives the character that had it.
     */
    public void setSkills(Array<SkillDef> available) {
        java.util.Arrays.fill(skills, null);
        if (available == null) {
            return;
        }
        for (SkillDef s : available) {
            int slot = s.slot - 1;
            if (slot >= 0 && slot < skills.length) {
                skills[slot] = s;
            }
        }
    }

    public SkillDef skill(int slot) {
        return slot >= 0 && slot < skills.length ? skills[slot] : null;
    }

    /** Steps until this slot may fire again; zero when it is ready. */
    public int cooldown(int slot) {
        return slot >= 0 && slot < cooldown.length ? cooldown[slot] : 0;
    }

    /** Whether the ultimate is up, which the HUD and the dash both ask. */
    public boolean transformed() {
        return avatarSteps > 0 && avatar != null;
    }

    public int avatarSteps() {
        return avatarSteps;
    }

    /** The ultimate that is up, or null. */
    public SkillDef avatar() {
        return avatar;
    }

    /** Health this step's actions cost on purpose; see {@link #spentThisStep}. */
    public int spentThisStep() {
        return spentThisStep;
    }

    public boolean lunging() {
        return lunging != null;
    }

    private void stepSkills(EntityWorld world) {
        for (int i = 0; i < cooldown.length; i++) {
            if (cooldown[i] > 0) {
                cooldown[i]--;
            }
        }
        if (avatarSteps > 0 && --avatarSteps == 0) {
            // Ended by putting the modifiers back, not by undoing each one:
            // half a dozen inverse operations applied in the wrong order is
            // how a buff leaves something behind.
            avatar = null;
            world.refreshMods();
        } else if (transformed() && ++auraTick >= AURA_EVERY) {
            auraTick = 0;
            world.avatarAura(avatar);
        }
    }

    /**
     * Whether anything may be cast at all.
     *
     * <p>The same gate a roll uses, plus not mid-lunge. A skill during a roll
     * would cancel the invulnerability the roll was spent on, and a skill
     * during a swing would let the player skip the recovery every weapon is
     * balanced around.
     */
    private boolean canCast() {
        return alive() && !rolling && lunging == null && hurtSteps == 0
            && (!swing.busy() || swing.cancellable());
    }

    /**
     * Tries to cast whatever is on a slot.
     *
     * @return whether anything happened, so the press is only spent if it did
     */
    private boolean cast(int slot, EntityWorld world) {
        SkillDef s = skills[slot];
        if (s == null || cooldown[slot] > 0 || !canCast()) {
            return false;
        }
        switch (s.kind) {
            case LUNGE: beginLunge(s, world); break;
            case NOVA: castNova(s, world); break;
            case AVATAR: if (!beginAvatar(s, world)) {
                    return false;
                }
                break;
            default: return false;
        }
        cooldown[slot] = s.cooldownSteps;
        return true;
    }

    /**
     * The thrust, aimed by the stick and not by the facing.
     *
     * <p>{@code Dir} has four values and the player can hold eight directions,
     * so a lunge that read {@code facing} would refuse to go diagonally - the
     * player would press up-right and be thrown straight right. The heading is
     * taken from the raw axes the way {@link #beginRoll} takes it, which is
     * the one place in this class that already knew how.
     */
    private void beginLunge(SkillDef s, EntityWorld world) {
        swing.cancel();
        lunging = s;
        lungeElapsed = 0;
        lungeStruck.clear();
        if (intentMoving) {
            lungeDirX = intentX;
            lungeDirY = intentY;
            facing = Dir.of(intentX, intentY);
        } else {
            lungeDirX = facing.dx;
            lungeDirY = facing.dy;
        }
        lungeDamage = Damage.outgoing(weapon == null ? 1 : weapon.damage,
            damageMult * mods.outgoingMult(hpFraction()) * s.damageMult,
            false, critMult, rng);
        // The bolt lies along the thrust and rides the body, which is what was
        // asked for: pointing where the player is going, not where they were.
        float angle = MathUtils.atan2(lungeDirY, lungeDirX) * MathUtils.radiansToDegrees;
        world.skillFx(s.vfx, x, y, true, angle, 1f, 1f, this, 0f);
        world.onSwingBegun(weapon);
    }

    private void advanceLunge(EntityWorld world) {
        float perStep = lunging.range / LUNGE_STEPS;
        moveBy(world.collision(), lungeDirX * perStep, lungeDirY * perStep);
        world.lungeThrough(lunging, lungeDamage, lungeStruck);
        if (++lungeElapsed >= LUNGE_STEPS) {
            lunging = null;
        }
    }

    private void castNova(SkillDef s, EntityWorld world) {
        swing.cancel();
        int damage = Damage.outgoing(weapon == null ? 1 : weapon.damage,
            damageMult * mods.outgoingMult(hpFraction()) * s.damageMult,
            false, critMult, rng);
        world.castNova(s, damage);
    }

    /**
     * The transformation, and the blood it costs.
     *
     * <p>The cost is a share of current health with a floor under it, and the
     * floor is the whole design: an ultimate that cannot be used when losing is
     * an ultimate that cannot be used, because losing is when anybody reaches
     * for one. Below the floor it is free rather than refused - refusing would
     * be the same mistake dressed as a warning.
     *
     * @return false only if the player is already dead, which cannot happen
     *         here but is cheap to be sure of before subtracting health
     */
    private boolean beginAvatar(SkillDef s, EntityWorld world) {
        if (!alive()) {
            return false;
        }
        if (hpFraction() > s.hpFloor) {
            int cost = Math.round(run.hp * s.hpCost);
            int paid = run.hp - Math.max(1, run.hp - cost);
            run.hp -= paid;
            hp = run.hp;
            spentThisStep += paid;
        }
        avatar = s;
        avatarSteps = s.durationSteps;
        auraTick = 0;
        // The numbers change by rebuilding them, so ending is the same act as
        // starting and neither has to know what the other did.
        world.refreshMods();
        world.avatarFlash();
        world.avatarAura(s);
        return true;
    }

    /**
     * What the ultimate adds, folded in beside gear and relics.
     *
     * <p>Read by {@code Loadout.of} rather than applied here, so there is one
     * place that knows how a modifier is built and the ultimate is just a
     * fourth source of them - which is also why ending it needs no inverse.
     */
    public SkillDef activeAvatar() {
        return transformed() ? avatar : null;
    }

    private boolean canRoll(Intent intent) {
        return intent.roll && !rolling && rollCooldown == 0 && hurtSteps == 0
            && (!swing.busy() || swing.cancellable());
    }

    private boolean canAttack(Intent intent) {
        return intent.attack && !rolling && !swing.busy() && hurtSteps == 0;
    }

    /** Nothing in the off hand means the key does nothing, quietly. */
    private boolean canThrow(Intent intent) {
        return intent.throwing && throwWeapon != null
            && !rolling && !swing.busy() && hurtSteps == 0;
    }

    /**
     * A throw, on its own button and its own weapon.
     *
     * <p>It runs through the same {@link AttackState} as a swing, which is what
     * stops the two being usable at once: one pair of hands, one action. The
     * timings come from the thrown weapon, so a shuriken is quicker to let go
     * of than a kunai exactly as its numbers say.
     */
    private void beginThrow(Intent intent, EntityWorld world) {
        intent.consumeThrow();
        if (intent.moving()) {
            facing = Dir.of(intent.moveX, intent.moveY);
        }
        swingIsThrow = true;
        swingCrit = Damage.rollCrit(rng, critChance + mods.critChanceAdd());
        // The off hand has its own multiplier. It used to share the main
        // hand's, which meant melee_damage_mult - a perk and an upgrade both
        // named for melee - was quietly sharpening thrown kunai as well.
        swingDamage = Damage.outgoing(throwWeapon.damage,
            damageMult * mods.throwMult(),
            swingCrit, critMult * mods.critDamageMult(), rng);
        float haste = Math.max(0.25f, mods.attackSpeedMult());
        world.onSwingBegun(throwWeapon);
        swing.begin(Math.max(1, Math.round(throwWeapon.windupSteps / haste)),
            throwWeapon.activeSteps,
            Math.max(1, Math.round(throwWeapon.recoverSteps / haste)),
            throwWeapon.rootSteps);
    }

    private void beginRoll(Intent intent, EntityWorld world) {
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
        // While the storm is up, a dash is also a weapon: it leaves a trail
        // and arcs to whatever is close. Both are the ultimate's doing rather
        // than the dash's, so both are asked of the ultimate's definition and
        // neither happens when it is not up.
        if (transformed()) {
            world.dashTrail(rollDirX, rollDirY);
            world.dashChain(avatar, Damage.outgoing(weapon == null ? 1 : weapon.damage,
                damageMult * mods.outgoingMult(hpFraction()) * avatar.damageMult,
                false, critMult, rng));
        }
    }

    private void advanceRoll(CollisionGrid grid) {
        if (rollElapsed == ROLL_IFRAME_FROM) {
            iframes.grant(ROLL_IFRAME_TO - ROLL_IFRAME_FROM + mods.rollInvulnAdd());
        }
        moveBy(grid, rollDirX * ROLL_SPEED * Cfg.STEP, rollDirY * ROLL_SPEED * Cfg.STEP);
        rollElapsed++;
        if (rollElapsed >= ROLL_STEPS) {
            rolling = false;
            rollCooldown = ROLL_COOLDOWN;
        }
    }

    /** Reused every swing rather than allocated: this runs sixty times a second. */
    private final java.util.List<com.kagebi.combat.Combatant> struck =
        new java.util.ArrayList<>();

    private void beginSwing(Intent intent, EntityWorld world) {
        intent.consumeAttack();
        if (intent.moving()) {
            facing = Dir.of(intent.moveX, intent.moveY);
        }
        swingIsThrow = false;
        swingCrit = Damage.rollCrit(rng, critChance + mods.critChanceAdd());
        swingDamage = Damage.outgoing(weapon.damage,
            damageMult * mods.outgoingMult(hpFraction()),
            swingCrit, critMult * mods.critDamageMult(), rng);
        // Attack speed shortens the parts the player waits through. The active
        // window is left alone: shrinking it would make a faster weapon harder
        // to land, which is the opposite of what the relic promises.
        float haste = Math.max(0.25f, mods.attackSpeedMult());
        world.onSwingBegun(weapon);
        swing.begin(Math.max(1, Math.round(weapon.windupSteps / haste)),
            weapon.activeSteps,
            Math.max(1, Math.round(weapon.recoverSteps / haste)),
            weapon.rootSteps);
    }

    private void resolveSwing(EntityWorld world) {
        WeaponDef using = swingIsThrow ? throwWeapon : weapon;
        if (using != null && using.thrown()) {
            // A thrown weapon leaves the hand once, on the first active step;
            // the rest of the window is follow-through with nothing attached.
            if (swing.stepsInPhase() == 0) {
                world.throwFrom(this, using, swingDamage, swingCrit);
            }
            return;
        }
        Hitbox box = Hitbox.swing(x, y, facing, weapon.reach + mods.reachAdd(),
            weapon.width, swingDamage, weapon.knockback, Faction.PLAYER);
        struck.clear();
        int landed = HitResolver.resolve(box, world.hostiles(), swing, struck);
        hitsLandedThisStep += landed;
        if (landed > 0) {
            world.onPlayerHitLanded(this, swingDamage, landed);
            world.applyOnHit(this, struck, swingDamage, swingCrit);
        }
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
        float dx = intent.moveX * speed * scale * Cfg.STEP;
        float dy = intent.moveY * speed * scale * Cfg.STEP;
        if (!moveBy(grid, dx, dy)) {
            slideRoundCorner(grid, dx, dy);
        }
    }

    /**
     * Walking straight into the edge of something with a way past it a few
     * pixels to one side: take a step sideways, toward the way past.
     *
     * <p>Without this a gap has to be lined up to the pixel. The garden gate is
     * eighteen pixels between its posts and the ninja is twelve wide, which is
     * six pixels of lining up - so a player walking down the path at it bumps
     * a post, stops dead, and reads the gate as shut. Only a walk along one
     * axis slides: a diagonal one already slides along a wall in moveBy.
     *
     * <p>Standing clear where the step sideways ends, and clear one step on
     * from there, is enough to know the whole sideways step is clear: the box
     * the player stands in and the box they end in overlap, and between them
     * they cover every box in between.
     */
    private void slideRoundCorner(CollisionGrid grid, float dx, float dy) {
        if (grid == null || (dx != 0f) == (dy != 0f)) {
            return;
        }
        float halfW = bodyW / 2f;
        float halfH = bodyH / 2f;
        float stride = Math.abs(dx + dy);
        for (int off = 1; off <= CORNER_SLIDE; off++) {
            for (int side = 0; side < 2; side++) {
                float sign = side == 0 ? 1f : -1f;
                float ox = dx == 0f ? sign * off : 0f;
                float oy = dy == 0f ? sign * off : 0f;
                if (!grid.overlaps(x + ox - halfW, y + oy - halfH, bodyW, bodyH)
                    && !grid.overlaps(x + ox + dx - halfW, y + oy + dy - halfH, bodyW, bodyH)) {
                    float slide = Math.min(stride, off);
                    moveBy(grid, Math.signum(ox) * slide, Math.signum(oy) * slide);
                    return;
                }
            }
        }
    }

    @Override
    public void takeHit(int damage, float fromX, float fromY, float knockback) {
        // A shield potion is a pool in front of the hit points, not a heal, so
        // it survives a run at full health - which is when the player has one
        // spare to drink.
        if (shield > 0) {
            int absorbed = Math.min(shield, damage);
            shield -= absorbed;
            damage -= absorbed;
        }
        run.hp = Math.max(0, run.hp - damage);
        hp = run.hp;
        iframes.grant(HURT_IFRAMES + mods.invulnStepsAdd());
        flashSteps = 8;
        hurtSteps = HURT_STUN;
        shove.apply(fromX, fromY, x, y, knockback, 0f, facing.opposite());
        // A hit interrupts the swing outright. Letting it finish means a player
        // who traded blows still collects their damage, which removes every
        // reason not to trade.
        swing.cancel();
        rolling = false;
    }

    public void buffSpeed(float mult, int steps) {
        speedMult = Math.max(speedMult, mult);
        speedBuffSteps = Math.max(speedBuffSteps, steps);
    }

    public void buffDamage(float mult, int steps) {
        damageMult = Math.max(damageMult, mult);
        damageBuffSteps = Math.max(damageBuffSteps, steps);
    }

    public void poison(int perTick, int steps) {
        poisonPerTick = Math.max(poisonPerTick, perTick);
        poisonSteps = Math.max(poisonSteps, steps);
    }

    public void clearPoison() {
        poisonSteps = 0;
        poisonPerTick = 0;
        poisonTick = 0;
    }

    public boolean poisoned() {
        return poisonSteps > 0;
    }

    /** Runs the timed buffs and any poison down. Called once per step. */
    private void stepBuffs() {
        if (speedBuffSteps > 0 && --speedBuffSteps == 0) {
            speedMult = 1f;
        }
        if (damageBuffSteps > 0 && --damageBuffSteps == 0) {
            damageMult = 1f;
        }
        if (poisonSteps > 0) {
            poisonSteps--;
            if (++poisonTick >= Modifiers.TICK_STEPS) {
                poisonTick = 0;
                // Straight to hit points: poison that i-frames block would be
                // cured by being hit, which is the wrong lesson entirely.
                // Scaled here because it is the one source of damage that
                // never passes through HitResolver, and a difficulty that
                // halved every blow but not the poison would leave a poison
                // stack worth twice what a sword blow is.
                int tick = Math.max(1, Math.round(poisonPerTick * run.difficulty.damageTaken));
                run.hp = Math.max(0, run.hp - tick);
                hp = run.hp;
                flashSteps = Math.max(flashSteps, 4);
            }
            if (poisonSteps == 0) {
                poisonPerTick = 0;
            }
        }
    }

    /**
     * Comes back from the dead with a long grace window, so the hit that would
     * have killed the player again is not the very next one.
     */
    public void reviveAt(int hitPoints) {
        run.hp = hitPoints;
        hp = hitPoints;
        iframes.grant(HURT_IFRAMES * 2);
        flashSteps = 30;
        swing.cancel();
        rolling = false;
    }

    /** Walk speed with buffs and relics folded in. */
    public float speed() {
        return SPEED * speedMult * mods.moveSpeedMult();
    }

    /** Hit points as a fraction of the maximum, for the relics that key off it. */
    public float hpFraction() {
        return run.maxHp <= 0 ? 0f : (float) run.hp / run.maxHp;
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
    protected boolean castsShadow() {
        return alive();
    }

    /**
     * The held weapon, drawn over the swing.
     *
     * <p>The five weapon sheets are 256x256: four columns of {@link
     * #WEAPON_CELL} by four rows, column per facing and row per frame - the
     * same shape as every other sheet in the packs, so the frame index that
     * picks the body picks the blade. The player's own cell is 32 and sits
     * centred inside the 64, which is where the offset comes from and why it is
     * exact rather than tuned: {@code (64 - 32) / 2}.
     *
     * <p>This was left undone for a long time on the grounds that per-frame
     * hand anchors would have to be guessed. They do not - the two sheets were
     * drawn to be composited, and measuring the figure inside each cell shows
     * them already lined up.
     *
     * <p>Thrown weapons are skipped: their sprite points at the projectile in
     * the fx atlas, not at a sheet of someone holding one.
     */
    @Override
    protected void drawOverlay(SpriteBatch batch, int drawX, int drawY, boolean flip) {
        if (weaponArt == null || weapon == null || weapon.thrown()
                || swingIsThrow || !swing.busy()) {
            return;
        }
        TextureRegion held = ActorSprites.frameOf(weaponArt, facing,
            swing.elapsed(), Math.max(1, swing.totalSteps()));
        if (held == null) {
            return;
        }
        int w = held.getRegionWidth();
        int h = held.getRegionHeight();
        int x0 = drawX - WEAPON_INSET;
        int y0 = drawY - WEAPON_INSET;
        if (flip) {
            batch.draw(held, x0 + w, y0, -w, h);
        } else {
            batch.draw(held, x0, y0, w, h);
        }
    }

    @Override
    public TextureRegion frame() {
        if (sprites == null) {
            return null;
        }
        if (!alive()) {
            // A side-view hero carries a real death strip; the ninjas carry a
            // two-frame column. Whichever exists is played once and held on
            // its last frame.
            if (sprites.death != null) {
                return sprites.death.frame(facing, deadSteps);
            }
            TextureRegion[] d = sprites.dead;
            return d == null ? null
                : d[Math.min(d.length - 1, deadSteps / DEAD_STEPS_PER_FRAME)];
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
