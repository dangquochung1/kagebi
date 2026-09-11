package com.kagebi.data.def;

/**
 * A melee weapon or a thrown one, from {@code assets/data/weapons.json}.
 *
 * <p>The pack ships five held weapons drawn to overlay the player sprite -
 * katana, axe, hammer, pickaxe, net - plus thrown kunai and shuriken with a
 * full rotation sheet. A def carrying a {@link #projectile} is thrown; one
 * without swings.
 */
public final class WeaponDef {

    public final String id;
    public final String nameKey;
    public final String descKey;

    /** Region in {@code actors.atlas}, e.g. {@code player/weapons/katana}. */
    public final String sprite;
    /** Index into the Raven icon grid for the inventory, or -1 to use the sprite. */
    public final int icon;

    public final int damage;
    /** How far the hitbox reaches from the player centre, in pixels. */
    public final float reach;
    /** Half-width of the swing box in pixels; a hammer is wider than a katana. */
    public final float width;

    public final int windupSteps;
    public final int activeSteps;
    public final int recoverSteps;

    public final float knockback;
    /** Extra steps the player cannot move: a hammer commits, a katana does not. */
    public final int rootSteps;

    /** Projectile id for a thrown weapon, or null for a melee swing. */
    public final String projectile;

    public WeaponDef(String id, String nameKey, String descKey, String sprite,
                     int icon, int damage, float reach, float width,
                     int windupSteps, int activeSteps, int recoverSteps,
                     float knockback, int rootSteps, String projectile) {
        this.id = id;
        this.nameKey = nameKey;
        this.descKey = descKey;
        this.sprite = sprite;
        this.icon = icon;
        this.damage = damage;
        this.reach = reach;
        this.width = width;
        this.windupSteps = windupSteps;
        this.activeSteps = activeSteps;
        this.recoverSteps = recoverSteps;
        this.knockback = knockback;
        this.rootSteps = rootSteps;
        this.projectile = projectile;
    }

    public boolean thrown() {
        return projectile != null;
    }

    @Override
    public String toString() {
        return "WeaponDef(" + id + ")";
    }
}
