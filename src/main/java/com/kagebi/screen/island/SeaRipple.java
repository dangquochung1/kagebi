package com.kagebi.screen.island;

import java.util.Random;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.kagebi.Cfg;
import com.kagebi.gfx.Silhouette;

/**
 * The sea, rippling: the scene's sea layer drawn to a buffer, then drawn again
 * through a shader that pushes each pixel a little sideways and a little up or
 * down by two slowly moving fields of noise.
 *
 * <p>The pack's scene does this with GameMaker's underwater filter on its sea
 * layer, and a flat sea next to the scene's own screenshot looks painted on.
 * The filter's settings in the room are the starting point here - a fine,
 * wide field of distortion three pixels strong and a coarse one six - with the
 * noise made at start-up rather than shipped, since GameMaker's own noise
 * texture is not in the pack.
 *
 * <p>Two rules keep it looking like the rest of the island. <b>The ripples are
 * fixed to the map</b>, not the screen: the noise is sampled at the map point a
 * pixel shows, so walking and zooming move over still water rather than water
 * that follows the camera. And <b>a pixel moves by whole pixels of the view</b>,
 * so the sea breaks up into shifted rows of pixel art rather than smearing.
 *
 * <p>A driver that will not compile the shader, or give a frame buffer, gets
 * the sea drawn plainly; it is said once and not again.
 */
public final class SeaRipple implements Disposable {

    /** Map pixels one feature of each field spans, across and down: long, flat bands. */
    private static final float SCALE_1_X = 20f * 8f;
    private static final float SCALE_1_Y = 2f * 8f;
    private static final float SCALE_2_X = 100f * 8f;
    private static final float SCALE_2_Y = 10f * 8f;
    /** How far each field drifts, in its own repeats a second. */
    private static final float SPEED_1 = 0.1f;
    private static final float SPEED_2 = 0.25f;
    /** Map pixels each field can push a pixel, at most half of this each way. */
    private static final float AMOUNT_1 = 3f;
    private static final float AMOUNT_2 = 6f;
    /** View pixels between the red and the blue of a pixel, the filter's colour fringe. */
    private static final float CHROMA = 1f;

    private static final int NOISE_SIZE = 64;
    private static final int NOISE_CELLS = 8;

    private final int[] layers;
    private final Matrix4 screen = new Matrix4().setToOrtho2D(0, 0, Cfg.VIRT_W, Cfg.VIRT_H);
    private final TextureRegion picture = new TextureRegion();

    private ShaderProgram shader;
    private Texture noise;
    private FrameBuffer buffer;
    private boolean plain;

    /** @param layer the sea layer's index among the map's layers */
    public SeaRipple(int layer) {
        layers = new int[] {layer};
        shader = new ShaderProgram(Silhouette.spriteVertex(), fragment());
        if (!shader.isCompiled()) {
            Gdx.app.error("sea", "ripple shader did not compile; the sea is drawn flat: " + shader.getLog());
            shader.dispose();
            shader = null;
            plain = true;
            return;
        }
        noise = noise(0x5EAL);
    }

    /**
     * Draws the sea for this frame through the world camera. Leaves the batch
     * ended, with no shader, and the viewport bound as it was.
     */
    public void draw(OrthogonalTiledMapRenderer renderer, OrthographicCamera cam, Viewport viewport,
                     SpriteBatch batch, float seconds) {
        if (!plain && !ensureBuffer(viewport.getScreenWidth(), viewport.getScreenHeight())) {
            plain = true;
        }
        renderer.setView(cam);
        if (plain) {
            renderer.render(layers);
            return;
        }
        buffer.begin();
        Gdx.gl.glClearColor(0f, 0f, 0f, 0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        renderer.render(layers);
        buffer.end();
        viewport.apply();

        float viewW = cam.viewportWidth * cam.zoom;
        float viewH = cam.viewportHeight * cam.zoom;
        batch.setProjectionMatrix(screen);
        batch.setShader(shader);
        batch.begin();
        noise.bind(1);
        Gdx.gl.glActiveTexture(GL20.GL_TEXTURE0);
        shader.setUniformi("u_noise", 1);
        shader.setUniformf("u_origin", cam.position.x - viewW / 2f, cam.position.y - viewH / 2f);
        shader.setUniformf("u_view", viewW, viewH);
        shader.setUniformf("u_zoom", cam.zoom);
        shader.setUniformf("u_time", seconds);
        batch.draw(picture, 0f, 0f, Cfg.VIRT_W, Cfg.VIRT_H);
        batch.end();
        batch.setShader(null);
    }

    /** A buffer the size of the view on screen, made again when the window changes size. */
    private boolean ensureBuffer(int width, int height) {
        if (width <= 0 || height <= 0) {
            return false;
        }
        if (buffer != null && buffer.getWidth() == width && buffer.getHeight() == height) {
            return true;
        }
        if (buffer != null) {
            buffer.dispose();
            buffer = null;
        }
        try {
            buffer = new FrameBuffer(Pixmap.Format.RGBA8888, width, height, false);
        } catch (RuntimeException e) {
            Gdx.app.error("sea", "no frame buffer for the ripple; the sea is drawn flat: " + e);
            return false;
        }
        Texture texture = buffer.getColorBufferTexture();
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        picture.setRegion(texture);
        // A buffer's rows run bottom to top, and a region's top to bottom.
        picture.flip(false, true);
        return true;
    }

    private static String fragment() {
        return ""
            + "#ifdef GL_ES\n"
            + "precision mediump float;\n"
            + "#endif\n"
            + "varying vec4 v_color;\n"
            + "varying vec2 v_texCoords;\n"
            + "uniform sampler2D u_texture;\n"
            + "uniform sampler2D u_noise;\n"
            + "uniform vec2 u_origin;\n"
            + "uniform vec2 u_view;\n"
            + "uniform float u_zoom;\n"
            + "uniform float u_time;\n"
            + "void main() {\n"
            + "    vec2 world = u_origin + v_texCoords * u_view;\n"
            + "    vec2 n1 = texture2D(u_noise, world / vec2(" + f(SCALE_1_X) + ", " + f(SCALE_1_Y) + ")"
            + " + vec2(u_time * " + f(SPEED_1) + ", 0.0)).rg - 0.5;\n"
            + "    vec2 n2 = texture2D(u_noise, world / vec2(" + f(SCALE_2_X) + ", " + f(SCALE_2_Y) + ")"
            + " + vec2(0.0, u_time * " + f(SPEED_2) + ")).rg - 0.5;\n"
            + "    vec2 shift = n1 * " + f(AMOUNT_1) + " + n2 * " + f(AMOUNT_2) + ";\n"
            + "    shift = floor(shift / u_zoom + 0.5) * u_zoom;\n"
            + "    vec2 uv = v_texCoords + shift / u_view;\n"
            + "    vec2 spread = vec2(" + f(CHROMA) + " * u_zoom, 0.0) / u_view;\n"
            + "    vec4 mid = texture2D(u_texture, uv);\n"
            // A neighbour off the edge of the sea is empty, and taking its
            // channel would cut the colour out of the sea's last column.
            + "    vec4 right = texture2D(u_texture, uv + spread);\n"
            + "    vec4 left = texture2D(u_texture, uv - spread);\n"
            + "    float r = mix(mid.r, right.r, right.a);\n"
            + "    float b = mix(mid.b, left.b, left.a);\n"
            + "    gl_FragColor = vec4(r, mid.g, b, mid.a) * v_color;\n"
            + "}\n";
    }

    private static String f(float v) {
        return String.format(java.util.Locale.ROOT, "%.4f", v);
    }

    /**
     * Smooth noise that tiles: a grid of random values, eight to a side and
     * wrapping at the edges, eased between. Red and green are independent, one
     * for each direction a pixel is pushed.
     */
    private static Texture noise(long seed) {
        Random random = new Random(seed);
        float[][][] grid = new float[2][NOISE_CELLS][NOISE_CELLS];
        for (float[][] channel : grid) {
            for (float[] row : channel) {
                for (int i = 0; i < row.length; i++) {
                    row[i] = random.nextFloat();
                }
            }
        }
        Pixmap pixmap = new Pixmap(NOISE_SIZE, NOISE_SIZE, Pixmap.Format.RGBA8888);
        int step = NOISE_SIZE / NOISE_CELLS;
        for (int y = 0; y < NOISE_SIZE; y++) {
            for (int x = 0; x < NOISE_SIZE; x++) {
                float r = sample(grid[0], x, y, step);
                float g = sample(grid[1], x, y, step);
                pixmap.drawPixel(x, y, Color.rgba8888(r, g, 0f, 1f));
            }
        }
        Texture texture = new Texture(pixmap);
        texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    private static float sample(float[][] grid, int x, int y, int step) {
        int cells = grid.length;
        int ix = x / step;
        int iy = y / step;
        float fx = smooth((x % step) / (float) step);
        float fy = smooth((y % step) / (float) step);
        float top = MathUtils.lerp(grid[iy][ix], grid[iy][(ix + 1) % cells], fx);
        float bottom = MathUtils.lerp(grid[(iy + 1) % cells][ix], grid[(iy + 1) % cells][(ix + 1) % cells], fx);
        return MathUtils.lerp(top, bottom, fy);
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }

    @Override
    public void dispose() {
        if (buffer != null) {
            buffer.dispose();
        }
        if (shader != null) {
            shader.dispose();
        }
        if (noise != null) {
            noise.dispose();
        }
    }
}
