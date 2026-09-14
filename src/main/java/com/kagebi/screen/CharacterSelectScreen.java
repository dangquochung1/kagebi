package com.kagebi.screen;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.kagebi.Cfg;
import com.kagebi.Dir;
import com.kagebi.Kagebi;
import com.kagebi.assets.Assets;
import com.kagebi.data.def.WeaponDef;
import com.kagebi.gfx.Anim;
import com.kagebi.gfx.CameraController;
import com.kagebi.input.GameAction;
import com.kagebi.run.RunState;
import com.kagebi.ui.Hud;
import com.kagebi.ui.I18n;

/**
 * Six ninjas, one weapon, and either the start of a run or a change of clothes.
 *
 * <p><b>Two modes, one screen.</b> It opened the game for most of this
 * project's life: New Game showed it, you picked, and it built the run. That
 * was a wall in front of a fresh profile, which owns exactly one ninja and one
 * sword and therefore has nothing to choose - and it was the only way to change
 * an off hand, so buying a kunai meant quitting to the menu and starting over.
 * {@link #asLoadout()} is the same screen editing the run the player already
 * has, reached from the village whenever they want it.
 *
 * <p>Every portrait runs its own idle animation rather than showing frame zero.
 * A still sprite sheet frame is the character; a breathing one is a character
 * who is waiting for you, and it costs one integer per row to get.
 *
 * <p><b>Locked characters are drawn, darkened.</b> Hiding them would make the
 * screen honest and the progression invisible: a player who cannot see the
 * other five has no reason to believe there are any. Darkening states the rule
 * - this is real, and it is not yours yet - without a word of explanation.
 */
public class CharacterSelectScreen extends SimScreen {

    /** Cell for one portrait. 32px of sprite with three pixels of air. */
    private static final int CELL = 38;
    private static final int GAP = 6;
    private static final int WEAPON_CELL = 20;
    private static final int WEAPON_GAP = 4;
    /**
     * Space before the off-hand cell, so it reads as its own slot - and wide
     * enough for the key cap that cycles it to sit in the gap.
     *
     * <p>The cap used to float above the cell, where it landed on the second
     * wrapped line of the perk description and covered a word of it. Invisible
     * for as long as this screen was something a player saw once at the start
     * of a run; the village badge opens it whenever they like now.
     */
    private static final int OFF_HAND_GAP = 24;

    // The column, top to bottom. Line tops include the four rows above cap
    // height that a stacked Vietnamese tone mark needs; see Hud.line.
    private static final int TITLE_TOP = 169;
    private static final int PORTRAIT_BOTTOM = 114;
    private static final int NAME_TOP = 109;
    /** The perk block, inside the panel's own border on both sides. */
    private static final int PERK_X = 14;
    private static final int PERK_WIDTH = Cfg.VIRT_W - 2 * PERK_X;
    private static final int WEAPON_BOTTOM = 56;
    /** Above the panel's bottom border, which is seven pixels of art. */
    private static final int FOOTER_BOTTOM = 16;

    /** Down, right, up, left: a quarter turn clockwise, as seen from above. */
    private static final Dir[] TURN = {Dir.DOWN, Dir.RIGHT, Dir.UP, Dir.LEFT};
    /** Long enough on each side to read it; a full turn is four seconds. */
    private static final int TURN_STEPS = 60;

    /**
     * Twelve steps a frame, so the idle cycle takes eight tenths of a second.
     * Faster reads as fidgeting when six of them are side by side.
     */
    private static final int IDLE_STEPS_PER_FRAME = 12;

    /**
     * The weapons the pack ships art for, in the order they unlock. Replaced by
     * {@code ContentRegistry.allWeapons()} the moment there is any content; the
     * list is here so that the screen has something to show before there is.
     */
    static final String[] FALLBACK_WEAPONS = {"katana", "axe", "hammer", "pickaxe"};

    private static final Color INK = new Color(0x131b1bff);
    private static final Color SOFT = new Color(0x46503cff);
    private static final Color LOCKED = new Color(0.32f, 0.30f, 0.36f, 1f);
    private static final Color DANGER = new Color(0xd94a3aff);

    private final Kagebi game;
    private final CameraController camera = new CameraController();

    private BitmapFont font;
    private TextureAtlas actors;
    private Anim[] idle;
    private final Array<String> weapons = new Array<>();
    /** Thrown weapons the profile owns, with a null at index 0 for "none". */
    private final Array<String> throwables = new Array<>();
    private int throwable;

    private int character;
    private int weapon;

    /** Editing the run that exists, rather than building a new one. */
    private boolean loadout;

    public CharacterSelectScreen(Kagebi game, int startIndex) {
        super(game.input());
        this.game = game;
        this.character = Math.max(0, Math.min(startIndex, Assets.Actor.CHARACTERS.length - 1));
    }

    /**
     * Opens on what the player is currently carrying, and saves back into it.
     *
     * <p>The distinction that matters is {@link Screens#freshRun}: starting a
     * run builds a new {@link RunState}, and doing that from the village would
     * throw away the seed, the health and anything else the run is holding for
     * a reason that amounts to the player looking at their own sword.
     */
    CharacterSelectScreen asLoadout() {
        loadout = true;
        return this;
    }

    @Override
    public InputProcessor inputProcessor() {
        return game.input();
    }

    @Override
    public void show() {
        if (idle != null) {
            return;
        }
        font = game.skin().getFont("default");
        actors = Preload.actors();
        idle = new Anim[Assets.Actor.CHARACTERS.length];
        for (int i = 0; i < idle.length; i++) {
            idle[i] = Anim.directional(actors,
                Assets.Actor.player(Assets.Actor.CHARACTERS[i], "idle"),
                32, IDLE_STEPS_PER_FRAME, true);
        }
        collectWeapons();
        camera.snapTo(Cfg.VIRT_W / 2f, Cfg.VIRT_H / 2f);
        if (!loadout) {
            // The village's own music keeps playing underneath a loadout: this
            // is a glance at a rack, not a place the player has travelled to.
            game.audio().playMusic(Assets.MUSIC_INTRO);
        }
    }

    /**
     * Content first, the pack's own five second, so an empty registry still works.
     *
     * <p>Split in two, which is the whole of the off-hand feature. A thrown
     * weapon used to sit in this one list beside the swords, so choosing a
     * kunai meant giving up melee for the whole run - and the throw key did
     * nothing whatever you picked. Now the main hand is melee, the off hand is
     * thrown, and the off hand is empty until one has been bought.
     */
    private void collectWeapons() {
        weapons.clear();
        throwables.clear();
        for (WeaponDef def : game.content().allWeapons()) {
            if (def.thrown()) {
                if (game.profile().unlockedWeapons.contains(def.id)) {
                    throwables.add(def.id);
                }
            } else {
                weapons.add(def.id);
            }
        }
        if (weapons.isEmpty()) {
            for (String id : FALLBACK_WEAPONS) {
                weapons.add(id);
            }
        }
        weapon = Math.max(0, weapons.indexOf(currentUnlockedWeapon(), false));
        // Index 0 is always "nothing in the off hand", so a player who owns a
        // kunai may still choose to run without one.
        throwables.insert(0, null);
        throwable = 0;
        if (loadout) {
            startFromWhatIsWorn();
        }
    }

    /** A loadout opens on the current kit, not on the first thing in each list. */
    private void startFromWhatIsWorn() {
        RunState run = game.run();
        if (run == null) {
            return;
        }
        int worn = Assets.Actor.indexOf(run.characterId);
        if (worn >= 0) {
            character = worn;
        }
        int held = weapons.indexOf(run.weaponId, false);
        if (held >= 0) {
            weapon = held;
        }
        int off = throwables.indexOf(run.throwWeaponId, false);
        if (off >= 0) {
            throwable = off;
        }
    }

    private String currentUnlockedWeapon() {
        for (String id : weapons) {
            if (game.profile().unlockedWeapons.contains(id)) {
                return id;
            }
        }
        return weapons.isEmpty() ? null : weapons.first();
    }

    @Override
    protected void step() {
        int dx = 0;
        if (input().justPressed(GameAction.MOVE_RIGHT)) {
            dx = 1;
        } else if (input().justPressed(GameAction.MOVE_LEFT)) {
            dx = -1;
        }
        if (dx != 0) {
            character = Math.floorMod(character + dx, Assets.Actor.CHARACTERS.length);
            game.audio().playSfx(Assets.SFX_MOVE);
        }

        int dy = 0;
        if (input().justPressed(GameAction.MOVE_UP)) {
            dy = -1;
        } else if (input().justPressed(GameAction.MOVE_DOWN)) {
            dy = 1;
        }
        if (dy != 0 && weapons.size > 0) {
            weapon = Math.floorMod(weapon + dy, weapons.size);
            game.audio().playSfx(Assets.SFX_MOVE);
        }

        // The throw key picks what it will throw. Cycling the off hand with the
        // button it is used with beats a fourth direction to remember, and it
        // is the only key on this screen that is otherwise idle.
        if (input().justPressed(GameAction.THROW) && throwables.size > 1) {
            throwable = Math.floorMod(throwable + 1, throwables.size);
            game.audio().playSfx(Assets.SFX_MOVE);
        }

        if (input().justPressed(GameAction.PAUSE)
                || (loadout && input().justPressed(GameAction.INVENTORY))) {
            game.audio().playSfx(Assets.SFX_CANCEL);
            if (loadout) {
                stack().pop();
            } else {
                stack().set(new MainMenuScreen(game));
            }
            return;
        }
        if (input().justPressed(GameAction.INTERACT) || input().justPressed(GameAction.ATTACK)) {
            confirm();
        }
    }

    private void confirm() {
        if (!unlockedCharacter(character) || !unlockedWeapon(weapon)) {
            game.audio().playSfx(Assets.SFX_CANCEL);
            return;
        }
        game.audio().playSfx(Assets.SFX_ACCEPT);
        if (loadout && game.run() != null) {
            RunState run = game.run();
            run.characterId = Assets.Actor.CHARACTERS[character];
            run.weaponId = weapons.get(weapon);
            run.throwWeaponId = throwables.get(throwable);
            stack().pop();
            return;
        }
        RunState run = Screens.freshRun(game, Assets.Actor.CHARACTERS[character],
                                        weapons.get(weapon), Screens.DEFAULT_MAX_HP);
        run.throwWeaponId = throwables.get(throwable);
        game.setRun(run);
        stack().set(new HubScreen(game));
    }

    private boolean unlockedCharacter(int index) {
        return game.profile().unlockedCharacters.contains(Assets.Actor.CHARACTERS[index]);
    }

    private boolean unlockedWeapon(int index) {
        return index >= 0 && index < weapons.size
            && game.profile().unlockedWeapons.contains(weapons.get(index));
    }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.05f, 0.04f, 0.07f, 1f);
        camera.apply();
        SpriteBatch batch = game.batch();
        batch.setProjectionMatrix(camera.camera().combined);
        batch.begin();

        game.skin().getDrawable(Assets.Ui.PANEL_2).draw(batch, 3, 3, 314, 174);
        I18n t = game.i18n();

        batch.setColor(INK);
        Hud.centred(batch, font, t.get(loadout ? "select.loadout" : "select.title"),
                    Cfg.VIRT_W / 2f, TITLE_TOP);
        batch.setColor(Color.WHITE);

        drawPortraits(batch);
        drawDetails(batch, t);
        drawWeapons(batch, t);
        drawFooter(batch, t);

        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void drawPortraits(SpriteBatch batch) {
        int count = Assets.Actor.CHARACTERS.length;
        int total = count * CELL + (count - 1) * GAP;
        int left = (Cfg.VIRT_W - total) / 2;
        for (int i = 0; i < count; i++) {
            int x = left + i * (CELL + GAP);
            game.skin().getDrawable(Assets.Ui.CELL).draw(batch, x, PORTRAIT_BOTTOM, CELL, CELL);
            // The selected ninja turns slowly on the spot, showing all four
            // sides of the sheet; the rest face the camera. Motion is what the
            // eye goes to, so this is the selection cue as much as the ring is.
            Dir facing = i == character ? TURN[(steps() / TURN_STEPS) % TURN.length] : Dir.DOWN;
            TextureRegion frame = idle[i].frame(facing, steps());
            // Darkened rather than hidden: the player should be able to see
            // exactly what is still ahead of them.
            batch.setColor(unlockedCharacter(i) ? Color.WHITE : LOCKED);
            batch.draw(frame, x + (CELL - 32) / 2, PORTRAIT_BOTTOM + (CELL - 32) / 2);
            batch.setColor(Color.WHITE);
            if (i == character) {
                game.skin().getDrawable(Assets.Ui.FOCUS)
                    .draw(batch, x - 2, PORTRAIT_BOTTOM - 2, CELL + 4, CELL + 4);
            }
        }
    }

    private void drawDetails(SpriteBatch batch, I18n t) {
        String id = Assets.Actor.CHARACTERS[character];
        batch.setColor(unlockedCharacter(character) ? INK : SOFT);
        Hud.centred(batch, font, t.get("char." + id + ".name"), Cfg.VIRT_W / 2f, NAME_TOP);
        // The perk line comes from the same file the perk itself does. It used
        // to come from "char.<id>.passive", a second set of strings written
        // beside these names - and five of the six described a perk the game
        // has never had. A locked ninja's line is the one the shop shows, which
        // says what it costs to find out.
        //
        // Wrapped, not centred on one line. These sentences were written for
        // the shop's wrapped block and are longer than a name; drawn as a
        // single centred line the longest of them ran off both edges of the
        // screen. Two lines fit between the name and the weapon row, and
        // wrapping is also the only thing that stays right when the language
        // changes - Vietnamese runs longer than English for the same sentence.
        batch.setColor(Color.WHITE);
        font.setColor(SOFT);
        font.draw(batch, t.get("character." + id + ".desc"), PERK_X,
                  NAME_TOP - Hud.LINE - font.getAscent(), PERK_WIDTH, Align.center, true);
        font.setColor(Color.WHITE);
    }

    private void drawWeapons(SpriteBatch batch, I18n t) {
        // The off hand rides on the end of the same row, behind a wider gap so
        // it reads as a separate slot rather than a sixth sword.
        int cells = weapons.size + 1;
        int total = cells * WEAPON_CELL + Math.max(0, cells - 1) * WEAPON_GAP + OFF_HAND_GAP;
        int left = (Cfg.VIRT_W - total) / 2;
        int offHandX = left + weapons.size * (WEAPON_CELL + WEAPON_GAP) + OFF_HAND_GAP;
        drawOffHand(batch, offHandX);
        for (int i = 0; i < weapons.size; i++) {
            int x = left + i * (WEAPON_CELL + WEAPON_GAP);
            game.skin().getDrawable(Assets.Ui.CELL)
                .draw(batch, x, WEAPON_BOTTOM, WEAPON_CELL, WEAPON_CELL);
            TextureRegion icon = game.skin().getRegion(Assets.Ui.weaponIcon(weapons.get(i)));
            batch.setColor(unlockedWeapon(i) ? Color.WHITE : LOCKED);
            batch.draw(icon,
                x + (WEAPON_CELL - icon.getRegionWidth()) / 2,
                WEAPON_BOTTOM + (WEAPON_CELL - icon.getRegionHeight()) / 2);
            batch.setColor(Color.WHITE);
            if (i == weapon) {
                game.skin().getDrawable(Assets.Ui.FOCUS)
                    .draw(batch, x - 2, WEAPON_BOTTOM - 2, WEAPON_CELL + 4, WEAPON_CELL + 4);
            }
        }
        if (weapons.size > 0) {
            // One line, label and name together: a separate heading above the
            // icons cost fourteen pixels the footer needed. The off hand shares
            // it for the same reason - there is no room for a second heading.
            String offHand = throwables.get(throwable);
            // The off hand is named even when it is empty. An unexplained empty
            // cell on the end of the row is a question; "Throwing weapon: none"
            // is an answer, and it is the one that sends a player to the shop.
            String line = t.get(loadout ? "select.mainhand" : "select.weapon") + ": "
                + t.get("select.weapon." + weapons.get(weapon))
                + "   " + t.get("select.throw") + ": " + (offHand == null
                    ? t.get("select.throw.none") : t.get("select.weapon." + offHand));
            batch.setColor(SOFT);
            Hud.centred(batch, font, line, Cfg.VIRT_W / 2f, WEAPON_BOTTOM - 4);
            batch.setColor(Color.WHITE);
        }
    }

    /**
     * The off-hand slot: a throwing weapon, or an empty cell with a hint.
     *
     * <p>Drawn even when the player owns nothing throwable. An empty slot says
     * the game has a second attack and this run does not have it yet, which is
     * the whole reason to go and buy a kunai; hiding it would make the feature
     * invisible to exactly the players who have not found it.
     */
    private void drawOffHand(SpriteBatch batch, int x) {
        game.skin().getDrawable(Assets.Ui.CELL)
            .draw(batch, x, WEAPON_BOTTOM, WEAPON_CELL, WEAPON_CELL);
        String id = throwables.get(throwable);
        if (id != null) {
            TextureRegion icon = game.skin().getRegion(Assets.Ui.weaponIcon(id));
            batch.draw(icon,
                x + (WEAPON_CELL - icon.getRegionWidth()) / 2,
                WEAPON_BOTTOM + (WEAPON_CELL - icon.getRegionHeight()) / 2);
        }
        if (throwables.size > 1) {
            Hud.prompt(batch, game.skin(), font,
                game.input().map().primary(GameAction.THROW), "",
                x - OFF_HAND_GAP / 2f, WEAPON_BOTTOM + 4);
        }
    }

    private void drawFooter(SpriteBatch batch, I18n t) {
        boolean ready = unlockedCharacter(character) && unlockedWeapon(weapon);
        if (!ready) {
            batch.setColor(DANGER);
            Hud.centred(batch, font, t.get("common.locked"), Cfg.VIRT_W / 2f, FOOTER_BOTTOM + Hud.LINE);
            batch.setColor(Color.WHITE);
            return;
        }
        Hud.prompt(batch, game.skin(), font,
                   game.input().map().primary(GameAction.INTERACT),
                   t.get(loadout ? "select.done" : "select.start"),
                   Cfg.VIRT_W / 2f, FOOTER_BOTTOM);
    }

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
    }
}
