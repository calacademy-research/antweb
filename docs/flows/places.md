# Places, species lists, maps, field guides and the compare tools

Read from the code on master at commit e81a8945 (2026-10-22). Paths are relative to the repo root; Java classes are under `src/org/calacademy/antweb/`. Traffic: production Tomcat log, 2026-10-11 to 2026-10-18 (8 days).

## In plain words

- **Maps** (`bigMap.do`, 139,996 requests in 8 days, second only to taxon overviews): the large map of a taxon's specimens in a place, of one specimen, a locality, a collection, a country or adm1, or a museum's specimens. Points open popups linking the localities and specimens.
- **Place pages** (country 2,129; adm1 1,514; museum 215; project 179; bioregion 145; region 11; subregion 5; island): the place's text (written by curators), its statistics table (subfamilies, genera, species, valid species, endemic, introduced, specimens, imaged specimens, images and views, each with its definition; see #191/#192; Subfamilies count valid subfamilies only on every kind of place, curated projects too, since 2026-10-30 (B67 of 2026-10-27 superseded); Endemic and Introduced, and their lists `endemic.do` and `introduced.do`, count flagged names with a counted status, never a synonym, B70, both since 2026-10-27, the stored numbers from the next nightly run), pie charts, a map of its specimens, and links to its **species lists**. List pages (`countries.do`, `regions.do`, `subregions.do`, `adm1s.do`, `bioregions.do`, `museums.do`, `projects.do`) list every place with its counts. The header Places box opens a place's page ([search.md](search.md)).
- **Species lists** (`taxonomicPage.do`, 2,443): the subfamilies, genera or species of a place, one row per taxon with status, author, specimen and image counts and a map flag, optionally with images (`images=true`) and with the taxa known only from specimens (`specimen=true`, "Show Specimen Taxa"; remembered in the session). Country and adm1 lists take valid names from specimens; since 2026-10-29 they also have the genus (with its subfamily) of every specimen identified to genus, such as "Lividopone (indet)" in Mozambique, without the name itself (nightly set 2; [nightly.md](nightly.md), numbers.md row 21). Bioregion and museum lists already had them.
- **Field guides** (`fieldGuide.do`, 286; needs a login): a printable page with an image, a map and the habitat summaries of each taxon, either of a taxon's children in a place ("Create Field Guide" on a genus or subfamily page) or of a whole rank in a place (the Field Guides menu of a place's species list: Subfamilies / Genera / Species of <place>). From search results: `fieldGuideResults.do`.
- **Compare tools** (need a login): **Compare Images** (`getComparison.do`, 16,740) shows the head, profile and dorsal images of a taxon's children side by side; **Map** of the children (`mapComparison.do`, 7,023) one small map per child taxon. Anonymous visitors get a message asking them to log in.

Nothing is cached per request: species lists, maps and field guides are built from the database each time. Place maps (country, adm1, museum) are read pre-computed from `object_map` (nightly set 5); taxon, specimen, locality and collection maps are computed live. The site allows **3 maps, 2 image comparisons, 2 map comparisons and 3 field guides at the same time** for all visitors together (static counters); above that the visitor gets "Simultaneous ... limit exceeded. Please try again later" (finding F2).

## URLs

| URL | What it shows |
|---|---|
| `country.do?name=Madagascar` (`island.do?name=Hawaii`, `id=<geolocale id>`) | Country (island) page; title "Country Madagascar - Antweb" |
| `adm1.do?name=Antananarivo&country=Madagascar` | Adm1 page; title "Adm1: Antananarivo - Antweb"; without `country`: a message |
| `region.do?name=Africa`, `subregion.do?name=Polynesia` | Region, subregion pages ("Region: Africa - Antweb") |
| `bioregion.do?name=Afrotropical` | Bioregion page ("Browsing Afrotropical Ants - AntWeb") |
| `museum.do?museumCode=BMNH` (or `code=`, `name=`) | Museum page |
| `project.do?name=fossilants` | Project page |
| `geolocale.do?id=<id>` (or `countryName=`, `adm1Name=` ...) | Redirect to the place's own page |
| `countries.do`, `regions.do`, `subregions.do`, `adm1s.do`, `bioregions.do`, `museums.do`, `projects.do` | Lists |
| `taxonomicPage.do?rank=genus&countryName=Madagascar` (any place parameter, `rank=subfamily|genus|species`, `&images=true`, `&specimen=true|false`, `&statusSet=`, `&orderBy=`) | Species list; title "<Place> Ants (Genera) - Antweb" |
| `fieldGuide.do?subfamily=..&genus=..&<place>&rank=genus` | Field guide of a taxon's children |
| `fieldGuide.do?countryName=Comoros&rank=genus` | Field guide of a rank in a place ("Field Guide of Genera in Comoros") |
| `getComparison.do?<taxon>&pr=d` | Images of the children side by side ("Comparison Tool - AntWeb") |
| `mapComparison.do?<taxon>&pr=d` | Maps of the children ("Map Comparison - AntWeb") |
| `bigMap.do?taxonName=formicinae&project=allantwebants` (`&countryName=`, `&adm1Name=`, `&geolocaleFocus=true`) | Map of a taxon in a place |
| `bigMap.do?specimen=<code>` (the specimen alone; until 2026-10-26 the first request of a session mapped its whole species, F9), `?locality=<code>`, `?collection=<code>`, `?countryName=<country>`, `?museumCode=<code>` | Other maps; title "Map of ... from AntWeb" |
| `bigmap.do`, `fieldguide.do`, `mapcomparison.do` | Lower-case aliases |

`pr=d|b|i` records which tab the tool was opened from (description, browse, images).

## Code path

- **Place pages:** `OverviewAction.execute()` (one class for all place and list pages; form `OverviewForm`): `GeolocaleMgr` / `ProjectMgr` / `MuseumMgr` / `BioregionMgr` (in memory) find the place; `action=recalc` (curators, POST) recounts it (`GeolocaleDb.updateCounts`, `ProjTaxonCountDb`, `MuseumDb.populate`, `BioregionDb.populate`, `PlaceImageCountDb.updatePlace`); `saveDescriptionEdit()` / `prepareDescriptionEdit()` (page text); `ObjectMapDb.getGeolocaleMap()` / `getMuseumMap()` (the map); `setPlaceImages()` (`PlaceImageCountDb.read`, the image numbers). JSPs `country.jsp`, `adm1.jsp`, `region.jsp`, `subregion.jsp`, `bioregion.jsp`, `museum.jsp`, `project.jsp` share `overview-body.jsp` (statistics table `id="place-stats"`, species list links); the lists share `overviews-body.jsp`. `geolocale.do` redirects; `place.do` is `geolocale.PlaceNameAction` (search.md).
- **Species lists:** `TaxaPageAction.execute()`: the place (`OverviewMgr`), `Rank.isValid(rank)`, `StatusSet.getStatusSet()` (the status choice of 2026-10-30: valid, validmorpho, all, fossil, from the address or the place's default, never the session; `StatusSet.listStatusSet` gives its criteria; selector `common/statusesDisplay.jsp`, header `StatusSet.listHeading`, docs/numbers.md "Status choice of lists"), `TaxaPage.fetchChildren(connection, overview, rank, withImages, withTaxa, withSpecimen, ...)` (the place's list joined to `taxon`); subspecies refused. JSP `taxonomicPage.jsp` + `taxonomicPage-body.jsp` (`common/overviewHeading.jsp` with the Field Guides menu, `taxonReportBody.jsp` rows) or `taxonomicPageImages-body.jsp`.
- **Field guides:** `search.FieldGuideAction.execute()`: `Check.loginForFeature()` (anonymous: the explained log-in page, then back); the place; a taxon (`TaxonDb.getFullTaxon` -> `setChildren` / `setChildrenLocalized`) or a rank of the place (`TaxaPage.fetchChildren`); `FieldGuide.setMembers()` (images, maps, habitat summaries of each taxon). JSP `search/fieldGuide.jsp` + `fieldGuide-body.jsp`. From search results: `FieldGuideResultsAction`.
- **Compare tools:** `BrowseAction` with `cacheType` getComparison or mapComparison ([taxon-pages.md](taxon-pages.md)): login required (anonymous visitors get the explained log-in page with return, `Check.loginForFeature()`, since 2026-10-26, F15; before, `Login.MUST_LOGIN_MESSAGE`), the simultaneous limit, `setChildren(..., getChildImages, getChildMaps ...)`; mapComparison builds one `Map` per child. JSPs `comparison.jsp` + `comparison-body.jsp`, `mapComparison.jsp` + `mapComparison-body.jsp`. The form is session scope (`browseForm`).
- **Maps:** `BigMapAction.execute()`: the place (`OverviewMgr`); `getMap()`: a taxon (`TaxonDb.getTaxon` -> `MapMgr.getMap(taxon, place)` or `new Map(taxon, place, connection, geolocaleFocus)`), a locality (`LocalityDb`), a collection (`CollectionDb`), a specimen (`SpecimenDb.exists`, `new Map(list)`), a geolocale or museum (`ObjectMapDb`); "No map found for target:..." otherwise. JSP `dynamicMap.jsp` + `dynamicMap-body.jsp` + `maps/googleMapInclude.jsp` (Leaflet; the popups are tested by test_site_links.py and tests/unit/test_map_popups.py). Bots get "Log In to see maps." instead of the map.

- **Red flags (decision of 2026-10-30: one rule for every list):** a list row made from specimens never comes from a red-flagged specimen ("loaded, hidden from most pages until fixed"), on every kind of place: country and adm1 lists (`GeolocaleTaxonDb.insertGeolocaleTaxaFromSpecimens`, also the genus rows of specimens identified to genus), bioregion lists (`BioregionTaxonDb.speciesFromSpecimenSql`, set 3) and museum lists (`MuseumDb.speciesFromSpecimenSql`), one condition `SpecimenDb.notRedFlagged`. docs/numbers.md "Red flags: one rule for every list".

## Tables read

Place pages: in memory from [`geolocale`](../data-model.md#geolocale), [`project`](../data-model.md#project), [`museum`](../data-model.md#museum), [`bioregion`](../data-model.md#bioregion); [`place_image_count`](../data-model.md#place_image_count); [`object_map`](../data-model.md#object_map); [`description_edit`](../data-model.md#description_edit). Species lists and field guides: [`geolocale_taxon`](../data-model.md#geolocale_taxon), [`proj_taxon`](../data-model.md#proj_taxon), [`museum_taxon`](../data-model.md#museum_taxon), [`bioregion_taxon`](../data-model.md#bioregion_taxon), [`taxon`](../data-model.md#taxon), [`taxon_prop`](../data-model.md#taxon_prop), [`specimen`](../data-model.md#specimen), [`image`](../data-model.md#image). Maps: [`specimen`](../data-model.md#specimen) (coordinates) and [`object_map`](../data-model.md#object_map).

## Numbers shown

[numbers.md](../numbers.md) #20 to #37 (place pages), #38 to #49 (species lists), #53 (field guide summaries), #57 to #60 (images view, comparisons), #73 to #79 (list pages, endemic and introduced).

## Answers to unknown places (pinned by the tests)

Message page, HTTP 200: "name:<name> not found." (country, region, subregion, adm1 with country); "Invalid request. Must include a country to uniquely specimen an Adm1 in adm1.do."; "Bioregion not found"; "Museum not found"; "Project:<name> not found."; "Geolocale:<id> not found" and "Geolocale not found"; species lists: "invalid rank:<rank>", "Subspecies not supported for taxonomic page. Use species.", "Country not found for countryName:<name>"; maps: "Enter parameters for a map", "No map found for target:...".

## Known issues

[public-pages-findings.md](public-pages-findings.md) F11 (place parameters not encoded in tool links, left). Fixed 2026-10-26: F2 (site-wide simultaneous limits), F9 (specimen map differed on reload), F15 (comparisons' old log-in message), F17 (a database error on a place page gave a blank page; now a message), F19 (dead "chosen" code of the comparison); F18 needed no change after F2.

## Tests

| Test | What it pins |
|---|---|
| `tests/e2e/test_place_pages.py::test_list_and_place_page` | each list page and its first place: titles, statistics table, species list links |
| `test_place_pages.py::test_species_list_of_a_country`, `test_place_selects_the_species_list`, `test_adm1_page_of_its_country`, `test_geolocale_id_opens_the_place_page` | species lists, the place parameter, adm1, geolocale.do |
| `test_place_pages.py::test_unknown_place_message` | the messages above |
| `tests/e2e/test_field_guide_compare.py` | field guide of a small genus and of Comoros, image and map comparison of the genus (test curator); anonymous comparisons ask to log in |
| `test_specimen_pages.py::test_specimen_map`, `test_taxon_and_place_maps`, `test_map_needs_parameters`, `test_lower_case_map_alias` | maps |
| `test_region_counts.py`, `test_place_image_counts.py`, `test_numbers_pages.py`, `test_count_check.py` | the place numbers |
| `test_login_needed.py` | field guides need a login, return target |
| `test_site_links.py`, `test_page_content.py`, `test_browser.py` | links and map popups, fingerprints, JavaScript |
| `test_overview_editor.py`, `test_geolocale_edit*.py` | place page text and settings (curators) |
