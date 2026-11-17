#!/usr/bin/env bash
# =============================================================================
# AquaOps: the ONLY way to run Terraform on k3s (SCRUM-56 / NFR-09, E5).
#
#   deploy/terraform/tf.sh <stack> <env> <command> [terraform options]
#
#   stack    cluster      cluster-wide add-ons (cert-manager); env must be "cluster"
#            environment  one namespace per environment; env = envs/<env>.tfvars
#   command  init | plan | apply | destroy | output
#
# Examples:
#   deploy/terraform/tf.sh cluster cluster plan
#   deploy/terraform/tf.sh environment test plan
#   AQUAOPS_CONFIRM_DESTROY=rc11 deploy/terraform/tf.sh environment rc11 destroy
#
# Safety:
#   - KUBE_CONFIG_PATH must point to a kubeconfig OUTSIDE the repository
#     (mode 600 or 400 on Linux). The cluster address is printed before
#     apply/destroy.
#   - State outside the repository: $AQUAOPS_TF_STATE_DIR (default
#     ~/.local/state/aquaops-terraform), one state per stack and environment.
#   - Workspace = environment, always (Terraform also checks it).
#   - destroy: refused for "cluster" and "test"; allowed only for the
#     disposable environments below, with AQUAOPS_CONFIRM_DESTROY=<env>.
# Secrets are never read here: export TF_VAR_* before apply (README).
# =============================================================================
set -euo pipefail

DISPOSABLE_ENVS="rc11"
TERRAFORM="${TERRAFORM:-terraform}"
KUBECTL="${KUBECTL:-kubectl}"

die() { echo "tf.sh: $*" >&2; exit 2; }

[ $# -ge 3 ] || die "usage: tf.sh <cluster|environment> <env> <init|plan|apply|destroy|output> [options]"
STACK="$1"; ENV_NAME="$2"; CMD="$3"; shift 3

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPO="$(cd "$HERE/../.." && pwd -P)"
STACK_DIR="$HERE/$STACK"

# --- arguments ----------------------------------------------------------------
case "$STACK" in
  cluster)
    [ "$ENV_NAME" = cluster ] || die "the cluster stack has one environment: \"cluster\""
    VAR_ARGS=() ;;
  environment)
    [[ "$ENV_NAME" =~ ^[a-z][a-z0-9-]{1,20}$ ]] || die "invalid environment name: $ENV_NAME"
    [ -f "$STACK_DIR/envs/$ENV_NAME.tfvars" ] || die "no envs/$ENV_NAME.tfvars"
    VAR_ARGS=("-var-file=envs/$ENV_NAME.tfvars") ;;
  *) die "unknown stack: $STACK (cluster or environment)" ;;
esac

case "$CMD" in
  init|plan|apply|destroy|output) ;;
  *) die "unknown command: $CMD (init, plan, apply, destroy, output)" ;;
esac

if [ "$CMD" = destroy ]; then
  case " $DISPOSABLE_ENVS " in
    *" $ENV_NAME "*) ;;
    *) die "destroy refused: \"$ENV_NAME\" is not a disposable environment ($DISPOSABLE_ENVS)" ;;
  esac
  [ "${AQUAOPS_CONFIRM_DESTROY:-}" = "$ENV_NAME" ] \
    || die "destroy refused: set AQUAOPS_CONFIRM_DESTROY=$ENV_NAME to confirm"
fi

# --- kubeconfig: given, outside the repository, private -----------------------
[ -n "${KUBE_CONFIG_PATH:-}" ] || die "KUBE_CONFIG_PATH is not set (path to the k3s kubeconfig)"
[ -f "$KUBE_CONFIG_PATH" ] || die "KUBE_CONFIG_PATH is not a file"
KCFG_REAL="$(cd "$(dirname "$KUBE_CONFIG_PATH")" && pwd -P)/$(basename "$KUBE_CONFIG_PATH")"
case "$KCFG_REAL/" in
  "$REPO"/*) die "the kubeconfig must not be inside the repository" ;;
esac
if [ "$(uname -s)" = Linux ]; then
  mode="$(stat -c '%a' "$KCFG_REAL")"
  case "$mode" in
    600|400) ;;
    *) die "kubeconfig mode is $mode: run chmod 600 on it" ;;
  esac
fi

# --- state: outside the repository, private -----------------------------------
STATE_ROOT="${AQUAOPS_TF_STATE_DIR:-$HOME/.local/state/aquaops-terraform}"
STATE_DIR="$STATE_ROOT/$STACK"
umask 077
mkdir -p "$STATE_DIR"
case "$(cd "$STATE_DIR" && pwd -P)/" in
  "$REPO"/*) die "AQUAOPS_TF_STATE_DIR must be outside the repository" ;;
esac

tf() { "$TERRAFORM" -chdir="$STACK_DIR" "$@"; }

tf init -input=false -no-color -reconfigure \
  -backend-config="path=$STATE_DIR/default.tfstate" \
  -backend-config="workspace_dir=$STATE_DIR/workspaces" > /dev/null
tf workspace select -or-create "$ENV_NAME" > /dev/null
[ "$(tf workspace show)" = "$ENV_NAME" ] || die "could not select workspace $ENV_NAME"

[ "$CMD" = init ] && { echo "tf.sh: $STACK/$ENV_NAME ready (state in $STATE_DIR)"; exit 0; }

if [ "$CMD" = apply ] || [ "$CMD" = destroy ]; then
  server="$("$KUBECTL" --kubeconfig "$KCFG_REAL" config view --minify -o jsonpath='{.clusters[0].cluster.server}' 2>/dev/null || true)"
  echo "tf.sh: $CMD $STACK/$ENV_NAME on cluster ${server:-<unknown>}"
fi

case "$CMD" in
  output) tf output "$@" ;;
  *)      tf "$CMD" -input=false "${VAR_ARGS[@]}" "$@" ;;
esac
