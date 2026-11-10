#!/usr/bin/env bash
# RC-01 : rejoue le #!/usr/bin/env bash
# RC-01 : rejoue le scénario « fuite » et affiche OK ou KO à chaque étape.
# Prérequis : docker compose up -d (dossier deploy/), puis telemetry-service,
# detection-service, incidents-service et simulator lancés avec ./mvnw spring-boot:run.
# Usage : bash deploy/demo/rc01.sh

SIM="${SIMULATOR_URL:-http://localhost:8086}/api/simulator/scenarios"
INC="${INCIDENTS_URL:-http://localhost:8084}/api/incidents"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-aquaops-kafka}"
ACTEUR="rc01-script"
TECHNICIEN="technicien-demo"
ECHECS=0

ok() { echo "  OK : $1"; }
ko() { echo "  KO : $1"; ECHECS=$((ECHECS+1)); }

# Lit tous les messages d'un topic (s'arrête après 5 s sans nouveau message).
lire_topic() {
  MSYS_NO_PATHCONV=1 docker exec "$KAFKA_CONTAINER" /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 --topic "$1" --from-beginning --timeout-ms 5000 2>/dev/null
}

# Change l'état d'un incident. $1 = id, $2 = état, $3 = responsable (facultatif).
changer_etat() {
  if [ -n "$3" ]; then resp="\"$3\""; else resp="null"; fi
  code=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$INC/$1" \
    -H "Content-Type: application/json" \
    -d "{\"status\":\"$2\",\"assignee\":$resp,\"actor\":\"$ACTEUR\"}")
  if [ "$code" = "200" ]; then ok "état $2"; else ko "état $2 refusé (HTTP $code)"; fi
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

echo "=== Étape 3 : incident critique créé ==="
LISTE=""
for i in 1 2 3 4 5 6; do
  LISTE=$(curl -s "$INC" | sed 's/},{/}\n{/g' | grep '"type":"LEAK_SUSPECTED"' \
    | grep '"priority":"CRITICAL"' \
    | awk -v d="$DEBUT" '{ if (match($0, /"createdAt":"[^"]*"/)) { c=substr($0, RSTART+13, RLENGTH-14); if (c > d) print $0 } }')
  [ -n "$LISTE" ] && break
  sleep 5
done
NB=$(echo "$LISTE" | grep -c .)
ID=$(echo "$LISTE" | head -1 | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
if [ -n "$ID" ]; then
  ok "incident critique créé (id $ID)"
  [ "$NB" -gt 1 ] && echo "  ATTENTION : $NB incidents critiques créés (un par zone) ; le plan en attend un seul"
else
  ko "aucun incident critique créé après le démarrage"
fi

# Les étapes suivantes ne s'exécutent que si l'incident existe.
if [ -n "$ID" ]; then
  echo "=== Étape 4 : confirmer l'incident ==="
  changer_etat "$ID" CONFIRMED

  echo "=== Étape 5 : affecter l'incident ==="
  changer_etat "$ID" ASSIGNED "$TECHNICIEN"

  echo "=== Étape 6 : en cours, résolu, clos ==="
  changer_etat "$ID" IN_PROGRESS "$TECHNICIEN"
  changer_etat "$ID" RESOLVED "$TECHNICIEN"
  changer_etat "$ID" CLOSED "$TECHNICIEN"

  echo "=== Étape 7 : état final et historique ==="
  if curl -s "$INC/$ID" | grep -q '"status":"CLOSED"'; then ok "incident $ID clos"; else ko "l'incident $ID n'est pas clos"; fi
  h=$(curl -s "$INC/$ID/history")
  case "$h" in ""|"[]") ko "historique vide" ;; *) ok "historique présent" ;; esac
fi

# À ajouter quand les API seront prêtes : notification reçue (E6) et
# intervention (E2, jeton nécessaire).

curl -s -o /dev/null -X POST "$SIM/stop"

echo "=== Résultat ==="
if [ "$ECHECS" -eq 0 ]; then echo "RC-01 : toutes les étapes OK"; else echo "RC-01 : $ECHECS étape(s) KO"; exit 1; fi