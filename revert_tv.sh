#!/bin/zsh
# Undoes the optimizations applied to a Hisense Android TV over adb.
#
# Configuration: set TV_IP in the .env file next to this script (copy .env.example to .env),
#                or export TV_IP before running it.
#
# Usage: ./revert_tv.sh                -> re-enables everything
#        ./revert_tv.sh com.vt.alexa   -> re-enables only that app

SCRIPT_DIR=${0:A:h}
if [[ -f "$SCRIPT_DIR/.env" ]]; then
  set -a; source "$SCRIPT_DIR/.env"; set +a
fi
if [[ -z "$TV_IP" ]]; then
  echo "TV_IP is not set. Copy .env.example to .env and put your TV's IP address in it." >&2
  exit 1
fi

ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
TV="$TV_IP:${TV_PORT:-5555}"

PACKAGES=(
  # Hisense
  com.vt.alexa com.vt.tv.aiot com.vt.tv.aiotprotocolapp com.vt.tv.aiotsystemservice
  com.vt.tv.smartsense com.vt.tv.smartsense.ui com.vt.tv.ai_sense com.vt.tv.smartservice
  com.vt.storemode com.vt.tv.b2b com.vt.tv.dlna com.vt.hishow com.vt.tv.mobiledeviceconnectiontips
  com.vt.tv.calmanconnector com.hisense.odinbrowser com.vidaa.oemtabcontentprovider
  net.vidaatv.hisense.android.tv
  # Apps
  com.univision.prendetv com.earthflare.android.radioparadisewidget.gpv2
  # AirPlay
  com.mediatek.AirplayAPK com.mediatek.airplaydaemon com.mediatek.partner.airplay_sys
  com.vt.tv.airplaykeycheck
  # Google ambient screensaver (ads)
  com.google.android.apps.tv.dreamx
  # Google TV launcher
  com.google.android.apps.tv.launcherx
  # Google setup app (its recovery screen can take the place of home)
  com.google.android.tungsten.setupwraith
)

"$ADB" connect "$TV" >/dev/null
if [[ -n "$1" ]]; then PACKAGES=("$@"); fi
for p in $PACKAGES; do echo -n "$p: "; "$ADB" -s "$TV" shell pm enable --user 0 $p; done

if [[ -z "$1" ]]; then
  "$ADB" -s "$TV" shell settings put global window_animation_scale 1.0
  "$ADB" -s "$TV" shell settings put global transition_animation_scale 1.0
  "$ADB" -s "$TV" shell settings put global animator_duration_scale 0.6
  "$ADB" -s "$TV" shell device_config delete activity_manager max_cached_processes
  echo "Animations and process limit restored."
fi
