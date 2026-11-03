# deploy/ : infrastructure AquaOps en Docker Compose

Carte [TECH] déploiement · E5.

Une seule commande démarre PostgreSQL, Kafka et Keycloak. Les services Java
continuent de se lancer avec `./mvnw spring-boot:run` et trouvent l'infrastructure
sur `localhost`, avec leurs valeurs par défaut : rien à configurer.

Prérequis : Docker Desktop (ou Docker Engine avec le plugin `compose`).

## Les services

| Service | Port | Rôle |
| --- | --- | --- |
| `postgres` | 5432 | Bases `aquaops_assets`, `aquaops_telemetry`, `aquaops_incidents` |
| `kafka` | 9092 | Topics `sensor-readings`, `anomaly-detected`, `incident-events` (créés automatiquement) |
| `keycloak` | 8180 | Connexion et rôles, realm `aquaops` importé au démarrage. Console : http://localhost:8180/admin |

## Commandes

Toutes se lancent depuis le dossier `deploy/` :

```bash
cd deploy
```

| Commande | Ce qu'elle fait |
| --- | --- |
| `docker compose up -d` | Télécharge les images si besoin, crée et démarre les 3 conteneurs en arrière-plan (`-d`). |
| `docker compose ps` | Liste les conteneurs et leur état. PostgreSQL affiche `healthy` quand il accepte les connexions. |
| `docker compose logs -f` | Affiche les journaux et les suit en direct (`-f`). Ajouter un nom pour un seul service : `docker compose logs -f keycloak`. Ctrl+C pour quitter, les conteneurs continuent de tourner. |
| `docker compose down` | Arrête et supprime les conteneurs. **Les données PostgreSQL sont gardées** dans le volume `pgdata`. |
| `docker compose down -v` | Pareil, **et supprime le volume** (`-v`) : toutes les données PostgreSQL sont effacées. Au prochain `up -d`, les 3 bases sont recréées vides. |

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
docker compose ps
```

On doit voir les 3 conteneurs `aquaops-postgres`, `aquaops-kafka` et
`aquaops-keycloak` à l'état `Up`, avec `(healthy)` pour PostgreSQL.

```bash
docker compose exec -T postgres psql -U aquaops -l | grep aquaops
```

On doit voir les lignes `aquaops_assets`, `aquaops_telemetry` et `aquaops_incidents`.
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

On doit voir la liste des topics, un par ligne. **Elle est vide tant qu'aucun
service n'a utilisé Kafka** : les topics sont créés automatiquement au premier
message envoyé ou lu. Pour les créer à la main sans attendre :

```bash
for t in sensor-readings anomaly-detected incident-events; do
  MSYS_NO_PATHCONV=1 docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh \
    --create --if-not-exists --topic "$t" --partitions 1 --replication-factor 1 \
    --bootstrap-server localhost:9092
done
```

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
- **Les bases ne sont créées qu'une fois.** `postgres-init/01-bases.sql` ne
  s'exécute que si le volume `pgdata` est vide. Après une modification de ce
  script, il faut `docker compose down -v` puis `docker compose up -d`.
- **Port déjà utilisé** (`port is already allocated`) : un PostgreSQL installé
  sur la machine occupe souvent 5432. L'arrêter, ou ne pas lancer ce conteneur.
