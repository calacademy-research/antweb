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
    srv.shutdown()
    print("prod_smoke.py: all logic checks passed.")


if __name__ == "__main__":
    main()
