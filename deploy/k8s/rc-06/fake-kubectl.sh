#!/usr/bin/env bash
# kubectl SIMULE, uniquement pour tester la logique du script run-rc06.sh sans cluster.
# Il ne prouve rien sur Kubernetes. Comportement pilote par des variables : FAKE_MAX_UNAVAILABLE (0),
# FAKE_READY (2), FAKE_5XX (0), FAKE_CONN (0), FAKE_ROLLOUT_FAIL (0), FAKE_EMPTY_LOG (0), FAKE_LOG (fichier de commandes).
set -euo pipefail
args=("$@")
[ "${args[0]:-}" = "-n" ] && args=("${args[@]:2}")
cmd="${args[0]:-}"; sub="${args[1]:-}"
echo "kubectl ${args[*]}" >> "${FAKE_LOG:-/dev/null}"
case "$cmd" in
  get)
    case "$sub" in
      deployment)
        case "${args[*]}" in
          *maxUnavailable*) echo -n "${FAKE_MAX_UNAVAILABLE:-0}";;
          *readyReplicas*)  echo -n "${FAKE_READY:-2}";;
        esac;;
      pods) echo "NAME READY STATUS"; echo "detection-service-abc 1/1 Running";;
    esac;;
  delete|run|wait|exec|set) ;;
  rollout)
    case "$sub" in
      restart) ;;
      status)
        if [ "${FAKE_ROLLOUT_FAIL:-0}" = 1 ]; then echo "error: timed out waiting for the condition" >&2; exit 1; fi
        echo "deployment \"detection-service\" successfully rolled out";;
    esac;;
  logs)
    [ "${FAKE_EMPTY_LOG:-0}" = 1 ] && exit 0
    now=$(date +%s)
    for i in $(seq 1 50); do echo "$now 200"; done
    for i in $(seq 1 "${FAKE_5XX:-0}"); do echo "$now 503"; done
    for i in $(seq 1 "${FAKE_CONN:-0}"); do echo "$now 000"; done;;
  *) echo "commande inattendue : ${args[*]}" >&2; exit 99;;
esac
