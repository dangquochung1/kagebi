package com.kagebi.save.migration;

import com.badlogic.gdx.utils.JsonValue;

/**
 * Version 0 is any save without a {@code version} field. None was ever
 * shipped - the placeholder save manager persisted nothing - so there is no
 * shape to convert and this step changes nothing.
 *
 * <p>It exists so that the chain has a first link before it is needed. The
 * migration that matters is the one written in a hurry next month, and the
 * cheapest time to prove the machinery that runs it is now, while the
 * machinery is the only thing under test.
 */
final class V0ToV1 implements Migration {

    @Override
    public int from() {
        return 0;
    }

    @Override
    public void apply(JsonValue root) {
        // The v1 shape is the v0 shape; the chain stamps the new version.
    }
}
