#!/usr/bin/env python3
"""Cutting a skill strip this game can draw, whatever pack it came from.

Three tools write strips into `assets/gfx/fx/skill/` - one per element - and
they were three copies of the same hundred lines. This is those lines, once.
Each tool is now a table of sources plus the reasons behind it, which is the
part that is actually different between them.

The rules every strip obeys, and why:

* **Square cells, always.** `gfx.Anim.strip` takes the cell size from the
  region's *height* and divides the width by it, so a strip of 64x32 frames is
  read as twice as many 32px ones. Almost nothing arrives square, so every
  frame is padded into a square cell here.

* **One bounding box per effect, measured across all of its frames.** A ring
  opens outwards, a lash travels left to right, a comet falls. Cropping each
  frame to its own ink would centre every one of those and throw the movement
  away.

* **Lanczos, then a hard alpha cut.** A resampled edge leaves a halo of
  half-transparent pixels, which on fire reads as smoke and on poison reads as
  a stain.

* **Stored lying along +x, and upright.** Everything that draws a strip at an
  angle - `EntityWorld.dashTrail`, `Player.beginLunge`, `meleeTrail` - rotates
  by a heading and assumes the art points right. Art that arrives diagonal is
  straightened here rather than corrected by a magic number in Java, because
  the offset belongs to the picture and not to the code that aims it.
"""
import os
import re

from PIL import Image

#: Below this, a resampled edge pixel is background rather than ink.
ALPHA_CUT = 100

#: The plate an icon sits on. Shared with tools/make_skillfx.py exactly: a
#: skill cell has to look like the ones beside it, not like a second art style.
ICON_CELL = 24
ICON_INK = (26, 16, 22, 255)
ICON_RIM = (155, 81, 60, 255)


def number_in(name):
    """The last whole number in a filename, so frame10 follows frame9.

    tools/make_skillfx.py sorts on the last *digit*, which is correct only
    while a folder holds nine frames or fewer. Several of these hold twelve or
    more, and on the last digit alone frame10 sorts before frame2.
    """
    found = re.findall(r"\d+", os.path.basename(name))
    return int(found[-1]) if found else 0


class Source:
    """Where an effect's frames come from, in the three shapes they arrive in.

    `loose` is a folder of numbered PNGs. `grid` is a sheet of square cells
    read left to right and top to bottom. `row` is a single strip of square
    cells, which is what a sheet holding two animations end to end looks like.
    """

    def __init__(self, kind, path, cell=0):
        self.kind = kind
        self.path = path
        self.cell = cell

    def frames(self):
        if self.kind == "loose":
            return self._loose()
        return self._cells()

    def _loose(self):
        names = [n for n in os.listdir(self.path) if n.lower().endswith(".png")]
        names.sort(key=number_in)
        return [Image.open(os.path.join(self.path, n)).convert("RGBA") for n in names]

    def _cells(self):
        with Image.open(self.path) as sheet:
            sheet = sheet.convert("RGBA")
            c = self.cell
            out = []
            for r in range(sheet.height // c):
                for col in range(sheet.width // c):
                    out.append(sheet.crop((col * c, r * c, col * c + c, r * c + c)))
            return out


def loose(*parts):
    return Source("loose", os.path.join(*parts))


def grid(path, cell):
    return Source("grid", path, cell)


def row(path, cell):
    return Source("row", path, cell)


def union_box(images):
    """One box covering the ink of every frame, so they share an origin."""
    box = None
    for im in images:
        b = im.getbbox()
        if b is None:
            continue
        box = b if box is None else (min(box[0], b[0]), min(box[1], b[1]),
                                     max(box[2], b[2]), max(box[3], b[3]))
    return box


def shrink(im, divisor):
    out = im.resize((max(1, im.width // divisor), max(1, im.height // divisor)),
                    Image.LANCZOS)
    alpha = out.split()[3].point(lambda v: 255 if v >= ALPHA_CUT else 0)
    out.putalpha(alpha)
    return out


def plate(ink):
    """One frame, fitted onto an opaque tile with a one-pixel rim.

    A transparent icon over whatever the dungeon floor happens to be is
    unreadable. Cut to a centred square first, so a wide effect becomes a
    legible piece of the animation rather than a correct but invisible
    letterbox.
    """
    side = min(ink.width, ink.height)
    ink = ink.crop(((ink.width - side) // 2, (ink.height - side) // 2,
                    (ink.width - side) // 2 + side, (ink.height - side) // 2 + side))
    inner = ICON_CELL - 4
    scale = min(inner / ink.width, inner / ink.height)
    art = ink.resize((max(1, int(ink.width * scale)), max(1, int(ink.height * scale))),
                     Image.LANCZOS)
    alpha = art.split()[3].point(lambda v: 255 if v >= ALPHA_CUT else 0)
    art.putalpha(alpha)

    out = Image.new("RGBA", (ICON_CELL, ICON_CELL), ICON_INK)
    for x in range(ICON_CELL):
        out.putpixel((x, 0), ICON_RIM)
        out.putpixel((x, ICON_CELL - 1), ICON_RIM)
    for y in range(ICON_CELL):
        out.putpixel((0, y), ICON_RIM)
        out.putpixel((ICON_CELL - 1, y), ICON_RIM)
    out.paste(art, ((ICON_CELL - art.width) // 2, (ICON_CELL - art.height) // 2), art)
    return out


class Effect:
    """One strip this game will draw, and everything it took to get there.

    `window` trims the source to the frames worth keeping - several packs run
    far longer than they read at twenty frames a second, and some lay two
    animations end to end. `divisor` is what puts the ink at this game's scale
    beside a 16px player. `flip` and `turn` are the two normalisations: a
    reflection no rotation can produce, and a rotation that straightens art
    which arrived on a diagonal.

    `centred` opts out of the shared bounding box, and only an aura should.
    The box exists so that travel survives cropping, which is right for
    everything that goes somewhere - but an aura does not go anywhere, it
    rides a body. A pack that draws its shield side-on grows the sphere upward
    from a crescent, so one box across every frame leaves the crescent at the
    bottom of it, and a strip drawn centred on the player then puts the ward on
    the floor below their feet. Per-frame cropping throws away a rise that this
    game's camera cannot see anyway.
    """

    def __init__(self, name, source, cell, divisor=1, window=None,
                 icon=None, flip=False, turn=0, centred=False):
        self.name = name
        self.source = source
        self.cell = cell
        self.divisor = divisor
        self.window = window
        self.icon = icon
        self.flip = flip
        self.turn = turn
        self.centred = centred


def build_one(effect, out_fx, out_icon, report=False):
    src = effect.source.frames()
    if effect.window is not None:
        src = src[effect.window[0]:effect.window[1]]
    # Both transforms come before the bounding box, never after: the box is
    # measured across every frame at once, and moving the ink afterwards slides
    # it off the centre it was fitted to.
    if effect.flip:
        src = [im.transpose(Image.FLIP_TOP_BOTTOM) for im in src]
    if effect.turn:
        # expand=False keeps the square cell the pack drew the effect inside,
        # which is also the frame the animation was composed against. A corner
        # that leaves the cell was empty; the union box below finds what is
        # left either way.
        src = [im.rotate(effect.turn, Image.BICUBIC, expand=False) for im in src]
    src = [im for im in src if im.getbbox() is not None]
    if not src:
        raise SystemExit("%s: every frame is empty" % effect.name)

    box = union_box(src)
    small = [shrink(im.crop(im.getbbox() if effect.centred else box), effect.divisor)
             for im in src]
    w = max(im.width for im in small)
    h = max(im.height for im in small)
    if w > effect.cell or h > effect.cell:
        raise SystemExit("%s: ink is %dx%d, which does not fit a %d cell"
                         % (effect.name, w, h, effect.cell))

    cell = effect.cell
    strip = Image.new("RGBA", (cell * len(small), cell), (0, 0, 0, 0))
    for i, im in enumerate(small):
        strip.paste(im, (i * cell + (cell - im.width) // 2,
                         (cell - im.height) // 2), im)
    os.makedirs(out_fx, exist_ok=True)
    strip.save(os.path.join(out_fx, effect.name + ".png"))

    if effect.icon is not None:
        icon = plate(src[min(effect.icon, len(src) - 1)].crop(box))
        os.makedirs(out_icon, exist_ok=True)
        icon.save(os.path.join(out_icon, effect.name + ".png"))

    if report:
        print("  %-11s %2d frames  cell %2d  ink %2dx%-2d  strip %4dx%-3d%s"
              % (effect.name, len(small), cell, w, h, strip.width, strip.height,
                 "  + icon" if effect.icon is not None else ""))


def build_all(effects, out_fx, out_icon, label, report=False):
    for effect in effects:
        build_one(effect, out_fx, out_icon, report)
    print("%s: %d strips -> %s" % (label, len(effects), out_fx))
