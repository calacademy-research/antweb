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

`limit` (default and maximum 10,000 records per request) and `offset` page through large
results. Example:

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
