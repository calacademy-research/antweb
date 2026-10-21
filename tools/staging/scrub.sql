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
