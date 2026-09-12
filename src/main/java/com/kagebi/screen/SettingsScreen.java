package com.kagebi.screen;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.kagebi.Cfg;
import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;
import com.kagebi.gfx.PixelViewport;
import com.kagebi.input.GameAction;
import com.kagebi.input.InputMap;
import com.kagebi.settings.Difficulty;
import com.kagebi.ui.I18n;
import com.kagebi.ui.KeyPrompts;
import com.kagebi.ui.TabBar;

/**
 * Audio, controls and video, in three tabs.
 *
 * <p>Everything here is laid out in the 320x180 virtual space, which leaves
 * roughly nine visible rows once the title, tabs and footer are accounted for.
 * That is why the keybinding list scrolls rather than trying to fit twelve
 * actions on screen at once.
 */
public class SettingsScreen extends GameScreen {

    private static final int ROW_HEIGHT = 13;

    private final Kagebi game;
    private Stage stage;
    private InputMultiplexer input;

    /** The action currently waiting for a key, or null. */
    private GameAction listening;
    private boolean listeningSecondary;
    private Table controlsList;
    private ScrollPane controlsScroll;
    private Label hint;

    private final int initialTab;

    public SettingsScreen(Kagebi game) {
        this(game, 0);
    }

    public SettingsScreen(Kagebi game, int initialTab) {
        this.game = game;
        this.initialTab = initialTab;
    }

    @Override
    public boolean isOpaque() {
        return false;   // the menu keeps drawing its village behind this panel
    }

    @Override
    public InputProcessor inputProcessor() {
        return input;
    }

    @Override
    public void show() {
        if (stage != null) {
            return;
        }
        stage = new Stage(
            new PixelViewport(Cfg.VIRT_W, Cfg.VIRT_H, new OrthographicCamera()),
            game.batch());

        // The capture processor sits in front of the stage so that, while a
        // rebind is pending, the next key press goes to the binding instead of
        // to whatever widget has focus.
        input = new InputMultiplexer(new RebindCapture(), stage);
        build();
    }

    private void build() {
        stage.clear();
        I18n t = game.i18n();

        Table root = new Table();
        root.setFillParent(true);
        root.pad(2);

        Table panel = new Table();
        panel.setBackground(game.skin().getDrawable(Assets.Ui.PANEL_2));
        panel.top();

        panel.add(new Label(t.get("settings.title"), game.skin(), "title"))
             .left().padBottom(3).row();

        TabBar tabs = new TabBar(game.skin());
        tabs.addTab(t.get("settings.tab.audio"), audioPage());
        tabs.addTab(t.get("settings.tab.controls"), controlsPage());
        tabs.addTab(t.get("settings.tab.video"), videoPage());
        tabs.select(initialTab);
        panel.add(tabs).grow().row();

        hint = new Label("", game.skin(), "danger");
        panel.add(hint).left().height(8).row();

        Table footer = new Table();
        TextButton reset = new TextButton(t.get("settings.reset"), game.skin());
        reset.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.input().map().resetToDefaults();
                game.input().map().save();
                refreshControls();
            }
        });
        TextButton back = new TextButton(t.get("common.back"), game.skin());
        back.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.settings().save();
                game.input().map().save();
                stack().pop();
            }
        });
        footer.add(reset).padRight(3);
        footer.add(back);
        panel.add(footer).right().padTop(2).row();

        root.add(panel).grow();
        stage.addActor(root);
    }

    // ---- audio -----------------------------------------------------------

    private Actor audioPage() {
        Table page = new Table();
        page.top().left();
        I18n t = game.i18n();
        addSlider(page, t.get("settings.master"), game.settings().master(),
                  v -> game.settings().setMaster(v));
        addSlider(page, t.get("settings.music"), game.settings().music(),
                  v -> game.settings().setMusic(v));
        addSlider(page, t.get("settings.sfx"), game.settings().sfx(),
                  v -> game.settings().setSfx(v));
        return page;
    }

    private interface FloatSetter {
        void set(float value);
    }

    private void addSlider(Table page, String label, float value, FloatSetter setter) {
        Slider slider = new Slider(0f, 1f, 0.05f, false, game.skin());
        slider.setValue(value);
        Label readout = new Label(percent(value), game.skin(), "dim");
        slider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                setter.set(slider.getValue());
                readout.setText(percent(slider.getValue()));
            }
        });
        page.add(new Label(label, game.skin())).left().width(58).padBottom(2);
        page.add(slider).width(150).height(11).padRight(4).padBottom(2);
        page.add(readout).width(24).right().padBottom(2).row();
    }

    private static String percent(float v) {
        return Math.round(v * 100) + "%";
    }

    // ---- controls --------------------------------------------------------

    private Actor controlsPage() {
        controlsList = new Table();
        controlsList.top().left();
        refreshControls();

        controlsScroll = new ScrollPane(controlsList, game.skin());
        controlsScroll.setScrollingDisabled(true, false);
        // Drag-to-scroll would steal clicks from the bind buttons, and both
        // smooth scrolling and overscroll leave the content at a fractional
        // offset, which makes every pixel in the list shimmer.
        controlsScroll.setFlickScroll(false);
        controlsScroll.setOverscroll(false, false);
        controlsScroll.setSmoothScrolling(false);
        controlsScroll.setFadeScrollBars(false);
        controlsScroll.setForceScroll(false, true);
        return controlsScroll;
    }

    private void refreshControls() {
        if (controlsList == null) {
            return;
        }
        controlsList.clear();
        for (GameAction action : GameAction.values()) {
            controlsList.add(new Label(game.i18n().get(action.i18nKey), game.skin()))
                        .left().width(96).height(ROW_HEIGHT);
            controlsList.add(bindButton(action, false)).width(34).height(ROW_HEIGHT).padRight(2);
            controlsList.add(bindButton(action, true)).width(34).height(ROW_HEIGHT).row();
        }
    }

    private Button bindButton(GameAction action, boolean secondary) {
        InputMap map = game.input().map();
        int keycode = secondary ? map.secondary(action) : map.primary(action);

        // "bind" is a ButtonStyle, not a TextButtonStyle: Button looks its
        // style up by its own class, and the two live in separate maps.
        Button button = new Button(game.skin(), "bind");
        button.add(KeyPrompts.actor(game.skin(), keycode));
        button.setDisabled(!action.rebindable());
        button.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!action.rebindable()) {
                    return;
                }
                listening = action;
                listeningSecondary = secondary;
                hint.setText(game.i18n().get("settings.press_key"));
            }
        });
        return button;
    }

    // ---- video -----------------------------------------------------------

    private Actor videoPage() {
        Table page = new Table();
        page.top().left();
        I18n t = game.i18n();

        CheckBox fullscreen = new CheckBox(" " + t.get("settings.fullscreen"),
                                           game.skin(), "switch");
        fullscreen.setChecked(game.settings().fullscreen());
        fullscreen.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.settings().setFullscreen(fullscreen.isChecked());
            }
        });
        page.add(fullscreen).left().padBottom(2).row();

        CheckBox vsync = new CheckBox(" " + t.get("settings.vsync"), game.skin(), "switch");
        vsync.setChecked(game.settings().vsync());
        vsync.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.settings().setVsync(vsync.isChecked());
            }
        });
        page.add(vsync).left().padBottom(2).row();

        CheckBox shake = new CheckBox(" " + t.get("settings.screenshake"),
                                      game.skin(), "switch");
        shake.setChecked(game.settings().screenShake());
        shake.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.settings().setScreenShake(shake.isChecked());
            }
        });
        page.add(shake).left().padBottom(3).row();

        // Difficulty cycles rather than offering three buttons: at 320 pixels
        // three labelled choices plus their own label is the whole row, and the
        // language control beneath it already taught the player that a button
        // showing a value changes to the next one.
        Table hard = new Table();
        hard.add(new Label(t.get("settings.difficulty"), game.skin())).left().padRight(4);
        TextButton level = new TextButton(t.get(game.settings().difficulty().i18nKey),
                                          game.skin());
        level.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                Difficulty[] all = Difficulty.values();
                Difficulty next =
                    all[(game.settings().difficulty().ordinal() + 1) % all.length];
                game.settings().setDifficulty(next);
                // Takes effect on the next run, not this one: see RunState.
                level.setText(game.i18n().get(next.i18nKey));
            }
        });
        hard.add(level);
        page.add(hard).left().padBottom(3).row();

        Table lang = new Table();
        lang.add(new Label(t.get("settings.language"), game.skin())).left().padRight(4);
        TextButton toggle = new TextButton(t.language().label, game.skin());
        toggle.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                I18n.Language next = game.i18n().language() == I18n.Language.VI
                    ? I18n.Language.EN : I18n.Language.VI;
                game.i18n().load(next);
                game.settings().setLanguage(next);
                // Text widths change with the language, so the layout is rebuilt
                // rather than each label being reassigned.
                build();
            }
        });
        lang.add(toggle);
        page.add(lang).left().row();
        return page;
    }

    // ---- rebinding -------------------------------------------------------

    private final class RebindCapture extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            if (listening == null) {
                return false;
            }
            if (keycode == Keys.ESCAPE) {
                listening = null;
                hint.setText("");
                return true;
            }
            GameAction stolenFrom =
                game.input().map().bind(listening, keycode, listeningSecondary);
            listening = null;
            if (stolenFrom != null) {
                hint.setText(game.i18n().format("settings.taken_from",
                    game.i18n().get(stolenFrom.i18nKey)));
            } else {
                hint.setText("");
            }
            refreshControls();
            return true;
        }
    }

    @Override
    public void render(float delta) {
        if (controlsScroll != null) {
            // ScrollPane positions its child at a float offset and has no
            // rounding of its own, so snap it or the list shimmers as it moves.
            controlsScroll.setScrollY(MathUtils.round(controlsScroll.getScrollY()));
        }
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
