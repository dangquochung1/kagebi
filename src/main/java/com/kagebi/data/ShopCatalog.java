package com.kagebi.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.kagebi.assets.Assets;
import com.kagebi.run.RunSummary;
import com.kagebi.save.Profile;
import com.kagebi.save.Progression;

/**
 * The village shop: permanent upgrades bought with banked gold, and the
 * characters and weapons that gold and a milestone open together.
 *
 * <p>This lives beside {@link ContentRegistry} rather than inside it only
 * because the registry's shape was frozen for this milestone. It is authored
 * the same way - the {@code upgrades} and {@code unlocks} sections of the
 * content directory - and checked by the same validator at boot.
 *
 * <p>Everything that decides a price or a requirement is a plain method over a
 * {@link Profile}, with no Gdx in it, so the shop's rules are tested the same
 * way the loot tables are.
 */
public final class ShopCatalog {

    /**
     * What a shop entry hands over.
     *
     * <p>There was a third kind, {@code SKIN}, for the ninja colours while they
     * were cosmetic. They carry perks again and are characters again, so a
     * colour is bought the same way a body is and there is one fewer set for a
     * chest to file a prize into by mistake.
     */
    public enum UnlockKind { CHARACTER, WEAPON }

    /**
     * What an unlock can ask of the profile. Kept beside {@link
     * #requirementMet}, which is the only code that reads them, so the two
     * cannot drift apart; the validator checks the JSON against this set.
     */
    public static final Set<String> REQUIREMENTS =
        Set.of("none", "deepest_floor", "wins", "runs", "bestiary");

    /** One upgrade track: vigor, edge, and so on. */
    public static final class Upgrade {
        public final String id;
        public final String nameKey;
        public final String descKey;
        public final int icon;
        public final int maxLevel;
        /** Price of level 1, 2, ... - one entry per level. */
        public final int[] costs;
        public final String effect;
        public final float magnitudePerLevel;

        public Upgrade(String id, String nameKey, String descKey, int icon, int maxLevel,
                       int[] costs, String effect, float magnitudePerLevel) {
            this.id = id;
            this.nameKey = nameKey;
            this.descKey = descKey;
            this.icon = icon;
            this.maxLevel = maxLevel;
            this.costs = costs;
            this.effect = effect;
            this.magnitudePerLevel = magnitudePerLevel;
        }

        @Override
        public String toString() {
            return "Upgrade(" + id + ")";
        }
    }

    /** A character or weapon that starts locked. */
    public static final class Unlock {
        public final String id;
        public final UnlockKind kind;
        public final String nameKey;
        public final String descKey;
        public final int icon;
        public final int cost;
        public final String requirement;
        public final int requirementValue;
        /** A character's run-start perk, spelt as an upgrade effect; null for weapons. */
        public final String effect;
        public final float magnitude;

        public Unlock(String id, UnlockKind kind, String nameKey, String descKey, int icon,
                      int cost, String requirement, int requirementValue,
                      String effect, float magnitude) {
            this.id = id;
            this.kind = kind;
            this.nameKey = nameKey;
            this.descKey = descKey;
            this.icon = icon;
            this.cost = cost;
            this.requirement = requirement;
            this.requirementValue = requirementValue;
            this.effect = effect;
            this.magnitude = magnitude;
        }

        @Override
        public String toString() {
            return "Unlock(" + id + ")";
        }
    }

    private final Array<Upgrade> upgrades = new Array<>();
    private final Array<Unlock> unlocks = new Array<>();

    public ShopCatalog() {}

    // ---- loading -------------------------------------------------------------

    /** For the village screen. ContentLoader has already validated this at boot. */
    public static ShopCatalog load() {
        return parse(Gdx.files.internal(Assets.DATA_DIR));
    }

    public static ShopCatalog parse(FileHandle dataDir) {
        List<String> problems = new ArrayList<>();
        ShopCatalog out = parse(dataDir, problems);
        ContentValidator.throwIfAny(problems);
        return out;
    }

    /**
     * Reads the same directory ContentLoader does, so a broken file is reported
     * by both; {@link ContentValidator#throwIfAny} drops the repeats.
     */
    static ShopCatalog parse(FileHandle dataDir, List<String> problems) {
        DataFiles data = DataFiles.read(dataDir, problems);
        ShopCatalog out = new ShopCatalog();
        for (Fields f : data.objects(DataFiles.UPGRADES, "upgrade")) {
            String id = f.id();
            out.add(new Upgrade(id, f.string("nameKey"), f.string("descKey"),
                f.icon("icon", data.icons), f.integer("maxLevel"), f.integers("costs"),
                f.string("effect"), f.number("magnitudePerLevel")));
            f.done();
        }
        for (Fields f : data.objects(DataFiles.UNLOCKS, "unlock")) {
            String id = f.id();
            out.add(new Unlock(id, f.enumeration("kind", UnlockKind.class),
                f.string("nameKey"), f.string("descKey"), f.icon("icon", data.icons),
                f.integer("cost"), f.string("requirement"), f.integerOr("requirementValue", 0),
                f.stringOr("effect", null), f.numberOr("magnitude", 0f)));
            f.done();
        }
        return out;
    }

    public void add(Upgrade u) {
        upgrades.add(u);
    }

    public void add(Unlock u) {
        unlocks.add(u);
    }

    public Array<Upgrade> upgrades() {
        return upgrades;
    }

    public Array<Unlock> unlocks() {
        return unlocks;
    }

    public Upgrade upgrade(String id) {
        for (Upgrade u : upgrades) {
            if (u.id.equals(id)) {
                return u;
            }
        }
        throw new IllegalArgumentException("no upgrade with id '" + id + "'");
    }

    public Unlock unlock(String id) {
        for (Unlock u : unlocks) {
            if (u.id.equals(id)) {
                return u;
            }
        }
        throw new IllegalArgumentException("no unlock with id '" + id + "'");
    }

    // ---- rules ---------------------------------------------------------------

    /** Price of the next level, or -1 once the track is full. */
    public static int cost(Upgrade u, Profile p) {
        int level = p.upgrade(u.id);
        return level >= u.maxLevel ? -1 : u.costs[level];
    }

    public static boolean canBuy(Upgrade u, Profile p) {
        int price = cost(u, p);
        return price >= 0 && p.gold >= price;
    }

    /** Debits the gold and raises the level, or changes nothing and says so. */
    public static boolean buy(Upgrade u, Profile p) {
        if (!canBuy(u, p)) {
            return false;
        }
        p.gold -= cost(u, p);
        p.upgrades.getAndIncrement(u.id, 0, 1);
        return true;
    }

    public static boolean owned(Unlock u, Profile p) {
        return switch (u.kind) {
            case CHARACTER -> p.unlockedCharacters.contains(u.id);
            default -> p.unlockedWeapons.contains(u.id);
        };
    }

    public static boolean requirementMet(Unlock u, Profile p) {
        return requirementMet(u.requirement, u.requirementValue, p);
    }

    /**
     * The same rule, for anything else gated on a milestone.
     *
     * <p>The forge's lines are, and they are not unlocks - there is no shelf
     * entry to hand in, just a recipe that appears once the player has been
     * deep enough. Two copies of this switch would be two places to add the
     * next requirement name to, and the second one would be forgotten.
     */
    public static boolean requirementMet(String requirement, int value, Profile p) {
        switch (requirement) {
            case "none":
                return true;
            case "deepest_floor":
                return p.deepestFloor >= value;
            case "wins":
                return p.wins >= value;
            case "runs":
                return p.runs >= value;
            case "bestiary":
                return p.bestiary.size >= value;
            default:
                // Unreachable for validated content; false rather than true so
                // that a typo can never give something away.
                return false;
        }
    }

    /**
     * Puts an unlock into the set that owns it, without charging for it.
     *
     * <p>Split out of {@link #buy} because a chest gives one away, and the
     * chest used to carry its own copy of this mapping. The two drifted apart
     * once already: a third kind was added, the shop learned about it and the
     * chest did not, so a chest that rolled one filed it under weapons - where
     * {@link #owned} would never find it, and the same prize could be won again
     * every floor.
     */
    public static void grant(Unlock u, Profile p) {
        switch (u.kind) {
            case CHARACTER -> p.unlockedCharacters.add(u.id);
            default -> p.unlockedWeapons.add(u.id);
        }
    }

    public static boolean canBuy(Unlock u, Profile p) {
        return !owned(u, p) && requirementMet(u, p) && p.gold >= u.cost;
    }

    public static boolean buy(Unlock u, Profile p) {
        if (!canBuy(u, p)) {
            return false;
        }
        p.gold -= u.cost;
        grant(u, p);
        return true;
    }

    /**
     * The combined value of every upgrade with this effect, at the levels the
     * profile owns. An effect ending in {@code _mult} compounds - two levels of
     * 1.08 is 1.1664 - and anything else adds, so the identity is 1 for the
     * first kind and 0 for the second. Run start calls this once per effect.
     */
    public float upgradeValue(String effect, Profile p) {
        boolean mult = effect.endsWith("_mult");
        float value = mult ? 1f : 0f;
        for (Upgrade u : upgrades) {
            if (!u.effect.equals(effect)) {
                continue;
            }
            int level = Math.min(p.upgrade(u.id), u.maxLevel);
            value = mult
                ? value * (float) Math.pow(u.magnitudePerLevel, level)
                : value + u.magnitudePerLevel * level;
        }
        return value;
    }

    /** A character's run-start perk, or null for one without an unlock entry. */
    public Unlock character(String characterId) {
        for (Unlock u : unlocks) {
            if (u.kind == UnlockKind.CHARACTER && u.id.equals(characterId)) {
                return u;
            }
        }
        return null;
    }

    /**
     * What the game-over and victory screens call. Kept here rather than in
     * the screens so there is one way to write a run into a profile; the
     * arithmetic itself is {@link Progression#bank}, which knows nothing of
     * the shop.
     */
    public void bank(Profile p, RunSummary summary) {
        Progression.bank(p, summary);
    }

    /**
     * As {@link #bank}, for a stage cleared that is not the last one. It comes
     * through here for the same reason: the end screens keep having exactly
     * one way to write a run into a profile.
     */
    public void bankStage(Profile p, RunSummary summary) {
        Progression.bankStage(p, summary);
    }
}
