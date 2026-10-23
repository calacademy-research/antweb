-- Schema catch-up (2026-10-22): makes a database built from the repository's SQL match production.
--
-- Brian Fisher, 2026-10-22: "AntWeb needs to be a source of trusted data ... once we have it right the right
-- stays right." On 2026-10-22 the production schema (table, column, type of every table and view) was exported
-- to doc/schema/live-schema-2026-10-22.tsv (how: docs/data-model.md, "The live snapshot"). It differed from what
-- the db/upgrade files and the code describe: tables made before the upgrade scripts (about 2010) that no file
-- creates, columns added or widened by hand on the server, and changes in old upgrade files that were never run.
-- This file records production as it is. tools/datamodel/list_tables.py reads it (also its CALL lines), and CI
-- (--check) fails when the repository's SQL and the snapshot differ in a way not explained here or by the
-- allow-list in that script (dated backups and the old unused tables of docs/data-model.md section 13).
--
-- SAFE TO RUN ON PRODUCTION: IT CHANGES NOTHING THERE. Every statement is guarded and production already is the
-- target: CREATE TABLE IF NOT EXISTS (the tables exist); a column is added only when it is missing, retyped only
-- when its type is not production's type, dropped only when it exists (production has none of the four); DROP
-- TABLE IF EXISTS only for tables production does not have; a view is dropped only when a view of that name
-- exists (on production image_count is a table and tapir_specimen does not exist). Nothing is dropped from
-- production (the helper procedures it creates are removed at its end). Running it there is optional (to confirm: it prints nothing and the snapshot query gives the same
-- result before and after). Staging is a copy of production, so the same holds there.
--
-- What it does on a database built from the repository's SQL (a new or test database):
--   1. creates the helper procedures (removed again at the end; needs the CREATE ROUTINE privilege; if the app's
--      MySQL user lacks it, run the file as MySQL root);
--   2. creates the tables production has that no file creates, with production's columns and types (keys,
--      indexes, NOT NULL and defaults are not in the snapshot: a full `mysqldump --no-data` of production would
--      give them; docs/data-model.md section 14);
--   3. adds the columns production has;
--   4. gives columns production's type (production's is wider, or MySQL converted it: text to mediumtext by the
--      utf8 conversion of 2021, LONG (mediumtext) widened to longtext by hand, float to double);
--   5. drops (when they hold no values) four columns that old upgrade files add but production does not have and no code reads:
--      bioregion.biogeographicregion (db/upgrade/4.134), geolocale_taxon.modified (7.9),
--      project.endemic_species_count (6.3; ProjectDb reads it only in commented-out code),
--      specimen.is_endemic (6.74; endemic flags are on the taxon lists: geolocale_taxon, bioregion_taxon, proj_taxon);
--   6. drops (when empty) the tables, and the views, the repository describes that production no longer has (old backups from
--      2015 to 2021, the unused command table, the TAPIR view tapir_specimen, and the 2012 view image_count,
--      which production replaced by a table of the same name).
--
-- Not changed here, a decision for Brian (needs an ALTER on production): ant_group.name is varchar(100) and
-- upload.group_name varchar(64); an 81-character group name made an upload row insert fail (PR #232 handles that
-- in code). Widening upload.group_name to varchar(100) would match them. (Done: 2026-10-23-drop-old-tables-widen-group-name.sql.)
--
-- Limits on any other database: "matches production" means names and types only (no keys, indexes, NOT NULL or
-- defaults; see 2). A retype acts on base tables only, never shortens a varchar, char, text or integer type (nor turns
-- text into varchar, or unsigned into signed), and keeps NULL / NOT NULL, the default, ON UPDATE,
-- AUTO_INCREMENT, the comment and the column's character set and collation; datetime to timestamp
-- (geolocale_taxon.created) fails under strict mode, rather than changing data, if a value is outside 1970 to 2038.
-- The columns of step 5 and the old tables of step 6 are dropped only when they hold no values / no rows: a database
-- where one still has data keeps it and the file says so (back it up and drop it by hand, data-model.md section 13).
-- Tested on MySQL 8.0.46 (production runs 8.4: REGEXP_SUBSTR, FIELD and information_schema used here are the same).
--
-- How to run: by hand, with the mysql command-line client (DELIMITER is a client command), as a user with CREATE
-- ROUTINE; nothing in the deploy runs db/upgrade files. If it stops part-way (for example the datetime to timestamp
-- retype above), fix the cause and run it again: every step is safe to repeat. The tables of step 2 have no keys:
-- a database built from them shows the structure but is not fit to run the site until keys are added (a
-- `mysqldump --no-data` of production, docs/data-model.md section 14, would supply them; it would need ALTERs where
-- 8.115 already ran, as CREATE TABLE IF NOT EXISTS then does nothing).
--
-- Rollback: none needed on production (nothing changes). On a test database: rebuild it.

DELIMITER //
DROP PROCEDURE IF EXISTS schema_add_column //
CREATE PROCEDURE schema_add_column(IN t VARCHAR(64), IN c VARCHAR(64), IN def VARCHAR(255))
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.tables
              WHERE table_schema = DATABASE() AND table_name = t AND table_type = 'BASE TABLE')
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns
                      WHERE table_schema = DATABASE() AND table_name = t AND column_name = c) THEN
    SET @schema_sql = CONCAT('ALTER TABLE `', t, '` ADD COLUMN `', c, '` ', def);
    PREPARE schema_stmt FROM @schema_sql;
    EXECUTE schema_stmt;
    DEALLOCATE PREPARE schema_stmt;
  END IF;
END //
DROP PROCEDURE IF EXISTS schema_modify_column //
CREATE PROCEDURE schema_modify_column(IN t VARCHAR(64), IN c VARCHAR(64), IN ty VARCHAR(255))
BEGIN
  -- Only when the column exists with another type. Keeps its NULL / NOT NULL, default, ON UPDATE and AUTO_INCREMENT.
  DECLARE cur_type VARCHAR(255);
  DECLARE cur_null VARCHAR(3);
  DECLARE cur_default TEXT;
  DECLARE cur_extra VARCHAR(255);
  DECLARE cur_comment TEXT;
  DECLARE cur_charset VARCHAR(64);
  DECLARE cur_collation VARCHAR(64);
  DECLARE found INT DEFAULT 0;
  SELECT COUNT(*) INTO found FROM information_schema.columns c2 JOIN information_schema.tables t2 USING (table_schema, table_name)
   WHERE c2.table_schema = DATABASE() AND c2.table_name = t AND c2.column_name = c AND t2.table_type = 'BASE TABLE';
  IF found = 1 THEN
    SELECT column_type, is_nullable, column_default, extra, column_comment, character_set_name, collation_name
      INTO cur_type, cur_null, cur_default, cur_extra, cur_comment, cur_charset, cur_collation
      FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = t AND column_name = c;
    IF LOWER(cur_type) <> LOWER(ty)
       -- never shorten a varchar or char (that could cut values)
       AND NOT (cur_type REGEXP '^(var)?char\\([0-9]+\\)$' AND ty REGEXP '^(var)?char\\([0-9]+\\)$'
                AND CAST(REGEXP_SUBSTR(cur_type, '[0-9]+') AS UNSIGNED) > CAST(REGEXP_SUBSTR(ty, '[0-9]+') AS UNSIGNED))
       -- nor a text type to a varchar or char
       AND NOT (LOWER(cur_type) REGEXP 'text$' AND LOWER(ty) REGEXP '^(var)?char')
       -- nor an unsigned integer to a signed one, nor a smaller text or integer type (longtext to mediumtext, bigint to int)
       AND NOT (LOWER(cur_type) LIKE '%unsigned%' AND LOWER(ty) NOT LIKE '%unsigned%')
       AND NOT (FIELD(LOWER(cur_type), 'tinytext', 'text', 'mediumtext', 'longtext') > FIELD(LOWER(ty), 'tinytext', 'text', 'mediumtext', 'longtext')
                AND FIELD(LOWER(ty), 'tinytext', 'text', 'mediumtext', 'longtext') > 0)
       AND NOT (FIELD(SUBSTRING_INDEX(SUBSTRING_INDEX(LOWER(cur_type), ' ', 1), '(', 1), 'tinyint', 'smallint', 'mediumint', 'int', 'bigint')
                  > FIELD(SUBSTRING_INDEX(LOWER(ty), '(', 1), 'tinyint', 'smallint', 'mediumint', 'int', 'bigint')
                AND FIELD(SUBSTRING_INDEX(LOWER(ty), '(', 1), 'tinyint', 'smallint', 'mediumint', 'int', 'bigint') > 0) THEN
      SET @schema_sql = CONCAT('ALTER TABLE `', t, '` MODIFY COLUMN `', c, '` ', ty,
        -- the column's own character set and collation (text types only; a numeric target has none)
        IF(cur_charset IS NOT NULL AND ty REGEXP '(char|text|enum|set)', CONCAT(' CHARACTER SET ', cur_charset, ' COLLATE ', cur_collation), ''),
        IF(cur_null = 'NO', ' NOT NULL', ' NULL'),
        CASE WHEN cur_default IS NULL THEN ''
             WHEN cur_extra LIKE '%DEFAULT_GENERATED%' AND UPPER(cur_default) REGEXP '^(CURRENT_TIMESTAMP|NOW)(\\([0-9]*\\))?$'
               THEN CONCAT(' DEFAULT ', cur_default)
             WHEN cur_extra LIKE '%DEFAULT_GENERATED%' THEN CONCAT(' DEFAULT (', cur_default, ')')
             ELSE CONCAT(' DEFAULT ', QUOTE(cur_default)) END,
        IF(cur_extra LIKE '%on update CURRENT_TIMESTAMP%', ' ON UPDATE CURRENT_TIMESTAMP', ''),
        IF(cur_extra LIKE '%auto_increment%', ' AUTO_INCREMENT', ''),
        IF(cur_comment IS NOT NULL AND cur_comment <> '', CONCAT(' COMMENT ', QUOTE(cur_comment)), ''));
      PREPARE schema_stmt FROM @schema_sql;
      EXECUTE schema_stmt;
      DEALLOCATE PREPARE schema_stmt;
    END IF;
  END IF;
END //
DROP PROCEDURE IF EXISTS schema_drop_column //
CREATE PROCEDURE schema_drop_column(IN t VARCHAR(64), IN c VARCHAR(64))
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns c2 JOIN information_schema.tables t2 USING (table_schema, table_name)
              WHERE c2.table_schema = DATABASE() AND c2.table_name = t AND c2.column_name = c AND t2.table_type = 'BASE TABLE') THEN
    -- only when no row has a value in it; otherwise the column is kept and named in the output
    SET @schema_sql = CONCAT('SELECT COUNT(*) INTO @schema_rows FROM `', t, '` WHERE `', c, '` IS NOT NULL');
    PREPARE schema_stmt FROM @schema_sql;
    EXECUTE schema_stmt;
    DEALLOCATE PREPARE schema_stmt;
    IF @schema_rows = 0 THEN
      SET @schema_sql = CONCAT('ALTER TABLE `', t, '` DROP COLUMN `', c, '`');
      PREPARE schema_stmt FROM @schema_sql;
      EXECUTE schema_stmt;
      DEALLOCATE PREPARE schema_stmt;
    ELSE
      SELECT CONCAT('kept ', t, '.', c, ': ', @schema_rows, ' rows have a value (back up, then drop it by hand)') AS schema_catch_up;
    END IF;
  END IF;
END //
DROP PROCEDURE IF EXISTS schema_drop_empty_table //
CREATE PROCEDURE schema_drop_empty_table(IN t VARCHAR(64))
BEGIN
  -- Only a base table with no rows; a table that still holds data is kept and named in the output.
  IF EXISTS (SELECT 1 FROM information_schema.tables
              WHERE table_schema = DATABASE() AND table_name = t AND table_type = 'BASE TABLE') THEN
    SET @schema_sql = CONCAT('SELECT COUNT(*) INTO @schema_rows FROM `', t, '`');
    PREPARE schema_stmt FROM @schema_sql;
    EXECUTE schema_stmt;
    DEALLOCATE PREPARE schema_stmt;
    IF @schema_rows = 0 THEN
      SET @schema_sql = CONCAT('DROP TABLE `', t, '`');
      PREPARE schema_stmt FROM @schema_sql;
      EXECUTE schema_stmt;
      DEALLOCATE PREPARE schema_stmt;
    ELSE
      SELECT CONCAT('kept ', t, ': it has ', @schema_rows, ' rows (back it up, then drop it by hand)') AS schema_catch_up;
    END IF;
  END IF;
END //
DROP PROCEDURE IF EXISTS schema_drop_view //
CREATE PROCEDURE schema_drop_view(IN v VARCHAR(64))
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.views WHERE table_schema = DATABASE() AND table_name = v) THEN
    SET @schema_sql = CONCAT('DROP VIEW `', v, '`');
    PREPARE schema_stmt FROM @schema_sql;
    EXECUTE schema_stmt;
    DEALLOCATE PREPARE schema_stmt;
  END IF;
END //
DELIMITER ;

-- 2. Tables production has that no file in the repository creates (they predate db/upgrade, about 2010).
--    Columns and types as on production; keys, indexes, NOT NULL and defaults are not in the snapshot.
CREATE TABLE IF NOT EXISTS `adm1` (
  `name` varchar(128)
);
CREATE TABLE IF NOT EXISTS `ancillary` (
  `id` int,
  `title` varchar(80),
  `fileName` varchar(80),
  `directory` varchar(80),
  `contents` mediumtext,
  `last_changed` datetime,
  `project_name` varchar(32),
  `access_login` int
);
CREATE TABLE IF NOT EXISTS `ant_group` (
  `id` int,
  `name` varchar(100),
  `last_specimen_update` datetime,
  `last_specimen_upload` datetime,
  `admin_login_id` int,
  `abbrev` varchar(20),
  `upload_specimens` int,
  `first_specimen_upload` datetime,
  `upload_count` int
);
CREATE TABLE IF NOT EXISTS `artist` (
  `id` int,
  `name` text,
  `created` timestamp,
  `active` tinyint,
  `curator_id` int
);
CREATE TABLE IF NOT EXISTS `artist_group` (
  `artist_id` int,
  `group_id` int
);
CREATE TABLE IF NOT EXISTS `copyright` (
  `id` int,
  `copyright` text,
  `year` int
);
CREATE TABLE IF NOT EXISTS `country_bioregion` (
  `id` int,
  `country` varchar(40),
  `bioregion` varchar(40)
);
CREATE TABLE IF NOT EXISTS `group_image` (
  `group_id` int,
  `image_id` int
);
CREATE TABLE IF NOT EXISTS `image` (
  `id` int,
  `shot_type` varchar(8),
  `source_table` varchar(16),
  `image_of_id` varchar(128),
  `shot_number` int,
  `artist` int,
  `copyright` int,
  `license` int,
  `upload_date` datetime,
  `has_tiff` smallint,
  `wikimedia_upload_date` datetime,
  `modified` timestamp,
  `image_upload_id` int
);
CREATE TABLE IF NOT EXISTS `license` (
  `id` int,
  `license` text
);
CREATE TABLE IF NOT EXISTS `proj_taxon` (
  `project_name` varchar(32),
  `taxon_name` varchar(128),
  `created` timestamp,
  `subfamily_count` int,
  `genus_count` int,
  `species_count` int,
  `specimen_count` int,
  `image_count` int,
  `source` varchar(30),
  `rev` int,
  `antwiki_rev` int,
  `is_endemic` tinyint,
  `is_introduced` tinyint
);
CREATE TABLE IF NOT EXISTS `project` (
  `project_name` varchar(32),
  `project_title` varchar(80),
  `extent` varchar(40),
  `last_changed` datetime,
  `specimenImage1` varchar(100),
  `specimenImage2` varchar(100),
  `specimenImage3` varchar(100),
  `authorImage` varchar(100),
  `map` varchar(40),
  `authorbio` text,
  `root` varchar(40),
  `locality` varchar(80),
  `specimenImage1Link` varchar(160),
  `specimenImage2Link` varchar(160),
  `specimenImage3Link` varchar(160),
  `author` varchar(128),
  `created` timestamp,
  `species_list_mappable` tinyint,
  `coords` varchar(128),
  `is_live` tinyint,
  `source` varchar(40),
  `display_key` varchar(64),
  `subfamily_count` int,
  `genus_count` int,
  `species_count` int,
  `specimen_count` int,
  `image_count` int,
  `imaged_specimen_count` int,
  `taxon_subfamily_dist_json` varchar(5000),
  `specimen_subfamily_dist_json` varchar(5000),
  `chart_color` varchar(30),
  `contents` text,
  `country` varchar(64),
  `adm1` varchar(128),
  `scope` varchar(64),
  `valid_species_count` int
);
CREATE TABLE IF NOT EXISTS `taxon_country` (
  `taxon_name` varchar(128),
  `country` varchar(40),
  `project_name` varchar(32),
  `created` timestamp
);

-- 3. Columns production has that the repository's SQL does not add (description_edit, _hist and _homonym were made
--    in 2010 as copies of the pre-2010 table description, so their first columns were never written down; the
--    taxon counts were dropped by db/upgrade/4.150 but production still has them and the API reads them).
CALL schema_add_column('bioregion', 'specimen_local_count', 'int');
CALL schema_add_column('description_edit', 'taxon_name', 'varchar(128)');
CALL schema_add_column('description_edit', 'title', 'varchar(32)');
CALL schema_add_column('description_edit', 'content', 'longtext');
CALL schema_add_column('description_hist', 'taxon_name', 'varchar(128)');
CALL schema_add_column('description_hist', 'title', 'varchar(32)');
CALL schema_add_column('description_hist', 'content', 'longtext');
CALL schema_add_column('description_homonym', 'taxon_name', 'varchar(128)');
CALL schema_add_column('description_homonym', 'title', 'varchar(32)');
CALL schema_add_column('description_homonym', 'content', 'longtext');
CALL schema_add_column('geolocale', 'contents', 'text');
CALL schema_add_column('geolocale', 'specimenImage1', 'varchar(100)');
CALL schema_add_column('geolocale', 'specimenImage2', 'varchar(100)');
CALL schema_add_column('geolocale', 'specimenImage3', 'varchar(100)');
CALL schema_add_column('geolocale', 'specimenImage1Link', 'varchar(160)');
CALL schema_add_column('geolocale', 'specimenImage2Link', 'varchar(160)');
CALL schema_add_column('geolocale', 'specimenImage3Link', 'varchar(160)');
CALL schema_add_column('geolocale', 'author', 'varchar(128)');
CALL schema_add_column('geolocale', 'authorImage', 'varchar(100)');
CALL schema_add_column('geolocale', 'authorbio', 'text');
CALL schema_add_column('geolocale', 'species_list_mappable', 'tinyint');
CALL schema_add_column('geolocale', 'latitude', 'varchar(30)');
CALL schema_add_column('geolocale', 'longitude', 'varchar(30)');
CALL schema_add_column('geolocale', 'latitude_fixed', 'varchar(30)');
CALL schema_add_column('geolocale', 'longitude_fixed', 'varchar(30)');
CALL schema_add_column('geolocale', 'specimen_local_count', 'int');
CALL schema_add_column('image_uploaded', 'specimen_data_exists', 'tinyint');
CALL schema_add_column('login', 'upload_as', 'varchar(128)');
CALL schema_add_column('login_project', 'project_name', 'varchar(32)');
CALL schema_add_column('museum', 'specimen_local_count', 'int');
CALL schema_add_column('proj_taxon_log_detail', 'project_name', 'varchar(32)');
CALL schema_add_column('proj_taxon_log_detail', 'taxon_name', 'varchar(128)');
CALL schema_add_column('specimen', 'is_male', 'tinyint');
CALL schema_add_column('specimen', 'is_worker', 'tinyint');
CALL schema_add_column('specimen', 'is_queen', 'tinyint');
CALL schema_add_column('taxon', 'subfamily_count', 'int');
CALL schema_add_column('taxon', 'genus_count', 'int');
CALL schema_add_column('taxon', 'species_count', 'int');
CALL schema_add_column('taxon', 'specimen_count', 'int');

-- 4. Column types: the type production has (wider, or as MySQL converted it).
CALL schema_modify_column('login_country', 'login_id', 'int');  -- repository: unknown (CREATE TABLE ... AS SELECT, db/upgrade/6.7)
CALL schema_modify_column('login_country', 'country', 'varchar(250)');  -- repository: unknown (as above)
CALL schema_modify_column('geolocale_taxon', 'created', 'timestamp');  -- repository: datetime
CALL schema_modify_column('image_upload', 'license', 'mediumtext');  -- repository: text
CALL schema_modify_column('object_map', 'google_map_function', 'longtext');  -- repository: long
CALL schema_modify_column('specimen', 'decimal_latitude', 'double');  -- repository: float
CALL schema_modify_column('specimen', 'decimal_longitude', 'double');  -- repository: float
CALL schema_modify_column('specimen', 'habitat', 'varchar(512)');  -- repository: varchar(256)
CALL schema_modify_column('specimen', 'microhabitat', 'varchar(500)');  -- repository: varchar(256)
CALL schema_modify_column('specimen', 'type_status', 'varchar(512)');  -- repository: varchar(128)
CALL schema_modify_column('team_member', 'img_file_name', 'mediumtext');  -- repository: text
CALL schema_modify_column('team_member', 'img_file_type', 'mediumtext');  -- repository: text
CALL schema_modify_column('team_member', 'img_file_size', 'mediumtext');  -- repository: text

-- 5. Columns the repository's SQL adds that production does not have and no code reads (see the top).
CALL schema_drop_column('bioregion', 'biogeographicregion');
CALL schema_drop_column('geolocale_taxon', 'modified');
CALL schema_drop_column('project', 'endemic_species_count');
CALL schema_drop_column('specimen', 'is_endemic');

-- 6. Tables and views the repository describes that production does not have (none is read by the code). A
--    table is dropped only when it is empty.
CALL schema_drop_empty_table('command');
CALL schema_drop_empty_table('description_edit_plazi20150601');
CALL schema_drop_empty_table('favorite_images_bak');
CALL schema_drop_empty_table('login_project_bak');
CALL schema_drop_empty_table('museumbak');
CALL schema_drop_empty_table('proj_taxon20170422');
CALL schema_drop_empty_table('project20170422');
CALL schema_drop_empty_table('similar_species_bak');
CALL schema_drop_empty_table('taxon_country_bak');
CALL schema_drop_empty_table('taxon_prop20170617');
CALL schema_drop_view('tapir_specimen');
CALL schema_drop_view('image_count');

DROP PROCEDURE IF EXISTS schema_add_column;
DROP PROCEDURE IF EXISTS schema_modify_column;
DROP PROCEDURE IF EXISTS schema_drop_column;
DROP PROCEDURE IF EXISTS schema_drop_empty_table;
DROP PROCEDURE IF EXISTS schema_drop_view;
