#!/usr/bin/env bash
# Verifies overlay blocking across screen shapes, booting a fresh emulator per shape.
#
# Why a reboot per shape instead of `wm size`: an override changes the logical resolution but not
# the panel's hardware modes, so the profile fingerprint (which includes the largest mode) no longer
# matched what the app computed, and repeated resize+rotate cycles wedged the emulator's input
# subsystem -- `dumpsys input` reported zero windows while the window manager still listed seven.
# Booting with `-skin WxH` gives a genuinely different display and keeps every run independent.
#
# Usage: boot_matrix.sh <profile> [profile ...]      (see PROFILES below)
set -uo pipefail

SDK="${SDK:-/e/Android Studio SDKs/Android9}"
EMU="$SDK/emulator/emulator.exe"
export PATH="$SDK/platform-tools:$PATH"
AVD="${AVD:-Medium_Tablet}"
SERIAL="${SERIAL:-emulator-5554}"
ADB="adb -s $SERIAL"
export ADB
PROBE="$(dirname "$0")/overlay_probe.sh"
APK="$(dirname "$0")/../app2/build/outputs/apk/debug/app2-debug.apk"
PKG=com.payton.touchblocker

# name|WxH|density|cutout
PROFILES=(
  "plain_fhd|1080x1920|420|none"        # classic 16:9 rectangle, no cutout
  "tall_20by9|1080x2400|440|none"       # modern tall phone
  "notch|1080x2280|440|tall"            # teardrop / eyebrow notch
  "holepunch|1080x2400|440|hole"        # centred hole-punch camera
  "corner_cam|1080x2340|440|corner"     # corner hole-punch
  "dual_cutout|1080x2340|440|double"    # cutout top and bottom
  "waterfall|1440x3200|560|waterfall"   # curved-edge flagship
  "budget_hd|720x1280|320|none"         # low-density budget phone
  "tablet|1600x2560|320|none"           # tablet, portrait-native
  "compact|1080x2160|440|none"          # compact 18:9
)

kill_emulator() {
  adb -s "$SERIAL" emu kill >/dev/null 2>&1
  sleep 6
  find "$HOME/.android/avd/$AVD.avd" -maxdepth 1 -name "*.lock" -exec rm -rf {} \; 2>/dev/null
}

boot_emulator() {
  local size="$1" density="$2"
  kill_emulator
  # -wipe-data is required, not tidiness: repeatedly killing this API 29 image mid-boot left its
  # /data partition corrupt, after which `pm install` reported Success while creating no data dir
  # and `am start` insisted MainActivity did not exist. A wipe per profile keeps each run honest.
  ( cd "$SDK/emulator" && nohup ./emulator.exe -avd "$AVD" \
      -no-snapshot-load -no-snapshot-save -no-boot-anim -wipe-data \
      -skin "$size" -prop "qemu.sf.lcd_density=$density" \
      >/tmp/boot_matrix_emu.log 2>&1 & disown )
  # Readiness is judged by the package manager and window manager actually answering, not by
  # sys.boot_completed: on this image that property is never set, so waiting for it timed out
  # every time while the device was in fact fully usable (installs and `wm size` both worked).
  local ready=0
  for _ in $(seq 1 75); do
    if [ "$($ADB shell pm list packages 2>/dev/null | wc -l)" -gt 100 ] \
       && $ADB shell wm size >/dev/null 2>&1; then
      ready=1; break
    fi
    sleep 8
  done
  if [ "$ready" -ne 1 ]; then echo "emulator never became ready" >&2; return 1; fi
  # The window manager keeps settling after that; probing too early reads stale geometry.
  sleep 30
  $ADB shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
  $ADB shell wm dismiss-keyguard >/dev/null 2>&1
  sleep 3
}

prepare_app() {
  local cutout="$1"
  # Installing while the package manager is still coming up fails silently, which then looks
  # like "the overlay never started". Retry until an install actually sticks.
  local installed=0
  for _ in $(seq 1 10); do
    $ADB install -r "$APK" >/dev/null 2>&1 \
      || { $ADB push "$APK" /data/local/tmp/bm.apk >/dev/null 2>&1
           $ADB shell pm install -r -d -g /data/local/tmp/bm.apk >/dev/null 2>&1; }
    if [ "$($ADB shell pm list packages 2>/dev/null | grep -c $PKG)" -gt 0 ]; then
      installed=1; break
    fi
    sleep 6
  done
  if [ "$installed" -ne 1 ]; then echo "install failed" >&2; return 1; fi
  local uid
  uid=$($ADB shell dumpsys package $PKG 2>/dev/null | grep -oE "userId=[0-9]+" | head -1 | cut -d= -f2)
  $ADB shell appops set $PKG SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1
  [ -n "${uid:-}" ] && $ADB shell appops set --uid "$uid" SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1
  $ADB shell pm grant $PKG android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
  $ADB shell settings put secure immersive_mode_confirmations confirmed >/dev/null 2>&1
  $ADB shell settings put system accelerometer_rotation 0 >/dev/null 2>&1
  $ADB shell settings put system screen_off_timeout 1800000 >/dev/null 2>&1
  $ADB shell svc power stayon true >/dev/null 2>&1
  $ADB shell setprop log.tag.PointOverlayView VERBOSE >/dev/null 2>&1
  $ADB shell setprop log.tag.OverlayService VERBOSE >/dev/null 2>&1
  # Apps whose windows sit above overlay windows, or whose launcher icons a stray tap can
  # launch; either way they take focus and swallow every later probe. Chrome stays enabled
  # because its first-run screen is the rotatable inert host used below.
  for p in com.google.android.gms com.google.android.apps.photos com.google.android.googlequicksearchbox com.android.settings com.android.settings.intelligence com.google.android.apps.messaging com.google.android.gm com.android.vending com.google.android.youtube com.google.android.apps.docs com.google.android.calendar com.google.android.deskclock com.android.camera2 com.google.android.contacts com.android.dialer com.google.android.apps.maps; do
    $ADB shell pm disable-user "$p" >/dev/null 2>&1
  done
  for c in corner double hole tall waterfall emu01; do
    $ADB shell cmd overlay disable "com.android.internal.display.cutout.emulation.$c" >/dev/null 2>&1
  done
  if [ "$cutout" != "none" ]; then
    $ADB shell cmd overlay enable "com.android.internal.display.cutout.emulation.$cutout" >/dev/null 2>&1
    sleep 8
  fi
}

# Requests a rotation and waits for the display to actually report it. Returns 1 when the device
# refuses, so a row can say REFUSED instead of silently re-measuring the previous orientation.
set_rotation() {
  local want="$1" expected="ROTATION_$(( $1 * 90 ))"
  $ADB shell settings put system user_rotation "$want" >/dev/null 2>&1
  for _ in $(seq 1 15); do
    [ "$("$PROBE" rotation)" = "$expected" ] && { sleep 4; return 0; }
    sleep 1
  done
  return 1
}

run_profile() {
  local spec="$1" name size density cutout
  IFS='|' read -r name size density cutout <<<"$spec"

  boot_emulator "$size" "$density"
  prepare_app "$cutout"

  "$PROBE" seed "0.15,0.15 0.85,0.15 0.15,0.85 0.85,0.85 0.5,0.5 0.30,0.70 0.70,0.30" >/dev/null 2>&1
  # `am start` returns before the window is focusable, and the probe reads the overlay switch from
  # the UI, so wait until the activity actually owns focus.
  for _ in $(seq 1 12); do
    $ADB shell am start -n $PKG/.MainActivity >/dev/null 2>&1
    sleep 3
    $ADB shell dumpsys window 2>/dev/null | grep -q "mCurrentFocus.*$PKG/" && break
  done
  "$PROBE" restart >/dev/null 2>&1
  # Chrome's first-run screen rotates freely and does not consume overlay taps. The launcher
  # pins orientation, and Settings walks into a detail page that swallows every later tap.
  $ADB shell pm enable com.android.chrome >/dev/null 2>&1
  $ADB shell am start -a android.intent.action.VIEW -d https://example.com >/dev/null 2>&1
  for _ in $(seq 1 12); do
    $ADB shell dumpsys window 2>/dev/null | grep -q "mCurrentFocus.*chrome" && break
    $ADB shell am start -a android.intent.action.VIEW -d https://example.com >/dev/null 2>&1
    sleep 3
  done

  for rot in 0 1 2 3; do
    if ! set_rotation "$rot"; then
      printf '%-13s %-4s %-10s %-8s %-9s %-9s %s\n' \
        "$name" "$rot" "$("$PROBE" screen | tr ' ' 'x')" "-" "ROT-REFUSED" "-" "$cutout"
      continue
    fi
    $ADB shell cmd statusbar collapse >/dev/null 2>&1
    sleep 1
    local windows
    windows=$("$PROBE" count)
    if [ "$windows" -eq 0 ]; then
      "$PROBE" restart >/dev/null 2>&1
      sleep 3
      windows=$("$PROBE" count)
    fi
    printf '%-13s %-4s %-10s %-8s %-9s %-9s %s\n' \
      "$name" "$rot" "$("$PROBE" screen | tr ' ' 'x')" "$windows" \
      "$("$PROBE" taps)" "$("$PROBE" outside)" "$cutout"
  done
}

printf '%-13s %-4s %-10s %-8s %-9s %-9s %s\n' PROFILE ROT SCREEN WINDOWS INSIDE OUTSIDE CUTOUT
if [ $# -gt 0 ]; then
  for want in "$@"; do
    for spec in "${PROFILES[@]}"; do
      [ "${spec%%|*}" = "$want" ] && run_profile "$spec"
    done
  done
else
  for spec in "${PROFILES[@]}"; do run_profile "$spec"; done
fi
kill_emulator
