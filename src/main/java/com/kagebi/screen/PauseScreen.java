package com.kagebi.screen;

import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.kagebi.Cfg;
import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;
import com.kagebi.gfx.PixelViewport;
import com.kagebi.input.GameAction;
import com.kagebi.ui.I18n;

/**
 * The pause menu, over a still-visible game.
 *
 * <p>{@link #isOpaque()} is false, and that one line is the reason
 * {@link ScreenStack} exists. A pause menu that replaces the dungeon with a
 * black rectangle feels like a different program; one that dims the room the
 * player was just standing in feels like a breath. The dungeon keeps drawing
 * underneath but stops simulating, because {@code updatesWhenCovered} is false.
 *
 * <p>Abandoning takes two presses. It ends the run and cannot be undone, and
 * the button sits one key-press below Settings.
 */
public class PauseScreen extends SimScreen {

    private final Kagebi game;
    /** False in the village, where there is no run to abandon. */
    private final boolean inRun;

    private Stage stage;
    private InputMultiplexer inputs;
    private MenuColumn menu;
    private boolean confirmingAbandon;
    private TextButton abandon;

    public PauseScreen(Kagebi game, boolean inRun) {
        super(game.input());
        this.game = game;
        this.inRun = inRun;
    }

    @Override
    public boolean isOpaque() {
        return false;
    }

    @Override
    public InputProcessor inputProcessor() {
        return inputs;
    }

    @Override
    public void show() {
        game.input().clear();
        if (stage != null) {
            build();        // language may have changed in settings
            return;
        }
        stage = new Stage(new PixelViewport(Cfg.VIRT_W, Cfg.VIRT_H, new OrthographicCamera()),
                          game.batch());
        // Actions first, so movement and INTERACT drive the menu; the stage
        // still gets the mouse, and every key the input map does not claim.
        inputs = new InputMultiplexer(game.input(), stage);
        build();
    }

    private void build() {
        stage.clear();
        I18n t = game.i18n();
        confirmingAbandon = false;

        Table root = new Table();
        root.setFillParent(true);
        root.setBackground(game.skin().getDrawable("scrim"));

        Table panel = new Table();
        panel.setBackground(game.skin().getDrawable(Assets.Ui.PANEL_2));
        panel.add(new Label(t.get("pause.title"), game.skin(), "title")).padBottom(5).row();

        menu = new MenuColumn(game.skin(), game.audio());
        menu.add(t.get("pause.resume"), this::resume);
        menu.add(t.get("menu.settings"), () -> stack().push(new SettingsScreen(game)));
        if (inRun) {
            abandon = menu.add(t.get("pause.abandon"), this::abandon);
        } else {
            menu.add(t.get("pause.to_menu"), () -> stack().set(new MainMenuScreen(game)));
        }
        panel.add(menu.table(112)).row();

        root.add(panel).width(140);
        stage.addActor(root);
    }

    private void resume() {
        stack().pop();
    }

    private void abandon() {
        if (!confirmingAbandon) {
            confirmingAbandon = true;
            abandon.setText(game.i18n().get("pause.confirm"));
            return;
        }
        // Abandoning is a failed descent, and is scored as one: the gold is
        // banked exactly as if the player had died.
        ScreenStack s = stack();
        s.pop();
        s.push(new GameOverScreen(game));
    }

    @Override
    protected void step() {
        if (input().justPressed(GameAction.PAUSE)) {
            game.audio().playSfx(Assets.SFX_CANCEL);
            resume();
            return;
        }
        menu.step(input());
    }

    @Override
    public void render(float delta) {
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
