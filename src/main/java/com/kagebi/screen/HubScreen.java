package com.kagebi.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
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
import com.kagebi.run.RunState;
import com.kagebi.save.VillageState;
import com.kagebi.screen.island.CloudLayer;
import com.kagebi.screen.island.IslandProps;
import com.kagebi.ui.DialogBox;
import com.kagebi.ui.Hud;
import com.kagebi.ui.I18n;
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
 * rounding to whole pixels happens inside {@link CameraController}.
 *
 * <p>The villagers and the island's people are drawn and managed here, not by
 * the {@link World}. They are furniture that talks: they do not move, fight or
 * drop anything, and teaching the simulation about them would be teaching combat
 * code about dialogue.
 */
public class HubScreen extends SimScreen {

    private static final Color GOLD = new Color(0xffad55ff);

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
    /** Steps a "+2 wood" floats over the player. */
    private static final int TOAST_STEPS = 60;
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
     * The kit badge: the ninja's own face, top right, opening the loadout.
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

    private final Kagebi game;
    private final CameraController camera = new CameraController();
    private final CameraController ui = new CameraController();

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
    /** Crop pictures by region name, null for one the atlas does not have. */
    private final ObjectMap<String, TextureRegion> cropArt = new ObjectMap<>();
    private VillageCatalog village;
    private VillageState farm;
    /** What was just harvested or collected, floating over the player, and its steps left. */
    private String toast;
    private int toastSteps;

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
    /** Every marker the map names, y-up. */
    private final java.util.Map<String, int[]> markers = new java.util.HashMap<>();

    /** The badge portrait, rebuilt when the player changes who they are. */
    private Anim badgeIdle;
    private String badgeCharacter;

    /** Set when the loadout closes, so the world picks the new kit up. */
    private boolean loadoutOpen;

    /** Set while the herbalist is speaking; her last page opens her stall. */
    private boolean openShopAfterTalk;

    // Per-frame state of the depth-sorted pass.
    private int nextVillager;
    private boolean actorsDrawn;
    private float playerFoot;

    public HubScreen(Kagebi game) {
        super(game.input());
        this.game = game;
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

    @Override
    public InputProcessor inputProcessor() {
        return game.input();
    }

    @Override
    public void show() {
        game.input().clear();
        if (map != null) {
            if (loadoutOpen) {
                loadoutOpen = false;
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

        if (dialog.open()) {
            dialog.step();
            if (input().justPressed(GameAction.INTERACT) || input().justPressed(GameAction.ATTACK)) {
                // The herbalist's conversation ends by opening her stall. She
                // is the only villager who sells anything, so the shop is
                // reached by talking to her rather than by a second key: one
                // fewer thing on screen, and the dialogue is the sign.
                if (dialog.advance() && openShopAfterTalk) {
                    openShopAfterTalk = false;
                    stack().push(new ShopScreen(game));
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
        if (input().justPressed(GameAction.INVENTORY) || badgeClicked()) {
            openLoadout();
            return;
        }

        world.step(input());
        findInteraction();

        if (input().justPressed(GameAction.INTERACT)) {
            if (nearVillager != null) {
                talk(nearVillager);
            } else if (nearWorker != null) {
                // Goods waiting are taken first; a worker with none has a word.
                if (!collect(nearWorker)) {
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
        game.audio().playSfx(Assets.SFX_ACCEPT);
        return true;
    }

    /** Picks a plot's ripe crop, or sows a bare one with the first seed that fits. */
    private void workPlot(int index) {
        if (Farm.ripe(village, farm, index)) {
            VillageCatalog.Crop crop = Farm.crop(village, farm, index);
            int picked = Farm.harvest(village, farm, index);
            showToast(game.i18n().format("village.got", picked, goodName(crop.good)));
            game.audio().playSfx(Assets.SFX_ACCEPT);
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
        String prefix = "npc." + v.id + ".";
        String first = t.get(prefix + "1");
        // The herbalist with an empty customer says so and opens nothing. A
        // shop screen where every price is out of reach is a worse answer than
        // a sentence, and she is the one who can give the sentence.
        boolean broke = v.id.equals(Assets.Npc.HERBALIST) && game.profile().gold <= 0;
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
     * Whether the mouse was clicked on the kit badge this step.
     *
     * <p>Polled here rather than routed through {@link com.kagebi.input.InputService},
     * which is a key-to-action map and has no notion of a pointer. {@link SimScreen}
     * asks that input be read in {@code step} and nowhere else, and this obeys
     * that; one clickable rectangle on one screen is not worth a second input
     * model. The badge is drawn in UI space, so the click is tested there.
     */
    private boolean badgeClicked() {
        if (!Gdx.input.justTouched()) {
            return false;
        }
        float scaleX = Gdx.graphics.getWidth() / (float) Cfg.VIRT_W;
        float scaleY = Gdx.graphics.getHeight() / (float) Cfg.VIRT_H;
        float x = Gdx.input.getX() / scaleX;
        // Screen y runs down from the top; the virtual one runs up.
        float y = Cfg.VIRT_H - Gdx.input.getY() / scaleY;
        return x >= BADGE_X && x <= BADGE_X + BADGE
            && y >= BADGE_Y && y <= BADGE_Y + BADGE;
    }

    private void openLoadout() {
        game.audio().playSfx(Assets.SFX_ACCEPT);
        loadoutOpen = true;
        stack().push(new CharacterSelectScreen(game, 0).asLoadout());
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

        batch.setColor(Color.WHITE);
        for (Pass pass : passes) {
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
        drawOverWorld(batch);
        batch.end();

        ui.snapTo(Cfg.VIRT_W / 2f, Cfg.VIRT_H / 2f);
        ui.apply();
        batch.setProjectionMatrix(ui.camera().combined);
        batch.begin();
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
        String name = "ui/sunny/crops/" + crop + "_0" + stage;
        if (!cropArt.containsKey(name)) {
            cropArt.put(name, game.skin().has(name, TextureRegion.class) ? game.skin().getRegion(name) : null);
        }
        return cropArt.get(name);
    }

    /** Over each worker, a mark while goods wait; over the player, what was just taken. */
    private void drawOverWorld(SpriteBatch batch) {
        TextureRegion alert = game.skin().has(ALERT, TextureRegion.class) ? game.skin().getRegion(ALERT) : null;
        if (alert != null) {
            for (IslandProps.Prop worker : workers) {
                VillageCatalog.Workshop workshop = workshopOf(worker);
                if (workshop != null && Workshops.ready(village, farm, workshop) > 0) {
                    batch.draw(alert, Math.round(worker.centreX() - alert.getRegionWidth() / 2f),
                               worker.foot + 26);
                }
            }
        }
        if (toastSteps > 0 && toast != null) {
            float rise = (TOAST_STEPS - toastSteps) * 0.25f;
            batch.setColor(1f, 0.93f, 0.72f, Math.min(1f, toastSteps / 20f));
            Hud.shadowed(batch, font, toast, world.playerX(), world.playerY() + 22 + rise, Align.center);
            batch.setColor(Color.WHITE);
        }
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
        TextureRegion coin = game.skin().getRegion(Assets.Ui.COIN);
        batch.draw(coin, 6, Cfg.VIRT_H - 15);
        batch.setColor(GOLD);
        Hud.shadowed(batch, font, String.valueOf(game.profile().gold), 17, Cfg.VIRT_H - 5,
                     Align.left);
        batch.setColor(Color.WHITE);
        drawBadge(batch);
        // The village's name as an arrival card, not a permanent label: in the
        // first screenshot it sat across a roof, unreadable, saying something
        // the player already knew.
        Hud.card(batch, game.skin(), font, card, t.get("floor.hub"), null);

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
            VillageCatalog.Workshop workshop = workshopOf(nearWorker);
            label = t.get(workshop != null && Workshops.ready(village, farm, workshop) > 0
                ? "prompt.collect" : "prompt.talk");
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
        String id = run == null ? Assets.Actor.DEFAULT_CHARACTER : run.characterId;
        if (badgeIdle == null || !id.equals(badgeCharacter)) {
            badgeCharacter = id;
            badgeIdle = Anim.directional(Preload.actors(),
                Assets.Actor.player(id, Assets.Actor.PlayerAnim.IDLE), 32,
                Anim.DEFAULT_STEPS_PER_FRAME, true);
        }
        game.skin().getDrawable(Assets.Ui.CELL).draw(batch, BADGE_X, BADGE_Y, BADGE, BADGE);
        TextureRegion frame = badgeIdle.frame(Dir.DOWN, steps());
        batch.draw(frame, BADGE_X + (BADGE - 32) / 2f, BADGE_Y + (BADGE - 32) / 2f);
        Hud.prompt(batch, game.skin(), font,
                   game.input().map().primary(GameAction.INVENTORY), "",
                   BADGE_X + BADGE / 2f, BADGE_Y - Hud.LINE + 4);
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
        if (renderer != null) {
            renderer.dispose();
        }
        if (map != null) {
            map.dispose();
        }
    }

    /** One draw step: a run of tile layers, a layer of sprites, or a layer of cloud. */
    private static final class Pass {
        final int[] tiles;
        final Array<IslandProps.Prop> sprites;
        final boolean sorted;
        final CloudLayer clouds;

        Pass(int[] tiles, Array<IslandProps.Prop> sprites, boolean sorted, CloudLayer clouds) {
            this.tiles = tiles;
            this.sprites = sprites;
            this.sorted = sorted;
            this.clouds = clouds;
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
