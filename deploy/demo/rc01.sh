#!/usr/bin/env bash
# RC-01 : rejoue le scénario « fuite » jusqu'à la clôture et affiche OK ou KO à chaque étape.
# Prérequis : docker compose up -d (dossier deploy/), puis telemetry-service, detection-service,
# incidents-service, notifications-service, assets-service et simulator lancés (./mvnw spring-boot:run).
# Usage : bash deploy/demo/rc01.sh   (rapport écrit dans ./rc01-rapport.txt)

SIM="${SIMULATOR_URL:-http://localhost:8086}/api/simulator/scenarios"
INC="${INCIDENTS_URL:-http://localhost:8084}/api/incidents"
NOTIF="${NOTIFICATIONS_URL:-http://localhost:8085}/api/notifications/list"
ASSETS="${ASSETS_URL:-http://localhost:8082}/api/assets/interventions"
KC="${KEYCLOAK_URL:-http://localhost:8180}/realms/aquaops/protocol/openid-connect/token"
KC_USER="${KC_USER:-demo}"
KC_PASSWORD="${KC_PASSWORD:-demo}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-aquaops-kafka}"
RAPPORT="${RAPPORT:-./rc01-rapport.txt}"
ACTEUR="rc01-script"
TECHNICIEN="technicien-demo"
ECHECS=0

: > "$RAPPORT"
etape() { echo "=== $1 ==="; echo "=== $1 ===" >> "$RAPPORT"; }
ok() { echo "  OK : $1"; echo "  OK : $1" >> "$RAPPORT"; }
ko() { echo "  KO : $1"; echo "  KO : $1" >> "$RAPPORT"; ECHECS=$((ECHECS+1)); }

# Lit tous les messages d'un topic (s'arrête après 5 s sans nouveau message).
lire_topic() {
  MSYS_NO_PATHCONV=1 docker exec "$KAFKA_CONTAINER" /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 --topic "$1" --from-beginning --timeout-ms 5000 2>/dev/null
}

# Nombre de notifications (-1 si le service ne répond pas).
compter_notifications() {
  r=$(curl -s "$NOTIF")
  case "$r" in
    "") echo -1 ;;
    "[]") echo 0 ;;
    *) echo $(( $(echo "$r" | grep -o '","' | wc -l) + 1 )) ;;
  esac
}

# Change l'état d'un incident. $1 = id, $2 = état, $3 = responsable (facultatif).
changer_etat() {
  if [ -n "$3" ]; then resp="\"$3\""; else resp="null"; fi
  code=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$INC/$1" \
    -H "Content-Type: application/json" \
    -d "{\"status\":\"$2\",\"assignee\":$resp,\"actor\":\"$ACTEUR\"}")
  if [ "$code" = "200" ]; then ok "incident : état $2"; else ko "incident : état $2 refusé (HTTP $code)"; fi
}

# Change le statut d'une intervention. $1 = id, $2 = statut.
changer_statut_intervention() {
  code=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$ASSETS/$1/status" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
    -d "{\"status\":\"$2\"}")
  if [ "$code" = "200" ]; then ok "intervention : statut $2"; else ko "intervention : statut $2 refusé (HTTP $code)"; fi
}

DEBUT=$(date -u +%Y-%m-%dT%H:%M:%S)
AVANT=$(compter_notifications)

etape "Étape 1 : lancement du scénario « fuite »"
code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$SIM/fuite/start")
if [ "$code" = "200" ]; then ok "scénario fuite démarré"; else ko "démarrage refusé (HTTP $code)"; fi

etape "Étape 2 : anomalie LEAK_SUSPECTED dans anomaly-detected"
trouve=""
for i in 1 2 3 4 5 6; do
  trouve=$(lire_topic anomaly-detected | grep '"type":"LEAK_SUSPECTED"' \
    | grep -o '"detectedAt":"[^"]*"' | cut -d'"' -f4 | awk -v d="$DEBUT" '$0 > d' | head -1)
  [ -n "$trouve" ] && break
  sleep 5
done
if [ -n "$trouve" ]; then ok "anomalie détectée à $trouve"; else ko "aucune anomalie LEAK_SUSPECTED après le démarrage"; fi

etape "Étape 3 : incident critique créé"
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
  [ "$NB" -gt 1 ] && echo "  ATTENTION : $NB incidents critiques créés (un par zone)"
else
  ko "aucun incident critique créé après le démarrage"
fi

etape "Étape 4 : notification reçue"
APRES=$AVANT
for i in 1 2 3 4 5 6; do
  APRES=$(compter_notifications)
  [ "$APRES" -gt "$AVANT" ] && break
  sleep 5
done
if [ "$AVANT" -lt 0 ]; then ko "notifications-service ne répond pas"
elif [ "$APRES" -gt "$AVANT" ]; then ok "notification reçue ($AVANT -> $APRES)"
else ko "aucune nouvelle notification"; fi

if [ -n "$ID" ]; then
  etape "Étape 5 : confirmer l'incident"
  changer_etat "$ID" CONFIRMED

  etape "Étape 6 : affecter l'incident"
  changer_etat "$ID" ASSIGNED "$TECHNICIEN"

  etape "Étape 7 : créer et affecter l'intervention"
  TOKEN=$(curl -s -X POST "$KC" -d "client_id=admin-cli" -d "grant_type=password" \
    -d "username=$KC_USER" -d "password=$KC_PASSWORD" \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')
  INT_ID=""
  if [ -z "$TOKEN" ]; then
    ko "pas de jeton Keycloak (utilisateur $KC_USER)"
  else
    resp=$(curl -s -w "\n%{http_code}" -X POST "$ASSETS" \
      -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
      -d "{\"incidentId\":$ID}")
    code=$(echo "$resp" | tail -1)
    INT_ID=$(echo "$resp" | head -1 | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
    if [ "$code" = "201" ] && [ -n "$INT_ID" ]; then
      ok "intervention créée (id $INT_ID)"
      code=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$ASSETS/$INT_ID/assign" \
        -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
        -d "{\"team\":\"Equipe demo\",\"assignedTo\":\"$TECHNICIEN\"}")
      if [ "$code" = "200" ]; then ok "intervention affectée"; else ko "affectation refusée (HTTP $code)"; INT_ID=""; fi
    else
      ko "création de l'intervention refusée (HTTP $code ; 403 = jeton sans rôle)"
      INT_ID=""
    fi
  fi

  etape "Étape 8 : en cours"
  changer_etat "$ID" IN_PROGRESS "$TECHNICIEN"
  [ -n "$INT_ID" ] && changer_statut_intervention "$INT_ID" IN_PROGRESS

  etape "Étape 9 : résolu et clos"
  [ -n "$INT_ID" ] && changer_statut_intervention "$INT_ID" DONE
  changer_etat "$ID" RESOLVED "$TECHNICIEN"
  changer_etat "$ID" CLOSED "$TECHNICIEN"

  etape "Étape 10 : état final et historique"
  if curl -s "$INC/$ID" | grep -q '"status":"CLOSED"'; then ok "incident $ID clos"; else ko "l'incident $ID n'est pas clos"; fi
  h=$(curl -s "$INC/$ID/history")
  case "$h" in ""|"[]") ko "historique vide" ;; *) ok "historique présent" ;; esac
  if [ -n "$INT_ID" ]; then
    if curl -s -H "Authorization: Bearer $TOKEN" "$ASSETS/$INT_ID" | grep -q '"status":"DONE"'; then
      ok "intervention $INT_ID terminée"
    else
      ko "l'intervention $INT_ID n'est pas terminée"
    fi
  fi
fi

curl -s -o /dev/null -X POST "$SIM/stop"

etape "Résultat"
if [ "$ECHECS" -eq 0 ]; then echo "RC-01 : toutes les étapes OK" | tee -a "$RAPPORT"
else echo "RC-01 : $ECHECS étape(s) KO" | tee -a "$RAPPORT"; exit 1; fi