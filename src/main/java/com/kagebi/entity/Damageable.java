package com.kagebi.entity;

import com.kagebi.combat.Combatant;

/**
 * An entity with hit points.
 *
 * <p>Adds only what the simulation needs on top of {@link Combatant}, which is
 * the narrower contract {@code combat} resolves hits against. Combat never sees
 * this interface; the world does.
 */
public interface Damageable extends Combatant {

    int hp();

    int maxHp();

    default boolean dead() {
        return hp() <= 0;
    }
}
