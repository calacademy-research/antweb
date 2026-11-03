-- DATA CHANGE (Brian Fisher, 2026-11-03 11:02): three names removed from lists by the 2018-11-16 job that have specimens
-- collected there come back: Senegal Camponotus acvapimensis (30, group 52), Senegal Bothroponera talpa (5), Fiji
-- Technomyrmex albipes (4). Their removals (disputes) are deleted; the next nightly puts the names on the lists.
-- Rollback: INSERT INTO geolocale_taxon_dispute SELECT * FROM geolocale_taxon_dispute_bak_20261103_senfiji;
CREATE TABLE geolocale_taxon_dispute_bak_20261103_senfiji AS
SELECT d.* FROM geolocale_taxon_dispute d JOIN geolocale g ON g.id = d.geolocale_id AND g.georank = 'country'
WHERE (g.name = 'Senegal' AND d.taxon_name IN ('formicinaecamponotus acvapimensis', 'ponerinaebothroponera talpa'))
   OR (g.name = 'Fiji' AND d.taxon_name = 'dolichoderinaetechnomyrmex albipes');
DELETE d FROM geolocale_taxon_dispute d
JOIN geolocale_taxon_dispute_bak_20261103_senfiji b ON b.geolocale_id = d.geolocale_id AND b.taxon_name = d.taxon_name;
SELECT g.name, b.taxon_name FROM geolocale_taxon_dispute_bak_20261103_senfiji b JOIN geolocale g ON g.id = b.geolocale_id;
