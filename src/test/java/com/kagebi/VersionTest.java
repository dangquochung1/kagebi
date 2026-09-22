package com.kagebi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The build's number, held in two places, has to be one number.
 *
 * <p>Maven needs it in {@code pom.xml} and the game needs it at runtime - for
 * the corner of the main menu and the head of a crash report - and there is no
 * way to have the second read the first without a filtered resource or a
 * generated source file, neither of which is worth its weight for one string.
 *
 * <p>So the copy stays, and this test is the reason the copy is safe. The
 * failure it prevents is quiet: a release tagged v0.7.0 whose crash reports all
 * say 0.6.0 sends every bug in it to the wrong build, and nothing else in the
 * project would ever notice.
 */
class VersionTest {

    /** The project's own version, not one of the dependency versions below it. */
    private static final Pattern PROJECT_VERSION =
        Pattern.compile("<artifactId>kagebi</artifactId>\\s*(?:<!--.*?-->\\s*)?<version>([^<]+)</version>",
                        Pattern.DOTALL);

    @Test
    void theGameAndThePomAgreeOnTheVersion() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        Matcher m = PROJECT_VERSION.matcher(pom);
        assertTrue(m.find(), "pom.xml has no <version> under <artifactId>kagebi</artifactId>");
        assertEquals(m.group(1).trim(), Cfg.VERSION,
            "Cfg.VERSION and the pom version have drifted apart - change both");
    }

    /**
     * Semantic versioning, and no {@code -SNAPSHOT}.
     *
     * <p>A tag is a promise that the thing it names will not move again, so the
     * version a tagged build reports must not be the one Maven uses for a build
     * still in progress.
     */
    @Test
    void theVersionIsAReleaseNumber() {
        assertTrue(Cfg.VERSION.matches("\\d+\\.\\d+\\.\\d+"),
            "expected MAJOR.MINOR.PATCH, got " + Cfg.VERSION);
    }
}
