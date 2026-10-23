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
| `inv:<id>` | CountInvariants (`src/org/calacademy/antweb/util/CountInvariants.java`): for **every** place of every kind, the stored number against the number it must equal or not exceed, with grouped queries (one per check for all places). | `countCheck.do` (admins, read-only); on staging in the end-to-end suite (`tests/e2e/test_count_check.py`) on every pull request and every day; on production read-only every day. Database behaviour: `CountInvariantsTest` (CI, MySQL 8.4). |
| `cc:<check>` | CountCheck (`CountCheck.java`): recount from the specimen and image records for a sample of places: Madagascar, Reunion, the country with the most morphospecies, the subregion of Madagascar, the largest live project, CASC, Malagasy, plus a rotating slice so every country, subregion, region, project and museum is recounted within a week and every live adm1 within a month (`CountCheckAction.rotatingSample`). | same page and tests; `CountCheckTest` |
| `junit:<Class>#<method>` | JUnit test against MySQL 8.4 in CI (`test/`). | CI "Tests" |
| `e2e:<file>::<function>` | end-to-end test of the page on staging (`tests/e2e/`). | e2e workflow |
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

Status: **yes** = the number is compared with an independent recount or with the number it must equal, on every
place (or a rotating sample covering every place); **partly** = only some places or parts, or only a consistency
rule; **not yet** = no test (see the plans in section 4).

## 2. Inventory

Coverage after 2026-10-22: **48 validated, 23 partly, 47 not yet** of 118 (2026-10-21: 42, 22, 54; before: 9, 7, 102). The public page flows of road map step 2 added the API meta counts and the locality page's collections ([flows/api.md](flows/api.md), [flows/specimen-and-image-pages.md](flows/specimen-and-image-pages.md)).

| # | Page | Number | How it is computed | Status | Test |
|---|---|---|---|---|---|
| 1 | home page `/` (genInc/statistics.jsp) | N valid species + ssp. | statistics row (StatisticsDb.populateStatistics): valid species and subspecies, fossils included; written after every specimen upload and, since 2026-10-21, every night (set 4) | yes | inv:home |
| 2 | home page `/` (genInc/statistics.jsp) | N specimen records | statistics row: all specimen records | yes | inv:home |
| 3 | home page `/` (genInc/statistics.jsp) | N species + ssp. imaged | statistics row: species and subspecies names (counted status) with a specimen that has an image of the ant (not a label); was genus/species/subspecies strings, labels and every status counted | yes | inv:home; junit:PlaceTotalsDbTest#testHomeNumbersCountImagesOfTheAnt |
| 4 | home page `/` (genInc/statistics.jsp) | N specimens imaged | statistics row: specimens with an image of the ant (labels not counted, as Imaged specimens on place pages) | yes | inv:home; junit:PlaceTotalsDbTest#testHomeNumbersCountImagesOfTheAnt |
| 5 | home page `/` (genInc/statistics.jsp) | N total specimen images | statistics row: all image rows | yes | inv:home |
| 6 | statsPage.do | N valid species + ssp. | TaxonMgr.getValidSpeciesCount(): valid species and subspecies in memory (was: valid taxa of every rank) | yes | inv:home; junit:PlaceTotalsDbTest#testStatsPageCountsValidSpeciesAndSubspecies; e2e:test_numbers_pages.py::test_statspage_valid_species_count_species_and_subspecies |
| 7 | statsPage.do | N specimen records | AntwebMgr, from the latest statistics row (= #2) | yes | inv:home |
| 8 | statsPage.do | N species + ssp. imaged | AntwebMgr (= #3) | yes | inv:home |
| 9 | statsPage.do | N specimens imaged | AntwebMgr (= #4) | yes | inv:home |
| 10 | statsPage.do | N total specimen images | AntwebMgr (= #5) | yes | inv:home |
| 11 | statsPage.do | Global Data, Extant: valid subfamilies, genera, species, subspecies | live count of the taxon table (StatisticsDb.getExtantData) | partly | e2e:test_numbers_pages.py::test_statspage_valid_species_count_species_and_subspecies |
| 12 | statsPage.do | Global Data, Fossil (4) | live count (getFossilData) | partly | e2e:test_numbers_pages.py::test_statspage_valid_species_count_species_and_subspecies |
| 13 | statsPage.do | Images table: Total per specimen status | live (ImageDb.getImageStats) | partly | junit:PlaceTotalsDbTest#testStatsPageImageTableAddsUp |
| 14 | statsPage.do | Images table: Worker / Male / Queen / Other | live; Other includes images of specimens without a caste (2026-10-21; they were in no column) | partly | junit:PlaceTotalsDbTest#testStatsPageImageTableAddsUp |
| 15 | statsPage.do | Images table: Sum row | summed in the JSP over every status in the table (was six fixed statuses) | not yet | not yet: e2e reading the table (Sum Total = home total images minus orphan images) |
| 16 | statsPage.do | Imaged Species per taxon status | live (ImageDb.getImageTaxonStats) | not yet | not yet |
| 17 | statsPage.do | Bioregion Data (4 per bioregion) | live over bioregion_taxon (StatisticsDb.getBioregionData) | not yet | not yet |
| 18 | statsPage.do | Museum Data, Valid Extant (4 per museum) | live over museum_taxon | not yet | not yet |
| 19 | statsPage.do | Museum Data, Valid Fossil (4 per museum) | live over museum_taxon | not yet | not yet |
| 20 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Subfamilies | subfamily rows on the place's list, counted status (PlaceTotals.listedCountSql); museums and bioregions were roll-up sums until 2026-10-21 | yes | inv:place.totals.subfamily; cc:place totals; junit:PlaceTotalsDbTest#testFinishWritesEveryTotalFromTheList |
| 21 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Genera | genus rows on the list, counted status (PlaceTotals); was sum(genus_count) of the subfamily rows | yes | inv:place.totals.genus; junit:PlaceTotalsDbTest#testFinishWritesEveryTotalFromTheList |
| 22 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Species / subspecies | species and subspecies rows on the list, counted status, ants (PlaceTotals); was sum(species_count) of the genus rows | yes | inv:place.totals.species; junit:PlaceTotalsDbTest#testFinishWritesEveryTotalFromTheList |
| 23 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Valid species / subspecies | valid extant species and subspecies rows on the list (PlaceTotals.validSpeciesSql); geolocales: written every night since 2026-10-21 (GeolocaleDb.finish was never called) | yes | inv:place.totals.valid; junit:PlaceTotalsDbTest#testFinishWritesEveryTotalFromTheList |
| 24 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Endemic | rows flagged is_endemic on the list (PlaceTotals.countsFromFlagsSql); the flags by calcEndemic (geolocales: set 2 and after uploads; bioregions: set 3); the count loop was off by one at each place change | partly | inv:place.totals.endemic; junit:PlaceTotalsDbTest#testEndemicNumbersAreTheFlaggedRows |
| 25 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Introduced | rows flagged is_introduced on the list (PlaceTotals.countsFromFlagsSql) | partly | inv:place.totals.introduced |
| 26 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Specimens | geolocales, projects: sum of the species and subspecies rows' specimens (projects: list recounted first since 2026-10-21); museums, bioregions: their specimens (specimen.museum, specimen.bioregion) | yes | inv:place.totals.specimens; inv:geo.regions; cc:place specimens; junit:CountInvariantsTest#testRecountOfASampledPlaceChecksItsImagesColumn |
| 27 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Imaged specimens | PlaceImageCountDb (set 4): specimens with an image that is not a label | yes | cc:place images; inv:place.images; inv:place.images.order |
| 28 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | (N additional specimens) label only | PlaceImageCountDb: specimens whose only images are labels | partly | inv:place.images |
| 29 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Images | PlaceImageCountDb: all image files (labels included) | yes | cc:place images; inv:place.images; inv:place.images.order |
| 30 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Ant images | PlaceImageCountDb: images that are not labels | yes | cc:place images; inv:place.images |
| 31 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Views: N head, profile, dorsal, ventral, field, other | PlaceImageCountDb: images per shot type | yes | cc:place images; inv:place.images |
| 32 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Label images | PlaceImageCountDb: shot type l | yes | cc:place images; inv:place.images |
| 33 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Imaged specimens / Images before set 4 ran once | place table image_count, imaged_specimen_count | yes | cc:place images; inv:place.images |
| 34 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Species per Subfamily pie | PlaceTotals.speciesPieSql: the rows of Species by subfamily (geolocales counted rank species only; projects and bioregions every rank) | yes | inv:place.pies; junit:PlaceTotalsDbTest#testPiesOfMuseumsAndBioregionsCountTheirOwnSpecimens |
| 35 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Specimens per Subfamily pie | PlaceTotals.specimenPieSql: geolocales, projects the species rows' specimens; museums, bioregions their own specimens (were every specimen in AntWeb of their names); one Other slice for names that are not a subfamily; fitted to its column, a chart never stops an upload | yes | inv:place.pies; junit:PlaceTotalsDbTest#testPiesOfMuseumsAndBioregionsCountTheirOwnSpecimens |
| 36 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Taxa menu N Species | = #22 (getSpeciesCount()) | yes | inv:place.totals.species |
| 37 | place pages (country, adm1, island, region, subregion, project, museum, bioregion) | Admin: species count link | = #22 | yes | inv:place.totals.species |
| 38 | taxonomicPage.do, browse.do | List header N [status] Species / Genera / Subfamilies | live: rows shown (session status set) | not yet | not yet: e2e comparing the header (statusSet=all) with #20-#22 |
| 39 | taxonomicPage.do, browse.do | N Unimaged X (out of M) | live over list image_count (labels included) | not yet | not yet |
| 40 | taxonomicPage.do, browse.do | Species row N Specimens (place column) | list row specimen_count (nightly crawl) | yes | cc:specimens |
| 41 | taxonomicPage.do, browse.do | Genus row N Species | list row species_count: its species and subspecies rows | yes | inv:list.genus; cc:species |
| 42 | taxonomicPage.do, browse.do | Subfamily row N Genera | list row genus_count: its genus rows | yes | inv:list.subfamily; cc:genera |
| 43 | taxonomicPage.do, browse.do | Global N Specimens | allantwebants proj_taxon specimen_count: all specimens of the name; rebuilt after uploads and, since 2026-10-21, after the nightly name update | yes | inv:global.specimens; inv:list.global; cc:global; junit:PlaceTotalsDbTest#testGlobalIsRecountedAfterTheNameUpdateMovedSpecimens |
| 44 | taxonomicPage.do, browse.do | Images column N Images | list row image_count (set 4): images (labels included) of the place's specimens of the name; project rows reset first since 2026-10-21 | partly | cc:images |
| 45 | taxonomicPage.do, browse.do | Admin Subtaxon total | live sum of the shown rows | not yet | not yet |
| 46 | taxonomicPage.do, browse.do | browse header N Specimens / Species / Genera (M imaged) | live children (Overview.getOverviewCriteria) | not yet | not yet: B15 plan |
| 47 | taxonomicPage.do, browse.do | browse (Only the first N of M displayed) | live | not yet | not yet |
| 48 | taxonomicPage.do, browse.do | browse sub-taxon lines (genus, subfamily, family rows' counts) | list rows of genus, subfamily, family: sums of their child rows (CountDb crawl; every row reset first since 2026-10-21) | yes | inv:list.genus; inv:list.subfamily; inv:list.family |
| 49 | taxonomicPage.do, browse.do | browse hidden N Imaged X | live | not yet | not yet |
| 50 | description.do, images.do, comparisons | N times found in <habitat> | live: collection events per habitat, ordered by that number since 2026-10-21 | partly | junit:PlaceTotalsDbTest#testHabitatsAreOrderedByTheNumberShown |
| 51 | description.do, images.do, comparisons | N times <microhabitat> | live, as #50 (10 entries, was 11) | partly | junit:PlaceTotalsDbTest#testHabitatsAreOrderedByTheNumberShown |
| 52 | description.do, images.do, comparisons | N times <method> | live, as #50 (10 entries, was 11) | partly | junit:PlaceTotalsDbTest#testHabitatsAreOrderedByTheNumberShown |
| 53 | description.do, images.do, comparisons | Field guide: the same three summaries | as #50-#52 | partly | junit:PlaceTotalsDbTest#testHabitatsAreOrderedByTheNumberShown |
| 54 | description.do, images.do, comparisons | Admin ImageCount (taxon) | taxon.image_count (set 4): images of the taxon's specimens and of the taxa under it (species were never added to their genus) | yes | inv:taxon.images; junit:PlaceTotalsDbTest#testTaxonImageCountsAddTheTaxaUnderThem |
| 55 | description.do, images.do, comparisons | images.do header N Species Imaged (M total) | live | not yet | not yet |
| 56 | description.do, images.do, comparisons | images.do child N Images | list image_count, falling back to the worldwide taxon.image_count | partly | cc:images; inv:taxon.images |
| 57 | description.do, images.do, comparisons | taxonomicPage images view: N X Imaged (out of M Total) | live: children with images, out of all children (printed the same number twice) | partly | e2e:test_numbers_pages.py::test_images_view_header_counts_imaged_children |
| 58 | description.do, images.do, comparisons | oneView total | live | not yet | not yet |
| 59 | description.do, images.do, comparisons | mapComparison (N X Mapped) | live: every child, mapped or not | not yet | not yet: B19 plan |
| 60 | description.do, images.do, comparisons | comparison.do child count | live | not yet | not yet |
| 61 | description.do, images.do, comparisons | specimenReport N Images | list or taxon image_count | partly | inv:taxon.images |
| 62 | description.do, images.do, comparisons | unImagedTaxa Non-imaged X (N) | live | not yet | not yet |
| 63 | specimen, collection, locality pages | Specimen Image Count (admin) | specimen.image_count (set 4) | yes | inv:taxon.images |
| 64 | specimen, collection, locality pages | specimenImages N Images | live: the specimen's images | not yet | not yet |
| 65 | specimen, collection, locality pages | collection N specimens of M species | live | not yet | not yet |
| 66 | specimen, collection, locality pages | locality N collections | live | partly | e2e:test_specimen_pages.py::test_locality_page_lists_the_specimens_collection |
| 67 | search results | Advanced search N specimens (M imaged) | live, HTTP session | not yet | not yet: B20 plan |
| 68 | search results | Advanced search by taxon N species (from M specimens) | live, session | not yet | not yet: B20 plan |
| 69 | search results | Compare / map preparation counts | live | not yet | not yet |
| 70 | search results | multiTaxaComparison N specimens | live | not yet | not yet |
| 71 | search results | Recent images N Images | live | not yet | not yet |
| 72 | search results | Bay Area search N records | live | not yet | not yet |
| 73 | countries.do, regions.do, subregions.do, adm1s.do, projects.do, museums.do, bioregions.do | Valid species column (labelled Species until 2026-10-21) | = #23 | yes | inv:place.totals.valid; e2e:test_numbers_pages.py::test_list_of_countries_labels_valid_species |
| 74 | countries.do, regions.do, subregions.do, adm1s.do, projects.do, museums.do, bioregions.do | Specimens column | = #26 | yes | inv:place.totals.specimens |
| 75 | countries.do, regions.do, subregions.do, adm1s.do, projects.do, museums.do, bioregions.do | Images column | = #29 | yes | inv:place.images |
| 76 | countries.do, regions.do, subregions.do, adm1s.do, projects.do, museums.do, bioregions.do | Image Submissions pie | = #75 per place | yes | inv:place.images |
| 77 | countries.do, regions.do, subregions.do, adm1s.do, projects.do, museums.do, bioregions.do | Specimen Submissions pie | = #74 per place | yes | inv:place.totals.specimens; inv:geo.regions |
| 78 | endemic.do, introduced.do | endemic.do rows | live over is_endemic flags | partly | inv:place.totals.endemic |
| 79 | endemic.do, introduced.do | introduced.do rows | live over is_introduced flags | partly | inv:place.totals.introduced |
| 80 | curators, groups, artists, uploads (admin) | curators.do Specimen Uploads | LoginMgr at startup | not yet | not yet: B22 plan |
| 81 | curators, groups, artists, uploads (admin) | curators.do Description Edits | LoginMgr at startup | not yet | not yet: B22 plan |
| 82 | curators, groups, artists, uploads (admin) | curator.do specimen uploads | LoginMgr at startup | not yet | not yet: B22 plan |
| 83 | curators, groups, artists, uploads (admin) | curator.do last upload numbers | upload row | not yet | not yet: B14 plan |
| 84 | curators, groups, artists, uploads (admin) | curator.do image uploads, images uploaded | LoginMgr at startup | not yet | not yet: B22 plan |
| 85 | curators, groups, artists, uploads (admin) | groups.do Uploads | ant_group / UploadDb | not yet | not yet |
| 86 | curators, groups, artists, uploads (admin) | groups.do Specimens | upload row | not yet | not yet |
| 87 | curators, groups, artists, uploads (admin) | groups.do Ungeoreferenced, Flagged, Collections, Localities | upload row | not yet | not yet |
| 88 | curators, groups, artists, uploads (admin) | groups.do Subfamilies, Genera, Species | upload row: count(distinct species) counts epithets | not yet | not yet: B14 plan |
| 89 | curators, groups, artists, uploads (admin) | groups.do Totals row | live sums | not yet | not yet |
| 90 | curators, groups, artists, uploads (admin) | groups.do Totals Subfamilies / Genera / Species | live maximum, labelled Totals | not yet | not yet: B14 plan |
| 91 | curators, groups, artists, uploads (admin) | group.do uploads, last upload numbers | upload row | not yet | not yet |
| 92 | curators, groups, artists, uploads (admin) | artists.do Images, Specimen | live / ArtistMgr | not yet | not yet |
| 93 | curators, groups, artists, uploads (admin) | listImageUploads totals | live / image_upload | not yet | not yet |
| 94 | curators, groups, artists, uploads (admin) | imageUploadReport counts | at upload | not yet | not yet |
| 95 | curators, groups, artists, uploads (admin) | Upload report Inserted / Updated Species | at upload | not yet | not yet |
| 96 | curators, groups, artists, uploads (admin) | Upload report message counts | at upload; static error list (B23) | not yet | not yet: B23 plan |
| 97 | curators, groups, artists, uploads (admin) | Admin statistics.do table | statistics rows; V. Sp. Imaged counts valid species and subspecies imaged since 2026-10-21 (counted images) | partly | inv:home |
| 98 | curators, groups, artists, uploads (admin) | statistics.do breakdowns | live | not yet | not yet |
| 99 | curators, groups, artists, uploads (admin) | orphanedImages Found N | live | not yet | not yet |
| 100 | API v3.1 | /specimens imageCount | specimen.image_count | yes | inv:taxon.images |
| 101 | API v3.1 | /specimens, /geoSpecimens, ... meta count | live: rows in this page | yes | e2e:test_api.py::test_reply_shape_and_limit |
| 102 | API v3.1 | /geoSpeciesStats caste counts | live | not yet | not yet |
| 103 | API v3.1 | /taxa subfamilyCount, genusCount, speciesCount, specimenCount | taxon columns that no current code writes | not yet | not yet: B11 plan (remove or write them) |
| 104 | API v3.1 | /taxa imageCount | taxon.image_count (= #54) | yes | inv:taxon.images |
| 105 | API v3.1 | /taxa meta taxonCount | live | yes | e2e:test_api.py::test_reply_shape_and_limit |
| 106 | API v3.1 | /images meta counts | live: count = image rows, specimenCount = records | yes | e2e:test_api.py::test_images_records_name_their_specimen; unit:test_api_counts.py::test_images_records_are_keyed_by_specimen_code |
| 107 | API v3.1 | /taxaImages meta counts | live | not yet | not yet |
| 108 | API v3.1 | /geolocales subfamily, genus, species, specimen counts | = #20-#22, #26 | yes | inv:place.totals.subfamily; inv:place.totals.genus; inv:place.totals.species; inv:place.totals.specimens |
| 109 | API v3.1 | /geolocales imageCount, imagedSpecimenCount | = #29, #27 | yes | inv:place.images; cc:place images |
| 110 | API v3.1 | /geolocales endemic, introduced counts | = #24, #25 | partly | inv:place.totals.endemic; inv:place.totals.introduced |
| 111 | API v3.1 | /geolocales meta geolocaleCount | live | yes | e2e:test_api.py::test_reply_shape_and_limit |
| 112 | API v3.1 | /geolocaleTaxa per row counts | list rows | partly | inv:list.genus; cc:specimens; cc:images |
| 113 | API v3.1 | /geolocaleTaxa meta count | live (metaData geolocaleCount) | yes | e2e:test_api.py::test_reply_shape_and_limit |
| 114 | API v3.1 | /bioregions 6 counts | bioregion table (= place page) | yes | inv:place.totals.subfamily; inv:place.totals.genus; inv:place.totals.species; inv:place.totals.specimens; inv:place.images |
| 115 | API v3.1 | /bioregions meta count | live (metaData bioregionCount) | yes | e2e:test_api.py::test_reply_shape_and_limit |
| 116 | API v3.1 | /bioregionTaxa per row counts | list rows | partly | inv:list.genus; cc:specimens |
| 117 | API v3.1 | /unimagedGeolocaleTaxa list | live: names without a specimen with an image of the ant (was image_count > 1) | partly | unit:test_api_counts.py::test_a_taxon_with_one_image_of_the_ant_is_imaged; unit:test_api_counts.py::test_endpoint_uses_the_rule |
| 118 | API v3.1 | /bioregionTaxa, /geolocaleTaxa meta and dev counters | live | not yet | not yet |

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

- **B25** (planned): when a subspecies' parent is its species, the crawl writes the sum of the subspecies'
  specimens into the species row (`countGrandChildren` for rank species groups by `parent_taxon_name`). To
  confirm on staging: `select count(*) from taxon where taxarank = 'subspecies' and parent_taxon_name in (select
  taxon_name from taxon where taxarank = 'species')`. `cc:specimens` shows such rows on sampled places.
- **B26** (planned): adm1 numbers match specimens by adm1 name only (`specimen.adm1 = name`), so two adm1s of the
  same name in different countries share specimens. Fix: add the country (`and s.country = parent`).

## 4. Plans for the larger ones

| Id | Plan |
|---|---|
| B11 | API /taxa `subfamilyCount`, `genusCount`, `speciesCount`, `specimenCount`: read the allantwebants row (`proj_taxon`) instead of taxon columns nobody writes; add an `inv:` check that /taxa equals the Global row; document in the API page. |
| B14 | Upload Species: `count(distinct taxon_name)` of species rank at upload; groups.do: real totals (distinct over all groups) or rename the row "Largest"; test against `upload` and `specimen`. |
| B15 | One place rule for counts and links: `Overview.getOverviewCriteria` uses `Geolocale.getSpecimenPlaceCriteria` (country = X, island_country for islands, the countries of a region); browse filters on `specimen.status` like the counts; e2e: for 3 random species rows of the sampled places, the browse page's count equals the row. |
| B19 | Count children with coordinates; label "N of M mapped". |
| B20 | Keep search totals in the request, not the session; count imaged as images of the ant; fix "N specimens Specimens". |
| B21 | Header says "0 unimaged" instead of falling back; imaged = an image of the ant (list image counts without labels, or a separate column). |
| B22 | Curator counts: recount when an upload or image upload ends; place pages: an e2e test comparing each sampled place's page (`data-stat` numbers) with `countCheck.do?format=json`; alert when set 5 fails. |
| B23 | `MessageMgr` errors per upload (instance, not static). |
| B25, B26 | as above, each with an `inv:` or `cc:` check. |
| museum, bioregion lists | set 3 still renames synonym rows of museum and bioregion lists after their counts (rows of a genus may then not add up: `inv:list.genus`). Plan: run the rename before `populateMuseum` and `populateBioregion` count, or recount their lists after it. |
| not yet rows | Live numbers (search results, browse headers, collection and locality pages, API meta counts) are counted when shown; next: e2e tests that compare each with a SQL count on staging, starting with #38, #46 and #55. Curator, group and upload numbers: an `inv:` check against `upload` and `image_upload`. |
