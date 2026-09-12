package com.kagebi.screen;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kagebi.assets.Assets;
import com.kagebi.audio.AudioService;
import com.kagebi.input.GameAction;
import com.kagebi.input.InputService;

/**
 * A column of buttons that works from the keyboard as well as the mouse.
 *
 * <p>The dungeon is played on the keyboard, so every screen reachable from it
 * has to be too: a pause menu that makes the player let go of WASD and find
 * the mouse to press Resume is a pause menu that gets pressed by accident.
 * Movement actions move the focus and INTERACT presses it, all through
 * {@link InputService} so a rebound key works here as well.
 *
 * <p>Focus is drawn with the skin's {@code toggle} style: the focused button is
 * "checked", which that style draws exactly like hover. Programmatic change
 * events are switched off, so moving the focus never fires the action.
 */
final class MenuColumn {

    private final Skin skin;
    private final AudioService audio;
    private final Array<TextButton> buttons = new Array<>();
    private final Array<Runnable> actions = new Array<>();
    private int focus;

    MenuColumn(Skin skin, AudioService audio) {
        this.skin = skin;
        this.audio = audio;
    }

    TextButton add(String text, Runnable action) {
        TextButton button = new TextButton(text, skin, "toggle");
        button.setProgrammaticChangeEvents(false);
        final int index = buttons.size;
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                focus = index;
                press();
            }
        });
        buttons.add(button);
        actions.add(action);
        refresh();
        return button;
    }

    /** The buttons, stacked, each {@code width} wide. */
    Table table(float width) {
        Table t = new Table();
        for (TextButton b : buttons) {
            t.add(b).width(width).padBottom(2).row();
        }
        return t;
    }

    void step(InputService input) {
        if (buttons.isEmpty()) {
            return;
        }
        int move = 0;
        if (input.justPressed(GameAction.MOVE_DOWN)) {
            move = 1;
        } else if (input.justPressed(GameAction.MOVE_UP)) {
            move = -1;
        }
        if (move != 0) {
            focus = Math.floorMod(focus + move, buttons.size);
            audio.playSfx(Assets.SFX_MOVE);
            refresh();
        }
        if (input.justPressed(GameAction.INTERACT)) {
            press();
        }
    }

    private void press() {
        refresh();
        audio.playSfx(Assets.SFX_ACCEPT);
        actions.get(focus).run();
    }

    private void refresh() {
        for (int i = 0; i < buttons.size; i++) {
            buttons.get(i).setChecked(i == focus);
        }
    }
}
