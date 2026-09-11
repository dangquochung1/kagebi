package com.kagebi.screen;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.kagebi.Cfg;
import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;
import com.kagebi.gfx.PixelViewport;

/**
 * A developer-only sheet showing every widget the skin defines.
 *
 * <p>Colour and spacing are the parts of this project no test can judge, so
 * there has to be one place that puts every state side by side: a hover colour
 * that is unreadable, or a disabled label that vanishes, is obvious here and
 * invisible when the widget is alone on a screen.
 *
 * <p>Reached with {@code --screen style}; it is never pushed during play.
 */
public class StyleSheetScreen extends GameScreen {

    private final Kagebi game;
    private final int page;
    private Stage stage;

    public StyleSheetScreen(Kagebi game, int page) {
        this.game = game;
        this.page = page;
    }

    @Override
    public InputProcessor inputProcessor() {
        return stage;
    }

    @Override
    public void show() {
        if (stage != null) {
            return;
        }
        stage = new Stage(
            new PixelViewport(Cfg.VIRT_W, Cfg.VIRT_H, new OrthographicCamera()),
            game.batch());
        Table outer = new Table();
        outer.setFillParent(true);
        outer.pad(3);

        // Everything sits on a panel, because that is where it sits in the real
        // game: the default label colours are dark, tuned for the light sage
        // interior. Laying them straight onto a dark background made half of
        // this sheet invisible, which was a fault in the sheet rather than in
        // the skin - but a useful reminder that a label on a dark surface needs
        // the "inverse" style.
        Table root = new Table();
        root.setBackground(game.skin().getDrawable(Assets.Ui.PANEL_2));
        root.top().left();

        if (page == 2) {
            buildSurfaces(root);
        } else {
            buildWidgets(root);
        }
        outer.add(root).grow();
        stage.addActor(outer);
    }

    private Label caption(String text) {
        return new Label(text, game.skin(), "dim");
    }

    private void buildWidgets(Table root) {
        root.add(new Label("WIDGETS — trạng thái", game.skin(), "title"))
            .colspan(4).left().padBottom(3).row();

        // Buttons, every state at once. `over` and `down` cannot be produced by
        // hovering in a screenshot, so they are forced here.
        Table buttons = new Table();
        buttons.add(new TextButton("Bình thường", game.skin())).padRight(2);
        TextButton over = new TextButton("Di chuột", game.skin());
        over.getClickListener().setButton(-1);
        buttons.add(over).padRight(2);
        TextButton disabled = new TextButton("Đã khoá", game.skin());
        disabled.setDisabled(true);
        buttons.add(disabled);
        root.add(buttons).colspan(4).left().padBottom(3).row();

        Table tabs = new Table();
        ButtonGroup<TextButton> group = new ButtonGroup<>();
        group.setMinCheckCount(1);
        group.setMaxCheckCount(1);
        for (String name : new String[] {"Đang chọn", "Chưa chọn", "Khoá"}) {
            TextButton tab = new TextButton(name, game.skin(), "tab");
            group.add(tab);
            tabs.add(tab).padRight(1);
        }
        group.getButtons().get(2).setDisabled(true);
        root.add(tabs).colspan(4).left().padBottom(4).row();

        root.add(caption("Slider")).left().width(42);
        root.add(slider(0f)).width(52).padRight(2);
        root.add(slider(0.45f)).width(52).padRight(2);
        root.add(slider(1f)).width(52).row();

        root.add(caption("Progress")).left().padTop(2);
        ProgressBar bar = new ProgressBar(0f, 1f, 0.01f, false, game.skin());
        bar.setValue(0.62f);
        root.add(bar).width(52).padTop(2).colspan(3).left().row();

        Table toggles = new Table();
        CheckBox check = new CheckBox(" Check", game.skin());
        check.setChecked(true);
        CheckBox radio = new CheckBox(" Radio", game.skin(), "radio");
        radio.setChecked(true);
        CheckBox sw = new CheckBox(" Switch", game.skin(), "switch");
        toggles.add(check).padRight(4);
        toggles.add(radio).padRight(4);
        toggles.add(sw);
        root.add(toggles).colspan(4).left().padTop(4).row();

        Table labels = new Table();
        labels.add(new Label("default", game.skin())).padRight(4);
        labels.add(new Label("dim", game.skin(), "dim")).padRight(4);
        labels.add(new Label("danger", game.skin(), "danger")).padRight(4);
        labels.add(new Label("title", game.skin(), "title")).padRight(4);
        labels.add(new Label("boxed", game.skin(), "boxed"));
        root.add(labels).colspan(4).left().padTop(3).row();

        // The dark-surface case, which the light-background styles cannot serve.
        Table onDark = new Table();
        onDark.setBackground(game.skin().getDrawable("panel_depths"));
        onDark.add(new Label("inverse — trên nền tối", game.skin(), "inverse")).pad(1);
        root.add(onDark).colspan(4).left().padTop(2).row();
    }

    private Slider slider(float value) {
        Slider s = new Slider(0f, 1f, 0.01f, false, game.skin());
        s.setValue(value);
        return s;
    }

    private void buildSurfaces(Table root) {
        root.add(new Label("SURFACES — nền & khung", game.skin(), "title"))
            .colspan(5).left().padBottom(3).row();

        String[] names = {Assets.Ui.PANEL, Assets.Ui.PANEL_2, Assets.Ui.PANEL_INTERIOR,
                          Assets.Ui.BG, Assets.Ui.CELL};
        String[] captions = {"panel", "panel_2", "interior", "bg", "cell"};
        for (int i = 0; i < names.length; i++) {
            Table swatch = new Table();
            swatch.setBackground(game.skin().getDrawable(names[i]));
            root.add(swatch).size(52, 30).padRight(3);
        }
        root.row();
        for (String c : captions) {
            root.add(caption(c)).padRight(3).padBottom(4);
        }
        root.row();

        // Tinted fills, which is what every solid colour in the UI is made of.
        String[] tints = {"fill_accent", "fill_selected", "fill_over", "divider",
                          "panel_depths"};
        for (String tint : tints) {
            Drawable d = game.skin().getDrawable(tint);
            Table swatch = new Table();
            swatch.setBackground(d);
            root.add(swatch).size(52, 22).padRight(3);
        }
        root.row();
        for (String tint : tints) {
            root.add(caption(tint.replace("fill_", ""))).padRight(3).padBottom(4);
        }
        root.row();

        Table sample = new Table();
        sample.setBackground(game.skin().getDrawable("panel_depths"));
        sample.add(new Label("Vực Nguyền Rủa", game.skin(), "inverse"));
        root.add(sample).colspan(3).left().padTop(2);

        Image cursor = new Image(game.skin().getDrawable(Assets.Ui.CURSOR));
        root.add(cursor).padTop(2);
        root.row();
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(new Color(0x141020ff));
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
