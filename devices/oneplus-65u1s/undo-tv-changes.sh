#!/bin/bash
# Undo the ad-removal changes on the OnePlus TV (65U1S).
# Re-enables Google's home app and the 9 packages, and turns private DNS back off (it was off before).
# Projectivy and the cleanyourtv glass launcher (com.hushtile.home) stay installed;
# uninstall them from Settings > Apps if you want.
# Needs USB debugging on the TV. Run: bash undo-tv-changes.sh [tv-ip]
cd "$(dirname "$0")"
TV="${1:?give the TV's address: bash $0 <tv ip>}:5555"
ADB="${ADB:-adb}"   # set ADB=/path/to/adb if adb is not on PATH
$ADB connect "$TV"
# Bring back Google's home screen and make it the default again.
$ADB -s "$TV" shell pm enable com.google.android.tvlauncher
$ADB -s "$TV" shell cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity
for p in com.google.android.tvrecommendations com.oneplus.tv.launcher com.heytap.mcs \
         com.heytap.tv.remotediagnosing com.google.android.feedback android.autoinstalls.config.OnePlus \
         com.coloros.relax.tv com.oneplus.tv.userguide com.heytap.tv.assistant.oneplus; do
  $ADB -s "$TV" shell pm enable "$p"
done
$ADB -s "$TV" shell settings put global private_dns_mode off
$ADB -s "$TV" shell settings delete global private_dns_specifier
echo "Done. Restart the TV."
