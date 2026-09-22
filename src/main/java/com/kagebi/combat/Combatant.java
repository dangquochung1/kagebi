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
     * Proportion of a hit shrugged off after armour, 0 to 1.
     *
     * <p>{@code Damage.incoming} has taken this since it was written and
     * {@code HitResolver} passed it a literal zero, so the parameter was real
     * and the value never was. Defaulted here rather than added to every
     * implementor: nothing in the game resists yet, and a percentage is a
     * different kind of defence from armour's flat soak - armour is worth most
     * against a hail of small hits, resistance against one large one.
     * {@code Damage.MAX_RESIST} caps it so nothing can become immune.
     */
    default float resist() {
        return 0f;
    }

    /**
     * What this one's relics do to a hit it takes: below 1 shrugs damage off,
     * above 1 takes more. Default 1, so nothing that has no relics has to care.
     */
    default float damageTakenMult() {
        return 1f;
    }

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
