package com.kagebi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region;

/**
 * Checks the atlases produced by {@code tools/pack_atlas.py} against what
 * libGDX will actually do with them.
 *
 * <p>These parse with libGDX's own reader rather than a hand-rolled one, which
 * is both stronger and cheaper: {@code TextureAtlasData} is a plain public
 * class that creates no {@link Texture}, so it needs no GL context and no
 * headless application - just a {@link FileHandle} wrapping a real file.
 *
 * <p>The duplicate-name assertion is the reason this file exists. Region names
 * go into an ObjectMap, so a collision overwrites silently: 92 monsters would
 * share one sprite and nothing anywhere would report an error.
 */
class AtlasContractTest {

    private static final String[] ATLASES = {"ui", "actors", "npc", "fx"};

    private static TextureAtlasData load(String name) {
        File file = new File("assets/atlas/" + name + ".atlas");
        Assumptions.assumeTrue(file.isFile(),
            "atlas not built - run: python tools/pack_atlas.py");
        FileHandle handle = new FileHandle(file);
        // Throwing here means the emitted text is not valid atlas syntax.
        return new TextureAtlasData(handle, handle.parent(), false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ui", "actors", "npc", "fx"})
    void parsesAndPageImageExists(String name) {
        TextureAtlasData data = load(name);
        assertTrue(data.getRegions().size > 0, name + " has no regions");
        for (Page page : data.getPages()) {
            assertTrue(page.textureFile.exists(),
                name + ": page image missing: " + page.textureFile);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ui", "actors", "npc", "fx"})
    void pagesAreConfiguredForPixelArt(String name) {
        for (Page page : load(name).getPages()) {
            assertEquals(Pixmap.Format.RGBA8888, page.format, name + ": wrong format");
            assertEquals(Texture.TextureFilter.Nearest, page.minFilter,
                name + ": min filter must stay Nearest or the art goes blurry");
            assertEquals(Texture.TextureFilter.Nearest, page.magFilter,
                name + ": mag filter must stay Nearest or the art goes blurry");
            assertEquals(Texture.TextureWrap.ClampToEdge, page.uWrap, name + ": uWrap");
            assertEquals(Texture.TextureWrap.ClampToEdge, page.vWrap, name + ": vWrap");
        }
    }

    /** The one that matters most: a collision here is invisible at runtime. */
    @ParameterizedTest
    @ValueSource(strings = {"ui", "actors", "npc", "fx"})
    void regionNamesAreUnique(String name) {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (Region r : load(name).getRegions()) {
            if (!seen.add(r.name)) {
                duplicates.add(r.name);
            }
        }
        assertTrue(duplicates.isEmpty(), name + ": duplicate region names " + duplicates);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ui", "actors", "npc", "fx"})
    void regionsAreUnrotatedUnindexedAndOnPage(String name) {
        TextureAtlasData data = load(name);
        for (Region r : data.getRegions()) {
            assertFalse(r.rotate, name + ": " + r.name + " is rotated; libGDX does not "
                + "compensate for rotation when a font page is involved");
            assertEquals(0, r.degrees, name + ": " + r.name + " has a rotation angle");
            assertEquals(-1, r.index, name + ": " + r.name + " has an animation index, "
                + "which makes libGDX re-sort every region in the atlas");
            assertTrue(r.left >= 0 && r.top >= 0
                    && r.left + r.width <= r.page.width
                    && r.top + r.height <= r.page.height,
                name + ": " + r.name + " falls outside its page");
        }
    }

    /**
     * Proves the 2px gutter survived packing. Inflating every rectangle by one
     * pixel on each side and finding no overlap means no two sprites are closer
     * than two transparent texels, so a half-pixel sampling error at a region
     * edge can never pick up a neighbour's colour.
     */
    @ParameterizedTest
    @ValueSource(strings = {"ui", "actors", "npc", "fx"})
    void regionsKeepTheirPadding(String name) {
        List<Region> regions = new ArrayList<>();
        Set<String> boundsSeen = new HashSet<>();
        for (Region r : load(name).getRegions()) {
            // Aliases deliberately share a rectangle with their source.
            if (boundsSeen.add(r.page.textureFile + "@" + r.left + "," + r.top
                    + "," + r.width + "," + r.height)) {
                regions.add(r);
            }
        }
        for (int i = 0; i < regions.size(); i++) {
            for (int j = i + 1; j < regions.size(); j++) {
                Region a = regions.get(i);
                Region b = regions.get(j);
                if (a.page != b.page) {
                    continue;
                }
                boolean apart = a.left - 1 >= b.left + b.width + 1
                        || b.left - 1 >= a.left + a.width + 1
                        || a.top - 1 >= b.top + b.height + 1
                        || b.top - 1 >= a.top + a.height + 1;
                assertTrue(apart, name + ": '" + a.name + "' and '" + b.name
                    + "' are packed closer than the 2px gutter");
            }
        }
    }

    /**
     * The skin binds its font by looking for a region named exactly
     * "pixeloid_9". If the packer ever drops that alias the game still runs and
     * still looks right - it just quietly loads a second, untracked texture.
     * Only a test notices.
     */
    @org.junit.jupiter.api.Test
    void uiAtlasCarriesTheFontPage() {
        int count = 0;
        for (Region r : load("ui").getRegions()) {
            if ("pixeloid_9".equals(r.name)) {
                count++;
            }
        }
        assertEquals(1, count, "ui.atlas must contain exactly one 'pixeloid_9' region");
    }

    /** Splits must fit inside the region, or NinePatch throws at construction. */
    @org.junit.jupiter.api.Test
    void ninePatchSplitsAreSane() {
        for (Region r : load("ui").getRegions()) {
            int[] split = r.findValue("split");
            if (split == null) {
                continue;
            }
            assertEquals(4, split.length, r.name + ": split needs four values");
            assertTrue(split[0] + split[1] <= r.width,
                r.name + ": horizontal splits exceed width");
            assertTrue(split[2] + split[3] <= r.height,
                r.name + ": vertical splits exceed height");
        }
    }
}
