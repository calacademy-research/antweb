#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/5.0.1/2013-06-27.sql
#


alter table proj_taxon add column subfamily_count int(11) default 0, add column genus_count int(11) default 0
  , add column species_count int(11) default 0, add column specimen_count int(11) default 0;
alter table specimen add column image_count int(11) default 0;
alter table taxon add column image_count int(11) default 0;
alter table proj_taxon add column image_count int(11) default 0;


# 5.0.3
alter table taxon add column parent_taxon_name varchar (128);
alter table taxon drop column parent_taxon_id;
alter table taxon drop column taxon_id;
alter table specimen drop column parent_taxon_id;


