# Analyses de sécurité : SCA et IaC (Sprint 5, E5)

Workflow : `.github/workflows/security.yml` (« Security »). Exécuté sur chaque
pull request vers `main`, chaque push sur `main`, chaque lundi (de nouvelles
CVE apparaissent sans commit) et à la demande. Runners hébergés par GitHub,
jeton en lecture seule, aucun déploiement : **séparé** du déploiement
privilégié (`k3s-deploy.yml`, runner auto-hébergé).

| Job | Outil | Ce qui est analysé | Ce qui bloque | Rapport (artefact, 30 jours) |
| --- | --- | --- | --- | --- |
| `iac` | Trivy config 0.76.2 | `deploy/` : Terraform (évalué avec `envs/rc11.tfvars`), manifestes de E2 (`deploy/k8s`), Dockerfiles | sévérité **CRITICAL** | `iac-reports/iac-trivy.txt` (toutes sévérités, fichier et ligne) |
| `iac` | Checkov 3.4.0 | `deploy/terraform` (ressources `kubernetes_*`, que Trivy ne sait pas analyser) et `deploy/k8s` | liste `CHECKOV_HARD_FAIL` (ci-dessous) | `iac-reports/iac-checkov.sarif` + journal du job |
| `sca-trivy` | Trivy rootfs 0.76.2 | les **8 jars** Spring Boot (exactement ce que livrent les images), base Java de Trivy (avis GitHub) | **CRITICAL corrigeable** | `sca-trivy/sca-trivy.{json,txt}` (HIGH et CRITICAL) |
| `sca-owasp` | OWASP Dependency-Check 13.0.0 (plugin Maven) | les 8 services, base NVD | **CVSS ≥ 9** (= CRITICAL) | `sca-dependency-check/<service>/dependency-check-report.{html,json}` |
| `build-java`, `build-frontend` | Trivy 0.76.2, format CycloneDX | les 8 images applicatives construites pour le commit | Échec de génération ou de validation du SBOM | `sbom-<image>-cyclonedx` (JSON, 90 jours) |

Dans le workflow Security, outils figés par **version et empreinte** (`sha256`)
et actions par SHA de commit. Dans le workflow Images, l'action Trivy est
également épinglée par SHA et l'exécutable par version (`v0.76.2`).

## SBOM CycloneDX des images

Sur chaque push vers `main`, `publish.yml` génère un SBOM CycloneDX JSON pour
chacune des 7 images Java et l'image frontend, à partir de l'image locale exacte
qui vient de passer le contrôle Trivy. Le pipeline vérifie le format CycloneDX,
la référence de l'image dans les métadonnées et la présence de composants;
l'absence ou l'invalidité d'un SBOM fait échouer le job de construction.

Les huit artefacts `sbom-<image>-cyclonedx` sont téléchargeables depuis
l'exécution GitHub Actions correspondante pendant **90 jours**. Ils ne sont pas
publiés comme références OCI dans GHCR.

## Pourquoi deux outils pour l'IaC

Mesuré sur ce dépôt (16/11/2026) :

- Trivy n'a **aucune règle** pour les ressources `kubernetes_*` de Terraform :
  il ne voit que les fichiers YAML de E2. Checkov, lui, analyse ces ressources
  (187 contrôles réussis sur `deploy/terraform`).
- Checkov (version libre) n'a pas de niveaux de sévérité. La règle « bloquer le
  CRITICAL » y est donc une **liste de contrôles** : conteneur privilégié
  (CKV_K8S_16), espaces de noms de l'hôte (17, 18, 19), élévation de
  privilèges (20), capacités ajoutées (25), hostPort (26), socket Docker
  (27), SYS_ADMIN (39). Tout le reste est signalé sans bloquer.
- Test négatif : un pod `privileged: true` est **HIGH** pour Trivy (il passe
  la barrière CRITICAL) et **bloqué** par Checkov (code 1). Les deux sont
  nécessaires.

## Une analyse incomplète n'est jamais un succès

- Trivy : un échec de téléchargement de sa base sort en erreur, le job échoue.
  En plus, `sca-trivy` vérifie que les 8 jars ont été lus et qu'au moins 100
  paquets Java ont été trouvés (789 mesurés), sinon échec « incomplete scan ».
- Dependency-Check : **exige une clé d'API NVD** (secret de dépôt
  `NVD_API_KEY`). Vérifié : sans clé, la version 13 s'arrête en erreur
  (« Invalid API Key, length of 0 »). Le job vérifie d'abord la présence de la
  clé et échoue avec « INCOMPLETE, not passed » si elle manque.
  `failOnError=true` : une indisponibilité de la NVD ou une limite de débit
  (`nvdMaxRetryCount=10`) fait échouer le job. La copie de la NVD est gardée en
  cache une semaine (mise à jour incrémentale).
- `sca-owasp` ne tourne **pas** sur les pull requests : les secrets ne sont pas
  donnés aux PR de forks, et la clé ne doit pas l'être. Les PR ont `sca-trivy`.
- OSS Index (Sonatype) est désactivé : il demande désormais un compte.
- **Contrôle de complétude de Dependency-Check** (ajouté le 19/11/2026) : un
  job vert exige, pour **chacun des 8 services**, un rapport JSON, au moins
  10 dépendances lues et des données NVD datant de **8 jours au plus**
  (`scanInfo.dataSource`, entrées « NVD API Last Checked / Last Modified »).
  Tout le reste échoue, y compris un format de rapport inconnu (le contrôle
  échoue fermé). Testé sur 11 cas (rapport manquant, 0 dépendance, NVD absente,
  vieille ou illisible, JSON invalide, 9 services, horodatages `Z[UTC]` et
  `+01:00[Europe/Paris]`).

### Correctif du 19/11/2026 : rapports Dependency-Check jamais publiés

Depuis l'ajout du secret `NVD_API_KEY`, le job `sca-owasp` était **vert sur
`main`** mais l'exécution n'avait **aucun artefact** `sca-dependency-check`.
Cause : le plugin lie le dossier de sortie à la propriété
`odc.outputDirectory` (vérifié dans le `plugin.xml` de
`dependency-check-maven` 13.0.0) ; `-DoutputDirectory` était ignoré, les
rapports restaient dans `services/*/target/` et l'envoi
(`if-no-files-found: ignore`) ne trouvait rien. La barrière CVSS ≥ 9
fonctionnait, mais sans preuve consultable ni vérification que chaque service
avait été analysé. Corrigé : `-Dodc.outputDirectory`, contrôle de complétude
ci-dessus, `if-no-files-found: warn`.

## Résultats au 16/11/2026 (commit `e02eda3`, exécutions locales)

### SCA : 0 CRITICAL, 14 HIGH distinctes (toutes corrigées en amont)

| CVE | Paquet | Installé | Corrigé dans | Origine probable |
| --- | --- | --- | --- | --- |
| CVE-2026-89407, CVE-2026-89425 | jackson-core (2.x et `tools.jackson` 3.x) | 2.21.5 / 3.1.5 | 2.21.7 / 3.1.7 | Spring Boot 4.1.1 |
| CVE-2026-68497, CVE-2026-91776, CVE-2026-91777 | jackson-databind (2.x et 3.x) | 2.21.5 / 3.1.5 | 2.21.7 / 3.1.7 | Spring Boot 4.1.1 |
| CVE-2026-106451 | lz4-java | 1.10.1 | 1.11.4 | client Kafka |
| CVE-2026-108106, CVE-2026-93451 | snappy-java | 1.1.10.7 | 1.1.10.9 | client Kafka |
| CVE-2026-102554 | guava | 33.0.0-jre | 33.7.2 | client / serveur Eureka |

Les images (Trivy, `publish.yml`) montrent les mêmes HIGH : 8 à 13 par
image, 0 CRITICAL.

### Plan de correction par responsable (vérifié sur Maven Central le 19/11/2026)

Ce que chaque montée de version corrige réellement :

| Changement | Corrige | Ne corrige pas |
| --- | --- | --- |
| Spring Boot **4.1.1 → 4.1.2** (parent du `pom.xml`) | jackson 2.21.5 → **2.21.7**, `tools.jackson` 3.1.5 → **3.1.7** (10 HIGH sur 14) ; Tomcat géré **11.0.27** | lz4 : Kafka 4.2.2 apporte lz4-java **1.11.2** (corrigé en 1.11.4) ; snappy-java reste **1.1.10.7** |
| Spring Cloud **2025.1.3 → 2025.1.4** | (alignement) | guava : Eureka reste en **2.0.6**, guava 33.0.0-jre |
| `<dependencyManagement>` : `com.google.guava:guava` **33.7.2-jre** | CVE-2026-102554 (`eureka-server` seulement : le client Eureka n'apporte pas guava) | — |
| `<dependencyManagement>` : `at.yawk.lz4:lz4-java` **1.11.4**, `org.xerial.snappy:snappy-java` **1.1.10.9** (coordonnées utilisées par kafka-clients 4.2.2) | CVE-2026-106451, CVE-2026-108106, CVE-2026-93451 (services avec Kafka) | — |

Une entrée déclarée directement dans `<dependencyManagement>` l'emporte sur
les BOM importés (Spring Cloud) et sur le parent : la placer avant l'import
de `spring-cloud-dependencies`, avec un commentaire « à retirer quand la
version gérée est corrigée ».

**Piège à éviter :** sept `pom.xml` fixent `<tomcat.version>11.0.25</tomcat.version>`
(correctif CRITICAL du Sprint 4). Avec Boot 4.1.2, ce réglage **rétrograde**
Tomcat de 11.0.27 à 11.0.25 : **le supprimer** en même temps que la montée.

Mesuré le 19/11/2026 (Trivy 0.76.2 sur les jars des images de `main` à
`4572bca`, mêmes options que `sca-trivy`) : **0 CRITICAL, 9 CVE distinctes**,
HIGH par service ci-dessous. guava n'est présent **que dans `eureka-server`** ;
`simulator` n'embarque plus Kafka (envoi HTTP par la gateway) : ni lz4, ni snappy.

| Service | Responsable | HIGH à `4572bca` | À faire dans son `pom.xml` | État |
| --- | --- | --- | --- | --- |
| `gateway` | E5 | 10 (Jackson) | Boot 4.1.2, Cloud 2025.1.4 | **Fait** (PR `feature/TECH-sca-e5-deps`) : **0 HIGH**, 15 tests OK |
| `eureka-server` | E5 | 11 (Jackson, guava) | Boot 4.1.2, Cloud 2025.1.4, guava ; retirer `tomcat.version` et `freemarker.version` | **Fait** (même PR) : **0 HIGH**, 6 tests OK, Tomcat 11.0.27 |
| `telemetry-service` | E1 | 13 (Jackson, lz4, snappy) | Boot 4.1.2, Cloud 2025.1.4, lz4, snappy ; retirer `tomcat.version` | À faire |
| `simulator` | E1 | 10 (Jackson) | Boot 4.1.2, Cloud 2025.1.4 ; retirer `tomcat.version` | À faire |
| `assets-service` | E2 | 13 (Jackson, lz4, snappy) | Boot 4.1.2, Cloud 2025.1.4, lz4, snappy ; retirer `tomcat.version` | À faire |
| `detection-service` | E3 | 8 (Jackson 3, lz4, snappy) | Boot 4.1.2, lz4, snappy ; retirer `tomcat.version` | À faire |
| `incidents-service` | E4 | 13 (Jackson, lz4, snappy) | Boot 4.1.2, Cloud 2025.1.4, lz4, snappy ; retirer `tomcat.version` | À faire |
| `notifications-service` | E6 | 13 (Jackson, lz4, snappy) | idem | À faire |

Vérification après chaque PR : job `sca-trivy` de la PR (0 CRITICAL, plus
aucune HIGH listée ci-dessus) et tests du service.

Dependency-Check : rapports disponibles à partir du correctif du 19/11/2026
(artefact `sca-dependency-check` de l'exécution sur `main`).

### IaC

| Outil | Résultat | Bloquant |
| --- | --- | --- |
| Trivy config | 0 CRITICAL ; 2 HIGH, 4 MEDIUM, 4 LOW, tous dans `deploy/k8s/{assets,telemetry}/deployment.yaml` | non |
| Checkov | Terraform : 187 réussis, 3 échoués, 25 ignorés avec raison ; Kubernetes : 168 réussis, 16 échoués ; aucun contrôle de `CHECKOV_HARD_FAIL` | non |

Détail et traitement :

| Constat | Où | Traitement |
| --- | --- | --- |
| Système de fichiers racine en écriture (KSV-0014 HIGH, CKV_K8S_22), pas de `runAsNonRoot` (KSV-0012, CKV_K8S_23), jeton de compte de service monté (CKV_K8S_38) | `deploy/k8s/assets/deployment.yaml:27`, `deploy/k8s/telemetry/deployment.yaml:26` (E2) | **Corrigé au déploiement** par la surcouche E5 de `e2_services.tf` (uid/gid 999, `runAsNonRoot`, racine en lecture seule, `/tmp` en `emptyDir`, pas de jeton), vérifié sur les pods. Proposé à E2 de l'écrire aussi dans ses fichiers. |
| Pas de NetworkPolicy associée (CKV2_K8S_6) | idem | Faux positif : les NetworkPolicies sont créées par Terraform (`main.tf`). |
| Secrets en variables d'environnement (CKV_K8S_35) | idem, `services.tf` | **Risque accepté** : Spring lit ces variables ; des fichiers demanderaient une modification des services. Secrets jamais dans Git ni dans l'état Terraform (`data_wo`). |
| `imagePullPolicy` différent de `Always` (CKV_K8S_15), pas d'empreinte (CKV_K8S_43) | `services.tf`, `eureka.tf`, E2 | **Risque accepté** : images importées après analyse, `Never` garantit qu'aucune image publique homonyme ne les remplace et qu'aucun jeton GHCR n'est dans le cluster. Une image tirée d'un registre exige une empreinte (`services_image_digests`, contrôlé par Terraform). |
| uid ≤ 10000 (KSV-0020/21, CKV_K8S_40) | toutes les images Spring | **Risque accepté (LOW)** : uid 999 fixé par l'image ; le changer touche le Dockerfile partagé et la pile Compose du Sprint 4. |
| Keycloak et Kafka en écriture (CKV_K8S_22, déjà noté HIGH dans `deploy/terraform/README.md` § 7) | `keycloak.tf`, `kafka.tf` | **Risque accepté** : `start-dev` écrit dans `/opt/keycloak`, l'image Kafka génère sa configuration. |
| Registres de confiance (KSV-0125) | E2 | Signalé ; les images sont celles du pipeline. |
| Limites et étiquette d'image (CKV_K8S_12/13/14) | `services.tf` | Faux positifs (valeurs en `for_each`), ignorés avec raison dans le code ; vérifiés sur les pods. |

## Ajouter une exception

- Vulnérabilité réelle sans correctif : `deploy/trivy/.trivyignore` (une ligne,
  raison, date, personne).
- Faux positif de Dependency-Check : `deploy/sca/dependency-check-suppressions.xml`
  (avec `until`).
- Contrôle IaC : commentaire `#checkov:skip=<ID>:<raison>` dans la ressource.
  Jamais pour un contrôle de `CHECKOV_HARD_FAIL` sans accord de l'équipe.

## Exécuter en local (sans cluster)

```bash
# IaC
docker run --rm -v "$PWD:/src:ro" -w /src aquasec/trivy:0.76.2 config --skip-dirs '**/.terraform' \
  --tf-vars deploy/terraform/environment/envs/rc11.tfvars --severity CRITICAL --exit-code 1 deploy
docker run --rm -v "$PWD:/src:ro" -w /src bridgecrew/checkov:3.4.0 -d deploy/terraform -d deploy/k8s \
  --framework terraform kubernetes --skip-path .terraform \
  --var-file deploy/terraform/environment/envs/rc11.tfvars --soft-fail \
  --hard-fail-on CKV_K8S_16,CKV_K8S_17,CKV_K8S_18,CKV_K8S_19,CKV_K8S_20,CKV_K8S_25,CKV_K8S_26,CKV_K8S_27,CKV_K8S_39
# SCA : les jars d'images déjà construites
mkdir -p sca/jars
for n in gateway telemetry assets detection incidents notifications simulator eureka-server; do
  c=$(docker create localhost/aquaops/$n:<sha>); docker cp $c:/app/app.jar sca/jars/$n.jar; docker rm $c
done
docker run --rm -v "$PWD/sca:/sca" aquasec/trivy:0.76.2 rootfs --scanners vuln \
  --severity CRITICAL --ignore-unfixed --exit-code 1 /sca/jars
```

## OWASP ZAP (DAST)
Workflow `.github/workflows/zap.yml` : construit l'image du frontend, la démarre sur le runner GitHub et lance `zap-baseline.py` (scan passif, image ZAP épinglée par digest). Le job échoue uniquement sur une alerte HIGH ; les alertes moyennes et basses restent dans le rapport HTML (artefact `zap-report`, 14 jours). Déclenchement : pull request ou push touchant `frontend/`, chaque lundi, ou à la main.

Premier scan (local) : 0 HIGH. En-têtes `Content-Security-Policy` et `Permissions-Policy` ajoutés dans nginx. Alertes acceptées : cache long des fichiers compilés (noms avec empreinte, voulu), horodatages du bundle (faux positifs), `Cross-Origin-Embedder-Policy` (risque de casser la connexion Keycloak).

Limite : l'API derrière la passerelle n'est pas analysée (chaque route exige un jeton Keycloak).
