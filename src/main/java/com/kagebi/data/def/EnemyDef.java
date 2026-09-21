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

    /**
     * What this turns into when it dies, or null for most things.
     *
     * <p>The whole of stage 6's boss lives in this field. Pirate Leader
     * summons a fireball, the fireball summons Pirate Zombie, and so on to
     * Squidman - five defs with five health bars, rather than one bar cut into
     * slices. Written here rather than in five Java brains so the chain is
     * legible in the file that balances it, and so ContentValidator can walk
     * it: a link that names nothing, or names a loop, is a build failure.
     */
    public final String evolvesInto;

    /**
     * Ids this may call into the room when it is cornered, or empty.
     *
     * <p>Only read by the brain that has a reason to; it is here so that
     * {@code ContentValidator.orphans} can see that these enemies do appear
     * on a floor, through whoever summons them.
     */
    public final String[] summons;

    /**
     * Health fraction at which a boss turns nasty, or 0 for never.
     *
     * <p>Not {@link #phases}, deliberately. A phase change plays a {@code
     * trans} strip and the validator refuses a def whose art has none - which
     * is right for tengured, whose transformation is the only place the story
     * is told without a line of text, and wrong for a body that simply starts
     * fighting harder at 40%. This is the second kind.
     */
    public final float enrageAt;

    /**
     * The fx region this fires, or null for the generic orb.
     *
     * <p>notes/b.md asked for this: "enemies.json names inkball, sporecloud
     * and flamewave in comments but there is no field for them, so every shot
     * looks the same". Stage 6 needed six different-looking shots, so here it
     * is - a name from {@code Assets.Fx}, resolved by the world.
     */
    public final String projectile;

    public EnemyDef(String id, String nameKey, String sprite, int cell,
                    int maxHp, int contactDamage, int attackDamage, float moveSpeed,
                    String brain, float aggroRange, float attackRange,
                    int windupSteps, int activeSteps, int recoverSteps,
                    int cooldownSteps, float knockbackResist, int hurtInvulnSteps,
                    String lootTable, int goldMin, int goldMax,
                    boolean boss, int phases, boolean flying) {
        this(id, nameKey, sprite, cell, maxHp, contactDamage, attackDamage, moveSpeed,
             brain, aggroRange, attackRange, windupSteps, activeSteps, recoverSteps,
             cooldownSteps, knockbackResist, hurtInvulnSteps, lootTable, goldMin,
             goldMax, boss, phases, flying, null, new String[0], 0f, null);
    }

    public EnemyDef(String id, String nameKey, String sprite, int cell,
                    int maxHp, int contactDamage, int attackDamage, float moveSpeed,
                    String brain, float aggroRange, float attackRange,
                    int windupSteps, int activeSteps, int recoverSteps,
                    int cooldownSteps, float knockbackResist, int hurtInvulnSteps,
                    String lootTable, int goldMin, int goldMax,
                    boolean boss, int phases, boolean flying,
                    String evolvesInto, String[] summons, float enrageAt,
                    String projectile) {
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
        this.evolvesInto = evolvesInto;
        this.summons = summons == null ? new String[0] : summons;
        this.enrageAt = enrageAt;
        this.projectile = projectile;
    }

    @Override
    public String toString() {
        return "EnemyDef(" + id + ")";
    }
}
