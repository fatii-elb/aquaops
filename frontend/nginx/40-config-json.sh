#!/bin/sh
# Cree /config.json a partir des variables d'environnement, a CHAQUE demarrage du conteneur.
# Le fichier contient uniquement des valeurs publiques (adresse de Keycloak, identifiant du client public) : aucun secret.
set -eu
for name in KEYCLOAK_ISSUER KEYCLOAK_CLIENT_ID; do
  eval "value=\${$name:-}"
  if [ -z "$value" ]; then
    echo "[aquaops] ERREUR : $name est vide" >&2; exit 1
  fi
  case "$value" in
    *\"*|*\\*) echo "[aquaops] ERREUR : $name contient un caractere interdit (guillemet ou antislash)" >&2; exit 1 ;;
  esac
done
envsubst '${KEYCLOAK_ISSUER} ${KEYCLOAK_CLIENT_ID}' < /etc/nginx/config.json.template > /usr/share/nginx/html/config.json
echo "[aquaops] config.json cree : issuer=${KEYCLOAK_ISSUER} clientId=${KEYCLOAK_CLIENT_ID}"
