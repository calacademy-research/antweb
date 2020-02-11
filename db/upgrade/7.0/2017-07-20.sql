delete from geolocale where id in (select t.id from (select a.id from geolocale a, geolocale c where a.parent = c.name and c.is_valid = 0 and c.georank = "country" order by a.name) as t);

insert into geolocale(name, georank, parent, region, bioregion) values ('São Tomé & Principe', 'country', 'Middle Africa', 'Africa', 'Afrotropical');


// Fixes the lost geolocale_taxon records. Depends on sep2017 db.
insert into geolocale_taxon (geolocale_id, taxon_name, source) select geolocale_id, taxon_name, 'curator' from sep2017.geolocale_taxon where (geolocale_id, taxon_name) not in (select geolocale_id, taxon_name from geolocale_taxon);


// Try out created as original.
alter table geolocale_taxon modify column created timestamp default CURRENT_TIMESTAMP;
alter table proj_taxon modify column created timestamp default CURRENT_TIMESTAMP;


insert into museum (code, name, title, active) values ("ABS", "ABS, Lake Placid", "ABS, Lake Placid", 1);

insert into taxon (taxon_name, rank, subfamily, source, family, status) values ("(formicidae)", "subfamily", "(formicidae)", "sql", "formicidae", "morphotaxon");

---


// Here the biogeographicregion field comes from specimen.bioregion.  It changed within the last year.
drop view darwin_core_2;
CREATE ALGORITHM=UNDEFINED DEFINER=`antweb`@`localhost` SQL SECURITY DEFINER VIEW `darwin_core_2` 
AS select concat(_utf8'CAS:ANTWEB:',`specimen`.`code`) AS `occurrenceId`,`specimen`.`ownedby` AS `ownerInstitutionCode`
,'preserved specimen' AS `basisOfRecord`,'CAS' AS `institutionCode`,'ANTWEB' AS `collectionCode`
,`specimen`.`code` AS `catalogNumber`,`specimen`.`last_modified` AS `dcterms:modified`
,'ICZN' AS `nomenclaturalCode`,`specimen`.`kingdom_name` AS `kingdom`,`specimen`.`phylum_name` AS `phylum`
,`specimen`.`class_name` AS `class`,`specimen`.`order_name` AS `order`,`specimen`.`family` AS `family`
,`specimen`.`subfamily` AS `subfamily`,(case when (left(`specimen`.`genus`,1) = '(') then concat(left(`specimen`.`genus`,1),upper(substr(`specimen`.`genus`,2,1)),substr(`specimen`.`genus`,3)) else concat(upper(left(`specimen`.`genus`,1)),substr(`specimen`.`genus`,2)) end) AS `genus`
,`specimen`.`subgenus` AS `subgenus`,`specimen`.`species` AS `specificEpithet`,`specimen`.`subspecies` AS `intraspecificEpithet`,concat(`specimen`.`genus`,_utf8' ',`specimen`.`species`) AS `scientific_name`,concat(`specimen`.`kingdom_name`,_utf8';',`specimen`.`phylum_name`,_utf8';',`specimen`.`class_name`,_utf8';',`specimen`.`order_name`,_utf8';',`specimen`.`order_name`,_utf8';',`specimen`.`family`,_utf8';',`specimen`.`subfamily`) AS `higherClassification`,`specimen`.`type` AS `typeStatus`
,`specimen`.`adm1` AS `stateProvince`,`specimen`.`country` AS `country`,`specimen`.`decimal_latitude` AS `decimalLatitude`,`specimen`.`decimal_longitude` AS `decimalLongitude`,`specimen`.`latlonmaxerror` AS `georeferenceRemarks`
,`specimen`.`datedetermined` AS `dateIdentified`
,`specimen`.`datecollectedstart` AS `dateCollected`
,concat(`specimen`.`habitat`,if(strcmp(`specimen`.`microhabitat`,''),concat(if(strcmp(`specimen`.`habitat`,''),_utf8'; ','')
,`specimen`.`microhabitat`),'')) AS `habitat`,`specimen`.`collectedby` AS `recordedBy`,`specimen`.`method` AS `samplingProtocol`,`specimen`.`caste` AS `sex`,`specimen`.`medium` AS `preparations`,`specimen`.`collectioncode` AS `fieldNumber`,`specimen`.`determinedby` AS `identifiedBy`,`specimen`.`localityname` AS `locality`,`specimen`.`localitynotes` AS `locationRemarks`,`specimen`.`specimennotes` AS `occurrenceRemarks`
,`specimen`.`collectionnotes` AS `fieldNotes`,concat(`specimen`.`datecollectedstart`,if(strcmp(`specimen`.`datecollectedend`,''),concat(_utf8'/',`specimen`.`datecollectedend`),'')) AS `eventDate`,concat(`specimen`.`datecollectedstartstr`,if(strcmp(`specimen`.`datecollectedendstr`,''),concat(_utf8'/',`specimen`.`datecollectedendstr`),'')) AS `verbatimEventDate`,`specimen`.`elevation` AS `minimumElevationInMeters`
,`specimen`.`bioregion` AS `biogeographicregion`,`specimen`.`taxon_name` AS `antweb_taxon_name` from `specimen`
;

