#!/bin/bash
# Push theme/ (config.json, wallpaper.jpg and wallpapers/) to the glass launcher and restart Home.
# The same steps as cleanyourtv's tools/theme.sh, using the official adb. Run: bash push-theme.sh [tv-ip]
set -e
cd "$(dirname "$0")"
TV="${1:?give the TV's address: bash $0 <tv ip>}:5555"
ADB="${ADB:-adb}"   # set ADB=/path/to/adb if adb is not on PATH
PKG=com.hushtile.home
DIR=/sdcard/Android/data/$PKG/files
URI=content://$PKG.theme
TMP=/data/local/tmp/cyt-theme
"$ADB" connect "$TV" >/dev/null
sh() { "$ADB" -s "$TV" shell "$@"; }

# Writes one file through the launcher's provider (so the launcher owns it), cp as the fallback.
put() {
  local src=$1 name=$2 stage="$TMP-${2//\//-}"
  "$ADB" -s "$TV" push "$src" "$stage" >/dev/null
  sh "rm -f $DIR/$name 2>/dev/null; content write --uri $URI/$name < $stage 2>/dev/null || cp $stage $DIR/$name; rm -f $stage"
}

# The downloaded pictures' captions, read before the old captions.txt goes.
ONLINE=$(mktemp); CAPS=$(mktemp)
sh "grep '^online-' $DIR/wallpapers/captions.txt 2>/dev/null" > "$ONLINE" || true
# Clear the theme's own pictures, keeping the ones Launcher settings downloaded (online-*.jpg).
sh "mkdir -p $DIR/wallpapers; cd $DIR/wallpapers && for f in *; do case \$f in online-*) ;; *) rm -f \"\$f\";; esac; done" || true
put theme/config.json config.json
[ -f theme/wallpaper.jpg ] && put theme/wallpaper.jpg wallpaper.jpg
if [ -d theme/wallpapers ]; then
  for f in theme/wallpapers/*; do [ "$(basename "$f")" = captions.txt ] || put "$f" "wallpapers/$(basename "$f")"; done
  # captions.txt: the theme's lines plus those of the downloaded pictures already on the TV.
  cat theme/wallpapers/captions.txt "$ONLINE" > "$CAPS"
  put "$CAPS" wallpapers/captions.txt
  rm -f "$CAPS" "$ONLINE"
fi
REV=$(python3 -I -c 'import uuid; print(uuid.uuid4().hex)')
sh "printf '%s' '$REV' > $TMP-theme_revision"
sh "content write --uri $URI/theme_revision < $TMP-theme_revision 2>/dev/null || cp $TMP-theme_revision $DIR/theme_revision; rm -f $TMP-theme_revision"
sh am force-stop $PKG
sh input keyevent KEYCODE_HOME
echo "Theme pushed."
