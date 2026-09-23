package com.kagebi.data;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.kagebi.data.def.CraftDef;
import com.kagebi.data.def.EnemyDef;
import com.kagebi.data.def.FloorDef;
import com.kagebi.data.def.GearDef;
import com.kagebi.data.def.GemDef;
import com.kagebi.data.def.ItemDef;
import com.kagebi.data.def.LootTableDef;
import com.kagebi.data.def.QuestDef;
import com.kagebi.data.def.SkillDef;
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
    private final ObjectMap<String, GearDef> gear = new ObjectMap<>();
    private final ObjectMap<String, GemDef> gems = new ObjectMap<>();
    private final ObjectMap<String, CraftDef> crafts = new ObjectMap<>();
    private final ObjectMap<String, QuestDef> quests = new ObjectMap<>();
    private final ObjectMap<String, SkillDef> skills = new ObjectMap<>();
    /** Icon name to grid index, as icons.json spells it. */
    private final com.badlogic.gdx.utils.ObjectIntMap<String> icons =
        new com.badlogic.gdx.utils.ObjectIntMap<>();

    /** Filled once by the loader, from the icons section read before everything else. */
    public void putIcons(com.badlogic.gdx.utils.ObjectIntMap<String> from) {
        icons.clear();
        icons.putAll(from);
    }

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

    public void put(GearDef d) {
        gear.put(d.id, d);
    }

    public void put(GemDef d) {
        gems.put(d.id, d);
    }

    public void put(CraftDef d) {
        crafts.put(d.id, d);
    }

    public void put(SkillDef d) {
        skills.put(d.id, d);
    }

    public void put(QuestDef d) {
        quests.put(d.id, d);
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

    public GearDef gear(String id) {
        return require(gear.get(id), "gear", id);
    }

    public GemDef gem(String id) {
        return require(gems.get(id), "gem", id);
    }

    public CraftDef craft(String id) {
        return require(crafts.get(id), "craft", id);
    }

    public SkillDef skill(String id) {
        return require(skills.get(id), "skill", id);
    }

    public QuestDef quest(String id) {
        return require(quests.get(id), "quest", id);
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

    /**
     * The grid index for a named icon, or -1.
     *
     * <p>Defs resolve their own icons at parse time, so this exists for the
     * screens: a badge that wants "the armour icon" should name it the way the
     * content files do rather than writing down a number that moves the next
     * time {@code icons.json} is re-sorted.
     */
    public int icon(String name) {
        return icons.get(name, -1);
    }

    public boolean hasGear(String id) {
        return gear.containsKey(id);
    }

    public boolean hasGem(String id) {
        return gems.containsKey(id);
    }

    public boolean hasSkill(String id) {
        return skills.containsKey(id);
    }

    public boolean hasQuest(String id) {
        return quests.containsKey(id);
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

    public Array<GearDef> allGear() {
        return values(gear);
    }

    public Array<GemDef> allGems() {
        return values(gems);
    }

    public Array<CraftDef> allCrafts() {
        return values(crafts);
    }

    public Array<SkillDef> allSkills() {
        return values(skills);
    }

    /**
     * The three skills one character actually carries.
     *
     * <p>Their own if the file gives them any, and the shared set otherwise.
     * It is all or nothing on purpose: a character with a fire ultimate and
     * two borrowed lightning skills is a bug that validates cleanly, and the
     * one-per-slot check could not see it either, because it would be looking
     * at two different sets.
     *
     * <p>This is the only place a character and a skill meet. The last rule
     * that gave one character something of their own - a pair of spells only
     * she could hold - was written into four places and right in two, and both
     * the spells and the character went rather than the rule being fixed. One
     * method, one caller.
     */
    public Array<SkillDef> skillsFor(String characterId) {
        Array<SkillDef> mine = new Array<>();
        Array<SkillDef> shared = new Array<>();
        for (SkillDef s : allSkills()) {
            if (s.character == null) {
                shared.add(s);
            } else if (s.character.equals(characterId)) {
                mine.add(s);
            }
        }
        return mine.size > 0 ? mine : shared;
    }

    /**
     * The one thing a character has without pressing anything, or null.
     *
     * <p>Read out of the same set {@link #skillsFor} returns rather than out
     * of a second table, so a passive belongs to a character the same way the
     * three keys do and arrives and leaves with them. Nothing else in the
     * game has to know it is different: it has no slot, so
     * {@code Player.setSkills} drops it, and its effects reach the player the
     * way an ultimate's do - through {@code Loadout}.
     */
    public SkillDef passiveFor(String characterId) {
        for (SkillDef s : skillsFor(characterId)) {
            if (s.kind == SkillDef.Kind.PASSIVE) {
                return s;
            }
        }
        return null;
    }

    public Array<QuestDef> allQuests() {
        return values(quests);
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
