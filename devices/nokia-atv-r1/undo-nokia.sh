#!/bin/bash
# Undo the changes on the Nokia ATV R1 (SCBC, Android TV 11): re-enables Google's home screen and every package
# that was disabled, makes Google's home the default again and turns private DNS off (it was off before).
# Nothing was disabled before (backup/disabled-before.txt is empty). Hushtile and its add-ons stay installed.
# Needs USB debugging on the TV. Run: bash undo-nokia.sh [tv-ip]
TV="${1:?give the TV's address: bash $0 <tv ip>}:5555"
ADB="${ADB:-adb}"   # set ADB=/path/to/adb if adb is not on PATH
"$ADB" connect "$TV"
for p in com.google.android.tvlauncher com.realtek.leanbacklauncher.partnercustomizer \
         com.apps.function.gdpr com.vewd.core.service com.google.android.feedback com.apps.channels com.apps.eshow \
         com.apps.esticker com.google.android.tvrecommendations android.autoinstalls.config.scbc.device com.apps.clear \
         com.google.android.apps.nbu.smartconnect.tv com.apps.wifihotspot com.android.printspooler com.apps.networkspeed \
         com.apps.timeclock com.google.android.videos com.google.android.play.games; do
  "$ADB" -s "$TV" shell pm enable "$p"
done
"$ADB" -s "$TV" shell cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity
"$ADB" -s "$TV" shell settings put global private_dns_mode off
"$ADB" -s "$TV" shell settings delete global private_dns_specifier
echo "Done. Restart the TV."
