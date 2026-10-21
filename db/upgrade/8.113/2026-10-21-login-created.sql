-- login.created: the date the login was made. (2026-10-21)
--
-- The column was defined in 2011 as "timestamp DEFAULT CURRENT_TIMESTAMP ON UPDATE
-- CURRENT_TIMESTAMP" (db/upgrade/3.5/2011-02-11.sql), so every update of a login row that did not
-- also say "created = created" set created to the time of the update: a password reset, the
-- end of all sessions, an edit of the login. The code now says "created = created" in every
-- update of login (checked by tests/unit/test_small_fixes.py). This script removes the ON UPDATE
-- so that a future update that forgets it cannot overwrite the date either.
--
-- Optional, and independent of the code: the code works with or without it, so it can run before
-- or after the deploy. Run on staging first, then production. Dates already overwritten are not
-- restored (the original dates are not known).
--
-- Check before and after (the "created" line):
--   SHOW CREATE TABLE login\G
-- Before: `created` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
-- After:  `created` timestamp NULL DEFAULT CURRENT_TIMESTAMP
-- (NULL or NOT NULL stays as it was.) Running it twice changes nothing.
--
-- Rollback (use NOT NULL instead of NULL if the "before" line said NOT NULL):
--   ALTER TABLE login MODIFY created timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

SET @login_created_ddl = (
  SELECT CONCAT('ALTER TABLE login MODIFY created timestamp ', IF(IS_NULLABLE = 'YES', 'NULL', 'NOT NULL'),
                ' DEFAULT CURRENT_TIMESTAMP')
    FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'login' AND COLUMN_NAME = 'created'
     AND DATA_TYPE = 'timestamp' AND LOWER(COLUMN_DEFAULT) LIKE 'current_timestamp%'
     AND LOWER(EXTRA) LIKE '%on update%');
SET @login_created_ddl = COALESCE(@login_created_ddl, 'SELECT ''login.created has no ON UPDATE: nothing to do'' AS note');
PREPARE login_created_stmt FROM @login_created_ddl;
EXECUTE login_created_stmt;
DEALLOCATE PREPARE login_created_stmt;
