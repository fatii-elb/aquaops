# Cadre de tests commun (TECH-5)

Carte TECH-5 · rédigée par E6 · version 0, à relire par l'équipe.

## 1. Principe

Chaque service a des tests JUnit 5, lancés par `./mvnw test` depuis le dossier du service. Une pull request n'est relue que si les tests passent (voir `CONTRIBUTING.md`).

## 2. Deux catégories de tests

| Catégorie | Annotation | Besoin | Quand l'utiliser |
|---|---|---|---|
| **Test unitaire** | aucune (JUnit seul) | Rien : ni Spring, ni base, ni Kafka | Logique pure : calculs, conversions, validations, règles de détection |
| **Test d'intégration** | `@SpringBootTest` (+ `MockMvc` pour les API) | Dépend du service : PostgreSQL pour assets et telemetry | API, accès base, sécurité |

Règle : **par défaut, écris un test unitaire**. Il est rapide (moins d'une seconde) et passe sur tous les postes. N'utilise `@SpringBootTest` que si tu as besoin de Spring.

Modèle de test unitaire : `ExempleTests.java`, présent dans chaque service (ex. `services/simulator/src/test/java/ma/aquaops/simulator/ExempleTests.java`). Modèle de test d'intégration : `AssetsCrudTests.java` dans `assets-service`.

## 3. Conventions

| Sujet | Règle | Exemple |
|---|---|---|
| Emplacement | `src/test/java/ma/aquaops/<court>/` | `ma/aquaops/assets/` |
| Nom de la classe | `<Sujet>Tests` | `AssetsCrudTests` |
| Nom d'une méthode | décrit le comportement, en camelCase | `zoneCrudComplet`, `refuseUneZoneSansNom` |
| Un test | vérifie **un** comportement | |
| Structure | Préparer, Agir, Vérifier (commentaires `// Préparer` ...) | voir `ExempleTests` |
| Cas de refus | tester aussi les erreurs (400, 403, 404) | capteur sans zone : 400 |
| Données | créées par le test lui-même, jamais supposées déjà en base | |
| Base de données | `@Transactional` sur la classe pour annuler les changements | `AssetsCrudTests` |
| Indépendance | un test ne dépend pas d'un autre ni de l'ordre d'exécution | |
| Secrets | aucun mot de passe réel, aucune adresse écrite en dur | |

## 4. Commandes

    cd services/<nom-du-service>
    ./mvnw test                          # tous les tests du service
    ./mvnw test -Dtest=ExempleTests      # un seul test, sans démarrer Spring
    start target/site/jacoco/index.html  # ouvrir le rapport de couverture (Windows)

Prérequis : Java 21. Les tests d'intégration d'`assets-service` et de `telemetry-service` demandent PostgreSQL démarré et la base du service créée. Les tests unitaires ne demandent rien.

## 5. Interprétation des résultats

- `BUILD SUCCESS` et `Failures: 0, Errors: 0` : c'est bon.
- Les `WARN` affichés pendant les tests de refus (403, 400) sont volontaires, ce n'est pas une erreur.
- Les avertissements Mockito ou « Java agent » sont normaux avec Java 21.

## 6. État par service

| Service | Test exemple (`ExempleTests`) |
|---|---|
| assets-service | présent |
| telemetry-service | présent |
| detection-service | présent |
| simulator | présent |
| notifications-service | `StatusControllerTests` (test unitaire du contrôleur) |
| incidents-service | présent |

## 7. Rapport de couverture (JaCoCo)

JaCoCo mesure la part du code exécutée par les tests. Il est branché dans `assets-service`, `telemetry-service`, `detection-service`, `simulator`, `notifications-service` et `incidents-service`.

- Le rapport est généré à chaque `./mvnw test`, dans `target/site/jacoco/index.html` (dossier du service).
- Aucun seuil minimal n'est exigé pour l'instant : la carte demande seulement de produire le rapport.
- Pour un nouveau service : ajouter le plugin `org.jacoco:jacoco-maven-plugin` (version 0.8.13, objectifs `prepare-agent` et `report`) dans la balise `<plugins>` de son `pom.xml`, comme dans `notifications-service`.
- Les six services ont maintenant JaCoCo.