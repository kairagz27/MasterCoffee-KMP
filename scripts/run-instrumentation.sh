#!/bin/sh
# Run already-built Android APKs. The emulator/device must already be available.
set -eu
cd "$(dirname "$0")/.."
app_apk=${1:?Usage: sh scripts/run-instrumentation.sh app.apk test.apk}
test_apk=${2:?Provide the Android test APK}
evidence=${ANDROID_EVIDENCE_DIR:-artifacts/android-ui}
mkdir -p "$evidence"
adb wait-for-device
adb install -r "$app_apk"
adb install -r "$test_apk"
adb logcat -c
collect_evidence() {
  adb logcat -d -v threadtime AndroidRuntime:E '*:S' > "$evidence/crashes.txt" || true
  adb pull /sdcard/Android/data/kz.master_coffee.app/files/verification "$evidence/screens" >/dev/null 2>&1 || true
}
# Keep earlier successful screenshots even when a later test fails.
trap collect_evidence EXIT
if [ "${RUN_BACKEND_E2E:-false}" = true ]; then
  # CI/local development credentials only. Never point this test at a production API.
  : "${DEMO_PASSWORD:?Set the development staff password}"
  adb shell am instrument -w -r \
    -e masterCoffeeLaunchSmoke "${RUN_LAUNCH_SMOKE:-false}" \
    -e masterCoffeeLiveApi "${ANDROID_API_URL:-http://10.0.2.2:8080/api/v1}" \
    -e masterCoffeeStaffEmail staff@mastercoffee.local \
    -e masterCoffeeStaffPassword "$DEMO_PASSWORD" \
    kz.master_coffee.app.test/androidx.test.runner.AndroidJUnitRunner > "$evidence/instrumentation.txt" 2>&1
else
  adb shell am instrument -w -r \
    -e masterCoffeeLaunchSmoke "${RUN_LAUNCH_SMOKE:-false}" \
    kz.master_coffee.app.test/androidx.test.runner.AndroidJUnitRunner > "$evidence/instrumentation.txt" 2>&1
fi
cat "$evidence/instrumentation.txt"
# am instrument can return a zero shell status when the tests themselves failed.
if ! grep -Eq 'OK \([1-9][0-9]* tests?\)' "$evidence/instrumentation.txt" ||
  grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|Process crashed|shortMsg=' "$evidence/instrumentation.txt"; then
  exit 1
fi
if [ "${RUN_BACKEND_E2E:-false}" = true ] && ! grep -q 'masterCoffeeLiveOrderComplete=true' "$evidence/instrumentation.txt"; then
  echo 'The live backend scenario did not report completion.' >&2
  exit 1
fi
if [ "${RUN_LAUNCH_SMOKE:-false}" = true ] && ! grep -q 'masterCoffeeLaunchComplete=true' "$evidence/instrumentation.txt"; then
  echo 'The real MainActivity launch scenario did not report completion.' >&2
  exit 1
fi
if ! grep -q 'masterCoffeeHybridUiComplete=true' "$evidence/instrumentation.txt"; then
  echo 'The drawer, language/theme and large-text scenario did not report completion.' >&2
  exit 1
fi
echo 'PASS: Android instrumentation completed; this does not certify physical camera or TalkBack behavior.'
