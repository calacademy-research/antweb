-- Curator rows of region and project species lists are kept (Brian, 2026-10-19).
--
--   taxon_set_parked   a curator's row of geolocale_taxon (list_type 'geolocale', list_key =
--                      geolocale_id) or proj_taxon (list_type 'project', list_key = project_name)
--                      whose taxon row was missing when a cleanup ran (nightly set 2 dataCleanup,
--                      the orphan step of a specimen upload). Restored into its list when the
--                      name exists again (start of dataCleanup, or utilData.do?action=
--                      restoreParkedTaxonSets); restored = when that happened.
--
-- Nothing to run by hand: the application creates the table when it is missing
-- (CuratorTaxonSetDb.ensureSchema(), same DDL, with the character set and collation of
-- taxon.taxon_name so that names compare without a collation clash). This file is the record.
--
-- Check:     select list_type, restored is null waiting, count(*) from taxon_set_parked group by 1, 2;
-- Undo one restore (DAY = the date it ran, e.g. '2026-10-20'):
--            delete gt from geolocale_taxon gt join taxon_set_parked p
--              on p.list_type = 'geolocale' and p.list_key = gt.geolocale_id and p.taxon_name = gt.taxon_name
--              where date(p.restored) = DAY;
--            delete pt from proj_taxon pt join taxon_set_parked p
--              on p.list_type = 'project' and p.list_key = pt.project_name and p.taxon_name = pt.taxon_name
--              where date(p.restored) = DAY;
--            update taxon_set_parked set restored = null where date(restored) = DAY;
-- Remove the table (deploy the previous code first): DROP TABLE taxon_set_parked;

CREATE TABLE IF NOT EXISTS taxon_set_parked (
  list_type varchar(10) NOT NULL,
  list_key varchar(128) NOT NULL,
  taxon_name varchar(128) NOT NULL,
  source varchar(30) DEFAULT NULL,
  created timestamp NULL DEFAULT NULL,
  parked timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  restored timestamp NULL DEFAULT NULL,
  PRIMARY KEY (list_type, list_key, taxon_name),
  KEY taxon_name (taxon_name)
) ENGINE=InnoDB;  -- the application adds DEFAULT CHARSET / COLLATE of taxon.taxon_name
