#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/5.1.19/2013-10-18.sql
#

alter table taxon add column insert_method varchar(30);

delete from taxon where source like "insert%";
delete from taxon where insert_method like "is%";

update taxon set insert_method = "addMissingSubfamilies" where source = "addMissingSubfamilies";
update taxon set insert_method = "addMissingSubfamilies" where source = "addMissingSubfamilies";

# Must reupload files to regain morpho and indet genera and subfamilies

update favorite_images set project_name = "default" where project_name = "def";


drop table image_count_materialized;
drop table image_catalog;
drop table image_catalog2;

update groups set admin_login_id = 27 where id = 24;
update groups set admin_login_id = 26 where id = 23;

alter table taxon add column typed tinyint(4);
update taxon set typed = 1 where rank = "species" and taxon_name in
  (select taxon_name from specimen where type != "");
