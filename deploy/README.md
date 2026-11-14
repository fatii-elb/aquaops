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

- **Utilisateurs de test Keycloak.** L'export `docs/keycloak/aquaops-realm.json` contient le
  realm, ses clients, ses rôles et 4 utilisateurs de test (`admin1`, `superviseur1`, `operateur1`,
  `technicien1`, mots de passe temporaires : voir `docs/keycloak/README.md`). Console :
  http://localhost:8180/admin (identifiant `admin`, mot de passe `admin` par défaut,
  modifiables avec `KEYCLOAK_ADMIN` et `KEYCLOAK_ADMIN_PASSWORD`). Un utilisateur créé à la main
  dans la console est perdu au prochain `down` : l'ajouter au fichier du realm.
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

---

## Environnement de test sur la VM (SCRUM-55 / NFR-08)

Carte SCRUM-55 · E5. Cette section prépare le déploiement automatique sur la VM
de test. Elle s'adresse à l'équipe **et à l'administrateur de la VM**.

> **État actuel — le déploiement automatique n'est pas encore en service :**
> - un runner auto-hébergé (`aquaops-vm-test`, label `aquaops-test`, compte
>   `aquaops-runner` sans `sudo` ni groupe `docker`) est enregistré sur la VM ;
> - le job `deploy-test` existe dans `.github/workflows/publish.yml` mais il est
>   **désactivé** (`DEPLOY_TEST: 'off'`) : il n'a jamais été exécuté. Il passe
>   par le wrapper `aquaops-deploy` (§ 7), à installer par l'administrateur ;
> - la **publication GHCR est désactivée** (`GHCR_PUBLISH: 'off'` dans
>   `.github/workflows/publish.yml`) et Trivy est en **mode audit**
>   (`TRIVY_EXIT_CODE: '0'`, décision de E4 en attente) ;
> - un premier déploiement **manuel** a été réalisé par E5 sur la VM Ubuntu de
>   test (services sains, smoke test de la gateway passé) ; il ne remplace pas
>   la preuve du déploiement automatique (NFR-08).
>
> **Eureka : intégration partielle, à coordonner dans l'équipe.**
> - assets-service contient le **client** Eureka
>   (`spring-cloud-starter-netflix-eureka-client`), **désactivé par défaut**
>   (`EUREKA_ENABLED`, défaut `false` ; adresse dans `EUREKA_URL`) : sans
>   serveur, le service démarre normalement ;
> - le dépôt ne contient **aucun serveur Eureka** (le `pom.xml` d'assets-service
>   l'attribue à E5) ; seuls les manifestes `deploy/k8s/` supposent un service
>   `eureka`, ils ne sont pas utilisés par cet environnement de test ;
> - `deploy/test/compose.yml` n'active pas Eureka : la gateway route vers des
>   adresses fixes (`TELEMETRY_URL`…) résolues par le réseau Docker, Eureka n'est
>   donc **pas nécessaire** au déploiement de test. L'activer demandera un
>   service et une image de plus, `EUREKA_ENABLED`/`EUREKA_URL`, et une mise à
>   jour du compose, de `deploy-test.sh` et de `wait-healthy.sh`.

### Ce qui est déjà prêt (dans le dépôt)

| Fichier | Rôle |
| --- | --- |
| `deploy/test/compose.yml` | Les 7 services Java, images `ghcr.io/fatii-elb/aquaops/<service>:<SHA>`, sans build. Seule la gateway est publiée sur l'hôte. |
| `deploy/test/compose.infra.yml` | Optionnel : PostgreSQL, Kafka et Keycloak dans Docker sur la même VM. |
| `deploy/test/.env.example` | Modèle du fichier de configuration (valeurs factices `change-me`). |
| `deploy/scripts/deploy-test.sh` | Déploie **un commit exact** : vérifications, pull des images, `up -d`, santé, smoke test, durée. |
| `deploy/scripts/wait-healthy.sh` | Attend que les 7 services soient `healthy` **et** tournent avec l'image du bon SHA. |
| `deploy/vm/aquaops-deploy` | Wrapper privilégié installé par l'administrateur : le runner lui demande un SHA, lui seul utilise Docker (§ 7). |
| `deploy/vm/aquaops-deploy.sudoers` | La **seule** règle `sudo` du runner : lancer ce wrapper, sous `aquaops-deploy`, avec `<SHA> <heure du push>`. |
| `deploy/vm/tests/test-aquaops-deploy.sh` | Tests du wrapper, sans root, sans Docker ni réseau (doublures de `docker`, `curl`, `git` et `stat`). |

### 1. Exigences de la VM

| Domaine | Exigence | Pourquoi |
| --- | --- | --- |
| Système | Linux **x86_64** (par exemple Ubuntu 22.04 ou 24.04 LTS) | Les images sont construites uniquement pour `linux/amd64`. Le script utilise `bash`, `curl`, `flock` et `stat -c` (outils GNU/Linux). |
| CPU | **4 vCPU** recommandés (estimation) | 9 JVM (7 services, Kafka, Keycloak) et PostgreSQL. |
| Mémoire | **8 Go minimum, 12 à 16 Go recommandés** (estimation) | 7 services × 768 Mo (`JAVA_MEM_LIMIT`) ≈ 5,4 Go, plus Kafka, Keycloak et PostgreSQL si l'infrastructure tourne sur la VM. |
| Disque | **40 Go libres minimum** pour Docker (estimation) | Images d'infrastructure et 7 images applicatives. Chaque nouveau commit déployé ajoute des couches. |
| Docker | **Docker Engine** (pas Docker Desktop), service démarré au boot | Le script refuse de continuer si le démon est injoignable. |
| Compose | Plugin **Docker Compose v2**, version récente | Le script refuse Compose v1. Il utilise `docker compose config --images` et `up --pull never` : vérifier que la version installée les accepte (voir les commandes de vérification). |
| Réseau sortant | HTTPS (443) vers `github.com`, `*.actions.githubusercontent.com`, `ghcr.io`, `pkg-containers.githubusercontent.com`, Docker Hub et `quay.io` | Runner GitHub, images GHCR, images PostgreSQL/Kafka (Docker Hub) et Keycloak (`quay.io`). Préciser s'il faut passer par un proxy. |
| Réseau entrant | **Aucun port à ouvrir pour le runner** | Le runner se connecte lui-même à GitHub (connexion sortante). L'accès à l'application pour la démo est une décision séparée (voir § 4). |

**Politique de nettoyage du disque (décision de l'administrateur).**
`deploy-test.sh` ne supprime **jamais** rien : ni conteneur, ni image, ni volume.
Les images des anciens commits s'accumulent donc. L'administrateur choisit qui
nettoie, quand et comment, avec ces règles :

- garder au minimum l'image **actuellement déployée** et la **précédente**
  (le script note les deux dans son dossier d'état, voir § 5) ;
- **ne jamais supprimer les volumes** `aquaops-test_pgdata` et
  `aquaops-test_kafkadata` : ils contiennent les données de l'environnement de test ;
- ne toucher qu'aux ressources du projet `aquaops-test`, jamais à celles d'autres
  projets présents sur la VM.

Les journaux des conteneurs sont déjà limités à 3 fichiers de 10 Mo par service
(`deploy/test/compose.yml`).

### 2. Sécurité du runner auto-hébergé : prérequis

Le futur job de déploiement tournera **sur la VM**, via un runner GitHub Actions
auto-hébergé. Ce choix évite d'ouvrir SSH sur Internet et de stocker une clé
privée dans GitHub. En contrepartie, **un runner exécute sur la VM le code des
workflows qu'on lui envoie** : il faut le traiter comme un accès à la VM.

**Ce qui protège réellement la VM :**

1. **VM dédiée au test.** Aucune donnée réelle, aucun autre service important,
   aucun accès à un réseau de production. Elle doit pouvoir être reconstruite.
2. **Compte dédié non-root** pour le runner (par exemple `aquaops-runner`), sans
   `sudo`, utilisé uniquement par le runner.
3. **Accès en écriture au dépôt limité aux membres de l'équipe.** Toute personne
   qui peut pousser une branche peut écrire un workflow qui vise ce runner.
4. **Secrets de test uniquement** dans `/etc/aquaops/test.env` : mots de passe
   propres à cet environnement, jamais réutilisés ailleurs, et changés si le
   runner ou la VM est compromis.

**Le groupe `docker` équivaut à `root`.** Un membre de ce groupe peut lancer
un conteneur qui monte `/` de l'hôte : il obtient alors les droits root sur la
VM. Une règle `sudo` limitée à `docker compose` ne change rien (`compose`
accepte n'importe quel fichier, donc n'importe quel conteneur). C'est pourquoi
**le compte du runner n'a pas accès à Docker** : il demande le déploiement
d'un SHA au wrapper `aquaops-deploy` (§ 7), qui seul tourne sous un compte du
groupe `docker`, avec des chemins, un projet et un fichier de configuration
fixés à l'avance. Un workflow malveillant poussé sur une branche obtient au pire
le compte `aquaops-runner` : pas de Docker, pas de mots de passe (le fichier
`test.env` lui est illisible), et seulement le droit de redéployer un commit
**déjà présent sur `main`** dont le `publish.yml` active le déploiement (§ 7).

**Ce qui reste une confiance, pas une garantie :** le wrapper exécute
`deploy/scripts/deploy-test.sh` et les fichiers Compose **du commit demandé**.
Un SHA présent sur `main` est du code relu et fusionné, pas du code prouvé sûr :
quiconque peut fusionner sur `main` décide de ce qui tourne avec les droits
Docker (donc root) sur la VM. La protection de `main` (PR obligatoire,
approbation, pas de push direct), quand le forfait la permet, et la règle
d'équipe « personne ne fusionne sa propre PR » restent donc indispensables.
La VM doit rester dédiée, sans données sensibles, et reconstructible.

**Ce qui réduit le risque, sans suffire seul :**

| Mesure | Ce qu'elle apporte | Sa limite |
| --- | --- | --- |
| **Labels dédiés** (`aquaops-test`) et enregistrement sans les labels par défaut | Un job qui demande seulement `self-hosted` n'atterrit pas par erreur sur la VM. Les jobs actuels (`runs-on: ubuntu-24.04`) tournent de toute façon chez GitHub. | Un workflow écrit dans **n'importe quelle branche** peut demander ces labels : ce n'est **pas** une frontière de sécurité. |
| **Condition dans le job** (`push` sur `main` uniquement) | Le job officiel ne se lance pas sur une PR. | Elle ne protège que le workflow de `main` : une branche peut contenir un autre workflow sans cette condition. |
| **Runner enregistré au niveau du dépôt** | Seul ce dépôt peut l'utiliser. | Les **groupes de runners** n'existent que pour les organisations GitHub. Le dépôt `fatii-elb/aquaops` est un dépôt personnel : il n'y a pas de groupe de runners pour filtrer davantage. |
| **Protection de la branche `main`** : PR obligatoire, au moins 1 approbation, pas de push direct ni de force-push, checks obligatoires (`CI result`, `Gitleaks`) | Le workflow de déploiement de `main` ne change qu'après relecture. | Ne s'applique pas aux workflows des branches de travail (voir ligne 1). |
| **Environnement GitHub `test`** : branches autorisées = `main` uniquement, relecteurs obligatoires si l'équipe le souhaite | Approbation humaine avant chaque déploiement officiel ; protège les secrets rangés dans cet environnement. | Un job qui **ne déclare pas** l'environnement peut quand même viser les labels du runner. |
| **PR venant de forks** : laisser désactivée l'exécution des workflows de forks (réglage du dépôt, désactivé par défaut pour un dépôt privé) | Du code externe à l'équipe ne peut pas tourner. | Ne concerne pas les branches poussées par les membres de l'équipe. |
| **Scripts de démarrage du runner** (hooks) | Utiles pour nettoyer l'espace de travail. | Pas une frontière de sécurité : le job lancé reste du code arbitraire. |

**Attention au forfait GitHub.** Le dépôt est **privé**. Pour un dépôt privé, la
protection de branche et les environnements (avec leurs règles de protection)
dépendent du forfait du compte propriétaire : certaines de ces fonctions ne sont
pas disponibles avec le forfait gratuit, et les relecteurs obligatoires d'un
environnement peuvent exiger un forfait supérieur. **Vérifier dans les réglages
du dépôt ce qui est réellement disponible** avant de compter dessus. Si une
protection manque, la règle d'équipe (« personne ne fusionne sa propre PR »)
reste la seule barrière, et la VM doit encore plus être traitée comme jetable.

En résumé : **labels, conditions et hooks limitent les erreurs ; ils
n'empêchent pas un membre de l'équipe d'exécuter du code sur la VM.** La vraie
frontière, ce sont les points 1 à 4 ci-dessus.

### 3. Configuration du déploiement

**Fichier de configuration `/etc/aquaops/test.env`.** Il est **hors du dépôt**,
lisible par le seul compte `aquaops-deploy` (pas par le runner). Le wrapper
refuse un dossier `/etc/aquaops` modifiable par un autre que `root`, un fichier
qui n'appartient pas à `aquaops-deploy`, un lien symbolique ou un mode autre
que `600`/`400`. `deploy-test.sh` refuse en plus :
- un fichier situé dans le dossier du dépôt ;
- un fichier accessible à d'autres que son propriétaire (`stat -c '%a'` doit
  donner `600`, recommandé, ou `400`, `500`, `700`) ;
- une variable obligatoire absente, vide ou laissée à `change-me` (avec
  `AQUAOPS_WITH_INFRA=true`, cela vaut aussi pour `KEYCLOAK_ADMIN`,
  `KEYCLOAK_ADMIN_PASSWORD` et `KEYCLOAK_AUDIT_CLIENT_SECRET`) ;
- un fichier qui définit `IMAGE_TAG` ou `IMAGE_NAMESPACE` (c'est le déploiement qui les fournit) ;
- une variable utilisée par les fichiers Compose qui serait **aussi exportée**
  dans l'environnement du shell (elle écraserait la valeur du fichier sans prévenir).

**Gitleaks et les fichiers d'exemple.** Depuis SCRUM-62 (#95), `.gitleaks.toml`
n'exclut plus les fichiers `.env.example` : ils sont analysés comme les autres
fichiers, sur les commits de chaque PR et de chaque push sur `main`. Un vrai
secret ajouté à `deploy/test/.env.example` serait donc signalé, ce qui ferait
échouer la PR et, sur `main`, bloquerait la construction des images. N'y
mettre que des valeurs factices (`change-me`) ; les vraies valeurs vont
uniquement dans `/etc/aquaops/test.env`, sur la VM.

Création, par l'administrateur, avec un fichier vide à remplir ensuite à partir
de `deploy/test/.env.example` :

```bash
sudo install -d -m 750 -o root -g aquaops-deploy /etc/aquaops
sudo install -m 600 -o aquaops-deploy -g aquaops-deploy /dev/null /etc/aquaops/test.env
```

Si le fichier existe déjà (créé pour `aquaops-runner`), le § 7 donne les
commandes qui changent seulement son propriétaire, sans toucher à son contenu.

**Variables du fichier `test.env`** (noms exacts lus par `deploy/test/compose.yml`
et `deploy/test/compose.infra.yml`) :

| Variable | Obligatoire | Rôle |
| --- | --- | --- |
| `DB_HOST` | oui | Hôte PostgreSQL vu par les conteneurs (`postgres` si l'infrastructure est dans Docker). |
| `DB_PORT` | non (5432) | Port PostgreSQL. |
| `DB_USER` | oui | Compte PostgreSQL. |
| `DB_PASSWORD` | oui | Mot de passe propre à cet environnement. |
| `KAFKA_BOOTSTRAP_SERVERS` | oui | Adresse Kafka vue par les conteneurs (`kafka:9092` si l'infrastructure est dans Docker). |
| `KEYCLOAK_ISSUER_URI` | oui | Doit être **exactement** l'`iss` des jetons (voir § 4). |
| `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD` | oui avec `compose.infra.yml` | Compte administrateur de Keycloak. |
| `KEYCLOAK_AUDIT_CLIENT_SECRET` | oui avec `compose.infra.yml` | Secret du client Keycloak `aquaops-audit` (audit des connexions, FR-24), lu par l'import du realm et par assets-service. Valeur propre à cet environnement (par exemple générée avec `openssl rand -hex 24`), jamais `change-me`. |
| `GATEWAY_BIND`, `GATEWAY_HOST_PORT` | non (`127.0.0.1`, `8080`) | Adresse et port de la gateway sur la VM. |
| `KEYCLOAK_BIND`, `KEYCLOAK_HOST_PORT` | non (`127.0.0.1`, `8180`) | Idem pour Keycloak (avec `compose.infra.yml`). |
| `JAVA_MEM_LIMIT` | non (`768m`) | Limite mémoire de chaque service Java. |

**Variables passées au script par le déploiement** (jamais dans `test.env`).
Dans le déploiement automatique, c'est le wrapper qui les fixe : le runner ne
fournit que le SHA, l'heure du push, le jeton et le nom d'utilisateur GitHub.

| Variable | Rôle |
| --- | --- |
| `IMAGE_TAG` | **SHA complet (40 caractères hexadécimaux)** du commit construit et vérifié par le workflow « Images ». Tout autre format est refusé (pas de `latest`). |
| `AQUAOPS_ENV_FILE` | Chemin du fichier : `/etc/aquaops/test.env`. |
| `AQUAOPS_WITH_INFRA` | `true` pour utiliser aussi `compose.infra.yml` (défaut `false`). |
| `AQUAOPS_PROJECT` | Projet Compose : `aquaops-test` (défaut) ou `aquaops-test-<suffixe>`. Tout autre nom est refusé. |
| `AQUAOPS_HEALTH_TIMEOUT` | Délai de `wait-healthy.sh` en secondes (défaut 180). |
| `AQUAOPS_STATE_DIR` | Dossier d'état (défaut `~/.local/state/aquaops-deploy/<projet>`). |
| `GHCR_USERNAME`, `GHCR_TOKEN` | Connexion **temporaire** à GHCR pour le pull (voir ci-dessous). |
| `PUSHED_AT` | Heure du push en secondes epoch, pour mesurer la durée de bout en bout. |
| `IMAGE_NAMESPACE` | Registre, défaut `ghcr.io/fatii-elb/aquaops`. |

**Authentification GHCR en lecture seule.** Les images d'un dépôt privé sont
privées : la VM doit s'authentifier pour les télécharger.
- Le job de déploiement utilise le **`GITHUB_TOKEN`** du workflow, limité à
  `contents: read` (le wrapper lit `main`) et `packages: read` (pull). Ce jeton
  est créé pour le job et expire avec lui. Il passe au wrapper **par stdin**,
  jamais en argument : le wrapper le donne à `git` par son environnement
  (aucun fichier, aucun gestionnaire d'identifiants) puis à `deploy-test.sh`.
- `deploy-test.sh` retire aussitôt le jeton de son environnement, se connecte
  avec `--password-stdin` dans une configuration Docker **temporaire**, fait le
  pull, se déconnecte et supprime cette configuration, même en cas d'échec.
  `~/.docker/config.json` n'est jamais modifié.
- **Ne stocker aucun jeton personnel (PAT) sur la VM.**
- À vérifier au premier déploiement réel : que ce `GITHUB_TOKEN` peut bien lire
  les paquets publiés par le workflow (ils sont liés au dépôt par le label
  `org.opencontainers.image.source`). Ce n'est pas encore testé.

**Le bon SHA, et seulement lui.** Le script vérifie, avant de toucher un conteneur,
que les 7 images configurées sont exactement `<namespace>/<service>:<IMAGE_TAG>`.
Après le démarrage, `wait-healthy.sh` vérifie que chaque conteneur tourne bien
avec cette image exacte (contrôle `wrong-image`).

**Isolation du projet Compose.** Toutes les commandes utilisent `-p aquaops-test`,
les mêmes fichiers et le même `--env-file`. Les conteneurs, le réseau `backend` et
les volumes sont préfixés par le nom du projet. Le script ne lance jamais `down`,
`rm`, `prune` ni `-v`, ne construit aucune image (`up -d --no-build --pull never`)
et ne touche à aucun autre projet de la VM. Un verrou (`flock`) empêche deux
déploiements simultanés.

### 4. Décisions d'infrastructure à prendre

| Sujet | Options | Qui décide |
| --- | --- | --- |
| **PostgreSQL** | Dans Docker sur la VM (`compose.infra.yml`, volume `aquaops-test_pgdata`, 4 bases créées au premier démarrage) **ou** serveur existant (`DB_HOST`). Sauvegarde du volume ? | Administrateur + E5 |
| **Kafka** | Dans Docker (`kafka:9092`, volume `aquaops-test_kafkadata`, topics créés automatiquement) **ou** broker existant (`KAFKA_BOOTSTRAP_SERVERS`). | Administrateur + E5 |
| **Keycloak** | `compose.infra.yml` le lance en mode `start-dev` : **test uniquement** (HTTP, base interne). Le realm (avec ses 4 utilisateurs de test) est importé depuis `docs/keycloak/` ; `KEYCLOAK_AUDIT_CLIENT_SECRET` est obligatoire. Les **utilisateurs créés à la main sont perdus** quand le conteneur est recréé. | E2 + administrateur |
| **Exposition de la gateway** | Par défaut `127.0.0.1:8080` : accessible seulement depuis la VM. Pour la démo, deux possibilités : **tunnel SSH** (rien à ouvrir) **ou** `GATEWAY_BIND` sur une interface réseau + règle de pare-feu limitée aux adresses autorisées. | Administrateur |
| **Issuer Keycloak** | `KEYCLOAK_ISSUER_URI` doit être **identique** à l'`iss` des jetons. Ce champ dépend de l'adresse à laquelle le **navigateur** joint Keycloak. Si le navigateur passe par `http://localhost:8180` (tunnel) alors que les services utilisent `http://keycloak:8180`, les jetons sont refusés (401). Il faut fixer une seule adresse publique de Keycloak, joignable par le navigateur **et** par les conteneurs. | **E2** |
| **Redirect URIs du frontend** | Le client `aquaops-frontend` n'autorise aujourd'hui que `http://localhost:4200/*`. Il faudra ajouter l'adresse du frontend sur la VM. Le frontend n'est pas encore dans `deploy/test/compose.yml`. | **E2** (+ E4 pour le frontend) |

### 5. Vérification de la santé et mesure du temps

- **`wait-healthy.sh`** (appelé par `deploy-test.sh`) : attend que les 7 services
  Java soient `running` et `healthy` (`HEALTHCHECK` des images, sur
  `/actuator/health`) **et** tournent avec l'image du bon SHA. Délai par défaut
  180 s (`AQUAOPS_HEALTH_TIMEOUT`). En cas d'échec, il affiche l'état, les
  derniers contrôles de santé et les dernières lignes de journal, avec les
  mots de passe masqués.
  Avec `compose.infra.yml`, PostgreSQL, Kafka et Keycloak ont leurs propres
  contrôles de santé : les services ne démarrent qu'une fois leurs dépendances
  `healthy` (`depends_on`).
- **Smoke test** : `curl` sur `http://<adresse publiée>/actuator/health` de la
  gateway (3 essais, sans proxy). La réponse doit contenir `"UP"`.
- **Mesure du temps** : le script affiche la durée de chaque étape et la durée
  totale. Avec `PUSHED_AT`, il affiche aussi la durée **depuis le push** et
  avertit si elle dépasse **1200 secondes** (objectif NFR-08 : moins de
  20 minutes entre le push sur `main` et l'application déployée et saine).
  Le job passe `PUSHED_AT` à partir de l'événement `push`. Comme le script
  tourne via `sudo` (environnement remis à zéro), ces durées apparaissent dans
  le **journal** du job, pas dans son résumé.
- **Codes de sortie** : `0` déployé et sain · `1` échec du déploiement · `2` refus
  avant d'avoir touché un conteneur (entrée, configuration ou environnement
  invalide).
- **Dossier d'état** (`/var/lib/aquaops-deploy/state/` avec le wrapper ;
  `~/.local/state/aquaops-deploy/aquaops-test/` si le script est lancé à la main) :
  `deployed-sha`, `previous-sha` et `history.log` (une ligne par tentative, sans
  secret). Il n'y a **pas de retour arrière automatique** : `previous-sha` sert à
  un retour arrière manuel ou futur.

### 6. Checklist pour l'administrateur de la VM

**A. Informations à fournir à E5 (ne pas mettre de secret dans ce document) :**

- [ ] Nom d'hôte ou adresse de la VM, et qui y a accès
- [ ] Système et version (`/etc/os-release`), architecture (`x86_64` attendu)
- [ ] vCPU, RAM et espace disque disponible pour Docker
- [ ] Docker Engine et Compose déjà installés ? Versions ?
- [ ] Proxy sortant éventuel ; accès HTTPS vers les domaines du § 1
- [ ] Infrastructure dans Docker sur la VM, ou PostgreSQL / Kafka / Keycloak fournis ailleurs
- [ ] Mode d'accès pour la démo (tunnel SSH ou exposition réseau + pare-feu)
- [ ] Politique de nettoyage du disque et de sauvegarde du volume PostgreSQL

**B. Permissions nécessaires :**

- [ ] Administrateur de la VM : créer le compte `aquaops-runner` (**sans**
      `sudo` ni groupe `docker`), installer le service du runner, puis le
      déploiement sécurisé du § 7 (compte `aquaops-deploy`, wrapper, règle sudoers,
      `/etc/aquaops/test.env`)
- [ ] Propriétaire du dépôt GitHub (`fatii-elb`) : enregistrer le runner
      (*Settings > Actions > Runners*), créer l'environnement `test`, protéger `main`
- [ ] L'équipe : remplir `test.env` avec des mots de passe propres au test

**C. Installation des prérequis (sur la VM, par l'administrateur) :**

- [ ] Docker Engine + plugin Compose v2 depuis les dépôts officiels de Docker
- [ ] `curl` et `flock` (paquet `util-linux`) présents
- [ ] Compte du runner, **sans** groupe `docker` ni `sudo` :
      `sudo useradd --create-home --shell /bin/bash aquaops-runner`
- [ ] Déploiement sécurisé installé (§ 7) ; `/etc/aquaops/test.env` en `600`,
      propriétaire `aquaops-deploy`, puis rempli

**D. Commandes de vérification (lecture seule, sous le compte `aquaops-deploy`,
par exemple `sudo -u aquaops-deploy bash` ; sous `aquaops-runner`, les commandes
`docker` et `stat` sur `test.env` doivent au contraire échouer) :**

```bash
uname -m                                   # x86_64
grep -E '^(NAME|VERSION)=' /etc/os-release
nproc && free -h && df -h /var/lib/docker
docker version --format '{{.Server.Version}}'     # le démon répond
docker compose version                     # v2.x
docker compose config --help | grep -e '--images'  # option utilisée par le script
docker compose up --help | grep -e '--pull'        # option utilisée par le script
command -v curl flock
stat -c '%a %U' /etc/aquaops/test.env     # 600 aquaops-deploy
curl -sS -o /dev/null -w '%{http_code}\n' https://ghcr.io/v2/   # 401 attendu : GHCR joignable, sans connexion
```

Une validation complète, sans rien démarrer ni télécharger, se fait avec le
mode `check` du wrapper (§ 7). Ne jamais lancer `docker compose ... config` sans
`--quiet` : la sortie afficherait les mots de passe.

**E. Approbation requise avant d'enregistrer le runner :**

- [ ] Les points A à D sont remplis et vérifiés
- [ ] L'équipe a accepté les risques du § 2, en particulier : VM dédiée,
      groupe `docker` équivalent à root (réservé à `aquaops-deploy`), code de
      `main` exécuté avec ces droits, accès en écriture au dépôt limité à l'équipe
- [ ] `main` est protégée et l'environnement `test` est créé, **dans la mesure où
      le forfait GitHub du dépôt le permet** (voir § 2) ; sinon, la limite est
      connue et acceptée par l'équipe
- [ ] La publication GHCR est active (décision Trivy de E4 prise) : sans images
      publiées, le runner n'aurait rien à déployer
- [ ] L'enregistrement est fait **par le propriétaire du dépôt**, avec le jeton
      d'enregistrement temporaire affiché par GitHub (jamais copié dans un fichier
      du dépôt), le label `aquaops-test`, sans les labels par défaut si possible,
      et le runner installé comme service sous le compte `aquaops-runner`

### 7. Déploiement sécurisé : compte `aquaops-deploy` et wrapper

**Principe.** Le runner ne touche jamais Docker. Il demande seulement le
déploiement d'un commit :

```
job deploy-test (compte aquaops-runner : ni docker, ni sudo général)
  printf '<jeton>\n<utilisateur>\n' | sudo -n -u aquaops-deploy /usr/local/sbin/aquaops-deploy <SHA> <heure du push>
      │ une seule règle sudoers : ce fichier, ce compte, ces deux arguments
      ▼
/usr/local/sbin/aquaops-deploy (root:root 755), exécuté sous aquaops-deploy (groupe docker)
  1. arguments : SHA de 40 caractères hexadécimaux minuscules, heure = nombre
  2. jeton et utilisateur lus sur stdin (jamais en argument, jamais affichés, jamais écrits)
  3. propriétaire et droits du wrapper, de /etc/aquaops, de test.env, de /var/lib/aquaops-deploy
     et des dossiers du PATH
  4. verrou : un seul déploiement à la fois
  5. récupère main dans un dépôt privé (/var/lib/aquaops-deploy/repo.git, aucun hook git,
     aucun identifiant enregistré) ; refuse un SHA absent de main
  5b. refuse si le publish.yml de CE commit ne contient pas DEPLOY_TEST 'on',
     GHCR_PUBLISH 'on' et TRIVY_EXIT_CODE '1'
  6. refuse si un simulateur tourne hors du projet aquaops-test, ou si un autre
     conteneur publie déjà le port de la gateway ou de Keycloak
  7. extrait les fichiers de déploiement de CE commit dans /var/lib/aquaops-deploy/release
  8. lance son deploy/scripts/deploy-test.sh avec des réglages fixes :
     /etc/aquaops/test.env, projet aquaops-test, compose.infra.yml, état dans
     /var/lib/aquaops-deploy/state
```

L'environnement et le dossier courant de l'appelant sont ignorés (`bash -p`,
environnement vidé, en plus de la remise à zéro faite par `sudo`). Codes de
sortie : ceux de `deploy-test.sh` (`0`, `1`, `2`). Tests :
`bash deploy/vm/tests/test-aquaops-deploy.sh` (Linux ou WSL, sans root).

**Fichiers sur la VM :**

| Chemin | Propriétaire | Mode | Rôle |
| --- | --- | --- | --- |
| `/usr/local/sbin/aquaops-deploy` | `root:root` | `755` | Le wrapper (copie de `deploy/vm/aquaops-deploy`). |
| `/etc/sudoers.d/aquaops-deploy` | `root:root` | `440` | La règle (copie de `deploy/vm/aquaops-deploy.sudoers`). |
| `/etc/aquaops/` | `root:aquaops-deploy` | `750` | Dossier de configuration. |
| `/etc/aquaops/test.env` | `aquaops-deploy` | `600` | Mots de passe de test (illisibles pour le runner). |
| `/var/lib/aquaops-deploy/` | `aquaops-deploy` | `700` | Dépôt privé, fichiers extraits, état, verrou. |

**Version de `sudo`.** Le fichier contient deux lignes actives : une ligne
`Defaults` (`!log_input, !log_output` : sudo n'enregistre jamais stdin, donc le
jeton, même si la journalisation des entrées est activée ailleurs) et la règle,
avec une expression régulière sur les arguments. Il faut le **sudo classique
1.9.10 ou plus récent** (validé avec `visudo` 1.9.17p2 ; Ubuntu 24.04 : 1.9.15).
**Le fichier n'est pas compatible avec sudo-rs** (sudo par défaut d'Ubuntu 25.10
et suivantes) : son `visudo` refuse les deux lignes (« regular expressions are
not supported », « unknown setting: 'log_input' »). Avec sudo-rs, remplacer les
deux lignes par la ligne commentée à la fin du fichier : tous les arguments
atteignent alors le wrapper, qui refuse tout sauf `<SHA> <chiffres>` (sudo-rs
n'a pas de journalisation des entrées). Dans les deux cas, `sudo` ne journalise
que la ligne de commande : SHA et heure du push, rien de secret.

**Ce que le runner peut encore faire, et ce qui reste une confiance :**
- **Contourner le job officiel** : le runner (ou tout workflow qui vise son
  label) peut appeler le wrapper directement. Il ne peut alors déployer qu'un
  commit **de `main`** dont le **`publish.yml` active la politique** (les trois
  interrupteurs). Il échappe en revanche à l'approbation de l'environnement
  GitHub `test`, au résultat du workflow « Images » de ce commit, et peut
  redéployer un **ancien** commit de `main` qui remplit ces conditions (retour
  arrière).
- **Images GHCR** : le wrapper déploie l'image étiquetée par le SHA. Toute
  personne qui peut écrire dans le dépôt peut aussi, depuis un workflow de
  branche, publier une image sous cette étiquette ; les conteneurs reçoivent
  les mots de passe de `test.env`. La frontière réelle reste donc **l'accès en
  écriture au dépôt**. Pour aller plus loin : déployer par empreinte (*digest*)
  vérifiée ou vérifier une attestation de provenance (non fait).
- **Déni de service** : le runner peut interrompre un déploiement (signal,
  limites `ulimit`) ou en lancer à répétition. Le verrou empêche deux
  déploiements simultanés ; un déploiement interrompu laisse la pile partielle,
  jamais les données supprimées.

#### Installation (administrateur, après la fusion de la PR)

Depuis une copie du dépôt sur la VM, placée sur un commit relu de `main` :

```bash
sudo -V | head -n 1                    # sudo classique >= 1.9.10, ou sudo-rs (voir ci-dessus)
command -v git flock base64 awk tar    # outils du wrapper (sinon : sudo apt install git)

# 1. Compte de déploiement : compte système, sans shell de connexion
sudo useradd --system --create-home --home-dir /var/lib/aquaops-deploy \
     --shell /usr/sbin/nologin aquaops-deploy
sudo usermod -aG docker aquaops-deploy
sudo chmod 700 /var/lib/aquaops-deploy

# 2. Wrapper et règle sudoers (vérifiée AVANT d'être installée)
sudo install -o root -g root -m 755 deploy/vm/aquaops-deploy /usr/local/sbin/aquaops-deploy
sudo visudo -cf deploy/vm/aquaops-deploy.sudoers
sudo install -o root -g root -m 440 deploy/vm/aquaops-deploy.sudoers /etc/sudoers.d/aquaops-deploy
sudo visudo -c

# 3. Configuration existante : seul le propriétaire change, pas le contenu
sudo chown root:aquaops-deploy /etc/aquaops && sudo chmod 750 /etc/aquaops
sudo chown aquaops-deploy:aquaops-deploy /etc/aquaops/test.env && sudo chmod 600 /etc/aquaops/test.env
```

Le wrapper du dépôt n'a **aucun effet** tant qu'il n'est pas réinstallé : une
modification fusionnée sur `main` doit être relue, puis réinstallée par
l'administrateur. Pour tout désactiver : `sudo rm /etc/sudoers.d/aquaops-deploy`.

#### Vérifications après installation (lecture seule)

```bash
id aquaops-runner     # ni docker, ni sudo, ni aquaops-deploy
id aquaops-deploy     # groupe docker
sudo -l -U aquaops-runner      # une seule règle : (aquaops-deploy) NOPASSWD: /usr/local/sbin/aquaops-deploy ...
sudo -u aquaops-runner sudo -n true                       # refusé
sudo -u aquaops-runner sudo -n -u aquaops-deploy /usr/local/sbin/aquaops-deploy check 0000000000000000000000000000000000000000   # refusé par sudo (sudo classique)
sudo -u aquaops-runner docker ps                          # refusé (pas d'accès au socket)
sudo -u aquaops-runner cat /etc/aquaops/test.env          # refusé
ls -ld /usr/local/sbin/aquaops-deploy /etc/sudoers.d/aquaops-deploy /etc/aquaops /etc/aquaops/test.env /var/lib/aquaops-deploy
```

#### Premier test : mode `check`, sans toucher au déploiement actuel

Le mode `check` fait toutes les vérifications (droits, commit sur `main`,
simulateur, ports, `docker compose config --quiet` avec `test.env`) puis
s'arrête. Il n'appelle que `docker version`, `docker ps` et `docker compose
config --quiet` : ni pull, ni démarrage, ni arrêt, ni changement de `release/`
ou de l'état. Si la politique du commit est encore désactivée (cas actuel), il
l'indique par une ligne `NOTE` sans échouer. La règle sudoers ne l'autorise pas au runner : l'administrateur le lance.
Le dépôt étant privé, il faut un jeton de lecture : un jeton *fine-grained*
**temporaire** (ce dépôt seulement, *Contents: read-only*, expiration 1 jour),
supprimé juste après sur GitHub.

```bash
read -rs GH_TOKEN      # coller le jeton : rien ne s'affiche, rien dans l'historique
printf '%s\n%s\n' "$GH_TOKEN" "<compte GitHub>" \
  | sudo -u aquaops-deploy /usr/local/sbin/aquaops-deploy check "$(git rev-parse origin/main)"
unset GH_TOKEN
```

Lecture du résultat :
- `CHECK OK` (code 0) : tout est prêt pour un déploiement de ce commit, sauf si
  une ligne `NOTE` indique que sa politique (`publish.yml`) le refuse encore ;
- `containers of project aquaops-test` : les conteneurs que le déploiement mettra à jour ;
- `project aquaops-test has no container yet` : la pile actuelle appartient à
  **un autre projet Compose**. Le premier déploiement en créerait une
  deuxième, avec des volumes vides (et le wrapper refusera si les ports 8080 ou
  8180 sont déjà pris). À régler avant l'activation ;
- `PROBLEM: simulator running outside project aquaops-test` : attendu tant que
  le simulateur lancé à part tourne (voir ci-dessous) ;
- `docker compose rejected the configuration` : une variable manque dans `test.env`.

Le mode `check` ne fait pas les contrôles propres à `deploy-test.sh` (valeurs
`change-me`, variables exportées) : ceux-ci ont lieu au début du vrai
déploiement, avant de toucher un conteneur (code 2).

#### Avant le premier déploiement automatique

- **Simulateur.** `deploy/test/compose.yml` contient déjà le simulateur
  (`aquaops-test-simulator-1`), et `wait-healthy.sh` le vérifie. Le simulateur
  lancé à part doit être **arrêté** juste avant l'activation
  (`docker stop <nom>`, sans le supprimer, pour pouvoir revenir en arrière) :
  sinon le wrapper refuse le déploiement, pour éviter deux simulateurs qui
  publieraient chacun des mesures.
- **Keycloak.** Keycloak tourne en `start-dev` **sans volume** et importe le
  realm de `docs/keycloak/` à chaque création du conteneur. Au premier
  déploiement par le wrapper, sa configuration change (nouveau chemin de
  `docs/keycloak`, et le secret de `test.env` s'il diffère) : Compose **recrée**
  Keycloak, ce qui **synchronise** `KEYCLOAK_AUDIT_CLIENT_SECRET`, recrée les
  utilisateurs de test du realm et **perd les utilisateurs et réglages faits à
  la main** dans la console. Il en va de même à chaque déploiement qui change sa
  configuration ou le fichier du realm.
- **PostgreSQL et Kafka.** PostgreSQL est aussi recréé au premier déploiement
  (nouveau chemin de `postgres-init`), mais ses données restent dans le volume
  `aquaops-test_pgdata` ; les scripts d'initialisation ne sont pas relancés
  (volume non vide). Kafka n'est recréé que si sa configuration change ; son
  volume est conservé. Le wrapper et `deploy-test.sh` ne lancent jamais `down`,
  `rm`, `prune` ni `-v`.
