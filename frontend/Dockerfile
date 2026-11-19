# syntax=docker/dockerfile:1
# =============================================================================
# AquaOps : image du frontend Angular (nginx, sans droits root)
# Carte [TECH] Dockerfile du frontend · E4
#
# Construction (depuis le dossier frontend/) :
#     docker build -t aquaops-frontend .
# Lancement :
#     docker run -p 4200:8080 -e GATEWAY_URL=http://host.docker.internal:8080 aquaops-frontend
#
# Les adresses (Keycloak, passerelle) ne sont PAS dans l'image : elles sont lues
# au demarrage du conteneur. Changer une adresse = relancer, pas reconstruire.
# Voir nginx/README.md pour la liste des variables.
# =============================================================================

# ---- Etape 1 : construction de l'application ------------------------------
FROM node:22-alpine AS build
WORKDIR /app

# On copie d'abord les fichiers de dependances : tant qu'ils ne changent pas,
# Docker reutilise la couche "npm ci" et la construction est bien plus rapide.
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
# Le dossier de sortie s'appelle dist/<nom du projet>/browser : on le range dans /out.
RUN npm run build -- --configuration production \
 && mkdir /out \
 && cp -r dist/*/browser/. /out/

# ---- Etape 2 : image finale (nginx, utilisateur 101, port 8080) ------------
FROM nginxinc/nginx-unprivileged:1.30-alpine

# Valeurs par defaut = environnement local. Elles sont remplacees par -e ... ou par le compose.
ENV GATEWAY_URL=http://gateway:8080 \
    KEYCLOAK_ISSUER=http://localhost:8180/realms/aquaops \
    KEYCLOAK_CLIENT_ID=aquaops-frontend \
    ASSETS_API_BASE_URL=/api/assets \
    TELEMETRY_API_BASE_URL=/api/telemetry \
    SENSORS_MODE=api \
    SENSOR_SILENCE_THRESHOLD_SECONDS=10 \
    SENSOR_LOOKBACK_SECONDS=120

# L'application compilee. L'utilisateur nginx doit pouvoir ecrire config.json dans ce dossier.
USER root

# Correctifs de securite Alpine (Trivy, SCRUM-63)
# CACHE_BUST : le echo force Docker a invalider le cache de cette couche.
# On retire l'epinglage de nginx, on rafraichit l'index, on met a jour nginx, et on verifie la version.
RUN echo "Force rebuild 2026-11-19 - CVE-2026-121810" \
 && sed -i 's/^nginx=.*/nginx/' /etc/apk/world \
 && apk update \
 && apk upgrade --no-cache \
 && nginx -v

COPY --from=build /out /usr/share/nginx/html
# Le dossier lui-meme doit etre inscriptible par l'utilisateur nginx (101) pour y creer config.json.
RUN chown -R 101:0 /usr/share/nginx/html && chmod -R g+w /usr/share/nginx/html
USER 101

# Au demarrage, l'image nginx traite tout ce qui est dans /etc/nginx/templates
# (variables d'environnement remplacees) puis lance les scripts de /docker-entrypoint.d.
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY nginx/config.json.template /etc/nginx/config.json.template
COPY --chmod=755 nginx/19-check-env.sh nginx/40-config-json.sh /docker-entrypoint.d/

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=5 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1