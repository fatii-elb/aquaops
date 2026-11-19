"""Tests du validateur : python3 -m unittest discover -s deploy/k8s/rc-06 -p 'test_*.py'"""
import copy
import unittest
from pathlib import Path

import validate_manifests as v

MANIFESTS = Path(__file__).resolve().parent.parent / "detection-service"


def docs():
    return copy.deepcopy(v.load(MANIFESTS))


def deployment(ds):
    return next(d for d in ds if d["kind"] == "Deployment")


def container(ds):
    return deployment(ds)["spec"]["template"]["spec"]["containers"][0]


class ManifestsTest(unittest.TestCase):
    def test_les_manifestes_livres_sont_conformes(self):
        self.assertEqual([], v.check(docs()))

    def test_maxunavailable_non_nul_refuse(self):
        ds = docs()
        deployment(ds)["spec"]["strategy"]["rollingUpdate"]["maxUnavailable"] = 1
        self.assertTrue(any("maxUnavailable" in p for p in v.check(ds)))

    def test_maxsurge_nul_refuse(self):
        ds = docs()
        deployment(ds)["spec"]["strategy"]["rollingUpdate"]["maxSurge"] = 0
        self.assertTrue(any("maxSurge" in p for p in v.check(ds)))

    def test_strategie_recreate_refusee(self):
        ds = docs()
        deployment(ds)["spec"]["strategy"] = {"type": "Recreate"}
        self.assertTrue(any("RollingUpdate" in p for p in v.check(ds)))

    def test_un_seul_replica_refuse(self):
        ds = docs()
        deployment(ds)["spec"]["replicas"] = 1
        self.assertTrue(any("replicas" in p for p in v.check(ds)))

    def test_sonde_de_disponibilite_obligatoire(self):
        ds = docs()
        del container(ds)["readinessProbe"]
        self.assertTrue(any("readinessProbe" in p for p in v.check(ds)))

    def test_sonde_de_vivacite_et_de_demarrage_obligatoires(self):
        ds = docs()
        del container(ds)["livenessProbe"]
        del container(ds)["startupProbe"]
        problems = v.check(ds)
        self.assertTrue(any("livenessProbe" in p for p in problems))
        self.assertTrue(any("startupProbe" in p for p in problems))

    def test_readiness_doit_utiliser_la_sonde_de_disponibilite(self):
        ds = docs()
        container(ds)["readinessProbe"]["httpGet"]["path"] = "/actuator/health/liveness"
        self.assertTrue(any("/actuator/health/readiness" in p for p in v.check(ds)))

    def test_prestop_obligatoire(self):
        ds = docs()
        del container(ds)["lifecycle"]
        self.assertTrue(any("preStop" in p for p in v.check(ds)))

    def test_delai_de_terminaison_trop_court_refuse(self):
        ds = docs()
        deployment(ds)["spec"]["template"]["spec"]["terminationGracePeriodSeconds"] = 40
        self.assertTrue(any("terminationGracePeriodSeconds" in p for p in v.check(ds)))

    def test_ressources_obligatoires(self):
        ds = docs()
        del container(ds)["resources"]["limits"]["memory"]
        self.assertTrue(any("resources.limits.memory" in p for p in v.check(ds)))

    def test_image_latest_refusee(self):
        ds = docs()
        container(ds)["image"] = "ghcr.io/x/detection-service:latest"
        self.assertTrue(any("latest" in p for p in v.check(ds)))

    def test_secret_en_clair_refuse(self):
        ds = docs()
        container(ds)["env"].append({"name": "DB_PASSWORD_BACKUP", "value": "aquaops"})
        self.assertTrue(any("en clair" in p for p in v.check(ds)))

    def test_pdb_obligatoire(self):
        ds = [d for d in docs() if d["kind"] != "PodDisruptionBudget"]
        self.assertTrue(any("PodDisruptionBudget" in p for p in v.check(ds)))

    def test_service_incoherent_refuse(self):
        ds = docs()
        next(d for d in ds if d["kind"] == "Service")["spec"]["selector"] = {"app": "autre"}
        self.assertTrue(any("Service" in p for p in v.check(ds)))

    def test_aucun_secret_dans_le_configmap(self):
        ds = docs()
        next(d for d in ds if d["kind"] == "ConfigMap")["data"]["DB_PASSWORD"] = "x"
        self.assertTrue(any("ConfigMap" in p for p in v.check(ds)))


if __name__ == "__main__":
    unittest.main()
