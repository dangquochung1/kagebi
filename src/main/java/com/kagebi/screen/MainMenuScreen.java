package com.kagebi.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.kagebi.Cfg;
import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;
import com.kagebi.gfx.PixelViewport;
import com.kagebi.save.Profile;
import com.kagebi.save.SavedRun;
import com.kagebi.ui.I18n;

/**
 * The title screen, over a live Tiled map of the village.
 *
 * <p>The backdrop is the real map renderer rather than a painted image, which
 * means this screen also proves the whole Tiled pipeline - relative tileset
 * paths, layer order, the 16px grid - before any of it is load-bearing for
 * gameplay.
 */
public class MainMenuScreen extends GameScreen {

    /** How fast the fog drifts across, in virtual pixels per second. */
    private static final float FOG_SPEED = 4f;

    /**
     * fog.png is 41% pure opaque white, so it lightens whatever it covers. Kept
     * this low because the village is meant to read as bright and fresh; there
     * is deliberately no darkening scrim over the scene either. The panel is
     * fully opaque, so it never needed one to be legible - dimming the backdrop
     * only drained the colour out of the art.
     */
    private static final float FOG_ALPHA = 0.04f;

    private final Kagebi game;
    private Stage stage;

    private TiledMap map;
    private OrthogonalTiledMapRenderer mapRenderer;
    private OrthographicCamera mapCamera;
    private Texture fog;
    private float fogScroll;

    public MainMenuScreen(Kagebi game) {
        this.game = game;
    }

    @Override
    public InputProcessor inputProcessor() {
        return stage;
    }

    @Override
    public void show() {
        if (stage != null) {
            rebuild();      // language may have changed while this was covered
            return;
        }
        map = new TmxMapLoader().load(Assets.MAP_VILLAGE);
        mapRenderer = new OrthogonalTiledMapRenderer(map, game.batch());

        mapCamera = new OrthographicCamera();
        mapCamera.setToOrtho(false, Cfg.VIRT_W, Cfg.VIRT_H);
        // Framed on the player's own house and its garden, which is the best
        // thing on the map and the one a title screen should be showing. The
        // menu panel covers the middle of it, so what this is really choosing
        // is what shows down both sides: the treeline on one, the well and the
        // scarecrow on the other.
        //
        // Whole pixels only - half a pixel of offset makes every edge in the
        // scene shimmer once it is magnified.
        mapCamera.position.set(368, 248, 0);
        mapCamera.update();

        fog = new Texture(Gdx.files.internal(Assets.FOG));
        fog.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        // Wrap is a property of the texture, not of a region, which is exactly
        // why this file is kept out of the atlas.
        fog.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);

        stage = new Stage(
            new PixelViewport(Cfg.VIRT_W, Cfg.VIRT_H, new OrthographicCamera()),
            game.batch());
        rebuild();
        game.audio().playMusic(Assets.MUSIC_INTRO);
    }

    private void rebuild() {
        stage.clear();
        I18n t = game.i18n();

        Table root = new Table();
        root.setFillParent(true);

        Table panel = new Table();
        panel.setBackground(game.skin().getDrawable(Assets.Ui.PANEL_2));
        panel.top();

        panel.add(new Label("KAGEBI", game.skin(), "title")).padBottom(1).row();
        panel.add(new Label(t.get("game.title"), game.skin(), "dim")).padBottom(5).row();

        // Lit exactly when there is something to carry on with, which is the
        // question the button asks. It used to be hard-coded disabled with a
        // null action - a placeholder that shipped, and that a player rightly
        // read as the game having forgotten their progress.
        SavedRun saved = game.profile().savedRun;
        addMenuButton(panel, t.get("menu.continue"), saved == null,
            saved == null ? null : () -> {
                game.setRun(saved.restore());
                stack().set(new HubScreen(game).arriveAt("gate"));
            });
        // set() rather than push(): a new run should not leave a title screen,
        // its map and its fog texture alive underneath it for the whole run.
        //
        // Straight to the village, not to a character select. There used to be
        // one here and it had nothing to offer a new player: a fresh profile
        // owns one ninja and one sword, so it was a choice between one option
        // and a locked row. Choosing now lives in the village's character
        // sheet, which is reached at the point where there is something to
        // choose between.
        addMenuButton(panel, t.get("menu.newgame"), false, this::newGame);
        addMenuButton(panel, t.get("menu.settings"), false,
            () -> stack().push(new SettingsScreen(game)));
        addMenuButton(panel, t.get("menu.code"), false,
            () -> stack().push(new CodeScreen(game)));
        addMenuButton(panel, t.get("menu.credits"), false,
            () -> stack().set(new CreditsScreen(game, 1)));
        addMenuButton(panel, t.get("menu.quit"), false, Gdx.app::exit);

        // Reachable before the player can read the settings label, which is the
        // point of putting it here rather than inside settings.
        TextButton language = new TextButton(t.language().label, game.skin());
        language.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                I18n.Language next = game.i18n().language() == I18n.Language.VI
                    ? I18n.Language.EN : I18n.Language.VI;
                game.i18n().load(next);
                game.settings().setLanguage(next);
                rebuild();
            }
        });
        panel.add(language).padTop(5).row();

        root.add(panel).width(150);
        stage.addActor(root);

        // Outside the panel, in the screen's corner. The panel is already 177
        // of the 180 rows tall with the buttons it has, so a row added to it
        // would push its own border off the top of the screen; and this is not
        // a menu entry anyway. "boxed" rather than "dim" because what is behind
        // it here is the village map rather than a flat panel, and a version
        // nobody can read against the sea is a version nobody will quote.
        //
        // Untranslated on purpose: the question it answers - which build is
        // this? - is asked by a bug report, not by a player.
        Table corner = new Table();
        corner.setFillParent(true);
        corner.bottom().right();
        corner.add(new Label("v" + Cfg.VERSION, game.skin(), "boxed")).pad(2);
        stage.addActor(corner);
    }

    /**
     * Starts over, asking first when there is something to lose.
     *
     * <p>"Everything resets" is what New Game has to mean, and until now it did
     * not: it built a fresh run and left the profile - gold, unlocks, gear, the
     * island - entirely alone, so it was Continue with the floor forgotten.
     *
     * <p>The question is skipped on a profile with nothing in it. A dialog
     * asking a new player to confirm the destruction of a save they have not
     * made yet teaches them that the dialog means nothing.
     */
    private void newGame() {
        if (worthKeeping(game.profile())) {
            stack().push(new ConfirmScreen(game, "menu.newgame", "confirm.newgame",
                                           this::wipeAndStart));
            return;
        }
        wipeAndStart();
    }

    private void wipeAndStart() {
        game.resetProfile();
        game.setRun(Screens.freshRun(game, Assets.Actor.DEFAULT_CHARACTER, "katana",
                                     Screens.DEFAULT_MAX_HP));
        stack().set(new HubScreen(game));
    }

    /**
     * Whether this profile represents play that a wipe would destroy.
     *
     * <p>Four independent signs, because progress arrives by several routes and
     * any one of them is enough: a run to continue, money banked, a stage
     * cleared, or anything bought. A profile that has only had the language
     * changed is not progress.
     */
    private static boolean worthKeeping(Profile p) {
        return p.savedRun != null || p.gold > 0 || p.runs > 0 || p.deepestFloor > 0;
    }

    private void addMenuButton(Table panel, String text, boolean disabled,
                               Runnable action) {
        TextButton button = new TextButton(text, game.skin());
        button.setDisabled(disabled);
        if (action != null) {
            button.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    action.run();
                }
            });
        }
        panel.add(button).width(104).padBottom(2).row();
    }

    @Override
    public void update(float delta) {
        fogScroll += delta * FOG_SPEED;
    }

    @Override
    public void render(float delta) {
        mapRenderer.setView(mapCamera);
        mapRenderer.render();

        SpriteBatch batch = game.batch();
        batch.setProjectionMatrix(mapCamera.combined);
        batch.begin();
        float left = mapCamera.position.x - Cfg.VIRT_W / 2f;
        float bottom = mapCamera.position.y - Cfg.VIRT_H / 2f;

        batch.setColor(1f, 1f, 1f, FOG_ALPHA);
        float u = fogScroll / fog.getWidth();
        batch.draw(fog, left, bottom, Cfg.VIRT_W, Cfg.VIRT_H, u, 1f, u + 1f, 0f);

        batch.setColor(1f, 1f, 1f, 1f);
        batch.end();

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        if (stage != null) {
            stage.dispose();
        }
        if (mapRenderer != null) {
            mapRenderer.dispose();
        }
        if (map != null) {
            map.dispose();
        }
        if (fog != null) {
            fog.dispose();
        }
    }
}
