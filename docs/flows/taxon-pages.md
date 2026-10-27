# Taxon pages: description.do, browse.do, images.do

Read from the code on master at commit e81a8945 (2026-10-22). Paths are relative to the repo root; Java classes are under `src/org/calacademy/antweb/`. Traffic: production Tomcat log, 2026-10-11 to 2026-10-18 (8 days), from [catalog/actions.csv](../catalog/actions.csv).

## In plain words

A taxon page is what most visitors see: **287,732** requests in 8 days for the overview (`description.do`), **37,959** for the images page (`images.do`) and **31,893** for the list of child taxa (`browse.do`). Visitors are researchers, students and search engine crawlers; links come from Google, AntCat, AntWiki and AntWeb's own lists.

Every taxon (subfamily, genus, species, subspecies) has three tabs:

- **Overview** (`description.do`): title and author, status, the taxonomic hierarchy, the "View in AntCat" link, the head shot image, a small map of the specimens, the AntCat taxonomic history ("Taxonomic History (provided by Barry Bolton, year)"), the page text curators write (overview, distribution, identification, biology, comments), the habitat, microhabitat and method summaries, and the tools: Compare Images, Map of the child taxa, Create Field Guide (genus and subfamily), Download Data (genus and below).
- **Child list** (`browse.do`): the genera of a subfamily, the species of a genus, or the specimens of a species, each with its image, counts and status. The heading names the place: "Browsing genus Camponotus in Madagascar".
- **Images** (`images.do`): the images of the child taxa, by caste (worker, queen, male) and view.

**The place filter.** Every taxon page is seen "in" a place: all of AntWeb (project `allantwebants`), the World Ants catalog (`worldants`), a country, adm1, region, subregion, island, bioregion, museum or project. The place comes from a parameter (`project=`, `countryName=`, `adm1Name=` with `countryName=`, `regionName=`, `subregionName=`, `bioregionName=`, `museumCode=`, `geolocaleId=`). Without one, the page uses **the place of the visitor's previous page** (kept in the session) and, for a new visitor, all of AntWeb. The place selects which children are listed (the place's species list), which counts are shown and which specimens are mapped. The header search box "Taxa" sends `description.do?taxonName=<name>`, which redirects to the taxon's page.

**Name history.** Taxon pages have no Name History section: the history of a name is AntCat's "Taxonomic History" (from the AntCat import, [antcat-to-antweb.md](antcat-to-antweb.md)). The "Name History" section added in October 2026 (#212) is on specimen pages: see [specimen-and-image-pages.md](specimen-and-image-pages.md).

**Caching.** None. Page caching (`AntwebCacheMgr`) is switched off in the code (`CACHING_OFF`; the Cache Manager was removed 2026-10-18) and the map cache in `MapMgr.getMap()` is off (`persist = false`): every request reads the database. What is in memory is the name and place lists loaded at start-up (`TaxonMgr`, `GeolocaleMgr`, `ProjectMgr`, `MuseumMgr`, `BioregionMgr`; refreshed after uploads and by the nightly sets).

## URLs

| URL | What it shows |
|---|---|
| `description.do?subfamily=formicinae&rank=subfamily` | Overview of a subfamily |
| `description.do?genus=camponotus&rank=genus` (also `?subfamily=...&genus=...`, `?name=camponotus&rank=genus`) | Overview of a genus |
| `description.do?genus=camponotus&species=maculatus&rank=species` | Overview of a species |
| `description.do?genus=...&species=...&subspecies=...&rank=subspecies` (or `name=<species> <subspecies>&rank=species`) | Overview of a subspecies |
| `description.do?taxonName=<name or Pretty Name>` | Redirect (302) to the page; a specimen code (casent..., blf..., jtl...) redirects to `specimen.do?code=` |
| `description.do?antcatId=<id>` | Redirect to the taxon of that AntCat id |
| `browse.do?...` with the same parameters | Child list; at species rank, the specimens |
| `images.do?...&caste=worker` | Images of the children, by caste |
| any of these `&project=` / `&countryName=` / `&bioregionName=` ... | The same page in that place |
| `&statusSet=valid` (or `all`, `valid extant` ...), `&global=true` | Which statuses are listed; `global=true` lists every child, not only the place's |
| `homonym`: `&status=homonym&authorDate=...` | A homonym's page (`homonymPage.jsp`) |

Without a rank, the rank is inferred from the parameters (`BrowseAction.inferredRank()`); `rank=subgenus` is treated as genus.

## Code path

1. `BrowseAction.execute()` (one class for `description.do`, `browse.do`, `images.do`, `getComparison.do` and `mapComparison.do`; the form is `BrowseForm`, request scope for the three tabs):
   - start-up and request checks: `Check.init(TAXON)` (message while the name lists load), `Check.valid()` (`HttpUtil.invalidRequest`: several `?`, blocked agents), `ProjectMgr.hasMoved()` (redirects old project names);
   - the place: `OverviewMgr.getAndSetOverview()` -> `findOverview()` reads the place parameters in a fixed order (project, museumCode, geolocaleId, regionName, subregionName, countryName, adm1Name, bioregionName), else the session's `overview`, else `allantwebants`; an unknown place throws `AntwebException` and the page is a message ("Country not found for countryName:X");
   - `taxonName=` / `antcatId=` -> `taxonNameRedirect()`: `TaxonDb.getTaxon()`, else `HomonymDb.getHomonym()`, else `CommonNames`, else the AntCat id (`TaxonDb.getTaxonNameFromAntcatId()`), then a redirect to `Taxon.getUrl()`;
   - the taxon: `TaxonDb.getFullTaxon(family, subfamily, genus, species, subspecies, rank)`, else `HomonymDb.getFullHomonym()`; a name that is not valid seen in `worldants` switches the place to `allantwebants`;
   - `taxon.initTaxonSet(connection, overview)`: the taxon's row in the place's list (none: "Taxon:x not found for overview:y");
   - page text: `saveDescriptionEdit()` (curator POST, see the editor flow) and `prepareDescriptionEdit()`; the text itself is in `description_edit`;
   - images: `taxon.setImages(connection, overview, caste)`: the default specimen of the taxon for each caste (`taxon_prop`, chosen by curators with the image picker), else a child's default, else a specimen of the taxon with a head shot;
   - children (`browse.do`, `images.do`): `taxon.setChildren(connection, overview, statusSet, getChildImages, getChildMaps, caste, global, subgenus)` reads the place's list (`proj_taxon`, `geolocale_taxon`, `museum_taxon` or `bioregion_taxon`) and the children's images;
   - map (`description.do`, `images.do`): `MapMgr.getMap(taxon, overview)` -> `new Map(taxon, overview, connection)`, the place's georeferenced specimens of the taxon, read live;
   - forward: `success` -> `taxonPage.jsp` / `showBrowse.jsp` / `imagePage.jsp`; `homonym` -> `homonymPage.jsp`; `message` -> `common/message.jsp`.
2. JSPs: `web/taxonPage.jsp` + `taxonPage-body.jsp` (title `<Rank>: <Name> - AntWeb`; includes `common/taxonTitle.jsp`, `common/viewInAntCat.jsp`, `common/taxonomicHierarchy.jsp`, `common/pageToolLinks.jsp`, `distribution.jsp`, the page-text fields `common/descEdit/taxonEditFieldCK.jsp`, the map `maps/googleMapInclude.jsp`); `web/showBrowse.jsp` + `showBrowse-body.jsp` (title `Browsing <rank> <Name> in <place> - AntWeb`; `taxonReport.jsp` for taxa, `specimenReport.jsp` for specimens); `web/imagePage.jsp` + `imagePage-body.jsp` (title `<Rank>: <Name> - AntWeb`).
3. Autocomplete of the header box: `web/search/autoCompleteKeys.jsp?q=` -> `TaxonMgr.getPrettyTaxaNames()` (in memory). Allowed as a direct JSP by Caddy (`Caddyfile.common`).

## Tables read

[`taxon`](../data-model.md#taxon), [`homonym`](../data-model.md#homonym), [`taxon_prop`](../data-model.md#taxon_prop) (default images), [`description_edit`](../data-model.md#description_edit) (page text), [`proj_taxon`](../data-model.md#proj_taxon), [`geolocale_taxon`](../data-model.md#geolocale_taxon), [`museum_taxon`](../data-model.md#museum_taxon), [`bioregion_taxon`](../data-model.md#bioregion_taxon) (the place's list), [`specimen`](../data-model.md#specimen) and [`image`](../data-model.md#image) (images, map points, habitat summaries). Places come from memory (loaded from [`geolocale`](../data-model.md#geolocale), [`project`](../data-model.md#project), [`museum`](../data-model.md#museum), [`bioregion`](../data-model.md#bioregion)).

## Numbers shown

[numbers.md](../numbers.md) rows #38 to #49 (list headers and row counts of `browse.do`), #50 to #53 (habitat, microhabitat and method summaries), #54 to #57 (image counts), #61 and #62.

## Answers to unknown names and bad parameters (pinned by the tests)

All are AntWeb's message page (`common/message.jsp`, title "message...", HTTP 200):

| Request | Message |
|---|---|
| unknown genus, species ... | "Taxon not found." (or "Taxon not found for ...") |
| `taxonName=` unknown | "<name> does not exist in the database" |
| `rank=specimen` | "Invalid request.  Description.do requests are for taxa, not specimens." |
| unknown rank | "Bad rank:<rank>" (the request details go to the log only, since 2026-10-26, F12) |
| no taxon parameter | "Taxon not found." |
| unknown place | "Country not found for countryName:X", "Geolocale not found for geolocaleId:X" (also a `geolocaleId` that is not a number, since 2026-10-26, F3), "Bioregion not found bioregionName:X", "Museum not found for museumCode:X", "Not found:project overview" (with "for <referer> <time>" and a line asking to email AntWeb) |
| comparisons without a login | not a message: the explained log-in page with return, as for field guides (`Check.loginForFeature()`, since 2026-10-26, F15) |

## Known issues

See [public-pages-findings.md](public-pages-findings.md): the place filter carries over from the previous page (by design, pinned, but surprising: F10); place parameters in tool links are not URL-encoded (F11, left). Fixed 2026-10-26: a `geolocaleId` that is not a number gave the error page (F3); old bioregion project names (`project=africanants` ...) lost their filter on redirect, now `bioregionName=` (F4, `ProjectMgr.movedBioregionTarget()`); the busy check for bots did nothing (F1); the "View in AntCat" link is https with the name URL-encoded (F11).

## Tests

| Test | What it pins |
|---|---|
| `tests/e2e/test_taxon_pages.py::test_taxon_page_at_each_rank` | subfamily, genus (found on the Formicinae browse page), species and subspecies (found through the API): title, heading, Overview / child list / Images links, View in AntCat |
| `test_taxon_pages.py::test_genus_page_has_taxonomic_history_and_tools` | Taxonomic History, Compare, Map, Field Guide and Download Data tools |
| `test_taxon_pages.py::test_species_page_links_its_map`, `test_species_browse_lists_specimens`, `test_genus_browse_lists_species`, `test_images_page` | map link, specimen list, species list, images page |
| `test_taxon_pages.py::test_place_filter_selects_the_list`, `test_bioregion_filter`, `test_last_place_filter_stays_for_the_session`, `test_unknown_place_filter` | the place filter, its session carry-over, unknown places |
| `test_taxon_pages.py::test_taxon_name_redirects_to_the_taxon_page`, `test_taxon_name_keeps_the_page_type`, `test_taxon_name_of_a_specimen_code_opens_the_specimen`, `test_antcat_id_opens_the_taxon_page`, `test_autocomplete_suggests_names` | the header Taxa box and old links |
| `test_taxon_pages.py::test_unknown_taxon_or_rank_message` | the messages above |
| `test_taxon_pages.py::test_geolocale_id_not_a_number_gets_a_message`, `test_old_bioregion_project_link_keeps_its_filter`; `test/.../util/PublicPagesLowFindingsTest` | findings F3, F4 (fixed 2026-10-26, xfail removed) |
| `test_page_content.py` | fingerprints of the key taxon pages (title, headings, images, links, rows) |
| `test_site_links.py` | the links of each rank's pages, map popups included |
| `test_browser.py` | the Taxa box suggestions, no JavaScript errors, phone width |
| `test_display_cleanup.py`, `test_image_picker.py`, `test_numbers_pages.py` | the distribution text, the image picker's form, the images-view counts |
| `test_page_editor.py`, `test_field_history.py`, `test_browser_editor.py` | the page-text editor (curators) |
