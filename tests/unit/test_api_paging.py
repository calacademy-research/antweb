"""API v4: complete sets (api/v3.1/api_v4.py rows(), page_info(); 2026-11-10).

Why: a collaborator's extract of March 2026 was incomplete (Stenamma: 14 of 42 species) and nothing in the API's
answers showed it; metaData gave only the number of records on the page. Now, in v4:

- metaData.total = the records that match the filters (not the page), counted from the route's own filtered query, on
  the first page or with total=true; metaData.returned = the records of this answer; metaData.complete = true when
  the answer reaches the end
- afterCode=<specimen code> on /specimens and /geoSpecimens: the records after that code in specimen code order, with
  metaData.nextAfterCode; a set fetched this way holds every record exactly once also when records are added or
  removed between the pages (offset paging then skips or repeats)
- afterCode with offset is HTTP 400; v4's unknown-parameter answer knows the new names
- a total is not a record: the records-a-day count is as before
- v3.1 is unchanged while v4 is closed (no new metaData, no new parameters, the same SQL)

The data routes run against a small SQLite database, the counters against another (no MySQL needed). Run inside the
API image (CI, .github/workflows/tests.yaml):
  docker run --rm --entrypoint python -e REPO_DIR=/repo -v "$PWD/tests/unit:/t:ro" ... antweb/api:dev /t/test_api_paging.py
or locally with Flask and SQLAlchemy 1.4 installed:
  API_DIR=api/v3.1 python3 tests/unit/test_api_paging.py
"""
import contextlib
import datetime
import json
import os
import sys
import urllib.parse

sys.path.insert(0, os.environ.get("API_DIR", "/api"))
import api  # noqa: E402
import api_keys  # noqa: E402
import api_v4  # noqa: E402

from sqlalchemy import create_engine, event, text  # noqa: E402
from sqlalchemy.orm import scoped_session, sessionmaker  # noqa: E402
from sqlalchemy.pool import StaticPool  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = next((d for d in (os.environ.get("REPO_DIR"), os.path.join(HERE, "..", ".."))
             if d and os.path.isfile(os.path.join(d, "docs", "flows", "api.md"))), None)

data_engine = create_engine("sqlite://", poolclass=StaticPool, connect_args={"check_same_thread": False})
api.Base.metadata.create_all(data_engine)
api.session = scoped_session(sessionmaker(bind=data_engine))
client = api.application.test_client()

NOW = [datetime.datetime(2026, 11, 10, 10, 0, 15)]
api_v4.clock = lambda: NOW[0]
KEYS = {}
STATEMENTS = []
N = 40                      # specimens casent0000 .. casent0039, all in Madagascar, species sp0, sp1, sp2 in turn
PAGING_KEYS = ("total", "returned", "complete", "nextAfterCode")


@event.listens_for(data_engine, "before_cursor_execute")
def _record(conn, cursor, statement, parameters, context, executemany):
    STATEMENTS.append((statement, parameters))


def fresh_usage():
    """A new, empty counter database with an admin key (works while v4 is closed), a collaborator key with raised
    limits and a small key (5 records an answer, 12 a day)."""
    e = create_engine("sqlite://", poolclass=StaticPool, connect_args={"check_same_thread": False})
    with e.begin() as c:
        c.execute(text("create table api_key (id integer primary key, prefix text, key_hash text unique, name text,"
                       " email text, institution text, project text, level text default 'registered',"
                       " status text default 'pending', per_minute int, per_day int, max_records int,"
                       " records_per_day int, confirm_token_hash text, confirm_expires datetime,"
                       " created datetime default current_timestamp, confirmed datetime, last_used datetime,"
                       " revoked datetime, note text)"))
        c.execute(text("create table api_usage_daily (day date not null, key_id int not null,"
                       " requests int not null default 0, records int not null default 0,"
                       " limited int not null default 0, primary key (day, key_id))"))
        c.execute(text("create table api_rate (bucket varchar(80) not null, period char(1) not null,"
                       " start datetime not null, requests int not null default 0, records int not null default 0,"
                       " primary key (bucket, period, start))"))
    api_v4.usage = api_keys.UsageStore(e, "test-usage-password")
    KEYS.clear()
    for i, (name, level, extra) in enumerate([
            ("admin", "admin", {"per_minute": 100000, "per_day": 100000, "records_per_day": 1000000}),
            ("collaborator", "collaborator", {"per_minute": 100000, "per_day": 100000, "max_records": 10000,
                                              "records_per_day": 1000000}),
            ("small", "registered", {"max_records": 5, "records_per_day": 12, "per_minute": 1000}),
            ("registered", "registered", {}),
    ], start=1):
        key = api_keys.new_key()
        KEYS[name] = key
        cols = {"id": i, "prefix": api_keys.display_prefix(key) + str(i), "key_hash": api_keys.key_hash(key),
                "name": name, "email": name + "@example.org", "project": "test", "level": level, "status": "active"}
        cols.update(extra)
        with e.begin() as c:
            c.execute(text("insert into api_key (%s) values (%s)" % (", ".join(cols), ", ".join(":" + k for k in cols))),
                      cols)


def specimen(code, species="sp0", country="Madagascar", lat=None, lon=None, taxon="myrmicinaepheidole sp1"):
    return api.Specimen(occurrenceId="o-" + code, code=code, taxonName=taxon, genus="pheidole",
                        subfamily="myrmicinae", species=species, family="formicidae", country=country,
                        collectionCode="JTL0001", decimalLatitude=lat, decimalLongitude=lon)


def add(code, **kw):
    s = api.session()
    s.add(specimen(code, **kw))
    s.commit()
    with data_engine.begin() as c:
        c.execute(text("insert into specimen values (:c, 'LOC01', :t)"), {"c": code, "t": "myrmicinaepheidole sp1"})
    api.session.remove()


def add_original(i):
    """casent<i> as setup() made it: the first 30 are within 10 km of 37.77,-122.47 (/geoSpecimens)."""
    add("casent%04d" % i, species="sp%d" % (i % 3), lat=37.77 if i < 30 else None, lon=-122.47 if i < 30 else None)


def delete(code):
    with data_engine.begin() as c:
        c.execute(text("delete from api3_1_specimen where specimen_code = :c"), {"c": code})
        c.execute(text("delete from specimen where code = :c"), {"c": code})
    api.session.remove()


def setup():
    with data_engine.begin() as c:
        c.execute(text("create table specimen (code text primary key, localitycode text, taxon_name text)"))
    s = api.session()
    s.add(api.Taxon(taxonName="myrmicinaepheidole sp1", subfamily="myrmicinae", genus="pheidole", species="sp1",
                    taxarank="species"))
    for i in range(12):
        s.add(api.Taxon(taxonName="formicinaecamponotus t%02d" % i, subfamily="formicinae", genus="camponotus",
                        species="t%02d" % i, taxarank="species"))
    s.add(api.Geolocale(id="1", name="Madagascar", georank="country", parent="Eastern Africa", isValid="1"))
    s.add(api.GeolocaleTaxon(geolocaleId=1, taxonName="myrmicinaepheidole sp1"))
    s.commit()
    api.session.remove()
    for i in range(N):
        add_original(i)
    for i in range(7):
        add("kenya%02d" % i, country="Kenya")
    fresh_usage()


@contextlib.contextmanager
def opened():
    api.V4_OPEN = True
    try:
        yield
    finally:
        api.V4_OPEN = False


def get(url, key="collaborator", expect=200):
    headers = {"CF-Connecting-IP": "198.51.100.7"}
    if key is not None:
        headers["X-API-Key"] = KEYS[key]
    r = client.get(url, headers=headers)
    assert r.status_code == expect, (url, r.status_code, r.get_data(as_text=True)[:300])
    return r


def meta(url, key="collaborator"):
    return get(url, key).get_json()["metaData"]


def codes_of(r):
    return [rec["specimenCode"] for rec in r.get_json()["specimens"]]


MADAGASCAR = "/v4/specimens?country=Madagascar"
ALL = ["casent%04d" % i for i in range(N)]


def walk_after(url, limit, between=None, key="collaborator"):
    """Every page of url with afterCode, as the documentation's loop: (codes in the order received, pages)."""
    got, pages, after = [], 0, None
    while True:
        r = get(url + "&limit=%d" % limit + ("&afterCode=" + urllib.parse.quote(after) if after else ""), key)
        m = r.get_json()["metaData"]
        got += codes_of(r)
        pages += 1
        if between:
            between(pages)
        if m["complete"]:
            assert "nextAfterCode" not in m, m
            return got, pages
        after = m["nextAfterCode"]
        assert pages < 200, "the walk does not end"


def walk_offset(url, limit, between=None, key="collaborator"):
    got, pages = [], 0
    while True:
        r = get(url + "&limit=%d&offset=%d" % (limit, pages * limit), key)
        page = codes_of(r)
        got += page
        pages += 1
        if between:
            between(pages)
        if len(page) < limit:
            return got, pages
        assert pages < 200, "the walk does not end"


# --- total -----------------------------------------------------------------------------------------------------

def test_total_is_the_filtered_count_whatever_the_limit():
    fresh_usage()
    with opened():
        for limit in (1, 7, 39, 40, 41, 500):
            r = get(MADAGASCAR + "&limit=%d" % limit)
            m = r.get_json()["metaData"]
            assert m["total"] == N, (limit, m)
            assert m["returned"] == min(limit, N) == len(codes_of(r)) == m["count"], (limit, m)
            assert m["complete"] is (limit >= N), (limit, m)
        # without limit too, and for every filter: the total is what the filter matches
        assert meta(MADAGASCAR)["total"] == N
        assert meta("/v4/specimens?limit=3")["total"] == N + 7
        assert meta("/v4/specimens?country=Kenya&limit=3")["total"] == 7
        assert meta(MADAGASCAR + "&species=sp1&limit=2")["total"] == len([i for i in range(N) if i % 3 == 1]) == 13
        assert meta("/v4/specimens?country=Niger&limit=2") == dict(meta("/v4/specimens?country=Niger&limit=2"),
                                                                    total=0, returned=0, complete=True)
        # the v4 filters and the place filter (a join) are counted as they are answered
        assert meta("/v4/specimens?localityCode=LOC01&limit=2")["total"] == N + 7
        assert meta("/v4/specimens?collectingEventCode=nothing&limit=2")["total"] == 0
        m = meta("/v4/specimens?geolocaleName=Madagascar&limit=4")
        assert m["total"] == N and m["returned"] == 4 and m["complete"] is False, m


def test_total_and_records_come_from_one_filtered_query():
    """The count statement has the WHERE clause of the data statement, so the two cannot drift."""
    fresh_usage()
    with opened():
        del STATEMENTS[:]
        get(MADAGASCAR + "&species=sp1&minElevation=0&georeferenced=true&limit=3")
    views = [(s, p) for s, p in STATEMENTS if "api3_1_specimen" in s and "localitycode" not in s]
    count = [(s, p) for s, p in views if "count(" in s.lower()]
    data = [(s, p) for s, p in views if "count(" not in s.lower()]
    assert len(count) == 1 and len(data) == 1, [s for s, _ in views]

    def where(statement):
        w = statement[statement.index("WHERE"):]
        return w.split("ORDER BY")[0].split("LIMIT")[0].strip()
    assert where(count[0][0]) == where(data[0][0]), (where(count[0][0]), where(data[0][0]))
    assert "ORDER BY" not in count[0][0] and "LIMIT" not in count[0][0], count[0][0]
    # the same values: the data statement adds only its limit and offset
    assert tuple(data[0][1])[:len(count[0][1])] == tuple(count[0][1]), (count[0][1], data[0][1])


def test_total_only_on_the_first_page_or_when_asked():
    fresh_usage()
    with opened():
        del STATEMENTS[:]
        assert "total" in meta(MADAGASCAR + "&limit=5")
        assert "total" in meta(MADAGASCAR + "&limit=5&offset=0")
        assert "total" in meta(MADAGASCAR + "&limit=5&afterCode=")        # an empty afterCode is the start
        assert sum("count(" in s.lower() for s, _ in STATEMENTS) == 3
        del STATEMENTS[:]
        for url in (MADAGASCAR + "&limit=5&offset=5", MADAGASCAR + "&limit=5&afterCode=casent0004",
                    MADAGASCAR + "&limit=5&total=false"):
            m = meta(url)
            assert "total" not in m and m["returned"] == 5 and m["complete"] is False, (url, m)
        assert not any("count(" in s.lower() for s, _ in STATEMENTS), "a later page counted"
        # on a later page when asked: the total of the filters, not of what is left
        assert meta(MADAGASCAR + "&limit=5&offset=5&total=true")["total"] == N
        assert meta(MADAGASCAR + "&limit=5&afterCode=casent0034&total=true")["total"] == N
        r = get(MADAGASCAR + "&limit=5&total=maybe", expect=400)
        assert r.get_json()["error"] == "total must be true or false.", r.get_json()


# --- complete and nextAfterCode --------------------------------------------------------------------------------

def test_complete_and_next_after_code_on_first_middle_and_last_page():
    fresh_usage()
    with opened():
        first = get(MADAGASCAR + "&limit=15")
        m = first.get_json()["metaData"]
        assert codes_of(first) == ALL[:15]
        assert (m["total"], m["returned"], m["complete"], m["nextAfterCode"]) == (N, 15, False, "casent0014"), m
        middle = get(MADAGASCAR + "&limit=15&afterCode=" + m["nextAfterCode"])
        m = middle.get_json()["metaData"]
        assert codes_of(middle) == ALL[15:30]
        assert "total" not in m and (m["returned"], m["complete"], m["nextAfterCode"]) == (15, False, "casent0029"), m
        last = get(MADAGASCAR + "&limit=15&afterCode=" + m["nextAfterCode"])
        m = last.get_json()["metaData"]
        assert codes_of(last) == ALL[30:]
        assert (m["returned"], m["complete"]) == (10, True) and "nextAfterCode" not in m and "total" not in m, m
        # a last page that is exactly full is complete (one record more is looked for, not given)
        m = meta(MADAGASCAR + "&limit=10&afterCode=casent0029")
        assert (m["returned"], m["complete"]) == (10, True) and "nextAfterCode" not in m, m
        # after the last code: empty and complete
        m = meta(MADAGASCAR + "&limit=10&afterCode=casent0039")
        assert (m["returned"], m["complete"]) == (0, True) and "nextAfterCode" not in m, m
        # offset pages say the same, so a walk can change from offset to afterCode
        m = meta(MADAGASCAR + "&limit=15&offset=15")
        assert (m["returned"], m["complete"], m["nextAfterCode"]) == (15, False, "casent0029"), m
        assert meta(MADAGASCAR + "&limit=15&offset=30")["complete"] is True
        # a code that is not a record is a place in the order all the same
        assert codes_of(get(MADAGASCAR + "&limit=2&afterCode=casent0009x")) == ALL[10:12]


def test_after_code_walk_returns_every_record_once():
    fresh_usage()
    with opened():
        for limit in (1, 3, 7, 40, 41):
            got, pages = walk_after(MADAGASCAR, limit)
            assert got == ALL, (limit, got)
            assert pages == -(-N // limit), (limit, pages)      # a full last page is known to be the last
        got, _ = walk_after("/v4/specimens?species=sp2", 4)
        assert got == [c for i, c in enumerate(ALL) if i % 3 == 2], got
        got, _ = walk_after("/v4/specimens?geolocaleName=Madagascar", 6)
        assert got == ALL, got


def test_after_code_walk_is_complete_when_records_change_between_pages():
    """A record removed, or added, among the pages already read: offset paging skips or repeats a record,
    afterCode does neither."""
    fresh_usage()
    with opened():
        # removed: casent0002 goes after the first page of 5
        def remove(pages):
            if pages == 1:
                delete("casent0002")
        try:
            got, _ = walk_offset(MADAGASCAR, 5, remove)
            assert "casent0005" not in got and len(got) == N - 1, "offset paging skips the record that moved up"
        finally:
            add_original(2)
        try:
            got, _ = walk_after(MADAGASCAR, 5, remove)
            assert got == ALL, got            # every record that was there, once (the removed one was already read)
            assert set(ALL) - set(got) == set()
        finally:
            add_original(2)

        # added: casent0001a appears after the first page of 5
        def insert(pages):
            if pages == 1:
                add("casent0001a")
        try:
            got, _ = walk_offset(MADAGASCAR, 5, insert)
            assert got.count("casent0004") == 2, "offset paging repeats the record that moved down"
        finally:
            delete("casent0001a")
        try:
            got, _ = walk_after(MADAGASCAR, 5, insert)
            assert got == ALL and len(set(got)) == len(got), got
        finally:
            delete("casent0001a")

        # added ahead of the walk: it is in the set, once; removed ahead of the walk: it is not
        def ahead(pages):
            if pages == 2:
                add("casent0020a")
                delete("casent0031")
        try:
            got, _ = walk_after(MADAGASCAR, 5, ahead)
            assert got == sorted(set(ALL) - {"casent0031"} | {"casent0020a"}), got
        finally:
            delete("casent0020a")
            add_original(31)
        # the total of the first page and the records received disagree when the set changed: that is the check
        assert meta(MADAGASCAR + "&limit=1")["total"] == N


def test_after_code_is_a_bound_value_in_the_order_of_the_query():
    fresh_usage()
    with opened():
        del STATEMENTS[:]
        hostile = "casent0037' or '1'='1"
        r = get(MADAGASCAR + "&limit=50&afterCode=" + urllib.parse.quote(hostile))
        assert codes_of(r) == ALL[38:], codes_of(r)
        stmt, params = next((s, p) for s, p in STATEMENTS if "api3_1_specimen" in s and "ORDER BY" in s)
        assert hostile not in stmt and hostile in tuple(params), (stmt, params)
        # compared and ordered by the same column
        assert "api3_1_specimen.specimen_code > ?" in stmt and "ORDER BY api3_1_specimen.specimen_code" in stmt, stmt


# --- 400s and parameter names ----------------------------------------------------------------------------------

def test_offset_with_after_code_is_400():
    fresh_usage()
    with opened():
        for url in (MADAGASCAR + "&offset=5&afterCode=casent0004", MADAGASCAR + "&afterCode=casent0004&offset=0",
                    "/v4/geoSpecimens?coords=37.77,-122.47&afterCode=casent0004&offset=5"):
            r = get(url, expect=400)
            j = r.get_json()
            assert j["error"].startswith("Use afterCode or offset, not both."), j
            assert j["documentation"] == "https://www.antweb.org/v4/", j
        r = get("/v4/geoSpecimens?coords=37.77,-122.47&distinct=genus&afterCode=casent0004", expect=400)
        assert "cannot be used with distinct" in r.get_json()["error"], r.get_json()
        # a refused request is not counted
        with api_v4.usage.engine.connect() as c:
            assert c.execute(text("select coalesce(sum(requests), 0) from api_usage_daily")).scalar() == 0


def test_unknown_parameters_know_the_new_names():
    fresh_usage()
    with opened():
        for route in ("specimens", "geoSpecimens"):
            names = get("/v4/%s?nonsense=1" % route, expect=400).get_json()["acceptedParameters"]
            assert "afterCode" in names and "total" in names and "offset" in names, (route, names)
        for wrong in ("aftercode", "after", "cursor", "afterCod", "after_code"):
            j = get("/v4/specimens?%s=casent0004" % wrong, expect=400).get_json()
            assert j["didYouMean"] == {wrong: ["afterCode"]}, (wrong, j)
            assert "Did you mean afterCode" in j["error"], j
        assert get("/v4/specimens?totl=true", expect=400).get_json()["didYouMean"] == {"totl": ["total"]}
        # /taxa has total, not afterCode (its order is the taxon name)
        names = get("/v4/taxa?nonsense=1", expect=400).get_json()["acceptedParameters"]
        assert "total" in names and "afterCode" not in names, names
        assert get("/v4/taxa?afterCode=x", expect=400).get_json()["unknownParameters"] == ["afterCode"]
        # routes without the totals do not take the names
        assert get("/v4/images?total=true", expect=400).get_json()["unknownParameters"] == ["total"]


# --- the other list routes -------------------------------------------------------------------------------------

def test_geo_specimens_total_and_after_code():
    fresh_usage()
    near = "/v4/geoSpecimens?coords=37.77,-122.47&radius=10"
    with opened():
        r = get(near + "&limit=8")
        m = r.get_json()["metaData"]
        assert (m["total"], m["returned"], m["complete"], m["nextAfterCode"]) == (30, 8, False, "casent0007"), m
        got, pages = walk_after(near, 8)
        assert got == ALL[:30] and pages == 4, (pages, got)
        # the distinct lists (names, not specimens): total and complete, no afterCode
        m = meta(near + "&distinct=species&limit=2")
        assert (m["total"], m["returned"], m["complete"]) == (3, 2, False) and "nextAfterCode" not in m, m
        m = meta(near + "&distinct=species&limit=2&offset=2")
        assert "total" not in m and (m["returned"], m["complete"]) == (1, True), m
        m = meta(near + "&distinct=genus")
        assert (m["total"], m["returned"], m["complete"]) == (1, 1, True), m


def test_taxa_total_and_complete():
    fresh_usage()
    with opened():
        m = meta("/v4/taxa?genus=camponotus&limit=5")
        assert (m["total"], m["returned"], m["complete"]) == (12, 5, False) and "nextAfterCode" not in m, m
        m = meta("/v4/taxa?genus=camponotus&limit=5&offset=10")
        assert "total" not in m and (m["returned"], m["complete"]) == (2, True), m
        assert meta("/v4/taxa?limit=500")["total"] == 13
        # a route without the totals is as before
        m = get("/v4/bioregions").get_json()["metaData"]
        assert not [k for k in PAGING_KEYS if k in m], m


def test_ndjson_has_the_same_values_in_headers():
    fresh_usage()
    with opened():
        r = get(MADAGASCAR + "&limit=15&ndjson=true")
        lines = [json.loads(x) for x in r.get_data(as_text=True).splitlines() if x.strip()]
        assert len(lines) == 30 and lines[-1]["specimenCode"] == "casent0014", len(lines)
        h = r.headers
        assert (h["X-AntWeb-Total"], h["X-AntWeb-Returned"], h["X-AntWeb-Complete"], h["X-AntWeb-Next-After-Code"]) \
            == (str(N), "15", "false", "casent0014"), dict(h)
        r = get(MADAGASCAR + "&limit=15&ndjson=true&afterCode=casent0029")
        h = r.headers
        assert h["X-AntWeb-Complete"] == "true" and h["X-AntWeb-Returned"] == "10", dict(h)
        assert "X-AntWeb-Next-After-Code" not in h and "X-AntWeb-Total" not in h, dict(h)
        # JSON answers carry the headers too; a code with other characters is URL-encoded, ready for afterCode=
        add("casent0000 é/x")
        try:
            r = get(MADAGASCAR + "&limit=2")
            assert r.get_json()["metaData"]["nextAfterCode"] == "casent0000 é/x"
            assert r.headers["X-AntWeb-Next-After-Code"] == "casent0000%20%C3%A9%2Fx", dict(r.headers)
            nxt = get(MADAGASCAR + "&limit=2&afterCode=" + r.headers["X-AntWeb-Next-After-Code"])
            assert codes_of(nxt) == ALL[1:3], codes_of(nxt)
        finally:
            delete("casent0000 é/x")


# --- limits ----------------------------------------------------------------------------------------------------

def test_a_total_is_not_a_record_and_the_daily_limit_cuts_a_page_cleanly():
    """The key 'small': 5 records an answer, 12 a day. Totals do not use the day's records; the page cut by the day's
    limit says it is not complete and where to go on; the next day the walk ends with every record once."""
    fresh_usage()
    kenya = "/v4/specimens?country=Kenya"     # 7 records
    with opened():
        NOW[0] = datetime.datetime(2026, 11, 10, 10, 0, 15)
        r = get(MADAGASCAR + "&limit=5&total=true", "small")
        m = r.get_json()["metaData"]
        assert (m["total"], m["returned"], m["recordsLeftToday"]) == (N, 5, 7), m
        assert r.headers["X-AntWeb-Records-Left-Today"] == "7"
        r = get(MADAGASCAR + "&limit=5&total=true&afterCode=" + m["nextAfterCode"], "small")
        m = r.get_json()["metaData"]
        assert (m["total"], m["returned"], m["recordsLeftToday"]) == (N, 5, 2), m
        # 2 records left today: the page holds 2, is not complete and names the code to go on from
        r = get(MADAGASCAR + "&limit=5&afterCode=" + m["nextAfterCode"], "small")
        m = r.get_json()["metaData"]
        assert codes_of(r) == ALL[10:12], codes_of(r)
        assert (m["returned"], m["complete"], m["nextAfterCode"], m["recordsLeftToday"]) == (2, False, "casent0011", 0), m
        assert "limitNotice" in m, m
        get(MADAGASCAR + "&limit=5&afterCode=casent0011", "small", expect=429)
        with api_v4.usage.engine.connect() as c:
            assert c.execute(text("select sum(records) from api_usage_daily where key_id = 3")).scalar() == 12
        # the next day (UTC) the walk goes on from the code and ends
        NOW[0] = datetime.datetime(2026, 11, 11, 0, 0, 5)
        got, pages = walk_after(kenya, 5, key="small")
        assert got == ["kenya%02d" % i for i in range(7)] and pages == 2, (pages, got)
        # no key: 25 records an answer, as before, with the totals
        m = client.get(MADAGASCAR, headers={"CF-Connecting-IP": "203.0.113.9"}).get_json()["metaData"]
        assert (m["total"], m["returned"], m["complete"], m["nextAfterCode"]) == (N, 25, False, "casent0024"), m
    assert api_keys.ABSOLUTE_MAX_RECORDS == 10000 and api_keys.REGISTERED.max_records == 500
    assert api_keys.REGISTERED.records_per_day == 50000 and api_keys.NO_KEY.max_records == 25


def test_limit_notice_of_a_cut_page_names_after_code_for_specimens():
    """/specimens and /geoSpecimens records: continue with afterCode=<last specimenCode> (offset stays allowed, and
    the text keeps the word offset=). The lists of other routes and the distinct lists: offset=."""
    fresh_usage()
    by_code = ("Answers with your key hold at most 5 records, and 12 records a day. This answer holds 5; to continue,"
               " send afterCode=<the last specimenCode of this answer> (offset= also works).")
    with opened():
        r = get(MADAGASCAR + "&limit=100", "small")
        m = r.get_json()["metaData"]
        assert m["limitNotice"] == r.headers["X-AntWeb-Limit-Notice"] == by_code, m["limitNotice"]
        # the code the notice means is in the same answer, and going on with it works
        assert m["nextAfterCode"] == codes_of(r)[-1] == "casent0004", m
        fresh_usage()
        r = get("/v4/geoSpecimens?coords=37.77,-122.47&radius=10", "small")
        assert r.get_json()["metaData"]["limitNotice"] == by_code, r.get_json()["metaData"]
        fresh_usage()
        r = get(MADAGASCAR + "&ndjson=true", "small")
        assert r.headers["X-AntWeb-Limit-Notice"] == by_code and r.headers["X-AntWeb-Next-After-Code"] == "casent0004"
        # not specimen records: offset, as before
        by_offset = ("Answers with your key hold at most 5 records, and 12 records a day. This answer holds {};"
                     " ask for the next ones with offset=.")
        fresh_usage()
        r = get("/v4/taxa?limit=100", "small")
        assert r.get_json()["metaData"]["limitNotice"] == by_offset.format(5), r.get_json()["metaData"]
        fresh_usage()
        r = get("/v4/geoSpecimens?coords=37.77,-122.47&radius=10&distinct=species&limit=100", "small")
        assert r.get_json()["metaData"]["limitNotice"] == by_offset.format(3), r.get_json()["metaData"]
        # a page that was not cut has no notice
        fresh_usage()
        assert "limitNotice" not in meta(MADAGASCAR + "&limit=3", "small")


def test_geo_specimens_without_coords_is_400_naming_the_parameter():
    """It was HTTP 500 in v4: the v3.1 route answered plain text with HTTP 200, which v4 read as JSON. Now the route
    itself answers HTTP 400 as JSON, in v3.1 (while v4 is closed) and in v4."""
    fresh_usage()
    message = ("The coords parameter is required: the latitude and longitude of the centre of the area, for example"
               " coords=37.77,-122.47 (radius is in kilometres, default 5).")
    for url in ("/geoSpecimens?up=1", "/geoSpecimens?up=1&radius=10&limit=5", "/geoSpecimens?up=1&distinct=genus"):
        r = client.get(url)
        assert r.status_code == 400 and r.get_json() == {"error": message}, (url, r.status_code, r.get_data(as_text=True))
    with opened():
        for url in ("/v4/geoSpecimens", "/v4/geoSpecimens?radius=10&limit=5", "/v4/geoSpecimens?total=true",
                    "/v4/geoSpecimens?afterCode=casent0004", "/v4/geoSpecimens?ndjson=true"):
            r = get(url, expect=400)
            assert r.get_json() == {"error": message}, (url, r.get_data(as_text=True))
            assert "application/json" in r.headers["Content-Type"], dict(r.headers)
        # no records were used, and the next request works
        with api_v4.usage.engine.connect() as c:
            assert c.execute(text("select coalesce(sum(records), 0) from api_usage_daily")).scalar() == 0
        assert get("/v4/geoSpecimens?coords=37.77,-122.47&radius=10&limit=2").get_json()["metaData"]["returned"] == 2
        # coords that are not two numbers were 400 already
        get("/v4/geoSpecimens?coords=abc", expect=400)
    # without up=1 v3.1 is closed as before (503 comes first)
    assert client.get("/geoSpecimens").status_code == 503


def test_unimaged_geolocale_taxa_unknown_place_is_404_as_geo_species_stats():
    """It was HTTP 500 in v4: the v3.1 route answered the plain text "Must enter valid geolocale" with HTTP 200.
    Now, in v3.1 and v4: an unknown place is HTTP 404 as JSON, the answer /geoSpeciesStats gives; no geolocaleName
    is HTTP 400 naming the parameter."""
    fresh_usage()
    required = "The geolocaleName parameter is required: the name of a place, for example geolocaleName=Madagascar."
    for route in ("/unimagedGeolocaleTaxa", "/geoSpeciesStats"):
        r = client.get(route + "?up=1&geolocaleName=Atlantis")
        assert r.status_code == 404 and r.get_json() == {"error": "Geolocale not found: Atlantis"}, (route, r.status_code)
    for url in ("/unimagedGeolocaleTaxa?up=1", "/unimagedGeolocaleTaxa?up=1&geolocaleName=", "/unimagedGeolocaleTaxa?up=1&byCaste=1"):
        r = client.get(url)
        assert r.status_code == 400 and r.get_json() == {"error": required}, (url, r.status_code, r.get_data(as_text=True))
    with opened():
        for route in ("/v4/unimagedGeolocaleTaxa", "/v4/geoSpeciesStats"):
            r = get(route + "?geolocaleName=Atlantis", expect=404)
            assert r.get_json() == {"error": "Geolocale not found: Atlantis"}, (route, r.get_data(as_text=True))
            assert "application/json" in r.headers["Content-Type"], dict(r.headers)
        assert get("/v4/unimagedGeolocaleTaxa", expect=400).get_json() == {"error": required}
        assert get("/v4/unimagedGeolocaleTaxa?byCaste=true&ndjson=true", expect=400).get_json() == {"error": required}
        with api_v4.usage.engine.connect() as c:
            assert c.execute(text("select coalesce(sum(records), 0) from api_usage_daily")).scalar() == 0
    assert 'return "Must enter valid geolocale"' not in open(api.__file__).read()


def test_no_route_answers_plain_text_with_200():
    """What made v4 answer HTTP 500: a v3.1 route returning a bare string (HTTP 200, not JSON). No route does, and no
    route reads an attribute of a place that was not found."""
    import ast
    tree = ast.parse(open(api.__file__).read())
    bare = []
    for fn in ast.walk(tree):
        if isinstance(fn, ast.FunctionDef) and any(
                isinstance(d, ast.Call) and getattr(d.func, "attr", "") == "route" for d in fn.decorator_list):
            for node in ast.walk(fn):
                if isinstance(node, ast.Return) and isinstance(node.value, (ast.Constant, ast.JoinedStr, ast.BinOp)):
                    bare.append((fn.name, node.lineno))
    assert not bare, bare
    # every lookup of a place is followed by its "not found" branch
    src = open(api.__file__).read()
    calls = src.count("= getGeolocale(")
    assert calls == src.count("if geolocale is None:") and calls >= 4, (calls, src.count("if geolocale is None:"))
    # every v4 route answers JSON (never 500) without parameters and with an unknown place or name
    fresh_usage()
    with opened():
        for name in sorted(api_v4.routes()):
            for query in ("", "?geolocaleName=Atlantis", "?taxonName=nosuchtaxon", "?bioregionName=Atlantis",
                          "?specimenCode=nosuchcode", "?geolocaleId=999999", "?coords=0,0&distinct=tribe"):
                r = client.get("/v4/" + name + query, headers={"X-API-Key": KEYS["collaborator"],
                                                              "CF-Connecting-IP": "198.51.100.7"})
                if r.status_code == 400 and "Unknown parameter" in (r.get_json(silent=True) or {}).get("error", ""):
                    continue
                assert r.status_code in (200, 400, 404), (name + query, r.status_code, r.get_data(as_text=True)[:200])


def test_nan_and_inf_are_refused_not_500():
    """float() reads nan and inf, the MySQL driver refuses them (an HTTP 500 before): now HTTP 400."""
    fresh_usage()
    for url in ("/geoSpecimens?up=1&coords=nan,nan", "/geoSpecimens?up=1&coords=inf,0", "/geoSpecimens?up=1&coords=0,-inf",
                "/geoSpecimens?up=1&coords=37,-122&radius=nan", "/geoSpecimens?up=1&coords=37,-122&radius=inf",
                "/specimens?up=1&bbox=nan,1,2,3", "/specimens?up=1&bbox=1,inf,2,3"):
        r = client.get(url)
        assert r.status_code == 400, (url, r.status_code, r.get_data(as_text=True)[:200])
    assert client.get("/geoSpecimens?up=1&coords=37.77,-122.47&radius=10&limit=1").status_code == 200
    assert client.get("/specimens?up=1&bbox=40,-120,30,-125&limit=1").status_code == 200
    with opened():
        get("/v4/geoSpecimens?coords=nan,nan", expect=400)
        r = get("/v4/geoSpecimens?coords=37,-122&radius=inf", expect=400)
        assert r.get_json() == {"error": "radius must be a number of kilometres, for example radius=10"}, r.get_json()
        get("/v4/specimens?bbox=nan,1,2,3", expect=400)


def test_page_info_when_an_answer_is_cut():
    records = [{"specimenCode": "a"}, {"specimenCode": "b"}, {"specimenCode": "c"}]
    found = {"byCode": True, "complete": True, "lastCode": "c", "total": 3}
    assert api_v4.page_info(found, records, 3) == {"total": 3, "returned": 3, "complete": True}
    assert api_v4.page_info(found, records, 2) == {"total": 3, "returned": 2, "complete": False, "nextAfterCode": "b"}
    assert api_v4.page_info({"byCode": True, "complete": False, "lastCode": "c"}, records, 3) == {
        "returned": 3, "complete": False, "nextAfterCode": "c"}
    assert api_v4.page_info({"byCode": False, "complete": False}, ["x", "y"], 5) == {"returned": 2, "complete": False}
    assert api_v4.page_info(None, records, 3) is None


# --- closed: v3.1 as before ------------------------------------------------------------------------------------

def test_v31_is_unchanged_while_v4_is_closed():
    fresh_usage()
    assert api.V4_OPEN is False
    del STATEMENTS[:]
    r = client.get("/specimens?up=1&country=Madagascar&limit=15")
    assert r.status_code == 200, r.status_code
    m = r.get_json()["metaData"]
    assert sorted(m) == ["count", "limit", "offset", "parameters", "queryTime", "request"], sorted(m)
    assert m["count"] == 15 and codes_of(r) == ALL[:15]
    assert not [h for h in r.headers.keys() if h.startswith("X-AntWeb-") and h != "X-AntWeb-Limit-Notice"], dict(r.headers)
    # the same statements as before: no count, and the limit asked for (not one more)
    views = [(s, p) for s, p in STATEMENTS if "api3_1_specimen" in s]
    assert len(views) == 1 and "count(" not in views[0][0].lower(), [s for s, _ in views]
    assert tuple(views[0][1])[-2:] == (15, 0), views[0][1]
    for route, key in (("/geoSpecimens?up=1&coords=37.77,-122.47&radius=10&limit=4", "specimens"),
                       ("/geoSpecimens?up=1&coords=37.77,-122.47&radius=10&distinct=species&limit=2", "species"),
                       ("/taxa?up=1&genus=camponotus&limit=5", "taxa")):
        del STATEMENTS[:]
        r = client.get(route)
        j = r.get_json()
        assert r.status_code == 200 and len(j[key]) == int(route.rsplit("=", 1)[1]), (route, r.status_code)
        assert not [k for k in PAGING_KEYS if k in j["metaData"]], (route, j["metaData"])
        assert not any("count(" in s.lower() for s, _ in STATEMENTS), route
    # the new names are not v3.1 parameters
    for name in ("afterCode", "total"):
        r = client.get("/specimens?up=1&limit=2&%s=true" % name)
        assert r.status_code == 400 and r.get_json()["unknownParameters"] == [name], (name, r.status_code)
        assert client.get("/geoSpecimens?up=1&coords=37,-122&%s=true" % name).status_code == 400
    assert "afterCode" not in api.acceptedParams("getSpecimen") and "total" not in api.acceptedParams("getSpecimen")
    # v4 itself is closed to everyone but admin keys, with or without the new parameters
    for key in (None, "registered", "collaborator"):
        r = get(MADAGASCAR + "&afterCode=casent0004", key, expect=503)
        assert r.get_json()["error"] == api_v4.NOT_OPEN_MESSAGE
    m = meta(MADAGASCAR + "&limit=5", "admin")
    assert (m["total"], m["returned"], m["complete"], m["nextAfterCode"]) == (N, 5, False, "casent0004"), m


def test_rows_outside_v4_is_the_query_as_it_was():
    src = open(api_v4.__file__).read()
    body = src[src.index("def rows("):src.index("def specimen_filters(")]
    code = body[body.index('"""', body.index('"""') + 3):]
    first = [line.strip() for line in code.splitlines() if line.strip()][1:3]
    assert first == ["if not getattr(g, 'apiV4Inner', False):", "return query.all()"], first
    apisrc = open(api.__file__).read()
    assert apisrc.count("data = api_v4.rows(") == 4, "specimens, geoSpecimens (records and distinct), taxa"


# --- countries and islands --------------------------------------------------------------------------------------

def test_an_island_is_its_own_country():
    """Brian Fisher, 2026-11-11: "An oceanic island or archipelago with its own ant fauna is its own country in AntWeb.
    Its specimens never count toward the political parent." (Reverses the rule of 2026-11-10: Hawaii was part of
    United States.) country= compares the stored country and nothing else: Hawaii's specimens are stored with country
    Hawaii (tools/place/island_countries.py), so country=United States has none of them, in the records and in the
    total, and country=Hawaii has them all."""
    islands = ["hawaii%02d" % i for i in range(3)] + ["galap01"]
    try:
        for c in islands[:3]:
            add(c, country="Hawaii")
        add("galap01", country="Galapagos Islands")
        for i in range(2):
            add("usa%02d" % i, country="United States")
        add("ecu01", country="Ecuador")
        s = api.session()
        s.add_all([api.Geolocale(id="20", name="United States", georank="country", isValid="1"),
                   api.Geolocale(id="21", name="Hawaii", georank="country", isValid="1", parent="United States"),
                   api.Geolocale(id="22", name="Ecuador", georank="country", isValid="1"),
                   api.Geolocale(id="23", name="Galapagos Islands", georank="country", isValid="1", parent="Ecuador")])
        s.commit()
        api.session.remove()
        with opened():
            for country, expected in (("United States", ["usa00", "usa01"]), ("Hawaii", islands[:3]),
                                      ("hawaii", islands[:3]), ("Ecuador", ["ecu01"]), ("Galapagos Islands", ["galap01"])):
                r = get("/v4/specimens?country=" + country.replace(" ", "%20") + "&limit=100")
                assert codes_of(r) == expected, (country, codes_of(r))
                m = r.get_json()["metaData"]
                assert m["total"] == len(expected) and m["complete"] is True, (country, m)
            # the specimens of a place by its name (geolocaleName, georank country) follow the same stored country
            del STATEMENTS[:]
            get("/v4/specimens?geolocaleName=United%20States&limit=5")
            sql = " ".join(st for st, _p in STATEMENTS).lower()
            assert "country) = " in sql and "island" not in sql, sql[-400:]
            # paged with afterCode the parent's set still has no island record
            got, _pages = walk_after("/v4/specimens?country=United%20States", 1)
            assert got == ["usa00", "usa01"], got
            # one island record asked for inside its political parent: not there
            assert codes_of(get("/v4/specimens?country=United%20States&specimenCode=hawaii00")) == []
            assert codes_of(get("/v4/specimens?country=Hawaii&specimenCode=hawaii00")) == ["hawaii00"]
    finally:
        for c in islands + ["usa00", "usa01", "ecu01"]:
            delete(c)
        with data_engine.begin() as c:
            c.execute(text("delete from geolocale where id in ('20', '21', '22', '23')"))
        api.session.remove()


def test_the_country_filter_never_reads_the_island_column():
    """The rule is the stored country alone. A filter that also took specimens by an island column would put an
    island back into its political parent (the rule of 2026-11-10, reversed on 2026-11-11)."""
    apisrc = open(api.__file__, encoding="utf-8").read()
    assert "island_country" not in apisrc and "islandCountry" not in apisrc, "api.py reads the island column"
    v4src = open(api_v4.__file__, encoding="utf-8").read()
    assert "island_country" not in v4src and "islandCountry" not in v4src, "api_v4.py reads the island column"


def test_front_door_and_documentation_say_an_island_is_its_own_country():
    j = client.get("/v4/").get_json()
    c = j["countries"]
    assert "country=Hawaii" in c["islands"] and "never part of its political parent" in c["islands"], c
    assert "country=United States gives no record of Hawaii" in c["islands"], c
    assert "no country" in c["noCountry"], c
    if REPO is None:
        print("  (repository files not mounted, skipped)")
        return
    for path in ("web/documentation/api/apiV3_1-body.jsp", "web/llms.txt", "docs/flows/api.md"):
        with open(os.path.join(REPO, path), encoding="utf-8") as f:
            page = f.read()
        assert "Hawaii" in page and "political parent" in page, path
        if path.startswith("docs/"):
            assert "reverses the rule of 2026-11-10" in page, "the flow doc does not say which rule this replaces"
            continue
        # the public texts never say the rule of 2026-11-10 (an island is part of the country it is filed under)
        for old in ("islands filed under", "islands are included", "Hawaii under United States", "including its islands"):
            assert old not in page, (path, old)


# --- the front door and the documentation ----------------------------------------------------------------------

def test_front_door_describes_paging():
    j = client.get("/v4/").get_json()
    p = j["paging"]
    assert p["routes"] == ["/v4/specimens", "/v4/geoSpecimens", "/v4/taxa"], p
    assert "metaData.total" in p["total"] and "metaData.nextAfterCode" in p["afterCode"], p
    assert "metaData.complete" in p["complete"] and "X-AntWeb-Next-After-Code" in p["ndjson"], p


def test_documentation_names_total_and_after_code():
    if REPO is None:
        print("  (repository files not mounted, skipped)")
        return
    for path in ("web/documentation/api/apiV3_1-body.jsp", "web/llms.txt", "docs/flows/api.md"):
        with open(os.path.join(REPO, path), encoding="utf-8") as f:
            page = f.read()
        for word in ("afterCode", "total", "complete", "nextAfterCode"):
            assert word in page, (path, word)
        for line in page.splitlines():
            if "afterCode" in line or "metaData.total" in line:
                assert chr(0x2014) not in line and "nightly" not in line.lower(), (path, line[:120])
    with open(os.path.join(REPO, "web/documentation/api/apiV3_1-body.jsp"), encoding="utf-8") as f:
        jsp = f.read()
    v4 = jsp[jsp.index('id="v4"'):]
    v4 = v4[:v4.index("</ul>")]
    assert "<code>metaData.total</code>" in v4 and "<code>afterCode</code>" in v4, "the Version 4 list names both"


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
