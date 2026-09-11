package com.kagebi.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
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
                        boot.screenshotAfterFrames = 12;
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
        new Lwjgl3Application(new Kagebi(boot), config);
    }

    private DesktopLauncher() {}
}
