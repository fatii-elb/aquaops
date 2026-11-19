#!/usr/bin/env python3
"""Verifie qu'un dossier de manifestes Kubernetes respecte les regles de mise a jour sans interruption (NFR-07).

Usage : python3 validate_manifests.py ../detection-service
Controles : RollingUpdate avec maxUnavailable 0 et maxSurge >= 1, au moins 2 replicas, sondes de disponibilite,
de vivacite et de demarrage, preStop, delai de terminaison suffisant, ressources, PodDisruptionBudget,
Service coherent, image sans « latest », aucun secret en clair. Necessite PyYAML.
Code de sortie : 0 conforme, 1 au moins une regle non respectee.
"""
import re
import sys
from pathlib import Path

import yaml


def load(directory):
    docs = []
    for path in sorted(Path(directory).glob("*.y*ml")):
        if path.name.startswith("kustomization"):
            continue
        docs.extend(d for d in yaml.safe_load_all(path.read_text(encoding="utf-8")) if d)
    return docs


def seconds(text):
    """'30s' -> 30, '2m' -> 120."""
    m = re.fullmatch(r"(\d+)(ms|s|m|h)?", str(text).strip())
    if not m:
        return None
    n, unit = int(m.group(1)), m.group(2) or "s"
    return {"ms": n / 1000, "s": n, "m": n * 60, "h": n * 3600}[unit]


def zero(value):
    return str(value) in ("0", "0%")


def check(docs):
    problems = []
    by_kind = {}
    for d in docs:
        by_kind.setdefault(d.get("kind"), []).append(d)

    deployments = by_kind.get("Deployment", [])
    if not deployments:
        return ["Aucun Deployment dans le dossier."]

    configmaps = {c["metadata"]["name"]: c.get("data", {}) for c in by_kind.get("ConfigMap", [])}

    for dep in deployments:
        name = dep["metadata"]["name"]
        spec = dep["spec"]
        tpl = spec["template"]
        pod = tpl["spec"]

        def fail(msg):
            problems.append(f"[{name}] {msg}")

        strategy = spec.get("strategy", {})
        rolling = strategy.get("rollingUpdate", {})
        if strategy.get("type") != "RollingUpdate":
            fail("strategy.type doit valoir RollingUpdate.")
        if not zero(rolling.get("maxUnavailable", 1)):
            fail("maxUnavailable doit valoir 0 : aucun pod ne doit etre retire avant que le nouveau soit pret.")
        surge = rolling.get("maxSurge", 0)
        if zero(surge):
            fail("maxSurge doit etre superieur a 0 : il faut pouvoir creer un pod en plus.")
        if spec.get("replicas", 1) < 2:
            fail("replicas doit etre au moins 2.")
        if spec.get("minReadySeconds", 0) < 5:
            fail("minReadySeconds doit etre au moins 5.")
        if not spec.get("progressDeadlineSeconds"):
            fail("progressDeadlineSeconds absent : une mise a jour bloquee ne serait jamais signalee.")

        for container in pod.get("containers", []):
            cname = container["name"]
            for probe in ("readinessProbe", "livenessProbe", "startupProbe"):
                p = container.get(probe)
                if not p or "httpGet" not in p:
                    fail(f"{cname} : {probe} HTTP manquante.")
                elif not str(p["httpGet"].get("path", "")).startswith("/actuator/health/"):
                    fail(f"{cname} : {probe} doit interroger /actuator/health/liveness ou /readiness.")
            ready = container.get("readinessProbe", {}).get("httpGet", {}).get("path", "")
            live = container.get("livenessProbe", {}).get("httpGet", {}).get("path", "")
            if ready and not ready.endswith("/readiness"):
                fail(f"{cname} : readinessProbe doit utiliser /actuator/health/readiness.")
            if live and not live.endswith("/liveness"):
                fail(f"{cname} : livenessProbe doit utiliser /actuator/health/liveness.")

            res = container.get("resources", {})
            for part in ("requests", "limits"):
                for key in ("cpu", "memory"):
                    if key not in res.get(part, {}) and not (part == "limits" and key == "cpu"):
                        fail(f"{cname} : resources.{part}.{key} manquant.")

            image = container.get("image", "")
            if ":" not in image.rsplit("/", 1)[-1] or image.endswith(":latest"):
                fail(f"{cname} : l'image doit avoir un tag fixe (pas « latest »).")

            for env in container.get("env", []):
                if re.search(r"PASSWORD|SECRET|TOKEN|KEY", env["name"], re.I) and "value" in env:
                    fail(f"{cname} : {env['name']} est en clair ; utiliser un secretKeyRef.")

            pre = container.get("lifecycle", {}).get("preStop", {})
            pre_seconds = None
            command = pre.get("exec", {}).get("command", [])
            if command[:1] == ["sleep"] and len(command) > 1:
                pre_seconds = seconds(command[1])
            elif "sleep" in pre:
                pre_seconds = pre["sleep"].get("seconds")
            if pre_seconds is None:
                fail(f"{cname} : lifecycle.preStop (sleep) manquant : le pod risque de recevoir des requetes en s'arretant.")

            shutdown = None
            for ref in container.get("envFrom", []):
                data = configmaps.get(ref.get("configMapRef", {}).get("name"), {})
                if "SHUTDOWN_TIMEOUT" in data:
                    shutdown = seconds(data["SHUTDOWN_TIMEOUT"])
                for k, v in data.items():
                    if re.search(r"PASSWORD|SECRET|TOKEN", k, re.I):
                        fail(f"ConfigMap : {k} ne doit pas etre dans un ConfigMap.")
            grace = pod.get("terminationGracePeriodSeconds", 30)
            if pre_seconds is not None and shutdown is not None and grace < pre_seconds + shutdown + 5:
                fail(f"terminationGracePeriodSeconds ({grace}) doit depasser preStop ({pre_seconds}) + arret gracieux ({shutdown}) + 5 s.")
            if shutdown is None:
                fail("SHUTDOWN_TIMEOUT absent du ConfigMap : l'arret gracieux n'est pas borne.")

        labels = tpl["metadata"]["labels"]
        match = spec["selector"]["matchLabels"]
        if any(labels.get(k) != v for k, v in match.items()):
            fail("selector.matchLabels ne correspond pas aux etiquettes du pod.")

        pdbs = [p for p in by_kind.get("PodDisruptionBudget", []) if p["spec"]["selector"]["matchLabels"] == match]
        if not pdbs:
            fail("PodDisruptionBudget manquant.")
        for pdb in pdbs:
            if not (pdb["spec"].get("minAvailable", 0) >= 1 or zero(pdb["spec"].get("maxUnavailable", 1))):
                fail("PodDisruptionBudget : minAvailable >= 1 (ou maxUnavailable 0) requis.")

        services = [s for s in by_kind.get("Service", []) if s["spec"]["selector"] == match]
        if not services:
            fail("Aucun Service ne selectionne ce Deployment.")
        for svc in services:
            port_names = {p.get("name") for c in pod["containers"] for p in c.get("ports", [])}
            for port in svc["spec"]["ports"]:
                target = port.get("targetPort")
                if isinstance(target, str) and target not in port_names:
                    fail(f"Service : targetPort « {target} » absent des ports du conteneur.")
    return problems


def main(argv):
    if len(argv) != 2:
        print(__doc__, file=sys.stderr)
        return 1
    problems = check(load(argv[1]))
    if problems:
        print("Manifestes NON conformes :")
        for p in problems:
            print(f"  - {p}")
        return 1
    print("Manifestes conformes (RollingUpdate, maxUnavailable 0, sondes, arret gracieux, ressources, PDB).")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
