-- READ-ONLY. The disputes (names removed from a place's list) for review (2026-11-03: all 47 on Madagascar's list were
-- saved at the same second, 2018-11-16 01:37:48: an automatic job). Part 1: every place, disputes grouped by the second
-- they were saved (a bulk save shows as one line with many names). Part 2: the disputes of one country with what each
-- name is now in AntCat and its specimens there. Edit the SET line for another country.
SELECT g.name place, g.georank, d.created, COUNT(*) names, GROUP_CONCAT(DISTINCT d.source) sources,
       GROUP_CONCAT(DISTINCT d.curator_id) curators
FROM geolocale_taxon_dispute d JOIN geolocale g ON g.id = d.geolocale_id
GROUP BY g.name, g.georank, d.created ORDER BY names DESC, d.created LIMIT 60;
SET @country = 'Madagascar';
SELECT id INTO @gid FROM geolocale WHERE name = @country AND georank = 'country' LIMIT 1;
SELECT d.taxon_name, d.source, t.status, t.current_valid_name,
       (SELECT COUNT(*) FROM specimen s WHERE (s.country = @country OR s.island_country = @country) AND s.taxon_name = d.taxon_name
          AND (s.flag IS NULL OR s.flag <> 'red')) specimens_here,
       EXISTS (SELECT 1 FROM geolocale_taxon gt WHERE gt.geolocale_id = @gid AND gt.taxon_name = d.taxon_name) on_list_now
FROM geolocale_taxon_dispute d LEFT JOIN taxon t ON t.taxon_name = d.taxon_name
WHERE d.geolocale_id = @gid ORDER BY specimens_here DESC, d.taxon_name;
