# Specimen upload: curator file, TaxonWorks (manual and nightly)

Read from the code on master at commit 5b0c1d5 (2026-10-18); the background run and status page (sections 3 and 3a, 6, 7.3, 7.4) from the background-jobs change of 2026-10-20, where line numbers are not given. Paths are relative to the repo root; Java classes are under `src/org/calacademy/antweb/` unless a full path is given. Line numbers are those of that commit. "Verified" means read in the code; "check" means the code suggests it but it should be confirmed on staging or in the database.

## In plain words

A curator uploads one file that holds **all** of their group's specimens. AntWeb first deletes every specimen of that group, then reads the file line by line and inserts each record again. So an upload always replaces the group's whole dataset; there is no "add these few records" upload.

Each line's name is checked against the AntCat names AntWeb loaded that morning. If the curator's name is an old name (a synonym, an obsolete combination, a misspelling), the specimen is filed under the current valid name and the curator's name is kept as "Uploaded As" on the specimen page. Names AntCat does not know are kept as they are and listed in the report.

Lines that cannot be read (bad specimen code, wrong number of columns, impossible coordinates, a code another group already uses, and so on) are left out and listed in the upload report under headings marked "(not uploaded)". The report is a web page under `/web/log/specimen/`; the curator gets an email when the upload has finished (not for CAS and not for the nightly TaxonWorks job).

The upload runs in the background (since 2026-10-20). The upload page saves the file, checks it (permission, file type, the zip's content) and answers at once with the upload's **status page** (`jobStatus.do?id=N`), which shows what the upload is doing and reloads itself every 5 seconds until it is complete; the result and the report link are shown there, and the email is sent as before. Uploads run one at a time: a second curator's upload waits in the queue ("Waiting for another upload to finish") instead of being refused. While an upload of a group (or by a login) is waiting or running, a second one of that group or by that login is not started: the curator is sent to the first one's status page. See section 3a.

CAS does the same through TaxonWorks: every night at about 5 AM AntWeb downloads the CAS TaxonWorks export, converts it to an AntWeb specimen file and uploads it as Brian's login. It refuses to upload when the new file has fewer than 95% of the specimens CAS has now.

Counts, maps and region lists that depend on specimens are only partly updated at upload time; the rest is recomputed by the nightly run (see [nightly.md](nightly.md)).

Two things worth knowing (details under "What can fail"): if not a single line of a file can be loaded, the group ends up with **no** specimens (the report says "Rollback occurred" but nothing is rolled back); and an "Invalid Column" error in the "other" columns stops the upload part way, keeping only the lines before it.

---

## 1. Entry points

| Way in | URL / caller | Code | Who |
|---|---|---|---|
| Curator upload page | `curate.do`, form "Upload Zip File" (`web/curate/curate-body.jsp` lines 170 to 265), POST to `upload.do` with `action=specimenUpload` | `upload/UploadAction.execute()` line 107 | Curators with "upload specimens" (`Login.isUploadSpecimens()`), and admins |
| Manual TaxonWorks upload | same form, `action=taxonWorksUpload` (the Upload Type dropdown is shown to admins only, line 199) | same, line 260 | same permission (the server accepts the action from any curator who may upload specimens) |
| Manual GBIF upload | same form, `action=GBIFUpload` | same, line 268 | same; catalog status "check" |
| Nightly TaxonWorks job | nightly set 1 (`util/UtilDataAction.doAction` line 222), or an admin at `utilData.do?action=taxonWorksNightly` (line 285) | `upload/TaxonWorksNightly.run()` line 81 | the scheduler (secure code) or an admin |

Struts mapping: `WEB-INF/struts-config.xml` line 482 (`/upload`, form `UploadForm`).

`upload.do` sub-actions removed in 2026-10 and answered "Action not found." before anything else: `reloadSpecimenList`, `removeSpecimenList`, `uploadSpeciesList` (`UploadAction.REMOVED_ACTIONS`, line 52).

## 2. File formats

### 2.1 AntWeb specimen file (`specimenUpload`)

- The page accepts only a `.zip` (browser check `validateZipUpload()`, `curate-body.jsp` line 147). The zip is unpacked with the system `unzip` into `workingDir/group<id>/`; the **last** file in the folder listing whose name has no `__` is taken (macOS `__MACOSX` entries are skipped) and copied to `workingDir/specimen<groupId>.txt` (`SpecimenUploader.copyAndUnzipFile()` line 65). `workingDir` is `/usr/local/antweb/workingDir/` inside the container (`AntwebProps.getWorkingDir()`), not on Spaces.
- Plain text, UTF-8 (the encoding chosen in `UploadFile.figureEncoding()` is always UTF-8), tab-delimited, first line is the header.
- Checks before anything is deleted (`Uploader.uploadSpecimenFile()` lines 72 to 83): file name contains `.txt`; the header has no `/` and does not contain "taxonomic history" (`isCurrentSpecimenFormat()` line 116); the header contains a tab (`Utility.isTabDelimited()`). A failed check shows a message and changes nothing.
- Header names are matched case-insensitively after trimming. Recognised columns and the database column they go to: `SpecimenUploadParse.buildHeaderArrayList()` line 1007 and `buildColumnTranslations()` line 1092. Notable translations: `SpecimenCode` to `code`, `[Species]Genus` / `SpeciesGenus` to `genus`, `SpeciesName` to `species`, `TypeStatus` to `type_status`, `LifeStageSex` to `caste` (the text is kept in `life_stage`), `LocXYAccuracy` to `latlonmaxerror`, `CollXYAccuracy` to `collectionnotes`, `DNANotes` to `dnaextractionnotes`, `previousDeterminations` to `previous_determinations`. `LocLatitude`, `LocLongitude` and `Elevation` are converted (decimal degrees, a number of metres). Any other column is stored in the specimen's `other` XML and listed in the report as "Unrecognized Column (ignored)".
- Required in practice: `SpecimenCode` (a line without a code is refused), and enough taxonomy to build a name. `Subfamily` may be empty: it is taken from the genus when AntWeb knows the genus. Empty species becomes `(indet)`; empty genus becomes `(<subfamily>)`. The data requirements sheet given to curators (AntWeb_specimen_data_requirements_2026v1.xlsx) lists the recommended columns.

### 2.2 TaxonWorks Darwin Core archive (`taxonWorksUpload`, nightly)

- A DwC-A zip; only `data.tsv` inside it is used (`TaxonWorksUploader` line 38, `prepareFromArchive()` line 94).
- `TaxonWorksTransformer.transformFile()` (line 171) rewrites it as an AntWeb specimen file `workingDir/specimenTW<groupId>.txt`: column renames in `directHeaderTranslations` (line 36), e.g. `catalogNumber` to `SpecimenCode`, `genus` to `[Species]Genus`, `specificEpithet` to `SpeciesName`, `TW:Internal:collection_object_id` to `taxonworks_co_id`; generated columns: date range split from `eventDate`, `collectioncode` from `fieldNumber` (prefix `eventID:` removed), elevation from min/max, `ownedby` (CAS becomes CASC), `previousDeterminations` from `previousIdentifications` when present.
- Required TaxonWorks columns: every left-hand column of `directHeaderTranslations` plus `TW:Internal:otu_name`, the three `TW:DataAttribute:CollectingEvent:` Country/adm1/adm2 columns (`passFieldCheck()` line 226), `eventDate`, `fieldNumber`, `country`, `stateProvince`, `county`, `minimumElevationInMeters`, `maximumElevationInMeters`, `institutionCode`, `taxonRank`. A missing one stops the transform with a message ("transformFile() record N: ...").
- Records whose column count differs from the header are left out (`skippedRecords`); the nightly job lists them in `workingDir/taxonworksSkipped.txt` and in its log line. Rows with an empty `catalogNumber` are dropped silently.
- After the transform the file goes through exactly the same steps as an AntWeb specimen file (section 3).

### 2.3 GBIF archive (`GBIFUpload`)

Same shape: `occurrence.txt` from the zip, `GBIFTransformer`, then section 3. Not used in the last 8 days of production traffic (catalog).

## 3. Step by step (all three upload types)

Numbers are the order in which things happen for `upload.do?action=specimenUpload`.

1. **Gates, in the request** (`UploadAction.execute()`):
   - login, curator or admin, "upload specimens" permission;
   - `AntwebMgr.isPostInitialized()`: not during the 2 to 4 minutes after a restart;
   - POST only. The CSRF token is checked earlier by `SessionRequestFilter`.
   - Not a gate any more: another upload running or the nightly compute running. The upload waits in the queue (step 1c).
1b. **Save and answer** (`UploadAction.submitSpecimenUpload()`): the effective curator (step 3); if an upload of the same group or by the same login is waiting or running (`BackgroundJobs.Runner.findDuplicate()`), a redirect to that upload's status page with `&already=1`, nothing saved. Otherwise the file is saved as `workingDir/jobs/upload-group<id>-<random>.zip` (or `.txt`), a zip's list of entries is checked (`SafeUnzip.check()`: the same rules as the unpacking in step 4, nothing is unpacked yet), and refusals are shown at once as a message. The job is recorded (table `background_job`) and the answer is a redirect to `jobStatus.do?id=N`. A small file answers in a second or two; the time is that of receiving the file.
1c. **Queue** (`BackgroundJobs`): one thread runs the jobs one at a time, in order (uploads and the admin data jobs started from pages). A job waits ("Waiting for ...", shown as its step) while AntWeb is starting, while the nightly run or another data job runs (`UtilDataAction.isInComputeProcess()`, `Scheduler.isRunning()`), and while another upload holds the upload flag (an image upload, the nightly TaxonWorks upload). The "upload in process" flag (`UploadAction.s_isInProcess`, shared with the image uploader and the nightly TaxonWorks job) is then taken with `claimUploadProcess()`, only when free. The rest runs in `SpecimenUploadJob.run()`; the down time switch in table `server` (`ServerDb.isDownTime`) is checked there (the upload then ends as "did not complete" with the down time message).
2. **Upload ID**: `AntwebMgr.incrementSpecimenUploadId()` (line 202). This number is written into every inserted specimen (`specimen.upload_id`) and into the `upload` table row.
3. **Effective curator**: the login, or the curator chosen in "Upload as" if `isAllowedUploadAs()` (line 68) allows it (admins, or ids listed in the login's `upload_as`). The specimens belong to that curator's group.
4. **Copy and check the file**: section 2. Nothing has been deleted yet.
5. **Pre-scan** (`LineNumMgr.init()`): deletes this group's rows in `upload_line`, then marks lines whose column count is far below the header's as "Extra Carriage Return" (they are skipped) and stores them in `upload_line` for the report link.
6. **Delete the group's data** (`SpecimenUpload.doPreliminaries()` line 66, `SpecimenUploadDb.dropSpecimens()` line 81): `delete from specimen where access_group = ?` and `delete from taxon where access_group = ? and insert_method in ('addMissingGenus','homononymMirroringTaxon')`. Logged to `web/log/taxonSet.log`. Images (`image`, `group_image`) are **not** touched.
7. **Each line** (`SpecimenUpload.importSpecimens()` line 71):
   - **Parse** (`SpecimenUploadParse.parseLine()` line 70). Refusals at this step (line not loaded, listed in the report): whitespace inside subfamily/genus/species/subspecies (line 140); a specimen code with characters other than letters, digits and `. _ : / ( ) # + -` (line 187; spaces are removed and reported); latitude outside -90..90 or longitude outside -180..180 (lines 229, 249); more columns than the header (line 298); a genus AntWeb does not know in an ant subfamily, unless it is a morpho or indet name (line 424); a species name with quotes or a space inside the subspecies (line 448); no name could be built (line 659).
   - Taxonomy cleaning during parse: a subfamily written as `(Name)` loses the parentheses (line 339); the subfamily is replaced by the genus's subfamily in AntWeb (reported as "Preferred Subfamily For Genus"); a synonymised genus is handled per GitHub #95 (line 359): names AntCat knows keep the old genus for step "name resolution" below, morphos and indets move to the valid genus ("Synonymized genus replaced"); `sp.` or empty species becomes `(indet)`; `species subspecies` in one column is split.
   - Geography during parse: a missing country is guessed from `adm1` if possible; country and adm1 names are mapped to AntWeb's valid names (reported "Not Antweb valid countries", "Not valid Antweb adm1" with a link to `adm1Mgr.do`); the bioregion is taken from the country or adm1 and overrides the file's value ("Corrected Biogeographic Regions"); islands become their island country.
   - Locality and collection codes during parse: see section 3b.
   - Coordinates outside the country's or adm1's bounding box: the specimen **is** loaded but marked `flag = 'red'` (lines 709, 717): "Red Flag", hidden from most AntWeb services until fixed.
   - **Process** (`SpecimenUploadProcess.processLine()` line 45). Refusals: no code (line 83); empty subfamily or genus; a subfamily that is not an ant subfamily for family Formicidae (line 148); a genus filed under a different subfamily than AntWeb's and no AntWeb subfamily found (line 213). A genus/subfamily pair AntWeb does not have but which is not contradicted is created as a taxon with `insert_method = 'addMissingGenus'` (line 246) and reported "not found in Bolton World Catalog (uploaded)".
   - **Name resolution** (`setStatusAndCurrentValidName()` line 431, called at line 270). See section 4.
   - Introduced / outside native range: `TaxonPropMgr.isIntroduced()` sets `is_introduced = 1`; a genus outside its native bioregions gets a red flag ("Genus found outside of known native biogeographic region").
   - **Save** (`AntwebUpload.saveSpecimen()` line 743): if the code already exists in `specimen` (any group, including a duplicate line in the same file) the line is refused as "Duplicate Entries (not uploaded)" (line 758). Dates: only a plain `yyyy-MM-dd` result goes into the date columns; the text is always kept in `datecollectedstartstr` etc.; future dates are refused for the date column. Then `saveTaxon()` inserts or updates the taxon row for the name (source = file name, `insert_method = 'specimenUpload'`).
8. **After the last line** (`importSpecimens()` lines 211 to 280):
   - if no line was processed: `AntwebException("No records processed")`, caught at line 243 and only written into the report (see section 7.1);
   - report sections: bad ranks, multiple bioregions for non-introduced taxa (link to `list.do?action=multiBioregionTaxaList`), morpho genera (`compileMessages()` line 394);
   - orphaned species of this group's source (`specimen<groupId>`) are deleted unless they have page text, but only if fewer than 100 would go; otherwise the report gets "Warning. Please check the inserted specimens count to verify that the full file was uploaded." (`OrphansDb.deleteOrphanedSpeciesFromSource()`);
   - `ProjTaxonDb.regenerateAllAntweb()` (the `allantwebants` project list, rebuilt from the taxon table);
   - `TaxonMgr.populate()` (reload the in-memory taxon cache);
   - `ant_group.last_specimen_upload = now()`.
9. **Backup copy of the file** (`UploadFile.backup()`): `web/upload/<yyyyMMdd-HH:mm:ss>-specimen<groupId>.txt` (for TaxonWorks `-specimenTW<groupId>.txt`). `web/upload` is on Spaces (`/mnt/antweb/web/upload`). Not served directly: curators download it through `uploadFile.do?file=` (link in the report).
10. **Post-processing** (`UploadAction.postProcessSpecimenUpload()` line 641, also used by the nightly job):
    - `specimen.status` copied from the taxon (`SpecimenDb.updateSpecimenStatus(groupId)`);
    - `ant_group.upload_specimens` = count (`GroupDb.updateUploadSpecimens`);
    - the group's map (`ObjectMapDb.genGroupObjectMap`);
    - one row in `upload` (`SpecimenUploadDb.recordUpload()`: upload id, login id of the effective curator, group, report file name, backup file; bound values, the group name cut to the width of its column (100 since 2026-10-23, 64 before; read on every upload), file names refused when longer than their 64-character columns, checked after the insert; a failure throws and the upload rolls back, since 2026-10-22) and its counts (`UploadDb.updateCounts`), `ant_group.first_specimen_upload` and `upload_count` (`UploadDb.updateGroup`). The job's "Upload ID N", the email and the report use the id of that row;
    - one row in `statistics` (`StatisticsDb.populateStatistics`, site totals);
    - the museums found in the file are kept in the session for the "Recalculate Your Museums" button (`museumCalc`).
    - then for the whole site (`specimenPostProcessGlobal()` line 673): `GeolocaleDb.calcEndemic()` and `TaxonDb.removeIndetWithoutSpecimen()`.
11. **Report** (`UploadDetails.finish()` line 248, `genUploadReport()` line 93): written to `web/log/specimen/<yyyyMMdd-HH:mm:ss>-<operation>.html` (folder chosen by `getLogDir()` line 279: operations containing "specimen", "taxonWorks" or "GBIF"). Public URL `/web/log/specimen/...`; also reachable as `uploadReport.do?uploadId=` (redirect) and listed by `listSpecimenUploads.do?groupId=`. Each line of a section that names taxa or problems ends with "N specimens" and a collapsed list of the specimen codes it concerns (since 2026-10-21; `util/SpecimenCodes`, the same markup as the Name updates page): linked to the specimen pages, plain codes under headings marked "(not uploaded)". The codes are collected while each line of the file is read (`MessageMgr.setCurrentCode()`, set by `SpecimenUploadParse` when the code column is read; every message added meanwhile records it), the morpho genera's in one query after the file is read. At most 200 codes per line ("... and N more") and 10,000 per report (later lines give the count only), so a CAS report grows by at most about 0.9 MB. Sections whose lines already give the code or line number (bad codes, coordinates out of bounds, empty names and the like) get no list.
12. **Commit** (`SpecimenUploadJob.run()`). Everything from step 6 to 11 is one database transaction (`connection.setAutoCommit(false)`). An exception anywhere: rollback and "Error. Case number:N. No changes made." (the status page shows "AntWeb hit an unexpected problem (case N) and made no changes. ...").
13. **Finally** (`SpecimenUploadJob.run()`): the upload flag is released, `UploadMgr.populate()` reloads the upload list, the connection is closed, the email is sent (section 6) and the saved file in `workingDir/jobs/` is deleted. The job's status (finished or failed), result and report link go to the status page and the table `background_job`.

Steps the status page shows while the upload runs ("Now: ..."): "Checking the file (format, columns, encoding).", for TaxonWorks "Converting the TaxonWorks file to an AntWeb specimen file.", "Removing the group's current specimens (the file replaces them).", "Reading and saving specimens: line N of M." (every 500 lines), "All lines read. Checking names and updating species lists.", "Keeping a copy of the uploaded file.", "Updating the group's counts, map and upload record.", "Updating site-wide lists (endemic species, unused indet names).", "Writing the upload report.".

## 3b. Locality and collection codes

A specimen's `LocalityCode` (`specimen.localitycode`) groups the specimens of one collecting site: the locality
page `locality.do?code=<code>`, the map popups, the "Show All Specimens" search. `CollectionCode`
(`specimen.collectioncode`) does the same for a collecting event (`collection.do?name=<code>`).

During parse (`SpecimenUploadParse.cleanCollectionCode()` and `cleanLocalityCode()`, which calls
`plainLocalityCode()`):

- `UploadUtil.cleanCode()` removes spaces, `%`, `#` and apostrophes (straight `'` only). Unchanged since before
  2026; changing it would change every code at the group's next upload.
- A locality code that is missing, `Null` or shorter than 5 characters, on a specimen with coordinates, becomes
  `loc` (or the short code) followed by `lat,lon`, for example `loc-18.91,47.52`. A collection code that is
  missing gets a random `tc<number>`.
- **A double quote becomes `-`** (since 2026-10-23, `UploadUtil.quotesToDash()`, called first in `cleanCode()`,
  so it applies to locality and collection codes). `""` (a spreadsheet export doubles a quote) and `"` both become
  one `-`: `KM55""805` and `KM55"805`, the same place stored as two codes, are both `KM55-805`. The upload report
  lists each code changed this way ("Locality or collection code with a double quote", `MessageMgr.codeQuoteReplaced`).
  Decision: Brian Fisher, 2026-10-23 (the Economo Ant Lab, whose codes these were, could not be reached).
- **Otherwise stored as written** (since 2026-10-22). Every other character is kept: `<`, `>`, `?`, `,`, `/`,
  accented letters. Until 2026-10-22 the locality code was then URL-encoded (`HttpUtil.encodePath`, ISO-8859-1):
  `Ahi_<5m` was stored as `Ahi_%3C5m`, `MontagnedesFrançais_150` as `MontagnedesFran%E7ais_150`, and a character
  outside ISO-8859-1 (a curly apostrophe) as `%3F`. A character outside the Basic Multilingual Plane (an emoji) is still stored as `?`:
  the column is `utf8mb3`. On 2026-10-22 production held 1,117 specimens in 67 such codes
  (Economo Ant Lab 921 in 18 codes, CAS 196 in 49). Collection codes were never encoded.

Readers of the stored code, all of which take the plain code:

| Reader | How it uses the code |
|---|---|
| Specimen page and specimen lists (`Specimen.getLocalityLink()`, `getLocalityInfoString()`) | link `locality.do?code=` with the code encoded once (`Html.url`, UTF-8; was `HttpUtil.encodePath`, ISO-8859-1, which a plain `ç` would have broken) |
| `collection-body.jsp`, `specimen-body.jsp`, `specimenReport.jsp`, `locality-body.jsp` | `Html.url` once (PR #236) |
| Locality page (`LocalityAction`, `LocalityDb.getLocalityByCode()`) | `where sp.localitycode = <DBUtil.sqlLiteral(code)>`; was `escapeQuotes`, which doubled a `"` so `KM55""805` was never found |
| Collection page (`CollectionDb.getCollection()`) | the same, `DBUtil.sqlLiteral` |
| "Show All Specimens" on the locality page, advanced search Locality Code / Collection Code (`GenericSearch.getCodeSearchString()`) | `localityCode="<code>"`: a value in one pair of double quotes with no space is one code, taken whole (it may hold `"` or `,`); a `"` stays one character in the SQL. Several quoted codes must be separated by a space (`"a" "b"`): `"a","b"` is now one code |
| "Enlarge Map" link under the small map (`maps/googleMapInclude.jsp`) | `bigMap.do?locality=` (or `collection=`) with the code encoded once (`Html.url`) and the link escaped (was printed raw) |
| Code shown as text (locality page, collection page, specimen lists) | `Html.esc` or `bean:write` (escapes); the end-to-end round trip checks that the raw `<` never reaches the HTML |
| Maps (`Map.java`, `drawMap.js`) | `Map.java` form-encodes the code once, `drawMap.js` decodes once and builds the link with one `encodeURIComponent` |
| Field guide (`FieldGuide`) | `LocalityDb.getLocalityByCode()` (was `getLocality(code)`, which used the code as an SQL clause) |
| TaxonWorks nightly (`TaxonWorksTransformer`) | passes `LocalityCode` through; the parse above applies |
| API v3.1 | does not return the locality code |
| `MapLocality` | not used (no caller) |

MySQL compares codes ignoring case and accents (`utf8mb3_general_ci`): `MontagnedesFrançais_150` and
`MontagnedesFrancais_150` open the same locality page.

Checks: `LocalityCodePlainTest` (JUnit, CI), `tests/unit/test_map_popups.py`, `tests/unit/test_locality_codes.py`,
the end-to-end upload round trip (`zztest0007` with the code `zzloc<5m_Français"?`), and the Integrity Check query
`encodedLocalityCodes` (codes holding `%` and two hex digits; normally none). Links saved before a group's codes are stored plain (bookmarks, search engines: `locality.do?code=Ahi_%253C5m`)
stop finding the locality once the codes are decoded; the new links work. Old rows: the group's next upload
stores them plain (CAS every night through the TaxonWorks upload); the one-off repair for the others is in
`docs/runbook.md`, "Locality codes stored URL-encoded".

## 3a. The status page (`jobStatus.do`)

`util/JobStatusAction`, `web/curate/jobStatus.jsp`. Needs a curator login. Curators see the uploads of their own group (and their own uploads to another group); admins see every job, including the admin data jobs. Another group's job, or a number that does not exist, answers "Not available.". Without `id`: "Upload status", the recent jobs, newest first (linked from the Curate page and from the upload form).

The page shows "Status: Waiting / Running / Finished / Did not complete / Interrupted" and, while the upload waits or runs, "Now: <step>", the sentence "This page updates itself every 5 seconds (update now). You may close it: the upload continues on the server, and you can come back to it from the Curate page (Upload status)." (an HTTP `Refresh: 5` header; no script). Per status:

| Status | Text |
|---|---|
| Waiting | "Your file has been received and is waiting its turn: uploads run one at a time." and the reason, e.g. "Waiting for another upload to finish. Your job starts right after it." or "Waiting for the nightly data update (or another data job) to finish. Your job starts right after it." |
| Running | "Your file has been received and is being processed. Large files take several minutes." |
| Finished | "Upload complete." then "Upload complete: N specimens loaded, R of them red-flagged. Upload ID U." (plus "The upload report lists lines that need your attention (some may not have been loaded)." when the report has messages) and the link "Your upload report" (the same report as in the email) |
| Did not complete | "The upload did not complete." and the reason as plain text (the same text as in the email; crashes become "AntWeb hit an unexpected problem (case N) and made no changes. Please write to antweb@antweb.org (with the case number) so we can look into it.") |
| Interrupted | "The server restarted before this job finished. An upload runs as one database transaction, so an upload that did not finish is undone: the group's specimens are as they were before. Please upload the file again." Set at start-up for rows still waiting or running (`BackgroundJobDb.startup()`), and shown for such rows when read. |

A second upload while one is waiting or running: the status page of the first, with "You already have an upload waiting or running (below). The file you just sent was not used: a group's uploads run one after the other. When this upload has finished, upload again if you need to."

Restarts: the restart check (`AntwebSystem.isRestart()`, asked by `bin/reboot_checker.sh`) answers false while a job is running, as it already did during an upload. Jobs still waiting do not hold a restart back; after it they show as interrupted. A `docker restart`, a deploy or the watchdog interrupts a running upload the same way as before (the transaction is rolled back).

Not done at upload time (waits for the nightly run): geolocale (country/adm1) taxon lists and counts, project, museum and bioregion lists and counts, image counts, type flags, object maps of taxa and regions. Curators see a "Count Crawls" link on the Curate page (`utilData.do?action=runCountCrawls`, admin only).

## 4. Name resolution: current valid name and "Uploaded As"

`SpecimenUploadProcess.setStatusAndCurrentValidName()`, per line. Since 2026-10 the decision itself is `upload/CurrentValidNameRule.decide()` (unchanged behaviour), shared with the nightly name update ([antcat-to-antweb.md](antcat-to-antweb.md) section 7); the report lines, the placeholder taxon and the name columns stay in the upload:

1. A name that is an indet (`(indet)`, `(genus)`) gets status `indetermined`; a morphospecies name (non-alphabetic characters, e.g. `jtl-001`) gets `morphotaxon`. Both are kept as uploaded.
2. Otherwise the taxon is read from the database (`TaxonDb.getTaxon`; `TaxonMgr.isUseRefreshing()` is false), so the upload sees the AntCat import of that morning.
3. Not found, or status `unrecognized`: kept as uploaded. Report: "Names AntCat has only as a junior homonym (or another name AntWeb does not file specimens under): kept as uploaded" when the AntCat export has the name but none of its taxa is valid or has a valid name (each AntCat taxon listed with author, year and status, linked to AntCat; `SharedNameReport.homonymOnlyLine`), "Unavailable quadrinomial" for four-part names, otherwise "Unrecognized Invalid species. Names not found in AntCat.org (uploaded)".
3a. **A name of two or more AntCat taxa** (homonyms and the like, e.g. Pachycondyla sauteri: Wheeler, 1906, now Cryptopone sauteri; Forel, 1912, now Ectomomyrmex sauteri) that the rule would move (step 4) is **not moved**: the AntCat import keeps only one taxon per name in `taxon`, so the move would go to whichever taxon it kept. The specimen keeps the uploaded name (status as in AntCat) and the report lists it under "Needs the curator's decision. Name shared by two or more AntCat taxa: kept under the uploaded name (uploaded)": the name, "name is shared by two taxa; please confirm the identification: Cryptopone sauteri or Ectomomyrmex sauteri", and each AntCat taxon with author, year, status and valid name, linked to its AntCat page. **This is the same code as the nightly name update's "Needs the curator's decision" list on nameUpdates.do** ([antcat-to-antweb.md](antcat-to-antweb.md) section 7): `upload/SharedNameReport` decides (`candidates`, `heldAtUpload`) and writes the line (`line`) for both, from the names `SharedNames.load()` reads for both (the AntCat export the nightly import loaded, `web/speciesList/world/worldants_speciesList.txt`, read again when it changes; the homonym table without it). A fix in one fixes the other; `SharedNameReportTest` and `SpecimenNameUpdateDbTest.testUploadAndPageGiveTheSameSharedNameLine` check that the two texts are identical.
   Until 2026-10-20 the report had a section "Taxon names recognized as Homonyms. Names are not verified against AntCat.org" instead. It never listed a shared name: it was asked only for names without a taxon row (or with status `unrecognized`) and looked them up in the `homonym` table, but the import puts a name in `homonym` only when `taxon` already has it and deletes the others (`WorldantsUploadDb.deleteHomonymsWithoutTaxa`), so a homonym's name always has a taxon row and the rule filed its specimens under that one taxon (or its valid name) without a word.
4. Status in `Status.usesCurrentValidName()` (original combination, obsolete combination, obsolete classification, unavailable uncategorized, unavailable misspelling, synonym) and AntCat gives a current valid name:
   - the valid name's taxon is looked up (`TaxonDb.getCurrentValidTaxonName`, line 516);
   - found: the specimen gets `taxon_name` = valid name, `status = 'valid'`, `original_taxon_name` = the uploaded name (line 601), and its subfamily, tribe, genus, subgenus, species and subspecies columns are copied from the valid taxon (`makeSpecimenUseTaxon()` line 609). Report: "Recognized invalid species. Submission replaced with current valid name from AntCat.org (uploaded)", one line "old -> new: reason" with links, the reason worded and linked to AntCat as on the Name updates page (`UploadNameReasons`, the nightly name update's code; worked out once per old -> new pair; the plain "old -> new" if AntCat's history cannot be read);
   - the valid name is only a homonym: a placeholder taxon with `insert_method = 'homononymMirroringTaxon'` is created and the specimen points to it (line 540);
   - neither: see section 7.5;
   - except for a name of two or more AntCat taxa: kept, see 3a.
5. Status `valid`, `unavailable`, `unidentifiable`: kept.
6. Any name outside Formicidae is listed as "Names not in Family Formicidae (uploaded)".

The specimen page shows "Uploaded As: <original_taxon_name>" when it differs (`web/specimen-body.jsp` line 491).

This happens only at upload time. When AntCat later changes a name, existing specimens stay on the old name until the group uploads again (CAS: until the next TaxonWorks upload). See [antcat-to-antweb.md](antcat-to-antweb.md), "What does not happen".

## 5. The nightly TaxonWorks job (`TaxonWorksNightly`)

Settings: `etc/taxonworks.properties` on the host (`/root/antweb/etc/`, container `/antweb/deploy/etc/taxonworks.properties`, gitignored): `url=` (TaxonWorks "Public download link", contains the project token), `curatorId=` (1 = Brian, CAS group 1), optional `minRatio=` (default 0.95). No file: the job logs "skipped: no ..." and does nothing.

`run(connection, 90)` from set 1 (line 81):

1. One run at a time (`AtomicBoolean s_running`).
2. `runOnce()` (line 124):
   1. Download the archive to `workingDir/taxonworksNightly.zip.part` (connect timeout 30 s, read up to 60 min). Not HTTP 200 or not a zip: "skipped: TaxonWorks answered HTTP <code> <first 200 characters>".
   2. SHA-256 equal to `workingDir/taxonworksNightly.sha256` (the last uploaded archive): "skipped: archive unchanged since the last upload".
   3. Unzip `data.tsv` and transform (section 2.2); count data lines.
   4. **Guard** (line 179): if the group has specimens and the new file has fewer than `minRatio` times as many lines: "SKIPPED BY GUARD: archive has N records, group 1 has M specimens now (minimum 95%). Nothing changed."
   5. Another upload running (line 185): "skipped: another upload is running (...)".
   6. Set the upload flag to "TaxonWorks nightly:<curator>", new upload id, `uploadPreparedFile()` (section 3 steps 5 to 9, as `taxonWorksUpload`), `postProcessSpecimenUpload`, `specimenPostProcessGlobal`, report with "Uploaded by: Nightly TaxonWorks job (automatic)", commit, write the new SHA-256. Result "uploaded N records for group 1 (was M specimens) in <time>".
   7. Any failure: rollback, "FAILED: <exception class>" (the message is not logged because it can contain the download URL with its token).
3. TaxonWorks builds a new archive at most once a day, and only when the link is requested; the request that starts the build still receives the old archive (HTTP 422 while building). So while the answer is "unchanged" or HTTP 422, the job asks again every 3 minutes (`WAIT_STEP_MINUTES`, line 67) for up to 90 minutes (`isWaitingForTaxonWorks()`, line 216). Since 2026-10-24 it also waits while the archive was built more than 12 hours before the run started (the newest file time in the zip, which TaxonWorks writes in UTC; `isOlderArchive()`): on 2026-10-24 the run uploaded the archive built on 10-23 at 05:01 Pacific at once, so a name Brian Fisher fixed in TaxonWorks during 10-23 (CASENT0904877, *Allomerus villosus*) arrived only a day later. At the last check of the wait it uploads an older archive rather than nothing, and says so in the log line ("archive built ... UTC (older than this run ...)").
4. Log: one line per run in `web/log/taxonworksNightly.log` and `admin.log` (admins read them through `logMgr.do`), plus the normal upload report and `upload` row when it uploaded.

Timing: a manual TaxonWorks upload of 265,384 records took 23 minutes (upload #2248, 2026-10-16). Set 1 is about 12 minutes of AntCat import, up to 90 minutes of waiting, then the upload.

No email is sent for the nightly job (section 6).

## 6. Upload-complete emails (`UploadNotice`)

- Built in `SpecimenUploadJob.run()` and sent in its `finally` block (`UploadAction.sendUploadNotice()`) on a background thread (`UploadNotice.send()`). Unchanged by the background run, except that the failure text is the job's failure message instead of the page's.
- To: the **logged-in uploader** (not the "Upload as" curator). From antweb@antweb.org through Amazon SES (`Emailer.sendNotice`), reply-to antweb@antweb.org.
- Complete: subject "AntWeb: your specimen upload is complete (N specimens)"; body with file, type, group, time, upload id, specimens loaded (= inserted count), red-flagged count, report link.
- Not complete: "did not complete", with the page's message as plain text; crashes (case numbers, Java exception names) become a fixed sentence (`failureText()` line 262).
- Not sent (`skipReason()` line 139, logged in `emails.log`): switch off; no web session (nightly job); either group excluded (default group 1, CAS); no or invalid email; on staging and dev only to antweb.org and calacademy.org addresses.
- Switch: `etc/upload-email.properties` (`enabled=on|off`, `excludeGroups=`, `onlyDomains=`), read at most once a minute; default on in production, off elsewhere.

## 7. What can fail and how it shows

### 7.1 Every line refused: the group ends up empty (check)

`doPreliminaries()` deletes the group's specimens before the first line is read (step 6). If no line is processed, `importSpecimens()` throws "No records processed" and catches it itself (line 243): the message goes into the report under "Rollback occurred. (not uploaded)", but no rollback happens. `UploadAction` then post-processes and commits (line 550). Read in the code; to confirm on staging with a test group (first test in [test-gaps-uploads-nightly.md](test-gaps-uploads-nightly.md)). Example causes: a header-only file, every line with a bad code, every genus unknown. For the nightly job the 95% guard counts lines in the file, not lines loaded, so it does not catch this case.

### 7.2 "Invalid Column" stops the upload part way (check)

`parseLine()` throws `AntwebException("Invalid Column ...")` (line 603) when the "other" columns do not make valid XML (`SpecimenXML.isInvalid`). The exception leaves the line loop and is caught at line 243: the lines before stay loaded, the rest are not read, and the upload commits. The report shows the reason under "Rollback occurred".

### 7.3 Messages shown instead of an upload (nothing changed)

| Message | Cause |
|---|---|
| (no longer shown for a specimen upload: it waits in the queue, section 1c) "A curator is currently in the process of an Upload" / "Antweb is currently in it's re-computation process" | another upload, or a nightly set (about 5 to 8 AM) |
| status page of an earlier upload, "You already have an upload waiting or running" | an upload of the same group, or by the same login, is waiting or running |
| "AntwebMgr not yet initialized." | within minutes of a restart |
| "The zip file has no .txt file." and the other zip refusals | the zip's list of entries (`SafeUnzip.check()`), at once, before anything is queued |
| on the status page, "The upload did not complete." with the down time message | `server.is_down_time = 1` |
| "Specimen File must be a .txt file." / "...in the most current format. ..." / "...tab-delimited file." | section 2.1 checks |
| "transformFile() ..." | TaxonWorks/GBIF conversion failed (missing column) |
| "Error. Case number:N. No changes made. e:..." | an exception; transaction rolled back |
| "Must choose a specimen file for upload." | no file selected |

From "Specimen File must be ..." on, the messages appear on the status page under "The upload did not complete." (they are found by the background job).

### 7.4 Cloudflare timeout

Until 2026-10-20 the upload ran in the request: Cloudflare answered 524 after 100 seconds while the upload continued on the server, and curators sent the file again. Now the request ends as soon as the file is received (section 1b) and the status page follows the upload. The upload form's note says "Large files take several minutes; you will see the progress on the next page". Only sending a very large file over a slow connection can still take longer than 100 seconds (the time to receive the file).

### 7.5 Other behaviour worth knowing

- A code used by another group is refused as a duplicate; there is no way for one group to take over a specimen.
- Refused lines are not counted anywhere except the report; the email reports "Specimens loaded", the report "Record Count", "Parsed", "Processed" and "Inserted Specimens".
- A name whose status uses a current valid name, where the valid name has no taxon row and is not a homonym, is filed under the valid name anyway (`setStatusAndCurrentValidName()` lines 512 to 560 leave `taxonName` set to the valid name): the specimen then points to a name with no taxon row. Check with a query on staging.
- The whole upload is one transaction on a `longConPool` connection; that pool destroys connections held longer than 3600 seconds (`WEB-INF/struts-configDbAnt.xml` line 82). An upload longer than an hour would fail (CAS takes about 23 minutes today).

## 8. Tables written

| Table | When |
|---|---|
| `specimen` | deleted for the group, then inserted line by line |
| `taxon` | group's `addMissingGenus` / `homononymMirroringTaxon` rows deleted; new or updated rows per name; orphans of the group's source deleted; indets without specimens deleted (site-wide) |
| `upload_line` | group's rows replaced (pre-scan) |
| `proj_taxon` | project `allantwebants` regenerated |
| `upload` | one row per upload, then its counts |
| `ant_group` | `last_specimen_upload`, `upload_specimens`, `first_specimen_upload`, `upload_count` |
| object map table (`ObjectMapDb`) | the group's map |
| `statistics` | one row of site totals |
| geolocale / geolocale_taxon | endemic flags (site-wide) |

Also `background_job` (one row per upload or admin job started from a page: status, step, result; section 3a; record and undo in `db/upgrade/8.113/2026-10-20-background-job.sql`).

Files: `workingDir/jobs/upload-group<id>-*.zip` (the received file, until the job ends), `workingDir/specimen*.txt` (working copy), `web/upload/<date>-specimen<id>.txt` (kept), `web/log/specimen/<date>-<operation>.html` (report), `web/log/taxonSet.log` (delete counts); nightly: `workingDir/taxonworksNightly.zip`, `.sha256`, `taxonworksSkipped.txt`, `web/log/taxonworksNightly.log`.

## 9. Related code not covered here

- Admin data jobs in the background (2026-10-20): `utilData.do` sub-actions started from a page (all except `siteWarning`, `setIsRestart` and `fixGeolocaleTaxonParentage`, which answer quickly or have a second button), `upload.do?action=fetchAndReloadWorldants` ("Import AntCat names now") and `schedule.do?action=run` (a nightly set started by hand) answer with their status page. The nightly run itself (`Scheduler` and its `utilData.do` calls with the scheduler's code) runs in the request as before.
- `upload.do` other sub-actions (`fetchAndReloadWorldants`, `museumCalc`, `runStatistics`, `genRecentDescEdits`, `allSpecimenFiles`, `specimenTest`, `speciesTest`) and the "Upload a Data File" branch (`uploadDataFile()`, line 743): listed in [test-gaps-uploads-nightly.md](test-gaps-uploads-nightly.md) where they matter.
- `validateSpeciesList.do`: a read-only checker, not an upload.
