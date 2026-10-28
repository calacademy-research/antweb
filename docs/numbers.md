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
| `inv:<id>` | CountInvariants (`src/org/calacademy/antweb/util/CountInvariants.java`): for **every** place of every kind, the stored number against the number it must equal or not exceed, with grouped queries (one per check for all places). Since 2026-10-22 also NumberChecks (`NumberChecks.java`, ids `stats.*`, `admin.*`, `api.taxa`, `list.images`, `place.endemic.flags`, `taxon.parents`, `geo.adm1.country`): the statsPage tables, the admin pages, the API's taxon counts, the Images column of every list, the endemic flags, and the suspects B25 and B26; the value a page shows (computed by the page's own code, or read from LoginMgr and ArtistMgr as the pages read it) against an independent recount. | `countCheck.do` (admins, read-only); on staging in the end-to-end suite (`tests/e2e/test_count_check.py`) on every pull request and every day; on production read-only every day. Database behaviour: `CountInvariantsTest`, `NumberChecksTest` (CI, MySQL 8.4). |
| `cc:<check>` | CountCheck (`CountCheck.java`): recount from the specimen and image records for a sample of places: Madagascar, Reunion, the country with the most morphospecies, the subregion of Madagascar, the largest live project, CASC, Malagasy, plus a rotating slice so every country, subregion, region, project and museum is recounted within a week and every live adm1 within a month (`CountCheckAction.rotatingSample`). | same page and tests; `CountCheckTest` |
| `junit:<Class>#<method>` | JUnit test against MySQL 8.4 in CI (`test/`). | CI "Tests" |
| `e2e:<file>::<function>` | end-to-end test of the page on staging (`tests/e2e/`). Since 2026-10-22 the count check's report carries its recounts as JSON (`id="count-check-expected"`), and `test_count_check.py` compares statsPage, curators.do, curator.do, artists.do, groups.do, group.do, listImageUploads.do and orphanedImages.do with them; `test_numbers_live.py` compares live numbers (list headers, browse, images, comparisons, search, collection, locality, endemic.do) with the Madagascar place page, whose numbers the check validates; `test_api_numbers.py` the API's counts with what it returns. | e2e workflow |
| `unit:<file>::<function>` | unit test (`tests/unit/`). | CI "Tests" |
| rotating sample (e2e) | since 2026-10-26, `test_numbers_endemic.py`, `test_numbers_lists.py`, `test_numbers_species.py` check numbers that are shown on every place, list row or species page on a sample chosen from the day (`tests/e2e/numbers_sample.py`): every kind of place the number is shown on (country, adm1, region, subregion, bioregion, museum, project) on every run, a different place of each kind (and genus, species or name within it) each day, so that over the days every one is checked; the places are found on the site (API `/geolocales`, `/bioregions`, museums.do, projects.do). The numbers are compared with numbers the count check validates on every place (list rows, place totals) or recounted independently from the API's records (endemism by name, unimaged names, specimens with images, habitat summaries). A failure names the day; `ANTWEB_SAMPLE_DAY` repeats it. | e2e workflow |

A difference the check finds is **real** (the site shows a wrong number) or **pending** (something changed after
the last nightly run: an upload, new images, a list edit, a name update by hand; or the number's rule changed on
2026-10-21 and no nightly run has counted it that way yet). The end-to-end test fails on real differences.

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

## 2. Inventory

Coverage after 2026-10-27: **120 validated, 0 partly, 0 not yet** of 120 (2026-10-27: row 120, imaged castes, added with Brian Fisher's decision on images from anywhere, section 3; rows 19, 28, 44, 45, 49, 55 to 61, 64 to 71, 83, 94 to 96, 98, 101, 102, 105 to 107, 110 to 113, 116 to 118 by end-to-end tests, findings B45 to B64; 2026-10-26: 81, 38, 0, rows 24, 25, 38, 39, 46, 47, 50 to 53, 78,
79 by end-to-end tests on a rotating sample, section 1; 2026-10-22: 69, 50, 0 of 119, row 119, Port of Entry, added with
Brian Fisher's answers of 2026-10-22, section 5.2; earlier on 2026-10-22: 67, 51, 0 of 118; 2026-10-21: 42, 22, 54; first
inventory 2026-10-20: 9, 7, 102).

| # | Page | Number | How it is computed | Status | Test |
|---|---|---|---|---|---|
| 1 | home page `/` (genInc/statistics.jsp) | N valid species + ssp. | statistics row (StatisticsDb.populateStatistics): valid ant (Formicidae) species and subspecies identified to genus, fossils included (Q7, 2026-10-22: was every family; `PlaceTotals.validAntSpeciesCondition`); explained on hover and on statsPage; written after every specimen upload and, since 2026-10-21, every night (set 4) | yes | inv:home; junit:NumberDecisionsDbTest#testStatsPageAndHomeCountAntsIdentifiedToGenusOnly |
| 2 | home page `/` (genInc/statistics.jsp) | N specimen records | statistics row: all specimen records | yes | inv:home |
| 3 | home page `/` (genInc/statistics.jsp) | N species + ssp. imaged | statistics row: ant species and subspecies names (valid names and morphospecies since 2026-10-28, the counting rules of section 5; before: the five counted statuses; identified to genus; ants only since 2026-10-22, Q7) with a specimen that has an image of the ant (not a label); was genus/species/subspecies strings, labels and every status counted | yes | inv:home; junit:PlaceTotalsDbTest#testHomeNumbersCountImagesOfTheAnt |
| 4 | home page `/` (genInc/statistics.jsp) | N specimens imaged | statistics row: specimens with an image of the ant (labels not counted, as Imaged specimens on place pages) | yes | inv:home; junit:PlaceTotalsDbTest#testHomeNumbersCountImagesOfTheAnt |
| 5 | home page `/` (genInc/statistics.jsp) | N total specimen images (+ N labels since 2026-10-27) | statistics row: all image rows; since 2026-10-27 images of the ant (StatisticsDb.TOTAL_IMAGES_SQL), label images shown apart ("+ N labels", LABEL_IMAGES_SQL); until then every image row; a row written before the next nightly is pending (CountInvariants.ANT_IMAGES_PENDING) | yes | inv:home; unit:test_image_counts_defined.py::test_specimen_counts_show_labels_apart |
| 6 | statsPage.do | N valid species + ssp. | TaxonMgr.getValidSpeciesCount(): valid species and subspecies in memory, ants identified to genus (`PlaceTotals.isValidAntSpecies`, Q7 2026-10-22; was: valid taxa of every rank, then of every family) | yes | inv:home; junit:PlaceTotalsDbTest#testStatsPageCountsValidSpeciesAndSubspecies; junit:NumberDecisionsDbTest#testStatsPageAndHomeCountAntsIdentifiedToGenusOnly; e2e:test_numbers_pages.py::test_statspage_valid_species_count_species_and_subspecies |
| 7 | statsPage.do | N specimen records | AntwebMgr, from the latest statistics row (= #2); the text under it, "from AntWeb's N collaborating institutions", counts the museums of the Museums list (`MuseumMgr.getMuseumCount`, as museums.do; B73, decided by Brian Fisher 2026-10-27; was the fixed text "21") | yes | inv:home; junit:CountRules20261027Test#testInstitutionsAreTheMuseumsListed; e2e:test_home_and_stats.py::test_statistics_page_institutions_are_the_museums_listed |
| 8 | statsPage.do | N species + ssp. imaged | AntwebMgr (= #3) | yes | inv:home; inv:stats.imaged |
| 9 | statsPage.do | N specimens imaged | AntwebMgr (= #4) | yes | inv:home |
| 10 | statsPage.do | N total specimen images (+ N labels since 2026-10-27) | AntwebMgr (= #5); = #5; the labels counted live when the server starts and by the nightly (AntwebMgr.getLabelImagesCount) | yes | inv:home |
| 11 | statsPage.do | Global Data, Extant: valid subfamilies, genera, species, subspecies | live count of the taxon table, ants only, species identified to genus (StatisticsDb.globalCountSql, `PlaceTotals.validAntTaxonCondition`; Q7 2026-10-22: was every family); said under the heading | yes | inv:stats.global; junit:NumberDecisionsDbTest#testStatsPageAndHomeCountAntsIdentifiedToGenusOnly; e2e:test_count_check.py::test_statspage_tables_equal_the_recount; e2e:test_numbers_pages.py::test_statspage_valid_species_count_species_and_subspecies; e2e:test_numbers_pages.py::test_statspage_says_it_counts_ants_only |
| 12 | statsPage.do | Global Data, Fossil (4) | live count, ants only (as #11) | yes | inv:stats.global; e2e:test_count_check.py::test_statspage_tables_equal_the_recount; e2e:test_numbers_pages.py::test_statspage_valid_species_count_species_and_subspecies |
| 13 | statsPage.do | Images table: Total per specimen status | live (ImageDb.getImageStats); since 2026-10-27 images of the ant; label images in their own column (Label images) | yes | inv:stats.images; e2e:test_count_check.py::test_statspage_tables_equal_the_recount; junit:PlaceTotalsDbTest#testStatsPageImageTableAddsUp; junit:PlaceTotalsDbTest#testStatsPageImageTableAddsUp |
| 14 | statsPage.do | Images table: Worker / Male / Queen / Other | live; Other includes images of specimens without a caste (2026-10-21; they were in no column); the caste of a specimen is read from its Life Stage field (`Caste.getCasteValues`; B69, 2026-10-27: "alate male" is a male, "and" and "/" split without a comma; existing specimens after their next upload or a calcCaste run, the counts after the next nightly run); since 2026-10-27 images of the ant | yes | inv:stats.images; e2e:test_count_check.py::test_statspage_tables_equal_the_recount; junit:PlaceTotalsDbTest#testStatsPageImageTableAddsUp; junit:CountRules20261027Test#testAlateMaleIsAMale; junit:CountRules20261027Test#testAndAndSlashSplitWithoutAComma |
| 15 | statsPage.do | Images table: Sum row | summed in the JSP over every status in the table (was six fixed statuses); images of specimens without a status are in no row; since 2026-10-27 with the Label images column; Sum + labels + images of specimens without a status = all images minus orphan images | yes | inv:stats.images; e2e:test_count_check.py::test_statspage_tables_equal_the_recount; junit:NumberChecksTest#testStatsPageImagesSumLeavesOutSpecimensWithoutStatus |
| 16 | statsPage.do | Imaged Species per taxon status | live (ImageDb.getImageTaxonStats): names of any rank with a specimen that has any image (labels too), by taxon status; only seven statuses shown; since 2026-10-27 names with a specimen that has an image of the ant (label images did count) | yes | inv:stats.imaged; e2e:test_count_check.py::test_statspage_tables_equal_the_recount |
| 17 | statsPage.do | Bioregion Data (4 per bioregion) | live over bioregion_taxon, ants only (StatisticsDb.listCountSql; Q7 2026-10-22: was every family, B31) | yes | inv:stats.bioregions; inv:stats.nonants; junit:NumberChecksTest#testStatsPagePlaceQueriesMatchThePageCode; junit:NumberChecksTest#testStatsPageBioregionAndMuseumData; e2e:test_count_check.py::test_statspage_tables_equal_the_recount |
| 18 | statsPage.do | Museum Data, Valid Extant (4 per museum) | live over museum_taxon, ants only (as #17) | yes | inv:stats.museums; inv:stats.nonants; junit:NumberChecksTest#testStatsPagePlaceQueriesMatchThePageCode; junit:NumberDecisionsDbTest#testStatsPageAndHomeCountAntsIdentifiedToGenusOnly; e2e:test_count_check.py::test_statspage_tables_equal_the_recount |
| 19 | statsPage.do | Museum Data, Valid Fossil (4 per museum) | live over museum_taxon, ants only; since 2026-10-27 compared end-to-end with the headers of the museum's lists with statusSet valid fossil (TaxaPage, another code path) on a daily sample of museums (two with fossils, one without) | yes | inv:stats.museums; inv:stats.nonants; junit:NumberChecksTest#testStatsPagePlaceQueriesMatchThePageCode; e2e:test_count_check.py::test_statspage_tables_equal_the_recount; e2e:test_numbers_search.py::test_statspage_valid_fossil_equals_the_museum_lists |
| 20 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Subfamilies | subfamily rows on the place's list, counted status (PlaceTotals.listedCountSql); museums and bioregions were roll-up sums until 2026-10-21; a curated project (not worldants, allantwebants) counts its subfamily rows of every status, as its list rows (`PlaceTotals.listedCondition`; B67, decided by Brian Fisher 2026-10-27; after the next nightly run); 2026-10-28 (counting rules, section 5): a subfamily counts when a specimen is identified at least to it (its row is on the list); the rule of the rows is unchanged | yes | inv:place.totals.subfamily; cc:place totals; junit:PlaceTotalsDbTest#testFinishWritesEveryTotalFromTheList; junit:CountRules20261027Test#testCuratedProjectSubfamiliesCountEveryStatusAsTheirListRows; junit:CountRules20261028Test#testGeneraAndSubfamiliesCountRowsOfEveryCountedStatus |
| 21 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Genera | genus rows on the list, counted status (PlaceTotals); was sum(genus_count) of the subfamily rows; 2026-10-28 (counting rules, section 5): a genus counts when a specimen is identified at least to genus, so a genus known only from "Lioponera indet" counts (its row is on the list; the rule of the rows is unchanged) | yes | inv:place.totals.genus; junit:PlaceTotalsDbTest#testFinishWritesEveryTotalFromTheList; junit:CountRules20261028Test#testGeneraAndSubfamiliesCountRowsOfEveryCountedStatus; junit:PlaceTotalsDbTest#testSpecimensCountAtEachRankTheyAreIdentifiedTo |
| 22 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Species / subspecies | species and subspecies rows on the list of status valid or morphotaxon (`PlaceTotals.SPECIES_STATUSES`, counting rules of 2026-10-28, section 5; until then the five counted statuses: indetermined, unidentifiable and unrecognized names counted), identified to genus, ants (PlaceTotals); was sum(species_count) of the genus rows; the definition on the page says so; the numbers follow the rule after the next nightly run (pending until compute.log has the line `CountInvariants.COUNTING_RULES_LOG`) | yes | inv:place.totals.species; junit:PlaceTotalsDbTest#testFinishWritesEveryTotalFromTheList; junit:CountRules20261028Test#testSpeciesAreValidNamesAndMorphospeciesOnly; junit:PlaceTotalsDbTest#testSpecimensCountAtEachRankTheyAreIdentifiedTo |
| 23 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Valid species / subspecies | valid extant species and subspecies rows on the list (PlaceTotals.validSpeciesSql); geolocales: written every night since 2026-10-21 (GeolocaleDb.finish was never called) | yes | inv:place.totals.valid; junit:PlaceTotalsDbTest#testFinishWritesEveryTotalFromTheList |
| 24 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Endemic | rows flagged is_endemic on the list (PlaceTotals.countsFromFlagsSql); the flags by calcEndemic (geolocales: set 2 and after uploads; bioregions: set 3); the count loop was off by one at each place change; end-to-end since 2026-10-26: the page number = endemic.do on every bioregion and a daily sample of countries and adm1s, and the names of endemic.do recounted by the rule from the API (bioregion flags are recounted only there); since 2026-10-27 only flagged names with a counted status, no synonyms, as the lists (`PlaceTotals.flaggedCondition`; B70, decided by Brian Fisher 2026-10-27); **since 2026-10-28 valid names only** (Brian Fisher 2026-10-22 and 2026-10-28, Q3: morphospecies, indetermined, unidentifiable and unrecognized names never count toward endemism, at any place; `PlaceTotals.ENDEMIC_STATUSES`, used by every endemism rule, `flaggedCondition` and the check), counted on native rows only (Q4: computed after the introduced flags, rows flagged introduced neither make a name non-endemic nor are flagged endemic; `PlaceTotals.nativeRow`), and subregions and regions have the names whose native country rows are all in their countries (Q3, `GeolocaleDb.HIGHER_ENDEMIC_SQL`; until then only the names endemic to one of their countries); Endemic <= Valid species; the lists change at once, the stored numbers and flags at the next nightly run (geolocales set 2, bioregions set 3) | yes | inv:place.totals.endemic; junit:PlaceTotalsDbTest#testEndemicNumbersAreTheFlaggedRows; junit:PlaceTotalsDbTest#testEndemismCountsValidNativeNamesOnly; junit:PlaceTotalsDbTest#testBioregionEndemismCountsValidNativeNamesOnly; junit:CountRules20261028Test#testEndemicCountsValidNamesOnly; junit:CountRules20261028Test#testEveryEndemismRuleCountsValidNativeRows; junit:CountRules20261028Test#testRegionsAndSubregionsHaveTheNamesFoundOnlyInThem; inv:place.endemic.flags; e2e:test_numbers_live.py::test_endemic_and_introduced_lists_equal_place_numbers; e2e:test_numbers_endemic.py::test_endemic_and_introduced_numbers_equal_their_lists; e2e:test_numbers_endemic.py::test_endemic_names_follow_the_endemism_rule; e2e:test_numbers_endemic.py::test_endemic_and_introduced_marks_on_the_species_list_agree; e2e:test_numbers_endemic.py::test_endemic_names_of_countries_are_named_species; junit:CountRules20261027Test#testEndemicAndIntroducedListsAndNumbersShareTheStatusRule |
| 25 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Introduced | rows flagged is_introduced on the list (PlaceTotals.countsFromFlagsSql); end-to-end since 2026-10-26: the page number = introduced.do on every bioregion and a daily sample of countries and adm1s, and introduced.do = the names the species list marks Introduced (computed live from the native bioregions) that are on the introduced-species list; B41 (a place without a bioregion) xfail; since 2026-10-27 only flagged names with a counted status, no synonyms, as the lists (`PlaceTotals.flaggedCondition`; B70, decided by Brian Fisher 2026-10-27; after the next nightly run) ; regions and subregions (Brian Fisher, 2026-10-28): counted the way Endemic is for them, a name flagged introduced on a country's list is flagged on its subregion's list, and a subregion's on its region's (`GeolocaleDb.calcHigherIntroduced`; until then 0); their pages show no Endemic or Introduced row (the numbers are in the API and introduced.do) | yes | inv:place.totals.introduced; e2e:test_numbers_live.py::test_endemic_and_introduced_lists_equal_place_numbers; e2e:test_numbers_endemic.py::test_endemic_and_introduced_numbers_equal_their_lists; e2e:test_numbers_endemic.py::test_endemic_and_introduced_marks_on_the_species_list_agree; e2e:test_numbers_endemic.py::test_a_place_without_a_bioregion_has_no_introduced_species; junit:CountRules20261027Test#testEndemicAndIntroducedListsAndNumbersShareTheStatusRule; junit:CountRules20261028Test#testRegionsAndSubregionsCountIntroducedAsTheyCountEndemic |
| 26 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Specimens | every kind of place: the sum of the species and subspecies rows' specimens (projects: list recounted first since 2026-10-21); museums and bioregions since 2026-10-28 (Brian Fisher: like countries; `PlaceTotals.writeListedSpecimens`): their specimens of a counted status filed under a name of their list or a synonym of one; until then all their ant specimens (specimen.museum, specimen.bioregion), any status, any name; the count check recounts them (`CountCheck.checkListPlaceSpecimens`) | yes | inv:place.totals.specimens; inv:geo.regions; cc:place specimens; junit:CountInvariantsTest#testRecountOfASampledPlaceChecksItsImagesColumn; inv:geo.adm1.country; junit:CountRules20261028Test#testMuseumAndBioregionSpecimensAreTheSumOfTheirListRows; junit:PlaceTotalsDbTest#testSpecimensCountAtEachRankTheyAreIdentifiedTo |
| 27 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Imaged specimens | PlaceImageCountDb (set 4): specimens with an image that is not a label; museums, bioregions and projects since 2026-10-28 (Brian Fisher: images follow the Specimens rule): the images of the specimens of their list rows, a synonym's under the valid name (`PlaceImageCountDb.listSource`; museums and bioregions: counted status, as their Specimens; before: every ant specimen of the museum or bioregion) | yes | cc:place images; inv:place.images; inv:place.images.order; inv:geo.adm1.country; junit:PlaceImageCountDbTest#testImagesOfASynonymCountUnderTheValidName |
| 28 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | (N additional specimens) label only | PlaceImageCountDb: specimens whose only images are labels; = specimens with any image minus Imaged specimens on every place (inv:place.images), Imaged specimens recounted by cc:place images; since 2026-10-27 the specimens with any image (and the Images) recounted from the API on a different museum every day (one with at most 3,000 specimens with images) | yes | inv:place.images; e2e:test_numbers_images.py::test_label_only_specimens_are_the_specimens_with_only_label_images |
| 29 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Image files (labelled Images until 2026-10-27) | PlaceImageCountDb: all image files (labels included); museums, bioregions and projects since 2026-10-28 (Brian Fisher: images follow the Specimens rule): the images of the specimens of their list rows, a synonym's under the valid name (`PlaceImageCountDb.listSource`; museums and bioregions: counted status, as their Specimens; before: every ant specimen of the museum or bioregion) | yes | cc:place images; inv:place.images; inv:place.images.order; inv:geo.adm1.country; junit:PlaceImageCountDbTest#testImagesOfASynonymCountUnderTheValidName |
| 30 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Images (labelled Ant images until 2026-10-27) | PlaceImageCountDb: images that are not labels; museums, bioregions and projects since 2026-10-28 (Brian Fisher: images follow the Specimens rule): the images of the specimens of their list rows, a synonym's under the valid name (`PlaceImageCountDb.listSource`; museums and bioregions: counted status, as their Specimens; before: every ant specimen of the museum or bioregion) | yes | cc:place images; inv:place.images; junit:PlaceImageCountDbTest#testImagesOfASynonymCountUnderTheValidName |
| 31 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Views: N head, profile, dorsal, ventral, field, other | PlaceImageCountDb: images per shot type | yes | cc:place images; inv:place.images |
| 32 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Label images | PlaceImageCountDb: shot type l | yes | cc:place images; inv:place.images |
| 33 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Imaged specimens / Image files before set 4 ran once | place table image_count, imaged_specimen_count | yes | cc:place images; inv:place.images |
| 34 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Species per Subfamily pie | PlaceTotals.speciesPieSql: the rows of Species by subfamily (valid names and morphospecies since 2026-10-28) (geolocales counted rank species only; projects and bioregions every rank) | yes | inv:place.pies; junit:PlaceTotalsDbTest#testPiesOfMuseumsAndBioregionsCountTheirOwnSpecimens |
| 35 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Specimens per Subfamily pie | PlaceTotals.specimenPieSql: the species rows' specimens (museums and bioregions since 2026-10-28, as their Specimens; before: their own specimens, which were every specimen in AntWeb of their names until 2026-10-21); one Other slice for names that are not a subfamily; fitted to its column, a chart never stops an upload | yes | inv:place.pies; junit:PlaceTotalsDbTest#testPiesOfMuseumsAndBioregionsCountTheirOwnSpecimens |
| 36 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Taxa menu N Species | = #22 (getSpeciesCount()) | yes | inv:place.totals.species |
| 37 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Admin: species count link | = #22 | yes | inv:place.totals.species |
| 38 | taxonomicPage.do, browse.do | List header N [status] Species / Genera / Subfamilies | live: rows shown (session status set); since 2026-10-28 a species list of mixed statuses (all, the default of places) shows valid names and morphospecies only, as Species counts them (`TaxaPage.hidesNonSpecies`), and says under the list how many specimens identified only to genus it leaves out, by genus, with a link to the place's specimens (`GenusOnly`); since 2026-10-26 checked on a different place of every kind every day (country, adm1, region, subregion, bioregion, museum, project) against the place page, and against the rows listed | yes | e2e:test_numbers_live.py::test_list_headers_equal_place_numbers; e2e:test_numbers_lists.py::test_list_headers_equal_place_numbers_of_every_kind; e2e:test_numbers_lists.py::test_species_lists_show_valid_names_and_morphospecies_only; junit:CountRules20261028Test#testSpeciesListsLeaveOutNamesThatAreNotSpecies; junit:CountRules20261028Test#testGenusOnlySpecimensAreNamedOnGenusAndSpeciesLists |
| 39 | taxonomicPage.do, browse.do | N Unimaged X (out of M) | live: the children with no photo of the ant anywhere in AntWeb (`StatisticsDb.antImagedNames`, the API's rule; Q12 2026-10-22: was no image from this place, labels included); 0 is shown too (B21); said under the header; since 2026-10-26 the names listed = the API /unimagedGeolocaleTaxa names of the list (the rule written twice), Madagascar and a country a day | yes | junit:NumberDecisionsDbTest#testUnimagedMeansNoPhotoOfTheAntAnywhere; e2e:test_numbers_live.py::test_unimaged_header_is_within_the_list; e2e:test_numbers_pages.py::test_unimaged_list_says_anywhere_in_antweb; e2e:test_numbers_lists.py::test_unimaged_names_equal_the_api |
| 40 | taxonomicPage.do, browse.do | Species row N Specimens (place column) | list row specimen_count (nightly crawl); B25 (a subspecies under its species replaces the species' row) checked by taxon.parents | yes | cc:specimens; inv:taxon.parents |
| 41 | taxonomicPage.do, browse.do | Genus row N Species | list row species_count: its species and subspecies rows of status valid or morphotaxon (2026-10-28; before: a counted status; every list, curated projects too); a genus with none says "No Species" and, when the place has specimens identified to genus only, "N specimens identified to genus only", linked to them (`GenusOnly`) (country and adm1 genus rows were rewritten after the count by set 2's geolocaleTaxonFix with every row under the genus, any status or rank, until 2026-10-22) | yes | inv:list.genus; cc:species; inv:taxon.parents; junit:CountRules20261028Test#testGenusRowsCountTheSameSpeciesOnEveryKindOfList |
| 42 | taxonomicPage.do, browse.do | Subfamily row N Genera | list row genus_count: its genus rows | yes | inv:list.subfamily; cc:genera |
| 43 | taxonomicPage.do, browse.do | Global N Specimens | allantwebants proj_taxon specimen_count: all specimens of the name; rebuilt after uploads and, since 2026-10-21, after the nightly name update | yes | inv:global.specimens; inv:list.global; cc:global; junit:PlaceTotalsDbTest#testGlobalIsRecountedAfterTheNameUpdateMovedSpecimens |
| 44 | taxonomicPage.do, browse.do | Images column: N Images, the images of the name in AntWeb ("N Images", the place's number only, until 2026-10-27; "T Images (P from X)" from 2026-10-27 to 10-28) | One number (decided by Brian Fisher 2026-10-28: "153 Images (0 from Comoros)" wrapped to two lines, and the number script blanked the 0, "( from Comoros)"): the images of the ant of the name in AntWeb, from any specimen; labels never count (decided by Brian Fisher 2026-10-27; until then every image file, labels included); the definition under the list says so and, on a place's list, that the name's images page (the link) shows the place's specimens first (that page says how many imaged specimens are from the place, taxonChildImages.jsp). N = the allantwebants list row's image_count, every rank (set 4, `getTaxonImageCountQuery`: the images of the ant, `StatisticsDb.ANT_IMAGES_OF_SPECIMEN`; a genus is the sum of its names by the taxon tree), read with the place's row (`TaxonSet.globalImageCountColumn`, one key lookup per row), so it changes after the nightly run; set 4 counts the allantwebants rows with the live projects since 2026-10-27 (`ProjTaxonCountDb.imageCrawlProjects`; they were set to 0 with the project rows and never counted again; pending in the end-to-end test until compute.log has the line "AntWeb-wide list Images counted"); PR #291's second staging run counted species rows live from the image table instead, one count per row, and list, taxon and browse pages came back cut off on staging, so that was taken out. The place's own list row image_count (set 4, the same rule; project rows reset first since 2026-10-21) is no longer shown in the cell; it is used only when the AntWeb-wide number is not known or lower (`ImagesAnywhere.imagesColumn`: the larger of the two), it is still checked by the count check (cc:images) and the API (rows 112, 116). Sorting by Images follows the number shown (`ImagesAnywhere.shownImages`, `SortTaxaByImages`). Not taxon.image_count (row 54, every image file, labels included); "No Images" only when N = 0; N recounted from the API's records of every specimen of the name (/taxaImages without label shots) on species rows of the day's genus of the day's place of every kind (test_numbers_images.py) and of Comoros and the day's place of every kind (test_images_anywhere.py); the numbers follow the ant-only rule after the first nightly run with it (until then the count check calls their differences pending: compute.log has no "list Images counted as ant images" line) | yes | cc:images; inv:list.images; e2e:test_numbers_images.py::test_list_images_column_equals_the_api_recount; e2e:test_images_anywhere.py::test_list_images_column_shows_the_images_in_antweb; junit:ImagesAnywhereTest#testImagesColumnShowsTheAntWebNumberOnly; junit:ImagesAnywhereTest#testImagesColumnFallsBackToThePlaceNumber; junit:ImagesAnywhereTest#testNoImagesOnlyWhenThereIsNoImageInAntWeb; junit:ImagesAnywhereTest#testEveryRankReadsTheAllAntwebRow; junit:ImagesAnywhereTest#testImagesColumnOfARow; junit:ImagesAnywhereTest#testReadGlobalImageCountNeverThrows; junit:ImagesAnywhereTest#testSetFourCountsTheAllAntwebRows; junit:TaxonTreeCountDbTest#testCountryImagesAreUnchanged; junit:ImagesAnywhereTest#testListImagesArePendingUntilANightlyCountsAntImages; e2e:test_images_anywhere.py::test_list_says_what_the_images_column_counts; unit:test_images_anywhere.py::test_list_images_column_shows_the_antweb_number_only |
| 45 | taxonomicPage.do, browse.do | Admin column total ("Total of the ... column", until 2026-10-27 "Subtaxon total") | live sum of the shown rows; end-to-end since 2026-10-27 on the day's place of every kind: = the sum of the rows' numbers, rank genus = Species and rank subfamily = Genera of the place page, rank species at most Specimens; browse.do of a genus = the sum of its rows; a total below the place's number is B76 (open, an expected failure of the test), above it fails | yes | e2e:test_count_check.py::test_subtaxon_totals_equal_the_place_numbers; e2e:test_numbers_search.py::test_subtaxon_total_is_the_sum_of_the_rows_of_every_kind_of_place |
| 46 | taxonomicPage.do, browse.do | browse header N Specimens / Species / Genera (M imaged) | live children (Overview.getOverviewCriteria); a genus's species: valid names and morphospecies since 2026-10-28 (`Genus.setChildren`, as the genus row); since 2026-10-26 on a different place of every kind every day: Species of a genus and Genera of a subfamily = the list row (validated by inv:list.genus, inv:list.subfamily) and the rows listed; Specimens of a species = the rows listed, and = the list row only after B15 (xfail) | yes | e2e:test_numbers_live.py::test_browse_header_and_hidden_totals_agree; e2e:test_numbers_lists.py::test_browse_headers_equal_the_list_rows; e2e:test_numbers_lists.py::test_species_browse_imaged_count_equals_the_specimens_with_images; e2e:test_numbers_lists.py::test_species_browse_counts_the_specimens_of_its_list_row |
| 47 | taxonomicPage.do, browse.do | browse (Only the first N of M displayed) | live; N of "of N" = the header (2026-10-26); the page shows limit + 1 rows (B42, xfail) | yes | e2e:test_numbers_live.py::test_browse_header_and_hidden_totals_agree; e2e:test_numbers_lists.py::test_browse_limit_note_names_the_header_number; e2e:test_numbers_lists.py::test_browse_limit_shows_that_many_specimens |
| 48 | taxonomicPage.do, browse.do | browse sub-taxon lines (genus, subfamily, family rows' counts) | list rows of genus, subfamily, family: sums of their child rows (CountDb crawl; every row reset first since 2026-10-21; Images: images of the ant since 2026-10-27, #44) | yes | inv:list.genus; inv:list.subfamily; inv:list.family; inv:list.images |
| 49 | taxonomicPage.do, browse.do | browse hidden N Imaged X | live; species browse (2026-10-26, every kind of place): M = the specimens listed with an image, also by the API imageCount; genus browse (2026-10-27, every kind of place): M = the children with pictures (Taxon.getHasImages, the pictures of a specimen of the name from anywhere, B45) = the children images.do's All view shows; a specimen with only label photos is not imaged (`Specimen.getHasImages`; B71, decided by Brian Fisher 2026-10-27, Q12) | yes | e2e:test_numbers_live.py::test_browse_header_and_hidden_totals_agree; e2e:test_numbers_lists.py::test_species_browse_imaged_count_equals_the_specimens_with_images; e2e:test_numbers_lists.py::test_browse_headers_equal_the_list_rows; e2e:test_numbers_images.py::test_browse_imaged_count_equals_the_children_with_pictures; junit:CountRules20261027Test#testASpecimenWithOnlyLabelPhotosIsNotImaged |
| 50 | description.do, images.do, comparisons | N times found in <habitat> | live: collection events per habitat, ordered by that number since 2026-10-21; since 2026-10-26 recounted from the API specimen records of the species of a different genus of Madagascar every day; a specimen without a collection code is a collection event of its own (`Species.COLLECTION_EVENTS`; B72, decided by Brian Fisher 2026-10-27) | yes | junit:PlaceTotalsDbTest#testHabitatsAreOrderedByTheNumberShown; e2e:test_numbers_species.py::test_species_page_habitat_summaries_equal_a_recount_of_the_specimens; junit:CountRules20261027Test#testSpecimensWithoutACollectionCodeAreCollectionEventsOfTheirOwn |
| 51 | description.do, images.do, comparisons | N times <microhabitat> | live, as #50 (10 entries, was 11); since 2026-10-26 recounted from the API specimen records of the species of a different genus of Madagascar every day; a specimen without a collection code is a collection event of its own (`Species.COLLECTION_EVENTS`; B72, decided by Brian Fisher 2026-10-27) | yes | junit:PlaceTotalsDbTest#testHabitatsAreOrderedByTheNumberShown; e2e:test_numbers_species.py::test_species_page_habitat_summaries_equal_a_recount_of_the_specimens; junit:CountRules20261027Test#testSpecimensWithoutACollectionCodeAreCollectionEventsOfTheirOwn |
| 52 | description.do, images.do, comparisons | N times <method> | live, as #50 (10 entries, was 11); since 2026-10-26 recounted from the API specimen records of the species of a different genus of Madagascar every day; a specimen without a collection code is a collection event of its own (`Species.COLLECTION_EVENTS`; B72, decided by Brian Fisher 2026-10-27) | yes | junit:PlaceTotalsDbTest#testHabitatsAreOrderedByTheNumberShown; e2e:test_numbers_species.py::test_species_page_habitat_summaries_equal_a_recount_of_the_specimens; junit:CountRules20261027Test#testSpecimensWithoutACollectionCodeAreCollectionEventsOfTheirOwn |
| 53 | description.do, images.do, comparisons | Field guide: the same three summaries | as #50-#52; habitats and methods recounted as #50, #52 (2026-10-26); the field guide shows no microhabitats (B43, xfail) | yes | junit:PlaceTotalsDbTest#testHabitatsAreOrderedByTheNumberShown; e2e:test_numbers_species.py::test_field_guide_habitat_summaries_equal_a_recount_of_the_specimens; e2e:test_numbers_species.py::test_field_guide_shows_the_microhabitat_summary |
| 54 | description.do, images.do, comparisons | Admin Image files (taxon.image_count; labelled ImageCount until 2026-10-27) | taxon.image_count (set 4): images of the taxon's specimens and of the taxa under it (species were never added to their genus); left as every image file (labels included): the label says Image files | yes | inv:taxon.images; junit:PlaceTotalsDbTest#testTaxonImageCountsAddTheTaxaUnderThem |
| 55 | description.do, images.do, comparisons | images.do header N Species Imaged (M total) | live: N = children with a head, profile or dorsal picture (from any specimen of the name, B45), M = all children; since 2026-10-27 on a different genus of a different place of every kind every day: M = browse.do's N and its rows, N = the children of the All view with such a picture | yes | e2e:test_numbers_live.py::test_image_and_comparison_pages_count_the_same_children; e2e:test_numbers_images.py::test_images_page_header_counts_the_children |
| 56 | description.do, images.do, comparisons | images.do child N Images ("T Images (P from X)" from 2026-10-27 to 10-28; one number since) | the child's Images cell of this place's list (#44; `ImagesAnywhere.childImages`): images of the ant of the name in AntWeb, labels never count (Brian Fisher 2026-10-27); when the list says No Images but the page shows images of the name, the images of the ant of the specimen shown (until 2026-10-27: the place's number, falling back to every picture of the specimen shown, B45 option A); the All view showed 0 for every name until 2026-10-27 (B46, fixed); = the list row's cell on every kind of place, default and All view | yes | cc:images; inv:list.images; e2e:test_numbers_images.py::test_images_page_child_counts_equal_the_list_rows; unit:test_image_count_fixes.py::test_all_view_shows_the_list_images_of_a_name; junit:ImagesAnywhereTest#testAntImagesShownLeaveLabelsOut |
| 57 | description.do, images.do, comparisons | taxonomicPage images view: N X Imaged (out of M Total) | live: children with pictures (from any specimen of the name, B45), out of all children; since 2026-10-27 on every kind of place: M = the list header, N = the children shown with pictures, N + the children with no image = M | yes | e2e:test_numbers_pages.py::test_images_view_header_counts_imaged_children; e2e:test_numbers_images.py::test_images_view_header_counts_the_children_with_pictures |
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
| 75 | countries.do, regions.do, subregions.do, adm1s.do, projects.do, museums.do, bioregions.do | Image files column (labelled Images until 2026-10-27) | = #29 | yes | inv:place.images |
| 76 | countries.do, regions.do, subregions.do, adm1s.do, projects.do, museums.do, bioregions.do | Image files pie (Image Submissions) | = #75 per place | yes | inv:place.images |
| 77 | countries.do, regions.do, subregions.do, adm1s.do, projects.do, museums.do, bioregions.do | Specimen Submissions pie | = #74 per place | yes | inv:place.totals.specimens; inv:geo.regions |
| 78 | endemic.do, introduced.do | endemic.do rows | live over is_endemic flags; = the names marked Endemic on the species list, and a daily sample of its names (and of the valid names not on it) recounted by the endemism rule from the API (2026-10-26); since 2026-10-27 counted statuses only, no synonyms (`PlaceTotals.flaggedNamesSql`, the rule of #24, #25; B70); since 2026-10-28 valid names only, native rows only (#24; Brian Fisher), so every name on endemic.do is on the place's species list (the end-to-end test no longer allows an endemic name the list does not show), and the page says what is counted | yes | inv:place.totals.endemic; inv:place.endemic.flags; e2e:test_numbers_live.py::test_endemic_and_introduced_lists_equal_place_numbers; e2e:test_numbers_endemic.py::test_endemic_and_introduced_marks_on_the_species_list_agree; e2e:test_numbers_endemic.py::test_endemic_names_follow_the_endemism_rule; e2e:test_numbers_endemic.py::test_endemic_names_of_countries_are_named_species; junit:CountRules20261027Test#testEndemicAndIntroducedListsAndNumbersShareTheStatusRule |
| 79 | endemic.do, introduced.do | introduced.do rows | live over is_introduced flags; = the names the species list marks Introduced (live, from the native bioregions) that are on the introduced-species list (2026-10-26); since 2026-10-27 counted statuses only, no synonyms (`PlaceTotals.flaggedNamesSql`, the rule of #24, #25; B70) | yes | inv:place.totals.introduced; e2e:test_numbers_live.py::test_endemic_and_introduced_lists_equal_place_numbers; e2e:test_numbers_endemic.py::test_endemic_and_introduced_marks_on_the_species_list_agree; e2e:test_numbers_endemic.py::test_a_place_without_a_bioregion_has_no_introduced_species; junit:CountRules20261027Test#testEndemicAndIntroducedListsAndNumbersShareTheStatusRule |
| 80 | curators, groups, artists, uploads (admin) | curators.do Specimen Uploads | LoginMgr at startup | yes | inv:admin.curators; e2e:test_count_check.py::test_curator_and_artist_pages_equal_the_recount |
| 81 | curators, groups, artists, uploads (admin) | curators.do Description Edits | LoginMgr at startup: page-text edits, every saved version: taxon texts (description_edit, description_hist) and, since 2026-10-22, place and project texts (object_edit, object_hist); said on curators.do and curator.do | yes | inv:admin.curators; e2e:test_count_check.py::test_curator_and_artist_pages_equal_the_recount |
| 82 | curators, groups, artists, uploads (admin) | curator.do specimen uploads | LoginMgr at startup | yes | inv:admin.curators; e2e:test_count_check.py::test_curator_and_artist_pages_equal_the_recount |
| 83 | curators, groups, artists, uploads (admin) | curator.do last upload numbers | the specimens today of the group of the curator's last upload (group_count, recounted every night since 2026-10-22, Q13); its date apart; end-to-end since 2026-10-27: Specimen, Ungeoreferenced, Flagged = the group's numbers that the count check compared with its recount, on the curator with the most uploads and a daily sample of the others | yes | inv:admin.groups; junit:NightlyCountOrderDbTest#testGroupNumbersAreRecountedEveryNight; e2e:test_numbers_uploads.py::test_curator_last_upload_numbers_equal_the_checked_group_numbers |
| 84 | curators, groups, artists, uploads (admin) | curator.do image uploads, image files uploaded (labelled Images Uploaded until 2026-10-27) | LoginMgr at startup; files uploaded, label images included: labelled Image files | yes | inv:admin.curators; e2e:test_count_check.py::test_curator_and_artist_pages_equal_the_recount; unit:test_image_counts_defined.py::test_every_image_count_getter_on_a_page_is_defined |
| 85 | curators, groups, artists, uploads (admin) | groups.do Uploads | live count of upload rows (GroupAction); ant_group.upload_count orders the page | yes | inv:admin.groups; e2e:test_count_check.py::test_group_pages_equal_the_stored_upload_numbers |
| 86 | curators, groups, artists, uploads (admin) | groups.do Specimens | the group's specimens today (group_count, nightly set 3, GroupCountDb; the last upload row until a nightly run has counted the group) | yes | inv:admin.groups; e2e:test_count_check.py::test_group_pages_equal_the_stored_upload_numbers |
| 87 | curators, groups, artists, uploads (admin) | groups.do Ungeoreferenced, Flagged, Collections, Localities | group_count, as #86 | yes | inv:admin.groups; e2e:test_count_check.py::test_group_pages_equal_the_stored_upload_numbers |
| 88 | curators, groups, artists, uploads (admin) | groups.do Subfamilies, Genera, Species | group_count, as #86, by one rule (`GroupCountDb.countColumns`, also each upload's row, UploadDb.getCounts): Species = distinct full names of species and subspecies of the five counted statuses, ants, identified to genus; Subfamilies, Genera = distinct valid ant subfamilies and genera identified to genus of the counted specimens (Q8 2026-10-22: Species counted epithets of valid names); explained on groups.do and group.do | yes | inv:admin.groups; junit:NumberDecisionsDbTest#testGroupSpeciesAreFullNamesAndTotalsAreDistinct; junit:NumberDecisionsDbTest#testGroupNumbersOfTheOldRuleArePendingUntilTheNightlyRun; e2e:test_count_check.py::test_group_pages_equal_the_stored_upload_numbers |
| 89 | curators, groups, artists, uploads (admin) | groups.do Totals row: contributors, Uploads, Specimens, Ungeoreferenced, Flagged | live sums of the rows (each specimen belongs to one group) | yes | e2e:test_count_check.py::test_group_pages_equal_the_stored_upload_numbers; inv:admin.groups |
| 90 | curators, groups, artists, uploads (admin) | groups.do Totals Collections, Localities, Subfamilies, Genera, Species | all listed groups' specimens together, distinct over them (group_count row 0, nightly set 3, `GroupCountDb.totalsSql`; Q8 2026-10-22: Subfamilies, Genera, Species were the largest row, Collections and Localities the sum); "-" until the first nightly count | yes | inv:admin.groups; junit:NumberDecisionsDbTest#testGroupSpeciesAreFullNamesAndTotalsAreDistinct; e2e:test_count_check.py::test_group_pages_equal_the_stored_upload_numbers |
| 91 | curators, groups, artists, uploads (admin) | group.do uploads, last upload numbers | group_count, as #86 (the page says so, with the date of the count) | yes | e2e:test_count_check.py::test_group_pages_equal_the_stored_upload_numbers; inv:admin.groups |
| 92 | curators, groups, artists, uploads (admin) | artists.do Image files (labelled Images until 2026-10-27), Specimen | live / ArtistMgr; files credited, label images included: labelled Image files | yes | inv:admin.artists; e2e:test_count_check.py::test_curator_and_artist_pages_equal_the_recount; unit:test_image_counts_defined.py::test_every_image_count_getter_on_a_page_is_defined |
| 93 | curators, groups, artists, uploads (admin) | listImageUploads totals (Image files column, holding N image files; labelled Image Count, images until 2026-10-27) | live / image_upload | yes | inv:admin.imageUploads; inv:admin.imageUploadArtists; e2e:test_count_check.py::test_image_upload_and_orphan_counts_equal_the_recount |
| 94 | curators, groups, artists, uploads (admin) | imageUploadReport counts (Image files, Total image files uploaded; labelled Images until 2026-10-27) | image_upload.image_count, written when the upload ends; saved and not saved files; end-to-end since 2026-10-27 on the newest batch and a daily sample: Images = the batch's Image Count on listImageUploads.do = the files the report lists (complete batches), Total Images Uploaded = the files listed as saved, Not saved = the files listed as not saved; the report of a batch from an earlier year failed until 2026-10-27 (B74, fixed) | yes | inv:admin.imageUploads; e2e:test_numbers_uploads.py::test_image_upload_report_counts_equal_their_files |
| 95 | curators, groups, artists, uploads (admin) | Upload report Inserted / Updated Species | at upload: the species and subspecies names the upload added to the taxon table (Inserted) or whose existing row it rewrote (Updated), each name once (B60, B61 fixed 2026-10-27; a name AntCat gives is never rewritten by a specimen upload); shown in every upload's report (`UploadDetails.speciesCountsHtml`; until PR #291's second staging run only in reports with project statistics, that is species list uploads, so a specimen upload's report never showed them); end-to-end: the test group's reports = the names of the uploaded file that AntCat does not give (API /taxa), other reports at most the file's distinct names; reports written before the fix (no `report-counts-rule` mark) only at most the lines processed | yes | junit:UploadReportCountsTest#testANewNameIsInsertedNotUpdated; junit:UploadReportCountsTest#testEachUpdatedNameCountsOnce; junit:UploadReportCountsTest#testSpecimenUploadReportShowsTheSpeciesCounts; junit:UploadReportCountsTest#testReportPrintsTheCountsWithoutProjectStatistics; e2e:test_numbers_uploads.py::test_upload_report_counts_agree_with_the_uploaded_file; e2e:test_specimen_upload_roundtrip.py::test_specimen_upload_round_trip |
| 96 | curators, groups, artists, uploads (admin) | Upload report message counts | at upload; static error list (B23). End-to-end since 2026-10-27 on the test group's newest reports and a daily sample: Record Count = the uploaded file's lines after the header (B62 fixed 2026-10-27: counted the header too), Parsed at most Record Count, Processed at most Parsed, Inserted Specimens at most Processed; each line's (total:N): sections listed by key have N lines, sections of values list at most N values and N specimens; the Inserted Specimens of the test group's last upload = the group's specimens (statistics.do by upload, and its codes found through the API). Red Flagged Specimens: no test of its own | yes | e2e:test_numbers_uploads.py::test_upload_report_counts_agree_with_the_uploaded_file; e2e:test_numbers_uploads.py::test_statistics_by_upload_counts_each_groups_specimens; e2e:test_specimen_upload_roundtrip.py::test_specimen_upload_round_trip |
| 97 | curators, groups, artists, uploads (admin) | Admin statistics.do table | statistics rows (latest row checked; older rows are history); V. Sp. Imaged counts valid species and subspecies imaged since 2026-10-21; Total Images: images of the ant in rows written since 2026-10-27, every image file in older rows (the column title says so) | yes | inv:home; inv:admin.statistics |
| 98 | curators, groups, artists, uploads (admin) | statistics.do breakdowns | live; byUpload (specimens per upload group) checked; byProject, byBioregion, byMuseum, byGeolocale are row counts of the lists. End-to-end since 2026-10-27: byUpload together at most the current specimen count, the test group's = its codes found through the API; on the daily row of each other breakdown every number up to 998 = the names its taxaList.do link lists, All = Extinct + Extant, Imaged v. sp. at most V. sp. (B63, B64 fixed 2026-10-27) | yes | inv:admin.statistics; e2e:test_numbers_uploads.py::test_statistics_by_upload_counts_each_groups_specimens; e2e:test_numbers_uploads.py::test_statistics_breakdown_numbers_equal_their_lists |
| 99 | curators, groups, artists, uploads (admin) | orphanedImages Found N image files of M specimen codes that are not in AntWeb (images until 2026-10-27) | live: images whose specimen code is no specimen record, and their codes (`OrphanedImages.COUNTS_SQL`, Q9 2026-10-22: the page said "N orphaned images" and counted codes); explained on the page | yes | inv:stats.images; junit:NumberDecisionsDbTest#testOrphanedImagesCountImagesAndCodes; e2e:test_count_check.py::test_image_upload_and_orphan_counts_equal_the_recount |
| 100 | API v3.1 | /specimens imageCount, antImageCount, labelImageCount | specimen.image_count; imageCount kept (every image file); since 2026-10-27 antImageCount and labelImageCount of the specimen (api.imageSplit, one grouped query per reply), also on /geoSpecimens | yes | inv:taxon.images; e2e:test_api_numbers.py::test_images_meta_counts_and_specimen_image_count; e2e:test_numbers_live.py::test_specimen_images_count_equals_the_api; unit:test_api_image_split.py::test_split_by_specimen; unit:test_api_image_split.py::test_replies_add_the_fields |
| 101 | API v3.1 | /specimens, /geoSpecimens, ... meta count | live: rows in this page; since 2026-10-27 recounted: the records of the filter (day's country; pages of k add up to 2k), the records inside the radius box and their genera (distinct=genus) | yes | e2e:test_api_numbers.py::test_meta_count_is_the_rows_returned; e2e:test_numbers_api_meta.py::test_specimen_counts_are_the_records_of_the_filter |
| 102 | API v3.1 | /geoSpeciesStats caste counts | live; every subcaste count only within its caste since 2026-10-27 (decided by Brian Fisher); since 2026-10-27 the ten counts of two names of the day's country, adm1 and subregion are recounted from /specimens; a name shared by several geolocales counted each specimen once per geolocale (B55, fixed 2026-10-27); adm1 by name only (B26); the caste of a specimen is read from its Life Stage field (`Caste.getCasteValues`; B69, 2026-10-27: "alate male" is a male, "and" and "/" split without a comma; existing specimens after their next upload or a calcCaste run, the counts after the next nightly run) | yes | e2e:test_api_numbers.py::test_geo_species_stats_castes_add_up; unit:test_api_counts.py::test_geo_species_stats_counts_ergatoids_of_their_caste; e2e:test_numbers_api_meta.py::test_geo_species_stats_castes_equal_the_specimens; e2e:test_numbers_api_meta.py::test_geo_species_stats_of_a_shared_name_count_each_specimen_once; unit:test_api_counts.py::test_geo_species_stats_counts_the_list_of_one_geolocale; unit:test_api_counts.py::test_get_geolocale_prefers_a_valid_geolocale; unit:test_api_decisions.py::test_geo_species_stats_counts_subcastes_within_their_caste; junit:CountRules20261027Test#testAlateMaleIsAMale; junit:CountRules20261027Test#testAndAndSlashSplitWithoutAComma |
| 103 | API v3.1 | /taxa subfamilyCount, genusCount, speciesCount, specimenCount | taxon columns, copied each night from the name's Global row (`TaxonApiCountDb`, since 2026-10-26; B11) | yes | inv:api.taxa |
| 104 | API v3.1 | /taxa imageCount, antImageCount, labelImageCount | taxon.image_count (= #54); imageCount kept; since 2026-10-27 antImageCount and labelImageCount: the images of the specimens filed under the name (api.imageSplit by taxon_name) | yes | inv:taxon.images; unit:test_api_image_split.py::test_split_by_name |
| 105 | API v3.1 | /taxa meta taxonCount | live; since 2026-10-27: the species of the day's genus = the species rows of all its taxa, pages add up | yes | e2e:test_api_numbers.py::test_meta_count_is_the_rows_returned; e2e:test_numbers_api_meta.py::test_taxa_count_is_the_taxa_of_the_filter |
| 106 | API v3.1 | /images meta counts | live: rows in this page; specimenCount was 1 and one empty specimen when nothing was found, and every specimen but the last was under 'spefimenCode' (fixed 2026-10-22); since 2026-10-27 a reply of many specimens and its specimens one by one (/images, /specimens imageCount); since 2026-10-27 the metaData also has antImageCount and labelImageCount of the reply's images | yes | e2e:test_api_numbers.py::test_images_meta_counts_and_specimen_image_count; e2e:test_api_numbers.py::test_images_of_an_unknown_specimen_are_none; unit:test_api_counts.py::test_images_of_nothing_are_no_specimen; unit:test_api_counts.py::test_images_are_grouped_by_specimen; e2e:test_numbers_api_meta.py::test_images_counts_of_many_specimens; unit:test_api_image_split.py::test_shot_split |
| 107 | API v3.1 | /taxaImages meta counts | live; since 2026-10-27 the specimens and images of a name = its specimens with images (/specimens) and their images (/images); since 2026-10-27 the metaData also has antImageCount and labelImageCount of the reply's images | yes | e2e:test_api_numbers.py::test_taxa_images_meta_counts; e2e:test_numbers_api_meta.py::test_taxa_images_counts_equal_the_specimens_and_images_of_the_name; unit:test_api_image_split.py::test_replies_add_the_fields |
| 108 | API v3.1 | /geolocales subfamily, genus, species, specimen counts | = #20-#22, #26 | yes | inv:place.totals.subfamily; inv:place.totals.genus; inv:place.totals.species; inv:place.totals.specimens |
| 109 | API v3.1 | /geolocales imageCount, imagedSpecimenCount | = #29, #27; imageCount = the place's Image files (every file; the API page says so) | yes | inv:place.images; cc:place images |
| 110 | API v3.1 | /geolocales endemic, introduced counts | = #24, #25 (Endemic: valid native names only since 2026-10-28); since 2026-10-27 compared with the place page of the day's country, adm1, region, subregion and Madagascar | yes | inv:place.totals.endemic; inv:place.totals.introduced; inv:place.endemic.flags; e2e:test_numbers_api_meta.py::test_geolocale_endemic_and_introduced_equal_the_place_page |
| 111 | API v3.1 | /geolocales meta geolocaleCount | live: geolocales returned; since 2026-10-27 the adm1s of the day's countries and the geolocales of a name recounted from the lists by rank | yes | e2e:test_api_numbers.py::test_meta_count_is_the_rows_returned; e2e:test_numbers_api_meta.py::test_geolocales_count_is_the_geolocales_of_the_filter |
| 112 | API v3.1 | /geolocaleTaxa per row counts | list rows (imageCount: images of the ant since 2026-10-27, #44); since 2026-10-27 compared with the subfamily, genus and species lists (taxonomicPage.do) of the day's country, adm1, region and subregion; the list's Images cell shows the AntWeb-wide number since 2026-10-28 (#44), so it is checked to be at least the row's imageCount (the place's own) | yes | inv:list.genus; cc:specimens; cc:images; inv:list.images; e2e:test_numbers_api_meta.py::test_geolocale_taxa_rows_equal_the_place_lists |
| 113 | API v3.1 | /geolocaleTaxa meta count | live: taxa returned (named geolocaleCount); since 2026-10-27 on every page of the day's places, with the rank filter and every name of the place's list | yes | e2e:test_api_numbers.py::test_meta_count_is_the_rows_returned; e2e:test_numbers_api_meta.py::test_geolocale_taxa_rows_equal_the_place_lists |
| 114 | API v3.1 | /bioregions 6 counts | bioregion table (= place page) | yes | inv:place.totals.subfamily; inv:place.totals.genus; inv:place.totals.species; inv:place.totals.specimens; inv:place.images |
| 115 | API v3.1 | /bioregions meta count | live | yes | e2e:test_api_numbers.py::test_meta_count_is_the_rows_returned; e2e:test_api_numbers.py::test_bioregions_count_equals_the_bioregions_page |
| 116 | API v3.1 | /bioregionTaxa per row counts | list rows (imageCount: images of the ant since 2026-10-27, #44); since 2026-10-27 compared with the subfamily, genus and species lists of the day's bioregion; the list's Images cell shows the AntWeb-wide number since 2026-10-28 (#44), so it is checked to be at least the row's imageCount (the place's own) | yes | inv:list.genus; cc:specimens; inv:list.images; e2e:test_numbers_api_meta.py::test_bioregion_taxa_rows_equal_the_bioregion_lists |
| 117 | API v3.1 | /unimagedGeolocaleTaxa list | live: names without a specimen with an image of the ant anywhere in AntWeb (was image_count > 1); the API page says so (Q12, 2026-10-22) | yes | unit:test_api_counts.py::test_a_taxon_with_one_image_of_the_ant_is_imaged; unit:test_api_counts.py::test_endpoint_uses_the_rule; junit:NumberDecisionsDbTest#testUnimagedMeansNoPhotoOfTheAntAnywhere; e2e:test_api_numbers.py::test_meta_count_is_the_rows_returned; e2e:test_numbers_api_meta.py::test_unimaged_names_have_no_image_of_the_ant; unit:test_api_counts.py::test_unimaged_names_have_a_fixed_order |
| 118 | API v3.1 | /bioregionTaxa, /geolocaleTaxa meta and dev counters | live: taxa returned (geolocaleTaxa: geolocaleCount, bioregionTaxa: bioregionCount, unimagedGeolocaleTaxa: unimagedGeolocaleTaxaCount, with byCaste also unimagedGeolocaleTaxaCasteCount = name and caste pairs); since 2026-10-27 on every page of the day's places; byCaste=true was ignored (B57, fixed 2026-10-27) | yes | e2e:test_api_numbers.py::test_meta_count_is_the_rows_returned; e2e:test_numbers_api_meta.py::test_geolocale_taxa_rows_equal_the_place_lists; e2e:test_numbers_api_meta.py::test_bioregion_taxa_rows_equal_the_bioregion_lists; e2e:test_numbers_api_meta.py::test_unimaged_by_caste_counters; unit:test_api_counts.py::test_by_caste_true_is_by_caste |
| 119 | project.do?name=poeants (Port of Entry) and its list | Specimens, Imaged specimens, Images, the Specimens pie, the list's Specimens and Images columns, projects.do Specimens and Images | none since 2026-10-22: a species list only (`Project.isSpeciesListOnly`); the nightly run sets them to 0 and counts none (ProjTaxonCountDb.recount, ProjectDb.updateCounts, PlaceImageCountDb); the pages show "-" or no column and say that these species were intercepted at ports of entry, are not established introductions and are not counted in any place total or the introduced species list; the count check skips the project with a note | yes | junit:NumberDecisionsDbTest#testPortOfEntryHasNoNumbersAndIsNotChecked; e2e:test_numbers_pages.py::test_port_of_entry_is_a_species_list_without_numbers |
| 120 | description.do (species, subspecies) | Imaged: worker (N specimens: N worker, N major/soldier), queen (N), male (N), other (N), caste not given (N) (since 2026-10-27) | live (`ImagedCastes`, decided by Brian Fisher 2026-10-27): the specimens of the name, from anywhere in AntWeb, with an image of the ant (labels do not count: `StatisticsDb.ANT_IMAGE`, Q12), by specimen.caste (and subcaste when more than one is given; the ordinary worker subcaste, "normal" in the data, is shown as "worker", Brian Fisher 2026-10-27); one grouped query (specimen key on taxon_name, image joined on image_of_id); each caste and subcaste links to images.do with that caste; recounted from the API (/taxaImages without label shots, /specimens caste) for Leptogenys purpurea and the day's species of Madagascar | yes | e2e:test_images_anywhere.py::test_species_page_imaged_castes_equal_a_recount; junit:ImagesAnywhereTest#testImagedCastesLine; junit:ImagesAnywhereTest#testImagedCastesQueryLeavesLabelsOut |

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
- **B26** (confirmed in the code 2026-10-22, check `inv:geo.adm1.country`). An adm1 takes its specimens by name
  only: `Geolocale.getSpecimenPlaceCriteria` (`s.adm1 = name`; used by the nightly specimen counts of the list rows
  and so the page's Specimens, and by the place downloads), `PlaceImageCountDb` (adm1 source: `s.adm1 = g.name`;
  Images, Imaged specimens), `TaxaPage` (the specimen names of an adm1's taxonomic page), `GeolocaleDb
  .getSpecimenLocaleClause`, and the API `/geoSpeciesStats` (`s.adm1 = :geolocaleName`). The list itself is built
  per country and adm1 (`GeolocaleTaxonDb`, by country then adm1), so only the numbers are wrong: a state of the
  same name in another country (or a specimen with that adm1 and no country) adds its specimens to every row of
  the list that it shares. The count check's own recount of a rotating adm1 (`cc:specimens`, `cc:place images`)
  uses the same name-only rule, so it agrees with the wrong numbers; `inv:geo.adm1.country` counts, per live adm1,
  the specimens its page counts that were not collected in its country, and reports the adm1's Specimens against
  Specimens minus those (real differences until fixed). Estimate on staging: the check's report lists every
  affected adm1 and the JSON (`b26`) gives the number of adm1s and specimens. Staging queries: `select name,
  count(*) from geolocale where georank = 'adm1' and is_valid = 1 group by name having count(*) > 1` (shared
  names) and `select g.name, g.parent, s.country, count(*) from geolocale g join specimen s on s.adm1 = g.name and
  (s.country is null or s.country <> g.parent) where g.georank = 'adm1' and g.is_valid = 1 group by g.id,
  s.country order by 4 desc`. Fix (tomorrow): add the country (`and s.country = parent`) in all six places, and in
  the count check's recount.

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
  `#testNoAlertWithoutAValidNameMissingItsGenus`; `SubfamilyFromGenusTest` (4 tests).

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
- **B42** browse.do of a species with more specimens than the limit says "(Only the first k of N displayed)" and lists
  k + 1 specimens: `specimenReport.jsp` shows rows with index 0 to limit (`index <= limit`, index starts at 0); with the
  default limit, 2,001 of a species with more than 2,000 specimens. Test:
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
- **B67** (decided by Brian Fisher 2026-10-27, Q1 of the triage) a curated project's Subfamilies number counted its
  subfamily rows of a counted status, while the nightly run counts the rows of its list in every status (the list's
  genus_count, `StatusSet.getAndCriteria(project)` empty) and the count check's `checkChildren` does the same. Decision:
  the project Subfamilies number follows the same status rule as the project's list rows. Now every subfamily row of
  an ant subfamily counts for a curated project (`PlaceTotals.listedCondition`, used by the nightly run, the count
  check's `checkSubfamilyTotal` and `inv:place.totals.subfamily`); worldants and allantwebants keep the counted statuses.
  The stored numbers change at the next nightly run (set 3). Tests:
  `junit:CountRules20261027Test#testCuratedProjectSubfamiliesCountEveryStatusAsTheirListRows`,
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
- **B76** (open, 2026-10-27) the admin Subtaxon total of a list is below the place's number (staging 10-27: region
  Americas rank genus 4952 against Species 4953; bioregion Afrotropical rank subfamily 121 against Genera 130 and rank
  genus 7179 against 7183; project floridakeysants rank genus 90 against 92). Each row's number counts its child rows
  by `taxon.parent_taxon_name` (`CountDb.countChildren`), and the rows add up to the total, but the place's number
  counts every counted row of the rank (`PlaceTotals.rankCondition`). So a counted name with no parent (B36: names
  whose genus row does not exist are not repaired), or whose parent row is not a counted row of the place's list (for
  example an AntCat genus row of a bioregion without its subfamily row), is in the place's number and under no row.
  Not yet confirmed on the database; to see the names (bioregion; the same with geolocale_taxon, proj_taxon):
  `select c.taxon_name, t.parent_taxon_name from bioregion_taxon c join taxon t on t.taxon_name = c.taxon_name where
  c.bioregion_name = 'Afrotropical' and t.taxarank = 'genus' and t.status in (counted) and t.family = 'formicidae' and
  not exists (select 1 from bioregion_taxon p join taxon pt on pt.taxon_name = p.taxon_name where p.bioregion_name =
  c.bioregion_name and p.taxon_name = t.parent_taxon_name and pt.status in (counted))`. Fix to decide with Brian:
  add the missing parent rows to the list (the crawl), or count the place's Genera and Species from the rows' sums.
  The test marks these differences as an expected failure (`pytest.xfail`, B76); a total above the place's number
  still fails. Test: `e2e:test_numbers_search.py::test_subtaxon_total_is_the_sum_of_the_rows_of_every_kind_of_place`.
- **B77** (open, 2026-10-27) a bioregion's subfamily browse page says more genera than the subfamily's row on the
  bioregion's list (Palearctic Ponerinae: browse.do "18 Genera (14 imaged)", list row "16 Genera"; the same on
  production, so not caused by PR #291). The browse page counts and lists every genus row of the bioregion
  (`taxon.getChildrenCount`, any status, including "ponerine"), the list row only the counted statuses
  (`PlaceTotals.rankCondition`). Fix to decide with Brian: browse pages count the counted statuses only (and list
  the others under a note), like the list. The test marks bioregion subfamily differences as an expected failure
  (`pytest.xfail`, B77); any other place or rank still fails. Test:
  `e2e:test_numbers_lists.py::test_browse_headers_equal_the_list_rows`.
- Not a number, for the record: the recent images test expected 100 rows on the first page; the page lists 50 per
  page (`recentSearchResults-body.jsp`, parameter `length`; `numToShow` is not the page size). The test now reads the
  page's own "Showing records a to b".
- Open (2026-10-27, to look at after B65 is live): fossilants Genera 259 against 258 and four subfamily rows one over
  (the project is recounted in set 3 after the clean-up, so B65 does not explain it); statistics.do latest row proj_taxa
  69257 against 69251; the atolants images row "Paráponera clavata" (89 against 0), a name with an accent.

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
  valid geolocale first, then the lowest id. An adm1 still takes its specimens by name only (B26). Tests:
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
| api.taxa, geo.adm1.country | 29,910, 3 | known B11, B26 | as before |
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
| B26 | Add the country in the six places listed in section 3 and in the count check's recount (`CountCheck.Place` of an adm1: `and s.country = parent`); `inv:geo.adm1.country` then shows no difference. |
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
| Subfamilies | subfamilies with at least one specimen identified at least to subfamily (the subfamily rows of the list; unchanged) | `PlaceTotals.rankCondition(SUBFAMILY)` |
| Genera | genera with at least one specimen identified at least to genus: a "Lioponera indet" specimen makes Lioponera count (its genus row is on the list); names not identified to genus (`PlaceTotals.identifiedToGenus`) do not | `PlaceTotals.rankCondition(GENUS)` |
| Species / subspecies | names with status valid or morphotaxon only; indetermined, unidentifiable and unrecognized never count as species (until 2026-10-28 all five counted statuses did) | `PlaceTotals.SPECIES_STATUSES`, `rankCondition(SPECIES)`, `countsAsSpecies` |
| Valid species | valid only (unchanged) | `PlaceTotals.VALID_CONDITION` |
| Endemic | valid names only (decided 2026-10-28, below); until then valid and unrecognized names on the lists and numbers, and every counted status but morphotaxon (countries, adm1s) or all five (bioregions) in the flags | `PlaceTotals.ENDEMIC_STATUSES`, `endemicEligible`, `flaggedCondition` |

Lists: on a genus list, a genus known at the place only from specimens identified to genus shows "No Species" and
"N specimens identified to genus only", linked to those specimens (the place's specimen search for the genus and those
names). On a species list (every counted status, the default of places) there is no row for names that are not species,
and one line under the list: "N specimens identified only to genus are not listed here: Lioponera (1), Pheidole (2).
Show all specimens." (`GenusOnly`, `PlaceTotals.genusOnlySql`; indetermined and unidentifiable names identified to
genus). A status chosen on its own (statusSet indetermined, for a curator) still lists those names. The genus rows'
Species of every list (the nightly crawl), the browse page of a genus, "species + ssp. imaged" (home, statsPage) and
the count check take the same rule. Unrecognized names are not counted; listing them for curators in "Check names
against AntCat" (validateSpeciesList.do) is not done yet: that page checks an uploaded file, so a list of a place's
unrecognized names is a new page section (later).

Also decided on 2026-10-28:

| Decision | What | Change |
|---|---|---|
| D3 | Images follow the Specimens rule | On projects, museums and bioregions the images of specimens filed under a synonym count under the valid name, as Specimens do (Carebara polita, a synonym of C. diversa, 6 images, Afrotropical): the lists' Images column (`PlaceTotals.imagesByListNameSql` in `getTaxonImageCountQuery` of Museum, Bioregion and curated projects) and the place's Images and Imaged specimens (`PlaceImageCountDb.listSource`); the count check recounts both the same way (`CountCheck.checkRowImages`, `checkPlaceImages`). worldants and allantwebants (the Images column's AntWeb-wide number) count the very name. |
| D4 | Museum and bioregion Specimens = the sum of their list rows, like countries | `MuseumDb.finish`, `BioregionDb.finish` write `PlaceTotals.writeListedSpecimens`; their Specimens pie and their place images follow the same specimens; `inv:place.totals.specimens` compares every kind of place with the sum of its rows; `CountCheck.checkListPlaceSpecimens` recounts them from the specimens. Row 26 documented the opposite until then. |
| D5 | Bioregion and museum list images get the same status filter as countries | specimens of a counted status (`Bioregion.getTaxonImageCountQuery`, `Museum.getTaxonImageCountQuery`, the count check). |
| D6 | Subregions and regions: Introduced counted the same way Endemic is for them | `GeolocaleDb.calcHigherIntroduced` (`HIGHER_INTRODUCED_SQL`): a name flagged introduced on a country's list is flagged on its subregion's list, a subregion's on its region's; until then 0. |

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
link list at once. Tests: `junit:CountRules20261028Test#testEndemicCountsValidNamesOnly`,
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
| Q10 (answered 2026-10-21, see 5.1) | Specimens with an adm1 but no country (B26): count them for the state? | Counted for every adm1 of that name. | Not counted for any adm1 (the state is ambiguous without a country); the fix adds `country = parent`. | 26, 27, 29 |
| Q11 (answered 2026-10-22, see 5.2) | Port of Entry: a place? | Before: its page showed 37,430 specimens while its list had no species with specimens; the nightly run did not recount it. | Hide it as a place (is_valid = 0) and keep interceptions searchable. Decided instead: keep it as a species list without numbers (5.2). | 26, 119 |
| Q12 (answered 2026-10-22, see 5.2) | Unimaged names: unimaged where? | Before: API /unimagedGeolocaleTaxa: no image of the ant anywhere in AntWeb; taxonomicPage "Unimaged": no image (labels included) from this place. | Both: "no image of the ant anywhere" (what a curator needs to photograph), labelled so. | 39, 62, 117 |
| Q13 (answered 2026-10-22) | Upload group numbers: the last upload, or now? | Brian Fisher: today's numbers. Since 2026-10-22 groups.do, group.do and curator.do show the group's specimens today, recounted every night in set 3 (`GroupCountDb.recountAll`, table `group_count`) by the rule of the upload counts (`UploadDb.getCounts`); the date of the last upload is shown apart; the upload rows and the upload reports keep each upload's own numbers. `inv:admin.groups` compares the pages' numbers with a recount. | Done. | 83, 86-88, 91 |

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

### 5.2 Answers of 2026-10-22 (Q7, Q8, Q9, Q11, Q12)

| Q | Brian Fisher, 2026-10-22 | Change (code, page wording, check) | Visible after |
|---|---|---|---|
| Q7 ants only | "ants only everywhere": statsPage Global Data, Bioregion Data, Museum Data and the home "valid species + ssp." count Formicidae only, as the place pages; names not identified to genus never count as species there | One rule, `PlaceTotals.validAntTaxonCondition` / `validAntSpeciesCondition` / `isValidAntSpecies`: StatisticsDb (`globalCountSql`, `listCountSql`, `VALID_SPECIES_SQL`; also "species + ssp. imaged" and statistics.do "V. Sp. Imaged") and TaxonMgr (statsPage first number). statsPage says under each heading that it counts ants (Formicidae) only; the home numbers explain themselves on hover. Checks: `inv:stats.global`, `inv:stats.bioregions`, `inv:stats.museums` recount ants only; `inv:stats.nonants` compares the page's rule with an independent recount; `inv:home` and `inv:admin.statistics` report a stored number that still equals the old rule as pending. Home "specimen records", "specimens imaged" and "total specimen images" still count every record (not part of the decision). | statsPage at once (live); home after the next nightly run (set 4) or upload |
| Q8 upload groups | Species = distinct full names (taxon_name) of species and subspecies of the five counted statuses, ants only, identified to genus; Totals row = distinct over all groups combined; Subfamilies and Genera totals the same way | `GroupCountDb.countColumns`: one rule for the nightly recount (set 3), each upload's own row (`UploadDb.getCounts`) and the check. Subfamilies = valid ant subfamilies, Genera = genera identified to genus, of the counted specimens (any of the five statuses; were valid names of AntWeb's extant genera). Totals: group_count row 0 (`GroupCountDb.ALL_GROUPS`) = the specimens of every listed group together; groups.do shows it for Collections, Localities, Subfamilies, Genera, Species ("-" until the first nightly count); Uploads, Specimens, Ungeoreferenced, Flagged stay sums of the rows. groups.do and group.do explain the rule. Check: `inv:admin.groups` compares every group and the Totals row with a recount; numbers still by the old rule are pending until the nightly run. | groups.do, group.do after the next nightly run (set 3) |
| Q9 orphaned images | "N images of M specimen codes that are not in AntWeb" (count both) | `OrphanedImages.COUNTS_SQL` and `CODES_SQL`; the page says both numbers and what an orphaned image is. Check: `inv:stats.images` compares both with a recount. | at once |
| Q11 Port of Entry | Keep `poeants` ("Port of Entry", 212 names) as a species list of species recorded at ports of entry; drop its specimen and image numbers (page totals and per-row columns); the page says these species were intercepted at ports of entry, are not established introductions, and are not counted in any place total or the introduced species list; no nightly step recounts its numbers; the check skips them with a note | `Project.isSpeciesListOnly` / `SPECIES_LIST_ONLY_SQL`: ProjTaxonCountDb (recount, curated crawl, image crawl), ProjectDb (updateCounts, finish, makeCharts) and PlaceImageCountDb leave it out and set its specimen and image numbers to 0 (list counts of Subfamilies, Genera, Species, Valid species are kept); project page: an "About this list" row instead of Specimen holdings and Image documentation, no Specimens pie; its list: no Specimens, Species, Genera or Images columns, the note under the list; projects.do: "-". Nothing else uses the project: place totals count geolocale, museum and bioregion lists, and the introduced flags come from the introduced-species list. Check: `CountInvariants.scopeSql` and the rotating sample leave it out; the report says so (`SPECIES_LIST_ONLY_NOTE`). | page text at once; numbers set to 0 by the next nightly run (set 3) |
| Q12 unimaged | "Unimaged" always means no photo of the ant anywhere in AntWeb; label photos never count | `StatisticsDb.antImagedNames` (the rule of the API's `imagedTaxaSql`): taxonomicPage.do?isImaged=false (header "N Unimaged X (out of M)", 0 included, and the list) with a note under the header; the API page says what unimaged means. The images view's per-place list is labelled "X with no image from this place (N)" (row 62). | at once |


### 5.3 Search rules decided by Brian Fisher 2026-10-27 (rows 67, 68)

| Decision | Change | Rows | Visible after |
|---|---|---|---|
| Multi-word search values: phrase match; "equals": exact match | A box value is a list of entries separated by commas, each entry one phrase (before: every word was a separate entry, so "Santa Cruz" counted the specimens of Santa or Cruz); "not equals" excludes every entry (before: matched every specimen); taxon name "equals" matches the whole name or its last part (before: a suffix, "rufa" also counted "pseudorufa"). The N specimens and N taxa of a search with such a value are smaller (they now count only what the value says). Details: [flows/search.md](flows/search.md#search-rules-decided-on-2026-10-27). Test: `junit:SearchRulesTest#testSeveralWordsAreOnePhrase`, `junit:SearchRulesTest#testNotEqualsExcludesEveryEntry`, `junit:SearchRulesTest#testTaxonNameEqualsIsNotASuffix` | 67, 68 | at once |
| Searches over 50,000 specimens: one fixed order | The first 50,000 by specimen code, every time (before: every other search reversed the order, so a by-taxon or map search of more than 50,000 specimens counted two different sets in turn). Test: `unit:test_decided_pages.py::test_search_has_one_fixed_order` | 68 | at once |
