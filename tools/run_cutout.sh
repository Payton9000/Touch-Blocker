#!/usr/bin/env bash
# Probes ONE cutout emulation across all four rotations.
#
# Deliberately one cutout per invocation: each cutout toggle restarts SystemUI, and chaining all
# six in a single long-lived script repeatedly destabilised the emulator (launcher ANRs, a wedged
# window manager). Running them separately keeps every result trustworthy.
#
# Usage: run_cutout.sh <none|corner|double|hole|tall|waterfall>
set -uo pipefail

ADB="${ADB:-adb}"
PROBE="$(dirname "$0")/overlay_probe.sh"
CUT="${1:?usage: run_cutout.sh <none|corner|double|hole|tall|waterfall>}"

for c in corner double hole tall waterfall emu01; do
  $ADB shell cmd overlay disable "com.android.internal.display.cutout.emulation.$c" >/dev/null 2>&1
done
[ "$CUT" != "none" ] \
  && $ADB shell cmd overlay enable "com.android.internal.display.cutout.emulation.$CUT" >/dev/null 2>&1
sleep 8

# SystemUI restarting can take the app's overlay down with it; make sure it is back up.
$ADB shell svc power stayon true >/dev/null 2>&1
[ "$("$PROBE" count)" -eq 0 ] && "$PROBE" restart >/dev/null
$ADB shell input keyevent KEYCODE_HOME >/dev/null 2>&1
sleep 4

printf '%-10s %-4s %-8s %-9s %-9s %-9s %s\n' CUTOUT ROT WINDOWS INSIDE OUTSIDE SHADOWED SCREEN
for rot in 0 1 2 3; do
  $ADB shell settings put system user_rotation "$rot" >/dev/null 2>&1
  want="ROTATION_$((rot * 90))"
  for _ in $(seq 1 12); do
    [ "$("$PROBE" rotation)" = "$want" ] && break
    sleep 1
  done
  sleep 3

  windows=$("$PROBE" count)
  if [ "$windows" -eq 0 ]; then
    "$PROBE" restart >/dev/null
    $ADB shell input keyevent KEYCODE_HOME >/dev/null 2>&1
    sleep 3
    windows=$("$PROBE" count)
  fi
  printf '%-10s %-4s %-8s %-9s %-9s %-9s %s\n' \
    "$CUT" "$rot" "$windows" "$("$PROBE" taps)" "$("$PROBE" outside)" \
    "$("$PROBE" shadowed)" "$("$PROBE" screen | tr ' ' 'x')"
done
