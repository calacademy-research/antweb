# AntWeb end-to-end tests

These tests drive a running AntWeb over HTTP, like a visitor or curator. They don't depend on Struts internals, so they stay valid while the web layer is upgraded (plan: `antweb-struts-plan.md`).

Default target: https://staging.antweb.org. They never change data; the write-action checks call data-changing URLs anonymously, with no parameters, and are refused on production.

## Files
- `survey.py` requests every Struts action anonymously, crawls public pages for real example URLs, and calls the API. It writes a report (GitHub job summary, `out/survey.json`) and a candidate baseline (`out/expectations.json`).
- `expectations.json` is the recorded baseline: what each action and crawled page returned.
- `test_actions.py` checks every action and recorded page against the baseline, checks that every action in `struts-config.xml` has an expectation, and checks that data-changing actions ask for a login.
- `test_api.py` checks API v3.1 returns JSON. Note: `api.py` returns an empty response unless the request has `up=1`.
- `policy.py` lists actions never called automatically, data-changing actions, and crawl seeds.
- `test_browser.py` opens pages in a real browser (Playwright, headless Chromium), because the other tests never run a page's JavaScript. It checks: the header Taxa and Places boxes show suggestions ("campon" gives Camponotus, "madag" gives Madagascar); no uncaught JavaScript errors on key pages (home, genus, species, specimen, images, bigMap, advanced search, login, Madagascar); every local script and stylesheet on those pages returns 200 and has the `?v=<hash>` version (so browsers never keep an old copy, see `src/org/calacademy/antweb/util/Asset.java`); and no JavaScript errors at phone width (390x844). It only opens pages and types into the search boxes. Skipped if Playwright is not installed.

## Running
- GitHub: Actions > End-to-end tests > Run workflow (mode `test`, `survey` or `record`). It also runs on every pull request and daily at 13:17 Pacific.
- Locally: `pip install -r tests/e2e/requirements.txt; python -m playwright install chromium; ANTWEB_URL=https://staging.antweb.org pytest tests/e2e`
- Browser tests only: `pytest tests/e2e/test_browser.py`. In GitHub the browser goes through the same SSH tunnel as the other tests (`ANTWEB_BROWSER_HOST_RULES` maps staging.antweb.org to the tunnel; `ANTWEB_BROWSER_IGNORE_HTTPS_ERRORS=1` accepts staging's origin certificate, never honoured for production).

## When a test fails
Either AntWeb broke (fix it), or the change was intended: re-record with mode `record`, and review the diff of `expectations.json` in the pull request.
