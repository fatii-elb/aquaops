-- Une base par service (docs/conventions.md, section 6).
-- Execute par PostgreSQL au tout premier demarrage du conteneur,
-- seulement quand le volume pgdata est vide.
-- Pour le relancer : docker compose down -v (efface les donnees), puis up -d.
--
-- Le role aquaops est cree par l'image a partir de POSTGRES_USER.
-- Si DB_USER est change, remplacer aussi "aquaops" ci-dessous.

CREATE DATABASE aquaops_assets OWNER aquaops;
CREATE DATABASE aquaops_telemetry OWNER aquaops;
CREATE DATABASE aquaops_incidents OWNER aquaops;
