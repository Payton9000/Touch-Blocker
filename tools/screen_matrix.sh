#!/usr/bin/env bash
# Verifies overlay blocking across a broad range of screen shapes on one emulator.
#
# Real devices differ in resolution, aspect ratio, density and cutout shape. `wm size` / `wm density`
# reproduce all of those without needing a separate AVD per phone, and the cutout emulation overlays
# cover notch / hole-punch / waterfall shapes. Each profile is probed at all four rotations.
#
# Usage: screen_matrix.sh [profile-name ...]   (default: every profile)
set -uo pipefail

ADB="${ADB:-adb -s emulator-5554}"
export ADB
PROBE="$(dirname "$0")/overlay_probe.sh"

# name|WxH|density|cutout  -- a cross-section of shipping form factors.
PROFILES=(
  "plain_1080p|1080x1920|420|none"          # classic 16:9 rectangle, no cutout
  "tall_20by9|1080x2400|440|none"           # modern tall phone
  "notch_wide|1080x2280|440|tall"           # wide teardrop/eyebrow notch
  "holepunch|1080x2400|440|hole"            # centred hole-punch camera
  "corner_cam|1080x2340|440|corner"         # corner hole-punch
  "dual_cutout|1080x2340|440|double"        # two cutouts (top + bottom)
  "waterfall|1440x3200|560|waterfall"       # curved-edge flagship
  "low_dpi_hd|720x1280|320|none"            # budget HD phone
  "tablet_4by3|1600x2560|320|none"          # tablet portrait-native
  "small_compact|1080x2160|440|none"        # compact 18:9
)

run_profile() {
  local spec="$1"
  IFS='|' read -r name size density cutout <<<"$spec"

  $ADB shell wm size "$size" >/dev/null 2>&1
  $ADB shell wm density "$density" >/dev/null 2>&1
  for c in corner double hole tall waterfall emu01; do
    $ADB shell cmd overlay disable "com.android.internal.display.cutout.emulation.$c" >/dev/null 2>&1
  done
  [ "$cutout" != "none" ] \
    && $ADB shell cmd overlay enable "com.android.internal.display.cutout.emulation.$cutout" >/dev/null 2>&1
  sleep 8
  $ADB shell svc power stayon true >/dev/null 2>&1

  # Points at fractions well inside the frame so none is clipped by a system bar; edge behaviour is
  # covered separately by the dedicated boundary probe.
  "$PROBE" seed "0.15,0.15 0.85,0.15 0.15,0.85 0.85,0.85 0.5,0.5 0.30,0.70 0.70,0.30" >/dev/null 2>&1
  "$PROBE" restart >/dev/null 2>&1
  # The foreground app must (a) allow rotation -- the tablet launcher pins orientation, so
  # `user_rotation` would be silently ignored and every "rotated" row would really re-measure
  # rotation 0 -- and (b) not navigate when probe taps land on it. Settings met (a) but not (b):
  # taps walked it into a lock-password screen that then swallowed every later tap. The app's own
  # TestBlock screen is a single inert surface, so it satisfies both.
  $ADB shell am start -n com.payton.touchblocker/.TestBlockActivity >/dev/null 2>&1
  sleep 4

  for rot in 0 1 2 3; do
    $ADB shell settings put system user_rotation "$rot" >/dev/null 2>&1
    want="ROTATION_$((rot * 90))"
    for _ in $(seq 1 12); do
      [ "$("$PROBE" rotation)" = "$want" ] && break
      sleep 1
    done
    sleep 3
    $ADB shell cmd statusbar collapse >/dev/null 2>&1

    windows=$("$PROBE" count)
    if [ "$windows" -eq 0 ]; then
      "$PROBE" restart >/dev/null 2>&1
      sleep 2
      windows=$("$PROBE" count)
    fi
    printf '%-15s %-4s %-9s %-8s %-9s %-9s %s\n' \
      "$name" "$rot" "$("$PROBE" screen | tr ' ' 'x')" "$windows" \
      "$("$PROBE" taps)" "$("$PROBE" outside)" "$cutout"
  done
}

printf '%-15s %-4s %-9s %-8s %-9s %-9s %s\n' PROFILE ROT SCREEN WINDOWS INSIDE OUTSIDE CUTOUT
if [ $# -gt 0 ]; then
  for want in "$@"; do
    for spec in "${PROFILES[@]}"; do
      [ "${spec%%|*}" = "$want" ] && run_profile "$spec"
    done
  done
else
  for spec in "${PROFILES[@]}"; do run_profile "$spec"; done
fi

# Restore the emulator's native geometry.
$ADB shell wm size reset >/dev/null 2>&1
$ADB shell wm density reset >/dev/null 2>&1
$ADB shell settings put system user_rotation 0 >/dev/null 2>&1
