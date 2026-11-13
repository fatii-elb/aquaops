# Guide d'installation de Gitleaks — AquaOps



## 1. Objectif



Gitleaks est un outil de détection des secrets (clés API, tokens, identifiants, etc.) dans un dépôt Git.



Dans AquaOps, il est utilisé pour prévenir l'introduction de secrets dans le code source grâce à un hook Git pre-commit.



## 2. Prérequis



- Git installé sur la machine.

- Gitleaks version 8.30.1.

- Accès au dépôt AquaOps.



## 3. Installation de Gitleaks



### Windows



Télécharger la version Windows depuis les [releases officielles de Gitleaks](https://github.com/gitleaks/gitleaks/releases).



Extraire l'archive et ajouter le dossier contenant `gitleaks.exe` à la variable d'environnement `PATH`.



### Linux



Télécharger l'archive Linux correspondant à l'architecture de la machine depuis les releases officielles, puis installer l'exécutable dans un répertoire du `PATH`.



### macOS



Avec Homebrew :



```bash

brew install gitleaks

```



Vérifier l'installation :



```bash

gitleaks version

```



## 4. Activer le hook pre-commit



Depuis la racine du dépôt AquaOps :



```bash

git config --local core.hooksPath scripts/git-hooks

```



Sur Linux ou macOS, vérifier également les permissions :



```bash

chmod +x scripts/git-hooks/pre-commit

```



Le hook sera exécuté automatiquement avant chaque commit Git effectué dans ce dépôt.



## 5. Vérifier le fonctionnement



Pour lancer une analyse manuelle :



```bash

gitleaks dir --config .gitleaks.toml --redact .

```



Pour vérifier le hook avec les changements préparés :



```bash

sh scripts/git-hooks/pre-commit

```



Le hook utilise la configuration commune `.gitleaks.toml`.



## 6. En cas de secret détecté



Si Gitleaks détecte un secret, le commit est refusé.



Il faut alors :



1\. Identifier le fichier concerné.

2\. Retirer le secret du code source.

3\. Révoquer ou remplacer le secret s'il était réel.

4\. Utiliser un mécanisme de gestion des secrets adapté.

5\. Préparer à nouveau les fichiers avec `git add`, puis relancer le commit.



Ne pas désactiver le hook pour contourner une détection.



## 7. Contrôle dans GitHub Actions



En complément du hook local, AquaOps dispose d'un workflow `.github/workflows/gitleaks.yml` pour contrôler les modifications lors des pull requests vers `main` et des pushs sur `main`.



Le hook local constitue une première protection. Le contrôle GitHub Actions apporte une vérification supplémentaire.



## 8. Références



- [Documentation officielle Gitleaks](https://github.com/gitleaks/gitleaks)

- Configuration du projet : `.gitleaks.toml`

- Hook local : `scripts/git-hooks/pre-commit`

- Workflow CI : `.github/workflows/gitleaks.yml`

