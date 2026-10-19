# Uploads and the nightly run: tests, gaps, findings

Companion to [specimen-upload.md](specimen-upload.md), [image-upload.md](image-upload.md), [antcat-to-antweb.md](antcat-to-antweb.md) and [nightly.md](nightly.md). Written 2026-10-18 from master at 5b0c1d5. The proposed tests are to be built in the next pull request.

Rules for every proposed test: it runs against **staging only** (or locally, or as a unit test with no server). A test that writes data refuses to run unless the host is `staging.antweb.org`, `localhost` or `127.0.0.1` (the pattern of `tests/e2e/test_image_upload.py`). Read-only checks may also run against production. No test ever writes to production.

## 0. Built on 2026-10-19

- Staging test accounts: `tools/staging/create-test-accounts.py` (test group "ZZ Test Group (staging only)", codes `zztest0001`..., an uploader, a curator without upload rights, an admin; `--remove`). Secrets `ANTWEB_TEST_UPLOADER_*`, `ANTWEB_TEST_NOUPLOAD_*`, `ANTWEB_TEST_ADMIN_*`, sent to staging only.
- e2e (skip without the secrets): test 1 nightly health (`test_admin_staging.py`), test 2 round trip and test 3 as xfail (`test_specimen_upload_roundtrip.py`), test 10 first part, a real image upload to a test specimen (`test_image_upload_test_group.py`), test 12 "no upload right" (both files), admin pages load (`test_admin_staging.py`).
- Unit, in CI: test 5 `TaxonWorksTransformFixtureTest` (fixture `test/org/calacademy/antweb/upload/fixtures/taxonworks-data.tsv`), test 6 without the refactor (`TaxonWorksNightlyDecisionsTest`: wait decisions, zip check, fingerprint, record count, one run at a time, no settings file; the guard and HTTP steps still need the seam), test 7 the database-free half (`NameResolutionRuleTest`), and `PasswordHashCompatTest` with `tests/unit/test_staging_accounts.py`.
- New finding while writing test 5: the transform writes a value containing quote marks in CSV quoting (`"""Smith"" collection"`), and the specimen upload only trims the outer quote marks, so such a value would be stored with doubled quote marks (check on staging).

## 1. What is tested today, step by step

"e2e" = `tests/e2e/` (runs against staging for every pull request and daily at 13:17 Pacific). "unit" = `test/` JUnit. "unit, not in CI" = the test exists but `.github/workflows/tests.yaml` only compiles and runs `SecurityBatch7Test` (step "Java unit tests of the 2026-10-18 hardening batch"); the others are run by hand (`ant test`) if at all.

### Specimen upload

| Step | Existing test |
|---|---|
| `upload.do` needs a login; removed sub-actions answer "not found"; `genAll`, `allSpecimenFiles` admin-only | e2e `test_hardening_2026_10_18.py` (`test_upload_gen_all_needs_login`, `test_removed_upload_actions_*`, `test_all_specimen_files_admin_only`); e2e `test_cleanup_batch3.py` |
| Admin-only sub-action list | unit `SecurityBatch7Test.testAdminOnlyUploadActions` (in CI) |
| "Upload as" rule | unit, not in CI: `AccessChecksTest.testUploadAs` |
| Curator without "upload specimens" refused | none |
| Upload / compute / down-time / POST gates | none |
| Zip unpacking, file format checks (.txt, header, tabs) | none |
| TaxonWorks transform | unit, not in CI: `PreviousDeterminationsTest` (previous determinations column only) |
| Subfamily in parentheses | unit, not in CI: `SubfamilyParenthesesTest` |
| Delete group, insert lines, refusals, red flags | none |
| Name resolution (current valid name, "Uploaded As") | none |
| Duplicate codes across groups | none |
| Post-processing (upload row, counts, maps) | none |
| Report written and linked | e2e `test_upload_report_links.py` (links of existing reports, read-only); unit, not in CI: `UploadReportExistsTest` |
| Backup file download (`uploadFile.do`) | e2e `test_privacy.py::test_upload_file_download_requires_login`; unit, not in CI: `UploadFileAccessTest` |
| Upload-complete email | unit, not in CI: `UploadNoticeTest` (settings, texts, exclusions) |
| Nightly TaxonWorks job | none (only by watching `taxonworksNightly.log`) |

### Image upload

| Step | Existing test |
|---|---|
| Anonymous, GET, missing or wrong form token refused | e2e `test_image_upload.py` |
| Naming rules, unknown specimen, other group's image (start, dry run) | e2e `test_image_upload.py` (`test_start_checks_names`, `test_start_refuses_replacing_other_groups_image`) |
| Ownership rule | unit `SecurityBatch7Test` (in CI) |
| Backup names | unit `SecurityBatch7Test.testBackupNamesAreNew` (in CI) |
| Add (dry run), one file per request, unknown or other login's upload | e2e `test_image_upload.py` |
| Not saved files recorded and shown | e2e `test_not_saved_round_trip` (writes one empty upload record per run on staging); unit, not in CI: `ImageUploadRefusedDbTest` |
| Real upload, four sizes, report | e2e `test_real_upload_round_trip`, opt-in only (`ANTWEB_IMAGE_UPLOAD_WRITE_TEST=1`), so normally skipped |
| Browser page, several files | e2e `test_browser_image_upload.py` (Playwright, dry run) |
| ImageMagick policy | CI container step `tests/container/imagemagick_policy_check.sh` |
| Replacement: backup file exists, report link to it | none |
| ImageMagick failure reported | none |
| Email | unit, not in CI: `UploadNoticeTest.testImagesComplete*` |

### AntCat import and nightly run

| Step | Existing test |
|---|---|
| `utilData.do` / `schedule.do` need an admin | e2e `test_privacy.py::test_data_jobs_need_admin`; anonymous probes in `test_actions.py` |
| Launch time, restart switch, 210-minute rule | unit, not in CI: `NightlyScheduleTest` |
| `reboot_checker.sh` answers | none |
| Fetch, size guard, delete and reload of AntCat names | none |
| List entries moved to current valid names | none |
| Export order assumption (valid names first) | none (neither repo) |
| Sets 2 to 5 (what they compute) | none |
| Nightly ran today and finished | none (by hand: compute.log) |
| Set failure detected | none |

## 2. Proposed tests, most valuable first

| # | Test | Kind | Where it runs | Checks | Needs |
|---|---|---|---|---|---|
| 1 | **Nightly health check** | e2e read-only, admin login | staging daily; may also run read-only against production | `logMgr.do` compute.log has today's "Begin run" and "End run", no "FAILED" or "still running after 210 minutes"; `taxonworksNightly.log` and `admin.log` have a line from today; today's `web/log/worldants/*-fetchAndReloadWorldants.html` exists; `worldants_upload` newest row is today with message "success" (admin query) | an admin test login on staging (secret in GitHub) |
| 2 | **Specimen upload round trip** | e2e write | staging, test group only | upload a fixture zip (5 lines) as the test curator: one valid name, one synonym (expect filed under the valid name, report "Recognized invalid species", specimen page "Uploaded As"), one unknown name (kept, "Unrecognized"), one bad code (refused), one out-of-bounds coordinate (red flag). Then upload a 2-line baseline file and check that the other 3 specimens are gone (replacement), the upload row, report link and `listSpecimenUploads.do` row exist. Ends with the group back at its baseline | a staging test group and curator with upload permission and its own code prefix (e.g. `ANTWEBTEST0001`); fixture files in `tests/e2e/fixtures/` |
| 3 | **All lines refused must not empty the group** | e2e write | staging, test group | upload a header-only file and a file whose every line has a bad code; expect the baseline specimens still there. Fails today (section 3, bug 1): mark `xfail` until fixed | as 2 |
| 4 | **Run every JUnit test in CI** | CI | GitHub Actions | compile and run all of `test/` (not only `SecurityBatch7Test`): `UploadNoticeTest`, `NightlyScheduleTest`, `AccessChecksTest`, `UploadFileAccessTest`, `PreviousDeterminationsTest`, `SubfamilyParenthesesTest`, `UploadReportExistsTest`, `ImageUploadRefusedDbTest`, ... | none |
| 5 | **TaxonWorks transform fixture** | unit | CI | a small `data.tsv` (6 rows) through `TaxonWorksTransformer.transformFile()`: column renames, `eventID:` prefix removed, date range split, CAS to CASC, elevation range, one row with a wrong column count in `getSkippedRecords()`, an empty catalogNumber dropped, a missing required column gives a message (and the "null" prefix bug, section 3 bug 12) | fixture file |
| 6 | **TaxonWorks nightly decisions** | unit | CI | with a local fake HTTP server or a small seam for the URL and settings path: no settings file, HTTP 422 (waiting), unchanged archive (same SHA-256), guard (fewer than 95%), another upload running, success writes the SHA file; `isWaitingForTaxonWorks()` | small refactor: settings path and clock injectable |
| 7 | **Name resolution decision** | unit | CI | the per-name rule of `setStatusAndCurrentValidName()` against a small fake taxon table: synonym to valid, obsolete combination, misspelling, valid kept, unknown kept, morpho and indet kept, homonym placeholder, valid name without taxon row (section 3 bug 5) | extract the decision into a method that takes a lookup (also needed by the nightly name update) |
| 8 | **Scheduler notices a failed set** | unit | CI | a set answer "Error. No changes made ..." stops the run and writes "FAILED" to compute.log. Fails today (bug 36) | a seam for `HttpUtil.fetchUrl` |
| 9 | **AntCat import invariants** | e2e read-only, admin queries | staging and production | number of `taxon` rows with `antcat = 1` close to the number of lines in today's export (from `worldants_upload`); no `proj_taxon` / `geolocale_taxon` / `bioregion_taxon` / `museum_taxon` row (outside `worldants`, `allantwebants`) on a non-valid name that has a current valid name; synonyms present (count of `status = 'synonym'` above a floor), which catches a change of export order | admin queries in `util/Queries.java` (new named queries) |
| 10 | **Image replacement and failure** | e2e write (opt-in, as `test_real_upload_round_trip`) | staging | upload the same test image twice: second answer "replaced", a dated backup exists under `images/backup/`, the report's "Duplicate" link opens it (fails today, bug 24); a corrupt TIFF is reported as not saved (fails today, bug 21); `CODE_Z_91.JPG` is refused (fails today, bug 23, also checkable with dry run) | confirm staging's images bucket is not production's |
| 11 | `reboot_checker.sh` | shell unit | CI | a fake `curl` and `docker`: `<b>true</b>` and `case#` restart, `<b>false</b>` and empty do not; `--dry-run` never restarts | none |
| 12 | Upload gates | e2e write-probe | staging | curator without upload permission gets "does not have permission"; non-zip `.txt` with a `/` in the header, a file without tabs: refused with the message and the group unchanged; GET `upload.do?action=specimenUpload` refused | test curator without upload permission |
| 13 | One nightly set by hand with count assertions | e2e write, slow, weekly | staging | as admin POST `utilData.do?action=set4` (images, about 15 minutes) or `crawlForType`: page answers "Finished ...", counts on a known taxon and country page unchanged or plausibly changed, compute.log line written. Set 2 and 3 take too long for the PR run; a weekly workflow | admin test login; a separate scheduled workflow |
| 14 | Invalid Column stops part way | e2e write | staging, test group | a file whose third line has an "other" column that is not valid XML: today lines 1 and 2 stay, the rest are lost (bug 3); expected after the fix: the line is refused, the others load | as 2 |
| 15 | Export order on the AntCat side | RSpec (antcat repo) | AntCat CI | `Exporters::Antweb::Exporter` writes every valid taxon before any taxon whose current valid name points to it | AntCat PR |

Top 10 for the next PR: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10.

## 3. Bugs, dead branches and suspicious behaviour

Security-relevant findings are not listed here; they are kept privately and handled separately.

### Specimen upload

1. **A file whose lines are all refused empties the group.** `SpecimenUpload.importSpecimens()` deletes the group's specimens first (line 102), throws "No records processed" (line 216) and catches it itself (line 243); `UploadAction` then commits (line 550). The report heading says "Rollback occurred" (`MessageMgr`), which is not true. Same for the nightly TaxonWorks job: its 95% guard counts lines in the file, not lines loaded. Fix: throw out of `importSpecimens()` (or check `processLineTotal`) so `UploadAction.handleException()` rolls back; add a guard on lines loaded.
2. The nightly TaxonWorks guard (95%) counts the lines of the converted file, not the lines that load (see 1); and a real shrink of more than 5% (CAS records deleted in TaxonWorks on purpose) is refused every night until someone lowers `minRatio` in `etc/taxonworks.properties` on the server; the log line is the only sign.
3. **"Invalid Column" stops the upload part way.** `SpecimenUploadParse.parseLine()` throws at line 603; the loop ends; earlier lines stay, later lines are lost; committed.
4. **A plain `.txt` (not zip) specimen upload cannot work.** `SpecimenUploader.uploadSpecimenFile()` line 52 passes an absolute path (`workingDir + "specimen<id>.txt"`) to `Uploader.uploadSpecimenFile()`, which prefixes `workingDir` again (line 61 of Uploader, `specimenFileLoc = workingDir + fileName`), so the format check reads a file that does not exist. The page only offers zip, so curators do not see it; the developer hint on the Curate page ("To test ... file:20250209allantsbiota.txt") would fail. Check on staging.
5. **A specimen can point to a name with no taxon row.** In `setStatusAndCurrentValidName()`, when the name uses a current valid name, that valid name has no taxon row and is not a homonym (lines 512 to 560), the specimen is still filed under the valid name. Check with a query on staging.
6. A processed line whose code is a duplicate (refused in `saveSpecimen()`) still counts as "Processed" and still creates or updates its taxon row (`processLine()` line 335).
7. Future collection dates are reported under "Date Determined is in the future" (`AntwebUpload.validDate()` line 1011); the heading "Date Collected Start is in the future" is never used.
8. `SpecimenUploadDb.dropSpecimens()` (line 81) swallows SQL errors: if the delete fails, the upload goes on and every line is refused as a duplicate.
9. `SpecimenUploadDb.updateUpload()` (line 140) builds its insert by string concatenation: a group name with an apostrophe makes it fail silently, and the upload then has no `upload` row (no report link, not listed). Same pattern in `WorldantsUploadDb`.
10. `SpecimenUploader.copyAndUnzipFile()` takes the **last** file of the unzipped folder listing; a zip with two files or a folder gives an arbitrary or failing choice. It also calls `new File(groupName).mkdir()` relative to Tomcat's working directory (unused).
11. `UploadAction` line 256 (`if (abort) AntwebUtil.returnMessage(...)`, result ignored; dev only) and the unused `theFileName` variables (lines 251, 261, 269): dead.
12. `TaxonWorksTransformer.passFieldCheck()` (line 226) starts its message from `null`, so the error reads "nullTW:Internal:otu_name".
13. `GBIFUploader.getZipFileTarget()` returns "occurence.txt" (misspelt) and is never used.
14. `Status.usesCurrentValidName()` includes "unavailable uncategorized", but the AntCat import never loads that status (`Status.excludeFromImport()`), so that branch can never match.
15. The "Create a New Ancillary Page" form on the Curate page (admin, `curate-body.jsp` around line 413) posts to `upload.do`, but nothing reads `ancFileDirectory`: it writes an empty "undefined" upload report and shows the upload results page. The feature is dead.
16. `upload.do` sub-actions in `ADMIN_ONLY_ACTIONS` that do nothing: `reloadSpeciesLists` (code commented out), `toggleDownTime` (handled by `serverStatus.do`), `runCountCrawls` (logs "should be done through UtilData.do"); all answer "Action not found." Candidates for removal from the list and the catalog.
17. `upload.do?action=speciesTest` reads `/Users/mark/dev/calAcademy/workingdir/worldants.txt` (a developer's Mac path) and `specimenTest` reloads the last file of a login called `testLogin`: developer leftovers; candidates for removal.
18. The "Upload a Data File" branch (`uploadDataFile()` line 743: AntWiki valid and fossil lists, synonyms, Bolton NGC, specimen list) has no form on any page since issue #33; only the specimen list function might still be wanted.
19. `UploadAction` checks the upload flag (line 165) and sets it (line 192) in two steps; two uploads arriving at the same moment could both start.
20. The upload email goes to the logged-in uploader, not to the "Upload as" curator whose data it is. Intended? (question 4)

### Image upload

21. **ImageMagick failures are silent.** `ImageUploaded.genImage()` (line 277) only logs `CommandException` and others; `ImageUploadBatchAction.add()` ignores the result of `genImages()` and answers "saved". A corrupt or oversized TIFF (policy limits: 256 megapixels, 300 s) leaves an image row with missing or stale sizes.
22. **Image row insert failures are silent.** `ImageDb.insertImage()` (line 352) catches `SQLException` and logs it; the file still counts as saved.
23. **Shot letter not checked when a number is given.** `ImageUploaded.init()` checks `SHOT_TYPES` only for `CODE_SHOT.ext` (line 229); `CODE_Z_1.JPG` is accepted with shot "z".
24. **Report "Duplicate" link is broken** since backups get a dated name: `imageUploadReport-body.jsp` line 177 links `/images/backup/<file name>`.
25. **Resize box swapped (check).** `genImage()` calls `op.resize(height, width)` (line 287); im4java's `resize` is (width, height), so "thumbview 400x300" is made in a 300x400 box, "high 2000x1600" in 1600x2000. Possibly what the old system did; to confirm against existing images.
26. Replacing an image deletes its `image` row and inserts a new one with a new id (`ImageDb.putImage()` line 314): likes (`image_like.image_id`) of the old image are orphaned. Only the original file is backed up; the four sizes are overwritten. A replacement with a different extension (old `.jpg`, new `.tif`) leaves the old original in place.
27. A curator may add a new image (not a replacement) to another group's specimen; the image is then recorded under the uploader's group. Intended? (question 5)
28. Image uploads are not paused during the nightly sets (no `Check.compute`), unlike specimen uploads.
29. `image.id` is `max(id) + 1` (`ImageDb.insertImage()`), serialised only within this JVM by `ImageUploadGate.DB_LOCK`.

### AntCat import

30. **The import relies on the export's sort order** (valid names first; `exporter.rb` `order(:status)...reverse`). A synonym whose valid name comes later in the file is skipped (`SpeciesListUpload` line 683). Undocumented on both sides.
31. **The AntCat import is committed only as a side effect** of `WorldantsUploadDb.insertWorldantsUpload()` / `updateWorldantsUpload()` calling `commit()`.
32. **No size guard on staging** (`validateWorldantsFile()` line 124 returns "success" in dev and stage mode).
33. `importSpeciesList()` deletes AntCat `bioregion_taxon` rows (line 153) **before** it repeats the size check (line 170); for `worldantsReload` with a bad file this leaves the bioregion lists without AntCat rows, and the transaction is committed.
34. The fetch writes over `worldants_speciesList.txt` before the file is validated; the export side copies (not moves) its file into place. No date check: a failed export is loaded again unnoticed (the weekly "no changes" admin alert is the only sign).
35. The set 1 fetch failure is not added as an admin alert in the nightly path (only the Curate-page button does that).

### Nightly run

36. **Failed sets go unnoticed.** `Scheduler.doAction()` stops only when a set's answer contains "Unexpected error" (lines 205 to 240); that text appears nowhere else in the code. A set that fails answers "Error. No changes made ..." (`UtilDataAction.handleException()`), the run continues, and nothing reaches compute.log.
37. **Sets 2 to 5 ran on only 3 of 8 nights** in the production traffic sample of 2026-10-11 to 10-18 (set 1: 8). Cause unknown; see question 1.
38. **Pool timeout.** `longConPool` (`unreturnedConnectionTimeout` 3600 s) destroys a connection held for more than an hour. Set 1 holds one connection through the AntCat import, up to 90 minutes of TaxonWorks waiting (sleeping with the connection checked out) and the upload. Check compute.log and the Tomcat log for nights the TaxonWorks job waited more than about 45 minutes.
39. **Watchdog during the run.** The hourly host watchdog (not in the repo) restarts the container when a thread is above 95% CPU twice in 30 s and the container has been up 12 hours; set 2 and set 5 are long CPU-heavy loops and run at 05:15, 06:15, 07:15.
40. `UtilDataAction` does not refuse a set while another compute is running and clears the shared compute flag when it ends; a manual run during the nightly lets curator uploads start mid-run. `Scheduler.execute()` creates a new secure code before it checks whether a run is in progress, and its `finally` clears the code and the flag, which breaks a running nightly (admin action only).
41. The nightly does not check for a curator upload in progress before the AntCat import (only the TaxonWorks step checks), so the import can run alongside a curator upload that started just before 5:00.
42. `dataCleanup` overwrites its first message (`UtilDataAction` line 350: `message = ...; message = ...`); `generateGeolocaleTaxaFromSpecimens` builds a message starting with "null".
43. `UtilDataAction.doAction("set1")` appends an `OperationDetails` object to a String (`message += doAction(...)`), so the set's answer contains "{operation:...}" instead of the step result.
44. `AntwebMgr.populate()` (end of set 5) increments the specimen upload id as a side effect.
45. The dev-mode branches and `if (false && ...)` blocks throughout `UploadAction`, `SpecimenUpload`, `SpecimenUploadParse`, `SpecimenUploadProcess` and `Scheduler` are dead code.

## 4. Feature backlog candidates

To be done after this area is documented and tested; each one needs Brian's go-ahead.

1. **Nightly current-valid-name update for all specimens** (approved 2026-10-18): after the AntCat import, apply the upload's name rule to every existing specimen, record each change in a new table `specimen_name_update` (run date, mode, code, group, uploaded name, old, new, AntCat status), show them on a "Name updates" page under Curate (admins all groups, curators their own), one compute.log line per night, switch `etc/name-update.properties` (`mode=off|report|apply`, `maxChanges=`). First run on staging in report-only mode. Design: project notes "name-updates-and-species-lists.md" section 5.
2. Specimen upload safety: refuse (and roll back) an upload that loads no line, or far fewer lines than the group has now (the same 95% idea as the TaxonWorks guard, with an admin override); make "Invalid Column" refuse one line, not stop the file.
3. Specimen upload preview ("dry run"): check a file and show the report without changing anything.
4. Nightly status page or email: one admin page (or a morning email to Brian) with each set's start, end, result and the TaxonWorks and AntCat lines, built from compute.log and the Scheduler's answers; make the Scheduler record failures (bug 36).
5. Resume a nightly run after a restart, or run each set on its own short-lived connection, so a restart or pool timeout does not lose the rest of the night.
6. Image uploader: report ImageMagick and database failures per file; keep the backup link in `image_uploaded`; keep image ids (and likes) on replacement.
7. Freshness check on the AntCat file (refuse a file older than a day, add an admin alert), and an atomic move on the export side.
8. Record list moves to current valid names in the species-list history, so curators see why an entry changed.
9. Move `antweb-watchdog.sh` into the repo (`bin/`), skip it while a nightly set or an upload runs.
10. Remove the dead `upload.do` sub-actions and the "Upload a Data File" and "Ancillary Page" leftovers (bugs 15 to 18), after Brian confirms no one uses them.

## 5. Questions only Brian can answer

1. Sets 2 to 5 ran on only 3 of 8 nights in the 10-11 to 10-18 traffic sample. Please run on antweb-prod (`ssh root@165.232.137.86`): `docker exec antweb_antweb_1 grep -E "Begin|End|FAILED|still running|skipped" /usr/local/antweb/web/log/compute.log | tail -60` (read-only; it prints the start, end and failure lines of recent runs; `logMgr.do` shows the same file), plus `tail -20 /var/log/antweb-watchdog.log` on the host, so we can see which nights stopped and why.
2. Is staging's `/mnt/antweb` (images, uploads, reports) a separate Spaces bucket from production's? The write tests (specimen upload round trip, image replacement) must not touch production files.
3. Can we have, on staging only, a test group with its own specimen code prefix, a test curator with "upload specimens" and "upload images", a second test curator without upload rights, and an admin test login for the read-only nightly check?
4. Should the upload-complete email go to the curator the upload was made **for** ("Upload as"), the uploader, or both?
5. Should a curator be able to add new images to another group's specimens (allowed today; replacing them is not)?
6. The image sizes: is the box for "high" meant to be 2000 wide x 1600 high (what the code's names say) or 1600 wide x 2000 high (what it actually makes)?
7. Curator-made species names that are neither AntCat names nor used by any specimen are deleted from `taxon` every night (set 2 `dataCleanup`), and their species-list rows with them. Is that intended for names a curator added to a list on purpose (for example a species described in a paper not yet in AntCat)?
8. Are `upload.do?action=GBIFUpload`, `museumCalc` ("Recalculate Your Museums") and the "Upload a Data File" functions still used by anyone?
