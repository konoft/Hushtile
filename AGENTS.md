# AGENTS.md

Instructions for coding agents (Claude Code, Codex, Gemini CLI, Cursor, Copilot and others) working in this
repository. People should start with [README.md](README.md) and [CONTRIBUTING.md](CONTRIBUTING.md).

Hushtile is built on [cleanyourtv](https://github.com/Gathin23/cleanyourtv) (MIT). The TV-cleaning procedure, its
safety rules, the tools and the tested-TV folders come from there; the four-pane launcher, the device profiles and
the Wikimedia wallpapers are Hushtile's.

## First: which job are you doing?

| You were asked to | Do this |
|---|---|
| Clean up a TV: debloat it, install Hushtile, push a theme | Read [PROMPT.md](PROMPT.md) and follow it exactly, on the real TV. Use the tools in `tools/` and the launcher in `launcher/` rather than writing new ones; the only files you change are a theme's `config.json` for this TV. Do not edit the repo's docs, tests or other code unless the person asks you to. |
| Change this repo: a tested TV, a fix, a theme, docs | Skip the next section and read on from [Repo map](#repo-map). |

### Cleaning a TV: the rules that are not negotiable

[PROMPT.md](PROMPT.md) is the procedure. Its safety rules, in brief:

- **Disable, never uninstall.** `pm disable-user --user 0`, one package per shell call. No
  `pm uninstall`, no root, nothing flashed. Every step must be undone by `tools/restore.sh`.
- **Back up first.** Package list, disabled list, `getprop` and settings into `backup/` before the first
  change. `tools/disable.sh` also writes its own record in `~/.cleanyourtv/`; keep the list you applied.
- **Leave alone what the TV needs to work:** the TV app and input services behind HDMI and the tuner,
  the input/source menu, remote and Bluetooth pairing, the on-screen keyboard, Play Store and Play
  Services. When a package name says nothing, read `dumpsys package <name>`, then ask.
- **Never disable Android's core packages:** the permission controller, package installer, ext
  services/shared and module metadata (`com.android.*` and `com.google.android.*`),
  `com.google.android.overlay.modules*`, `com.android.providers.*` (calendar, contacts, user dictionary
  and blocked numbers aside), `com.android.systemui`, `com.android.shell`, `com.android.phone`,
  `com.android.settings`, `com.android.tv.settings`, `android`. Without them Android may not boot ([cleanyourtv#884](https://github.com/Gathin23/cleanyourtv/issues/884));
  `tools/disable.sh`, `disable.ps1` and `check.py --fix` refuse such a list, with no override.
- **Never start a factory or test package** (`com.cvte.tv.hkcfac`, `mediatek.factorymenu.ui` and the like),
  not even to look. On some boards that flips a factory-mode flag the shell cannot undo.
- **Keep the Home guard.** The stock launcher is disabled only after the new launcher is installed and
  confirmed as Home (`dumpsys activity activities | grep mFocusedApp`). On Google TV, check
  `dumpsys wifi | grep -o 'cname=[^ ]*'` before disabling setupwraith: if it owns the saved Wi-Fi
  network, disabling it takes the TV off Wi-Fi and ADB. `tools/disable.sh` and
  `launcher/build-and-install.sh` already enforce this. Use `FORCE_HOME=1` or `FORCE_SETUPWRAITH=1` only in
  the situation the script's own message describes, and only with the person's agreement.
  - On a CVTE board (any `com.cvte.*` package), leave its stock launcher (`com.cvte.tv.launcher.*`) enabled
    unless the `examples/` folder for that exact board disables it. Make the new launcher Home with
    `cmd package set-home-activity`, reboot once, and confirm Home and ADB come back before anything else.
    Don't enable a remote-key helper in the same pass: a JVC set hung on its boot animation with ADB gone
    after both were done before one reboot ([cleanyourtv#741](https://github.com/Gathin23/cleanyourtv/issues/741)).
- **Ask before anything the family uses.** Streaming apps, the voice assistant, Chromecast, Miracast and
  the firmware updater go into one checklist question; disable only what the person does not tick.
- **Disable in small batches** and screenshot between them (`tools/tv.py --screenshot shots/01.png`).
  Live TV, HDMI and some streaming apps give empty screenshots; that is the TV, not damage.
- **An injected key is not proof** for a remote button that goes through an accessibility helper: ask the
  person to press the real button. `am force-stop com.hushtile.home` switches those helpers off.
- **Close ADB afterwards.** USB debugging and Network/ADB debugging off, authorisations revoked, reboot,
  then `tools/adb-closed.py <tv ip>` must report the port closed. Port 5555 is plaintext.
- **Stop and say so** when the TV refuses: `pm disable-user` refused for every package, a sideload that
  fails verification, or an SDK below the launcher's `minSdk` 26. Do not retry in a loop, and do not lower
  `minSdk` or hand over an APK built elsewhere.
- **Not an Android TV?** No ADB port at all, or the set identifies as Tizen, webOS, Roku OS, VIDAA or
  SmartCast: stop, nothing here applies. Send the person to https://cleanyour.tv/not-android, which turns
  off the tracking with the remote.

The person's backups (`backup/`), screenshots (`shots/`), notes (`work/`) and the ADB key
(`tools/adbkey/`) are listed in `.gitignore` and must never be committed. If the person wants to
share their TV's list afterwards, follow [`docs/workflows/tv-list.md`](docs/workflows/tv-list.md).

## Repo map

| Path | What it is |
|---|---|
| `PROMPT.md` | The prompt people paste into their agent to clean a TV. Safety rules live here. |
| `README.md` | Front page: what Hushtile does, the three settings layers, install. |
| `CONTRIBUTING.md` | How to add a tested TV, report a bug or send a fix. |
| `SECURITY.md` | Private vulnerability reporting. |
| `CODE_OF_CONDUCT.md` | Includes the rule against publishing device identifiers, Wi-Fi names and IPs. |
| `launcher/` | Hushtile. App ID `com.hushtile.home`; the Java package stays `com.gathin.tvhome`, as do the add-on card and intent names, so cleanyourtv's add-ons keep working. Plain Java, `minSdk` 26, `targetSdk` 34. |
| `launcher/app/src/main/assets/hushtile.json` | Hushtile's built-in look: panes, tools, glass, glow, wallpaper rotation. |
| `launcher/app/src/main/assets/profiles.json` | Generated by `launcher/profiles/gen_profiles.py` from `examples/`: never edit it by hand. |
| `examples/` | One folder per tested TV: `debloat.txt`, `tv.json`, sometimes `config.json` and a `README.md`. |
| `tools/sync_upstream.py` | Brings in cleanyourtv's newly tested TVs every Monday (`.github/workflows/sync-upstream.yml`): copies `examples/` and `docs/devices/`, links their issue numbers, rebuilds the profiles. `tools/upstream_manifest.txt` records which files came from cleanyourtv; folders only here are never touched. |
| `devices/` | The two TVs Hushtile was built on, with their themes and undo scripts. |
| `addons/` | Tool apps shown in the Tools pane, each its own Gradle project, never built into the launcher. |
| `themes/` | Looks and wallpapers, each with its credits. |
| `tools/` | The ADB client (`tv.py`) and the scripts: `disable.sh`, `restore.sh`, `theme.sh`, `check.py` and others. Each script's usage is in its first lines. |
| `tests/` | Python `unittest` suite for the lists, themes, add-ons, the Home guard and the tools against fake TVs. |
| `docs/` | Troubleshooting, the add-on card contract (`docs/modules.md`), theme keys (`docs/switches.md`) and per-TV notes. |

## How Hushtile decides what a TV gets

Three layers, each over the one before (`DeviceProfile.java`):

1. `assets/hushtile.json`, Hushtile's look.
2. The tested TV whose brand and codenames (device, product, board, model) match, from `profiles.json`. Only the
   keys about how a TV works are carried (Live TV app, Source panel, remote keys, apps to hide), never a look. With
   no match, a package rule applies: a key every tested TV with that vendor app agreed on.
3. A theme pushed over ADB (`tools/theme.sh`, `devices/*/push-theme.sh`).

After changing anything in `examples/`, run `python3 launcher/profiles/gen_profiles.py` and commit the new
`profiles.json` with it. `DeviceProfileTest` must still pass.

## Before sending a change

| Check | Command or rule |
|---|---|
| Tests | `python3 -m unittest discover -s tests` from the repo root. |
| Launcher | JDK 17, Android SDK platform 34, build-tools 34.0.0. In `launcher/`: `./gradlew --no-daemon testReleaseUnitTest lintRelease assembleRelease`. Lint stays at zero errors. |
| Size | `launcher/app/build/outputs/apk/release/app-release.apk` under 589,824 B (576 KB). |
| Real TV | Launcher changes are tried on a real TV: D-pad from every edge, and the real remote for any button an accessibility helper handles. Say which TV and Android version in the description. |
| Text | Every label is crisp at 720p and 1080p: medium weight, near-white, an edge shadow, on a dark pill. |
| Screenshots | Every visual change has a screenshot from the TV, with posters and personal details blurred. |

## Hard rules

- **No secrets or identifiers** in commits, lists or screenshots: no IP addresses, Wi-Fi names, account emails,
  serial numbers, Android IDs, MAC addresses or ADB keys. Use `<tv ip>` in docs. Blur Wi-Fi names, people's
  names, cities and streaming posters before a screenshot goes anywhere public.
- **No prebuilt APKs or binaries** in the tree. Releases carry the built app.
- **No uninstalling.** No `pm uninstall` in a list or a script; everything is disabled and `tools/restore.sh` undoes it.
- **The network is used for one thing:** pictures from Wikimedia Commons, only when the person asks for them in
  Launcher settings. No analytics, telemetry, ads, accounts or polling.
- **No changes to the safety rules** in `PROMPT.md`, the Home guard in `tools/disable.sh`, `tools/check.py` and
  `launcher/build-and-install.sh`, or the list of packages a TV needs, without an issue first.
- **No root**, no getting around app-store protection, no builds others cannot reproduce from this repo.
- **Don't lower `minSdk`** (26).
- **Credits stay.** cleanyourtv's copyright line in `LICENSE`, the add-on authors' names and the wallpaper credits
  are kept in every change.

## Commit style

Titles are `area: what changed`, in plain words: `examples: add Nokia ATV R1`, `Launcher: Wi-Fi shows its signal`.
The area is the folder the change is in. The body says what changed and the TV it was tried on.
