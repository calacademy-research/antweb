-- DATA CHANGE (Brian Fisher, 2026-11-03: a genus stays on a place's list only with a species row of that genus or a
-- specimen of it there; the literature-only genus rows from the AntWiki import of 2021 go). Staging first.
-- 1. Backs up the rows to geolocale_taxon_bak_20261103_litgenus. 2. Deletes them. 3. Shows what was removed.
-- Rollback: INSERT INTO geolocale_taxon SELECT * FROM geolocale_taxon_bak_20261103_litgenus;
-- Rows removed: source antwiki, a genus, no species/subspecies row of that genus on the same list, no specimen of the
-- genus collected in the country (not red-flagged). Countries only (the 102 rows of 2026-11-02); proxy rows of regions
-- and subregions follow at the next nightly run.
CREATE TABLE geolocale_taxon_bak_20261103_litgenus AS
SELECT gt.* FROM geolocale_taxon gt
JOIN geolocale g ON g.id = gt.geolocale_id AND g.georank = 'country'
JOIN taxon t ON t.taxon_name = gt.taxon_name AND t.taxarank = 'genus'
WHERE gt.source = 'antwiki'
  AND NOT EXISTS (SELECT 1 FROM geolocale_taxon s WHERE s.geolocale_id = gt.geolocale_id AND s.taxon_name LIKE CONCAT(gt.taxon_name, ' %'))
  AND NOT EXISTS (SELECT 1 FROM specimen sp WHERE sp.country = g.name AND sp.subfamily = t.subfamily AND sp.genus = t.genus
                  AND (sp.flag IS NULL OR sp.flag <> 'red'));
DELETE gt FROM geolocale_taxon gt
JOIN geolocale_taxon_bak_20261103_litgenus b ON b.geolocale_id = gt.geolocale_id AND b.taxon_name = gt.taxon_name;
SELECT COUNT(*) AS rows_removed, COUNT(DISTINCT b.geolocale_id) AS countries,
       SUBSTRING(GROUP_CONCAT(DISTINCT CONCAT(g.name, ': ', b.taxon_name) ORDER BY g.name SEPARATOR '; '), 1, 600) AS examples
FROM geolocale_taxon_bak_20261103_litgenus b JOIN geolocale g ON g.id = b.geolocale_id;
