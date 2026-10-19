#!/usr/bin/env bash
# Run global display-overlay changes last so emulator System UI cannot affect other tests.
set -e
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class 'de.steffen.musikmathe.BrowserFullscreenTest#cameraCutoutAreaIsUsedInBrowserAndVideoAndChromeIsRestored' -e cutoutProbe true de.steffen.musikmathe.test/androidx.test.runner.AndroidJUnitRunner | tee cutout-test.txt
python3 - <<'PY'
import re
from pathlib import Path

output = Path('cutout-test.txt').read_text()
if not re.search(r'^OK \(1 test\)\s*$', output, re.MULTILINE) or 'FAILURES' in output:
    raise SystemExit('The separate camera-cutout test did not pass.')
PY
