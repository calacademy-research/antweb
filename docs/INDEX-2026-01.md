# AntWeb Documentation

**Complete technical documentation for AntWeb - the world's largest online database of ant images, specimen records, and natural history information.**

**Live Site:** https://www.antweb.org  
**API:** https://api.antweb.org/v3/  
**Repository:** https://github.com/calacademy-research/antweb

---

## 📚 Documentation Index

This documentation suite provides comprehensive technical information about AntWeb's architecture, data model, API, and development processes.

### Planning & Roadmap

⭐ **NEW** - Strategic development planning

0. **[ROADMAP.md](ROADMAP.md)** - 20-Year Development Roadmap
   - Strategic modernization priorities
   - Phase-by-phase implementation plan
   - Technology decisions and trade-offs
   - Timeline and success metrics

0. **[PHASE1.md](PHASE1.md)** - Phase 1: Security & Foundation (Detailed)
   - Week-by-week implementation guide
   - Release management & versioning
   - Authentication system overhaul
   - Dependency updates and database migration
### Core Documentation

1. **[ARCHITECTURE.md](ARCHITECTURE.md)** - System Architecture Overview
   - Technology stack (Java, Python, MySQL, Docker)
   - High-level architecture and data flow
   - Core components and their interactions
   - Deployment configurations
   - Performance and security considerations

2. **[DATABASE.md](DATABASE.md)** - Database Schema & Data Model
   - Complete schema documentation for 50+ tables
   - Entity-relationship diagrams
   - Key tables: taxon, specimen, image, geolocale
   - Specimen upload file format (42 columns)
   - Query patterns and optimization
   - Migration system

3. **[API.md](API.md)** - RESTful API Documentation
   - All v3 API endpoints
   - Request/response formats
   - Authentication and rate limiting
   - Code examples (Python, JavaScript, R, cURL)
   - Error handling
   - Best practices

4. **[DEVELOPMENT.md](DEVELOPMENT.md)** - Developer Guide
   - Environment setup (Docker, database, file storage)
   - Project structure and organization
   - Development workflow
   - Testing and debugging
   - Code style guidelines
   - Git workflow and contribution process

---

## 🚀 Quick Start

### For New Developers

**Getting the code running:**

```bash
# 1. Clone repository
git clone git@github.com:calacademy-research/antweb.git
cd antweb

# 2. Set up Docker
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

# 3. Download database snapshot (get credentials from team)
scp root@antweb.org:/root/ant-currentDump.sql.gz ./

# 4. Load database
docker volume create antweb_database
# (see DEVELOPMENT.md for full commands)

# 5. Start services
ln -sf docker-compose.dev.yml docker-compose.override.yml
docker-compose up -d

# 6. Visit http://localhost
```

**Complete setup instructions:** See [DEVELOPMENT.md](DEVELOPMENT.md) → "Initial Setup"

### For API Users

**Making your first API request:**

```bash
# Get all Camponotus specimens
curl "https://api.antweb.org/v3/specimens?genus=camponotus&limit=10"

# Get a specific specimen
curl "https://api.antweb.org/v3/specimens/CASENT0106322"

# Search taxa
curl "https://api.antweb.org/v3/taxa?subfamily=formicinae&rank=genus"
```

**Complete API documentation:** See [API.md](API.md)

### For System Administrators

**Key deployment commands:**

```bash
# Staging deployment
ln -sf docker-compose.stage.yml docker-compose.override.yml
docker-compose up -d --build

# Production deployment  
ln -sf docker-compose.prod.yml docker-compose.override.yml
docker-compose up -d --build

# Database backup
docker-compose exec mysql mysqldump -uantweb -p ant | gzip > backup.sql.gz
```

**Complete deployment guide:** See [ARCHITECTURE.md](ARCHITECTURE.md) → "Deployment"

---

## 🏗️ System Overview

### Technology Stack at a Glance

| Layer | Technology |
|-------|-----------|
| **Backend** | Java (Struts), Apache Tomcat |
| **API** | Python (Flask), SQLAlchemy |
| **Database** | MySQL 5.x |
| **Search** | Apache Solr |
| **Frontend** | JSP, JavaScript, jQuery |
| **Infrastructure** | Docker, Docker Compose |
| **Web Server** | Caddy (automatic HTTPS) |
| **File Storage** | DigitalOcean Spaces (S3-compatible) |

**Detailed breakdown:** [ARCHITECTURE.md](ARCHITECTURE.md) → "Technology Stack"

### Data Scale

- **Specimens:** 500,000+ records
- **Taxa:** 50,000+ ant species and subspecies
- **Images:** 200,000+ high-resolution images (~1.6 TB)
- **Geographic Locations:** 5,000+ countries, states, and regions
- **API Requests:** ~1M per month

### Key Features

1. **Taxonomic Database** - Complete ant taxonomy based on Bolton's catalog
2. **Specimen Records** - Museum specimens with location, images, and metadata  
3. **Image Gallery** - Multiple views (head, dorsal, profile) for most species
4. **Geographic Distribution** - Species occurrence maps and endemic species tracking
5. **RESTful API** - Programmatic access to all data
6. **Curator Tools** - Web-based tools for data upload and management

---

## 📖 Understanding the Codebase

### Directory Structure

```
antweb/
├── src/org/calacademy/antweb/     # Java source code
│   ├── home/                       # Database access layer (TaxonDb, SpecimenDb, etc.)
│   ├── upload/                     # File upload processing
│   └── util/                       # Utilities
├── web/                            # Frontend (JSP templates, JS, CSS)
├── api/v3/                         # Python Flask API
├── db/                             # Database schema and migrations
├── WEB-INF/                        # Web application configuration
├── docker/                         # Docker configuration
└── doc/                            # Legacy documentation
```

**Detailed structure:** [DEVELOPMENT.md](DEVELOPMENT.md) → "Project Structure"

### Core Concepts

**1. Taxonomic Hierarchy**

```
Subfamily (e.g., "formicinae")
  └─ Genus (e.g., "camponotus")
      └─ Species (e.g., "camponotus pennsylvanicus")
          └─ Subspecies (e.g., "camponotus pennsylvanicus ferrugineus")
```

**2. Database Schema**

**Primary tables:**
- `taxon` - All ant taxa (subfamilies to subspecies)
- `specimen` - Individual museum specimens
- `image` - Specimen/taxon images
- `geolocale` - Geographic locations
- `geolocale_taxon` - Species distributions

**See:** [DATABASE.md](DATABASE.md) → "Core Entity-Relationship Model"

**3. Data Flow**

```
User Upload → Validation → Database Insert → Index Update → Web Display
      ↓
   Backup to S3
```

**See:** [ARCHITECTURE.md](ARCHITECTURE.md) → "Data Flow"

---

## 🔧 Common Tasks

### For Developers

**Add a new web page:**
1. Create JSP template in `web/`
2. Create Action class in `src/org/calacademy/antweb/`
3. Register in `WEB-INF/struts-config.xml`
4. Deploy: `docker-compose exec antweb ant deploy`

**See:** [DEVELOPMENT.md](DEVELOPMENT.md) → "Add a New Page"

**Add a database table:**
1. Create migration SQL in `db/upgrade/{version}/`
2. Create Java Db class in `src/org/calacademy/antweb/home/`
3. Apply migration and redeploy

**See:** [DEVELOPMENT.md](DEVELOPMENT.md) → "Add a Database Table"

**Add API endpoint:**
1. Create SQLAlchemy model in `api/v3/home/`
2. Add route to `api/v3/api.py`
3. Test with cURL

**See:** [DEVELOPMENT.md](DEVELOPMENT.md) → "Add an API Endpoint"

### For Curators

**Upload specimen data:**
1. Prepare tab-delimited file with 42 columns
2. Upload via web interface at `/upload.do`
3. Verify upload in logs

**See:** [DATABASE.md](DATABASE.md) → "Specimen Upload File Format"

**Upload images:**
1. Prepare images named `{code}_{shot}_{num}.jpg`
2. Upload via web interface
3. Images automatically processed and stored

**See:** [ARCHITECTURE.md](ARCHITECTURE.md) → "Image Upload Flow"

---

## 🤝 Contributing

We welcome contributions! Whether you're fixing bugs, adding features, improving documentation, or reporting issues.

### Getting Started

1. **Read the docs** - Especially [DEVELOPMENT.md](DEVELOPMENT.md)
2. **Set up environment** - Follow the Quick Start above
3. **Find an issue** - Check [GitHub Issues](https://github.com/calacademy-research/antweb/issues)
4. **Ask questions** - Contact antweb@calacademy.org

### Contribution Workflow

1. Fork the repository
2. Create a feature branch (`feature/my-new-feature`)
3. Make your changes
4. Write/update tests and documentation
5. Submit a pull request

**Detailed process:** [DEVELOPMENT.md](DEVELOPMENT.md) → "Contributing"

### What to Contribute

- **Bug fixes** - Fix issues, improve stability
- **New features** - Add functionality after discussion
- **Documentation** - Improve or add documentation
- **Tests** - Add unit or integration tests
- **Performance** - Optimize queries or code
- **UI/UX** - Improve user interface

---

## 📋 Documentation Standards

These documentation files follow these conventions:

- **Markdown format** - GitHub-flavored markdown
- **Code examples** - Runnable, tested examples
- **Clear structure** - Logical sections with clear headings
- **Cross-references** - Links between related sections
- **Version info** - Updated with major changes

### Maintaining Documentation

**When to update:**
- New features added
- API changes
- Database schema changes
- Deployment process changes
- Breaking changes

**How to update:**
1. Edit relevant .md file in `/docs`
2. Update "Last Updated" date
3. Include in pull request with code changes
4. Get documentation reviewed

---

## 📞 Support & Contact

### Getting Help

- **GitHub Issues:** https://github.com/calacademy-research/antweb/issues
- **Email:** antweb@calacademy.org
- **Live Site Help:** https://www.antweb.org/feedback.do

### Reporting Issues

When reporting bugs, please include:
- Description of the issue
- Steps to reproduce
- Expected vs actual behavior
- Environment (browser, OS, etc.)
- Screenshots if applicable

### Feature Requests

We're always looking to improve! Submit feature requests via GitHub Issues with:
- Clear description of the feature
- Use case / motivation
- Proposed implementation (optional)
- Willingness to contribute

---

## 📜 License

AntWeb data is provided under Creative Commons licenses (varies by content).
Check individual record license fields for specific terms.

**Code:** [License information to be added]

---

## 🙏 Acknowledgments

AntWeb is maintained by the California Academy of Sciences and supported by contributions from myrmecologists worldwide.

**Project Lead:** Brian Fisher  
**Development Team:** Past and present contributors listed in CONTRIBUTORS.md

Special thanks to:
- Bolton's World Catalog of Ants (taxonomic authority)
- AntWiki community
- Museum collections worldwide
- Individual photographers and researchers

---

## 📚 Additional Resources

### External Links

- **AntWiki:** https://www.antwiki.org
- **Bolton's Catalog:** References in AntWiki
- **California Academy of Sciences:** https://www.calacademy.org

### Related Projects

- **AntCat:** https://github.com/calacademy-research/antcat
- **AntWeb Mobile:** (if applicable)

### Academic Citations

If using AntWeb data in research, please cite:
```
Fisher, B.L. and AntWeb. [Year]. AntWeb. Version [X.X]. 
California Academy of Sciences. Available from: https://www.antweb.org
(accessed [date])
```

---

## 📋 Documentation Changelog

### Version 1.0 - January 2026
- Initial comprehensive documentation created
- Architecture, Database, API, and Development guides
- Consolidated legacy documentation
- Added quick start guides and examples

---

**Last Updated:** January 2026  
**Documentation Version:** 1.0  
**AntWeb Version:** 8.105+

---

## Quick Navigation

| I want to... | Go to... |
|--------------|----------|
| Understand the system architecture | [ARCHITECTURE.md](ARCHITECTURE.md) |
| Learn the database schema | [DATABASE.md](DATABASE.md) |
| Use the API | [API.md](API.md) |
| Set up development environment | [DEVELOPMENT.md](DEVELOPMENT.md) |
| Deploy to production | [ARCHITECTURE.md](ARCHITECTURE.md) → Deployment |
| Upload specimen data | [DATABASE.md](DATABASE.md) → Specimen Upload |
| Add a new feature | [DEVELOPMENT.md](DEVELOPMENT.md) → Development Workflow |
| Fix a bug | [DEVELOPMENT.md](DEVELOPMENT.md) → Debugging |
| Optimize performance | [ARCHITECTURE.md](ARCHITECTURE.md) → Performance |
| Report an issue | [GitHub Issues](https://github.com/calacademy-research/antweb/issues) |
