#alter table proj_taxon add column subfamily varchar(64);
#alter table proj_taxon add column genus varchar(64);
#alter table proj_taxon add column species varchar(64);
#alter table proj_taxon add column subspecies varchar(64);


alter table project add column display_key varchar(64);

alter table specimen add column is_introduced tinyint(4);


update proj_taxon set source = "speciesListUpload" where source is null;

delete from proj_taxon where length(taxon_name) - length(replace(taxon_name, ' ', '')) >= 3 and project_name not in ('allantwebants', 'worldants');

# start here

update geolocale set is_valid = 1 where parent = "United States" and name not in ('MA', 'VT', 'PA', 'NY', 'NJ', 'D.C.', 'Line Islands', 'Oahu', 'Office of Insular Affairs', 'Washington DC');

update geolocale set valid_name = 'Distinct of Columbia' where name ='D.C.';
update geolocale set valid_name = 'Massachusetts' where name ='MA';
update geolocale set valid_name = 'New Jersey' where name ='NJ';
update geolocale set valid_name = 'New York' where name ='NY';
update geolocale set valid_name = 'Hawaii' where name ='Oahu';
update geolocale set valid_name = 'Pennsylvania' where name ='PA';
update geolocale set valid_name = 'Vermont' where name ='VT';
update geolocale set valid_name = 'Distinct of Columbia' where name = 'Washington DC';

update project set adm1 = 'Tennessee' where project_name = 'tennesseeants';
update project set adm1 = 'South Carolina' where project_name = 'southcarolinaants';

update project set country = "United States" where adm1 is not null;

update geolocale set parent = 'United States', is_valid = 1, bioregion = 'Nearctic' where name = 'New Jersey';
update geolocale set parent = 'United States', is_valid = 1, bioregion = 'Nearctic' where name = 'Colorado';
update geolocale set parent = 'United States', is_valid = 1, bioregion = 'Nearctic' where name = 'Wyoming';

update geolocale set region = 'Americas', bioregion = 'Nearctic' where parent = 'United States' and is_valid = 1;

insert into geolocale (name, georank, is_valid, is_un, source, parent, region, bioregion) values ('Vermont', 'adm1', 1, 0, 'sql', 'United States', 'Americas', 'Nearctic');
insert into geolocale (name, georank, is_valid, is_un, source, parent, region, bioregion) values ('Rhode Island', 'adm1', 1, 0, 'sql', 'United States', 'Americas', 'Nearctic');

delete from geolocale where name in ('Misouri', 'Oaklahoma');

update project set adm1 = 'Georgia' where project_name = 'georgiaants';

update project set geolocale_id = (select id from geolocale where name = 'Alabama') where project_name = 'alabamaants';
update project set geolocale_id = (select id from geolocale where name = 'Arizona') where project_name = 'arizonaants';
update project set geolocale_id = (select id from geolocale where name = 'Arkansas') where project_name = 'arkansasants';
update project set geolocale_id = (select id from geolocale where name = 'California') where project_name = 'californiaants';
update project set geolocale_id = (select id from geolocale where name = 'Colorado') where project_name = 'coloradoants';
update project set geolocale_id = (select id from geolocale where name = 'Florida') where project_name = 'floridaants';
update project set geolocale_id = (select id from geolocale where name = 'Georgia' and georank = "adm1") where project_name = 'georgiaants';
update project set geolocale_id = (select id from geolocale where name = 'Illinois') where project_name = 'illinoisants';
update project set geolocale_id = (select id from geolocale where name = 'Louisiana') where project_name = 'louisianaants';
update project set geolocale_id = (select id from geolocale where name = 'Mississippi') where project_name = 'mississippiants';
update project set geolocale_id = (select id from geolocale where name = 'Missouri') where project_name = 'missouriants';
update project set geolocale_id = (select id from geolocale where name = 'New Mexico') where project_name = 'newmexicoants';
update project set geolocale_id = (select id from geolocale where name = 'North Carolina') where project_name = 'northcarolinaants';
update project set geolocale_id = (select id from geolocale where name = 'Ohio') where project_name = 'ohioants';
update project set geolocale_id = (select id from geolocale where name = 'Pennsylvania') where project_name = 'pennsylvaniaants';
update project set geolocale_id = (select id from geolocale where name = 'South Carolina') where project_name = 'southcarolinaants';
update project set geolocale_id = (select id from geolocale where name = 'Tennessee') where project_name = 'tennesseeants';
update project set geolocale_id = (select id from geolocale where name = 'Texas') where project_name = 'texasants';
update project set geolocale_id = (select id from geolocale where name = 'Utah') where project_name = 'utahants';

delete from geolocale where name = 'Hawaii' and georank = 'country';
update geolocale set bioregion = 'Oceana' where name = 'Hawaii';
update project set geolocale_id = (select id from geolocale where name = 'Hawaii') where project_name = 'hawaiiants';


insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('alaskaants', 'Alaska', 'sql', 'nearcticants', 'United States', 'alaska', 'Alaska', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'Alaska') where project_name = 'alaskaants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('connecticutants', 'Connecticut', 'sql', 'nearcticants', 'United States', 'connecticut', 'Connecticut', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'Connecticut') where project_name = 'connecticutants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('delawareants', 'Delaware', 'sql', 'nearcticants', 'United States', 'delaware', 'Delaware', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'Delaware') where project_name = 'delawareants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('districtofcolumbiaants', 'District of Columbia', 'sql', 'nearcticants', 'United States', 'districtofcolumbia', 'District of Columbia', 1, 0);
update project set geolocale_id = (select id from geolocale where name = 'District of Columbia') where project_name = 'districtofcolumbiaants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('idahoants', 'Idaho', 'sql', 'nearcticants', 'United States', 'idaho', 'Idaho', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'Idaho') where project_name = 'idahoants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('indianaants', 'Indiana', 'sql', 'nearcticants', 'United States', 'indiana', 'Indiana', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'Indiana') where project_name = 'indianaants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('iowaants', 'iowa', 'sql', 'nearcticants', 'United States', 'iowa', 'Iowa', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'Iowa') where project_name = 'iowaants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('kansasants', 'Kansas', 'sql', 'nearcticants', 'United States', 'kansas', 'Kansas', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'Kansas') where project_name = 'kansasants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('kentuckyants', 'Kentucky', 'sql', 'nearcticants', 'United States', 'kentucky', 'Kentucky', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'Kentucky') where project_name = 'kentuckyants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('maineants', 'Maine', 'sql', 'nearcticants', 'United States', 'maine', 'Maine', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'Maine') where project_name = 'maineants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('marylandants', 'Maryland', 'sql', 'nearcticants', 'United States', 'maryland', 'Maryland', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'Maryland') where project_name = 'Marylandants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('massachusettsants', 'Massachusetts', 'sql', 'nearcticants', 'United States', 'massachusetts', 'Massachusetts', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'Massachusetts') where project_name = 'massachusettsants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('michiganants', 'Michigan', 'sql', 'nearcticants', 'United States', 'michigan', 'Michigan', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'Michigan') where project_name = 'michiganants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('minnesotaants', 'Minnesota', 'sql', 'nearcticants', 'United States', 'minnesota', 'Minnesota', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'Minnesota') where project_name = 'minnesotaants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('montanaants', 'Montana', 'sql', 'nearcticants', 'United States', 'montana', 'Montana', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'Montana') where project_name = 'montanaants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('nebraskaants', 'Nebraska', 'sql', 'nearcticants', 'United States', 'nebraska', 'Nebraska', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'Nebraska') where project_name = 'nebraskaants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('nevadaants', 'Nevada', 'sql', 'nearcticants', 'United States', 'nevada', 'Nevada', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'Nevada') where project_name = 'nevadaants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('newhampshireants', 'New Hampshire', 'sql', 'nearcticants', 'United States', 'newhampshire', 'New Hampshire', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'New Hampshire') where project_name = 'newhampshireants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('newjerseyants', 'New Jersey', 'sql', 'nearcticants', 'United States', 'newjersey', 'New Jersey', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'New Jersey') where project_name = 'newjerseyants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('newyorkants', 'New York', 'sql', 'nearcticants', 'United States', 'newyork', 'New York', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'New York') where project_name = 'newyorkants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('northdakotaants', 'North Dakota', 'sql', 'nearcticants', 'United States', 'northdakota', 'North Dakota', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'North Dakota') where project_name = 'northdakotaants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('oklahomaants', 'Oklahoma', 'sql', 'nearcticants', 'United States', 'oklahoma', 'Oklahoma', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'Oklahoma') where project_name = 'oklahomaants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('oregonants', 'Oregon', 'sql', 'nearcticants', 'United States', 'oregon', 'Oregon', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'Oregon') where project_name = 'oregonants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('rhodeislandants', 'Rhode Island', 'sql', 'nearcticants', 'United States', 'rhodeisland', 'Rhode Island', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'Rhode Island') where project_name = 'rhodeislandants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('southdakotaants', 'South Dakota', 'sql', 'nearcticants', 'United States', 'southdakota', 'South Dakota', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'South Dakota') where project_name = 'southdakotaants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('vermontants', 'Vermont', 'sql', 'nearcticants', 'United States', 'vermont', 'Vermont', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'Vermont') where project_name = 'vermontants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('virginiaants', 'Virginia', 'sql', 'nearcticants', 'United States', 'virginia', 'Virginia', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'Virginia') where project_name = 'virginiaants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('washingtonants', 'Washington', 'sql', 'nearcticants', 'United States', 'washington', 'Washington', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'Washington') where project_name = 'washingtonants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('westvirginiaants', 'West Virginia', 'sql', 'nearcticants', 'United States', 'westvirginia', 'West Virginia', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'West Virginia') where project_name = 'westvirginiaants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('wisconsinants', 'Wisconsin', 'sql', 'nearcticants', 'United States', 'wisconsin', 'Wisconsin', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'Wisconsin') where project_name = 'wisconsinants';

insert into project (project_name, project_title, source, biogeographicregion, country, root, adm1, species_list_mappable, is_live)
   values ('wyomingants', 'Wyoming', 'sql', 'nearcticants', 'United States', 'wyoming', 'Wyoming', 1, 1);
update project set geolocale_id = (select id from geolocale where name = 'Wyoming') where project_name = 'wyomingants';


update geolocale set valid_name = "Galapagos Islands" where name = "Galapagos" and georank = 'adm1';


update project set geolocale_id = (select id from geolocale where name = 'Alabama') where project_name = 'alabamaants';



# Remove table column
#   geolocale is_live
#   project root
