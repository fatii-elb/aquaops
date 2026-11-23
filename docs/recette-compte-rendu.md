# Compte rendu de recette RC-01 à RC-11

À remplir après l'exécution complète (carte E2 : « Correction ou escalade des écarts »). Ne rien inscrire qui n'a pas été exécuté.

- Date de la recette :
- Participantes :
- Environnement (k3s, version du commit / SHA des images) :

## Résultats

| RC | Réussi / Écart | Date | Preuve (fichier) | Commentaire |
| --- | --- | --- | --- | --- |
| RC-01 | Réussi (journal, environnement à préciser) | 10 nov. 2026 | docs/preuves/RC-01/rc01-rapport.txt | Les dix étapes consignées sont OK; environnement et SHA des images non indiqués dans le journal. Responsable : E1 |
| RC-02 | Réussi (zéro perte, retard sous charge) | 6 nov. 2026 | docs/rapport-test-charge-nfr01.md, docs/charge-lag.txt | 150000/150000 stockées sur 5 min à 500 mesures/s. Lag maximal : 74202, résorbé environ 6 min après l'envoi; un essai local, un broker et une partition. Responsable : E1 |
| RC-03 | Non exécuté | | | Responsable : E3 |
| RC-04 | Non exécuté | | | Responsable : E1 |
| RC-05 | Réussi (avec réserves) | 18 nov. 2026 | docs/preuves/RC-05/ (telemetrie-sous-charge.txt, telemetrie-methode.md) | Pod de télémétrie consommateur supprimé sous charge : rétabli en 22 s, 36000 envoyées = 36000 stockées. Réserves : kind, un seul essai, arrêt gracieux, sans passerelle ; à refaire sur k3s. |
| RC-06 | Non exécuté | | | Responsable : E3 |
| RC-07 | Réussi | 14 nov. 2026 | docs/preuves/RC-07/ (rc07-forbidden.png, rc07-api-403.png) | 4 rôles testés sur les écrans, conformes à la matrice. API testée avec le rôle opérateur seulement. |
| RC-08 | Non exécuté | | | Responsables : E3, E4 |
| RC-09 | Non exécuté | | | Responsable : E1 |
| RC-10 | Non exécuté | | | Responsable : E4 |
| RC-11 | Non exécuté | | | Responsable : E5 |

**Bilan :** 4 résultats consignés sur 11 à ce jour (RC-01 avec métadonnées à compléter, RC-02 avec retard sous charge, RC-05 avec réserves, RC-07), 7 non exécutés.

## Écarts

| RC | Écart constaté | Décision (corrigé / escaladé à l'encadrant / accepté) | Responsable | Date |
| --- | --- | --- | --- | --- |

## Seuils du cahier des charges

- Exigences Must : toutes validées ? (oui / non)
- Exigences Should : ... sur 8 validées (7 sur 8 minimum, soit 80 %)
