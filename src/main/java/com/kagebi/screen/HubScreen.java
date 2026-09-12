package com.kagebi.screen;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.TiledMapTileSet;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.kagebi.Cfg;
import com.kagebi.Dir;
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
import com.kagebi.gfx.Anim;
import com.kagebi.gfx.CameraController;
import com.kagebi.input.GameAction;
import com.kagebi.run.RunState;
import com.kagebi.ui.DialogBox;
import com.kagebi.ui.Hud;
import com.kagebi.ui.I18n;

/**
 * Kagemura, between runs: three villagers, a torii, and the way down.
 *
 * <p>The only map in the game larger than the screen, so the only screen where
 * the camera scrolls. It follows the player and is clamped to the map; the
 * rounding to whole pixels happens inside {@link CameraController}.
 *
 * <p><b>The village darkens with every failed descent.</b> The story is that
 * the flame the village is named for weakens each time someone does not come
 * back, and {@code Profile.villageDarkness} counts that. It is drawn as a dusk
 * wash over the whole scene rather than said in a line of dialogue - the one
 * place the narrative is carried by the art.
 *
 * <p>The villagers are drawn and managed here, not by the {@link World}. They
 * are furniture that talks: they do not move, fight or drop anything, and
 * teaching the simulation about them would be teaching combat code about
 * dialogue.
 */
public class HubScreen extends SimScreen {

    /**
     * How much dusk one failed descent adds, as the alpha of the wash. Six
     * failures reach the cap; past that the village is dark but still legible,
     * which matters more than the metaphor.
     */
    private static final float DUSK_PER_STEP = 0.09f;
    private static final float DUSK_MAX = 0.54f;
    private static final Color DUSK = new Color(0x0c0a1aff);
    private static final Color GOLD = new Color(0xffad55ff);

    /**
     * The village's props layer mixes structure and scenery in one layer: 42
     * tiles of houses and torii from the {@code house} tileset, 237 tiles of
     * grass tufts and bushes from {@code nature}. Measured, not guessed. Every
     * props tile blocking - the rule rooms use - would leave the player unable
     * to take a step, so here the scenery tileset is walkable and the rest is
     * solid. See {@code notes/a.md}: the map should grow a {@code walls} layer.
     */
    private static final String SCENERY_TILESET = "nature";

    // Positions are in map pixels, y-up. The village has no object layer yet,
    // so they are written here; each was read off tools/preview_map.py output.

    /** In front of the three houses, beside each door. */
    private static final int[][] VILLAGER_AT = {{96, 208}, {238, 208}, {382, 208}};
    /**
     * In the clearing below the middle house. The first thing a player should
     * see is the village and the three people in it; framing the torii as well
     * cut the houses to a sixteen-pixel sliver across the top edge. The gate is
     * straight down from here, which is where anyone walks first anyway.
     */
    private static final int ENTRY_X = 240;
    private static final int ENTRY_Y = 186;
    /** The centre of the torii, which is the dungeon gate. */
    private static final int GATE_X = 248;
    private static final int GATE_Y = 80;
    private static final float GATE_RANGE = 30f;
    private static final float TALK_RANGE = 22f;
    /** Villagers turn to face the player inside this range. */
    private static final float NOTICE_RANGE = 56f;

    private final Kagebi game;
    private final CameraController camera = new CameraController();
    private final CameraController ui = new CameraController();

    private TiledMap map;
    private OrthogonalTiledMapRenderer renderer;
    private int[] below;
    private int[] above;
    private int mapW;
    private int mapH;

    private World world;
    private RunState run;
    private final Array<Villager> villagers = new Array<>();
    private DialogBox dialog;
    private BitmapFont font;

    /** Steps left on the arrival card. */
    private int card = Hud.CARD_STEPS;

    /** What INTERACT would do this step: a villager, the gate, or nothing. */
    private Villager nearVillager;
    private boolean nearGate;

    /** A villager to be already talking to on arrival, or -1. */
    private int talkOnShow = -1;

    public HubScreen(Kagebi game) {
        super(game.input());
        this.game = game;
    }

    /**
     * Opens with a villager mid-sentence. For {@code --screen talk}: the dialog
     * box is the screen's densest Vietnamese text, and the one most likely to
     * clip a tone mark, so it has to be reachable without walking to it.
     */
    HubScreen talkingTo(int villager) {
        talkOnShow = villager;
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
            game.audio().playMusic(Assets.MUSIC_VILLAGE);
            return;
        }
        font = game.skin().getFont("default");
        dialog = new DialogBox(game.skin());

        map = new TmxMapLoader().load(Assets.MAP_VILLAGE);
        renderer = new OrthogonalTiledMapRenderer(map, game.batch());
        below = TiledRooms.layerIndices(map, TiledRooms.BELOW);
        above = TiledRooms.layerIndices(map, TiledRooms.ABOVE);

        CollisionGrid grid = collision(map);
        mapW = grid.width() * CollisionGrid.TILE;
        mapH = grid.height() * CollisionGrid.TILE;

        run = game.run();
        if (run == null) {
            // Reached with --screen hub, or after an end screen that has
            // already cleared the run. The village still needs someone in it.
            run = Screens.freshRun(Assets.Actor.DEFAULT_CHARACTER, "katana",
                                   Screens.DEFAULT_MAX_HP);
            game.setRun(run);
        }
        run.floor = 0;
        run.layout = null;
        run.room = null;

        TextureAtlas npc = Preload.npc();
        for (int i = 0; i < Assets.Npc.VILLAGERS.length; i++) {
            String id = Assets.Npc.VILLAGERS[i];
            Villager v = new Villager(id, VILLAGER_AT[i][0], VILLAGER_AT[i][1],
                Anim.directional(npc, Assets.Npc.idle(id), 16, Anim.DEFAULT_STEPS_PER_FRAME, true),
                npc.findRegion(Assets.Npc.face(id)));
            villagers.add(v);
            // A villager occupies its tile, so the player walks round rather
            // than through them. The world only knows about the grid, which is
            // exactly why this is enough.
            grid.set(v.x / CollisionGrid.TILE, v.y / CollisionGrid.TILE, true);
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
        world.enterRoom(villageRoom(), grid, null);
        if (talkOnShow >= 0 && talkOnShow < villagers.size) {
            talk(villagers.get(talkOnShow));
        }
        game.audio().playMusic(Assets.MUSIC_VILLAGE);
    }

    /**
     * The village as a room, so the world can be handed it through the same
     * door as a dungeon room. It has no doors of its own - the way out is the
     * gate, which this screen owns - and one ENTRY spawn, which is how the
     * contract says an arrival with no door is placed.
     */
    private static Room villageRoom() {
        Array<RoomKind> kinds = new Array<>();
        kinds.add(RoomKind.START);
        Array<SpawnPoint> spawns = new Array<>();
        spawns.add(new SpawnPoint(SpawnPoint.Kind.ENTRY, ENTRY_X, ENTRY_Y, null));
        RoomTemplate template = new RoomTemplate("village", "village", kinds,
                                                 Assets.MAP_VILLAGE, spawns);
        return new Room(0, 0, RoomKind.START, template);
    }

    /**
     * Blocking tiles, by the room rule where it can be trusted and by tileset
     * where it cannot. A {@code walls} layer, if the village ever has one,
     * blocks outright.
     */
    private static CollisionGrid collision(TiledMap map) {
        CollisionGrid grid = TiledRooms.collision(map);
        MapLayer props = map.getLayers().get(TiledRooms.PROPS);
        TiledMapTileSet scenery = map.getTileSets().getTileSet(SCENERY_TILESET);
        if (!(props instanceof TiledMapTileLayer) || scenery == null) {
            return grid;
        }
        TiledMapTileLayer layer = (TiledMapTileLayer) props;
        MapLayer walls = map.getLayers().get(TiledRooms.WALLS);
        for (int y = 0; y < layer.getHeight(); y++) {
            for (int x = 0; x < layer.getWidth(); x++) {
                TiledMapTileLayer.Cell cell = layer.getCell(x, y);
                // A tile set stores its tiles by global id, so asking it for
                // this cell's id answers "is this grass?" without arithmetic
                // on first-gid ranges that the next map edit would break.
                if (cell != null && cell.getTile() != null
                        && scenery.getTile(cell.getTile().getId()) != null
                        && !solidIn(walls, x, y)) {
                    grid.set(x, y, false);
                }
            }
        }
        return grid;
    }

    private static boolean solidIn(MapLayer layer, int x, int y) {
        return layer instanceof TiledMapTileLayer
            && ((TiledMapTileLayer) layer).getCell(x, y) != null;
    }

    // ---- simulation --------------------------------------------------------

    @Override
    protected void step() {
        if (card > 0) {
            card--;
        }
        for (Villager v : villagers) {
            v.face(world.playerX(), world.playerY());
        }

        if (dialog.open()) {
            dialog.step();
            if (input().justPressed(GameAction.INTERACT) || input().justPressed(GameAction.ATTACK)) {
                dialog.advance();
            } else if (input().justPressed(GameAction.PAUSE)) {
                dialog.close();
            }
            return;         // the player stands still while spoken to
        }

        if (input().justPressed(GameAction.PAUSE)) {
            stack().push(new PauseScreen(game, false));
            return;
        }

        world.step(input());
        findInteraction();

        if (input().justPressed(GameAction.INTERACT)) {
            if (nearVillager != null) {
                talk(nearVillager);
            } else if (nearGate) {
                descend();
            } else if (world.promptKey() != null) {
                world.interact();
            }
        }
    }

    private void findInteraction() {
        float px = world.playerX();
        float py = world.playerY();
        nearVillager = null;
        float best = TALK_RANGE;
        for (Villager v : villagers) {
            float d = dist(px, py, v.x, v.y + 8);
            if (d < best) {
                best = d;
                nearVillager = v;
            }
        }
        nearGate = nearVillager == null && dist(px, py, GATE_X, GATE_Y) < GATE_RANGE;
    }

    private void talk(Villager v) {
        I18n t = game.i18n();
        String prefix = "npc." + v.id + ".";
        // The elder is the one who notices the flame going out. One line is
        // enough; the wash over the scene is saying the rest.
        String first = v.id.equals(Assets.Npc.ELDER) && game.profile().villageDarkness > 0
            ? t.get(prefix + "dim") : t.get(prefix + "1");
        dialog.show(t.get(prefix + "name"), v.face, first, t.get(prefix + "2"));
        game.audio().playSfx(Assets.SFX_ACCEPT);
    }

    private void descend() {
        game.audio().playSfx(Assets.SFX_ACCEPT);
        run.floor = 1;
        stack().set(new DungeonScreen(game));
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

        batch.setColor(Color.WHITE);
        renderer.setView(camera.camera());
        renderer.render(below);

        batch.setProjectionMatrix(camera.camera().combined);
        batch.begin();
        // Villagers further up the screen are behind the player, the rest in
        // front. The world draws the player in one call, so this split is the
        // whole of the depth sort.
        float py = world.playerY();
        for (Villager v : villagers) {
            if (v.y > py) {
                v.draw(batch, steps());
            }
        }
        world.renderActors(batch);
        for (Villager v : villagers) {
            if (v.y <= py) {
                v.draw(batch, steps());
            }
        }
        batch.end();
        renderer.render(above);

        ui.snapTo(Cfg.VIRT_W / 2f, Cfg.VIRT_H / 2f);
        ui.apply();
        batch.setProjectionMatrix(ui.camera().combined);
        batch.begin();
        drawDusk(batch);
        drawOverlay(batch);
        batch.end();
    }

    private void drawDusk(SpriteBatch batch) {
        int darkness = game.profile().villageDarkness;
        if (darkness <= 0) {
            return;
        }
        // A wash over the finished frame rather than a tint on the batch: the
        // actors are drawn by the world, which is free to set its own colour
        // for a hit flash and would quietly undo a tint set from out here.
        float alpha = Math.min(DUSK_MAX, darkness * DUSK_PER_STEP);
        batch.setColor(DUSK.r, DUSK.g, DUSK.b, alpha);
        batch.draw(game.skin().getRegion(Assets.Ui.PIXEL), 0, 0, Cfg.VIRT_W, Cfg.VIRT_H);
        batch.setColor(Color.WHITE);
    }

    private void drawOverlay(SpriteBatch batch) {
        I18n t = game.i18n();
        TextureRegion coin = game.skin().getRegion(Assets.Ui.COIN);
        batch.draw(coin, 6, Cfg.VIRT_H - 15);
        batch.setColor(GOLD);
        Hud.shadowed(batch, font, String.valueOf(game.profile().gold), 17, Cfg.VIRT_H - 5,
                     Align.left);
        batch.setColor(Color.WHITE);
        // The village's name as an arrival card, not a permanent label: in the
        // first screenshot it sat across a roof, unreadable, saying something
        // the player already knew.
        Hud.card(batch, game.skin(), font, card, t.get("floor.hub"), null);

        if (dialog.open()) {
            dialog.draw(batch);
            return;
        }
        String key = nearVillager != null ? "prompt.talk"
            : nearGate ? "prompt.descend"
            : world.promptKey();
        if (key != null) {
            Hud.prompt(batch, game.skin(), font,
                       game.input().map().primary(GameAction.INTERACT),
                       t.get(key), Cfg.VIRT_W / 2f, 10);
        }
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

        /** Feet at (x, y), which is also the tile the villager blocks. */
        void draw(SpriteBatch batch, int steps) {
            TextureRegion frame = idle.frame(facing, steps);
            batch.draw(frame, x - frame.getRegionWidth() / 2, y);
        }
    }
}
