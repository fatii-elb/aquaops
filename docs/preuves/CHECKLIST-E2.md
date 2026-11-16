# Checklist des preuves : carte E2

Une preuve = un fichier dans `docs/preuves/<carte>/`. Cocher quand le fichier est dans le dépôt. Les captures montrent **la date et l'heure** (barre des tâches ou `date` dans le terminal).

| ☐ | Carte | À faire | Commande ou action | Fichier à déposer |
|---|---|---|---|---|
| ☐ | Eureka (actifs) | Registre avec assets-service UP | `bash deploy/demo/check-eureka.sh`, puis capture de `http://localhost:8761` | `docs/preuves/Eureka/eureka-registre.txt` et `eureka-actifs-up.png` |
| ☐ | MFA (SCRUM-47) | Contrôle de la config | `python scripts/check_mfa_realm.py` | `docs/preuves/FR-25/mfa-config.txt` (copier la sortie) |
| ☐ | MFA | Opérateur : aucun code demandé | Connexion `operateur1` | `docs/preuves/FR-25/mfa-operateur-sans-otp.png` |
| ☐ | MFA | Superviseur : code demandé | Connexion `superviseur1` (écran de configuration OTP, puis écran du code) | `docs/preuves/FR-25/mfa-superviseur-otp.png` |
| ☐ | MFA | Connexion refusée sans code | Reconnexion `superviseur1` avec un faux code | `docs/preuves/FR-25/mfa-code-refuse.png` |
| ☐ | Audit connexions | Connexions dans le journal | Après les connexions ci-dessus : `GET /api/assets/audit?action=LOGIN` (30 s au plus) | `docs/preuves/FR-24/audit-connexions.png` |
| ☐ | TLS (SCRUM-61) | Appliquer `tlsoption.yaml` puis refaire le scan **sur k3s** | `kubectl apply -f deploy/k8s/tls/` puis `testssl.sh --color 0 --logfile testssl-k3s.log <hôte>:443` | `docs/preuves/NFR-14/testssl-k3s.txt` (LUCKY13 ne doit plus apparaître) |
| ☐ | TLS | Dashboard en HTTPS | Capture du cadenas / `https://...` | `docs/preuves/NFR-14/https-dashboard.png` |
| ☐ | RC-05 (SCRUM-52) | Test complet | `bash deploy/demo/rc05.sh` en filmant `kubectl -n aquaops get pods -l app=telemetry -w` | `docs/preuves/RC-05/rc05-rapport.txt` et `rc05-pods.png` |
| ☐ | RC-07 | API avec les 4 rôles | `bash deploy/demo/rc07-api.sh` | `docs/preuves/RC-07/rc07-api-roles.txt` (le script l'écrit) |

## Après chaque preuve

1. Renseigner la colonne **Résultat** du tableau de `docs/recette.md` (`Réussi le <date>` ou `Écart` + lien vers le fichier).
2. En cas d'écart, ne pas modifier la matrice ni le critère : le noter dans le compte rendu (`docs/recette-compte-rendu.md`).
