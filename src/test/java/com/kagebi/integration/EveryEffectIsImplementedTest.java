package com.kagebi.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/**
 * Every effect the content names is mentioned somewhere that implements it.
 *
 * <p>This is the test the whole effect system exists to make possible. A relic
 * whose effect nothing implements is the hardest bug in the game to notice: it
 * loads, it validates, it appears in the inventory with its description, the
 * player picks it up believing they are stronger, and nothing whatsoever
 * happens. No crash, no log, no wrong number on screen - just a build that
 * quietly is not a build.
 *
 * <p>{@code ContentValidator} already refuses to boot on an effect name that is
 * not on the documented list. That catches a typo. It cannot catch a name that
 * is correctly spelled, correctly listed, and implemented by nobody, which is
 * the failure that actually happened here: the first cut of this system shipped
 * with two effects classified as additive that had to be multiplicative, and
 * every hit the player took collapsed to one point of damage.
 *
 * <p>Searching the source for the literal string is crude, and a mention is not
 * proof of correct behaviour. It is proof that someone considered it, which is
 * the whole distance between a relic that is wrong and a relic that is absent.
 */
class EveryEffectIsImplementedTest {

    /** Where an effect may legitimately be handled. */
    private static final String[] IMPLEMENTING = {
        "src/main/java/com/kagebi/combat",
        "src/main/java/com/kagebi/entity",
        "src/main/java/com/kagebi/ai",
        "src/main/java/com/kagebi/loot",
        "src/main/java/com/kagebi/save",
        "src/main/java/com/kagebi/data/ShopCatalog.java",
        "src/main/java/com/kagebi/screen",
    };

    private static Set<String> effectsIn(String file, String section) {
        Set<String> out = new LinkedHashSet<>();
        File f = new File("assets/data/" + file);
        assertTrue(f.isFile(), "missing content file: " + f);
        JsonValue root = new JsonReader().parse(new FileHandle(f));
        JsonValue list = root.get(section);
        assertTrue(list != null, file + " has no '" + section + "' section");
        for (JsonValue entry = list.child; entry != null; entry = entry.next) {
            String effect = entry.getString("effect", null);
            if (effect != null && !effect.isEmpty()) {
                out.add(effect);
            }
        }
        return out;
    }

    private static String allImplementingSource() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (String entry : IMPLEMENTING) {
            Path path = Paths.get(entry);
            if (Files.isRegularFile(path)) {
                sb.append(Files.readString(path, StandardCharsets.UTF_8));
                continue;
            }
            if (!Files.isDirectory(path)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(path)) {
                for (Path p : (Iterable<Path>) walk
                        .filter(f -> f.toString().endsWith(".java"))::iterator) {
                    sb.append(Files.readString(p, StandardCharsets.UTF_8));
                }
            }
        }
        return sb.toString();
    }

    private void assertAllMentioned(Set<String> effects, String what) throws IOException {
        String source = allImplementingSource();
        List<String> unimplemented = new ArrayList<>();
        for (String effect : effects) {
            if (!source.contains('"' + effect + '"')) {
                unimplemented.add(effect);
            }
        }
        assertTrue(unimplemented.isEmpty(),
            what + " named by the content that nothing implements: " + unimplemented
            + " - a player picking one of these up would get nothing at all");
    }

    @Test
    void everyRelicEffectIsImplemented() throws IOException {
        Set<String> effects = effectsIn("relics.json", "relics");
        assertTrue(effects.size() >= 20, "only " + effects.size() + " relic effects found");
        assertAllMentioned(effects, "relic effects");
    }

    @Test
    void everyItemEffectIsImplemented() throws IOException {
        assertAllMentioned(effectsIn("items.json", "items"), "item effects");
    }

    @Test
    void everyUpgradeEffectIsImplemented() throws IOException {
        assertAllMentioned(effectsIn("upgrades.json", "upgrades"), "upgrade effects");
    }
}
