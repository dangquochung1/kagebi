package com.kagebi.screen;

import com.badlogic.gdx.math.MathUtils;
import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;
import com.kagebi.data.def.GearDef;
import com.kagebi.data.def.GemDef;
import com.kagebi.gen.RoomKind;
import com.kagebi.input.GameAction;
import com.kagebi.run.RunState;
import com.kagebi.save.OwnedGear;
import com.kagebi.save.Profile;
import com.kagebi.save.VillageState;
import com.kagebi.settings.Difficulty;
import com.kagebi.village.Pantry;

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
 *   confirm                the New Game wipe warning, over the menu
 *   sheet     --page 1-6   the character sheet, on one of its six tabs
 *   profile   --page 1-4   the same, on its profile tab, on one of its four panels
 *   hub       --page 1-11  the village: 1 at home, 2 the torii, 3 the shop, 4-9 each
 *                          region's worker, 10 zoomed out to the whole island,
 *                          11 zoomed out once at the torii
 *   harvest   --page 1-3   the village mid-effect: goods flying from the woodcutter, a
 *                          fish over the fisher, a pumpkin picked
 *   bag       --page 1-5   the bag over the village, on that tab
 *   counter   --page 1-6   a trading counter: the herbalist's four tabs, the farmer, the cook
 *   home      --page 1-2   inside the house: on the doormat, or on the rug by the table
 *   world     --page 1-7   the world map, open to that stage and focused on it
 *   stage     --page 1-7   the same, with that stage's panel and difficulty row
 *   cleared   --page 1-7   the stage-clear screen, for that stage
 *   talk      --page 1-3   the village, mid-conversation with that villager
 *   store     --page 1-4   the herbalist's stall, over a profile N runs deep
 *   unlocks   --page 1-4   the same, on its second tab
 *   dungeon   --page 1-7   the start room of that floor
 *   map       --page 1-7   the same, with the floor map expanded
 *   fight     --page 1-7   the first room of that floor that has enemies in it
 *   swing     --page 1-7   the same, swinging on a timer so a blade is visible
 *   throw     --page 1-10  the same with a kunai in the off hand, throwing; 6-10 a shuriken
 *   boss      --page 3,5,6,7 the boss arena of a floor that has one
 *   treasure  --page 1-7   the first treasure room, for looking at a chest
 *   shop      --page 1-7   the first shop room, for looking at the shopkeeper
 *   trade     --page 1-7   the same, mid-purchase, with gold to spend
 *   slide                  halfway through the first room transition
 *   exit      --page 1-7   standing on that floor's way down
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
                return new GameScreen[] {hubAt(game, page)};
            case "home":
                return new GameScreen[] {new HubScreen(game),
                    page >= 2 ? new HomeScreen(game).arriveAt("rug") : new HomeScreen(game)};
            case "talk":
                return new GameScreen[] {new HubScreen(game).talkingTo(page - 1)};
            case "counter":
                stockVillage(game);
                return new GameScreen[] {new HubScreen(game), tradeAt(game, page)};
            case "harvest": {
                // 1 goods flying from the woodcutter, 2 a fish over the fisher,
                // 3 a pumpkin picked.
                int kind = Math.max(1, Math.min(3, page));
                String at = kind == 2 ? "stand_fishing" : kind == 3 ? "stand_farm" : "stand_forest";
                return new GameScreen[] {new HubScreen(game).arriveAt(at).demo(kind)};
            }
            case "bag": {
                stockVillage(game);
                HubScreen hub = new HubScreen(game);
                return new GameScreen[] {hub, new BagScreen(game, hub::openKit).onTab(page - 1)};
            }
            case "store":
                stockProfile(game, page);
                return new GameScreen[] {new HubScreen(game), new ShopScreen(game)};
            case "unlocks":
                stockProfile(game, page);
                return new GameScreen[] {new HubScreen(game),
                                         new ShopScreen(game).onUnlocks()};
            // The character sheet, on whichever of its four pages. Stocked,
            // because an empty bag and a forge with nothing in it photograph
            // as a broken screen rather than as a new profile.
            case "code":
                return new GameScreen[] {new MainMenuScreen(game), new CodeScreen(game)};
            // The wipe warning. It has no way in from a fresh profile, which is
            // exactly the profile a screenshot run has, so it needs a door.
            case "confirm":
                return new GameScreen[] {new MainMenuScreen(game),
                    new ConfirmScreen(game, "menu.newgame", "confirm.newgame", () -> { })};
            // The profile's three upright panels: worn, stones, roster.
            case "profile": {
                stockProfile(game, 4);
                stockGear(game);
                startRun(game, 1);
                return new GameScreen[] {new HubScreen(game),
                    new CharacterScreen(game, 0, Math.max(0, page - 1))};
            }
            case "sheet": {
                stockProfile(game, 4);
                stockGear(game);
                startRun(game, 1);
                return new GameScreen[] {new HubScreen(game),
                    new CharacterScreen(game, Math.max(0, page - 1))};
            }
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
                // someone had banked 350 gold. Pages past five are the same
                // floors with a shuriken, which flew as a kunai for a long time.
                RunState throwing = startRun(game, page > 5 ? page - 5 : page);
                throwing.throwWeaponId = page > 5 ? "shuriken" : "kunai";
                // The start room, not a fight: enemies chase, and a kunai that
                // hits something a step after it leaves the hand cannot be
                // photographed in flight at all.
                return new GameScreen[] {
                    new DungeonScreen(game).openIn(RoomKind.START).throwing()};
            // One skill, cast on a cadence. Page 1 to 3 is the key; page 4
            // casts the ultimate and then swings under it, which is the only
            // way to see what a transformation does to an ordinary attack.
            case "skill": {
                startRun(game, 1);
                if (page >= 8) {
                    // Shift and nothing else, which is how the ability with no
                    // key is read. ATTACK is held so that some key is down and
                    // none of the three is - the state the panel answers to.
                    return new GameScreen[] {new DungeonScreen(game)
                        .openIn(RoomKind.NORMAL).reading(GameAction.ATTACK)};
                }
                if (page >= 5) {
                    // Shift held on each key in turn: pages 5, 6 and 7.
                    GameAction read = page == 7 ? GameAction.SKILL_3
                        : page == 6 ? GameAction.SKILL_2 : GameAction.SKILL_1;
                    return new GameScreen[] {new DungeonScreen(game)
                        .openIn(RoomKind.NORMAL).reading(read)};
                }
                if (page == 4) {
                    return new GameScreen[] {new DungeonScreen(game).openIn(RoomKind.NORMAL)
                        .casting(GameAction.SKILL_3, GameAction.ATTACK)};
                }
                GameAction key = page >= 3 ? GameAction.SKILL_3
                    : page == 2 ? GameAction.SKILL_2 : GameAction.SKILL_1;
                return new GameScreen[] {
                    new DungeonScreen(game).openIn(RoomKind.NORMAL).casting(key)};
            }
            case "boss":
                // The one room no other flag reaches. Stage 6's boss is five
                // bodies with two untouchable intermissions between them, and
                // none of it can be looked at without either playing a whole
                // stage to it or opening the arena directly.
                startRun(game, page);
                return new GameScreen[] {new DungeonScreen(game).openIn(RoomKind.BOSS)};
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
        String hero = debugHero == null ? Assets.Actor.DEFAULT_CHARACTER : debugHero;
        RunState run = freshRun(game, hero, "katana", DEFAULT_MAX_HP);
        // Whoever is being looked at is unlocked for the length of the look.
        // A debug flag that opened a locked character would show the shop's
        // darkened placeholder rather than the character.
        game.profile().unlockedCharacters.add(hero);
        run.floor = Math.max(1, floor);
        // Something carried, so a screenshot of the dungeon shows the quick
        // key's slot - which draws only while there is something to use.
        run.items.put("food_onigiri", 2);
        run.items.put("potion_small", 1);
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
    /**
     * Some armour, some stones, and one of each worn.
     *
     * <p>A character sheet on a fresh profile is four empty windows, which
     * photographs as a screen that does not work. Guarded the same way
     * {@link #stockProfile} is: a profile that has been played is left alone.
     */
    private static void stockGear(Kagebi game) {
        Profile p = game.profile();
        if (p.stash.size > 0 || p.materials.size > 0) {
            return;
        }
        for (GearDef def : game.content().allGear()) {
            if (def.tier > 2) {
                continue;
            }
            OwnedGear piece = new OwnedGear(p.nextGearId(), def.id, def.sockets);
            if (piece.sockets.length > 0) {
                piece.sockets[0] = "red_t1";
            }
            p.stash.add(piece);
            if (def.tier == 2) {
                p.equipped.put(def.slot.name(), piece.instance);
            }
        }
        for (GemDef gem : game.content().allGems()) {
            if (gem.tier == 1) {
                p.addMaterial(gem.id, 4);
            }
        }
        // Half the book filled, so the bestiary shows a met row and an unmet
        // one in the same shot.
        int half = game.content().allEnemies().size / 2;
        for (com.kagebi.data.def.EnemyDef e : game.content().allEnemies()) {
            if (p.bestiary.size >= half) {
                break;
            }
            p.bestiary.add(e.id);
        }
    }

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
            p.upgrades.put("keyring", 2);
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
        p.clearedStages = Math.max(0, Math.min(7, stage - 1));
        p.deepestFloor = Math.max(p.deepestFloor, p.clearedStages);
    }

    /**
     * The village with the player standing somewhere worth photographing:
     * 1 the front door, 2 the torii, 3 the shop, and after that in front of
     * each region's worker, in the order {@link HubScreen#REGIONS} lists them.
     */
    private static HubScreen hubAt(Kagebi game, int page) {
        // A run first, so --hero reaches the village. Without one the hub
        // backfills the default ninja, which meant the flag silently did
        // nothing here - and the village is where the badge that broke on a
        // side-view hero is drawn, so it was the one screen worth aiming it at.
        startRun(game, 1);
        HubScreen hub = new HubScreen(game);
        if (page == 2) {
            return hub.arriveAt("gate");
        }
        if (page == 3) {
            return hub.arriveAt("shop");
        }
        if (page == 10) {
            return hub.zoomedTo(10);
        }
        if (page == 11) {
            return hub.arriveAt("gate").zoomedTo(1);
        }
        int region = page - 4;
        if (region >= 0 && region < HubScreen.REGIONS.length) {
            return hub.arriveAt("stand_" + HubScreen.REGIONS[region]);
        }
        return hub;
    }

    /**
     * A trading counter to photograph: 1 to 4 the herbalist's tabs - sell, buy,
     * tools, the old shelf - 5 the farmer's seed and 6 the cook's kitchen.
     */
    private static TradeScreen tradeAt(Kagebi game, int page) {
        if (page == 5) {
            return new TradeScreen(game, TradeScreen.Counter.FARMER);
        }
        if (page == 6) {
            return new TradeScreen(game, TradeScreen.Counter.COOK);
        }
        return new TradeScreen(game, TradeScreen.Counter.HERBALIST).onTab(page - 1);
    }

    /**
     * Something in the storehouse, the seed bag and the pantry, so the trading
     * shelves have something on them. Guarded like {@link #stockProfile}: a
     * village that has been played is left exactly as it is.
     */
    private static void stockVillage(Kagebi game) {
        VillageState v = game.profile().village;
        if (v.stock.size > 0 || v.seeds.size > 0 || v.pantry.size > 0) {
            return;
        }
        v.stock.put("fish", 3);
        v.stock.put("wood", 5);
        v.stock.put("egg", 4);
        v.stock.put("wheat", 2);
        v.stock.put("carrot", 6);
        v.seeds.put("carrot", 2);
        v.pantry.put("food_onigiri", 1);
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
        run.quickItem = previous == null ? null : previous.quickItem;
        run.difficulty = difficulty != null ? difficulty : Difficulty.DEFAULT;
        run.floor = Math.max(1, stage);
        // What the village packed goes down with the ninja, and is the run's now.
        Pantry.packInto(game.profile().village, run.items);
        game.setRun(run);
        return run;
    }

    private static Long fixedSeed;
    /** Who {@code --hero} says a debug run should be; null for the default. */
    private static String debugHero;

    /** Set from {@code Boot.hero}; see DesktopLauncher for what it is for. */
    public static void debugHero(String characterId) {
        debugHero = characterId != null && Assets.Actor.indexOf(characterId) >= 0
            ? characterId : null;
    }

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
