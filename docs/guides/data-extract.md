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
   letters do not matter. If AntWeb does not know a name, or keeps it only as an old name (USA, for example),
   nothing starts and the page tells you which name to correct and, for an old name, the valid one.
2. **Include islands filed under these countries** (ticked unless you clear it). AntWeb treats some islands as
   countries of their own, Hawaii under the United States for example. With the box ticked their specimens are in
   the file, and the manifest names the islands that were included.
3. **Include specimens that are not ants** (not ticked unless you tick it). An extract holds **ants only**: family
   Formicidae, and the few records with no family given. AntWeb also holds beetles, wasps, spiders and other
   animals found with ants (about 1,300 of the 200,000 specimens of the United States, Canada and Mexico); they
   are left out, and the manifest says how many, by family. Tick the box only when the collaborator wants those
   too.
4. **Also a file of the specimens that have no country** (not ticked unless you tick it). About 1,600 specimen
   records on AntWeb have no country at all, so no choice of countries finds them, and some of them belong to
   the countries you ask for. With the box ticked they come in a file of their own
   (`specimens_no_country.csv.gz`: all of AntWeb's, the same columns, ants only unless the box above is ticked),
   for you or the collaborator to look through. The manifest counts them by upload group.
5. **Species list of a bioregion** (optional). Choose Nearctic, for example, to also get AntWeb's species list of
   that bioregion, each name with its number of specimens in the extract. The list follows the same choice: ants
   only unless you ticked the box, and the manifest says how many names of other families it leaves out.
6. **Note** (optional): who the extract is for. It is for your own records; it is not written into the files.
7. Press **Start the extract**. You are taken to the job's page, which updates itself. A small country takes
   seconds, a large one may take several minutes. You may close the page: the job continues, and you find it again
   under **Uploads and data jobs** or in the list at the bottom of the Data extract page.

## Reading the result

The job's page shows one of two things at the top:

- **VERIFIED**: the file holds exactly the specimens the database has for these places. This is the extract to send.
- **NOT VERIFIED** (the job is listed as "Did not complete"): the number of specimens written and the database's
  own count differ. The usual reason is an upload that ran at the same moment. Do not send these files; start the
  extract again. There is no zip file for such an extract, every link says "NOT VERIFIED, do not send", and a file
  you download anyway is saved as `antweb-extract-NOT-VERIFIED-...`.

Two more things can appear under it:

- **"AntWeb has no specimen for: ..."**: a country you asked for has no specimen at all. The extract is still
  complete for what AntWeb holds; check that this is the country you meant.
- **"Specimens still filed under an old country name are included"**: some specimen records still say, for
  example, USA instead of United States. They belong to the country you asked for, so they are in the file; the
  manifest names the old names and counts those specimens ("Filed under an outdated country name").

Below that is the manifest: the countries and islands, the number of specimens in all and by country and by
bioregion, how many have no coordinates, how many have a name AntWeb does not know, how many were red-flagged at
upload, and a line to cite AntWeb. If a country is written in more than one way in the records (Mexico with and
without an accent, for example), each spelling has its own line and a line names the other spellings; they are all
in the file.

## The files

| File | What it holds |
|---|---|
| **Everything in one zip file** | all the files below in one download: the file to send |
| `specimens.csv.gz` | one line per ant specimen (or per specimen of any family, if you ticked the box): every column of AntWeb's specimen record, then the status, fossil mark, rank, current valid name and author of its name, the name of its upload group, `no_taxon_row` and `has_coordinates` |
| `specimens_no_country.csv.gz` | only when asked for: every specimen of AntWeb that has no country (same columns) |
| `genus_summary.csv` | per genus: specimens, names, names with at least one specimen with coordinates (and the same for the chosen bioregion) |
| `bioregion_list.csv` | only with a bioregion: AntWeb's species list of it, each name with its specimens in the extract. A name with 0 comes from the literature or from a country outside the extract |
| `manifest.txt` | what was asked for, the counts and the check, to read |
| `manifest.json` | the same for a program, with the checksum (sha256) of each file |

Of the ants of the countries, nothing is left out of `specimens.csv.gz` (the manifest's line "Ants only (family
Formicidae): N specimens of other families are left out" says what the choice of families left out; the words
"--all-families" in it are the name of the same choice in the command-line tool):

- specimens **without coordinates** are in the file, with `has_coordinates` = 0 (a cut by a map shape would lose
  them);
- specimens whose name has **no taxon record** on AntWeb are in the file, with `no_taxon_row` = 1 (the API leaves
  those out);
- red-flagged specimens are in the file; the column `flag` says so.

`specimens.csv.gz` is a compressed CSV file in UTF-8. The collaborator unzips it and opens it in R, Python or a
spreadsheet (a very large file is too big for Excel). In a spreadsheet, import the columns **as text**: the values
are written exactly as they are in the database, also when one starts with =, +, - or @, which a spreadsheet would
otherwise take for a formula. The manifest says the same.

### What leaves AntWeb with the file

The file holds every column of the specimen record. Besides the specimen data shown on the site that includes:

- `other`: every column of the curator's upload file that AntWeb has no column of its own for (on the site only
  curators see it);
- `access_group`, `access_login`, `upload_id`: the numbers of the upload group, the login and the upload that
  loaded the record (numbers only; `group_name` is the group's name);
- `backup_file_name`, `line_num`: the uploaded file the record came from and its line;
- `flag`, `issue`: the upload checks (red flag and its reason);
- the names of collectors and determiners (`collectedby`, `determinedby`), as on the site.

There are no login names, email addresses or passwords in any file, and your note is not in them.

## Rules

- **One extract at a time.** While one is waiting or running, a second one is not started.
- **Not during the daily update** (05:00 to about 08:00 Pacific) or while another data job runs: the page says so
  and nothing starts. Try again afterwards.
- **Kept for 30 days.** The extracts on the server are listed at the bottom of the page with their date, who
  started them, the countries, the note, the number of specimens and VERIFIED or not. After 30 days the files are
  removed automatically; the job stays listed under Uploads and data jobs, so there is a record of who made which
  extract.
- **Delete.** The button at the end of an extract's line removes its files at once (not while it is running). The
  job stays listed; its download links then answer "not found".
- **Only administrators** can open the page or download a file. The files are kept outside the website's own
  folder: no web address leads to them other than the download links.
- **Disk.** The server needs 1 GB of free disk space to start an extract, and all extracts together may use 5 GB;
  above that, delete one first. An extract stops by itself if the disk runs low while it is written, or after 50
  minutes (ask for fewer countries at a time); it then leaves no files.
- **While an extract runs** the specimens are read a few thousand at a time. A record that changes at that moment
  is in the file as it was when it was read; if records were added or removed the check says NOT VERIFIED.

## For developers

The engine is `DataExtract` (a port of `tools/export/specimen_extract.py`, which stays for use on a server and
writes the same files), run by `DataExtractJob` as a background job of kind `extract`; the files are handled only by
`DataExtractFiles`. The flow, the folder and the access rules are in
[../flows/data-extract.md](../flows/data-extract.md). Tests: `DataExtractTest`, `DataExtractDbTest` (CI),
`tests/e2e/test_data_extract.py` (staging).
