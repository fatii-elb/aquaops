#!/bin/sh
# Verifie GATEWAY_URL avant que nginx ne demarre : une erreur ici arrete le conteneur avec un message clair.
set -eu
case "${GATEWAY_URL:-}" in
  "")
    echo "[aquaops] ERREUR : GATEWAY_URL est vide (ex. http://gateway:8080)" >&2; exit 1 ;;
  */)
    echo "[aquaops] ERREUR : GATEWAY_URL ne doit pas finir par / (valeur : ${GATEWAY_URL})" >&2; exit 1 ;;
  http://*|https://*) ;;
  *)
    echo "[aquaops] ERREUR : GATEWAY_URL doit commencer par http:// ou https:// (valeur : ${GATEWAY_URL})" >&2; exit 1 ;;
esac
echo "[aquaops] passerelle : ${GATEWAY_URL}"
