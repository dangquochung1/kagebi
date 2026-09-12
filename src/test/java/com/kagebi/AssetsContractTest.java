package com.kagebi;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region;
import com.kagebi.assets.Assets;

/**
 * Asserts that everything named in {@link Assets} actually exists.
 *
 * <p>The point of funnelling every path through one class is that it can be
 * checked in one place, and this is that check. The source packs are full of
 * near-miss names - {@code Slime.png} beside {@code mushroom.png} beside
 * {@code SpriteSheet.png} - and a mistyped one fails at runtime, three screens
 * in, with a stack trace pointing at the loader.
 *
 * <p>Reflection rather than a written-out list, so a constant added next month
 * is covered without anyone remembering to add it here.
 */
class AssetsContractTest {

    private static TextureAtlasData atlas(String name) {
        File file = new File("assets/atlas/" + name + ".atlas");
        Assumptions.assumeTrue(file.isFile(),
            "atlas not built - run: python tools/pack_atlas.py");
        FileHandle handle = new FileHandle(file);
        return new TextureAtlasData(handle, handle.parent(), false);
    }

    private static Set<String> regionNames(String atlasName) {
        Set<String> names = new HashSet<>();
        for (Region r : atlas(atlasName).getRegions()) {
            names.add(r.name);
        }
        return names;
    }

    private static List<String> constants(Class<?> type, String... skip) {
        Set<String> skipped = new HashSet<>();
        for (String s : skip) {
            skipped.add(s);
        }
        List<String> out = new ArrayList<>();
        for (Field f : type.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != String.class
                    || skipped.contains(f.getName())) {
                continue;
            }
            try {
                f.setAccessible(true);
                out.add((String) f.get(null));
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
        return out;
    }

    @Test
    void everyUiRegionExists() {
        Set<String> names = regionNames("ui");
        List<String> missing = new ArrayList<>();
        for (String region : constants(Assets.Ui.class)) {
            if (!names.contains(region)) {
                missing.add(region);
            }
        }
        assertTrue(missing.isEmpty(), "missing from ui.atlas: " + missing);
    }

    @Test
    void everyActorRegionExists() {
        Set<String> names = regionNames("actors");
        List<String> missing = new ArrayList<>();
        // DEFAULT_CHARACTER is an id, not a region; the characters it names are
        // covered by everyCharacterHasEveryAnimation below.
        for (String region : constants(Assets.Actor.class, "DEFAULT_CHARACTER")) {
            if (!names.contains(region)) {
                missing.add(region);
            }
        }
        assertTrue(missing.isEmpty(), "missing from actors.atlas: " + missing);
    }

    /**
     * Character select offers six ninjas, and five of them are generated. A
     * recolour that silently skipped a file would show up as one character
     * unable to attack - which is exactly the sort of thing nobody tries until
     * after release.
     */
    @Test
    void everyCharacterHasEveryAnimation() {
        Set<String> names = regionNames("actors");
        List<String> missing = new ArrayList<>();
        for (String character : Assets.Actor.CHARACTERS) {
            for (String animation : Assets.Actor.PLAYER_ANIMS) {
                String region = Assets.Actor.player(character, animation);
                if (!names.contains(region)) {
                    missing.add(region);
                }
            }
        }
        assertTrue(missing.isEmpty(), "missing player animations: " + missing);
    }

    @Test
    void everyPathConstantPointsAtSomethingReal() {
        List<String> missing = new ArrayList<>();
        List<String> paths = new ArrayList<>(constants(Assets.class));
        // Sounds are nested rather than top level, and a missing one is silent
        // in the worst way: the hit still lands, it just stops making a noise.
        paths.addAll(constants(Assets.Sfx.class));
        for (String path : paths) {
            if (!path.startsWith("assets/")) {
                continue;
            }
            File f = new File(path);
            boolean directory = path.endsWith("/");
            if (directory ? !f.isDirectory() : !f.isFile()) {
                // The derived trees are gitignored and rebuilt by the tools, so
                // skip rather than fail when they have not been built yet.
                if (path.startsWith("assets/gfx/") || path.startsWith("assets/audio/")
                        || path.startsWith("assets/atlas/")) {
                    Assumptions.assumeTrue(false,
                        "asset tree not built - run: python tools/build_assets.py");
                }
                missing.add(path);
            }
        }
        assertTrue(missing.isEmpty(), "Assets names files that do not exist: " + missing);
    }
}
