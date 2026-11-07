#!/usr/bin/env python3

import argparse
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path

MARKER = "<!-- plugkit-util:new-code-coverage -->"
SOURCE_DIRS = ("src/main/java",)
SOURCE_SUFFIXES = (".java",)
REPORT_GLOB = "**/target/site/jacoco/jacoco.xml"
HUNK = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@")


@dataclass
class ModuleResult:
    new: int = 0
    covered: int = 0
    uncovered: list = field(default_factory=list)
    missing_report: bool = False

    @property
    def percent(self):
        return 100.0 * self.covered / self.new if self.new else None


def changed_lines(base):
    diff = subprocess.run(
        ["git", "diff", "--unified=0", "--no-color", "--find-renames", f"{base}...HEAD"],
        check=True, capture_output=True, text=True,
    ).stdout

    result, current = {}, None
    for line in diff.splitlines():
        if line.startswith("+++ "):
            path = line[4:]
            path = path[2:] if path.startswith("b/") else None
            is_main_source = (
                path is not None
                and path.endswith(SOURCE_SUFFIXES)
                and any(f"{d}/" in path for d in SOURCE_DIRS)
            )
            current = path if is_main_source else None
        elif current and (m := HUNK.match(line)):
            start, count = int(m.group(1)), int(m.group(2) or 1)
            if count:
                result.setdefault(current, set()).update(range(start, start + count))
    return result


def module_of(path):
    for d in SOURCE_DIRS:
        marker = f"{d}/"
        if marker in path:
            prefix = path.split(marker, 1)[0].rstrip("/")
            return prefix or "."
    return "."


def load_reports():
    lines, modules = {}, set()
    for report in sorted(Path(".").glob(REPORT_GLOB)):
        module_dir = report.parents[3]
        module = module_dir.as_posix()
        modules.add(module)
        root = ET.parse(report).getroot()
        for package in root.iter("package"):
            for sourcefile in package.iter("sourcefile"):
                rel = f"{package.get('name')}/{sourcefile.get('name')}"
                info = {
                    int(l.get("nr")): int(l.get("ci", "0")) > 0
                    for l in sourcefile.iter("line")
                }
                for d in SOURCE_DIRS:
                    path = f"{d}/{rel}" if module == "." else f"{module}/{d}/{rel}"
                    lines[path] = info
    return lines, modules


def analyse(base):
    changed = changed_lines(base)
    coverage, modules_with_report = load_reports()
    results = {}

    for path, line_numbers in sorted(changed.items()):
        module = module_of(path)
        res = results.setdefault(module, ModuleResult())
        if module not in modules_with_report:

            res.missing_report = True
            continue
        info = coverage.get(path, {})
        for n in sorted(line_numbers):
            if n in info:
                res.new += 1
                if info[n]:
                    res.covered += 1
                else:
                    res.uncovered.append(f"{path}:{n}")
    return results


def render(results, threshold, passed, total_new, total_covered):
    overall = 100.0 * total_covered / total_new if total_new else None
    out = [MARKER, "## New code coverage", ""]

    if not results:
        out.append("No new or changed main source code in this PR.")
        return "\n".join(out) + "\n"

    out += ["| Module | New lines | Covered | Coverage |", "|---|---:|---:|---:|"]
    for module, r in sorted(results.items()):
        if r.missing_report:
            cell = "❌ no coverage data (no tests?)"
            out.append(f"| `{module}` | ? | ? | {cell} |")
        else:
            pct = "N/A (no executable lines)" if r.percent is None else f"{r.percent:.1f}%"
            out.append(f"| `{module}` | {r.new} | {r.covered} | {pct} |")

    overall_text = "N/A" if overall is None else f"{overall:.1f}%"
    verdict = "✅ passed" if passed else "❌ failed"
    out += ["", f"**Overall: {overall_text}** (minimum {threshold:g}%) {verdict}", ""]

    uncovered = [u for r in results.values() for u in r.uncovered]
    if uncovered:
        out += ["<details><summary>Uncovered new lines</summary>", "", "```"]
        out += uncovered
        out += ["```", "</details>", ""]
    if any(r.missing_report for r in results.values()):
        out.append("Modules marked ❌ have new code but produced no coverage report. "
                   "Add tests for them.")
    return "\n".join(out) + "\n"


def main():
    parser = argparse.ArgumentParser(description="Coverage of new code in a pull request, per module.")
    parser.add_argument("--base", required=True, help="base branch/commit, e.g. origin/main")
    parser.add_argument("--threshold", type=float, default=95.0)
    parser.add_argument("--output", default="coverage.md")
    args = parser.parse_args()

    try:
        results = analyse(args.base)
    except subprocess.CalledProcessError as e:
        print(f"git diff failed: {e.stderr.strip()}", file=sys.stderr)
        return 2
    except ET.ParseError as e:
        print(f"could not read a JaCoCo report: {e}", file=sys.stderr)
        return 2

    total_new = sum(r.new for r in results.values())
    total_covered = sum(r.covered for r in results.values())
    missing = any(r.missing_report for r in results.values())
    below = total_new > 0 and 100.0 * total_covered / total_new < args.threshold
    passed = not missing and not below

    report = render(results, args.threshold, passed, total_new, total_covered)
    Path(args.output).write_text(report, encoding="utf-8")
    print(report)
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
