-- READ-ONLY (2026-11-03). Why the nightly (set 2, GeolocaleTaxonDb.insertGeolocaleTaxaFromSpecimens) did not put
-- Parvaponera darwinii (CAS specimens, re-identified from P. madecassa 2025-12-21) on Madagascar's list.
SELECT id, name, georank, is_valid, is_live, is_use_children, bioregion FROM geolocale WHERE name = 'Madagascar';
SELECT id INTO @gid FROM geolocale WHERE name = 'Madagascar' AND georank = 'country' LIMIT 1;
-- 1. Madagascar's list rows by source (does the nightly add specimen rows to this list at all?)
SELECT source, COUNT(*) n FROM geolocale_taxon WHERE geolocale_id = @gid GROUP BY source ORDER BY n DESC;
-- 2. the nightly's own select, for this name: does it return Madagascar?
SELECT DISTINCT country, adm1, taxon_name, family, subfamily, status, flag FROM specimen
WHERE taxon_name IN ('ponerinaeparvaponera darwinii', 'ponerinaeparvaponera madecassa')
  AND (country = 'Madagascar' OR island_country = 'Madagascar') LIMIT 12;
-- 3. the subfamily check the nightly uses
SELECT taxon_name, taxarank, family, status FROM taxon WHERE taxon_name IN ('ponerinae', 'ponerinaeparvaponera',
  'ponerinaeparvaponera darwinii', 'ponerinaeparvaponera madecassa');
-- 4. when was a specimen row last added to Madagascar's list (is set 2 adding anything there?)
SELECT MAX(created) last_specimen_row, COUNT(*) FROM geolocale_taxon WHERE geolocale_id = @gid AND source LIKE '%specimen%';
