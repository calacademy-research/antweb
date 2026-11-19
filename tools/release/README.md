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
hand for a week; then it becomes a required check on master (a repository setting; Brian's yes 2026-11-17).

## The gate as a GitHub check (2026-11-17)

`.github/workflows/release-gate.yaml` runs the gate and writes the commit status
`release-gate` on its head: green for GATE OPEN, red with the first failing check otherwise; the run's summary has
each PR's full output. It runs again on a push, label or text change of a PR (not a fork's), a finished Tests,
End-to-end tests, Deploy Staging or Claude review run (which writes the review comment), every hour at :07 and at
04:46 Pacific. A PR event, or a finished run of a PR's head, gates only that PR; the hourly, 04:46 and manual
runs, and runs with no PR (Deploy Staging serves a merge of the staged head), gate every open PR
(`tools/release/gate_targets.py`, 2026-11-18: re-gating every PR on every event could reach the API's hourly limit).

- Check 2 asks that the PR's full e2e run started while staging had the PR's code: the last staging deploy before
  the run was of the head or of its `merge -s ours` staging commit (same files, the head's tree), and no deploy of
  other code started before the run ended. It passes a PR that
  changes only files outside the site (`NO_SITE_PATHS`: docs, unit tests, the production smoke and
  speed checks, the release tools, this workflow).
- Check 4: Claude writes a PR comment with the line `nightly-checked: <head sha>` once the staging nightly ran that
  commit clean (morning compute.log and tests read). It is tied to the sha: it never passes another commit. Only
  comments of members and collaborators count.
- Checks 6 and 8 depend on the time: a status stays as written until the next run (hourly, and at 04:46 Pacific),
  so Claude still runs the gate by hand at merge time.
- The workflow runs on `pull_request_target`: its file and the gate's code come from master, so a PR cannot change
  what writes its own status. No PR code runs in it.
- Check 2 and the staging procedure: the PR is staged as `git checkout --detach <head>; git merge -s ours
  origin/staging`, a commit with the head's tree; the full e2e run is for the PR head (label full-e2e).
- Check 8 reads `dailyUpdate.do?format=json` through an SSH tunnel to production (the Deploy key, pinned host keys).
- The gate's code and the known-failures list come from master: a new known failure is merged in its own PR first.

By hand, the gate still runs as before (`python3 tools/release/gate.py N`), with `--nightly-checked` and
`--daily-update-checked` when those were read by hand.
Tests: tests/unit/test_release_gate.py.
