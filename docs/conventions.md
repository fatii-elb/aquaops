# AquaOps — Team conventions

> **Status: proposal v0** — to validate at sprint planning, Monday 12 October 2026.
> Owner: E5 (foundations). Related cards: TECH-1, TECH-3.

## 1. Shared tool versions

Everyone uses the same versions so that code runs identically on all machines.

| Tool | Version | Note |
| --- | --- | --- |
| Java (JDK) | **21 (LTS)** | Temurin recommended |
| Build tool | **Maven**, via the `mvnw` wrapper | Nobody needs to install Maven |
| Spring Boot | **4.1.1** | Confirmed from the gateway skeleton, 8 Oct |
| Node.js | LTS (to confirm with E4, Angular compatibility) | |
| PostgreSQL | to confirm with E2 | |
| Kafka | to confirm with E1 | |
| Keycloak | to confirm with E2 | Must run on port 8180 |

## 2. Repository layout

```text
aquaops/
├── docs/                 install guide, conventions, Definition of Done
├── contracts/events/     the 3 Kafka event contracts (SCRUM-60)
├── services/             one folder per backend service
│   ├── gateway/                 E5
│   ├── telemetry-service/       E1
│   ├── simulator/               E1
│   ├── assets-service/          E2
│   ├── detection-service/       E3
│   ├── incidents-service/       E4
│   └── notifications-service/   E6
└── frontend/             the Angular application (E4, support E5)
```

Service folder names: lowercase, hyphens, ending in `-service`
(`assets-service`, `telemetry-service`, …). Exceptions: `gateway`, `simulator`.

## 3. Java project settings

| Setting | Value | Example |
| --- | --- | --- |
| Group | `ma.aquaops` | same for everybody |
| Artifact | = folder name | `assets-service` |
| Package | `ma.aquaops.<short>` | `ma.aquaops.assets` |
| Packaging | Jar | |
| Config file | YAML | `src/main/resources/application.yml` |

## 4. Ports

| Component | Port |
| --- | --- |
| API Gateway | 8080 |
| Telemetry | 8081 |
| Assets | 8082 |
| Detection | 8083 |
| Incidents | 8084 |
| Notifications | 8085 |
| Simulator | 8086 |
| Keycloak | **8180** (its default 8080 clashes with the gateway) |
| PostgreSQL | 5432 |
| Kafka | 9092 |
| Angular dev server | 4200 |

## 5. API paths — IMPORTANT for the gateway

Each service prefixes **all** its own paths with its name:

| Service | Path prefix | Example |
| --- | --- | --- |
| Telemetry | `/api/telemetry/**` | `/api/telemetry/readings` |
| Assets | `/api/assets/**` | `/api/assets/zones/3` |
| Detection | `/api/detection/**` | `/api/detection/rules` |
| Incidents | `/api/incidents/**` | `/api/incidents/12` |
| Notifications | `/api/notifications/**` | `/api/notifications/subscribe` |
| Simulator | `/api/simulator/**` | `/api/simulator/scenarios` |

Why: the gateway routes by the start of the path. Without the prefix, it cannot
tell which service a request belongs to.

## 6. Environment variables (TECH-3)

**Rule: no address, port or password is written in the code.** Every setting is
read from an environment variable with a local default:

```yaml
server:
  port: ${ASSETS_PORT:8082}

spring:
  datasource:
    url: ${ASSETS_DB_URL:jdbc:postgresql://localhost:5432/aquaops_assets}
    username: ${DB_USER:aquaops}
    password: ${DB_PASSWORD:aquaops}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
```

`${ASSETS_PORT:8082}` reads as: "use the variable `ASSETS_PORT` if it exists,
otherwise 8082". The same code then runs on a laptop and on the shared server.

Naming:

| Rule | Example |
| --- | --- |
| UPPER_SNAKE_CASE | `KAFKA_BOOTSTRAP_SERVERS` |
| Shared infrastructure: no prefix | `DB_HOST`, `KEYCLOAK_ISSUER_URI` |
| Specific to one service: service name first | `ASSETS_PORT`, `ASSETS_DB_URL` |
| Ending says the type | `_URL`, `_PORT`, `_HOST`, `_PASSWORD`, `_SECRET` |
| Never a real secret as a default or in `.env.example` | `DB_PASSWORD=change-me` |

All names are listed in [`.env.example`](../.env.example) at the repository root.

> **Note:** Spring Boot does not read a `.env` file by itself. You need nothing
> locally thanks to the defaults. To override a value, set it in your terminal
> (`export ASSETS_PORT=9000`) or in your IDE run configuration.

## 7. Git

| Rule | Value |
| --- | --- |
| Main branch | `main`, protected: no direct push |
| Branch name | `feature/<CARD>-<short-name>`, e.g. `feature/TECH-9-gateway` |
| Commit message | `<CARD>: what I did`, e.g. `TECH-9: add gateway routes` |
| Pull request | reviewed by one other student before merge |

Full Definition of Done: see `docs/definition-of-done.md` (TECH-7, E3).

## 8. Open questions for sprint planning

1. Does the simulator send readings to Telemetry over HTTP (through the gateway)
   or directly into Kafka? Affects the gateway and NFR-18.
2. Node.js, PostgreSQL, Kafka and Keycloak versions (E1, E2, E4 to confirm).
3. How is an incident's priority calculated (low, medium, high, critical)?
