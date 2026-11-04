# AquaOps — Development environment installation guide

> **Version 1** (TECH-2) · Owner: E5.
> Goal: from a blank machine to a running project.
> PostgreSQL, Kafka and Keycloak now run in Docker: nothing to install by hand.
> Report anything that does not work to E5.

> **Windows users:** run every command of this guide in **Git Bash**, not
> PowerShell or cmd. The commands are then the same on Windows, macOS and Linux.

---

## 1. Prerequisites to install yourself

| Tool | Version | Where to get it |
| --- | --- | --- |
| JDK | **21 LTS, Eclipse Temurin** | https://adoptium.net |
| Git | 2.x, latest | https://git-scm.com/downloads |
| Docker Desktop | latest, with Docker Compose v2 or later | https://www.docker.com/products/docker-desktop/ |
| Node.js | **24 LTS** (frontend only) | https://nodejs.org |

Maven is **not** installed: every service ships its own `./mvnw` wrapper.
An IDE is up to you (IntelliJ IDEA Community or VS Code).

### 1.1 JDK 21 (Temurin)

During install, tick **"Set JAVA_HOME variable"** and **"Add to PATH"**.
Close and reopen Git Bash, then:

```bash
java -version
```

Expected: the first line starts with `openjdk version "21.`, and the second
contains `Temurin-21`, for example:

```text
openjdk version "21.0.12.1" 2026-08-18 LTS
OpenJDK Runtime Environment Temurin-21.0.12.1+1 (build 21.0.12.1+1-LTS)
```

### 1.2 Git

Install the **Standalone Installer**. During install, keep the recommended PATH
option and set the default branch name to **`main`**. Then:

```bash
git --version
```

Expected: `git version 2.` followed by any number (e.g. `git version 2.56.0.windows.2`).

Set your identity once, otherwise your commits are signed "Your Name":

```bash
git config --global user.name  "First Last"
git config --global user.email "you@example.com"
git config --global init.defaultBranch main
```

### 1.3 Docker Desktop

Install it, **start it**, and wait until it says *Engine running*.
On Windows, accept the WSL 2 backend when asked. Then:

```bash
docker --version
docker compose version
```

Expected: `Docker version ...` then `Docker Compose version v2.` or later
(e.g. `Docker version 29.8.2` and `Docker Compose version v5.5.1`).
`docker compose` is written **with a space**: the old `docker-compose` is not used.

### 1.4 Node.js (frontend only)

Needed only to run the Angular frontend. Angular 21 requires Node.js
`^20.19`, `^22.12` or `>=24`: take the **24 LTS** installer. Then:

```bash
node -v
npm -v
```

Expected: `v24.` followed by any number, then an npm version (`10.` or later).

---

## 2. Get the project

```bash
git clone https://github.com/fatii-elb/aquaops.git
cd aquaops
```

The repository holds one folder per backend service in `services/`, the Angular
application in `frontend/`, the Kafka event contracts in `contracts/`, the local
infrastructure in `deploy/` and the team documentation in `docs/`.
Read [`docs/conventions.md`](conventions.md) before writing code: it fixes the
layout, the ports, the API path prefixes and the environment variables.

---

## 3. Start the infrastructure

```bash
cd deploy
docker compose up -d
docker compose ps
```

This starts PostgreSQL, Kafka and Keycloak, creates the service databases listed
in `deploy/postgres-init/01-bases.sql` and imports the `aquaops` realm. Wait until
`docker compose ps` shows PostgreSQL as `(healthy)`.

> **Databases: 3 created today, 4 needed.** On `main`, the init script creates
> `aquaops_assets`, `aquaops_incidents` and `aquaops_telemetry`. notifications-service
> also needs `aquaops_notifications`: the fix that creates it is on the branch
> `feature/TECH-deploy-compose`, **not merged yet**. Until then, notifications-service
> cannot start with this setup (see [§6](#6-known-problems)). Check what your
> PostgreSQL really holds:
>
> ```bash
> docker compose exec -T postgres psql -U aquaops -l | grep aquaops
> ```

**No `.env` file is needed**: defaults match those of the services.
Commands to stop, restart or reset, the checks, and the `.env` rules are all in
[`deploy/README.md`](../deploy/README.md).

---

## 4. Run a Java service

From the repository root, in a **new Git Bash window per service**:

```bash
cd services/assets-service
./mvnw spring-boot:run
```

The service is ready when the log shows `Started ...Application`.
Stop it with **Ctrl+C** (see [§6](#6-known-problems) if the port stays busy).

> **Keep your prompt: run in the background.** In Git Bash, `./mvnw spring-boot:run`
> takes over the window, and Ctrl+C stops the service. To keep a prompt, start it
> in the background and send its log to a file:
>
> ```bash
> ./mvnw spring-boot:run > ~/assets.log 2>&1 &
> tail -f ~/assets.log    # follow the log; Ctrl+C here stops tail, not the service
> ```
>
> Useful to start several services and run `curl` commands in the same window.
> Use one log file per service (`~/gateway.log`, `~/simulator.log`, …). A
> background service ignores Ctrl+C: stop it with `kill %1` from the same window
> (`jobs` lists the numbers). If the port is still busy afterwards, the Java child
> process survived: stop it by its PID, see [§6](#6-known-problems).

| Service | Folder | Port (variable, default) | Needs | Owner |
| --- | --- | --- | --- | --- |
| Gateway | `services/gateway` | `GATEWAY_PORT`, 8080 | The services it routes to | E5 |
| Telemetry | `services/telemetry-service` | `TELEMETRY_PORT`, 8081 | PostgreSQL, Kafka | E1 |
| Assets | `services/assets-service` | `ASSETS_PORT`, 8082 | PostgreSQL, Keycloak | E2 |
| Detection | `services/detection-service` | `DETECTION_PORT`, 8083 | Kafka | E3 |
| Incidents | `services/incidents-service` | `INCIDENTS_PORT`, 8084 | PostgreSQL, Kafka | E4 |
| Notifications | `services/notifications-service` | `NOTIFICATIONS_PORT`, 8085 | PostgreSQL, Kafka | E6 |
| Simulator | `services/simulator` | `SIMULATOR_PORT`, 8086 | — | E1 |

### Check that a service answers

Each service exposes `/actuator/health`. For example, for assets:

```bash
curl -s http://localhost:${ASSETS_PORT:-8082}/actuator/health
```

Expected: `{"status":"UP"}`. `"DOWN"` usually means the infrastructure it needs
(see the *Needs* column) is not running. Replace `ASSETS_PORT` and `8082` with the
variable and port of the service you are checking.

### The gateway: single entry point

The gateway listens on `GATEWAY_PORT` (8080) and is **the single entry point** of
the API: clients call `http://localhost:8080/api/...` and the gateway forwards
each request to the right service by the start of its path. It has 6 routes:

| Path | Forwarded to (variable, default) |
| --- | --- |
| `/api/telemetry/**` | `TELEMETRY_URL`, `http://localhost:8081` |
| `/api/assets/**` | `ASSETS_URL`, `http://localhost:8082` |
| `/api/detection/**` | `DETECTION_URL`, `http://localhost:8083` |
| `/api/incidents/**` | `INCIDENTS_URL`, `http://localhost:8084` |
| `/api/notifications/**` | `NOTIFICATIONS_URL`, `http://localhost:8085` |
| `/api/simulator/**` | `SIMULATOR_URL`, `http://localhost:8086` |

Start the gateway like any other service, plus the services you want to reach
through it. Example with the simulator:

```bash
curl -s http://localhost:${GATEWAY_PORT:-8080}/api/simulator/scenarios
```

Expected: the same JSON as when calling the simulator directly on 8086.

Incidents through the gateway (incidents-service, PostgreSQL and Kafka must run):

```bash
curl -s http://localhost:${GATEWAY_PORT:-8080}/api/incidents
```

Expected: a JSON list, `[]` while no incident exists. incidents-service exposes
`GET /api/incidents` (optional `status` and `zoneId` filters), `GET /api/incidents/{id}`,
`POST /api/incidents` and `PUT /api/incidents/{id}`.

> **Detection has no HTTP endpoint yet.** detection-service only reads and writes
> Kafka topics, so `/api/detection/...` returns `404` even when it runs, both
> through the gateway and directly on 8083. That is expected, not a gateway bug.

#### When a call through the gateway fails

Call the same path **directly** on the service's port, then compare:

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:${GATEWAY_PORT:-8080}/api/incidents
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:${INCIDENTS_PORT:-8084}/api/incidents
```

| Gateway | Direct | Meaning |
| --- | --- | --- |
| Error (`5xx`) | No answer (`000`, connection refused) | The service is **not started**, or listens on another port. Start it and check its `/actuator/health`. In our tests the gateway answered `500` here, but do not rely on that exact code |
| Same code as direct, e.g. `404` | Same code | The route is correct: the request reached the service. The problem is in the service (wrong path, no endpoint) |
| Error | Works | The gateway forwards to the wrong address: check the `*_URL` variable of that route |

Path prefixes and ports are fixed in [`docs/conventions.md`](conventions.md),
sections 4 and 5.

### Other useful commands

From a service folder:

```bash
./mvnw test             # run the tests (see docs/testing.md)
./mvnw clean package    # build the .jar
```

### Run the frontend

```bash
cd frontend
npm ci
npm start
```

Then open http://localhost:4200. API calls are forwarded by
`frontend/proxy.conf.json`, which for now sends them **directly to the services**,
not through the gateway. Details: [`frontend/README.md`](../frontend/README.md).

---

## 5. Create the test users in Keycloak

The realm export in `docs/keycloak/` contains the roles but **no user**: create
them by hand after the first `docker compose up -d`.

The project has **4 roles**:

| Role | Suggested test user |
| --- | --- |
| `administrateur` | `test-administrateur` |
| `superviseur` | `test-superviseur` |
| `operateur` | `test-operateur` |
| `technicien` | `test-technicien` |

For each user:

1. Open http://localhost:8180/admin and log in with the values of
   `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` (default `admin` / `admin`).
2. Top left, switch the realm from **master** to **aquaops**.
3. **Users > Add user**: fill *Username*, *Email*, *First name* and *Last name*
   (otherwise Keycloak asks for them at the first login), then **Create**.
4. **Credentials > Set password**: choose a password and turn **Temporary** off,
   otherwise it must be changed at the first login.
5. **Role mapping > Assign role**: choose **Filter by realm roles**, tick the
   user's role, then **Assign**.

> These users are **lost after `docker compose down`**. Use `stop`/`start` for
> daily work: see "Au quotidien" in [`deploy/README.md`](../deploy/README.md).

---

## 6. Known problems

| Problem | Why | Fix |
| --- | --- | --- |
| `java: command not found` | JDK not installed, or not in PATH | Reinstall with "Add to PATH", reopen Git Bash |
| `java -version` shows 17 or older | Another JDK is first in PATH | Fix `JAVA_HOME` and the PATH order |
| `./mvnw: Permission denied` | The file lost its executable flag | `chmod +x mvnw` |
| Git asks for a password and fails | GitHub rejects passwords | Log in via the browser pop-up, or use a Personal Access Token |
| `Cannot connect to the Docker daemon`, or on Windows `error during connect ... dockerDesktopLinuxEngine` | Docker Desktop is not started | Start Docker Desktop, wait for *Engine running*, run the command again |
| `Port 8080 was already in use` | Another program uses 8080 (Keycloak no longer does: it runs on 8180) | Stop that program (see below), or start the gateway on another port with `GATEWAY_PORT` |
| A service is `DOWN`, or will not start. The **last** error shown by Spring is about Hibernate: `Unable to determine Dialect without JDBC metadata`, with no word about Docker or PostgreSQL | The dialect error is only a **consequence**: Hibernate could not open a connection to PostgreSQL, so it could not read the database metadata. The real cause, e.g. `Connection to localhost:5432 refused`, is logged as a `WARN` a few lines **higher** | Scroll **up** in the log to the first connection error instead of reading only the last message. Then `cd deploy && docker compose ps`, and `docker compose up -d` if PostgreSQL is not `(healthy)` |
| notifications-service fails with `database "aquaops_notifications" does not exist` | On `main`, `deploy/postgres-init/01-bases.sql` does not create this database yet. The fix is on `feature/TECH-deploy-compose`, not merged. Once it is, a volume created **before** the fix still lacks it: the init script only runs on an empty volume | Before the merge: nothing to do on your side, run the other services. After the merge: `cd deploy && docker compose down -v && docker compose up -d` (erases local data, see the warning in [`deploy/README.md`](../deploy/README.md)) |
| A call through the gateway returns an error (`5xx`, often `500`) | The target service is not started, or the route's `*_URL` points to the wrong address | Call the same path directly on the service's port and compare, see [When a call through the gateway fails](#when-a-call-through-the-gateway-fails) |
| `/api/detection/...` returns `404`, through the gateway and directly | detection-service has no HTTP endpoint yet: it only uses Kafka | Expected for now. The route itself is correct |
| `password authentication failed for user "aquaops"` | A `.env` changed the database password on one side only | Read "Faut-il un fichier .env ?" in [`deploy/README.md`](../deploy/README.md): no `.env` is needed locally, and Compose reads `deploy/.env`, not the one at the root |
| Service looks stopped (window closed, Ctrl+C) but its port still answers; restarting fails with `Port 80xx was already in use` | `./mvnw spring-boot:run` starts a child Java process. Closing the shell or stopping mvnw does not always end it, especially on Windows | Stop the Java process holding the port, see below |

### Stopping the process that holds a port (Windows)

Find the PID listening on the port (last column), then stop it. Replace `8086` with
the port shown in the error:

```bash
netstat -ano | findstr :8086
taskkill //PID <pid> //F
```

In Git Bash, `taskkill` needs **`//PID`** and **`//F`**: with a single `/`, Git Bash
turns them into file paths. In cmd or PowerShell, write `taskkill /PID <pid> /F`.

You can also read the PID in the `Started ...Application` line of the service log:
`INFO 18512 --- [simulator] ... Started SimulatorApplication` → PID `18512`.

### If a tool will not install on your machine

Use the one on the team's **shared VM**, and tell E5 so it goes into this guide.
