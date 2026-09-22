package com.kagebi.entity;

import java.util.Random;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectIntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.kagebi.Cfg;
import com.kagebi.Dir;
import com.kagebi.ai.AiBrain;
import com.kagebi.ai.AiBrains;
import com.kagebi.ai.AiContext;
import com.kagebi.assets.Assets;
import com.kagebi.data.def.LootTableDef;
import com.kagebi.data.def.RelicDef;
import com.kagebi.data.def.SkillDef;
import com.kagebi.data.def.ItemDef;
import com.kagebi.data.def.QuestDef;
import com.kagebi.loot.LootRoller;
import com.kagebi.save.Profile;
import com.kagebi.data.ShopCatalog;
import com.kagebi.audio.AudioService;
import com.kagebi.combat.AttackState;
import com.kagebi.combat.Faction;
import com.kagebi.combat.HitResolver;
import com.kagebi.combat.Hitbox;
import com.kagebi.combat.Modifiers;
import com.kagebi.data.ContentRegistry;
import com.kagebi.data.ContentValidator;
import com.kagebi.data.def.EnemyDef;
import com.kagebi.data.def.FloorDef;
import com.kagebi.data.def.WeaponDef;
import com.kagebi.gen.CollisionGrid;
import com.kagebi.gen.Room;
import com.kagebi.gen.RoomKind;
import com.kagebi.gen.RoomTemplate;
import com.kagebi.gen.SpawnPoint;
import com.kagebi.gfx.Anim;
import com.kagebi.input.InputService;
import com.kagebi.quest.Quests;
import com.kagebi.run.RunState;
import com.kagebi.settings.Settings;

/**
 * One room's worth of simulation: the player, what is trying to kill them, and
 * what they drop.
 *
 * <p>It is also the {@link AiContext} every brain sees, which is why a brain
 * never holds a reference to anything: it is handed this each step, and this is
 * dropped and rebuilt with the room.
 *
 * <p><b>It survives missing content.</b> {@code ContentLoader} is being written
 * at the same time as this, so an enemy id with no def, a def naming a brain
 * nobody wrote, a sprite not in the atlas, and a weapon id that resolves to
 * nothing all degrade - skipped spawn, fallback brain, invisible enemy, starter
 * katana - rather than throwing three rooms into a run.
 *
 * <p><b>It runs with a null atlas.</b> Every sprite lookup is skipped and
 * {@link #renderActors} draws nothing, which is what lets the tests drive a
 * whole room - spawning, fighting, clearing, gold - at the real fixed step with
 * no GL context.
 *
 * <p>Two feel details live here rather than on an entity, because they stop the
 * whole room: <b>hit-stop</b>, a freeze of a few steps when a hit lands, which
 * is the single cheapest way to make a hit feel like it connected; and
 * <b>screen shake</b>, which the screen applies to its camera.
 */
public final class EntityWorld implements World, AiContext {

    /**
     * The starting katana, exactly as weapons.json has it (7 damage, 22 reach,
     * 9 half-width, 4/3/11 steps, 40 knockback). Used when the run's weapon id
     * resolves to nothing, so an empty registry still plays like the real game.
     */
    public static final WeaponDef FALLBACK_WEAPON = new WeaponDef("katana",
        "weapon.katana.name", "weapon.katana.desc", Assets.Actor.WEAPON_KATANA, -1,
        7, 22f, 9f, 4, 3, 11, 40f, 0, null);

    /**
     * Freeze steps when the player's swing lands. 3 steps is 50ms: long enough
     * that the eye registers the impact, short enough that a combo keeps its
     * rhythm. Measured by feel against 2 (reads as nothing) and 5 (reads as lag).
     */
    public static final int HITSTOP_LAND = 3;
    /** Freeze when the player is hit. Longer, because it should feel worse. */
    public static final int HITSTOP_HURT = 5;

    /** Camera shake in virtual pixels, before fading. */
    public static final float SHAKE_HURT = 3f;
    public static final float SHAKE_LAND = 1f;
    public static final float SHAKE_KILL = 1.5f;
    /** Per-step multiplier: 0.8 fades 3px to under half a pixel in eight steps. */
    public static final float SHAKE_DECAY = 0.8f;

    /** Shove when a body touches the player: 90 px/s moves them about 10px. */
    public static final float CONTACT_KNOCKBACK = 90f;

    /** How close a door edge must be to count as standing in it. */
    public static final float DOOR_MARGIN = 8f;
    /** How close the player must be to a chest, shop or stairs to be offered it. */
    public static final float INTERACT_RANGE = 20f;

    /** Steps per frame of the chest lid. 8 makes the four frames read as one act. */
    public static final int CHEST_STEPS = 8;

    /**
     * The step count a lid that finished opening long ago is held at.
     *
     * <p>Comfortably past any lid animation, and a ceiling rather than a
     * starting point: the counter stops here, so a chest opened on the first
     * floor of a long run cannot quietly count its way to overflow.
     */
    static final int OPEN_HELD = 4096;

    /**
     * A sought-out chest's chance of opening a locked weapon, and a locked
     * ninja.
     *
     * <p>Two and a half in a hundred together, and only on the chests worth
     * walking to - treasure, locked, secret and boss. Over a full descent that
     * is roughly one unlock every three or four runs, which is slow enough
     * that the village shop is still how a player gets a hammer and fast
     * enough that opening a chest is never only gold.
     */
    public static final float CHARACTER_FROM_CHEST = 0.005f;
    public static final float WEAPON_FROM_CHEST = 0.02f;

    /** Sideways nudge per extra projectile, as a fraction of forward speed. */
    public static final float FAN_SPREAD = 0.16f;

    /** How far a chained hit will reach for its second target, in pixels. */
    public static final float CHAIN_RANGE = 72f;
    /** What the arc carries. A full second hit would make the relic the build. */
    public static final float CHAIN_DAMAGE = 0.5f;

    /** Player projectiles, px/s. Fast enough to feel thrown rather than lobbed. */
    public static final float THROW_SPEED = 220f;
    /** Enemy projectile knockback: a nudge, since dodging it was the real test. */
    public static final float SHOT_KNOCKBACK = 40f;

    /** i18n keys this world advertises through {@link #promptKey}. */
    public static final String PROMPT_CHEST = "prompt.open_chest";
    public static final String PROMPT_DESCEND = "prompt.descend";
    public static final String PROMPT_SHOP = "prompt.shop";

    private final TextureAtlas actors;
    private final ContentRegistry content;
    private final RunState run;
    private final Settings settings;

    private final Player player;
    private final Array<Enemy> enemies = new Array<>();
    private final Array<Enemy> spawnQueue = new Array<>();
    private final Array<Projectile> projectiles = new Array<>();
    private final Array<Pickup> pickups = new Array<>();
    private final Array<Marker> markers = new Array<>();
    private final Array<Decor> decor = new Array<>();
    private final Array<DamagePop> pops = new Array<>();

    /**
     * One-shot effects: an animation, a place, and nothing else.
     *
     * <p>Modelled on {@link #pops} rather than on the entity list. A burst of
     * flame is not an actor - it has no body, no faction and nothing can hit
     * it - and making it one would put it in the depth sort and in every
     * sweep that looks for {@code removed}. It is scenery that finishes.
     */
    private final Array<OneShot> oneShots = new Array<>();

    /** Named fx regions, sliced once per room. Looked up by brains, by name. */
    private final ObjectMap<String, Anim> namedFx = new ObjectMap<>();
    /**
     * The skill strips, kept apart from {@link #namedFx} for their timing.
     *
     * <p>Everything in namedFx is sliced at a hard nine steps a frame, which is
     * right for a hazard that has to keep burning and far too slow for a
     * skill: four frames at nine steps is six tenths of a second, so a bolt
     * meant to punctuate a thrust would still be on screen after the thrust
     * had finished. These run at {@link #SKILL_STEPS_PER_FRAME}.
     */
    private final ObjectMap<String, Anim> skillFx = new ObjectMap<>();

    /** Three steps a frame: a four-frame bolt lasts a fifth of a second. */
    private static final int SKILL_STEPS_PER_FRAME = 3;
    private final Array<Entity> drawList = new Array<>();

    /**
     * The clock the wall torches flicker on. Separate from the combat step
     * because it keeps running through hit-stop: hit-stop is a device for
     * selling an impact, and freezing the scenery with it would say the impact
     * stopped the room rather than the fight.
     */
    private int decorSteps;

    private final Intent intent = new Intent();
    private InputService boundInput;
    private ActionSource boundSource;

    private CollisionGrid collision;
    private Room room;
    private Random rng = new Random(0L);

    private ShopCatalog shop;
    private Profile profile;
    /** What a chest just opened permanently, until the screen takes it. */
    private ShopCatalog.Unlock unlocked;
    private AudioService audio;

    /** Guards the once-per-run grants, which useVillage would otherwise repeat. */
    private boolean startingKitGiven;

    private int hitstop;
    private int burnTick;
    /** The player's drift over the last step, in pixels per second. */
    private float playerVelX;
    private float playerVelY;
    private float shake;
    private boolean descendRequested;
    private boolean shopRequested;

    // Regions from atlases this world was not handed; see useSharedAtlases.
    private TextureAtlas fxAtlas;
    private TextureAtlas uiAtlas;
    private boolean ownsFx;
    private boolean ownsUi;
    private Anim orbAnim;
    private Anim cloudAnim;
    private Anim torchAnim;
    private Anim sideTorchAnim;
    private Anim bannerAnim;
    private Anim chestAnim;
    private Anim chestOpenAnim;
    private TextureAtlas npcAtlas;
    private BitmapFont font;
    private TextureRegion pixel;
    /**
     * Thrown art by projectile id: a still picture turned to its heading, or a
     * strip that spins in place. One or the other for each id, never both.
     */
    private final ObjectMap<String, TextureRegion> thrownStill = new ObjectMap<>();
    private final ObjectMap<String, Anim> thrownSpin = new ObjectMap<>();
    private TextureRegion goldRegion;
    private TextureRegion heartRegion;
    private TextureRegion keyRegion;
    private TextureRegion gemRegion;

    /**
     * A chest, the stairs, or a shopkeeper: something the player walks up to.
     *
     * <p>An {@link Entity} purely so it can be drawn in the depth sort. It was
     * a bare struct for a long time and nothing ever drew it, which is how the
     * game shipped with invisible chests that could still be opened - the
     * prompt appeared, the loot dropped, and there was nothing on the floor to
     * explain either. The room's tiles do not draw them either: the generator
     * measured two chest tiles and never stamped one.
     *
     * <p>Being in the sort is the point rather than a detail. A chest stands in
     * the middle of the floor and is walked around, so a player above it has to
     * pass behind it; the wall torches in {@link Decor} can skip the sort
     * precisely because nothing can ever stand on them.
     */
    private static final class Marker extends Entity {
        final SpawnPoint.Kind kind;
        /**
         * Which chest of this room's chests this is, counting CHEST spawns in
         * template order from zero; -1 for anything that is not a chest.
         * {@link Room#chestsOpened} is indexed by it, which is how a chest
         * stays open after the player has walked out and back in.
         */
        final int index;
        boolean used;
        /** Set when the chest is opened, so the lid animation plays once. */
        private int openSteps = -1;
        private Anim resting;
        private Anim afterUse;
        /** False holds frame 0: a chest lid is an event, a shopkeeper breathes. */
        private boolean animates;

        Marker(SpawnPoint.Kind kind, int index, float x, float y) {
            this.kind = kind;
            this.index = index;
            this.x = x;
            this.y = y;
            this.bodyW = 16f;
            // 16, so footY() lands 8 below the centre and a 16px sprite draws
            // centred on the tile. The marker's position IS the thing the
            // player walks up to, so the art has to sit on it exactly.
            this.bodyH = 16f;
            this.hp = 1;
            this.maxHp = 1;
        }

        void art(Anim resting, Anim afterUse, boolean animates) {
            this.resting = resting;
            this.afterUse = afterUse;
            this.animates = animates;
        }

        void open() {
            used = true;
            openSteps = 0;
        }

        /**
         * Opened on an earlier visit: already empty, and already finished
         * opening.
         *
         * <p>Drawn rather than skipped, because the lid is the only thing that
         * says the room has been looted. The animation is non-looping, so
         * {@code Anim.frame} clamps a step count past its end to the last
         * frame - which is exactly the held-open pose, without playing the
         * lid again on every re-entry.
         */
        void openedEarlier() {
            used = true;
            openSteps = OPEN_HELD;
        }

        @Override
        public Faction faction() {
            return Faction.HAZARD;
        }

        /** Scenery. A swing that reaches a chest passes through it. */
        @Override
        public void takeHit(int damage, float fromX, float fromY, float knockback) {
        }

        @Override
        public void step(EntityWorld world) {
            animSteps++;
            if (openSteps >= 0 && openSteps < OPEN_HELD) {
                openSteps++;
            }
        }

        @Override
        public TextureRegion frame() {
            if (openSteps >= 0 && afterUse != null) {
                return afterUse.frame(facing, openSteps);
            }
            return resting == null ? null
                : resting.frame(facing, animates ? animSteps : 0);
        }
    }

    /**
     * @param actors null to run headless: no sprites are sliced, nothing draws,
     *               everything else behaves identically
     * @param settings null to run headless; screen shake then counts as on
     */
    public EntityWorld(TextureAtlas actors, ContentRegistry content,
                       RunState run, Settings settings) {
        this.actors = actors;
        this.content = content != null ? content : new ContentRegistry();
        this.run = run;
        this.settings = settings;
        this.player = new Player(run, resolveWeapon(run.weaponId),
            ActorSprites.player(actors, run.characterId),
            new Random(run.seed ^ 0x5DEECE66DL));
        refreshMods();
        if (actors != null) {
            loadOwnAtlases();
        }
    }

    /**
     * Hands over the village, so bought upgrades and the character's perk count
     * for as much as the relics picked up during the run. Separate from the
     * constructor because a test wants a player with relics and no village, and
     * because the screens do not all have one.
     */
    public void useVillage(ShopCatalog shop, Profile profile) {
        this.shop = shop;
        this.profile = profile;
        refreshMods();
        if (!startingKitGiven) {
            startingKitGiven = true;
            run.keys += player.mods().startKeysAdd();
        }
    }

    /** The one funnel for sound. Null leaves the world silent, as in tests. */
    public void useAudio(AudioService audio) {
        this.audio = audio;
    }

    /**
     * The font damage numbers are drawn in. Null leaves them unspawned, which
     * is the headless case: a test that drives a whole fight must not need a
     * glyph atlas to do it.
     */
    public void useFont(BitmapFont font) {
        this.font = font;
    }

    /**
     * Floats a damage number off an enemy and shows its health bar for a while.
     *
     * <p>Both cues at once because they answer different questions. The number
     * says how hard that hit was - which is the only way a crit, a relic or the
     * damage roll is ever visible. The bar says how much is left, which the
     * numbers cannot: the final boss has eighteen hundred health and no amount
     * of arithmetic in the player's head turns a stream of sevens into "nearly
     * there".
     */
    public void popDamage(Enemy e, int amount, boolean crit) {
        e.showHealthBar();
        if (font != null) {
            // Above the bar, not level with it. Started at the same height the
            // two drew over each other for the first half of the number's life,
            // and a figure sitting in a red bar is unreadable.
            pops.add(new DamagePop(amount, crit, e.x, barTop(e) + 8f));
        }
    }

    /** Bottom of an enemy's health bar: just clear of the top of its sprite. */
    private static float barTop(Enemy e) {
        return e.y + e.bodyH * 0.6f + 3f;
    }

    /**
     * Recomputes what the player's relics, upgrades and perk add up to. Called
     * whenever the set changes - which is to say when a relic is picked up -
     * rather than every step, because nothing else can change it.
     *
     * <p>The base is read from the run, not snapshotted here: see
     * {@link RunState#baseMaxHp}. Recomputing after a second relic must add the
     * second relic rather than the first twice, and so must a second world.
     */
    /**
     * Tells the journal something happened, if there is a journal to tell.
     *
     * <p>Counted into the profile as it happens rather than banked at the end
     * of the run, which is the opposite of how the bestiary works and is
     * deliberate: the book is a record of the run and a quest is a job. A
     * player who kills nine of the ten and dies has still killed nine, and
     * making them do it again would be the game forgetting on purpose.
     *
     * <p>Silent with no profile, which is the case in every headless test that
     * does not care about quests.
     */
    private void questRecord(QuestDef.Kind kind, String target, int amount) {
        if (profile != null && content != null) {
            Quests.record(content, profile, kind, target, amount);
        }
    }

    public void refreshMods() {
        player.setMods(Loadout.of(run, content, shop, profile, player.activeAvatar()));
        // Bound here too: the skills a character has and the numbers they have
        // are the same question asked twice, and answering them in two places
        // is how one of them ends up stale.
        player.setSkills(content == null ? null : content.allSkills());
        int bonus = player.mods().maxHpAdd();
        if (bonus > 0 && run.maxHp < run.baseMaxHp + bonus) {
            int added = run.baseMaxHp + bonus - run.maxHp;
            run.maxHp += added;
            // Extra maximum health that does not also heal is a relic the
            // player cannot feel picking up.
            run.hp = Math.min(run.maxHp, run.hp + added);
        }
    }

    private void sfx(String path) {
        if (audio != null) {
            audio.playSfx(path);
        }
    }

    // ---- World -------------------------------------------------------------

    @Override
    public void enterRoom(Room room, CollisionGrid collision, Dir enteredFrom) {
        this.room = room;
        this.collision = collision;
        enemies.clear();
        spawnQueue.clear();
        projectiles.clear();
        pickups.clear();
        markers.clear();
        decor.clear();
        pops.clear();
        oneShots.clear();
        hitstop = 0;
        shake = 0f;
        playerVelX = 0f;
        playerVelY = 0f;
        descendRequested = false;
        shopRequested = false;
        // Seeded by run and room, so the same room in the same run plays out
        // the same way - which is what makes a reported bug reproducible.
        rng = new Random(run.seed * 31L + room.gx * 73856093L + room.gy * 19349663L);
        WeaponDef held = resolveWeapon(run.weaponId);
        player.setWeapon(held, heldArt(held));
        player.setThrowWeapon(resolveThrowWeapon(run.throwWeaponId));
        // And who is holding them. The ninja was read once, in the constructor,
        // back when it could not change after the run started; the village's
        // loadout screen changes it, and a player who swaps ninja and then
        // watches the old one walk away has been told the swap did not work.
        if (actors != null) {
            ActorSprites worn = ActorSprites.player(actors, run.characterId);
            if (worn != null) {
                player.sprites = worn;
            }
        }

        placePlayer(room, enteredFrom);
        spawn(room);
        room.visited = true;
        if (hostilesAlive() == 0) {
            room.cleared = true;
        }
    }

    @Override
    public void step(InputService input) {
        if (input != boundInput) {
            boundInput = input;
            boundSource = input == null ? null : ActionSource.of(input);
        }
        stepWith(boundSource);
    }

    /**
     * One fixed step, reading intent from any source. {@link #step} adapts the
     * real input service onto this; tests call it with a scripted one.
     */
    public void stepWith(ActionSource source) {
        if (source != null) {
            intent.read(source);
        } else {
            intent.clear();
        }
        run.elapsedSeconds += Cfg.STEP;
        decorSteps++;
        shake = shake * SHAKE_DECAY < 0.05f ? 0f : shake * SHAKE_DECAY;

        if (hitstop > 0) {
            hitstop--;
            return;
        }
        int hpBefore = run.hp;

        float wasX = player.x;
        float wasY = player.y;
        player.step(this);
        // Differenced rather than read off the player, because the player does
        // not keep one: Intent is consumed and thrown away every step, and a
        // roll, a slide round a corner and a wall all change the distance
        // actually covered. What a brain needs to lead a shot is where the
        // body went, not where it was asked to go.
        playerVelX = (player.x - wasX) / Cfg.STEP;
        playerVelY = (player.y - wasY) / Cfg.STEP;
        if (player.hitsLandedThisStep > 0) {
            hitstop = Math.max(hitstop, HITSTOP_LAND);
            shake = Math.max(shake, SHAKE_LAND);
        }

        for (int i = 0; i < enemies.size; i++) {
            enemies.get(i).step(this);
        }
        applyContactDamage();
        stepBurnAura();
        for (int i = 0; i < projectiles.size; i++) {
            projectiles.get(i).step(this);
        }
        for (int i = 0; i < pickups.size; i++) {
            pickups.get(i).step(this);
        }
        for (int i = 0; i < markers.size; i++) {
            markers.get(i).step(this);
        }
        for (int i = oneShots.size - 1; i >= 0; i--) {
            oneShots.get(i).step();
            if (oneShots.get(i).done()) {
                oneShots.removeIndex(i);
            }
        }
        for (int i = pops.size - 1; i >= 0; i--) {
            pops.get(i).step();
            if (pops.get(i).done()) {
                pops.removeIndex(i);
            }
        }

        countDeaths();
        if (spawnQueue.size > 0) {
            enemies.addAll(spawnQueue);
            spawnQueue.clear();
        }
        sweep();

        // Minus what the player spent on purpose: the ultimate is bought
        // with health, and a cost is not a wound.
        if (run.hp < hpBefore - player.spentThisStep()) {
            hitstop = Math.max(hitstop, HITSTOP_HURT);
            shake = Math.max(shake, SHAKE_HURT);
            sfx(Assets.Sfx.HURT);
        }
        if (run.hp <= 0) {
            revive();
        }
        if (room != null && !room.cleared && hostilesAlive() == 0) {
            room.cleared = true;
            int heal = player.mods().roomClearHeal();
            if (heal > 0) {
                player.heal(heal);
                sfx(Assets.Sfx.HEAL);
            }
        }
    }

    /**
     * Spends a revive, if the player has one. Checked every step rather than at
     * the screen's death handling so that the run never actually ends: a revive
     * that fires after the game-over screen has appeared is not a revive.
     */
    private void revive() {
        Modifiers mods = player.mods();
        if (!mods.spendRevive()) {
            return;
        }
        run.hp = Math.max(1, Math.round(run.maxHp * mods.reviveFraction()));
        player.reviveAt(run.hp);
        shake = Math.max(shake, SHAKE_HURT);
        sfx(Assets.Sfx.ALERT);
    }

    @Override
    public void renderActors(SpriteBatch batch) {
        // Wall dressing before anything that moves. It needs no place in the
        // depth sort: every piece sits on a solid wall cell, so no actor can
        // ever occupy the same pixels and there is no ordering to get wrong.
        for (Decor d : decor) {
            d.draw(batch, decorSteps);
        }

        // Floor-level hazards first, so actors stand in a cloud rather than
        // under it.
        for (Projectile p : projectiles) {
            if (p.pierces()) {
                p.draw(batch);
            }
        }
        drawOneShots(batch, false);

        drawList.clear();
        drawList.add(player);
        for (Enemy e : enemies) {
            drawList.add(e);
        }
        for (Pickup p : pickups) {
            drawList.add(p);
        }
        for (Marker m : markers) {
            // The stairs are drawn by the screen, from the template, on a path
            // that predates markers; two circles on one tile helps nobody.
            if (m.kind != SpawnPoint.Kind.EXIT) {
                drawList.add(m);
            }
        }
        // Higher y first, so whatever stands lower on screen is drawn in front
        // of it. Without this a slime walking up past the player passes over
        // their head, and the room reads as a flat sticker sheet.
        drawList.sort((a, b) -> Float.compare(b.depth(), a.depth()));

        // Every shadow before any sprite, so no shadow ever lands on top of
        // another actor's feet.
        for (Entity e : drawList) {
            e.drawShadow(batch);
        }
        for (Entity e : drawList) {
            e.draw(batch);
        }

        for (Projectile p : projectiles) {
            if (!p.pierces()) {
                p.draw(batch);
            }
        }
        drawOneShots(batch, true);

        // Last, and in world space: a number or a bar hidden behind the sprite
        // it describes is worse than not drawing it, because the player sees a
        // flicker and learns to distrust it.
        for (Enemy e : enemies) {
            drawHealthBar(batch, e);
        }
        if (font != null) {
            for (DamagePop pop : pops) {
                pop.draw(batch, font);
            }
        }
    }

    /**
     * An animation playing itself out at a point, then gone.
     *
     * <p>{@code overhead} picks which of the two render passes draws it: an
     * impact on the floor belongs under the actors so they stand in it, and a
     * burst around a body belongs over them.
     */
    /**
     * A transient effect: an animation played once at a place, and gone.
     *
     * <p>Grew three fields when the skills arrived, and each is here because
     * the alternative was a second effect system beside this one:
     *
     * <ul>
     * <li><b>Rotation.</b> The lunge's bolt has to lie along the direction the
     *     player threw themselves, which is any of eight and not any of four.
     * <li><b>Scale.</b> The ultimate's opening stroke is one strip stretched
     *     the width of the screen; every other use is 1.
     * <li><b>A thing to follow.</b> The aura belongs to the player for as long
     *     as it lasts, and an aura pinned to where the player was standing when
     *     they cast it is a puddle rather than a glow.
     * </ul>
     */
    private static final class OneShot {
        final Anim anim;
        float x;
        float y;
        final boolean overhead;
        final float rotation;
        final float scaleX;
        final float scaleY;
        /** Followed each step, or null to stay where it was put. */
        final Entity follow;
        final float offsetY;
        int steps;

        OneShot(Anim anim, float x, float y, boolean overhead) {
            this(anim, x, y, overhead, 0f, 1f, 1f, null, 0f);
        }

        OneShot(Anim anim, float x, float y, boolean overhead, float rotation,
                float scaleX, float scaleY, Entity follow, float offsetY) {
            this.anim = anim;
            this.x = x;
            this.y = y;
            this.overhead = overhead;
            this.rotation = rotation;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.follow = follow;
            this.offsetY = offsetY;
        }

        /**
         * One pass and gone, whether or not the strip is a loop.
         *
         * <p>Not {@code anim.finished}, which is false forever for a looping
         * one - and every named effect is sliced looping, because a hazard has
         * to keep drawing for the whole of its linger. So the summon fireballs
         * never expired: three of them per enrage, left burning in the arena
         * for the rest of the fight. A one-shot is one pass by definition, and
         * that is a property of this list rather than of the strip it borrows.
         */
        boolean done() {
            return anim == null || steps >= anim.durationSteps();
        }

        void step() {
            steps++;
            if (follow != null) {
                x = follow.x;
                y = follow.y + offsetY;
            }
        }

        void draw(SpriteBatch batch) {
            TextureRegion f = anim.frame(Dir.DOWN, steps);
            if (f == null) {
                return;
            }
            float w = f.getRegionWidth();
            float h = f.getRegionHeight();
            // The rotating overload even when nothing rotates: one call site is
            // one thing to get wrong, and at scale 1 and angle 0 it draws the
            // same pixels as the short form.
            batch.draw(f, x - w / 2f, y - h / 2f, w / 2f, h / 2f, w, h,
                       scaleX, scaleY, rotation);
        }
    }

    private void drawOneShots(SpriteBatch batch, boolean overhead) {
        for (OneShot fx : oneShots) {
            if (fx.overhead == overhead) {
                fx.draw(batch);
            }
        }
    }

    /**
     * Width of an enemy's health bar, and how tall its filled strip is.
     *
     * <p>Fourteen, which is under the 16px an enemy occupies. Eighteen plus its
     * outline came to twenty and read as a wider object than the thing it
     * belonged to, which on a 320px screen is a lot of furniture for a cue that
     * is meant to be glanced at.
     */
    public static final int BAR_WIDTH = 14;
    public static final int BAR_HEIGHT = 2;

    private void drawHealthBar(SpriteBatch batch, Enemy e) {
        float fade = e.healthBarFade();
        if (pixel == null || fade <= 0f || !e.alive()) {
            return;
        }
        int x = Math.round(e.x - BAR_WIDTH / 2f);
        int y = Math.round(barTop(e));
        float left = Math.max(0f, Math.min(1f, e.hp / (float) e.maxHp));

        Color was = batch.getColor();
        float r = was.r;
        float g = was.g;
        float b = was.b;
        float a = was.a;
        // A dark trough under a red fill, so the bar reads on a cream floor and
        // on a near-black one without either needing its own colour.
        batch.setColor(0.07f, 0.05f, 0.08f, 0.8f * fade);
        batch.draw(pixel, x - 1, y - 1, BAR_WIDTH + 2, BAR_HEIGHT + 2);
        batch.setColor(0.85f, 0.24f, 0.20f, fade);
        batch.draw(pixel, x, y, Math.max(1, Math.round(BAR_WIDTH * left)), BAR_HEIGHT);
        batch.setColor(r, g, b, a);
    }

    @Override
    public float playerX() {
        return player.x;
    }

    @Override
    public float playerY() {
        return player.y;
    }

    @Override
    public float playerVelX() {
        return playerVelX;
    }

    @Override
    public float playerVelY() {
        return playerVelY;
    }

    @Override
    public Dir playerFacing() {
        return player.facing;
    }

    @Override
    public boolean roomCleared() {
        return room == null || room.cleared;
    }

    @Override
    public boolean playerDead() {
        return run.dead();
    }

    @Override
    public int playerDeathSteps() {
        return player == null ? 0 : player.deathDuration();
    }

    /**
     * Gated on the room being cleared. The screen owns the doors, but a world
     * that reported a doorway mid-fight would let the player walk out of every
     * room that went badly, and the roguelite stops having rooms.
     */
    @Override
    public Dir doorReached() {
        if (room == null || !roomCleared()) {
            return null;
        }
        float x = player.x;
        float y = player.y;
        if (x < DOOR_MARGIN && room.hasDoor(Dir.LEFT)) {
            return Dir.LEFT;
        }
        if (x > roomPixelWidth() - DOOR_MARGIN && room.hasDoor(Dir.RIGHT)) {
            return Dir.RIGHT;
        }
        if (y < DOOR_MARGIN && room.hasDoor(Dir.DOWN)) {
            return Dir.DOWN;
        }
        if (y > roomPixelHeight() - DOOR_MARGIN && room.hasDoor(Dir.UP)) {
            return Dir.UP;
        }
        return null;
    }

    @Override
    public String promptKey() {
        Marker m = nearestMarker();
        if (m == null) {
            return null;
        }
        switch (m.kind) {
            case CHEST: return PROMPT_CHEST;
            case EXIT: return PROMPT_DESCEND;
            case SHOPKEEPER: return PROMPT_SHOP;
            default: return null;
        }
    }

    @Override
    public void interact() {
        Marker m = nearestMarker();
        if (m == null) {
            return;
        }
        switch (m.kind) {
            case CHEST:
                openChest(m);
                break;
            case EXIT:
                descendRequested = true;
                break;
            case SHOPKEEPER:
                shopRequested = true;
                break;
            default:
                break;
        }
    }

    /**
     * Which loot table a chest rolls, by the room it stands in. A locked room
     * costs a key to enter, so its chest has to be worth the key.
     */
    private String chestTable() {
        if (room == null) {
            return "chest_common";
        }
        switch (room.kind) {
            case LOCKED: return "chest_locked";
            case TREASURE: return "chest_treasure";
            // chest_secret is written, balanced and was reachable from
            // nowhere: a secret room rolled the treasure table, so the one
            // table weighted towards things that change a run rather than pay
            // for one had never dropped anything.
            case SECRET: return "chest_secret";
            case BOSS: return "chest_boss";
            default: return "chest_common";
        }
    }

    private void openChest(Marker m) {
        m.open();
        // Written onto the room, not just onto the marker: the marker is torn
        // down and rebuilt on the next entry, and the room is what lasts.
        if (room != null && m.index >= 0) {
            room.markChestOpened(m.index);
        }
        sfx(Assets.Sfx.PICKUP);
        for (int i = 0, coins = 3 + rng.nextInt(4); i < coins; i++) {
            dropGold(m.x, m.y, 1 + rng.nextInt(3));
        }
        dropLoot(chestTable(), m.x, m.y);
        // A treasure room exists to hand out a relic. Its table drops potions
        // and keys, which are useful but are not a build - so the relic comes
        // from here, and only from the rooms whose whole purpose is to give one.
        if (room != null && (room.kind == RoomKind.TREASURE
                || room.kind == RoomKind.LOCKED || room.kind == RoomKind.SECRET)) {
            grantRelic();
        }
        if (room != null && room.kind != RoomKind.SHOP && room.kind != RoomKind.NORMAL) {
            ShopCatalog.Unlock won = rollUnlock(m.x, m.y);
            if (won != null) {
                unlocked = won;
                sfx(Assets.Sfx.UNLOCK);
            } else {
                // The shelf was empty, or the roll missed. Only the first of
                // those pays out; a miss is a miss.
                if (shop != null && profile != null && nothingLeftToUnlock()) {
                    dropPickup(Pickup.Kind.DIAMOND, 2, null, m.x, m.y);
                }
            }
        }
    }

    private boolean nothingLeftToUnlock() {
        for (ShopCatalog.Unlock u : shop.unlocks()) {
            if (!ShopCatalog.owned(u, profile) && ShopCatalog.requirementMet(u, profile)) {
                return false;
            }
        }
        return true;
    }

    /**
     * What a chest opened permanently, taken once.
     *
     * <p>Polled rather than pushed, like {@link #shopRequested}: writing the
     * profile to disk and putting a card on the screen are both the screen's
     * business, and the world has neither a save manager nor a font for it.
     */
    public ShopCatalog.Unlock takeUnlock() {
        ShopCatalog.Unlock won = unlocked;
        unlocked = null;
        return won;
    }

    /**
     * Rolls a chest's chance of opening something permanently: a weapon, or a
     * ninja.
     *
     * <p>Not a loot table entry, and it cannot be one. A table hands back an
     * item id which becomes a pickup and then a line in {@code RunState}; an
     * unlock is written into {@code Profile} and outlives the run entirely.
     * Those are different places and different lifetimes, so this is its own
     * roll beside {@link #grantRelic}.
     *
     * <p><b>Only what the player could already have earned.</b> The roll draws
     * from the unlocks that are unowned <em>and</em> whose requirement is
     * already met, because a hammer falling out of a chest for a player who
     * has not reached floor five is a reward that arrives without its story -
     * and it quietly deletes the milestone the requirement was there to mark.
     * A player who has met the requirement and not saved the gold is exactly
     * who this is for.
     *
     * <p>Nothing to give is not nothing to gain: a chest that rolls a hit with
     * the shelf empty pays in gems instead, so the roll is never wasted.
     *
     * @return what was opened, or null
     */
    private ShopCatalog.Unlock rollUnlock(float x, float y) {
        if (shop == null || profile == null) {
            return null;
        }
        Array<ShopCatalog.Unlock> weapons = new Array<>();
        Array<ShopCatalog.Unlock> prizes = new Array<>();
        for (ShopCatalog.Unlock u : shop.unlocks()) {
            if (ShopCatalog.owned(u, profile) || !ShopCatalog.requirementMet(u, profile)) {
                continue;
            }
            (u.kind == ShopCatalog.UnlockKind.WEAPON ? weapons : prizes).add(u);
        }
        if (weapons.isEmpty() && prizes.isEmpty()) {
            return null;
        }
        // Prizes - characters and colours - first and at a quarter of the
        // chance: they cost two to four times what a weapon does, and one
        // arriving is the rarer thing to have happen.
        float roll = rng.nextFloat();
        Array<ShopCatalog.Unlock> from = null;
        if (roll < CHARACTER_FROM_CHEST && !prizes.isEmpty()) {
            from = prizes;
        } else if (roll < CHARACTER_FROM_CHEST + WEAPON_FROM_CHEST && !weapons.isEmpty()) {
            from = weapons;
        }
        if (from == null) {
            return null;
        }
        ShopCatalog.Unlock won = from.get(rng.nextInt(from.size));
        ShopCatalog.grant(won, profile);
        return won;
    }

    /**
     * Gives a relic the run does not already hold.
     *
     * <p>Weighted by rarity rather than uniformly, because twenty-four relics
     * drawn flat means an epic is as common as a common and the rarities in the
     * content stop meaning anything. RelicDef carries no weight of its own -
     * the content agent listed it as a field it wished it had - so the weights
     * live here until it does.
     */
    public String grantRelic() {
        Array<RelicDef> pool = new Array<>();
        Array<Integer> weights = new Array<>();
        int total = 0;
        for (RelicDef r : content.allRelics()) {
            if (run.hasRelic(r.id)) {
                continue;
            }
            int w = r.rarity == RelicDef.Rarity.EPIC ? 10
                : r.rarity == RelicDef.Rarity.RARE ? 30 : 60;
            pool.add(r);
            weights.add(w);
            total += w;
        }
        if (total <= 0) {
            return null;
        }
        int pick = rng.nextInt(total);
        for (int i = 0; i < pool.size; i++) {
            pick -= weights.get(i);
            if (pick < 0) {
                run.relics.add(pool.get(i).id);
                refreshMods();
                sfx(Assets.Sfx.HEAL);
                return pool.get(i).id;
            }
        }
        return null;
    }

    @Override
    public float shake() {
        if (settings != null && !settings.screenShake()) {
            return 0f;
        }
        return shake;
    }

    @Override
    public void dispose() {
        if (ownsFx && fxAtlas != null) {
            fxAtlas.dispose();
        }
        if (ownsUi && uiAtlas != null) {
            uiAtlas.dispose();
        }
        fxAtlas = null;
        uiAtlas = null;
    }

    // ---- AiContext -----------------------------------------------------------

    @Override
    public boolean playerAlive() {
        return player.alive();
    }

    /**
     * The current room's width in pixels.
     *
     * <p>Off the collision grid rather than off {@code RoomTemplate}'s
     * constants, because stage 6's boss arena is 40x22 and every wall, door
     * and corner below has to be the arena's rather than a screen's. The grid
     * is handed over in {@link #enterRoom} before anything is placed in the
     * room, so it is set by the time any of this runs; the fallback is for the
     * AI tests, which step a world that was never given a room.
     */
    private float roomPixelWidth() {
        return collision != null
            ? collision.width() * CollisionGrid.TILE : RoomTemplate.PIXEL_WIDTH;
    }

    /** The current room's height in pixels. See {@link #roomPixelWidth()}. */
    private float roomPixelHeight() {
        return collision != null
            ? collision.height() * CollisionGrid.TILE : RoomTemplate.PIXEL_HEIGHT;
    }

    @Override
    public CollisionGrid collision() {
        return collision;
    }

    @Override
    public Random rng() {
        return rng;
    }

    @Override
    public boolean strike(Hitbox box, AttackState swing) {
        return HitResolver.hit(box, player, swing);
    }

    @Override
    public void fireProjectile(Enemy from, float dirX, float dirY, float speed,
                               int damage, int lifeSteps) {
        projectiles.add(new Projectile(Faction.ENEMY, from.x, from.y, dirX, dirY, speed,
            damage, SHOT_KNOCKBACK, lifeSteps, orbAnim, null, false));
    }

    @Override
    public void placeHazard(Enemy from, float x, float y, int damage, int armSteps,
                            int lifeSteps) {
        projectiles.add(Projectile.hazard(Faction.ENEMY, x, y, damage, armSteps,
            lifeSteps, cloudAnim));
    }

    @Override
    public void placeHazard(Enemy from, float x, float y, int damage, int armSteps,
                            int lifeSteps, String fx) {
        // A hazard is something lying on the ground burning, so it is drawn
        // with the puddle rather than with the comet that would have carried
        // it: fire_spell stretched into a 22x16 box is a smear, and a wall of
        // five of them read as five smears. burstOf hands anything that is not
        // one of the cove's spells straight back, so every other caster is
        // untouched.
        String art = Assets.Fx.burstOf(fx);
        Projectile p = Projectile.hazard(Faction.ENEMY, x, y, damage, armSteps,
            lifeSteps, fxOr(art, cloudAnim));
        if (!java.util.Objects.equals(art, fx)) {
            p.drawAs(Projectile.BURST_W, Projectile.BURST_H);
        }
        projectiles.add(p);
    }

    @Override
    public void fireProjectile(Enemy from, float dirX, float dirY, float speed,
                               int damage, int lifeSteps, String fx) {
        Projectile p = new Projectile(Faction.ENEMY, from.x, from.y, dirX, dirY, speed,
            damage, SHOT_KNOCKBACK, lifeSteps, fxOr(fx, orbAnim), null, false);
        if (Assets.Fx.aimed(fx)) {
            p.aimed();
        }
        projectiles.add(p);
    }

    @Override
    public void homingProjectile(Enemy from, float dirX, float dirY, float speed,
                                 int damage, int lifeSteps, int homeSteps,
                                 float turnRate, String fx) {
        Projectile p = new Projectile(Faction.ENEMY, from.x, from.y, dirX, dirY, speed,
            damage, SHOT_KNOCKBACK, lifeSteps, fxOr(fx, orbAnim), null, false);
        if (Assets.Fx.aimed(fx)) {
            p.aimed();
        }
        projectiles.add(p.homing(homeSteps, turnRate));
    }

    @Override
    public void lobProjectile(Enemy from, float toX, float toY, int damage,
                              int flightSteps, int lingerSteps, String fx) {
        projectiles.add(Projectile.lob(Faction.ENEMY, from.x, from.y,
            insideRoomX(toX), insideRoomY(toY),
            damage, flightSteps, lingerSteps,
            fxOr(fx, orbAnim), fxOr(Assets.Fx.burstOf(fx), cloudAnim)));
    }

    /**
     * Keeps a thing falling out of the sky inside the floor it is aimed at.
     *
     * <p>Both of the callers below aim at a point derived from the player -
     * scattered around them, or led ahead of them - and neither can see how
     * big the room is, because a brain is not shown one. A lob ignores walls
     * while it is airborne on purpose, so without this a spell thrown at
     * someone standing in a corner lands in the stone behind it and burns
     * where nobody can be hurt or even see it.
     *
     * <p>A margin of one tile rather than none: the burst is 32px wide, and a
     * puddle centred exactly on the wall line is half buried.
     */
    private float insideRoomX(float x) {
        return Math.max(CollisionGrid.TILE, Math.min(roomPixelWidth() - CollisionGrid.TILE, x));
    }

    private float insideRoomY(float y) {
        return Math.max(CollisionGrid.TILE, Math.min(roomPixelHeight() - CollisionGrid.TILE, y));
    }

    @Override
    public void rainSpell(Enemy from, float x, float y, int damage,
                          int fallSteps, int lingerSteps, String fx) {
        projectiles.add(Projectile.fall(Faction.ENEMY, insideRoomX(x), insideRoomY(y), damage,
            fallSteps, lingerSteps,
            fxOr(fx, orbAnim), fxOr(Assets.Fx.burstOf(fx), cloudAnim)));
    }

    @Override
    public void spawnFx(String fx, float x, float y, boolean overhead) {
        Anim anim = fxOr(fx, null);
        if (anim != null) {
            oneShots.add(new OneShot(anim, x, y, overhead));
        }
    }

    /**
     * The animation a brain named, or a fallback.
     *
     * <p>Every miss falls back rather than failing. A brain names regions as
     * strings because it has no atlas, so a build whose art has not been
     * packed yet must still be playable - the attack lands, it simply looks
     * like the generic orb while it does.
     */
    private Anim fxOr(String name, Anim fallback) {
        if (name == null) {
            return fallback;
        }
        Anim found = namedFx.get(name);
        return found != null ? found : fallback;
    }

    @Override
    public Enemy summon(String enemyId, float x, float y, boolean dormant) {
        if (content == null || enemyId == null || !content.hasEnemy(enemyId)) {
            return null;
        }
        Enemy child = buildEnemy(content.enemy(enemyId));
        if (child == null) {
            return null;
        }
        placeInRoom(child, x, y);
        child.dormant = dormant;
        child.dormantSteps = dormant ? SUMMON_SLEEP : 0;
        child.brain().onSpawn(child);
        if (!dormant) {
            child.setState(com.kagebi.ai.AiState.CHASE);
        }
        // Queued, not added: this is called from inside the loop over enemies,
        // and hostilesAlive() counts the queue - so the room does not blink
        // cleared between one body dying and the next one arriving, which is
        // the whole reason a boss can be a chain of them.
        spawnQueue.add(child);
        return child;
    }

    /**
     * How long a summoned body lies still while the effect that called it plays.
     *
     * <p>Twenty steps, a third of a second: the fireball strip is eight frames
     * at nine steps each, so the body is up before the ball has finished
     * fading, which is what makes it read as having come out of it.
     */
    public static final int SUMMON_SLEEP = 20;

    /**
     * Puts a body down somewhere it can actually be reached.
     *
     * <p>A brain picks the point - a ring around itself, a spread either side
     * of a corpse - and knows nothing about the walls, because {@code ai} is
     * given the collision grid to move against and no room size at all. A
     * ring of adds around a boss standing near the top of the arena therefore
     * lands half of them outside it, where nothing can be hit and the room can
     * never be cleared. That is exactly what FullRunTest found: two slimes at
     * y 393 in a room 352 tall.
     *
     * <p>Clamped into the room, then walked towards its centre until it is out
     * of the wall. Towards the centre rather than in any free direction,
     * because the centre is where the fight is and a body squeezed into a
     * corner pocket would be reachable but pointless.
     */
    private void placeInRoom(Enemy e, float x, float y) {
        float w = roomPixelWidth();
        float h = roomPixelHeight();
        float margin = Math.max(e.bodyW, e.bodyH);
        e.x = Math.max(margin, Math.min(w - margin, x));
        e.y = Math.max(margin, Math.min(h - margin, y));
        if (collision == null) {
            return;
        }
        float cx = w / 2f;
        float cy = h / 2f;
        for (int i = 0; i < PLACE_TRIES; i++) {
            if (!collision.overlaps(e.x - e.bodyW / 2f, e.y - e.bodyH / 2f, e.bodyW, e.bodyH)) {
                return;
            }
            e.x += (cx - e.x) * PLACE_PULL;
            e.y += (cy - e.y) * PLACE_PULL;
        }
        // Still stuck after walking most of the way in: the centre itself is
        // the last resort, and every room has open floor there.
        e.x = cx;
        e.y = cy;
    }

    /** How far towards the centre each attempt moves, and how many it gets. */
    private static final float PLACE_PULL = 0.2f;
    private static final int PLACE_TRIES = 12;

    @Override
    public void spawnCopy(Enemy parent, float x, float y, int hp) {
        Enemy child = new Enemy(parent.def, parent.brain(), parent.sprites);
        placeInRoom(child, x, y);
        child.maxHp = hp;
        child.hp = hp;
        child.generation = parent.generation + 1;
        child.brain().onSpawn(child);
        // Straight into the chase: a copy that idled would give the player a
        // free second to reposition, and the split is meant to be a surprise.
        child.setState(com.kagebi.ai.AiState.CHASE);
        child.cooldown = parent.def.cooldownSteps / 2;
        spawnQueue.add(child);
    }

    // ---- queries the entities use ------------------------------------------

    public Player player() {
        return player;
    }

    /**
     * The living boss in this room, or null.
     *
     * <p>The first one found, which is the only one: nothing in the game puts
     * two in a room, and stage 6's chain of five is five bodies one after
     * another rather than five at once.
     */
    public Enemy boss() {
        for (Enemy e : enemies) {
            if (e.def.boss && e.alive()) {
                return e;
            }
        }
        return null;
    }

    /** Every enemy in the room, dying ones included; the resolver skips the dead. */
    public Array<Enemy> hostiles() {
        return enemies;
    }

    public Array<Projectile> projectiles() {
        return projectiles;
    }

    public Array<Pickup> pickups() {
        return pickups;
    }

    public Intent intent() {
        return intent;
    }

    public RunState run() {
        return run;
    }

    public Room room() {
        return room;
    }

    public int hitstop() {
        return hitstop;
    }

    /**
     * The player used the stairs. Not on {@link World}, which predates stairs;
     * the screen polls it until it is folded into the interface. See notes/b.md.
     */
    public boolean descendRequested() {
        return descendRequested;
    }

    /**
     * Whether the player just asked the shopkeeper to trade, clearing the ask.
     *
     * <p>Consuming rather than merely reporting, unlike {@link
     * #descendRequested}: descending ends the room, so a flag left raised there
     * can never be seen twice. A shop can be closed and the player can walk on,
     * and a flag left raised would reopen it on the next interaction anywhere
     * in the room - including on a chest.
     */
    public boolean shopRequested() {
        boolean asked = shopRequested;
        shopRequested = false;
        return asked;
    }

    public int hostilesAlive() {
        int n = 0;
        for (Enemy e : enemies) {
            if (e.alive()) {
                n++;
            }
        }
        for (Enemy e : spawnQueue) {
            if (e.alive()) {
                n++;
            }
        }
        return n;
    }

    /**
     * The player threw their weapon.
     *
     * <p>The bandolier relic adds projectiles, fanned rather than stacked: extra
     * shots on the same line would land on the same target and read as one
     * shot doing more damage, which is what a damage relic is for.
     */
    void throwFrom(Player p, WeaponDef w, int damage, boolean crit) {
        int life = Math.max(1, Math.round(w.reach / THROW_SPEED / Cfg.STEP));
        int extra = Math.max(0, p.mods().throwExtra());
        sfx(Assets.Sfx.THROW);
        String id = w.projectile == null ? "kunai" : w.projectile;
        Anim spin = thrownSpin.get(id);
        TextureRegion still = spin != null ? null : thrownStill.get(id, thrownStill.get("kunai"));
        for (int i = 0; i <= extra; i++) {
            // 0, then +/-1, +/-2 ... spread of about nine degrees a step.
            int rank = (i + 1) / 2;
            float spread = (i % 2 == 0 ? rank : -rank) * FAN_SPREAD;
            float dx = p.facing.dx - p.facing.dy * spread;
            float dy = p.facing.dy + p.facing.dx * spread;
            projectiles.add(new Projectile(Faction.PLAYER, p.x, p.y, dx, dy,
                THROW_SPEED, damage, w.knockback, life, spin, still, crit));
        }
    }

    /** Steps per frame of a spinning projectile: the shuriken's two frames turn it ten times a second. */
    static final int SPIN_STEPS = 3;

    /**
     * Whether a projectile picture is a strip of square frames that spins in
     * place, rather than one picture turned to its heading. The shuriken is
     * two 16px frames side by side; the kunai is a single 14x5 blade.
     */
    static boolean spins(int width, int height) {
        return height > 0 && width % height == 0 && width / height >= 2;
    }

    /**
     * Lets the screen hand over the atlases it already holds, so this world
     * stops holding second copies of them. Safe to call at any time.
     */
    public void useSharedAtlases(TextureAtlas ui, TextureAtlas fx) {
        dispose();
        ownsFx = false;
        ownsUi = false;
        fxAtlas = fx;
        uiAtlas = ui;
        resolveExtraRegions();
    }

    // ---- internals ---------------------------------------------------------

    private WeaponDef resolveWeapon(String id) {
        if (id != null) {
            for (WeaponDef w : content.allWeapons()) {
                if (id.equals(w.id)) {
                    return w;
                }
            }
        }
        return FALLBACK_WEAPON;
    }

    /**
     * The off hand, or null.
     *
     * <p>No fallback, because an empty off hand is the normal state - the game
     * starts with one and the throw key is meant to do nothing until a kunai is
     * bought. A melee weapon named here is refused rather than equipped: the
     * throw path would send a hammer flying as a kunai sprite.
     */
    private WeaponDef resolveThrowWeapon(String id) {
        if (id == null) {
            return null;
        }
        for (WeaponDef w : content.allWeapons()) {
            if (id.equals(w.id)) {
                return w.thrown() ? w : null;
            }
        }
        return null;
    }

    private void placePlayer(Room room, Dir enteredFrom) {
        float px = roomPixelWidth() / 2f;
        float py = roomPixelHeight() / 2f;
        SpawnPoint entry = null;
        String side = enteredFrom == null ? null : enteredFrom.name().toLowerCase();
        for (SpawnPoint s : room.template.spawns) {
            if (s.kind != SpawnPoint.Kind.ENTRY) {
                continue;
            }
            if (side != null ? side.equals(s.tag) : (entry == null)) {
                entry = s;
            }
        }
        Dir face = Dir.DOWN;
        if (entry != null) {
            px = entry.x;
            py = entry.y;
        } else if (enteredFrom != null) {
            // Step in from the door rather than onto it, or the screen would
            // immediately read the player as standing in a doorway again. The
            // same arithmetic as the placeholder the screen was written against.
            px += enteredFrom.dx * (roomPixelWidth() / 2f - 32f);
            py += enteredFrom.dy * (roomPixelHeight() / 2f - 32f);
        }
        if (enteredFrom != null) {
            face = enteredFrom.opposite();
        }
        player.placeAt(px, py, face);
    }

    /**
     * The enemy a spawn point asks for: a named one, the floor's boss, or a roll.
     *
     * <p>The tag {@code boss} is not an id and never was - it is the word the
     * map writes to mean "the boss of whatever floor this room turns out to be
     * on", because a room template is shared by every floor that uses its
     * biome and cannot know which. It used to fall through to the pool, so
     * every boss room in the game quietly opened with one extra trash mob
     * standing on the mark the boss was meant to occupy, and the boss itself
     * arrived through the fallback at the room's centre. The Drowned Cove's
     * arena is where that showed: a slime wandering a boss chain.
     */
    private EnemyDef enemyFor(String tag) {
        if (tag == null) {
            return rollFromPool();
        }
        if (content.hasEnemy(tag)) {
            return content.enemy(tag);
        }
        if (BOSS_TAG.equals(tag)) {
            FloorDef floor = floorDef();
            return floor != null && floor.hasBoss() && content.hasEnemy(floor.boss)
                ? content.enemy(floor.boss) : null;
        }
        return rollFromPool();
    }

    /** What a template writes on the spawn its floor's boss should stand on. */
    public static final String BOSS_TAG = "boss";

    /**
     * Fills a room with what its template says is in it.
     *
     * <p>Two things are remembered from an earlier visit, and they are
     * remembered on {@link Room} because that is the object that survives
     * walking out of the door. Enemies do not come back once the room is
     * cleared, and a chest does not refill once it has been opened. The
     * second of those used to be missing, which left every treasure room a
     * two-second loop: walk out, walk in, press E, and take the identical
     * loot again off an RNG re-seeded to the identical state.
     */
    private void spawn(Room room) {
        boolean spawnEnemies = !room.cleared;
        boolean bossSpawned = false;
        int chestIndex = 0;
        for (SpawnPoint s : room.template.spawns) {
            switch (s.kind) {
                case ENEMY:
                    if (spawnEnemies) {
                        EnemyDef def = enemyFor(s.tag);
                        if (def != null && !(def.boss && bossSpawned)) {
                            spawnEnemy(def, s.x, s.y);
                            bossSpawned |= def.boss;
                        } else if (def == null) {
                            log("no enemy for spawn " + s + " on floor " + run.floor);
                        }
                    }
                    break;
                case CHEST: {
                    // Still drawn when it has been emptied: the open lid is
                    // the only thing that tells the player they have been
                    // here. nearestMarker skips a used marker, so the prompt
                    // does not come back with it.
                    Marker chest = dress(new Marker(s.kind, chestIndex, s.x, s.y));
                    if (room.chestOpened(chestIndex)) {
                        chest.openedEarlier();
                    }
                    chestIndex++;
                    markers.add(chest);
                    break;
                }
                case EXIT:
                case SHOPKEEPER:
                    markers.add(dress(new Marker(s.kind, -1, s.x, s.y)));
                    break;
                case PROP:
                    addDecor(s);
                    break;
                default:
                    break;
            }
        }
        if (spawnEnemies && room.kind == RoomKind.BOSS && !bossSpawned) {
            FloorDef floor = floorDef();
            if (floor != null && floor.hasBoss() && content.hasEnemy(floor.boss)) {
                spawnEnemy(content.enemy(floor.boss),
                    roomPixelWidth() / 2f, roomPixelHeight() * 0.62f);
            }
        }
    }

    /**
     * Turns one wall marker into a piece of scenery.
     *
     * <p>Which torch to use is read off the position rather than named by the
     * map: a marker against the left or right wall gets the side-on sprite, and
     * the one on the right is mirrored so its bracket faces into the stone.
     * The generator would have to spell all three cases out otherwise, and a
     * map that said "side torch" while sitting on the top wall would draw a
     * torch bracketed to thin air.
     *
     * <p>An unknown tag is skipped silently on purpose. It is scenery: a map
     * naming a prop this build has no art for should cost the player nothing,
     * and the log line would fire once per room for the whole run.
     */
    private void addDecor(SpawnPoint s) {
        boolean side = s.x < Cfg.TILE || s.x > roomPixelWidth() - Cfg.TILE;
        Anim anim;
        if ("banner".equals(s.tag)) {
            anim = bannerAnim;
        } else if ("torch".equals(s.tag)) {
            anim = side ? sideTorchAnim : torchAnim;
        } else {
            return;
        }
        if (anim == null) {
            return;
        }
        // Phase from the position, not from a counter, so the same room always
        // flickers the same way and a screenshot of it is reproducible.
        int phase = ((s.x * 7 + s.y * 13) % anim.frameCount()) * Decor.FLICKER_STEPS;
        boolean flip = side && s.x > roomPixelWidth() / 2;
        decor.add(new Decor(anim, s.x, s.y, phase, flip));
    }

    /**
     * Gives a marker the art it draws with, if this build has any.
     *
     * <p>The stairs get none: {@code DungeonScreen} draws its own magic circle
     * from the room template, on a path that predates markers entirely. Drawing
     * one here as well would put two things on the same tile.
     */
    private Marker dress(Marker m) {
        if (m.kind == SpawnPoint.Kind.CHEST) {
            m.art(chestAnim, chestOpenAnim, false);
        } else if (m.kind == SpawnPoint.Kind.SHOPKEEPER && npcAtlas != null
                && npcAtlas.findRegion(Assets.Npc.idle(Assets.Npc.MERCHANT)) != null) {
            m.art(Anim.directional(npcAtlas, Assets.Npc.idle(Assets.Npc.MERCHANT), 16,
                Anim.DEFAULT_STEPS_PER_FRAME, true), null, true);
        }
        return m;
    }

    /**
     * Hands over the village atlas, so the dungeon's shopkeeper has a face.
     * Separate from {@link #useSharedAtlases} because that pair is on the
     * {@code World} interface and the hub does not need this one.
     */
    public void useNpcAtlas(TextureAtlas npc) {
        this.npcAtlas = npc;
        for (Marker m : markers) {
            dress(m);
        }
    }

    /**
     * One enemy, built and scaled but not placed and not in the room yet.
     *
     * <p>Split out from {@link #spawnEnemy} for {@link #summon}, which must
     * put its result on the spawn queue rather than into {@code enemies}: it
     * is called from inside the loop over that array.
     */
    private Enemy buildEnemy(EnemyDef def) {
        AiBrain brain = AiBrains.createOrFallback(def.brain);
        if (!AiBrains.knows(def.brain)) {
            log("enemy '" + def.id + "' names unknown brain '" + def.brain
                + "'; using " + AiBrains.FALLBACK);
        }
        ActorSprites sprites = ActorSprites.enemy(actors, def);
        if (actors != null && sprites == null) {
            log("enemy '" + def.id + "' sprite '" + def.sprite + "' not in the atlas");
        }
        Enemy e = def.boss
            ? new Boss(def, brain, sprites,
                ActorSprites.transformation(actors, Assets.Actor.bossIdOf(def.sprite)))
            : new Enemy(def, brain, sprites);
        scaleHealth(e, def.boss);
        scaleToFloor(e);
        return e;
    }

    /**
     * Applies the floor's own multipliers, which are 1 on every floor but one.
     *
     * <p>Separate from {@link #scaleHealth}, which is the player's chosen
     * difficulty and applies everywhere. This is the floor saying "these
     * enemies, but a stage deeper": the Sunken Vault fights the cove's three
     * slimes and has to be harder than the cove, and the alternative was three
     * more defs on the same three sprites. {@code damageMult} is a field the
     * brains already read for an enrage, so nothing downstream changes.
     */
    private void scaleToFloor(Enemy e) {
        FloorDef floor = floorDef();
        if (floor == null) {
            return;
        }
        if (floor.hpScale != 1f) {
            e.maxHp = Math.max(1, Math.round(e.maxHp * floor.hpScale));
            e.hp = e.maxHp;
        }
        e.damageMult *= floor.damageScale;
    }

    /** Places an enemy. Public so a test, or a debug console, can populate a room. */
    public Enemy spawnEnemy(EnemyDef def, float x, float y) {
        Enemy e = buildEnemy(def);
        e.x = x;
        e.y = y;
        e.brain().onSpawn(e);
        enemies.add(e);
        return e;
    }

    /**
     * Applies the run's difficulty to one enemy's health, at full health.
     *
     * <p>Here rather than in {@link Enemy}: the def is the content as authored
     * and an enemy has no idea which run it belongs to. Bosses take a gentler
     * factor - the final one is already the longest fight in the game, and a
     * fifth more of it is not harder, only longer.
     */
    private void scaleHealth(Enemy e, boolean boss) {
        if (run == null) {
            return;
        }
        e.maxHp = run.difficulty.scaleHp(e.maxHp, boss);
        e.hp = e.maxHp;
    }

    /**
     * The current floor's def, or null.
     *
     * <p>Caught rather than searched for: {@code ContentRegistry} has no
     * {@code hasFloor}, and {@code allFloors()} stops at the first missing
     * number - so a floors.json that defines floor 5 but not yet floor 4 would
     * make the floor-5 boss silently vanish. Once per room entry, so the cost of
     * the exception is nothing.
     */
    private FloorDef floorDef() {
        if (content == null || run == null) {
            return null;                // a unit test with no content around it
        }
        try {
            return content.floor(run.floor);
        } catch (IllegalArgumentException missing) {
            return null;
        }
    }

    /** A weighted pick from the floor's pool, over the ids that actually resolve. */
    private EnemyDef rollFromPool() {
        FloorDef floor = floorDef();
        if (floor == null || floor.enemies == null) {
            return null;
        }
        int total = 0;
        for (int i = 0; i < floor.enemies.length; i++) {
            if (content.hasEnemy(floor.enemies[i])) {
                total += weight(floor, i);
            }
        }
        if (total <= 0) {
            return null;
        }
        int roll = rng.nextInt(total);
        for (int i = 0; i < floor.enemies.length; i++) {
            if (!content.hasEnemy(floor.enemies[i])) {
                continue;
            }
            roll -= weight(floor, i);
            if (roll < 0) {
                return content.enemy(floor.enemies[i]);
            }
        }
        return null;
    }

    private static int weight(FloorDef floor, int i) {
        return floor.enemyWeights != null && i < floor.enemyWeights.length
            ? Math.max(0, floor.enemyWeights[i]) : 1;
    }

    /**
     * Called by the player when a swing connects, once per step rather than
     * once per target: a wide hammer that catches three enemies should not heal
     * for three swings' worth.
     */
    public void onPlayerHitLanded(Player p, int damagePerTarget, int targets) {
        sfx(Assets.Sfx.HIT);
        float steal = p.mods().lifesteal();
        if (steal > 0f) {
            int healed = Math.round(damagePerTarget * targets * steal);
            if (healed > 0) {
                p.heal(healed);
            }
        }
    }

    /**
     * A thrown weapon landed, which is the same event as a swing landing.
     *
     * <p>Routed through both of the callbacks the main hand uses rather than
     * just the one that pops a number. The complaint that found this was "the
     * off hand shows no damage and no enemy health", but the cause was that a
     * projectile told the world nothing at all - so it was also silent, also
     * did not lifesteal, and also did not carry poison, a slow or a chain. Half
     * a fix would have left a weapon that hurts things without ever saying so.
     *
     * <p>One consequence worth naming: on-hit relics now work off the off hand.
     * That is what the relics say they do, and the bandolier is a relic for the
     * off hand in the first place - but it is a change in balance, not only in
     * feedback.
     */
    public void onThrownHitLanded(java.util.List<com.kagebi.combat.Combatant> struck,
                                  int damage, boolean crit) {
        onPlayerHitLanded(player, damage, struck.size());
        applyOnHit(player, struck, damage, crit);
    }

    /**
     * The relics that mark what they hit: poison, a slow, a chain.
     *
     * <p>Separate from {@link #onPlayerHitLanded} because these need to know
     * <em>which</em> enemy rather than how many, and a chain has to pick a
     * second target that was not one of the first.
     */
    public void applyOnHit(Player p, java.util.List<com.kagebi.combat.Combatant> struck,
                           int damage, boolean crit) {
        // Numbers first, and outside the early return below: every hit gets one
        // whether or not the player happens to own an on-hit relic.
        for (com.kagebi.combat.Combatant c : struck) {
            if (c instanceof Enemy) {
                popDamage((Enemy) c, damage, crit);
            }
        }
        // A bolt on the head of everything struck, while the storm is up.
        // Before this, a melee hit spawned no sprite at all - the only signs
        // were a flash, a number and a sound - so this is the first time a
        // sword landing looks like anything.
        if (p.transformed()) {
            shockStruck(struck);
        }

        Modifiers mods = p.mods();
        float slow = mods.slowOnHit();
        int poison = mods.poisonOnHit();
        float chain = mods.chainLightning();
        if (slow <= 0f && poison <= 0 && chain <= 0f) {
            return;
        }
        for (com.kagebi.combat.Combatant c : struck) {
            if (!(c instanceof Enemy)) {
                continue;
            }
            Enemy e = (Enemy) c;
            e.slow(slow, Modifiers.SLOW_STEPS);
            e.poison(poison, Modifiers.POISON_STEPS);
        }
        if (chain > 0f && rng.nextFloat() < chain) {
            chainTo(struck, damage);
        }
    }

    /**
     * The strips the ultimate casts on the player's behalf, and the sizes it
     * casts them at.
     *
     * <p>Named here rather than in {@code skills.json} because these are not
     * choices a skill makes - they are what the ultimate <em>is</em>, the same
     * way a lunge moving is what a lunge is. The file decides the numbers; the
     * shape is code, and this is the shape.
     */
    private static final String AVATAR_FLASH = "bolt";
    private static final String TRAIL_FX = "trail";
    private static final String CHAIN_FX = "strike";
    private static final String SHOCK_FX = "shock";

    /** How many enemies a dash arcs to while the ultimate is up. */
    private static final int DASH_CHAIN_TARGETS = 3;
    /** A bolt lands on the head, not the feet. */
    private static final float CHAIN_LIFT = 10f;
    /** How far behind the player the dash trail is dropped. */
    private static final float TRAIL_BACK = 8f;
    /** The box a lunge carries through the world with it. */
    private static final float LUNGE_W = 14f;
    private static final float LUNGE_H = 12f;

    // ---- skills ----------------------------------------------------------------

    /**
     * Spawns one of the skill strips, with everything a skill needs of it.
     *
     * <p>A separate door from {@link #spawnFx} because the two draw from
     * different maps: the named effects are a brain's business and run slowly,
     * these are the player's and run fast.
     *
     * @param rotation degrees, counter-clockwise from pointing right
     * @param follow   an entity to ride, or null to stay put
     */
    void skillFx(String name, float x, float y, boolean overhead, float rotation,
                 float scaleX, float scaleY, Entity follow, float offsetY) {
        Anim anim = skillFx.get(name);
        if (anim != null) {
            oneShots.add(new OneShot(anim, x, y, overhead, rotation,
                                     scaleX, scaleY, follow, offsetY));
        }
    }

    void skillFx(String name, float x, float y, boolean overhead) {
        skillFx(name, x, y, overhead, 0f, 1f, 1f, null, 0f);
    }

    /**
     * The ring: everything inside the radius is hurt and thrown outwards.
     *
     * <p>Straight to {@code takeHit} rather than through a {@link Hitbox},
     * for the reason the chain arc does the same - the shape here is a circle
     * and a hitbox is a rectangle, so building one would be answering a
     * different question less accurately.
     *
     * <p>The shove comes from the player's own centre, so everything is pushed
     * radially outward; {@link Knockback} has the fallback that stops an enemy
     * standing exactly on the player from producing a NaN and vanishing.
     */
    int castNova(SkillDef skill, int damage) {
        int hit = 0;
        for (Enemy e : within(skill.range)) {
            e.takeHit(damage, player.x, player.y, skill.knockback);
            hit++;
        }
        skillFx(skill.vfx, player.x, player.y, false);
        sfx(hit > 0 ? Assets.Sfx.HIT : Assets.Sfx.SWING);
        if (hit > 0) {
            hitstop = Math.max(hitstop, HITSTOP_LAND);
            shake = Math.max(shake, SHAKE_LAND);
        }
        return hit;
    }

    /**
     * The thrust: whatever the player passes through on the way is hurt once.
     *
     * <p>Resolved per step against a box around the player rather than as one
     * long sweep at the end, so an enemy standing halfway along is hit as the
     * player reaches it rather than after arriving. {@code struck} carries
     * across the whole lunge, which is what stops the same enemy being hit
     * eighteen times on the way past.
     */
    int lungeThrough(SkillDef skill, int damage, java.util.List<com.kagebi.combat.Combatant> struck) {
        Hitbox box = Hitbox.body(player.x, player.y, LUNGE_W, LUNGE_H, damage,
                                 skill.knockback, Faction.PLAYER);
        int hit = 0;
        for (Enemy e : enemies) {
            if (!e.alive() || struck.contains(e)) {
                continue;
            }
            if (HitResolver.hit(box, e, null)) {
                struck.add(e);
                hit++;
            }
        }
        if (hit > 0) {
            hitstop = Math.max(hitstop, HITSTOP_LAND);
            shake = Math.max(shake, SHAKE_LAND);
            sfx(Assets.Sfx.HIT);
        }
        return hit;
    }

    /**
     * The ultimate's opening stroke: one bolt drawn the width of the screen.
     *
     * <p>Stretched rather than tiled. A bolt is a shape that has no natural
     * length, so a viewer reads a long one as a long bolt and not as a short
     * one pulled - which is exactly the property that makes tiling pointless
     * and stretching free.
     */
    void avatarFlash() {
        Anim anim = skillFx.get(AVATAR_FLASH);
        if (anim == null) {
            return;
        }
        TextureRegion frame = anim.frame(Dir.DOWN, 0);
        float across = Cfg.VIRT_W * 1.5f / Math.max(1, frame.getRegionWidth());
        skillFx(AVATAR_FLASH, player.x, player.y, true, 0f, across, 1.5f, player, 0f);
        shake = Math.max(shake, SHAKE_LAND * 3f);
    }

    /** The aura that says the ultimate is still up, riding the player. */
    void avatarAura(SkillDef skill) {
        skillFx(skill.vfx, player.x, player.y, false, 0f, 1f, 1f, player, 0f);
    }

    /**
     * The three nearest, zapped: what dashing does while the ultimate is up.
     *
     * @return how many were caught, which may be none if nothing is in range
     */
    int dashChain(SkillDef skill, int damage) {
        int hit = 0;
        for (Enemy e : within(skill.range, DASH_CHAIN_TARGETS, null)) {
            e.takeHit(damage, player.x, player.y, skill.knockback);
            skillFx(CHAIN_FX, e.x, e.y + CHAIN_LIFT, true);
            hit++;
        }
        if (hit > 0) {
            sfx(Assets.Sfx.HIT);
        }
        return hit;
    }

    /** The trail left behind a dash, pointing the way it went. */
    void dashTrail(float dirX, float dirY) {
        float angle = com.badlogic.gdx.math.MathUtils.atan2(dirY, dirX)
            * com.badlogic.gdx.math.MathUtils.radiansToDegrees;
        skillFx(TRAIL_FX, player.x - dirX * TRAIL_BACK, player.y - dirY * TRAIL_BACK,
                false, angle, 1f, 1f, null, 0f);
    }

    /** A bolt over the head of everything a swing landed on. */
    void shockStruck(java.util.List<com.kagebi.combat.Combatant> struck) {
        for (com.kagebi.combat.Combatant c : struck) {
            skillFx(SHOCK_FX, c.centreX(), c.centreY() + CHAIN_LIFT, true);
        }
    }

    // ---- who is close ----------------------------------------------------------

    /**
     * Living enemies within a radius of the player, nearest first.
     *
     * <p>Three places asked this question before, each with its own squared
     * distance loop written out: the chain arc, the burning aura and now the
     * skills. Four copies of the same loop is where they start to disagree -
     * one of them counts a dying enemy, another measures from a corner - so it
     * is written once and the callers say what they want rather than how.
     *
     * <p>The list is reused between calls, so read it before asking again.
     */
    private final Array<Enemy> nearby = new Array<>();

    Array<Enemy> within(float range) {
        return within(range, Integer.MAX_VALUE, null);
    }

    /**
     * @param most    how many to take, nearest first
     * @param exclude enemies already dealt with, or null for none
     */
    Array<Enemy> within(float range, int most, java.util.List<?> exclude) {
        nearby.clear();
        float limit = range * range;
        for (Enemy e : enemies) {
            if (!e.alive() || (exclude != null && exclude.contains(e))) {
                continue;
            }
            float dx = e.x - player.x;
            float dy = e.y - player.y;
            if (dx * dx + dy * dy <= limit) {
                nearby.add(e);
            }
        }
        // Sorted so "the three nearest" means the three nearest rather than the
        // first three the spawn order happens to hold.
        nearby.sort((a, b) -> Float.compare(distanceSq(a), distanceSq(b)));
        if (nearby.size > most) {
            nearby.truncate(most);
        }
        return nearby;
    }

    private float distanceSq(Enemy e) {
        float dx = e.x - player.x;
        float dy = e.y - player.y;
        return dx * dx + dy * dy;
    }

    /** Arcs a hit to the nearest enemy that was not already caught by the swing. */
    private void chainTo(java.util.List<com.kagebi.combat.Combatant> struck, int damage) {
        Array<Enemy> reachable = within(CHAIN_RANGE, 1, struck);
        if (reachable.isEmpty()) {
            return;
        }
        Enemy best = reachable.first();
        // Straight to takeHit rather than through a hitbox: the arc has no
        // geometry, and building one would only be a way of asking the resolver
        // a question this has already answered.
        best.takeHit(Math.max(1, Math.round(damage * CHAIN_DAMAGE)),
            player.x, player.y, 20f);
        sfx(Assets.Sfx.HIT);
    }

    /**
     * Burns whatever is standing close. Ticks once a second rather than every
     * step, so an aura of 4 is four damage a second and not two hundred and
     * forty.
     */
    private void stepBurnAura() {
        int burn = player.mods().burnAura();
        if (burn <= 0 || !player.alive()) {
            burnTick = 0;
            return;
        }
        if (++burnTick < Modifiers.TICK_STEPS) {
            return;
        }
        burnTick = 0;
        for (Enemy e : within(Modifiers.BURN_RANGE)) {
            e.takeHit(burn, player.x, player.y, 0f);
        }
    }

    /** A swing started. Heavier weapons get the heavier sound. */
    public void onSwingBegun(WeaponDef weapon) {
        sfx(weapon != null && weapon.thrown() ? Assets.Sfx.THROW
            : weapon != null && weapon.rootSteps > 0 ? Assets.Sfx.SWING_HEAVY
            : Assets.Sfx.SWING);
    }

    private void applyContactDamage() {
        if (!player.alive()) {
            return;
        }
        for (Enemy e : enemies) {
            if (!e.alive() || !e.brain().harmfulOnContact(e)) {
                continue;
            }
            Hitbox body = Hitbox.body(e.x, e.y, e.bodyW, e.bodyH,
                Math.max(1, Math.round(e.def.contactDamage * e.damageMult)),
                CONTACT_KNOCKBACK, Faction.ENEMY);
            HitResolver.hit(body, player, null);
        }
    }

    private void countDeaths() {
        for (Enemy e : enemies) {
            if (e.alive() || e.deathCounted) {
                continue;
            }
            e.deathCounted = true;
            run.kills++;
            // Met, for the bestiary. On the kill rather than on the spawn: a
            // book that filled itself from across a dark room would record
            // things the player never actually saw.
            run.met.add(e.def.id);
            questRecord(QuestDef.Kind.KILL, e.def.id, 1);
            shake = Math.max(shake, SHAKE_KILL);
            sfx(Assets.Sfx.DEATH);
            e.brain().onDeath(e, this);
            int healOnKill = player.mods().healOnKill();
            if (healOnKill > 0) {
                player.heal(healOnKill);
            }
            // A splitter's children drop nothing, or splitting would double the
            // gold a mushroom is worth, and the room would pay for its own trap.
            if (e.generation == 0) {
                if (e.def.goldMax > 0) {
                    dropGold(e.x, e.y, LootRoller.gold(e.def, rng.nextLong()));
                }
                dropLoot(e.def.lootTable, e.x, e.y);
            }
        }
    }

    /**
     * Drops something on the floor. Public because rolling loot tables is the
     * loot package's job, and this is where it hands the result back.
     */
    public Pickup dropPickup(Pickup.Kind kind, int amount, String itemId, float x, float y) {
        TextureRegion region;
        switch (kind) {
            case GOLD: region = goldRegion; break;
            case DIAMOND: region = gemRegion; break;
            case HEART: region = heartRegion; break;
            case KEY: region = keyRegion; break;
            default: region = null; break;
        }
        Pickup p = new Pickup(kind, amount, itemId, x, y, region);
        pickups.add(p);
        return p;
    }

    /**
     * Rolls a loot table and puts what comes out on the floor.
     *
     * <p>One pickup per {@code Drop}, because a three-roll chest can legally
     * return the same item twice and a player who sees one potion appear for a
     * chest that gave them two has been robbed as far as they can tell.
     */
    public void dropLoot(String tableId, float x, float y) {
        if (tableId == null || !content.hasLootTable(tableId)) {
            return;
        }
        LootTableDef table = content.lootTable(tableId);
        for (LootRoller.Drop drop : LootRoller.roll(table, rng.nextLong(),
                player.mods().luckAdd())) {
            if (!content.hasItem(drop.itemId)) {
                continue;
            }
            ItemDef item = content.item(drop.itemId);
            Pickup.Kind kind;
            switch (item.kind) {
                case GOLD: kind = Pickup.Kind.GOLD; break;
                case DIAMOND: kind = Pickup.Kind.DIAMOND; break;
                case KEY: kind = Pickup.Kind.KEY; break;
                case INSTANT: kind = Pickup.Kind.HEART; break;
                default: kind = Pickup.Kind.ITEM; break;
            }
            float jx = (rng.nextFloat() - 0.5f) * 14f;
            float jy = (rng.nextFloat() - 0.5f) * 8f;
            dropPickup(kind, drop.count, item.id, x + jx, y + jy);
        }
    }

    /**
     * Takes a pickup off the floor. Gold is multiplied here rather than where it
     * was dropped, so a relic picked up after the coin still pays.
     */
    public void collect(Pickup p) {
        // Every pickup counts towards a COLLECT step, whichever kind it is:
        // the id is what a quest names, and a heart and a gem arrive by
        // different branches below but are the same thing to the journal.
        if (p.itemId != null) {
            questRecord(QuestDef.Kind.COLLECT, p.itemId, Math.max(1, p.amount));
        }
        switch (p.kind) {
            case GOLD:
                run.gold += Math.max(1, Math.round(p.amount * player.mods().goldMult()));
                sfx(Assets.Sfx.COIN);
                break;
            case DIAMOND:
                // Not multiplied by goldMult. The fortune track is priced
                // against gold income, and letting it compound the scarce
                // currency as well would make it the only upgrade worth
                // buying twice.
                run.diamonds += p.amount;
                sfx(Assets.Sfx.KEY_GET);
                break;
            case KEY:
                run.keys += p.amount;
                sfx(Assets.Sfx.KEY_GET);
                break;
            case HEART:
                if (p.itemId != null && content.hasItem(p.itemId)) {
                    applyItem(content.item(p.itemId));
                } else {
                    player.heal(p.amount);
                }
                sfx(Assets.Sfx.HEAL);
                break;
            default:
                if (p.itemId == null) {
                    break;
                }
                if (content.hasRelic(p.itemId)) {
                    if (!run.hasRelic(p.itemId)) {
                        run.relics.add(p.itemId);
                        refreshMods();
                    }
                } else {
                    run.addItem(p.itemId, p.amount);
                }
                sfx(Assets.Sfx.PICKUP);
                break;
        }
    }

    /**
     * Uses a consumable from the inventory. Public because the inventory screen
     * is what decides when, and this is what decides what happens.
     *
     * @return false when the player has none of it
     */
    public boolean useItem(String itemId) {
        if (itemId == null || !content.hasItem(itemId) || !run.spendItem(itemId)) {
            return false;
        }
        applyItem(content.item(itemId));
        return true;
    }

    /**
     * What the quick key would use now: the item the player put on it while one
     * is carried, else whatever heals, else any consumable - lowest id first, so
     * the choice does not flicker from one frame to the next.
     *
     * @return null when nothing usable is carried
     */
    public String quickItem() {
        if (run.quickItem != null && run.items.get(run.quickItem, 0) > 0 && content.hasItem(run.quickItem)) {
            return run.quickItem;
        }
        String healing = null;
        String any = null;
        for (ObjectIntMap.Entry<String> e : new ObjectIntMap.Entries<>(run.items)) {
            if (e.value <= 0 || !content.hasItem(e.key)
                || content.item(e.key).kind != ItemDef.Kind.CONSUMABLE) {
                continue;
            }
            if (any == null || e.key.compareTo(any) < 0) {
                any = e.key;
            }
            if ("heal".equals(content.item(e.key).effect)
                && (healing == null || e.key.compareTo(healing) < 0)) {
                healing = e.key;
            }
        }
        return healing != null ? healing : any;
    }

    /** The quick key: uses {@link #quickItem}, if there is one, and says whether it did. */
    public boolean useQuickItem() {
        String id = quickItem();
        if (id == null || !useItem(id)) {
            return false;
        }
        sfx("heal".equals(content.item(id).effect) ? Assets.Sfx.HEAL : Assets.Sfx.PICKUP);
        return true;
    }

    /**
     * What an item's effect actually does. The names are the content's, and
     * ContentValidator rejects any it does not recognise, so an unknown one
     * here means this list and that one have drifted - worth a log rather than
     * a silent shrug.
     */
    private void applyItem(ItemDef item) {
        int magnitude = Math.round(item.magnitude);
        switch (item.effect) {
            case "heal":
                player.heal(magnitude);
                break;
            case "max_hp_add":
                run.maxHp += magnitude;
                player.heal(magnitude);
                break;
            case "gold":
                run.gold += Math.max(1, Math.round(magnitude * player.mods().goldMult()));
                break;
            case "diamond":
                // No goldMult, for the reason collect() gives: the fortune
                // track is priced against gold income and must not compound
                // the scarce currency as well.
                run.diamonds += magnitude;
                break;
            case "key":
                run.keys += magnitude;
                break;
            case "cure_poison":
                player.clearPoison();
                break;
            case "speed_buff":
                player.buffSpeed(item.magnitude, Modifiers.BUFF_STEPS);
                break;
            case "damage_buff":
                player.buffDamage(item.magnitude, Modifiers.BUFF_STEPS);
                break;
            case "shield_buff":
                player.addShield(magnitude);
                break;
            case "drop_aggro":
                for (Enemy e : enemies) {
                    e.brain().forget(e, magnitude);
                }
                break;
            case "reveal_map":
                if (run.layout != null) {
                    for (Room r : run.layout.rooms()) {
                        r.visited = true;
                    }
                }
                break;
            default:
                log("item '" + item.id + "' has effect '" + item.effect
                    + "', which nothing here implements");
                break;
        }
    }

    private void dropGold(float x, float y, int amount) {
        // Scattered a few pixels so a pile reads as several coins, not one.
        float jx = (rng.nextFloat() - 0.5f) * 10f;
        float jy = (rng.nextFloat() - 0.5f) * 6f;
        dropPickup(Pickup.Kind.GOLD, amount, null, x + jx, y + jy);
    }

    private void sweep() {
        for (int i = enemies.size - 1; i >= 0; i--) {
            if (enemies.get(i).removed) {
                enemies.removeIndex(i);
            }
        }
        for (int i = projectiles.size - 1; i >= 0; i--) {
            if (projectiles.get(i).removed) {
                projectiles.removeIndex(i);
            }
        }
        for (int i = pickups.size - 1; i >= 0; i--) {
            if (pickups.get(i).removed) {
                pickups.removeIndex(i);
            }
        }
    }

    /**
     * How many chests this room has put on the floor, emptied ones included.
     *
     * <p>Package-private, for the test that an emptied chest is still drawn.
     * That is the half of the fix no behaviour can show: the prompt going away
     * is easy to check, and a chest quietly vanishing from the room because it
     * had been looted would pass every one of those checks.
     */
    int chestsDrawn() {
        int n = 0;
        for (Marker m : markers) {
            if (m.kind == SpawnPoint.Kind.CHEST) {
                n++;
            }
        }
        return n;
    }

    /** Whether the chest at that template index has been emptied. */
    boolean chestEmptied(int index) {
        for (Marker m : markers) {
            if (m.kind == SpawnPoint.Kind.CHEST && m.index == index) {
                return m.used;
            }
        }
        return false;
    }

    private Marker nearestMarker() {
        Marker best = null;
        float bestDist = INTERACT_RANGE;
        for (Marker m : markers) {
            if (m.used) {
                continue;
            }
            // The stairs only open on a cleared floor exit; a chest in a room
            // still being fought over is not on offer either.
            if (!roomCleared() && m.kind != SpawnPoint.Kind.SHOPKEEPER) {
                continue;
            }
            float dx = m.x - player.x;
            float dy = m.y - player.y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d <= bestDist) {
                best = m;
                bestDist = d;
            }
        }
        return best;
    }

    /**
     * Loads the fx and ui atlases itself, because the constructor that the
     * screen was written against only hands over the actors atlas. That costs a
     * second copy of both pages - about 20MB of texture - until the screen calls
     * {@link #useSharedAtlases}; see notes/b.md.
     */
    private void loadOwnAtlases() {
        if (Gdx.files == null) {
            return;
        }
        try {
            if (Gdx.files.internal(Assets.ATLAS_FX).exists()) {
                fxAtlas = new TextureAtlas(Gdx.files.internal(Assets.ATLAS_FX));
                ownsFx = true;
            }
            if (Gdx.files.internal(Assets.ATLAS_UI).exists()) {
                uiAtlas = new TextureAtlas(Gdx.files.internal(Assets.ATLAS_UI));
                ownsUi = true;
            }
        } catch (RuntimeException e) {
            log("could not load fx/ui atlases; projectiles and pickups will not draw: " + e);
        }
        resolveExtraRegions();
    }

    private void resolveExtraRegions() {
        orbAnim = null;
        cloudAnim = null;
        namedFx.clear();
        skillFx.clear();
        thrownStill.clear();
        thrownSpin.clear();
        torchAnim = null;
        sideTorchAnim = null;
        bannerAnim = null;
        if (fxAtlas != null) {
            if (fxAtlas.findRegion(Assets.Fx.PROJECTILE_ORB) != null) {
                orbAnim = Anim.strip(fxAtlas, Assets.Fx.PROJECTILE_ORB, 4, true);
            }
            if (fxAtlas.findRegion(Assets.Fx.HAZARD_CLOUD) != null) {
                cloudAnim = Anim.strip(fxAtlas, Assets.Fx.HAZARD_CLOUD, 8, true);
            }
            for (String name : ContentValidator.SKILL_VFX) {
                if (fxAtlas.findRegion(Assets.Fx.skill(name)) != null) {
                    skillFx.put(name, Anim.strip(fxAtlas, Assets.Fx.skill(name),
                                                 SKILL_STEPS_PER_FRAME, true));
                }
            }
            for (String id : ContentValidator.PROJECTILES) {
                String name = Assets.Fx.projectile(id);
                TextureRegion art = fxAtlas.findRegion(name);
                if (art == null) {
                    continue;
                }
                if (spins(art.getRegionWidth(), art.getRegionHeight())) {
                    thrownSpin.put(id, Anim.strip(fxAtlas, name, SPIN_STEPS, true));
                } else {
                    thrownStill.put(id, art);
                }
            }
            // Named effects, sliced once. Nine steps a frame on an eight
            // frame strip is about 1.2 seconds, which is how long a spell
            // lies burning; the strip is the whole life of the hazard rather
            // than a loop played over it.
            namedFx.clear();
            for (String name : Assets.Fx.NAMED) {
                if (fxAtlas.findRegion(name) != null) {
                    namedFx.put(name, Anim.strip(fxAtlas, name, 9, true));
                }
            }
            torchAnim = loop(Assets.Prop.TORCH);
            sideTorchAnim = loop(Assets.Prop.SIDE_TORCH);
            bannerAnim = loop(Assets.Prop.BANNER);
            // Not loops: both strips run a lid from shut to open, so they play
            // once and hold. CHEST_STEPS is slower than a torch flicker because
            // this one is a single event the player should see happen.
            chestAnim = once(Assets.Prop.CHEST);
            chestOpenAnim = once(Assets.Prop.CHEST_OPEN);
        }
        pixel = uiAtlas == null ? null : uiAtlas.findRegion(Assets.Ui.PIXEL);
        goldRegion = uiAtlas == null ? null : uiAtlas.findRegion(Assets.Ui.COIN);
        heartRegion = uiAtlas == null ? null : uiAtlas.findRegion(Assets.Ui.PICKUP_HEART);
        keyRegion = uiAtlas == null ? null : uiAtlas.findRegion(Assets.Ui.KEY);
        gemRegion = uiAtlas == null ? null : uiAtlas.findRegion(Assets.Ui.GEM);
    }

    /**
     * The sheet of this weapon being swung, or null when there is not one.
     *
     * <p>One step per frame and non-looping, the same as the player's own
     * attack sheet, because {@code ActorSprites.frameOf} stretches the frames
     * over the swing's real length rather than the art assuming a tempo. That
     * is what keeps the blade on the same frame as the arm holding it.
     */
    private Anim heldArt(WeaponDef w) {
        if (actors == null || w == null || w.sprite == null || w.thrown()
                || actors.findRegion(w.sprite) == null) {
            return null;
        }
        return Anim.directional(actors, w.sprite, Player.WEAPON_CELL, 1, false);
    }

    /** A looping strip from the fx atlas, or null when it is not packed. */
    private Anim loop(String region) {
        return fxAtlas.findRegion(region) == null
            ? null : Anim.strip(fxAtlas, region, Decor.FLICKER_STEPS, true);
    }

    /** A strip that plays once and holds its last frame. */
    private Anim once(String region) {
        return fxAtlas.findRegion(region) == null
            ? null : Anim.strip(fxAtlas, region, CHEST_STEPS, false);
    }

    private static void log(String message) {
        if (Gdx.app != null) {
            Gdx.app.log("world", message);
        }
    }
}
