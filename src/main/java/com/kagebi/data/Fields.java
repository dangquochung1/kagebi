package com.kagebi.data;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectIntMap;

/**
 * Reads one JSON object into constructor arguments, and remembers which keys
 * it was asked for.
 *
 * <p>The remembering is the point. An optional field with a typo in its name -
 * {@code "flyng": true} - is otherwise read as absent, takes its default, and
 * produces a bat that walks into pits with nothing anywhere reporting a
 * problem. {@link #done()} lists every key nobody asked for.
 *
 * <p>Problems are collected rather than thrown so that a content author fixing
 * a file sees all of its mistakes in one run, not one per restart.
 */
final class Fields {

    private final JsonValue json;
    private final List<String> problems;
    private final Set<String> read = new HashSet<>();
    private String where;

    Fields(JsonValue json, String where, List<String> problems) {
        this.json = json;
        this.where = where;
        this.problems = problems;
        if (!json.isObject()) {
            problem("expected an object, found " + json.type());
        }
    }

    /** Reads the id and uses it in every later message, which is what makes them findable. */
    String id() {
        String id = string("id");
        if (id != null) {
            where = where + " '" + id + "'";
        }
        return id;
    }

    /** For defs keyed by something other than an id, such as a floor's number. */
    void label(String label) {
        where = where + " " + label;
    }

    String where() {
        return where;
    }

    /** So a nested object - a loot entry - reports into the same list. */
    List<String> problemsSink() {
        return problems;
    }

    void problem(String message) {
        problems.add(where + ": " + message);
    }

    private JsonValue get(String name, boolean required) {
        read.add(name);
        JsonValue v = json.get(name);
        if (v == null || v.isNull()) {
            if (required) {
                problem("missing '" + name + "'");
            }
            return null;
        }
        return v;
    }

    String string(String name) {
        JsonValue v = get(name, true);
        if (v == null) {
            return null;
        }
        if (!v.isString()) {
            problem("'" + name + "' should be a string");
            return null;
        }
        return v.asString();
    }

    String stringOr(String name, String fallback) {
        JsonValue v = get(name, false);
        if (v == null) {
            return fallback;
        }
        if (!v.isString()) {
            problem("'" + name + "' should be a string");
            return fallback;
        }
        return v.asString();
    }

    int integer(String name) {
        JsonValue v = get(name, true);
        return v == null ? 0 : asInt(name, v);
    }

    int integerOr(String name, int fallback) {
        JsonValue v = get(name, false);
        return v == null ? fallback : asInt(name, v);
    }

    private int asInt(String name, JsonValue v) {
        if (!v.isNumber()) {
            problem("'" + name + "' should be a number");
            return 0;
        }
        double d = v.asDouble();
        if (d != Math.rint(d)) {
            // 1.5 steps is not a duration the simulation can represent; silently
            // truncating it would ship a different number from the one typed.
            problem("'" + name + "' should be a whole number, found " + d);
        }
        return (int) d;
    }

    float number(String name) {
        JsonValue v = get(name, true);
        if (v == null) {
            return 0f;
        }
        if (!v.isNumber()) {
            problem("'" + name + "' should be a number");
            return 0f;
        }
        return v.asFloat();
    }

    float numberOr(String name, float fallback) {
        JsonValue v = get(name, false);
        if (v == null) {
            return fallback;
        }
        if (!v.isNumber()) {
            problem("'" + name + "' should be a number");
            return fallback;
        }
        return v.asFloat();
    }

    boolean bool(String name, boolean fallback) {
        JsonValue v = get(name, false);
        if (v == null) {
            return fallback;
        }
        if (!v.isBoolean()) {
            problem("'" + name + "' should be true or false");
            return fallback;
        }
        return v.asBoolean();
    }

    String[] strings(String name) {
        JsonValue v = get(name, true);
        if (v == null) {
            return new String[0];
        }
        if (!v.isArray()) {
            problem("'" + name + "' should be an array");
            return new String[0];
        }
        String[] out = new String[v.size];
        int i = 0;
        for (JsonValue e = v.child; e != null; e = e.next, i++) {
            if (!e.isString()) {
                problem("'" + name + "'[" + i + "] should be a string");
            }
            out[i] = e.isString() ? e.asString() : null;
        }
        return out;
    }

    /** An optional string array: missing is empty, not an error. */
    String[] stringsOr(String name) {
        return json.get(name) == null ? new String[0] : strings(name);
    }

    /** An optional float array, paired with {@link #stringsOr}. */
    float[] numbersOr(String name) {
        return json.get(name) == null ? new float[0] : numbers(name);
    }

    int[] integers(String name) {
        JsonValue v = get(name, true);
        if (v == null) {
            return new int[0];
        }
        if (!v.isArray()) {
            problem("'" + name + "' should be an array");
            return new int[0];
        }
        int[] out = new int[v.size];
        int i = 0;
        for (JsonValue e = v.child; e != null; e = e.next, i++) {
            out[i] = asInt(name + "[" + i + "]", e);
        }
        return out;
    }

    /**
     * A float array, for magnitudes that sit alongside a list of effect names.
     *
     * <p>{@link #integers} rejects a fractional value, deliberately: half a
     * step is a content error. A magnitude is the opposite - 1.08 is the
     * ordinary case - so this is a separate reader rather than a flag on that
     * one.
     */
    float[] numbers(String name) {
        JsonValue v = get(name, true);
        if (v == null) {
            return new float[0];
        }
        if (!v.isArray()) {
            problem("'" + name + "' should be an array");
            return new float[0];
        }
        float[] out = new float[v.size];
        int i = 0;
        for (JsonValue e = v.child; e != null; e = e.next, i++) {
            if (!e.isNumber()) {
                problem("'" + name + "'[" + i + "] should be a number");
                continue;
            }
            out[i] = e.asFloat();
        }
        return out;
    }

    /** The raw child, for nested arrays of objects. */
    JsonValue child(String name) {
        return get(name, true);
    }

    /**
     * An icon, given either as a name from {@code icons.json} or as a raw
     * index. Absent means -1, which every def reads as "no icon".
     */
    int icon(String name, ObjectIntMap<String> icons) {
        JsonValue v = get(name, false);
        if (v == null) {
            return -1;
        }
        if (v.isNumber()) {
            return asInt(name, v);
        }
        if (v.isString()) {
            int index = icons.get(v.asString(), Integer.MIN_VALUE);
            if (index == Integer.MIN_VALUE) {
                problem("icon '" + v.asString() + "' is not named in the icons section");
                return -1;
            }
            return index;
        }
        problem("'" + name + "' should be an icon name or an index");
        return -1;
    }

    <E extends Enum<E>> E enumeration(String name, Class<E> type) {
        String s = string(name);
        if (s == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, s);
        } catch (IllegalArgumentException e) {
            problem("'" + name + "' is '" + s + "', which is not one of "
                + java.util.Arrays.toString(type.getEnumConstants()));
            return null;
        }
    }

    /** Reports every key that was present but never read. */
    void done() {
        for (JsonValue e = json.child; e != null; e = e.next) {
            if (!read.contains(e.name)) {
                problem("unknown field '" + e.name + "' - a typo here would otherwise be silently ignored");
            }
        }
    }
}
