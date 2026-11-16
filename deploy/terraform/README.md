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
    ├── envs/test.tfvars  namespace "aquaops" (celui des manifestes de E2), durable, infrastructure désactivée
    └── envs/rc11.tfvars  namespace "aquaops-rc11", jetable (RC-11), infrastructure activée
```

## 1. Ce que crée chaque environnement (fondation)

| Objet | Nom | Rôle |
| --- | --- | --- |
| Namespace | `aquaops` / `aquaops-rc11` | Pod Security `baseline` imposé, `restricted` signalé |
| ResourceQuota | `aquaops-quota` | Plafond du namespace (mémoire, CPU, pods, volumes) ; ni NodePort ni LoadBalancer |
| LimitRange | `aquaops-limits` | Valeurs par défaut et maximum par conteneur |
| NetworkPolicy | `default-deny-ingress`, `allow-same-namespace`, `allow-ingress-controller` | Rien n'entre sauf depuis le même namespace ; Traefik n'atteint que les pods `aquaops.ma/ingress=true` |
| ConfigMap | `aquaops-config` | Adresses internes communes (`DB_HOST`, `KAFKA_BOOTSTRAP_SERVERS`, `EUREKA_URL`, `KEYCLOAK_ISSUER_URI`…), aucun secret |
| Secret | `aquaops-db` | `DB_USER`, `DB_PASSWORD` (nom attendu par `deploy/k8s/assets`) |
| Secret | `aquaops-keycloak` | `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD`, `KEYCLOAK_AUDIT_CLIENT_SECRET` |
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

Dans les deux cas, le `plan` suivant ne trouve aucun écart, et `test` est
inchangé (même identifiant, mêmes objets). Les services (PR suivante)
s'ajouteront au même cycle ; ce sera alors la preuve RC-11 complète (+ pipeline).

## 5. Validations (sans cluster)

```bash
cd deploy/terraform
terraform fmt -check -recursive
for s in cluster environment; do terraform -chdir=$s init -backend=false && terraform -chdir=$s validate; done
docker run --rm -v "$PWD/../..:/mnt:ro" -w /mnt koalaman/shellcheck:v0.11.0 deploy/terraform/tf.sh deploy/k3s/preflight.sh
docker run --rm -v "$PWD/../..:/src:ro" aquasec/trivy:latest config --skip-dirs '**/.terraform' /src/deploy/terraform
kubeconform -strict -summary deploy/k8s/assets      # manifestes de E2
```

`.terraform.lock.hcl` est versionné : sommes de contrôle des providers pour
Linux et Windows (`terraform providers lock -platform=linux_amd64 -platform=windows_amd64`).

## 6. Prochaines PRs (même pile `environment`, mêmes noms)

| PR | Contenu | Utilise |
| --- | --- | --- |
| Infrastructure | **Faite** : § 7 | `aquaops-db`, `aquaops-keycloak`, quotas |
| Services | Les 7 services (images importées comme Eureka, puis `ghcr.io/...:<SHA>` quand Images reviendra), sondes Actuator, gateway et frontend avec `aquaops.ma/ingress=true` ; reprise des manifestes de E2 ; quota de `rc11` à relever | `aquaops-config` |
| Pipeline | Job de déploiement k3s après Images, même modèle de compte dédié que `aquaops-deploy` ; quand le quota GitHub Actions le permet | `tf.sh` |

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
| **Total** | | | | **1,6 / 2,6 Gi** (quota `rc11` : 2 / 3 Gi) | **1,27 à 1,31 Gi** |

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

Le test E2 a demandé un quota plus grand (passé par `-var`, `rc11.tfvars`
inchangé) : infrastructure + 1 réplica d'assets = 3,4 Gi de limites, au-delà
des 3 Gi de `rc11`. À relever dans la PR « Services ».

Point d'attention : avec `default-deny-ingress`, la **toute première**
connexion d'un pod qui vient de démarrer peut être refusée (le contrôleur de
NetworkPolicy de k3s met moins d'une seconde à l'autoriser). Les services
Spring, qui démarrent en plus de 10 s, ne sont pas concernés ; un script de
test doit réessayer.
