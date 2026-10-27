# Specimen and image pages: specimen.do, specimenImages.do, bigPicture.do, locality.do, collection.do

Read from the code on master at commit e81a8945 (2026-10-22). Paths are relative to the repo root; Java classes are under `src/org/calacademy/antweb/`. Traffic: production Tomcat log, 2026-10-11 to 2026-10-18 (8 days). The map page `bigMap.do` is in [places.md](places.md).

## In plain words

Every specimen has its own page, reached from the specimen lists of taxon pages, collections, localities and search results, from GBIF and from the persistent identifier (`antweb.org/specimen/CASENT0103190` style links):

- **Specimen page** (`specimen.do`, 49,584 requests in 8 days): the name it is filed under, then **Locality Information** (bioregion, country, adm1 and adm2 linked to their place pages, locality name linked to `locality.do`, coordinates, elevation), **Collection Information** (collection code linked to `collection.do`, collector, dates, museum, habitat, method), **Specimen Information** (caste, located at, owned by, determined by and date, type status, medium, notes, Uploaded As, the upload group), the **Name History** (since 2026-10-21: name updates, uploaded vs filed name, previous determinations), the page text curators write, and Images / Map links. Curators and admins see more (upload record, admin information).
- **Specimen images** (`specimenImages.do`, 31,523): every image of the specimen, head, profile, dorsal, label ...; the heading counts images of the ant and label images apart ("5 Images + label", "Label only", "No Images"; Brian Fisher 2026-10-27, docs/numbers.md section 1 Definitions), as do the specimen's page (admin data), the specimen rows of browse.do and the advanced search, and recent images
- **One image** (`bigPicture.do`, 8,435): one image at full size, with the photographer, copyright and licence; administrators see its EXIF data; a curator of the owning group can delete it (POST only).
- **Locality** (`locality.do`, 1,262): one collecting place: name, coordinates, country, and the collection events there ("N collections").
- **Collection** (`collection.do`, 123,310: mostly crawlers): one collection event: the locality, date, method and habitat, and every specimen collected in it.

Nothing is cached: each request reads the database.

## URLs

| URL | What it shows |
|---|---|
| `specimen.do?code=casent0103190` (or `?name=`, any case) | Specimen page |
| `specimenImages.do?code=` (or `?name=`) | The specimen's images |
| `bigPicture.do?name=<code>&shot=h&number=1` (or `code=`, or `imageId=`) | One image |
| `locality.do?code=<locality code>` (or `name=`; bots must use the code) | Locality page |
| `collection.do?name=<collection code>` | Collection page |
| `bigpicture.do`, `specimenimages.do` | Lower-case aliases (old links) |

## Code path

- **`SpecimenAction.execute()`** (`specimen.do`, `specimenImages.do`; form `SpecimenForm`): `code` (else `name`) lower-cased; `OverviewMgr.getAndSetOverview()` (the place, as for taxon pages); `specimen.isSpecimen(connection, overview)`; `specimen.init()` and `fullInit()` read the specimen, its images, its group and page text; `saveDescriptionEdit()` / `prepareDescriptionEdit()` (the page-text editor); `new Map(specimenList, connection)` (the specimen's point); `TaxonPropDb.getDefaultFor()` (taxa that use it as default image); `upload.SpecimenNameHistory.html()` (Name History, request attribute `nameHistory`). Forward `success` -> `specimen.jsp` + `specimen-body.jsp` (title "AntWeb Specimen: <Genus species> - <CODE>") or `specimenImages.jsp` + `specimenImages-body.jsp` (title "Specimen: <Genus species> - <CODE> - AntWeb").
- **`BigPictureAction.execute()`** (form `SpecimenImageForm`; its `validate()` sends a request with no code, name or imageId back to the home page): `ImageDb.getSpecimenImage(imageId)` or `(code, shot, number)`; the delete branch (curator of the image's group or admin, POST only, `ImageDb.deleteImage()`); `new Specimen(code, allantwebants, connection, true)`. JSP `bigPicture.jsp` + `bigPicture-body.jsp` (title "Closeup of the <view> of Specimen <code>"), EXIF for admins (`util/Exif*`).
- **`LocalityAction.execute()`**: `LocalityDb.getLocalityByCode(code)`, else `getLocalityByCodeOrName(name)`; a bot asking by name only is refused; `new Map(locality)` in the session for the map link. JSP `locality.jsp` + `locality-body.jsp` (title "Locality - AntWeb", heading "Locality: <name>").
- **`CollectionAction.execute()`**: `CollectionDb.getCollection(name)` (specimens of the collection with their image counts and groups); the collection is also put in the session as `advancedSearchResults` so that Download Data, Map and Compare work on it. JSP `collection.jsp` + `collection-body.jsp` (title "Collection Search - AntWeb", heading "Collection: <code>").

## Tables read

[`specimen`](../data-model.md#specimen), [`image`](../data-model.md#image), [`taxon`](../data-model.md#taxon), [`taxon_prop`](../data-model.md#taxon_prop), [`description_edit`](../data-model.md#description_edit), [`specimen_name_update`](../data-model.md#specimen_name_update) (Name History), [`upload`](../data-model.md#upload) (upload record), [`ant_group`](../data-model.md#ant_group), [`artist`](../data-model.md#artist), [`copyright`](../data-model.md#copyright), [`license`](../data-model.md#license) (image credits), [`image_like`](../data-model.md#image_like) (favourites).

## Numbers shown

[numbers.md](../numbers.md) #63 (image count, admin), #64 (specimenImages "N Images"), #65 (collection "N specimens of M species"), #66 (locality "N collections", now partly validated by `test_specimen_pages.py::test_locality_page_lists_the_specimens_collection`).

## Answers to unknown codes (pinned by the tests)

Message page, HTTP 200: "Specimen:<code> is not in the AntWeb database." (specimen and images pages); "Image not found for code:<code> shot:<shot> number:<n>"; "Must specify code and shot, or imageId."; "Locality not found for name:... or code:..."; "Collection not found - name:<code>."; a collection code with spaces, `%`, `#` or `'`: "Unsatisfactory collection code name. Perhaps looking for <cleaned code>?".

## Known issues

[public-pages-findings.md](public-pages-findings.md) F1 (busy check unused), F12 (debugging text in the locality and collection not-found messages), F21 (bigPicture.do without a code shows the home page).

## Tests

| Test | What it pins |
|---|---|
| `tests/e2e/test_specimen_pages.py::test_specimen_page_sections` | a georeferenced, imaged specimen found through the API: title, Locality / Collection / Specimen Information, Located at, Owned by, links to its images, locality, collection and map |
| `test_specimen_pages.py::test_specimen_by_name_parameter` | `name=` and upper case |
| `test_specimen_pages.py::test_specimen_images_page_and_image_page` | specimenImages "N Images", its first image on bigPicture.do |
| `test_specimen_pages.py::test_locality_page_lists_the_specimens_collection`, `test_collection_page_lists_the_specimen` | the specimen's locality and collection pages list it |
| `test_specimen_pages.py::test_unknown_code_message`, `test_lower_case_aliases` | the messages above, old lower-case addresses |
| `test_specimen_name_history.py`, `test_previous_determinations.py` | Name History and previous determinations |
| `test_exif_view.py` | EXIF on bigPicture.do (admins) |
| `test_page_content.py`, `test_site_links.py`, `test_browser.py` | fingerprints, links (with map popups), JavaScript |
| `test_page_editor.py`, `test_field_history.py` | the specimen page text (agreed test record CASENT0922067) |
