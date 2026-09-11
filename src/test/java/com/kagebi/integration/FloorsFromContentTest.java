package com.kagebi.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.kagebi.assets.Assets;
import com.kagebi.data.ContentLoader;
import com.kagebi.data.ContentRegistry;
import com.kagebi.data.def.FloorDef;
import com.kagebi.gen.FloorGenerator;
import com.kagebi.gen.FloorLayout;
import com.kagebi.gen.Room;
import com.kagebi.gen.RoomCatalog;
import com.kagebi.gen.RoomKind;
import com.kagebi.gen.RoomTemplate;
import com.kagebi.gen.SpawnPoint;

/**
 * The generator run against the floors the game actually ships, over the rooms
 * actually on disk.
 *
 * <p>Both halves were already tested, and neither test could see this. The
 * content was written without the rooms in its checkout, so it could only check
 * that each floor names <em>some</em> biome; the generator was tested against
 * floors it built by hand, so it could only prove it handles whatever it is
 * given. The mismatch between them - a floor naming a biome with no rooms, or
 * with no boss arena in it - lives exactly in the gap, and on the first
 * integration it very nearly shipped: floors 2 and 3 were written as
 * {@code "ruins"} while the rooms had been split into three colour biomes, so
 * two thirds of the ruins rooms would never have been seen.
 */
class FloorsFromContentTest {

    private static final int SEEDS = 300;

    private static ContentRegistry content;
    private static Array<RoomTemplate> rooms;

    @BeforeAll
    static void load() {
        content = ContentLoader.load(p -> new FileHandle(new File(p)));
        rooms = RoomCatalog.load(new FileHandle(new File(Assets.ROOMS_DIR)));
    }

    /** A floor whose biome has no rooms falls back to any room at all - silently. */
    @Test
    void everyFloorHasRoomsInItsOwnBiome() {
        for (FloorDef floor : content.allFloors()) {
            int count = 0;
            for (RoomTemplate t : rooms) {
                if (t.biome.equals(floor.biome)) {
                    count++;
                }
            }
            assertTrue(count > 0, "floor " + floor.number + " names biome '"
                + floor.biome + "' but no room on disk is in it");
        }
    }

    /**
     * The three ruins floors are meant to look different: that is the whole
     * point of the tileset's colour variants. Sharing a biome between them is
     * legal data and a wasted two thirds of the art.
     */
    @Test
    void theRuinsFloorsDoNotShareABiome() {
        List<String> seen = new ArrayList<>();
        for (int n = 1; n <= 3; n++) {
            String biome = content.floor(n).biome;
            assertFalse(seen.contains(biome),
                "floors 1-3 should each have their own ruins palette; '"
                + biome + "' is used twice");
            seen.add(biome);
        }
    }

    /**
     * Every kind of room the generator can place must have a template in the
     * floor's own biome. When it does not, the generator borrows one from any
     * biome, and a brown-walled shop appears in the middle of the purple depths.
     */
    @Test
    void everyPlacedRoomComesFromTheFloorsBiome() {
        for (FloorDef floor : content.allFloors()) {
            FloorGenerator generator = new FloorGenerator(rooms);
            for (long seed = 0; seed < SEEDS; seed++) {
                FloorLayout layout = generator.generate(floor, seed);
                for (Room room : layout.rooms()) {
                    assertEquals(floor.biome, room.template.biome,
                        "floor " + floor.number + " seed " + seed + ": " + room
                        + " used " + room.template.id);
                    assertTrue(room.template.suits(room.kind),
                        "floor " + floor.number + " seed " + seed + ": " + room
                        + " got " + room.template.id + ", which is not a " + room.kind);
                }
            }
        }
    }

    /** A boss floor with no boss arena, or a boss arena with no boss marker. */
    @Test
    void bossFloorsHaveABossRoomWithSomewhereToPutTheBoss() {
        for (FloorDef floor : content.allFloors()) {
            if (!floor.hasBoss()) {
                continue;
            }
            assertTrue(content.hasEnemy(floor.boss),
                "floor " + floor.number + " boss '" + floor.boss + "' is not an enemy");
            assertTrue(content.enemy(floor.boss).boss,
                "floor " + floor.number + " boss '" + floor.boss + "' is not flagged boss");

            FloorLayout layout = new FloorGenerator(rooms).generate(floor, 1L);
            Room boss = layout.boss();
            assertNotNull(boss, "floor " + floor.number + " generated no boss room");
            boolean marker = false;
            for (SpawnPoint s : boss.template.spawns) {
                marker |= s.kind == SpawnPoint.Kind.ENEMY;
            }
            assertTrue(marker, boss.template.id + " has no ENEMY marker for the boss to stand on");
        }
    }

    /** The last floor has to end somewhere the victory screen can be reached from. */
    @Test
    void theDungeonHasAnEnd() {
        Array<FloorDef> floors = content.allFloors();
        FloorDef last = floors.peek();
        FloorLayout layout = new FloorGenerator(rooms).generate(last, 7L);
        assertNotNull(layout.exit(), "floor " + last.number + " has no exit or boss room");
        assertTrue(layout.exit().kind == RoomKind.BOSS || layout.exit().kind == RoomKind.EXIT);
    }
}
