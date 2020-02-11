# 3.3.1/2011-01-27.sql
#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/3.3.1/2011-01-27.sql
#
# This view needed to be recreated because it referenced the old Taxon table (now taxon).  The table
# appears to be unused, but was breaking the mysqldump process

drop view if exists valid_taxa;

CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`localhost` SQL SECURITY DEFINER VIEW `valid_taxa` AS select distinct `ant`.`taxon`.`subfamily` AS `subfamily`,`ant`.`taxon`.`subgenus` AS `subgenus`,`ant`.`taxon`.`speciesgroup` AS `speciesgroup`,`ant`.`taxon`.`genus` AS `genus`,`ant`.`taxon`.`species` AS `species`,`ant`.`proj_taxon`.`project_name` AS `project_name` from (`taxon` join `proj_taxon` on((`ant`.`taxon`.`taxon_name` = `ant`.`proj_taxon`.`taxon_name`))) where (`ant`.`taxon`.`valid` = 1)
