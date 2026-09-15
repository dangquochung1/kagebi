package com.kagebi.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.badlogic.gdx.files.FileHandle;
import com.kagebi.assets.Assets;

/** The village economy's content, read the way the game reads it. */
class VillageCatalogTest {

    @TempDir
    Path dir;

    @Test
    void theShippedVillageLoadsWhole() {
        VillageCatalog v = VillageCatalog.parse(ContentLoaderTest.DISK.apply(Assets.DATA_DIR));
        assertEquals(17, v.goods().size, "goods");
        assertEquals(11, v.crops().size, "crops");
        assertEquals(3, v.farmLevels().size, "farm levels");
        assertEquals(4, v.workshops().size, "workshops");
        assertEquals(4, v.tools().size, "tools");
        assertEquals(5, v.recipes().size, "recipes");
        for (String region : VillageCatalog.WORKSHOPS) {
            assertNotNull(v.workshop(region), region);
        }
        assertNull(v.good("unobtainium"), "a missing id is null, so the validator can name it");
    }

    /** A typo in an optional-looking field, and a copy-pasted entry, both said out loud. */
    @Test
    void aMisspeltFieldAndAnIdDefinedTwiceAreReportedNotIgnored() throws IOException {
        Files.writeString(dir.resolve("village.json"),
            "{ \"goods\": ["
                + " {\"id\": \"egg\", \"nameKey\": \"good.egg.name\", \"prce\": 4},"
                + " {\"id\": \"egg\", \"nameKey\": \"good.egg.name\", \"price\": 4} ] }",
            StandardCharsets.UTF_8);
        List<String> problems = new ArrayList<>();
        VillageCatalog.parse(new FileHandle(dir.toFile()), problems);
        assertTrue(problems.stream().anyMatch(p -> p.contains("unknown field 'prce'")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("defined twice")), problems.toString());
    }
}
