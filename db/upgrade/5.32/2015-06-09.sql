#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/5.32/2015-06-09.sql
#
# handle data from: http://www.antwiki.org/wiki/index.php?title=Countries_by_Regions&action=edit
#

drop table if exists un_country;
create table un_country (
  name varchar (250)
, un_region varchar (250)
, un_subregion varchar(250)
, bioregion varchar(64)
, primary key (name)
);


alter table specimen add column original_taxon_name varchar(128)
  , add column line_num int(11);

