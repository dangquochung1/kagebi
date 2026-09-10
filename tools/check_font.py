#!/usr/bin/env python3
"""Check whether a font has the glyphs needed for Vietnamese + English UI.

Usage:  python tools/check_font.py <font.ttf|font.otf> [more fonts...]

Exits non-zero if any font is missing required glyphs, so it can gate a build.
"""
import struct
import sys

# Every precomposed Vietnamese letter, both cases, plus the base Latin letters
# Vietnamese needs beyond ASCII.
VIET = (
    "ÀÁÂÃÈÉÊÌÍÒÓÔ"
    "ÕÙÚÝĂĐĨŨƠƯ"
    "ẠẢẤẦẨẪẬẮẰẲẴẶ"
    "ẸẺẼẾỀỂỄỆỈỊỌỎ"
    "ỐỒỔỖỘỚỜỞỠỢỤỦ"
    "ỨỪỬỮỰỲỴỶỸ"
    "àáâãèéêìíòóô"
    "õùúýăđĩũơư"
    "ạảấầẩẫậắằẳẵặ"
    "ẹẻẽếềểễệỉịọỏ"
    "ốồổỗộớờởỡợụủ"
    "ứừửữựỳỵỷỹ"
)
ASCII = "".join(chr(c) for c in range(0x20, 0x7F))


def read_cmap(path):
    """Return the set of Unicode codepoints a font's cmap maps to a glyph."""
    with open(path, "rb") as fh:
        data = fh.read()

    if data[:4] == b"ttcf":                      # font collection: use first font
        offset = struct.unpack(">I", data[12:16])[0]
    else:
        offset = 0

    num_tables = struct.unpack(">H", data[offset + 4 : offset + 6])[0]
    tables = {}
    for i in range(num_tables):
        rec = offset + 12 + 16 * i
        tag = data[rec : rec + 4].decode("latin1")
        tables[tag] = struct.unpack(">I", data[rec + 8 : rec + 12])[0]

    if "cmap" not in tables:
        raise ValueError("no cmap table")

    base = tables["cmap"]
    codepoints = set()
    for i in range(struct.unpack(">H", data[base + 2 : base + 4])[0]):
        rec = base + 4 + 8 * i
        sub = base + struct.unpack(">I", data[rec + 4 : rec + 8])[0]
        fmt = struct.unpack(">H", data[sub : sub + 2])[0]

        if fmt == 4:
            seg = struct.unpack(">H", data[sub + 6 : sub + 8])[0] // 2
            ends = struct.unpack(">%dH" % seg, data[sub + 14 : sub + 14 + seg * 2])
            sp = sub + 16 + seg * 2
            starts = struct.unpack(">%dH" % seg, data[sp : sp + seg * 2])
            dp = sp + seg * 2
            deltas = struct.unpack(">%dh" % seg, data[dp : dp + seg * 2])
            rp = dp + seg * 2
            ranges = struct.unpack(">%dH" % seg, data[rp : rp + seg * 2])
            for k in range(seg):
                if starts[k] > ends[k] or ends[k] == 0xFFFF:
                    continue
                for cp in range(starts[k], ends[k] + 1):
                    if ranges[k] == 0:
                        gid = (cp + deltas[k]) & 0xFFFF
                    else:
                        gi = rp + k * 2 + ranges[k] + (cp - starts[k]) * 2
                        if gi + 2 > len(data):
                            continue
                        gid = struct.unpack(">H", data[gi : gi + 2])[0]
                        if gid:
                            gid = (gid + deltas[k]) & 0xFFFF
                    if gid:
                        codepoints.add(cp)

        elif fmt == 6:
            first, count = struct.unpack(">HH", data[sub + 6 : sub + 10])
            gids = struct.unpack(">%dH" % count, data[sub + 10 : sub + 10 + count * 2])
            codepoints.update(first + n for n, g in enumerate(gids) if g)

        elif fmt == 12:
            ngroups = struct.unpack(">I", data[sub + 12 : sub + 16])[0]
            for g in range(ngroups):
                rec = sub + 16 + 12 * g
                start, end, _ = struct.unpack(">III", data[rec : rec + 12])
                if end - start > 0x10000:        # guard against absurd ranges
                    end = start + 0x10000
                codepoints.update(range(start, end + 1))

    return codepoints


def check(path):
    try:
        have = read_cmap(path)
    except Exception as exc:                     # noqa: BLE001 - report, don't crash
        print("  ERROR reading %s: %s" % (path, exc))
        return False

    missing_ascii = [c for c in ASCII if ord(c) not in have]
    missing_viet = [c for c in VIET if ord(c) not in have]

    print("  codepoints in font : %d" % len(have))
    print("  ASCII printable    : %d/%d" % (len(ASCII) - len(missing_ascii), len(ASCII)))
    print("  Vietnamese letters : %d/%d" % (len(VIET) - len(missing_viet), len(VIET)))

    if missing_viet:
        shown = "".join(missing_viet[:40])
        print("  MISSING (first 40) : %s" % shown.encode("unicode_escape").decode())
    ok = not missing_ascii and not missing_viet
    print("  -> %s" % ("OK, usable for Vietnamese UI" if ok else "NOT usable for Vietnamese UI"))
    return ok


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        raise SystemExit(2)
    all_ok = True
    for font in sys.argv[1:]:
        print(font)
        all_ok &= check(font)
        print()
    raise SystemExit(0 if all_ok else 1)
