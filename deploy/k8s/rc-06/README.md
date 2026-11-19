# RC-06 : mise à jour sans interruption (NFR-07)

Critère : **0 erreur HTTP 5xx pendant une mise à jour progressive sous trafic continu.**
Ces fichiers préparent le test. **RC-06 n'a PAS été exécuté sur un cluster** : il reste à passer sur la VM k3s d'E5.

| Fichier | Rôle |
|---|---|
| `../detection-service/` | Manifestes du service de détection : `RollingUpdate`, `maxUnavailable: 0`, `maxSurge: 1`, 2 replicas, sondes, ressources, `preStop`, arrêt gracieux, PodDisruptionBudget |
| `validate_manifests.py` | Vérifie les règles de mise à jour sans coupure sur un dossier de manifestes (utilisable pour les manifestes des autres services d'E5) |
| `run-rc06.sh` | Scénario RC-06 : trafic continu dans le cluster, mise à jour, comptage des codes HTTP |
| `tally.sh` | Dépouille le journal de trafic (échec si 5xx ou connexion refusée) |
| `test_*.py`, `fake-kubectl.sh` | Tests de la logique des scripts avec un `kubectl` simulé (ne prouvent rien sur Kubernetes) |

## Pourquoi cette configuration évite les 5xx

1. `maxUnavailable: 0` et `maxSurge: 1` : un nouveau pod est créé et doit être **prêt** avant qu'un ancien soit retiré. `minReadySeconds: 10` évite de retirer l'ancien trop tôt.
2. `readinessProbe` sur `/actuator/health/readiness` : un pod pas prêt ne reçoit aucun trafic et bloque la mise à jour.
3. À l'arrêt d'un pod : `preStop` (20 s) laisse le temps de le retirer des Services, d'Eureka et du cache du répartiteur de charge ; puis Spring Boot (`server.shutdown: graceful`, 30 s) termine les requêtes en cours ; `terminationGracePeriodSeconds: 60` couvre le tout.
4. Eureka : renouvellement 5 s, expiration 15 s (disparition du registre en 45 s au plus).

## Lancer le test (cluster k3s, namespace `aquaops`)

```bash
# 1) Manifestes conformes (hors cluster)
python3 deploy/k8s/rc-06/validate_manifests.py deploy/k8s/detection-service

# 2) Déployer la version initiale
kubectl apply -k deploy/k8s/detection-service        # après avoir fixé le tag de l'image (voir kustomization.yaml)
kubectl -n aquaops rollout status deployment/detection-service

# 3) RC-06 : mise à jour sous trafic (nouvelle image, sinon redémarrage progressif)
NEW_IMAGE=ghcr.io/fatii-elb/aquaops/detection-service:<nouveau-sha> \
  bash deploy/k8s/rc-06/run-rc06.sh
# Résultats dans rc06-report-<date>/ : summary.txt, traffic.log, pods-before.txt, pods-after.txt
```

Variables : `NAMESPACE`, `DEPLOYMENT`, `CONTAINER`, `NEW_IMAGE`, `TARGET_URL`, `CURL_HEADER` (jeton pour une API protégée, jamais affiché), `INTERVAL`, `WARMUP`, `COOLDOWN`, `ROLLOUT_TIMEOUT`, `CURL_IMAGE`.
Par défaut, le trafic vise le **Service** du déploiement (`/actuator/health/readiness`, sans authentification).
Pour tester le chemin complet, pointer `TARGET_URL` vers la passerelle (route `/api/detection/...` avec `CURL_HEADER`).

## Points d'attention (E5)

- **Passerelle et Eureka** : le cache du répartiteur de charge de la passerelle (35 s par défaut) peut continuer à viser un pod en arrêt. Régler `spring.cloud.loadbalancer.cache.ttl` à quelques secondes et activer une nouvelle tentative sur les requêtes de lecture, sinon des 5xx peuvent apparaître via la passerelle alors que le Service direct est propre.
- Le `Secret` `aquaops-db-credentials` (clés `username`, `password`) et les noms de Service (`kafka`, `postgres`, `keycloak`, `eureka`) du `ConfigMap` sont des hypothèses à aligner sur le Terraform d'E5.
- Le même gabarit s'applique aux autres services : même stratégie, mêmes sondes, `SHUTDOWN_TIMEOUT`, `preStop`. `server.shutdown: graceful` et les sondes `/actuator/health/{liveness,readiness}` doivent être activés dans chaque service (seul le service de détection l'est ici).
