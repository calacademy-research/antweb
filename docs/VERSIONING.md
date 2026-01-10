# AntWeb Versioning Policy

## Overview

AntWeb follows [Semantic Versioning 2.0](https://semver.org/) to provide clear, predictable version numbering that communicates the nature of changes in each release.

**Current Version:** See [`.version`](../.version) file in repository root

---

## Version Format

### MAJOR.MINOR.PATCH

Given a version number **MAJOR.MINOR.PATCH**, increment the:

1. **MAJOR** version when you make incompatible API changes or major architectural updates
2. **MINOR** version when you add functionality in a backward-compatible manner
3. **PATCH** version when you make backward-compatible bug fixes or security patches

---

## Version Number Examples

### Patch Releases (Bug Fixes)
```
9.0.0 → 9.0.1: Security patch for authentication
9.0.1 → 9.0.2: Bug fix in specimen upload
9.0.2 → 9.0.3: Performance improvement in search
```

**When to use:**
- Security patches
- Bug fixes
- Performance improvements
- Documentation updates
- No new features
- No breaking changes

---

### Minor Releases (New Features)
```
9.0.3 → 9.1.0: New authentication system (Phase 1 complete)
9.1.0 → 9.2.0: Complete REST API (Phase 2)
9.2.0 → 9.3.0: Background job processing
```

**When to use:**
- New features added
- API endpoints added (backward-compatible)
- New functionality
- Database schema additions (non-breaking)
- Deprecated features (marked but still functional)

---

### Major Releases (Breaking Changes)
```
9.3.0 → 10.0.0: API v3 deprecated, API v4 required
10.0.0 → 11.0.0: New frontend requires different authentication
11.0.0 → 12.0.0: Struts removed, Spring Boot only
```

**When to use:**
- Breaking API changes
- Removed deprecated features
- Database schema changes requiring migration
- Major architecture changes
- Authentication/authorization changes requiring user action
- Incompatible with previous version

---

## Release Schedule

### Patch Releases
- **Frequency:** As needed
- **Planning:** Immediate for critical security issues
- **Testing:** Regression tests required
- **Downtime:** Minimal (< 5 minutes)

### Minor Releases
- **Frequency:** Monthly during active development
- **Planning:** 1 week notice to users
- **Testing:** Full test suite required
- **Downtime:** Brief (< 30 minutes)

### Major Releases
- **Frequency:** Per roadmap milestones (see [ROADMAP.md](ROADMAP.md))
- **Planning:** 1 month notice, migration guides provided
- **Testing:** Extensive beta testing required
- **Downtime:** Scheduled maintenance window

---

## Version Locations

### In Code
```
Repository Root:
/.version                    (Single source of truth)

Java (Application Startup):
ServletContext.getAttribute("version")

Python API:
GET /api/v3/version          (JSON response)

Website:
Footer of all pages          (Visible to users)
/about.do page              (Full version information)
Admin dashboard             (System information)
```

### On GitHub
```
Releases:
https://github.com/calacademy-research/antweb/releases

Tags:
git tag -l                   (List all version tags)
```

---

## Version Metadata

### Version Tag Format
```bash
# Git tags use 'v' prefix
git tag -a v9.0.0 -m "Release 9.0.0: Establish release management"

# Examples:
v9.0.0      # Major release
v9.1.0      # Minor release  
v9.1.1      # Patch release
```

### Release Naming
```
Release Title Format:
"Release [VERSION]: [BRIEF DESCRIPTION]"

Examples:
"Release 9.0.0: Establish Release Management"
"Release 9.1.0: New Authentication System"
"Release 9.1.1: Security Patch for CVE-2026-XXXXX"
```

---

## Special Cases

### Pre-Release Versions
For testing and staging:
```
9.1.0-alpha.1    # Alpha release 1
9.1.0-beta.1     # Beta release 1
9.1.0-rc.1       # Release candidate 1
```

**Not used in production.** Only for staging/testing environments.

### Development Builds
For development branches:
```
9.1.0-dev        # Development build
9.1.0-snapshot   # Snapshot build
```

**Never deployed to production.**

---

## Migration Notes

### From Previous Versioning (Pre-2026)

**Old System:**
- Version tracking in `/doc/version.txt`
- Release notes in `/doc/releaseNotes.txt` and `/doc/release.txt`
- No consistent version numbering
- No semantic meaning to version numbers

**New System (Starting v9.0.0):**
- Semantic versioning
- GitHub Releases for all releases
- CHANGELOG.md in repository
- Automated version display on website

**Historical Releases:**
All releases prior to v9.0.0 are documented in:
- `/doc/release.txt` (complete history)
- `/doc/releaseNotes.txt` (detailed notes)

These files are preserved for historical reference but no longer updated.

---

## Deprecation Policy

### Announcing Deprecation
When deprecating features:
1. Announce in **MINOR** release
2. Mark feature as deprecated in code
3. Update documentation
4. Provide migration guide

### Removing Deprecated Features
Deprecated features are removed in:
1. Next **MAJOR** release (minimum 3 months after deprecation)
2. With clear migration path documented
3. After user notification

### Example Deprecation Timeline
```
v9.0.0: Feature X introduced
v9.5.0: Feature X deprecated, Feature Y introduced (replacement)
        Warning: "Feature X will be removed in v10.0.0"
v10.0.0: Feature X removed (breaking change)
```

---

## API Versioning

### API Versions vs Application Versions

**Application Version** (this document): Overall AntWeb release version
**API Version**: Independent versioning for API endpoints

```
Application: v9.1.0
API: v3.1

Application: v10.0.0  (major change to web UI)
API: v3.2            (backward-compatible API update)
```

API versioning follows its own semantic versioning, documented in [API.md](API.md).

---

## Version History

### Version 9.x (Current)
- **9.0.0** (2026-01-XX): Established release management and semantic versioning
- Future releases documented in [CHANGELOG.md](../CHANGELOG.md)

### Version 8.x (Legacy)
- See `/doc/release.txt` for complete history
- Database migrations in `/db/upgrade/8.*/`

---

## Automated Version Management

### Build Tools
Version is automatically read from `.version` file during:
- Application startup (Java ServletContext)
- API initialization (Python Flask)
- Docker image build
- Deployment scripts

### CI/CD Integration
```yaml
# Example GitHub Actions workflow
- name: Get version
  run: echo "VERSION=$(cat .version)" >> $GITHUB_ENV

- name: Build with version
  run: ./build.sh ${{ env.VERSION }}
```

---

## Developer Guidelines

### Before Release
1. Update `.version` file
2. Update `CHANGELOG.md`
3. Tag release in Git
4. Create GitHub Release
5. Deploy to staging
6. Test thoroughly
7. Deploy to production
8. Announce release

### Version Bump Commands
```bash
# Patch release (9.0.0 → 9.0.1)
echo "9.0.1" > .version

# Minor release (9.0.1 → 9.1.0)
echo "9.1.0" > .version

# Major release (9.1.0 → 10.0.0)
echo "10.0.0" > .version
```

See [RELEASING.md](RELEASING.md) for complete release process.

---

## Questions & Support

- **Version Questions:** Open GitHub issue with `versioning` label
- **Release Process:** See [RELEASING.md](RELEASING.md)
- **Roadmap:** See [ROADMAP.md](ROADMAP.md)
- **Changelog:** See [CHANGELOG.md](../CHANGELOG.md)

---

## References

- [Semantic Versioning 2.0](https://semver.org/)
- [Keep a Changelog](https://keepachangelog.com/)
- [AntWeb Roadmap](ROADMAP.md)
- [AntWeb Release Process](RELEASING.md)

---

**Last Updated:** January 2026  
**Next Review:** June 2026
