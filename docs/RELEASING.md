# AntWeb Release Process

**Official guide for creating and publishing AntWeb releases**

This document describes the complete process for creating, testing, and publishing AntWeb releases. It ensures consistent, professional releases that follow our versioning policy.

---

## Table of Contents

1. [Pre-Release Checklist](#pre-release-checklist)
2. [Release Types](#release-types)
3. [Creating a Release](#creating-a-release)
4. [Post-Release Tasks](#post-release-tasks)
5. [Hotfix Process](#hotfix-process)
6. [Rollback Procedure](#rollback-procedure)

---

## Pre-Release Checklist

Before creating any release, ensure all of these items are complete:

### Code Quality
- [ ] All tests passing in development environment
- [ ] All tests passing in staging environment
- [ ] Code review completed for all changes
- [ ] No known critical bugs
- [ ] Documentation updated for new features

### Version Management
- [ ] `.version` file updated with new version number
- [ ] `CHANGELOG.md` updated with all changes
- [ ] Breaking changes clearly documented
- [ ] Migration guides written (if needed)

### Database
- [ ] Database migrations tested in staging
- [ ] Rollback scripts prepared and tested
- [ ] Backup procedures verified
- [ ] Data integrity checks completed

### Security
- [ ] Security vulnerabilities addressed
- [ ] Dependency security scan clean
- [ ] Authentication/authorization tested
- [ ] No credentials in code

### Testing
- [ ] Manual testing completed
- [ ] Performance testing completed (if relevant)
- [ ] Cross-browser testing (for UI changes)
- [ ] API endpoint testing (for API changes)

---

## Release Types

### Patch Release (X.Y.Z → X.Y.Z+1)

**When to use:**
- Bug fixes
- Security patches
- Performance improvements
- Documentation updates
- No new features
- No breaking changes

**Example:** 9.0.0 → 9.0.1

**Testing required:** Regression tests

**Deployment window:** Can deploy any time (minimal risk)

---

### Minor Release (X.Y.Z → X.Y+1.0)

**When to use:**
- New features (backward-compatible)
- API additions (no breaking changes)
- Database schema additions (additive only)
- Enhanced functionality
- Deprecation warnings (features still work)

**Example:** 9.0.1 → 9.1.0

**Testing required:** Full test suite

**Deployment window:** Scheduled maintenance (brief downtime acceptable)

---

### Major Release (X.Y.Z → X+1.0.0)

**When to use:**
- Breaking API changes
- Database schema changes requiring migration
- Removed deprecated features
- Major architecture changes
- Authentication changes requiring user action

**Example:** 9.5.0 → 10.0.0

**Testing required:** Extensive testing, beta period

**Deployment window:** Scheduled maintenance (extended downtime acceptable)

**Notice period:** Minimum 1 month advance notice to users

---

## Creating a Release

### Step 1: Determine Version Number

Follow [Semantic Versioning](VERSIONING.md) rules:

```bash
# Current version
cat .version
# Example output: 9.0.0

# Decide new version based on changes:
# - Patch: 9.0.1 (bug fixes only)
# - Minor: 9.1.0 (new features, backward-compatible)
# - Major: 10.0.0 (breaking changes)
```

---

### Step 2: Update Version File

```bash
# Edit .version file
echo "9.1.0" > .version

# Verify
cat .version
```

---

### Step 3: Update CHANGELOG.md

Add new section at the top of `CHANGELOG.md`:

```markdown
## [9.1.0] - 2026-02-15

### Added
- New authentication system with bcrypt password hashing
- Password reset functionality via email
- Two-factor authentication support (optional)

### Changed
- Updated all Java dependencies to latest versions
- Improved login page UI and error messages

### Fixed
- Fixed session timeout bug (#123)
- Corrected specimen search pagination issue (#145)

### Security
- Upgraded to bcrypt for password hashing
- Fixed authentication vulnerability CVE-2026-XXXXX

### Deprecated
- Old login system will be removed in v10.0.0

### Migration Notes
- All users will receive password reset emails
- Old passwords will no longer work after upgrade
- See detailed migration guide: docs/migrations/v9.1.0.md
```

**Categories to use:**
- **Added** - New features
- **Changed** - Changes to existing functionality
- **Deprecated** - Soon-to-be removed features
- **Removed** - Removed features (major releases only)
- **Fixed** - Bug fixes
- **Security** - Security patches

---

### Step 4: Commit Version Changes

```bash
# Stage changes
git add .version CHANGELOG.md

# Commit with conventional commit message
git commit -m "chore: Release 9.1.0

- Update version to 9.1.0
- Update CHANGELOG with release notes"

# Push to master
git push origin master
```

---

### Step 5: Create Git Tag

```bash
# Create annotated tag
git tag -a v9.1.0 -m "Release 9.1.0: New Authentication System"

# Push tag to GitHub
git push origin v9.1.0
```

**Tag naming convention:**
- Always prefix with `v`
- Match version in `.version` file exactly
- Examples: `v9.0.0`, `v9.1.0`, `v10.0.0`

---

### Step 6: Create GitHub Release

1. **Navigate to releases page:**
   - Go to https://github.com/calacademy-research/antweb/releases/new
   - Or: Repository → Releases → "Draft a new release"

2. **Select tag:**
   - Choose the tag you just created (e.g., `v9.1.0`)

3. **Release title:**
   ```
   Release 9.1.0: New Authentication System
   ```

4. **Release description:**
   ```markdown
   ## Release 9.1.0: New Authentication System
   
   This release introduces a modern authentication system with improved security and user experience.
   
   ### What's New
   
   **Authentication System**
   - Modern password hashing using bcrypt
   - Password reset functionality via email
   - Optional two-factor authentication
   - Improved session management
   
   **Security Improvements**
   - Fixed authentication vulnerability CVE-2026-XXXXX
   - Upgraded password storage to bcrypt with 12 rounds
   - Enhanced audit logging for security events
   
   **Dependency Updates**
   - Updated all Java dependencies to latest stable versions
   - Updated Spring Security to 6.2.1
   - Updated MySQL connector to 8.0.33
   
   ### Breaking Changes
   
   None. This release is fully backward compatible.
   
   ### Migration Notes
   
   **For Users:**
   - You will receive a password reset email
   - Please reset your password after upgrade
   - Old passwords will no longer work
   
   **For Developers:**
   - Review new authentication API in docs/API.md
   - Update any code that directly accesses login table
   - See migration guide: docs/migrations/v9.1.0.md
   
   ### Files Changed
   
   - Updated authentication system (20 files)
   - Updated dependencies (build.xml, requirements.txt)
   - Added migration scripts (db/migrations/v9.1.0/)
   - Updated documentation (docs/)
   
   ### Documentation
   
   - Release Notes: [CHANGELOG.md](https://github.com/calacademy-research/antweb/blob/master/CHANGELOG.md)
   - Migration Guide: [docs/migrations/v9.1.0.md](https://github.com/calacademy-research/antweb/blob/master/docs/migrations/v9.1.0.md)
   - Authentication Docs: [docs/AUTHENTICATION.md](https://github.com/calacademy-research/antweb/blob/master/docs/AUTHENTICATION.md)
   
   ### Testing
   
   Tested on:
   - Development environment
   - Staging environment (production copy)
   - Multiple browsers (Chrome, Firefox, Safari)
   - API endpoints verified
   
   ### Contributors
   
   Thank you to everyone who contributed to this release.
   
   ---
   
   **Full Changelog**: https://github.com/calacademy-research/antweb/compare/v9.0.0...v9.1.0
   ```

5. **Publish settings:**
   - Leave "Set as a pre-release" unchecked (unless it's a pre-release)
   - "Set as the latest release" will be automatic

6. **Click "Publish release"**

---

### Step 7: Deploy to Staging

```bash
# Deploy to staging environment
ssh antweb-staging
cd /path/to/antweb
git fetch --tags
git checkout v9.1.0
docker-compose down
docker-compose up -d
```

**Verify staging deployment:**
- [ ] Application starts successfully
- [ ] Version displays correctly (footer, about page)
- [ ] All features work as expected
- [ ] Database migrations applied correctly
- [ ] No errors in logs

---

### Step 8: Deploy to Production

**Only after staging verification is complete.**

```bash
# Deploy to production
ssh antweb-production
cd /path/to/antweb

# Backup database before deployment
./scripts/backup-database.sh

# Deploy
git fetch --tags
git checkout v9.1.0
docker-compose down
docker-compose up -d

# Verify deployment
docker-compose ps
docker-compose logs -f
```

**Post-deployment checks:**
- [ ] Application running
- [ ] Version displays correctly
- [ ] Database migrations successful
- [ ] Critical features functional
- [ ] No errors in logs
- [ ] Performance acceptable

**Monitor for 24 hours:**
- Watch error logs
- Monitor performance metrics
- Watch user reports
- Be ready to rollback if needed

---

## Post-Release Tasks

### Update Documentation

- [ ] Update version on website (if not automatic)
- [ ] Update API documentation (if API changed)
- [ ] Update README if needed

### Communication

**Announce the release:**

1. **Email announcement** (for significant releases):
   ```
   Subject: AntWeb Release 9.1.0: New Authentication System
   
   Dear AntWeb users,
   
   We've released AntWeb version 9.1.0 with a new authentication system 
   and improved security.
   
   What's new:
   - Modern password hashing
   - Password reset functionality
   - Improved security
   
   Action required:
   - You will receive a password reset email
   - Please reset your password at your convenience
   
   Full release notes:
   https://github.com/calacademy-research/antweb/releases/tag/v9.1.0
   
   Questions? Contact support@antweb.org
   
   Best regards,
   The AntWeb Team
   ```

2. **Social media** (if applicable):
   - Twitter/X announcement
   - Blog post for major releases

3. **Update status page** (if you have one):
   - Mark maintenance window complete
   - Update current version

### Archive

- [ ] Archive release artifacts (if any)
- [ ] Document any deployment issues encountered
- [ ] Update deployment runbook if needed

---

## Hotfix Process

**For urgent security fixes or critical bugs.**

### When to Use Hotfix Process

- Critical security vulnerability
- Data loss bug
- Application crash affecting all users
- Cannot wait for normal release cycle

### Hotfix Steps

1. **Create hotfix branch from latest release tag:**
   ```bash
   git checkout v9.1.0
   git checkout -b hotfix/9.1.1
   ```

2. **Fix the issue:**
   ```bash
   # Make minimal changes to fix issue
   # Add tests
   # Update CHANGELOG.md
   ```

3. **Test thoroughly:**
   ```bash
   # Run all tests
   # Test in staging
   # Verify fix works
   ```

4. **Bump version (patch only):**
   ```bash
   echo "9.1.1" > .version
   ```

5. **Commit and merge:**
   ```bash
   git add .
   git commit -m "fix: Critical authentication bug (CVE-2026-XXXXX)"
   git checkout master
   git merge hotfix/9.1.1
   git push origin master
   ```

6. **Tag and release:**
   ```bash
   git tag -a v9.1.1 -m "Hotfix 9.1.1: Critical authentication bug"
   git push origin v9.1.1
   ```

7. **Create GitHub release** (follow Step 6 above)

8. **Deploy immediately to production** (follow Step 8 above)

9. **Announce:**
   ```
   Subject: [URGENT] AntWeb Security Update 9.1.1
   
   A critical security vulnerability has been fixed in version 9.1.1.
   
   Issue: Authentication bypass vulnerability
   Severity: Critical
   Action: Automatic update deployed
   
   Details: https://github.com/calacademy-research/antweb/releases/tag/v9.1.1
   ```

---

## Rollback Procedure

**If a release causes critical issues:**

### Immediate Rollback

```bash
# SSH to production
ssh antweb-production
cd /path/to/antweb

# Checkout previous version
git checkout v9.0.0

# Restart application
docker-compose down
docker-compose up -d
```

### Database Rollback

**If database migrations were applied:**

```bash
# Run rollback script
./db/migrations/v9.1.0/rollback.sql

# Or restore from backup
./scripts/restore-database.sh backup-2026-02-15.sql
```

### Communication

```
Subject: AntWeb Rolled Back to Version 9.0.0

Due to unexpected issues, we've rolled back to version 9.0.0.

The issue will be investigated and a new release will be 
published after thorough testing.

Apologies for any inconvenience.
```

### Post-Rollback

- [ ] Document what went wrong
- [ ] Fix issue in development
- [ ] Add tests to prevent recurrence
- [ ] Test extensively in staging
- [ ] Re-release when ready

---

## Release Checklist Template

**Copy this for each release:**

```markdown
## Release X.Y.Z Checklist

### Pre-Release
- [ ] All tests passing
- [ ] Code review complete
- [ ] .version updated
- [ ] CHANGELOG.md updated
- [ ] Migration guides written
- [ ] Security scan clean
- [ ] Staging tested

### Release
- [ ] Committed version changes
- [ ] Created git tag vX.Y.Z
- [ ] Pushed tag to GitHub
- [ ] Created GitHub Release
- [ ] Deployed to staging
- [ ] Verified staging
- [ ] Deployed to production
- [ ] Verified production

### Post-Release
- [ ] Monitored for 24 hours
- [ ] Announced release
- [ ] Updated documentation
- [ ] Archived artifacts
- [ ] Updated roadmap status
```

---

## Version Comparison Commands

**Useful commands for release preparation:**

```bash
# See all changes since last release
git log v9.0.0..HEAD --oneline

# See files changed since last release
git diff v9.0.0..HEAD --name-only

# See detailed diff since last release
git diff v9.0.0..HEAD

# List all tags
git tag -l

# Show tag details
git show v9.1.0
```

---

## Common Issues and Solutions

### Issue: Tag already exists

```bash
# Delete local tag
git tag -d v9.1.0

# Delete remote tag (careful!)
git push origin :refs/tags/v9.1.0

# Recreate tag
git tag -a v9.1.0 -m "Release 9.1.0"
git push origin v9.1.0
```

### Issue: Wrong version in .version file

```bash
# Fix version
echo "9.1.0" > .version

# Amend last commit
git add .version
git commit --amend --no-edit

# Force push (if not yet released publicly)
git push origin master --force
```

### Issue: Forgot to update CHANGELOG

```bash
# Edit CHANGELOG.md
nano CHANGELOG.md

# Create new commit
git add CHANGELOG.md
git commit -m "docs: Update CHANGELOG for 9.1.0"

# Update release notes on GitHub manually
```

---

## References

- [Semantic Versioning](https://semver.org/)
- [Keep a Changelog](https://keepachangelog.com/)
- [Conventional Commits](https://www.conventionalcommits.org/)
- [AntWeb Versioning Policy](VERSIONING.md)
- [AntWeb Development Roadmap](ROADMAP.md)

---

## Questions and Support

**Release process questions:**
- Open GitHub issue with `release` label
- Contact development team

**Emergency issues during release:**
- Contact: support@antweb.org
- Emergency rollback: Follow rollback procedure above

---

**Last Updated:** January 2026  
**Next Review:** July 2026  
**Maintained by:** AntWeb Development Team
