-- Données de démo du service d'actifs : 2 zones, 2 conduites, 1 réservoir, 2 pompes, 7 capteurs.
-- Les identifiants correspondent à services/simulator/src/main/resources/sensors.json.
-- À lancer sur une base aquaops_assets VIDE (les identifiants sont imposés).
-- Usage (depuis deploy/) :
--   docker compose exec -T postgres psql -U aquaops -d aquaops_assets < demo/demo-assets.sql

INSERT INTO zones (id, name, description) VALUES
  (1, 'Zone 1', 'Zone de démonstration 1'),
  (2, 'Zone 2', 'Zone de démonstration 2');

INSERT INTO pipes (id, name, zone_id, lengthm, diameter_mm) VALUES
  (1, 'Conduite Z1', 1, 500.0, 200),
  (2, 'Conduite Z2', 2, 400.0, 150);

INSERT INTO tanks (id, name, zone_id, capacity_m3) VALUES
  (1, 'Réservoir Z1', 1, 1000.0);

INSERT INTO pumps (id, name, zone_id) VALUES
  (1, 'Pompe Z1', 1),
  (2, 'Pompe Z2', 2);

INSERT INTO sensors (id, name, type, zone_id, asset_type, asset_id) VALUES
  (1, 'Pression Z1',   'PRESSURE',   1, 'PIPE', 1),
  (2, 'Débit Z1',      'FLOW',       1, 'PIPE', 1),
  (3, 'Niveau Z1',     'LEVEL',      1, 'TANK', 1),
  (4, 'Pompe Z1 état', 'PUMP_STATE', 1, 'PUMP', 1),
  (5, 'Pression Z2',   'PRESSURE',   2, 'PIPE', 2),
  (6, 'Débit Z2',      'FLOW',       2, 'PIPE', 2),
  (7, 'Pompe Z2 état', 'PUMP_STATE', 2, 'PUMP', 2);

-- Les prochains éléments créés par l'API recevront les numéros suivants.
SELECT setval(pg_get_serial_sequence('zones',   'id'), (SELECT max(id) FROM zones));
SELECT setval(pg_get_serial_sequence('pipes',   'id'), (SELECT max(id) FROM pipes));
SELECT setval(pg_get_serial_sequence('tanks',   'id'), (SELECT max(id) FROM tanks));
SELECT setval(pg_get_serial_sequence('pumps',   'id'), (SELECT max(id) FROM pumps));
SELECT setval(pg_get_serial_sequence('sensors', 'id'), (SELECT max(id) FROM sensors));