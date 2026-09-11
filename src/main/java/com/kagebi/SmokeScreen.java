package com.kagebi;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kagebi.gfx.PixelViewport;

/**
 * Milestone 1 smoke test: draws one frame that exercises every risky part of the
 * pipeline at once, so a break shows up here rather than three milestones later.
 *
 * <p>It checks that the 320x180 world scales by whole numbers, that the tileset
 * and player sprites survived the asset rebuild, that the Theme Wood nine-patch
 * splits are right, and that the synthesised Vietnamese glyphs render.
 */
public class SmokeScreen extends ScreenAdapter {

    /**
     * Measured off the actual pixels of nine_path_panel.png, not guessed: the
     * panel has a light bevel on the left and a dark one on the right, so the
     * splits are not symmetric.
     */
    private static final int PANEL_L = 6, PANEL_R = 5, PANEL_T = 6, PANEL_B = 5;

    private static final String[] LINES = {
        "KAGEBI - Ngọn Lửa Thiêng",
        "Bắt đầu mới    Tiếp tục",
        "Cài đặt        Điều khiển",
        "Chiến thắng! Thất bại?",
    };

    private final Kagebi game;
    private final OrthographicCamera worldCam = new OrthographicCamera();
    private final PixelViewport world = new PixelViewport(Cfg.VIRT_W, Cfg.VIRT_H, worldCam);
    private final ScreenViewport ui = new ScreenViewport();

    private Texture tileset;
    private Texture playerSheet;
    private Texture panelTex;
    private TextureRegion playerIdle;
    private NinePatch panel;
    private BitmapFont font;
    private int panelScale;

    public SmokeScreen(Kagebi game) {
        this.game = game;
    }

    @Override
    public void show() {
        tileset = pixelTexture("assets/gfx/tiles/depths/dungeon_tileset.png");
        playerSheet = pixelTexture("assets/gfx/actors/player/ninjagreen/idle.png");
        panelTex = pixelTexture("assets/gfx/ui/theme/theme_wood/nine_path_panel.png");

        // idle.png is 4 directions x 4 frames of 32x32; take the first
        // down-facing frame.
        playerIdle = new TextureRegion(playerSheet, 0, 0, 32, 32);

        font = new BitmapFont(Gdx.files.internal("assets/fonts/pixeloid_9.fnt"), false);
        font.getRegion().getTexture()
            .setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        font.setUseIntegerPositions(true);
    }

    /**
     * A NinePatch keeps its border widths in pixels and does not scale them
     * with the drawn size, so at 4x the frame would stay a hairline around a
     * huge panel. Rebuild it whenever the window's integer scale changes.
     */
    private void panelAtScale(int scale) {
        if (panel != null && panelScale == scale) {
            return;
        }
        panel = new NinePatch(panelTex, PANEL_L, PANEL_R, PANEL_T, PANEL_B);
        panel.scale(scale, scale);
        panelScale = scale;
    }

    private static Texture pixelTexture(String path) {
        Texture t = new Texture(Gdx.files.internal(path));
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return t;
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.09f, 0.07f, 0.11f, 1f);
        SpriteBatch batch = game.batch();

        // --- world, at 320x180 -------------------------------------------
        world.apply();
        batch.setProjectionMatrix(worldCam.combined);
        batch.begin();
        for (int y = 0; y < Cfg.VIRT_H; y += tileset.getHeight()) {
            for (int x = 0; x < Cfg.VIRT_W; x += tileset.getWidth()) {
                batch.draw(tileset, x, y);
            }
        }
        batch.draw(playerIdle, 24, Cfg.VIRT_H - 56);
        batch.end();

        // --- ui, at window resolution, scaled by the same whole number ----
        int s = world.scale();
        panelAtScale(s);
        ui.apply();
        batch.setProjectionMatrix(ui.getCamera().combined);
        batch.begin();
        font.getData().setScale(s);
        float lineH = font.getLineHeight();
        float padding = 6f * s;
        float boxW = 150f * s;
        float boxH = lineH * LINES.length + padding * 2;
        float boxX = (Gdx.graphics.getWidth() - boxW) / 2f;
        float boxY = (Gdx.graphics.getHeight() - boxH) / 2f;

        panel.draw(batch, boxX, boxY, boxW, boxH);
        font.setColor(Color.WHITE);
        float textY = boxY + boxH - padding;
        for (String line : LINES) {
            font.draw(batch, line, boxX + padding, textY);
            textY -= lineH;
        }
        font.getData().setScale(1f);
        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        world.update(width, height, true);
        ui.update(width, height, true);
    }

    @Override
    public void dispose() {
        tileset.dispose();
        playerSheet.dispose();
        panelTex.dispose();
        font.dispose();
    }
}
