package com.kagebi.settings;

/**
 * How hard the dungeon is, as three named settings.
 *
 * <p>Two numbers, because the two halves of "too hard" are different
 * complaints. <b>Incoming damage</b> answers "I keep dying"; it changes how
 * many mistakes a floor forgives and is the one a struggling player feels
 * immediately. <b>Enemy health</b> answers "every fight drags"; it changes how
 * long a room takes and is the one that makes a run feel long rather than
 * dangerous. Scaling only damage would make HARD a game where the same fights
 * kill you faster, which is tension without any extra substance.
 *
 * <p><b>Bosses scale more gently than ordinary enemies.</b> The final boss is
 * 1,800 hit points, which is eighty-nine measured seconds of unbroken swinging
 * with the starting katana. At the ordinary 1.2 that is nearly two minutes of
 * the same four attacks, which is not harder, only longer - so bosses take 1.1.
 *
 * <p>NORMAL is exactly 1.0 in every direction on purpose: it is the content as
 * authored and as {@code BalanceTest} models it, so the balance work already
 * done stays the specification rather than becoming one setting among three.
 */
public enum Difficulty {

    /** "Hard". Mistakes cost half again as much and everything has more health. */
    HARD("settings.difficulty.hard", 1.5f, 1.2f, 1.1f),

    /** "Normal". The content as authored; every multiplier is one. */
    NORMAL("settings.difficulty.normal", 1.0f, 1.0f, 1.0f),

    /**
     * "You're weak". Named the way the player named it, which is a joke at
     * their own expense rather than at the game's - so it is the ninja who is
     * weak, not the setting that is for weaklings.
     */
    WEAK("settings.difficulty.weak", 0.6f, 0.8f, 0.9f);

    /** Lookup key for the label shown in the settings screen. */
    public final String i18nKey;
    /** What the player's incoming damage is multiplied by. */
    public final float damageTaken;
    /** What an ordinary enemy's maximum health is multiplied by. */
    public final float enemyHp;
    /** What a boss's maximum health is multiplied by. */
    public final float bossHp;

    Difficulty(String i18nKey, float damageTaken, float enemyHp, float bossHp) {
        this.i18nKey = i18nKey;
        this.damageTaken = damageTaken;
        this.enemyHp = enemyHp;
        this.bossHp = bossHp;
    }

    public static final Difficulty DEFAULT = NORMAL;

    /** Reads a saved name, falling back rather than failing on a bad one. */
    public static Difficulty fromName(String name) {
        for (Difficulty d : values()) {
            if (d.name().equals(name)) {
                return d;
            }
        }
        return DEFAULT;
    }

    /** Health for one enemy at this setting, never below one. */
    public int scaleHp(int maxHp, boolean boss) {
        float scaled = maxHp * (boss ? bossHp : enemyHp);
        return Math.max(1, Math.round(scaled));
    }
}
