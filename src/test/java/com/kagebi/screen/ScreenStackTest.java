package com.kagebi.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;

/**
 * The stack's one hard rule: it is never empty.
 *
 * <p>An empty stack is not a quit, and from the outside it does not look like
 * one either. {@code installInput} clears the input processor and {@code
 * render} returns at its first line, so the window stays open, black and deaf
 * for as long as the player leaves it there. Reported as "the game closed
 * itself", which is unfixable because the evidence is that there is none.
 *
 * <p>The stack is driven with a fake screen rather than a real one: everything
 * real needs an atlas, and this is a test about bookkeeping.
 */
class ScreenStackTest {

    private static HeadlessApplication app;

    @BeforeAll
    static void boot() {
        HeadlessApplicationConfiguration config = new HeadlessApplicationConfiguration();
        config.updatesPerSecond = -1;
        app = new HeadlessApplication(new ApplicationAdapter() {}, config);
    }

    @AfterAll
    static void shutdown() {
        if (app != null) {
            app.exit();
        }
    }

    /** Records what the stack did to it, and draws nothing. */
    private static final class Fake extends GameScreen {
        final String name;
        int shown;
        int hidden;
        boolean disposed;

        Fake(String name) {
            this.name = name;
        }

        @Override
        public void show() {
            shown++;
        }

        @Override
        public void hide() {
            hidden++;
        }

        @Override
        public void render(float delta) {
        }

        @Override
        public void dispose() {
            disposed = true;
        }
    }

    @Test
    void poppingTheLastScreenIsRefusedRatherThanLeavingADeadWindow() {
        ScreenStack stack = new ScreenStack();
        Fake only = new Fake("only");
        stack.push(only);

        IllegalStateException boom = assertThrows(IllegalStateException.class, stack::pop);

        // The message has to name the screen, because the caller is the bug and
        // the crash log is where somebody will read this.
        assertTrue(boom.getMessage().contains("Fake"), boom.getMessage());
        assertEquals(1, stack.size());
        assertSame(only, stack.top());
        assertTrue(!only.disposed, "the refused pop must not have disposed anything");
    }

    @Test
    void poppingDownToOneIsFine() {
        ScreenStack stack = new ScreenStack();
        Fake under = new Fake("under");
        Fake over = new Fake("over");
        stack.push(under);
        stack.push(over);

        stack.pop();

        assertEquals(1, stack.size());
        assertSame(under, stack.top());
        assertTrue(over.disposed);
        assertEquals(2, under.shown, "the revealed screen is shown again");
    }

    @Test
    void popOnAnEmptyStackStaysQuiet() {
        // Never reached in the game, but a stack that has already been disposed
        // must not turn tidy-up into a second failure.
        ScreenStack stack = new ScreenStack();
        stack.pop();
        assertEquals(0, stack.size());
    }

    @Test
    void setReplacesEverythingAndCanNeverEmptyTheStack() {
        ScreenStack stack = new ScreenStack();
        Fake a = new Fake("a");
        Fake b = new Fake("b");
        stack.push(a);
        stack.push(b);

        Fake c = new Fake("c");
        stack.set(c);

        assertEquals(1, stack.size());
        assertSame(c, stack.top());
        assertTrue(a.disposed);
        assertTrue(b.disposed);
    }
}
