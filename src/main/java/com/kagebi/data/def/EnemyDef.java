package com.kagebi.data.def;

/**
 * One enemy, as it appears in {@code assets/data/enemies.json}.
 *
 * <p>Fields are public and final: this is a record of what the designer typed,
 * not an object with behaviour. Behaviour lives in {@code entity} and
 * {@code ai}, which read these numbers and never write them - every enemy of a
 * kind shares one def.
 *
 * <p>All durations are in fixed simulation steps (60 to the second), because
 * combat timing has to be identical on a 60Hz and a 144Hz display. All speeds
 * are virtual pixels per second.
 */
public final class EnemyDef {

    public final String id;
    public final String nameKey;

    /**
     * Atlas region holding the sheet, in {@code actors.atlas}. Trash mobs use
     * {@code monsters/<id>/spritesheet}, which is uniformly 64x64 - four
     * directions by four frames of 16x16 - across all 66 of them. Bosses use
     * {@code bosses/<id>/<animation>} and are horizontal strips instead.
     */
    public final String sprite;
    /** Frame size in pixels: 16 for trash, whatever the strip is for a boss. */
    public final int cell;

    public final int maxHp;
    /** Damage dealt by touching the player; 0 for enemies that only strike. */
    public final int contactDamage;
    public final int attackDamage;
    /** Virtual pixels per second. */
    public final float moveSpeed;

    /** Which {@code ai} brain drives it. An unknown id must fail at load. */
    public final String brain;
    public final float aggroRange;
    public final float attackRange;
    public final int windupSteps;
    public final int activeSteps;
    public final int recoverSteps;
    public final int cooldownSteps;

    /** 0 shrugs nothing off, 1 cannot be knocked back at all. */
    public final float knockbackResist;
    /** Invulnerable steps after being hit, so a fast weapon cannot shred. */
    public final int hurtInvulnSteps;

    public final String lootTable;
    public final int goldMin;
    public final int goldMax;

    public final boolean boss;
    /** Bosses only; 1 for everything else. TenguRed has a trans sheet for 2. */
    public final int phases;
    /** Ignores floor hazards and pits. */
    public final boolean flying;

    public EnemyDef(String id, String nameKey, String sprite, int cell,
                    int maxHp, int contactDamage, int attackDamage, float moveSpeed,
                    String brain, float aggroRange, float attackRange,
                    int windupSteps, int activeSteps, int recoverSteps,
                    int cooldownSteps, float knockbackResist, int hurtInvulnSteps,
                    String lootTable, int goldMin, int goldMax,
                    boolean boss, int phases, boolean flying) {
        this.id = id;
        this.nameKey = nameKey;
        this.sprite = sprite;
        this.cell = cell;
        this.maxHp = maxHp;
        this.contactDamage = contactDamage;
        this.attackDamage = attackDamage;
        this.moveSpeed = moveSpeed;
        this.brain = brain;
        this.aggroRange = aggroRange;
        this.attackRange = attackRange;
        this.windupSteps = windupSteps;
        this.activeSteps = activeSteps;
        this.recoverSteps = recoverSteps;
        this.cooldownSteps = cooldownSteps;
        this.knockbackResist = knockbackResist;
        this.hurtInvulnSteps = hurtInvulnSteps;
        this.lootTable = lootTable;
        this.goldMin = goldMin;
        this.goldMax = goldMax;
        this.boss = boss;
        this.phases = phases;
        this.flying = flying;
    }

    @Override
    public String toString() {
        return "EnemyDef(" + id + ")";
    }
}
