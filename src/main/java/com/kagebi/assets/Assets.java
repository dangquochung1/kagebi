package com.kagebi.assets;

/**
 * Every asset path and atlas region name in the game, in one place.
 *
 * <p>Nothing else in the codebase may write an asset path as a literal. The
 * source packs are a minefield of near-miss names - the monster sheets alone
 * shipped as {@code Slime.png}, {@code mushroom.png} and {@code SpriteSheet.png}
 * - and a mistyped path fails at runtime, three screens deep, with a stack
 * trace that points at the loader rather than the typo.
 *
 * <p>Keeping them here makes them testable: {@code AtlasContractTest} walks the
 * nested region classes by reflection and asserts each name exists in the atlas
 * it claims to come from.
 */
public final class Assets {

    // ---- atlases ---------------------------------------------------------
    public static final String ATLAS_UI = "assets/atlas/ui.atlas";
    public static final String ATLAS_ACTORS = "assets/atlas/actors.atlas";
    public static final String ATLAS_NPC = "assets/atlas/npc.atlas";
    public static final String ATLAS_FX = "assets/atlas/fx.atlas";

    // ---- skin & i18n -----------------------------------------------------
    public static final String SKIN = "assets/ui/kagebi_skin.json";
    public static final String I18N_DIR = "assets/i18n/";

    // ---- standalone textures --------------------------------------------
    /**
     * 2,192 icons in a 16-wide grid. Deliberately outside any atlas: at
     * 256x2192 it is taller than a 2048 page, and its regions are addressed by
     * index arithmetic rather than by name.
     */
    public static final String ICONS = "assets/gfx/icons/raven_icons_16.png";

    /**
     * Full-screen overlays. These need TextureWrap.Repeat to scroll, and wrap
     * is a property of the whole texture rather than of a region, so they can
     * never live in an atlas.
     */
    public static final String FOG = "assets/gfx/fx/environment/fog.png";
    public static final String RAYLIGHT = "assets/gfx/fx/environment/raylight.png";

    // ---- tilesets (referenced by .tmx, never packed) ---------------------
    public static final String TILES_DIR = "assets/gfx/tiles/";
    public static final String MAPS_DIR = "assets/maps/";

    // ---- audio -----------------------------------------------------------
    public static final String MUSIC_DIR = "assets/audio/music/";
    public static final String JINGLE_DIR = "assets/audio/jingles/";
    public static final String SFX_DIR = "assets/audio/sfx/";

    public static final String MUSIC_INTRO = MUSIC_DIR + "38_intro.ogg";
    public static final String MUSIC_VILLAGE = MUSIC_DIR + "4_village.ogg";
    public static final String MUSIC_DUNGEON = MUSIC_DIR + "21_dungeon.ogg";
    public static final String MUSIC_FIGHT = MUSIC_DIR + "17_fight.ogg";
    public static final String MUSIC_FINAL = MUSIC_DIR + "24_final_area.ogg";
    public static final String MUSIC_CREDITS = MUSIC_DIR + "15_credit_theme.ogg";
    public static final String MUSIC_END = MUSIC_DIR + "8_end_theme.ogg";
    public static final String MUSIC_SAD = MUSIC_DIR + "7_sad_theme.ogg";

    /** Region names in {@link #ATLAS_UI}. */
    public static final class Ui {
        public static final String PANEL = "ui/panel";
        public static final String PANEL_2 = "ui/panel_2";
        public static final String PANEL_INTERIOR = "ui/panel_interior";
        public static final String BG = "ui/bg";
        public static final String BG_2 = "ui/bg_2";
        public static final String FOCUS = "ui/focus";
        public static final String CELL = "ui/cell";
        public static final String WHITE = "ui/white";
        public static final String PIXEL = "ui/px";

        public static final String BUTTON_UP = "ui/button_up";
        public static final String BUTTON_OVER = "ui/button_over";
        public static final String BUTTON_DOWN = "ui/button_down";
        public static final String BUTTON_DISABLED = "ui/button_disabled";

        public static final String TAB = "ui/tab";
        public static final String TAB_OVER = "ui/tab_over";
        public static final String TAB_SELECTED = "ui/tab_selected";
        public static final String TAB_DISABLED = "ui/tab_disabled";

        public static final String CHECK_ON = "ui/check_on";
        public static final String CHECK_OFF = "ui/check_off";
        public static final String TOGGLE_ON = "ui/toggle_on";
        public static final String TOGGLE_OFF = "ui/toggle_off";
        public static final String RADIO_ON = "ui/radio_on";
        public static final String RADIO_OFF = "ui/radio_off";

        public static final String HSLIDER_KNOB = "ui/hslider_knob";
        public static final String VSLIDER_KNOB = "ui/vslider_knob";
        public static final String SLIDER_FILL = "ui/slider_fill";

        public static final String ARROW_LEFT = "ui/arrow_left";
        public static final String ARROW_RIGHT = "ui/arrow_right";
        public static final String CURSOR = "ui/cursor";

        public static final String DIALOG = "ui/dialog";
        public static final String DIALOG_FACESET = "ui/dialog_faceset";
        public static final String FACESET_FRAME = "ui/faceset_frame";

        /** The font page, bound by name when the Skin loads its BitmapFont. */
        public static final String FONT_PAGE = "pixeloid_9";

        private Ui() {}
    }

    /** Region names in {@link #ATLAS_ACTORS}. */
    public static final class Actor {
        private static final String P = "player/ninjagreen/";

        public static final String PLAYER_IDLE = P + "idle";
        public static final String PLAYER_WALK = P + "walk";
        public static final String PLAYER_ATTACK = P + "attack";
        public static final String PLAYER_ROLL = P + "roll";
        public static final String PLAYER_HIT = P + "hit";
        public static final String PLAYER_DEAD = P + "dead";
        public static final String PLAYER_PICKUP = P + "pickup";

        public static final String WEAPON_KATANA = "player/weapons/katana";
        public static final String WEAPON_AXE = "player/weapons/axe";
        public static final String WEAPON_HAMMER = "player/weapons/hammer";

        /** Trash mobs: a 64x64 sheet of 4 directions x 4 frames of 16x16. */
        public static String monster(String id) {
            return "monsters/" + id + "/spritesheet";
        }

        public static String monsterFace(String id) {
            return "monsters/" + id + "/faceset";
        }

        /** Bosses ship one strip per animation, at a size specific to each. */
        public static String boss(String id, String animation) {
            return "bosses/" + id + "/" + animation;
        }

        private Actor() {}
    }

    private Assets() {}
}
