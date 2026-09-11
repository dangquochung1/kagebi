package com.kagebi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

/**
 * Checks on the data files that no amount of playing would reliably catch.
 *
 * <p>All of it runs off plain files, so no GL context and no headless
 * application are needed - {@code JsonReader} and the {@code .fnt} text format
 * are both readable with a {@link FileHandle} over a real {@link File}.
 */
class ContentContractTest {

    // ---- i18n ------------------------------------------------------------

    /**
     * Interface strings and content strings are separate files, so that the
     * person naming the screens and the person naming four hundred items never
     * have to open the same file. Both are checked here as one set: from a
     * player's point of view a missing key is a missing key.
     */
    private static Map<String, String> readStrings(String lang) {
        Map<String, String> out = new HashMap<>();
        boolean any = false;
        for (String pattern : new String[] {"%s.json", "content.%s.json"}) {
            File file = new File("assets/i18n/" + String.format(pattern, lang));
            if (!file.isFile()) {
                continue;
            }
            any = true;
            JsonValue root = new JsonReader().parse(new FileHandle(file));
            for (JsonValue e = root.child; e != null; e = e.next) {
                out.put(e.name, e.asString());
            }
        }
        assertTrue(any, "no translation file for " + lang);
        return out;
    }

    /** A key present in one language and absent in the other shows up on screen
     *  as the raw key, which is the kind of thing nobody notices until a
     *  player screenshots it. */
    @Test
    void languagesDefineTheSameKeys() {
        Map<String, String> vi = readStrings("vi");
        Map<String, String> en = readStrings("en");

        Set<String> onlyVi = new LinkedHashSet<>(vi.keySet());
        onlyVi.removeAll(en.keySet());
        Set<String> onlyEn = new LinkedHashSet<>(en.keySet());
        onlyEn.removeAll(vi.keySet());

        assertTrue(onlyVi.isEmpty(), "keys missing from en.json: " + onlyVi);
        assertTrue(onlyEn.isEmpty(), "keys missing from vi.json: " + onlyEn);
        assertTrue(vi.size() > 20, "suspiciously few translation keys");
    }

    /**
     * The font ships precomposed Vietnamese letters, not combining marks, so a
     * decomposed string would silently lose its accents on screen. A
     * translator's editor can emit either form.
     */
    @Test
    void translationsArePrecomposed() {
        for (String lang : new String[] {"vi", "en"}) {
            for (Map.Entry<String, String> e : readStrings(lang).entrySet()) {
                String value = e.getValue();
                assertEquals(Normalizer.normalize(value, Normalizer.Form.NFC), value,
                    lang + ".json key '" + e.getKey() + "' is not NFC-normalised");
            }
        }
    }

    /** Every codepoint the UI can show must exist in the generated font. */
    @Test
    void fontCoversEveryTranslatedCharacter() {
        Set<Integer> glyphs = fontGlyphs();
        List<String> missing = new ArrayList<>();
        for (String lang : new String[] {"vi", "en"}) {
            for (Map.Entry<String, String> e : readStrings(lang).entrySet()) {
                e.getValue().codePoints().distinct().forEach(cp -> {
                    if (!glyphs.contains(cp)) {
                        missing.add(String.format("U+%04X '%c' (%s/%s)",
                            cp, cp, lang, e.getKey()));
                    }
                });
            }
        }
        assertTrue(missing.isEmpty(), "font is missing glyphs for: " + missing);
    }

    // ---- font ------------------------------------------------------------

    private static final Pattern CHAR_LINE = Pattern.compile(
        "char id=(-?\\d+).*?width=(-?\\d+)\\s+height=(-?\\d+)\\s+"
        + "xoffset=(-?\\d+)\\s+yoffset=(-?\\d+)");

    private static Set<Integer> fontGlyphs() {
        Set<Integer> out = new LinkedHashSet<>();
        for (String line : fontLines()) {
            Matcher m = CHAR_LINE.matcher(line);
            if (m.find()) {
                out.add(Integer.parseInt(m.group(1)));
            }
        }
        return out;
    }

    private static List<String> fontLines() {
        File file = new File("assets/fonts/pixeloid_9.fnt");
        assertTrue(file.isFile(), "font not built - run tools/make_font.py");
        return new ArrayList<>(java.util.Arrays.asList(
            new FileHandle(file).readString("UTF-8").split("\\r?\\n")));
    }

    /**
     * Guards the bug where the writer emitted the untrimmed line metrics while
     * every glyph's yoffset had already been trimmed. A single line of text
     * still looked right, because every glyph was off by the same amount; what
     * broke was the text's relationship to its line box.
     */
    @Test
    void fontMetricsAreSelfConsistent() {
        int lineHeight = -1;
        int base = -1;
        int highest = Integer.MAX_VALUE;
        int lowest = Integer.MIN_VALUE;
        int belowBaseline = 0;

        for (String line : fontLines()) {
            if (line.startsWith("common ")) {
                lineHeight = intField(line, "lineHeight");
                base = intField(line, "base");
                continue;
            }
            Matcher m = CHAR_LINE.matcher(line);
            if (!m.find() || Integer.parseInt(m.group(2)) == 0) {
                continue;
            }
            int height = Integer.parseInt(m.group(3));
            int yoffset = Integer.parseInt(m.group(5));
            highest = Math.min(highest, yoffset);
            lowest = Math.max(lowest, yoffset + height);
            if (yoffset + height > base) {
                belowBaseline++;
            }
        }

        assertTrue(lineHeight > 0 && base > 0, "font header not parsed");
        assertEquals(0, highest, "the tallest glyph should start at the top of the line box");
        assertEquals(lineHeight - 1, lowest,
            "the deepest glyph should reach the bottom of the line box");
        assertTrue(belowBaseline > 0,
            "no glyph descends below the baseline, so `base` is wrong");
    }

    private static int intField(String line, String name) {
        Matcher m = Pattern.compile(name + "=(-?\\d+)").matcher(line);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }
}
