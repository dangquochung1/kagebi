package com.kagebi.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.SerializationException;

/**
 * Every JSON file in the content directory, split into its named sections.
 *
 * <p>Files are found by listing the directory and sections by their top-level
 * key - {@code "enemies"}, {@code "floors"} - never by filename. That keeps the
 * one rule about asset paths intact (no file name is written anywhere but
 * {@code Assets}), and it means content can be split however is convenient to
 * edit: a second file holding more enemies simply adds to the first.
 *
 * <p>The icon map is read first because every other section may refer to an
 * icon by name.
 */
final class DataFiles {

    static final String ENEMIES = "enemies";
    static final String WEAPONS = "weapons";
    static final String RELICS = "relics";
    static final String ITEMS = "items";
    static final String TABLES = "tables";
    static final String FLOORS = "floors";
    static final String ICONS = "icons";
    static final String UPGRADES = "upgrades";
    static final String UNLOCKS = "unlocks";
    static final String GOODS = "goods";
    static final String CROPS = "crops";
    static final String FARM = "farm";
    static final String WORKSHOPS = "workshops";
    static final String TOOLS = "tools";
    static final String RECIPES = "recipes";
    static final String GEAR = "gear";
    static final String GEMS = "gems";
    static final String CRAFTS = "crafts";
    static final String QUESTS = "quests";
    static final String SKILLS = "skills";

    /** A misspelt section would otherwise load as nothing at all. */
    static final Set<String> SECTIONS = Set.of(
        ENEMIES, WEAPONS, RELICS, ITEMS, TABLES, FLOORS, ICONS, UPGRADES, UNLOCKS,
        GOODS, CROPS, FARM, WORKSHOPS, TOOLS, RECIPES, GEAR, GEMS, CRAFTS, QUESTS,
        SKILLS);

    /** One section as it appeared in one file. */
    static final class Section {
        final String file;
        final JsonValue value;

        Section(String file, JsonValue value) {
            this.file = file;
            this.value = value;
        }
    }

    final List<String> problems;
    final ObjectIntMap<String> icons = new ObjectIntMap<>();
    private final ObjectMap<String, Array<Section>> sections = new ObjectMap<>();

    private DataFiles(List<String> problems) {
        this.problems = problems;
    }

    static DataFiles read(FileHandle dir, List<String> problems) {
        DataFiles out = new DataFiles(problems);
        if (!dir.isDirectory()) {
            problems.add(dir.path() + ": content directory not found");
            return out;
        }
        FileHandle[] files = dir.list(".json");
        // Sorted so that a duplicate id across two files is always reported the
        // same way round, whatever order the filesystem lists them in.
        Arrays.sort(files, Comparator.comparing(FileHandle::name));
        if (files.length == 0) {
            // Directory listing returns nothing for assets packed onto the
            // classpath. Say so, rather than booting with an empty registry.
            problems.add(dir.path() + ": no .json files found - are the assets on the classpath"
                + " rather than on disk?");
        }
        for (FileHandle file : files) {
            JsonValue root;
            try {
                root = new JsonReader().parse(file);
            } catch (SerializationException e) {
                problems.add(file.name() + ": not valid JSON: " + e.getMessage());
                continue;
            }
            if (root == null || !root.isObject()) {
                problems.add(file.name() + ": top level should be an object of named sections");
                continue;
            }
            for (JsonValue section = root.child; section != null; section = section.next) {
                if (!SECTIONS.contains(section.name)) {
                    problems.add(file.name() + ": unknown section '" + section.name
                        + "', expected one of " + SECTIONS);
                    continue;
                }
                Array<Section> list = out.sections.get(section.name);
                if (list == null) {
                    list = new Array<>();
                    out.sections.put(section.name, list);
                }
                list.add(new Section(file.name(), section));
            }
        }
        out.readIcons();
        return out;
    }

    private void readIcons() {
        for (Section s : sections(ICONS)) {
            if (!s.value.isObject()) {
                problems.add(s.file + ": 'icons' should be an object of name to index");
                continue;
            }
            for (JsonValue e = s.value.child; e != null; e = e.next) {
                if (!e.isNumber()) {
                    problems.add(s.file + ": icon '" + e.name + "' should be an index");
                    continue;
                }
                if (icons.containsKey(e.name)) {
                    problems.add(s.file + ": icon '" + e.name + "' is named twice");
                }
                icons.put(e.name, e.asInt());
            }
        }
    }

    Array<Section> sections(String name) {
        Array<Section> list = sections.get(name);
        return list != null ? list : new Array<>();
    }

    /**
     * Every object in every array-valued section of this name, wrapped for
     * reading. {@code kind} is only for messages: "enemy", "floor".
     */
    List<Fields> objects(String section, String kind) {
        List<Fields> out = new ArrayList<>();
        for (Section s : sections(section)) {
            if (!s.value.isArray()) {
                problems.add(s.file + ": '" + section + "' should be an array");
                continue;
            }
            int i = 0;
            for (JsonValue e = s.value.child; e != null; e = e.next, i++) {
                out.add(new Fields(e, s.file + ": " + kind + " #" + i, problems));
            }
        }
        return out;
    }
}
