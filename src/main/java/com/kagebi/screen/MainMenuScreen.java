package com.kagebi.screen;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.OrthographicCamera;
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
import com.kagebi.ui.I18n;

/**
 * The title screen.
 *
 * <p>Everything lives in one 320x180 {@link PixelViewport} shared with the
 * world, so the font is always drawn at scale 1 and nine-patches are never
 * rescaled. The language button rebuilds the whole layout rather than setting
 * each label, because widths change when the text does.
 */
public class MainMenuScreen extends GameScreen {

    private final Kagebi game;
    private Stage stage;

    public MainMenuScreen(Kagebi game) {
        this.game = game;
    }

    @Override
    public InputProcessor inputProcessor() {
        return stage;
    }

    @Override
    public void show() {
        if (stage == null) {
            stage = new Stage(
                new PixelViewport(Cfg.VIRT_W, Cfg.VIRT_H, new OrthographicCamera()),
                game.batch());
            rebuild();
            game.audio().playMusic(Assets.MUSIC_INTRO);
        }
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
        panel.add(new Label(t.get("game.title"), game.skin(), "dim")).padBottom(6).row();

        addMenuButton(panel, t.get("menu.continue"), true);
        addMenuButton(panel, t.get("menu.newgame"), false);
        addMenuButton(panel, t.get("menu.settings"), false);
        addMenuButton(panel, t.get("menu.credits"), false);
        addMenuButton(panel, t.get("menu.quit"), false);

        // Switching language here rather than burying it in settings makes it
        // reachable before the player can read the settings label.
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
        panel.add(language).padTop(6).row();

        root.add(panel).width(180);
        stage.addActor(root);
    }

    private void addMenuButton(Table panel, String text, boolean disabled) {
        TextButton button = new TextButton(text, game.skin());
        button.setDisabled(disabled);
        panel.add(button).width(120).padBottom(2).row();
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
