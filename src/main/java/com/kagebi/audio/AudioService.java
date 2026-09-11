package com.kagebi.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.kagebi.settings.Settings;

/**
 * The only place in the game that plays a sound.
 *
 * <p>Routing everything through here is what makes a volume slider a three-line
 * feature. Scatter {@code Sound.play} calls across forty files and it becomes a
 * refactor across forty files.
 *
 * <p>It also owns the rule about which files stream. libGDX's {@link Sound}
 * decodes the whole file into memory, which is fine for a 110 KB hit sound and
 * wrong for a 4.6 MB looping wind bed - those are {@link Music} regardless of
 * format.
 */
public final class AudioService implements Settings.Listener, Disposable {

    /** Files at or above this size are streamed rather than held in memory. */
    private static final long STREAM_THRESHOLD_BYTES = 1_000_000L;

    /** Seconds for a music crossfade. */
    private static final float FADE = 1.2f;

    private final Settings settings;
    private final ObjectMap<String, Sound> sounds = new ObjectMap<>();
    private final ObjectMap<String, Music> ambience = new ObjectMap<>();

    private Music current;
    private Music previous;
    private String currentPath;
    private float fade = 1f;

    public AudioService(Settings settings) {
        this.settings = settings;
        settings.addListener(this);
    }

    // ---- music -----------------------------------------------------------

    /** Crossfades to a track. Calling it with the track already playing is a no-op. */
    public void playMusic(String path) {
        if (path.equals(currentPath)) {
            return;
        }
        if (!Gdx.files.internal(path).exists()) {
            Gdx.app.error("audio", "music not found: " + path);
            return;
        }
        if (previous != null) {
            previous.dispose();
        }
        previous = current;
        currentPath = path;
        current = Gdx.audio.newMusic(Gdx.files.internal(path));
        current.setLooping(true);
        current.setVolume(0f);
        current.play();
        fade = 0f;
    }

    public void stopMusic() {
        if (current != null) {
            current.stop();
            current.dispose();
            current = null;
        }
        currentPath = null;
    }

    /** Drives the crossfade. Call once per frame with the real frame delta. */
    public void update(float delta) {
        if (current == null) {
            return;
        }
        if (fade < 1f) {
            fade = Math.min(1f, fade + delta / FADE);
            if (previous != null) {
                previous.setVolume(musicVolume() * (1f - fade));
                if (fade >= 1f) {
                    previous.stop();
                    previous.dispose();
                    previous = null;
                }
            }
        }
        current.setVolume(musicVolume() * fade);
    }

    // ---- sound effects ---------------------------------------------------

    /**
     * Plays a one-shot effect. {@code pitchVariation} of 0.1 spreads the pitch
     * by +/-10%, which stops a repeated sound - footsteps, sword hits - from
     * turning into a machine gun.
     */
    public void playSfx(String path, float pitchVariation) {
        Sound sound = sounds.get(path);
        if (sound == null) {
            if (!Gdx.files.internal(path).exists()) {
                Gdx.app.error("audio", "sfx not found: " + path);
                return;
            }
            sound = Gdx.audio.newSound(Gdx.files.internal(path));
            sounds.put(path, sound);
        }
        float pitch = pitchVariation <= 0f ? 1f
                : MathUtils.random(1f - pitchVariation, 1f + pitchVariation);
        sound.play(sfxVolume(), pitch, 0f);
    }

    public void playSfx(String path) {
        playSfx(path, 0.06f);
    }

    /**
     * Starts a looping ambient bed. These are the large files, so they stream
     * through {@link Music} rather than being decoded into memory.
     */
    public void playAmbience(String path) {
        if (ambience.containsKey(path)) {
            return;
        }
        if (!Gdx.files.internal(path).exists()) {
            Gdx.app.error("audio", "ambience not found: " + path);
            return;
        }
        Music bed = Gdx.audio.newMusic(Gdx.files.internal(path));
        bed.setLooping(true);
        bed.setVolume(sfxVolume());
        bed.play();
        ambience.put(path, bed);
    }

    public void stopAmbience() {
        for (Music bed : ambience.values()) {
            bed.stop();
            bed.dispose();
        }
        ambience.clear();
    }

    /** True if this file is big enough that it must stream rather than preload. */
    public static boolean shouldStream(String path) {
        return Gdx.files.internal(path).length() >= STREAM_THRESHOLD_BYTES;
    }

    // ---- volumes ---------------------------------------------------------

    private float musicVolume() {
        return settings.master() * settings.music();
    }

    private float sfxVolume() {
        return settings.master() * settings.sfx();
    }

    @Override
    public void onSettingsChanged(Settings s) {
        if (current != null && fade >= 1f) {
            current.setVolume(musicVolume());
        }
        for (Music bed : ambience.values()) {
            bed.setVolume(sfxVolume());
        }
    }

    @Override
    public void dispose() {
        stopMusic();
        stopAmbience();
        if (previous != null) {
            previous.dispose();
        }
        for (Sound s : sounds.values()) {
            s.dispose();
        }
        sounds.clear();
    }
}
