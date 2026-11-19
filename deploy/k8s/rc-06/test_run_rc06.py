"""Teste la LOGIQUE de run-rc06.sh avec un kubectl simule (fake-kubectl.sh). Ne remplace pas RC-06 sur un cluster."""
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent


def run(**env):
    out = tempfile.mkdtemp()
    full = {**os.environ, "KUBECTL": str(HERE / "fake-kubectl.sh"), "OUT_DIR": out, "WARMUP": "0", "COOLDOWN": "0",
            "FAKE_LOG": str(Path(out) / "commands.log"), **{k: str(v) for k, v in env.items()}}
    r = subprocess.run(["bash", str(HERE / "run-rc06.sh")], capture_output=True, text=True, env=full, timeout=60)
    return r, Path(out)


class RunRc06Test(unittest.TestCase):
    def test_zero_erreur_5xx_donne_un_succes_et_un_rapport(self):
        r, out = run()
        self.assertEqual(0, r.returncode, r.stdout + r.stderr)
        self.assertIn("RC-06 REUSSI", r.stdout)
        self.assertTrue((out / "traffic.log").exists() and (out / "summary.txt").exists())
        self.assertIn("5xx=0", (out / "summary.txt").read_text())

    def test_une_erreur_5xx_donne_un_echec(self):
        r, _ = run(FAKE_5XX=3)
        self.assertEqual(1, r.returncode)
        self.assertIn("5xx=3", r.stdout)
        self.assertIn("RC-06 ECHEC", r.stdout)

    def test_une_coupure_de_connexion_donne_un_echec(self):
        r, _ = run(FAKE_CONN=1)
        self.assertEqual(1, r.returncode)
        self.assertIn("connexion=1", r.stdout)

    def test_une_mise_a_jour_qui_n_aboutit_pas_donne_un_echec(self):
        r, _ = run(FAKE_ROLLOUT_FAIL=1)
        self.assertEqual(1, r.returncode)
        self.assertIn("n'a pas abouti", r.stdout)

    def test_un_journal_vide_ne_prouve_rien(self):
        r, _ = run(FAKE_EMPTY_LOG=1)
        self.assertEqual(1, r.returncode)
        self.assertIn("aucune requete enregistree", r.stdout)

    def test_maxunavailable_non_nul_est_refuse_avant_tout_trafic(self):
        r, out = run(FAKE_MAX_UNAVAILABLE=1)
        self.assertEqual(1, r.returncode)
        self.assertIn("maxUnavailable", r.stdout)
        self.assertNotIn(" run ", (out / "commands.log").read_text())

    def test_un_seul_replica_pret_est_refuse(self):
        r, _ = run(FAKE_READY=1)
        self.assertEqual(1, r.returncode)
        self.assertIn("2 au moins", r.stdout)

    def test_avec_une_image_la_commande_set_image_est_utilisee(self):
        r, out = run(NEW_IMAGE="ghcr.io/x/detection-service:v2")
        self.assertEqual(0, r.returncode, r.stdout)
        self.assertIn("set image deployment/detection-service detection-service=ghcr.io/x/detection-service:v2",
                      (out / "commands.log").read_text())

    def test_sans_image_le_redemarrage_progressif_est_utilise(self):
        _, out = run()
        self.assertIn("rollout restart deployment/detection-service", (out / "commands.log").read_text())

    def test_l_en_tete_d_authentification_n_est_jamais_affiche(self):
        r, _ = run(CURL_HEADER="Authorization: Bearer SECRET-TOKEN-123")
        self.assertNotIn("SECRET-TOKEN-123", r.stdout + r.stderr)


class TallyTest(unittest.TestCase):
    def tally(self, content):
        with tempfile.NamedTemporaryFile("w", suffix=".log", delete=False) as f:
            f.write(content)
        return subprocess.run(["bash", str(HERE / "tally.sh"), f.name], capture_output=True, text=True)

    def test_compte_par_classe(self):
        r = self.tally("1 200\n2 200\n3 301\n4 404\n")
        self.assertEqual(0, r.returncode)
        self.assertIn("total=4 2xx=2 3xx=1 4xx=1 5xx=0 connexion=0", r.stdout)

    def test_5xx_et_000_echouent(self):
        self.assertEqual(1, self.tally("1 200\n2 502\n").returncode)
        self.assertEqual(1, self.tally("1 200\n2 000\n").returncode)

    def test_journal_vide_ou_illisible(self):
        self.assertEqual(2, self.tally("").returncode)
        self.assertEqual(2, self.tally("pas un journal\n").returncode)


if __name__ == "__main__":
    unittest.main()
