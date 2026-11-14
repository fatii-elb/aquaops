import argparse
import subprocess
import sys
import time
import urllib.request
from datetime import datetime, timezone

BASE_URL = "http://localhost:8086/api/simulator/scenarios"


def activate(scenario):
    url = f"{BASE_URL}/{scenario}/start"
    request = urllib.request.Request(url, method="POST")
    with urllib.request.urlopen(request, timeout=10) as response:
        if response.status != 200:
            raise RuntimeError(f"HTTP {response.status} : {scenario}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cycles", type=int, default=20)
    parser.add_argument("--fault-seconds", type=float, default=7)
    parser.add_argument("--normal-seconds", type=float, default=3)
    parser.add_argument("--min-samples", type=int, default=30)
    args = parser.parse_args()

    if args.cycles < 1 or args.fault_seconds <= 0 or args.normal_seconds <= 0:
        parser.error("Les cycles et les durees doivent etre positifs.")

    since = datetime.now(timezone.utc).isoformat()

    print(f"Debut campagne NFR-02 : {since}", flush=True)

    try:
        for i in range(1, args.cycles + 1):
            activate("pompe")
            print(f"Cycle {i}/{args.cycles} : FAULT", flush=True)
            time.sleep(args.fault_seconds)

            activate("normal")
            print(f"Cycle {i}/{args.cycles} : NORMAL", flush=True)
            time.sleep(args.normal_seconds)

    finally:
        try:
            activate("normal")
        except Exception as exc:
            print(f"Attention : retour NORMAL impossible : {exc}")

    print("Attente de la persistance des derniers incidents...", flush=True)
    time.sleep(5)

    command = [
        sys.executable,
        "scripts/check_nfr02.py",
        "--container", "aquaops-postgres",
        "--since", since,
        "--min-samples", str(args.min_samples),
    ]

    result = subprocess.run(command, check=False)
    sys.exit(result.returncode)


if __name__ == "__main__":
    main()
