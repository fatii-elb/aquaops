# RC-10 : trace complète d'un scénario de fuite (NFR-11)

Carte SCRUM-58 · E4. Résultat : **réussi** (exécution locale, pile d'intégration avec traçage).

## Procédure
1. `docker compose -f deploy/integration/docker-compose.integration.yml -f deploy/tracing/docker-compose.tracing.yml up -d`
2. `bash deploy/demo/rc10.sh` : lance le scénario « fuite » du simulateur, attend l'incident, cherche dans Jaeger la trace qui couvre le plus de services.
3. Interface Jaeger : http://localhost:16686

## Résultat observé
- Trace `32be7ebcade4392c03187826f1dc406e` : **4 services** (gateway, telemetry-service, detection-service, incidents-service), **30 spans**, profondeur 10, durée 1,63 s.
- Chaîne : `gateway POST telemetry` → `telemetry-service POST /api/telemetry/readings` → publication `sensor-readings` (Kafka) → traitement par telemetry-service et detection-service → publication `anomaly-detected` → traitement par incidents-service (création de l'incident) → publication `incident-events`.
- Le contexte de trace traverse Kafka (en-têtes de message, géré par l'agent OpenTelemetry) : une seule trace relie la mesure, l'anomalie et l'incident.
- Preuves : `trace-rc10.png` (capture de la timeline), `trace-32be7ebcade4392c03187826f1dc406e.json` (export de la trace).

## Limites
- Jaeger stocke en mémoire (5000 traces) : les traces disparaissent au redémarrage, d'où l'export JSON.
- Le simulateur n'est pas instrumenté : la trace commence à la passerelle.
- Test réalisé avec Docker Compose ; le déploiement de Jaeger sur k3s reste à faire.
