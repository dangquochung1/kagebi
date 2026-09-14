package com.kagebi.save.migration;

import com.badlogic.gdx.utils.JsonValue;

/**
 * The save format's history, applied in order.
 *
 * <p>Adding a field to {@code Profile} needs no migration: a missing field
 * reads as its default. A migration is for the changes that would otherwise
 * lose data - a rename, a split, a unit change - and each one is a class
 * here plus a bump of {@code Profile.CURRENT_VERSION}.
 */
public final class Migrations {

    /** Oldest first. Each entry's {@code from()} is one more than the last. */
    private static final Migration[] CHAIN = {
        new V0ToV1(),
        new V1ToV2(),
    };

    /** The shipped chain, from version 0 to {@code to}. */
    public static int upgrade(JsonValue root, int from, int to) {
        return upgrade(root, from, to, CHAIN);
    }

    /**
     * Applies every step from {@code from} up to {@code to} and stamps the
     * result's version. Throws rather than skipping a missing step: a skipped
     * migration is a save that loads looking fine with a field silently
     * zeroed, which is the exact failure versioning exists to prevent.
     */
    public static int upgrade(JsonValue root, int from, int to, Migration... chain) {
        int version = from;
        while (version < to) {
            Migration step = find(chain, version);
            if (step == null) {
                throw new IllegalStateException("no migration from save version " + version
                    + " towards " + to);
            }
            step.apply(root);
            version++;
        }
        JsonValue v = root.get("version");
        if (v != null) {
            v.set(version, null);
        } else {
            root.addChild("version", new JsonValue(version));
        }
        return version;
    }

    private static Migration find(Migration[] chain, int from) {
        for (Migration m : chain) {
            if (m.from() == from) {
                return m;
            }
        }
        return null;
    }

    private Migrations() {}
}
