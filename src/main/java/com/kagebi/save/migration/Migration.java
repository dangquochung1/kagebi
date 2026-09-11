package com.kagebi.save.migration;

import com.badlogic.gdx.utils.JsonValue;

/**
 * One step in the save format's history: reads a profile tree written at
 * {@link #from()} and rewrites it, in place, into the shape of {@code from()+1}.
 *
 * <p>Migrations work on the raw JSON tree rather than on {@code Profile}
 * because the whole point is that the old shape no longer matches the class.
 * A field renamed next month has to be moved while both names are still just
 * strings.
 *
 * <p>Nothing in this package may touch {@code Gdx} or the graphics classes:
 * a migration is a pure function of a tree, and is tested as one.
 */
public interface Migration {

    /** The version this step reads. It produces {@code from() + 1}. */
    int from();

    /** Rewrites {@code root} in place. Leave {@code version} alone; the chain sets it. */
    void apply(JsonValue root);
}
