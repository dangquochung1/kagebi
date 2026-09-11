package com.kagebi.entity;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.kagebi.Cfg;
import com.kagebi.combat.Faction;
import com.kagebi.run.RunState;

/**
 * Something on the floor that walks itself into the player's pocket.
 *
 * <p>The magnet is the feature. Loot that has to be stood on exactly turns the
 * satisfying part of a kill into a chore of hoovering up coins, and at 320x180
 * a 7px coin is genuinely hard to stand on. Anything inside {@link #MAGNET} is
 * pulled in at a speed that ramps with proximity, so collecting is automatic
 * without being instant - the flight is the reward animation.
 *
 * <p>There is a short delay before the magnet engages so that a drop visibly
 * lands where the enemy died. Pulling from step one hides the causality.
 */
public final class Pickup extends Entity {

    public enum Kind { GOLD, HEART, KEY, ITEM }

    /** Virtual pixels. Roughly two tiles: generous, but not across the room. */
    public static final float MAGNET = 34f;
    public static final float MAGNET_SPEED = 130f;
    /** Steps before the magnet turns on, so the drop reads as coming from the kill. */
    public static final int SETTLE_STEPS = 18;
    /** Close enough to count as collected, in pixels. */
    public static final float COLLECT = 7f;

    public final Kind kind;
    public final int amount;
    /** Item id for {@link Kind#ITEM}, null otherwise. */
    public final String itemId;

    private final TextureRegion region;
    private int settle = SETTLE_STEPS;

    public Pickup(Kind kind, int amount, String itemId, float x, float y, TextureRegion region) {
        this.kind = kind;
        this.amount = amount;
        this.itemId = itemId;
        this.x = x;
        this.y = y;
        this.region = region;
        this.bodyW = 8f;
        this.bodyH = 8f;
        this.hp = 1;
        this.maxHp = 1;
    }

    @Override
    public Faction faction() {
        return Faction.HAZARD;
    }

    @Override
    public void step(EntityWorld world) {
        animSteps++;
        Player player = world.player();
        if (player == null || !player.alive()) {
            return;
        }
        if (settle > 0) {
            settle--;
            return;
        }
        float dx = player.x - x;
        float dy = player.y - y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist <= COLLECT) {
            collect(world.run(), player);
            removed = true;
            return;
        }
        if (dist < MAGNET && dist > 0.001f) {
            // Faster the closer it is: the pull accelerates into the pocket
            // rather than drifting in at a constant crawl.
            float pull = MAGNET_SPEED * (1f - dist / MAGNET) + 25f;
            x += dx / dist * pull * Cfg.STEP;
            y += dy / dist * pull * Cfg.STEP;
        }
    }

    private void collect(RunState run, Player player) {
        switch (kind) {
            case GOLD:
                run.gold += amount;
                break;
            case HEART:
                player.heal(amount);
                break;
            case KEY:
                run.keys += amount;
                break;
            default:
                if (itemId != null) {
                    run.addItem(itemId, amount);
                }
                break;
        }
    }

    @Override
    public void takeHit(int damage, float fromX, float fromY, float knockback) {
    }

    @Override
    protected int spriteFootOffset() {
        // A two-pixel bob over 40 steps. Static loot on a static floor is
        // genuinely easy to miss at this resolution.
        return animSteps % 40 < 20 ? 1 : 2;
    }

    @Override
    public TextureRegion frame() {
        return region;
    }
}
