#!/usr/bin/env bash
# =============================================================================
# AquaOps: deploy one exact commit to the TEST environment from pre-built images.
# Card SCRUM-55 / NFR-08 · E5
#
# Run on the test VM (later by the deploy job), from anywhere:
#     IMAGE_TAG=<40-char commit SHA> \
#     AQUAOPS_ENV_FILE=/etc/aquaops/test.env \
#     GHCR_USERNAME=<github user> GHCR_TOKEN=<short-lived token> \
#         bash deploy/scripts/deploy-test.sh
#
# Required:
#     IMAGE_TAG          full commit SHA (40 lowercase hex) built and tested on main
#     AQUAOPS_ENV_FILE   the environment's .env (passwords...), outside the repo,
#                        readable by its owner only (mode 600 or 400). It is
#                        the only source of the Compose variables: if one of
#                        them is also exported in the environment, the script
#                        refuses (it would silently win over the file).
# Optional:
#     IMAGE_NAMESPACE        default ghcr.io/fatii-elb/aquaops
#     AQUAOPS_PROJECT        default aquaops-test; must be aquaops-test or
#                            aquaops-test-<suffix>: other projects are refused
#     AQUAOPS_WITH_INFRA     true: also use compose.infra.yml (PostgreSQL, Kafka,
#                            Keycloak in Docker); default false. The env file
#                            must then also set KEYCLOAK_ADMIN,
#                            KEYCLOAK_ADMIN_PASSWORD and KEYCLOAK_AUDIT_CLIENT_SECRET
#                            (not empty, not "change-me")
#     AQUAOPS_HEALTH_TIMEOUT seconds for wait-healthy.sh, default 180
#     AQUAOPS_STATE_DIR      where deployed SHAs are recorded, default
#                            ${XDG_STATE_HOME:-~/.local/state}/aquaops-deploy/<project>
#     GHCR_USERNAME, GHCR_TOKEN  registry login, used once then forgotten. No
#                            token: no login (the pull fails if one is needed).
#     PUSHED_AT              epoch seconds of the push, to report end-to-end time
#
# Steps: checks (nothing touched) -> note the deployed SHA -> temporary login
# -> pull exact images -> logout -> up -d (no build, no pull) -> wait-healthy.sh
# (health + exact image) -> gateway smoke test -> record the new SHA.
#
# Never: builds, "down", "-v", "rm", "prune", stops other projects, prints the
# env file or the resolved Compose config (they hold passwords), keeps the
# registry login (temporary DOCKER_CONFIG, deleted on exit, even on failure).
# No rollback yet: the previous SHA is recorded for a future rollback step.
#
# Exit codes: 0 deployed and healthy · 1 deployment failed · 2 refused before
# touching any container (bad input, configuration or environment).
# Depends on deploy/scripts/wait-healthy.sh (same folder).
# =============================================================================

set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
WAIT_HEALTHY="$SCRIPT_DIR/wait-healthy.sh"
COMPOSE_APPS="$REPO_ROOT/deploy/test/compose.yml"
COMPOSE_INFRA="$REPO_ROOT/deploy/test/compose.infra.yml"
JAVA_SERVICES=(gateway telemetry assets detection incidents notifications simulator)

IMAGE_TAG="${IMAGE_TAG:-}"
IMAGE_NAMESPACE="${IMAGE_NAMESPACE:-ghcr.io/fatii-elb/aquaops}"
PROJECT="${AQUAOPS_PROJECT:-aquaops-test}"
ENV_FILE="${AQUAOPS_ENV_FILE:-}"
WITH_INFRA="${AQUAOPS_WITH_INFRA:-false}"
HEALTH_TIMEOUT="${AQUAOPS_HEALTH_TIMEOUT:-180}"
PUSHED_AT="${PUSHED_AT:-}"

# The token is taken out of the environment at once: no child process
# (compose, wait-healthy.sh, curl) ever inherits it.
REGISTRY_USER="${GHCR_USERNAME:-}"
REGISTRY_TOKEN="${GHCR_TOKEN:-}"
unset GHCR_TOKEN GHCR_USERNAME

# Compose settings that would silently change which files, project, profiles
# or orphans are used: this script always passes them explicitly.
unset COMPOSE_FILE COMPOSE_PROJECT_NAME COMPOSE_PROFILES COMPOSE_ENV_FILES \
      COMPOSE_REMOVE_ORPHANS

# --- State used by the exit handler --------------------------------------------
START_EPOCH="$(date +%s)"
CURRENT_STEP="input checks"
STEP_START=$SECONDS
TIMINGS=()
TOUCHED=0            # 1 once containers may have changed
TMP_DOCKER_CONFIG=""
ORIG_DOCKER_CONFIG="${DOCKER_CONFIG:-}"
ORIG_DOCKER_CONFIG_SET="${DOCKER_CONFIG+1}"
REGISTRY=""
LOGGED_IN=0
PREVIOUS_SHA="none"
STATE_DIR=""

log()  { printf '%s deploy-test: %s\n' "$(date -u +%H:%M:%S)" "$*"; }
refuse() { log "REFUSED: $*" >&2; exit 2; }

step() {
    if [ -n "${CURRENT_STEP:-}" ] && [ "$CURRENT_STEP" != "input checks" ]; then
        TIMINGS+=("$CURRENT_STEP=$((SECONDS - STEP_START))s")
    fi
    CURRENT_STEP="$1"; STEP_START=$SECONDS
    log "== $1"
}

# Every docker compose call of this script: always this project, these files,
# this env file. IMAGE_TAG / IMAGE_NAMESPACE come from this shell and take
# precedence over the env file (which must not define them anyway).
compose() {
    local files=(-f "$COMPOSE_APPS")
    [ "$WITH_INFRA" != true ] || files+=(-f "$COMPOSE_INFRA")
    IMAGE_TAG="$IMAGE_TAG" IMAGE_NAMESPACE="$IMAGE_NAMESPACE" \
        docker compose -p "$PROJECT" "${files[@]}" --env-file "$ENV_FILE" "$@"
}

cleanup_credentials() {
    if [ "$LOGGED_IN" = 1 ] && [ -n "$REGISTRY" ]; then
        docker logout "$REGISTRY" > /dev/null 2>&1 || true
        LOGGED_IN=0
    fi
    if [ -n "$TMP_DOCKER_CONFIG" ] && [ -d "$TMP_DOCKER_CONFIG" ]; then
        rm -rf -- "$TMP_DOCKER_CONFIG"
    fi
    # Back to the user's normal Docker config (plugins, context) for the rest.
    if [ "$ORIG_DOCKER_CONFIG_SET" = 1 ]; then
        export DOCKER_CONFIG="$ORIG_DOCKER_CONFIG"
    else
        unset DOCKER_CONFIG
    fi
}

record() {   # record <result> : one line per attempt, never any secret
    [ -n "$STATE_DIR" ] || return 0
    printf '%s project=%s sha=%s previous=%s result=%s duration=%ss\n' \
        "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$PROJECT" "${IMAGE_TAG:-?}" \
        "$PREVIOUS_SHA" "$1" "$(( $(date +%s) - START_EPOCH ))" \
        >> "$STATE_DIR/history.log" 2> /dev/null || true
}

summary() {   # summary <result>
    local total=$(( $(date +%s) - START_EPOCH )) since_push="" t
    if [[ "$PUSHED_AT" =~ ^[0-9]+$ ]]; then
        since_push="$(( $(date +%s) - PUSHED_AT ))"
    fi
    log "result: $1"
    log "commit: ${IMAGE_TAG:-?}  previous: $PREVIOUS_SHA  project: $PROJECT"
    for t in "${TIMINGS[@]}"; do log "  step ${t%%=*}: ${t#*=}"; done
    log "deploy duration: ${total}s${since_push:+  (push to now: ${since_push}s, budget 1200s)}"
    if [ -n "$since_push" ] && [ "$since_push" -gt 1200 ]; then
        log "WARNING: over the 20-minute NFR-08 budget"
    fi
    if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
        {
            echo "### Test deployment: $1"
            echo "| Item | Value |"
            echo "| --- | --- |"
            echo "| Commit | \`${IMAGE_TAG:-?}\` |"
            echo "| Previous | \`$PREVIOUS_SHA\` |"
            echo "| Project | \`$PROJECT\` |"
            for t in "${TIMINGS[@]}"; do echo "| ${t%%=*} | ${t#*=} |"; done
            echo "| Deploy duration | ${total}s |"
            [ -z "$since_push" ] || echo "| Push to deployed | ${since_push}s (budget 1200s) |"
        } >> "$GITHUB_STEP_SUMMARY" 2> /dev/null || true
    fi
}

on_exit() {
    local rc=$?
    trap - EXIT
    set +e
    TIMINGS+=("$CURRENT_STEP=$((SECONDS - STEP_START))s")
    cleanup_credentials
    if [ "$rc" -eq 0 ]; then
        summary "SUCCESS"
        exit 0
    fi
    log "FAILED during step: $CURRENT_STEP (exit $rc)" >&2
    if [ "$TOUCHED" = 1 ]; then
        # Container list of THIS project only: names, images, states. No env.
        log "containers of project $PROJECT:" >&2
        compose ps -a --format 'table {{.Service}}\t{{.Image}}\t{{.State}}\t{{.Status}}' >&2 2>&1 \
            || log "  (could not list containers)" >&2
    else
        log "no container was changed" >&2
    fi
    record "FAILED:$CURRENT_STEP"
    summary "FAILED ($CURRENT_STEP)"
    # Exit 2 promises "no container was touched": never once "up" has started,
    # whatever code compose or wait-healthy.sh returned.
    [ "$rc" -eq 2 ] && [ "$TOUCHED" = 0 ] && exit 2
    exit 1
}
trap on_exit EXIT
trap 'log "interrupted"; exit 1' INT TERM

# =============================================================================
# 1. Input checks: nothing is touched yet (exit 2 on any problem)
# =============================================================================
[[ "$IMAGE_TAG" =~ ^[0-9a-f]{40}$ ]] \
    || refuse "IMAGE_TAG must be a full 40-character lowercase commit SHA, got [${IMAGE_TAG}]"
[[ "$IMAGE_NAMESPACE" =~ ^[a-z0-9]([a-z0-9._-]*[a-z0-9])?(:[0-9]+)?(/[a-z0-9]([a-z0-9._-]*[a-z0-9])?)*$ ]] \
    || refuse "invalid IMAGE_NAMESPACE [$IMAGE_NAMESPACE]"
[[ "$PROJECT" =~ ^aquaops-test(-[a-z0-9]+)?$ ]] \
    || refuse "project [$PROJECT] is not a test project (aquaops-test or aquaops-test-<suffix>)"
[[ "$HEALTH_TIMEOUT" =~ ^[1-9][0-9]*$ ]] || refuse "AQUAOPS_HEALTH_TIMEOUT must be a positive number"
case "$WITH_INFRA" in true|false) ;; *) refuse "AQUAOPS_WITH_INFRA must be true or false" ;; esac

[ -n "$ENV_FILE" ] || refuse "AQUAOPS_ENV_FILE is required (the environment's .env file)"
[ -f "$ENV_FILE" ] && [ -r "$ENV_FILE" ] || refuse "env file not found or not readable: $ENV_FILE"
ENV_FILE="$(cd "$(dirname "$ENV_FILE")" && pwd)/$(basename "$ENV_FILE")"
case "$ENV_FILE" in
    "$REPO_ROOT"/*) refuse "the env file must live outside the repository checkout" ;;
esac
env_mode="$(stat -c '%a' "$ENV_FILE")"
[[ "$env_mode" =~ ^[4-7]00$ ]] || refuse "env file must be readable by its owner only (chmod 600), mode is $env_mode"

for f in "$COMPOSE_APPS" "$WAIT_HEALTHY"; do [ -f "$f" ] || refuse "missing $f"; done
[ "$WITH_INFRA" != true ] || [ -f "$COMPOSE_INFRA" ] || refuse "missing $COMPOSE_INFRA"

# The env file must be the ONLY source of the Compose variables: an exported
# variable of the same name (runner, profile...) would silently win over it.
# IMAGE_TAG / IMAGE_NAMESPACE are the exception: this script sets them.
compose_files=("$COMPOSE_APPS")
[ "$WITH_INFRA" != true ] || compose_files+=("$COMPOSE_INFRA")
exported="$(compgen -e)"
shadowed=()
while read -r var; do
    case "$var" in IMAGE_TAG|IMAGE_NAMESPACE|"") continue ;; esac
    if grep -qxF "$var" <<< "$exported"; then shadowed+=("$var"); fi
done < <(grep -ohE '\$\{[A-Za-z_][A-Za-z0-9_]*' "${compose_files[@]}" | cut -c3- | sort -u)
[ "${#shadowed[@]}" -eq 0 ] \
    || refuse "exported variables would override the env file, unset them: ${shadowed[*]}"
unset exported

# Required keys present and not placeholders. Only key NAMES are ever printed.
env_value() {   # last assignment of KEY in the env file, quotes removed
    sed -n -E "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p" "$ENV_FILE" | tail -n 1 \
        | sed -E 's/^"(.*)"$/\1/; s/^'\''(.*)'\''$/\1/; s/[[:space:]]+$//'
}
required=(DB_HOST DB_USER DB_PASSWORD KAFKA_BOOTSTRAP_SERVERS KEYCLOAK_ISSUER_URI)
# With compose.infra.yml: Keycloak admin, and the secret of the "aquaops-audit"
# client (login audit, read by the realm import and by assets-service).
[ "$WITH_INFRA" != true ] \
    || required+=(KEYCLOAK_ADMIN KEYCLOAK_ADMIN_PASSWORD KEYCLOAK_AUDIT_CLIENT_SECRET)
bad=()
for k in "${required[@]}"; do
    v="$(env_value "$k")"
    if [ -z "$v" ] || [ "$v" = change-me ]; then bad+=("$k"); fi
done
[ "${#bad[@]}" -eq 0 ] || refuse "env file: missing, empty or placeholder value for: ${bad[*]}"
for k in IMAGE_TAG IMAGE_NAMESPACE; do
    if grep -qE "^[[:space:]]*${k}[[:space:]]*=" "$ENV_FILE"; then
        refuse "env file must not set $k: the deployment provides it"
    fi
done
unset v

# Registry: first path element of the namespace, only if it is a host name.
first="${IMAGE_NAMESPACE%%/*}"
if [ "$first" != "$IMAGE_NAMESPACE" ] && [[ "$first" == *.* || "$first" == *:* || "$first" == localhost ]]; then
    REGISTRY="$first"
fi
if [ -n "$REGISTRY_TOKEN" ]; then
    [ -n "$REGISTRY" ] || refuse "GHCR_TOKEN given but IMAGE_NAMESPACE has no registry host"
    [ -n "$REGISTRY_USER" ] || refuse "GHCR_TOKEN given without GHCR_USERNAME"
fi

log "commit $IMAGE_TAG, project $PROJECT, namespace $IMAGE_NAMESPACE, infra in Docker: $WITH_INFRA"

# =============================================================================
# 2. Environment checks: Docker, Compose v2, curl (still nothing touched)
# =============================================================================
step "preflight"
command -v docker > /dev/null || refuse "docker not found"
command -v curl > /dev/null || refuse "curl not found (needed for the smoke test)"
docker version --format '{{.Server.Version}}' > /dev/null 2>&1 || refuse "cannot reach the Docker daemon"
compose_version="$(docker compose version --short 2> /dev/null)" || refuse "Docker Compose v2 plugin not found"
[[ "$compose_version" =~ ^v?([2-9]|[1-9][0-9]+)\. ]] || refuse "Docker Compose v2 or later required, found [$compose_version]"
log "docker compose $compose_version"

# One deployment at a time on this machine, and a place to record SHAs.
STATE_DIR="${AQUAOPS_STATE_DIR:-${XDG_STATE_HOME:-$HOME/.local/state}/aquaops-deploy/$PROJECT}"
mkdir -p "$STATE_DIR" || refuse "cannot create state directory $STATE_DIR"
if command -v flock > /dev/null; then
    exec 9> "$STATE_DIR/lock"
    flock -n 9 || refuse "another deployment of $PROJECT is running"
else
    log "flock not available: no local lock (the workflow serialises deployments)"
fi

# =============================================================================
# 3. Configuration checks: resolved by Compose itself, never printed
# =============================================================================
step "validate configuration"
compose config --quiet || refuse "docker compose rejected the configuration (see message above)"
# Every application image must be EXACTLY namespace/service:IMAGE_TAG.
images="$(compose config --images)" || refuse "cannot list configured images"
for svc in "${JAVA_SERVICES[@]}"; do
    grep -qxF "$IMAGE_NAMESPACE/$svc:$IMAGE_TAG" <<< "$images" \
        || refuse "configured image for $svc is not $IMAGE_NAMESPACE/$svc:$IMAGE_TAG"
done
while read -r img; do
    case "$img" in
        "$IMAGE_NAMESPACE"/*) [[ "$img" == *":$IMAGE_TAG" ]] || refuse "unexpected image $img" ;;
    esac
done <<< "$images"
# No build on the VM: refuse any build: section. The resolved config holds
# passwords: kept in memory only, never printed, dropped right after.
# (Not piped into grep -q: an early exit would kill compose with SIGPIPE and,
# under pipefail, hide the match.)
resolved="$(compose config 2> /dev/null)" || refuse "cannot resolve the configuration"
if grep -qE '^[[:space:]]+build:' <<< "$resolved"; then
    refuse "the configuration contains a build: section"
fi
resolved=""
log "configuration valid, 7 application images pinned to $IMAGE_TAG"

# =============================================================================
# 4. What is deployed now (kept for a future rollback)
# =============================================================================
step "record previous deployment"
current_image="$(docker ps -a --no-trunc \
    --filter "label=com.docker.compose.project=$PROJECT" \
    --filter "label=com.docker.compose.service=gateway" \
    --format '{{.Image}}' | head -n 1)" || current_image=""
case "$current_image" in
    "$IMAGE_NAMESPACE/gateway:"*) PREVIOUS_SHA="${current_image##*:}" ;;
    "") PREVIOUS_SHA="none" ;;
    *) PREVIOUS_SHA="unknown" ;;
esac
printf '%s\n' "$PREVIOUS_SHA" > "$STATE_DIR/previous-sha"
log "currently deployed: $PREVIOUS_SHA"

# =============================================================================
# 5. Temporary registry login and exact pull
# =============================================================================
step "pull images"
# Private, throw-away Docker client config: the login never reaches
# ~/.docker/config.json. Keep the current daemon and the Compose plugin.
if [ -z "${DOCKER_HOST:-}" ]; then
    DOCKER_HOST="$(docker context inspect --format '{{.Endpoints.docker.Host}}' 2> /dev/null || true)"
    [ -z "$DOCKER_HOST" ] || export DOCKER_HOST
fi
plugins_dir="${DOCKER_CONFIG:-$HOME/.docker}/cli-plugins"
TMP_DOCKER_CONFIG="$(mktemp -d "${TMPDIR:-/tmp}/aquaops-docker.XXXXXX")"
chmod 700 "$TMP_DOCKER_CONFIG"
[ ! -d "$plugins_dir" ] || ln -s "$plugins_dir" "$TMP_DOCKER_CONFIG/cli-plugins"
export DOCKER_CONFIG="$TMP_DOCKER_CONFIG"
docker compose version --short > /dev/null 2>&1 || refuse "Compose plugin lost with the temporary Docker config"

if [ -n "$REGISTRY_TOKEN" ]; then
    # Set BEFORE the login: an interruption during it still triggers a logout
    # (needed if a credential helper keeps the token outside DOCKER_CONFIG).
    LOGGED_IN=1
    printf '%s' "$REGISTRY_TOKEN" \
        | docker login "$REGISTRY" --username "$REGISTRY_USER" --password-stdin > /dev/null
    log "logged in to $REGISTRY (temporary)"
else
    log "no registry token given: pulling without login"
fi
REGISTRY_TOKEN=""
compose pull --quiet
cleanup_credentials        # not needed any more: logout + delete now
log "images pulled: ${JAVA_SERVICES[*]} at $IMAGE_TAG"

# =============================================================================
# 6. Start / update the test stack (only changed services are recreated)
# =============================================================================
step "start containers"
TOUCHED=1
compose up -d --no-build --pull never

# =============================================================================
# 7. Health and exact image of the 7 Java services
# =============================================================================
step "wait for health"
IMAGE_TAG="$IMAGE_TAG" IMAGE_NAMESPACE="$IMAGE_NAMESPACE" \
    bash "$WAIT_HEALTHY" -p "$PROJECT" -t "$HEALTH_TIMEOUT"

# =============================================================================
# 8. Smoke test through the published gateway port
# =============================================================================
step "gateway smoke test"
gateway_addr="$(compose port gateway 8080 | head -n 1)"
[ -n "$gateway_addr" ] || { log "gateway port 8080 is not published"; exit 1; }
case "$gateway_addr" in 0.0.0.0:*|'[::]':*) gateway_addr="127.0.0.1:${gateway_addr##*:}" ;; esac
body=""
for attempt in 1 2 3; do
    # --noproxy: a local address must never go through an http_proxy of the VM.
    if body="$(curl -fsS --noproxy '*' --max-time 5 "http://$gateway_addr/actuator/health")" \
        && [[ "$body" == *'"UP"'* ]]; then
        break
    fi
    body=""
    log "gateway not answering UP yet (attempt $attempt/3)"
    [ "$attempt" -eq 3 ] || sleep 2
done
[ -n "$body" ] || { log "gateway health check failed at http://$gateway_addr/actuator/health"; exit 1; }
log "gateway http://$gateway_addr/actuator/health: UP"

# =============================================================================
# 9. Record the new deployment
# =============================================================================
step "record deployment"
printf '%s\n' "$IMAGE_TAG" > "$STATE_DIR/deployed-sha"
record "SUCCESS"
