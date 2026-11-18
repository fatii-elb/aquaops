# Dossier de tests LIV-07 — AquaOps

**Responsable :** E1
**Version :** trame initiale, à compléter avec les preuves de RC-04 à RC-11 et à relire.

## 1. Objet et périmètre

Ce dossier rassemble la méthode et les résultats des scénarios de recette RC-01
à RC-11. Il couvre les tests fonctionnels, de charge et de performance, de
résilience et de sécurité. Les scénarios et critères de recette de référence
restent dans [`recette.md`](recette.md); le suivi d'exécution commun est dans
[`recette-compte-rendu.md`](recette-compte-rendu.md).

Un résultat est déclaré conforme seulement si le critère annoncé a été mesuré
et si la preuve est accessible. Les écarts de méthode, limites de
l'environnement et scénarios non exécutés sont conservés dans le dossier;
ils ne sont pas assimilés à des réussites.

## 2. Trame d'une campagne

Pour chaque scénario, consigner :

| Champ | Information à renseigner |
| --- | --- |
| Scénario et exigence | Numéro RC, exigence NFR/FR, responsable |
| Date et version | Date/heure, branche et SHA des images ou du commit |
| Environnement | Local, Compose, kind ou k3s; versions et configuration pertinente |
| Préconditions | Services, données et comptes nécessaires |
| Procédure | Commandes, paramètres et nombre de répétitions |
| Résultat attendu | Seuil mesurable repris de `recette.md` |
| Résultat observé | Mesures brutes, verdict et écarts |
| Preuve | Chemin du journal, rapport ou capture archivé dans le dépôt |
| Limites | Écarts au scénario, réserves et suites à donner |

## 3. Classement des scénarios

| Famille | Scénarios | Mesure principale |
| --- | --- | --- |
| Fonctionnel | RC-01, RC-07 | Parcours achevé et autorisations conformes |
| Charge et performance | RC-02, RC-03, RC-04 | Perte de mesures, latence, nombre de pods |
| Panne et résilience | RC-05, RC-06, RC-11 | Temps de reprise, erreurs pendant mise à jour, reconstruction |
| Sécurité | RC-07, RC-08, RC-09 | Refus d'accès, blocage des contrôles, authentification et limitation |
| Observabilité | RC-10 | Trace distribuée de bout en bout |

## 4. Résultats déjà disponibles

| RC | Résultat observé | Preuve et réserves |
| --- | --- | --- |
| RC-01 — parcours complet | Le journal indique que les dix étapes ont réussi : fuite simulée, alerte, incident créé, confirmé, affecté, intervention terminée, incident clos et historique présent. | [Journal RC-01](preuves/RC-01/rc01-rapport.txt). Le journal indique l'heure de détection, mais pas l'environnement ni le SHA testé; compléter ces métadonnées si elles sont disponibles. |
| RC-02 — NFR-01, charge | **Critère zéro perte atteint** : à 500 mesures/s pendant 300 s, 150 000 mesures tentées et acquittées, 0 échec et 150 000 en base. | [Rapport NFR-01](rapport-test-charge-nfr01.md) et [relevé du lag](charge-lag.txt). Réserves : un seul essai local, un broker et une partition; lag maximal de 74 202, revenu à zéro environ six minutes après la fin de l'envoi. Le critère de perte est atteint, mais le retard montre que la latence sous cette charge n'est pas garantie. |
| RC-03 — NFR-02, latence | Le rapport SCRUM-49 mesure 40 incidents : P95 de 0,057 s, maximum de 0,067 s, 40/40 sous 10 s. | [Rapport NFR-02](rapport-test-nfr02.md) et [données de latence](nfr02-latencies.csv). **À confirmer pour RC-03** : la procédure provisoire de `recette.md` demande 50 répétitions sur le scénario « fuite », alors que le rapport utilise 20 cycles du scénario « pompe ». Le résultat est utile, mais ne prouve pas l'exécution exacte de RC-03. |
| RC-05 — NFR-05, reprise | Résultat existant : service rétabli en 22 s, 36 000 mesures acquittées = 36 000 stockées, au moins un pod prêt; lag Kafka revenu à zéro. | [Méthode et réserves RC-05](preuves/RC-05/telemetrie-methode.md) et [journal](preuves/RC-05/telemetrie-sous-charge.txt). Réserves : kind plutôt que k3s, un essai, suppression gracieuse et test sans passerelle; le scénario est à refaire sur k3s. |
| RC-07 — FR-23, rôles | Résultat existant : les quatre rôles ont été vérifiés sur les écrans `/dashboard`, `/audit` et `/administration`; les accès observés correspondent à la matrice. | [Compte rendu de recette](recette-compte-rendu.md) et preuves dans `preuves/RC-07/`. Réserve : l'API a été testée avec le rôle opérateur seulement. |

RC-01 est documenté à partir du journal conservé. Reporter son résultat dans le
compte rendu commun. RC-03 reste à exécuter selon la procédure de recette (50
répétitions sur le scénario « fuite »); le rapport NFR-02 existant est informatif
mais ne remplace pas cette exécution.

## 5. Scénarios à compléter

| RC | État actuel | Preuve attendue |
| --- | --- | --- |
| RC-04 — autoscaling telemetry | Non exécuté; observation k3s à réaliser | Observations HPA et pods : montée à au moins 4 pods sous charge, puis retour à 2 |
| RC-06 — mise à jour sous trafic | Non exécuté | Mesure des erreurs HTTP 5xx pendant la mise à jour |
| RC-08 — contrôles du pipeline | Non exécuté | Résultats des essais Gitleaks, Trivy et SCA, incluant le blocage puis le passage après correction |
| RC-09 — ingestion sécurisée | Non exécuté comme scénario de recette | Réponses 401 et 429, journal sans secret, puis mesure valide acceptée |
| RC-10 — traçage | Non exécuté | Capture ou export d'une trace traversant gateway, telemetry, détection et incidents |
| RC-11 — reconstruction | Non exécuté | Journaux de `terraform destroy`, `terraform apply` et vérification de disponibilité |

Pour chaque scénario, ajouter le résultat daté, l'environnement, la preuve et les
réserves, puis reporter le verdict dans le compte rendu commun. Les preuves
doivent être archivées sous `docs/preuves/RC-xx/`.

## 6. Relecture et clôture

- Vérifier que chaque résultat correspond à la procédure et au seuil de
  `recette.md`.
- Vérifier que chaque preuve référencée existe et peut être consultée.
- Distinguer « réussi », « réussi avec réserves », « écart » et « non exécuté ».
- Faire relire le dossier par l'équipe et noter la date et les personnes ayant
  participé à la relecture.
