# Scénarios de panne du simulateur (FR-06)

Carte SCRUM-28 · rédigé par E5 · paquet `ma.aquaops.simulator.scenario`.

Critère de vérification du cahier des charges : **une commande documentée par scénario**.

## 1. Les scénarios

Valeurs normales : pression 3.0 bar, débit 20.0 m3/h, niveau 5.0 m, pompe `NORMAL`.

| Nom | Ce qui se passe | Anomalie attendue |
| --- | --- | --- |
| `normal` | Tous les capteurs émettent les valeurs normales | Aucune |
| `fuite` | Après 10 s : pression 1.2 **et** débit 60.0, en même temps | `LEAK_SUSPECTED` |
| `pompe` | Après 5 s : `PUMP_STATE` passe à `FAULT`, le reste est normal | `PUMP_FAULT` |
| `silence` | Après 5 s : les capteurs de pression cessent d'émettre, les autres continuent | `SENSOR_SILENT` |
| `pic` | Après 10 s : débit 70.0, **la pression reste à 3.0** | **Aucune** : test de fausse alerte (RC-02) |

Le scénario `pic` est voulu ainsi. Un débit élevé seul ne doit pas déclencher de
fuite : la règle exige une pression basse **et** un débit élevé. Ne pas « corriger »
sa pression.

Les anomalies sont émises par `detection-service` (E3), pas par le simulateur.
Pour la fuite, la règle attend aussi que les deux conditions durent 10 s
(`LEAK_DURATION_SECONDS`) : l'anomalie arrive donc environ 20 s après le démarrage.

## 2. Commandes

Le simulateur doit tourner (`./mvnw spring-boot:run` dans `services/simulator`).

### Lister les scénarios

```bash
curl -i http://localhost:8086/api/simulator/scenarios
```

Réponse attendue, `200` :

```json
{"disponibles":["normal","fuite","pompe","silence","pic"],"actif":"normal"}
```

### Démarrer un scénario de panne

```bash
curl -i -X POST http://localhost:8086/api/simulator/scenarios/fuite/start
curl -i -X POST http://localhost:8086/api/simulator/scenarios/pompe/start
curl -i -X POST http://localhost:8086/api/simulator/scenarios/silence/start
curl -i -X POST http://localhost:8086/api/simulator/scenarios/pic/start
```

Réponse attendue, `200`, avec le nom du scénario démarré dans `actif` (ici `fuite`) :

```json
{"disponibles":["normal","fuite","pompe","silence","pic"],"actif":"fuite"}
```

Un seul scénario est actif à la fois : en démarrer un autre remplace le précédent.

Nom inconnu, réponse `400` :

```bash
curl -i -X POST http://localhost:8086/api/simulator/scenarios/nimportequoi/start
```

```json
{"message":"Scenario inconnu : nimportequoi"}
```

### Arrêter (retour au normal)

```bash
curl -i -X POST http://localhost:8086/api/simulator/scenarios/stop
```

Réponse attendue, `200` :

```json
{"disponibles":["normal","fuite","pompe","silence","pic"],"actif":"normal"}
```

## 3. À savoir

- **Retour automatique au normal après 2 minutes.** Chaque scénario de panne a une
  durée (`duration()`, 2 minutes). Au-delà, le simulateur revient seul au profil
  `normal`, et `actif` vaut `"normal"`. Pour une démonstration plus longue,
  augmenter la valeur de `duration()` dans le profil concerné
  (par exemple `LeakProfile.java`).
  **À vérifier avant la répétition du 19 novembre.**
- **`POST /normal/start` équivaut à `POST /stop`** : les deux remettent le profil normal.

## 4. Pour E1 : brancher les scénarios dans le simulateur

`ScenarioRegistry` est un bean Spring. Il dit, pour un type de capteur, ce qu'il
faut émettre maintenant. Il ne publie rien lui-même.

1. Injecter `ScenarioRegistry` dans la classe qui publie les mesures (par le constructeur).
2. Chaque seconde, pour chaque capteur, appeler `registry.mesure(capteur.type())`.
3. Si `emit()` est faux, **ne rien publier** : c'est le capteur silencieux.
4. Sinon, publier `value()`, ou `pumpState()` pour un capteur `PUMP_STATE`.
   Cela correspond au contrat `sensor-readings.v1.json`.

```java
@Scheduled(fixedRate = 1000)   // nécessite @EnableScheduling
void emettre() {
    for (Capteur c : capteurs) {
        Sample s = registry.mesure(c.type());
        if (s.emit()) publier(c, s.value(), s.pumpState());   // emit() faux : capteur silencieux
    }
}
```

`Capteur`, `capteurs` et `publier(...)` sont à toi : ils représentent ta liste de
capteurs et ton envoi vers Kafka ou Telemetry. `mesure()` renvoie la même valeur
pour tous les capteurs d'un même type.

## 5. Tests

```bash
cd services/simulator
./mvnw clean test
```

Tests unitaires purs, sans Spring ni Kafka :

- `ScenarioProfilesTests` : le comportement de chaque scénario dans le temps.
- `ScenarioRegistryTests` : le temps écoulé, le retour au normal après 2 minutes
  et le refus d'un nom inconnu, avec une horloge faussée.
