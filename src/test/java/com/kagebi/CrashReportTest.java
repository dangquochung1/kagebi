package com.kagebi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The report is the only witness to a crash, so it has to survive one.
 *
 * <p>Every case here is a way the reporter could make a bad moment worse:
 * throwing while reporting a throw, overwriting the first and most useful
 * trace with the fifth, or handing the screen a message too long to read.
 */
class CrashReportTest {

    private static Throwable thrown() {
        try {
            throw new IllegalStateException("the kit does not fit the character");
        } catch (IllegalStateException e) {
            return e;
        }
    }

    @Test
    void theTraceAndTheContextBothReachTheFile(@TempDir Path dir) throws IOException {
        Path written = CrashReport.write(dir, thrown(), "screen: HubScreen");
        assertNotNull(written);

        String text = new String(Files.readAllBytes(written), StandardCharsets.UTF_8);
        assertTrue(text.contains("IllegalStateException"), text);
        assertTrue(text.contains("the kit does not fit the character"), text);
        assertTrue(text.contains("screen: HubScreen"), text);
        assertTrue(text.contains("CrashReportTest"), "the trace itself must be there");
    }

    @Test
    void asecondCrashIsAddedRatherThanReplacingTheFirst(@TempDir Path dir) throws IOException {
        // The first crash is usually the informative one; a bad state throws
        // again in shallower places afterwards.
        CrashReport.write(dir, new RuntimeException("first"), null);
        Path written = CrashReport.write(dir, new RuntimeException("second"), null);

        String text = new String(Files.readAllBytes(written), StandardCharsets.UTF_8);
        assertTrue(text.contains("first"), text);
        assertTrue(text.contains("second"), text);
    }

    @Test
    void anUnwritableDirectoryCostsTheFileAndNothingElse(@TempDir Path dir) throws IOException {
        // A read-only install must not turn a reportable crash into a second,
        // unreportable one. Null means "no file", not "no report".
        //
        // A regular file standing where a directory should be, rather than a
        // malformed path: a malformed one is rejected by Paths.get before this
        // class is reached, which tests the JDK and not the reporter.
        Path blocked = Files.createFile(dir.resolve("in-the-way"));
        assertNull(CrashReport.write(blocked.resolve("nested"), thrown(), "screen: none"));
    }

    @Test
    void theHeadlineNamesTheFirstFrameThatIsOurs() {
        String line = CrashReport.headline(thrown());
        assertTrue(line.startsWith("IllegalStateException: the kit does not fit"), line);
        // Not the JDK frame above it: the actionable one is in com.kagebi.
        assertTrue(line.contains("CrashReportTest:"), line);
    }

    @Test
    void theHeadlineSurvivesAnExceptionWithNoMessage() {
        String line = CrashReport.headline(new NullPointerException());
        assertTrue(line.startsWith("NullPointerException"), line);
        assertFalse(line.contains("null"), "a missing message must not print as the word null");
    }

    @Test
    void theHeadlineSurvivesATraceWithNothingOfOursInIt() {
        RuntimeException alien = new RuntimeException("from somewhere else");
        alien.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("java.util.ArrayList", "get", "ArrayList.java", 427),
        });
        assertEquals("RuntimeException: from somewhere else", CrashReport.headline(alien));
    }
}
