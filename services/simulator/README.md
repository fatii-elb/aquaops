# simulator

Simulateur de capteurs (FR-03, SCRUM-25). Il lit une liste de capteurs, génère une mesure par capteur chaque seconde et la publie dans le topic Kafka `sensor-readings` (contrat `contracts/events/sensor-readings.v1.json`).

Les valeurs viennent du scénario actif (voir `SCENARIOS.md`) : normal par défaut, ou une panne (fuite, pompe, silence, pic).

## Prérequis

- Java 21
- Un Kafka accessible sur `localhost:9092`, avec le topic `sensor-readings`

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
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Adresse de Kafka |
| `SIMULATOR_PORT` | `8086` | Port HTTP du simulateur |
| `SIM_INTERVAL_MS` | `1000` | Intervalle entre deux envois, en millisecondes |
| `SENSORS_FILE` | `classpath:sensors.json` | Fichier des capteurs. Pour un fichier externe : `file:C:/chemin/sensors.json` |

## Liste des capteurs

Le fichier `src/main/resources/sensors.json` contient les capteurs : `sensorId` et `zoneId` (entiers) et `type` (`PRESSURE`, `FLOW`, `LEVEL` ou `PUMP_STATE`). Pour ajouter un capteur, ajouter une ligne dans ce fichier.

## Scénarios de panne

Voir `SCENARIOS.md`. Exemple :

```bash
curl -X POST http://localhost:8086/api/simulator/scenarios/pompe/start
curl -X POST http://localhost:8086/api/simulator/scenarios/stop
```

## Tests

```bash
./mvnw test
```

Le test `SimulatorPublisherKafkaTests` démarre un Kafka de test en mémoire (EmbeddedKafka) : ni Docker ni Kafka local ne sont nécessaires pour les tests.
