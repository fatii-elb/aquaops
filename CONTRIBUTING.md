# Contribuer à AquaOps : règles Git et Definition of Done

Carte TECH-7 · rédigée par E3 · version 0, à valider à la planification du lundi 12 octobre 2026.
Ce document reprend et complète les sections 5 et 7 du guide de démarrage de l'équipe.

---

## 1. Le principe

Personne n'écrit directement sur `main`. Pour chaque carte, on travaille sur une branche, on ouvre une pull request (PR), **une autre étudiante** la relit, puis on la fusionne.

```
main  ──────────────●───────────●──────  (toujours propre)
                     \         /
branche               ●──●──●─●   (ton travail, relu avant fusion)
```

---

## 2. Règles Git

| Règle | Ce qu'on fait | Exemple |
|---|---|---|
| Modifier `main` | Jamais directement, seulement par pull request | |
| Nom de branche | `feature/<CARTE>-<nom-court>` ; `fix/...` pour une correction ; `docs/...` pour la documentation | `feature/SCRUM-23-pumps-sensors` |
| Message de commit | `<CARTE>: ce que j'ai fait` | `SCRUM-23: API des pompes` |
| Une branche | Une seule carte | |
| Taille d'une PR | Courte : moins de 300 lignes si possible | |
| Relecture | Au moins 1 approbation d'une autre étudiante | |
| Fusion | **Personne ne fusionne sa propre PR** | |
| Chaque matin | `git checkout main && git pull`, pour éviter les conflits | |
| Interdit | `git push --force` sur `main` | |
| Ne jamais pousser | Mots de passe, jetons, fichiers `.env`, dossiers générés (`target/`, `node_modules/`) | |

### Le cycle d'une tâche

```bash
git checkout main
git pull
git checkout -b feature/SCRUM-23-pumps-sensors
# ... je code, je lance les tests : ./mvnw test ...
git status
git add <les fichiers de ma carte>
git commit -m "SCRUM-23: API des pompes"
git push -u origin feature/SCRUM-23-pumps-sensors
# puis j'ouvre la pull request sur GitHub et je demande une relecture
```

### Si Git annonce un conflit

```bash
git checkout main && git pull
git checkout ma-branche
git merge main
# ouvrir les fichiers marqués <<<<<<< ======= >>>>>>>, garder la bonne version,
# effacer les marques, relancer les tests
git add . && git commit
git push
```

En cas de doute, demande à la personne qui a écrit le texte concerné.

---

## 3. Definition of Done

### Une story du Backlog est « Terminée » quand les 4 conditions sont vraies

1. Ses critères d'acceptation sont vérifiés (colonne « Vérification » du cahier des charges).
2. Le code marche et les tests passent.
3. Une autre étudiante a relu le code (pull request).
4. Le Product Owner a validé.

### Checklist à cocher dans chaque pull request

- [ ] Les critères d'acceptation de la carte sont vérifiés
- [ ] Les tests passent (`./mvnw test`) et j'ai ajouté des tests pour mon code
- [ ] Aucune adresse ni aucun mot de passe écrit dans le code : tout passe par des variables d'environnement avec une valeur par défaut locale
- [ ] Aucun secret dans le dépôt (mot de passe, jeton, fichier `.env`)
- [ ] Les chemins d'API commencent par le préfixe de mon service (`/api/<service>/...`)
- [ ] La documentation est à jour (README du service, contrat d'API ou d'événement)
- [ ] Une autre étudiante a relu et approuvé la PR
- [ ] La carte Jira est passée dans la bonne colonne

### Pour une tâche [TECH]

Les conditions 2 et 3 s'appliquent, plus le critère « Terminé quand » écrit sur la carte. La validation du Product Owner n'est pas obligatoire.

### Les colonnes Jira

| À faire | En cours | En revue | Terminé |
|---|---|---|---|
| Personne n'a commencé | La branche est créée | La PR est ouverte | Les conditions ci-dessus sont remplies |

---

## 4. Cartes Jira

- Les clés des cartes sont celles du Backlog : `SCRUM-xx`. Les cartes techniques s'appellent `TECH-x`.
- Le titre de la carte garde l'identifiant de l'exigence : « FR-09 Détecter une fuite probable ».

---

## 5. Être bloquée

Dire « je suis bloquée » tôt est une qualité. Règle simple : **30 minutes sans avancer, on demande** (au point quotidien ou dans le groupe).

---

## 6. À valider lundi 12 octobre

1. **Format du message de commit :** le guide de démarrage donne deux formats (`SCRUM-45: rôles Keycloak` et `SCRUM-23 FR-01 CRUD des zones`). Proposition : toujours `<CARTE>: message`, avec deux-points.
2. **Clés Jira :** le guide cite des cartes `AQUA-1`, `AQUA-2`. Le Backlog utilise `SCRUM-xx` : à harmoniser.
3. **Protection de `main` sur GitHub :** vérifier dans Settings → Branches que la relecture obligatoire est bien activée.
4. **Fusion :** qui clique sur « Merge » une fois la PR approuvée ? Proposition : la personne qui a relu.
