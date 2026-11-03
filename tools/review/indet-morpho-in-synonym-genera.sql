-- READ-ONLY. Specimens identified only to genus ("(indet)") or to a morphospecies whose genus AntCat now calls a
-- synonym of another genus (Phyracaces, synonym of Lioponera: FMNHINS0000118007 "Phyracaces (indet)", Madagascar).
-- The nightly name update leaves morphospecies and indets alone, so these stay under the old genus.
-- One line per old genus: the genus AntCat gives as its current valid name, specimens, names, groups and countries.
SELECT og.taxon_name AS old_genus, og.status AS old_status, og.current_valid_name AS antcat_valid_genus,
       COUNT(*) AS specimens, COUNT(DISTINCT s.taxon_name) AS names,
       SUM(s.taxon_name LIKE '%(indet)%') AS indet_specimens,
       SUBSTRING(GROUP_CONCAT(DISTINCT s.access_group ORDER BY s.access_group), 1, 60) AS upload_groups,
       SUBSTRING(GROUP_CONCAT(DISTINCT s.country ORDER BY s.country SEPARATOR ', '), 1, 120) AS countries,
       SUBSTRING(GROUP_CONCAT(DISTINCT REPLACE(s.taxon_name, og.taxon_name, '') ORDER BY s.taxon_name SEPARATOR ', '), 1, 120) AS name_parts
FROM specimen s
JOIN taxon st ON st.taxon_name = s.taxon_name
JOIN taxon og ON og.taxon_name = CONCAT(s.subfamily, s.genus) AND og.taxarank = 'genus'
WHERE og.status <> 'valid'
  AND (st.status IN ('morphotaxon', 'indetermined', 'unidentifiable') OR s.taxon_name LIKE '%(indet)%')
GROUP BY og.taxon_name, og.status, og.current_valid_name
ORDER BY specimens DESC;
