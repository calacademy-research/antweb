-- DATA CHANGE. "(indet)" names (identified only to genus) are never rows of a place's species list (decision of
-- 2026-10-29); to put such specimens on a list they get a morphospecies code. Two were added to Mozambique's list with
-- the Species List Tool on 2026-10-31 (Lividopone (indet) and one more), plus their region and subregion proxy rows.
-- 1. Backs up the rows to geolocale_taxon_bak_20261103_indet. 2. Deletes them. 3. Shows what was removed.
-- Rollback: INSERT INTO geolocale_taxon SELECT * FROM geolocale_taxon_bak_20261103_indet;
-- (Project and bioregion lists hold specimen names of every status, "(indet)" ones too, shown only in All specimen
-- names; they are not touched.)
CREATE TABLE geolocale_taxon_bak_20261103_indet AS
SELECT gt.* FROM geolocale_taxon gt WHERE gt.taxon_name LIKE '%(indet)%'
  AND gt.source IN ('speciesListTool', 'proxyspeciesListTool', 'curator', 'speciesList', 'proxyspeciesList');
DELETE gt FROM geolocale_taxon gt
JOIN geolocale_taxon_bak_20261103_indet b ON b.geolocale_id = gt.geolocale_id AND b.taxon_name = gt.taxon_name;
SELECT g.name, g.georank, b.taxon_name, b.source, b.created
FROM geolocale_taxon_bak_20261103_indet b JOIN geolocale g ON g.id = b.geolocale_id ORDER BY g.georank, g.name;
