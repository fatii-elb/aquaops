# Campagne de recette RC-01 à RC-11 (carte E2)

Un scénario par ligne : procédure, jeu de données, résultat attendu. La colonne **Résultat** est à remplir pendant l'exécution
(`Réussi` ou `Écart` + lien vers la preuve : capture, journal, rapport). Chaque étudiante archive ses preuves dans `docs/preuves/RC-xx/`.

> Les scénarios sont reconstitués d'après les plans de sprint (colonnes « Vérification »). Les numéros RC-02 et RC-03 sont à
> confirmer avec le cahier des charges ; corriger ce tableau si l'intitulé diffère.

| RC | Exigence | Procédure | Jeu de données | Résultat attendu | Responsable | Résultat |
| --- | --- | --- | --- | --- | --- | --- |
| RC-01 | Parcours complet | `deploy/demo/rc01.sh` : fuite simulée, alerte, incident, confirmation, affectation, intervention, résolution, clôture, audit | Zones de démonstration (`deploy/demo/demo-assets.sql`), 4 utilisateurs de test | Parcours complet sans intervention manuelle, incident clos avec son historique | E1 | |
| RC-02 | NFR-01 | Mode « charge » du simulateur à 500 mesures/s pendant 5 min ; comparer envoyées et stockées | Simulateur | 0 perte (voir `docs/rapport-test-charge-nfr01.md`) | E1 | |
| RC-03 | NFR-02 | Script de latence sur le scénario « fuite », 50 répétitions, 95e percentile | Simulateur | 95 % des alertes en 10 s au plus | E3 | |
| RC-04 | NFR-04 | Montée en charge de la télémétrie (HPA 2 à 6 pods), puis retour au calme | Simulateur en mode « charge » | 2 à 4 pods au moins sous charge, retour à 2 ensuite | E1 | |
| RC-05 | NFR-05 | Suppression d'un pod de télémétrie sous charge (`kubectl delete pod`) | Charge continue | Service rétabli en 60 s au plus, mesures envoyées = stockées | E2 | |
| RC-06 | NFR-07 | Mise à jour d'un service (nouvelle image) sous trafic continu | Trafic constant sur l'API | 0 erreur 5xx pendant toute la mise à jour | E3 | |
| RC-07 | FR-23 | Contrôle d'accès par rôle : chaque rôle tente chaque écran et chaque action (voir matrice ci-dessous) | 4 utilisateurs de test | Accès autorisés acceptés, autres refusés (403 ou écran « Accès refusé ») | E2 | Réussi le 14 nov. 2026. Écrans : 4 rôles testés sur /dashboard, /audit et /administration, conformes à la matrice. API (jeton opérateur) : GET /api/assets/audit 403, POST /api/assets/assets 403. Réserve : API testée avec le rôle opérateur seulement. |
| RC-08 | NFR-15, NFR-16 | Commit avec un faux secret, puis avec une dépendance vulnérable | Branche de test | Pipeline bloqué (Gitleaks, Trivy, Semgrep, SCA), puis accepté après correction | E3, E4 | |
| RC-09 | NFR-18 | Envoyer une mesure valide en `POST /api/telemetry/readings` sans `X-API-Key`, puis dépasser `TELEMETRY_INGEST_RATE_LIMIT` (500 req/s par défaut) avec la clé configurée | Simulateur ou script de requêtes vers la passerelle | Sans clé : `401`, rejet journalisé sans exposer la clé ; au-delà du seuil : `429` | E1 | |
| RC-10 | NFR-11 | Scénario de fuite complet observé dans Jaeger | Simulateur | Une trace unique passerelle, télémétrie, détection, incidents | E4 | |
| RC-11 | NFR-09 | `terraform destroy` puis `terraform apply` + pipeline | VM | Environnement reconstruit sans action manuelle | E5 | |

## Matrice RC-07 (contrôle d'accès par rôle)

À tester avec les 4 utilisateurs de test (`docs/keycloak/README.md`). `✔` = autorisé, `✘` = refusé.

| Écran ou action | operateur1 | technicien1 | superviseur1 | admin1 |
| --- | --- | --- | --- | --- |
| Tableau de bord, liste et détail des incidents | ✔ | ✔ | ✔ | ✔ |
| Journal d'audit (`/audit`, `GET /api/assets/audit`) | ✘ | ✘ | ✔ | ✔ |
| Gestion des actifs (`/administration`) | ✘ | ✘ | ✘ | ✔ |
| Créer ou modifier un actif (`POST/PUT /api/assets/...`) | ✘ | ✘ | ✔ | ✔ |
| Changer le statut d'une intervention | ✘ | ✔ (la sienne) | ✔ | ✔ |
| Connexion avec second facteur (FR-25) | non demandé | non demandé | demandé | demandé |

> Contrôle de l'implémentation (`SecurityConfig` d'assets-service) : les écritures sur `/api/assets/**` sont réservées à
> administrateur et superviseur ; l'écran `/administration` est réservé à l'administrateur dans le frontend. Si le tableau
> ci-dessus diffère du comportement observé, noter un **écart** plutôt que de modifier la matrice.

## Compte rendu final

À remplir après l'exécution (carte « Correction ou escalade des écarts ») : nombre de RC réussis, liste des écarts, décision pour chacun
(corrigé, escaladé à l'encadrant, accepté), date et participants.
