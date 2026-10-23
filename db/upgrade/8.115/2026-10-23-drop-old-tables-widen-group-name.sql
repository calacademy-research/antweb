-- Drop the old unused tables, widen upload.group_name (2026-10-23).
--
-- Brian Fisher approved both on 2026-10-22:
--   1. Drop the 14 old tables and 1 view that production has and nothing reads (docs/data-model.md section 13, "Old
--      tables on production only", and section 12 for the view): no SQL in src/, web/, api/, WEB-INF/, bin/ or tools/
--      reads them (searched again 2026-10-22; "description" and "image_count" occur in the code only as column names,
--      "synonymy" and "homepage" only in prose and paths). The old hand-run script db/delOrphanTaxon.sql deleted from
--      three of them; those statements are removed from it in the same change.
--   2. Widen upload.group_name from varchar(64) to varchar(100), the width of ant_group.name: an 81-character group
--      name made an upload row insert fail on 2026-10-21 (PR #232 then cut the name to 64 in the code;
--      SpecimenUploadDb now cuts to the column's width, read on every upload: 64 before this file, 100 after).
--      NOT NULL and the column's character set and collation (utf8mb3, utf8mb3_general_ci:
--      db/upgrade/8.56/2021-06-21.sql converted the table) are kept; the runbook's
--      archive step prints them first. The table is small (one row per upload); MySQL copies it for this change
--      (a utf8mb3 varchar over 85 characters needs a two-byte length), which takes a second.
--
-- RUN BY HAND, AFTER THE ARCHIVE STEP: docs/runbook.md, "Drop the old unused tables (2026-10-23)". Staging first, then
-- production. Step a there dumps exactly these 14 tables and the view's definition to
-- /mnt/backup/archive/old_tables_20261023.sql.gz and checks the file; only then is this file run (step b). Nothing in
-- the deploy runs db/upgrade files. Safe to run twice: DROP ... IF EXISTS, and the MODIFY gives the same definition.
--
-- The repository's record of production (doc/schema/live-schema-2026-10-23.tsv, the 2026-10-22 snapshot without these
-- objects and with group_name varchar(100)) describes the database after this file. The daily structure check
-- (SchemaCheck, on the count check page) lists the difference while this file has run and the deploy has not, or the
-- other way round.
--
-- Rollback (docs/runbook.md, step d): load the archive (zcat ... | mysql ant), which recreates the tables with their
-- rows and the view, and, only if no group name longer than 64 characters was stored meanwhile, ALTER the column back:
--   ALTER TABLE upload MODIFY group_name varchar(64) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL;

ALTER TABLE upload MODIFY group_name varchar(100) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci NOT NULL;

DROP TABLE IF EXISTS afro_types_tmp;
DROP TABLE IF EXISTS artist_copy;
DROP TABLE IF EXISTS description;
DROP TABLE IF EXISTS favorite_images;
DROP TABLE IF EXISTS groups_project;
DROP TABLE IF EXISTS homepage;
DROP TABLE IF EXISTS image_backup;
DROP TABLE IF EXISTS image_count;
DROP TABLE IF EXISTS image_count_materialized;
DROP TABLE IF EXISTS junkspec;
-- exact case: production's MySQL tells table names apart by case (lower_case_table_names = 0)
DROP TABLE IF EXISTS proj_taxonT;
DROP TABLE IF EXISTS similar_species;
DROP TABLE IF EXISTS synonymy;
DROP TABLE IF EXISTS temp_image_artist;

-- Last, so the tables are gone even if this fails: dropping a view whose DEFINER is an account with SYSTEM_USER (such
-- as root) needs SYSTEM_USER (tested on MySQL 8.0.46). The archive step prints the view's definer and the login's grants.
DROP VIEW IF EXISTS darwin_core_2_test;
