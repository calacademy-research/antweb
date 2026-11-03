-- READ-ONLY. Valid names with (not red-flagged) specimens in a country that are not rows of the country's list, and
-- for each: specimens, groups, any dispute on the list (the name itself, or another name with the same genus and
-- epithet), whether any saved version of the list ever had it. 2026-11-03: Madagascar was missing Parvaponera darwinii
-- (54 CAS specimens), Lepisiota capensis (39) and others. Edit the SET line for another country.
SET @country = 'Madagascar';
SELECT id INTO @gid FROM geolocale WHERE name = @country AND georank = 'country' LIMIT 1;
SELECT s.taxon_name, COUNT(*) specimens, GROUP_CONCAT(DISTINCT s.access_group) grps,
       SUM(s.is_introduced = 1) introduced_flag,
       (SELECT GROUP_CONCAT(CONCAT(d.taxon_name, ' ', d.source, ' ', DATE(d.created))) FROM geolocale_taxon_dispute d
         WHERE d.geolocale_id = @gid AND (d.taxon_name = s.taxon_name
           OR SUBSTRING_INDEX(d.taxon_name, ' ', -1) = SUBSTRING_INDEX(s.taxon_name, ' ', -1))) disputes,
       EXISTS (SELECT 1 FROM geolocale_taxon_log_detail ld WHERE ld.geolocale_id = @gid AND ld.taxon_name = s.taxon_name) ever_saved
FROM specimen s
JOIN taxon t ON t.taxon_name = s.taxon_name AND t.status = 'valid' AND t.taxarank IN ('species', 'subspecies')
WHERE (s.country = @country OR s.island_country = @country) AND s.status = 'valid'
  AND (s.flag IS NULL OR s.flag <> 'red')
  AND NOT EXISTS (SELECT 1 FROM geolocale_taxon gt WHERE gt.geolocale_id = @gid AND gt.taxon_name = s.taxon_name)
GROUP BY s.taxon_name ORDER BY specimens DESC;
SELECT COUNT(*) disputes_on_this_list, MIN(created) first, MAX(created) last FROM geolocale_taxon_dispute WHERE geolocale_id = @gid;
