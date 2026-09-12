package com.kagebi.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.kagebi.Cfg;
import com.kagebi.Kagebi;
import com.kagebi.Kagebi.Boot;

/**
 * Desktop entry point.
 *
 * <p>The launch flags exist so a specific state can be reproduced without
 * clicking to it: reviewing colour or checking that pixels stay square at a
 * given zoom is otherwise a manual trip through the menus every time.
 *
 * <pre>
 *   --screen menu|style   which screen to open       (default menu)
 *   --page N              page within that screen    (default 1)
 *   --lang vi|en          force a language
 *   --scale N             window at N x 320x180      (default 4)
 *   --screenshot PATH     render a few frames, save a PNG, exit
 *   --frames N            how many frames first; use it to let fades finish
 * </pre>
 */
public final class DesktopLauncher {

    public static void main(String[] args) {
        Boot boot = new Boot();
        int scale = 4;

        for (int i = 0; i < args.length; i++) {
            boolean hasValue = i + 1 < args.length;
            switch (args[i]) {
                case "--screen" -> {
                    if (hasValue) {
                        boot.screen = args[++i];
                    }
                }
                case "--page" -> {
                    if (hasValue) {
                        boot.page = Integer.parseInt(args[++i]);
                    }
                }
                case "--lang" -> {
                    if (hasValue) {
                        boot.language = args[++i];
                    }
                }
                case "--scale" -> {
                    if (hasValue) {
                        scale = Integer.parseInt(args[++i]);
                    }
                }
                case "--screenshot" -> {
                    if (hasValue) {
                        boot.screenshotPath = args[++i];
                        if (boot.screenshotAfterFrames < 0) {
                            boot.screenshotAfterFrames = 12;
                        }
                    }
                }
                // Anything that fades - a floor title card, a room transition,
                // a hit flash - looks like a permanent part of the screen in a
                // shot taken while it is still up. Being able to say when the
                // shot is taken is the difference between reviewing the game
                // and reviewing frame twelve of it.
                case "--frames" -> {
                    if (hasValue) {
                        boot.screenshotAfterFrames = Integer.parseInt(args[++i]);
                    }
                }
                // Without this every screenshot is of a different dungeon, so
                // two shots of the same feature cannot be compared and the
                // player may not even be in the same room. --frames says WHEN
                // to look; this says WHERE.
                case "--seed" -> {
                    if (hasValue) {
                        boot.seed = Long.parseLong(args[++i]);
                    }
                }
                default -> { }
            }
        }

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("KAGEBI");
        config.setWindowedMode(Cfg.VIRT_W * scale, Cfg.VIRT_H * scale);
        // Never below 2x: at 1x a 12px UI row is 12 screen pixels and unclickable.
        config.setWindowSizeLimits(Cfg.VIRT_W * 2, Cfg.VIRT_H * 2, -1, -1);
        config.useVsync(true);
        config.setForegroundFPS(60);

        // The desktop stops delivering key events the instant focus moves, so a
        // key held while alt-tabbing away is never reported released and the
        // ninja keeps walking. Clearing on focus loss is the only place that
        // can be known, and it is why this needs a window listener rather than
        // pauseWhenLostFocus - that would stop the render loop and the music
        // too, which is a different decision.
        final Kagebi game = new Kagebi(boot);
        config.setWindowListener(new Lwjgl3WindowAdapter() {
            @Override
            public void focusLost() {
                if (game.input() != null) {
                    game.input().clear();
                }
            }
        });
        new Lwjgl3Application(game, config);
    }

    private DesktopLauncher() {}
}
