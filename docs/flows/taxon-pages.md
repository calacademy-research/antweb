# Taxon pages: description.do, browse.do, images.do

Read from the code on master at commit e81a8945 (2026-10-22). Paths are relative to the repo root; Java classes are under `src/org/calacademy/antweb/`. Traffic: production Tomcat log, 2026-10-11 to 2026-10-18 (8 days), from [catalog/actions.csv](../catalog/actions.csv).

## In plain words

A taxon page is what most visitors see: **287,732** requests in 8 days for the overview (`description.do`), **37,959** for the images page (`images.do`) and **31,893** for the list of child taxa (`browse.do`). Visitors are researchers, students and search engine crawlers; links come from Google, AntCat, AntWiki and AntWeb's own lists.

Every taxon (subfamily, genus, species, subspecies) has three tabs:

- **Overview** (`description.do`): title and author, status, the taxonomic hierarchy, the "View in AntCat" link, the head shot image, a small map of the specimens, the AntCat taxonomic history ("Taxonomic History (provided by Barry Bolton, year)"), the page text curators write (overview, distribution, identification, biology, comments), the habitat, microhabitat and method summaries ("N times": N collection events, each specimen without a collection code counted as an event of its own since 2026-10-27, B72), and the tools: Compare Images, Map of the child taxa, Create Field Guide (genus and subfamily), Download Data (genus and below).
- **Child list** (`browse.do`): the genera of a subfamily, the species of a genus, or the specimens of a species, each with its image, counts and status. The heading names the place: "Browsing genus Camponotus in Madagascar". A specimen with only label photos is not imaged (since 2026-10-27, B71, Q12: labels never count): it has the no-photos mark and is not in "(M imaged)".
- **Images** (`images.do`): the images of the child taxa, by caste (worker, queen, male) and view.

**Images from anywhere** (decided by Brian Fisher, 2026-10-27). AntWeb exists to help people identify what they found: a visitor picks a place (say Comoros) to see and identify its ants. Specimen counts stay per place (what has been recorded there); images help identification wherever the specimen came from:

- The images page of a **species or subspecies** reached with a place that selects specimens (`countryName`, `adm1Name`, `bioregionName`, `museumCode`) shows every imaged specimen of the name in AntWeb. The place's specimens come first and carry a short mark ("from Indonesia"; "in CASC" for a museum), then the representative specimens (the picked defaults and the specimen shown for the name), then the others, each group by caste. A note above the images says how many of the place's specimens are imaged, or "No specimens from Indonesia are imaged yet". The link "Only specimens from Indonesia" adds `placeOnly=true`, the view before 2026-10-27. Regions, subregions and projects select no specimens, so their species images pages always showed every specimen.
- The **Images column** of a place's list shows one number, "153 Images": the images of the ant of the name in AntWeb, from any specimen; "No Images" only when there is no image of the name in AntWeb (Brian Fisher, 2026-10-28; from 10-27 to 10-28 it also showed the place's number, "153 Images (0 from Comoros)", which wrapped to two lines, and the number script blanked the 0, "( from Comoros)"). The number links to the name's images page for the place, which shows the place's specimens first and says how many imaged specimens are from the place ("No specimens from Comoros are imaged yet..." or "The N imaged specimens from Comoros come first..."). Images of the ant only: label images are never counted (Brian Fisher, 2026-10-27, "we will go crazy if we are not consistent"); the list says so under it, with what the number counts and that the place's specimens come first on the images page. The images pages show the same number for each name. It is the allantwebants list row's Images, counted by nightly set 4, so it follows uploads, and the ant-only rule, after the next nightly run; the place's own row is used only when that number is not known. Sorting by Images follows the number shown. A specimen's own "N Images" (specimen rows, specimen images page) still counts all its image files, labels included, as its page shows them.
- The **species overview** says which castes are imaged, under the head shot: "Imaged: worker (12 specimens: 9 worker, 3 major/soldier; the ordinary worker subcaste, "normal" in the data, is shown as "worker"), queen (2), male (1)", counted live from the name's specimens anywhere with an image of the ant (label images do not count); each caste links to the images page of that caste. With no such specimen: "no specimen of this name has an image of the ant yet".
- The species list of specimens (`browse.do` of a species) and every count stay per place.

**Specimens identified only to genus** (Brian Fisher, 2026-10-29). Their one name is `<Genus> (indet)`; it is not a species, so no genus page listed it. Every genus page (`description.do` under the tools, `browse.do` under the species list, `images.do` under the images; any place or all of AntWeb) says "Not identified to species: Lividopone (indet), 45 specimens (4 imaged)", linked to the indet name's page of the same kind and place. N and M are the place's specimens of that name, as its page lists them (country, adm1, bioregion, museum; a region or subregion: its countries; all of AntWeb: all); M counts specimens with an image of the ant. No line when N is 0 or the place is a curated project. Nothing else on the page changes (numbers.md row 121).

**Not recorded from a place** (Brian Fisher, 2026-10-29). Because the place carries over in the session, a visitor could land on Lividopone "in Afrotropical" and see "0 Species Imaged (0 total)". A genus, species or subspecies page opened for a place (country, adm1, region, subregion, bioregion, museum, or a project other than `allantwebants` and `worldants`) whose list does not have the taxon and where no specimen of it was collected says at the top: "Lividopone is not recorded from Afrotropical. Recorded from: Malagasy. See Lividopone in all of AntWeb." (the bioregions whose list has it, each linked to the same page there; the last link with `project=allantwebants`). The specimen check matters for countries and adm1s, whose lists take valid names only from specimens: a morphospecies or indet name collected there is recorded though not on the list. Subfamily pages and comparisons get no notice.

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
| `images.do?...&rank=species&countryName=Indonesia` | Every imaged specimen of the species in AntWeb, Indonesia's first and marked (since 2026-10-27) |
| `images.do?...&rank=species&countryName=Indonesia&placeOnly=true` | Indonesia's specimens only |
| any of these `&project=` / `&countryName=` / `&bioregionName=` ... | The same page in that place |
| `&statusSet=valid` (or `validmorpho`, `all`, `fossil`; old values such as `valid extant` or `complete` still work, mapped to the nearest), `&global=true` | Which statuses are listed (the status choice of 2026-10-30, docs/numbers.md "Status choice of lists"; without it the place's default: Valid species on geolocales and curated projects, Valid species + morphospecies on bioregions and museums; not applied to a species page's specimens); `global=true` lists every child, not only the place's (on a species images page every specimen is listed since 2026-10-27, unless `placeOnly=true`) |
| `homonym`: `&status=homonym&authorDate=...` | A homonym's page (`homonymPage.jsp`) |

Without a rank, the rank is inferred from the parameters (`BrowseAction.inferredRank()`); `rank=subgenus` is treated as genus.

## Code path

1. `BrowseAction.execute()` (one class for `description.do`, `browse.do`, `images.do`, `getComparison.do` and `mapComparison.do`; the form is `BrowseForm`, request scope for the three tabs):
   - start-up and request checks: `Check.init(TAXON)` (message while the name lists load), `Check.valid()` (`HttpUtil.invalidRequest`: several `?`, blocked agents), `ProjectMgr.hasMoved()` (redirects old project names);
   - the place: `OverviewMgr.getAndSetOverview()` -> `findOverview()` reads the place parameters in a fixed order (project, museumCode, geolocaleId, regionName, subregionName, countryName, adm1Name, bioregionName), else the session's `overview`, else `allantwebants`; an unknown place throws `AntwebException` and the page is a message ("Country not found for countryName:X");
   - `taxonName=` / `antcatId=` -> `taxonNameRedirect()`: `TaxonDb.getTaxon()`, else `HomonymDb.getHomonym()`, else `CommonNames`, else the AntCat id (`TaxonDb.getTaxonNameFromAntcatId()`), then a redirect to `Taxon.getUrl()`;
   - the taxon: `TaxonDb.getFullTaxon(family, subfamily, genus, species, subspecies, rank)`, else `HomonymDb.getFullHomonym()`; a name that is not valid seen in `worldants` switches the place to `allantwebants`;
   - `taxon.initTaxonSet(connection, overview)`: the taxon's row in the place's list (none: "Taxon:x not found for overview:y");
   - place notes (`PlaceTaxonNotes`, 2026-10-29; description, browse and images only): when the row does not exist (`TaxonSet.exists()`), one query of the place's specimens of the taxon (`placeSpecimenSql`) and one of `bioregion_taxon` (`BIOREGIONS_SQL`) give the not-recorded notice (request attribute `notRecorded`, `web/common/notRecorded.jsp` above the hierarchy); on a genus page one count of the indet name's specimens (`indetCountSql`) gives the indet line (`genusIndetLine`, `web/common/genusIndetLine.jsp`); a failure is logged and costs only the note;
   - page text: `saveDescriptionEdit()` (curator POST, see the editor flow) and `prepareDescriptionEdit()`; the text itself is in `description_edit`;
   - images: `taxon.setImages(connection, overview, caste)`: the default specimen of the taxon for each caste (`taxon_prop`, chosen by curators with the image picker), else a child's default, else a specimen of the taxon with a head shot;
   - imaged castes (`description.do` of a species or subspecies): `ImagedCastes.count()`, one grouped query of the name's specimens joined to their images (labels left out), `ImagedCastes.toHtml()` -> request attribute `imagedCastes`;
   - children (`browse.do`, `images.do`): `taxon.setChildren(connection, overview, statusSet, getChildImages, getChildMaps, caste, global, subgenus)` reads the place's list (`proj_taxon`, `geolocale_taxon`, `museum_taxon` or `bioregion_taxon`) and the children's images; on `images.do` of a species with a place that selects specimens, `global` is set unless `placeOnly=true` (`ImagesAnywhere`), `Species.setChildren` keeps the codes of the place's specimens (`Taxon.getPlaceCodes()`) and the request carries `imagesPlaceCodes`, `imagesFromPhrase` and `imagesPlaceOnly` for `taxonChildImages.jsp` (order and note) and `taxonChildImageSet.jsp` (the mark);
   - map (`description.do`, `images.do`): `MapMgr.getMap(taxon, overview)` -> `new Map(taxon, overview, connection)`, the place's georeferenced specimens of the taxon, read live;
   - forward: `success` -> `taxonPage.jsp` / `showBrowse.jsp` / `imagePage.jsp`; `homonym` -> `homonymPage.jsp`; `message` -> `common/message.jsp`.
2. JSPs: `web/taxonPage.jsp` + `taxonPage-body.jsp` (title `<Rank>: <Name> - AntWeb`; includes `common/taxonTitle.jsp`, `common/viewInAntCat.jsp`, `common/taxonomicHierarchy.jsp`, `common/pageToolLinks.jsp`, `distribution.jsp`, the page-text fields `common/descEdit/taxonEditFieldCK.jsp`, the map `maps/googleMapInclude.jsp`); `web/showBrowse.jsp` + `showBrowse-body.jsp` (title `Browsing <rank> <Name> in <place> - AntWeb`; `taxonReport.jsp` for taxa, `specimenReport.jsp` for specimens); `web/imagePage.jsp` + `imagePage-body.jsp` (title `<Rank>: <Name> - AntWeb`).
3. Autocomplete of the header box: `web/search/autoCompleteKeys.jsp?q=` -> `TaxonMgr.getPrettyTaxaNames()` (in memory). Allowed as a direct JSP by Caddy (`Caddyfile.common`).

## Tables read

[`taxon`](../data-model.md#taxon), [`homonym`](../data-model.md#homonym), [`taxon_prop`](../data-model.md#taxon_prop) (default images), [`description_edit`](../data-model.md#description_edit) (page text), [`proj_taxon`](../data-model.md#proj_taxon), [`geolocale_taxon`](../data-model.md#geolocale_taxon), [`museum_taxon`](../data-model.md#museum_taxon), [`bioregion_taxon`](../data-model.md#bioregion_taxon) (the place's list), [`specimen`](../data-model.md#specimen) and [`image`](../data-model.md#image) (images, map points, habitat summaries). Places come from memory (loaded from [`geolocale`](../data-model.md#geolocale), [`project`](../data-model.md#project), [`museum`](../data-model.md#museum), [`bioregion`](../data-model.md#bioregion)).

## Numbers shown

[numbers.md](../numbers.md) rows #38 to #49 (list headers and row counts of `browse.do`), #50 to #53 (habitat, microhabitat and method summaries), #54 to #57 (image counts), #61 and #62, #44 (the Images column: the images of the name in AntWeb, one number since 2026-10-28) and #120 (imaged castes).

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
| `test_images_anywhere.py` | images from anywhere: Michele's Leptogenys purpurea in Indonesia (an image from outside Indonesia, Indonesian specimens first and marked, `placeOnly=true` Indonesia's only), the day's places (marked first = `placeOnly=true`), the Images column (one number, the images of the name in AntWeb) against the API, the imaged castes line against the API |
| `tests/e2e/test_genus_indet_places.py` | the indet line on the three genus pages agrees with the indet page it links (Lividopone in all of AntWeb and Mozambique, the day's indet genera); the not-recorded notice (Lividopone in Neotropical; never in Malagasy, Madagascar, Mozambique; the day's genus against the API's bioregion lists); the genus rows of indet specimens on the Mozambique and Afrotropical lists (pending until nightly set 2 has the compute.log line) |
| `test/org/calacademy/antweb/PlaceTaxonNotesTest.java`, `test/.../home/PlaceTaxonNotesDbTest.java` | the line's and notice's text and links, which specimens each place selects (no database); the counts, the specimen check and the genus rows against MySQL |
| `test/org/calacademy/antweb/ImagesAnywhereTest.java`, `tests/unit/test_images_anywhere.py` | the order, the column format, the castes line and query (no database); the wiring in the action and JSPs |

- 2026-10-29: the picture of a genus on a place's genus images page is chosen from the genus's names in that place, checked in groups of 250 names (up to 5,000). Before, a list over 10,000 characters was dropped, so large genera (Crematogaster and Pheidole in the Afrotropical region) showed no image; only the first 50 names were checked for a picked image. Test: TaxonImageNamesTest, tests/e2e/test_images_anywhere.py.
- 2026-10-30: the status selector of lists offers four plain choices (Valid species, Valid species + morphospecies, All names, Fossil species; address values valid, validmorpho, all, fossil), each matching a place-page number; "all determined", "all indetermined", "complete" and "type" are removed and their old addresses mapped to the nearest choice. Defaults by kind of place; the choice is kept in the page's tabs, never in the session; a species page's specimens are listed whatever the choice (with "valid extant" a morphospecies page listed none), and it shows no selector. Tests: StatusChoiceTest, tests/e2e/test_status_choice.py.
