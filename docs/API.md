# AntWeb API Documentation

## Overview

AntWeb provides a RESTful API (version 3) for programmatic access to ant specimen, taxonomy, image, and geographic data. The API is built with Python/Flask and provides JSON responses.

**Base URL:** `https://api.antweb.org/v3/`  
**Format:** JSON  
**Authentication:** Not required for read operations

## Technology Stack

- **Framework:** Flask + Flask-RESTful
- **Database Access:** SQLAlchemy ORM
- **Python Version:** 3.6+
- **Database:** MySQL 5.x (shared with main application)
- **Response Format:** JSON

## API Endpoints

### Specimens

#### GET /specimens

Retrieve specimen records with optional filtering.

**Query Parameters:**
- `genus` - Filter by genus name
- `species` - Filter by species epithet  
- `subspecies` - Filter by subspecies epithet
- `country` - Filter by country
- `bbox` - Bounding box `min_lat,min_lon,max_lat,max_lon`
- `georeferenced` - Only georeferenced specimens (`true`/`false`)
- `type_status` - Filter by type status (e.g., "holotype", "paratype")
- `limit` - Maximum records to return (default: 100, max: 1000)
- `offset` - Pagination offset

**Example Requests:**
```bash
# All Camponotus specimens
GET /specimens?genus=camponotus&limit=10

# Specimens from California
GET /specimens?country=United%20States&adm1=California

# Georeferenced specimens in bounding box
GET /specimens?bbox=34.0,-120.0,37.0,-117.0&georeferenced=true

# Type specimens
GET /specimens?type_status=holotype
```

**Response Format:**
```json
{
  "specimens": [
    {
      "code": "CASENT0106322",
      "taxon_name": "gauromyrmex acanthinus",
      "subfamily": "myrmicinae",
      "genus": "gauromyrmex",
      "species": "acanthinus",
      "caste": "worker",
      "country": "malaysia",
      "adm1": "sabah",
      "decimal_latitude": 4.96478,
      "decimal_longitude": 117.80465,
      "elevation": "190 m",
      "collected_by": "Ward, P. S.",
      "date_collected": "2010-08-20",
      "image_count": 4,
      "url": "https://www.antweb.org/specimen/CASENT0106322"
    }
  ],
  "count": 1,
  "limit": 10,
  "offset": 0
}
```

#### GET /specimens/{code}

Retrieve a single specimen by code.

**Example:**
```bash
GET /specimens/CASENT0106322
```

**Response:**
```json
{
  "code": "CASENT0106322",
  "taxon_name": "gauromyrmex acanthinus",
  "subfamily": "myrmicinae",
  "genus": "gauromyrmex",
  "species": "acanthinus",
  "subspecies": null,
  "life_stage": "adult",
  "caste": "worker",
  "medium": "pin",
  "type_status": null,
  "determined_by": "Ward, P. S.",
  "date_determined": "2013-09-01",
  "collected_by": "Ward, P. S.",
  "date_collected": "2010-08-20",
  "method": "at light",
  "habitat": "rainforest edge",
  "microhabitat": "at light",
  "locality_name": "Danum Valley Field Centre",
  "country": "malaysia",
  "adm1": "sabah",
  "decimal_latitude": 4.96478,
  "decimal_longitude": 117.80465,
  "lat_lon_max_error": "3 m",
  "elevation": "190 m",
  "biogeographic_region": "indomalaya",
  "owned_by": "UCDC",
  "located_at": "UCDC",
  "specimen_notes": "Ant AToL voucher",
  "images": [
    {
      "shot_type": "h",
      "shot_number": 1,
      "url": "https://www.antweb.org/images/casent0106322/casent0106322_h_1_high.jpg"
    }
  ],
  "url": "https://www.antweb.org/specimen/CASENT0106322"
}
```

### Taxa

#### GET /taxa

Retrieve taxonomic records.

**Query Parameters:**
- `rank` - Taxonomic rank (`subfamily`, `genus`, `species`, `subspecies`)
- `status` - Status (`valid`, `synonym`, `homonym`)
- `subfamily` - Filter by subfamily
- `genus` - Filter by genus
- `species` - Filter by species epithet
- `fossil` - Include only fossils (`true`) or exclude (`false`)
- `limit` - Maximum records (default: 100, max: 1000)
- `offset` - Pagination offset

**Example Requests:**
```bash
# All valid genera in Formicinae
GET /taxa?subfamily=formicinae&rank=genus&status=valid

# All Camponotus species
GET /taxa?genus=camponotus&rank=species

# Fossil taxa
GET /taxa?fossil=true&limit=50
```

**Response:**
```json
{
  "taxa": [
    {
      "taxon_name": "camponotus pennsylvanicus",
      "status": "valid",
      "subfamily": "formicinae",
      "genus": "camponotus",
      "species": "pennsylvanicus",
      "author_date": "(De Geer, 1773)",
      "rank": "species",
      "fossil": false,
      "specimen_count": 1234,
      "image_count": 56,
      "url": "https://www.antweb.org/description.do?taxonName=camponotus%20pennsylvanicus"
    }
  ],
  "count": 1,
  "limit": 100,
  "offset": 0
}
```

#### GET /taxa/{taxon_name}

Retrieve details for a specific taxon.

**Example:**
```bash
GET /taxa/camponotus%20pennsylvanicus
```

**Response:**
```json
{
  "taxon_name": "camponotus pennsylvanicus",
  "status": "valid",
  "subfamily": "formicinae",
  "genus": "camponotus",
  "species": "pennsylvanicus",
  "author_date": "(De Geer, 1773)",
  "rank": "species",
  "fossil": false,
  "parent_taxon_name": "camponotus",
  "valid_name": null,
  "specimen_count": 1234,
  "image_count": 56,
  "countries": ["united states", "canada"],
  "bioregions": ["nearctic"],
  "url": "https://www.antweb.org/description.do?taxonName=camponotus%20pennsylvanicus"
}
```

### Images

#### GET /images

Retrieve image records.

**Query Parameters:**
- `specimen_code` - Filter by specimen code
- `taxon_name` - Filter by taxon name
- `shot_type` - Shot type (`h`, `d`, `p`, `l`)
- `artist` - Photographer name
- `has_tiff` - Has TIFF original (`true`/`false`)
- `upload_date_start` - Upload date range start (YYYY-MM-DD)
- `upload_date_end` - Upload date range end (YYYY-MM-DD)
- `limit` - Maximum records (default: 100, max: 500)
- `offset` - Pagination offset

**Shot Types:**
- `h` - Head view
- `d` - Dorsal view
- `p` - Profile view
- `l` - Label

**Example Requests:**
```bash
# Images for a specimen
GET /images?specimen_code=CASENT0106322

# Head shots by a photographer
GET /images?shot_type=h&artist=April%20Nobile

# Recent uploads
GET /images?upload_date_start=2024-01-01&limit=20
```

**Response:**
```json
{
  "images": [
    {
      "image_id": 12345,
      "specimen_code": "CASENT0106322",
      "taxon_name": "gauromyrmex acanthinus",
      "shot_type": "h",
      "shot_number": 1,
      "artist": "April Nobile",
      "copyright": "California Academy of Sciences",
      "upload_date": "2014-05-20",
      "has_tiff": true,
      "urls": {
        "thumbnail": "https://www.antweb.org/images/casent0106322/casent0106322_h_1_low.jpg",
        "medium": "https://www.antweb.org/images/casent0106322/casent0106322_h_1_med.jpg",
        "high": "https://www.antweb.org/images/casent0106322/casent0106322_h_1_high.jpg",
        "tiff": "https://www.antweb.org/images/casent0106322/casent0106322_h_1.tif"
      }
    }
  ],
  "count": 1,
  "limit": 100,
  "offset": 0
}
```

### Geolocales

#### GET /geolocales

Retrieve geographic location records.

**Query Parameters:**
- `name` - Location name (partial match)
- `type` - Type (`country`, `adm1`, `adm2`)
- `iso_code` - ISO country code
- `bioregion` - Biogeographic region
- `limit` - Maximum records (default: 100, max: 500)
- `offset` - Pagination offset

**Example Requests:**
```bash
# All countries
GET /geolocales?type=country

# States/provinces in United States
GET /geolocales?name=united%20states&type=adm1

# Locations in Neotropical region
GET /geolocales?bioregion=neotropical
```

**Response:**
```json
{
  "geolocales": [
    {
      "geolocale_id": 123,
      "name": "california",
      "type": "adm1",
      "parent": "united states",
      "iso_code": "US-CA",
      "bioregion": "nearctic",
      "centroid_latitude": 37.0,
      "centroid_longitude": -120.0,
      "bounds": {
        "north": 42.0,
        "south": 32.5,
        "east": -114.1,
        "west": -124.4
      },
      "species_count": 450,
      "endemic_species_count": 12,
      "specimen_count": 15678
    }
  ],
  "count": 1,
  "limit": 100,
  "offset": 0
}
```

#### GET /geolocales/{geolocale_id}/taxa

Get taxa found in a specific location.

**Query Parameters:**
- `rank` - Filter by rank
- `endemic` - Only endemic taxa (`true`)
- `introduced` - Only introduced taxa (`true`)
- `limit` - Maximum records

**Example:**
```bash
GET /geolocales/123/taxa?rank=species&endemic=true
```

## Response Codes

### Success Codes

- **200 OK** - Request successful
- **201 Created** - Resource created (future write operations)

### Error Codes

- **400 Bad Request** - Invalid parameters
- **404 Not Found** - Resource not found
- **429 Too Many Requests** - Rate limit exceeded
- **500 Internal Server Error** - Server error

### Error Response Format

```json
{
  "error": {
    "code": 400,
    "message": "Invalid parameter: limit must be <= 1000",
    "details": "Parameter 'limit' exceeded maximum allowed value"
  }
}
```

## Rate Limiting

**Limits:**
- 1000 requests per hour per IP address
- 10 requests per second burst

**Headers:**
```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 995
X-RateLimit-Reset: 1609459200
```

**Exceeding Limits:**
- Returns 429 status code
- Response includes Retry-After header

## Pagination

**Parameters:**
- `limit` - Records per page (default: 100)
- `offset` - Starting position (default: 0)

**Response Metadata:**
```json
{
  "count": 50,
  "total": 5000,
  "limit": 100,
  "offset": 0,
  "next": "/specimens?genus=camponotus&offset=100&limit=100",
  "previous": null
}
```

## Filtering and Search

### Text Search

Use `q` parameter for free-text search:

```bash
GET /specimens?q=california+oak+woodland
GET /taxa?q=carpenter+ant
```

### Geographic Filters

**Bounding Box:**
```bash
GET /specimens?bbox=34.0,-120.0,37.0,-117.0
```

**Circle (future):**
```bash
GET /specimens?lat=34.5&lon=-118.5&radius=50km
```

### Date Ranges

```bash
GET /specimens?date_collected_start=2020-01-01&date_collected_end=2020-12-31
GET /images?upload_date_start=2024-01-01
```

## Data Export

### CSV Export

Add `format=csv` to any endpoint:

```bash
GET /specimens?genus=camponotus&format=csv
```

**Response:**
```
code,taxon_name,genus,species,country,latitude,longitude
CASENT0001,camponotus pennsylvanicus,camponotus,pennsylvanicus,united states,40.5,-75.2
...
```

### JSON Lines (future)

For large exports:
```bash
GET /specimens?format=jsonl&limit=10000
```

## CORS Support

API supports CORS for browser-based applications.

**Headers:**
```
Access-Control-Allow-Origin: *
Access-Control-Allow-Methods: GET, OPTIONS
Access-Control-Allow-Headers: Content-Type
```

## API Versioning

**Current Version:** v3  
**Deprecated:** v2.1 (read-only, will be removed 2027-01-01)

**Version Header:**
```
X-API-Version: 3.0
```

## Code Examples

### Python

```python
import requests

# Get all Camponotus specimens
response = requests.get(
    'https://api.antweb.org/v3/specimens',
    params={'genus': 'camponotus', 'limit': 10}
)

specimens = response.json()['specimens']

for specimen in specimens:
    print(f"{specimen['code']}: {specimen['taxon_name']}")
```

### JavaScript

```javascript
// Fetch specimens from California
fetch('https://api.antweb.org/v3/specimens?country=United%20States&adm1=California&limit=10')
  .then(response => response.json())
  .then(data => {
    data.specimens.forEach(specimen => {
      console.log(`${specimen.code}: ${specimen.taxon_name}`);
    });
  });
```

### R

```r
library(httr)
library(jsonlite)

# Get all valid Formicinae genera
response <- GET("https://api.antweb.org/v3/taxa",
                query = list(
                  subfamily = "formicinae",
                  rank = "genus",
                  status = "valid"
                ))

data <- fromJSON(content(response, "text"))
genera <- data$taxa$taxon_name
print(genera)
```

### cURL

```bash
# Get specimen details
curl "https://api.antweb.org/v3/specimens/CASENT0106322"

# Search with multiple parameters
curl "https://api.antweb.org/v3/specimens?genus=camponotus&country=united+states&limit=5"

# Get images for a taxon
curl "https://api.antweb.org/v3/images?taxon_name=camponotus+pennsylvanicus&shot_type=h"
```

## Advanced Queries

### Complex Filters

Combine multiple parameters:

```bash
# Valid Camponotus species from California with images
GET /taxa?genus=camponotus&rank=species&status=valid&has_images=true

# Type specimens from a specific collector
GET /specimens?collected_by=Ward,%20P.%20S.&type_status=holotype

# Recent georeferenced specimens in tropics
GET /specimens?georeferenced=true&bbox=-23.5,-180,23.5,180&date_collected_start=2020-01-01
```

### Aggregations (future)

```bash
# Count specimens by country
GET /specimens/aggregate?group_by=country

# Average elevation by genus
GET /specimens/aggregate?group_by=genus&function=avg&field=elevation
```

## Best Practices

### Efficient Queries

**DO:**
- Use pagination for large result sets
- Apply specific filters to reduce result size
- Cache responses on client side
- Use batch requests when possible

**DON'T:**
- Request all records without pagination
- Make repeated identical requests
- Request fields you don't need (future: field selection)

### Error Handling

Always check response status and handle errors:

```python
response = requests.get('https://api.antweb.org/v3/specimens/INVALID123')

if response.status_code == 200:
    specimen = response.json()
elif response.status_code == 404:
    print("Specimen not found")
else:
    print(f"Error: {response.status_code}")
```

### Rate Limiting

Implement exponential backoff when hitting rate limits:

```python
import time

def get_with_retry(url, max_retries=3):
    for attempt in range(max_retries):
        response = requests.get(url)
        if response.status_code == 429:
            wait = int(response.headers.get('Retry-After', 60))
            time.sleep(wait)
            continue
        return response
    raise Exception("Max retries exceeded")
```

## Future Enhancements

### Planned Features (2026+)

- **GraphQL API** - Flexible querying
- **Webhook Support** - Real-time updates
- **Batch Operations** - Multiple resources in one request
- **Field Selection** - Request only needed fields
- **Aggregation Endpoints** - Built-in analytics
- **Write Operations** - POST/PUT for data submission (authenticated)
- **Async Export** - Long-running export jobs
- **Spatial Queries** - Advanced GIS operations

### API v4 (TBD)

Considering these improvements:
- RESTful resource nesting
- HATEOAS links
- JSON:API compliance
- OAuth2 authentication
- WebSocket streaming

## Support

**Issues:** https://github.com/calacademy-research/antweb/issues  
**Email:** antweb@calacademy.org  
**Documentation:** https://api.antweb.org/v3/docs

## License

API data is provided under Creative Commons licenses (varies by content).
Check individual record `license` field for specific terms.
