-- READ-ONLY (2026-11-03). Names removed from a place's list (disputes) that now have specimens collected there (not
-- red-flagged): a removal that may be out of date (Parvaponera darwinii in Madagascar: removed by the 2018-11-16 job,
-- then re-identified CAS specimens arrived, and the removal kept it off the list). One line per place and name.
SELECT g.name place, g.georank, d.taxon_name, d.source, DATE(d.created) removed_on, t.status,
       COUNT(s.code) specimens, GROUP_CONCAT(DISTINCT s.access_group) grps
FROM geolocale_taxon_dispute d
JOIN geolocale g ON g.id = d.geolocale_id
LEFT JOIN taxon t ON t.taxon_name = d.taxon_name
JOIN specimen s ON s.taxon_name = d.taxon_name AND (s.flag IS NULL OR s.flag <> 'red')
  AND ((g.georank = 'country' AND (s.country = g.name OR s.island_country = g.name))
    OR (g.georank = 'adm1' AND s.adm1 = g.name AND s.country = g.parent))
GROUP BY g.name, g.georank, d.taxon_name, d.source, d.created, t.status
ORDER BY specimens DESC;
