# deploy/ : infrastructure AquaOps en Docker Compose

Carte [TECH] déploiement · E5.

Une seule commande démarre PostgreSQL, Kafka et Keycloak. Les services Java
continuent de se lancer avec `./mvnw spring-boot:run` et trouvent l'infrastructure
sur `localhost`, avec leurs valeurs par défaut : rien à configurer.

Prérequis : Docker Desktop (ou Docker Engine avec le plugin `compose`).

## Les services

| Service | Port | Rôle |
| --- | --- | --- |
| `postgres` | 5432 | Bases `aquaops_assets`, `aquaops_incidents`, `aquaops_notifications`, `aquaops_telemetry` |
| `kafka` | 9092 | Topics `sensor-readings`, `anomaly-detected`, `incident-events` (créés automatiquement au démarrage) |
| `kafka-init` | - | Crée les 3 topics au démarrage, puis s'arrête (état `Exited (0)`, c'est normal) |
| `keycloak` | 8180 | Connexion et rôles, realm `aquaops` importé au démarrage. Console : http://localhost:8180/admin |

## Commandes

Toutes se lancent depuis le dossier `deploy/` :

```bash
cd deploy
```

| Commande | Ce qu'elle fait |
| --- | --- |
| `docker compose up -d` | Télécharge les images si besoin, crée et démarre PostgreSQL, Kafka et Keycloak en arrière-plan (`-d`). Un conteneur `kafka-init` crée ensuite les 3 topics, puis s'arrête. |
| `docker compose ps` | Liste les conteneurs en cours. PostgreSQL et Kafka affichent `healthy` quand ils sont prêts. Ajouter `-a` pour voir aussi `kafka-init`, arrêté. |
| `docker compose logs -f` | Affiche les journaux et les suit en direct (`-f`). Ajouter un nom pour un seul service : `docker compose logs -f keycloak`. Ctrl+C pour quitter, les conteneurs continuent de tourner. |
| `docker compose down` | Arrête et supprime les conteneurs. **Les données PostgreSQL sont gardées** dans le volume `pgdata`. |
| `docker compose down -v` | Pareil, **et supprime le volume** (`-v`) : toutes les données PostgreSQL sont effacées. Au prochain `up -d`, les 4 bases sont recréées vides. |

> **Avertissement : correction du 3 novembre 2026.** La base `aquaops_notifications`
> manquait. Les scripts de `postgres-init/` ne s'exécutent que sur un volume vide :
> si tu as lancé le compose avant cette correction, fais `docker compose down -v`
> puis `docker compose up -d` pour que la nouvelle base soit créée. Attention,
> `down -v` efface aussi les données des autres bases.

## Au quotidien : `stop`/`start` ou `down`/`up` ?

| Commandes | Conteneurs | Données PostgreSQL | Utilisateurs Keycloak | Messages Kafka |
| --- | --- | --- | --- | --- |
| `docker compose stop` puis `docker compose start` | Arrêtés, puis redémarrés | Gardées | Gardés | Gardés |
| `docker compose down` puis `docker compose up -d` | Supprimés, puis recréés | Gardées (volume nommé `pgdata`) | **Perdus** | Perdus |

- **`stop`/`start` garde tout** : les conteneurs sont seulement mis en pause, rien n'est supprimé.
- **`down` supprime les conteneurs.** PostgreSQL ne perd rien, car ses données
  sont dans le volume nommé `pgdata`, qui existe en dehors du conteneur.
  Keycloak, lui, perd ses utilisateurs : en mode `start-dev`, sa base est stockée
  *dans* le conteneur. Au `up -d` suivant, le realm `aquaops` est réimporté
  (clients, rôles), mais les utilisateurs créés à la main ont disparu.

**Recommandation : `stop`/`start` pour le travail courant.** Réserver `down`
(et `down -v`) aux remises à zéro volontaires, par exemple après une
modification de `docker-compose.yml` ou de `postgres-init/`.

## Vérifier que tout fonctionne

Commandes testées sous Git Bash, depuis le dossier `deploy/` :

```bash
docker compose ps -a
```

On doit voir `aquaops-postgres`, `aquaops-kafka` et `aquaops-keycloak` à l'état
`Up`, avec `(healthy)` pour PostgreSQL et Kafka. Le conteneur `aquaops-kafka-init`
est à l'état `Exited (0)` : c'est normal, il a créé les topics puis s'est arrêté.

```bash
docker compose exec -T postgres psql -U aquaops -l | grep aquaops
```

On doit voir les lignes `aquaops_assets`, `aquaops_incidents`, `aquaops_notifications`
et `aquaops_telemetry`.
D'autres bases (`aquaops`, `postgres`, `template0`, `template1`) apparaissent aussi,
car leur propriétaire est le compte `aquaops` : c'est normal.

```bash
curl -s http://localhost:8180/realms/aquaops/.well-known/openid-configuration
```

On doit voir un JSON qui commence par `{"issuer":"http://localhost:8180/realms/aquaops"`.
`{"error":"Realm does not exist"}` signifie que l'import du realm a échoué
(voir `docker compose logs keycloak`).

```bash
MSYS_NO_PATHCONV=1 docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --list --bootstrap-server localhost:9092
```

On doit voir les 3 topics, un par ligne : `anomaly-detected`, `incident-events`
et `sensor-readings`. Ils sont créés automatiquement au démarrage par le conteneur
`kafka-init` : si la liste est incomplète juste après `up -d`, attendre une dizaine
de secondes et relancer la commande.

`MSYS_NO_PATHCONV=1` est nécessaire sous Git Bash : sans lui, Git Bash traduit
`/opt/kafka/...` en chemin Windows (`C:/Program Files/Git/opt/kafka/...`) et la
commande échoue dans le conteneur. Sous cmd ou PowerShell, l'enlever.

## Faut-il un fichier `.env` ?

**Non.** Le compose a des valeurs par défaut (`aquaops` / `aquaops`), identiques à
celles des `application.yml` des services. `docker compose up -d` marche tel quel.

- **Ne copie pas `.env.example` vers `deploy/.env`.** `.env.example` contient
  `DB_PASSWORD=change-me`, destiné au déploiement sur la VM. Copié ici, il fait
  démarrer PostgreSQL avec `change-me`, alors que les services Java gardent
  `aquaops` : la connexion échoue avec une erreur d'authentification.
- **Pour changer le mot de passe en local**, il faut le changer aux deux endroits :
  dans `deploy/.env` **et** dans la variable d'environnement `DB_PASSWORD` vue par
  les services Java. PostgreSQL ne lit ce mot de passe qu'à la création du volume :
  après un changement, faire `docker compose down -v` puis `docker compose up -d`.
- **Compose lit le `.env` du dossier du compose** (`deploy/.env`), pas celui de la
  racine. Pour utiliser celui de la racine : `docker compose --env-file ../.env up -d`.

## À savoir

- **Utilisateurs de test Keycloak : à créer à la main.** L'export
  `docs/keycloak/aquaops-realm.json` contient le realm, ses clients et ses rôles,
  mais **aucun utilisateur**. Après le premier `up -d`, se connecter à
  http://localhost:8180/admin (identifiant `admin`, mot de passe `admin` par défaut,
  modifiables avec `KEYCLOAK_ADMIN` et `KEYCLOAK_ADMIN_PASSWORD`), choisir le realm
  `aquaops`, puis *Users > Add user*, avec un mot de passe dans l'onglet
  *Credentials* et les rôles dans *Role mapping*.
- **Keycloak perd ses utilisateurs après un `down`** : voir « Au quotidien » plus haut.
- **Kafka perd ses messages après un `down`.** Il n'a pas de volume nommé : ses
  topics et leurs messages sont stockés dans le conteneur, que `docker compose down`
  supprime. Ce n'est pas grave en local : les 3 topics sont recréés
  automatiquement par `kafka-init` au prochain `up -d`.
- **Les bases ne sont créées qu'une fois.** `postgres-init/01-bases.sql` ne
  s'exécute que si le volume `pgdata` est vide. Après une modification de ce
  script, il faut `docker compose down -v` puis `docker compose up -d`.
- **Port déjà utilisé** (`port is already allocated`) : un PostgreSQL installé
  sur la machine occupe souvent 5432. L'arrêter, ou ne pas lancer ce conteneur.