#!/usr/bin/env bash
# Trivy en local sur les images AquaOps (SCRUM-63, sous-tache 1).
# Construit les images avec les memes Dockerfiles que le pipeline, puis les analyse.
#
# Depuis la racine du depot (Git Bash) :
#     bash deploy/trivy/scan-local.sh                 # incidents + frontend (par defaut)
#     bash deploy/trivy/scan-local.sh all             # les 8 images
#     bash deploy/trivy/scan-local.sh gateway assets  # au choix
# Images possibles : gateway telemetry assets detection incidents notifications simulator frontend
#
# Sorties : deploy/trivy/reports/<image>.txt (CRITICAL + HIGH) ; code retour 1 si un CRITICAL est trouve.
set -uo pipefail
export MSYS_NO_PATHCONV=1                       # Git Bash : ne pas deformer les chemins /var/run/...
cd "$(git rev-parse --show-toplevel)" || exit 1

TRIVY_IMAGE="${TRIVY_IMAGE:-aquasec/trivy:latest}"   # en local : derniere version ; le pipeline fixe la sienne
OUT="deploy/trivy/reports"; mkdir -p "$OUT"

declare -A SVC=( [gateway]=gateway [telemetry]=telemetry-service [assets]=assets-service
                 [detection]=detection-service [incidents]=incidents-service
                 [notifications]=notifications-service [simulator]=simulator )
ALL="gateway telemetry assets detection incidents notifications simulator frontend"
if   [ $# -eq 0 ];        then TARGETS="incidents frontend"
elif [ "$1" = "all" ];    then TARGETS="$ALL"
else TARGETS="$*"; fi

command -v docker >/dev/null || { echo "docker introuvable"; exit 1; }
FAIL=0; SUMMARY=""
for img in $TARGETS; do
  tag="aquaops/$img:scan"
  echo; echo "=== [$img] construction"
  if [ "$img" = "frontend" ]; then
    DOCKER_BUILDKIT=1 docker build -t "$tag" frontend || { echo "ECHEC build $img"; FAIL=1; SUMMARY+="$img : BUILD KO\n"; continue; }
  elif [ -n "${SVC[$img]:-}" ]; then
    DOCKER_BUILDKIT=1 docker build -f deploy/docker/spring-service.Dockerfile -t "$tag" "services/${SVC[$img]}" \
      || { echo "ECHEC build $img"; FAIL=1; SUMMARY+="$img : BUILD KO\n"; continue; }
  else echo "image inconnue : $img"; FAIL=1; continue; fi

  echo "=== [$img] analyse Trivy"
  trivy() { docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v aquaops-trivy-cache:/root/.cache/ \
              -v "$PWD/deploy/trivy/.trivyignore:/.trivyignore:ro" "$TRIVY_IMAGE" image --ignorefile /.trivyignore --ignore-unfixed "$@" "$tag"; }
  trivy --severity CRITICAL,HIGH --format table > "$OUT/$img.txt" 2>"$OUT/$img.err"
  trivy --severity CRITICAL --quiet --exit-code 1 --format table >/dev/null 2>>"$OUT/$img.err"
  rc=$?
  # Nombre de CRITICAL lu dans le rapport ("Total: 81 (HIGH: 77, CRITICAL: 4)")
  crit=$(grep -oE 'CRITICAL: [0-9]+' "$OUT/$img.txt" | awk '{s+=$2} END{print s+0}')
  if [ "$crit" -gt 0 ]; then SUMMARY+="$img : $crit CRITICAL (voir $OUT/$img.txt)\n"; FAIL=1
  elif [ $rc -ne 0 ]; then SUMMARY+="$img : ANALYSE EN ERREUR, pas un resultat (voir $OUT/$img.err)\n"; FAIL=1
  else SUMMARY+="$img : 0 CRITICAL\n"; fi
done
echo; echo "================ RESUME ================"; printf "$SUMMARY"
echo "Rapports : $OUT/"
exit $FAIL
