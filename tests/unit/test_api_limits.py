"""API v3.1 (api/v3.1/api.py): per-request limit/offset, the 500-record cap and its notice,
and the streamed ndjson reply.

Runs without a database: only the request handling helpers are called.
Run inside the API image (CI, .github/workflows/tests.yaml):
  docker run --rm --entrypoint python -v "$PWD/tests/unit:/t:ro" antweb/api:dev /t/test_api_limits.py
or locally with Flask and SQLAlchemy installed: API_DIR=api/v3.1 python3 tests/unit/test_api_limits.py
"""
import inspect
import os
import re
import sys
import tempfile
import threading
from decimal import Decimal

sys.path.insert(0, os.environ.get("API_DIR", "/api"))
import api  # noqa: E402

app = api.application


def settings(query):
    with app.test_request_context("/specimens?" + query):
        from flask import request
        return api.setGlobals(request)


def test_limits():
    limit, offset, log, ndjson, start = settings("up=1")
    assert (limit, offset) == (api.DEFAULT_LIMIT, 0)
    assert settings("limit=5&offset=7")[:2] == (5, 7)
    assert settings("limit=0&offset=-3")[:2] == (api.DEFAULT_LIMIT, 0)
    assert settings("limit=x")[0] == api.DEFAULT_LIMIT
    # 2026-10-21: 500 records per request, JSON and ndjson, until registered API keys arrive.
    assert api.MAX_LIMIT == api.MAX_LIMIT_NDJSON == api.DEFAULT_LIMIT == 500
    assert settings("limit=500")[0] == 500
    assert settings("limit=501")[0] == 500
    assert settings("limit=50000")[0] == 500
    assert settings("limit=1000000&ndjson=true")[0] == 500
    assert settings("limit=1000&offset=1000")[:2] == (500, 1000)   # paging with offset still works
    # Nothing is left in module globals for another request to pick up.
    for name in ("limit", "offset", "ndjson", "startTime"):
        assert not hasattr(api, name), name


def test_threads_keep_their_own_limit():
    results = {}
    barrier = threading.Barrier(8)

    def run(n):
        with app.test_request_context(f"/specimens?limit={n}&offset={n}"):
            from flask import request
            barrier.wait()
            got = api.setGlobals(request)
            barrier.wait()   # every thread has called setGlobals before any reads its values
            results[n] = got[:2]

    threads = [threading.Thread(target=run, args=(n,)) for n in range(1, 9)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    assert results == {n: (n, n) for n in range(1, 9)}, results


def test_cap_is_a_server_setting():
    # maxLimit=<n> in etc/api-db.properties (read at start); missing or bad values give 500.
    assert api.readMaxLimit({}) == 500
    assert api.readMaxLimit({"maxLimit": "1000"}) == 1000
    assert api.readMaxLimit({"maxLimit": " 250 "}) == 250
    for bad in ("x", "0", "-5", ""):
        assert api.readMaxLimit({"maxLimit": bad}) == 500, bad
    with tempfile.NamedTemporaryFile("w", suffix=".properties", delete=False) as f:
        f.write("# comment\nuser=reader\npassword=a=b\nmaxLimit = 750\n")
    try:
        props = api._apiProperties(f.name)
    finally:
        os.unlink(f.name)
    assert props["password"] == "a=b" and api.readMaxLimit(props) == 750, props
    assert api._apiProperties("/nonexistent/api-db.properties") == {}


NOTICE = ("Requests are limited to 500 records while AntWeb moves to registered API keys. "
          "For larger data needs contact antweb@antweb.org.")


def reply(query, count):
    """metaData and notice header of a JSON reply with count records."""
    with app.test_request_context("/specimens?" + query):
        from flask import request
        api.setGlobals(request)
        resp = api.jsonify(metaData={"count": count}, specimens=[{"n": i} for i in range(count)])
        return resp.get_json()["metaData"], resp.headers.get(api.LIMIT_HEADER)


def test_notice_when_more_than_the_cap_is_asked():
    assert api.limitNoticeText() == NOTICE
    meta, header = reply("limit=1000", 500)
    assert meta["limitNotice"] == NOTICE and header == NOTICE, (meta, header)
    meta, header = reply("limit=1000", 3)    # asked for more than allowed, even if fewer exist
    assert meta["limitNotice"] == NOTICE and header == NOTICE
    meta, header = reply("", 500)            # no limit given and the reply is full
    assert meta["limitNotice"] == NOTICE and header == NOTICE
    for query, count in (("limit=500", 500), ("limit=100", 100), ("", 3), ("limit=0", 10)):
        meta, header = reply(query, count)
        assert "limitNotice" not in meta and header is None, (query, count, meta, header)


def test_notice_in_other_reply_shapes():
    with app.test_request_context("/geoSpecimens?limit=2000&distinct=genus"):
        from flask import request
        api.setGlobals(request)
        body = api.jsonify(_metaData={}, genera=["a"]).get_json()
        assert body["_metaData"]["limitNotice"] == NOTICE, body
        # Error and parameter replies are unchanged.
        resp = api.jsonify(error="x")
        assert resp.get_json() == {"error": "x"} and api.LIMIT_HEADER not in resp.headers
        resp = api.jsonify({"error": "y"})
        assert resp.get_json() == {"error": "y"} and api.LIMIT_HEADER not in resp.headers


def test_ndjson_notice_is_a_header():
    with app.test_request_context("/specimens?limit=1000000&ndjson=true"):
        from flask import request
        assert api.setGlobals(request)[0] == 500
        resp = api.getNdjson([{"a": 1}])
        assert resp.headers.get(api.LIMIT_HEADER) == NOTICE
        assert resp.is_streamed
    body = b"".join(resp.response).decode("utf8")
    assert "limitNotice" not in body   # the ndjson lines stay records only
    with app.test_request_context("/specimens?limit=10&ndjson=true"):
        from flask import request
        api.setGlobals(request)
        assert api.LIMIT_HEADER not in api.getNdjson([{"a": 1}]).headers


def test_every_endpoint_applies_the_limit():
    # Every endpoint that reads limit (setGlobals) passes it to its query. geolocales, bioregions
    # and geoSpeciesStats used to ignore it and return everything.
    checked = []
    for rule in app.url_map.iter_rules():
        fn = app.view_functions[rule.endpoint]
        src = inspect.getsource(fn)
        if "setGlobals(" not in src:
            continue
        checked.append(rule.rule)
        assert re.search(r"\.limit\(limit\)|:limit\b|\" limit \" \+", src), rule.rule + " does not apply limit"
        assert re.search(r"\.offset\(offset\)|:offset\b|\" offset \" \+", src), rule.rule + " does not apply offset"
    assert len(checked) >= 13, checked


def test_every_record_reply_carries_the_notice():
    # The notice is added by api.jsonify when the reply is built as jsonify(metaData=..., <records>=[...]).
    # Every jsonify call of an endpoint that reads limit has that shape (keywords, a metaData dict
    # first, then the record list), so none can skip the notice or count the wrong list.
    for rule in app.url_map.iter_rules():
        fn = app.view_functions[rule.endpoint]
        src = inspect.getsource(fn)
        if "setGlobals(" not in src:
            continue
        calls = re.findall(r"\bjsonify\(([^)]*)\)", src)
        assert calls, rule.rule
        for args in calls:
            assert re.match(r"\s*_?metaData=metaDataDict,\s*\w+=dataList\s*$", args), (rule.rule, args)
        assert "flaskJsonify" not in src, rule.rule


def test_parameter_errors_have_no_notice():
    # The unknown-parameter reply (before the endpoint and setGlobals run) carries lists but no notice.
    client = app.test_client()
    r = client.get("/specimens?up=1&limit=5000&specimenCod=x")
    assert r.status_code == 400
    assert "limitNotice" not in r.get_json() and api.LIMIT_HEADER not in r.headers


def test_ndjson_is_streamed_and_unchanged():
    data = [{"b": Decimal("1.5"), "a": "Ä", "n": None}, {"a": "x"}]
    with app.test_request_context("/specimens?ndjson=true"):
        resp = api.getNdjson(data)
        assert resp.is_streamed, "ndjson must be streamed"
    body = b"".join(resp.response)   # read outside the request, as the server does
    lines = body.decode("utf8").split("\n")
    assert lines == [
        '{"index": {"_index": "specimens", "_id": 0}}',
        '{"a": "Ä", "b": 1.5, "n": null}',
        '{"index": {"_index": "specimens", "_id": 1}}',
        '{"a": "x"}',
        "",
    ], lines
    assert resp.content_type == "application/json; charset=utf-8"


if __name__ == "__main__":
    failed = 0
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            try:
                fn()
                print("ok:", name)
            except AssertionError as e:
                failed += 1
                print("FAIL:", name, e)
    print("API limits", "FAILED" if failed else "OK")
    sys.exit(1 if failed else 0)
