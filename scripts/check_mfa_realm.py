#!/usr/bin/env python3
"""FR-25 : contrôle STATIQUE de la configuration MFA dans l'export du realm Keycloak.

Vérifie que le flux de connexion du realm exige un code OTP pour `superviseur` et `administrateur` seulement.
Ce contrôle lit le fichier JSON : il ne remplace pas le test réel (connexion refusée sans second facteur, voir docs/keycloak/README.md).
Usage : python scripts/check_mfa_realm.py [docs/keycloak/aquaops-realm.json]
"""
import json
import sys

chemin = sys.argv[1] if len(sys.argv) > 1 else "docs/keycloak/aquaops-realm.json"
realm = json.load(open(chemin, encoding="utf-8"))
flux = {f["alias"]: f for f in realm["authenticationFlows"]}
config = {c["alias"]: c["config"] for c in realm.get("authenticatorConfig", [])}
echecs = 0


def verifie(ok, texte):
    global echecs
    print(("OK : " if ok else "KO : ") + texte)
    if not ok:
        echecs += 1


def contient_flux(parent, enfant, exigence=None):
    return any(e.get("flowAlias") == enfant and (exigence is None or e["requirement"] == exigence)
               for e in flux[parent]["authenticationExecutions"])


navigateur = realm.get("browserFlow")
verifie(navigateur in flux, f"flux de connexion du realm : {navigateur}")
verifie(contient_flux(navigateur, "forms-mfa", "ALTERNATIVE"), "le flux de connexion contient les formulaires MFA")
for role in ("superviseur", "administrateur"):
    sous = f"MFA - {role}"
    verifie(sous in flux, f"sous-flux « {sous} » présent")
    if sous not in flux:
        continue
    verifie(contient_flux("forms-mfa", sous, "CONDITIONAL"), f"« {sous} » est conditionnel dans forms-mfa")
    execs = flux[sous]["authenticationExecutions"]
    cond = [e for e in execs if e.get("authenticator") == "conditional-user-role"]
    verifie(len(cond) == 1 and cond[0]["requirement"] == "REQUIRED", f"condition sur le rôle présente ({sous})")
    if cond:
        cfg = config.get(cond[0].get("authenticatorConfig"), {})
        verifie(cfg.get("condUserRole") == role and cfg.get("negate") == "false", f"la condition vise bien le rôle {role} (sans négation)")
    otp = [e for e in execs if e.get("authenticator") == "auth-otp-form"]
    verifie(len(otp) == 1 and otp[0]["requirement"] == "REQUIRED", f"code OTP exigé ({sous})")
roles = {r["name"] for r in realm.get("roles", {}).get("realm", [])}
verifie({"superviseur", "administrateur"} <= roles, "rôles superviseur et administrateur définis dans le realm")
verifie(not any(f"MFA - {r}" in flux for r in ("operateur", "technicien")), "aucun MFA prévu pour operateur et technicien")
print("RÉSULTAT :", "configuration conforme" if echecs == 0 else f"{echecs} point(s) KO")
sys.exit(1 if echecs else 0)
