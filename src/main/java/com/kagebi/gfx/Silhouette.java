package com.kagebi.gfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;

/**
 * A shader that draws a picture's shape in the batch's colour: every pixel the
 * picture has, none of its colours.
 *
 * <p>For outlines. A sprite drawn four times a pixel off in white under this,
 * and once more on top as itself, is ringed in white - which is how the
 * Sunnyside pack marks a good the player has just picked up. A tint cannot do
 * it: a batch colour multiplies, and white times brown is brown.
 */
public final class Silhouette {

    private static final String VERTEX = ""
        + "attribute vec4 " + ShaderProgram.POSITION_ATTRIBUTE + ";\n"
        + "attribute vec4 " + ShaderProgram.COLOR_ATTRIBUTE + ";\n"
        + "attribute vec2 " + ShaderProgram.TEXCOORD_ATTRIBUTE + "0;\n"
        + "uniform mat4 u_projTrans;\n"
        + "varying vec4 v_color;\n"
        + "varying vec2 v_texCoords;\n"
        + "void main() {\n"
        + "    v_color = " + ShaderProgram.COLOR_ATTRIBUTE + ";\n"
        + "    v_color.a = v_color.a * (255.0 / 254.0);\n"
        + "    v_texCoords = " + ShaderProgram.TEXCOORD_ATTRIBUTE + "0;\n"
        + "    gl_Position = u_projTrans * " + ShaderProgram.POSITION_ATTRIBUTE + ";\n"
        + "}\n";

    private static final String FRAGMENT = ""
        + "#ifdef GL_ES\n"
        + "precision mediump float;\n"
        + "#endif\n"
        + "varying vec4 v_color;\n"
        + "varying vec2 v_texCoords;\n"
        + "uniform sampler2D u_texture;\n"
        + "void main() {\n"
        + "    float a = texture2D(u_texture, v_texCoords).a * v_color.a;\n"
        + "    gl_FragColor = vec4(v_color.rgb, a);\n"
        + "}\n";

    /** The SpriteBatch vertex shader, shared by every full-screen and sprite shader here. */
    public static String spriteVertex() {
        return VERTEX;
    }

    /**
     * Compiles the shader, or returns null and says why: an outline is a
     * nicety, and a driver that will not compile it should cost the outline,
     * not the game.
     */
    public static ShaderProgram create() {
        ShaderProgram program = new ShaderProgram(VERTEX, FRAGMENT);
        if (!program.isCompiled()) {
            Gdx.app.error("gfx", "silhouette shader did not compile: " + program.getLog());
            program.dispose();
            return null;
        }
        return program;
    }

    private Silhouette() {}
}
