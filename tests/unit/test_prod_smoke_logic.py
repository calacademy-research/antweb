#!/usr/bin/env python3
"""tests/smoke/prod_smoke.py against a local stand-in for AntWeb (no network): passes, fails and skips as it should.

Cases: a healthy site passes; a stack trace, a missing text, HTTP 500 and a wrong header commit fail; a Cloudflare
challenge page is a skip, not a failure; the morning check passes for an ended run, fails for a failed, unfinished
or missing run, and skips while dailyUpdate.do answers 404. Also the date handling of evaluate_daily (Pacific day).
Run: python3 tests/unit/test_prod_smoke_logic.py   (also in .github/workflows/tests.yaml)
"""
import datetime as dt
import http.server
import json
import os
import re
import subprocess
import sys
import threading

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
SCRIPT = os.path.join(ROOT, "tests", "smoke", "prod_smoke.py")
sys.path.insert(0, os.path.join(ROOT, "tests", "smoke"))
import prod_smoke  # noqa: E402

COMMIT = "0123456789abcdef0123456789abcdef01234567"
HEADER = ('<div id="version">\n <a href="https://github.com/x/releases" title="Release notes">&nbsp;&nbsp;v9.3.0</a>'
          '<span class="aw-commit"> &middot; <a href="https://github.com/x/commit/%s" title="Deployed commit">%s</a></span>'
          '\n</div>')
BODY = {
    "/": "AntWeb in numbers",
    "/taxonomicPage.do": "116 Genera (valid and curated) of Madagascar",
    "/description.do": "Camponotus maculatus",
    "/specimen.do": "Specimen CASENT0103190",
    "/advancedSearch.do": "Specimens from Comoros",
    "/statsPage.do": "Antweb Statistics",
}
CHALLENGE = "<html><head><title>Just a moment...</title></head><body>challenge-platform</body></html>"

STATE = {}


class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def do_POST(self):  # the smoke run must never send one
        STATE["posted"] = True
        self.send_response(405)
        self.end_headers()

    def _send(self, status, text, ctype="text/html", headers=None):
        b = text.encode()
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.send_header("Content-Length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)

    def do_GET(self):
        path, _, query = self.path.partition("?")
        if "Cookie" in self.headers:
            STATE["cookie"] = True
        mode = STATE.get("mode", "ok")
        v4 = mode in ("v4open", "v4open503", "v4down")
        if path == "/v3.1/":
            if v4:
                return self._send(200, json.dumps({"name": "Antweb API V3.1", "status": "ended", "v4": {"status": "open"}}),
                                  "application/json")
            return self._send(200, "Antweb API v3.1<br><br><a href='x'>docs</a>")
        if path == "/v4/":
            if mode == "v4down":
                return self._send(502, "<html>Bad gateway</html>")
            return self._send(200, json.dumps({"name": "Antweb API v4", "status": "open" if v4 else "closed"}), "application/json")
        if path == "/v3.1/taxa":
            # no admin switch and no key are ever sent
            assert "up=1" not in query and "limit=1" in query and "X-API-Key" not in self.headers
            if STATE.get("mode") == "api502":
                return self._send(502, "<html>Bad gateway</html>")
            if mode == "v4open":
                # v4 is open (2026-11-09): v3.1 has ended, 410 with the signpost
                return self._send(410, json.dumps({"error": "ended", "status": "ended", "v4": "https://www.antweb.org/v4/",
                                                   "terms": "https://www.antweb.org/api.do#terms"}), "application/json")
            # the closed API (2026-11-09): 503 with the terms
            return self._send(503, json.dumps({"error": "closed", "terms": "https://www.antweb.org/api.do#terms"}),
                              "application/json", headers={"Retry-After": "86400"})
        if path == "/dailyUpdate.do":
            d = STATE.get("daily")
            if d is None:
                return self._send(404, "<h1>Page not found</h1>")
            return self._send(200, json.dumps(d), "application/json")
        if mode == "speedok":   # the page speed run: every page answers at once, with every marker
            return self._send(200, "<html><body>AntWeb in numbers megacephala Pheidole Myrmicinae Madagascar casent0103190</body></html>")
        if mode == "challenge":
            return self._send(403, CHALLENGE, headers={"cf-mitigated": "challenge"})
        if path not in BODY:
            return self._send(404, "Page not found")
        text = BODY[path]
        if mode == "trace" and path == "/specimen.do":
            text += "<pre>java.lang.NullPointerException: x\n\tat org.calacademy.antweb.Specimen.get(Specimen.java:12)</pre>"
        if mode == "missing" and path == "/statsPage.do":
            text = "Antweb"
        if mode == "500" and path == "/description.do":
            return self._send(500, "boom")
        short = COMMIT[:7]
        return self._send(200, "<html><body>" + HEADER % (COMMIT, short) + text + "</body></html>")


def run(mode_arg, mode="ok", daily=None, expect=COMMIT, today="2026-11-07"):
    STATE.clear()
    STATE.update(mode=mode, daily=daily)
    env = dict(os.environ, SMOKE_SITE_URL=BASE, SMOKE_API_URL=BASE, SMOKE_EXPECT_COMMIT=expect, SMOKE_TODAY=today,
               SMOKE_TIMEOUT="10")
    env.pop("GITHUB_STEP_SUMMARY", None)
    r = subprocess.run([sys.executable, SCRIPT, mode_arg], env=env, capture_output=True, text=True, timeout=120)
    assert not STATE.get("posted"), "the smoke run sent a POST"
    assert not STATE.get("cookie"), "the smoke run sent a cookie"
    return r.returncode, r.stdout


def expect(cond, msg, out=""):
    if not cond:
        print("FAIL", msg)
        print(out)
        sys.exit(1)
    print("ok  ", msg)


def daily(state, started="2026-11-07T05:00:01-08:00", ended="2026-11-07T07:41:00-08:00", steps=None):
    return {"runs": [
        {"started": "2026-11-06T05:00:00-08:00", "ended": "2026-11-06T07:50:00-08:00", "state": "ok"},
        {"started": started, "ended": ended, "state": state,
         "steps": steps or [{"name": "set 1", "state": "ok", "started": "2026-11-07T05:00:01-08:00",
                             "ended": "2026-11-07T06:10:00-08:00"}]},
    ]}


def main():
    global BASE
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    BASE = f"http://127.0.0.1:{srv.server_address[1]}"

    code, out = run("deploy")
    expect(code == 0 and "9 of 9 checks passed" in out and "v9.3.0 · 0123456" in out, "healthy site passes", out)
    code, out = run("deploy", expect=COMMIT[:7])
    expect(code == 0, "a short expected commit matches", out)
    code, out = run("deploy", expect="fedcba9876543210")
    expect(code == 1 and "expected commit fedcba9" in out, "wrong header commit fails", out)
    code, out = run("deploy", mode="trace")
    expect(code == 1 and "stack trace" in out, "a Java stack trace fails", out)
    code, out = run("deploy", mode="missing")
    expect(code == 1 and "['Statistics'] not on the page" in out, "missing page text fails", out)
    code, out = run("deploy", mode="500")
    expect(code == 1 and "HTTP 500" in out, "HTTP 500 fails", out)
    code, out = run("deploy", mode="api502")
    expect(code == 1 and "expected 503 while the API is closed" in out, "a proxy error on the closed API's data route fails", out)
    # v4 open (one switch on the server): /v4/ answers open and the v3.1 data route is 410 with the signpost
    code, out = run("deploy", mode="v4open")
    expect(code == 0 and "9 of 9 checks passed" in out and "/v4/ answers (open)" in out
           and "answers as ended (410 with the signpost to v4)" in out, "v4 open and v3.1 ended passes", out)
    code, out = run("deploy", mode="v4open503")
    expect(code == 1 and "expected 410, v3.1 has ended since v4 is open" in out, "v4 open but v3.1 still 503 fails", out)
    code, out = run("deploy", mode="v4down")
    expect(code == 1 and "/v3.1/ says v4 is open, but /v4/ answers HTTP 502" in out, "v4 open but /v4/ not answering fails", out)
    code, out = run("deploy", mode="challenge")
    expect(code == 0 and out.count("\nskip  ") + out.startswith("skip  ") == 7 and "header: home page not read" in out
           and "not reachable through Cloudflare challenge" in out and "::warning" in out and "API /v3.1/taxa (" in out,
           "Cloudflare challenge pages are skips with a message, not failures", out)

    code, out = run("morning", daily=None)
    expect(code == 0 and "not deployed yet" in out and "skip" in out, "dailyUpdate.do 404 is a skip", out)
    code, out = run("morning", daily=daily("ok"))
    expect(code == 0 and "05:00-07:41" in out, "ended run passes", out)
    code, out = run("morning", daily=daily("failed", steps=[
        {"name": "set 1", "state": "ok", "started": "2026-11-07T05:00:01-08:00", "ended": "2026-11-07T06:10:00-08:00"},
        {"name": "set 2", "state": "failed", "started": "2026-11-07T06:10:00-08:00", "ended": "2026-11-07T06:12:00-08:00",
         "message": "geolocale counts: lock wait timeout"}]))
    expect(code == 1 and "failed" in out and "set 2: failed 06:10-06:12 (geolocale counts" in out,
           "failed run fails and lists the steps", out)
    code, out = run("morning", daily=daily("running", ended=None))
    expect(code == 1 and "has not ended" in out, "unfinished run fails", out)
    code, out = run("morning", daily=daily("ok"), today="2026-11-08")
    expect(code == 1 and "no daily update started today" in out and "2026-11-07 05:00" in out,
           "no run started today fails and names the last one", out)

    # Pacific day: 2026-11-07 04:30 UTC is 2026-11-06 in Pacific; naive times are Pacific.
    ok, msg, _ = prod_smoke.evaluate_daily({"lastRun": {"started": "2026-11-07T04:30:00Z", "ended": "2026-11-07T06:00:00Z",
                                                        "status": "done"}}, dt.date(2026, 11, 6))
    expect(ok, "UTC start time is counted on its Pacific day (" + msg + ")")
    ok, msg, _ = prod_smoke.evaluate_daily({"started": "2026-11-07 05:00:00", "ended": "2026-11-07 07:00:00",
                                            "state": "ok"}, dt.date(2026, 11, 7))
    expect(ok, "a single run object with naive Pacific times (" + msg + ")")
    # the statistics page counts its tables on its first visit after a restart: it gets a longer wait than the other
    # pages (2026-11-13: two deploy checks failed at 60 s while the page was fine a minute later)
    seen = {}
    real_get = prod_smoke.get

    def fake_get(url, timeout=None):
        seen[url] = timeout
        return prod_smoke.Resp(200, "Statistics Madagascar Genera maculatus", url, {})
    prod_smoke.get = fake_get
    try:
        prod_smoke.check_page("http://x", "statistics page", "/statsPage.do", ["Statistics"])
        prod_smoke.check_page("http://x", "species page", "/description.do", ["maculatus"])
    finally:
        prod_smoke.get = real_get
    expect(seen["http://x/statsPage.do"] == max(240, prod_smoke.TIMEOUT),
           "the statistics page waits longer", str(seen))
    expect(seen["http://x/description.do"] in (None, prod_smoke.TIMEOUT), "the other pages keep the usual timeout", str(seen))
    # page speed (2026-11-17): the best of two reads counts; over the limit fails and says the time and the limit
    clock = {"t": 0.0, "reads": []}
    real_now = prod_smoke._now

    def fake_get2(url, timeout=None):
        clock["reads"].append(url)
        clock["timeouts"].append(timeout)
        clock["t"] += clock["costs"].pop(0)
        return prod_smoke.Resp(200, "page", url, {})
    prod_smoke.get = fake_get2
    prod_smoke._now = lambda: clock["t"]
    try:
        clock.update(costs=[1.0], timeouts=[])
        msg = prod_smoke.check_speed("http://x", "species images", "/images.do", 4.0)
        expect(msg.startswith("1.0 s") and len(clock["reads"]) == 1, "a quick page is read once (" + msg + ")")
        expect(clock["timeouts"] == [20], "a timed read waits at most max(3 x limit, 20) s " + str(clock["timeouts"]))
        clock.update(costs=[9.0, 3.0], reads=[])
        msg = prod_smoke.check_speed("http://x", "species images", "/images.do", 4.0)
        expect(msg.startswith("3.0 s") and len(clock["reads"]) == 2, "a slow first read is tried again, the best counts (" + msg + ")")
        clock.update(costs=[9.0, 8.0], reads=[])
        try:
            prod_smoke.check_speed("http://x", "species images", "/images.do", 4.0)
            expect(False, "a page slow twice fails")
        except prod_smoke.Fail as e:
            expect("slow, 8.0 s (limit 4 s)" in str(e) and "/images.do" in str(e), "a page slow twice fails with its time (" + str(e) + ")")
        # AntWeb's time is until the first bytes (ttfb); the transfer of a large page is shown, not judged
        def big_page(url, timeout=None):
            clock["t"] += 8.0
            return prod_smoke.Resp(200, "page", url, {}, ttfb=1.5)
        prod_smoke.get = big_page
        msg = prod_smoke.check_speed("http://x", "subfamily page", "/browse.do", 5.0)
        expect(msg.startswith("1.5 s") and "whole read with transfer 8.0 s" in msg,
               "the time judged is AntWeb's (first bytes), the whole read is shown beside it (" + msg + ")")
        # the body coming slowly (over 3 x the limit) fails even when the first bytes were quick
        def trickle(url, timeout=None):
            clock["t"] += 20.0
            return prod_smoke.Resp(200, "page", url, {}, ttfb=1.0)
        prod_smoke.get = trickle
        try:
            prod_smoke.check_speed("http://x", "subfamily page", "/browse.do", 5.0)
            expect(False, "a slow whole read fails")
        except prod_smoke.Fail as e:
            expect("whole read with transfer 20.0 s" in str(e) and "over 3 x" in str(e), "a whole read over 3 x the limit fails (" + str(e) + ")")
        # a quick first byte with a stalled body, then a read that passes both: the second read counts
        pairs2 = [(1.0, 20.0), (1.5, 8.0)]

        def stall_then_ok(url, timeout=None):
            ttfb, whole = pairs2.pop(0)
            clock["t"] += whole
            return prod_smoke.Resp(200, "page", url, {}, ttfb=ttfb)
        prod_smoke.get = stall_then_ok
        msg = prod_smoke.check_speed("http://x", "subfamily page", "/browse.do", 5.0)
        expect(msg.startswith("1.5 s") and "8.0 s" in msg, "a read that passes both checks wins over a stalled one (" + msg + ")")
        # two reads: the message pairs the time and the whole read of the same read
        pairs = [(5.0, 5.2), (6.0, 14.0)]

        def two(url, timeout=None):
            ttfb, whole = pairs.pop(0)
            clock["t"] += whole
            return prod_smoke.Resp(200, "page", url, {}, ttfb=ttfb)
        prod_smoke.get = two
        try:
            prod_smoke.check_speed("http://x", "species images", "/images.do", 4.0)
        except prod_smoke.Fail as e:
            expect("5.0 s" in str(e) and "5.2 s" in str(e) and "14.0" not in str(e), "the best read's own pair is reported (" + str(e) + ")")
        # a first read that fails is tried again; a restart (502, initializing) on every read is a skip, not slow
        pauses = []
        real_sleep = prod_smoke._sleep
        prod_smoke._RESTART_SEEN.append("an earlier page")   # these cases test the reads, not the wait for a restart
        prod_smoke._sleep = lambda s: pauses.append(s)
        try:
            answers = []

            def fake_get3(url, timeout=None):
                clock["t"] += 1.0
                kind = answers.pop(0)
                if kind == "502":
                    return prod_smoke.Resp(502, "Bad gateway", url, {})
                if kind == "init":
                    return prod_smoke.Resp(200, "Server is initializing", url, {})
                if kind == "timeout":
                    raise TimeoutError("timed out")
                return prod_smoke.Resp(200, "page", url, {})
            prod_smoke.get = fake_get3
            answers[:] = ["502", "ok"]
            msg = prod_smoke.check_speed("http://x", "species images", "/images.do", 4.0)
            expect(msg.startswith("1.0 s") and pauses == [prod_smoke.RETRY_PAUSE], "a 502 then an answer: timed by the second read (" + msg + ")")
            answers[:] = ["502", "init"]
            try:
                prod_smoke.check_speed("http://x", "species images", "/images.do", 4.0)
                expect(False, "a restart on both reads is skipped")
            except prod_smoke.Skip as e:
                expect("restarting" in str(e), "a restart on both reads is skipped, not called slow (" + str(e) + ")")
            answers[:] = ["timeout", "ok"]
            msg = prod_smoke.check_speed("http://x", "species images", "/images.do", 4.0)
            expect("one read failed first" in msg, "an error then a quick read: green, the error kept in the log (" + msg + ")")
            answers[:] = ["ok", "ok"]
            try:
                prod_smoke.check_speed("http://x", "species images", "/images.do", 4.0, marker="megacephala")
                expect(False, "a page without its marker fails")
            except prod_smoke.Fail as e:
                expect("megacephala" in str(e), "a quick page without its marker text fails (" + str(e) + ")")
            answers[:] = ["timeout", "timeout"]
            try:
                prod_smoke.check_speed("http://x", "species images", "/images.do", 4.0)
                expect(False, "no answer twice fails")
            except prod_smoke.Fail as e:
                expect("no answer" in str(e), "no answer on both reads fails (" + str(e) + ")")
        finally:
            prod_smoke._sleep = real_sleep
            prod_smoke._RESTART_SEEN.clear()
    finally:
        prod_smoke.get = real_get
        prod_smoke._now = real_now
    expect(all(limit > 0 and path.startswith("/") for _, path, limit, _m in prod_smoke.SPEED_PAGES) and len(prod_smoke.SPEED_PAGES) >= 5,
           "every speed page has a path and a limit")
    # the wait may overrun by one read (30 s) and one poll (15 s); setup (checkout, tunnel) gets 120 s below
    worst = 2 * (prod_smoke.SPEED_WAIT + prod_smoke.WAIT_READ_TIMEOUT + prod_smoke.WAIT_POLL) + 30 + sum(2 * prod_smoke.speed_timeout(limit) + prod_smoke.RETRY_PAUSE for _, _, limit, _m in prod_smoke.SPEED_PAGES)
    wf = open(os.path.join(ROOT, ".github", "workflows", "prod-speed.yaml")).read()
    minutes = int(re.search(r"timeout-minutes:\s*(\d+)", wf).group(1))
    expect(worst + 120 < minutes * 60, f"the worst case ({worst} s of reads) ends before the workflow's {minutes} minutes")
    # the real get() measures the time to the first bytes (stand-in server)
    STATE.clear(); STATE.update(mode="speedok")
    r = prod_smoke.get(BASE + "/")
    expect(isinstance(r.ttfb, float) and 0 <= r.ttfb, "get() records the time to the first bytes (" + repr(r.ttfb) + ")")
    # the whole speed run through main and report, against the stand-in (2026-11-17, review of 81747f9)
    code, out = run("speed", mode="speedok")
    expect(code == 0 and out.count("speed: ") >= len(prod_smoke.SPEED_PAGES), "prod_smoke.py speed: every page timed, green", out)
    # no page answered during the whole run: red, never green (every check skipped as restarting)
    skipped = [("skip", "speed: " + n, "AntWeb was restarting", 0.0) for n, _, _, _ in prod_smoke.SPEED_PAGES]
    expect(any(r[0] == "FAIL" for r in prod_smoke.all_skipped_fails(skipped)), "a run where no page answered is red")
    mostly = skipped[:-1] + [("pass", "speed: home page", "1.0 s", 1.0)]
    expect(any(r[0] == "FAIL" for r in prod_smoke.all_skipped_fails(mostly)), "a run where most pages were skipped is red")
    few = [("pass", "speed: " + n, "1.0 s", 1.0) for n, _, _, _ in prod_smoke.SPEED_PAGES[:-1]] + [skipped[-1]]
    expect(not any(r[0] == "FAIL" for r in prod_smoke.all_skipped_fails(few)), "one restarting page among answering ones stays a skip")
    # a refused connection is the tunnel itself gone: it fails, it is not skipped
    expect(not prod_smoke.restarting(prod_smoke.Fail("x: no answer from http://127.0.0.1:18081/ ([Errno 111] Connection refused)")),
           "a refused connection is not counted as a restart")
    # a restart behind the tunnel: the far end closes the connection twice -> the page is skipped, not failed
    import http.client as _hc
    real_get_r = prod_smoke.get
    real_sleep_r = prod_smoke._sleep

    def closed(url, timeout=None):
        raise _hc.RemoteDisconnected("Remote end closed connection without response")
    prod_smoke.get, prod_smoke._sleep = closed, (lambda s: None)
    real_wait = prod_smoke.wait_until_up
    try:
        # the first page to meet a restart waits once for AntWeb, then is read again and timed
        waits = []
        prod_smoke._RESTART_SEEN.clear()

        def back_up(secs, poll=15, after="", level="error"):
            waits.append(after)
            prod_smoke.get = lambda url, timeout=None: prod_smoke.Resp(200, "page", url, {})
            return 0
        prod_smoke.wait_until_up = back_up
        msg = prod_smoke.check_speed("http://x", "species images", "/images.do", 4.0)
        expect(len(waits) == 1 and "after waiting for a restart" in msg, "a restart: wait once, then the page is timed (" + msg + ")")
        # after that restart, a page over its limit is a skip (caches loading), not a failure
        clock2 = {"t": 0.0}
        real_now2 = prod_smoke._now
        prod_smoke._now = lambda: clock2["t"]

        def slow(url, timeout=None):
            clock2["t"] += 9.0
            return prod_smoke.Resp(200, "page", url, {})
        prod_smoke.get = slow
        try:
            prod_smoke.check_speed("http://x", "genus images", "/images.do", 4.0)
            expect(False, "slow after a restart is a skip")
        except prod_smoke.Skip as e:
            expect("after a restart" in str(e), "a slow page after a restart in the run is skipped, not failed (" + str(e) + ")")
        finally:
            prod_smoke._now = real_now2
        # a later page that meets a restart in the same run is skipped without waiting again
        prod_smoke.get = closed
        try:
            prod_smoke.check_speed("http://x", "genus page", "/browse.do", 4.0)
            expect(False, "a second restart page in a run is skipped")
        except prod_smoke.Skip as e:
            expect(len(waits) == 1 and "restarting" in str(e), "a restart behind the tunnel (remote end closed), after the one wait, is skipped (" + str(e) + ")")
    finally:
        prod_smoke.get, prod_smoke._sleep = real_get_r, real_sleep_r
        prod_smoke.wait_until_up = real_wait
        prod_smoke._RESTART_SEEN.clear()
    expect(prod_smoke.restarting(prod_smoke.Fail("x: HTTP 502 for /")), "a 502 is counted as a restart")
    # AntWeb never answers before timing: red with its own line (SMOKE_SPEED_WAIT=0, the stand-in challenges every page)
    os.environ["SMOKE_SPEED_WAIT"] = "0"
    try:
        code, out = run("speed", mode="challenge")
    finally:
        os.environ.pop("SMOKE_SPEED_WAIT", None)
    expect(code == 1 and "did not answer before timing" in out, "speed: AntWeb not answering before timing is red", out)
    srv.shutdown()
    print("prod_smoke.py: all logic checks passed.")


if __name__ == "__main__":
    main()
