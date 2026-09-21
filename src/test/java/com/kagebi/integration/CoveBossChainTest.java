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
import com.kagebi.entity.Enemy;
import com.kagebi.entity.EntityWorld;
import com.kagebi.entity.ScriptedInput;
import com.kagebi.gen.FloorGenerator;
import com.kagebi.gen.FloorLayout;
import com.kagebi.gen.Room;
import com.kagebi.gen.RoomCatalog;
import com.kagebi.gen.RoomKind;
import com.kagebi.gen.RoomTemplate;
import com.kagebi.run.RunState;

/**
 * The Drowned Cove's boss is one fight with five bodies. This walks it.
 *
 * <p>{@code FullRunTest} already proves the arena can be cleared, which is the
 * structural question. It cannot say <em>how</em> it was cleared: a chain that
 * quietly lost its third body, or skipped an intermission, or summoned the
 * wrong thing would clear just as green and rather faster. The order the
 * player meets these five in is the content, so it is asserted here.
 *
 * <p>Headless, with a null atlas: every sprite comes back null, the render
 * pass becomes a no-op, and the simulation runs at the real fixed step. The
 * boss is killed by fiat rather than by swinging at it, because what is under
 * test is the chain and not the player's damage.
 */
final class CoveBossChainTest {

    /** Long enough for two eight-second intermissions and some slack. */
    private static final int BUDGET = 60 * 60;

    private static ContentRegistry content;
    private static Array<RoomTemplate> rooms;
    private static FloorDef cove;

    @BeforeAll
    static void load() {
        content = ContentLoader.load(p -> new FileHandle(new File(p)));
        rooms = RoomCatalog.load(new FileHandle(new File("assets/maps/rooms")));
        for (FloorDef f : content.allFloors()) {
            if ("cove".equals(f.biome)) {
                cove = f;
            }
        }
        assertNotNull(cove, "no floor uses the cove biome");
        assertNotNull(cove.boss, "the cove has no boss");
    }

    /**
     * Every body appears, in order, and the fight ends with the last of them.
     *
     * <p>The two orbs are in the list on purpose. They are what the player
     * spends sixteen seconds dodging, and dropping them from the chain - by
     * summoning the next body directly on death - is the single easiest way
     * for this fight to quietly become three fights in a row.
     */
    @Test
    void theFiveBodiesArriveInOrder() {
        EntityWorld world = arena();
        ScriptedInput input = new ScriptedInput();
        List<String> seen = new ArrayList<>();
        String last = null;

        for (int t = 0; t < BUDGET && world.hostilesAlive() > 0; t++) {
            Enemy boss = world.boss();
            if (boss != null && !boss.def.id.equals(last)) {
                last = boss.def.id;
                seen.add(last);
                // Straight to zero. The bot's swing is FullRunTest's subject;
                // here the only question is what stands up next.
                boss.takeHit(boss.hp, boss.x, boss.y, 0f);
            }
            input.tick(world);
        }

        assertEquals(List.of("pirateleader", "fireorb", "piratezombie",
                             "waterorb", "squidman"), seen);
        StringBuilder left = new StringBuilder();
        for (Enemy e : world.hostiles()) {
            if (e.alive()) {
                left.append(e.def.id).append('@').append(e.state()).append(' ');
            }
        }
        assertEquals(0, world.hostilesAlive(),
            "the last body of the chain left something behind it: " + left);
    }

    /**
     * The room stays shut for the whole chain.
     *
     * <p>The one failure mode that would not look like a failure. A room
     * latches itself cleared the moment nothing hostile is in it and never
     * unlatches: if a body ever died on one step and its successor arrived on
     * the next, the doors would open, the stairs would light, and the player
     * could walk out of a boss fight two bodies early - permanently, because
     * re-entering a cleared room spawns nothing.
     */
    @Test
    void theRoomIsNeverEmptyBetweenBodies() {
        EntityWorld world = arena();
        ScriptedInput input = new ScriptedInput();
        String last = null;

        for (int t = 0; t < BUDGET; t++) {
            Enemy boss = world.boss();
            boolean lastBody = boss != null && boss.def.evolvesInto == null;
            if (boss != null && !boss.def.id.equals(last)) {
                last = boss.def.id;
                boss.takeHit(boss.hp, boss.x, boss.y, 0f);
            }
            input.tick(world);
            if (lastBody && world.hostilesAlive() == 0) {
                return;                 // the fight is over, and only then
            }
            assertTrue(world.hostilesAlive() > 0,
                "the arena emptied at step " + t + ", after " + last);
        }
        assertFalse(true, "the chain never finished inside " + BUDGET + " steps");
    }

    /** An intermission is eight seconds of something to dodge, not a pause. */
    @Test
    void anOrbRainsWhileItCannotBeTouched() {
        EntityWorld world = arena();
        ScriptedInput input = new ScriptedInput();
        Enemy first = world.boss();
        assertNotNull(first);
        first.takeHit(first.hp, first.x, first.y, 0f);
        input.tick(world);

        Enemy orb = world.boss();
        assertNotNull(orb, "nothing replaced the first body");
        assertEquals("fireorb", orb.def.id);

        int mostAtOnce = 0;
        for (int t = 0; t < 8 * 60 - 2; t++) {
            input.tick(world);
            assertTrue(orb.invulnerable(), "the orb was hittable at step " + t);
            mostAtOnce = Math.max(mostAtOnce, world.projectiles().size);
        }
        assertTrue(mostAtOnce >= 2,
            "only " + mostAtOnce + " spells were ever in the air at once");
    }

    /**
     * At 40% the second body calls for help, and the help wakes up.
     *
     * <p>Both halves matter and only one of them is obvious. A summoned body
     * is put down asleep so the fireball that called it can play over
     * something rather than over nothing - and dormant means still, silent and
     * harmless. Nothing in the room is watching the clock for it, so a body
     * that is never woken is a statue, and a boss calling for help three times
     * fills its own arena with furniture. That failure is invisible: the
     * slimes are there, they look right, and they simply never do anything.
     */
    @Test
    void theEnragedBodyCallsForHelpAndTheHelpWakesUp() {
        EntityWorld world = arena();
        ScriptedInput input = new ScriptedInput();

        Enemy first = world.boss();
        assertNotNull(first);
        first.takeHit(first.hp, first.x, first.y, 0f);
        for (int t = 0; t < 8 * 60 + 8; t++) {
            input.tick(world);          // through the whole first intermission
        }

        Enemy zombie = world.boss();
        assertNotNull(zombie, "the fire orb never handed over");
        assertEquals("piratezombie", zombie.def.id);

        // Just past the 40% mark, and one step for the boss to notice.
        zombie.takeHit(zombie.hp - (int) (zombie.maxHp * 0.4f) + 1,
                       zombie.x, zombie.y, 0f);
        input.tick(world);
        input.tick(world);

        int adds = 0;
        for (Enemy e : world.hostiles()) {
            if ("slimeember".equals(e.def.id) && e.alive()) {
                adds++;
            }
        }
        assertTrue(adds >= 2, "the enrage called in only " + adds + " slimes");

        for (int t = 0; t < EntityWorld.SUMMON_SLEEP + 4; t++) {
            input.tick(world);
        }
        for (Enemy e : world.hostiles()) {
            if ("slimeember".equals(e.def.id) && e.alive()) {
                assertFalse(e.dormant,
                    "a summoned slime was still asleep "
                    + EntityWorld.SUMMON_SLEEP + " steps after the fireball");
            }
        }
    }

    /** A cove arena with its boss in it, and nothing else. */
    private static EntityWorld arena() {
        RunState run = new RunState(4242L, Assets.Actor.DEFAULT_CHARACTER, "katana", 9999);
        run.floor = cove.number;
        EntityWorld world = new EntityWorld(null, content, run, null);
        FloorLayout layout = new FloorGenerator(rooms).generate(cove, 4242L);
        run.layout = layout;
        Room arena = null;
        for (Room r : layout.rooms()) {
            if (r.kind == RoomKind.BOSS) {
                arena = r;
            }
        }
        assertNotNull(arena, "the cove generated no boss arena");
        run.room = arena;
        world.enterRoom(arena, RoomCollision.of(arena.template.path), null);
        assertTrue(world.hostilesAlive() > 0, "the arena spawned nothing");
        return world;
    }

}
