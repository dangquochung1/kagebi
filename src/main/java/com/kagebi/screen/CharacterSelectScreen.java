package com.kagebi.screen;

import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
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
import com.kagebi.ui.Hud;
import com.kagebi.ui.I18n;

/**
 * Six ninjas, one weapon, and the start of a run.
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

    // The column, top to bottom. Line tops include the four rows above cap
    // height that a stacked Vietnamese tone mark needs; see Hud.line.
    private static final int TITLE_TOP = 169;
    private static final int PORTRAIT_BOTTOM = 114;
    private static final int NAME_TOP = 109;
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

    private int character;
    private int weapon;

    public CharacterSelectScreen(Kagebi game, int startIndex) {
        super(game.input());
        this.game = game;
        this.character = Math.max(0, Math.min(startIndex, Assets.Actor.CHARACTERS.length - 1));
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
        game.audio().playMusic(Assets.MUSIC_INTRO);
    }

    /** Content first, the pack's own five second, so an empty registry still works. */
    private void collectWeapons() {
        weapons.clear();
        for (WeaponDef def : game.content().allWeapons()) {
            weapons.add(def.id);
        }
        if (weapons.isEmpty()) {
            for (String id : FALLBACK_WEAPONS) {
                weapons.add(id);
            }
        }
        weapon = Math.max(0, weapons.indexOf(currentUnlockedWeapon(), false));
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

        if (input().justPressed(GameAction.PAUSE)) {
            game.audio().playSfx(Assets.SFX_CANCEL);
            stack().set(new MainMenuScreen(game));
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
        game.setRun(Screens.freshRun(Assets.Actor.CHARACTERS[character],
                                     weapons.get(weapon), Screens.DEFAULT_MAX_HP));
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
        Hud.centred(batch, font, t.get("select.title"), Cfg.VIRT_W / 2f, TITLE_TOP);
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
        batch.setColor(SOFT);
        Hud.centred(batch, font,
            unlockedCharacter(character) ? t.get("char." + id + ".passive")
                                         : t.get("select.locked_hint"),
            Cfg.VIRT_W / 2f, NAME_TOP - Hud.LINE);
        batch.setColor(Color.WHITE);
    }

    private void drawWeapons(SpriteBatch batch, I18n t) {
        int total = weapons.size * WEAPON_CELL + Math.max(0, weapons.size - 1) * WEAPON_GAP;
        int left = (Cfg.VIRT_W - total) / 2;
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
            // icons cost fourteen pixels the footer needed.
            batch.setColor(SOFT);
            Hud.centred(batch, font, t.get("select.weapon") + ": "
                        + t.get("select.weapon." + weapons.get(weapon)),
                        Cfg.VIRT_W / 2f, WEAPON_BOTTOM - 4);
            batch.setColor(Color.WHITE);
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
                   t.get("select.start"), Cfg.VIRT_W / 2f, FOOTER_BOTTOM);
    }

    @Override
    public void resize(int width, int height) {
        camera.resize(width, height);
    }
}
