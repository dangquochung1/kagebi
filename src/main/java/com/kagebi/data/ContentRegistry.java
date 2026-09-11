package com.kagebi.data;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.kagebi.data.def.EnemyDef;
import com.kagebi.data.def.FloorDef;
import com.kagebi.data.def.ItemDef;
import com.kagebi.data.def.LootTableDef;
import com.kagebi.data.def.RelicDef;
import com.kagebi.data.def.WeaponDef;

/**
 * Every definition the game was loaded with, looked up by id.
 *
 * <p>Deliberately a plain container rather than an interface, and deliberately
 * not the thing that reads JSON. Loading lives in {@code ContentLoader}, which
 * means combat, loot and generation tests can build a registry with three defs
 * in it and never touch a file - the reason those packages can be tested
 * without an OpenGL context at all.
 *
 * <p>Lookups throw rather than returning null. A typo in an id is a content
 * bug, and the useful moment to hear about it is at startup with the id in the
 * message, not three rooms into a run as an invisible enemy.
 */
public final class ContentRegistry {

    private final ObjectMap<String, EnemyDef> enemies = new ObjectMap<>();
    private final ObjectMap<String, WeaponDef> weapons = new ObjectMap<>();
    private final ObjectMap<String, RelicDef> relics = new ObjectMap<>();
    private final ObjectMap<String, ItemDef> items = new ObjectMap<>();
    private final ObjectMap<String, LootTableDef> lootTables = new ObjectMap<>();
    private final ObjectMap<Integer, FloorDef> floors = new ObjectMap<>();

    public void put(EnemyDef d) {
        enemies.put(d.id, d);
    }

    public void put(WeaponDef d) {
        weapons.put(d.id, d);
    }

    public void put(RelicDef d) {
        relics.put(d.id, d);
    }

    public void put(ItemDef d) {
        items.put(d.id, d);
    }

    public void put(LootTableDef d) {
        lootTables.put(d.id, d);
    }

    public void put(FloorDef d) {
        floors.put(d.number, d);
    }

    public EnemyDef enemy(String id) {
        return require(enemies.get(id), "enemy", id);
    }

    public WeaponDef weapon(String id) {
        return require(weapons.get(id), "weapon", id);
    }

    public RelicDef relic(String id) {
        return require(relics.get(id), "relic", id);
    }

    public ItemDef item(String id) {
        return require(items.get(id), "item", id);
    }

    public LootTableDef lootTable(String id) {
        return require(lootTables.get(id), "loot table", id);
    }

    public FloorDef floor(int number) {
        return require(floors.get(number), "floor", String.valueOf(number));
    }

    public boolean hasEnemy(String id) {
        return enemies.containsKey(id);
    }

    public boolean hasItem(String id) {
        return items.containsKey(id);
    }

    public boolean hasRelic(String id) {
        return relics.containsKey(id);
    }

    public boolean hasLootTable(String id) {
        return lootTables.containsKey(id);
    }

    public Array<EnemyDef> allEnemies() {
        return values(enemies);
    }

    public Array<WeaponDef> allWeapons() {
        return values(weapons);
    }

    public Array<RelicDef> allRelics() {
        return values(relics);
    }

    public Array<ItemDef> allItems() {
        return values(items);
    }

    public Array<LootTableDef> allLootTables() {
        return values(lootTables);
    }

    public Array<FloorDef> allFloors() {
        Array<FloorDef> out = new Array<>();
        for (int n = 1; floors.containsKey(n); n++) {
            out.add(floors.get(n));
        }
        return out;
    }

    private static <T> Array<T> values(ObjectMap<?, T> map) {
        Array<T> out = new Array<>(map.size);
        for (T v : map.values()) {
            out.add(v);
        }
        return out;
    }

    private static <T> T require(T value, String kind, String id) {
        if (value == null) {
            throw new IllegalArgumentException("no " + kind + " with id '" + id + "'");
        }
        return value;
    }
}
