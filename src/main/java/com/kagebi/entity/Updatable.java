package com.kagebi.entity;

/** Anything the room simulation advances once per fixed step. */
public interface Updatable {

    /**
     * Advances one fixed step of {@link com.kagebi.Cfg#STEP}.
     *
     * <p>The world is passed in rather than held as a field so that an entity
     * carries no back-reference it could outlive: entities are created and
     * dropped on every room change, the world is not.
     */
    void step(EntityWorld world);
}
