#!/usr/bin/env python3
"""Generate a pixel-perfect libGDX BitmapFont (.fnt + .png) with full Vietnamese.

Pixeloid Sans is designed on a 9px em grid but ships without the 32 precomposed
letters that stack a tone mark on top of a circumflex or breve: {a,e,o} with a
hat and a with a breve, times acute/grave/hook/tilde, times case. Those are
exactly the characters Vietnamese UI strings hit constantly, so we synthesise
them -- lift the bare tone mark out of the plain accented letter (A-acute minus
A, and so on) and stack it two rows above the hat. Everything stays on the pixel
grid, so the result is still crisp.

Usage:  python tools/make_font.py <font.ttf> <out_dir> [--size 9] [--preview]
"""
import argparse
import os
import sys

from PIL import Image, ImageDraw, ImageFont

# Scratch headroom above the ascender so a stacked tone mark always has room
# during composition. The real line metrics are derived from the finished
# glyphs afterwards, so making this generous costs nothing.
EXTRA_TOP = 4

# Characters the game needs. The Latin-1 accented letters come along for free
# and cost nothing meaningful in atlas space.
ASCII = "".join(chr(c) for c in range(0x20, 0x7F))
LATIN1 = "ÀÁÂÃÈÉÊÌÍÒÓÔ" \
         "ÕÙÚÝàáâãèéêì" \
         "íòóôõùúý"
VIET_EXTRA = "ĂăĐđĨĩŨũƠơƯư" \
             + "".join(chr(c) for c in range(0x1ea0, 0x1efa))
EXTRA = "‘’“”…—–→←↑↓×·«»©°"

# tone name -> (plain letter, that letter carrying only this tone)
TONE_SOURCE = {
    "acute": ("A", "Á"),   # A, A-acute
    "grave": ("A", "À"),   # A, A-grave
    "hook":  ("A", "Ả"),   # A, A-hook-above
    "tilde": ("A", "Ã"),   # A, A-tilde
}


def _composites():
    """codepoint -> (existing hatted base char, tone to stack on it).

    In Unicode each of these blocks runs acute, grave, hook, tilde, dot-below,
    with uppercase on the even codepoint and lowercase on the odd one directly
    after. Only the first four tones stack above the hat; dot-below sits under
    the letter and the font already ships it.
    """
    table = {}
    groups = [
        ("Â", "â", 0x1ea4),   # A with circumflex
        ("Ă", "ă", 0x1eae),   # A with breve
        ("Ê", "ê", 0x1ebe),   # E with circumflex
        ("Ô", "ô", 0x1ed0),   # O with circumflex
    ]
    for upper, lower, start in groups:
        for i, tone in enumerate(("acute", "grave", "hook", "tilde")):
            table[start + i * 2] = (upper, tone)
            table[start + i * 2 + 1] = (lower, tone)
    return table


COMPOSITES = _composites()

_CMAP_CACHE = {}


def font_cmap(font_path):
    """Set of codepoints the TTF actually maps to a glyph."""
    if font_path not in _CMAP_CACHE:
        from fontTools.ttLib import TTFont
        tt = TTFont(font_path)
        cps = set()
        for table in tt["cmap"].tables:
            cps.update(table.cmap.keys())
        _CMAP_CACHE[font_path] = cps
    return _CMAP_CACHE[font_path]


class Rasterizer:
    """Renders single characters with the ascender line pinned at y=EXTRA_TOP."""

    def __init__(self, font_path, size):
        self.path = font_path
        self.font = ImageFont.truetype(font_path, size)
        self.ascent, self.descent = self.font.getmetrics()
        self.height = EXTRA_TOP + self.ascent + self.descent
        self.baseline = EXTRA_TOP + self.ascent

    def render(self, ch):
        """Return (1-bit-ish 'L' image, advance width)."""
        adv = int(round(self.font.getlength(ch)))
        width = max(adv, 1) + 8          # slack for marks that overhang the advance
        img = Image.new("L", (width, self.height), 0)
        ImageDraw.Draw(img).text((0, EXTRA_TOP), ch, font=self.font, fill=255)
        # The font is on-grid at this size, but threshold anyway so a stray
        # antialiased pixel can never leak into the atlas.
        return img.point(lambda v: 255 if v >= 128 else 0), adv


def extract_mark(rast, plain, accented):
    """The pixels the accented letter has and the plain one does not: the mark."""
    plain_img, _ = rast.render(plain)
    acc_img, _ = rast.render(accented)
    width = min(plain_img.width, acc_img.width)
    mark = Image.new("L", (width, rast.height), 0)
    pp, pa, pm = plain_img.load(), acc_img.load(), mark.load()
    for y in range(rast.height):
        for x in range(width):
            if pa[x, y] and not pp[x, y]:
                pm[x, y] = 255
    return mark, mark.getbbox()


def compose(rast, base_ch, mark_img, mark_box):
    """Stack a tone mark directly above the base letter's own hat."""
    base, adv = rast.render(base_ch)
    ink = base.getbbox()
    if ink is None or mark_box is None:
        return base, adv

    mx0, my0, mx1, my1 = mark_box
    mark_w, mark_h = mx1 - mx0, my1 - my0

    # Centre over the base letter's own hat rather than the whole letter: the
    # tone mark belongs on the circumflex/breve, and the letter body below is
    # often wider and off-centre relative to it.
    hat = base.crop((0, ink[1], base.width, ink[1] + mark_h)).getbbox()
    if hat is None:
        hx0, hx1 = ink[0], ink[2]
    else:
        hx0, hx1 = hat[0], hat[2]
    tx = hx0 + ((hx1 - hx0) - mark_w) // 2
    ty = ink[1] - mark_h

    out = base.copy()
    po, pm = out.load(), mark_img.load()
    for y in range(my0, my1):
        for x in range(mx0, mx1):
            if not pm[x, y]:
                continue
            dx, dy = tx + (x - mx0), ty + (y - my0)
            if 0 <= dx < out.width and 0 <= dy < out.height:
                po[dx, dy] = 255
    return out, adv


def wanted_chars():
    out, seen = [], set()
    for ch in ASCII + LATIN1 + VIET_EXTRA + EXTRA:
        if ch not in seen:
            seen.add(ch)
            out.append(ch)
    return out


def build(font_path, out_dir, size, preview):
    rast = Rasterizer(font_path, size)
    cmap = font_cmap(font_path)

    marks = {}
    for tone, (plain, accented) in TONE_SOURCE.items():
        if ord(accented) not in cmap:
            print("  ! tone source U+%04X ('%s') missing; composites using it "
                  "will be skipped" % (ord(accented), tone))
            continue
        marks[tone] = extract_mark(rast, plain, accented)

    glyphs, synthesized, skipped = {}, [], []
    for ch in wanted_chars():
        cp = ord(ch)
        if cp in cmap:
            img, adv = rast.render(ch)
        elif cp in COMPOSITES:
            base_ch, tone = COMPOSITES[cp]
            if tone not in marks or ord(base_ch) not in cmap:
                skipped.append(cp)
                continue
            img, adv = compose(rast, base_ch, *marks[tone])
            synthesized.append(cp)
        else:
            skipped.append(cp)
            continue
        glyphs[cp] = (img, adv)

    print("  glyphs rendered   : %d" % len(glyphs))
    print("  glyphs synthesized: %d" % len(synthesized))
    if skipped:
        print("  glyphs SKIPPED    : %d  (%s%s)"
              % (len(skipped), " ".join("U+%04X" % c for c in skipped[:12]),
                 " ..." if len(skipped) > 12 else ""))

    # --- pack into a square atlas on a 1px grid ---------------------------
    pad = 1
    cells = []
    for cp, (img, adv) in sorted(glyphs.items()):
        box = img.getbbox()
        if box is None:                              # space and friends
            cells.append((cp, None, 0, 0, 0, 0, adv))
        else:
            x0, y0, x1, y1 = box
            cells.append((cp, img.crop(box), x1 - x0, y1 - y0, x0, y0, adv))

    # Derive the real line metrics from the finished glyphs. EXTRA_TOP is
    # deliberately generous during composition; whatever headroom went unused
    # is trimmed here so it does not become dead space on every line.
    inked = [c for c in cells if c[1] is not None]
    top = min(c[5] for c in inked)
    bottom = max(c[5] + c[3] for c in inked)
    line_height = bottom - top + 1          # +1 px of leading
    base_line = rast.baseline - top

    area = sum((w + pad) * (h + pad) for _, im, w, h, _, _, _ in cells if im)
    side = 64
    while side * side < area * 1.35:
        side *= 2

    atlas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    records, px, py, row_h = [], pad, pad, 0
    for cp, im, w, h, xo, yo, adv in cells:
        if im is None:
            records.append((cp, 0, 0, 0, 0, 0, 0, adv))
            continue
        if px + w + pad > side:
            px, py, row_h = pad, py + row_h + pad, 0
        white = Image.new("RGBA", (w, h), (255, 255, 255, 255))
        atlas.paste(white, (px, py), im)             # im doubles as the mask
        records.append((cp, px, py, w, h, xo, yo - top, adv))
        px += w + pad
        row_h = max(row_h, h)

    os.makedirs(out_dir, exist_ok=True)
    stem = "pixeloid_%d" % size
    png_name = stem + ".png"
    atlas.save(os.path.join(out_dir, png_name))

    with open(os.path.join(out_dir, stem + ".fnt"), "w", encoding="utf-8") as fh:
        fh.write('info face="%s" size=%d bold=0 italic=0 charset="" unicode=1 '
                 'stretchH=100 smooth=0 aa=1 padding=0,0,0,0 spacing=1,1\n'
                 % (stem, size))
        fh.write('common lineHeight=%d base=%d scaleW=%d scaleH=%d pages=1 packed=0\n'
                 % (line_height, base_line, side, side))
        fh.write('page id=0 file="%s"\n' % png_name)
        fh.write('chars count=%d\n' % len(records))
        for cp, x, y, w, h, xo, yo, adv in records:
            fh.write('char id=%-6d x=%-4d y=%-4d width=%-3d height=%-3d '
                     'xoffset=%-3d yoffset=%-3d xadvance=%-3d page=0 chnl=15\n'
                     % (cp, x, y, w, h, xo, yo, adv))
        fh.write("kernings count=0\n")

    print("  atlas             : %dx%d -> %s" % (side, side, png_name))
    print("  lineHeight/base   : %d / %d" % (line_height, base_line))

    if preview:
        write_preview(glyphs, rast, out_dir, stem, line_height, top)
    return not skipped


SAMPLES = [
    "Bắt đầu mới",
    "Tiếp tục",
    "Cài đặt",
    "Điều khiển",
    "Ngọn Lửa Thiêng",
    "Làng Kagemura",
    "Thất bại — thử lại?",
    "Chiến thắng!",
    "Âm lượng nhạc nền",
    "AaBbCc 0123 ÂĂÊÔƠƯ",
]


def write_preview(glyphs, rast, out_dir, stem, line_h, top):
    """Draw sample Vietnamese UI strings from the generated glyphs themselves,
    so the preview shows what the game will actually display."""
    width = 4 + max(
        sum(glyphs[ord(c)][1] for c in s if ord(c) in glyphs) for s in SAMPLES
    )
    img = Image.new("RGBA", (width, line_h * len(SAMPLES) + 4), (24, 20, 28, 255))
    ink = Image.new("RGBA", (1, 1), (255, 240, 220, 255))
    for row, text in enumerate(SAMPLES):
        pen_x, row_top = 2, 2 + row * line_h
        for ch in text:
            entry = glyphs.get(ord(ch))
            if entry is None:
                continue
            glyph, adv = entry
            box = glyph.getbbox()
            if box:
                cropped = glyph.crop(box)
                patch = ink.resize(cropped.size)
                img.paste(patch, (pen_x + box[0], row_top + box[1] - top), cropped)
            pen_x += adv
    # Previews are for a human to eyeball, not for the game to ship.
    preview_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "preview")
    os.makedirs(preview_dir, exist_ok=True)
    img.save(os.path.join(preview_dir, stem + "_1x.png"))
    img.resize((img.width * 4, img.height * 4), Image.NEAREST).save(
        os.path.join(preview_dir, stem + "_4x.png"))
    print("  preview           : tools/preview/%s_4x.png" % stem)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("font")
    ap.add_argument("out_dir")
    ap.add_argument("--size", type=int, default=9)
    ap.add_argument("--preview", action="store_true")
    args = ap.parse_args()
    print("%s @ %dpx" % (args.font, args.size))
    sys.exit(0 if build(args.font, args.out_dir, args.size, args.preview) else 1)
