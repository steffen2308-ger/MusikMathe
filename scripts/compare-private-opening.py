"""Compare the same native password/profile flow on the previous and current APK."""
import json
from pathlib import Path

root = Path('performance-reports')
before = json.loads((root / 'baseline-private-opening-ui.json').read_text())
after = json.loads((root / 'current-private-opening-ui.json').read_text())
assert len(before) == len(after) == 4, 'All profile creation, restoration and crash-recovery opens must complete'
report = {'baseline': before, 'current': after,
          'notes': 'First two opens create areas including password confirmation; third restores an area, fourth follows process death. Times include UI automation and local page loading.'}
(root / 'private-opening-ui-comparison.json').write_text(json.dumps(report, indent=2))
print('PRIVATE OPENING UI COMPARISON ' + json.dumps(report))
