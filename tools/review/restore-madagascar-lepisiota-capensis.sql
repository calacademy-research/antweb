-- DATA CHANGE (Brian Fisher, 2026-11-03): Lepisiota capensis is not a wrong label in Madagascar (39 CAS specimens); its
-- removal from Madagascar's list (source antwiki, 2018-11-16 job) goes, so the nightly puts it back on the list.
-- Rollback: INSERT INTO geolocale_taxon_dispute SELECT * FROM geolocale_taxon_dispute_bak_20261103_lepisiota;
SELECT id INTO @gid FROM geolocale WHERE name = 'Madagascar' AND georank = 'country' LIMIT 1;
CREATE TABLE geolocale_taxon_dispute_bak_20261103_lepisiota AS
SELECT * FROM geolocale_taxon_dispute WHERE geolocale_id = @gid AND taxon_name = 'formicinaelepisiota capensis';
DELETE FROM geolocale_taxon_dispute WHERE geolocale_id = @gid AND taxon_name = 'formicinaelepisiota capensis';
SELECT COUNT(*) removed FROM geolocale_taxon_dispute_bak_20261103_lepisiota;
-- Polyrhachis schistacea (a wrong label, Brian): why it is not on the list now
SELECT 'polyrhachis schistacea' what, t.status, t.taxarank, t.current_valid_name,
       (SELECT COUNT(*) FROM specimen s WHERE (s.country = 'Madagascar' OR s.island_country = 'Madagascar')
          AND s.taxon_name = 'formicinaepolyrhachis schistacea') specimens,
       (SELECT GROUP_CONCAT(DISTINCT CONCAT(s.code, ' ', s.status, ' flag:', COALESCE(s.flag, ''))) FROM specimen s
          WHERE (s.country = 'Madagascar' OR s.island_country = 'Madagascar') AND s.taxon_name = 'formicinaepolyrhachis schistacea') codes
FROM taxon t WHERE t.taxon_name = 'formicinaepolyrhachis schistacea';
