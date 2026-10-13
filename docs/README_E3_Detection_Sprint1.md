# AquaOps · Détection des anomalies (E3) : travail du Sprint 1

Projet académique DevOps / DevSecOps · plateforme de surveillance d'un réseau de distribution d'eau **simulé**.

| | |
|---|---|
| Sprint | 1 · Cadrage et fondations · du 12 au 23 octobre 2026 |
| Domaine | E3 · Détection des anomalies |
| Dépôt | https://github.com/fatii-elb/aquaops |
| Dernière mise à jour | 13 octobre 2026 |

Ce document décrit ce qui a été réalisé pour le domaine **détection** au Sprint 1, avec les procédures pour le reproduire. Les parties des autres membres de l'équipe (actifs et Keycloak, incidents, passerelle, notifications) ne sont pas détaillées ici.

---

## Sommaire

1. [Livrables](#1-livrables)
2. [Environnement requis](#2-environnement-requis)
3. [TECH-1 · Squelette du service de détection](#3-tech-1--squelette-du-service-de-détection)
4. [TECH-4 · Topics Kafka de la détection](#4-tech-4--topics-kafka-de-la-détection)
5. [SCRUM-60 · Contrat de l'événement `anomaly-detected`](#5-scrum-60--contrat-de-lévénement-anomaly-detected)
6. [SCRUM-23 · Capteurs et pompes](#6-scrum-23--capteurs-et-pompes)
7. [TECH-7 · Règles Git et Definition of Done](#7-tech-7--règles-git-et-definition-of-done)
8. [Vérifications et tests](#8-vérifications-et-tests)
9. [Dépannage](#9-dépannage)
10. [Points à valider](#10-points-à-valider)
11. [Prochaines étapes](#11-prochaines-étapes)
12. [Annexe · Tâches de E1 prises en charge](#12-annexe--tâches-de-e1-prises-en-charge)

---

## 1. Livrables

| Carte | Livrable | Référence | Statut |
|---|---|---|---|
| TECH-1 | Squelette `detection-service` (port 8083) | PR #6 | En revue |
| TECH-4 | Topics `anomaly-detected` et `incident-events` | Kafka local (section 4) | Fait et testé |
| SCRUM-60 | Contrat `anomaly-detected` v1 (brouillon) | Branche `feature/SCRUM-60-contract-anomaly-detected` | En revue |
| SCRUM-23 | CRUD des capteurs (`/api/assets/sensors`) | PR #13 | En revue |
| SCRUM-23 | CRUD des pompes (`/api/assets/pumps`) | Branche `feature/SCRUM-23-pumps` | PR à référencer |
| TECH-7 | Règles Git et Definition of Done (`CONTRIBUTING.md`) | PR #8 | En revue |

Statuts relevés le 13 octobre 2026 : l'état à jour se lit sur GitHub. Une carte est « Terminée » seulement après relecture par une autre étudiante.

---

## 2. Environnement requis

| Outil | Version | Vérification |
|---|---|---|
| Git | 2.53 ou plus | `git --version` |
| Java (JDK) | **21** (Temurin) | `java -version` |
| Python | 3.12 | `python --version` |
| Docker | Pour Kafka | `docker --version` |
| PostgreSQL | 16.4, pour les tests du service des actifs | `pg_isready` |

Les commandes se tapent dans **Git Bash** sous Windows.

**Java 21 obligatoire.** Java 17 ne convient pas. Après l'installation de Temurin 21 (adoptium.net, avec les options *Add to PATH* et *Set JAVA_HOME*), rouvrir Git Bash. Si la version 17 s'affiche encore, rendre Java 21 permanent (remplacer le nom du dossier par celui du poste) :

```bash
ls "/c/Program Files/Eclipse Adoptium/"
echo 'export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot"' >> ~/.bashrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.bashrc
```

Clonage du dépôt :

```bash
mkdir -p ~/dev
cd ~/dev
git clone https://github.com/fatii-elb/aquaops.git
cd aquaops
git status
```

Résultat attendu : `On branch main` et `nothing to commit, working tree clean`.

---

## 3. TECH-1 · Squelette du service de détection

### 3.1 Principe

Un **squelette** est un projet Spring Boot qui démarre et répond sur `/actuator/health`, sans code métier. Il prouve que l'environnement fonctionne et fixe la base du service. La logique de détection commence au Sprint 2.

| Élément | Valeur |
|---|---|
| Dossier | `services/detection-service/` |
| Package | `ma.aquaops.detection` |
| Spring Boot | 4.1.1 |
| Dépendances | `web`, `kafka`, `validation`, `actuator` |
| Port | 8083, variable `DETECTION_PORT` |
| Base de données | aucune : le service lit et écrit sur Kafka |
| Branche | `feature/TECH-1-skeleton-detection` |

### 3.2 Génération

```bash
cd ~/dev/aquaops
git checkout main && git pull
git checkout -b feature/TECH-1-skeleton-detection
cd services/detection-service

curl -L https://start.spring.io/starter.zip -d type=maven-project -d language=java -d javaVersion=21 -d groupId=ma.aquaops -d artifactId=detection-service -d name=detection-service -d packageName=ma.aquaops.detection -d dependencies=web,kafka,validation,actuator -o projet.zip

python -c "import zipfile; zipfile.ZipFile('projet.zip').extractall('.')"
rm projet.zip
ls
grep -A4 "<parent>" pom.xml
```

Contrôles : le dossier contient `mvnw`, `mvnw.cmd`, `pom.xml` et `src/`, et le `grep` affiche la version `4.1.1`.

### 3.3 Configuration

Aucune adresse n'est écrite en dur : le service lit des variables d'environnement avec une valeur par défaut valable en local.

```bash
rm -f src/main/resources/application.properties
cat > src/main/resources/application.yml <<'EOF'
server:
  port: ${DETECTION_PORT:8083}
spring:
  application:
    name: detection-service
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
management:
  endpoints:
    web:
      exposure:
        include: health
EOF
```

`${DETECTION_PORT:8083}` se lit : « utiliser la variable `DETECTION_PORT` si elle existe, sinon 8083 ».

### 3.4 Démarrage et publication

```bash
./mvnw spring-boot:run
# dans un second terminal :
curl http://localhost:8083/actuator/health
```

Réponse attendue : `{"groups":["liveness","readiness"],"status":"UP"}`. Le premier lancement télécharge les dépendances (1 à 3 minutes). Arrêt : `Ctrl+C`.

```bash
cd ~/dev/aquaops
git status
git add services/detection-service
git commit -m "TECH-1: squelette du service detection"
git push -u origin feature/TECH-1-skeleton-detection
```

Avant le commit, `git status` ne doit lister aucun dossier `target/` ni fichier étranger au service.

---

## 4. TECH-4 · Topics Kafka de la détection

La détection **consomme** `sensor-readings` et **produit** `anomaly-detected`. Les trois topics du projet ont été créés sur Kafka, dont les deux de ce domaine.

| Topic | Producteur | Consommateurs |
|---|---|---|
| `sensor-readings` | Télémétrie | **Détection**, stockage |
| `anomaly-detected` | **Détection** | Incidents, notifications |
| `incident-events` | Incidents et maintenance | Notifications, audit |

Kafka 4.3.1 tourne dans un conteneur Docker, en mode KRaft (sans ZooKeeper), sur le port 9092 :

```bash
docker run -d --name aquaops-kafka -p 9092:9092 apache/kafka:4.3.1
docker start aquaops-kafka          # après un redémarrage du poste
```

La version se vérifie avec `docker ps` (colonne `IMAGE`).

**Création des topics.** Sous Git Bash, le préfixe `MSYS_NO_PATHCONV=1` est obligatoire : sans lui, Git Bash convertit `/opt/kafka/...` en chemin Windows et la commande échoue.

```bash
MSYS_NO_PATHCONV=1 docker exec aquaops-kafka /opt/kafka/bin/kafka-topics.sh --create --topic anomaly-detected --bootstrap-server localhost:9092
MSYS_NO_PATHCONV=1 docker exec aquaops-kafka /opt/kafka/bin/kafka-topics.sh --create --topic incident-events --bootstrap-server localhost:9092
MSYS_NO_PATHCONV=1 docker exec aquaops-kafka /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092
```

Résultat obtenu : `anomaly-detected`, `incident-events` et `sensor-readings`.

**Test d'envoi.** Un message saisi dans le producteur est lu par le consommateur (résultat obtenu) :

```bash
# Terminal 1 : consommateur
MSYS_NO_PATHCONV=1 docker exec -it aquaops-kafka /opt/kafka/bin/kafka-console-consumer.sh --topic sensor-readings --from-beginning --bootstrap-server localhost:9092
# Terminal 2 : producteur
MSYS_NO_PATHCONV=1 docker exec -it aquaops-kafka /opt/kafka/bin/kafka-console-producer.sh --topic sensor-readings --bootstrap-server localhost:9092
```

Si le conteneur est supprimé, les topics disparaissent et doivent être recréés.

---

## 5. SCRUM-60 · Contrat de l'événement `anomaly-detected`

Fichier : `contracts/events/anomaly-detected.v1.json` (JSON Schema, **brouillon v1**). Il sera ajusté après publication de la règle de versionnement des événements (E6, prévue le 16 octobre).

Consommateurs : le service des incidents (E4), qui crée l'incident (FR-15), et les notifications (E6).

| Champ | Type | Obligatoire | Description |
|---|---|---|---|
| `eventVersion` | `"1"` | Oui | Version du contrat |
| `anomalyId` | chaîne (UUID) | Oui | Identifiant unique de l'anomalie |
| `type` | énumération | Oui | `LEAK_SUSPECTED` (FR-09), `SENSOR_SILENT` (FR-10), `PUMP_FAULT` (FR-11), `LOW_LEVEL` (FR-12) |
| `zoneId` | entier | Oui | Zone concernée |
| `sensorId` | entier | Non | Capteur à l'origine, si applicable |
| `dedupKey` | chaîne | Non | Clé de déduplication d'une anomalie persistante (FR-14), par exemple `LEAK_SUSPECTED:3:12` |
| `severity` | énumération | Non | `LOW`, `MEDIUM`, `HIGH`, `CRITICAL` |
| `measuredAt` | date-heure UTC | Oui | Mesure ayant déclenché l'anomalie |
| `detectedAt` | date-heure UTC | Oui | Instant de la détection |
| `details` | objet | Non | Valeurs et seuils franchis |

Exemple :

```json
{
  "eventVersion": "1",
  "anomalyId": "7b0f2a3e-4c1d-4e6a-9d52-1f3c8a9b0e11",
  "type": "LEAK_SUSPECTED",
  "zoneId": 3,
  "sensorId": 12,
  "dedupKey": "LEAK_SUSPECTED:3:12",
  "severity": "CRITICAL",
  "measuredAt": "2026-10-12T21:30:00Z",
  "detectedAt": "2026-10-12T21:30:04Z",
  "details": { "pressureBar": 1.1, "flowM3h": 48.0 }
}
```

**Choix de conception**
- `measuredAt` et `detectedAt` permettent de mesurer la latence de bout en bout (exigence NFR-02 : 10 secondes pour 95 % des cas).
- `dedupKey` prépare la déduplication : 100 mesures anormales doivent produire un seul incident (FR-14).
- `severity` est facultative : le calcul de la priorité d'un incident reste à valider avec l'encadrant.

---

## 6. SCRUM-23 · Capteurs et pompes

Entités Capteur et Pompe ajoutées au service `assets-service` (domaine de E2), dans le style des entités existantes (`Zone`, `Pipe`, `Tank`). Elles sont développées sur des branches issues de `feature/SCRUM-45-role-protection`.

### 6.1 Modèle

**Capteur** (table `sensors`)

| Champ | Type | Contrainte |
|---|---|---|
| `id` | Long | Généré |
| `name` | String | Obligatoire |
| `type` | `SensorType` | Obligatoire : `PRESSURE`, `FLOW`, `LEVEL`, `PUMP_STATE` |
| `zoneId` | Long | Obligatoire, doit référencer une zone existante |

**Pompe** (table `pumps`)

| Champ | Type | Contrainte |
|---|---|---|
| `id` | Long | Généré |
| `name` | String | Obligatoire |
| `zoneId` | Long | Obligatoire, doit référencer une zone existante |

Les types de capteur reprennent les mesures du simulateur (pression, débit, niveau, état de pompe) : ce sont les mêmes valeurs que dans le contrat `sensor-readings`.

### 6.2 API

| Méthode | Chemin | Effet | Codes |
|---|---|---|---|
| GET | `/api/assets/sensors` | Liste | 200 |
| GET | `/api/assets/sensors/{id}` | Un capteur | 200, 404 |
| POST | `/api/assets/sensors` | Créer | 201, 400 |
| PUT | `/api/assets/sensors/{id}` | Modifier | 200, 400, 404 |
| DELETE | `/api/assets/sensors/{id}` | Supprimer | 204, 404 |

Les chemins `/api/assets/pumps` suivent le même modèle. Une réponse 400 est renvoyée si le nom est vide, si la zone est absente ou inexistante, ou si le type de capteur est invalide.

```json
POST /api/assets/sensors
{ "name": "P1", "type": "PRESSURE", "zoneId": 3 }
```

### 6.3 Sécurité

`SecurityConfig` (E2) s'applique à `/api/assets/**` et couvre les nouveaux chemins sans modification.

| Action | Rôles autorisés |
|---|---|
| Lecture (GET) | opérateur, superviseur, technicien, administrateur |
| Écriture (POST, PUT, DELETE) | administrateur, superviseur |
| Autre chemin | refusé |

### 6.4 Fichiers ajoutés

| Capteur | Pompe |
|---|---|
| `model/Sensor.java`, `model/SensorType.java` | `model/Pump.java` |
| `repository/SensorRepository.java` | `repository/PumpRepository.java` |
| `web/SensorController.java` | `web/PumpController.java` |
| `SensorCrudTests.java` | `PumpCrudTests.java` |

### 6.5 Pull requests

Les branches de E2 ne sont pas encore fusionnées dans `main`. Une pull request qui cible `main` affiche donc aussi leurs commits (27 fichiers sur la PR #13 au lieu de 5). Deux options :
- cibler `feature/SCRUM-45-role-protection`, pour ne montrer que le travail propre ;
- cibler `main` en précisant dans la description qu'elle ne doit pas être fusionnée avant celles de E2.

### 6.6 Reste à faire (SCRUM-24, E2)

Le rattachement d'un capteur à un **actif** (conduite, réservoir ou pompe) n'est pas implémenté. Les champs envisagés sont `assetType` et `assetId`, à valider avec E2.

---

## 7. TECH-7 · Règles Git et Definition of Done

Le fichier `CONTRIBUTING.md` fixe les règles de travail de l'équipe :

- Personne n'écrit directement sur `main` : une branche par carte, une pull request, une relecture par une autre étudiante. Personne ne fusionne sa propre PR.
- Nom de branche : `feature/<CARTE>-<nom-court>`.
- Message de commit : `<CARTE>: ce qui a été fait`.
- Pull requests courtes (moins de 300 lignes si possible).
- Aucun secret dans le dépôt ; aucune adresse en dur.

**Definition of Done d'une story :** critères d'acceptation vérifiés, code fonctionnel et tests passés, relecture par une autre étudiante, validation du Product Owner.

Le document signale aussi trois points à trancher : le format du message de commit, la numérotation des cartes Jira (`SCRUM-xx` contre `AQUA-x` dans le guide de démarrage), et la protection de `main` sur GitHub.

---

## 8. Vérifications et tests

| Vérification | Résultat |
|---|---|
| `curl http://localhost:8083/actuator/health` | `UP` |
| Topics Kafka | 3 topics listés |
| Message de test Kafka | reçu par le consommateur |
| `./mvnw test` sur la branche des capteurs | `Tests run: 17, Failures: 0, Errors: 0` |

Les 17 tests comprennent les tests existants du service des actifs (E2) et les 5 tests `SensorCrudTests` : CRUD complet, zone inexistante, zone absente, nom vide, type invalide. Les messages `WARN` affichés pendant l'exécution proviennent des cas de refus testés volontairement.

Les tests de la pompe (`PumpCrudTests` : CRUD complet, zone inexistante, nom vide) sont écrits ; leur exécution reste à confirmer sur la branche `feature/SCRUM-23-pumps`.

Pour lancer les tests :

```bash
cd ~/dev/aquaops/services/assets-service
./mvnw test
```

Prérequis : PostgreSQL démarré et base `aquaops_assets` créée (`CREATE DATABASE aquaops_assets OWNER aquaops;`).

---

## 9. Dépannage

| Symptôme | Cause | Solution |
|---|---|---|
| `java -version` affiche 17 | Java 17 installé avant la 21 | Installer Temurin 21, rouvrir Git Bash, rendre `JAVA_HOME` permanent |
| `bash: $'\302\226...': command not found` | Caractère invisible copié avec la commande | Retaper la commande |
| `mkdir: cannot create directory` | Dossier parent absent | Utiliser `mkdir -p` |
| `docker exec` : chemin introuvable | Conversion de chemin par Git Bash | Préfixer par `MSYS_NO_PATHCONV=1` |
| `./mvnw test` : plugin `maven-surefire-plugin` introuvable | Téléchargement interrompu (réseau) | `curl -I https://repo.maven.apache.org/maven2/` pour tester la connexion, puis `./mvnw test -U` |
| `git status` liste des fichiers d'une autre branche | Fichiers non suivis (par exemple `HELP.md`) conservés d'une branche à l'autre | Ne pas les ajouter ; ajouter par chemin (`git add services/assets-service/src`) |
| Avertissements Kafka au démarrage d'un service | Kafka éteint | Normal tant que `Started` apparaît ; `docker start aquaops-kafka` |

---

## 10. Points à valider

| Sujet | Situation | Avec qui |
|---|---|---|
| Règle de versionnement des événements | Le contrat `anomaly-detected` reste en brouillon | E6 (vendredi 16 octobre) |
| Type des identifiants (`zoneId`, `sensorId`) | Entiers dans le contrat et dans l'API | E2 |
| Priorité d'un incident | `severity` facultative dans l'événement | Encadrant |
| Rattachement capteur → actif | Non implémenté (SCRUM-24) | E2 |
| Base des PR capteurs et pompes | `main` ou branche de E2 | Équipe |

---

## 11. Prochaines étapes

| Échéance | Tâche | Carte |
|---|---|---|
| Lun 19 – mar 20 octobre | Finaliser les tests de l'API capteurs et pompes ; repointer les PR sur `main` après la fusion de E2 | SCRUM-23 |
| Mar 20 – mer 21 octobre | Ajuster `anomaly-detected` à la règle de versionnement de E6, puis geler la v1 | SCRUM-60 |
| Ven 23 octobre | Revue et rétrospective du Sprint 1 | Équipe |
| Sprint 2 (27 octobre au 6 novembre) | FR-11 pompe en défaut ; jeu de données connu pour le test RC-02 ; soutien à la validation, l'historisation et les scénarios de panne | FR-11, FR-04, FR-05, FR-06 |
| Sprint 3 (9 au 20 novembre) | FR-09 fuite probable ; FR-14 déduplication ; gravité dans l'événement pour FR-15 | FR-09, FR-14, FR-15 |

---

## 12. Annexe · Tâches de E1 prises en charge

Pendant l'absence de E1, les tâches suivantes ont été réalisées à sa place. Elles sont documentées en détail dans le README complet du Sprint 1.

| Carte | Livrable | Référence |
|---|---|---|
| TECH-1 | Squelettes `simulator` (port 8086, proposition) et `telemetry-service` (port 8081) | PR #7 et #9 |
| TECH-4 | Installation de PostgreSQL 16.4 (utilisateur `aquaops`, base `aquaops_telemetry`) et de Kafka avec le topic `sensor-readings` | Postes locaux |
| TECH-3 | Variable `SIMULATOR_PORT` dans `.env.example` | Branche `feature/TECH-3-simulator-port` |
| SCRUM-60 | Contrat `sensor-readings` v1 (brouillon) | Branche `feature/SCRUM-60-contract-sensor-readings` |
