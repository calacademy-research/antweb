# AntWeb end-to-end tests

These tests drive a running AntWeb over HTTP, like a visitor or curator. They don't depend on Struts internals, so they stay valid while the web layer is upgraded (plan: `antweb-struts-plan.md`).

Default target: https://staging.antweb.org. They never change data, with one exception below; the write-action checks call data-changing URLs anonymously, with no parameters, and are refused on production.

Exception: `test_page_editor.py` (page-text editor safety) saves and clears text on staging only, in two agreed test records: the taxon page Polyrhachis amana, field Comments, and specimen CASENT0922067, field Notes. It runs a write test only if that field is empty at the start, and leaves it empty. It skips itself on any host other than staging.antweb.org or a local server, and where the editor safety code is not deployed yet.

## Files
- `survey.py` requests every Struts action anonymously, crawls public pages for real example URLs, and calls the API. It writes a report (GitHub job summary, `out/survey.json`) and a candidate baseline (`out/expectations.json`).
- `expectations.json` is the recorded baseline: what each action and crawled page returned.
- `test_actions.py` checks every action and recorded page against the baseline, checks that every action in `struts-config.xml` has an expectation, and checks that data-changing actions ask for a login.
- `test_page_editor.py` checks the page-text editor's safety rules (refused saves keep the text, no empty or stale saves, clear, CSRF), as a curator, on staging only.
- `test_api.py` checks API v3.1 returns JSON. Note: `api.py` returns an empty response unless the request has `up=1`.
- `policy.py` lists actions never called automatically, data-changing actions, and crawl seeds.

## Running
- GitHub: Actions > End-to-end tests > Run workflow (mode `test`, `survey` or `record`). It also runs on every pull request and daily at 07:17 Pacific.
- Locally: `pip install -r tests/e2e/requirements.txt; ANTWEB_URL=https://staging.antweb.org pytest tests/e2e`

## When a test fails
Either AntWeb broke (fix it), or the change was intended: re-record with mode `record`, and review the diff of `expectations.json` in the pull request.
