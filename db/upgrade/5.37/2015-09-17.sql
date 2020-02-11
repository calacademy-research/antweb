
alter table un_country add column created timestamp default CURRENT_TIMESTAMP;

drop table if exists antwiki_taxon_country;
CREATE TABLE antwiki_taxon_country (
    id int(11) not null auto_increment
  , rev int(11) not null
  , short_taxon_name varchar(128)
  , original_taxon_name varchar(128)
  , taxon_name varchar(128) NOT NULL
  , country varchar(100) not null
  , region varchar(100)      
  , is_introduced tinyint(4) 
  , source varchar(150) 
  , project_name varchar(40)
  , created timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
  , primary key (id)
  , unique KEY (rev, taxon_name, country)
) ENGINE=MyISAM DEFAULT CHARSET=utf8;

# alter table specimen add column project_name varchar(100);

alter table proj_taxon add column source varchar(100);

drop table if exists proj_taxon_dispute;
CREATE TABLE proj_taxon_dispute (
    project_name varchar(32) not null
  , taxon_name varchar(128) not null
  , source varchar(150)
  , rev int(11)
  , curator_id int(11)
  , created timestamp
);


#Create Borneo.  Map to borneoants.
delete from geolocale where name = "Borneo";
insert into geolocale (name, georank, is_valid, is_un, source) values ('Borneo', 'country', true, false, "sql");
update project set geolocale_id = (select id from geolocale where name = 'Borneo') where projectdescr_name = "borneoants";

#Create Macaronesia.  Map to macaronesiaants.
delete from geolocale where name = "Macaronesia";
insert into geolocale (name, georank, is_valid, is_un, source) values ('Macaronesia', 'country', true, false, "sql");
update project set geolocale_id = (select id from geolocale where name = 'Macaronesia') where project_name = "macaronesiaants";

#New Guinea to replace New Guinea|Papua New Guinea in australianants.  newguineaants already exists
delete from geolocale where name = "New Guinea";
insert into geolocale (name, georank, is_valid, is_un, source, parent, region, bioregion) values ('New Guinea', 'country', true, false, 'sql', ' Melanesia', 'Oceania', 'Oceania');
update project set geolocale_id = (select id from geolocale where name = 'New Guinea') where project_name = "newguineaants";
update geolocale set valid_name = "New Guinea" where name = "New Guinea|Papua New Guinea";

update geolocale set is_valid = true where name = "North Korea";
update geolocale set valid_name = "North Korea" where id = 118;
update geolocale set is_valid = true where name = "South Korea";
update geolocale set valid_name = "South Korea" where name = "Republic of Korea";
update geolocale set is_valid = true where name = "Iran";
update geolocale set valid_name = "Iran" where name = "Iran (Islamic Republic of)";
update geolocale set is_valid = true where name = "Tanzania";
update geolocale set valid_name = "Tanzania" where name = "United Republic of Tanzania";
update geolocale set is_valid = false where name = "United Republic of Tanzania";
update geolocale set is_valid = true where name = "Switzerland";
update geolocale set is_valid = true where name = "Rwanda";
update geolocale set is_valid = true where name = "Finland";
update geolocale set is_valid = true where name = "Libya";
update geolocale set is_valid = true where name = "Poland";
update geolocale set is_valid = true where name = "Palestine";
update geolocale set is_valid = true where name = "Ireland";
update geolocale set is_valid = true where name = "Swaziland";
update geolocale set is_valid = true where name = "Thailand";
update geolocale set is_valid = true where name = "Uganda";

#Barrow Island.  rename barrowants -> barrowislandants
update project set project_name = "barrowislandants" where project_name = "barrowants";
update proj_taxon set project_name = "barrowislandants" where project_name = "barrowants";
update proj_taxon_log set project_name = "barrowislandants" where project_name = "barrowants";
update proj_taxon_log_detail set project_name = "barrowislandants" where project_name = "barrowants"; 
update antwiki_taxon_country set project_name = "barrowislandants" where project_name = "barrowants";
update login_project set project_name = "barrowislandants" where project_name = "barrowants";
update favorite_images set project_name = "barrowislandants" where project_name = "barrowants";
#Create Barrow Islands geolocale

#Europa Island - set to valid (it is from specimen).  malagasyants?  rename europaants -> europaislandants.  
update project set project_name = "europaislandants" where project_name = "europaants";
update proj_taxon set project_name = "europaislandants" where project_name = "europaants";
update proj_taxon_log set project_name = "europaislandants" where project_name = "europaants";
update proj_taxon_log_detail set project_name = "europaislandants" where project_name = "europaants"; 
update antwiki_taxon_country set project_name = "europaislandants" where project_name = "europaants";
update login_project set project_name = "europaislandants" where project_name = "europaants";
update favorite_images set project_name = "europaislandants" where project_name = "europaants";
update geolocale set is_valid = true where name = "Europa Island";

#Solomon Island.  rename solomonsants -> solomonislandants
update project set project_name = "solomonislandsants" where project_name = "solomonsants";
update proj_taxon set project_name = "solomonislandsants" where project_name = "solomonsants";
update proj_taxon_log set project_name = "solomonislandsants" where project_name = "solomonsants";
update proj_taxon_log_detail set project_name = "solomonislandsants" where project_name = "solomonsants"; 
update antwiki_taxon_country set project_name = "solomonislandsants" where project_name = "solomonsants";
update login_project set project_name = "solomonislandsants" where project_name = "solomonsants";
update favorite_images set project_name = "solomonislandsants" where project_name = "solomonsants";
update project set project_title = "Solomon Islands" where project_name = "solomonislandsants";
update geolocale set is_valid = true where name = "solomon Islands";



# select project_name, country, p.geolocale_id, g.name from project p, geolocale g where p.geolocale_id = g.id and replace(concat(g.name, "ants"), " ", "") != project_name;

#select project_name, biogeographicregion from project where replace(project_name, "ants", "") not in (select replace(name, " ", "") from geolocale) 
#and biogeographicregion in ("africanants", "australianants", "southeastasiaants", "malagasyants", "nearcticants", "neotropicalants", "pacificislandsants", "eurasianants");


# To Do

# need to create valid, non-un, adm1?
bajaants          | NULL                 | nearcticants        |
xishuangbannaants | NULL                 | southeastasiaants 

#Koreas
1833 not valid
1975 valid
1836 not valid
1985 valid

1966 USA validName is United States

Tanzania valid
1732 (United Rep of Tanzania) unvalid.  Valid Name Tanzania

1969 Laos valid
cote' de ivory -> ivory coast
1968 iran valid.

Democratic Peoples Republic of Korea -> North Korea
Republic of Korea -> South Korea
 -> Micronesia


mysql> select count(*), country from antwiki_taxon_country where project_name is null group by country ;
+----------+------------------------------------------------------+
| count(*) | country                                              |
+----------+------------------------------------------------------+
|        1 | Åland Islands                                        |
|       84 | Andorra                                              |
|        6 | Antigua and Barbuda                                  |
|       80 | Balearic Islands                                     |
|        7 | Bosnia and Herzegovina                               |
|        9 | British Virgin Islands                               |
|      332 | Cameroun                                             |
|       85 | Canary Islands                                       |
|        7 | Cayman Islands                                       |
|       24 | Channel Islands                                      |
|        4 | Christmas Island                                     |
|        1 | Cocos (Keeling) Islands                              |
|       23 | Cook Islands                                         |
|      107 | Democratic Peoples Republic of Korea                 |
|        2 | Faeroe Islands                                       |
|        1 | Falkland Islands (Malvinas)                          |
|       62 | Galapagos Islands                                    |
|      257 | Greater Antilles                                     |
|       14 | Guinea-Bissau                                        |
|      254 | Iberian Peninsula                                    |
|        1 | Kashmir                                              |
|        1 | Kerguelen Islands                                    |
|       62 | Krakatau Islands                                     |
|       27 | Laos                                                 |
|      101 | Lesser Antilles                                      |
|       15 | Lord Howe Island                                     |
|       46 | Marshall Islands                                     |
|       85 | Micronesia (Federated States of)                     |
|       73 | Netherlands                                          |
|       10 | Netherlands Antilles                                 |
|       48 | New Zealand                                          |
|       41 | Nicobar Island                                       |
|       25 | Norfolk Island                                       |
|       46 | Northern Mariana Islands                             |
|      121 | Republic of Macedonia                                |
|        2 | Saint Kitts and Nevis                                |
|        5 | Saint Martin (French part)                           |
|       13 | Saint Vincent and the Grenadines                     |
|       15 | São Tomé & Principe                                  |
|        6 | St. Vincent                                          |
|      146 | Sulawesi                                             |
|       15 | Tibet                                                |
|        8 | Timor-Leste                                          |
|      276 | Trinidad and Tobago                                  |
|        5 | Turks and Caicos Islands                             |
|       78 | United Kingdom of Great Britain and Northern Ireland |
|        7 | United States Virgin Islands                         |
|       35 | Wallis and Futuna Islands                            |
+----------+------------------------------------------------------+
49 rows in set (0.10 sec)
