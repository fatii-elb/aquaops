# k3s sur la VM de test (SCRUM-56 / NFR-09)

Carte SCRUM-56 · E5. Cluster k3s **à nœud unique** sur la VM de test Ubuntu 24.04
(8 Go, 4 CPU, 60 Go), **à côté** de la pile Docker Compose du Sprint 4, qui doit
continuer à tourner. Terraform (`deploy/terraform/`) crée ensuite le contenu du
cluster.

| Fichier | Rôle |
| --- | --- |
| `config.yaml` | Configuration du serveur k3s, copiée en `/etc/rancher/k3s/config.yaml` **avant** l'installation |
| `preflight.sh` | Vérifications **en lecture seule** avant l'installation (ne modifie rien) |

## 1. Architecture

- k3s **v1.37.2+k3s1** (canal stable), un seul nœud, base SQLite intégrée
  (plus légère qu'etcd sur 8 Go).
- Gardés : Traefik (Ingress HTTPS de E2, NFR-14), ServiceLB, metrics-server
  (autoscaling, RC-04), local-path (volumes PostgreSQL et Kafka).
- k3s utilise **son propre containerd** : il ne touche ni au démon Docker, ni
  aux conteneurs, ni aux volumes Compose.
- Secrets Kubernetes **chiffrés au repos** (`secrets-encryption`).
- kubeconfig en `0600`, lisible par root seulement.

## 2. Coexistence avec Docker Compose

| Sujet | Docker Compose (Sprint 4) | k3s | Conflit ? |
| --- | --- | --- | --- |
| Ports TCP | 127.0.0.1:8080 (gateway), 8180 (Keycloak) | 6443 (API), 10250 (kubelet), 80 et 443 (Traefik) | Non. `preflight.sh` vérifie qu'ils sont libres |
| Port UDP | - | 8472 (flannel, inutile avec un seul nœud mais ouvert) | Non |
| Réseaux | 172.17.0.0/16 et suivants (Docker) | pods 10.42.0.0/16, services 10.43.0.0/16 | Non. `preflight.sh` vérifie les réseaux Docker et les routes |
| Stockage | `/var/lib/docker` (volumes `aquaops-test_*`) | `/var/lib/rancher/k3s` (images, volumes local-path) | Non, dossiers séparés. Disque partagé : nettoyage d'images dès 75 % |
| Mémoire | 7 JVM + Kafka + Keycloak + PostgreSQL | k3s environ 600 à 800 Mo + pods | **Oui** : voir le budget ci-dessous |
| iptables | règles `DOCKER*` | règles `KUBE-*`, `CNI-*`, `FLANNEL*` | Cohabitent ; la désinstallation de k3s ne retire que les siennes |

### Budget mémoire (8 Gio)

Le kubelet ne voit pas les conteneurs Docker. `config.yaml` lui réserve donc :

| Réservé | Quantité |
| --- | --- |
| OS + pile Compose (`system-reserved`) | 5 Gio |
| k3s lui-même (`kube-reserved`) | 512 Mio |
| Seuil d'éviction | 300 Mio |
| **Reste pour les pods (allocatable)** | **environ 2,2 Gio** (mesuré dans le bac à sable : 5,8 Gio retirés de la capacité) |

C'est assez pour cert-manager et la base de chaque environnement (namespace,
quotas, secrets). Ce n'est **pas** assez pour la pile complète dans k3s : 7
services, PostgreSQL, Kafka, Keycloak et Eureka demandent environ 5 à 6 Gio
(estimation à partir des limites actuelles). Avant de la déployer dans k3s, il
faudra choisir entre :

1. **Retirer la pile Compose** après validation de k3s : baisser
   `system-reserved` à `memory=1Gi`, puis redémarrer k3s ;
2. **Une seconde VM** pour k3s, si l'hôte VirtualBox a la mémoire ;
3. **Une fenêtre de maintenance** : `docker compose stop`, jamais `down -v`.
   Les volumes sont conservés.

## 3. Installation (administrateur de la VM, plus tard)

À faire depuis votre copie du dépôt sur la VM, sur la branche fusionnée.

```bash
# 0. Point de retour : instantané VirtualBox de la VM, depuis l'HÔTE
#    (VirtualBox > Instantanés, ou VBoxManage snapshot "<nom de la VM>" take avant-k3s --live)

# 1. Vérifications (lecture seule). Avec sudo pour contrôler Docker.
sudo bash deploy/k3s/preflight.sh            # attendu : 0 blocker(s)
docker ps --format '{{.Names}} {{.Status}}' | sort > ~/avant-k3s-conteneurs.txt
free -m > ~/avant-k3s-memoire.txt

# 2. Configuration AVANT l'installation (root:root, 600)
sudo install -d -m 755 /etc/rancher/k3s
sudo install -m 600 -o root -g root deploy/k3s/config.yaml /etc/rancher/k3s/config.yaml

# 3. Script officiel téléchargé, relu, puis exécuté avec la version fixée.
#    Il vérifie lui-même la somme SHA-256 du binaire k3s.
curl -sfL https://get.k3s.io -o /tmp/k3s-install.sh
less /tmp/k3s-install.sh
sudo INSTALL_K3S_VERSION='v1.37.2+k3s1' sh /tmp/k3s-install.sh

# 4. Contrôles
sudo k3s kubectl get nodes                   # 1 nœud Ready
sudo k3s kubectl get pods -A                 # tout en Running ou Completed
sudo k3s secrets-encrypt status              # Encryption Status: Enabled
sudo k3s kubectl get node -o jsonpath='{.items[0].status.allocatable.memory}{"\n"}'   # environ 2,2 Gio
docker ps --format '{{.Names}} {{.Status}}' | sort | diff ~/avant-k3s-conteneurs.txt - \
  && echo "Compose inchangé"                 # les durées "Up" changent : comparer noms et "healthy"
curl -fsS http://127.0.0.1:8080/actuator/health   # gateway Compose toujours UP
```

### Pare-feu (si `ufw` est actif)

```bash
sudo ufw status verbose
# Les pods doivent joindre l'API et le DNS du nœud (recommandation k3s) :
sudo ufw allow from 10.42.0.0/16 to any
sudo ufw allow from 10.43.0.0/16 to any
# API Kubernetes et kubelet : jamais ouverts au réseau. Les administrateurs
# passent par SSH ; si la politique entrante par défaut est "allow" :
sudo ufw deny 6443/tcp
sudo ufw deny 10250/tcp
```

### kubeconfig pour Terraform

```bash
mkdir -p ~/.kube && chmod 700 ~/.kube
sudo install -m 600 -o "$USER" -g "$USER" /etc/rancher/k3s/k3s.yaml ~/.kube/aquaops-k3s.yaml
export KUBE_CONFIG_PATH=~/.kube/aquaops-k3s.yaml
```

Il donne un accès **administrateur** au cluster : il ne quitte jamais la VM. Il
n'est jamais copié dans le dépôt (`tf.sh` le refuse), ni collé dans un ticket ou
un chat. Son adresse est `https://127.0.0.1:6443` : utilisable seulement sur la VM.

## 4. Sauvegarde

| Quoi | Comment | Quand |
| --- | --- | --- |
| **La VM entière** | Instantané VirtualBox (hôte) | Avant l'installation et avant chaque étape risquée |
| **Ce que contient le cluster** | Le code Terraform du dépôt : tout est recréé par `tf.sh ... apply` | Toujours à jour (Git) |
| **État Terraform** | `~/.local/state/aquaops-terraform/` (sans secret : voir `deploy/terraform/README.md`) | `tar` après chaque apply |
| **Base k3s, jeton et clé de chiffrement** | voir ci-dessous (arrêt de k3s seulement, Docker continue) | Avant une mise à jour de k3s |
| **Données PostgreSQL dans k3s** | `pg_dump` (PR « infrastructure ») | Quand PostgreSQL sera dans k3s |

```bash
sudo systemctl stop k3s           # les pods k3s s'arrêtent, les conteneurs Docker continuent
sudo tar -czf /root/k3s-backup-$(date +%Y%m%d-%H%M).tar.gz \
  /var/lib/rancher/k3s/server/db /var/lib/rancher/k3s/server/token \
  /var/lib/rancher/k3s/server/cred /etc/rancher/k3s
sudo chmod 600 /root/k3s-backup-*.tar.gz
sudo systemctl start k3s
```

`server/cred` contient la clé qui chiffre les secrets : sans elle, une base
restaurée est illisible. L'archive reste sur la VM, accessible à root seulement.

## 5. Retour arrière

| Niveau | Situation | Commande | Effet sur Compose |
| --- | --- | --- | --- |
| 1 | Un changement Terraform pose problème | `git revert` du commit, puis `tf.sh ... apply` | Aucun |
| 2 | Supprimer l'environnement jetable | `AQUAOPS_CONFIRM_DESTROY=rc11 deploy/terraform/tf.sh environment rc11 destroy` | Aucun |
| 3 | Retirer k3s entièrement | `sudo /usr/local/bin/k3s-uninstall.sh` | Aucun : ne retire que k3s, ses interfaces et ses règles iptables |
| 4 | La VM est dans un état inconnu | Restaurer l'instantané VirtualBox (hôte) | Retour à l'état de l'instantané |

Après le niveau 3, refaire les contrôles de l'étape 4 : liste des conteneurs
inchangée, gateway `UP`. Si le réseau Docker ne fonctionne plus, **ne pas**
relancer Docker sans accord : redémarrer le démon redémarre tous les conteneurs.

## 6. Ce qui a été testé, et où

Dans un **cluster k3s jetable** (image `rancher/k3s:v1.37.2-k3s1` dans Docker
Desktop, sur le poste de E5), avec ce `config.yaml` monté tel quel. Rien n'a été
installé sur la VM.

- nœud `Ready`, label `aquaops.ma/role=test`, chiffrement des secrets activé,
  DNS en 10.43.0.10 ;
- réservations prises en compte : 5,8 Gio retirés de la mémoire allouable ;
- `preflight.sh` exécuté sous WSL Ubuntu : 0 blocker. Sa partie Docker n'a pas
  pu tourner, faute d'accès à Docker dans WSL : elle sera testée sur la VM
  avec `sudo`.
