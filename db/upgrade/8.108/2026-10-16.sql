-- Password reset (PasswordResetMgr, PasswordResetDb).
-- The application creates these itself at startup when missing
-- (LoginMgr -> PasswordResetDb.ensureSchema()); this file is for reference
-- or to apply by hand.
--
--   password_reset          one row per emailed reset link; only the SHA-256
--                           (hex) of the token is stored
--   login.must_reset        1 = must set a new password before logging in
--   login.session_version   incremented to end all sessions of a login
--
-- Check first:   show tables like 'password_reset';
--                show columns from login like 'must_reset';
--                show columns from login like 'session_version';
-- Rollback:      DROP TABLE password_reset;
--                ALTER TABLE login DROP COLUMN must_reset, DROP COLUMN session_version;
--                (harmless to leave in place: older code ignores them)

CREATE TABLE IF NOT EXISTS password_reset (
  id int NOT NULL AUTO_INCREMENT,
  login_id int NOT NULL,
  token_hash char(64) NOT NULL,
  created datetime NOT NULL,
  expires datetime NOT NULL,
  used_at datetime NULL DEFAULT NULL,
  requested_ip varchar(64) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY token_hash (token_hash),
  KEY login_created (login_id, created)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- MySQL 8.4 has no "ADD COLUMN IF NOT EXISTS": run each only if the column is missing.
ALTER TABLE login ADD COLUMN must_reset tinyint NOT NULL DEFAULT 0;
ALTER TABLE login ADD COLUMN session_version int NOT NULL DEFAULT 0;
