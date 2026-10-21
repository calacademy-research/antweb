# Specimen downloads and search results: what can be downloaded

AntWeb does not offer free bulk downloads of specimen data (Brian Fisher, 2026-10-21). Downloads from the web pages are for one genus or species, or for one search, and hold at most 5,000 records. Larger data sets will come only through the API with registered tokens (planned for the coming weeks). Until then, people who need more write to antweb@antweb.org.

## What a visitor or curator can download

| Where | What you get | Limits |
|---|---|---|
| **Download Data** on a genus, species or subspecies page (`getSpecimenList.do`) | A tab-delimited file of the specimens of that taxon. On a place page, only the specimens collected in that place. | Login needed. Genus, species or subspecies only: a family or subfamily gets the message "Downloads are available for a genus or species. For larger data sets, contact antweb@antweb.org about API access." At most 5,000 records; with more, the file has the first 5,000 (by specimen code) and the box says "This download is limited to 5,000 records (N match). ..." Admins: no cap. |
| **Download Data** on the advanced search results page (`getSearchList.do`) | A tab-delimited file of the specimens the last search found, in the order shown. | Login needed. At most 5,000 records, same message. Admins: no cap. |
| Advanced search results page (`advancedSearch.do`) | 200 specimens a page, "Showing 1–200 of 141,728", with Previous and Next. | The old "See all" link (10,000 records on one page) is gone. |

Files are written under `web/data/specimenData/` and `web/data/search/` with the date, time and login id in the name, and deleted after a day.

## Which specimens a place download holds

The same specimens the place's numbers count (`SpecimenDb.getDownloadFromWhere`):

- country: `specimen.country`; island: `specimen.island_country`; adm1: `specimen.adm1`;
- region and subregion: the specimens of their countries (`Geolocale.getSpecimenPlaceJoin`);
- museum: the museum's own specimens (`specimen.museum`); bioregion: `specimen.bioregion`;
- project: the specimens of the names on the project's list (`proj_taxon`); allantwebants: all specimens.

Before 2026-10-21 every place used its taxon list only, so a country file held every specimen, worldwide, of the names on the country's list (Reunion, family Formicidae: 35,851 specimens, 1,778 of them collected in Reunion).

## The paged search list

`SearchAction.doPagedSpecimenSearch` runs a `COUNT(*)` for the total and one query per page (`AdvancedSearch.countPaged`, `getPage`: `ORDER BY ... LIMIT 200 OFFSET n`). The sort (`sortBy`, `sortOrder`) goes into the `ORDER BY` (`SearchPaging.orderBy`, a fixed list of columns, specimen code last), so it applies before paging. "Images only" and "Types only" are SQL conditions there.

The session's `advancedSearchResults` holds the current page only, so **Map Results**, **Compare Images** and **Create Field Guide** on that page work on the specimens of that page (they use checkbox numbers within it). The search itself is kept in the session (`advancedSearchPaging`) so that Download Data can read it again, up to 5,000 records.

Searches by taxon (results by species, genus or subfamily) and the map outputs still read up to 50,000 specimens (admins: no limit) as before; their lists are shown 200 at a time.

## Crawlers

Links that run a specimen search ("List all specimens collected in ...", museum type lists, group and locality links) carry `rel="nofollow"`, and `robots.txt` disallows `/advancedSearch.do`, `/getSpecimenList.do`, `/getSearchList.do` and `/speciesListDownload.do`.

## What is still open

- The API (`/v3.1/specimens`) still serves specimens without a token, 10,000 a request with `offset`, so every record can still be read by paging through it. Registered tokens are the next step.
- `speciesListDownload.do` gives the species list of a place (names, not specimen records) and is not capped.
- Files written before 2026-10-21 under `web/data/specimenData/` stay until the next download there removes files older than a day.

Code: `util/DownloadLimits.java`, `util/SpecimenListAction.java`, `search/SearchListAction.java`, `search/SearchPaging.java`, `search/SearchAction.java`, `search/AdvancedSearch.java`, `web/search/advancedSearchResults-body.jsp`, `web/search/searchPager.jsp`. Tests: `test/.../util/DownloadGuardsTest.java`, `test/.../home/DownloadGuardsDbTest.java`, `tests/unit/test_download_guards.py`, `tests/e2e/test_download_guards.py`.
