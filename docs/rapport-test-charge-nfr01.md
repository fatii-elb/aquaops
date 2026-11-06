# Rapport du test de charge · NFR-01 (ingestion soutenue de 500 mesures/s)

Carte SCRUM-48 · E1 (Télémétrie et Kafka) · Test réalisé le 6 novembre 2026

## 1. Objectif

Vérifier le critère de NFR-01 : **0 perte sur 5 minutes à 500 mesures/s**, c'est-à-dire que
le nombre de mesures envoyées par le simulateur est égal au nombre de mesures enregistrées en base.

## 2. Méthode

**Chaîne testée** : simulateur (mode « charge ») -> Kafka (topic `sensor-readings`) -> validation (E6, FR-04)
-> historisation PostgreSQL (E2, FR-05).

**Environnement** : poste local sous Windows, Docker Compose du dossier `deploy/` (Kafka 4.3.1 en mode KRaft,
un seul broker, une seule partition ; PostgreSQL 18.6). Le `telemetry-service` et le simulateur tournent avec
`./mvnw spring-boot:run`.

**Déroulement**
1. Lancement : `POST /api/simulator/load/start?rate=500&seconds=300`.
2. Relevé du retard du consommateur (LAG, groupe `telemetry-validation`) toutes les 10 s, jusqu'au retour à 0.
3. Lecture du statut du simulateur : `GET /api/simulator/load` (mesures tentées, acquittées, échouées).
4. Comptage en base des mesures dont `measured_at` est compris entre `startedAt` et `endedAt` :

```sql
SELECT count(*) FROM readings
WHERE measured_at >= '2026-11-06T21:06:47.040879Z'
  AND measured_at <= '2026-11-06T21:11:47.047581Z';
```

Le comptage a été fait **après** le retour du LAG à 0, pour ne pas compter de faux manquants.

## 3. Résultats

| Mesure | Valeur |
|---|---|
| Début du test (UTC) | 21:06:47 |
| Fin de l'envoi (UTC) | 21:11:47 |
| Mesures tentées / acquittées / échouées (simulateur) | 150 000 / 150 000 / 0 |
| Mesures en base sur la fenêtre du test | **150 000** |
| **Perte** | **0** |
| LAG maximal | 74 202 messages (21:11:37) |
| Retour du LAG à 0 | 21:17:41, soit environ 6 minutes après la fin de l'envoi |

Essai préliminaire de 30 s à 500 mesures/s : 15 000 envoyées, 15 000 en base, LAG de 3 252 résorbé en environ 70 s.

Relevé complet du LAG : fichier `charge-lag.txt` (une ligne toutes les 10 s).

## 4. Analyse

- **Le simulateur tient 500 mesures/s** : 150 000 envois acquittés en 300 s, aucun échec.
- **Aucune perte** : les messages en attente restent dans Kafka et sont tous traités.
- **Le consommateur n'absorbe pas 500 mesures/s en direct.** Pendant l'envoi, le LAG a augmenté d'environ
  250 messages/s (de 1 204 à 74 202 en 290 s), ce qui situe le débit de traitement autour de **250 mesures/s**,
  la moitié de la charge. Après l'envoi, le LAG a baissé au même rythme jusqu'à 0.
- **Conséquence sur la latence** : à la fin du test, une mesure attendait environ 5 minutes avant d'être
  enregistrée. Le critère de perte est atteint, mais la latence mesure -> base (et donc NFR-02, 10 s pour
  95 % des cas) ne serait pas tenue sous cette charge. À 7 mesures/s (charge normale), le LAG reste à 0.

## 5. Limites

- Test réalisé sur un seul poste, avec un seul broker Kafka et une seule partition.
- Une seule exécution de 5 minutes.
- Le débit de traitement (~250 mesures/s) est une estimation déduite de la pente du LAG.

## 6. Pistes d'optimisation (non appliquées)

Le plan prévoit « insertion par lots, index » si nécessaire. Le code d'écriture appartient à E2 (FR-05) :
ces pistes lui sont proposées avec les chiffres ci-dessus.

1. **Insertion par lots** : l'écriture se fait probablement une transaction par mesure (`repository.save`) ;
   regrouper les mesures par lot (par exemple 500) devrait augmenter fortement le débit.
2. **Plusieurs partitions** sur `sensor-readings` avec plusieurs consommateurs, si les lots ne suffisent pas.
3. Vérifier les index de la table `readings` : chaque index ralentit l'écriture.

Après optimisation, relancer le même test et comparer le LAG maximal et le temps de retour à 0.

## 7. Conclusion

Le critère **« 0 perte sur 5 minutes à 500 mesures/s » est atteint** (150 000 envoyées, 150 000 enregistrées).
Le débit de traitement en direct (~250 mesures/s) reste la limite à lever par l'insertion par lots.
