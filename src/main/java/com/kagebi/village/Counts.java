package com.kagebi.village;

import com.badlogic.gdx.utils.ObjectIntMap;

/**
 * Counts in an {@link ObjectIntMap} that never holds a zero: a key that reaches
 * nothing is removed, so what the save lists is what there is.
 */
final class Counts {

    /** Adds {@code n}, which may be negative; a count that reaches zero or below is gone. */
    static void add(ObjectIntMap<String> counts, String key, int n) {
        int left = counts.get(key, 0) + n;
        if (left <= 0) {
            counts.remove(key, 0);
        } else {
            counts.put(key, left);
        }
    }

    /** Everything counted, summed. A fresh iterator, so it is safe inside another loop over the map. */
    static int total(ObjectIntMap<String> counts) {
        int n = 0;
        for (ObjectIntMap.Entry<String> e : new ObjectIntMap.Entries<>(counts)) {
            n += e.value;
        }
        return n;
    }

    private Counts() {}
}
