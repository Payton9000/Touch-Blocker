#!/usr/bin/env bash
# Sweeps every display-cutout emulation against every rotation and reports whether the overlay
# still blocks exactly the right pixels.
#
# For each (cutout, rotation) pair it prints:
#   windows   how many overlay windows the service has up
#   inside    blocked/attempted taps on window centres and corners  -> must be all blocked
#   outside   blocked/attempted taps just outside every window      -> must be 0 blocked
set -uo pipefail

ADB="${ADB:-adb}"
PROBE="$(dirname "$0")/overlay_probe.sh"
CUTOUTS=(none corner double hole tall waterfall)
ROTATIONS=(0 1 2 3)

apply_cutout() {
  local name="$1"
  for c in corner double hole tall waterfall emu01; do
    $ADB shell cmd overlay disable "com.android.internal.display.cutout.emulation.$c" >/dev/null 2>&1
  done
  if [ "$name" != "none" ]; then
    $ADB shell cmd overlay enable "com.android.internal.display.cutout.emulation.$name" >/dev/null 2>&1
  fi
  # Toggling a cutout overlay restarts SystemUI, which briefly takes the foreground and can
  # relaunch the app. Wait for the launcher to settle before probing, or taps land nowhere.
  sleep 6
  settle_to_launcher
}

set_rotation() {
  local want="$1"
  $ADB shell settings put system user_rotation "$want" >/dev/null 2>&1
  # Poll until the window manager reports the rotation it was asked for; a fixed sleep raced with
  # the animation and produced taps against stale window positions.
  local expected="ROTATION_$((want * 90))"
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    [ "$("$PROBE" rotation)" = "$expected" ] && break
    sleep 1
  done
  sleep 2
  settle_to_launcher
}

# Puts the launcher in front and waits for it, so probes are never swallowed by a system dialog
# or by the app's own immersive window (which hides the system bars).
# Brings the launcher forward once, then waits passively. Sending HOME repeatedly in a loop ANRs
# the launcher, which then steals focus with an "isn't responding" dialog and swallows every tap.
settle_to_launcher() {
  $ADB shell input keyevent KEYCODE_HOME >/dev/null 2>&1
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    local focus
    focus=$($ADB shell dumpsys window 2>/dev/null | grep -oE "mCurrentFocus=Window\{[^}]*\}" | head -1)
    case "$focus" in
      *NexusLauncher*) return 0 ;;
      *"Not Responding"*|*ANR*)
        $ADB shell input keyevent KEYCODE_BACK >/dev/null 2>&1 ;;
    esac
    sleep 2
  done
  return 0
}

# The emulator can doze between adb commands during a long sweep; a dozing screen swallows
# `input tap` and looks exactly like a blocking failure. Keep it awake and verify before probing.
ensure_awake() {
  $ADB shell svc power stayon true >/dev/null 2>&1
  local state
  state=$($ADB shell dumpsys power 2>/dev/null | grep -oE "mWakefulness=[A-Za-z]+" | head -1)
  if [ "$state" != "mWakefulness=Awake" ]; then
    $ADB shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
    sleep 2
    $ADB shell wm dismiss-keyguard >/dev/null 2>&1
    sleep 1
  fi
}

printf '%-11s %-4s %-8s %-9s %-9s %s\n' CUTOUT ROT WINDOWS INSIDE OUTSIDE SCREEN
for cut in "${CUTOUTS[@]}"; do
  apply_cutout "$cut"
  for rot in "${ROTATIONS[@]}"; do
    set_rotation "$rot"
    ensure_awake
    windows=$("$PROBE" count)
    if [ "$windows" -eq 0 ]; then
      printf '%-11s %-4s %-8s %-9s %-9s %s\n' "$cut" "$rot" 0 "NO-WINDOWS" "-" "$("$PROBE" screen | tr ' ' 'x')"
      continue
    fi
    inside=$("$PROBE" taps)
    outside=$("$PROBE" outside)
    printf '%-11s %-4s %-8s %-9s %-9s %s\n' \
      "$cut" "$rot" "$windows" "$inside" "$outside" "$("$PROBE" screen | tr ' ' 'x')"
  done
done

apply_cutout none
set_rotation 0
