# Manifestes Kubernetes (k3s) : parties de E2

Namespace `aquaops`. Ces fichiers sont indépendants de Terraform : E5 peut les appliquer avec `kubectl apply -f`
ou les intégrer dans son déploiement (SCRUM-56). Rien ici n'a besoin d'un cloud : k3s tourne sur la VM.

| Dossier | Carte | Contenu |
| --- | --- | --- |
| `assets/` | NFR-05 | Service des actifs : 2 réplicas, sondes liveness/readiness (Actuator), mise à jour sans coupure, arrêt gracieux |
| `telemetry/` | NFR-04, NFR-05 | Service télémétrie : HPA CPU (2 à 6 pods, cible 70 %), client Eureka, sondes Actuator, mise à jour sans coupure |
| `tls/` | NFR-14 | Certificat auto-signé (cert-manager), options TLS (`tlsoption.yaml` : TLS 1.2+ et chiffrements AEAD) et Ingress HTTPS (Traefik, fourni par k3s) |

## Prérequis (fournis par E5 / le cluster)

- Secret `aquaops-db` (clés `DB_USER`, `DB_PASSWORD`) : créé par Terraform, jamais dans le dépôt.
- Services du cluster nommés `postgres`, `kafka`, `keycloak`, `eureka`, `frontend`.
- cert-manager installé (`tls/`).
- namespace `aquaops` créé (`kubectl create namespace aquaops`) : aucun manifeste du dépôt ne le crée.
- service `frontend` sur le port 8080 présent dans ce namespace : sans lui, Traefik ignore la route et l'option TLS ne s'applique pas.

## Appliquer

```bash
kubectl create namespace aquaops --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -f deploy/k8s/assets/
kubectl apply -f deploy/k8s/telemetry/
kubectl apply -f deploy/k8s/tls/          # après cert-manager
kubectl -n aquaops rollout status deploy/assets
```

L'étiquette d'image `IMAGE_TAG` est à remplacer par le SHA du commit (le pipeline le fait avec `sed` ou Terraform).

## Vérifier NFR-05 (reprise après suppression d'un pod)

```bash
kubectl -n aquaops get pods -l app=assets          # 2 pods Ready
kubectl -n aquaops delete pod -l app=assets --field-selector=status.phase=Running --wait=false | head -1
kubectl -n aquaops get pods -l app=assets -w       # un nouveau pod passe Ready en moins de 60 s
```
Pendant ce temps le second pod répond encore : le service n'est jamais coupé. Les mesures (Kafka) ne sont pas perdues car le groupe
de consommateurs reprend à l'offset validé (`assets-service-audit`, validation après traitement).

## Vérifier NFR-04 (HPA telemetry)

Le cluster doit exposer `metrics.k8s.io` (metrics-server). Le Deployment demande
200m CPU par pod ; la cible de 70 % correspond à 140m CPU moyen par pod.

Dans un terminal, observer le HPA et les pods :

```bash
kubectl -n aquaops get hpa telemetry -w
kubectl -n aquaops top pods -l app=telemetry
```

Dans un autre terminal, lancer la charge via le simulateur pendant cinq minutes :

```bash
kubectl -n aquaops port-forward svc/simulator 8086:8086
curl -i -X POST "http://localhost:8086/api/simulator/load/start?rate=500&seconds=300"
curl -i http://localhost:8086/api/simulator/load
```

À la fin de la charge, suivre le HPA jusqu'au retour à 2 réplicas (la stabilisation
de la baisse peut prendre quelques minutes) et conserver les observations comme
preuve RC-04. Le HPA autorise jusqu'à 6 pods ; le quota et la capacité mémoire du
cluster peuvent limiter le nombre réellement planifiable. Le nombre de pods ne
prouve pas à lui seul un débit Kafka accru : `sensor-readings` reste à une
partition.

## Vérifier NFR-14 (TLS)

```bash
curl -vk https://aquaops.local/           # HTTPS, certificat auto-signé (-k car pas de CA publique)
./testssl.sh https://aquaops.local        # aucun protocole faible (SSLv3, TLS 1.0/1.1) ni chiffrement faible
```
Ajouter `aquaops.local` au fichier hosts de chaque poste (adresse IP de la VM).

### Vérifier NFR-14 en local (kind)

```bash
kubectl port-forward -n traefik svc/traefik 8443:443   # dans un onglet dédié
echo "127.0.0.1 aquaops.local" | sudo tee -a /etc/hosts
./testssl.sh --ip 127.0.0.1 --nodns min aquaops.local:8443
```

Attendu : TLS 1.2 et 1.3 seulement, `Obsoleted CBC ciphers` : `not offered`, LUCKY13 : `not vulnerable`.
Le certificat reste auto-signé (« chain of trust NOT ok ») : normal sans autorité publique.
