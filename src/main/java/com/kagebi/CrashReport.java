package com.kagebi;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * What is written down when the game throws where nobody was catching.
 *
 * <p><b>Why this exists.</b> An exception escaping {@code render()} does not
 * produce an error, a dialog or a log line: the LWJGL3 backend tears the
 * process down, and on Windows a double-clicked jar has no console for the
 * trace to land in. The window simply vanishes. A player reports that as "the
 * game closed itself", which is unfalsifiable and unfixable - the whole of the
 * evidence is gone at the moment it is created.
 *
 * <p>So the trace is written to a file beside the save, and the player is shown
 * where. That turns every future crash of this shape from a ghost story into a
 * bug report, which is worth more than any single fix.
 *
 * <p><b>Appending, not overwriting.</b> The interesting crash is often not the
 * last one - a bad state usually throws several times in slightly different
 * places before anyone thinks to look - so each report is added to the end with
 * a timestamp, and the file is the history rather than a snapshot.
 *
 * <p>Nothing here may throw. It runs at the one moment the program is already
 * failing, and an exception raised while reporting an exception replaces the
 * useful trace with a useless one.
 */
public final class CrashReport {

    static final String FILE = "kagebi_crash.log";

    private static final DateTimeFormatter STAMP =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Keeps the log from growing without bound over a long-lived install. */
    private static final long MAX_BYTES = 512 * 1024;

    private CrashReport() {
    }

    /**
     * Appends one report and returns the file it went to, or null if it could
     * not be written - a read-only install is not a reason to lose the trace
     * from the screen as well.
     */
    public static Path write(Path dir, Throwable error, String context) {
        try {
            Files.createDirectories(dir);
            Path path = dir.resolve(FILE);
            if (Files.exists(path) && Files.size(path) > MAX_BYTES) {
                Files.deleteIfExists(path);
            }
            Files.write(path, body(error, context).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return path;
        } catch (Throwable ignored) {
            // Deliberately swallowed, and deliberately Throwable rather than
            // Exception: this is the handler of last resort, and there is
            // nowhere left to report a failure to report.
            return null;
        }
    }

    static String body(Throwable error, String context) {
        StringWriter out = new StringWriter();
        PrintWriter w = new PrintWriter(out);
        w.println("---- " + LocalDateTime.now().format(STAMP)
            + "  kagebi " + Cfg.VERSION + " ----");
        if (context != null && !context.isEmpty()) {
            w.println(context);
        }
        error.printStackTrace(w);
        w.println();
        w.flush();
        return out.toString();
    }

    /**
     * The one line worth putting on the screen: the exception's own name and
     * message, plus the first frame that is ours.
     *
     * <p>The first frame of the trace is usually inside libGDX or the JDK. The
     * first one in {@code com.kagebi} is the one a person can act on, and
     * fitting it on a 320px screen means choosing rather than wrapping.
     */
    public static String headline(Throwable error) {
        String name = error.getClass().getSimpleName();
        String message = error.getMessage();
        String where = firstOwnFrame(error);
        return name + (message == null ? "" : ": " + message)
            + (where == null ? "" : "  @ " + where);
    }

    private static String firstOwnFrame(Throwable error) {
        for (StackTraceElement frame : error.getStackTrace()) {
            if (frame.getClassName().startsWith("com.kagebi.")) {
                String cls = frame.getClassName();
                return cls.substring(cls.lastIndexOf('.') + 1) + ":" + frame.getLineNumber();
            }
        }
        return null;
    }
}
