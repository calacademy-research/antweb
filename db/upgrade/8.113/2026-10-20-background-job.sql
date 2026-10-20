-- Uploads and admin data jobs run in the background (Brian, 2026-10-20): the upload page answers at
-- once and shows the upload's progress on a status page (jobStatus.do), instead of keeping the
-- request open until Cloudflare cuts it after 100 seconds ("524 timeout").
--
--   background_job   one row per job started from a page (org.calacademy.antweb.util.BackgroundJobs):
--     kind             'upload' (curator specimen or TaxonWorks upload) or 'data' (admin data job:
--                      utilData.do, "Import AntCat names now", a nightly set started by hand)
--     action           upload.do / utilData.do sub-action (e.g. specimenUpload, placeImageCounts)
--     title            what the status page shows, e.g. "Specimen upload for <group> (<file>)"
--     login_id, login_name   who started it; group_id = the group uploaded to (0 for data jobs)
--     status           queued, running, finished, failed, interrupted (the server restarted
--                      before the job ended; set at the next start-up)
--     step             what the job is doing or waiting for
--     result           the outcome text; result_link / result_link_text: the upload report
--     created, started, finished
--
-- Nothing to run by hand: the application creates the table when it is missing
-- (BackgroundJobDb.ensureSchema(), same DDL). This file is the record.
--
-- Check:   select id, kind, action, status, login_name, created, finished, step from background_job order by id desc limit 20;
-- Undo (deploy the previous code first; uploads then run in the request again): DROP TABLE background_job;

CREATE TABLE IF NOT EXISTS background_job (
  id int NOT NULL AUTO_INCREMENT,
  kind varchar(16) NOT NULL,
  action varchar(64) NOT NULL,
  title varchar(255) NULL,
  login_id int NOT NULL DEFAULT 0,
  login_name varchar(128) NULL,
  group_id int NOT NULL DEFAULT 0,
  status varchar(16) NOT NULL,
  step varchar(500) NULL,
  result text NULL,
  result_link varchar(500) NULL,
  result_link_text varchar(255) NULL,
  created datetime NOT NULL,
  started datetime NULL,
  finished datetime NULL,
  PRIMARY KEY (id),
  KEY by_status (status),
  KEY by_group (group_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
