-- Removed names keep a record (Brian Fisher, 2026-11-03: "We need transparency: transparency in source, and if
-- something is deleted, we still need transparency").
--
--   geolocale_taxon_dispute.reason, proj_taxon_dispute.reason  the curator's reason for removing a name with the
--                       Species List Tool ("Reason for removing (shown with the name)", optional, one line); null before
--                       2026-11-03 and when none was given
--   list_removal_log    one row per name removed from a country's, adm1's, region's, subregion's or project's list:
--     list_type         'geolocale' or 'project'
--     list_key          the geolocale id or the project name
--     taxon_name, source  the removed row's name and source
--     removed_by_login  the curator (Species List Tool), or null
--     removed_by_job    the job: "nightly clean-up: morphospecies with no specimens", "nightly clean-up: name no longer in
--                       AntWeb", "orphan tool: name deleted", or a one-off script's own name; null for a curator
--     reason            the curator's or the job's reason, or null
--     removed_at        when
--
-- Written by the Species List Tool's save (SpeciesListDb.saveTaxonSet), the nightly clean-up (TaxonSetDb.dataCleanup:
-- GeolocaleTaxonDb and ProjTaxonDb.deleteUncuratedMorphosWithoutSpecimen, deleteGeolocaleTaxaWithoutTaxon, the project
-- rows without a taxon) and the orphan tool (OrphansDb.deleteTaxon), just before each delete, by the same condition
-- (home.ListRemovalLog). Not by the routine rebuilds (the rows from specimens each night, the AntCat rows of set 1, the
-- Global list). Read by the lists' view All specimen names ("removed from the list by ... (date): reason").
--
-- Nothing to run by hand: the application adds both at startup when missing (ListRemovalLog.ensureSchema, from
-- LoginMgr; same DDL, the table with the character set and collation of taxon.taxon_name). This file is the record.
--
-- A one-off data script that deletes list rows records them first, with the delete's condition on alias l:
--   insert into list_removal_log (list_type, list_key, taxon_name, source, removed_by_job, reason, removed_at)
--     select 'geolocale', l.geolocale_id, l.taxon_name, l.source, 'script 2026-11-03 <name>', '<why>', now()
--     from geolocale_taxon l where <condition>;
--   (projects: 'project', l.project_name, from proj_taxon l)
--
-- Check:   select list_type, removed_by_job, count(*), max(removed_at) from list_removal_log group by 1, 2;
--          select count(*), count(reason) from geolocale_taxon_dispute;
-- Undo (deploy the previous code first): DROP TABLE list_removal_log;
--          ALTER TABLE geolocale_taxon_dispute DROP COLUMN reason; ALTER TABLE proj_taxon_dispute DROP COLUMN reason;

ALTER TABLE geolocale_taxon_dispute ADD COLUMN reason varchar(255) NULL;
ALTER TABLE proj_taxon_dispute ADD COLUMN reason varchar(255) NULL;

CREATE TABLE IF NOT EXISTS list_removal_log (
  id int NOT NULL AUTO_INCREMENT,
  list_type varchar(16) NOT NULL,
  list_key varchar(128) NOT NULL,
  taxon_name varchar(128) NOT NULL,
  source varchar(64) NULL,
  removed_by_login int NULL,
  removed_by_job varchar(80) NULL,
  reason varchar(255) NULL,
  removed_at datetime NOT NULL,
  PRIMARY KEY (id),
  KEY list_removal_place (list_type, list_key, taxon_name),
  KEY list_removal_at (removed_at)
) ENGINE=InnoDB;
