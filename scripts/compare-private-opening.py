"""Compare the same native password/profile flow on the previous and current APK."""
import json
import re
from pathlib import Path

root = Path('performance-reports')
before = json.loads((root / 'baseline-private-opening-ui.json').read_text())
after = [json.loads(sample) for sample in re.findall(r'private-opening (\{[^\r\n]+\})',
         (root / 'current-private-opening-logcat.txt').read_text())]
assert len(before) == len(after) == 4, 'All profile creation, restoration and crash-recovery opens must complete'
assert [sample['ordinal'] for sample in before] == [sample['ordinal'] for sample in after] == [1, 2, 3, 4]
(root / 'current-private-opening-ui.json').write_text(json.dumps(after, indent=2))
report = {'baseline': before, 'current': after,
          'notes': 'First two opens create areas including password confirmation; third restores an area, fourth follows process death. Times include UI automation and local page loading.'}
(root / 'private-opening-ui-comparison.json').write_text(json.dumps(report, indent=2))
print('PRIVATE OPENING UI COMPARISON ' + json.dumps(report))
