
update artist set created = "2001-01-01 01:01:01" where id <= 112;


ALTER TABLE geolocale_taxon ENGINE=InnoDB;
ALTER TABLE museum_taxon ENGINE=InnoDB;
ALTER TABLE proj_taxon ENGINE=InnoDB;
ALTER TABLE bioregion_taxon ENGINE=InnoDB;

ALTER TABLE geolocale_taxon ENGINE=MyIsam;
ALTER TABLE museum_taxon ENGINE=MyIsam;
ALTER TABLE proj_taxon ENGINE=MyIsam;
ALTER TABLE bioregion_taxon ENGINE=MyIsam;

alter table specimen modify column dnaextractionnotes varchar(512);


DROP FUNCTION IF EXISTS initcap;

CREATE  FUNCTION `initcap`(x varchar(255)) RETURNS varchar(255) CHARSET latin1
return concat( upper(substring(x,1,1)),lower(substring(x,2)) )
;

alter table geolocale_taxon_log modify column created timestamp default CURRENT_TIMESTAMP on update CURRENT_TIMESTAMP;

alter table bioregion add column endemic_species_count int(11);
alter table bioregion_taxon add column is_endemic tinyint(4);

alter table bioregion add column introduced_species_count int(11);
alter table bioregion_taxon add column is_introduced tinyint(4);


alter table specimen modify column collectedby varchar(256);


#

alter table taxon rename column `rank` to `taxarank`;
alter table homonym rename column `rank` to `taxarank`;
alter table team_member rename column `rank` to `teamrank`;
rename table ant.groups to ant_group;

On linux:
alter table taxon change rank taxarank varchar(16);
alter table homonym change rank taxarank varchar(16);
alter table team_member change rank teamrank int(11);
rename table ant.groups to ant_group;




update geolocale set is_island = 1 where id = 1721 or id = 1732 or id = 620;
update geolocale set valid_name = "Galapagos Islands", is_island = 1, is_valid = 0 where id = 2394;
update geolocale set valid_name = "Galapagos Islands" where id = 620;

alter table specimen add column island_country varchar(64);
update geolocale set country = "Ecuador" where id = 1721;

update specimen set country = "United States", island_country = "Hawaii" where country = "Hawaii";
update specimen set island_country = "Galapagos Islands" where adm1 like  "Galapagos%";

alter table specimen drop column date_collected;

update specimen set datecollectedstart = null where datecollectedstart = "null";
update specimen set datecollectedend = null where datecollectedend = "null";



alter table image_uploaded modify column code varchar(128);



