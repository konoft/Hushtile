# Security

Hushtile runs as the home screen on people's TVs, and its tools talk to TVs over ADB, so a security bug here can
reach living rooms. Please report anything you find privately, not in a public issue.

## How to report

Use GitHub's private vulnerability reporting: open the
[Security tab](https://github.com/konoft/Hushtile/security) and choose **Report a vulnerability**. Only the
maintainer sees the report.

Include what is affected (the launcher, a tool in `tools/`, the prompt, a debloat list), the steps to reproduce it,
and what an attacker could do with it.

## What counts

- The launcher (`com.hushtile.home`) doing something it shouldn't: reaching the network for anything but the
  Wikimedia Commons pictures you asked for, exposing data to other apps, or being made to open something it
  shouldn't.
- The tools (`tools/tv.py`, `disable.sh`, `restore.sh`, `theme.sh`, `recovery.py`) running commands on a device or
  computer they weren't pointed at, or mishandling paths and input.
- A debloat list or the prompt leading to a TV that can't be restored with `tools/restore.sh`.
- Anything in a pull request that runs code on a contributor's or maintainer's machine.

ADB itself is powerful by design. Turning on USB or network debugging lets any computer on your network ask to
connect, and the TV asks you to allow it. Turn debugging off when you're done.

A problem in code Hushtile shares with cleanyourtv (the tools, the lists, the prompt) may affect it too: say so, and
it will be passed on.

## What happens next

You'll get a reply within a few days. Fixes are released with credit to you, unless you'd rather stay anonymous.
