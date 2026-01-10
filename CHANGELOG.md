# Changelog

All notable changes to AntWeb will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- Semantic versioning system (SemVer 2.0)
- GitHub Releases integration
- Version display on website (footer, about page, admin dashboard)
- API endpoint `/api/v3/version` for programmatic version checking
- This CHANGELOG file
- Comprehensive versioning documentation ([docs/VERSIONING.md](docs/VERSIONING.md))

### Changed
- Migrated from text-based release notes to structured changelog
- Established formal release management process

---

## [9.0.0] - 2026-01-XX

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
**Last Updated:** January 2026
