# telemetry-service (owner: E1) — skeleton coming in TECH-1

## Historisation et consultation des mesures (SCRUM-27, FR-05, E2)

Code dans le paquet `ma.aquaops.telemetry.readings`.

### Ce que fait cette partie
- Enregistre chaque mesure valide dans la table `readings` (contrat `sensor-readings` v1).
- Index `(sensor_id, measured_at)` pour que la recherche par capteur et par période soit rapide.
- Expose une API de consultation paginée.

### Pour brancher la validation (E6)
L'interface `MeasureSink` a une seule méthode : `record(Reading)`.
L'implémentation `ReadingMeasureSink` écrit la mesure en base. Le validateur l'appelle pour chaque mesure valide.

### API (port 8081)
`GET /api/telemetry/readings?sensor=<id>&from=<date>&to=<date>&page=0&size=100`

| Paramètre | Obligatoire | Détail |
|---|---|---|
| `sensor` | oui | identifiant du capteur |
| `from`, `to` | oui | dates ISO 8601 UTC, ex. `2026-10-20T00:00:00Z` |
| `page` | non | commence à 0 (défaut 0) |
| `size` | non | de 1 à 1000 (défaut 100) |

Réponse : `content` (mesures triées par date), `page`, `size`, `totalElements`, `totalPages`.
Erreur **400** : paramètre manquant, `from` après `to`, `page` ou `size` hors limites.

Exemple :
```
curl "http://localhost:8081/api/telemetry/readings?sensor=12&from=2026-10-20T00:00:00Z&to=2026-10-21T00:00:00Z&size=50"
```
Cette API n'a pas encore de sécurité (la passerelle et Keycloak viendront plus tard).

### Lancer les tests
1. Démarrer PostgreSQL et créer la base une seule fois : `CREATE DATABASE aquaops_telemetry OWNER aquaops;`
2. Dans `services/telemetry-service` : `./mvnw test`

Variables d'environnement (valeur locale par défaut) : `TELEMETRY_PORT` (8081), `TELEMETRY_DB_URL`, `DB_USER`, `DB_PASSWORD`.

### Eureka (optionnel)

Le service s'inscrit sous le nom `telemetry-service`. Eureka est désactivé
par défaut ; pour activer l'inscription, définir `EUREKA_ENABLED=true` et
`EUREKA_URL` (par exemple `http://eureka:8761/eureka` dans Docker/Kubernetes).
Chaque instance annonce son adresse IP et utilise un bail court pour être
retirée rapidement du registre en cas d'arrêt brutal.

### Test de volume (exigence : requête sur 24 h en 2 s au plus)
- Test : `ReadingVolumeTests` — 100 000 mesures d'un capteur réparties sur 24 h, puis une requête sur toute la période (page de 1000).
- Résultat mesuré : **90 ms** (seuil : 2 000 ms), PostgreSQL 18 en local.
- Une mesure sur une machine locale : à refaire si la base ou le matériel change.
