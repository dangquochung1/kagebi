package com.kagebi.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.kagebi.Dir;
import com.kagebi.assets.Assets;
import com.kagebi.data.ContentLoader;
import com.kagebi.data.ContentRegistry;
import com.kagebi.data.def.FloorDef;
import com.kagebi.entity.EntityWorld;
import com.kagebi.gen.CollisionGrid;
import com.kagebi.gen.FloorGenerator;
import com.kagebi.gen.FloorLayout;
import com.kagebi.gen.Room;
import com.kagebi.gen.RoomCatalog;
import com.kagebi.gen.RoomKind;
import com.kagebi.gen.RoomTemplate;
import com.kagebi.gen.SpawnPoint;
import com.kagebi.run.RunState;

/**
 * Somebody plays the game, start to finish, before anybody claims it works.
 *
 * <p>Every other test in this project checks a part. The generator makes
 * floors, the content balances, a room clears, a relic multiplies correctly -
 * all true, all green, and none of them can answer the question the whole
 * project rests on: can a player get from the first room of floor one to the
 * end of floor five? That spans generation, content, combat and the room
 * graph, which is to say it belongs to no package and so to no unit test.
 *
 * <p>A {@link Bot} plays it. It walks at the nearest enemy and swings; it never
 * rolls, never drinks, never retreats. Two different questions come out of
 * that, and mixing them up is how a test like this starts lying:
 *
 * <ul>
 * <li><b>Is the game finishable?</b> Asked with the bot made unkillable, so a
 *     failure can only mean a room that cannot be cleared or reached - a
 *     structural break. That is a bug, always.
 * <li><b>Is the game survivable?</b> Asked at real starting health. A failure
 *     here means the crudest possible play died somewhere, which is a
 *     difficulty reading, not a defect - the answer is printed rather than
 *     asserted, except on floor one.
 * </ul>
 */
class FullRunTest {

    /**
     * Steps a single room may take before it counts as unclearable. 60/s.
     *
     * <p>Three minutes, which is far longer than any room should need and is
     * meant to be. This budget answers "can it be cleared at all"; how long a
     * fight takes is a different question and is asserted separately in
     * {@link #noBossOutlastsThePlayersPatience}, with a number rather than a
     * timeout. Set close to the real fight length, this constant would quietly
     * turn every balance change into a mysterious failure here.
     */
    private static final int ROOM_BUDGET = 60 * 180;
    private static final int WALK_BUDGET = 60 * 20;

    /**
     * The longest a boss may take to kill with the weapon the player starts
     * with, swinging without pause.
     *
     * <p>Two minutes of uninterrupted damage is already a four-minute fight in
     * practice, because a person spends half a boss fight not attacking - and
     * the starting katana is the worst case, not the expected one. Nothing had
     * ever measured this: the content tunes hit points and the balance model
     * tunes the health economy, and neither of them multiplies out to a
     * duration.
     */
    private static final int BOSS_PATIENCE_STEPS = 60 * 120;

    /**
     * Health for the structural tests. Not "cheating": the question those ask
     * is whether every enemy can be reached and killed at all, and a bot that
     * dies cannot answer it. Balance is measured separately, below and in
     * {@code BalanceTest}.
     */
    private static final int UNKILLABLE = 1_000_000;

    private static ContentRegistry content;
    private static Array<RoomTemplate> rooms;
    private static final Map<String, CollisionGrid> GRIDS = new HashMap<>();

    @BeforeAll
    static void load() {
        content = ContentLoader.load(p -> new FileHandle(new File(p)));
        rooms = RoomCatalog.load(new FileHandle(new File(Assets.ROOMS_DIR)));
        assertTrue(rooms.size > 0, "no room templates on disk");
        assertTrue(content.allFloors().size > 0, "no floors in content");
    }

    // ---- is it finishable ---------------------------------------------------

    /**
     * Every room between the entrance and the stairs, on every floor, can be
     * cleared - and the stairs can then be stood on.
     *
     * <p>This is the test that says the game has an ending. A failure is one of
     * three things and all three are bugs: an enemy that cannot be reached, an
     * enemy that cannot be killed, or a floor whose stairs no path leads to.
     */
    @Test
    void everyFloorCanBeClearedFromItsEntranceToItsStairs() {
        RunState run = new RunState(20260912L, Assets.Actor.DEFAULT_CHARACTER,
                                    "katana", UNKILLABLE);
        EntityWorld world = new EntityWorld(null, content, run, null);
        Bot bot = new Bot(world);
        List<String> log = new ArrayList<>();

        for (int number = 1; number <= content.allFloors().size; number++) {
            FloorDef def = floorDef(number);
            assertNotNull(def, "no floor " + number + " in content");
            FloorLayout layout = new FloorGenerator(rooms).generate(def, floorSeed(run, number));
            run.floor = number;
            run.layout = layout;

            List<Room> path = pathTo(layout.start(), layout.exit());
            assertNotNull(path, "floor " + number + ": no path from the entrance to the stairs");

            for (Room room : path) {
                run.room = room;
                run.hp = run.maxHp;
                world.enterRoom(room, grid(room), null);
                int steps = bot.clearRoom(ROOM_BUDGET);
                assertTrue(steps >= 0, "floor " + number + " " + room.kind + " "
                    + room.template.id + ": " + bot.diagnosis()
                    + "\n  cleared before it:\n" + String.join("\n", log));
                log.add(String.format("  F%d %-8s %-24s %5.1fs", number, room.kind,
                    room.template.id, steps / 60f));
            }
            assertTrue(world.roomCleared(), "floor " + number + ": the stairs room never cleared");
        }
        System.out.println("every floor clears, entrance to stairs:");
        System.out.println(String.join("\n", log));
        assertEquals(content.allFloors().size, run.floor);
    }

    /**
     * The weapon a run set out with is the weapon it has in every room.
     *
     * <p>Written for a report that an axe chosen before stage 6 was an axe in
     * the first two rooms and a katana from the third on. Nothing in the code
     * can do that - {@code EntityWorld.enterRoom} re-reads
     * {@code run.weaponId} on every entry and {@code Player.setWeapon} is
     * called from nowhere else, so a weapon cannot change unless the run's
     * own field does, and only two screens write it. But "I read the code and
     * it cannot happen" is worth exactly nothing to somebody who watched it
     * happen, so this walks a real floor with a real axe and looks in every
     * room, which is worth something either way: if it passes, the report was
     * a stale build, and if the mechanism is ever introduced it fails here
     * rather than in a player's stage-6 run.
     *
     * <p>Both halves matter. The def is what the swing is made of and the art
     * is what the player sees, and a bug that swapped only the second would be
     * invisible to an assertion about the first.
     */
    @Test
    void theWeaponAtTheDoorIsTheWeaponInEveryRoom() {
        RunState run = new RunState(20260912L, Assets.Actor.DEFAULT_CHARACTER,
                                    "axe", UNKILLABLE);
        EntityWorld world = new EntityWorld(null, content, run, null);
        Bot bot = new Bot(world);

        for (int number = 1; number <= content.allFloors().size; number++) {
            FloorDef def = floorDef(number);
            FloorLayout layout = new FloorGenerator(rooms).generate(def, floorSeed(run, number));
            run.floor = number;
            run.layout = layout;
            List<Room> path = pathTo(layout.start(), layout.exit());
            assertNotNull(path, "floor " + number + ": no path to the stairs");

            int index = 0;
            for (Room room : path) {
                index++;
                run.room = room;
                run.hp = run.maxHp;
                world.enterRoom(room, grid(room), null);
                assertEquals("axe", run.weaponId,
                    "floor " + number + " room " + index + ": the run's own weapon changed");
                assertNotNull(world.player().weapon(), "floor " + number + " room " + index);
                assertEquals("axe", world.player().weapon().id,
                    "floor " + number + " room " + index + " (" + room.template.id
                    + "): went in with an axe, came out holding "
                    + world.player().weapon().id);
                bot.clearRoom(ROOM_BUDGET);
                assertEquals("axe", world.player().weapon().id,
                    "floor " + number + " room " + index + ": the weapon changed mid-fight");
            }
        }
    }

    /**
     * Every boss the content names can actually be killed.
     *
     * <p>Separate from the run above because the run can skip it:
     * {@code FloorLayout.exit()} only falls back to the boss room when the
     * floor has no separate exit, so a boss that never dies could sit on a
     * floor the path walks round.
     */
    @Test
    void everyBossCanBeKilled() {
        int checked = 0;
        for (FloorDef def : content.allFloors()) {
            if (!def.hasBoss()) {
                continue;
            }
            assertTrue(content.hasEnemy(def.boss),
                "floor " + def.number + " names boss '" + def.boss + "', which is not an enemy");
            RunState run = new RunState(77L, Assets.Actor.DEFAULT_CHARACTER, "katana", UNKILLABLE);
            run.floor = def.number;
            EntityWorld world = new EntityWorld(null, content, run, null);
            Room arena = bossRoom(def);
            assertNotNull(arena, "floor " + def.number + ": no boss arena in biome " + def.biome);
            run.room = arena;
            world.enterRoom(arena, grid(arena), null);
            assertTrue(world.hostilesAlive() > 0,
                "floor " + def.number + ": the boss arena spawned nothing");

            Bot bot = new Bot(world);
            int steps = bot.clearRoom(ROOM_BUDGET);
            assertTrue(steps >= 0,
                "floor " + def.number + " boss '" + def.boss + "': " + bot.diagnosis());
            System.out.printf("  boss %-12s floor %d  %5d hp  down in %5.1fs of swinging%n",
                def.boss, def.number, content.enemy(def.boss).maxHp, steps / 60f);
            checked++;
        }
        assertTrue(checked >= 2, "only " + checked + " bosses; the design calls for two");
    }

    /**
     * How long each boss takes, as a number rather than as a timeout.
     *
     * <p>Kept apart from the kill test on purpose. "It dies" and "it dies
     * before the player puts the controller down" fail for different reasons
     * and want different fixes: the first is a broken brain, the second is a
     * hit-point count. Folding the second into a timeout on the first is how a
     * balance change starts failing a structural test.
     */
    @Test
    void noBossOutlastsThePlayersPatience() {
        for (FloorDef def : content.allFloors()) {
            if (!def.hasBoss() || !content.hasEnemy(def.boss)) {
                continue;
            }
            RunState run = new RunState(77L, Assets.Actor.DEFAULT_CHARACTER, "katana", UNKILLABLE);
            run.floor = def.number;
            EntityWorld world = new EntityWorld(null, content, run, null);
            Room arena = bossRoom(def);
            run.room = arena;
            world.enterRoom(arena, grid(arena), null);
            int steps = new Bot(world).clearRoom(ROOM_BUDGET);
            assertTrue(steps >= 0 && steps <= BOSS_PATIENCE_STEPS,
                String.format("boss '%s' on floor %d has %d hp and takes %.0fs of "
                    + "uninterrupted swings with the starting katana, over the %ds "
                    + "this allows. A person spends half a boss fight not attacking, "
                    + "so that is a fight twice this long.",
                    def.boss, def.number, content.enemy(def.boss).maxHp,
                    steps / 60f, BOSS_PATIENCE_STEPS / 60));
        }
    }

    /** The stairs are offered to a player standing on them in a cleared room. */
    @Test
    void theStairsCanBeReachedAndOfferAWayDown() {
        FloorLayout layout = new FloorGenerator(rooms).generate(floorDef(1), 4242L);
        Room exit = layout.exit();
        RunState run = new RunState(4242L, Assets.Actor.DEFAULT_CHARACTER, "katana", UNKILLABLE);
        run.floor = 1;
        run.layout = layout;
        run.room = exit;
        EntityWorld world = new EntityWorld(null, content, run, null);
        world.enterRoom(exit, grid(exit), Dir.LEFT);
        Bot bot = new Bot(world);
        assertTrue(bot.clearRoom(ROOM_BUDGET) >= 0, "could not clear the stairs room");

        float tx = RoomTemplate.PIXEL_WIDTH / 2f;
        float ty = RoomTemplate.PIXEL_HEIGHT / 2f;
        for (SpawnPoint spawn : exit.template.spawns) {
            if (spawn.kind == SpawnPoint.Kind.EXIT) {
                tx = spawn.x;
                ty = spawn.y;
            }
        }
        assertTrue(bot.walkTo(tx, ty, EntityWorld.INTERACT_RANGE, WALK_BUDGET),
            "the bot could not reach the stairs at " + tx + "," + ty
            + "; it stopped at " + world.playerX() + "," + world.playerY());
        assertEquals(EntityWorld.PROMPT_DESCEND, world.promptKey(),
            "standing on the stairs of a cleared room offers no way down");
    }

    // ---- is it survivable ---------------------------------------------------

    /**
     * How far the crudest possible play gets on the starting kit, printed.
     *
     * <p>Only floor one is asserted, and that is a deliberate line: floor one
     * is where a player learns the controls, and if walking up and swinging
     * dies there then the game opens by being unfair. Past that the bot's death
     * says more about the bot - it does not roll, does not drink the potion it
     * is carrying, and does not step out of a boss's telegraph - than about the
     * balance, so the floor it reaches is reported and left for a person to
     * judge against {@code BalanceTest}'s model.
     */
    @Test
    void theFirstFloorIsSurvivableByTheCrudestPlay() {
        RunState run = new RunState(20260912L, Assets.Actor.DEFAULT_CHARACTER, "katana", 100);
        EntityWorld world = new EntityWorld(null, content, run, null);
        Bot bot = new Bot(world);
        List<String> log = new ArrayList<>();
        int reached = 0;

        for (int number = 1; number <= content.allFloors().size; number++) {
            FloorLayout layout = new FloorGenerator(rooms)
                .generate(floorDef(number), floorSeed(run, number));
            run.floor = number;
            run.layout = layout;
            boolean survived = true;
            for (Room room : pathTo(layout.start(), layout.exit())) {
                run.room = room;
                world.enterRoom(room, grid(room), null);
                int before = run.hp;
                int steps = bot.clearRoom(ROOM_BUDGET);
                log.add(String.format("  F%d %-8s %-24s %5.1fs  hp %3d -> %3d",
                    number, room.kind, room.template.id,
                    Math.max(steps, 0) / 60f, before, Math.max(run.hp, 0)));
                if (steps < 0) {
                    log.add("    " + bot.diagnosis().replace("\n", "\n    "));
                    survived = false;
                    break;
                }
            }
            if (!survived) {
                break;
            }
            reached = number;
        }

        System.out.println("no-dodge, no-potion run:");
        System.out.println(String.join("\n", log));
        System.out.println("  cleared through floor " + reached
            + " of " + content.allFloors().size);
        assertTrue(reached >= 1, "a bot that only walks and swings cannot survive floor one:\n"
            + String.join("\n", log));
    }

    // ---- helpers ------------------------------------------------------------

    private static long floorSeed(RunState run, int number) {
        return run.seed ^ (number * 0x9E3779B97F4A7C15L);
    }

    private static FloorDef floorDef(int number) {
        for (FloorDef f : content.allFloors()) {
            if (f.number == number) {
                return f;
            }
        }
        return null;
    }

    private static Room bossRoom(FloorDef def) {
        FloorLayout layout = new FloorGenerator(rooms).generate(def, 31337L);
        for (Room r : layout.rooms()) {
            if (r.kind == RoomKind.BOSS) {
                return r;
            }
        }
        return null;
    }

    /** Shortest room path, doors only. Null when the stairs cannot be walked to. */
    private static List<Room> pathTo(Room from, Room to) {
        Map<Room, Room> cameFrom = new HashMap<>();
        Set<Room> seen = new HashSet<>();
        ArrayDeque<Room> queue = new ArrayDeque<>();
        seen.add(from);
        queue.add(from);
        while (!queue.isEmpty()) {
            Room at = queue.poll();
            if (at == to) {
                List<Room> path = new ArrayList<>();
                for (Room r = to; r != null; r = cameFrom.get(r)) {
                    path.add(r);
                }
                Collections.reverse(path);
                return path;
            }
            for (Dir d : Dir.ALL) {
                Room next = at.neighbour(d);
                if (next != null && seen.add(next)) {
                    cameFrom.put(next, at);
                    queue.add(next);
                }
            }
        }
        return null;
    }

    /**
     * The room's collision, read straight off the .tmx.
     *
     * <p>The game builds this from a {@code TmxMapLoader} map, which creates
     * textures and so needs a GL context. {@link RoomCollision} reads the same
     * layers as XML and gets the same grid with no window.
     */
    private static CollisionGrid grid(Room room) {
        return GRIDS.computeIfAbsent(room.template.path, RoomCollision::of);
    }
}
