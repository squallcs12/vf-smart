#!/usr/bin/env bash
# Launch the Desktop Head Unit (DHU) against the VF3 Smart app.
# Works with a physical phone or an AVD emulator.
#
# Usage:
#   ./start-dhu.sh                # auto-detect the single connected device/emulator
#   ./start-dhu.sh emulator-5554  # target a specific device/emulator serial
set -euo pipefail

# Resolve the Android SDK location (override with ANDROID_SDK_ROOT/ANDROID_HOME).
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
DHU_DIR="$SDK/extras/google/auto"
DHU_BIN="$DHU_DIR/desktop-head-unit"
CONFIG="$DHU_DIR/config/vf3_720p.ini"

if [ ! -x "$DHU_BIN" ]; then
  echo "DHU not found at: $DHU_BIN" >&2
  echo "Install it via the SDK Manager (Android Auto Desktop Head Unit emulator)." >&2
  exit 1
fi

# Pick the device: first CLI arg, then $ANDROID_SERIAL, then auto-detect a single
# connected device/emulator (the AVD shows up as e.g. emulator-5554).
DEVICE="${1:-${ANDROID_SERIAL:-}}"
if [ -z "$DEVICE" ]; then
  DEVICE="$(adb devices | awk 'NR>1 && $2 == "device" { print $1; exit }')"
fi
if [ -z "$DEVICE" ]; then
  echo "No adb device/emulator found. Start an AVD or plug in the phone, then retry." >&2
  exit 1
fi
echo "Using device: $DEVICE"

adb -s "$DEVICE" forward tcp:5277 tcp:5277
exec "$DHU_BIN" --config "$CONFIG"
