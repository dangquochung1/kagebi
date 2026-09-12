package com.kagebi.assets;

/**
 * Where an icon sits on the Raven sheet.
 *
 * <p>The sheet is outside every atlas - at 256x2192 it is taller than a texture
 * page - so it is addressed by index rather than by name, and the index is the
 * one thing about it that is easy to get wrong.
 *
 * <p><b>Icons are numbered from one.</b> The pack ships them as
 * {@code fa1.png} through {@code fa2192.png}, there is no {@code fa0.png}, and
 * {@code assets/data/icons.json} was written from those file names - so
 * {@code "heart_red": 659} means the six hundred and fifty-ninth icon, which is
 * at zero-based offset 658.
 *
 * <p>Two screens cut the sheet up and both forgot the subtraction, so every
 * relic, item and upgrade in the game drew its neighbour's picture: vigor, an
 * upgrade named for a red heart, showed the lump of meat next to it. Nothing
 * caught it because every index resolved to a real icon - just the wrong one -
 * and there is no test that can look at a picture and say it is the wrong
 * picture. Putting the rule here means it is stated once and cannot drift.
 */
public final class IconSheet {

    public static final int SIZE = 16;
    public static final int COLUMNS = 16;
    /** 16 columns x 137 rows, which is the whole 256x2192 sheet. */
    public static final int COUNT = 2192;

    /** Whether a one-based index names an icon that is actually on the sheet. */
    public static boolean has(int oneBased) {
        return oneBased >= 1 && oneBased <= COUNT;
    }

    /** Left edge in pixels of a one-based icon index. */
    public static int x(int oneBased) {
        return ((oneBased - 1) % COLUMNS) * SIZE;
    }

    /**
     * Top edge in pixels of a one-based icon index, measured down from the top
     * of the sheet - which is the direction both {@code TextureRegion} and the
     * PNG's own rows run in.
     */
    public static int y(int oneBased) {
        return ((oneBased - 1) / COLUMNS) * SIZE;
    }

    private IconSheet() {}
}
