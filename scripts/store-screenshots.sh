#!/usr/bin/env bash
# Google Play phone screenshots (1080 × 1920, 9:16) in English and German from the seeded demo data.
# PNGs → docs/store/screenshots/<locale>/<n>_<screen>.png (gitignored: the UI still changes, rerun
# before each listing update). Optional caption band: docs/store/caption_screenshots.py.
#
#   ANDROID_SERIAL=localhost:5561 scripts/store-screenshots.sh            # build, install .store, shoot, clean up
#   ANDROID_SERIAL=localhost:5561 scripts/store-screenshots.sh --no-build # reuse the last debug APK (must be a .store build)
#   LOCALES='de-DE' SHOTS='1:p6:round_table' ANDROID_SERIAL=… scripts/store-screenshots.sh
#
# One emulator only (ANDROID_SERIAL is required; never the user's phone). Installs a debug build
# with applicationId suffix .store, sets wm size 1080x1920 + density 420 (Play: long side ≤ 2× the
# short side), per-app locale via `cmd locale set-app-locales`, status bar demo mode (fixed clock,
# full battery/wifi, no notifications). Uses the debug_seed / debug_open hooks
# (app/.../debug/DebugSeed.kt; they WIPE the .store app's data, never the normal app's).
# Everything is reset at the end (also on error / Ctrl-C) and .store is uninstalled.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"

: "${ANDROID_SERIAL:?set ANDROID_SERIAL to one emulator, e.g. ANDROID_SERIAL=localhost:5561}"
case $ANDROID_SERIAL in
  adb-*|192.168.*) echo "refusing to run on the phone ($ANDROID_SERIAL); use an emulator" >&2; exit 1 ;;
esac
export ANDROID_SERIAL
A=(adb -s "$ANDROID_SERIAL")

SUFFIX=.store
PKG=net.moesli.scan7$SUFFIX
ACT=$PKG/net.moesli.scan7.MainActivity
OUT=${OUT:-$ROOT/docs/store/screenshots}
read -r -a LOCALE_LIST <<<"${LOCALES:-en-US de-DE}"
# <n>:<debug_seed>:<debug_open>, order = docs/store/README.md shot list.
read -r -a SHOT_LIST <<<"${SHOTS:-1:p6:round_table 2:p6:review 3:p6:round_list 4:p6:scoreboard 5:gameover:gameover 6:history:home}"
WAIT=${WAIT:-4}
TRIES=${TRIES:-3}
# The review shot scans a real table photo without personal info; photos aren't in git, so in a
# worktree take it from the main checkout.
PHOTO=ml/data/real_photos/train/PXL_20261018_103625190.jpg
MAIN=$(cd "$(git rev-parse --git-common-dir)/.." && pwd)
if [[ -f $ROOT/$PHOTO ]]; then PHOTO_SRC=${PHOTO_SRC:-$ROOT/$PHOTO}; else PHOTO_SRC=${PHOTO_SRC:-$MAIN/$PHOTO}; fi
[[ -f $PHOTO_SRC ]] || { echo "missing review photo $PHOTO_SRC" >&2; exit 1; }

if [[ "${1:-}" != "--no-build" ]]; then
  (cd app && JAVA_HOME="${JAVA_HOME:-$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home}" \
    ./gradlew -q -Pscan7.idSuffix=$SUFFIX assembleDebug)
fi
APK=app/app/build/outputs/apk/debug/app-debug.apk

demo() { "${A[@]}" shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }

cleanup() {
  set +e
  echo "Resetting $ANDROID_SERIAL"
  demo exit
  "${A[@]}" shell settings put global sysui_demo_allowed 0 >/dev/null
  "${A[@]}" shell wm size reset >/dev/null
  "${A[@]}" shell wm density reset >/dev/null
  "${A[@]}" shell settings put global window_animation_scale 1 >/dev/null
  "${A[@]}" shell settings put global transition_animation_scale 1 >/dev/null
  "${A[@]}" shell cmd locale set-app-locales $PKG --locales "" >/dev/null 2>&1
  "${A[@]}" uninstall $PKG >/dev/null 2>&1
}
trap cleanup EXIT

"${A[@]}" install -r -g "$APK" >/dev/null
"${A[@]}" push "$PHOTO_SRC" /data/local/tmp/screen_review.jpg >/dev/null
"${A[@]}" shell run-as $PKG mkdir -p files
"${A[@]}" shell run-as $PKG cp /data/local/tmp/screen_review.jpg files/screen_review.jpg
"${A[@]}" shell rm /data/local/tmp/screen_review.jpg
"${A[@]}" shell settings put global window_animation_scale 0
"${A[@]}" shell settings put global transition_animation_scale 0

"${A[@]}" shell wm size 1080x1920
"${A[@]}" shell wm density 420
# The emulator needs a moment to relayout after a size change; wait until it reports the override.
for _ in $(seq 20); do "${A[@]}" shell wm size | grep -q 'Override size: 1080x1920' && break; sleep 1; done
sleep 3

"${A[@]}" shell settings put global sysui_demo_allowed 1
demo enter
demo clock -e hhmm 1200
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e fully true
demo network -e mobile hide
demo notifications -e visible false

# A shot is "blank" when the screencap is tiny (flat ground colour) or another app is on top.
shot_ok() {
  local png=$1 size
  size=$(wc -c <"$png" | tr -d ' ')
  ((size > 60000)) || { echo "    small capture ($size bytes)" >&2; return 1; }
  "${A[@]}" shell dumpsys activity activities 2>/dev/null | grep -m1 'topResumedActivity' | grep -q "$PKG/" \
    || { echo "    app not in front" >&2; return 1; }
  python3 - "$png" <<'EOF' || { echo "    wrong size" >&2; return 1; }
import struct, sys
with open(sys.argv[1], "rb") as f:
    head = f.read(24)
w, h = struct.unpack(">II", head[16:24])
sys.exit(0 if (w, h) == (1080, 1920) else 1)
EOF
}

shoot() {
  local locale=$1 n=$2 seed=$3 screen=$4 png try wait extra=()
  png="$OUT/$locale/${n}_${screen}.png"
  [[ $screen == review ]] && extra=(--ez debug_cpu true)
  for ((try = 1; try <= TRIES; try++)); do
    wait=$((WAIT * try))
    [[ $screen == review ]] && wait=$((wait + 6))   # CPU detection on the photo
    "${A[@]}" shell am force-stop $PKG
    "${A[@]}" shell am start -W -n $ACT --es debug_seed "$seed" --es debug_open "$screen" "${extra[@]}" >/dev/null
    sleep "$wait"
    # Home's list can open scrolled past the running-game card (it loads after the history):
    # fling it back to the top.
    if [[ $screen == home ]]; then "${A[@]}" shell input swipe 540 500 540 1500 150; sleep 2; fi
    "${A[@]}" exec-out screencap -p >"$png"
    if shot_ok "$png"; then echo "  $locale/${n}_${screen}.png"; return 0; fi
    echo "  retry $try: $locale/${n}_${screen}" >&2
  done
  echo "FAILED: $png" >&2
  return 1
}

for locale in "${LOCALE_LIST[@]}"; do
  mkdir -p "$OUT/$locale"
  "${A[@]}" shell am force-stop $PKG
  "${A[@]}" shell cmd locale set-app-locales $PKG --locales "$locale"
  echo "[$ANDROID_SERIAL] $locale"
  for s in "${SHOT_LIST[@]}"; do
    IFS=: read -r n seed screen <<<"$s"
    shoot "$locale" "$n" "$seed" "$screen"
  done
done
echo "Done: $OUT/<locale>/*.png  (captions: uv run --project ml python -I docs/store/caption_screenshots.py)"
