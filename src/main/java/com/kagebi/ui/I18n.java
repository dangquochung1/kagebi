package com.kagebi.ui;

import java.text.Normalizer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;
import com.kagebi.assets.Assets;

/**
 * Interface strings, loaded from {@code assets/i18n/<lang>.json}.
 *
 * <p>No user-visible string is written as a literal anywhere else. Retrofitting
 * that later means touching every screen, and the point of doing it now is that
 * switching language is a menu option rather than a rebuild.
 */
public final class I18n {

    /** Languages the game ships. The label is deliberately in its own language. */
    public enum Language {
        VI("vi", "Tiếng Việt"),
        EN("en", "English");

        public final String code;
        public final String label;

        Language(String code, String label) {
            this.code = code;
            this.label = label;
        }

        public static Language fromCode(String code) {
            for (Language l : values()) {
                if (l.code.equals(code)) {
                    return l;
                }
            }
            return VI;
        }
    }

    private final ObjectMap<String, String> strings = new ObjectMap<>();
    private Language language = Language.VI;

    public I18n(Language language) {
        load(language);
    }

    public Language language() {
        return language;
    }

    /**
     * Interface strings and content strings live in separate files.
     *
     * <p>Screen labels and the names of four hundred enemies, relics and items
     * are written by different people at different times, and keeping them
     * apart means neither has to open the other's file. Content is loaded
     * second, so a key defined in both resolves to the content one.
     */
    private static final String[] FILES = {"%s.json", "content.%s.json"};

    public void load(Language lang) {
        this.language = lang;
        strings.clear();
        for (String pattern : FILES) {
            FileHandle file = Gdx.files.internal(
                Assets.I18N_DIR + String.format(pattern, lang.code));
            if (!file.exists()) {
                continue;       // content may not be written yet
            }
            JsonValue root = new JsonReader().parse(file);
            for (JsonValue entry = root.child; entry != null; entry = entry.next) {
                // The font carries precomposed Vietnamese letters, not combining
                // marks, so a decomposed string would render its accents as
                // missing glyphs. Normalising on load means a translator's
                // editor settings can never cause that.
                strings.put(entry.name,
                    Normalizer.normalize(entry.asString(), Normalizer.Form.NFC));
            }
        }
    }

    /**
     * The string for a key, or the key itself if it is missing. Showing the raw
     * key makes a gap obvious on screen without crashing a running game.
     */
    public String get(String key) {
        String value = strings.get(key);
        return value != null ? value : key;
    }

    /** As {@link #get}, substituting {0}, {1}, ... with the given arguments. */
    public String format(String key, Object... args) {
        String value = get(key);
        for (int i = 0; i < args.length; i++) {
            value = value.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return value;
    }

    public boolean has(String key) {
        return strings.containsKey(key);
    }

    public int size() {
        return strings.size;
    }
}
