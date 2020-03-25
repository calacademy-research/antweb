
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

#alter table bioregion add column introduced_species_count int(11);
#alter table bioregion_taxon add column is_introduced tinyint(4);
