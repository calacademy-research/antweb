#
# Execute as such:
#  mysql -u antweb -p ant < db/upgrade/4.150/2013-06-19.sql
#

#update project set map =  |                                          |  where project_name =  | nearcticants        |
#update project set map =  |                                          |  where project_name =  | australianants      |
#update project set map =  |                                          |  where project_name =  | macaronesiaants     |
#update project set map =  |                                          |  where project_name =  | kenyaants           |
#update project set map =  |                                          |  where project_name =  | mayotteants         |
#update project set map =  |                                          |  where project_name =  | pennsylvaniaants    |
#update project set map =  |                                          |  where project_name =  | indiaants           |
#update project set map =  |                                          |  where project_name =  | czechants           |
#update project set map =  |                                          |  where project_name =  | albertaants         |
#update project set map =  |                                          |  where project_name =  | utahants            |
#update project set map =  |                                          |  where project_name =  | coloradoants        |
#update project set map =  |                                          |  where project_name =  | texasants           |
#update project set map =  |                                          |  where project_name =  | borneoants          |
#update project set map =  |                                          |  where project_name =  | britishcolumbiaants |
#update project set map =  |                                          |  where project_name =  | floridakeysants     |
#update project set map =  |                                          |  where project_name =  | uaeants             |
#update project set map =  |                                          |  where project_name =  | greeceants          |
#update project set map =  |                                          |  where project_name =  | iranants            |
#update project set map =  |                                          |  where project_name =  | saudiants           |
#update project set map =  |                                          |  where project_name =  | queenslandants      |
#update project set map =  |                                          |  where project_name =  | arkansasants        |
#update project set map =  |                                          |  where project_name =  | franceants          |
#update project set map =  |                                          |  where project_name =  | newmexicoants       |
#update project set map =  |                                          |  where project_name =  | solomonsants        |
#update project set map =  |                                          |  where project_name =  | alicanteants        |
#update project set map =  |                                          |  where project_name =  | italyants           |
#update project set map =  |                                          |  where project_name =  | chinaants           |
#update project set map =  |                                          |  where project_name =  | northcarolinaants   |

update project set map = "biogeo-Africanv3a.gif" where project_name = "africanants";
update project set map = "Alabama_233.gif" where project_name = "alabamaants";
update project set map = "arizona_map.gif" where project_name = "arizonaants";
update project set map = "austria_233v4.gif" where project_name = "austriaants";
update project set map = "worldv2.jpg" where project_name = "atolants";
update project set map = "worldv2.jpg" where project_name = "anomalousants";
update project set map = "" where project_name = "allantwebants";
update project set map = "baja_233.gif" where project_name = "bajaants";
update project set map = "bayarea_map.gif" where project_name = "bayareaants";
update project set map = "belgium_233.gif" where project_name = "belgiumants";
update project set map = "cali_map.gif" where project_name = "calants";
update project set map = "comoros_2332.gif" where project_name = "comorosants";
update project set map = "costarica_233v4.gif" where project_name = "costaricaants";
update project set map = "Croatia_233.gif" where project_name = "croatiaants";
update project set map = "biogeo-Eurasian233v3.gif" where project_name = "eurasianants";
update project set map = "Fiji_233v2.gif" where project_name = "fijiants";
update project set map = "florida_233.gif" where project_name = "floridaants";
update project set map = "Early-k1.jpg" where project_name = "fossilants";
update project set map = "Galapagos_233v2.gif" where project_name = "galapagosants";
update project set map = "" where project_name = "globalants";
update project set map = "" where project_name = "georgiaants";
update project set map = "Hawaii_233.gif" where project_name = "hawaiiants";
update project set map = "worldv2.jpg" where project_name = "introducedants";
update project set map = "illinois.gif" where project_name =  "illinoisants";
update project set map = "louisiana_233.gif" where project_name = "louisianaants";
update project set map = "madagascar_242.jpg" where project_name = "madants";
update project set map = "" where project_name = "matogrossodosulants";
update project set map = "malagasy_2331v2.gif" where project_name = "malagasyants";
update project set map = "mauritius_233.gif" where project_name = "mauritiusants";
update project set map = "Micronesia.jpg" where project_name = "micronesiaants";
update project set map = "MISSISSIPPI_233.gif" where project_name = "mississippiants";
update project set map = "missouriwh.gif" where project_name = "missouriants";
update project set map = "" where project_name = "neotropicalants";
update project set map = "netherlands_233.gif" where project_name = "netherlandsants";
update project set map = "new_guinea.jpg" where project_name = "newguineaants";
update project set map = "newzealand_233.gif" where project_name = "newzealandants";
update project set map = "Ohio_233.gif" where project_name = "ohioants";
update project set map = "" where project_name = "pacificislandsants";
update project set map = "Paraguay_233v2.gif" where project_name = "paraguayants";
update project set map = "Philippines_233.jpg" where project_name = "philippinesants";
update project set map = "worldv2.jpg" where project_name = "poeants";
update project set map = "" where project_name = "projectsants";
update project set map = "reunion_233.gif" where project_name = "reunionants";
update project set map = "sey_etm233.gif" where project_name = "seychellesants";
update project set map = "Slovenia_233.gif" where project_name = "sloveniaants";
update project set map = "Serbia_233.jpg" where project_name = "serbiaants";
update project set map = "" where project_name = "southcarolinaants";
update project set map = "" where project_name = "southeastasiaants";
update project set map = "" where project_name = "tennesseeants";
update project set map = "Tokelau_233v2.gif" where project_name = "tokelauants";
update project set map = "worldv2.jpg" where project_name = "worldants";

alter table taxon add column subfamily_count int(11) default 0, add column genus_count int(11) default 0
  , add column species_count int(11) default 0, add column specimen_count int(11) default 0;

alter table taxon drop column id;
alter table taxon add column parent_taxon_id int(15) default -1;
alter table specimen add column parent_taxon_id int(15) default -1;

alter table taxon drop primary key;
alter table taxon modify column taxon_name varchar (128) not null;
alter table taxon add unique index (taxon_name);
ALTER TABLE taxon ADD taxon_id INT(15) UNSIGNED NOT NULL AUTO_INCREMENT,
  ADD PRIMARY KEY (taxon_id);

alter table project add column coords varchar(128); 

#--- 

alter table taxon drop column subfamily_count;
alter table taxon drop column genus_count;
alter table taxon drop column species_count;
alter table taxon drop column specimen_count;

alter table proj_taxon add column subfamily_count int(11) default 0, add column genus_count int(11) default 0
  , add column species_count int(11) default 0, add column specimen_count int(11) default 0;

alter table taxon add column image_count int(11) default 0;