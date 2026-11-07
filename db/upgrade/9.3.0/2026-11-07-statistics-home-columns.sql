-- The home page numbers of release 9.3.0 (Brian Fisher, 2026-11-06), one column each on the statistics row.
-- Applied automatically at startup by StatisticsDb.ensureHomeColumns (called from LoginMgr), only for the columns
-- that are missing; this file records the change. Rollback: drop the columns (the home box then shows "not yet counted").
alter table statistics add column living_species int null;
alter table statistics add column fossil_species int null;
alter table statistics add column living_species_specimens int null;
alter table statistics add column living_species_imaged int null;
alter table statistics add column fossil_species_imaged int null;
alter table statistics add column living_subspecies int null;
alter table statistics add column morphospecies int null;
alter table statistics add column morphospecies_imaged int null;
