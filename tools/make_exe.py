#!/usr/bin/env python3
"""Build a standalone Windows KAGEBI.exe with a bundled Java runtime.

The player needs no Java installed: jpackage copies a trimmed JRE in beside the
game. Output is dist/KAGEBI/, and KAGEBI.exe in it is the whole thing.

    python tools/make_exe.py            # app image, a folder you can zip
    python tools/make_exe.py --installer  # also an .msi that installs it

THE ONE THING THAT IS NOT OBVIOUS. jpackage puts everything from --input into
an `app/` subfolder, but the launcher runs with the IMAGE root as its working
directory - and libGDX resolves Gdx.files.internal("assets/...") against the
working directory. So the assets have to sit next to KAGEBI.exe, not next to
the jar, and this script moves them there after packaging. Skip that and the
build succeeds, the exe launches, and it dies on the first line of
Kagebi.create with "File not found: assets\\atlas\\ui.atlas", which reads like
a broken build rather than a misplaced folder.

Requires the JDK's jpackage (JDK 14+; this project is on 21) and WiX only for
--installer.
"""
import os
import shutil
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STAGE = os.path.join(ROOT, "build", "stage")
DIST = os.path.join(ROOT, "dist")
APP = "KAGEBI"


def jpackage():
    """The JDK's jpackage, from JAVA_HOME or from PATH."""
    home = os.environ.get("JAVA_HOME")
    if home:
        exe = os.path.join(home, "bin", "jpackage.exe")
        if os.path.isfile(exe):
            return exe
    found = shutil.which("jpackage")
    if found:
        return found
    raise SystemExit("jpackage not found. It ships with the JDK (14+); set "
                     "JAVA_HOME to your JDK, not to a JRE.")


def run(cmd, **kw):
    print("  " + " ".join(os.path.basename(c) if i == 0 else c
                          for i, c in enumerate(cmd)))
    subprocess.run(cmd, check=True, **kw)


def main():
    installer = "--installer" in sys.argv

    jar = os.path.join(ROOT, "target", "kagebi.jar")
    print("building the jar")
    run(["mvn", "-q", "package", "-DskipTests"], cwd=ROOT, shell=os.name == "nt")
    if not os.path.isfile(jar):
        raise SystemExit("mvn package produced no " + jar)

    print("staging")
    clear(STAGE)
    clear(DIST)
    os.makedirs(STAGE)
    shutil.copy2(jar, STAGE)
    # The assets are NOT inside the jar - 118MB of them, and the .tmx files
    # reference their tilesets by relative path, which does not survive being
    # flattened into a classpath. They ship as a folder next to the exe.
    shutil.copytree(os.path.join(ROOT, "assets"), os.path.join(STAGE, "assets"))

    print("packaging")
    cmd = [jpackage(),
           "--type", "msi" if installer else "app-image",
           "--name", APP,
           "--input", STAGE,
           "--main-jar", os.path.basename(jar),
           "--dest", DIST,
           "--java-options", "-Xmx1G"]
    if installer:
        cmd += ["--win-dir-chooser", "--win-shortcut", "--win-menu"]
    run(cmd, cwd=ROOT)

    image = os.path.join(DIST, APP)
    if not installer:
        # See the module docstring: beside the exe, not beside the jar.
        inner = os.path.join(image, "app", "assets")
        if os.path.isdir(inner):
            shutil.move(inner, os.path.join(image, "assets"))
        print("\n  %s\\%s.exe" % (image, APP))
        print("  %.0f MB, runs with no Java installed" % (size(image) / 1e6))
        print("  zip the KAGEBI folder to hand it to someone")
    else:
        print("\n  installer in " + DIST)


def clear(path):
    """Delete a build directory, and say why if it cannot be deleted.

    Not ignore_errors: on Windows the previous build's exe holds its own folder
    open while it runs, and a silent failure here surfaces four minutes later
    as jpackage refusing a destination that "already exists" - which points at
    the wrong problem entirely.
    """
    if not os.path.exists(path):
        return
    try:
        shutil.rmtree(path)
    except OSError as e:
        raise SystemExit("could not clear %s: %s\nClose anything running out "
                         "of that folder and try again." % (path, e))


def size(path):
    total = 0
    for dirpath, _, names in os.walk(path):
        for n in names:
            total += os.path.getsize(os.path.join(dirpath, n))
    return total


if __name__ == "__main__":
    main()
