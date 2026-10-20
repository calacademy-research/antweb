-- Image numbers of places (Brian, 2026-10-20): Images, images of each view, Imaged specimens.
--
--   place_image_count   one row per place and shot type, written by nightly set 4
--                       (utilData.do?action=placeImageCounts, PlaceImageCountDb) and by the
--                       Recalculate button of a place page.
--     place_type        geolocale, bioregion, museum or project
--     place_key         geolocale id, bioregion name, museum code or project name
--     shot_type         image.shot_type in lower case (h head, p profile, d dorsal, v ventral,
--                       f field, l label; '' when empty), or a summary row:
--                         *all         all image files; specimens with any image
--                         *ant         images that are not labels; specimens with at least one
--                                      ("Imaged specimens" on the page)
--                         *label_only  specimens whose only images are labels, and their images
--     image_count       images of that shot type
--     specimen_count    specimens with at least one image of that shot type
--     computed          when the row was written
--
--   The same run also writes image_count (all files, labels included) and imaged_specimen_count
--   (specimens with an image of the ant, labels not counted) of geolocale, bioregion, museum and project.
--
-- Nothing to run by hand: the application creates the table when it is missing
-- (PlaceImageCountDb.ensureSchema(), same DDL, with the character set and collation of
-- taxon.taxon_name). This file is the record.
--
-- Check:   select place_type, count(distinct place_key), max(computed) from place_image_count group by 1;
--          select p.shot_type, p.image_count, p.specimen_count from place_image_count p
--            join geolocale g on p.place_type = 'geolocale' and p.place_key = g.id
--            where g.name = 'Madagascar' and g.georank = 'country';
-- Undo (deploy the previous code first; the pages then show the old numbers again after the next
-- nightly run): DROP TABLE place_image_count;

CREATE TABLE IF NOT EXISTS place_image_count (
  place_type varchar(16) NOT NULL,
  place_key varchar(128) NOT NULL,
  shot_type varchar(32) NOT NULL,
  image_count int NOT NULL DEFAULT 0,
  specimen_count int NOT NULL DEFAULT 0,
  computed datetime NULL,
  PRIMARY KEY (place_type, place_key, shot_type)
) ENGINE=InnoDB;  -- the application adds DEFAULT CHARSET / COLLATE of taxon.taxon_name
