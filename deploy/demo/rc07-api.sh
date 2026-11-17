#!/usr/bin/env bash
# RC-07 (complément) : contrôle d'accès de l'API des actifs avec les 4 rôles (la réserve de la recette : « API testée avec l'opérateur seulement »).
# Écriture : un POST avec un corps vide est refusé (403) AVANT toute validation pour un rôle sans droit, et rejeté en 400 pour un rôle autorisé :
# aucune donnée n'est créée.
# Prérequis : Keycloak et assets-service lancés ; mots de passe définitifs des 4 utilisateurs de test (voir docs/install-guide.md).
# Usage : PWD_OPERATEUR1=... PWD_TECHNICIEN1=... PWD_SUPERVISEUR1=... PWD_ADMIN1=... bash deploy/demo/rc07-api.sh
# Rapport : docs/preuves/RC-07/rc07-api-roles.txt
# Note : les utilisateurs superviseur1 et admin1 ont le MFA pour la connexion par navigateur ; le mot de passe direct (admin-cli)
# ne le demande pas (voir docs/keycloak/README.md). Leur mot de passe temporaire doit avoir été changé avant.

KC="${KEYCLOAK_URL:-http://localhost:8180}/realms/aquaops/protocol/openid-connect/token"
ASSETS="${ASSETS_URL:-http://localhost:8082}/api/assets"
RAPPORT="${RAPPORT:-docs/preuves/RC-07/rc07-api-roles.txt}"
ECHECS=0
mkdir -p "$(dirname "$RAPPORT")"
: > "$RAPPORT"
ligne() { echo "$1"; echo "$1" >> "$RAPPORT"; }

jeton() {
  curl -s -X POST "$KC" -d "client_id=admin-cli" -d "grant_type=password" -d "username=$1" --data-urlencode "password=$2" \
    | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'
}

# $1 = utilisateur, $2 = variable du mot de passe, $3 = attendu sur le journal d'audit (200 ou 403), $4 = autorisé en écriture (oui/non)
tester() {
  user="$1"; pwd="${!2}"
  if [ -z "$pwd" ]; then ligne "$user : mot de passe absent ($2) : NON TESTÉ"; ECHECS=$((ECHECS+1)); return; fi
  tok=$(jeton "$user" "$pwd")
  if [ -z "$tok" ]; then ligne "$user : jeton impossible à obtenir (mot de passe temporaire non changé ?) : NON TESTÉ"; ECHECS=$((ECHECS+1)); return; fi
  a=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $tok" "$ASSETS/audit")
  w=$(curl -s -o /dev/null -w "%{http_code}" -X POST -H "Authorization: Bearer $tok" -H "Content-Type: application/json" -d '{}' "$ASSETS/zones")
  if [ "$a" = "$3" ]; then ra="conforme"; else ra="ÉCART (attendu $3)"; ECHECS=$((ECHECS+1)); fi
  if [ "$4" = "oui" ]; then
    case "$w" in 401|403) rw="ÉCART (écriture refusée, autorisée attendue)"; ECHECS=$((ECHECS+1)) ;; *) rw="conforme (non refusé)" ;; esac
  else
    if [ "$w" = "403" ]; then rw="conforme"; else rw="ÉCART (attendu 403)"; ECHECS=$((ECHECS+1)); fi
  fi
  ligne "$user : GET audit = $a [$ra] ; POST zones = $w [$rw]"
}

ligne "RC-07 API : $(date -u +%Y-%m-%dT%H:%M:%SZ)"
tester operateur1 PWD_OPERATEUR1 403 non
tester technicien1 PWD_TECHNICIEN1 403 non
tester superviseur1 PWD_SUPERVISEUR1 200 oui
tester admin1 PWD_ADMIN1 200 oui
if [ "$ECHECS" -eq 0 ]; then ligne "Résultat : conforme à la matrice (audit et écriture)"; else ligne "Résultat : $ECHECS écart(s) ou test manquant"; fi
echo "Rapport : $RAPPORT"
exit "$ECHECS"
