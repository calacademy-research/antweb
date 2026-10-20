-- Nightly name update (Brian, 2026-10-18): existing specimens follow AntCat name changes.
--
--   specimen_name_update   one row per specimen that the nightly name update moved to another name
--                          (mode 'changed') or would move (mode 'would_change': report mode, or the
--                          "Preview now" button of the Name updates page, or apply mode above
--                          maxChanges). run_date = start of that run; uploaded_name = the name in the
--                          curator's file; old_name / new_name = specimen.taxon_name before / after;
--                          antcat_status = AntCat status of old_name at that time (e.g. synonym);
--                          reason = why, from AntCat's taxonomic history at the time of the move, e.g.
--                          "synonym: junior synonym of Strumigenys pilinasis (Booher 2019)" or "uploaded as
--                          Crematogaster hespera; valid again (was junior synonym of Crematogaster laeviuscula,
--                          Morgan & Mackay 2017; status as species: Ward & Blaimer 2022)" (added 2026-10-20);
--                          antcat_id = AntCat id of the taxon whose history gave the reason
--                          (https://www.antcat.org/catalog/<antcat_id>).
--                          Each run first deletes the previous would_change rows; changed rows are kept
--                          (history, and the record for an undo).
--
-- Nothing to run by hand: the application creates the table when it is missing
-- (SpecimenNameUpdateDb.ensureSchema(), same DDL, with the character set and collation of
-- taxon.taxon_name). This file is the record.
--
-- Check:   select run_date, mode, count(*) from specimen_name_update group by 1, 2 order by 1 desc limit 10;
-- Undo one night (RUN = its run_date, e.g. '2026-10-21 05:31:07'); back up first:
--          create table specimen_name_update_undo_20261021 as
--            select s.* from specimen s join specimen_name_update u on u.code = s.code and u.access_group = s.access_group
--            where u.run_date = RUN and u.mode = 'changed';
--          update specimen s join specimen_name_update u on u.code = s.code and u.access_group = s.access_group
--              join taxon t on t.taxon_name = u.old_name
--            set s.taxon_name = u.old_name,
--                s.original_taxon_name = if(u.uploaded_name = u.old_name, null, u.uploaded_name),
--                s.family = t.family, s.subfamily = t.subfamily, s.tribe = t.tribe, s.genus = t.genus,
--                s.subgenus = t.subgenus, s.species = t.species, s.subspecies = t.subspecies
--            where u.run_date = RUN and u.mode = 'changed';
--          Turn the step off first (mode=off), or the next night moves them again. The counts
--          follow at the next nightly run.
-- Remove the table (deploy the previous code first): DROP TABLE specimen_name_update;
--
-- 2026-10-20, reason and antcat_id: a table made by the first version of the step gets the two
-- columns from the application too (SpecimenNameUpdateDb.ensureSchema(), SpecimenNameUpdateDb.ADD_REASON):
--          ALTER TABLE specimen_name_update ADD COLUMN reason varchar(500) NULL, ADD COLUMN antcat_id int NULL, ALGORITHM=INSTANT;
-- Undo (deploy the previous code first):
--          ALTER TABLE specimen_name_update DROP COLUMN reason, DROP COLUMN antcat_id, ALGORITHM=INSTANT;

CREATE TABLE IF NOT EXISTS specimen_name_update (
  id int NOT NULL AUTO_INCREMENT,
  run_date datetime NOT NULL,
  mode varchar(12) NOT NULL,
  code varchar(128) NOT NULL,
  access_group int NOT NULL,
  uploaded_name varchar(128) NOT NULL,
  old_name varchar(128) NOT NULL,
  new_name varchar(128) NOT NULL,
  antcat_status varchar(40) NULL,
  reason varchar(500) NULL,
  antcat_id int NULL,
  PRIMARY KEY (id),
  KEY by_group_date (access_group, run_date),
  KEY by_date (run_date),
  KEY by_code (code)
) ENGINE=InnoDB;  -- the application adds DEFAULT CHARSET / COLLATE of taxon.taxon_name
