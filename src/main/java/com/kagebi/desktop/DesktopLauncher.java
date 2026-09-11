package com.kagebi.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.kagebi.Cfg;
import com.kagebi.Kagebi;

public final class DesktopLauncher {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("KAGEBI");
        config.setWindowedMode(Cfg.VIRT_W * 4, Cfg.VIRT_H * 4);   // 1280x720
        // Never below 2x: at 1x a 12px UI row is 12 screen pixels and unclickable.
        config.setWindowSizeLimits(Cfg.VIRT_W * 2, Cfg.VIRT_H * 2, -1, -1);
        config.useVsync(true);
        config.setForegroundFPS(60);

        // --screenshot <path>: render a few frames, save a PNG, exit.
        int shotAfter = -1;
        String shotPath = null;
        for (int i = 0; i < args.length - 1; i++) {
            if ("--screenshot".equals(args[i])) {
                shotPath = args[i + 1];
                shotAfter = 10;                 // let the first frames settle
            }
        }
        new Lwjgl3Application(new Kagebi(shotAfter, shotPath), config);
    }

    private DesktopLauncher() {}
}
