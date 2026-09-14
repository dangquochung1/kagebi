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

    /**
     * The one hand-drawn map in the game. Everything else is a generated room
     * template under {@link #ROOMS_DIR}.
     */
    public static final String MAP_VILLAGE = MAPS_DIR + "village.tmx";

    /**
     * Stings, not music: short enough to decode into memory, which is what lets
     * them start on the frame the run ends instead of a beat later. Both are
     * exactly 2.0 s, measured from the WAV headers. {@code success1} is 0.45 s
     * and over before the victory panel has finished appearing.
     */
    public static final String JINGLE_SUCCESS = JINGLE_DIR + "success3.wav";
    public static final String JINGLE_GAMEOVER = JINGLE_DIR + "gameover.wav";

    public static final String SFX_ACCEPT = SFX_DIR + "menu/accept.wav";
    public static final String SFX_CANCEL = SFX_DIR + "menu/cancel.wav";
    public static final String SFX_MOVE = SFX_DIR + "menu/move1.wav";
    public static final String SFX_DOOR = SFX_DIR + "menu/menu5.wav";

    /**
     * A music path from a content field. {@code floors.json} may name a track
     * as a bare file ({@code 21_dungeon}) or as a full path, and resolving that
     * here keeps the one place that knows where music lives the one place that
     * builds its paths.
     */
    public static String music(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        if (name.startsWith(MUSIC_DIR)) {
            return name;
        }
        return MUSIC_DIR + (name.endsWith(".ogg") ? name : name + ".ogg");
    }

    /**
     * Combat sounds. Chosen by ear from the pack's eleven groups, not by name:
     * the files are called hit1 through hit9 and say nothing about what they
     * sound like.
     *
     * <p>Hit-stop already tells the player a blow landed; this is the other
     * half of it, and the two together are most of what makes a hit feel like
     * a hit rather than a number changing.
     */
    public static final class Sfx {
        private static final String S = SFX_DIR;

        public static final String SWING = S + "whoosh_and_slash/slash.wav";
        /** For a hammer or an axe: slower, heavier, and it should sound it. */
        public static final String SWING_HEAVY = S + "whoosh_and_slash/sword2.wav";
        public static final String THROW = S + "whoosh_and_slash/whoosh.wav";

        public static final String HIT = S + "hit_and_impact/hit1.wav";
        /** The player being hit, deliberately a duller and lower sound. */
        public static final String HURT = S + "hit_and_impact/hit5.wav";
        public static final String DEATH = S + "hit_and_impact/hit8.wav";

        public static final String COIN = S + "bonus/coin.wav";
        public static final String PICKUP = S + "bonus/bonus.wav";
        public static final String HEAL = S + "bonus/bonus2.wav";
        public static final String KEY_GET = S + "bonus/bonus3.wav";
        /**
         * A chest opening something permanently.
         *
         * <p>The one pickup sound that is not in the bonus folder, because it
         * is not a pickup: what it announces outlives the run. The secret
         * jingle is short enough to fire in the world rather than over a
         * screen, and it is the only cue in the pack that already means "you
         * have found something that was hidden".
         */
        public static final String UNLOCK = JINGLE_DIR + "secret1.wav";
        /** A boss noticing the player, or changing phase. */
        public static final String ALERT = S + "alert/alert.wav";

        private Sfx() {}
    }

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
        /**
         * The same box without the baked-in name tab. That tab is nine pixels
         * tall, which fits a Latin capital and not a Vietnamese one - see
         * {@code ui.DialogBox} for what that costs.
         */
        public static final String DIALOG_SIMPLE = "ui/dialog/dialogueboxsimple";
        public static final String FACESET_FRAME = "ui/faceset_frame";

        /**
         * Five 16x16 frames in one 80x16 strip, empty through to full, so the
         * frame index <em>is</em> the quarter-hearts remaining. That is why the
         * player's hit points are a multiple of four.
         */
        public static final String HEART = "ui/receptacle/heart";
        public static final int HEART_STEPS = 4;

        public static final String COIN = "items/treasure/goldcoin";
        public static final String KEY = "items/treasure/goldkey";

        /**
         * The gem, on the floor and on the HUD.
         *
         * <p>The pack ships four colourways - green, purple, red and yellow -
         * and no blue one, so this is the whole choice. Purple reads as the
         * precious one against a floor that is brown on three biomes and
         * grey-violet on the other two, and does not collide with the red a
         * heart is or the yellow a coin is.
         */
        public static final String GEM = "items/resource/gempurple";

        /**
         * The heart a dead enemy drops. A potion rather than the HUD's
         * receptacle art above: lying on the floor it has to read as a thing to
         * walk over, not as a gauge.
         */
        public static final String PICKUP_HEART = "items/potion/heart";

        /**
         * What covers a doorway that leads nowhere. A boulder rather than a wall
         * tile on purpose: every template carries all four doorways and is drawn
         * in its own biome's tileset, so a patch cut from one tileset would be
         * visibly wrong in the next. A boulder belongs to no tileset and reads
         * as "not this way" at a glance.
         */
        public static final String SEAL = "items/resource/rock";

        /** Inventory and relic icons, drawn at 16x16 in a {@link #CELL} grid. */
        public static final String ICON_KATANA = "items/weapons/katana/sprite";
        public static final String ICON_AXE = "items/weapons/axe/sprite";
        public static final String ICON_HAMMER = "items/weapons/hammer/sprite";
        public static final String ICON_PICKAXE = "items/weapons/pickaxe/sprite";
        public static final String ICON_SWORD = "items/weapons/sword/sprite";
        public static final String ICON_KUNAI = "items/projectile/kunai";
        public static final String ICON_SHURIKEN = "items/projectile/shuriken";
        public static final String ICON_RELIC = "items/scroll/scrollrock";

        /**
         * The icon for a weapon id, falling back to a plain sword. Weapon defs
         * carry their own icon once content exists; this is what the character
         * select shows before it does.
         */
        public static String weaponIcon(String weaponId) {
            switch (weaponId) {
                case "katana": return ICON_KATANA;
                case "axe": return ICON_AXE;
                case "hammer": return ICON_HAMMER;
                case "pickaxe": return ICON_PICKAXE;
                case "kunai": return ICON_KUNAI;
                case "shuriken": return ICON_SHURIKEN;
                default: return ICON_SWORD;
            }
        }

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
     * Region names in {@link #ATLAS_NPC}.
     *
     * <p>Ninety villagers ship in the pack and the hub uses three of them, so
     * these are named rather than generated: which villager stands outside
     * which house is art direction, and belongs somewhere a reviewer can read
     * it. The sheets are 4 columns x 7 rows of 16x16, the same shape as the
     * monsters, so {@code Anim.directional} slices them unchanged.
     */
    public static final class Npc {

        /** The village elder, who keeps the flame. */
        public static final String ELDER = "oldman";
        /** The one who trained the player, outside the middle house. */
        public static final String MASTER = "master";
        /** The herbalist, who sells nothing yet. */
        public static final String HERBALIST = "woman";

        public static final String[] VILLAGERS = {ELDER, MASTER, HERBALIST};

        /**
         * Whoever it is that keeps a stall five floors underground. A top hat
         * and a coat, which at 16 pixels is the clearest "this one sells
         * things" in the pack - and the only bright figure in a dark room, so
         * the player looks at it without being told to.
         */
        public static final String MERCHANT = "noble";

        public static String idle(String npcId) {
            return "npc/" + npcId + "/anim/idle";
        }

        public static String face(String npcId) {
            return "npc/" + npcId + "/faceset";
        }

        private Npc() {}
    }

    /**
     * Region names in {@link #ATLAS_FX}. Not walked by {@code AssetsContractTest},
     * which only reaches Ui and Actor, so {@code entity.FxRegionsTest} checks
     * these instead.
     */
    public static final class Fx {

        /**
         * The way down: a four-frame strip of 32x32 magic circle. The packs
         * ship no stairs, and a glowing circle on the floor of the last room
         * reads as "step here" in any tileset.
         */
        public static final String EXIT = "fx/magic/circle/spritesheetorange";

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

    /**
     * Scenery bracketed to a room's walls, from {@link #ATLAS_FX}.
     *
     * <p>All three are four-frame loops of 16x16, which is what makes them
     * worth spawning at all: a still torch is a smudge on a wall, and a
     * flickering one is the only thing in a stone room that moves while the
     * player stands still.
     *
     * <p>These come from the Pixel Dungeon pack, which the art plan otherwise
     * confines to floors 4 and 5. The exception is deliberate: no Ninja
     * Adventure tileset draws a torch, lamp or brazier anywhere, and the one
     * fire sprite in that pack is a twelve-frame particle that shrinks to
     * nothing and cannot loop. A 16px torch is mostly flame, and flame reads
     * the same in any palette - checked against all four wall colours before
     * this was written.
     */
    public static final class Prop {

        /** Front-facing, for the top wall: the bracket reads straight on. */
        public static final String TORCH = "props/depths/torch/torch";
        /**
         * Seen from the side, bracket against the left edge of its cell, so it
         * suits a wall on the player's left and is mirrored for the right.
         */
        public static final String SIDE_TORCH = "props/depths/torch/side_torch";
        /** A hanging heraldic banner. Used to flag a room worth entering. */
        public static final String BANNER = "props/depths/flag/flag";

        /**
         * A treasure chest, and the same chest swinging open on gold.
         *
         * <p>Both are four-frame strips, and neither is a loop: {@code CHEST}
         * runs closed to ajar and {@code CHEST_OPEN} runs closed to a lid full
         * of gold. So the closed chest sits on frame 0 and the opened one plays
         * {@code CHEST_OPEN} once and rests on its last frame. A chest that
         * animated on the spot would be four frames of noise in a room the
         * player is trying to read.
         */
        public static final String CHEST = "props/depths/chest/chest";
        public static final String CHEST_OPEN = "props/depths/chest/chest_open";

        private Prop() {}
    }

    private Assets() {}
}
