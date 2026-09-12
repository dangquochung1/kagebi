package com.kagebi.entity;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.kagebi.Cfg;
import com.kagebi.Dir;
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
 */
public final class Projectile extends Entity {

    /** Small, so a shot threads the gaps it visually appears to thread. */
    public static final float BODY = 6f;
    /** A cloud is a little over a tile across; big enough to have to walk round. */
    public static final float HAZARD_W = 22f;
    public static final float HAZARD_H = 16f;

    private final Faction owner;
    private final float vx;
    private final float vy;
    private final int damage;
    private final float knockback;
    /** Animated and radially symmetric, so drawn unrotated. */
    private final Anim anim;
    /** A single image drawn pointing right, rotated to the heading. */
    private final TextureRegion still;
    /** Not consumed by a hit. I-frames, not removal, stop it hitting every step. */
    private final boolean pierce;

    private int armSteps;
    private int life;

    public Projectile(Faction owner, float x, float y, float dirX, float dirY,
                      float speed, int damage, float knockback, int lifeSteps,
                      Anim anim, TextureRegion still) {
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

    @Override
    public void step(EntityWorld world) {
        animSteps++;
        if (--life <= 0) {
            removed = true;
            return;
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
        boolean landed = owner == Faction.PLAYER
            ? HitResolver.resolve(box, world.hostiles(), null) > 0
            : HitResolver.hit(box, world.player(), null);
        if (landed && !pierce) {
            removed = true;
        }
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
            batch.draw(f, x - bodyW / 2f, y - bodyH / 2f, bodyW, bodyH);
            return;
        }
        float degrees = still != null
            ? (float) Math.toDegrees(Math.atan2(vy, vx)) : 0f;
        batch.draw(f, x - w / 2f, y - h / 2f, w / 2f, h / 2f, w, h, 1f, 1f, degrees);
    }
}
