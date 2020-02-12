
drop table image_upload;
create table image_upload (
    id int(11) not null auto_increment
  , curator_id int(11)
  , group_id int(11)
  , created timestamp default CURRENT_TIMESTAMP
  , artist_id int(11)
  , image_count int(11)
  , license text
  , copyright_year int(4)
  , PRIMARY KEY (id)
);

drop table image_uploaded;
create table image_uploaded (
    id int(11) not null auto_increment
  , filename varchar(100)
  , code varchar(20)
  , shot varchar(10)
  , number int(11)
  , ext varchar(10)
  , image_upload_id int(11)
  , created timestamp default CURRENT_TIMESTAMP
  , PRIMARY KEY (id)
);

alter table copyright add column year int(4);
update copyright set year = 2008 where uid = 2;
update copyright set year = 2008 where uid = 2;
update copyright set year = 2009 where uid = 7;
update copyright set year = 2010 where uid = 8;
update copyright set year = 2012 where uid = 10;
delete from copyright where year is null;
update copyright set copyright = 'California Academy of Sciences, 2000-2012'  where uid = 10;
#alter table copyright add primary key (year);
alter table copyright modify column uid int(11) auto_increment primary key;


alter table image modify column modified timestamp default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP;
alter table image add column image_upload_id int(11);

# To do...

# Image table implementation
#  image_path, desc_title, height, width, resolution are null.
#  source_table is always 'specimen'
#  license is always 1
#  image_of_id should be code.

#

Alter table image_upload add column complete tinyint(4) default 0;
Alter table image_uploaded add column reuploaded tinyint(4) default 0;

#

delete from artist where uid = 273;
update image set artist = 122 where artist = 129;
delete from artist where uid = 129;

update image set artist = 58 where artist = 236;
delete from artist where uid = 236;


Alter table image_uploaded add column specimen_data_exists tinyint(4) default 0;

Alter table image_uploaded drop column specimen_data_exists;
 
Alter table image_uploaded add column error_message varchar(200);
 
update image_uploaded set error_message = null where error_message = "null";
 
 
 
alter table artist change column artist name text; 

drop table worldants_upload;
create table worldants_upload (
    id int(11) not null auto_increment
  , file_size varchar(200)
  , backup_file_size int(11)
  , backup_file_name varchar(200)
  , orig_worldants_count int(11)
  , validate_message varchar(1000)
  , created timestamp default CURRENT_TIMESTAMP
  , log_file_name varchar(100)
  , exec_time varchar(100)
  , PRIMARY KEY (id)
);

delete from admin_alerts where id = 42524;

// To get rid of uid = 0 Alex Wild.
update image set artist = 116 where artist = 0;
delete from artist where uid = 0;

alter table artist add curator_id int(11);

update image set artist = 225 where uid in (select uid from (SELECT * FROM image) as image where artist = 237 and image_of_id != "antweb1038931");

update login set password = "qf_fWj-7jl" where email = "saemi.schaer@gmail.com";
update login set password = "LLAMAres5LL" where email = "mmprebus@ucdavis.edu";



drop table user_agent;
create table user_agent (
  name varchar(500) not null
  , created timestamp default CURRENT_TIMESTAMP
);
 

// To free up frustrated users.
delete from user_agent where name not like '%bot%' and name not like '%pider%' and name not like '%http%' and name not like '%Knowledge%';

update museum set title = "Archbold Biological Station" where code = "ABS";
update museum set title = "California Academy of Sciences" where code = "CASC";
update museum set title = "British Museum of Natural History" where code = "BMNH";
update museum set title = "University of California, Davis" where code = "UCDC";
update museum set title = "Museum of Natural History, Geneva" where code = "MHNG"; 
update museum set title = "Natural History Museum, Basel" where code = "NHMB";
update museum set title = "Philip S. Ward Collection" where code = "PSWC"; 
update museum set title = "Natural History Museum, Genoa" where code = "MSNG";
update museum set title = "Museum of Natural History Vienna" where code = "NHMW";
update museum set title = "Museum of Comparative Zoology, Cambridge " where code = "MCZC"; 
update museum set title = "John T. Longino Collection" where code = "JTLC";
update museum set title = "Smithsonian National Museum of Natural History" where code = "USNM";
update museum set title = "Afribugs Collection" where code = "AFRC";
update museum set title = "The Field Museum" where code = "FMNH";
update museum set title = "Matthew M. Prebus Collection" where code = "MMPC";
update museum set title = "Schmalhausen Institute" where code = "SIZK";
update museum set title = "University of Texas Insect Collection" where code = "UTIC";
update museum set title = "Ecology of French Guiana Forests" where code = "EcoFoG";

update museum set title = "Museum für Naturkunde der Humboldt-Universität Berlin" where code = "ZMHB"; 
update museum set title = "Kiko Gómez Abal Collection" where code = "KGAC";
update museum set title = "Laboratório de Ecologia de Comunidades UFV" where code = "UFV-LABECOL";
update museum set title = "Staatliches Museum für Naturkunde Görlitz" where code = "SMNG";

# Temporary. Use the above when tested and able.
update museum set title = "Museum fur Naturkunde der Humboldt-Universitat Berlin" where code = "ZMHB"; 
update museum set title = "Kiko Gomez Abal Collection" where code = "KGAC";
update museum set title = "Laboratorio de Ecologia de Comunidades UFV" where code = "UFV-LABECOL";
update museum set title = "Staatliches Museum fur Naturkunde Gorlitz" where code = "SMNG";

update image set image_of_id = "casent0005669" where image_of_id = "casent0005669 ";

delete from admin_alerts where id = 42549

show create table museum;

SELECT table_name, engine, CCSA.character_set_name FROM information_schema.`TABLES` T,information_schema.`COLLATION_CHARACTER_SET_APPLICABILITY` CCSA WHERE CCSA.collation_name = T.table_collation AND T.table_schema = "ant";

ALTER TABLE museumBak ENGINE = InnoDB;
ALTER TABLE museumBak charset = utf8;
 

# Modify image table
alter table image drop column image_path;
alter table image drop column resolution;
alter table image drop column desc_title;
alter table image drop column width;
alter table image drop column height;

alter table license change column uid id int(11);
alter table copyright change column uid id int(11);
alter table image change column uid id int(11);
alter table artist change column uid id int(11);



 CREATE ALGORITHM=UNDEFINED DEFINER=`antweb`@`localhost` SQL SECURITY DEFINER VIEW `darwin_core_multimedia` 
   AS select concat(_utf8'CAS:ANTWEB:',`im`.`image_of_id`) 
   AS `occurrenceid`,`im`.`image_of_id` 
   AS `catalognumber`,concat('https://www.antweb.org/images/',`im`.`image_of_id`,'/',`im`.`image_of_id`,'_',`im`.`shot_type`,'_',`im`.`shot_number`,'_high.jpg') 
   AS `imageurl`,`im`.`shot_type` AS `shot_type`,`im`.`shot_number` 
   AS `shot_number`,concat('https://www.antweb.org/specimenImages.do?name=',`im`.`image_of_id`) 
   AS `references`,concat(`im`.`image_of_id`,convert((case `im`.`shot_type` when 'd' then ' dorsal' when 'h' then ' head' when 'l' then ' label' when 'p' then ' profile' end) using utf8),' view ',`im`.`shot_number`) 
   AS `title`,`li`.`license` AS `license`,nullif(`cr`.`copyright`,'') AS `rightsHolder`,nullif(`ar`.`name`,'') 
   AS `creator` from (((`image` `im` 
   left join `license` `li` on((`im`.`license` = `li`.`id`))) 
   left join `copyright` `cr` on((`im`.`copyright` = `cr`.`id`))) 
   left join `artist` `ar` on((`im`.`artist` = `ar`.`id`))) 
   order by concat('https://www.antweb.org/images/',`im`.`image_of_id`,'/',`im`.`image_of_id`,'_',`im`.`shot_type`,'_',`im`.`shot_number`,'_high.jpg')

CREATE ALGORITHM=UNDEFINED DEFINER=`antweb`@`localhost` SQL SECURITY DEFINER VIEW `taxon_image` 
AS select `t`.`taxon_name` AS `taxon_name`,`t`.`subfamily` AS `subfamily`,`t`.`genus` AS `genus`,`t`.`species` AS `species`,`t`.`subspecies` AS `subspecies`
,`s`.`code` AS `code`,`i`.`id` AS `uid`,`i`.`shot_type` AS `shot_type`
,`i`.`shot_number` AS `shot_number`
,`i`.`artist` AS `artist`,`i`.`copyright` AS `copyright`,`i`.`license` AS `license`,`i`.`upload_date` AS `upload_date`
,`i`.`has_tiff` AS `has_tiff` from ((`taxon` `t` join `specimen` `s`) join `image` `i`) 
where ((`t`.`taxon_name` = `s`.`taxon_name`) and (`s`.`code` = `i`.`image_of_id`))


update image set modified = "2000-01-01 00:00:00" where modified < "2000-01-01 00:00:00";
Query OK, 185629 rows affected (1.79 sec)



 
 
 
