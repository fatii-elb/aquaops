#!/usr/bin/env bash
# RC-01 : rejoue le scénario « fuite » et affiche OK ou KO à chaque étape.
# Prérequis : docker compose up -d (dossier deploy/), puis telemetry-service,
# detection-service et simulator lancés avec ./mvnw spring-boot:run.
# Usage : bash deploy/demo/rc01.sh

SIM="${SIMULATOR_URL:-http://localhost:8086}/api/simulator/scenarios"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-aquaops-kafka}"
ECHECS=0

ok() { echo "  OK : $1"; }
ko() { echo "  KO : $1"; ECHECS=$((ECHECS+1)); }

# Lit tous les messages d'un topic (s'arrête après 5 s sans nouveau message).
lire_topic() {
  MSYS_NO_PATHCONV=1 docker exec "$KAFKA_CONTAINER" /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 --topic "$1" --from-beginning --timeout-ms 5000 2>/dev/null
}

DEBUT=$(date -u +%Y-%m-%dT%H:%M:%S)

echo "=== Étape 1 : lancement du scénario « fuite » ==="
code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$SIM/fuite/start")
if [ "$code" = "200" ]; then ok "scénario fuite démarré"; else ko "démarrage refusé (HTTP $code)"; fi

echo "=== Étape 2 : anomalie LEAK_SUSPECTED dans anomaly-detected ==="
trouve=""
for i in 1 2 3 4 5 6; do
  trouve=$(lire_topic anomaly-detected | grep '"type":"LEAK_SUSPECTED"' \
    | grep -o '"detectedAt":"[^"]*"' | cut -d'"' -f4 | awk -v d="$DEBUT" '$0 > d' | head -1)
  [ -n "$trouve" ] && break
  sleep 5
done
if [ -n "$trouve" ]; then ok "anomalie détectée à $trouve"; else ko "aucune anomalie LEAK_SUSPECTED après le démarrage"; fi

# Les étapes suivantes (incident, notification, affectation, intervention,
# résolution, clôture) seront ajoutées quand les API seront fusionnées.

curl -s -o /dev/null -X POST "$SIM/stop"

echo "=== Résultat ==="
if [ "$ECHECS" -eq 0 ]; then echo "RC-01 : toutes les étapes OK"; else echo "RC-01 : $ECHECS étape(s) KO"; exit 1; fi