-- Stored licence texts to CC BY 4.0 (2026-10-29).
--
-- Brian Fisher, 2026-10-29: images and data on AntWeb are licensed CC BY 4.0 (Creative Commons Attribution 4.0
-- International), everywhere and for all CAS images; the stored texts of earlier uploads are updated too.
-- Before: every image_upload row recorded "Attribution-ShareAlike (BY-SA) Creative Commons License and GNU Free
-- Documentation License (GFDL)"; the old license table (id, license; image.license = 1) held an older text.
--
-- NOT RUN BY HAND: the application runs these statements itself at every start (LicenseUpdateDb.run, called from
-- LoginMgr with the other start-up schema steps). This file is the record. Safe to run twice: the backups are
-- CREATE TABLE IF NOT EXISTS ... SELECT (MySQL 8 copies nothing when the table exists), the UPDATEs only touch rows
-- whose text is not yet the new one. The text is org.calacademy.antweb.util.License.LINE.
--
-- Rollback (restores the texts as they were before the first run):
--   UPDATE image_upload u JOIN image_upload_license_bak_20261029 b ON b.id = u.id SET u.license = b.license;
--   UPDATE license l JOIN license_bak_20261029 b ON b.id = l.id SET l.license = b.license;
-- then, once no longer needed: DROP TABLE image_upload_license_bak_20261029, license_bak_20261029;

CREATE TABLE IF NOT EXISTS image_upload_license_bak_20261029 AS
  SELECT id, license FROM image_upload
  WHERE license IS NOT NULL
    AND license <> 'CC BY 4.0 (Creative Commons Attribution 4.0 International), https://creativecommons.org/licenses/by/4.0/';

CREATE TABLE IF NOT EXISTS license_bak_20261029 AS SELECT * FROM license;

UPDATE image_upload
  SET license = 'CC BY 4.0 (Creative Commons Attribution 4.0 International), https://creativecommons.org/licenses/by/4.0/'
  WHERE license IS NOT NULL
    AND license <> 'CC BY 4.0 (Creative Commons Attribution 4.0 International), https://creativecommons.org/licenses/by/4.0/';

UPDATE license
  SET license = 'CC BY 4.0 (Creative Commons Attribution 4.0 International), https://creativecommons.org/licenses/by/4.0/'
  WHERE license IS NULL
    OR license <> 'CC BY 4.0 (Creative Commons Attribution 4.0 International), https://creativecommons.org/licenses/by/4.0/';
