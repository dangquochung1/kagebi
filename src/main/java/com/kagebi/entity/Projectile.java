package com.kagebi.entity;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.kagebi.Cfg;
import com.kagebi.Dir;
import com.kagebi.combat.Combatant;
import com.kagebi.combat.Faction;
import com.kagebi.combat.HitResolver;
import com.kagebi.combat.Hitbox;
import com.kagebi.gen.CollisionGrid;
import com.kagebi.gfx.Anim;

/**
 * Something in flight, or something left lying on the floor.
 *
 * <p>A thrown kunai, a shooter's orb, and a caster's lingering cloud are the
 * same object with different numbers: a cloud is a projectile with no speed
 * that pierces, and an arming delay before it bites. One class rather than
 * three keeps "what hurts the player from a distance" in one place.
 *
 * <p>Carries its own faction rather than a reference to whoever threw it, so it
 * still knows whose it was after that enemy has died and been dropped from the
 * room. And it is tested against walls before actors: the other order lets a
 * shot land through a wall on the step it should have been absorbed, which
 * players read, correctly, as the wall not working.
 *
 * <p>A third shape joined the two with stage 6: a <b>lob</b>, which travels to
 * a fixed point over a fixed number of steps, is drawn lifted off the ground
 * by a parabola, and leaves a hazard where it lands. It ignores walls while it
 * is in the air, and that is the whole reason it exists - a fountain of water
 * spells that stopped at the first pillar would not read as a fountain.
 * {@code screen/island/HarvestFx} does the same arc for the village.
 */
public final class Projectile extends Entity {

    /** Small, so a shot threads the gaps it visually appears to thread. */
    public static final float BODY = 6f;
    /** A cloud is a little over a tile across; big enough to have to walk round. */
    public static final float HAZARD_W = 22f;
    public static final float HAZARD_H = 16f;

    /**
     * How big the puddle a spell leaves is drawn, as opposed to how big it
     * bites.
     *
     * <p>The hitbox stays {@link #HAZARD_W} by {@link #HAZARD_H}, because that
     * is a balance number and the fountain's rings were laid out against it.
     * The art is a 48px pool of fire and squeezing it into 22x16 flattened it
     * into a smear. Art may overhang its hitbox - it does everywhere else in
     * this game - and a burning pool that looks slightly bigger than it is
     * errs in the direction the player will forgive.
     */
    public static final float BURST_W = 32f;
    public static final float BURST_H = 32f;

    /** Gravity for a lob, in pixels per step squared. The village uses 0.3f. */
    public static final float LOB_GRAVITY = 0.3f;

    private final Faction owner;
    /**
     * Velocity. Not final since stage 6: a homing shot turns, and a lob eases.
     * Everything else writes these once in the constructor and never again.
     */
    private float vx;
    private float vy;
    private final int damage;
    private final float knockback;
    /** Animated and radially symmetric, so drawn unrotated. */
    private final Anim anim;
    /** A single image drawn pointing right, rotated to the heading. */
    private final TextureRegion still;
    /** Not consumed by a hit. I-frames, not removal, stop it hitting every step. */
    private final boolean pierce;
    /** Animated but drawn pointing right, so it is turned to its heading. */
    private boolean aimed;
    /**
     * Whether the throw that launched this one crit. Carried rather than
     * recomputed: the roll happened once, when the arm went back, and every
     * shot of a fanned throw is that one roll - rolling again per projectile
     * would let a bandolier throw crit three times out of one press.
     */
    private final boolean crit;

    /** Whoever this lands on, this step, reused rather than allocated. */
    private final java.util.List<Combatant> struck = new java.util.ArrayList<>();

    private int armSteps;
    private int life;

    /**
     * Height above the floor, for a lob. Zero for everything else, and drawn
     * as an offset only: {@link #x} and {@link #y} stay the ground track, so
     * the hitbox, the wall test and the depth sort all see a flat world.
     */
    private float z;
    private float vz;
    /** Steps of flight left. Above zero means airborne: no walls, no hitbox. */
    private int flightSteps;
    /**
     * Where a lob started, how far it has to go, and how long it has.
     *
     * <p>The ground track of an arc is interpolated over these rather than
     * integrated from a velocity, which is what lets it ease: it leaves fast,
     * slows as it rises, and is barely moving sideways by the time it tips
     * over and drops. A constant horizontal speed with a parabola stuck on top
     * is the shape a brick makes, and that is what the fountain looked like.
     */
    private float fromX;
    private float fromY;
    private float spanX;
    private float spanY;
    private int spanTotal;
    private int flown;
    /** What a lob leaves behind on landing, or null. */
    private Anim landAnimSet;
    private int landStepsSet;

    /**
     * Size to draw a hazard at, or zero to stretch it to the hitbox.
     *
     * <p>Only what a spell leaves behind sets it; the generic cloud keeps the
     * old behaviour, which its art was drawn for.
     */
    private float drawW;
    private float drawH;

    /** Steps of steering left, and how far it may turn in one of them. */
    private int homeSteps;
    private float turnRate;

    /**
     * A fixed heading to draw at, in degrees, or NaN for "read it off the
     * velocity". Only a falling spell sets it: it has no horizontal velocity
     * at all, so {@code atan2(vy, vx)} is atan2(0, 0) and every drop would
     * point stubbornly east.
     */
    private float fixedAngle = Float.NaN;

    public Projectile(Faction owner, float x, float y, float dirX, float dirY,
                      float speed, int damage, float knockback, int lifeSteps,
                      Anim anim, TextureRegion still, boolean crit) {
        this.owner = owner;
        this.x = x;
        this.y = y;
        this.vx = dirX * speed;
        this.vy = dirY * speed;
        this.damage = damage;
        this.knockback = knockback;
        this.life = lifeSteps;
        this.anim = anim;
        this.still = still;
        this.pierce = false;
        this.crit = crit;
        this.bodyW = BODY;
        this.bodyH = BODY;
        this.facing = Dir.of(dirX, dirY);
        this.hp = 1;
        this.maxHp = 1;
    }

    private Projectile(Faction owner, float x, float y, int damage, int armSteps,
                       int lifeSteps, Anim anim) {
        this.owner = owner;
        this.x = x;
        this.y = y;
        this.vx = 0f;
        this.vy = 0f;
        this.damage = damage;
        this.knockback = 30f;
        this.armSteps = armSteps;
        this.life = lifeSteps;
        this.anim = anim;
        this.still = null;
        this.pierce = true;
        this.crit = false;
        this.bodyW = HAZARD_W;
        this.bodyH = HAZARD_H;
        this.hp = 1;
        this.maxHp = 1;
    }

    /** A stationary damaging area that arms after a delay and lingers. */
    public static Projectile hazard(Faction owner, float x, float y, int damage,
                                    int armSteps, int lifeSteps, Anim anim) {
        return new Projectile(owner, x, y, damage, armSteps, lifeSteps, anim);
    }

    /**
     * Something thrown in an arc at a point, which lands as a hazard.
     *
     * <p>The flight is interpolated rather than simulated: the horizontal
     * speed is simply the distance over the time, and the vertical throw is
     * chosen so the parabola is back at zero on the last step. Simulating it
     * would mean solving for a launch angle that lands on the target, and an
     * arc that missed by a few pixels would leave its puddle beside the ring
     * it was aimed at - which, for an attack whose whole point is the pattern
     * it draws on the floor, is the one error that shows.
     */
    public static Projectile lob(Faction owner, float fromX, float fromY,
                                 float toX, float toY, int damage, int flightSteps,
                                 int lingerSteps, Anim flightAnim, Anim landAnim) {
        int steps = Math.max(1, flightSteps);
        // Two steps of life past the flight, so the arc finishes before the
        // life counter can cut it short.
        Projectile p = new Projectile(owner, fromX, fromY, 1f, 0f, 0f,
            damage, 40f, steps + 2, flightAnim, null, false, landAnim, lingerSteps);
        p.flightSteps = steps;
        // The ground track is interpolated over these, not integrated from a
        // velocity; see stepFlight for why, and Projectile.aimed for how it
        // still ends up pointing the right way.
        p.fromX = fromX;
        p.fromY = fromY;
        p.spanX = toX - fromX;
        p.spanY = toY - fromY;
        p.spanTotal = steps;
        // Up fast enough that the parabola is back on the floor as it arrives.
        p.vz = LOB_GRAVITY * steps / 2f;
        p.aimed = true;
        return p;
    }

    /**
     * A spell dropped out of the sky onto a point, which burns where it lands.
     *
     * <p>The rain the cove's orbs make. It used to be a bare
     * {@link #hazard}: the spell simply appeared on the floor, armed, and bit
     * - which reads as something climbing out of the ground rather than
     * something falling on you, and was reported as exactly that.
     *
     * <p>Falling from rest rather than being thrown, so the height follows
     * from the time: {@code g * n(n+1)/2} is where it has to start for the
     * same gravity the lob uses to put it on the floor on step n. No
     * arming delay, because the fall itself is the warning - the player can
     * see where it is going for the whole of its descent, which a blinking
     * decal on the floor only approximates.
     */
    public static Projectile fall(Faction owner, float x, float y, int damage,
                                  int fallSteps, int lingerSteps,
                                  Anim flightAnim, Anim landAnim) {
        int steps = Math.max(1, fallSteps);
        Projectile p = new Projectile(owner, x, y, 1f, 0f, 0f,
            damage, 40f, steps + 2, flightAnim, null, false, landAnim, lingerSteps);
        p.flightSteps = steps;
        p.z = LOB_GRAVITY * steps * (steps + 1) / 2f;
        p.vz = 0f;
        // Straight down. Nothing can be read off a velocity that is zero on
        // both axes, and the spell strips are all drawn pointing east.
        p.fixedAngle = -90f;
        return p;
    }

    private Projectile(Faction owner, float x, float y, float dirX, float dirY,
                       float speed, int damage, float knockback, int lifeSteps,
                       Anim anim, TextureRegion still, boolean crit,
                       Anim landAnim, int landSteps) {
        this(owner, x, y, dirX, dirY, speed, damage, knockback, lifeSteps, anim, still, crit);
        this.landAnimSet = landAnim;
        this.landStepsSet = landSteps;
    }

    @Override
    public Faction faction() {
        return owner;
    }

    public boolean armed() {
        return armSteps <= 0;
    }

    public boolean pierces() {
        return pierce;
    }

    /** Turns an animated projectile to face where it is going. */
    public Projectile aimed() {
        this.aimed = true;
        return this;
    }

    /** Draws a hazard at this size instead of stretching it to its hitbox. */
    public Projectile drawAs(float w, float h) {
        this.drawW = w;
        this.drawH = h;
        return this;
    }

    /** Makes this one steer at the player for a while. See AiContext.homingProjectile. */
    public Projectile homing(int steps, float turnRate) {
        this.homeSteps = steps;
        this.turnRate = turnRate;
        return this;
    }

    /**
     * Steers the heading toward the player, by at most {@link #turnRate}.
     *
     * <p>The speed is preserved exactly - the velocity is rotated, not
     * re-aimed - so a homing shot never accelerates into the player, which is
     * the failure mode that makes homing feel unfair rather than tense.
     */
    private void home(EntityWorld world) {
        Player target = world.player();
        if (target == null || !target.alive()) {
            homeSteps = 0;
            return;
        }
        homeSteps--;
        float speed = (float) Math.sqrt(vx * vx + vy * vy);
        if (speed < 0.001f) {
            return;
        }
        double current = Math.atan2(vy, vx);
        double wanted = Math.atan2(target.y - y, target.x - x);
        // The signed shortest way round, so a shot behind the player turns the
        // near way rather than taking the long road through pi.
        double delta = Math.atan2(Math.sin(wanted - current), Math.cos(wanted - current));
        double turn = Math.max(-turnRate, Math.min(turnRate, delta));
        double heading = current + turn;
        vx = (float) Math.cos(heading) * speed;
        vy = (float) Math.sin(heading) * speed;
        facing = Dir.of(vx, vy);
    }

    @Override
    public void step(EntityWorld world) {
        animSteps++;
        if (--life <= 0) {
            land(world);
            removed = true;
            return;
        }

        if (flightSteps > 0) {
            // Airborne: no wall, no hitbox, no landing yet. It is over the
            // room, not in it.
            stepFlight(world);
            return;
        }

        if (homeSteps > 0) {
            home(world);
        }

        x += vx * Cfg.STEP;
        y += vy * Cfg.STEP;

        CollisionGrid grid = world.collision();
        if (!pierce && grid != null && grid.overlaps(x - bodyW / 2f, y - bodyH / 2f, bodyW, bodyH)) {
            removed = true;
            return;
        }
        if (armSteps > 0) {
            armSteps--;
            return;
        }

        Hitbox box = Hitbox.body(x, y, bodyW, bodyH, damage, knockback, owner);
        boolean landed;
        if (owner == Faction.PLAYER) {
            // Collected, not counted. A thrown weapon used to pass null here
            // and tell the world nothing, so a kunai that killed an enemy did
            // it in silence: no damage number, no health bar, no hit sound,
            // and no on-hit relic. The main hand has always reported itself;
            // see Player.resolveSwing, which this now mirrors.
            struck.clear();
            landed = HitResolver.resolve(box, world.hostiles(), null, struck) > 0;
            if (landed) {
                world.onThrownHitLanded(struck, damage, crit);
            }
        } else {
            landed = HitResolver.hit(box, world.player(), null);
        }
        if (landed && !pierce) {
            removed = true;
        }
    }

    /**
     * One step of an arc or a fall.
     *
     * <p>The ground track eases out - {@code 1-(1-t)^2}, whose slope is twice
     * the average at launch and zero on arrival - while the height stays the
     * honest parabola. Together they read as a throw: away fast and low, up,
     * a moment of nearly nothing at the top, then over and down. It still
     * lands on the exact step and the exact pixel it was aimed at, which the
     * fountain's ring pattern depends on.
     *
     * <p>{@link #vx}/{@link #vy} are written back as the step's real
     * displacement so that {@link #angle()} turns the sprite along the path
     * without knowing any of this.
     */
    private void stepFlight(EntityWorld world) {
        flown++;
        if (spanTotal > 0) {
            float t = Math.min(1f, flown / (float) spanTotal);
            float eased = 1f - (1f - t) * (1f - t);
            float nx = fromX + spanX * eased;
            float ny = fromY + spanY * eased;
            vx = (nx - x) / Cfg.STEP;
            vy = (ny - y) / Cfg.STEP;
            x = nx;
            y = ny;
        }
        z += vz;
        vz -= LOB_GRAVITY;
        if (--flightSteps <= 0) {
            z = 0f;
            land(world);
            removed = true;
        }
    }

    /** Leaves the puddle, if this was a lob. Harmless for anything else. */
    private void land(EntityWorld world) {
        if (landStepsSet <= 0) {
            return;
        }
        Projectile pool = hazard(owner, x, y, damage, 0, landStepsSet, landAnimSet);
        pool.drawW = BURST_W;
        pool.drawH = BURST_H;
        world.projectiles().add(pool);
        landStepsSet = 0;
    }

    /** Projectiles are not targets; nothing in the game shoots one down. */
    @Override
    public void takeHit(int amount, float fromX, float fromY, float kb) {
    }

    @Override
    protected boolean castsShadow() {
        return false;
    }

    @Override
    public TextureRegion frame() {
        if (still != null) {
            return still;
        }
        return anim == null ? null : anim.frame(facing, animSteps);
    }

    @Override
    public void draw(SpriteBatch batch) {
        TextureRegion f = frame();
        if (f == null) {
            return;
        }
        // An unarmed cloud blinks: it is a warning, and a warning that looked
        // identical to the real thing would not be one.
        if (!armed() && animSteps % 6 >= 3) {
            return;
        }
        float w = f.getRegionWidth();
        float h = f.getRegionHeight();
        if (pierce) {
            float dw = drawW > 0f ? drawW : bodyW;
            float dh = drawH > 0f ? drawH : bodyH;
            batch.draw(f, x - dw / 2f, y - dh / 2f, dw, dh);
            return;
        }
        batch.draw(f, x - w / 2f, y + z - h / 2f, w / 2f, h / 2f, w, h, 1f, 1f, angle());
    }

    /**
     * Which way this is drawn pointing, in degrees.
     *
     * <p>Three cases. A falling spell has a heading nailed on, because it has
     * no horizontal velocity to read one from. Anything in the air is turned
     * along the way it moves <em>on screen</em>, which includes its height:
     * that is what makes an arc nose up as it leaves, level off at the top and
     * tip over into the fall, instead of sliding across the room at a fixed
     * angle like a thrown brick. Everything else reads its ground heading, and
     * a radially symmetric shot is not turned at all.
     */
    private float angle() {
        if (!Float.isNaN(fixedAngle)) {
            return fixedAngle;
        }
        if (still == null && !aimed) {
            return 0f;
        }
        // vz is a per-step displacement and vy is per second; the divide puts
        // them in the same units before they are added.
        float screenY = flightSteps > 0 || z > 0f ? vy + vz / Cfg.STEP : vy;
        return (float) Math.toDegrees(Math.atan2(screenY, vx));
    }
}
