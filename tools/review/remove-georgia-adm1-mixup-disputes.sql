-- DATA CHANGE (Brian Fisher, 2026-11-03): the removals (disputes) on the US state Georgia's list that belong to the
-- country Georgia (B26 name mix-up, 2018-11-16 job): the 127 names on the country Georgia's list, plus the 4 names with
-- specimens collected in the US state (Brachymyrmex patagonicus 95, Tetramorium bicarinatum 3, Camponotus atriceps 2,
-- Strumigenys membranifera 2), which the removal kept off the state's list. The 17 other names (no specimens in the
-- state, not on the country's list) stay. The names with specimens come back to the state's list at the next nightly.
-- 1. Backup table geolocale_taxon_dispute_bak_20261103_georgia. 2. Delete. 3. What is left.
-- Rollback: INSERT INTO geolocale_taxon_dispute SELECT * FROM geolocale_taxon_dispute_bak_20261103_georgia;
SELECT id INTO @state FROM geolocale WHERE name = 'Georgia' AND georank = 'adm1' AND parent = 'United States' LIMIT 1;
SELECT id INTO @country FROM geolocale WHERE name = 'Georgia' AND georank = 'country' LIMIT 1;
CREATE TABLE geolocale_taxon_dispute_bak_20261103_georgia AS
SELECT d.* FROM geolocale_taxon_dispute d WHERE d.geolocale_id = @state AND (
     EXISTS (SELECT 1 FROM geolocale_taxon c WHERE c.geolocale_id = @country AND c.taxon_name = d.taxon_name)
  OR EXISTS (SELECT 1 FROM specimen s WHERE s.country = 'United States' AND s.adm1 = 'Georgia' AND s.taxon_name = d.taxon_name));
DELETE d FROM geolocale_taxon_dispute d
JOIN geolocale_taxon_dispute_bak_20261103_georgia b ON b.geolocale_id = d.geolocale_id AND b.taxon_name = d.taxon_name;
SELECT (SELECT COUNT(*) FROM geolocale_taxon_dispute_bak_20261103_georgia) removed,
       (SELECT COUNT(*) FROM geolocale_taxon_dispute WHERE geolocale_id = @state) left_on_state;
