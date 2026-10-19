# The Curate page: a guide for curators and admins

The Curate page (**Curator Tools**, `https://www.antweb.org/curate.do`) is where you start when you upload data, check your uploads, or (for admins) manage the site. You reach it from the **Curator Tools** link after you log in.

This guide follows the page from top to bottom. Each tool says what it is for and how to use it. It covers the tools we intend to keep; a few tools on the page today are under review and are not described here. Where a label on the page is unclear, the guide gives the plain meaning next to it.

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
- **View Your Specimen Upload Reports**: the list of your group's specimen uploads, newest first. Each line opens the upload report (how many specimens were loaded, which names were changed to the current valid name, which lines had problems). Check it after every upload.
- **View Your Image Upload Reports**: the same for image uploads: one line per upload batch, with its report.
- **View the files in your curator folder**: the files you uploaded for use on taxon pages (for example an image you inserted into a taxon's page text). Only you and admins see this list.

At the top of this area a red or blue line may appear when the server is busy (an upload or the nightly run is in progress, or uploads are paused). If you see one, wait and try again later.

### Specimen Data (curators with the "upload specimens" permission)

**Upload Zip File**: this is how you send your specimen data to AntWeb.

1. Put your specimen file (the tab-delimited AntWeb specimen file) in a ZIP file. Only ZIP files are accepted.
2. Click **Choose File**, pick the ZIP, then click **Submit**.
3. Wait. A large file can take several minutes. If the page shows a Cloudflare timeout, your data is still being processed: after 85 seconds the page sends you to your upload reports, and you also get an email when the upload is finished.
4. Open the upload report and check it.

Your upload replaces your group's specimen records on AntWeb. Names are matched to the current valid names in AntCat. Counts on country, region and museum pages are brought up to date in the nightly run (from 5 AM Pacific), so new specimens can take until the next morning to show in those counts.

The optional column **previousDeterminations** lets you send earlier identifications of a specimen, newest first, separated by " | " (an example is shown on the page).

**View Archived Specimen List Files**: your group's earlier uploaded specimen files. Choose your group in the list and click **Submit** to see the files; each one can be downloaded. Useful if you need a copy of what you sent before.

### Upload Specimen Images (curators with the "upload images" permission)

**Specimen Image Upload** opens the Image Uploader. Image files must be named by specimen code and view (the naming rules are on the uploader page and in the "Submitting images" guide). You can add many files at once; each is checked, and you get a report and an email at the end.

### Read-Only Tools

These tools never change anything on AntWeb.

- **Validate Species List (Pre-flight checker)**: upload a species list file (tab-delimited text) to check every name against AntWeb's names from AntCat. You get a report on screen and can download it, or download a corrected copy of your list with the current valid names.
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
- **Upload as**: upload the file on behalf of another curator, so the data goes to that curator's group. The menu lists you and the curators your login may upload for.

### AntCat names

**Fetch and Reload Worldants** (the **Submit** button next to "Reload Worldants"): downloads the AntCat names now and loads them into AntWeb. This normally happens every night (05:00 Pacific). Use it only when the nightly AntCat import failed (you would see an admin alert or a red line in the nightly log). It takes several minutes. Hover over "Source" to see the address of the AntCat export it downloads.

### Edit Species List

Shown when your login looks after project or region species lists. Choose a list and click **Submit** to open the **Species List Tool** for it: compare it with another list and add or remove species.

### Manager Tools

- **Login Manager**: all logins. Open one to change its details and permissions (upload specimens, upload images, admin, the projects and regions it may edit, "upload as"). The form at the top creates a new login.
- **Group Manager**: the contributing groups (institutions). Edit a group, create one, or delete one (you are asked to confirm).
- **Museum Manager**: the museum codes used in specimen data (code, name, title, active). Edit or add a museum. **Delete** (on a museum's form) asks you to confirm on a second page; there is no undo.
- **Geolocale Manager**: countries and states/provinces (adm1): whether they are valid and shown on the site, names, bioregion and map coordinates. Curators can look; only admins can save changes.
- **Project Manager**: the projects (for example regional ant faunas): settings and page text. Create or delete projects here.
- **Artist Manager**: the list of photographers credited on images. Add or rename a photographer here.
- **Admin Alert Manager**: problems AntWeb has flagged for admins (for example a failed AntCat import). Remove an alert once it is dealt with.
- **Bioregion Map Manager**: which bioregions a species is native to (used for the "introduced" flags and maps).
- **Introduced Map Manager**: in which bioregions a species is introduced.
- **Query Manager**: a list of ready-made database checks, by topic. Click a name to run it and see the rows. The checks only read data. Only admins can run them, except the few that public pages link to (for example the AntCat import history).
- **Log Manager**: the server logs. The most useful are compute.log (the nightly run), admin.log (AntCat imports and scheduler runs) and taxonWorksNightly.log (the nightly CAS TaxonWorks upload).

### Administrator Functions

- **Antweb Statistics**: statistics tables by project, museum, bioregion and upload.
- **Server Status**: what the server is doing now: database connections, running jobs, and the switch that pauses uploads ("down time").
- **Site Warning**: the message banner shown at the top of every page (for example before maintenance). The link opens a confirmation page that saves the current text again; to change the text you edit the `text=` part of the address. An empty text removes the banner. Simple formatting (bold, colour, line breaks, links) is kept; anything else, such as scripts, is removed.
- **Restart server now**: asks for a restart of the AntWeb application. It happens within 5 minutes (never during an upload), and the site shows an error page for 2 to 4 minutes while it starts again. You are asked to confirm first. The link appears only when the server has been up for at least 10 minutes.

### Reports

- **Curate AntCat Queries**: checks on how AntWeb's names line up with AntCat: names not recognized by AntCat that have no specimens, unrecognized names under a synonym parent, region lists not using the current valid name, bad parent names, and the history of AntCat imports.
- **Homonym Report**: the homonyms AntWeb holds.
- **Events**: a log of curator edits to the images, videos and overview text of taxon pages, with the curator's name and date.
- **Unimaged Bolton Ants**: species in the AntCat list that have no image on AntWeb yet. Useful for planning imaging.
- **Recent Description Edits**: the latest edits to taxon page text, by whom.
- **Casent -Dxx Anomalies**: specimens whose code has been split into parts (for example `casent0001234-d01`) where the parts carry different taxon names; usually a data entry problem to fix in the source data.
- **Taxa Outside of Native Bioregion**: taxa with specimens recorded outside their native bioregions; a list to check for misidentifications or introduced records. It runs through a confirmation page but only reads data.

### Technical Functions

- **Reload All Managers**, and the one-letter links after "Manager:": AntWeb keeps lists such as logins, groups, projects, regions and taxa in memory. After a change made directly in the database, reload them here (or all at once). The letters stand for: g group, a all-AntWeb, l login, p project, b bioregion, m museum, g geolocale, t taxon, tp taxon properties, u upload, a artist, aa admin alert.

Data jobs started from this page (reloads, recounts, restarts) always show a confirmation page with a **Run it** button first; nothing runs on the first click.

---

See also: [docs/flows/specimen-upload.md](../flows/specimen-upload.md), [docs/flows/image-upload.md](../flows/image-upload.md) and [docs/flows/nightly.md](../flows/nightly.md) for what happens behind the upload and nightly tools (on the flows branch until it is merged), and [docs/catalog/actions.md](../catalog/actions.md) for every page and action.
