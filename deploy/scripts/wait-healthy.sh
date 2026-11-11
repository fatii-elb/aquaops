#!/usr/bin/env bash
# =============================================================================
# AquaOps: wait until the services of a deployed Compose project are healthy.
# Card SCRUM-55 / NFR-08 · E5
#
# Run after "docker compose ... up -d", for example by the deploy job:
#     bash deploy/scripts/wait-healthy.sh                    # aquaops-test, 7 Java services
#     bash deploy/scripts/wait-healthy.sh -t 300             # longer timeout
#     bash deploy/scripts/wait-healthy.sh postgres kafka     # other services
#
# Options (or environment variables):
#     -p PROJECT    Compose project name       AQUAOPS_PROJECT  aquaops-test
#     -t SECONDS    global timeout             HEALTH_TIMEOUT   180
#     -i SECONDS    time between two checks    HEALTH_INTERVAL  5
#     -l LINES      log lines shown on failure HEALTH_LOG_LINES 40
#     SERVICE...    Compose service names      default: the 7 Java services
#
# Image version check, same variables as deploy/test/compose.yml:
#     IMAGE_TAG        the commit SHA that must be running. Unset or empty:
#                      no image check (health only, for local tests).
#     IMAGE_NAMESPACE  registry namespace, default ghcr.io/fatii-elb/aquaops
#                      (keep in line with the default in compose.yml).
# When IMAGE_TAG is set, each of the 7 Java services must run EXACTLY
# ${IMAGE_NAMESPACE}/<service>:${IMAGE_TAG} (full reference, not a substring).
# Other services given on the command line (postgres, kafka...) are not ours:
# only their health is checked.
#
# A service is ready when its container is running AND its Docker HEALTHCHECK
# says "healthy" (for the Java images: /actuator/health answers 200).
# Stops at once, with diagnostics, if a container is missing, exited, dead,
# paused, unhealthy, has no healthcheck or runs the wrong image. Otherwise
# waits up to the timeout.
#
# Read-only: only "docker version", "docker ps", "docker inspect" and
# "docker logs", the last three limited to the project's Compose label.
# Never starts, stops, restarts or removes
# anything, never looks at other projects, never prints environment variables.
# Log lines are filtered to hide values that look like passwords or tokens.
#
# Exit codes: 0 all healthy · 1 failure or timeout · 2 bad usage or no Docker.
# =============================================================================

set -u -o pipefail

PROJECT="${AQUAOPS_PROJECT:-aquaops-test}"
TIMEOUT="${HEALTH_TIMEOUT:-180}"
INTERVAL="${HEALTH_INTERVAL:-5}"
LOG_LINES="${HEALTH_LOG_LINES:-40}"
DEFAULT_SERVICES=(gateway telemetry assets detection incidents notifications simulator)
# Our Java services: Compose service name = image name in the namespace.
JAVA_SERVICES=" ${DEFAULT_SERVICES[*]} "
IMAGE_TAG="${IMAGE_TAG:-}"
IMAGE_NAMESPACE="${IMAGE_NAMESPACE:-ghcr.io/fatii-elb/aquaops}"

usage() {
    sed -n '/^# Run after/,/^# Exit codes/p' "$0" | sed 's/^# \{0,1\}//' >&2
    exit 2
}

die() { echo "wait-healthy: $*" >&2; exit 2; }

while getopts ":p:t:i:l:h" opt; do
    case "$opt" in
        p) PROJECT="$OPTARG" ;;
        t) TIMEOUT="$OPTARG" ;;
        i) INTERVAL="$OPTARG" ;;
        l) LOG_LINES="$OPTARG" ;;
        h) usage ;;
        :) die "option -$OPTARG needs a value" ;;
        *) die "unknown option -$OPTARG (use -h)" ;;
    esac
done
shift $((OPTIND - 1))
if [ "$#" -gt 0 ]; then SERVICES=("$@"); else SERVICES=("${DEFAULT_SERVICES[@]}"); fi

# --- Input checks: values end up in docker filters, keep them simple ---------
for n in TIMEOUT INTERVAL LOG_LINES; do
    case "${!n}" in
        ''|*[!0-9]*) die "$n must be a whole number of seconds/lines, got [${!n}]" ;;
    esac
done
[ "$TIMEOUT" -ge 1 ] || die "TIMEOUT must be at least 1"
[ "$INTERVAL" -ge 1 ] || die "INTERVAL must be at least 1"
[[ "$PROJECT" =~ ^[a-z0-9][a-z0-9_-]*$ ]] || die "invalid project name [$PROJECT]"
for s in "${SERVICES[@]}"; do
    [[ "$s" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]] || die "invalid service name [$s]"
done
if [ -n "$IMAGE_TAG" ]; then
    # Docker tag rules: letters, digits, _ . -, at most 128, not starting with . or -
    [[ "$IMAGE_TAG" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]] \
        || die "invalid IMAGE_TAG [$IMAGE_TAG]"
    # Lowercase registry/path, optional :port, no tag or digest of its own.
    [[ "$IMAGE_NAMESPACE" =~ ^[a-z0-9]([a-z0-9._-]*[a-z0-9])?(:[0-9]+)?(/[a-z0-9]([a-z0-9._-]*[a-z0-9])?)*$ ]] \
        || die "invalid IMAGE_NAMESPACE [$IMAGE_NAMESPACE]"
fi

# Expected image reference of a service, empty if its image is not checked.
expected_image() {
    if [ -n "$IMAGE_TAG" ] && [[ "$JAVA_SERVICES" == *" $1 "* ]]; then
        echo "$IMAGE_NAMESPACE/$1:$IMAGE_TAG"
    fi
}

command -v docker > /dev/null || die "docker command not found"
docker version --format '{{.Server.Version}}' > /dev/null 2>&1 \
    || die "cannot reach the Docker daemon"

# --- Helpers ------------------------------------------------------------------

# Containers of one service in THIS project only (one-off "compose run"
# containers excluded). Prints IDs, one per line. Fails if Docker fails.
service_containers() {
    docker ps -aq --no-trunc \
        --filter "label=com.docker.compose.project=$PROJECT" \
        --filter "label=com.docker.compose.service=$1" \
        --filter "label=com.docker.compose.oneoff=False"
}

# One line per container of THIS project:
# "service image status health exit restarts". The image is the reference the
# container was created from (Config.Image), exactly as Compose resolved it.
# Two docker calls per check, whatever the number of services. Fails only if
# Docker itself fails ("docker inspect" may race with a removed container:
# that container is then simply absent from the output).
project_snapshot() {
    local ids
    ids="$(docker ps -aq --no-trunc \
        --filter "label=com.docker.compose.project=$PROJECT" \
        --filter "label=com.docker.compose.oneoff=False")" || return 1
    [ -n "$ids" ] || return 0
    # shellcheck disable=SC2086  # one argument per container ID
    docker inspect --format \
        '{{index .Config.Labels "com.docker.compose.service"}} {{.Config.Image}} {{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}} {{.State.ExitCode}} {{.RestartCount}}' \
        $ids 2> /dev/null || true
}

# Hide values after password/secret/token-like keys and bearer tokens.
redact() {
    sed -E \
        -e 's/((pass(word|wd)?|secret|token|credential|api[_-]?key)[A-Za-z_]*["'\'']?[[:space:]]*[:=][[:space:]]*)[^[:space:],;&"'\'']+/\1***/Ig' \
        -e 's/(bearer[[:space:]]+)[A-Za-z0-9._~+\/=-]+/\1***/Ig'
}

# Diagnostics for one service: state, last health checks, last log lines.
diagnose() {
    local svc="$1" ids id want
    echo "----- $svc -----"
    if ! ids="$(service_containers "$svc")"; then
        echo "  cannot list containers (Docker error)"
        return
    fi
    if [ -z "$ids" ]; then
        echo "  no container for service \"$svc\" in project \"$PROJECT\""
        echo "  (was it started with the right -p / project name and compose files?)"
        return
    fi
    want="$(expected_image "$svc")"
    [ -z "$want" ] || echo "  expected image $want"
    for id in $ids; do
        docker inspect --format \
            '  container {{.Name}}  image {{.Config.Image}}
  status {{.State.Status}}  health {{if .State.Health}}{{.State.Health.Status}}{{else}}none (no HEALTHCHECK){{end}}  exit code {{.State.ExitCode}}  restarts {{.RestartCount}}
  started {{.State.StartedAt}}{{if .State.Error}}  error: {{.State.Error}}{{end}}{{if .State.OOMKilled}}  OOM-KILLED (memory limit){{end}}' \
            "$id" 2>&1 | redact
        echo "  last health checks (exit code, output):"
        docker inspect --format \
            '{{if .State.Health}}{{range .State.Health.Log}}    {{.ExitCode}} {{printf "%.300q" .Output}}{{"\n"}}{{end}}{{end}}' \
            "$id" 2>&1 | tail -n 3 | redact
        echo "  last $LOG_LINES log lines:"
        docker logs --tail "$LOG_LINES" "$id" 2>&1 | redact | sed 's/^/    | /'
    done
}

# --- Main loop ------------------------------------------------------------------

echo "wait-healthy: project=$PROJECT timeout=${TIMEOUT}s interval=${INTERVAL}s"
echo "wait-healthy: services: ${SERVICES[*]}"
if [ -n "$IMAGE_TAG" ]; then
    echo "wait-healthy: image check: $IMAGE_NAMESPACE/<service>:$IMAGE_TAG (Java services)"
else
    echo "wait-healthy: image check: off (IMAGE_TAG not set), health only"
fi

start=$SECONDS
deadline=$((start + TIMEOUT))
last_summary=""

while :; do
    ok=(); waiting=(); failed=()
    snapshot="$(project_snapshot)" || die "docker ps failed (Docker daemon unreachable?)"

    for svc in "${SERVICES[@]}"; do
        verdict="missing"; detail="missing"
        want_image="$(expected_image "$svc")"
        while read -r name image status health code restarts; do
            [ "$name" = "$svc" ] || continue
            [ "$verdict" != missing ] || verdict="ok"
            case "$status/$health" in
                running/healthy)        ;;
                running/starting)       [ "$verdict" = fail ] || { verdict="wait"; detail="starting"; } ;;
                restarting/*|created/*) [ "$verdict" = fail ] || { verdict="wait"; detail="$status(restarts=$restarts)"; } ;;
                running/unhealthy)      verdict="fail"; detail="unhealthy" ;;
                running/none)           verdict="fail"; detail="no-healthcheck" ;;
                exited/*|dead/*)        verdict="fail"; detail="$status(exit=$code)" ;;
                *)                      verdict="fail"; detail="$status/$health" ;;
            esac
            # Wrong version: fails even if healthy, waiting will not fix it.
            # Exact string comparison of the full reference.
            if [ -n "$want_image" ] && [ "$image" != "$want_image" ]; then
                verdict="fail"; detail="wrong-image"
            fi
        done <<< "$snapshot"
        case "$verdict" in
            ok)           ok+=("$svc") ;;
            wait)         waiting+=("$svc:$detail") ;;
            fail|missing) failed+=("$svc:$detail") ;;
        esac
    done

    elapsed=$((SECONDS - start))
    summary="healthy ${#ok[@]}/${#SERVICES[@]}"
    [ "${#waiting[@]}" -eq 0 ] || summary+="  waiting: ${waiting[*]}"
    [ "${#failed[@]}" -eq 0 ] || summary+="  FAILED: ${failed[*]}"
    # One line per change (plus one every ~30 s), not one per check.
    if [ "$summary" != "$last_summary" ] || [ $((elapsed % 30)) -lt "$INTERVAL" ]; then
        echo "[${elapsed}s] $summary"
        last_summary="$summary"
    fi

    if [ "${#failed[@]}" -eq 0 ] && [ "${#waiting[@]}" -eq 0 ]; then
        echo "wait-healthy: OK, all ${#SERVICES[@]} services healthy after ${elapsed}s"
        exit 0
    fi

    if [ "${#failed[@]}" -gt 0 ]; then
        echo "wait-healthy: FAILED after ${elapsed}s: ${failed[*]}" >&2
        for f in "${failed[@]}"; do diagnose "${f%%:*}"; done >&2
        exit 1
    fi

    if [ "$SECONDS" -ge "$deadline" ]; then
        echo "wait-healthy: TIMEOUT after ${TIMEOUT}s, not ready: ${waiting[*]}" >&2
        for w in "${waiting[@]}"; do diagnose "${w%%:*}"; done >&2
        exit 1
    fi

    remaining=$((deadline - SECONDS))
    sleep $(( INTERVAL < remaining ? INTERVAL : remaining ))
done
