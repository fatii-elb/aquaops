#!/usr/bin/env bash
# Test automatisé SCRUM-64 : vérifie qu'un flux non autorisé est bloqué par NetworkPolicy.
# Usage : ./test-networkpolicy.sh

set -euo pipefail

NAMESPACE="aquaops"
TEST_POD="netpol-attacker"
TARGET_SERVICE="postgres"
TARGET_PORT="5432"

echo "=== Test NetworkPolicy (default-deny-all) ==="
echo "Namespace : $NAMESPACE"
echo "Cible     : $TARGET_SERVICE:$TARGET_PORT (doit être bloquée)"
echo

# 1. Nettoyage préalable
kubectl -n "$NAMESPACE" delete pod "$TEST_POD" --ignore-not-found >/dev/null 2>&1

# 2. Création du pod attaquant
echo "[1/3] Lancement du pod attaquant..."
kubectl -n "$NAMESPACE" run "$TEST_POD" \
  --image=curlimages/curl:latest \
  --restart=Never \
  --labels="app=attacker" \
  --command -- sleep 3600 >/dev/null

kubectl -n "$NAMESPACE" wait --for=condition=Ready pod/"$TEST_POD" --timeout=60s >/dev/null

# 3. Test de connexion
echo "[2/3] Tentative de connexion à $TARGET_SERVICE:$TARGET_PORT..."
if kubectl -n "$NAMESPACE" exec "$TEST_POD" -- \
  timeout 5 curl -s --connect-timeout 3 telnet://$TARGET_SERVICE:$TARGET_PORT 2>/dev/null; then
  echo "❌ ÉCHEC : La connexion a réussi ! La NetworkPolicy ne bloque pas."
  RESULT=1
else
  echo "✅ SUCCÈS : Connexion bloquée comme prévu."
  RESULT=0
fi

# 4. Nettoyage
echo "[3/3] Nettoyage..."
kubectl -n "$NAMESPACE" delete pod "$TEST_POD" --ignore-not-found >/dev/null 2>&1

echo
if [ "$RESULT" -eq 0 ]; then
  echo "=== TEST PASSÉ ==="
else
  echo "=== TEST ÉCHOUÉ ==="
fi
exit "$RESULT"
