package com.kagebi.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.ScreenUtils;
import com.kagebi.Cfg;
import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;
import com.kagebi.data.def.ItemDef;
import com.kagebi.gfx.PixelViewport;
import com.kagebi.run.RunState;
import com.kagebi.run.RunSummary;
import com.kagebi.save.Profile;
import com.kagebi.ui.I18n;
import com.kagebi.village.Pantry;

/**
 * What victory and game over have in common: the numbers, the bank, the way
 * home.
 *
 * <p>The two screens differ in a title, a colour and two pieces of music, and
 * agree on everything that matters - in particular on <em>when the gold is
 * banked</em>. That happens once, on first show, and not on the button press:
 * a player who closes the window on this screen has still finished the run,
 * and should still have the gold.
 *
 * <p>The sting plays first and the music only once it has finished. Starting
 * both at once buries a two-second jingle under a crossfade, and the jingle is
 * the part that tells the player what just happened.
 */
abstract class RunEndScreen extends SimScreen {

    /** Both stings are exactly 2.0 s - see {@code Assets.JINGLE_SUCCESS}. */
    private static final int STING_STEPS = 120;

    protected final Kagebi game;
    private final boolean victory;

    private Stage stage;
    private InputMultiplexer inputs;
    private MenuColumn menu;

    private RunSummary summary;
    private int banked;
    private boolean musicStarted;

    RunEndScreen(Kagebi game, boolean victory) {
        super(game.input());
        this.game = game;
        this.victory = victory;
    }

    protected abstract String sting();

    protected abstract String music();

    /** The skin label style for the title. */
    protected abstract String titleStyle();

    /**
     * How this run is written into the profile.
     *
     * <p>A hook rather than a branch because there are now three endings and
     * they bank differently, but the thing that must not vary is <em>when</em>:
     * once, on first show, before any button is pressed. That guarantee is
     * what {@link #bank} exists to hold, and it is held for every subclass by
     * there being no second copy of it.
     */
    protected void bankInto(Profile profile, RunSummary summary) {
        game.shop().bank(profile, summary);
    }

    /** The headline. */
    protected String titleKey() {
        return victory ? "game.victory" : "game.gameover";
    }

    /**
     * The ways onward. The map first, because that is where the next stage is,
     * and the village second, because that is where the gold is spent.
     */
    protected void addButtons(MenuColumn menu, I18n t) {
        menu.add(t.get("end.to_map"), () -> stack().set(new WorldMapScreen(game)));
        menu.add(t.get("end.to_village"), () -> stack().set(new HubScreen(game).arriveAt("gate")));
    }

    @Override
    public InputProcessor inputProcessor() {
        return inputs;
    }

    @Override
    public void show() {
        game.input().clear();
        if (stage != null) {
            return;
        }
        bank();
        stage = new Stage(new PixelViewport(Cfg.VIRT_W, Cfg.VIRT_H, new OrthographicCamera()),
                          game.batch());
        inputs = new InputMultiplexer(game.input(), stage);
        build();
        game.audio().stopMusic();
        game.audio().playSfx(sting(), 0f);
    }

    /**
     * Freezes the run into a summary and moves what survives death into the
     * profile.
     */
    private void bank() {
        RunState run = game.run();
        if (run == null) {
            run = Screens.freshRun(game, Assets.Actor.DEFAULT_CHARACTER,
                                   "katana", Screens.DEFAULT_MAX_HP);
        }
        run.victory = victory;
        summary = run.summary();
        banked = run.gold;

        // What a death is worth is a balance decision with a test behind it;
        // keeping it out of this screen is what stops the two drifting apart.
        Profile profile = game.profile();
        bankInto(profile, summary);
        if (victory) {
            // A cleared stage brings home what is left of what was carried, as
            // far as the pantry has room; a death loses it with everything else.
            Pantry.bringHome(profile, game.shop(), run.items, this::carriable);
        }
        // Banked into the profile above whatever happens, because this screen
        // has to show the totals. Written to disk only for a real run: a
        // launch that exists to photograph the death screen must not bank its
        // invented gold into somebody's actual save, which it did - taking one
        // screenshot of this screen moved a real profile from 3136 gold to
        // 5507. Same rule as the village clock and the village's own save.
        if (!game.reviewing() && !game.saves().save(profile)) {
            Gdx.app.error("save", "profile not written; the previous save stands");
        }

        // The next run starts from the village with the same kit, at full
        // health. What the dead run was carrying stays with it.
        //
        // The off hand comes along, which it did not used to. It was chosen at
        // character select and only had to survive one descent; now it has to
        // survive between stages, and dropping it here meant a player bought a
        // kunai for 350 gold and lost it the first time they cleared anything.
        RunState next = Screens.freshRun(game, run.characterId, run.weaponId,
                                         Screens.DEFAULT_MAX_HP);
        next.throwWeaponId = run.throwWeaponId;
        // So is the choice of what the quick key uses, for the same reason.
        next.quickItem = run.quickItem;
        // And the colour, which is the one of these the player can see.
        game.setRun(next);
    }

    /** Whether a run item may come home in the pantry: a consumable the content knows. */
    private boolean carriable(String itemId) {
        return game.content().hasItem(itemId)
            && game.content().item(itemId).kind == ItemDef.Kind.CONSUMABLE;
    }

    private void build() {
        stage.clear();
        I18n t = game.i18n();

        Table root = new Table();
        root.setFillParent(true);

        Table panel = new Table();
        panel.setBackground(game.skin().getDrawable(Assets.Ui.PANEL_2));
        panel.defaults().padLeft(2).padRight(2);

        panel.add(new Label(t.get(titleKey()), game.skin(), titleStyle()))
             .colspan(2).padBottom(6).row();

        stat(panel, t.get("game.stats.floor"), String.valueOf(summary.deepestFloor));
        stat(panel, t.get("game.stats.kills"), String.valueOf(summary.kills));
        stat(panel, t.get("game.stats.gold"), String.valueOf(summary.gold));
        // Gems only when there were some. Most runs find none, and a row of
        // zero on the screen that sums up a run says the run was worse than
        // it was.
        if (summary.diamonds > 0) {
            stat(panel, t.get("game.stats.gems"), String.valueOf(summary.diamonds));
        }
        stat(panel, t.get("game.stats.time"), summary.time());

        panel.add(new Label(t.format("end.banked", banked), game.skin(), "dim"))
             .colspan(2).padTop(6).padBottom(5).row();

        menu = new MenuColumn(game.skin(), game.audio());
        addButtons(menu, t);
        panel.add(menu.table(112)).colspan(2).row();

        root.add(panel).width(200);
        stage.addActor(root);
    }

    private void stat(Table panel, String label, String value) {
        panel.add(new Label(label, game.skin(), "dim")).left().expandX().padBottom(1);
        panel.add(new Label(value, game.skin())).right().padBottom(1).row();
    }

    @Override
    protected void step() {
        if (!musicStarted && steps() >= STING_STEPS) {
            musicStarted = true;
            game.audio().playMusic(music());
        }
        menu.step(input());
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.05f, 0.04f, 0.07f, 1f);
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
    }
}
