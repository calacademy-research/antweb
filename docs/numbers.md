# Every number on AntWeb and the test that checks it

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

## 2. Inventory

Coverage after 2026-10-22: **67 validated, 51 partly, 0 not yet** of 118 (2026-10-21: 42, 22, 54; first inventory
2026-10-20: 9, 7, 102).

| # | Page | Number | How it is computed | Status | Test |
|---|---|---|---|---|---|
| 1 | home page `/` (genInc/statistics.jsp) | N valid species + ssp. | statistics row (StatisticsDb.populateStatistics): valid species and subspecies, fossils included; written after every specimen upload and, since 2026-10-21, every night (set 4) | yes | inv:home |
| 2 | home page `/` (genInc/statistics.jsp) | N specimen records | statistics row: all specimen records | yes | inv:home |
| 3 | home page `/` (genInc/statistics.jsp) | N species + ssp. imaged | statistics row: species and subspecies names (counted status) with a specimen that has an image of the ant (not a label); was genus/species/subspecies strings, labels and every status counted | yes | inv:home; junit:PlaceTotalsDbTest#testHomeNumbersCountImagesOfTheAnt |
| 4 | home page `/` (genInc/statistics.jsp) | N specimens imaged | statistics row: specimens with an image of the ant (labels not counted, as Imaged specimens on place pages) | yes | inv:home; junit:PlaceTotalsDbTest#testHomeNumbersCountImagesOfTheAnt |
| 5 | home page `/` (genInc/statistics.jsp) | N total specimen images | statistics row: all image rows | yes | inv:home |
| 6 | statsPage.do | N valid species + ssp. | TaxonMgr.getValidSpeciesCount(): valid species and subspecies in memory (was: valid taxa of every rank) | yes | inv:home; junit:PlaceTotalsDbTest#testStatsPageCountsValidSpeciesAndSubspecies; e2e:test_numbers_pages.py::test_statspage_valid_species_count_species_and_subspecies |
| 7 | statsPage.do | N specimen records | AntwebMgr, from the latest statistics row (= #2) | yes | inv:home |
| 8 | statsPage.do | N species + ssp. imaged | AntwebMgr (= #3) | yes | inv:home; inv:stats.imaged |
| 9 | statsPage.do | N specimens imaged | AntwebMgr (= #4) | yes | inv:home |
| 10 | statsPage.do | N total specimen images | AntwebMgr (= #5) | yes | inv:home |
| 11 | statsPage.do | Global Data, Extant: valid subfamilies, genera, species, subspecies | live count of the taxon table (StatisticsDb.getExtantData) | yes | inv:stats.global; e2e:test_count_check.py::test_statspage_tables_equal_the_recount; e2e:test_numbers_pages.py::test_statspage_valid_species_count_species_and_subspecies |
| 12 | statsPage.do | Global Data, Fossil (4) | live count (getFossilData) | yes | inv:stats.global; e2e:test_count_check.py::test_statspage_tables_equal_the_recount; e2e:test_numbers_pages.py::test_statspage_valid_species_count_species_and_subspecies |
| 13 | statsPage.do | Images table: Total per specimen status | live (ImageDb.getImageStats) | yes | inv:stats.images; e2e:test_count_check.py::test_statspage_tables_equal_the_recount; junit:PlaceTotalsDbTest#testStatsPageImageTableAddsUp |
| 14 | statsPage.do | Images table: Worker / Male / Queen / Other | live; Other includes images of specimens without a caste (2026-10-21; they were in no column) | yes | inv:stats.images; e2e:test_count_check.py::test_statspage_tables_equal_the_recount; junit:PlaceTotalsDbTest#testStatsPageImageTableAddsUp |
| 15 | statsPage.do | Images table: Sum row | summed in the JSP over every status in the table (was six fixed statuses); images of specimens without a status are in no row | yes | inv:stats.images; e2e:test_count_check.py::test_statspage_tables_equal_the_recount; junit:NumberChecksTest#testStatsPageImagesSumLeavesOutSpecimensWithoutStatus |
| 16 | statsPage.do | Imaged Species per taxon status | live (ImageDb.getImageTaxonStats): names of any rank with a specimen that has any image (labels too), by taxon status; only seven statuses shown | yes | inv:stats.imaged; e2e:test_count_check.py::test_statspage_tables_equal_the_recount |
| 17 | statsPage.do | Bioregion Data (4 per bioregion) | live over bioregion_taxon (StatisticsDb.getBioregionData) | yes | inv:stats.bioregions; junit:NumberChecksTest#testStatsPagePlaceQueriesMatchThePageCode; e2e:test_count_check.py::test_statspage_tables_equal_the_recount |
| 18 | statsPage.do | Museum Data, Valid Extant (4 per museum) | live over museum_taxon | yes | inv:stats.museums; junit:NumberChecksTest#testStatsPagePlaceQueriesMatchThePageCode; e2e:test_count_check.py::test_statspage_tables_equal_the_recount |
| 19 | statsPage.do | Museum Data, Valid Fossil (4 per museum) | live over museum_taxon (no independent recount: the check runs the page's query for all museums; relations to the museum page and Global Data) | partly | inv:stats.museums; junit:NumberChecksTest#testStatsPagePlaceQueriesMatchThePageCode; e2e:test_count_check.py::test_statspage_tables_equal_the_recount |
| 20 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Subfamilies | subfamily rows on the place's list, counted status (PlaceTotals.listedCountSql); museums and bioregions were roll-up sums until 2026-10-21 | yes | inv:place.totals.subfamily; cc:place totals; junit:PlaceTotalsDbTest#testFinishWritesEveryTotalFromTheList |
| 21 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Genera | genus rows on the list, counted status (PlaceTotals); was sum(genus_count) of the subfamily rows | yes | inv:place.totals.genus; junit:PlaceTotalsDbTest#testFinishWritesEveryTotalFromTheList |
| 22 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Species / subspecies | species and subspecies rows on the list, counted status, ants (PlaceTotals); was sum(species_count) of the genus rows | yes | inv:place.totals.species; junit:PlaceTotalsDbTest#testFinishWritesEveryTotalFromTheList |
| 23 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Valid species / subspecies | valid extant species and subspecies rows on the list (PlaceTotals.validSpeciesSql); geolocales: written every night since 2026-10-21 (GeolocaleDb.finish was never called) | yes | inv:place.totals.valid; junit:PlaceTotalsDbTest#testFinishWritesEveryTotalFromTheList |
| 24 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Endemic | rows flagged is_endemic on the list (PlaceTotals.countsFromFlagsSql); the flags by calcEndemic (geolocales: set 2 and after uploads; bioregions: set 3); the count loop was off by one at each place change | partly | inv:place.totals.endemic; junit:PlaceTotalsDbTest#testEndemicNumbersAreTheFlaggedRows; inv:place.endemic.flags; e2e:test_numbers_live.py::test_endemic_and_introduced_lists_equal_place_numbers |
| 25 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Introduced | rows flagged is_introduced on the list (PlaceTotals.countsFromFlagsSql) | partly | inv:place.totals.introduced; e2e:test_numbers_live.py::test_endemic_and_introduced_lists_equal_place_numbers |
| 26 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Specimens | geolocales, projects: sum of the species and subspecies rows' specimens (projects: list recounted first since 2026-10-21); museums, bioregions: their specimens (specimen.museum, specimen.bioregion) | yes | inv:place.totals.specimens; inv:geo.regions; cc:place specimens; junit:CountInvariantsTest#testRecountOfASampledPlaceChecksItsImagesColumn; inv:geo.adm1.country |
| 27 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Imaged specimens | PlaceImageCountDb (set 4): specimens with an image that is not a label | yes | cc:place images; inv:place.images; inv:place.images.order; inv:geo.adm1.country |
| 28 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | (N additional specimens) label only | PlaceImageCountDb: specimens whose only images are labels | partly | inv:place.images |
| 29 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Images | PlaceImageCountDb: all image files (labels included) | yes | cc:place images; inv:place.images; inv:place.images.order; inv:geo.adm1.country |
| 30 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Ant images | PlaceImageCountDb: images that are not labels | yes | cc:place images; inv:place.images |
| 31 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Views: N head, profile, dorsal, ventral, field, other | PlaceImageCountDb: images per shot type | yes | cc:place images; inv:place.images |
| 32 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Label images | PlaceImageCountDb: shot type l | yes | cc:place images; inv:place.images |
| 33 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Imaged specimens / Images before set 4 ran once | place table image_count, imaged_specimen_count | yes | cc:place images; inv:place.images |
| 34 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Species per Subfamily pie | PlaceTotals.speciesPieSql: the rows of Species by subfamily (geolocales counted rank species only; projects and bioregions every rank) | yes | inv:place.pies; junit:PlaceTotalsDbTest#testPiesOfMuseumsAndBioregionsCountTheirOwnSpecimens |
| 35 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Specimens per Subfamily pie | PlaceTotals.specimenPieSql: geolocales, projects the species rows' specimens; museums, bioregions their own specimens (were every specimen in AntWeb of their names); one Other slice for names that are not a subfamily; fitted to its column, a chart never stops an upload | yes | inv:place.pies; junit:PlaceTotalsDbTest#testPiesOfMuseumsAndBioregionsCountTheirOwnSpecimens |
| 36 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Taxa menu N Species | = #22 (getSpeciesCount()) | yes | inv:place.totals.species |
| 37 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Admin: species count link | = #22 | yes | inv:place.totals.species |
| 38 | taxonomicPage.do, browse.do | List header N [status] Species / Genera / Subfamilies | live: rows shown (session status set) | partly | e2e:test_numbers_live.py::test_list_headers_equal_place_numbers |
| 39 | taxonomicPage.do, browse.do | N Unimaged X (out of M) | live over list image_count (labels included) | partly | e2e:test_numbers_live.py::test_unimaged_header_is_within_the_list |
| 40 | taxonomicPage.do, browse.do | Species row N Specimens (place column) | list row specimen_count (nightly crawl); B25 (a subspecies under its species replaces the species' row) checked by taxon.parents | yes | cc:specimens; inv:taxon.parents |
| 41 | taxonomicPage.do, browse.do | Genus row N Species | list row species_count: its species and subspecies rows | yes | inv:list.genus; cc:species; inv:taxon.parents |
| 42 | taxonomicPage.do, browse.do | Subfamily row N Genera | list row genus_count: its genus rows | yes | inv:list.subfamily; cc:genera |
| 43 | taxonomicPage.do, browse.do | Global N Specimens | allantwebants proj_taxon specimen_count: all specimens of the name; rebuilt after uploads and, since 2026-10-21, after the nightly name update | yes | inv:global.specimens; inv:list.global; cc:global; junit:PlaceTotalsDbTest#testGlobalIsRecountedAfterTheNameUpdateMovedSpecimens |
| 44 | taxonomicPage.do, browse.do | Images column N Images | list row image_count (set 4): images (labels included) of the place's specimens of the name; project rows reset first since 2026-10-21 | partly | cc:images; inv:list.images |
| 45 | taxonomicPage.do, browse.do | Admin Subtaxon total | live sum of the shown rows | partly | e2e:test_count_check.py::test_subtaxon_totals_equal_the_place_numbers |
| 46 | taxonomicPage.do, browse.do | browse header N Specimens / Species / Genera (M imaged) | live children (Overview.getOverviewCriteria) | partly | e2e:test_numbers_live.py::test_browse_header_and_hidden_totals_agree |
| 47 | taxonomicPage.do, browse.do | browse (Only the first N of M displayed) | live | partly | e2e:test_numbers_live.py::test_browse_header_and_hidden_totals_agree |
| 48 | taxonomicPage.do, browse.do | browse sub-taxon lines (genus, subfamily, family rows' counts) | list rows of genus, subfamily, family: sums of their child rows (CountDb crawl; every row reset first since 2026-10-21) | yes | inv:list.genus; inv:list.subfamily; inv:list.family; inv:list.images |
| 49 | taxonomicPage.do, browse.do | browse hidden N Imaged X | live | partly | e2e:test_numbers_live.py::test_browse_header_and_hidden_totals_agree |
| 50 | description.do, images.do, comparisons | N times found in <habitat> | live: collection events per habitat, ordered by that number since 2026-10-21 | partly | junit:PlaceTotalsDbTest#testHabitatsAreOrderedByTheNumberShown |
| 51 | description.do, images.do, comparisons | N times <microhabitat> | live, as #50 (10 entries, was 11) | partly | junit:PlaceTotalsDbTest#testHabitatsAreOrderedByTheNumberShown |
| 52 | description.do, images.do, comparisons | N times <method> | live, as #50 (10 entries, was 11) | partly | junit:PlaceTotalsDbTest#testHabitatsAreOrderedByTheNumberShown |
| 53 | description.do, images.do, comparisons | Field guide: the same three summaries | as #50-#52 | partly | junit:PlaceTotalsDbTest#testHabitatsAreOrderedByTheNumberShown |
| 54 | description.do, images.do, comparisons | Admin ImageCount (taxon) | taxon.image_count (set 4): images of the taxon's specimens and of the taxa under it (species were never added to their genus) | yes | inv:taxon.images; junit:PlaceTotalsDbTest#testTaxonImageCountsAddTheTaxaUnderThem |
| 55 | description.do, images.do, comparisons | images.do header N Species Imaged (M total) | live | partly | e2e:test_numbers_live.py::test_image_and_comparison_pages_count_the_same_children |
| 56 | description.do, images.do, comparisons | images.do child N Images | list image_count, falling back to the worldwide taxon.image_count | partly | cc:images; inv:taxon.images; inv:list.images |
| 57 | description.do, images.do, comparisons | taxonomicPage images view: N X Imaged (out of M Total) | live: children with images, out of all children (printed the same number twice) | partly | e2e:test_numbers_pages.py::test_images_view_header_counts_imaged_children |
| 58 | description.do, images.do, comparisons | oneView total | live: the pictures of one view of the children (layout total, not printed) | partly | e2e:test_numbers_live.py::test_image_and_comparison_pages_count_the_same_children |
| 59 | description.do, images.do, comparisons | mapComparison (N X Mapped) | live: every child, mapped or not | partly | e2e:test_numbers_live.py::test_image_and_comparison_pages_count_the_same_children |
| 60 | description.do, images.do, comparisons | comparison.do child count | live: one checkbox per child with images (layout total, not printed) | partly | e2e:test_numbers_live.py::test_image_and_comparison_pages_count_the_same_children |
| 61 | description.do, images.do, comparisons | specimenReport N Images | list or taxon image_count | partly | inv:taxon.images; inv:list.images |
| 62 | description.do, images.do, comparisons | unImagedTaxa Non-imaged X (N) | live | partly | e2e:test_numbers_live.py::test_unimaged_names_listed_match_their_number |
| 63 | specimen, collection, locality pages | Specimen Image Count (admin) | specimen.image_count (set 4) | yes | inv:taxon.images |
| 64 | specimen, collection, locality pages | specimenImages N Images | live: the specimen's images | partly | e2e:test_numbers_live.py::test_specimen_images_count_equals_the_api |
| 65 | specimen, collection, locality pages | collection N specimens of M species | live | partly | e2e:test_numbers_live.py::test_collection_and_locality_counts_equal_their_lists |
| 66 | specimen, collection, locality pages | locality N collections | live | partly | e2e:test_numbers_live.py::test_collection_and_locality_counts_equal_their_lists |
| 67 | search results | Advanced search N specimens (M imaged) | live, HTTP session | partly | e2e:test_numbers_live.py::test_search_counts_agree; e2e:test_download_guards.py::test_search_list_is_paged_and_fast |
| 68 | search results | Advanced search by taxon N species (from M specimens) | live, session | partly | e2e:test_numbers_live.py::test_search_counts_agree |
| 69 | search results | Compare / map preparation counts | live | partly | e2e:test_numbers_live.py::test_search_counts_agree |
| 70 | search results | multiTaxaComparison N specimens | live | partly | e2e:test_numbers_live.py::test_search_counts_agree |
| 71 | search results | Recent images N Images | live | partly | e2e:test_numbers_live.py::test_recent_images_count_equals_the_rows_shown |
| 72 | search results | Bay Area search N records | no longer shown: /bayAreaSearch was removed (2026-10-19), so the page cannot be reached | yes | e2e:test_actions.py::test_removed_actions_stay_removed |
| 73 | countries.do, regions.do, subregions.do, adm1s.do, projects.do, museums.do, bioregions.do | Valid species column (labelled Species until 2026-10-21) | = #23 | yes | inv:place.totals.valid; e2e:test_numbers_pages.py::test_list_of_countries_labels_valid_species |
| 74 | countries.do, regions.do, subregions.do, adm1s.do, projects.do, museums.do, bioregions.do | Specimens column | = #26 | yes | inv:place.totals.specimens |
| 75 | countries.do, regions.do, subregions.do, adm1s.do, projects.do, museums.do, bioregions.do | Images column | = #29 | yes | inv:place.images |
| 76 | countries.do, regions.do, subregions.do, adm1s.do, projects.do, museums.do, bioregions.do | Image Submissions pie | = #75 per place | yes | inv:place.images |
| 77 | countries.do, regions.do, subregions.do, adm1s.do, projects.do, museums.do, bioregions.do | Specimen Submissions pie | = #74 per place | yes | inv:place.totals.specimens; inv:geo.regions |
| 78 | endemic.do, introduced.do | endemic.do rows | live over is_endemic flags | partly | inv:place.totals.endemic; inv:place.endemic.flags; e2e:test_numbers_live.py::test_endemic_and_introduced_lists_equal_place_numbers |
| 79 | endemic.do, introduced.do | introduced.do rows | live over is_introduced flags | partly | inv:place.totals.introduced; e2e:test_numbers_live.py::test_endemic_and_introduced_lists_equal_place_numbers |
| 80 | curators, groups, artists, uploads (admin) | curators.do Specimen Uploads | LoginMgr at startup | yes | inv:admin.curators; e2e:test_count_check.py::test_curator_and_artist_pages_equal_the_recount |
| 81 | curators, groups, artists, uploads (admin) | curators.do Description Edits | LoginMgr at startup | yes | inv:admin.curators; e2e:test_count_check.py::test_curator_and_artist_pages_equal_the_recount |
| 82 | curators, groups, artists, uploads (admin) | curator.do specimen uploads | LoginMgr at startup | yes | inv:admin.curators; e2e:test_count_check.py::test_curator_and_artist_pages_equal_the_recount |
| 83 | curators, groups, artists, uploads (admin) | curator.do last upload numbers | the curator's last upload row (written at the upload); groups.do shows the group's last upload row, checked for every group | partly | inv:admin.groups |
| 84 | curators, groups, artists, uploads (admin) | curator.do image uploads, images uploaded | LoginMgr at startup | yes | inv:admin.curators; e2e:test_count_check.py::test_curator_and_artist_pages_equal_the_recount |
| 85 | curators, groups, artists, uploads (admin) | groups.do Uploads | live count of upload rows (GroupAction); ant_group.upload_count orders the page | yes | inv:admin.groups; e2e:test_count_check.py::test_group_pages_equal_the_stored_upload_numbers |
| 86 | curators, groups, artists, uploads (admin) | groups.do Specimens | upload row | yes | inv:admin.groups; e2e:test_count_check.py::test_group_pages_equal_the_stored_upload_numbers |
| 87 | curators, groups, artists, uploads (admin) | groups.do Ungeoreferenced, Flagged, Collections, Localities | upload row | yes | inv:admin.groups; e2e:test_count_check.py::test_group_pages_equal_the_stored_upload_numbers |
| 88 | curators, groups, artists, uploads (admin) | groups.do Subfamilies, Genera, Species | upload row: count(distinct species) counts epithets, of valid names only (UploadDb.getCounts, getAntwebGenusCriteria) | yes | inv:admin.groups; e2e:test_count_check.py::test_group_pages_equal_the_stored_upload_numbers |
| 89 | curators, groups, artists, uploads (admin) | groups.do Totals row | live sums | yes | e2e:test_count_check.py::test_group_pages_equal_the_stored_upload_numbers; inv:admin.groups |
| 90 | curators, groups, artists, uploads (admin) | groups.do Totals Subfamilies / Genera / Species | live maximum of the rows, labelled Totals | partly | e2e:test_count_check.py::test_group_pages_equal_the_stored_upload_numbers |
| 91 | curators, groups, artists, uploads (admin) | group.do uploads, last upload numbers | upload row | yes | e2e:test_count_check.py::test_group_pages_equal_the_stored_upload_numbers; inv:admin.groups |
| 92 | curators, groups, artists, uploads (admin) | artists.do Images, Specimen | live / ArtistMgr | yes | inv:admin.artists; e2e:test_count_check.py::test_curator_and_artist_pages_equal_the_recount |
| 93 | curators, groups, artists, uploads (admin) | listImageUploads totals | live / image_upload | yes | inv:admin.imageUploads; e2e:test_count_check.py::test_image_upload_and_orphan_counts_equal_the_recount |
| 94 | curators, groups, artists, uploads (admin) | imageUploadReport counts | image_upload.image_count, written when the upload ends; saved and not saved files | partly | inv:admin.imageUploads |
| 95 | curators, groups, artists, uploads (admin) | Upload report Inserted / Updated Species | at upload | partly | e2e:test_specimen_upload_roundtrip.py::test_specimen_upload_round_trip |
| 96 | curators, groups, artists, uploads (admin) | Upload report message counts | at upload; static error list (B23) | partly | e2e:test_specimen_upload_roundtrip.py::test_specimen_upload_round_trip |
| 97 | curators, groups, artists, uploads (admin) | Admin statistics.do table | statistics rows (latest row checked; older rows are history); V. Sp. Imaged counts valid species and subspecies imaged since 2026-10-21 | yes | inv:home; inv:admin.statistics |
| 98 | curators, groups, artists, uploads (admin) | statistics.do breakdowns | live; byUpload (specimens per upload group) checked; byProject, byBioregion, byMuseum, byGeolocale are row counts of the lists | partly | inv:admin.statistics |
| 99 | curators, groups, artists, uploads (admin) | orphanedImages Found N | live: specimen codes of images whose specimen is not in AntWeb (labelled images, counts codes) | yes | inv:stats.images; e2e:test_count_check.py::test_image_upload_and_orphan_counts_equal_the_recount |
| 100 | API v3.1 | /specimens imageCount | specimen.image_count | yes | inv:taxon.images; e2e:test_api_numbers.py::test_images_meta_counts_and_specimen_image_count; e2e:test_numbers_live.py::test_specimen_images_count_equals_the_api |
| 101 | API v3.1 | /specimens, /geoSpecimens, ... meta count | live: rows in this page | partly | e2e:test_api_numbers.py::test_meta_count_is_the_rows_returned |
| 102 | API v3.1 | /geoSpeciesStats caste counts | live | partly | e2e:test_api_numbers.py::test_geo_species_stats_castes_add_up |
| 103 | API v3.1 | /taxa subfamilyCount, genusCount, speciesCount, specimenCount | taxon columns that no current code writes (B11); must be the name's Global row | yes | inv:api.taxa |
| 104 | API v3.1 | /taxa imageCount | taxon.image_count (= #54) | yes | inv:taxon.images |
| 105 | API v3.1 | /taxa meta taxonCount | live | partly | e2e:test_api_numbers.py::test_meta_count_is_the_rows_returned |
| 106 | API v3.1 | /images meta counts | live: rows in this page; specimenCount was 1 and one empty specimen when nothing was found, and every specimen but the last was under 'spefimenCode' (fixed 2026-10-22) | partly | e2e:test_api_numbers.py::test_images_meta_counts_and_specimen_image_count |
| 107 | API v3.1 | /taxaImages meta counts | live | partly | e2e:test_api_numbers.py::test_taxa_images_meta_counts |
| 108 | API v3.1 | /geolocales subfamily, genus, species, specimen counts | = #20-#22, #26 | yes | inv:place.totals.subfamily; inv:place.totals.genus; inv:place.totals.species; inv:place.totals.specimens |
| 109 | API v3.1 | /geolocales imageCount, imagedSpecimenCount | = #29, #27 | yes | inv:place.images; cc:place images |
| 110 | API v3.1 | /geolocales endemic, introduced counts | = #24, #25 | partly | inv:place.totals.endemic; inv:place.totals.introduced; inv:place.endemic.flags |
| 111 | API v3.1 | /geolocales meta geolocaleCount | live: geolocales returned | partly | e2e:test_api_numbers.py::test_meta_count_is_the_rows_returned |
| 112 | API v3.1 | /geolocaleTaxa per row counts | list rows | partly | inv:list.genus; cc:specimens; cc:images; inv:list.images |
| 113 | API v3.1 | /geolocaleTaxa meta count | live: taxa returned (named geolocaleCount) | partly | e2e:test_api_numbers.py::test_meta_count_is_the_rows_returned |
| 114 | API v3.1 | /bioregions 6 counts | bioregion table (= place page) | yes | inv:place.totals.subfamily; inv:place.totals.genus; inv:place.totals.species; inv:place.totals.specimens; inv:place.images |
| 115 | API v3.1 | /bioregions meta count | live | yes | e2e:test_api_numbers.py::test_meta_count_is_the_rows_returned; e2e:test_api_numbers.py::test_bioregions_count_equals_the_bioregions_page |
| 116 | API v3.1 | /bioregionTaxa per row counts | list rows | partly | inv:list.genus; cc:specimens; inv:list.images |
| 117 | API v3.1 | /unimagedGeolocaleTaxa list | live: names without a specimen with an image of the ant (was image_count > 1) | partly | unit:test_api_counts.py::test_a_taxon_with_one_image_of_the_ant_is_imaged; unit:test_api_counts.py::test_endpoint_uses_the_rule; e2e:test_api_numbers.py::test_meta_count_is_the_rows_returned |
| 118 | API v3.1 | /bioregionTaxa, /geolocaleTaxa meta and dev counters | live: taxa returned (geolocaleTaxa: geolocaleCount, bioregionTaxa: bioregionCount, unimagedGeolocaleTaxa: unimagedGeolocaleTaxaCount) | partly | e2e:test_api_numbers.py::test_meta_count_is_the_rows_returned |

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
| B11 | taxon image_count of genus and above wrong; API /taxa counts with no writer | image_count **fixed** (species added to genera, each rank added to its parent's own images). Test: `inv:taxon.images`. The /taxa subfamilyCount, genusCount, speciesCount, specimenCount: **planned** (section 4). |
| B12 | statsPage "valid species + ssp." counted valid taxa of every rank | **Fixed** (`TaxonMgr.getValidSpeciesCount`). Test: `inv:home`, e2e. |
| B13 | Home and statsPage numbers written only after uploads; labels counted; species by name strings | **Fixed**: also written every night (set 4); imaged = images of the ant; species by name with a counted status; statistics.valid_species_imaged counts species (it counted images). Test: `inv:home`. |
| B14 | Upload and group Species count epithets; groups.do "Totals" are maxima | **Planned** (section 4). |
| B15 | Counts and the pages they link to use different rules | **Planned**. |
| B16 | Row counts not reset before the crawl (project Images; bioregion AntCat rows; geolocale genus and subfamily rows) | **Fixed**: every count column of a geolocale list, the counts of bioregion AntCat rows, project subfamily_count and project Images reset before the crawl. Test: `inv:list.*`, `cc:images`. |
| B17 | statsPage image table did not add up (NULL caste; Sum of six statuses) | **Fixed**. Test: `PlaceTotalsDbTest#testStatsPageImageTableAddsUp`. |
| B18 | List-of-places column labelled Species shows Valid species | **Fixed** (label "Valid species"; the number itself was B1). Test: e2e. |
| B19 | mapComparison "N X Mapped" counts every child | **Planned**. |
| B20 | Search counts kept in the HTTP session | **Planned**. |
| B21 | "Unimaged" header falls back to the total; label-only taxa count as imaged | **Planned**. |
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
- **Port of Entry** (open): its page shows 37,430 specimens while its list has no species with specimens
  (staging). The nightly does not recount it. Decide: hide it as a place (is_valid = 0) or count interceptions
  as a place; until then the check shows it as a real difference.

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
  one empty specimen; every specimen but the last was under the key `spefimenCode`. Test:
  `e2e:test_api_numbers.py::test_images_meta_counts_and_specimen_image_count`.
- **B30** (fixed, trivial) API `/geoSpeciesStats`: `queenErgatoidCount` and `maleErgatoidCount` both counted every
  ergatoid specimen (no caste condition), so a male ergatoid was also counted as a queen ergatoid. Now each counts
  its caste. Test: `e2e:test_api_numbers.py::test_geo_species_stats_castes_add_up`.
- **B31** statsPage Bioregion Data and Museum Data count valid taxa of every family on the lists (no
  `family = 'formicidae'`), while the place pages count ants only since 2026-10-21; `inv:stats.bioregions` and
  `inv:stats.museums` report a difference where a non-ant is on a list. Question: section 5 Q7.
- **B32** API `/geolocaleTaxa` and `/bioregionTaxa` name their taxon count `geolocaleCount` and `bioregionCount`
  (label only; the number is the taxa returned).
- **B33** groups.do and group.do "Species" (upload row, UploadDb.getCounts) count distinct species epithets of
  **valid** names only (`getAntwebGenusCriteria` has `status = 'valid'`), and Subfamilies and Genera only AntWeb's
  extant subfamilies and genera (B14 had the epithets). Question: section 5 Q8.
- **B34** home "N valid species + ssp." (and statsPage Global Data) count valid taxa of every family, not ants
  only; statsPage "Imaged Species" counts names of any rank and labels (B13 fixed the home number only).
  Question: section 5 Q6.

## 4. Plans for the larger ones

| Id | Plan |
|---|---|
| B11 | API /taxa `subfamilyCount`, `genusCount`, `speciesCount`, `specimenCount`: read the allantwebants row (`proj_taxon`) instead of taxon columns nobody writes; document in the API page. The check `inv:api.taxa` (2026-10-22) compares the taxon columns with the Global row for every taxon (where the columns exist; real differences until fixed). |
| B14 | Upload Species: `count(distinct taxon_name)` of species rank at upload; groups.do: real totals (distinct over all groups) or rename the row "Largest"; test against `upload` and `specimen`. |
| B15 | One place rule for counts and links: `Overview.getOverviewCriteria` uses `Geolocale.getSpecimenPlaceCriteria` (country = X, island_country for islands, the countries of a region); browse filters on `specimen.status` like the counts; e2e: for 3 random species rows of the sampled places, the browse page's count equals the row. |
| B19 | Count children with coordinates; label "N of M mapped". |
| B20 | Keep search totals in the request, not the session; count imaged as images of the ant; fix "N specimens Specimens". |
| B21 | Header says "0 unimaged" instead of falling back; imaged = an image of the ant (list image counts without labels, or a separate column). |
| B22 | Curator counts: recount when an upload or image upload ends; place pages: an e2e test comparing each sampled place's page (`data-stat` numbers) with `countCheck.do?format=json`; alert when set 5 fails. |
| B23 | `MessageMgr` errors per upload (instance, not static). |
| B25 | Checked by `inv:taxon.parents`; if it finds any: set the parent of those subspecies to their genus (as uploads do), or make the crawl group species-rank rows by genus. |
| B26 | Add the country in the six places listed in section 3 and in the count check's recount (`CountCheck.Place` of an adm1: `and s.country = parent`); `inv:geo.adm1.country` then shows no difference. |
| museum, bioregion lists | set 3 still renames synonym rows of museum and bioregion lists after their counts (rows of a genus may then not add up: `inv:list.genus`). Plan: run the rename before `populateMuseum` and `populateBioregion` count, or recount their lists after it. |
| partly rows | Most are live numbers checked by the end-to-end tests on Madagascar and the genus Pheidole only, or by a consistency rule. Next: a rotating place for the e2e tests (the count check's rotating sample, in its JSON), and recounts in the check for the live numbers that have a SQL definition (taxonomicPage headers, browse counts, the habitat lists of a sample species). |

## 5. Questions for Brian

Where the right number is a scientific choice, the checks test the rule as the code has it now and the question is
here, with a recommendation; nothing was decided. Each answer becomes a rule in the code and in its check.

| # | Question | Now | Recommendation | Rows |
|---|---|---|---|---|
| Q1 | Do subspecies count in species totals? | Place "Species / subspecies" counts species and subspecies names (a species with two subspecies counts 3); a genus row's "N Species" counts both; a species row's Specimens are only the specimens filed under the species name (its subspecies have rows of their own); statsPage "valid species + ssp." counts both. | Keep counting both, as the label says, and label the genus row "Species / subspecies" too. Keep a species row to its own specimens (the subspecies rows are listed under it), so no specimen is counted twice in a place total. | 20-22, 41, 1, 6 |
| Q2 | Do morphospecies and indet names count as "species"? | Species / subspecies: yes (valid, unrecognized, morphotaxon, indetermined, unidentifiable); Valid species: valid, extant only; statsPage "species + ssp. imaged" counts the same five statuses. | Keep both numbers as they are; they are labelled and defined on the page. | 22, 23, 3 |
| Q3 | Endemic at list edges: which records decide that a name is endemic? | A name is endemic to a country if it is on exactly one country's list, counting lists of any source (specimens, literature, curator lists) and of invalid country rows; morphospecies and fossils excluded. An adm1: endemic to its country and on one adm1 list. A subregion or region shows the names endemic to one of its countries (not the names found only in that subregion). A bioregion includes morphospecies. | Count valid countries only; keep all sources; one rule for all places: exclude morphospecies everywhere (their codes are not comparable between collections), and for subregions and regions "found in no country outside it". Records of introduced populations should not make a native species non-endemic (see Q4). | 24, 78, 110 |
| Q4 | Introduced species: how do they count? | They count in Species, Valid species and Specimens of the place where they are introduced; the Introduced number is the rows flagged from the introduced-species list; an introduced record elsewhere makes a species non-endemic at home. | Keep them in the place totals (they occur there), show "of which N introduced" under Species, and ignore introduced records when computing endemism. | 22-25, 79 |
| Q5 | Fossils: do fossil taxa count in place totals? | Subfamilies, Genera and Species / subspecies of a place count fossil taxa on its list; Valid species excludes them; taxonomicPage counts them too (withFossil). | Exclude fossils from Subfamilies, Genera and Species / subspecies of living places, and show "Fossil taxa: N" as its own row (amber and fossil records stay listed). | 20-22, 38 |
| Q6 | "Imaged": images of the ant only, or labels too; species only, or any name? | Home and place pages: a specimen with an image that is not a label; statsPage "Imaged Species" per status: any rank (genus-level names too) and label images; taxonomicPage "Unimaged" and list Images count labels. | One rule everywhere: imaged = a specimen with an image of the ant (not a label); "Imaged Species" counts species and subspecies names only. | 3, 16, 39, 44, 117 |
| Q7 | Ants only on statsPage? | statsPage Global Data, Bioregion Data and Museum Data, and the home "valid species + ssp.", count valid taxa of every family on the lists; place pages count ants only (2026-10-21). | Ants only everywhere (Formicidae), as on the place pages. | 1, 6, 11, 12, 17-19 |
| Q8 | Upload group numbers: what is a "species", and what are "Totals"? | groups.do Species = distinct species epithets of valid names of AntWeb's extant genera (Pheidole minor and Camponotus minor count once; morphospecies not counted); Totals of Subfamilies, Genera, Species = the largest group. | Species = distinct names (taxon_name) of species and subspecies of the five counted statuses; Totals = distinct over all groups (or rename the row "Largest"). | 86-90, 83, 91 |
| Q9 | Orphan images: count images or codes? | "Found N orphaned images" counts specimen codes that have images but no specimen record. | Show both: "N images of M specimen codes that are not in AntWeb". | 99 |
| Q10 | Specimens with an adm1 but no country (B26): count them for the state? | Counted for every adm1 of that name. | Not counted for any adm1 (the state is ambiguous without a country); the fix adds `country = parent`. | 26, 27, 29 |
| Q11 | Port of Entry: a place? | Its page shows 37,430 specimens while its list has no species with specimens; the nightly run does not recount it. | Hide it as a place (is_valid = 0) and keep interceptions searchable. | 26 |
| Q12 | Unimaged names: unimaged where? | API /unimagedGeolocaleTaxa: no image of the ant anywhere in AntWeb; taxonomicPage "Unimaged": no image (labels included) from this place. | Both: "no image of the ant anywhere" (what a curator needs to photograph), labelled so. | 39, 117 |
