# Data extract: a complete specimen file for a collaborator

**Where:** Curate page, admin part, **Manager tools**, **Data extract** (`https://www.antweb.org/dataExtract.do`).
Administrators only.

**What it is for:** a collaborator asks for "all AntWeb specimens of the United States, Canada and Mexico". The page
makes that file on the server and proves it is complete, so you can send it without asking a developer and without
a command line. It only reads the database; it changes nothing on AntWeb.

Why it exists: the specimen file made for the Ants of the Nearctic project in March 2026 was short (Stenamma had 14
of 42 species) and nothing in the file said so. Every extract now comes with a count that is checked against the
database.

## Making an extract

1. **Countries.** Type the country names as they are written on AntWeb's country pages, separated by commas:
   `United States, Canada, Mexico`. Or open "Or pick from AntWeb's countries" and pick them from the list. Capital
   letters do not matter. If AntWeb does not know a name (USA, for example), nothing starts and the page tells you
   which name to correct.
2. **Include islands filed under these countries** (ticked unless you clear it). AntWeb treats some islands as
   countries of their own, Hawaii under the United States for example. With the box ticked their specimens are in
   the file, and the manifest names the islands that were included.
3. **Species list of a bioregion** (optional). Choose Nearctic, for example, to also get AntWeb's species list of
   that bioregion, each name with its number of specimens in the extract.
4. **Note** (optional): who the extract is for. It is for your own records; it is not written into the files.
5. Press **Start the extract**. You are taken to the job's page, which updates itself. A small country takes
   seconds, a large one may take several minutes. You may close the page: the job continues, and you find it again
   under **Uploads and data jobs** or in the list at the bottom of the Data extract page.

## Reading the result

The job's page shows one of two things at the top:

- **VERIFIED**: the file holds exactly the specimens the database has for these places. This is the extract to send.
- **NOT VERIFIED** (the job is listed as "Did not complete"): the number of specimens written and the database's
  own count differ. The usual reason is an upload that ran at the same moment. Do not send these files; start the
  extract again.

Below it is the manifest: the countries and islands, the number of specimens in all and by country and by
bioregion, how many have no coordinates, how many have a name AntWeb does not know, how many were red-flagged at
upload, and a line to cite AntWeb.

## The files

| File | What it holds |
|---|---|
| **Everything in one zip file** | all the files below in one download: the file to send |
| `specimens.csv.gz` | one line per specimen: every column of AntWeb's specimen record, then the status, fossil mark, rank, current valid name and author of its name, the name of its upload group, `no_taxon_row` and `has_coordinates` |
| `genus_summary.csv` | per genus: specimens, names, names with at least one specimen with coordinates (and the same for the chosen bioregion) |
| `bioregion_list.csv` | only with a bioregion: AntWeb's species list of it, each name with its specimens in the extract. A name with 0 comes from the literature or from a country outside the extract |
| `manifest.txt` | what was asked for, the counts and the check, to read |
| `manifest.json` | the same for a program, with the checksum (sha256) of each file |

Nothing is left out of `specimens.csv.gz`:

- specimens **without coordinates** are in the file, with `has_coordinates` = 0 (a cut by a map shape would lose
  them);
- specimens whose name has **no taxon record** on AntWeb are in the file, with `no_taxon_row` = 1 (the API leaves
  those out);
- red-flagged specimens are in the file; the column `flag` says so.

`specimens.csv.gz` is a compressed CSV file in UTF-8. The collaborator unzips it and opens it in R, Python or a
spreadsheet (a very large file is too big for Excel).

## Rules

- **One extract at a time.** While one is waiting or running, a second one is not started.
- **Not during the daily update** (05:00 to about 08:00 Pacific) or while another data job runs: the page says so
  and nothing starts. Try again afterwards.
- **Kept for 30 days.** The extracts on the server are listed at the bottom of the page with their date, who
  started them, the countries, the note, the number of specimens and VERIFIED or not. After 30 days the files are
  removed automatically; the job stays listed under Uploads and data jobs, so there is a record of who made which
  extract.
- **Only administrators** can open the page or download a file. The files are not reachable by any web address
  other than the download links.
- The server needs 1 GB of free disk space to start an extract.

## For developers

The engine is `DataExtract` (a port of `tools/export/specimen_extract.py`, which stays for use on a server and
writes the same files), run by `DataExtractJob` as a background job of kind `extract`; the files are handled only by
`DataExtractFiles`. The flow, the folder and the access rules are in
[../flows/data-extract.md](../flows/data-extract.md). Tests: `DataExtractTest`, `DataExtractDbTest` (CI),
`tests/e2e/test_data_extract.py` (staging).
