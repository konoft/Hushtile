"""Rename cleanyourtv's product name to Hushtile and turn bare issue numbers into links to cleanyourtv's GitHub.

History stays as it happened: the TV lists (examples/) and device notes (docs/devices/) keep the launcher their
contributors actually tested with; credits, links, add-on package names and LICENSE are never changed.
Usage: python3 upstream_names.py <repo root> [paths...]
"""
import re, sys, pathlib

ISSUES = "https://github.com/Gathin23/cleanyourtv/issues/"
TEXT = {".md", ".txt", ".java", ".py", ".sh", ".ps1", ".xml", ".json", ".gradle", ".kt", ".yml", ".yaml"}
HISTORY = ("examples/", "docs/devices/")

def fix_issues(text, markdown):
    # "[#1355](...)" -> "[cleanyourtv#1355](...)"; bare "#1355" -> a link (markdown) or "cleanyourtv#1355".
    text = re.sub(r"\[#(\d{2,4})\]\(", r"[cleanyourtv#\1](", text)
    def bare(m):
        n = m.group(2)
        return m.group(1) + (f"[cleanyourtv#{n}]({ISSUES}{n})" if markdown else f"cleanyourtv#{n}")
    return re.sub(r"(^|[\s(,;])#(\d{2,4})\b(?![\]\w])", bare, text, flags=re.M)

def app_id(text):
    """cleanyourtv's launcher installs as com.gathin.tvhome; Hushtile as com.hushtile.home with the same Java classes.

    pkg/.Activity becomes com.hushtile.home/com.gathin.tvhome.Activity; the bare package becomes com.hushtile.home,
    except class names and the add-on contract strings (category, card, GUARD, action, extra), which keep the old one."""
    text = re.sub(r"com\.gathin\.tvhome/\.", "com.hushtile.home/com.gathin.tvhome.", text)
    text = re.sub(r"com\.gathin\.tvhome(?![./]?(?:category|card|GUARD|action|extra|[A-Z]))", "com.hushtile.home", text)
    return text.replace("com.hushtile.home/com.hushtile.home.", "com.hushtile.home/com.gathin.tvhome.")


def rename(text):
    return text.replace("CleanYourTV", "Hushtile")

def run(root, paths):
    changed = 0
    for p in paths:
        rel = p.relative_to(root).as_posix()
        if rel == "LICENSE" or p.suffix not in TEXT or "/.git/" in f"/{rel}":
            continue
        try:
            old = p.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        new = fix_issues(old, p.suffix == ".md")
        if p.suffix != ".java":
            new = app_id(new)
        if not rel.startswith(HISTORY):
            new = rename(new)
        if new != old:
            p.write_text(new, encoding="utf-8")
            changed += 1
    return changed

if __name__ == "__main__":
    root = pathlib.Path(sys.argv[1]).resolve()
    targets = [pathlib.Path(a).resolve() for a in sys.argv[2:]] or [root]
    files = [f for t in targets for f in ([t] if t.is_file() else t.rglob("*")) if f.is_file()]
    print(run(root, files), "files changed")
