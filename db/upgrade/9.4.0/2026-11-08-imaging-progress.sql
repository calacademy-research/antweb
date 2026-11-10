-- The imaging progress page (imagingProgress.do, Brian Fisher, 2026-11-08): valid living ant species and subspecies
-- imaged, for all of AntWeb and by bioregion, country, genus and museum, with the unimaged names that have specimens in
-- AntWeb (the imaging labs' work queue). One row per place, replaced once a day by the daily update (set 4, step
-- imagingProgress, after the statistics row; ImagingProgressDb.fill; admins: utilData.do?action=imagingProgress).
--
--   imaging_progress           key (place_type, place_key)
--     place_type               all, bioregion, country, genus, museum
--     place_key                all: living (valid living species and subspecies, the headline), living-species (species
--                              only, = statistics.living_species and living_species_imaged), fossil; bioregion: its name;
--                              country: the geolocale id; genus: subfamily + genus (the genus taxon_name); museum: its code
--     place_name, parent_name  shown name and its parent (country: name, bioregion; genus: genus, subfamily)
--     total                    valid living species and subspecies of the place (bioregion, country: the Valid view of the
--                              list; genus: of the genus; museum: with a specimen there)
--     imaged                   of them, with a photo of the ant anywhere in AntWeb (labels never count)
--     imaged_here              of them, with a photo of the ant of a specimen collected there (museum: a specimen of the
--                              museum); null for all and genus
--     unimaged_with_specimens  not imaged, with at least one specimen record in AntWeb
--     unimaged_casc            of those, with a specimen of museum CASC
--     computed                 when the row was written
--
-- The application creates the table when it is missing (ImagingProgressDb.ensureSchema, in the character set and
-- collation of taxon.taxon_name); this file records it and can be run by hand (safe to run twice).

CREATE TABLE IF NOT EXISTS imaging_progress (
  place_type varchar(16) NOT NULL,
  place_key varchar(128) NOT NULL,
  place_name varchar(250) NULL,
  parent_name varchar(128) NULL,
  total int NOT NULL DEFAULT 0,
  imaged int NOT NULL DEFAULT 0,
  imaged_here int NULL,
  unimaged_with_specimens int NOT NULL DEFAULT 0,
  unimaged_casc int NOT NULL DEFAULT 0,
  computed datetime NULL,
  PRIMARY KEY (place_type, place_key)
) ENGINE=InnoDB;

-- Undo: the page then says the imaging progress has not been counted; the next daily update creates the table again
-- unless the code is rolled back too.
-- DROP TABLE IF EXISTS imaging_progress;
