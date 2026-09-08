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
DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=overlay_dump.sh
. "$DIR/overlay_dump.sh"

# --- helpers ---------------------------------------------------------------







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

# Collapse the notification shade, but do not send HOME. HOME on a tablet/phone that pins
# orientation silently rotates the display back to 0, so a landscape probe would then be
# scoring against a portrait overlay. Overlay windows sit above the current app, so they
# consume the tap even when a non-launcher app is in front -- as long as that app is not a
# system dialog (already handled by dismiss_shade).
settle_foreground() {
  dismiss_shade
  wait_for_input_regions </dev/null
}

# Waits until the input system's overlay regions stop moving.
#
# After a rotation the window manager reports new positions before the input system finishes
# re-registering the windows, so a probe fired too early taps where the windows used to be. That
# produced results that swung between 0/18 and 18/18 for the same configuration. Polling until two
# consecutive reads agree makes a run reproducible.
wait_for_input_regions() {
  local previous="" current=""
  for _ in 1 2 3 4 5 6 7 8 9 10 11 12; do
    current=$(ov_rects </dev/null)
    if [ -n "$current" ] && [ "$current" = "$previous" ]; then
      return 0
    fi
    previous="$current"
    sleep 2
  done
  return 0
}

# Counts blocked-tap log lines whose coordinate matches one of the taps this run actually sent.
# Counting every "blocked tap" line instead let stale lines from a previous probe inflate the
# result, which showed up as leaks that varied run to run (2, then 0, then 10 out of 24).
count_matching_blocks() {
  local sent="$1" log hits=0 pair px py
  log=$($ADB logcat -d -s PointOverlayView:D 2>/dev/null | grep "blocked tap")
  for pair in $sent; do
    px=${pair%,*}; py=${pair#*,}
    if printf '%s\n' "$log" | grep -q "blocked tap x=$px.0 y=$py.0"; then
      hits=$((hits + 1))
    fi
  done
  echo "$hits"
}

probe_taps() {
  local blocked=0 total=0 sw sh sent=""
  read -r sw sh < <(screen_wh)
  local bars; bars=$(system_bar_rows)
  settle_foreground
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
      # the remaining taps in this run are still delivered to the overlay. stdin is redirected
      # because the adb calls inside would otherwise consume the enclosing loop's input and
      # silently drop every remaining row.
      if [ "$py" -lt 200 ]; then
        dismiss_shade </dev/null
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
  settle_foreground
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
        dismiss_shade </dev/null
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
  # `am start` returns before the window becomes focusable, so reading the switch straight after it
  # found nothing and the overlay was never toggled. `-W` waits for the launch to settle, and the
  # focus poll covers the case where another app (launcher, a system dialog) still owns focus.
  $ADB shell am start -W -n $PKG/.MainActivity >/dev/null 2>&1
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    $ADB shell dumpsys window 2>/dev/null | grep -q "mCurrentFocus.*$PKG/" && break
    $ADB shell am start -W -n $PKG/.MainActivity >/dev/null 2>&1
    sleep 2
  done
  sleep 2
  local xy; xy=$(switch_xy)
  if [ -z "$xy" ]; then
    # The switch can be scrolled out of view; scroll the form back to the top and retry.
    $ADB shell input swipe 1100 500 1100 1600 </dev/null >/dev/null 2>&1
    sleep 2
    xy=$(switch_xy) || { echo "switch not found" >&2; return 1; }
  fi
  set -- $xy
  # Always tap once. The seed writes overlay_should_be_enabled=true, so if the UI already shows
  # ON this is a no-op for the flag and a START_OVERLAY is not sent -- in that case we start the
  # service explicitly. If the UI shows OFF, one tap turns it on.
  $ADB shell input tap "$1" "$2" </dev/null >/dev/null 2>&1; sleep 5
  $ADB shell am start-foreground-service -n $PKG/.OverlayService -a $PKG.action.START_OVERLAY >/dev/null 2>&1     || $ADB shell am startservice -n $PKG/.OverlayService -a $PKG.action.START_OVERLAY >/dev/null 2>&1
  sleep 3
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
