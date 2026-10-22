#!/usr/bin/env bash
set -euo pipefail

gradle --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
gradle --no-daemon --project-dir performance-baseline :app:assembleDebug
adb install -r -d performance-baseline/app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p performance-reports
for variant in baseline current; do
  if test "$variant" = current; then
    adb install -r app/build/outputs/apk/debug/app-debug.apk
  fi
  adb shell am force-stop de.steffen.musikmathe
  adb shell am instrument -w -e class de.steffen.musikmathe.BrowserPerformanceBenchmarkTest -e performanceBenchmark "$variant" de.steffen.musikmathe.test/androidx.test.runner.AndroidJUnitRunner > "performance-reports/$variant-instrumentation.txt"
  cat "performance-reports/$variant-instrumentation.txt"
  if ! grep -Fq 'OK (1 test)' "performance-reports/$variant-instrumentation.txt"; then
    adb logcat -d -s MenuPreview:I AndroidRuntime:E '*:S'
    exit 1
  fi
  adb shell run-as de.steffen.musikmathe cat "files/browser-performance-$variant.json" > "performance-reports/$variant.json"
done
python3 - <<'PY'
import json
from pathlib import Path
before=json.loads(Path('performance-reports/baseline.json').read_text())
after=json.loads(Path('performance-reports/current.json').read_text())
report={'baseline':before,'current':after,'relative_change_percent':{key:round(100*(after[key]-before[key])/before[key],1) for key in ['warmActivityReadyMedianMs','tabSwitchUiMedianMs','tabSwitchFrameMedianMs'] if before[key]>0}}
Path('performance-reports/comparison.json').write_text(json.dumps(report,indent=2))
print('BROWSER PERFORMANCE COMPARISON '+json.dumps(report))
PY
