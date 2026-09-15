package com.kagebi.loot;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * House rule 2, enforced rather than remembered: {@code loot/} and
 * {@code save/migration/} never touch {@code Gdx} or the graphics classes.
 *
 * <p>That purity is what lets their tests run as plain JUnit with no GL
 * context and no headless application. The rule is easy to break by accident
 * - one {@code Gdx.app.log} while debugging a drop - and the breakage shows up
 * later as a test that suddenly needs a backend to run, far from its cause.
 */
class PurityTest {

    private static final String[] PURE = {
        "src/main/java/com/kagebi/loot",
        "src/main/java/com/kagebi/save/migration",
        // combat was always meant to be on this list - the plan says so - and
        // was not. It is the package where purity pays for itself most: damage,
        // i-frames, knockback and the relic arithmetic are the numbers that
        // most need testing and would otherwise need a GL context to reach.
        "src/main/java/com/kagebi/combat",
        // The village economy: crops, workers, trade and the kitchen, tested
        // over hours of village clock in milliseconds of test.
        "src/main/java/com/kagebi/village",
    };

    private static final String[] FORBIDDEN = {
        "Gdx.", "com.badlogic.gdx.Gdx", "com.badlogic.gdx.graphics",
    };

    @Test
    void purePackagesDoNotReachForGdx() throws IOException {
        List<String> offences = new ArrayList<>();
        int files = 0;
        for (String dir : PURE) {
            try (Stream<Path> walk = Files.walk(Paths.get(dir))) {
                for (Path p : (Iterable<Path>) walk.filter(f -> f.toString().endsWith(".java"))::iterator) {
                    files++;
                    String code = stripComments(Files.readString(p, StandardCharsets.UTF_8));
                    for (String bad : FORBIDDEN) {
                        if (code.contains(bad)) {
                            offences.add(p + " uses " + bad);
                        }
                    }
                }
            }
        }
        assertTrue(files >= 10, "found only " + files + " source files - has a package moved?");
        assertTrue(offences.isEmpty(), offences.toString());
    }

    /** Comments may explain the rule without breaking it. */
    private static String stripComments(String src) {
        return src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
    }
}
