#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.148/2013-05-20.sql
#

delete from taxon where subfamily = "" and rank = "genus" or taxon_name = " fhg-lili";
delete from taxon where subfamily = "pseudomyrmicinae";
update taxon set source = "setHigherTaxonomicHierarchy" where taxon_name = "formicidae";
delete from taxon where subfamily = "" and rank = "species";

update project set species_list_mappable=1  where project_name = "comorosants" or project_name = "madants" or project_name = "mauritiusants" or project_name = "seychellesants" or project_name = "reunionants";

insert into proj_taxon values ("madants", "formicidae", now());
insert into proj_taxon values ("nearcticants", "formicidae", now());
insert into proj_taxon values ("arizonaants", "formicidae", now());
insert into proj_taxon values ("eurasianants", "formicidae", now());
insert into proj_taxon values ("pacificislandsants", "formicidae", now());
insert into proj_taxon values ("malagasyants", "formicidae", now());
insert into proj_taxon values ("alabamaants", "formicidae", now());
insert into proj_taxon values ("mississippiants", "formicidae", now());
insert into proj_taxon values ("neotropicalants", "formicidae", now());
insert into proj_taxon values ("africanants", "formicidae", now());
insert into proj_taxon values ("sloveniaants", "formicidae", now());
insert into proj_taxon values ("australianants", "formicidae", now());
insert into proj_taxon values ("serbiaants", "formicidae", now());
insert into proj_taxon values ("southeastasiaants", "formicidae", now());
insert into proj_taxon values ("philippinesants", "formicidae", now());
insert into proj_taxon values ("newguineaants", "formicidae", now());
insert into proj_taxon values ("indiaants", "formicidae", now());
insert into proj_taxon values ("borneoants", "formicidae", now());
insert into proj_taxon values ("atolants", "formicidae", now());
insert into proj_taxon values ("uaeants", "formicidae", now());
insert into proj_taxon values ("greeceants", "formicidae", now());
insert into proj_taxon values ("iranants", "formicidae", now());
insert into proj_taxon values ("saudiants", "formicidae", now());
insert into proj_taxon values ("introducedants", "formicidae", now());
insert into proj_taxon values ("projectsants", "formicidae", now());
insert into proj_taxon values ("globalants", "formicidae", now());
insert into proj_taxon values ("solomonants", "formicidae", now());
insert into proj_taxon values ("alicanteants", "formicidae", now());
insert into proj_taxon values ("georgiaants", "formicidae", now());
insert into proj_taxon values ("southcarolinaants", "formicidae", now());
insert into proj_taxon values ("tennesseeants", "formicidae", now());

