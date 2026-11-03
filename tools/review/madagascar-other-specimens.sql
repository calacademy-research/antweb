-- Read-only. What "Other specimens" counts for Madagascar's genus rows (Curated view): the specimens collected in
-- Madagascar (not red-flagged) by genus and status of their name, and whether the name is on Madagascar's list.
SET SESSION TRANSACTION READ ONLY;
SELECT s.genus, t.status,
       CASE WHEN s.taxon_name LIKE '%(indet)%' THEN 'indet' WHEN gt.taxon_name IS NULL THEN 'not on list' ELSE 'on list' END AS on_list,
       COUNT(*) AS specimens, COUNT(DISTINCT s.taxon_name) AS names
FROM specimen s
JOIN taxon t ON t.taxon_name = s.taxon_name
LEFT JOIN geolocale_taxon gt ON gt.taxon_name = s.taxon_name
  AND gt.geolocale_id = (SELECT id FROM geolocale WHERE name = 'Madagascar' AND georank = 'country')
WHERE s.country = 'Madagascar' AND (s.flag IS NULL OR s.flag <> 'red') AND s.family = 'formicidae'
  AND s.genus IN ('pheidole', 'camponotus', 'strumigenys', 'tetramorium')
GROUP BY s.genus, t.status, on_list
ORDER BY s.genus, specimens DESC;
