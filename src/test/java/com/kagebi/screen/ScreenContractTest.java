package com.kagebi.screen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.kagebi.assets.Assets;

/**
 * What the screens ask the asset pipeline and the translators for.
 *
 * <p>{@code AssetsContractTest} covers the constants in {@link Assets.Ui} and
 * {@link Assets.Actor} by reflection. It cannot cover the two things the
 * screens do on top of that: regions built at runtime from an id - villagers,
 * weapon icons - and translation keys built the same way. Both fail the same
 * quiet way, as a blank space or a raw key on a screen nobody happened to open.
 */
class ScreenContractTest {

    private static Set<String> regions(String atlas) {
        File file = new File("assets/atlas/" + atlas + ".atlas");
        Assumptions.assumeTrue(file.isFile(),
            "atlas not built - run: python tools/pack_atlas.py");
        FileHandle handle = new FileHandle(file);
        Set<String> names = new HashSet<>();
        for (Region r : new TextureAtlasData(handle, handle.parent(), false).getRegions()) {
            names.add(r.name);
        }
        return names;
    }

    @Test
    void everyVillagerHasASheetAndAFace() {
        Set<String> npc = regions("npc");
        List<String> missing = new ArrayList<>();
        for (String id : Assets.Npc.VILLAGERS) {
            for (String region : new String[] {Assets.Npc.idle(id), Assets.Npc.face(id)}) {
                if (!npc.contains(region)) {
                    missing.add(region);
                }
            }
        }
        assertTrue(missing.isEmpty(), "missing from npc.atlas: " + missing);
    }

    @Test
    void theExitMarkerExists() {
        assertTrue(regions("fx").contains(Assets.Fx.EXIT),
            Assets.Fx.EXIT + " is missing from fx.atlas");
    }

    /**
     * Character select draws an icon for every weapon it offers, and falls back
     * to a plain sword for an id it has no icon for. Both paths have to land on
     * something, or a weapon is a blank cell.
     */
    @Test
    void everyOfferedWeaponHasAnIcon() {
        Set<String> ui = regions("ui");
        List<String> missing = new ArrayList<>();
        for (String id : CharacterSelectScreen.FALLBACK_WEAPONS) {
            if (!ui.contains(Assets.Ui.weaponIcon(id))) {
                missing.add(id + " -> " + Assets.Ui.weaponIcon(id));
            }
        }
        if (!ui.contains(Assets.Ui.weaponIcon("a weapon from content, unknown here"))) {
            missing.add("the fallback icon");
        }
        assertTrue(missing.isEmpty(), "missing from ui.atlas: " + missing);
    }

    @Test
    void bothStingsExist() {
        for (String path : new String[] {Assets.JINGLE_SUCCESS, Assets.JINGLE_GAMEOVER,
                                         Assets.SFX_ACCEPT, Assets.SFX_CANCEL,
                                         Assets.SFX_MOVE, Assets.SFX_DOOR}) {
            File f = new File(path);
            if (!f.isFile()) {
                Assumptions.assumeTrue(false,
                    "audio not built - run: python tools/build_assets.py");
            }
        }
    }

    /** A content field may name a track as a bare file or as a full path. */
    @Test
    void musicPathsResolveEitherWay() {
        assertTrue(Assets.music("21_dungeon").equals(Assets.MUSIC_DUNGEON));
        assertTrue(Assets.music("21_dungeon.ogg").equals(Assets.MUSIC_DUNGEON));
        assertTrue(Assets.music(Assets.MUSIC_DUNGEON).equals(Assets.MUSIC_DUNGEON));
        assertTrue(Assets.music(null) == null);
    }

    // ---- translations ------------------------------------------------------

    private static Set<String> keysOf(String lang) {
        Set<String> out = new LinkedHashSet<>();
        for (String pattern : new String[] {"%s.json", "content.%s.json"}) {
            File file = new File("assets/i18n/" + String.format(pattern, lang));
            if (!file.isFile()) {
                continue;
            }
            JsonValue root = new JsonReader().parse(new FileHandle(file));
            for (JsonValue e = root.child; e != null; e = e.next) {
                out.add(e.name);
            }
        }
        return out;
    }

    /**
     * Every key a screen looks up, including the ones built from an id. The
     * sibling test in {@code ContentContractTest} checks the two languages
     * agree; this checks that what the code asks for is in them at all.
     */
    private static List<String> screenKeys() {
        List<String> keys = new ArrayList<>();
        for (String k : new String[] {
            "menu.newgame", "menu.credits", "menu.settings", "menu.quit", "menu.continue",
            "select.title", "select.weapon", "select.start",
            "shop.title", "shop.tab.upgrades", "shop.tab.unlocks", "shop.buy",
            "shop.max", "shop.owned", "shop.poor", "shop.bought",
            "prompt.talk", "prompt.descend", "prompt.escape",
            // Advertised by World.promptKey(); the screens only look them up.
            "prompt.open_chest", "prompt.shop",
            "pause.title", "pause.resume", "pause.abandon", "pause.confirm", "pause.to_menu",
            "inv.title", "inv.relics", "inv.items", "inv.empty", "inv.slot_empty",
            "inv.set_quick", "inv.on_quick",
            "end.banked", "end.to_village", "end.to_map",
            "dungeon.no_rooms", "dungeon.locked", "dungeon.unlocked",
            "map.title", "map.play", "stage.cleared", "prompt.leave",
            "game.title", "game.floor", "game.victory", "game.gameover",
            "game.stats.floor", "game.stats.kills", "game.stats.gold", "game.stats.gems", "game.stats.time",
            "common.back", "common.locked", "floor.hub"}) {
            keys.add(k);
        }
        for (int floor = 1; floor <= 5; floor++) {
            keys.add("floor." + floor);
            // The world map reads this before the stage is entered, so a
            // missing one is a blank panel rather than a crash - which is
            // exactly the kind of hole a contract test is for.
            keys.add("floor." + floor + ".desc");
        }
        for (String id : Assets.Actor.CHARACTERS) {
            // One name and one perk line each, and each in one file. There used
            // to be two of both: "char.<id>.passive" beside "character.<id>.desc"
            // and "char.<id>.name" beside "character.<id>.name", so the select
            // screen and the shop called the same ninja by different names and
            // promised different perks. See CharacterSelectScreen.drawDetails.
            keys.add("char." + id + ".name");
            keys.add("character." + id + ".desc");
        }
        for (String id : Assets.Npc.VILLAGERS) {
            keys.add("npc." + id + ".name");
            keys.add("npc." + id + ".1");
            keys.add("npc." + id + ".2");
        }
        keys.add("npc." + Assets.Npc.HERBALIST + ".broke");
        for (String region : HubScreen.REGIONS) {
            // Built from the role on the map's sprite, so a region added in
            // make_island.py without its lines would be a dialog of raw keys.
            keys.add("npc.worker_" + region + ".name");
            keys.add("npc.worker_" + region + ".1");
            keys.add("npc.worker_" + region + ".2");
        }
        keys.add("prompt.enter_home");
        for (String id : CharacterSelectScreen.FALLBACK_WEAPONS) {
            keys.add("select.weapon." + id);
        }
        for (String k : CreditsScreen.rollKeys()) {
            keys.add(k);
        }
        return keys;
    }

    @Test
    void everyKeyTheScreensAskForIsTranslated() {
        for (String lang : new String[] {"vi", "en"}) {
            Set<String> have = keysOf(lang);
            List<String> missing = new ArrayList<>();
            for (String key : screenKeys()) {
                if (!have.contains(key)) {
                    missing.add(key);
                }
            }
            assertTrue(missing.isEmpty(), "missing from " + lang + ".json: " + missing);
        }
    }

    /** A format string that lost its placeholder shows the player nothing. */
    @Test
    void formatStringsKeepTheirPlaceholders() {
        for (String lang : new String[] {"vi", "en"}) {
            File file = new File("assets/i18n/" + lang + ".json");
            JsonValue root = new JsonReader().parse(new FileHandle(file));
            for (String key : new String[] {"end.banked", "game.floor"}) {
                String value = root.getString(key, "");
                assertTrue(value.contains("{0}"),
                    lang + ".json key '" + key + "' lost its {0}");
            }
        }
    }

    /**
     * Three packs were downloaded and cut for clashing with the art direction,
     * and {@code CREDITS.md} says so. Crediting art that is not in the game is
     * its own kind of misattribution, and the roll is exactly where it would
     * creep back in.
     */
    @Test
    void theRollNamesNobodyWhoseArtWasCut() {
        for (String lang : new String[] {"vi", "en"}) {
            File file = new File("assets/i18n/" + lang + ".json");
            JsonValue root = new JsonReader().parse(new FileHandle(file));
            for (String key : CreditsScreen.rollKeys()) {
                String value = root.getString(key, "").toLowerCase(java.util.Locale.ROOT);
                for (String cut : new String[] {"sprout", "pixel food", "mystic woods",
                                                "ghostpixxells", "cup nooble"}) {
                    assertTrue(!value.contains(cut),
                        "the credits roll names " + cut + ", whose art was cut (" + key + ")");
                }
            }
        }
    }
}
