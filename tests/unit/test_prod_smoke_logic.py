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
        if path == "/v3.1/":
            return self._send(200, "Antweb API v3.1<br><br><a href='x'>docs</a>")
        if path == "/v3.1/taxa":
            assert "up=1" in query and "limit=1" in query
            return self._send(200, json.dumps({"taxa": [{"taxonName": "formicinaecamponotus"}]}), "application/json")
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
    srv.shutdown()
    print("prod_smoke.py: all logic checks passed.")


if __name__ == "__main__":
    main()
