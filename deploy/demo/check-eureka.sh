#!/usr/bin/env bash
# Eureka : liste les services inscrits et vérifie que ceux attendus sont UP. À lancer pour la capture « service UP dans le registre ».
# Usage : bash deploy/demo/check-eureka.sh
#   EUREKA_URL (http://localhost:8761/eureka)   EXPECTED_APPS (ASSETS-SERVICE ; ex. "ASSETS-SERVICE TELEMETRY-SERVICE GATEWAY")
# Rapport : docs/preuves/Eureka/eureka-registre.txt
EUREKA="${EUREKA_URL:-http://localhost:8761/eureka}"
EXPECTED="${EXPECTED_APPS:-ASSETS-SERVICE}"
RAPPORT="${RAPPORT:-docs/preuves/Eureka/eureka-registre.txt}"
PY=$(command -v python3 || command -v python) || { echo "python3 requis"; exit 2; }
mkdir -p "$(dirname "$RAPPORT")"
rep=$(curl -s -m 10 -H "Accept: application/json" "$EUREKA/apps")
[ -z "$rep" ] && { echo "KO : registre injoignable ($EUREKA)"; exit 1; }
EXPECTED="$EXPECTED" "$PY" - "$rep" <<'PYEOF' | tee "$RAPPORT"
import json, os, sys, datetime
data = json.loads(sys.argv[1])
apps = data.get("applications", {}).get("application", [])
if isinstance(apps, dict): apps = [apps]
registre = {}
print("Registre Eureka au", datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"))
for a in apps:
    inst = a["instance"] if isinstance(a["instance"], list) else [a["instance"]]
    registre[a["name"]] = inst
    for i in inst:
        print(f"  {a['name']:<22} {i['status']:<5} {i['instanceId']}  ({i['ipAddr']}:{i.get('port', {}).get('$')})")
ko = 0
for nom in os.environ["EXPECTED"].split():
    inst = registre.get(nom, [])
    up = [i for i in inst if i["status"] == "UP"]
    if up: print(f"OK : {nom} UP ({len(up)} instance(s))")
    else: print(f"KO : {nom} absent ou pas UP"); ko += 1
sys.exit(1 if ko else 0)
PYEOF
exit "${PIPESTATUS[0]}"
