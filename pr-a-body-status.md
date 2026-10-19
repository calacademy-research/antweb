**Status (2026-10-18 18:23 PDT):** all checks passed on 7a3152d0; merged as 32c6a283. Production check pending.

First of two pull requests that tidy up the Curate page (`curate.do`), following the reviewed list of its tools. This one tightens checks on admin tools and removes tools that are unused, duplicated or no longer working. The second (merges, renames, a Technical page) follows once this one is live.

**Admin tools**
- Named queries (`query.do`) are for administrators, except the few that public or curator pages link to. Named queries only read data; the two clean-up queries that deleted rows are gone (the nightly clean-up does the same). Unknown `query.do` sub-actions answer 404.
- Museum Manager: Delete runs from the form button only, after a confirmation page.
- `upload.do` runs nothing from a plain link; Generate Statistics and Recent Desc Edits links show the usual confirmation page.
- Developer tools and debug output follow the admin role rather than specific login ids.
- The site warning banner keeps only simple formatting (the page-text allowlist).

**Removed**
- Orphan Manager and its pages; Type Status Manager; Cache Manager (page caching is off); the Countries and Bioregions lists (the public pages cover them); the developer Notes query and links page; the GBIF upload type; the "load all specimen files" upload tool.
- From the Curate page: the jump boxes and the Curious Query box (`description.do?antcatId=N` keeps working and is documented), Refresh, Count Crawls, Create a New Ancillary Page, the developer species list download and test hints, dead links (Materialize Images, Update Advanced Search, Update Specimen Status, Populate All), hard-coded developer crawl links and the staging-only boxes.

**Tests**
- `tests/e2e/test_curate_cleanup.py`: removed URLs answer 404 or "Action not found."; admin tools are refused to anonymous visitors and to the test curator (staging only, nothing changes); `description.do?antcatId=429011` opens a taxon page; static checks of the Curate page sources.
- `test_actions.py`: the removed mappings answer 404.
- `test/.../security/CurateCleanupTest.java` (new CI step): named queries read only, query access, admin role, removed upload tools, site warning cleaning.

**Docs**: `docs/guides/curate-page.md` (this branch also carries #169, the guide itself); catalog regenerated, removed rows kept with status `removed` (`build_catalog.py` now keeps them).

Rollback: revert this merge commit.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
