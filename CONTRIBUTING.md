# Contributing

Thanks for helping more TVs lose their ads. Working with a coding agent? Point it at [AGENTS.md](AGENTS.md): the
same rules, written for agents.

## Where does my change go?

| Your change | Where it goes |
|---|---|
| A TV you tested: its ad list and settings | `examples/<brand>-<model>/` (section 1) |
| A bug in Hushtile | An issue first, with the TV, Android version and what you pressed |
| A fix or a small feature in the launcher | A pull request (section 2) |
| A new look: colours, wallpaper, dock | `themes/<name>/`, with a screenshot from a real TV |
| A tool tile that can be its own small app | `addons/<name>/`, as a card that never touches `launcher/` (`docs/modules.md`) |

A tested-TV list is just as welcome upstream in [cleanyourtv](https://github.com/Gathin23/cleanyourtv), where
Hushtile's tested TVs come from; one sent there reaches Hushtile the next time its `examples/` are brought in.

## 1. Add a tested TV

1. Create `examples/<brand>-<model>/debloat.txt`, one package per line, grouped under comments that say what each
   group is. Put the exact model, Android version and firmware build in the header (`tools/tv.py --probe` prints
   them). Only list packages you disabled and lived with for a few days; note anything you had to re-enable.
2. Add `tv.json` beside it: `tv` (the model), `os`, `brand` and `notes` (one line on what the list disables and
   keeps). Copy an existing folder's.
3. If Hushtile needed settings for this TV (its Live TV app, how Source opens, remote keys, apps to hide), add them
   as `config.json`. Only the keys about how the TV works are used; the look stays Hushtile's.
4. Run `python3 launcher/profiles/gen_profiles.py` and commit the new `profiles.json` with your folder.

Never list packages to *uninstall*: everything here disables with `pm disable-user`, which the TV's own settings
can undo. Android's core packages are never bloat either (the permission controller, package installer, systemui,
shell, settings, tv.settings, `android` and the others [PROMPT.md](PROMPT.md) names): a TV that loses one may never
boot again, and `tools/disable.sh` refuses such a list.

## 2. Change the launcher

- Build and test as [AGENTS.md](AGENTS.md#before-sending-a-change) says: tests, lint at zero errors, the APK under
  576 KB.
- Try it on a real TV and say which one. Press the D-pad from every edge, and use the real remote for any button an
  accessibility helper handles.
- Keep text crisp, like the TV's own Settings: medium weight, near-white, an edge shadow, on a dark pill.
- Anything one TV needs goes in its `examples/` folder or a theme, not in code.
- A screenshot from the TV for every visual change, with posters, Wi-Fi names and anything personal blurred.

## 3. Privacy

No IP addresses, Wi-Fi names, account emails, serial numbers, Android IDs, MAC addresses or ADB keys in a commit,
a list or a screenshot. `backup/`, `shots/` and `work/` are in `.gitignore` for this reason; keep them there.

Contributions are released under the repo's MIT licence.
