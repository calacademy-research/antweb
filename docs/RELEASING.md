# Releasing AntWeb

A release is cut when a meaningful set of changes is live on www.antweb.org (not for every merge).

1. Pick the version (see [VERSIONING.md](VERSIONING.md)): usually MINOR for new features, PATCH for fixes only.
2. In one pull request:
   - set the number in `.version` and `web/documentation/releaseNum.jsp` (CI checks they match; the site header shows it);
   - move the `[Unreleased]` items in `CHANGELOG.md` under the new version and date.
   - write the release notes in `release-notes/X.Y.Z.md`: a short plain-language summary.
3. Merge. Production deploys, and the Release workflow publishes `vX.Y.Z` with those notes plus GitHub's list of merged pull requests.

The version in the site header links to https://github.com/calacademy-research/antweb/releases.

The previous, much longer release process (January 2026) was never used; it is in the git history of this file.
