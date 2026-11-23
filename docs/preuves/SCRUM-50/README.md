# SCRUM-50 : temps de réponse de l'API REST (NFR-03)

Exigence : 95 % des requêtes en 500 ms au plus. Test de charge k6 (`deploy/load/run-k6.sh`), image k6 épinglée par digest.

## Conditions
- Plateforme d'intégration Docker Compose sur un poste de développement (un conteneur par service, bases PostgreSQL locales).
- Requêtes de lecture (GET) via la passerelle avec un jeton Keycloak (utilisateur `test-superviseur`, choisi parmi les utilisateurs de test pour couvrir le plus de routes), routes choisies au hasard parmi celles qui répondent 200.
- Montée de 0 à 10 puis 20 utilisateurs virtuels, 70 secondes au total, pause de 0,2 à 0,5 s entre deux requêtes.
- Seuils k6 : p95 < 500 ms et moins de 1 % d'erreurs (le test échoue sinon).

## Résultat
- Date : 2026-11-23T11:48:47.512Z
- Requêtes mesurées : 2291 (32.6 par seconde), erreurs : 0.00 %
- **95e percentile global : 24.9 ms** (seuil : 500 ms), moyenne 13.0 ms, maximum 151.8 ms
- Résultat : **conforme (NFR-03)**

| Route | p95 (ms) | moyenne (ms) |
|---|---|---|
| /api/incidents | 23.7 | 12.1 |
| /api/assets/zones | 22.8 | 12.6 |
| /api/assets/sensors | 23.6 | 12.5 |
| /api/assets/pumps | 24.5 | 12.8 |
| /api/assets/pipes | 21.1 | 12.2 |
| /api/assets/tanks | 23.2 | 12.6 |
| /api/assets/interventions | 22.4 | 12.9 |
| /api/detection/anomalies | 36.8 | 19.9 |
| /api/detection/thresholds | 25.2 | 14.8 |
| /api/notifications/list | 12.7 | 7.7 |
## Limites
- Mesure sur un poste local, une instance par service : les valeurs sur k3s peuvent différer.
- Lecture seule : l'ingestion (POST télémétrie) est couverte par RC-04 (autoscaling).
- Les routes qui répondent 403 au rôle utilisé sont exclues de la charge (sondes au démarrage du test).
- Le jeton est demandé au client public aquaops-frontend (mot de passe direct, activé uniquement sur le Keycloak local, réglage non versionné) pour que le jeton porte les rôles.
