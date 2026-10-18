-- Image Uploader: files that were not saved, and why (Brian, 2026-10-18).
--
--   image_upload_refused   one row per file name per image upload (image_upload.id):
--                          files the server refused (bad name, unknown specimen) and files
--                          the uploader page refused or could not send (selected twice, too
--                          large, empty, failed after retries). Shown under "Not saved" in
--                          imageUploadReport.do and counted in the upload-complete email.
--                          source: "server" or "browser" (who refused it).
--
-- Nothing to run by hand: the application creates the table at startup when it is missing
-- (ImageUploadRefusedDb.ensureSchema(), same DDL). This file is the record, and can be run
-- first on staging, then production, if preferred. Safe to run twice.
--
-- Check:     show create table image_upload_refused;
-- Rollback:  deploy the previous code first, then
--            DROP TABLE image_upload_refused;
--            (Only the "Not saved" lists are lost; no other table refers to it.)

CREATE TABLE IF NOT EXISTS image_upload_refused (
  id int NOT NULL AUTO_INCREMENT,
  image_upload_id int NOT NULL,
  filename varchar(255) NOT NULL,
  reason varchar(300) NOT NULL,
  source varchar(10) NOT NULL,
  created datetime NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY upload_file (image_upload_id, filename)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
