# Data extract (`dataExtract.do`)

**In plain language.** An administrator fills in a form (countries, a bioregion, a note) and AntWeb writes,
in the background, a complete specimen file for those countries with a manifest whose counts are checked against
the database. The administrator downloads the files from the job's page and sends them to the collaborator. Nothing
on AntWeb changes. The guide for the person using it is [../guides/data-extract.md](../guides/data-extract.md).

Added 2026-11-10 (Brian Fisher: the Ants of the Nearctic extract of March 2026 was short and nothing said so).
Islands: the rule of 2026-11-11, below ("An island is its own country").

## Steps

| Step | Code | What happens |
|---|---|---|
| 1. The form | `curate/DataExtractAction` (GET), `web/curate/dataExtract.jsp` + `dataExtract-body.jsp` | `Check.initLogin`, `Check.admin` (and `adminCheck.jsp` in the page). Shows the form, the country pick list (`geolocale`, `georank = 'country'`, valid rows), the bioregions (`bioregion`), and the extracts on the server (`DataExtractFiles.list`) with a Delete button each. Removes extracts older than 30 days and the half-written files of unfinished ones first. The form's two boxes: "Include specimens that are not ants" and "Also a file of the specimens that have no country", both clear unless ticked. No box for islands (2026-11-11): a note says that an island is its own country and lists AntWeb's island countries with their political parents (`islandCountries`: valid country rows with `is_island = 1`). A GET never starts or deletes anything, whatever its parameters. |
| 2. Start | `DataExtractAction` (POST, form token checked by `SessionRequestFilter`) | `check()`: `DataExtract.parseCountries`, `resolve` (each name must have a **valid** country row; the name is taken as the table spells it; a row that is not valid is refused, with its valid name when it has one), `resolveBioregion`. `cannotStartNow()`: refused while `UtilDataAction.isInComputeProcess()` or `Scheduler.isRunning()` (the daily update or another data job, the same check `utilData.do` makes), while another extract waits or runs, with less than 1 GB of free disk, or when the extracts kept use more than 5 GB (`DataExtractJob.diskRefusal`). A refusal shows the form again with one sentence; nothing is started. Otherwise `DataExtractJob.submit` and a redirect to `jobStatus.do?id=N`. `op=delete` with `job=N` removes that extract's folder (not a running one). |
| 3. The job | `util/DataExtractJob.run` on the one background thread (`BackgroundJobs`, kind `extract`, action `dataExtract`) | Waits at the application gate like every background job (daily update, another data job, an upload). Checks the disk again, makes the folder (a folder that already has the job number is removed first), writes `request.json` (who, note, filters), takes a connection from `longConPool` and runs `DataExtract.run`. Step text: "Writing and checking the files: N specimens written so far." (also between the count and check phases, so the number can stand still while they run) Every 5,000 rows it stops the run if less than 512 MB of disk is free or 50 minutes have passed (the long pool takes back a connection held for an hour). |
| 4. The engine | `util/DataExtract` | Connection read-only, READ COMMITTED, autocommit, results as utf8mb4, given back as it was (a setting that cannot be put back is logged). Islands (`DataExtract.islands`): the valid country rows with `is_island = 1`; the ones named in the request are included, every other one is left out, and the ones whose `parent` or `country` is a requested country are named in the manifest as left out (see "An island is its own country"). Old names: country rows that are not valid and whose `valid_name` is one of the countries; their specimens are part of the filter. The specimens are read **in pages by primary key** (`pageSql`: the filter, `s.code > ?`, `order by s.code limit 10000`), each page streamed (`setFetchSize(Integer.MIN_VALUE)`) and its statement closed before the next: one row in memory at a time, and no statement or transaction open between pages. Each row is counted (`Stats`) and written to `specimens.csv.gz.part`, renamed when complete. Then a `count(*)` per (country, island country), grouped by the bytes of the names (`hex(convert(... using utf8mb4))`), is compared with the rows written (`compareCounts`): equal = verified. Then the count per requested country, of what the file can hold (ants, when ants only; 0 = warning), `genus_summary.csv`, `bioregion_list.csv` (with a bioregion), `manifest.txt`, and last `manifest.json` (written under another name and moved in one step) with the sha256 of each file. |
| 5. The result | `DataExtractJob.resultHtml`, `JobStatusAction` | The job's result (stored in `background_job.result`): VERIFIED or NOT VERIFIED, the warning for a country without specimens, the islands left out ("Not in this extract: Hawaii"), the old names included, the note, the manifest text, the download links. Verified: the job is "Finished". Not verified: "Did not complete" (the command-line tool ends with an error in the same case); the files stay, without a zip and labelled NOT VERIFIED. A line goes to `admin.log`. On an error, a stop or an `Error` the data files are deleted; `request.json` stays so the page lists the extract as "did not finish". |
| 6. Download | `curate/DataExtractFileAction` (`dataExtractFile.do?job=N&file=NAME`) | `Check.login`, `Check.admin`. `DataExtractFiles.file(root, job, name)`: the folder is found by listing the root and comparing the job number (the newest date if two have it), the name must be one of `DataExtractFiles.DOWNLOADS`, `manifest.json` must be there and readable with `specimens` and `verified` (the extract finished), and the file must be a plain file (no link). `file=all.zip` streams every file of a **verified** extract as one zip. Anything else is a 404. `Content-Disposition: attachment; filename="antweb-extract-<date>-<job>-<name>"` (`antweb-extract-NOT-VERIFIED-...` when not verified), `no-store`. Each download is noted in `admin.log`. |
| 7. Removal | `DataExtractFiles.removeOlderThan`, `removeUnfinished`; `DataExtractJob.dailyCleanup` | Folders whose date is more than 30 days old are deleted (plain files, then the folder; never into sub-folders, never through a link), and the half-written files of an extract the server stopped in the middle of (never the running one: the active job is asked right before, and the folder read again): after each daily update (`Scheduler`, a line in compute.log, before the log size check) and when the page is opened. |

## Where the files are

`[Tomcat folder]/logs/extracts/[yyyy-mm-dd]-[job]/`: in the container `/usr/local/tomcat/logs/extracts/`, on the
host `logs/extracts/` in the project folder (`docker-compose.yml` mounts `./logs` there, so a rebuilt container
keeps them; `antweb-fix-perms` gives the folder to the user tomcat at every start).

It is **outside the web application's folder** (`/usr/local/antweb`, Tomcat's docBase `/data/antweb`), so no
address reaches it: not through Caddy, and not for a request that comes to Tomcat directly (host port 8081,
another container) or after a change of the Caddy files. Review of 2026-11-10 (M1): the first version kept the
folder under the data logs (`/usr/local/antweb/log/extracts`), inside the web root, private only by Caddy's rule
for `/log/*`. No compose or `web.xml` change was needed for the move. Caddy's `/log/*` rule stays as it was (the
data logs are still there) and `tests/e2e/test_data_extract.py` checks both Caddy files and, on staging, asks for
the real folder of a real extract under every address it could be guessed at.

Do not move it to `logs/web` (that is the web application's `web/log`), anywhere under the data root (the data
root is the web root), or to the Spaces mounts (`/mnt/antweb` is the image bucket). `bin/clearDisk.py`, which
tidies Tomcat's logs every night, skips `extracts/`.

## What "verified" means, and what it does not

- The rows written per (country, island country), **by the exact spelling of the names**, equal a count of the
  database taken after the last page. MySQL compares names without case or accents, so `country in ('Mexico')`
  also finds "MEXICO" and Mexico with an accent; the file and the count keep such spellings apart and the manifest
  lists them ("Country spellings in the file besides the names asked for").
- The filter is complete for the countries asked for: specimens filed under an old name of a country (a
  `geolocale` country row that is not valid and names the country as `valid_name`) are included, named and counted.
  A requested country without any specimen the file could hold (no ant specimen, in an ants-only extract) is a
  WARNING line in the manifest and on the job's page.
- An island that is its own country is **not** part of the filter of its political parent; the manifest and the
  job's page name it, so a reader of a United States extract sees that Hawaii is not in it.
- It is not one moment of the database. The specimens are read in pages so that no statement stays open (an
  `ALTER TABLE` during an extract waited for the whole extract before, and the site queued behind it). A record
  changed while the extract runs is in the file as it was when its page was read; records added behind the page
  being read are missed and the count then differs (not verified). Uploads started from a page cannot run beside
  an extract (one background job at a time); the daily TaxonWorks upload can, if an extract is started just
  before 05:00.

## An island is its own country (formats 9 and 10)

Brian Fisher, 2026-11-11: "An oceanic island or archipelago with its own ant fauna is its own country in AntWeb.
Its specimens never count toward the political parent." (The Nearctic extract of all ants of the United States
held Hawaii, which had to be removed by hand.) This reverses the rule of 2026-11-10, when the islands filed under
a country came with it and the form had a box "Include islands filed under these countries", ticked by default.

- **Which places.** A valid `geolocale` country row with `is_island = 1` (Hawaii, Galapagos Islands). Its
  political parent is the row's `parent` or `country`.
- **Asked for like any country.** `United States` gives no specimen of Hawaii; `United States, Hawaii` gives both.
  There is no switch.
- **Both stored forms.** Until the stored specimens are moved on a server (`tools/place/island_countries.py
  --apply`, the one-time step of the island-countries work, pull request 345; see the runbook), a specimen of
  Hawaii has `country = United States` and
  `island_country = Hawaii`; afterwards `country = Hawaii`. The filter reads both columns
  (`whereSql(countries, islands, antsOnly, otherIslands)`): a specimen marked as the specimen of an island that was
  not asked for is left out whatever its country column says, and a named island is found by its own name in
  either column. So the file is the same before and after the move, and the page does not depend on it.
- **Old island names.** A country row that is not valid and names an island as its `valid_name` (Galapagos for
  Galapagos Islands) is an old name of the island: a specimen marked with it (`island_country`) is the island's.
  Manifest `old_island_names`.
- **The manifest** (`islands`, `islands_left_out`, `old_island_names`): "Islands included (named in the
  countries)", "Islands of these countries that are their own country in AntWeb, NOT in this file: Hawaii (name an
  island with the countries to include it)", "Old island names read as the island". The job's page repeats the
  second one ("Not in this extract"), and the list of extracts says "not in the file: Hawaii".
- **The warning count** of a named island counts both forms too (`perCountrySql`).
- **No specimen in two files.** A specimen with no country that is filed under an included island (country empty,
  `island_country = Hawaii`) is in `specimens.csv.gz`; `noCountryWhere` keeps it out of the file of the specimens
  without a country.
- Not covered: the count behind the WARNING for a country that is **not** an island is by the country column alone
  (as in the tool). Before the stored specimens are moved, a parent whose only specimens are an island's (none on
  the mainland) gets no warning although the file is empty. After the move it does.

## Ants only, and the specimens without a country (formats 5 and 6)

Brian Fisher, 2026-11-10: "why did you include non ants for an ant study?" AntWeb also holds beetles, wasps,
spiders and other animals found with ants (production: 1,289 of the 200,075 specimens of United States, Canada and
Mexico).

- **Ants only is the default** (the form's box "Include specimens that are not ants" is clear; the tool's
  `--all-families`). The filter is `DataExtract.ANTS`: `s.family = 'formicidae'`, or no family given (those are
  kept and counted: "No family given"). The pages of the export, the count the file is checked against and the
  no-country file all use it, so the check compares like with like.
- What is left out is counted by family (`leftOutSql`; manifest `left_out_not_formicidae`, the line "Ants only
  (family Formicidae): N specimens of other families are left out (the six largest ...)").
- A row in an ants-only file whose family does not read as Formicidae (the database compares without accents or
  case, the file's text does not) makes the extract **not verified**
  (`not_formicidae_in_an_ants_only_file` among the differences).
- The bioregion's species list follows the same choice on the taxon table (`T_ANTS`), and
  `bioregion_list.names_left_out_not_formicidae` says how many names of other families it leaves out.
- **The specimens without a country** (production: 1,623; no country filter finds them, and some belong to the
  countries asked for): on request (the form's box, the tool's `--with-no-country`) every one of them goes to
  `specimens_no_country.csv.gz`, read in pages like the main file, with the same columns and the same choice of
  families; the manifest's `no_country` counts them (in the file, in the database, with coordinates, names, names
  not in the countries file, by upload group) and a different count makes the extract not verified. The genus
  summary and the bioregion list are of the main file only.

## Who ran which extract

- `background_job`: one row per extract (kind `extract`, `login_id`, `login_name`, the countries in `title`, the
  result with the note, the times). Kept.
- `request.json` in the extract's folder: job, login, note, countries, all families, the no-country file, bioregion. Kept as long as the
  files (30 days). Never downloadable.
- `admin.log`: one line per extract, one per download, one per Delete. The note is put on one line first
  (`DataExtractJob.cleanNote`), so it cannot start a line of its own in the log.

No new table.

## The same files as the command-line tool

`tools/export/specimen_extract.py` (run on a server by an administrator) and `DataExtract` write the same files
(format 10, the tool with the island rule of 2026-11-11, pull request 345;
`tests/unit/test_data_extract_tool_version.py` fails when the two format numbers differ): the same columns, the same CSV quoting (Python's `csv` default: a field is quoted only when it holds a
comma, a quote or a line break), NULL as an empty field, numbers and dates as the database prints them (the Java
code takes their text from the raw answer, not through Java's number formatting), the same filter (old country
names included), the same manifest keys and text. Checked on 2026-11-10 by running both against the same database
(old names, three spellings of a country, a country without specimens, a four-byte character, specimens of eight
other families, specimens with no family and with no country, a species list with names of other families), for
the four combinations of the two boxes: the CSV files were
identical byte for byte after unzipping, and the manifests identical except for the time and the checksum of the
gzip file. Checked again on 2026-11-12 against the tool of format 10, with islands in both stored forms, old island
names, a specimen with no country under an island and an island that is not asked for, for eleven requests (the
parent alone, the parent with the island, the island alone, several islands; ants only and all families; with and
without the no-country file): the same result. Keep the two in step: a change of the columns or the manifest goes into both, with `VERSION` raised in
both.

Differences on purpose:


- The page takes a country name as the database compares it (so `united states` finds United States); the
  command-line tool needs the exact spelling. Both refuse a name that is not a valid country row.
- The page reads in pages by code; the tool reads with one statement (run it when no change of the tables is
  planned).
- The tool cannot tell the text NULL from a missing value (mysql prints both the same) and writes both as an
  empty field; the page keeps the text.

## Tests

| Test | What it covers |
|---|---|
| `test/.../util/DataExtractTest` (CI, no database) | CSV, manifest text and JSON equal to what the Python tool and Python's `csv` and `json` write; the file lookup (no path, no link, no unfinished extract, a half-written manifest, a job number in two folders); the folder outside the web application; the zip, and none for an extract that is not verified; removal after 30 days and of unfinished extracts; the disk limit; the note on one line; the list; the result and form HTML escaped |
| `test/.../util/DataExtractDbTest` (CI, MySQL 8.4) | the cases of `tests/unit/test_specimen_extract.py` against a real database: complete and verified, special characters, NULL, numbers and dates, a zero date, 0/0 coordinates, a name without taxon row, an island named with the countries, a count that differs, unknown and not-valid names, old names included, a country without specimens (and one with a beetle only), two spellings, an island never in its political parent's extract (both stored forms, old island names, no specimen in two files), read-only, pages of any size give the same file, a change of the table during the extract does not wait, a four-byte character, 12,000 rows |
| `tests/e2e/test_data_extract.py` (staging) | access (anonymous, curator, admin), a wrong name, a GET starts and deletes nothing, downloads refused, a small country's extract from the form to the downloaded files, its real folder under every guessable address, Delete; Ecuador without the Galapagos Islands and the Galapagos Islands alone |
| `tests/unit/test_specimen_extract.py` | the command-line tool |
| `tests/unit/test_data_extract_tool_version.py` | the page and the tool have the same format number and the same manifest lines for islands; no text of the page says a country's islands come with it |
