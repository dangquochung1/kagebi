package com.kagebi.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/**
 * The one arithmetic fact about the icon sheet, and the evidence for it.
 *
 * <p>Written after every icon in the game was found to be drawing its
 * neighbour's picture. A test cannot look at a rendered frame and say the
 * picture is wrong, so this pins the two things that can be checked: that the
 * index is read as one-based, and that the numbers the content actually uses
 * all land on the sheet.
 */
class IconSheetTest {

    /**
     * The pack ships {@code fa1.png} to {@code fa2192.png} - there is no
     * {@code fa0.png} - and {@code icons.json} was written from those names. So
     * icon 1 is the top-left cell, and reading it as zero-based puts every icon
     * one place to the left of where it belongs.
     */
    @Test
    void theFirstIconIsNumberOneAndSitsInTheTopLeftCorner() {
        assertEquals(0, IconSheet.x(1));
        assertEquals(0, IconSheet.y(1));
        assertEquals(IconSheet.SIZE, IconSheet.x(2), "icon 2 is one cell to the right");
        assertEquals(0, IconSheet.y(2));
        assertEquals(0, IconSheet.x(IconSheet.COLUMNS + 1), "icon 17 starts the second row");
        assertEquals(IconSheet.SIZE, IconSheet.y(IconSheet.COLUMNS + 1));
    }

    /** The last icon must be the last cell, not one past it. */
    @Test
    void theLastIconFillsTheBottomRightCell() {
        int last = IconSheet.COUNT;
        assertTrue(IconSheet.has(last));
        assertFalse(IconSheet.has(last + 1));
        assertFalse(IconSheet.has(0), "there is no icon zero");
        assertEquals((IconSheet.COLUMNS - 1) * IconSheet.SIZE, IconSheet.x(last));
        assertEquals(IconSheet.COUNT / IconSheet.COLUMNS * IconSheet.SIZE - IconSheet.SIZE,
                     IconSheet.y(last));
    }

    /**
     * Every index the content names has to be on the sheet. An index past the
     * end draws nothing at all, which is at least visible; one just inside it
     * draws the wrong picture, which is not.
     */
    @Test
    void everyIconTheContentNamesIsOnTheSheet() {
        File file = new File("assets/data/icons.json");
        assertTrue(file.isFile(), "assets/data/icons.json is missing");
        JsonValue icons = new JsonReader().parse(new FileHandle(file)).get("icons");
        assertTrue(icons != null && icons.child != null, "no icons section");
        int count = 0;
        for (JsonValue e = icons.child; e != null; e = e.next) {
            int index = e.asInt();
            assertTrue(IconSheet.has(index),
                "icon '" + e.name + "' is " + index + ", which is not on the sheet");
            count++;
        }
        assertTrue(count > 50, "suspiciously few named icons: " + count);
    }

    /**
     * The grid has to match the PNG it describes. Read out of the file header
     * rather than trusted, because a re-exported sheet would move every icon
     * and nothing else here would notice.
     */
    @Test
    void theGridMatchesTheSheetOnDisk() {
        File png = new File("assets/gfx/icons/raven_icons_16.png");
        assertTrue(png.isFile(), "the icon sheet is missing");
        int[] size = pngSize(png);
        assertEquals(IconSheet.COLUMNS * IconSheet.SIZE, size[0], "sheet width");
        assertEquals(IconSheet.COUNT / IconSheet.COLUMNS * IconSheet.SIZE, size[1],
                     "sheet height - COUNT and the PNG disagree");
    }

    /** Width and height out of a PNG's IHDR, which is always the first chunk. */
    private static int[] pngSize(File file) {
        byte[] head = new FileHandle(file).readBytes();
        assertTrue(head.length > 24, "not a PNG");
        return new int[] {intAt(head, 16), intAt(head, 20)};
    }

    private static int intAt(byte[] b, int at) {
        return ((b[at] & 0xff) << 24) | ((b[at + 1] & 0xff) << 16)
             | ((b[at + 2] & 0xff) << 8) | (b[at + 3] & 0xff);
    }
}
