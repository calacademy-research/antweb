-- The image view of the GBIF archive: medium images from static.antweb.org, rights holder by upload group
-- (Brian Fisher, 2026-11-11).
--
-- Who reads the view. The Academy's IPT (ipt.calacademy.org, resource antweb) publishes AntWeb to GBIF every Monday
-- at 12:00 Pacific from two SQL sources, "select * from darwin_core_2" (the specimens) and
-- "select * from darwin_core_multimedia" (the images, this view). It reads them from a copy of this database kept at
-- the Academy, restored from the daily dump about a day and a half behind; the views travel with the dump, so a
-- change here reaches the archive without any change in the IPT. The IPT maps the ten columns of this view by their
-- position: their number, order and names stay as they were.
--
-- What changes.
--   imageurl      was https://www.antweb.org/images/<code>/<code>_<shot>_<number>_high.jpg (1600 pixels wide, behind
--                 the bot shield of www.antweb.org: GBIF got "403" for every image newer than its own copies).
--                 Now https://static.antweb.org/images/<code>/<code>_<shot>_<number>_med.jpg, the address the
--                 site's own pages use (SpecimenImage.getMedium). Brian Fisher: GBIF and Lens get links to the
--                 medium images only.
--   rightsHolder  was the copyright line of the image ("California Academy of Sciences, 2000-<upload year>", which
--                 the Image Uploader stamps on every image of every group). Now "California Academy of Sciences"
--                 for the images the Academy's group uploaded (group_image.group_id = 1) and empty (NULL) for the
--                 images of the other groups and the images with no group. Production 2026-11-11: 229,989 images
--                 of the Academy, 49,741 of other groups, 3 with no group; no image under two groups. The
--                 photographer (creator), the licence and the link to the specimen's page (references) stay on
--                 every image.
--   order         by the new address (it was by the old one).
-- Everything else is the definition as it stood on production on 2026-11-11 (SHOW CREATE VIEW), including the
-- definer. The Academy's images are found through a list of their ids (one row per image, whatever group_image
-- holds), so a second group_image row of an image can never give the archive the image twice.
--
-- Safe to run twice. Rollback: 2026-11-11-darwin-core-multimedia-medium-rollback.sql (the definition of 2026-11-11).

CREATE OR REPLACE ALGORITHM=UNDEFINED DEFINER=`antweb`@`localhost` SQL SECURITY DEFINER VIEW `darwin_core_multimedia` AS
select concat(_utf8mb3'CAS:ANTWEB:',`im`.`image_of_id`) AS `occurrenceid`,
       `im`.`image_of_id` AS `catalognumber`,
       concat('https://static.antweb.org/images/',`im`.`image_of_id`,'/',`im`.`image_of_id`,'_',`im`.`shot_type`,'_',`im`.`shot_number`,'_med.jpg') AS `imageurl`,
       `im`.`shot_type` AS `shot_type`,
       `im`.`shot_number` AS `shot_number`,
       concat('https://www.antweb.org/specimenImages.do?name=',`im`.`image_of_id`) AS `references`,
       concat(`im`.`image_of_id`,convert((case `im`.`shot_type` when 'd' then ' dorsal' when 'h' then ' head' when 'l' then ' label' when 'p' then ' profile' end) using utf8mb3),' view ',`im`.`shot_number`) AS `title`,
       `li`.`license` AS `license`,
       (case when `cas`.`image_id` is not null then 'California Academy of Sciences' end) AS `rightsHolder`,
       nullif(`ar`.`name`,'') AS `creator`
from (((`image` `im`
       left join `license` `li` on((`im`.`license` = `li`.`id`)))
       left join (select distinct `image_id` from `group_image` where `group_id` = 1) `cas` on((`cas`.`image_id` = `im`.`id`)))
       left join `artist` `ar` on((`im`.`artist` = `ar`.`id`)))
order by concat('https://static.antweb.org/images/',`im`.`image_of_id`,'/',`im`.`image_of_id`,'_',`im`.`shot_type`,'_',`im`.`shot_number`,'_med.jpg');
