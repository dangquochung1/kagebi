package com.kagebi.ui;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.Scaling;

/**
 * Draws a key as the pack's own key-cap art instead of a name.
 *
 * <p>There are icons for 60 keys, which covers what anyone will bind, but not
 * what every keyboard has. An unmapped key falls back to its name as text - an
 * unrecognised key must never render as an invisible button.
 */
public final class KeyPrompts {

    private static final String PREFIX = "ui/input/keyboard/";
    private static final IntMap<String> REGIONS = new IntMap<>();

    static {
        for (int k = Keys.A; k <= Keys.Z; k++) {
            REGIONS.put(k, "key" + (char) ('a' + k - Keys.A));
        }
        for (int k = Keys.NUM_0; k <= Keys.NUM_9; k++) {
            REGIONS.put(k, "key" + (k - Keys.NUM_0));
        }
        for (int i = 1; i <= 12; i++) {
            REGIONS.put(Keys.F1 + i - 1, "keyf" + i);
        }
        REGIONS.put(Keys.UP, "keyup");
        REGIONS.put(Keys.DOWN, "keydown");
        REGIONS.put(Keys.LEFT, "keyleft");
        REGIONS.put(Keys.RIGHT, "keyright");
        REGIONS.put(Keys.SPACE, "keyspace");
        REGIONS.put(Keys.ENTER, "keyenter");
        REGIONS.put(Keys.ESCAPE, "keyescape");
        REGIONS.put(Keys.TAB, "keytab");
        REGIONS.put(Keys.SHIFT_LEFT, "keyshift");
        REGIONS.put(Keys.SHIFT_RIGHT, "keyshift");
        REGIONS.put(Keys.CONTROL_LEFT, "keyctrl");
        REGIONS.put(Keys.CONTROL_RIGHT, "keyctrl");
        REGIONS.put(Keys.ALT_LEFT, "keyalt");
        REGIONS.put(Keys.ALT_RIGHT, "keyalt");
        REGIONS.put(Keys.FORWARD_DEL, "keydelete");
    }

    /** The key-cap drawable for a keycode, or null if there is no icon for it. */
    public static Drawable drawable(Skin skin, int keycode) {
        String name = REGIONS.get(keycode);
        if (name == null) {
            return null;
        }
        return skin.has(PREFIX + name, Drawable.class)
            || skin.optional(PREFIX + name, com.badlogic.gdx.graphics.g2d.TextureRegion.class) != null
            ? skin.getDrawable(PREFIX + name) : null;
    }

    /**
     * An actor showing the key: its icon where one exists, otherwise its name.
     * Icons are never stretched - scene2d would happily scale a 13x13 key cap
     * into a blurry rectangle.
     */
    public static Actor actor(Skin skin, int keycode) {
        if (keycode < 0) {
            return new Label("--", skin, "dim");
        }
        Drawable d = drawable(skin, keycode);
        if (d == null) {
            String name = Keys.toString(keycode);
            return new Label(name == null ? "?" : name, skin);
        }
        Image image = new Image(d);
        image.setScaling(Scaling.none);
        return image;
    }

    private KeyPrompts() {}
}
