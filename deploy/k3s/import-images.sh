#!/usr/bin/env bash
# =============================================================================
# AquaOps: put the 7 service images of ONE commit into k3s, without any
# registry credential stored in the cluster (SCRUM-56 / NFR-09, E5).
#
#   deploy/k3s/import-images.sh <sha>
#
# Source (AQUAOPS_IMAGE_SOURCE):
#   ghcr   (default) pull ghcr.io/fatii-elb/aquaops/<service>:<full 40-char sha>,
#          the images the Images workflow scanned (Trivy, CRITICAL blocks) and
#          pushed. Private packages: stdin line 1 = token with read:packages
#          (in a job: the GITHUB_TOKEN, packages: read), line 2 = user name.
#          The token is never on a command line nor on disk (temporary
#          DOCKER_CONFIG, logged out and deleted on exit).
#   local  images already built on this host as localhost/aquaops/<service>:<sha>
#          (sandbox, or a manual build + Trivy as in deploy/terraform/README.md).
#
# Every image must carry org.opencontainers.image.revision = <sha> (the label
# set by the pipeline and by the README build). It is then tagged
# localhost/aquaops/<service>:<sha> and imported into k3s containerd, where
# the Deployments find it with imagePullPolicy: Never. The registry digest of
# each pulled image is printed and written to $AQUAOPS_DIGESTS_FILE if set.
#
# Settings: K3S_IMPORT_CMD (default "k3s ctr -n k8s.io images import -"; the
# sandbox uses "docker exec -i <k3s container> ctr -n k8s.io images import -").
# Exit codes: 0 imported · 1 failed · 2 refused (arguments).
# =============================================================================
set -euo pipefail

SERVICES="gateway telemetry assets detection incidents notifications simulator"
SOURCE="${AQUAOPS_IMAGE_SOURCE:-ghcr}"
REGISTRY=ghcr.io/fatii-elb/aquaops
IMPORT_CMD="${K3S_IMPORT_CMD:-k3s ctr -n k8s.io images import -}"

log() { printf '%s import-images: %s\n' "$(date -u +%H:%M:%S)" "$*" >&2; }
die() { log "REFUSED: $*"; exit 2; }

[ $# -eq 1 ] || die "usage: import-images.sh <sha>"
SHA="$1"
case "$SOURCE" in
  ghcr)  [[ "$SHA" =~ ^[0-9a-f]{40}$ ]] || die "ghcr images are tagged with the full 40-character SHA" ;;
  local) [[ "$SHA" =~ ^[0-9a-f]{7,40}$ ]] || die "not a commit SHA: $SHA" ;;
  *)     die "AQUAOPS_IMAGE_SOURCE must be ghcr or local" ;;
esac

if [ "$SOURCE" = ghcr ]; then
  IFS= read -r TOKEN || true
  IFS= read -r USERNAME || true
  [ -n "${TOKEN:-}" ] && [ -n "${USERNAME:-}" ] || die "stdin must give the token (line 1) and the user name (line 2)"
  DOCKER_CONFIG="$(mktemp -d)"; export DOCKER_CONFIG
  trap 'docker logout ghcr.io > /dev/null 2>&1 || true; rm -rf "$DOCKER_CONFIG"' EXIT
  printf '%s' "$TOKEN" | docker login ghcr.io --username "$USERNAME" --password-stdin > /dev/null
  unset TOKEN
fi

[ -z "${AQUAOPS_DIGESTS_FILE:-}" ] || : > "$AQUAOPS_DIGESTS_FILE"
LOCAL_REFS=()
for s in $SERVICES; do
  local_ref="localhost/aquaops/$s:$SHA"
  if [ "$SOURCE" = ghcr ]; then
    ref="$REGISTRY/$s:$SHA"
    docker pull --quiet "$ref" > /dev/null
    digest="$(docker image inspect --format '{{range .RepoDigests}}{{.}}{{"\n"}}{{end}}' "$ref" | sed -n "s#^$REGISTRY/$s@##p" | head -n 1)"
    [[ "$digest" =~ ^sha256:[0-9a-f]{64}$ ]] || { log "$s: no registry digest"; exit 1; }
    docker tag "$ref" "$local_ref"
  else
    ref="$local_ref"; digest="(local build)"
    docker image inspect "$ref" > /dev/null 2>&1 || { log "$s: $ref not found"; exit 1; }
  fi
  revision="$(docker image inspect --format '{{index .Config.Labels "org.opencontainers.image.revision"}}' "$ref")"
  case "$SHA" in
    "$revision"*) ;;   # full SHA, or the short SHA the label starts with
    *) log "$s: revision label [$revision] does not match $SHA"; exit 1 ;;
  esac
  echo "$s $digest"
  [ -z "${AQUAOPS_DIGESTS_FILE:-}" ] || echo "$s $digest" >> "$AQUAOPS_DIGESTS_FILE"
  LOCAL_REFS+=("$local_ref")
done

log "importing ${#LOCAL_REFS[@]} images into k3s"
# shellcheck disable=SC2086  # IMPORT_CMD is a command line on purpose
docker save "${LOCAL_REFS[@]}" | $IMPORT_CMD > /dev/null
log "done: localhost/aquaops/<service>:$SHA ready (imagePullPolicy Never)"
