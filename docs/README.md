# AntWeb documentation: start here

AntWeb (https://www.antweb.org) is the California Academy of Sciences' database of ant specimens, images and natural history. This folder is being rebuilt as one documented base for the code: what every page and job does, who may use it, and which test proves it works.

## Current

| Document | What it is |
|---|---|
| [architecture-overview.md](architecture-overview.md) | **Architecture overview.** Servers, request path (Cloudflare, Caddy, Tomcat, MySQL, API), containers and mounts, settings files in `etc/`, scheduled jobs and the nightly sets, email, the AntCat feed, monitoring, and the deploy pipeline. Two diagrams. |
| [runbook.md](runbook.md) | **Operations runbook.** Deploy and rollback, restart, logs, common checks, backups and restore to staging, what each alert means, weekly reboot, restart checker and watchdog, settings switches, emergencies (site down, disk full, nightly stuck, upload stuck). |
| [catalog/actions.md](catalog/actions.md) | **Function catalog.** One row per Struts action (`*.do`), per sub-action (`?action=...`) and per API v3.1 route: URL, Java class, JSPs, the access check found in the code, the anonymous result on staging, the e2e tests that request it, recent traffic, purpose and status (keep, check, remove?). |
| [catalog/actions.csv](catalog/actions.csv) | The same catalog as CSV. The purpose, status and notes columns are written by hand here. |
| [catalog/coverage-gaps.md](catalog/coverage-gaps.md) | Rows with no functional e2e test, most important first (uploads, curator saves, login, nightly jobs, public pages). |
| [flows/README.md](flows/README.md) | **Data flows.** Step by step, with class and file names: specimen upload (curator file and TaxonWorks), image upload, AntCat names to AntWeb, the nightly sets 1 to 5; plus the test gaps and findings for these areas. |
| [guides/curate-page.md](guides/curate-page.md) | **Curate page guide** for curators and admins: each tool on the Curator Tools page, in page order, what it is for and how to use it. |
| [guides/manager-tools.md](guides/manager-tools.md) | **Manager Tools guide** for admins: Museum, Geolocale, Project Manager, Photographers, Admin Alert, Bioregion and Introduced Map, Query and Log Manager; fields, what saving does, pitfalls. |
| [../tests/e2e/README.md](../tests/e2e/README.md) | The end-to-end test suite: what each test file checks and how to run it. |
| [RELEASING.md](RELEASING.md), [VERSIONING.md](VERSIONING.md) | How a release is made and numbered. |

### Regenerating the catalog

```
python3 tools/catalog/build_catalog.py
```

Standard library only (Python 3.9 or later). It reads `WEB-INF/struts-config.xml`, the Java sources under `src/`, the JSPs under `web/`, `tests/e2e/` and `api/v3.1/api.py`, and rewrites `docs/catalog/actions.csv`, `actions.md` and `coverage-gaps.md`. The hand columns (purpose, status, notes) are read back from the existing `actions.csv` by key and kept.

Traffic: run `tools/traffic-by-action.sh` on the production host (see the comments at the top of the script), copy its output here and pass it in:

```
python3 tools/catalog/build_catalog.py --traffic traffic.tsv --traffic-min 2 \
  --traffic-label "production Tomcat log, 2026-10-11 to 2026-10-18 (8 days)"
```

`--traffic-min N` shows keys missing from a list that was cut at its smallest counts as `<N`. Without `--traffic`, the hit counts already in the catalog are kept. Do not commit the traffic file itself.

### When you add or remove an action

The pull request check (`.github/workflows/tests.yaml`, step "Function catalog has a row for every action") runs `build_catalog.py --check` and fails when an action or sub-action in the code has no row in `docs/catalog/actions.csv`. Run the generator, fill in purpose and status for the new rows, and commit `docs/catalog/`.

### Reading the access column

The access rule is read from the code by the generator: `LoginMgr.mustBe...` and `Check.admin/curator/login` calls, `if (!...isAdmin...) return ...` tests, the scheduler's secure code, and the `adminCheck.jsp` / `curatorCheck.jsp` / `loginCheck.jsp` includes of the JSPs an action forwards to. It is a static reading, not a proof. `partial` means checks were found only on some paths (for example, the save branch of a public page); the evidence column gives the line numbers. A JSP check runs after the Java class, so it protects what the page shows, not what the class does before it.

## Old, to be reviewed

These were written before the documentation phase (October 2026). Some are out of date; each will be checked against the code and either updated, merged into the documents above, or removed.

In `docs/`:

| File | Topic |
|---|---|
| [INDEX-2026-01.md](INDEX-2026-01.md) | The previous index of this folder (January 2026) |
| [API.md](API.md) | API documentation |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Architecture overview (January 2026; superseded by [architecture-overview.md](architecture-overview.md)) |
| [DATABASE.md](DATABASE.md) | Database schema |
| [DEVELOPMENT.md](DEVELOPMENT.md) | Development guide |
| [EXAMPLES.md](EXAMPLES.md) | Modification examples |
| [FEATURES.md](FEATURES.md) | Feature-to-code mapping |
| [LABELS.md](LABELS.md) | GitHub issue labels |
| [PHASE1.md](PHASE1.md) | Phase 1 plan (security and foundation) |
| [ROADMAP.md](ROADMAP.md) | Long-term roadmap |
| [TESTING.md](TESTING.md) | Testing guide |

In `doc/` (mostly developer notes from the 2010s). These stay where they are for now and will be reviewed area by area as the documentation phase reaches each part of the code (uploads and nightly sets, curator and admin tools, public pages and API, data model); anything still true moves into `docs/`, the rest is removed: antweb-docs.txt, antweb-docs2.txt, apiV2.txt, apiV3.txt, artist.txt, bolton.txt, cron.txt, dataFlow.txt, dbDebug.txt, deploy.txt, deployment.md, description.txt, develop.txt, downtimes.txt, erd.mwb, erd.png, exif.txt, feed.txt, fileChanges.txt, fileMods.txt, fileSystem.txt, git.txt, hack.txt, homepage.txt, imageUpload.txt, innoDbAndUtf8.txt, install.txt, installAfterJoe.txt, issues.txt, locality.txt, localityMaps.txt, login.txt, mobile.txt, museumTitle.txt, notes.txt, projectData.txt, pythonDev.txt, release.txt, restartBug.txt, search.txt, specialChars.txt, speciesListMerge.txt, specimen16.txt, stableId.txt, stgHttpd.conf, taxonImages.txt, thau.txt, toDo.txt, tomcat-nonroot-runbook.md, uErrors.txt, uiV2.txt, version.txt, zonageek.txt.
