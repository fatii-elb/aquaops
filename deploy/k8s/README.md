# Manifestes Kubernetes (k3s) : parties de E2

Namespace `aquaops`. Ces fichiers sont indépendants de Terraform : E5 peut les appliquer avec `kubectl apply -f`
ou les intégrer dans son déploiement (SCRUM-56). Rien ici n'a besoin d'un cloud : k3s tourne sur la VM.

| Dossier | Carte | Contenu |
| --- | --- | --- |
| `assets/` | NFR-05 | Service des actifs : 2 réplicas, sondes liveness/readiness (Actuator), mise à jour sans coupure, arrêt gracieux |
| `telemetry/` | NFR-05 | Service télémétrie : 2 réplicas, sondes Actuator, mise à jour sans coupure (base du HPA de E1) |
| `tls/` | NFR-14 | Certificat auto-signé (cert-manager), options TLS (`tlsoption.yaml` : TLS 1.2+ et chiffrements AEAD) et Ingress HTTPS (Traefik, fourni par k3s) |

## Prérequis (fournis par E5 / le cluster)

- Secret `aquaops-db` (clés `DB_USER`, `DB_PASSWORD`) : créé par Terraform, jamais dans le dépôt.
- Services du cluster nommés `postgres`, `kafka`, `keycloak`, `eureka`, `frontend`.
- cert-manager installé (`tls/`).

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

## Vérifier NFR-14 (TLS)

```bash
curl -vk https://aquaops.local/           # HTTPS, certificat auto-signé (-k car pas de CA publique)
./testssl.sh https://aquaops.local        # aucun protocole faible (SSLv3, TLS 1.0/1.1) ni chiffrement faible
```
Ajouter `aquaops.local` au fichier hosts de chaque poste (adresse IP de la VM).
