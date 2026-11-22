#!/usr/bin/env bash
# RC-10 (NFR-11) : une trace unique passerelle -> telemetrie -> detection -> incidents sur un scenario de fuite.
# Prerequis : pile d'integration lancee avec l'overlay deploy/tracing (voir son en-tete).
# Usage : bash deploy/demo/rc10.sh      Rapport JSON : docs/preuves/RC-10/
set -uo pipefail
export MSYS_NO_PATHCONV=1
cd "$(git rev-parse --show-toplevel)"
F="-f deploy/integration/docker-compose.integration.yml -f deploy/tracing/docker-compose.tracing.yml"
[ -f deploy/integration/docker-compose.override.local.yml ] && F="-f deploy/integration/docker-compose.integration.yml -f deploy/integration/docker-compose.override.local.yml -f deploy/tracing/docker-compose.tracing.yml"
echo "=== 1. Scenario de fuite ==="
code=$(docker compose $F exec -T simulator curl -s -o /dev/null -w '%{http_code}' -X POST http://localhost:8086/api/simulator/scenarios/fuite/start)
echo "simulateur : HTTP $code"
echo "=== 2. Attente d'un incident (3 min max) ==="
for i in $(seq 1 12); do
  N=$(docker compose $F exec -T incidents curl -s http://localhost:8084/api/incidents | grep -o '"id"' | wc -l)
  echo "essai $i : incidents = $N"; [ "$N" -gt 0 ] && break; sleep 15
done
sleep 10
docker compose $F exec -T simulator curl -s -o /dev/null -X POST http://localhost:8086/api/simulator/scenarios/stop
echo "=== 3. Meilleure trace dans Jaeger ==="
J=$(curl -s "http://localhost:16686/api/traces?service=incidents-service&lookback=1h&limit=100")
best=0; bestid=""; bestsvc=""
while IFS= read -r line; do
  c=0; svc=""
  for n in gateway telemetry-service detection-service incidents-service; do
    if echo "$line" | grep -q "\"serviceName\":\"$n\""; then c=$((c+1)); svc="$svc $n"; fi
  done
  if [ "$c" -gt "$best" ]; then best=$c; bestsvc="$svc"; bestid=$(echo "$line" | grep -o '"traceID":"[0-9a-f]*"' | head -1 | cut -d'"' -f4); fi
done < <(echo "$J" | sed 's/{"traceID"/\n&/g' | tail -n +2)
echo "Meilleure trace : $bestid -> $best/4 services :$bestsvc"
if [ "$best" = 4 ]; then
  mkdir -p docs/preuves/RC-10
  curl -s "http://localhost:16686/api/traces/$bestid" > "docs/preuves/RC-10/trace-$bestid.json"
  echo ">>> RC-10 OK : http://localhost:16686/trace/$bestid"
else
  echo ">>> RC-10 PARTIEL"; exit 1
fi
