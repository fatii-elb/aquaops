#!/usr/bin/env bash
# SCRUM-50 / NFR-03 : test de charge k6 sur l'API (p95 < 500 ms).
# Prerequis : plateforme d'integration lancee (Keycloak sur localhost:18180, passerelle sur localhost:18080).
# Choisit parmi les utilisateurs de test celui qui peut lire le plus de routes (K6_USER pour en forcer un).
# Usage : bash deploy/load/run-k6.sh      Resultats : docs/preuves/SCRUM-50/
set -euo pipefail
export MSYS_NO_PATHCONV=1
cd "$(git rev-parse --show-toplevel)"
KC=${KEYCLOAK_URL:-http://localhost:18180}
API=${API_URL:-http://localhost:18080}
PASS=${K6_PASSWORD:-test1234}
K6_IMAGE=grafana/k6@sha256:3d8e5c7f1d53ec59408c2ffbd9943602a0985ffcbd4a2430ec00f3ffca89796e
ROUTES="/api/incidents /api/assets/zones /api/assets/sensors /api/assets/pumps /api/assets/pipes /api/assets/tanks /api/assets/interventions /api/detection/anomalies /api/detection/thresholds /api/notifications/list"
token() { curl -s -d grant_type=password -d client_id=${K6_CLIENT:-aquaops-frontend} -d username="$1" -d password="$PASS" \
  "$KC/realms/aquaops/protocol/openid-connect/token" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'; }
TOKEN=""; BEST=0; USER_NAME=""
for u in ${K6_USER:-test-superviseur test-technicien test-operateur}; do
  t=$(token "$u" || true); [ -n "$t" ] || { echo "$u : pas de jeton"; continue; }
  c=0
  for p in $ROUTES; do
    code=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $t" "$API$p" || true)
    [ "$code" = 200 ] && c=$((c+1)) || true
  done
  echo "$u : $c routes en 200"
  if [ "$c" -gt "$BEST" ]; then BEST=$c; USER_NAME=$u; TOKEN=$t; fi
done
[ -n "$TOKEN" ] || { echo "ECHEC : aucun utilisateur utilisable"; exit 1; }
echo "utilisateur retenu : $USER_NAME ($BEST routes)"
mkdir -p docs/preuves/SCRUM-50
echo "$USER_NAME" > docs/preuves/SCRUM-50/.user
set +e
docker run --rm -e TOKEN="$TOKEN" -e BASE_URL=http://host.docker.internal:18080 \
  -v "$(cygpath -m "$PWD/deploy/load")":/scripts:ro \
  -v "$(cygpath -m "$PWD/docs/preuves/SCRUM-50")":/out \
  "$K6_IMAGE" run --quiet /scripts/k6-api.js 2>/dev/null | tee docs/preuves/SCRUM-50/k6-sortie.txt
rc=${PIPESTATUS[0]}
set -e
exit $rc
