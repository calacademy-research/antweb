#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.119/2013-01-22.sql
#

delete from description_edit where taxon_name = "agroecomyrmecinaeeulithomyrmex striatus" and title = "comments" and created = "2011-08-19 01:53:46";
delete from description_edit where taxon_name = "agroecomyrmecinaeeulithomyrmex striatus" and title = "textauthor" and created = "2012-01-19 23:39:54";
delete from description_edit where taxon_name = "agroecomyrmecinaeeulithomyrmex striatus" and title = "distribution" and created = "2011-03-31 23:46:13";
delete from description_edit where taxon_name = "agroecomyrmecinaeeulithomyrmex striatus" and title = "references" and created = "2011-08-19 01:48:12";

alter table description_edit add constraint unique index description_edit (taxon_name, title);

alter table taxon modify column fossil tinyint(4) default 0;
update taxon set fossil = 0 where fossil is null;

# Above implemented in V4.118.1

alter table proj_taxon drop index project_taxon;
alter table proj_taxon add constraint unique index project_taxon (project_name, taxon_name); 


# These were inserted when "none"/Select option was not prevented from drop down box.
delete from proj_taxon where project_name = "none";

alter table taxon modify column valid  tinyint(4) default 0;
update taxon set valid = 0 where valid  is null;
