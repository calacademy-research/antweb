-- Indexes for the place queries of the specimen table (Brian Fisher, 2026-11-09).
--
-- Production 2026-11-09: the daily update step "Place list totals" (PlaceViewTotalDb) took 60 minutes on 11-07 and 11-08
-- and about 4 hours that day. Each place's specimens are read by one condition on the specimen table; the indexes on
-- production were code_type, col_code_idx, col_museum_idx, country_adm1_idx (country, adm1), county_idx, created,
-- genus_species_idx, loc_code_idx, PRIMARY (code), specimen_taxon_name, subfamily. The code change of the same day
-- (B26 fixed) reads an adm1 by country and adm1, so country_adm1_idx serves adm1s and countries. Two kinds of place have
-- no index yet, and each of their reads scans every specimen:
--   island_country  the second part of a country's specimens (ListView.placeSpecimensSql: specimen.country = X, then
--                   specimen.island_country = X and not of country X), islands (specimen.island_country = X)
--   bioregion       bioregion pages, lists and counts (specimen.bioregion = X)
-- Both indexes are built online: the site keeps reading and writing the table (ALGORITHM=INPLACE, LOCK=NONE). The ALTER
-- waits for a metadata lock at its start and end only; run it at a quiet time, not 04:45-08:15 Pacific (nightly run),
-- not during an upload. Since 2026-11-09 the Place list totals commit after each place, so they no longer hold a
-- metadata lock for minutes (an online ALTER TABLE timed out waiting for it that morning). Building the two indexes
-- reads the whole table once (minutes, depending on its size: select count(*) from specimen).
--
-- Run on staging first, then production:
--   F=<project dir>/etc/db.properties; docker exec -i -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 \
--     mysql -u "$(sed -n 's/^user=//p' "$F")" ant < <project dir>/db/upgrade/9.3.1/2026-11-09-specimen-place-indexes.sql
-- It refuses to run twice (the index exists: "Duplicate key name").
-- Check after: show index from specimen where Key_name in ('island_country_idx', 'bioregion_idx');
--
-- Rollback (online as well):
--   alter table specimen drop index island_country_idx, drop index bioregion_idx, algorithm=inplace, lock=none;
--
-- Documented in docs/data-model.md (table specimen).

-- wait at most 60 s for the metadata lock (a long query or transaction on specimen): then stop, nothing changed
set session lock_wait_timeout = 60;

alter table specimen
  add index island_country_idx (island_country),
  add index bioregion_idx (bioregion),
  algorithm=inplace, lock=none;
