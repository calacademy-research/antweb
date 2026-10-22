# The nightly run: Scheduler and sets 1 to 5

Read from the code on master at commit 5b0c1d5 (2026-10-18). Paths are relative to the repo root; Java classes are under `src/org/calacademy/antweb/`. Durations are the ones written in code comments (production, older measurements) and should be read as rough. "Check" means the code suggests it but it should be confirmed on staging or in the logs.

## In plain words

Every morning at 5:00 Pacific, AntWeb recomputes everything that depends on names and specimens, in five steps ("sets") that run one after the other and take until about 8 AM:

1. **Set 1, names and CAS specimens:** load the AntCat catalog ([antcat-to-antweb.md](antcat-to-antweb.md)), then upload the CAS specimens from TaxonWorks ([specimen-upload.md](specimen-upload.md) section 5), then (when switched on) move existing specimens whose name AntCat has changed to the current valid name ([antcat-to-antweb.md](antcat-to-antweb.md) section 7). Can take up to about two hours because it waits for TaxonWorks.
2. **Set 2, countries and states:** remove names nobody uses any more, rebuild the country and adm1 species lists from the specimens, recount every country and adm1.
3. **Set 3, projects, museums, bioregions:** recount projects, rebuild museum and bioregion lists and counts, move list entries to current valid names, mark species with type specimens.
4. **Set 4, images:** count images for every taxon, then for every place (country, adm1, island, region, subregion, project, museum, bioregion) its Images, the images of each view and its Imaged specimens, straight from the image table (since 2026-10-20).
5. **Set 5, maps and checks:** drop default-specimen choices that no longer fit, rebuild the map data of countries, adm1s and museums, tidy species lists, reload all caches.

While a set runs, specimen uploads are refused ("re-computation process"). Since 2026-10-18 the container is **not** restarted after the run (it used to be, around 8 AM); a restart only happens if set 1 is still running after 3.5 hours, or if an admin asks for one. On staging, set 5 is skipped because the 4 GB droplet runs out of memory; the caches are reloaded instead.

---

## 1. How the run starts

| Piece | Code | What |
|---|---|---|
| Timer | `util/SessionRequestFilter.runTask()` (line 325), `nextLaunchTime()` (line 338), `scheduleNextRun()`, `CustomTask` (line 358) | At startup (filter `init`), a `java.util.Timer` is set for the next 05:00:00 in the JVM's time zone (Pacific). Each run schedules the next one, also after a failure. Not in dev mode. |
| Launch hour | `Scheduler.LAUNCHTIME = 5` (line 33) | |
| Admin, by hand | `schedule.do?action=run` (`WEB-INF/struts-config.xml` line 603), optionally `&num=1..5` for one set | `Scheduler.execute()` (line 84): admin only, POST confirmation (`PostOnly.confirm`), then the same `doAction()`; the browser waits for the whole run. |
| One set, by hand | `utilData.do?action=set1` ... `set5` (struts line 588) | `UtilDataAction` directly: admin, POST confirmation (line 109). `utilData.do?action=<step>` runs one step of a set (the list in `UtilDataAction.ACTIONS`, line 46). |

The secure code: `Scheduler.doAction()` creates a one-time 128-bit code (`AntwebUtil.getSecureCode()`) and calls `http://localhost:8080/utilData.do?action=setN&param=allow&secureCode=...` on the container itself (`AntwebProps.getThisDomainApp()`). `UtilDataAction` accepts the call without a login when the code matches (`isSecureCode`, line 100). The code is cleared at the end of the run (`resetSecureCode()`).

## 2. `Scheduler.doAction()` (line 138)

1. Wait until `ServerStatusAction.isReady()` (checks every 60 seconds in production).
2. If `UtilDataAction.isInComputeProcess()`: log "Already in compute proceess" to compute.log and stop.
3. Set the compute flag to "run", log "Begin run" (compute.log).
4. For each set in order: `HttpUtil.fetchUrl(url, SET_READ_TIMEOUT_MS)` with a 4-hour read timeout (line 40; before #62 the 5-minute default made set 1 time out every night). If the answer contains "Unexpected error" an exception stops the remaining sets (lines 205 to 240), see the test-gaps file for why this check never fires. An `IOException` (timeout, connection refused) logs "Scheduler FAILED (remaining sets skipped)" to compute.log.
5. Staging (`AntwebProps.isStageMode()`, full run only): set 5 is skipped and "set5 skipped on staging (memory)" is logged (line 230).
6. End of a full run (`num == 0`, line 250):
   - `etc/scheduler.properties` has `nightlyRestart=true` (`isNightlyRestartOn()`, line 51, read now): `AntwebSystem.setIsRestart(true)`, compute.log "Nightly restart requested ... heap used ..."; the host restarts the container within 5 minutes (section 5);
   - otherwise: if set 5 was skipped, `AntwebMgr.populate(connection, true)` reloads all caches (line 261); compute.log "Nightly restart skipped (off ...) heap used ..." (memory is logged so slow growth can be watched).
7. Finally: compute flag cleared, secure code cleared, "End run" (compute.log).

## 3. `UtilDataAction` around each set

`execute()` (from line 60):

1. The action must be in `ACTIONS` (else 404).
2. Admin login, or the scheduler's secure code; a browser request must also pass the POST confirmation.
3. `setInComputeProcess(action)` (line 136): the flag now holds e.g. "set2"; it is cleared in `finally`. This is the flag that blocks curator uploads (`Check.compute`).
4. Connection from `longConPool`, auto-commit off (line 140); `doAction()`; `commit()` (line 154); any exception: `handleException()` (line 182) rolls back and answers "Error. No changes made for action:...".
5. A whole set is one transaction, except where the code commits on its own (set 1: see [antcat-to-antweb.md](antcat-to-antweb.md) section 4 and the TaxonWorks upload).

## 4. The sets step by step

Times are from comments in `UtilDataAction.doAction()` (production unless stated) and are old; compute.log has the real ones.

### Set 1 (line 222): names and CAS specimens, about 12 minutes plus up to 90 minutes of waiting plus the upload

| Step | Code | Writes |
|---|---|---|
| `worldantsFetchAndReload` | `SpeciesListUploader.worldantsFetchAndReload()`, then `ProjTaxonCountDb.childrenCountCrawl("worldants")` | see [antcat-to-antweb.md](antcat-to-antweb.md): `taxon`, `homonym`, taxonomic history, `proj_taxon`, `geolocale_taxon`, `bioregion_taxon`, `museum_taxon`, `worldants_upload` |
| `taxonFinish` | `TaxonDb.setSubfamilyChartColor()` (35 s) | `taxon` chart colours |
| TaxonWorks | `TaxonWorksNightly.run(connection, 90)` | CAS specimens, see [specimen-upload.md](specimen-upload.md) section 5 |
| name update | `UtilDataAction.nightlyNameUpdate()` -> `SpecimenNameUpdate.runLocked()`; setting `etc/name-update.properties` (no file = off); skipped if the AntCat import failed or an upload is running; commits on its own | `specimen` (apply mode), `specimen_name_update`, `taxon_set_parked`; see [antcat-to-antweb.md](antcat-to-antweb.md) section 7 |
| cache | `TaxonMgr.populate(connection, true, false)` | in-memory taxon cache |

### Set 2 (line 233): countries and adm1s, about 44 minutes

| Step | Code | What it computes / writes |
|---|---|---|
| `dataCleanup` | `TaxonDb.deleteSpeciesWithoutSpecimenOrAntcatSource()`, `deleteGeneraWithoutSpecimenOrAntcatSource()` (`home/TaxonDb.java` line 889), `TaxonSetDb.dataCleanup()` (line 72) | first puts back curator list rows parked on an earlier night whose name exists again (`CuratorTaxonSetDb.restore()`, table `taxon_set_parked`, since 2026-10-19); deletes species, subspecies and genera from `taxon` that are neither AntCat names (project `worldants`) nor used by any specimen; parks curator rows (sources curator, speciesList, speciesListTool, speciesListUpload) of `geolocale_taxon` and `proj_taxon` whose taxon is gone; deletes morphos without specimens from `geolocale_taxon` (source specimen) and `proj_taxon` (not curator rows); deletes `geolocale_taxon` rows without a taxon, and `proj_taxon` rows (not `worldants`) without a taxon |
| `generateGeolocaleTaxaFromSpecimens` (12 min) | `GeolocaleTaxonDb.generateGeolocaleTaxaFromSpecimens()` (line 660) | deletes the specimen-derived rows of `geolocale_taxon` and inserts them again from `specimen` (country and adm1 of each specimen's `taxon_name`); removes rows listed in `geolocale_taxon_dispute` |
| `geolocaleCountCrawl` (30 min) | `GeolocaleDb.updateCounts()` (line 867) | for every live adm1, every valid country, subregion and region: child counts in `geolocale_taxon` (`GeolocaleTaxonCountDb.childrenCountCrawl`), `geolocale.specimen_count`, subfamily/genus/species counts (not `image_count` / `imaged_specimen_count` any more: until 2026-10-20 `image_count` was the sum of `geolocale_taxon.image_count`, read here before set 4 had filled those in again, so Madagascar showed 3,435 images instead of about 29,700; set 4 now writes both); then endemic and introduced flags (`calcEndemic`, `calcIntroduced`) and colours. Logs "Populating Geolocales", "Adm1s populated", ..., to compute.log. **Specimens of a list name** (`geolocale_taxon.specimen_count`, `Geolocale.getCountSpecimensQuery()`): the list's species and subspecies rows are first set to 0 (since 2026-10-20; before, a name whose specimens were gone kept its old number), then counted: country, island, adm1: specimens collected there (`specimen.country` / `island_country` / `adm1`), countable status. **Regions and subregions** (since 2026-10-20, `Geolocale.regionSpecimensSql()`): the specimens that the pages of their countries count (valid countries whose parent is the subregion, or a subregion of the region; islands by `island_country`; collected there, countable status, name on that country's list), each specimen once, name also on the region's list; before, every specimen of the list's names anywhere in the world (Brian Fisher: "no one believes our numbers"). `geolocale.specimen_count` (Specimens on the page) is the sum of those rows, and the "Specimens per Subfamily" chart (`specimen_subfamily_dist_json`) is the same sum by subfamily (before: every specimen of the names on the list, anywhere, for every kind of place). The compute.log lines "Subregions populated (specimens collected in their countries)" and "Regions populated (...)" mark runs with this rule (countCheck.do treats region differences as pending until one has run) |
| `updateTaxonSets` (before the count, since 2026-10-21) | `TaxonSetDb.updateTaxonSetTaxonNames()`, `updateTaxonSetSources()` | list rows of synonyms renamed to (merged into) their current valid name, in all four kinds of lists |
| (`geolocaleTaxonFix`, removed 2026-10-22) | `GeolocaleTaxonDb.geolocaleTaxonFix()` | ran after the count and rewrote the Species of every country and adm1 genus row with every list row under the genus, any status or rank (docs/numbers.md B37); now an admin job only (`utilData.do?action=geolocaleTaxonFix`), by the count's rule |

### Set 3 (line 243): projects, museums, bioregions, about 15 minutes

| Step | Code | What |
|---|---|---|
| `projectCountCrawl` (0.7 min, plus the list recount) | `ProjectDb.updateCounts()`, then (since 2026-10) `ProjTaxonCountDb.curatedProjectsCountCrawl()` | project totals (`project`; `image_count` and `imaged_specimen_count` come from set 4 since 2026-10-20); "Projects counts computed". Then the counts on the species list of every live project except worldants and allantwebants (`proj_taxon`: synonym rows renamed to the valid name (since 2026-10-22), set to 0, then counted from the specimens, a synonym's specimens under the valid name); "Project species-list counts crawled: N projects". Before 2026-10 these list counts were only recounted when a curator saved the list or pressed Recalculate, and went stale (found by countCheck.do) |
| `populateMuseum` (8 min) | `MuseumDb.populate()` then `updateMuseum()` | per museum: delete its `museum_taxon` rows, set `specimen.museum`, rebuild `museum_taxon` from specimens (species; since 2026-10-22 synonym rows renamed to the valid name right away; then higher taxa), child counts (a synonym's specimens under the valid name), museum fields; "Museums populated" |
| `populateBioregion` (5.5 min) | `BioregionDb.populate()` (line 291) | per bioregion: reset counts, rebuild `bioregion_taxon` from specimens (since 2026-10-22 synonym rows renamed to the valid name right away) and higher taxa, child counts (a synonym's specimens under the valid name), fields; then endemic and introduced for bioregions; "Bioregions populated" |
| sources | `TaxonSetDb.updateTaxonSetSources()` | recompute the `source` column of `proj_taxon`, `bioregion_taxon`, `geolocale_taxon` (until 2026-10-22 the whole `updateTaxonSets`, which also renamed list rows after the counts: docs/numbers.md B38) |
| group counts (since 2026-10-22) | `GroupCountDb.recountAll()` | `group_count`: today's numbers of every upload group's specimens (groups.do, group.do, curator.do); "Group counts: N groups ..." |
| `crawlForType` | `TaxonDb.crawlForType()` (line 722) | `taxon.type` reset, then `type = 1` for species (not morphos) with at least one specimen with a type status; "Types Crawl completed" |

### Set 4 (line 253): images, about 15 minutes

| Step | Code | What |
|---|---|---|
| `imageCountCrawl` | `ImageCountDb.imageCountCrawls()` (line 22) | image counts per specimen, species, genus, subfamily, family, then in `geolocale_taxon`, `proj_taxon`, `bioregion_taxon`, `museum_taxon` (the "N Images" of each taxon on a place's image pages). Since 2026-10-20 the counts of a country, adm1, island, region, subregion, museum or bioregion list are of the images of that place's specimens only (regions and subregions: the specimens of their countries, as in set 2) (before: of every specimen of the name, anywhere), the rows are set to 0 first, and the query is ordered so the genus and subfamily sums are complete; "Image Count Crawl completed" |
| `placeImageCounts` (since 2026-10-20) | `PlaceImageCountDb.updateAll()` | for every place, from the `image` table joined to the specimens that the place's Specimens number counts (country, adm1, island: `specimen.country` / `adm1` / `island_country`, countable status, name on the list; region and subregion (since 2026-10-20): the specimens of their countries, see set 2; bioregion: `specimen.bioregion`; museum: `specimen.museum`; project: names on the list, anywhere, any status): table `place_image_count` (one row per place and shot type, plus `*all`, `*ant` and `*label_only`; created by the application, record and undo in `db/upgrade/8.113/2026-10-20-place-image-count.sql`), and `image_count` (all files, labels too) and `imaged_specimen_count` (an image of the ant, labels not counted) of `geolocale`, `project`, `museum`, `bioregion`. Two grouped queries per kind of place (all places at once), done by MySQL; estimated 1 to 3 minutes on production (about 3 times the image table for projects, as allantwebants and worldants each cover nearly every image). compute.log "Place image counts: geolocale N places with images in S s; ...". `utilData.do?action=placeImageCounts&name=<geolocale, bioregion, museum or project>` redoes one kind. The Recalculate button of a place page redoes that place. Checked by countCheck.do ("place images") |

### Set 5 (line 262): maps and checks, about 51 minutes; skipped on staging

| Step | Code | What |
|---|---|---|
| `deleteConflictedDefaultImages` | `TaxonPropDb.deleteConflictedDefaultImages()` (line 302) | deletes `taxon_prop` default-specimen choices (`...Specimen` properties) whose specimen is now filed under another name |
| `genObjectMaps` (49 min) | `ObjectMapDb.genObjectMaps()` (line 27) | map data for every country, museum and adm1 (group maps are made at upload time) |
| `cleanupSpeciesListProxyRecords` | `ProjTaxonDb.cleanupSpeciesListProxyRecords()` (line 398) | deletes `proj_taxon` rows with source `proxyspeciesListTool` that have no children in that project |
| `verifyProjTaxon` | `ProjTaxonDb.verifyProjTaxon()` (line 472) | deletes genus rows from project lists (not `worldants`) that have no species in that project |
| caches | `AntwebMgr.populate(connection, true)` | reloads every in-memory manager (taxa, geolocales, projects, museums, groups, ...); pages show "initializing" meanwhile; also increments the specimen upload id |

## 5. Restart logic

| Piece | Where | Rule |
|---|---|---|
| Host poll | `bin/reboot_checker.sh`, host cron every 5 minutes | `curl http://127.0.0.1:8081/util.do?action=isRestart` (10 s). Answer `<b>true</b>` or a page containing `case#`: `docker restart antweb_antweb_1` and a line in `../logs/detail/rebootCheck.log`. `<b>false</b>`: nothing. Empty (app down): logged, nothing. `--dry-run` prints the URL and the answer. |
| App answer | `util/UtilAction` (`isRestart`, line 230) calls `AntwebSystem.isRestart()` | false while any upload holds the upload flag (a curator's, the image uploader's or the nightly TaxonWorks upload); true if `Scheduler.isStuckInSet1()`; true if `AntwebSystem.setIsRestart(true)` was called |
| Stuck in set 1 | `Scheduler.isStuckInSet1()` (line 299), `SET1_STUCK_MINUTES = 210` (line 297) | the compute flag contains "set1" and was set at least 210 minutes ago (8:30 AM for the 5 AM run); checked each time the host asks; logged once to compute.log |
| After the run | `Scheduler.doAction()` line 250 | only with `nightlyRestart=true` in `etc/scheduler.properties` (host `/root/antweb/etc/`, container `/antweb/deploy/etc/`, gitignored, read at the end of each run); off since 2026-10-18 |
| Manual | `utilData.do?action=setIsRestart&param=true` (admin) | sets the flag; `param=false` clears it |
| Hourly watchdog | `/root/antweb-watchdog.sh` on the host (not in the repo), minute 15 | restarts the container when a Java thread is above 95% CPU in two snapshots 30 s apart and the container has been up more than 12 hours (6-hour cooldown); see section 8 |

After any restart AntWeb needs 2 to 4 minutes to load its caches. A restart during a set loses that set's uncommitted work; the run is not resumed (the next attempt is the next 5:00).

## 6. Staging differences

- Set 5 skipped in the nightly run; caches reloaded instead (`AntwebMgr.populate`). An admin can still run `utilData.do?action=set5`.
- The AntCat size guard always passes on staging (`validateWorldantsFile()`), so staging loads whatever file it gets.
- Staging fetches the same `antcat-export` file (10.124.0.4) as production.
- TaxonWorks: runs only if `/root/antweb-next/etc/taxonworks.properties` exists on staging.
- Upload emails are off by default on staging.

## 7. Logs

| Log | Content |
|---|---|
| `web/log/compute.log` | Begin/End of each run, per-step lines written by the Db classes ("Populating Geolocales", "Museums populated", ...), the name update line ("name update: N specimens in M groups (mode)", "name update: off", "... FAILED"), staging set 5 skip, restart decision with heap use, "Scheduler FAILED", "set1 still running after 210 minutes" |
| `web/log/admin.log` | "worldantsFetchAndReload: <result>", "taxonWorksNightly: <result>", "Scheduler complete." (manual runs) |
| `web/log/taxonworksNightly.log` | one line per TaxonWorks run (and the "waiting" line) |
| `web/log/worldants/<date>-fetchAndReloadWorldants.html` | AntCat import report |
| `web/log/specimen/<date>-taxonWorksUpload.html` | TaxonWorks upload report |
| `web/log/getUrl.txt` | each set URL requested (secure code masked by `HttpUtil.forLog`) |
| `web/log/geolocaleTaxonFix.log`, `taxonSet.log` | step details |
| Tomcat log (`docker logs antweb_antweb_1`) | everything else, including a set's "Error. No changes made" |
| host `../logs/detail/rebootCheck.log`, `/var/log/antweb-watchdog.log` | restarts |

Admins read the web/log files through `logMgr.do`; only the upload report folders are public.

## 8. Known weak points (details and tests in [test-gaps-uploads-nightly.md](test-gaps-uploads-nightly.md))

- A set that fails answers "Error. No changes made ..."; the Scheduler only stops on "Unexpected error", a text that appears nowhere else in the code, so it goes on with the next set and nothing reaches compute.log.
- In the 8-day production traffic sample used by the function catalog (2026-10-11 to 10-18), `utilData.do?action=set1` was requested 8 times but sets 2 to 5 only 3 times each. Something stopped the run after set 1 on about 5 nights (check compute.log for those dates; candidates: deploy restarts during the run, the hourly watchdog, the set 1 connection being closed by the pool after 60 minutes).
- `longConPool` destroys a connection held longer than 3600 seconds (`WEB-INF/struts-configDbAnt.xml` line 82). Set 1 holds one connection for the AntCat import, up to 90 minutes of TaxonWorks waiting and the upload; set 5 runs about 51 minutes.
- The hourly watchdog can restart the container in the middle of a set if a set thread is busy at minute 15 and the container has been up for 12 hours (more likely now that the nightly restart is off).
- `UtilDataAction` does not refuse to start when another compute is running, and clears the shared flag when it ends; an admin running a set by hand during the nightly can let curator uploads in while the nightly is still running, and an admin `schedule.do` during the nightly replaces and then clears the secure code.
