# AntCat names to AntWeb: the export and the WorldAnts import

Read from the code on master at commit 5b0c1d5 (2026-10-18), and from calacademy-research/antcat at 185285c (2026-10-15) for the export side. Paths are relative to each repo's root. "Check" means the code suggests it but it should be confirmed on staging or in the database.

## In plain words

AntCat is the ant catalog: every name, whether it is valid, and if not, which name is valid now. AntWeb does not ask AntCat about names while it runs; it keeps its own copy of the whole catalog in its `taxon` table and replaces that copy every morning.

1. At 3:00 AM Pacific the antcat-export server copies the live AntCat database and writes one big tab-separated file, `antcat.antweb.txt` (about 139 MB), which it serves on port 9090.
2. At 5:00 AM AntWeb's nightly set 1 downloads that file. If it is too short (29,000 lines or fewer), AntWeb stops and keeps yesterday's names. Otherwise it deletes all AntCat names from its own tables and loads them again from the file, with each name's status and current valid name, its countries and bioregions, its fossil flag and its taxonomic history text.
3. Then AntWeb moves the entries of every species list (projects, countries and states, bioregions, museums) from old names to the current valid name.

What does **not** happen: specimens are not moved. A specimen gets its current valid name only when its file is uploaded. If AntCat changes a name later, the specimen stays on the old name until the curator uploads again (for CAS, until the next TaxonWorks upload), and with the default page settings it then shows on neither the old nor the new species page. A nightly "name update" for existing specimens is designed and waiting in the feature backlog.

---

## 1. The export (antcat-export server)

Droplet antcat-export (public 165.232.141.186, private 10.124.0.4). Full rebuild notes are in the project document `antcat-export-rebuild.md`; this is the data path.

| Step | Where | What |
|---|---|---|
| 03:00 Pacific | root crontab on antcat-export | `cd /root/antcat/docker; ./export_database.sh >> /root/antcat/run-results 2>&1` |
| 1 | `docker/export_database.sh` (antcat repo) | `git reset --hard origin/master`, `docker-compose build` (stops if the build fails), runs `download-database.sh` in a throwaway Ruby + MySQL stack |
| 2 | `docker/download-database.sh` | over the private network `ssh root@10.124.0.5` (antcat-prod): `mysqldump --single-transaction` of `antcat`, gzip, copy back, archive a dated copy in `/root/antcat/database_archive/` (7 days kept), load it locally |
| 3 | `bundle exec rake antweb:export` (`lib/tasks/antweb.rake`) | `Exporters::Antweb::Exporter` writes `data/output/antcat.antweb.txt` |
| 4 | `download-database.sh` last lines | `cp` the file to `/root/antcat/database_export/antcat.antweb.txt` (not an atomic move) |
| serve | container `web` (httpd), `docker/web_server_daemon.sh` | `http://antcat-export:9090/antcat.antweb.txt`; AntWeb resolves `antcat-export` to 10.124.0.4 through `extra_hosts` in `docker-compose.prod.yml` line 11 and `docker-compose.stage.yml` line 7 |

The export took about an hour on 2026-10-11 (finished 3:56 AM). If it fails, the file from the last good run stays in place and AntWeb loads that again (no date check on the AntWeb side).

### File format

Header and columns: `app/services/exporters/antweb/export_taxon.rb` (antcat repo), one line per taxon, tab-separated, newlines removed from values:

`antcat id, subfamily, tribe, genus, subgenus, species, subspecies, author date, author date html, authors, year, status, available, current valid name, original combination, was original combination, fossil, taxonomic history html, reference id, bioregion, country, current valid rank, hol id, current valid parent`

Exported ranks: all except subtribe and infrasubspecies (`exporter.rb`, `EXPORTABLE_TYPES`).

**Order matters.** The exporter sorts taxon ids by status and reverses the list (`Taxon.where(...).order(:status).pluck(:id).reverse`), so `valid` names come first, then `unidentifiable`, `unavailable...`, `synonym`, `original combination`, `obsolete combination`, `homonym`, and so on. AntWeb's import (section 2, step 5) skips a non-valid name whose current valid name is not yet in its `taxon` table, so it relies on valid names being written before the names that point to them. Nothing in either repo states this; a change of sort order on the AntCat side would silently drop synonyms.

## 2. The import in nightly set 1

Caller: `util/UtilDataAction.doAction()` line 222 (`set1`), which runs, in order: `worldantsFetchAndReload` (line 291), `taxonFinish` (line 312), `TaxonWorksNightly.run(connection, 90)` (see [specimen-upload.md](specimen-upload.md) section 5), `TaxonMgr.populate`. Manual equivalents for admins: `utilData.do?action=worldantsFetchAndReload` (fetch and load), `utilData.do?action=worldantsReload` (load the file already downloaded, no fetch), and `upload.do?action=fetchAndReloadWorldants` (fetch and load from the Curate page; on failure it adds an admin alert).

Steps (`curate/speciesList/SpeciesListUploader.java`, `upload/SpeciesListUpload.java`):

1. **Fetch** (`fetchWorldantsList()` line 66): `HttpUtil.writeUrlContents("http://antcat-export:9090/antcat.antweb.txt", web/speciesList/world/worldants_speciesList.txt)` (line 78). The file is written in place. Failure: message "Failed to fetch: ... Connected to VPN?"; set 1 goes on with TaxonWorks.
2. **Size guard** (`validateWorldantsFile()` line 123): 29,000 lines or fewer (`WORLDANTS_LOW_COUNT`, line 121): "Worldants count is low:N origWorldantsCount:M", nothing loaded. **On staging and dev the guard always answers "success"** (line 124), so a short file would be loaded there.
3. On success a dated copy is kept: `web/upload/<yyyyMMdd-HH:mm:ss>-worldants.txt` (`record()` line 169).
4. **Delete the old AntCat data** (`SpeciesListUpload.importSpeciesList()`, lines 152 to 182):
   - `BioregionTaxonDb.deleteSource("antcat")`: AntCat-sourced rows of `bioregion_taxon`;
   - the size guard again (line 170);
   - `SpeciesListUploadDb.prepareDatabase("worldants")` (line 35): `delete from taxon where antcat = 1`; `delete from homonym where antcat = 1`; `delete from description_edit where title = 'taxonomichistory'`; same for `description_homonym`; `delete from proj_taxon where project_name = 'worldants'`; `delete from geolocale_taxon where source like '%antcat'`; AntCat rows of project `fossilants`.
5. **Load each line** (`importSpeciesList(project, fileName, ...)` from line 231):
   - required header columns `subfamily`, `genus`, `species` (line 370); names are lower-cased except author, country and bioregion columns; "species subspecies" in one column is split;
   - each row gets `antcat = 1`, `source = 'worldants'`, `insert_method = 'speciesListUpload'`, rank from `current valid rank` (family rows become `formicidae`);
   - skipped (`ignoreTaxon`): a rank other than family, subfamily, genus, species, subspecies (line 643); status `homonym` (saved to `homonym` and `description_homonym` instead, line 646); status `unavailable uncategorized` (`Status.excludeFromImport`, line 663); a name whose status uses a current valid name but whose current valid name is itself (line 671); a name whose current valid name is not (yet) in `taxon` (line 683; see "Order matters" above);
   - saved: `taxon` row and `proj_taxon` row in project `worldants` (`saveTaxonAndProjTaxon`, line 708); taxonomic history and other text columns to `description_edit`; if not a fossil: `geolocale_taxon` for the AntCat country under the **current valid name**, only if that taxon is valid (line 744), and `bioregion_taxon` for the bioregion (line 759); if a fossil: `proj_taxon` in project `fossilants` (line 771). Unknown countries are reported ("Country not found").
   - The status and current valid name are stored on the `taxon` row (`taxon.status`, `taxon.current_valid_name` as AntWeb's internal key, e.g. `formicinaecamponotus maculatus`).
6. **Record the load** (`SpeciesListUploader.worldantsReload()` line 98, `record(..., true)`): a row in `worldants_upload` (`WorldantsUploadDb.insertWorldantsUpload()` line 43). That method calls `commit()` (line 55), which commits the whole import so far. Nothing else commits it before TaxonWorks starts; see section 4.
7. **Move list entries to current valid names** (`TaxonSetDb.updateTaxonSetTaxonNames()` line 88, called at `SpeciesListUpload` line 198): in `museum_taxon`, `bioregion_taxon`, `geolocale_taxon` and `proj_taxon` (except projects `worldants` and `allantwebants`), every row whose taxon has `status != 'valid'` and a `current_valid_name` gets `taxon_name` = the valid name; where the valid name is already in that list the old row is deleted (`updateTaxonSetTaxonName()` line 119). One step per run (a chain A to B to C needs two runs); set 3 runs it again (`updateTaxonSets`), so two steps per night. No entry is written to the species-list history tables, so curators see no record of the move.
8. `ProjTaxonDb.regenerateAllAntweb()` (project `allantwebants` rebuilt from `taxon`), `WorldantsUploadDb.deleteHomonymsWithoutTaxa()`, project upload date.
9. Back in `UtilDataAction` (line 291): `ProjTaxonCountDb.childrenCountCrawl("worldants")` (counts for the worldants project), a line in `admin.log`, the report (`UploadDetails.finish()`): `web/log/worldants/<date>-fetchAndReloadWorldants.html` and `worldants_upload.log_file_name` (`updateWorldantsUpload()` line 64, which commits again, line 79).
10. If no taxon changed this week, an admin alert "no Worldants changes" is added (`worldantsFetchAndReload()` line 60).
11. `taxonFinish`: `TaxonDb.setSubfamilyChartColor()`.
12. After TaxonWorks: `TaxonMgr.populate(connection, true, false)` reloads the in-memory taxon cache, so pages use the new names.

Duration: about 12 minutes on production (comments in `UtilDataAction` line 289: 12 to 18 minutes).

## 3. Tables changed

| Table | Change |
|---|---|
| `taxon` | all `antcat = 1` rows deleted and re-inserted (curator-made names, `antcat` not 1, are kept) |
| `homonym` | AntCat rows deleted and re-inserted |
| `description_edit`, `description_homonym` | all `taxonomichistory` texts deleted and re-inserted |
| `proj_taxon` | project `worldants` replaced; AntCat rows of `fossilants` replaced; `allantwebants` regenerated; other projects: names moved to current valid names |
| `geolocale_taxon` | rows with source `...antcat` replaced (under current valid names); other rows: names moved |
| `bioregion_taxon` | AntCat-sourced rows replaced; other rows: names moved |
| `museum_taxon` | names moved |
| `worldants_upload` | one row per load |
| files | `web/speciesList/world/worldants_speciesList.txt` (overwritten), `web/upload/<date>-worldants.txt`, `web/log/worldants/<date>-fetchAndReloadWorldants.html`, `web/log/worldantsLog.txt` |

## 4. Transactions

Set 1 runs on one `longConPool` connection with auto-commit off (`UtilDataAction` line 140). The AntCat import is committed by the `commit()` inside `WorldantsUploadDb.insertWorldantsUpload()` (step 6) and `updateWorldantsUpload()` (step 9), not by the import code itself. So when the TaxonWorks step later rolls back a failed upload, the AntCat import stays. This is a side effect that a future change could break without noticing (for example by moving the `worldants_upload` row to its own connection); the test file proposes a check.

## 5. What does NOT happen (the known gap)

- **Specimens are not re-resolved.** `specimen.taxon_name` is written only by the specimen upload (`SpecimenUploadProcess.setStatusAndCurrentValidName()`, see [specimen-upload.md](specimen-upload.md) section 4). No nightly set touches it. When AntCat synonymises or recombines a name after the upload:
  - the valid species page lists only specimens filed under the valid name (`Species.setChildren()` joins `specimen.taxon_name` to `taxon.taxon_name`), so the specimen is missing there;
  - the old name's page filters by taxon status ("valid extant" by default), so it is hidden there too, unless the status set is "complete" or "worldants";
  - set 2 rebuilds the specimen-derived country and adm1 lists from `specimen.taxon_name` (`generateGeolocaleTaxaFromSpecimens`), re-adding the old name, which set 3 (`updateTaxonSets`) then moves again; maps, museum and bioregion lists also start from `specimen.taxon_name`.
  - The admin query `notValidTaxaFromSpecimenList` (`util/Queries.java`) lists the affected specimens per group.
  - CAS is affected only until the next TaxonWorks upload; other groups until they upload again. The old "reload last file" button (`reloadSpecimenList`) was removed in 2026-10.
- Page text (`description_edit` other than taxonomic history), `taxon_prop` (default images and other properties) and favourite images under an old name are not moved.
- Nothing checks that the AntCat file is from today.
- Curator-made names on species lists whose current valid name AntCat changes are moved (step 7) but the move is not recorded in the list history.

The approved fix (nightly name update for all specimens, table `specimen_name_update`, "Name updates" page, `etc/name-update.properties` with off / report / apply) is described in the feature backlog section of [test-gaps-uploads-nightly.md](test-gaps-uploads-nightly.md).

## 6. Logs and where failures show

| What | Where |
|---|---|
| Export ran, line count | `/root/antcat/run-results` on antcat-export |
| Fetch or size-guard failure | `admin.log` line "worldantsFetchAndReload: <message>" and a report under `web/log/upload/<date>-Fetch.html`; no admin alert from the nightly path |
| Load done | `web/log/worldants/<date>-fetchAndReloadWorldants.html`; table `worldants_upload` (admin query `query.do?name=worldantsUploads`) |
| An exception in set 1 | `UtilDataAction.handleException()`: rolled back, message only in the set's HTTP answer and the Tomcat log (not compute.log) |
