package com.kagebi;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.SkinLoader;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.ScreenUtils;
import com.kagebi.assets.Assets;
import com.kagebi.gfx.PixelViewport;

/**
 * Milestone 2 smoke test: builds a scene2d screen out of the generated skin so
 * that anything wrong with the atlas, the nine-patch splits, the font binding
 * or the pixel grid shows up in one screenshot.
 *
 * <p>Everything - world and UI alike - lives in a single 320x180
 * {@link PixelViewport}. The earlier dual-viewport approach worked but meant
 * rescaling the font and rebuilding every nine-patch on each resize; here the
 * font is always drawn at scale 1 and a nine-patch is never touched.
 */
public class SmokeScreen extends ScreenAdapter {

    private final Kagebi game;
    private final AssetManager assets = new AssetManager();
    private Stage stage;
    private Skin skin;

    public SmokeScreen(Kagebi game) {
        this.game = game;
    }

    @Override
    public void show() {
        // SkinLoader guesses the atlas sits next to the skin file; ours does
        // not, so the path has to be given explicitly or nothing loads.
        assets.load(Assets.ATLAS_UI, TextureAtlas.class);
        assets.load(Assets.SKIN, Skin.class, new SkinLoader.SkinParameter(Assets.ATLAS_UI));
        assets.finishLoading();
        skin = assets.get(Assets.SKIN, Skin.class);

        stage = new Stage(new PixelViewport(Cfg.VIRT_W, Cfg.VIRT_H, new OrthographicCamera()),
                          game.batch());
        Gdx.input.setInputProcessor(stage);
        stage.addActor(buildRoot());
    }

    private Table buildRoot() {
        Table root = new Table();
        root.setFillParent(true);

        Table panel = new Table();
        // panel_2's interior is sage green rather than the orange of panel_1.
        // Dark text on orange was legible but loud, and the slider fill had
        // almost no contrast against it.
        panel.setBackground(skin.getDrawable(Assets.Ui.PANEL_2));
        panel.top();
        panel.pad(8, 9, 7, 8);

        panel.add(new Label("KAGEBI — Ngọn Lửa Thiêng", skin, "title"))
             .colspan(3).padBottom(4).row();

        // Tabs: scene2d has no tab widget, so a ButtonGroup of toggle buttons
        // makes "exactly one selected" a structural guarantee rather than
        // something to police by hand.
        Table tabs = new Table();
        ButtonGroup<TextButton> group = new ButtonGroup<>();
        group.setMinCheckCount(1);
        group.setMaxCheckCount(1);
        for (String name : new String[] {"Âm thanh", "Điều khiển", "Hiển thị"}) {
            TextButton tab = new TextButton(name, skin, "tab");
            // No forced height here either: the tab art is 16x12 but a line of
            // text needs 14, and clamping to the art's height clipped it.
            tab.getLabelCell().pad(0, 3, 1, 3);
            group.add(tab);
            tabs.add(tab).padRight(1);
        }
        panel.add(tabs).colspan(3).left().padBottom(4).row();

        addSlider(panel, "Nhạc nền", 0.8f);
        addSlider(panel, "Hiệu ứng", 1.0f);
        addSlider(panel, "Môi trường", 0.4f);

        panel.add(new CheckBox(" Toàn màn hình", skin, "switch"))
             .colspan(3).left().padTop(3).row();
        panel.add(new CheckBox(" Rung màn hình", skin, "switch"))
             .colspan(3).left().padTop(1).row();

        Table footer = new Table();
        // No forced height: the button's preferred height already accounts for
        // the 14px line box, and clamping it to 10 clipped the descenders.
        footer.add(new TextButton("Bắt đầu", skin)).padRight(3);
        footer.add(new TextButton("Quay lại", skin)).padRight(3);
        TextButton disabled = new TextButton("Đã khoá", skin);
        disabled.setDisabled(true);
        footer.add(disabled);
        panel.add(footer).colspan(3).padTop(6).row();

        // Height follows the content: a fixed height left a third of the
        // panel empty below the buttons.
        root.add(panel).width(300);
        return root;
    }

    private void addSlider(Table panel, String label, float value) {
        Slider slider = new Slider(0f, 1f, 0.05f, false, skin);
        slider.setValue(value);
        Label readout = new Label(Math.round(value * 100) + "%", skin, "dim");
        slider.addListener(event -> {
            readout.setText(Math.round(slider.getValue() * 100) + "%");
            return false;
        });
        panel.add(new Label(label, skin)).left().width(64).padBottom(2);
        panel.add(slider).width(146).height(11).padRight(4).padBottom(2);
        panel.add(readout).width(24).right().padBottom(2).row();
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.09f, 0.07f, 0.11f, 1f);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        stage.dispose();
        assets.dispose();
    }
}
