package com.kagebi.screen;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.utils.Align;
import com.kagebi.Cfg;
import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;
import com.kagebi.data.def.FloorDef;
import com.kagebi.gen.TiledRooms;
import com.kagebi.gfx.CameraController;
import com.kagebi.input.GameAction;
import com.kagebi.save.Profile;
import com.kagebi.settings.Difficulty;
import com.kagebi.ui.Hud;
import com.kagebi.ui.I18n;

/**
 * Where a stage is chosen, and how hard it will be.
 *
 * <p>The game used to be one descent: the village gate opened onto floor one
 * and the only way to see floor four was to survive the three above it in a
 * single sitting. Stages break that into five, each played from full health
 * and banked on its own, and this is the screen that holds them - nodes on a
 * map, opening one at a time as the one before it is cleared. Two more hang
 * off the trail rather than extending it: a side stage is open from the first
 * run and clearing it opens nothing, so the five stay a sequence.
 *
 * <p><b>The panel is a state of this screen, not a second screen.</b> The map
 * behind it is the context for the choice being made, the keys that move
 * between nodes are the keys that move between difficulties, and a pushed
 * screen would have to be told which node it was about and hand an answer
 * back. {@code ShopScreen} holds its two tabs the same way.
 *
 * <p>Drawn straight to the batch and driven from the keyboard, for the reason
 * {@code ShopScreen} gives for the same decision: this is a focus ring, a
 * detail block and a prompt row, and a scene2d {@code Stage} would buy a
 * layout pass and a second focus model to draw the same thing.
 */
public class WorldMapScreen extends SimScreen {

    /** The node marker, and the cell it is drawn in. */
    static final int NODE = 16;

    /**
     * Node centres in map pixels, y-up, indexed by stage - 1.
     *
     * <p>Read off {@code world.tmx}'s object layer when it has one, so the map
     * can be laid out again in Tiled without touching this file. These are the
     * stand-in for a map that has lost that layer, and they are also what makes
     * the arrangement checkable in a test with no GL context.
     */
    static final int[][] NODE_AT = {
        {40, 104}, {104, 72}, {168, 120}, {232, 56}, {280, 104},
        // Stage 6, low and left of the trail rather than past the end of it.
        // The five are a sequence; the cove is a side stage, and a node in
        // line with the others would say it is the step after the Flame Core.
        {56, 40},
        // Stage 7 beside stage 6 rather than past it: the two side stages are
        // one detour with two rooms in it, not a longer descent.
        {120, 24},
    };

    // The detail panel, centred. 200x128 is the largest box that still leaves
    // the outermost nodes showing at both edges, which is what makes it read
    // as opening ON the map rather than replacing it.
    private static final int PANEL_W = 200;
    private static final int PANEL_H = 128;
    private static final int PANEL_X = (Cfg.VIRT_W - PANEL_W) / 2;
    private static final int PANEL_Y = (Cfg.VIRT_H - PANEL_H) / 2;

    // The column, top to bottom. Line tops leave room for the four rows above
    // cap height that a stacked Vietnamese tone mark needs; see Hud.line.
    private static final int NAME_TOP = PANEL_Y + PANEL_H - 10;
    private static final int DESC_TOP = NAME_TOP - Hud.LINE - 2;
    private static final int DESC_X = PANEL_X + 10;
    private static final int DESC_W = PANEL_W - 20;
    /** Three wrapped lines. A fourth would reach the difficulty row. */
    private static final int DESC_LINES = 3;
    private static final int DIFF_TOP = DESC_TOP - DESC_LINES * Hud.LINE - 4;
    private static final int PROMPT_BOTTOM = PANEL_Y + 10;

    /** Air either side of a difficulty label, inside its box and between boxes. */
    private static final int CHIP_PAD = 12;

    private static final Color INK = new Color(0x131b1bff);
    private static final Color SOFT = new Color(0x46503cff);
    private static final Color LOCKED = new Color(0.32f, 0.30f, 0.36f, 1f);

    private final Kagebi game;
    private final CameraController camera = new CameraController();
    private BitmapFont font;

    private TiledMap map;
    private OrthogonalTiledMapRenderer renderer;
    private int[] below;
    private int[] above;
    private int[][] nodes = NODE_AT;

    /** Zero-based index of the node under the ring. */
    private int focus;
    private boolean panelOpen;
    private Difficulty chosen = Difficulty.DEFAULT;

    private boolean focusOnShow;
    private int focusWanted;
    private boolean panelOnShow;

    public WorldMapScreen(Kagebi game) {
        super(game.input());
        this.game = game;
    }

    /**
     * Opens with that stage under the ring, for {@code --screen world --page N}.
     *
     * <p>Stage 1 is left alone rather than pinned, which is the same place
     * {@link #openingFocus} puts it - so {@code --page 1}, the default, shows
     * the map exactly as a player finds it rather than a state only the flag
     * can reach.
     */
    WorldMapScreen focusOn(int stage) {
        if (stage <= 1) {
            return this;
        }
        focusOnShow = true;
        focusWanted = stage - 1;
        return this;
    }

    /** The same, with its panel already open, for {@code --screen stage}. */
    WorldMapScreen withPanelOpen() {
        panelOnShow = true;
        return this;
    }

    // ---- the rules, pure, so a test needs no window ---------------------------

    /**
     * Whether a stage may be played: one past the last one cleared, and every
     * one below it.
     *
     * <p>Cleared stages stay open on purpose. A stage that vanished once it
     * was beaten would make the map a checklist; leaving them means an early
     * stage is somewhere to go and earn gold, which is what a player who has
     * hit a wall on stage four actually needs.
     */
    static boolean unlocked(int stage, int clearedStages) {
        return unlocked(stage, clearedStages, false);
    }

    /**
     * As above, except that a side stage is open from the first run.
     *
     * <p>A side stage is not on the descent, so there is nothing for it to be
     * one past: gating it behind stage five would make a stage that exists to
     * be played whenever into the last thing anyone reaches.
     */
    static boolean unlocked(int stage, int clearedStages, boolean side) {
        return stage >= 1 && (side || stage <= clearedStages + 1);
    }

    /**
     * Moves the ring. Clamped rather than wrapped: the nodes sit along a drawn
     * trail, and stepping off the end of it back to the start reads as a bug
     * rather than as a convenience.
     */
    static int stepFocus(int focus, int delta, int count) {
        return Math.max(0, Math.min(count - 1, focus + delta));
    }

    /**
     * Which node the ring starts on: the first, always.
     *
     * <p>It used to be {@code clearedStages} - "open on the stage you are
     * working on" - and the reasoning was sound and the result was not. A
     * player who has finished the game opens the map on stage five every time,
     * with the whole trail behind them and the ring at the far end, and has to
     * walk it back to reach the early stage they came to farm. Reading left to
     * right is what a map with a numbered trail promises.
     *
     * <p>The argument is kept rather than dropped so this reads as a decision
     * at the call site instead of a constant somebody forgot to wire up.
     */
    static int openingFocus(int clearedStages) {
        return 0;
    }

    // ---- lifecycle -------------------------------------------------------------

    @Override
    public InputProcessor inputProcessor() {
        return game.input();
    }

    @Override
    public void show() {
        game.input().clear();
        font = game.skin().getFont("default");
        camera.snapTo(Cfg.VIRT_W / 2f, Cfg.VIRT_H / 2f);

        map = new TmxMapLoader().load(Assets.MAP_WORLD);
        below = TiledRooms.layerIndices(map, TiledRooms.BELOW);
        above = TiledRooms.layerIndices(map, TiledRooms.ABOVE);
        renderer = new OrthogonalTiledMapRenderer(map, game.batch());
        nodes = readNodes(map);

        if (game.settings() != null) {
            chosen = game.settings().difficulty();
        }
        focus = openingFocus(game.profile().clearedStages);
        if (focusOnShow) {
            focus = Math.max(0, Math.min(count() - 1, focusWanted));
        }
        panelOpen = panelOnShow && isOpen(focus + 1);
        game.audio().playMusic(Assets.MUSIC_VILLAGE);
    }

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
    }

    @Override
    public void dispose() {
        if (renderer != null) {
            renderer.dispose();
        }
        if (map != null) {
            map.dispose();
        }
    }

    /** Whether this stage may be played, with its own def consulted. */
    private boolean isOpen(int stage) {
        return unlocked(stage, game.profile().clearedStages, floor(stage).side);
    }

    private int count() {
        int n = game.content() == null ? 0 : game.content().allFloors().size;
        return n > 0 ? n : NODE_AT.length;
    }

    /**
     * Node centres out of the map's object layer, or the constants above.
     *
     * <p>Validated rather than trusted. Tiled writes an object's y down from
     * the top and libGDX flips it on load; if that ever stops being true every
     * node lands off the top of the screen, and a map with no nodes on it
     * looks like a map that failed to load rather than like a flip. A position
     * outside the screen is that symptom, so a layer containing one is refused
     * whole and the stand-in is used.
     */
    private int[][] readNodes(TiledMap tiled) {
        MapLayer layer = tiled.getLayers().get("spawns");
        if (layer == null) {
            return NODE_AT;
        }
        int[][] out = new int[count()][];
        for (MapObject o : layer.getObjects()) {
            int stage;
            try {
                stage = Integer.parseInt(o.getName());
            } catch (NumberFormatException e) {
                continue;
            }
            if (stage < 1 || stage > out.length) {
                continue;
            }
            Float x = o.getProperties().get("x", Float.class);
            Float y = o.getProperties().get("y", Float.class);
            if (x == null || y == null
                    || x < 0 || x > Cfg.VIRT_W || y < 0 || y > Cfg.VIRT_H) {
                return NODE_AT;
            }
            out[stage - 1] = new int[] {Math.round(x), Math.round(y)};
        }
        for (int[] node : out) {
            if (node == null) {
                return NODE_AT;
            }
        }
        return out;
    }

    // ---- simulation ------------------------------------------------------------

    @Override
    protected void step() {
        if (input().justPressed(GameAction.PAUSE)) {
            if (panelOpen) {
                panelOpen = false;
                game.audio().playSfx(Assets.SFX_CANCEL);
            } else {
                stack().set(new HubScreen(game).arriveAt("gate"));
            }
            return;
        }
        int delta = 0;
        if (input().justPressed(GameAction.MOVE_LEFT)) {
            delta = -1;
        } else if (input().justPressed(GameAction.MOVE_RIGHT)) {
            delta = 1;
        }
        if (delta != 0) {
            // With the panel open the same keys pick the difficulty, because
            // that is the only choice left to make and reaching for a second
            // pair of keys to make it would be a rule to learn for nothing.
            if (panelOpen) {
                chooseDifficulty(delta);
            } else {
                int next = stepFocus(focus, delta, count());
                if (next != focus) {
                    focus = next;
                    game.audio().playSfx(Assets.SFX_MOVE);
                }
            }
            return;
        }
        if (input().justPressed(GameAction.INTERACT) || input().justPressed(GameAction.ATTACK)) {
            if (panelOpen) {
                play();
            } else if (isOpen(focus + 1)) {
                panelOpen = true;
                game.audio().playSfx(Assets.SFX_ACCEPT);
            } else {
                game.audio().playSfx(Assets.SFX_CANCEL);
            }
        }
    }

    private void chooseDifficulty(int delta) {
        Difficulty[] all = Difficulty.values();
        int i = Math.max(0, Math.min(all.length - 1, chosen.ordinal() + delta));
        if (all[i] != chosen) {
            chosen = all[i];
            game.audio().playSfx(Assets.SFX_MOVE);
        }
    }

    private void play() {
        game.audio().playSfx(Assets.SFX_ACCEPT);
        // The map is now the place a difficulty is chosen, so the choice made
        // here is the one the settings screen opens on next time. One value in
        // two places, and no explaining to the player which of them wins.
        if (game.settings() != null) {
            game.settings().setDifficulty(chosen);
        }
        Screens.stageRun(game, focus + 1, chosen);
        stack().set(new DungeonScreen(game));
    }

    // ---- drawing ---------------------------------------------------------------

    @Override
    public void render(float delta) {
        camera.apply();
        SpriteBatch batch = game.batch();
        batch.setProjectionMatrix(camera.camera().combined);
        renderer.setView(camera.camera());

        renderer.render(below);
        batch.begin();
        drawNodes(batch);
        batch.end();
        renderer.render(above);

        batch.begin();
        if (panelOpen) {
            drawPanel(batch);
        } else {
            drawHeader(batch);
        }
        batch.end();
    }

    private void drawNodes(SpriteBatch batch) {
        Profile p = game.profile();
        int next = Math.min(count(), p.clearedStages + 1);
        for (int i = 0; i < count(); i++) {
            int[] at = nodes[Math.min(i, nodes.length - 1)];
            int x = at[0] - NODE / 2;
            int y = at[1] - NODE / 2;
            boolean open = isOpen(i + 1);
            game.skin().getDrawable(Assets.Ui.CELL).draw(batch, x, y, NODE, NODE);
            // Locked nodes are darkened, never hidden: the shape of what is
            // still ahead is half of what a map is for. The shop shelf and the
            // character select already say it this way.
            batch.setColor(open ? Color.WHITE : LOCKED);
            Hud.centred(batch, font, String.valueOf(i + 1), x + NODE / 2f, y + NODE - 3);
            batch.setColor(Color.WHITE);
            if (i == focus) {
                game.skin().getDrawable(Assets.Ui.FOCUS)
                    .draw(batch, x - 2, y - 2, NODE + 4, NODE + 4);
            }
            if (i + 1 == next) {
                // The "start here" marker. One pixel of bob, so the eye finds
                // it without it looking like something that can be picked up.
                int bob = steps() % 60 < 30 ? 0 : 1;
                game.skin().getDrawable(Assets.Ui.CURSOR)
                    .draw(batch, x + 2, y + NODE + 3 + bob, 13, 13);
            }
        }
    }

    /** The focused stage's name, in the rows the map does not reach. */
    private void drawHeader(SpriteBatch batch) {
        I18n t = game.i18n();
        boolean open = isOpen(focus + 1);
        String name = t.get(floor(focus + 1).nameKey);
        String label = open ? name : name + " - " + t.get("common.locked");
        batch.setColor(Color.WHITE);
        // Seven below the top edge, not three: Hud.line takes the line box's
        // top and then corrects upward by the font's ascent, so a name written
        // any higher loses the stacked tone mark on a letter like E-circumflex
        // to the edge of the screen - in Vietnamese only, which is exactly the
        // kind of thing that ships.
        Hud.shadowed(batch, font, label, Cfg.VIRT_W / 2f, Cfg.VIRT_H - 7, Align.center);
    }

    private void drawPanel(SpriteBatch batch) {
        I18n t = game.i18n();
        FloorDef def = floor(focus + 1);
        game.skin().getDrawable("scrim").draw(batch, 0, 0, Cfg.VIRT_W, Cfg.VIRT_H);
        game.skin().getDrawable(Assets.Ui.PANEL_3)
            .draw(batch, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);

        batch.setColor(INK);
        Hud.centred(batch, font, t.get(def.nameKey), Cfg.VIRT_W / 2f, NAME_TOP);
        batch.setColor(Color.WHITE);
        // Wrapped here and never in the language files: the font has no glyph
        // for a newline, so a break in a string fails the build - and a break
        // placed for English is in the wrong place in Vietnamese, which runs
        // longer.
        font.setColor(SOFT);
        font.draw(batch, t.get(def.descKey), DESC_X, DESC_TOP - font.getAscent(),
                  DESC_W, Align.left, true);
        font.setColor(Color.WHITE);

        drawDifficulties(batch, t);

        Hud.prompt(batch, game.skin(), font, game.input().map().primary(GameAction.INTERACT),
                   t.get("map.play"), PANEL_X + 8, PROMPT_BOTTOM, Align.left);
        Hud.prompt(batch, game.skin(), font, game.input().map().primary(GameAction.PAUSE),
                   t.get("common.back"), PANEL_X + PANEL_W - 8, PROMPT_BOTTOM, Align.right);
    }

    /**
     * The three difficulties, boxed the way the shop boxes its tabs.
     *
     * <p>Laid out from their measured widths rather than on a fixed pitch. The
     * labels are the longest in the game in both languages - "You're weak" and
     * "Binh thuong" with its marks - and a pitch that fits one does not fit
     * the other.
     */
    private void drawDifficulties(SpriteBatch batch, I18n t) {
        Difficulty[] all = Difficulty.values();
        float total = 0f;
        for (Difficulty d : all) {
            total += Hud.width(font, t.get(d.i18nKey)) + CHIP_PAD;
        }
        float x = PANEL_X + (PANEL_W - total) / 2f;
        for (Difficulty d : all) {
            String label = t.get(d.i18nKey);
            float w = Hud.width(font, label);
            if (d == chosen) {
                game.skin().getDrawable(Assets.Ui.BG)
                    .draw(batch, x - 4, DIFF_TOP - Hud.LINE - 1, w + 8, Hud.LINE + 3);
            }
            batch.setColor(d == chosen ? Color.WHITE : SOFT);
            Hud.line(batch, font, label, Math.round(x), DIFF_TOP);
            x += w + CHIP_PAD;
        }
        batch.setColor(Color.WHITE);
    }

    /** That stage's definition, or a stand-in while floors.json is short. */
    private FloorDef floor(int stage) {
        for (FloorDef f : game.content().allFloors()) {
            if (f.number == stage) {
                return f;
            }
        }
        return new FloorDef(stage, "floor." + stage, "floor." + stage + ".desc",
                            "", null, null, null, 5, 7, 0, 0,
                            new String[0], new int[0], 0, 0, null);
    }
}
