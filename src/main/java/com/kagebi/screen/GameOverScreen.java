package com.kagebi.screen;

import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;

/**
 * Death, or giving up - the pause menu's abandon lands here too, because the
 * village should not be able to tell the difference.
 */
public class GameOverScreen extends RunEndScreen {

    public GameOverScreen(Kagebi game) {
        super(game, false);
    }

    @Override
    protected String sting() {
        return Assets.JINGLE_GAMEOVER;
    }

    @Override
    protected String music() {
        return Assets.MUSIC_SAD;
    }

    @Override
    protected String titleStyle() {
        return "danger";
    }
}
