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
import com.kagebi.input.InputMap;
import com.kagebi.input.InputService;
import com.kagebi.run.RunState;
import com.kagebi.save.Profile;
import com.kagebi.save.SaveManager;
import com.kagebi.screen.GameScreen;
import com.kagebi.screen.ScreenStack;
import com.kagebi.screen.Screens;
import com.kagebi.settings.Settings;
import com.kagebi.ui.I18n;

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
    private SaveManager saves;
    private Profile profile;
    /** The run in progress, or null outside one. Set by the screens. */
    private RunState run;

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
    }

    private final Boot boot;
    private int framesRendered;

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

    public Profile profile() {
        return profile;
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
        saves = new SaveManager();
        profile = saves.load();

        screens = new ScreenStack();
        for (GameScreen screen : Screens.build(this, boot.screen, boot.page)) {
            screens.push(screen);
        }
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        ScreenUtils.clear(0.05f, 0.04f, 0.07f, 1f);
        audio.update(delta);
        screens.render(delta);

        if (boot.screenshotAfterFrames >= 0 && ++framesRendered >= boot.screenshotAfterFrames) {
            saveScreenshot(boot.screenshotPath);
            Gdx.app.exit();
        }
    }

    @Override
    public void resize(int width, int height) {
        screens.resize(width, height);
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
