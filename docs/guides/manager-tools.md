# Manager Tools: a guide for admins

The **Manager Tools** on the Curate page (`https://www.antweb.org/curate.do`, admin part) keep AntWeb's
reference lists: museums, places, projects, photographers, the native and introduced bioregion tables,
and the admin alerts, queries and logs. This guide has one section per tool: what it is for, its fields,
what saving does, and the pitfalls. Login Manager, Add an uploader and Upload groups have their own
guide: [Adding a new uploader](adding-an-uploader.md). The rest of the Curate page is in
[the Curate page guide](curate-page.md).

Every page below starts with a short explanation box. All changes are made with buttons (POST forms
with the session's form token); a link or a typed address never changes anything. Unless a section
says otherwise, only administrators can open the tool.

Reviewed end to end on 2026-10-21 (code, forms, access, escaping); the fixes of that review are noted
under "Changed 2026-10-21".

---

## Museum Manager (`manageMuseum.do`)

**For**: the museum codes that specimen data refer to (the "owned by" column of an upload, for
example CASC). Each museum has a public page (`museum.do?code=CASC`) with its counts.

**The list** shows code, name and Active. Click a code to edit the museum, **Add a museum** for a new one.

**Fields** (`viewMuseum.do`):

- **Code**: as in the specimen data. Fixed once the museum exists (it is the key specimen records
  carry); to change a code, add a new museum and delete the old one.
- **Name**: shown on the museum page and in lists.
- **Title**: the name in the museum page's "change view" menu.
- **Active**: the nightly run (set 5) makes a specimen map only for active museums.

**Save** writes the four values to the `museum` table and reloads the museum list in memory. Specimen
data are not changed. The counts on the museum page are recounted by the nightly run (set 3) or by
the page's **Recalculate** button.

**Delete** (on an existing museum) asks on a second page, which says how many specimen records carry
the code. Deleting removes only the `museum` row: those specimens keep the code but it has no page or
counts any more. A museum in use is better kept (untick Active). There is no undo.

**Changed 2026-10-21**: a name or title with an apostrophe ("Musee d'histoire naturelle") failed to
save (the values were written into the SQL text; now bound values); a blank code is refused; the code
of an existing museum can no longer be overwritten by accident (it used to create a second museum);
the delete page counts the specimens; the list and form show names as text.

**Museum page text (fixed 2026-10-21)**: the page-text editor read and wrote a museum's text under the
museum's **name**, but the museum page loads it by **code**. For a museum whose name is not its code
(code ABS, name "ABS, Lake Placid") saved text did not show after a reload. The editor now uses the
code (`DescEditDb.objectKey`). Text saved earlier under a name stays there until it is moved, on
production, by an admin, with the database command pattern (back up first; nothing here runs by itself):

```sql
-- 1. Check (read only): museum page text stored under the museum's name instead of its code.
--    to_code_has_text = 1: the code already has text for that field (that row is not moved).
select o.object_key as stored_under_name, m.code, o.title, o.created, length(o.content) as chars,
       (select count(*) from object_edit c where c.object_key = m.code and c.title = o.title) as to_code_has_text
from object_edit o join museum m on o.object_key = m.name
where m.name <> m.code order by m.code, o.title;
select count(*) as history_rows_under_name from object_hist h join museum m on h.object_key = m.name where m.name <> m.code;

-- 2. Backup (the rows that will move, with their new key).
create table object_edit_bak_20261021 as
  select o.*, m.code as new_key from object_edit o join museum m on o.object_key = m.name
  where m.name <> m.code
    and not exists (select 1 from object_edit c where c.object_key = m.code and c.title = o.title);
create table object_hist_bak_20261021 as
  select h.*, m.code as new_key from object_hist h join museum m on h.object_key = m.name where m.name <> m.code;

-- 3. Move (dates kept).
update object_edit o join object_edit_bak_20261021 b on b.object_key = o.object_key and b.title = o.title
  set o.object_key = b.new_key, o.created = o.created;
update object_hist h join object_hist_bak_20261021 b on b.hist_id = h.hist_id
  set h.object_key = b.new_key, h.created = h.created;

-- Rollback (if needed):
-- update object_edit o join object_edit_bak_20261021 b on o.object_key = b.new_key and o.title = b.title
--   set o.object_key = b.object_key, o.created = o.created;
-- update object_hist h join object_hist_bak_20261021 b on b.hist_id = h.hist_id
--   set h.object_key = b.object_key, h.created = h.created;
```

A row whose code already has text for the same field (to_code_has_text = 1) is not moved: compare the
two texts on the museum page and the check output and decide by hand.

---

## Geolocale Manager (`geolocaleMgr.do`)

**For**: the places AntWeb uses: regions, subregions, countries and adm1s (states, provinces). Curators
can look; only administrators can save.

**The list**: choose a rank and click the magnifier. A number in the second column opens that place's
children (the countries of a subregion, the adm1s of a country). Click a name to open Edit Geolocale.
Columns:

- **Valid**: an accepted name, with its own page and species list.
- **Live**: listed in the site's region menus.
- **UN**: on the UN list (fixed, for information).
- **Source**: where the place came from (UN list, geolocaleMgr, adm1Mgr...).
- **Valid Name**: for an invalid name (only in a list opened from its parent), the valid place that
  specimen data with this name are moved to at upload. "Other" keeps a valid name that is not in the menu.
- **C** and **BB**: a map centre and a bounding box are set.

**The green arrow** saves that row's Valid, Live and Valid Name (`geolocale` table) and reloads the
places in memory, so the change is live at once. Counts follow in the nightly run. Region rows have no
arrow (regions are fixed).

**Create** (bottom, administrators): only in a list opened from a parent (the adm1 list of a country,
the country list of a subregion), so the new place gets its parent. A new place is not valid and not
live; open it afterwards to set its bioregion, map centre and bounding box.

**Links on the page**: the georegion table (region, subregion, country) and the bioregion table (read
only); Country Documentation; Dispute Manager; the geolocale queries of the Query Manager; Bioregion
Country List; Country Adm1 List; Taxa outside their native bioregions.

### Edit Geolocale (`editGeolocale.do?id=N`)

All settings of one place. **Submit** (administrators) saves every field at once and reloads the
places in memory, unless **Fast** is ticked (then reload later with "Reload places").

- **Name**, **ISO Code**, **ISO 3 Code** (countries), **Geo Rank**, **Valid**, **Valid Name** (for an
  invalid place), **Parent** (region, subregion or country above it), **Live**.
- **Bioregion**: the bioregion given to specimens collected here, at upload. **Alternate Bioregion**: set
  only for a place that lies in two bioregions; then the place gives its specimens no bioregion.
  **None** means no value.
- **Centroid** and **Bounding box**: the map centre and area; a field left as shown is not saved, an
  emptied field clears the fixed value (checked: numbers in range, south below north).
- **Admin Notes**: internal, never shown on the site. **Use Parent Region**, **Is Island** and **Country
  (of an island)**, **Use Children** (use the adm1s, for large countries).
- The page text (Contents, Author, Author Bio, page images) is edited on the place's own page; the
  links at the top go there.

**Delete** (administrators) asks first. It is refused while places lie under it (a country with
adm1s); otherwise it deletes the place and its species list rows (`geolocale_taxon`) and reloads the
places. Specimens are not changed (they name places by text).

**Changed 2026-10-21**:

- The menus' **None** was saved as the text "none" (parent, bioregion, alternate bioregion). An
  alternate bioregion of "none" made AntWeb think the country lies in two bioregions, so its specimens
  got no bioregion at upload. None is now saved as no value, and a stored "none" reads as no value.
  The rows that still hold the text can be listed (and, after a backup, cleared) with:
  `select id, name, parent, bioregion, alt_bioregion, country from geolocale where parent in ('none','null') or bioregion in ('none','null','') or alt_bioregion in ('none','null','') or country in ('none','null','');`
- An island's Country field showed the text "null" when empty and saved it.
- The row arrow did not make the change live (the page said to reload by hand; that link reloaded every
  list in memory).
- Create from a list without parent created a place with the parent "null"; it is now refused with a hint.
- Delete left the place's species list rows behind and did not reload the places; deleting a place with
  places under it is refused.
- Request values (the list's parent and rank) and names are shown as text; the column headings line up
  for regions.

**Known issue (being fixed separately)**: renaming a place leaves its page text and history
(`object_edit`, `object_hist`, keyed by the name) under the old name.

### Adm1 Manager (`adm1Mgr.do?adm1Name=..&countryName=..`)

Linked from the adm1 rows and from the upload reports. Checks one adm1 name as written in specimen data
against the adm1s of the country: valid, invalid with a valid name (used at upload), or unmatched. Lists
the country's adm1s and up to 20 specimens with that adm1. Administrators get **Create** for an
unmatched name and **Edit** for an invalid one. The page itself changes nothing. (2026-10-21: the adm1
name from the address is shown as text.)

---

## Project Manager (`projectMgr.do`)

**For**: projects, AntWeb's named species lists with their own page (regional faunas such as
malagasyants; introducedants; fossilants).

- **Create**: type a key (one lowercase word ending with "ants") and click Create. This opens the settings
  form for the new project; the project is created when you save that form.
- **Delete**: choose a project, confirm. Deletes the project row and its species list (`project`,
  `proj_taxon`); specimens are not changed. No undo.
- **Live / Not Live**: each project with its public page and its **Settings**.

### Project settings (`editProject.do?projectName=..`)

**Display Key** (optional other name in the page address), **Title**, **Map (Image)** (file name in the
project's species list folder, at most 40 characters), **Is Live** (listed in the region menus), **Source**
(shown, kept), **Map centre** (latitude, longitude), **Geographic Extent** (four numbers separated by spaces,
no commas; for the field guide maps), **Species List Mapping** (true: the list can be changed in the
Species List Tool). **Save** stores them at once and reloads the lists in memory. The page text is edited
on the project page; the species in the Species List Tool.

**Changed 2026-10-21**: both pages check for an administrator in the action (the settings save
accepted assigned curators although the page was admin-only); saving blanked the project's Source; the
"Locality (Mapping Range Criteria)" field was never stored and was removed; the extent error showed an
internal key; Create no longer goes through the upload page and a "click Here" step.

---

## Photographers (`artists.do`)

**For**: the photographers credited on images (the image upload asks for one). The list is public;
photographers without images are listed only for curators.

- **Create** (any curator): adds a photographer; you become its curator.
- On a photographer's page (`artist.do?id=N`), the photographer's curator or an administrator can
  **rename** (Save changes; every image credited to them shows the new name) and **delete**. To merge
  two entries for one person, delete one and enter the other's ID: its images move first, then it is
  deleted. Without an ID, delete is refused while images are credited.

**Changed 2026-10-21**: a rename to a name another photographer already has is refused (with the merge
hint); a rename now confirms what changed; images credited to no photographer no longer break the
counts; the "moved to" link shows the name as text.

---

## Admin Alert Manager (`adminAlert.do`)

**For**: problems AntWeb records for administrators: a failed AntCat import (Import AntCat names now),
no AntCat changes for a week. (Low disk space at start-up is only written to adminAlerts.log.) Administrators' pages show the number of
open alerts in the header.

- The **red X** marks one alert as dealt with (all of them with the bigger X); it stays in the full list.
- **Reload** re-reads the alerts and reruns the alert checks (the "Checks" line: ready-made queries that
  should find nothing).
- Links: the full list of alerts (a named query), the AntCat import history, the adminAlerts.log file.

**Changed 2026-10-21**: since May 2026 no new alert was stored (the code only logged "skipping"), so
the page could only show old alerts. Writing is restored, with the text stored and shown as plain text;
the alert of a failed AntCat import is now written after the rollback, so it is kept.

---

## Bioregion Map Manager (`bioregionMapMgr.do`)

**For**: the bioregions where each genus is native (`taxon_prop`, prop bioregionMap). A specimen upload
flags specimens of a genus collected outside its native bioregions ("not native to ... Region" in the
upload report; species on the introduced list are not flagged), and the report "Taxa outside their
native bioregions" uses the same table. Curators can look.

- Tick the bioregions and click the green arrow to save that genus (administrators). It applies to
  the next upload.
- **Refresh** (administrators, bottom) rebuilds the whole table from the bioregions of all specimens and
  replaces every change made here. Specimens of introduced populations count as native there, so check
  the result.

**Changed 2026-10-21**: the Totals row was out of column order (Oceania, Nearctic and Neotropical
shifted); saving a genus is for administrators (any curator could); the saved map reaches uploads at
once (before: only after the next reload of the taxa); one query instead of one per genus.

---

## Introduced Map Manager (`introducedMapMgr.do`)

**For**: the species on the introduced species list (project introducedants). For each, tick the
bioregions where it is **native**: a specimen uploaded from any other bioregion is marked introduced,
and these species are not flagged by the genus check above. A red dot means nothing is ticked: the
species counts as introduced everywhere.

- The green arrow saves that species' row (administrators); it applies to the next upload.
- Opening the page adds an empty row for a listed species that has none (so that the species is
  treated as introduced at upload). This is the only manager page that writes on opening.

**Changed 2026-10-21**: the Curate page and the guide said "where a species is introduced"; the ticks
are where it is native. Values in the form are escaped and the rows read with bound queries.

---

## Query Manager (`query.do`)

**For**: ready-made database queries by topic (taxa, homonyms, specimens, fossils, logins, groups,
geolocales, projects, bioregions, museums, images). Click a name to run it: the page shows the query, the
rows and the time taken. They only read data; some take a minute. Administrators only, except the few
that public pages link to (the AntCat import history, the bioregion species lists with range data, the
image data download). "Older list" shows the same queries grouped as before (integrity, dev, curious).

**Changed 2026-10-21**: result values were written into the page as HTML; they are now text, and only
the links the queries build to AntWeb pages stay links.

---

## Log Manager (`logMgr.do?action=list`)

**For**: reading the server's log files (the last 2000 lines; add `&lines=N` or `&grep=word` to the
address). Most useful: **compute.log** (the nightly run from 05:00 Pacific), **admin.log** (AntCat imports
and scheduler runs), **taxonworksNightly.log** (the nightly CAS TaxonWorks upload), **profiler.jsp**
(timings of uploads and pages). Logs are emptied when the AntWeb container is rebuilt. Only the listed
files can be read. Curators reach one line of their own group's upload files from specimen pages and
upload reports.

**Changed 2026-10-21**: taxonworksNightly.log and adminAlerts.log are linked in the list.

---

## Removal candidates (for Brian's decision; nothing removed)

- **Older Query Manager list** (`queries.do`): the same named queries as the Query Manager, grouped by
  an older scheme; one list would do.
- **Museum Title**: only used in the museum page's "change view" menu, where the name would do.
- **upload.do createProject**: the old first step of creating a project (a "click Here" page); no form
  uses it since 2026-10-21.
- **Edit Geolocale "Fast"**: saves without reloading the places, for bulk edits; rarely useful now that
  saves are one place at a time.
- **Bioregion Map Manager "Refresh"**: rebuilds every genus's native bioregions from all specimens,
  including introduced populations, and overwrites hand corrections.
