# Documentation des contrats (SCRUM-60)

Page rédigée par E6 · version 0, à relire par l'équipe.

La règle de versionnement est dans `contracts/events/README.md`. Les schémas JSON sont dans `contracts/events/`. Cette page donne la vue d'ensemble.

## 1. Les événements Kafka

| Topic | Schéma | Qui l'écrit | Qui le lit | État |
|---|---|---|---|---|
| `sensor-readings` | `contracts/events/sensor-readings.v1.json` | E1 (télémétrie) | E3 (détection), stockage télémétrie | brouillon v1 |
| `anomaly-detected` | `contracts/events/anomaly-detected.v1.json` | E3 (détection) | E4 (incidents), E6 (notifications) | brouillon v1 |
| `incident-events` | `contracts/events/incident-events.v1.json` | E4 (incidents) | E6 (notifications), audit | brouillon v1 d'E4, en revue |

## 2. Exemples de messages

### sensor-readings

    { "eventVersion": "1", "sensorId": 12, "zoneId": 3, "type": "PRESSURE", "value": 2.4, "unit": "bar", "measuredAt": "2026-10-12T21:30:00Z" }

- `type` : PRESSURE, FLOW, LEVEL ou PUMP_STATE.
- `value` est obligatoire sauf pour PUMP_STATE, qui utilise `pumpState` (NORMAL ou FAULT).

### anomaly-detected

    { "eventVersion": "1", "anomalyId": "7b0f2a3e-4c1d-4e6a-9d52-1f3c8a9b0e11", "type": "LEAK_SUSPECTED", "zoneId": 3, "sensorId": 12, "dedupKey": "LEAK_SUSPECTED:3:12", "severity": "CRITICAL", "measuredAt": "2026-10-12T21:30:00Z", "detectedAt": "2026-10-12T21:30:04Z" }

- `type` : LEAK_SUSPECTED, SENSOR_SILENT, PUMP_FAULT ou LOW_LEVEL.
- `severity` est optionnelle : LOW, MEDIUM, HIGH ou CRITICAL.

### incident-events

Brouillon v1 d'E4, produit par incidents-service, lu par notifications-service. Fichier : `contracts/events/incident-events.v1.json` (branche `feature/SCRUM-60-contract-incident-events`, pas encore dans `main`).

- `eventType` : INCIDENT_CREATED, INCIDENT_ASSIGNED, INCIDENT_STATUS_CHANGED, INCIDENT_RESOLVED ou INCIDENT_CLOSED.
- Champs obligatoires : `eventVersion`, `eventType`, `incidentId`, `zoneId`, `status`, `occurredAt`.
- `anomalyId` (optionnel) relie l'incident à l'anomalie d'origine.

## 3. Les API REST

| Service | Préfixe | Documentation OpenAPI |
|---|---|---|
| notifications-service | `/api/notifications/**` | `contracts/api/notifications.openapi.yaml`, générée par le service (page Swagger : `/api/notifications/swagger-ui.html`) |
| assets-service | `/api/assets/**` | à fournir par E2 |
| incidents-service | `/api/incidents/**` | brouillon d'E4, en revue : `contracts/api/incidents.openapi.yaml` (branche `feature/SCRUM-60-openapi-incidents`) |
| telemetry-service | `/api/telemetry/**` | à fournir par E1 |
| detection-service | `/api/detection/**` | à fournir par E3 |

## 4. Modifier un contrat

Voir la section 6 de `contracts/events/README.md` : pull request relue par le producteur et par un consommateur.