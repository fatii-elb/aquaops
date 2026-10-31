# incidents-service (propriétaire : E4)

Service des incidents et des interventions de maintenance d'AquaOps.
État actuel (TECH-1) : squelette qui démarre et répond sur `/actuator/health`, sans code métier.

| Élément | Valeur |
|---|---|
| Port | 8084 (variable `INCIDENTS_PORT`) |
| Préfixe de l'API | `/api/incidents/**` (à venir) |
| Base de données | PostgreSQL `aquaops_incidents` (`INCIDENTS_DB_URL`, `DB_USER`, `DB_PASSWORD`) |
| Kafka | `KAFKA_BOOTSTRAP_SERVERS` ; consomme `anomaly-detected`, produira `incident-events` |
| Java / Spring Boot | 21 / 4.1.1 |

## Lancer

PostgreSQL et Kafka doivent tourner (voir `docs/install-guide.md`), puis :

```bash
./mvnw spring-boot:run
curl http://localhost:8084/actuator/health
```

Réponse attendue : `{"groups":["liveness","readiness"],"status":"UP"}`.
