#!/bin/sh
# Cree /config.json a partir des variables d'environnement, a CHAQUE demarrage du conteneur.
# Le fichier contient uniquement des valeurs publiques (adresse de Keycloak, identifiant du client public) : aucun secret.
set -eu
for name in KEYCLOAK_ISSUER KEYCLOAK_CLIENT_ID ASSETS_API_BASE_URL TELEMETRY_API_BASE_URL SENSORS_MODE SENSOR_SILENCE_THRESHOLD_SECONDS SENSOR_LOOKBACK_SECONDS; do
  eval "value=\${$name:-}"
  if [ -z "$value" ]; then
    echo "[aquaops] ERREUR : $name est vide" >&2; exit 1
  fi
  case "$value" in
    *\"*|*\\*) echo "[aquaops] ERREUR : $name contient un caractere interdit (guillemet ou antislash)" >&2; exit 1 ;;
  esac
done
case "$SENSORS_MODE" in
  mock|api) ;;
  *) echo "[aquaops] ERREUR : SENSORS_MODE doit valoir mock ou api" >&2; exit 1 ;;
esac
case "$SENSOR_SILENCE_THRESHOLD_SECONDS:$SENSOR_LOOKBACK_SECONDS" in
  *[!0-9:]*|0:*|*:0)
    echo "[aquaops] ERREUR : le seuil de silence et la fenêtre de recherche doivent être des entiers positifs" >&2; exit 1 ;;
esac
envsubst '${KEYCLOAK_ISSUER} ${KEYCLOAK_CLIENT_ID} ${ASSETS_API_BASE_URL} ${TELEMETRY_API_BASE_URL} ${SENSORS_MODE} ${SENSOR_SILENCE_THRESHOLD_SECONDS} ${SENSOR_LOOKBACK_SECONDS}' < /etc/nginx/config.json.template > /usr/share/nginx/html/config.json
echo "[aquaops] config.json cree : issuer=${KEYCLOAK_ISSUER} clientId=${KEYCLOAK_CLIENT_ID}"
