package com.kagebi.combat;

/**
 * The only thing {@code combat} knows about an entity.
 *
 * <p>Kept to a handful of primitives on purpose. {@code HitResolver} works
 * against this rather than against {@code entity.Entity}, so the whole of
 * combat can be exercised with a ten-line fake in a unit test and never needs a
 * texture, an atlas or a GL context.
 */
public interface Combatant {

    float centreX();

    float centreY();

    /** Hurtbox width in virtual pixels, centred on {@link #centreX}. */
    float bodyWidth();

    float bodyHeight();

    Faction faction();

    boolean alive();

    boolean invulnerable();

    /** Flat damage soak, before percentage resistance. */
    int armour();

    /**
     * Takes a hit that has already passed the faction, overlap and
     * invulnerability checks.
     *
     * <p>The victim applies its own i-frames and its own knockback resistance
     * rather than being told: those belong to the thing being hit, and putting
     * them here is what lets an armoured boss and a slime share one resolver.
     *
     * @param fromX the attacker's centre, not the hitbox's - a long weapon
     *              should shove a target away from the body, not sideways off
     *              the tip of the blade
     */
    void takeHit(int damage, float fromX, float fromY, float knockback);
}
