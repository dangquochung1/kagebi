package com.kagebi.ai;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The registry {@code EnemyDef.brain} resolves against.
 *
 * <p>Brains are shared: one object per id for the whole game, because they hold
 * no per-enemy state. {@link #create} throws on an id nobody implements, which
 * is what lets the content loader validate enemies.json at startup instead of
 * shipping a slime that stands still.
 *
 * <p>{@link #knows} exists so that a caller which must not throw - the room
 * spawner, which has to survive content still being authored - can ask first
 * and fall back rather than catching.
 */
public final class AiBrains {

    /** Used when a def names a brain nobody wrote. Something that fights beats nothing. */
    public static final String FALLBACK = "chaser";

    private static final Map<String, AiBrain> BRAINS = new LinkedHashMap<>();

    static {
        register(new ChaserBrain());
        register(new WandererBrain());
        register(new ShooterBrain());
        register(new ChargerBrain());
        register(new StationaryBrain());
        register(new HopperBrain());
        register(new FlyerBrain());
        register(new OrbiterBrain());
        register(new AmbusherBrain());
        register(new SplitterBrain());
        register(new CasterBrain());
        register(BossBrain.generic());
        register(BossBrain.frog());
        register(BossBrain.tengu());
    }

    private static void register(AiBrain brain) {
        BRAINS.put(brain.id(), brain);
    }

    public static boolean knows(String id) {
        return id != null && BRAINS.containsKey(id);
    }

    public static AiBrain create(String id) {
        AiBrain brain = id == null ? null : BRAINS.get(id);
        if (brain == null) {
            throw new IllegalArgumentException("no ai brain '" + id + "'; known: " + ids());
        }
        return brain;
    }

    /** Resolves, or falls back to {@link #FALLBACK} rather than throwing. */
    public static AiBrain createOrFallback(String id) {
        return knows(id) ? BRAINS.get(id) : BRAINS.get(FALLBACK);
    }

    /** Every registered id, in registration order, for the loader's error message. */
    public static Set<String> ids() {
        return Collections.unmodifiableSet(BRAINS.keySet());
    }

    private AiBrains() {}
}
