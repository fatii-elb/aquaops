# Compte rendu de recette RC-01 à RC-11

À remplir après l'exécution complète (carte E2 : « Correction ou escalade des écarts »). Ne rien inscrire qui n'a pas été exécuté.

- Date de la recette :
- Participantes :
- Environnement (k3s, version du commit / SHA des images) :

## Résultats

| RC | Réussi / Écart | Date | Preuve (fichier) | Commentaire |
| --- | --- | --- | --- | --- |
| RC-01 | Non exécuté | | | Responsable : E1 |
| RC-02 | Non exécuté | | | Responsable : E1 |
| RC-03 | Non exécuté | | | Responsable : E3 |
| RC-04 | Non exécuté | | | Responsable : E1 |
| RC-05 | Écart (partiel) | 18 nov. 2026 | docs/preuves/RC-05/ (avant.txt, apres.txt, sonde-disponibilite.txt) | Pod du service des actifs supprimé sur kind : rétabli en 27 s, 0 échec de la sonde (2 essais). Télémétrie sous charge, reprise Kafka et mesures envoyées = stockées non testées. |
| RC-06 | Non exécuté | | | Responsable : E3 |
| RC-07 | Réussi | 14 nov. 2026 | docs/preuves/RC-07/ (rc07-forbidden.png, rc07-api-403.png) | 4 rôles testés sur les écrans, conformes à la matrice. API testée avec le rôle opérateur seulement. |
| RC-08 | Non exécuté | | | Responsables : E3, E4 |
| RC-09 | Non exécuté | | | Responsable : E1 |
| RC-10 | Non exécuté | | | Responsable : E4 |
| RC-11 | Non exécuté | | | Responsable : E5 |

**Bilan :** 1 RC réussi sur 11 à ce jour (RC-07), 1 écart (RC-05, partiel), 9 non exécutés.

## Écarts

| RC | Écart constaté | Décision (corrigé / escaladé à l'encadrant / accepté) | Responsable | Date |
| --- | --- | --- | --- | --- |

## Seuils du cahier des charges

- Exigences Must : toutes validées ? (oui / non)
- Exigences Should : ... sur 8 validées (7 sur 8 minimum, soit 80 %)
