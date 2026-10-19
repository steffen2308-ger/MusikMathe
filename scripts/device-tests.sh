#!/usr/bin/env bash
# Keep device failures and visual previews readable in CI without downloading a report ZIP.
gradle --no-daemon :app:connectedDebugAndroidTest "$@"
test_result=$?
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

for path in Path('app/build/outputs/androidTest-results').rglob('*.xml'):
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    for case in root.iter('testcase'):
        for failure in list(case.findall('failure')) + list(case.findall('error')):
            print(f"DEVICE FAILURE: {case.get('classname')}#{case.get('name')}")
            print(failure.get('message', ''))
            print((failure.text or '')[:16000])
PY
adb logcat -d -s AdBlockLiveProbe:I MenuPreview:I NightPreview:I '*:S'
exit "$test_result"
