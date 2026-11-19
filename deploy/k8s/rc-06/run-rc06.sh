#!/usr/bin/env bash
# RC-06 (NFR-07) : mise a jour progressive d'un service sous trafic HTTP continu ; 0 erreur 5xx attendue.
#
# Deroulement : 1) controle de la strategie du Deployment ; 2) trafic continu lance DANS le cluster (un pod curl
# interroge le Service : un port-forward serait coupe a la disparition du pod qu'il vise) ; 3) mise a jour
# (nouvelle image, sinon redemarrage progressif) ; 4) attente de la fin ; 5) comptage des codes HTTP.
# Echec si un 5xx ou une connexion refusee est observe, si la mise a jour n'aboutit pas, ou si aucune requete
# n'a ete envoyee. Les resultats sont ecrits dans OUT_DIR (a joindre au dossier de tests, LIV-07).
#
# Variables (valeurs par defaut) :
#   NAMESPACE=aquaops DEPLOYMENT=detection-service CONTAINER=$DEPLOYMENT NEW_IMAGE=(vide : rollout restart)
#   TARGET_URL=http://$DEPLOYMENT.$NAMESPACE.svc.cluster.local:8083/actuator/health/readiness
#   CURL_HEADER=(vide ; ex. "Authorization: Bearer ..." pour une API protegee ; jamais affiche)
#   CURL_IMAGE=curlimages/curl:8.11.1 INTERVAL=0.05 WARMUP=10 COOLDOWN=15 ROLLOUT_TIMEOUT=300 KUBECTL=kubectl
set -euo pipefail

NAMESPACE="${NAMESPACE:-aquaops}"
DEPLOYMENT="${DEPLOYMENT:-detection-service}"
CONTAINER="${CONTAINER:-$DEPLOYMENT}"
NEW_IMAGE="${NEW_IMAGE:-}"
SERVICE_PORT="${SERVICE_PORT:-8083}"
TARGET_URL="${TARGET_URL:-http://${DEPLOYMENT}.${NAMESPACE}.svc.cluster.local:${SERVICE_PORT}/actuator/health/readiness}"
CURL_HEADER="${CURL_HEADER:-}"
CURL_IMAGE="${CURL_IMAGE:-curlimages/curl:8.11.1}"
INTERVAL="${INTERVAL:-0.05}"
WARMUP="${WARMUP:-10}"
COOLDOWN="${COOLDOWN:-15}"
ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-300}"
KUBECTL="${KUBECTL:-kubectl}"
OUT_DIR="${OUT_DIR:-rc06-report-$(date +%Y%m%d-%H%M%S)}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
POD="rc06-traffic"

k() { "$KUBECTL" -n "$NAMESPACE" "$@"; }
say() { printf '%s\n' "$*"; }

mkdir -p "$OUT_DIR"
cleanup() { k delete pod "$POD" --ignore-not-found --wait=false >/dev/null 2>&1 || true; }
trap cleanup EXIT

# 1) Strategie : sans maxUnavailable a 0, le test n'a pas de sens
max_unavailable="$(k get deployment "$DEPLOYMENT" -o jsonpath='{.spec.strategy.rollingUpdate.maxUnavailable}')"
case "$max_unavailable" in 0|0%) ;; *) say "ECHEC : maxUnavailable vaut '${max_unavailable}' (0 attendu) pour ${DEPLOYMENT}."; exit 1;; esac
ready="$(k get deployment "$DEPLOYMENT" -o jsonpath='{.status.readyReplicas}')"
if [ "${ready:-0}" -lt 2 ]; then say "ECHEC : ${DEPLOYMENT} a ${ready:-0} replica(s) pret(s) ; 2 au moins sont requis."; exit 1; fi
say "Strategie verifiee : maxUnavailable=${max_unavailable}, ${ready} replicas prets."
k get pods -l "app.kubernetes.io/name=${DEPLOYMENT}" -o wide > "$OUT_DIR/pods-before.txt"

# 2) Trafic continu dans le cluster
cleanup
max_seconds=$(( WARMUP + ROLLOUT_TIMEOUT + COOLDOWN + 60 ))
loop='end=$(( $(date +%s) + '"$max_seconds"' ));
while [ ! -f /tmp/stop ] && [ "$(date +%s)" -lt "$end" ]; do
  code=$(curl -s -o /dev/null -m 2 ${HDR:+-H "$HDR"} -w "%{http_code}" "$URL") || code=000
  echo "$(date +%s) $code"
  sleep '"$INTERVAL"'
done'
k run "$POD" --restart=Never --image="$CURL_IMAGE" --env="URL=${TARGET_URL}" --env="HDR=${CURL_HEADER}" \
  --command -- sh -c "$loop" >/dev/null
k wait --for=condition=Ready "pod/${POD}" --timeout=90s >/dev/null
say "Trafic en cours sur ${TARGET_URL} (echauffement ${WARMUP} s)."
sleep "$WARMUP"

# 3) Mise a jour progressive
started="$(date +%s)"
if [ -n "$NEW_IMAGE" ]; then
  say "Mise a jour : ${CONTAINER} -> ${NEW_IMAGE}"
  k set image "deployment/${DEPLOYMENT}" "${CONTAINER}=${NEW_IMAGE}" >/dev/null
else
  say "Mise a jour : redemarrage progressif de ${DEPLOYMENT}"
  k rollout restart "deployment/${DEPLOYMENT}" >/dev/null
fi
rollout_ok=1
if ! k rollout status "deployment/${DEPLOYMENT}" --timeout="${ROLLOUT_TIMEOUT}s" | tee "$OUT_DIR/rollout.txt"; then
  rollout_ok=0
fi
duration=$(( $(date +%s) - started ))
sleep "$COOLDOWN"

# 4) Arret du trafic et recuperation du journal
k exec "$POD" -- touch /tmp/stop >/dev/null 2>&1 || true
k wait --for=jsonpath='{.status.phase}'=Succeeded "pod/${POD}" --timeout=60s >/dev/null 2>&1 || true
k logs "$POD" > "$OUT_DIR/traffic.log"
k get pods -l "app.kubernetes.io/name=${DEPLOYMENT}" -o wide > "$OUT_DIR/pods-after.txt"

# 5) Verdict
set +e
summary="$("$HERE/tally.sh" "$OUT_DIR/traffic.log")"
tally_status=$?
set -e
{
  say "RC-06 : ${DEPLOYMENT} sous trafic continu"
  say "Duree de la mise a jour : ${duration} s ; rollout reussi : $([ "$rollout_ok" = 1 ] && echo oui || echo NON)"
  say "$summary"
} | tee "$OUT_DIR/summary.txt"

if [ "$rollout_ok" = 1 ] && [ "$tally_status" = 0 ]; then
  say "RC-06 REUSSI : aucune erreur 5xx ni coupure pendant la mise a jour."
  exit 0
fi
[ "$tally_status" = 2 ] && say "RC-06 ECHEC : aucune requete enregistree, le test ne prouve rien."
[ "$tally_status" = 1 ] && say "RC-06 ECHEC : erreurs 5xx ou coupures de connexion observees (voir ${OUT_DIR}/traffic.log)."
[ "$rollout_ok" = 0 ] && say "RC-06 ECHEC : la mise a jour n'a pas abouti dans ${ROLLOUT_TIMEOUT} s."
exit 1
