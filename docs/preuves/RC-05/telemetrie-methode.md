# RC-05 sur la télémétrie : méthode et réserves (18 nov. 2026)

Résultat : RÉUSSI sur kind. Pod consommateur supprimé sous charge (200 mesures/s pendant 180 s), service rétabli en 22 s,
jamais moins d'un pod prêt, 36000 mesures acquittées = 36000 stockées, retard Kafka revenu à 0.
Rapport brut : telemetrie-sous-charge.txt.

## Environnement du test
- Cluster kind (pas k3s). Image aquaops/telemetry:local construite depuis main. Manifeste deploy/k8s/telemetry/deployment.yaml
  avec l'image locale et KAFKA_BOOTSTRAP_SERVERS=aquaops-kafka:9094 (copie locale, dépôt inchangé), 2 réplicas.
- Kafka et PostgreSQL : conteneurs de Docker Compose, hors du cluster. Second listener Kafka (KIND://aquaops-kafka:9094)
  ajouté par un fichier de surcharge Compose local, non commité (l'adresse annoncée par défaut est localhost:9092).
- Simulateur lancé sur la machine avec GATEWAY_URL=http://localhost:8081 : port-forward direct vers un pod de télémétrie,
  sans passerelle (le service de télémétrie n'exige pas la clé API).

## Écarts avec deploy/demo/rc05.sh (copie locale du script)
- docker exec à la place de kubectl exec pour Kafka et PostgreSQL (hors cluster).
- Pod supprimé forcé : celui qui détenait la partition sensor-readings-0 (vérifié dans ses journaux). Depuis kind, l'IP vue
  par Kafka est celle du nœud, pas celle du pod : le script ne pouvait pas l'identifier seul.

## Réserves
- Un seul essai, sur kind, avec une image locale (non issue du pipeline).
- Arrêt gracieux (kubectl delete pod). Le cas d'un pod tué sans préavis n'est pas testé.
- Le pod qui recevait la charge n'a pas été supprimé.
- Passerelle non traversée (clé API et limitation de débit non exercées).
- À refaire sur le cluster k3s d'E5.
