package com.kagebi;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.SkinLoader;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.BufferUtils;
import com.badlogic.gdx.utils.ScreenUtils;
import com.kagebi.assets.Assets;
import com.kagebi.audio.AudioService;
import com.kagebi.data.ContentLoader;
import com.kagebi.data.ContentRegistry;
import com.kagebi.data.ShopCatalog;
import com.kagebi.data.VillageCatalog;
import com.kagebi.input.InputMap;
import com.kagebi.input.InputService;
import com.kagebi.run.RunState;
import com.kagebi.save.Profile;
import com.kagebi.save.SaveManager;
import com.kagebi.screen.CrashScreen;
import com.kagebi.screen.GameScreen;
import com.kagebi.screen.ScreenStack;
import com.kagebi.screen.Screens;
import com.kagebi.settings.Settings;
import com.kagebi.ui.I18n;
import com.kagebi.village.VillageClock;

/** Entry point. Owns everything that outlives a single screen. */
public class Kagebi extends ApplicationAdapter {

    private SpriteBatch batch;
    private AssetManager assets;
    private Skin skin;
    private Settings settings;
    private I18n i18n;
    private AudioService audio;
    private InputMap inputMap;
    private InputService input;
    private ScreenStack screens;
    private ContentRegistry content;
    private ShopCatalog shop;
    private VillageCatalog village;
    private SaveManager saves;
    private Profile profile;
    /** The run in progress, or null outside one. Set by the screens. */
    private RunState run;
    /** Set once something escapes {@link #render}, so the report is written once. */
    private boolean crashed;

    /**
     * Launch options. Art direction is the one thing no test can check, so the
     * game has to be able to open a given state and show its work.
     */
    public static final class Boot {
        public String screen = "menu";
        public int page = 1;
        public String language;
        /** Frames to render before saving a screenshot and quitting; -1 to disable. */
        public int screenshotAfterFrames = -1;
        public String screenshotPath;
        /**
         * Run seed, or null to draw a fresh one. Fixing it is what makes two
         * screenshots comparable: without it every launch builds a different
         * floor, and the same feature is photographed in a different room each
         * time - which is not a review, it is two unrelated pictures.
         */
        public Long seed;
        /**
         * The character every debug run starts as, or null for the default.
         *
         * <p>The roster is four bodies with two different animation shapes, and
         * a shot of a fight proves nothing about three of them. {@code --seed}
         * says where to look and {@code --frames} says when; this says who.
         */
        public String hero;
    }

    private final Boot boot;
    private int framesRendered;

    /** How often the village clock writes the profile, in seconds of play. */
    private static final float AUTOSAVE_SECONDS = 30f;
    /** Seconds since the clock last wrote it. */
    private float sinceSave;

    public Kagebi() {
        this(new Boot());
    }

    public Kagebi(Boot boot) {
        this.boot = boot;
    }

    public SpriteBatch batch() {
        return batch;
    }

    public Skin skin() {
        return skin;
    }

    public Settings settings() {
        return settings;
    }

    public I18n i18n() {
        return i18n;
    }

    public AudioService audio() {
        return audio;
    }

    public InputService input() {
        return input;
    }

    public ScreenStack screens() {
        return screens;
    }

    public ContentRegistry content() {
        return content;
    }

    /**
     * The village shop. Deliberately not inside {@link ContentRegistry}, which
     * holds only what a run is made of; upgrades and unlocks outlive runs.
     */
    public ShopCatalog shop() {
        return shop;
    }

    /** The village economy's content: goods, crops, workshops, tools, recipes. */
    public VillageCatalog village() {
        return village;
    }

    public Profile profile() {
        return profile;
    }

    /**
     * Throws the profile away and starts a new one.
     *
     * <p>Everything: gold, unlocks, gear, the bestiary, quests, the island and
     * the run that was being continued. This is what "New Game" has to mean if
     * it is to mean anything - before this it built a fresh run and left every
     * unlock in place, so the only difference between New Game and Continue was
     * which floor you stood on.
     *
     * <p>Written to disk here rather than at the next autosave. A player who
     * asks for a wipe and then closes the game must not find their old profile
     * waiting for them, having been told it was gone.
     */
    public void resetProfile() {
        profile = new Profile();
        saves.save(profile);
        run = null;
    }

    public SaveManager saves() {
        return saves;
    }

    public RunState run() {
        return run;
    }

    public void setRun(RunState run) {
        this.run = run;
    }

    @Override
    public void create() {
        batch = new SpriteBatch();
        settings = new Settings();
        if (boot.language != null) {
            settings.setLanguage(I18n.Language.fromCode(boot.language));
        }
        i18n = new I18n(settings.language());
        audio = new AudioService(settings);
        inputMap = new InputMap();
        input = new InputService(inputMap);

        assets = new AssetManager();
        // SkinLoader otherwise looks for the atlas next to the skin file; ours
        // lives under assets/atlas/, so the path has to be passed explicitly.
        assets.load(Assets.ATLAS_UI, TextureAtlas.class);
        assets.load(Assets.SKIN, Skin.class, new SkinLoader.SkinParameter(Assets.ATLAS_UI));
        assets.finishLoading();
        skin = assets.get(Assets.SKIN, Skin.class);

        // Worth knowing for real rather than trusting documentation: if these
        // differ, the viewport is being scaled and pixel art will shimmer.
        Gdx.app.log("kagebi", "window " + Gdx.graphics.getWidth() + "x" + Gdx.graphics.getHeight()
            + "  backbuffer " + Gdx.graphics.getBackBufferWidth() + "x"
            + Gdx.graphics.getBackBufferHeight());

        content = ContentLoader.load();
        shop = ShopCatalog.load();
        village = VillageCatalog.load();
        saves = new SaveManager();
        profile = saves.load();

        screens = new ScreenStack();
        if (boot.seed != null) {
            Screens.fixSeed(boot.seed);
        }
        Screens.debugHero(boot.hero);
        for (GameScreen screen : Screens.build(this, boot.screen, boot.page)) {
            screens.push(screen);
        }
    }

    /**
     * The frame, and the net under it.
     *
     * <p>An exception thrown from here is not reported anywhere by default: the
     * LWJGL3 backend tears the process down, and a game started from a shortcut
     * has no console for the trace to reach. The window vanishes, and a player
     * can only say that the game closed itself. Catching it costs one try block
     * and turns that into a file somebody can read - see {@link CrashReport}.
     *
     * <p>Caught once, not every frame. Whatever threw is still broken, so
     * letting the loop carry on would write the same trace sixty times a second
     * over a screen nobody can read; the stack is replaced by the report
     * instead, and there is deliberately no way back into the run from it.
     */
    @Override
    public void render() {
        try {
            frame();
        } catch (Throwable error) {
            reportCrash(error);
        }
    }

    private void frame() {
        float delta = Gdx.graphics.getDeltaTime();
        tickVillage(delta);
        ScreenUtils.clear(0.05f, 0.04f, 0.07f, 1f);
        audio.update(delta);
        screens.render(delta);

        if (boot.screenshotAfterFrames >= 0 && ++framesRendered >= boot.screenshotAfterFrames) {
            saveScreenshot(boot.screenshotPath);
            Gdx.app.exit();
        }
    }

    private void reportCrash(Throwable error) {
        if (crashed) {
            // The crash screen itself threw. Nothing here is trustworthy any
            // more, and a loop of failing reports helps no one.
            Gdx.app.error("kagebi", "crash screen failed", error);
            Gdx.app.exit();
            return;
        }
        crashed = true;
        Gdx.app.error("kagebi", "uncaught", error);
        java.nio.file.Path log = CrashReport.write(saves.directory(), error, crashContext());
        try {
            screens.set(new CrashScreen(this, CrashReport.headline(error),
                                        log == null ? null : log.toString()));
        } catch (Throwable fatal) {
            Gdx.app.error("kagebi", "could not show the crash screen", fatal);
            Gdx.app.exit();
        }
    }

    /** The line above the trace: what was on screen, and who was playing. */
    private String crashContext() {
        StringBuilder out = new StringBuilder();
        GameScreen top = screens == null ? null : screens.top();
        out.append("screen: ").append(top == null ? "none" : top.getClass().getSimpleName());
        if (run != null) {
            out.append("\nrun: ").append(run.characterId)
               .append(" / ").append(run.weaponId)
               .append(" / off=").append(run.throwWeaponId)
               .append("  floor=").append(run.floor)
               .append("  hp=").append(run.hp).append('/').append(run.maxHp)
               .append("  seed=").append(run.seed);
        } else {
            out.append("\nrun: none");
        }
        return out.toString();
    }

    @Override
    public void resize(int width, int height) {
        screens.resize(width, height);
    }

    /**
     * The village clock, and the save that keeps it.
     *
     * <p>Advanced here, above every screen, because the village grows while the
     * game is open - in the dungeon and the menus as much as on the island - and
     * not while it is closed. Written every thirty seconds and on the way out:
     * before the village had a clock the profile changed only at a purchase or
     * the end of a stage, and those were the only saves it needed.
     *
     * <p>A review run, one taking a screenshot, never writes the profile, so
     * photographing a screen changes nothing about the game photographed.
     */
    private void tickVillage(float delta) {
        VillageClock.advance(profile.village, delta);
        if (reviewing()) {
            return;
        }
        sinceSave += delta;
        if (sinceSave >= AUTOSAVE_SECONDS) {
            sinceSave = 0f;
            saves.save(profile);
        }
    }

    /**
     * Whether this launch exists only to photograph a screen.
     *
     * <p>Public because it is not only the clock's business any more: the
     * village writes the run down when it is entered, and a screenshot of the
     * village must not change the save it was taken from. Photographing a
     * screen changes nothing about the game photographed.
     */
    public boolean reviewing() {
        return boot.screenshotAfterFrames >= 0;
    }

    private static void saveScreenshot(String path) {
        int w = Gdx.graphics.getBackBufferWidth();
        int h = Gdx.graphics.getBackBufferHeight();
        byte[] pixels = ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
        Pixmap pixmap = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        BufferUtils.copy(pixels, 0, pixmap.getPixels(), pixels.length);
        PixmapIO.writePNG(Gdx.files.absolute(new java.io.File(path).getAbsolutePath()), pixmap);
        pixmap.dispose();
        Gdx.app.log("kagebi", "screenshot -> " + path + " (" + w + "x" + h + ")");
    }

    @Override
    public void dispose() {
        // First, while everything the profile could depend on still exists.
        if (saves != null && profile != null && !reviewing()) {
            saves.save(profile);
        }
        if (screens != null) {
            screens.dispose();
        }
        if (audio != null) {
            audio.dispose();
        }
        if (settings != null) {
            settings.save();
        }
        if (assets != null) {
            assets.dispose();
        }
        if (batch != null) {
            batch.dispose();
        }
    }
}
