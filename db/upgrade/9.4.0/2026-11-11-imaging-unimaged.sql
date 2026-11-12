-- The unimaged list of the imaging progress page (imagingProgress.do?format=csv and its genus view), stored with the
-- counts (review of PR #341, 2026-11-11). One row per valid living ant species or subspecies with no photo of the ant,
-- in the order of the list (names with CASC specimens first, then most specimens, then name). ImagingProgressDb.fill
-- replaces the rows in the same transaction as the rows of imaging_progress, so the page's "Still to image" (total
-- minus imaged of the row all/living) and the rows of the CSV are always of the same moment. Until then the list was
-- read live at the first request after a fill and kept in memory only: after a restart or a deploy it was read again
-- with whatever had changed since the counts (staging 2026-11-10: page "Still to image: 2,773", CSV 2,780 names).
--
--   imaging_unimaged       key (pos)
--     pos                  the place in the list, from 1
--     taxon_name           the name (taxon.taxon_name)
--     subfamily, genus, species, taxarank   of the taxon row (taxarank: species or subspecies)
--     specimens            specimen records filed under the name, anywhere in AntWeb (red-flagged ones too)
--     casc_specimens       of them, at museum CASC
--     museums              the museum codes of its specimens, in alphabetical order, separated by a blank
--     computed             when the row was written (with the counts)
--
-- The application creates the table when it is missing (ImagingProgressDb.ensureSchema, at start-up, in the character
-- set and collation of taxon.taxon_name); this file records it and can be run by hand (safe to run twice). A server
-- whose counts were stored before the table existed shows "not counted yet" on the page (and 503 for the CSV) until
-- the next fill: the daily update, or utilData.do?action=imagingProgress.

CREATE TABLE IF NOT EXISTS imaging_unimaged (
  pos int NOT NULL,
  taxon_name varchar(128) NOT NULL,
  subfamily varchar(128) NULL,
  genus varchar(128) NULL,
  species varchar(128) NULL,
  taxarank varchar(32) NULL,
  specimens int NOT NULL DEFAULT 0,
  casc_specimens int NOT NULL DEFAULT 0,
  museums text NULL,
  computed datetime NULL,
  PRIMARY KEY (pos)
) ENGINE=InnoDB;

-- Undo: with the code rolled back the table is not read (the earlier code reads the list live); it can stay or go.
-- With the code in place the page then says the imaging progress has not been counted, until the next fill creates
-- and fills the table again.
-- DROP TABLE IF EXISTS imaging_unimaged;
