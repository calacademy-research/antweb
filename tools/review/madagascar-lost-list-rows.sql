-- Read-only. Madagascar's species list as last saved in 2020 (log 413, 1,322 species rows) against the list today:
-- the names no longer on the list, by what the name is now (status in taxon, or gone from taxon), with examples.
SET SESSION TRANSACTION READ ONLY;
SET @g = (SELECT id FROM geolocale WHERE name = 'Madagascar' AND georank = 'country');
SET @log = (SELECT MAX(l.log_id) FROM geolocale_taxon_log l WHERE l.geolocale_id = @g AND l.created < '2026-01-01');
SELECT @log AS compared_with_log, COUNT(*) AS rows_then,
       SUM(gt.taxon_name IS NULL) AS not_on_list_now
FROM geolocale_taxon_log_detail d
LEFT JOIN geolocale_taxon gt ON gt.geolocale_id = @g AND gt.taxon_name = d.taxon_name
WHERE d.log_id = @log;
SELECT COALESCE(t.status, '(no taxon row now)') AS status_now, COUNT(*) AS names,
       SUM(EXISTS (SELECT 1 FROM specimen s WHERE s.taxon_name = d.taxon_name AND s.country = 'Madagascar')) AS with_madagascar_specimens_now,
       SUBSTRING(GROUP_CONCAT(d.taxon_name ORDER BY d.taxon_name SEPARATOR ', '), 1, 400) AS examples
FROM geolocale_taxon_log_detail d
LEFT JOIN geolocale_taxon gt ON gt.geolocale_id = @g AND gt.taxon_name = d.taxon_name
LEFT JOIN taxon t ON t.taxon_name = d.taxon_name
WHERE d.log_id = @log AND gt.taxon_name IS NULL
GROUP BY status_now ORDER BY names DESC;
