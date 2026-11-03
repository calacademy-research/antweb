-- READ-ONLY. The 148 removals (disputes) on the list of the US state Georgia (adm1) saved by the 2018-11-16 job: are
-- they the country Georgia's names (Caucasus), mixed up by name (B26)? Per name: source, AntCat status, on the country
-- Georgia's list, specimens in the US state, specimens in the country.
SELECT id INTO @state FROM geolocale WHERE name = 'Georgia' AND georank = 'adm1' AND parent = 'United States' LIMIT 1;
SELECT id INTO @country FROM geolocale WHERE name = 'Georgia' AND georank = 'country' LIMIT 1;
SELECT @state state_id, @country country_id;
SELECT
  SUM(EXISTS (SELECT 1 FROM geolocale_taxon c WHERE c.geolocale_id = @country AND c.taxon_name = d.taxon_name)) on_country_list,
  SUM((SELECT COUNT(*) FROM specimen s WHERE s.country = 'United States' AND s.adm1 = 'Georgia' AND s.taxon_name = d.taxon_name) > 0) with_us_specimens,
  COUNT(*) disputes
FROM geolocale_taxon_dispute d WHERE d.geolocale_id = @state;
SELECT d.taxon_name, d.source, DATE(d.created) saved, t.status,
       EXISTS (SELECT 1 FROM geolocale_taxon c WHERE c.geolocale_id = @country AND c.taxon_name = d.taxon_name) on_country_list,
       (SELECT COUNT(*) FROM specimen s WHERE s.country = 'United States' AND s.adm1 = 'Georgia' AND s.taxon_name = d.taxon_name) us_specimens,
       (SELECT COUNT(*) FROM specimen s WHERE s.country = 'Georgia' AND s.taxon_name = d.taxon_name) country_specimens
FROM geolocale_taxon_dispute d LEFT JOIN taxon t ON t.taxon_name = d.taxon_name
WHERE d.geolocale_id = @state ORDER BY us_specimens DESC, on_country_list, d.taxon_name;
