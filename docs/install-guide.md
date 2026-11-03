# AquaOps — Development environment installation guide

> **Version 0 — proposal, to complete at sprint planning (12 Oct 2026).**
> Owner: E5 (TECH-2). Tested on a second machine by E6 on 19 Oct.
> Report anything that does not work to E5 so v1 can fix it.

---

## 1. Versions — everyone uses the same

| Tool | Version | Where to get it |
| --- | --- | --- |
| Java (JDK) | **21 LTS** | https://adoptium.net (Eclipse Temurin) |
| Maven | **none to install** — each service ships `mvnw` | — |
| Git | latest | https://git-scm.com/download/win |
| Node.js | LTS — *to confirm with E4* | https://nodejs.org |
| PostgreSQL | *to confirm with E2* | https://www.postgresql.org/download/ |
| Kafka | *to confirm with E1* | https://kafka.apache.org/downloads |
| Keycloak | *to confirm with E2* | https://www.keycloak.org/downloads |
| IDE | IntelliJ IDEA Community or VS Code | free |

---

## 2. Install, step by step

### 2.1 Java 21
1. Download the **JDK 21 (LTS)** installer for your system from Adoptium.
2. During install, tick **"Set JAVA_HOME variable"** and **"Add to PATH"**.
3. Close and reopen your terminal, then check:
```bash
   java -version
```
   You must see `21.x`. If you see 8, 11 or 17, another JDK is ahead in your PATH.

### 2.2 Git
1. Install from https://git-scm.com/download/win — choose the **Standalone Installer**, not Portable.
2. During install: default branch name → **`main`**; PATH option → the **recommended middle choice**.
3. Then configure your identity once:
```bash
   git config --global user.name  "Your Name"
   git config --global user.email "your@email.com"
   git config --global init.defaultBranch main
```

> **Windows users:** use **Git Bash** for every command in this guide, not PowerShell.
> All commands are then identical on Windows, macOS and Linux.

### 2.3 Node.js and Angular
*To complete — E4 confirms the Node version and the Angular CLI install command.*

### 2.4 PostgreSQL
*To complete — E2 provides the version, the install steps and how to create the databases.*

### 2.5 Kafka
*To complete — E1 provides the version, the start procedure and how the 3 topics are created.*

> **Note for Windows:** Kafka is awkward to run natively on Windows.
> Options to evaluate: WSL2, Docker (optional per the spec), or the team's shared VM.

### 2.6 Keycloak
*To complete — E2 provides the version, the realm import and the test users.*

> Keycloak must run on **port 8180**, not its default 8080, which clashes with the gateway.

---

## 3. Get the project

```bash
git clone https://github.com/fatii-elb/aquaops.git
cd aquaops
```

Read `docs/conventions.md` **before** generating your service skeleton:
it fixes the Java version, the group id, your port and your API path prefix.

---

## 4. Check your installation

| Command | Expected |
| --- | --- |
| `java -version` | 21.x |
| `git --version` | any recent version |
| `node -v` | *to confirm* |
| `psql --version` | *to confirm* |

---

## 5. Run a service

From inside a service folder:

```bash
./mvnw spring-boot:run     # start it      (Windows cmd: mvnw.cmd spring-boot:run)
./mvnw test                # run the tests
./mvnw clean package       # build the .jar
```

Configuration comes from environment variables with local defaults, so **nothing
to configure** to start locally. To override a value:

```bash
export ASSETS_PORT=9000 && ./mvnw spring-boot:run
```

Or set it in your IDE's run configuration. See `docs/conventions.md` section 6.

---

## 6. If a tool will not install on your machine

Per the specification: use the one on the team's **shared VM** instead.
Tell E5 so it goes into this guide.

---

## 7. Common problems

| Problem | Why | Fix |
| --- | --- | --- |
| `java: command not found` | Not installed, or not in PATH | Reinstall with "Add to PATH", reopen the terminal |
| `java -version` shows 17 | Another JDK is first in PATH | Fix `JAVA_HOME` and the PATH order |
| `./mvnw: Permission denied` | Lost its executable flag | `chmod +x mvnw` |
| `Port 8080 already in use` | Keycloak or another service | Move Keycloak to 8180, or set the service's port variable |
| `Connection refused` to the database | PostgreSQL not started | Start the PostgreSQL service |
| Git asks for a password and fails | GitHub rejects passwords | Log in via the browser pop-up, or use a Personal Access Token |
| Service looks stopped (window closed, Ctrl+C) but its port still answers; restarting fails with `Port 80xx was already in use` | `./mvnw spring-boot:run` starts a child Java process. Closing the shell or stopping mvnw does not always end it, especially on Windows | Find and stop the Java process holding the port, see below |

**Stopping a leftover Java process (Windows)**

In cmd or PowerShell, find the PID listening on the port (last column), then stop it:

```bat
netstat -ano | findstr :8086
taskkill /PID <pid> /F
```

In Git Bash, write `taskkill //PID <pid> //F` (Git Bash turns a single `/PID`
into a file path). You can also read the PID in the `Started ...Application`
line of the startup log, e.g. `INFO 18512 --- [simulator] ... Started SimulatorApplication`
→ PID `18512`.
