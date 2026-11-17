#!/usr/bin/env python3
"""SCRUM-49 / NFR-02 : mesure du P95 mesure -> création d'incident."""

import argparse
import csv
import math
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

def percentile(values, p):
    values = sorted(values)
    rank = (len(values) - 1) * p
    low = math.floor(rank)
    high = math.ceil(rank)
    return values[low] + (values[high] - values[low]) * (rank - low)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--container", default="aquaops-postgres")
    parser.add_argument("--database", default="aquaops_incidents")
    parser.add_argument("--user", default="aquaops")
    parser.add_argument("--since", required=True,
                        help="Début du test au format ISO 8601 UTC")
    parser.add_argument("--min-samples", type=int, default=30)
    parser.add_argument("--output", default="docs/nfr02-latencies.csv")
    args = parser.parse_args()

    try:
        datetime.fromisoformat(args.since.replace("Z", "+00:00"))
    except ValueError:
        parser.error("--since doit être une date ISO 8601 valide")

    sql = """
    SELECT id, type, measured_at, created_at,
           EXTRACT(EPOCH FROM (created_at - measured_at))
    FROM incidents
    WHERE measured_at IS NOT NULL
      AND created_at >= '%s'::timestamptz
      AND type <> 'SENSOR_SILENT'
    ORDER BY created_at, id;
    """ % args.since.replace("'", "''")

    command = [
        "docker", "exec", args.container,
        "psql", "-U", args.user, "-d", args.database,
        "-A", "-F", "|", "-t", "-c", sql
    ]

    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode:
        print("Erreur PostgreSQL :", result.stderr, file=sys.stderr)
        return 2

    rows = []
    for line in result.stdout.splitlines():
        parts = line.split("|")
        if len(parts) != 5:
            continue
        incident_id, kind, measured, created, latency = parts
        try:
            seconds = float(latency)
        except ValueError:
            continue
        rows.append((incident_id, kind, measured, created, seconds))

    if not rows:
        print("Aucun incident exploitable dans cette période.")
        return 2

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", newline="", encoding="utf-8") as file:
        writer = csv.writer(file)
        writer.writerow([
            "incident_id", "type", "measured_at",
            "created_at", "latency_seconds"
        ])
        writer.writerows(rows)

    values = [row[4] for row in rows]
    p95 = percentile(values, 0.95)
    valid = sum(0 <= v <= 10 for v in values)
    negatives = sum(v < 0 for v in values)

    print("=== SCRUM-49 / NFR-02 ===")
    print(f"Incidents analysés : {len(rows)}")
    print(f"Latence moyenne    : {sum(values)/len(values):.3f} s")
    print(f"Latence P95        : {p95:.3f} s")
    print(f"Latence maximale   : {max(values):.3f} s")
    print(f"Dans les 10 s      : {valid}/{len(rows)}")
    print(f"Latences négatives : {negatives}")
    print(f"CSV                : {output}")

    if negatives:
        print("VERDICT : INVALIDE (horodatages incohérents)")
        return 2
    if len(rows) < args.min_samples:
        print("VERDICT : INSUFFISANT (échantillon trop petit)")
        return 2

    passed = p95 <= 10
    print("VERDICT :", "CONFORME" if passed else "NON CONFORME")
    return 0 if passed else 1

if __name__ == "__main__":
    sys.exit(main())
