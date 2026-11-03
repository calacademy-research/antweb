-- Read-only. Chrysapace mg01 on Madagascar's list: the row now, and the species list history (each saved version of
-- the list that had it), to see whether a curator's entry was later lost.
SET SESSION TRANSACTION READ ONLY;
SELECT 'list now' AS what, gt.taxon_name, gt.source, gt.created, NULL AS log_id
FROM geolocale_taxon gt JOIN geolocale g ON g.id = gt.geolocale_id AND g.name = 'Madagascar' AND g.georank = 'country'
WHERE gt.taxon_name LIKE '%chrysapace%';
SELECT d.log_id, l.curator_id, l.is_current, l.created AS log_created, d.taxon_name, d.created AS row_created
FROM geolocale_taxon_log_detail d
JOIN geolocale_taxon_log l ON l.log_id = d.log_id
JOIN geolocale g ON g.id = l.geolocale_id AND g.name = 'Madagascar' AND g.georank = 'country'
WHERE d.taxon_name LIKE '%chrysapace%'
ORDER BY d.log_id;
SELECT l.log_id, l.curator_id, l.is_current, l.created,
       (SELECT COUNT(*) FROM geolocale_taxon_log_detail d WHERE d.log_id = l.log_id) AS rows_in_version
FROM geolocale_taxon_log l JOIN geolocale g ON g.id = l.geolocale_id AND g.name = 'Madagascar' AND g.georank = 'country'
ORDER BY l.log_id DESC LIMIT 15;
