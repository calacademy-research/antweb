# AntWeb Architecture Documentation

## Overview

AntWeb is the world's largest online database of images, specimen records, and natural history information on ants. It is community-driven and open to contribution from anyone with specimen records, natural history comments, or images.

**Live Site:** https://www.antweb.org  
**Repository:** https://github.com/calacademy-research/antweb

## Technology Stack

### Backend
- **Primary Language:** Java (51.2% of codebase)
- **Web Framework:** Apache Struts (Java EE)
- **Build Tool:** Apache Ant
- **Application Server:** Apache Tomcat 7+
- **Database:** MySQL 5.x
- **Search Engine:** Apache Solr (Java-based full-text search)

### Frontend
- **Languages:** JavaScript (37.2%), HTML (1.7%), CSS (4.1%)
- **Templates:** JSP (JavaServer Pages)
- **UI Framework:** Custom JavaScript with jQuery

### API Layer
- **Language:** Python 3.6+
- **Framework:** Flask with Flask-RESTful
- **ORM:** SQLAlchemy
- **Database Connector:** MySQL-python (mysqldb)

### Infrastructure
- **Containerization:** Docker + Docker Compose
- **Web Server/Proxy:** Caddy (automatic HTTPS/TLS management)
- **File Storage:** DigitalOcean Spaces (S3-compatible object storage)
  - Images: ~1.6 TB
  - Documents: ~10 GB
  - Upload History: ~200 GB
- **File Mounting:** s3fs-fuse / rclone

### Development Tools
- **Version Control:** Git / GitHub
- **Database Migration:** Custom SQL scripts in `/db/upgrade/`
- **Environment Management:** Docker Compose with environment-specific overrides

## System Architecture

### High-Level Architecture

```
┌─────────────┐
│   Users     │
│ (Browsers)  │
└──────┬──────┘
       │ HTTPS
       ▼
┌─────────────┐
│   Caddy     │ ← Automatic TLS, Reverse Proxy
│  (Port 80/  │
│    443)     │
└──────┬──────┘
       │
       ▼
┌─────────────────────────────────┐
│     Tomcat Application Server    │
│  ┌───────────────────────────┐  │
│  │    Struts Controllers     │  │
│  └───────────┬───────────────┘  │
│              │                   │
│  ┌───────────▼───────────────┐  │
│  │   Business Logic Layer    │  │
│  │  (Java Classes in         │  │
│  │   org.calacademy.antweb)  │  │
│  └───────────┬───────────────┘  │
│              │                   │
│  ┌───────────▼───────────────┐  │
│  │  Data Access Layer (Db)   │  │
│  │  - TaxonDb, SpecimenDb    │  │
│  │  - ImageDb, GeolocaleDb   │  │
│  └───────────┬───────────────┘  │
└──────────────┼───────────────────┘
               │
       ┌───────┴───────┐
       │               │
       ▼               ▼
┌─────────────┐ ┌─────────────┐
│   MySQL     │ │  Apache     │
│  Database   │ │   Solr      │
│  (Port 3306)│ │ (Port 8983) │
└─────────────┘ └─────────────┘

┌─────────────────────────────┐
│  Python API (Flask)          │
│  api.antweb.org/v3/          │
│  - Independent service       │
│  - Direct MySQL access       │
│  - RESTful JSON responses    │
└──────────┬──────────────────┘
           │
           ▼
    ┌─────────────┐
    │   MySQL     │
    │  (Shared)   │
    └─────────────┘

┌────────────────────────────┐
│  DigitalOcean Spaces       │
│  (S3-Compatible Storage)   │
│  - /antweb/images/         │
│  - /antweb/web/            │
│  - /antweb-dbarchive/      │
└────────────────────────────┘
```

## Core Components

### 1. Web Application (Java/Struts)

**Location:** `/src/org/calacademy/antweb/`

**Key Packages:**
- `home/` - Database access layer (Db classes)
- `util/` - Utility classes and helpers
- `upload/` - File upload processing
- `geolocale/` - Geographic location handling
- `curate/` - Data curation tools (admin)

**Request Flow:**
1. User makes HTTP request
2. Caddy routes to Tomcat
3. Struts framework maps URL to Action class
4. Action class calls Business Logic
5. Business Logic uses Db classes to access MySQL
6. Response rendered via JSP template
7. Returned to user

### 2. Database Access Layer

**Location:** `/src/org/calacademy/antweb/home/`

**Pattern:** Each major entity has a corresponding `*Db.java` class

**Key Db Classes:**
- `TaxonDb.java` - Taxonomic data (species, genera, subfamilies)
- `SpecimenDb.java` - Individual specimen records
- `ImageDb.java` - Image metadata and associations
- `GeolocaleDb.java` - Geographic locations (countries, regions)
- `ProjectDb.java` - Collections/projects (e.g., "allantwebants", "worldants")
- `UploadDb.java` - Upload history and processing
- `LoginDb.java` - User authentication
- `BioregionDb.java` - Biogeographic regions

**Database Connection:**
- Connection pooling managed by Tomcat
- Configuration in `WEB-INF/web.xml` and `context.xml`
- JDBC URL format: `jdbc:mysql://mysql:3306/ant`

### 3. Search Engine (Apache Solr)

**Purpose:** Full-text search across taxa, specimens, and descriptions

**Integration:**
- Java application sends queries to Solr via HTTP
- Solr indexes are rebuilt periodically via admin tools
- Results merged with database queries

**Search Fields:**
- Taxon names (scientific names, synonyms)
- Specimen codes
- Collection locations
- Descriptive text

### 4. RESTful API (Python/Flask)

**Location:** `/api/v3/`

**Endpoints:**
- `/api/v3/specimens` - Specimen data
- `/api/v3/images` - Image metadata
- `/api/v3/taxa` - Taxonomic information
- `/api/v3/geolocales` - Geographic data

**Technology:**
- Flask application server
- SQLAlchemy ORM for database access
- Direct MySQL queries for performance
- JSON responses

**Deployment:**
- Runs as separate Docker container
- Accessible at `api.antweb.org`
- Can be deployed independently from main app

### 5. File Storage

**Architecture:**
- **Primary Storage:** DigitalOcean Spaces (S3-compatible)
- **Mount Method:** rclone/s3fs-fuse to local filesystem
- **Access Pattern:** Files appear as local paths to application

**Directory Structure:**
```
/mnt/antweb/
├── images/              # ~1.6 TB of ant images
│   ├── specimens/       # Organized by specimen code
│   └── ...
├── web/                 # ~10 GB application data
│   ├── speciesList/     # Species list uploads
│   ├── upload/          # Upload history (~200 GB)
│   └── ...
```

**Image Processing:**
- Images uploaded via web interface
- Processed and resized on server
- Stored in Spaces with multiple size variants
- CDN delivery via Spaces CDN

### 6. Upload System

**Specimen Upload Flow:**
1. Admin uploads tab-delimited `.txt` file
2. File validated for encoding (UTF-8) and format
3. Backup created in `/web/upload/`
4. Parser reads file line-by-line
5. Data inserted/updated in MySQL
6. Related tables updated (geolocale_taxon, proj_taxon, etc.)
7. Statistics recalculated
8. Upload logged in database

**Image Upload Flow:**
1. Images uploaded via web form
2. EXIF data extracted
3. Multiple sizes generated (thumbnail, medium, high-res)
4. Stored in DigitalOcean Spaces
5. Metadata stored in `image` table
6. Associated with specimen/taxon

## Data Flow

### 1. Taxonomic Data (Bolton Catalog)

**Source:** Bolton's World Catalog of Ants (via http://www.antwiki.org/)

**Process:**
1. Species list downloaded from AntWiki
2. File uploaded via `/upload.do` action
3. `SpeciesListUpload.java` parses file
4. Updates `taxon` and `proj_taxon` tables
5. Taxonomic hierarchy computed
6. Valid/invalid taxa marked

### 2. Specimen Data

**Source:** Museums, researchers, citizen scientists

**Format:** Tab-delimited text file with standardized fields (see DATABASE.md)

**Process:**
1. Curator prepares specimen file
2. Upload via web interface
3. `SpecimenUploadDb.java` processes file
4. Specimens inserted into `specimen` table
5. Geographic data populated in `geolocale_taxon`
6. Image associations created if images exist

### 3. Geographic Data

**Sources:**
- UN Country list
- User-uploaded specimen locality data
- AntWiki regional taxon lists

**Processing:**
- Country boundaries fetched from AntWiki
- Specimen coordinates validated
- Endemic/introduced species calculated
- Bioregion assignments computed

### 4. Image Data

**Sources:**
- Direct uploads by authorized users
- Bulk imports from collections

**Processing:**
- Images validated (format, size)
- EXIF data extracted (camera, date, GPS)
- Multiple sizes generated
- Watermarking applied (optional)
- Associated with specimens/taxa

## Deployment

### Development Environment

**Setup:**
```bash
# Use dev override
ln -sf docker-compose.dev.yml docker-compose.override.yml

# Start services
docker-compose up -d

# Deploy code changes
docker-compose exec antweb ant deploy
```

**Features:**
- Code mounted as volume (live updates)
- Debug logging enabled
- Local database snapshots

### Staging Environment

**Setup:**
```bash
# Use staging override
ln -sf docker-compose.stage.yml docker-compose.override.yml

# Build and start
docker-compose up -d --build
```

**Features:**
- Production-like configuration
- Smaller dataset for testing
- Staging domain (stage.antweb.org)

### Production Environment

**Setup:**
```bash
# Use production override
ln -sf docker-compose.prod.yml docker-compose.override.yml

# Build and deploy
docker-compose up -d --build
```

**Features:**
- Full dataset
- Optimized JVM settings
- Automated backups
- Monitoring and logging

**Key Services:**
- `antweb` - Main Java application (Tomcat)
- `mysql` - MySQL 5 database
- `caddy` - Web server with automatic TLS
- `api` - Python Flask API server

## Performance Considerations

### Database Optimization

- **Indexes:** Critical indexes on specimen_code, taxon_name, geolocale fields
- **Query Caching:** MySQL query cache enabled
- **Connection Pooling:** Tomcat connection pool (max 100 connections)
- **Partitioning:** Large tables partitioned by project/date

### Application Caching

- **In-Memory Cache:** Taxon hierarchy cached in application memory
- **Static Assets:** Served via Caddy with aggressive caching
- **Solr Cache:** Search results cached by Solr

### Image Delivery

- **CDN:** DigitalOcean Spaces CDN for global distribution
- **Lazy Loading:** Images loaded on-demand in UI
- **Responsive Images:** Multiple sizes for different devices
- **Compression:** Images optimized for web delivery

## Security

### Authentication

- **User Roles:** Administrator, Curator, Contributor, Public
- **Session Management:** Server-side sessions with timeout
- **Password Hashing:** Bcrypt for password storage

### Authorization

- **Role-Based Access Control (RBAC):** Different permissions by role
- **Project-Level Permissions:** Users assigned to specific projects
- **Upload Restrictions:** Only authenticated users can upload

### Data Protection

- **SQL Injection Prevention:** Parameterized queries throughout
- **XSS Prevention:** Input sanitization and output encoding
- **CSRF Protection:** Token-based CSRF protection on forms
- **HTTPS Only:** Caddy enforces HTTPS for all traffic

## Monitoring and Logging

### Application Logs

- **Location:** `/var/log/tomcat/` and `/logs/antwebInfo.log`
- **Levels:** DEBUG (dev), INFO (prod), WARN, ERROR
- **Rotation:** Daily rotation with compression

### Database Logs

- **Slow Query Log:** Queries > 2 seconds logged
- **Error Log:** MySQL errors logged
- **Binary Log:** For point-in-time recovery

### Monitoring

- **Health Checks:** `/health.do` endpoint
- **Uptime Monitoring:** External service (pingdom/uptimerobot)
- **Error Tracking:** Email alerts for critical errors

## Backup and Recovery

### Database Backups

- **Frequency:** Daily full backups
- **Location:** `/mnt/backup` (DigitalOcean Spaces)
- **Retention:** 30 days
- **Format:** Compressed SQL dump (`.sql.gz`)

### File Backups

- **Images:** Stored in DigitalOcean Spaces (durable, replicated)
- **Upload History:** Retained indefinitely in Spaces
- **Configuration:** Git repository serves as backup

### Recovery Process

1. Stop application containers
2. Restore database from backup
3. Verify data integrity
4. Restart containers
5. Run smoke tests

## Future Improvements

### Planned Enhancements

- **Modernization:** Migrate from Struts to Spring Boot
- **API Expansion:** GraphQL API for flexible queries
- **Search Improvements:** Elasticsearch for better full-text search
- **Mobile App:** Native iOS/Android applications
- **Real-Time Sync:** WebSocket updates for collaborative editing

### Technical Debt

- **Legacy Code:** Some Java code dates back 10+ years
- **Testing:** Limited unit test coverage
- **Documentation:** Incomplete inline documentation
- **Dependencies:** Some outdated library versions
