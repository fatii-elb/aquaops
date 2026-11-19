#!/usr/bin/env bash
# Depouille le journal de trafic de RC-06 : une ligne « <epoch> <code HTTP> » par requete.
# 000 = connexion impossible ou delai depasse. Sortie : 0 aucun 5xx ni echec de connexion, 1 au moins un,
# 2 journal vide (le test ne prouve rien).
# Usage : tally.sh traffic.log
set -euo pipefail

log="${1:?usage: tally.sh traffic.log}"
[ -f "$log" ] || { echo "Journal introuvable : $log" >&2; exit 2; }

awk '
  NF == 2 && $2 ~ /^[0-9]+$/ {
    total++
    code = $2 + 0
    if (code == 0)        conn++
    else if (code >= 500) err5++
    else if (code >= 400) err4++
    else if (code >= 300) r3++
    else                  ok2++
  }
  END {
    printf "total=%d 2xx=%d 3xx=%d 4xx=%d 5xx=%d connexion=%d\n", total, ok2, r3, err4, err5, conn
    if (total == 0) exit 2
    exit (err5 > 0 || conn > 0) ? 1 : 0
  }
' "$log"
