# Changelog

All notable changes to AntWeb will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

---

## [9.2.0] - 2026-10-17

AntWeb's 24th birthday release (AntWeb launched 17 October 2002).

### Added
- AntWeb on phones: one responsive layout for all devices; the old user-agent mobile layout is removed (#134).
- Page editor TinyMCE 8 for all curator page text, with safety checks: no save before the stored text has loaded, no accidental empty saves (separate confirmed Clear), conflict check, refused saves keep the text, browser drafts, login keep-alive (#128, #131).
- Field history with differences and one-click restore for curators (#134).
- "Keep me logged in" (30 days) and password reset by email (#112, #116).
- Curator files page for each curator's own folder (#135).
- Version stamps on scripts and stylesheets so browsers never keep an old copy after a release (#130).
- Real-browser tests (Playwright) in the end-to-end suite (#130, #134).
- Image Uploader sends one file per request (imageUploadBatch.do) with per-file progress and retries; any number of images per upload behind Cloudflare's 100 MB request limit (#137).
- Previous determinations: column `specimen.previous_determinations`, from TaxonWorks `previousIdentifications` and the optional `previousDeterminations` column in specimen text uploads; shown on specimen pages (#138).
- Link tests in the end-to-end suite: home page (AntWeb and outside links), upload reports, specimen and taxon pages (#137, #138).

### Changed
- Tomcat runs as a non-root user (#134).
- TaxonWorks nightly upload waits for the fresh archive, so it includes the previous day's edits (#114).
- Small overview maps use Leaflet and OpenStreetMap; Google Maps removed (#109).
- jQuery 3.7, jQuery UI 1.13 (#124).
- Favicon visible in dark mode (#131).
- Project settings form links to the page editor; old page-text boxes removed (#132).
- Specimen and taxon pages: upload-line, extra-fields and Worldants-line links fixed (pointed at showLog.do, renamed in 2023); Default Specimen linked; whole-upload search and file links removed from the specimen admin block (#138).
- Plazi: link to a live TreatmentBank search instead of the stored 2012 table; Ant Course link to www.antcourse.org (#138).
- Participate page updated with the 2026 specimen template (new previousDeterminations column); Curate page links fixed; field guide, compare and map-of-results links show a login message to visitors (#140).
- Group, groups and curator pages link upload reports in their current folder (dead since 2019) (#143).

### Removed
- Unused editors, test pages, actions, servlets and classes (#118, #132, #135).
- HOL (hol.osu.edu, offline) links; AntShare from the menu (#138).
- AntShare page, unused URL aliases, three unlinked PDFs; direct requests to page templates (*.jsp) refused except a short allow-list (#140).
- Flickr, Geonames, Geonet and Google APIs geodata importers; the geolocale edit page keeps one centroid and one bounding-box field (#143).

### Security
- Several rounds of security fixes from two audits (#99-#101, #107, #111, #113, #115, #117, #119, #126, #132, #134-#137, #139, #143).

---

## [9.1.0] - 2026-10-16

AntWeb's software brought up to current, supported versions, and several long-standing issues fixed. Contributors: Sathya (Sathyavrv), Gladrin (Gladrin22), Antony (antony-jr), Brian Fisher (bpescador).

### Added
- Nightly upload of CAS specimens from TaxonWorks (#27, #89).
- Upload reports show whether an upload was made from the upload page or by the nightly job (#93).
- Specimen List Lookup for curators (#61); codes not in AntWeb (and codes containing spaces) are listed separately and data is still returned for the rest (#64, #92).
- Specimen validation tool (#55).
- Read-only API endpoint for specimen measurements, `/v3.1/measurements` (#65).
- Bot check on Create Account (#84).
- End-to-end test suite that runs against staging on every pull request and daily (#76, #82).

### Changed
- Platform: Java 21 (#70), Tomcat 11 (#86), Struts1 Reloaded (#81), MySQL 8.4 (#71), API on Python 3.12 and Flask 3 (#67, #74), updated Java libraries (#69, #73, #79).
- Specimen uploads move records of a synonymized genus to its current valid genus; named species take AntCat's current combination, e.g. *Wasmannia auropunctata* to *Allomerus auropunctatus* (#95, #97).
- Login and account security improvements; existing passwords keep working (#83, #87).
- The API reads the database with a read-only login (#88).
- Curator and admin actions that change data require login (#75).

### Fixed
- Nightly jobs that rebuild country, region, museum, bioregion, image and type counts run in full again; since May they had stopped after 5 minutes (#62, #90).
- Species uploaded before AntCat published them get their Taxonomic History and distribution with the next nightly AntCat load (#63, #91).
- Uploads no longer create genera from a subfamily written in parentheses (#94, #96).
- Duplicate genus rows that broke subfamily pages (#58); upload records with spaces in taxonomic names are rejected (#59).
- Specimen upload fixes and clearer messages for long uploads (#32, #36, #37, #50).
- Recent Images species links (#68); curator pages no longer show login details to visitors (#80).

### Removed
- Unused upload servlets, dead page mappings and the broken home page editor (#78, #85).

---

## [9.0.0] - 2026-01-10

**Release 9.0.0: Establish Release Management**

This version marks the beginning of structured release management and semantic versioning for AntWeb. It establishes the foundation for transparent, predictable releases and sets the stage for Phase 1 modernization efforts.

### Added
- `.version` file for version tracking
- `CHANGELOG.md` for release documentation
- `docs/VERSIONING.md` with versioning policy
- `docs/RELEASING.md` with release process (coming soon)
- Version API endpoint at `/api/v3/version`
- GitHub Releases integration
- Links to releases and roadmap on website

### Changed
- Adopted Semantic Versioning 2.0
- Release process now documented and standardized
- Version information now visible to users

### Documentation
- Added comprehensive [development roadmap](docs/ROADMAP.md) with 20-year plan
- Added detailed [Phase 1 implementation guide](docs/PHASE1.md)
- Added [complete technical documentation](docs/) suite (10 files, 80+ pages)

### Migration Notes
- This is the first release using semantic versioning
- Previous releases are documented in `/doc/release.txt` and `/doc/releaseNotes.txt`
- No breaking changes from previous version
- No user action required

---

## Historical Releases

For releases prior to v9.0.0, see:
- `/doc/release.txt` - Complete release history
- `/doc/releaseNotes.txt` - Detailed release notes

### Version 8.x (2015-2025)
Last recorded version before structured versioning. Database schema version 8.105.

Key features in 8.x series:
- Docker containerization
- API v3 development
- Specimen upload improvements
- Image processing enhancements
- Database optimizations

For complete history, see `/doc/release.txt`.

---

## Changelog Format Guide

This file follows the [Keep a Changelog](https://keepachangelog.com/) format with these sections:

### Section Types
- **Added** - New features
- **Changed** - Changes to existing functionality
- **Deprecated** - Soon-to-be removed features
- **Removed** - Removed features
- **Fixed** - Bug fixes
- **Security** - Security patches

### Example Entry
```markdown
## [9.1.0] - 2026-03-15

### Added
- New authentication system with bcrypt password hashing
- Two-factor authentication support
- Password reset functionality via email

### Changed
- Updated all dependencies to latest versions
- Improved login page UI

### Security
- Fixed authentication vulnerability CVE-2026-XXXXX
- Upgraded to MySQL 8.0 for better security

### Deprecated
- Old login system will be removed in v10.0.0

### Migration Notes
- All users will receive password reset email
- Old passwords will no longer work after upgrade
- See migration guide: docs/migrations/v9.1.0.md
```

---

## Links

- **GitHub Releases:** https://github.com/calacademy-research/antweb/releases
- **Development Roadmap:** [docs/ROADMAP.md](docs/ROADMAP.md)
- **Phase 1 Plan:** [docs/PHASE1.md](docs/PHASE1.md)
- **Versioning Policy:** [docs/VERSIONING.md](docs/VERSIONING.md)
- **Release Process:** [docs/RELEASING.md](docs/RELEASING.md)

---

**Maintained by:** California Academy of Sciences  
**Last Updated:** October 2026
