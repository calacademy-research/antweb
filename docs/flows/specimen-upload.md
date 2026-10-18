# Specimen upload: curator file, TaxonWorks (manual and nightly)

Read from the code on master at commit 5b0c1d5 (2026-10-18). Paths are relative to the repo root; Java classes are under `src/org/calacademy/antweb/` unless a full path is given. Line numbers are those of that commit. "Verified" means read in the code; "check" means the code suggests it but it should be confirmed on staging or in the database.

## In plain words

A curator uploads one file that holds **all** of their group's specimens. AntWeb first deletes every specimen of that group, then reads the file line by line and inserts each record again. So an upload always replaces the group's whole dataset; there is no "add these few records" upload.

Each line's name is checked against the AntCat names AntWeb loaded that morning. If the curator's name is an old name (a synonym, an obsolete combination, a misspelling), the specimen is filed under the current valid name and the curator's name is kept as "Uploaded As" on the specimen page. Names AntCat does not know are kept as they are and listed in the report.

Lines that cannot be read (bad specimen code, wrong number of columns, impossible coordinates, a code another group already uses, and so on) are left out and listed in the upload report under headings marked "(not uploaded)". The report is a web page under `/web/log/specimen/`; the curator gets an email when the upload has finished (not for CAS and not for the nightly TaxonWorks job).

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

1. **Gates** (`UploadAction.execute()`):
   - another upload running (`Check.upload`, line 110, and line 165) or the nightly compute running (`Check.compute`, line 112): message, nothing done. The "upload in process" flag is a single static string (`UploadAction.s_isInProcess`), shared with the image uploader and the nightly TaxonWorks job.
   - login, curator or admin, "upload specimens" permission (lines 127 to 154);
   - `AntwebMgr.isPostInitialized()` (line 159): not during the 2 to 4 minutes after a restart;
   - down time switch in table `server` (`ServerDb.isDownTime`, line 206);
   - POST only (line 214). The CSRF token is checked earlier by `SessionRequestFilter`.
2. **Upload ID**: `AntwebMgr.incrementSpecimenUploadId()` (line 202). This number is written into every inserted specimen (`specimen.upload_id`) and into the `upload` table row.
3. **Effective curator**: the login, or the curator chosen in "Upload as" if `isAllowedUploadAs()` (line 68) allows it (admins, or ids listed in the login's `upload_as`). The specimens belong to that curator's group.
4. **Copy and check the file**: section 2. Nothing has been deleted yet.
5. **Pre-scan** (`LineNumMgr.init()`): deletes this group's rows in `upload_line`, then marks lines whose column count is far below the header's as "Extra Carriage Return" (they are skipped) and stores them in `upload_line` for the report link.
6. **Delete the group's data** (`SpecimenUpload.doPreliminaries()` line 66, `SpecimenUploadDb.dropSpecimens()` line 81): `delete from specimen where access_group = ?` and `delete from taxon where access_group = ? and insert_method in ('addMissingGenus','homononymMirroringTaxon')`. Logged to `web/log/taxonSet.log`. Images (`image`, `group_image`) are **not** touched.
7. **Each line** (`SpecimenUpload.importSpecimens()` line 71):
   - **Parse** (`SpecimenUploadParse.parseLine()` line 70). Refusals at this step (line not loaded, listed in the report): whitespace inside subfamily/genus/species/subspecies (line 140); a specimen code with characters other than letters, digits and `. _ : / ( ) # + -` (line 187; spaces are removed and reported); latitude outside -90..90 or longitude outside -180..180 (lines 229, 249); more columns than the header (line 298); a genus AntWeb does not know in an ant subfamily, unless it is a morpho or indet name (line 424); a species name with quotes or a space inside the subspecies (line 448); no name could be built (line 659).
   - Taxonomy cleaning during parse: a subfamily written as `(Name)` loses the parentheses (line 339); the subfamily is replaced by the genus's subfamily in AntWeb (reported as "Preferred Subfamily For Genus"); a synonymised genus is handled per GitHub #95 (line 359): names AntCat knows keep the old genus for step "name resolution" below, morphos and indets move to the valid genus ("Synonymized genus replaced"); `sp.` or empty species becomes `(indet)`; `species subspecies` in one column is split.
   - Geography during parse: a missing country is guessed from `adm1` if possible; country and adm1 names are mapped to AntWeb's valid names (reported "Not Antweb valid countries", "Not valid Antweb adm1" with a link to `adm1Mgr.do`); the bioregion is taken from the country or adm1 and overrides the file's value ("Corrected Biogeographic Regions"); islands become their island country.
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
    - one row in `upload` (`SpecimenUploadDb.updateUpload()` line 140: upload id, login id of the effective curator, group, report file name, backup file) and its counts (`UploadDb.updateCounts`), `ant_group.first_specimen_upload` and `upload_count` (`UploadDb.updateGroup`);
    - one row in `statistics` (`StatisticsDb.populateStatistics`, site totals);
    - the museums found in the file are kept in the session for the "Recalculate Your Museums" button (`museumCalc`).
    - then for the whole site (`specimenPostProcessGlobal()` line 673): `GeolocaleDb.calcEndemic()` and `TaxonDb.removeIndetWithoutSpecimen()`.
11. **Report** (`UploadDetails.finish()` line 248, `genUploadReport()` line 93): written to `web/log/specimen/<yyyyMMdd-HH:mm:ss>-<operation>.html` (folder chosen by `getLogDir()` line 279: operations containing "specimen", "taxonWorks" or "GBIF"). Public URL `/web/log/specimen/...`; also reachable as `uploadReport.do?uploadId=` (redirect) and listed by `listSpecimenUploads.do?groupId=`.
12. **Commit** (line 550). Everything from step 6 to 11 is one database transaction (`connection.setAutoCommit(false)`, line 196). An exception anywhere goes to `handleException()` (line 608): rollback and "Error. Case number:N. No changes made."
13. **Finally** (line 568): the upload flag is cleared, `UploadMgr.populate()` reloads the upload list, the connection is closed, and the email is sent (section 6).

Not done at upload time (waits for the nightly run): geolocale (country/adm1) taxon lists and counts, project, museum and bioregion lists and counts, image counts, type flags, object maps of taxa and regions. Curators see a "Count Crawls" link on the Curate page (`utilData.do?action=runCountCrawls`, admin only).

## 4. Name resolution: current valid name and "Uploaded As"

`SpecimenUploadProcess.setStatusAndCurrentValidName()` line 431, per line:

1. A name that is an indet (`(indet)`, `(genus)`) gets status `indetermined`; a morphospecies name (non-alphabetic characters, e.g. `jtl-001`) gets `morphotaxon`. Both are kept as uploaded.
2. Otherwise the taxon is read from the database (`TaxonDb.getTaxon`; `TaxonMgr.isUseRefreshing()` is false), so the upload sees the AntCat import of that morning.
3. Not found, or status `unrecognized`: kept as uploaded. Report: "Taxon names recognized as Homonyms" if it is in `homonym`, "Unavailable quadrinomial" for four-part names, otherwise "Unrecognized Invalid species. Names not found in AntCat.org (uploaded)" (line 496).
4. Status in `Status.usesCurrentValidName()` (original combination, obsolete combination, obsolete classification, unavailable uncategorized, unavailable misspelling, synonym) and AntCat gives a current valid name:
   - the valid name's taxon is looked up (`TaxonDb.getCurrentValidTaxonName`, line 516);
   - found: the specimen gets `taxon_name` = valid name, `status = 'valid'`, `original_taxon_name` = the uploaded name (line 601), and its subfamily, tribe, genus, subgenus, species and subspecies columns are copied from the valid taxon (`makeSpecimenUseTaxon()` line 609). Report: "Recognized invalid species. Submission replaced with current valid name from AntCat.org (uploaded)", one line "old -> new" with links (line 567);
   - the valid name is only a homonym: a placeholder taxon with `insert_method = 'homononymMirroringTaxon'` is created and the specimen points to it (line 540);
   - neither: see section 7.5.
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
3. TaxonWorks builds a new archive at most once a day, and only when the link is requested; the request that starts the build still receives the old archive (HTTP 422 while building). So while the answer is "unchanged" or HTTP 422, the job asks again every 3 minutes (`WAIT_STEP_MINUTES`, line 67) for up to 90 minutes (`isWaitingForTaxonWorks()`, line 216).
4. Log: one line per run in `web/log/taxonworksNightly.log` and `admin.log` (admins read them through `logMgr.do`), plus the normal upload report and `upload` row when it uploaded.

Timing: a manual TaxonWorks upload of 265,384 records took 23 minutes (upload #2248, 2026-10-16). Set 1 is about 12 minutes of AntCat import, up to 90 minutes of waiting, then the upload.

No email is sent for the nightly job (section 6).

## 6. Upload-complete emails (`UploadNotice`)

- Built in `UploadAction` when a file is chosen (line 244) and sent in the `finally` block (line 579, `sendUploadNotice()` line 588) on a background thread (`UploadNotice.send()` line 299).
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
| "A curator is currently in the process of an Upload" / "Server is currently in an Upload Process" | another specimen or image upload, or the nightly TaxonWorks upload |
| "Antweb is currently in it's re-computation process" | a nightly set is running (about 5 to 8 AM) |
| "AntwebMgr not yet initialized." | within minutes of a restart |
| down time message | `server.is_down_time = 1` |
| "Specimen File must be a .txt file." / "...in the most current format. ..." / "...tab-delimited file." | section 2.1 checks |
| "transformFile() ..." | TaxonWorks/GBIF conversion failed (missing column) |
| "Error. Case number:N. No changes made. e:..." | an exception; transaction rolled back |
| "Must choosen specimen file for upload" | no file selected |

### 7.4 Cloudflare timeout

Cloudflare answers 524 after 100 seconds; the upload continues on the server. The page's script switches to a message after 85 seconds and sends the curator to the Upload Reports list (`curate-body.jsp` line 161). The email tells the curator when it is done.

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

Files: `workingDir/specimen*.txt` (working copy), `web/upload/<date>-specimen<id>.txt` (kept), `web/log/specimen/<date>-<operation>.html` (report), `web/log/taxonSet.log` (delete counts); nightly: `workingDir/taxonworksNightly.zip`, `.sha256`, `taxonworksSkipped.txt`, `web/log/taxonworksNightly.log`.

## 9. Related code not covered here

- `upload.do` other sub-actions (`fetchAndReloadWorldants`, `museumCalc`, `runStatistics`, `genRecentDescEdits`, `allSpecimenFiles`, `specimenTest`, `speciesTest`) and the "Upload a Data File" branch (`uploadDataFile()`, line 743): listed in [test-gaps-uploads-nightly.md](test-gaps-uploads-nightly.md) where they matter.
- `validateSpeciesList.do`: a read-only checker, not an upload.
