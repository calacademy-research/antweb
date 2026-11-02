-- Read-only review query (2026-11-02). Genus rows on place lists whose source is the AntCat import and that
-- have no species/subspecies row of that genus on the same list: the page shows a dash under Species.
-- Run: tools/review/README.md.
WITH
sg AS (
  SELECT country, island_country, adm1, subregion, region, bioregion,
         SUBSTRING_INDEX(taxon_name, ' ', 1) AS gname, COUNT(*) AS n
  FROM specimen
  WHERE (flag IS NULL OR flag <> 'red') AND family = 'formicidae' AND taxon_name IS NOT NULL
  GROUP BY country, island_country, adm1, subregion, region, bioregion, gname
),
spec AS (
  SELECT 'country' AS lvl, country AS place, NULL AS parent, gname, SUM(n) AS n FROM sg WHERE country IS NOT NULL GROUP BY country, gname
  UNION ALL
  SELECT 'country', island_country, NULL, gname, SUM(n) FROM sg WHERE island_country IS NOT NULL AND island_country <> IFNULL(country, '') GROUP BY island_country, gname
  UNION ALL
  SELECT 'adm1', adm1, country, gname, SUM(n) FROM sg WHERE adm1 IS NOT NULL AND country IS NOT NULL GROUP BY country, adm1, gname
  UNION ALL
  SELECT 'subregion', subregion, NULL, gname, SUM(n) FROM sg WHERE subregion IS NOT NULL GROUP BY subregion, gname
  UNION ALL
  SELECT 'region', region, NULL, gname, SUM(n) FROM sg WHERE region IS NOT NULL GROUP BY region, gname
  UNION ALL
  SELECT 'bioregion', bioregion, NULL, gname, SUM(n) FROM sg WHERE bioregion IS NOT NULL GROUP BY bioregion, gname
),
ac AS (
  SELECT t.taxon_name AS tname,
         COALESCE(NULLIF(t.current_valid_name, ''), t.taxon_name) AS cvname,
         t.status, t.bioregion,
         COALESCE(NULLIF(gc.valid_name, ''), gc.name) AS cty
  FROM taxon t
  LEFT JOIN geolocale gc ON gc.georank = 'country' AND gc.name = TRIM(SUBSTRING_INDEX(t.country, '(', 1))
  WHERE t.antcat = 1 AND IFNULL(t.fossil, 0) = 0
    AND ((t.country IS NOT NULL AND t.country <> '') OR (t.bioregion IS NOT NULL AND t.bioregion <> ''))
),
cand AS (
  SELECT 'geolocale' AS list_type, g.georank AS place_rank, g.name AS place, g.parent AS parent_place,
         gt.taxon_name AS genus, tg.status AS genus_status, gt.source, gt.insert_method, gt.created, gt.species_count,
         (SELECT COUNT(*) FROM geolocale_taxon gs JOIN taxon ts ON ts.taxon_name = gs.taxon_name
           WHERE gs.geolocale_id = gt.geolocale_id AND gs.taxon_name LIKE CONCAT(gt.taxon_name, ' %')
             AND ts.taxarank IN ('species', 'subspecies')) AS species_rows
  FROM geolocale_taxon gt
  JOIN geolocale g ON g.id = gt.geolocale_id
  JOIN taxon tg ON tg.taxon_name = gt.taxon_name AND tg.taxarank = 'genus'
  WHERE gt.source LIKE '%worldants' OR gt.source LIKE '%antcat'
  UNION ALL
  SELECT 'bioregion', 'bioregion', bt.bioregion_name, NULL,
         bt.taxon_name, tg.status, bt.source, bt.insert_method, bt.created, bt.species_count,
         (SELECT COUNT(*) FROM bioregion_taxon bs JOIN taxon ts ON ts.taxon_name = bs.taxon_name
           WHERE bs.bioregion_name = bt.bioregion_name AND bs.taxon_name LIKE CONCAT(bt.taxon_name, ' %')
             AND ts.taxarank IN ('species', 'subspecies'))
  FROM bioregion_taxon bt
  JOIN taxon tg ON tg.taxon_name = bt.taxon_name AND tg.taxarank = 'genus'
  WHERE bt.source LIKE '%worldants' OR bt.source LIKE '%antcat'
  UNION ALL
  SELECT 'project', 'project', pt.project_name, p.country,
         pt.taxon_name, tg.status, pt.source, NULL, pt.created, pt.species_count,
         (SELECT COUNT(*) FROM proj_taxon ps JOIN taxon ts ON ts.taxon_name = ps.taxon_name
           WHERE ps.project_name = pt.project_name AND ps.taxon_name LIKE CONCAT(pt.taxon_name, ' %')
             AND ts.taxarank IN ('species', 'subspecies'))
  FROM proj_taxon pt
  JOIN taxon tg ON tg.taxon_name = pt.taxon_name AND tg.taxarank = 'genus'
  LEFT JOIN project p ON p.project_name = pt.project_name
  WHERE pt.project_name NOT IN ('worldants', 'allantwebants', 'fossilants')
    AND (pt.source LIKE '%worldants' OR pt.source LIKE '%antcat')
),
orphan AS (
  SELECT c.*,
         CASE c.place_rank WHEN 'country' THEN c.place WHEN 'adm1' THEN c.parent_place WHEN 'project' THEN c.parent_place END AS origin_cty
  FROM cand c
  WHERE c.species_rows = 0
)
SELECT o.list_type, o.place_rank, o.place, o.parent_place, o.genus, o.genus_status, o.source, o.insert_method,
       DATE(o.created) AS row_created, o.species_count AS stored_species_count, o.species_rows,
       IFNULL(CASE
         WHEN o.place_rank = 'adm1' THEN (SELECT s.n FROM spec s WHERE s.lvl = 'adm1' AND s.place = o.place AND s.parent = o.parent_place AND s.gname = o.genus)
         WHEN o.place_rank = 'project' THEN (SELECT SUM(s.n) FROM spec s WHERE s.lvl = 'country' AND s.place = o.parent_place AND s.gname = o.genus)
         ELSE (SELECT SUM(s.n) FROM spec s WHERE s.lvl = o.place_rank AND s.place = o.place AND s.gname = o.genus)
       END, 0) AS specimens,
       (SELECT SUBSTRING(GROUP_CONCAT(DISTINCT a.tname ORDER BY a.tname SEPARATOR '; '), 1, 300) FROM ac a
         WHERE a.cvname LIKE CONCAT(o.genus, ' %')
           AND ((o.origin_cty IS NOT NULL AND a.cty = o.origin_cty) OR (o.list_type = 'bioregion' AND a.bioregion = o.place))) AS antcat_now_in_genus,
       (SELECT SUBSTRING(GROUP_CONCAT(DISTINCT CONCAT(a.tname, ' -> ', a.cvname) ORDER BY a.tname SEPARATOR '; '), 1, 300) FROM ac a
         WHERE a.tname LIKE CONCAT(o.genus, ' %') AND a.cvname NOT LIKE CONCAT(o.genus, ' %')
           AND ((o.origin_cty IS NOT NULL AND a.cty = o.origin_cty) OR (o.list_type = 'bioregion' AND a.bioregion = o.place))) AS antcat_moved_out_of_genus,
       CASE
         WHEN o.source LIKE '%worldants' THEN 'legacy: old source name worldants, never deleted by the nightly'
         WHEN o.place_rank IN ('subregion', 'region') THEN 'proxy of a country row (see the country rows)'
         WHEN EXISTS (SELECT 1 FROM ac a WHERE a.tname LIKE CONCAT(o.genus, ' %') AND a.cvname NOT LIKE CONCAT(o.genus, ' %')
                        AND ((o.origin_cty IS NOT NULL AND a.cty = o.origin_cty) OR (o.list_type = 'bioregion' AND a.bioregion = o.place)))
           THEN 'AntCat type locality of a name now in another genus'
         WHEN EXISTS (SELECT 1 FROM ac a WHERE a.cvname LIKE CONCAT(o.genus, ' %')
                        AND ((o.origin_cty IS NOT NULL AND a.cty = o.origin_cty) OR (o.list_type = 'bioregion' AND a.bioregion = o.place)))
           THEN 'AntCat type locality in this genus, but its species row is gone'
         ELSE 'no AntCat name now places this genus here'
       END AS likely_origin
FROM orphan o
ORDER BY o.list_type, o.place_rank, o.place, o.genus;
