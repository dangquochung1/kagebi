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
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectSet;
import com.kagebi.assets.Assets;
import com.kagebi.save.migration.Migrations;
import com.kagebi.settings.Difficulty;

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
     * Where saves are kept. Exposed so a crash report lands beside them rather
     * than in whichever directory the game happened to be started from.
     */
    public Path directory() {
        return dir;
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
        root.addChild("gearSeq", new JsonValue(p.gearSeq));
        root.addChild("stash", stash(p));
        root.addChild("materials", counts(p.materials));
        root.addChild("equipped", equipped(p));
        root.addChild("quests", quests(p));
        root.addChild("redeemed", sortedArray(p.redeemed));
        if (p.tracked != null) {
            root.addChild("tracked", new JsonValue(p.tracked));
        }
        root.addChild("village", village(p.village));
        // Absent rather than null when there is nothing to continue, so the
        // file says "no run" by not mentioning one.
        if (p.savedRun != null) {
            root.addChild("savedRun", savedRun(p.savedRun));
        }
        return root.prettyPrint(JsonWriter.OutputType.json, 60) + "\n";
    }

    /**
     * The owned armour, in instance order.
     *
     * <p>Sorted for the same reason the upgrades are: a save file that reorders
     * itself between two identical sessions cannot be diffed, and diffing it is
     * how a broken one is found.
     */
    private static JsonValue stash(Profile p) {
        List<OwnedGear> sorted = new ArrayList<>();
        for (OwnedGear g : p.stash) {
            sorted.add(g);
        }
        sorted.sort((a, b) -> Integer.compare(a.instance, b.instance));
        JsonValue out = new JsonValue(JsonValue.ValueType.array);
        for (OwnedGear g : sorted) {
            JsonValue one = new JsonValue(JsonValue.ValueType.object);
            one.addChild("instance", new JsonValue(g.instance));
            one.addChild("def", new JsonValue(g.defId));
            JsonValue sockets = new JsonValue(JsonValue.ValueType.array);
            for (String s : g.sockets) {
                // An empty socket is written as "", not dropped: the position
                // of a stone in the row is what the panel draws, so a piece
                // with a stone in its third hole must not read back with it in
                // the first.
                sockets.addChild(new JsonValue(s == null ? "" : s));
            }
            one.addChild("sockets", sockets);
            out.addChild(one);
        }
        return out;
    }

    /**
     * The quest log: one object per quest, holding its state and its per-step
     * counts.
     *
     * <p>Counts are written as an array rather than an object keyed by index,
     * because the index is a position in the quest's own step list and an
     * object with keys "0" and "1" reads as a mistake in a file meant to be
     * repaired by hand.
     */
    private static JsonValue quests(Profile p) {
        List<String> ids = new ArrayList<>();
        for (String id : p.quests.state.keys()) {
            ids.add(id);
        }
        Collections.sort(ids);
        JsonValue out = new JsonValue(JsonValue.ValueType.object);
        for (String id : ids) {
            JsonValue one = new JsonValue(JsonValue.ValueType.object);
            one.addChild("state", new JsonValue(p.quests.state.get(id).name()));
            ObjectIntMap<Integer> counts = p.quests.progress.get(id);
            if (counts != null && counts.size > 0) {
                int highest = 0;
                for (ObjectIntMap.Entry<Integer> e : counts) {
                    highest = Math.max(highest, e.key);
                }
                JsonValue steps = new JsonValue(JsonValue.ValueType.array);
                for (int i = 0; i <= highest; i++) {
                    steps.addChild(new JsonValue(counts.get(i, 0)));
                }
                one.addChild("steps", steps);
            }
            out.addChild(id, one);
        }
        return out;
    }

    private static JsonValue counts(ObjectIntMap<String> map) {
        List<String> ids = new ArrayList<>();
        for (ObjectIntMap.Entry<String> e : map) {
            ids.add(e.key);
        }
        Collections.sort(ids);
        JsonValue out = new JsonValue(JsonValue.ValueType.object);
        for (String id : ids) {
            out.addChild(id, new JsonValue(map.get(id, 0)));
        }
        return out;
    }

    private static JsonValue equipped(Profile p) {
        List<String> slots = new ArrayList<>();
        for (String slot : p.equipped.keys()) {
            slots.add(slot);
        }
        Collections.sort(slots);
        JsonValue out = new JsonValue(JsonValue.ValueType.object);
        for (String slot : slots) {
            out.addChild(slot, new JsonValue(p.equipped.get(slot, 0)));
        }
        return out;
    }

    /**
     * The run in progress, flattened.
     *
     * <p>Only the fields {@link SavedRun} carries. The floor and its layout are
     * absent by design - see that class - and the difficulty is written by name
     * rather than by ordinal, so reordering the enum cannot quietly move
     * somebody from Normal to Hard.
     */
    private static JsonValue savedRun(SavedRun r) {
        JsonValue out = new JsonValue(JsonValue.ValueType.object);
        out.addChild("seed", new JsonValue(r.seed));
        out.addChild("characterId", new JsonValue(r.characterId));
        out.addChild("weaponId", new JsonValue(r.weaponId));
        if (r.throwWeaponId != null) {
            out.addChild("throwWeaponId", new JsonValue(r.throwWeaponId));
        }
        out.addChild("difficulty", new JsonValue(r.difficulty.name()));
        out.addChild("hp", new JsonValue(r.hp));
        out.addChild("maxHp", new JsonValue(r.maxHp));
        out.addChild("baseMaxHp", new JsonValue(r.baseMaxHp));
        out.addChild("gold", new JsonValue(r.gold));
        out.addChild("diamonds", new JsonValue(r.diamonds));
        out.addChild("keys", new JsonValue(r.keys));
        out.addChild("relics", strings(r.relics));
        out.addChild("items", counts(r.items));
        if (r.quickItem != null) {
            out.addChild("quickItem", new JsonValue(r.quickItem));
        }
        out.addChild("kills", new JsonValue(r.kills));
        out.addChild("deepestFloor", new JsonValue(r.deepestFloor));
        out.addChild("elapsedSeconds", new JsonValue(r.elapsedSeconds));
        out.addChild("met", strings(r.met));
        return out;
    }

    /**
     * An Array of strings, in the order it is held.
     *
     * <p>Not sorted, unlike every set in this file: relics are a list, and the
     * order they were picked up in is part of what the player has.
     */
    private static JsonValue strings(Array<String> values) {
        JsonValue arr = new JsonValue(JsonValue.ValueType.array);
        for (String s : values) {
            arr.addChild(new JsonValue(s));
        }
        return arr;
    }

    /**
     * Reads a saved run back, or null for a file that has none.
     *
     * <p>Every field has a default and nothing here throws. This object is the
     * newest thing in the save file and therefore the most likely to be absent,
     * partial, or written by a build that is not this one - and the cost of
     * being strict is a player who cannot load at all.
     */
    private static SavedRun readSavedRun(JsonValue v) {
        if (v == null || !v.isObject()) {
            return null;
        }
        SavedRun r = new SavedRun();
        r.seed = v.getLong("seed", 0L);
        // Filtered, not copied. A character this build does not have reaches
        // ActorSprites as an atlas path that is not there, and the throw from
        // findRegion comes out of the top of render and takes the process with
        // it - which is how a save naming a deleted hero used to end an evening.
        r.characterId = knownCharacter(v.getString("characterId", null));
        r.weaponId = v.getString("weaponId", null);
        r.throwWeaponId = v.getString("throwWeaponId", null);
        r.difficulty = difficultyNamed(v.getString("difficulty", null));
        r.maxHp = Math.max(1, v.getInt("maxHp", 1));
        r.baseMaxHp = v.getInt("baseMaxHp", r.maxHp);
        r.hp = v.getInt("hp", r.maxHp);
        r.gold = v.getInt("gold", 0);
        r.diamonds = v.getInt("diamonds", 0);
        r.keys = v.getInt("keys", 0);
        readStrings(v.get("relics"), r.relics);
        JsonValue items = v.get("items");
        if (items != null) {
            for (JsonValue e = items.child; e != null; e = e.next) {
                r.items.put(e.name, e.asInt());
            }
        }
        r.quickItem = v.getString("quickItem", null);
        r.kills = v.getInt("kills", 0);
        r.deepestFloor = v.getInt("deepestFloor", 0);
        r.elapsedSeconds = v.getFloat("elapsedSeconds", 0f);
        readStrings(v.get("met"), r.met);
        // A run with nobody in it cannot be restored, and lighting the Continue
        // button for it would be a button that fails. Read as no saved run.
        return r.characterId == null || r.weaponId == null ? null : r;
    }

    /** By name, falling back to the default rather than throwing on a rename. */
    private static Difficulty difficultyNamed(String name) {
        if (name != null) {
            for (Difficulty d : Difficulty.values()) {
                if (d.name().equals(name)) {
                    return d;
                }
            }
        }
        return Difficulty.DEFAULT;
    }

    private static void readStrings(JsonValue array, Array<String> into) {
        if (array == null) {
            return;
        }
        for (JsonValue e = array.child; e != null; e = e.next) {
            into.add(e.asString());
        }
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
        // unlocked whatever a hand-edited file says. Characters are filtered as
        // well, because an id this build does not have is one the roster screen
        // will offer and the world will then fail to draw.
        addKnownCharacters(root.get("unlockedCharacters"), p.unlockedCharacters);
        addAll(root.get("unlockedWeapons"), p.unlockedWeapons);
        addAll(root.get("bestiary"), p.bestiary);
        addAll(root.get("redeemed"), p.redeemed);

        readQuests(root.get("quests"), p);
        p.tracked = root.getString("tracked", null);
        readStash(root.get("stash"), p);
        p.gearSeq = Math.max(root.getInt("gearSeq", 0), highestInstance(p));
        JsonValue materials = root.get("materials");
        if (materials != null) {
            for (JsonValue e = materials.child; e != null; e = e.next) {
                p.addMaterial(e.name, e.asInt());
            }
        }
        JsonValue worn = root.get("equipped");
        if (worn != null) {
            for (JsonValue e = worn.child; e != null; e = e.next) {
                // Only onto a piece that is actually in the stash. A slot
                // pointing at a piece that was sold, or at a hand-edited
                // number, would be a permanently empty slot the player could
                // not fill because the game believed it was full.
                if (p.gear(e.asInt()) != null) {
                    p.equipped.put(e.name, e.asInt());
                }
            }
        }
        // A save from before the village had an economy has no such object,
        // and reads as a village that has just begun.
        JsonValue village = root.get("village");
        if (village != null && village.isObject()) {
            readVillage(village, p.village);
        }
        p.savedRun = readSavedRun(root.get("savedRun"));
        return p;
    }

    private static void readQuests(JsonValue object, Profile p) {
        if (object == null || !object.isObject()) {
            return;
        }
        for (JsonValue e = object.child; e != null; e = e.next) {
            String name = e.getString("state", null);
            QuestLog.State state = null;
            for (QuestLog.State s : QuestLog.State.values()) {
                if (s.name().equals(name)) {
                    state = s;
                }
            }
            if (state == null) {
                // A state this build does not know is a file from a later one,
                // or a hand edit. Skipped rather than guessed: an unknown state
                // read as ACTIVE would put a finished quest back on the board.
                continue;
            }
            p.quests.set(e.name, state);
            JsonValue steps = e.get("steps");
            if (steps == null || !steps.isArray()) {
                continue;
            }
            int i = 0;
            for (JsonValue s = steps.child; s != null; s = s.next, i++) {
                p.quests.advance(e.name, i, s.asInt());
            }
        }
    }

    private static void readStash(JsonValue array, Profile p) {
        if (array == null || !array.isArray()) {
            return;
        }
        for (JsonValue e = array.child; e != null; e = e.next) {
            String def = e.getString("def", null);
            int instance = e.getInt("instance", 0);
            if (def == null || instance <= 0) {
                continue;
            }
            JsonValue sockets = e.get("sockets");
            int count = sockets != null && sockets.isArray() ? sockets.size : 0;
            String[] set = new String[count];
            int i = 0;
            for (JsonValue s = sockets == null ? null : sockets.child; s != null; s = s.next, i++) {
                String id = s.asString();
                set[i] = id == null || id.isEmpty() ? null : id;
            }
            p.stash.add(new OwnedGear(instance, def, set));
        }
    }

    /**
     * The largest instance id in the stash.
     *
     * <p>{@code gearSeq} is taken as the higher of what was written and this,
     * so a hand-edited file that added a piece with a big number cannot go on
     * to hand out an id that is already in use - two pieces with one id would
     * make the equipped slot ambiguous.
     */
    private static int highestInstance(Profile p) {
        int highest = 0;
        for (OwnedGear g : p.stash) {
            highest = Math.max(highest, g.instance);
        }
        return highest;
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
     * The same, dropping ids that are not characters in this build.
     *
     * <p>A migration renames what it knows about; this catches the rest - a
     * hand-edited file, a profile from a branch, a character removed after the
     * migration that removed its neighbours was written. Everything else in
     * this file is checked against what exists before it is trusted, and the
     * roster was the one set that was not.
     */
    private static void addKnownCharacters(JsonValue array, ObjectSet<String> into) {
        if (array == null) {
            return;
        }
        for (JsonValue e = array.child; e != null; e = e.next) {
            String id = knownCharacter(e.asString());
            if (id != null) {
                into.add(id);
            }
        }
    }

    /** The id, or null if no such character ships. */
    private static String knownCharacter(String id) {
        return id != null && Assets.Actor.indexOf(id) >= 0 ? id : null;
    }

    // ---- the village ---------------------------------------------------------------

    /**
     * The village laid out like the rest of the file: counts sorted by name,
     * workshops by id, and a bare plot written as null so the field keeps its
     * order.
     */
    private static JsonValue village(VillageState v) {
        JsonValue out = new JsonValue(JsonValue.ValueType.object);
        out.addChild("clock", new JsonValue(v.clock));
        out.addChild("harvests", new JsonValue(v.harvests));
        out.addChild("planted", new JsonValue(v.planted));
        out.addChild("stock", sortedCounts(v.stock));
        out.addChild("seeds", sortedCounts(v.seeds));
        out.addChild("tools", sortedCounts(v.tools));
        out.addChild("pantry", sortedCounts(v.pantry));

        JsonValue plots = new JsonValue(JsonValue.ValueType.array);
        for (VillageState.Plot plot : v.plots) {
            if (plot == null || plot.crop == null) {
                plots.addChild(new JsonValue(JsonValue.ValueType.nullValue));
                continue;
            }
            JsonValue o = new JsonValue(JsonValue.ValueType.object);
            o.addChild("crop", new JsonValue(plot.crop));
            o.addChild("sown", new JsonValue(plot.sown));
            plots.addChild(o);
        }
        out.addChild("plots", plots);

        List<String> ids = new ArrayList<>();
        for (String id : v.workshops.keys()) {
            ids.add(id);
        }
        Collections.sort(ids);
        JsonValue works = new JsonValue(JsonValue.ValueType.object);
        for (String id : ids) {
            VillageState.Work w = v.workshops.get(id);
            JsonValue o = new JsonValue(JsonValue.ValueType.object);
            o.addChild("held", new JsonValue(w.held));
            o.addChild("since", new JsonValue(w.since));
            o.addChild("made", new JsonValue(w.made));
            works.addChild(id, o);
        }
        out.addChild("workshops", works);
        return out;
    }

    private static JsonValue sortedCounts(ObjectIntMap<String> counts) {
        List<String> keys = new ArrayList<>();
        for (ObjectIntMap.Entry<String> e : counts) {
            keys.add(e.key);
        }
        Collections.sort(keys);
        JsonValue out = new JsonValue(JsonValue.ValueType.object);
        for (String key : keys) {
            out.addChild(key, new JsonValue(counts.get(key, 0)));
        }
        return out;
    }

    /**
     * Reads what {@link #village} wrote. A missing field takes the value a new
     * village has; a negative count, or a time later than the clock - both only
     * reachable by editing the file - is brought back to something the rules
     * can use rather than trusted.
     */
    private static void readVillage(JsonValue j, VillageState v) {
        v.clock = Math.max(0, j.getDouble("clock", 0));
        v.harvests = Math.max(0, j.getInt("harvests", 0));
        v.planted = j.getBoolean("planted", false);
        readCounts(j.get("stock"), v.stock);
        readCounts(j.get("seeds"), v.seeds);
        readCounts(j.get("tools"), v.tools);
        readCounts(j.get("pantry"), v.pantry);

        JsonValue plots = j.get("plots");
        if (plots != null && plots.isArray()) {
            for (JsonValue e = plots.child; e != null; e = e.next) {
                VillageState.Plot plot = new VillageState.Plot();
                if (e.isObject() && e.getString("crop", null) != null) {
                    plot.crop = e.getString("crop");
                    plot.sown = Math.min(v.clock, e.getDouble("sown", v.clock));
                }
                v.plots.add(plot);
            }
        }

        JsonValue works = j.get("workshops");
        if (works != null && works.isObject()) {
            for (JsonValue e = works.child; e != null; e = e.next) {
                if (!e.isObject()) {
                    continue;
                }
                VillageState.Work w = new VillageState.Work();
                w.held = Math.max(0, e.getInt("held", 0));
                w.since = Math.min(v.clock, e.getDouble("since", v.clock));
                w.made = Math.max(0, e.getLong("made", 0));
                v.workshops.put(e.name, w);
            }
        }
    }

    private static void readCounts(JsonValue object, ObjectIntMap<String> into) {
        if (object == null || !object.isObject()) {
            return;
        }
        for (JsonValue e = object.child; e != null; e = e.next) {
            if (e.isNumber() && e.asInt() > 0) {
                into.put(e.name, e.asInt());
            }
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
