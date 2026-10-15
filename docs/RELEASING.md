# Releasing AntWeb

A release is cut when a meaningful set of changes is live on www.antweb.org (not for every merge).

1. Pick the version (see [VERSIONING.md](VERSIONING.md)): usually MINOR for new features, PATCH for fixes only.
2. In one pull request:
   - set the number in `.version` and `web/documentation/releaseNum.jsp` (CI checks they match; the site header shows it);
   - move the `[Unreleased]` items in `CHANGELOG.md` under the new version and date.
3. Merge (deploys to production).
4. Create the GitHub release, tag `vX.Y.Z` on master. Start the notes with a short plain-language summary, then let GitHub list the merged pull requests:
   ```
   gh release create vX.Y.Z --target master --title "AntWeb X.Y.Z" --notes-file notes.md --generate-notes
   ```
The version in the site header links to https://github.com/calacademy-research/antweb/releases.

The previous, much longer release process (January 2026) was never used; it is in the git history of this file.
