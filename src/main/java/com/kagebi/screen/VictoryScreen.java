package com.kagebi.screen;

import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;

/** The way out of floor five. Plays a success sting, then the end theme. */
public class VictoryScreen extends RunEndScreen {

    public VictoryScreen(Kagebi game) {
        super(game, true);
    }

    @Override
    protected String sting() {
        return Assets.JINGLE_SUCCESS;
    }

    @Override
    protected String music() {
        return Assets.MUSIC_END;
    }

    @Override
    protected String titleStyle() {
        return "title";
    }
}
