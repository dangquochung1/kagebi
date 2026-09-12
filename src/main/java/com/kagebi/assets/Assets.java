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
    /** Generated room templates, one .tmx each, under a per-biome folder. */
    public static final String ROOMS_DIR = "assets/maps/rooms/";

    // ---- content -----------------------------------------------------------
    public static final String DATA_DIR = "assets/data/";

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

        /** Floor pickups. Single images, 7 to 12 pixels across. */
        public static final String PICKUP_GOLD = "items/treasure/goldcoin";
        public static final String PICKUP_HEART = "items/potion/heart";
        public static final String PICKUP_KEY = "items/treasure/goldkey";

        /** The font page, bound by name when the Skin loads its BitmapFont. */
        public static final String FONT_PAGE = "pixeloid_9";

        private Ui() {}
    }

    /** Region names in {@link #ATLAS_ACTORS}. */
    public static final class Actor {

        /**
         * The six playable characters.
         *
         * <p>Only NinjaGreen ships the full set of animations; the pack's other
         * sixteen ninjas are NPC-grade 16x16 walk cycles and cannot be played.
         * The other five here are recoloured from it by
         * {@code tools/make_ninjas.py}, each using the cloth ramp of the pack's
         * own variant of that name, so they are in the pack's palette rather
         * than hue-rotated out of it.
         */
        public static final String[] CHARACTERS = {
            "ninjagreen", "ninjared", "ninjablue",
            "ninjadark", "ninjafire", "ninjawater",
        };

        /**
         * The animations every character has. Climb, swim, push, jump and item
         * exist in the source pack but are not packed: a top-down dungeon
         * crawler never uses them, and six characters times twelve animations
         * does not fit on one 2048 page.
         */
        public static final String[] PLAYER_ANIMS = {
            "idle", "walk", "attack", "roll", "hit", "dead", "pickup",
        };

        /**
         * The animation names in {@link #PLAYER_ANIMS}, by role. A nested class
         * rather than String fields here, because AssetsContractTest reads every
         * String field of Actor as a complete region name.
         */
        public static final class PlayerAnim {
            public static final String IDLE = "idle";
            public static final String WALK = "walk";
            public static final String ATTACK = "attack";
            public static final String ROLL = "roll";
            public static final String HIT = "hit";
            /** 32x64: one column of two frames, not a directional sheet. */
            public static final String DEAD = "dead";
            public static final String PICKUP = "pickup";

            private PlayerAnim() {}
        }

        /** e.g. {@code player(CHARACTERS[0], "walk")} -> player/ninjagreen/walk */
        public static String player(String characterId, String animation) {
            return "player/" + characterId + "/" + animation;
        }

        public static final String DEFAULT_CHARACTER = CHARACTERS[0];

        public static final String WEAPON_KATANA = "player/weapons/katana";
        public static final String WEAPON_AXE = "player/weapons/axe";
        public static final String WEAPON_HAMMER = "player/weapons/hammer";
        public static final String WEAPON_PICKAXE = "player/weapons/pickaxe";
        public static final String WEAPON_NET = "player/weapons/net";

        public static final String SHADOW = "shadow";

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

        /**
         * The boss id inside a boss sprite path, or null if it is not one:
         * {@code bosses/giantfrog2/idle} gives {@code giantfrog2}.
         */
        public static String bossIdOf(String spriteRegion) {
            if (spriteRegion == null || !spriteRegion.startsWith("bosses/")) {
                return null;
            }
            String[] parts = spriteRegion.split("/");
            return parts.length >= 2 ? parts[1] : null;
        }

        /*
         * Boss strip names, probed in order. Measured across the 20 boss folders:
         * no animation name is shared by all of them - giantfrog ships
         * "idle40x40" where giantfrog2 ships "idle", the samurai split attack
         * into "attackleft" and "attackright", the racoons ship "sprite" - so
         * each role lists what to try and the first that exists wins. Arrays
         * rather than String constants so AssetsContractTest does not read an
         * animation name as a region name.
         */
        public static final String[] BOSS_IDLE = {"idle", "idle40x40", "sprite"};
        public static final String[] BOSS_MOVE = {"walk", "jump", "idle", "idle40x40"};
        public static final String[] BOSS_ATTACK = {"attack", "charge", "attackright", "shoot"};
        public static final String[] BOSS_HURT = {"hit"};
        /** Only the two-phase bosses have one; tengured's is eleven frames. */
        public static final String[] BOSS_TRANSFORM = {"trans"};

        /*
         * The depths pack (floors 4 and 5) ships each actor as five separate
         * single-facing strips, 32px tall, rather than one sheet:
         * skeleton1_{idle,movement,attack,take_damage,death}, measured at 6, 10,
         * 9, 5 and 17 frames. An EnemyDef names the idle strip and the rest are
         * found by swapping the suffix here.
         *
         * No String field holds the suffix: AssetsContractTest reads every
         * String field of this class, private ones included, as a region name.
         */
        public static boolean isDepthsSet(String region) {
            return region != null && region.startsWith("depths/") && region.endsWith("_idle");
        }

        public static String depthsMove(String idleRegion) {
            return depthsSibling(idleRegion, "_movement");
        }

        public static String depthsAttack(String idleRegion) {
            return depthsSibling(idleRegion, "_attack");
        }

        public static String depthsHurt(String idleRegion) {
            return depthsSibling(idleRegion, "_take_damage");
        }

        public static String depthsDeath(String idleRegion) {
            return depthsSibling(idleRegion, "_death");
        }

        private static String depthsSibling(String idleRegion, String suffix) {
            return idleRegion.substring(0, idleRegion.length() - "_idle".length()) + suffix;
        }

        private Actor() {}
    }

    /**
     * Region names in {@link #ATLAS_FX}. Not walked by {@code AssetsContractTest},
     * which predates it, so {@code entity.FxRegionsTest} checks these instead.
     */
    public static final class Fx {
        /** 64x16: four 16px frames of a radial glow, so it needs no rotation. */
        public static final String PROJECTILE_ORB = "fx/projectile/energyball";
        /** 14x5, drawn pointing right, so it is rotated to its heading. */
        public static final String PROJECTILE_KUNAI = "fx/projectile/kunai";
        /** 32x16: two frames of a spinning star. */
        public static final String PROJECTILE_SHURIKEN = "fx/projectile/shuriken";
        /** A lingering area, for casters' clouds. Same radial glow, different use. */
        public static final String HAZARD_CLOUD = "fx/projectile/energyball";

        private Fx() {}
    }

    private Assets() {}
}
