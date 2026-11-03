-- READ-ONLY. Why a name was missing from a country's list (2026-11-03: Parvaponera darwinii, Madagascar; Brian added it
-- back with the Species List Tool at ~09:30). Edit the two SET lines for another name or country.
SET @name = 'ponerinaeparvaponera darwinii';
SET @country = 'Madagascar';
SELECT id INTO @gid FROM geolocale WHERE name = @country AND georank = 'country' LIMIT 1;
SELECT 'taxon' what, taxon_name, status, current_valid_name, fossil, created FROM taxon WHERE taxon_name = @name;
SELECT 'list row now' what, source, created, specimen_count, image_count FROM geolocale_taxon WHERE geolocale_id = @gid AND taxon_name = @name;
SELECT 'dispute' what, source, rev, curator_id, created FROM geolocale_taxon_dispute WHERE geolocale_id = @gid AND taxon_name = @name;
SELECT 'parked' what, list_type, list_key, source, created, parked, restored FROM taxon_set_parked WHERE taxon_name = @name AND list_key IN (@country, CAST(@gid AS CHAR));
-- each saved version of the list (Species List Tool saves): was the name on it?
SELECT 'list versions' what, l.log_id, l.created, l.curator_id,
       EXISTS (SELECT 1 FROM geolocale_taxon_log_detail d WHERE d.log_id = l.log_id AND d.taxon_name = @name) had_name
FROM geolocale_taxon_log l WHERE l.geolocale_id = @gid ORDER BY l.created DESC LIMIT 15;
SELECT 'specimens here' what, taxon_name, status, coalesce(flag, '') flag, is_introduced, coalesce(original_taxon_name, '') uploaded_as, access_group, COUNT(*) n
FROM specimen WHERE (country = @country OR island_country = @country) AND (taxon_name = @name OR original_taxon_name = @name)
GROUP BY taxon_name, status, flag, is_introduced, original_taxon_name, access_group;
SELECT 'name updates' what, mode, old_name, new_name, COUNT(*) n, MIN(run_date) first, MAX(run_date) last FROM specimen_name_update
WHERE old_name = @name OR new_name = @name GROUP BY mode, old_name, new_name;
