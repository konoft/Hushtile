#!/usr/bin/env python3
"""Brings in the TVs the cleanyourtv community has tested since the last sync.

Hushtile can't be tried on every TV, so it carries cleanyourtv's tested TV lists (examples/) and device notes
(docs/devices/), credited to their contributors. This copies new and updated ones from cleanyourtv's main branch,
turns their bare issue numbers into links to cleanyourtv's GitHub (tools/upstream_names.py), and rebuilds what is
generated from them: Hushtile's TV profiles and the table in examples/README.md. A link in a note to a page Hushtile
doesn't carry (SHOWCASE.md, cleanyourtv's own docs) is pointed at that page on cleanyourtv's GitHub.
A file that came from cleanyourtv (tools/upstream_manifest.txt) and is gone there is removed here too; folders that
exist only here (TVs tested for Hushtile) are never touched.

Usage:  python3 tools/sync_upstream.py [--source DIR]   (DIR: a cleanyourtv checkout; default: a fresh clone)
The weekly workflow (.github/workflows/sync-upstream.yml) runs it, runs the tests and commits only if they pass.
"""
import argparse
import re
import filecmp
import pathlib
import shutil
import subprocess
import sys
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
UPSTREAM = "https://github.com/Gathin23/cleanyourtv.git"
SYNCED = ("examples", "docs/devices")
MANIFEST = ROOT / "tools/upstream_manifest.txt"
BLOB = "https://github.com/Gathin23/cleanyourtv/blob/main/"
LINK = re.compile(r"\]\((?!https?:|mailto:|#)([^)\s#]+)(#[^)\s]*)?\)")


def listed(path):
    if not path.exists():
        return set()
    return {l.strip() for l in path.read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")}


def upstream_files(src):
    return {f.relative_to(src).as_posix() for part in SYNCED if (src / part).is_dir()
            for f in (src / part).rglob("*") if f.is_file()}




def relink(staged, root):
    """In the incoming notes (under staged), a relative link to a file that isn't in this repository (root) goes to
    cleanyourtv's GitHub. A file the same sync brings in counts as here."""
    for path in staged.rglob("*.md"):
        rel = path.relative_to(staged)
        text = path.read_text(encoding="utf-8")

        def fix(m, rel=rel):
            target = ((root / rel).parent / m.group(1)).resolve()
            if not target.exists() and (staged / rel).parent.joinpath(m.group(1)).resolve().exists():
                return m.group(0)
            if target.exists():
                return m.group(0)
            try:
                inside = target.relative_to(root.resolve()).as_posix()
            except ValueError:
                return m.group(0)
            return f"]({BLOB}{inside}{m.group(2) or ''})"

        new = LINK.sub(fix, text)
        if new != text:
            path.write_text(new, encoding="utf-8")


def remove_gone(src, dst):
    """Files that came from cleanyourtv at the last sync and are no longer there."""
    gone = sorted(listed(MANIFEST) - upstream_files(src))
    removed = []
    for rel in gone:
        target = dst / rel
        if target.is_file():
            target.unlink()
            removed.append(rel)
            parent = target.parent
            while parent != dst and parent.is_dir() and not any(parent.iterdir()):
                parent.rmdir()
                parent = parent.parent
    return removed
GENERATED = {"README.md"}   # examples/README.md is ours: its table is rebuilt below, never copied


def changed_files(src, dst):
    """(new, updated) relative paths under SYNCED that differ between upstream src and this checkout dst."""
    new, updated = [], []
    for part in SYNCED:
        base = src / part
        if not base.is_dir():
            continue
        for f in sorted(base.rglob("*")):
            if not f.is_file():
                continue
            rel = f.relative_to(src)
            if rel.parts[0] == "examples" and rel.name in GENERATED and len(rel.parts) == 2:
                continue
            target = dst / rel
            if not target.exists():
                new.append(rel)
            elif not filecmp.cmp(f, target, shallow=False):
                updated.append(rel)
    return new, updated


def copy(src, dst, rels):
    for rel in rels:
        (dst / rel).parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src / rel, dst / rel)


def rebuild_tables(root):
    sys.path.insert(0, str(root / "tools"))
    import gen_tables
    entries = gen_tables.load(root)
    path = root / "examples/README.md"
    text = path.read_text(encoding="utf-8")
    path.write_text(gen_tables.between(text, "tv-table", gen_tables.examples_rows(entries), "examples/README.md"),
                    encoding="utf-8")


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--source", help="an existing cleanyourtv checkout instead of a fresh clone")
    args = ap.parse_args(argv)
    with tempfile.TemporaryDirectory() as tmp:
        src = pathlib.Path(args.source).resolve() if args.source else pathlib.Path(tmp) / "cleanyourtv"
        if not args.source:
            subprocess.run(["git", "clone", "--depth", "1", "--quiet", UPSTREAM, str(src)], check=True)
        # Compare after the same link rewrite, so a file we only relinked doesn't count as updated.
        staged = pathlib.Path(tmp) / "staged"
        for part in SYNCED:
            if (src / part).is_dir():
                shutil.copytree(src / part, staged / part)
        sys.path.insert(0, str(ROOT / "tools"))
        import upstream_names
        upstream_names.run(staged, [f for f in staged.rglob("*") if f.is_file()])
        relink(staged, ROOT)
        new, updated = changed_files(staged, ROOT)
        tvs = sorted({r.parts[1] for r in new if r.parts[0] == "examples" and len(r.parts) > 2
                      and not (ROOT / "examples" / r.parts[1]).exists()})
        copy(staged, ROOT, new + updated)
        removed = remove_gone(staged, ROOT)
        files = sorted(upstream_files(staged))
        MANIFEST.write_text("\n".join(files) + "\n", encoding="utf-8")
    if new or updated or removed:
        subprocess.run([sys.executable, str(ROOT / "launcher/profiles/gen_profiles.py")], check=True)
        rebuild_tables(ROOT)
    print(f"{len(new)} new, {len(updated)} updated and {len(removed)} removed files from cleanyourtv"
          + (f"; new TVs: {', '.join(tvs)}" if tvs else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
