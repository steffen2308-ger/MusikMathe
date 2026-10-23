"""Compile standard EasyList CSS rules; procedural/scriptlet rules are not executable input."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOMAIN = re.compile(r"(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z0-9-]+", re.I)


def compile_rules():
    generic, rules, skipped = set(), [], 0
    for name in ("easylist_general_hide.txt", "easylist_specific_hide.txt", "easylist_allowlist_general_hide.txt", "custom_cosmetic.txt"):
        for line in (ROOT / "filters" / name).read_text().splitlines():
            if not line or line.startswith("!"):
                continue
            marker = "#@#" if "#@#" in line else "##"
            if marker not in line:
                skipped += 1
                continue
            domains, selector = line.split(marker, 1)
            # Only native CSS is compiled. Never evaluate procedural filters or scriptlets.
            if not selector or selector.startswith("@") or any(x in selector for x in (";", "{", "}", "/*", "+js(", ":has-text(", ":matches-css(", ":upward(", ":xpath(")):
                skipped += 1
                continue
            includes, excludes = [], []
            valid = True
            for domain in filter(None, domains.split(",")):
                negative = domain.startswith("~")
                domain = domain.lstrip("~").lower()
                if not DOMAIN.fullmatch(domain):
                    valid = False
                    break
                (excludes if negative else includes).append(domain)
            if not valid:
                skipped += 1
                continue
            if not domains and marker == "##":
                generic.add(selector)
            else:
                rules.append([includes, excludes, selector, marker == "#@#"])
    return {"generic": sorted(generic), "rules": rules}, skipped


if __name__ == "__main__":
    data, skipped = compile_rules()
    target = ROOT / "app/src/main/assets/adblock_cosmetic.json"
    target.write_text(json.dumps(data, ensure_ascii=True, separators=(",", ":")) + "\n")
    (target.parent / "adblock_sources.json").write_text((ROOT / "filters/SOURCES.json").read_text())
    (target.parent / "third-party-notices.txt").write_text((ROOT / "THIRD_PARTY_NOTICES.md").read_text())
    print(f"Compiled {len(data['generic'])} generic selectors and {len(data['rules'])} site rules; skipped {skipped} unsupported entries.")
