package com.kagebi.combat;

/**
 * Who a hit belongs to, so nothing can hurt its own side.
 *
 * <p>A faction rather than a boolean because projectiles outlive their shooter:
 * a kunai in flight has to remember whose it was after the enemy that threw it
 * is already off the entity list.
 */
public enum Faction {

    PLAYER,
    ENEMY,
    /** Environmental: spike traps and explosions, which hurt everyone. */
    HAZARD;

    /** Whether a hit from {@code this} may land on {@code other}. */
    public boolean hostileTo(Faction other) {
        return this == HAZARD || other == HAZARD || this != other;
    }
}
