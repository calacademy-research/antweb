-- READ-ONLY (2026-11-03, staging after a manual run of generateGeolocaleTaxaFromSpecimens at 10:51): where the nightly
-- put Parvaponera darwinii, and any dispute or odd row that could stop the Madagascar country row.
SELECT g.id, g.name, g.georank, gt.source, gt.insert_method, gt.created FROM geolocale_taxon gt JOIN geolocale g ON g.id = gt.geolocale_id
WHERE gt.taxon_name IN ('ponerinaeparvaponera darwinii', 'ponerinaeparvaponera') ORDER BY g.georank, g.name;
SELECT d.geolocale_id, g.name, d.taxon_name, d.source, d.created FROM geolocale_taxon_dispute d LEFT JOIN geolocale g ON g.id = d.geolocale_id
WHERE d.taxon_name LIKE '%parvaponera%' OR d.taxon_name LIKE '%darwinii%';
SELECT id, name, georank, parent, is_valid FROM geolocale WHERE name = 'Madagascar';
SELECT HEX(country) hx, country, COUNT(*) FROM specimen WHERE taxon_name = 'ponerinaeparvaponera darwinii' GROUP BY country;
