update geolocale set bounding_box = null where bounding_box = "null";


drop table if exists taxon_prop;
create table taxon_prop (
  taxon_name varchar(128) not null
, prop varchar(30) not null
, value varchar(1000)
, created timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
, primary key (taxon_name, prop)
);


alter table taxon_prop add column login_id int(11);

# Release 6.21

alter table geolocale add column centroid varchar(60);
alter table geolocale add column centroid_fixed varchar(60);

# Run https://www.antweb.org/util.do?action=antwebMgrPopulate and then...

update geolocale set centroid_fixed = "-17.66667, 177.6667" where id = 956;
update geolocale set centroid_fixed = "-14.3753671, 48.6560913" where id = 3193;
update geolocale set centroid_fixed = "-60.66667, 11.25" where id = 4559;

# Release 6.22
alter table geolocale drop column latitude;
alter table geolocale drop column latitude_fixed;


update geolocale set parent = "Myanmar" where parent = "Burma" and name not in ('Bago', 'Kachin', 'Magway', 'Mandalay', 'Sagaing', 'Rakhine', 'Shan', 'Yangon');

update geolocale set is_valid = 1 where parent = "Myanmar" and name in ('Bago', 'Kachin', 'Magway', 'Mandalay', 'Sagaing', 'Rakhine', 'Shan', 'Yangon');

#
set charset utf8;
insert into geolocale (name, parent, georank, source) values ("Îles Loyauté", "New Caledonia", "adm1", "sql");

insert into geolocale (name, parent, georank, source) values ("Saint-Benoît", "Reunion", "adm1", "sql");

update geolocale set parent = "Reunion" where parent = "Réunion";

insert into geolocale (name, georank, valid_name, bioregion, parent, source, is_valid) values ("Réunion", "country", "Reunion", "Malagasy", "Eastern Africa", "sql", 0);  
update geolocale set region = "Africa" where name = "Réunion";

insert into geolocale (name, georank, bioregion, parent, source, is_valid) 
  values ("Pénama", "adm1", "Australasia", "Vanuatu", "sql", 0);  
insert into geolocale (name, georank, bioregion, parent, source, is_valid) 
  values ("Shéfa", "adm1", "Australasia", "Vanuatu", "sql", 0);  
insert into geolocale (name, georank, bioregion, parent, source, is_valid) 
  values ("Taféa", "adm1", "Australasia", "Vanuatu", "sql", 0);  

#insert into geolocale (name, georank, bioregion, parent, source, is_valid) 
#  values ("Šiaulių Miestas", "adm1", "Palearctic", "Lithuania", "sql", 0);  
#insert into geolocale (name, georank, bioregion, parent, source, is_valid) 
#  values ("Panevėžio Miestas", "adm1", "Palearctic", "Lithuania", "sql", 0);  
#insert into geolocale (name, georank, bioregion, parent, source, is_valid) 
#  values ("Klaipėdos Miestas", "adm1", "Palearctic", "Lithuania", "sql", 0);  

create view tempgeoview as select * from geolocale where id = 7739 or id = 7716 or id = 7728;
insert into geolocale (name, georank, bioregion, parent, source, is_valid) select concat(name, " Miestas"), georank, bioregion, parent, "sql", is_valid from tempgeoview;

create index valid_name_idx on geolocale (valid_name);

#---

update geolocale set rev = 0;

alter table geolocale add column georank_type varchar(100);
update geolocale set georank_type = "Province" where georank = "adm1";

?alter table geolocale modify column valid_name varchar(250) collate utf8_bin;	


//alter table taxon add column bioregion_map varchar(250);
//update taxon set bioregion_map = 'Neotropical:true Afrotropical:true Malagasy:true Australasia:true Oceania:true Indomalaya:true Palearctic:true Nearctic:true' where rank = "genus" and status = 'valid';

//alter table homonym add column bioregion_map varchar(250);
//update homonym set bioregion_map = 'Neotropical:true Afrotropical:true Malagasy:true Australasia:true Oceania:true Indomalaya:true Palearctic:true Nearctic:true' where rank = "genus" and status = 'valid';


alter table taxon drop column bioregion_map;
alter table homonym drop column bioregion_map;

#

alter table specimen add column bioregion varchar(64);
update specimen set bioregion = biogeographicregion;




select a.name, c.name, a.bioregion, c.bioregion  from geolocale a, geolocale c where a.parent = c.name and a.georank = "adm1" and a.bioregion = "none";

select concat(concat(concat("update geolocale set bioregion = '", c.bioregion), "' where id = "), a.id) from geolocale a, geolocale c where a.parent = c.name and a.georank = "adm1" and a.bioregion = "none";

update geolocale set bioregion = 'Australasia' where id = 400     ;
update geolocale set bioregion = 'Neotropical' where id = 579     ;
update geolocale set bioregion = 'Neotropical' where id = 579     ;
update geolocale set bioregion = 'Neotropical' where id = 608     ;
update geolocale set bioregion = 'Neotropical' where id = 657     ;
update geolocale set bioregion = 'Neotropical' where id = 657     ;
update geolocale set bioregion = 'Palearctic' where id = 669      ;
update geolocale set bioregion = 'Neotropical' where id = 694     ;
update geolocale set bioregion = 'Neotropical' where id = 694     ;
update geolocale set bioregion = 'Neotropical' where id = 732     ;
update geolocale set bioregion = 'Neotropical' where id = 752     ;
update geolocale set bioregion = 'Australasia' where id = 857     ;
update geolocale set bioregion = 'Malagasy' where id = 872        ;
update geolocale set bioregion = 'Oceania' where id = 956         ;
update geolocale set bioregion = 'Palearctic' where id = 1076     ;
update geolocale set bioregion = 'Neotropical' where id = 1150    ;
update geolocale set bioregion = 'Neotropical' where id = 1151    ;
update geolocale set bioregion = 'Palearctic' where id = 1264     ;
update geolocale set bioregion = 'Malagasy' where id = 1405       ;
update geolocale set bioregion = 'Afrotropical' where id = 1435   ;
update geolocale set bioregion = 'Palearctic' where id = 1443     ;
update geolocale set bioregion = 'Afrotropical' where id = 1471   ;
update geolocale set bioregion = 'Palearctic' where id = 1615     ;
update geolocale set bioregion = 'Neotropical' where id = 1617    ;
update geolocale set bioregion = 'Palearctic' where id = 8753     ;
update geolocale set bioregion = 'Palearctic' where id = 1675     ;
update geolocale set bioregion = 'Palearctic' where id = 1815     ;
update geolocale set bioregion = 'Afrotropical' where id = 1849   ;
update geolocale set bioregion = 'Afrotropical' where id = 2207   ;
update geolocale set bioregion = 'Neotropical' where id = 2301    ;
update geolocale set bioregion = 'Palearctic' where id = 2321     ;
update geolocale set bioregion = 'Oceania' where id = 2481        ;
update geolocale set bioregion = 'Palearctic' where id = 2724     ;
update geolocale set bioregion = 'Palearctic' where id = 2873     ;
update geolocale set bioregion = 'Palearctic' where id = 3053     ;
update geolocale set bioregion = 'Palearctic' where id = 3054     ;
update geolocale set bioregion = 'Palearctic' where id = 3055     ;
update geolocale set bioregion = 'Palearctic' where id = 3056     ;
update geolocale set bioregion = 'Palearctic' where id = 3057     ;
update geolocale set bioregion = 'Palearctic' where id = 3058     ;
update geolocale set bioregion = 'Palearctic' where id = 3059     ;
update geolocale set bioregion = 'Palearctic' where id = 3060     ;
update geolocale set bioregion = 'Palearctic' where id = 3061     ;
update geolocale set bioregion = 'Palearctic' where id = 3062     ;
update geolocale set bioregion = 'Palearctic' where id = 3063     ;
update geolocale set bioregion = 'Palearctic' where id = 3064     ;
update geolocale set bioregion = 'Palearctic' where id = 3065     ;
update geolocale set bioregion = 'Malagasy' where id = 3176       ;
update geolocale set bioregion = 'Malagasy' where id = 3177       ;
update geolocale set bioregion = 'Malagasy' where id = 3178       ;
update geolocale set bioregion = 'Malagasy' where id = 3179       ;
update geolocale set bioregion = 'Malagasy' where id = 3180       ;
update geolocale set bioregion = 'Malagasy' where id = 3181       ;
update geolocale set bioregion = 'Malagasy' where id = 3182       ;
update geolocale set bioregion = 'Malagasy' where id = 3183       ;
update geolocale set bioregion = 'Malagasy' where id = 3184       ;
update geolocale set bioregion = 'Malagasy' where id = 3185       ;
update geolocale set bioregion = 'Malagasy' where id = 3186       ;
update geolocale set bioregion = 'Malagasy' where id = 3187       ;
update geolocale set bioregion = 'Malagasy' where id = 3188       ;
update geolocale set bioregion = 'Malagasy' where id = 3189       ;
update geolocale set bioregion = 'Malagasy' where id = 3190       ;
update geolocale set bioregion = 'Malagasy' where id = 3191       ;
update geolocale set bioregion = 'Malagasy' where id = 3192       ;
update geolocale set bioregion = 'Malagasy' where id = 3193       ;
update geolocale set bioregion = 'Malagasy' where id = 3194       ;
update geolocale set bioregion = 'Malagasy' where id = 3196       ;
update geolocale set bioregion = 'Malagasy' where id = 3197       ;
update geolocale set bioregion = 'Palearctic' where id = 3277     ;
update geolocale set bioregion = 'Palearctic' where id = 3536     ;
update geolocale set bioregion = 'Palearctic' where id = 3537     ;
update geolocale set bioregion = 'Palearctic' where id = 3746     ;
update geolocale set bioregion = 'Palearctic' where id = 3916     ;
update geolocale set bioregion = 'Neotropical' where id = 4394    ;
update geolocale set bioregion = 'Neotropical' where id = 4394    ;
update geolocale set bioregion = 'Neotropical' where id = 4553    ;
update geolocale set bioregion = 'Neotropical' where id = 4556    ;
update geolocale set bioregion = 'Neotropical' where id = 4559    ;
update geolocale set bioregion = 'Neotropical' where id = 4980    ;
update geolocale set bioregion = 'Palearctic' where id = 5375     ;
update geolocale set bioregion = 'Palearctic' where id = 5421     ;
update geolocale set bioregion = 'Palearctic' where id = 6895     ;
update geolocale set bioregion = 'Palearctic' where id = 6887     ;
update geolocale set bioregion = 'Palearctic' where id = 6883     ;
update geolocale set bioregion = 'Palearctic' where id = 6882     ;
update geolocale set bioregion = 'Palearctic' where id = 6877     ;
update geolocale set bioregion = 'Palearctic' where id = 6816     ;
update geolocale set bioregion = 'Oceania' where id = 6211        ;
update geolocale set bioregion = 'Oceania' where id = 6212        ;
update geolocale set bioregion = 'Oceania' where id = 6213        ;
update geolocale set bioregion = 'Oceania' where id = 6214        ;
update geolocale set bioregion = 'Oceania' where id = 6215        ;
update geolocale set bioregion = 'Palearctic' where id = 6238     ;
update geolocale set bioregion = 'Neotropical' where id = 6397    ;
update geolocale set bioregion = 'Neotropical' where id = 6400    ;
update geolocale set bioregion = 'Palearctic' where id = 6539     ;
update geolocale set bioregion = 'Palearctic' where id = 6672     ;
update geolocale set bioregion = 'Palearctic' where id = 6673     ;
update geolocale set bioregion = 'Palearctic' where id = 6941     ;
update geolocale set bioregion = 'Palearctic' where id = 6945     ;
update geolocale set bioregion = 'Afrotropical' where id = 7410   ;
update geolocale set bioregion = 'Palearctic' where id = 7093     ;
update geolocale set bioregion = 'Palearctic' where id = 7102     ;
update geolocale set bioregion = 'Palearctic' where id = 7103     ;
update geolocale set bioregion = 'Palearctic' where id = 7104     ;
update geolocale set bioregion = 'Palearctic' where id = 7106     ;
update geolocale set bioregion = 'Palearctic' where id = 7107     ;
update geolocale set bioregion = 'Palearctic' where id = 7108     ;
update geolocale set bioregion = 'Palearctic' where id = 7109     ;
update geolocale set bioregion = 'Palearctic' where id = 7110     ;
update geolocale set bioregion = 'Palearctic' where id = 7111     ;
update geolocale set bioregion = 'Palearctic' where id = 7112     ;
update geolocale set bioregion = 'Palearctic' where id = 7113     ;
update geolocale set bioregion = 'Palearctic' where id = 7115     ;
update geolocale set bioregion = 'Palearctic' where id = 7116     ;
update geolocale set bioregion = 'Palearctic' where id = 7117     ;
update geolocale set bioregion = 'Palearctic' where id = 7118     ;
update geolocale set bioregion = 'Palearctic' where id = 7119     ;
update geolocale set bioregion = 'Palearctic' where id = 7120     ;
update geolocale set bioregion = 'Palearctic' where id = 7133     ;
update geolocale set bioregion = 'Palearctic' where id = 7134     ;
update geolocale set bioregion = 'Palearctic' where id = 7135     ;
update geolocale set bioregion = 'Afrotropical' where id = 7137   ;
update geolocale set bioregion = 'Palearctic' where id = 7207     ;
update geolocale set bioregion = 'Afrotropical' where id = 7310   ;
update geolocale set bioregion = 'Afrotropical' where id = 7316   ;
update geolocale set bioregion = 'Afrotropical' where id = 7318   ;
update geolocale set bioregion = 'Afrotropical' where id = 7319   ;
update geolocale set bioregion = 'Afrotropical' where id = 7320   ;
update geolocale set bioregion = 'Palearctic' where id = 7389     ;
update geolocale set bioregion = 'Palearctic' where id = 7402     ;
update geolocale set bioregion = 'Palearctic' where id = 7404     ;
update geolocale set bioregion = 'Palearctic' where id = 7408     ;
update geolocale set bioregion = 'Afrotropical' where id = 7412   ;
update geolocale set bioregion = 'Afrotropical' where id = 7413   ;
update geolocale set bioregion = 'Afrotropical' where id = 7414   ;
update geolocale set bioregion = 'Afrotropical' where id = 7418   ;
update geolocale set bioregion = 'Palearctic' where id = 7463     ;
update geolocale set bioregion = 'Palearctic' where id = 7464     ;
update geolocale set bioregion = 'Palearctic' where id = 7466     ;
update geolocale set bioregion = 'Palearctic' where id = 7467     ;
update geolocale set bioregion = 'Palearctic' where id = 7468     ;
update geolocale set bioregion = 'Palearctic' where id = 7469     ;
update geolocale set bioregion = 'Palearctic' where id = 7470     ;
update geolocale set bioregion = 'Palearctic' where id = 7471     ;
update geolocale set bioregion = 'Palearctic' where id = 7472     ;
update geolocale set bioregion = 'Palearctic' where id = 7473     ;
update geolocale set bioregion = 'Palearctic' where id = 7474     ;
update geolocale set bioregion = 'Palearctic' where id = 7475     ;
update geolocale set bioregion = 'Palearctic' where id = 7476     ;
update geolocale set bioregion = 'Palearctic' where id = 7538     ;
update geolocale set bioregion = 'Palearctic' where id = 7541     ;
update geolocale set bioregion = 'Palearctic' where id = 7543     ;
update geolocale set bioregion = 'Palearctic' where id = 7545     ;
update geolocale set bioregion = 'Palearctic' where id = 7546     ;
update geolocale set bioregion = 'Palearctic' where id = 7548     ;
update geolocale set bioregion = 'Palearctic' where id = 7560     ;
update geolocale set bioregion = 'Palearctic' where id = 7579     ;
update geolocale set bioregion = 'Palearctic' where id = 7581     ;
update geolocale set bioregion = 'Palearctic' where id = 7582     ;
update geolocale set bioregion = 'Palearctic' where id = 7583     ;
update geolocale set bioregion = 'Palearctic' where id = 7586     ;
update geolocale set bioregion = 'Palearctic' where id = 7587     ;
update geolocale set bioregion = 'Palearctic' where id = 7639     ;
update geolocale set bioregion = 'Palearctic' where id = 7640     ;
update geolocale set bioregion = 'Palearctic' where id = 7697     ;
update geolocale set bioregion = 'Palearctic' where id = 7890     ;
update geolocale set bioregion = 'Palearctic' where id = 7922     ;
update geolocale set bioregion = 'Palearctic' where id = 7923     ;
update geolocale set bioregion = 'Palearctic' where id = 7924     ;
update geolocale set bioregion = 'Palearctic' where id = 8061     ;
update geolocale set bioregion = 'Palearctic' where id = 8115     ;
update geolocale set bioregion = 'Palearctic' where id = 8120     ;
update geolocale set bioregion = 'Palearctic' where id = 8320     ;
update geolocale set bioregion = 'Palearctic' where id = 8321     ;
update geolocale set bioregion = 'Palearctic' where id = 8420     ;
update geolocale set bioregion = 'Indomalaya' where id = 8654     ;
update geolocale set bioregion = 'Malagasy' where id = 8676       ;
update geolocale set bioregion = 'Palearctic' where id = 8678     ;
update geolocale set bioregion = 'Afrotropical' where id = 8679   ;
update geolocale set bioregion = 'Afrotropical' where id = 8680   ;
update geolocale set bioregion = 'Palearctic' where id = 8711     ;
update geolocale set bioregion = 'Australasia' where id = 8743    ;
update geolocale set bioregion = 'Palearctic' where id = 8809     ;

#

drop table project20160809;
drop table proj_taxon20160809;
drop table proj_taxon20160804;
drop table proj_taxonProd;
drop table proj_taxon20170422;
drop table project20160804;
drop table project20170422;
drop table object_edit20151210;

drop table groups_project;


create table proj_taxon20170422 as select * from proj_taxon;
delete from proj_taxon where project_name in (select project_name from project where geolocale_id is not null);
create table project20170422 as select * from project;
delete from project where geolocale_id is not null;
alter table project drop column geolocale_id;

alter table project drop column biogeographicregion;

alter table project add column scope varchar(64);
update project set scope = bioregion;
alter table project drop column bioregion;

#

alter table geolocale drop column latitude;
alter table geolocale drop column longitude;
alter table geolocale drop column longitude_fixed;
alter table geolocale drop column latitude_fixed;



alter table specimen add column line varchar(3000);
alter table specimen drop column line;
alter table specimen add backup_file_name varchar(50);

create table favorite_images_bak as select * from favorite_images;
drop table favorite_images;


create table taxon_country_bak as select * from taxon_country;
drop table taxon_country;

create table similar_species_bak as select * from similar_species;
drop table similar_species;

