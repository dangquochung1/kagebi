package com.kagebi.screen;

import com.kagebi.Kagebi;

/**
 * Builds the screen a {@code --screen} launch flag asks for.
 *
 * <p>Art direction is the one thing no test can check, so the game has to be
 * able to open any state and show its work. That means every screen needs a
 * name the command line can reach it by, and putting the switch here rather
 * than in {@code Kagebi} keeps the entry point out of the way while the screens
 * are being written.
 *
 * <p>A name that matches nothing falls through to the main menu rather than
 * failing, so a typo during a screenshot run costs a glance, not a stack trace.
 */
public final class Screens {

    /**
     * @param name the {@code --screen} value
     * @param page the {@code --page} value, one-based; what it means is the
     *             screen's business - a settings tab, a credits section
     * @return the stack to show, bottom first
     */
    public static GameScreen[] build(Kagebi game, String name, int page) {
        if ("style".equals(name)) {
            return new GameScreen[] {new StyleSheetScreen(game, page)};
        }
        if ("settings".equals(name)) {
            return new GameScreen[] {new MainMenuScreen(game), new SettingsScreen(game, page - 1)};
        }
        return new GameScreen[] {new MainMenuScreen(game)};
    }

    private Screens() {}
}
