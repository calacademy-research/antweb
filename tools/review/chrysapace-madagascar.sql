-- Read-only. Why Chrysapace mg01 (checked in the Species List Tool for Madagascar) is not counted on the genus list.
SET SESSION TRANSACTION READ ONLY;
SELECT 'taxon' AS what, t.taxon_name, t.taxarank, t.status, t.subfamily, t.genus, t.species, t.parent_taxon_name, t.fossil, NULL AS source, NULL AS created
FROM taxon t WHERE t.taxon_name LIKE '%chrysapace%'
UNION ALL
SELECT 'madagascar list', gt.taxon_name, NULL, NULL, NULL, NULL, NULL, NULL, NULL, gt.source, DATE(gt.created)
FROM geolocale_taxon gt JOIN geolocale g ON g.id = gt.geolocale_id AND g.name = 'Madagascar' AND g.georank = 'country'
WHERE gt.taxon_name LIKE '%chrysapace%'
UNION ALL
SELECT 'madagascar dispute', d.taxon_name, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, DATE(d.created)
FROM geolocale_taxon_dispute d JOIN geolocale g ON g.id = d.geolocale_id AND g.name = 'Madagascar' AND g.georank = 'country'
WHERE d.taxon_name LIKE '%chrysapace%'
UNION ALL
SELECT CONCAT('specimens in Madagascar, flag ', COALESCE(s.flag, 'none')), s.taxon_name, NULL, s.status, s.subfamily, s.genus, s.species, NULL, NULL, NULL, CAST(COUNT(*) AS CHAR)
FROM specimen s WHERE s.country = 'Madagascar' AND s.genus = 'chrysapace'
GROUP BY s.taxon_name, s.status, s.subfamily, s.genus, s.species, s.flag;
