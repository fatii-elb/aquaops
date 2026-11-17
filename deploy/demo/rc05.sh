#!/usr/bin/env bash
# RC-05 (NFR-05) : suppression d'un pod de télémétrie sous charge.
# Critères : service rétabli en 60 s au plus, aucune mesure perdue (envoyées = stockées).
# Prérequis : plateforme sur k3s (kubectl pointe sur le cluster), simulateur joignable, jq ou python3.
# Usage : bash deploy/demo/rc05.sh   (rapport écrit dans ./rc05-rapport.txt)
#
# Réglages par variables d'environnement (valeurs par défaut entre parenthèses) :
#   NAMESPACE (aquaops)  TELEMETRY_LABEL (app=telemetry)  SIMULATOR_URL (http://localhost:8086)
#   RATE (200 mesures/s : le consommateur traite environ 250/s, voir docs/rapport-test-charge-nfr01.md)
#   LOAD_SECONDS (180)  KILL_AFTER (40 s après le début de la charge)
#   PG_TARGET (deploy/postgres)  DB_USER (aquaops)  TELEMETRY_DB (aquaops_telemetry)
#   KAFKA_TARGET (deploy/kafka)  GROUP (telemetry-validation)  LIMITE (60)

NS="${NAMESPACE:-aquaops}"
LABEL="${TELEMETRY_LABEL:-app=telemetry}"
SIM="${SIMULATOR_URL:-http://localhost:8086}/api/simulator/load"
RATE="${RATE:-200}"
LOAD_SECONDS="${LOAD_SECONDS:-180}"
KILL_AFTER="${KILL_AFTER:-40}"
PG="${PG_TARGET:-deploy/postgres}"
PGUSER="${DB_USER:-aquaops}"
PGDB="${TELEMETRY_DB:-aquaops_telemetry}"
KAFKA="${KAFKA_TARGET:-deploy/kafka}"
GROUP="${GROUP:-telemetry-validation}"
LIMITE="${LIMITE:-60}"
RAPPORT="${RAPPORT:-./rc05-rapport.txt}"
ECHECS=0

: > "$RAPPORT"
etape() { echo "=== $1 ==="; echo "=== $1 ===" >> "$RAPPORT"; }
info() { echo "  $1"; echo "  $1" >> "$RAPPORT"; }
ok() { echo "  OK : $1"; echo "  OK : $1" >> "$RAPPORT"; }
ko() { echo "  KO : $1"; echo "  KO : $1" >> "$RAPPORT"; ECHECS=$((ECHECS+1)); }

# Lecture d'un champ JSON (jq si présent, sinon python).
json() {
  if command -v jq >/dev/null 2>&1; then jq -r ".$2 // empty" <<< "$1"
  else PY=$(command -v python3 || command -v python); "$PY" -c 'import json,sys; v=json.loads(sys.stdin.read()).get(sys.argv[1]); print("" if v is None else v)' "$2" <<< "$1"; fi
}

command -v kubectl >/dev/null 2>&1 || { echo "kubectl introuvable"; exit 2; }
command -v jq >/dev/null 2>&1 || command -v python3 >/dev/null 2>&1 || command -v python >/dev/null 2>&1 || { echo "jq ou python3 requis"; exit 2; }

# Nombre de pods de télémétrie prêts (hors pods en cours de suppression).
pods_prets() {
  kubectl -n "$NS" get pods -l "$LABEL" -o jsonpath='{range .items[*]}{.metadata.deletionTimestamp}|{.status.conditions[?(@.type=="Ready")].status}{"\n"}{end}' 2>/dev/null | grep -c '^|True$'
}

etape "1. Situation de départ"
INITIAL=$(pods_prets)
info "pods télémétrie prêts : $INITIAL"
if [ "$INITIAL" -ge 2 ]; then ok "au moins 2 pods prêts avant le test"; else ko "moins de 2 pods prêts : test impossible"; echo "Rapport : $RAPPORT"; exit 1; fi

etape "2. Démarrage de la charge ($RATE mesures/s pendant $LOAD_SECONDS s)"
code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$SIM/start?rate=$RATE&seconds=$LOAD_SECONDS")
case "$code" in 2*) ok "charge démarrée (HTTP $code)" ;; *) ko "démarrage de la charge refusé (HTTP $code)"; echo "Rapport : $RAPPORT"; exit 1 ;; esac
sleep "$KILL_AFTER"

etape "3. Suppression d'un pod de télémétrie"
# On supprime le pod qui LIT le topic : le topic sensor-readings n'a qu'une partition, donc un seul pod consomme et les
# autres attendent. Supprimer un pod qui attend ne prouverait rien. (Colonne HOST de la commande describe = IP du pod.)
HOTE=$(kubectl -n "$NS" exec "$KAFKA" -- /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group "$GROUP" 2>/dev/null | awk '$6 ~ /^[0-9]+$/ && $8 ~ /^\// {print substr($8,2); exit}')
VICTIME=""
if [ -n "$HOTE" ]; then
  VICTIME=$(kubectl -n "$NS" get pods -l "$LABEL" -o jsonpath='{range .items[*]}{.metadata.name}{" "}{.status.podIP}{"\n"}{end}' | awk -v ip="$HOTE" '$2==ip {print $1; exit}')
fi
if [ -n "$VICTIME" ]; then
  info "pod qui consomme le topic : $VICTIME (IP $HOTE)"
else
  VICTIME=$(kubectl -n "$NS" get pods -l "$LABEL" --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}')
  info "ATTENTION : pod consommateur non identifié, premier pod pris (le test peut être trop facile)"
fi
info "pod supprimé : $VICTIME"
T0=$(date +%s)
kubectl -n "$NS" delete pod "$VICTIME" --wait=false >/dev/null
MIN_PRETS=$INITIAL
DUREE=-1
while [ $(( $(date +%s) - T0 )) -lt 180 ]; do
  n=$(pods_prets)
  [ "$n" -lt "$MIN_PRETS" ] && MIN_PRETS=$n
  if [ "$n" -ge "$INITIAL" ] && ! kubectl -n "$NS" get pod "$VICTIME" >/dev/null 2>&1; then DUREE=$(( $(date +%s) - T0 )); break; fi
  sleep 1
done
info "pods prêts au plus bas : $MIN_PRETS sur $INITIAL"
if [ "$DUREE" -ge 0 ] && [ "$DUREE" -le "$LIMITE" ]; then ok "service rétabli en ${DUREE} s (limite ${LIMITE} s)"
elif [ "$DUREE" -ge 0 ]; then ko "service rétabli en ${DUREE} s (limite ${LIMITE} s dépassée)"
else ko "service non rétabli après 180 s"; fi
if [ "$MIN_PRETS" -ge 1 ]; then ok "jamais moins d'un pod prêt : le service n'a pas été coupé"; else ko "aucun pod prêt pendant un moment : coupure totale"; fi

etape "4. Fin de la charge et retour du retard (LAG) à 0"
FIN=$(( $(date +%s) + LOAD_SECONDS + 120 ))
STATUT=""
while [ "$(date +%s)" -lt "$FIN" ]; do
  STATUT=$(curl -s "$SIM")
  [ -n "$(json "$STATUT" endedAt)" ] && break
  sleep 5
done
[ -n "$(json "$STATUT" endedAt)" ] && ok "charge terminée" || ko "le simulateur n'a pas terminé la charge à temps"
LAG=-1
for i in $(seq 1 180); do
  LAG=$(kubectl -n "$NS" exec "$KAFKA" -- /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group "$GROUP" 2>/dev/null | awk '$6 ~ /^[0-9]+$/ {s+=$6; n++} END {if (n>0) print s; else print -1}')
  [ "$LAG" = "0" ] && break
  sleep 5
done
[ "$LAG" = "0" ] && ok "retard du groupe $GROUP revenu à 0" || ko "retard du groupe $GROUP : $LAG (0 attendu ; -1 = lecture impossible)"

etape "5. Mesures envoyées et stockées"
ENVOYEES=$(json "$STATUT" acked); ECHOUEES=$(json "$STATUT" failed)
DEBUT=$(json "$STATUT" startedAt); FINC=$(json "$STATUT" endedAt)
info "simulateur : acquittées=$ENVOYEES échouées=$ECHOUEES fenêtre $DEBUT -> $FINC"
STOCKEES=$(kubectl -n "$NS" exec "$PG" -- psql -U "$PGUSER" -d "$PGDB" -tA -c "SELECT count(*) FROM readings WHERE measured_at >= '$DEBUT' AND measured_at <= '$FINC'" 2>/dev/null | tr -d '[:space:]')
info "mesures stockées sur la fenêtre : ${STOCKEES:-illisible}"
if [ -z "$ENVOYEES" ] || [ -z "$STOCKEES" ]; then ko "comptage impossible (simulateur ou base injoignable)"
elif [ "$STOCKEES" -eq "$ENVOYEES" ]; then ok "envoyées = stockées ($ENVOYEES) : 0 perte"
elif [ "$STOCKEES" -gt "$ENVOYEES" ]; then ko "stockées > envoyées (+$((STOCKEES-ENVOYEES))) : doublons malgré la contrainte uk_readings_source (vérifier qu'elle existe : voir docs/reprise-kafka.md)"
else ko "PERTE de $((ENVOYEES-STOCKEES)) mesures"; fi

etape "Résultat"
if [ "$ECHECS" -eq 0 ]; then echo "RC-05 : RÉUSSI"; echo "RC-05 : RÉUSSI" >> "$RAPPORT"; else echo "RC-05 : ÉCART ($ECHECS point(s) KO)"; echo "RC-05 : ÉCART ($ECHECS point(s) KO)" >> "$RAPPORT"; fi
echo "Rapport : $RAPPORT (à archiver dans docs/preuves/RC-05/ avec une capture de 'kubectl get pods -w')"
exit "$ECHECS"
