package com.kagebi.ai;

import com.kagebi.entity.Enemy;

/**
 * The thing a boss of stage 6 becomes between bodies: a ball, hanging, raining.
 *
 * <p>An intermission with teeth. The player cannot hurt it and it cannot be
 * walked into, so the whole of it is dodging what falls out of the sky for as
 * long as it lasts - which is the one thing the three bodies never ask for,
 * and the reason the chain does not read as three fights in a row.
 *
 * <p><b>It is an enemy, and that is load-bearing.</b> The room counts itself
 * cleared the moment nothing hostile is left in it, and that latch opens the
 * doors, lights the stairs and cuts the boss music - permanently, because
 * nothing ever unlatches it. A timer on the screen or a flag on the world
 * would all have to fight that. A body in the room does not.
 */
public final class OrbBrain extends BaseBrain {

    /** How long an intermission runs: eight seconds, inside the user's 5-10. */
    public static final int LIFE_STEPS = 8 * 60;
    /** Steps between spells falling. Twelve a second is a shower, not a wall. */
    public static final int DROP_INTERVAL = 26;
    /** How far from the player they fall, at the outside. */
    public static final float DROP_RADIUS = 96f;
    /**
     * How long a spell takes to fall, and how long it burns where it lands.
     *
     * <p>The fall is the warning, and it replaces one: these used to be placed
     * on the floor armed-but-harmless for twenty-six steps, blinking. That
     * told the player where but looked like something climbing out of the
     * ground. Twenty-four steps of gravity starts the drop ninety pixels up,
     * which is high enough to read as sky in a 180px view and gives the same
     * four tenths of a second of notice.
     */
    public static final int DROP_FALL = 24;
    public static final int DROP_LINGER = 70;

    /**
     * Untouchable for longer than it can possibly live.
     *
     * <p>Through i-frames rather than through a flag, because i-frames are
     * already checked by HitResolver and already ignored by Enemy.takeHit -
     * so the player cannot touch it and it can still kill itself on the step
     * its time runs out, which is how the chain moves on.
     */
    private static final int FOREVER = LIFE_STEPS * 4;

    /** Spells per turn of the fountain, and how far out they land. */
    public static final int FOUNTAIN_ARMS = 5;
    public static final float FOUNTAIN_MIN = 48f;
    public static final float FOUNTAIN_MAX = 140f;
    /** Steps a lobbed spell spends in the air, which also sets how high it goes. */
    public static final int FOUNTAIN_FLIGHT = 34;

    private final String id;
    private final boolean fountain;

    private OrbBrain(String id, boolean fountain) {
        this.id = id;
        this.fountain = fountain;
    }

    /**
     * Fire: spells fall out of the sky onto the player.
     *
     * <p>Aimed where they are, so standing still is what kills. The answer is
     * to keep moving, and there is nowhere in the arena that is safe.
     */
    public static OrbBrain rain() {
        return new OrbBrain("boss_orb", false);
    }

    /**
     * Water: spells are thrown outward in arcs, and land in rings.
     *
     * <p>The opposite question. A fountain does not care where the player is -
     * it throws the same pattern every turn and floods the floor outward from
     * the middle, so the answer is to be where it has already been rather than
     * to keep moving. Two intermissions, two habits, and the second one
     * punishes what the first one taught.
     *
     * <p>Lobbed rather than fired, so the arcs clear whatever is between the
     * orb and where they land. A fountain that stopped at the first wall
     * would be a volley.
     */
    public static OrbBrain fountain() {
        return new OrbBrain("boss_orb_tide", true);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public void onSpawn(Enemy self) {
        self.iframes.grant(FOREVER);
        self.aiTimer = LIFE_STEPS;
        self.setState(AiState.CHASE);
    }

    /** A ball of fire nobody can hit does not also get to burn on contact. */
    @Override
    public boolean harmfulOnContact(Enemy self) {
        return false;
    }

    @Override
    protected void idle(Enemy self, AiContext ctx) {
        chase(self, ctx);
    }

    @Override
    protected void chase(Enemy self, AiContext ctx) {
        self.halt();
        // Re-granted every step. IFrames.grant extends and never shortens, so
        // this cannot be shaved off by anything that happens to it.
        self.iframes.grant(FOREVER);

        if (self.aiTimer > 0) {
            self.aiTimer--;
            if (self.aiTimer % DROP_INTERVAL == 0 && ctx.playerAlive()) {
                drop(self, ctx);
            }
            return;
        }
        // Its own hit, not the player's: takeHit does not consult i-frames,
        // which is what lets a thing nobody can touch still end on time. A
        // ball that outlived its welcome would stall every boss test there is.
        // The successor is summoned in onDeath rather than here, so that the
        // chain survives the orb dying some other way - which it cannot today,
        // and which would break the fight in silence if it ever could.
        self.takeHit(self.hp, self.x, self.y, 0f);
    }

    @Override
    public void onDeath(Enemy self, AiContext ctx) {
        if (self.def.evolvesInto != null) {
            ctx.summon(self.def.evolvesInto, self.x, self.y, false);
        }
    }

    /** One turn of whichever thing this orb is. */
    private void drop(Enemy self, AiContext ctx) {
        int damage = Math.max(1, Math.round(self.def.attackDamage * self.damageMult));
        if (fountain) {
            spray(self, ctx, damage);
            return;
        }
        double a = ctx.rng().nextDouble() * Math.PI * 2;
        float r = DROP_RADIUS * (0.15f + 0.85f * ctx.rng().nextFloat());
        ctx.rainSpell(self, ctx.playerX() + (float) Math.cos(a) * r,
            ctx.playerY() + (float) Math.sin(a) * r,
            damage, DROP_FALL, DROP_LINGER, self.def.projectile);
    }

    /**
     * A ring of arcs thrown outward, turning a little each time.
     *
     * <p>The turn is what stops it being five fixed spokes the player learns
     * to stand between. A fifth of the gap between arms per turn means the
     * whole circle is covered over five turns, and the floor fills in rather
     * than being striped.
     */
    private void spray(Enemy self, AiContext ctx, int damage) {
        double turn = self.aiTimer * (Math.PI * 2 / FOUNTAIN_ARMS / 5.0);
        for (int i = 0; i < FOUNTAIN_ARMS; i++) {
            double a = turn + i * (Math.PI * 2 / FOUNTAIN_ARMS);
            float r = FOUNTAIN_MIN
                + (FOUNTAIN_MAX - FOUNTAIN_MIN) * ctx.rng().nextFloat();
            ctx.lobProjectile(self, self.x + (float) Math.cos(a) * r,
                self.y + (float) Math.sin(a) * r,
                damage, FOUNTAIN_FLIGHT, DROP_LINGER, self.def.projectile);
        }
    }
}
