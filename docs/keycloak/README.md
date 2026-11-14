# Realm Keycloak `aquaops`

Fichier : `aquaops-realm.json`, importé au démarrage (`--import-realm`) **seulement si le realm n'existe pas encore**.
Après une modification : `docker compose down` puis `up -d` (le realm de la base interne est alors recréé).

## Ce que contient le realm

| Élément | Détail |
| --- | --- |
| Rôles | `administrateur`, `superviseur`, `operateur`, `technicien` |
| Client `aquaops-frontend` | Public, Authorization Code + **PKCE S256 obligatoire**. Adresses autorisées : `http://localhost:4200/*`. Pour la VM, ajouter son adresse dans `redirectUris` (avec `/*`) et `webOrigins` (sans `/*`). |
| Client `aquaops-audit` | Compte de service, rôle `view-events` seulement. Lu par assets-service pour copier les connexions dans le journal d'audit. Son secret vient de la variable `KEYCLOAK_AUDIT_CLIENT_SECRET` (jamais écrit dans le dépôt). |
| Utilisateurs de test | `admin1`, `superviseur1`, `operateur1`, `technicien1` : mots de passe **temporaires** (voir `docs/install-guide.md`). |
| MFA (FR-25) | Flux de connexion `browser-mfa` : un code OTP est exigé pour `superviseur` et `administrateur` seulement. À la première connexion, Keycloak demande de configurer l'application d'authentification. |
| Événements | `LOGIN`, `LOGIN_ERROR`, `LOGOUT` enregistrés 7 jours. |

## Variable obligatoire

`KEYCLOAK_AUDIT_CLIENT_SECRET` doit être transmise au conteneur Keycloak (les fichiers compose le font).
Sans elle, le secret du client serait le texte `${KEYCLOAK_AUDIT_CLIENT_SECRET}`.
Pour le service des actifs : `AUDIT_KEYCLOAK_ENABLED=true`, `KEYCLOAK_BASE_URL` (adresse de Keycloak vue par le service)
et la même valeur de secret.

## Tester le MFA

1. Ouvrir l'application, se connecter avec `operateur1` : aucun code demandé.
2. Se connecter avec `superviseur1` : changement du mot de passe, puis configuration de l'OTP (FreeOTP, Google Authenticator...), puis code.
3. Reconnexion avec `superviseur1` sans code : refusée.
4. Contrôle de l'audit : `GET /api/assets/audit?action=LOGIN` (jeton superviseur ou administrateur) liste les connexions, au plus 30 s après.

Note : les scripts qui utilisent le mot de passe direct (`admin-cli`, `grant_type=password`) ne passent pas par le MFA,
et échouent tant que le mot de passe temporaire n'a pas été changé.
