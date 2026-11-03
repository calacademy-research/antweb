-- Read-only. "(indet)" names (identified only to genus) as rows of place lists: they should never be list rows
-- (decision of 2026-10-29). How many, by list kind and source, and the Mozambique Lividopone row.
SET SESSION TRANSACTION READ ONLY;
SELECT 'geolocale' AS list, g.georank, gt.source, COUNT(*) AS rows_, COUNT(DISTINCT gt.geolocale_id) AS places, DATE(MAX(gt.created)) AS newest
FROM geolocale_taxon gt JOIN geolocale g ON g.id = gt.geolocale_id
WHERE gt.taxon_name LIKE '%(indet)%' GROUP BY g.georank, gt.source
UNION ALL
SELECT 'project', NULL, pt.source, COUNT(*), COUNT(DISTINCT pt.project_name), DATE(MAX(pt.created))
FROM proj_taxon pt WHERE pt.taxon_name LIKE '%(indet)%' GROUP BY pt.source
UNION ALL
SELECT 'bioregion', NULL, bt.source, COUNT(*), COUNT(DISTINCT bt.bioregion_name), DATE(MAX(bt.created))
FROM bioregion_taxon bt WHERE bt.taxon_name LIKE '%(indet)%' GROUP BY bt.source;
SELECT gt.taxon_name, gt.source, gt.insert_method, gt.created
FROM geolocale_taxon gt JOIN geolocale g ON g.id = gt.geolocale_id AND g.name = 'Mozambique' AND g.georank = 'country'
WHERE gt.taxon_name LIKE '%lividopone%';
