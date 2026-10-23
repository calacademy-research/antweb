# API v3.1

Read from the code on master at commit e81a8945 (2026-10-22): `api/v3.1/api.py` (Flask 3, SQLAlchemy 1.4, gunicorn), container `antweb_api_1`, served by Caddy at `https://www.antweb.org/v3.1/` (Cloudflare's challenge is off for this path). The public description is the page `apiV3.1.do` (also `api.do`; `web/documentation/api/apiV3_1-body.jsp`) and [../API.md](../API.md). The API's requests are not in the Tomcat log, so there is no traffic figure here.

## In plain words

The API gives the same data as the site as JSON, for scripts: specimens, names, images, places and their species lists, measurements. Users are researchers and other databases (GBIF-style harvesters, R and Python packages, AntMaps-type sites). It only reads: on the servers it logs in to MySQL as a read-only user (`etc/api-db.properties`, mounted as `/antweb-etc/api-db.properties`).

Rules common to every route:

- **`up=1` is required.** Without it every data route answers HTTP 200 with an empty body (`down()`, a switch kept from an outage). The documentation's examples add it.
- **At most 500 records per request** (since 2026-10-21, until registered API keys; it was 10,000). `limit=` asks for fewer; without `limit` a request gets 500. Above the cap, the reply has 500 records, `metaData.limitNotice` and the header `X-AntWeb-Limit-Notice` ("Requests are limited to 500 records while AntWeb moves to registered API keys. For larger data needs contact antweb@antweb.org."). The cap is `maxLimit` in `etc/api-db.properties` (restart the container after a change). `offset=` fetches the next records; only routes with a fixed order page reliably (below).
- **Unknown parameters are refused:** HTTP 400 with `error`, `unknownParameters`, `acceptedParameters`, `documentation` and, when a name is close, `didYouMean` (`/specimens?code=` -> `specimenCode`). Since 2026-10-21 (#224); before, a misspelt filter was ignored.
- `ndjson=true`: one JSON line per record (with an index line before each), no metaData; the limit notice is in the header.
- Errors: a database or code error answers HTTP 500 `{"error": "The request could not be completed. Please try again later or simplify the query."}` (details only in the container log); an unknown route `{"error": "Not Found"}` with 404.
- Reply: `{"metaData": {"parameters": [...], "request": "...", "limit": N, "offset": N, "count": N, ...}, "<list>": [...]}`. Some routes have no `limit`/`offset` in metaData or a different count name (table below).

## Routes

| Route | Parameters (besides up, limit, offset, ndjson, log) | Returns (list key; metaData count) | Reads | Order (offset paging) |
|---|---|---|---|---|
| `/v3.1/` | none | the text "Antweb API V3.1" and links | - | - |
| `/specimens` | family, subfamily, genus, species, specimenCode, region, subregion, country, adm1, bioregion, habitat, type, georeferenced, bbox, minDate, maxDate, minElevation, maxElevation, museum, ownedby, locatedat, collectedby, caste, subcaste, fossil, validGenus, validSubfamily, status, hasImage, geolocaleName, lifeStageSex | `specimens`: specimenCode, occurrenceId, institution and collection codes, classification (kingdom to subspecies, scientificName), type, region to adm1, bioregion, decimalLatitude/Longitude, geo, dates, habitat, microhabitat, collectedBy, samplingMethod, lifeStageSex, medium, determinedBy, locality, notes, minimumElevationInMeters, antwebTaxonName, ownedBy, locatedAt, caste, subcaste, fossil, status, imageCount; `count`, `queryTime` | view [`api3_1_specimen`](../data-model.md#api3_1_specimen) (+ `taxon`, `geolocale_taxon`, `geolocale` for geolocaleName) | specimen code: yes |
| `/geoSpecimens` | coords (lat,lon; required), radius (default 5), distinct (subfamily, genus) | `specimens` (as /specimens); with distinct: `subfamilies` or `genera` (names); `count` | `api3_1_specimen` | specimen code: yes |
| `/geoSpeciesStats` | geolocaleName (required) | `geoSpeciesStats`: per valid species of the place, its specimens by caste and subcaste (queenCount ... maleAlateCount); `count`. An unknown place answers the plain text "Geolocale not found" (HTTP 200) | [`specimen`](../data-model.md#specimen), [`taxon`](../data-model.md#taxon), [`geolocale_taxon`](../data-model.md#geolocale_taxon), [`geolocale`](../data-model.md#geolocale) | taxon name: yes |
| `/taxa` | taxonName, taxarank, subfamily, genus, species, subspecies, status | `taxa`: taxonName, taxarank, classification, parentTaxonName, fossil, source, created, antcat, antcatId, authorDate, authors, year, status, currentValidName, currentValidParent, bioregion, type, the counts (subfamilyCount, genusCount, speciesCount, specimenCount: not written by any code, numbers.md #103), imageCount; `taxonCount` (no limit in metaData) | [`taxon`](../data-model.md#taxon) | none |
| `/distinctTaxa` | taxarank (subfamily, genus, species), country, habitat, minDate, maxDate, minElevation, maxElevation, status | `subfamilies`, `genera` or `taxarank` (names); `count` | `api3_1_specimen` | none |
| `/images` | since (days), specimenCode, shotType, shotNumber, hasTiff | `images`: one record per specimen: specimenCode, url, images (imageId, shotType, shotNumber, uploadDate, hasTiff, urls: low, med, high, thumbview); `count` = images, `specimenCount` = records | [`image`](../data-model.md#image) | none |
| `/measurements` | release, trait, view, status, hasValue, specimenCode, subfamily, genus, species, country, adm1, bioregion, caste, taxonStatus | `measurements`: one per measured trait (traitName, value, unit, keypoints, QA flags, the image and the specimen's taxon and place) | [`measurement`](../data-model.md#measurement), `api3_1_specimen` | measurement id: yes |
| `/taxaImages` | taxonName, subfamily, genus, species, subspecies, specimenCode, imageId, shotType, shotNumber, hasTiff | `taxaImages`: per taxon its specimens and their images; `taxonCount`, `specimenCount`, `imageCount` | view [`taxon_image`](../data-model.md#taxon_image) | none (F8) |
| `/geolocales` | geolocaleId, geolocaleName (or name), parent, georank | `geolocales`: geolocaleId, geolocaleName, georank, parent, region, subregion, country, bioregion, the place counts, imagedSpecimenCount, isValid, endemic and introduced counts, isoCode, iso3Code; `geolocaleCount` | [`geolocale`](../data-model.md#geolocale) | id: yes |
| `/geolocaleTaxa` | geolocaleId, geolocaleName, georank, region, subregion, country, island, adm1, taxonName, taxarank, subfamily, genus, species, status | `geolocaleTaxa`: the place, the taxon, its status, source and counts; `geolocaleCount` (= records) | [`geolocale_taxon`](../data-model.md#geolocale_taxon), `geolocale`, `taxon` | none |
| `/bioregions` | bioregionName | `bioregions`: bioregionName, description and the counts; `bioregionCount` | [`bioregion`](../data-model.md#bioregion) | name: yes |
| `/bioregionTaxa` | bioregionName, taxonName, taxarank, subfamily, genus, species, status | `bioregionTaxa`; `bioregionCount` (= records) | [`bioregion_taxon`](../data-model.md#bioregion_taxon), `taxon` | none |
| `/unimagedGeolocaleTaxa` | geolocaleName, byCaste (1), taxarank, status | `unimagedGeolocaleTaxaDict`: taxonName (and castes); `unimagedGeolocaleTaxaCount`, `unimagedGeolocaleTaxaCasteCount` | `geolocale_taxon`, `taxon`, `specimen`, `image` (a taxon is imaged when a specimen has an image that is not a label, 2026-10-21) | taxon name: yes |

## Code path

`api.py`: `checkParams()` (before every request: the accepted names are read from each route's `request.args.get()` calls, plus up, limit, offset, ndjson, log) -> the route function -> `setGlobals()` (limit, offset, ndjson, per request in `flask.g`) -> `down()` -> a SQLAlchemy query on a model class (`Specimen`, `Taxon`, `Image`, `Measurement`, `TaxaImage`, `Geolocale`, `GeolocaleTaxon`, `Bioregion`, `BioregionTaxon`) or plain SQL with bound parameters (`/geoSpeciesStats`, `/unimagedGeolocaleTaxa`) -> `jsonify()` (adds the limit notice) or `getNdjson()`. One database session per request (`scoped_session`, released in `remove_session()`); `pool_pre_ping` replaces connections MySQL closed. Uncaught errors: `handleUncaught()` -> `apiError()`.

## Numbers shown

[numbers.md](../numbers.md) #100 to #118. Validated by this step: #101, #105, #106, #111, #113, #115 (the metaData counts equal the records).

## Known issues

[public-pages-findings.md](public-pages-findings.md): F5 (fixed here: /images keyed every record but the last `spefimenCode`), F6 (/images `url` points to `localhost:5000` with a refused parameter; an empty result is `[{}]`), F7 (`validGenus=true` drops every record; the valid filters run after limit and offset), F8 (routes without a fixed order page unreliably; /taxaImages's order is computed and thrown away), F16 (/measurements is not on the documentation page), F20 (debug lines in the log for every /specimens request), F22 (/geoSpecimens radius is in degrees, not kilometres; `distinct` ignores the area), F23 (/geoSpeciesStats counts every ergatoid specimen as an ergatoid queen and as an ergatoid male).

## Tests

| Test | What it pins |
|---|---|
| `tests/e2e/test_api.py::test_api` | every route answers 200 with JSON (`api_cases.py`) |
| `test_api.py::test_reply_shape_and_limit` | every route: metaData and its list, at most `limit` records, the count in metaData = records, the documented fields of each record |
| `test_api.py::test_images_records_name_their_specimen` | /images records: specimenCode, images with four urls, counts |
| `test_api.py::test_offset_pages_follow_on` | offset paging of /geolocales and /bioregions |
| `test_api.py::test_limit_1000_returns_500_and_the_notice`, `test_limit_within_the_cap_has_no_notice`, `test_ndjson_is_capped_with_the_notice_header`, `test_offset_pages_past_the_cap`, `test_geolocales_are_capped`, `test_taxa_cap_sends_the_notice_header`, `test_taxa_respects_limit` | the 500 cap and its notice, /specimens paging |
| `test_api.py::test_unknown_parameter_is_refused`, `test_specimen_code_returns_one_specimen` | unknown parameters, specimenCode |
| `test_api.py::test_without_up_the_reply_is_empty`, `test_unknown_route_is_json_404` | `up=1`, 404 |
| `test_api.py::test_documentation_page_names_every_route` | api.do and apiV3.1.do describe each route (except /measurements, F16) |
| `test_api.py::test_valid_genus_filter_keeps_valid_genera`, `test_geo_specimens_stay_within_the_radius`, `test_geo_specimens_distinct_uses_the_area` | xfail: F7, F22 |
| `test_hardening_2026_10_18.py::test_api_ndjson_lines_are_json`, `test_api_limit_is_capped` | ndjson, cap |
| `tests/unit/test_api_limits.py`, `test_api_params.py`, `test_api_counts.py` (CI, in the API image) | limits per request, parameter checks and the documented examples, imaged rule, /images records |
