package com.kagebi.save.migration;

import com.badlogic.gdx.utils.JsonValue;

/**
 * The village stopped darkening, and the upgrade that slowed it was withdrawn.
 *
 * <p>Two things leave the file. {@code villageDarkness} counted failed descents
 * for a dusk wash over the hub; the wash is gone, so the count means nothing.
 * And {@code flamekeeper} was a shop upgrade whose only effect was slowing that
 * count. Left in a save it would be gold spent on nothing, so every level owned
 * is paid back.
 *
 * <p>The prices are written down here rather than read from the shop, because
 * a migration is a pure function of the save tree and the shop no longer sells
 * the thing: these are what it cost when it did.
 */
final class V1ToV2 implements Migration {

    /** What each level of the flamekeeper cost, in order. */
    static final int[] FLAMEKEEPER_COSTS = {1800, 3800};

    @Override
    public int from() {
        return 1;
    }

    @Override
    public void apply(JsonValue root) {
        root.remove("villageDarkness");
        JsonValue upgrades = root.get("upgrades");
        JsonValue keeper = upgrades == null ? null : upgrades.get("flamekeeper");
        if (keeper == null) {
            return;
        }
        // A hand-edited level past the top of the track was never paid for.
        int levels = Math.max(0, Math.min(FLAMEKEEPER_COSTS.length, keeper.asInt()));
        int refund = 0;
        for (int i = 0; i < levels; i++) {
            refund += FLAMEKEEPER_COSTS[i];
        }
        upgrades.remove("flamekeeper");
        JsonValue gold = root.get("gold");
        if (gold == null) {
            root.addChild("gold", new JsonValue(refund));
        } else {
            gold.set(gold.asLong() + refund, null);
        }
    }
}
