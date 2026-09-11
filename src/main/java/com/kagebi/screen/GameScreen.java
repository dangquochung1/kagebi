package com.kagebi.screen;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.utils.Disposable;

/**
 * A screen in the {@link ScreenStack}.
 *
 * <p>Unlike libGDX's own {@code Screen}, a screen here knows whether it covers
 * what is underneath. That is the whole reason for the stack: a pause menu that
 * hides the game behind a black rectangle feels like a different program, while
 * one that dims the still-visible game feels like a pause.
 */
public abstract class GameScreen implements Disposable {

    private ScreenStack stack;

    void attach(ScreenStack stack) {
        this.stack = stack;
    }

    protected ScreenStack stack() {
        return stack;
    }

    /**
     * False when this screen leaves part of the one below visible, so the stack
     * knows it has to draw that one first. Pause and dialog overlays are
     * translucent; a menu or the dungeon itself is not.
     */
    public boolean isOpaque() {
        return true;
    }

    /**
     * Whether this screen keeps simulating while something sits on top of it.
     * A paused dungeon should not; a hub with wandering NPCs behind a shop
     * window might.
     */
    public boolean updatesWhenCovered() {
        return false;
    }

    /** The processor to install while this screen is on top; null for none. */
    public InputProcessor inputProcessor() {
        return null;
    }

    public void show() {}

    public void hide() {}

    /** Called only when this screen is the top one, or it opted into updating. */
    public void update(float delta) {}

    public abstract void render(float delta);

    public void resize(int width, int height) {}

    @Override
    public void dispose() {}
}
