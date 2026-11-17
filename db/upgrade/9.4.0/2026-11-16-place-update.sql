-- Places from coordinates, step 1 (decided by Brian Fisher 2026-11-16 18:43; Claude Doc "Places from coordinates:
-- proposal"; claude/place-authority-design.md step 3). The daily place step (tools/place/place_daily.py, host cron
-- after the daily update) writes what it would change into place_update; for one week only that (mode report), then
-- it applies (mode apply, a later pull request).
--
--   place_update           one row per specimen and field the step proposes to change, per run
--     run_date             the day of the run (the step keeps the last 60 runs)
--     mode                 report: proposed only; apply: written to specimen (with applied = 1)
--     code                 specimen.code
--     upload_group         the upload group's name (ant_group.name), for the report by collection
--     field                country or adm1
--     stored_value         what the specimen has (blank when nothing; not `stored`, a reserved word in MySQL)
--     proposed             AntWeb's valid name the coordinates give
--     basis                why, in plain words (place_corrections.py's basis)
--     flag                 "within the stated error of <state>" when another state is that near
--     note                 place_corrections.py's note (state to add; province of Madagascar; coast; islet)
--     applied              1 once written to specimen
--
--   specimen.adm1_verbatim   the state the curator uploaded, kept when the coordinates give another (Decision 1 = A:
--                            the derived state goes into specimen.adm1, which every page reads). Empty until the
--                            apply mode; the upload fills it in step 2.
--
-- Safe to run twice. Rollback: DROP TABLE place_update; ALTER TABLE specimen DROP COLUMN adm1_verbatim (both empty or
-- reports only until the apply mode).

CREATE TABLE IF NOT EXISTS place_update (
  id bigint NOT NULL AUTO_INCREMENT,
  run_date date NOT NULL,
  mode enum('report','apply') NOT NULL DEFAULT 'report',
  code varchar(128) NOT NULL,
  upload_group varchar(100) NULL,
  field varchar(16) NOT NULL,
  stored_value varchar(128) NULL,
  proposed varchar(128) NOT NULL,
  basis varchar(255) NULL,
  flag varchar(255) NULL,
  note varchar(255) NULL,
  applied tinyint NOT NULL DEFAULT 0,
  created timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY place_update_run (run_date, upload_group),
  KEY place_update_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @has := (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'specimen' AND column_name = 'adm1_verbatim');
SET @sql := IF(@has = 0, 'ALTER TABLE specimen ADD COLUMN adm1_verbatim varchar(128) NULL, ALGORITHM=INSTANT', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
