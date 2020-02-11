#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.67/2012-04-25.sql
#

# new version that does not use description, does not use author_date from description table.

drop view tapir_specimen;
CREATE ALGORITHM=UNDEFINED DEFINER=antweb@localhost SQL SECURITY DEFINER VIEW tapir_specimen as 
select code, specimen.taxon_name AS taxon_name, subgenus, tribe, speciesgroup, subfamily, genus, species
, other, type, subspecies, country, adm2, adm1, localityname, localitycode, collectioncode
, biogeographicregion, decimal_latitude, decimal_longitude, last_modified, habitat, method, toc
, ownedby, collectedby, caste, access_group, locatedat, 
concat(genus,_utf8' ',species) AS scientific_name, concat(_utf8'antweb:', code) AS guid
from specimen


drop view tapir_specimen;
CREATE ALGORITHM=UNDEFINED DEFINER=antweb@localhost SQL SECURITY DEFINER VIEW tapir_specimen as 
select code, specimen.taxon_name AS taxon_name, subgenus, tribe, speciesgroup, subfamily, genus, species
, other, type, subspecies, country, adm2, adm1, localityname, localitycode, collectioncode
, biogeographicregion, decimal_latitude, decimal_longitude, last_modified, habitat, method, toc
, ownedby, collectedby, caste, access_group, locatedat, 
concat(concat(upper(substring(genus, 1, 1)), lower(substring(genus, 2))),_utf8' ',species) AS scientific_name
, concat(_utf8'antweb:', code) AS guid
from specimen


