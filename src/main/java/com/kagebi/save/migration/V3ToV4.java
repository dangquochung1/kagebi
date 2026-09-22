package com.kagebi.save.migration;

import com.badlogic.gdx.utils.JsonValue;

/**
 * The save learns to hold a run in progress.
 *
 * <p><b>Nothing is converted, and that is the whole of it.</b> Version 4 adds
 * one optional object, {@code savedRun}; a version 3 profile simply does not
 * have one, which reads as the default of null and means "nothing to continue".
 * The player's first visit to the village after upgrading writes one.
 *
 * <p>So why does this class exist at all? Because {@code Migrations.upgrade}
 * throws rather than skipping a missing step. That rule is deliberate - a
 * skipped migration is a save that loads looking fine with a field silently
 * zeroed - and the price of it is that a version bump with nothing to do has
 * to say so out loud. Saying so out loud is also the point: the chain is the
 * format's history, and a gap in it is a question nobody can answer later.
 */
public final class V3ToV4 implements Migration {

    @Override
    public int from() {
        return 3;
    }

    @Override
    public void apply(JsonValue root) {
        // Intentionally empty. See the class comment: the new field is
        // optional and its absence is already the correct value.
    }
}
