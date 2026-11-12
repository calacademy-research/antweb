# Every number on AntWeb and the test that checks it

The tables these numbers are stored in and computed from, and the code that writes them: [data-model.md](data-model.md).

Brian Fisher, 2026-10-20: every number shown on the site must be validated by a test, so new code cannot undo
past progress. This page lists every kind of number AntWeb shows (JSP pages and the v3.1 API), where it is
shown, how it is computed, and which test validates it. CI checks this page (`tools/numbers/check_numbers_doc.py`,
step "Every number on the site names its test" in `.github/workflows/tests.yaml`): every row says `yes`,
`partly` or `not yet`, every `yes` or `partly` row names tests that exist, and every check of the count check
is named by a row. A new number on the site gets a row here, with its test.

A "kind" is one label on one page type, for example "Specimens on a place page"; it appears on every country,
adm1, island, region, subregion, project, museum and bioregion page. Pure identifiers, dates, coordinates,
elevations and years are left out. The first inventory (2026-10-20, read-only review of the code) found 118
kinds; 9 were validated, 7 partly, 102 not, and 24 numbers looked wrong in the code (section 3).

## 1. How the numbers are checked

| Test | What it does | Where it runs |
|---|---|---|
| `inv:<id>` | CountInvariants (`src/org/calacademy/antweb/util/CountInvariants.java`): for **every** place of every kind, the stored number against the number it must equal or not exceed, with grouped queries (one per check for all places). Since 2026-10-22 also NumberChecks (`NumberChecks.java`, ids `stats.*`, `admin.*`, `api.taxa`, `list.images`, `place.endemic.flags`, `taxon.parents`, `geo.adm1.country`): the statsPage tables, the admin pages, the API's taxon counts, the Images column of every list, the endemic flags, the suspect B25 and the adm1 Specimens recount (B26, fixed 2026-11-09); the value a page shows (computed by the page's own code, or read from LoginMgr and ArtistMgr as the pages read it) against an independent recount. | `countCheck.do` (admins, read-only); on staging in the end-to-end suite (`tests/e2e/test_count_check.py`) on every pull request and every day; on production read-only every day. Database behaviour: `CountInvariantsTest`, `NumberChecksTest` (CI, MySQL 8.4). |
| `cc:<check>` | CountCheck (`CountCheck.java`): recount from the specimen and image records for a sample of places: Madagascar, Reunion, the country with the most morphospecies, the subregion of Madagascar, the largest live project, CASC, Malagasy, plus a rotating slice so every country, subregion, region, project and museum is recounted within a week and every live adm1 within a month (`CountCheckAction.rotatingSample`). | same page and tests; `CountCheckTest` |
| `junit:<Class>#<method>` | JUnit test against MySQL 8.4 in CI (`test/`). | CI "Tests" |
| `e2e:<file>::<function>` | end-to-end test of the page on staging (`tests/e2e/`). Since 2026-10-22 the count check's report carries its recounts as JSON (`id="count-check-expected"`), and `test_count_check.py` compares statsPage, curators.do, curator.do, artists.do, groups.do, group.do, listImageUploads.do and orphanedImages.do with them; `test_numbers_live.py` compares live numbers (list headers, browse, images, comparisons, search, collection, locality, endemic.do) with the Madagascar place page, whose numbers the check validates; `test_api_numbers.py` the API's counts with what it returns. | e2e workflow |
| `unit:<file>::<function>` | unit test (`tests/unit/`). | CI "Tests" |
| rotating sample (e2e) | since 2026-10-26, `test_numbers_endemic.py`, `test_numbers_lists.py`, `test_numbers_species.py` check numbers that are shown on every place, list row or species page on a sample chosen from the day (`tests/e2e/numbers_sample.py`): every kind of place the number is shown on (country, adm1, region, subregion, bioregion, museum, project) on every run, a different place of each kind (and genus, species or name within it) each day, so that over the days every one is checked; the places are found on the site (API `/geolocales`, `/bioregions`, museums.do, projects.do). The numbers are compared with numbers the count check validates on every place (list rows, place totals) or recounted independently from the API's records (endemism by name, unimaged names, specimens with images, habitat summaries). A failure names the day; `ANTWEB_SAMPLE_DAY` repeats it. | e2e workflow |

A difference the check finds is **real** (the site shows a wrong number) or **pending** (something changed after
the last nightly run: an upload, new images, a list edit, a name update by hand; or the number's rule changed on
2026-10-21 and no nightly run has counted it that way yet). The end-to-end test fails on real differences. Since
2026-11-03 (audit of 2026-11-02: one upload made every difference of every place pending) an upload, a rename or new
images make a difference pending only where they could have changed it: a name uploaded or renamed, or a place they
touched (`util.ChangedPlaces`: countries, islands and adm1s by the specimens' country and adm1, with the subregions and
regions above them; museums by `specimen.museum` or `ownedBy`; bioregions by `specimen.bioregion`; projects by the
names on their lists). Numbers of the whole site (home page, statistics, the Global column) are still pending after
any upload. A re-upload's removed specimens leave no record: if they were the group's only specimens in a place, that
place's difference shows as real until the nightly run. Tests: `junit:CountCheckTest#testAnUploadElsewhereLeavesTheDifferenceReal`,
`junit:CountInvariantsTest#testAnUploadElsewhereLeavesAPlaceDifferenceReal`.

Run time: the whole check runs as a background job (CountCheckJob, BackgroundJobs; staging took 107 seconds,
over Cloudflare's 100-second limit for a request). `countCheck.do` shows the latest report or starts the job
(`countCheck.do?run=1`) and sends the browser to `jobStatus.do?id=N`; the report is `countCheck.do?job=N`. The
nightly run starts the same job when it has finished, so the check runs every day on staging and production,
read-only. Order: the invariants over every place, the core places, then the rotating slice until the whole check
has run 300 seconds (parameter `seconds`, 0 to 600); places of the slice not reached are listed. With the
invariants run, the rotating places skip the genus, subfamily and Global recounts (checked for every place).
The report prints the time of each part. The end-to-end test fails when the job takes over 10 minutes or does not
finish. Measured on a copy of production's size (1,000,000 specimens, 1,100,000 images, 63,000 taxa, 4,300
geolocales, 380,000 geolocale list rows; MySQL 8.0, 2 cores): every place 29 to 35 s, core places 7 s, 175
rotating places 14 s. A check of given places (`geolocaleId`, `project`, `museum`, `bioregion`) still runs in
the request.

The checks added on 2026-10-22 (NumberChecks), measured on a synthetic copy of production's size (1,000,000
specimens, 1,100,000 images, 62,000 taxa, 4,300 geolocales, 370,000 geolocale list rows, 208,000 project rows,
170,000 museum rows, 150,000 bioregion rows, 5,000 uploads; MySQL 8.0 on the build machine, where the checks that
existed took 59 s): 49 s together (stats.images 7, stats.imaged 8, list.images 13, admin.statistics 12 including
the one pass over the specimens of every upload group, place.endemic.flags 4, the others under 1.2 s each; first
versions of admin.groups and api.taxa took 33 s and 124 s and were rewritten). On staging (2 cores, 4 GB) expect
about 60 to 90 s more for "every place". The whole job still stops the rotating recount at 300 s, so it stays
within about 5 minutes; on staging the rotating sample may then reach fewer places a day (those not reached are
listed). The report prints the time of every check (column ms), and "every place" is split into the place
numbers and the other pages' numbers (`data-other`).

Status: **yes** = the number is compared with an independent recount or with the number it must equal, on every
place (or a rotating sample covering every place); **partly** = only some places or parts (for example the
Madagascar pages of the end-to-end tests), or only a consistency rule (a part at most the whole, a header equal to
the rows listed); **not yet** = no test (see the plans in section 4).

### Definitions (Brian Fisher, 2026-10-27)

"We need to carry our definitions to every place: consistency will keep us sane." Wherever AntWeb shows a number of
images:

- **Images** = images of the ant (head, profile, dorsal and other views). Every Images number counts these.
- **Label images** are counted separately: next to the number of images ("5 Images + label", "5 Images + 2 labels",
  "Label only" when a specimen has label images only, "No Images" when it has none), or in their own row or column
  (the place pages' statistics table, the statsPage Images table).
- **Image files** = every uploaded file, label images included (upload lists and reports, photographers, the place
  lists' and the place table's file totals, the admin taxon number). The word "Images" is never used for these.

The code: `org.calacademy.antweb.ImageCounts` (the definitions and the "N Images + label" text),
`StatisticsDb.ANT_IMAGE` (the rule: shot type not l), the line under every list or table with an Images number
(`web/common/imagesDefinition.jsp`), the API's `antImageCount` and `labelImageCount` next to `imageCount` (kept as
every image file). `tests/unit/test_image_counts_defined.py` fails on a page that prints an image count not defined
this way.

### Names not identified to genus (rule of 2026-10-22)

Brian Fisher, 2026-10-22: names not identified to genus (the genus part is indet, (indet), or a subfamily in
parentheses, or the whole name is unidentified) do NOT count as species anywhere (species totals, endemism, the species
counts of lists). Their specimens still count in the specimen totals (under their subfamily where it is known). No
placeholder genus rows are ever created for them.

One definition, `PlaceTotals.identifiedToGenus(alias)` (SQL) and `PlaceTotals.isIdentifiedToGenus(genus)` (Java): the
taxon's `genus` column is not `indet` or `undet` and does not start with "(" (AntWeb's placeholders `(indet)`,
`((indet))`, `(formicinae)`, `(formicidae)`). Examples on staging: `(indet)((indet)) (indet)` (226 specimens),
`(indet)(formicinae) (indet)`, `formicinaeindet (indet)`, `indetindet (indet)`. Morphogenera
(`amblyoponinaeamblyoponine_genus8 nz01`) are identified to genus and count. Used by: place Species and the Species
per Subfamily pie (`PlaceTotals.rankCondition(SPECIES)`), the Species of genus rows of every list (the nightly crawl,
`getCountChildrenQuery` of Geolocale, Project, Museum, Bioregion), endemism (`PlaceTotals.endemicEligible`: `GeolocaleDb.COUNTRY_ENDEMIC_SQL`,
`ADM1_ENDEMIC_SQL`, `HIGHER_ENDEMIC_SQL`, `BioregionDb.ENDEMIC_SQL`), home and statsPage "species + ssp. imaged"
(`StatisticsDb.IMAGED_SPECIES_SQL`), the species list of taxonomicPage.do and its header (`TaxaPage`), the nightly
parent repair (`TaxonParentDb`) and uploads (`AntwebUpload.saveTaxon`: no genus row made for them); and by the checks
(`inv:place.totals.species`, `inv:place.pies`, `inv:list.genus`, `inv:place.endemic.flags`, `inv:taxon.parents`, the
count check's genus rows `cc:species`). Specimens, Images and the Specimens per Subfamily pie still count them. Not
changed: list rows of subfamilies hold the specimens of their genus rows only, so a name not identified to genus with
no genus row as parent is in its place's Specimens (and the pie, under its subfamily) but in no subfamily row's
Specimens. Test: `TaxonTreeCountDbTest#testANameNotIdentifiedToGenusIsNotASpecies`, `#testANameNotIdentifiedToGenusIsNotEndemic`,
`#testIdentifiedToGenusSqlAndJavaAgree`.

One form for identified only to genus (Brian Fisher, 2026-10-28): `<Genus> (indet)`, the species epithet in parentheses.
A species written `indet`, `Indet`, `indet.` or `undet` is filed that way by uploads (specimen and species list), so
`Pheidole indet` and `Pheidole (indet)` are no longer two taxa counted twice in a list. Older names in the old form
are renamed by `tools/data/indet_form.py` (merged into the `(indet)` taxon where it exists; docs/runbook.md, "Indet
form").

## 2. Inventory

Coverage after 2026-11-12: **130 validated, 0 partly, 0 not yet** of 130 (2026-11-12: row 130, the counts of a data extract; 2026-11-11: row 129, the place pages; 2026-11-08: rows 124 to 128, the imaging progress page; 2026-11-06: rows 122 and 123, the home page numbers of release 9.3.0 and their statsPage details; 2026-10-29: row 121, the indet line of genus pages; 2026-10-27: row 120, imaged castes, added with Brian Fisher's decision on images from anywhere, section 3; rows 19, 28, 44, 45, 49, 55 to 61, 64 to 71, 83, 94 to 96, 98, 101, 102, 105 to 107, 110 to 113, 116 to 118 by end-to-end tests, findings B45 to B64; 2026-10-26: 81, 38, 0, rows 24, 25, 38, 39, 46, 47, 50 to 53, 78,
79 by end-to-end tests on a rotating sample, section 1; 2026-10-22: 69, 50, 0 of 119, row 119, Port of Entry, added with
Brian Fisher's answers of 2026-10-22, section 5.2; earlier on 2026-10-22: 67, 51, 0 of 118; 2026-10-21: 42, 22, 54; first
inventory 2026-10-20: 9, 7, 102).

| # | Page | Number | How it is computed | Status | Test |
|---|---|---|---|---|---|
| 1 | home page `/` (genInc/statistics.jsp, box "AntWeb in numbers") | N valid living ant species (+ N fossil species) | since 2026-11-06 (Brian Fisher, release 9.3.0; `StatisticsDb.homeBoxHtml`): statistics row columns living_species and fossil_species (`StatisticsDb.LIVING_SPECIES_SQL`, `FOSSIL_SPECIES_SQL`): valid ant species, species rank only (subspecies on statsPage, row 123), identified to genus, fossil 0 or 1 as statsPage Global Data (so equal to its Extant and Fossil Valid Species); written by the nightly (set 4) and after every specimen upload. Until 2026-11-06: N valid species + ssp. (valid species and subspecies, fossils included; still on statsPage, row 6) | yes | inv:home; junit:PlaceTotalsDbTest#testHomeNumbersOf20261106; e2e:test_home_numbers.py::test_home_box_numbers_and_labels; e2e:test_home_numbers.py::test_home_box_numbers_nest_and_shares_are_right; e2e:test_home_numbers.py::test_statspage_has_the_home_numbers_and_the_details |
| 2 | home page `/` (genInc/statistics.jsp) | N specimen records | statistics row: all specimen records | yes | inv:home |
| 3 | home page `/` (genInc/statistics.jsp) | N species imaged (P% of valid living species) | since 2026-11-06: statistics column living_species_imaged (`StatisticsDb.LIVING_SPECIES_IMAGED_SQL`): valid living ant species (row 1) a specimen with a photo of the ant is filed under (labels never count); the share = of row 1, rounded (`StatisticsDb.percent`); imaged <= with specimens (row 122) <= row 1, checked by inv:home. Until 2026-11-06: N species + ssp. imaged (valid names and morphospecies, species and subspecies; still on statsPage, row 8) (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:home; junit:PlaceTotalsDbTest#testHomeNumbersOf20261106; e2e:test_home_numbers.py::test_home_box_numbers_and_labels; e2e:test_home_numbers.py::test_home_box_numbers_nest_and_shares_are_right; junit:NumberDecisionsDbTest#testPhotosOfRedFlaggedSpecimensAreNotCounted; junit:CountInvariantsTest#testImagesOfARedFlaggedSpecimenAreNotCounted |
| 4 | home page `/` (genInc/statistics.jsp) | images of ants of N specimens (the specimens imaged) | statistics row: specimens with an image of the ant (labels not counted, as Imaged specimens on place pages); since 2026-11-06 in the label of the images number (row 5); until then its own line "N specimens imaged" (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:home; junit:PlaceTotalsDbTest#testHomeNumbersCountImagesOfTheAnt; junit:PlaceTotalsDbTest#testHomeNumbersOf20261106; e2e:test_home_numbers.py::test_home_box_numbers_and_labels; e2e:test_home_numbers.py::test_home_box_numbers_nest_and_shares_are_right; junit:NumberDecisionsDbTest#testPhotosOfRedFlaggedSpecimensAreNotCounted; junit:CountInvariantsTest#testImagesOfARedFlaggedSpecimenAreNotCounted |
| 5 | home page `/` (genInc/statistics.jsp) | N images of ants (+ N label images; until 2026-11-06 "N total specimen images + N labels") | statistics row: all image rows; since 2026-10-27 images of the ant (StatisticsDb.TOTAL_IMAGES_SQL), label images shown apart ("+ N labels", LABEL_IMAGES_SQL); until then every image row; a row written before the next nightly is pending (CountInvariants.ANT_IMAGES_PENDING) (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:home; unit:test_image_counts_defined.py::test_specimen_counts_show_labels_apart; junit:NumberDecisionsDbTest#testPhotosOfRedFlaggedSpecimensAreNotCounted; junit:CountInvariantsTest#testImagesOfARedFlaggedSpecimenAreNotCounted |
| 6 | statsPage.do | N valid species + ssp. | TaxonMgr.getValidSpeciesCount(): valid species and subspecies in memory, ants identified to genus (`PlaceTotals.isValidAntSpecies`, Q7 2026-10-22; was: valid taxa of every rank, then of every family) | yes | inv:home; junit:PlaceTotalsDbTest#testStatsPageCountsValidSpeciesAndSubspecies; junit:NumberDecisionsDbTest#testStatsPageAndHomeCountAntsIdentifiedToGenusOnly; e2e:test_numbers_pages.py::test_statspage_valid_species_count_species_and_subspecies |
| 7 | statsPage.do | N specimen records | AntwebMgr, from the latest statistics row (= #2); the text under it, "from AntWeb's N collaborating institutions", counts the museums of the Museums list (`MuseumMgr.getMuseumCount`, as museums.do; B73, decided by Brian Fisher 2026-10-27; was the fixed text "21") | yes | inv:home; junit:CountRules20261027Test#testInstitutionsAreTheMuseumsListed; e2e:test_home_and_stats.py::test_statistics_page_institutions_are_the_museums_listed |
| 8 | statsPage.do | N species + ssp. imaged | AntwebMgr (= #3) (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:home; inv:stats.imaged; junit:NumberDecisionsDbTest#testPhotosOfRedFlaggedSpecimensAreNotCounted |
| 9 | statsPage.do | N specimens imaged | AntwebMgr (= #4) (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:home; junit:NumberDecisionsDbTest#testPhotosOfRedFlaggedSpecimensAreNotCounted |
| 10 | statsPage.do | N total specimen images (+ N labels since 2026-10-27) | AntwebMgr (= #5); = #5; the labels counted live when the server starts and by the nightly (AntwebMgr.getLabelImagesCount) (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:home; junit:NumberDecisionsDbTest#testPhotosOfRedFlaggedSpecimensAreNotCounted |
| 11 | statsPage.do | Global Data, Extant: valid subfamilies, genera, species, subspecies | live count of the taxon table, ants only, species identified to genus (StatisticsDb.globalCountSql, `PlaceTotals.validAntTaxonCondition`; Q7 2026-10-22: was every family); said under the heading | yes | inv:stats.global; junit:NumberDecisionsDbTest#testStatsPageAndHomeCountAntsIdentifiedToGenusOnly; e2e:test_count_check.py::test_statspage_tables_equal_the_recount; e2e:test_numbers_pages.py::test_statspage_valid_species_count_species_and_subspecies; e2e:test_numbers_pages.py::test_statspage_says_it_counts_ants_only |
| 12 | statsPage.do | Global Data, Fossil (4) | live count, ants only (as #11) | yes | inv:stats.global; e2e:test_count_check.py::test_statspage_tables_equal_the_recount; e2e:test_numbers_pages.py::test_statspage_valid_species_count_species_and_subspecies |
| 13 | statsPage.do | Images table: Total per specimen status | live (ImageDb.getImageStats); since 2026-10-27 images of the ant; label images in their own column (Label images) (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:stats.images; e2e:test_count_check.py::test_statspage_tables_equal_the_recount; junit:PlaceTotalsDbTest#testStatsPageImageTableAddsUp; junit:PlaceTotalsDbTest#testStatsPageImageTableAddsUp; junit:NumberDecisionsDbTest#testPhotosOfRedFlaggedSpecimensAreNotCounted |
| 14 | statsPage.do | Images table: Worker / Male / Queen / Other | live; Other includes images of specimens without a caste (2026-10-21; they were in no column); the caste of a specimen is read from its Life Stage field (`Caste.getCasteValues`; B69, 2026-10-27: "alate male" is a male, "and" and "/" split without a comma; existing specimens after their next upload or a calcCaste run, the counts after the next nightly run); since 2026-10-27 images of the ant (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:stats.images; e2e:test_count_check.py::test_statspage_tables_equal_the_recount; junit:PlaceTotalsDbTest#testStatsPageImageTableAddsUp; junit:CountRules20261027Test#testAlateMaleIsAMale; junit:CountRules20261027Test#testAndAndSlashSplitWithoutAComma; junit:NumberDecisionsDbTest#testPhotosOfRedFlaggedSpecimensAreNotCounted |
| 15 | statsPage.do | Images table: Sum row | summed in the JSP over every status in the table (was six fixed statuses); images of specimens without a status are in no row; since 2026-10-27 with the Label images column; Sum + labels + images of specimens without a status = all images minus orphan images (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:stats.images; e2e:test_count_check.py::test_statspage_tables_equal_the_recount; junit:NumberChecksTest#testStatsPageImagesSumLeavesOutSpecimensWithoutStatus; junit:NumberDecisionsDbTest#testPhotosOfRedFlaggedSpecimensAreNotCounted |
| 16 | statsPage.do | Imaged Species per taxon status | live (ImageDb.getImageTaxonStats): names of any rank with a specimen that has any image (labels too), by taxon status; only seven statuses shown; since 2026-10-27 names with a specimen that has an image of the ant (label images did count) (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:stats.imaged; e2e:test_count_check.py::test_statspage_tables_equal_the_recount; junit:NumberDecisionsDbTest#testPhotosOfRedFlaggedSpecimensAreNotCounted |
| 17 | statsPage.do | Bioregion Data (4 per bioregion) | live over bioregion_taxon, ants only (StatisticsDb.listCountSql; Q7 2026-10-22: was every family, B31) | yes | inv:stats.bioregions; inv:stats.nonants; junit:NumberChecksTest#testStatsPagePlaceQueriesMatchThePageCode; junit:NumberChecksTest#testStatsPageBioregionAndMuseumData; e2e:test_count_check.py::test_statspage_tables_equal_the_recount |
| 18 | statsPage.do | Museum Data, Valid Extant (4 per museum) | live over museum_taxon, ants only (as #17) | yes | inv:stats.museums; inv:stats.nonants; junit:NumberChecksTest#testStatsPagePlaceQueriesMatchThePageCode; junit:NumberDecisionsDbTest#testStatsPageAndHomeCountAntsIdentifiedToGenusOnly; e2e:test_count_check.py::test_statspage_tables_equal_the_recount |
| 19 | statsPage.do | Museum Data, Valid Fossil (4 per museum) | live over museum_taxon, ants only; since 2026-11-06 subfamilies and genera are those with fossil species on the list, extinct and living, as the Fossil lists (`PlaceTotals.fossilViewCondition`; Global Data Fossil the same over the taxon table); since 2026-10-27 compared end-to-end with the headers of the museum's lists with statusSet valid fossil (TaxaPage, another code path) on a daily sample of museums (two with fossils, one without) | yes | inv:stats.museums; inv:stats.nonants; junit:NumberChecksTest#testStatsPagePlaceQueriesMatchThePageCode; e2e:test_count_check.py::test_statspage_tables_equal_the_recount; e2e:test_numbers_search.py::test_statspage_valid_fossil_equals_the_museum_lists |
| 20 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Subfamilies | subfamily rows on the place's list that are valid subfamilies (`PlaceTotals.countsAsSubfamily`, `PlaceTotals.listedCountSql`); museums and bioregions were roll-up sums until 2026-10-21; from 2026-10-27 to 2026-10-30 a curated project counted its subfamily rows of every status (B67, superseded); 2026-10-28 (counting rules, section 5): a subfamily counts when a specimen is identified at least to it (its row is on the list); **2026-10-30** (genera and subfamilies rule, section 5): valid subfamilies only, on every kind of place, curated projects too: the placeholder "(formicidae)" (status morphotaxon) and misspelled subfamily names (status unrecognized) stay on the list but are not counted (was: counted statuses, every status on curated projects) | yes | inv:place.totals.subfamily; cc:place totals; junit:GeneraRuleDbTest#testSubfamiliesCountValidSubfamiliesOnly; junit:CountRules20261030Test#testSubfamiliesAreValidSubfamilies; junit:CountRules20261030Test#testPlaceGeneraAndSubfamiliesTakeTheRule; junit:PlaceTotalsDbTest#testFinishWritesEveryTotalFromTheList; junit:CountRules20261027Test#testB67SupersededCuratedProjectSubfamiliesTakeThePlaceRule; junit:CountRules20261028Test#testGeneraAndSubfamiliesCountTheRowsOfTheirRule |
| 21 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Genera | genus rows on the list, counted status (PlaceTotals); was sum(genus_count) of the subfamily rows; 2026-10-28 (counting rules, section 5): a genus counts when a specimen is identified at least to genus, so a genus known only from "Lioponera indet" counts (its row is on the list; the rule of the rows is unchanged); **2026-10-29** country and adm1 lists (and through them subregions and regions) get the genus row of every specimen identified to genus, of any counted status (`GeolocaleTaxonDb.GENUS_ROWS_FROM_SPECIMENS_SQL`, set 2; until then they took valid names only from specimens, so Lividopone, known in Mozambique only from "Lividopone (indet)", was not on its list); bioregion and museum lists already did; the names themselves are not added, so Species and Specimens do not change; Genera (and Subfamilies where a subfamily is new) of countries, adm1s, subregions and regions rise at the next nightly run; **2026-10-30** (genera rule, section 5): valid genera and morphogenera only (`PlaceTotals.countsAsGenus`): placeholders identified only to subfamily or family ("(myrmicinae)", "(formicidae)") and genus names AntCat does not know (status unrecognized, "(formicidae)polyrachis") stay on the list but are not counted | yes | junit:GeneraRuleDbTest#testGeneraCountValidGeneraAndMorphogeneraOnly; junit:CountRules20261030Test#testPlaceGeneraAndSubfamiliesTakeTheRule; junit:PlaceTaxonNotesDbTest#testGenusRowsComeFromSpecimensIdentifiedToGenus; e2e:test_genus_indet_places.py::test_genus_rows_of_specimens_identified_only_to_genus; inv:place.totals.genus; junit:PlaceTotalsDbTest#testFinishWritesEveryTotalFromTheList; junit:CountRules20261028Test#testGeneraAndSubfamiliesCountTheRowsOfTheirRule; junit:PlaceTotalsDbTest#testSpecimensCountAtEachRankTheyAreIdentifiedTo |
| 22 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Species / subspecies / morphospecies (label since 2026-10-29; was Species / subspecies) | species and subspecies rows on the list of status valid or morphotaxon (`PlaceTotals.SPECIES_STATUSES`, counting rules of 2026-10-28, section 5; until then the five counted statuses: indetermined, unidentifiable and unrecognized names counted), identified to genus, ants (PlaceTotals); was sum(species_count) of the genus rows; the definition on the page says so; the numbers follow the rule after the next nightly run (pending until compute.log has the line `CountInvariants.COUNTING_RULES_LOG`) | yes | inv:place.totals.species; junit:PlaceTotalsDbTest#testFinishWritesEveryTotalFromTheList; junit:CountRules20261028Test#testSpeciesAreValidNamesAndMorphospeciesOnly; junit:PlaceTotalsDbTest#testSpecimensCountAtEachRankTheyAreIdentifiedTo; unit:test_place_species_label.py::main |
| 23 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Valid species / subspecies | valid extant species and subspecies rows on the list (PlaceTotals.validSpeciesSql); geolocales: written every night since 2026-10-21 (GeolocaleDb.finish was never called); on Fossil Ants and the AntCat list since 2026-11-11 the species rows of their view Valid (status valid, fossil or not; a list total, `PlaceViewTotalDb.VALID_SPECIES`; Fossil Ants 856 beside 868 with its morphospecies; the rule of the place page's list numbers, section 5): `valid_species_count` is not shown there | yes | inv:place.totals.valid; junit:PlaceTotalsDbTest#testFinishWritesEveryTotalFromTheList |
| 24 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Endemic | rows flagged is_endemic on the list (PlaceTotals.countsFromFlagsSql); the flags by calcEndemic (geolocales: set 2 and after uploads; bioregions: set 3); the count loop was off by one at each place change; end-to-end since 2026-10-26: the page number = endemic.do on every bioregion and a daily sample of countries and adm1s, and the names of endemic.do recounted by the rule from the API (bioregion flags are recounted only there); since 2026-10-27 only flagged names with a counted status, no synonyms, as the lists (`PlaceTotals.flaggedCondition`; B70, decided by Brian Fisher 2026-10-27); **since 2026-10-28 valid names only** (Brian Fisher 2026-10-22 and 2026-10-28, Q3: morphospecies, indetermined, unidentifiable and unrecognized names never count toward endemism, at any place; `PlaceTotals.ENDEMIC_STATUSES`, used by every endemism rule, `flaggedCondition` and the check), counted on native rows only (Q4: computed after the introduced flags, rows flagged introduced neither make a name non-endemic nor are flagged endemic; `PlaceTotals.nativeRow`), and subregions and regions have the names whose native country rows are all in their countries (Q3, `GeolocaleDb.HIGHER_ENDEMIC_SQL`; until then only the names endemic to one of their countries); Endemic <= Valid species; the lists change at once, the stored numbers and flags at the next nightly run (geolocales set 2, bioregions set 3) | yes | inv:place.totals.endemic; junit:PlaceTotalsDbTest#testEndemicNumbersAreTheFlaggedRows; junit:PlaceTotalsDbTest#testEndemismCountsValidNativeNamesOnly; junit:PlaceTotalsDbTest#testBioregionEndemismCountsValidNativeNamesOnly; junit:CountRules20261028Test#testEndemicCountsValidNamesOnly; junit:CountRules20261028Test#testEveryEndemismRuleCountsValidNativeRows; junit:CountRules20261028Test#testRegionsAndSubregionsHaveTheNamesFoundOnlyInThem; inv:place.endemic.flags; e2e:test_numbers_live.py::test_endemic_and_introduced_lists_equal_place_numbers; e2e:test_numbers_endemic.py::test_endemic_and_introduced_numbers_equal_their_lists; e2e:test_numbers_endemic.py::test_endemic_names_follow_the_endemism_rule; e2e:test_numbers_endemic.py::test_endemic_and_introduced_marks_on_the_species_list_agree; e2e:test_numbers_endemic.py::test_endemic_names_of_countries_are_named_species; junit:CountRules20261027Test#testEndemicAndIntroducedListsAndNumbersShareTheStatusRule |
| 25 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Introduced | rows flagged is_introduced on the list (PlaceTotals.countsFromFlagsSql); end-to-end since 2026-10-26: the page number = introduced.do on every bioregion and a daily sample of countries and adm1s, and introduced.do = the names the species list marks Introduced (computed live from the native bioregions) that are on the introduced-species list; B41 (a place without a bioregion) xfail; since 2026-10-27 only flagged names with a counted status, no synonyms, as the lists (`PlaceTotals.flaggedCondition`; B70, decided by Brian Fisher 2026-10-27; after the next nightly run) ; regions and subregions (Brian Fisher, 2026-10-28): counted the way Endemic is for them, a name flagged introduced on a country's list is flagged on its subregion's list, and a subregion's on its region's (`GeolocaleDb.calcHigherIntroduced`; until then 0); their pages show no Endemic or Introduced row (the numbers are in the API and introduced.do) | yes | inv:place.totals.introduced; e2e:test_numbers_live.py::test_endemic_and_introduced_lists_equal_place_numbers; e2e:test_numbers_endemic.py::test_endemic_and_introduced_numbers_equal_their_lists; e2e:test_numbers_endemic.py::test_endemic_and_introduced_marks_on_the_species_list_agree; e2e:test_numbers_endemic.py::test_a_place_without_a_bioregion_has_no_introduced_species; junit:CountRules20261027Test#testEndemicAndIntroducedListsAndNumbersShareTheStatusRule; junit:CountRules20261028Test#testRegionsAndSubregionsCountIntroducedAsTheyCountEndemic |
| 26 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Specimens | every kind of place: the sum of the species and subspecies rows' specimens (an adm1's: specimens of its country and adm1 name since 2026-11-09, B26 fixed; before: of its adm1 name in any country or none) (projects: list recounted first since 2026-10-21); museums and bioregions since 2026-10-28 (Brian Fisher: like countries; `PlaceTotals.writeListedSpecimens`): their specimens of a counted status filed under a name of their list or a synonym of one; until then all their ant specimens (specimen.museum, specimen.bioregion), any status, any name; the count check recounts them (`CountCheck.checkListPlaceSpecimens`) | yes | inv:place.totals.specimens; inv:geo.regions; cc:place specimens; junit:CountInvariantsTest#testRecountOfASampledPlaceChecksItsImagesColumn; inv:geo.adm1.country; junit:CountRules20261028Test#testMuseumAndBioregionSpecimensAreTheSumOfTheirListRows; junit:PlaceTotalsDbTest#testSpecimensCountAtEachRankTheyAreIdentifiedTo |
| 27 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Imaged specimens | PlaceImageCountDb (set 4): specimens with an image that is not a label (an adm1's specimens by its country and name since 2026-11-09, B26; rows 28 to 32 the same); museums, bioregions and projects since 2026-10-28 (Brian Fisher: images follow the Specimens rule): the images of the specimens of their list rows, a synonym's under the valid name (`PlaceImageCountDb.listSource`; museums and bioregions: counted status, as their Specimens; before: every ant specimen of the museum or bioregion) (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | cc:place images; inv:place.images; inv:place.images.order; inv:geo.adm1.country; junit:PlaceImageCountDbTest#testImagesOfASynonymCountUnderTheValidName |
| 28 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | (N additional specimens) label only | PlaceImageCountDb: specimens whose only images are labels; = specimens with any image minus Imaged specimens on every place (inv:place.images), Imaged specimens recounted by cc:place images; since 2026-10-27 the specimens with any image (and the Images) recounted from the API on a different museum every day (one with at most 3,000 specimens with images); since 2026-10-30 the API's specimens of a counted status only (the museum's rule since 2026-10-28; the test compared every status, staging 10-30: USNM 1,275 against 1,279) (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:place.images; e2e:test_numbers_images.py::test_label_only_specimens_are_the_specimens_with_only_label_images; junit:PlaceImageCountDbTest#testRedFlaggedSpecimensImagesAreNotCounted |
| 29 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Image files (labelled Images until 2026-10-27) | PlaceImageCountDb: all image files (labels included); museums, bioregions and projects since 2026-10-28 (Brian Fisher: images follow the Specimens rule): the images of the specimens of their list rows, a synonym's under the valid name (`PlaceImageCountDb.listSource`; museums and bioregions: counted status, as their Specimens; before: every ant specimen of the museum or bioregion) (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | cc:place images; inv:place.images; inv:place.images.order; inv:geo.adm1.country; junit:PlaceImageCountDbTest#testImagesOfASynonymCountUnderTheValidName; junit:PlaceImageCountDbTest#testRedFlaggedSpecimensImagesAreNotCounted |
| 30 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Images (labelled Ant images until 2026-10-27) | PlaceImageCountDb: images that are not labels; museums, bioregions and projects since 2026-10-28 (Brian Fisher: images follow the Specimens rule): the images of the specimens of their list rows, a synonym's under the valid name (`PlaceImageCountDb.listSource`; museums and bioregions: counted status, as their Specimens; before: every ant specimen of the museum or bioregion) (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | cc:place images; inv:place.images; junit:PlaceImageCountDbTest#testImagesOfASynonymCountUnderTheValidName; junit:PlaceImageCountDbTest#testRedFlaggedSpecimensImagesAreNotCounted |
| 31 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Views: N head, profile, dorsal, ventral, field, other | PlaceImageCountDb: images per shot type (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | cc:place images; inv:place.images; junit:PlaceImageCountDbTest#testRedFlaggedSpecimensImagesAreNotCounted |
| 32 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Label images | PlaceImageCountDb: shot type l (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | cc:place images; inv:place.images; junit:PlaceImageCountDbTest#testRedFlaggedSpecimensImagesAreNotCounted |
| 33 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Imaged specimens / Image files before set 4 ran once | place table image_count, imaged_specimen_count (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | cc:place images; inv:place.images; junit:PlaceImageCountDbTest#testRedFlaggedSpecimensImagesAreNotCounted |
| 34 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Species per Subfamily pie | PlaceTotals.speciesPieSql: the rows of Species by subfamily (valid names and morphospecies since 2026-10-28) (geolocales counted rank species only; projects and bioregions every rank) | yes | inv:place.pies; junit:PlaceTotalsDbTest#testPiesOfMuseumsAndBioregionsCountTheirOwnSpecimens |
| 35 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Specimens per Subfamily pie | PlaceTotals.specimenPieSql: the species rows' specimens (museums and bioregions since 2026-10-28, as their Specimens; before: their own specimens, which were every specimen in AntWeb of their names until 2026-10-21); one Other slice for names that are not a subfamily; fitted to its column, a chart never stops an upload | yes | inv:place.pies; junit:PlaceTotalsDbTest#testPiesOfMuseumsAndBioregionsCountTheirOwnSpecimens |
| 36 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Taxa menu N Species | = #22 (getSpeciesCount()) | yes | inv:place.totals.species |
| 37 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Admin: species count link | = #22 | yes | inv:place.totals.species |
| 38 | taxonomicPage.do, browse.do | List header, its number the rows of the chosen view (since 2026-11-02; `StatusSet.listHeading`): "2,672 Valid species (of 6,960 species and morphospecies)", "2,700 Species (valid and curated)", "116 Genera (valid and curated)", "N Species (all specimen names)", "N Fossil species" | live: rows shown in the view of the address (statusSet), else Curated, the default on every kind of list (2026-11-02, `StatusSet.defaultChoice`, see "Views of lists" in section 5; the AntCat list and Fossil Ants: valid with fossils; 2026-10-29: a view chosen on one page is never kept in the session for the next). Valid = Valid species / subspecies (row 23), its "of" number = Species / subspecies / morphospecies (row 22); the views nest (Valid <= Curated <= All specimen names); no view lists exactly the rows of row 22 (it counts every morphospecies of the list, curated or not; until 2026-11-02 the choice Valid species + morphospecies did); genus and subfamily lists of All specimen names: their rows that are genera and subfamilies = Genera / Subfamilies; a species list of Valid or Curated says under the list how many specimens identified only to genus it leaves out, by genus, with a link to the place's specimens (`GenusOnly`, since 2026-10-28); checked on a different place of every kind every day (country, adm1, region, subregion, bioregion, museum, project) against the place page, and against the rows listed; the place page's Subfamilies, Genera and Species are stored totals of the headers in the default view (`PlaceViewTotalDb`, 2026-11-04; section 5 "Views of lists"), counted by the daily update and, since 2026-11-10, again for one place by its "Update statistics" button and the admin jobs for one place (`recountPlace`) | yes | e2e:test_numbers_live.py::test_list_headers_equal_place_numbers; e2e:test_numbers_lists.py::test_list_headers_equal_place_numbers_of_every_kind; e2e:test_numbers_lists.py::test_species_lists_show_valid_names_and_morphospecies_only; e2e:test_status_choice.py::test_each_choice_header_equals_the_place_page_number; e2e:test_status_choice.py::test_lists_open_with_the_default_of_their_kind_of_place; junit:CountRules20261028Test#testSpeciesListsLeaveOutNamesThatAreNotSpecies; junit:CountRules20261028Test#testGenusOnlySpecimensAreNamedOnGenusAndSpeciesLists; e2e:test_numbers_lists.py::test_default_list_forgets_the_last_choice; junit:Decisions1029Test#testListsOpenWithTheStatusesOfTheSpeciesNumber; junit:StatusChoiceTest#testHeadersSayWhatTheListShows; junit:StatusChoiceTest#testDefaultOfEachKindOfPlace; junit:PlaceTaxonNotesDbTest#testViewsOfMadagascar; junit:PlaceTaxonNotesDbTest#testVettedMorphospeciesFollowsItsSpecimens; e2e:test_list_sources.py::test_views_header_and_about_this_list; junit:PlaceViewTotalDbTest#testOnePlaceIsCountedAgainAndNoOtherPlaceIsTouched; e2e:test_place_recalc.py::test_admin_updates_a_museum_a_project_a_bioregion_and_an_island; e2e:test_place_recalc.py::test_admin_updates_a_country_and_an_adm1 |
| 39 | taxonomicPage.do, browse.do | N Unimaged X (out of M) | live: the children with no photo of the ant anywhere in AntWeb (`StatisticsDb.antImagedNames`, the API's rule; Q12 2026-10-22: was no image from this place, labels included); 0 is shown too (B21); said under the header; since 2026-10-26 the names listed = the API /unimagedGeolocaleTaxa names of the list (the rule written twice), Madagascar and a country a day; the tests use the view Valid, whose M = Valid species (2026-11-02) (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | junit:NumberDecisionsDbTest#testUnimagedMeansNoPhotoOfTheAntAnywhere; e2e:test_numbers_live.py::test_unimaged_header_is_within_the_list; e2e:test_numbers_pages.py::test_unimaged_list_says_anywhere_in_antweb; e2e:test_numbers_lists.py::test_unimaged_names_equal_the_api; junit:NumberDecisionsDbTest#testPhotosOfRedFlaggedSpecimensAreNotCounted |
| 40 | taxonomicPage.do, browse.do | Species row N Specimens (place column) | list row specimen_count (nightly crawl); B25 (a subspecies under its species replaces the species' row) checked by taxon.parents | yes | cc:specimens; inv:taxon.parents |
| 41 | taxonomicPage.do, browse.do | Genus row Species, Not curated and Images (since 2026-11-02; until then "N Species"; Other specimens from the morning to the evening of 2026-11-02) | in a place with the views (`ListView.GenusRows`, a few queries per page, none per row): Species = the species and subspecies of the genus in the chosen view (the view's species list grouped by genus), a number linked to the genus page in the same view, or "&ndash;" when none; Not curated (Brian Fisher, 2026-11-02, 19:44; views Valid and Curated, the same number in both; countries, adm1s, museums, bioregions; not regions, subregions, projects) = the specimens of the genus collected in the place (not red-flagged, ants) whose name is not curated: of a counted status and neither a valid name nor a curated morphospecies (`ListView.notCuratedCondition`), so uncurated morphospecies, names identified only to genus and unrecognized names; counted once a day by nightly set 4 and `utilData.do?action=placeGenusCuration` (`PlaceGenusCurationDb`, tables `place_genus_curation` and `place_specimen_name`), so after an upload it catches up the next morning; hover "N specimens not curated"; linked to the genus page in the place in the view Not curated (`statusSet=notcurated`), whose header gives the breakdown ("3,392 specimens not curated: 298 morphospecies (2,820 specimens), 572 identified only to genus") and whose rows are those names with their specimens collected here; empty when 0 or not counted. It replaced Other specimens (the specimens under names not in the view, read live from the specimen table on every page: 1.4 to 2.2 s more per genus list on staging; its link searched every specimen of the genus in the place, more than it counted). Images = "N species imaged" (row 44). In Valid and Curated a genus known here only from specimens identified to genus is not a row (`ListView.keepGenus`); the genus lists read the names collected in the place from `place_specimen_name` (the specimen table only until the first count); the AntCat list and Fossil Ants keep the stored species_count (its species and subspecies rows of status valid or morphotaxon, 2026-10-28) (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:list.genus; cc:species; inv:taxon.parents; junit:CountRules20261028Test#testGenusRowsCountTheSameSpeciesOnEveryKindOfList; junit:PlaceTaxonNotesTest#testNotCuratedCell; junit:PlaceTaxonNotesDbTest#testGenusRowsOfEachView; junit:PlaceTaxonNotesDbTest#testNamesReadOnceGiveTheSameRows; junit:PlaceGenusCurationDbTest#testFillCountsTheSpecimensNotCurated; junit:PlaceGenusCurationDbTest#testOtherKindsOfPlaces; junit:PlaceGenusCurationDbTest#testPageReadsTheStoredNumbers; junit:PlaceGenusCurationDbTest#testNotCuratedViewListsThoseNames; junit:ListViewTest#testWhichGenusRowsEachViewShows; junit:ListViewTest#testNotCuratedColumnInValidAndCuratedOnly; junit:StatusChoiceTest#testNotCuratedView; e2e:test_list_sources.py::test_mozambique_acropyga_row_links_its_names_not_curated; e2e:test_list_sources.py::test_genus_rows_have_the_columns_of_the_views |
| 42 | taxonomicPage.do, browse.do | Subfamily row N Genera | in a place with the views since 2026-11-03 (B77; `ListView.SubfamilyRows`, a few queries per page, none per row): the genus rows the subfamily's browse page shows in the chosen view, linked to it in that view; the AntCat list and Fossil Ants, and the stored number the count check and the API read: list row genus_count: its genus rows that are genera (valid genera and morphogenera, no placeholders; genera rule of 2026-10-30, `PlaceTotals.childCountCondition`); browse.do of a subfamily lists, and its header counts, the genus rows of its view (since 2026-11-02 as the genus list: `ListView.GenusRows`; All specimen names: every genus row; 2026-10-30: Valid genera + morphogenera, the genera of this rule) | yes | inv:list.subfamily; cc:genera; junit:GeneraRuleDbTest#testTheCrawlCountsTheGeneraOfASubfamilyRowByTheRule; junit:PlaceTaxonNotesDbTest#testSubfamilyRowsCountTheGenusRowsOfTheirBrowsePage; e2e:test_numbers_lists.py::test_browse_headers_equal_the_list_rows |
| 43 | taxonomicPage.do, browse.do | Global N Specimens | allantwebants proj_taxon specimen_count: all specimens of the name; rebuilt after uploads and, since 2026-10-21, after the nightly name update | yes | inv:global.specimens; inv:list.global; cc:global; junit:PlaceTotalsDbTest#testGlobalIsRecountedAfterTheNameUpdateMovedSpecimens |
| 44 | taxonomicPage.do, browse.do | Images column: N Images, the images of the name in AntWeb ("N Images", the place's number only, until 2026-10-27; "T Images (P from X)" from 2026-10-27 to 10-28) | Genus rows in a place since 2026-11-02 (Brian Fisher, 18:40): "N species imaged" = of the species and subspecies of the genus in the chosen view (the names of its Species, row 41), how many have at least one image of the ant anywhere in AntWeb (labels never count; `StatisticsDb.antImagedNames`, one query per 500 names per page; `ImagesAnywhere.speciesImagedCell`), linked to the genus's images page in the same view (no place-first order there; place-first is for species pages); it replaced "N Images" on genus rows (every image of the genus in AntWeb, e.g. Camponotus 20,663), junit:ListViewTest#testGenusRowsSpeciesImaged, junit:PlaceTaxonNotesDbTest#testGenusRowsOfEachView, e2e:test_list_sources.py::test_genus_rows_have_the_columns_of_the_views. Species and subfamily rows: One number (decided by Brian Fisher 2026-10-28: "153 Images (0 from Comoros)" wrapped to two lines, and the number script blanked the 0, "( from Comoros)"): the images of the ant of the name in AntWeb, from any specimen; labels never count (decided by Brian Fisher 2026-10-27; until then every image file, labels included); the definition under the list says so and, on a place's list, that the name's images page (the link) shows the place's specimens first (that page says how many imaged specimens are from the place, taxonChildImages.jsp). N = the allantwebants list row's image_count, every rank (set 4, `getTaxonImageCountQuery`: the images of the ant, `StatisticsDb.ANT_IMAGES_OF_SPECIMEN`; a genus is the sum of its names by the taxon tree; since 2026-10-28, decision 3, the images of specimens filed under a synonym or other name with a current valid name count under the valid name, as on museum, bioregion and curated project lists (`PlaceTotals.allAntwebImagesSql`; each specimen once: under its own name when that has an allantwebants row, else under its valid name; set 4 and every upload's rebuild, `ProjTaxonDb.regenerateAllAntweb`; the count check recounts the rows by the same rule, `CountCheck.checkRowImages`). Until then such specimens were not counted at all, since allantwebants has rows of the counted statuses only, so a curated project's row could exceed the AntWeb-wide one: staging run 40795139086, floridakeysants Camponotus inaequalis 32 against 29), read with the place's row (`TaxonSet.globalImageCountColumn`, one key lookup per row), so it changes after the nightly run; set 4 counts the allantwebants rows with the live projects since 2026-10-27 (`ProjTaxonCountDb.imageCrawlProjects`; they were set to 0 with the project rows and never counted again; pending in the end-to-end test until compute.log has the line "AntWeb-wide list Images counted"); PR #291's second staging run counted species rows live from the image table instead, one count per row, and list, taxon and browse pages came back cut off on staging, so that was taken out. The place's own list row image_count (set 4, the same rule; project rows reset first since 2026-10-21) is no longer shown in the cell; it is used only when the AntWeb-wide number is not known or lower (`ImagesAnywhere.imagesColumn`: the larger of the two), it is still checked by the count check (cc:images) and the API (rows 112, 116). Sorting by Images follows the number shown (`ImagesAnywhere.shownImages`, `SortTaxaByImages`). Not taxon.image_count (row 54, every image file, labels included); "No Images" only when N = 0; N recounted from the API's records of every specimen of the name and, since 2026-10-28, of the specimens of its synonyms (/taxa?currentValidName, added to the API for this; names of a status allantwebants has no row for) (/taxaImages without label shots; when the API cannot list the synonyms, an older API ignoring the filter or more than one page, the test only checks N >= the recount of the name) on species rows of the day's genus of the day's place of every kind (test_numbers_images.py) and of Comoros and the day's place of every kind (test_images_anywhere.py); the numbers follow the ant-only rule after the first nightly run with it (until then the count check calls their differences pending: compute.log has no "list Images counted as ant images" line) (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | cc:images; inv:list.images; e2e:test_numbers_images.py::test_list_images_column_equals_the_api_recount; e2e:test_images_anywhere.py::test_list_images_column_shows_the_images_in_antweb; junit:ImagesAnywhereTest#testImagesColumnShowsTheAntWebNumberOnly; junit:ImagesAnywhereTest#testImagesColumnFallsBackToThePlaceNumber; junit:ImagesAnywhereTest#testNoImagesOnlyWhenThereIsNoImageInAntWeb; junit:ImagesAnywhereTest#testEveryRankReadsTheAllAntwebRow; junit:ImagesAnywhereTest#testImagesColumnOfARow; junit:ImagesAnywhereTest#testReadGlobalImageCountNeverThrows; junit:ImagesAnywhereTest#testSetFourCountsTheAllAntwebRows; junit:TaxonTreeCountDbTest#testCountryImagesAreUnchanged; junit:ImagesAnywhereTest#testListImagesArePendingUntilANightlyCountsAntImages; e2e:test_images_anywhere.py::test_list_says_what_the_images_column_counts; unit:test_images_anywhere.py::test_list_images_column_shows_the_antweb_number_only; unit:test_allantweb_image_crawl.py::main; junit:PlaceImageCountDbTest#testAllAntwebImagesCountASynonymUnderTheValidName; junit:PlaceImageCountDbTest#testRedFlaggedSpecimensImagesAreNotCounted |
| 45 | taxonomicPage.do, browse.do | Admin column total ("Total of the ... column", until 2026-10-27 "Subtaxon total") | live sum of the shown rows; end-to-end since 2026-10-27 on the day's place of every kind, in the default view Curated: = the sum of the rows' numbers; rank subfamily = the header of the genus list in the same view (2026-11-03, B77: the subfamily rows' Genera are those of the view) and at most Genera of the place page; rank genus = the header of the species list in the same view (2026-11-02: the genus rows' Species are those of the view); in both, rows whose parent row is not on the list are under no row (B76, fixed 2026-11-03: the nightly count adds the missing parent rows; a row whose parent is a counted name fails, one whose parent is not, B36, is an expected failure); rank species at most Specimens; browse.do of a genus = the sum of its rows; Madagascar (count check test): rank species = Specimens, rank genus and subfamily at most the headers | yes | e2e:test_count_check.py::test_subtaxon_totals_equal_the_place_numbers; e2e:test_numbers_search.py::test_subtaxon_total_is_the_sum_of_the_rows_of_every_kind_of_place; junit:ParentRowsDbTest#testTheCountAddsThemAndTheRowsAddUpToThePlaceNumbers |
| 46 | taxonomicPage.do, browse.do | browse header N Specimens / Species / Genera (M imaged) | live children (Overview.getOverviewCriteria); a genus's species: valid names and morphospecies since 2026-10-28 (`Genus.setChildren`, as the genus row); since 2026-10-26 on a different place of every kind every day: Species of a genus and Genera of a subfamily = the list row (validated by inv:list.genus, inv:list.subfamily) and the rows listed; Specimens of a species = the rows listed, and = the list row only after B15 (xfail) | yes | e2e:test_numbers_live.py::test_browse_header_and_hidden_totals_agree; e2e:test_numbers_lists.py::test_browse_headers_equal_the_list_rows; e2e:test_numbers_lists.py::test_species_browse_imaged_count_equals_the_specimens_with_images; e2e:test_numbers_lists.py::test_species_browse_counts_the_specimens_of_its_list_row |
| 47 | taxonomicPage.do, browse.do | browse (Only the first N of M displayed) | live; N of "of N" = the header (2026-10-26); the page shows limit rows (B42, fixed 2026-11-02) | yes | e2e:test_numbers_live.py::test_browse_header_and_hidden_totals_agree; e2e:test_numbers_lists.py::test_browse_limit_note_names_the_header_number; e2e:test_numbers_lists.py::test_browse_limit_shows_that_many_specimens |
| 48 | taxonomicPage.do, browse.do | browse sub-taxon lines (genus, subfamily, family rows' counts) | list rows of genus, subfamily, family: sums of their child rows (the Formicidae row's Subfamilies: valid subfamilies only since 2026-10-30, `PlaceTotals.childCountCondition`; CountDb crawl; every row reset first since 2026-10-21; Images: images of the ant since 2026-10-27, #44) | yes | inv:list.genus; inv:list.subfamily; inv:list.family; inv:list.images |
| 49 | taxonomicPage.do, browse.do | browse hidden N Imaged X | live; species browse (2026-10-26, every kind of place): M = the specimens listed with an image, also by the API imageCount; genus browse (2026-10-27, every kind of place): M = the children with pictures (Taxon.getHasImages, the pictures of a specimen of the name from anywhere, B45) = the children images.do's All view shows; a specimen with only label photos is not imaged (`Specimen.getHasImages`; B71, decided by Brian Fisher 2026-10-27, Q12) | yes | e2e:test_numbers_live.py::test_browse_header_and_hidden_totals_agree; e2e:test_numbers_lists.py::test_species_browse_imaged_count_equals_the_specimens_with_images; e2e:test_numbers_lists.py::test_browse_headers_equal_the_list_rows; e2e:test_numbers_images.py::test_browse_imaged_count_equals_the_children_with_pictures; junit:CountRules20261027Test#testASpecimenWithOnlyLabelPhotosIsNotImaged |
| 50 | description.do, images.do, comparisons | N times found in <habitat> | live: collection events per habitat, ordered by that number since 2026-10-21; since 2026-10-26 recounted from the API specimen records of the species of a different genus of Madagascar every day; a specimen without a collection code is a collection event of its own (`Species.COLLECTION_EVENTS`; B72, decided by Brian Fisher 2026-10-27) | yes | junit:PlaceTotalsDbTest#testHabitatsAreOrderedByTheNumberShown; e2e:test_numbers_species.py::test_species_page_habitat_summaries_equal_a_recount_of_the_specimens; junit:CountRules20261027Test#testSpecimensWithoutACollectionCodeAreCollectionEventsOfTheirOwn |
| 51 | description.do, images.do, comparisons | N times <microhabitat> | live, as #50 (10 entries, was 11); since 2026-10-26 recounted from the API specimen records of the species of a different genus of Madagascar every day; a specimen without a collection code is a collection event of its own (`Species.COLLECTION_EVENTS`; B72, decided by Brian Fisher 2026-10-27) | yes | junit:PlaceTotalsDbTest#testHabitatsAreOrderedByTheNumberShown; e2e:test_numbers_species.py::test_species_page_habitat_summaries_equal_a_recount_of_the_specimens; junit:CountRules20261027Test#testSpecimensWithoutACollectionCodeAreCollectionEventsOfTheirOwn |
| 52 | description.do, images.do, comparisons | N times <method> | live, as #50 (10 entries, was 11); since 2026-10-26 recounted from the API specimen records of the species of a different genus of Madagascar every day; a specimen without a collection code is a collection event of its own (`Species.COLLECTION_EVENTS`; B72, decided by Brian Fisher 2026-10-27) | yes | junit:PlaceTotalsDbTest#testHabitatsAreOrderedByTheNumberShown; e2e:test_numbers_species.py::test_species_page_habitat_summaries_equal_a_recount_of_the_specimens; junit:CountRules20261027Test#testSpecimensWithoutACollectionCodeAreCollectionEventsOfTheirOwn |
| 53 | description.do, images.do, comparisons | Field guide: the same three summaries | as #50-#52; habitats and methods recounted as #50, #52 (2026-10-26); the field guide shows no microhabitats (B43, xfail) | yes | junit:PlaceTotalsDbTest#testHabitatsAreOrderedByTheNumberShown; e2e:test_numbers_species.py::test_field_guide_habitat_summaries_equal_a_recount_of_the_specimens; e2e:test_numbers_species.py::test_field_guide_shows_the_microhabitat_summary |
| 54 | description.do, images.do, comparisons | Admin Image files (taxon.image_count; labelled ImageCount until 2026-10-27) | taxon.image_count (set 4): images of the taxon's specimens and of the taxa under it (species were never added to their genus); left as every image file (labels included): the label says Image files (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:taxon.images; junit:PlaceTotalsDbTest#testTaxonImageCountsAddTheTaxaUnderThem; junit:CountInvariantsTest#testImagesOfARedFlaggedSpecimenAreNotCounted |
| 55 | description.do, images.do, comparisons | images.do header "N Valid species imaged (out of M valid species)", "N Species imaged (out of M species, valid and curated)" (views of 2026-11-02; since 2026-10-30 the status choice named as on the list's images view, `StatusSet.imagedHeading`, data-imaged and data-total; was "N Species Imaged (M total)"; a species: "N Specimens imaged (out of M specimens)") | live: N = children with a head, profile or dorsal picture (from any specimen of the name, B45), M = all children; since 2026-10-27 on a different genus of a different place of every kind every day: M = browse.do's N and its rows, N = the children of the All view with such a picture | yes | e2e:test_numbers_live.py::test_image_and_comparison_pages_count_the_same_children; e2e:test_numbers_images.py::test_images_page_header_counts_the_children |
| 56 | description.do, images.do, comparisons | images.do child N Images ("T Images (P from X)" from 2026-10-27 to 10-28; one number since) | the child's Images cell of this place's list (#44; `ImagesAnywhere.childImages`): images of the ant of the name in AntWeb, labels never count (Brian Fisher 2026-10-27); when the list says No Images but the page shows images of the name, the images of the ant of the specimen shown (until 2026-10-27: the place's number, falling back to every picture of the specimen shown, B45 option A); the All view showed 0 for every name until 2026-10-27 (B46, fixed); = the list row's cell on every kind of place, default and All view (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | cc:images; inv:list.images; e2e:test_numbers_images.py::test_images_page_child_counts_equal_the_list_rows; unit:test_image_count_fixes.py::test_all_view_shows_the_list_images_of_a_name; junit:ImagesAnywhereTest#testAntImagesShownLeaveLabelsOut |
| 57 | description.do, images.do, comparisons | taxonomicPage images view: "N Valid species imaged (out of M valid species)", "N Genera imaged (out of M genera, valid and curated)" (views of 2026-11-02; since 2026-10-30 the status choice named in the words of the list header, `StatusSet.imagedHeading`; was "N X Imaged (out of M Total)") | live: children with pictures (from any specimen of the name, B45), out of all children; since 2026-10-27 on every kind of place: M = the list header, N = the children shown with pictures, N + the children with no image = M | yes | e2e:test_numbers_pages.py::test_images_view_header_counts_imaged_children; e2e:test_numbers_images.py::test_images_view_header_counts_the_children_with_pictures; junit:StatusChoiceTest#testImagesViewHeaderNamesTheStatusChoice |
| 58 | description.do, images.do, comparisons | oneView total | live: the pictures of one view of the children (layout total, not printed); since 2026-10-27 the children shown (getComparison.do&shot=Head) = the children images.do shows with a head picture, every kind of place | yes | e2e:test_numbers_live.py::test_image_and_comparison_pages_count_the_same_children; e2e:test_numbers_images.py::test_one_view_shows_the_children_with_that_view |
| 59 | description.do, images.do, comparisons | mapComparison (N X Mapped) | live: every child, mapped or not (= browse.do's N, every kind of place since 2026-10-27); N = the children with a map is B19 (xfail) | yes | e2e:test_numbers_live.py::test_image_and_comparison_pages_count_the_same_children; e2e:test_numbers_images.py::test_map_comparison_counts_every_child; e2e:test_numbers_images.py::test_map_comparison_counts_the_children_mapped |
| 60 | description.do, images.do, comparisons | comparison.do child count | live: one checkbox per child with pictures (layout total, not printed); since 2026-10-27 = the children of images.do's All view, every kind of place | yes | e2e:test_numbers_live.py::test_image_and_comparison_pages_count_the_same_children; e2e:test_numbers_images.py::test_comparison_has_one_box_per_child_with_pictures |
| 61 | description.do, images.do, comparisons | specimenReport N Images + label (since 2026-10-27) | live: the specimen's images in the image table (Specimen.getImageCount; the taxon branch is not reached); since 2026-10-27 = the API's /images count of the specimen, for specimens of a different species of a different place of every kind every day; No Images where the no-photos mark is; a specimen's own images, labels included, as on its page (#64): left as it is on 2026-10-27, see "Images columns count ant images only" in section 3; since 2026-10-27 "5 Images + label", "5 Images + 2 labels", "Label only", "No Images" (ImageCounts.text; Specimen.setHasImages counts the labels; the advanced search's rows read them with the page, AdvancedSearch.LABEL_COUNT); images of the ant + label images = the API's /images of the specimen, by shot type | yes | inv:taxon.images; inv:list.images; e2e:test_numbers_images.py::test_specimen_rows_count_the_images_of_each_specimen; unit:test_image_counts_defined.py::test_every_image_count_getter_on_a_page_is_defined; junit:ImagesAnywhereTest#testAntImagesShownLeaveLabelsOut |
| 62 | description.do, images.do, comparisons | X with no image in AntWeb (N) (labelled Non-imaged X until 2026-10-22, "with no image from this place" until 2026-10-27) | live: children without pictures; the pictures come from any specimen of the name, so these are the children without a picture anywhere, not those with no image from this place (B45: Brian Fisher 2026-10-27 chose option A: pictures from anywhere, the label says so); since 2026-10-27 on every kind of place: N + the children with pictures = all children, names listed = min(N, 50) | yes | e2e:test_numbers_live.py::test_unimaged_names_listed_match_their_number; e2e:test_numbers_images.py::test_no_image_list_names_the_children_without_pictures |
| 63 | specimen, collection, locality pages | Specimen Images (admin; labelled Image Count until 2026-10-27) | specimen.image_count (set 4); since 2026-10-27 shown as images of the ant + labels (Specimen.getImagesText) | yes | inv:taxon.images |
| 64 | specimen, collection, locality pages | specimenImages N Images + label (since 2026-10-27) | live: the specimen's images; since 2026-10-27 = the API's /images count (the image table, live) for specimens of a different species of a different place of every kind every day; since 2026-10-27 images of the ant + label images (ImageCounts.text, data-images and data-labels), = the API's /images by shot type | yes | e2e:test_numbers_live.py::test_specimen_images_count_equals_the_api; e2e:test_numbers_images.py::test_specimen_images_page_counts_the_images_of_the_specimen; unit:test_image_counts_defined.py::test_specimen_counts_show_labels_apart |
| 65 | specimen, collection, locality pages | collection N specimens of M species | live; N = the search by the collection code and its specimens, M = distinct genus and species of the specimens' API records, names not identified to genus left out (B52, fixed 2026-10-27); daily sample of collections | yes | e2e:test_numbers_live.py::test_collection_and_locality_counts_equal_their_lists; e2e:test_numbers_search.py::test_collection_counts_equal_a_search_and_the_api |
| 66 | specimen, collection, locality pages | locality N collections | live; one row per collection code since 2026-10-27 (B51); N = the rows = the distinct collection codes of the locality's specimens (search by its locality code); daily sample of localities | yes | e2e:test_numbers_live.py::test_collection_and_locality_counts_equal_their_lists; e2e:test_numbers_search.py::test_locality_collections_equal_the_codes_of_its_specimens |
| 67 | search results | Advanced search N specimens (M imaged) | live, HTTP session; N = the API's records of the same country, genus and statuses, M = the page's rows whose API imageCount > 0; daily sample of country and genus | yes | e2e:test_numbers_live.py::test_search_counts_agree; e2e:test_download_guards.py::test_search_list_is_paged_and_fast; e2e:test_numbers_search.py::test_search_total_and_imaged_count_equal_the_api |
| 68 | search results | Advanced search by taxon N species (from M specimens) | live, session; S = the rows = distinct names of the API's records, M = the specimen search's N; names not identified to genus left out (B54, fixed 2026-10-27), their specimens still in M and given under the list ("N specimens not identified to genus (not counted as species)", linked to the specimen view): that N + the specimens of the species listed = M; the same list feeds Compare, Map and the field guide of the search | yes | e2e:test_numbers_live.py::test_search_counts_agree; e2e:test_numbers_search.py::test_search_by_species_counts_the_names_of_its_specimens; e2e:test_numbers_search.py::test_search_by_species_leaves_out_names_not_identified_to_genus; e2e:test_numbers_search.py::test_search_by_species_note_and_listed_species_add_up_to_the_specimens; junit:SearchSpeciesListTest#testNamesNotIdentifiedToGenusAreNotSpecies; junit:SearchSpeciesListTest#testOnlyUnidentifiedNamesGiveAnEmptyListNotAnError |
| 69 | search results | Compare / map preparation counts | live (log in needed); specimens: the page's rows with an image or API coordinates; species: the header = the rows listed (B50, fixed 2026-10-27), also when the preparation leaves none, and Compare by species no longer fails (B75, fixed 2026-10-27); coordinates read as decimals (B53, fixed 2026-10-27) | yes | e2e:test_numbers_live.py::test_search_counts_agree; e2e:test_numbers_search.py::test_compare_and_map_preparations_count_what_they_list |
| 70 | search results | multiTaxaComparison N specimens | live (log in needed); N = the specimens chosen, and those are the specimens shown | yes | e2e:test_numbers_live.py::test_comparison_of_chosen_specimens_counts_them; e2e:test_numbers_search.py::test_comparison_counts_the_chosen_specimens |
| 71 | search results | Recent images N Images + N labels (since 2026-10-27) | live: one row per image since 2026-10-27 (B47, fixed: images of two specimens of one name with the same shot, number and upload second were one row); = the images the API counts as uploaded in the last 30 days (between its 29 and 31 days); pages of 50 rows (the `length` parameter), the header says "N Images" up to 50, else "Showing records a to b of N records"; since 2026-10-27 the rows' images of the ant + label images (data-images, data-labels); their sum = the image rows | yes | e2e:test_numbers_live.py::test_recent_images_count_equals_the_rows_shown; e2e:test_numbers_images.py::test_recent_images_count_equals_the_images_uploaded; unit:test_image_count_fixes.py::test_recent_images_one_row_per_image |
| 72 | search results | Bay Area search N records | no longer shown: /bayAreaSearch was removed (2026-10-19), so the page cannot be reached | yes | e2e:test_actions.py::test_removed_actions_stay_removed |
| 73 | countries.do, regions.do, subregions.do, adm1s.do, projects.do, museums.do, bioregions.do | Valid species column (labelled Species until 2026-10-21) | = #23 | yes | inv:place.totals.valid; e2e:test_numbers_pages.py::test_list_of_countries_labels_valid_species |
| 74 | countries.do, regions.do, subregions.do, adm1s.do, projects.do, museums.do, bioregions.do | Specimens column | = #26 | yes | inv:place.totals.specimens |
| 75 | countries.do, regions.do, subregions.do, adm1s.do, projects.do, museums.do, bioregions.do | Image files column (labelled Images until 2026-10-27) | = #29 (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:place.images; junit:PlaceImageCountDbTest#testRedFlaggedSpecimensImagesAreNotCounted |
| 76 | countries.do, regions.do, subregions.do, adm1s.do, projects.do, museums.do, bioregions.do | Image files pie (Image Submissions) | = #75 per place (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:place.images |
| 77 | countries.do, regions.do, subregions.do, adm1s.do, projects.do, museums.do, bioregions.do | Specimen Submissions pie | = #74 per place | yes | inv:place.totals.specimens; inv:geo.regions |
| 78 | endemic.do, introduced.do | endemic.do rows | live over is_endemic flags; = the names marked Endemic on the species list, and a daily sample of its names (and of the valid names not on it) recounted by the endemism rule from the API (2026-10-26); since 2026-10-27 counted statuses only, no synonyms (`PlaceTotals.flaggedNamesSql`, the rule of #24, #25; B70); since 2026-10-28 valid names only, native rows only (#24; Brian Fisher), so every name on endemic.do is on the place's species list (the end-to-end test no longer allows an endemic name the list does not show), and the page says what is counted | yes | inv:place.totals.endemic; inv:place.endemic.flags; e2e:test_numbers_live.py::test_endemic_and_introduced_lists_equal_place_numbers; e2e:test_numbers_endemic.py::test_endemic_and_introduced_marks_on_the_species_list_agree; e2e:test_numbers_endemic.py::test_endemic_names_follow_the_endemism_rule; e2e:test_numbers_endemic.py::test_endemic_names_of_countries_are_named_species; junit:CountRules20261027Test#testEndemicAndIntroducedListsAndNumbersShareTheStatusRule |
| 79 | endemic.do, introduced.do | introduced.do rows | live over is_introduced flags; = the names the species list marks Introduced (live, from the native bioregions) that are on the introduced-species list (2026-10-26); since 2026-10-27 counted statuses only, no synonyms (`PlaceTotals.flaggedNamesSql`, the rule of #24, #25; B70) | yes | inv:place.totals.introduced; e2e:test_numbers_live.py::test_endemic_and_introduced_lists_equal_place_numbers; e2e:test_numbers_endemic.py::test_endemic_and_introduced_marks_on_the_species_list_agree; e2e:test_numbers_endemic.py::test_a_place_without_a_bioregion_has_no_introduced_species; junit:CountRules20261027Test#testEndemicAndIntroducedListsAndNumbersShareTheStatusRule |
| 80 | curators, groups, artists, uploads (admin) | curators.do Specimen Uploads | LoginMgr at startup | yes | inv:admin.curators; e2e:test_count_check.py::test_curator_and_artist_pages_equal_the_recount |
| 81 | curators, groups, artists, uploads (admin) | curators.do Description Edits | LoginMgr at startup: page-text edits, every saved version: taxon texts (description_edit, description_hist) and, since 2026-10-22, place and project texts (object_edit, object_hist); said on curators.do and curator.do | yes | inv:admin.curators; e2e:test_count_check.py::test_curator_and_artist_pages_equal_the_recount |
| 82 | curators, groups, artists, uploads (admin) | curator.do specimen uploads | LoginMgr at startup | yes | inv:admin.curators; e2e:test_count_check.py::test_curator_and_artist_pages_equal_the_recount |
| 83 | curators, groups, artists, uploads (admin) | curator.do last upload numbers | the specimens today of the group of the curator's last upload (group_count, recounted every night since 2026-10-22, Q13); its date apart; end-to-end since 2026-10-27: Specimen, Ungeoreferenced, Flagged = the group's numbers that the count check compared with its recount, on the curator with the most uploads and a daily sample of the others; since 2026-10-30 the block names the upload's group (data-group-id; the test used the first group link of the page, the curator's own group), and a curator whose uploads are all from before 2018 (no upload_id) shows the last one (`UploadDb.getLastUpload`; it showed none) | yes | inv:admin.groups; junit:NightlyCountOrderDbTest#testGroupNumbersAreRecountedEveryNight; junit:UploadRecordDbTest#testLastUploadAlsoWithoutUploadId; e2e:test_numbers_uploads.py::test_curator_last_upload_numbers_equal_the_checked_group_numbers |
| 84 | curators, groups, artists, uploads (admin) | curator.do image uploads, image files uploaded (labelled Images Uploaded until 2026-10-27) | LoginMgr at startup; files uploaded, label images included: labelled Image files | yes | inv:admin.curators; e2e:test_count_check.py::test_curator_and_artist_pages_equal_the_recount; unit:test_image_counts_defined.py::test_every_image_count_getter_on_a_page_is_defined |
| 85 | curators, groups, artists, uploads (admin) | groups.do Uploads | live count of upload rows (GroupAction); ant_group.upload_count orders the page | yes | inv:admin.groups; e2e:test_count_check.py::test_group_pages_equal_the_stored_upload_numbers |
| 86 | curators, groups, artists, uploads (admin) | groups.do Specimens | the group's specimens today (group_count, nightly set 3 and, since 2026-10-29, each upload of the group for its own row (GroupCountDb.recountGroup; the Totals row stays nightly); the last upload row until a nightly run or an upload has counted the group) | yes | inv:admin.groups; e2e:test_count_check.py::test_group_pages_equal_the_stored_upload_numbers |
| 87 | curators, groups, artists, uploads (admin) | groups.do Ungeoreferenced, Flagged, Collections, Localities | group_count, as #86; Collections leaves out specimens without a collection code (`GroupCountDb.COLLECTION`; uploads store none since 2026-10-30, Brian Fisher; random `tc<number>` codes stored before stay until a cleanup) | yes | inv:admin.groups; e2e:test_count_check.py::test_group_pages_equal_the_stored_upload_numbers |
| 88 | curators, groups, artists, uploads (admin) | groups.do Subfamilies, Genera, Species | group_count, as #86, by one rule (`GroupCountDb.countColumns`, also each upload's row, UploadDb.getCounts): Species = distinct full names of species and subspecies of status valid or morphotaxon (`PlaceTotals.rankCondition`, the counting rules of 2026-10-28, as every Species; before: the five counted statuses), ants, identified to genus; Subfamilies, Genera = distinct valid ant subfamilies and genera identified to genus of the counted specimens (Q8 2026-10-22: Species counted epithets of valid names); since 2026-10-30 a genus counts only when its genus row is a valid genus or a morphogenus (`GroupCountDb.COUNTED_GENERA`; genera rule, section 5; pending until the nightly recount); until a nightly run has recounted the groups by the 2026-10-28 rule (compute.log `COUNTING_RULES_LOG`) the check calls a Species difference pending; explained on groups.do and group.do | yes | inv:admin.groups; junit:NumberDecisionsDbTest#testGroupSpeciesAreFullNamesAndTotalsAreDistinct; junit:NumberDecisionsDbTest#testGroupNumbersOfTheOldRuleArePendingUntilTheNightlyRun; junit:GeneraRuleDbTest#testUploadGroupGeneraCountValidGeneraAndMorphogenera; e2e:test_count_check.py::test_group_pages_equal_the_stored_upload_numbers |
| 89 | curators, groups, artists, uploads (admin) | groups.do Totals row: contributors, Uploads, Specimens, Ungeoreferenced, Flagged | live sums of the rows (each specimen belongs to one group) | yes | e2e:test_count_check.py::test_group_pages_equal_the_stored_upload_numbers; inv:admin.groups |
| 90 | curators, groups, artists, uploads (admin) | groups.do Totals Collections, Localities, Subfamilies, Genera, Species | all listed groups' specimens together, distinct over them (group_count row 0, nightly set 3, `GroupCountDb.totalsSql`; Q8 2026-10-22: Subfamilies, Genera, Species were the largest row, Collections and Localities the sum); "-" until the first nightly count | yes | inv:admin.groups; junit:NumberDecisionsDbTest#testGroupSpeciesAreFullNamesAndTotalsAreDistinct; e2e:test_count_check.py::test_group_pages_equal_the_stored_upload_numbers |
| 91 | curators, groups, artists, uploads (admin) | group.do uploads, last upload numbers | group_count, as #86 (the page says so, with the date of the count) | yes | e2e:test_count_check.py::test_group_pages_equal_the_stored_upload_numbers; inv:admin.groups |
| 92 | curators, groups, artists, uploads (admin) | artists.do Image files (labelled Images until 2026-10-27), Specimen | live / ArtistMgr; files credited, label images included: labelled Image files | yes | inv:admin.artists; e2e:test_count_check.py::test_curator_and_artist_pages_equal_the_recount; unit:test_image_counts_defined.py::test_every_image_count_getter_on_a_page_is_defined |
| 93 | curators, groups, artists, uploads (admin) | listImageUploads totals (Image files column, holding N image files; labelled Image Count, images until 2026-10-27) | live / image_upload | yes | inv:admin.imageUploads; inv:admin.imageUploadArtists; e2e:test_count_check.py::test_image_upload_and_orphan_counts_equal_the_recount |
| 94 | curators, groups, artists, uploads (admin) | imageUploadReport counts (Image files, Total image files uploaded; labelled Images until 2026-10-27) | image_upload.image_count, written when the upload ends; saved and not saved files; end-to-end since 2026-10-27 on the newest batch and a daily sample: Images = the batch's Image Count on listImageUploads.do = the files the report lists (complete batches), Total Images Uploaded = the files listed as saved, Not saved = the files listed as not saved; the report of a batch from an earlier year failed until 2026-10-27 (B74, fixed) | yes | inv:admin.imageUploads; e2e:test_numbers_uploads.py::test_image_upload_report_counts_equal_their_files |
| 95 | curators, groups, artists, uploads (admin) | Upload report Inserted / Updated Species | at upload: the species and subspecies names the upload added to the taxon table (Inserted) or whose existing row it rewrote (Updated), each name once (B60, B61 fixed 2026-10-27; a name AntCat gives is never rewritten by a specimen upload); shown in every upload's report (`UploadDetails.speciesCountsHtml`; until PR #291's second staging run only in reports with project statistics, that is species list uploads, so a specimen upload's report never showed them); end-to-end: the test group's reports = the names of the uploaded file that AntCat does not give (API /taxa), other reports at most the file's distinct names; reports written before the fix (no `report-counts-rule` mark) only at most the lines processed | yes | junit:UploadReportCountsTest#testANewNameIsInsertedNotUpdated; junit:UploadReportCountsTest#testEachUpdatedNameCountsOnce; junit:UploadReportCountsTest#testSpecimenUploadReportShowsTheSpeciesCounts; junit:UploadReportCountsTest#testReportPrintsTheCountsWithoutProjectStatistics; e2e:test_numbers_uploads.py::test_upload_report_counts_agree_with_the_uploaded_file; e2e:test_specimen_upload_roundtrip.py::test_specimen_upload_round_trip |
| 96 | curators, groups, artists, uploads (admin) | Upload report message counts | at upload; static error list (B23). End-to-end since 2026-10-27 on the test group's newest reports and a daily sample: Record Count = the uploaded file's lines after the header (B62 fixed 2026-10-27: counted the header too), Parsed at most Record Count, Processed at most Parsed, Inserted Specimens at most Processed; each line's (total:N): sections listed by key have N lines, sections of values list at most N values and N specimens; the Inserted Specimens of the test group's last upload = the group's specimens (statistics.do by upload, and its codes found through the API). Red Flagged Specimens: no test of its own | yes | e2e:test_numbers_uploads.py::test_upload_report_counts_agree_with_the_uploaded_file; e2e:test_numbers_uploads.py::test_statistics_by_upload_counts_each_groups_specimens; e2e:test_specimen_upload_roundtrip.py::test_specimen_upload_round_trip |
| 97 | curators, groups, artists, uploads (admin) | Admin statistics.do table | statistics rows (latest row checked; older rows are history); V. Sp. Imaged counts valid species and subspecies imaged since 2026-10-21; Total Images: images of the ant in rows written since 2026-10-27, every image file in older rows (the column title says so) (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:home; inv:admin.statistics; junit:CountInvariantsTest#testImagesOfARedFlaggedSpecimenAreNotCounted |
| 98 | curators, groups, artists, uploads (admin) | statistics.do breakdowns | live; byUpload (specimens per upload group) checked against each group's stored `ant_group.upload_specimens`, which the group's own upload writes and, since 2026-11-11, the daily update for every group (the last statement of set 3, `GroupDb.recountUploadSpecimens`; a number an upload wrote during the set is left; until then a correction or deletion of a group's specimens left it stale until the group's next upload: production 11-11, group 52, live 21,102, stored 21,099); byProject, byBioregion, byMuseum, byGeolocale are row counts of the lists. End-to-end since 2026-10-27: byUpload together at most the current specimen count, the test group's = its codes found through the API; on the daily row of each other breakdown every number up to 998 = the names its taxaList.do link lists, All = Extinct + Extant, Imaged v. sp. at most V. sp. (B63, B64 fixed 2026-10-27) | yes | inv:admin.statistics; junit:GroupSpecimenCountDbTest#testStaleNumbersAreCorrectedAndTheOthersAreUnchanged; junit:GroupSpecimenCountDbTest#testTheCountChecksDifferencesAreGoneAfterTheRecount; junit:GroupSpecimenCountDbTest#testANumberWrittenSinceTheReadIsNotOverwritten; e2e:test_numbers_uploads.py::test_statistics_by_upload_counts_each_groups_specimens; e2e:test_numbers_uploads.py::test_statistics_breakdown_numbers_equal_their_lists |
| 99 | curators, groups, artists, uploads (admin) | orphanedImages Found N image files of M specimen codes that are not in AntWeb (images until 2026-10-27) | live: images whose specimen code is no specimen record, and their codes (`OrphanedImages.COUNTS_SQL`, Q9 2026-10-22: the page said "N orphaned images" and counted codes); explained on the page | yes | inv:stats.images; junit:NumberDecisionsDbTest#testOrphanedImagesCountImagesAndCodes; e2e:test_count_check.py::test_image_upload_and_orphan_counts_equal_the_recount |
| 100 | API v3.1 | /specimens imageCount, antImageCount, labelImageCount | specimen.image_count; imageCount kept (every image file); since 2026-10-27 antImageCount and labelImageCount of the specimen (api.imageSplit, one grouped query per reply), also on /geoSpecimens | yes | inv:taxon.images; e2e:test_api_numbers.py::test_images_meta_counts_and_specimen_image_count; e2e:test_numbers_live.py::test_specimen_images_count_equals_the_api; unit:test_api_image_split.py::test_split_by_specimen; unit:test_api_image_split.py::test_replies_add_the_fields |
| 101 | API v3.1 | /specimens, /geoSpecimens, ... meta count | live: rows in this page; since 2026-10-27 recounted: the records of the filter (day's country; pages of k add up to 2k), the records inside the radius box and their genera (distinct=genus) | yes | e2e:test_api_numbers.py::test_meta_count_is_the_rows_returned; e2e:test_numbers_api_meta.py::test_specimen_counts_are_the_records_of_the_filter |
| 102 | API v3.1 | /geoSpeciesStats caste counts | live; every subcaste count only within its caste since 2026-10-27 (decided by Brian Fisher); since 2026-10-27 the ten counts of two names of the day's country, adm1 and subregion are recounted from /specimens; a name shared by several geolocales counted each specimen once per geolocale (B55, fixed 2026-10-27); adm1 by name only (B26); the caste of a specimen is read from its Life Stage field (`Caste.getCasteValues`; B69, 2026-10-27: "alate male" is a male, "and" and "/" split without a comma; existing specimens after their next upload or a calcCaste run, the counts after the next nightly run) | yes | e2e:test_api_numbers.py::test_geo_species_stats_castes_add_up; unit:test_api_counts.py::test_geo_species_stats_counts_ergatoids_of_their_caste; e2e:test_numbers_api_meta.py::test_geo_species_stats_castes_equal_the_specimens; e2e:test_numbers_api_meta.py::test_geo_species_stats_of_a_shared_name_count_each_specimen_once; unit:test_api_counts.py::test_geo_species_stats_counts_the_list_of_one_geolocale; unit:test_api_counts.py::test_get_geolocale_prefers_a_valid_geolocale; unit:test_api_decisions.py::test_geo_species_stats_counts_subcastes_within_their_caste; junit:CountRules20261027Test#testAlateMaleIsAMale; junit:CountRules20261027Test#testAndAndSlashSplitWithoutAComma |
| 103 | API v3.1 | /taxa subfamilyCount, genusCount, speciesCount, specimenCount | taxon columns, copied each night from the name's Global row (`TaxonApiCountDb`, since 2026-10-26; B11) | yes | inv:api.taxa |
| 104 | API v3.1 | /taxa imageCount, antImageCount, labelImageCount | taxon.image_count (= #54); imageCount kept; since 2026-10-27 antImageCount and labelImageCount: the images of the specimens filed under the name (api.imageSplit by taxon_name) (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:taxon.images; unit:test_api_image_split.py::test_split_by_name |
| 105 | API v3.1 | /taxa meta taxonCount | live; since 2026-10-27: the species of the day's genus = the species rows of all its taxa, pages add up | yes | e2e:test_api_numbers.py::test_meta_count_is_the_rows_returned; e2e:test_numbers_api_meta.py::test_taxa_count_is_the_taxa_of_the_filter |
| 106 | API v3.1 | /images meta counts | live: rows in this page; specimenCount was 1 and one empty specimen when nothing was found, and every specimen but the last was under 'spefimenCode' (fixed 2026-10-22); since 2026-10-27 a reply of many specimens and its specimens one by one (/images, /specimens imageCount); since 2026-10-27 the metaData also has antImageCount and labelImageCount of the reply's images | yes | e2e:test_api_numbers.py::test_images_meta_counts_and_specimen_image_count; e2e:test_api_numbers.py::test_images_of_an_unknown_specimen_are_none; unit:test_api_counts.py::test_images_of_nothing_are_no_specimen; unit:test_api_counts.py::test_images_are_grouped_by_specimen; e2e:test_numbers_api_meta.py::test_images_counts_of_many_specimens; unit:test_api_image_split.py::test_shot_split |
| 107 | API v3.1 | /taxaImages meta counts | live; since 2026-10-27 the specimens and images of a name = its specimens with images (/specimens) and their images (/images); since 2026-10-27 the metaData also has antImageCount and labelImageCount of the reply's images | yes | e2e:test_api_numbers.py::test_taxa_images_meta_counts; e2e:test_numbers_api_meta.py::test_taxa_images_counts_equal_the_specimens_and_images_of_the_name; unit:test_api_image_split.py::test_replies_add_the_fields |
| 108 | API v3.1 | /geolocales subfamily, genus, species, specimen counts | = #20-#22, #26 | yes | inv:place.totals.subfamily; inv:place.totals.genus; inv:place.totals.species; inv:place.totals.specimens |
| 109 | API v3.1 | /geolocales imageCount, imagedSpecimenCount | = #29, #27; imageCount = the place's Image files (every file; the API page says so) (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:place.images; cc:place images; junit:PlaceImageCountDbTest#testRedFlaggedSpecimensImagesAreNotCounted |
| 110 | API v3.1 | /geolocales endemic, introduced counts | = #24, #25 (Endemic: valid native names only since 2026-10-28); since 2026-10-27 compared with the place page of the day's country, adm1, region, subregion and Madagascar | yes | inv:place.totals.endemic; inv:place.totals.introduced; inv:place.endemic.flags; e2e:test_numbers_api_meta.py::test_geolocale_endemic_and_introduced_equal_the_place_page |
| 111 | API v3.1 | /geolocales meta geolocaleCount | live: geolocales returned; since 2026-10-27 the adm1s of the day's countries and the geolocales of a name recounted from the lists by rank | yes | e2e:test_api_numbers.py::test_meta_count_is_the_rows_returned; e2e:test_numbers_api_meta.py::test_geolocales_count_is_the_geolocales_of_the_filter |
| 112 | API v3.1 | /geolocaleTaxa per row counts | list rows (imageCount: images of the ant since 2026-10-27, #44); since 2026-10-27 compared with the subfamily, genus and species lists (taxonomicPage.do) of the day's country, adm1, region and subregion; the list's Images cell shows the AntWeb-wide number since 2026-10-28 (#44), so it is checked to be at least the row's imageCount (the place's own) (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:list.genus; cc:specimens; cc:images; inv:list.images; e2e:test_numbers_api_meta.py::test_geolocale_taxa_rows_equal_the_place_lists; junit:PlaceImageCountDbTest#testRedFlaggedSpecimensImagesAreNotCounted |
| 113 | API v3.1 | /geolocaleTaxa meta count | live: taxa returned (named geolocaleCount); since 2026-10-27 on every page of the day's places, with the rank filter and every name of the place's list | yes | e2e:test_api_numbers.py::test_meta_count_is_the_rows_returned; e2e:test_numbers_api_meta.py::test_geolocale_taxa_rows_equal_the_place_lists |
| 114 | API v3.1 | /bioregions 6 counts | bioregion table (= place page) | yes | inv:place.totals.subfamily; inv:place.totals.genus; inv:place.totals.species; inv:place.totals.specimens; inv:place.images |
| 115 | API v3.1 | /bioregions meta count | live | yes | e2e:test_api_numbers.py::test_meta_count_is_the_rows_returned; e2e:test_api_numbers.py::test_bioregions_count_equals_the_bioregions_page |
| 116 | API v3.1 | /bioregionTaxa per row counts | list rows (imageCount: images of the ant since 2026-10-27, #44); since 2026-10-27 compared with the subfamily, genus and species lists of the day's bioregion; the list's Images cell shows the AntWeb-wide number since 2026-10-28 (#44), so it is checked to be at least the row's imageCount (the place's own) (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:list.genus; cc:specimens; inv:list.images; e2e:test_numbers_api_meta.py::test_bioregion_taxa_rows_equal_the_bioregion_lists |
| 117 | API v3.1 | /unimagedGeolocaleTaxa list | live: names without a specimen with an image of the ant anywhere in AntWeb (was image_count > 1); the API page says so (Q12, 2026-10-22) (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | unit:test_api_counts.py::test_a_taxon_with_one_image_of_the_ant_is_imaged; unit:test_api_counts.py::test_endpoint_uses_the_rule; junit:NumberDecisionsDbTest#testUnimagedMeansNoPhotoOfTheAntAnywhere; e2e:test_api_numbers.py::test_meta_count_is_the_rows_returned; e2e:test_numbers_api_meta.py::test_unimaged_names_have_no_image_of_the_ant; unit:test_api_counts.py::test_unimaged_names_have_a_fixed_order |
| 118 | API v3.1 | /bioregionTaxa, /geolocaleTaxa meta and dev counters | live: taxa returned (geolocaleTaxa: geolocaleCount, bioregionTaxa: bioregionCount, unimagedGeolocaleTaxa: unimagedGeolocaleTaxaCount, with byCaste also unimagedGeolocaleTaxaCasteCount = name and caste pairs); since 2026-10-27 on every page of the day's places; byCaste=true was ignored (B57, fixed 2026-10-27) | yes | e2e:test_api_numbers.py::test_meta_count_is_the_rows_returned; e2e:test_numbers_api_meta.py::test_geolocale_taxa_rows_equal_the_place_lists; e2e:test_numbers_api_meta.py::test_bioregion_taxa_rows_equal_the_bioregion_lists; e2e:test_numbers_api_meta.py::test_unimaged_by_caste_counters; unit:test_api_counts.py::test_by_caste_true_is_by_caste |
| 119 | project.do?name=poeants (Port of Entry) and its list | Specimens, Imaged specimens, Images, the Specimens pie, the list's Specimens and Images columns, projects.do Specimens and Images | none since 2026-10-22: a species list only (`Project.isSpeciesListOnly`); the nightly run sets them to 0 and counts none (ProjTaxonCountDb.recount, ProjectDb.updateCounts, PlaceImageCountDb); the pages show "-" or no column and say that these species were intercepted at ports of entry, are not established introductions and are not counted in any place total or the introduced species list; the count check skips the project with a note | yes | junit:NumberDecisionsDbTest#testPortOfEntryHasNoNumbersAndIsNotChecked; e2e:test_numbers_pages.py::test_port_of_entry_is_a_species_list_without_numbers |
| 120 | description.do (species, subspecies) | Imaged: worker (N specimens: N worker, N major/soldier), queen (N), male (N), other (N), caste not given (N) (since 2026-10-27) | live (`ImagedCastes`, decided by Brian Fisher 2026-10-27): the specimens of the name, from anywhere in AntWeb, with an image of the ant (labels do not count: `StatisticsDb.ANT_IMAGE`, Q12), by specimen.caste (and subcaste when more than one is given; the ordinary worker subcaste, "normal" in the data, is shown as "worker", Brian Fisher 2026-10-27); one grouped query (specimen key on taxon_name, image joined on image_of_id); each caste and subcaste links to images.do with that caste; recounted from the API (/taxaImages without label shots, /specimens caste) for Leptogenys purpurea and the day's species of Madagascar (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | e2e:test_images_anywhere.py::test_species_page_imaged_castes_equal_a_recount; junit:ImagesAnywhereTest#testImagedCastesLine; junit:ImagesAnywhereTest#testImagedCastesQueryLeavesLabelsOut; junit:NumberDecisionsDbTest#testPhotosOfRedFlaggedSpecimensAreNotCounted |
| 121 | description.do, browse.do, images.do (genus) | Not identified to species: <Genus> (indet), N specimens (M imaged) (since 2026-10-29) | live (`PlaceTaxonNotes.countIndet`, decided by Brian Fisher 2026-10-29): the specimens filed under "<Genus> (indet)" in the page's place (country, adm1, bioregion, museum: `Overview.getOverviewCriteria`, as the indet page lists them; region, subregion: its countries, `Geolocale.regionCountriesSql`; all of AntWeb: every specimen; curated projects: no line), M those with an image of the ant (`StatisticsDb.ANT_IMAGE`, labels do not count); shown only when N > 0, linked to the indet name's page of the same kind and place; not a species, no other number of the page changes; **2026-10-30** in a place that selects specimens the line is "Specimens collected here not on the species list: N (names: ...)": every specimen collected there (red-flagged left out) under a name of the genus that is not a species of the place's list, the indet name being one of them, N linked to the place's specimen search and each name to its page in the place (`PlaceTaxonNotes.otherNamesLineHtml`); all of AntWeb keeps the indet line; **2026-11-02** the names not in the list of the current view, shown in Valid and Curated only (in All specimen names those names are on the list; `ListView.genusOtherNames`) (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | junit:PlaceTaxonNotesTest#testIndetLineTextAndLink; junit:PlaceTaxonNotesTest#testOtherNamesSearchAndLine; junit:PlaceTaxonNotesDbTest#testGenusPageLineAndPlaceSpecimens; e2e:test_list_sources.py::test_genus_page_names_specimens_not_on_the_list; junit:PlaceTaxonNotesDbTest#testIndetLineCountsThePlacesSpecimens; e2e:test_genus_indet_places.py::test_genus_page_names_its_specimens_identified_only_to_genus; e2e:test_genus_indet_places.py::test_day_genus_indet_line |
| 122 | home page `/` (genInc/statistics.jsp) | N species with specimens in AntWeb (P% of valid living species) | since 2026-11-06 (Brian Fisher: the measure of AntWeb's coverage): statistics column living_species_specimens (`StatisticsDb.LIVING_SPECIES_WITH_SPECIMENS_SQL`): valid living ant species (row 1) with at least one specimen record filed under the name; the share = of row 1, rounded | yes | inv:home; junit:PlaceTotalsDbTest#testHomeNumbersOf20261106; e2e:test_home_numbers.py::test_home_box_numbers_and_labels; e2e:test_home_numbers.py::test_home_box_numbers_nest_and_shares_are_right |
| 123 | statsPage.do | N valid living ant species (+ N fossil species), N species with specimens in AntWeb, N species imaged (P% of valid living species; P% of those with specimens), N fossil species imaged, N valid living subspecies, N morphospecies, N imaged | since 2026-11-06: AntwebMgr.getHomeCount, from the latest statistics row (`StatisticsDb.HOME_COLUMNS`: the home numbers, rows 1, 3, 122, and fossil_species_imaged, living_subspecies, morphospecies, morphospecies_imaged: `FOSSIL_SPECIES_IMAGED_SQL`, `LIVING_SUBSPECIES_SQL`, `MORPHOSPECIES_SQL` (species and subspecies rank, identified to genus), `MORPHOSPECIES_IMAGED_SQL`); "not yet counted" until a nightly run writes them; living species, fossil species and living subspecies = statsPage Global Data (Extant and Fossil Valid Species, Extant Valid Subspecies) | yes | inv:home; junit:PlaceTotalsDbTest#testHomeNumbersOf20261106; e2e:test_home_numbers.py::test_statspage_has_the_home_numbers_and_the_details |
| 124 | imagingProgress.do (headline) | N of M valid living ant species and subspecies are imaged (P%); still to image: M minus N (since 2026-11-08) | table imaging_progress, row all/living, filled once a day by the daily update (set 4, step imagingProgress, after the statistics row; `ImagingProgressDb.fill`, decided by Brian Fisher 2026-11-08): M = valid living ant species and subspecies identified to genus (`ImagingProgressDb.living`); N = those a specimen with a photo of the ant is filed under, anywhere in AntWeb (`StatisticsDb.ANT_IMAGE`: label photos never count, Q6, Q12); P one decimal; recounted every morning by exists subqueries (count check), imaged <= total (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:imaging.progress; junit:ImagingProgressDbTest#testALabelOnlyNameIsUnimaged; junit:ImagingProgressDbTest#testAllOfAntWebEqualsTheHomePageNumbers; e2e:test_imaging_progress.py::test_page_loads_with_the_headline; junit:ImagingProgressDbTest#testPhotosOfRedFlaggedSpecimensDoNotCount |
| 125 | imagingProgress.do | Species only: N of M valid living species imaged (the home page's "species imaged"); fossil species and subspecies: N of M imaged | rows all/living-species (`StatisticsDb.validAntSpeciesOnly`, fossil 0: equals statistics.living_species and living_species_imaged, rows 1 and 3, written in the same set 4 just before) and all/fossil (valid fossil species and subspecies; not in the headline, Brian Fisher 2026-11-08); the count check compares them with a recount by `LIVING_SPECIES_SQL`, `LIVING_SPECIES_IMAGED_SQL` and the stored statistics row (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:imaging.progress; junit:ImagingProgressDbTest#testAllOfAntWebEqualsTheHomePageNumbers; e2e:test_imaging_progress.py::test_headline_species_equals_the_statistics_page; junit:ImagingProgressDbTest#testPhotosOfRedFlaggedSpecimensDoNotCount |
| 126 | imagingProgress.do ("Can be imaged now"), its CSV and the genus view (genus=) | N of the unimaged names have specimens in AntWeb, N of them a specimen at CASC; the CSV and genus view list the unimaged names with their specimens, CASC specimens and museums | unimaged_with_specimens: not imaged and at least one specimen record filed under the name (any museum); unimaged_casc: of those, a specimen with museum CASC; the list (`ImagingProgressDb.UNIMAGED_SQL`) has total minus imaged names, CASC specimens first, then most specimens; since 2026-11-11 it is stored by the fill in the transaction of the counts (table `imaging_unimaged`; until then read live at the first request after a fill and kept in memory, so after a restart the CSV could have other names than the page counted: staging 2026-11-10, page 2,773, CSV 2,780), so the CSV always has the page's "Still to image" rows and each genus view its genus row's total minus imaged; counts without their stored list are not shown ("not counted yet", CSV 503); checked by the count check (the list counted now, and the stored list) (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:imaging.progress; junit:ImagingProgressDbTest#testUnimagedListAndCsv; junit:ImagingProgressDbTest#testTheCsvAlwaysHasThePagesStillToImage; junit:ImagingProgressDbTest#testDataChangingDuringTheFillNeverSeparatesTheCountsAndTheList; junit:ImagingProgressDbTest#testCountsWithoutTheirListAreNotShown; e2e:test_imaging_progress.py::test_csv_downloads_every_unimaged_name; e2e:test_imaging_progress.py::test_genus_view_lists_its_unimaged_names; junit:ImagingProgressDbTest#testPhotosOfRedFlaggedSpecimensDoNotCount |
| 127 | imagingProgress.do tables (by museum, bioregion, country, genus) | Valid living species and subspecies, Imaged, %, Imaged from here, Unimaged (linked to the unimaged list), Unimaged but with specimens in AntWeb, At CASC | one grouped query per kind (`ImagingProgressDb.progressSql`): bioregion and country, the valid living species and subspecies of the place's list (the Valid view, `PlaceTotals.VALID_CONDITION`, so Valid living species = the list's "out of N" with isImaged=false); genus, those of the genus (key subfamily + genus); museum, those with a specimen there. Imaged: anywhere in AntWeb; Imaged from here: a photo of the ant of a specimen collected in the place (specimen.bioregion; specimen.country, island_country for island countries as the place's Images; museum: a specimen of the museum); none for genera. Every row: from here <= imaged <= total, with specimens <= unimaged, CASC <= with specimens; a sample of each kind (the largest and some chosen by the day) recounted every morning (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:imaging.progress; junit:ImagingProgressDbTest#testImagedElsewhereIsImagedButNotImagedFromHere; junit:ImagingProgressDbTest#testCountCheckAgreesAndFindsAWrongRow; e2e:test_imaging_progress.py::test_rows_nest_and_a_bioregion_opens_its_unimaged_list; junit:ImagingProgressDbTest#testPhotosOfRedFlaggedSpecimensDoNotCount |
| 128 | imagingProgress.do (Trend) | valid species and subspecies imaged per day (fossils included), of how many, %; living species imaged (from 2026-11-06) | the last statistics row of each day since 2026-11-12 (`ImagingProgressDb.RULE_SINCE`, the one date of the query and of the page's texts: the first day counted on every server by the rule that photos of red-flagged specimens do not count; decided 2026-11-09, on production from 2026-11-11 after that day's daily update, so production's rows of 11-09 to 11-11 are of the old rule or mixed; until then since 2026-10-28; earlier days left out, the page says so, also while there are no rows yet): valid_species_imaged (`StatisticsDb.VALID_SPECIES_IMAGED_SQL`, valid ant species and subspecies, fossils included, a photo of the ant) and extant_taxa (`VALID_SPECIES_SQL`, row 6), living_species_imaged (row 3); stored numbers, the latest row recounted every morning by inv:admin.statistics and inv:home (red-flagged specimens excluded, Brian Fisher 2026-11-09) | yes | inv:admin.statistics; inv:home; junit:ImagingProgressDbTest#testTrendReadsTheLastRowOfEachDaySinceTheRuleChanged |
| 129 | place pages (every place page with a list: valid geolocales with list rows, bioregions, museums, projects including the site-wide ones, not allantwebants) | Subfamilies, Genera, Species / subspecies / morphospecies (and Valid species / subspecies of Fossil Ants and the AntCat list): that they are list totals at all (since 2026-11-11) | the page shows the list totals of `place_view_total` (the headers of the place's lists in its default view, counted by the daily update with the list's own code, `PlaceViewTotalDb`); a place without them falls back to the place's stored counts (rows 20 to 22), which another rule counts, and nothing said so: Fossil Ants and the AntCat list were never counted (production 2026-11-11: Genera 259 beside a list of 258). The count check compares, for every place of `PlaceViewTotalDb.places()` (the daily update's own list), the totals stored and counted in the last 8 days (`FRESH_DAYS`; a place is counted at least every 7) with the number the place has (`expectedTotals`: 3; 4 on Fossil Ants and the AntCat list, whose Valid species is a list total too); a place without them is a real difference, never pending | yes | inv:place.view.totals; junit:NumberChecksTest#testAPlacePageWithoutFreshListTotalsIsARealDifference; junit:PlaceViewTotalDbTest#testTheSiteWideListsAreCountedToo; e2e:test_numbers_lists.py::test_list_headers_equal_place_numbers_of_every_kind; unit:test_count_check_alert.py::test_the_check_is_run_and_listed |
| 130 | dataExtract.do, the extract's job page (jobStatus.do) and manifest.txt / manifest.json (admins) | Specimens in the file; by country and island country (each spelling apart); by bioregion; without coordinates; name has no taxon row; red-flagged; specimens of other families left out, by family (ants only) or in the file (all families); no family given; filed under an outdated country name; genera and distinct names; names on the bioregion's species list, those without a specimen in the extract and the names of other families left out; the specimens with no country (in the file, in the database, with coordinates, names, by upload group) (since 2026-11-10) | live, counted while the file is written (`DataExtract.Stats`): one per row of specimens.csv.gz, the specimens whose `country` is one of the chosen countries or one of their old names (a `geolocale` country row that is not valid, `valid_name` = the country) . An island that is its own country (a valid country row with `is_island = 1`: Hawaii, Galapagos Islands) is counted only when it is named with the countries, never with its political parent (Brian Fisher, 2026-11-11); a named island's specimens are found by `country` or `island_country`, a specimen marked as another island's is left out whatever its country says, and the manifest names the islands left out. The check: the rows written per (country, island country), by the bytes of the names, must equal a separate `count(*)` of `specimen` taken after the last page (`DataExtract.compareCounts`); VERIFIED only when every pair is equal, otherwise the job ends as "Did not complete" and names the differences. A requested country with 0 specimens the file can hold (ants, when ants only) is a warning. Ants only (the default) uses the same filter (`DataExtract.ANTS`) for the file and for the count; a row that is not an ant in an ants-only file, or a no-country file whose rows differ from the database's count, is not verified | yes | junit:DataExtractDbTest#testTheExtractIsCompleteAndVerified; junit:DataExtractDbTest#testADifferentDatabaseCountIsReported; junit:DataExtractDbTest#testAnIslandIsNotPartOfItsPoliticalParent; junit:DataExtractDbTest#testAnOldIslandNameIsTheIslandAndNoSpecimenIsInBothFiles; junit:DataExtractDbTest#testSpecimensUnderAnOutdatedNameAreInTheFileAndCounted; junit:DataExtractDbTest#testTwoSpellingsOfACountryAreCountedApartAndVerified; junit:DataExtractDbTest#testAntsOnlyIsTheDefaultAndSaysWhatItLeftOut; junit:DataExtractDbTest#testARowThatIsNotAnAntInAnAntsOnlyFileIsNotVerified; junit:DataExtractDbTest#testTheBioregionListIsOfAntsOnlyToo; junit:DataExtractDbTest#testTheSpecimensWithoutACountryGoInTheirOwnFile; junit:DataExtractTest#testADifferentCountIsNamed; e2e:test_data_extract.py::test_small_extract_end_to_end |

## 3. The 24 numbers that looked wrong (inventory of 2026-10-20)

Checked again against master after #189, #191, #192, #204 and #206.

| Id | What looked wrong | Now |
|---|---|---|
| B1 | Geolocale Valid species never recounted (`GeolocaleDb.finish()` had no caller) | **Fixed**: written by every nightly geolocale count (`GeolocaleDb.finish(id)`). Test: `inv:place.totals.valid`, `PlaceTotalsDbTest#testFinishWritesEveryTotalFromTheList`. |
| B2 | Project totals a day behind their list (totals summed before the list was recounted) | **Fixed**: set 3 recounts the lists first, then the totals; the admin recount of one project too. Test: `inv:place.totals.specimens`. |
| B3 | Specimens per Subfamily pie: museums and bioregions counted every specimen in AntWeb of their names | Geolocales fixed by #192; **fixed** for museums, bioregions and projects (`PlaceTotals.specimenPieSql`). Test: `inv:place.pies`, `PlaceTotalsDbTest#testPiesOfMuseumsAndBioregionsCountTheirOwnSpecimens`. |
| B4 | Species per Subfamily pie did not add up to Species (geolocales: species only; projects, bioregions: every rank) | **Fixed** (`PlaceTotals.speciesPieSql`). Test: `inv:place.pies`. |
| B5 | Endemic numbers off by one at each change of place | **Fixed**: the loop, and the number is now the flagged rows for every geolocale and bioregion (also subregions and regions). Test: `inv:place.totals.endemic`, `PlaceTotalsDbTest#testEndemicNumbersAreTheFlaggedRows`. |
| B6 | Museum specimens assigned by substring (`ownedBy like '%CODE%'`) | **Fixed**: the code as a whole word of ownedBy (`MuseumDb.ownedByPattern`). Test: `PlaceTotalsDbTest#testMuseumCodeIsAWholeWordOfOwnedBy`; museum Specimens by `inv:place.totals.specimens`. |
| B7 | Global column stale after the nightly name update | **Fixed**: the nightly name update rebuilds allantwebants when it moved specimens. Test: `inv:global.specimens`, `PlaceTotalsDbTest#testGlobalIsRecountedAfterTheNameUpdateMovedSpecimens`. |
| B8 | Genera, Species (and museum, bioregion Subfamilies) were roll-up sums of row counts | **Fixed**: counted directly from the list (`PlaceTotals`). Test: `inv:place.totals.*`. |
| B9 | API /unimagedGeolocaleTaxa used `image_count > 1` and counted labels | **Fixed**: imaged = a specimen with an image that is not a label (`api.imagedTaxaSql`). Test: `unit:test_api_counts.py`. |
| B10 | Images view header printed the same number twice | **Fixed**: imaged children out of all. Test: `e2e:test_numbers_pages.py::test_images_view_header_counts_imaged_children`. |
| B11 | taxon image_count of genus and above wrong; API /taxa counts with no writer | image_count **fixed** (species added to genera, each rank added to its parent's own images). Test: `inv:taxon.images`. The /taxa subfamilyCount, genusCount, speciesCount, specimenCount: **fixed 2026-10-26** (copied each night from the Global row, `TaxonApiCountDb`; pending after an upload until the next night). Test: `inv:api.taxa`, `NumberChecksTest.testNightlyCopyMakesApiTaxaCountsTheGlobalRow`. |
| B12 | statsPage "valid species + ssp." counted valid taxa of every rank | **Fixed** (`TaxonMgr.getValidSpeciesCount`). Test: `inv:home`, e2e. |
| B13 | Home and statsPage numbers written only after uploads; labels counted; species by name strings | **Fixed**: also written every night (set 4); imaged = images of the ant; species by name with a counted status; statistics.valid_species_imaged counts species (it counted images). Test: `inv:home`. |
| B14 | Upload and group Species count epithets; groups.do "Totals" are maxima | **Fixed** 2026-10-22 (Q8, section 5.2): Species = distinct full names; Totals = all groups' specimens together. Test: `inv:admin.groups`, `NumberDecisionsDbTest#testGroupSpeciesAreFullNamesAndTotalsAreDistinct`. |
| B15 | Counts and the pages they link to use different rules | **Planned**. |
| B16 | Row counts not reset before the crawl (project Images; bioregion AntCat rows; geolocale genus and subfamily rows) | **Fixed**: every count column of a geolocale list, the counts of bioregion AntCat rows, project subfamily_count and project Images reset before the crawl. Test: `inv:list.*`, `cc:images`. |
| B17 | statsPage image table did not add up (NULL caste; Sum of six statuses) | **Fixed**. Test: `PlaceTotalsDbTest#testStatsPageImageTableAddsUp`. |
| B18 | List-of-places column labelled Species shows Valid species | **Fixed** (label "Valid species"; the number itself was B1). Test: e2e. |
| B19 | mapComparison "N X Mapped" counts every child | **Planned**. |
| B20 | Search counts kept in the HTTP session | **Planned**. |
| B21 | "Unimaged" header falls back to the total; label-only taxa count as imaged | **Fixed** 2026-10-22 (Q12, section 5.2): the header shows 0 too; unimaged = no photo of the ant anywhere in AntWeb. Test: `NumberDecisionsDbTest#testUnimagedMeansNoPhotoOfTheAntAnywhere`. |
| B22 | Cached counts (curators at startup; place pages if set 5 fails) | **Planned**. |
| B23 | Upload report error list is static | **Planned**. |
| B24 | Habitat, microhabitat and method lists ordered by another number than the one shown | **Fixed** (ordered by the number shown; 10 entries each). Test: `PlaceTotalsDbTest#testHabitatsAreOrderedByTheNumberShown`. |

Found while doing this (not in the 24):

- **B27** (fixed 2026-10-21): place Specimens larger than the sum of the species rows on every country, adm1,
  region, subregion and project (staging: 110 places, Brazil 20,981 against 20,678). Cause: set 3 renames the
  list rows of synonyms to their current valid name and deletes a synonym row when the valid name already has
  one (`TaxonSetDb.updateTaxonSetTaxonNames`), after set 2 had summed the rows into the total. Now it also runs
  in set 2 before the counts. Projects also had B2 (totals before their list).
- **Ants only** (Brian Fisher, 2026-10-21): specimens of other families stay in AntWeb but are not counted on
  place pages. Subfamilies, Genera, Species, Valid species, Specimens, both pies, Images and Imaged specimens
  of every place count Formicidae only (`PlaceTotals.ANTS`), in the nightly run and in the check.
- **Port of Entry** (decided 2026-10-22, section 5.2): its page showed 37,430 specimens while its list has no species
  with specimens (staging); its list rows counted every AntWeb specimen of its 212 species (213,986), not interceptions.
  Brian Fisher: keep it as a species list of species recorded at ports of entry, without specimen and image numbers.
  The count check skips it with a note (row 119).

- **B25** (investigated 2026-10-22, check `inv:taxon.parents`). The crawl sums the rows of a list by
  `parent_taxon_name` (`CountDb.countGrandChildren`, rank species: species and subspecies rows grouped by parent),
  so a subspecies whose parent is its **species** would replace the species row's Specimens by the sum of its
  subspecies (not add to them: `updateCount` sets the column), and the genus row would not count the subspecies.
  From the code this does not happen to names that come through an upload or the AntCat import:
  `AntwebUpload.saveTaxon` sets every parent from the name (`Taxon.getParentTaxonNameFromName`, the part before the
  first space, so a subspecies' parent is its genus). It can only come from older rows or a hand edit. The check
  compares every species' and subspecies' parent with its genus by name, and counts per place the species rows a
  subspecies under its species would change. Affected places on staging: the check's report (0 expected). Staging
  query: `select count(*) from taxon c join taxon p on p.taxon_name = c.parent_taxon_name where c.taxarank =
  'subspecies' and p.taxarank = 'species'`. Consequence of the current rule (no bug, but a question, section 5
  Q1): a species row's Specimens are only the specimens filed under the species name; its subspecies have rows of
  their own, and the genus row's "N Species" counts species and subspecies.
- **B26** (confirmed in the code 2026-10-22, check `inv:geo.adm1.country`; **fixed** 2026-11-09, Brian Fisher). An
  adm1 took its specimens by name only: `Geolocale.getSpecimenPlaceCriteria` (`s.adm1 = name`; the nightly specimen
  counts of the list rows and so the page's Specimens, and the place downloads), `Overview.getOverviewCriteria` (the
  views and the specimen names of an adm1's lists, `TaxaPage`, `ListView`, `PlaceTaxonNotes`), `PlaceImageCountDb`
  (adm1 source: `s.adm1 = g.name`; Images, Imaged specimens), `PlaceGenusCurationDb` (Not curated and the stored
  specimen names, keyed by the adm1's name), `Geolocale.getLocality` (field guide, species pages in a place),
  `GeolocaleDb.getSpecimenLocaleClause`, and the count check's recount of an adm1 (`CountCheck.Place`). The list itself
  is built per country and adm1 (`GeolocaleTaxonDb`), so only the numbers were wrong: a state of the same name in
  another country (or a specimen with that adm1 and no country) added its specimens to every row of the list it
  shares. Production 2026-11-09 showed the cost too: no index serves `adm1 = X` alone (the index is
  `country_adm1_idx` (country, adm1)), so the nightly Place list totals walked every ant specimen for each state
  (60 minutes on 11-07 and 11-08, about 4 hours on 11-09). Fix: everywhere an adm1's specimens are selected,
  `specimen.country = <the adm1's parent> and specimen.adm1 = <its name>` (`Geolocale.adm1Criteria`,
  `Geolocale.ADM1_CONDITION` with bound parameters; the country matched exactly as the country pages match theirs);
  `place_specimen_name` and `place_genus_curation` key an adm1 by its geolocale id; the API (`/geoSpeciesStats`,
  `/specimens?geolocaleName`) already took the parent since 2026-10-29 and now takes no specimens for an adm1 without a
  parent. Specimens with an adm1 and no country no longer count for any state (Q10, intended). `inv:geo.adm1.country`
  is now a recount: per live adm1, its Specimens = its specimens by country and name (0 differences expected; pending
  until a nightly run has written "adm1 rule of 2026-11-09", `CountInvariants.ADM1_RULE_LOG`, on its "Set 4 done"
  line). Tests: `junit:NumberChecksTest#testAdm1SpecimensAreRecountedByCountryAndName`,
  `junit:PlaceViewTotalDbTest#testAdm1TakesTheSpecimensOfItsOwnCountry`,
  `junit:PlaceViewTotalDbTest#testPlaceQueriesCanUseTheCountryAdm1Index`, `junit:PlaceGenusCurationDbTest#testOtherKindsOfPlaces`,
  `junit:DownloadGuardsDbTest#testIslandAdm1MuseumBioregionProject`,
  `e2e:test_numbers_api_meta.py::test_geo_species_stats_castes_equal_the_specimens` (the adm1 recount in its country).

Found while doing this (2026-10-22):

- **B28** orphanedImages.do "Found N orphaned images" counts the specimen codes of the orphan images (`select
  distinct image_of_id`), not the images. The check gives both (JSON `orphanedImages`: codes, images) and the
  e2e test compares the page with the codes. Label question: section 5 Q9.
- **B29** (fixed, trivial) API `/images`: with no image found, `metaData.specimenCount` was 1 and the list held
  one empty specimen; every specimen but the last was under the key `spefimenCode` (now `api.imagesBySpecimen`); the
  query had no order, so the images of one specimen could be split into several entries (now ordered by code).
  Tests: `unit:test_api_counts.py::test_images_of_nothing_are_no_specimen`,
  `e2e:test_api_numbers.py::test_images_of_an_unknown_specimen_are_none`,
  `e2e:test_api_numbers.py::test_images_meta_counts_and_specimen_image_count`.
- **B30** (fixed, trivial) API `/geoSpeciesStats`: `queenErgatoidCount` and `maleErgatoidCount` both counted every
  ergatoid specimen (no caste condition), so a male ergatoid was also counted as a queen ergatoid. Now each counts
  its caste. The other subcaste counts (alate/dealate, brachypterous, major/soldier, normal, male alate) are
  not restricted to their caste either; with distinct subcaste names that only matters where a specimen has a
  subcaste of another caste. Tests: `unit:test_api_counts.py::test_geo_species_stats_counts_ergatoids_of_their_caste`,
  `e2e:test_api_numbers.py::test_geo_species_stats_castes_add_up`.
  **Decided by Brian Fisher 2026-10-27:** the other subcaste counts count only within their caste too
  (`queenAlateDealateCount`, `queenBrachypterousCount`: queens; `workerMajorSoldierCount`, `workerNormalCount`:
  workers; `maleAlateCount`: males). Live counts, so the change shows as soon as the API is deployed (no nightly).
  Test: `unit:test_api_decisions.py::test_geo_species_stats_counts_subcastes_within_their_caste`.
  Also decided the same day: the API's date and elevation filters (`minDate`, `maxDate`, `minElevation`,
  `maxElevation`, on /specimens and /distinctTaxa) include their bounds, so the records and meta counts (#101) of a
  query with them can grow by the records on a bound. Tests: `unit:test_api_decisions.py::test_elevation_bounds_are_inclusive`,
  `unit:test_api_decisions.py::test_date_bounds_are_inclusive_and_max_date_takes_the_whole_day`,
  `unit:test_api_decisions.py::test_distinct_taxa_uses_the_same_bounds`.
- **B31** statsPage Bioregion Data and Museum Data count valid taxa of every family on the lists (no
  `family = 'formicidae'`), while the place pages count ants only since 2026-10-21. `inv:stats.nonants` reports
  each bioregion and museum whose statsPage numbers include other families (real until decided);
  `inv:stats.bioregions` and `inv:stats.museums` compare the ants among them with the place pages, so they show
  only other differences. **Fixed** 2026-10-22 (Q7, section 5.2): ants only; `inv:stats.nonants` now compares the
  page's numbers with an independent recount of the ant taxa.
- **B32** API `/geolocaleTaxa` and `/bioregionTaxa` name their taxon count `geolocaleCount` and `bioregionCount`
  (label only; the number is the taxa returned).
- **B33** groups.do and group.do "Species" (upload row, UploadDb.getCounts) count distinct species epithets of
  **valid** names only (`getAntwebGenusCriteria` has `status = 'valid'`), and Subfamilies and Genera only AntWeb's
  extant subfamilies and genera (B14 had the epithets). **Fixed** 2026-10-22 (Q8, section 5.2).
- **B34** home "N valid species + ssp." (and statsPage Global Data) count valid taxa of every family, not ants
  only; statsPage "Imaged Species" counts names of any rank and labels (B13 fixed the home number only).
  Question: section 5 Q6. The families part **fixed** 2026-10-22 (Q7, section 5.2); "Imaged Species" per status
  still counts any rank and labels (Q6 plan).

- **B36** (found by `inv:taxon.parents`, 2026-10-22; **fixed** 2026-10-22, revised the same day with Brian Fisher's rule
  on names not identified to genus, section 1) an ant name of a counted status, identified to genus, whose parent is not
  its genus row (or empty), or whose genus has no taxon row: the nightly crawl counts a list's species and subspecies
  rows under their parent (`CountDb.countGrandChildren`), so such a name is missing from its genus row's Species and
  Specimens (and is counted under another genus row if the parent is another genus). Staging preview of 2026-10-22 (the
  first preview, before the rule, had 14 names): (a) 7 names not identified to genus (`(indet)((indet)) (indet)` 226
  specimens, `(indet)(formicinae) (indet)` 4, `(indet)(myrmicinae) (indet)` 6, `(indet)(ponerinae) (indet)` 10,
  `formicinaeindet (indet)` 1, `ponerinaeindet (indet)` 2, `indetindet (indet)` 1): not species, left alone, no genus
  rows (section 1); (b) `(indet)decamorium decem`, `(indet)paltotyreus tarsatus` (1 specimen each): real species whose
  upload file lacked the subfamily, so they were filed under subfamily "(indet)"; their genus rows `(indet)decamorium`,
  `(indet)paltotyreus` do not exist and are not made; a new upload of the file files them under their AntCat genus (see
  Fix 3), and until then they are reported; (c) `(indet)termes (indet)` (Termes is a termite) and `(indet)dont exist
  (indet)` (not a name): reported, and flagged in the upload report from now on; (d) `formicinaewilsonia liaoningensis`
  (valid, 0 specimens): its genus row is missing, an AntCat import problem; Brian fixes it in AntCat; reported, and an
  admin alert; (e) `amblyoponinaeamblyoponine_genus8 nz01`, `ponerinaeponerine_genus1 ba01` (Université Rennes, 1
  specimen each): morphotaxa whose genus row exists, parent empty: repaired.

  Causes in `AntwebUpload.saveTaxon`: the parent was left empty when `TaxonMgr` (read before the upload or the AntCat
  import) did not know the genus (a genus new in tonight's AntCat import, such as Holcoponera or Mycetomoellerius; a
  morphogenus). Fix: (1) uploads set the parent of a name identified to genus to its genus by name (a name not
  identified to genus gets a parent only when it is a known genus, as before, and no genus row is made for it); a genus
  row is made only when its subfamily can be read from its name, as before (no placeholder genus rows); (2) **every
  night** in set 1, after the AntCat import, the TaxonWorks upload and the name update, before sets 2 to 5 build the
  lists, `TaxonParentDb.repair` (called by `UtilDataAction.nightlyTaxonParents`) sets the parent of each such name to its
  genus row **only where that genus row exists**; it never makes a genus row. Names whose genus row is missing are
  listed in its compute.log line ("taxon parents (B36): N parents set to the genus; M names whose genus has no taxon row,
  not changed (K valid: AntCat import problem; others: subfamily missing in the upload, or not an ant), e.g. ...", with
  the AntCat genus row of the same genus when there is one), and valid ones in an admin alert ("Valid ant names whose
  genus row is missing (AntCat import problem)", at most once a week). When it changed a row it recounts the AntCat list
  (worldants, counted by the import before this step) and rebuilds the Global column (allantwebants), as the name update
  does. Only ant species and subspecies of a counted status that are their own current valid name are touched: synonyms
  and other names keep the parent of their valid name (by design). Names whose genus name is a taxon row of another rank
  are left alone and stay in the check. This also repairs B25 (a subspecies whose parent is its species) if one appears.
  (3) Uploads: when the subfamily is missing or "(indet)" and the genus is an AntCat genus, the line takes the genus's
  subfamily (`SpecimenUploadParse.subfamilyFromGenus`: `(indet)decamorium decem` becomes `myrmicinaedecamorium decem`);
  a line given as an ant (family formicidae or none) whose genus is not an ant genus in AntCat (Termes) or is not a name
  is kept and listed in the upload report under "Genus not in AntCat (not an ant?)" (`MessageMgr.genusNotInAntcat`), not
  refused. The check (`inv:taxon.parents`) leaves out names not identified to genus and says for each finding whether
  the genus row is missing (AntCat import problem for a valid name; else subfamily missing in the upload, or not an
  ant). Tests: `TaxonTreeCountDbTest#testRepairSetsParentsOnlyToExistingGenusRows`,
  `#testNoAlertWithoutAValidNameMissingItsGenus`; `SubfamilyFromGenusTest` (5 tests). (4) A species column
  that holds an AntCat genus name (genus "Pyramica", species "strumigenys", Marcio Pie Ant Lab 2019) is loaded as given
  and listed under "Species column holds a genus name" (`MessageMgr.speciesIsGenusName`, 2026-11-07), only when AntCat
  does not know the combination (Atta cephalotes and Solenopsis azteca are real species). (5) A non-ant line (family not Formicidae)
  without a subfamily is filed under its family in parentheses, "(scarabaeidae)cremastocheilus (indet)", or the higher
  group given as family, "(coleoptera)(coleoptera) (indet)"; never under the ant placeholder "(formicidae)". A species
  that repeats the group in parentheses, or "_indet_", is "(indet)" (2026-11-08, `nonAntSubfamilyPlaceholder`,
  `nonAntIndetSpecies`; existing records: `db/upgrade/9.3.1/2026-11-08-nonant-family-placeholder.sql`).

  One-off repair (Brian runs it; staging first: `ssh root@165.232.136.170`, then production: `ssh root@165.232.137.86`
  with `F=/root/antweb/etc/db.properties` in each block). Nightly set 1 with this code does the same every night; the
  blocks below do it now, without waiting for set 1. Each block is one line.

  1. Preview (read-only): the names the nightly repair looks at. Staging: expect 7 rows: the 2 Rennes morphotaxa with
     `genus_row_rank` = genus (repaired by block 3), and 5 with `genus_row_rank` empty (no genus row: reported, not
     repaired: `(indet)decamorium decem`, `(indet)dont exist (indet)`, `(indet)paltotyreus tarsatus`, `(indet)termes
     (indet)`, `formicinaewilsonia liaoningensis`). The 7 names not identified to genus are not listed.

     ```
     F=/root/antweb-next/etc/db.properties; docker exec -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysql -u "$(sed -n 's/^user=//p' "$F")" ant -e "select c.taxon_name, c.taxarank, c.status, c.parent_taxon_name, substring_index(c.taxon_name, ' ', 1) genus_by_name, g.taxarank genus_row_rank from taxon c left join taxon g on g.taxon_name = substring_index(c.taxon_name, ' ', 1) where c.family = 'formicidae' and c.taxarank in ('species', 'subspecies') and c.taxon_name like '% %' and c.status in ('valid', 'unrecognized', 'morphotaxon', 'indetermined', 'unidentifiable') and (c.current_valid_name is null or c.current_valid_name = '' or c.current_valid_name = c.taxon_name) and (coalesce(c.genus, '') not like '(%' and lower(coalesce(c.genus, '')) not in ('indet', 'undet')) and (c.parent_taxon_name is null or c.parent_taxon_name <> substring_index(c.taxon_name, ' ', 1) or g.taxon_name is null) order by c.taxon_name"
     ```

  2. Back up exactly the rows the repair changes (those whose genus row exists) to a dated table (fails if the table
     exists, so it cannot run twice by mistake); prints them and their number. Staging: expect 2.

     ```
     F=/root/antweb-next/etc/db.properties; docker exec -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysql -u "$(sed -n 's/^user=//p' "$F")" ant -e "create table taxon_parent_bak_20261022 as select c.* from taxon c join taxon g on g.taxon_name = substring_index(c.taxon_name, ' ', 1) and g.taxarank = 'genus' where c.family = 'formicidae' and c.taxarank in ('species', 'subspecies') and c.taxon_name like '% %' and c.status in ('valid', 'unrecognized', 'morphotaxon', 'indetermined', 'unidentifiable') and (c.current_valid_name is null or c.current_valid_name = '' or c.current_valid_name = c.taxon_name) and (coalesce(c.genus, '') not like '(%' and lower(coalesce(c.genus, '')) not in ('indet', 'undet')) and (c.parent_taxon_name is null or c.parent_taxon_name <> g.taxon_name); select taxon_name, parent_taxon_name from taxon_parent_bak_20261022; select count(*) backed_up from taxon_parent_bak_20261022"
     ```

  3. Repair: only the rows in the backup table, in one transaction; sets their parent to their genus row; prints the
     parents set (must equal block 2's number).

     ```
     F=/root/antweb-next/etc/db.properties; docker exec -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysql -u "$(sed -n 's/^user=//p' "$F")" ant -e "start transaction; update taxon c join taxon_parent_bak_20261022 b on b.taxon_name = c.taxon_name join taxon g on g.taxon_name = substring_index(c.taxon_name, ' ', 1) and g.taxarank = 'genus' set c.parent_taxon_name = g.taxon_name; select row_count() parents_set; commit"
     ```

  4. Check: run the preview again (expect only the rows with `genus_row_rank` empty, 5 on staging).

  Rollback (puts every parent back as backed up):

  ```
  F=/root/antweb-next/etc/db.properties; docker exec -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysql -u "$(sed -n 's/^user=//p' "$F")" ant -e "start transaction; update taxon c join taxon_parent_bak_20261022 b on b.taxon_name = c.taxon_name set c.parent_taxon_name = b.parent_taxon_name; select row_count() parents_restored; commit"
  ```

  Then the nightly sets 2, 3 and 4 (or the next nightly run) rebuild and count the lists with the new parents; the
  application's name cache (`TaxonMgr`) reads them at the next set 1 or restart. The AntCat list's Species and Specimens
  (worldants, counted only in set 1) and the Global column get them at the next set 1 with this code. Drop the backup
  table after a week: `drop table taxon_parent_bak_20261022`.

- **B37** (found and fixed 2026-10-22, confirmed on staging by Brian Fisher) Genus rows of country and adm1 lists one too
  high (US formicinaeformica 104 with `formicinaeformica fuliginothorax`, status unavailable; Czech Republic
  formicinaecamponotus 8 with the subspecies `formicinaecamponotus ligniperda afer`, unavailable; the subfamily rows,
  287 and 57, were right). Cause: nightly set 2 ran `geolocaleTaxonFix` (`GeolocaleTaxonDb.geolocaleTaxonFix`) after
  the count; for every genus row of every country and live adm1 it set Species to the number of list rows whose taxon
  has the genus as parent, of any status and rank (`getGeolocaleTaxonCount`). The count
  (`GeolocaleTaxonCountDb.childrenCountCrawl`, from `geolocaleCountCrawl`) covers the same places (valid countries,
  live valid adm1s; the job also touched countries marked invalid, which the count skips) and writes every genus row by the
  list rule (species and subspecies of a counted status) after resetting it, so the job did nothing else. Fix:
  removed from set 2; the admin job (`utilData.do?action=geolocaleTaxonFix`) counts by the list rule. Checks:
  `inv:list.genus`, `cc:species`.
- **B38** (found and fixed 2026-10-22, confirmed on staging) Museum and bioregion lists counted before their synonym
  rows were renamed. Set 3 built and counted each museum's and bioregion's list and wrote its totals
  (`MuseumDb.populate`, `BioregionDb.populate`), and only afterwards renamed rows of synonyms and other names with a
  current valid name to that name (`updateTaxonSets`); the renamed rows kept their old counts, and Species did not count
  them (museum AFRC: 14 valid species rows with no AFRC specimen under that exact name, such as ponerinaeboltonopone
  ilgii, 52 specimens filed under an older combination; ABS 1; CELC 4 species and 1 genus). Projects too were counted
  before that rename (set 2 renamed them already). Brian Fisher: a specimen filed under a junior synonym belongs to the
  valid species. Fix: each museum, bioregion and curated project list is renamed right after it is built and before its
  higher taxa, counts and totals (`TaxonSetDb.renameToValid`, in `MuseumDb.populate`, `BioregionDb.populate`,
  `ProjTaxonCountDb.recount`); a synonym whose valid name is on the list already is merged into it (one row); the
  specimen counts of museum, bioregion and project rows count a synonym's specimens under the valid name
  (`PlaceTotals.specimensByListNameSql`, used by `Museum`, `Bioregion`, `Project.getCountSpecimensQuery` and by the
  count check's recount); bioregion endemism runs after the rename. Set 3 no longer renames after the counts (it
  still updates the list sources). worldants and allantwebants (Global) keep every name as it is. `inv:list.global`
  compares such a row with the Global specimens of the name and of its synonyms. Geolocale lists are renamed before
  their counts (set 2, B27), but their specimen counts are still by the exact name: a specimen filed under a synonym is
  not counted on a country page (open; same rule could be applied there).
- **B39** (decided 2026-10-22) Curator "Description Edits": the site counted description_edit and description_hist
  (every saved version of taxon texts), the check description_edit only. Brian Fisher: place and project page texts
  count too. Site (`LoginDb.setDescEditCounts`) and check (`NumberChecks.adminCurators`) now count description_edit,
  description_hist, object_edit and object_hist (`LoginDb.PAGE_TEXT_EDIT_TABLES`); curators.do and curator.do say so.

- **B40** (found by `inv:list.images`, 2026-10-22; **fixed** 2026-10-22) Images of a list's genus row lower than the sum of
  the rows under it: staging worldants `ectatomminaeholcoponera` 596, its rows 604; `myrmicinaemycetomoellerius` 337 (345);
  `myrmicinaeallomerus` 394 (401); 9 rows, all genera with recent name changes (Gnamptogenys names now in Holcoponera,
  Trachymyrmex names in Mycetomoellerius, Wasmannia in Allomerus). Cause: set 4's image crawl (`CountDb.imageCountCrawl`,
  for every geolocale, project, museum and bioregion list) wrote each species row from the specimens filed under the name,
  but added the genus, subfamily and family rows up from the specimens' own columns (genus row = `specimen.subfamily` +
  `specimen.genus`). A synonym or obsolete combination takes the parent of its current valid name (Wasmannia auropunctata
  under Allomerus), so the images of specimens still filed under it went to the old genus row (Wasmannia) and not to the
  genus whose row holds the synonym's row. The specimen counts of the lists already roll up by the taxon tree
  (`parent_taxon_name`), so a genus row's Specimens and Images followed two different rules. Fix: the higher rows are added
  up by the taxon tree, the rule of the specimen counts and of the check (a species row still counts only the specimens
  filed under its own name; labels count as image files, as the column says; the lists of places count ants only as
  before). So a genus, subfamily or family row holds the images of every row under it by construction. A name that is not
  in the taxon table falls back to the specimen's columns as before. The check was right. Test:
  `TaxonTreeCountDbTest#testGenusRowHoldsTheImagesOfASynonymUnderIt` (fails with the old crawl: Pheidole 3, its rows 6),
  `#testCountryImagesAreUnchanged`. Seen on staging after nightly set 4.

Found while doing this (2026-10-26, end-to-end tests of rows 24 to 53; each has an xfail test until fixed):

- **B41** a country or adm1 without a bioregion: nightly `GeolocaleDb.calcIntroducedGeolocales` flags every name of the
  introduced-species list on its list that has a native-bioregion map as introduced, because
  `TaxonPropMgr.isIntroduced(name, null)` is true (`isMapped(map, null)` is false). The page says "not native to this
  place's bioregion", and its species list marks none of them Introduced (`TaxonPropMgr.isBioregionNative` treats no
  bioregion as native). Test: `e2e:test_numbers_endemic.py::test_a_place_without_a_bioregion_has_no_introduced_species`.
  Fixed 2026-11-02 (Brian Fisher: no bioregion = not introduced, everywhere): one rule, `TaxonPropMgr.isIntroducedIn`
  (no, blank or unknown bioregion: not introduced; a bioregion the map does not mark native: introduced), used by the
  upload and name update (`specimen.is_introduced`), the nightly flags and the species list's mark and sort. The stored
  flags and numbers change at the next nightly run (set 2); the xfail stays until then. Test:
  `junit:AuditTlFixesTest#testNoBioregionIsNotIntroduced`.
- **B42** browse.do of a species with more specimens than the limit says "(Only the first k of N displayed)" and lists
  k + 1 specimens: `specimenReport.jsp` shows rows with index 0 to limit (`index <= limit`, index starts at 0); with the
  default limit, 2,001 of a species with more than 2,000 specimens. Fixed 2026-11-02 (showBrowse-body.jsp and
  collection-body.jsp pass the last index, limit - 1). Test:
  `e2e:test_numbers_lists.py::test_browse_limit_shows_that_many_specimens`.
- **B43** the field guide shows the habitat and method summaries but not the microhabitat one (row 53 says the same
  three summaries): `FieldGuide.setSpecimenData` calls `setHabitats` and `setMethods`, not `setMicrohabitats`. Test:
  `e2e:test_numbers_species.py::test_field_guide_shows_the_microhabitat_summary`.
- **B44** (fixed 2026-10-27) the nightly recounted only live adm1s (`GeolocaleDb.updateAdm1Counts`:
  `if (geolocale.isLive())`), and the count check checked only live adm1s; an adm1 that is not live kept the numbers
  of whenever it was last recounted next to a current list (production 10-27: Castile-Leon, Spain, is_live 0, showed
  0 subfamilies, 5 species and 39 specimens; its list has 2 subfamilies and 6 species; a recount on staging gave 2, 6
  and 15). Now every valid adm1 is recounted each night (the compute.log line "Adm1s populated" gives how many and
  the time). The count check still checks live adm1s only: with every adm1 in scope, staging showed about 25,000
  differences on the 5,144 adm1s not yet recounted (Valle del Cauca, Colombia: Genera 27 shown, 66 listed); it is widened
  after the first nightly run with this fix. Tests:
  `e2e:test_numbers_lists.py::test_list_headers_equal_place_numbers_of_every_kind`, `tests/unit/test_small_fixes.py` (8).
- **B65** (fixed 2026-10-27) set 2's clean-up (`dataCleanup`: morphospecies without specimens, names no longer in the
  taxon table) deletes rows of every project list, the Global column (allantwebants) too, after the specimen upload has
  counted it; nothing recounted it, so its genus and subfamily rows counted rows they no longer had (production 10-27:
  Genera 577 against 575, Leptogenys 631 species against 623 rows, 15 genera). Until 10-26 the count check called these
  pending (it took the nightly's own changes as after the last count; #284 changed that). Now set 2 recounts the Global
  column after the clean-up (`UtilDataAction.recountGlobal`, a compute.log line). Test: `tests/unit/test_small_fixes.py` (9);
  the count check's list.genus and place.totals.genus of allantwebants.
- **B66** (check fixed 2026-10-27) list.global bounded a museum, bioregion or project row by the Global rows of the name
  and its synonyms, but synonyms have no Global row (the Global column lists counted statuses only), so a row holding a
  synonym's specimens showed as more than Global (production 10-27: Diacamma solitaria 5 against 1, Carebara diversa 42
  against 34, 12 rows). The bound is now the specimen records of the name and its synonyms. Test:
  `tests/unit/test_small_fixes.py` (9).
- **B67** (decided by Brian Fisher 2026-10-27, Q1 of the triage; **superseded 2026-10-30**: Subfamilies count valid
  subfamilies only on every kind of place, curated projects too, `PlaceTotals.countsAsSubfamily`, section 5 "Genera and
  subfamilies rule"; `listedCondition` is now `rankCondition` for every place) a curated project's Subfamilies number counted its
  subfamily rows of a counted status, while the nightly run counts the rows of its list in every status (the list's
  genus_count, `StatusSet.getAndCriteria(project)` empty) and the count check's `checkChildren` does the same. Decision:
  the project Subfamilies number follows the same status rule as the project's list rows. Now every subfamily row of
  an ant subfamily counts for a curated project (`PlaceTotals.listedCondition`, used by the nightly run, the count
  check's `checkSubfamilyTotal` and `inv:place.totals.subfamily`); worldants and allantwebants keep the counted statuses.
  The stored numbers change at the next nightly run (set 3). Tests:
  `junit:CountRules20261027Test#testB67SupersededCuratedProjectSubfamiliesTakeThePlaceRule` (now: the same rule as every place),
  `e2e:test_numbers_lists.py::test_list_headers_equal_place_numbers_of_every_kind` (a curated project: the header with
  counted statuses <= Subfamilies <= the complete list's header).
- **B68** (decided by Brian Fisher 2026-10-27, Q2) the orphan genus and subfamily report and their deletes
  (`OrphansDb`) used `taxon_name not in (select parent_taxon_name from taxon)`; one taxon without a parent makes that
  false for every row, so the report showed nothing and the deletes deleted nothing. The Orphan Manager pages were
  removed on 2026-10-18, so the report is now two read-only named queries of the Query Manager, `orphanGenera` and
  `orphanSubfamilies` (`not exists`, with each name's specimens and description edits), and `OrphansDb` uses the same
  condition. The deletes stay off (`OrphansDb.ORPHAN_GENUS_SUBFAMILY_DELETES_ON = false`; the query pages say so) until
  Brian Fisher and Claude have reviewed the list on staging. Tests:
  `junit:CountRules20261027Test#testOrphanReportIsNullSafeAndTheDeletesAreOff`, `QueriesReviewTest`,
  `tests/e2e/test_named_queries.py` (runs both on staging).
- **B69** (decided by Brian Fisher 2026-10-27) caste from the Life Stage field (`Caste.getCasteValues`): "alate male"
  was a queen (the queen test matched "late" in "alate"), and "and" or "/" split the field only when a comma followed,
  so "male and worker" was a worker. Now a note that names a male is never a queen,
  and the first ant is the text before the first comma, " and ", "&" or "/". Specimens get the new caste at their next
  upload (CAS: the nightly TaxonWorks upload) or when an admin runs `utilData.do?action=calcCaste`; the caste counts
  (statsPage images table, API `/geoSpeciesStats`, caste filters) follow at the next nightly run. Tests:
  `junit:CountRules20261027Test#testAlateMaleIsAMale`, `#testAndAndSlashSplitWithoutAComma`.
- **B70** (decided by Brian Fisher 2026-10-27) endemic.do and introduced.do listed flagged rows of any status
  (endemic.do's status set was "complete"), and the Endemic and Introduced numbers counted every flagged row. Decision:
  the lists use the same status rule as the counts, no synonyms. Now lists, numbers and the count check
  (`inv:place.totals.endemic`, `inv:place.totals.introduced`) count flagged names with a counted status
  (`PlaceTotals.flaggedCondition`). The lists change at once, the stored numbers at the next nightly run (geolocales set
  2, bioregions set 3). Test: `junit:CountRules20261027Test#testEndemicAndIntroducedListsAndNumbersShareTheStatusRule`.
  2026-10-28 (Brian Fisher): Endemic takes valid names only (`PlaceTotals.ENDEMIC_STATUSES`; #24, Q3); Introduced keeps
  the counted statuses. Test: `junit:CountRules20261028Test#testEndemicCountsValidNamesOnly`.
- **B71** (decided by Brian Fisher 2026-10-27; Q12: labels never count) a specimen with only label photos counted as
  imaged on species pages and browse pages ("(M imaged)", rows without the no-photos mark), unlike the unimaged lists.
  Now `Specimen.getHasImages` needs a photo of the ant; the specimen's own image count still counts every photo. Tests:
  `junit:CountRules20261027Test#testASpecimenWithOnlyLabelPhotosIsNotImaged`,
  `e2e:test_numbers_lists.py::test_species_browse_imaged_count_equals_the_specimens_with_images`.
- **B72** (decided by Brian Fisher 2026-10-27) the habitat, microhabitat and method summaries counted distinct
  collection codes, so specimens without a collection code were not counted (and an empty code was one event). Now
  each such specimen is a collection event of its own (`Species.COLLECTION_EVENTS`). Live, no nightly run needed. Tests:
  `junit:CountRules20261027Test#testSpecimensWithoutACollectionCodeAreCollectionEventsOfTheirOwn`,
  `junit:PlaceTotalsDbTest#testHabitatsAreOrderedByTheNumberShown`,
  `e2e:test_numbers_species.py::test_species_page_habitat_summaries_equal_a_recount_of_the_specimens` (its recount
  counts them the same way).
- **B73** (decided by Brian Fisher 2026-10-27) statsPage.do said "Antweb's 21 collaborating institutions", fixed text.
  Now the number is the museums of the Museums list (`MuseumMgr.getMuseumCount`, museums.do), with a link to it. Tests:
  `junit:CountRules20261027Test#testInstitutionsAreTheMuseumsListed`,
  `e2e:test_home_and_stats.py::test_statistics_page_institutions_are_the_museums_listed`.
- **B74** (fixed 2026-10-27) imageUploadReport.do of every batch whose copyright year is not the newest row of the
  copyright table showed the error page, so it had no "Images" number (staging 10-27: batches 1998 to 2000).
  `CopyrightDb.getCopyrights` made a new list for each row it read, so only the last row was kept and every other
  year's copyright was null; `imageUploadReport-body.jsp` then failed on `getCopyright().getCopyright()`. Now one
  list of every row, and the report shows an empty copyright when a batch has none (a year before 2000). Tests:
  `e2e:test_numbers_uploads.py::test_image_upload_report_counts_equal_their_files`, `tests/unit/test_small_fixes.py` (10).
- **B75** (fixed 2026-10-27) Compare of a search by species (`prepareCompareResults.do?resultRank=species`) failed
  whenever the search had a species: `PrepareCompareResultsAction.getExtraSpeciesList` asked each name
  `Taxon.setHasImages(connection)`, which has no place to count from and threw a NullPointerException. Now the
  species with a photo of the ant anywhere in AntWeb (`StatisticsDb.antImagedNames`, the Q12 rule, one query for all
  names). And when a Compare or Map preparation of a search by species leaves no species, the page still prints its
  count ("0 imaged species", "0 georeferenced species (from 0 specimens)") above "Sorry, nothing matches your
  request." (it printed no count; staging 10-27, Solenopsis of Bosnia and Herzegovina). Tests:
  `e2e:test_numbers_search.py::test_compare_and_map_preparations_count_what_they_list`, `tests/unit/test_small_fixes.py` (10).
- **B76** (fixed 2026-11-03) the admin Subtaxon total of a list is below the place's number (staging 10-27: region
  Americas rank genus 4952 against Species 4953; bioregion Afrotropical rank subfamily 121 against Genera 130 and rank
  genus 7179 against 7183; project floridakeysants rank genus 90 against 92). Each row's number counts its child rows
  by `taxon.parent_taxon_name` (`CountDb.countChildren`), and the rows add up to the total, but the place's number
  counts every counted row of the rank (`PlaceTotals.rankCondition`). So a counted name with no parent (B36: names
  whose genus row does not exist are not repaired), or whose parent row is not a counted row of the place's list (for
  example an AntCat genus row of a bioregion without its subfamily row), is in the place's number and under no row.
  Decided by Brian Fisher (2026-11-03): "missing parent rows: add them". The nightly count of every list (geolocales in
  set 2; projects, museums, bioregions in set 3; also the Global and AntCat lists) first adds the genus row of every
  counted species and subspecies (`rankCondition` SPECIES) and then the subfamily row of every counted genus
  (`rankCondition` GENUS, the new genus rows included) that the list lacks (`home.ParentRows`, called by
  `CountDb.childrenCountCrawl`): a parent of rank genus or subfamily in the taxon table, an ant, of a counted status, not a
  placeholder; never a parent a curator removed from the list (`geolocale_taxon_dispute`, `proj_taxon_dispute`). The rows
  follow the conventions of the derived rows: source "proxy" + the best source of the children (`Source.aTrumpsB`; museum
  lists have no source), `insert_method` "parentRow". The place's Subfamilies and Genera can go up by those rows (the
  subfamily or genus is in the place through its names). Names whose parent is a placeholder, of another status, or not
  in the taxon table (B36) stay under no row. The "Set 3 done" line of compute.log says "missing parent rows added
  (2026-11-03)" (`ParentRows.LOG`). The test compares strictly again: in the view Curated, rank subfamily = the header of
  the genus list and rank genus = the header of the species list, the rows whose parent row is not on the list apart;
  such a row fails when its parent is a counted genus or subfamily (pending until the first nightly run with the line),
  and is an expected failure (`pytest.xfail`, B36) only when its parent is not. Tests:
  `junit:ParentRowsDbTest` (4), `e2e:test_numbers_search.py::test_subtaxon_total_is_the_sum_of_the_rows_of_every_kind_of_place`,
  `unit:test_rules_pending.py::test_parent_rows_are_pending_until_the_nightly_run`. To see what a place still has under
  no row (bioregion; the same with geolocale_taxon, proj_taxon):
  `select c.taxon_name, t.parent_taxon_name from bioregion_taxon c join taxon t on t.taxon_name = c.taxon_name where
  c.bioregion_name = 'Afrotropical' and t.taxarank = 'genus' and t.status in (counted) and t.family = 'formicidae' and
  not exists (select 1 from bioregion_taxon p join taxon pt on pt.taxon_name = p.taxon_name where p.bioregion_name =
  c.bioregion_name and p.taxon_name = t.parent_taxon_name and pt.status in (counted))`.
- **B77** (fixed 2026-11-03) a bioregion's subfamily browse page says more genera than the subfamily's row on the
  bioregion's list (Palearctic Ponerinae: browse.do "18 Genera (14 imaged)", list row "16 Genera"; the same on
  production, so not caused by PR #291). Since 2026-11-02 (#316) the browse page of a subfamily in a place counts and
  lists the genus rows of the chosen view (`ListView.GenusRows`); what remained on production: the subfamily's row on
  the place's subfamily list showed the stored Genera of the nightly count (Palearctic Ponerinae 14) while its browse
  page in the default view Curated listed 15 (the curated morphogenus Ponerine_genus2). Decided by Brian Fisher
  (2026-11-03): "browse header counts the view's rows". A subfamily row's Genera on a place's subfamily list (and on the
  family's browse page in a place) now follow the view, as a genus row's Species do: the genus rows the subfamily's
  browse page shows in the same view (the list's genus rows with the view's criteria, `ListView.subfamilyGenusRowsSql`,
  kept by `GenusRows.keep` in Valid and Curated), counted per subfamily for the whole page (`ListView.SubfamilyRows`,
  `loadSubfamilyRows`: the view's names, the evidence, the genus rows; none per row), linked to the browse page in the
  same view ("No Genera" when none). The admin column total of a subfamily list is then that view's sum and should
  equal the header of the genus list in the same view (B76). The AntCat list and Fossil Ants keep their own sets and the
  stored numbers. The stored genus_count stays (count check `inv:list.subfamily`, the API). Tests:
  `junit:PlaceTaxonNotesDbTest#testSubfamilyRowsCountTheGenusRowsOfTheirBrowsePage`,
  `e2e:test_numbers_lists.py::test_browse_headers_equal_the_list_rows` (the day's subfamily row in Valid, Curated and All
  specimen names, strict; the expected failure is gone).
- **B78** (fixed 2026-10-29) the AntCat list (worldants) is counted by the AntCat import, the first step of set 1;
  the TaxonWorks upload and the name update that follow it in set 1 file specimens under other names, and nothing
  recounted worldants (sets 2 to 5 skip it), so its species rows kept the specimens counted before (production 10-29,
  list.global, 7 species: Camponotus buchneri 40 against 33 for the name and its synonyms, C. dufouri,
  C. immaculatus, C. repens, Technomyrmex fisheri, Plagiolepis madecassa, Hypoponera punctatissima 1 to 3 over).
  worldants still counts each row by the very name (it lists synonyms as rows of their own, so a synonym's specimens
  stay on the synonym's row; `PlaceTotals.synonymsToValid`); the check's bound (the name plus its synonyms) is right.
  Now set 2 recounts worldants after the clean-up, next to the Global column (`UtilDataAction.recountAntCatList`, a
  compute.log line). Tests: `junit:PlaceTotalsDbTest#testAntCatListIsRecountedAfterSpecimensMoved`,
  `tests/unit/test_small_fixes.py` (9).
- Not a number, for the record: the recent images test expected 100 rows on the first page; the page lists 50 per
  page (`recentSearchResults-body.jsp`, parameter `length`; `numToShow` is not the page size). The test now reads the
  page's own "Showing records a to b".
- Open (2026-10-27, to look at after B65 is live): fossilants Genera 259 against 258 and four subfamily rows one over
  (the project is recounted in set 3 after the clean-up, so B65 does not explain it); statistics.do latest row proj_taxa
  69257 against 69251; the atolants images row "Paráponera clavata" (89 against 0), a name with an accent.
  fossilants, looked at 2026-11-03 from the code (no database reachable; to confirm on production, read-only). Not a
  fossil filter: the place totals count fossil and extant names alike (`PlaceTotals.rankCondition` has no `fossil`
  condition; Q5's `fossil = 0` was proposed, never made), and fossilants is counted like any live project (set 3,
  `ProjTaxonCountDb.recount`; its own status sets only change which rows its pages show: "Valid (with fossils)", status
  valid, so a morphogenus row counted by Genera and by its subfamily row's genus_count is not listed). Two causes fit
  the code: (1) a counted genus (valid or morphotaxon, not a placeholder) whose parent is not a valid subfamily row on
  the list (B76; for example a genus incertae sedis whose parent is `formicidae` or the subfamily `incertae_sedis` of
  another status): in Genera, under no row; the parent rows of 2026-11-03 fix it when the parent is a counted subfamily;
  (2) set 5's `verifyProjTaxon` deletes the genus rows of every project (not worldants) that have no child row on the
  list, after set 3 has counted the project's Genera and its subfamily rows: until the next night Genera and those
  subfamily rows are one over per deleted genus, and the count check calls it real (the rows were deleted before the end
  of the run). Queries (read-only):
  `select t.taxon_name, t.status, t.fossil, t.parent_taxon_name, pt.taxarank, pt.status, (select count(*) from proj_taxon x
  where x.project_name = 'fossilants' and x.taxon_name = t.parent_taxon_name) parent_on_list from proj_taxon l join taxon t
  on t.taxon_name = l.taxon_name left join taxon pt on pt.taxon_name = t.parent_taxon_name where l.project_name =
  'fossilants' and t.taxarank = 'genus' and t.status in ('valid', 'morphotaxon') and t.family = 'formicidae' and
  t.taxon_name not like '%(%' and t.taxon_name not like '%)%' and not (pt.taxarank = 'subfamily' and pt.status = 'valid'
  and exists (select 1 from proj_taxon x where x.project_name = 'fossilants' and x.taxon_name = t.parent_taxon_name))`
  (cause 1: the genera under no counted subfamily row);
  `select l.taxon_name, l.genus_count, (select count(*) from proj_taxon c join taxon ct on ct.taxon_name = c.taxon_name
  where c.project_name = 'fossilants' and ct.taxarank = 'genus' and ct.parent_taxon_name = l.taxon_name and ct.status in
  ('valid', 'morphotaxon') and ct.taxon_name not like '%(%' and ct.taxon_name not like '%)%') live, (select count(*) from
  proj_taxon c join taxon ct on ct.taxon_name = c.taxon_name where c.project_name = 'fossilants' and ct.taxarank = 'genus'
  and ct.subfamily = l.taxon_name and ct.status = 'valid') listed_valid from proj_taxon l join taxon t on t.taxon_name =
  l.taxon_name where l.project_name = 'fossilants' and t.taxarank = 'subfamily' having l.genus_count <> live or
  l.genus_count <> listed_valid` (the four subfamily rows: stored against the live rows, and against the valid genera
  the page lists);
  `select l.taxon_name, t.status, l.source, l.created from proj_taxon l join taxon t on t.taxon_name = l.taxon_name where
  l.project_name = 'fossilants' and t.taxarank = 'genus' and not exists (select 1 from proj_taxon c join taxon ct on
  ct.taxon_name = c.taxon_name where c.project_name = 'fossilants' and ct.parent_taxon_name = l.taxon_name)` (cause 2:
  the genus rows set 5 deletes tonight; run it after set 3 and again after set 5, and compare
  `select genus_count from project where project_name = 'fossilants'` with the first query's count of genera).
  Confirmed on production 2026-11-03 16:50 (Brian ran the three queries, tools/review/fossilants-genus-count.sql): stored
  Genera 257; cause 1 holds: the 15 incertae sedis fossil genera and the morphogenus ponerinaeponerine_genus2 have no
  parent (subfamily row Incertae sedis: genus_count 0, 15 listed); Myrmicinae 70 stored against 69 (recheck after the
  next nightly); cause 2 found nothing. B78 (fixed 2026-11-03): the nightly parent repair (TaxonParentDb, set 1) gives a
  genus without parent its subfamily row (test TaxonTreeCountDbTest.testGenusWithoutParentGetsItsSubfamily). Open for
  Brian: Genera counts the morphogenus but the Fossil ants list (status valid) did not show it. Brian 16:55: (a), the
  list shows morphotaxa too (StatusSet Valid (with fossils): status valid or morphotaxon; StatusChoiceTest), one
  counting rule everywhere.
  Code left as it is until the cause is confirmed (cause 2). Seen in the code on the way: `ProjTaxonCountDb.fixExceptions` gives
  fossilants rows that are not valid the source `curator`, a source that makes a morphospecies curated everywhere in the
  Curated view (`ListView.vettedCondition` leaves out worldants and allantwebants only), to look at with Brian.
  2026-11-11 (Brian Fisher, production: Fossil Ants Genera 259, its list "258 Valid (with fossils) Genera"; "Valid
  species / subspecies 3" opening a list of 868). From the code: (a) the page's Subfamilies, Genera and Species were not
  the list totals of `PlaceViewTotalDb`: it counted the projects of scope PROJECT only (`ProjectMgr.getAntProjects`), and
  Fossil Ants and the AntCat list are GLOBAL, so their pages showed `project.genus_count` and `species_count` (set 3);
  (b) Genera one over is cause 2 after all: the stored rule's rows are a subset of the list's rows at any one moment
  (same statuses, the list has no placeholder condition), so a stored number above the list means the list lost a row
  after the count. Every day the AntCat import puts each fossil name on the list (`SpeciesListUpload`, "IF FOSSIL"), a
  fossil genus with no species on the list among them; set 3 counts it; set 5's `verifyProjTaxon` deletes it. The query
  of 2026-11-03 for cause 2 was run in the afternoon, after set 5, so it found nothing; (c) the Valid line links
  `statusSet=valid`, which the two lists with their own views read as their default, Valid (with fossils)
  (`StatusSet.ownChoice`), while the number is `valid_species_count` (valid, not fossil). Fixed 2026-11-11: the daily
  update counts the site-wide lists' totals too (`PlaceViewTotalDb.countedProjects`; not allantwebants), in their own
  default view; set 5's `verifyProjTaxon` and `cleanupSpeciesListProxyRecords` count a project's totals again when they
  take a row off its list (`ProjTaxonDb.getProjectsWithRowsRemoved`, `PlaceViewTotalDb.recountProjects`); the page's
  lines follow one rule on every place page ("The rule of the place page's list numbers", section 5). The first form
  of that fix showed the species total of "Valid (with fossils)" on both species lines; read from production the same
  day, that view has 868 rows on Fossil Ants: 856 valid names (853 fossil, 3 not) and 12 morphospecies, so it was no
  list of the valid species. The two lists now have the views Valid and Curated like every list, counted with their
  fossil names ("Views of lists", the AntCat list and Fossil Ants): the Valid line shows the rows of Valid (856), the
  Species line the rows of Curated (868), each a stored list total; `valid_species_count` (valid names that are not
  fossils) is no longer shown on those two pages. And nobody had been told (Brian Fisher: "how did we not get an alert that the
  counts were not true"): row 129 (the count check's `place.view.totals`), the alert of the count check after the
  daily update, and the two lists in the end-to-end test of row 38 on every run (same section). Tests:
  `PlaceViewTotalDbTest.testTheSiteWideListsAreCountedToo`, `testFossilAntsNumbersAreTheRowsOfItsOwnViews`,
  `StatusChoiceTest.testTheAntCatListAndFossilAntsHaveValidAndCurated`, `tests/unit/test_place_species_label.py`,
  `e2e:test_fossil_page_counts.py`. Still as before: `project.genus_count` of
  Fossil Ants (and its subfamily rows' stored genus_count) is one over from set 3 to the next daily update; no page
  shows it once the list totals are counted. To confirm on production (read-only), the genus the import adds and set 5
  removes: `select t.taxon_name from taxon t where t.taxarank = 'genus' and t.fossil = 1 and t.status = 'valid' and
  t.family = 'formicidae' and not exists (select 1 from proj_taxon l where l.project_name = 'fossilants' and
  l.taxon_name = t.taxon_name)`.

Found while testing the admin and upload report numbers (2026-10-27, rows 83, 94 to 96, 98; all fixed the same day).
Upload reports are files written once, so a report written before the fix keeps its old numbers; reports of the new
rule carry a hidden mark (`id="report-counts-rule"`, `UploadDetails.COUNTS_RULE`) and only those are held to it:

- **B60** (fixed) the upload report never showed "Inserted Species": `UploadDetails.countInsertedSpecies()` had no
  caller, and `AntwebUpload.saveTaxon` counted a newly inserted species or subspecies as updated. Now the insert counts
  it as inserted. Tests: `junit:UploadReportCountsTest#testANewNameIsInsertedNotUpdated`,
  `e2e:test_numbers_uploads.py::test_upload_report_counts_agree_with_the_uploaded_file`.
- **B61** (fixed) "Updated Species" counted a name again each time its lines came back after another name's lines
  (`saveTaxon` skips only a name equal to the previous line's): lines A, B, A counted 3. Now each name once
  (`UploadDetails` keeps the names). Test: `junit:UploadReportCountsTest#testEachUpdatedNameCountsOnce`.
- **B62** (fixed) the report's Record Count counted the header line (`SpecimenUpload.importSpecimens`:
  `setRecordCount(lineNum)`, lineNum one past the last line); now the lines after the header. Test:
  `e2e:test_numbers_uploads.py::test_upload_report_counts_agree_with_the_uploaded_file`.
- **B63** (fixed) statistics.do breakdowns: the "Imaged v. sp." link (taxaList.do?imaged=1) listed the place's valid
  species with an image anywhere (`taxon.image_count`), the number counts those imaged in the place (the place list's
  `image_count`). taxaList.do now takes a place's image counts when it is given a place. Test:
  `e2e:test_numbers_uploads.py::test_statistics_breakdown_numbers_equal_their_lists`.
- **B64** (fixed) statistics.do?byGeolocale: "Imaged v. sp." counted valid imaged names of every rank (genera and
  subfamilies too; `GeolocaleTaxonDb.getStatistics`), and every link named the place by its name, which
  `GeolocaleMgr.getGeolocale(name)` resolves to a region, subregion or country of that name before an adm1, and to the
  first adm1 of a name several adm1s share (the US state Georgia listed the country Georgia). Now species and
  subspecies only, and the links give the place's id. Test:
  `e2e:test_numbers_uploads.py::test_statistics_breakdown_numbers_equal_their_lists`.
- Q3 (decided 2026-10-22 and 2026-10-28, plan in 5.1): **done** 2026-10-28: Endemic counts valid names only, native rows
  only, at every kind of place (#24); the xfail is removed. Test: `e2e:test_numbers_endemic.py::test_endemic_names_of_countries_are_named_species`.
- B15 has a test now: `e2e:test_numbers_lists.py::test_species_browse_counts_the_specimens_of_its_list_row`.
- Not a number: the API's `/geolocaleTaxa?geolocaleId=N` fails (it filters on `GeolocaleTaxon.id`, a column the model does
  not have); the tests ask for a place's rows by name and keep that place's id.

Found while doing this (2026-10-27, end-to-end tests of the API counts, rows 101 to 118; all three fixed the same day):

- **B55** (fixed 2026-10-27) `/geoSpeciesStats` joined the place's list by name (`g.name = :geolocaleName`,
  `api/v3.1/api.py` getGeoSpeciesStats), so a name that several geolocales have (a country and an adm1 such as
  Georgia, or two adm1s) counted each specimen once per geolocale whose list has the name; and `getGeolocale` took
  whichever geolocale of the name the database returned first, an invalid one too (also used by
  `/unimagedGeolocaleTaxa` and `/specimens?geolocaleName`). Now the list of the one geolocale found (`g.id`), and a
  valid geolocale first, then the lowest id. An adm1 takes its specimens by its country and name (B26, fixed
  2026-11-09). Tests:
  `e2e:test_numbers_api_meta.py::test_geo_species_stats_of_a_shared_name_count_each_specimen_once`,
  `unit:test_api_counts.py::test_geo_species_stats_counts_the_list_of_one_geolocale`,
  `unit:test_api_counts.py::test_get_geolocale_prefers_a_valid_geolocale`.
- **B56** (fixed 2026-10-27) `/unimagedGeolocaleTaxa` without byCaste had no order, so pages read with offset could
  repeat or skip names (test_numbers_lists.py worked around it). Now ordered by name. Tests:
  `e2e:test_numbers_api_meta.py::test_unimaged_names_have_no_image_of_the_ant`,
  `unit:test_api_counts.py::test_unimaged_names_have_a_fixed_order`.
- **B57** (fixed 2026-10-27) `/unimagedGeolocaleTaxa?byCaste=true` was ignored: `true` was turned into the number 1
  and then compared with the text '1' (the documented byCaste=1 worked). Tests:
  `e2e:test_numbers_api_meta.py::test_unimaged_by_caste_counters`, `unit:test_api_counts.py::test_by_caste_true_is_by_caste`.

Found while doing this (2026-10-27, end-to-end tests of rows 19, 45, 65 to 70, `tests/e2e/test_numbers_search.py`):

- **B50** (fixed 2026-10-27) after Compare or Map of a search by species, the list holds the imaged (compare) or
  georeferenced (map) species only, but the header kept the search's "S Species (from N specimens).":
  `PrepareCompareResultsAction` and `PrepareMapResultsAction` put their count in a request attribute that
  `advancedSearchResultsByTaxon-body.jsp` never read. The page now shows the preparation's count for species. Tests:
  `e2e:test_numbers_search.py::test_compare_and_map_preparations_count_what_they_list`,
  `tests/unit/test_numbers_search_fixes.py`.
- **B51** (fixed 2026-10-27) locality.do "N collections": `LocalityDb.getLocality` listed `select distinct` collection
  code, habitat, method, coordinates, collector, microhabitat and dates, so a collection whose specimens differ in one of
  these was listed and counted once per value, and specimens without a collection code made a row of their own. Now one
  row per collection code (`group by sp.collectioncode`, codes only). Tests:
  `e2e:test_numbers_search.py::test_locality_collections_equal_the_codes_of_its_specimens`,
  `tests/unit/test_numbers_search_fixes.py`.
- **B52** (fixed 2026-10-27) collection.do "of M species" counted every genus + species string of its specimens,
  names not identified to genus included (`(indet) ...`), against the rule of 2026-10-22. `collection-body.jsp` now
  leaves them out (`PlaceTotals.isIdentifiedToGenus`). Tests:
  `e2e:test_numbers_search.py::test_collection_counts_equal_a_search_and_the_api`, `tests/unit/test_numbers_search_fixes.py`.
- **B53** (fixed 2026-10-27) map preparation (`PrepareMapResultsAction.hasGeoRefInfo`) read the coordinates with
  `getInt`, so a specimen within one degree of the equator or of the prime meridian (latitude or longitude between -1
  and 1) counted as not georeferenced and was left out of "N georeferenced specimens". Now read as decimals, a missing
  value is not georeferenced. Tests: `e2e:test_numbers_search.py::test_compare_and_map_preparations_count_what_they_list`,
  `tests/unit/test_numbers_search_fixes.py`.
- **B54** (fixed 2026-10-27) the search by species ("S Species (from N specimens).", `AdvancedSearchResults.getSpeciesList`)
  counted every genus + species (+ subspecies) of the specimens found, names not identified to genus included.
  `getSpeciesList` now leaves out names whose genus is not identified (`AdvancedSearchResults.countsAsSpecies`, the rule
  of `PlaceTotals.isIdentifiedToGenus`). The same list is the session's `taxonList` that Compare, Map and the field guide
  of the search choose from by index (`PrepareCompareResultsAction`, `PrepareMapResultsAction`, `CompareResultsAction`,
  `MapResultsAction`, `FieldGuideResultsAction`), so they leave these names out too; a search of such names only lists
  "0 Species (from N specimens)." and no error. The field guide shows no species count. The specimens are not hidden:
  N still counts them, and a line under the list says "K specimens not identified to genus (not counted as species),
  listed in the specimen view of this search" (`SearchAction` sets the count and the link, the same search with
  `resultRank=specimen`; the search has no filter for "not identified to genus"). The curators' Species List Tool
  ("search results" list) uses the same method and leaves them out as well. Tests:
  `e2e:test_numbers_search.py::test_search_by_species_leaves_out_names_not_identified_to_genus`,
  `e2e:test_numbers_search.py::test_search_by_species_note_and_listed_species_add_up_to_the_specimens`,
  `junit:SearchSpeciesListTest` (no database), `tests/unit/test_numbers_search_fixes.py`.
- Not a number, for the record: `test_numbers_live.py::test_collection_and_locality_counts_equal_their_lists` and
  `test_comparison_of_chosen_specimens_counts_them` could not fail as written (collection.do links its specimens as
  `specimen.do?name=`, the search rows' checkboxes have `class` before `name`, and the compare pages need a login), and
  `test_search_counts_agree` read a compare count that no page prints. The new tests above check these numbers.

Found while doing this (2026-10-27, end-to-end tests of the image counts, rows 28, 44, 49, 55 to 62, 64, 71):

- **B45** (decided 2026-10-27, option A, label changed) images.do, the taxonomicPage images view (`images=true`), browse.do of a genus and the comparison pages decide
  whether a child is imaged by its pictures, and `Taxon.setImages` takes them from the picked default specimen of the
  name, else from any specimen of the names on the place's list, collected anywhere (`getUnpickedFromSpeciesSet` has no
  place condition). So "X with no image from this place (N)" (`unImagedTaxa.jsp`, `!getHasImages()`) lists only the
  children without a picture anywhere in AntWeb, and a child with no image from this place is shown with another
  place's specimen and "N Images" = the number of that specimen's pictures (`taxonChildImageSet.jsp`: when the list
  row says No Images it prints `thisChild.getImageCount()`, the size of the pictures shown; row 56 said the worldwide
  `taxon.image_count`). Fix (needs Brian's choice): either the label says "with no image in AntWeb" (the Q12 rule,
  then computed by `StatisticsDb.antImagedNames`), or the list and the "N Images" use the list row's Images (`No
  Images` when it is 0, and a note that the picture is from elsewhere). Test (option A):
  `e2e:test_numbers_images.py::test_no_image_list_names_the_children_without_pictures`.
  Brian Fisher 2026-10-27: keep the best pictures from anywhere (people looking at a list want a picture of the
  species); the list is now "with no image in AntWeb" and the images view says pictures may be of specimens
  collected elsewhere; a local picture for a list is chosen with the image picker.
- **B46** (fixed 2026-10-27) images.do with "Browse Images By: All" (cookie thumbs=a) showed "0 Images" for every name:
  `taxonChildImageSet.jsp` printed `getHasImagesCount()`, which only `Specimen.setHasImages` sets. It now shows the
  number of the default view (the list row's Images; B45 for 0). Tests:
  `unit:test_image_count_fixes.py::test_all_view_shows_the_list_images_of_a_name`,
  `e2e:test_numbers_images.py::test_images_page_child_counts_equal_the_list_rows`.
- **B47** (fixed 2026-10-27) recent images (`recentSearchResults.do?searchMethod=recentImageSearch`) grouped by name, subfamily,
  genus, species, subspecies, shot type, shot number, upload time and group (`RecentImageSearch.createInitialResults`),
  not by image: the images of two specimens of one name with the same shot and number uploaded in the same second (one
  image upload) were one row, so "N Images" was too low; an image of two groups was two rows. Now `group by image.id`.
  Tests: `unit:test_image_count_fixes.py::test_recent_images_one_row_per_image`,
  `e2e:test_numbers_images.py::test_recent_images_count_equals_the_images_uploaded`.

- **Images from anywhere** (decided by Brian Fisher 2026-10-27): AntWeb exists to help people identify what they
  found. Specimen counts stay per place (what has been recorded there); images help identification wherever the
  specimen came from. Found by curator Michele Esposito: the Indonesia Leptogenys images page showed L. purpurea with
  CASENT0270569 (Papua New Guinea), the best image from anywhere (B45), but its species images page kept
  countryName=Indonesia and showed only the Indonesian specimens, so the representative disappeared. Now: (1) images.do
  of a species or subspecies reached with a place that selects specimens (country, adm1, bioregion, museum) shows every
  imaged specimen of the name; the place's specimens first, marked "from Indonesia", then the representatives (picked
  defaults, the specimen shown for the name), then the others, each group by caste; "No specimens from Indonesia are
  imaged yet" when the place has none; `placeOnly=true` ("Only specimens from Indonesia") is the old view (regions,
  subregions and projects select no specimens, so their species images pages always showed every specimen; the old
  `global=true` link is gone, the parameter still works). Specimen counts and the species browse page are unchanged.
  (3) The Images column shows the images of the name in AntWeb (row 44; both numbers from 10-27 to 10-28). (5) The species page says which castes are imaged (row 120).
  Rows 55 to 62 are unchanged (the species images page's header counts the specimens shown, now from anywhere).
  Tests: `junit:ImagesAnywhereTest`, `unit:test_images_anywhere.py`, `e2e:test_images_anywhere.py`.

- **Images columns count ant images only** (decided by Brian Fisher 2026-10-27: "We will go crazy if we are not
  consistent"). Every Images column of a list counts images of the ant; label images never count. Nightly set 4 counts
  the list rows' image_count (geolocale_taxon, museum_taxon, bioregion_taxon, proj_taxon, so the allantwebants row
  too) from the image table without label shots (`getTaxonImageCountQuery` of Geolocale, Museum, Bioregion, Project:
  `StatisticsDb.ANT_IMAGES_OF_SPECIMEN`; it summed specimen.image_count, every image file). The place pages'
  statistics table (rows 27 to 33: Images = ant images + label images, apart) is not changed. Changed with it: the
  Images column of every list (row 44), its definition under the list ("Images: images of the ant
  (head, profile, dorsal and other views); label images are not counted."), the genus, subfamily and family rows
  (48), images.do and the taxonomicPage images view's "N Images" of a name (56), the API's /geolocaleTaxa and
  /bioregionTaxa imageCount (112, 116), the count check's recount of the rows (cc:images, `CountCheck.checkRowImages`;
  pending until a nightly run has written "list Images counted as ant images" to compute.log), the end-to-end
  recounts (test_numbers_images.py, test_images_anywhere.py: /taxaImages without label shots). Left as they are,
  because they count a specimen's or a taxon's image files, which its pages show, labels included: a specimen row's
  "N Images" on browse.do of a species (61) and specimenImages.do (64), taxon.image_count and the API's /taxa,
  /specimens and /taxaImages imageCount (54, 104, 106, 107), the place statistics (27 to 33). The numbers follow
  the rule after the first nightly run with this code (set 4); until then the lists show the counts with labels.

- **Images are images of the ant; label images apart; Image files** (decided by Brian Fisher 2026-10-27, "5 images +
  label"; section 1, Definitions). Audit of every image number (rows 1 to 120): already images of the ant: 3, 4, 8, 9,
  27, 28 (label only), 30, 31, 32 (labels, their own row), 44, 48, 55 to 60, 62, 112, 116, 117, 120; changed to images
  of the ant with label images shown apart: 5, 10 (+ N labels), 13 to 15 (Label images column), 16 (ant images only),
  56 (the specimen's own count when the list has none), 61, 63, 64, 71 ("N Images + label"), 100, 104, 106, 107
  (antImageCount, labelImageCount added, imageCount kept for clients); labelled "Image files" (every uploaded file,
  label images are files too): 29, 33, 75, 76, 84, 92, 93, 94, 99, 54 (admin); left: 97 (the admin statistics
  history: rows before 2026-10-27 counted files, the column title says so), 109 and 114 (the API's place imageCount:
  the place's Image files, documented on the API page), the counts of imaged specimens and names (rows 3, 4, 27, 46,
  49, 67, 69: specimens with an image; 46, 49 and 67 still count a specimen with a label image only as imaged, a
  follow-up). Tests: `unit:test_image_counts_defined.py` (every image-count getter on a page has its meaning; no
  number printed before "Images" outside the helpers; the definition under the lists), `unit:test_api_image_split.py`,
  `junit:PlaceTotalsDbTest#testStatsPageImageTableAddsUp`, NumberChecks stats.images and stats.imaged, the end-to-end
  recounts (test_numbers_images.py, test_numbers_live.py, test_specimen_pages.py, test_count_check.py,
  test_numbers_uploads.py, test_image_upload.py). Home and statsPage totals follow the rule after the next nightly run
  (or upload); until then the count check calls the difference pending.

**First run on staging (2026-10-22, full e2e with #226; the nightly run before it had the code from before #216):**

| Check | Real differences | What it was | Now |
|---|---|---|---|
| taxon.parents | 6,503 | mostly a check bug: synonyms, homonyms and obsolete combinations take the parent of their current valid name by design | counted statuses only, names that are their own current valid name; what remains is B36 (or B25 when the parent is a species) |
| admin.curators | 18 | check timing: the job starts right after the nightly run, LoginMgr reloads the curator numbers in a thread that finishes later, so the job compared the numbers of the previous load; and (found 2026-10-22) a check bug: Description Edits on the site count description_edit and description_hist, the check counted description_edit only | pending when the rows are newer than LoginMgr's read (`LoginMgr.getCuratorsRead`); **fixed** 2026-10-22: site and check count description_edit, description_hist, object_edit, object_hist (Brian Fisher: place and project texts count too; B39) |
| stats.bioregions, stats.museums | 5, 11 | check bug: they compare with the place pages' Valid species, Subfamilies, Genera, Species, whose rules changed on 2026-10-21 and were last written by the old code; and (found 2026-10-22) a real cause: museum and bioregion lists were counted, and their totals written, before set 3 renamed their synonym rows (B38) | pending until a nightly run with the 2026-10-21 rules; B38 **fixed** 2026-10-22 |
| admin.groups | 5 | real: the upload row is a snapshot written at the group's last upload (Q13); specimens changed since (deleted, flagged, statuses changed) or the counting rule changed since an old upload | **fixed** 2026-10-22 (Q13 decided): the pages show the group's specimens today, recounted every night (`group_count`, `GroupCountDb`, set 3); the check compares them with a recount; pending until the first nightly run has counted a group |
| api.taxa, geo.adm1.country | 29,910, 3 | known B11, B26 | as before; B26 **fixed** 2026-11-09 (geo.adm1.country is now a recount, 0 expected) |
| list.genus, place.totals (geolocale genus rows) | US formicinaeformica 104 (right: 103), Czech Republic formicinaecamponotus 8 (right: 7) | real: set 2's `geolocaleTaxonFix` ran after the count and rewrote every country and live adm1 genus row's Species with every list row under the genus, of any status and rank (an unavailable species, a subspecies of an unavailable name) (B37) | **fixed** 2026-10-22: no longer in set 2 (the count writes the same rows, same places, by the counted rule); the admin job counts by that rule too. Test: `NightlyCountOrderDbTest#testAnUnavailableSpeciesDoesNotRaiseItsGenusRow` |
| place.totals (museums, bioregions) | museum AFRC: 14 valid species rows not in its Species (e.g. ponerinaeboltonopone ilgii 52 specimens filed under an older combination), ABS 1, CELC 4 species and 1 genus | real (B38) | **fixed** 2026-10-22, see B38. Test: `NightlyCountOrderDbTest#testAMuseumListCountsTheSpecimensOfASynonymUnderTheValidName` |
| taxon.parents (after the fixes above, full e2e of 2026-10-22) | 6 | real (B36): counted ant names with no parent or with no genus row, e.g. `(indet)decamorium decem` | **fixed** 2026-10-22: uploads set the genus by name; nightly set 1 sets the parent where the genus row exists (`TaxonParentDb`), never makes a genus row and reports the rest; names not identified to genus are not species (section 1); one-off repair in B36. Test: `TaxonTreeCountDbTest#testRepairSetsParentsOnlyToExistingGenusRows` |
| list.images (same run) | 9 | real (B40): worldants genus rows lower than their rows (Holcoponera 596 against 604) | **fixed** 2026-10-22: the higher rows are added up by the taxon tree. Test: `TaxonTreeCountDbTest#testGenusRowHoldsTheImagesOfASynonymUnderIt` |
| taxonomicPage headers (e2e) | Madagascar | expected: the place numbers are from the nightly run with the old code (non-ants, roll-up sums) | should agree after the 2026-10-22 nightly; a difference after that is real |

## 4. Plans for the larger ones

| Id | Plan |
|---|---|
| B11 | API /taxa `subfamilyCount`, `genusCount`, `speciesCount`, `specimenCount`: read the allantwebants row (`proj_taxon`) instead of taxon columns nobody writes; document in the API page. The check `inv:api.taxa` (2026-10-22) compares the taxon columns with the Global row for every taxon (where the columns exist; real differences until fixed). |
| B14 | **Done** 2026-10-22 (Q8): `GroupCountDb.countColumns`, one rule for the nightly recount, each upload's row and the check; groups.do Totals = group_count row 0. |
| B15 | One place rule for counts and links: `Overview.getOverviewCriteria` uses `Geolocale.getSpecimenPlaceCriteria` (country = X, island_country for islands, the countries of a region); browse filters on `specimen.status` like the counts; e2e: for 3 random species rows of the sampled places, the browse page's count equals the row. |
| B19 | Count children with coordinates; label "N of M mapped". |
| B20 | Keep search totals in the request, not the session; count imaged as images of the ant; fix "N specimens Specimens". |
| B21 | **Done** 2026-10-22 (Q12): the header shows 0 too; unimaged = no photo of the ant anywhere (`StatisticsDb.antImagedNames`). The images view keeps a per-place list, now labelled "with no image from this place". |
| B22 | Curator counts: recount when an upload or image upload ends; place pages: an e2e test comparing each sampled place's page (`data-stat` numbers) with `countCheck.do?format=json`; alert when set 5 fails. |
| B23 | `MessageMgr` errors per upload (instance, not static). |
| B25 | Checked by `inv:taxon.parents`; **repaired every night** since 2026-10-22: nightly set 1 sets the parent of such a subspecies to its genus (`TaxonParentDb.repair`, B36). |
| B26 | **Done** 2026-11-09 (Brian Fisher): the country in every adm1 specimen query (section 3) and in the count check's recount (`CountCheck.Place.adm1`: `and s.country = ? and s.adm1 = ?`); `inv:geo.adm1.country` recounts every live adm1 by that rule. |
| museum, bioregion lists | **Done** 2026-10-22 (B38): each list is renamed right after it is built, before it is counted and its totals written; a synonym's specimens count under the valid name. |
| partly rows | Most are live numbers checked by the end-to-end tests on Madagascar and the genus Pheidole only, or by a consistency rule. **Done** 2026-10-26 for rows 24, 25, 38, 39, 46, 47, 50 to 53, 78, 79: a rotating sample of every kind of place in the e2e tests (section 1). Next: the same for the comparison and image pages (55 to 62), and recounts in the check for the live numbers that have a SQL definition. |

## 5. Questions for Brian

### Counting rules (Brian Fisher, 2026-10-28)

A specimen counts at each rank it is identified to, and no further. This replaces the answer of 2026-10-21 to Q2
(morphospecies and indet names count as species). For any place (country, adm1, region, subregion, bioregion, museum,
project):

| Number | Counts | Code |
|---|---|---|
| Specimens | every ant specimen collected there (unchanged); museums and bioregions: the sum of their list rows, like countries (decision 4 below) | `PlaceTotals.listedSpecimensSql`, `writeListedSpecimens` |
| Subfamilies | subfamilies with at least one specimen identified at least to subfamily (the subfamily rows of the list); since 2026-10-30 valid subfamilies only, not the placeholder "(formicidae)" (genera and subfamilies rule, below) | `PlaceTotals.rankCondition(SUBFAMILY)` |
| Genera | genera with at least one specimen identified at least to genus: a "Lioponera indet" specimen makes Lioponera count (its genus row is on the list); names not identified to genus (`PlaceTotals.identifiedToGenus`) do not. Since 2026-10-30 valid genera and morphogenera only (genera rule, below). Country and adm1 lists took valid names only from specimens, so the genus of an indet name or morphospecies was missing there; since 2026-10-29 set 2 adds its genus row (not the name) | `PlaceTotals.rankCondition(GENUS)`, `GeolocaleTaxonDb.GENUS_ROWS_FROM_SPECIMENS_SQL` |
| Species / subspecies / morphospecies | names with status valid or morphotaxon only; indetermined, unidentifiable and unrecognized never count as species (until 2026-10-28 all five counted statuses did) | `PlaceTotals.SPECIES_STATUSES`, `rankCondition(SPECIES)`, `countsAsSpecies` |
| Valid species | valid only (unchanged) | `PlaceTotals.VALID_CONDITION` |
| Endemic | valid names only (decided 2026-10-28, below); until then valid and unrecognized names on the lists and numbers, and every counted status but morphotaxon (countries, adm1s) or all five (bioregions) in the flags | `PlaceTotals.ENDEMIC_STATUSES`, `endemicEligible`, `flaggedCondition` |

Lists: on a genus list, a genus known at the place only from specimens identified to genus showed "No Species" and
"N specimens identified to genus only", linked to those specimens; since 2026-10-30 every specimen collected there under
a name not on the species list counts (morphospecies and unrecognized names too): "No species on the list · N specimens
collected here", or "+ N specimens of other names" under a genus with species (row 41); since 2026-11-02 (views) the
Other specimens column of genus rows, the same evening replaced by Not curated (counted nightly: the specimens whose name
is not curated, linked to the view Not curated), and a genus known only from such specimens is a row in All specimen names only. On a species list of valid names and morphospecies (the choices Valid species and Valid species +
morphospecies, 2026-10-30) there is no row for names that are not species,
and one line under the list: "N specimens identified only to genus are not listed here: Lioponera (1), Pheidole (2).
Show all specimens." (`GenusOnly`, `PlaceTotals.genusOnlySql`; indetermined and unidentifiable names identified to
genus). The choice All names (statusSet=all, 2026-10-30) still lists those names. The genus rows'
Species of every list (the nightly crawl), the browse page of a genus, "species + ssp. imaged" (home, statsPage) and
the count check take the same rule. Unrecognized names are not counted; listing them for curators in "Check names
against AntCat" (validateSpeciesList.do) is not done yet: that page checks an uploaded file, so a list of a place's
unrecognized names is a new page section (later).

Genus pages and places (Brian Fisher, 2026-10-29). Every genus page (description.do, browse.do, images.do; any place)
says under its species "Not identified to species: Lividopone (indet), 45 specimens (4 imaged)", linked to the indet
name's page in the same place (row 121; before, no genus page linked it). The genus of a specimen identified only to
genus is on the country's and adm1's list (row 21). A genus or species page opened for a place that has neither the
taxon on its list nor a specimen of it says "<Taxon> is not recorded from <Place>.", the bioregions whose list has it and
a link to all of AntWeb (`PlaceTaxonNotes`; docs/flows/taxon-pages.md). Numbers that change at the next nightly run
(set 2): Genera (and Subfamilies where a subfamily is new) of countries, adm1s, subregions and regions, and the N Genera of
the subfamily rows of their lists; the Genera
column of the countries, adm1s, subregions and regions list pages and the API `/geolocales` genusCount with them. Not
changed: Species, Valid species, Endemic, Specimens, Images, every bioregion, museum and project number.

### Genera and subfamilies rule (Brian Fisher, 2026-10-30)

The Afrotropical bioregion page showed Genera 129 because placeholder and unrecognized genus rows were counted:
"(dolichoderinae)", "(formicidae)", "(formicinae)", "(myrmicinae)", "Amblyoponinae)" (specimens identified only to
subfamily or family), and "(formicidae)oligomyrmex", "(formicidae)opthalmopone", "(formicidae)pattothyreus",
"(formicidae)polyrachis", "(formicidae)pyramica", "(formicidae)phasmomyrmex", "(formicidae)rhoptomyrmex", "(dacetini)"
(genus names AntCat does not know, uploaded without a subfamily, status unrecognized).

**Genera = valid genera + morphogenera** (status valid or morphotaxon; "Form_gen-afrc-za01" is a morphogenus). A genus
value with parentheses and a subfamily or family name is undetermined, never a genus. Unrecognized genus names
(misspellings, old names) are not genera; they stay in AntWeb with their specimens and stay on the lists (which rows
are listed is a separate change). One rule, `PlaceTotals.countsAsGenus` (SQL; `isCountedGenus` in Java): status in
`GENUS_STATUSES` ('valid', 'morphotaxon'), identified to genus (`identifiedToGenus`) and not a placeholder
(`notPlaceholder`: no "(" or ")" in the taxon name, so "Amblyoponinae)" is out too). Used on every kind of place
(country, adm1, region, subregion, bioregion, museum, project: `rankCondition(GENUS)`), the subfamily rows' "N Genera"
of every list (the nightly crawl, `childCountCondition` in `getCountChildrenQuery` of Geolocale, Project, Museum,
Bioregion), upload groups and their Totals row (`GroupCountDb.COUNTED_GENERA`; also each new upload's numbers), the
API (`/geolocales`, `/bioregions` genusCount read the stored numbers; `/taxa` genusCount copies the Global rows), and
the count check (`inv:place.totals.genus`, `inv:list.subfamily`, `cc:genera`, `inv:admin.groups`). The definition under
the place statistics table says "valid genera and morphogenera ... Names identified only to subfamily and genus names
not in AntCat are not counted." Not changed: statsPage "valid genera" (valid only, as labelled). The list headers of
taxonomicPage.do and browse.do count the rows they list; since 2026-10-30 (review of #307) the lists of the choices
Valid and Valid + morphotaxa leave out the rows this rule does not count (`StatusSet.listRankCondition`, "Status choice
of lists" below), so their headers equal Genera and Subfamilies. The SQL rule and the Java rule are identical
(`countsAsGenus`, `isCountedGenus`: a genus value or a taxon name with "(" or ")" is never a genus; identified to genus
with the value trimmed; `junit:GeneraRuleDbTest#testJavaAndSqlGenusRulesAgree`).

Subfamilies: the same bug in one form: the placeholder subfamily row "(formicidae)" (status morphotaxon since 2017,
db/upgrade/7.0) was counted as a subfamily where it is on a list. **Decision (Brian Fisher, 2026-10-30): Subfamilies =
valid subfamilies only**, the same principle as Genera, everywhere: `PlaceTotals.countsAsSubfamily` (status 'valid',
AntCat's valid subfamilies, and `notPlaceholder`; `isCountedSubfamily` in Java). The placeholder "(formicidae)" and
misspelled or unknown subfamily names (status unrecognized) stay on the lists but are not counted. Used on every kind
of place (`rankCondition(SUBFAMILY)`), **curated projects too: this replaces B67** (2026-10-27, a curated project
counted its subfamily rows of every status; `listedCondition` is now `rankCondition` for every place), the Formicidae
row's "N Subfamilies" of every list (`childCountCondition`), and the count check (`inv:place.totals.subfamily`,
`cc:place totals`, `inv:list.family`). Upload groups' Subfamilies already counted valid ant subfamilies only
(`SpecimenDb.getAntSpecimenCriteria`); statsPage "valid subfamilies" is valid only; the API `/geolocales`,
`/bioregions` subfamilyCount read the stored numbers: no change there. The definition under the place statistics table
says "valid subfamilies only. Specimens identified only to family and subfamily names not in AntCat are not counted."
Tests: `junit:GeneraRuleDbTest#testSubfamiliesCountValidSubfamiliesOnly` (Myrmicinae, "(formicidae)" and a misspelled
"myrmicicinae" on a country and a curated project: Subfamilies = 1 each, the crawl and the count check too),
`junit:CountRules20261030Test#testSubfamiliesAreValidSubfamilies`.

Also decided 2026-10-30: the header of a list's images view names the status choice in the words of the list header
(`StatusSet.imagedHeading`, row 57): "N Valid species imaged (out of M valid species)", "N Species imaged (out of M
valid species and morphospecies)", "N Genera imaged (out of M valid genera and morphogenera)"; it said "N Species
Imaged (out of M Total)".

The stored numbers change at the next nightly run (sets 2 and 3; groups set 3). Set 4 writes "genera and subfamilies
rule of 2026-10-30" on its "Set 4 done" line (`CountInvariants.GENERA_RULE_LOG`; both rules went live before the same
nightly run, so one marker; a line with the earlier wording "genera rule of 2026-10-30" alone does not count); until then the count check calls these
differences pending (`rulesCounted`; groups: `GENERA_RULE_PENDING`) and the e2e tests skip as pending
(`numbers_sample.genera_rule_counted`; the tests that read compute.log are not in [parallel]). Tests:
`junit:CountRules20261030Test` (no database), `junit:GeneraRuleDbTest` (a valid genus, a morphogenus, "(myrmicinae)",
"(formicidae)polyrachis" and "Amblyoponinae)" on one list: Genera = 2),
`e2e:test_numbers_lists.py::test_list_headers_equal_place_numbers_of_every_kind` (Genera and Subfamilies = the listed
rows that are genera and subfamilies, from the API /taxa statuses).

Also decided on 2026-10-30: the admin alert "No Worldants changes in last week" was raised although the AntCat file
changed every day. `WorldantsUploadDb.CHANGE_COUNT_SQL` compared each import's file size with its own
backup_file_size, which is the size of the dated copy `SpeciesListUploader.record` makes of that same file (so always
equal: production 2026-10-30 139379248 and 139379248). It now counts the imports of the last 7 days whose file size
differs from the previous import's (MySQL `lag` over created); an import with no previous row counts as no change.
backup_file_size is right for what it is (the copy's size) and is left as it is. Test:
`junit:GeneraRuleDbTest#testWorldantsChangeCountComparesWithThePreviousImport`.

Also decided on 2026-10-28:

| Decision | What | Change |
|---|---|---|
| D3 | Images follow the Specimens rule | On projects, museums and bioregions the images of specimens filed under a synonym count under the valid name, as Specimens do (Carebara polita, a synonym of C. diversa, 6 images, Afrotropical): the lists' Images column (`PlaceTotals.imagesByListNameSql` in `getTaxonImageCountQuery` of Museum, Bioregion and curated projects) and the place's Images and Imaged specimens (`PlaceImageCountDb.listSource`); the count check recounts both the same way (`CountCheck.checkRowImages`, `checkPlaceImages`). worldants and allantwebants (the Images column's AntWeb-wide number) count the very name. |
| D4 | Museum and bioregion Specimens = the sum of their list rows, like countries | `MuseumDb.finish`, `BioregionDb.finish` write `PlaceTotals.writeListedSpecimens`; their Specimens pie and their place images follow the same specimens; `inv:place.totals.specimens` compares every kind of place with the sum of its rows; `CountCheck.checkListPlaceSpecimens` recounts them from the specimens. Row 26 documented the opposite until then. |
| D5 | Bioregion and museum list images get the same status filter as countries | specimens of a counted status (`Bioregion.getTaxonImageCountQuery`, `Museum.getTaxonImageCountQuery`, the count check). |
| D6 | Subregions and regions: Introduced counted the same way Endemic is for them | `GeolocaleDb.calcHigherIntroduced` (`HIGHER_INTRODUCED_SQL`): a name flagged introduced on a country's list is flagged on its subregion's list, a subregion's on its region's; until then 0. |
| D7 | Upload groups follow the Species rule (one rule everywhere) | groups.do, group.do, curator.do Species and the groups.do Totals Species count valid names and morphospecies only (`GroupCountDb.countColumns` uses `PlaceTotals.rankCondition`); the stored numbers change at the next nightly recount (set 3); until then `inv:admin.groups` calls a Species difference pending. |

The numbers follow these rules after the first nightly run with this code: until compute.log has a "Set 4 done" line
with `CountInvariants.COUNTING_RULES_LOG` ("counting rules of 2026-10-28"), the count check calls the differences of
these rules pending. Tests: `junit:CountRules20261028Test` (one test per rank and per decision, no database),
`junit:PlaceTotalsDbTest#testSpecimensCountAtEachRankTheyAreIdentifiedTo`,
`junit:PlaceImageCountDbTest#testImagesOfASynonymCountUnderTheValidName`,
`e2e:test_numbers_lists.py::test_species_lists_show_valid_names_and_morphospecies_only`.

Endemic (raised and decided by Brian Fisher, 2026-10-28): a name counts toward endemism only if its status is valid.
Morphospecies (codes not comparable between collections, 2026-10-22), indetermined, unidentifiable and unrecognized
names never count, anywhere: countries, adm1s, regions, subregions, bioregions; fossils stay excluded. One constant,
`PlaceTotals.ENDEMIC_STATUSES` (`endemicEligible`), in every endemism rule (`GeolocaleDb.COUNTRY_ENDEMIC_SQL`,
`ADM1_ENDEMIC_SQL`, `HIGHER_ENDEMIC_SQL`, `BioregionDb.ENDEMIC_SQL`), the lists and numbers (`flaggedCondition`) and the
check (`NumberChecks.endemicFlags`). Implemented with it: Q4 (endemism after the introduced flags, native rows only)
and the Q3 rule for subregions and regions (5.1). endemic.do lists only names the species list shows. The flags and
numbers change at the next nightly run (geolocales set 2, bioregions set 3); endemic.do and the place pages' Endemic
link list at once. Sets 2 and 3 end with a compute.log line "Set 2 done (endemic rule of 2026-10-28)" and "Set 3 done
(...)" (`CountInvariants.ENDEMIC_RULE_LOG`); until both are there the e2e tests call a stored Endemic number that differs
from endemic.do pending (`numbers_sample.endemic_rule_counted`), and the other stored numbers of these rules until the
"Set 4 done" line has `COUNTING_RULES_LOG` (`numbers_sample.counting_rules_counted`); then they compare strictly. Tests: `junit:CountRules20261028Test#testEndemicCountsValidNamesOnly`,
`junit:PlaceTotalsDbTest#testEndemismCountsValidNativeNamesOnly`, `e2e:test_numbers_endemic.py::test_endemic_names_follow_the_endemism_rule`.

Where the right number is a scientific choice, the checks test the rule as the code has it now and the question is
here, with a recommendation. Brian Fisher answered Q1 to Q6 and Q10 on 2026-10-21, and on 2026-10-22 (05:28) that morphospecies and indet
names never count toward endemism (section 5.1, with the plan); Q13 was decided on 2026-10-22. On 2026-10-22 he
answered Q7, Q8, Q9 and Q12 and decided the Port of Entry project (Q11): section 5.2, implemented in the same pull
request as this text. On 2026-10-28 he decided the counting rules (below, replacing Q2), four related changes,
and that Endemic counts valid names only (Q3).

| # | Question | Now | Recommendation | Rows |
|---|---|---|---|---|
| Q1 (answered 2026-10-21, see 5.1) | Do subspecies count in species totals? | Place "Species / subspecies" counts species and subspecies names (a species with two subspecies counts 3); a genus row's "N Species" counts both; a species row's Specimens are only the specimens filed under the species name (its subspecies have rows of their own); statsPage "valid species + ssp." counts both. | Keep counting both, as the label says, and label the genus row "Species / subspecies" too. Keep a species row to its own specimens (the subspecies rows are listed under it), so no specimen is counted twice in a place total. | 20-22, 41, 1, 6 |
| Q2 (answered 2026-10-21, see 5.1; **replaced 2026-10-28** by the counting rules above: only valid names and morphospecies are species) | Do morphospecies and indet names count as "species"? | Species / subspecies: yes (valid, unrecognized, morphotaxon, indetermined, unidentifiable); Valid species: valid, extant only; statsPage "species + ssp. imaged" counts the same five statuses. | Keep both numbers as they are; they are labelled and defined on the page. | 22, 23, 3 |
| Q3 (answered 2026-10-21, 2026-10-22 and 2026-10-28, see 5.1; **done** 2026-10-28) | Endemic at list edges: which records decide that a name is endemic? | A name is endemic to a country if it is on exactly one country's list, counting lists of any source (specimens, literature, curator lists) and of invalid country rows; morphospecies and fossils excluded. An adm1: endemic to its country and on one adm1 list. A subregion or region shows the names endemic to one of its countries (not the names found only in that subregion). A bioregion includes morphospecies. | Count valid countries only; keep all sources; one rule for all places: exclude morphospecies everywhere (their codes are not comparable between collections), and for subregions and regions "found in no country outside it". Records of introduced populations should not make a native species non-endemic (see Q4). | 24, 78, 110 |
| Q4 (answered 2026-10-21, see 5.1; **done** 2026-10-28) | Introduced species: how do they count? | They count in Species, Valid species and Specimens of the place where they are introduced; the Introduced number is the rows flagged from the introduced-species list; an introduced record elsewhere makes a species non-endemic at home. | Keep them in the place totals (they occur there), show "of which N introduced" under Species, and ignore introduced records when computing endemism. | 22-25, 79 |
| Q5 (answered 2026-10-21, see 5.1) | Fossils: do fossil taxa count in place totals? | Subfamilies, Genera and Species / subspecies of a place count fossil taxa on its list; Valid species excludes them; taxonomicPage counts them too (withFossil). | Exclude fossils from Subfamilies, Genera and Species / subspecies of living places, and show "Fossil taxa: N" as its own row (amber and fossil records stay listed). | 20-22, 38 |
| Q6 (answered 2026-10-21, see 5.1) | "Imaged": images of the ant only, or labels too; species only, or any name? | Home and place pages: a specimen with an image that is not a label; statsPage "Imaged Species" per status: any rank (genus-level names too) and label images; taxonomicPage "Unimaged" and list Images count labels. | One rule everywhere: imaged = a specimen with an image of the ant (not a label); "Imaged Species" counts species and subspecies names only. | 3, 16, 39, 44, 117 |
| Q7 (answered 2026-10-22, see 5.2) | Ants only on statsPage? | Before: statsPage Global Data, Bioregion Data and Museum Data, and the home "valid species + ssp.", counted valid taxa of every family on the lists; place pages count ants only (2026-10-21). | Ants only everywhere (Formicidae), as on the place pages. | 1, 3, 6, 11, 12, 17-19 |
| Q8 (answered 2026-10-22, see 5.2) | Upload group numbers: what is a "species", and what are "Totals"? | Before: groups.do Species = distinct species epithets of valid names of AntWeb's extant genera (Pheidole minor and Camponotus minor count once; morphospecies not counted); Totals of Subfamilies, Genera, Species = the largest group. | Species = distinct names (taxon_name) of species and subspecies of the five counted statuses; Totals = distinct over all groups (or rename the row "Largest"). | 86-90, 83, 91 |
| Q9 (answered 2026-10-22, see 5.2) | Orphan images: count images or codes? | Before: "Found N orphaned images" counted specimen codes that have images but no specimen record. | Show both: "N images of M specimen codes that are not in AntWeb". | 99 |
| Q10 (answered 2026-10-21, see 5.1) | Specimens with an adm1 but no country (B26): count them for the state? | Counted for every adm1 of that name. | Not counted for any adm1 (the state is ambiguous without a country); the fix adds `country = parent` (**done** 2026-11-09). | 26, 27, 29 |
| Q11 (answered 2026-10-22, see 5.2) | Port of Entry: a place? | Before: its page showed 37,430 specimens while its list had no species with specimens; the nightly run did not recount it. | Hide it as a place (is_valid = 0) and keep interceptions searchable. Decided instead: keep it as a species list without numbers (5.2). | 26, 119 |
| Q12 (answered 2026-10-22, see 5.2) | Unimaged names: unimaged where? | Before: API /unimagedGeolocaleTaxa: no image of the ant anywhere in AntWeb; taxonomicPage "Unimaged": no image (labels included) from this place. | Both: "no image of the ant anywhere" (what a curator needs to photograph), labelled so. | 39, 62, 117 |
| Q13 (answered 2026-10-22) | Upload group numbers: the last upload, or now? | Brian Fisher: today's numbers. Since 2026-10-22 groups.do, group.do and curator.do show the group's specimens today, recounted every night in set 3 (`GroupCountDb.recountAll`, table `group_count`) by the rule of the upload counts (`UploadDb.getCounts`); the date of the last upload is shown apart; the upload rows and the upload reports keep each upload's own numbers. `inv:admin.groups` compares the pages' numbers with a recount. | Done. | 83, 86-88, 91 |

### Red flags: one rule for every list (Brian Fisher, 2026-10-30)

Red-flagged specimens are "loaded, hidden from most pages until fixed". Decision: one rule for red flags on every list.
A list row made from specimens never comes from a red-flagged specimen, on every kind of place: country and adm1 lists
(valid names, and the genus rows of specimens identified to genus: `GeolocaleTaxonDb`), bioregion lists
(`BioregionTaxonDb.speciesFromSpecimenSql`) and museum lists (`MuseumDb.speciesFromSpecimenSql`), all by one condition,
`SpecimenDb.notRedFlagged` (flag null or not 'red'). The code already left them out everywhere (the bioregion and museum
queries used the same condition unqualified); the condition is now one method. Production listed Lividopone on the
Afrotropical genus list on 2026-10-30 while staging did not: the Mozambique Lividopone (indet) specimens are red-flagged
on staging (a copy of production from before Brian Fisher ticked Afrotropical in the Bioregion Map Manager), so the
difference is the data, not the code. Tests: `junit:GeneraRuleDbTest#testListsFromSpecimensLeaveOutRedFlags`,
`e2e:test_genus_indet_places.py::test_genus_rows_of_specimens_identified_only_to_genus` (checks Mozambique and
Afrotropical only when those specimens are not red-flagged).

### Red flags: images of red-flagged specimens are not counted (Brian Fisher, 2026-11-09)

"A red-flagged specimen is still a specimen and still imaged, but for counting images exclude them everywhere; we want
to bring them back into the fold later, so we need a way to curate and improve them, starting with the CAS data."

Rule: wherever AntWeb counts images or imaged specimens or names, the images of a red-flagged specimen (specimen.flag =
'red') are left out. One condition: `StatisticsDb.NOT_RED_FLAGGED_IMAGE` for queries on the image table alone (an image
of a code that is no specimen, an orphan image, is not of a red-flagged specimen, so orphan counts do not change), and
`SpecimenDb.notRedFlagged` where the query joins the specimen, the condition the lists already used (2026-10-30). The
label rule (shot type l) is unchanged: label images are still counted apart, and label images of red-flagged specimens
are not counted either. Numbers that follow it (section 2 says so in each row):

- home page and statsPage: species imaged, images of ants, imaged specimens, label images, species + ssp. imaged,
  fossil species and morphospecies imaged, the Images table and Imaged Species per status (rows 3 to 16), admin
  statistics.do (row 97);
- place pages and the place lists: Images, Imaged specimens, views, label images, label-only specimens, Image files
  (`PlaceImageCountDb`, rows 27 to 33, 75, 76, 109);
- the Images column of every list and "N species imaged" of genus rows (`StatisticsDb.ANT_IMAGES_OF_SPECIMEN`,
  `antImagedNames`; rows 41, 44, 56, 112, 116), unimaged lists (row 39) and the API's /unimagedGeolocaleTaxa (row 117);
- taxon image_count (`ImageCountDb.SPECIES_IMAGES_SQL`, nightly set 4; rows 54, 104) and the API /taxa
  antImageCount and labelImageCount;
- the species page's imaged castes (row 120) and the indet line's "(M imaged)" (row 121);
- the imaging progress page (rows 124 to 128; the trend starts on 2026-11-12, `ImagingProgressDb.RULE_SINCE`: the first
  daily update with the rule on production).

Not changed: a red-flagged specimen's own image_count, its images and its page; it still counts as a specimen (for
example "unimaged, with specimens" on the imaging progress page, so its name stays in the labs' queue). Pages that count
the pictures they show (images.do and browse headers, comparisons: rows 49, 55, 57 to 60, 62) follow the pictures
shown, which still include red-flagged specimens; photographer and image upload numbers (rows 84, 92 to 94) count work
done, not the collection. The nightly set 4 writes `StatisticsDb.RED_FLAG_IMAGES_RULE_LOG` on its "Set 4 done" line;
until a nightly run has, the stored image numbers that differ from a recount are pending (`CountInvariants.rulesCounted`;
e2e: `numbers_sample.red_flag_images_counted`). The API has no flag field, so the end-to-end recounts from the API read
the flag from the specimen page of the specimens concerned (`numbers_sample.without_red_flagged`). Tests:
`junit:NumberDecisionsDbTest#testPhotosOfRedFlaggedSpecimensAreNotCounted`,
`junit:PlaceImageCountDbTest#testRedFlaggedSpecimensImagesAreNotCounted`,
`junit:ImagingProgressDbTest#testPhotosOfRedFlaggedSpecimensDoNotCount`,
`junit:CountInvariantsTest#testImagesOfARedFlaggedSpecimenAreNotCounted`, `unit:test_api_counts.py`,
`unit:test_api_image_split.py`. Follow-up: a curation view of the red-flagged specimens (why each is flagged, by
group, CAS first), so curators can fix and unflag them.

Decided by Brian Fisher, 2026-11-09 (the open points of this rule):

- For now red-flagged specimens are left out of the image and specimen counts everywhere: their images by this rule,
  the specimens themselves by the list rule of 2026-10-30 above. Not for good: we want to bring them back into the
  fold through a curation view where curators fix and unflag them, starting with the CAS data. Unflagging brings a
  specimen and its images back into every count at the next nightly run.
- Pages that show photos keep showing the photos of red-flagged specimens (images.do, browse, comparisons, the
  specimen page), and the numbers that count the pictures such a page shows (rows 49, 55, 57 to 60, 62) stay as they
  are.
- Photographer and image upload numbers (rows 84, 92 to 94) stay as they are: they count work done, not the collection.

### The rule of the place page's list numbers (Brian Fisher, 2026-11-11)

 1. Every number on a place page is the header of the list its link opens.
 2. The species lines are in the same order on every place page: first "Valid species / subspecies", then
    "Species / subspecies / morphospecies".
 3. "Valid species / subspecies" = the rows of the list's view Valid (`statusSet=valid`).
 4. "Species / subspecies / morphospecies" = the rows of the list's default view, Curated (`statusSet=curated`).

The same four lines, in the same order, with the same links on every place page. Fossil Ants and the AntCat list have
the same two views, counted with their fossil names ("Views of lists" below): Fossil Ants on 2026-11-11 Valid species
856, Species / subspecies / morphospecies 868 (its 12 morphospecies), Genera 258 (two morphogenera among them).

Where it is kept: `web/overview-body.jsp` (one set of lines; for those two lists only the texts differ: no "living
(non-fossil)", Brian Fisher: "fossil ants are fossil: how can you mention living in the same phrase", and no mention
of the view All specimen names, which they do not have), `PlaceViewTotalDb` (the numbers: the daily update counts the
rows of every place's lists in the default view, the site-wide lists too since 2026-11-11, and a project again when
set 5 takes a row off its list). Valid species: every place's stored `valid_species_count` (valid names that are not
fossils, row 23), except on Fossil Ants and the AntCat list, where it is the stored species total of their view Valid
(`place_view_total`, taxarank `validspecies`, view `valid`; `PlaceViewTotalDb.storesValidSpecies`); until it is
counted those two pages show no Valid line.

How a break of rule 1 is found (2026-11-11; until then it was not: no check compared a page number with its list on
every place, the one end-to-end test skipped a place whose totals were never counted, and real differences of the count
check after the daily update went into the job's result and nowhere else):

- the count check (row 129, `inv:place.view.totals`): every place page with a list has its list totals (three; four on
  Fossil Ants and the AntCat list), counted in the last 8 days; a real difference otherwise;
- the count check after the daily update: real differences outside the known findings (`CountInvariants.KNOWN_FINDINGS`:
  B11, B31, B36, the same list as `KNOWN` in `tests/e2e/test_count_check.py`) end the Count check step of the Daily
  update page with a warning (done, yellow, "N real differences: see Check the counts") and send the administrators
  one email with the number and the link to countCheck.do (`CountCheckJob.warning`, `DailyUpdate.countCheckFinished`,
  the mail path of a failed or slow step; no mail file, no mail). Pending differences and the known findings alone
  send nothing: an email every morning for the same known numbers would be ignored;
- `e2e:test_numbers_lists.py::test_list_headers_equal_place_numbers_of_every_kind` checks Fossil Ants and the AntCat
  list on every run, each of the four numbers against the header of the list its own link opens, Valid <= Species;
  uncounted there is a failure; `e2e:test_fossil_page_counts.py` the lines (order, names, links, texts), the numbers
  again, the selector of the two lists and the old address value.

On the day of this change (once per server): the site-wide lists have no list totals until the first daily update with
this code, so until then the count check reports them (row 129) and the two end-to-end tests fail for Fossil Ants and
the AntCat list. An administrator counts them at once with `/utilData.do?action=placeViewTotals` (the places whose data
changed and the places never counted).

### Views of lists (Brian Fisher, 2026-11-02)

The status menu of lists ("Species Status:", four choices of 2026-10-30, below) and the "Names from" selector of
2026-10-30 are replaced by four views, shown as four buttons (a segmented control, `web/common/statusesDisplay.jsp`),
the same on every kind of list (countries, adm1s, regions, subregions, bioregions, museums, projects, All AntWeb) and
every rank (`StatusSet.LIST_CHOICES`, `ListView`). The header always counts the rows of the chosen view. The Fossil
button is shown only when the list has fossil names in that place (one EXISTS query, `ListView.hasFossil`, request
attribute `ListView.HAS_FOSSIL_ATTR`; hidden otherwise, not greyed). Curated was first called Vetted (staging, a few
hours on 2026-11-02): `statusSet=vetted` still opens Curated; code names (`LIST_VETTED`, `vettedCondition`) keep it.

| View (button) | Address value | Rows |
|---|---|---|
| Valid | `valid` | the list's names AntCat calls valid, extant: from specimens (not red-flagged), curated entries or literature |
| Curated (default everywhere) | `curated` | Valid + the curated morphospecies (below); on country, adm1, region and subregion lists also the curated morphospecies whose not red-flagged specimens were collected there |
| All specimen names | `all` | Curated + every other morphospecies, indet name ("Acropyga (indet)") and other counted status; on country, adm1, region and subregion lists also the names of every specimen collected there (not red-flagged) |
| Fossil | `fossil` | valid fossil names; the genus and subfamily lists show the genera and subfamilies with fossil species on the list, extinct and living ("25 genera with fossil species"; Brian Fisher, 2026-11-06: a list of genera with fossil species, not of fossil genera; `TaxaPage.genusAwareCriteria`) |

Curated morphospecies: a morphospecies (status morphotaxon) is curated when it is on any curated list entry, a
`geolocale_taxon` or `proj_taxon` row whose source a curator writes (`CuratorTaxonSetDb.CURATOR_SOURCES`: curator,
speciesList, speciesListTool, speciesListUpload; not the proxy rows of parent regions, not the system lists worldants
and allantwebants). Vetting belongs to the morphospecies: once curated it is in the Curated view of every place where it
is on the list or (geolocales) where its specimens were collected, up the hierarchy and on museum and bioregion lists
(those lists take every specimen name). A curator who removes it from one place's list (a dispute,
`geolocale_taxon_dispute`, `proj_taxon_dispute`) takes it off that place only. Computed at query time
(`ListView.vettedCondition`): per morphospecies row one indexed lookup of `geolocale_taxon` by taxon_name and, when that
finds none, one primary-key lookup of `proj_taxon` per project; no stored flag, so a curator's change shows at once.
Speed (2026-11-02, staging): a country's specimens are selected by `country = X or island_country = X`, which no index
serves (there is an index on country, adm1 only), so each query that selected them scanned the specimen table (900,000
rows); the first build of the views made three such scans per genus list (Mozambique 6 to 11 s, was 1.6 s). A genus list
now reads the place's specimens once (`ListView.loadPlaceSpecimens`, grouped by name) and gives their names to the other
queries (`ListView.namedIn`, `TaxaPage.specimenNamesWhere` with the names); the rows are the same
(`junit:PlaceTaxonNotesDbTest#testNamesReadOnceGiveTheSameRows`).

The AntCat list and Fossil Ants (Brian Fisher, 2026-11-11: "we want valid species first - that is the rule for all
lists, after that is with morpho - like in all the lists"). Their names are fossils or include fossils, so their Valid
and Curated count the fossil names (`StatusSet.OWN_SET_CHOICES`, `ownChoice`, `ownStatusSet`); the selector shows four
buttons:

| View (button) | Address value | Rows | Fossil Ants, species, 2026-11-11 |
|---|---|---|---|
| Valid | `valid` | the list's names of status valid, fossil or not | 856 ("856 Valid species (of 868 species, valid and curated)") |
| Curated (default) | `curated` | Valid + the morphotaxa on the list | 868 ("868 Species (valid and curated)"): 856 + 12 morphospecies |
| Valid extant | `valid extant` | valid names that are not fossils | 3 ("3 Valid extant Species") |
| Valid fossil | `valid fossil` | valid fossil names | 853 ("853 Valid fossil Species") |

Until 2026-11-11 the default was labelled "Valid (with fossils)"; since 2026-11-03 it listed the morphotaxa too, so it
was not a list of valid names, and there was no view of the valid names alone. Its rows are unchanged; it is now called
Curated, and the address value `Valid (with fossils)` still opens it, as does any value these lists do not offer.
`statusSet=valid` opened the default there and now opens Valid. The AntCat list has no morphotaxa: its Valid and
Curated list the same rows. Genera on Fossil Ants: Curated 258, with the morphogenera amblyoponine_genus8 and
ponerine_genus2. Tests: `StatusChoiceTest.testTheAntCatListAndFossilAntsHaveValidAndCurated`,
`PlaceViewTotalDbTest.testFossilAntsNumbersAreTheRowsOfItsOwnViews`, `e2e:test_fossil_page_counts.py`.

Genus rows (`ListView.GenusRows`): Species = the species and subspecies of the genus in the view, or "&ndash;"; Other
specimens = the specimens collected here under names not in the view (row 41). In Valid and Curated a genus known in
the place only from specimens identified to genus (indetermined, unidentifiable) is not a row; it is in All specimen
names (`ListView.keepGenus`); a morphogenus is in Curated only with a curated morphospecies. Red-flagged specimens never
count, in any view. Source column: an icon (specimen, curated, literature, AntCat): literature is Bolton's catalogue
only (list source literature); a row from the AntCat import (source worldants, the type localities) has its own label
"AntCat", hover "From the AntCat import" (Brian Fisher, 2026-11-02); a name with a specimen collected here shows specimen
even when it also has another entry (`ListView.sourceKind`). On a genus page in a place the line "Specimens collected
here not on the species list" stands above the notes on specimen and image counts.
"About this list" (closed) explains the views, the columns and the icons.

Old addresses map to the nearest view (`StatusSet.listChoice`): valid, valid extant, Valid (with fossils) -> Valid;
validmorpho (Valid + morphospecies of 2026-10-30), all determined, all, all indetermined, complete, type, a single status
-> All specimen names; fossil, valid fossil -> Fossil; specimen=true (Show Specimen Taxa, Names from) -> All specimen
names when no statusSet is given. The AntCat list (worldants) and Fossil Ants keep their own sets. Not changed: the
nightly counting rules and the place pages' numbers (row 22 still counts every morphospecies of the list; no view lists
exactly those rows). Tests: junit StatusChoiceTest, ListViewTest, PlaceTaxonNotesDbTest; e2e test_status_choice.py,
test_list_sources.py, test_numbers_lists.py.

Place page numbers and the views (Brian Fisher, 2026-11-04; claude/antweb-list-design-rules.md): each number of a place
page is the header of the list it opens. Subfamilies, Genera and Species / subspecies / morphospecies (rows 20 to 22)
are shown from stored totals, the rows the place's lists show in their default view (Curated; the AntCat list and Fossil
Ants: Valid with fossils), counted with the list page's own code and kept per place in `place_view_total`
(`PlaceViewTotalDb`; docs/flows/nightly.md "Place list totals"). A place without stored totals shows its own counts
(the rules of rows 20 to 22) and `data-view-counted="false"`, and the tests wait instead of comparing. The daily update
counts them (the places whose data changed; every place on Sundays). **2026-11-10**: they are also counted again, for
that one place, whenever one place's list is rebuilt between two daily updates (`PlaceViewTotalDb.recountPlace`): by the
"Update statistics" button of a place page and by the admin jobs for one place. Before, the button rebuilt the list and
left the totals, so the page and the list disagreed until the next daily update (staging 2026-11-10, after 2,215
Mexican specimens became Nearctic and "Update statistics" on the bioregion Nearctic: genus list 91 genera, page Genera
90; Indomalaya: species list 3,785, page Species 3,784; found by
`e2e:test_numbers_search.py::test_subtaxon_total_is_the_sum_of_the_rows_of_every_kind_of_place` and
`e2e:test_numbers_lists.py::test_list_headers_equal_place_numbers_of_every_kind`). Still pending until the next daily
update: a place whose list changes without a recount of the place (a save in the Species List Tool, a species list
upload, a specimen upload), and the other places that have a morphospecies a curator has just put on a list. Tests:
`junit:PlaceViewTotalDbTest#testOnePlaceIsCountedAgainAndNoOtherPlaceIsTouched`,
`junit:PlaceViewTotalDbTest#testEveryKindOfPlaceIsCountedByItsOwnKey`,
`junit:PlaceViewTotalDbTest#testAPlaceThatIsNotCountedIsLeftAlone`,
`junit:PlaceViewTotalDbTest#testAFailingCountLeavesNoWrongNumber`,
`e2e:test_place_recalc.py::test_admin_updates_a_country_and_an_adm1`,
`e2e:test_place_recalc.py::test_admin_updates_a_museum_a_project_a_bioregion_and_an_island`,
`unit:test_recalc_view_totals.py::test_update_statistics_counts_the_list_totals_in_every_branch`.

### Status choice of lists (Brian Fisher, 2026-10-30; replaced 2026-11-02 by the views above)

The status selector of lists (taxonomicPage.do, browse.do, images.do; "Species Status:" / "Genus Status:") offered
"all", "all determined", "all indetermined", "complete", "valid fossil", "valid extant", "type" and "Valid (with
fossils)". The labels matched no number on the place pages ("I have no idea what they mean"). Decision: four plain
choices, each matching a number of the place page (`StatusSet.LIST_CHOICES`):

| Choice (label) | Address value | Rows | Matches |
|---|---|---|---|
| Valid species | `valid` | status valid, not fossil | Valid species / subspecies (row 23) |
| Valid species + morphospecies | `validmorpho` | status valid or morphotaxon (`PlaceTotals.SPECIES_STATUSES`), species identified to genus | Species / subspecies / morphospecies (row 22); genus lists: Genera (row 21); subfamily lists: Subfamilies (row 20) |
| All names | `all` | the five counted statuses (valid, morphotaxon, indetermined, unidentifiable, unrecognized): every name with specimens | (genus and subfamily lists: Genera, Subfamilies) |
| Fossil species | `fossil` | status valid, fossil | statsPage Valid Fossil (row 19) |

Genus lists ("Genus Status") take the same choices on the genus rows (Valid genera, Valid genera + morphogenera, All
names, Fossil genera). Since 2026-10-30 (review of #307) the choices Valid and Valid + morphotaxa list the rows of the
place page's number of the rank by the number's own rule (`StatusSet.listRankCondition`, used by TaxaPage and by
browse.do's Family, Subfamily and Genus lists): genus rows `PlaceTotals.countsAsGenus` (no placeholders such as
"(myrmicinae)" or "amblyoponinae)", identified to genus), subfamily rows `PlaceTotals.countsAsSubfamily` (valid
subfamilies, no "(formicidae)"), species rows identified to genus; so the header of a genus list under Valid genera +
morphogenera equals the place's Genera, and that of a subfamily list equals its Subfamilies. A subfamily list's choice
Valid + morphotaxa is labelled "Valid subfamilies, extant and fossil" and its header "N Subfamilies (valid
subfamilies, extant and fossil)" (subfamilies have no morphotaxa); since 2026-10-30 (Brian Fisher) "extant and fossil"
only when the place's subfamilies of that choice include a fossil one (`TaxaPage.hasFossilSubfamilies`, request
attribute `StatusSet.FOSSIL_SUBFAMILIES_ATTR`), else "Valid subfamilies" and "8 Subfamilies (valid subfamilies)"
(Algeria); the images view header and the selector alike. Such a list (no fossil subfamily) offers no Valid +
morphotaxa choice (it would repeat "Valid subfamilies" and list the same rows; `StatusSet.choicesFor(name, rank,
fossilSubfamilies)`); an address asking for it still works, labelled "Valid subfamilies". All names lists every row, placeholders too. The
specimen half of a list (specimen=true, countries, adm1s, projects) takes the choice too (it took none), and the
specimen criteria of the sets valid extant, valid fossil and Valid (with fossils) (search, maps) filter by
specimen.status and the name's taxon.fossil (specimens have no fossil column); they fell through to the five counted
statuses before. "all determined", "all indetermined", "complete" and "type" are removed. Old addresses still work
(`StatusSet.listChoice`): valid extant and Valid (with fossils) -> valid; all determined -> validmorpho; all
indetermined, complete, type, worldants and a single status -> all; valid fossil -> fossil; an unknown value -> the
default. "type" never listed type taxa (its criteria were "1 = 1"); type specimens are found by search (types=on) and
the list's type column (orderBy=type), which do not use statusSet.

Defaults when the address has no statusSet (replaces the rule of 2026-10-29, "every counted status everywhere"):
country, adm1, region, subregion: Valid species; bioregion, museum: Valid species + morphospecies; curated project:
Valid species; All AntWeb (allantwebants) and other pages: Valid species + morphospecies. The AntCat list (worldants)
and Fossil Ants keep their own sets (until 2026-11-11: default Valid (with fossils); choices Valid (with fossils), Valid
extant, Valid fossil; since then the four views of "The AntCat list and Fossil Ants" in "Views of lists"). Since 2026-10-30 (review of #307) any other address value there (validmorpho or fossil from another list, a
single status, an unknown value) gives their default, and the selector's label is always one of their choices
(`StatusSet.ownChoice`); until then the value was used as it was ("validmorpho" listed the five counted statuses under
the label "Validmorpho").

The choice applies to the page it was made on and is kept in that page's links (the Overview, Taxa and Images tabs,
the selector, sort links), never in the session (2026-10-29). The list header says what it shows: "2,672 Valid species
(of 6,960 species and morphospecies)", "116 Genera (valid genera and morphogenera)". A species or subspecies page lists
the specimens of one name: the choice does not apply there and it shows no selector (with "valid extant" a
morphospecies page listed no specimens). Tests: junit StatusChoiceTest; e2e test_status_choice.py.

### 5.1 Answers of 2026-10-21 and the plan

| Q | Brian Fisher, 2026-10-21 | Change | Pages and API fields affected | Effort |
|---|---|---|---|---|
| Q1 subspecies | "maybe we should split it always: how many species and how many subspecies, so it is clear (is that possible)" | Yes, it is possible. Every "species / subspecies" number becomes two numbers, "N species, M subspecies", counted from the same list rows (taxarank species, taxarank subspecies). Stored: new columns `subspecies_count` and `valid_subspecies_count` on geolocale, project, museum, bioregion (db/upgrade, written by `PlaceTotals` with the totals; `species_count` then means species only), and `subspecies_count` on the four list tables for genus, subfamily and family rows (written by the crawl: `countChildren` split by rank). Checks: `inv:place.totals.species` and `inv:list.genus` compare both numbers. | Place pages: Species / subspecies, Valid species / subspecies, Endemic, Introduced; lists of places: Valid species column; taxonomicPage header (rank species: "N Species, M Subspecies"); genus, subfamily, family rows "N Species"; browse.do and images.do headers; home and statsPage "valid species + ssp.", "species + ssp. imaged" (statsPage Global Data, Bioregion and Museum Data are already split); API `/geolocales`, `/bioregions` `speciesCount` (+ new `subspeciesCount`, `validSubspeciesCount`), `/geolocaleTaxa`, `/bioregionTaxa` row `speciesCount` (+ `subspeciesCount`). The species-per-subfamily pie stays one pie (species and subspecies). | 2 days (schema, totals, crawl, 12 JSPs, API, checks, tests); API fields are added, the meaning of `speciesCount` changes (release note). |
| Q2 morphospecies and indet (replaced 2026-10-28: counting rules at the top of section 5) | "yes [they count as species] but we should indicate when it does" | Each count that includes names other than valid ones says so: "59 species (including 12 morphospecies, 3 indetermined, 1 unidentifiable, 2 unrecognized)" (zero parts left out). The parts are counted from the same list rows by `taxon.status` (one grouped query; stored as a small JSON column `species_status_json` on the place tables by `PlaceTotals`, so the page stays a read). Check: the parts add up to the total (`inv:place.totals.species`). | Place pages: Species (and Subspecies after Q1), Endemic for bioregions (they include morphospecies); taxonomicPage header with every counted status; genus rows "N Species" (a tooltip, not in the cell); home and statsPage "species + ssp. imaged"; API `/geolocales`, `/bioregions` (new `speciesByStatus`). | 1 day |
| Q3 endemic for regions and subregions | "native species found only in that region" | A name is endemic to a region or subregion when it is on the list of at least one of its countries and on no list of a valid country outside it, counting only native rows (rows not flagged introduced; Q4). Replaces `calcHigherEndemism`, which now flags only the names endemic to one of its countries. Same rule for countries (no other valid country) and adm1s (no other adm1 of any country); valid countries only (lists of invalid country rows no longer make a name non-endemic). Morphospecies and indet names (statuses morphotaxon, indetermined, unidentifiable) never count toward endemism, anywhere: countries, adm1s, regions, subregions and bioregions (Brian Fisher, 2026-10-22 05:28: agreed; a morphospecies code from one collection is not comparable with another's). Today countries and adm1s already leave out morphotaxon only (indetermined and unidentifiable count), and bioregions count all five statuses (`StatusSet.getAndCriteria(ALLANTWEBANTS)` in `BioregionDb.calcEndemic`): the rule becomes `status in ('valid', 'unrecognized')` in all endemic computations, so endemic flags and Endemic numbers of bioregions drop their morphospecies, and those of countries and adm1s their indet names. **Changed 2026-10-28 (Brian Fisher): `status = 'valid'`** (`PlaceTotals.ENDEMIC_STATUSES`): unrecognized names are no longer species and never count either. **Done 2026-10-28**: valid names only everywhere; subregions and regions by this rule (`GeolocaleDb.HIGHER_ENDEMIC_SQL`, replacing calcHigherEndemism's loops); a subregion's row flagged introduced (introduced in another of its countries) can still be endemic to it. Not done: "valid countries only" (country, adm1 and the higher rule still count the lists of every country row, valid or not, as before; a country without a valid subregion counts as outside every subregion). endemic.do and the bioregion page definition text ("including morphospecies") change with it. Check: `inv:place.endemic.flags` recounts regions and subregions too. | Place pages Endemic (regions, subregions, countries, adm1s, bioregions), endemic.do, API `endemicSpeciesCount`, statistics.do `geolocale_taxa_endemic` | 1 day |
| Q4 introduced and endemism | "NO" (introduced species never count toward endemism); 2026-10-22: morphospecies and indet names do not count either (Q3) | **Done 2026-10-28** (`PlaceTotals.nativeRow`; `calcIntroduced` now runs before `calcEndemic` in GeolocaleDb.updateCounts and BioregionDb.populate; after an upload `calcEndemic` uses the last nightly introduced flags). Endemism is computed after the introduced flags (until 2026-10-28 `calcEndemic` ran before `calcIntroduced`, in GeolocaleDb and BioregionDb) and counts only rows not flagged introduced: a species introduced in Reunion stays endemic to Madagascar, and an introduced row is never flagged endemic. Check: `inv:place.endemic.flags` adds the rule. | as Q3 | 0.5 day (with Q3) |
| Q5 fossils | "no" (not in place totals); "do we have a fossil page where someone can view just the fossils?" | Place totals (Subfamilies, Genera, Species, Specimens, pies) count `taxon.fossil = 0` only (`PlaceTotals.ANTS` gets `and t.fossil = 0`; the taxonomicPage header and statusSet "all" follow, or the header names the fossils apart). Place pages get a row "Fossil taxa: N" that links to the place's list with statusSet "valid fossil" (exists: `StatusSet.VALID_FOSSIL`). The fossil page exists: Global menu, project `fossilants` (project.do?name=fossilants): the valid fossil taxa from AntCat's species list (`SpeciesListUpload`: every fossil name goes on it) plus taxa curators added; its list uses statusSet "Valid (with fossils)", so only valid fossil names show (fossil morphospecies do not), with their genera and subfamilies (also extant genera that have a fossil species); its Specimens are all AntWeb specimens of those names. Improvements: say on the page what it lists (the header note says "Antcat fossil records and curator added records"); link each place's "Fossil taxa" to it; make sure it is live (recounted every night). Staging: `select project_name, is_live, scope, species_count, specimen_count from project where project_name = 'fossilants'`. | Place pages (Subfamilies, Genera, Species, Valid species already without fossils, Specimens, pies, new Fossil taxa row), lists of places, taxonomicPage header, API `/geolocales`, `/bioregions` counts | 1 day |
| Q6 label photos | "a specimen is not imaged, or a species is not imaged, if it has only label images" | This is the rule of the home page, place pages (Imaged specimens) and API /unimagedGeolocaleTaxa since 2026-10-21. Apply it to the rest: statsPage "Imaged Species" (B34), taxonomicPage "Unimaged" (B21: list `image_count` counts labels), browse.do "(M imaged)", search "(M imaged)", comparison pages (`getHasImages`), the worldants "Unimaged Valid Ants" list, the curator imaging progress views. Image counts ("N Images") keep counting every file, labels included, as labelled. Check: an e2e test per page with a label-only specimen on staging (zztest group). | statsPage, taxonomicPage, browse.do, images.do, comparison pages, search results, imaging progress | 1 day |
| Q10 adm1 without country (B26) | "yes [count for the state], but that is a data problem we need to fix and should be in the specimen reports"; "Reunion species should not count for France" | (a) The upload report gets two data problems for the curator: "Adm1 without a country" (none today: the adm1 check runs only when the country is given) and the existing "Not valid Antweb Adm1" for an adm1 that is not a state of its country. (b) B26: an adm1 takes the specimens with its country and its name (`s.country = parent and s.adm1 = name`) in the six places of section 3; specimens with an adm1 and no country still count for that adm1 only when its name belongs to exactly one country (Brian's "yes"), and are reported. (c) Islands: at upload a specimen of an island geolocale (Reunion) gets `country` = the island's `geolocale.country` and `island_country` = the island, and its adm1 is removed (`SpecimenUploadParse`); if Reunion's `country` is France, every Reunion specimen counts for France (country pages match `s.country`) and France's list gets Reunion's species (`insertGeolocaleTaxaFromSpecimens` adds rows for country and for island_country). Fix: country pages leave out specimens with an island_country (`and s.island_country is null`), the lists likewise. Staging queries: `select id, name, georank, parent, country, is_island, is_valid from geolocale where name in ('Reunion', 'France') or name like 'R%union%'`; `select country, island_country, adm1, count(*) from specimen where island_country like 'R%union%' or country like 'R%union%' or adm1 like 'R%union%' group by 1, 2, 3`. | Country and adm1 pages (Specimens, list rows, Images, Imaged specimens, pies), adm1 taxonomic pages, place downloads, API `/geoSpeciesStats`, the upload report; checks `inv:geo.adm1.country` (then 0) and a new check that no island specimen counts for a country | 1.5 days |
| Islands (2026-11-11) | "An oceanic island or archipelago with its own ant fauna is its own country in AntWeb. Its specimens never count toward the political parent. Test: would you put it in the same key as the parent?" (continental-shelf islands stay adm1s) | **Done 2026-11-11** for the flagged islands, Hawaii and Galapagos Islands: an island's specimen is stored with `specimen.country` = the island's own name (and `island_country`, the marker, unchanged), so every number of United States and Ecuador (rows 20 to 37, the lists, the downloads, the API's country filter, the extract tool) leaves the island out and the island's own numbers count each specimen once; subregions and regions count Hawaii in Polynesia and Oceania. The rules of the numbers themselves are unchanged (a country by `specimen.country`, an island by `island_country`). Endemic (row 24): a species found only on the island is now on the island's list alone and so endemic to it. Upload: `RecordPlace`; stored specimens: `tools/place/island_countries.py`; docs/flows/places.md, "Islands that are their own country". Tests: `junit:IslandCountryTest`, `junit:IslandParseLineTest`, `junit:IslandCountryDbTest`, `e2e:test_island_countries.py`. Not yet: Reunion, Mayotte and Rodrigues flagged as islands; the single Galapagos islands as adm1s; retiring `island_country`. |

### 5.2 Answers of 2026-10-22 (Q7, Q8, Q9, Q11, Q12)

| Q | Brian Fisher, 2026-10-22 | Change (code, page wording, check) | Visible after |
|---|---|---|---|
| Q7 ants only | "ants only everywhere": statsPage Global Data, Bioregion Data, Museum Data and the home "valid species + ssp." count Formicidae only, as the place pages; names not identified to genus never count as species there | One rule, `PlaceTotals.validAntTaxonCondition` / `validAntSpeciesCondition` / `isValidAntSpecies`: StatisticsDb (`globalCountSql`, `listCountSql`, `VALID_SPECIES_SQL`; also "species + ssp. imaged" and statistics.do "V. Sp. Imaged") and TaxonMgr (statsPage first number). statsPage says under each heading that it counts ants (Formicidae) only; the home numbers explain themselves on hover. Checks: `inv:stats.global`, `inv:stats.bioregions`, `inv:stats.museums` recount ants only; `inv:stats.nonants` compares the page's rule with an independent recount; `inv:home` and `inv:admin.statistics` report a stored number that still equals the old rule as pending. Home "specimen records", "specimens imaged" and "total specimen images" still count every record (not part of the decision). | statsPage at once (live); home after the next nightly run (set 4) or upload |
| Q8 upload groups | Species = distinct full names (taxon_name) of species and subspecies of the five counted statuses (since 2026-10-28: valid and morphotaxon only, the counting rules), ants only, identified to genus; Totals row = distinct over all groups combined; Subfamilies and Genera totals the same way | `GroupCountDb.countColumns`: one rule for the nightly recount (set 3), each upload's own row (`UploadDb.getCounts`) and the check. Subfamilies = valid ant subfamilies, Genera = genera identified to genus, of the counted specimens (any of the five statuses; were valid names of AntWeb's extant genera). Totals: group_count row 0 (`GroupCountDb.ALL_GROUPS`) = the specimens of every listed group together; groups.do shows it for Collections, Localities, Subfamilies, Genera, Species ("-" until the first nightly count); Uploads, Specimens, Ungeoreferenced, Flagged stay sums of the rows. groups.do and group.do explain the rule. Check: `inv:admin.groups` compares every group and the Totals row with a recount; numbers still by the old rule are pending until the nightly run. | groups.do, group.do after the next nightly run (set 3) |
| Q9 orphaned images | "N images of M specimen codes that are not in AntWeb" (count both) | `OrphanedImages.COUNTS_SQL` and `CODES_SQL`; the page says both numbers and what an orphaned image is. Check: `inv:stats.images` compares both with a recount. | at once |
| Q11 Port of Entry | Keep `poeants` ("Port of Entry", 212 names) as a species list of species recorded at ports of entry; drop its specimen and image numbers (page totals and per-row columns); the page says these species were intercepted at ports of entry, are not established introductions, and are not counted in any place total or the introduced species list; no nightly step recounts its numbers; the check skips them with a note | `Project.isSpeciesListOnly` / `SPECIES_LIST_ONLY_SQL`: ProjTaxonCountDb (recount, curated crawl, image crawl), ProjectDb (updateCounts, finish, makeCharts) and PlaceImageCountDb leave it out and set its specimen and image numbers to 0 (list counts of Subfamilies, Genera, Species, Valid species are kept); project page: an "About this list" row instead of Specimen holdings and Image documentation, no Specimens pie; its list: no Specimens, Species, Genera or Images columns, the note under the list; projects.do: "-". Nothing else uses the project: place totals count geolocale, museum and bioregion lists, and the introduced flags come from the introduced-species list. Check: `CountInvariants.scopeSql` and the rotating sample leave it out; the report says so (`SPECIES_LIST_ONLY_NOTE`). | page text at once; numbers set to 0 by the next nightly run (set 3) |
| Q12 unimaged | "Unimaged" always means no photo of the ant anywhere in AntWeb; label photos never count | `StatisticsDb.antImagedNames` (the rule of the API's `imagedTaxaSql`): taxonomicPage.do?isImaged=false (header "N Unimaged X (out of M)", 0 included, and the list) with a note under the header; the API page says what unimaged means. The images view's per-place list is labelled "X with no image from this place (N)" (row 62). | at once |


### 5.3 Search rules decided by Brian Fisher 2026-10-27 (rows 67, 68)

| Decision | Change | Rows | Visible after |
|---|---|---|---|
| Multi-word search values: phrase match; "equals": exact match | A box value is a list of entries separated by commas, each entry one phrase (before: every word was a separate entry, so "Santa Cruz" counted the specimens of Santa or Cruz); "not equals" excludes every entry (before: matched every specimen); taxon name "equals" matches the whole name or its last part (before: a suffix, "rufa" also counted "pseudorufa"). The N specimens and N taxa of a search with such a value are smaller (they now count only what the value says). Details: [flows/search.md](flows/search.md#search-rules-decided-on-2026-10-27). Test: `junit:SearchRulesTest#testSeveralWordsAreOnePhrase`, `junit:SearchRulesTest#testNotEqualsExcludesEveryEntry`, `junit:SearchRulesTest#testTaxonNameEqualsIsNotASuffix` | 67, 68 | at once |
| Searches over 50,000 specimens: one fixed order | The first 50,000 by specimen code, every time (before: every other search reversed the order, so a by-taxon or map search of more than 50,000 specimens counted two different sets in turn). Test: `unit:test_decided_pages.py::test_search_has_one_fixed_order` | 68 | at once |
