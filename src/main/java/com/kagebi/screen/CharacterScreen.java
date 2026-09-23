package com.kagebi.screen;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.kagebi.Cfg;
import com.kagebi.Dir;
import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;
import com.kagebi.data.ShopCatalog;
import com.kagebi.data.def.CraftDef;
import com.kagebi.data.def.EnemyDef;
import com.kagebi.data.def.GearDef;
import com.kagebi.data.def.GemDef;
import com.kagebi.data.def.QuestDef;
import com.kagebi.data.def.SkillDef;
import com.kagebi.data.def.WeaponDef;
import com.kagebi.entity.ActorSprites;
import com.kagebi.entity.Intent;
import com.kagebi.gfx.Anim;
import com.kagebi.entity.Loadout;
import com.kagebi.entity.StatSheet;
import com.kagebi.gfx.CameraController;
import com.kagebi.input.GameAction;
import com.kagebi.save.OwnedGear;
import com.kagebi.quest.Quests;
import com.kagebi.run.RunState;
import com.kagebi.save.Profile;
import com.kagebi.save.QuestLog;
import com.kagebi.ui.Hit;
import com.kagebi.ui.Hud;
import com.kagebi.ui.I18n;

/**
 * The character sheet: what you are, what you are wearing, what you are
 * carrying, and what you can make out of it.
 *
 * <p><b>One screen with tabs rather than four screens.</b> The four things a
 * player does here are one activity - look at a number, work out which stone
 * would raise it, go and make that stone, come back and put it in - and four
 * separate screens would put a village walk between each step of it. Tabs also
 * mean one place that knows how to draw a grid of things with a cursor on it,
 * which is most of what any of these pages are.
 *
 * <p><b>Where the art comes from.</b> The forge, the equipment frame and the
 * bag grid are whole windows out of the CraftPix GUI pack, cut by
 * {@code tools/slice_heroui.py} and drawn at their own size - see
 * {@link Assets.Ui#HERO_CRAFT}. The profile page has no window of its own and
 * uses the game's own panel, because it is a list of numbers and the pack has
 * no window shaped like one.
 *
 * <p>Keyboard first, like every other screen here: arrows move the cursor,
 * {@code INTERACT} acts, {@code PAUSE} leaves, and the tab keys walk the strip.
 * The mouse does the same things and nothing else.
 */
public class CharacterScreen extends SimScreen {

    /** The tab strip, along the top where a window's own title bar would be. */
    private static final int TAB_H = 14;
    private static final int TAB_TOP = Cfg.VIRT_H - 2;
    private static final int TAB_Y = TAB_TOP - TAB_H;

    /** Where a page's window sits: centred, under the strip, above the prompts. */
    private static final int BODY_TOP = TAB_Y - 3;
    private static final int PROMPT_BOTTOM = 4;

    private static final Color INK = new Color(0x131b1bff);
    private static final Color SOFT = new Color(0x46503cff);
    private static final Color GOLD = new Color(0xffad55ff);
    private static final Color DANGER = new Color(0xd94a3aff);
    private static final Color GOOD = new Color(0x4f9d52ff);
    /** A recipe whose cost is not met yet, still listed so it can be aimed at. */
    private static final Color LOCKED = new Color(0.55f, 0.52f, 0.48f, 1f);
    /** A cell holding the thing this page is about. */
    private static final Color PICKED = new Color(1.25f, 1.05f, 0.62f, 1f);

    static final String[] TABS = {
        "panel.tab.profile", "panel.tab.gear", "panel.tab.bag",
        "panel.tab.forge", "panel.tab.tasks", "panel.tab.foes",
    };
    static final int TAB_PROFILE = 0;
    static final int TAB_GEAR = 1;
    static final int TAB_BAG = 2;
    static final int TAB_FORGE = 3;
    static final int TAB_TASKS = 4;
    static final int TAB_FOES = 5;
    /** Rows of the journal on screen at once; it scrolls around the cursor. */
    private static final int TASK_ROWS = 5;
    /** Rows of the bestiary on screen at once; it scrolls around the cursor. */
    private static final int FOE_ROWS = 7;
    /**
     * A face at the head of a bestiary row.
     *
     * <p>Twelve in a fourteen-pixel row, which leaves a pixel of air top and
     * bottom and keeps the pitch the rest of the panel was measured at. The
     * name starts where the icon ends rather than where it used to.
     */
    private static final int FOE_ICON = 12;
    private static final int FOE_ICON_X = 11;
    private static final int FOE_NAME_X = FOE_ICON_X + FOE_ICON + 4;

    private final Kagebi game;
    private final CameraController camera = new CameraController();
    private final Hit hit;

    private BitmapFont font;
    private int tab;
    /** The cursor within the active page; each page reads it as it likes. */
    private int cursor;
    /** Which of the forge's three category plates is chosen. */
    private int forgeTab = Forge.ALL.ordinal();
    /** Which character the roster panel is pointing at. */
    private int charPick;
    /** Which weapon the kit panel is pointing at: melee first, then thrown. */
    private int kitPick;
    /** Steps left of the "done" flash after an action. */
    private int flash;
    private static final int FLASH_STEPS = 40;
    /**
     * Every rectangle below was measured off the pack's own PNGs by finding the
     * recessed cells, rather than estimated from a screenshot.
     *
     * <p>They were estimated once, and all three windows were wrong in the same
     * way: close enough at the first cell to look deliberate, and drifting by a
     * pixel or two per step until the last row of the bag sat on the window's
     * bottom frame and the fifth armour slot fell off the grid entirely. An
     * off-by-one repeated down a grid does not read as an off-by-one; it reads
     * as the art being wrong.
     *
     * <p>All three windows use the same unit: a 14x14 recess on a 16px pitch.
     * Icons are 16x16 ({@link com.kagebi.assets.IconSheet#SIZE}), so an icon
     * overhangs its cell by one pixel on each side, which is what the pack's own
     * item art does and is why the cells look right with it.
     */
    private static final int CELL = 14;
    private static final int PITCH = 16;
    /** Half a pixel each side, as a float: {@code (14 - 16) / 2} is 0 in ints. */
    private static final float ICON_INSET = (CELL - com.kagebi.assets.IconSheet.SIZE) / 2f;

    /** inventory.png (110x101): 5 x 4, first cell at 17 from the left, 70 up. */
    private static final int BAG_COLUMNS = 5;
    private static final int BAG_ROWS = 4;
    private static final int BAG_LEFT = 17;
    private static final int BAG_TOP = 70;

    /**
     * equipment.png (160x85): the grid is <b>four</b> columns, not five.
     *
     * <p>The five armour slots were laid across a row that has room for four,
     * so the charm was drawn on the window's right-hand frame. Seven things now
     * have to fit - five pieces of armour and the two weapons, which the player
     * asked to see here - and seven over two rows of four is the shape the art
     * already has.
     */
    private static final int EQ_COLUMNS = 4;
    private static final int EQ_CELL_X = 81;
    private static final int EQ_CELL_TOP = 54;
    private static final int EQ_PORTRAIT_X = 6;
    private static final int EQ_PORTRAIT_Y = 20;
    private static final int EQ_PORTRAIT_W = 66;

    /**
     * craft.png (208x117), which is three panes rather than the one this used
     * to treat it as.
     *
     * <p>Left is a 3x5 browser of recipes under three category tabs; the middle
     * is a 3x3 of what a recipe costs, with the thing being made in the cell
     * beside the mortar; the right page is where its name and price go. The old
     * code put the inputs in a single column down the left - over the browser -
     * and the result as text on the right, so the two grids the art draws were
     * both left empty and everything appeared in the wrong pane.
     */
    private static final int CRAFT_LIST_X = 17;
    private static final int CRAFT_LIST_TOP = 70;
    private static final int CRAFT_LIST_COLUMNS = 3;
    private static final int CRAFT_LIST_ROWS = 5;

    private static final int CRAFT_IN_X = 81;
    private static final int CRAFT_IN_TOP = 86;
    private static final int CRAFT_IN_COLUMNS = 3;
    private static final int CRAFT_IN_ROWS = 3;

    /** The single output cell, which is 16 rather than 14 and sits low right. */
    private static final int CRAFT_OUT_X = 113;
    private static final int CRAFT_OUT_Y = 11;
    private static final int CRAFT_OUT_CELL = 16;

    /**
     * The three category plates in the top rail, left to right.
     *
     * <p>Pure background art until now: the pack draws the middle one raised,
     * which reads as a selected tab, and the game neither highlighted a
     * different one nor changed anything when they were clicked. They are the
     * obvious place to filter a list that is already twenty-five recipes long.
     */
    private static final int CRAFT_TAB_X = 16;
    private static final int CRAFT_TAB_PITCH = 16;
    private static final int CRAFT_TAB_Y = 85;
    private static final int CRAFT_TAB_W = 16;
    private static final int CRAFT_TAB_H = 16;
    /** How far the pack lifts the chosen plate above its neighbours. */
    private static final int CRAFT_TAB_LIFT = 6;

    private static final int CRAFT_PAGE_X = 139;
    private static final int CRAFT_PAGE_W = 60;

    private static final int BUTTON_W = 43;
    private static final int BUTTON_H = 18;
    private static final int BUTTON_Y = 8;

    /**
     * The profile page, laid out the way the player asked for it: the character
     * on top, what they are carrying beside them, and the numbers along the
     * bottom.
     *
     * <p>Before this it was two columns of eight numbers on a dark panel and
     * nothing else - no character, no kit, nothing to look at. The three
     * upright tabs down the right edge follow the reference they gave: one
     * panel at a time in the same frame, rather than three more tabs across an
     * already six-wide strip.
     */
    private static final int PROF_PAD = 6;
    /** The foot of the page: eight numbers over four rows, and its own frame. */
    private static final int PROF_STATS_Y = 22;
    private static final int PROF_STATS_H = 66;
    /** Everything above it starts here. */
    private static final int PROF_TOP_Y = PROF_STATS_Y + PROF_STATS_H + 4;

    private static final int PROF_HERO_X = PROF_PAD;
    private static final int PROF_HERO_W = 112;

    private static final int PROF_PANEL_X = PROF_HERO_X + PROF_HERO_W + 4;
    private static final int PROF_PANEL_W = 122;

    private static final int PROF_SIDE_X = PROF_PANEL_X + PROF_PANEL_W + 4;
    private static final int PROF_SIDE_W = Cfg.VIRT_W - PROF_SIDE_X - PROF_PAD;
    /**
     * Fifteen, not twenty. Four tabs at twenty would be eighty-nine pixels of
     * a sixty-nine pixel column; at fifteen they are exactly sixty-nine.
     */
    private static final int PROF_SIDE_H = 15;
    private static final int PROF_SIDE_GAP = 3;

    /**
     * Two columns of four, not four of two.
     *
     * <p>Four columns across 296px is 74 each, and "ST chí mạng" alone is 55 of
     * them - the Vietnamese ran straight into its own number. Vietnamese runs
     * about fifteen per cent longer than the English these were sized against,
     * so the wide-and-short arrangement was never going to hold.
     */
    private static final int PROF_STAT_COLUMNS = 2;

    /** The playable sheet's cell, which every portrait here is cut from. */
    private static final int PLAYER_CELL = 32;

    /**
     * A face in the roster: a crop wide enough to read, narrow enough for six.
     *
     * <p>Eighteen rather than the twenty-four four of them used to get. Six at
     * twenty-four is a hundred and fifty-six pixels in a hundred and
     * twenty-two pixel panel. It costs less than it sounds: these six differ
     * by colour rather than by shape, and a colour reads at any size.
     */
    private static final int CHAR_CELL = 18;
    private static final int CHAR_PITCH = CHAR_CELL + 1;

    /** A weapon in the kit panel: the icon's own size, with a cell around it. */
    private static final int KIT_CELL = 16;
    private static final int KIT_PITCH = KIT_CELL + 2;

    /**
     * A lighter page than the rest of the sheet.
     *
     * <p>Asked for directly - the profile was the darkest thing in the game,
     * dark ink on the dark PANEL_2 - and taken from colours the skin already
     * defines rather than invented, so it belongs to the same set as
     * everything else: cream for the page, ink for the numbers that matter and
     * a soft green-grey for their labels.
     */
    private static final Color PAPER = new Color(0xeecf9bff);
    private static final Color PAPER_EDGE = new Color(0x9b513cff);

    /** The portrait's animation, rebuilt only when the ninja changes. */
    private Anim heroIdle;
    private String heroCharacter;

    /** Bestiary portraits, sliced once each and kept; null for one with no art. */
    private final java.util.Map<String, TextureRegion> faces = new java.util.HashMap<>();

    /** Rebuilt whenever something is worn, made or spent. */
    private final Array<OwnedGear> shelf = new Array<>();
    private final Array<String> stones = new Array<>();

    public CharacterScreen(Kagebi game) {
        this(game, TAB_PROFILE);
    }

    public CharacterScreen(Kagebi game, int startTab) {
        this(game, startTab, 0);
    }

    /**
     * @param startSide which of the profile's upright tabs to open on, for
     *                  {@code --screen profile}: the three side panels are
     *                  otherwise only reachable by pressing a key, and a panel
     *                  that cannot be photographed does not get reviewed
     */
    public CharacterScreen(Kagebi game, int startTab, int startSide) {
        super(game.input());
        this.game = game;
        this.hit = new Hit(game.input(), camera);
        this.tab = Math.max(0, Math.min(startTab, TABS.length - 1));
        this.cursor = Math.max(0, Math.min(startSide, Side.ALL.length - 1));
    }

    @Override
    public InputProcessor inputProcessor() {
        return game.input();
    }

    @Override
    public boolean isOpaque() {
        return false;
    }

    @Override
    public void show() {
        font = game.skin().getFont("default");
        game.input().clear();
        camera.snapTo(Cfg.VIRT_W / 2f, Cfg.VIRT_H / 2f);
        restock();
        pointAtWhatIsWorn();
    }

    /**
     * Opens the two choosing panels on the choice already made.
     *
     * <p>Both used to open on index nought whoever was playing, so the roster
     * highlighted the first ninja while the village walked around as the
     * fourth. A screen that shows a selection has to show the real one: the
     * alternative is a player pressing the wear key to fix what looks wrong and
     * changing a character they had not meant to change.
     */
    private void pointAtWhatIsWorn() {
        RunState run = game.run();
        if (run == null) {
            return;
        }
        int who = Assets.Actor.indexOf(run.characterId);
        if (who >= 0) {
            charPick = who;
        }
        Array<WeaponDef> kit = kitWeapons();
        for (int i = 0; i < kit.size; i++) {
            WeaponDef w = kit.get(i);
            if (w.id.equals(w.thrown() ? run.throwWeaponId : run.weaponId)) {
                kitPick = i;
                return;
            }
        }
    }

    // ---- drawing -------------------------------------------------------------

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.05f, 0.04f, 0.07f, 1f);
        camera.apply();
        SpriteBatch batch = game.batch();
        batch.setProjectionMatrix(camera.camera().combined);
        batch.begin();
        game.skin().getDrawable("scrim").draw(batch, 0, 0, Cfg.VIRT_W, Cfg.VIRT_H);
        I18n t = game.i18n();

        drawTabs(batch, t);
        switch (tab) {
            case TAB_GEAR: drawGear(batch, t); break;
            case TAB_BAG: drawBag(batch, t); break;
            case TAB_FORGE: drawForge(batch, t); break;
            case TAB_TASKS: drawTasks(batch, t); break;
            case TAB_FOES: drawFoes(batch, t); break;
            default: drawProfile(batch, t); break;
        }
        drawFooter(batch, t);

        batch.setColor(Color.WHITE);
        batch.end();
    }

    /**
     * The eight numbers, in two columns, over the game's own panel.
     *
     * <p>No window from the pack here: it ships none shaped like a list, and
     * the numbers are the page. Two columns because eight rows down one side
     * would run past the bottom of the panel, and because the four that say
     * how hard you hit belong beside each other.
     */
    /**
     * Which of the four upright tabs on the right of the profile is open.
     *
     * <p>The last two are why the character-select screen is gone rather than
     * merely unused. It chose a character and a weapon; the sheet already
     * showed what both do to the numbers, so the choice belonged next to them
     * rather than two screens away behind a different key. While both existed
     * they could disagree about who was selected, which they did.
     */
    enum Side {
        GEAR("panel.side.gear"), ITEMS("panel.side.items"),
        CHARS("panel.side.chars"), KIT("panel.side.kit");

        static final Side[] ALL = values();
        final String key;

        Side(String key) {
            this.key = key;
        }
    }

    /**
     * The open side tab, which is simply where the profile page's cursor is.
     *
     * <p>A second cursor field would have to be kept in step with the first
     * one through every tab change, and the two would disagree the first time
     * one of them was reset and the other was not.
     */
    private int side() {
        return Math.min(cursor, Side.ALL.length - 1);
    }

    private Side openSide() {
        return Side.ALL[side()];
    }

    private void drawProfile(SpriteBatch batch, I18n t) {
        int top = BODY_TOP;
        int h = top - PROF_TOP_Y;

        // The hero, top left, on a page of his own.
        page(batch, PROF_HERO_X, PROF_TOP_Y, PROF_HERO_W, h);
        drawHero(batch, PROF_HERO_X, PROF_TOP_Y, PROF_HERO_W, h);

        // Whatever the open side tab is showing, beside him.
        page(batch, PROF_PANEL_X, PROF_TOP_Y, PROF_PANEL_W, h);
        switch (openSide()) {
            case ITEMS: drawSideItems(batch, t); break;
            case CHARS: drawSideChars(batch, t); break;
            case KIT: drawSideKit(batch, t); break;
            default: drawSideGear(batch, t); break;
        }

        drawSideTabs(batch, t, top);

        // And the numbers along the foot, which is where they were asked for.
        page(batch, PROF_PAD, PROF_STATS_Y, Cfg.VIRT_W - 2 * PROF_PAD, PROF_STATS_H);
        drawProfileStats(batch, t);
    }

    /** A cream page with a dark edge, which is what makes this tab the light one. */
    private void page(SpriteBatch batch, int x, int y, int w, int h) {
        TextureRegion px = game.skin().getRegion(Assets.Ui.PIXEL);
        batch.setColor(PAPER_EDGE);
        batch.draw(px, x, y, w, h);
        batch.setColor(PAPER);
        batch.draw(px, x + 1, y + 1, w - 2, h - 2);
        batch.setColor(Color.WHITE);
    }

    /**
     * The character, as large as the page will take him.
     *
     * <p>Scaled by a whole number and no other: a 32px ninja doubled is 64 and
     * lands on the pixel grid, where 1.5x would sample every other row twice
     * and make the outline crawl. The side-view heroes are 48 and stay at 1x
     * for the same reason.
     */
    private void drawHero(SpriteBatch batch, int x, int y, int w, int h) {
        TextureRegion frame = heroFrame();
        if (frame == null) {
            return;
        }
        int fit = Math.max(1, Math.min(w / frame.getRegionWidth(),
                                       h / frame.getRegionHeight()));
        int dw = frame.getRegionWidth() * fit;
        int dh = frame.getRegionHeight() * fit;
        batch.draw(frame, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh);

        RunState run = game.run();
        if (run == null) {
            return;
        }
        // Above his head, not below his feet.
        //
        // A doubled ninja is 64 tall in a 69 page, so there is no band left to
        // put a name in - but the sprite's own cell has its figure low and
        // nineteen empty rows over the head, and a caption costs fourteen.
        // Under the feet it was clipped by the page's own border; up here it
        // sits in space the art was never using. Shadowed, because what is
        // behind it is the sprite rather than the page.
        Hud.shadowed(batch, font, game.i18n().get("char." + run.characterId + ".name"),
                     x + w / 2f, y + h - 3, Align.center);
    }

    /** The eight numbers, two rows of four along the foot of the page. */
    private void drawProfileStats(SpriteBatch batch, I18n t) {
        StatSheet s = stats();
        String[][] rows = {
            {"stat.hp", String.valueOf(s.maxHp)},
            {"stat.damage", String.valueOf(s.damage)},
            {"stat.crit", percent(s.crit)},
            {"stat.critdmg", times(s.critDamage)},
            {"stat.throw", s.throwDamage == 0 ? "-" : String.valueOf(s.throwDamage)},
            {"stat.armour", String.valueOf(s.armour)},
            {"stat.attackspeed", times(s.attackSpeed)},
            {"stat.movespeed", String.valueOf(Math.round(s.moveSpeed))},
        };
        int usable = Cfg.VIRT_W - 2 * PROF_PAD - 12;
        int column = usable / PROF_STAT_COLUMNS;
        int firstRow = PROF_STATS_Y + PROF_STATS_H - 6;
        for (int i = 0; i < rows.length; i++) {
            // Down a column and then across, so the four defensive numbers stay
            // together rather than being dealt alternately into two columns.
            int rowsEach = rows.length / PROF_STAT_COLUMNS;
            int left = PROF_PAD + 6 + (i / rowsEach) * column;
            int y = firstRow - (i % rowsEach) * Hud.LINE;
            batch.setColor(SOFT);
            Hud.line(batch, font, t.get(rows[i][0]), left, y);
            batch.setColor(INK);
            Hud.right(batch, font, rows[i][1], left + column - 6, y);
            batch.setColor(Color.WHITE);
        }
    }

    /** The three upright tabs down the right edge. */
    private void drawSideTabs(SpriteBatch batch, I18n t, int top) {
        for (int i = 0; i < Side.ALL.length; i++) {
            int y = sideTabY(i, top);
            boolean on = i == side();
            // Not the pack's own button: HERO_BUTTON has the word CREATE
            // printed into it, so three of them down the edge read "CREATE
            // CREATE CREATE". These are the skin's blank tabs.
            game.skin().getDrawable(on ? Assets.Ui.TAB_SELECTED : Assets.Ui.TAB)
                .draw(batch, PROF_SIDE_X, y, PROF_SIDE_W, PROF_SIDE_H);
            batch.setColor(on ? INK : SOFT);
            Hud.centred(batch, font, t.get(Side.ALL[i].key),
                        PROF_SIDE_X + PROF_SIDE_W / 2f, y + PROF_SIDE_H - 4);
            batch.setColor(Color.WHITE);
        }
    }

    private static int sideTabY(int index, int top) {
        return top - PROF_SIDE_H - index * (PROF_SIDE_H + PROF_SIDE_GAP);
    }

    /** What is worn and what is held: the same seven cells as the gear tab. */
    private void drawSideGear(SpriteBatch batch, I18n t) {
        int x = PROF_PANEL_X + 8;
        int y = BODY_TOP - 8 - CELL;
        for (int i = 0; i < Cell.ALL.length; i++) {
            int cx = x + (i % EQ_COLUMNS) * PITCH;
            int cy = y - (i / EQ_COLUMNS) * PITCH;
            game.skin().getDrawable(Assets.Ui.CELL).draw(batch, cx - 1, cy - 1, CELL + 2, CELL + 2);
            TextureRegion art = cellIcon(Cell.ALL[i]);
            if (art != null) {
                centreIn(batch, art, cx, cy);
            }
        }
        batch.setColor(SOFT);
        Hud.line(batch, font, cellLine(t, Cell.WEAPON), PROF_PANEL_X + 6, y - PITCH - 4);
        Hud.line(batch, font, cellLine(t, Cell.OFFHAND), PROF_PANEL_X + 6,
                 y - PITCH - 4 - Hud.LINE);
        batch.setColor(Color.WHITE);
    }

    /** The stones and materials the player is holding, as a grid of icons. */
    private void drawSideItems(SpriteBatch batch, I18n t) {
        int x = PROF_PANEL_X + 8;
        int y = BODY_TOP - 8 - CELL;
        int columns = 6;
        int shown = 0;
        for (String id : stones) {
            int cx = x + (shown % columns) * PITCH;
            int cy = y - (shown / columns) * PITCH;
            if (cy < PROF_TOP_Y + 6) {
                break;
            }
            game.skin().getDrawable(Assets.Ui.CELL).draw(batch, cx - 1, cy - 1, CELL + 2, CELL + 2);
            TextureRegion art = inputIcon(id);
            if (art != null) {
                centreIn(batch, art, cx, cy);
            }
            shown++;
        }
        if (shown == 0) {
            batch.setColor(SOFT);
            Hud.centred(batch, font, t.get("panel.nothing"),
                        PROF_PANEL_X + PROF_PANEL_W / 2f, y);
            batch.setColor(Color.WHITE);
        }
    }

    /**
     * The roster, changeable here.
     *
     * <p>This is the only door onto the choice. There was a second screen in
     * the village that made it, and while both existed they could show
     * different answers - the sheet always opened pointing at the first ninja
     * whoever was actually playing. A locked ninja can be looked at, not worn.
     *
     * <p>Four bands now: the six faces, the name, the perk and - for a
     * character that has one - its passive. The perk is what separates these
     * from the colours they used to be, so it is what the panel spends most of
     * its last band on. The skill icons that used to sit here are gone: they
     * were drawn when all six shared one set, so showing it beside each one
     * said the same thing six times.
     *
     * <p>The passive is here because there is nowhere else it could be. The
     * three keyed skills are read by holding shift over their key; a passive
     * has no key, so without this line a player would have an ability that
     * the game never mentions.
     */
    private void drawSideChars(SpriteBatch batch, I18n t) {
        int x = PROF_PANEL_X + 4;
        int y = BODY_TOP - 4 - CHAR_CELL;
        String[] roster = Assets.Actor.CHARACTERS;
        for (int i = 0; i < roster.length; i++) {
            int cx = x + i * CHAR_PITCH;
            boolean owned = game.profile().unlockedCharacters.contains(roster[i]);
            game.skin().getDrawable(i == charPick ? Assets.Ui.FOCUS : Assets.Ui.CELL)
                .draw(batch, cx - 1, y - 1, CHAR_CELL + 2, CHAR_CELL + 2);
            TextureRegion face = faceOf(roster[i]);
            if (face != null) {
                batch.setColor(owned ? Color.WHITE : LOCKED);
                batch.draw(face, cx + (CHAR_CELL - face.getRegionWidth()) / 2f,
                           y + (CHAR_CELL - face.getRegionHeight()) / 2f);
                batch.setColor(Color.WHITE);
            }
        }
        String id = roster[charPick];
        boolean owned = game.profile().unlockedCharacters.contains(id);
        batch.setColor(owned ? INK : SOFT);
        Hud.line(batch, font, t.get("char." + id + ".name")
                 + (owned ? "" : "  " + t.get("common.locked")), x, y - 3);
        batch.setColor(Color.WHITE);
        // Wrapped: the Vietnamese runs about fifteen per cent longer than the
        // English it would otherwise be measured against, and this panel is a
        // third of the width the perk line used to have.
        font.setColor(SOFT);
        font.draw(batch, t.get("character." + id + ".desc"), x, y - 4 - Hud.LINE,
                  PERK_W, Align.left, true);
        font.setColor(Color.WHITE);
        drawSidePassive(batch, t, id, y - 4 - Hud.LINE * 2);
    }

    /**
     * The one ability a character has without a key, if it has one.
     *
     * <p>Its name and nothing else. The panel has the faces, a name and two
     * bands of perk text in sixty-five pixels, so what is left after the perk
     * is one line - and a description cut off halfway is worse than a pointer
     * to where the whole of it is. That place is the skill bar: shift on its
     * own raises the same panel the three keys raise, which is where a player
     * already goes to read about what they can do.
     */
    private void drawSidePassive(SpriteBatch batch, I18n t, String id, int y) {
        SkillDef passive = game.content().passiveFor(id);
        if (passive == null) {
            return;
        }
        int x = PROF_PANEL_X + 4;
        batch.setColor(INK);
        Hud.line(batch, font, t.get(passive.nameKey), x, y);
        batch.setColor(Color.WHITE);
    }

    /** The perk line's wrap width: the panel, less a margin each side. */
    private static final int PERK_W = PROF_PANEL_W - 8;
    /**
     * The kit: a main hand and an off hand, chosen where the character is.
     *
     * <p>The fourth upright tab, and the other half of what the select screen
     * was for. Melee along the top and thrown beneath, under one cursor,
     * because up and down already belong to the tabs themselves - so sideways
     * walks the whole kit and the two rows are only how it is laid out.
     *
     * <p>An empty off hand is a cell like any other. It is the state a fresh
     * profile is in, and without a cell for it a player who picked up a kunai
     * could never put it down.
     */
    private void drawSideKit(SpriteBatch batch, I18n t) {
        int x = PROF_PANEL_X + 4;
        Array<WeaponDef> kit = kitWeapons();
        RunState run = game.run();
        int melee = 0;
        for (WeaponDef w : kit) {
            if (!w.thrown()) {
                melee++;
            }
        }
        int topRow = BODY_TOP - 4 - KIT_CELL;
        int lowRow = topRow - KIT_PITCH - 4;
        for (int i = 0; i < kit.size; i++) {
            WeaponDef w = kit.get(i);
            boolean thrown = w.thrown();
            int column = thrown ? i - melee + 1 : i;
            int cx = x + column * KIT_PITCH;
            int cy = thrown ? lowRow : topRow;
            boolean held = run != null
                && w.id.equals(thrown ? run.throwWeaponId : run.weaponId);
            boolean owned = game.profile().unlockedWeapons.contains(w.id);
            game.skin().getDrawable(i == kitPick ? Assets.Ui.FOCUS : Assets.Ui.CELL)
                .draw(batch, cx - 1, cy - 1, KIT_CELL + 2, KIT_CELL + 2);
            TextureRegion icon = game.skin().getRegion(Assets.Ui.weaponIcon(w.id));
            if (icon != null) {
                batch.setColor(!owned ? LOCKED : held ? PICKED : Color.WHITE);
                batch.draw(icon, cx + (KIT_CELL - icon.getRegionWidth()) / 2f,
                           cy + (KIT_CELL - icon.getRegionHeight()) / 2f);
                batch.setColor(Color.WHITE);
            }
        }
        // The empty hand, at the head of the thrown row.
        boolean empty = run != null && run.throwWeaponId == null;
        game.skin().getDrawable(kitPick == kit.size ? Assets.Ui.FOCUS : Assets.Ui.CELL)
            .draw(batch, x - 1, lowRow - 1, KIT_CELL + 2, KIT_CELL + 2);
        batch.setColor(empty ? PICKED : SOFT);
        Hud.centred(batch, font, "-", x + KIT_CELL / 2f, lowRow + KIT_CELL - 3);
        batch.setColor(Color.WHITE);

        batch.setColor(INK);
        Hud.line(batch, font, kitLine(t), x, lowRow - 4);
        batch.setColor(Color.WHITE);
    }

    /** Everything that can go in a hand, melee first, in content order. */
    private Array<WeaponDef> kitWeapons() {
        Array<WeaponDef> out = new Array<>();
        for (WeaponDef w : game.content().allWeapons()) {
            if (!w.thrown()) {
                out.add(w);
            }
        }
        for (WeaponDef w : game.content().allWeapons()) {
            if (w.thrown()) {
                out.add(w);
            }
        }
        return out;
    }
    /** The name of the kit cell under the cursor, and what it hits for. */
    private String kitLine(I18n t) {
        Array<WeaponDef> kit = kitWeapons();
        if (kitPick >= kit.size) {
            return t.get("panel.offhand.none");
        }
        WeaponDef w = kit.get(kitPick);
        if (!game.profile().unlockedWeapons.contains(w.id)) {
            return t.get(w.nameKey) + "  " + t.get("common.locked");
        }
        return t.get(w.nameKey) + "  " + t.get("stat.damage") + " " + w.damage;
    }

    /**
     * Puts the picked weapon in the hand it belongs to.
     *
     * <p>Straight into the run, the way the roster beside it writes a
     * character. Which hand is not a choice: a thrown weapon is an off hand and
     * a melee one is not, so there is no way to ask for a katana in the off
     * hand and nothing to check afterwards.
     */
    private boolean wieldPicked() {
        RunState run = game.run();
        if (run == null) {
            return false;
        }
        Array<WeaponDef> kit = kitWeapons();
        if (kitPick >= kit.size) {
            if (run.throwWeaponId == null) {
                return false;
            }
            run.throwWeaponId = null;
            return true;
        }
        WeaponDef w = kit.get(kitPick);
        if (!game.profile().unlockedWeapons.contains(w.id)) {
            return false;
        }
        if (w.thrown()) {
            if (w.id.equals(run.throwWeaponId)) {
                return false;
            }
            run.throwWeaponId = w.id;
        } else {
            if (w.id.equals(run.weaponId)) {
                return false;
            }
            run.weaponId = w.id;
        }
        return true;
    }
    /**
     * A head-and-shoulders crop of a character's idle frame.
     *
     * <p>Cropped rather than scaled: a 32px frame shrunk to fit a roster cell
     * lands off the pixel grid and turns a face into porridge. The same corner
     * of each sheet reads as a row of faces.
     */
    private TextureRegion faceOf(String characterId) {
        TextureRegion sheet =
            Preload.actors().findRegion(Assets.Actor.player(characterId, "idle"));
        if (sheet == null) {
            return null;
        }
        // The first cell of the sheet, which is the down-facing idle, then the
        // shared head crop - the badge in the village needs exactly the same
        // thing and used to get it wrong.
        return Preload.face(new TextureRegion(sheet, 0, 0, PLAYER_CELL, PLAYER_CELL),
                            CHAR_CELL);
    }

    /**
     * Five slots across the pack's equipment grid, with the hero in the frame
     * the pack drew a knight into.
     *
     * <p>Icons in the cells rather than names beside them, because the window's
     * grid is 16px cells and a name does not fit in one - the name of whatever
     * the cursor is on goes under the window, where there is a whole line for
     * it. Five slots and five cells across is not a coincidence; the window was
     * chosen for it.
     */
    /**
     * The seven cells on the armour grid, in the order they are laid out.
     *
     * <p>Five slots of armour and the two weapons. The weapons are not
     * {@link GearDef.Slot}s - nothing is crafted into them and they hold no
     * stones - but the player asked to see what they are carrying in the place
     * that shows what they are wearing, which is the right instinct: "why am I
     * not hitting harder" is answered by the whole kit or by none of it.
     *
     * <p>Seven over a four-wide grid is two rows, which is what the art has.
     */
    private enum Cell {
        HEAD, BODY, HANDS, FEET, TRINKET, WEAPON, OFFHAND;

        static final Cell[] ALL = values();

        /** The armour slot this is, or null for the two weapon cells. */
        GearDef.Slot slot() {
            return ordinal() < GearDef.Slot.values().length
                ? GearDef.Slot.values()[ordinal()] : null;
        }
    }

    private void drawGear(SpriteBatch batch, I18n t) {
        TextureRegion win = game.skin().getRegion(Assets.Ui.HERO_EQUIPMENT);
        int x = (Cfg.VIRT_W - win.getRegionWidth()) / 2;
        int y = BODY_TOP - win.getRegionHeight();
        batch.draw(win, x, y);

        // The pack's left panel is a mannequin - a brown silhouette in armour,
        // with a ring and an amulet below it. The player's own sprite used to
        // be drawn on top of it, which read as two characters standing in the
        // same box. The mannequin is left to be a mannequin; the character
        // itself is on the profile tab, at a size worth looking at.

        for (int i = 0; i < Cell.ALL.length; i++) {
            int cx = x + EQ_CELL_X + (i % EQ_COLUMNS) * PITCH;
            int cy = y + EQ_CELL_TOP - (i / EQ_COLUMNS) * PITCH;
            TextureRegion art = cellIcon(Cell.ALL[i]);
            if (art != null) {
                centreIn(batch, art, cx, cy);
            }
            if (i == cursor) {
                game.skin().getDrawable(Assets.Ui.FOCUS)
                    .draw(batch, cx - 2, cy - 2, CELL + 4, CELL + 4);
            }
        }

        Cell at = Cell.ALL[Math.min(cursor, Cell.ALL.length - 1)];
        drawDetail(batch, cellLine(t, at), gearCellFacts(t, at), y);
    }

    // ---- the detail strip ----------------------------------------------------

    /**
     * The strip along the foot of the three window tabs.
     *
     * <p><b>Two problems, one fix.</b> Each of those tabs drew the name of the
     * thing under the cursor at {@code y - 2}, where {@code y} is the window's
     * bottom edge - so the one line of text on the page sat below the art, on
     * the scrim, in the same dark ink the art is written in. Ink at #131b1b on
     * a #0c0a10 scrim is a contrast ratio of 1.13 to 1, which is to say
     * invisible; the equipment window is 85px tall in a 180px screen, so what
     * was actually on screen was a small picture floating on black with an
     * unreadable caption under it.
     *
     * <p>The other problem was that nothing in the game ever showed what a
     * piece of armour did. Twenty pieces of gear and fifteen stones all carry
     * effects, and the player could read the total on the profile page but
     * never the parts - so "should I wear this?" had no answer anywhere.
     *
     * <p>Both are answered by giving the empty space below the window to a
     * page of its own, on the same cream the profile tab uses, with the name on
     * the first line and the numbers under it.
     */
    private static final int DETAIL_X = PROF_PAD;
    /**
     * Above the footer, not behind it. At 4 the page ran the full way down and
     * the gold counter and the key prompts were drawn on top of it - orange on
     * cream, and a dark key cap in the middle of a light page.
     */
    private static final int DETAIL_Y = PROMPT_BOTTOM + Hud.LINE + 2;
    private static final int DETAIL_W = Cfg.VIRT_W - 2 * PROF_PAD;
    /**
     * Three, because the strip is what is left under a window and that is one
     * line on the forge tab and two on the bag. A piece of gear can carry
     * three effects and three stones, and six numbers across two rows only
     * fits if a row holds three of them.
     */
    private static final int DETAIL_COLUMNS = 3;

    private void drawDetail(SpriteBatch batch, String title, Array<String[]> facts,
                            int windowBottom) {
        int h = windowBottom - DETAIL_Y - 4;
        if (h < Hud.LINE) {
            return;
        }
        page(batch, DETAIL_X, DETAIL_Y, DETAIL_W, h);
        int firstRow = DETAIL_Y + h - 4;
        batch.setColor(INK);
        Hud.line(batch, font, title, DETAIL_X + 6, firstRow);
        batch.setColor(Color.WHITE);

        int rows = (h - 6) / Hud.LINE - 1;
        int column = (DETAIL_W - 12) / DETAIL_COLUMNS;
        for (int i = 0; i < facts.size && i < rows * DETAIL_COLUMNS; i++) {
            int left = DETAIL_X + 6 + (i % DETAIL_COLUMNS) * column;
            int y = firstRow - Hud.LINE * (1 + i / DETAIL_COLUMNS);
            batch.setColor(SOFT);
            Hud.line(batch, font, facts.get(i)[0], left, y);
            batch.setColor(INK);
            Hud.right(batch, font, facts.get(i)[1], left + column - 8, y);
            batch.setColor(Color.WHITE);
        }
    }

    /**
     * An effect's magnitude as a player should read it.
     *
     * <p>Three shapes hide behind one float. A name ending {@code _mult} is a
     * factor, so 1.08 is eight per cent more; a chance is already a fraction,
     * so 0.02 is two per cent; everything else is a flat number of hit points
     * or armour. Written once here because the same names are read by gear, by
     * stones and by the forge.
     */
    static String formatEffect(String effect, float magnitude) {
        if (effect.endsWith("_mult")) {
            return signed(Math.round((magnitude - 1f) * 100f)) + "%";
        }
        if (effect.endsWith("_chance_add")) {
            return signed(Math.round(magnitude * 100f)) + "%";
        }
        return signed(Math.round(magnitude));
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }

    private Array<String[]> effectFacts(I18n t, String[] effects, float[] magnitudes) {
        Array<String[]> out = new Array<>();
        for (int i = 0; i < effects.length && i < magnitudes.length; i++) {
            out.add(new String[] {t.get("effect." + effects[i]),
                                  formatEffect(effects[i], magnitudes[i])});
        }
        return out;
    }

    /** Everything worth knowing about the gear or weapon cell under the cursor. */
    private Array<String[]> gearCellFacts(I18n t, Cell cell) {
        GearDef.Slot slot = cell.slot();
        if (slot == null) {
            String id = weaponIn(cell);
            return id == null ? new Array<String[]>() : weaponFacts(t, id);
        }
        OwnedGear worn = game.profile().worn(slot.name());
        if (worn == null || !game.content().hasGear(worn.defId)) {
            return new Array<String[]>();
        }
        return ownedGearFacts(t, worn);
    }

    /**
     * A worn or stored piece, and what its stones add.
     *
     * <p>The stones are listed beside the piece rather than folded into it. A
     * player choosing between two helmets wants to know which numbers are the
     * helmet's and which came out of a socket they could move.
     */
    private Array<String[]> ownedGearFacts(I18n t, OwnedGear owned) {
        GearDef def = game.content().gear(owned.defId);
        Array<String[]> out = effectFacts(t, def.effects, def.magnitudes);
        for (String id : owned.sockets) {
            if (id == null || !game.content().hasGem(id)) {
                continue;
            }
            GemDef gem = game.content().gem(id);
            out.add(new String[] {t.get("effect." + gem.effect),
                                  formatEffect(gem.effect, gem.magnitude)});
        }
        return out;
    }

    /** What a weapon does, including the number the file derives its pacing from. */
    private Array<String[]> weaponFacts(I18n t, String id) {
        Array<String[]> out = new Array<>();
        WeaponDef w = game.content().weapon(id);
        if (w == null) {
            return out;
        }
        out.add(new String[] {t.get("stat.damage"), String.valueOf(w.damage)});
        out.add(new String[] {t.get("detail.reach"), Math.round(w.reach) + ""});
        return out;
    }

    /** The stone under the cursor: one effect, and how many are in the drawer. */
    private Array<String[]> gemFacts(I18n t, String id) {
        Array<String[]> out = new Array<>();
        GemDef gem = game.content().gem(id);
        out.add(new String[] {t.get("effect." + gem.effect),
                              formatEffect(gem.effect, gem.magnitude)});
        out.add(new String[] {t.format("detail.tier", gem.tier), gem.colour.name()});
        return out;
    }

    /**
     * Draws a region in the middle of a cell, whatever size it is.
     *
     * <p>Item icons are all 16 and armour cells are 14, so the usual case is a
     * one-pixel overhang. Weapon icons are not on that sheet and are not all
     * one size, so the offset is taken from the region rather than assumed -
     * the assumption is what left a kunai hanging out of the top of its cell.
     */
    private static void centreIn(SpriteBatch batch, TextureRegion art, int cx, int cy) {
        batch.draw(art, cx + (CELL - art.getRegionWidth()) / 2f,
                        cy + (CELL - art.getRegionHeight()) / 2f);
    }

    /** What is in a cell: a worn piece's icon, or a weapon's. */
    private TextureRegion cellIcon(Cell cell) {
        GearDef.Slot slot = cell.slot();
        if (slot != null) {
            OwnedGear piece = game.profile().worn(slot.name());
            if (piece == null || !game.content().hasGear(piece.defId)) {
                return null;
            }
            int icon = game.content().gear(piece.defId).icon;
            return icon > 0 ? Preload.icon(icon) : null;
        }
        String id = weaponIn(cell);
        return id == null ? null : game.skin().getRegion(Assets.Ui.weaponIcon(id));
    }

    /** The weapon id in one of the two weapon cells, or null for an empty hand. */
    private String weaponIn(Cell cell) {
        RunState run = game.run();
        if (run == null) {
            return null;
        }
        return cell == Cell.WEAPON ? run.weaponId : run.throwWeaponId;
    }

    /** The line under the window: what the cursor is on, and what is in it. */
    private String cellLine(I18n t, Cell cell) {
        String label = t.get("slot." + cell.name()) + ": ";
        GearDef.Slot slot = cell.slot();
        if (slot == null) {
            String id = weaponIn(cell);
            return label + (id == null ? t.get("panel.empty")
                : t.get(game.content().weapon(id).nameKey));
        }
        OwnedGear chosen = game.profile().worn(slot.name());
        if (chosen == null || !game.content().hasGear(chosen.defId)) {
            return label + t.get("panel.empty");
        }
        String line = label + t.get(game.content().gear(chosen.defId).nameKey);
        if (chosen.sockets.length > 0) {
            line += "   " + t.format("panel.sockets", chosen.stonesSet(), chosen.sockets.length);
        }
        return line;
    }

    /**
     * The hero's idle frame, for the portrait panel.
     *
     * <p>Facing the player, and breathing. Through {@code Preload.idle} rather
     * than sliced here: the sheet is four columns of facings by however many
     * rows of frames, and walking it by hand walked the columns - so the
     * portrait turned on the spot every fifth of a second instead of idling.
     *
     * <p>Null when there is no run, which is only the case from a debug flag.
     */
    private TextureRegion heroFrame() {
        if (game.run() == null) {
            return null;
        }
        String id = game.run().characterId;
        if (heroIdle == null || !id.equals(heroCharacter)) {
            heroCharacter = id;
            heroIdle = Preload.idle(id);
        }
        return heroIdle.frame(Dir.DOWN, steps());
    }

    /**
     * Everything owned, plus the stones, over the pack's bag grid.
     *
     * <p>Gear and stones in one grid rather than two tabs, because at the forge
     * they are the same thing: both are what a recipe eats. The grid is five
     * across because that is how the window is drawn.
     */
    private void drawBag(SpriteBatch batch, I18n t) {
        TextureRegion win = game.skin().getRegion(Assets.Ui.HERO_INVENTORY);
        int x = (Cfg.VIRT_W - win.getRegionWidth()) / 2;
        int y = BODY_TOP - win.getRegionHeight();
        batch.draw(win, x, y);

        int n = shelf.size + stones.size;
        if (n == 0) {
            // Ink, not the soft green-grey: this sits on the window's own cream
            // rather than on the scrim, where soft is 3.8 to 1 and reads as a
            // smudge.
            batch.setColor(INK);
            Hud.centred(batch, font, t.get("panel.nothing"), Cfg.VIRT_W / 2f,
                        y + win.getRegionHeight() / 2);
            batch.setColor(Color.WHITE);
            return;
        }
        for (int i = 0; i < Math.min(n, BAG_COLUMNS * BAG_ROWS); i++) {
            int cx = x + BAG_LEFT + (i % BAG_COLUMNS) * PITCH;
            int cy = y + BAG_TOP - (i / BAG_COLUMNS) * PITCH;
            TextureRegion art = bagIcon(i);
            if (art != null) {
                batch.setColor(i < shelf.size && isWorn(shelf.get(i)) ? PICKED : Color.WHITE);
                batch.draw(art, cx + ICON_INSET, cy + ICON_INSET);
                batch.setColor(Color.WHITE);
            }
            if (i == cursor) {
                game.skin().getDrawable(Assets.Ui.FOCUS)
                    .draw(batch, cx - 2, cy - 2, CELL + 4, CELL + 4);
            }
        }
        drawDetail(batch, bagName(t), bagFacts(t), y);
    }

    /** The numbers behind whatever the bag's cursor is on. */
    private Array<String[]> bagFacts(I18n t) {
        int n = shelf.size + stones.size;
        if (cursor < 0 || cursor >= n) {
            return new Array<String[]>();
        }
        return cursor < shelf.size ? ownedGearFacts(t, shelf.get(cursor))
            : gemFacts(t, stones.get(cursor - shelf.size));
    }

    /** The icon for one bag cell: gear first, then the stones. */
    private TextureRegion bagIcon(int index) {
        int icon = index < shelf.size
            ? game.content().gear(shelf.get(index).defId).icon
            : game.content().gem(stones.get(index - shelf.size)).icon;
        return icon <= 0 ? null : Preload.icon(icon);
    }

    /** The name and count of whatever the bag's cursor is on. */
    private String bagName(I18n t) {
        int n = shelf.size + stones.size;
        if (cursor < 0 || cursor >= n) {
            return "";
        }
        if (cursor < shelf.size) {
            OwnedGear g = shelf.get(cursor);
            GearDef def = game.content().gear(g.defId);
            String name = t.get(def.nameKey);
            return isWorn(g) ? name + "  (" + t.get("panel.worn") + ")" : name;
        }
        String id = stones.get(cursor - shelf.size);
        return t.get(game.content().gem(id).nameKey)
            + "  x" + game.profile().material(id);
    }

    /**
     * The three category plates, and what each one shows.
     *
     * <p>Order matches the art left to right: the hammer forges armour, the
     * mortar grinds stones, the scissors are everything. A filter rather than a
     * decoration, because the recipe list is twenty-five long and the player
     * arriving to make one piece of armour should not leaf past fifteen stones.
     */
    private enum Forge {
        GEAR, GEMS, ALL;

        static final Forge[] ALL_TABS = values();

        boolean shows(CraftDef line) {
            switch (this) {
                case GEAR: return line.kind == CraftDef.Output.GEAR;
                case GEMS: return line.kind == CraftDef.Output.GEM;
                default: return true;
            }
        }
    }

    /**
     * The forge, laid out on the three panes the pack actually drew.
     *
     * <p>Left is a browser of recipes under the three category plates, the
     * middle 3x3 is what the chosen one costs, the cell beside the mortar is
     * what comes out, and the right page names it and prices it.
     *
     * <p>It used to be one recipe at a time with its inputs in a single column
     * down the left - on top of the browser grid - and the result as text on
     * the right. Both grids the art draws sat empty, the counts landed in the
     * browser's cells, and the only way to find a recipe was to press a
     * direction twenty-five times.
     */
    private void drawForge(SpriteBatch batch, I18n t) {
        TextureRegion win = game.skin().getRegion(Assets.Ui.HERO_CRAFT);
        int x = (Cfg.VIRT_W - win.getRegionWidth()) / 2;
        int y = BODY_TOP - win.getRegionHeight();
        batch.draw(win, x, y);

        drawForgeTabs(batch, x, y);

        Array<CraftDef> lines = recipes();
        if (lines.isEmpty()) {
            // The middle of this window is the darkest surface on the screen,
            // #825c2f, and the soft green-grey that reads well on cream is 1.4
            // to 1 on it. This is the skin's own light ink, for dark ground.
            batch.setColor(PAPER);
            Hud.centred(batch, font, t.get("panel.nothing"), Cfg.VIRT_W / 2f,
                        y + win.getRegionHeight() / 2);
            batch.setColor(Color.WHITE);
            drawDetail(batch, "", new Array<String[]>(), y);
            return;
        }
        int at = Math.min(cursor, lines.size - 1);
        int page = at / (CRAFT_LIST_COLUMNS * CRAFT_LIST_ROWS);
        int first = page * CRAFT_LIST_COLUMNS * CRAFT_LIST_ROWS;

        // The browser. A recipe is shown as the thing it makes, which is what
        // the player is looking for; what it costs is the middle pane's job.
        for (int i = 0; i < CRAFT_LIST_COLUMNS * CRAFT_LIST_ROWS; i++) {
            int index = first + i;
            if (index >= lines.size) {
                break;
            }
            int cx = x + CRAFT_LIST_X + (i % CRAFT_LIST_COLUMNS) * PITCH;
            int cy = y + CRAFT_LIST_TOP - (i / CRAFT_LIST_COLUMNS) * PITCH;
            TextureRegion art = outputIcon(lines.get(index));
            if (art != null) {
                batch.setColor(canForge(lines.get(index)) ? Color.WHITE : LOCKED);
                batch.draw(art, cx + ICON_INSET, cy + ICON_INSET);
                batch.setColor(Color.WHITE);
            }
            if (index == at) {
                game.skin().getDrawable(Assets.Ui.FOCUS)
                    .draw(batch, cx - 2, cy - 2, CELL + 4, CELL + 4);
            }
        }

        CraftDef line = lines.get(at);
        drawForgeCost(batch, line, x, y);

        // What comes out, in the cell the art puts beside the mortar.
        TextureRegion made = outputIcon(line);
        if (made != null) {
            batch.draw(made, x + CRAFT_OUT_X + (CRAFT_OUT_CELL - made.getRegionWidth()) / 2f,
                       y + CRAFT_OUT_Y + (CRAFT_OUT_CELL - made.getRegionHeight()) / 2f);
        }

        drawForgePage(batch, t, line, x, y);

        drawDetail(batch, forgeTitle(t, line, at, lines.size), forgeFacts(t, line), y);
    }

    /** What the forge is pointing at, and where in the list it sits. */
    private String forgeTitle(I18n t, CraftDef line, int at, int total) {
        return madeName(t, line) + "   " + (at + 1) + "/" + total;
    }

    /** What the recipe makes, in the same numbers the gear tab would show. */
    private Array<String[]> forgeFacts(I18n t, CraftDef line) {
        if (line.kind == CraftDef.Output.GEM) {
            return gemFacts(t, line.output);
        }
        if (game.content().hasGear(line.output)) {
            GearDef def = game.content().gear(line.output);
            Array<String[]> out = effectFacts(t, def.effects, def.magnitudes);
            out.add(new String[] {t.format("detail.tier", def.tier), ""});
            return out;
        }
        return new Array<String[]>();
    }

    /** The category plates. The chosen one is lifted, the way the pack draws it. */
    private void drawForgeTabs(SpriteBatch batch, int x, int y) {
        for (int i = 0; i < Forge.ALL_TABS.length; i++) {
            boolean on = i == forgeTab;
            int cx = x + CRAFT_TAB_X + i * CRAFT_TAB_PITCH;
            int cy = y + CRAFT_TAB_Y + (on ? CRAFT_TAB_LIFT : 0);
            if (on) {
                game.skin().getDrawable(Assets.Ui.FOCUS)
                    .draw(batch, cx - 1, cy - 1, CRAFT_TAB_W + 2, CRAFT_TAB_H + 2);
            }
        }
    }

    /** The 3x3 of what a recipe costs, with the count beside each icon. */
    private void drawForgeCost(SpriteBatch batch, CraftDef line, int x, int y) {
        int slots = CRAFT_IN_COLUMNS * CRAFT_IN_ROWS;
        for (int i = 0; i < line.inputs.length && i < slots; i++) {
            int cx = x + CRAFT_IN_X + (i % CRAFT_IN_COLUMNS) * PITCH;
            int cy = y + CRAFT_IN_TOP - (i / CRAFT_IN_COLUMNS) * PITCH;
            TextureRegion art = inputIcon(line.inputs[i]);
            if (art != null) {
                batch.draw(art, cx + ICON_INSET, cy + ICON_INSET);
            }
            // In the cell's bottom-right corner, over a drop shadow, the way
            // a stack count is drawn in every game that has one. Two things
            // forced it here: under the cell there is only a two-pixel gutter,
            // and plain text over the icon disappeared into it.
            //
            // Green when the player has enough, red when they do not, so the
            // reason CREATE is dark can be read at a glance.
            int have = held(line.inputs[i]);
            batch.setColor(have >= line.counts[i] ? GOOD : DANGER);
            Hud.shadowed(batch, font, String.valueOf(line.counts[i]),
                         cx + CELL + 1, cy + 9, Align.right);
            batch.setColor(Color.WHITE);
        }
    }

    /** What a recipe makes, by name. */
    private String madeName(I18n t, CraftDef line) {
        return t.get(line.kind == CraftDef.Output.GEM
            ? game.content().gem(line.output).nameKey
            : game.content().gear(line.output).nameKey);
    }

    /** The right page: what is being made, what it costs in gold, and the button. */
    private void drawForgePage(SpriteBatch batch, I18n t, CraftDef line, int x, int y) {
        int page = x + CRAFT_PAGE_X;
        String made = madeName(t, line);
        font.setColor(INK);
        font.draw(batch, made, page, y + 100, CRAFT_PAGE_W, Align.center, true);
        font.setColor(Color.WHITE);
        if (line.goldCost > 0) {
            batch.setColor(game.profile().gold >= line.goldCost ? GOOD : DANGER);
            Hud.centred(batch, font, String.valueOf(line.goldCost),
                        page + CRAFT_PAGE_W / 2f, y + 44);
            batch.setColor(Color.WHITE);
        }
        int bx = page + (CRAFT_PAGE_W - BUTTON_W) / 2;
        String face = !canForge(line) ? Assets.Ui.HERO_BUTTON_OFF
            : hit.over(bx, y + BUTTON_Y, BUTTON_W, BUTTON_H) ? Assets.Ui.HERO_BUTTON_OVER
            : Assets.Ui.HERO_BUTTON;
        batch.draw(game.skin().getRegion(face), bx, y + BUTTON_Y);
    }

    /** The icon of whatever a recipe produces. */
    private TextureRegion outputIcon(CraftDef line) {
        int icon = line.kind == CraftDef.Output.GEM
            ? game.content().gem(line.output).icon
            : game.content().gear(line.output).icon;
        return icon <= 0 ? null : Preload.icon(icon);
    }

    /** The icon for a recipe input, whichever of the three kinds it is. */
    private TextureRegion inputIcon(String id) {
        int icon = game.content().hasGem(id) ? game.content().gem(id).icon
            : game.content().hasGear(id) ? game.content().gear(id).icon
            : game.content().item(id).icon;
        return icon <= 0 ? null : Preload.icon(icon);
    }

    private String inputName(I18n t, String id) {
        if (game.content().hasGem(id)) {
            return t.get(game.content().gem(id).nameKey);
        }
        if (game.content().hasGear(id)) {
            return t.get(game.content().gear(id).nameKey);
        }
        return t.get(game.content().item(id).nameKey);
    }

    /**
     * A bestiary row's portrait: the first frame of the enemy's idle, cropped
     * to its head.
     *
     * <p>Through {@code ActorSprites.enemy}, which is the one place that knows
     * how every enemy sheet is laid out - four-column directionals, single
     * strips, the depths' horizontal sets, and five different cell sizes from
     * 16 to 82. The bestiary would otherwise need its own copy of that, and a
     * second copy is a second thing to be wrong.
     *
     * <p>Not {@code Assets.Actor.monsterFace}, which exists and points at real
     * 38px portraits: only fifteen of the thirty-one enemies have one, so half
     * the list would be blank and the other half would be drawn in a different
     * style from its neighbours.
     *
     * <p>Built when a row first scrolls into view and kept. Seven rows are on
     * screen at once and each one means slicing a sheet, so building all
     * thirty-one on the way into the tab would be paying for twenty-four
     * portraits nobody has asked to see.
     */
    private TextureRegion foeFace(EnemyDef def) {
        if (faces.containsKey(def.id)) {
            return faces.get(def.id);
        }
        TextureRegion face = null;
        ActorSprites sprites = ActorSprites.enemy(Preload.actors(), def);
        if (sprites != null && sprites.idle != null) {
            TextureRegion frame = sprites.idle.frame(Dir.DOWN, 0);
            if (frame != null) {
                // The measured top of the ink, not a guess from the frame
                // height: a 32px skeleton fills its cell and a 64px orb floats
                // in the middle of one, and the same rule of thumb cannot find
                // a head in both. It found a waist in the skeleton.
                face = Preload.face(frame, FOE_ICON, sprites.figureTop());
            }
        }
        faces.put(def.id, face);
        return face;
    }

    /**
     * The bestiary: every monster in the game, and the numbers for the ones
     * that have been fought.
     *
     * <p>Unmet monsters are listed and greyed rather than hidden. The same
     * argument as the locked characters on the select screen: a player who
     * cannot see the empty rows has no reason to believe there is anything left
     * to find, and the count at the foot is what two of the shop's unlocks are
     * priced against.
     *
     * <p>Names are hidden too, not only the numbers. A row that said "Squidlord
     * - ???" would give away the shape of what is coming, and finding out is
     * the point of going down there.
     */
    private void drawFoes(SpriteBatch batch, I18n t) {
        int top = BODY_TOP;
        int h = top - 22;
        game.skin().getDrawable(Assets.Ui.PANEL_2).draw(batch, 6, top - h, Cfg.VIRT_W - 12, h);

        Array<EnemyDef> all = foes();
        if (all.isEmpty()) {
            return;
        }
        int at = Math.min(cursor, all.size - 1);
        // A window of rows around the cursor, so a list of thirty-one scrolls
        // rather than running off the panel.
        int rows = FOE_ROWS;
        int first = Math.max(0, Math.min(at - rows / 2, all.size - rows));
        int y = top - 12;
        for (int i = first; i < Math.min(all.size, first + rows); i++) {
            EnemyDef e = all.get(i);
            boolean known = game.profile().bestiary.contains(e.id);
            if (i == at) {
                // Measured, not guessed. The row used to start at
                // y - LINE + 2, which put the frame's lower rule straight
                // through the bottom of the glyphs; on an unmet row, where the
                // whole content is question marks, that reads as the text
                // having slipped off its line, which is what it was reported as.
                //
                // The numbers: Hud.line puts the ink of a 9px row at y-11 to
                // y-5, and this nine-patch insets its rules by two pixels at
                // the bottom and three at the top. One pixel taller than the
                // line, one pixel lower, is what clears the ink at both ends.
                game.skin().getDrawable(Assets.Ui.FOCUS)
                    .draw(batch, 10, y - Hud.LINE - 1, Cfg.VIRT_W - 20, Hud.LINE + 1);
            }
            // The face at the head of the row, for a met enemy only. An unmet
            // one keeps its blank: the name is withheld precisely so the shape
            // of what is coming is a surprise, and a silhouette gives that away
            // as surely as a name does. The name column does not move, so the
            // rows still line up either way.
            if (known) {
                TextureRegion face = foeFace(e);
                if (face != null) {
                    batch.draw(face, FOE_ICON_X, y - Hud.LINE + 2, FOE_ICON, FOE_ICON);
                }
            }
            batch.setColor(known ? INK : SOFT);
            // "???" rather than "? ? ?": spaced out, three marks read as three
            // separate things on the line instead of one withheld name.
            Hud.line(batch, font, known ? t.get(e.nameKey) : "???", FOE_NAME_X, y);
            if (known) {
                batch.setColor(SOFT);
                Hud.right(batch, font, e.maxHp + " hp", Cfg.VIRT_W - 14, y);
            }
            batch.setColor(Color.WHITE);
            y -= Hud.LINE;
        }

        EnemyDef chosen = all.get(at);
        int bottom = top - h + 4;
        if (game.profile().bestiary.contains(chosen.id)) {
            batch.setColor(SOFT);
            Hud.line(batch, font, foeLine(t, chosen), 14, bottom + Hud.LINE);
            batch.setColor(Color.WHITE);
        } else {
            batch.setColor(SOFT);
            Hud.line(batch, font, t.get("foes.unmet"), 14, bottom + Hud.LINE);
            batch.setColor(Color.WHITE);
        }
        batch.setColor(SOFT);
        Hud.right(batch, font, game.profile().bestiary.size + "/" + all.size,
                  Cfg.VIRT_W - 14, bottom + Hud.LINE);
        batch.setColor(Color.WHITE);
    }

    /** The four numbers worth knowing about something that is trying to kill you. */
    private String foeLine(I18n t, EnemyDef e) {
        int hits = Math.max(e.contactDamage, e.attackDamage);
        return t.get("stat.hp") + " " + e.maxHp
            + "   " + t.get("stat.damage") + " " + hits
            + "   " + t.get("stat.movespeed") + " " + Math.round(e.moveSpeed)
            + "   " + e.brain;
    }

    /** Every monster, bosses last, in a stable order. */
    private Array<EnemyDef> foes() {
        Array<EnemyDef> out = new Array<>(game.content().allEnemies());
        out.sort((a, b) -> a.boss != b.boss ? (a.boss ? 1 : -1) : a.id.compareTo(b.id));
        return out;
    }

    /**
     * The journal: what is going, how far along it is, and which one the arrow
     * is pointing at.
     *
     * <p>Claimed jobs drop off the list entirely. A journal that kept every
     * finished job would be a list whose top was history and whose bottom was
     * work, and the player reads it to find the work.
     */
    private void drawTasks(SpriteBatch batch, I18n t) {
        int top = BODY_TOP;
        int h = top - 22;
        game.skin().getDrawable(Assets.Ui.PANEL_2).draw(batch, 6, top - h, Cfg.VIRT_W - 12, h);

        Array<QuestDef> open = jobs();
        if (open.isEmpty()) {
            batch.setColor(SOFT);
            Hud.centred(batch, font, t.get("quest.none"), Cfg.VIRT_W / 2f, top - h / 2);
            batch.setColor(Color.WHITE);
            return;
        }
        int at = Math.min(cursor, open.size - 1);
        int rows = TASK_ROWS;
        int first = Math.max(0, Math.min(at - rows / 2, open.size - rows));
        int y = top - 12;
        for (int i = first; i < Math.min(open.size, first + rows); i++) {
            QuestDef q = open.get(i);
            QuestLog.State state = game.profile().quests.state(q.id);
            if (i == at) {
                game.skin().getDrawable(Assets.Ui.FOCUS)
                    .draw(batch, 10, y - Hud.LINE + 2, Cfg.VIRT_W - 20, Hud.LINE);
            }
            batch.setColor(state == QuestLog.State.DONE ? GOOD : INK);
            Hud.line(batch, font, t.get(q.nameKey), 14, y);
            batch.setColor(SOFT);
            Hud.right(batch, font, tag(t, q, state), Cfg.VIRT_W - 14, y);
            batch.setColor(Color.WHITE);
            y -= Hud.LINE;
        }

        // The chosen job's own line, and its steps, under the list.
        QuestDef chosen = open.get(at);
        int bottom = top - h + 4;
        batch.setColor(SOFT);
        font.setColor(SOFT);
        font.draw(batch, t.get(chosen.descKey), 14, bottom + Hud.LINE * 3,
                  Cfg.VIRT_W - 28, Align.left, true);
        font.setColor(Color.WHITE);
        Hud.line(batch, font, steps(t, chosen), 14, bottom + Hud.LINE);
        batch.setColor(Color.WHITE);
    }

    /** Where a job is, in one word. */
    private String tag(I18n t, QuestDef q, QuestLog.State state) {
        if (state == QuestLog.State.DONE) {
            return t.get("quest.done");
        }
        if (q.id.equals(game.profile().tracked)) {
            return t.get("quest.tracked");
        }
        return state == null ? "" : progressOf(q) + "%";
    }

    /** How far through a job is, as a percentage of its steps' totals. */
    private int progressOf(QuestDef q) {
        int have = 0;
        int want = 0;
        for (int i = 0; i < q.steps.length; i++) {
            have += Math.min(game.profile().quests.progress(q.id, i), q.steps[i].count);
            want += q.steps[i].count;
        }
        return want == 0 ? 0 : have * 100 / want;
    }

    /** "3/5, 0/8" - the counts, in the order the steps are written. */
    private String steps(I18n t, QuestDef q) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < q.steps.length; i++) {
            if (i > 0) {
                out.append(",  ");
            }
            out.append(Math.min(game.profile().quests.progress(q.id, i), q.steps[i].count))
               .append('/').append(q.steps[i].count);
        }
        return out.toString();
    }

    /** Every job worth showing: unlocked, unclaimed, in a stable order. */
    private Array<QuestDef> jobs() {
        return Quests.available(game.content(), game.profile());
    }

    /**
     * Points the arrow at the job under the cursor, or turns it off again.
     *
     * <p>One at a time. An arrow for every active job would be a compass rose,
     * and the point of the arrow is that it answers "where now".
     */
    private boolean track() {
        Array<QuestDef> open = jobs();
        if (open.isEmpty()) {
            return false;
        }
        QuestDef q = open.get(Math.min(cursor, open.size - 1));
        game.profile().tracked = q.id.equals(game.profile().tracked) ? null : q.id;
        return true;
    }

    private void drawStats(SpriteBatch batch, I18n t, String[][] rows,
                           int left, int right, int top) {
        int y = top;
        for (String[] pair : rows) {
            batch.setColor(SOFT);
            Hud.line(batch, font, t.get(pair[0]), left, y);
            batch.setColor(INK);
            Hud.right(batch, font, pair[1], right, y);
            batch.setColor(Color.WHITE);
            y -= Hud.LINE;
        }
    }

    /** 0.075 reads as "7%", which is the precision the numbers are chosen at. */
    private static String percent(float v) {
        return Math.round(v * 100f) + "%";
    }

    /** 1.15 reads as "x1.15"; 1.0 reads as "x1" rather than "x1.0". */
    private static String times(float v) {
        float rounded = Math.round(v * 100f) / 100f;
        return rounded == Math.round(rounded)
            ? "x" + Math.round(rounded)
            : "x" + String.format(java.util.Locale.ROOT, "%.2f", rounded);
    }

    private static int tabW() {
        return Cfg.VIRT_W / TABS.length;
    }

    private static int tabX(int index) {
        return index * tabW();
    }

    private void drawTabs(SpriteBatch batch, I18n t) {
        for (int i = 0; i < TABS.length; i++) {
            boolean on = i == tab;
            String face = on ? Assets.Ui.TAB_SELECTED
                : hit.over(tabX(i), TAB_Y, tabW(), TAB_H) ? Assets.Ui.TAB_OVER : Assets.Ui.TAB;
            game.skin().getDrawable(face).draw(batch, tabX(i), TAB_Y, tabW(), TAB_H);
            batch.setColor(on ? INK : SOFT);
            Hud.centred(batch, font, t.get(TABS[i]), tabX(i) + tabW() / 2f, TAB_Y + TAB_H - 1);
            batch.setColor(Color.WHITE);
        }
    }

    /**
     * The purse and the one prompt this screen has.
     *
     * <p>The coin is the spinning one rather than the HUD's seven-pixel still:
     * outside the dungeon there is room for it, and a six-figure total beside a
     * seven-pixel picture reads as a number with a speck next to it.
     */
    private void drawFooter(SpriteBatch batch, I18n t) {
        TextureRegion coin = Preload.coin();
        if (coin != null) {
            batch.draw(coin, 4, PROMPT_BOTTOM);
        }
        batch.setColor(GOLD);
        Hud.line(batch, font, String.valueOf(game.profile().gold), 22,
                 PROMPT_BOTTOM + Hud.LINE);
        batch.setColor(Color.WHITE);

        if (flash > 0) {
            batch.setColor(GOOD);
            Hud.centred(batch, font, t.get("trade.done"), Cfg.VIRT_W / 2f,
                        PROMPT_BOTTOM + Hud.LINE);
            batch.setColor(Color.WHITE);
        } else {
            String verb = verb();
            if (verb != null) {
                Hud.prompt(batch, game.skin(), font,
                    game.input().map().primary(GameAction.INTERACT), t.get(verb),
                    Cfg.VIRT_W / 2f, PROMPT_BOTTOM, Align.center);
            }
        }
        Hud.prompt(batch, game.skin(), font,
            game.input().map().primary(GameAction.PAUSE), t.get("common.back"),
            Cfg.VIRT_W - 6f, PROMPT_BOTTOM, Align.right);
    }

    /** The one verb this page offers, or null for a page that only shows things. */
    private String verb() {
        switch (tab) {
            case TAB_PROFILE:
                switch (openSide()) {
                    case CHARS: return "panel.side.wear";
                    case KIT: return "panel.side.wield";
                    default: return null;
                }
            case TAB_GEAR:
                GearDef.Slot armour = Cell.ALL[Math.min(cursor, Cell.ALL.length - 1)].slot();
                if (armour == null) {
                    return null;
                }
                return game.profile().worn(armour.name()) == null
                    ? "panel.equip" : "panel.unequip";
            case TAB_FORGE:
                return recipes().isEmpty() ? null : "panel.forge";
            case TAB_TASKS:
                return jobs().isEmpty() ? null : "quest.track";
            default:
                return null;
        }
    }

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
    }

    // ---- what the pages are looking at ---------------------------------------

    /**
     * Rebuilds the two lists the pages walk.
     *
     * <p>Called after anything that can change them rather than every frame:
     * a sort on every draw would reorder the shelf under the cursor mid-press.
     * The order is by slot then tier so a player's own gear arrives in the
     * order they think about it, and never depends on the order it was made in.
     */
    private void restock() {
        Profile p = game.profile();
        shelf.clear();
        for (OwnedGear g : p.stash) {
            if (game.content().hasGear(g.defId)) {
                shelf.add(g);
            }
        }
        shelf.sort((a, b) -> {
            GearDef x = game.content().gear(a.defId);
            GearDef y = game.content().gear(b.defId);
            if (x.slot != y.slot) {
                return x.slot.ordinal() - y.slot.ordinal();
            }
            return x.tier != y.tier ? x.tier - y.tier : a.instance - b.instance;
        });
        stones.clear();
        for (GemDef gem : game.content().allGems()) {
            if (p.material(gem.id) > 0) {
                stones.add(gem.id);
            }
        }
        stones.sort();
        cursor = Math.max(0, Math.min(cursor, Math.max(0, count() - 1)));
    }

    /** How many things the active page's cursor can be on. */
    private int count() {
        switch (tab) {
            case TAB_PROFILE: return Side.ALL.length;
            case TAB_GEAR: return Cell.ALL.length;
            case TAB_BAG: return shelf.size + stones.size;
            case TAB_FORGE: return recipes().size;
            case TAB_TASKS: return jobs().size;
            case TAB_FOES: return game.content().allEnemies().size;
            default: return 0;
        }
    }

    /** The forge lines this profile is allowed to see, in catalogue order. */
    private Array<CraftDef> recipes() {
        Array<CraftDef> out = new Array<>();
        Forge showing = Forge.ALL_TABS[forgeTab];
        for (CraftDef c : game.content().allCrafts()) {
            if (showing.shows(c) && ShopCatalog.requirementMet(
                    c.requirement, c.requirementValue, game.profile())) {
                out.add(c);
            }
        }
        out.sort((a, b) -> a.id.compareTo(b.id));
        return out;
    }

    // ---- input ---------------------------------------------------------------

    @Override
    protected void step() {
        if (flash > 0) {
            flash--;
        }
        if (mouse()) {
            return;
        }
        // The key that opened it closes it, which is the one thing every
        // player tries. It was the bag key, from when the bag key opened this.
        if (input().justPressed(GameAction.PAUSE)
                || input().justPressed(GameAction.SHEET)) {
            game.audio().playSfx(Assets.SFX_CANCEL);
            stack().pop();
            return;
        }
        // The two shoulder keys walk the strip, which is what they do on the
        // village shelves too. Left and right belong to the grid.
        if (input().justPressed(GameAction.ZOOM_OUT)) {
            turn(-1);
        } else if (input().justPressed(GameAction.ZOOM_IN)) {
            turn(1);
        }
        move();
        if (input().justPressed(GameAction.INTERACT)) {
            activate();
        }
    }

    private void turn(int by) {
        tab = Math.floorMod(tab + by, TABS.length);
        cursor = 0;
        game.audio().playSfx(Assets.SFX_MOVE);
    }

    /**
     * Left and right along the row, up and down by a row - and off the top or
     * bottom of the grid onto the next tab, the way {@code ShelfScreen} does.
     */
    private void move() {
        int n = count();
        int columns = columns();
        int dx = 0;
        if (input().justPressed(GameAction.MOVE_RIGHT)) {
            dx = 1;
        } else if (input().justPressed(GameAction.MOVE_LEFT)) {
            dx = -1;
        }
        // On the roster, sideways walks the six characters rather than the
        // four tabs: the tabs are stacked, so up and down is what points at
        // them, and sideways is free for whatever the open one holds.
        if (dx != 0 && tab == TAB_PROFILE && openSide() == Side.CHARS) {
            charPick = Math.floorMod(charPick + dx, Assets.Actor.CHARACTERS.length);
            game.audio().playSfx(Assets.SFX_MOVE);
            return;
        }
        // The same again for the kit, whose cells are two rows of one list:
        // the empty hand is the last of them, so sideways reaches everything.
        if (dx != 0 && tab == TAB_PROFILE && openSide() == Side.KIT) {
            kitPick = Math.floorMod(kitPick + dx, kitWeapons().size + 1);
            game.audio().playSfx(Assets.SFX_MOVE);
            return;
        }
        if (dx != 0 && n > 0) {
            cursor = Math.floorMod(cursor + dx, n);
            game.audio().playSfx(Assets.SFX_MOVE);
        }
        int dy = 0;
        if (input().justPressed(GameAction.MOVE_DOWN)) {
            dy = 1;
        } else if (input().justPressed(GameAction.MOVE_UP)) {
            dy = -1;
        }
        if (dy == 0) {
            return;
        }
        int to = cursor + dy * columns;
        if (n == 0 || to < 0 || to >= n) {
            // Off the top of the forge's browser is the row of category plates
            // above it, which is where the art puts them. Everywhere else,
            // walking off the grid turns the page.
            if (tab == TAB_FORGE && dy < 0) {
                pickForgeTab(Math.floorMod(forgeTab + 1, Forge.ALL_TABS.length));
                return;
            }
            turn(dy);
            return;
        }
        cursor = to;
        game.audio().playSfx(Assets.SFX_MOVE);
    }

    /** How wide the active page's grid is; 1 for a list. */
    private int columns() {
        switch (tab) {
            case TAB_GEAR: return EQ_COLUMNS;
            case TAB_BAG: return BAG_COLUMNS;
            case TAB_FORGE: return CRAFT_LIST_COLUMNS;
            default: return 1;
        }
    }

    private boolean mouse() {
        for (int i = 0; i < TABS.length; i++) {
            if (hit.clicked(tabX(i), TAB_Y, tabW(), TAB_H)) {
                if (i != tab) {
                    tab = i;
                    cursor = 0;
                }
                game.audio().playSfx(Assets.SFX_MOVE);
                return true;
            }
        }
        if (tab == TAB_PROFILE) {
            return profileMouse();
        }
        return tab == TAB_FORGE && forgeMouse();
    }

    /** The profile page's own targets: the three upright tabs and the roster. */
    private boolean profileMouse() {
        for (int i = 0; i < Side.ALL.length; i++) {
            if (hit.clicked(PROF_SIDE_X, sideTabY(i, BODY_TOP), PROF_SIDE_W, PROF_SIDE_H)) {
                cursor = i;
                game.audio().playSfx(Assets.SFX_MOVE);
                return true;
            }
        }
        int x = PROF_PANEL_X + 4;
        if (openSide() == Side.CHARS) {
            int y = BODY_TOP - 4 - CHAR_CELL;
            for (int i = 0; i < Assets.Actor.CHARACTERS.length; i++) {
                if (hit.clicked(x + i * CHAR_PITCH, y, CHAR_CELL, CHAR_CELL)) {
                    charPick = i;
                    activate();
                    return true;
                }
            }
            return false;
        }
        if (openSide() == Side.KIT) {
            Array<WeaponDef> kit = kitWeapons();
            int melee = 0;
            for (WeaponDef w : kit) {
                if (!w.thrown()) {
                    melee++;
                }
            }
            int topRow = BODY_TOP - 4 - KIT_CELL;
            int lowRow = topRow - KIT_PITCH - 4;
            for (int i = 0; i <= kit.size; i++) {
                boolean thrown = i >= melee;
                int column = i == kit.size ? 0
                    : thrown ? i - melee + 1 : i;
                int cx = x + column * KIT_PITCH;
                if (hit.clicked(cx, thrown ? lowRow : topRow, KIT_CELL, KIT_CELL)) {
                    kitPick = i;
                    activate();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The forge's own three targets: the category plates, the recipes in the
     * browser, and CREATE.
     *
     * <p>None of these could be clicked before. The plates did nothing at all,
     * and the button lit up under the pointer but only ever fired from the
     * keyboard - which is the worst of both, because it says it is a button.
     */
    private boolean forgeMouse() {
        TextureRegion win = game.skin().getRegion(Assets.Ui.HERO_CRAFT);
        int x = (Cfg.VIRT_W - win.getRegionWidth()) / 2;
        int y = BODY_TOP - win.getRegionHeight();

        for (int i = 0; i < Forge.ALL_TABS.length; i++) {
            boolean on = i == forgeTab;
            if (hit.clicked(x + CRAFT_TAB_X + i * CRAFT_TAB_PITCH,
                            y + CRAFT_TAB_Y + (on ? CRAFT_TAB_LIFT : 0),
                            CRAFT_TAB_W, CRAFT_TAB_H)) {
                pickForgeTab(i);
                return true;
            }
        }

        Array<CraftDef> lines = recipes();
        int perPage = CRAFT_LIST_COLUMNS * CRAFT_LIST_ROWS;
        int first = Math.min(cursor, Math.max(0, lines.size - 1)) / perPage * perPage;
        for (int i = 0; i < perPage && first + i < lines.size; i++) {
            if (hit.clicked(x + CRAFT_LIST_X + (i % CRAFT_LIST_COLUMNS) * PITCH,
                            y + CRAFT_LIST_TOP - (i / CRAFT_LIST_COLUMNS) * PITCH,
                            CELL, CELL)) {
                cursor = first + i;
                game.audio().playSfx(Assets.SFX_MOVE);
                return true;
            }
        }

        int bx = x + CRAFT_PAGE_X + (CRAFT_PAGE_W - BUTTON_W) / 2;
        if (hit.clicked(bx, y + BUTTON_Y, BUTTON_W, BUTTON_H)) {
            activate();
            return true;
        }
        return false;
    }

    private void pickForgeTab(int which) {
        if (which != forgeTab) {
            forgeTab = which;
            cursor = 0;
        }
        game.audio().playSfx(Assets.SFX_MOVE);
    }

    /**
     * Does whatever the thing under the cursor is for.
     *
     * <p>Each page has exactly one verb, which is why there is no menu here:
     * on the gear page you wear or take off, in the bag you set a stone, at the
     * forge you forge. A page with two verbs would need a second key, and the
     * profile page has none at all.
     */
    private void activate() {
        boolean done;
        switch (tab) {
            case TAB_PROFILE:
                done = openSide() == Side.KIT ? wieldPicked() : wearPicked();
                break;
            case TAB_GEAR: done = toggleWorn(); break;
            case TAB_FORGE: done = forge(); break;
            case TAB_TASKS: done = track(); break;
            default: done = false; break;
        }
        if (!done) {
            game.audio().playSfx(Assets.SFX_CANCEL);
            return;
        }
        game.audio().playSfx(Assets.SFX_ACCEPT);
        flash = FLASH_STEPS;
        restock();
        // Saved on every change, like the village shelves: a forge that lost a
        // sword to a crash would be worse than one that never worked.
        game.saves().save(game.profile());
    }

    // ---- the two things this screen actually does ----------------------------

    /**
     * Wears the best unworn piece for the slot under the cursor, or takes off
     * what is in it.
     *
     * <p>One key for both, because a slot is either full or empty and the
     * player can see which. "Best" is the highest tier the profile owns and is
     * not already wearing - there is no case where a player means to wear the
     * worse of two identical helmets, and offering the choice would cost a
     * second list on a screen that has no room for one.
     */
    /**
     * Puts the picked character on, if the player owns them.
     *
     * <p>Written straight into the run: there is nothing here to confirm, so
     * there is nothing to lose by leaving. This is the only door onto the
     * choice now - there was a second screen in the village that made it too,
     * and the two could show different answers.
     */
    private boolean wearPicked() {
        if (openSide() != Side.CHARS) {
            return false;
        }
        RunState run = game.run();
        String id = Assets.Actor.CHARACTERS[charPick];
        if (run == null || !game.profile().unlockedCharacters.contains(id)) {
            return false;
        }
        if (id.equals(run.characterId)) {
            return false;
        }
        run.characterId = id;
        return true;
    }

    private boolean toggleWorn() {
        GearDef.Slot slot = Cell.ALL[Math.min(cursor, Cell.ALL.length - 1)].slot();
        if (slot == null) {
            // A weapon cell. Shown here so the kit reads as one thing, but
            // changed on the sheet's own character tab, where the choice sits
            // beside the character it belongs to.
            return false;
        }
        Profile p = game.profile();
        if (p.worn(slot.name()) != null) {
            p.equipped.remove(slot.name());
            return true;
        }
        OwnedGear best = null;
        int bestTier = 0;
        for (OwnedGear g : shelf) {
            GearDef def = game.content().gear(g.defId);
            if (def.slot != slot || isWorn(g)) {
                continue;
            }
            if (def.tier > bestTier) {
                bestTier = def.tier;
                best = g;
            }
        }
        if (best == null) {
            return false;
        }
        p.equipped.put(slot.name(), best.instance);
        return true;
    }

    private boolean isWorn(OwnedGear g) {
        for (Integer instance : game.profile().equipped.values()) {
            if (instance != null && instance == g.instance) {
                return true;
            }
        }
        return false;
    }

    /**
     * Spends the materials and gold a forge line asks for, and hands over what
     * it makes.
     *
     * <p>Checked and then spent in two passes, so a line that turns out to be
     * one stone short cannot have already eaten the other four. Gear comes out
     * as a fresh instance with empty sockets; stones are counted.
     */
    private boolean forge() {
        Array<CraftDef> lines = recipes();
        if (cursor < 0 || cursor >= lines.size) {
            return false;
        }
        CraftDef line = lines.get(cursor);
        Profile p = game.profile();
        if (!canForge(line)) {
            return false;
        }
        for (int i = 0; i < line.inputs.length; i++) {
            spend(line.inputs[i], line.counts[i]);
        }
        p.gold -= line.goldCost;
        if (line.kind == CraftDef.Output.GEM) {
            p.addMaterial(line.output, 1);
        } else {
            GearDef made = game.content().gear(line.output);
            p.stash.add(new OwnedGear(p.nextGearId(), made.id, made.sockets));
        }
        return true;
    }

    /** Whether every input is in hand and the gold is there. */
    private boolean canForge(CraftDef line) {
        if (game.profile().gold < line.goldCost) {
            return false;
        }
        for (int i = 0; i < line.inputs.length; i++) {
            if (held(line.inputs[i]) < line.counts[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * How many of an input the player has.
     *
     * <p>An input is a stone, an item or a piece of gear, and the three are
     * counted differently: the first two are counts, and gear is instances -
     * of which only the unworn ones can go in the pot. Melting the helmet off
     * the player's own head would be a forge that punished not reading.
     */
    private int held(String id) {
        Profile p = game.profile();
        if (game.content().hasGear(id)) {
            int n = 0;
            for (OwnedGear g : shelf) {
                if (g.defId.equals(id) && !isWorn(g)) {
                    n++;
                }
            }
            return n;
        }
        return p.material(id);
    }

    private void spend(String id, int count) {
        Profile p = game.profile();
        if (!game.content().hasGear(id)) {
            p.addMaterial(id, -count);
            return;
        }
        for (int i = p.stash.size - 1; i >= 0 && count > 0; i--) {
            OwnedGear g = p.stash.get(i);
            if (g.defId.equals(id) && !isWorn(g)) {
                p.stash.removeIndex(i);
                count--;
            }
        }
    }

    private StatSheet stats() {
        return StatSheet.of(game.run(), game.content(),
            Loadout.of(game.run(), game.content(), game.shop(), game.profile()),
            game.run() == null ? Screens.DEFAULT_MAX_HP : game.run().baseMaxHp);
    }
}
