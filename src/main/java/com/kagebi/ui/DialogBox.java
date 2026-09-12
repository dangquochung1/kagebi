package com.kagebi.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.kagebi.assets.Assets;

/**
 * What a villager says, one page at a time.
 *
 * <p>Built on {@code ui/dialog/dialogueboxsimple} rather than on the pack's
 * handsomer {@code dialogbox}, and the reason is Vietnamese. That box carries a
 * baked-in name tab nine pixels tall, which is room for a capital and nothing
 * else; {@code Trưởng} and {@code Sư} stack a tone mark four rows above cap
 * height and would sit on the tab's own border. So the speaker's name goes on
 * the first line <em>inside</em> the box, where there is a full 14-pixel line
 * to put it in, and the portrait sits in its own frame above.
 *
 * <p>Each page is wrapped to the box here, not broken by hand in the language
 * files. Vietnamese runs longer than English for the same sentence, so a break
 * placed for one is wrong for the other, and a translator should never have to
 * count pixels. A page is written to wrap to two lines; anything longer is a
 * second page, which is also how the player is given control of the pace.
 */
public final class DialogBox {

    /** Outer box, sitting against the bottom of the 320x180 screen. */
    private static final int BOX_X = 2;
    private static final int BOX_Y = 4;
    private static final int BOX_W = 316;
    private static final int BOX_H = 60;

    /** Measured interior of the box art, from its bottom-left corner. */
    private static final int PAD_X = 6;
    private static final int PAD_TOP = 6;

    private static final int TEXT_X = BOX_X + PAD_X + 4;
    private static final int TEXT_TOP = BOX_Y + BOX_H - PAD_TOP - 3;
    /** Interior less the text inset on both sides and room for the arrow. */
    private static final int TEXT_WIDTH = BOX_W - 2 * (PAD_X + 4) - 12;

    private static final int FACE = 38;
    private static final int FRAME = 48;

    /** Steps in one blink of the "there is more" arrow. */
    private static final int BLINK_STEPS = 40;

    private static final Color INK = new Color(0x131b1bff);
    private static final Color TEXT = new Color(0x46503cff);

    private final BitmapFont font;
    private final TextureRegion box;
    private final TextureRegion frame;
    private final TextureRegion arrow;

    private final Array<String> pages = new Array<>();
    private String speaker;
    private TextureRegion face;
    private int page;
    private int steps;

    public DialogBox(Skin skin) {
        this.font = skin.getFont("default");
        this.box = skin.getRegion(Assets.Ui.DIALOG_SIMPLE);
        this.frame = skin.getRegion(Assets.Ui.FACESET_FRAME);
        this.arrow = skin.getRegion(Assets.Ui.ARROW_RIGHT);
    }

    public boolean open() {
        return pages.size > 0;
    }

    /** Starts a conversation. {@code lines} are pages, already translated. */
    public void show(String speaker, TextureRegion face, String... lines) {
        this.speaker = speaker;
        this.face = face;
        pages.clear();
        for (String line : lines) {
            pages.add(line);
        }
        page = 0;
        steps = 0;
    }

    public void close() {
        pages.clear();
        page = 0;
    }

    /** Advances a page, and reports whether the conversation is now over. */
    public boolean advance() {
        if (++page >= pages.size) {
            close();
            return true;
        }
        return false;
    }

    public void step() {
        steps++;
    }

    public void draw(SpriteBatch batch) {
        if (!open()) {
            return;
        }
        batch.setColor(Color.WHITE);
        batch.draw(box, BOX_X, BOX_Y, BOX_W, BOX_H);

        if (face != null) {
            int fx = BOX_X + 4;
            int fy = BOX_Y + BOX_H - 2;
            batch.draw(frame, fx, fy, FRAME, FRAME);
            batch.draw(face, fx + (FRAME - FACE) / 2, fy + (FRAME - FACE) / 2, FACE, FACE);
        }

        int top = TEXT_TOP;
        if (speaker != null) {
            batch.setColor(INK);
            Hud.line(batch, font, speaker, TEXT_X, top);
            top -= Hud.LINE;
        }
        // The same ascent and colour handling as Hud.line, applied to a
        // wrapped block; the font steps each following line down by its own
        // line height.
        font.setColor(TEXT);
        font.draw(batch, pages.get(page), TEXT_X, top - font.getAscent(),
                  TEXT_WIDTH, Align.left, true);
        font.setColor(Color.WHITE);
        batch.setColor(Color.WHITE);

        // The arrow blinks rather than sitting still, because a dialogue box
        // with nothing moving in it reads as a game that has stopped.
        if (steps % BLINK_STEPS < BLINK_STEPS / 2) {
            batch.draw(arrow, BOX_X + BOX_W - PAD_X - 18, BOX_Y + PAD_TOP);
        }
    }
}
