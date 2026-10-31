-- STAGING ONLY. Run by tools/staging/refresh-from-prod.sh right after production's dump has
-- been loaded into staging's database `ant`, and before AntWeb starts again.
--
-- Production's dump holds real people's email addresses and password hashes. Staging is not
-- public, but it is a copy nobody needs these on. After this:
--   - every email address is login<id>@example.invalid (".invalid" can never be delivered), so a
--     staging page that sends mail (password reset, upload notice) can never reach a real person;
--   - every password is replaced by a random hash nobody knows (an empty password, an unaccepted
--     invitation, stays empty), so nobody can log in to staging with their production password;
--   - every session and "keep me logged in" token is ended, and every password reset link is gone.
-- The refresh script then puts back the staging test logins with their own saved hashes
-- (tools/staging/create-test-accounts.py --restore-from).
--
-- Tested in CI against MySQL 8.4: tests/unit/test_staging_refresh_db.py.

UPDATE login
   SET email = CONCAT('login', id, '@example.invalid')
 WHERE email IS NOT NULL AND email <> '';

-- Same format as PasswordHash.java (pbkdf2_sha256$iterations$salt$hash), with a random salt and
-- a random "hash" that no password produces. A value in this format is never compared as plain
-- text (PasswordHash.matches compares unparseable values as legacy plain text).
UPDATE login
   SET password = CONCAT('pbkdf2_sha256$600000$', TO_BASE64(RANDOM_BYTES(16)), '$', TO_BASE64(RANDOM_BYTES(32)))
 WHERE password IS NOT NULL AND password <> '';

UPDATE login SET session_version = session_version + 1;

DELETE FROM remember_token;
DELETE FROM password_reset;

-- Dated copies of the login table that clean-ups left in production (login_deleted_2026_10_16,
-- login_inactive_backup_20261018, ...) hold the same emails and password hashes: every column named
-- email or password in a table whose name starts with "login" (login itself is done above) is scrubbed
-- the same way. Columns of other tables (team_member.email: the public staff page) are left alone.
DROP PROCEDURE IF EXISTS antweb_scrub_login_copies;
DELIMITER //
CREATE PROCEDURE antweb_scrub_login_copies()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE t VARCHAR(64);
  DECLARE c VARCHAR(64);
  DECLARE cols CURSOR FOR
    SELECT col.table_name, col.column_name
      FROM information_schema.columns col
      JOIN information_schema.tables tab
        ON tab.table_schema = col.table_schema AND tab.table_name = col.table_name
     WHERE col.table_schema = DATABASE() AND tab.table_type = 'BASE TABLE'
       AND col.table_name LIKE 'login%' AND col.table_name <> 'login'
       AND col.column_name IN ('email', 'password');
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;
  OPEN cols;
  scrub: LOOP
    FETCH cols INTO t, c;
    IF done THEN LEAVE scrub; END IF;
    IF c = 'email' THEN
      SET @antweb_scrub = CONCAT('UPDATE `', t, '` SET email = CONCAT(''copy-'', UUID(), ''@example.invalid'') WHERE email IS NOT NULL AND email <> ''''');
    ELSE
      SET @antweb_scrub = CONCAT('UPDATE `', t, '` SET password = CONCAT(''pbkdf2_sha256$600000$'', TO_BASE64(RANDOM_BYTES(16)), ''$'', TO_BASE64(RANDOM_BYTES(32))) WHERE password IS NOT NULL AND password <> ''''');
    END IF;
    PREPARE scrub_stmt FROM @antweb_scrub;
    EXECUTE scrub_stmt;
    DEALLOCATE PREPARE scrub_stmt;
  END LOOP;
  CLOSE cols;
END //
DELIMITER ;
CALL antweb_scrub_login_copies();
DROP PROCEDURE antweb_scrub_login_copies;
