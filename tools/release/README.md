# Release gate

`tools/release/gate.sh PR [--nightly-checked] [--daily-update-checked] [--no-review "reason"]` says whether a pull request may be merged to
master (which deploys production): one line per check, then GATE OPEN or GATE CLOSED. Claude runs it before every
merge and pastes the result into the pull request. It only reads the GitHub API (gh), the repository and the
production daily update status (check 8).

| # | Check | Passes when |
|---|---|---|
| 1 | tests | the Tests workflow (compile, unit tests) passed on the PR head |
| 2 | staging | the head was deployed to staging and its full e2e run (label full-e2e) finished; failures only as check 3 allows |
| 3 | known | every failure is in `tests/e2e/known-failures.toml` (test, reason, decided_by, decided, expires; at most 7 days) and no entry has expired |
| 4 | nightly | the PR changes no nightly code (`NIGHTLY_PATHS` in gate.py), or `--nightly-checked`: a staging nightly (or a by-hand run of the changed sets through schedule.do) of this commit ran clean and the morning tests passed |
| 5 | review | the latest Claude review is of the head and starts "No blocking issues", or `--no-review "reason"` |
| 6 | window | not 04:45 to 08:15 Pacific (the daily update) |
| 7 | rollback | the PR text has a "Rollback:" line |
| 8 | daily | the daily update is not running on production: one GET of `dailyUpdate.do?format=json` (no sign-in; `ANTWEB_DAILY_UPDATE_URL` to read it elsewhere, for example through a tunnel). If it cannot be read (Cloudflare challenge, no network, an answer the gate does not know) the check says "unknown - check dailyUpdate.do by hand" and fails, unless `--daily-update-checked` says it was read by hand and is not running |

After the merge: production deployed this commit (Deploy workflow, header commit), a read-only production smoke
run, and the next production nightly ran clean including set 5 (compute.log).

Decided by Brian Fisher, 2026-11-05 (claude/antweb-release-gate-draft.md in the project): Claude runs the gate by
hand for a week; then it becomes a required check on master (a repository setting, Brian's yes needed).
Tests: tests/unit/test_release_gate.py.
