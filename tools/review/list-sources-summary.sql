-- Read-only review query (2026-11-02): how many rows each source contributes to each kind of place list,
-- by taxon rank, with the oldest and newest row date. Sources: specimen, curator/speciesList*, antcat/proxyantcat
-- (the nightly AntCat import), worldants/proxyworldants (pre-2020 import, never refreshed), antwiki (one-time AntWiki
-- import), and others. Run: tools/review/README.md.
SELECT 'geolocale' AS list_type, g.georank AS place_rank, gt.source, IFNULL(t.taxarank, '(no taxon)') AS taxarank,
       COUNT(*) AS n_rows, COUNT(DISTINCT gt.geolocale_id) AS places, DATE(MIN(gt.created)) AS oldest, DATE(MAX(gt.created)) AS newest
FROM geolocale_taxon gt JOIN geolocale g ON g.id = gt.geolocale_id LEFT JOIN taxon t ON t.taxon_name = gt.taxon_name
GROUP BY g.georank, gt.source, t.taxarank
UNION ALL
SELECT 'bioregion', 'bioregion', bt.source, IFNULL(t.taxarank, '(no taxon)'), COUNT(*), COUNT(DISTINCT bt.bioregion_name), DATE(MIN(bt.created)), DATE(MAX(bt.created))
FROM bioregion_taxon bt LEFT JOIN taxon t ON t.taxon_name = bt.taxon_name
GROUP BY bt.source, t.taxarank
ORDER BY 1, 2, 3, 4;
