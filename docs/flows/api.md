# API v3.1

Read from the code on master at commit e81a8945 (2026-10-22): `api/v3.1/api.py` (Flask 3, SQLAlchemy 1.4, gunicorn), container `antweb_api_1`, served by Caddy at `https://www.antweb.org/v3.1/` (Cloudflare's challenge is off for this path). The public description is the page `apiV3.1.do` (also `api.do`; `web/documentation/api/apiV3_1-body.jsp`) and [../API.md](../API.md). The API's requests are not in the Tomcat log, so there is no traffic figure here.

## In plain words

The API gives the same data as the site as JSON, for scripts: specimens, names, images, places and their species lists, measurements. Users are researchers and other databases (GBIF-style harvesters, R and Python packages, AntMaps-type sites). It only reads: on the servers it logs in to MySQL as a read-only user (`etc/api-db.properties`, mounted as `/antweb-etc/api-db.properties`). On production and staging the API does not start without that file (`API_REQUIRE_READONLY_LOGIN: "true"` in `docker-compose.yml`, decided by Brian Fisher 2026-10-27; the container log says `ERROR: no read-only login` and the container restarts until the file is there). On a development machine (`docker-compose.dev.yml` sets `"false"`) it prints a warning and uses the main account.

Rules common to every route:

- **`up=1` is required.** Without it every data route answers HTTP 503 with `{"error": "The AntWeb API is closed while we add free registered API keys. It will reopen in the coming weeks; see antweb.org for news.", "documentation": ..., "terms": ...}` (`checkUp()` before every request, `apiDown()`; decided by Brian Fisher 2026-10-27, it was HTTP 200 with an empty body; wording changed 2026-11-02 so callers see why and that it is not an outage). `/v3.1/` itself stays HTTP 200 (the uptime check and the container health check use it). The documentation's examples add `up=1` for admins. **This holds while v4 is closed.** Once v4 is open, v3.1 has ended: every data route answers HTTP 410 and `up=1` does nothing (see "The switch: v4 opens, v3.1 ends" below).
- **Date and elevation filters include their bounds** (`boundsFilter()`, `/specimens` and `/distinctTaxa`; decided by Brian Fisher 2026-10-27, they were exclusive): `minDate`/`maxDate` compare the collection start date, `maxDate=1979-12-31` includes the whole of that day; `minElevation`/`maxElevation` compare `minimumElevationInMeters`, so `minElevation=1200` includes specimens at 1200 m.
- **`/geoSpeciesStats` subcaste counts count only within their caste** (decided by Brian Fisher 2026-10-27, as the ergatoids since B30): `queenAlateDealateCount` and `queenBrachypterousCount` count queens, `workerMajorSoldierCount` and `workerNormalCount` workers, `maleAlateCount` males. Before, a specimen of another caste with the same subcaste name was counted too.
- **At most 500 records per request** (since 2026-10-21, until registered API keys; it was 10,000). `limit=` asks for fewer; without `limit` a request gets 500. Above the cap, the reply has 500 records, `metaData.limitNotice` and the header `X-AntWeb-Limit-Notice` ("Requests are limited to 500 records while AntWeb moves to registered API keys. For larger data needs contact antweb@antweb.org."). The cap is `maxLimit` in `etc/api-db.properties` (restart the container after a change). `offset=` fetches the next records; only routes with a fixed order page reliably (below).
- **Unknown parameters are refused:** HTTP 400 with `error`, `unknownParameters`, `acceptedParameters`, `documentation` and, when a name is close, `didYouMean` (`/specimens?code=` -> `specimenCode`). Since 2026-10-21 (#224); before, a misspelt filter was ignored.
- **`country=` is the whole country name, in any case** (`countryIs()`), on every route that has it: `country=Niger` finds Niger, not Nigeria. Since 2026-10-27 (F24); before, /specimens, /distinctTaxa and /measurements matched any country containing the text.
- **`country=` and islands** (2026-11-11, Brian Fisher: an oceanic island is its own country): `country=` compares with `specimen.country`, which for a specimen of Hawaii or the Galapagos Islands is the island's own name once the stored specimens are moved (`tools/place/island_countries.py`). So `country=United States` no longer returns Hawaii's specimens and `country=Hawaii` returns them (before: none).
- **`country=` given as an old name of a country** (2026-11-10, `currentCountry()`; /specimens, /distinctTaxa, /measurements, /geolocaleTaxa, v3.1 and v4): when AntWeb has the name as a not-valid country whose valid name is a valid country (USA: United States; after a rename, Turkey: Türkiye), the answer is the current country's records and `metaData.countryName` names both: `{"requested": "USA", "used": "United States"}`. A valid name, an unknown name and an old name without a valid name are used as given. `geolocaleName=` is not resolved. Tests: `tests/unit/test_api_country.py`, `test_api_v4.py`.
- `ndjson=true`: one JSON line per record (with an index line before each), no metaData; the limit notice is in the header.
- **Licence** (since 2026-10-29, CC BY 4.0): every reply has the header `Link: <https://creativecommons.org/licenses/by/4.0/>; rel="license"` (`addLicenseHeader`); every JSON data reply has a top-level `license` object (name, url, citation; `LICENSE` in api.py, the site's text in `util/License.java`). Error replies stay `{"error": ...}`. `tests/unit/test_api_license.py`.
- Errors: a database or code error answers HTTP 500 `{"error": "The request could not be completed. Please try again later or simplify the query."}` (details only in the container log); an unknown route `{"error": "Not Found"}` with 404.
- Reply: `{"license": {...}, "metaData": {"parameters": [...], "request": "...", "limit": N, "offset": N, "count": N, ...}, "<list>": [...]}`. Some routes have no `limit`/`offset` in metaData or a different count name (table below).

## Routes

| Route | Parameters (besides up, limit, offset, ndjson, log) | Returns (list key; metaData count) | Reads | Order (offset paging) |
|---|---|---|---|---|
| `/v3.1/` | none | JSON front door (2026-11-08): name "Antweb API V3.1", status, message, documentation, terms (`apiV3.1.do#terms`), guideForPrograms (`/llms.txt`), contact, citation, v4 (status open or closed, url, getAKey; since PR 3) | - | - |
| `/specimens` | family, subfamily, genus, species, specimenCode, region, subregion, country, adm1, bioregion, habitat, type, georeferenced, bbox, minDate, maxDate, minElevation, maxElevation, museum, ownedby, locatedat, collectedby, caste, subcaste, fossil, validGenus, validSubfamily, status, hasImage, geolocaleName, lifeStageSex | `specimens`: specimenCode, occurrenceId, institution and collection codes, classification (kingdom to subspecies, scientificName), type, region to adm1, bioregion, decimalLatitude/Longitude, geo, dates, habitat, microhabitat, collectedBy, samplingMethod, lifeStageSex, medium, determinedBy, locality, notes, minimumElevationInMeters, antwebTaxonName, ownedBy, locatedAt, caste, subcaste, fossil, status, imageCount; `count`, `queryTime` | view [`api3_1_specimen`](../data-model.md#api3_1_specimen) (+ `taxon`, `geolocale_taxon`, `geolocale` for geolocaleName) | specimen code: yes |
| `/geoSpecimens` | coords (lat,lon; required: without it HTTP 400 as JSON since 2026-11-10, it was plain text with HTTP 200), radius (kilometres, default 5; converted to a box in degrees by `radiusBox()` since 2026-10-26, was added as degrees), distinct (subfamily, genus, species) | `specimens` (as /specimens); with distinct: `subfamilies`, `genera` or `species` ("Genus species") names of the specimens in the area; another distinct value: HTTP 400; `count` | `api3_1_specimen` | specimen code; distinct lists by name: yes |
| `/geoSpeciesStats` | geolocaleName (required) | `geoSpeciesStats`: per valid species of the place, its specimens by caste and subcaste (queenCount ... maleAlateCount); `count`. An unknown place answers HTTP 404 `{"error": "Geolocale not found: <name>"}` (since 2026-10-26, F23; before, the plain text "Geolocale not found" with HTTP 200) | [`specimen`](../data-model.md#specimen), [`taxon`](../data-model.md#taxon), [`geolocale_taxon`](../data-model.md#geolocale_taxon), [`geolocale`](../data-model.md#geolocale) | taxon name: yes |
| `/taxa` | taxonName, taxarank, subfamily, genus, species, subspecies, status | `taxa`: taxonName, taxarank, classification, parentTaxonName, fossil, source, created, antcat, antcatId, authorDate, authors, year, status, currentValidName, currentValidParent, bioregion, type, the counts (subfamilyCount, genusCount, speciesCount, specimenCount: not written by any code, numbers.md #103), imageCount; `taxonCount` (no limit in metaData) | [`taxon`](../data-model.md#taxon) | taxon name: yes (since 2026-10-26) |
| `/distinctTaxa` | taxarank (subfamily, genus, species), country, habitat, minDate, maxDate, minElevation, maxElevation, status | `subfamilies`, `genera` or `taxarank` (names; for species "Genus species" since 2026-10-27, leaving out names not identified to genus: genus "(...)", indet or undet; there is no family filter, so specimens of other families in the database are listed too); `count` | `api3_1_specimen` | the selected rank (specimen code without taxarank): yes (since 2026-10-26) |
| `/images` | since (days), specimenCode, shotType, shotNumber, hasTiff | `images`: one record per specimen: specimenCode, url (`https://www.antweb.org/v3.1/specimens?specimenCode=<code>` since 2026-10-26), images (imageId, shotType, shotNumber, uploadDate, hasTiff, urls: low, med, high, thumbview); `count` = images, `specimenCount` = records; nothing found: `[]`, specimenCount 0 | [`image`](../data-model.md#image) | specimen code, shot type, shot number, id: yes (limit and offset count images, so one specimen's images can still fall on two pages) |
| `/measurements` | release, trait, view, status, hasValue, specimenCode, subfamily, genus, species, country, adm1, bioregion, caste, taxonStatus | `measurements`: one per measured trait (traitName, value, unit, keypoints, QA flags, the image and the specimen's taxon and place) | [`measurement`](../data-model.md#measurement), `api3_1_specimen` | measurement id: yes |
| `/taxaImages` | taxonName, subfamily, genus, species, subspecies, specimenCode, imageId, shotType, shotNumber, hasTiff | `taxaImages`: per taxon its specimens and their images; `taxonCount`, `specimenCount`, `imageCount` | view [`taxon_image`](../data-model.md#taxon_image) | taxon name, code, uid: yes (since 2026-10-26, F8) |
| `/geolocales` | geolocaleId, geolocaleName (or name), parent, georank | `geolocales`: geolocaleId, geolocaleName, georank, parent, region, subregion, country, bioregion, the place counts, imagedSpecimenCount, isValid, endemic and introduced counts, isoCode, iso3Code; `geolocaleCount` | [`geolocale`](../data-model.md#geolocale) | id: yes |
| `/geolocaleTaxa` | geolocaleId, geolocaleName, georank, region, subregion, country, island, adm1, taxonName, taxarank, subfamily, genus, species, status | `geolocaleTaxa`: the place, the taxon, its status, source and counts; `geolocaleCount` (= records) | [`geolocale_taxon`](../data-model.md#geolocale_taxon), `geolocale`, `taxon` | geolocale id, taxon name: yes (since 2026-10-26) |
| `/bioregions` | bioregionName | `bioregions`: bioregionName, description and the counts; `bioregionCount` | [`bioregion`](../data-model.md#bioregion) | name: yes |
| `/bioregionTaxa` | bioregionName, taxonName, taxarank, subfamily, genus, species, status | `bioregionTaxa`; `bioregionCount` (= records) | [`bioregion_taxon`](../data-model.md#bioregion_taxon), `taxon` | bioregion, taxon name: yes (since 2026-10-26) |
| `/unimagedGeolocaleTaxa` | geolocaleName (required: without it HTTP 400, an unknown place HTTP 404 as JSON since 2026-11-10; both were plain text with HTTP 200), byCaste (1), taxarank, status | `unimagedGeolocaleTaxaDict`: taxonName (and castes); `unimagedGeolocaleTaxaCount`, `unimagedGeolocaleTaxaCasteCount` | `geolocale_taxon`, `taxon`, `specimen`, `image` (a taxon is imaged when a specimen has an image that is not a label, 2026-10-21) | taxon name: yes |

## Code path

`api.py`: `checkUp()` (before every request: 503 without up=1) -> `checkParams()` (before every request: the accepted names are read from each route's `request.args.get()` calls, plus up, limit, offset, ndjson, log) -> the route function -> `setGlobals()` (limit, offset, ndjson, per request in `flask.g`) -> `down()` -> a SQLAlchemy query on a model class (`Specimen`, `Taxon`, `Image`, `Measurement`, `TaxaImage`, `Geolocale`, `GeolocaleTaxon`, `Bioregion`, `BioregionTaxon`) or plain SQL with bound parameters (`/geoSpeciesStats`, `/unimagedGeolocaleTaxa`) -> `jsonify()` (adds the limit notice) or `getNdjson()`. One database session per request (`scoped_session`, released in `remove_session()`); `pool_pre_ping` replaces connections MySQL closed. Uncaught errors: `handleUncaught()` -> `apiError()`.

## Numbers shown

[numbers.md](../numbers.md) #100 to #118. Validated by this step: #101, #105, #106, #111, #113, #115 (the metaData counts equal the records).

## Known issues

[public-pages-findings.md](public-pages-findings.md): F5 (fixed here: /images keyed every record but the last `spefimenCode`), F6, F7, F8 (fixed 2026-10-26: /images `url` was `localhost:5000` with a refused parameter; `validGenus=true` dropped every record and both valid filters ran after limit and offset, now SQL filters in lower case; routes without a fixed order), F16 and F20 (fixed 2026-10-26: /measurements is described on the documentation page; /specimens prints its debug lines only with `log=true`), F22 (fixed 2026-10-26: /geoSpecimens radius was in degrees, not kilometres; `distinct` ignored the area; `distinct=species` was not handled), F23 (/geoSpeciesStats: the ergatoid counts by caste were fixed 2026-10-22; an unknown place is a JSON 404 since 2026-10-26), F24 (fixed 2026-10-27: /distinctTaxa `taxarank=species` gave epithets without their genus, now "Genus species", without names not identified to genus (Brian Fisher's rule of 2026-10-22); `country` matched by "contains", now the whole name in any case on /specimens, /distinctTaxa, /measurements, /geolocaleTaxa and the geolocaleName filter of /specimens).

## Tests

| Test | What it pins |
|---|---|
| `tests/e2e/test_api.py::test_api` | every route answers 200 with JSON (`api_cases.py`) |
| `test_api.py::test_reply_shape_and_limit` | every route: metaData and its list, at most `limit` records, the count in metaData = records, the documented fields of each record |
| `test_api.py::test_images_records_name_their_specimen` | /images records: specimenCode, images with four urls, counts |
| `test_api.py::test_offset_pages_follow_on` | offset paging of /geolocales, /bioregions, /taxa and /bioregionTaxa |
| `test_api.py::test_limit_1000_returns_500_and_the_notice`, `test_limit_within_the_cap_has_no_notice`, `test_ndjson_is_capped_with_the_notice_header`, `test_offset_pages_past_the_cap`, `test_geolocales_are_capped`, `test_taxa_cap_sends_the_notice_header`, `test_taxa_respects_limit` | the 500 cap and its notice, /specimens paging |
| `test_api.py::test_unknown_parameter_is_refused`, `test_specimen_code_returns_one_specimen` | unknown parameters, specimenCode |
| `test_api.py::test_without_up_the_reply_is_503_json`, `test_unknown_route_is_json_404` | `up=1` (503 JSON, `/v3.1/` stays 200), 404 |
| `test_api.py::test_documentation_page_names_every_route` | api.do and apiV3.1.do describe each route (geoSpeciesStats to admins only; /measurements since 2026-10-26, F16) |
| `test_api.py::test_valid_genus_filter_keeps_valid_genera`, `test_geo_specimens_stay_within_the_radius`, `test_geo_specimens_distinct_uses_the_area` | F7, F22 (xfail removed 2026-10-26) |
| `test_api.py::test_geo_species_stats_unknown_place_is_json_404`; `tests/unit/test_api_findings.py::test_geo_species_stats_unknown_place_is_json_404`, `test_specimens_debug_lines_only_with_log_true` | F23, F20 (2026-10-26) |
| `test_hardening_2026_10_18.py::test_api_ndjson_lines_are_json`, `test_api_limit_is_capped` | ndjson, cap |
| `tests/unit/test_api_limits.py`, `test_api_params.py`, `test_api_counts.py` (CI, in the API image) | limits per request, parameter checks and the documented examples, imaged rule, /images records |
| `tests/unit/test_api_decisions.py` (CI, in the API image; SQLite) | decisions of 2026-10-27: /geoSpeciesStats subcastes within their caste, inclusive date and elevation bounds (/specimens, /distinctTaxa), 503 JSON without up=1 on every data route, the read-only login required with `API_REQUIRE_READONLY_LOGIN` |
| `.github/workflows/tests.yaml` "API refuses to start without its read-only login" | the API container with `API_REQUIRE_READONLY_LOGIN=true` and no login file exits |
| `tests/unit/test_api_findings.py` (CI, in the API image; SQLite) | F6 /images url and empty result, F7 valid filters in lower case before limit, F8 ORDER BY on every listed route, F22 radius in km (`radiusBox`), distinct lists of the area, distinct=species |
| `tests/unit/test_api_country.py` (CI, in the API image; SQLite); `test_api.py::test_distinct_taxa_species_are_full_names`, `test_country_matches_the_whole_name` | F24 (2026-10-27): /distinctTaxa species are "Genus species", none of them not identified to genus; `country` is the whole name in any case on every route |

## Access, terms and /llms.txt (step 6, API keys PR 1, 2026-11-08)

The API page `apiV3.1.do` has a section "Access and terms of use" (`#terms`), text approved by Brian Fisher 2026-11-08: status, access and free keys (coming), responsible access (bulk by arrangement), citation with specimen codes, collaboration and contributing back, licence CC BY 4.0, identifications vs AntCat names, usage records. Every 503 sends `Retry-After: 86400`. `web/llms.txt` (served at `https://www.antweb.org/llms.txt`) is the plain-text version for programs and AI agents: use the API not the web pages, no harvesting or mirrors, bulk data only by agreement, citation, CC BY 4.0, contact. Both are linked from `/v3.1/` and from every 503. Tests: `tests/unit/test_api_decisions.py::test_front_door_points_to_the_terms_and_the_guide`, `tests/e2e/test_api.py::test_llms_txt_and_the_terms_on_the_api_page`.

## v4 and API keys (step 6, API keys PR 3, 2026-11-08)

Decisions by Brian Fisher, 2026-11-08. The keyed API is **v4** at `https://www.antweb.org/v4/`: the same routes as v3.1 (`/v4/specimens`, `/v4/taxa`, ... every v3.1 data route), with free registered API keys, limits per level and clearer field names. v3.1 answers exactly as before (closed, 503 with the terms).

### Code

- `api/v3.1/api_v4.py`: a separate small Flask application, mounted by the last lines of `api.py` under `/v4` (werkzeug `DispatcherMiddleware`), so the v3.1 application, its url map and its checks (`checkUp`, `checkParams`) are untouched. For each data request it refuses a key in the web address (400), answers 503 without the usage login, checks the key (401), answers 503 while `V4_OPEN` is False unless the key is an admin key, refuses unknown parameters (400, v4 names, `didYouMean` also for old and Darwin Core names: `collectionCode`, `catalogNumber`, `eventID`, `fieldNumber`, `locationID`, `code`), counts the request (429), then runs the v3.1 route itself in an inner request (`application.test_request_context` and `full_dispatch_request`, let in by `flask.g.apiV4Inner`, which only this code sets (never by `up=1`), with `limit` = the records reserved for this answer, `flask.g.apiMaxLimit` raising or lowering the v3.1 cap in `setGlobals()`), renames the fields, adds `localityCode`, rewrites `metaData.parameters` and `metaData.request` to the v4 request, and gives back the records it did not use.
- `api/v3.1/api_keys.py`: keys, levels, the 429 and 401 texts and the counters (`UsageStore`).
- Two small hooks in `api.py`, inert outside v4: `setGlobals()` reads `g.apiMaxLimit`, and `getSpecimen()` calls `api_v4.specimen_filters()` (`g.apiV4Filters`).
- `V4_OPEN` in `api.py` is the one switch, read at start from the server's settings file (next section). While False (the default): v4 answers admin keys only (everyone else 503 "not open yet", `Retry-After: 86400`), `/v4/` says `"status": "closed"`, and v3.1 is as described above (503 without `up=1`; `up=1` works, the staging tests use it). v4's inner requests never use `up=1` (`g.apiV4Inner`), in either state.
- Caddy: `@api_v4 path /v4 /v4/*` with `handle` (not `handle_path`: the API sees `/v4/...`), in `docker/caddy/Caddyfile` and `Caddyfile.common`. Caddy writes request headers into its access log, so both files drop `X-Api-Key` from the log (`format filter`, `request>headers>X-Api-Key delete`; checked with Caddy 2.10: without the filter the header is logged). They also drop the `token` value from the logged address (`request>uri query { delete token }`), so the one-time links apiKeyConfirm.do?token=... of the registration emails never reach the log (checked with Caddy 2.10: the rest of the address is kept).
- Cloudflare: not changed by code. `/v4/` must be excepted from Under Attack Mode and covered by the API rate rule in the Cloudflare console before v4 opens (Brian's yes first; the steps are in [runbook.md](../runbook.md), "Open API v4, end v3.1").

### The switch: v4 opens, v3.1 ends (2026-11-09)

Decided by Brian Fisher 2026-11-09: when v4 opens, v3.1 ends. There is no period in which v3.1 still answers data, and no admin switch afterwards.

- **One switch, on the server, no code change:** the line `v4Open=true` in `etc/api-db.properties` (the API's own settings file, mounted in the api container), then `docker restart antweb_api_1`. `api.readV4Open()` reads it at start (`true`, `1`, `yes`, `on`, in any case; anything else or no line: closed). The container log says `API v4 open: True` or `False`. The environment variable `API_V4_OPEN`, when set, wins (development and tests; not set on the servers). Rollback: remove the line and restart; v3.1 is then closed as before (503, `up=1` works again). Staging first.
- **Closed (the default):** exactly as before this change.
- **Open:**
  - v4 answers everyone at their level's limits (table below); `/v4/` says `"status": "open"`.
  - Every v3.1 data route answers **HTTP 410 Gone** with a short JSON signpost (`apiEnded()`): `error` ("The AntWeb API v3.1 has ended. Please use the AntWeb API v4 at https://www.antweb.org/v4/ with a free API key from https://www.antweb.org/apiKey.do."), `status` "ended", `v4`, `getAKey`, `documentation`, `terms`, `guideForPrograms`, `citation`, `license`. With or without `up=1`, a key or other parameters; no `Retry-After` (it does not come back), `Cache-Control: no-store` (so no cache keeps the 410 after a rollback), the licence `Link` header as on every reply. No database is asked.
  - **`/v3.1/` itself stays HTTP 200** (uptime checks and old clients get a readable answer), with `status` "ended", the same message and the `v4` block (`status` "open"). An unknown route stays a JSON 404.
  - `up=1` is ignored (`down()`); the earlier plan of `up=1` with an admin key (`upSwitch()`) is removed.
- **The site's pages do not read the switch.** `apiV3.1.do` (section "Version 4", `#v4`) and `/llms.txt` are written to be right in both states and point to the live status at `/v4/`.
- **Tests in both states.** The end-to-end tests ask the API as `/v3.1/<route>?...&up=1`. `tests/e2e/antweb.py` (`ApiSession`, every `new_session()`) asks `/v3.1/` once whether v4 is open; if so it sends such requests to `/v4/<route>` without `up`, with the test key in `X-API-Key`, and waits out a 429 for the minute. Requests without `up=1` are never changed. The key is an admin key of staging from the GitHub Actions secret `ANTWEB_TEST_API_KEY` (passed for staging only, `.github/workflows/e2e.yaml`), made on staging by `python3 /root/antweb-next/tools/api/create-test-key.py`: it prints the key once, stores only its SHA-256 (in `api_key` and in `/root/staging-refresh/api-test-key.json`, root only), and gives the key its own limits (600 requests a minute, 200,000 requests and 10,000,000 records a day; 500 records an answer, the cap the tests pin). The weekly refresh from production puts it back (`--restore`, `tools/staging/refresh-from-prod.sh`). The script removes only the test key itself (by its name or its own hash), never a key by its first characters, checks first that no other key has those characters (they are unique in the table) and changes nothing if one has; the delete and the insert are one transaction. Without the key or with a refused key, API tests are skipped and `test_api.py::test_the_tests_can_reach_the_api` fails with what to do. `tests/smoke/prod_smoke.py` reads the state from `/v3.1/` and expects 503 (closed) or `/v4/` open and 410 (open); it sends no key.

### Keys

- Sent only in the header `X-API-Key`. A key in the web address (a parameter named key, apiKey, api_key, X-API-Key, token, or any value that looks like `aw_...`) is refused with HTTP 400 "Send your API key in the X-API-Key header, never in the web address ...", without repeating it.
- Format `aw_` and random URL-safe characters: 32 for keys from the registration page (`ApiKeys.newKey()`, 192 bits), 40 from `api_keys.new_key()` (240 bits); the API accepts 20 to 80 (`api_keys.KEY_RE`). Stored only as SHA-256 in `api_key.key_hash`, with a display prefix (first 7 characters) in `api_key.prefix`. Accepted only with `status = 'active'`.
- Unknown, malformed, pending or revoked key: HTTP 401 `{"error": "This API key is not valid. Get a free key at https://www.antweb.org/apiKey.do."}`.
- Never written to a log, an error or a reply: the inner v3.1 request carries no key header, nothing prints request headers, errors print the exception type and message only, and Caddy drops the header from its log (above). `apiKey.do` (the registration form) is PR 4.

### Levels and limits

| | No key (per visitor IP) | Registered key | Collaborator, admin |
|---|---|---|---|
| Requests a minute | 5 | 60 | per key (`api_key.per_minute`) |
| Requests a day | 50 | 5,000 | per key (`per_day`) |
| Records per answer | 25 | 500 | per key (`max_records`, at most 10,000) |
| Records a day | 1,250 (50 answers of 25) | 50,000 | per key (`records_per_day`) |

A key's own columns override the defaults for every level; NULL means the registered value. Days and minutes are UTC (a day ends at midnight UTC, 4 PM Pacific in winter). Without a key: 1,250 records a day per address (50 answers of 25; confirmed by Brian Fisher 2026-11-09), and the API day starts again at midnight UTC (`api_keys.utcnow()`, `seconds_to_next_day()`; `test_no_key_day_is_1250_records_and_ends_at_midnight_utc`).

- **No "give me everything":** before the query runs, the answer's records (the smaller of the level's records per answer, the records left today and the caller's `limit`) are reserved on the day's row; after it, the unused part is given back. Paging with `offset` therefore stops at the daily record limit, also with requests running at the same time in several workers. When the reply is cut, `metaData.limitNotice` and the header `X-AntWeb-Limit-Notice` say so ("Answers with your key hold at most 500 records, and 50,000 records a day. ..."). Every v4 data reply has `metaData.level` and `metaData.recordsLeftToday` and the headers `X-AntWeb-Level` and `X-AntWeb-Records-Left-Today`.
- **Over a limit:** HTTP 429, `Retry-After` in seconds (to the next minute, or to midnight UTC), and JSON `{"error", "retryAfter", "getAKey", "contact", "citation"}`. The text: "You have reached the limit for visitors without a key: 5 requests a minute. Please wait 42 seconds. For higher limits, get a free API key at https://www.antweb.org/apiKey.do. Larger projects: write to antweb@antweb.org. Please cite AntWeb: <citation>". For a key it says "your key" and leaves out the sentence about getting a free key (they have one); waits of 2 hours or more add "(about N hours)". The limit names are "requests a minute", "requests a day" and "records a day".
- **Without the usage login** (`usageUser=`, `usagePassword=` in `etc/api-db.properties`, made by `tools/api/setup-usage-login.sh`): every v4 data route answers 503 "The AntWeb API v4 is not available: API keys are not configured on this server ..." and `/v4/` says `"status": "not configured"`. If the counter database cannot be reached, 503 "The request could not be counted, so it was not answered" (`Retry-After: 60`). v4 never answers without limits.

### Counting (shared by the three gunicorn workers)

In MySQL, written only with the usage login `antweb_api_usage` (the data login stays read-only):

- `api_rate` (new, `db/upgrade/9.4.0/2026-11-08-api-rate.sql`): requests in the current minute per key (`key:<id>`) or visitor (`ip:<hash>`), and requests and records per day of visitors without a key. Rows older than two days are deleted (each worker at most every ten minutes).
- `api_usage_daily`: requests, records and refusals (429) per key and day; `key_id` 0 = all visitors without a key together.
- `api_key.last_used`: set at most once a minute per key.

A request is counted by adding to its rows inside one transaction and reading them back (the rows stay locked until the transaction ends, so two workers cannot both take the last request of a limit); a refused request is rolled back and counted only as a refusal. The usage login's connections use READ COMMITTED, and a transaction that hits a deadlock or a lock wait timeout is tried again up to 4 times (`api_keys.retried()`): a check against real MySQL 8.0 (2026-11-08) showed that two workers adding the first request of a new minute at the same moment could deadlock under the default REPEATABLE READ. With both, 5 rounds of 40 simultaneous requests from two stores gave exact counts and no errors. Visitors without a key are told apart by `CF-Connecting-IP` (else the last `X-Forwarded-For` entry, else the TCP peer, as `util/HttpUtil.getClientIpAddress`), for IPv6 by the /64 network (`api_keys.visitor_network()`: one household or server usually holds a whole /64; an IPv4 address written as `::ffff:a.b.c.d` counts as the IPv4 address; IPv4 unchanged), stored only as SHA-256 of the IP or network with a salt that changes every day (an HMAC of the date with the usage login's password), never as the IP.

Setup on a server (staging first): `bash /root/antweb-next/tools/api/setup-usage-login.sh` then `docker restart antweb_api_1` (staging), the same with `/root/antweb/` on production. The script is safe to run again: on a server where PR 2 already made the login, it adds `api_rate` and its grant. On every run it first revokes all of the login's privileges and then grants exactly (in one session): on `api_key` select of `id, key_hash, level, status, per_minute, per_day, max_records, records_per_day, last_used` (the key lookup and the WHERE of its own `last_used` update) and update of `last_used` only (it cannot read names or emails); select, insert, update on `api_usage_daily`; select, insert, update, delete on `api_rate`. So a re-run on a server where PR 2 granted select, insert, update on all of `api_key` narrows it. When the script writes a new password it also sets it on the MySQL login (`alter user`); when the file has one already, that one is set (and the login made again if it was dropped), so the file and MySQL always agree. The self-check logs in as the usage login with its password in an option file on stdin (`--defaults-extra-file=/dev/stdin`), never on a command line. Checked against MySQL 8.0.46 (2026-11-08): three runs, with a broader grant and a changed password in between and with the login dropped, end with the same three grants, and `UsageStore` (lookup, counters, `last_used`, cleanup) works with them. Rollback: `drop table api_rate;` (v4 then answers 503) and the PR 2 rollback for the rest.

### Field names

| v4 | v3.1 | Darwin Core | Meaning |
|---|---|---|---|
| `specimenCode` | `specimenCode` (in `/taxaImages` specimens: `code`) | `catalogNumber` | the code of one specimen, e.g. CASENT0922626 |
| `collectingEventCode` | `collectionCode` | `eventID`, `fieldNumber` | the collecting event, what AntWeb calls the collection code, e.g. JTL7095 |
| `localityCode` | (not given) | `locationID` | the locality as coded by the collector |

`collectionCode` is renamed wherever it appears in a v4 reply (JSON and `ndjson=true`). `localityCode` is added to every specimen record of `/v4/specimens` and `/v4/geoSpecimens` (read from `specimen.localitycode`: the `api3_1_specimen` view has no locality code). `/v4/specimens` also takes `collectingEventCode=` and `localityCode=` as filters. v4 does not take `up` or `log`. Everything else (parameters, filters, other fields, order, paging) is as in v3.1 above, with the totals and `afterCode` of the next section added.

### Countries and islands in v4 (2026-11-11)

Brian Fisher, 2026-11-11: "An oceanic island or archipelago with its own ant fauna is its own country in AntWeb. Its specimens never count toward the political parent." This reverses the rule of 2026-11-10 (an island filed under a country was part of it: Hawaii under United States).

- **The rule in the API is the stored country and nothing else.** `country=` (and `geolocaleName=` of a country) compares `specimen.country` by the whole name (`countryIs()`); neither `api.py` nor `api_v4.py` reads the island column. `country=United States` returns no record of Hawaii, in the records and in `metaData.total`; `country=Hawaii` returns Hawaii's. The same for Ecuador and Galapagos Islands. A record with no country is in no country search.
- **It depends on the stored data.** The upload files an island's record under the island since the island work (pull request 345, in master since 2026-11-12). The specimens stored before that are moved once per server by `tools/place/island_countries.py --apply` (runbook, "Islands that are their own country"; staging: done 2026-11-11). Until that step is done on a server, a specimen of Hawaii is stored there with country United States and is returned for `country=United States`. So v4 is switched on (next section) only on a server where the move is done.
- **Where it is said:** the API page (`#v4-countries` in the section "Version 4"), `/llms.txt`, and the v4 front door (`/v4/`, `countries`: `api_v4.COUNTRY`).
- **Ants only, as the data extract:** next section. **Not in v4:** `metaData.total` is given on the first page and with `total=true`, not on every page (below). Records with no country are in no country search and are not counted in an API answer (the data extract's manifest counts them, and its page can give them in a file of their own).
- Tests: `tests/unit/test_api_paging.py` (`test_an_island_is_its_own_country`, `test_the_country_filter_never_reads_the_island_column`, `test_front_door_and_documentation_say_an_island_is_its_own_country`); on a server `tests/e2e/test_api.py::test_an_island_is_not_part_of_its_political_parent` (skips until the stored specimens are moved).

### Ants only by default in v4 (2026-11-12)

Brian Fisher's definition of 2026-11-10: "Country search: every ant specimen record whose country in AntWeb is the country", the same definition as the data extract (his question about the first Nearctic file: "why did you include non ants for an ant study?"). AntWeb also holds beetles, wasps, spiders and other animals found with ants (production: 1,289 of the 200,075 specimens of United States, Canada and Mexico).

- **Where: every route that reads specimens and takes a place filter** (`api_v4.FAMILY_ROUTES`):

| Route | What follows the rule |
|---|---|
| `/v4/specimens` | the records and `metaData.total` |
| `/v4/geoSpecimens` | the records, `metaData.total`, and the name lists with `distinct=` |
| `/v4/geoSpeciesStats` | the specimens counted by caste for each species of the place (a species with no ant specimen is not listed) |
| `/v4/distinctTaxa` | the subfamilies, genera and species named by the specimens (of a country, a habitat, a date range) |
| `/v4/measurements` | the specimens whose measurements are given (it filters by country, adm1 and bioregion). A measurement whose code has no specimen record stays: it has no family, and as before no place |

- **Where not.** `/v4/images` and `/v4/taxaImages` (images by specimen code or name; no place filter), and the routes that give names and lists from other tables: `/v4/taxa`, `/v4/geolocales`, `/v4/geolocaleTaxa` and `/v4/unimagedGeolocaleTaxa` (the species list of a place, `geolocale_taxon`), `/v4/bioregions`, `/v4/bioregionTaxa`. They do not take `allFamilies` (HTTP 400, unknown parameter) and say nothing about families.
- **One definition, one helper.** The test is the extract's own, not one of the API's: `(s.family = 'formicidae' or s.family is null or s.family = '')` (`tools/export/specimen_extract.py` `ANTS`, `DataExtract.ANTS`): family Formicidae, or no family given (those records are kept, as in the extract). It is written once, `api_v4.ANTS`, and applied by one function, `api_v4.ants_only(query, specimen)`, which each of the five routes calls on its one query: a SQLAlchemy query with the class `Specimen` (the view passes `specimen.family` through), or for `/geoSpeciesStats`, whose SQL is text, the WHERE text with the alias `s`. The records, totals and lists of a route come from that one query, so they cannot differ. `tests/unit/test_api_paging.py` compares the text with the extract's and checks that exactly the five routes call the helper and that `api.py` has no family test of its own; a new route that filters specimens by country fails that test until it is on the list.
- **Asking for more.** `allFamilies=true` (the extract's name: `--all-families`, the form's `allFamilies`): every family. On `/v4/specimens`, `family=<name>` (v3.1's own filter, the whole family name): that one family, whatever `allFamilies` says; `family=formicidae` gives the records that say Formicidae, without those that have no family given. A blank `family=` and `family=*` name no family: they are as if the parameter was not sent, so the ants-only default stays and the blank value is not handed to v3.1's filter (`family_asked()`; review of pull request 348, 2026-11-12: a blank `family=` turned the default off). `allFamilies` must be `true` or `false`, and `family=all` is refused (HTTP 400, saying `allFamilies=true`): it would match no family and give an empty answer. Both are refused before the request is counted.
- **The answer says which.** `metaData.families`: `antsOnly`, `allFamilies` or `family`; the header `X-AntWeb-Families` on JSON and `ndjson=true` answers. The front door explains it (`/v4/`, `families`: `api_v4.FAMILIES`).
- **How.** `api_v4.run()` works out the rule from the request (`family_rule()`) and hands it to the route through `flask.g.apiV4Families`; `ants_only()` adds the condition only inside a v4 request with the rule `antsOnly`.
- **v3.1 is not changed**: no family condition in its SQL, every family in its answers, `allFamilies` is an unknown parameter there. A v4 request with an admin key while v4 is closed is v4 (ants only).
- **Known differences from the extract:** a specimen whose name has no taxon row is in the extract (marked `no_taxon_row`) and in no API answer (the finding below); `family=formicidae` gives only the records that say Formicidae, while "ants only" also keeps the records with no family given.
- **The end-to-end tests** write their API requests as v3.1 requests and compare them with the site's numbers, which count every specimen. When v4 is open their session sends a request to one of the five routes to v4 with `allFamilies=true` (`tests/e2e/antweb.py`, `V4_ANTS_ONLY_ROUTES`), so they mean the same in both states.
- Tests: `tests/unit/test_api_paging.py` (`test_ants_only_is_the_default_and_the_answer_says_so`, `test_all_families_gives_the_others_and_family_gives_one`, `test_geo_specimens_are_ants_only_too_with_their_distinct_lists`, `test_distinct_taxa_geo_species_stats_and_measurements_follow_the_same_rule`, `test_one_definition_and_one_helper_for_every_route`, `test_other_routes_have_no_family_rule`, `test_bad_family_values_are_400_before_anything_is_counted`, `test_v31_answers_for_every_family_as_before`, `test_the_ants_only_condition_is_the_data_extracts`, `test_front_door_and_documentation_say_ants_only`); `tests/unit/test_e2e_api_access.py`; on staging `tests/e2e/test_api.py::test_v4_specimens_are_ants_only_unless_asked` (needs the test key; finds a record of another family by asking for one, no code is written into the test).

### Complete sets: totals and afterCode (2026-11-10)

Why: a collaborator's extract of March 2026 was incomplete (Stenamma: 14 of 42 species) and nothing in the API's answers showed it. `metaData.count` is the number of records on the page; nothing said how many records matched. v4 only; v3.1 is unchanged while v4 is closed (no new metaData, no new parameters, the same SQL).

**What the answers say.** `/v4/specimens`, `/v4/geoSpecimens` (records and the `distinct` lists) and `/v4/taxa`:

| metaData | Meaning |
|---|---|
| `total` | the number of records that match the filters, whatever `limit` is. On the first page (no `offset` or `offset=0`, no `afterCode`) and on any page with `total=true`; `total=false` leaves it out. Later pages leave it out because a count reads every matching row. |
| `returned` | the number of records in this answer |
| `complete` | `true` when this answer reaches the end of the set |
| `nextAfterCode` | `/specimens` and `/geoSpecimens` records only: the specimen code of the last record of this answer; absent when `complete` is true |

```json
"metaData": {"total": 48211, "returned": 10000, "complete": false, "nextAfterCode": "CASENT0191574",
             "count": 10000, "limit": 10000, "offset": 0, "level": "key", "recordsLeftToday": 190000, ...}
```

With `ndjson=true` (no metaData) the same values are in the headers `X-AntWeb-Total`, `X-AntWeb-Returned`, `X-AntWeb-Complete` and `X-AntWeb-Next-After-Code`; JSON answers have the headers too. The header values are URL-encoded, so the code can be put into `afterCode=` as it is.

**`afterCode=<specimen code>`** (`/v4/specimens`, `/v4/geoSpecimens`): the records whose specimen code is greater than the given one, in the fixed order of these routes (specimen code), `limit` as before. The code need not be a record (it is a place in the order); an empty `afterCode` is the start. `offset` stays; `afterCode` together with `offset` is HTTP 400 ("Use afterCode or offset, not both. ..."), as is `afterCode` with `distinct` and a `total` that is not `true` or `false`. These 400s come before the request is counted. v4's unknown-parameter answer lists `afterCode` and `total` for these routes and suggests `afterCode` for `after`, `cursor` and near spellings. `/v4/taxa` has `total`, not `afterCode`.

Why `afterCode` and not `offset` for a large set: `offset=1000` means "leave out the first 1000 records of the set as it is now". If a record before that place is added or removed between two requests (an upload, the daily update), every later record moves by one, and the next page repeats a record or skips one. `afterCode` names the last record received, so the next page starts after it whatever happened before it. A record added or removed ahead of the walk is in the set, or not, as it is when the walk gets there. It is also cheaper for the database on deep pages: `offset` reads and throws away the records it leaves out, `afterCode` can start at the code in the specimen table's primary key.

**How a collaborator pulls a large set** (for example every specimen of three countries; a collaborator key with `max_records` 10,000 and `records_per_day` raised on apiKeys.do; nothing in the limits changed with this work):

1. One country at a time (`country=` takes one name; an island that is its own country, Hawaii for example, is a request of its own and is not in its political parent's set). The set is of ants only; add `&allFamilies=true` to every request of the walk for the other families too. First request: `GET /v4/specimens?country=Madagascar&limit=10000` with the key in `X-API-Key`. Note `metaData.total`.
2. While `metaData.complete` is false: the same request with `&afterCode=<metaData.nextAfterCode>` (URL-encode the code).
3. At the end, the number of records received equals the `total` of the first request. If it does not, records were added or removed while the set was fetched (compare the specimen codes, or fetch again after the daily update); it is no longer a silent gap.
4. `metaData.recordsLeftToday` and HTTP 429 with `Retry-After` say when the day's records are used up. A page cut by the day's limit is short, says `"complete": false` and gives `nextAfterCode`; go on from that code the next day (days are UTC).

**Limits are unchanged.** A total is not a record: the records of the day are counted as before (the records in the answer). One answer holds at most 10,000 records (`api_keys.ABSOLUTE_MAX_RECORDS`), whatever a key's `max_records` says; collaborator and admin keys take their four limits from their `api_key` columns (table above).

**Code.** `api_v4.rows(query, limit, offset, key, after_code)` replaces `query.all()` in `getSpecimen`, `getGeoSpecimens` (twice) and `getTaxa`; outside a v4 inner request its first lines return `query.all()`. In v4 it takes the route's finished query (filters, order, limit, offset) and
- counts from that same query without its order, limit and offset (`count(specimen_code)`, `count(taxon_name)`, or a counted subquery for the distinct lists), so the count and the records cannot use different filters (`test_total_and_records_come_from_one_filtered_query` compares the two WHERE clauses);
- adds `specimen_code > :afterCode` as a bound parameter;
- reads `limit + 1` records and gives `limit`: whether there is one more is `complete`, on every page, without a count. The extra record is not given and not counted.
It hands the values to `api_v4.run()` through `flask.g.apiV4Paging`; `page_info()` writes them into metaData and the headers. `afterCode` and `total` reach the query through `flask.g.apiV4Filters` (`EXTRA_PARAMS`), so they are not v3.1 parameters. The count runs under the API's 90-second statement limit like every read; if it fails the request answers HTTP 500, not an answer without its total.

**Order and comparison agree.** `afterCode` is only right if `specimen_code > X` means the same as "comes after X in ORDER BY specimen_code". Both use the one column `api3_1_specimen.specimen_code` (= `specimen.code`, the specimen table's primary key). MySQL compares a column with a value sent by the program in the column's collation (the column's collation has precedence over a literal's), and sorts the column in the same collation, so the two agree whatever the collation is; and because `code` is the primary key, no two specimens are equal in that collation, so "greater than the last code" leaves out exactly the records already given. Checked 2026-11-10 with the API's own code against MariaDB 10.11 (MySQL's rules) with mixed-case codes, spaces, hyphens, underscores and accented letters under utf8mb4_general_ci, utf8mb4_unicode_ci, utf8mb4_bin, utf8mb3_general_ci, utf8mb3_unicode_ci, latin1_swedish_ci and latin1_bin: the `afterCode` walk equalled `ORDER BY` of the whole set for every page size. Not checked: production's MySQL 8.4 itself (no server access in that session). To see the column's collation: `show full columns from specimen like 'code'`.

**Not included (decisions open).** `/images` and `/taxaImages`: their `limit` and `offset` count images while their records are specimens and taxa, so a total would be in another unit than the list, and one specimen's images can fall on two pages. `/measurements`, `/distinctTaxa`, `/geolocales`, `/geolocaleTaxa`, `/bioregions`, `/bioregionTaxa`: the same `rows()` call would give them `total`, `returned` and `complete`; not done here.

**No route answers plain text with HTTP 200 any more** (2026-11-10). Two v3.1 routes did for a missing or unknown input, which v4 could not read as JSON and answered with HTTP 500. Both are fixed in the route, so v3.1 and v4 answer alike (one code path), and no records are used:
- `/unimagedGeolocaleTaxa` with an unknown place: HTTP 404 `{"error": "Geolocale not found: <name>"}`, the answer `/geoSpeciesStats` gives (it was the text "Must enter valid geolocale"); without `geolocaleName`: HTTP 400 "The geolocaleName parameter is required: the name of a place, for example geolocaleName=Madagascar."
- `/geoSpecimens` without `coords`: HTTP 400, next paragraph.
- Also found by the same reading of the routes: `nan` and `inf` as numbers (`coords`, `radius`, `bbox`) passed Python's `float()` and were refused by the MySQL driver (HTTP 500); now HTTP 400 (`numbers()`, `getGeoSpecimens`).
- Read and found safe: every `getGeolocale()` call has its "not found" branch (`/specimens` and `addExtraGeolocaleListCriteria`: no specimens; the two routes above: 404); `/geolocaleTaxa` and `/bioregionTaxa` read `.geolocale`, `.bioregion` and `.taxon` of rows that are inner-joined to those tables, so they are never missing, and an unknown place or name gives an empty list; `/measurements` checks its specimen for None.
- Tests: `test_unimaged_geolocale_taxa_unknown_place_is_404_as_geo_species_stats`, `test_no_route_answers_plain_text_with_200` (no route returns a bare string; every v4 route answers 200, 400 or 404 without parameters and with unknown places, names and codes), `test_nan_and_inf_are_refused_not_500`.

**`/geoSpecimens` without `coords` is HTTP 400** (2026-11-10), in v3.1 and v4 (one code path): `{"error": "The coords parameter is required: the latitude and longitude of the centre of the area, for example coords=37.77,-122.47 (radius is in kilometres, default 5)."}`. It was the plain text "Must enter coords for a geoSpecimens request" with HTTP 200 in v3.1, and so HTTP 500 in v4. No records are used. `test_geo_specimens_without_coords_is_400_naming_the_parameter`.

**The limit notice names `afterCode`** (2026-11-10): when an answer of specimen records (`/v4/specimens`, `/v4/geoSpecimens` without `distinct`) is cut by a limit, `metaData.limitNotice` and `X-AntWeb-Limit-Notice` end "This answer holds 500; to continue, send afterCode=<the last specimenCode of this answer> (offset= also works)." Every other list keeps "ask for the next ones with offset=." (`api_v4.limit_notice()`, `test_limit_notice_of_a_cut_page_names_after_code_for_specimens`).

### Finding: specimens whose name has no taxon row are never served (2026-11-10, decision open)

The view `api3_1_specimen` (`db/upgrade/8.77/2022-06-19.sql`; `db/upgrade/8.110` did not change it) is `specimen s join taxon t on t.taxon_name = s.taxon_name`: an INNER JOIN. A specimen whose `taxon_name` has no row in `taxon` is in no answer of `/specimens`, `/geoSpecimens`, `/distinctTaxa`, and `/measurements` gives its measurements without the specimen's taxon and place. `metaData.total` counts the view, so it does not show these specimens either. Nothing was changed. `/taxaImages` (view `taxon_image`) has the same join.

How many there are is not known from the repository. On production (read-only):

```
F=/root/antweb/etc/db.properties; docker exec -e MYSQL_PWD="$(sed -n 's/^password=//p' "$F")" antweb_mysql_1 mysql -u "$(sed -n 's/^user=//p' "$F")" ant -e "select count(*) specimens, count(distinct s.taxon_name) names from specimen s left join taxon t on t.taxon_name = s.taxon_name where t.taxon_name is null; select s.genus, count(*) specimens, count(distinct s.taxon_name) names from specimen s left join taxon t on t.taxon_name = s.taxon_name where t.taxon_name is null group by s.genus order by specimens desc limit 30; show create view api3_1_specimen\G"
```

(The last statement shows the view as it is on production, which may differ from the repository's last definition.) If the first number is 0 there is nothing to decide. The second list shows whether Stenamma is among them, that is, whether this join is what cut the March 2026 extract.

What a LEFT JOIN (`specimen s left join taxon t ...`) would change:

- **More records:** those specimens appear in `/specimens`, `/geoSpecimens`, `/distinctTaxa` (their names and counts) and with their taxon and place in `/measurements`; every count and `total` of these routes rises by them. Tests that compare API counts with the site's numbers (`tests/e2e/test_api_numbers.py`, numbers.md #100 to #118) would need checking against how the site counts these specimens.
- **Two fields are empty for them:** the view takes only `fossil` and `status` (`taxon_status`) from `taxon`; both would be `null`. A program that expects `true`/`false` and a status word would meet `null`.
- **Filters on those two fields leave them out, as now:** `status=valid`, `fossil=true` and `fossil=false` do not match `null` (so `fossil=false` would not mean "everything that is not a fossil"). `/measurements` `taxonStatus=` likewise. `geolocaleName=` joins `taxon` itself and still leaves them out.
- **Speed:** for filters on specimen columns (country, genus, code) the plan is the same (specimen first, taxon by its key). For `status=` or `fossil=` MySQL turns the LEFT JOIN back into an inner join (the filter rejects `null`), so those are as now.
- **How:** one upgrade script that redefines the view (staging first; rollback is the present definition, saved from `show create view` before the change). `tests/unit` uses a table of the same name, so only an end-to-end test on staging can pin it.

The other way to close the gap is at the source: a specimen without a taxon row is a data fault (the upload or a name update left it behind), so a data check that lists them (and a repair that makes the missing taxon rows, or a report to the curator) fixes the site and the API at once, and the view can stay. Both can be done. Decision for Brian Fisher.

### Front doors

- `/v4/` (always 200): name "Antweb API v4", status (open, closed, not configured), message, getAKey, keyHeader `X-API-Key`, limits (the table above), routes, fieldNames (the table above), paging (total, returned, complete, afterCode and the ndjson headers, in a sentence each; `api_v4.PAGING`), countries (`api_v4.COUNTRY`), families (ants only by default; `api_v4.FAMILIES`), documentation, terms, guideForPrograms, terms_of_use, contact, citation, license.
- `/v3.1/` (always 200) adds `v4` (status, url, getAKey, message); its own `status` is "closed", or "ended" once v4 is open.

### Tests

| Test | What it pins |
|---|---|
| `tests/unit/test_api_v4.py` (CI, in the API image; SQLite for the data and for the counters) | key format and SHA-256, only the hash stored; admin key works before `V4_OPEN`, everyone else 503; 401 for unknown, malformed, pending, revoked keys; key in the address 400; no usage login 503, counter database down 503; no key 5 a minute (429 text and `Retry-After`), 50 a day, 25 records; registered 60 a minute and the defaults; per-key overrides (any level, 10,000 cap); the daily record limit stops offset paging and unused records are given back; counts in `api_usage_daily` and `api_rate`, shared by two stores (workers), no raw IP stored; IP hash per day; the key never in stdout, stderr, bodies or headers (also on 400, 401, 429 and 500); v4 field names in JSON, ndjson and /taxaImages, localityCode, the two new filters; v4 parameter names and hints; every v3.1 data route has a v4 route; both front doors in both states; v3.1 unchanged while v4 is closed (`up=1` as before); the switch (`v4Open=` read from the settings file, default closed, a fresh start in each state); once open: every v3.1 data route 410 with the signpost fields, `up=1` and keys without effect, no key lookup, every v4 route answers, `/v3.1/` 200 "ended"; the no-key day (1,250 records, ends at midnight UTC); v4 never uses `up=1`; IPv6 visitors counted per /64; both Caddyfiles route `/v4` and drop `X-Api-Key` and the `token` query value from the log |
| `tests/unit/test_api_paging.py` (CI, in the API image; SQLite) | complete sets: `total` is the filtered count whatever the limit, for every kind of filter (also the v4 filters and the `geolocaleName` join), and comes from the WHERE clause of the data query; `total` only on the first page or with `total=true`; `returned`, `complete` and `nextAfterCode` on the first, a middle and the last page (also a last page that is exactly full); the `afterCode` walk gives every record once for every page size, also when a record is added or removed between pages (where the `offset` walk repeats or skips one); `afterCode` is a bound value compared with the ORDER BY column; `afterCode` with `offset` or `distinct` and a bad `total` are 400 and not counted; the new names in the unknown-parameter answer; `/geoSpecimens` and `/taxa`; the ndjson headers; ants only by default on the five routes that read specimens by place (`/specimens`, `/geoSpecimens`, `/geoSpeciesStats`, `/distinctTaxa`, `/measurements`: records, totals, name lists, counts), by one definition and one helper, `allFamilies=true` and `family=`, `metaData.families`, the condition equal to the data extract's, v3.1 for every family as before; a total is not a record and a page cut by the day's limit says where to go on; v3.1 unchanged while v4 is closed (metaData, headers, SQL, parameters); the front door's `paging` block; the API page and llms.txt name both |
| `tests/unit/test_api_usage_login_script.py` | the script grants only the three key tables (on `api_key` only the lookup columns and an update of `last_used`), delete only on `api_rate`, revokes broader grants first on every run, sets the file's password on the login in both branches, never puts a password on a command line, and adds the grants also for an existing login |
| `tests/e2e/test_api.py::test_v31_has_ended_when_v4_is_open`, `test_the_tests_can_reach_the_api` | on a server where v4 is open: every v3.1 data route 410 with the signpost, `/v3.1/` "ended", `/v4/` "open" with every route; the suite's own way in (up=1, or the test key) works |
| `tests/unit/test_e2e_api_access.py`, `tests/unit/test_api_test_key_script.py` | the end-to-end session in both states (address rewritten only for `up=1` requests, key only in the header and only from the environment, skipped without it), the secret passed for staging only, no key in the repository; the test key script (staging only, only the hash kept, one admin key with its own limits, put back by the weekly refresh) |
| `tests/unit/test_prod_smoke_logic.py` | the production smoke run passes in both states and fails when they disagree (v4 open but v3.1 not 410, or `/v4/` not answering) |
| `tests/e2e/test_api.py::test_v4_front_door_and_refusals` | on staging: `/v4/` answers JSON, a data route without a key answers 503 (closed or not configured) or 429, a key in the address is refused; skipped when Cloudflare challenges `/v4/` |

## Free API keys: registration and the admin page (step 6, PRs 4 and 5, 2026-11-08)

Decided by Brian Fisher: keys are free and issued automatically, with a copy of every registration to
bfisher@calacademy.org; the admin page gives him the overview.

- **apiKey.do** (`ApiKeyAction`, `web/apiKey-body.jsp`): public form, no AntWeb account needed. Name, email,
  project description (required), institution or affiliation (optional), "I agree to the terms"
  (apiV3.1.do#terms), Cloudflare Turnstile, CSRF token. Limits: 5 registrations an hour per IP address (in
  memory), 3 a day per email address, 100 confirmed registrations a day in all and at most 300 pending
  (unconfirmed) ones made in a day (both counted in `api_key`, last 24 hours), so a flood of unconfirmed
  registrations stops at its own ceiling without using up the day's 100. A registration is saved as `pending` with
  the SHA-256 of a random link token (24 hours); the registrant gets the link apiKeyConfirm.do?token=... (the email
  starts "Hello," and does not repeat the name typed on the form, so the form cannot carry text to any address),
  Brian a plain-text copy with a link to apiKeys.do?id=N (Reply-To the registrant). The copies keep their own count
  (`Emailer.sendNoticeOwnLimit`, not the 20-a-day notice budget the upload emails use): at most 20 a day (UTC), then
  one email "AntWeb API key registrations: more today" pointing to apiKeys.do, then none until the next day (the
  count restarts with Tomcat). Until `etc/api-keys.properties` on the server has `apiKeys.open=true` **and** the
  human check is configured (`etc/turnstile.properties` with siteKey and secret; `ApiKeys.registrationOpen()`; open
  without Turnstile is logged once) the page says "Registration opens soon" and shows no form, so it can go live
  before the API checks keys (PR 3). Caddy drops the `token` value from its access log (PR 3 review changes).
- **apiKeyConfirm.do** (`ApiKeyConfirmAction`): opening the link only shows a button (email scanners open links
  first). The button makes the key, `aw_` + 32 random URL-safe characters (SecureRandom), stores only its SHA-256
  (`key_hash`) and first 7 characters (`prefix`), sets `active` and `confirmed`, and shows the key once with how
  to send it (header `X-API-Key`, with a curl example against `https://www.antweb.org/v4/specimens` and a link to
  `/v4/`). Used, expired and cancelled links say so and show no key.
- **apiKeys.do** (`curate.ApiKeysAction`, admins; linked from the Curate page's Manager tools): one row per key,
  newest first, with use from `api_usage_daily` (today, 7 and 30 days, records and refusals in 30 days) and a
  first row for requests without a key (`key_id` 0). Filters by status, level, name/email/institution; CSV
  download (same filters). One key's page (`?id=N`): revoke, reactivate, level (registered, collaborator,
  admin), the four limits (blank = the default; 0 is refused with "use Revoke to block a key", since the API reads
  0 as the default; records an answer at most 10,000, the API's own ceiling `api_keys.ABSOLUTE_MAX_RECORDS`), note;
  each change needs the confirmation box.
- **Times are UTC** everywhere for API keys: the API writes `api_usage_daily.day` and `last_used` in UTC, and
  `home/ApiKeyDb` writes `created`, `confirmed`, `confirm_expires` and `revoked` with `utc_timestamp()` and compares
  with `utc_timestamp()`/`utc_date()` (the servers' MySQL clock is Pacific). The admin page, its CSV and the copy
  email label times "(UTC)"; "today" is the UTC day (it ends at 4 PM Pacific in winter).
- Keys and tokens are never logged or emailed. admin.log gets the registration number and, once active, the
  prefix. While pending, `prefix` and `key_hash` hold random stand-ins (`p_...`) because both columns are
  required and unique.
- Code: `util/ApiKeys` (rules, no database), `util/ApiKeyMgr` (register, confirm, email texts), `home/ApiKeyDb`
  (tables; `ensureSchema()` at startup from LoginMgr). Tests: `test/.../security/ApiKeysTest.java`,
  `test/.../home/ApiKeyDbTest.java` (MySQL 8.4 in CI), `tests/e2e/test_api_keys.py`.
