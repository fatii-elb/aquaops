#!/usr/bin/env python3
"""Fait echouer le job Semgrep si le rapport contient une constatation de gravite elevee (ERROR).

Usage : python3 .github/scripts/semgrep_gate.py semgrep-report.json
Ecrit un resume dans le resume de l'execution GitHub ($GITHUB_STEP_SUMMARY) quand il est disponible.
Code de sortie : 0 aucune constatation ERROR, 1 au moins une, 2 rapport illisible.
"""
import json
import os
import sys
from collections import Counter

HIGH = "ERROR"


def load(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, json.JSONDecodeError) as e:
        print(f"Rapport Semgrep illisible ({path}) : {e}", file=sys.stderr)
        sys.exit(2)


def summarize(report):
    results = report.get("results", [])
    errors = report.get("errors", [])
    by_severity = Counter(r.get("extra", {}).get("severity", "INCONNUE") for r in results)
    high = [r for r in results if r.get("extra", {}).get("severity") == HIGH]
    return results, errors, by_severity, high


def main(argv):
    if len(argv) != 2:
        print(__doc__, file=sys.stderr)
        return 2
    results, errors, by_severity, high = summarize(load(argv[1]))

    lines = [
        "## Semgrep",
        "",
        f"- Constatations : {len(results)} (gravite elevee ERROR : {by_severity.get(HIGH, 0)}, "
        f"WARNING : {by_severity.get('WARNING', 0)}, INFO : {by_severity.get('INFO', 0)})",
        f"- Erreurs d'analyse : {len(errors)}",
    ]
    for r in high:
        start = r.get("start", {}).get("line", "?")
        lines.append(f"- **ERROR** `{r.get('check_id')}` : {r.get('path')}:{start}")
    text = "\n".join(lines)
    print(text)

    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(text + "\n")

    if high:
        print(f"\n{len(high)} constatation(s) de gravite elevee : corrigez-les ou justifiez-les "
              "(// nosemgrep: <regle> -- <justification>).", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
