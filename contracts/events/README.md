# Contrats d'événements Kafka (SCRUM-60)

Règle de versionnement rédigée par E6 · version 0, à relire par l'équipe.

## 1. Les contrats

| Topic | Fichier | Producteur | Consommateurs | État |
|---|---|---|---|---|
| `sensor-readings` | `sensor-readings.v1.json` | E1 (télémétrie) | E3 (détection), stockage télémétrie | brouillon v1 |
| `anomaly-detected` | `anomaly-detected.v1.json` | E3 (détection) | E4 (incidents), E6 (notifications) | brouillon v1 |
| `incident-events` | `incident-events.v1.json` | E4 (incidents) | E6 (notifications), audit | brouillon v1 |

## 2. Format commun

- Un fichier **JSON Schema** (draft 2020-12) par topic et par version : `<topic>.v<N>.json`. Le `$id` vaut `<topic>.v<N>`.
- Topics en kebab-case, champs en camelCase, énumérations en MAJUSCULES_AVEC_TIRETS_BAS.
- Dates en ISO 8601, toujours en UTC : `2026-10-12T21:30:00Z`.
- Chaque événement contient `eventVersion`, une chaîne qui contient un entier (`"1"`, `"2"`). Dans le schéma : `"eventVersion": { "const": "1" }`.
- `additionalProperties: false` et au moins un exemple dans `examples`.
- Pour un nouveau contrat, copier `_modele.v1.json`.

## 3. Quand change la version ?

`eventVersion` change **seulement** quand on casse le contrat.

| Type de changement | Exemples | Ce qu'on fait |
|---|---|---|
| **Compatible** | ajouter un champ optionnel ; ajouter une valeur d'énumération ; préciser une description | On modifie le fichier `.v<N>.json` par pull request, sans changer la version, et on prévient l'équipe. |
| **Cassant** | supprimer ou renommer un champ ; changer le type, l'unité ou le sens d'un champ ; rendre obligatoire un champ optionnel ; retirer une valeur d'énumération | Nouveau fichier `.v<N+1>.json` avec `eventVersion` à `"<N+1>"`. L'ancien fichier reste dans le dépôt, marqué obsolète. |

En cas de doute, c'est un changement cassant.

## 4. Règles pour les services

**Producteur**
- Renseigner toujours `eventVersion`.
- Vérifier ses messages contre son schéma dans ses tests.
- Ne produire la version N+1 qu'après la confirmation de tous les consommateurs.

**Consommateur**
- Ignorer les champs inconnus (configurer le désérialiseur JSON et le vérifier par un test).
- Valeur d'énumération inconnue : l'écrire dans les journaux et continuer, sans planter.
- `eventVersion` inconnue : écrire un avertissement (topic et version) dans les journaux et ignorer le message, sans planter.

## 5. Passer de la version N à N+1

1. Le producteur propose le fichier `.v<N+1>.json` par pull request.
2. Chaque consommateur implémente N et N+1, puis le confirme dans la pull request.
3. Quand tous ont confirmé, le producteur commence à envoyer N+1.
4. La version N reste supportée jusqu'à la fin du sprint suivant, puis le fichier est retiré.

## 6. Modifier un contrat

- Une pull request qui ne touche que `contracts/events/`, avec un message `SCRUM-60: ...`.
- Elle doit être approuvée par le **producteur** et par **au moins un consommateur** du topic.
- Le sujet de la pull request dit s'il s'agit d'un changement compatible ou cassant.

## 7. Cycle de vie

- **Brouillon** : le contrat peut encore changer sans règle particulière.
- **Gelé** : les trois contrats v1 sont publiés et relus pour le mercredi 21 octobre 2026, et gelés à la fin du Sprint 1. À partir de là, tout changement suit la section 3.