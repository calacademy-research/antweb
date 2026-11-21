"""The release gate's checks (tools/release/gate.py) and the known-failures list (tests/e2e/known-failures.toml)."""
import os
import datetime as dt
import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("gate", ROOT / "tools/release/gate.py")
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)

UTC = dt.timezone.utc


def test_known_failures_list_is_valid_today():
    entries = gate.read_known_failures()
    assert gate.known_failure_problems(entries, dt.date.today()) == []


def test_known_failure_rules():
    d = dt.date(2026, 11, 6)
    ok = {"test": "t", "reason": "r", "decided_by": "Brian Fisher", "decided": d, "expires": d + dt.timedelta(days=7)}
    assert gate.known_failure_problems([ok], d) == []
    assert "expired" in gate.known_failure_problems([dict(ok, expires=d - dt.timedelta(days=1))], d)[0]
    assert "at most 7" in gate.known_failure_problems([dict(ok, expires=d + dt.timedelta(days=8))], d)[0]
    assert "missing reason" in gate.known_failure_problems([dict(ok, reason="")], d)[0]
    # review of #330: a future decision date would stretch the 7 days; a datetime is refused, not a crash
    assert "future" in gate.known_failure_problems([dict(ok, decided=d + dt.timedelta(days=30), expires=d + dt.timedelta(days=35))], d)[0]
    assert "must be dates" in gate.known_failure_problems([dict(ok, decided=dt.datetime(2026, 11, 6, 9, 0))], d)[0]


def test_window():
    # 2026-11-06 is PST (UTC-8)
    assert gate.window_closed(dt.datetime(2026, 11, 6, 12, 45, tzinfo=UTC))       # 04:45
    assert gate.window_closed(dt.datetime(2026, 11, 6, 16, 14, tzinfo=UTC))       # 08:14
    assert not gate.window_closed(dt.datetime(2026, 11, 6, 16, 15, tzinfo=UTC))   # 08:15
    assert not gate.window_closed(dt.datetime(2026, 11, 6, 12, 44, tzinfo=UTC))   # 04:44
    assert gate.window_closed(dt.datetime(2026, 7, 1, 12, 0, tzinfo=UTC))         # 05:00 PDT


def test_rollback_line():
    assert gate.has_rollback("text\n\nRollback: revert this PR (no database step).")
    assert gate.has_rollback("**Rollback:** revert")
    assert not gate.has_rollback("roll back if needed")
    assert not gate.has_rollback(None)


def test_nightly_files():
    files = ["src/org/calacademy/antweb/util/Scheduler.java", "src/org/calacademy/antweb/home/PlaceViewTotalDb.java",
             "src/org/calacademy/antweb/home/GroupCountDb.java", "web/overview-body.jsp", "tests/e2e/test_x.py"]
    assert gate.nightly_files(files) == files[:3]


def test_review_state():
    head = "d13a8863e8dd17bf09e9046ccb7d12b9175f6587"
    def c(text, sha="d13a886"):
        return {"body": f"{gate.REVIEW_MARKER}\n### Claude review\n\n{text}\n\n<sub>Automatic review of {sha} by `m`.</sub>",
                "user": {"login": gate.REVIEW_BOT}}
    assert gate.review_state([], head)[0] is False
    assert gate.review_state([c("No blocking issues. Should fix: ...")], head)[0] is True
    assert gate.review_state([c("No blocking issues.", sha="bbd866d")], head)[0] is False
    assert gate.review_state([c("No blocking issues."), c("**Blocking**: x")], head)[0] is False
    assert gate.review_state([{"body": "an ordinary comment"}, c("No blocking issues.")], head)[0] is True
    # review of #330: a comment in the review's form posted by someone else does not count
    forged = dict(c("No blocking issues."), user={"login": "someone"})
    assert gate.review_state([c("**Blocking**: x"), forged], head)[0] is False


def test_failed_tests_and_unlisted():
    notes = [{"title": "e2e failed tests", "message": "2 failed: test_a, test_b"},
             {"title": "e2e failure", "message": "test_c: AssertionError: x"},
             {"title": "e2e timing", "message": "full run: 40 min"}]
    failed = gate.failed_tests(notes)
    assert failed == {"test_a", "test_b", "test_c"}
    assert gate.unlisted(failed, [{"test": "test_x.py::test_b"}, {"test": "test_c"}]) == ["test_a"]
    # the e2e workflow separates the names with "; " (2026-11-06, run 42249265193)
    assert gate.failed_tests([{"title": "e2e failed tests", "message": "2 failed: test_a; test_b"}]) == {"test_a", "test_b"}


def _json(*runs):
    import json
    return 200, {"Content-Type": "application/json"}, json.dumps({"runs": list(runs)})


def test_daily_update_state_json():
    running = {"started": "2026-11-09T05:00:00", "ended": None, "state": "running", "steps": []}
    ok = {"started": "2026-11-08T05:00:00", "ended": "2026-11-08T07:41:00", "state": "ok", "steps": []}
    failed = {"started": "2026-11-08T05:00:00", "ended": None, "state": "failed", "steps": []}
    assert gate.daily_update_state(*_json(running, ok))[0] == "running"
    assert gate.daily_update_state(*_json(ok, failed))[0] == "not running"
    assert gate.daily_update_state(*_json(failed, ok))[0] == "not running"
    assert gate.daily_update_state(*_json())[0] == "not running"
    # conservative: an ok run with no end, a state the gate does not know, other JSON
    assert gate.daily_update_state(*_json(dict(ok, ended=None)))[0] == "unknown"
    assert gate.daily_update_state(*_json(dict(ok, state="waiting")))[0] == "unknown"
    assert gate.daily_update_state(200, {}, '{"status": "fine"}')[0] == "unknown"
    assert gate.daily_update_state(200, {}, "[]")[0] == "unknown"


def test_daily_update_state_unreachable():
    challenge = "<html><title>Just a moment...</title><script src='/cdn-cgi/challenge-platform/x'></script>"
    assert gate.daily_update_state(403, {}, challenge)[0] == "unknown"
    assert gate.daily_update_state(200, {"cf-mitigated": "challenge"}, "")[0] == "unknown"
    assert gate.daily_update_state(404, {}, "not found")[0] == "unknown"
    assert gate.daily_update_state(502, {}, "")[0] == "unknown"
    # a login page (the HTML page needs a curator login)
    assert gate.daily_update_state(200, {}, "<html><form action='login.do'>")[0] == "unknown"


def test_daily_update_state_page():
    # markup of DailyUpdateAction.runCard (util/dailyUpdate-body.jsp)
    def page(state):
        return ('<div class="aw-card" id="daily-update-latest"><h2>Today\'s daily update (running)</h2>'
                f'<p class="aw-lead" id="daily-update-state" data-state="{state}">2026-11-09, started 05:00: x.</p>'
                '<table class="daily-update-steps"><tr data-step="end" data-state="done"></tr></table></div>')
    assert gate.daily_update_state(200, {}, page("running"))[0] == "running"
    assert gate.daily_update_state(200, {}, page("ok"))[0] == "not running"
    assert gate.daily_update_state(200, {}, page("failed"))[0] == "not running"
    assert gate.daily_update_state(200, {}, page("weird"))[0] == "unknown"
    assert gate.daily_update_state(200, {}, '<div class="aw-card" id="daily-update-none"><p>No daily update</p></div>')[0] == "not running"


def test_daily_check():
    running = {"started": "2026-11-09T05:00:00", "ended": None, "state": "running"}
    ok = {"started": "2026-11-08T05:00:00", "ended": "2026-11-08T07:41:00", "state": "ok"}

    def boom():
        raise OSError("Tunnel connection failed: 403 Forbidden")
    # running: closed, also with --daily-update-checked
    assert gate.daily_check(lambda: _json(running), False)[0] is False
    assert gate.daily_check(lambda: _json(running), True)[0] is False
    assert gate.daily_check(lambda: _json(ok), False)[0] is True
    # unknown fails closed unless it was read by hand
    ok_, text = gate.daily_check(boom, False)
    assert ok_ is False and gate.UNKNOWN_DAILY in text
    ok_, text = gate.daily_check(lambda: (403, {"cf-mitigated": "challenge"}, ""), False)
    assert ok_ is False and gate.UNKNOWN_DAILY in text
    ok_, text = gate.daily_check(boom, True)
    assert ok_ is True and gate.UNKNOWN_DAILY in text and "--daily-update-checked" in text
    # user wording: "daily update", never "nightly" in what check 8 says (the flag's name aside)
    for fetch, by_hand in ((lambda: _json(running), False), (lambda: _json(ok), False), (boom, False)):
        assert "nightly" not in gate.daily_check(fetch, by_hand)[1].replace("--daily-update-checked", "")


def test_no_site_code():
    # only docs, unit tests, the production checks, the release tools: no staging run needed (check 2)
    assert gate.no_site_code(["tests/smoke/prod_smoke.py", ".github/workflows/prod-speed.yaml", "docs/runbook.md",
                              "tests/unit/test_prod_smoke_logic.py", "tools/release/gate.py", "README.md"])
    assert gate.no_site_code([".github/workflows/release-gate.yaml"])
    # the place tools run only by hand need no staging run; the daily place report (host cron, writes) and the site do
    for f in ("iso_states.py", "build_iso3166_2.py", "iso3166_2.tsv", "iso3166_countries.tsv", "english_names.tsv", "country_rename.py", "island_countries.py", "place_fix.py", "empty_states.py"):
        assert gate.no_site_code(["tools/place/" + f, "tests/unit/test_iso_states.py"]), f
    assert sorted(gate.HAND_RUN_PLACE_TOOLS) == sorted(["iso_states.py", "build_iso3166_2.py", "iso3166_2.tsv", "iso3166_countries.tsv",
                                                        "english_names.tsv", "country_rename.py", "island_countries.py", "place_fix.py", "empty_states.py"])
    for f in ("tools/place/place_daily.py", "tools/place/place_corrections.py", "tools/place/coordinate_country.py",
              "tools/place/adm1_shape_map.csv", "tools/place/antweb-place-daily.cron", "tools/place/sub/iso_states.py",
              "src/org/calacademy/antweb/util/GeolocaleMgr.java"):
        assert not gate.no_site_code(["tools/place/iso_states.py", f]), f
    # markdown at the top level only: one inside the site needs staging
    assert not gate.no_site_code(["web/help/howto.md"]) and not gate.no_site_code(["docker/antweb/NOTES.md"])
    # anything of the site, the e2e tests (they run against staging) or the other workflows needs staging
    for f in ("src/org/calacademy/antweb/BrowseAction.java", "web/showBrowse-body.jsp", "tests/e2e/test_api.py",
              "api/v3.1/api.py", "docker/antweb/Dockerfile", ".github/workflows/tests.yaml", "db/upgrade/x.sql"):
        assert not gate.no_site_code(["docs/runbook.md", f]), f
    assert not gate.no_site_code([])


def test_the_daily_place_report_uses_no_hand_run_tool():
    # review of #393: the exemption is by file name, so neither the scripts the host's cron runs every day nor the site
    # may import or read a tool exempted as hand-run. The scripts are parsed (ast): every imported module and every
    # string constant is checked against the names and their stems; comments and docstrings do not count.
    import ast
    import re
    root = Path(__file__).resolve().parents[2]
    place = root / "tools" / "place"
    names = set(gate.HAND_RUN_PLACE_TOOLS)
    stems = {n.rsplit(".", 1)[0] for n in names}
    cron = (place / "antweb-place-daily.cron").read_text(encoding="utf-8")
    daily = set(re.findall(r"tools/place/(\w+\.py)", cron))
    assert "place_daily.py" in daily, "the cron line no longer names place_daily.py: update this guard to what it runs"
    todo, seen = sorted(daily), set()
    while todo:
        f = todo.pop()
        if f in seen:
            continue
        seen.add(f)
        tree = ast.parse((place / f).read_text(encoding="utf-8"))
        docstrings = {id(n.body[0].value) for n in ast.walk(tree) if isinstance(n, (ast.Module, ast.FunctionDef, ast.ClassDef))
                      and n.body and isinstance(n.body[0], ast.Expr) and isinstance(n.body[0].value, ast.Constant)}
        modules = [a.name for n in ast.walk(tree) if isinstance(n, ast.Import) for a in n.names]
        modules += [n.module for n in ast.walk(tree) if isinstance(n, ast.ImportFrom) and n.module]
        strings = [n.value for n in ast.walk(tree) if isinstance(n, ast.Constant) and isinstance(n.value, str) and id(n) not in docstrings]
        assert not stems & {m.split(".")[0] for m in modules}, (f, modules)
        for text in strings:
            assert not any(n in text for n in names) and not any(re.search(r"\b%s\b" % re.escape(st), text) for st in stems), (f, text[:80])
            assert "iso3166" not in text.lower(), (f, text[:80])      # the ISO files, also by a name built in parts
        for m in modules + [t for t in strings if t.endswith(".py")]:
            m = m.split(".")[0] + ".py" if not m.endswith(".py") else m
            if (place / m).exists():
                todo.append(m)
    assert {"place_daily.py", "coordinate_country.py", "place_corrections.py"} <= seen, seen
    # the site: the code, the pages, the image and the deploy never name one (outside Java and shell comments)
    site = [p for d in ("src", "web", "docker", "api") for p in (root / d).rglob("*") if p.is_file() and p.suffix in
            (".java", ".jsp", ".py", ".sh", ".xml", ".properties", ".yaml", ".yml", ".conf", "")]
    site += [root / "deploy.sh"] + sorted((root / ".github" / "workflows").glob("deploy*.y*ml"))
    for p in site:
        if not p.exists():
            continue
        try:
            text = p.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        code = re.sub(r"(?s)/\*.*?\*/", "", text)
        code = "\n".join(re.split(r"//|(?<![\w$])#", line)[0] for line in code.splitlines())
        assert not any("tools/place/" + n in code or (n.endswith(".tsv") and n in code) for n in names), p


def test_staged_by_tree():
    # Claude stages a PR as a "merge -s ours" commit: another sha with the head's tree
    trees = {"stage1": "T-old", "stage2": "T-head", "stage3": "T-head"}
    runs = [{"head_sha": "stage1", "conclusion": "success", "created_at": "2026-11-17T10:00:00Z"},
            {"head_sha": "stage2", "conclusion": "failure", "created_at": "2026-11-17T11:00:00Z"},
            {"head_sha": "stage3", "conclusion": "success", "created_at": "2026-11-17T12:00:00Z"}]
    assert gate.staged_by_tree("T-head", runs, trees.get)["head_sha"] == "stage3"
    assert gate.staged_by_tree("T-head", runs[:2], trees.get) is None        # the one with the tree failed
    # a failed redeploy of the same tree after a good one: the newest decides
    later = runs + [{"head_sha": "stage4", "conclusion": "failure", "created_at": "2026-11-17T13:00:00Z"}]
    assert gate.staged_by_tree("T-head", later, dict(trees, stage4="T-head").get) is None
    assert gate.staged_by_tree("T-other", runs, trees.get) is None
    assert gate.staged_by_tree("T-head", [], trees.get) is None


def test_nightly_checked_by_comment():
    head = "387bd550c905e9dbccecd6175e6a0e095e826164"
    c = lambda body, who="OWNER": {"body": body, "author_association": who}
    assert gate.nightly_checked_by_comment([c("Morning read.\nnightly-checked: 387bd55")], head)
    assert gate.nightly_checked_by_comment([c("nightly-checked: " + head, "COLLABORATOR")], head)
    # another commit's sha, an outsider, too short a sha, or no mark: no
    assert not gate.nightly_checked_by_comment([c("nightly-checked: 53d28ae")], head)
    assert not gate.nightly_checked_by_comment([c("nightly-checked: 387bd55", "NONE")], head)
    assert not gate.nightly_checked_by_comment([c("nightly-checked: 387b")], head)
    assert not gate.nightly_checked_by_comment([c("the nightly was checked")], head)


def test_e2e_while_staged():
    trees = {"a": "T-A", "b": "T-B", "a2": "T-A"}
    runs = [{"head_sha": "a", "conclusion": "success", "created_at": "2026-11-17T10:00:00Z", "updated_at": "2026-11-17T10:05:00Z"},
            {"head_sha": "b", "conclusion": "success", "created_at": "2026-11-17T11:00:00Z", "updated_at": "2026-11-17T11:05:00Z"}]
    w = lambda start, end, rs=runs, tree="T-A": gate.e2e_while_staged(start, end, rs, trees.get, tree)
    # PR A's full run while A was on staging
    assert w("2026-11-17T10:10:00Z", "2026-11-17T10:50:00Z")
    # PR B staged before A's run started (A deployed by its own commit, then B): A's run tested B's code
    assert not w("2026-11-17T11:30:00Z", "2026-11-17T12:10:00Z")
    assert w("2026-11-17T11:30:00Z", "2026-11-17T12:10:00Z", tree="T-B")
    # B's deploy started during A's run (still running or finished after the start): staging changed under the run
    assert not w("2026-11-17T10:30:00Z", "2026-11-17T11:10:00Z")
    running = runs[:1] + [{"head_sha": "b", "status": "in_progress", "created_at": "2026-11-17T10:40:00Z"}]
    assert not w("2026-11-17T10:30:00Z", "2026-11-17T11:10:00Z", running)
    # A staged again (same tree) during its run: fine; its deploy failed before the run: no
    again = runs[:1] + [{"head_sha": "a2", "conclusion": "success", "created_at": "2026-11-17T10:40:00Z", "updated_at": "2026-11-17T10:45:00Z"}]
    assert w("2026-11-17T10:30:00Z", "2026-11-17T11:00:00Z", again)
    failed = runs + [{"head_sha": "a2", "conclusion": "failure", "created_at": "2026-11-17T12:00:00Z", "updated_at": "2026-11-17T12:05:00Z"}]
    assert not w("2026-11-17T12:30:00Z", "2026-11-17T13:00:00Z", failed)
    # B created before A's deploy finished, still running when A's run started: no
    overlap = [{"head_sha": "a", "conclusion": "success", "created_at": "2026-11-17T10:00:00Z", "updated_at": "2026-11-17T10:05:00Z"},
               {"head_sha": "b", "conclusion": "success", "created_at": "2026-11-17T10:03:00Z", "updated_at": "2026-11-17T10:20:00Z"}]
    assert not w("2026-11-17T10:10:00Z", "2026-11-17T10:50:00Z", overlap)
    # no deploy before the run: no
    assert not w("2026-11-17T09:00:00Z", "2026-11-17T09:40:00Z")

tspec = importlib.util.spec_from_file_location("gate_targets", ROOT / "tools/release/gate_targets.py")
gate_targets = importlib.util.module_from_spec(tspec)
tspec.loader.exec_module(gate_targets)


def test_gate_targets_only_the_pr_concerned():
    """Review of #375: a PR event or a finished run of a PR's head gates that PR, not every open PR (API limit)."""
    t = gate_targets.targets
    open_prs = {374: "aaa", 376: "bbb", 380: "ccc"}
    assert t("pull_request_target", "376", [], "", open_prs) == [376]
    assert t("pull_request_target", "999", [], "", open_prs) == []          # closed meanwhile: nothing
    assert t("workflow_run", "", [{"number": 380, "head": {"sha": "ccc"}}], "ccc", open_prs) == [380]
    assert t("workflow_run", "", [], "bbb", open_prs) == [376]              # GitHub named no PR: the head decides
    assert t("workflow_run", "", [], "staged-merge", open_prs) == [374, 376, 380]   # Deploy Staging: every PR
    assert t("schedule", "", [], "", open_prs) == [374, 376, 380]
    assert t("workflow_dispatch", "", [], "", open_prs) == [374, 376, 380]
    assert t("workflow_run", "", [{"number": 1}], "zzz", {}) == []
    assert gate_targets.read_open(["374 aaa", "", "x y", "376 bbb"]) == {374: "aaa", 376: "bbb"}


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
    print("ok")


def test_workflow_keeps_the_status_of_the_later_gating():
    """Review of #377: runs of different concurrency groups can gate one PR at once; a run that began gating earlier
    must not overwrite the status written by one that began later (each status says when its run began)."""
    wf = (ROOT / ".github/workflows/release-gate.yaml").read_text(encoding="utf-8")
    assert "github.event.workflow_run.pull_requests[0].number" in wf
    assert 'started=$(date -u +%Y-%m-%dT%H:%M:%SZ)' in wf
    assert '[[ "$lastchecked" > "$started" ]]' in wf
    assert '· checked $started"' in wf
