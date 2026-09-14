package com.kagebi.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.kagebi.Cfg;
import com.kagebi.assets.Assets;

/**
 * The world map's arithmetic, and the map file it reads.
 *
 * <p>No GL context: the rules are statics on {@link WorldMapScreen} and the
 * {@code .tmx} is parsed as text, the way the room tests already read their
 * maps. What cannot be checked here is what the thing looks like, which is
 * what {@code --screen world} and {@code --screen stage} are for.
 */
class WorldMapLayoutTest {

    // ---- which stages are open ------------------------------------------------

    @Test
    void theFirstStageIsOpenOnAProfileThatHasDoneNothing() {
        assertTrue(WorldMapScreen.unlocked(1, 0));
        assertFalse(WorldMapScreen.unlocked(2, 0));
    }

    @Test
    void clearingAStageOpensTheNextOneAndLeavesTheOldOnesOpen() {
        assertTrue(WorldMapScreen.unlocked(1, 2), "still replayable");
        assertTrue(WorldMapScreen.unlocked(2, 2));
        assertTrue(WorldMapScreen.unlocked(3, 2), "the one just opened");
        assertFalse(WorldMapScreen.unlocked(4, 2));
    }

    /**
     * The reason {@code Profile.clearedStages} exists at all.
     *
     * <p>{@code deepestFloor} counts the deepest floor reached, and the dungeon
     * sets it on arrival - so a player who walks into stage three and dies has
     * a deepestFloor of 3 without having cleared it. Keying the map on that
     * would hand out stage four as a reward for dying in stage three.
     */
    @Test
    void reachingAStageIsNotClearingIt() {
        int clearedStages = 2;          // beat one and two
        assertFalse(WorldMapScreen.unlocked(4, clearedStages),
            "died in stage three; stage four stays shut");
    }

    @Test
    void stageZeroAndBeyondTheEndAreNeverOpen() {
        assertFalse(WorldMapScreen.unlocked(0, 5));
        assertFalse(WorldMapScreen.unlocked(-1, 5));
    }

    // ---- moving the ring -------------------------------------------------------

    /**
     * The map opens on stage one, whatever the profile has done.
     *
     * <p>It opened on {@code clearedStages} first, meaning a finished profile
     * found the ring parked on stage five with the whole trail behind it, and
     * had to walk it back to replay stage one. Worth a test rather than a
     * comment because the discarded rule is the plausible-sounding one, and
     * this is the kind of line somebody helpfully "fixes" back.
     */
    @Test
    void theMapAlwaysOpensOnTheFirstStage() {
        assertEquals(0, WorldMapScreen.openingFocus(0), "a profile that has done nothing");
        assertEquals(0, WorldMapScreen.openingFocus(5), "and one that has finished the game");
    }

    @Test
    void theFocusWalksTheStagesAndStopsAtBothEnds() {
        assertEquals(1, WorldMapScreen.stepFocus(0, 1, 5));
        assertEquals(0, WorldMapScreen.stepFocus(1, -1, 5));
        assertEquals(0, WorldMapScreen.stepFocus(0, -1, 5), "clamped, not wrapped");
        assertEquals(4, WorldMapScreen.stepFocus(4, 1, 5), "clamped at the far end too");
    }

    // ---- where the nodes are ---------------------------------------------------

    @Test
    void everyStandInNodeIsOnScreen() {
        for (int[] node : WorldMapScreen.NODE_AT) {
            assertTrue(node[0] - WorldMapScreen.NODE / 2 >= 0
                    && node[0] + WorldMapScreen.NODE / 2 <= Cfg.VIRT_W,
                "node x " + node[0] + " runs off the side");
            assertTrue(node[1] - WorldMapScreen.NODE / 2 >= 0
                    && node[1] + WorldMapScreen.NODE / 2 <= Cfg.VIRT_H,
                "node y " + node[1] + " runs off the top or bottom");
        }
    }

    /**
     * Two focus rings that overlap read as one wide node, and the ring is four
     * pixels bigger than the cell on every side.
     */
    @Test
    void noTwoNodesAreCloseEnoughForTheirRingsToTouch() {
        int[][] at = WorldMapScreen.NODE_AT;
        for (int i = 0; i < at.length; i++) {
            for (int j = i + 1; j < at.length; j++) {
                int dx = Math.abs(at[i][0] - at[j][0]);
                int dy = Math.abs(at[i][1] - at[j][1]);
                assertTrue(dx >= WorldMapScreen.NODE + 4 || dy >= WorldMapScreen.NODE + 4,
                    "nodes " + (i + 1) + " and " + (j + 1) + " are " + dx + "," + dy + " apart");
            }
        }
    }

    // ---- the map file ----------------------------------------------------------

    private static final Pattern OBJECT = Pattern.compile(
        "<object[^>]*name=\"(\\d+)\"[^>]*x=\"([0-9.]+)\"[^>]*y=\"([0-9.]+)\"");

    /**
     * {@code world.tmx} names every stage once in its object layer.
     *
     * <p>Read as text rather than through libGDX, which would want a GL
     * context for the tilesets. A missing node is not a crash - the screen
     * falls back to {@link WorldMapScreen#NODE_AT} whole - so nothing else
     * would ever report it, and the map would quietly stop being the thing
     * that decides where the nodes are.
     */
    @Test
    void theWorldMapNamesEveryStageExactlyOnce() throws IOException {
        String tmx = new String(Files.readAllBytes(new File(Assets.MAP_WORLD).toPath()),
                                StandardCharsets.UTF_8);
        Matcher m = OBJECT.matcher(tmx);
        Set<String> seen = new HashSet<>();
        while (m.find()) {
            assertTrue(seen.add(m.group(1)), "stage " + m.group(1) + " is named twice");
            float x = Float.parseFloat(m.group(2));
            float y = Float.parseFloat(m.group(3));
            // Tiled's y runs down from the top and libGDX flips it on load, so
            // both are inside the map either way round. A node outside it is
            // the symptom of that flip changing, which the screen also guards
            // against by refusing the layer and using its own constants.
            assertTrue(x >= 0 && x <= Cfg.VIRT_W, "stage " + m.group(1) + " x is " + x);
            assertTrue(y >= 0 && y <= Cfg.VIRT_H, "stage " + m.group(1) + " y is " + y);
        }
        assertEquals(WorldMapScreen.NODE_AT.length, seen.size(),
            "world.tmx should name one node per stage, found " + seen);
    }
}
