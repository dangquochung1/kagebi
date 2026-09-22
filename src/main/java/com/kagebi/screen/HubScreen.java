package com.kagebi.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.kagebi.Cfg;
import com.kagebi.Dir;
import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;
import com.kagebi.data.VillageCatalog;
import com.kagebi.data.def.QuestDef;
import com.kagebi.entity.EntityWorld;
import com.kagebi.entity.Player;
import com.kagebi.entity.World;
import com.kagebi.gen.CollisionGrid;
import com.kagebi.gen.Room;
import com.kagebi.gen.RoomKind;
import com.kagebi.gen.RoomTemplate;
import com.kagebi.gen.SpawnPoint;
import com.kagebi.gen.TiledRooms;
import com.kagebi.gfx.Anim;
import com.kagebi.gfx.CameraController;
import com.kagebi.input.GameAction;
import com.kagebi.quest.Quests;
import com.kagebi.run.RunState;
import com.kagebi.save.QuestLog;
import com.kagebi.save.SavedRun;
import com.kagebi.save.VillageState;
import com.kagebi.gfx.Silhouette;
import com.kagebi.screen.island.CloudLayer;
import com.kagebi.screen.island.HarvestFx;
import com.kagebi.screen.island.IslandProps;
import com.kagebi.screen.island.SeaRipple;
import com.kagebi.ui.DialogBox;
import com.kagebi.ui.Hit;
import com.kagebi.ui.Hud;
import com.kagebi.ui.I18n;
import com.kagebi.ui.Waypoint;
import com.kagebi.village.Farm;
import com.kagebi.village.Workshops;

/**
 * Kagemura, between runs: the island of the Sunnyside World pack, with the
 * player's house in the middle of it and the torii down to the dungeon beside.
 *
 * <p><b>The map is the scene.</b> {@code tools/make_island.py} builds
 * {@code village.tmx} from the pack's own showcase room, and this screen draws
 * it in the file's own layer order: runs of tile layers through the map
 * renderer, and between them the four object layers of sprites that libGDX
 * loads but never draws. One of those, {@code sprites}, is where the player is
 * slotted in among the people, animals and things by where each stands.
 *
 * <p><b>Where everything stands is read off the map.</b> The {@code spawns}
 * layer names the front door, the torii, the shop, a place in front of each
 * region's worker, and where the three villagers stand. Nothing about the
 * village's shape is written down in Java.
 *
 * <p>The only map in the game larger than the screen, so the only screen where
 * the camera scrolls. It follows the player and is clamped to the map; the
 * rounding to whole pixels happens inside {@link CameraController}. It is also
 * the only one that zooms, from half out to the whole island, on the mouse
 * wheel or {@link GameAction#ZOOM_IN}/{@link GameAction#ZOOM_OUT}. Marks over
 * the world - who has goods, what was just taken - are drawn at the screen's
 * size rather than the map's, so they stay readable however far out it is.
 *
 * <p>The villagers and the island's people are drawn and managed here, not by
 * the {@link World}. They are furniture that talks: they do not move, fight or
 * drop anything, and teaching the simulation about them would be teaching combat
 * code about dialogue.
 */
public class HubScreen extends SimScreen {

    private static final Color GOLD = new Color(0xffad55ff);
    /**
     * The pack's open water, painted under the map: zoomed out to the whole
     * island the view is wider than the map, and the bands either side would
     * otherwise be the dark the rest of the game clears to.
     */
    private static final Color SEA = new Color(0x0099dbff);

    static final float GATE_RANGE = 24f;
    /**
     * Wide enough that the prompt is up where the player lands.
     *
     * <p>Arriving home and being told the door opens is the only way anyone
     * finds out that it does - there is no sign on it and no line of dialogue
     * about it. The marker sits on the doormat and the player arrives two
     * tiles off it, so this is just over that.
     */
    static final float DOOR_RANGE = 26f;
    static final float TALK_RANGE = 22f;
    /**
     * How close a player comes to a region's worker to deal with them. Wider
     * than {@link #TALK_RANGE}: a cook stands behind her counter and a
     * woodcutter among his logs. {@code tools/make_island.py} checks every
     * worker can be walked this close to.
     */
    static final float WORK_RANGE = 32f;
    /** The object layer the village's own markers live in. */
    static final String SPAWNS = "spawns";
    /** The object layer the farm field's plots are marked in, named in the order the farm opens them. */
    static final String PLOTS = "plots";
    /**
     * How close to a plot's middle a player works it: most of a tile, so the
     * plot in front of the player is the one worked and not its neighbour.
     * {@code tools/make_island.py} checks each plot can be walked this close to.
     */
    static final float PLOT_RANGE = 14f;
    /** Over a worker with goods waiting. */
    private static final String ALERT = "ui/sunny/icons/expression_alerted";
    /** How far above a villager's feet their quest mark floats. */
    private static final int MARK_LIFT = 22;
    /** Steps per half of the mark's bob: half a second up, half a second down. */
    private static final int MARK_BOB_STEPS = 30;
    /** Steps a "+2 wood" floats over the player. */
    private static final int TOAST_STEPS = 60;
    /** Goods drawn flying at one collection; a worker holding eleven sends six. */
    private static final int MAX_FLYING = 6;
    /** The workshop whose units are fish, which leap rather than hop. */
    private static final String FISHING = "fishing";
    /** How far out from the fisher's middle the line meets the water. */
    private static final float LINE_REACH = 12f;
    /** A splash further off than this is not heard. */
    private static final float EARSHOT = 180f;
    /** The map layer that ripples, as the pack's scene has its sea under a water filter. */
    private static final String SEA_LAYER = "ground_sea";
    /** The pack's green progress bars, 0 to 6 on the end. */
    private static final String BAR = "ui/sunny/icons/greenbar_0";
    /** Frames after arriving that {@code --screen harvest} sets its effect off. */
    private static final int DEMO_FLY_FRAME = 212;
    private static final int DEMO_LEAP_FRAME = 220;
    /** Villagers turn to face the player inside this range. */
    private static final float NOTICE_RANGE = 56f;

    /** The marker each villager stands at, and who they are. */
    static final String[][] VILLAGERS = {
        {"elder", Assets.Npc.ELDER},
        {"master", Assets.Npc.MASTER},
        {"herbalist", Assets.Npc.HERBALIST},
    };
    /**
     * The island's regions, each with the one person in it the player deals
     * with, in the order {@code --screen hub} and {@code --screen talk} count
     * them. Each is a sprite with this {@code role} on the map.
     */
    static final String[] REGIONS = {"farm", "forest", "ranch", "fishing", "mine", "kitchen"};

    /**
     * Two badges, top right: the ninja's own face, and the basket beside it.
     *
     * <p>There were three. The third opened a separate screen for choosing a
     * character and a weapon, which the sheet already had a panel for, so the
     * village was offering two doors onto the same choice and they disagreed
     * about who was selected. The face opens the sheet now and the screen it
     * used to open is gone.
     *
     * <p>Twenty-four square with the 32px idle frame drawn into it and
     * trimmed - the portrait is the label, so there is no text to translate and
     * nothing to run into the gold counter across the way. It mirrors the purse
     * at the other corner: the two things the village is for are what it costs
     * and what you are carrying.
     */
    private static final int BADGE = 24;
    private static final int BADGE_X = Cfg.VIRT_W - BADGE - 4;
    private static final int BADGE_Y = Cfg.VIRT_H - BADGE - 4;
    /** The bag button beside it, which F also presses. */
    private static final int BAG_X = BADGE_X - BADGE - 4;
    private static final String BAG_ICON = "ui/sunny/icons/basket";

    private final Kagebi game;
    private final CameraController camera = new CameraController();
    private final CameraController ui = new CameraController();
    /** The corner badges are drawn in UI space, so they are hit-tested there. */
    private final Hit hit;

    private TiledMap map;
    private OrthogonalTiledMapRenderer renderer;
    private IslandProps props;
    private final Array<Pass> passes = new Array<>();
    private int mapW;
    private int mapH;
    private CollisionGrid grid;

    private World world;
    private RunState run;
    private final Array<Villager> villagers = new Array<>();
    /** The same villagers, highest on the map first, for the depth sort. */
    private final Array<Villager> byFoot = new Array<>();
    private final Array<IslandProps.Prop> workers = new Array<>();

    /** Each plot's middle across and bottom edge, y up, in the order the farm counts them. */
    private final Array<float[]> plots = new Array<>();
    /** Crop and mark pictures by region name, null for one the atlas does not have. */
    private final ObjectMap<String, TextureRegion> art = new ObjectMap<>();
    private VillageCatalog village;
    private VillageState farm;
    /** What was just harvested or collected, floating over the player, and its steps left. */
    private String toast;
    private int toastSteps;

    /** Goods flying to the player, the fisher's fish, a unit hopping over its maker. */
    private HarvestFx fx;
    /** For the white ring round a flying good; null where the driver would not compile it. */
    private ShaderProgram silhouette;
    /** Each workshop's waiting count as last seen, to notice a unit being made. */
    private final ObjectIntMap<String> lastReady = new ObjectIntMap<>();
    /**
     * For {@code --screen harvest}: which effect to set off, and on which drawn
     * frame. Frames rather than steps, because {@code --frames} counts frames and
     * a slow first second runs several steps a frame to catch up, by a different
     * amount every run.
     */
    private int demoKind;
    private int demoAt = -1;
    private int framesDrawn;

    private DialogBox dialog;
    private BitmapFont font;

    /** Steps left on the arrival card. */
    private int card = Hud.CARD_STEPS;

    private int gateX;
    private int gateY;
    private int doorX;
    private int doorY;
    private int entryX;
    private int entryY;

    /** What INTERACT would do this step. At most one of these is set. */
    private Villager nearVillager;
    private IslandProps.Prop nearWorker;
    private int nearPlot = -1;
    private boolean nearGate;
    private boolean nearDoor;

    /** Someone to be already talking to on arrival - villagers, then workers - or -1. */
    private int talkOnShow = -1;

    /** A marker to arrive at instead of the front door, or null. */
    private String arriveAt;
    /** Zoom notches to open at, for a screenshot; spent once the map's size is known. */
    private int zoomOnShow;
    /** Every marker the map names, y-up. */
    private final java.util.Map<String, int[]> markers = new java.util.HashMap<>();

    /** The badge portrait, rebuilt when the player changes who they are. */
    private Anim badgeIdle;
    private String badgeCharacter;

    /** Set when the loadout closes, so the world picks the new kit up. */
    /** Set while the character sheet is open, so the village re-reads the run after. */
    private boolean kitOpen;

    /** Set while the herbalist is speaking; her last page opens her stall. */
    private boolean openShopAfterTalk;

    // Per-frame state of the depth-sorted pass.
    private int nextVillager;
    private boolean actorsDrawn;
    private float playerFoot;

    public HubScreen(Kagebi game) {
        super(game.input());
        this.game = game;
        this.hit = new Hit(game.input(), ui);
    }

    /**
     * Opens with someone mid-sentence: a villager for 0 to 2, a region's worker
     * after that. For {@code --screen talk}: the dialog box is the screen's
     * densest Vietnamese text, and the one most likely to clip a tone mark, so it
     * has to be reachable without walking to it.
     */
    HubScreen talkingTo(int who) {
        talkOnShow = who;
        return this;
    }

    /**
     * Opens with the player standing at a named marker rather than at their
     * own door: {@code gate} on the way back up from the dungeon, and any of
     * them for {@code --screen hub --page}.
     */
    HubScreen arriveAt(String marker) {
        arriveAt = marker;
        return this;
    }

    /**
     * Sets off one of the island's effects a few seconds after arriving, for
     * {@code --screen harvest}: 1 the woodcutter's goods flying over, 2 a fish
     * at the fisher's line, 3 a crop picked. Timed so a screenshot at 240
     * frames lands mid-flight.
     */
    HubScreen demo(int kind) {
        demoKind = kind;
        return this;
    }

    /** Opens already zoomed out by this many notches, for {@code --screen hub --page 10}. */
    HubScreen zoomedTo(int notches) {
        zoomOnShow = notches;
        return this;
    }

    @Override
    public InputProcessor inputProcessor() {
        return game.input();
    }

    @Override
    public void show() {
        game.input().clear();
        if (map != null) {
            if (kitOpen) {
                kitOpen = false;
                // Walk back into the village, which is how the world is told
                // anything about the run: entering a room re-reads the ninja
                // and both weapons. Where the player already stands, and not at
                // the front door: on an island this size that was a teleport.
                world.enterRoom(villageRoom(Math.round(world.playerX()),
                                            Math.round(world.playerY())), grid, null);
                card = 0;
            }
            game.audio().playMusic(Assets.MUSIC_VILLAGE);
            return;
        }
        font = game.skin().getFont("default");
        dialog = new DialogBox(game.skin());

        map = new TmxMapLoader().load(Assets.MAP_VILLAGE);
        renderer = new OrthogonalTiledMapRenderer(map, game.batch());
        props = IslandProps.load(map, Gdx.files.internal(Assets.MAP_VILLAGE));
        buildPasses();
        readSpawns(map);
        village = game.village();
        farm = game.profile().village;
        readPlots(map);

        grid = TiledRooms.collision(map);
        mapW = grid.width() * CollisionGrid.TILE;
        mapH = grid.height() * CollisionGrid.TILE;
        if (zoomOnShow != 0) {
            camera.zoomBy(zoomOnShow, CameraController.fitZoom(mapW, mapH));
            camera.snapZoom();
            zoomOnShow = 0;
        }

        run = game.run();
        if (run == null) {
            // Reached with --screen hub, or after an end screen that has
            // already cleared the run. The village still needs someone in it.
            run = Screens.freshRun(game, Assets.Actor.DEFAULT_CHARACTER, "katana",
                                   Screens.DEFAULT_MAX_HP);
            game.setRun(run);
        }
        run.floor = 0;
        run.layout = null;
        run.room = null;
        rememberRun();

        TextureAtlas npc = Preload.npc();
        for (String[] villager : VILLAGERS) {
            int[] at = markers.get(villager[0]);
            if (at == null) {
                continue;
            }
            String id = villager[1];
            Villager v = new Villager(id, at[0], at[1],
                Anim.directional(npc, Assets.Npc.idle(id), 16, Anim.DEFAULT_STEPS_PER_FRAME, true),
                npc.findRegion(Assets.Npc.face(id)));
            villagers.add(v);
            byFoot.add(v);
            // A villager's feet are in the way, as the workers' are in the
            // map's own collision layer; the player walks round rather than
            // through them.
            grid.fill(at[0] - 5f, at[1], 10f, 6f);
        }
        byFoot.sort((a, b) -> Integer.compare(b.y, a.y));
        for (IslandProps.Prop p : props.with("role")) {
            workers.add(p);
        }

        world = new EntityWorld(Preload.actors(), game.content(), run, game.settings());
        // Without this the world loads its own copies of both pages: ~20MB of
        // texture for art this screen is already holding.
        world.useSharedAtlases(game.skin().getAtlas(), Preload.fx());
        if (world instanceof EntityWorld) {
            // Upgrades bought in the village count for as much as relics found
            // in the dungeon, and both are resolved in one place.
            ((EntityWorld) world).useVillage(game.shop(), game.profile());
            ((EntityWorld) world).useAudio(game.audio());
        }
        if (talkOnShow >= 0 && talkOnShow < villagers.size) {
            Villager v = villagers.get(talkOnShow);
            world.enterRoom(villageRoom(v.x, v.y - 20), grid, null);
            talk(v);
        } else if (talkOnShow >= villagers.size && workerFor(talkOnShow - villagers.size) != null) {
            IslandProps.Prop worker = workerFor(talkOnShow - villagers.size);
            int[] at = markers.get("stand_" + worker.properties.get("role"));
            world.enterRoom(at == null ? villageRoom() : villageRoom(at[0], at[1]), grid, null);
            talkTo(worker);
        } else {
            int[] at = arriveAt == null ? null : markers.get(arriveAt);
            world.enterRoom(at == null ? villageRoom() : villageRoom(at[0], at[1]), grid, null);
        }

        fx = new HarvestFx(Double.doubleToLongBits(farm.clock));
        silhouette = Silhouette.create();
        for (IslandProps.Prop worker : workers) {
            VillageCatalog.Workshop workshop = workshopOf(worker);
            if (workshop != null) {
                // What is already waiting was made while nobody watched.
                lastReady.put(workshop.id, Workshops.ready(village, farm, workshop));
            }
        }
        if (demoKind > 0) {
            demoAt = framesDrawn + (demoKind == 2 ? DEMO_LEAP_FRAME : DEMO_FLY_FRAME);
        }
        game.audio().playMusic(Assets.MUSIC_VILLAGE);
    }

    /** The worker of the n-th region in {@link #REGIONS}, or null. */
    private IslandProps.Prop workerFor(int region) {
        if (region < 0 || region >= REGIONS.length) {
            return null;
        }
        for (IslandProps.Prop w : workers) {
            if (REGIONS[region].equals(w.properties.get("role"))) {
                return w;
            }
        }
        return null;
    }

    /**
     * The map's layers, grouped into what one draw call can do.
     *
     * <p>Consecutive tile layers are one call to the map renderer; each object
     * layer of sprites, and each cloud layer, is a pass of its own. The order is
     * the file's, which is the order the build script checked its render in.
     */
    private void buildPasses() {
        IntArray tiles = new IntArray();
        for (int i = 0; i < map.getLayers().size(); i++) {
            MapLayer layer = map.getLayers().get(i);
            String name = layer.getName();
            if (layer instanceof TiledMapTileLayer) {
                if (name.startsWith("overhead_cloud")) {
                    flushTiles(tiles);
                    passes.add(new Pass(null, null, false, new CloudLayer((TiledMapTileLayer) layer)));
                } else if (SEA_LAYER.equals(name)) {
                    flushTiles(tiles);
                    passes.add(new Pass(new SeaRipple(i)));
                } else {
                    tiles.add(i);
                }
                continue;
            }
            Array<IslandProps.Prop> sprites = props.layer(name);
            if (sprites != null) {
                flushTiles(tiles);
                passes.add(new Pass(null, sprites, IslandProps.SORTED.equals(name), null));
            }
        }
        flushTiles(tiles);
    }

    private void flushTiles(IntArray tiles) {
        if (tiles.size > 0) {
            passes.add(new Pass(tiles.toArray(), null, false, null));
            tiles.clear();
        }
    }

    /**
     * The village as a room, so the world can be handed it through the same
     * door as a dungeon room. It has no doors of its own - the way out is the
     * gate, which this screen owns - and one ENTRY spawn, which is how the
     * contract says an arrival with no door is placed.
     */
    private Room villageRoom() {
        return villageRoom(entryX, entryY);
    }

    private static Room villageRoom(int x, int y) {
        Array<RoomKind> kinds = new Array<>();
        kinds.add(RoomKind.START);
        Array<SpawnPoint> spawns = new Array<>();
        spawns.add(new SpawnPoint(SpawnPoint.Kind.ENTRY, x, y, null));
        RoomTemplate template = new RoomTemplate("village", "village", kinds,
                                                 Assets.MAP_VILLAGE, spawns);
        return new Room(0, 0, RoomKind.START, template);
    }

    /**
     * Every named marker, off the map's own object layer.
     *
     * <p>A marker the map does not name leaves what depends on it out - a
     * villager who is not there, a door that does not open - rather than
     * crashing on the way in. What it must never do is go unnoticed, which is
     * why the names are asserted in {@code VillageLayoutTest}.
     */
    private void readSpawns(TiledMap map) {
        MapLayer layer = map.getLayers().get(SPAWNS);
        if (layer == null) {
            return;
        }
        for (MapObject object : layer.getObjects()) {
            String name = object.getName();
            Float x = object.getProperties().get("x", Float.class);
            Float y = object.getProperties().get("y", Float.class);
            if (name == null || x == null || y == null) {
                continue;
            }
            markers.put(name, new int[] {Math.round(x), Math.round(y)});
        }
        int[] gate = markers.getOrDefault("gate", new int[] {-1000, -1000});
        int[] door = markers.getOrDefault("door", new int[] {-1000, -1000});
        int[] entry = markers.getOrDefault("entry", door);
        gateX = gate[0];
        gateY = gate[1];
        doorX = door[0];
        doorY = door[1];
        entryX = entry[0];
        entryY = entry[1];
    }

    /**
     * The field's plots, and on the very first visit the crops the scene grows on
     * them. The map carries a crop only where the scene painted one; from then on
     * the farm is the save's, and every crop is drawn from it.
     */
    private void readPlots(TiledMap map) {
        MapLayer layer = map.getLayers().get(PLOTS);
        if (layer == null) {
            return;
        }
        Array<MapObject> named = new Array<>();
        for (MapObject object : layer.getObjects()) {
            if (object.getName() != null) {
                named.add(object);
            }
        }
        named.sort((a, b) -> a.getName().compareTo(b.getName()));
        String[] crops = new String[named.size];
        int[] stages = new int[named.size];
        for (int i = 0; i < named.size; i++) {
            MapObject object = named.get(i);
            Float x = object.getProperties().get("x", Float.class);
            Float y = object.getProperties().get("y", Float.class);
            plots.add(new float[] {x == null ? 0f : x, y == null ? 0f : y});
            crops[i] = object.getProperties().get("crop", String.class);
            Integer stage = object.getProperties().get("stage", Integer.class);
            stages[i] = stage == null ? 0 : stage;
        }
        Farm.plantScene(village, farm, crops, stages);
    }

    // ---- simulation --------------------------------------------------------

    @Override
    protected void step() {
        if (card > 0) {
            card--;
        }
        if (toastSteps > 0) {
            toastSteps--;
        }
        for (Villager v : villagers) {
            v.face(world.playerX(), world.playerY());
        }
        // Before the dialog: looking around is not doing anything, so it is
        // allowed mid-sentence too.
        stepZoom();
        stepFx();

        if (dialog.open()) {
            dialog.step();
            if (input().justPressed(GameAction.INTERACT) || input().justPressed(GameAction.ATTACK)) {
                // The herbalist's conversation ends by opening her stall. She
                // is the only villager who sells anything, so the shop is
                // reached by talking to her rather than by a second key: one
                // fewer thing on screen, and the dialogue is the sign.
                if (dialog.advance() && openShopAfterTalk) {
                    openShopAfterTalk = false;
                    stack().push(new TradeScreen(game, TradeScreen.Counter.HERBALIST));
                }
            } else if (input().justPressed(GameAction.PAUSE)) {
                dialog.close();
                openShopAfterTalk = false;
            }
            return;         // the player stands still while spoken to
        }

        if (input().justPressed(GameAction.PAUSE)) {
            stack().push(new PauseScreen(game, false));
            return;
        }
        if (input().justPressed(GameAction.BAG)
                || hit.clicked(BAG_X, BADGE_Y, BADGE, BADGE)) {
            openBag();
            return;
        }
        if (input().justPressed(GameAction.SHEET)
                || hit.clicked(BADGE_X, BADGE_Y, BADGE, BADGE)) {
            openSheet(CharacterScreen.TAB_PROFILE, 0);
            return;
        }

        world.step(input());
        findInteraction();

        if (input().justPressed(GameAction.INTERACT)) {
            if (nearVillager != null) {
                talk(nearVillager);
            } else if (nearWorker != null) {
                // The farmer and the cook keep a counter. Any other worker's
                // goods are taken first, and one with none has a word.
                TradeScreen.Counter counter = counterOf(nearWorker);
                if (counter != null) {
                    game.audio().playSfx(Assets.SFX_ACCEPT);
                    stack().push(new TradeScreen(game, counter));
                } else if (!collect(nearWorker)) {
                    talkTo(nearWorker);
                }
            } else if (nearPlot >= 0) {
                workPlot(nearPlot);
            } else if (nearGate) {
                openMap();
            } else if (nearDoor) {
                goInside();
            } else if (world.promptKey() != null) {
                world.interact();
            }
        }
    }

    /**
     * The effects, a step on; and a unit being made anywhere on the island,
     * noticed by its workshop's waiting count going up.
     */
    private void stepFx() {
        fx.step(world.playerX(), world.playerY());
        if (fx.arrived() > 0 && !fx.flying()) {
            game.audio().playSfx(Assets.Sfx.COIN);
        }
        if (fx.splashed() > 0) {
            game.audio().playSfx(Assets.Sfx.BUBBLE);
        }
        for (IslandProps.Prop worker : workers) {
            VillageCatalog.Workshop workshop = workshopOf(worker);
            if (workshop == null) {
                continue;
            }
            int ready = Workshops.ready(village, farm, workshop);
            if (ready > lastReady.get(workshop.id, ready)) {
                made(worker, workshop, ready - 1);
            }
            lastReady.put(workshop.id, ready);
        }
        if (demoAt >= 0 && framesDrawn >= demoAt) {
            demoAt = -1;
            runDemo();
        }
    }

    /**
     * A unit just made: at the fisher's line a fish leaps clear of the water;
     * over anyone else, the unit they made hops up. Either way it is the good
     * that will be collected, not a picture of the workshop's usual one.
     */
    private void made(IslandProps.Prop worker, VillageCatalog.Workshop workshop, int index) {
        String good = Workshops.upcoming(village, farm, workshop, index);
        TextureRegion icon = good == null ? null : TradeScreen.goodIcon(game, good);
        if (icon == null) {
            return;
        }
        boolean heard = dist(world.playerX(), world.playerY(), worker.centreX(), worker.foot) < EARSHOT;
        if (FISHING.equals(workshop.id)) {
            boolean right = !worker.flipX;
            fx.leap(icon, worker.centreX() + (right ? LINE_REACH : -LINE_REACH), worker.foot - 2, right);
            if (heard) {
                game.audio().playSfx(Assets.Sfx.WATER);
            }
        } else {
            fx.hop(icon, worker.centreX(), worker.foot + 26);
        }
    }

    private void runDemo() {
        IslandProps.Prop worker = workerFor(demoKind == 2 ? 3 : 1);
        VillageCatalog.Workshop workshop = worker == null ? null : workshopOf(worker);
        if (demoKind == 3) {
            for (int i = 0; i < plots.size; i++) {
                if (Farm.ripe(village, farm, i)) {
                    workPlot(i);
                    return;
                }
            }
            // Nothing ripe on this profile: the flight alone, from the first plot.
            TextureRegion pumpkin = TradeScreen.goodIcon(game, "pumpkin");
            for (int i = 0; i < 3 && plots.size > 0; i++) {
                fx.fly(pumpkin, plots.get(0)[0], plots.get(0)[1] + 8);
            }
            return;
        }
        if (workshop == null) {
            return;
        }
        VillageState.Work work = Workshops.work(farm, workshop);
        work.held = Math.max(work.held, demoKind == 2 ? 1 : 3);
        if (demoKind == 2) {
            made(worker, workshop, 0);
        } else {
            collect(worker);
        }
    }

    /** The wheel, towards the player to go further out, and the two zoom keys. */
    private void stepZoom() {
        int notches = input().scroll()
            + (input().justPressed(GameAction.ZOOM_OUT) ? 1 : 0)
            - (input().justPressed(GameAction.ZOOM_IN) ? 1 : 0);
        if (camera.zoomBy(notches, CameraController.fitZoom(mapW, mapH))) {
            game.audio().playSfx(Assets.SFX_MOVE);
        }
        camera.stepZoom();
    }

    /** The nearest person in range, or else the torii, or else the door. */
    private void findInteraction() {
        float px = world.playerX();
        float py = world.playerY();
        nearVillager = null;
        nearWorker = null;
        float best = Float.MAX_VALUE;
        for (Villager v : villagers) {
            float d = dist(px, py, v.x, v.y + 8);
            if (d < TALK_RANGE && d < best) {
                best = d;
                nearVillager = v;
            }
        }
        for (IslandProps.Prop w : workers) {
            float d = dist(px, py, w.centreX(), w.foot + 8);
            if (d < WORK_RANGE && d < best) {
                best = d;
                nearWorker = w;
                nearVillager = null;
            }
        }
        boolean someone = nearVillager != null || nearWorker != null;
        nearPlot = -1;
        if (!someone) {
            float closest = PLOT_RANGE;
            for (int i = 0; i < plots.size; i++) {
                float[] plot = plots.get(i);
                float d = dist(px, py, plot[0], plot[1] + CollisionGrid.TILE / 2f);
                if (d < closest) {
                    closest = d;
                    nearPlot = i;
                }
            }
        }
        boolean busy = someone || nearPlot >= 0;
        nearGate = !busy && dist(px, py, gateX, gateY) < GATE_RANGE;
        nearDoor = !busy && !nearGate && dist(px, py, doorX, doorY) < DOOR_RANGE;
    }

    // ---- the farm and the workers ----------------------------------------------

    /** The counter a worker keeps: the farmer's seed, the cook's kitchen, or null. */
    private static TradeScreen.Counter counterOf(IslandProps.Prop worker) {
        String role = worker.properties.get("role");
        return "farm".equals(role) ? TradeScreen.Counter.FARMER
            : "kitchen".equals(role) ? TradeScreen.Counter.COOK
            : null;
    }

    /** The workshop a worker keeps, or null for the farmer and the cook, who make nothing alone. */
    private VillageCatalog.Workshop workshopOf(IslandProps.Prop worker) {
        return village.workshop(worker.properties.get("role"));
    }

    /** Takes what a worker has made into the storehouse. False when there was nothing waiting. */
    private boolean collect(IslandProps.Prop worker) {
        VillageCatalog.Workshop workshop = workshopOf(worker);
        if (workshop == null) {
            return false;
        }
        ObjectIntMap<String> got = Workshops.collect(village, farm, workshop);
        if (got.size == 0) {
            return false;
        }
        StringBuilder said = new StringBuilder();
        for (ObjectIntMap.Entry<String> e : got) {
            if (said.length() > 0) {
                said.append("  ");
            }
            said.append(game.i18n().format("village.got", e.value, goodName(e.key)));
        }
        showToast(said.toString());
        int flying = 0;
        for (ObjectIntMap.Entry<String> e : got) {
            TextureRegion icon = TradeScreen.goodIcon(game, e.key);
            for (int i = 0; i < e.value && flying < MAX_FLYING; i++, flying++) {
                fx.fly(icon, worker.centreX(), worker.foot + 16);
            }
        }
        game.audio().playSfx(Assets.Sfx.PICKUP);
        return true;
    }

    /** Picks a plot's ripe crop, or sows a bare one with the first seed that fits. */
    private void workPlot(int index) {
        if (Farm.ripe(village, farm, index)) {
            VillageCatalog.Crop crop = Farm.crop(village, farm, index);
            int picked = Farm.harvest(village, farm, index);
            showToast(game.i18n().format("village.got", picked, goodName(crop.good)));
            TextureRegion icon = TradeScreen.goodIcon(game, crop.good);
            float[] plot = plots.get(index);
            for (int i = 0; i < Math.min(picked, MAX_FLYING); i++) {
                fx.fly(icon, plot[0], plot[1] + 8);
            }
            game.audio().playSfx(Assets.Sfx.GRASS);
            return;
        }
        VillageCatalog.Crop seed = seedFor(index);
        if (seed != null && Farm.sow(village, farm, index, seed)) {
            game.audio().playSfx(Assets.SFX_ACCEPT);
        }
    }

    /**
     * The seed a plot would take now: the first crop, in the catalog's order, of
     * which the player holds a seed the farm's level allows. Null when the plot
     * is growing, not yet open, or there is no such seed.
     */
    private VillageCatalog.Crop seedFor(int index) {
        for (int i = 0; i < village.crops().size; i++) {
            VillageCatalog.Crop crop = village.crops().get(i);
            if (Farm.canSow(village, farm, index, crop)) {
                return crop;
            }
        }
        return null;
    }

    /** What the prompt at a plot says: what pressing would do, or why it would do nothing. */
    private String plotLabel(I18n t, int index) {
        if (Farm.ripe(village, farm, index)) {
            return t.get("prompt.harvest");
        }
        if (Farm.stage(village, farm, index) >= 0) {
            int minutes = (int) Math.ceil(Farm.untilRipe(village, farm, index) / 60.0);
            return t.format("prompt.growing", Math.max(1, minutes));
        }
        if (index >= Farm.openPlots(village, farm)) {
            return t.get("prompt.plot_locked");
        }
        VillageCatalog.Crop seed = seedFor(index);
        return seed == null ? t.get("prompt.no_seed") : t.format("prompt.sow", goodName(seed.good));
    }

    private String goodName(String goodId) {
        VillageCatalog.Good good = village.good(goodId);
        return good == null ? goodId : game.i18n().get(good.nameKey);
    }

    private void showToast(String text) {
        toast = text;
        toastSteps = TOAST_STEPS;
    }

    private void talk(Villager v) {
        I18n t = game.i18n();
        Quests.record(game.content(), game.profile(), QuestDef.Kind.TALK, v.id, 1);
        // A job this villager is holding out, or one they are owed for, comes
        // before their usual two lines: someone with something to say about
        // work says it first.
        if (questTalk(v)) {
            return;
        }
        String prefix = "npc." + v.id + ".";
        String first = t.get(prefix + "1");
        // The herbalist with an empty customer says so and opens nothing. A
        // shop screen where every price is out of reach is a worse answer than
        // a sentence, and she is the one who can give the sentence.
        // Broke means nothing to pay with and nothing to sell her either.
        boolean broke = v.id.equals(Assets.Npc.HERBALIST) && game.profile().gold <= 0
            && game.profile().village.stock.size == 0;
        openShopAfterTalk = v.id.equals(Assets.Npc.HERBALIST) && !broke;
        dialog.show(t.get(prefix + "name"), v.face, first,
                    t.get(prefix + (broke ? "broke" : "2")));
        game.audio().playSfx(Assets.SFX_ACCEPT);
    }

    /** A region's worker, who has a name, two lines, and their own face. */
    private void talkTo(IslandProps.Prop worker) {
        I18n t = game.i18n();
        String prefix = "npc.worker_" + worker.properties.get("role") + ".";
        openShopAfterTalk = false;
        dialog.show(t.get(prefix + "name"), portrait(worker), t.get(prefix + "1"),
                    t.get(prefix + "2"));
        game.audio().playSfx(Assets.SFX_ACCEPT);
    }

    /**
     * A worker's face for the dialog box, cut from their own first frame.
     *
     * <p>The pack's people have no portraits. Their frames are 96x64 with the
     * figure standing in the middle, head about a third of the way down, so a
     * 20px square there is head and shoulders.
     */
    private static TextureRegion portrait(IslandProps.Prop worker) {
        TextureRegion frame = worker.frame(0f);
        int x = frame.getRegionWidth() / 2 - 10;
        return new TextureRegion(frame, Math.max(0, x), 16, 20, 20);
    }

    /**
     * Whether this villager had something to say about work, and said it.
     *
     * <p>Paying first, then offering. A villager who owes the player for one
     * job and has another to hand out should settle up before asking for more,
     * which is both politer and how a player expects a quest giver to behave.
     *
     * <p>Accepting is not asked about. The dialogue box pages text and has no
     * yes-or-no, and building one for this would be a widget used by exactly
     * one screen; a job that costs nothing to hold is not a decision worth a
     * prompt. Taking it on is what reading about it does.
     */
    private boolean questTalk(Villager v) {
        I18n t = game.i18n();
        Array<QuestDef> owed = Quests.claimableFrom(game.content(), game.profile(), v.id);
        if (owed.size > 0) {
            QuestDef q = owed.first();
            Quests.claim(q, game.content(), game.profile());
            game.saves().save(game.profile());
            game.audio().playSfx(Assets.SFX_ACCEPT);
            dialog.show(t.get("npc." + v.id + ".name"), v.face,
                t.get("quest.handin"), t.get(q.nameKey) + " - " + q.rewardGold + "g");
            return true;
        }
        Array<QuestDef> offers = Quests.offeredBy(game.content(), game.profile(), v.id);
        if (offers.size == 0) {
            return false;
        }
        QuestDef q = offers.first();
        Quests.accept(q, game.profile());
        game.profile().tracked = q.id;
        game.saves().save(game.profile());
        game.audio().playSfx(Assets.SFX_ACCEPT);
        dialog.show(t.get("npc." + v.id + ".name"), v.face,
            t.get(q.nameKey), t.get(q.descKey), t.get("quest.taken"));
        return true;
    }

    /**
     * Marks this run as the one to carry on with, and writes it down.
     *
     * <p><b>The village is the only place a run is saved, and that is a rule
     * rather than an omission.</b> Saving inside a dungeon as well would sound
     * more generous and would be the opposite: a player could dive, take
     * whatever floor four was holding, close the game and continue in the
     * village with the loot and none of the risk. Every relic in the run would
     * be one quit away from being free.
     *
     * <p>So abandoning a dive costs the dive, exactly as dying does, and
     * nothing that was already banked is ever at stake - gold, unlocks, gear,
     * quests and the island all live on the profile and are written every
     * thirty seconds regardless of this.
     *
     * <p>Written immediately rather than left to the autosave. The whole point
     * is to survive the game being closed, and the closing is not always
     * something the game gets told about.
     */
    private void rememberRun() {
        if (game.reviewing()) {
            // A run built by --screen is nobody's progress, and writing it
            // over a real save would mean taking a screenshot cost the
            // player their game.
            return;
        }
        game.profile().savedRun = SavedRun.of(run);
        game.saves().save(game.profile());
    }

    /** The bag: the storehouse, the tools, the island's people, what is packed, and the kit. */
    private void openBag() {
        game.audio().playSfx(Assets.SFX_ACCEPT);
        stack().push(new BagScreen(game, this::openKit));
    }

    /**
     * The character sheet, on whichever page the caller means.
     *
     * <p>{@code kitOpen} is set whichever page it is, because the sheet can
     * change the ninja and both weapons from its own panels and the village
     * only learns about a change by walking back into its room. Setting it for
     * the bag page as well costs one room rebuild that was not needed; not
     * setting it for the page that did change costs a player who swapped
     * character and watched the old one walk away.
     */
    void openSheet(int tab, int side) {
        game.audio().playSfx(Assets.SFX_ACCEPT);
        kitOpen = true;
        stack().push(new CharacterScreen(game, tab, side));
    }

    /** The kit: the sheet, open on the panel that chooses a weapon. */
    void openKit() {
        openSheet(CharacterScreen.TAB_PROFILE, CharacterScreen.Side.KIT.ordinal());
    }

    /**
     * Through the front door.
     *
     * <p>A marker and a key press, the way the torii works, rather than a hole
     * cut in the collision under the door. The wall is one object in the art
     * and punching a gap in it means knowing which tile is the doorway, which
     * is a guess that survives exactly until the house moves.
     */
    private void goInside() {
        game.audio().playSfx(Assets.SFX_DOOR);
        stack().push(new HomeScreen(game));
    }

    private void openMap() {
        game.audio().playSfx(Assets.SFX_ACCEPT);
        // The gate opens onto the map now, not onto floor one. Which stage is
        // being descended to, and how hard it will be, are the map's questions;
        // the run itself is built there, by Screens.stageRun.
        stack().set(new WorldMapScreen(game));
    }

    private static float dist(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // ---- drawing -----------------------------------------------------------

    @Override
    public void render(float delta) {
        framesDrawn++;
        camera.follow(world.playerX(), world.playerY(), mapW, mapH);
        camera.apply();
        SpriteBatch batch = game.batch();
        OrthographicCamera cam = camera.camera();
        float seconds = steps() * Cfg.STEP;
        float halfW = cam.viewportWidth * cam.zoom / 2f;
        float halfH = cam.viewportHeight * cam.zoom / 2f;
        float left = cam.position.x - halfW;
        float right = cam.position.x + halfW;
        float bottom = cam.position.y - halfH;
        float top = cam.position.y + halfH;

        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        batch.setColor(SEA);
        batch.draw(game.skin().getRegion(Assets.Ui.PIXEL), left, bottom, right - left, top - bottom);
        batch.setColor(Color.WHITE);
        batch.end();

        for (Pass pass : passes) {
            if (pass.sea != null) {
                pass.sea.draw(renderer, cam, camera.viewport(), batch, seconds);
                continue;
            }
            if (pass.tiles != null) {
                renderer.setView(cam);
                renderer.render(pass.tiles);
                continue;
            }
            batch.setProjectionMatrix(cam.combined);
            batch.begin();
            if (pass.clouds != null) {
                pass.clouds.draw(batch, cam, world.playerX(), world.playerY());
            } else if (pass.sorted) {
                drawSorted(batch, pass.sprites, seconds, left, bottom, right, top);
            } else {
                for (IslandProps.Prop p : pass.sprites) {
                    if (p.touches(left, bottom, right, top)) {
                        p.draw(batch, seconds);
                    }
                }
                if (pass.sprites == props.ground) {
                    drawCrops(batch);
                }
            }
            batch.end();
        }

        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        fx.drawWorld(batch);
        batch.end();

        ui.snapTo(Cfg.VIRT_W / 2f, Cfg.VIRT_H / 2f);
        ui.apply();
        batch.setProjectionMatrix(ui.camera().combined);
        batch.begin();
        drawOverWorld(batch, cam);
        fx.drawScreen(batch, cam, silhouette);
        drawToast(batch, cam);
        drawOverlay(batch);
        batch.end();
    }

    /**
     * The farm's crops, from the save, on the ground under everyone: a crop is
     * no taller than its soil, so nobody ever stands behind one. Each sits in
     * the middle of its plot's tile.
     */
    private void drawCrops(SpriteBatch batch) {
        for (int i = 0; i < plots.size; i++) {
            int stage = Farm.stage(village, farm, i);
            if (stage < 0) {
                continue;
            }
            TextureRegion art = cropArt(Farm.crop(village, farm, i).id, stage);
            if (art == null) {
                continue;
            }
            float[] plot = plots.get(i);
            batch.draw(art, Math.round(plot[0] - art.getRegionWidth() / 2f),
                       plot[1] + Math.round((CollisionGrid.TILE - art.getRegionHeight()) / 2f));
        }
    }

    private TextureRegion cropArt(String crop, int stage) {
        return art("ui/sunny/crops/" + crop + "_0" + stage);
    }

    private TextureRegion art(String name) {
        if (!art.containsKey(name)) {
            art.put(name, game.skin().has(name, TextureRegion.class) ? game.skin().getRegion(name) : null);
        }
        return art.get(name);
    }

    /**
     * Which of the pack's seven green bars, empty to full, shows a workshop's
     * progress. The full one is kept for a full workshop, so a full bar always
     * means come and collect.
     */
    static int barFrame(float progress) {
        if (progress >= 1f) {
            return 6;
        }
        return Math.max(0, Math.min(5, (int) (progress * 6f)));
    }

    /**
     * Over each worker, a mark while goods wait; over the player, what was just
     * taken. Drawn in screen space at a map point, so they keep their size at
     * any zoom: from the whole island the marks are how to find who has goods.
     */
    private void drawOverWorld(SpriteBatch batch, OrthographicCamera cam) {
        TextureRegion alert = game.skin().has(ALERT, TextureRegion.class) ? game.skin().getRegion(ALERT) : null;
        for (IslandProps.Prop worker : workers) {
            VillageCatalog.Workshop workshop = workshopOf(worker);
            if (workshop == null) {
                continue;
            }
            float x = screenX(cam, worker.centreX());
            float y = Math.round(screenY(cam, worker.foot + 24)) + 2;
            if (x < -16 || x > Cfg.VIRT_W + 16 || y < -24 || y > Cfg.VIRT_H + 8) {
                continue;
            }
            // The bar over the head, and the mark for goods waiting over the bar.
            TextureRegion bar = art(BAR + barFrame(Workshops.progress(village, farm, workshop)));
            float top = y;
            if (bar != null) {
                batch.draw(bar, Math.round(x - bar.getRegionWidth() / 2f), y);
                top = y + bar.getRegionHeight() + 1;
            }
            if (alert != null && Workshops.ready(village, farm, workshop) > 0) {
                batch.draw(alert, Math.round(x - alert.getRegionWidth() / 2f), top);
            }
        }
        drawQuestMarks(batch, cam, alert);
        drawWaypoint(batch, cam);
    }

    /**
     * The arrow for the tracked job: at whoever is owed, or at the gate.
     *
     * <p>Two cases and no more. A finished job is handed in to the person who
     * gave it, so the arrow points at them; anything still in progress is done
     * down the well, so it points at the gate. A job with neither - a bounty
     * whose steps are all in the dungeon - falls into the second case, which is
     * the right answer for it too.
     */
    private void drawWaypoint(SpriteBatch batch, OrthographicCamera cam) {
        String tracked = game.profile().tracked;
        if (tracked == null || !game.content().hasQuest(tracked)) {
            return;
        }
        QuestDef q = game.content().quest(tracked);
        if (game.profile().quests.is(tracked, QuestLog.State.CLAIMED)) {
            return;
        }
        float toX;
        float toY;
        if (game.profile().quests.is(tracked, QuestLog.State.DONE) && q.hasGiver()) {
            Villager giver = villagerNamed(q.giver);
            if (giver == null) {
                return;
            }
            toX = screenX(cam, giver.x);
            toY = screenY(cam, giver.y + MARK_LIFT);
        } else {
            toX = screenX(cam, gateX);
            toY = screenY(cam, gateY);
        }
        Waypoint.draw(batch, art(Assets.Ui.ARROW_UP),
            screenX(cam, world.playerX()), screenY(cam, world.playerY()), toX, toY);
    }

    private Villager villagerNamed(String id) {
        for (Villager v : villagers) {
            if (v.id.equals(id)) {
                return v;
            }
        }
        return null;
    }

    /**
     * A mark over any villager with work to give or work to settle.
     *
     * <p><b>!</b> for a job on offer and <b>?</b> for one waiting to be paid,
     * which is the convention every game with quest givers uses and therefore
     * the one a player already knows. The same alert mark the workshops use is
     * the exclamation; the pack's speech bubble is the question, and until now
     * it was packed into the atlas and drawn by nothing.
     *
     * <p>Drawn in screen space at a map point, like the workshop marks above,
     * so they stay legible zoomed out to the whole island - which is exactly
     * when a player needs to know who to walk to.
     */
    private void drawQuestMarks(SpriteBatch batch, OrthographicCamera cam, TextureRegion alert) {
        TextureRegion chat = art(Assets.Ui.MARK_CHAT);
        for (Villager v : villagers) {
            boolean owed = Quests.claimableFrom(game.content(), game.profile(), v.id).size > 0;
            boolean offering = Quests.offeredBy(game.content(), game.profile(), v.id).size > 0;
            TextureRegion mark = owed ? chat : offering ? alert : null;
            if (mark == null) {
                continue;
            }
            float x = screenX(cam, v.x);
            float y = Math.round(screenY(cam, v.y + MARK_LIFT));
            if (x < -16 || x > Cfg.VIRT_W + 16 || y < -24 || y > Cfg.VIRT_H + 8) {
                continue;
            }
            // A slow bob, so the mark reads as attached to the person rather
            // than stuck to the map behind them.
            float bob = (steps() / MARK_BOB_STEPS) % 2 == 0 ? 0f : 1f;
            batch.draw(mark, Math.round(x - mark.getRegionWidth() / 2f), y + bob);
        }
    }

    /** What was just taken, over the player. After the flying goods, which would cover the words. */
    private void drawToast(SpriteBatch batch, OrthographicCamera cam) {
        if (toastSteps > 0 && toast != null) {
            float rise = (TOAST_STEPS - toastSteps) * 0.25f;
            batch.setColor(1f, 0.93f, 0.72f, Math.min(1f, toastSteps / 20f));
            Hud.shadowed(batch, font, toast, Math.round(screenX(cam, world.playerX())),
                         Math.round(screenY(cam, world.playerY() + 22)) + rise, Align.center);
            batch.setColor(Color.WHITE);
        }
    }

    /** Where a map x is on the virtual screen, through the world camera. */
    private static float screenX(OrthographicCamera cam, float x) {
        return (x - cam.position.x) / cam.zoom + Cfg.VIRT_W / 2f;
    }

    private static float screenY(OrthographicCamera cam, float y) {
        return (y - cam.position.y) / cam.zoom + Cfg.VIRT_H / 2f;
    }

    /**
     * The sprites a player walks among, with the player and the villagers
     * slotted in by where they stand.
     *
     * <p>The map lists these sprites already sorted, back to front, except
     * where two overlap and the scene's own order has to win. So the pass is a
     * merge, not a sort: before each sprite, everyone standing further back
     * than it is drawn first.
     */
    private void drawSorted(SpriteBatch batch, Array<IslandProps.Prop> sprites, float seconds,
                            float left, float bottom, float right, float top) {
        nextVillager = 0;
        actorsDrawn = false;
        playerFoot = world.playerY() - Player.BODY / 2f;
        for (IslandProps.Prop p : sprites) {
            drawStandingBehind(batch, p.foot);
            if (p.touches(left, bottom, right, top)) {
                p.draw(batch, seconds);
            }
        }
        drawStandingBehind(batch, -Float.MAX_VALUE);
    }

    /** Everyone not yet drawn who stands further back than {@code foot}, back first. */
    private void drawStandingBehind(SpriteBatch batch, float foot) {
        while (true) {
            float villager = nextVillager < byFoot.size ? byFoot.get(nextVillager).y : -Float.MAX_VALUE;
            float actors = actorsDrawn ? -Float.MAX_VALUE : playerFoot;
            float furthest = Math.max(villager, actors);
            if (furthest == -Float.MAX_VALUE || furthest <= foot) {
                return;
            }
            if (actors >= villager) {
                world.renderActors(batch);
                actorsDrawn = true;
            } else {
                byFoot.get(nextVillager++).draw(batch, steps());
            }
        }
    }

    private void drawOverlay(SpriteBatch batch) {
        I18n t = game.i18n();
        // The bigger coin out here; the dungeon HUD keeps the small one. See
        // Preload.coin for why the two are different.
        TextureRegion coin = Preload.coin();
        int textX = 17;
        if (coin != null) {
            batch.draw(coin, 4, Cfg.VIRT_H - 20);
            textX = 4 + coin.getRegionWidth() + 3;
        }
        batch.setColor(GOLD);
        Hud.shadowed(batch, font, String.valueOf(game.profile().gold), textX, Cfg.VIRT_H - 5,
                     Align.left);
        batch.setColor(Color.WHITE);
        drawBadge(batch);
        drawBagButton(batch);
        // The village's name as an arrival card, not a permanent label: in the
        // first screenshot it sat across a roof, unreadable, saying something
        // the player already knew.
        Hud.card(batch, game.skin(), font, card, t.get("floor.hub"), null);
        if (camera.targetZoom() >= CameraController.fitZoom(mapW, mapH)) {
            // Says where the player is, and the key that brings them back.
            Hud.prompt(batch, game.skin(), font, game.input().map().primary(GameAction.ZOOM_IN),
                       t.get("hub.zoom.fit"), 6, Cfg.VIRT_H - 32, Align.left);
        }

        if (dialog.open()) {
            dialog.draw(batch);
            return;
        }
        // A prompt that would do nothing - a crop still growing, no seed in hand -
        // says why without a key on it.
        int keycode = game.input().map().primary(GameAction.INTERACT);
        String label;
        if (nearVillager != null) {
            label = t.get("prompt.talk");
        } else if (nearWorker != null) {
            TradeScreen.Counter counter = counterOf(nearWorker);
            VillageCatalog.Workshop workshop = workshopOf(nearWorker);
            label = t.get(counter == TradeScreen.Counter.FARMER ? "prompt.shop"
                : counter == TradeScreen.Counter.COOK ? "prompt.cook"
                : workshop != null && Workshops.ready(village, farm, workshop) > 0 ? "prompt.collect"
                : "prompt.talk");
        } else if (nearPlot >= 0) {
            label = plotLabel(t, nearPlot);
            if (!Farm.ripe(village, farm, nearPlot) && seedFor(nearPlot) == null) {
                keycode = -1;
            }
        } else {
            String key = nearGate ? "prompt.descend"
                : nearDoor ? "prompt.enter_home"
                : world.promptKey();
            label = key == null ? null : t.get(key);
        }
        if (label != null) {
            Hud.prompt(batch, game.skin(), font, keycode, label, Cfg.VIRT_W / 2f, 10);
        }
    }

    /**
     * The kit badge, with the ninja currently in it.
     *
     * <p>Rebuilt only when the character changes, because building one means
     * slicing a sheet. The 32px idle frame is drawn into a 24px cell and
     * clipped by four pixels a side, which cuts empty sheet rather than the
     * ninja: the sprite sits in the middle of its cell with air round it.
     */
    private void drawBadge(SpriteBatch batch) {
        // Through Preload.idle rather than an atlas path built here. This drew
        // its own, of the ninja's shape, whoever was playing - so walking out
        // of the kit screen as one of the three side-view heroes threw out of
        // render and took the process with it.
        String who = run == null ? Assets.Actor.DEFAULT_CHARACTER : run.characterId;
        if (badgeIdle == null || !who.equals(badgeCharacter)) {
            badgeCharacter = who;
            badgeIdle = Preload.idle(who);
        }
        game.skin().getDrawable(Assets.Ui.CELL).draw(batch, BADGE_X, BADGE_Y, BADGE, BADGE);
        // Cropped to the head rather than scaled: a 32px frame in a 24px cell
        // loses four pixels a side, and they should come off the empty sheet
        // around the ninja rather than off the ninja.
        TextureRegion frame = Preload.face(badgeIdle.frame(Dir.DOWN, steps()), BADGE);
        batch.draw(frame, BADGE_X + (BADGE - frame.getRegionWidth()) / 2f,
                   BADGE_Y + (BADGE - frame.getRegionHeight()) / 2f);
        // The key under it, exactly as the bag beside it has always had one.
        // Without this the badge was the only thing on the screen that could
        // be opened but said nothing about how - it had to be clicked, on a
        // screen the player is otherwise driving entirely from the keyboard.
        Hud.prompt(batch, game.skin(), font,
                   game.input().map().primary(GameAction.SHEET), "",
                   BADGE_X + BADGE / 2f, BADGE_Y - Hud.LINE + 4);
    }

    /** The bag, beside the badge: the pack's basket, with the key that opens it under it. */
    private void drawBagButton(SpriteBatch batch) {
        game.skin().getDrawable(Assets.Ui.CELL).draw(batch, BAG_X, BADGE_Y, BADGE, BADGE);
        if (game.skin().has(BAG_ICON, TextureRegion.class)) {
            TextureRegion basket = game.skin().getRegion(BAG_ICON);
            batch.draw(basket, BAG_X + (BADGE - basket.getRegionWidth()) / 2,
                       BADGE_Y + (BADGE - basket.getRegionHeight()) / 2);
        }
        Hud.prompt(batch, game.skin(), font,
                   game.input().map().primary(GameAction.BAG), "",
                   BAG_X + BADGE / 2f, BADGE_Y - Hud.LINE + 4);
    }

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
        ui.resize(width, height);
    }

    @Override
    public void dispose() {
        if (world != null) {
            world.dispose();
        }
        if (silhouette != null) {
            silhouette.dispose();
        }
        for (Pass pass : passes) {
            if (pass.sea != null) {
                pass.sea.dispose();
            }
        }
        if (renderer != null) {
            renderer.dispose();
        }
        if (map != null) {
            map.dispose();
        }
    }

    /** One draw step: a run of tile layers, a layer of sprites, a layer of cloud, or the sea. */
    private static final class Pass {
        final int[] tiles;
        final Array<IslandProps.Prop> sprites;
        final boolean sorted;
        final CloudLayer clouds;
        final SeaRipple sea;

        Pass(int[] tiles, Array<IslandProps.Prop> sprites, boolean sorted, CloudLayer clouds) {
            this.tiles = tiles;
            this.sprites = sprites;
            this.sorted = sorted;
            this.clouds = clouds;
            this.sea = null;
        }

        Pass(SeaRipple sea) {
            this.tiles = null;
            this.sprites = null;
            this.sorted = false;
            this.clouds = null;
            this.sea = sea;
        }
    }

    /** A villager: where they stand, which way they look, what they look like. */
    private static final class Villager {
        final String id;
        final int x;
        final int y;
        final Anim idle;
        final TextureRegion face;
        Dir facing = Dir.DOWN;

        Villager(String id, int x, int y, Anim idle, TextureRegion face) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.idle = idle;
            this.face = face;
        }

        /** Turns toward the player once they are close enough to be noticed. */
        void face(float px, float py) {
            float dx = px - x;
            float dy = py - (y + 8);
            if (dx * dx + dy * dy < NOTICE_RANGE * NOTICE_RANGE) {
                facing = Dir.of(dx, dy);
            } else {
                facing = Dir.DOWN;
            }
        }

        /** Feet at (x, y). */
        void draw(SpriteBatch batch, int steps) {
            TextureRegion frame = idle.frame(facing, steps);
            batch.draw(frame, x - frame.getRegionWidth() / 2, y);
        }
    }
}
