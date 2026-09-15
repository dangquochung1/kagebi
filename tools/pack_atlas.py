#!/usr/bin/env python3
"""Pack the game's sprites into libGDX texture atlases.

Written by hand rather than using libGDX's own TexturePacker, for two reasons:
gdx-tools drags in an obsolete LWJGL backend, and TexturePacker treats a
trailing `_<digits>` in a filename as an animation frame index -- which would
quietly merge `nine_path_panel_2.png` into `nine_path_panel` and break the skin.

Two rules here are load-bearing and easy to get wrong:

* **Region names are paths, not filenames.** The asset tree has 92 files called
  `spritesheet.png` and 86 called `faceset.png`. `Skin.addRegions` puts them in
  an ObjectMap, so duplicates overwrite each other silently -- every monster
  would end up wearing the same sprite with no error anywhere.
* **Nine-patch splits live in the atlas, not the skin JSON.** `Skin.getDrawable`
  returns a NinePatchDrawable only when the region carries a `split` value;
  there is no way to declare one from skin JSON alone.

Usage:  python tools/pack_atlas.py [--only ui] [--verbose]
"""
import argparse
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GFX = os.path.join(ROOT, "assets", "gfx")
OUT = os.path.join(ROOT, "assets", "atlas")

# One transparent pixel of gutter on every side, so a half-pixel sampling error
# at the edge of a region lands on transparency instead of the neighbouring
# sprite. Two adjacent images therefore always have 2px between them.
PAD = 1

# Nine-patch splits, measured off the actual pixels rather than guessed. The
# panel's are asymmetric because it has a light bevel on the left and a dark one
# on the right; button_down's are shifted a row down because the art itself is,
# which gives the pressed-in look for free.
SPLITS = {
    "ui/panel":             (6, 5, 6, 5),
    "ui/panel_2":           (6, 5, 6, 5),
    "ui/panel_3":           (6, 5, 6, 5),
    "ui/panel_disabled":    (6, 5, 6, 5),
    "ui/panel_interior":    (3, 2, 3, 2),
    "ui/bg":                (2, 2, 2, 2),
    "ui/bg_2":              (2, 2, 2, 2),
    "ui/focus":             (3, 3, 3, 3),
    "ui/button_up":         (2, 2, 2, 2),
    "ui/button_over":       (2, 2, 2, 2),
    "ui/button_disabled":   (2, 2, 2, 2),
    "ui/button_down":       (2, 2, 3, 1),
    "ui/slider_fill":       (3, 4, 3, 3),
    "ui/slider_fill_over":  (3, 4, 3, 3),
    "ui/tab":               (4, 6, 4, 0),
    "ui/tab_over":          (4, 6, 4, 0),
    "ui/tab_selected":      (4, 6, 4, 0),
    "ui/tab_disabled":      (4, 6, 4, 0),
    "ui/cell":              (3, 1, 3, 1),
    "ui/white":             (1, 1, 1, 1),
    # The Sunnyside interface's boxes - dark, light and white - and its name
    # label, each put together from the pack's loose pieces by
    # tools/make_island.py. The boxes' pieces are all 3px; the label stretches
    # only across.
    "ui/sunny/box_dark":    (3, 3, 3, 3),
    "ui/sunny/box_light":   (3, 3, 3, 3),
    "ui/sunny/box_white":   (3, 3, 3, 3),
    "ui/sunny/label":       (4, 4, 0, 0),
}

# Content insets, as (left, right, top, bottom). libGDX applies these via
# NinePatch.setPadding, and Button/Table read them to inset their label, so
# declaring the breathing room here gives it to every widget everywhere rather
# than needing a pad() call at each call site.
#
# Without these the label sits directly against the frame and Vietnamese
# diacritics collide with the top border -- the tone mark on a capital sits two
# rows above the cap height, which is taller than Latin text ever gets.
PADS = {
    "ui/panel":            (8, 8, 7, 7),
    "ui/panel_2":          (8, 8, 7, 7),
    "ui/panel_3":          (8, 8, 7, 7),
    "ui/bg":               (4, 4, 3, 3),
    "ui/bg_2":             (4, 4, 3, 3),
    "ui/button_up":        (5, 5, 3, 2),
    "ui/button_over":      (5, 5, 3, 2),
    "ui/button_down":      (5, 5, 4, 1),
    "ui/button_disabled":  (5, 5, 3, 2),
    "ui/tab":              (5, 6, 4, 2),
    "ui/tab_over":         (5, 6, 4, 2),
    "ui/tab_selected":     (5, 6, 4, 2),
    "ui/tab_disabled":     (5, 6, 4, 2),
    "ui/cell":             (3, 2, 3, 2),
}

# Short, stable names for the widget art, so the skin JSON does not have to
# know the asset pack's folder layout. An alias is a second region entry
# pointing at the same rectangle -- it costs no pixels.
_W = "ui/theme/theme_wood/"
ALIASES = {
    "ui/panel":                 _W + "nine_path_panel",
    "ui/panel_2":               _W + "nine_path_panel_2",
    "ui/panel_3":               _W + "nine_path_panel_3",
    "ui/panel_disabled":        _W + "nine_path_panel_disabled",
    "ui/panel_interior":        _W + "nine_path_panel_interior",
    "ui/bg":                    _W + "nine_path_bg",
    "ui/bg_2":                  _W + "nine_path_bg_2",
    "ui/focus":                 _W + "nine_path_focus",
    "ui/button_up":             _W + "button_normal",
    "ui/button_over":           _W + "button_hover",
    "ui/button_down":           _W + "button_pressed",
    "ui/button_disabled":       _W + "button_disabled",
    "ui/toggle_on":             _W + "button_checked",
    "ui/toggle_off":            _W + "button_unchecked",
    "ui/toggle_on_disabled":    _W + "button_checked_disabled",
    "ui/toggle_off_disabled":   _W + "button_unchecked_disabled",
    "ui/check_on":              _W + "checked",
    "ui/check_off":             _W + "unchecked",
    "ui/check_on_disabled":     _W + "checked_disabled",
    "ui/check_off_disabled":    _W + "unchecked_disabled",
    "ui/radio_on":              _W + "radio_checked",
    "ui/radio_off":             _W + "radio_unchecked",
    "ui/radio_on_disabled":     _W + "radio_checked_disabled",
    "ui/radio_off_disabled":    _W + "radio_unchecked_disabled",
    "ui/arrow_left":            _W + "arrow_left",
    "ui/arrow_left_over":       _W + "arrow_left_hover",
    "ui/arrow_right":           _W + "arrow_right",
    "ui/arrow_right_over":      _W + "arrow_right_hover",
    # The source pack misspells these as "slidder".
    "ui/hslider_knob":          _W + "h_slidder_grabber",
    "ui/hslider_knob_over":     _W + "h_slidder_grabber_hover",
    "ui/hslider_knob_disabled": _W + "h_slidder_grabber_disabled",
    "ui/vslider_knob":          _W + "v_slidder_grabber",
    "ui/vslider_knob_over":     _W + "v_slidder_grabber_hover",
    "ui/vslider_knob_disabled": _W + "v_slidder_grabber_disabled",
    "ui/slider_fill":           _W + "slider_progress",
    "ui/slider_fill_over":      _W + "slider_progress_hover",
    # tab.png and tab_selected.png are byte-identical in the pack; only four
    # of the five tab images are actually distinct.
    "ui/tab":                   _W + "tab_unselected",
    "ui/tab_over":              _W + "tab_hover",
    "ui/tab_selected":          _W + "tab",
    "ui/tab_disabled":          _W + "tab_disabled",
    "ui/cell":                  _W + "inventory_cell",
    "ui/dialog":                "ui/dialog/dialogbox",
    "ui/dialog_faceset":        "ui/dialog/dialogboxfaceset",
    "ui/faceset_frame":         "ui/dialog/facesetbox",
    "ui/cursor":                "ui/arrow",
}

ATLASES = {
    "ui": {
        "roots": [("ui", os.path.join(GFX, "ui")),
                  ("items", os.path.join(GFX, "items"))],
        # The pack's own bitmap fonts are unused (we generate our own), and the
        # WIP themes are unfinished recolours that would pollute the namespace.
        "exclude_dirs": ["ui/font", "ui/theme/wip"],
        "aliases": ALIASES,
        "extra_images": [(os.path.join(ROOT, "assets", "fonts", "pixeloid_9.png"),
                          "pixeloid_9")],
        "synthetic": True,
        "size": 1024,
    },
    "actors": {
        "roots": [("player", os.path.join(GFX, "actors", "player")),
                  ("monsters", os.path.join(GFX, "actors", "monsters")),
                  ("bosses", os.path.join(GFX, "actors", "bosses")),
                  ("depths", os.path.join(GFX, "actors", "depths"))],
        # The combined player sheet repeats every frame the separate files
        # already carry, and at 256x544 it is the single largest image here.
        "exclude_files": ["player/ninjagreen/spritesheet"],
        # Sits directly in actors/ rather than in one of the roots above, but a
        # drop shadow under every actor is what stops a top-down sprite looking
        # like a sticker on the floor.
        "extra_images": [(os.path.join(GFX, "actors", "shadow.png"), "shadow")],
        "size": 2048,
    },
    "npc": {
        "roots": [("npc", os.path.join(GFX, "actors", "npc"))],
        "size": 2048,
    },
    "fx": {
        "roots": [("fx", os.path.join(GFX, "fx")),
                  ("props", os.path.join(GFX, "props"))],
        # Full-screen overlays need Texture.setWrap(Repeat) to scroll, and wrap
        # is per-texture, not per-region: pushing UVs past a region's edge walks
        # into whatever was packed next to it. These stay standalone textures.
        "exclude_files": ["fx/environment/fog", "fx/environment/raylight"],
        "size": 1024,
    },
}


class MaxRects:
    """Best-short-side-fit bin packer.

    Shelf packing would leave the actors atlas (77% full at 2048) spilling onto
    a second page; MaxRects fits it on one.
    """

    def __init__(self, width, height):
        self.width, self.height = width, height
        self.free = [(0, 0, width, height)]

    def insert(self, w, h):
        best, best_score = None, None
        for fx, fy, fw, fh in self.free:
            if fw < w or fh < h:
                continue
            score = (min(fw - w, fh - h), max(fw - w, fh - h))
            if best_score is None or score < best_score:
                best, best_score = (fx, fy), score
        if best is None:
            return None
        x, y = best
        self._place(x, y, w, h)
        return x, y

    def _place(self, x, y, w, h):
        split = []
        for r in self.free:
            if not self._overlaps(r, x, y, w, h):
                split.append(r)
                continue
            split.extend(self._split(r, x, y, w, h))
        self.free = self._prune(split)

    @staticmethod
    def _overlaps(r, x, y, w, h):
        rx, ry, rw, rh = r
        return not (x >= rx + rw or x + w <= rx or y >= ry + rh or y + h <= ry)

    @staticmethod
    def _split(r, x, y, w, h):
        rx, ry, rw, rh = r
        out = []
        if x > rx:
            out.append((rx, ry, x - rx, rh))
        if x + w < rx + rw:
            out.append((x + w, ry, rx + rw - (x + w), rh))
        if y > ry:
            out.append((rx, ry, rw, y - ry))
        if y + h < ry + rh:
            out.append((rx, y + h, rw, ry + rh - (y + h)))
        return out

    @staticmethod
    def _prune(rects):
        out = []
        for i, a in enumerate(rects):
            contained = False
            for j, b in enumerate(rects):
                if i != j and MaxRects._contains(b, a):
                    # Keep exactly one of a pair of identical rects.
                    if a != b or j < i:
                        contained = True
                        break
            if not contained:
                out.append(a)
        return out

    @staticmethod
    def _contains(outer, inner):
        ox, oy, ow, oh = outer
        ix, iy, iw, ih = inner
        return ix >= ox and iy >= oy and ix + iw <= ox + ow and iy + ih <= oy + oh


def collect(spec):
    """Gather (region_name, PIL image) for one atlas, with path-based names."""
    exclude_dirs = tuple(spec.get("exclude_dirs", []))
    exclude_files = set(spec.get("exclude_files", []))
    items = []

    for prefix, root in spec["roots"]:
        if not os.path.isdir(root):
            raise SystemExit("missing source tree: %s" % root)
        for cur, dirs, files in os.walk(root):
            dirs.sort()
            for f in sorted(files):
                if not f.endswith(".png"):
                    continue
                rel = os.path.relpath(os.path.join(cur, f), root).replace(os.sep, "/")
                name = prefix + "/" + rel[:-4]
                if name.startswith(exclude_dirs) or name in exclude_files:
                    continue
                items.append((name, Image.open(os.path.join(cur, f)).convert("RGBA")))

    for path, name in spec.get("extra_images", []):
        if not os.path.isfile(path):
            raise SystemExit("missing extra image: %s" % path)
        items.append((name, Image.open(path).convert("RGBA")))

    if spec.get("synthetic"):
        # A stretchable solid white, for every tinted fill: scrims, list
        # selection, slider fill, scrollbars. 3x3 with 1px splits rather than
        # 1x1 so a stretched edge can never sample its own transparent padding.
        items.append(("ui/white", Image.new("RGBA", (3, 3), (255, 255, 255, 255))))
        items.append(("ui/px", Image.new("RGBA", (1, 1), (255, 255, 255, 255))))

    return items


def pack(name, spec, verbose):
    items = collect(spec)
    by_name = {n: im for n, im in items}

    missing = [src for src in spec.get("aliases", {}).values() if src not in by_name]
    if missing:
        raise SystemExit("%s: alias sources not found: %s" % (name, ", ".join(sorted(missing))))

    # Deterministic: biggest first (packs better), then by name so the output is
    # byte-stable across runs and diffs cleanly.
    items.sort(key=lambda t: (-max(t[1].size), -(t[1].width * t[1].height), t[0]))

    size = spec["size"]
    packer = MaxRects(size, size)
    page = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    placed = {}
    used_area = 0

    for region, im in items:
        w, h = im.size
        spot = packer.insert(w + PAD * 2, h + PAD * 2)
        if spot is None:
            raise SystemExit(
                "%s: %dx%d page is too small (failed on '%s' at %dx%d). "
                "Raise 'size' in the manifest." % (name, size, size, region, w, h))
        x, y = spot
        page.paste(im, (x + PAD, y + PAD))
        placed[region] = (x + PAD, y + PAD, w, h)
        used_area += w * h

    os.makedirs(OUT, exist_ok=True)
    png_name = name + ".png"
    page.save(os.path.join(OUT, png_name))

    # Aliases share a rectangle with their source, so they must be emitted with
    # the same bounds. Splits are keyed by the final region name.
    entries = dict(placed)
    for alias, src in spec.get("aliases", {}).items():
        entries[alias] = placed[src]

    lines = [png_name,
             "size: %d,%d" % (size, size),
             "format: RGBA8888",
             "filter: Nearest,Nearest",
             "repeat: none"]
    for region in sorted(entries):
        x, y, w, h = entries[region]
        lines.append(region)
        lines.append("  bounds: %d, %d, %d, %d" % (x, y, w, h))
        if region in SPLITS:
            lines.append("  split: %d, %d, %d, %d" % SPLITS[region])
            # libGDX only honours `pad` on a region that also has `split`.
            if region in PADS:
                lines.append("  pad: %d, %d, %d, %d" % PADS[region])
        # Always -1: any region with a real index makes libGDX sort the whole
        # region list by index, silently reordering atlas.getRegions().
        lines.append("  index: -1")

    with open(os.path.join(OUT, name + ".atlas"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")

    occupancy = 100.0 * used_area / (size * size)
    print("  %-8s %4d images + %2d aliases -> %dx%d, %.0f%% full"
          % (name, len(placed), len(entries) - len(placed), size, size, occupancy))
    if verbose:
        for region in sorted(entries):
            print("      %-46s %s" % (region, entries[region]))
    return entries


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only", help="pack just this atlas")
    ap.add_argument("--verbose", action="store_true")
    args = ap.parse_args()

    names = [args.only] if args.only else list(ATLASES)
    for n in names:
        if n not in ATLASES:
            raise SystemExit("unknown atlas '%s' (have: %s)" % (n, ", ".join(ATLASES)))

    print("packing atlases")
    for n in names:
        pack(n, ATLASES[n], args.verbose)
    return 0


if __name__ == "__main__":
    sys.exit(main())
