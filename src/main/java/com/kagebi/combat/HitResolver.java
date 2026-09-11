package com.kagebi.combat;

/**
 * Applies one hitbox to a list of candidates.
 *
 * <p>Everything about combat that is worth arguing over lives in the order of
 * the four tests below, so they are spelled out rather than inlined.
 */
public final class HitResolver {

    /**
     * @param swing the attack this box belongs to, or null for a box with no
     *              memory - contact damage and lingering hazards, which are
     *              supposed to be able to hurt the same target again as soon as
     *              its i-frames lapse
     * @return how many targets were actually hurt
     */
    public static int resolve(Hitbox box, Iterable<? extends Combatant> targets,
                              AttackState swing) {
        int hits = 0;
        for (Combatant t : targets) {
            if (hit(box, t, swing)) {
                hits++;
            }
        }
        return hits;
    }

    /** The single-target case, and where the ordering actually lives. */
    public static boolean hit(Hitbox box, Combatant target, AttackState swing) {
        if (target == null || !target.alive()) {
            return false;
        }
        if (!box.source.hostileTo(target.faction())) {
            return false;
        }
        if (!box.overlapsCentred(target.centreX(), target.centreY(),
                target.bodyWidth(), target.bodyHeight())) {
            return false;
        }
        // Claimed before the invulnerability test, not after. A player who
        // rolls through the active window of a swing has dodged that swing for
        // good; without this, a swing whose active window outlasts the roll
        // clips them on the way out and the dodge they earned reads as stolen.
        if (swing != null && !swing.markHit(target)) {
            return false;
        }
        if (target.invulnerable()) {
            return false;
        }
        int amount = Damage.incoming(box.damage, target.armour(), 0f);
        target.takeHit(amount, box.originX, box.originY, box.knockback);
        return true;
    }

    private HitResolver() {}
}
