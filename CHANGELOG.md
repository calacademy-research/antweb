# Changelog

All notable changes to AntWeb will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

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
