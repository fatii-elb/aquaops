#!/bin/bash
# Ce script envoie un faux événement dans le topic Kafka 'incident-events'.
# Il permet de tester le service de notifications sans attendre un vrai incident.

KAFKA_CONTAINER="aquaops-kafka"
TOPIC="incident-events"

EVENT='{
  "eventVersion": "1",
  "eventType": "INCIDENT_CREATED",
  "incidentId": 123,
  "zoneId": 1,
  "status": "OPEN",
  "priority": "HIGH",
  "occurredAt": "2026-11-01T12:00:00Z"
}'

echo "Envoi de l'événement dans le topic $TOPIC..."
echo "$EVENT" | docker exec -i $KAFKA_CONTAINER kafka-console-producer.sh --bootstrap-server localhost:9092 --topic $TOPIC
echo "Événement envoyé avec succès."