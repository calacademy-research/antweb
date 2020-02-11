#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/5.35/2015-07-21.sql
#

delete from proj_taxon where project_name = "malagasyants";

delete from taxon where access_group is null and source not like '%specimen%' and source != "worldants.txt" and source != "allantwebants" and source not like '%speciesList.txt' and antcat_id is null order by source, taxon_name;

delete from taxon where taxon_name = "(formicidae)(formicidae)";

alter table specimen add index (created);

