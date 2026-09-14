package com.kagebi.screen;

import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;
import com.kagebi.run.RunSummary;
import com.kagebi.save.Profile;

/**
 * A stage finished, which is not the end of the game.
 *
 * <p>A subclass rather than a screen of its own, because the thing that is
 * hard about the end of a run is not the layout. {@link RunEndScreen} banks
 * once, on first show and not on the button press, so that a player who closes
 * the window still keeps what the stage paid; writing that a second time is
 * how it gets got wrong the second time.
 *
 * <p>What differs is four lines: the headline, the sting, the track, and the
 * fact that this banks through {@code bankStage} - which opens the next stage
 * without counting a win or relighting the village. Both of those belong to
 * the last stage alone.
 */
public class StageClearScreen extends RunEndScreen {

    public StageClearScreen(Kagebi game) {
        super(game, true);
    }

    @Override
    protected String sting() {
        return Assets.JINGLE_SUCCESS;
    }

    /**
     * The village theme, which is what the world map plays.
     *
     * <p>{@code AudioService.playMusic} does nothing when asked for the track
     * already playing, so the sting lands over silence, the village theme
     * comes up under it, and moving on to the map does not cut it off.
     */
    @Override
    protected String music() {
        return Assets.MUSIC_VILLAGE;
    }

    @Override
    protected String titleStyle() {
        return "title";
    }

    @Override
    protected String titleKey() {
        return "stage.cleared";
    }

    @Override
    protected void bankInto(Profile profile, RunSummary summary) {
        game.shop().bankStage(profile, summary);
    }
}
