
alter table museum drop column chart_color;
alter table museum add column chart_color varchar(30);

alter table taxon drop column chart_color;
alter table taxon add column chart_color varchar(30);

alter table homonym drop column chart_color;
alter table homonym add column chart_color varchar(30);


# Above is deployed to live already

drop table if exists geolocale_taxon;
CREATE TABLE geolocale_taxon (
    geolocale_id int(11) NOT NULL
  , taxon_name varchar(128) NOT NULL
  , created timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
  , subfamily_count int(11) DEFAULT 0
  , genus_count int(11) DEFAULT 0
  , species_count int(11) DEFAULT 0
  , specimen_count int(11) DEFAULT 0
  , image_count int(11) DEFAULT 0
  , insert_method varchar(30)
  , chart_color varchar(30)
  , UNIQUE KEY geolocale_taxon (geolocale_id, taxon_name)
  , KEY taxon_name (taxon_name)
);

drop table if exists bioregion_taxon;
CREATE TABLE bioregion_taxon (
    bioregion_name varchar(28) NOT NULL
  , taxon_name varchar(128) NOT NULL
  , created timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
  , subfamily_count int(11) DEFAULT 0
  , genus_count int(11) DEFAULT 0
  , species_count int(11) DEFAULT 0
  , specimen_count int(11) DEFAULT 0
  , image_count int(11) DEFAULT 0
  , insert_method varchar(30)
  , chart_color varchar(30)
  , UNIQUE KEY bioregion_taxon (bioregion_name, taxon_name)
  , KEY taxon_name (taxon_name)
);


alter table geolocale add column subfamily_count int(11) DEFAULT NULL;
alter table geolocale add column genus_count int(11) DEFAULT NULL;
alter table geolocale add column species_count int(11) DEFAULT NULL;
alter table geolocale add column specimen_count int(11) DEFAULT NULL;
alter table geolocale add column image_count int(11) DEFAULT NULL;
alter table geolocale add column imaged_specimen_count int(11) DEFAULT NULL;
alter table geolocale add column taxon_subfamily_dist_json varchar(5000);
alter table geolocale add column specimen_subfamily_dist_json varchar(5000);
alter table geolocale add column chart_color varchar(30);

alter table bioregion add column subfamily_count int(11) DEFAULT NULL;
alter table bioregion add column genus_count int(11) DEFAULT NULL;
alter table bioregion add column species_count int(11) DEFAULT NULL;
alter table bioregion add column specimen_count int(11) DEFAULT NULL;
alter table bioregion add column image_count int(11) DEFAULT NULL;
alter table bioregion add column imaged_specimen_count int(11) DEFAULT NULL;
alter table bioregion add column taxon_subfamily_dist_json varchar(5000);
alter table bioregion add column specimen_subfamily_dist_json varchar(5000);
alter table bioregion add column chart_color varchar(30);

alter table project add column subfamily_count int(11) DEFAULT NULL;
alter table project add column genus_count int(11) DEFAULT NULL;
alter table project add column species_count int(11) DEFAULT NULL;
alter table project add column specimen_count int(11) DEFAULT NULL;
alter table project add column image_count int(11) DEFAULT NULL;
alter table project add column imaged_specimen_count int(11) DEFAULT NULL;
alter table project add column taxon_subfamily_dist_json varchar(5000);
alter table project add column specimen_subfamily_dist_json varchar(5000);
alter table project add column chart_color varchar(30);

alter table project add column endemic_species_count int(11);
alter table proj_taxon add column is_endemic tinyint(4);



update bioregion set name = "Afrotropical" where name = "Afrotropic";
update bioregion set name = "Neotropical" where name = "Neotropic";

update geolocale set bioregion = "Afrotropical" where bioregion = "Afrotropic";
update geolocale set bioregion = "Neotropical" where bioregion = "Neotropic";

update specimen set biogeographicregion = "Afrotropical" where biogeographicregion = "Afrotropic";
update specimen set biogeographicregion = "Neotropical" where biogeographicregion = "Neotropic";

update specimen set biogeographicregion = "Palearctic" where biogeographicregion = "Eurasian";
update specimen set biogeographicregion = "Oceania" where biogeographicregion = "Pacific Islands";

alter table project add column bioregion varchar(64);
update project set bioregion = "Palearctic" where biogeographicregion = "eurasianants";
update project set bioregion = "Nearctic" where biogeographicregion = "nearcticants";
update project set bioregion = "Oceania" where biogeographicregion = "pacificislandsants";
update project set bioregion = "Afrotropical" where biogeographicregion = "africanants";
update project set bioregion = "Neotropical" where biogeographicregion = "neotropicalants";
update project set bioregion = "Australasia" where biogeographicregion = "australianants";
update project set bioregion = "Indomalaya" where biogeographicregion = "southeastasiaants";
update project set bioregion = "Malagasy" where biogeographicregion = "malagasyants";

update project set bioregion = "GLOBAL" where biogeographicregion = "globalants";
update project set bioregion = "PROJECT" where biogeographicregion = "projectsants";

delete from project where project_name in ('africanants', 'australianants', 'eurasianants', 'globalants', 'malagasyants', 'nearcticants', 'neotropicalants', 'pacificislandsants', 'projectsants', 'southeastasiaants');

update bioregion set title = "Palearctic" where title = "Eurasian";
update bioregion set title = "Oceania" where title = "Pacific Islands";
update bioregion set title = "Indomalaya" where title = "South East Asia";
update bioregion set title = "Australasia" where title = "Australian";
update bioregion set title = "Afrotropical" where title = "African";

update bioregion set locality = "bioregion='Afrotropical'" where name = "Afrotropical";
update bioregion set locality = "bioregion='Malagasy'" where name = "Malagasy";
update bioregion set locality = "bioregion='Palearctic'" where name = "Palearctic";
update bioregion set locality = "bioregion='Australasia'" where name = "Australasia";
update bioregion set locality = "bioregion='Indomalaya'" where name = "Indomalaya";
update bioregion set locality = "bioregion='Oceania'" where name = "Oceania";
update bioregion set locality = "bioregion='Nearctic'" where name = "Nearctic";
update bioregion set locality = "bioregion='Neotropical'" where name = "Neotropical";

alter table bioregion drop column project_name;

# To get highest...
insert into proj_taxon (project_name, taxon_name, source, is_endemic) select distinct project_name, "formicidae", "manual6.3", 0 from proj_taxon where project_name not in (select distinct project_name from proj_taxon where taxon_name = "formicidae");

#  delete from favorite_images where taxon_name = "stenamma";

update favorite_images set project_name = "wasDefault" where project_name = "default" and taxon_name in 
 (select taxon_name from taxon where rank in ("subfamily", "genus")) 
 and project_name = "default";

