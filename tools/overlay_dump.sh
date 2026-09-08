#!/usr/bin/env bash
# Device-dump parsers used by overlay_probe.sh.
set -uo pipefail
PKG="${PKG:-com.payton.touchblocker}"
ADB="${ADB:-adb}"

ov_rects() {
  # Window-manager positions update on rotation immediately. dumpsys input on API 36 can keep
  # serving the previous orientation's regions for a long time, so tapping those scores real
  # blocking as a failure. Prefer the window manager; fall back to input if it has nothing.
  local wm
  wm=$($ADB shell dumpsys window windows 2>/dev/null | tr -d '\r' \
    | grep "ty=APPLICATION_OVERLAY" \
    | grep -oE "mAttrs=\{\([0-9-]+,[0-9-]+\)\([0-9]+x[0-9]+\)" \
    | sed 's/mAttrs={(//; s/)(/ /; s/x/ /; s/,/ /; s/)$//')
  if [ -n "$wm" ]; then
    echo "$wm"
    return 0
  fi
  $ADB shell dumpsys input 2>/dev/null | tr -d '\r' | awk -v pkg="$PKG" '
    index($0, pkg) > 0 && index($0, "touchableRegion=") > 0 {
      if ($0 ~ (pkg "/")) next
      if (match($0, /touchableRegion=\[[0-9-]+,[0-9-]+\]\[[0-9-]+,[0-9-]+\]/)) {
        r = substr($0, RSTART, RLENGTH)
        gsub(/touchableRegion=\[|\]/, "", r)
        gsub(/\[/, ",", r)
        split(r, v, ",")
        w = v[3] - v[1]; h = v[4] - v[2]
        if (w > 0 && h > 0) print v[1], v[2], w, h
      }
    }'
}

ov_count() {
  ov_rects | awk 'NF==4 {c++} END {print c+0}'
}

screen_wh() {
  local real physical rot w h
  real=$($ADB shell dumpsys display 2>/dev/null | tr -d '\r' \
    | grep -oE "mOverrideDisplayInfo=DisplayInfo\{[^}]*real [0-9]+ x [0-9]+" \
    | grep -oE "real [0-9]+ x [0-9]+" | head -1 | grep -oE "[0-9]+ x [0-9]+")
  if [ -n "$real" ]; then
    echo "${real% x *} ${real##* x }"
    return 0
  fi
  physical=$($ADB shell wm size 2>/dev/null | tr -d '\r' | grep -oE '[0-9]+x[0-9]+' | tail -1)
  rot=$(rotation)
  w=${physical%x*}; h=${physical#*x}
  case "$rot" in
    ROTATION_90|ROTATION_270) echo "$h $w" ;;
    *) echo "$w $h" ;;
  esac
}

rotation() {
  local rot
  rot=$($ADB shell dumpsys display 2>/dev/null | tr -d '\r' \
    | grep -oE "mOverrideDisplayInfo=DisplayInfo\{[^}]*rotation [0-9]+" \
    | grep -oE "rotation [0-9]+$" | grep -oE "[0-9]+" | head -1)
  if [ -n "$rot" ]; then
    echo "ROTATION_$((rot * 90))"
    return 0
  fi
  $ADB shell dumpsys window 2>/dev/null | tr -d '\r' \
    | grep -oE "mRotation=ROTATION_[0-9]+" | grep -oE "ROTATION_[0-9]+" | head -1
}

system_bar_rows() {
  local sw sh
  read -r sw sh < <(screen_wh)
  $ADB shell dumpsys input 2>/dev/null | tr -d '\r' | awk -v sw="$sw" -v sh="$sh" '
    /(StatusBar|Taskbar|NavigationBar)/ {
      if (match($0, /touchableRegion=\[[0-9-]+,[0-9-]+\]\[[0-9-]+,[0-9-]+\]/)) {
        r = substr($0, RSTART, RLENGTH)
        gsub(/touchableRegion=\[|\]/, "", r)
        gsub(/\[/, ",", r)
        split(r, v, ",")
        l = v[1] + 0; t = v[2] + 0; rr = v[3] + 0; b = v[4] + 0
        if (l < 0) l = 0
        if (t < 0) t = 0
        if (rr > sw) rr = sw
        if (b > sh) b = sh
        if (rr > l && b > t) print l, t, rr, b
      }
    }'
}
