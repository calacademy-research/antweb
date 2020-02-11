#Trouble found.

#old database snapshot loaded into neighboring database. SpeciesListTool changes updated.
update geolocale_taxon gt set source = "speciesListTool" where (gt.taxon_name, gt.geolocale_id) in (select pt.taxon_name, p.geolocale_id from sepAnt.project p, sepAnt.proj_taxon pt where p.project_name = pt.project_name and pt.source = "speciesListTool");
# 894 modified. 946 in proj_taxon. Presumably those are for actual projects.
# select count(*) from sepAnt.proj_taxon where source = "speciesListTool" and project_name in ("introducedants", "fossilants", "réunionants");  28 records.

delete from geolocale_taxon where taxon_name = "myrmicinaestenamma petiolatum" and geolocale_id = 202;
delete from geolocale_taxon where taxon_name = "myrmicinaestenamma sardoum" and geolocale_id = 202;
delete from geolocale_taxon where taxon_name = "myrmicinaestenamma westwoodii" and geolocale_id = 202;
delete from geolocale_taxon where taxon_name = "myrmicinaestenamma sardoum" and geolocale_id = 202;

update geolocale set is_live = 1 where georank in ("region", "subregion");
update geolocale set is_live = 1 where parent = "United States" and is_valid = 1;


# release 6.10.6
delete from geolocale_taxon where taxon_name not in (select taxon_name from taxon where family = 'formicidae');
delete from proj_taxon where taxon_name not in (select taxon_name from taxon where family = 'formicidae') and project_name != "allantwebants";



#alter table geolocale add unique index (name, georank);
#show index from geolocale;
#insert into geolocale (name, georank, source) values ('Réunion', 'country', 'geolocaleMgr');
#select name from geolocale where name like '%union';
#alter table geolocale CONVERT TO CHARACTER SET utf8mb4;

#alter table geolocale charset utf8mb4 COLLATE utf8mb4_bin;
#alter table geolocale add unique index (name, georank);
#alter table geolocale modify name varchar(250) collate utf8mb4_bin;



alter table geolocale charset utf8 COLLATE utf8_bin;
alter table geolocale modify name varchar(250) collate utf8_bin;
alter table geolocale add unique index (name, georank);

# Set Curaçao to valid.

alter table geolocale add column georankType varchar(100);
alter table geolocale add column georankTypeLoc varchar(100);

Set Venezuela to live. Others?



create table lookup (
  handle varchar(64) not null,
  value varchar(64) not null,
  created timestamp null default now()
);

create table antwiki_valid_taxa ( 
  taxon_name varchar(128) 
);


#

alter table proj_taxon modify column created timestamp default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
alter table proj_taxon_log modify column created timestamp default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
alter table proj_taxon_log_detail modify column created timestamp default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

alter table geolocale_taxon modify column created timestamp default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
alter table geolocale_taxon_log modify column created timestamp default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
alter table geolocale_taxon_log_detail modify column created timestamp default CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

update proj_taxon set created = now() where created <= "1996-01-01";
update proj_taxon_log set created = now() where created <= "1996-01-01";
update proj_taxon_log_detail set created = now() where created <= "1996-01-01";

update geolocale_taxon set created = now() where created <= "1996-01-01";
update geolocale_taxon_log set created = now() where created <= "1996-01-01";
update geolocale_taxon_log_detail set created = now() where created <= "1996-01-01";

#
alter table geolocale add column bounding_box varchar(100);
alter table geolocale add column latitude varchar(30);
alter table geolocale add column longitude varchar(30);

create table antwiki_fossil_taxa ( 
  taxon_name varchar(128) 
);

update lookup set handle = "validSpeciesListUpDate";

#alter table login modify column created timestamp default CURRENT_TIMESTAMP;
#alter table login add column modified default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP;


alter table geolocale drop index name;
alter table geolocale drop index name_2;

alter table geolocale drop index geolocale_idx;
create unique index geolocale_idx on geolocale (name, parent(50), georank);


update geolocale set bounding_box = "-25.3587, -46.9005, 63.5254, 37.5671", latitude = "15.8005", longitude = "2.07.08" where name = "Africa";
update geolocale set bounding_box = "-25.3587, -46.9005, 63.5254, 37.5671", latitude = "99.8191", longitude = "34.9697" where name = "Asia";
update geolocale set bounding_box = "-31.2660, 27.6363, 39.8693, 81.0088", latitude = "7.8578", longitude = "52.9762" where name = "Europe";
update geolocale set bounding_box = "-167.2764, -59.4505, -26.3325, 83.1621", latitude = "-90", longitude = "10" where name = "Americas";
update geolocale set bounding_box = "105.3770, -53.0587, -175.2925, -6.0694", latitude = "140.8107", longitude = "-30.9410" where name = "Oceania";


# To get around "You can't specify target table" sql error...
# To delete the straglers. Might be good to set the access group to be more politic.
# delete from geolocale where parent not in (select n from (select name as n from geolocale where is_valid = 1) as c);

alter table geolocale add column bounding_box_fixed varchar(100);
alter table geolocale add column latitude_fixed varchar(30);
alter table geolocale add column longitude_fixed varchar(30);

update geolocale set bounding_box_fixed = "-57.6180, -33.7507, -49.6911, -27.0799", latitude_fixed = "-53.6545", longitude_fixed = "-30.4153" where id = 602; # Rio Grande do Sul
update geolocale set bounding_box_fixed = "-64.6417, -1.5803, -58.8918, 5.2649", latitude_fixed = "-61.7668", longitude_fixed = "1.8423" where id = 1054; # Roraima

#
alter table geolocale add column woe_id varchar(30);

# To be set on each database. The do not stick!
set character_set_client = utf8;
set character_set_connection = utf8;
set character_set_database = utf8;
set character_set_filesystem = utf8;
set character_set_results = utf8;
set character_set_server = utf8;

alter table object_edit convert to charset utf8;

update geolocale set source = "Flickr" where source = "flickr";
update geolocale set source = "Geonames" where source = "geonames";



drop table dwc_image;
drop table dwc_specimen;

# These are gone now. They had 0 records.
CREATE TABLE `dwc_image` (
  `occurrenceId` tinyint(4) NOT NULL,
  `catalogNumber` tinyint(4) NOT NULL,
  `imageUrl` tinyint(4) NOT NULL,
  `shot_type` tinyint(4) NOT NULL,
  `shot_number` tinyint(4) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 

CREATE TABLE `dwc_specimen` (
  `occurrenceId` tinyint(4) NOT NULL,
  `ownerInstitutionCode` tinyint(4) NOT NULL,
  `basisOfRecord` tinyint(4) NOT NULL,
  `institutionCode` tinyint(4) NOT NULL,
  `catalogNumber` tinyint(4) NOT NULL,
  `dcterms:modified` tinyint(4) NOT NULL,
  `nomenclaturalCode` tinyint(4) NOT NULL,
  `kingdom` tinyint(4) NOT NULL,
  `phylum` tinyint(4) NOT NULL,
  `class` tinyint(4) NOT NULL,
  `order` tinyint(4) NOT NULL,
  `family` tinyint(4) NOT NULL,
  `subfamily` tinyint(4) NOT NULL,
  `genus` tinyint(4) NOT NULL,
  `subgenus` tinyint(4) NOT NULL,
  `specificEpithet` tinyint(4) NOT NULL,
  `intraspecificEpithet` tinyint(4) NOT NULL,
  `scientific_name` tinyint(4) NOT NULL,
  `higherClassification` tinyint(4) NOT NULL,
  `typeStatus` tinyint(4) NOT NULL,
  `stateProvince` tinyint(4) NOT NULL,
  `country` tinyint(4) NOT NULL,
  `decimalLatitude` tinyint(4) NOT NULL,
  `decimalLongitude` tinyint(4) NOT NULL,
  `georeferenceRemarks` tinyint(4) NOT NULL,
  `dateIdentified` tinyint(4) NOT NULL,
  `habitat` tinyint(4) NOT NULL,
  `recordedBy` tinyint(4) NOT NULL,
  `samplingProtocol` tinyint(4) NOT NULL,
  `sex` tinyint(4) NOT NULL,
  `preparations` tinyint(4) NOT NULL,
  `fieldNumber` tinyint(4) NOT NULL,
  `identifiedBy` tinyint(4) NOT NULL,
  `locality` tinyint(4) NOT NULL,
  `locationRemarks` tinyint(4) NOT NULL,
  `occurrenceRemarks` tinyint(4) NOT NULL,
  `fieldNotes` tinyint(4) NOT NULL,
  `eventDate` tinyint(4) NOT NULL,
  `verbatimEventDate` tinyint(4) NOT NULL,
  `minimumElevationInMeters` tinyint(4) NOT NULL,
  `biogeographicregion` tinyint(4) NOT NULL,
  `antweb_taxon_name` tinyint(4) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 |
