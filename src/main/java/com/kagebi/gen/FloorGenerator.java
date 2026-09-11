package com.kagebi.gen;

import com.badlogic.gdx.utils.Array;
import com.kagebi.Dir;
import com.kagebi.data.def.FloorDef;

import java.util.Arrays;
import java.util.Random;

/**
 * Builds a floor's room graph from a {@link FloorDef} and a seed.
 *
 * <p>The model is Binding of Isaac's: a controlled random walk on a grid grows
 * the spine of the floor, and the rooms off the critical path - treasure, shop,
 * the locked room, the secret room - are hung off it afterwards as dead ends.
 * Attaching them last, and never growing anything <em>from</em> them, is what
 * makes the central invariant true by construction rather than by luck:
 * nothing the player needs can end up behind a door they cannot open.
 *
 * <p>The walk refuses any cell that already touches two placed rooms, so the
 * graph is a tree and grid adjacency is exactly the set of doors. That is a
 * readability decision more than a technical one: a minimap of a tree can be
 * read at a glance, a floor full of loops cannot. The one exception is the
 * secret room, dropped deliberately where it touches as many rooms as
 * possible, the way Isaac does it, so that finding it is worth something.
 *
 * <p><b>Pure.</b> No {@code Gdx.} and no graphics below - the only libGDX type
 * is {@code Array}, a plain collection. That is what lets
 * {@code FloorGeneratorTest} hammer this with five thousand seeds in an
 * ordinary JUnit run with no GL context. It matters more here than anywhere
 * else in the game: an unreachable boss room never shows up in hand testing,
 * because hand testing is done by someone who already knows the way.
 */
public final class FloorGenerator {

    /** Rooms the walk lays in a straight line out of the start before it may branch. */
    private static final int SPINE = 2;

    /**
     * One hidden room and one key room per floor, budget permitting. Neither
     * is in {@link FloorDef} because nobody has yet asked for them to vary by
     * floor; when that changes they belong in the JSON, not here.
     */
    private static final int SECRET_ROOMS = 1;
    private static final int LOCKED_ROOMS = 1;

    /**
     * Attempts per room before the walk gives up. The grid is sized so the
     * walk cannot actually stall (see {@link #sideFor}); this exists so that a
     * nonsense FloorDef produces an exception with a seed in it rather than a
     * hung test run.
     */
    private static final int ATTEMPTS_PER_ROOM = 10_000;

    /**
     * Whole-floor retries before accepting a floor short of its side rooms.
     * See {@link #plan} for why a retry is ever needed. The worst seed seen
     * across 25,000 - five floors of 8 to 16 rooms, 5,000 seeds each - took
     * five attempts, so fifty is headroom, not a tuning knob. The last attempt
     * is still built and checked, so a floor that never fits fails loudly in
     * {@link #check} rather than looping.
     */
    private static final int FLOOR_ATTEMPTS = 50;

    private final Array<RoomTemplate> templates;

    public FloorGenerator(Array<RoomTemplate> templates) {
        this.templates = templates;
    }

    public FloorLayout generate(FloorDef floor, long seed) {
        Random rng = new Random(streamFor(seed, floor.number));

        // The room count is rolled once: it is the designer's number, and
        // re-rolling it on a retry would quietly bias floors towards the small
        // end, since big floors are the ones that run out of space.
        int lo = Math.max(1, floor.roomsMin);
        int hi = Math.max(lo, floor.roomsMax);
        int target = lo + (hi > lo ? rng.nextInt(hi - lo + 1) : 0);
        int side = sideFor(target);

        RoomKind[] kinds = null;
        Array<int[]> order = null;
        for (int attempt = 0; attempt < FLOOR_ATTEMPTS; attempt++) {
            kinds = new RoomKind[side * side];
            order = new Array<>();
            if (plan(kinds, order, side, target, floor, seed, rng)) {
                break;
            }
        }

        FloorLayout layout = build(kinds, order, side, floor, seed, rng);
        check(layout, floor);
        return layout;
    }

    /**
     * One attempt at the floor's shape. Returns false if the side rooms could
     * not all be hung off it, and the caller tries again.
     *
     * <p>That can happen, and it is geometry rather than a bug. Side rooms go
     * only on cells that touch exactly one plain room; on a compact tree the
     * side rooms placed first can take the only such cells there are, and then
     * the shop has nowhere legal to go. A retry from the same random stream is
     * the cheap answer - Isaac does the same - and it stays deterministic,
     * because the stream is the seed's. Failures other than running out of
     * space are not retried here: those are bugs, and {@link #check} throws.
     */
    private boolean plan(RoomKind[] kinds, Array<int[]> order, int side, int target,
                         FloorDef floor, long seed, Random rng) {
        int specials = floor.treasureRooms + floor.shopRooms
            + SECRET_ROOMS + LOCKED_ROOMS;
        // At least start + spine + one more, so the boss has somewhere to be
        // other than the doorstep of the start; the side rooms take whatever
        // budget is left. On a floor too small for all of them, the last in
        // line - the secret room, then the locked room, then shops - go first.
        int spine = Math.max(Math.min(target, SPINE + 2), target - specials);
        walk(kinds, order, side, spine, rng, floor, seed);

        int bossAt = pickBoss(kinds, order, side, distances(kinds, order, side), target);
        if (bossAt >= 0) {
            kinds[bossAt] = floor.hasBoss() ? RoomKind.BOSS : RoomKind.EXIT;
        }

        attach(kinds, order, side, RoomKind.TREASURE, floor.treasureRooms, target, rng);
        attach(kinds, order, side, RoomKind.SHOP, floor.shopRooms, target, rng);
        attach(kinds, order, side, RoomKind.LOCKED, LOCKED_ROOMS, target, rng);
        secret(kinds, order, side, target, rng);
        return order.size == target;
    }

    /**
     * The seed {@code java.util.Random} actually gets, for a caller's seed on
     * one floor. Two separate problems are fixed here.
     *
     * <p>Random's first output barely moves between nearby seeds: seed and
     * seed+1 differ by about 2^34.5 in its 48-bit state, so the top bits that
     * {@code nextInt(4)} reads change only every ~2,900 seeds. Measured over
     * seeds 0 to 999, the first {@code nextInt(4)} came out
     * {@code [0, 0, 810, 190]} - so an 8-11 room floor only ever had 10 or 11
     * rooms, and the 1,000-seed test never once built the smallest, most
     * cramped floors it exists to check. Runs seeded from a counter, or floors
     * seeded as run seed plus floor number, would have had the same flat
     * spread in play.
     *
     * <p>{@link #mix} scrambles that away. It is a bijection, so on any one
     * floor two seeds still never share a layout, and {@link FloorLayout#seed}
     * keeps the caller's seed rather than this one.
     */
    static long streamFor(long seed, int floorNumber) {
        // The floor number is folded in so that one run seed can be handed to
        // every floor. Without it, the same seed on two floors is the same
        // random stream, and whenever the first draw lands on the same room
        // count the whole floor repeats: measured over 1,000 shared seeds,
        // floors 4 and 5 - both depths - came out as the identical floor,
        // same rooms in the same places, 150 times. Adding the golden-ratio
        // constant per floor is how SplitMix64 itself steps between streams.
        return mix(seed + 0x9E3779B97F4A7C15L * floorNumber);
    }

    /** The SplitMix64 finalizer; see {@link #streamFor}. */
    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * Grid side length for a floor of {@code rooms} rooms.
     *
     * <p>The second term is what makes the walk unable to stall. It can only
     * run out of legal cells if every extreme room of the tree - furthest left,
     * right, up and down - is pinned against the grid border, and a tree that
     * touches all four borders spans the grid in both directions, which takes
     * at least {@code 2 * side - 1} rooms. Keeping that above the room count
     * means there is always an extreme room with a free cell beyond it that
     * touches nothing else. The first term just keeps small floors from
     * hugging the edges; the array is null references, so slack costs nothing.
     */
    static int sideFor(int rooms) {
        int roomy = (int) Math.ceil(Math.sqrt(rooms)) * 3;
        int proof = (rooms + 3) / 2 + 1;
        return Math.max(9, Math.max(roomy, proof));
    }

    // ---- the walk --------------------------------------------------------

    private void walk(RoomKind[] kinds, Array<int[]> order, int side, int target,
                      Random rng, FloorDef floor, long seed) {
        int cx = side / 2, cy = side / 2;
        put(kinds, order, side, cx, cy, RoomKind.START);

        // Straight out of the start before anything may branch. Unconstrained
        // growth can hand back a star - three rooms all one step from the start
        // - and then there is nowhere for the boss that is not next door.
        Dir d = Dir.ALL[rng.nextInt(4)];
        for (int i = 0; i < SPINE && order.size < target; i++) {
            cx += d.dx;
            cy += d.dy;
            put(kinds, order, side, cx, cy, RoomKind.NORMAL);
        }

        long budget = (long) ATTEMPTS_PER_ROOM * target;
        while (order.size < target) {
            if (budget-- <= 0) {
                throw new IllegalStateException("floor " + floor.number + " seed "
                    + seed + ": walk stalled at " + order.size + " of " + target
                    + " rooms on a " + side + "x" + side + " grid");
            }
            int[] from = order.get(rng.nextInt(order.size));
            Dir step = Dir.ALL[rng.nextInt(4)];
            int nx = from[0] + step.dx, ny = from[1] + step.dy;
            if (free(kinds, side, nx, ny) && neighbours(kinds, side, nx, ny) == 1) {
                put(kinds, order, side, nx, ny, RoomKind.NORMAL);
            }
        }
    }

    /** Hop count from the start over the whole graph, indexed by grid cell. */
    private int[] distances(RoomKind[] kinds, Array<int[]> order, int side) {
        int[] dist = new int[side * side];
        Arrays.fill(dist, -1);
        int[] queue = new int[order.size];
        int head = 0, tail = 0;

        int[] start = order.first();
        int origin = start[1] * side + start[0];
        dist[origin] = 0;
        queue[tail++] = origin;
        while (head < tail) {
            int cell = queue[head++];
            int x = cell % side, y = cell / side;
            for (Dir d : Dir.ALL) {
                int nx = x + d.dx, ny = y + d.dy;
                if (!inside(side, nx, ny)) {
                    continue;
                }
                int next = ny * side + nx;
                if (kinds[next] != null && dist[next] < 0) {
                    dist[next] = dist[cell] + 1;
                    queue[tail++] = next;
                }
            }
        }
        return dist;
    }

    /**
     * The furthest dead end from the start, which is where the boss goes.
     *
     * <p>In a tree the deepest room is always a leaf, and the spine puts one at
     * depth two or more, so this always finds a room at least two doors from
     * the start once a floor has four rooms. Below four the floor's own room
     * count makes the spacing rule impossible, and the count wins: it is the
     * number the designer wrote down.
     */
    private int pickBoss(RoomKind[] kinds, Array<int[]> order, int side,
                         int[] distance, int target) {
        int best = -1, bestDist = -1;
        for (int[] c : order) {
            int cell = c[1] * side + c[0];
            if (kinds[cell] != RoomKind.NORMAL || neighbours(kinds, side, c[0], c[1]) != 1) {
                continue;
            }
            if (distance[cell] < 2 && target >= 4) {
                continue;
            }
            if (distance[cell] > bestDist) {
                bestDist = distance[cell];
                best = cell;
            }
        }
        return best;
    }

    // ---- hanging the side rooms off it -----------------------------------

    /**
     * Adds up to {@code count} rooms of one kind, each on a free cell that
     * touches exactly one placed room, and that room a START or a NORMAL.
     *
     * <p>Exactly one neighbour, not at least one: a side room with two doors is
     * a shortcut, and a shortcut through a locked room is how a boss ends up
     * behind a key the player cannot reach. The parent has to be a plain room
     * for the other half of the same idea. Nothing grows off the boss - Isaac's
     * boss room has one door, and a shop behind it would be a shop nobody
     * visits - and nothing grows off a locked or secret room, because a room
     * behind one of those is a room the player may never be able to enter.
     */
    private void attach(RoomKind[] kinds, Array<int[]> order, int side,
                        RoomKind kind, int count, int target, Random rng) {
        for (int placed = 0; placed < count && order.size < target; placed++) {
            Array<int[]> spots = new Array<>();
            for (int[] c : order) {
                RoomKind parent = kinds[c[1] * side + c[0]];
                if (parent != RoomKind.START && parent != RoomKind.NORMAL) {
                    continue;
                }
                for (Dir d : Dir.ALL) {
                    int nx = c[0] + d.dx, ny = c[1] + d.dy;
                    if (free(kinds, side, nx, ny) && neighbours(kinds, side, nx, ny) == 1) {
                        spots.add(new int[] {nx, ny});
                    }
                }
            }
            if (spots.isEmpty()) {
                return;
            }
            int[] spot = leastWasteful(spots, rng);
            put(kinds, order, side, spot[0], spot[1], kind);
        }
    }

    /**
     * Of the legal spots, one that rules out as few of the others as possible.
     *
     * <p>Taking a spot kills every other spot beside it, because those would
     * then touch two rooms. Picking uniformly at random spends that budget
     * carelessly: measured over 5,000 seeds, a 10-13 room floor asking for
     * five side rooms needed a whole-floor retry on 9.7% of seeds and six
     * attempts on its worst one. Preferring the spots with the fewest spot
     * neighbours is the greedy answer to that packing problem, and still
     * leaves a random choice among the ties; with it, the same floor retries
     * on 5.3% of seeds and never more than five times. What is left is
     * arithmetic rather than waste - a ten-room floor minus five side rooms
     * and the boss leaves four plain rooms to hang five side rooms on.
     */
    private static int[] leastWasteful(Array<int[]> spots, Random rng) {
        Array<int[]> best = new Array<>();
        int fewest = Integer.MAX_VALUE;
        // Indexed, not for-each: libGDX's Array hands out cached iterators and
        // throws if the same array is iterated inside its own loop.
        for (int i = 0; i < spots.size; i++) {
            int[] s = spots.get(i);
            int crowd = 0;
            for (int j = 0; j < spots.size; j++) {
                int[] o = spots.get(j);
                if (Math.abs(o[0] - s[0]) + Math.abs(o[1] - s[1]) == 1) {
                    crowd++;
                }
            }
            if (crowd < fewest) {
                fewest = crowd;
                best.clear();
            }
            if (crowd == fewest) {
                best.add(s);
            }
        }
        return best.get(rng.nextInt(best.size));
    }

    /**
     * The secret room, dropped where it touches the most rooms at once.
     *
     * <p>The opposite rule to every other side room, deliberately: a hidden
     * room is only worth finding if it shortcuts something. It is safe to let
     * it, because {@link #check} refuses to walk through a secret door on its
     * way to the boss.
     *
     * <p>But only plain rooms may be on the other side of its walls. A secret
     * room beside the locked room is a way round the key, and beside the boss
     * it gives the boss a second door; either way a side room stops having
     * exactly one entrance, which is what keeps it off the critical path. This
     * was caught writing the test that asserts one door per side room, before
     * the test had even run.
     */
    private void secret(RoomKind[] kinds, Array<int[]> order, int side,
                        int target, Random rng) {
        if (SECRET_ROOMS == 0 || order.size >= target) {
            return;
        }
        Array<int[]> best = new Array<>();
        int bestTouch = 0;
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                if (!free(kinds, side, x, y) || touchesSideRoom(kinds, side, x, y)) {
                    continue;
                }
                int touch = neighbours(kinds, side, x, y);
                if (touch == 0) {
                    continue;
                }
                if (touch > bestTouch) {
                    bestTouch = touch;
                    best.clear();
                }
                if (touch == bestTouch) {
                    best.add(new int[] {x, y});
                }
            }
        }
        if (best.isEmpty()) {
            return;
        }
        int[] spot = best.get(rng.nextInt(best.size));
        put(kinds, order, side, spot[0], spot[1], RoomKind.SECRET);
    }

    /** Whether any room beside (x, y) is something other than START or NORMAL. */
    private static boolean touchesSideRoom(RoomKind[] kinds, int side, int x, int y) {
        for (Dir d : Dir.ALL) {
            int nx = x + d.dx, ny = y + d.dy;
            if (!inside(side, nx, ny)) {
                continue;
            }
            RoomKind k = kinds[ny * side + nx];
            if (k != null && k != RoomKind.START && k != RoomKind.NORMAL) {
                return true;
            }
        }
        return false;
    }

    // ---- turning cells into rooms ----------------------------------------

    private FloorLayout build(RoomKind[] kinds, Array<int[]> order, int side,
                              FloorDef floor, long seed, Random rng) {
        FloorLayout layout = new FloorLayout(floor.number, seed, side, side);
        for (int[] c : order) {
            RoomKind kind = kinds[c[1] * side + c[0]];
            layout.place(new Room(c[0], c[1], kind, pick(floor.biome, kind, rng)));
        }
        // Linked from grid adjacency rather than from the walk's own record of
        // who grew from whom: two rooms side by side on the grid are always
        // neighbours in the graph, and Room.link writes both ends, so a
        // one-way door cannot be expressed.
        for (Room room : layout.rooms()) {
            for (Dir d : Dir.ALL) {
                Room other = layout.roomAt(room.gx + d.dx, room.gy + d.dy);
                if (other != null && room.neighbour(d) == null) {
                    room.link(d, other);
                }
            }
        }
        return layout;
    }

    /**
     * A template for one room, narrowing by biome first and by kind second.
     *
     * <p>A missing biome or kind yields a mismatched room, never an exception,
     * and an empty catalog yields a room with no template. A floor drawn in the
     * wrong tileset is a bug someone can see and fix; a throw here is a crash
     * three screens into a run that costs the player the run.
     */
    private RoomTemplate pick(String biome, RoomKind kind, Random rng) {
        RoomTemplate found = choose(biome, kind, rng);
        if (found == null) {
            found = choose(biome, RoomKind.NORMAL, rng);
        }
        if (found == null) {
            found = choose(biome, null, rng);
        }
        if (found == null) {
            found = choose(null, kind, rng);
        }
        if (found == null) {
            found = choose(null, null, rng);
        }
        return found;
    }

    /** A null biome or kind means "do not filter on it". */
    private RoomTemplate choose(String biome, RoomKind kind, Random rng) {
        Array<RoomTemplate> matching = new Array<>();
        // Indexed: the catalog is shared, and a caller already for-eaching
        // over it when it asks for a floor would otherwise trip libGDX's
        // nested-iterator check.
        for (int i = 0; i < templates.size; i++) {
            RoomTemplate t = templates.get(i);
            if ((biome == null || t.biome.equals(biome)) && (kind == null || t.suits(kind))) {
                matching.add(t);
            }
        }
        return matching.isEmpty() ? null : matching.get(rng.nextInt(matching.size));
    }

    // ---- the invariant ---------------------------------------------------

    /**
     * Everything that must be true of a finished floor, checked before
     * {@link #generate} returns and again by the tests.
     *
     * <p>Public so the tests can hand it deliberately broken floors and prove
     * it rejects each one. A checker that never fires looks exactly like a
     * generator that never fails, and five thousand passing seeds prove
     * nothing if the check itself is vacuous. It throws rather than returns a
     * flag: none of these failures is recoverable, and a run that starts on a
     * broken floor wastes the player's time before it strands them.
     */
    public static void check(FloorLayout layout, FloorDef floor) {
        String where = "floor " + floor.number + " seed " + layout.seed() + ": ";
        int count = layout.rooms().size;
        if (count < floor.roomsMin || count > floor.roomsMax) {
            throw new IllegalStateException(where + count + " rooms, wanted "
                + floor.roomsMin + ".." + floor.roomsMax);
        }
        if (layout.start() == null) {
            throw new IllegalStateException(where + "no start room");
        }
        if (floor.hasBoss() && layout.boss() == null) {
            throw new IllegalStateException(where + "boss floor with no boss room");
        }

        for (Room room : layout.rooms()) {
            for (Dir d : Dir.ALL) {
                Room other = room.neighbour(d);
                Room onGrid = layout.roomAt(room.gx + d.dx, room.gy + d.dy);
                if (other != onGrid) {
                    throw new IllegalStateException(where + room + " links " + d
                        + " to " + other + " but the grid holds " + onGrid);
                }
                if (room.hasDoor(d) != (other != null)) {
                    throw new IllegalStateException(where + room + " door " + d
                        + " disagrees with its neighbour");
                }
                if (other != null && other.neighbour(d.opposite()) != room) {
                    throw new IllegalStateException(where + "one-way door "
                        + room + " -" + d + "-> " + other);
                }
            }
        }

        int reached = reach(layout, false).size;
        if (reached != count) {
            throw new IllegalStateException(where + (count - reached) + " orphan rooms");
        }

        // The stairs down: the boss room on a boss floor, the exit otherwise.
        Room goal = layout.exit();
        if (goal == null) {
            return;
        }
        if (!reach(layout, true).contains(goal, true)) {
            throw new IllegalStateException(where + goal
                + " is only reachable through a locked or secret door");
        }
        if (count >= 4 && Math.abs(goal.gx - layout.start().gx)
                          + Math.abs(goal.gy - layout.start().gy) == 1) {
            throw new IllegalStateException(where + goal + " is next door to the start");
        }
    }

    /**
     * Rooms reachable from the start.
     *
     * <p>With {@code open} set the walk will not pass through a locked or
     * secret room: that is the route a player has with no key and no bomb,
     * and the boss has to be on it.
     */
    private static Array<Room> reach(FloorLayout layout, boolean open) {
        Array<Room> seen = new Array<>();
        Array<Room> stack = new Array<>();
        seen.add(layout.start());
        stack.add(layout.start());
        while (stack.size > 0) {
            Room room = stack.pop();
            for (Dir d : Dir.ALL) {
                Room next = room.neighbour(d);
                if (next == null || seen.contains(next, true)) {
                    continue;
                }
                if (open && (next.kind == RoomKind.LOCKED || next.kind == RoomKind.SECRET)) {
                    continue;
                }
                seen.add(next);
                stack.add(next);
            }
        }
        return seen;
    }

    // ---- grid helpers ----------------------------------------------------

    private static void put(RoomKind[] kinds, Array<int[]> order, int side,
                            int x, int y, RoomKind kind) {
        kinds[y * side + x] = kind;
        order.add(new int[] {x, y});
    }

    private static boolean inside(int side, int x, int y) {
        return x >= 0 && y >= 0 && x < side && y < side;
    }

    private static boolean free(RoomKind[] kinds, int side, int x, int y) {
        return inside(side, x, y) && kinds[y * side + x] == null;
    }

    private static int neighbours(RoomKind[] kinds, int side, int x, int y) {
        int n = 0;
        for (Dir d : Dir.ALL) {
            int nx = x + d.dx, ny = y + d.dy;
            if (inside(side, nx, ny) && kinds[ny * side + nx] != null) {
                n++;
            }
        }
        return n;
    }
}
