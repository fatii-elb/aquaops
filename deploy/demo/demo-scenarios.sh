#!/usr/bin/env bash
# Démo AquaOps : enchaîne les scénarios du simulateur (durée d'environ 4 min 30).
# Prérequis : docker compose up -d (dossier deploy/), puis telemetry-service,
# detection-service et simulator lancés avec ./mvnw spring-boot:run.
# Usage : bash deploy/demo/demo-scenarios.sh

SIM="${SIMULATOR_URL:-http://localhost:8086}/api/simulator/scenarios"

# Durées en secondes (modifiables par variable d'environnement)
T_NORMAL="${T_NORMAL:-30}"
T_FUITE="${T_FUITE:-60}"      # l'anomalie arrive environ 20 s après le démarrage
T_POMPE="${T_POMPE:-20}"      # défaut de pompe après 5 s
T_SILENCE="${T_SILENCE:-40}"  # doit dépasser le seuil de silence de detection-service
T_PIC="${T_PIC:-30}"
T_RETOUR="${T_RETOUR:-20}"

attendre() {
  echo "   ... attente de $1 s"
  sleep "$1"
}

demarrer() {
  curl -s -X POST "$SIM/$1/start" > /dev/null
}

arreter() {
  curl -s -X POST "$SIM/stop" > /dev/null
}

echo "=== Étape 1 : tout est normal ==="
arreter
attendre "$T_NORMAL"

echo "=== Étape 2 : fuite (pression basse + débit haut) -> LEAK_SUSPECTED ==="
demarrer fuite
attendre "$T_FUITE"

echo "=== Étape 3 : retour à la normale ==="
arreter
attendre "$T_RETOUR"

echo "=== Étape 4 : pompe en défaut -> PUMP_FAULT ==="
demarrer pompe
attendre "$T_POMPE"
arreter
attendre "$T_RETOUR"

echo "=== Étape 5 : capteur silencieux -> SENSOR_SILENT ==="
demarrer silence
attendre "$T_SILENCE"
arreter
attendre "$T_RETOUR"

echo "=== Bonus : pic de débit seul (aucune anomalie attendue) ==="
demarrer pic
attendre "$T_PIC"
arreter

echo "=== Démo terminée ==="