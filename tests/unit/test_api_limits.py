"""API v3.1 (api/v3.1/api.py): per-request limit/offset and the streamed ndjson reply.

Runs without a database: only the request handling helpers are called.
Run inside the API image (CI, .github/workflows/tests.yaml):
  docker run --rm --entrypoint python -v "$PWD/tests/unit:/t:ro" antweb/api:dev /t/test_api_limits.py
or locally with Flask and SQLAlchemy installed: API_DIR=api/v3.1 python3 tests/unit/test_api_limits.py
"""
import os
import sys
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
    assert settings("limit=50000")[0] == api.MAX_LIMIT == 10000
    assert settings("limit=1000000&ndjson=true")[0] == api.MAX_LIMIT_NDJSON
    assert api.MAX_LIMIT_NDJSON <= 10000
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
