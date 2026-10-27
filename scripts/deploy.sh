#!/usr/bin/env bash
# Build, install over network ADB, and launch Glass TV — one command.
#
#   scripts/deploy.sh 192.168.1.42          # release build (fast, minified — use this on the TV)
#   scripts/deploy.sh 192.168.1.42 debug    # debug build
#   TV_IP=192.168.1.42 scripts/deploy.sh
#
# On the TV first: Settings → System → About → tap "Android TV OS build" 7× → Developer options →
# enable "USB debugging" (and "Wireless debugging" on Android 11+ firmware), then accept the prompt on the TV.
set -euo pipefail
cd "$(dirname "$0")/.."

TV="${1:-${TV_IP:-}}"
VARIANT="${2:-release}"
[[ -z "$TV" ]] && { echo "usage: scripts/deploy.sh <tv-ip[:port]> [release|debug]"; exit 1; }
[[ "$TV" == *:* ]] || TV="$TV:5555"
PKG=dev.glasstv.launcher

command -v adb >/dev/null || { echo "adb not found. Install Android platform-tools and add it to PATH."; exit 1; }

echo "→ Connecting to $TV"
adb connect "$TV" >/dev/null
adb -s "$TV" wait-for-device
echo "  $(adb -s "$TV" shell getprop ro.product.model | tr -d '\r') · Android $(adb -s "$TV" shell getprop ro.build.version.release | tr -d '\r') · API $(adb -s "$TV" shell getprop ro.build.version.sdk | tr -d '\r')"

TASK="assemble${VARIANT^}"
echo "→ ./gradlew $TASK"
./gradlew -q "$TASK"
APK="app/build/outputs/apk/$VARIANT/app-$VARIANT.apk"

echo "→ Installing $APK"
# Debug and release share an applicationId but may differ in signature; reinstall cleanly if needed.
if ! adb -s "$TV" install -r "$APK" 2>/tmp/glasstv-install.err; then
  if grep -q INSTALL_FAILED_UPDATE_INCOMPATIBLE /tmp/glasstv-install.err; then
    echo "  signature changed — uninstalling old build (settings will reset)"
    adb -s "$TV" uninstall "$PKG" >/dev/null
    adb -s "$TV" install "$APK"
  else
    cat /tmp/glasstv-install.err; exit 1
  fi
fi

echo "→ Launching"
adb -s "$TV" shell am start -n "$PKG/.MainActivity" >/dev/null
echo "✓ Done. Home-button setup: docs/home-button.md"
