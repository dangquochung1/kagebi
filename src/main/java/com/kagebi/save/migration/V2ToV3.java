package com.kagebi.save.migration;

import com.badlogic.gdx.utils.JsonValue;

/**
 * The six ninja recolours stop being six characters.
 *
 * <p>Each colour used to be a roster entry with a shop unlock and a run-start
 * perk, because {@code ContentValidator} requires a perk of anything calling
 * itself a character - "a perk is what makes a character a choice rather than
 * a palette". They were a palette, and the rule was being satisfied by
 * inventing statistics for a hue. The ninja is now one character with six
 * colours, and a colour changes nothing.
 *
 * <p><b>Nothing bought is taken away.</b> A profile that owned {@code ninjafire}
 * owns the fire colour afterwards, and gold already spent stays spent: the
 * player still has the thing they paid for. No refund is made for the perks
 * that went with them, which is a judgement rather than an oversight - the
 * five perks were worth 600 to 2000 gold apiece and refunding them would hand
 * a long-running profile several thousand gold it never banked.
 *
 * <p>The character id is added unconditionally. A v2 profile always owned
 * {@code ninjagreen} - {@code Profile}'s constructor put it there and
 * {@code SaveManager} adds to the unlock sets rather than replacing them - so
 * there is no such thing as a saved profile with no ninja.
 */
public final class V2ToV3 implements Migration {

    /** Old character id to the colour it becomes, in unlock order. */
    private static final String[][] SKINS = {
        {"ninjagreen", "green"},
        {"ninjared", "red"},
        {"ninjablue", "blue"},
        {"ninjadark", "dark"},
        {"ninjafire", "fire"},
        {"ninjawater", "water"},
    };

    @Override
    public int from() {
        return 2;
    }

    @Override
    public void apply(JsonValue root) {
        JsonValue characters = root.get("unlockedCharacters");
        JsonValue skins = new JsonValue(JsonValue.ValueType.array);
        JsonValue kept = new JsonValue(JsonValue.ValueType.array);
        kept.addChild(new JsonValue("ninja"));

        for (JsonValue e = characters == null ? null : characters.child; e != null; e = e.next) {
            String id = e.asString();
            String skin = skinFor(id);
            if (skin != null) {
                skins.addChild(new JsonValue(skin));
            } else {
                // Not one of the six. A hand-edited file, or a character added
                // after this migration was written; either way it is not this
                // migration's business to drop it.
                kept.addChild(new JsonValue(id));
            }
        }
        // Green is free and always was, so it is owned whatever the file said.
        if (!has(skins, "green")) {
            skins.addChild(new JsonValue("green"));
        }

        root.remove("unlockedCharacters");
        root.addChild("unlockedCharacters", kept);
        root.addChild("unlockedSkins", skins);
        root.addChild("ninjaSkin", new JsonValue("green"));
    }

    private static String skinFor(String characterId) {
        for (String[] pair : SKINS) {
            if (pair[0].equals(characterId)) {
                return pair[1];
            }
        }
        return null;
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
