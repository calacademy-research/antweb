#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.138/2013-04-08.sql
#

alter table project add column country varchar(64);
alter table project add adm1 varchar(128);
alter table project drop column species_list_mapping;
alter table project add column species_list_mappable tinyint(4) default 0;
update project set species_list_mappable = 1 where project_name in ("comorosants", "seychellesants", "mauritiusants", "mayotteants", "reunionants", "madants");


update project set country = "Madagascar" where project_name = "madants";
update project set country = "Réunion" where project_name = "reunionants";
update project set country = "Mauritius" where project_name = "mauritiusants";
update project set country = "Seychelles" where project_name = "seychellesants";
update project set country = "Comoros" where project_name = "comorosants";
update project set country = "New Zealand" where project_name = "newzealandants";
update project set country = "Belgium" where project_name = "belgiumants";
update project set country = "Galapogos" where project_name = "galapagosants";
update project set country = "Costa Rica" where project_name = "costaricaants";
update project set country = "Netherlands" where project_name = "netherlandsants";
update project set country = "Slovenia" where project_name = "sloveniaants";
update project set country = "Croatia" where project_name = "croatiaants";
update project set country = "Austria" where project_name = "austriaants";
update project set country = "Paraguay" where project_name = "paraguayants";
update project set country = "Fiji" where project_name = "fijiants";
update project set country = "Micronesia" where project_name = "micronesiaants";
update project set country = "Macaronesia" where project_name = "macaronesiaants";
update project set country = "Serbia" where project_name = "serbiaants";
update project set country = "Kenya" where project_name = "kenyaants";
update project set country = "New Guinea" where project_name = "newguineaants";
update project set country = "Philippines" where project_name = "philippinesants";
update project set country = "Mayotte" where project_name = "mayotteants";
update project set country = "India" where project_name = "indiaants";
update project set country = "Czech Republic" where project_name = "czechants";
update project set country = "Borneo" where project_name = "borneoants";
update project set country = "Greece" where project_name = "greeceants";
update project set country = "Iran" where project_name = "iranants";
update project set country = "Saudi Arabia" where project_name = "saudiants";
# What about: matogrossodosul, Queensland, Alicante ?
update project set country = "France" where project_name = "franceants";
update project set country = "Solomon Islands" where project_name = "solomonsants";
update project set country = "Italy" where project_name = "italyants";
update project set country = "China" where project_name = "chinaants";

update project set country = "United Arab Emirates" where project_name = "uaeants";
update project set adm1 = "California" where project_name = "calants";
update project set adm1 = "Louisiana" where project_name = "louisianaants";
update project set adm1 = "Arizona" where project_name = "arizonaants";
update project set adm1 = "Florida" where project_name = "floridaants";
update project set adm1 = "Missouri" where project_name = "missouriants";
update project set adm1 = "Hawaii" where project_name = "hawaiiants";
update project set adm1 = "Alabama" where project_name = "alabamaants";
update project set adm1 = "Mississippi" where project_name = "mississippiants";
update project set adm1 = "Ohio" where project_name = "ohioants";
update project set adm1 = "Illinois" where project_name = "illinoisants";
update project set adm1 = "Pennsylvania" where project_name = "pennsylvaniaants";
update project set adm1 = "Alberta" where project_name = "albertaants";
update project set adm1 = "Utah" where project_name = "utahants";
update project set adm1 = "Colorado" where project_name = "coloradoants";
update project set adm1 = "Texas" where project_name = "texasants";
update project set adm1 = "British Columbia" where project_name = "britishcolumbianants";
update project set adm1 = "Arkansas" where project_name = "arkansasants";
update project set adm1 = "New Mexico" where project_name = "newmexicoants";
update project set adm1 = "North Carolina" where project_name = "northcarolinaants";



