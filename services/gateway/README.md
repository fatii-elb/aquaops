# gateway (owner: E5)

Point d'entrée unique de l'API AquaOps (Spring Cloud Gateway, WebFlux), port
**8080**. Chaque préfixe `/api/...` est envoyé au service qui le possède, sans
réécrire le chemin : `/api/assets/zones` arrive tel quel à `assets-service`.

| Route | Préfixe (contrat API) | Mode fixe (défaut) | Mode Eureka |
| --- | --- | --- | --- |
| `telemetry` | `/api/telemetry/**` | `TELEMETRY_URL`, défaut `http://localhost:8081` | `lb://telemetry-service` |
| `assets` | `/api/assets/**` | `ASSETS_URL`, défaut `http://localhost:8082` | `lb://assets-service` |
| `detection` | `/api/detection/**` | `DETECTION_URL`, défaut `http://localhost:8083` | `lb://detection-service` |
| `incidents` | `/api/incidents/**` | `INCIDENTS_URL`, défaut `http://localhost:8084` | `lb://incidents-service` |
| `notifications` | `/api/notifications/**` | `NOTIFICATIONS_URL`, défaut `http://localhost:8085` | `lb://notifications-service` |
| `simulator` | `/api/simulator/**` | `SIMULATOR_URL`, défaut `http://localhost:8086` | `lb://simulator` |

## Deux modes de routage

**Mode fixe (défaut, Sprint 4).** Aucune variable à ajouter : la gateway envoie
à l'adresse donnée par `*_URL`. Le client Eureka est désactivé. C'est le mode
de `deploy/test/compose.yml` (VM de test) et de la pile d'intégration.

**Mode Eureka** : `SPRING_PROFILES_ACTIVE=eureka` et `EUREKA_URL`.

- La gateway s'inscrit dans Eureka et relit le registre toutes les 5 s.
- Chaque route va vers `lb://<spring.application.name>` : les instances
  viennent d'Eureka, les requêtes sont réparties entre elles.
- **Migration service par service** : en mode Eureka, un `*_URL` fourni garde
  ce service en adresse fixe. Ne le fournir que pour les services qui n'ont pas
  encore le client Eureka ; le retirer (ou le mettre à `lb://<nom>`) ensuite.
- Pas de cache LoadBalancer en plus du registre : une instance retirée
  d'Eureka ne reçoit plus de requêtes.

| Où | `SPRING_PROFILES_ACTIVE` | `EUREKA_URL` |
| --- | --- | --- |
| Poste local (`java -jar` ou `./mvnw spring-boot:run`) | `eureka` | `http://localhost:8761/eureka` (défaut) |
| Docker Compose | `eureka` | `http://eureka:8761/eureka` |
| Kubernetes (SCRUM-56) | `eureka` | `http://eureka:8761/eureka` |

Exemple Compose prêt à l'emploi : `deploy/integration/compose.eureka.yml`
(surcouche optionnelle de la pile d'intégration).

Retour au mode fixe : retirer `SPRING_PROFILES_ACTIVE=eureka`. Rien d'autre.

## Comportement mesuré (poste local, serveur Eureka, gateway et assets-service réels)

| Situation | Réponse de la gateway |
| --- | --- |
| Aucune instance inscrite | `503` |
| Service démarré ou redémarré | routé 1 à 5 s après être devenu sain (mesures locales) |
| Arrêt normal du service (désinscription) | `503` en moins de 5 s (0,5 s mesuré) |
| Arrêt brutal (`kill -9`, plantage) | retiré du registre en 34 s, `503` en 37 s ; **entre les deux, `500`** (connexion refusée vers l'instance morte) |
| Serveur Eureka absent | la gateway démarre, santé `UP` ; routes `lb://` en `503`, routes en adresse fixe inchangées |

## Tests

```bash
./mvnw verify
```

12 tests, sans serveur Eureka ni service réel : routes et préfixes en mode fixe
et en mode Eureka, variables `*_URL` du Sprint 4, migration partielle, routage
HTTP réel `lb://` vers un faux service (chemin et paramètres intacts), `503`
sans instance, `404` hors des préfixes `/api`.
