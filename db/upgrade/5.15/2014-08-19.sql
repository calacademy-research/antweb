#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/5.15/2014-08-19.sql
#

#Create table homonym as select * from taxon where 1=0;
#alter table homonym add primary key (taxon_name, author_date);


CREATE TABLE homonym (
  taxon_name varchar(128) NOT NULL,
  rank varchar(16) NOT NULL,
  subfamily varchar(64) DEFAULT NULL,
  tribe varchar(64) DEFAULT NULL,
  genus varchar(64) DEFAULT NULL,
  subgenus varchar(64) DEFAULT NULL,
  speciesgroup varchar(64) DEFAULT NULL,
  species varchar(64) DEFAULT NULL,
  subspecies varchar(64) DEFAULT NULL,
  parent varchar(128) DEFAULT NULL,
  fossil tinyint(4) DEFAULT '0',
  created timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  source varchar(64) DEFAULT NULL,
  family varchar(65) DEFAULT 'formicidae',
  kingdom_name varchar(64) DEFAULT NULL,
  phylum_name varchar(64) DEFAULT NULL,
  class_name varchar(64) DEFAULT NULL,
  order_name varchar(64) DEFAULT NULL,
  antcat int(11) DEFAULT '0',
  pending int(11) DEFAULT '0',
  subfamily_count int(11) DEFAULT '0',
  genus_count int(11) DEFAULT '0',
  species_count int(11) DEFAULT '0',
  specimen_count int(11) DEFAULT '0',
  image_count int(11) DEFAULT '0',
  parent_taxon_name varchar(128) DEFAULT NULL,
  insert_method varchar(30) DEFAULT NULL,
  typed tinyint(4) DEFAULT NULL,
  antcat_id int(11) DEFAULT NULL,
  author_date varchar(100) DEFAULT NULL,
  author_date_html varchar(300) DEFAULT NULL,
  authors varchar(300) DEFAULT NULL,
  year varchar(30) DEFAULT NULL,
  status varchar(300) DEFAULT NULL,
  available tinyint(4) DEFAULT NULL,
  current_valid_name varchar(300) DEFAULT NULL,
  original_combination tinyint(4) DEFAULT NULL,
  was_original_combination varchar(128) DEFAULT NULL,
  reference_id int(11) DEFAULT NULL,
  bioregion varchar(128) DEFAULT NULL,
  country varchar(128) DEFAULT NULL,
  current_valid_rank varchar(64) DEFAULT NULL,
  current_valid_parent varchar(300) DEFAULT NULL,
  PRIMARY KEY taxon_name_2 (taxon_name, author_date),
  KEY genus_species (genus,species),
  KEY species (species),
  KEY subfamily_genus_species (subfamily,genus,species)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

# We are doing it this way.  Not that the content and taxonName are utf8 but not author date.
Create table description_homonym as select * from description_edit where 1=0;
alter table description_homonym add column author_date varchar(100);
alter table description_homonym add primary key (taxon_name, author_date, title);

#CREATE TABLE description_homonym (
#  taxon_name varchar(128) CHARACTER SET utf8 NOT NULL,
#  title varchar(32) CHARACTER SET utf8 NOT NULL,
#  author_date varchar(100),
#  content longtext CHARACTER SET utf8,
#  edit_id int(10) NOT NULL DEFAULT '0',
#  created timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
#  taxon_id int(10) DEFAULT NULL,
#  is_manual_entry tinyint(4) NOT NULL,
#  access_group int(11) DEFAULT NULL,
# access_login int(11) DEFAULT NULL,
#  PRIMARY KEY taxon_name_2 (taxon_name, author_date, title)
#) ENGINE=MyISAM DEFAULT CHARSET=utf8;

