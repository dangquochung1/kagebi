package com.kagebi.settings;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.Array;
import com.kagebi.ui.I18n;

/**
 * Player preferences, backed by libGDX {@link Preferences}.
 *
 * <p>Volumes live here rather than on the audio service so that the settings
 * screen has one thing to talk to, and so a value survives a restart without
 * anyone writing serialisation code for it.
 */
public final class Settings {

    private static final String FILE = "kagebi-settings";

    public interface Listener {
        void onSettingsChanged(Settings settings);
    }

    private final Preferences prefs;
    private final Array<Listener> listeners = new Array<>();

    private float master = 0.8f;
    private float music = 0.7f;
    private float sfx = 0.9f;
    private boolean fullscreen;
    private boolean vsync = true;
    private boolean screenShake = true;
    private I18n.Language language = I18n.Language.VI;
    private Difficulty difficulty = Difficulty.DEFAULT;

    public Settings() {
        prefs = Gdx.app.getPreferences(FILE);
        master = prefs.getFloat("audio.master", master);
        music = prefs.getFloat("audio.music", music);
        sfx = prefs.getFloat("audio.sfx", sfx);
        fullscreen = prefs.getBoolean("video.fullscreen", fullscreen);
        vsync = prefs.getBoolean("video.vsync", vsync);
        screenShake = prefs.getBoolean("game.screenShake", screenShake);
        language = I18n.Language.fromCode(prefs.getString("game.language", language.code));
        difficulty = Difficulty.fromName(prefs.getString("game.difficulty", difficulty.name()));
        // Where Preferences actually lands is backend-specific and worth
        // knowing for real rather than trusting the documentation.
        Gdx.app.log("settings", "preferences file: " + FILE + " (backend-resolved)");
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    private void changed() {
        for (Listener l : listeners) {
            l.onSettingsChanged(this);
        }
    }

    public void save() {
        prefs.putFloat("audio.master", master);
        prefs.putFloat("audio.music", music);
        prefs.putFloat("audio.sfx", sfx);
        prefs.putBoolean("video.fullscreen", fullscreen);
        prefs.putBoolean("video.vsync", vsync);
        prefs.putBoolean("game.screenShake", screenShake);
        prefs.putString("game.language", language.code);
        prefs.putString("game.difficulty", difficulty.name());
        prefs.flush();
    }

    public float master() {
        return master;
    }

    public float music() {
        return music;
    }

    public float sfx() {
        return sfx;
    }

    public boolean fullscreen() {
        return fullscreen;
    }

    public boolean vsync() {
        return vsync;
    }

    public boolean screenShake() {
        return screenShake;
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    /**
     * Changes the setting. A run in progress is not rewritten: the choice is
     * copied into the RunState when the run starts, so tabbing into settings
     * mid-descent cannot make the floor you are standing on easier.
     */
    public void setDifficulty(Difficulty v) {
        difficulty = v;
        changed();
    }

    public I18n.Language language() {
        return language;
    }

    public void setMaster(float v) {
        master = clamp(v);
        changed();
    }

    public void setMusic(float v) {
        music = clamp(v);
        changed();
    }

    public void setSfx(float v) {
        sfx = clamp(v);
        changed();
    }

    public void setFullscreen(boolean v) {
        fullscreen = v;
        changed();
    }

    public void setVsync(boolean v) {
        vsync = v;
        Gdx.graphics.setVSync(v);
        changed();
    }

    public void setScreenShake(boolean v) {
        screenShake = v;
        changed();
    }

    public void setLanguage(I18n.Language v) {
        language = v;
        changed();
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
