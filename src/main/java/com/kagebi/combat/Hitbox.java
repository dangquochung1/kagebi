package com.kagebi.combat;

import com.kagebi.Dir;

/**
 * One axis-aligned box of damage, alive for a single simulation step.
 *
 * <p>Hitboxes are values, not objects with a lifetime: whatever is swinging
 * builds a fresh one on each step of its active window and throws it away. That
 * is what keeps the geometry honest - the box is always derived from where the
 * attacker is <em>now</em>, so a knocked-back attacker's swing travels with it
 * instead of hanging in the air where the attack started.
 *
 * <p>Coordinates are the bottom-left corner in the same y-up virtual pixel
 * space as everything else. Nothing here touches libGDX, which is the only
 * reason the whole of {@code combat} is testable in plain JUnit.
 */
public final class Hitbox {

    public final float x;
    public final float y;
    public final float w;
    public final float h;

    /** Already scaled by relics and already crit-multiplied; armour is not applied yet. */
    public final int damage;
    /** Impulse in virtual pixels per second, before the target's resistance. */
    public final float knockback;
    public final Faction source;

    /**
     * Where the hit reads as coming from, for knockback direction. Usually the
     * attacker's centre rather than the box's, so that a long weapon shoves the
     * target away from the body instead of sideways off the blade tip.
     */
    public final float originX;
    public final float originY;

    public Hitbox(float x, float y, float w, float h, int damage, float knockback,
                  Faction source, float originX, float originY) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.damage = damage;
        this.knockback = knockback;
        this.source = source;
        this.originX = originX;
        this.originY = originY;
    }

    /**
     * The player's swing: a box projected from the attacker's centre along its
     * facing.
     *
     * <p>It is anchored <em>at</em> the centre rather than in front of the body,
     * so an enemy pressed against the player is still inside it. A box that
     * starts at the edge of the body leaves a dead ring at point-blank range,
     * and players read that as the game refusing to register hits.
     *
     * @param reach     how far out the box goes, {@code WeaponDef.reach}
     * @param halfWidth half the box across the swing, {@code WeaponDef.width}
     */
    public static Hitbox swing(float cx, float cy, Dir facing, float reach, float halfWidth,
                               int damage, float knockback, Faction source) {
        float x;
        float y;
        float w;
        float h;
        switch (facing) {
            case RIGHT:
                x = cx; y = cy - halfWidth; w = reach; h = halfWidth * 2f; break;
            case LEFT:
                x = cx - reach; y = cy - halfWidth; w = reach; h = halfWidth * 2f; break;
            case UP:
                x = cx - halfWidth; y = cy; w = halfWidth * 2f; h = reach; break;
            default: // DOWN
                x = cx - halfWidth; y = cy - reach; w = halfWidth * 2f; h = reach; break;
        }
        return new Hitbox(x, y, w, h, damage, knockback, source, cx, cy);
    }

    /** A body-shaped box, for contact damage and for projectiles. */
    public static Hitbox body(float cx, float cy, float w, float h, int damage,
                              float knockback, Faction source) {
        return new Hitbox(cx - w / 2f, cy - h / 2f, w, h, damage, knockback, source, cx, cy);
    }

    /**
     * Plain open-interval AABB overlap against a centred box.
     *
     * <p>Deliberately not the one-pixel-inset rule {@code CollisionGrid} uses.
     * That inset exists so a tile-wide body fits a tile-wide corridor; between
     * two actors there is no grid to fit into, and insetting here would make
     * hits at the very edge of a weapon silently miss.
     */
    public boolean overlapsCentred(float cx, float cy, float bw, float bh) {
        float bx = cx - bw / 2f;
        float by = cy - bh / 2f;
        return bx < x + w && bx + bw > x && by < y + h && by + bh > y;
    }

    public float centreX() {
        return x + w / 2f;
    }

    public float centreY() {
        return y + h / 2f;
    }

    @Override
    public String toString() {
        return "Hitbox(" + x + "," + y + " " + w + "x" + h + " dmg=" + damage
            + " " + source + ")";
    }
}
