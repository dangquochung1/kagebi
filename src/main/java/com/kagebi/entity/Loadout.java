package com.kagebi.entity;

import com.kagebi.combat.Modifiers;
import com.kagebi.data.ContentRegistry;
import com.kagebi.data.ShopCatalog;
import com.kagebi.data.def.RelicDef;
import com.kagebi.run.RunState;
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
        return mods;
    }

    private static float identity(String effect) {
        return Modifiers.multiplicative(effect) ? 1f : 0f;
    }

    private Loadout() {}
}
