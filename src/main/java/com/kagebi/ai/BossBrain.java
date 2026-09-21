package com.kagebi.ai;

import com.kagebi.Cfg;
import com.kagebi.combat.Damage;
import com.kagebi.data.def.EnemyDef;
import com.kagebi.combat.Faction;
import com.kagebi.combat.Hitbox;
import com.kagebi.entity.Boss;
import com.kagebi.entity.Enemy;

/**
 * A boss: the same seven states, with a choice of attack at each windup.
 *
 * <p>The move is picked at the moment the windup starts, so the telegraph is
 * telling the truth about which one is coming. The later-phase pool is what the
 * phase change is <em>for</em> - a boss whose second phase is only bigger
 * numbers reads as the fight getting longer rather than changing.
 *
 * <p>Deliberately not a scripted sequence. A fixed rotation is memorised in two
 * attempts and the fight becomes a dance recital; a weighted choice keeps the
 * player reading the telegraph, which is the skill the whole system is built
 * around.
 *
 * <p>One class, configured per boss, rather than a class per boss: giantfrog2
 * and tengured differ in which moves they have, not in how a move works.
 */
public final class BossBrain extends BaseBrain {

    public enum Move {
        /** A melee box along the facing, at the def's attack range. */
        SWING,
        /** A straight dash along a direction locked at the windup's end. */
        CHARGE,
        /** A hop onto the point the player stood on when the windup began. */
        SLAM,
        /** A fan of projectiles. */
        VOLLEY,
        /**
         * A flurry of blows along one locked direction, advancing.
         *
         * <p>The one move that takes ground away from the player instead of
         * asking them to give it up for a moment. Each blow shoves, and the
         * boss walks in behind it, so standing still inside it means being
         * carried backwards to the wall.
         */
        COMBO,
        /** The ranged twin of {@link #COMBO}: shot after shot down one line. */
        BARRAGE,
        /** A line of burning ground thrown out along the facing. */
        WALL,
        /** A closing ring of hazards with exactly one gap in it. */
        RING
    }

    /** Dash multiplier on walking speed. Lower than a charger's: it is bigger. */
    public static final float DASH_SPEED = 2.4f;
    /**
     * How far past the player a charge carries, and the furthest it goes at all.
     *
     * <p>A charge used to run for the whole active window whatever happened,
     * which on a boss whose window was sized for a three-blow combo meant three
     * seconds of dashing: it crossed the arena, hit the far wall, and ended up
     * further from the player than it started. It now stops a body's length
     * past where they were standing, which is what "he charged at me" is
     * supposed to look like. The ceiling is for the case where they are across
     * a 640px arena and the dash would otherwise be a journey.
     */
    public static final float CHARGE_OVERSHOOT = 22f;
    public static final float CHARGE_MAX = 150f;
    /** Ceiling on a dash's window; the distance above is what normally ends it. */
    public static final int CHARGE_STEPS = 44;

    /** Tail on a combo after its last blow, so it ends on a beat rather than a cut. */
    public static final int COMBO_TAIL = 34;
    /** Shots a barrage fires; its window is this many intervals. */
    public static final int BARRAGE_SHOTS = 6;
    /**
     * A volley and a ground effect are both placed on the first step and then
     * held. Twenty steps of holding is a follow-through; ninety-six - which is
     * what the defs carried, because the number also had to cover a barrage -
     * is the boss standing frozen for a second and a half after three arrows
     * have already left. That is the "hơi đơ" in the report.
     */
    public static final int VOLLEY_STEPS = 20;
    public static final int GROUND_STEPS = 26;
    /** The longest a slam carries, so a slam from across the arena lands short. */
    public static final float SLAM_MAX = 96f;
    /** Slam landing box as a multiple of the body: the shockwave is wider than the frog. */
    public static final float SLAM_SPREAD = 1.4f;

    public static final float PROJECTILE_SPEED = 80f;
    public static final int PROJECTILE_LIFE = 150;
    /** Shots in a volley, spread evenly across {@link #VOLLEY_ARC} radians. */
    public static final int VOLLEY_SHOTS = 5;
    public static final float VOLLEY_ARC = 1.0f;

    /**
     * Steps between blows of a combo, and how many land.
     *
     * <p>Sixty, because the player's hurt i-frames are forty. At anything
     * under that the second blow lands inside the first one's mercy window and
     * is eaten; at forty-eight it lands the moment they end, which leaves
     * eight steps to act in and is a stunlock rather than a combo. Sixty
     * leaves a third of a second - enough to roll out, not enough to stroll.
     */
    public static final int COMBO_INTERVAL = 60;
    public static final int COMBO_BLOWS = 3;
    /** Each blow of a combo is worth this much of a full swing. */
    public static final float COMBO_DAMAGE = 0.6f;
    /** And shoves this much harder, because being moved is the point of it. */
    public static final float COMBO_KNOCKBACK = 2f;
    /** It follows the player in at half pace, or they simply walk out of it. */
    public static final float COMBO_ADVANCE = 0.5f;

    /** Steps between shots of a barrage. Faster than a combo: nothing is shoved. */
    public static final int BARRAGE_INTERVAL = 14;
    /**
     * How long a barrage shot chases the player before it gives up and flies
     * straight, and how sharply it may turn while it does.
     *
     * <p>A line of arrows down a fixed heading is dodged by taking one step
     * sideways and then ignoring it, which is why it read as stiff. Homing
     * makes the first second of each shot a real question. It is bounded on
     * both ends so it stays answerable: the shot can only turn so fast, so a
     * player who keeps moving outruns the turn, and once the window is up it
     * commits to wherever it is pointing and is simply a shot again.
     */
    public static final int HOME_STEPS = 48;
    public static final float HOME_TURN = 0.055f;
    /** Homing shots are worth less, because they are much harder to walk out of. */
    public static final float HOME_DAMAGE = 0.4f;

    /** Tiles of burning ground a wall throws out, and the gap between them. */
    public static final int WALL_SEGMENTS = 5;
    public static final float WALL_SPACING = 26f;

    /** Hazards in a ring, and how far out it is laid. */
    public static final int RING_COUNT = 12;
    public static final float RING_RADIUS = 76f;
    /** Steps a ring and a wall stay dangerous, and the warning before they do. */
    public static final int GROUND_ARM = 20;
    public static final int GROUND_LINGER = 90;

    /** Spells dropped by an enrage, and the circle they fall in. */
    public static final int RAIN_COUNT = 9;
    public static final float RAIN_RADIUS = 110f;
    /**
     * How long the first of them takes to fall, and how much longer each
     * later one takes.
     *
     * <p>They are all thrown on the same step, so the stagger has to be in the
     * falling: a longer fall starts higher and lands later. That gets a wave
     * that sweeps rather than a wave that arrives, out of the one number the
     * drop already has, and with no scheduler anywhere near the room - which
     * matters, because a summon or a hazard owed to the room later is exactly
     * what {@code AiContext.summon} exists to warn about.
     */
    public static final int RAIN_FALL = 22;
    public static final int RAIN_STAGGER = 5;
    public static final int RAIN_LINGER = 80;
    /** Adds called in by an enrage, when the def names any to call. */
    public static final int ENRAGE_ADDS = 3;

    private final String id;
    private final Move[] opening;
    private final Move[] later;

    /**
     * What an enrage rains down, and how wide a fan this one throws.
     *
     * <p>Set once at construction and never written again - these objects are
     * one per id for the whole game, shared by every boss that names them, and
     * a field that changed during a fight would be a field two bosses shared.
     * What they shoot is data, on {@code EnemyDef.projectile}; what their
     * scripted moves look like is part of the script, and lives here.
     */
    private String rainFx;
    private int shots = VOLLEY_SHOTS;
    private float arc = VOLLEY_ARC;

    public BossBrain(String id, Move[] opening, Move[] later) {
        this.id = id;
        this.opening = opening;
        this.later = later;
    }

    private BossBrain fan(int shots, float arc) {
        this.shots = shots;
        this.arc = arc;
        return this;
    }

    private BossBrain rains(String fx) {
        this.rainFx = fx;
        return this;
    }

    /** Used when a boss names no brain of its own. */
    public static BossBrain generic() {
        return new BossBrain("boss",
            new Move[] {Move.CHARGE},
            new Move[] {Move.CHARGE, Move.VOLLEY});
    }

    /**
     * giantfrog2 ships jump and charge strips, and enemies.json describes the
     * fight as a hop-slam that "forces them to disengage". One phase.
     */
    public static BossBrain frog() {
        return new BossBrain("boss_frog",
            new Move[] {Move.SLAM, Move.SLAM, Move.CHARGE},
            new Move[] {Move.SLAM, Move.CHARGE, Move.VOLLEY});
    }

    /**
     * tengured: a swordsman first, and after the eleven-frame transformation
     * at half health, a swordsman who also throws.
     */
    public static BossBrain tengu() {
        return new BossBrain("boss_tengu",
            new Move[] {Move.CHARGE},
            new Move[] {Move.CHARGE, Move.VOLLEY, Move.VOLLEY});
    }

    /**
     * Pirate Leader, the first body of stage 6's chain: a swordsman, and
     * nothing else. No shot, no ground effect, no enrage - everything he does
     * he does by walking to the player and swinging, which is what makes the
     * two bodies after him read as an escalation rather than as more of the
     * same. The combo is his whole trick.
     */
    public static BossBrain pirateLeader() {
        return new BossBrain("boss_pirateleader",
            new Move[] {Move.SWING, Move.CHARGE, Move.COMBO},
            new Move[] {Move.SWING, Move.CHARGE, Move.COMBO, Move.COMBO});
    }

    /**
     * Pirate Zombie, the second body: the same fight at a distance. The wall
     * is what stops the arena being a circle to run round - it cuts the floor
     * in half and makes the next thirty steps about where to stand.
     */
    public static BossBrain pirateZombie() {
        return new BossBrain("boss_piratezombie",
            new Move[] {Move.VOLLEY, Move.VOLLEY, Move.BARRAGE, Move.WALL},
            new Move[] {Move.VOLLEY, Move.BARRAGE, Move.WALL, Move.WALL})
            .fan(3, 0.45f)
            .rains(com.kagebi.assets.Assets.Fx.FIRE_SPELL);
    }

    /**
     * Squidman, the third and last: three water arrows at once, and a tide
     * that closes from every side but one. The ring is the fight's last idea
     * and the only attack in the game a player answers by reading the floor
     * rather than the boss.
     */
    public static BossBrain squidman() {
        return new BossBrain("boss_squidman",
            new Move[] {Move.VOLLEY, Move.VOLLEY, Move.RING},
            new Move[] {Move.VOLLEY, Move.RING, Move.RING, Move.BARRAGE})
            .fan(3, 0.5f)
            .rains(com.kagebi.assets.Assets.Fx.WATER_SPELL);
    }

    @Override
    public String id() {
        return id;
    }

    /**
     * Whether this boss is on its second move pool.
     *
     * <p>Two ways in, because there are two kinds of second half. tengured
     * earns it by transforming at half health; stage 6's bodies earn it by
     * falling past {@code enrageAt} with no transformation at all, because a
     * chain of three bodies already has two of those in it.
     */
    private static boolean harder(Enemy self) {
        return self instanceof Boss
            && (((Boss) self).phase() >= 2 || ((Boss) self).enraged());
    }

    /**
     * A window the length of the move, not the length of the def.
     *
     * <p>{@code EnemyDef.activeSteps} is one number and a boss has eight
     * moves. Before this, the Drowned Captain's def carried 190 - the combo's
     * length, because the combo needed the most - and every swing he threw
     * stood open for 190 steps too. Three and a bit seconds of one swing is
     * not a slow animation, it is a stuck one, and both bodies after him had
     * the same problem at 96.
     *
     * <p>The def's own number stays the swing, which is what it reads as
     * everywhere else in enemies.json; the moves that need longer or shorter
     * ask for it here, next to the code that fills the time.
     */
    @Override
    protected int activeSteps(Enemy self) {
        return windowOf(moveOf(self), self.def);
    }

    private static int windowOf(Move move, EnemyDef def) {
        switch (move) {
            case COMBO:
                return COMBO_INTERVAL * (COMBO_BLOWS - 1) + COMBO_TAIL;
            case BARRAGE:
                return BARRAGE_SHOTS * BARRAGE_INTERVAL;
            case VOLLEY:
                return VOLLEY_STEPS;
            case WALL:
            case RING:
                return GROUND_STEPS;
            case CHARGE:
                // A ceiling only. Passing the player is what normally ends it.
                return Math.max(def.activeSteps, CHARGE_STEPS);
            default:
                return Math.max(1, def.activeSteps);
        }
    }

    /**
     * The longest window any of this boss's moves asks for. See AiBrain.
     *
     * <p>Computed from the same switch rather than written out beside it, so
     * a move whose length changes cannot drift away from the ceiling that is
     * supposed to bound it.
     */
    @Override
    public int longestActiveSteps(EnemyDef def) {
        int longest = Math.max(1, def.activeSteps);
        for (Move m : Move.values()) {
            longest = Math.max(longest, windowOf(m, def));
        }
        return longest;
    }

    private static Move moveOf(Enemy self) {
        return self instanceof Boss ? Move.values()[((Boss) self).move] : Move.SWING;
    }

    private static void setMove(Enemy self, Move m) {
        if (self instanceof Boss) {
            ((Boss) self).move = m.ordinal();
        }
    }

    @Override
    protected void chase(Enemy self, AiContext ctx) {
        // A boss never loses interest: there is nowhere in the arena to hide,
        // and a boss that wandered off would leave the room uncleared.
        if (self.cooldown == 0) {
            float dist = self.distanceTo(ctx.playerX(), ctx.playerY());
            if (dist <= self.def.attackRange) {
                setMove(self, Move.SWING);
                beginWindup(self, ctx);
                return;
            }
            if (dist <= self.def.aggroRange) {
                Move[] pool = harder(self) ? later : opening;
                setMove(self, pool[ctx.rng().nextInt(pool.length)]);
                beginWindup(self, ctx);
                return;
            }
        }
        approach(self, ctx, self.def.moveSpeed * self.speedMult);
    }

    @Override
    protected void windupStep(Enemy self, AiContext ctx) {
        Move move = moveOf(self);
        if (move == Move.COMBO || move == Move.BARRAGE
                || move == Move.WALL || move == Move.RING) {
            // Rooted, and flashing twice as fast as a slam's squat. These are
            // the long moves; the player is being told to move now, not to
            // brace.
            self.halt();
            self.facing = com.kagebi.Dir.of(ctx.playerX() - self.x, ctx.playerY() - self.y);
            if (self.stateSteps() % 6 < 3) {
                self.flashSteps = 2;
            }
            return;
        }
        if (move == Move.SLAM) {
            // A squat before a hop, not a lunge back.
            self.halt();
            if (self.stateSteps() % 6 < 3) {
                self.flashSteps = 2;
            }
            return;
        }
        super.windupStep(self, ctx);
    }

    @Override
    protected void onAttackStart(Enemy self, AiContext ctx) {
        Move move = moveOf(self);
        if (move == Move.SLAM) {
            lockAim(self, self.targetX, self.targetY);
            if (self.distanceTo(self.targetX, self.targetY) > SLAM_MAX) {
                self.targetX = self.x + self.aimX * SLAM_MAX;
                self.targetY = self.y + self.aimY * SLAM_MAX;
            }
            return;
        }
        lockAim(self, ctx.playerX(), ctx.playerY());
        if (move == Move.CHARGE) {
            // Where the dash stops: a body past where they were standing, or
            // the ceiling, whichever comes first. Kept in targetX/targetY the
            // way a slam keeps its landing point, so the step below only has
            // to ask whether it has arrived.
            float reach = Math.min(CHARGE_MAX,
                self.distanceTo(ctx.playerX(), ctx.playerY()) + CHARGE_OVERSHOOT);
            self.targetX = self.x + self.aimX * reach;
            self.targetY = self.y + self.aimY * reach;
            return;
        }
        if (move == Move.VOLLEY) {
            fireVolley(self, ctx);
        } else if (move == Move.WALL) {
            throwWall(self, ctx);
        } else if (move == Move.RING) {
            layRing(self, ctx);
        }
    }

    /**
     * A line of burning ground running away from the boss along its facing.
     *
     * <p>Laid all at once rather than crawling outward. A wall that grew would
     * be dodged by walking beside it and stepping over the end, which is the
     * opposite of what it is for; laid whole, it is a fence, and the question
     * is which side of it to be on.
     */
    private void throwWall(Enemy self, AiContext ctx) {
        int damage = Math.max(1, Math.round(self.def.attackDamage * self.damageMult * 0.75f));
        for (int i = 1; i <= WALL_SEGMENTS; i++) {
            ctx.placeHazard(self, self.x + self.aimX * WALL_SPACING * i,
                self.y + self.aimY * WALL_SPACING * i,
                damage, GROUND_ARM, GROUND_ARM + GROUND_LINGER, self.def.projectile);
        }
    }

    /**
     * A ring around the boss with one segment missing.
     *
     * <p>The gap is where the player was when the windup began, rotated half
     * a turn: it opens on the far side, so the answer is always to cross the
     * boss rather than to back away from it. A gap under the player's feet
     * would make the move free, and a ring with no gap at all would make it
     * unanswerable.
     */
    private void layRing(Enemy self, AiContext ctx) {
        int damage = Math.max(1, Math.round(self.def.attackDamage * self.damageMult * 0.75f));
        double away = Math.atan2(self.targetY - self.y, self.targetX - self.x) + Math.PI;
        for (int i = 0; i < RING_COUNT; i++) {
            double a = i * (Math.PI * 2 / RING_COUNT);
            double off = Math.abs(Math.atan2(Math.sin(a - away), Math.cos(a - away)));
            if (off < Math.PI * 2 / RING_COUNT * 1.5) {
                continue;               // the gap
            }
            ctx.placeHazard(self, self.x + (float) Math.cos(a) * RING_RADIUS,
                self.y + (float) Math.sin(a) * RING_RADIUS,
                damage, GROUND_ARM, GROUND_ARM + GROUND_LINGER, self.def.projectile);
        }
    }

    private void fireVolley(Enemy self, AiContext ctx) {
        int damage = Math.max(1, Math.round(self.def.attackDamage * self.damageMult));
        double base = Math.atan2(self.aimY, self.aimX);
        for (int i = 0; i < shots; i++) {
            double offset = arc * (i - (shots - 1) / 2.0) / Math.max(1, shots - 1);
            ctx.fireProjectile(self, (float) Math.cos(base + offset),
                (float) Math.sin(base + offset), PROJECTILE_SPEED, damage,
                PROJECTILE_LIFE, self.def.projectile);
        }
    }

    /**
     * Turns into whatever the def says it turns into, if anything.
     *
     * <p>One line, and it is the whole of stage 6's three-bodied boss. The
     * successor is spawned on the step this one dies rather than after a
     * pause, because the room latches itself cleared the moment nothing
     * hostile is left in it - see AiContext.summon.
     */
    @Override
    public void onDeath(Enemy self, AiContext ctx) {
        if (self.def.evolvesInto != null) {
            ctx.summon(self.def.evolvesInto, self.x, self.y, false);
        }
    }

    /**
     * One wave of spells, and whatever help the def can call for.
     *
     * <p>The wave first and the adds behind it: the player is reading the
     * floor when the slimes arrive, which is the point of doing both at once
     * rather than either alone.
     */
    @Override
    public void onEnrage(Enemy self, AiContext ctx) {
        int damage = Math.max(1, Math.round(self.def.attackDamage * self.damageMult * 0.8f));
        for (int i = 0; i < RAIN_COUNT; i++) {
            double a = ctx.rng().nextDouble() * Math.PI * 2;
            float r = RAIN_RADIUS * (0.3f + 0.7f * ctx.rng().nextFloat());
            ctx.rainSpell(self, ctx.playerX() + (float) Math.cos(a) * r,
                ctx.playerY() + (float) Math.sin(a) * r,
                damage, RAIN_FALL + i * RAIN_STAGGER, RAIN_LINGER, rainFx);
        }
        if (self.def.summons.length == 0) {
            return;
        }
        for (int i = 0; i < ENRAGE_ADDS; i++) {
            String id = self.def.summons[i % self.def.summons.length];
            double a = i * (Math.PI * 2 / ENRAGE_ADDS);
            float x = self.x + (float) Math.cos(a) * RING_RADIUS;
            float y = self.y + (float) Math.sin(a) * RING_RADIUS;
            // Asleep, with the ball that called them drawn over the top: the
            // summon has to be in the room now, so the effect plays over a
            // body that is already there rather than promising one later.
            Enemy add = ctx.summon(id, x, y, true);
            if (add != null) {
                ctx.spawnFx(self.def.projectile, x, y, true);
            }
        }
    }

    @Override
    protected void onAttackStep(Enemy self, AiContext ctx) {
        switch (moveOf(self)) {
            case VOLLEY:
                self.halt();
                return;
            case COMBO: {
                // Walking in behind its own blows. Without this the player
                // steps out during the third of a second between them and the
                // move reads as the boss flailing at nothing.
                self.moveToward(ctx.collision(), ctx.playerX(), ctx.playerY(),
                    self.def.moveSpeed * self.speedMult * COMBO_ADVANCE);
                int blow = self.attack().elapsed() / COMBO_INTERVAL;
                if (blow < COMBO_BLOWS && self.attack().elapsed() % COMBO_INTERVAL == 0) {
                    // Null swing, not this one: HitResolver claims a target in
                    // the swing's hit set before it tests i-frames, so one
                    // AttackState can never land on the player twice however
                    // long it stays open. A combo needs each blow to be its
                    // own, and the null path is the one the game already uses
                    // for contact damage.
                    ctx.strike(comboBox(self), null);
                }
                return;
            }
            case BARRAGE:
                self.halt();
                if (self.attack().elapsed() % BARRAGE_INTERVAL == 0) {
                    // Each one chases for a moment before committing, so the
                    // line is a question rather than a wall to sidestep once.
                    // Worth less than a volley arrow for exactly that reason.
                    ctx.homingProjectile(self, self.aimX, self.aimY, PROJECTILE_SPEED,
                        Math.max(1, Math.round(
                            self.def.attackDamage * self.damageMult * HOME_DAMAGE)),
                        PROJECTILE_LIFE, HOME_STEPS, HOME_TURN, self.def.projectile);
                }
                return;
            case WALL:
            case RING:
                self.halt();
                return;
            case CHARGE:
                // Arrived, or run out of room. Both end the dash the same way,
                // so a charge that reaches its mark stops there rather than
                // carrying on to the far wall.
                boolean past = (self.targetX - self.x) * self.aimX
                    + (self.targetY - self.y) * self.aimY <= 0f;
                if (past || !self.moveDir(ctx.collision(), self.aimX, self.aimY,
                        self.def.moveSpeed * DASH_SPEED * self.speedMult)) {
                    self.attack().cancel();
                    self.cooldown = self.def.cooldownSteps;
                    self.setState(AiState.RECOVER);
                    return;
                }
                ctx.strike(bodyBox(self, self.def.attackDamage), self.attack());
                return;
            case SLAM:
                int left = Math.max(1, self.def.activeSteps - self.attack().elapsed());
                float remaining = self.distanceTo(self.targetX, self.targetY);
                if (remaining > 0.5f) {
                    self.moveToward(ctx.collision(), self.targetX, self.targetY,
                        remaining / (left * Cfg.STEP));
                }
                ctx.strike(slamBox(self), self.attack());
                return;
            default:
                self.halt();
                ctx.strike(meleeBox(self), self.attack());
        }
    }

    /** A combo blow: a normal swing worth less, that shoves twice as hard. */
    private static Hitbox comboBox(Enemy self) {
        int damage = Damage.outgoing(
            Math.max(1, Math.round(self.def.attackDamage * COMBO_DAMAGE)),
            self.damageMult, false, 1f);
        return Hitbox.swing(self.x, self.y, self.facing,
            self.def.attackRange + ATTACK_OVERREACH, ATTACK_HALF_WIDTH,
            damage, ATTACK_KNOCKBACK * COMBO_KNOCKBACK, Faction.ENEMY);
    }

    private static Hitbox slamBox(Enemy self) {
        int damage = Damage.outgoing(self.def.attackDamage, self.damageMult, false, 1f);
        return Hitbox.body(self.x, self.y, self.bodyW * SLAM_SPREAD, self.bodyH * SLAM_SPREAD,
            damage, ATTACK_KNOCKBACK * 1.5f, Faction.ENEMY);
    }

    /** A transforming boss is untouchable, so it is also harmless to touch. */
    @Override
    public boolean harmfulOnContact(Enemy self) {
        if (self instanceof Boss && ((Boss) self).transforming()) {
            return false;
        }
        return super.harmfulOnContact(self);
    }
}
