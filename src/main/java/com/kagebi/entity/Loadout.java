package com.kagebi.entity;

import com.kagebi.combat.Modifiers;
import com.kagebi.data.ContentRegistry;
import com.kagebi.data.ShopCatalog;
import com.kagebi.data.def.GearDef;
import com.kagebi.data.def.GemDef;
import com.kagebi.data.def.RelicDef;
import com.kagebi.data.def.SkillDef;
import com.kagebi.run.RunState;
import com.kagebi.save.OwnedGear;
import com.kagebi.save.Profile;

/**
 * Collects a run's effects from the three places they come from.
 *
 * <p>Relics are picked up during the run, upgrades are bought in the village
 * between runs, and a character's perk comes with the ninja. The content was
 * deliberately written so that all three speak the same effect names, which is
 * why one {@link Modifiers} can hold the lot and combat never has to ask where
 * a number came from.
 *
 * <p>Lives here rather than in {@code combat} so that combat imports no content
 * types at all and stays testable with nothing but numbers.
 */
public final class Loadout {

    /** Upgrade effects, so the shop can be asked for each exactly once. */
    private static final String[] UPGRADE_EFFECTS = {
        "max_hp_add", "melee_damage_mult", "move_speed_mult", "gold_mult",
        "potion_capacity_add", "start_keys_add", "revive_once",
    };

    /**
     * @param shop    may be null, as in a test with no village
     * @param profile may be null for the same reason
     */
    public static Modifiers of(RunState run, ContentRegistry content,
                               ShopCatalog shop, Profile profile) {
        return of(run, content, shop, profile, (SkillDef) null);
    }

    /**
     * @param timed the skills that are up right now, if any - a fifth source,
     *              and the only one that is temporary. They fold in here rather
     *              than being applied and unapplied on the player, because
     *              undoing five multipliers in the right order is a bug waiting
     *              to happen and rebuilding from nothing cannot be.
     *
     *              <p>More than one, because more than one can be: a charge
     *              cast while the ultimate is up is two timed effects at once,
     *              and the two compound like any other pair of sources.
     */
    public static Modifiers of(RunState run, ContentRegistry content,
                               ShopCatalog shop, Profile profile, SkillDef... timed) {
        Modifiers mods = new Modifiers();
        if (run != null && content != null) {
            for (String id : run.relics) {
                // A relic id with no def is content that failed to load, and
                // ContentValidator would have refused to boot. Skipping keeps a
                // test that hands over three defs from having to be complete.
                if (content.hasRelic(id)) {
                    RelicDef relic = content.relic(id);
                    mods.add(relic.effect, relic.magnitude);
                }
            }
        }
        if (shop != null && profile != null) {
            for (String effect : UPGRADE_EFFECTS) {
                float value = shop.upgradeValue(effect, profile);
                // upgradeValue returns the identity when nothing is owned, and
                // folding an identity in would be harmless but for revive_once,
                // where it would hand out a free charge.
                if (value != identity(effect)) {
                    mods.add(effect, value);
                }
            }
            if (run != null) {
                ShopCatalog.Unlock character = shop.character(run.characterId);
                if (character != null && character.effect != null) {
                    mods.add(character.effect, character.magnitude);
                }
            }
        }
        if (profile != null && content != null) {
            worn(mods, content, profile);
        }
        for (SkillDef skill : timed) {
            if (skill == null) {
                continue;
            }
            for (int i = 0; i < skill.effects.length && i < skill.magnitudes.length; i++) {
                mods.add(skill.effects[i], skill.magnitudes[i]);
            }
        }
        return mods;
    }

    /**
     * The fourth source: armour worn, and the stones set into it.
     *
     * <p>Folded in here rather than anywhere nearer the player because this is
     * already the one place that knows how to turn a thing the player owns into
     * a number combat can read. Gear needed no new machinery at all - it names
     * effects from the same vocabulary, and {@link Modifiers} compounds the
     * multiplicative ones and sums the rest without being told which is which.
     *
     * <p>Only what is worn counts. A stash full of Shrine Sandals does nothing
     * until a pair is on, which is the difference between this and the unlock
     * sets above.
     */
    private static void worn(Modifiers mods, ContentRegistry content, Profile profile) {
        for (String slot : profile.equipped.keys()) {
            OwnedGear piece = profile.worn(slot);
            if (piece == null || !content.hasGear(piece.defId)) {
                continue;
            }
            GearDef def = content.gear(piece.defId);
            // Parallel arrays, and the validator has already checked they are
            // the same length; the min is for a def hand-built by a test.
            int pairs = Math.min(def.effects.length, def.magnitudes.length);
            for (int i = 0; i < pairs; i++) {
                mods.add(def.effects[i], def.magnitudes[i]);
            }
            for (String stone : piece.sockets) {
                if (stone != null && content.hasGem(stone)) {
                    GemDef gem = content.gem(stone);
                    mods.add(gem.effect, gem.magnitude);
                }
            }
        }
    }

    private static float identity(String effect) {
        return Modifiers.multiplicative(effect) ? 1f : 0f;
    }

    private Loadout() {}
}
