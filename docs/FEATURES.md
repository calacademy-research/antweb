# AntWeb Feature-to-Code Mapping

## Purpose

This document maps major AntWeb features to their code locations. Use this to understand:
- **"Where is the code for X?"** - Find all files involved in a feature
- **"What does this file do?"** - Understand which features it supports
- **"What breaks if I change X?"** - Identify dependencies

**For each feature, you'll find:**
- Database tables
- Java classes (database access, business logic, actions)
- JSP pages (if applicable)
- API endpoints
- Configuration files
- Related documentation

---

## Feature Index

1. [Bioregion Management](#1-bioregion-management)
2. [Image Picker / Default Specimens](#2-image-picker--default-specimens)
3. [Specimen Upload](#3-specimen-upload)
4. [Image Upload](#4-image-upload)
5. [Taxonomic Hierarchy](#5-taxonomic-hierarchy)
6. [Geographic Distribution](#6-geographic-distribution)
7. [Search](#7-search)
8. [Project Management](#8-project-management)
9. [Statistics & Counts](#9-statistics--counts)
10. [User Authentication](#10-user-authentication)
11. [Endemic Species Calculation](#11-endemic-species-calculation)
12. [Species List Management](#12-species-list-management)

---

## 1. Bioregion Management

**What it does:** Manages biogeographic regions (Nearctic, Neotropical, etc.) and tracks which taxa occur in each region.

### Database Tables
```
bioregion
├── Primary table for bioregion data
├── Columns: name, title, locality, extent, subfamily_count, genus_count, 
│            species_count, endemic_species_count, introduced_species_count
└── Stats: taxon_subfamily_dist_json, specimen_subfamily_dist_json

bioregion_taxon
├── Junction table: which taxa occur in which bioregions
└── Columns: name, taxon_name, source, created

specimen.biogeographic_region
└── Links specimens to bioregions

geolocale.biogeographic_region
└── Links locations to bioregions
```

### Java Classes

**Database Access Layer:**
```
src/org/calacademy/antweb/home/
├── BioregionDb.java                    [749 lines]
│   ├── getBioregion(name)              - Fetch single bioregion
│   ├── getBioregions()                 - List all bioregions
│   ├── getBioregionNames()             - Names only
│   ├── getCountryNames(bioregion)      - Countries in bioregion
│   └── Various count/statistics methods
│
├── BioregionTaxonDb.java               [602 lines]
│   ├── getBioregionTaxa()              - Taxa in a bioregion
│   ├── insertBioregionTaxon()          - Add taxon to bioregion
│   ├── deleteBioregionTaxon()          - Remove taxon
│   └── recalculate() methods
│
└── BioregionTaxonCountDb.java          [83 lines]
    ├── getBioregionTaxonCount()        - Count taxa in bioregion
    └── recrawlBioregion()              - Recalculate all counts
```

**Model Class:**
```
src/org/calacademy/antweb/geolocale/
└── Bioregion.java
    ├── Properties: name, title, description, counts
    ├── Getters/setters
    └── Helper methods
```

**Action Class (Web Controller):**
```
src/org/calacademy/antweb/
└── BioregionAction.java
    ├── Handles /bioregion.do requests
    ├── Prepares data for JSP
    └── Coordinates Db classes
```

### Web Layer (JSP)
```
web/
├── bioregion.jsp                       - Main bioregion display page
├── bioregionList.jsp                   - List of all bioregions
└── common/bioregionNav.jsp             - Navigation component
```

### API Endpoints
```
api/v3/api.py
└── Lines ~800-850: Bioregion endpoints
    ├── GET /geolocales?bioregion=X     - Filter by bioregion
    └── (Bioregions returned as part of geolocale data)
```

### Configuration
```
WEB-INF/struts-config.xml
└── <action path="/bioregion" type="...BioregionAction">
```

### Related Documentation
```
doc/
└── locality.txt                        - Geographic data processing
```

### How Bioregion Assignments Work

1. **Specimen Upload:**
   - User uploads specimen file with `BiogeographicRegion` column
   - `SpecimenUploadDb` inserts into `specimen.biogeographic_region`

2. **Taxon Association:**
   - `BioregionTaxonDb.recalculate()` runs periodically
   - Queries specimens to find which taxa occur in each bioregion
   - Populates `bioregion_taxon` table

3. **Statistics Update:**
   - `BioregionDb` methods calculate counts
   - Updates `bioregion` table statistics
   - Generates JSON distributions

4. **Display:**
   - `BioregionAction` fetches data via `BioregionDb`
   - JSP renders bioregion page with maps and stats

### Modifying Bioregion Feature

**To add a field to bioregion:**
1. Add column to `bioregion` table (see DATABASE.md)
2. Update `BioregionDb.getBioregion()` to fetch new field
3. Add property to `Bioregion.java` model class
4. Update JSP to display new field
5. Update API response if needed

**To change bioregion assignment logic:**
1. Modify `BioregionTaxonDb.recalculate()`
2. Test with `BioregionTaxonCountDb.recrawlBioregion()`

---

## 2. Image Picker / Default Specimens

**What it does:** Selects which specimen image is shown as the "default" or "representative" image for a taxon. Different defaults can be chosen for workers, queens, and males.

### Database Tables
```
taxon_prop
├── Stores default specimen selections
├── Columns: taxon_name, prop, value
└── Props: "defaultWorkerImage", "defaultQueenImage", "defaultMaleImage"
    Example: taxon_name='camponotus', prop='defaultWorkerImage', value='CASENT0106322'

image
└── Images linked to specimens

specimen
└── Specimens with caste information (is_worker, is_queen, is_male)
```

### Java Classes

**Database Access Layer:**
```
src/org/calacademy/antweb/home/
├── ImagePickDb.java                    [158 lines]
│   ├── getDefaultSpecimen(caste, taxon) - Get default specimen for a caste
│   ├── getDefaultSpecimenForTaxon()     - By taxon name
│   └── getDefaultSpecimenWithClause()   - Generic query method
│
├── TaxonPropDb.java                    [~300 lines]
│   ├── getTaxonProp()                   - Get property value
│   ├── setTaxonProp()                   - Set property value
│   └── deleteTaxonProp()                - Remove property
│
└── ImageDb.java                         [~600 lines]
    ├── getImages()                      - Get all images for specimen
    ├── getDefaultImage()                - Get representative image
    └── Various image query methods
```

**Caste Handling:**
```
src/org/calacademy/antweb/
└── Caste.java
    ├── Constants: WORKER, QUEEN, MALE, DEFAULT
    ├── getSpecimenClause(caste)         - SQL clause for caste
    ├── getProp(caste)                   - Property name for caste
    └── getPropsClause(caste)            - Props query clause
```

### Web Layer (JSP)
```
web/
├── imagePicker.jsp                     - Interface for curators to pick defaults
├── description.jsp                     - Shows default images on taxon pages
└── common/imageDisplay.jsp             - Image display component
```

### How Image Picker Works

1. **Curator Interface:**
   - Curator visits taxon page
   - Clicks "Pick Default Image" (admin only)
   - `imagePicker.jsp` shows all available specimens with images

2. **Selection Process:**
   - Curator selects specimen for each caste (worker/queen/male)
   - Form submits to Action class

3. **Database Update:**
   - `TaxonPropDb.setTaxonProp()` stores selection:
     - `taxon_name = 'camponotus pennsylvanicus'`
     - `prop = 'defaultWorkerImage'`
     - `value = 'CASENT0106322'`

4. **Display:**
   - `ImagePickDb.getDefaultSpecimen()` queries `taxon_prop`
   - Filters by caste: `is_worker = 1` OR `is_queen = 1`, etc.
   - Returns specimen code
   - `ImageDb` fetches images for that specimen
   - JSP displays the images

### Query Flow Example

```sql
-- Get default worker for Camponotus
SELECT value 
FROM taxon_prop 
WHERE taxon_name LIKE 'camponotus%' 
  AND prop = 'defaultWorkerImage'
  AND value IN (
    SELECT code FROM specimen WHERE is_worker = 1
  );
```

### Modifying Image Picker

**To add a new caste type:**
1. Add constant to `Caste.java`
2. Update `specimen` table with `is_[caste]` column
3. Add prop name in `Caste.getProp()`
4. Update UI to show new caste option

**To change selection logic:**
1. Modify `ImagePickDb.getDefaultSpecimenWithClause()`
2. Update SQL query logic
3. Test with various taxa

---

## 3. Specimen Upload

**What it does:** Processes curator uploads of specimen data files (tab-delimited, 42 columns).

### Database Tables
```
specimen
├── Main specimen data
└── All 42 columns from upload file

upload
├── Tracks upload history
└── Columns: file_name, upload_type, status, record_count, error_count, uploaded_by

geolocale_taxon
├── Updated from specimen data
└── Tracks which taxa occur in which locations

proj_taxon
├── Updated if project specified
└── Adds taxa to projects
```

### Java Classes

**Upload Processing:**
```
src/org/calacademy/antweb/upload/
├── UploadAction.java                   [~2000 lines] **KEY CLASS**
│   ├── execute()                        - Main upload handler
│   ├── runStatistics()                  - Calculate post-upload stats
│   └── updateUpload()                   - Update upload record
│
├── SpecimenUploadDb.java               [~800 lines]
│   ├── processUpload()                  - Main processing logic
│   ├── parseSpecimenLine()              - Parse each specimen row
│   ├── validateSpecimen()               - Validation rules
│   ├── insertSpecimen()                 - Insert into database
│   └── updateRelatedTables()            - Update geolocale_taxon, etc.
│
├── UploadFile.java                     [~400 lines]
│   ├── figureEncoding()                 - Detect file encoding (UTF-8, etc.)
│   ├── correctEncoding()                - Convert if needed
│   ├── backup()                         - Backup uploaded file
│   └── isTabDelimited()                 - Validate format
│
└── SpecimenUploadLookup.java           [~150 lines]
    ├── lookupCountry()                  - Validate country names
    ├── lookupAdm1()                     - Validate state/province
    └── Various lookup helpers
```

**Database Access:**
```
src/org/calacademy/antweb/home/
├── SpecimenDb.java                     [~1200 lines]
│   ├── insertSpecimen()                 - Low-level insert
│   ├── updateSpecimen()                 - Update existing
│   ├── getSpecimen(code)                - Fetch by code
│   └── Various query methods
│
├── UploadDb.java                       [~400 lines]
│   ├── insertUpload()                   - Log upload start
│   ├── updateUpload()                   - Update status/counts
│   └── getUploadHistory()               - Historical uploads
│
└── GeolocaleTaxonDb.java               [~800 lines]
    ├── populateFromSpecimenData()       - Update location data
    └── insertGeolocaleTaxon()           - Add taxon to location
```

### Web Layer (JSP)
```
web/
├── upload.jsp                          - Upload form
├── uploadResults.jsp                   - Upload results/errors
└── curate.jsp                          - Curator dashboard (links to upload)
```

### File Format

**42-Column Tab-Delimited Format:**
```
Count	SpecimenCode	Subfamily	Genus	SpeciesGroup	Species	LifeStageSex	
Medium	SpecimenNotes	DNANotes	LocatedAt	OwnedBy	TypeStatus	
DeterminedBy	DateDetermined	CollectionCode	CollectedBy	DateCollectedStart	
DateCollectedEnd	Method	Habitat	Microhabitat	CollectionNotes	
LocalityName	Adm2	Adm1	Adm1check	ISO_3166	Country	Elevation	
LatDeg	LatMin	NS	LonDeg	LonMin	EW	LocLatitude	LocLongitude	
LatLonMaxError	BiogeographicRegion	LocalityNotes	ElevationMaxError
```

See DATABASE.md → "Specimen Upload File Format" for full details and examples.

### Upload Processing Flow

1. **File Upload:**
   ```
   Curator → upload.jsp → POST to /upload.do → UploadAction.execute()
   ```

2. **Validation:**
   ```
   UploadFile.figureEncoding()    - Check UTF-8
   UploadFile.isTabDelimited()    - Verify format
   UploadFile.backup()            - Save to /web/upload/YYYYMMDD-HH:MM:SS-filename.txt
   ```

3. **Line-by-Line Processing:**
   ```
   SpecimenUploadDb.processUpload()
   ├── Parse each line
   ├── Lookup/validate country, adm1
   ├── Validate taxon name exists
   ├── Check for duplicate specimen codes
   └── Insert or update specimen
   ```

4. **Related Tables Update:**
   ```
   GeolocaleTaxonDb.populateFromSpecimenData()
   ├── Find specimens for each geolocale
   ├── Update geolocale_taxon table
   └── Increment specimen counts
   ```

5. **Statistics:**
   ```
   UploadAction.runStatistics()
   ├── Count total specimens
   ├── Count images
   ├── Insert into statistics table
   └── Log execution time
   ```

6. **Results Display:**
   ```
   uploadResults.jsp shows:
   ├── Records processed
   ├── Errors encountered
   ├── Link to upload log file
   └── Statistics
   ```

### Configuration
```
WEB-INF/struts-config.xml
└── <action path="/upload" type="...UploadAction">

WEB-INF/classes/AntwebResources.properties
├── upload.maxFileSize=100000000           # 100 MB
├── upload.backupDir=/web/upload/
└── upload.tempDir=/tmp/antweb/
```

### Related Documentation
```
doc/
├── specimen16.txt                      - Upload format documentation
├── dataFlow.txt                        - Data flow examples
└── specialChars.txt                    - Character encoding issues
```

### Common Upload Issues

**Encoding Problems:**
- Files must be UTF-8
- Excel exports may add UTF-8 BOM (handled by `figureEncoding()`)
- Special characters (é, ñ, etc.) require proper encoding

**Validation Errors:**
- Unknown taxon names → must exist in `taxon` table first
- Unknown countries → must match geolocale country names
- Duplicate specimen codes → updates existing, or errors if specified

**Fixes:**
- Check upload log file in `/web/upload/`
- Review errors in `uploadResults.jsp`
- Manually fix problematic specimens via SQL

### Modifying Specimen Upload

**To add a new column:**
1. Add column to `specimen` table
2. Update specimen upload format (add column position)
3. Modify `SpecimenUploadDb.parseSpecimenLine()` to read new column
4. Update `SpecimenDb.insertSpecimen()` SQL
5. Update documentation and example files

**To change validation rules:**
1. Modify `SpecimenUploadDb.validateSpecimen()`
2. Add/remove validation checks
3. Update error messages

---

## 4. Image Upload

**What it does:** Processes curator uploads of specimen/taxon images (TIFF, JPG) with automatic resizing and EXIF extraction.

### Database Tables
```
image
├── Image metadata
└── Columns: image_of_id, specimen_code, taxon_name, shot_type, artist, 
             has_tiff, upload_date, [size]_resolution paths

artist
├── Photographer information
└── Columns: name, bio, url

copyright
├── Copyright/license info
└── Columns: holder, license, year, statement
```

### Upload Process (PHP/Perl)

**Note:** Image upload uses PHP + Perl + ImageMagick, NOT Java (legacy system).

```
/var/www/html/imageUpload/
├── upload.php                          - Upload interface (YUI uploader)
├── getFiles.php                        - Validate uploaded files
├── process.php                         - Trigger processing
└── uploadResults.php                   - Display results

/home/antweb/workingdir/
└── processImages.pl                    - ImageMagick processing
    ├── Generate thumbnail (_low.jpg)
    ├── Generate medium (_med.jpg)
    ├── Generate high-res (_high.jpg)
    ├── Keep TIFF original (.tif)
    ├── Extract EXIF data
    └── Insert into database
```

### Java Classes (Database Only)

```
src/org/calacademy/antweb/home/
├── ImageDb.java                        [~600 lines]
│   ├── insertImage()                    - Add image record
│   ├── updateImage()                    - Update metadata
│   ├── deleteImage()                    - Remove image
│   └── getImages()                      - Query images
│
├── ImageUploadDb.java                  [~300 lines]
│   ├── processImageUpload()             - Process upload event
│   └── logImageUpload()                 - Log to database
│
└── ArtistDb.java                       [~250 lines]
    ├── getArtist()                      - Fetch photographer
    ├── insertArtist()                   - Add new photographer
    └── listArtists()                    - All photographers
```

### Web Layer
```
web/
├── imageUpload.jsp                     - Upload interface
├── imageUploadGuts.jsp                 - YUI uploader code (embedded)
└── curate.jsp                          - Link to upload

/var/www/html/imageUpload/              - PHP files (see above)
```

### Image Naming Convention

**Format:** `{specimen_code}_{shot_type}_{shot_number}.{ext}`

**Shot Types:**
- `h` or `H` - Head view
- `d` or `D` - Dorsal (top) view
- `p` or `P` - Profile (side) view
- `l` or `L` - Label

**Examples:**
```
CASENT0106322_h_1.tif       → Head shot, shot 1
CASENT0106322_d_1.jpg       → Dorsal shot, shot 1
CASENT0106322_p_2.tif       → Profile shot, shot 2
CASENT0106322_l_1.jpg       → Label shot, shot 1
```

**Generated Files:**
```
CASENT0106322_h_1.tif       (original)
├── CASENT0106322_h_1_low.jpg     (thumbnail: 90px)
├── CASENT0106322_h_1_med.jpg     (medium: 305px)
└── CASENT0106322_h_1_high.jpg    (high-res: 1500px)
```

### Upload Processing Flow

1. **Upload Interface:**
   ```
   Curator → imageUpload.jsp → YUI Flash uploader → upload.php
   ```

2. **File Validation (getFiles.php):**
   ```
   ✓ Check filename format
   ✓ Check specimen exists in database
   ✓ Check no duplicate image
   ✓ Check file size
   ✓ Check image group permissions
   ```

3. **File Staging:**
   ```
   Files saved to: /var/www/html/imageUpload/toUpload/
   ```

4. **Processing Trigger (process.php):**
   ```
   Calls: /home/antweb/workingdir/processImages.pl
   
   Arguments:
   -a "Artist Name"
   -c "Copyright Statement"
   -l "License Type"
   ```

5. **Image Processing (processImages.pl):**
   ```perl
   For each image file:
   ├── Validate format (TIFF or JPG)
   ├── Generate 3 sizes using ImageMagick:
   │   ├── convert -resize 90x90   → _low.jpg
   │   ├── convert -resize 305x305 → _med.jpg
   │   └── convert -resize 1500x1500 → _high.jpg
   ├── Extract EXIF data (camera, date, GPS)
   ├── Embed copyright in EXIF
   ├── Move to final location: /data/antweb/images/{specimen_code}/
   └── Insert record into `image` table
   ```

6. **Database Insert:**
   ```sql
   INSERT INTO image (
     specimen_code, shot_type, shot_number,
     artist, copyright, has_tiff,
     low_resolution, medium_resolution, high_resolution, tiff_resolution
   ) VALUES (...);
   ```

7. **Results:**
   ```
   uploadResults.php shows:
   ├── Images processed
   ├── Images failed
   ├── Error details
   └── Links to view images
   ```

### File System Structure

```
/data/antweb/images/
└── {specimen_code}/
    ├── {specimen_code}_h_1_low.jpg
    ├── {specimen_code}_h_1_med.jpg
    ├── {specimen_code}_h_1_high.jpg
    ├── {specimen_code}_h_1.tif
    ├── {specimen_code}_d_1_low.jpg
    └── ... (all shots for this specimen)
```

### Configuration

```
/etc/php.ini
├── upload_max_filesize = 100M
├── post_max_size = 100M
└── max_execution_time = 300

/var/www/html/imageUpload/config.php (if exists)
├── Image directory paths
├── Database connection
└── Processing options
```

### Related Documentation
```
doc/
├── imageUpload.txt                     - Upload process details
├── exif.txt                            - EXIF handling
├── artist.txt                          - Artist/photographer management
└── taxonImages.txt                     - Taxon-level images
```

### Common Image Upload Issues

**File Naming:**
- Specimen code must exist in database
- Shot type must be h/d/p/l (case insensitive)
- Invalid characters in code cause rejection

**Processing Failures:**
- Images stuck in /toUpload/ directory
- Manual trigger: `sudo -u apache /home/antweb/workingdir/processImages.pl -a "Artist" -c "Copyright" -l "License"`

**EXIF Problems:**
- Artist name not embedded → use exifedit tool
- Wrong date → extract from filename if possible

**Permissions:**
- Files must be owned by apache:apache
- Directories must be writable (755 or 777)

### Modifying Image Upload

**To add a new shot type:**
1. Update validation in getFiles.php
2. Update `Caste.java` if caste-related
3. Update display JSPs

**To change image sizes:**
1. Modify processImages.pl resize commands
2. Regenerate existing images (batch script)

**To add EXIF fields:**
1. Update processImages.pl extraction
2. Add columns to `image` table
3. Update `ImageDb.java`

---

## 5. Taxonomic Hierarchy

**What it does:** Manages ant taxonomy (subfamilies, tribes, genera, species, subspecies) with hierarchical relationships.

### Database Tables
```
taxon
├── Central taxonomic authority
├── Hierarchical structure via parent_taxon_name
└── Columns: taxon_name (PK), parent_taxon_name, rank, status, 
             subfamily, genus, species, subspecies, author_date, fossil

proj_taxon
├── Taxa in projects (e.g., "worldants" = Bolton's catalog)
└── Columns: project_name, taxon_name

homonym
├── Tracks homonyms (same name, different taxa)
└── Columns: taxon_name, author_date, valid_name
```

### Java Classes

**Database Access:**
```
src/org/calacademy/antweb/home/
├── TaxonDb.java                        [~2500 lines] **LARGEST CLASS**
│   ├── getTaxon(name)                   - Fetch single taxon
│   ├── getTaxonWithClause()             - Generic taxon query
│   ├── getChildren(taxon)               - Get child taxa
│   ├── getHierarchy(taxon)              - Build full hierarchy
│   ├── insertTaxon()                    - Add new taxon
│   ├── updateTaxon()                    - Update taxon data
│   ├── isValidTaxon()                   - Check validity
│   ├── getSynonyms()                    - Get synonyms
│   └── Hundreds of query methods
│
├── HomonymDb.java                      [~300 lines]
│   ├── getHomonyms()                    - Find homonyms
│   ├── insertHomonym()                  - Add homonym
│   └── resolveHomonym()                 - Mark resolution
│
└── ProjTaxonDb.java                    [~800 lines]
    ├── getProjectTaxa()                 - Taxa in a project
    ├── insertProjTaxon()                - Add taxon to project
    ├── deleteProjectTaxa()              - Remove from project
    └── recrawlProject()                 - Rebuild project taxa
```

**Model Class:**
```
src/org/calacademy/antweb/
└── Taxon.java                          [~1000 lines]
    ├── Properties: name, rank, status, subfamily, genus, species
    ├── getFullName()                    - Formatted scientific name
    ├── isSpeciesOrSubspecies()          - Check rank
    ├── isValid()                        - Check status
    └── Hierarchy navigation methods
```

### Taxonomic Ranks

```
Hierarchy (top to bottom):
├── Subfamily    (e.g., "formicinae")
├── Tribe        (e.g., "camponotini")
├── Genus        (e.g., "camponotus")
├── Subgenus     (e.g., "camponotus (tanaemyrmex)")
├── Species      (e.g., "camponotus pennsylvanicus")
└── Subspecies   (e.g., "camponotus pennsylvanicus ferrugineus")
```

### Taxonomic Status

- **valid** - Accepted name
- **synonym** - Invalid name, points to valid name
- **homonym** - Name used for multiple taxa
- **unavailable** - Name not available for use
- **unidentifiable** - Cannot be identified from description

### Web Layer (JSP)
```
web/
├── description.jsp                     - Main taxon page
├── browse.jsp                          - Browse hierarchy
├── taxonomicPage.jsp                   - Taxonomic overview
└── common/taxonNav.jsp                 - Navigation breadcrumbs
```

### API Endpoints
```
api/v3/api.py
└── GET /taxa
    ├── ?rank=genus                      - Filter by rank
    ├── ?status=valid                    - Filter by status
    ├── ?subfamily=formicinae            - Filter by subfamily
    └── ?fossil=true                     - Fossil taxa only
```

### Hierarchy Navigation

**Parent-Child Relationships:**
```sql
-- Get all genera in Formicinae
SELECT * FROM taxon 
WHERE parent_taxon_name = 'formicinae' 
  AND rank = 'genus';

-- Get full hierarchy for a species
WITH RECURSIVE hierarchy AS (
  SELECT * FROM taxon WHERE taxon_name = 'camponotus pennsylvanicus'
  UNION ALL
  SELECT t.* FROM taxon t
  JOIN hierarchy h ON t.taxon_name = h.parent_taxon_name
)
SELECT * FROM hierarchy;
```

**Java Hierarchy Building:**
```java
TaxonDb taxonDb = new TaxonDb(conn);

// Get taxon
Taxon species = taxonDb.getTaxon("camponotus pennsylvanicus");

// Get parent (genus)
Taxon genus = taxonDb.getTaxon(species.getParentTaxonName());

// Get children (subspecies)
List<Taxon> subspecies = taxonDb.getChildren(species);

// Get all descendants
List<Taxon> descendants = taxonDb.getDescendants(genus);
```

### Modifying Taxonomy

**To add a new taxon:**
1. Ensure parent exists in database
2. Insert into `taxon` table with correct parent_taxon_name
3. Add to appropriate project (e.g., "worldants")
4. Recalculate statistics

**To change taxonomic status:**
1. Update `status` field
2. If making synonym, set `current_valid_name`
3. Update proj_taxon to reflect status
4. Recrawl projects

---

## 6. Geographic Distribution

**What it does:** Tracks which ant taxa occur in which geographic locations (countries, states, regions).

### Database Tables
```
geolocale
├── Geographic locations (hierarchy: world → country → adm1 → adm2)
├── Columns: geolocale_id (PK), name, parent_id, geolocale_type,
│            iso_code, centroid_lat/lon, bounds, biogeographic_region
└── Counts: species_count, endemic_species_count, specimen_count

geolocale_taxon
├── Junction: which taxa occur where
├── Columns: geolocale_id, taxon_name, is_introduced, is_endemic, specimen_count
└── Populated from specimen locality data

specimen
├── Geographic fields: country, adm1, adm2, decimal_latitude, decimal_longitude
└── Links specimens to locations

antwiki_taxon_country
├── Data from AntWiki (introduced species)
└── Columns: taxon_name, country, introduced (Yes/No)
```

### Java Classes

**Database Access:**
```
src/org/calacademy/antweb/home/
├── GeolocaleDb.java                    [~2500 lines] **HUGE CLASS**
│   ├── getGeolocale(id)                 - Fetch geolocale
│   ├── getGeolocaleByName()             - Lookup by name
│   ├── getChildren()                    - Child locations
│   ├── getCountries()                   - All countries
│   ├── getAdm1s(country)                - States/provinces
│   ├── calcEndemic()                    - Calculate endemic species
│   ├── pushUnCountryToGeolocale()       - Import UN country data
│   └── Many geographic query methods
│
├── GeolocaleTaxonDb.java               [~1200 lines]
│   ├── getGeolocaleTaxa()               - Taxa in location
│   ├── insertGeolocaleTaxon()           - Add taxon to location
│   ├── populateFromSpecimenData()       - Build from specimens
│   ├── populateFromAntwikiData()        - Import AntWiki data
│   ├── setIntroduced()                  - Mark as introduced
│   └── setEndemic()                     - Mark as endemic
│
├── CountryDb.java                      [~200 lines]
│   ├── getCountries()                   - List countries
│   ├── getCountryByCode()               - ISO code lookup
│   └── Various country helpers
│
└── AntwikiTaxonCountryDb.java          [~400 lines]
    ├── loadAntwikiData()                - Import regional list
    └── getIntroducedTaxa()              - Get introduced species
```

**Model Classes:**
```
src/org/calacademy/antweb/geolocale/
├── Geolocale.java                      - Location object
├── Country.java                        - Country-specific
└── Adm1.java                           - State/province
```

### Geographic Hierarchy

```
World
├── Oceania (bioregion)
│   └── Australia (country, ISO: AU)
│       ├── Queensland (adm1)
│       │   └── Brisbane (adm2)
│       └── New South Wales (adm1)
└── Nearctic (bioregion)
    └── United States (country, ISO: US)
        ├── California (adm1)
        └── New York (adm1)
```

### Distribution Data Flow

**1. Specimen Upload Populates Locations:**
```
User uploads specimen file
├── Country: "United States"
├── Adm1: "California"
├── Coordinates: 34.0522, -118.2437
└── BiogeographicRegion: "Nearctic"

SpecimenUploadDb processes:
├── Lookup geolocale_id for "United States"
├── Lookup geolocale_id for "California"
├── Insert specimen with location data
└── Trigger: populateFromSpecimenData()
```

**2. Geolocale_Taxon Population:**
```sql
-- For each geolocale, find all taxa from specimens
INSERT INTO geolocale_taxon (geolocale_id, taxon_name, specimen_count)
SELECT 
  geolocale.geolocale_id,
  specimen.taxon_name,
  COUNT(*) as specimen_count
FROM specimen
JOIN geolocale ON specimen.country = geolocale.name
WHERE geolocale.geolocale_type = 'country'
GROUP BY geolocale.geolocale_id, specimen.taxon_name;
```

**3. Endemic Calculation:**
```java
GeolocaleDb.calcEndemic()
├── For each geolocale:
│   ├── Find taxa that occur ONLY in this geolocale
│   ├── Set is_endemic = 1 in geolocale_taxon
│   └── Update endemic_species_count in geolocale
```

**4. Introduced Species:**
```java
GeolocaleTaxonDb.populateFromAntwikiData()
├── Read antwiki_taxon_country table
├── For each taxon with introduced="Yes":
│   ├── Find geolocale_id for country
│   └── Set is_introduced = 1 in geolocale_taxon
```

### Web Layer (JSP)
```
web/
├── geolocale.jsp                       - Location page
├── countryPage.jsp                     - Country-specific
├── worldMap.jsp                        - World distribution map
└── common/geolocaleNav.jsp             - Geographic navigation
```

### API Endpoints
```
api/v3/api.py
├── GET /geolocales                     - List locations
│   ├── ?name=california                - Search by name
│   ├── ?type=country                   - Filter by type
│   └── ?bioregion=nearctic             - Filter by bioregion
│
├── GET /geolocales/{id}                - Single location
│
└── GET /geolocales/{id}/taxa           - Taxa in location
    ├── ?endemic=true                   - Only endemic
    └── ?introduced=true                - Only introduced
```

### Modifying Geographic Features

**To add a new country:**
1. Insert into `geolocale` table
2. Set parent_id to World geolocale
3. Set geolocale_type = 'country'
4. Set ISO code
5. Run populateFromSpecimenData()

**To recalculate distributions:**
```sql
-- Clear existing data
DELETE FROM geolocale_taxon;

-- Rebuild from specimens
CALL GeolocaleTaxonDb.populateFromSpecimenData();

-- Calculate endemics
CALL GeolocaleDb.calcEndemic();
```

---

## 7. Search

**What it does:** Full-text search across specimens, taxa, and descriptions using Apache Solr.

### Search Components

**Search Engine:**
```
Apache Solr (Java-based)
├── Runs on port 8983
├── Indexes: taxa, specimens, descriptions
└── Query parser: Lucene syntax
```

### Database Tables
```
(Solr maintains its own indexes, but pulls from these tables)

taxon
├── Scientific names
└── Full-text indexed

specimen
├── Specimen codes, localities, collectors
└── Full-text indexed

description_edit
├── Taxonomic descriptions and notes
└── Full-text indexed

search_history (optional)
└── Log search queries
```

### Java Classes

**Search Implementation:**
```
src/org/calacademy/antweb/home/
└── SearchDb.java                       [~300 lines]
    ├── search(query)                    - Execute search
    ├── advancedSearch(params)           - Multi-field search
    ├── buildSolrQuery()                 - Construct Solr query
    └── parseResults()                   - Process Solr response

src/org/calacademy/antweb/
└── SearchAction.java                   [~500 lines]
    ├── execute()                        - Handle search requests
    ├── simpleSearch()                   - Single field search
    ├── advancedSearch()                 - Multi-field search
    └── prepareResults()                 - Format for display
```

**Solr Integration:**
```
src/org/calacademy/antweb/util/
└── SolrUtil.java                       [~400 lines]
    ├── query(solrQuery)                 - Send query to Solr
    ├── indexTaxon()                     - Add/update taxon in index
    ├── indexSpecimen()                  - Add/update specimen
    ├── rebuildIndex()                   - Full reindex
    └── optimize()                       - Optimize Solr index
```

### Web Layer (JSP)
```
web/
├── search.jsp                          - Search form
├── simpleSearch.jsp                    - Single-field search
├── advancedSearch.jsp                  - Multi-field search
├── searchResults.jsp                   - Results display
└── common/searchBox.jsp                - Header search box
```

### Search Types

**1. Simple Search:**
- Single search box
- Searches: taxon names, specimen codes, localities
- Example: "Camponotus"

**2. Advanced Search:**
- Multiple fields: genus, species, country, collector, etc.
- Boolean operators: AND, OR, NOT
- Example: genus:camponotus AND country:"United States"

**3. Geographic Search:**
- Bounding box search
- Radius search around point
- Example: specimens within 50km of coordinates

### Solr Query Examples

**Simple taxon search:**
```
http://localhost:8983/solr/antweb/select?q=camponotus&wt=json
```

**Advanced search:**
```
http://localhost:8983/solr/antweb/select
  ?q=genus:camponotus AND country:"United States"
  &fq=status:valid
  &rows=50
  &start=0
  &wt=json
```

**Faceted search:**
```
http://localhost:8983/solr/antweb/select
  ?q=*:*
  &facet=true
  &facet.field=subfamily
  &facet.field=country
  &wt=json
```

### Solr Schema

**Indexed Fields:**
```xml
<fields>
  <!-- Taxon fields -->
  <field name="taxon_name" type="string" indexed="true" stored="true"/>
  <field name="subfamily" type="string" indexed="true"/>
  <field name="genus" type="string" indexed="true"/>
  <field name="species" type="string" indexed="true"/>
  
  <!-- Specimen fields -->
  <field name="code" type="string" indexed="true" stored="true"/>
  <field name="country" type="string" indexed="true"/>
  <field name="adm1" type="string" indexed="true"/>
  <field name="collected_by" type="text" indexed="true"/>
  
  <!-- Full-text fields -->
  <field name="description_text" type="text" indexed="true"/>
</fields>
```

### Index Rebuilding

**Full reindex (run via admin tools):**
```java
// Trigger reindex
SolrUtil solrUtil = new SolrUtil();
solrUtil.rebuildIndex();

// Steps:
// 1. Clear existing Solr index
// 2. Query all taxa from database
// 3. For each taxon, add to Solr
// 4. Query all specimens
// 5. For each specimen, add to Solr
// 6. Optimize index
// 7. Takes ~30-60 minutes for full database
```

**Incremental update (automatic):**
```java
// When taxon updated
TaxonDb.updateTaxon(taxon);
SolrUtil.indexTaxon(taxon);  // Updates Solr

// When specimen uploaded
SpecimenDb.insertSpecimen(specimen);
SolrUtil.indexSpecimen(specimen);  // Updates Solr
```

### Configuration

```
WEB-INF/classes/AntwebResources.properties
├── solr.url=http://localhost:8983/solr/antweb
├── solr.timeout=30000
└── solr.rows.default=50

solr/conf/solrconfig.xml
├── Request handlers
├── Query parsing
└── Caching configuration

solr/conf/schema.xml
├── Field definitions
├── Field types
└── Analyzers/tokenizers
```

### Related Documentation
```
doc/
└── search.txt                          - Search implementation notes
```

### Modifying Search

**To add a new searchable field:**
1. Add field to Solr schema.xml
2. Update `SolrUtil.indexTaxon()` or `indexSpecimen()`
3. Add to search form JSP
4. Update `SearchAction` to handle new field
5. Rebuild Solr index

**To improve relevance:**
1. Modify Solr field boosts in schema.xml
2. Adjust query parser settings
3. Add synonyms
4. Tune analyzer/tokenizer

---

## 8. Project Management

**What it does:** Organizes taxa into logical collections (e.g., "worldants" = Bolton's catalog, "introducedants" = invasive species).

### Database Tables
```
project
├── Project definitions
└── Columns: name (PK), title, description, url, root_taxon

proj_taxon
├── Taxa in each project
└── Columns: project_name, taxon_name

proj_taxon_count
└── Statistics per project

proj_taxon_log
└── Change history
```

### Java Classes

```
src/org/calacademy/antweb/home/
├── ProjectDb.java                      [~600 lines]
│   ├── getProject(name)                 - Fetch project
│   ├── getProjects()                    - List all projects
│   ├── insertProject()                  - Create project
│   └── updateProject()                  - Modify project
│
├── ProjTaxonDb.java                    [~800 lines]
│   ├── getProjectTaxa()                 - Taxa in project
│   ├── insertProjTaxon()                - Add taxon to project
│   ├── deleteProjTaxon()                - Remove from project
│   ├── recrawlProject()                 - Rebuild project taxa
│   └── Various project queries
│
└── ProjTaxonCountDb.java               [~300 lines]
    ├── getProjectCounts()               - Statistics
    └── recrawlProject()                 - Recalculate
```

### Common Projects

```
worldants
├── Bolton's World Catalog of Ants
└── Only valid taxa (status='valid')

allantwebants
├── All taxa in AntWeb
└── Valid and invalid

fossilants
├── Fossil ants only
└── fossil=1

introducedants
├── Introduced/invasive species
└── Special handling for native bioregion
```

### Project Processing

**Recrawl Project:**
```java
ProjTaxonDb projTaxonDb = new ProjTaxonDb(conn);

// Clear existing project taxa
projTaxonDb.deleteProjectTaxa("worldants");

// Rebuild from taxon table
projTaxonDb.recrawlProject("worldants");

// Logic:
// SELECT * FROM taxon WHERE status='valid'
// INSERT INTO proj_taxon (project_name, taxon_name) VALUES ('worldants', taxon_name)
```

### Modifying Projects

**To create a new project:**
```sql
INSERT INTO project (name, title, description)
VALUES ('myproject', 'My Project', 'Description here');

-- Add taxa
INSERT INTO proj_taxon (project_name, taxon_name)
SELECT 'myproject', taxon_name 
FROM taxon 
WHERE subfamily='formicinae';
```

---

## 9. Statistics & Counts

**What it does:** Maintains denormalized counts for performance (species count, specimen count, image count, etc.).

### Database Tables
```
(Many tables have _count fields that are periodically updated)

bioregion
├── subfamily_count, genus_count, species_count, specimen_count, image_count

geolocale
├── species_count, endemic_species_count, specimen_count

taxon
├── specimen_count, image_count

statistics (log table)
└── Logs each statistics recalculation run
```

### Java Classes

```
src/org/calacademy/antweb/home/
├── StatisticsDb.java                   [~1000 lines]
│   ├── recrawl()                        - Recalculate all statistics
│   ├── recrawlTaxonCounts()             - Update taxon counts
│   ├── recrawlGeolocaleCounts()         - Update location counts
│   ├── recrawlBioregionCounts()         - Update bioregion counts
│   └── logStatistics()                  - Record in statistics table
│
├── CountDb.java                        [~800 lines]
│   ├── updateTaxonCounts()              - Specimens, images per taxon
│   ├── updateGeolocaleCounts()          - Species per location
│   └── Various count methods
│
└── *TaxonCountDb.java classes
    ├── BioregionTaxonCountDb.java
    ├── GeolocaleTaxonCountDb.java
    ├── MuseumTaxonCountDb.java
    └── ProjTaxonCountDb.java
```

### When Statistics Are Updated

**Automatic:**
- After specimen upload
- After image upload
- After project recrawl

**Manual (admin tools):**
- `/util.do?action=recrawlStatistics`
- Takes 5-30 minutes depending on database size

### Example Count Queries

```sql
-- Update specimen count for each taxon
UPDATE taxon t
SET specimen_count = (
  SELECT COUNT(*) FROM specimen s 
  WHERE s.taxon_name = t.taxon_name
);

-- Update species count for each country
UPDATE geolocale g
SET species_count = (
  SELECT COUNT(DISTINCT gt.taxon_name)
  FROM geolocale_taxon gt
  JOIN taxon t ON gt.taxon_name = t.taxon_name
  WHERE gt.geolocale_id = g.geolocale_id
    AND t.rank = 'species'
    AND t.status = 'valid'
);
```

---

## 10. User Authentication

**What it does:** Manages user accounts, roles, and permissions.

### Database Tables
```
login
├── User accounts
└── Columns: id (PK), email, password_hash, name, access_group, role, is_active

access_group
├── Permission groups
└── Columns: id, name, description

group_login
└── User-to-group mapping
```

### Java Classes

```
src/org/calacademy/antweb/home/
└── LoginDb.java                        [~600 lines]
    ├── authenticate()                   - Login verification
    ├── getLogin(email)                  - Fetch user
    ├── insertLogin()                    - Create user
    ├── updateLogin()                    - Modify user
    ├── changePassword()                 - Update password
    └── checkPermission()                - Authorization check
```

### User Roles

```
admin
├── Full system access
├── Upload specimens/images
├── Manage users
└── Admin tools access

curator
├── Upload specimens/images
├── Edit descriptions
└── No user management

contributor
├── Submit specimens for review
└── Read-only otherwise

public (no login)
└── Read-only access
```

### Authentication Flow

```
Login Form → LoginAction.execute()
├── LoginDb.authenticate(email, password)
│   ├── Fetch user from login table
│   ├── Compare password hashes (bcrypt)
│   └── Return Login object if valid
├── Create session
├── Store Login object in session
└── Redirect to curate.do or home
```

---

## 11. Endemic Species Calculation

**What it does:** Identifies species that occur in only one geographic location.

### Implementation

**Location:** `GeolocaleDb.calcEndemic()`

**Algorithm:**
```java
For each geolocale (country, adm1):
  1. Find all taxa that occur in this geolocale
  2. For each taxon:
     a. Count how many geolocales it occurs in
     b. If count = 1, it's endemic
     c. Set is_endemic = 1 in geolocale_taxon
  3. Count endemic species
  4. Update endemic_species_count in geolocale
```

**SQL Logic:**
```sql
-- Find endemic species for California
UPDATE geolocale_taxon
SET is_endemic = 1
WHERE geolocale_id = (SELECT geolocale_id FROM geolocale WHERE name='California')
  AND taxon_name IN (
    SELECT taxon_name 
    FROM geolocale_taxon
    GROUP BY taxon_name
    HAVING COUNT(DISTINCT geolocale_id) = 1
  );
```

**Triggered by:**
- Admin tool: `/util.do?action=calcEndemic`
- After specimen upload (if specified)
- Manual DB update

---

## 12. Species List Management

**What it does:** Import species lists from external sources (e.g., Bolton's catalog from AntWiki).

### Database Tables
```
proj_taxon
└── Populated from species list files

species_list (optional)
└── Import history
```

### Java Classes

```
src/org/calacademy/antweb/home/
├── SpeciesListUploadDb.java            [~500 lines]
│   ├── importSpeciesList()              - Process upload
│   ├── parseSpeciesLine()               - Parse each line
│   └── insertProjectTaxon()             - Add to project
│
└── WorldantsUploadDb.java              [~300 lines]
    └── Specialized for Bolton's catalog format
```

### Process

1. Download species list (e.g., from AntWiki)
2. Upload via web interface
3. `SpeciesListUploadDb.importSpeciesList()` processes
4. Updates `proj_taxon` table
5. Recalculates project statistics

---

## Quick Reference: "Where Do I Find X?"

| Feature | Primary Class | Database Table | JSP Page |
|---------|--------------|----------------|----------|
| Bioregion data | BioregionDb.java | bioregion | bioregion.jsp |
| Image picker | ImagePickDb.java | taxon_prop | imagePicker.jsp |
| Specimen upload | SpecimenUploadDb.java | specimen | upload.jsp |
| Image upload | ImageUploadDb.java | image | imageUpload.jsp |
| Taxonomy | TaxonDb.java | taxon | description.jsp |
| Geographic data | GeolocaleDb.java | geolocale | geolocale.jsp |
| Search | SearchDb.java | (Solr) | search.jsp |
| Projects | ProjectDb.java | project | project.jsp |
| Statistics | StatisticsDb.java | (multiple) | (admin) |
| User login | LoginDb.java | login | login.jsp |
| Endemic calc | GeolocaleDb.calcEndemic() | geolocale_taxon | (admin) |
| Species lists | SpeciesListUploadDb.java | proj_taxon | upload.jsp |

---

## Code Organization Patterns

### Pattern 1: *Db.java Classes (Database Access Layer)

**Every major entity has a Db class:**
```
TaxonDb.java        → taxon table
SpecimenDb.java     → specimen table
ImageDb.java        → image table
GeolocaleDb.java    → geolocale table
```

**Standard methods:**
- `get*()` - Fetch records
- `insert*()` - Add records
- `update*()` - Modify records
- `delete*()` - Remove records
- `recrawl*()` - Rebuild/recalculate data

### Pattern 2: Model Classes

**Java objects representing database entities:**
```
Taxon.java          → taxon table row
Specimen.java       → specimen table row
Image.java          → image table row
Geolocale.java      → geolocale table row
```

**Contains:**
- Properties (match table columns)
- Getters/setters
- Helper methods
- No database access logic

### Pattern 3: Action Classes (Web Controllers)

**Struts Action classes handle web requests:**
```
TaxonAction.java           → /description.do
SpecimenAction.java        → /specimen.do
UploadAction.java          → /upload.do
```

**Standard flow:**
```java
public ActionForward execute(mapping, form, request, response) {
  // 1. Get parameters
  String taxonName = request.getParameter("taxonName");
  
  // 2. Call Db classes
  TaxonDb taxonDb = new TaxonDb(getConnection());
  Taxon taxon = taxonDb.getTaxon(taxonName);
  
  // 3. Prepare data for JSP
  request.setAttribute("taxon", taxon);
  
  // 4. Forward to JSP
  return mapping.findForward("success");
}
```

### Pattern 4: Junction Tables

**Many-to-many relationships:**
```
proj_taxon          → project ←→ taxon
geolocale_taxon     → geolocale ←→ taxon  
bioregion_taxon     → bioregion ←→ taxon
```

**Each has corresponding *Db.java class:**
```
ProjTaxonDb.java
GeolocaleTaxonDb.java
BioregionTaxonDb.java
```

### Pattern 5: Count/Statistics Tables

**Denormalized counts for performance:**
```
proj_taxon_count
geolocale_taxon_count
bioregion_taxon_count
```

**Managed by:**
```
ProjTaxonCountDb.java
GeolocaleTaxonCountDb.java
BioregionTaxonCountDb.java
```

---

## Finding Code: Step-by-Step

### Example: "I want to modify how endemic species are calculated"

**Step 1: Identify the feature**
→ Endemic Species Calculation

**Step 2: Find in feature list**
→ Section 11 above

**Step 3: Locate main class**
→ `GeolocaleDb.java`, method `calcEndemic()`

**Step 4: Find related code**
→ `GeolocaleTaxonDb.java` (updates `geolocale_taxon.is_endemic`)

**Step 5: Find database tables**
→ `geolocale` and `geolocale_taxon`

**Step 6: Make changes**
→ Modify `GeolocaleDb.calcEndemic()` algorithm

**Step 7: Test**
→ Run `/util.do?action=calcEndemic` and verify results

---

## Next Steps

- **For detailed modifications:** See [EXAMPLES.md](EXAMPLES.md)
- **For testing:** See [TESTING.md](TESTING.md)
- **For database details:** See [DATABASE.md](DATABASE.md)
- **For development setup:** See [DEVELOPMENT.md](DEVELOPMENT.md)
