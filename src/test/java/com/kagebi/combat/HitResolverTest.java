package com.kagebi.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.kagebi.Dir;

/**
 * The resolver against ten-line fakes. No entity, no atlas, no GL - which is
 * the point of {@link Combatant} being as narrow as it is.
 */
class HitResolverTest {

    /** A target with real i-frames, so the tests exercise the real rule. */
    static final class Dummy implements Combatant {
        float x;
        float y;
        int hp = 100;
        int armour;
        int invulnSteps = 20;
        Faction faction = Faction.ENEMY;
        final IFrames iframes = new IFrames();
        final Knockback shove = new Knockback();
        int hitsTaken;

        Dummy(float x, float y) {
            this.x = x;
            this.y = y;
        }

        @Override public float centreX() { return x; }
        @Override public float centreY() { return y; }
        @Override public float bodyWidth() { return 12f; }
        @Override public float bodyHeight() { return 12f; }
        @Override public Faction faction() { return faction; }
        @Override public boolean alive() { return hp > 0; }
        @Override public boolean invulnerable() { return iframes.invulnerable(); }
        @Override public int armour() { return armour; }

        @Override
        public void takeHit(int damage, float fromX, float fromY, float knockback) {
            hp -= damage;
            hitsTaken++;
            iframes.grant(invulnSteps);
            shove.apply(fromX, fromY, x, y, knockback, 0f, Dir.DOWN);
        }
    }

    private static Hitbox swingRight(int damage) {
        return Hitbox.swing(0f, 0f, Dir.RIGHT, 22f, 9f, damage, 40f, Faction.PLAYER);
    }

    @Test
    void aHitLandsWithArmourApplied() {
        Dummy d = new Dummy(12f, 0f);
        d.armour = 2;
        assertTrue(HitResolver.hit(swingRight(7), d, null));
        assertEquals(95, d.hp, "7 - 2 armour");
    }

    @Test
    void iFramesBlockASecondHitInsideTheWindow() {
        Dummy d = new Dummy(12f, 0f);
        d.invulnSteps = 10;
        assertTrue(HitResolver.hit(swingRight(5), d, null));
        for (int step = 1; step < 10; step++) {
            d.iframes.step();
            assertFalse(HitResolver.hit(swingRight(5), d, null),
                "inside the 10-step window at step " + step);
        }
        d.iframes.step();
        assertTrue(HitResolver.hit(swingRight(5), d, null), "window closed on step 10");
        assertEquals(2, d.hitsTaken);
    }

    @Test
    void oneSwingHitsEachTargetOnceAcrossItsWholeActiveWindow() {
        // Zero i-frames, so only the swing's own memory can stop a repeat.
        Dummy a = new Dummy(10f, 0f);
        Dummy b = new Dummy(16f, 4f);
        a.invulnSteps = 0;
        b.invulnSteps = 0;
        List<Dummy> targets = Arrays.asList(a, b);
        AttackState swing = new AttackState();
        swing.begin(0, 4, 0, 0);
        int total = 0;
        while (swing.active()) {
            total += HitResolver.resolve(swingRight(7), targets, swing);
            swing.step();
        }
        assertEquals(2, total, "four active steps, two targets, two hits");
        assertEquals(1, a.hitsTaken);
        assertEquals(1, b.hitsTaken);
    }

    @Test
    void contactDamageWithNoSwingCanHitAgainOnceIFramesLapse() {
        Dummy d = new Dummy(0f, 0f);
        d.faction = Faction.PLAYER;
        d.invulnSteps = 3;
        Hitbox body = Hitbox.body(0f, 0f, 12f, 12f, 4, 90f, Faction.ENEMY);
        int hits = 0;
        for (int step = 0; step < 8; step++) {
            if (HitResolver.hit(body, d, null)) {
                hits++;
            }
            d.iframes.step();
        }
        // Hits on steps 0, 3 and 6: a three-step window between each.
        assertEquals(3, hits);
    }

    @Test
    void aSwingDodgedWithIFramesStaysDodgedWhenTheyRunOut() {
        // The roll's invulnerability ends while the enemy's active window is
        // still open. The swing was claimed while the target was untouchable,
        // so the tail of it must not clip them on the way out.
        Dummy rolling = new Dummy(12f, 0f);
        rolling.faction = Faction.PLAYER;
        rolling.iframes.grant(2);
        Hitbox enemySwing = Hitbox.swing(0f, 0f, Dir.RIGHT, 22f, 9f, 10, 70f, Faction.ENEMY);
        AttackState swing = new AttackState();
        swing.begin(0, 6, 0, 0);
        while (swing.active()) {
            HitResolver.hit(enemySwing, rolling, swing);
            rolling.iframes.step();
            swing.step();
        }
        assertEquals(100, rolling.hp, "the dodge was earned on step 0");
    }

    @Test
    void noFriendlyFire() {
        Dummy ally = new Dummy(12f, 0f);
        ally.faction = Faction.PLAYER;
        assertFalse(HitResolver.hit(swingRight(7), ally, null));
        assertEquals(100, ally.hp);
    }

    @Test
    void theDeadAndTheDistantAreIgnored() {
        Dummy dead = new Dummy(12f, 0f);
        dead.hp = 0;
        Dummy far = new Dummy(60f, 0f);
        assertEquals(0, HitResolver.resolve(swingRight(7), Arrays.asList(dead, far), null));
        assertEquals(0, far.hitsTaken);
    }

    @Test
    void knockbackComesFromTheAttackerNotTheBlade() {
        // Target sits above the blade's midline. Shoving away from the box
        // centre would push it up; shoving from the attacker pushes it right
        // and slightly up, which is what a swing from the left looks like.
        Dummy d = new Dummy(18f, 6f);
        HitResolver.hit(swingRight(7), d, null);
        assertTrue(d.shove.vx() > d.shove.vy() * 2f,
            "pushed mostly away from the attacker at the origin");
    }
}
