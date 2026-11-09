# assets-service (propriétaire : E2)

Service des actifs du réseau d'eau, des interventions de maintenance et du journal d'audit.
Port **8082** ; derrière la passerelle, toutes les routes commencent par `/api/assets/`.

## Ce que fait le service

| Fonction | Carte | Détail |
|---|---|---|
| Actifs : zones, conduites, réservoirs, pompes, capteurs | SCRUM-11 et suivantes | CRUD complet, chaque actif appartient à une zone |
| Interventions de maintenance | SCRUM-41, SCRUM-42 (FR-19, FR-20) | liées à un incident, affectées à une équipe, avec un statut |
| Journal d'audit | SCRUM-46 (FR-24) | qui, quand, quoi, pour chaque écriture sur les actifs et chaque changement d'incident |

## API

| Ressource | Routes |
|---|---|
| Zones | `/api/assets/zones` |
| Conduites | `/api/assets/pipes` |
| Réservoirs | `/api/assets/tanks` |
| Pompes | `/api/assets/pumps` |
| Capteurs | `/api/assets/sensors` |
| Interventions | `/api/assets/interventions` |
| Journal d'audit | `/api/assets/audit` et `/api/assets/audit/actions` |

Pour les cinq types d'actifs : `GET` (liste), `GET /{id}`, `POST`, `PUT /{id}`, `DELETE /{id}`.
Un actif (conduite, réservoir, pompe, capteur) doit référencer une zone existante, sinon **400**.
Un identifiant inconnu donne **404**.

### Interventions

| Route | Rôle |
|---|---|
| `GET /api/assets/interventions?incidentId=<id>` | liste, filtre facultatif par incident |
| `GET /api/assets/interventions/{id}` | une intervention |
| `POST /api/assets/interventions` | crée une intervention **Planifiée** liée à `incidentId` (corps : `{"incidentId": 42}`) |
| `PUT /api/assets/interventions/{id}/assign` | affecte à une équipe (corps : `{"team": "Equipe A", "assignedTo": "tech1"}`, `assignedTo` facultatif) |
| `PUT /api/assets/interventions/{id}/status` | change le statut (corps : `{"status": "IN_PROGRESS"}`) |

Statuts : `PLANNED` (Planifiée) → `IN_PROGRESS` (En cours) → `DONE` (Terminée). Aucun retour en arrière, aucun saut.

- Transition interdite : **409**.
- Affecter une intervention terminée : **409**.
- Changer le statut : seulement le technicien assigné (son `preferred_username`), un superviseur ou un administrateur. Sinon **403**.

### Journal d'audit

`GET /api/assets/audit?actor=&action=&entityType=&from=&to=&page=0&size=20`

Tous les paramètres sont facultatifs. Réponse : `content` (les plus récentes d'abord), `page`, `size`, `totalElements`, `totalPages`.
**400** si `size` n'est pas entre 1 et 200, si `page` est négatif ou si `from` est après `to`.
Lecture seule, réservé aux superviseurs et administrateurs.
Détails, contenu des lignes et ajout d'une nouvelle action tracée : [`docs/audit.md`](../../docs/audit.md).
Contrat : [`contracts/api/assets-audit.openapi.yaml`](../../contracts/api/assets-audit.openapi.yaml).

## Sécurité

L'API attend un jeton Keycloak (`Authorization: Bearer ...`). Sans jeton : **401**. Les rôles viennent du jeton.

| Action | Rôles autorisés |
|---|---|
| Lire les actifs et les interventions (`GET`) | opérateur, superviseur, technicien, administrateur |
| Créer, modifier, supprimer (`POST`, `PUT`, `DELETE`) | superviseur, administrateur |
| Changer le statut d'une intervention | technicien (le sien), superviseur, administrateur |
| Consulter le journal d'audit | superviseur, administrateur |

Un rôle insuffisant donne **403**. `/actuator/health` est ouvert à tous.

## Variables d'environnement

Chaque variable a une valeur locale par défaut : il n'y a rien à configurer pour démarrer sur un poste.

| Variable | Défaut | Rôle |
|---|---|---|
| `ASSETS_PORT` | `8082` | port du service |
| `ASSETS_DB_URL` | `jdbc:postgresql://localhost:5432/aquaops_assets` | base PostgreSQL |
| `DB_USER`, `DB_PASSWORD` | valeurs locales de développement | connexion à la base |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka (lecture du topic `incident-events` pour l'audit) |
| `KEYCLOAK_ISSUER_URI` | `http://localhost:8180/realms/aquaops` | émetteur des jetons ; doit être égal au champ `iss` des jetons |
| `ASSETS_AUDIT_KAFKA_ENABLED` | `true` | `false` : ne pas lire `incident-events` (utile sans Kafka) |

Les tables sont créées au démarrage par Hibernate (`ddl-auto: update`).

## Lancer, tester

Démarrer l'infrastructure (PostgreSQL, Kafka, Keycloak) depuis `deploy/` : `docker compose up -d`.
Elle crée la base `aquaops_assets`.

    cd services/assets-service
    ./mvnw spring-boot:run     # démarrer le service
    ./mvnw verify              # tests

Les tests d'intégration demandent PostgreSQL démarré et la base `aquaops_assets`. Ils n'ont **pas** besoin de Kafka : le consommateur d'audit est désactivé pendant les tests (`src/test/resources/application.properties`). Voir `docs/testing.md`.

## Conteneur

Le service est construit avec le Dockerfile commun d'E5, `deploy/docker/spring-service.Dockerfile`.
Il est déclaré (service `assets`) dans `deploy/integration/docker-compose.integration.yml`, avec un contrôle de santé sur `/actuator/health`.
