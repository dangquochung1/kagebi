package com.kagebi.screen.island;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.math.MathUtils;

/**
 * The clouds the island floats in, drawn thin wherever the player is under one.
 *
 * <p>The pack's scene is framed in cloud and has a few puffs drifting over the
 * land besides, drawn over everything - and a village where the player walks
 * under a cloud and vanishes is a village with a hole in it. So cloud tiles
 * near the player fade, cell by cell, and the rest of the cloud stays as the
 * scene drew it.
 *
 * <p>Drawn by hand rather than by the map renderer, which can only give a whole
 * layer one opacity.
 */
public final class CloudLayer {

    /** How much of a cloud is left right over the player. */
    private static final float THINNEST = 0.3f;
    /** The distance, from the player's middle, inside which a cloud is thinnest. */
    private static final float NEAR = 20f;
    /** And how much further out it takes to be whole again. */
    private static final float SPAN = 44f;

    private final TiledMapTileLayer layer;
    private final TextureRegion scratch = new TextureRegion();

    public CloudLayer(TiledMapTileLayer layer) {
        this.layer = layer;
    }

    public void draw(SpriteBatch batch, OrthographicCamera camera, float playerX, float playerY) {
        int tw = layer.getTileWidth();
        int th = layer.getTileHeight();
        float halfW = camera.viewportWidth * camera.zoom / 2f;
        float halfH = camera.viewportHeight * camera.zoom / 2f;
        int x0 = Math.max(0, (int) ((camera.position.x - halfW) / tw) - 1);
        int x1 = Math.min(layer.getWidth() - 1, (int) ((camera.position.x + halfW) / tw) + 1);
        int y0 = Math.max(0, (int) ((camera.position.y - halfH) / th) - 1);
        int y1 = Math.min(layer.getHeight() - 1, (int) ((camera.position.y + halfH) / th) + 1);
        float opacity = layer.getOpacity();
        for (int ty = y0; ty <= y1; ty++) {
            for (int tx = x0; tx <= x1; tx++) {
                TiledMapTileLayer.Cell cell = layer.getCell(tx, ty);
                if (cell == null || cell.getTile() == null) {
                    continue;
                }
                float dx = tx * tw + tw / 2f - playerX;
                float dy = ty * th + th / 2f - playerY;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float whole = MathUtils.clamp((d - NEAR) / SPAN, 0f, 1f);
                batch.setColor(1f, 1f, 1f, opacity * (THINNEST + (1f - THINNEST) * whole));
                drawCell(batch, cell, tx * tw, ty * th, tw, th);
            }
        }
        batch.setColor(Color.WHITE);
    }

    /** A cell the way the map renderer draws one: flipped, then turned about its middle. */
    private void drawCell(SpriteBatch batch, TiledMapTileLayer.Cell cell, float x, float y,
                          int tw, int th) {
        TextureRegion region = cell.getTile().getTextureRegion();
        boolean flipH = cell.getFlipHorizontally();
        boolean flipV = cell.getFlipVertically();
        int rotation = cell.getRotation();
        if (!flipH && !flipV && rotation == TiledMapTileLayer.Cell.ROTATE_0) {
            batch.draw(region, x, y);
            return;
        }
        scratch.setRegion(region);
        scratch.flip(flipH, flipV);
        batch.draw(scratch, x, y, tw / 2f, th / 2f, tw, th, 1f, 1f, rotation * 90f);
    }
}
