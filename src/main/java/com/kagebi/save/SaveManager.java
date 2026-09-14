package com.kagebi.save;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.kagebi.save.migration.Migrations;

/**
 * Loads and stores the {@link Profile} as human-readable JSON.
 *
 * <p><b>How a save is written.</b> The new profile goes to a temporary file,
 * which is flushed to the disk itself - not just to the operating system's
 * cache - and only then renamed over the old one. A rename within a directory
 * is atomic on every filesystem the game runs on, so at every instant there
 * is one complete save on disk: the old one until the rename, the new one
 * after. Writing in place instead leaves a window where the file is half old
 * and half new, and the lid of a laptop closes at exactly the wrong moment
 * often enough that someone will lose everything they have.
 *
 * <p>The flush before the rename is the part that is easy to leave out and
 * matters most. Without it a power cut can persist the rename before the data,
 * and the player boots to a correctly named, zero-length file.
 *
 * <p><b>What survives what.</b> The previous save is kept beside the current
 * one as a backup, and read if the current one does not parse. A file that
 * does not parse is moved aside rather than overwritten, so a hand-edit gone
 * wrong can still be repaired by hand. A save from a newer build is copied
 * aside before this build first writes over it. None of these ever produce a
 * fresh profile while a readable one exists.
 */
public final class SaveManager {

    static final String FILE = "kagebi_profile.json";
    static final String TEMP = FILE + ".tmp";
    static final String BACKUP = FILE + ".bak";
    static final String CORRUPT = FILE + ".corrupt";

    /**
     * Where libGDX Preferences land on desktop - {@code ~/.prefs/}, read from
     * the Lwjgl3 backend's configuration defaults rather than assumed - so a
     * player looking for their settings finds their save beside them.
     */
    private static final String PREFS_DIR = ".prefs/";

    private final Path dir;
    private Profile cached;

    /** For the game: saves beside the Preferences file. */
    public SaveManager() {
        this(Gdx.files.external(PREFS_DIR).file().toPath());
    }

    /** For tests and tools: saves in the given directory. */
    public SaveManager(Path dir) {
        this.dir = dir;
    }

    public Path file() {
        return dir.resolve(FILE);
    }

    /**
     * The profile, read once and then held. A missing file is a first launch
     * and returns a fresh profile; an unreadable one falls back to the backup.
     */
    public Profile load() {
        if (cached == null) {
            cached = read();
        }
        return cached;
    }

    /**
     * Writes the profile. Returns false and leaves the previous save untouched
     * if anything goes wrong; a failed save is reported, not thrown, because
     * it happens on the game-over screen and crashing there would lose the run
     * the save was trying to keep.
     */
    public boolean save(Profile profile) {
        cached = profile;
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(FILE);
            Path temp = dir.resolve(TEMP);
            writeDurably(temp, toJson(profile).getBytes(StandardCharsets.UTF_8));
            if (Files.exists(target)) {
                // Copy rather than move: the current save must still be there
                // if the process dies between this line and the next.
                Files.copy(target, dir.resolve(BACKUP), StandardCopyOption.REPLACE_EXISTING);
            }
            replace(temp, target);
            return true;
        } catch (IOException e) {
            log("could not save profile to " + dir + ": " + e);
            return false;
        }
    }

    private static void writeDurably(Path path, byte[] bytes) throws IOException {
        try (FileChannel ch = FileChannel.open(path, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buf = ByteBuffer.wrap(bytes);
            while (buf.hasRemaining()) {
                ch.write(buf);
            }
            ch.force(true);
        }
    }

    private static void replace(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            // Only on filesystems without an atomic rename, such as some
            // network mounts. Still better than writing in place.
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ---- reading -------------------------------------------------------------

    private Profile read() {
        Path main = dir.resolve(FILE);
        Path backup = dir.resolve(BACKUP);
        if (!Files.exists(main) && !Files.exists(backup)) {
            return new Profile();
        }
        if (Files.exists(main)) {
            try {
                return fromJson(Files.readString(main, StandardCharsets.UTF_8), main);
            } catch (RuntimeException | IOException e) {
                log("profile " + main + " is unreadable (" + e.getMessage()
                    + "); moving it aside and trying the backup");
                moveAside(main);
            }
        }
        if (Files.exists(backup)) {
            try {
                return fromJson(Files.readString(backup, StandardCharsets.UTF_8), backup);
            } catch (RuntimeException | IOException e) {
                log("backup " + backup + " is unreadable too (" + e.getMessage() + ")");
            }
        }
        log("no readable profile; starting fresh. The unreadable file is kept as " + CORRUPT);
        return new Profile();
    }

    /**
     * Keeps a file that would not parse, so that the next save does not copy
     * it over a good backup, and so that a person can still open it.
     */
    private void moveAside(Path file) {
        try {
            Files.move(file, dir.resolve(CORRUPT), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log("could not move " + file + " aside: " + e);
        }
    }

    // ---- the format ------------------------------------------------------------

    /**
     * Written by hand rather than by reflection, so the file's field names are
     * a decision rather than an accident of the Java ones, and so it is sorted
     * and indented: a save that a person can read is a save a person can fix.
     */
    static String toJson(Profile p) {
        JsonValue root = new JsonValue(JsonValue.ValueType.object);
        root.addChild("version", new JsonValue(Profile.CURRENT_VERSION));
        root.addChild("gold", new JsonValue(p.gold));
        root.addChild("diamonds", new JsonValue(p.diamonds));
        root.addChild("runs", new JsonValue(p.runs));
        root.addChild("wins", new JsonValue(p.wins));
        root.addChild("deepestFloor", new JsonValue(p.deepestFloor));
        root.addChild("clearedStages", new JsonValue(p.clearedStages));

        JsonValue upgrades = new JsonValue(JsonValue.ValueType.object);
        List<String> ids = new ArrayList<>();
        for (ObjectIntMap.Entry<String> e : p.upgrades) {
            ids.add(e.key);
        }
        Collections.sort(ids);
        for (String id : ids) {
            upgrades.addChild(id, new JsonValue(p.upgrades.get(id, 0)));
        }
        root.addChild("upgrades", upgrades);

        root.addChild("unlockedCharacters", sortedArray(p.unlockedCharacters));
        root.addChild("unlockedWeapons", sortedArray(p.unlockedWeapons));
        root.addChild("bestiary", sortedArray(p.bestiary));
        return root.prettyPrint(JsonWriter.OutputType.json, 60) + "\n";
    }

    private static JsonValue sortedArray(ObjectSet<String> set) {
        List<String> sorted = new ArrayList<>();
        for (String s : set) {
            sorted.add(s);
        }
        Collections.sort(sorted);
        JsonValue arr = new JsonValue(JsonValue.ValueType.array);
        for (String s : sorted) {
            arr.addChild(new JsonValue(s));
        }
        return arr;
    }

    /**
     * Reads a profile, upgrading it through the migration chain first. A field
     * the file does not have takes the default a new profile has, which is why
     * adding a field needs no migration at all.
     */
    Profile fromJson(String text, Path source) {
        JsonValue root = new JsonReader().parse(text);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("top level is not an object");
        }
        // No version field means the file predates versioning, not that it is
        // current - reading it as current would skip every migration.
        int version = root.getInt("version", 0);
        if (version > Profile.CURRENT_VERSION) {
            preserveNewer(source, version);
        } else if (version < Profile.CURRENT_VERSION) {
            Migrations.upgrade(root, version, Profile.CURRENT_VERSION);
        }

        Profile p = new Profile();
        p.version = Profile.CURRENT_VERSION;
        p.gold = root.getInt("gold", p.gold);
        p.diamonds = root.getInt("diamonds", p.diamonds);
        p.runs = root.getInt("runs", p.runs);
        p.wins = root.getInt("wins", p.wins);
        p.deepestFloor = root.getInt("deepestFloor", p.deepestFloor);
        // A save written before the game had stages has no such field, and the
        // rules it was written under make the answer knowable: a continuous
        // descent that reached floor N had cleared the N-1 floors above it.
        // Inferring beats defaulting to zero, which would shut a returning
        // player out of stages they have already finished.
        p.clearedStages = root.getInt("clearedStages", Math.max(0, p.deepestFloor - 1));

        JsonValue upgrades = root.get("upgrades");
        if (upgrades != null) {
            for (JsonValue e = upgrades.child; e != null; e = e.next) {
                p.upgrades.put(e.name, e.asInt());
            }
        }
        // Added to, never replaced: the starting character and weapon stay
        // unlocked whatever a hand-edited file says.
        addAll(root.get("unlockedCharacters"), p.unlockedCharacters);
        addAll(root.get("unlockedWeapons"), p.unlockedWeapons);
        addAll(root.get("bestiary"), p.bestiary);
        return p;
    }

    private static void addAll(JsonValue array, ObjectSet<String> into) {
        if (array == null) {
            return;
        }
        for (JsonValue e = array.child; e != null; e = e.next) {
            into.add(e.asString());
        }
    }

    /**
     * A save from a newer build still loads - its known fields are read and
     * the rest ignored - but the next save would drop whatever this build does
     * not understand. So a copy is kept, once, named for its version.
     */
    private void preserveNewer(Path source, int version) {
        Path keep = dir.resolve(FILE + ".v" + version);
        if (Files.exists(keep)) {
            return;
        }
        try {
            Files.copy(source, keep);
            log("profile was written by a newer build (v" + version + "); kept a copy as " + keep);
        } catch (IOException e) {
            log("could not preserve newer profile: " + e);
        }
    }

    private static void log(String message) {
        if (Gdx.app != null) {
            Gdx.app.error("save", message);
        } else {
            System.err.println("[save] " + message);
        }
    }
}
