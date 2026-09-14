package com.kagebi.screen;

import com.badlogic.gdx.math.MathUtils;
import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;
import com.kagebi.gen.RoomKind;
import com.kagebi.run.RunState;
import com.kagebi.save.Profile;
import com.kagebi.settings.Difficulty;

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
 *   world     --page 1-5   the world map, open to that stage and focused on it
 *   stage     --page 1-5   the same, with that stage's panel and difficulty row
 *   cleared   --page 1-5   the stage-clear screen, for that stage
 *   talk      --page 1-3   the village, mid-conversation with that villager
 *   store     --page 1-4   the herbalist's stall, over a profile N runs deep
 *   unlocks   --page 1-4   the same, on its second tab
 *   dungeon   --page 1-5   the start room of that floor
 *   map       --page 1-5   the same, with the floor map expanded
 *   fight     --page 1-5   the first room of that floor that has enemies in it
 *   swing     --page 1-5   the same, swinging on a timer so a blade is visible
 *   throw     --page 1-5   the same with a kunai in the off hand, throwing
 *   treasure  --page 1-5   the first treasure room, for looking at a chest
 *   shop      --page 1-5   the first shop room, for looking at the shopkeeper
 *   trade     --page 1-5   the same, mid-purchase, with gold to spend
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
            case "world":
                stockStages(game, page);
                return new GameScreen[] {new WorldMapScreen(game).focusOn(page)};
            case "stage":
                stockStages(game, page);
                return new GameScreen[] {
                    new WorldMapScreen(game).focusOn(page).withPanelOpen()};
            case "cleared":
                sampleRun(game, page);
                return new GameScreen[] {new StageClearScreen(game)};
            case "hub":
                game.profile().villageDarkness = Math.max(0, page - 1);
                return new GameScreen[] {new HubScreen(game)};
            case "talk":
                return new GameScreen[] {new HubScreen(game).talkingTo(page - 1)};
            case "store":
                stockProfile(game, page);
                return new GameScreen[] {new HubScreen(game), new ShopScreen(game)};
            case "unlocks":
                stockProfile(game, page);
                return new GameScreen[] {new HubScreen(game),
                                         new ShopScreen(game).onUnlocks()};
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
            case "swing":
                startRun(game, page);
                return new GameScreen[] {
                    new DungeonScreen(game).openIn(RoomKind.NORMAL).swinging()};
            case "throw":
                // The off hand is empty on a fresh profile, so this hands one
                // over: otherwise the throw could not be looked at until
                // someone had banked 350 gold.
                startRun(game, page).throwWeaponId = "kunai";
                // The start room, not a fight: enemies chase, and a kunai that
                // hits something a step after it leaves the hand cannot be
                // photographed in flight at all.
                return new GameScreen[] {
                    new DungeonScreen(game).openIn(RoomKind.START).throwing()};
            case "treasure":
                startRun(game, page);
                return new GameScreen[] {new DungeonScreen(game).openIn(RoomKind.TREASURE)};
            case "shop":
                startRun(game, page);
                return new GameScreen[] {new DungeonScreen(game).openIn(RoomKind.SHOP)};
            case "trade": {
                // With a purse. The shelf is drawn against what can be paid for
                // and an empty one shows three prices in red and nothing else.
                RunState run = startRun(game, page);
                run.gold = 120 * Math.max(1, page);
                return new GameScreen[] {new DungeonScreen(game).openIn(RoomKind.SHOP),
                                         new TraderScreen(game)};
            }
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
        RunState run = freshRun(game, Assets.Actor.DEFAULT_CHARACTER,
                                "katana", DEFAULT_MAX_HP);
        run.floor = Math.max(1, floor);
        game.setRun(run);
        return run;
    }

    /**
     * A profile {@code runs} deep, for looking at the shop.
     *
     * <p>The screen has four states worth photographing - affordable, too dear,
     * requirement unmet, already owned - and an untouched profile shows only the
     * middle two. The numbers are the ones {@code BalanceTest} models: a death
     * on floor 3 banks about 635, one on floor 4 about 1,250.
     *
     * <p><b>A profile that has been played is left exactly as it is.</b> This
     * writes into the live profile, and the shop saves on every purchase - so
     * without this guard, opening the shop with a debug flag and buying
     * anything would write invented gold over a real player's bank.
     */
    private static void stockProfile(Kagebi game, int runs) {
        Profile p = game.profile();
        if (p.runs > 0 || p.gold > 0) {
            return;
        }
        p.runs = Math.max(1, runs);
        p.deepestFloor = Math.min(5, 2 + p.runs);
        p.gold = 635 * p.runs;
        if (p.runs >= 2) {
            p.unlockedWeapons.add("axe");
            p.upgrades.put("vigor", 1);
        }
        if (p.runs >= 3) {
            p.upgrades.put("flamekeeper", 2);
        }
    }

    /**
     * A profile that has cleared up to {@code stage - 1}, so the map has open
     * nodes to photograph.
     *
     * <p>Guarded the way {@link #stockProfile} is, and for the same reason:
     * this writes into the live profile, so without the guard a screenshot run
     * would hand a real player stages they have not finished.
     */
    private static void stockStages(Kagebi game, int stage) {
        Profile p = game.profile();
        if (p.runs > 0 || p.gold > 0) {
            return;
        }
        p.clearedStages = Math.max(0, Math.min(5, stage - 1));
        p.deepestFloor = Math.max(p.deepestFloor, p.clearedStages);
    }

    private static void sampleRun(Kagebi game, int deepest) {
        RunState run = startRun(game, deepest);
        run.deepestFloor = deepest;
        run.kills = 1284;
        run.gold = 2371;
        run.elapsedSeconds = 3725f;
    }

    /**
     * A run for one stage, at the difficulty chosen on the world map.
     *
     * <p>The ninja, the weapon and the off hand carry over from whatever the
     * player last played; nothing else does. Health starts full every time,
     * because a stage is a sitting and not a leg of a descent - which is also
     * how the "heal on arriving at a new floor" this game wanted turns out to
     * need no code at all.
     *
     * <p>The second and last place a difficulty is fixed onto a run. The dial
     * on the video tab is now the default the map opens on; this is what the
     * run is actually played at.
     */
    public static RunState stageRun(Kagebi game, int stage, Difficulty difficulty) {
        RunState previous = game.run();
        RunState run = freshRun(
            previous != null ? previous.characterId : Assets.Actor.DEFAULT_CHARACTER,
            previous != null ? previous.weaponId : "katana",
            DEFAULT_MAX_HP);
        run.throwWeaponId = previous == null ? null : previous.throwWeaponId;
        run.difficulty = difficulty != null ? difficulty : Difficulty.DEFAULT;
        run.floor = Math.max(1, stage);
        game.setRun(run);
        return run;
    }

    private static Long fixedSeed;

    /**
     * Pins the seed every later run is built from, for {@code --seed}.
     *
     * <p>The whole point of a screenshot is to compare it with another one, and
     * without this every launch draws a different dungeon - so two shots of the
     * same feature are taken in two different rooms, with different enemies, at
     * different distances. {@code --frames} says when to look; this says where.
     */
    public static void fixSeed(long seed) {
        fixedSeed = seed;
    }

    /**
     * A new run for a character, at full health, on no floor yet. The seed is
     * drawn here and nowhere else, which is what made {@link #fixSeed} one
     * field rather than a search.
     */
    public static RunState freshRun(String characterId, String weaponId, int maxHp) {
        long seed = fixedSeed != null ? fixedSeed : MathUtils.random.nextLong();
        return new RunState(seed, characterId, weaponId, maxHp);
    }

    /**
     * As above, taking the difficulty from the settings.
     *
     * <p>This is the only place the setting is read, and it is read once. A run
     * keeps the difficulty it started with, so the pause menu cannot be used to
     * make one bad room easier and then put it back.
     */
    public static RunState freshRun(Kagebi game, String characterId, String weaponId,
                                    int maxHp) {
        RunState run = freshRun(characterId, weaponId, maxHp);
        if (game.settings() != null) {
            run.difficulty = game.settings().difficulty();
        }
        return run;
    }

    private Screens() {}
}
