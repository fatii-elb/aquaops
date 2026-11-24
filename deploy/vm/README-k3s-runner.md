# Déploiement k3s par GitHub Actions : hôte, runner et wrapper (SCRUM-56 / NFR-09, E5)

Ce guide installe un **hôte k3s dédié** capable de recevoir le workflow
`.github/workflows/k3s-deploy.yml`. Rien ici ne concerne la VM de test du
Sprint 4 (Docker Compose, `deploy/README.md` § 7) : **ne pas installer k3s à
côté de la pile Compose sur la VM de 8 Go** (mémoire insuffisante, voir § 1).

État au moment de la rédaction : workflow **manuel uniquement**
(`workflow_dispatch`), variable `K3S_DEPLOY` **non définie**, aucun runner
`aquaops-k3s` enregistré. Le déclenchement automatique reste commenté dans le
workflow jusqu'à décision de l'équipe.

```
GitHub (hébergé)                       Hôte k3s dédié
───────────────────────────────        ─────────────────────────────────────────────
job policy (ubuntu-24.04)              runner aquaops-k3s (compte aquaops-k3s-runner :
  K3S_DEPLOY = on ?                     ni sudo général, ni docker, ni secrets)
  ref = main ? SHA complet ?                │ printf '<jeton>\n<acteur>\n' |
  SHA présent sur main ?                    │ sudo -n /usr/local/sbin/aquaops-k3s-deploy rc11 <SHA> <epoch>
  run « Images » réussi pour ce SHA ?       ▼   (une seule règle sudoers)
        │                              aquaops-k3s-deploy (root, bash -p, environnement vidé)
        ▼                                1. arguments, jeton (stdin), chemins de confiance
job deploy (environnement protégé         2. /etc/aquaops-k3s/rc11.env : lignes TF_VAR_* lues, jamais « sourcées »
  k3s-rc11 : relecteur obligatoire)       3. verrou ; fetch de main dans un dépôt privé root ; SHA sur main
                                          4. ce commit prépare k3s et active les services de rc11
                                          5. export des fichiers de déploiement de CE commit
                                          6. import-images.sh (compte aquaops-k3s) : pull GHCR, label, archive
                                          7. root : k3s ctr images import <archive>
                                          8. deploy-services.sh (compte aquaops-k3s) : apply, vérification,
                                             retour arrière automatique, temps push → sain (NFR-09)
```

## 1. Ressources de l'hôte (mesurées)

Mesures sur le cluster jetable (k3s v1.37.2, namespace `aquaops-rc11`,
13 pods) : voir `docs/preuves/RC-11/` et `docs/preuves/RC-05/`.

| Poste | Valeur |
| --- | --- |
| Demandes (`requests`) des 13 pods, télémétrie à 2 réplicas (PostgreSQL 512 Mi, simulateur 256 Mi) | 1 550m CPU, 5 120 Mi |
| Télémétrie à 6 réplicas (maximum du HPA de E1/E2) | + 800m CPU, + 1 536 Mi |
| Deux pods supplémentaires (mise à jour progressive + pod d'une version en échec) | + 400m CPU, + 768 Mi |
| Mémoire réellement utilisée par le nœud, tout le namespace actif | ≈ 5,3 Go (mesure `kubectl top node`) |
| k3s lui-même (serveur, containerd, CoreDNS, Traefik, metrics-server) et système | ≈ 1 à 1,5 Go |

**Décision proposée :** VM dédiée **Ubuntu 24.04, 8 vCPU, 16 Go de RAM,
40 Go de disque**, sans autre charge. Mesuré sur le cluster jetable (24/11/2026,
8 CPU, 16 Go) : pendant RC-04 (500 mesures/s) le nœud atteint environ 7 CPU et
8,2-8,4 Go ; une télémétrie bridée à 1 CPU et 200m garantis met 100-115 s à
démarrer sous charge (22-38 s avec 500m / 2 CPU, essai B). Une VM de 4 vCPU
suffit pour déployer et pour RC-05/RC-11, **pas** pour RC-04. La VM de 8 Go du
Sprint 4 garde la pile Compose ; k3s n'y est pas installé.

Le quota du namespace `rc11` (`deploy/terraform/environment/envs/rc11.tfvars`)
est dimensionné pour la télémétrie à 6 réplicas **plus** deux pods : avec
l'ancien quota, un déploiement **et son retour arrière** ont été refusés
(« exceeded quota », code 3, mesuré le 19/11/2026).

**PostgreSQL : `max_connections` = 200** (`postgres.tf`). Avec la valeur par
défaut (100), la télémétrie à 6 réplicas épuise les connexions (10 par pod) :
« too many clients already », pods de télémétrie en échec au démarrage
(mesuré le 19/11/2026).

## 2. Ce qui protège l'hôte

1. **Hôte dédié et jetable** : aucune donnée réelle, aucun autre service, aucun
   réseau de production. Reconstructible à partir de ce guide.
2. **Compte du runner sans droits** (`aquaops-k3s-runner`) : ni groupe `sudo`,
   ni groupe `docker`, ni lecture des secrets ou du kubeconfig.
3. **Une seule règle sudoers** (`deploy/vm/aquaops-k3s-deploy.sudoers`) :
   `rc11 <SHA de 40 caractères> <epoch>`, rien d'autre (vérifié avec le vrai
   `sudo` par `deploy/vm/tests/test-aquaops-k3s-boundary.sh`).
4. **Le wrapper revérifie tout** et ne déploie qu'un commit **de `main`**, avec
   des chemins, des comptes, un kubeconfig et un fichier de secrets fixés.
5. **Aucun code de PR sur l'hôte** : le workflow n'a ni `pull_request` ni
   `pull_request_target`, le job `deploy` ne fait pas de checkout, et le
   wrapper récupère lui-même `main`.
6. **Environnement GitHub protégé `k3s-rc11`** : approbation humaine avant
   chaque déploiement.

**Le groupe `docker` équivaut à `root`.** Le compte de service `aquaops-k3s`
en fait partie (pull des images) : la baisse de privilèges du wrapper évite
que Terraform, kubectl et Docker tournent en root, mais **ce n'est pas une
barrière contre un commit malveillant de `main`**. La frontière réelle est
« runner → sudo → wrapper » + relecture de tout ce qui entre dans `main`.

**Dépôt public : réglage obligatoire.** *Settings > Actions > General > Fork
pull request workflows from outside collaborators* : **« Require approval for
all outside collaborators »**. Sur un événement `pull_request`, GitHub exécute
le workflow **de la PR** : une PR de fork pourrait sinon demander
`runs-on: [self-hosted, aquaops-k3s]`. Le dépôt est personnel : pas de groupe
de runners pour filtrer davantage. **Démarrer le service du runner seulement
pendant une fenêtre de déploiement** (`sudo systemctl stop actions.runner.*`
le reste du temps).

## 3. Installation de l'hôte (administrateur)

Prérequis : `sudo -V | head -n 1` = sudo classique ≥ 1.9.10 (Ubuntu 24.04 :
1.9.15 ; **pas** sudo-rs, voir l'en-tête du fichier sudoers).

```bash
# 3.1 k3s (version validée sur le cluster jetable) et Docker (pull des images GHCR)
curl -sfL https://get.k3s.io | INSTALL_K3S_VERSION=v1.37.2+k3s1 sh -
sudo apt-get install -y docker.io git
sudo apt-get install -y terraform   # dépôt HashiCorp ; version validée : 1.17.2

# 3.2 Comptes
sudo useradd --create-home --shell /bin/bash aquaops-k3s-runner              # le runner : rien d'autre
sudo useradd --system --create-home --home-dir /var/lib/aquaops-k3s \
     --shell /usr/sbin/nologin --groups docker aquaops-k3s                    # compte de service
sudo chmod 700 /var/lib/aquaops-k3s
sudo install -d -o root -g root -m 700 /var/lib/aquaops-k3s-deploy           # verrou, dépôt privé

# 3.3 Configuration : kubeconfig (compte de service) et secrets (root seul)
sudo install -d -o root -g root -m 755 /etc/aquaops-k3s
sudo install -o aquaops-k3s -g aquaops-k3s -m 600 /etc/rancher/k3s/k3s.yaml /etc/aquaops-k3s/rc11.kubeconfig
sudo install -o root -g root -m 600 /dev/null /etc/aquaops-k3s/rc11.env
sudoedit /etc/aquaops-k3s/rc11.env      # contenu ci-dessous, valeurs PROPRES à cet hôte

# 3.4 Wrapper et règle, depuis une copie relue de main (jamais depuis une branche)
git -C ~/aquaops fetch origin && git -C ~/aquaops checkout --detach origin/main
sudo install -o root -g root -m 755 ~/aquaops/deploy/vm/aquaops-k3s-deploy /usr/local/sbin/aquaops-k3s-deploy
sudo visudo -cf ~/aquaops/deploy/vm/aquaops-k3s-deploy.sudoers
sudo install -o root -g root -m 440 ~/aquaops/deploy/vm/aquaops-k3s-deploy.sudoers /etc/sudoers.d/aquaops-k3s-deploy
sudo visudo -c
```

Contenu de `/etc/aquaops-k3s/rc11.env` : **uniquement** des lignes
`TF_VAR_<nom>=<valeur>` et des commentaires (le wrapper refuse tout le reste,
une valeur vide ou un modèle comme `change-me`). Valeurs générées pour cet
hôte, jamais réutilisées ailleurs :

```
TF_VAR_db_user=aquaops
TF_VAR_db_password=<aléatoire>
TF_VAR_keycloak_admin=<nom>
TF_VAR_keycloak_admin_password=<aléatoire>
TF_VAR_keycloak_audit_client_secret=<aléatoire>
TF_VAR_simulator_api_key=<aléatoire>
```

Générer une valeur : `openssl rand -base64 24 | tr -d '/+=' `.

| Chemin | Propriétaire | Mode | Rôle |
| --- | --- | --- | --- |
| `/usr/local/sbin/aquaops-k3s-deploy` | `root:root` | `755` | Le wrapper |
| `/etc/sudoers.d/aquaops-k3s-deploy` | `root:root` | `440` | La règle |
| `/etc/aquaops-k3s/` | `root:root` | `755` | Configuration |
| `/etc/aquaops-k3s/rc11.env` | `root:root` | `600` | Secrets Terraform (illisibles pour le runner et le compte de service) |
| `/etc/aquaops-k3s/rc11.kubeconfig` | `aquaops-k3s` | `600` | Accès au cluster |
| `/var/lib/aquaops-k3s-deploy/` | `root:root` | `700` | Verrou, dépôt git privé |
| `/var/lib/aquaops-k3s/` | `aquaops-k3s` | `700` | Fichiers exportés, état Terraform, historique NFR-09 |

## 4. Runner auto-hébergé dédié

Sur GitHub : *Settings > Actions > Runners > New self-hosted runner* (Linux
x64). Sur l'hôte, **en tant que `aquaops-k3s-runner`** :

```bash
sudo -iu aquaops-k3s-runner
mkdir actions-runner && cd actions-runner
# télécharger et vérifier l'archive comme indiqué par GitHub (sha256), puis :
./config.sh --url https://github.com/fatii-elb/aquaops --token <jeton d'enregistrement> \
  --name aquaops-k3s-1 --labels aquaops-k3s --no-default-labels --unattended
exit
cd /home/aquaops-k3s-runner/actions-runner && sudo ./svc.sh install aquaops-k3s-runner
```

`--no-default-labels` : un job qui demande seulement `self-hosted` n'atterrit
pas ici. Ce runner n'est **jamais** celui de la VM Compose (`aquaops-test`).

## 5. Réglages GitHub

| Réglage | Valeur |
| --- | --- |
| *Settings > Environments > New* : `k3s-rc11` | **Required reviewers** : au moins une autre personne de l'équipe ; *Prevent self-review* coché ; *Deployment branches* : **Selected branches → `main`** |
| *Settings > Actions > General* | Fork PR workflows : **Require approval for all outside collaborators** |
| *Settings > Secrets and variables > Actions > Variables* | `K3S_DEPLOY` = `on` **seulement** au moment de la première campagne ; le supprimer ensuite |
| Aucun secret supplémentaire | le job utilise le `GITHUB_TOKEN` (`contents: read`, `packages: read`) |

État relevé le 24/11/2026 (API publique, lecture seule) : un seul environnement,
`test`, **sans aucune règle de protection** (ni relecteur, ni branche) ;
`k3s-rc11` **n'existe pas encore**. Les secrets de l'hôte (`TF_VAR_*`, § 3.3)
ne vont **jamais** dans GitHub : ils restent dans `/etc/aquaops-k3s/rc11.env`.

## 6. Vérifications avant le premier déploiement (lecture seule)

```bash
id aquaops-k3s-runner            # ni sudo, ni docker
id aquaops-k3s                   # groupe docker
sudo -l -U aquaops-k3s-runner    # une seule règle : (root) NOPASSWD: /usr/local/sbin/aquaops-k3s-deploy ^rc11 ...
sudo -u aquaops-k3s-runner sudo -n true                                   # refusé
sudo -u aquaops-k3s-runner head -c1 /etc/aquaops-k3s/rc11.env             # refusé
sudo -u aquaops-k3s-runner docker ps                                      # refusé
```

Mode `check` (administrateur ; la règle ne l'autorise pas au runner) : toutes
les vérifications, cluster, Terraform, Docker et `k3s` joignables, **rien
n'est modifié**. Le dépôt est public : n'importe quel jeton valide suffit pour
lire `main` ; un jeton *fine-grained* temporaire (lecture seule, 1 jour).

```bash
read -rs GH_TOKEN
printf '%s\n%s\n' "$GH_TOKEN" "<compte GitHub>" \
  | sudo /usr/local/sbin/aquaops-k3s-deploy check rc11 "$(git -C ~/aquaops rev-parse origin/main)"
unset GH_TOKEN
```

### 6.1 Images nécessaires sur l'hôte

- Les 7 services : `ghcr.io/fatii-elb/aquaops/<service>:<SHA complet>`, publiés
  par le workflow **Images** sur chaque push de `main` (Trivy bloquant ;
  `DEPLOY_TEST` reste `off`). Vérifier que le run Images du SHA est vert.
- Eureka : `rc11.tfvars` fixe `eureka_image = localhost/aquaops/eureka-server:b496543`,
  une image **importée à la main** (pas tirée de GHCR). À faire une fois, par
  l'administrateur, depuis une copie relue du dépôt :

```bash
git -C ~/aquaops worktree add /tmp/eureka-b496543 b496543
docker build -f ~/aquaops/deploy/docker/spring-service.Dockerfile \
  -t localhost/aquaops/eureka-server:b496543 /tmp/eureka-b496543/services/eureka-server
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy:0.76.2 image --severity CRITICAL --exit-code 1 localhost/aquaops/eureka-server:b496543
docker save localhost/aquaops/eureka-server:b496543 | sudo k3s ctr images import -
```

  (Quand l'image Eureka sera publiée par Images, branche
  `feature/TECH-eureka-image-publish`, `eureka_image` pourra suivre le même
  chemin que les 7 services.)
- Infrastructure (PostgreSQL, Kafka, Keycloak) : images publiques épinglées
  dans `rc11.tfvars`, tirées par k3s.

### 6.2 Ordre quota / CPU de la télémétrie

Le quota `rc11` (5 200m / 29) doit être **déployé avant** la télémétrie à
500m / 2 CPU (branche E2). Pour le baisser plus tard : réduire d'abord les
pods, baisser le quota dans un second déploiement (sinon les nouveaux pods sont
refusés : `docs/preuves/RC-04/expBK-retablissement.txt`).

## 7. Premier déploiement et mesure NFR-09

1. Choisir un commit de `main` dont le workflow **Images** a réussi (images
   publiées sur GHCR, Trivy bloquant), et dont toutes les images du § 6.1 sont
   présentes.
2. Définir `K3S_DEPLOY=on`, démarrer le service du runner.
3. *Actions > k3s deploy > Run workflow* : branche `main`, environnement
   `rc11`, SHA complet. Approuver dans l'environnement `k3s-rc11`.
4. Le résumé du job contient la ligne JSON de `deploy-services.sh` :
   `push_to_healthy_s` (NFR-09 : de l'heure du commit à « tout est sain »),
   `deploy_s` (durée du déploiement seul) et `status`
   (`deployed`, `rolled_back`, `failed`). Historique complet :
   `/var/lib/aquaops-k3s/state/k3s-deployments.jsonl`.
5. Archiver dans `docs/preuves/NFR-09/` (voir la liste ci-dessous), supprimer
   `K3S_DEPLOY`, arrêter le runner.

**Preuves à archiver** (`docs/preuves/NFR-09/hote-dedie-<date>.txt`, sans
aucun secret ni jeton) :

```bash
# sur l'hôte, après le job (administrateur)
nproc; free -g; df -h /var/lib/rancher; k3s --version
tail -n 3 /var/lib/aquaops-k3s/state/k3s-deployments.jsonl
kubectl -n aquaops-rc11 get deploy,sts,hpa,quota -o wide
kubectl -n aquaops-rc11 get pods -o custom-columns=POD:.metadata.name,READY:.status.containerStatuses[0].ready,IMAGE:.spec.containers[0].image
kubectl -n aquaops-rc11 describe quota
```

Plus : l'URL du run *k3s deploy*, l'heure d'approbation dans `k3s-rc11` (à
retrancher de `push_to_healthy_s` pour le temps machine), la ligne JSON du
résumé du job. Un résultat du bac à sable **n'est pas** une preuve NFR-09.

**Méthode NFR-09.** `pushed_at` = heure du commit sur `main` (fournie par le
job `policy`) ; « sain » = les 7 Deployments déployés, chaque pod prêt **et**
sur l'image attendue, gateway `UP`, et chaque route `/api` de la gateway
atteint son service (codes attendus dans `deploy-services.sh`). La mesure
inclut la CI, le workflow Images (build, Trivy, publication), l'approbation
humaine et le déploiement : l'attente de l'approbation est à noter à part.

## 8. Retour arrière et dépannage

| Situation | Effet | Action |
| --- | --- | --- |
| Code 1 | Échec, version précédente **restaurée et vérifiée** | Lire le job ; corriger sur `main` |
| Code 2 | Refusé, **rien n'a changé** (arguments, droits, commit, verrou) | Lire la ligne `REFUSED:` |
| Code 3 | Échec **sans** restauration | Agir : `kubectl -n aquaops-rc11 get pods`, puis relancer un SHA connu sain |
| `image import failed` | Image absente de GHCR ou label différent : cluster intact | Vérifier le run Images du SHA |
| `another deployment is running` | Verrou pris | Attendre la fin du déploiement en cours |
| Changement manuel (`kubectl patch`) | Repris au déploiement suivant (`force_conflicts`) | — |

Désactiver tout : `sudo rm /etc/sudoers.d/aquaops-k3s-deploy` et
`sudo ./svc.sh stop` dans le dossier du runner.

## 9. Tests (sans hôte k3s)

```bash
bash deploy/vm/tests/test-aquaops-k3s-deploy.sh        # Linux/WSL, sans root : 187 tests
# frontière de privilèges avec le vrai sudo, UNIQUEMENT dans un conteneur jetable :
docker run --rm -v "$PWD:/src:ro" ubuntu:24.04 bash -c \
  'apt-get update -qq && apt-get install -y -qq git sudo ca-certificates >/dev/null \
   && bash /src/deploy/vm/tests/test-aquaops-k3s-boundary.sh'
```
