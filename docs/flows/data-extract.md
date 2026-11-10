# Data extract (`dataExtract.do`)

**In plain language.** An administrator fills in a form (countries, islands, a bioregion, a note) and AntWeb writes,
in the background, a complete specimen file for those countries with a manifest whose counts are checked against
the database. The administrator downloads the files from the job's page and sends them to the collaborator. Nothing
on AntWeb changes. The guide for the person using it is [../guides/data-extract.md](../guides/data-extract.md).

Added 2026-11-10 (Brian Fisher: the Ants of the Nearctic extract of March 2026 was short and nothing said so).

## Steps

| Step | Code | What happens |
|---|---|---|
| 1. The form | `curate/DataExtractAction` (GET), `web/curate/dataExtract.jsp` + `dataExtract-body.jsp` | `Check.initLogin`, `Check.admin` (and `adminCheck.jsp` in the page). Shows the form, the country pick list (`geolocale`, `georank = 'country'`, valid rows), the bioregions (`bioregion`), and the extracts on the server (`DataExtractFiles.list`) with a Delete button each. Removes extracts older than 30 days and the half-written files of unfinished ones first. A GET never starts or deletes anything, whatever its parameters. |
| 2. Start | `DataExtractAction` (POST, form token checked by `SessionRequestFilter`) | `check()`: `DataExtract.parseCountries`, `resolve` (each name must have a **valid** country row; the name is taken as the table spells it; a row that is not valid is refused, with its valid name when it has one), `resolveBioregion`. `cannotStartNow()`: refused while `UtilDataAction.isInComputeProcess()` or `Scheduler.isRunning()` (the daily update or another data job, the same check `utilData.do` makes), while another extract waits or runs, with less than 1 GB of free disk, or when the extracts kept use more than 5 GB (`DataExtractJob.diskRefusal`). A refusal shows the form again with one sentence; nothing is started. Otherwise `DataExtractJob.submit` and a redirect to `jobStatus.do?id=N`. `op=delete` with `job=N` removes that extract's folder (not a running one). |
| 3. The job | `util/DataExtractJob.run` on the one background thread (`BackgroundJobs`, kind `extract`, action `dataExtract`) | Waits at the application gate like every background job (daily update, another data job, an upload). Checks the disk again, makes the folder (a folder that already has the job number is removed first), writes `request.json` (who, note, filters), takes a connection from `longConPool` and runs `DataExtract.run`. Step text: "Writing specimens: N so far." Every 5,000 rows it stops the run if less than 512 MB of disk is free or 50 minutes have passed (the long pool takes back a connection held for an hour). |
| 4. The engine | `util/DataExtract` | Connection read-only, READ COMMITTED, autocommit, results as utf8mb4, given back as it was (a setting that cannot be put back is logged). Islands: `geolocale` rows with `is_island = 1` whose `parent` or `country` is one of the countries. Old names: country rows that are not valid and whose `valid_name` is one of the countries; their specimens are part of the filter. The specimens are read **in pages by primary key** (`pageSql`: the filter, `s.code > ?`, `order by s.code limit 10000`), each page streamed (`setFetchSize(Integer.MIN_VALUE)`) and its statement closed before the next: one row in memory at a time, and no statement or transaction open between pages. Each row is counted (`Stats`) and written to `specimens.csv.gz.part`, renamed when complete. Then a `count(*)` per (country, island country), grouped by the bytes of the names (`hex(convert(... using utf8mb4))`), is compared with the rows written (`compareCounts`): equal = verified. Then the count per requested country (0 = warning), `genus_summary.csv`, `bioregion_list.csv` (with a bioregion), `manifest.txt`, and last `manifest.json` (written under another name and moved in one step) with the sha256 of each file. |
| 5. The result | `DataExtractJob.resultHtml`, `JobStatusAction` | The job's result (stored in `background_job.result`): VERIFIED or NOT VERIFIED, the warning for a country without specimens, the old names included, the note, the manifest text, the download links. Verified: the job is "Finished". Not verified: "Did not complete" (the command-line tool ends with an error in the same case); the files stay, without a zip and labelled NOT VERIFIED. A line goes to `admin.log`. On an error, a stop or an `Error` the data files are deleted; `request.json` stays so the page lists the extract as "did not finish". |
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
  A requested country without any specimen is a WARNING line in the manifest and on the job's page.
- It is not one moment of the database. The specimens are read in pages so that no statement stays open (an
  `ALTER TABLE` during an extract waited for the whole extract before, and the site queued behind it). A record
  changed while the extract runs is in the file as it was when its page was read; records added behind the page
  being read are missed and the count then differs (not verified). Uploads started from a page cannot run beside
  an extract (one background job at a time); the daily TaxonWorks upload can, if an extract is started just
  before 05:00.

## Who ran which extract

- `background_job`: one row per extract (kind `extract`, `login_id`, `login_name`, the countries in `title`, the
  result with the note, the times). Kept.
- `request.json` in the extract's folder: job, login, note, countries, islands, bioregion. Kept as long as the
  files (30 days). Never downloadable.
- `admin.log`: one line per extract, one per download, one per Delete. The note is put on one line first
  (`DataExtractJob.cleanNote`), so it cannot start a line of its own in the log.

No new table.

## The same files as the command-line tool

`tools/export/specimen_extract.py` (run on a server by an administrator) and `DataExtract` write the same files
(format 3): the same columns, the same CSV quoting (Python's `csv` default: a field is quoted only when it holds a
comma, a quote or a line break), NULL as an empty field, numbers and dates as the database prints them (the Java
code takes their text from the raw answer, not through Java's number formatting), the same filter (old country
names included), the same manifest keys and text. Checked on 2026-11-10 by running both against the same database
(old names, three spellings of a country, a country without specimens, a four-byte character): the CSV files were
identical byte for byte after unzipping, and the manifests identical except for the time and the checksum of the
gzip file. Keep the two in step: a change of the columns or the manifest goes into both, with `VERSION` raised in
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
| `test/.../util/DataExtractDbTest` (CI, MySQL 8.4) | the cases of `tests/unit/test_specimen_extract.py` against a real database: complete and verified, special characters, NULL, numbers and dates, a zero date, 0/0 coordinates, a name without taxon row, an island, a count that differs, unknown and not-valid names, old names included, a country without specimens, two spellings, islands left out, read-only, pages of any size give the same file, a change of the table during the extract does not wait, a four-byte character, 12,000 rows |
| `tests/e2e/test_data_extract.py` (staging) | access (anonymous, curator, admin), a wrong name, a GET starts and deletes nothing, downloads refused, a small country's extract from the form to the downloaded files, its real folder under every guessable address, Delete |
| `tests/unit/test_specimen_extract.py` | the command-line tool |
