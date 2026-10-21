# The Curate page: a guide for curators and admins

The Curate page (**Curator Tools**, `https://www.antweb.org/curate.do`) is where you start when you upload data, check your uploads, or (for admins) manage the site. You reach it from the **Curator Tools** link after you log in.

This guide follows the page from top to bottom. Each tool says what it is for and how to use it. It covers the tools we keep; the tools removed in October 2026 are listed at the end. Where a label on the page is unclear, the guide gives the plain meaning next to it.

What you see depends on your login:

- **Curators** see the sections in part 1. The upload sections appear only if your login has the "upload specimens" or "upload images" permission.
- **Admins** also see the tools in part 2.

---

## Part 1: what every curator sees

### Help

**Curator Tool Documentation** (top of the page) opens the documentation page: the imaging manual and videos, the guides for submitting images and using the image picker, the specimen data overview and requirements spreadsheet, and the country and region lists.

### Curator Information

- **Your Curator Page**: your public page on AntWeb, listing your uploads, images and page-text edits. This is what other people see about you.
- **Manage Your Login Information**: your account. From here you change your name, email address and password.
- **View Your Specimen Upload Reports**: the list of your group's specimen uploads, newest first. Each line opens the upload report (how many specimens were loaded, which names were changed to the current valid name, which lines had problems). Check it after every upload. Line numbers in a report open that line of your upload as AntWeb stored it (your own group's uploads only).
- **View Your Image Upload Reports**: the same for image uploads: one line per upload batch, with its report.
- **Images you uploaded for taxon page text** (your curator folder): the files you uploaded for use on taxon pages (for example an image you inserted into a taxon's page text). Only you and admins see this list.

At the top of this area a red or blue line may appear when the server is busy (an upload or the nightly run is in progress, or uploads are paused). If you see one, wait and try again later.

### Specimen Data (curators with the "upload specimens" permission)

**Upload Zip File**: this is how you send your specimen data to AntWeb.

1. Put your specimen file (the tab-delimited AntWeb specimen file) in a ZIP file. Only ZIP files are accepted.
2. Click **Choose File**, pick the ZIP, then click **Submit**.
3. Wait. A large file can take several minutes. If the page shows a Cloudflare timeout, your data is still being processed: after 85 seconds the page sends you to your upload reports, and you also get an email when the upload is finished.
4. Open the upload report and check it.

Your upload replaces your group's specimen records on AntWeb. Names are matched to the current valid names in AntCat. Counts on country, region and museum pages are brought up to date in the nightly run (from 5 AM Pacific), so new specimens can take until the next morning to show in those counts.

The optional column **previousDeterminations** lets you send earlier identifications of a specimen, newest first, separated by " | " (an example is shown on the page).

**Recalculate Your Museums** (shown after you upload in the same session): recounts the museums found in your last upload now, instead of waiting for the nightly run. It only touches the museums of your own upload.

**Your past upload files**: your group's earlier uploaded specimen files; each one can be downloaded. Useful if you need a copy of what you sent before. (Admins choose the group from a list.)

### Upload Specimen Images (curators with the "upload images" permission)

**Specimen Image Upload** opens the Image Uploader. Image files must be named by specimen code and view (the naming rules are on the uploader page and in the "Submitting images" guide). You can add many files at once; each is checked, and you get a report and an email at the end.

### Read-Only Tools

These tools never change anything on AntWeb.

- **Check names against AntCat** (formerly "Validate Species List"): upload a list of species names (a tab-delimited text file) to check every name against AntWeb's names from AntCat. You get a report on screen and can download it, or download a corrected copy of your list with the current valid names. Nothing is saved on AntWeb.
- **Specimen List Lookup**: paste a list of specimen codes (one per line) and get the AntWeb data for those specimens, on screen or as a tab-delimited file. Handy for checking what AntWeb holds for a loan or a batch of material.

### AntWeb Statistics (right side)

Totals for the whole site (specimens, imaged specimens, species and so on). They are updated after uploads and in the nightly run.

### Opening a page by its id

The jump boxes that used to sit at the bottom right ("Access direct by ...", "Curious Query") were removed in October 2026. The addresses behind them still work; type them in the browser's address bar:

- `https://www.antweb.org/description.do?antcatId=429011` opens the taxon page for an AntCat taxon number (here 429011). This is the address AntCat links to.
- `https://www.antweb.org/description.do?taxonName=myrmicinaepheidole%20megacephala` opens a taxon page by its AntWeb taxon name (lower case, subfamily joined to the genus).
- `https://www.antweb.org/specimen.do?code=casent0005904` opens a specimen page.
- `https://www.antweb.org/geolocale.do?id=N` opens a country or state/province page by its AntWeb id (the ids are shown in the Geolocale Manager).

---

## Part 2: extra tools for admins

### In the upload form

Admins see two extra menus in **Upload Zip File**:

- **Upload Type**: **Antweb** (the normal AntWeb specimen file) or **TaxonWorks** (a TaxonWorks Darwin Core archive; the CAS TaxonWorks data normally arrives in the nightly run, so use this only when the nightly TaxonWorks upload failed). The GBIF option was removed in October 2026.
- **Upload as**: upload the file on behalf of another curator, so the data goes to that curator's group. The menu lists you and the curators your login may upload for, each with the group the upload goes into ("aerochild &mdash; Curator Peter Hawkes AFRC (group 31)"), sorted by group name. The upload replaces all specimens of the group shown.

### Import AntCat names now

Downloads the AntCat export and loads the names into AntWeb, as the nightly run does at 05:00 Pacific. Use it only when the nightly AntCat import failed (an admin alert or compute.log says so). It takes several minutes. (This replaces the old "Reload" link, the "Worldants" button and the staging-only "Worldants Fetch and Reload" link.)

### Open the Species List Tool

Shown when your login looks after project or region species lists. Choose a list and click **Submit** to open the **Species List Tool** for it: compare it with another list and add or remove species.

### Manager Tools

- **Login Manager**: all logins. Open one to change its details and permissions (upload specimens, upload images, admin, the projects and regions it may edit, "upload as"). The form at the top creates a new login. The page also links a table of all logins with their permissions and a list of logins by last login.
- **Group Manager**: the contributing groups (institutions). Edit a group, create one, or delete one (you are asked to confirm).
- **Museum Manager**: the museum codes used in specimen data (code, name, title, active). Edit or add a museum. **Delete** (on a museum's form) asks you to confirm on a second page; there is no undo.
- **Geolocale Manager**: countries and states/provinces (adm1): whether they are valid and shown on the site, names, bioregion and map coordinates. Curators can look; only admins can save changes. The page also links the georegion table (region, subregion, country) and the bioregion table.
- **Project Manager**: the projects (for example regional ant faunas): settings and page text. Create or delete projects here.
- **Photographers** (formerly "Artist Manager"): the photographers credited on images. Add or rename a photographer here.
- **Admin Alert Manager**: problems AntWeb has flagged for admins (for example a failed AntCat import). Remove an alert once it is dealt with. The page links the full history of alerts.
- **Bioregion Map Manager**: which bioregions a species is native to (used for the "introduced" flags and maps).
- **Introduced Map Manager**: in which bioregions a species is introduced.
- **Query Manager**: a list of ready-made database checks, by topic. Click a name to run it and see the rows. The checks only read data. Only admins can run them, except the few that public pages link to (for example the AntCat import history).
- **Log Manager**: the server logs. The most useful are compute.log (the nightly run), admin.log (AntCat imports and scheduler runs), taxonWorksNightly.log (the nightly CAS TaxonWorks upload) and the profiler log (timings of uploads and pages).

### Administrator Functions

- **Antweb Statistics**: statistics tables by project, museum, bioregion and upload.
- **Server Status**: what the server is doing now: database connections, running jobs, the switch that pauses uploads ("down time") and the pages being served. It links Database Status (connection pools) and Restart Status (whether the application has asked for a restart).
- **Technical page**: see below.
- **Site Warning**: the banner shown at the top of every page (for example before maintenance). Type the text in the box and click **Save**; **Clear the warning** removes it. Simple formatting (bold, colour, line breaks, links) is kept; anything else, such as scripts, is removed.
- **Restart server now**: asks for a restart of the AntWeb application. It happens within 5 minutes (never during an upload), and the site shows an error page for 2 to 4 minutes while it starts again. You are asked to confirm first. The link appears only when the server has been up for at least 10 minutes.

### Reports

Each report page starts with a short box: what it shows, why it matters, and what to do with a hit.

- **Integrity Check** (`query.do?action=checkIntegrity`): about 30 automatic checks of AntWeb's names, species lists and specimen data. Each check normally finds nothing; each one says what it looks for and how to fix a hit, shows "None." when it finds nothing, and a check that fails says so without stopping the others. Since October 2026 it includes the name checks of the former "Curate AntCat Queries" page: names under a synonym parent (fix the identification in TaxonWorks or the curator's upload file), country and region lists not using the current valid name (fix in the Species List Tool), and names whose parent is not among AntWeb's names (fix the identification, or check AntCat). The check for unrecognized names without specimens was dropped. Most hits are fixed at the source (TaxonWorks, the upload file or AntCat) and clear after the next upload or nightly run.
- **AntCat import history** (`query.do?name=worldantsUploads`, linked from the Log Manager and the upload reports): the last 60 imports of AntCat names, newest first, with their log files. If the newest is older than a day or reports a failure, open its log; if the AntCat export was not ready, use Import AntCat names now.
- **Homonym Report**: the homonyms AntWeb holds (names spelled like another ant name, by a different author and year). Each line opens that homonym's own page (the link includes the author and year) and its AntCat entry. If specimens were filed under a homonym by mistake, change their identification to the current valid name in TaxonWorks or the upload file.
- **Curator activity log** (formerly "Events"): curator edits to the images, videos and overview text of taxon pages, with the curator's name and date.
- **Species without images** (formerly "Unimaged Bolton Ants"): species in the AntCat list that have no image on AntWeb yet. Useful for planning imaging.
- **Recent Description Edits**: the latest edits to taxon page text, by whom.
- **Specimen parts (-D codes) with different names** (formerly "Casent -Dxx Anomalies"): specimens whose code has parts (a code ending in `-d` and digits, for example `casent0001234-d01`) where the parts, or the parts and the base specimen, carry different taxon names. Each row shows the part, its taxon name, the name as uploaded (when AntWeb replaced it with the current valid name), who determined it and when, the group that uploaded it and the museum code. Usually a data entry mistake: correct the identification in TaxonWorks or the curator's upload file. A real mixed series needs no change.
  - **Group filter**: the dropdown lists every group with at least one hit and its count, for example "California Academy of Sciences (212)"; `list.do?action=casentDAnomalies&groupId=1` is the CAS list. Use it to get the list for one institution; each curator can only fix the specimens their group uploaded. A specimen whose base record and parts were uploaded by different groups is listed under every group involved, and the rows of other groups are greyed and marked, so the curator sees the whole conflict and knows which rows are theirs.
  - **Download this list (CSV)** downloads the current list (`&format=csv`), sorted by group, then base code, to attach to an email. Columns: group, specimen code, part code, taxon name, determined by, date determined, uploaded name, uploaded by, this group can fix (yes/no), museum, specimen page. Administrators only, like the page.
- **Taxa outside their native bioregions** (`query.do?action=taxaOutsideOfNativeBioregion`, also linked from the Geolocale Manager): species and subspecies recorded in a country or adm1 that lies in a bioregion where their genus is not native (by the genus's bioregions in the Bioregion Map Manager); taxa on the introduced and fossil ant lists are left out. A list to check for misidentifications, wrong localities or introduced species not yet on the introduced list. It only reads data and opens directly (since October 2026 it no longer goes through the data-job confirmation page).
- **Orphaned images** (`/orphanedImages.do`): images whose specimen record no longer exists: images in AntWeb's image table whose specimen code is not (or no longer) in the specimen data, for example after a group removed or renamed specimens in an upload. Each line is a specimen code. Use it to find images to re-link (fix the code in the source data) or remove. It only reads data.

Elsewhere: each group's last specimen upload is linked from the Specimen Upload Reports page (admins); the number of likes per image from the Favorites page (admins); the explanation of taxon status values from the Curator Tool Documentation page.

### Museum pages: Recalculate (admins)

On a museum page (for example `https://www.antweb.org/museum.do?museumCode=BMNH`), admins see a **Recalculate** button under Curator Info. It recounts that one museum now, the same work the nightly run does for every museum (set 3, from 05:00 Pacific). Curators recount the museums of their own last upload with **Recalculate Your Museums** on the Curate page instead.

What it does, in order:

1. Resets the subfamily chart colours (the `chart_color` of each subfamily, used by the pie charts on every museum, country and project page).
2. Specimens: clears the `museum` field of the specimens marked with this museum, then marks again every specimen whose "owned by" value contains the museum code.
3. Museum totals: specimen and image counts in the `museum` table, from those specimens.
4. Museum taxa: deletes the museum's rows in `museum_taxon` and rebuilds them, one row per species or subspecies with its specimen and image counts, then the genera and subfamilies above them.
5. Count crawl: adds the counts up the tree (species to genus to subfamily).
6. Finish: the museum's subfamily, genus and species counts (valid and other countable names), imaged-specimen count, valid-species count, and the data for the two pie charts (taxa and specimens by subfamily).
7. Reloads the in-memory museum list, so the page shows the new numbers at once.

How long: a few minutes for most museums (the code notes about 4 minutes for ZMHB); a museum with many specimens, such as CASC, takes longer. The page answers when it is done. The nightly run recounts all museums in about 8 to 20 minutes.

When to use it: after a correction that should show on a museum page before tomorrow (for example a fixed "owned by" code, or a museum code just added in the Museum Manager). Otherwise the nightly run is enough. Avoid running it while an upload or the nightly run (05:00 to about 08:00 Pacific) is in progress: it rewrites the same tables. It does not change specimen data other than the `museum` field.

### Recalculate on region, project and bioregion pages (admins)

Country, adm1, project and bioregion pages have the same **Recalculate** button in their admin area. Each recounts that one page now, including the count crawl that adds the taxon counts up the tree (species to genus to subfamily). Since October 2026 there are no separate "Count Crawl" links on taxon pages or "Project Taxon Child Count Crawl" link on project pages: the project Recalculate now runs that crawl too.

### Technical page (`technical.do`, admins)

- **Reload the in-memory lists**: AntWeb keeps lists such as logins, groups, projects, regions and taxa in memory. After a change made directly in the database, reload the list concerned, or all of them.
- **Regenerate**: the recent content on the home page (recent description edits and images, `utilData.do?action=genRecentContent`; it also runs at every start; the old `upload.do?action=genAll` did only the first half and was removed), the statistics, the all-AntWeb ants project list (a repair tool), and the database query timings (written to queryStats.log).
- **Run the nightly run now**: set 1 (AntCat names, then the CAS specimens from TaxonWorks), set 2 (countries and adm1s), set 3 (projects, museums, bioregions), set 4 (image counts), set 5 (maps and checks), or all five. These run by themselves every night from 05:00 Pacific and put a heavy load on the server; run one by hand only when it failed in the night.
- **Debugging**: Server Debug (the server debug flag) and Session Properties (what is stored in your own session).

Repair without a link: **geolocale taxon parentage** (`utilData.do?action=fixGeolocaleTaxonParentage`). Every species recorded in an adm1 should also be recorded in its country, and every taxon in a place should have its parent taxon there. The tool first shows a preview (how many records are missing; nothing is changed), then an **Apply the fix** button that removes the proxy records and inserts the missing ones. Use it after a large change to the geolocale hierarchy; the nightly run does not need it.

Data jobs started from the Curate page or the Technical page (reloads, recounts, restarts, the nightly sets) always show a confirmation page with a **Run it** button first; nothing runs on the first click.

### Removed tools (19 October 2026)

These pages and links were taken out after a review of every unclear tool; their addresses now answer "not found". Where something replaces them, it is named.

- Old team member pages (`teamMember.do` and its photo upload and download): the public **Curators** (`curators.do`) and **Staff** (`staff.do`) pages replace them. The old team member table is archived on the server before it is dropped.
- Invitation links (`antwebInvite.do`): new logins get a one-time link to set their password; pending logins are edited from the Logins list.
- AntWiki list check (`antWikiData.do`), the world authority save (`worldAuthoritySave.do`; the nightly AntCat import replaced it) and the species list file (`getSpeciesList.do`).
- Editor trial page (`editorTrial.do`): TinyMCE 8 was chosen and is on every edit form.
- Credit editor (`editCredit.do`), new bioregion form (`newBioRegion.do`; bioregions are a fixed table), donation and thank-you pages, the old Google site search (`basicSearch.do`), the v3 API page (`apiV3.do`; the API is v3.1, see `apiV3.1.do`) and two lower-case aliases (`getcomparison.do`, `taxonomicpage.do`).
- Reports and jobs with no use left: `list.do` taxon names, specimens with morpho genera, recent CAS pinned Ponerinae, the toUpload folder listing; `query.do?action=countryEndemic` (the public **Endemic** page lists the same taxa); the Apache log in the Log Manager (there is no Apache); `upload.do` reload species lists and toggle down time (the switch is on **Server Status**); `utilData.do` bioregion taxon count crawl (the nightly run does it), update group counts and update group upload stats (every upload does both); the scheduler's `justLog` test.

---

See also: [docs/flows/specimen-upload.md](../flows/specimen-upload.md), [docs/flows/image-upload.md](../flows/image-upload.md) and [docs/flows/nightly.md](../flows/nightly.md) for what happens behind the upload and nightly tools (on the flows branch until it is merged), and [docs/catalog/actions.md](../catalog/actions.md) for every page and action.
