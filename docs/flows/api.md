# API v3.1

Read from the code on master at commit e81a8945 (2026-10-22): `api/v3.1/api.py` (Flask 3, SQLAlchemy 1.4, gunicorn), container `antweb_api_1`, served by Caddy at `https://www.antweb.org/v3.1/` (Cloudflare's challenge is off for this path). The public description is the page `apiV3.1.do` (also `api.do`; `web/documentation/api/apiV3_1-body.jsp`) and [../API.md](../API.md). The API's requests are not in the Tomcat log, so there is no traffic figure here.

## In plain words

The API gives the same data as the site as JSON, for scripts: specimens, names, images, places and their species lists, measurements. Users are researchers and other databases (GBIF-style harvesters, R and Python packages, AntMaps-type sites). It only reads: on the servers it logs in to MySQL as a read-only user (`etc/api-db.properties`, mounted as `/antweb-etc/api-db.properties`). On production and staging the API does not start without that file (`API_REQUIRE_READONLY_LOGIN: "true"` in `docker-compose.yml`, decided by Brian Fisher 2026-10-27; the container log says `ERROR: no read-only login` and the container restarts until the file is there). On a development machine (`docker-compose.dev.yml` sets `"false"`) it prints a warning and uses the main account.

Rules common to every route:

- **`up=1` is required.** Without it every data route answers HTTP 503 with `{"error": "The AntWeb API is closed while we add free registered API keys. It will reopen in the coming weeks; see antweb.org for news.", "documentation": ..., "terms": ...}` (`checkUp()` before every request, `apiDown()`; decided by Brian Fisher 2026-10-27, it was HTTP 200 with an empty body; wording changed 2026-11-02 so callers see why and that it is not an outage). `/v3.1/` itself stays HTTP 200 (the uptime check and the container health check use it). The documentation's examples add `up=1` for admins. Once `V4_OPEN` is True, `up=1` also needs an active admin key in the `X-API-Key` header (see "The v3.1 admin switch once v4 opens" below).
- **Date and elevation filters include their bounds** (`boundsFilter()`, `/specimens` and `/distinctTaxa`; decided by Brian Fisher 2026-10-27, they were exclusive): `minDate`/`maxDate` compare the collection start date, `maxDate=1979-12-31` includes the whole of that day; `minElevation`/`maxElevation` compare `minimumElevationInMeters`, so `minElevation=1200` includes specimens at 1200 m.
- **`/geoSpeciesStats` subcaste counts count only within their caste** (decided by Brian Fisher 2026-10-27, as the ergatoids since B30): `queenAlateDealateCount` and `queenBrachypterousCount` count queens, `workerMajorSoldierCount` and `workerNormalCount` workers, `maleAlateCount` males. Before, a specimen of another caste with the same subcaste name was counted too.
- **At most 500 records per request** (since 2026-10-21, until registered API keys; it was 10,000). `limit=` asks for fewer; without `limit` a request gets 500. Above the cap, the reply has 500 records, `metaData.limitNotice` and the header `X-AntWeb-Limit-Notice` ("Requests are limited to 500 records while AntWeb moves to registered API keys. For larger data needs contact antweb@antweb.org."). The cap is `maxLimit` in `etc/api-db.properties` (restart the container after a change). `offset=` fetches the next records; only routes with a fixed order page reliably (below).
- **Unknown parameters are refused:** HTTP 400 with `error`, `unknownParameters`, `acceptedParameters`, `documentation` and, when a name is close, `didYouMean` (`/specimens?code=` -> `specimenCode`). Since 2026-10-21 (#224); before, a misspelt filter was ignored.
- **`country=` is the whole country name, in any case** (`countryIs()`), on every route that has it: `country=Niger` finds Niger, not Nigeria. Since 2026-10-27 (F24); before, /specimens, /distinctTaxa and /measurements matched any country containing the text.
- `ndjson=true`: one JSON line per record (with an index line before each), no metaData; the limit notice is in the header.
- **Licence** (since 2026-10-29, CC BY 4.0): every reply has the header `Link: <https://creativecommons.org/licenses/by/4.0/>; rel="license"` (`addLicenseHeader`); every JSON data reply has a top-level `license` object (name, url, citation; `LICENSE` in api.py, the site's text in `util/License.java`). Error replies stay `{"error": ...}`. `tests/unit/test_api_license.py`.
- Errors: a database or code error answers HTTP 500 `{"error": "The request could not be completed. Please try again later or simplify the query."}` (details only in the container log); an unknown route `{"error": "Not Found"}` with 404.
- Reply: `{"license": {...}, "metaData": {"parameters": [...], "request": "...", "limit": N, "offset": N, "count": N, ...}, "<list>": [...]}`. Some routes have no `limit`/`offset` in metaData or a different count name (table below).

## Routes

| Route | Parameters (besides up, limit, offset, ndjson, log) | Returns (list key; metaData count) | Reads | Order (offset paging) |
|---|---|---|---|---|
| `/v3.1/` | none | JSON front door (2026-11-08): name "Antweb API V3.1", status, message, documentation, terms (`apiV3.1.do#terms`), guideForPrograms (`/llms.txt`), contact, citation, v4 (status open or closed, url, getAKey; since PR 3) | - | - |
| `/specimens` | family, subfamily, genus, species, specimenCode, region, subregion, country, adm1, bioregion, habitat, type, georeferenced, bbox, minDate, maxDate, minElevation, maxElevation, museum, ownedby, locatedat, collectedby, caste, subcaste, fossil, validGenus, validSubfamily, status, hasImage, geolocaleName, lifeStageSex | `specimens`: specimenCode, occurrenceId, institution and collection codes, classification (kingdom to subspecies, scientificName), type, region to adm1, bioregion, decimalLatitude/Longitude, geo, dates, habitat, microhabitat, collectedBy, samplingMethod, lifeStageSex, medium, determinedBy, locality, notes, minimumElevationInMeters, antwebTaxonName, ownedBy, locatedAt, caste, subcaste, fossil, status, imageCount; `count`, `queryTime` | view [`api3_1_specimen`](../data-model.md#api3_1_specimen) (+ `taxon`, `geolocale_taxon`, `geolocale` for geolocaleName) | specimen code: yes |
| `/geoSpecimens` | coords (lat,lon; required), radius (kilometres, default 5; converted to a box in degrees by `radiusBox()` since 2026-10-26, was added as degrees), distinct (subfamily, genus, species) | `specimens` (as /specimens); with distinct: `subfamilies`, `genera` or `species` ("Genus species") names of the specimens in the area; another distinct value: HTTP 400; `count` | `api3_1_specimen` | specimen code; distinct lists by name: yes |
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
| `/unimagedGeolocaleTaxa` | geolocaleName, byCaste (1), taxarank, status | `unimagedGeolocaleTaxaDict`: taxonName (and castes); `unimagedGeolocaleTaxaCount`, `unimagedGeolocaleTaxaCasteCount` | `geolocale_taxon`, `taxon`, `specimen`, `image` (a taxon is imaged when a specimen has an image that is not a label, 2026-10-21) | taxon name: yes |

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
- `V4_OPEN = False` in `api.py`. While False: v4 answers admin keys only (everyone else 503 "not open yet", `Retry-After: 86400`), `/v4/` says `"status": "closed"`, and the v3.1 503 keeps its message. When Brian decides keys go live: set `V4_OPEN = True` (one line, through a PR). Then v4 is open at its limits and the v3.1 503 says "The AntWeb API v3.1 is closed. Please use the AntWeb API v4 at https://www.antweb.org/v4/ with a free API key from https://www.antweb.org/apiKey.do." (`apiDownMessage()`). Answering v3.1 data for 3 months with a pointer to v4 is a later change.
- **The v3.1 admin switch once v4 opens** (`down()`, `upSwitch()`, `checkUp()` in `api.py`): while `V4_OPEN` is False, `up=1` works exactly as before (the staging tests use it). Once it is True, `up=1` opens v3.1 only with an active admin key in `X-API-Key`, looked up with the v4 usage login: without a key or with a registered or collaborator key 503 (the v3.1 message), with an unknown, malformed, pending or revoked key 401, without the usage login or when the counter database cannot be reached 503. The answer is decided once per request (`g.apiUpSwitch`). v4's inner requests do not use `up=1` at all (`g.apiV4Inner`).
- Caddy: `@api_v4 path /v4 /v4/*` with `handle` (not `handle_path`: the API sees `/v4/...`), in `docker/caddy/Caddyfile` and `Caddyfile.common`. Caddy writes request headers into its access log, so both files drop `X-Api-Key` from the log (`format filter`, `request>headers>X-Api-Key delete`; checked with Caddy 2.10: without the filter the header is logged). They also drop the `token` value from the logged address (`request>uri query { delete token }`), so the one-time links apiKeyConfirm.do?token=... of the registration emails never reach the log (checked with Caddy 2.10: the rest of the address is kept).
- Cloudflare: not changed in this PR. `/v4/` is not yet excepted from Under Attack Mode or covered by the `/v3.1/` rate rule; that must be done in the Cloudflare console before v4 opens (Brian's yes first).

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

A key's own columns override the defaults for every level; NULL means the registered value. Days and minutes are UTC (a day ends at midnight UTC, 4 PM Pacific in winter). The no-key level's 1,250 records a day is not one of Brian's numbers: it is what 50 answers of 25 can hold, stated so paging cannot exceed it.

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

`collectionCode` is renamed wherever it appears in a v4 reply (JSON and `ndjson=true`). `localityCode` is added to every specimen record of `/v4/specimens` and `/v4/geoSpecimens` (read from `specimen.localitycode`: the `api3_1_specimen` view has no locality code). `/v4/specimens` also takes `collectingEventCode=` and `localityCode=` as filters. v4 does not take `up` or `log`. Everything else (parameters, filters, other fields, order, paging) is as in v3.1 above.

### Front doors

- `/v4/` (always 200): name "Antweb API v4", status (open, closed, not configured), message, getAKey, keyHeader `X-API-Key`, limits (the table above), routes, fieldNames (the table above), documentation, terms, guideForPrograms, terms_of_use, contact, citation, license.
- `/v3.1/` adds `v4` (status, url, getAKey, message).

### Tests

| Test | What it pins |
|---|---|
| `tests/unit/test_api_v4.py` (CI, in the API image; SQLite for the data and for the counters) | key format and SHA-256, only the hash stored; admin key works before `V4_OPEN`, everyone else 503; 401 for unknown, malformed, pending, revoked keys; key in the address 400; no usage login 503, counter database down 503; no key 5 a minute (429 text and `Retry-After`), 50 a day, 25 records; registered 60 a minute and the defaults; per-key overrides (any level, 10,000 cap); the daily record limit stops offset paging and unused records are given back; counts in `api_usage_daily` and `api_rate`, shared by two stores (workers), no raw IP stored; IP hash per day; the key never in stdout, stderr, bodies or headers (also on 400, 401, 429 and 500); v4 field names in JSON, ndjson and /taxaImages, localityCode, the two new filters; v4 parameter names and hints; every v3.1 data route has a v4 route; both front doors; v3.1 unchanged; the v3.1 admin switch (`up=1` as before while `V4_OPEN` is False; once True only with an admin key, 401 for a bad key, 503 otherwise and without the usage login); v4 never uses `up=1`; IPv6 visitors counted per /64; both Caddyfiles route `/v4` and drop `X-Api-Key` and the `token` query value from the log |
| `tests/unit/test_api_usage_login_script.py` | the script grants only the three key tables (on `api_key` only the lookup columns and an update of `last_used`), delete only on `api_rate`, revokes broader grants first on every run, sets the file's password on the login in both branches, never puts a password on a command line, and adds the grants also for an existing login |
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
