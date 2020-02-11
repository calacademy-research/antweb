#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.44/2012-01-05.sql
#

#alter table specimen add column taxon_order varchar(64);
#alter table taxon add column taxon_order varchar(64);

drop table image_count;
create view image_count as select `ant`.`specimen`.`subfamily` AS `subfamily`,`ant`.`specimen`.`genus` AS `genus`,`ant`.`specimen`.`species` AS `species`,`ant`.`proj_taxon`.`project_name` AS `project_name`,count(`ant`.`specimen`.`code`) AS `the_count` from ((`ant`.`specimen` left join `ant`.`proj_taxon` on((`ant`.`specimen`.`taxon_name` = `ant`.`proj_taxon`.`taxon_name`))) join `ant`.`image` on((`ant`.`specimen`.`code` = `ant`.`image`.`image_of_id`))) where (`ant`.`image`.`shot_type` <> _utf8'l') group by `ant`.`specimen`.`subfamily`,`ant`.`specimen`.`genus`,`ant`.`specimen`.`species`,`ant`.`proj_taxon`.`project_name`;


#cd to web dir (/data/antweb)
rm bayAreaSearchResults-body.jsp  
rm bayAreaSearchResults.jsp