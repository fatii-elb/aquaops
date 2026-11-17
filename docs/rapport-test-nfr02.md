\# Rapport de test de performance — NFR-02



\*\*Projet :\*\* AquaOps  

\*\*Ticket :\*\* SCRUM-49 — Validation de la latence de création des incidents  

\*\*Date :\*\* 14 novembre 2026  

\*\*Résultat :\*\* CONFORME



\## 1. Objectif du test



L'objectif de NFR-02 est de vérifier que \*\*95 % des incidents sont créés dans un délai maximal de 10 secondes\*\* après la mesure ayant déclenché l'anomalie.



Le critère d'acceptation est :



\*\*P95 ≤ 10 secondes\*\*



La latence est calculée à partir des horodatages enregistrés dans PostgreSQL :



`Latence (secondes) = created\_at - measured\_at`



\## 2. Environnement de test



La campagne a été réalisée dans un environnement de développement local.



\*\*Composants utilisés :\*\*

\- Simulator : génération des mesures des capteurs.

\- Apache Kafka : transport asynchrone des événements.

\- Detection Service : détection des anomalies.

\- Incidents Service : création et persistance des incidents.

\- PostgreSQL Docker : stockage des incidents.



\*\*Chaîne de traitement testée :\*\*



`Simulator → Kafka → Detection Service → Kafka → Incidents Service → PostgreSQL`



\## 3. Méthodologie



La campagne utilise le scénario `pompe`, qui simule une panne de pompe en faisant passer son état à `FAULT`.



Le test est automatisé par le script `scripts/run\_nfr02.py`.



\*\*Configuration :\*\*

\- Nombre de cycles : 20.

\- Durée du scénario `pompe` : 7 secondes.

\- Durée du retour à `normal` : 3 secondes.

\- Nombre minimal d'incidents attendu : 30.

\- Intervalle de publication du simulateur : 1 seconde par défaut.



Chaque cycle déclenche une panne, puis rétablit l'état normal pour permettre le réarmement du mécanisme de déduplication.



Les incidents sont générés par la chaîne applicative réelle, sans insertion directe dans PostgreSQL.



Le script `scripts/check\_nfr02.py` interroge ensuite la base `aquaops\_incidents`, calcule les latences et détermine le 95e percentile.



Les incidents de type `SENSOR\_SILENT` sont exclus, car leur horodatage de mesure ne représente pas directement le déclenchement de l'anomalie.



\## 4. Résultats obtenus



\*\*Campagne exécutée le 14 novembre 2026.\*\*



| Indicateur | Résultat |

|---|---:|

| Nombre de cycles | 20 |

| Incidents analysés | 40 |

| Latence moyenne | 0,041 s |

| Latence P95 | 0,057 s |

| Latence maximale | 0,067 s |

| Incidents créés en ≤ 10 s | 40/40 |

| Pourcentage dans le délai | 100 % |

| Latences négatives | 0 |

| Verdict | CONFORME |



Les résultats détaillés sont exportés dans :



`docs/nfr02-latencies.csv`



\## 5. Analyse des résultats



La campagne a généré 40 incidents, dépassant le minimum de 30 incidents fixé pour ce test.



Le P95 mesuré est de \*\*0,057 seconde\*\*, soit 57 millisecondes.



La latence maximale observée est de 67 millisecondes.



Tous les incidents analysés ont été créés dans un délai inférieur à 10 secondes.



Le mécanisme de déduplication a également permis d'éviter la répétition continue des alertes pendant une panne, tout en autorisant de nouvelles détections après le retour à l'état normal.



\## 6. Verdict de conformité



\*\*NFR-02 : CONFORME pour la campagne réalisée.\*\*



Le critère d'acceptation est respecté :



\- P95 observé : 0,057 s.

\- P95 maximal autorisé : 10 s.

\- Pourcentage d'incidents dans le délai : 100 %.



La chaîne de traitement AquaOps satisfait donc l'exigence NFR-02 dans les conditions de cette campagne.



\## 7. Limites du test



Cette validation présente certaines limites :



\- Le test a été réalisé en environnement local.

\- Un seul type d'anomalie (`PUMP\_FAULT`) a été utilisé.

\- La campagne ne représente pas une situation de forte charge.

\- Les résultats ne garantissent pas les mêmes performances en production.



Une campagne complémentaire sous charge serait nécessaire pour évaluer le comportement de la chaîne dans des conditions plus exigeantes.



\## 8. Reproduction du test



\*\*Prérequis :\*\* PostgreSQL Docker, Kafka, Simulator, Detection Service et Incidents Service doivent être démarrés.



Depuis la racine du projet AquaOps, exécuter :



`python scripts/run\_nfr02.py --cycles 20 --fault-seconds 7 --normal-seconds 3 --min-samples 30`



Le script automatise les cycles de panne et de retour à la normale, puis lance le calcul des latences.



\*\*Fichiers associés :\*\*

\- `scripts/run\_nfr02.py` — Automatisation de la campagne.

\- `scripts/check\_nfr02.py` — Calcul des indicateurs et vérification du seuil.

\- `docs/nfr02-latencies.csv` — Résultats détaillés.

\- `docs/rapport-test-nfr02.md` — Rapport de validation.



\## 9. Conclusion



La campagne automatisée de 20 cycles a permis de mesurer la latence de création de 40 incidents réels.



Avec un \*\*P95 de 57 ms\*\*, l'objectif NFR-02 de 10 secondes est respecté dans les conditions testées.



Le ticket SCRUM-49 dispose ainsi d'une campagne reproductible, de résultats mesurés et d'un rapport documentant la conformité observée.

