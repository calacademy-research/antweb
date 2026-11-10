# Data extract (`dataExtract.do`)

**In plain language.** An administrator fills in a form (countries, islands, a bioregion, a note) and AntWeb writes,
in the background, a complete specimen file for those countries with a manifest whose counts are checked against
the database. The administrator downloads the files from the job's page and sends them to the collaborator. Nothing
on AntWeb changes. The guide for the person using it is [../guides/data-extract.md](../guides/data-extract.md).

Added 2026-11-10 (Brian Fisher: the Ants of the Nearctic extract of March 2026 was short and nothing said so).

## Steps

| Step | Code | What happens |
|---|---|---|
| 1. The form | `curate/DataExtractAction` (GET), `web/curate/dataExtract.jsp` + `dataExtract-body.jsp` | `Check.initLogin`, `Check.admin` (and `adminCheck.jsp` in the page). Shows the form, the country pick list (`geolocale`, `georank = 'country'`, valid rows), the bioregions (`bioregion`), and the extracts on the server (`DataExtractFiles.list`). Removes extracts older than 30 days first. |
| 2. Start | `DataExtractAction` (POST, form token checked by `SessionRequestFilter`) | `check()`: `DataExtract.parseCountries`, `resolve` (each name must have a country row; the name is taken as the table spells it; a row that is not valid and names its valid name is refused with that name), `resolveBioregion`. `cannotStartNow()`: refused while `UtilDataAction.isInComputeProcess()` or `Scheduler.isRunning()` (the daily update or another data job, the same check `utilData.do` makes), while another extract waits or runs, or with less than 1 GB of free disk. A refusal shows the form again with one sentence; nothing is started. Otherwise `DataExtractJob.submit` and a redirect to `jobStatus.do?id=N`. |
| 3. The job | `util/DataExtractJob.run` on the one background thread (`BackgroundJobs`, kind `extract`, action `dataExtract`) | Waits at the application gate like every background job (daily update, another data job, an upload). Makes the folder, writes `request.json` (who, note, filters), takes a connection from `longConPool` and runs `DataExtract.run`. Step text: "Writing specimens: N so far." |
| 4. The engine | `util/DataExtract` | Connection read-only, READ COMMITTED, autocommit, given back as it was. Islands: `geolocale` rows with `is_island = 1` whose `parent` or `country` is one of the countries. The specimen query (`select s.*`, the taxon columns, the group name, `no_taxon_row`, ordered by code) is streamed with a forward-only statement and `setFetchSize(Integer.MIN_VALUE)`: one row in memory at a time. Each row is counted (`Stats`) and written to `specimens.csv.gz`. Then a separate `count(*)` per (country, island country) is compared with the rows written (`compareCounts`): equal = verified. Then `genus_summary.csv`, `bioregion_list.csv` (with a bioregion), `manifest.json` and `manifest.txt` with the sha256 of each file. |
| 5. The result | `DataExtractJob.resultHtml`, `JobStatusAction` | The job's result (stored in `background_job.result`): VERIFIED or NOT VERIFIED, the note, the manifest text, the download links. Verified: the job is "Finished". Not verified: "Did not complete" (the command-line tool ends with an error in the same case); the files stay, marked DIFFERENT. A line goes to `admin.log`. On an error the data files are deleted; `request.json` stays so the page lists the extract as "did not finish". |
| 6. Download | `curate/DataExtractFileAction` (`dataExtractFile.do?job=N&file=NAME`) | `Check.login`, `Check.admin`. `DataExtractFiles.file(root, job, name)`: the folder is found by listing the root and comparing the job number, the name must be one of `DataExtractFiles.DOWNLOADS`, `manifest.json` must exist (the extract finished), and the file must be a plain file (no link). `file=all.zip` streams every file of the extract as one zip. Anything else is a 404. `Content-Disposition: attachment; filename="antweb-extract-<date>-<job>-<name>"`, `no-store`. Each download is noted in `admin.log`. |
| 7. Removal | `DataExtractFiles.removeOlderThan` | Folders whose date is more than 30 days old are deleted (plain files, then the folder; never into sub-folders, never through a link): after each daily update (`Scheduler`, a line in compute.log, before the log size check) and when the page is opened. |

## Where the files are

`[data root]/log/extracts/[yyyy-mm-dd]-[job]/`: in the container `/usr/local/antweb/log/extracts/`, on the host
`logbak/extracts/` in the project folder (so a rebuilt container keeps them). This is the folder of the "data" logs
(`emails.log`, ...), which is never served: Caddy answers 404 for `/log` and `/log/*`
(`docker/caddy/Caddyfile.common`, `@denied_paths`; `tests/e2e/test_privacy.py` and `test_data_extract.py` check it
on the running site). Do not move it under `web/log/` (some of its sub-folders are public) or to the top of the
data root (the data root is the web root and Caddy lets `*.txt` through, so `manifest.txt` would be public).

## Who ran which extract

- `background_job`: one row per extract (kind `extract`, `login_id`, `login_name`, the countries in `title`, the
  result with the note, the times). Kept.
- `request.json` in the extract's folder: job, login, note, countries, islands, bioregion. Kept as long as the
  files (30 days). Never downloadable.
- `admin.log`: one line per extract and one per download.

No new table.

## The same files as the command-line tool

`tools/export/specimen_extract.py` (run on a server by an administrator) and `DataExtract` write the same files:
the same columns, the same CSV quoting (Python's `csv` default: a field is quoted only when it holds a comma, a
quote or a line break), NULL as an empty field, numbers and dates as the database prints them (the Java code takes
their text from the raw answer, not through Java's number formatting), the same manifest keys and text. Checked
on 2026-11-10 by running both against the same database: the CSV files were identical byte for byte after
unzipping, and the manifests identical except for the time and the checksum of the gzip file. Keep the two in
step: a change of the columns or the manifest goes into both, with `VERSION` raised in both.

One difference on purpose: the page takes a country name as the database compares it (so `united states` finds
United States) and refuses an outdated name that points to a valid one; the command-line tool needs the exact
spelling.

## Tests

| Test | What it covers |
|---|---|
| `test/.../util/DataExtractTest` (CI, no database) | CSV, manifest text and JSON equal to what the Python tool and Python's `csv` and `json` write; the file lookup (no path, no link, no unfinished extract); the zip; removal after 30 days; the list; the result and form HTML escaped |
| `test/.../util/DataExtractDbTest` (CI, MySQL 8.4) | the cases of `tests/unit/test_specimen_extract.py` against a real database: complete and verified, special characters, NULL, numbers and dates, a zero date, 0/0 coordinates, a name without taxon row, an island, a count that differs, unknown names, islands left out, read-only, 12,000 rows streamed |
| `tests/e2e/test_data_extract.py` (staging) | access (anonymous, curator, admin), a wrong name, downloads refused, and a small country's extract from the form to the downloaded files |
| `tests/unit/test_specimen_extract.py` | the command-line tool |
