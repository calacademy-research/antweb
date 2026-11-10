#!/usr/bin/env python3
"""How the end-to-end tests reach the API in both states of the one switch (tests/e2e/antweb.py, 2026-11-09):

- v4 closed: a "/v3.1/<route>?...&up=1" request goes out as written, without a key;
- v4 open (v3.1 has ended): the same request goes to /v4/<route> without up, with the test key from the environment
  (ANTWEB_TEST_API_KEY) in the X-API-Key header; a request without up=1, /v3.1/ itself and every other address are
  never changed and never carry the key; a 429 for the minute is waited out; without the key, or with a refused key,
  the test is skipped (tests/e2e/test_api.py::test_the_tests_can_reach_the_api then fails with what to do);
- the key is read only from the environment: no key in the repository, the workflow passes the secret for staging only.

A small local server stands in for AntWeb. Run: python3 tests/unit/test_e2e_api_access.py
"""
import http.server
import json
import os
import re
import sys
import threading

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..")
STATE = {"open": False, "seen": [], "limit_once": False, "refuse": False}
KEY = "aw_" + "t" * 40   # made up here; not a real key


class Handler(http.server.BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def _send(self, code, body, headers=None):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/v3.1/":
            return self._send(200, {"name": "Antweb API V3.1", "v4": {"status": "open" if STATE["open"] else "closed"}})
        STATE["seen"].append((self.path, self.headers.get("X-API-Key")))
        if STATE["refuse"]:
            return self._send(401, {"error": "This API key is not valid."})
        if STATE["limit_once"]:
            STATE["limit_once"] = False
            return self._send(429, {"error": "limit"}, {"Retry-After": "0"})
        self._send(200, {"ok": True})


def start():
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return "http://127.0.0.1:%d" % server.server_address[1]


BASE = start()
os.environ["ANTWEB_URL"] = BASE
os.environ.pop("ANTWEB_API_V4", None)
os.environ.pop("ANTWEB_TEST_API_KEY", None)
sys.path.insert(0, os.path.join(ROOT, "tests", "e2e"))
import antweb  # noqa: E402
import pytest  # noqa: E402


def state(is_open, key=None):
    antweb._API_STATE.clear()
    STATE.update(open=is_open, seen=[], limit_once=False, refuse=False)
    if key is None:
        os.environ.pop("ANTWEB_TEST_API_KEY", None)
    else:
        os.environ["ANTWEB_TEST_API_KEY"] = key


def get(path):
    return antweb.new_session().get(BASE + path, timeout=10)


def test_v4_address_of_a_request():
    f = antweb.api_v4_url
    assert f(BASE + "/v3.1/specimens?genus=camponotus&limit=5&up=1") == BASE + "/v4/specimens?genus=camponotus&limit=5"
    assert f(BASE + "/v3.1/specimens?up=1&specimenCode=casent0922626") == BASE + "/v4/specimens?specimenCode=casent0922626"
    assert f(BASE + "/v3.1/bioregions?up=1") == BASE + "/v4/bioregions"
    assert f(BASE + "/v3.1/geoSpecimens?coords=37.77,-122.47&radius=10&up=1") == BASE + "/v4/geoSpecimens?coords=37.77%2C-122.47&radius=10"
    assert f(BASE + "/v3.1/geolocales?geolocaleName=New%20Zealand&up=1") == BASE + "/v4/geolocales?geolocaleName=New+Zealand"
    # not the admin switch, the front door, or another page: left alone
    for url in ("/v3.1/taxa?genus=camponotus", "/v3.1/taxa?up=0", "/v3.1/", "/v3.1/?up=1", "/v4/taxa?up=1",
                "/description.do?up=1", "/apiV3.1.do"):
        assert f(BASE + url) is None, url


def test_closed_requests_go_out_as_written():
    state(False, KEY)
    assert antweb.v4_open() is False
    assert get("/v3.1/taxa?genus=camponotus&limit=1&up=1").status_code == 200
    assert get("/v3.1/taxa?genus=camponotus").status_code == 200
    assert STATE["seen"] == [("/v3.1/taxa?genus=camponotus&limit=1&up=1", None), ("/v3.1/taxa?genus=camponotus", None)], STATE["seen"]


def test_open_requests_go_to_v4_with_the_key_in_the_header():
    state(True, KEY)
    assert antweb.v4_open() is True
    assert get("/v3.1/taxa?genus=camponotus&limit=1&up=1").status_code == 200
    assert STATE["seen"] == [("/v4/taxa?genus=camponotus&limit=1", KEY)], [(p, bool(k)) for p, k in STATE["seen"]]
    # v3.1's own answers, and every other address, are asked as written and never get the key
    STATE["seen"].clear()
    for path in ("/v3.1/taxa?genus=camponotus", "/description.do?up=1", "/v4/taxa?limit=1"):
        get(path)
    assert STATE["seen"] == [("/v3.1/taxa?genus=camponotus", None), ("/description.do?up=1", None), ("/v4/taxa?limit=1", None)], \
        [(p, bool(k)) for p, k in STATE["seen"]]
    # the key is never in an address
    assert all(KEY not in p for p, _ in STATE["seen"])


def test_a_minute_limit_is_waited_out():
    state(True, KEY)
    STATE["limit_once"] = True
    assert get("/v3.1/taxa?limit=1&up=1").status_code == 200
    assert [p for p, _ in STATE["seen"]] == ["/v4/taxa?limit=1", "/v4/taxa?limit=1"], STATE["seen"]


def test_without_a_key_or_with_a_refused_key_the_test_is_skipped():
    state(True, None)
    try:
        get("/v3.1/taxa?limit=1&up=1")
        raise AssertionError("not skipped without a key")
    except pytest.skip.Exception as e:
        assert "ANTWEB_TEST_API_KEY is not set" in str(e), str(e)
    assert STATE["seen"] == [], "nothing is asked without a key"
    state(True, KEY)
    STATE["refuse"] = True
    try:
        get("/v3.1/taxa?limit=1&up=1")
        raise AssertionError("not skipped with a refused key")
    except pytest.skip.Exception as e:
        assert "create-test-key.py" in str(e) and KEY not in str(e), str(e)


def test_the_state_can_be_forced_and_an_unreadable_answer_is_asked_again():
    state(False, KEY)
    os.environ["ANTWEB_API_V4"] = "open"
    try:
        assert antweb.v4_open() is True
    finally:
        os.environ.pop("ANTWEB_API_V4")
    antweb._API_STATE.clear()
    saved = antweb.BASE_URL
    antweb.BASE_URL = "http://127.0.0.1:9"   # nothing listens
    try:
        assert antweb.v4_open() is False and "open" not in antweb._API_STATE
    finally:
        antweb.BASE_URL = saved
    STATE["open"] = True
    assert antweb.v4_open() is True


def test_no_key_in_the_repository_and_the_secret_is_for_staging_only():
    wf = open(os.path.join(ROOT, ".github", "workflows", "e2e.yaml"), encoding="utf-8").read()
    line = next(x for x in wf.splitlines() if x.strip().startswith("ANTWEB_TEST_API_KEY:"))
    assert "secrets.ANTWEB_TEST_API_KEY" in line and "== 'https://staging.antweb.org' &&" in line and line.rstrip().endswith("|| '' }}"), line
    key_like = re.compile(r"aw_[A-Za-z0-9_-]{20,80}")
    allowed = re.compile(r"aw_(x{20,}|t{20,}|[A-Za-z0-9_-]*\{)")   # made-up keys of the tests
    for folder in ("tests/e2e", "tests/smoke", "tools/api", ".github/workflows", "docs/flows"):
        for dirpath, _dirs, files in os.walk(os.path.join(ROOT, folder)):
            for name in files:
                if not name.endswith((".py", ".yaml", ".yml", ".md", ".sh", ".toml", ".txt")):
                    continue
                text = open(os.path.join(dirpath, name), encoding="utf-8", errors="replace").read()
                found = [m.group(0) for m in key_like.finditer(text) if not allowed.match(m.group(0))]
                assert not found, (os.path.join(folder, name), [f[:7] + "..." for f in found])
    src = open(os.path.join(ROOT, "tests", "e2e", "antweb.py"), encoding="utf-8").read()
    assert 'os.environ.get(API_KEY_ENV' in src and 'API_KEY_ENV = "ANTWEB_TEST_API_KEY"' in src
    assert "print(" not in src.split("class ApiSession")[1].split("def new_session")[0], "the session prints nothing"


if __name__ == "__main__":
    failed = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                print("ok  ", name)
            except Exception as e:  # noqa: BLE001
                failed += 1
                import traceback
                traceback.print_exc()
                print("FAIL", name, repr(e))
    sys.exit(1 if failed else 0)
