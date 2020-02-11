#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.118/2013-01-08.sql
#



update taxon set valid = 0;
alter table taxon add column (antcat int(11) default 0);
alter table taxon add column (pending int(11) default 0);
alter table proj_taxon add column created timestamp default CURRENT_TIMESTAMP;
alter table description_edit add primary key (taxon_name, title);

alter table statistics add column proj_taxa int(11);
alter table statistics add column total_taxa int(11);

# If we dont start fresh
update taxon set antcat = 1 where taxon_name in (select taxon_name from proj_taxon where project_name = "worldants");
update taxon set valid = 1 where taxon_name in (select taxon_name from proj_taxon where project_name = "worldants");


# If formicidae breaks.
insert into taxon (taxon_name, family, kingdom_name, phylum_name, class_name, order_name, valid, valid_name, antcat, source) values ("formicidae", "formicidae", "animalia", "arthropoda", "insecta", "hypmenoptera", 1, 1, 1, "insert");
insert into proj_taxon(taxon_name, project_name) values ("formicidae", "allantwebants");
insert into proj_taxon(taxon_name, project_name) values ("formicidae", "worldants");


alter table proj_taxon drop index project_taxon;
alter table proj_taxon add constraint unique index project_taxon (project_name, taxon_name); 