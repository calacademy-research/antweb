-- Read-only. The image rows of casent4045392 (6 images on 10-27 per the page snapshot; production showed 3 on 11-02).
SET SESSION TRANSACTION READ ONLY;
SELECT id, image_of_id, shot_type, shot_number, upload_date, has_tiff, source_table
FROM image WHERE image_of_id = 'casent4045392' ORDER BY shot_type, shot_number;
SELECT COUNT(*) AS image_rows_now, MAX(upload_date) AS newest FROM image;
