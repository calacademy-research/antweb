-- "Keep me logged in" (RememberMeMgr, RememberTokenDb).
-- The application creates this itself at startup when missing
-- (LoginMgr -> RememberTokenDb.ensureSchema()); this file is for reference
-- or to apply by hand.
--
--   remember_token   one row per remembered browser. Cookie AW_REMEMBER = selector:validator;
--                    selector stored as is, validator only as SHA-256 hex (token_hash).
--                    prev_hash/rotated: the hash before the last rotation, accepted for 60 s.
--                    session_version: login.session_version when made; a password change,
--                    reset, must_reset or "end sessions" makes the token invalid.
--
-- Check first:   show tables like 'remember_token';
-- Rollback:      DROP TABLE remember_token;
--                (harmless to leave in place: older code ignores it)

CREATE TABLE IF NOT EXISTS remember_token (
  id int NOT NULL AUTO_INCREMENT,
  login_id int NOT NULL,
  selector char(16) NOT NULL,
  token_hash char(64) NOT NULL,
  prev_hash char(64) NULL DEFAULT NULL,
  rotated datetime NULL DEFAULT NULL,
  created datetime NOT NULL,
  expires datetime NOT NULL,
  last_used datetime NULL DEFAULT NULL,
  user_agent varchar(255) DEFAULT NULL,
  session_version int NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY selector (selector),
  KEY login_id (login_id),
  KEY expires (expires)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
