#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.45/2012-01-15.sql
#

delete from taxon;
delete from proj_taxon;
delete from taxon_country;

# Then run the bolton upload

drop view tapir_specimen;
CREATE ALGORITHM=UNDEFINED DEFINER=`antweb`@`localhost` SQL SECURITY DEFINER VIEW `tapir_specimen` AS select `specimen`.`code` AS `code`,`specimen`.`taxon_name` AS `taxon_name`,`specimen`.`subgenus` AS `subgenus`,`specimen`.`tribe` AS `tribe`,`specimen`.`speciesgroup` AS `speciesgroup`,`specimen`.`subfamily` AS `subfamily`,`specimen`.`genus` AS `genus`,`specimen`.`species` AS `species`,`specimen`.`other` AS `other`,`specimen`.`type` AS `type`,`specimen`.`subspecies` AS `subspecies`,`specimen`.`country` AS `country`,`ant`.`specimen`.`adm2` AS `adm2`,`ant`.`specimen`.`adm1` AS `adm1`,`ant`.`specimen`.`localityname` AS `localityname`,`ant`.`specimen`.`localitycode` AS `localitycode`,`ant`.`specimen`.`collectioncode` AS `collectioncode`,`ant`.`specimen`.`biogeographicregion` AS `biogeographicregion`,`ant`.`specimen`.`decimal_latitude` AS `decimal_latitude`,`ant`.`specimen`.`decimal_longitude` AS `decimal_longitude`,`ant`.`specimen`.`last_modified` AS `last_modified`,`ant`.`specimen`.`habitat` AS `habitat`,`ant`.`specimen`.`method` AS `method`,`ant`.`specimen`.`toc` AS `toc`,`ant`.`specimen`.`ownedby` AS `ownedby`,`ant`.`specimen`.`collectedby` AS `collectedby`,`ant`.`specimen`.`caste` AS `caste`,`ant`.`specimen`.`access_group` AS `access_group`,`ant`.`specimen`.`locatedat` AS `locatedat`,`ant`.`description`.`content` AS `author_date`,concat(`ant`.`specimen`.`genus`,_utf8' ',`ant`.`specimen`.`species`) AS `scientific_name`,concat(_utf8'antweb:',`ant`.`specimen`.`code`) AS `guid` from (`specimen` left join `description` on((`ant`.`description`.`taxon_name` = `ant`.`specimen`.`taxon_name`))) where (`ant`.`description`.`title` = _utf8'speciesauthordate');

