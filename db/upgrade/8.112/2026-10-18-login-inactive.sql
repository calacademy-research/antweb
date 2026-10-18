-- Inactive accounts (Brian, 2026-10-18): regular accounts not used for three years.
--
--   login.inactive         1 = cannot log in with the password; logging in or "Forgot your
--                          password?" emails a reactivation link (a password reset link that
--                          also sets inactive back to 0)
--   login.inactive_since   when the account was made inactive (NULL when active)
--
-- Nothing to run by hand: the application adds the columns at startup when they are missing
-- (InactiveAccountDb.ensureSchema(), same DDL). This file is the record. Run it only if the
-- columns are missing (ALTER ... ADD COLUMN fails if they exist).
--
-- Accounts are made inactive only by an administrator on /inactiveAccounts.do (preview first,
-- then apply), which backs up the affected login rows to login_inactive_backup_YYYYMMDD.
--
-- Check:     select inactive, count(*) from login group by inactive;
-- Undo one run (BACKUP = the table the run reports):
--            update login l join BACKUP b on b.id = l.id
--              set l.inactive = 0, l.inactive_since = null, l.created = l.created;
-- Remove the columns (deploy the previous code first):
--            ALTER TABLE login DROP COLUMN inactive, DROP COLUMN inactive_since;

ALTER TABLE login ADD COLUMN inactive tinyint NOT NULL DEFAULT 0;
ALTER TABLE login ADD COLUMN inactive_since datetime NULL DEFAULT NULL;
