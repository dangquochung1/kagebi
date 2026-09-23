package com.kagebi.screen;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.kagebi.assets.Assets;
import com.kagebi.data.ContentLoader;
import com.kagebi.data.def.WeaponDef;
import com.kagebi.input.GameAction;

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

    /**
     * Each thing that can be thrown flies as its own picture. Every throw used
     * to be drawn with the kunai, so a shuriken bought for 350 gold looked
     * exactly like the kunai it replaced.
     */
    @Test
    void everyThrownProjectileHasAPictureOfItsOwn() {
        Set<String> fx = regions("fx");
        Set<String> seen = new HashSet<>();
        for (String id : com.kagebi.data.ContentValidator.PROJECTILES) {
            String region = Assets.Fx.projectile(id);
            assertTrue(fx.contains(region), id + " -> " + region + " is missing from fx.atlas");
            assertTrue(seen.add(region), id + " is drawn with the same picture as another projectile");
        }
    }

    @Test
    void theExitMarkerExists() {
        assertTrue(regions("fx").contains(Assets.Fx.EXIT),
            Assets.Fx.EXIT + " is missing from fx.atlas");
    }

    /**
     * The kit panel draws an icon for every weapon in the content, and falls
     * back to a plain sword for an id it has no icon for. Both paths have to
     * land on something, or a weapon is a blank cell.
     *
     * <p>Driven off the content rather than off a list in the screen. There
     * was such a list, beside a set of {@code select.weapon.*} names that said
     * what {@code weapon.*.name} already said - so a weapon could be renamed
     * in one place and keep its old name in the other.
     */
    private static List<String> weaponIds() {
        List<String> out = new ArrayList<>();
        for (WeaponDef w : ContentLoader.load(p -> new FileHandle(new File(p))).allWeapons()) {
            out.add(w.id);
        }
        return out;
    }

    @Test
    void everyOfferedWeaponHasAnIcon() {
        Set<String> ui = regions("ui");
        List<String> missing = new ArrayList<>();
        for (String id : weaponIds()) {
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
            "confirm.newgame",
            "panel.side.gear", "panel.side.items", "panel.side.chars",
            "panel.side.kit", "panel.side.wear", "panel.side.wield",
            "panel.offhand.none", "slot.WEAPON", "slot.OFFHAND",
            "shop.title", "shop.tab.upgrades", "shop.tab.unlocks", "shop.buy",
            "shop.max", "shop.owned", "shop.poor", "shop.bought",
            "prompt.talk", "prompt.descend", "prompt.escape",
            "prompt.harvest", "prompt.sow", "prompt.no_seed", "prompt.plot_locked",
            "prompt.growing", "prompt.collect", "village.got", "prompt.cook",
            "trade.tab.sell", "trade.tab.buy", "trade.tab.tools", "trade.tab.upgrades",
            "trade.tab.seeds", "trade.tab.kitchen", "trade.sell", "trade.buy", "trade.cook",
            "trade.open", "trade.done", "trade.max", "trade.empty", "trade.held", "trade.seed",
            "trade.grows", "trade.farm_level", "trade.level", "trade.needs", "trade.amount",
            "trade.pantry", "trade.pantry_full", "trade.upgrades.name", "trade.upgrades.desc",
            "bag.title", "bag.tab.items", "bag.tab.tools", "bag.tab.people", "bag.tab.carry",
            "bag.tab.kit", "bag.ripens", "bag.tool_effect", "bag.makes", "bag.waiting", "bag.next",
            "bag.full", "bag.farm", "bag.kit.name", "bag.kit.desc",
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
            // The character sheet's six tabs and the numbers on them.
            "panel.tab.profile", "panel.tab.gear", "panel.tab.bag", "panel.tab.forge",
            "panel.tab.tasks", "panel.tab.foes",
            "panel.empty", "panel.nothing", "panel.equip", "panel.unequip", "panel.forge",
            "panel.worn", "panel.sockets",
            "stat.hp", "stat.damage", "stat.crit", "stat.critdmg", "stat.throw",
            "stat.armour", "stat.attackspeed", "stat.movespeed",
            "slot.HEAD", "slot.BODY", "slot.HANDS", "slot.FEET", "slot.TRINKET",
            "foes.unmet",
            // Jobs: the journal, and what a villager says about one.
            "quest.taken", "quest.handin", "quest.track", "quest.tracked",
            "quest.done", "quest.none",
            // The code box.
            "menu.code", "code.title", "code.hint", "code.ok", "code.again", "code.bad",
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
        keys.add("hub.zoom.fit");
        // The two lines the shift-held skill panel prints under a description.
        keys.add("skill.info.cooldown");
        keys.add("skill.info.duration");
        keys.add("skill.info.passive");
        for (GameAction action : GameAction.values()) {
            // Listed by the controls screen straight off the enum, so a new
            // action is a raw key there until someone translates it.
            keys.add(action.i18nKey);
        }
        for (String k : CreditsScreen.rollKeys()) {
            keys.add(k);
        }
        // The detail strip prints one of these beside every number it shows.
        // Without a label it prints the raw key, which reads as a broken game
        // rather than as an empty field.
        for (String effect : com.kagebi.data.ContentValidator.GEAR_EFFECTS) {
            keys.add("effect." + effect);
        }
        keys.add("detail.tier");
        keys.add("detail.reach");
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
     * Every pack the roll names is a pack {@code CREDITS.md} names.
     *
     * <p>Crediting art that is not in the game is its own kind of
     * misattribution, and a pack that is tried and then cut leaves the roll
     * last - the art goes, the credit stays, and nothing says so. This used to
     * be a hard-coded list of the packs that had been cut, which only caught
     * the ones somebody remembered to write down, and which kept their names in
     * a repository whose whole point was not to carry them. Checking against
     * {@code CREDITS.md} instead says the thing the screen's own javadoc claims:
     * that file is the authority.
     *
     * <p>Matched on the title's longest word rather than the whole string,
     * because one roll entry deliberately summarises five CraftPix packs in a
     * line that appears nowhere in the table.
     */
    @Test
    void everyPackTheRollNamesIsInCreditsMd() throws Exception {
        String credits = Files.readString(new File("CREDITS.md").toPath(),
                                          StandardCharsets.UTF_8)
                              .toLowerCase(java.util.Locale.ROOT);
        for (String lang : new String[] {"vi", "en"}) {
            File file = new File("assets/i18n/" + lang + ".json");
            JsonValue root = new JsonReader().parse(new FileHandle(file));
            for (String key : CreditsScreen.rollNameKeys()) {
                String title = root.getString(key, "");
                assertTrue(!title.isEmpty(), lang + ".json has no " + key);
                String word = longestWord(title);
                assertTrue(credits.contains(word.toLowerCase(java.util.Locale.ROOT)),
                    "the credits roll names '" + title + "' (" + key + ", " + lang
                        + "), which CREDITS.md does not: no '" + word + "' in it");
            }
        }
    }

    /** The most distinctive part of a pack title: its longest run of letters. */
    private static String longestWord(String title) {
        String best = "";
        StringBuilder word = new StringBuilder();
        for (int i = 0; i <= title.length(); i++) {
            if (i < title.length() && Character.isLetterOrDigit(title.charAt(i))) {
                word.append(title.charAt(i));
                continue;
            }
            if (word.length() > best.length()) {
                best = word.toString();
            }
            word.setLength(0);
        }
        return best;
    }
}
