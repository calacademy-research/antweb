
	
drop table if exists museum;
CREATE TABLE museum (
  # id int(11) NOT NULL AUTO_INCREMENT,
  # access_group int(11) not null,
  code varchar(10) NOT NULL,
  name varchar(128),
  title varchar(64),
  active tinyint(4) DEFAULT 0,
  subfamily_count int(11) DEFAULT NULL,
  genus_count int(11) DEFAULT NULL,
  species_count int(11) DEFAULT NULL,
  specimen_count int(11) DEFAULT NULL,
  image_count int(11) DEFAULT NULL,
  imaged_specimen_count int(11) DEFAULT NULL,
  taxon_subfamily_dist_json varchar(10000),
  specimen_subfamily_dist_json varchar(10000),
  created timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  # UNIQUE KEY obj_key_id (id),
  UNIQUE KEY obj_key_code (code)
  #, UNIQUE KEY obj_key2 (access_group, code)
);

#alter table museum drop column taxon_subfamily_dist_json;
#alter table museum add column taxon_subfamily_dist_json varchar(1000);
#alter table museum drop column specimen_subfamily_dist_json;
#alter table museum add column specimen_subfamily_dist_json varchar(1000);

drop table if exists museum_taxon;
CREATE TABLE museum_taxon (
   # museum_id int(11) NOT NULL
    code varchar(10) NOT NULL
  , taxon_name varchar(128) NOT NULL
  , created timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
  , subfamily_count int(11) DEFAULT 0
  , genus_count int(11) DEFAULT 0
  , species_count int(11) DEFAULT 0
  , specimen_count int(11) DEFAULT 0
  , image_count int(11) DEFAULT 0
  , insert_method varchar(30)
#  , is_introduced tinyint(4) DEFAULT 0
#  , source varchar(30) DEFAULT NULL
#  , rev int(11) DEFAULT NULL
  , UNIQUE KEY museum_taxon (code, taxon_name)
  , KEY museum_code (code)
  , KEY taxon_name (taxon_name)
);

alter table specimen
  add column museum varchar(128);

create index col_museum_idx on specimen (museum);


