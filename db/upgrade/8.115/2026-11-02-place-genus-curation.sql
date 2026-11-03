-- The Not curated column of genus lists (Brian Fisher, 2026-11-02, 19:44; replaces Other specimens).
--
--   place_genus_curation  one row per place and genus: the specimens of the genus collected in the place (not
--                         red-flagged, ants) and those whose name is not curated (neither a valid name nor a
--                         morphospecies a curator has put on a list: ListView.notCuratedCondition), with its parts.
--     list_type           country, adm1, museum or bioregion
--     list_key            the country's name, the adm1's name (specimen.adm1, as the adm1 pages select specimens),
--                         the museum's code, the bioregion's name
--     taxon_name          the genus row's taxon_name (subfamily + genus); subfamily and genus as on the row
--     specimens           specimens of the genus collected there (the specimen source icon)
--     not_curated         of those, the specimens whose name is not curated (the cell; hover "N specimens not curated")
--     morpho_names        uncurated morphospecies (names) ...
--     morpho_specimens    ... and their specimens
--     indet_specimens     specimens identified only to genus (indetermined, unidentifiable, "(indet)")
--     other_specimens     the rest of not_curated (names not in AntCat: unrecognized)
--     counted             when the row was written
--
--   place_specimen_name   the not red-flagged ant specimens collected in each place, by name (same list_type and
--                         list_key): the genus lists read it instead of the specimen table (the names the views add,
--                         the specimen source icon). specimens: how many of the name.
--
-- Written by nightly set 4 and by utilData.do?action=placeGenusCuration (admins; name=country, adm1, museum or bioregion
-- does one kind): PlaceGenusCurationDb.fillAll, each kind replaced in one transaction, one grouped query per kind.
-- compute.log: "Not curated counted: ...". Read by the genus lists (taxonomicPage.do?rank=genus, browse.do of a
-- subfamily) and the genus page in the view Not curated (statusSet=notcurated). Missing table or no rows: empty cells.
--
-- Nothing to run by hand: the application creates both tables when missing (PlaceGenusCurationDb.ensureSchema(), same
-- DDL, with the character set and collation of taxon.taxon_name). This file is the record.
--
-- Check:   select list_type, count(distinct list_key), count(*), max(counted) from place_genus_curation group by 1;
--          select taxon_name, specimens, not_curated, morpho_names, morpho_specimens, indet_specimens, other_specimens
--            from place_genus_curation where list_type = 'country' and list_key = 'Mozambique' order by not_curated desc limit 10;
-- Undo (deploy the previous code first): DROP TABLE place_genus_curation, place_specimen_name;

CREATE TABLE IF NOT EXISTS place_genus_curation (
  list_type varchar(16) NOT NULL,
  list_key varchar(128) NOT NULL,
  taxon_name varchar(128) NOT NULL,
  subfamily varchar(64) NULL,
  genus varchar(64) NULL,
  specimens int NOT NULL DEFAULT 0,
  not_curated int NOT NULL DEFAULT 0,
  morpho_names int NOT NULL DEFAULT 0,
  morpho_specimens int NOT NULL DEFAULT 0,
  indet_specimens int NOT NULL DEFAULT 0,
  other_specimens int NOT NULL DEFAULT 0,
  counted datetime NULL,
  PRIMARY KEY (list_type, list_key, taxon_name)
) ENGINE=InnoDB;  -- the application adds DEFAULT CHARSET / COLLATE of taxon.taxon_name

CREATE TABLE IF NOT EXISTS place_specimen_name (
  list_type varchar(16) NOT NULL,
  list_key varchar(128) NOT NULL,
  taxon_name varchar(128) NOT NULL,
  specimens int NOT NULL DEFAULT 0,
  counted datetime NULL,
  PRIMARY KEY (list_type, list_key, taxon_name)
) ENGINE=InnoDB;  -- the application adds DEFAULT CHARSET / COLLATE of taxon.taxon_name
