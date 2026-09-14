package com.kagebi.screen;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.kagebi.Cfg;
import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;
import com.kagebi.entity.EntityWorld;
import com.kagebi.entity.World;
import com.kagebi.gen.CollisionGrid;
import com.kagebi.gen.Room;
import com.kagebi.gen.RoomKind;
import com.kagebi.gen.RoomTemplate;
import com.kagebi.gen.SpawnPoint;
import com.kagebi.gen.TiledRooms;
import com.kagebi.gfx.CameraController;
import com.kagebi.input.GameAction;
import com.kagebi.run.RunState;
import com.kagebi.ui.Hud;

/**
 * Inside the player's house.
 *
 * <p>Somewhere to be, and nothing more: no shop, no villagers, no fight. The
 * house was asked for as a place the character could walk into, and a room
 * that promised more than that by having a thing to press would be a worse
 * answer than one that simply exists.
 *
 * <p>Built the way {@link HubScreen} is - a Tiled map, a collision grid, an
 * {@link EntityWorld} handed one room - because that is how a walkable place
 * is made here, and the third one of these will be the one worth factoring.
 * It differs from the village in the one way that matters: there is no dusk
 * wash. The flame going out is something the village carries, and painting it
 * over somebody's kitchen would make a story point into a filter.
 */
public class HomeScreen extends SimScreen {

    /** How close to the door the player has to be for it to be the way out. */
    private static final float DOOR_RANGE = 20f;
    private static final Color GOLD = new Color(0xffad55ff);

    private final Kagebi game;
    private final CameraController camera = new CameraController();
    private final CameraController ui = new CameraController();

    private TiledMap map;
    private com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer renderer;
    private int[] below;
    private int[] above;
    private int mapW;
    private int mapH;

    private World world;
    private BitmapFont font;

    private int doorX;
    private int doorY;
    private boolean nearDoor;

    /** Steps left on the arrival card. */
    private int card = Hud.CARD_STEPS;

    public HomeScreen(Kagebi game) {
        super(game.input());
        this.game = game;
    }

    @Override
    public InputProcessor inputProcessor() {
        return game.input();
    }

    @Override
    public void show() {
        game.input().clear();
        if (map != null) {
            return;
        }
        font = game.skin().getFont("default");

        map = new TmxMapLoader().load(Assets.MAP_HOME);
        renderer = new com.badlogic.gdx.maps.tiled.renderers
            .OrthogonalTiledMapRenderer(map, game.batch());
        below = TiledRooms.layerIndices(map, TiledRooms.BELOW);
        above = TiledRooms.layerIndices(map, TiledRooms.ABOVE);

        CollisionGrid grid = TiledRooms.collision(map);
        mapW = grid.width() * CollisionGrid.TILE;
        mapH = grid.height() * CollisionGrid.TILE;
        readDoor(map);

        RunState run = game.run();
        if (run == null) {
            run = Screens.freshRun(game, Assets.Actor.DEFAULT_CHARACTER, "katana",
                                   Screens.DEFAULT_MAX_HP);
            game.setRun(run);
        }
        world = new EntityWorld(Preload.actors(), game.content(), run, game.settings());
        world.useSharedAtlases(game.skin().getAtlas(), Preload.fx());
        if (world instanceof EntityWorld) {
            ((EntityWorld) world).useVillage(game.shop(), game.profile());
            ((EntityWorld) world).useAudio(game.audio());
        }
        world.enterRoom(room(), grid, null);
    }

    /**
     * The door, off the map's object layer. It is both the way in and the way
     * out, so the player arrives standing on the mat and leaves from it -
     * which also means the fallback, the middle of the map, is somewhere they
     * can be rather than somewhere they might be buried.
     */
    private void readDoor(TiledMap map) {
        doorX = mapW / 2;
        doorY = mapH / 2;
        MapLayer layer = map.getLayers().get(HubScreen.SPAWNS);
        if (layer == null) {
            return;
        }
        for (MapObject object : layer.getObjects()) {
            Float x = object.getProperties().get("x", Float.class);
            Float y = object.getProperties().get("y", Float.class);
            if ("door".equals(object.getName()) && x != null && y != null) {
                doorX = Math.round(x);
                doorY = Math.round(y);
            }
        }
    }

    private Room room() {
        Array<RoomKind> kinds = new Array<>();
        kinds.add(RoomKind.START);
        Array<SpawnPoint> spawns = new Array<>();
        spawns.add(new SpawnPoint(SpawnPoint.Kind.ENTRY, doorX, doorY, null));
        RoomTemplate template = new RoomTemplate("home", "home", kinds,
                                                 Assets.MAP_HOME, spawns);
        return new Room(0, 0, RoomKind.START, template);
    }

    // ---- simulation --------------------------------------------------------

    @Override
    protected void step() {
        if (card > 0) {
            card--;
        }
        if (input().justPressed(GameAction.PAUSE)) {
            leave();
            return;
        }
        world.step(input());
        nearDoor = dist(world.playerX(), world.playerY(), doorX, doorY) < DOOR_RANGE;
        if (nearDoor && input().justPressed(GameAction.INTERACT)) {
            leave();
        }
    }

    private void leave() {
        game.audio().playSfx(Assets.SFX_DOOR);
        stack().pop();
    }

    private static float dist(float ax, float ay, float bx, float by) {
        float dx = ax - bx;
        float dy = ay - by;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    // ---- drawing -----------------------------------------------------------

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.06f, 0.05f, 0.07f, 1f);
        camera.follow(world.playerX(), world.playerY(), mapW, mapH);
        camera.apply();
        SpriteBatch batch = game.batch();

        batch.setColor(Color.WHITE);
        renderer.setView(camera.camera());
        renderer.render(below);

        batch.setProjectionMatrix(camera.camera().combined);
        batch.begin();
        world.renderActors(batch);
        batch.end();
        renderer.render(above);

        ui.snapTo(Cfg.VIRT_W / 2f, Cfg.VIRT_H / 2f);
        ui.apply();
        batch.setProjectionMatrix(ui.camera().combined);
        batch.begin();
        batch.draw(game.skin().getRegion(Assets.Ui.COIN), 6, Cfg.VIRT_H - 15);
        batch.setColor(GOLD);
        Hud.shadowed(batch, font, String.valueOf(game.profile().gold), 17,
                     Cfg.VIRT_H - 5, com.badlogic.gdx.utils.Align.left);
        batch.setColor(Color.WHITE);
        Hud.card(batch, game.skin(), font, card, game.i18n().get("floor.home"), null);
        if (nearDoor) {
            Hud.prompt(batch, game.skin(), font,
                       game.input().map().primary(GameAction.INTERACT),
                       game.i18n().get("prompt.leave_home"), Cfg.VIRT_W / 2f, 10);
        }
        batch.end();
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
}
