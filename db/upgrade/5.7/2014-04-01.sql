#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/5.7/2014-04-01.sql
#

# join to dwc_image.  See below.

drop view if exists dwc_image;
create view dwc_image as 
select
    concat(_utf8'antweb:',image_of_id) as occurrenceId
  , image_of_id as catalogNumber
  , concat(
      concat(
        concat(
          concat(
            concat(
              concat(
                concat(
                  concat(_utf8'http://www.antweb.org/images/'
                    , image_of_id) 
                  , '/')
                , image_of_id)
              , '_')
            , shot_type)
          , '_')
        , shot_number)
      , '_high.jpg')
    as imageUrl
#  , image_path
  , shot_type
  , shot_number
from image;

# http://www.antweb.org/images/casent0179559/casent0179559_h_1_high.jpg

drop view if exists dwc_specimen;

create VIEW dwc_specimen AS 
select 
    concat(_utf8'antweb:',code) as occurrenceId
  , ownedby as ownerInstitutionCode
  , 'preserved specimen' as basisOfRecord
  , 'antweb' as institutionCode
  , code as catalogNumber
  , last_modified as 'dcterms:modified'
  , 'ICZN' as nomenclaturalCode
  , kingdom_name as kingdom
  , phylum_name as phylum
  , class_name as class
  , order_name as 'order'
  , family
  , subfamily
#  , genus
  , CASE WHEN LEFT(`specimen`.`genus`,1) = '(' THEN
           CONCAT(LEFT(`specimen`.`genus`,1), UPPER(SUBSTR(`specimen`.`genus`, 2,1)), SUBSTR(`specimen`.`genus`,3))
     Else
           CONCAT(UPPER(LEFT(`specimen`.`genus`,1)), SUBSTR(`specimen`.`genus`,2))
END as `genus`  
  , subgenus
  , species as specificEpithet
  , subspecies as intraspecificEpithet

  # , rank is lowest level thing that is not null. 
  # , tribe
  # , speciesgroup

  , concat(genus, _utf8' ',species) AS scientific_name
  , concat(kingdom_name, _utf8';', phylum_name, _utf8';', class_name, _utf8';', order_name, _utf8';', order_name, _utf8';', family, _utf8';', subfamily) as higherClassification
  , type as typeStatus
  , adm1 as stateProvince
  , adm2 as country
  , decimal_latitude as decimalLatitude
  , decimal_longitude as decimalLongitude
  , latlonmaxerror as georeferenceRemarks
  , datedetermined as dateIdentified
  # , speciesauthor is always null in specimen
  # Something like: rainforest; sifted (leaf litter, mold, rotten wood)	 
  , concat(
        habitat
      , if (STRCMP(microhabitat, ""), concat(if (STRCMP(habitat, ""), _utf8'; ', ""),microhabitat), "")
    ) as habitat
  , collectedby as recordedBy
  , method as samplingProtocol
  , caste as sex
  , medium as preparations
  , collectioncode as fieldNumber 
  , determinedby as identifiedBy
  , localityname as locality
  , localitynotes as locationRemarks
  , specimennotes as occurrenceRemarks
  , collectionnotes as fieldNotes
  # if datecollectedend is not null then eventDate is datecollectedstart/datecollectedend.  Something like: 2001-02-11/2001-0-04
  , concat(datecollectedstart
      , if (STRCMP(datecollectedend, ""), concat(_utf8'/',datecollectedend), "")
    ) as eventDate  
  #, datecollectedstartstr as verbatimEventDate
  , concat(datecollectedstartstr
      , if (STRCMP(datecollectedendstr, ""), concat(_utf8'/',datecollectedendstr), "")
    ) as verbatimEventDate
  # if datecollectedendstr is not "" then verbatimEventDate is datecollectedstartstr/datecollectedendstr: 2 Nov 1999/8 Nov 1999
  , elevation as minimumElevationInMeters
  , biogeographicregion  
  , specimen.taxon_name as 'antweb_taxon_name'
  # , other
  # , localitycode
  # , toc
  # , access_group
  # , locatedat
  # , description.content AS author_date
  from specimen
#  from (specimen left join description on ((description.taxon_name = specimen.taxon_name))) 
# where (description.title = _utf8'speciesauthordate')
;