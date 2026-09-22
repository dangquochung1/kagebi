package com.kagebi.save.migration;

import com.badlogic.gdx.utils.JsonValue;

/**
 * The six ninja colours become six characters again, and three heroes leave.
 *
 * <p>This is {@link V2ToV3} run backwards, and it is worth saying why rather
 * than pretending the first move was wrong. Folding the colours into skins was
 * right about the thing it objected to - five perks invented so that a hue
 * could be priced like a build - and wrong about the remedy. The remedy now is
 * that each colour carries one real perk, drawn from the same seven names the
 * village upgrades use, no two alike, and the green one carries none at all.
 * That last part is what makes the other five mean something.
 *
 * <p><b>Nothing bought is taken away here.</b> A profile that owned the blue
 * colour owns the blue ninja afterwards, at the same price it already paid.
 *
 * <p><b>Except the three that no longer exist.</b> Karasu, Kitsune and
 * Yamabushi were a second animation shape the roster could not carry, and they
 * are gone; a profile that bought them loses them and is not refunded. That is
 * a judgement and not an oversight. The five colours arrive here as characters
 * carrying perks they did not have when they were bought, which is worth more
 * than the three that go - and a migration that hands back six thousand seven
 * hundred gold would leave a long-running profile able to buy the rest of the
 * shelf outright, which is not a kindness to the game it is about to play.
 *
 * <p>The two spells go with Kitsune, for the same reason: they were hers, only
 * ever hers, and nothing in the roster can hold them now.
 */
public final class V4ToV5 implements Migration {

    /** Colour to the character it becomes, in unlock order. */
    private static final String[] COLOURS =
        {"green", "red", "blue", "dark", "fire", "water"};

    /** Gone: a different sheet shape, and the rule that came with the fox. */
    private static final String[] REMOVED_CHARACTERS = {"karasu", "kitsune", "yamabushi"};

    /** Gone with her. */
    private static final String[] REMOVED_WEAPONS = {"fireball", "waterball"};

    /** The one every profile has, whatever the file says. */
    private static final String STARTER = "ninjagreen";

    @Override
    public int from() {
        return 4;
    }

    @Override
    public void apply(JsonValue root) {
        JsonValue kept = new JsonValue(JsonValue.ValueType.array);
        kept.addChild(new JsonValue(STARTER));

        // Whatever was owned as a colour is owned as a ninja.
        for (JsonValue e = child(root, "unlockedSkins"); e != null; e = e.next) {
            String id = "ninja" + e.asString();
            if (!has(kept, id)) {
                kept.addChild(new JsonValue(id));
            }
        }
        // The old roster: "ninja" was the one entry the six colours hid behind,
        // and the other three no longer exist. Anything else is left alone -
        // a hand-edited file, or a character added after this was written.
        for (JsonValue e = child(root, "unlockedCharacters"); e != null; e = e.next) {
            String id = e.asString();
            if ("ninja".equals(id) || removed(id) || has(kept, id)) {
                continue;
            }
            kept.addChild(new JsonValue(id));
        }
        root.remove("unlockedCharacters");
        root.addChild("unlockedCharacters", kept);
        root.remove("unlockedSkins");

        JsonValue weapons = new JsonValue(JsonValue.ValueType.array);
        for (JsonValue e = child(root, "unlockedWeapons"); e != null; e = e.next) {
            if (!spell(e.asString())) {
                weapons.addChild(new JsonValue(e.asString()));
            }
        }
        root.remove("unlockedWeapons");
        root.addChild("unlockedWeapons", weapons);

        migrateSavedRun(root, root.getString("ninjaSkin", "green"));
        root.remove("ninjaSkin");
    }

    /**
     * The run in progress, which is the half of this that can crash.
     *
     * <p>An unknown character id reaches {@code ActorSprites} as an atlas path
     * that is not there, and the throw comes out of the top of {@code render}.
     * A run saved as the fox has to come back as somebody, so it comes back as
     * the starter - the alternative is dropping the run, and a player who left
     * off on floor five would rather change colour than lose the floor.
     */
    private static void migrateSavedRun(JsonValue root, String skin) {
        JsonValue run = root.get("savedRun");
        if (run == null || !run.isObject()) {
            return;
        }
        String who = run.getString("characterId", null);
        String became = "ninja".equals(who) ? "ninja" + run.getString("skinId", skin)
            : who == null || removed(who) ? STARTER : who;
        run.remove("characterId");
        run.addChild("characterId", new JsonValue(became));
        run.remove("skinId");
        if (spell(run.getString("throwWeaponId", null))) {
            // An empty off hand rather than a substitute: the player chose a
            // spell, and handing them a kunai instead would be answering a
            // question they did not ask.
            run.remove("throwWeaponId");
        }
    }

    private static JsonValue child(JsonValue root, String name) {
        JsonValue array = root.get(name);
        return array == null ? null : array.child;
    }

    private static boolean removed(String id) {
        return contains(REMOVED_CHARACTERS, id);
    }

    private static boolean spell(String id) {
        return contains(REMOVED_WEAPONS, id);
    }

    private static boolean contains(String[] ids, String id) {
        for (String each : ids) {
            if (each.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean has(JsonValue array, String value) {
        for (JsonValue e = array.child; e != null; e = e.next) {
            if (value.equals(e.asString())) {
                return true;
            }
        }
        return false;
    }
}
