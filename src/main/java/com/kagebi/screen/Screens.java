package com.kagebi.screen;

import com.badlogic.gdx.math.MathUtils;
import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;
import com.kagebi.gen.RoomKind;
import com.kagebi.run.RunState;

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
 *
 * <pre>
 *   boot                   the loading bar, then the menu
 *   menu                   (default)
 *   settings  --page 1-3   audio, controls, video
 *   style     --page 1-2   widget sheet, surface sheet
 *   select    --page 1-6   character select, with that ninja highlighted
 *   hub       --page N     the village, dimmed by N-1 failed descents
 *   talk      --page 1-3   the village, mid-conversation with that villager
 *   dungeon   --page 1-5   the start room of that floor
 *   map       --page 1-5   the same, with the floor map expanded
 *   fight     --page 1-5   the first room of that floor that has enemies in it
 *   treasure  --page 1-5   the first treasure room, for looking at a chest
 *   shop      --page 1-5   the first shop room, for looking at the shopkeeper
 *   slide                  halfway through the first room transition
 *   exit      --page 1-5   standing on that floor's way down
 *   pause                  the pause menu over floor 1
 *   inventory              the inventory over floor 1
 *   victory, gameover      the end screens, over a sample run
 *   credits   --page 1-4   the roll, starting at that section
 * </pre>
 *
 * <p>The end screens are handed a run with numbers in it. With no content yet
 * nothing can be killed or picked up, and a stats table of zeros hides exactly
 * the thing a screenshot of it is for: whether four-digit values still fit.
 */
public final class Screens {

    /**
     * What a run starts with until {@code data/} carries character defs.
     *
     * <p>A hundred because that is the number the content was balanced against:
     * {@code BalanceTest} models every floor from it, and the enemy damage,
     * floor pacing and shop prices were all tuned around it. This screen first
     * shipped with twelve - three hearts of the quarter-heart art - and the two
     * halves never met until the game was played: a floor-five enemy hitting for
     * sixteen killed a full-health player outright, and an idle player died on
     * floor one in two seconds. The art question is the HUD's, and
     * {@code Hud.HP_PER_QUARTER} answers it without moving this.
     */
    public static final int DEFAULT_MAX_HP = 100;

    /**
     * @param name the {@code --screen} value
     * @param page the {@code --page} value, one-based; what it means is the
     *             screen's business - a settings tab, a credits section
     * @return the stack to show, bottom first
     */
    public static GameScreen[] build(Kagebi game, String name, int page) {
        switch (name == null ? "" : name) {
            case "boot":
                return new GameScreen[] {new BootScreen(game)};
            case "style":
                return new GameScreen[] {new StyleSheetScreen(game, page)};
            case "settings":
                return new GameScreen[] {new MainMenuScreen(game),
                                         new SettingsScreen(game, page - 1)};
            case "select":
                return new GameScreen[] {new CharacterSelectScreen(game, page - 1)};
            case "hub":
                game.profile().villageDarkness = Math.max(0, page - 1);
                return new GameScreen[] {new HubScreen(game)};
            case "talk":
                return new GameScreen[] {new HubScreen(game).talkingTo(page - 1)};
            case "dungeon":
                startRun(game, page);
                return new GameScreen[] {new DungeonScreen(game)};
            case "map":
                startRun(game, page);
                return new GameScreen[] {new DungeonScreen(game).withMapOpen()};
            case "exit":
                startRun(game, page);
                return new GameScreen[] {new DungeonScreen(game).atExit()};
            case "fight":
                startRun(game, page);
                return new GameScreen[] {new DungeonScreen(game).openIn(RoomKind.NORMAL)};
            case "treasure":
                startRun(game, page);
                return new GameScreen[] {new DungeonScreen(game).openIn(RoomKind.TREASURE)};
            case "shop":
                startRun(game, page);
                return new GameScreen[] {new DungeonScreen(game).openIn(RoomKind.SHOP)};
            case "slide":
                startRun(game, 1);
                return new GameScreen[] {new DungeonScreen(game).sliding()};
            case "pause":
                startRun(game, 1);
                return new GameScreen[] {new DungeonScreen(game), new PauseScreen(game, true)};
            case "inventory":
                startRun(game, 1);
                return new GameScreen[] {new DungeonScreen(game), new InventoryScreen(game)};
            case "victory":
                sampleRun(game, 5);
                return new GameScreen[] {new VictoryScreen(game)};
            case "gameover":
                sampleRun(game, 3);
                return new GameScreen[] {new GameOverScreen(game)};
            case "credits":
                return new GameScreen[] {new CreditsScreen(game, page)};
            default:
                return new GameScreen[] {new MainMenuScreen(game)};
        }
    }

    private static RunState startRun(Kagebi game, int floor) {
        RunState run = freshRun(Assets.Actor.DEFAULT_CHARACTER,
                                "katana", DEFAULT_MAX_HP);
        run.floor = Math.max(1, floor);
        game.setRun(run);
        return run;
    }

    private static void sampleRun(Kagebi game, int deepest) {
        RunState run = startRun(game, deepest);
        run.deepestFloor = deepest;
        run.kills = 1284;
        run.gold = 2371;
        run.elapsedSeconds = 3725f;
    }

    /**
     * A new run for a character, at full health, on no floor yet. The seed is
     * drawn here and nowhere else, so that a seeded-run option later is one
     * parameter rather than a search.
     */
    public static RunState freshRun(String characterId, String weaponId, int maxHp) {
        return new RunState(MathUtils.random.nextLong(), characterId, weaponId, maxHp);
    }

    private Screens() {}
}
