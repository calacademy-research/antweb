-- Non-ants filed under the ant placeholder "(formicidae)": re-filed under their own family (Brian Fisher, 2026-11-08).
--
-- The upload gave every line without a subfamily the ant placeholder "(formicidae)", whatever its family, so non-ants
-- were filed as "(formicidae)(curculionidae) (curculionidae)", "(formicidae)(formicidae) (indet)" (CAS: a dragonfly, a
-- water beetle and a wasp under one name) or "(formicidae)zanna madagascariensis". Production 2026-11-08: 5,098
-- specimens (University of Utah 4,630, CAS 468). The upload now files them under their family (SpecimenUploadParse.
-- nonAntSubfamilyPlaceholder, nonAntIndetSpecies); this script re-files the specimens already loaded the same way:
--   subfamily  "(formicidae)"  -> "(<family>)"         ("(coleoptera)" when the family column holds a higher group)
--   genus      "(formicidae)"  -> "(<family>)"         (a real genus, or Utah's "(curculionidae)", is kept)
--   species    the group repeated in parentheses, or "_indet_" -> "(indet)" (status indetermined)
-- New taxon rows are copied from the old ones; old taxon rows no specimen uses any more are removed with their list
-- and place rows. Counts and lists follow at the next daily update.
--
-- Run on staging first, then production (not 04:45-08:15 Pacific), as one file:
--   F=<project dir>/etc/db.properties; docker exec -i -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 \
--     mysql -u "$(sed -n 's/^user=//p' "$F")" ant < <project dir>/db/upgrade/9.3.1/2026-11-08-nonant-family-placeholder.sql
-- It stops at the first error and refuses to run twice (the backup tables exist).
--
-- Rollback (the backup tables nonant_fix_20261108_*):
--   start transaction;
--   update specimen s join nonant_fix_20261108_specimen b on b.code = s.code
--     set s.taxon_name = b.taxon_name, s.subfamily = b.subfamily, s.genus = b.genus, s.species = b.species, s.status = b.status;
--   insert ignore into taxon select * from nonant_fix_20261108_oldtaxa;
--   insert into proj_taxon select * from nonant_fix_20261108_proj_taxon;
--   insert into geolocale_taxon select * from nonant_fix_20261108_geolocale_taxon;
--   insert into bioregion_taxon select * from nonant_fix_20261108_bioregion_taxon;
--   insert into museum_taxon select * from nonant_fix_20261108_museum_taxon;
--   -- list and place rows the daily update made for the new names since (review of #333):
--   delete from proj_taxon where taxon_name in (select taxon_name from nonant_fix_20261108_newtaxa);
--   delete from geolocale_taxon where taxon_name in (select taxon_name from nonant_fix_20261108_newtaxa);
--   delete from bioregion_taxon where taxon_name in (select taxon_name from nonant_fix_20261108_newtaxa);
--   delete from museum_taxon where taxon_name in (select taxon_name from nonant_fix_20261108_newtaxa);
--   delete t from taxon t join nonant_fix_20261108_newtaxa n on n.taxon_name = t.taxon_name
--     where not exists (select 1 from specimen s where s.taxon_name = t.taxon_name);
--   commit;
-- Run on production 2026-11-08 19:4x: 5,098 re-filed, 283 new taxon rows, 264 removed, 0 specimens without a taxon row.
-- Delete the backup tables after about 2026-12-08.

-- Reading the specimen table must not wait on the site's own work (staging 2026-11-08: "Lock wait timeout exceeded"
-- while the tests uploaded specimens): read committed rows without locking them, and wait up to 10 minutes for a lock.
set session transaction isolation level read committed;
set session innodb_lock_wait_timeout = 600;

-- 1. The specimens to re-file and their new name parts.
create table nonant_fix_20261108_specimen as
  select code, taxon_name, subfamily, genus, species, subspecies, status, family
  from specimen where subfamily = '(formicidae)' and family is not null and family <> '' and lower(family) <> 'formicidae';

create table nonant_fix_20261108_work as
  select code, taxon_name as old_name, family, subspecies, status as old_status,
         concat('(', regexp_replace(lower(trim(family)), '^[(]+|[)]+$', ''), ')') as new_sub,
         genus, species
  from nonant_fix_20261108_specimen;

alter table nonant_fix_20261108_work add column new_genus varchar(64), add column new_species varchar(64),
  add column new_status varchar(300), add column new_name varchar(128), add primary key (code);

update nonant_fix_20261108_work set new_genus =
  case when genus is null or genus = '' or genus = '(formicidae)' or genus = '(indet)' then new_sub else genus end;

update nonant_fix_20261108_work set new_species =
  case when lower(trim(species)) = '_indet_' then '(indet)'
       when species like '(%)' and (regexp_replace(lower(trim(species)), '^[(]+|[)]+$', '') = regexp_replace(new_sub, '^[(]+|[)]+$', '')
                                    or regexp_replace(lower(trim(species)), '^[(]+|[)]+$', '') = regexp_replace(lower(trim(new_genus)), '^[(]+|[)]+$', ''))
         then '(indet)'
       else species end;

update nonant_fix_20261108_work set
  new_status = case when new_species = '(indet)' then 'indetermined' else old_status end,
  new_name = concat(new_sub, new_genus, ' ', new_species, case when subspecies is null or subspecies = '' then '' else concat(' ', subspecies) end);

-- 2. New taxon rows, copied from the old row of each new name.
create table nonant_fix_20261108_newtaxa as
  select t.*, w.new_name, w.new_sub, w.new_genus, w.new_species, w.new_status, w.family as new_family
  from taxon t
  join (select new_name, min(old_name) as old_name, min(new_sub) as new_sub, min(new_genus) as new_genus,
               min(new_species) as new_species, min(new_status) as new_status, min(family) as family
        from nonant_fix_20261108_work group by new_name) w on w.old_name = t.taxon_name
  where w.new_name not in (select taxon_name from taxon);

update nonant_fix_20261108_newtaxa set
  parent = case when parent = parent_taxon_name then concat(new_sub, new_genus) else parent end,
  parent_taxon_name = concat(new_sub, new_genus),
  taxon_name = new_name, subfamily = new_sub, genus = new_genus, species = new_species, status = new_status,
  family = lower(new_family);

alter table nonant_fix_20261108_newtaxa drop column new_name, drop column new_sub, drop column new_genus,
  drop column new_species, drop column new_status, drop column new_family;

-- 3. The change itself.
start transaction;
insert into taxon select * from nonant_fix_20261108_newtaxa;
update specimen s join nonant_fix_20261108_work w on w.code = s.code
  set s.taxon_name = w.new_name, s.subfamily = w.new_sub, s.genus = w.new_genus, s.species = w.new_species,
      s.status = w.new_status
  where s.taxon_name = w.old_name;
commit;

-- 4. Old taxon rows no specimen uses any more, with their list and place rows (backed up first).
create table nonant_fix_20261108_oldtaxa as
  select * from taxon where taxon_name in (select distinct old_name from nonant_fix_20261108_work)
    and taxon_name not in (select distinct taxon_name from specimen where taxon_name is not null);
create table nonant_fix_20261108_proj_taxon as select * from proj_taxon where taxon_name in (select taxon_name from nonant_fix_20261108_oldtaxa);
create table nonant_fix_20261108_geolocale_taxon as select * from geolocale_taxon where taxon_name in (select taxon_name from nonant_fix_20261108_oldtaxa);
create table nonant_fix_20261108_bioregion_taxon as select * from bioregion_taxon where taxon_name in (select taxon_name from nonant_fix_20261108_oldtaxa);
create table nonant_fix_20261108_museum_taxon as select * from museum_taxon where taxon_name in (select taxon_name from nonant_fix_20261108_oldtaxa);

start transaction;
delete from proj_taxon where taxon_name in (select taxon_name from nonant_fix_20261108_oldtaxa);
delete from geolocale_taxon where taxon_name in (select taxon_name from nonant_fix_20261108_oldtaxa);
delete from bioregion_taxon where taxon_name in (select taxon_name from nonant_fix_20261108_oldtaxa);
delete from museum_taxon where taxon_name in (select taxon_name from nonant_fix_20261108_oldtaxa);
delete from taxon where taxon_name in (select taxon_name from nonant_fix_20261108_oldtaxa);
commit;

-- 5. Result.
select count(*) as specimens_refiled,
       sum(subfamily = '(formicidae)' and lower(family) <> 'formicidae') as still_under_formicidae_placeholder
  from specimen where code in (select code from nonant_fix_20261108_work);
select (select count(*) from nonant_fix_20261108_newtaxa) as new_taxon_rows,
       (select count(*) from nonant_fix_20261108_oldtaxa) as old_taxon_rows_removed,
       (select count(*) from specimen s left join taxon t on t.taxon_name = s.taxon_name
         where s.code in (select code from nonant_fix_20261108_work) and t.taxon_name is null) as specimens_without_taxon_row;
select new_name, count(*) as specimens from nonant_fix_20261108_work group by new_name order by specimens desc limit 12;
