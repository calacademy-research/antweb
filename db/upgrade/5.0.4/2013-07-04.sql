#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/5.0.4/2013-07-04.sql
#


alter table taxon add column parent_taxon_name varchar (128);
alter table taxon drop column parent_taxon_id;
alter table taxon drop column taxon_id;
alter table specimen drop column parent_taxon_id;


