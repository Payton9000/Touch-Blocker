#!/usr/bin/env bash
# Test harness for TouchBlocker overlay verification on an emulator.
#
# Seeds a known profile straight into the app's SharedPreferences (so point positions are exact
# and reproducible), starts the overlay, then taps the centre and the four corners of every
# overlay window and reports how many taps the overlay actually consumed.
#
# Usage: overlay_probe.sh <command> [args]
set -uo pipefail

PKG=com.payton.touchblocker
PREFS=/data/data/$PKG/shared_prefs/touch_blocker_prefs.xml
ADB="${ADB:-adb}"

# --- helpers ---------------------------------------------------------------

# Counts the app's APPLICATION_OVERLAY windows.
ov_count() {
  $ADB shell dumpsys window windows 2>/dev/null | awk '
    /Window #[0-9]+ Window\{.*'"$PKG"'\}:/ { p = 1 }
    p && /mAttrs=/ { if (/APPLICATION_OVERLAY/) c++; p = 0 }
    END { print c + 0 }'
}

# Prints "x y w h" for each overlay window.
ov_rects() {
  $ADB shell dumpsys window windows 2>/dev/null \
    | grep "ty=APPLICATION_OVERLAY" \
    | grep -oE "mAttrs=\{\([0-9-]+,[0-9-]+\)\([0-9]+x[0-9]+\)" \
    | sed 's/mAttrs={(//; s/)(/ /; s/x/ /; s/,/ /; s/)$//'
}

# Current logical size, accounting for rotation. `wm size` reports the unrotated physical
# size, so a 90/270 turn would otherwise reject in-bounds taps as "offscreen" and send
# out-of-bounds taps into the shorter axis.
screen_wh() {
  # The display's "real" size is already rotated and already reflects any `wm size` override,
  # so it needs no manual swap. Falls back to swapping the physical size by rotation.
  local real
  real=$($ADB shell dumpsys display 2>/dev/null | tr -d '
'          | grep -oE "mOverrideDisplayInfo=DisplayInfo\{[^}]*real [0-9]+ x [0-9]+"          | grep -oE "real [0-9]+ x [0-9]+" | head -1 | grep -oE "[0-9]+ x [0-9]+")
  if [ -n "$real" ]; then
    echo "${real% x *} ${real##* x }"
    return 0
  fi
  local physical rot w h
  physical=$($ADB shell wm size 2>/dev/null | tr -d '
' | grep -oE '[0-9]+x[0-9]+' | tail -1)
  rot=$(rotation)
  w=${physical%x*}; h=${physical#*x}
  case "$rot" in
    ROTATION_90|ROTATION_270) echo "$h $w" ;;
    *) echo "$w $h" ;;
  esac
}


# Reads rotation from the Display record, which reflects the state the window manager is actually
# using. The per-window mRotation can read `undefined` for windows that have not been reconfigured
# yet, so it is not a reliable source.
rotation() {
  # Prefer the display's own record: it is correct even under a `wm size` override, where
  # `dumpsys window displays` prints nothing at all. Falls back to the window-manager value.
  local rot
  rot=$($ADB shell dumpsys display 2>/dev/null | tr -d '
'         | grep -oE "mOverrideDisplayInfo=DisplayInfo\{[^}]*rotation [0-9]+"         | grep -oE "rotation [0-9]+$" | grep -oE "[0-9]+" | head -1)
  if [ -n "$rot" ]; then
    echo "ROTATION_$((rot * 90))"
    return 0
  fi
  $ADB shell dumpsys window 2>/dev/null | tr -d '
'     | grep -oE "mRotation=ROTATION_[0-9]+" | grep -oE "ROTATION_[0-9]+" | head -1
}


# --- profile seeding -------------------------------------------------------

# Writes a profile whose points sit at the given normalized natural anchors.
# Usage: seed "u1,v1 u2,v2 ..."
seed() {
  local anchors="$1" diameter="${2:-30.0}"
  local wh w h
  wh=$($ADB shell wm size | tr -d '\r' | grep -oE '[0-9]+x[0-9]+' | tail -1)
  w=${wh%x*}; h=${wh#*x}
  local nat_w=$(( w < h ? w : h )) nat_h=$(( w < h ? h : w ))
  local dpi
  dpi=$($ADB shell wm density | tr -d '\r' | grep -oE '[0-9]+' | tail -1)
  # `max` comes from the panel's largest hardware mode, NOT from the current (possibly
  # `wm size`-overridden) resolution. Read it from the display's mode list so a seeded profile
  # still matches the fingerprint the app computes; guessing it silently produced a profile the
  # app could never select.
  local modes max_w max_h
  modes=$($ADB shell dumpsys display 2>/dev/null | tr -d '
'           | grep -oE "\{id=[0-9]+, width=[0-9]+, height=[0-9]+"           | grep -oE "width=[0-9]+, height=[0-9]+" | tr -d ' ' | tr ',' ' '           | sed 's/width=//; s/height=//')
  max_w=$nat_w; max_h=$nat_h
  if [ -n "$modes" ]; then
    local best=0 mw mh
    while read -r mw mh; do
      [ -z "${mh:-}" ] && continue
      local area=$((mw * mh))
      if [ "$area" -gt "$best" ]; then
        best=$area
        max_w=$(( mw < mh ? mw : mh )); max_h=$(( mw < mh ? mh : mw ))
      fi
    done <<<"$modes"
  fi
  local key="unknown|natural=${nat_w}x${nat_h}|max=${max_w}x${max_h}|dpi=${dpi}|role=built-in"

  local pts="" id=1
  for a in $anchors; do
    local u=${a%,*} v=${a#*,}
    [ -n "$pts" ] && pts="$pts,"
    pts="$pts{\"id\":$id,\"regionId\":\"full\",\"u\":$u,\"v\":$v,\"naturalU\":$u,\"naturalV\":$v,\"diameterDpOverride\":0.0,\"enabled\":true,\"disabledReason\":\"NONE\",\"timestamp\":1,\"durationMs\":1,\"displayGeneration\":1}"
    id=$((id + 1))
  done

  local doc="{\"schemaVersion\":2,\"profiles\":[{\"id\":\"probe\",\"kind\":\"OUTER\",\"globalDiameterDp\":$diameter,\"fingerprints\":[\"$key\"],\"referenceRegions\":[{\"id\":\"full\",\"bounds\":{\"left\":0,\"top\":0,\"right\":$w,\"bottom\":$h}}],\"referenceUnsafeAreas\":[],\"points\":[$pts]}],\"manualBindings\":{}}"
  # Ampersand must be escaped first, and the replacements must not feed each other:
  # escaping & after " would turn &quot; into &amp;quot;.
  local esc=${doc//&/\&amp;}
  esc=${esc//</\&lt;}
  esc=${esc//\"/\&quot;}

  # A freshly installed app has no shared_prefs directory until it has run once, so launching it
  # (and letting it write its own prefs) is what makes the target path exist.
  $ADB shell am start -n $PKG/.MainActivity >/dev/null 2>&1
  sleep 4
  $ADB shell am force-stop $PKG
  $ADB shell "run-as $PKG mkdir -p /data/data/$PKG/shared_prefs" >/dev/null 2>&1
  local xml="<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name=\"profiles_v2_json\">$esc</string>
    <string name=\"legacy_points_backup_json\">[]</string>
    <boolean name=\"profiles_v2_migration_complete\" value=\"true\" />
    <boolean name=\"profile_prefs_bindings_merged\" value=\"true\" />
    <boolean name=\"overlay_should_be_enabled\" value=\"true\" />
</map>"
  $ADB shell "run-as $PKG sh -c 'cat > $PREFS'" <<<"$xml"
  $ADB shell "run-as $PKG cat $PREFS" >/dev/null || return 1
  echo "seeded ${id_count:-$((id - 1))} point(s) key=$key"
}

# --- overlay control -------------------------------------------------------

start_overlay() {
  $ADB shell am start -n $PKG/.MainActivity >/dev/null 2>&1
  sleep 3
  # The seeded flag already says enabled, so a single toggle pair guarantees a START.
  local b cx cy
  $ADB shell uiautomator dump /sdcard/probe.xml >/dev/null 2>&1
  b=$($ADB shell cat /sdcard/probe.xml 2>/dev/null | tr '<' '\n' | grep switch_overlay \
      | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | grep -oE '[0-9]+' | tr '\n' ' ')
  if [ -n "$b" ]; then
    set -- $b
    cx=$(( ($1 + $3) / 2 )); cy=$(( ($2 + $4) / 2 ))
    $ADB shell input tap $cx $cy; sleep 2   # off
    $ADB shell input tap $cx $cy; sleep 4   # on
  fi
  ov_count
}

# Screen rows covered by a system bar whose window sits ABOVE an app overlay in Z-order.
# TYPE_APPLICATION_OVERLAY is layer 111000 while StatusBar is 151000, and no window type above
# that is available to a normal app, so the system bar wins the hit-test there whenever it is
# visible. Those rows are excluded from pass/fail scoring and reported separately by
# probe_shadowed, because a miss there is a platform limit rather than an app defect.
# System-bar regions in the same coordinate space as overlay window positions.
#
# `dumpsys input` reports a bar's touchableRegion in the display's UNROTATED space, while overlay
# windows are positioned in the current rotated space. At rotation 90 the status bar therefore
# shows up as [2134,0][2208,1840] (a right-hand strip of the unrotated frame) even though it is
# drawn along the visual top edge. Deriving the strip from the bar's own thickness and the current
# rotation puts both in one space; without this the harness scored real blocks as failures.
system_bar_rows() {
  local sw sh thickness
  read -r sw sh < <(screen_wh)
  # Two dumpsys input formats are in the wild: API 30+ prints `name=<id> StatusBar, ...` while
  # API 29 prints `name='Window{<id> u0 StatusBar}', ...`. Match the bar name anywhere on the line
  # so both work; a mismatch here silently reported zero bars and scored real system-bar shadowing
  # as an app failure.
  thickness=$($ADB shell dumpsys input 2>/dev/null | tr -d '' | awk '
    /(StatusBar|Taskbar|NavigationBar)/ {
      if (match($0, /touchableRegion=\[[0-9-]+,[0-9-]+\]\[[0-9-]+,[0-9-]+\]/)) {
        region = substr($0, RSTART, RLENGTH)
        gsub(/touchableRegion=\[|\]/, "", region); gsub(/\[/, ",", region)
        split(region, v, ",")
        w = v[3] - v[1]; h = v[4] - v[2]
        if (w > 0 && h > 0) print (w < h ? w : h)
      }
    }' | sort -rn | head -1)
  [ -z "${thickness:-}" ] && return 0
  [ "$thickness" -le 0 ] && return 0
  echo "0 0 $sw $thickness"
}

# True when (x,y) falls inside any system-bar touchable region.
in_system_bar() {
  local px=$1 py=$2 l t r b
  while read -r l t r b; do
    [ -z "${b:-}" ] && continue
    if [ "$px" -ge "$l" ] && [ "$px" -lt "$r" ] && [ "$py" -ge "$t" ] && [ "$py" -lt "$b" ]; then
      return 0
    fi
  done <<<"$(system_bar_rows)"
  return 1
}

# Taps centre + 4 inset corners of every overlay window; prints blocked/total.
# Coordinates outside the physical display are skipped: a window whose point sits near an edge
# legitimately hangs off-screen, and `input tap` cannot deliver there. Points shadowed by a
# system bar are skipped too (see system_bar_rows).
# Dismisses the notification shade / quick settings if a probe tap happened to pull it down.
# A tap near the top edge can drag the shade open, and once it has focus it consumes every
# later tap, which silently turned whole probe runs into false failures.
dismiss_shade() {
  local focus
  focus=$($ADB shell dumpsys window 2>/dev/null | grep -oE "mCurrentFocus=Window\{[^}]*\}" | head -1)
  case "$focus" in
    *NotificationShade*|*QuickSettings*|*"Not Responding"*)
      $ADB shell cmd statusbar collapse >/dev/null 2>&1
      $ADB shell input keyevent KEYCODE_BACK >/dev/null 2>&1
      sleep 2
      ;;
  esac
}

# Counts blocked-tap log lines whose coordinate matches one of the taps this run actually sent.
# Counting every "blocked tap" line instead let stale lines from a previous probe inflate the
# result, which showed up as leaks that varied run to run (2, then 0, then 10 out of 24).
count_matching_blocks() {
  local sent="$1" log hits=0 pair px py
  log=$($ADB logcat -d -s PointOverlayView:D 2>/dev/null | grep "blocked tap")
  for pair in $sent; do
    px=${pair%,*}; py=${pair#*,}
    if printf '%s
' "$log" | grep -q "blocked tap x=$px.0 y=$py.0"; then
      hits=$((hits + 1))
    fi
  done
  echo "$hits"
}

probe_taps() {
  local blocked=0 total=0 sw sh sent=""
  read -r sw sh < <(screen_wh)
  local bars; bars=$(system_bar_rows)
  dismiss_shade
  # ov_rects must be captured up front: `adb shell` reads stdin and would eat the loop's input.
  local rects; rects=$(ov_rects)
  $ADB logcat -c; sleep 1; $ADB logcat -c
  while read -r x y w h; do
    [ -z "${h:-}" ] && continue
    for pt in "$((x + w / 2)) $((y + h / 2))" \
              "$((x + 1)) $((y + 1))" "$((x + w - 2)) $((y + 1))" \
              "$((x + 1)) $((y + h - 2))" "$((x + w - 2)) $((y + h - 2))"; do
      set -- $pt
      local px=$1 py=$2
      if [ "$px" -lt 0 ] || [ "$py" -lt 0 ] || [ "$px" -ge "$sw" ] || [ "$py" -ge "$sh" ]; then
        continue
      fi
      local shadowed=0 l t r b
      while read -r l t r b; do
        [ -z "${b:-}" ] && continue
        if [ "$px" -ge "$l" ] && [ "$px" -lt "$r" ] && [ "$py" -ge "$t" ] && [ "$py" -lt "$b" ]; then
          shadowed=1; break
        fi
      done <<<"$bars"
      [ "$shadowed" -eq 1 ] && continue
      $ADB shell input tap "$px" "$py" </dev/null >/dev/null 2>&1
      total=$((total + 1))
      sent="$sent$px,$py "
      # A tap that lands near the top edge can drag the shade open; clear it before continuing so
      # the remaining taps in this run are still delivered to the overlay.
      if [ "$py" -lt 200 ]; then
        dismiss_shade
      fi
    done
  done <<<"$rects"
  sleep 2
  blocked=$(count_matching_blocks "$sent")
  dismiss_shade
  echo "$blocked/$total"
}

# Taps just outside each overlay window; those must NOT be blocked. Any tap that happens to land
# inside another overlay window is skipped so overlapping windows cannot fake a leak.
probe_outside() {
  local leaked=0 total=0 sw sh sent=""
  read -r sw sh < <(screen_wh)
  local rects; rects=$(ov_rects)
  dismiss_shade
  $ADB logcat -c; sleep 1; $ADB logcat -c
  while read -r x y w h; do
    [ -z "${h:-}" ] && continue
    for pt in "$((x - 24)) $((y + h / 2))" "$((x + w + 24)) $((y + h / 2))" \
              "$((x + w / 2)) $((y - 24))" "$((x + w / 2)) $((y + h + 24))"; do
      set -- $pt
      local px=$1 py=$2
      if [ "$px" -lt 0 ] || [ "$py" -lt 0 ] || [ "$px" -ge "$sw" ] || [ "$py" -ge "$sh" ]; then
        continue
      fi
      local inside=0 rx ry rw rh
      while read -r rx ry rw rh; do
        [ -z "${rh:-}" ] && continue
        if [ "$px" -ge "$rx" ] && [ "$px" -lt $((rx + rw)) ] \
           && [ "$py" -ge "$ry" ] && [ "$py" -lt $((ry + rh)) ]; then
          inside=1; break
        fi
      done <<<"$rects"
      [ "$inside" -eq 1 ] && continue
      $ADB shell input tap "$px" "$py" </dev/null >/dev/null 2>&1
      total=$((total + 1))
      sent="$sent$px,$py "
      if [ "$py" -lt 200 ]; then
        dismiss_shade
      fi
    done
  done <<<"$rects"
  sleep 2
  leaked=$(count_matching_blocks "$sent")
  dismiss_shade
  echo "$leaked/$total"
}

# Prints the screen coordinates of the main screen's overlay switch, or nothing when it is not
# on screen. adb inserts carriage returns, so they are stripped before any arithmetic.
switch_xy() {
  $ADB shell uiautomator dump /sdcard/probe_sw.xml >/dev/null 2>&1
  local nums
  nums=$($ADB shell cat /sdcard/probe_sw.xml 2>/dev/null | tr -d '\r' | tr '<' '\n' \
    | grep switch_overlay \
    | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' \
    | grep -oE '[0-9]+' | tr '\n' ' ')
  [ -z "$nums" ] && return 1
  set -- $nums
  [ $# -lt 4 ] && return 1
  echo "$(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))"
}

# Toggles the overlay off then on, guaranteeing a user-initiated START (which plays the fade).
toggle_restart() {
  $ADB shell am start -n $PKG/.MainActivity >/dev/null 2>&1
  sleep 3
  local xy; xy=$(switch_xy)
  if [ -z "$xy" ]; then
    # The switch can be scrolled out of view; scroll the form back to the top and retry.
    $ADB shell input swipe 1100 500 1100 1600 </dev/null >/dev/null 2>&1
    sleep 2
    xy=$(switch_xy) || { echo "switch not found" >&2; return 1; }
  fi
  set -- $xy
  $ADB shell input tap "$1" "$2" </dev/null >/dev/null 2>&1; sleep 3
  $ADB shell input tap "$1" "$2" </dev/null >/dev/null 2>&1; sleep 4
  # Return to the launcher: the app's own window is immersive and hides the system bars, so
  # probing while it is in front would not reflect what a user sees in another app.
  $ADB shell input keyevent KEYCODE_HOME </dev/null >/dev/null 2>&1
  sleep 3
  ov_count
}

# Reports how many overlay windows are shadowed by a system bar, i.e. how many of the user's
# blocking points silently do nothing while that bar is visible.
probe_shadowed() {
  local shadowed=0 totalw=0 cx cy x y w h
  local bars; bars=$(system_bar_rows)
  while read -r x y w h; do
    [ -z "${h:-}" ] && continue
    totalw=$((totalw + 1))
    cx=$((x + w / 2)); cy=$((y + h / 2))
    local l t r b
    while read -r l t r b; do
      [ -z "${b:-}" ] && continue
      if [ "$cx" -ge "$l" ] && [ "$cx" -lt "$r" ] && [ "$cy" -ge "$t" ] && [ "$cy" -lt "$b" ]; then
        shadowed=$((shadowed + 1)); break
      fi
    done <<<"$bars"
  done <<<"$(ov_rects)"
  echo "$shadowed/$totalw"
}

case "${1:-}" in
  seed)          shift; seed "$@" ;;
  switch)        switch_xy ;;
  shadowed)      probe_shadowed ;;
  bars)          system_bar_rows ;;
  restart)       toggle_restart ;;
  start)         start_overlay ;;
  count)         ov_count ;;
  rects)         ov_rects ;;
  taps)          probe_taps ;;
  outside)       probe_outside ;;
  screen)        screen_wh ;;
  rotation)      rotation ;;
  *) echo "usage: $0 {seed|start|count|rects|taps|outside|screen|rotation}" >&2; exit 2 ;;
esac
