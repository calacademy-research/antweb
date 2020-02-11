
drop view if exists taxon_image;
create view taxon_image as 
select t.taxon_name, t.subfamily, t.genus, t.species, t.subspecies, s.code, i.uid
  , i.image_path, i.shot_type, i.resolution, i.desc_title, i.width, i.height, i.shot_number
  , i.artist, i.copyright, i.license, i.upload_date, i.has_tiff 
from taxon t, specimen s, image i 
where t.taxon_name = s.taxon_name 
  and s.code = i.image_of_id;



drop view if exists api3_specimen;
CREATE 
ALGORITHM=UNDEFINED DEFINER=antweb@localhost SQL SECURITY DEFINER 
VIEW api3_specimen
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
, s.type_status AS typeStatus
, s.adm1 AS stateProvince
, s.country AS country
, s.bioregion as bioregion
, s.status as status
, s.decimal_latitude AS decimalLatitude
, s.decimal_longitude AS decimalLongitude
, s.latlonmaxerror AS georeferenceRemarks
, s.datedetermined AS dateIdentified
, concat( s.habitat,if(strcmp( s.microhabitat,''),concat(if(strcmp( s.habitat,''),_utf8'; ',''), s.microhabitat),'')) AS habitats
, s.habitat as habitat
, s.microhabitat as microhabitat
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


alter table geolocale_taxon add unique key (geolocale_id, taxon_name);
alter table geolocale_taxon add KEY taxon_name (taxon_name);
  
alter table upload add column (upload_id int);

update upload set upload_id = 1 where id = 2938;
update upload set upload_id = 2 where id = 2939;
update upload set upload_id = 3 where id = 2940;
update upload set upload_id = 4 where id = 2941;
update upload set upload_id = 5 where id = 2942;
update upload set upload_id = 6 where id = 2943;
update upload set upload_id = 7 where id = 2944;
update upload set upload_id = 8 where id = 2945;
update upload set upload_id = 9 where id = 2946;
update upload set upload_id = 10 where id = 2947;
update upload set upload_id = 11 where id = 2948;
update upload set upload_id = 12 where id = 2949;
update upload set upload_id = 13 where id = 2950;
#update upload set upload_id = 13 where id = 2951;
update upload set upload_id = 14 where id = 2952;
#update upload set upload_id = 14 where id = 2953;
update upload set upload_id = 15 where id = 2954;
update upload set upload_id = 16 where id = 2955;
update upload set upload_id = 17 where id = 2956;
update upload set upload_id = 18 where id = 2957;
update upload set upload_id = 19 where id = 2958;
update upload set upload_id = 20 where id = 2959;
update upload set upload_id = 21 where id = 2960;
update upload set upload_id = 22 where id = 2961;
update upload set upload_id = 23 where id = 2962;
update upload set upload_id = 24 where id = 2963;
update upload set upload_id = 25 where id = 2964;
update upload set upload_id = 26 where id = 2965;
update upload set upload_id = 27 where id = 2966;
update upload set upload_id = 28 where id = 2967;
update upload set upload_id = 29 where id = 2968;
update upload set upload_id = 30 where id = 2969;
update upload set upload_id = 31 where id = 2970;
update upload set upload_id = 32 where id = 2971;
update upload set upload_id = 33 where id = 2972;
update upload set upload_id = 34 where id = 2973;
update upload set upload_id = 35 where id = 2974;
update upload set upload_id = 36 where id = 2975;
update upload set upload_id = 37 where id = 2976;
update upload set upload_id = 38 where id = 2977;
update upload set upload_id = 38 where id = 2978;
update upload set upload_id = 40 where id = 2979;
update upload set upload_id = 45 where id = 2980;
update upload set upload_id = 46 where id = 2981;
update upload set upload_id = 47 where id = 2982;
update upload set upload_id = 48 where id = 2983;
update upload set upload_id = 49 where id = 2984;
update upload set upload_id = 53 where id = 2985;
update upload set upload_id = 54 where id = 2986;
update upload set upload_id = 55 where id = 2987;
update upload set upload_id = 56 where id = 2988;
update upload set upload_id = 57 where id = 2989;
update upload set upload_id = 58 where id = 2990;



alter table geolocale add column subregion varchar(250);
alter table geolocale add column country varchar(250);
update geolocale set region = "Antarctica_region" where name = "Antarctica_region";
update geolocale set region = "Antarctica_region" where name = "Antarctica_subregion";
update geolocale set subregion = name where georank = "subregion";
update geolocale set subregion = parent where georank = "country";
update geolocale set country = name where georank = "country";
update geolocale set country = parent where georank = "adm1";
update geolocale set is_valid = 0 where id = 8818;
update geolocale set subregion = "Eastern Asia", region = "Asia" where parent = "Hong Kong";

#update geolocale a set a.subregion = (select subregion from (select c.subregion as subregion from geolocale c where g.parent = c.name)) where a.georank = "adm1";


# We should clean up.

# These images do not have corresponding specimen (or image_path).
select uid, image_path, image_of_id, modified from image where image_of_id not in (select code from specimen) and image_path is null;
# Delete those without specimen or image_path
delete from image where image_of_id not in (select code from specimen) and image_path is null;
# Should get rid of 949 records.

select s.code, s.caste, s.is_male, s.is_worker, s.is_queen, sv.specimen_code, sv.deductedCaste from api3_specimen sv, specimen s where s.code = sv.specimen_code limit 10;


drop view if exists api3_specimen;
CREATE 
ALGORITHM=UNDEFINED DEFINER=antweb@localhost SQL SECURITY DEFINER 
VIEW api3_specimen
AS select 
  concat(_utf8'CAS:ANTWEB:', s.code) AS occurrenceId
, s.code AS specimen_code
, s.taxon_name AS taxon_name
, t.fossil AS fossil
, t.status AS taxon_status
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
, s.bioregion as bioregion
, s.status as status
, s.decimal_latitude AS decimalLatitude
, s.decimal_longitude AS decimalLongitude
, s.latlonmaxerror AS georeferenceRemarks
, s.datedetermined AS dateIdentified
, concat( s.habitat,if(strcmp( s.microhabitat,''),concat(if(strcmp( s.habitat,''),_utf8'; ',''), s.microhabitat),'')) AS habitats
, s.habitat as habitat
, s.microhabitat as microhabitat
, s.collectedby AS recordedBy
, s.method AS samplingProtocol
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
, concat(if(s.is_male = 1, 'male', ''), concat(if(s.is_queen = 1, 'queen', ''), if(s.is_worker = 1, 'worker', ''))) as caste
from (specimen s join taxon t on((t.taxon_name =  s.taxon_name)))
# where t.status = 'valid' and t.fossil = 0;  # This would effect only using valids and not fossils for subfamily and genus but potentially limit utility of api.
;



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
,'CAS' AS institutionCode
,'AntWeb' AS dataSource
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
, concat( s.genus,_utf8' ', s.species) AS scientific_name,concat( s.kingdom_name,_utf8';', s.phylum_name,_utf8';', s.class_name,_utf8';', s.order_name,_utf8';', s.order_name,_utf8';', s.family,_utf8';', s.subfamily) AS higherClassification
, s.type AS typeStatus
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
, s.caste AS lifeStageSex
, s.antweb_caste
, s.antweb_subcaste
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



#Promote Hawaii

alter table geolocale modify column is_use_children tinyint(4);
alter table geolocale add column is_island tinyint(4);
update geolocale set georank = "country", parent = "Polynesia", subregion = "Polynesia", region = "Oceania", is_island=1 where name = "Hawaii";

alter table specimen drop column datescollected;

alter table geolocale add isoCode varchar(2);
alter table geolocale add iso3Code varchar(3);


alter table specimen add column antweb_caste varchar(128);
alter table specimen add column antweb_subcaste varchar(128);

alter table specimen drop column is_male, drop column is_worker, drop column is_queen;

alter table geolocale_taxon Drop column created;
alter table geolocale_taxon add column created DATETIME DEFAULT CURRENT_TIMESTAMP;
alter table geolocale_taxon add column modified DATETIME ON UPDATE CURRENT_TIMESTAMP;


alter table specimen add column region varchar(30), add column subregion varchar(30);


ALTER TABLE specimen change caste life_stage varchar(128);
ALTER TABLE specimen change antweb_caste caste varchar(128);
ALTER TABLE specimen change antweb_subcaste subcaste varchar(128);




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
,'CAS' AS institutionCode
,'AntWeb' AS dataSource
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
, concat( s.genus,_utf8' ', s.species) AS scientific_name,concat( s.kingdom_name,_utf8';', s.phylum_name,_utf8';', s.class_name,_utf8';', s.order_name,_utf8';', s.order_name,_utf8';', s.family,_utf8';', s.subfamily) AS higherClassification
, s.type AS typeStatus
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



drop table object_map;
create table object_map ( 
  id int(11) not null auto_increment,
  geolocale_id int(11),
  taxon_name varchar(128),
  google_map_function long,
  created timestamp default CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);


alter table specimen add column flag varchar(10); # red, yellow or null
alter table specimen add column issue varchar(128);

alter table object_map add column (title varchar(1000));
alter table object_map add column (subtitle varchar(1000));
alter table object_map add column (info varchar(1000));

alter table upload modify column `created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP;
alter table upload 
    add column specimens int(11)
  , add column collections int(11)
  , add column subfamilies int(11)
  , add column genera int(11)
  , add column species int(11)
  , add column ungeoreferenced int(11)
  , add column flagged int(11)
;
alter table upload add column localities int(11);

alter table object_map add column group_id int(11);


alter table groups add column first_specimen_upload datetime;
alter table groups add column upload_count int(11);

update upload set upload_id = 283 where id = 3203;



#mysql> select * from groups where is_upload_images >0;
#+----+--------------------------------+----------------------+----------------------+----------------+---------------------+------------------+----------+------------------+-----------------------+--------------+
#id'; name                          '; last_specimen_update'; last_specimen_upload'; admin_login_id'; is_upload_specimens'; is_upload_images'; abbrev  '; upload_specimens'; first_specimen_upload'; upload_count';
#+----+--------------------------------+----------------------+----------------------+----------------+---------------------+------------------+----------+------------------+-----------------------+--------------+
# 1'; California Academy of Sciences'; NULL                '; 2018-09-05 19:25:48 ';              1';                   1';                1'; CAS     ';                1'; 2012-01-26 13:00:16  ';         1908';
# 2'; University of Utah            '; NULL                '; 2018-08-07 16:18:12 ';              2';                   1';                1'; Utah    ';                1'; 2012-02-03 08:44:32  ';          115';
#16'; UC Davis Ant Group            '; NULL                '; 2018-07-24 23:55:34 ';             16';                   1';                1'; UCDavis ';                1'; 2013-09-05 15:07:56  ';           98';
#19'; Curator Bonnie Blaimer        '; NULL                '; NULL                ';             19';                   0';                1'; BBlaimer';             NULL'; NULL                 ';         NULL';
#24'; Université Rennes             '; NULL                '; 2018-05-06 19:24:17 ';             27';                   0';                1'; Rennes  ';                1'; 2012-01-30 03:24:51  ';          112';
#+----+--------------------------------+----------------------+----------------------+----------------+---------------------+------------------+----------+------------------+-----------------------+--------------+
#5 rows in set (0.00 sec)

#mysql> select * from groups where is_upload_specimens >0;
#+----+--------------------------------+----------------------+----------------------+----------------+---------------------+------------------+---------+------------------+-----------------------+--------------+
#id'; name                          '; last_specimen_update'; last_specimen_upload'; admin_login_id'; is_upload_specimens'; is_upload_images'; abbrev '; upload_specimens'; first_specimen_upload'; upload_count';
#+----+--------------------------------+----------------------+----------------------+----------------+---------------------+------------------+---------+------------------+-----------------------+--------------+
# 1'; California Academy of Sciences'; NULL                '; 2018-09-05 19:25:48 ';              1';                   1';                1'; CAS    ';                1'; 2012-01-26 13:00:16  ';         1908';
# 2'; University of Utah            '; NULL                '; 2018-08-07 16:18:12 ';              2';                   1';                1'; Utah   ';                1'; 2012-02-03 08:44:32  ';          115';
#16'; UC Davis Ant Group            '; NULL                '; 2018-07-24 23:55:34 ';             16';                   1';                1'; UCDavis';                1'; 2013-09-05 15:07:56  ';           98';
#+----+--------------------------------+----------------------+----------------------+----------------+---------------------+------------------+---------+------------------+-----------------------+--------------+
#3 rows in set (0.00 sec)


alter table groups drop column is_upload_specimens;
alter table groups drop column is_upload_images;


drop table upload_line;
create table upload_line (
  id int(11) not null auto_increment,
  file_name varchar(64),
  line_num int(11),
  display_line_num int(11),
  group_id int(11),
  line longtext,
  created timestamp default CURRENT_TIMESTAMP,  
  PRIMARY KEY (id)
);

alter table bioregion_taxon add column source varchar(30);
update bioregion_taxon set source = "specimen";
delete from proj_taxon where project_name = "syrianarabrepublicants";

update geolocale set map = null where name = "North America";

delete from geolocale_taxon where source like '%fix%';
delete from bioregion_taxon where source like '%fix%';

# Show the specimen source records that are higher in hierarchy
# select t.rank, g.name, g.parent, g.georank, gt.taxon_name, gt.geolocale_id, gt.source  from geolocale_taxon gt, geolocale g, taxon t where gt.taxon_name = t.taxon_name and gt.geolocale_id = g.id and gt.source = "specimen" and (rank in ('family', 'subfamily', 'genus') or georank in ('subregion', 'region')) order by gt.source, gt.geolocale_id;
# Delete those higher in the hierarchy.
# delete from geolocale_taxon where (geolocale_id, taxon_name) in ( select t.id, t.name from (select gt.geolocale_id id, gt.taxon_name name from geolocale_taxon gt, geolocale g, taxon t where gt.taxon_name = t.taxon_name and gt.geolocale_id = g.id and gt.source = "specimen" and (rank in ('family', 'subfamily', 'genus') or georank in ('subregion', 'region')) order by gt.source, gt.geolocale_id) t);


# Must update mysql first!
#alter table project add column modified timestamp default now() on update now()
alter table project modify column created timestamp default now() on update now()


select gt.taxon_name, gt.geolocale_id from geolocale_taxon gt 
where (gt.taxon_name, gt.geolocale_id) in (
  select gtld.taxon_name, gtld.geolocale_id from geolocale_taxon_log_detail gtld, geolocale_taxon_log gtl where gtld.log_id = gtl.log_id and curator_id = 22
);


select gtld.taxon_name, gtld.geolocale_id from geolocale_taxon_log_detail gtld, geolocale_taxon_log gtl 
where gtld.log_id = gtl.log_id and curator_id = 22
  and (gtld.taxon_name, gtld.geolocale_id) in (
select gt.taxon_name, gt.geolocale_id from geolocale_taxon gt where gt.source = "curator"
);


select distinct gtld.taxon_name, gtld.geolocale_id from geolocale_taxon_log_detail gtld, geolocale_taxon_log gtl  where gtld.log_id = gtl.log_id and curator_id = 22   
and (gtld.taxon_name, gtld.geolocale_id) in ( select gt.taxon_name, gt.geolocale_id from geolocale_taxon gt where gt.source = "curator" );


update artist_group set artist_id = 100 where group_id = 31;

delete from taxon where source like 'specimen%' and status = 'unrecognized' and taxon_name not in (select distinct taxon_name from specimen) order by source, created ;


drop table geolocale_taxon;
create table geolocale_taxon like sep.geolocale_taxon;
insert geolocale_taxon select * from sep.geolocale_taxon;

alter table statistics add column geolocale_taxa int(11), add column geolocale_taxa_introduced int(11), add column geolocale_taxa_endemic int(11);

alter table geolocale add column specimen_local_count int(11);
alter table geolocale_taxon add column specimen_local_count int(11);

delete from taxon where taxon_name = "1 worker(1 worker)";

delete from geolocale_taxon where taxon_name = "(formicidae)(formicidae)";

delete from proj_taxon where source in ("speciesListTool", "speciesListUpload") and taxon_name not in (select taxon_name from taxon);
These are deleted:



update group_image set group_id = 31 where group_id = 1 and image_id in (158995, 158996, 158997, 159012, 159014, 159016, 159018, 159026, 159028, 159029, 159030);
update image set artist = 100 where uid in (select image_id from group_image where group_id = 31) and artist = 156 and uid not in (259296, 259297, 259298, 259299);

#These records have been deleted from the live site:
# Also, the image were (re)moved: sudo mv /data/antweb/images/casent0256316\* /data/casent0256316/
# delete from image where uid in (255732, 255733, 255736, 255739);
mysql> select * from image where image_of_id like '%0256316%';
+--------+------------+-----------+------------+--------------+---------------+------------+-------+--------+-------------+--------+-----------+---------+---------------------+----------+-----------------------+---------------------+
uid   '; image_path'; shot_type'; resolution'; source_table'; image_of_id  '; desc_title'; width'; height'; shot_number'; artist'; copyright'; license'; upload_date        '; has_tiff'; wikimedia_upload_date'; modified           ';
+--------+------------+-----------+------------+--------------+---------------+------------+-------+--------+-------------+--------+-----------+---------+---------------------+----------+-----------------------+---------------------+
255732'; NULL      '; h        '; NULL      '; specimen    '; casent0256316'; NULL      ';  NULL';   NULL';           1';    100';        10';       1'; 2018-06-07 02:33:54';        1'; NULL                 '; 2018-11-13 01:28:29';
255733'; NULL      '; l        '; NULL      '; specimen    '; casent0256316'; NULL      ';  NULL';   NULL';           1';    100';        10';       1'; 2018-06-07 02:33:54';        1'; NULL                 '; 2018-11-13 01:28:29';
255736'; NULL      '; p        '; NULL      '; specimen    '; casent0256316'; NULL      ';  NULL';   NULL';           1';    100';        10';       1'; 2018-06-07 02:33:54';        1'; NULL                 '; 2018-11-13 01:28:29';
255739'; NULL      '; d        '; NULL      '; specimen    '; casent0256316'; NULL      ';  NULL';   NULL';           1';    100';        10';       1'; 2018-06-07 02:33:54';        1'; NULL                 '; 2018-11-13 01:28:29';
+--------+------------+-----------+------------+--------------+---------------+------------+-------+--------+-------------+--------+-----------+---------+---------------------+----------+-----------------------+---------------------+
4 rows in set (0.24 sec)


mysql> select * from proj_taxon where source in ("speciesListTool", "speciesListUpload") and taxon_name not in (select taxon_name from taxon);
+------------------+-------------------------------------+---------------------+-----------------+-------------+---------------+----------------+-------------+-------------------+------+-------------+------------+---------------+
project_name    '; taxon_name                         '; created            '; subfamily_count'; genus_count'; species_count'; specimen_count'; image_count'; source           '; rev '; antwiki_rev'; is_endemic'; is_introduced';
+------------------+-------------------------------------+---------------------+-----------------+-------------+---------------+----------------+-------------+-------------------+------+-------------+------------+---------------+
guianashieldants'; myrmicinaerhopalothrix wheeleri    '; 2017-09-08 15:06:03';               0';           0';             0';              2';          10'; speciesListTool  ';    1';        NULL';       NULL';          NULL';
guianashieldants'; myrmicinaeceratobasis              '; 2017-09-08 15:06:03';               0';           0';             1';             26';           8'; speciesListTool  ';    1';        NULL';       NULL';          NULL';
guianashieldants'; ponerinae exeuponerinae            '; 2017-09-08 15:06:03';               1';           8';            30';           6508';         879'; speciesListTool  ';    1';        NULL';       NULL';          NULL';
anomalousants   '; myrmicinaelabidus coecus           '; 2017-09-08 20:54:21';               0';           0';             0';             16';          66'; speciesListUpload'; NULL';        NULL';          0';          NULL';
anomalousants   '; ponerinae exeuponerinae            '; 2017-09-08 15:06:03';               1';           1';             1';             13';          12'; speciesListUpload'; NULL';        NULL';          0';          NULL';
atolants        '; dorylinaeacamatus                  '; 2017-09-08 15:06:03';               0';           0';             2';             89';          40'; speciesListUpload'; NULL';        NULL';          0';          NULL';
atolants        '; formicinaecamponotus (forelophilus)'; 2017-05-03 18:08:11';               0';           0';             1';              1';           4'; speciesListUpload'; NULL';        NULL';          0';          NULL';
atolants        '; formicinaemyrmecopsis              '; 2017-09-08 15:06:03';               0';           0';             2';             48';          24'; speciesListUpload'; NULL';        NULL';          0';          NULL';
atolants        '; formicinaecamponotus (phasmomyrmex)'; 2017-05-03 18:08:11';               0';           0';             1';              3';           4'; speciesListUpload'; NULL';        NULL';          0';          NULL';
atolants        '; formicinaeacrostigma               '; 2017-09-08 15:06:03';               0';           0';             1';              3';           4'; speciesListUpload'; NULL';        NULL';          0';          NULL';
atolants        '; myrmicinaeceratobasis              '; 2017-09-08 15:06:03';               0';           0';             1';             51';           8'; speciesListUpload'; NULL';        NULL';          0';          NULL';
atolants        '; ponerinae exeuponerinae            '; 2017-09-08 15:06:03';               1';          34';            52';           7176';        2506'; speciesListUpload'; NULL';        NULL';          0';          NULL';
bayareaants     '; dorylinaeacamatus                  '; 2017-09-08 15:06:03';               0';           0';             3';            165';         121'; speciesListUpload'; NULL';        NULL';          0';          NULL';
bayareaants     '; formicinaetetramorium caespitum    '; 2017-09-08 20:54:21';               0';           0';             0';             85';          87'; speciesListUpload'; NULL';        NULL';          0';          NULL';
bayareaants     '; ponerinae exeuponerinae            '; 2017-09-08 15:06:03';               1';           1';             3';           1126';         325'; speciesListUpload'; NULL';        NULL';          0';          NULL';
edibleants      '; formicinaetetramorium caespitum    '; 2017-09-08 20:53:51';               0';           0';             0';             85';          87'; speciesListUpload'; NULL';        NULL';          0';          NULL';
floridakeysants '; dorylinaeacamatus                  '; 2017-09-08 15:06:03';               0';           0';             1';             56';          35'; speciesListUpload'; NULL';        NULL';          0';          NULL';
floridakeysants '; myrmicinaeceratobasis              '; 2017-09-08 15:06:03';               0';           0';             1';             17';          30'; speciesListUpload'; NULL';        NULL';          0';          NULL';
floridakeysants '; ponerinae exeuponerinae            '; 2017-09-08 15:06:03';               1';           5';             9';           2477';         817'; speciesListUpload'; NULL';        NULL';          0';          NULL';
poeants         '; dorylinaeacamatus                  '; 2017-09-08 15:06:03';               0';           0';             2';             22';          44'; speciesListUpload'; NULL';        NULL';          0';          NULL';
poeants         '; ponerinae exeuponerinae            '; 2017-09-08 15:06:03';               1';           7';            19';           1503';         604'; speciesListUpload'; NULL';        NULL';          0';          NULL';
redlistants     '; myrmicinaetemnothorax africana     '; 2015-09-09 08:54:38';               0';           0';             0';              0';           0'; speciesListUpload'; NULL';        NULL';          0';          NULL';
guianashieldants'; dorylinaeacamatus                  '; 2017-09-08 15:06:03';               0';           0';             1';            148';          59'; speciesListTool  ';    1';        NULL';       NULL';          NULL';
+------------------+-------------------------------------+---------------------+-----------------+-------------+---------------+----------------+-------------+-------------------+------+-------------+------------+---------------+
23 rows in set (0.04 sec)


# Nov 15. Added biweekly reboot in cronjob. 
# Ran in mysql: SET GLOBAL general_log = 'OFF';
# See if by Monday the /var/etc/mysqld.log has not grown AND disk not filled up. 
# Prolly will come back after reboot. 
# Then problem identified. Remove from log file. Edit /etc/my.cnf add general-log = 0
# ls /var/log/mysqld.log -alh
# -rw-r-----. 1 mysql mysql 143M Nov 15 13:33 /var/log/mysqld.log



update geolocale_taxon_dispute set curator_id = 16 where created = "2018-10-25 15:25:09";
update geolocale_taxon_dispute set curator_id = 16 where created = "2018-10-25 15:08:23";
update geolocale_taxon_dispute set curator_id = 1 where created = "2018-10-24 16:58:40";
update geolocale_taxon_dispute set curator_id = 1 where created = "2018-10-24 16:58:39";
update geolocale_taxon_dispute set curator_id = 1 where created = "2018-10-23 10:42:14";
update geolocale_taxon_dispute set curator_id = 1 where created = "2018-10-23 10:16:37";
update geolocale_taxon_dispute set curator_id = 1 where created = "2018-10-23 10:04:35";
update geolocale_taxon_dispute set curator_id = 1 where created = "2018-08-02 09:42:33";
update geolocale_taxon_dispute set curator_id = 1 where created = "2018-06-13 15:56:25";
update geolocale_taxon_dispute set curator_id = 210 where created = "2018-05-23 02:25:11";
update geolocale_taxon_dispute set curator_id = 6 where created = "2018-03-27 13:25:55";
update geolocale_taxon_dispute set curator_id = 6 where created = "2018-03-27 13:13%";
update geolocale_taxon_dispute set curator_id = 6 where created like "2018-03-27 13:13%";
update geolocale_taxon_dispute set curator_id = 51 where created = "2018-03-20 12:02:44";

# ran the db backup here.

delete from geolocale_taxon_dispute where taxon_name not in (select taxon_name from taxon);

update geolocale_taxon_dispute set curator_id = 1 where curator_id is null;


update proj_taxon_dispute set curator_id = 56 where project_name = 'spainants';
update proj_taxon_dispute set curator_id = 1 where project_name = 'seychellesants';
update proj_taxon_dispute set curator_id = 1 where project_name = 'reunionants';
update proj_taxon_dispute set curator_id = 56 where project_name = 'portugalants';
update proj_taxon_dispute set curator_id = 17 where project_name = 'poeants';
update proj_taxon_dispute set curator_id = 1 where project_name = 'netherlandsants';
update proj_taxon_dispute set curator_id = 33 where project_name = 'nepalants';
update proj_taxon_dispute set curator_id = 1 where project_name = 'mayotteants';
update proj_taxon_dispute set curator_id = 210 where created like '2015-10-10%' and project_name = 'mauritiusants';
update proj_taxon_dispute set curator_id = 1 where curator_id is null and project_name = 'mauritiusants';
update proj_taxon_dispute set curator_id = 1 where project_name = 'madagascarants';
update proj_taxon_dispute set curator_id = 1 where project_name = 'kyrgyzstanants';
update proj_taxon_dispute set curator_id = 17 where created like '2016-06-22%' and project_name = 'introducedants';
update proj_taxon_dispute set curator_id = 17 where created like '2017-04-29%' and project_name = 'introducedants';
update proj_taxon_dispute set curator_id = 1 where project_name = 'introducedants' and curator_id is null;
update proj_taxon_dispute set curator_id = 33 where project_name = 'indiaants';
update proj_taxon_dispute set curator_id = 1 where project_name = 'hungaryants';
update proj_taxon_dispute set curator_id = 1 where created like '2016-11-12%' and project_name = 'guianashieldants';
update proj_taxon_dispute set curator_id = 1 where curator_id is null and project_name = 'guianashieldants';
update proj_taxon_dispute set curator_id = 27 where project_name = 'fossilants';
update proj_taxon_dispute set curator_id = 1 where project_name = 'floridakeysants';
update proj_taxon_dispute set curator_id = 1 where project_name = 'comorosants';
update proj_taxon_dispute set curator_id = 1 where project_name = 'bayareaants';
update proj_taxon_dispute set curator_id = 1 where project_name = 'atolants';
update proj_taxon_dispute set curator_id = 1 where project_name = 'arizonaants';
update proj_taxon_dispute set curator_id = 1 where project_name = 'anomalousants';
 
delete from proj_taxon_dispute where taxon_name not in (select taxon_name from taxon);


// -------- Above is to be run on live site. Test on stage.

alter table statistics add column bioregion_taxa int(11), add column museum_taxa int (11);
 

insert into museum (code, name, title, active) values ("JTLC", "JTLC, John T. Longino Collection", "JTLC, John T. Longino Collection", 1);


delete from admin_alerts where created < "2018";
delete from admin_alerts where alert = "No California Morphos";

#alter table taxon add column type tinyint(4);
#update taxon set type = typed;

# If we rollback prior to Feb 7, we would need to revert these columns!!!!
alter table taxon change column typed type varchar(128);
alter table homonym change column typed type varchar(128);
alter table specimen change column type type_status varchar(128);


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
,'CAS' AS institutionCode
,'AntWeb' AS dataSource
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
, concat( s.genus,_utf8' ', s.species) AS scientific_name,concat( s.kingdom_name,_utf8';', s.phylum_name,_utf8';', s.class_name,_utf8';', s.order_name,_utf8';', s.order_name,_utf8';', s.family,_utf8';', s.subfamily) AS higherClassification
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

# ---

alter table museum add column specimen_local_count int(11);
alter table bioregion add column specimen_local_count int(11); 

alter table object_map add column museum_code varchar(10);


delete from taxon where taxon_name like 'formicinaecamponotus sicheli%' and source = "specimen1.txt";
#---

alter table taxon_prop drop index value_index;
alter table taxon_prop add index value_index (value);

alter table description_edit drop index code_index;
alter table description_edit add index code_index (code);

ALTER TABLE artist ADD PRIMARY KEY(uid)

alter table museum drop column specimen_local_count;
alter table geolocale drop column specimen_local_count;
alter table bioregion drop column specimen_local_count;

alter table museum modify column code varchar(20);
alter table museum_taxon modify column code varchar(20);
alter table object_map modify column museum_code varchar(20);



update artist set artist = "unknown" where artist = "";

insert into artist (uid, artist) values (273, "Alex Wild");
insert into artist (uid, artist) values (274, "Jose Pacheco");
insert into artist (uid, artist) values (275, "Nick Olgeirson");
insert into artist (uid, artist) values (276, "Jen Fogarty");

insert into artist (uid, artist) values (277, "Michael Branstetter");
insert into artist (uid, artist) values (278, "Brian Heterick");
insert into artist (uid, artist) values (279, "Andrea Lucky");
insert into artist (uid, artist) values (280, "Dan Kjar");

insert into artist (uid, artist) values (290, "Jose Pacheco");
insert into artist (uid, artist) values (291, "Nick Olgeirson");
insert into artist (uid, artist) values (292, "Jen Fogarty");
# AntWeb


# Michael Branstetter
update image set artist = 135 where artist = 56;
update image set artist = 135 where artist = 74;
update image set artist = 135 where artist = 137;
update image set artist = 135 where artist = 277;
delete from artist where uid = 56;
delete from artist where uid = 74;
delete from artist where uid = 137;
delete from artist where uid = 277;

# Alex Wild
update image set artist = 116 where artist = 0;
delete from artist where uid = 0;
delete from artist where uid = 277;


update image set artist = 69 where artist = 1; # Set unknown to April
update image set artist = 69 where artist = 126; # other unknown
delete from artist where uid = 126;



