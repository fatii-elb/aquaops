# Terraform pour k3s (SCRUM-56 / NFR-09)

Carte SCRUM-56 · E5. Décrit **tout ce que contient le cluster k3s** de la VM de
test (`deploy/k3s/`), avec les providers **Kubernetes 3.5** et **Helm 3.3**
(Terraform ≥ 1.11, testé avec 1.17.2).

```text
deploy/terraform/
├── tf.sh                 seul point d'entrée : contrôles, état, workspace, garde-fous
├── cluster/              une fois par cluster : cert-manager (Helm), jamais détruit par RC-11
└── environment/          une fois par environnement = un namespace
    ├── main.tf           fondation : namespace, quotas, réseau, ConfigMap, secrets
    ├── postgres.tf  kafka.tf  keycloak.tf  eureka.tf   infrastructure (§ 7)
    ├── services.tf       gateway, detection, incidents, notifications, simulator (§ 8)
    ├── e2_services.tf    assets et telemetry, lus dans les manifestes de E2 (deploy/k8s) (§ 8)
    ├── databases.tf      Job idempotent : crée les bases manquantes, volume neuf ou existant (§ 8)
    ├── envs/test.tfvars  namespace "aquaops" (celui des manifestes de E2), durable, infrastructure et services désactivés
    └── envs/rc11.tfvars  namespace "aquaops-rc11", jetable (RC-11), infrastructure et services activés
```

## 1. Ce que crée chaque environnement (fondation)

| Objet | Nom | Rôle |
| --- | --- | --- |
| Namespace | `aquaops` / `aquaops-rc11` | Pod Security `baseline` imposé, `restricted` signalé |
| ResourceQuota | `aquaops-quota` | Plafond du namespace (mémoire, CPU, pods, volumes) ; ni NodePort ni LoadBalancer |
| LimitRange | `aquaops-limits` | Valeurs par défaut et maximum par conteneur |
| NetworkPolicy | `default-deny-ingress`, `allow-same-namespace`, `allow-ingress-controller` | Rien n'entre sauf depuis le même namespace ; Traefik n'atteint que les pods `aquaops.ma/ingress=true` |
| ConfigMap | `aquaops-config` | Adresses internes communes (`DB_HOST`, `KAFKA_BOOTSTRAP_SERVERS`, `EUREKA_URL`, `GATEWAY_URL`, limite d'ingestion…), aucun secret |
| Secret | `aquaops-db` | `DB_USER`, `DB_PASSWORD` (nom attendu par `deploy/k8s/assets`) |
| Secret | `aquaops-keycloak` | `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD`, `KEYCLOAK_AUDIT_CLIENT_SECRET` |
| Secret | `aquaops-simulator` | `SIMULATOR_API_KEY` (partagée par le simulateur et la gateway, SCRUM-65) |
| Secret | `ghcr-pull` | **Désactivé** (`ghcr_pull_secret = false`, décision du 15/11/2026) : aucun jeton GHCR dans Kubernetes |
| ServiceAccount | `default` | Aucun jeton d'API monté par défaut ; utiliserait `ghcr-pull` s'il était activé |

## 2. Secrets : jamais dans Git, ni dans l'état Terraform

- Les valeurs sont des variables **`ephemeral`**, passées aux secrets par
  **`data_wo`** (écriture seule) : elles vont à Kubernetes mais ne sont écrites
  **ni dans le plan ni dans l'état**. Vérifié : `data` et `data_wo` sont vides
  dans l'état, et aucune valeur (en clair ou en base64) n'apparaît dans les
  fichiers d'état ni dans les journaux.
- Elles sont nécessaires pour `plan` et `apply` (sinon arrêt avec un message
  clair), **pas** pour `destroy`.
- Changer une valeur : modifier le fichier, puis augmenter `secrets_revision`
  (`-var secrets_revision=2`). Les valeurs en écriture seule ne sont pas comparées.
- **Images AquaOps sans jeton GHCR dans le cluster** : elles sont construites,
  analysées (Trivy) puis importées dans k3s (§ 7, « Image Eureka »), avec
  `imagePullPolicy: Never`. Si un jour `ghcr_pull_secret = true` : jeton
  *classic* avec **uniquement** `read:packages`, expiration courte.

Sur la VM, un fichier par environnement, lisible par root seulement :

```bash
# Dossier À PART : ne jamais toucher à /etc/aquaops, vérifié par le wrapper
# de déploiement Compose (aquaops-deploy).
sudo install -d -m 700 -o root -g root /etc/aquaops-terraform
sudo install -m 600 -o root -g root /dev/null /etc/aquaops-terraform/test.env
sudoedit /etc/aquaops-terraform/test.env
# Contenu (une valeur par ligne, sans guillemets) :
#   TF_VAR_db_user=...
#   TF_VAR_db_password=...
#   TF_VAR_keycloak_admin=...
#   TF_VAR_keycloak_admin_password=...
#   TF_VAR_keycloak_audit_client_secret=...
#   TF_VAR_simulator_api_key=...
```

Pour `rc11` : un fichier `/etc/aquaops-terraform/rc11.env` avec **d'autres**
valeurs de test, jamais celles de `test`.

## 3. Utilisation (administrateur, sur la VM)

Prérequis : k3s installé (`deploy/k3s/README.md`), `KUBE_CONFIG_PATH` exporté,
Terraform installé :

```bash
# Terraform depuis le dépôt APT officiel de HashiCorp, version fixée
wget -O- https://apt.releases.hashicorp.com/gpg | sudo gpg --dearmor -o /usr/share/keyrings/hashicorp-archive-keyring.gpg
echo "deb [signed-by=/usr/share/keyrings/hashicorp-archive-keyring.gpg] https://apt.releases.hashicorp.com $(. /etc/os-release; echo "$VERSION_CODENAME") main" \
  | sudo tee /etc/apt/sources.list.d/hashicorp.list
sudo apt-get update
apt-cache madison terraform | head -3          # repérer la chaîne exacte de la 1.17.2
sudo apt-get install -y terraform=1.17.2-1    # adapter si la chaîne diffère
```

```bash
export KUBE_CONFIG_PATH=~/.kube/aquaops-k3s.yaml

# Une fois par cluster : cert-manager
deploy/terraform/tf.sh cluster cluster plan
deploy/terraform/tf.sh cluster cluster apply

# Environnement "test" (namespace aquaops)
set -a; source <(sudo cat /etc/aquaops-terraform/test.env); set +a
deploy/terraform/tf.sh environment test plan
deploy/terraform/tf.sh environment test apply
unset "${!TF_VAR_@}"                       # retirer les secrets de la session
```

`tf.sh` :
- vérifie que le kubeconfig est hors du dépôt et en mode 600 ;
- range l'état hors du dépôt, dans `~/.local/state/aquaops-terraform/<pile>/`
  (mode 700) ;
- sélectionne le workspace égal à l'environnement (Terraform le revérifie) ;
- affiche l'adresse du cluster avant `apply` et `destroy` ;
- refuse `destroy` sur `test` et `cluster`.

## 4. RC-11 / NFR-09 : destruction et reconstruction, isolées

**Principe** : RC-11 ne vise jamais l'environnement utilisé. Il vise
l'environnement jetable **`rc11`**, qui a :
- son namespace (`aquaops-rc11`) ;
- son état Terraform (workspace `rc11`) ;
- ses secrets (valeurs de test) ;
- ses volumes (local-path, sous `/var/lib/rancher/k3s/storage`), qui
  disparaissent avec le namespace.

Les NetworkPolicies l'isolent de `test`. Les volumes Docker Compose
(`/var/lib/docker`) et le namespace `aquaops` ne sont jamais concernés.

Garde-fous :
- `tf.sh` n'autorise `destroy` que pour `rc11`, avec
  `AQUAOPS_CONFIRM_DESTROY=rc11` ;
- un plan `rc11` lancé dans le workspace `test` (ou l'inverse) est refusé par
  Terraform.

```bash
export KUBE_CONFIG_PATH=~/.kube/aquaops-k3s.yaml
set -a; source <(sudo cat /etc/aquaops-terraform/rc11.env); set +a
deploy/terraform/tf.sh environment rc11 apply -auto-approve                # 1. créer
time AQUAOPS_CONFIRM_DESTROY=rc11 deploy/terraform/tf.sh environment rc11 destroy -auto-approve   # 2. détruire
sudo k3s kubectl get ns aquaops-rc11                                       # NotFound attendu
time deploy/terraform/tf.sh environment rc11 apply -auto-approve           # 3. reconstruire
deploy/terraform/tf.sh environment rc11 plan -detailed-exitcode            # 4. code 0 = aucun écart
sudo k3s kubectl -n aquaops get all,secret,cm                              # 5. "test" inchangé
unset "${!TF_VAR_@}"
```

**Mesuré dans le bac à sable** :

| Contenu de `rc11` | Destruction (sans secret chargé) | Reconstruction jusqu'à « tout Ready » |
| --- | --- | --- |
| Fondation seule | 9 s | 6 s |
| Fondation + infrastructure (§ 7) | 18 s, volumes et dossiers de données supprimés | 119 à 130 s (images déjà présentes ; 330 s au tout premier téléchargement) |
| Fondation + infrastructure + 7 services (§ 8 : assets et telemetry × 2), mesuré le 16/11/2026, k3s v1.37.2 | 65 s, 39 objets, namespace et volumes supprimés (0 PV restant) | 250 s pour l'apply, **270 s** jusqu'à 13 pods Ready, 0 redémarrage (images déjà importées) ; namespace `test` du même bac à sable inchangé (même UID, mêmes objets) |

Dans tous les cas, le `plan` suivant ne trouve aucun écart, et `test` est
inchangé (même identifiant, mêmes objets). Reste, pour la preuve RC-11
complète : le pipeline (« + pipeline » de la recette).

## 5. Validations (sans cluster)

```bash
cd deploy/terraform
terraform fmt -check -recursive
for s in cluster environment; do terraform -chdir=$s init -backend=false && terraform -chdir=$s validate; done
docker run --rm -v "$PWD/../..:/mnt:ro" -w /mnt koalaman/shellcheck:v0.11.0 deploy/terraform/tf.sh deploy/k3s/preflight.sh
docker run --rm -v "$PWD/../..:/src:ro" aquasec/trivy:latest config --skip-dirs '**/.terraform' /src/deploy/terraform
kubeconform -strict -summary deploy/k8s/assets deploy/k8s/telemetry   # manifestes de E2
docker run --rm -v "$PWD/../..:/repo:ro" -w /repo rhysd/actionlint:1.7.12 .github/workflows/*.yml
```

Analyses IaC (Trivy config + Checkov) et SCA : `docs/security-scans.md`,
exécutées par `.github/workflows/security.yml`.

`.terraform.lock.hcl` est versionné : sommes de contrôle des providers pour
Linux et Windows (`terraform providers lock -platform=linux_amd64 -platform=windows_amd64`).

## 6. Découpage en PRs (même pile `environment`, mêmes noms)

| PR | Contenu | État |
| --- | --- | --- |
| Fondation | § 1 à 5 | fusionnée (#111) |
| Infrastructure | § 7 | **ouverte (#112)** |
| Services | § 8, `deploy/k3s/{import-images,deploy-services}.sh` (§ 9) | branche `feature/SCRUM-56-k8s-services`, **empilée sur #112** |
| Analyses de sécurité | `.github/workflows/security.yml`, `docs/security-scans.md`, `deploy/sca/` | indépendante de #112 |
| Pipeline k3s | `.github/workflows/k3s-deploy.yml` (désactivé) ; puis wrapper privilégié `deploy/vm/aquaops-k3s-deploy` (à écrire) | après Services |
| Frontend + Ingress | image Angular (E4), Service `frontend`, Ingress TLS de E2 (`deploy/k8s/tls`) | à faire |

À l'arrivée de la pile complète, le budget mémoire de `deploy/k3s/README.md`
(§ 2) impose un choix : retirer Compose, ajouter une seconde VM, ou travailler
par fenêtres de maintenance.

## 7. Infrastructure (PostgreSQL, Kafka, Keycloak, Eureka)

Activée par `infrastructure_enabled = true` : **seulement dans `rc11`** pour
l'instant. Sur la VM, `test` garde l'infrastructure de Docker Compose.
Ressources Kubernetes natives, mêmes images et mêmes réglages que Compose,
images figées par **tag et empreinte** (`infrastructure_images`).

| Service (nom attendu par E2) | Objet | Image | Données | Mémoire demandée / limite | Mesurée |
| --- | --- | --- | --- | --- | --- |
| `postgres:5432` | StatefulSet | `postgres:18.6` | volume 2 Gi ; 4 bases créées par `deploy/integration/postgres-init/01-databases.sh` (lu tel quel) | 256 / 512 Mi | 45 à 50 Mi |
| `kafka:9092` | StatefulSet, KRaft 1 nœud | `apache/kafka:4.3.1` | volume 2 Gi ; topics créés automatiquement | 640 Mi / 1 Gi | 406 à 419 Mi |
| `keycloak:8180` | Deployment, `start-dev` (test) | `quay.io/keycloak/keycloak:26.8.0` | realm `docs/keycloak/aquaops-realm.json` (E2) réimporté à chaque démarrage | 512 / 768 Mi | 606 à 617 Mi |
| `eureka:8761` | Deployment | image importée (ci-dessous) | - | 256 / 384 Mi | 209 à 224 Mi |
| **Total** | | | | **1,6 / 2,6 Gi** (quota de `rc11` : § 8) | **1,27 à 1,31 Gi** |

Mesures `kubectl top` dans le bac à sable, au repos. Le nœud entier (k3s +
add-ons + infrastructure) occupe environ 2,1 Gio de mémoire anonyme : sur la
VM, c'est ce qui s'ajouterait à la pile Compose si `rc11` y tournait.

Sécurité : `enable_service_links = false` partout (obligatoire pour Kafka :
l'image transforme toute variable `KAFKA_*` en réglage du broker, et le Service
`kafka` injecterait `KAFKA_PORT=tcp://...`). Aucun jeton d'API monté, seccomp
`RuntimeDefault`, `allowPrivilegeEscalation: false`, toutes les capacités
retirées. PostgreSQL (uid 999) et Eureka (uid 999) : utilisateur non root
vérifié et système de fichiers racine en lecture seule. Écarts restants
(Trivy, objets déployés) : 0 CRITICAL ; 2 HIGH acceptés pour Kafka et Keycloak
(racine en écriture : Kafka génère sa configuration dans l'image, `start-dev`
écrit dans `/opt/keycloak`) ; 3 MEDIUM « registres de confiance » ; 9 LOW (uid
< 10000).

### Image Eureka : construite, analysée, importée (sans registre ni jeton)

L'image n'est pas encore sur GHCR (workflow Images désactivé). Sur la VM,
depuis la copie du dépôt, **sur le commit fusionné** :

```bash
SRC=$(git log -1 --format=%h -- services/eureka-server)     # doit égaler eureka_image dans envs/rc11.tfvars
docker build -f deploy/docker/spring-service.Dockerfile \
  --label org.opencontainers.image.revision=$SRC \
  -t localhost/aquaops/eureka-server:$SRC services/eureka-server
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:0.76.2 image \
  --severity CRITICAL --ignore-unfixed --exit-code 1 localhost/aquaops/eureka-server:$SRC   # 0 = aucun CRITICAL
docker save localhost/aquaops/eureka-server:$SRC | sudo k3s ctr images import -
sudo k3s crictl images | grep eureka-server
```

Le nom `localhost/...` et `imagePullPolicy: Never` garantissent que k3s ne
télécharge jamais cette image : sans import, le pod reste en
`ErrImageNeverPull` au lieu de prendre une image publique homonyme. La
construction utilise Docker sur la VM : la faire hors des heures de test
(CPU et mémoire partagés avec Compose).

### Ce qui a été vérifié (bac à sable, jamais sur la VM)

| Composant | Test | Résultat |
| --- | --- | --- |
| PostgreSQL | Bases présentes après un volume vide | `aquaops_assets`, `aquaops_incidents`, `aquaops_notifications`, `aquaops_telemetry` ; PostgreSQL 18.6, uid 999 |
| Kafka | Production puis lecture depuis un autre pod via `kafka:9092`, topic créé automatiquement | message relu |
| Keycloak | Issuer, client `aquaops-audit`, utilisateurs, rôles | issuer `http://keycloak:8180/realms/aquaops` ; jeton obtenu avec le secret de `aquaops-keycloak`, refusé avec un mauvais secret ou le texte `${KEYCLOAK_AUDIT_CLIENT_SECRET}` ; 4 utilisateurs et 4 rôles importés |
| Eureka | Santé, registre, actuator | `UP` ; registre 200 ; `/actuator/env` 404 |
| **Compatibilité E2** | **Vrai manifeste** `deploy/k8s/assets/deployment.yaml` déployé dans `rc11` (namespace, image importée et 1 réplica changés à la volée, fichier inchangé) | prêt en 41 s, 0 redémarrage, 0 erreur ; connecté à `aquaops_assets`, groupe Kafka rejoint, inscrit dans Eureka ; jeton émis par `keycloak:8180` **accepté** (403 sans le rôle, 401 pour un faux jeton) |

Ce test E2 avait demandé un quota plus grand (passé par `-var`) : le quota de
`rc11` est depuis dimensionné pour l'infrastructure et les 7 services (§ 8).

Point d'attention : avec `default-deny-ingress`, la **toute première**
connexion d'un pod qui vient de démarrer peut être refusée (le contrôleur de
NetworkPolicy de k3s met moins d'une seconde à l'autoriser). Les services
Spring, qui démarrent en plus de 10 s, ne sont pas concernés ; un script de
test doit réessayer.

## 8. Les 7 services

Activés par `services_enabled = true` (exige `infrastructure_enabled`) :
**seulement dans `rc11`** pour l'instant ; `test` (namespace `aquaops` de la
VM) garde `services_enabled = false` et les services de Docker Compose.

| Service | Fichier | Port | Réplicas | Variables (comme `deploy/test/compose.yml`) | Mémoire demandée / limite | Mesurée au repos |
| --- | --- | --- | --- | --- | --- | --- |
| `gateway` | `services.tf` | 8080 | 1 | ConfigMap `gateway-routes` (profil `eureka`, `*_URL` fixes sauf `gateway_eureka_routes`, actuator `health` seul), `EUREKA_URL`, limite 500 req/s, clé `SIMULATOR_API_KEY` | 256 / 512 Mi | 237 Mi |
| `telemetry` | `e2_services.tf` (**manifeste de E2**) | 8081 | HPA : 2–6 (CPU 70 %) | ceux de E2 + client Eureka | 384 / 768 Mi par pod | 275 et 284 Mi à 2 pods |
| `assets` | `e2_services.tf` (**manifeste de E2**) | 8082 | 2 (E2) | ceux de E2 + audit des connexions (`AUDIT_KEYCLOAK_ENABLED`, `KEYCLOAK_BASE_URL`, secret) comme `compose.infra.yml` | 384 / 768 Mi × 2 | 345 et 338 Mi |
| `detection` | `services.tf` | 8083 | 1 | depuis la PR #116 : `DETECTION_DB_URL` (base `aquaops_detection`, construite depuis `DB_HOST`/`DB_PORT`), `DB_*`, `KEYCLOAK_ISSUER_URI` (même émetteur qu'assets), `KAFKA_BOOTSTRAP_SERVERS` | 384 / 640 Mi (JPA depuis #116) | 189 Mi avant #116 ; 277 Mi avec #116 |
| `incidents` | `services.tf` | 8084 | 1 | `INCIDENTS_DB_URL` (construite depuis `DB_HOST`/`DB_PORT` de `aquaops-config`), `KAFKA_BOOTSTRAP_SERVERS`, `DB_*` | 384 / 640 Mi | 295 Mi |
| `notifications` | `services.tf` | 8085 | 1 | `NOTIFICATIONS_DB_URL` (idem), `KAFKA_BOOTSTRAP_SERVERS`, `DB_*`, `SPRING_JPA_SHOW_SQL=false` | 384 / 640 Mi | 287 Mi |
| `simulator` | `services.tf` | 8086 | 1 | `GATEWAY_URL`, client Eureka, secret `SIMULATOR_API_KEY` | 192 / 384 Mi | 186 Mi |

Mesures `kubectl top` dans le bac à sable (16/11/2026, au repos, simulateur
actif). Namespace `rc11` entier : **4,0 Gio** et 1,2 CPU (13 pods, infrastructure comprise ; mesure prise quelques minutes après un retour arrière, CPU d'assets et de la gateway encore en chauffe) ; nœud : 6,0 Gio (k3s, add-ons, `rc11` et le namespace `test` vide).

**Quota de `rc11`** (sommes : demandes 1,55 CPU / 4800 Mi, limites 12 CPU /
8576 Mi) : demandes 2,5 CPU / 5632 Mi, limites 16 CPU / 10 Gi, soit la place de
**deux** pods de plus : une mise à jour progressive à la fois
(`deploy-services.sh` applique avec `-parallelism=1`) **et** un pod resté
bloqué par une version défaillante. Observé dans le bac à sable avec la place
d'un seul pod : le retour arrière était refusé par le quota (« exceeded
quota ») et ses rollouts expiraient. Des mises à jour en parallèle
demanderaient jusqu'à 7 pods de plus : refusées par le quota.

### Bases de données des services (`databases.tf`)

Le script d'initialisation de PostgreSQL (`deploy/integration/postgres-init`,
partagé avec les piles Compose, dont celle de la VM) ne s'exécute que sur un
**volume vide** et ne crée que 4 bases. La PR #116 a ajouté
`aquaops_detection`. Le Job `postgres-databases-<empreinte>` crée chaque base
**manquante** de la liste (`aquaops_assets`, `aquaops_detection`,
`aquaops_incidents`, `aquaops_notifications`, `aquaops_telemetry`), sur un
volume neuf comme sur un volume existant : une base présente est laissée telle
quelle, rien n'est jamais supprimé ni modifié. Le nom du Job contient une
empreinte de la liste : ajouter une base relance le Job ; une liste inchangée
ne relance rien. Identifiants lus dans le Secret `aquaops-db` (jamais dans la
commande, le plan ou l'état). Les services attendent la fin du Job.

### Fichiers de E2 réutilisés tels quels

`deploy/k8s/assets/` et `deploy/k8s/telemetry/` (Deployment, Service,
PodDisruptionBudget et HPA) sont lus à chaque `plan` (Deployment avec
`manifest_decode`, autres documents avec `manifest_decode_multi`), jamais
copiés : une modification de E2 est reprise telle quelle.
Ne changent que : namespace, étiquettes communes, image (registre, SHA,
empreinte, `imagePullPolicy`), réplicas d'assets si `e2_replicas` le demande
(par défaut la valeur de E2 ; le nombre de réplicas telemetry est géré par son
HPA), `enableServiceLinks` et `automountServiceAccountToken` à
`false`, les 3 variables d'audit d'assets, et la **surcouche de sécurité E5**
(uid/gid 999 déclarés donc `runAsNonRoot` vérifiable, racine en lecture seule,
`/tmp` en `emptyDir`). Vérifié : les deux services démarrent et passent leurs
sondes avec cette surcouche.

Deux réglages découverts en testant les **mises à jour** (pas seulement la
création) :
- `field_manager { force_conflicts = true }` : après un `kubectl patch` ou
  `kubectl scale` manuel, l'apply suivant échouait (« field manager
  conflict ») au lieu de reprendre la main. Désormais Terraform reprend le
  champ (dérive corrigée, plan suivant sans écart).
- **pas de `wait { rollout = true }`** sur ces deux Deployments : avec lui,
  **chaque** changement d'image finissait en « Provider produced inconsistent
  result after apply » (annotation `deployment.kubernetes.io/revision`
  incrémentée pendant l'attente ; `computed_fields` ne suffit pas). L'attente
  et la vérification sont faites par `deploy/k3s/deploy-services.sh`.

### Gateway et Eureka

Profil `eureka` actif. Une route listée dans `gateway_eureka_routes`
(désormais `["assets", "telemetry", "simulator"]`) va vers
`lb://<spring.application.name>` ; les autres gardent leur adresse fixe
(`http://telemetry:8081`...). Tout est visible dans le ConfigMap
`gateway-routes` ; un changement redémarre la gateway (annotation
`aquaops.ma/routes-sha256`). Quand un service reçoit le client Eureka (PR de
son responsable), ajouter son nom à la liste.

### Sécurité des pods

Les 7 services : utilisateur non root vérifié (uid 999), racine en lecture
seule, `/tmp` en `emptyDir` (64 Mi), toutes les capacités retirées,
`allowPrivilegeEscalation: false`, seccomp `RuntimeDefault`, aucun jeton
d'API ni variable `<SERVICE>_PORT`, sondes `startup`/`liveness`/`readiness`
sur `/actuator/health/*`, mise à jour sans coupure (`maxUnavailable: 0`),
`preStop` de 5 s, arrêt gracieux de 20 s (`terminationGracePeriodSeconds`
40). Seule la gateway porte `aquaops.ma/ingress=true` (joignable par Traefik).

### Images : immuables, sans jeton dans le cluster

- Tag = SHA du commit (`services_image_tag`, validé : jamais `latest`).
- `localhost/aquaops/...` + `imagePullPolicy: Never` : images **importées**
  après analyse ; k3s ne télécharge jamais une image homonyme.
- Registre (`ghcr.io/fatii-elb/aquaops`) : Terraform **exige** l'empreinte
  `sha256` des 7 images (`services_image_digests`), l'image exécutée est
  octet pour octet celle analysée.
- Les paquets GHCR sont **privés** (vérifié : tirage anonyme refusé, 403) :
  `deploy/k3s/import-images.sh` les tire avec le jeton du job (stdin, jamais
  sur disque), vérifie l'étiquette `org.opencontainers.image.revision`, puis
  les importe dans k3s. Aucun jeton GHCR n'est stocké dans Kubernetes.

Construction manuelle (bac à sable ou VM, hors heures de test) :

```bash
TAG=$(git rev-parse --short HEAD)          # = services_image_tag
for pair in gateway:gateway telemetry:telemetry-service assets:assets-service \
            detection:detection-service incidents:incidents-service \
            notifications:notifications-service simulator:simulator; do
  name=${pair%%:*}; dir=${pair#*:}
  docker build -f deploy/docker/spring-service.Dockerfile \
    --label org.opencontainers.image.revision=$TAG -t localhost/aquaops/$name:$TAG services/$dir
  docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:0.76.2 image \
    --severity CRITICAL --ignore-unfixed --exit-code 1 localhost/aquaops/$name:$TAG || break
done
AQUAOPS_IMAGE_SOURCE=local sudo -E deploy/k3s/import-images.sh $TAG
```

### Ce qui a été vérifié (bac à sable k3s v1.37.2, jamais sur la VM)

Bac à sable : k3s v1.37.2+k3s1 (même version que la VM) dans un conteneur
Docker sur le poste de E5, chiffrement des secrets activé, état Terraform et
kubeconfig hors du dépôt, secrets de test générés au hasard, `tf.sh` exécuté
sans modification.

| Test | Résultat |
| --- | --- |
| Plan `test` (`services_enabled = false`) | 10 objets de fondation seulement, aucun service ni infrastructure |
| Plan `rc11` | 39 objets ; **aucune valeur secrète** (en clair ou base64) dans le plan |
| 8 images construites depuis `main` (`e02eda3`), Trivy CRITICAL (mêmes options que le pipeline) | 0 CRITICAL ; 8 à 13 HIGH par image (`docs/security-scans.md`) |
| Création de `rc11` | 39 objets en 296 s (premier téléchargement des images d'infrastructure inclus) ; 13 pods Ready, 0 redémarrage |
| Sécurité des pods | les 13 pods non root ; les 7 services et Eureka en uid 999 et racine en lecture seule, y compris assets et telemetry de E2 |
| Gateway | `UP` ; `/actuator/gateway` 404 (health seul) ; chemin inconnu 404 |
| Routage par la gateway | incidents 200, notifications/list 200, simulator 200, telemetry 200 (400 sans paramètres) ; detection : 404 du service, **délai dépassé** quand detection est à 0 réplica (la route va bien vers detection) ; assets via **Eureka** : 401 sans jeton, 401 faux jeton, **403** avec un vrai jeton Keycloak sans rôle (issuer `http://keycloak:8180/realms/aquaops`) |
| Eureka | `GATEWAY` (1) et `ASSETS-SERVICE` (2) inscrits |
| Flux de données | simulator → Kafka → telemetry → PostgreSQL : +147 mesures en 20 s |
| **RC-05** (script de E2 `deploy/demo/rc05.sh` inchangé, 200 mesures/s pendant 180 s, suppression du pod **consommateur**) | **0 perte** : 36 000 envoyées = 36 000 stockées ; jamais moins d'un pod prêt ; retard Kafka revenu à 0 ; **pod de remplacement prêt en 68 s : au-dessus des 60 s → écart** (36 s au repos) |
| Dérive | `kubectl patch` manuel corrigé par l'apply suivant (après correctif `force_conflicts`), plan suivant sans écart |

Les tests ci-dessus portent sur `e02eda3`. Après la PR #116 (detection avec
PostgreSQL et JWT), refaits sur `01434e2` le 17/11/2026 :

| Test | Résultat |
| --- | --- |
| 8 images de `01434e2`, Trivy CRITICAL | 0 CRITICAL ; 8 à 13 HIGH par image (mêmes nombres qu'avant) |
| Plan `rc11` sur l'environnement existant | 1 objet ajouté (le Job), 7 services mis à jour ; aucune valeur secrète dans le plan |
| **Volume existant** (créé la veille, 4 bases) | Job : `aquaops_detection` **créée**, les 4 autres « exists » ; déploiement vérifié (code 0, 489 s) |
| **Volume neuf** (RC-11 ci-dessous) | script d'initialisation : 4 bases ; Job : `aquaops_detection` créée |
| detection | Ready, 0 redémarrage ; pool PostgreSQL démarré ; table `anomalies` créée par Hibernate dans `aquaops_detection` ; 277 Mi |
| JWT detection, par la gateway (`/api/detection/anomalies`) | sans jeton **401** ; faux jeton **401** ; vrai jeton sans rôle **403** ; vrai jeton avec `operateur` **200** (liste paginée) ; rôle retiré **403**. Pour le 200 : le client `aquaops-audit` émet des jetons allégés (sans rôles, choix du realm de E2) ; jetons complets et rôle activés le temps du test dans le Keycloak du bac à sable, puis remis comme avant |
| Retour arrière (script de la branche, vérification forcée en échec) | retour automatique à `01434e2`, 7 services vérifiés, code 1 ; plan suivant sans écart |
| **RC-11** | destruction 54 s (40 objets, namespace et volumes supprimés) ; reconstruction 294 s, Deployments disponibles à **310 s**, 13 pods sans redémarrage, Job terminé ; plan suivant sans écart ; namespace `test` inchangé (même UID, mêmes objets) ; detection de nouveau vérifiée (401/401/403) |
| Namespace `rc11` au repos | 3,9 Gio mesurés ; quota : demandes 4800 / 5632 Mi, limites 8576 Mi / 10 Gi |

## 9. Déployer, vérifier, revenir en arrière (NFR-09)

```bash
# Sur l'hôte k3s, root (import dans containerd), secrets de l'environnement chargés
export KUBE_CONFIG_PATH=~/.kube/aquaops-k3s.yaml
set -a; source <(sudo cat /etc/aquaops-terraform/rc11.env); set +a
printf '%s\n%s\n' "$TOKEN" "$USER_GITHUB" | deploy/k3s/import-images.sh <sha complet>
deploy/k3s/deploy-services.sh rc11 <sha> [<heure du push, epoch>]
unset "${!TF_VAR_@}"
```

`deploy-services.sh` :
1. refuse un environnement non autorisé (`AQUAOPS_K3S_DEPLOY_ENVS`, `rc11`
   par défaut) ou dont le `.tfvars` n'active pas les services ;
2. vérifie que les 7 images sont dans k3s (si le socket containerd est
   accessible) ;
3. retient le SHA déployé (`tf.sh ... output services_image_tag`) ;
4. `tf.sh apply -parallelism=1 -var services_image_tag=<sha>` ;
5. vérifie : 7 rollouts terminés, tous les pods prêts **et** sur la nouvelle
   image (les pods en arrêt sont ignorés), gateway `UP`, et chaque route
   atteint son service (`/api/incidents` 200, `/api/notifications/list` 200,
   `/api/simulator/scenarios` 200, `/api/telemetry/readings` 400 sans
   paramètres, `/api/assets/zones` 401 sans jeton via Eureka,
   `/api/detection/anomalies` 401 sans jeton) ;
6. échec : **retour automatique** au SHA retenu, vérifié de la même façon ;
7. succès : durée push → sain (NFR-09) affichée, ajoutée au résumé du job
   GitHub et à `k3s-deployments.jsonl` (une ligne JSON par déploiement).

Codes : 0 déployé et sain · 1 échec, version précédente restaurée et saine ·
2 refusé, rien changé · 3 échec **non** restauré.

**Retour arrière manuel** (code 3, ou retour volontaire) : relancer
`deploy-services.sh rc11 <sha précédent>` (dans `k3s-deployments.jsonl`). Ne
pas utiliser `kubectl rollout undo` : Terraform remettrait sa version au
prochain apply.

Le tag passé par `-var` l'emporte sur `envs/rc11.tfvars`, qui ne sert que de
valeur de départ : un `tf.sh ... plan` à la main doit recevoir le même
`-var services_image_tag=<sha déployé>`, sinon il propose de revenir à la
valeur du fichier.

Mesuré dans le bac à sable (`AQUAOPS_ROLLOUT_TIMEOUT=240s`, heure du push
simulée 60 s avant le lancement) :

| Scénario | Résultat | Durée |
| --- | --- | --- |
| `1111111` → `e02eda3` | code 0, 7 services vérifiés | 88 s |
| `e02eda3` → `1111111` (7 nouvelles images, mises à jour une à une) | code 0, 7 services vérifiés | **366 s** (push → sain : 426 s) |
| `1111111` → `2222222` (image du simulateur volontairement cassée) | échec du rollout du simulateur, **retour automatique** à `1111111`, 7 services vérifiés, code 1 | 757 s |
| Plan après ces déploiements (`-var services_image_tag=1111111`) | aucun écart | |

Avant correctifs, le même scénario avait révélé trois défauts, corrigés et
retestés : erreur du provider à chaque mise à jour des Deployments de E2
(§ 8), pods en arrêt comptés comme « ancienne image » par la vérification,
et retour arrière bloqué par le quota (place pour un seul pod de plus :
porté à deux, § 8).

## 10. Pipeline k3s (préparé, désactivé)

| Fichier | Rôle | État |
| --- | --- | --- |
| `.github/workflows/security.yml` | SCA + IaC (`docs/security-scans.md`) | prêt ; actif dès la fusion (PR et `main`) ; `sca-owasp` échoue tant que le secret `NVD_API_KEY` manque |
| `.github/workflows/k3s-deploy.yml` | déploiement d'un commit de `main` dans k3s | **désactivé** : déclenchement manuel seulement, variable `K3S_DEPLOY` absente = refus ; déclenchement automatique écrit en commentaire |
| `deploy/k3s/import-images.sh`, `deploy-services.sh` | logique de déploiement, vérification, retour arrière | testés dans le bac à sable (§ 9) |
| `deploy/vm/aquaops-k3s-deploy` (+ règle sudoers) | wrapper privilégié appelé par le job | **à écrire** (sur le modèle de `deploy/vm/aquaops-deploy`) |

Modèle de confiance, identique au déploiement Compose du Sprint 4 :
- aucun déclencheur `pull_request` ni `pull_request_target` ; le job
  `policy` (hébergé par GitHub, sans secret) refuse toute autre branche que
  `main`, un SHA absent de `main`, ou un SHA sans exécution « Images »
  réussie (CI, Gitleaks, Trivy, publication GHCR) ;
- le job `deploy` (runner auto-hébergé **dédié**, étiquette `aquaops-k3s`,
  environnement GitHub `k3s-rc11` avec approbation) ne fait **aucun
  checkout** : il passe SHA, environnement et heure du push au wrapper
  installé par l'administrateur, le jeton du job (`packages: read`) passe
  par stdin ;
- le wrapper revérifie tout (SHA sur `main`, lecture des fichiers de ce
  commit), charge `/etc/aquaops-terraform/<env>.env` (root seulement) et
  lance les deux scripts.

**Dépôt public** : un runner auto-hébergé enregistré sur un dépôt public peut
être ciblé par un workflow modifié dans une PR de fork. Garder « Require
approval for all outside collaborators » (Settings > Actions), ne démarrer le
runner que pendant une fenêtre de déploiement, et ne jamais lui donner de
label partagé avec un autre usage.

Activer (après revue et accord) : installer le wrapper, créer l'environnement
`k3s-rc11` avec relecteur obligatoire, enregistrer le runner, puis
`K3S_DEPLOY=on` ; plus tard seulement, décommenter `workflow_run`.

## 11. Limites connues

- **Mémoire de la VM** : `rc11` complet demande ~4,7 Gi (requests) ; la VM
  de 8 Go ne réserve que ~2,2 Gi à k3s tant que Compose tourne
  (`deploy/k3s/README.md`). Le bac à sable a servi à toutes les mesures.
- **RC-05** (§ 8) : écart sur le temps de retour du pod sous charge ; à
  traiter avec E2.
- Le topic `sensor-readings` a **une** partition : un seul pod de télémétrie
  consomme, le second attend. Le HPA de E1 (2 à 6 pods) n'ajoutera de débit
  qu'avec plus de partitions (`KAFKA_NUM_PARTITIONS`, décision d'équipe).
- Eureka n'est pas publié par le workflow Images : son image reste à
  importer à la main (§ 7).
- Keycloak en `start-dev` (test seulement) ; le realm exporté contient les mots
  de passe en clair des 4 utilisateurs de test : dépôt public, ne jamais
  exposer cet environnement sur Internet.
- Le frontend et l'Ingress TLS de E2 ne sont pas encore dans Terraform.
- Le client `aquaops-audit` du realm émet des jetons **allégés** (sans rôles) :
  il ne peut pas servir à tester un accès autorisé. Un jeton d'utilisateur
  (client `aquaops-frontend`) porte bien `realm_access.roles`.
- La pile Compose du Sprint 4 (`deploy/test/compose.yml`) ne donne pas encore
  à detection sa base ni l'émetteur Keycloak exigés depuis la PR #116 : PR
  séparée (`feature/SCRUM-55-detection-compose`), à fusionner avant tout
  nouveau déploiement sur la VM.

## 12. Responsabilités et dépendances

| Qui | Quoi | Dépendance pour SCRUM-56 |
| --- | --- | --- |
| E1 | HPA de la télémétrie (NFR-04), clients Eureka de telemetry et simulator | le HPA se règle sur le Deployment `telemetry` de E2 ; les deux routes sont configurées dans `gateway_eureka_routes` |
| E2 | manifestes `deploy/k8s/{assets,telemetry,tls}`, RC-05, RC-07, realm Keycloak | réutilisés sans copie ; proposer la surcouche de sécurité dans leurs fichiers ; temps de démarrage (RC-05) |
| E3 | Gitleaks (`gitleaks.yml`, `.gitleaks.toml`), detection-service | réutilisés tels quels par le pipeline |
| E4 | frontend (image Angular), RC-10 | PR « Frontend + Ingress » |
| E6 | incidents et notifications (machine à états, consommateur `incident-events`) | montée de version Spring Boot (HIGH SCA) |
| Tous | `pom.xml` de leur service | corriger les 14 HIGH SCA (`docs/security-scans.md`) ; client Eureka |
| E5 | Terraform, scripts, pipeline, analyses | ce document |
