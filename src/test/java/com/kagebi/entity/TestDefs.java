package com.kagebi.entity;

import com.badlogic.gdx.utils.Array;
import com.kagebi.data.def.EnemyDef;
import com.kagebi.data.def.WeaponDef;
import com.kagebi.gen.CollisionGrid;
import com.kagebi.gen.Room;
import com.kagebi.gen.RoomKind;
import com.kagebi.gen.RoomTemplate;
import com.kagebi.gen.SpawnPoint;
import com.kagebi.run.RunState;

/**
 * Hand-built content for tests, so nothing here waits on enemies.json.
 *
 * <p>The archetypes copy the numbers from the content agent's draft of
 * enemies.json (read, not linked): a test that passes on made-up numbers and
 * fails on the real ones has proved nothing.
 */
public final class TestDefs {

    /** EnemyDef takes 23 arguments; this keeps a test to the three it cares about. */
    public static final class EnemyBuilder {
        String id;
        String sprite;
        int cell = 16;
        int maxHp = 20;
        int contactDamage = 4;
        int attackDamage = 0;
        float moveSpeed = 30f;
        String brain = "chaser";
        float aggroRange = 100f;
        float attackRange = 0f;
        int windup = 10;
        int active = 6;
        int recover = 12;
        int cooldown = 20;
        float knockbackResist = 0f;
        int hurtInvuln = 10;
        int goldMin;
        int goldMax;
        boolean boss;
        int phases = 1;
        boolean flying;

        EnemyBuilder(String id) {
            this.id = id;
            this.sprite = "monsters/" + id + "/spritesheet";
        }

        public EnemyBuilder brain(String b) { brain = b; return this; }
        public EnemyBuilder hp(int v) { maxHp = v; return this; }
        public EnemyBuilder contact(int v) { contactDamage = v; return this; }
        public EnemyBuilder attack(int dmg, float range) { attackDamage = dmg; attackRange = range; return this; }
        public EnemyBuilder speed(float v) { moveSpeed = v; return this; }
        public EnemyBuilder aggro(float v) { aggroRange = v; return this; }
        public EnemyBuilder timing(int w, int a, int r, int c) {
            windup = w; active = a; recover = r; cooldown = c; return this;
        }
        public EnemyBuilder resist(float v) { knockbackResist = v; return this; }
        public EnemyBuilder invuln(int v) { hurtInvuln = v; return this; }
        public EnemyBuilder gold(int min, int max) { goldMin = min; goldMax = max; return this; }
        public EnemyBuilder boss(int phaseCount, String sprite, int cell) {
            boss = true; phases = phaseCount; this.sprite = sprite; this.cell = cell; return this;
        }

        public EnemyDef build() {
            return new EnemyDef(id, "enemy." + id + ".name", sprite, cell, maxHp, contactDamage,
                attackDamage, moveSpeed, brain, aggroRange, attackRange, windup, active, recover,
                cooldown, knockbackResist, hurtInvuln, "trash_f1", goldMin, goldMax,
                boss, phases, flying);
        }
    }

    public static EnemyBuilder enemy(String id) {
        return new EnemyBuilder(id);
    }

    // ---- the content agent's numbers ---------------------------------------

    public static EnemyDef slime() {
        return enemy("slime").brain("hopper").hp(16).contact(4).speed(24).aggro(90)
            .timing(20, 10, 20, 30).resist(0.10f).invuln(10).gold(2, 5).build();
    }

    public static EnemyDef larva() {
        return enemy("larva").brain("chaser").hp(22).contact(5).speed(16).aggro(70)
            .timing(10, 8, 14, 20).resist(0.35f).invuln(12).gold(3, 6).build();
    }

    public static EnemyDef kappared() {
        return enemy("kappared").brain("charger").hp(34).contact(6).attack(11, 20).speed(30)
            .aggro(140).timing(26, 14, 24, 50).resist(0.40f).invuln(12).gold(6, 11).build();
    }

    public static EnemyDef octopus() {
        return enemy("octopus").brain("shooter").hp(20).contact(4).attack(8, 96).speed(26)
            .aggro(150).timing(22, 4, 20, 60).invuln(10).gold(4, 8).build();
    }

    public static EnemyDef mushroom() {
        return enemy("mushroom").brain("splitter").hp(44).contact(7).speed(30).aggro(110)
            .timing(10, 8, 14, 22).resist(0.30f).invuln(12).gold(7, 12).build();
    }

    public static EnemyDef tengured() {
        return enemy("tengured").brain("boss_tengu").hp(1800).contact(16).attack(30, 90)
            .speed(64).aggro(999).timing(26, 14, 30, 48).resist(1f).invuln(5).gold(150, 250)
            .boss(2, "bosses/tengured/idle", 82).build();
    }

    /** The starting katana, as weapons.json has it. */
    public static WeaponDef katana() {
        return EntityWorld.FALLBACK_WEAPON;
    }

    /** The hammer: the weapon with the longest root, for movement-lock tests. */
    public static WeaponDef hammer() {
        return new WeaponDef("hammer", "weapon.hammer.name", "weapon.hammer.desc",
            "player/weapons/hammer", -1, 26, 28f, 22f, 16, 5, 25, 190f, 24, null);
    }

    // ---- rooms ---------------------------------------------------------------

    public static RunState run() {
        RunState run = new RunState(1234L, "ninjagreen", "katana", 100);
        run.floor = 1;
        return run;
    }

    public static Room room(RoomKind kind, SpawnPoint... spawns) {
        Array<RoomKind> kinds = new Array<>();
        kinds.add(kind);
        Array<SpawnPoint> list = new Array<>();
        for (SpawnPoint s : spawns) {
            list.add(s);
        }
        RoomTemplate t = new RoomTemplate("test", "test", kinds, "test/room.tmx", list);
        return new Room(0, 0, kind, t);
    }

    /** A 20x11 room with a one-tile wall all the way round and nothing inside. */
    public static CollisionGrid walled() {
        CollisionGrid g = new CollisionGrid(RoomTemplate.WIDTH, RoomTemplate.HEIGHT);
        for (int x = 0; x < RoomTemplate.WIDTH; x++) {
            g.set(x, 0, true);
            g.set(x, RoomTemplate.HEIGHT - 1, true);
        }
        for (int y = 0; y < RoomTemplate.HEIGHT; y++) {
            g.set(0, y, true);
            g.set(RoomTemplate.WIDTH - 1, y, true);
        }
        return g;
    }

    public static SpawnPoint at(SpawnPoint.Kind kind, int x, int y, String tag) {
        return new SpawnPoint(kind, x, y, tag);
    }

    private TestDefs() {}
}
