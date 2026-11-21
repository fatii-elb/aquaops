# Modèle de menaces (LIV-06) - AquaOps

## 1. Objectif et périmètre
Ce document identifie les menaces de sécurité potentielles pesant sur la plateforme AquaOps (supervision de réseau d'eau) et définit les mesures de protection (parades) mises en place.

**Périmètre :** Frontend Angular, Passerelle (Gateway), Microservices (Télémétrie, Actifs, Incidents, Détection, Notifications), Kafka, PostgreSQL, Keycloak, et l'infrastructure de déploiement (Docker/k3s).

## 2. Méthodologie
Nous utilisons la méthode **STRIDE** (Spoofing, Tampering, Repudiation, Information disclosure, Denial of service, Elevation of privilege) pour classifier les menaces.

## 3. Cartographie des menaces et parades

### A. Usurpation d'identité (Spoofing)
- **Menace :** Un utilisateur malveillant tente de se faire passer pour un administrateur pour modifier les seuils ou les actifs.
- **Parade :** 
  - Authentification centralisée et robuste via **Keycloak** (OIDC/OAuth2).
  - Utilisation de jetons JWT signés pour chaque requête vers la passerelle.
  - **MFA (OTP)** activé pour les rôles superviseur et administrateur (NFR-14, SCRUM-47).
- **Menace :** Un faux simulateur envoie de fausses mesures de télémétrie pour déclencher de fausses alertes.
- **Parade :** Authentification du simulateur (clé API ou jeton) et rejet des mesures non authentifiées (SCRUM-65).

### B. Altération des données (Tampering)
- **Menace :** Modification non autorisée des données en transit entre le client et la passerelle (attaque de l'homme du milieu).
- **Parade :** Utilisation de **TLS/HTTPS** entre le client et la passerelle (NFR-14, SCRUM-61).
- **Menace :** Modification des seuils d'alerte sans laisser de trace.
- **Parade :** Journalisation de toutes les actions sensibles dans un journal d'audit infalsifiable (FR-24).

### C. Répudiation
- **Menace :** Un utilisateur effectue une action critique (ex: suppression d'une zone) et nie l'avoir fait.
- **Parade :** Toutes les actions de modification sont enregistrées avec l'identité de l'utilisateur, l'horodatage et l'adresse IP dans la table `audit_log` (SCRUM-46).

### D. Divulgation d'informations (Information Disclosure)
- **Menace :** Un opérateur accède à des données ou des pages réservées aux superviseurs ou administrateurs.
- **Parade :** 
  - Contrôle d'accès basé sur les rôles (RBAC) sur le frontend (Garde de rôle Angular) et sur le backend (Annotations Spring Security).
  - Cloisonnement des flux inter-services via des **NetworkPolicy** sur Kubernetes (NFR-17, SCRUM-64).
- **Menace :** Fuite de mots de passe ou de secrets dans le code source.
- **Parade :** Utilisation de variables d'environnement, Gitleaks dans le pipeline CI/CD pour détecter les secrets exposés.

### E. Déni de service (Denial of Service)
- **Menace :** Un client ou un service surcharge l'API et rend la plateforme indisponible.
- **Parade :** 
  - **Limitation de débit** (Rate Limiting) sur la passerelle (NFR-18, SCRUM-65) : retourne une erreur 429.
  - Mise en place de sondes de vivacité et de résilience pour que les services redémarrent automatiquement en cas de crash (NFR-05, SCRUM-52).
  - Le service de notifications est découplé via Kafka pour ne pas impacter la télémétrie en cas de surcharge (NFR-06, SCRUM-53).

### F. Élévation de privilèges (Elevation of Privilege)
- **Menace :** Un utilisateur avec un rôle opérateur parvient à exécuter des actions d'administrateur.
- **Parade :** 
  - Contrôle d'accès strict sur la passerelle (vérification des rôles dans le JWT).
  - Interdiction d'accéder aux routes d'administration sans le rôle correspondant.

## 4. Menaces liées à l'infrastructure
- **Menace :** Accès non autorisé aux conteneurs Docker ou au cluster k3s.
- **Parade :** Isolation des conteneurs, utilisation d'images de base minimales, et scan de vulnérabilités des images (**Trivy**) dans le pipeline.
- **Menace :** Vulnérabilités dans les bibliothèques tierces (dépendances Maven/npm).
- **Parade :** Scan de sécurité des dépendances (**SCA** - OWASP Dependency-Check) et génération de SBOM (CycloneDX).

## 5. Conclusion
La plateforme AquaOps met en œuvre une défense en profondeur (défense périmétrique avec TLS, authentification forte avec Keycloak, contrôle d'accès RBAC, et surveillance de l'infrastructure). Les menaces résiduelles sont documentées et suivies dans le cadre de la recette (RC-09 pour la sécurité).
## 6. Preuves de test (traçabilité)
Chaque menace est associée à une preuve de test ou d'audit disponible dans le dépôt.

| Menace | Parade | Preuve / Référence |
|---|---|---|
| Usurpation d'identité (admin) | Keycloak + MFA | `docs/preuves/FR-25/` (MFA superviseur/opérateur), `docs/preuves/FR-24/` (refus connexion) |
| Faux simulateur | Authentification simulateur | À faire (SCRUM-65) |
| Altération en transit | TLS/HTTPS | `docs/preuves/NFR-14/` (testssl avant/après TLSOption) |
| Modification de seuils sans trace | Journal d'audit | `docs/preuves/FR-24/` (audit connexions) |
| Répudiation | Journal d'audit + IP | Idem FR-24 |
| Accès inter-services non autorisé | NetworkPolicy | `docs/preuves/SCRUM-64/networkpolicy-test.txt` (test automatisé : flux bloqué) |
| Fuite de secrets | Gitleaks | `docs/security-scans.md` (CI) |
| Déni de service (surcharge) | Rate limiting | À faire (SCRUM-65) |
| Déni de service (crash service) | Sondes + résilience | `docs/preuves/RC-05/` (sonde disponibilité, télémétrie sous charge) |
| Déni de service (surcharge notifications) | Kafka découplé | `docs/preuves/SCRUM-53/notifications-zero-loss.txt` (test arrêt notifications = 0 perte) |
| Élévation de privilèges | RBAC passerelle | `docs/preuves/FR-24/` (audit + refus opérateur) |
| Accès conteneurs/k3s | Images minimales + Trivy | `docs/security-scans.md`, `.trivyignore` (nginx 1.30.7) |
| Vulnérabilités bibliothèques | SCA + SBOM | `docs/security-scans.md`, SBOM CycloneDX |
| Configuration k3s (moindre privilège) | SA default sans binding | `docs/preuves/SCRUM-64/networkpolicy-test.txt` (section ServiceAccounts) |

**Note :** Les preuves marquées "À faire" correspondent aux cartes SCRUM-65 (rate limiting, authentification simulateur) qui seront traitées au Sprint 6.
