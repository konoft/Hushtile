#!/usr/bin/env python3
"""Builds launcher/app/src/main/assets/profiles.json from the tested TV folders in examples/.

Hushtile cannot be tried on every TV, so it carries what the cleanyourtv community found on each one: the TV-specific
keys of each folder's config.json (Live TV app, Source panel, remote keys, apps to hide), never its look, which stays
Hushtile's own (assets/hushtile.json). At start DeviceProfile picks the folder whose brand and codenames match the TV,
and on a TV with no folder it uses the package rules: a key every tested TV with that vendor app agreed on.

Usage: python3 launcher/profiles/gen_profiles.py   (from the repo root; run again whenever examples/ changes)
"""
import collections
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[2]
EXAMPLES = ROOT / "examples"
OUT = ROOT / "launcher/app/src/main/assets/profiles.json"

# The keys about how this TV works. Looks (dock, columns, wallpaper, glow...) are left to Hushtile.
DEVICE_KEYS = [
    "live_tv_package", "live_tv_action", "live_tv_input_id",
    "source_panel_service", "source_panel_action", "source_panel_extra", "coocaa_sources",
    "input_action", "input_sources", "input_uri_scheme", "inputs_connected_only", "handle_view_inputs",
    "settings_key_keycodes", "settings_key_panel", "input_key_keycodes", "app_keys", "key_filter",
    "stock_home_redirect", "hidden_apps", "app_labels", "reduced_motion", "power_on_home", "power_on_home_always",
]

# Words in a TV's description that say nothing about which TV it is.
STOP = {
    "tv", "android", "google", "smart", "streaming", "box", "stick", "projector", "laser", "edition", "series",
    "mediatek", "realtek", "amlogic", "rockchip", "allwinner", "hisilicon", "novatek", "mstar", "mtk", "rtk",
    "india", "china", "europe", "eu", "us", "uk", "usa", "latin", "america", "north", "south", "australia", "japan",
    "russia", "global", "asia", "middle", "east", "germany", "france", "italy", "spain", "brazil", "korea",
    "led", "qled", "oled", "mini", "uhd", "fhd", "hdr", "hd", "4k", "8k", "2k", "inch", "the", "and", "with", "also",
    "update", "picture", "frame", "panel", "gb", "ram", "model", "models", "new", "pro", "max", "plus", "ultra",
    "firmware", "board", "build", "version", "tested", "region", "variant", "same",
}


def tokens(text):
    """Codenames and model numbers from a description like 'OnePlus TV (C00, Cebu, MediaTek m7632), India'."""
    out = set()
    for word in re.split(r"[\s,;()/+]+", text.lower()):
        word = word.strip(".:'\"[]")
        if (len(word) < 3 or word in STOP or re.fullmatch(r"(19|20)\d\d", word) or re.fullmatch(r"[\d.]+(gb|mb)?", word)
                or re.fullmatch(r"\d+x\d+", word)):
            continue
        out.add(word)
    return out


def brand_words(brand):
    words = {w for w in re.split(r"[\s/]+", brand.lower()) if len(w) >= 2 and w not in STOP}
    return sorted(words)


def package_of(value):
    return value.split("/")[0] if isinstance(value, str) else ""


def main():
    profiles, votes = [], collections.defaultdict(collections.Counter)
    for folder in sorted(p for p in EXAMPLES.iterdir() if p.is_dir()):
        try:
            tv = json.loads((folder / "tv.json").read_text())
        except (OSError, ValueError):
            continue
        cfg = {}
        if (folder / "config.json").is_file():
            try:
                full = json.loads((folder / "config.json").read_text())
                cfg = {k: full[k] for k in DEVICE_KEYS if k in full and full[k] not in ("", [], None)}
            except ValueError:
                pass
        if not cfg:
            continue   # nothing TV-specific to carry
        toks = tokens(tv.get("tv", "")) | tokens(folder.name.replace("-", " "))
        toks -= set(brand_words(tv.get("brand", "")))
        profiles.append({"id": folder.name, "brand": brand_words(tv.get("brand", "")), "tokens": sorted(toks), "config": cfg})
        # Package rules: the vendor apps behind Live TV and the Source panel, by what their TVs used.
        for key in ("live_tv_package", "source_panel_service"):
            pkg = package_of(cfg.get(key, ""))
            if not pkg:
                continue
            rule = {key: cfg[key]}
            if key == "source_panel_service":
                for extra in ("source_panel_action", "source_panel_extra"):
                    if extra in cfg:
                        rule[extra] = cfg[extra]
            votes[(pkg, key)][json.dumps(rule, sort_keys=True)] += 1
    rules = []
    for (pkg, key), counter in sorted(votes.items()):
        (best, n), = counter.most_common(1)
        if n == sum(counter.values()):   # only where every TV with that app agreed
            rules.append({"needs": pkg, "config": json.loads(best), "tvs": n})
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps({"source": "cleanyourtv examples/ (MIT)", "profiles": profiles, "package_rules": rules},
                              indent=1, sort_keys=True) + "\n")
    print(f"{len(profiles)} TV profiles, {len(rules)} package rules -> {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
