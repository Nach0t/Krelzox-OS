#!/usr/bin/env python3
"""Markdown summary of a CI run: unit tests, lint issues and the release APKs.

Reads the reports Gradle already writes; prints Markdown to stdout (the workflow
appends it to $GITHUB_STEP_SUMMARY). Never fails the build: missing reports are
reported as missing.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET
import zipfile


def module_of(path: str) -> str:
    parts = path.replace("\\", "/").split("/build/")[0]
    return ":" + parts.replace("/", ":") if parts else ":"


def tests() -> list[str]:
    rows: dict[str, list[int]] = {}
    for path in glob.glob("**/build/test-results/test*UnitTest/TEST-*.xml", recursive=True):
        suite = ET.parse(path).getroot()
        counts = rows.setdefault(module_of(path), [0, 0, 0])
        counts[0] += int(suite.get("tests", 0))
        counts[1] += int(suite.get("failures", 0)) + int(suite.get("errors", 0))
        counts[2] += int(suite.get("skipped", 0))
    if not rows:
        return ["_No unit test reports found._"]
    out = ["| Module | Tests | Failed | Skipped |", "|---|---:|---:|---:|"]
    for module, (total, failed, skipped) in sorted(rows.items()):
        mark = " ❌" if failed else ""
        out.append(f"| `{module}` | {total} | {failed}{mark} | {skipped} |")
    return out


def lint() -> list[str]:
    rows = []
    for path in sorted(glob.glob("**/build/reports/lint-results-*.xml", recursive=True)):
        issues = ET.parse(path).getroot().findall("issue")
        severities: dict[str, int] = {}
        for issue in issues:
            sev = issue.get("severity", "?")
            severities[sev] = severities.get(sev, 0) + 1
        detail = ", ".join(f"{n} {s.lower()}" for s, n in sorted(severities.items())) or "clean"
        rows.append(f"| `{module_of(path)}` | {len(issues)} | {detail} |")
    if not rows:
        return ["_No lint reports found._"]
    return ["| Module | Issues | Detail |", "|---|---:|---|", *rows]


# (module, expects a Baseline Profile). Only the launcher ships one.
APKS = (("app", True), ("store", False), ("remote", False))


def apk() -> list[str]:
    rows = []
    for module, wants_profile in APKS:
        paths = glob.glob(f"{module}/build/outputs/apk/release/*.apk")
        if not paths:
            rows.append(f"| `:{module}` | _not found_ | | | |")
            continue
        path = paths[0]
        size_mb = os.path.getsize(path) / 1_000_000
        with zipfile.ZipFile(path) as z:
            names = set(z.namelist())
            dex = sorted(n for n in names if n.startswith("classes") and n.endswith(".dex"))
        if wants_profile:
            profile = "✅ included" if "assets/dexopt/baseline.prof" in names else "❌ missing"
        else:
            profile = "n/a"
        rows.append(f"| `:{module}` | `{os.path.basename(path)}` (unsigned) | {size_mb:.2f} MB | {profile} | {len(dex)} |")
    return [
        "| Module | File | Size (budget: 10 MB) | Baseline Profile | Dex files |",
        "|---|---|---:|---|---:|",
        *rows,
    ]


def main() -> int:
    lines = ["## Krelzox CI", "", "### Unit tests", *tests(), "", "### Lint", *lint(), "", "### Release APKs", *apk(), ""]
    print("\n".join(lines))
    return 0


if __name__ == "__main__":
    sys.exit(main())
