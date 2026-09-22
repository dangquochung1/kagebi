package com.kagebi.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;

/**
 * A stack of screens, drawn bottom-up from the deepest one still visible.
 *
 * <p>libGDX's {@code Game} holds exactly one screen, which makes an overlay
 * impossible: a pause menu has to either redraw the world itself or cover it.
 * Here the stack walks down to the last opaque screen and draws forward from
 * there, so a translucent screen simply works.
 */
public final class ScreenStack implements Disposable {

    private final Array<GameScreen> screens = new Array<>();
    /** Scratch list for the visible slice, reused to avoid per-frame garbage. */
    private final Array<GameScreen> visible = new Array<>();

    public GameScreen top() {
        return screens.isEmpty() ? null : screens.peek();
    }

    public int size() {
        return screens.size;
    }

    public void push(GameScreen screen) {
        GameScreen previous = top();
        if (previous != null) {
            previous.hide();
        }
        screens.add(screen);
        screen.attach(this);
        screen.show();
        screen.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        installInput();
    }

    /**
     * Pops the top screen and disposes it.
     *
     * <p><b>Never down to nothing.</b> An empty stack is not a quit and does not
     * look like one: {@link #installInput} clears the input processor and
     * {@link #render} returns at its first line, so the window stays open,
     * black and deaf forever. That is indistinguishable from a hang or a crash
     * from the outside, and it is always a bug in the caller - a screen popping
     * itself when it was the only one, rather than replacing itself with
     * {@link #set}. Failing loudly puts it in the crash log where it can be
     * fixed, instead of leaving a dead window and no evidence.
     */
    public void pop() {
        if (screens.isEmpty()) {
            return;
        }
        if (screens.size == 1) {
            throw new IllegalStateException(
                "pop() would empty the screen stack; " + screens.peek().getClass().getSimpleName()
                + " is the last screen and must use set() to replace itself");
        }
        GameScreen removed = screens.pop();
        removed.hide();
        removed.dispose();
        GameScreen revealed = top();
        if (revealed != null) {
            revealed.show();
        }
        installInput();
    }

    /** Replaces the whole stack, disposing everything on it. */
    public void set(GameScreen screen) {
        while (!screens.isEmpty()) {
            GameScreen removed = screens.pop();
            removed.hide();
            removed.dispose();
        }
        push(screen);
    }

    private void installInput() {
        GameScreen current = top();
        InputProcessor processor = current == null ? null : current.inputProcessor();
        Gdx.input.setInputProcessor(processor);
    }

    private void collectVisible() {
        visible.clear();
        int first = 0;
        for (int i = screens.size - 1; i >= 0; i--) {
            if (screens.get(i).isOpaque()) {
                first = i;
                break;
            }
        }
        for (int i = first; i < screens.size; i++) {
            visible.add(screens.get(i));
        }
    }

    public void render(float delta) {
        if (screens.isEmpty()) {
            return;
        }
        GameScreen current = top();
        for (int i = 0; i < screens.size; i++) {
            GameScreen s = screens.get(i);
            if (s == current || s.updatesWhenCovered()) {
                s.update(delta);
            }
        }
        collectVisible();
        for (GameScreen s : visible) {
            s.render(delta);
        }
    }

    public void resize(int width, int height) {
        for (GameScreen s : screens) {
            s.resize(width, height);
        }
    }

    @Override
    public void dispose() {
        while (!screens.isEmpty()) {
            screens.pop().dispose();
        }
    }
}
