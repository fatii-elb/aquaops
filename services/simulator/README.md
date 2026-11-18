# simulator

Simulateur de capteurs (FR-03, SCRUM-25). Il lit une liste de capteurs, génère une mesure par capteur et l'envoie en HTTP au service de télémétrie via la gateway. La gateway authentifie le simulateur et limite le débit ; le service valide la mesure puis la publie dans Kafka `sensor-readings` (contrat `contracts/events/sensor-readings.v1.json`).

Les valeurs viennent du scénario actif (voir `SCENARIOS.md`) : normal par défaut, ou une panne (fuite, pompe, silence, pic).

## Prérequis

- Java 21
- La gateway accessible sur `localhost:8080`
- Le service telemetry et Kafka accessibles via la gateway

Exemple avec Docker :

```bash
docker run -d --name aquaops-kafka -p 9092:9092 apache/kafka:4.3.1
MSYS_NO_PATHCONV=1 docker exec aquaops-kafka /opt/kafka/bin/kafka-topics.sh --create --topic sensor-readings --bootstrap-server localhost:9092
```

## Lancer

```bash
cd services/simulator
./mvnw spring-boot:run
```

Le simulateur est prêt quand le journal affiche `Started SimulatorApplication`. Il écoute sur le port 8086.

## Voir les messages

```bash
MSYS_NO_PATHCONV=1 docker exec aquaops-kafka /opt/kafka/bin/kafka-console-consumer.sh --topic sensor-readings --bootstrap-server localhost:9092 --from-beginning --max-messages 14
```

Exemple de message :

```json
{"eventVersion":"1","sensorId":1,"zoneId":1,"type":"PRESSURE","value":3.0,"unit":"bar","measuredAt":"2026-11-06T11:06:57.873489200Z"}
```

Un capteur `PUMP_STATE` envoie `pumpState` (`NORMAL` ou `FAULT`) à la place de `value` et `unit`.

## Variables d'environnement

| Variable | Valeur par défaut | Rôle |
| --- | --- | --- |
| `SIMULATOR_PORT` | `8086` | Port HTTP du simulateur |
| `GATEWAY_URL` | `http://localhost:8080` | Adresse de la gateway |
| `SIMULATOR_API_KEY` | `local-dev-only` | Clé partagée avec la gateway ; utiliser une valeur aléatoire hors démo locale |
| `SIM_INTERVAL_MS` | `1000` | Intervalle entre deux envois, en millisecondes |
| `SIMULATOR_REQUEST_TIMEOUT` | `5s` | Délai maximal d'une requête d'ingestion |
| `SENSORS_FILE` | `classpath:sensors.json` | Fichier des capteurs. Pour un fichier externe : `file:C:/chemin/sensors.json` |
| `EUREKA_ENABLED` | `false` | Active l'inscription optionnelle du simulateur dans Eureka |
| `EUREKA_URL` | `http://localhost:8761/eureka` | URL du serveur Eureka lorsque l'inscription est activée |

## Liste des capteurs

Le fichier `src/main/resources/sensors.json` contient les capteurs : `sensorId` et `zoneId` (entiers) et `type` (`PRESSURE`, `FLOW`, `LEVEL` ou `PUMP_STATE`). Pour ajouter un capteur, ajouter une ligne dans ce fichier.

## Scénarios de panne

Voir `SCENARIOS.md`. Exemple :

```bash
curl -X POST http://localhost:8086/api/simulator/scenarios/pompe/start
curl -X POST http://localhost:8086/api/simulator/scenarios/stop
```

## Mode charge (NFR-01)

Le mode charge envoie un débit soutenu de mesures via la gateway et compte les
réponses HTTP réussies (la télémétrie ne répond qu'après confirmation Kafka).
Le simulateur doit tourner. Pendant le test, le simulateur normal est en pause.

```bash
# Démarrer 500 mesures/s pendant 300 s (valeurs par défaut)
curl -i -X POST "http://localhost:8086/api/simulator/load/start?rate=500&seconds=300"

# Lire l'état et les compteurs
curl -i http://localhost:8086/api/simulator/load

# Arrêter avant la fin
curl -i -X POST http://localhost:8086/api/simulator/load/stop
```

Le statut renvoie `attempted` (envois tentés), `acked` (acceptés par le service de télémétrie),
`failed` (en échec), `startedAt` et `endedAt`. Le nombre de mesures à comparer
avec la base est `acked`.

Remarques :
- Le test refuse de démarrer si un scénario de panne est actif (réponse 409) : appeler
  d'abord `POST /api/simulator/scenarios/stop`.
- Les mesures sont réparties sur les 7 capteurs de `sensors.json`.
- Après la fin du test, le simulateur normal reprend (7 mesures/s).

## Tests

```bash
./mvnw test
```

Les tests `SimulatorPublisherHttpTests` et `TelemetryIngestClientTests` utilisent
un faux serveur HTTP local : ni gateway, ni Docker, ni Kafka local ne sont
nécessaires pour les tests.