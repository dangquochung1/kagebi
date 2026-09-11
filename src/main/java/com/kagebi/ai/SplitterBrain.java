package com.kagebi.ai;

import com.kagebi.entity.Enemy;

/**
 * A chaser that dies into two smaller copies of itself, once.
 *
 * <p>Exactly one generation. enemies.json is blunt about why: "one generation
 * only, or a room of six becomes a room of ninety-six". The children carry half
 * their parent's maximum hit points each, so the pair costs the same total
 * damage as the parent did - the split is a change of shape, not a change of
 * price.
 *
 * <p>The children appear on either side of the corpse, across the line to the
 * player, so neither spawns stacked on the other and neither lands on top of
 * the player for a free contact hit.
 */
public final class SplitterBrain extends BaseBrain {

    /** Pixels from the corpse each child appears, one each side. */
    public static final float SPREAD = 7f;

    @Override
    public String id() {
        return "splitter";
    }

    @Override
    public void onDeath(Enemy self, AiContext ctx) {
        if (self.generation > 0) {
            return;
        }
        int hp = Math.max(1, self.maxHp / 2);
        float dx = ctx.playerX() - self.x;
        float dy = ctx.playerY() - self.y;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float px = len < 0.001f ? 1f : -dy / len;
        float py = len < 0.001f ? 0f : dx / len;
        ctx.spawnCopy(self, self.x + px * SPREAD, self.y + py * SPREAD, hp);
        ctx.spawnCopy(self, self.x - px * SPREAD, self.y - py * SPREAD, hp);
    }
}
