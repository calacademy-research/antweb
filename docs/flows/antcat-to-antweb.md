# AntCat names to AntWeb: the export and the WorldAnts import

Read from the code on master at commit 5b0c1d5 (2026-10-18), and from calacademy-research/antcat at 185285c (2026-10-15) for the export side. Paths are relative to each repo's root. "Check" means the code suggests it but it should be confirmed on staging or in the database.

## In plain words

AntCat is the ant catalog: every name, whether it is valid, and if not, which name is valid now. AntWeb does not ask AntCat about names while it runs; it keeps its own copy of the whole catalog in its `taxon` table and replaces that copy every morning.

1. At 3:00 AM Pacific the antcat-export server copies the live AntCat database and writes one big tab-separated file, `antcat.antweb.txt` (about 139 MB), which it serves on port 9090.
2. At 5:00 AM AntWeb's nightly set 1 downloads that file. If it is too short (29,000 lines or fewer), AntWeb stops and keeps yesterday's names. Otherwise it deletes all AntCat names from its own tables and loads them again from the file, with each name's status and current valid name, its countries and bioregions, its fossil flag and its taxonomic history text.
3. Then AntWeb moves the entries of every species list (projects, countries and states, bioregions, museums) from old names to the current valid name.

4. Then (since 2026-10, when switched on) the **name update** moves existing specimens whose name AntCat has changed since their upload to the current valid name, with the same rule the specimen upload uses, and keeps the name from the curator's file as "Uploaded As". Every specimen moved is listed on the Name updates page (section 7).

Before the name update, specimens were not moved: a specimen got its current valid name only when its file was uploaded, and if AntCat changed a name later it stayed on the old name until the curator uploaded again (for CAS, until the next TaxonWorks upload), showing on neither the old nor the new species page.

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

Caller: `util/UtilDataAction.doAction()` (`set1`), which runs, in order: `worldantsFetchAndReload`, `taxonFinish`, `TaxonWorksNightly.run(connection, 90)` (see [specimen-upload.md](specimen-upload.md) section 5), the name update (section 7), `TaxonMgr.populate`. Manual equivalents for admins: `utilData.do?action=worldantsFetchAndReload` (fetch and load), `utilData.do?action=worldantsReload` (load the file already downloaded, no fetch), and `upload.do?action=fetchAndReloadWorldants` (fetch and load from the Curate page; on failure it adds an admin alert).

Steps (`curate/speciesList/SpeciesListUploader.java`, `upload/SpeciesListUpload.java`):

1. **Fetch** (`fetchWorldantsList(nightly)`): `HttpUtil.downloadToFile("http://antcat-export:9090/antcat.antweb.txt", web/speciesList/world/worldants_speciesList.txt)`. It downloads to `worldants_speciesList.txt.part` and moves it into place only when complete (not empty, and as long as the server's Content-Length), so a failed or cut-off try keeps the previous file. Since 2026-10-25 the nightly downloads the file **before set 1 takes its database connection** (`UtilDataAction.execute` calls `SpeciesListUploader.prefetchWorldantsList()` for the scheduler's set 1 only): it tries again every 10 minutes and makes no new try once an hour has passed since the first (`util/FetchRetry`). Set 1 then uses that file without a second download. Why before the connection: the long pool closes a connection held over 60 minutes (`unreturnedConnectionTimeout=3600`, `WEB-INF/struts-configDb.xml`) and set 1 already takes up to about an hour. An admin run ("Import AntCat names now") downloads once. Failure: message "Failed to fetch <url> after N tries over M minutes: <reason>" (in compute.log through the name-update skip line and in admin.log); when the nightly has no file it also adds an admin alert and emails antweb@antweb.org (a failed alert or email never stops set 1). Set 1 goes on with TaxonWorks. Why: on 2026-10-23 one fetch failed and that night's name update was skipped. Tests: `test/.../util/FetchRetryTest.java`, `tests/unit/test_set1_prefetch.py`.
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

- **Specimens are not re-resolved (until 2026-10; see section 7).** `specimen.taxon_name` is written only by the specimen upload (`SpecimenUploadProcess.setStatusAndCurrentValidName()`, see [specimen-upload.md](specimen-upload.md) section 4). No nightly set touches it. When AntCat synonymises or recombines a name after the upload:
  - the valid species page lists only specimens filed under the valid name (`Species.setChildren()` joins `specimen.taxon_name` to `taxon.taxon_name`), so the specimen is missing there;
  - the old name's page filters by taxon status ("valid extant" by default), so it is hidden there too, unless the status set is "complete" or "worldants";
  - set 2 rebuilds the specimen-derived country and adm1 lists from `specimen.taxon_name` (`generateGeolocaleTaxaFromSpecimens`), re-adding the old name, which set 3 (`updateTaxonSets`) then moves again; maps, museum and bioregion lists also start from `specimen.taxon_name`.
  - The admin query `notValidTaxaFromSpecimenList` (`util/Queries.java`) lists the affected specimens per group.
  - CAS is affected only until the next TaxonWorks upload; other groups until they upload again. The old "reload last file" button (`reloadSpecimenList`) was removed in 2026-10.
- Page text (`description_edit` other than taxonomic history), `taxon_prop` (default images and other properties) and favourite images under an old name are not moved.
- Nothing checks that the AntCat file is from today.
- Curator-made names on species lists whose current valid name AntCat changes are moved (step 7) but the move is not recorded in the list history.

The first point is what the nightly name update (section 7) fixes, once it is in apply mode.

## 6. Logs and where failures show

| What | Where |
|---|---|
| Export ran, line count | `/root/antcat/run-results` on antcat-export |
| Fetch or size-guard failure | `admin.log` line "worldantsFetchAndReload: <message>" and a report under `web/log/upload/<date>-Fetch.html`; a fetch the nightly gave up on (no new try after an hour) also adds an admin alert and emails antweb@antweb.org (since 2026-10-25) |
| Load done | `web/log/worldants/<date>-fetchAndReloadWorldants.html`; table `worldants_upload` (admin query `query.do?name=worldantsUploads`) |
| An exception in set 1 | `UtilDataAction.handleException()`: rolled back, message only in the set's HTTP answer and the Tomcat log (not compute.log) |

## 7. The nightly name update (2026-10)

Approved by Brian 2026-10-18. Code: `upload/SpecimenNameUpdate.java` (the step), `upload/CurrentValidNameRule.java` (the rule, shared with the upload), `upload/SharedNames.java` and `upload/SharedNameReport.java` (names of two or more AntCat taxa: detection and the "Needs the curator's decision" line, shared with the upload report), `upload/SpecimenNameUpdateDb.java` (table), `curate/NameUpdatesAction.java` (page).

**The rule is the upload's.** `SpecimenUploadProcess.setStatusAndCurrentValidName()` now asks `CurrentValidNameRule.decide()` which name to file a specimen under and keeps its own side effects (report lines, the homonym placeholder taxon, the name columns). The nightly step asks the same method for the uploaded name of every specimen (`coalesce(nullif(original_taxon_name, ''), taxon_name)`; the upload fills `original_taxon_name` only when it replaced the name).

**When it runs.** Set 1, after the AntCat import and the TaxonWorks upload, before `TaxonMgr.populate` and before sets 2 to 5 rebuild lists, counts and maps from `specimen.taxon_name` (`UtilDataAction.nightlyNameUpdate`). Skipped when tonight's AntCat import did not succeed, and when a specimen upload is running; while it runs, uploads are refused ("nightly name update"). It commits what set 1 did before it and then its own work, so a failure in the step (logged "name update: FAILED") does not undo the AntCat import or the TaxonWorks upload.

**What it does.** Candidates are the (group, uploaded name, current name) combinations whose uploaded name has a status in `Status.USES_CURRENT_VALID_NAME` (synonym, original combination, obsolete combination, obsolete classification, unavailable misspelling, unavailable uncategorized) or whose uploaded name differs from the current name. For each, `SpecimenNameUpdate.decide()`:

| Case | Result |
|---|---|
| uploaded name now a synonym etc., current valid taxon exists | moved to it: `taxon_name`, `original_taxon_name` = uploaded name, `status` valid, family to subspecies from the valid taxon, `is_introduced` recomputed |
| uploaded name valid again (resurrected) | moved back to it, `original_taxon_name` cleared |
| name (filed or uploaded) belongs to two or more AntCat taxa with different valid names, e.g. Pachycondyla sauteri (Cryptopone sauteri or Ectomomyrmex sauteri) | not moved; listed on nameUpdates.do under "Needs the curator's decision" (mode `shared_name`, replaced each run). Read from the AntCat export the import loaded (`web/speciesList/world/worldants_speciesList.txt`, every row; the import stores only the first taxon of a name); without it, the homonym table (`SharedNames.load`). **Shared with the specimen upload:** the upload keeps such a name instead of moving it and lists it in its report under "Needs the curator's decision" with the same line; detection and wording are one code path, `upload/SharedNameReport` ([specimen-upload.md](specimen-upload.md) section 4, 3a) |
| filed under a name AntCat calls valid while the uploaded name now points to another name (e.g. uploaded Camponotus gigas, filed under Dinomyrmex gigas) | not moved; listed on nameUpdates.do, collapsed, under "Not moved: filed under a name AntCat calls valid (N names)" with the uploaded name, the name it is filed under, where AntCat points the uploaded name and the specimens (mode `kept_valid`, `new_name` = where the uploaded name points, replaced each run), for a spot check |
| already on the right name (also: filed under the valid name the uploaded name points to) | nothing |
| morphospecies or indet (uploaded or current name) | left alone |
| filed under a homonym placeholder (`insert_method = 'homononymMirroringTaxon'`), or the uploaded name is in the `homonym` table, or the current valid name is a homonym or a placeholder, or the name's status is homonym | the same category as the row above since 2026-10-20 (Brian: one category, one wording, one code path; until then these were left alone silently as "homonym" or "filed under a homonym placeholder"): not moved, listed under "Needs the curator's decision" (`SharedNameReport.involved`, also asked by the upload); never creates taxa. Reason without the candidates when only the homonym table or a placeholder tells: "name is shared by two or more AntCat taxa; please confirm the identification"; when it is the current valid name: "its current valid name, Carebara silvestrii, is shared ..." |
| current valid name has no taxon row, or no current valid name | left alone |
| uploaded name not in AntCat, or another status (unavailable, ...) | left alone |

Curator list entries: list rows are moved to the current valid name every night by the AntCat import (`TaxonSetDb.updateTaxonSetTaxonNames`, step 7 of section 2, and again in set 3). Curator rows parked by a cleanup (`taxon_set_parked`, PR #180) under a name that is now a synonym etc. are moved to the current valid name by the name update, so the restore puts them back under the name the list shows.

**Switch.** `etc/name-update.properties` on the host (`/root/antweb/etc/`, staging `/root/antweb-next/etc/`; the container reads `/antweb/deploy/etc/name-update.properties` at each run, no restart needed; gitignored):

```
mode=report        # off | report | apply   (no file = off)
maxChanges=50000   # apply mode: above this many specimens in one run, change nothing and list them as "would change"
```

- `report`: rows `would_change`, specimens untouched. `apply`: specimens moved, rows `changed`. Each run deletes the previous `would_change` rows (the page shows only the latest preview); `changed` rows are kept.
- Admins: "Preview now (report only)" on the Name updates page (`utilData.do?action=specimenNameUpdatePreview`, POST) runs it in report mode at once; `utilData.do?action=specimenNameUpdate` runs it with the file's setting.

**Record.** Table `specimen_name_update` (created by the application; DDL, checks and the undo in `db/upgrade/8.113/2026-10-20-specimen-name-update.sql`): run_date, mode, code, access_group, uploaded_name, old_name, new_name, antcat_status, reason, antcat_id. `reason` (added 2026-10-20) says why, with AntCat's reference, read from AntCat's taxonomic history as the AntCat import stored it that night (`description_edit`, title `taxonomichistory`; `AntcatHistory`, `NameUpdateReason`): It compares the old name with the new name across the whole chain of AntCat acts (rules decided by Brian Fisher 2026-10-20, `claude/antweb-name-update-reasons-spec.md` in the project): epithet changed: `synonym: junior synonym of Strumigenys pilinasis (Booher 2019)`; genus changed, epithet the same or only its gender ending: `new combination: Holcoponera striatula (Camacho et al. 2022)` (plus `; spelling as in AntCat` when the ending changed or AntCat calls the old name a misspelling); `unnecessary replacement name for L. reticulata (Brown 1975); junior objective synonym`; `raised to species: Crematogaster sjostedti (Taylor & McGavin 2020)`; `placed as subspecies: Pheidole pubiventris foederalis (Kempf 1972b)`; `nominotypical subspecies, shown as the species`; a nominotypical subspecies whose species is a synonym: `synonym: junior synonym of Solenopsis canariensis (Seifert 2026a); nominotypical subspecies of S. texana` (every synonym starts "synonym: junior synonym of", 2026-10-20); same genus, only the ending of the epithet: `spelling as in AntCat: Opisthopsis picta` (AntWeb shows AntCat's spelling and makes no gender claim); other spellings AntCat calls misspellings: `misspelling of Camponotus novaeboracensis`; back to the uploaded name: `Your name Crematogaster browni is valid again. It had been shown under C. cerasi (synonymy by Morgan & Mackay 2017), and was revived as a species by Ward & Blaimer 2022.` The reference is the earliest act of the current kind after the most recent contrary act, by date (not AntCat's order; 2025a before 2025b); when the old name has no history, the new name's "Senior synonym of" entry; no reference when the only one for the current state predates a contrary act (e.g. a synonymy older than a listing as a species). The same old -> new pair gets the same reason in every group; the uploaded name counts only for "valid again". A reference not found in the history is left out. `antcat_id` is the AntCat id of the taxon whose history gave the reason. One compute.log line per run, for example `name update: 214 specimens in 9 groups (apply: changed) [CAS 120, JTL 40, ...], 31 names; left alone (names): 2 name shared by two or more AntCat taxa, for the curator; ...; 0.4 min. See nameUpdates.do.`

**Page.** `nameUpdates.do`, linked from the Curate page: curators see their own group, admins all groups with a group filter and the Preview button; filter by date (from, to); newest run first; one line per old -> new pair (as shown, without subfamily or case) and group, its specimens added together, every distinct reason of them kept on the line (2026-10-20; before, rows differing in the stored taxon name or the reason were separate lines with the same names); each line "old name -> new name" with the links of the upload report (`Taxon.makeDescriptionLink`), the reason (names in italics, the reference linked to https://www.antcat.org/catalog/<antcat_id>; rows recorded before 2026-10-20 show the AntCat status of the old name instead) and the specimen codes.

**Specimen page.** Each specimen page (`specimen.do`) has a "Name History" section (`upload/SpecimenNameHistory`, called from `SpecimenAction`; road map step 6, 2026-10-21), newest first: "Filed under" the current name with the name it was uploaded as, when they differ (the reason is the upload report's, `UploadNameReasons`; when a nightly move below made the change it points to that line instead); "Needs the curator's decision" when the latest run held the specimen because its name belongs to two or more AntCat taxa and it is still filed under that name (`SharedNameReport.line` and `EXPLANATION`, the words of this page); one "Name update YYYY-MM-DD" line per applied move (`mode = 'changed'`, the line and reason of this page: `NameUpdatesAction.nameLine`, `UploadNameReasons.reasonPart`); then the previous determinations from TaxonWorks (moved there from Specimen Information). Report-only rows (`would_change`, the Preview) and the `kept_valid` list are never shown; no heading when there is nothing to show. Everything is escaped; no group or curator is named. One query by code (index `by_code`, in the table's DDL since it was created; no upgrade needed); if the table is missing the section shows only what the specimen row has. Tests: `SpecimenNameHistoryTest` (rendering), `SpecimenNameHistoryDbTest` (query, order, index), `tests/e2e/test_specimen_name_history.py`.

**Not covered.** Page text, `taxon_prop` and favourite images under an old name; the genus-level move of morphospecies of a synonymized genus (done at upload by `SpecimenUploadParse`); the list-history record of list moves.

**Tests.** `test/.../upload/SpecimenNameUpdateTest.java` (rule with a fake taxon table, decisions, cap, settings, log and page lines), `SpecimenNameUpdateDbTest.java` (MySQL 8.4 in CI: report, apply, cap, parked entries, upload lock), `tests/e2e/test_name_updates.py`.

**Count check.** `countCheck.do` (admins, read-only; `util/CountCheck.java`) recounts from the specimen records the list numbers of Madagascar, Reunion, the country with the most morphospecies, the largest live curated project, the Malagasy bioregion and the CASC museum, and lists every difference with both numbers. The end of the last completed nightly run is read from compute.log. A difference caused by a change after it (an upload of that name, a list row added, specimens moved by hand, or any upload when the stored number is higher) is "pending"; any other is "real" and fails `tests/e2e/test_count_check.py` (staging, admin test login), which lists them in the job summary. When compute.log has no completed run, the test lists the differences and skips. DB test: `test/.../util/CountCheckTest.java`. Since 2026-10-20 it also checks each sampled place's own Images, images per view and Imaged specimens against the image table, and that Images is never smaller than Imaged specimens ("place images"; see [nightly.md](nightly.md) set 4, `placeImageCounts`; DB test `test/.../home/PlaceImageCountDbTest.java`). Since 2026-10-20 the sample also has the subregion of Madagascar, counted like its page from the specimens of its countries, and each sampled country, island and subregion's own Specimens number is checked ("place specimens"; see [nightly.md](nightly.md) set 2; DB test `test/.../home/RegionCountDbTest.java`).
