delete from geolocale_taxon where taxon_name not in (select taxon_name from taxon) and source in ('specimen', 'antwiki', 'fixGeolocaleParentage');
Query OK, 1479 rows affected (1.33 sec)


# datecollected to dateCollected
# bioregion instead of biogeographicregion column of specimen.
drop view if exists darwin_core_3;
CREATE 
ALGORITHM=UNDEFINED DEFINER=antweb@localhost SQL SECURITY DEFINER 
VIEW darwin_core_3 
AS select 
  concat(_utf8'CAS:ANTWEB:', s.code) AS occurrenceId
, s.code AS specimen_code
, s.taxon_name AS taxon_name
,t.fossil AS fossil
,t.status AS taxon_status
, s.ownedby AS ownerInstitutionCode
,'preserved specimen' AS basisOfRecord
,'CAS' AS institutionCode
,'ANTWEB' AS collectionCode
, s.code AS catalogNumber
, s.last_modified AS 'dcterms:modified'
,'ICZN' AS nomenclaturalCode
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
, concat( s.genus,_utf8' ', s.species) AS scientific_name,concat( s.kingdom_name,_utf8';'
, s.phylum_name,_utf8';', s.class_name,_utf8';', s.order_name,_utf8';', s.order_name,_utf8';', s.family,_utf8';', s.subfamily) AS higherClassification
, s.type AS typeStatus
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


alter table specimen add column upload_id int;

# We will proceed with there being only dateCollectedStart and dateCollectedEnd fields
# of type String populated from the database columns ending in str.
# The other database columns of type date are to be deprecated.
# run these before the deployment
#update specimen set datecollectedstartstr = datecollectedstart;
#update specimen set datecollectedendstr = datecollectedend;
alter table specimen modify column datecollectedstart varchar(20);
alter table specimen modify column datecollectedend varchar(20);
alter table specimen modify column datedetermined varchar(20);

# Get rid of species group values
alter table taxon add column old_speciesgroup varchar(64);
update taxon set old_speciesgroup = speciesgroup;
update taxon set speciesgroup = null;
#Rows matched: 41881  Changed: 637  Warnings: 0


