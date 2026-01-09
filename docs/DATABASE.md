# AntWeb Database Schema Documentation

## Overview

AntWeb uses MySQL 5.x as its primary relational database. The database schema is complex with 50+ tables managing taxonomic data, specimen records, images, geographic information, and user management.

**Database Name:** `ant`  
**Character Set:** UTF-8 (utf8mb4)  
**Collation:** utf8mb4_unicode_ci

## Core Entity-Relationship Model

```
┌──────────────┐
│    taxon     │ ← Central taxonomic authority
└──────┬───────┘
       │
       ├─────┬─────────┬──────────┬────────────┐
       │     │         │          │            │
       ▼     ▼         ▼          ▼            ▼
┌──────────┐ ┌────────┐ ┌─────────┐ ┌──────────┐ ┌──────────┐
│ specimen │ │proj_taxon│geolocale│image      │ description│
└──────────┘ └────────┘ │_taxon   │           │_edit      │
                        └─────────┘ └──────────┘ └──────────┘
```

## Primary Tables

### 1. taxon

**Purpose:** Central taxonomic authority - stores all ant taxa (subfamilies, tribes, genera, species, subspecies)

**Key Fields:**
```sql
taxon_name         VARCHAR(128)  PRIMARY KEY    # e.g., "formicinae", "camponotus", "camponotus pennsylvanicus"
status             VARCHAR(32)                   # "valid", "synonym", "homonym", "unavailable"
subfamily          VARCHAR(128)                  # Subfamily name
genus              VARCHAR(128)                  # Genus name  
species            VARCHAR(128)                  # Species epithet
subspecies         VARCHAR(128)                  # Subspecies epithet (if any)
author_date        VARCHAR(128)                  # "(Smith, 1858)"
taxon_type         VARCHAR(32)                   # "subfamily", "genus", "species", "subspecies"
fossil             TINYINT(1)                    # 0 = extant, 1 = fossil
current_valid_name VARCHAR(128)                  # If synonym, points to valid name
parent_taxon_name  VARCHAR(128)                  # Hierarchical parent
rank               VARCHAR(32)                   # Taxonomic rank
created            DATETIME                      # Record creation timestamp
modified           DATETIME                      # Last modification timestamp
```

**Relationships:**
- Self-referential: `parent_taxon_name` → `taxon_name` (builds hierarchy)
- Referenced by: `specimen`, `proj_taxon`, `geolocale_taxon`, `image`

**Indexes:**
- PRIMARY KEY (`taxon_name`)
- INDEX (`status`)
- INDEX (`subfamily`, `genus`, `species`)
- INDEX (`fossil`)
- FULLTEXT (`taxon_name`, `author_date`)

**Record Count:** ~50,000 taxa

### 2. specimen

**Purpose:** Individual specimen records from museum collections

**Key Fields:**
```sql
code               VARCHAR(20)   PRIMARY KEY    # e.g., "CASENT0106322"
taxon_name         VARCHAR(128)  FK → taxon    # Scientific name
subfamily          VARCHAR(128)                 # Denormalized from taxon
genus              VARCHAR(128)                 # Denormalized from taxon
species            VARCHAR(128)                 # Species epithet
subspecies         VARCHAR(128)                 # Subspecies epithet
life_stage         VARCHAR(32)                  # "worker", "queen", "male"
caste              VARCHAR(32)                  # "worker", "soldier", etc.
medium             VARCHAR(32)                  # "pin", "alcohol", "point"
type_status        VARCHAR(64)                  # "holotype", "paratype", etc.
determined_by      VARCHAR(128)                 # Who identified it
date_determined    DATE                         # When identified
collection_code    VARCHAR(64)                  # Collection event code
collected_by       VARCHAR(255)                 # Collector name(s)
date_collected     DATE                         # Collection date
method             VARCHAR(255)                 # Collection method
habitat            VARCHAR(255)                 # Habitat description
microhabitat       VARCHAR(255)                 # Microhabitat
locality_name      VARCHAR(255)                 # Location name
country            VARCHAR(64)                  # Country
adm1               VARCHAR(128)                 # State/province/region
adm2               VARCHAR(128)                 # County/district
decimal_latitude   DECIMAL(10, 6)               # GPS latitude
decimal_longitude  DECIMAL(10, 6)               # GPS longitude
lat_lon_max_error  VARCHAR(64)                  # Coordinate uncertainty
elevation          VARCHAR(64)                  # Elevation (with units)
biogeographic_region VARCHAR(64)                # Bioregion
owned_by           VARCHAR(64)                  # Collection owner
located_at         VARCHAR(64)                  # Physical location
specimen_notes     TEXT                         # Free-form notes
dna_notes          TEXT                         # DNA/genetics notes
access_group       INT                          # Access control group
created            DATETIME
modified           DATETIME
```

**Relationships:**
- `taxon_name` → `taxon.taxon_name`
- Referenced by: `image` (specimen images)

**Indexes:**
- PRIMARY KEY (`code`)
- INDEX (`taxon_name`)
- INDEX (`country`, `adm1`)
- INDEX (`access_group`)
- INDEX (`collected_by`)
- SPATIAL INDEX on lat/lon (if using spatial features)

**Record Count:** ~500,000+ specimens

**Upload Format:** 42-column tab-delimited file (see specimens.txt format below)

### 3. image

**Purpose:** Image metadata and file locations

**Key Fields:**
```sql
image_id           INT           PRIMARY KEY AUTO_INCREMENT
shot_number        INT                          # Shot number for specimen
image_of_id        VARCHAR(128)                 # specimen.code OR taxon.taxon_name
specimen_code      VARCHAR(20)   FK → specimen # If image of specimen
taxon_name         VARCHAR(128)  FK → taxon    # If image of taxon
shot_type          VARCHAR(32)                  # "h" (head), "d" (dorsal), "p" (profile), "l" (label)
artist             VARCHAR(128)                 # Photographer
copyright          VARCHAR(255)                 # Copyright holder
has_tiff           TINYINT(1)                   # Has TIFF original
upload_date        DATE                         # When uploaded
image_type         VARCHAR(32)                  # File type
source_id          INT                          # Source/provenance
medium_resolution  VARCHAR(255)                 # Medium res file path
low_resolution     VARCHAR(255)                 # Thumbnail file path  
high_resolution    VARCHAR(255)                 # High res file path
tiff_resolution    VARCHAR(255)                 # TIFF file path (if exists)
access_group       INT                          # Access control
created            DATETIME
modified           DATETIME
```

**Image Naming Convention:**
```
{specimen_code}_{shot_type}_{shot_number}.jpg

Examples:
CASENT0106322_h_1.jpg     # Head shot
CASENT0106322_d_1.jpg     # Dorsal shot
CASENT0106322_p_1.jpg     # Profile shot
CASENT0106322_l_1.jpg     # Label shot
```

**Relationships:**
- `specimen_code` → `specimen.code`
- `taxon_name` → `taxon.taxon_name`
- `artist` → `artist.name`

**Indexes:**
- PRIMARY KEY (`image_id`)
- UNIQUE (`specimen_code`, `shot_type`, `shot_number`)
- INDEX (`taxon_name`)
- INDEX (`upload_date`)
- INDEX (`artist`)

**Record Count:** ~200,000+ images

### 4. geolocale

**Purpose:** Geographic locations (countries, states/provinces, regions)

**Key Fields:**
```sql
geolocale_id       INT           PRIMARY KEY
name               VARCHAR(128)                 # Location name
parent_id          INT           FK → self     # Parent location
geolocale_type     VARCHAR(32)                  # "country", "adm1", "adm2"
iso_code           VARCHAR(8)                   # ISO country code
biogeographic_region VARCHAR(64)                # Bioregion
centroid_latitude  DECIMAL(10, 6)               # Center point
centroid_longitude DECIMAL(10, 6)               # Center point
bounds_north       DECIMAL(10, 6)               # Bounding box
bounds_south       DECIMAL(10, 6)
bounds_east        DECIMAL(10, 6)
bounds_west        DECIMAL(10, 6)
created            DATETIME
modified           DATETIME
```

**Hierarchy:**
```
World
 ├─ Country (e.g., "United States")
 │   ├─ Adm1 (e.g., "California")
 │   │   └─ Adm2 (e.g., "San Francisco County")
 ├─ Country (e.g., "Brazil")
     └─ Adm1 (e.g., "São Paulo")
```

**Relationships:**
- Self-referential: `parent_id` → `geolocale_id`
- Referenced by: `geolocale_taxon`, `specimen`

**Indexes:**
- PRIMARY KEY (`geolocale_id`)
- INDEX (`name`, `geolocale_type`)
- INDEX (`parent_id`)
- UNIQUE (`name`, `parent_id`)

**Record Count:** ~5,000 locations

### 5. geolocale_taxon

**Purpose:** Junction table tracking which taxa occur in which locations

**Key Fields:**
```sql
geolocale_id       INT           FK → geolocale
taxon_name         VARCHAR(128)  FK → taxon
is_introduced      TINYINT(1)                   # Introduced species flag
is_endemic         TINYINT(1)                   # Endemic species flag
source             VARCHAR(128)                 # Data source
specimen_count     INT                          # # specimens from this location
created            DATETIME
```

**Purpose:**
- Track species distributions
- Calculate endemic species per location
- Identify introduced species
- Generate range maps

**Relationships:**
- `geolocale_id` → `geolocale.geolocale_id`
- `taxon_name` → `taxon.taxon_name`

**Indexes:**
- PRIMARY KEY (`geolocale_id`, `taxon_name`)
- INDEX (`taxon_name`)
- INDEX (`is_endemic`)
- INDEX (`is_introduced`)

**Record Count:** ~500,000+ location-taxon pairs

### 6. proj_taxon

**Purpose:** Junction table for taxa in projects/collections

**Key Fields:**
```sql
project_name       VARCHAR(64)   FK → project
taxon_name         VARCHAR(128)  FK → taxon
source             VARCHAR(128)                 # Data source
created            DATETIME
```

**Common Projects:**
- `allantwebants` - All ants in AntWeb
- `worldants` - Bolton's catalog (valid taxa)
- `fossilants` - Fossil ants
- `introducedants` - Introduced/invasive species
- Museum-specific projects (e.g., `casent`, `antwiki`)

**Relationships:**
- `project_name` → `project.name`
- `taxon_name` → `taxon.taxon_name`

**Indexes:**
- PRIMARY KEY (`project_name`, `taxon_name`)
- INDEX (`taxon_name`)

**Record Count:** ~200,000+ project-taxon pairs

### 7. project

**Purpose:** Defines collections, datasets, or thematic groupings

**Key Fields:**
```sql
name               VARCHAR(64)   PRIMARY KEY
title              VARCHAR(255)                 # Display title
description        TEXT                         # Project description
url                VARCHAR(255)                 # External URL
root_taxon         VARCHAR(128)  FK → taxon    # Top-level taxon (if applicable)
created            DATETIME
modified           DATETIME
```

**Relationships:**
- Referenced by: `proj_taxon`

**Indexes:**
- PRIMARY KEY (`name`)

**Record Count:** ~50 projects

### 8. description_edit

**Purpose:** Taxonomic descriptions and notes (editable by curators)

**Key Fields:**
```sql
id                 INT           PRIMARY KEY AUTO_INCREMENT
taxon_name         VARCHAR(128)  FK → taxon
title              VARCHAR(255)                 # e.g., "Biology", "Distribution"
content            LONGTEXT                     # HTML/formatted content
edit_type          VARCHAR(32)                  # "description", "biology", "notes"
access_group       INT                          # Access control
created_by         INT           FK → login    # Author
created            DATETIME
modified           DATETIME
```

**Common Title Types:**
- "Distribution" - Geographic range
- "Biology" - Natural history, behavior
- "Diagnosis" - Diagnostic features
- "Taxonomic Treatment" - Full description
- "Notes" - General notes

**Relationships:**
- `taxon_name` → `taxon.taxon_name`
- `created_by` → `login.id`

**Indexes:**
- PRIMARY KEY (`id`)
- INDEX (`taxon_name`, `title`)
- FULLTEXT (`content`)

**Record Count:** ~20,000+ descriptions

### 9. login

**Purpose:** User accounts and authentication

**Key Fields:**
```sql
id                 INT           PRIMARY KEY AUTO_INCREMENT
email              VARCHAR(255)  UNIQUE        # Email (username)
password_hash      VARCHAR(255)                 # Bcrypt hash
name               VARCHAR(255)                 # Full name
access_group       INT                          # Primary group
role               VARCHAR(32)                  # "admin", "curator", "contributor"
is_active          TINYINT(1)                   # Account enabled
last_login         DATETIME                     # Last login time
created            DATETIME
modified           DATETIME
```

**Roles:**
- `admin` - Full system access
- `curator` - Can upload data, edit descriptions
- `contributor` - Can submit data for review
- `public` - Read-only access (not in table)

**Relationships:**
- Referenced by: `description_edit`, `upload`, `specimen`

**Indexes:**
- PRIMARY KEY (`id`)
- UNIQUE (`email`)
- INDEX (`access_group`)

**Record Count:** ~200 users

### 10. bioregion

**Purpose:** Biogeographic regions (Palearctic, Nearctic, etc.)

**Key Fields:**
```sql
name               VARCHAR(64)   PRIMARY KEY
description        TEXT
created            DATETIME
```

**Standard Bioregions:**
- Nearctic (North America)
- Neotropical (Central/South America)
- Palearctic (Europe/North Asia)
- Afrotropic (Sub-Saharan Africa)
- Indomalaya (South/Southeast Asia)
- Australasia (Australia/Pacific)
- Oceania (Pacific Islands)
- Antarctic

**Relationships:**
- Referenced by: `specimen.biogeographic_region`, `geolocale_taxon`

**Record Count:** ~10 bioregions

## Supporting Tables

### 11. upload

**Purpose:** Track data upload history

**Key Fields:**
```sql
id                 INT           PRIMARY KEY AUTO_INCREMENT
file_name          VARCHAR(255)                 # Original filename
upload_type        VARCHAR(64)                  # "specimen", "image", "species_list"
status             VARCHAR(32)                  # "pending", "success", "error"
record_count       INT                          # Records processed
error_count        INT                          # Errors encountered
log_file           VARCHAR(255)                 # Path to log file
uploaded_by        INT           FK → login
upload_date        DATETIME
completed_date     DATETIME
```

### 12. artist

**Purpose:** Photographer/image creator information

**Key Fields:**
```sql
name               VARCHAR(128)  PRIMARY KEY
bio                TEXT                         # Biography
url                VARCHAR(255)                 # Website
created            DATETIME
```

### 13. copyright

**Purpose:** Copyright and licensing information

**Key Fields:**
```sql
id                 INT           PRIMARY KEY AUTO_INCREMENT
holder             VARCHAR(255)                 # Copyright holder
license            VARCHAR(64)                  # "CC-BY-SA", "All Rights Reserved", etc.
year               INT                          # Copyright year
statement          TEXT                         # Full statement
```

### 14. antwiki_taxon_country

**Purpose:** Track introduced species by country (from AntWiki)

**Key Fields:**
```sql
taxon_name         VARCHAR(128)  FK → taxon
genus              VARCHAR(128)
species            VARCHAR(128)
subspecies         VARCHAR(128)
country            VARCHAR(64)                  # Country name
introduced         VARCHAR(3)                   # "Yes"/"No"
source             VARCHAR(255)                 # Data source URL
```

**Data Source:** http://www.antwiki.org/wiki/images/0/0c/AntWiki_Regional_Taxon_List.txt

## Specimen Upload File Format

Tab-delimited file with 42 columns:

**Column Names:**
1. Count
2. SpecimenCode
3. Subfamily
4. Genus
5. SpeciesGroup
6. Species
7. LifeStageSex
8. Medium
9. SpecimenNotes
10. DNANotes
11. LocatedAt
12. OwnedBy
13. TypeStatus
14. DeterminedBy
15. DateDetermined
16. CollectionCode
17. CollectedBy
18. DateCollectedStart
19. DateCollectedEnd
20. Method
21. Habitat
22. Microhabitat
23. CollectionNotes
24. LocalityName
25. Adm2
26. Adm1
27. Adm1check
28. ISO_3166
29. Country
30. Elevation
31. LatDeg
32. LatMin
33. NS
34. LonDeg
35. LonMin
36. EW
37. LocLatitude
38. LocLongitude
39. LatLonMaxError
40. BiogeographicRegion
41. LocalityNotes
42. ElevationMaxError

**Example Row:**
```
1	CASENT0106322	Myrmicinae	Gauromyrmex		MY01	1aq	pin	Ant AToL voucher		UCDC	UCDC		Ward, P. S.	2013-09-01	PSW16444-07	Ward, P. S.	2010-08-20		at light	rainforest edge	at light		Danum Valley Field Centre		Sabah	1	MY	Malaysia	190 m							4.96478	117.80465	3 m	Indomalaya	GPS reading: 4.96478°N 117.80465°E		
```

## Database Maintenance

### Indexing Strategy

**Frequently Queried Columns:**
- All primary keys
- Foreign keys
- `taxon_name` (in all tables)
- `specimen_code`
- Geographic fields (`country`, `adm1`, `geolocale_id`)
- Status/type fields (`status`, `fossil`, `shot_type`)

**Full-Text Indexes:**
- `taxon.taxon_name` - For autocomplete
- `description_edit.content` - For text search

### Statistics and Counts

Several tables maintain denormalized counts for performance:

- `geolocale.endemic_species_count` - Endemic species per location
- `geolocale.specimen_count` - Specimens per location
- `geolocale_taxon.specimen_count` - Specimens per taxon per location
- `taxon.specimen_count` - Total specimens per taxon

**Recalculation:** These are recalculated after bulk uploads via:
```java
// Triggered by admin tools
GeolocaleDb.calcEndemic();
CountDb.recrawl();
```

### Migration System

**Location:** `/db/upgrade/`

**Structure:**
```
/db/upgrade/
├── 4.3/2011-04-29.sql
├── 5.0.1/2013-06-27.sql
├── 8.105/2024-02-29.sql
└── ...
```

**Naming:** `{version}/{date}.sql`

**Application:** Manual execution in order of version/date

### Backup Strategy

**Daily Backups:**
```bash
# Full database dump
mysqldump -h mysql -u antweb -p \
  --all-databases \
  --routines \
  --single-transaction \
  --quick \
  --column-statistics=0 \
  | gzip > ant-backup-$(date +%Y%m%d).sql.gz
```

**Storage:** DigitalOcean Spaces (`/antweb-dbarchive/`)

**Retention:** 30 days

## Query Patterns

### Common Queries

**1. Find all specimens for a taxon:**
```sql
SELECT * FROM specimen 
WHERE taxon_name = 'camponotus pennsylvanicus'
ORDER BY code;
```

**2. Get taxon hierarchy:**
```sql
WITH RECURSIVE taxon_tree AS (
  SELECT taxon_name, parent_taxon_name, 1 AS depth
  FROM taxon
  WHERE taxon_name = 'formicinae'
  
  UNION ALL
  
  SELECT t.taxon_name, t.parent_taxon_name, tt.depth + 1
  FROM taxon t
  INNER JOIN taxon_tree tt ON t.parent_taxon_name = tt.taxon_name
)
SELECT * FROM taxon_tree
ORDER BY depth, taxon_name;
```

**3. Species distribution by country:**
```sql
SELECT g.name AS country, COUNT(DISTINCT gt.taxon_name) AS species_count
FROM geolocale g
JOIN geolocale_taxon gt ON g.geolocale_id = gt.geolocale_id
JOIN taxon t ON gt.taxon_name = t.taxon_name
WHERE g.geolocale_type = 'country'
  AND t.status = 'valid'
  AND t.rank = 'species'
GROUP BY g.name
ORDER BY species_count DESC;
```

**4. Images for a specimen:**
```sql
SELECT * FROM image
WHERE specimen_code = 'CASENT0106322'
ORDER BY shot_type, shot_number;
```

## Performance Tuning

### InnoDB Configuration

**Recommended Settings:**
```ini
innodb_buffer_pool_size = 4G        # 50-75% of available RAM
innodb_log_file_size = 512M         # For large transactions
innodb_flush_log_at_trx_commit = 2  # Balance durability/performance
innodb_buffer_pool_instances = 4    # Parallel buffer pools
```

### Query Optimization

**Slow Query Log:**
- Enable: `slow_query_log = 1`
- Threshold: `long_query_time = 2`
- Log file: `/var/log/mysql/slow-query.log`

**Common Optimizations:**
- Add composite indexes for common query patterns
- Use EXPLAIN to analyze query plans
- Denormalize frequently accessed counts
- Partition large tables by date or project

## Data Integrity

### Referential Integrity

**Foreign Key Constraints:**
- Enforced on critical relationships
- Cascade deletes where appropriate
- Orphan cleanup via scheduled jobs

**Orphan Cleanup Queries:**
```sql
-- Find specimens without valid taxon
SELECT code FROM specimen s
LEFT JOIN taxon t ON s.taxon_name = t.taxon_name
WHERE t.taxon_name IS NULL;

-- Find images without specimen or taxon
SELECT image_id FROM image
WHERE specimen_code NOT IN (SELECT code FROM specimen)
  AND taxon_name NOT IN (SELECT taxon_name FROM taxon);
```

### Data Validation

**Upload Validation:**
- Required fields checked
- Data types validated
- Foreign keys verified
- Geographic coordinates validated

**Ongoing Validation:**
- Periodic data quality checks
- Duplicate detection
- Inconsistency reporting
