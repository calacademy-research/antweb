#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/5.18/2014-10-22.sql
#

# Proposed new command table by Mark
create table command (
  id int(11) NOT NULL auto_increment,
  command varchar(30) not null,
  parameter1
  curator_id int(11) not null,
  created timestamp default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  cached timestamp,
  PRIMARY KEY  (id)
);


# Proposed new view by Jay
# Group by removed at Jays request
create VIEW darwin_core_3 AS
select 
    concat(_utf8'CAS:ANTWEB:',s.code) as occurrenceId
    , s.code AS specimen_code
    , s.taxon_name AS taxon_name
    , t.fossil AS fossil
    , t.status AS taxon_status
    , s.ownedby as ownerInstitutionCode
    , 'preserved specimen' as basisOfRecord
    , 'CAS' as institutionCode
    , 'ANTWEB' as collectionCode
    , s.code as catalogNumber
    , s.last_modified as 'dcterms:modified'
    , 'ICZN' as nomenclaturalCode
    , s.kingdom_name as kingdom
    , s.phylum_name as phylum
    , s.class_name as class
    , s.order_name as 'order'
    , s.family
    , s.subfamily
    , CASE WHEN LEFT(s.genus,1) = '(' THEN
           CONCAT(LEFT(s.genus,1), UPPER(SUBSTR(s.genus, 2,1)), SUBSTR(s.genus,3))
     ELSE
           CONCAT(UPPER(LEFT(s.genus,1)), SUBSTR(s.genus,2))
     END as `genus`
    , s.subgenus AS subgenus
    , s.species as specificEpithet
    , s.subspecies as intraspecificEpithet
    , concat(s.genus, _utf8' ',s.species) AS scientific_name
    , concat(s.kingdom_name, _utf8';', s.phylum_name, _utf8';', s.class_name, _utf8';', s.order_name, _utf8';', s.order_name, _utf8';', s.family, _utf8';', s.subfamily) as higherClassification
    , s.type as typeStatus
    , s.adm1 as stateProvince
    , s.country as country
    , s.decimal_latitude as decimalLatitude
    , s.decimal_longitude as decimalLongitude
    , s.latlonmaxerror as georeferenceRemarks
    , s.datedetermined as dateIdentified

    # Something like: rainforest; sifted (leaf litter, mold, rotten wood)
    , concat(
        s.habitat
      , if (STRCMP(s.microhabitat, ""), concat(if (STRCMP(s.habitat, ""), _utf8'; ', ""),s.microhabitat), "")
    ) as habitat

    , s.collectedby as recordedBy
    , s.method as samplingProtocol
    , s.caste as sex
    , s.medium as preparations
    , s.datecollectedstart as datecollected

    , s.collectioncode as fieldNumber
    , s.determinedby as identifiedBy

    , s.localityname as locality
    , s.localitynotes as locationRemarks
    , s.specimennotes as occurrenceRemarks
    , s.collectionnotes as fieldNotes

  # if datecollectedend is not null then eventDate is datecollectedstart/datecollectedend.  Something like: 2001-02-11/2001-0-04
    , concat(s.datecollectedstart
      , if (STRCMP(s.datecollectedend, ""), concat(_utf8'/',s.datecollectedend), "")
    ) as eventDate

  #, datecollectedstartstr as verbatimEventDate
    , concat(s.datecollectedstartstr
      , if (STRCMP(s.datecollectedendstr, ""), concat(_utf8'/',s.datecollectedendstr), "")
    ) as verbatimEventDate
  # if datecollectedendstr is not "" then verbatimEventDate is datecollectedstartstr/datecollectedendstr: 2 Nov 1999/8 Nov 1999
    , s.elevation as minimumElevationInMeters
    , s.biogeographicregion AS biogeographicregion
    , s.image_count AS image_count
    from specimen s 
    inner join taxon t on t.taxon_name = s.taxon_name
#    group by s.code