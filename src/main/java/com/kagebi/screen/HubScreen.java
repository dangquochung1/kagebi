package com.kagebi.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
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
 * Kagemura, between runs: three villagers, a torii, and a house of your own.
 *
 * <p><b>Where everything stands is read off the map.</b> It used to be eight
 * pixel constants in this file, which {@code notes/a.md} recorded as a thing to
 * fix and which made the village impossible to rearrange without editing Java.
 * {@code village.tmx} carries a {@code spawns} object layer now - {@code entry},
 * {@code gate}, {@code door}, {@code villager1..3} - so moving a house in Tiled
 * moves the person standing at its door. The constants below are the fallback
 * for a map that has lost the layer, and nothing more.
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

    /** Fallbacks, in map pixels y-up, for a map with no {@code spawns} layer. */
    private static final int[][] VILLAGER_AT = {{64, 288}, {64, 192}, {64, 96}};
    private static final int ENTRY_X = 400;
    private static final int ENTRY_Y = 160;
    private static final int GATE_X = 72;
    private static final int GATE_Y = 48;
    private static final int DOOR_X = 400;
    private static final int DOOR_Y = 192;

    private static final float GATE_RANGE = 24f;
    /**
     * Wide enough that the prompt is up where the player lands.
     *
     * <p>Arriving home and being told the door opens is the only way anyone
     * finds out that it does - there is no sign on it and no line of dialogue
     * about it. The marker sits on the doormat and the player arrives two
     * tiles off it, so this is just over that.
     */
    private static final float DOOR_RANGE = 26f;
    private static final float TALK_RANGE = 22f;
    /** The object layer the village's own markers live in. */
    static final String SPAWNS = "spawns";
    /** Villagers turn to face the player inside this range. */
    private static final float NOTICE_RANGE = 56f;

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
    private int[] below;
    private int[] above;
    private int mapW;
    private int mapH;
    private CollisionGrid grid;

    private World world;
    private RunState run;
    private final Array<Villager> villagers = new Array<>();
    private DialogBox dialog;
    private BitmapFont font;

    /** Steps left on the arrival card. */
    private int card = Hud.CARD_STEPS;

    /** Read off the map's object layer, or the constants above. */
    private int[][] villagerAt = VILLAGER_AT;
    private int gateX = GATE_X;
    private int gateY = GATE_Y;
    private int doorX = DOOR_X;
    private int doorY = DOOR_Y;
    private int entryX = ENTRY_X;
    private int entryY = ENTRY_Y;

    /** What INTERACT would do this step: a villager, the gate, the door, or nothing. */
    private Villager nearVillager;
    private boolean nearGate;
    private boolean nearDoor;

    /** A villager to be already talking to on arrival, or -1. */
    private int talkOnShow = -1;

    /** The badge portrait, rebuilt when the player changes who they are. */
    private Anim badgeIdle;
    private String badgeCharacter;

    /** Set when the loadout closes, so the world picks the new kit up. */
    private boolean loadoutOpen;

    /** Set while the herbalist is speaking; her last page opens her stall. */
    private boolean openShopAfterTalk;

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
            if (loadoutOpen) {
                loadoutOpen = false;
                // Walk back into the village, which is how the world is told
                // anything about the run: entering a room re-reads the ninja
                // and both weapons. Without this a player who changes ninja
                // stands in the village still wearing the old one until
                // something else happens to move them between rooms.
                world.enterRoom(villageRoom(), grid, null);
                card = 0;
            }
            game.audio().playMusic(Assets.MUSIC_VILLAGE);
            return;
        }
        font = game.skin().getFont("default");
        dialog = new DialogBox(game.skin());

        map = new TmxMapLoader().load(Assets.MAP_VILLAGE);
        renderer = new OrthogonalTiledMapRenderer(map, game.batch());
        below = TiledRooms.layerIndices(map, TiledRooms.BELOW);
        above = TiledRooms.layerIndices(map, TiledRooms.ABOVE);
        readSpawns(map);

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
        for (int i = 0; i < Assets.Npc.VILLAGERS.length; i++) {
            String id = Assets.Npc.VILLAGERS[i];
            Villager v = new Villager(id, villagerAt[i][0], villagerAt[i][1],
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
        if (talkOnShow >= 0 && talkOnShow < villagers.size) {
            // Standing in front of them, not across the village. The player
            // arrives at their own house now, which is far enough from the
            // villagers' row that --screen talk framed a dialogue box over an
            // empty garden and photographed nobody.
            Villager v = villagers.get(talkOnShow);
            world.enterRoom(villageRoom(v.x, v.y - 20), grid, null);
            talk(v);
        } else {
            world.enterRoom(villageRoom(), grid, null);
        }
        game.audio().playMusic(Assets.MUSIC_VILLAGE);
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
     * Where the people, the gate and the door stand, off the map's own object
     * layer. Anything the layer does not name keeps the constant above.
     *
     * <p>The same shape as {@code WorldMapScreen.readNodes}: a map is content
     * and content can be wrong, so a missing marker leaves the village
     * playable with a villager in an odd spot rather than crashing on the way
     * in. What it must never do is silently move nothing, which is why the
     * names are asserted in {@code VillageLayoutTest}.
     */
    private void readSpawns(TiledMap map) {
        MapLayer layer = map.getLayers().get(SPAWNS);
        if (layer == null) {
            return;
        }
        int[][] found = new int[villagerAt.length][];
        for (MapObject object : layer.getObjects()) {
            String name = object.getName();
            Float x = object.getProperties().get("x", Float.class);
            Float y = object.getProperties().get("y", Float.class);
            if (name == null || x == null || y == null) {
                continue;
            }
            int px = Math.round(x);
            int py = Math.round(y);
            if (name.equals("gate")) {
                gateX = px;
                gateY = py;
            } else if (name.equals("door")) {
                doorX = px;
                doorY = py;
            } else if (name.equals("entry")) {
                entryX = px;
                entryY = py;
            } else if (name.startsWith("villager")) {
                int index = name.charAt(name.length() - 1) - '1';
                if (index >= 0 && index < found.length) {
                    found[index] = new int[] {px, py};
                }
            }
        }
        for (int i = 0; i < found.length; i++) {
            if (found[i] != null) {
                villagerAt[i] = found[i];
            }
        }
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
            } else if (nearGate) {
                openMap();
            } else if (nearDoor) {
                goInside();
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
        nearGate = nearVillager == null && dist(px, py, gateX, gateY) < GATE_RANGE;
        nearDoor = nearVillager == null && !nearGate
            && dist(px, py, doorX, doorY) < DOOR_RANGE;
    }

    private void talk(Villager v) {
        I18n t = game.i18n();
        String prefix = "npc." + v.id + ".";
        // The elder is the one who notices the flame going out. One line is
        // enough; the wash over the scene is saying the rest.
        String first = v.id.equals(Assets.Npc.ELDER) && game.profile().villageDarkness > 0
            ? t.get(prefix + "dim") : t.get(prefix + "1");
        // The herbalist with an empty customer says so and opens nothing. A
        // shop screen where every price is out of reach is a worse answer than
        // a sentence, and she is the one who can give the sentence.
        boolean broke = v.id.equals(Assets.Npc.HERBALIST) && game.profile().gold <= 0;
        openShopAfterTalk = v.id.equals(Assets.Npc.HERBALIST) && !broke;
        dialog.show(t.get(prefix + "name"), v.face, first,
                    t.get(prefix + (broke ? "broke" : "2")));
        game.audio().playSfx(Assets.SFX_ACCEPT);
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
        drawBadge(batch);
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
            : nearDoor ? "prompt.enter_home"
            : world.promptKey();
        if (key != null) {
            Hud.prompt(batch, game.skin(), font,
                       game.input().map().primary(GameAction.INTERACT),
                       t.get(key), Cfg.VIRT_W / 2f, 10);
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
