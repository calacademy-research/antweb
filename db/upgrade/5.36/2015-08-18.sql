#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/5.36/2015-08-18.sql
#


# ONLY ADD ONCE
alter table project add column geolocale_id int(11);
alter table project add column is_live tinyint(4) default 1;
update project set is_live = 1;
alter table project add column source varchar(40);

alter table bioregion add column project_name varchar(40);
alter table bioregion add column title varchar(40);
alter table bioregion add column locality varchar(50);
alter table bioregion add column extent varchar(40);

update bioregion set title="African", project_name="africanants", extent="-29.56 -38.08 67.60 43.02", locality="biogeographicregion='african'" where name = "Afrotropic";
update bioregion set title="Malagasy", project_name="malagasyants", extent="35.91 -27.42 60.12 -9.93", locality="biogeographicregion='malagasy'" where name = "Malagasy";
update bioregion set title="Eurasian", project_name="eurasianants", extent="-13.07 -23.97 185.89 119.70", locality="biogeographicregion='eurasian'" where name = "Palearctic";
update bioregion set title="Australian", project_name="australianants", extent="108.99 -44.94 160.21 -7.95", locality="biogeographicregion='Australasia'" where name = "Australasia";
update bioregion set title="South East Asia", project_name="southeastasiaants", extent="", locality="biogeographicregion='Indomalaya'" where name = "Indomalaya";
update bioregion set title="Pacific Islands", project_name="pacificislandsants", extent="", locality="biogeographicregion='Oceania'" where name = "Oceania";
update bioregion set title="Nearctic", project_name="nearcticants", extent="-180 -90 180 90", locality="biogeographicregion='nearctic'" where name = "Nearctic";
update bioregion set title="Neotropical", project_name="neotropicalants", extent="", locality="biogeographicregion='neotropic'" where name = "Neotropic";

alter table taxon drop index genus_species;
alter table taxon add index genus_species_subspecies (genus, species, subspecies);
alter table homonym drop index genus_species;
alter table homonym add index genus_species_subspecies (genus, species, subspecies);

alter table proj_taxon add column antwiki_rev int(11);
alter table proj_taxon add column is_introduced tinyint(4) default 0;

drop table if exists proj_taxon_dispute;
CREATE TABLE proj_taxon_dispute (
    project_name varchar(32) not null
  , taxon_name varchar(128) not null
  , antwiki_rev int(11)
  , created timestamp
);

drop table if exists antwiki_taxon_country;
CREATE TABLE antwiki_taxon_country (
    id int(11) not null auto_increment
  , rev int(11) not null
  , short_taxon_name varchar(128)
  , original_taxon_name varchar(128)
  , taxon_name varchar(128) NOT NULL
  , country varchar(100) not null
  , region varchar(100)      
  , is_introduced tinyint(4) 
  , source varchar(150) 
  , project_name varchar(40)
  , created timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
  , primary key (id)
  , unique KEY (rev, taxon_name, country)
) ENGINE=MyISAM DEFAULT CHARSET=utf8;

drop table if exists geolocale;
CREATE TABLE geolocale (
    id int(11) not null auto_increment
  , name varchar(250) NOT NULL
  , georank varchar(20)      
  , is_valid tinyint(4)      
  , is_un tinyint(4)
  , source varchar(250)       
  , valid_name varchar(250)   
  , parent varchar(250) DEFAULT NULL  
  , region varchar(250) DEFAULT NULL 
  , bioregion varchar(64) DEFAULT NULL 
  , is_live tinyint(4)                 
  , extent varchar(40)
  , coords varchar(128)
  , map varchar(40)
  , locality varchar(80)
  , is_use_children tinyint(40) default 0  
  , is_use_parent_region tinyint(4)
  , created timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
  , primary key (id)
  , unique KEY (name, georank)
) ENGINE=MyISAM DEFAULT CHARSET=utf8;

# georank: # region, subregion, country or adm1 (or adm2)?
# is_valid  # valid in Antweb.  Could be from UN or not.  Allowed.
# admin, ui, specimen, UN.
# if not is_valid then point to the valid.
# Parent - Point to the name of the locale one up the hierarchy.
# bioregion - # Refers to Antweb bioregion table.
# Dynamically publish or unpublish (don not display)


# To be done manually by admin.
#   To populate the extent and coords hit this:  /util.do?action=countryBounds
#   For the invalid names, through the UI, select the valid name for the invalid names.





