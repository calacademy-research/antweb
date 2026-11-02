-- Read-only. The genus rows of Madagascar's country list that the place page's Genera counts
-- (PlaceTotals.countsAsGenus: status valid or morphotaxon, identified to genus, no parentheses),
-- with what decides whether the list page shows them. Compare with the "All specimen names" genus list (57 rows vs 62).
SET SESSION TRANSACTION READ ONLY;
SELECT gt.taxon_name, t.status, t.fossil, t.subfamily, t.genus, gt.source, DATE(gt.created) AS created,
       (SELECT COUNT(*) FROM geolocale_taxon_dispute d WHERE d.geolocale_id = gt.geolocale_id AND d.taxon_name = gt.taxon_name) AS disputed,
       (SELECT COUNT(*) FROM geolocale_taxon s JOIN taxon ts ON ts.taxon_name = s.taxon_name
         WHERE s.geolocale_id = gt.geolocale_id AND s.taxon_name LIKE CONCAT(gt.taxon_name, ' %')) AS species_rows
FROM geolocale_taxon gt
JOIN geolocale g ON g.id = gt.geolocale_id AND g.name = 'Madagascar' AND g.georank = 'country'
JOIN taxon t ON t.taxon_name = gt.taxon_name
WHERE t.taxarank = 'genus' AND t.status IN ('valid', 'morphotaxon')
  AND t.taxon_name NOT LIKE '%(%' AND t.taxon_name NOT LIKE '%)%'
  AND COALESCE(t.genus, '') <> '' AND t.genus NOT LIKE '%(%' AND t.genus NOT LIKE '%indet%'
ORDER BY t.subfamily, t.genus;
