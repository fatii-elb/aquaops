# Image Docker du frontend AquaOps

Image nginx qui sert l'application Angular et **cree `/config.json` au demarrage**
a partir de variables d'environnement. Changer une adresse = relancer le conteneur, pas reconstruire l'image.

## Variables d'environnement

| Variable | Defaut | Role |
|---|---|---|
| `KEYCLOAK_ISSUER` | `http://localhost:8180/realms/aquaops` | Adresse du realm Keycloak **telle que le navigateur la voit** (pas l'adresse interne Docker) |
| `KEYCLOAK_CLIENT_ID` | `aquaops-frontend` | Client public Keycloak |
| `GATEWAY_URL` | `http://gateway:8080` | Adresse de la passerelle, utilisee par nginx pour `/api/` (sans `/` final) |

Le conteneur s'arrete avec un message clair si une variable est vide ou mal formee.
`config.json` ne contient que des valeurs publiques, aucun secret.

## Tester

```bash
docker build -t aquaops-frontend ./frontend
docker run --rm -p 4200:8080 \
  -e GATEWAY_URL=http://host.docker.internal:8080 \
  -e KEYCLOAK_ISSUER=http://localhost:8180/realms/aquaops \
  aquaops-frontend
curl http://localhost:4200/config.json   # {"issuer": "...", "clientId": "..."}
curl http://localhost:4200/healthz       # ok
```

## Extrait pour deploy/docker-compose.yml

```yaml
  frontend:
    build: ../frontend
    ports:
      - "4200:8080"
    environment:
      GATEWAY_URL: http://gateway:8080
      KEYCLOAK_ISSUER: http://localhost:8180/realms/aquaops
      KEYCLOAK_CLIENT_ID: aquaops-frontend
    depends_on:
      - gateway
```

## Details

- Port **8080**, utilisateur non-root (nginx-unprivileged).
- `/api/` et `/api/notifications/subscribe` (SSE, sans tampon) sont envoyes a `GATEWAY_URL`.
- `index.html` et `config.json` ne sont jamais mis en cache ; les fichiers compiles (empreinte dans le nom) le sont un an.
- Toute autre adresse (`/incidents/3`...) renvoie `index.html` (routage Angular).
- L'application lit `/config.json` au demarrage (PR #74). Sans ce fichier elle utilise ses adresses locales par defaut.
