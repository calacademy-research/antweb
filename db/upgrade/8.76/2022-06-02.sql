# Change scientific_name of darwin_core_3 and api3_1_specimen to have the genus with initCap.

drop view if exists api3_1_specimen;
CREATE 
ALGORITHM=UNDEFINED DEFINER=antweb@localhost SQL SECURITY DEFINER 
VIEW api3_1_specimen
AS select 
  concat(_utf8'CAS:ANTWEB:', s.code) AS occurrenceId
, s.code AS specimen_code
, s.taxon_name AS taxon_name
, t.fossil AS fossil
, t.status AS taxon_status
, s.ownedby AS ownerInstitutionCode
, 'CAS' AS institutionCode
, 'AntWeb' AS dataSource
, s.last_modified
, s.kingdom_name
, s.phylum_name
, s.class_name
, s.order_name
, s.family
, s.subfamily
, (case when (left( s.genus,1) = '(') then concat(left( s.genus,1),upper(substr(s.genus,2,1)),substr( s.genus,3)) else concat(upper(left( s.genus,1)),substr( s.genus,2)) end) AS genus
, s.subgenus AS subgenus
, s.species
, s.subspecies
, concat( initCap(s.genus),_utf8' ', s.species) AS scientific_name
, concat( s.kingdom_name,_utf8';', s.phylum_name,_utf8';', s.class_name,_utf8';', s.order_name,_utf8';', s.order_name,_utf8';', s.family,_utf8';', s.subfamily) AS higherClassification
, s.type_status AS typeStatus
, s.adm1
, s.country
, s.bioregion
, s.museum as museum
, s.decimal_latitude AS decimalLatitude
, s.decimal_longitude AS decimalLongitude
, s.latlonmaxerror
, s.dateDetermined
, concat( s.habitat,if(strcmp( s.microhabitat,''),concat(if(strcmp( s.habitat,''),_utf8'; ',''), s.microhabitat),'')) AS habitats
, s.habitat
, s.microhabitat
, s.method AS samplingMethod
, s.life_stage AS lifeStageSex
, s.caste
, s.subcaste
, s.medium
, s.datecollectedstart
, s.datecollectedend
, s.collectioncode
, s.determinedby
, s.localityname
, s.localitynotes
, s.specimennotes
, s.collectionnotes
, s.elevation AS minimumElevationInMeters
, s.image_count
, s.ownedby
, s.locatedat
, s.collectedby
, s.region
, s.subregion
from (specimen s join taxon t on((t.taxon_name =  s.taxon_name)))
# where t.status = 'valid' and t.fossil = 0;  # This would effect only using valids and not fossils for subfamily and genus but potentially limit utility of api.
;	

drop view if exists darwin_core_3;
CREATE 
ALGORITHM=UNDEFINED DEFINER=antweb@localhost SQL SECURITY DEFINER 
VIEW darwin_core_3
AS select 
  concat(_utf8'CAS:ANTWEB:', s.code) AS occurrenceId
, s.code AS specimen_code
, s.taxon_name AS taxon_name
, t.fossil AS fossil
, t.status AS taxon_status
, s.ownedby AS ownerInstitutionCode
, 'preserved specimen' AS basisOfRecord
, 'CAS' AS institutionCode
, 'ANTWEB' AS collectionCode
, s.code AS catalogNumber
, s.last_modified AS 'dcterms:modified'
, 'ICZN' AS nomenclaturalCode
, s.kingdom_name AS kingdom
, s.phylum_name AS phylum
, s.class_name AS 'class'
, s.order_name AS 'order'
, s.family AS family
, s.subfamily AS subfamily
, (case when (left( s.genus,1) = '(') then concat(left( s.genus,1),upper(substr(s.genus,2,1)),substr( s.genus,3)) else concat(upper(left( s.genus,1)),substr( s.genus,2)) end) AS genus
, s.subgenus AS subgenus
, s.species AS specificEpithet
, s.subspecies AS intraspecificEpithet
, concat( initCap(s.genus),_utf8' ', s.species) AS scientific_name
, concat( s.kingdom_name,_utf8';', s.phylum_name,_utf8';', s.class_name,_utf8';', s.order_name,_utf8';', s.order_name,_utf8';', s.family,_utf8';', s.subfamily) AS higherClassification
, s.type_status AS typeStatus
, s.adm1 AS stateProvince
, s.country AS country
, s.decimal_latitude AS decimalLatitude
, s.decimal_longitude AS decimalLongitude
, s.latlonmaxerror AS georeferenceRemarks
, s.datedetermined AS dateIdentified
, concat( s.habitat,if(strcmp( s.microhabitat,''),concat(if(strcmp( s.habitat,''),_utf8'; ','')
, s.microhabitat),'')) AS habitat
, s.collectedby AS recordedBy, s.method AS samplingProtocol
, s.caste AS sex
, s.medium AS preparations
, s.datecollectedstart AS dateCollected
, s.collectioncode AS fieldNumber
, s.determinedby AS identifiedBy
, s.localityname AS locality
, s.localitynotes AS locationRemarks
, s.specimennotes AS occurrenceRemarks
, s.collectionnotes AS fieldNotes
, concat( s.datecollectedstart,if(strcmp( s.datecollectedend,''), concat(_utf8'/', s.datecollectedend),'')) AS eventDate
, concat( s.datecollectedstartstr,if(strcmp( s.datecollectedendstr,''), concat(_utf8'/', s.datecollectedendstr),'')) AS verbatimEventDate
, s.elevation AS minimumElevationInMeters
, s.bioregion AS biogeographicregion
, s.image_count AS image_count 
, s.museum as museum
, s.ownedby as ownedby
, s.locatedat as locatedat
, s.collectedby as collectedby
from (specimen s join taxon t on((t.taxon_name =  s.taxon_name)))
;

