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
- **Title**: the museum's full name (for example "British Museum of Natural History" for BMNH, whose
  name is "BMNH, London"). It is the heading and browser title of the public museum page, the Title
  column of the Museums list (`museums.do`) and the entry in the "change view" menu of other museum pages.
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
places in memory, so the change is live at once.

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
"Locality (Mapping Range Criteria)" field was never stored and was removed; the "Map (Image)" field showed
the page's map object instead of the stored file name and was never saved (it now shows and saves `project.map`); the extent error showed an
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
- There is no button that rebuilds the whole table (the old **Refresh** was removed on 2026-10-21:
  it counted introduced populations as native and overwrote every correction made here).

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

**Where the introduced species list comes from**: it is the species list of the project
introducedants (its `proj_taxon` rows), kept by curators and administrators by hand in the Species
List Tool (`speciesListTool.do`; saved there with source speciesListTool). This manager page does not
add or remove species: it only records the native bioregions (`taxon_prop`, prop introducedMap) of
species already on the list. What the list and the ticks are used for:

- Specimen upload: a specimen of a listed species collected outside its native bioregions gets
  `specimen.is_introduced = 1` (`SpecimenUploadProcess`, `TaxonPropMgr.isIntroduced`); the nightly
  name update sets the flag again when a specimen's name changes (`SpecimenNameUpdate`). The flag
  shows as "Introduced" on the specimen and in the query "introducedSpecimen".
- Introduced numbers on place pages: the nightly counts (`GeolocaleDb.calcIntroduced`,
  `BioregionDb.calcIntroduced`) do not read the specimen flag. They take each country, state and
  bioregion list entry of a listed species and mark it introduced when that place's bioregion is not
  ticked as native here (`geolocale_taxon.is_introduced`, `bioregion_taxon.is_introduced`); the
  Introduced number is the count of those rows (the species `introduced.do` shows).

Nothing adds species to introducedants automatically (checked in the code 2026-10-21): the only writes
to `proj_taxon` are the Species List Tool (`SpeciesListDb.saveTaxonSet`) and the restore of a curator's
entry that was parked during a name change (`CuratorTaxonSetDb`); the specimen upload writes only to
allantwebants, the AntCat import only to worldants, allantwebants and fossilants
(`SpeciesListUpload`, `AntwebUpload.saveTaxonAndProjTaxon`), and the `insert ... select` in
`ProjTaxonDb.regenerateAllAntweb` only to allantwebants. Automatic changes touch only the names
already on the list: after the AntCat import a name that became a synonym is replaced by its current valid name
(`TaxonSetDb.updateTaxonSetTaxonNames`), and the project count crawl sets the source of every
introducedants row to curator (`ProjTaxonCountDb.fixExceptions`). A third: when a specimen upload corrects the subfamily in a name, the
list entries of the old spelling move to the corrected name
(`OrphansDb.moveTaxonSupportingDataToAlternate`). None of these adds a species.

**Content to do (Brian Fisher, 2026-10-21)**: the introduced species list is out of date. Species
introduced since it was last curated are missing, and their specimens are therefore not flagged and not
counted as Introduced. Update the list in the Species List Tool, then tick their native bioregions here.

---

## Query Manager (`query.do`)

**For**: ready-made database queries by topic. Click a name to run it: the page shows **What it shows** (what
the query lists, how to read it and what to do about its rows), the query, the rows and the time taken. They
only read data; some take a minute, and the server stops any query after 90 seconds. Administrators only,
except the few that public or curator pages link to (the AntCat import history, the species lists with range
data, the image data download). The checks that should find nothing also run together in the **Integrity
Check** (`query.do?action=checkIntegrity`); `noSpeciesForSpecies` and `geolocaleTaxaPresent` are also Admin
Alerts. The queries are defined in `src/org/calacademy/antweb/util/Queries.java` (and
`QueriesWithParams.java` for the four with a place).

**Changed 2026-10-21**: result values and column headings were written into the page as HTML; they are
now text, and only the links the queries build to AntWeb pages stay links.

**Page limit 2026-10-22**: a query shows at most 5,000 rows on its page ("Showing the first 5,000 of N rows";
rowCount is still the full count), so no report page runs to megabytes (each view of the old image data
query was about 13 MB and was copied whole into the messages log). The bioregion and country species lists
with range data are shown in full; `imageData` has its own limit of the newest 5,000 (below).

**Reviewed 2026-10-22** (Brian Fisher: "make sure they are working and explained"): every query now has a
plain explanation and one heading per column; a query that fails shows its error on the page instead of an
error page, and a heading/column mismatch is flagged. Fixed: `lastSpecimenUpload` (its "Last Upload" was the
date of the newest specimen record; it now shows the last upload recorded in the upload table and the newest
specimen record, labeled), `notValidTaxaFromSpecimenList` (an SQL error on MySQL 8), `invalidCurrentValid`
and `selfRefSynonyms` (compared the wrong form of the current valid name, so one listed every original
combination and the other nothing), checks that used `not in (select taxon_name from specimen)` (found
nothing once one specimen has no name), `usedAndUnusedCoords` (missing parentheses), the links to taxon
pages (shown as text when the name has a space), the headings of 16 queries (wrong count, wrong order or
broken), and the permalinks of the country species list queries. `imageData` (public link of the recent images search, formerly "Download All", now "Newest 5,000 images (table)") is limited to the
newest 5,000 images. CI test: `test/org/calacademy/antweb/util/QueriesReviewTest.java`; every query is run
on staging by `tests/e2e/test_named_queries.py`.

**Removed 2026-10-22** (duplicates, broken, or comparing with data frozen long ago): `brokenTaxaHierarchy` and
`badSpecies` (use `noSpeciesForSpecies`), `longCurrentValidNames` (part of `invalidCurrentValid`),
`generaInMultipleSubfamilies2` (known to overload the site; use the detail query of
`generaInMultipleSubfamilies`), `sourceWithBadParent` and its two detail queries (use `badParentTaxonName`
and `badParentTaxonNameSpecimens`), `oldBadBioregionsInSpecimen` (use `invalidBioregion`),
`multiBioregionSpecimenTaxa` (use `nonIntroducedAntsInMultipleBioregions`), `validNonfossilSpeciesWithMuseums`
(part of `validNonfossilSpeciesWithMuseumsOwnedByLocatedAt`), `validNonfossilTaxaWithBioregions` (an SQL
error), `extentAndCoords` (used a column dropped in 2017), `notValidTaxaFromSpeciesList` and
`incorrectNameInSpeciesList` (use `notValidProjectTaxonNames`), `geolocaleListMorphotaxa` (use
`geolocaleTaxaMorphosWithNoSpecimen`), `notValidGeolocaleTaxonNames` (use `geolocaleTaxaFromInvalidTaxonName`),
`antwebUniqueValidTaxa` and `antwebUniqueFossilTaxa` (compared with AntWiki lists loaded once in 2016),
`ancillaryFiles` (the "ancillary" pages of before 2011).

**The queries** (name: what it shows; the page has the full explanation):
**Taxon Queries**

- `noSpeciesForSpecies`: Species-rank names whose species part is empty.
- `geolocaleTaxaPresent`: Admin Alert check that the country species lists are filled: shows 0 when there are at least 50,000 rows (one per name per country; there are normally far more), and "records missing!" when there are fewer.
- `nonAsciiTaxonName`: Names that contain a character other than plain English letters, digits and punctuation (an accented letter, a curly quote, a non-breaking space), usually pasted into an upload file.
- `selfRefSynonyms`: Synonyms whose current valid name is the name itself, so the taxon page sends readers back to the same page.
- `homonyms`: Every name in the homonym table: AntCat names spelled exactly like another ant name but with another author and year.
- `invalidCurrentValid`: Names whose current valid name (the name AntCat says to use instead) is not among AntWeb's names or homonyms.
- `noGenusForGenus`: Genus-rank names whose genus part is empty.
- `noSubfamilyForSubfamily`: Subfamily-rank names whose subfamily part is empty.
- `emptyGenus`: Names below the genus (genera, species, subspecies) whose genus part is empty, so they do not show under any genus.
- `generaInMultipleSubfamilies`: Genera whose names in use (valid names and AntWeb's own morphospecies, indeterminate and unrecognized names) are filed under more than one subfamily.
- `generaInMultipleSubfamiliesDetail`: Detail of generaInMultipleSubfamilies: every name in use of the genera filed under more than one subfamily, with the subfamily each is under and where it came from.
- `emptySubfamily`: Names that have a genus but no subfamily, so they are missing from the subfamily's pages and counts.
- `taxonNameWithDoubleQuote`: Names that contain a double quote ("), which breaks links and searches.
- `taxonNameWithSingleQuote`: Names that contain an apostrophe ('), which breaks links and searches.
- `validsWithCurrentValidName`: Valid names that also have a current valid name.
- `missingCurrentValidName`: Synonyms, combinations and unavailable names without a current valid name.
- `spaceInGenusName`: Names whose genus part contains a space (for example "pheidole x" typed in the genus column of an upload).
- `unrecognizedWithSynonymParent`: Names that AntCat does not recognize (they came from specimen data) whose genus or species is a synonym in AntCat: the specimens were identified under an old name.
- `unrecognizedTaxaWithoutSpecimen`: Unrecognized names from specimen uploads that no specimen uses any more (the specimens were re-identified or deleted).
- `speciesEqualsSubspecies`: Pairs of names in the same subfamily and genus where the second name's species part is in parentheses (an old way of writing a subgenus) and its subspecies part is the first name's species.
- `taxonCountBySubfamily`: The 8 subfamilies with the most names in AntWeb (all ranks and statuses: valid names, synonyms, morphospecies...).
- `taxonCountByGenus`: The 8 genera with the most names in AntWeb (all ranks and statuses).
- `taxonCountBySpecies`: The 8 species epithets used by the most names, across all genera (the empty one counts the names above species rank).
- `indeterminedAndQuestionableMorphoTaxa`: Indeterminate names (status indetermined) and morphospecies whose name contains a parenthesis.
- `badParentTaxonName`: Names whose parent (the genus of a species, the species of a subspecies) is not among AntWeb's names, usually a misspelled or outdated genus or species in specimen data.
- `badParentTaxonNameSpecimens`: Detail of badParentTaxonName: the same names with the number of specimens filed under each and the groups that uploaded them, so each group can be asked to correct its identifications.
- `taxaFromOldSpecimenLists`: Names that came from the species list of a project that no longer exists (source ending in "ants" or "speciesList.txt"), with the number of specimens filed under each.

**Homonym Queries**

- `homonymWithoutTaxon`: Homonyms whose name is not in the taxon table.

**Specimen Queries**

- `notValidTaxaFromSpecimenList`: Specimen records filed under a name AntCat does not consider valid (synonym, homonym, unavailable name...), per group, with the name to use instead and the number of specimens.
- `specimenCountByOwner`: Specimen records per "owned by" value (the institution that owns the specimen, as written in the upload file), largest first.
- `checkSpecimenStatus`: Specimens with no status (the status of their name, set at upload).
- `invalidBioregion`: Bioregion names in specimen data that are not one of AntWeb's bioregions (bioregion table), with the number of specimens and the groups that uploaded them.
- `specimenCountByTaxa`: The 8 names with the most specimen records.
- `specimenGroups`: How many specimen records each upload group has on AntWeb: group number, name and count, largest first.
- `introducedSpecimen`: Specimen records flagged as introduced (a species found outside its native bioregions), by group, bioregion and country.
- `nonAnts`: Specimen records that are not ants (family not Formicidae, or empty), with the group that uploaded them.
- `castes`: Every combination of life stage/sex, caste and subcaste in specimen data, with the number of specimens and the groups (by number) that use it, most common first.
- `nullCastes`: Life stages of specimens that have no caste, with the number of specimens and the groups (by number), most common first.
- `nullLifeStage`: Imaged specimens with no life stage/sex, by group.
- `nonIntroducedAntsInMultipleBioregions`: Names whose specimens come from more than one bioregion and that are not on the introduced species list (project introducedants), with the bioregions, countries (first 140 characters) and groups.

**Species List Exports (valid living species)**

- `validNonfossilSpeciesWithBioregions`: Every valid, living (non-fossil) species with the bioregions it is recorded from (bioregion species lists).
- `validNonfossilSpeciesWithMuseumsOwnedByLocatedAt`: Every valid, living (non-fossil) species with the museums, "owned by" and "located at" values of its specimens.

**Login Queries**

- `loginProjectMapping`: Which logins may edit which project species lists, by group and login.
- `curatorInfoAndProjects`: The logins assigned to at least one live project (the species-list curators), with their email and projects, one row per login.
- `curatorEmails`: The emails of the logins assigned to a live project (the species-list curators), in one comma-separated cell to paste into an email.
- `uploadingCurators`: Every login allowed to upload specimens or images: login ID, first and last name, email, name, upload group number, administrator, the two upload rights (1 = yes) and whether the login is inactive (1 = cannot log in until reactivated).

**Group Queries**

- `adminLoginIdIsZero`: Upload groups that have no administrator login.
- `lastSpecimenUpload`: Each group's specimens and two dates: the last specimen upload recorded in the upload table (the Specimen Uploads list) and the creation date of the group's newest specimen record.

**Geolocale (Place) Queries**

- `invalidValidNamelessCountries`: Countries marked not valid (an old or alternative name) that do not say which valid country they stand for.
- `usedAndUnusedCoords`: Valid countries without map coordinates (their map is blank) and countries that are not valid but still have coordinates (unused).
- `nonAntsInSpeciesListsGeolocale`: Names on country, adm1 or region species lists that are not ants (family not Formicidae).
- `badSingleQuotes`: Place names written with a curly apostrophe (‘ or ’) instead of the straight one (').
- `geolocaleNameWithQuestionMarks`: Place names that contain a question mark, usually an accented letter lost when the name was loaded.
- `duplicatedGeolocaleNames`: Place names used by more than one place (for example Georgia, a country and a US state), highest rank first.
- `lowGeolocaleTaxonCount`: Valid countries whose species list has 5 names or fewer (all ranks), with the count.
- `unfinishedBioregions`: Valid countries without a bioregion.
- `nonvalidValidNames`: Places whose valid name (the place they stand for) is not a valid place.
- `nonLiveValidCountries`: Valid countries that are not live, so they have no public page or list.
- `geolocaleCountryTest`: Country names in specimen data that are not a country in AntWeb's place table (valid or not), with the number of specimens and a few of their codes.
- `geolocaleAdm1Test`: Adm1 names (state, province) in specimen data that are not an adm1 of that country in AntWeb's place table, with the number of specimens and a few codes.

**Project Species List Queries**

- `projTaxaWithoutTaxonWithoutHomonym`: Names on project species lists that are neither among AntWeb's names nor homonyms (the AntCat lists worldants and fossilants are left out).
- `projTaxaWithoutTaxonWithHomonym`: Names on project species lists that are homonyms but not among AntWeb's names (the AntCat lists are left out).
- `quadrinomialsInSpeciesLists`: Names with four parts (genus, species, subspecies and one more) on project species lists other than allantwebants and worldants.
- `nonAntsInSpeciesListsProject`: Names on project species lists (other than allantwebants) that are not ants.
- `speciesListHomonyms`: Homonyms on project species lists (other than allantwebants and worldants; the genus Camponotus is left out).
- `morphoProjTaxa`: Names on project species lists (other than allantwebants and worldants) that look like morphospecies (they contain a digit, ?, -, _, a parenthesis or a dot) and were added without a source.
- `projTaxaMorphosWithNoSpecimen`: Morphospecies on project species lists that no specimen is filed under.
- `notValidProjectTaxonNames`: Names on project species lists (other than allantwebants and worldants) that are not valid in AntCat (synonyms, unavailable names...; morphospecies left out), with the name to use instead.
- `projectTaxaCount`: The number of rows of all project species lists together.
- `projectTaxaCountByProject`: The number of names on each project species list.
- `projectTaxaCountByProjectRank`: The names on the worldants and allantwebants lists by rank and source.

**Place Species List Queries**

- `badGeolocaleTaxonParentTaxonName`: Names on a place's species list whose parent (the genus of a species, the subfamily of a genus) is not on the same list.
- `geolocaleTaxaMorphosWithNoSpecimen`: Morphospecies on country, adm1 or region species lists that no specimen is filed under.
- `geolocaleTaxaFromInvalidTaxonName`: Names on place species lists that are neither valid nor morphospecies (synonyms, unavailable, unrecognized and indeterminate names...), with the name to use instead where AntCat gives one.
- `geolocaleTaxaNotUsingCurrentValidName`: Country and region species lists (by geolocale id) that still hold a synonym or other non-valid name instead of its current valid name.
- `geolocaleTaxaWithoutTaxon`: Names on place species lists that are not among AntWeb's names (renamed or deleted), with the newest date one was added and where it came from.
- `geolocaleTaxaWithoutTaxonDetail`: Detail of geolocaleTaxaWithoutTaxon: each place species list row whose name is not among AntWeb's names, with the place.
- `geolocaleTaxaInMultipleBioregions`: Species and subspecies on the species lists of places in three or more bioregions that are not on the introduced species list (project introducedants), most bioregions first.

**Bioregion Species List Queries**

- `notValidBioregionTaxonNames`: Names on bioregion species lists that are neither valid nor morphospecies, with the name to use instead.

**Museum Species List Queries**

- `notValidMuseumTaxonNames`: Names on museum species lists that are neither valid nor morphospecies, with the name to use instead.

**Image Queries**

- `imageCountByOwner`: Images (one per photo: head, profile, dorsal, label...) per "owned by" value of the photographed specimen, largest first.
- `imageData`: The newest 5,000 images with their specimen, shot (h head, p profile, d dorsal, l label...), shot number, upload date, photographer and the group that uploaded them, newest first.
- `imageStatusCounts`: How many names have at least one photographed specimen, by the name's status (valid, morphotaxon, synonym...).

**Assorted Queries**

- `descriptionEditCountByOwner`: Taxon page text sections (one per name and section title) per group of the curator who last edited each, largest first.
- `importedCountryData`: The UN country list loaded once in 2015 (un_country): name, UN region and subregion, and the bioregion given then.
- `worldantsUploads`: The last 60 imports of AntCat names (Worldants uploads), newest first.
- `adminAlerts`: Every Admin Alert so far (warnings AntWeb raises for the administrators: failed imports, low counts...), the ones not yet acknowledged first.
- `emptySourceTaxonSets`: Rows of project, place and bioregion species lists with an empty source (where the name came from).
- `geolocaleTaxonDisputes`: Names a curator said do not occur in a place (disputes, set with the Species List Tool), newest first.
- `projectTaxonDisputes`: Names a curator said do not occur in a project (disputes, set with the Species List Tool), newest first.
- `conflictedDefaultImages`: Taxa whose default images (chosen on the taxon page) come from a specimen now filed under another name, so the page shows another species' photos.

---

## Log Manager (`logMgr.do?action=list`)

**For**: reading the server's log files (the last 2000 lines; add `&lines=N` or `&grep=word` to the
address). Most useful: **compute.log** (the nightly run from 05:00 Pacific), **admin.log** (AntCat imports
and scheduler runs), **taxonworksNightly.log** (the nightly CAS TaxonWorks upload), **profiler.jsp**
(timings of uploads and pages). Logs are emptied when the AntWeb container is rebuilt. Only the listed
files can be read. Curators reach one line of their own group's upload files from specimen pages and
upload reports.

**Changed 2026-10-21**: taxonworksNightly.log and adminAlerts.log are linked in the list.

**Changed 2026-10-22**: a line is shown with at most 2,000 characters ("... [cut, N characters]"), and the
output stops at 5 MB. messages.txt now holds one short line per message page (runbook, "Log sizes and
rotation").

---

## Removed 2026-10-21 (Brian's decision)

These addresses and buttons are gone; an old link answers "not found" or is ignored.

- **Older Query Manager list** (`queries.do`): the same named queries as the Query Manager, grouped by
  an older scheme. Use the Query Manager (`query.do`). The "Execute all" pages of that list
  (`query.do?action=devQueries` and `curiousQueries`) still work but are no longer linked.
- **upload.do createProject**: the old first step of creating a project (a "click Here" page). Projects
  are created in the Project Manager.
- **Edit Geolocale "Fast"**: saved without reloading the places in memory. Every save now reloads them.
- **Bioregion Map Manager "Refresh"**: rebuilt every genus's native bioregions from all specimens,
  including introduced populations, and overwrote hand corrections.
- **The "groups" named query** (Upload groups page, Relevant Queries): every upload group with its last
  upload date. The page lists the groups; `specimenGroups` stays.

**Kept**: the museum **Title**. It was listed as used only in the "change view" menu, but it is also the
heading and browser title of each public museum page and a column of the Museums list, and for some
museums it differs from the name (BMNH: name "BMNH, London", title "British Museum of Natural
History"). Removing it would change those public pages; it needs its own decision (for example: copy
each title into the name first).
