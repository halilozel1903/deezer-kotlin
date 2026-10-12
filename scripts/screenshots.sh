#!/usr/bin/env bash
# Captures README screenshots of the Pocket Charts sample on a running emulator (pixel_7).
# Every scene loads live data from the public Deezer API (charts, Daft Punk's artist page, a search, the car browse
# tree) and cover images through Coil, so the screenshots show what the API returns on the day they are taken.
# The sample opens each screen from the `scene` extra; in scene mode it retries the network a few times and then
# shows an error instead of the screen, so the expected text check below fails rather than capturing a broken or
# made-up screen.
#
#   bash scripts/screenshots.sh
set -euo pipefail
source "$(dirname "$0")/screenshot-lib.sh"

# The text each scene must show: section headers of the app, which only appear once the API answered.
expected_text() {
  case "$1" in
    charts) echo "Top tracks" ;;
    artist) echo "Popular" ;;
    search) echo "Matching tracks" ;;
    car) echo "Top tracks" ;;
  esac
}

# Polls the screen for $1 for up to $2 seconds, since the data comes over the network. Returns non-zero on timeout;
# capture() then fails with the usual message.
wait_for_text() {
  local text="$1" timeout="${2:-90}" waited=0
  while [ "$waited" -lt "$timeout" ]; do
    if adb shell uiautomator dump /sdcard/window.xml > /dev/null 2>&1 \
      && adb exec-out cat /sdcard/window.xml | grep -qF "$text"; then
      echo "\"$text\" is on screen after ${waited}s"
      return 0
    fi
    sleep 3
    waited=$((waited + 3))
  done
  echo "\"$text\" did not appear within ${timeout}s." >&2
  return 1
}

shoot() {
  local scene="$1" name="$2" expected
  expected="$(expected_text "$scene")"
  fresh_launch --es scene "$scene"
  wait_for_text "$expected" 90 || true
  # Cover images load after the text; give Coil time to fetch and draw them.
  sleep 8
  capture "$name" "$expected"
}

install_sample
set_night_mode light
for scene in charts artist search; do
  shoot "$scene" "phone-$scene"
done

# The car preview is laid out like a landscape car display.
ensure_landscape
shoot car "car-browse"
