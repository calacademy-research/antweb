alter table geolocale add column introduced_species_count int(11);

alter table specimen add column is_endemic tinyint(4);


# Jun 7, 2017
delete from taxon where (source like '%ants' or source like '%speciesList.txt') 
  and source not in (select project_name from project) 
  and taxon_name not in (select distinct taxon_name from specimen)
  and not (
       taxon_name in (select taxon_name from geolocale_taxon)
    or taxon_name in (select taxon_name from museum_taxon)
    or taxon_name in (select taxon_name from proj_taxon where project_name != "allantwebants")
    or taxon_name in (select taxon_name from bioregion_taxon)
  )
;
update taxon set source = "oldSpeciesListants" where source like '%ants' and source not in (select project_name from project);

alter table drop column notes;
alter table geolocale add column admin_notes varchar(5000);

 
 select taxon_name from ant2.taxon t2 where taxon_name not in (select taxon_name from taxon); 
 
Delete taxon records that came from ants or species_list.txt
that do not have geolocale_taxon, bioregion_taxon, museum_taxon, proj_taxon
description edit
 
Run the following query also against museum, bioregion and proj. Insert from ant2 into ant to be good.
mysql> select taxon_name from ant2.taxon where taxon_name not in (select taxon_name from taxon) and taxon_name in (select taxon_name from geolocale_taxon);
+-----------------------------------+
| taxon_name                        |
+-----------------------------------+
| formicinaepolyrhachis circumflexa |
| myrmicinaecarebara melanocephalus |
| formicinaepolyrhachis reticulata  |
+-----------------------------------+
3 rows in set (2 min 54.00 sec)


insert into taxon
select * from ant2.taxon where taxon_name not in (select taxon_name from taxon) 
  and (
      taxon_name in (select taxon_name from geolocale_taxon)
    or taxon_name in (select taxon_name from museum_taxon)
    or taxon_name in (select taxon_name from proj_taxon)
    or taxon_name in (select taxon_name from bioregion_taxon)
    or taxon_name in (select taxon_name from description_edit)
  )
;
Query OK, 1252 rows affected (3 min 50.77 sec)

#

//Computed fields
alter table specimen add column is_male tinyint(4);
alter table specimen add column is_worker tinyint(4);
alter table specimen add column is_queen tinyint(4);

alter table taxon drop column default_specimen;

update taxon_prop set prop = "maleSpecimen" where prop = "default_specimen" and value in (select code from specimen where is_male = 1);
update taxon_prop set prop = "queenSpecimen" where prop = "default_specimen" and value in (select code from specimen where is_queen = 1);
update taxon_prop set prop = "workerSpecimen" where prop = "default_specimen" and value in (select code from specimen where is_worker = 1);

update taxon_prop set prop = "workerSpecimen" where prop = "default_specimen";


alter table groups add column upload_specimens int(11);
update groups set upload_specimens = 1 where id in (select distinct access_group from specimen);





# See the default_specimen without caste
select code, access_group, is_male, is_worker, is_queen, taxon_name, caste from specimen where code in (select value from taxon_prop where prop = "default_specimen") order by access_group;

# Verify statuses are updated for specimen records.
select s.code, s.status from specimen s where s.status != (select status from taxon where taxon_name = s.taxon_name);

select count(*) from specimen where !(is_male is null or is_worker is null or is_queen is null) and access_group = 57;


