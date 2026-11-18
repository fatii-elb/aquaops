# Journal d'audit (SCRUM-46, FR-24)

Propriétaire : E2 · service `assets-service` · table `audit_log` (base `aquaops_assets`, créée automatiquement par Hibernate).

Contrat de l'API : `contracts/api/assets-audit.openapi.yaml`.

## Ce qui est tracé

| Action | Qui l'écrit | `source` |
|---|---|---|
| Création, modification, suppression de zones, conduites, réservoirs, pompes, capteurs | les contrôleurs de `assets-service` | `assets-service` |
| Création, affectation, changement de statut d'une intervention | `InterventionController` | `assets-service` |
| Changements d'incident (`INCIDENT_CREATED`, `INCIDENT_CONFIRMED`...) | `IncidentEventsAuditConsumer`, lit le topic `incident-events` (groupe `assets-service-audit`) | `incident-events` |

Une ligne = qui (`actor`, `actorRole`), quand (`occurredAt`, UTC), quoi (`action`, `entityType`, `entityId`, `details`).

- L'écriture métier et la ligne d'audit sont dans **la même transaction** : si l'audit échoue, l'action est annulée.
- Une action refusée (403, 400, 409) ne laisse **aucune ligne**.
- L'API est en lecture seule : aucune route ne modifie ni ne supprime une ligne.
- Un événement Kafka rejoué ne crée pas de doublon.
- Si la base est indisponible quand un événement d'incident arrive, `assets-service` le retente (30 fois, toutes les 2 secondes, voir `KafkaRetryConfig`) au lieu de l'ignorer. Seul un message illisible ou incomplet est ignoré.

## Consulter

`GET /api/assets/audit` avec `actor`, `action`, `entityType`, `from`, `to`, `page`, `size`. Réservé aux rôles `superviseur` et `administrateur`. Écran : `/audit` dans Angular.

## Tracer une nouvelle action (par exemple les seuils, FR-13)

Dans `assets-service`, injecter `AuditService` et appeler, **dans la même méthode `@Transactional`** que l'écriture :

```java
audit.record("UPDATE", "THRESHOLD", id, "pression min : 2.0 bar");
```

L'auteur et son rôle sont lus dans le jeton. Pour un autre service, il n'y a rien à copier : publier un événement sur `incident-events` (ou proposer un nouveau topic via la règle de `contracts/events/README.md`) et ajouter un consommateur ici.

## À savoir

- `notifications-service` a sa propre table `AuditLog` (qui, quand, quoi) alimentée par le même topic. Les deux ne se gênent pas (groupes de consommateurs différents), mais **l'écran d'audit lit uniquement celle d'assets-service**. À décider avec E6 : garder ou retirer l'ancienne.
- **Connexions (FR-24, reste de SCRUM-46)** : `KeycloakLoginAuditPoller` copie les événements `LOGIN`, `LOGIN_ERROR` et `LOGOUT` de Keycloak
  dans `audit_log` (`source` = `keycloak`), toutes les 30 s, avec le compte de service `aquaops-audit` (rôle `view-events` seulement).
  Désactivé par défaut : `AUDIT_KEYCLOAK_ENABLED=true`, `KEYCLOAK_BASE_URL` et `KEYCLOAK_AUDIT_CLIENT_SECRET` (voir `docs/keycloak/README.md`).
  Contrôle : `GET /api/assets/audit?action=LOGIN` (jeton superviseur ou administrateur).
