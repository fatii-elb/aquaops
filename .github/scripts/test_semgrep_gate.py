"""Tests du script de blocage Semgrep : python3 -m unittest discover -s .github/scripts -p 'test_*.py'"""
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

GATE = Path(__file__).with_name("semgrep_gate.py")


def result(severity):
    return {"check_id": f"regle.{severity}", "path": "services/x/A.java", "start": {"line": 3}, "extra": {"severity": severity}}


def run(report):
    with tempfile.NamedTemporaryFile("w", suffix=".json", delete=False) as f:
        f.write(report if isinstance(report, str) else json.dumps(report))
    return subprocess.run([sys.executable, str(GATE), f.name], capture_output=True, text=True)


class SemgrepGateTest(unittest.TestCase):
    def test_aucun_resultat(self):
        self.assertEqual(0, run({"results": [], "errors": []}).returncode)

    def test_warning_et_info_ne_bloquent_pas(self):
        self.assertEqual(0, run({"results": [result("WARNING"), result("INFO")], "errors": []}).returncode)

    def test_une_gravite_elevee_bloque_et_est_citee(self):
        r = run({"results": [result("WARNING"), result("ERROR")], "errors": []})
        self.assertEqual(1, r.returncode)
        self.assertIn("regle.ERROR", r.stdout)

    def test_rapport_illisible(self):
        self.assertEqual(2, run("pas du json").returncode)

    def test_sans_argument(self):
        self.assertEqual(2, subprocess.run([sys.executable, str(GATE)], capture_output=True, text=True).returncode)


if __name__ == "__main__":
    unittest.main()
