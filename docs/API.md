# AntWeb API

AntWeb's public API is **version 3.1**. It is read-only, needs no login and answers in JSON
(or newline-delimited JSON with `ndjson=true`).

**Base URL:** `https://www.antweb.org/v3.1/`
**Full documentation, with every parameter and example requests:** https://www.antweb.org/apiV3.1.do

The older versions are retired and no longer served: v3 (`api.antweb.org/v3/`, its code removed
from this repository on 2026-10-19) and v2.

## Endpoints

| Endpoint | Returns |
|---|---|
| `/specimens` | Specimen records (filter by taxon, `specimenCode`, place, `bbox`, dates, elevation, caste, type status, museum and more) |
| `/geoSpecimens` | Specimens within `radius` km of `coords` |
| `/geoSpeciesStats` | Species statistics for a geolocale (`geolocaleName`) |
| `/taxa` | Taxa (filter by rank, status, fossil and so on) |
| `/distinctTaxa` | Distinct taxa of the specimens matching the filters |
| `/images` | Specimen images and their URLs |
| `/taxaImages` | Images grouped by taxon |
| `/measurements` | Specimen measurements |
| `/geolocales` | Regions, subregions, countries and adm1 (states, provinces) |
| `/geolocaleTaxa` | Taxa recorded in a geolocale |
| `/unimagedGeolocaleTaxa` | Taxa of a geolocale that have no images |
| `/bioregions` | Bioregions |
| `/bioregionTaxa` | Taxa recorded in a bioregion |

The routes are defined in `api/v3.1/api.py`. An unknown parameter is refused with a message that
suggests the nearest valid name (`tests/unit/test_api_params.py`).

## Paging and limits

`limit` and `offset` page through large results. Since 2026-10-21 every endpoint returns at
most **500 records per request** (also with `ndjson=true`), until registered API keys arrive; it
was 10,000. Without `limit` a request gets up to 500. A request that asks for more gets 500, plus
this notice in the reply's `metaData` as `limitNotice` and in the `X-AntWeb-Limit-Notice` HTTP
header (ndjson replies have no metadata, only the header):

> Requests are limited to 500 records while AntWeb moves to registered API keys. For larger data
> needs contact antweb@antweb.org.

The notice is also given when no `limit` was asked for and the reply is full (500 records: there
may be more). `/geolocales`, `/bioregions` and `/geoSpeciesStats` used to ignore `limit` and
return everything; they now page like the others. Their counts (`geolocaleCount`, `bioregionCount`, `count`) are
for the page returned, not the total.

`/specimens` and `/geoSpecimens` are sorted by specimen code, so pages follow on without gaps or
overlap (unless specimens are uploaded between requests). The other record endpoints have no
fixed sort order (as before); when paging through them, de-duplicate on the record's name or id. Cloudflare also limits each address to 15
requests per 10 seconds on `/v3.1/`.

The cap is a server setting, not code: `maxLimit=<n>` in `etc/api-db.properties` (the API's own
settings file on the server, not in git), then `docker restart antweb_api_1`. Missing or invalid:
500. The API prints `API record limit per request: <n>` in its log at start.

Example:

```bash
curl "https://www.antweb.org/v3.1/specimens?genus=acanthognathus&limit=100&offset=200"
curl "https://www.antweb.org/v3.1/specimens?specimenCode=casent0922626"
curl "https://www.antweb.org/v3.1/specimens?caste=queen&genus=Tetramorium&limit=10&ndjson=true"
```

## Where it runs

The API is a Flask application in its own container (`antweb_api_1`, `docker/api/Dockerfile`),
reading the AntWeb database with a read-only login (`etc/api-db.properties` on the server).
Caddy sends `/v3.1/` requests on www.antweb.org to it; Cloudflare's challenge is switched off for
those paths so scripts can call the API.
