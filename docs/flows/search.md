# Search: the header boxes, advanced search, recent images

Read from the code on master at commit e81a8945 (2026-10-22). Paths are relative to the repo root; Java classes are under `src/org/calacademy/antweb/`. Traffic: production Tomcat log, 2026-10-11 to 2026-10-18 (8 days).

## In plain words

AntWeb has three ways in:

1. **The header boxes** on every page (`common/searchBar.jsp`): **Taxa** (`search/taxonNameSearchBox.jsp`) suggests names as you type (`search/autoCompleteKeys.jsp?q=`, from the names in memory) and opens `description.do?taxonName=<name>`, which redirects to the taxon page ([taxon-pages.md](taxon-pages.md)); **Places** (`search/placeNameSearchBox.jsp`) suggests place names (`search/autoCompletePlaceKeys.jsp?q=`) and opens `place.do?placeName=<name>`, which redirects to the country, adm1, island, region or subregion page ("Antananarivo, Madagascar" finds the adm1). This is the "quick search"; `search.do` itself only redirects to the advanced search form.
2. **Advanced search** (`advSearch.do`, the form; `advancedSearch.do`, the results, 644 requests in 8 days): any combination of name, place, collector, dates, elevation, habitat, method, museum, owner, type status, images only, upload group ... Results are either a **specimen list** (200 a page, "Showing 1-200 of N", Previous / Next, sortable) or a **list of taxa** (resultRank subfamily, genus or species: "N Genera (from M specimens)"), or a map (output=mapLocality / mapSpecimen). From the results: Map, Compare images and Field Guide of the selected rows (these need a login), and Download Data (genus or species only, at most 5,000 specimens, since 2026-10-21). `advancedSearchResults.do` shows the session's last results again; for a paged specimen list it sends the browser to that search again at the same page (since 2026-10-27, F25).
3. **Recent images** (`recentSearchResults.do?searchMethod=recentImageSearch&daysAgo=30`, linked as "More" from the home page): the images uploaded in the last N days.

Search results live in the visitor's HTTP session (`advancedSearchResults`, `advancedSearchPaging`, `searchTitle`, `resultSetModifier`, `taxonList`), so the follow-up pages (paging links, map, compare, download) work on the same search. Nothing is cached between visitors. robots.txt disallows the search and download addresses.

## URLs

| URL | What it does |
|---|---|
| `advSearch.do` | The form (`search/advancedSearch.jsp`) |
| `advancedSearch.do?searchMethod=advancedSearch&advanced=true&family=formicidae&country=Comoros&resultRank=specimen` | Specimen list, page 1 |
| `...&page=2`, `...&sortBy=<field>&sortOrder=up|down` | Paging, sorting (applied before paging) |
| `...&genusSearchType=equals&genus=<genus>` | A criterion; each field has a `<field>SearchType` (equals or contains; dates and elevation: greaterThanOrEqual, lessThanOrEqual) |
| `...&resultRank=genus` (subfamily, species) | Taxa found, with the number of specimens |
| `...&output=mapLocality` (mapSpecimen) | Map of the results (`dynamicMap.jsp`) |
| `advancedSearchResults.do` | The last results of the session again (`search.AdvancedSearchResultsAction`): a paged specimen search is redirected to `advancedSearch.do?<its query>&page=N` (`SearchPaging.reshowUrl()`, the query kept in `advancedSearchPaging` since 2026-10-27, F25); with parameters (Map, Compare) or without a paged search, the session list |
| `recentSearchResults.do?searchMethod=recentImageSearch&daysAgo=30` (`&sortBy=code`, `shotType`) | Recent images |
| `search.do` | Redirect to `advSearch.do` (`util.RedirectAction`) |
| `place.do?placeName=<name>` | Redirect to the place's page |

## Code path

- `search.SearchAction.execute()` (a `DispatchAction` on `searchMethod`): sets the session's place to none, `Check.busy()`, then `advancedSearch()`, `descEditSearch()` or `recentImageSearch()`. No or unknown searchMethod: message "Invalid searchType:... Case#:N. Please report behavior to ...". `recentImageSearch` must be followed by another parameter (`&daysAgo=...`), else a message.
- `advancedSearch()` -> `SearchParameters(form)` -> `doAdvancedSearch()`:
  - specimen list as a list (the default): `doPagedSpecimenSearch()`: `AdvancedSearchAction.buildSearch()` -> `AdvancedSearch.countPaged()` (COUNT) and `getPage(orderBy, SearchPaging.PAGE_SIZE, offset)`, on the `mediumConPool` connection pool; `SearchPaging` builds Previous / Next; `DownloadLimits.count()` the "N specimens" line. JSP `search/advancedSearchResults.jsp` (title "Advanced Specimen Search - AntWeb", heading "Advanced Search Results").
  - by taxon or as a map: `AdvancedSearchAction.getSearchResults()` (all matching specimens, up to `SearchAction.getSpecimenSearchLimit()`, 50,000; administrators 1,000,000), `AdvancedSearchResults.setResultsWithFilters()`, then `getSubfamilyList()` / `getGenusList()` / `getSpeciesList()` (names not identified to genus left out, B54, 2026-10-27: their specimens stay in "(from M specimens)" and a line under the list, "K specimens not identified to genus (not counted as species), listed in the specimen view of this search", links to the same search with `resultRank=specimen`; this list is the session's `taxonList` that Compare, Map and the field guide of the search choose from); JSP `search/advancedSearchResultsByTaxon.jsp` (heading "Advanced Search Genus Results") or `MapResultsAction.getMap()` -> `dynamicMap.jsp`.
- `search.SearchPageAction` (`advSearch.do`): the form, with the country list (`countryGenInc`).
- `geolocale.PlaceNameAction` (`place.do`): `GeolocaleMgr.getGeolocale(placeName)` (lower case is capitalised first; "Florida" means Florida, United States), else "<adm1>, <country>" -> `GeolocaleMgr.getAdm1()`; a redirect to `getThisPageTarget()`, or the message "Place not found:<name>".
- Downloads of a search (`getSearchList.do`) and of a taxon (`getSpecimenList.do`): `util.SpecimenListAction` with `DownloadLimits` (genus or species only, 5,000 records); see #222 and `test_download_guards.py`.

## Tables read

[`specimen`](../data-model.md#specimen) (every criterion), [`taxon`](../data-model.md#taxon) (status, names), [`image`](../data-model.md#image) (images only, counts), [`ant_group`](../data-model.md#ant_group) (upload group), [`description_edit`](../data-model.md#description_edit) (descEditSearch). Places, museums and names for the form and the boxes come from memory.

## Numbers shown

[numbers.md](../numbers.md) #67 (N specimens, M imaged on this page), #68 (N taxa from M specimens), #69 to #72 (compare and map preparation, recent images, Bay Area search).

## Known issues

[public-pages-findings.md](public-pages-findings.md) F13 (fixed 2026-10-26: an administrator's search raised the result limit for every search running at that moment; the temporary limit is now per thread, `SearchAction.setTempSpecimenSearchLimit()`), F25 (fixed 2026-10-27: results shown again counted only the session's page of 200; a paged search is now shown again from the database).

## Tests

| Test | What it pins |
|---|---|
| `tests/e2e/test_search_pages.py::test_advanced_search_form` | the form and its main fields |
| `test_search_pages.py::test_search_by_genus_rank_lists_genera` | results by taxon: heading, "N Genera (from M specimens)", genus links |
| `test_search_pages.py::test_genus_filter_narrows_the_specimen_list` | a genus found in the country's results narrows the country's specimen list |
| `test_search_pages.py::test_results_shown_again_from_the_session`, `test_results_shown_again_keep_the_total` (F25, xfail removed 2026-10-27); `SearchPagingReshowTest` (JUnit, CI) | advancedSearchResults.do |
| `test_numbers_search.py::test_search_by_species_leaves_out_names_not_identified_to_genus`, `test_search_by_species_note_and_listed_species_add_up_to_the_specimens`; junit `SearchSpeciesListTest` | search by species: no name not identified to genus listed or counted; the line under the list + the specimens of the species listed = the search's specimens (B54) |
| `test_search_pages.py::test_search_without_a_method`, `test_recent_images_needs_a_following_parameter` | the messages |
| `test_search_pages.py::test_recent_images` | recent images page |
| `test_search_pages.py::test_place_name_opens_the_place_page`, `test_place_not_found`, `test_place_autocomplete` | the Places box |
| `test_taxon_pages.py::test_taxon_name_redirects_to_the_taxon_page`, `test_autocomplete_suggests_names` | the Taxa box |
| `test_download_guards.py` | 200 a page, Previous / Next, sorting before paging, 5 seconds for Madagascar, download limits, robots.txt |
| `test_login_needed.py` | map, compare and field guide of results need a login |
| `test_browser.py` | the header boxes' suggestions in a real browser |
| `test_check57_merges.py::test_search_redirects_to_advanced_search` | search.do |

## Search rules decided on 2026-10-27

Decided by Brian Fisher on 2026-10-27; in effect as soon as the change is deployed (no nightly run needed).

- **What a value in a search box means** (`GenericSearch.searchEntries()`, `getSearchString()`): entries are separated by commas; an entry of several words is one phrase ("contains Santa Cruz" finds Santa Cruz, no longer every Santa and every Cruz). Double quotes still mark one entry. Several entries: a specimen matches any of them; with "not equals", none of them (before, "not equals A, B" matched every specimen). Specimen, locality and collection codes have no spaces, so those boxes also take a list separated by spaces, tabs or line breaks (a list pasted from a spreadsheet). Every box that has a search type uses these rules: taxon name, family, subfamily, genus, species, subspecies, adm2, locality name, locality code, habitat, elevation, method, microhabitat, collected by, collection code, specimen code, located at, life stage, caste, medium, specimen notes, DNA extraction notes, museum, owned by, determined by, date added. Country, adm1, bioregion and type status are pick lists and match exactly, as before.
- **"Equals"** is an exact match of the whole entry. The taxon name box (`GenericSearch.getTaxonNameEqualsString()`): a specimen's stored name starts with its subfamily ("formicinaeformica rufa"), so the box matches the whole name, the name without the subfamily ("Formica rufa") or the last words after a space ("rufa": the species or subspecies part). Before, it was a suffix match: "rufa" also found "pseudorufa".
- **Searches with more than 50,000 specimens** (by taxon, map; the limit `SearchAction.getSpecimenSearchLimit()`): always the first 50,000 by specimen code (`AdvancedSearch.SPECIMEN_ORDER`). Before, every other search reversed the order, so the same search gave two different sets of 50,000.
- **Field guide from search results** (`FieldGuideResultsAction.fieldGuideCaste()`): the images are of the caste chosen in the search (Caste/Subcaste: male, queen, worker or other; a subcaste counts as its caste); worker when no caste was chosen. Before, field guides from a search always showed queens. The search's caste is kept in the session (`searchCaste`); a collection page's specimens use worker.
- The help text on the form (`search/instruct.jsp`) says this.
- Tests: `test/org/calacademy/antweb/search/SearchRulesTest.java` (JUnit, no database: entries, phrases, code lists, "not equals", the taxon name clause, the field guide caste), `tests/unit/test_decided_pages.py` (source checks: one fixed order, the help text).
