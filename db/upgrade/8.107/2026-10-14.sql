-- Password hashing (PasswordHash.java): login.password now holds
-- "pbkdf2_sha256$iterations$salt$hash" (about 90 characters) instead of the
-- plain-text password. Widen the column before deploying that code.
-- Existing plain-text passwords are converted by the application at startup.
--
-- Check first:   show columns from login like 'password';
-- Rollback:      not needed; a wider column is harmless to the old code.

ALTER TABLE login MODIFY password varchar(255) NOT NULL DEFAULT '';
