package com.kagebi;

/**
 * The four facings, in the order the sprite sheets use.
 *
 * <p>Measured, not assumed: every actor sheet in the pack lays direction across
 * the <em>columns</em> and animation frames down the <em>rows</em>. All 66
 * monster sheets are 64x64, that is 4 columns x 4 rows of 16x16, and the player
 * sheets are 4 columns x N rows of 32x32. Column 0 faces the camera, column 1
 * shows the back of the head, columns 2 and 3 look left and right.
 *
 * <p>The ordinal is therefore the sheet column, and nothing may reorder these.
 */
public enum Dir {

    DOWN(0, -1),
    UP(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0);

    public final int dx;
    public final int dy;

    Dir(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public Dir opposite() {
        switch (this) {
            case DOWN: return UP;
            case UP: return DOWN;
            case LEFT: return RIGHT;
            default: return LEFT;
        }
    }

    /** Bit for this direction, for the door masks in {@code gen}. */
    public int bit() {
        return 1 << ordinal();
    }

    /**
     * The facing for a movement vector, biased to the horizontal on a tie.
     * A diagonal has to resolve to one sheet column, and picking the vertical
     * one makes a character walking up-right look like it is walking away.
     */
    public static Dir of(float dx, float dy) {
        if (Math.abs(dx) >= Math.abs(dy)) {
            if (dx == 0f && dy == 0f) {
                return DOWN;
            }
            return dx < 0 ? LEFT : RIGHT;
        }
        return dy < 0 ? DOWN : UP;
    }

    public static final Dir[] ALL = values();
}
