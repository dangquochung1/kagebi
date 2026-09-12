package com.kagebi.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region;
import com.kagebi.assets.Assets;

/**
 * Measures the sheets {@link ActorSprites} relies on, without a GL context.
 *
 * <p>The existing contract tests check that named regions exist. These check
 * the <em>shapes</em> the slicing code assumes, which is where a wrong guess
 * becomes an invisible or scrambled enemy rather than a crash.
 */
class ActorRegionsTest {

    private static Map<String, int[]> sizes(String atlas) {
        File file = new File("assets/atlas/" + atlas + ".atlas");
        Assumptions.assumeTrue(file.isFile(), "atlas not built - run: python tools/pack_atlas.py");
        FileHandle handle = new FileHandle(file);
        Map<String, int[]> out = new HashMap<>();
        for (Region r : new TextureAtlasData(handle, handle.parent(), false).getRegions()) {
            out.put(r.name, new int[] {r.width, r.height});
        }
        return out;
    }

    @Test
    void everyFxRegionExists() throws IllegalAccessException {
        Map<String, int[]> fx = sizes("fx");
        List<String> missing = new ArrayList<>();
        for (Field f : Assets.Fx.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                String region = (String) f.get(null);
                if (!fx.containsKey(region)) {
                    missing.add(region);
                }
            }
        }
        assertTrue(missing.isEmpty(), "missing from fx.atlas: " + missing);
    }

    @Test
    void theOrbIsAStripOfSquareFrames() {
        int[] orb = sizes("fx").get(Assets.Fx.PROJECTILE_ORB);
        assertEquals(0, orb[0] % orb[1], "Anim.strip slices by height; width must divide");
    }

    @Test
    void allSixtySixMonsterSheetsAreFourByFourAtSixteen() {
        int count = 0;
        List<String> odd = new ArrayList<>();
        for (Map.Entry<String, int[]> e : sizes("actors").entrySet()) {
            if (e.getKey().startsWith("monsters/") && e.getKey().endsWith("/spritesheet")) {
                count++;
                if (e.getValue()[0] != 64 || e.getValue()[1] != 64) {
                    odd.add(e.getKey());
                }
            }
        }
        assertEquals(66, count);
        assertTrue(odd.isEmpty(), "not 64x64: " + odd);
    }

    @Test
    void everyDepthsSetHasAllFourSiblings() {
        Map<String, int[]> actors = sizes("actors");
        List<String> missing = new ArrayList<>();
        int sets = 0;
        for (String name : actors.keySet()) {
            if (!Assets.Actor.isDepthsSet(name) || name.indexOf('/', "depths/".length()) >= 0) {
                continue;
            }
            sets++;
            for (String sibling : new String[] {
                Assets.Actor.depthsMove(name), Assets.Actor.depthsAttack(name),
                Assets.Actor.depthsHurt(name), Assets.Actor.depthsDeath(name)}) {
                if (!actors.containsKey(sibling)) {
                    missing.add(sibling);
                }
                // Anim.strip derives the frame from the height: 32 across the set.
                else if (actors.get(sibling)[1] != actors.get(name)[1]) {
                    missing.add(sibling + " (height differs)");
                }
            }
        }
        assertEquals(3, sets, "skeleton1, skeleton2, vampire");
        assertTrue(missing.isEmpty(), "incomplete depths sets: " + missing);
    }

    /**
     * The small depths idles are the trap: 64x16 is exactly four cells wide, so
     * Anim.directional accepts them and reads four frames as four facings. This
     * pins the measurement ActorSprites uses to tell them apart.
     */
    @Test
    void theSmallDepthsIdlesAreStripsNotSheets() {
        for (Map.Entry<String, int[]> e : sizes("actors").entrySet()) {
            if (e.getKey().startsWith("depths/monsters_idle/")
                    || e.getKey().startsWith("depths/priests_idle/")) {
                assertEquals(16, e.getValue()[1], e.getKey());
                assertEquals(64, e.getValue()[0], e.getKey());
            }
        }
    }

    /**
     * Which bosses the idle probe in Assets.Actor.BOSS_IDLE can draw. Measured:
     * every boss but the two dragons, which ship as separate head, body and
     * wing segments and would need an entity of their own.
     */
    @Test
    void everyBossButTheDragonsHasAnIdleStrip() {
        Map<String, int[]> actors = sizes("actors");
        TreeSet<String> bosses = new TreeSet<>();
        for (String name : actors.keySet()) {
            String id = Assets.Actor.bossIdOf(name);
            if (id != null) {
                bosses.add(id);
            }
        }
        assertEquals(20, bosses.size());
        TreeSet<String> undrawable = new TreeSet<>();
        for (String id : bosses) {
            boolean found = false;
            for (String anim : Assets.Actor.BOSS_IDLE) {
                if (actors.containsKey(Assets.Actor.boss(id, anim))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                undrawable.add(id);
            }
        }
        assertEquals(new TreeSet<>(List.of("dragonblue", "dragongreen")), undrawable);
    }

    @Test
    void theContentBossesHaveWhatTheirBrainsAnimate() {
        Map<String, int[]> actors = sizes("actors");
        assertTrue(actors.containsKey(Assets.Actor.boss("tengured", "trans")),
            "the two-phase boss's transformation strip");
        for (String anim : new String[] {"idle", "attack", "charge", "jump", "hit"}) {
            assertTrue(actors.containsKey(Assets.Actor.boss("giantfrog2", anim)), anim);
        }
        // tengured's strips are 82px tall: a boss cell is not 16 or 32.
        assertEquals(82, actors.get(Assets.Actor.boss("tengured", "idle"))[1]);
    }
}
