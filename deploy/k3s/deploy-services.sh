#!/usr/bin/env bash
# =============================================================================
# AquaOps: deploy the 7 services of ONE commit to a k3s environment, verify,
# roll back on failure, record push-to-healthy (SCRUM-56 / NFR-09, E5).
#
#   deploy/k3s/deploy-services.sh <env> <sha> [<pushed_at epoch seconds>]
#
# Runs Terraform ONLY through deploy/terraform/tf.sh (same checks, state and
# workspace rules). Needs what tf.sh needs: KUBE_CONFIG_PATH, and the TF_VAR_*
# secrets of the environment already exported (never read here).
#
# Steps:
#   1. refuse unless <env> is deployable (AQUAOPS_K3S_DEPLOY_ENVS, default
#      "rc11") and its envs/<env>.tfvars says services_enabled = true;
#   2. images localhost/aquaops/<service>:<sha> must be in k3s (imported after
#      their Trivy scan by deploy/k3s/import-images.sh); checked when the k3s
#      containerd socket is reachable;
#   3. remember the SHA currently deployed (tf.sh output services_image_tag);
#   4. apply with -var services_image_tag=<sha>, -parallelism=1: one rolling
#      update at a time, so the namespace quota needs room for one extra pod
#      only, and JVM start-ups do not compete for CPU;
#   5. verify: every Deployment rolled out, every pod Ready AND running the
#      expected image (no old pod left), gateway UP, each /api route reaches
#      its service (expected HTTP codes below);
#   6. failure: apply the remembered SHA again, verify it, exit 1 (3 if the
#      rollback itself fails or there was nothing to roll back to);
#   7. success: push-to-healthy = healthy time - <pushed_at>, printed, added to
#      $GITHUB_STEP_SUMMARY when present, and appended as one JSON line to
#      $AQUAOPS_K3S_HISTORY.
#
# Exit codes: 0 deployed and healthy · 1 failed, previous version restored and
# healthy · 2 refused, nothing changed · 3 failed, NOT restored (act now).
#
# Settings (environment, all optional):
#   AQUAOPS_K3S_DEPLOY_ENVS  deployable environments (default "rc11")
#   AQUAOPS_ROLLOUT_TIMEOUT  per-service wait, Terraform and kubectl (default 10m, as
#                            services_rollout_timeout: a hand-run plan then shows no change)
#   AQUAOPS_K3S_HISTORY      JSON lines history (default <state dir>/k3s-deployments.jsonl)
#   AQUAOPS_FORCE_FAIL_VERIFY=1  test hook: make the first verification fail
# =============================================================================
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPO="$(cd "$HERE/../.." && pwd -P)"
TF="$REPO/deploy/terraform/tf.sh"
KUBECTL="${KUBECTL:-kubectl}"

SERVICES="gateway telemetry assets detection incidents notifications simulator"
DEPLOY_ENVS="${AQUAOPS_K3S_DEPLOY_ENVS:-rc11}"
TIMEOUT="${AQUAOPS_ROLLOUT_TIMEOUT:-10m}"
STATE_ROOT="${AQUAOPS_TF_STATE_DIR:-$HOME/.local/state/aquaops-terraform}"
HISTORY="${AQUAOPS_K3S_HISTORY:-$STATE_ROOT/k3s-deployments.jsonl}"

# Gateway routes and the codes that prove the request reached the service
# (a route missing in the gateway answers 404; a service down answers 5xx).
SMOKE_ROUTES="
/actuator/health 200
/api/incidents 200
/api/notifications/list 200
/api/simulator/scenarios 200
/api/telemetry/readings 400
/api/assets/zones 401
/api/detection/anomalies 401
"

log() { printf '%s deploy-services: %s\n' "$(date -u +%H:%M:%S)" "$*" >&2; }
die() { log "REFUSED: $*"; exit 2; }

# --- 1. arguments and environment ------------------------------------------
[ $# -ge 2 ] && [ $# -le 3 ] || die "usage: deploy-services.sh <env> <sha> [<pushed_at epoch>]"
ENV_NAME="$1"; SHA="$2"; PUSHED_AT="${3:-}"
[[ "$ENV_NAME" =~ ^[a-z][a-z0-9-]{1,20}$ ]] || die "invalid environment name"
[[ "$SHA" =~ ^[0-9a-f]{7,40}$ ]] || die "not a commit SHA: $SHA"
[ -z "$PUSHED_AT" ] || [[ "$PUSHED_AT" =~ ^[0-9]{9,11}$ ]] || die "pushed_at must be epoch seconds"
[[ "$TIMEOUT" =~ ^[0-9]+[sm]$ ]] || die "AQUAOPS_ROLLOUT_TIMEOUT must look like 300s or 10m"
case " $DEPLOY_ENVS " in *" $ENV_NAME "*) ;; *) die "\"$ENV_NAME\" is not deployable here ($DEPLOY_ENVS)";; esac

TFVARS="$REPO/deploy/terraform/environment/envs/$ENV_NAME.tfvars"
[ -f "$TFVARS" ] || die "no envs/$ENV_NAME.tfvars"
grep -Eq '^[[:space:]]*services_enabled[[:space:]]*=[[:space:]]*true' "$TFVARS" \
  || die "services_enabled is not true in envs/$ENV_NAME.tfvars"
NS="$(sed -n 's/^[[:space:]]*namespace[[:space:]]*=[[:space:]]*"\([a-z0-9-]*\)".*/\1/p' "$TFVARS")"
[ -n "$NS" ] || die "no namespace in envs/$ENV_NAME.tfvars"
[ -n "${KUBE_CONFIG_PATH:-}" ] || die "KUBE_CONFIG_PATH is not set"
k() { "$KUBECTL" --kubeconfig "$KUBE_CONFIG_PATH" -n "$NS" "$@"; }

START="$(date +%s)"
[ -n "$PUSHED_AT" ] || PUSHED_AT="$START"

# --- 2. images present in k3s -----------------------------------------------
SOCK=/run/k3s/containerd/containerd.sock
if [ -S "$SOCK" ] && command -v k3s > /dev/null; then
  for s in $SERVICES; do
    k3s crictl inspecti "localhost/aquaops/$s:$SHA" > /dev/null 2>&1 \
      || die "image localhost/aquaops/$s:$SHA is not in k3s: run deploy/k3s/import-images.sh first"
  done
  log "the 7 images of $SHA are in k3s"
else
  log "k3s containerd not reachable from here: image presence not pre-checked"
fi

# --- 3. what runs now --------------------------------------------------------
# Roll back to the last version VERIFIED healthy in this environment (history:
# "deployed" -> its sha, "rolled_back" -> its previous); without history, to
# what Terraform last applied. Never to a release that failed.
PREV=""
if [ -f "$HISTORY" ]; then
  PREV="$(grep "\"environment\":\"$ENV_NAME\"" "$HISTORY" | grep -E '"status":"(deployed|rolled_back)"' | tail -n 1 \
    | sed -n -e 's/.*"sha":"\([0-9a-f]*\)".*"status":"deployed".*/\1/p' \
             -e 's/.*"previous":"\([0-9a-f]*\)".*"status":"rolled_back".*/\1/p')"
fi
[ -n "$PREV" ] || PREV="$("$TF" environment "$ENV_NAME" output -raw services_image_tag 2> /dev/null || true)"
[[ "$PREV" =~ ^[0-9a-f]{7,40}$ ]] || PREV=""
log "environment $ENV_NAME (namespace $NS): deployed=${PREV:-none} target=$SHA"

apply_tag() {
  "$TF" environment "$ENV_NAME" apply -auto-approve -no-color -parallelism=1 \
    -var "services_image_tag=$1" -var "services_rollout_timeout=$TIMEOUT"
}

verify() {
  local tag="$1" s want ready imgs code path expect hpa_ready
  for s in $SERVICES; do
    k rollout status "deployment/$s" --timeout="$TIMEOUT" > /dev/null || { log "verify: $s did not roll out"; return 1; }
    if [ "$s" = telemetry ]; then
      # Its Deployment omits spec.replicas; wait for the HPA's minimum of 2.
      hpa_ready=0
      for _ in {1..60}; do
        want="$(k get deployment "$s" -o jsonpath='{.spec.replicas}')"
        ready="$(k get deployment "$s" -o jsonpath='{.status.readyReplicas}')"
        if [ "${want:-0}" -ge 2 ] && [ "${ready:-0}" = "$want" ]; then
          hpa_ready=1
          break
        fi
        sleep 2
      done
      [ "$hpa_ready" = 1 ] || { log "verify: telemetry HPA did not reach 2 Ready replicas"; return 1; }
    fi
    want="$(k get deployment "$s" -o jsonpath='{.spec.replicas}')"
    ready="$(k get deployment "$s" -o jsonpath='{.status.readyReplicas}')"
    [ "${ready:-0}" = "$want" ] || { log "verify: $s ready ${ready:-0}/$want"; return 1; }
    # Every pod of the service must run the new image. Pods already being
    # deleted (old ones in their 40 s graceful stop) no longer get traffic.
    imgs="$(k get pods -l "app=$s" -o jsonpath='{range .items[*]}{.metadata.deletionTimestamp}|{.spec.containers[0].image}{"\n"}{end}' \
      | sed -n 's/^|//p' | sort -u)"
    case "$imgs" in
      "localhost/aquaops/$s:$tag"|"localhost/aquaops/$s:$tag@"*) ;;
      *) log "verify: $s pods run [$(tr '\n' ' ' <<< "$imgs")], expected localhost/aquaops/$s:$tag"; return 1 ;;
    esac
  done
  if [ "${AQUAOPS_FORCE_FAIL_VERIFY:-0}" = 1 ]; then
    AQUAOPS_FORCE_FAIL_VERIFY=0; log "verify: forced failure (test hook)"; return 1
  fi
  while read -r path expect; do
    [ -n "$path" ] || continue
    code=""
    for _ in 1 2 3 4 5 6; do      # Eureka and the gateway cache need a few seconds
      code="$(k exec deploy/gateway -- curl -s -o /dev/null -w '%{http_code}' --max-time 10 "http://127.0.0.1:8080$path" 2> /dev/null || true)"
      [ "$code" = "$expect" ] && break
      sleep 5
    done
    [ "$code" = "$expect" ] || { log "verify: gateway $path answered ${code:-nothing}, expected $expect"; return 1; }
  done <<< "$SMOKE_ROUTES"
  log "verify: 7 services Ready on $tag, gateway routes OK"
}

record() {   # status, healthy_at
  local status="$1" healthy="$2" line
  mkdir -p "$(dirname "$HISTORY")"
  line="$(printf '{"environment":"%s","sha":"%s","previous":"%s","status":"%s","pushed_at":%s,"started_at":%s,"finished_at":%s,"push_to_healthy_s":%s,"deploy_s":%s}' \
    "$ENV_NAME" "$SHA" "$PREV" "$status" "$PUSHED_AT" "$START" "$(date +%s)" \
    "$( [ -n "$healthy" ] && echo $((healthy - PUSHED_AT)) || echo null)" "$(( $(date +%s) - START ))")"
  echo "$line" >> "$HISTORY"
  echo "$line"
  if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    {
      echo "### k3s deployment ($ENV_NAME)"
      echo "| Commit | Previous | Result | Push to healthy (NFR-09) | Deployment |"
      echo "| --- | --- | --- | --- | --- |"
      echo "| \`$SHA\` | \`${PREV:-none}\` | $status | $( [ -n "$healthy" ] && echo "$((healthy - PUSHED_AT)) s" || echo -) | $(( $(date +%s) - START )) s |"
    } >> "$GITHUB_STEP_SUMMARY"
  fi
}

# --- 4-5. deploy and verify --------------------------------------------------
if apply_tag "$SHA" && verify "$SHA"; then
  HEALTHY="$(date +%s)"
  log "HEALTHY: $SHA in $ENV_NAME, push-to-healthy $((HEALTHY - PUSHED_AT)) s, deployment $((HEALTHY - START)) s"
  record deployed "$HEALTHY"
  exit 0
fi

# --- 6. rollback -------------------------------------------------------------
if [ -z "$PREV" ] || [ "$PREV" = "$SHA" ]; then
  log "FAILED and nothing to roll back to (previous: ${PREV:-none})"
  record failed ""
  exit 3
fi
log "FAILED: rolling back to $PREV"
# Even if Terraform reports an error (a rollout wait that timed out), the
# Deployments may still converge: the verification decides.
apply_ok=1
apply_tag "$PREV" || { apply_ok=0; log "rollback apply reported an error: verifying the cluster anyway"; }
if verify "$PREV"; then
  [ "$apply_ok" = 1 ] || log "WARNING: healthy on $PREV, but run tf.sh plan -var services_image_tag=$PREV to check the state"
  log "ROLLED BACK: $PREV is healthy again in $ENV_NAME"
  record rolled_back ""
  exit 1
fi
log "ROLLBACK FAILED: $ENV_NAME is not healthy, act now (README, 'Retour arriere')"
record rollback_failed ""
exit 3
