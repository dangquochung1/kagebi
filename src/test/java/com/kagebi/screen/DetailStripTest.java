package com.kagebi.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.function.Function;

import com.badlogic.gdx.files.FileHandle;
import com.kagebi.data.ContentLoader;
import com.kagebi.data.ContentRegistry;
import com.kagebi.data.ContentValidator;
import com.kagebi.data.def.GearDef;
import com.kagebi.data.def.GemDef;

/**
 * The numbers the detail strip prints, without a screen to print them on.
 *
 * <p>One float on a {@code GearDef} means three different things depending on
 * its effect's name, and the strip is the first place in the game where a
 * player reads any of them. Getting it wrong is not a crash, it is a helmet
 * that claims to add eight per cent health when it adds eight points.
 */
class DetailStripTest {

    /** Plain files standing in for Gdx.files, as ContentLoaderTest does. */
    private static final Function<String, FileHandle> DISK =
        p -> new FileHandle(new File(p));

    @Test
    void aFactorReadsAsThePercentageItAdds() {
        assertEquals("+8%", CharacterScreen.formatEffect("damage_mult", 1.08f));
        assertEquals("+20%", CharacterScreen.formatEffect("gold_mult", 1.2f));
        // Armour is the one gear effect that goes down to help you.
        assertEquals("-10%", CharacterScreen.formatEffect("armour_mult", 0.9f));
    }

    @Test
    void aChanceReadsAsAPercentageOfItself() {
        assertEquals("+2%", CharacterScreen.formatEffect("crit_chance_add", 0.02f));
        assertEquals("+5%", CharacterScreen.formatEffect("lifesteal", 0.05f));
    }

    @Test
    void aFlatAddendReadsAsItself() {
        assertEquals("+8", CharacterScreen.formatEffect("max_hp_add", 8f));
        assertEquals("+4", CharacterScreen.formatEffect("armour_add", 4f));
    }

    /**
     * The content only ever names effects the strip has a label for.
     *
     * <p>{@code ScreenContractTest} is what proves a label exists for every
     * name in {@code GEAR_EFFECTS}, in all four i18n files. This is the other
     * half: that no shipped piece of gear or stone names anything outside that
     * set. Without one of the two, the strip can print a raw key across a 148px
     * column - which looks like a broken game rather than an empty field.
     */
    @Test
    void everyEffectTheContentUsesIsOneTheStripKnows() {
        ContentRegistry reg = ContentLoader.load(DISK);
        for (GearDef g : reg.allGear()) {
            for (String effect : g.effects) {
                assertTrue(ContentValidator.GEAR_EFFECTS.contains(effect), g.id + " " + effect);
            }
        }
        for (GemDef gem : reg.allGems()) {
            assertTrue(ContentValidator.GEAR_EFFECTS.contains(gem.effect), gem.id);
        }
    }
}
