# Home page and statistics page

Read from the code on master at commit e81a8945 (2026-10-22). Paths are relative to the repo root; Java classes are under `src/org/calacademy/antweb/`. Traffic: production Tomcat log, 2026-10-11 to 2026-10-18 (8 days): `/` 2,615, `index.do` 109, `statsPage.do` 18.

## In plain words

**The home page** (`/`, old address `index.do`) has: the header (search boxes, menus, version), a background image of a specimen with links to it and its species, the **Current Statistics** box (valid species and subspecies, specimen records, species and subspecies imaged, specimens imaged, total specimen images; "More" opens the statistics page), the mission text, **Recent Images** and **Recent Edits** ("More" opens the recent images search), a slideshow, the AntWeb Community links (specimen contributors, curators, museums, photographers, the team), Ant Course, and the World Ant Collections box (Basel, Berlin, Copenhagen, Geneva, Genoa, Oxford).

The home page itself does no database work. Its changing parts are files written by other code and pasted in by `FileUtil.getContent()`:

| Part | File (under the web root) | Written by | When |
|---|---|---|---|
| Current Statistics | `web/genInc/statistics.jsp` | `home.StatisticsDb.populateStatistics()` | after each specimen upload (`UploadAction.runStatistics`) and in nightly set 4 |
| Recent Images | `web/genInc/recentImages_gen_inc.html` | `imageUploader.ImageUploaderAction.writeRecentImages()` | after an image upload saves images (`ImageUploadBatchAction`), at start-up (`AntwebMgr.genRecentContent`), `utilData.do?action=genRecentContent` (Technical page) |
| Recent Edits | `web/genInc/recentDescEdits.html` | `util.AntwebFunctions.genRecentDescEdits()` (the last manual page-text edits) | after each page-text save (`DescriptionAction`), at start-up, `genRecentContent` |
| Slideshow, background | `common/slideshow_homepage.jsp`, the page's script | static |

**The statistics page** (`statsPage.do`, "Antweb Statistics") shows: Current Statistics (the same five numbers, from memory: `TaxonMgr.getValidSpeciesCount()` and the latest `statistics` row in `AntwebMgr`; under the specimen records, the number of collaborating institutions = the museums of the Museums list, `MuseumMgr.getMuseumCount()`, linked to museums.do, since 2026-10-27, was the fixed text "21"), Global Data (valid extant and fossil subfamilies, genera, species, subspecies), Images (per specimen status: total, worker, male, queen, other), Imaged Species (per taxon status), Bioregion Data and Museum Data (valid extant and fossil). These tables are live queries on every request. The table rows link to plain name lists (`taxaList.do?rank=...&project=...`).

## URLs

| URL | What it shows |
|---|---|
| `/`, `index.do` | Home page (`index.jsp` + `index-body.jsp`, title "AntWeb") |
| `statsPage.do` | Statistics page (`util/statsPage.jsp` + `statsPage-body.jsp`, title "Antweb Statistics") |
| `taxaList.do?rank=subfamily&status=valid&project=allantwebants` (`bioregionName=`, `museumCode=`, `geolocaleName=`, `extant=0|1`, `imaged=0|1`) | Plain list of names (`util/list.jsp`) |
| `recentSearchResults.do?searchMethod=recentImageSearch&daysAgo=30` | Recent images ([search.md](search.md)) |

## Code path

- Home: `struts-config.xml` maps `/` and `/index` straight to `index.jsp` (no Action). `index-body.jsp` includes `genInc/statistics.jsp`, `genInc/recentImages_gen_inc.html`, `genInc/recentDescEdits.html` through `FileUtil.getContent()`, and `common/slideshow_homepage.jsp`.
- Statistics: `StatsPageAction.execute()`: `ImageDb.getImageStats()`, `ImageDb.getImageTaxonStats()`, `StatisticsDb.getExtantData()`, `getFossilData()`, `getBioregionData()`, `getExtantMuseumData()`, `getFossilMuseumData()`; the Current Statistics numbers come from `TaxonMgr` and `AntwebMgr` (memory).
- `TaxaListAction.execute()`: one `select taxon_name from taxon` with the rank, status, fossil and image conditions and the place's list (`proj_taxon`, `bioregion_taxon`, `museum_taxon`, `geolocale_taxon`), bound parameters.

## Tables read

Home: none at request time ([`statistics`](../data-model.md#statistics), [`image`](../data-model.md#image), [`description_edit`](../data-model.md#description_edit) when the files are written). Statistics page: [`taxon`](../data-model.md#taxon), [`image`](../data-model.md#image), [`specimen`](../data-model.md#specimen), [`bioregion_taxon`](../data-model.md#bioregion_taxon), [`museum_taxon`](../data-model.md#museum_taxon), [`statistics`](../data-model.md#statistics) (at start-up).

## Numbers shown

[numbers.md](../numbers.md) #1 to #5 (home page), #6 to #19 (statistics page).

## Known issues

[public-pages-findings.md](public-pages-findings.md) F14 (the statistics page's live queries run on every request).

## Tests

| Test | What it pins |
|---|---|
| `tests/e2e/test_home_and_stats.py::test_home_page_sections` | title, the five statistics written into the page, the link to statsPage.do, Recent activity, Community, World Ant Collections |
| `test_home_and_stats.py::test_old_home_address` | index.do |
| `test_home_and_stats.py::test_statistics_page_sections`, `test_taxa_list_of_a_project` | the six sections of statsPage.do and its valid species number; taxaList.do |
| `test_home_page_links.py` | every link on the home page |
| `test_numbers_pages.py`, count invariants `inv:home` | the numbers (numbers.md #1 to #12) |
| `test_world_ant_collections.py` | the World Ant Collections pages and photos |
| `test_header_version.py`, `test_browser.py` | the header, JavaScript, phone width |
