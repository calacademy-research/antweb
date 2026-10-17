-- Previous determinations of a specimen (curator request, 2026-10-17).
--
--   specimen.previous_determinations   free text, newest first, entries separated by " | ",
--                                      e.g. "Nylanderia: US_CA01 by Ward on 2025-12-10 | Nylanderia: US_CA01 by Fisher on 2000-11-10"
--                                      Filled by the TaxonWorks upload (DwC previousIdentifications)
--                                      and by the optional specimen-file column previousDeterminations.
--                                      Shown on the specimen page only (not in the API or downloads yet).
--
-- Run on staging, then production, BEFORE deploying the code that reads it
-- (the specimen page and every specimen list select this column).
--
-- Check first:   show columns from specimen like 'previous_determinations';
-- Rollback:      deploy the previous code first, then
--                ALTER TABLE specimen DROP COLUMN previous_determinations;
--
-- No other table needs it: api3_1_specimen and darwin_core_3 are views with explicit
-- column lists, and specimen uploads insert straight into specimen.

ALTER TABLE specimen ADD COLUMN previous_determinations TEXT NULL DEFAULT NULL;
