package com.kagebi.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.kagebi.Cfg;
import com.kagebi.Dir;
import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;
import com.kagebi.data.def.FloorDef;
import com.kagebi.entity.DemoInput;
import com.kagebi.entity.EntityWorld;
import com.kagebi.entity.World;
import com.kagebi.gen.CollisionGrid;
import com.kagebi.gen.FloorGenerator;
import com.kagebi.gen.FloorLayout;
import com.kagebi.gen.Room;
import com.kagebi.gen.RoomCatalog;
import com.kagebi.gen.RoomKind;
import com.kagebi.gen.RoomTemplate;
import com.kagebi.gen.SpawnPoint;
import com.kagebi.gen.TiledRooms;
import com.kagebi.gfx.Anim;
import com.kagebi.gfx.CameraController;
import com.kagebi.input.GameAction;
import com.kagebi.run.RunState;
import com.kagebi.ui.Hud;
import com.kagebi.ui.I18n;

/**
 * A floor of the dungeon, one room on screen at a time.
 *
 * <p>This screen owns the maps and the camera; the simulation is a
 * {@link World} and is only driven from here. It is written against that
 * interface and nothing else - the world behind it is being replaced as this
 * is written, and nothing below may depend on how the placeholder behaves.
 *
 * <p><b>Two map passes with the actors between.</b> {@link TiledRooms#BELOW}
 * draws, then the world draws its actors, then {@link TiledRooms#ABOVE}. That
 * order is what lets a character walk behind an archway.
 *
 * <p><b>Doors that lead nowhere are sealed here, at runtime.</b> Every template
 * carries all four doorways so that any template can go anywhere on a floor.
 * On entering a room, each side with no neighbour has its open edge tiles made
 * solid in the collision grid and covered by a boulder. The doorway is found by
 * scanning the edge for open tiles rather than assumed, so a template with a
 * doorway two tiles wide, or off-centre, is sealed just as well.
 *
 * <p><b>Rooms slide, not cut.</b> An instant cut between two rooms of the same
 * tileset reads as the screen glitching. A 0.4 second slide reads as moving.
 * Both rooms are drawn from their own camera during it, and because the two
 * camera positions differ by exactly one room, rounding each to a whole pixel
 * can never open a seam between them.
 */
public class DungeonScreen extends SimScreen {

    /** 24 steps is 0.4 s - about 13 px a frame across a 320 px room. */
    private static final int SLIDE_STEPS = 24;
    /** Each half of the fade between floors. */
    private static final int FADE_STEPS = 20;

    /**
     * The game is five floors long. The registry says so once there is content;
     * until then this does, and the brief this screen was written to says the
     * exit on floor five is the win.
     */
    private static final int DEFAULT_FLOORS = 5;

    /** How close to the exit the player must stand to be offered it. */
    private static final float EXIT_RANGE = 22f;

    private static final Color SOFT = new Color(0xe8cfa9ff);
    private static final Color GROUND = new Color(0x0c0a10ff);

    private final Kagebi game;
    private final CameraController camera = new CameraController();
    private final CameraController ui = new CameraController();

    /** Loaded maps, keyed by template path: a room revisited is not reloaded. */
    private final ObjectMap<String, TiledMap> maps = new ObjectMap<>();
    private final ObjectMap<TiledMap, int[][]> passes = new ObjectMap<>();
    private final TmxMapLoader loader = new TmxMapLoader();
    private OrthogonalTiledMapRenderer renderer;
    private Array<RoomTemplate> templates;

    private World world;
    private RunState run;
    private FloorDef floorDef;
    private Hud hud;
    private BitmapFont font;
    private TextureRegion seal;
    private TextureRegion pixel;
    private Anim exitGlow;

    private TiledMap map;
    private int roomW;
    private int roomH;
    /** Sealed edge tiles of the current room, packed as {@code x << 16 | y}. */
    private final IntArray sealed = new IntArray();

    // Room slide. While it runs the world is not stepped: the player has
    // already been moved into the new room, and is drawn there.
    private int slide;
    private Dir slideDir;
    private TiledMap slideFrom;
    private final IntArray slideFromSealed = new IntArray();

    // Fade between floors; the action runs at the dark midpoint.
    private int fade;
    private Runnable fadeAction;
    private int title;

    private boolean nearExit;
    private boolean ended;
    /** An i18n key explaining why there is no floor to play, or null. */
    private String failure;

    private boolean mapOnShow;

    public DungeonScreen(Kagebi game) {
        super(game.input());
        this.game = game;
    }

    /** Opens with the floor map expanded, for {@code --screen map}. */
    DungeonScreen withMapOpen() {
        mapOnShow = true;
        return this;
    }

    private boolean slideOnShow;
    private boolean exitOnShow;

    /**
     * Opens in the floor's exit room, for {@code --screen exit}: the stairs,
     * their glow and the prompt are otherwise a whole floor's walk away.
     */
    DungeonScreen atExit() {
        exitOnShow = true;
        return this;
    }

    /**
     * Opens already sliding into the start room's first neighbour, for
     * {@code --screen slide}. The slide is the one render path that draws two
     * maps from two cameras in a frame, and a seam between them is invisible
     * in every screenshot but one taken halfway through.
     */
    DungeonScreen sliding() {
        slideOnShow = true;
        return this;
    }

    private RoomKind openIn;

    /**
     * Opens in the first room of a given kind, for {@code --screen fight},
     * {@code --screen treasure} and {@code --screen shop}.
     *
     * <p>Without it the only dungeon anyone can screenshot is the start room,
     * which is empty by design - so the game's central view, a room with
     * something in it trying to kill you, could not be looked at at all. The
     * same gap hid invisible chests for just as long: the rooms that have one
     * were unreachable without playing to them.
     */
    DungeonScreen openIn(RoomKind kind) {
        openIn = kind;
        return this;
    }

    private DemoInput demo;

    /**
     * Swings on a timer instead of reading the keyboard, for
     * {@code --screen swing}.
     *
     * <p>A swing is eighteen steps of a whole run, so the held weapon and the
     * slash arc could not be screenshotted at all - and the only other way to
     * catch one is to press the key by hand while the capture runs, which is
     * unrepeatable and leaks keystrokes into whatever window has focus.
     */
    DungeonScreen swinging() {
        demo = new DemoInput(GameAction.ATTACK);
        return this;
    }

    /** The same, on the throw button, for {@code --screen throw}. */
    DungeonScreen throwing() {
        demo = new DemoInput(GameAction.THROW);
        return this;
    }

    @Override
    public InputProcessor inputProcessor() {
        return game.input();
    }

    @Override
    public void show() {
        // A key held while an overlay was open must not arrive here as held.
        game.input().clear();
        if (hud != null) {
            playRoomMusic();
            return;
        }
        font = game.skin().getFont("default");
        hud = new Hud(game.skin(), game.i18n());
        seal = game.skin().getRegion(Assets.Ui.SEAL);
        pixel = game.skin().getRegion(Assets.Ui.PIXEL);
        exitGlow = Anim.strip(Preload.fx(), Assets.Fx.EXIT, 8, true);

        run = game.run();
        if (run == null) {
            run = Screens.freshRun(Assets.Actor.DEFAULT_CHARACTER, "katana",
                                   Screens.DEFAULT_MAX_HP);
            game.setRun(run);
        }
        templates = RoomCatalog.load();
        world = new EntityWorld(Preload.actors(), game.content(), run, game.settings());
        // Without this the world loads its own copies of both pages: ~20MB of
        // texture for art this screen is already holding.
        world.useSharedAtlases(game.skin().getAtlas(), Preload.fx());
        if (world instanceof EntityWorld) {
            // Upgrades bought in the village count for as much as relics found
            // in the dungeon, and both are resolved in one place.
            ((EntityWorld) world).useVillage(game.shop(), game.profile());
            ((EntityWorld) world).useAudio(game.audio());
            // The dungeon shopkeeper is a villager sprite, and this is the only
            // screen that ever puts one underground.
            ((EntityWorld) world).useNpcAtlas(Preload.npc());
            ((EntityWorld) world).useFont(font);
        }
        startFloor(Math.max(1, run.floor));
        if (mapOnShow) {
            hud.toggleMap();
        }
        if (exitOnShow && run.layout != null) {
            enter(run.layout.exit(), null);
        }
        if (openIn != null && run.layout != null) {
            for (Room candidate : run.layout.rooms()) {
                if (candidate.kind == openIn) {
                    enter(candidate, null);
                    break;
                }
            }
        }
        if (slideOnShow && run.room != null) {
            for (Dir d : Dir.ALL) {
                if (run.room.neighbour(d) != null) {
                    startSlide(d, run.room.neighbour(d));
                    break;
                }
            }
        }
    }

    // ---- floors ------------------------------------------------------------

    private void startFloor(int number) {
        floorDef = floorDef(number);
        FloorLayout layout;
        try {
            layout = new FloorGenerator(templates).generate(floorDef, floorSeed(number));
        } catch (RuntimeException e) {
            // No templates on disk, or a generator that cannot satisfy its own
            // invariant. Either way there is nothing to walk on, and saying so
            // on screen beats a stack trace from the renderer one frame later.
            Gdx.app.error("dungeon", "cannot build floor " + number, e);
            failure = "dungeon.no_rooms";
            return;
        }
        run.floor = number;
        run.layout = layout;
        run.deepestFloor = Math.max(run.deepestFloor, number);
        enter(layout.start(), null);
        title = Hud.CARD_STEPS;
    }

    /**
     * Each floor gets its own seed from the run's, so floor three of a run is
     * the same floor whether or not the player died on floor two of a previous
     * attempt with the same seed. The multiplier is the 64-bit golden ratio,
     * which spreads consecutive floor numbers across the whole seed space.
     */
    private long floorSeed(int number) {
        return run.seed ^ (number * 0x9E3779B97F4A7C15L);
    }

    /**
     * The floor's definition, or a stand-in while {@code floors.json} is empty.
     * The stand-in walks the biomes on disk in order, one per floor, so that a
     * playtest before content exists still sees every tileset there is - and
     * it reads the biome names off the folders rather than assuming any.
     */
    private FloorDef floorDef(int number) {
        for (FloorDef f : game.content().allFloors()) {
            if (f.number == number) {
                return f;
            }
        }
        ObjectSet<String> seen = new ObjectSet<>();
        Array<String> biomes = new Array<>();
        for (RoomTemplate t : templates) {
            if (seen.add(t.biome)) {
                biomes.add(t.biome);
            }
        }
        String biome = biomes.isEmpty() ? "" : biomes.get((number - 1) % biomes.size);
        return new FloorDef(number, "floor." + number, biome, null, null, null,
                            5, 7, 0, 0, new String[0], new int[0], 0, 0, null);
    }

    private int floorCount() {
        int n = game.content().allFloors().size;
        return n > 0 ? n : DEFAULT_FLOORS;
    }

    // ---- rooms -------------------------------------------------------------

    private TiledMap mapFor(RoomTemplate template) {
        TiledMap m = maps.get(template.path);
        if (m == null) {
            m = loader.load(template.path);
            maps.put(template.path, m);
            passes.put(m, new int[][] {
                TiledRooms.layerIndices(m, TiledRooms.BELOW),
                TiledRooms.layerIndices(m, TiledRooms.ABOVE)});
        }
        return m;
    }

    /**
     * Puts the player in a room. {@code enteredFrom} is the door <em>of this
     * room</em> they came through - the one opposite the way they were walking
     * - or null on arriving at a floor.
     */
    private void enter(Room room, Dir enteredFrom) {
        run.room = room;
        // The world is expected to mark this too; setting it here as well
        // means the minimap cannot depend on whether it does.
        room.visited = true;
        map = mapFor(room.template);
        CollisionGrid grid = TiledRooms.collision(map);
        roomW = grid.width() * CollisionGrid.TILE;
        roomH = grid.height() * CollisionGrid.TILE;
        seal(room, grid, sealed);
        world.enterRoom(room, grid, enteredFrom);
        if (renderer == null) {
            renderer = new OrthogonalTiledMapRenderer(map, game.batch());
        }
        hud.closeMap();
        playRoomMusic();
    }

    /**
     * Closes every doorway that has no room behind it: the open tiles on that
     * edge become solid, and are remembered so a boulder can be drawn on each.
     */
    static void seal(Room room, CollisionGrid grid, IntArray out) {
        out.clear();
        int w = grid.width();
        int h = grid.height();
        for (Dir d : Dir.ALL) {
            if (room.neighbour(d) != null) {
                continue;
            }
            int count = d == Dir.UP || d == Dir.DOWN ? w : h;
            for (int i = 0; i < count; i++) {
                int tx = d == Dir.LEFT ? 0 : d == Dir.RIGHT ? w - 1 : i;
                int ty = d == Dir.DOWN ? 0 : d == Dir.UP ? h - 1 : i;
                if (!grid.solidTile(tx, ty)) {
                    grid.set(tx, ty, true);
                    out.add(tx << 16 | ty);
                }
            }
        }
    }

    private void playRoomMusic() {
        if (floorDef == null) {
            return;
        }
        String track = run.room != null && run.room.kind == RoomKind.BOSS
            && floorDef.bossMusic != null ? floorDef.bossMusic : floorDef.music;
        String path = Assets.music(track);
        game.audio().playMusic(path != null && Gdx.files.internal(path).exists()
                               ? path : Assets.MUSIC_DUNGEON);
    }

    // ---- simulation --------------------------------------------------------

    @Override
    protected void step() {
        if (title > 0) {
            title--;
        }
        if (fade > 0) {
            fade--;
            if (fade == FADE_STEPS && fadeAction != null) {
                Runnable action = fadeAction;
                fadeAction = null;
                action.run();
            }
            return;
        }
        if (failure != null) {
            if (input().justPressed(GameAction.INTERACT) || input().justPressed(GameAction.PAUSE)) {
                stack().set(new HubScreen(game));
            }
            return;
        }
        if (slide > 0) {
            slide--;
            return;
        }
        if (ended) {
            return;
        }

        if (input().justPressed(GameAction.PAUSE)) {
            stack().push(new PauseScreen(game, true));
            return;
        }
        if (input().justPressed(GameAction.INVENTORY)) {
            stack().push(new InventoryScreen(game));
            return;
        }
        if (input().justPressed(GameAction.MAP)) {
            hud.toggleMap();
        }

        if (demo != null && world instanceof EntityWorld) {
            EntityWorld w = (EntityWorld) world;
            demo.aimAt(w.player(), w.hostiles());
            demo.tick();
            w.stepWith(demo);
        } else {
            world.step(input());
        }

        if (world.roomCleared()) {
            run.room.cleared = true;
        }
        if (world.playerDead()) {
            ended = true;
            stack().push(new GameOverScreen(game));
            return;
        }

        Dir door = world.doorReached();
        if (door != null && run.room.neighbour(door) != null) {
            startSlide(door, run.room.neighbour(door));
            return;
        }

        nearExit = run.room == run.layout.exit() && world.roomCleared()
            && distanceToExit() < EXIT_RANGE;
        if (input().justPressed(GameAction.INTERACT)) {
            if (nearExit) {
                descend();
            } else if (world.promptKey() != null) {
                world.interact();
            }
        }
    }

    private void startSlide(Dir door, Room next) {
        slideFrom = map;
        slideFromSealed.clear();
        slideFromSealed.addAll(sealed);
        slideDir = door;
        slide = SLIDE_STEPS;
        enter(next, door.opposite());
        game.audio().playSfx(Assets.SFX_DOOR);
    }

    private void descend() {
        game.audio().playSfx(Assets.SFX_ACCEPT);
        if (run.floor >= floorCount()) {
            ended = true;
            run.victory = true;
            stack().push(new VictoryScreen(game));
            return;
        }
        int next = run.floor + 1;
        fade = FADE_STEPS * 2;
        fadeAction = () -> startFloor(next);
    }

    /** Where the stairs are: the template's EXIT marker, or the room centre. */
    private float exitX() {
        for (SpawnPoint s : run.room.template.spawns) {
            if (s.kind == SpawnPoint.Kind.EXIT) {
                return s.x;
            }
        }
        return roomW / 2f;
    }

    private float exitY() {
        for (SpawnPoint s : run.room.template.spawns) {
            if (s.kind == SpawnPoint.Kind.EXIT) {
                return s.y;
            }
        }
        return roomH / 2f;
    }

    private float distanceToExit() {
        float dx = world.playerX() - exitX();
        float dy = world.playerY() - exitY();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // ---- drawing -----------------------------------------------------------

    @Override
    public void render(float delta) {
        SpriteBatch batch = game.batch();
        if (failure == null) {
            camera.shake(world.shake());
            // Snapped to the room, which is what follow() does for any map no
            // larger than the screen. A template that is larger scrolls rather
            // than being cropped, which is the better failure.
            camera.follow(world.playerX(), world.playerY(), roomW, roomH);
            if (slide > 0) {
                drawSlide(batch);
            } else {
                camera.apply();
                drawRoom(batch, map, sealed, true);
            }
        }

        ui.snapTo(Cfg.VIRT_W / 2f, Cfg.VIRT_H / 2f);
        ui.apply();
        batch.setProjectionMatrix(ui.camera().combined);
        batch.begin();
        if (failure != null) {
            drawFailure(batch);
        } else {
            hud.draw(batch, run, Cfg.VIRT_W, Cfg.VIRT_H);
            drawPrompt(batch);
            drawTitle(batch);
        }
        drawFade(batch);
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawSlide(SpriteBatch batch) {
        float p = Math.min(1f, (SLIDE_STEPS - slide + alpha()) / SLIDE_STEPS);
        float s = p * p * (3f - 2f * p);
        // The camera travels the way the player walked, so the new room arrives
        // from ahead and the old one leaves behind. Both rooms are drawn in the
        // same local coordinates, so the old one is the same view pushed one
        // room further along - a whole number of pixels, which is what keeps
        // the two roundings from ever disagreeing and opening a seam.
        float newX = camera.x() - slideDir.dx * roomW * (1f - s);
        float newY = camera.y() - slideDir.dy * roomH * (1f - s);
        camera.applyAt(newX + slideDir.dx * roomW, newY + slideDir.dy * roomH);
        drawRoom(batch, slideFrom, slideFromSealed, false);
        camera.applyAt(newX, newY);
        drawRoom(batch, map, sealed, true);
    }

    private void drawRoom(SpriteBatch batch, TiledMap m, IntArray seals, boolean actors) {
        int[][] pass = passes.get(m);
        batch.setColor(Color.WHITE);
        renderer.setMap(m);
        renderer.setView(camera.camera());
        renderer.render(pass[0]);

        batch.setProjectionMatrix(camera.camera().combined);
        batch.begin();
        for (int i = 0; i < seals.size; i++) {
            int packed = seals.get(i);
            batch.draw(seal, (packed >>> 16) * CollisionGrid.TILE,
                       (packed & 0xffff) * CollisionGrid.TILE);
        }
        if (actors) {
            if (run.room == run.layout.exit() && world.roomCleared()) {
                TextureRegion glow = exitGlow.frame(Dir.DOWN, steps());
                batch.draw(glow, Math.round(exitX() - glow.getRegionWidth() / 2f),
                           Math.round(exitY() - glow.getRegionHeight() / 2f));
            }
            world.renderActors(batch);
        }
        batch.end();
        renderer.render(pass[1]);
    }

    private void drawPrompt(SpriteBatch batch) {
        if (slide > 0 || fade > 0 || hud.mapOpen()) {
            return;
        }
        String key = nearExit
            ? (run.floor >= floorCount() ? "prompt.escape" : "prompt.descend")
            : world.promptKey();
        if (key == null) {
            return;
        }
        Hud.prompt(batch, game.skin(), font, game.input().map().primary(GameAction.INTERACT),
                   game.i18n().get(key), Cfg.VIRT_W / 2f, 10);
    }

    /** The floor's number and name over the room on arrival. */
    private void drawTitle(SpriteBatch batch) {
        if (fade > FADE_STEPS || hud.mapOpen()) {
            return;         // still going dark on the floor above, or covered
        }
        I18n t = game.i18n();
        Hud.card(batch, game.skin(), font, title,
                 t.format("game.floor", run.floor), t.get(floorDef.nameKey));
    }

    private void drawFade(SpriteBatch batch) {
        if (fade <= 0) {
            return;
        }
        float a = 1f - Math.abs(fade - FADE_STEPS) / (float) FADE_STEPS;
        batch.setColor(GROUND.r, GROUND.g, GROUND.b, a);
        batch.draw(pixel, 0, 0, Cfg.VIRT_W, Cfg.VIRT_H);
        batch.setColor(Color.WHITE);
    }

    private void drawFailure(SpriteBatch batch) {
        batch.setColor(SOFT);
        Hud.centred(batch, font, game.i18n().get(failure), Cfg.VIRT_W / 2f, Cfg.VIRT_H / 2f + 8);
        batch.setColor(Color.WHITE);
        Hud.prompt(batch, game.skin(), font, game.input().map().primary(GameAction.INTERACT),
                   game.i18n().get("common.back"), Cfg.VIRT_W / 2f, Cfg.VIRT_H / 2f - 24);
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
        for (TiledMap m : maps.values()) {
            m.dispose();
        }
        maps.clear();
        passes.clear();
    }
}
