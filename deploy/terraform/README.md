# Terraform pour k3s (SCRUM-56 / NFR-09)

Carte SCRUM-56 · E5. Décrit **tout ce que contient le cluster k3s** de la VM de
test (`deploy/k3s/`), avec les providers **Kubernetes 3.5** et **Helm 3.3**
(Terraform ≥ 1.11, testé avec 1.17.2).

```text
deploy/terraform/
├── tf.sh                 seul point d'entrée : contrôles, état, workspace, garde-fous
├── cluster/              une fois par cluster : cert-manager (Helm), jamais détruit par RC-11
└── environment/          une fois par environnement = un namespace
    ├── envs/test.tfvars  namespace "aquaops" (celui des manifestes de E2), durable
    └── envs/rc11.tfvars  namespace "aquaops-rc11", jetable (RC-11)
```

## 1. Ce que crée chaque environnement (cette PR)

| Objet | Nom | Rôle |
| --- | --- | --- |
| Namespace | `aquaops` / `aquaops-rc11` | Pod Security `baseline` imposé, `restricted` signalé |
| ResourceQuota | `aquaops-quota` | Plafond du namespace (mémoire, CPU, pods, volumes) ; ni NodePort ni LoadBalancer |
| LimitRange | `aquaops-limits` | Valeurs par défaut et maximum par conteneur |
| NetworkPolicy | `default-deny-ingress`, `allow-same-namespace`, `allow-ingress-controller` | Rien n'entre sauf depuis le même namespace ; Traefik n'atteint que les pods `aquaops.ma/ingress=true` |
| ConfigMap | `aquaops-config` | Adresses internes communes (`DB_HOST`, `KAFKA_BOOTSTRAP_SERVERS`, `EUREKA_URL`, `KEYCLOAK_ISSUER_URI`…), aucun secret |
| Secret | `aquaops-db` | `DB_USER`, `DB_PASSWORD` (nom attendu par `deploy/k8s/assets`) |
| Secret | `aquaops-keycloak` | `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD`, `KEYCLOAK_AUDIT_CLIENT_SECRET` |
| Secret | `ghcr-pull` | Accès en lecture aux images privées `ghcr.io/fatii-elb/aquaops/*` |
| ServiceAccount | `default` | Utilise `ghcr-pull` pour tous les pods ; aucun jeton d'API monté par défaut |

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
- **Jeton GHCR** : jeton *classic* avec **uniquement** `read:packages` (GHCR
  n'accepte pas les jetons *fine-grained*), date d'expiration courte, créé pour
  cet usage seulement. Le jeton éphémère `GITHUB_TOKEN` du pipeline ne
  convient pas : il expire à la fin du job.

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
#   TF_VAR_ghcr_username=...
#   TF_VAR_ghcr_token=...
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

**Mesuré dans le bac à sable** (couche « fondation » seule) : destruction en 9 s
sans aucun secret chargé, puis reconstruction en 6 s, soit 22 s en tout. Le
`plan` suivant ne trouve aucun écart, et `test` est inchangé (même identifiant,
mêmes objets). Le même cycle couvrira l'infrastructure et les services quand
les PRs suivantes les ajouteront à cette pile ; ce sera alors la preuve RC-11
complète (+ pipeline).

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
| Infrastructure | PostgreSQL (StatefulSet + PVC + scripts de `deploy/integration/postgres-init`), Kafka KRaft, Keycloak (import du realm de `docs/keycloak`), Eureka : Services nommés `postgres`, `kafka`, `keycloak`, `eureka` | `aquaops-db`, `aquaops-keycloak`, quotas |
| Services | Les 7 services (images `ghcr.io/fatii-elb/aquaops/<service>:<SHA>`, variable `image_tag`), sondes Actuator, gateway et frontend avec `aquaops.ma/ingress=true` ; reprise des manifestes de E2 | `aquaops-config`, `ghcr-pull` |
| Pipeline | Job de déploiement k3s après Images, même modèle de compte dédié que `aquaops-deploy` ; quand le quota GitHub Actions le permet | `tf.sh` |

À l'arrivée de la pile complète, le budget mémoire de `deploy/k3s/README.md`
(§ 2) impose un choix : retirer Compose, ajouter une seconde VM, ou travailler
par fenêtres de maintenance.
