package com.kagebi.entity;

import java.util.Random;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.kagebi.Cfg;
import com.kagebi.Dir;
import com.kagebi.ai.AiBrain;
import com.kagebi.ai.AiBrains;
import com.kagebi.ai.AiContext;
import com.kagebi.assets.Assets;
import com.kagebi.combat.AttackState;
import com.kagebi.combat.Faction;
import com.kagebi.combat.HitResolver;
import com.kagebi.combat.Hitbox;
import com.kagebi.data.ContentRegistry;
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
    private final Array<Entity> drawList = new Array<>();

    private final Intent intent = new Intent();
    private InputService boundInput;
    private ActionSource boundSource;

    private CollisionGrid collision;
    private Room room;
    private Random rng = new Random(0L);

    private int hitstop;
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
    private TextureRegion kunaiRegion;
    private TextureRegion goldRegion;
    private TextureRegion heartRegion;
    private TextureRegion keyRegion;

    /** A chest, the stairs, or a shopkeeper: something the player walks up to. */
    private static final class Marker {
        final SpawnPoint.Kind kind;
        final float x;
        final float y;
        boolean used;

        Marker(SpawnPoint.Kind kind, float x, float y) {
            this.kind = kind;
            this.x = x;
            this.y = y;
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
            ActorSprites.player(actors, run.characterId), new Random(run.seed ^ 0x5DEECE66DL));
        if (actors != null) {
            loadOwnAtlases();
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
        hitstop = 0;
        shake = 0f;
        descendRequested = false;
        shopRequested = false;
        // Seeded by run and room, so the same room in the same run plays out
        // the same way - which is what makes a reported bug reproducible.
        rng = new Random(run.seed * 31L + room.gx * 73856093L + room.gy * 19349663L);
        player.setWeapon(resolveWeapon(run.weaponId));

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
        shake = shake * SHAKE_DECAY < 0.05f ? 0f : shake * SHAKE_DECAY;

        if (hitstop > 0) {
            hitstop--;
            return;
        }
        int hpBefore = run.hp;

        player.step(this);
        if (player.hitsLandedThisStep > 0) {
            hitstop = Math.max(hitstop, HITSTOP_LAND);
            shake = Math.max(shake, SHAKE_LAND);
        }

        for (int i = 0; i < enemies.size; i++) {
            enemies.get(i).step(this);
        }
        applyContactDamage();
        for (int i = 0; i < projectiles.size; i++) {
            projectiles.get(i).step(this);
        }
        for (int i = 0; i < pickups.size; i++) {
            pickups.get(i).step(this);
        }

        countDeaths();
        if (spawnQueue.size > 0) {
            enemies.addAll(spawnQueue);
            spawnQueue.clear();
        }
        sweep();

        if (run.hp < hpBefore) {
            hitstop = Math.max(hitstop, HITSTOP_HURT);
            shake = Math.max(shake, SHAKE_HURT);
        }
        if (room != null && !room.cleared && hostilesAlive() == 0) {
            room.cleared = true;
        }
    }

    @Override
    public void renderActors(SpriteBatch batch) {
        // Floor-level hazards first, so actors stand in a cloud rather than
        // under it.
        for (Projectile p : projectiles) {
            if (p.pierces()) {
                p.draw(batch);
            }
        }

        drawList.clear();
        drawList.add(player);
        for (Enemy e : enemies) {
            drawList.add(e);
        }
        for (Pickup p : pickups) {
            drawList.add(p);
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
        if (x > RoomTemplate.PIXEL_WIDTH - DOOR_MARGIN && room.hasDoor(Dir.RIGHT)) {
            return Dir.RIGHT;
        }
        if (y < DOOR_MARGIN && room.hasDoor(Dir.DOWN)) {
            return Dir.DOWN;
        }
        if (y > RoomTemplate.PIXEL_HEIGHT - DOOR_MARGIN && room.hasDoor(Dir.UP)) {
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
                m.used = true;
                // Gold only for now: rolling the room's loot table is the loot
                // package's job, and it hooks in here. See notes/b.md.
                int coins = 3 + rng.nextInt(4);
                for (int i = 0; i < coins; i++) {
                    dropGold(m.x, m.y, 1 + rng.nextInt(3));
                }
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
            damage, SHOT_KNOCKBACK, lifeSteps, orbAnim, null));
    }

    @Override
    public void placeHazard(Enemy from, float x, float y, int damage, int armSteps,
                            int lifeSteps) {
        projectiles.add(Projectile.hazard(Faction.ENEMY, x, y, damage, armSteps,
            lifeSteps, cloudAnim));
    }

    @Override
    public void spawnCopy(Enemy parent, float x, float y, int hp) {
        Enemy child = new Enemy(parent.def, parent.brain(), parent.sprites);
        child.x = x;
        child.y = y;
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

    public boolean shopRequested() {
        return shopRequested;
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

    /** The player threw their weapon. */
    void throwFrom(Player p, int damage) {
        WeaponDef w = p.weapon();
        int life = Math.max(1, Math.round(w.reach / THROW_SPEED / Cfg.STEP));
        projectiles.add(new Projectile(Faction.PLAYER, p.x, p.y, p.facing.dx, p.facing.dy,
            THROW_SPEED, damage, w.knockback, life, null, kunaiRegion));
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

    private void placePlayer(Room room, Dir enteredFrom) {
        float px = RoomTemplate.PIXEL_WIDTH / 2f;
        float py = RoomTemplate.PIXEL_HEIGHT / 2f;
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
            px += enteredFrom.dx * (RoomTemplate.PIXEL_WIDTH / 2f - 32f);
            py += enteredFrom.dy * (RoomTemplate.PIXEL_HEIGHT / 2f - 32f);
        }
        if (enteredFrom != null) {
            face = enteredFrom.opposite();
        }
        player.placeAt(px, py, face);
    }

    private void spawn(Room room) {
        boolean spawnEnemies = !room.cleared;
        boolean bossSpawned = false;
        for (SpawnPoint s : room.template.spawns) {
            switch (s.kind) {
                case ENEMY:
                    if (spawnEnemies) {
                        EnemyDef def = s.tag != null && content.hasEnemy(s.tag)
                            ? content.enemy(s.tag) : rollFromPool();
                        if (def != null && !(def.boss && bossSpawned)) {
                            spawnEnemy(def, s.x, s.y);
                            bossSpawned |= def.boss;
                        } else if (def == null) {
                            log("no enemy for spawn " + s + " on floor " + run.floor);
                        }
                    }
                    break;
                case CHEST:
                case EXIT:
                case SHOPKEEPER:
                    markers.add(new Marker(s.kind, s.x, s.y));
                    break;
                default:
                    break;
            }
        }
        if (spawnEnemies && room.kind == RoomKind.BOSS && !bossSpawned) {
            FloorDef floor = floorDef();
            if (floor != null && floor.hasBoss() && content.hasEnemy(floor.boss)) {
                spawnEnemy(content.enemy(floor.boss),
                    RoomTemplate.PIXEL_WIDTH / 2f, RoomTemplate.PIXEL_HEIGHT * 0.62f);
            }
        }
    }

    /** Places an enemy. Public so a test, or a debug console, can populate a room. */
    public Enemy spawnEnemy(EnemyDef def, float x, float y) {
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
        e.x = x;
        e.y = y;
        brain.onSpawn(e);
        enemies.add(e);
        return e;
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
            shake = Math.max(shake, SHAKE_KILL);
            e.brain().onDeath(e, this);
            // A splitter's children drop nothing, or splitting would double the
            // gold a mushroom is worth, and the room would pay for its own trap.
            if (e.generation == 0 && e.def.goldMax > 0) {
                int gold = e.def.goldMin + (e.def.goldMax > e.def.goldMin
                    ? rng.nextInt(e.def.goldMax - e.def.goldMin + 1) : 0);
                if (gold > 0) {
                    dropGold(e.x, e.y, gold);
                }
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
            case HEART: region = heartRegion; break;
            case KEY: region = keyRegion; break;
            default: region = null; break;
        }
        Pickup p = new Pickup(kind, amount, itemId, x, y, region);
        pickups.add(p);
        return p;
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
        kunaiRegion = null;
        if (fxAtlas != null) {
            if (fxAtlas.findRegion(Assets.Fx.PROJECTILE_ORB) != null) {
                orbAnim = Anim.strip(fxAtlas, Assets.Fx.PROJECTILE_ORB, 4, true);
            }
            if (fxAtlas.findRegion(Assets.Fx.HAZARD_CLOUD) != null) {
                cloudAnim = Anim.strip(fxAtlas, Assets.Fx.HAZARD_CLOUD, 8, true);
            }
            kunaiRegion = fxAtlas.findRegion(Assets.Fx.PROJECTILE_KUNAI);
        }
        goldRegion = uiAtlas == null ? null : uiAtlas.findRegion(Assets.Ui.COIN);
        heartRegion = uiAtlas == null ? null : uiAtlas.findRegion(Assets.Ui.PICKUP_HEART);
        keyRegion = uiAtlas == null ? null : uiAtlas.findRegion(Assets.Ui.KEY);
    }

    private static void log(String message) {
        if (Gdx.app != null) {
            Gdx.app.log("world", message);
        }
    }
}
