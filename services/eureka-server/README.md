# eureka-server (owner: E5)

Registre des services AquaOps (Spring Cloud Netflix Eureka), carte **TECH Eureka**.
Les services s'y inscrivent par leur nom (`spring.application.name`) ; la gateway
les retrouve par ce nom en mode Eureka (`lb://assets-service`, voir
`services/gateway/README.md`).

- Spring Boot 4.1.1, Spring Cloud **2025.1.3** (même train que `gateway` et
  `assets-service`). Le train 2025.1.x prend en charge Boot 4.1.x à partir de
  2025.1.2 (tableau de compatibilité Spring Cloud).
- Port **8761**. Serveur seul (`register-with-eureka` et `fetch-registry` à `false`).
- Actuator : seulement `/actuator/health`, avec `/liveness` et `/readiness` pour
  Kubernetes.
- **Pas d'authentification** : le registre ne doit être joignable que par les
  services (réseau Docker, `ClusterIP` dans Kubernetes, `127.0.0.1` en local).

## Lancer

```bash
# Avec Maven (comme les autres services Java)
cd services/eureka-server
./mvnw spring-boot:run

# Ou dans Docker (depuis deploy/), image construite avec le Dockerfile commun
cd deploy
docker compose --profile eureka up -d --build eureka
```

Tableau de bord : http://localhost:8761 · santé : http://localhost:8761/actuator/health

## Variables

| Variable | Défaut | Rôle |
| --- | --- | --- |
| `EUREKA_PORT` | `8761` | Port d'écoute |
| `EUREKA_HOSTNAME` | `localhost` | Nom du serveur (`eureka` dans Docker et Kubernetes) |
| `EUREKA_SELF_PRESERVATION` | `false` | Auto-préservation. Laisser `false` : sinon un service arrêté reste listé plusieurs minutes |
| `EUREKA_EVICTION_INTERVAL_MS` | `5000` | Fréquence de recherche des baux expirés |

## Disparition d'un service arrêté (critère : 45 s au plus)

| Arrêt du service | Ce qui se passe | Délai |
| --- | --- | --- |
| Normal (Ctrl+C, `docker stop`, suppression d'un pod) | Le client se désinscrit en s'arrêtant | Immédiat |
| Brutal (`kill -9`, plantage, coupure réseau) | Bail non renouvelé, retiré par l'éviction | Mesuré : **35 s** après le dernier renouvellement, avec les réglages client ci-dessous |

Les réglages client ci-dessous sont obligatoires pour tenir ce délai : avec les
valeurs par défaut d'Eureka (bail de 90 s), un service planté resterait listé
environ 3 minutes.

## Exemple de configuration client (pour chaque service)

C'est la configuration déjà utilisée par `assets-service`. Désactivée par
défaut : sans serveur Eureka, le service démarre normalement.

**1. `pom.xml`** : la dépendance et, si le service n'a pas encore Spring Cloud,
le BOM du même train.

```xml
<properties>
    <spring-cloud.version>2025.1.3</spring-cloud.version>
</properties>

<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>${spring-cloud.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**2. `application.yml`** : ne pas changer `spring.application.name`, c'est le
nom que la gateway utilisera (`telemetry-service`, `assets-service`,
`detection-service`, `incidents-service`, `notifications-service`, `simulator`).

```yaml
eureka:
  client:
    enabled: ${EUREKA_ENABLED:false}
    service-url:
      defaultZone: ${EUREKA_URL:http://localhost:8761/eureka}
    registry-fetch-interval-seconds: 5
  instance:
    # Inscription par adresse IP, un identifiant unique par instance (pods Kubernetes).
    prefer-ip-address: true
    instance-id: ${spring.application.name}:${spring.cloud.client.ip-address}:${server.port}
    # Bail court : un service planté disparaît du registre en moins de 45 s.
    lease-renewal-interval-in-seconds: 5
    lease-expiration-duration-in-seconds: 15
```

**3. Activer** au lancement, sans rien changer au code :

| Où | `EUREKA_ENABLED` | `EUREKA_URL` |
| --- | --- | --- |
| Poste local (`./mvnw spring-boot:run`) | `true` | `http://localhost:8761/eureka` |
| Docker Compose (réseau `backend`) | `true` | `http://eureka:8761/eureka` |
| Kubernetes (SCRUM-56) | `true` | `http://eureka:8761/eureka` |

**4. Vérifier** : le service apparaît dans le registre :

```bash
curl -s -H 'Accept: application/json' http://localhost:8761/eureka/apps/TELEMETRY-SERVICE
curl -s -H 'Accept: application/json' http://localhost:8761/eureka/apps/SIMULATOR
```

(noms en majuscules ; `404` = pas inscrit).

## Migration service par service (à faire par chaque responsable)

La gateway sait déjà router chaque service par Eureka (`services/gateway/README.md`).
Tant qu'un service n'a pas le client, la gateway garde son adresse fixe : rien
ne casse si un service migre plus tard que les autres.

| Responsable | Service | `spring.application.name` (ne pas changer) | Port | Route gateway en mode Eureka | État |
| --- | --- | --- | --- | --- | --- |
| E1 | telemetry-service | `telemetry-service` | 8081 | `lb://telemetry-service` | configuration présente ; inscription réelle à vérifier |
| E1 | simulator | `simulator` | 8086 | `lb://simulator` | inscription vérifiée avec Eureka local ; routage gateway testé |
| E2 | assets-service | `assets-service` | 8082 | `lb://assets-service` | **fait** (client présent, testé avec la gateway) |
| E3 | detection-service | `detection-service` | 8083 | `lb://detection-service` | à faire : étapes 1 à 3 |
| E4 | incidents-service | `incidents-service` | 8084 | `lb://incidents-service` | à faire : étapes 1 à 3 |
| E6 | notifications-service | `notifications-service` | 8085 | `lb://notifications-service` | à faire : étapes 1 à 3 |

Pour chaque service, dans **sa propre PR** (par son responsable) :

1. Ajouter la dépendance et le BOM (étape 1 ci-dessus). Si le service n'avait
   pas Spring Cloud, vérifier ensuite `./mvnw verify` : aucun test ne doit changer.
2. Copier le bloc `eureka:` (étape 2) dans son `application.yml`, **sans rien
   changer** : `enabled: ${EUREKA_ENABLED:false}` garde le service tel quel tant
   qu'on n'active pas Eureka.
3. Tester en local :

   ```bash
   # Terminal 1 : le registre
   cd services/eureka-server && ./mvnw spring-boot:run
   # Terminal 2 : la gateway en mode Eureka
   cd services/gateway && SPRING_PROFILES_ACTIVE=eureka ./mvnw spring-boot:run
   # Terminal 3 : le service, avec Eureka activé
   cd services/<service> && EUREKA_ENABLED=true ./mvnw spring-boot:run
   # Le service est inscrit, puis la gateway le joint par son nom :
   curl -s -H 'Accept: application/json' http://localhost:8761/eureka/apps/<NOM-EN-MAJUSCULES>
   curl -i http://localhost:8080/api/<prefixe>/...      # 503 = pas (encore) inscrit
   ```

4. Dans `deploy/integration/compose.eureka.yml`, activer le client du service
   avec `EUREKA_ENABLED: "true"` et `EUREKA_URL: http://eureka:8761/eureka`,
   puis passer le `*_URL` correspondant de la gateway à `lb://<nom>`.

Le service s'inscrit avec son adresse IP (`prefer-ip-address`) : dans Docker
et Kubernetes, c'est l'adresse du conteneur ou du pod, joignable par la gateway
sur le même réseau.

## Tests

```bash
./mvnw verify
```

Démarre le vrai serveur sur un port aléatoire et vérifie : santé `UP`, sondes
Kubernetes, seul `health` exposé, réglages du registre, inscription puis
désinscription d'une instance, éviction d'une instance qui ne renouvelle plus
son bail. Aucune base ni Kafka nécessaire.
