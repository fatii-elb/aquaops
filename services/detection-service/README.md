# detection-service (owner: E3) — skeleton coming in TECH-1

## SCRUM-33 - Detection d'une pompe en defaut (FR-11)

### Objectif
Detecter automatiquement une pompe en defaut depuis Kafka.

### Fonctionnement
1. SensorReadingListener consomme sensor-readings.
2. DetectionEngine applique les regles.
3. PumpFaultRule detecte PUMP_STATE = FAULT.
4. AnomalyPublisher publie PUMP_FAULT dans anomaly-detected.

Une mesure normale ne declenche aucune anomalie.

### Configuration
- Java : 21
- Spring Boot : 4.1.1
- Port : DETECTION_PORT (8083 par defaut)
- Kafka : KAFKA_BOOTSTRAP_SERVERS (localhost:9092)
- Topic entree : DETECTION_SENSOR_TOPIC (sensor-readings)
- Topic sortie : DETECTION_ANOMALY_TOPIC (anomaly-detected)
- Groupe : DETECTION_CONSUMER_GROUP (detection-service)

### Demarrage
Depuis services/detection-service :

    ./mvnw spring-boot:run

Kafka doit etre accessible pour traiter les messages.

### Tests
Executer :

    ./mvnw clean test

Tests realises :
- Regle pompe en defaut
- Moteur de detection
- Integration Kafka avec EmbeddedKafka
- Rejeu de 5 scenarios CSV

Jeu de donnees :
src/test/resources/pump-scenarios.csv

### Limite actuelle
La deduplication des anomalies persistantes sera
implementee dans SCRUM-36 (Sprint 3).
