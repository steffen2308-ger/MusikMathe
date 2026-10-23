"""Report the current APK's native password/profile flow after Gradle uninstalls it."""
import json
import re
from pathlib import Path

root = Path('performance-reports')
after = [json.loads(sample) for sample in re.findall(r'private-opening (\{[^\r\n]+\})',
         (root / 'current-private-opening-logcat.txt').read_text())]
assert len(after) == 4, 'All profile creation, restoration and crash-recovery opens must complete'
assert [sample['ordinal'] for sample in after] == [1, 2, 3, 4]
(root / 'current-private-opening-ui.json').write_text(json.dumps(after, indent=2))
report = {'current': after,
          'notes': 'Current APK only. First two opens create areas including password confirmation; third restores an area, fourth follows process death. Times include UI automation and local page loading; these are not component benchmark times.'}
(root / 'private-opening-ui-report.json').write_text(json.dumps(report, indent=2))
print('PRIVATE OPENING UI REPORT ' + json.dumps(report))
