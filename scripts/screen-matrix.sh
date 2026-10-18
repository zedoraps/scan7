#!/usr/bin/env bash
# Screenshot matrix: every seeded screen of the debug app (.shots) on many phone sizes / densities /
# font scales, split across two mini emulators in parallel. PNGs → out/screens/<profile>/<screen>.png,
# then contact sheets out/screens/sheet_<screen>.jpg (ml/scripts/contact_sheets.py).
#
#   scripts/screen-matrix.sh --install        # build + install the .shots debug app first
#   scripts/screen-matrix.sh                  # all profiles × all screens (~15 min)
#   PROFILES='720x1600@320x1.0' SCREENS='p6_round_table p10_round_table' scripts/screen-matrix.sh
#   DEVICES='localhost:5561' scripts/screen-matrix.sh   # one emulator
#
# Uses the debug_seed / debug_open hooks (app/.../debug/DebugSeed.kt). Never touch the user's phone,
# emulator-5554 or another agent's emulator: DEVICES defaults to emu-1 + emu-3. wm size / density /
# font_scale are reset at the end (also on Ctrl-C).
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"

PKG=net.moesli.scan7.shots
ACT=$PKG/net.moesli.scan7.MainActivity
PHOTO=ml/data/real_photos/train/PXL_20261018_103625190.jpg
# Photos aren't in git: in a worktree, take it from the main checkout.
MAIN=$(cd "$(git rev-parse --git-common-dir)/.." && pwd)
if [[ -f $ROOT/$PHOTO ]]; then PHOTO_SRC=${PHOTO_SRC:-$ROOT/$PHOTO}; else PHOTO_SRC=${PHOTO_SRC:-$MAIN/$PHOTO}; fi
OUT=${OUT:-$ROOT/out/screens}
read -r -a DEVS <<<"${DEVICES:-localhost:5561 localhost:5565}"
WAIT=${WAIT:-3}

# <W>x<H>@<density>x<font_scale>
ALL_PROFILES=(
  720x1600@320x1.0 1080x2340@420x1.0 1080x2400@420x1.0 1440x3120@560x1.0 1080x2520@420x1.0
  720x1280@320x1.0 1080x1920@420x1.0 1812x2176@420x1.0 1600x2560@320x1.0
  720x1600@320x1.3 1080x2400@420x1.3 1440x3120@560x1.3
  720x1600@320x2.0 1080x2400@420x2.0
)
# <scenario>_<screen>: scenario = debug_seed (p3/p6/p10/gameover), screen = debug_open.
ALL_SCREENS=(
  p3_home p3_round_table p3_round_list
  p6_home p6_seating p6_round_table p6_round_list p6_sheet_open p6_sheet_done p6_adjust_dialog
  p6_manual p6_review p6_camera p6_scoreboard
  p10_players p10_newgame p10_seating p10_round_table p10_round_list p10_scoreboard
  gameover_gameover
)
read -r -a PROFILE_LIST <<<"${PROFILES:-${ALL_PROFILES[*]}}"
read -r -a SCREEN_LIST <<<"${SCREENS:-${ALL_SCREENS[*]}}"

if [[ "${1:-}" == "--install" ]]; then
  (cd app && JAVA_HOME="${JAVA_HOME:-$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home}" \
    ./gradlew -q -Pscan7.idSuffix=.shots assembleDebug)
  for d in "${DEVS[@]}"; do adb -s "$d" install -r -g app/app/build/outputs/apk/debug/app-debug.apk >/dev/null; done
fi

reset_device() {
  adb -s "$1" shell wm size reset >/dev/null 2>&1 || true
  adb -s "$1" shell wm density reset >/dev/null 2>&1 || true
  adb -s "$1" shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
}

# Taps the centre of the first node whose content-desc starts with $2.
tap_desc() {
  local dev=$1 prefix=$2 xml b
  xml=$(adb -s "$dev" exec-out uiautomator dump /dev/tty 2>/dev/null || true)
  b=$(grep -o "content-desc=\"$prefix[^\"]*\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" <<<"$xml" \
      | head -1 | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' || true)
  if [[ -z "$b" ]]; then echo "  [$dev] no node '$prefix'" >&2; return 0; fi
  read -r x1 y1 x2 y2 <<<"$(tr '[],' '   ' <<<"$b")"
  adb -s "$dev" shell input tap $(((x1 + x2) / 2)) $(((y1 + y2) / 2))
}

shoot() {
  local dev=$1 profile=$2 key=$3 scenario=${3%%_*} screen=${3#*_} extra=() wait=$WAIT
  case $screen in
    review) extra=(--ez debug_cpu true); wait=$((WAIT + 6)) ;;
    camera) wait=$((WAIT + 3)) ;;
  esac
  adb -s "$dev" shell am force-stop $PKG
  adb -s "$dev" shell am start -W -n $ACT --es debug_seed "$scenario" --es debug_open "$screen" "${extra[@]}" >/dev/null
  sleep "$wait"
  case $screen in
    sheet_open) tap_desc "$dev" "Maximilian-Alexander, "; sleep 2 ;;
    sheet_done) tap_desc "$dev" "Philip, "; sleep 2 ;;
    adjust_dialog) tap_desc "$dev" "Philip, "; sleep 2; tap_desc "$dev" "Adjust Philip"; sleep 2 ;;
  esac
  adb -s "$dev" exec-out screencap -p >"$OUT/$profile/$key.png"
}

run_device() {
  local dev=$1; shift
  trap 'reset_device '"$dev" EXIT
  adb -s "$dev" push "$PHOTO_SRC" /data/local/tmp/screen_review.jpg >/dev/null
  adb -s "$dev" shell run-as $PKG mkdir -p files
  adb -s "$dev" shell run-as $PKG cp /data/local/tmp/screen_review.jpg files/screen_review.jpg
  adb -s "$dev" shell pm grant $PKG android.permission.CAMERA 2>/dev/null || true
  adb -s "$dev" shell settings put global window_animation_scale 0
  adb -s "$dev" shell settings put global transition_animation_scale 0
  for profile in "$@"; do
    local size=${profile%@*} rest=${profile#*@}
    adb -s "$dev" shell wm size "$size"
    adb -s "$dev" shell wm density "${rest%x*}"
    adb -s "$dev" shell settings put system font_scale "${rest#*x}"
    mkdir -p "$OUT/$profile"
    echo "[$dev] $profile"
    for key in "${SCREEN_LIST[@]}"; do shoot "$dev" "$profile" "$key"; done
  done
}

mkdir -p "$OUT"
trap 'for d in "${DEVS[@]}"; do reset_device "$d"; done' EXIT
pids=()
for i in "${!DEVS[@]}"; do
  mine=()
  for j in "${!PROFILE_LIST[@]}"; do (((j % ${#DEVS[@]}) == i)) && mine+=("${PROFILE_LIST[$j]}"); done
  ((${#mine[@]})) || continue
  run_device "${DEVS[$i]}" "${mine[@]}" &
  pids+=($!)
done
for p in "${pids[@]}"; do wait "$p"; done

(cd ml && uv run python -I scripts/contact_sheets.py --screens "$OUT" --profiles "${ALL_PROFILES[@]}")
echo "Done: $OUT/sheet_*.jpg"
