"""API v4 with free registered API keys (api/v3.1/api_v4.py, api_keys.py; step 6 PR 3, decisions by Brian Fisher
2026-11-08):

- keys only in the X-API-Key header, stored only as SHA-256, never in a log, an error or a reply; status 'active' only
- levels: no key 5 a minute, 50 a day, 25 records an answer; registered 60, 5,000, 500, 50,000 records a day;
  per-key columns override the defaults for every level
- 429 with Retry-After and Brian's text; 401 for a bad or revoked key; 503 without the usage login
- the daily record limit stops paging with offset; the counts live in the database, so they hold across workers
- v4 field names specimenCode, collectingEventCode, localityCode; v3.1 unchanged while v4 is closed
- when v4 opens, v3.1 ends (Brian Fisher 2026-11-09): every v3.1 data route 410 with a signpost, up=1 does nothing,
  /v3.1/ stays 200 with status "ended"; one switch, v4Open= in etc/api-db.properties, default closed

The data routes run against a small SQLite database, the counters against another (no MySQL needed). Run inside the
API image (CI, .github/workflows/tests.yaml):
  docker run --rm --entrypoint python -v "$PWD/tests/unit:/t:ro" -v "$PWD/docker/caddy:/caddy:ro" antweb/api:dev /t/test_api_v4.py
or locally with Flask and SQLAlchemy 1.4 installed:
  API_DIR=api/v3.1 python3 tests/unit/test_api_v4.py
"""
import contextlib
import datetime
import hashlib
import io
import os
import sys

sys.path.insert(0, os.environ.get("API_DIR", "/api"))
import api  # noqa: E402
import api_keys  # noqa: E402
import api_v4  # noqa: E402

from sqlalchemy import create_engine, text  # noqa: E402
from sqlalchemy.orm import scoped_session, sessionmaker  # noqa: E402
from sqlalchemy.pool import StaticPool  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
CADDY = next((d for d in ("/caddy", os.path.join(HERE, "..", "..", "docker", "caddy")) if os.path.isdir(d)), None)

data_engine = create_engine("sqlite://")
api.Base.metadata.create_all(data_engine)
api.session = scoped_session(sessionmaker(bind=data_engine))
client = api.application.test_client()

NOW = [datetime.datetime(2026, 11, 8, 10, 0, 15)]
api_v4.clock = lambda: NOW[0]

KEYS = {}   # name -> key (made here; only their hashes go into api_key)
SECRET = "test-usage-password"


def usage_engine():
    """The three key tables, as in db/upgrade/9.4.0/2026-11-08-api-keys.sql and -api-rate.sql, in SQLite."""
    e = create_engine("sqlite://", poolclass=StaticPool, connect_args={"check_same_thread": False})
    with e.begin() as c:
        c.execute(text("create table if not exists api_key (id integer primary key, prefix text, key_hash text unique,"
                       " name text, email text, institution text, project text, level text default 'registered',"
                       " status text default 'pending', per_minute int, per_day int, max_records int,"
                       " records_per_day int, confirm_token_hash text, confirm_expires datetime,"
                       " created datetime default current_timestamp, confirmed datetime, last_used datetime,"
                       " revoked datetime, note text)"))
        c.execute(text("create table if not exists api_usage_daily (day date not null, key_id int not null,"
                       " requests int not null default 0, records int not null default 0,"
                       " limited int not null default 0, primary key (day, key_id))"))
        c.execute(text("create table if not exists api_rate (bucket varchar(80) not null, period char(1) not null,"
                       " start datetime not null, requests int not null default 0, records int not null default 0,"
                       " primary key (bucket, period, start))"))
    return e


def fresh_usage():
    """A new, empty counter database (each test starts at zero)."""
    api_v4.usage = api_keys.UsageStore(usage_engine(), SECRET)
    KEYS.clear()
    for i, (name, level, status, extra) in enumerate([
            ("registered", "registered", "active", {}),
            ("collaborator", "collaborator", "active", {"max_records": 30, "per_minute": 1000}),
            ("admin", "admin", "active", {}),
            ("revoked", "registered", "revoked", {}),
            ("pending", "registered", "pending", {}),
            ("tight", "registered", "active", {"per_minute": 2, "per_day": 3}),
            ("pager", "registered", "active", {"max_records": 5, "records_per_day": 12, "per_minute": 1000}),
    ], start=1):
        key = api_keys.new_key()
        KEYS[name] = key
        cols = {"id": i, "prefix": api_keys.display_prefix(key) + str(i), "key_hash": api_keys.key_hash(key),
                "name": name, "email": name + "@example.org", "project": "test", "level": level, "status": status}
        cols.update(extra)
        with api_v4.usage.engine.begin() as c:
            c.execute(text("insert into api_key (%s) values (%s)" % (", ".join(cols), ", ".join(":" + k for k in cols))),
                      cols)


def setup():
    s = api.session()
    for i in range(40):
        code = "casent%04d" % i
        s.add(api.Specimen(occurrenceId="o-" + code, code=code, genus="pheidole", subfamily="myrmicinae",
                           species="sp%d" % (i % 3), family="formicidae", collectionCode="JTL%04d" % (i % 4),
                           country="Madagascar"))
    s.add(api.TaxaImage(uid=1, taxonName="myrmicinaepheidole sp1", code="casent0001", shotType="h", shotNumber="1"))
    s.commit()
    with data_engine.begin() as c:
        c.execute(text("create table specimen (code text primary key, localitycode text)"))
        for i in range(40):
            c.execute(text("insert into specimen values (:c, :l)"), {"c": "casent%04d" % i, "l": "LOC%02d" % (i % 5)})
    fresh_usage()


@contextlib.contextmanager
def opened():
    api.V4_OPEN = True
    try:
        yield
    finally:
        api.V4_OPEN = False


def get(url, key=None, ip="198.51.100.7", **headers):
    if key is not None:
        headers["X-API-Key"] = key
    headers.setdefault("CF-Connecting-IP", ip)
    return client.get(url, headers=headers)


def at(minute, second=15, day=8):
    NOW[0] = datetime.datetime(2026, 11, day, 10, minute, second)


# --- keys ------------------------------------------------------------------------------------------------------

def test_key_format_and_hash():
    key = api_keys.new_key()
    assert key.startswith("aw_") and len(key) == 43 and api_keys.KEY_RE.match(key), key
    assert api_keys.key_hash(key) == hashlib.sha256(key.encode()).hexdigest() and len(api_keys.key_hash(key)) == 64
    assert api_keys.display_prefix(key) == key[:7]
    assert api_keys.new_key() != key


def test_only_the_hash_is_stored():
    fresh_usage()
    with api_v4.usage.engine.connect() as c:
        rows = [tuple(r) for r in c.execute(text("select * from api_key"))]
    dump = repr(rows)
    assert all(k not in dump for k in KEYS.values()), "a key is stored in api_key"
    assert api_keys.key_hash(KEYS["admin"]) in dump


def test_admin_key_works_before_v4_opens():
    fresh_usage()
    assert api.V4_OPEN is False
    r = get("/v4/specimens?limit=3", KEYS["admin"])
    assert r.status_code == 200, (r.status_code, r.get_data(as_text=True)[:300])
    assert len(r.get_json()["specimens"]) == 3


def test_closed_to_everyone_else_before_v4_opens():
    fresh_usage()
    for key in (None, KEYS["registered"], KEYS["collaborator"]):
        r = get("/v4/specimens", key)
        assert r.status_code == 503 and r.get_json()["error"] == api_v4.NOT_OPEN_MESSAGE, (key and key[:3], r.status_code)
        assert r.headers.get("Retry-After") == "86400"


def test_bad_revoked_and_pending_keys_are_401():
    fresh_usage()
    with opened():
        for key in ("aw_" + "x" * 40, KEYS["revoked"], KEYS["pending"], "not-a-key", ""):
            r = get("/v4/specimens", key)
            assert r.status_code == 401, (r.status_code, r.get_data(as_text=True)[:200])
            assert r.get_json() == {"error": "This API key is not valid. Get a free key at https://www.antweb.org/apiKey.do."}


def test_key_in_the_web_address_is_refused():
    fresh_usage()
    key = KEYS["registered"]
    with opened():
        for url in ("/v4/specimens?apiKey=" + key, "/v4/specimens?key=" + key, "/v4/specimens?genus=" + key,
                    "/v4/specimens?X-API-Key=" + key):
            r = get(url)
            assert r.status_code == 400, (url[:40], r.status_code)
            body = r.get_data(as_text=True)
            assert "X-API-Key header" in r.get_json()["error"] and key not in body, body[:200]


def test_without_the_usage_login_v4_answers_503():
    saved = api_v4.usage
    try:
        assert api_v4.usage_store({"user": "apiro", "password": "x"}) is None
        api_v4.usage = None
        with opened():
            for key in (None, KEYS["admin"]):
                r = get("/v4/specimens", key)
                assert r.status_code == 503, r.status_code
                assert "API keys are not configured" in r.get_json()["error"], r.get_json()
        r = client.get("/v4/")
        assert r.status_code == 200 and r.get_json()["status"] == "not configured", r.get_json()
    finally:
        api_v4.usage = saved


def test_counter_database_down_is_closed_not_open():
    fresh_usage()
    saved = api_v4.usage.engine
    api_v4.usage.engine = create_engine("sqlite:////nonexistent/dir/x.db")
    try:
        with opened(), contextlib.redirect_stdout(io.StringIO()):
            r = get("/v4/specimens")
        assert r.status_code == 503 and r.get_json()["error"] == api_v4.STORE_ERROR_MESSAGE, r.status_code
    finally:
        api_v4.usage.engine = saved


# --- levels and 429 ---------------------------------------------------------------------------------------------

def test_no_key_five_a_minute_then_429_with_retry_after_and_the_text():
    fresh_usage()
    at(1, second=18)
    with opened():
        for i in range(5):
            r = get("/v4/taxa")
            assert r.status_code == 200, (i, r.status_code, r.get_data(as_text=True)[:200])
        r = get("/v4/taxa")
    assert r.status_code == 429, r.status_code
    assert r.headers["Retry-After"] == "42", r.headers.get("Retry-After")
    j = r.get_json()
    assert j["retryAfter"] == 42
    assert j["error"] == ("You have reached the limit for visitors without a key: 5 requests a minute. Please wait 42"
                          " seconds. For higher limits, get a free API key at https://www.antweb.org/apiKey.do. Larger"
                          " projects: write to antweb@antweb.org. Please cite AntWeb: " + api.CITATION), j["error"]
    # another visitor has their own count
    with opened():
        assert get("/v4/taxa", ip="203.0.113.9").status_code == 200
    # the next minute: open again
    at(2)
    with opened():
        assert get("/v4/taxa").status_code == 200


def test_no_key_fifty_a_day():
    fresh_usage()
    with opened():
        for i in range(50):
            at(i // 5 + 3)
            assert get("/v4/taxa?limit=1").status_code == 200, i
        at(20, second=0)
        r = get("/v4/taxa?limit=1")
    assert r.status_code == 429, r.status_code
    assert "visitors without a key: 50 requests a day" in r.get_json()["error"], r.get_json()["error"]
    assert int(r.headers["Retry-After"]) == 14 * 3600 - 20 * 60, r.headers["Retry-After"]
    assert "about 14 hours" in r.get_json()["error"]


def test_old_country_name_gives_the_current_country_and_the_answer_says_so():
    """2026-11-10 (api.currentCountry): v4 answers country=<an old name> with the current country's records, and its
    metaData names the name asked for and the name used."""
    fresh_usage()
    s = api.session()
    s.add_all([api.Geolocale(id="901", name="Madagascar", georank="country", isValid="1"),
               api.Geolocale(id="902", name="Malagasy Republic", georank="country", isValid="0", validName="Madagascar")])
    s.commit()
    try:
        r = get("/v4/specimens?limit=3&country=Malagasy%20Republic", KEYS["admin"])
        j = r.get_json()
        assert r.status_code == 200 and len(j["specimens"]) == 3, (r.status_code, r.get_data(as_text=True)[:300])
        assert j["metaData"]["countryName"] == {"requested": "Malagasy Republic", "used": "Madagascar"}, j["metaData"]
        assert j["metaData"]["parameters"] == [{"limit": "3", "country": "Malagasy Republic"}], j["metaData"]["parameters"]
        j = get("/v4/specimens?limit=3&country=Madagascar", KEYS["admin"]).get_json()
        assert len(j["specimens"]) == 3 and "countryName" not in j["metaData"], j["metaData"]
    finally:
        s.query(api.Geolocale).filter(api.Geolocale.id.in_(["901", "902"])).delete(synchronize_session=False)
        s.commit()


def test_no_key_twenty_five_records_an_answer():
    fresh_usage()
    at(5)
    with opened():
        r = get("/v4/specimens?limit=100")
    j = r.get_json()
    assert r.status_code == 200 and len(j["specimens"]) == 25, (r.status_code, len(j.get("specimens", [])))
    assert "at most 25 records" in j["metaData"]["limitNotice"] and api.LIMIT_HEADER in r.headers
    assert "get a free API key" in r.headers[api.LIMIT_HEADER]
    # the whole text: a cut page of specimens goes on with afterCode (offset stays allowed), 2026-11-10
    assert j["metaData"]["limitNotice"] == r.headers[api.LIMIT_HEADER] == (
        "Answers without a key hold at most 25 records, and 1,250 records a day. This answer holds 25; to continue,"
        " send afterCode=<the last specimenCode of this answer> (offset= also works). For more, get a free API key at"
        " https://www.antweb.org/apiKey.do."), j["metaData"]["limitNotice"]
    assert j["metaData"]["limit"] == 25 and j["metaData"]["level"] == "no key"


def test_registered_key_sixty_a_minute_and_500_records():
    fresh_usage()
    at(6, second=0)
    with opened():
        for i in range(60):
            assert get("/v4/taxa?limit=1", KEYS["registered"]).status_code == 200, i
        r = get("/v4/taxa?limit=1", KEYS["registered"])
        assert r.status_code == 429 and r.headers["Retry-After"] == "60", (r.status_code, r.headers.get("Retry-After"))
        msg = r.get_json()["error"]
        assert msg.startswith("You have reached the limit for your key: 60 requests a minute. Please wait 60 seconds."), msg
        assert "get a free API key" not in msg and "write to antweb@antweb.org" in msg and api.CITATION in msg, msg
    assert api_keys.limits_for(api_keys.KeyRow(1, "registered", "active", None, None, None, None)) == \
        api_keys.Limits(60, 5000, 500, 50000)
    assert api_keys.NO_KEY == api_keys.Limits(5, 50, 25, 1250)


def test_per_key_columns_override_the_defaults():
    fresh_usage()
    row = api_keys.KeyRow(9, "collaborator", "active", 300, None, 20000, None)
    assert api_keys.limits_for(row) == api_keys.Limits(300, 5000, 10000, 50000)   # capped at 10,000 an answer
    row = api_keys.KeyRow(9, "registered", "active", None, 10, None, 99)
    assert api_keys.limits_for(row) == api_keys.Limits(60, 10, 500, 99)            # any level
    at(7, second=30)
    with opened():
        assert get("/v4/taxa", KEYS["tight"]).status_code == 200
        assert get("/v4/taxa", KEYS["tight"]).status_code == 200
        r = get("/v4/taxa", KEYS["tight"])
        assert r.status_code == 429 and "your key: 2 requests a minute" in r.get_json()["error"], r.get_json()
        at(8)
        assert get("/v4/taxa", KEYS["tight"]).status_code == 200
        at(9)
        r = get("/v4/taxa", KEYS["tight"])
        assert r.status_code == 429 and "your key: 3 requests a day" in r.get_json()["error"], r.get_json()
        r = get("/v4/specimens?limit=100", KEYS["collaborator"])
        assert r.status_code == 200 and len(r.get_json()["specimens"]) == 30, len(r.get_json()["specimens"])


def test_daily_record_limit_stops_offset_paging():
    fresh_usage()
    at(10)
    seen = []
    with opened():
        for offset in (0, 5, 10):
            r = get("/v4/specimens?offset=%d" % offset, KEYS["pager"])
            assert r.status_code == 200, (offset, r.status_code)
            seen += [s["specimenCode"] for s in r.get_json()["specimens"]]
        assert len(seen) == 12 and len(set(seen)) == 12, seen   # 5 + 5 + the 2 left of 12 a day
        assert r.get_json()["metaData"]["recordsLeftToday"] == 0
        r = get("/v4/specimens?offset=12", KEYS["pager"])
        assert r.status_code == 429, r.status_code
        assert "your key: 12 records a day" in r.get_json()["error"], r.get_json()["error"]
        # a small answer charges only what it returned (the reservation is given back)
    fresh_usage()
    with opened():
        get("/v4/specimens?specimenCode=casent0001", KEYS["pager"])
        r = get("/v4/specimens?limit=5", KEYS["pager"])
        assert r.get_json()["metaData"]["recordsLeftToday"] == 12 - 1 - 5, r.get_json()["metaData"]


def test_counts_are_in_the_database_and_shared_by_workers():
    fresh_usage()
    other_worker = api_keys.UsageStore(api_v4.usage.engine, SECRET)   # a second process, same database
    first_worker = api_v4.usage
    at(11)
    with opened():
        try:
            for i in range(2):
                api_v4.usage = first_worker if i % 2 == 0 else other_worker
                assert get("/v4/taxa", KEYS["tight"]).status_code == 200
            api_v4.usage = other_worker
            assert get("/v4/taxa", KEYS["tight"]).status_code == 429
        finally:
            api_v4.usage = first_worker
        get("/v4/specimens?limit=4", KEYS["registered"])
        get("/v4/specimens?limit=3")
    with first_worker.engine.connect() as c:
        daily = {r[0]: r[1:] for r in c.execute(text("select key_id, requests, records, limited from api_usage_daily"))}
        buckets = [r[0] for r in c.execute(text("select bucket from api_rate"))]
    tight = [i for i, n in enumerate(KEYS, 1) if n == "tight"][0]
    assert daily[tight] == (2, 0, 1), daily        # two answered (taxa has no rows here), one refused
    assert daily[1] == (1, 4, 0), daily            # registered: one request, 4 records
    assert daily[0] == (1, 3, 0), daily            # key_id 0: all visitors without a key
    assert all(b.startswith("key:") or (b.startswith("ip:") and len(b) == 67) for b in buckets), buckets
    assert not any("198.51.100.7" in b for b in buckets), "a raw IP is stored"


def test_deadlocks_are_retried():
    """Real MySQL 8.0 (2026-11-08): two workers adding the first request of a new minute can deadlock (error 1213);
    the transaction is tried again. Other errors are not retried."""
    from sqlalchemy.exc import OperationalError

    class Orig(Exception):
        pass
    calls = []

    def flaky():
        calls.append(1)
        if len(calls) < 3:
            raise OperationalError("insert", {}, Orig(1213, "Deadlock found"))
        return "done"
    assert api_keys.retried(flaky) == "done" and len(calls) == 3
    calls.clear()

    def broken():
        calls.append(1)
        raise OperationalError("insert", {}, Orig(1142, "denied"))
    try:
        api_keys.retried(broken)
    except OperationalError:
        pass
    else:
        raise AssertionError("a permission error was swallowed")
    assert len(calls) == 1


def test_ip_hash_changes_every_day_and_needs_the_secret():
    d1, d2 = datetime.date(2026, 11, 8), datetime.date(2026, 11, 9)
    h = api_keys.ip_hash("198.51.100.7", SECRET, d1)
    assert len(h) == 64 and h == api_keys.ip_hash("198.51.100.7", SECRET, d1)
    assert h != api_keys.ip_hash("198.51.100.7", SECRET, d2) and h != api_keys.ip_hash("198.51.100.7", "other", d1)
    assert h != hashlib.sha256(b"198.51.100.7").hexdigest()
    assert api_keys.client_ip({"CF-Connecting-IP": "1.2.3.4", "X-Forwarded-For": "5.6.7.8"}, "9.9.9.9") == "1.2.3.4"
    assert api_keys.client_ip({"X-Forwarded-For": "6.6.6.6, 5.6.7.8"}, "9.9.9.9") == "5.6.7.8"
    assert api_keys.client_ip({}, "9.9.9.9") == "9.9.9.9"


def test_ipv6_visitors_are_counted_per_64_network():
    assert api_keys.visitor_network("198.51.100.7") == "198.51.100.7"
    assert api_keys.visitor_network("2001:db8:1:2:aaaa::1") == "2001:db8:1:2::/64"
    assert api_keys.visitor_network("2001:db8:1:2:bbbb:cccc:dddd:eeee") == "2001:db8:1:2::/64"
    assert api_keys.visitor_network("2001:db8:1:3::1") == "2001:db8:1:3::/64"
    assert api_keys.visitor_network("::ffff:198.51.100.7") == "198.51.100.7"
    assert api_keys.visitor_network("not an ip") == "not an ip"
    fresh_usage()
    at(4, second=10)
    with opened():
        # five addresses of one /64 share the five requests a minute
        for i in range(5):
            assert get("/v4/taxa", ip="2001:db8:1:2::%x" % (i + 1)).status_code == 200, i
        assert get("/v4/taxa", ip="2001:db8:1:2:ffff::9").status_code == 429
        # another /64 has its own count; IPv4 addresses are still one each
        assert get("/v4/taxa", ip="2001:db8:1:3::1").status_code == 200
        assert get("/v4/taxa", ip="203.0.113.10").status_code == 200
    with api_v4.usage.engine.connect() as c:
        buckets = [r[0] for r in c.execute(text("select bucket from api_rate"))]
    assert not any("2001:db8" in b for b in buckets), buckets


# --- the v3.1 admin switch up=1 -----------------------------------------------------------------------------------

def test_up_switch_before_v4_opens_is_as_before():
    fresh_usage()
    assert api.V4_OPEN is False
    assert client.get("/specimens?up=1&limit=2").status_code == 200
    assert client.get("/specimens?limit=2").status_code == 503
    # works without the usage login too
    saved = api_v4.usage
    api_v4.usage = None
    try:
        assert client.get("/specimens?up=1&limit=2").status_code == 200
    finally:
        api_v4.usage = saved


def v31_data_routes():
    return sorted(r.rule for r in api.application.url_map.iter_rules()
                  if r.endpoint not in api.UNCHECKED_ENDPOINTS and "<" not in r.rule)


SIGNPOST = {"error", "status", "v4", "getAKey", "documentation", "terms", "guideForPrograms", "citation", "license"}


def test_v31_has_ended_once_v4_opens_every_data_route_is_410_with_the_signpost():
    """Decided by Brian Fisher 2026-11-09: when v4 opens, v3.1 ends. Every data route answers 410 Gone with a short
    JSON signpost; up=1 and keys change nothing."""
    fresh_usage()
    routes = v31_data_routes()
    assert len(routes) >= 13, routes
    with opened():
        for route in routes:
            for url, headers in ((route, {}), (route + "?up=1", {}), (route + "?up=1&limit=2", {"X-API-Key": KEYS["admin"]}),
                                 (route + "?limit=2", {"X-API-Key": KEYS["registered"]}),
                                 (route + "?up=1", {"X-API-Key": "nonsense"}), (route + "?noSuchParameter=1", {})):
                r = client.get(url, headers=headers)
                assert r.status_code == 410, (url, r.status_code, r.get_data(as_text=True)[:200])
                j = r.get_json()
                assert set(j) == SIGNPOST, (url, sorted(j))
                assert j["status"] == "ended" and j["error"] == api.API_ENDED_MESSAGE, j
                assert j["v4"] == "https://www.antweb.org/v4/" and j["getAKey"] == "https://www.antweb.org/apiKey.do", j
                assert j["documentation"] == api.API_DOCS_URL and j["terms"] == api.API_TERMS_URL, j
                assert j["citation"] == api.CITATION and j["license"] == api.LICENSE, j
                assert "Retry-After" not in r.headers and r.headers["Cache-Control"] == "no-store", dict(r.headers)
                assert 'rel="license"' in r.headers.get("Link", ""), dict(r.headers)
        assert "has ended" in api.API_ENDED_MESSAGE and "https://www.antweb.org/v4/" in api.API_ENDED_MESSAGE
        assert "https://www.antweb.org/apiKey.do" in api.API_ENDED_MESSAGE
        # an unknown route stays a JSON 404
        assert client.get("/noSuchRoute?up=1").status_code == 404
        # without the usage login too (v3.1 needs no key lookup any more)
        saved = api_v4.usage
        api_v4.usage = None
        try:
            assert client.get("/specimens?up=1", headers={"X-API-Key": KEYS["admin"]}).status_code == 410
        finally:
            api_v4.usage = saved
    # closed again (the rollback): exactly as before
    assert client.get("/specimens?limit=2").status_code == 503
    assert client.get("/specimens?up=1&limit=2").status_code == 200


def test_up_has_no_effect_once_v4_opens():
    fresh_usage()
    with opened():
        with api.application.test_request_context("/specimens?up=1"):
            assert api.down(api.request) == 1
        # no key lookup for v3.1: the counter database is not asked
        saved_engine = api_v4.usage.engine
        api_v4.usage.engine = create_engine("sqlite:////nonexistent/dir/x.db")
        try:
            assert client.get("/specimens?up=1", headers={"X-API-Key": KEYS["admin"]}).status_code == 410
        finally:
            api_v4.usage.engine = saved_engine
    assert not hasattr(api, "upSwitch"), "the admin-key variant of up=1 is gone (v3.1 ends when v4 opens)"


def test_every_v4_route_answers_once_open():
    fresh_usage()
    at(40)
    urls = {"geoSpecimens": "?coords=-18.9,47.5&radius=10&limit=1", "geoSpeciesStats": "?geolocaleName=Madagascar&limit=1"}
    with opened():
        for name in sorted(api_v4.routes()):
            r = get("/v4/" + name + urls.get(name, "?limit=1"), KEYS["collaborator"])
            # the small SQLite database lacks some tables and functions of MySQL: an inner 500 or 404 is still the
            # route answering; never 410, 503 or 401
            assert r.status_code in (200, 400, 404, 500), (name, r.status_code, r.get_data(as_text=True)[:200])
        r = get("/v4/specimens?limit=2")
        assert r.status_code == 200 and len(r.get_json()["specimens"]) == 2, r.status_code
        r = get("/v4/taxaImages?limit=2", KEYS["registered"])
        assert r.status_code == 200, r.status_code


def test_the_switch_is_one_line_in_the_settings_file():
    """v4Open=true in etc/api-db.properties (api.readV4Open); default closed; API_V4_OPEN in the environment wins."""
    assert api.V4_OPEN is False, "closed unless the server says otherwise"
    read = api.readV4Open
    assert read({}, {}) is False
    for value in ("true", "True", " TRUE ", "1", "yes", "on"):
        assert read({"v4Open": value}, {}) is True, value
    for value in ("", "false", "0", "no", "off", "open", "tru"):
        assert read({"v4Open": value}, {}) is False, value
    assert read({"v4open": "true"}, {}) is False and read({"V4_OPEN": "true"}, {}) is False, "the name is v4Open"
    assert read({"v4Open": "true"}, {"API_V4_OPEN": "false"}) is False
    assert read({"v4Open": "false"}, {"API_V4_OPEN": "true"}) is True
    assert read({"v4Open": "true"}, {"API_V4_OPEN": " "}) is True
    # as the file is read: spaces around = are fine, a commented line does not count
    import tempfile
    with tempfile.NamedTemporaryFile("w", suffix=".properties", delete=False) as f:
        f.write("user=apiro\npassword=x\n#v4Open=true\n")
        path = f.name
    try:
        assert read(api._apiProperties(path), {}) is False
        with open(path, "a") as f:
            f.write("v4Open = true\n")
        assert read(api._apiProperties(path), {}) is True
    finally:
        os.unlink(path)
    # a fresh start with the switch on: v4 open, the v3.1 data route 410, the root 200 "ended"
    import subprocess
    code = ("import sys; sys.path.insert(0, %r); import api; c = api.application.test_client();"
            " print(api.V4_OPEN, c.get('/specimens').status_code, c.get('/').status_code,"
            " c.get('/').get_json()['status'], c.get('/v4/').get_json()['status'])" % os.path.dirname(api.__file__))
    for value, expected in (("true", "True 410 200 ended"), ("", "False 503 200 closed")):
        out = subprocess.run([sys.executable, "-c", code], env=dict(os.environ, API_V4_OPEN=value),
                             capture_output=True, text=True, timeout=120)
        last = out.stdout.strip().splitlines()[-1] if out.stdout.strip() else out.stderr[-300:]
        assert last.startswith(expected), (value, last)
    src = open(api.__file__).read()
    assert "V4_OPEN = readV4Open(_apiProps)" in src and "\nV4_OPEN = True" not in src


def test_no_key_day_is_1250_records_and_ends_at_midnight_utc():
    fresh_usage()
    assert api_keys.NO_KEY.records_per_day == 1250 and api_keys.NO_KEY.max_records == 25
    assert "timezone.utc" in open(api_keys.__file__).read().split("def utcnow")[1][:120], "API days are UTC days"
    with opened():
        got = 0
        for i in range(50):
            NOW[0] = datetime.datetime(2026, 11, 8, 12 + i // 5, i % 5, 30)
            r = get("/v4/specimens?limit=25", ip="203.0.113.50")
            assert r.status_code == 200, (i, r.status_code)
            got += len(r.get_json()["specimens"])
        assert got == 1250, got
        assert r.headers["X-AntWeb-Records-Left-Today"] == "0", dict(r.headers)
        NOW[0] = datetime.datetime(2026, 11, 8, 23, 59, 50)
        r = get("/v4/specimens?limit=1", ip="203.0.113.50")
        assert r.status_code == 429 and r.headers["Retry-After"] == "10", (r.status_code, r.headers.get("Retry-After"))
        # another address has its own day
        assert get("/v4/specimens?limit=1", ip="203.0.113.51").status_code == 200
        NOW[0] = datetime.datetime(2026, 11, 9, 0, 0, 1)
        r = get("/v4/specimens?limit=25", ip="203.0.113.50")
        assert r.status_code == 200 and len(r.get_json()["specimens"]) == 25, r.status_code
        assert r.headers["X-AntWeb-Records-Left-Today"] == "1225", dict(r.headers)
    at(0)


def test_v4_does_not_use_up_and_is_unaffected_by_the_switch():
    fresh_usage()
    run_code = open(api_v4.__file__).read().split("def run(")[1].split("def level_name")[0]
    assert "inner['up']" not in run_code and "g.apiV4Inner = True" in run_code
    # closed: admin key works; open: no key and registered key work, without any up parameter
    assert get("/v4/specimens?limit=2", key=KEYS["admin"]).status_code == 200
    with opened():
        assert get("/v4/specimens?limit=2").status_code == 200
        assert get("/v4/specimens?limit=2", key=KEYS["registered"]).status_code == 200
        assert get("/v4/specimens?up=1").status_code == 400
    # the flag is set only inside v4's inner request, never for a v3.1 request
    with api.application.test_request_context("/specimens"):
        assert api.down(api.request) == 1


def test_the_key_is_never_in_logs_or_replies():
    fresh_usage()
    key = KEYS["admin"]
    out, err = io.StringIO(), io.StringIO()
    replies = []
    saved = api_v4.add_locality_codes
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        at(12)
        replies.append(get("/v4/specimens?limit=2", key))
        replies.append(get("/v4/specimens?limit=2&ndjson=true", key))
        replies.append(get("/v4/specimens?specimenCod=x", key))            # 400
        replies.append(get("/v4/specimens?minElevation=abc", key))         # 400 inside v3.1
        replies.append(get("/v4/specimens?apiKey=" + key))                 # key in the address
        replies.append(get("/v4/specimens", key[:-1] + "Z"))               # 401

        def boom(*a, **k):
            raise RuntimeError("forced error")
        api_v4.add_locality_codes = boom
        try:
            replies.append(get("/v4/specimens?limit=1", key))              # 500
        finally:
            api_v4.add_locality_codes = saved
        with opened():
            for _ in range(5):
                replies.append(get("/v4/taxa?limit=1", KEYS["tight"]))     # 429s
    assert [r.status_code for r in replies[:7]] == [200, 200, 400, 400, 400, 401, 500], [r.status_code for r in replies]
    assert replies[-1].status_code == 429
    logs = out.getvalue() + err.getvalue()
    assert "forced error" in logs, "the test did not reach the error log"
    for k in KEYS.values():
        assert k not in logs, "a key is in the log"
        for r in replies:
            assert k not in r.get_data(as_text=True) and k not in str(r.headers), "a key is in a reply"


# --- v4 replies -------------------------------------------------------------------------------------------------

def test_v4_field_names_and_locality_code():
    fresh_usage()
    at(13)
    r = get("/v4/specimens?limit=5", KEYS["admin"])
    sp = r.get_json()["specimens"]
    assert len(sp) == 5
    for s in sp:
        assert "collectionCode" not in s and s["collectingEventCode"].startswith("JTL"), s
        assert s["localityCode"] == "LOC%02d" % (int(s["specimenCode"][6:]) % 5), s
    body = r.get_data(as_text=True)
    assert "collectionCode" not in body
    j = r.get_json()["metaData"]
    assert j["request"] == "GET /v4/specimens?limit=5" and j["parameters"] == [{"limit": "5"}], j
    assert "up=1" not in body and '"up"' not in body, "v4 replies do not show the v3.1 admin switch"
    # ndjson: the same names
    r = get("/v4/specimens?limit=2&ndjson=true", KEYS["admin"])
    lines = [l for l in r.get_data(as_text=True).splitlines() if l]
    assert len(lines) == 4 and '"collectingEventCode"' in lines[1] and '"localityCode"' in lines[1], lines[:2]
    # /taxaImages: specimen code under specimenCode
    r = get("/v4/taxaImages?taxonName=myrmicinaepheidole%20sp1", KEYS["admin"])
    assert r.status_code == 200, r.get_data(as_text=True)[:300]
    specimen = r.get_json()["taxaImages"][0]["specimen"][0]
    assert specimen["specimenCode"] == "casent0001" and "code" not in specimen, specimen


def test_v4_filters_collecting_event_and_locality_code():
    fresh_usage()
    at(14)
    sp = get("/v4/specimens?collectingEventCode=JTL0002", KEYS["admin"]).get_json()["specimens"]
    assert sp and all(s["collectingEventCode"] == "JTL0002" for s in sp) and len(sp) == 10, len(sp)
    sp = get("/v4/specimens?localityCode=LOC03", KEYS["admin"]).get_json()["specimens"]
    assert sp and all(s["localityCode"] == "LOC03" for s in sp) and len(sp) == 8, len(sp)
    sp = get("/v4/specimens?localityCode=LOC03&collectingEventCode=JTL0003", KEYS["admin"]).get_json()["specimens"]
    assert [s["specimenCode"] for s in sp] == ["casent0003", "casent0023"], sp


def test_v4_unknown_parameters_use_v4_names():
    fresh_usage()
    at(15)
    j = get("/v4/specimens?collectionCode=JTL0002", KEYS["admin"]).get_json()
    assert j["didYouMean"] == {"collectionCode": ["collectingEventCode"]}, j
    assert "up" not in j["acceptedParameters"] and "log" not in j["acceptedParameters"], j["acceptedParameters"]
    assert "localityCode" in j["acceptedParameters"] and "collectingEventCode" in j["acceptedParameters"]
    assert get("/v4/specimens?up=1", KEYS["admin"]).status_code == 400
    assert get("/v4/specimens?catalogNumber=x", KEYS["admin"]).get_json()["didYouMean"] == {"catalogNumber": ["specimenCode"]}
    assert get("/v4/noSuchRoute", KEYS["admin"]).status_code == 404


def test_every_v31_data_route_has_a_v4_route():
    fresh_usage()
    at(16)
    v31 = sorted(r.rule.lstrip("/") for r in api.application.url_map.iter_rules()
                 if r.endpoint not in api.UNCHECKED_ENDPOINTS and "<" not in r.rule)
    assert sorted(api_v4.routes()) == v31 and len(v31) >= 13, v31
    front = client.get("/v4/").get_json()
    assert front["routes"] == ["/v4/" + r for r in v31], front["routes"]


def test_front_doors():
    fresh_usage()
    j = client.get("/v4/").get_json()
    assert j["name"] == "Antweb API v4" and j["status"] == "closed" and j["keyHeader"] == "X-API-Key", j
    assert j["limits"]["withoutKey"] == {"requestsPerMinute": 5, "requestsPerDay": 50, "recordsPerAnswer": 25,
                                         "recordsPerDay": 1250}, j["limits"]
    assert j["limits"]["registeredKey"] == {"requestsPerMinute": 60, "requestsPerDay": 5000, "recordsPerAnswer": 500,
                                            "recordsPerDay": 50000}, j["limits"]
    assert {f["name"]: f["darwinCore"] for f in j["fieldNames"]} == {
        "specimenCode": "catalogNumber", "collectingEventCode": "eventID, fieldNumber", "localityCode": "locationID"}
    assert j["getAKey"] == "https://www.antweb.org/apiKey.do"
    v31 = client.get("/")
    assert v31.status_code == 200 and v31.get_json()["status"] == "closed" and "Antweb API" in v31.get_json()["name"]
    with opened():
        j = client.get("/v4/")
        assert j.status_code == 200 and j.get_json()["status"] == "open"
        assert j.get_json()["message"].startswith("The AntWeb API v4 is open")
        # the v3.1 front door stays 200 (uptime checks, old clients) and says "ended"
        r = client.get("/")
        j = r.get_json()
        assert r.status_code == 200 and j["name"] == "Antweb API V3.1" and j["status"] == "ended", (r.status_code, j)
        assert j["message"] == api.API_ENDED_MESSAGE and j["v4"]["status"] == "open", j
        assert j["v4"]["url"] == "https://www.antweb.org/v4/" and j["v4"]["getAKey"] == "https://www.antweb.org/apiKey.do"
        assert j["citation"] == api.CITATION and j["license"] == api.LICENSE and j["documentation"] == api.API_DOCS_URL
        assert client.get("/?up=1").get_json()["status"] == "ended"
    r = client.get("/v4")
    assert r.status_code in (301, 302, 308) and r.headers["Location"].endswith("/v4/"), r.status_code
    v31 = client.get("/").get_json()
    assert v31["v4"]["status"] == "closed" and v31["v4"]["url"] == "https://www.antweb.org/v4/", v31.get("v4")


def test_v31_is_unchanged():
    r = client.get("/specimens")
    assert r.status_code == 503 and r.get_json()["error"] == api.API_DOWN_MESSAGE == api.API_CLOSED_MESSAGE
    r = client.get("/specimens?up=1&limit=3")
    assert r.status_code == 200 and "collectionCode" in r.get_data(as_text=True)
    assert "collectingEventCode" not in r.get_data(as_text=True) and "localityCode" not in r.get_data(as_text=True)
    assert client.get("/specimens?up=1&collectingEventCode=JTL0002").status_code == 400
    # once v4 opens, v3.1 has ended and its message points to v4
    msg = api.apiDownMessage(True)
    assert msg == api.API_ENDED_MESSAGE
    assert "https://www.antweb.org/v4/" in msg and "https://www.antweb.org/apiKey.do" in msg, msg


def test_caddy_routes_v4_and_drops_the_key_and_token_from_its_log():
    if CADDY is None:
        print("  (docker/caddy not mounted, skipped)")
        return
    for name in ("Caddyfile", "Caddyfile.common"):
        text_ = open(os.path.join(CADDY, name)).read()
        assert "@api_v4 path /v4 /v4/*" in text_ and "handle @api_v4" in text_, name
        v4 = text_[text_.index("handle @api_v4"):]
        assert v4.index("reverse_proxy api:5000") < v4.index("}"), name
        assert "handle_path @api_v4" not in text_, name + ": /v4/ keeps its path"
        assert "request>headers>X-Api-Key delete" in text_, name + ": the key header must not reach the access log"
        # the one-time token of the confirmation link (apiKeyConfirm.do?token=...) is dropped from the logged address
        f = text_[text_.index("format filter"):]
        q = f[f.index("request>uri query {"):]
        assert q.index("delete token") < q.index("}"), name + ": the confirmation token must not reach the access log"


if __name__ == "__main__":
    setup()
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
