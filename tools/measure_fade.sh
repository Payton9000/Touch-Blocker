#!/usr/bin/env bash
# Measures the cost of one overlay start: CPU jiffies and rendered frames across the reveal fade.
# Takes exactly one measurement around exactly one restart so runs are comparable.
set -uo pipefail

ADB="${ADB:-adb}"
PKG=com.payton.touchblocker
PROBE="$(dirname "$0")/overlay_probe.sh"
FADE_WAIT="${FADE_WAIT:-12}"

# Settle first: make sure the overlay is already up and any previous fade has finished.
"$PROBE" restart >/dev/null
sleep "$FADE_WAIT"

pid=$($ADB shell pidof $PKG | tr -d '\r')
[ -z "$pid" ] && { echo "app not running" >&2; exit 1; }

$ADB shell dumpsys gfxinfo $PKG reset >/dev/null 2>&1
j1=$($ADB shell cat /proc/$pid/stat | tr -d '\r' | awk '{print $14+$15}')
t1=$($ADB shell cat /proc/stat | head -1 | tr -d '\r' | awk '{for(i=2;i<=8;i++)s+=$i; print s}')

windows=$("$PROBE" restart)
sleep "$FADE_WAIT"

j2=$($ADB shell cat /proc/$pid/stat | tr -d '\r' | awk '{print $14+$15}')
t2=$($ADB shell cat /proc/stat | head -1 | tr -d '\r' | awk '{for(i=2;i<=8;i++)s+=$i; print s}')
frames=$($ADB shell dumpsys gfxinfo $PKG 2>/dev/null | grep -oE "Total frames rendered: [0-9]+" | grep -oE "[0-9]+")

python3 - "$j1" "$j2" "$t1" "$t2" "${frames:-0}" "$windows" <<'PY'
import sys
j1, j2, t1, t2, frames, windows = (int(float(a)) for a in sys.argv[1:7])
dj, dt = j2 - j1, max(1, t2 - t1)
print(f"windows={windows}  cpu={100*dj/dt:.2f}% of all cores  app_jiffies={dj}  frames={frames}")
PY
