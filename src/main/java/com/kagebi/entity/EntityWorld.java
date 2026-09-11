package com.kagebi.entity;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.kagebi.Cfg;
import com.kagebi.Dir;
import com.kagebi.data.ContentRegistry;
import com.kagebi.gen.CollisionGrid;
import com.kagebi.gen.Room;
import com.kagebi.gen.RoomTemplate;
import com.kagebi.gen.SpawnPoint;
import com.kagebi.gfx.Anim;
import com.kagebi.input.InputService;
import com.kagebi.run.RunState;
import com.kagebi.settings.Settings;

/**
 * PLACEHOLDER. Walks a player around a room and nothing else.
 *
 * <p>It exists so the dungeon screen has something real to drive while the
 * simulation is being written beside it: the player moves, collides, faces the
 * right way and animates, which is enough to check a camera, a HUD and room
 * transitions. There are no enemies, no combat and no loot.
 *
 * <p>This whole file is expected to be replaced, not extended. What must
 * survive is the constructor signature and {@link World} - those are what the
 * screen was written against.
 */
public final class EntityWorld implements World {

    private static final float SPEED = 60f;
    /** The ninja occupies roughly the middle 12x12 of its 32x32 cell. */
    private static final float BODY = 12f;

    private final RunState run;
    private final Settings settings;

    private final Anim idle;
    private final Anim walk;

    private float x;
    private float y;
    private Dir facing = Dir.DOWN;
    private int animSteps;
    private boolean moving;

    private CollisionGrid collision;
    private Room room;

    public EntityWorld(TextureAtlas actors, ContentRegistry content,
                       RunState run, Settings settings) {
        this.run = run;
        this.settings = settings;
        String base = "player/" + run.characterId + "/";
        this.idle = Anim.directional(actors, base + "idle", 32, 12, true);
        this.walk = Anim.directional(actors, base + "walk", 32, 6, true);
    }

    @Override
    public void enterRoom(Room room, CollisionGrid collision, Dir enteredFrom) {
        this.room = room;
        this.collision = collision;
        x = RoomTemplate.PIXEL_WIDTH / 2f;
        y = RoomTemplate.PIXEL_HEIGHT / 2f;
        if (enteredFrom != null) {
            // Step in from the door rather than onto it, or the screen would
            // immediately read the player as standing in a doorway again.
            Dir in = enteredFrom.opposite();
            x -= in.dx * (RoomTemplate.PIXEL_WIDTH / 2f - 32);
            y -= in.dy * (RoomTemplate.PIXEL_HEIGHT / 2f - 32);
        } else {
            for (SpawnPoint s : room.template.spawns) {
                if (s.kind == SpawnPoint.Kind.ENTRY) {
                    x = s.x;
                    y = s.y;
                    break;
                }
            }
        }
        room.visited = true;
    }

    @Override
    public void step(InputService input) {
        int dx = input.axisX();
        int dy = input.axisY();
        moving = dx != 0 || dy != 0;
        if (moving) {
            facing = Dir.of(dx, dy);
            float len = (dx != 0 && dy != 0) ? 0.70710678f : 1f;
            move(dx * SPEED * Cfg.STEP * len, dy * SPEED * Cfg.STEP * len);
        }
        animSteps++;
        run.elapsedSeconds += Cfg.STEP;
    }

    /** Axis at a time, so sliding along a wall works instead of sticking. */
    private void move(float mx, float my) {
        if (collision == null) {
            x += mx;
            y += my;
            return;
        }
        float half = BODY / 2f;
        if (!collision.overlaps(x + mx - half, y - half, BODY, BODY)) {
            x += mx;
        }
        if (!collision.overlaps(x - half, y + my - half, BODY, BODY)) {
            y += my;
        }
    }

    @Override
    public void renderActors(SpriteBatch batch) {
        Anim anim = moving ? walk : idle;
        TextureRegion frame = anim.frame(facing, animSteps);
        batch.draw(frame, Math.round(x - 16f), Math.round(y - 16f));
    }

    @Override
    public float playerX() {
        return x;
    }

    @Override
    public float playerY() {
        return y;
    }

    @Override
    public Dir playerFacing() {
        return facing;
    }

    @Override
    public boolean roomCleared() {
        return true;        // nothing spawns yet
    }

    @Override
    public boolean playerDead() {
        return run.dead();
    }

    @Override
    public Dir doorReached() {
        if (room == null) {
            return null;
        }
        float margin = 8f;
        if (x < margin && room.hasDoor(Dir.LEFT)) {
            return Dir.LEFT;
        }
        if (x > RoomTemplate.PIXEL_WIDTH - margin && room.hasDoor(Dir.RIGHT)) {
            return Dir.RIGHT;
        }
        if (y < margin && room.hasDoor(Dir.DOWN)) {
            return Dir.DOWN;
        }
        if (y > RoomTemplate.PIXEL_HEIGHT - margin && room.hasDoor(Dir.UP)) {
            return Dir.UP;
        }
        return null;
    }

    @Override
    public String promptKey() {
        return null;
    }

    @Override
    public void interact() {
    }

    @Override
    public float shake() {
        return settings.screenShake() ? 0f : 0f;
    }

    @Override
    public void dispose() {
    }
}
