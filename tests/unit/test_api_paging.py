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
- ants only by default (2026-11-12; Brian Fisher's definition of 2026-11-10: "Country search: every ant specimen
  record whose country in AntWeb is the country", the same definition as the data extract): /specimens and
  /geoSpecimens answer for family Formicidae and the records with no family given, in the records, the total and the
  distinct lists; allFamilies=true gives every family, family=<name> that family; metaData.families says which; the
  condition is the extract's own; v3.1 answers for every family as before. The same rule, by the same helper, on the
  other routes that read specimens and take a place filter: /geoSpeciesStats, /distinctTaxa, /measurements

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


def specimen(code, species="sp0", country="Madagascar", lat=None, lon=None, taxon="myrmicinaepheidole sp1",
             family="formicidae", genus="pheidole"):
    return api.Specimen(occurrenceId="o-" + code, code=code, taxonName=taxon, genus=genus,
                        subfamily="myrmicinae", species=species, family=family, country=country,
                        collectionCode="JTL0001", decimalLatitude=lat, decimalLongitude=lon)


def add(code, **kw):
    s = api.session()
    s.add(specimen(code, **kw))
    s.commit()
    # the specimen table itself (the localityCode lookup of v4, and the SQL of /geoSpeciesStats)
    with data_engine.begin() as c:
        c.execute(text("insert into specimen (code, localitycode, taxon_name, genus, species, caste, status, country, family)"
                       " values (:c, 'LOC01', :t, :g, :s, 'worker', 'valid', :country, :f)"),
                  {"c": code, "t": kw.get("taxon", "myrmicinaepheidole sp1"), "g": kw.get("genus", "pheidole"),
                   "s": kw.get("species", "sp0"), "country": kw.get("country", "Madagascar"),
                   "f": kw.get("family", "formicidae")})
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
        c.execute(text("create table specimen (code text primary key, localitycode text, taxon_name text, genus text,"
                       " species text, caste text, subcaste text, status text, country text, family text)"))
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
        assert meta("/v4/specimens?limit=3&total=true", key="registered")["total"] == N + 7      # without a filter: only when asked, with a key
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


def test_no_total_for_a_request_without_a_filter_unless_asked():
    """Review of pull request 348 (2026-11-12): the first page of /v4/specimens with no filter, with or without a
    key, made the database count every record of the specimen view (it joins taxon, and the ants-only test reads
    every row); only the per-minute limit stood in the way. Now such a request gets no total unless it says
    total=true, and the answer says that the total is not given and how to ask. A request with a filter keeps its
    total on the first page."""
    fresh_usage()
    with opened():
        urls = ("/v4/specimens?limit=3", "/v4/specimens?limit=3&offset=0", "/v4/specimens?limit=3&afterCode=",
                "/v4/specimens?limit=3&allFamilies=true", "/v4/specimens?limit=3&family=", "/v4/specimens?limit=3&genus=*")
        # with a key or without (without: 5 requests a minute, so the first three)
        for key, some in (("collaborator", urls), ("registered", urls), (None, urls[:3])):
            for url in some:
                del STATEMENTS[:]
                r = get(url, key)
                m = r.get_json()["metaData"]
                assert "total" not in m and "X-AntWeb-Total" not in r.headers, (key, url, m)
                assert "total=true" in m["totalNotGiven"] and "without a filter" in m["totalNotGiven"], (url, m)
                assert r.headers["X-AntWeb-Total-Not-Given"] == "add total=true"
                assert m["returned"] == len(codes_of(r)) > 0 and m["complete"] is False, (url, m)
                assert not any("count(" in s.lower() for s, _ in STATEMENTS), (url, "the unfiltered request counted")
        # an answer that holds every record needs no count to know the total (47 records, 500 an answer)
        del STATEMENTS[:]
        m = meta("/v4/specimens", key="registered")
        assert m["total"] == N + 7 == m["returned"] and m["complete"] is True and "totalNotGiven" not in m, m
        assert not any("count(" in s.lower() for s, _ in STATEMENTS)
        # a blank value is no filter for the total either; here it finds nothing, so the answer is whole: 0, no count
        del STATEMENTS[:]
        m = meta("/v4/specimens?limit=3&country=%20")
        assert m["total"] == 0 and m["complete"] is True and not any("count(" in s.lower() for s, _ in STATEMENTS), m
        # asked for, with a key: the count of every record (ants only, the default)
        del STATEMENTS[:]
        m = meta("/v4/specimens?limit=3&total=true", key="registered")
        assert m["total"] == N + 7 and "totalNotGiven" not in m, m
        assert sum("count(" in s.lower() for s, _ in STATEMENTS) == 1
        # asked for without a key: refused before it is counted or run (review of #348, second round)
        fresh_usage()
        del STATEMENTS[:]
        r = get("/v4/specimens?limit=3&total=true", None, expect=400)
        assert r.status_code == 400 and "needs an API key" in r.get_json()["error"], r.get_json()
        assert not any("count(" in s.lower() for s, _ in STATEMENTS), "the refused request counted"
        r = get("/v4/specimens?limit=3&total=true&country=Madagascar", None)
        assert r.status_code == 200, "a total with a filter needs no key"
        # total=false and later pages say nothing about it, as before
        for url in ("/v4/specimens?limit=3&total=false", "/v4/specimens?limit=3&offset=3", "/v4/specimens?limit=3&afterCode=casent0002"):
            m = meta(url)
            assert "total" not in m and "totalNotGiven" not in m, (url, m)
        # any filter keeps the total of the first page; a parameter that pages, formats or chooses families is none
        for url, total in ((MADAGASCAR + "&limit=3", N), ("/v4/specimens?genus=pheidole&limit=3", N + 7),
                           ("/v4/specimens?family=formicidae&limit=3", N + 7), ("/v4/specimens?localityCode=LOC01&limit=3", N + 7),
                           ("/v4/specimens?georeferenced=false&limit=3", 17), ("/v4/specimens?specimenCode=casent0001", 1)):
            m = meta(url)
            assert m["total"] == total and "totalNotGiven" not in m, (url, m)
        assert api_v4.NOT_FILTERS == {"limit", "offset", "afterCode", "total", "ndjson", "allFamilies"}
        # ndjson: no total header, and the header that says how to ask
        r = get("/v4/specimens?limit=3&ndjson=true")
        assert "X-AntWeb-Total" not in r.headers and r.headers["X-AntWeb-Total-Not-Given"] == "add total=true"
        assert r.headers["X-AntWeb-Returned"] == "3"
        # the other list routes keep their totals: /geoSpecimens always has its area, /taxa counts the taxon table
        assert "total" in meta("/v4/geoSpecimens?coords=37.77,-122.47&radius=10&limit=2")
        m = meta("/v4/taxa?limit=2")
        assert "total" in m and "totalNotGiven" not in m, m
        # the front door says it
        assert "total=true" in client.get("/v4/").get_json()["paging"]["total"]
    if REPO is None:
        print("  (repository files not mounted, skipped)")
        return
    for path in ("web/documentation/api/apiV3_1-body.jsp", "web/llms.txt", "docs/flows/api.md"):
        with open(os.path.join(REPO, path), encoding="utf-8") as f:
            page = f.read()
        assert "total=true" in page and "without a filter" in page, path


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


# --- ants only by default ---------------------------------------------------------------------------------------

ANTS = ["mxant01", "mxant02", "mxnofam01", "mxnofam02"]            # Formicidae twice, no family (NULL), no family ('')
OTHERS = ["mxbeetle01", "mxwasp01"]
MEXICO = "/v4/specimens?country=Mexico"
NEAR = "/v4/geoSpecimens?coords=10,10&radius=1"


ANT_NAME, BEETLE_NAME, WASP_NAME = "myrmicinaestenamma mx1", "curculionidaebeetlegenus mx1", "vespidaewaspgenus mx1"


@contextlib.contextmanager
def other_families():
    """Six specimens of Mexico at 10,10: two ants, two with no family given, a beetle and a wasp. Mexico is a place
    with the three names on its list (/geoSpeciesStats), and four specimens have a measurement, as has a code with no
    specimen record (/measurements)."""
    try:
        add("mxant01", country="Mexico", lat=10, lon=10, taxon=ANT_NAME, genus="stenamma", species="mx1")
        add("mxant02", country="Mexico", lat=10, lon=10, taxon=ANT_NAME, genus="stenamma", species="mx1")
        add("mxnofam01", country="Mexico", lat=10, lon=10, family=None, genus="pheidole", species="mx2")
        add("mxnofam02", country="Mexico", lat=10, lon=10, family="", genus="pheidole", species="mx2")
        add("mxbeetle01", country="Mexico", lat=10, lon=10, family="curculionidae", taxon=BEETLE_NAME, genus="beetlegenus", species="mx1")
        add("mxwasp01", country="Mexico", lat=10, lon=10, family="vespidae", taxon=WASP_NAME, genus="waspgenus", species="mx1")
        s = api.session()
        s.add(api.Geolocale(id="30", name="Mexico", georank="country", isValid="1"))
        for name, genus in ((ANT_NAME, "stenamma"), (BEETLE_NAME, "beetlegenus"), (WASP_NAME, "waspgenus")):
            s.add(api.Taxon(taxonName=name, genus=genus, species="mx1", taxarank="species"))
            s.add(api.GeolocaleTaxon(geolocaleId=30, taxonName=name))
        for i, code in enumerate(("mxant01", "mxnofam01", "mxbeetle01", "mxwasp01", "mxghost01"), start=900):
            s.add(api.Measurement(id=i, code=code, trait="WL"))
        s.commit()
        api.session.remove()
        yield
    finally:
        api.session.remove()
        for c in ANTS + OTHERS:
            delete(c)
        with data_engine.begin() as c:
            c.execute(text("delete from measurement where id >= 900"))
            c.execute(text("delete from geolocale_taxon where geolocale_id = 30"))
            c.execute(text("delete from taxon where taxon_name in (:a, :b, :w)"), {"a": ANT_NAME, "b": BEETLE_NAME, "w": WASP_NAME})
            c.execute(text("delete from geolocale where id = '30'"))
        api.session.remove()


def test_ants_only_is_the_default_and_the_answer_says_so():
    """Brian Fisher's definition of 2026-11-10: "Country search: every ant specimen record whose country in AntWeb is
    the country", the same definition as the data extract (his question about the first Nearctic file: "why did you
    include non ants for an ant study?"). Without a word about families a v4 specimen answer holds ants only: family
    Formicidae and the records with no family given; the total counts the same records."""
    fresh_usage()
    with other_families(), opened():
        r = get(MEXICO + "&limit=100")
        m = r.get_json()["metaData"]
        assert codes_of(r) == ANTS, codes_of(r)
        assert m["families"] == "antsOnly" and m["total"] == 4 and m["returned"] == 4 and m["complete"] is True, m
        assert r.headers["X-AntWeb-Families"] == "antsOnly"
        # allFamilies=false is the default said aloud
        r = get(MEXICO + "&limit=100&allFamilies=false")
        assert codes_of(r) == ANTS and r.get_json()["metaData"]["families"] == "antsOnly"
        # a record of another family asked for by its code is not there, and the total says 0
        r = get("/v4/specimens?specimenCode=mxbeetle01")
        assert codes_of(r) == [] and r.get_json()["metaData"]["total"] == 0, r.get_json()["metaData"]
        # and the answer says why, and how to see it (2026-11-13)
        assert r.get_json()["metaData"]["familiesNote"] == ("Specimen mxbeetle01 is in family Curculionidae, not an"
            " ant. Answers made from specimen records hold ants only unless you add allFamilies=true."), r.get_json()["metaData"]
        # no note for an ant, for a code that is in no record, or with allFamilies=true
        assert "familiesNote" not in get("/v4/specimens?specimenCode=mxant01").get_json()["metaData"]
        assert "familiesNote" not in get("/v4/specimens?specimenCode=nosuchcode").get_json()["metaData"]
        assert "familiesNote" not in get("/v4/specimens?specimenCode=mxbeetle01&allFamilies=true").get_json()["metaData"]
        # the same on every page: by afterCode and by offset the set is the ants, each once
        got, pages = walk_after(MEXICO, 1)
        assert got == ANTS and pages == 4, (got, pages)
        got, _pages = walk_offset(MEXICO, 3)
        assert got == ANTS, got
        # a later page with total=true counts the same records
        m = meta(MEXICO + "&limit=1&afterCode=mxant01&total=true")
        assert m["total"] == 4 and m["families"] == "antsOnly", m
        # the daily record count is of the records given, not of the ones left out
        fresh_usage()
        m = meta(MEXICO + "&limit=100", key="small")
        assert m["returned"] == 4 and m["recordsLeftToday"] == 12 - 4, m


def test_all_families_gives_the_others_and_family_gives_one():
    fresh_usage()
    with other_families(), opened():
        r = get(MEXICO + "&limit=100&allFamilies=true")
        m = r.get_json()["metaData"]
        assert codes_of(r) == sorted(ANTS + OTHERS), codes_of(r)
        assert m["families"] == "allFamilies" and m["total"] == 6 and m["complete"] is True, m
        assert r.headers["X-AntWeb-Families"] == "allFamilies"
        got, _pages = walk_after(MEXICO + "&allFamilies=true", 2)
        assert got == sorted(ANTS + OTHERS), got
        assert codes_of(get("/v4/specimens?specimenCode=mxbeetle01&allFamilies=true")) == ["mxbeetle01"]
        # one family by its name (v3.1's own filter): that family, whatever allFamilies says
        for extra in ("", "&allFamilies=true", "&allFamilies=false"):
            r = get(MEXICO + "&family=curculionidae&limit=100" + extra)
            m = r.get_json()["metaData"]
            assert codes_of(r) == ["mxbeetle01"] and m["families"] == "family" and m["total"] == 1, (extra, m)
        # family=formicidae is the records that say Formicidae: not those with no family given
        r = get(MEXICO + "&family=formicidae&limit=100")
        assert codes_of(r) == ["mxant01", "mxant02"] and r.get_json()["metaData"]["families"] == "family"
        # family=* is no filter in v3.1: the default stays
        r = get(MEXICO + "&family=*&limit=100")
        assert codes_of(r) == ANTS and r.get_json()["metaData"]["families"] == "antsOnly"
        # a blank family= names no family either (review of pull request 348): the default stays, with every ant
        # (not only the records whose family is empty, which v3.1's own filter would give), also with allFamilies
        for blank in ("&family=", "&family=%20", "&family=&family="):
            r = get(MEXICO + blank + "&limit=100")
            m = r.get_json()["metaData"]
            assert codes_of(r) == ANTS and m["families"] == "antsOnly" and m["total"] == 4, (blank, codes_of(r), m)
            assert r.headers["X-AntWeb-Families"] == "antsOnly"
        r = get(MEXICO + "&family=&allFamilies=true&limit=100")
        assert codes_of(r) == sorted(ANTS + OTHERS) and r.get_json()["metaData"]["families"] == "allFamilies"
        assert api_v4.family_asked({"family": " vespidae "}) == "vespidae" and api_v4.family_asked({}) is None


def test_geo_specimens_are_ants_only_too_with_their_distinct_lists():
    fresh_usage()
    with other_families(), opened():
        r = get(NEAR + "&limit=100")
        m = r.get_json()["metaData"]
        assert codes_of(r) == ANTS and m["families"] == "antsOnly" and m["total"] == 4, m
        r = get(NEAR + "&limit=100&allFamilies=true")
        m = r.get_json()["metaData"]
        assert codes_of(r) == sorted(ANTS + OTHERS) and m["families"] == "allFamilies" and m["total"] == 6, m
        # the names of the specimens in the area follow the same choice
        r = get(NEAR + "&distinct=genus")
        assert r.get_json()["genera"] == ["pheidole", "stenamma"], r.get_json()
        assert r.get_json()["metaData"]["families"] == "antsOnly" and r.get_json()["metaData"]["total"] == 2
        r = get(NEAR + "&distinct=genus&allFamilies=true")
        assert r.get_json()["genera"] == ["beetlegenus", "pheidole", "stenamma", "waspgenus"], r.get_json()
        # ndjson: the records of ants, and the choice in a header
        r = get(NEAR + "&ndjson=true&limit=100")
        lines = [json.loads(x) for x in r.get_data(as_text=True).splitlines() if x.strip()]
        assert [x["specimenCode"] for x in lines[1::2]] == ANTS, lines
        assert r.headers["X-AntWeb-Families"] == "antsOnly" and r.headers["X-AntWeb-Total"] == "4"


def test_distinct_taxa_geo_species_stats_and_measurements_follow_the_same_rule():
    """Every route that reads specimens and takes a place filter (the coordinator's decision of 2026-11-12, after the
    first two): the names of a country's specimens, the caste counts of a place's species and the measurements of a
    country's specimens are of ants only unless allFamilies=true, and each answer says which."""
    fresh_usage()
    with other_families(), opened():
        # /distinctTaxa: the names of the specimens of a country
        j = get("/v4/distinctTaxa?country=Mexico&taxarank=genus").get_json()
        assert j["genera"] == ["pheidole", "stenamma"] and j["metaData"]["families"] == "antsOnly", j
        r = get("/v4/distinctTaxa?country=Mexico&taxarank=genus&allFamilies=true")
        assert r.get_json()["genera"] == ["beetlegenus", "pheidole", "stenamma", "waspgenus"], r.get_json()
        assert r.get_json()["metaData"]["families"] == "allFamilies" and r.headers["X-AntWeb-Families"] == "allFamilies"
        j = get("/v4/distinctTaxa?country=Mexico&taxarank=species").get_json()
        assert j["taxarank"] == ["Pheidole mx2", "Stenamma mx1"], j
        j = get("/v4/distinctTaxa?country=Mexico&taxarank=species&allFamilies=true").get_json()
        assert j["taxarank"] == ["Beetlegenus mx1", "Pheidole mx2", "Stenamma mx1", "Waspgenus mx1"], j

        # /geoSpeciesStats: the species of a place with their specimens by caste (the SQL is text; the same helper)
        j = get("/v4/geoSpeciesStats?geolocaleName=Mexico").get_json()
        assert [(x["taxonName"], x["workerCount"]) for x in j["geoSpeciesStats"]] == [(ANT_NAME, 2)], j
        assert j["metaData"]["families"] == "antsOnly", j["metaData"]
        j = get("/v4/geoSpeciesStats?geolocaleName=Mexico&allFamilies=true").get_json()
        assert [(x["taxonName"], x["workerCount"]) for x in j["geoSpeciesStats"]] == [
            (BEETLE_NAME, 1), (ANT_NAME, 2), (WASP_NAME, 1)], j
        assert j["metaData"]["families"] == "allFamilies", j["metaData"]

        # /measurements: of ants, and of a code with no specimen record (it has no family; as before it has no place)
        j = get("/v4/measurements?trait=WL").get_json()
        assert [m["specimenCode"] for m in j["measurements"]] == ["mxant01", "mxnofam01", "mxghost01"], j
        assert j["metaData"]["families"] == "antsOnly", j["metaData"]
        j = get("/v4/measurements?trait=WL&country=Mexico").get_json()
        assert [m["specimenCode"] for m in j["measurements"]] == ["mxant01", "mxnofam01"], j
        j = get("/v4/measurements?trait=WL&country=Mexico&allFamilies=true").get_json()
        assert [m["specimenCode"] for m in j["measurements"]] == ["mxant01", "mxnofam01", "mxbeetle01", "mxwasp01"], j
        assert j["metaData"]["families"] == "allFamilies", j["metaData"]

        # these three have no family= of their own; a bad allFamilies is refused on each
        for url in ("/v4/distinctTaxa?taxarank=genus", "/v4/geoSpeciesStats?geolocaleName=Mexico", "/v4/measurements?trait=WL"):
            j = get(url + "&allFamilies=maybe", expect=400).get_json()
            assert j["error"] == "allFamilies must be true or false.", (url, j)
            assert get(url + "&family=vespidae", expect=400).get_json()["unknownParameters"] == ["family"], url


def test_one_definition_and_one_helper_for_every_route():
    """The rule is written once (api_v4.ANTS) and applied by one function (api_v4.ants_only), called by exactly the
    routes of api_v4.FAMILY_ROUTES; no route has a family test of its own."""
    import inspect
    import re
    apisrc = open(api.__file__, encoding="utf-8").read()
    v4src = open(api_v4.__file__, encoding="utf-8").read()
    callers = sorted(name for name, fn in inspect.getmembers(api, inspect.isfunction)
                     if fn.__module__ == api.__name__ and "api_v4.ants_only(" in inspect.getsource(fn))
    assert callers == sorted(api_v4.FAMILY_ROUTES), callers
    assert apisrc.count("api_v4.ants_only(") == len(api_v4.FAMILY_ROUTES) + 1       # the line and a comment in /geoSpeciesStats
    assert "formicidae" not in re.sub(r"#.*", "", apisrc).replace('"(formicidae)"', ""), "api.py has a family test of its own"
    assert v4src.count("= 'formicidae'") == 1 and "def ants_condition" not in v4src, "one definition: api_v4.ANTS"
    assert sorted(api_v4.FAMILIES["routes"]) == sorted("/v4/" + r for r, e in api_v4.routes().items() if e in api_v4.FAMILY_ROUTES)
    # every one of them takes allFamilies, and no other route does
    for route, endpoint in api_v4.routes().items():
        assert ("allFamilies" in api_v4.accepted(endpoint)) is (endpoint in api_v4.FAMILY_ROUTES), route
    # a route that reads specimens and has a country filter is on the list (a new one must be added to it)
    for name, fn in inspect.getmembers(api, inspect.isfunction):
        src = inspect.getsource(fn) if fn.__module__ == api.__name__ else ""
        if name.startswith("get") and "request.args.get('country'" in src and "Specimen.country" in src:
            assert name in api_v4.FAMILY_ROUTES, name + " filters specimens by country and has no family rule"


def test_other_routes_have_no_family_rule():
    """The routes that give images by specimen code or name, and names and lists from other tables, answer as before
    and do not take the parameter (an unknown parameter is refused)."""
    fresh_usage()
    with other_families(), opened():
        for url in ("/v4/taxa?genus=camponotus&limit=2", "/v4/images?limit=2", "/v4/taxaImages?limit=2",
                    "/v4/geolocales?limit=2", "/v4/geolocaleTaxa?limit=2", "/v4/bioregions?limit=2", "/v4/bioregionTaxa?limit=2"):
            r = get(url)
            j = r.get_json()
            meta_ = j.get("metaData") or j.get("_metaData") or {}
            assert "families" not in meta_ and "X-AntWeb-Families" not in r.headers, url
            j = get(url + "&allFamilies=true", expect=400).get_json()
            assert j["unknownParameters"] == ["allFamilies"], (url, j)


def test_bad_family_values_are_400_before_anything_is_counted():
    fresh_usage()
    with opened():
        for url in ("/v4/specimens?allFamilies=yes", "/v4/specimens?allFamilies=", "/v4/geoSpecimens?coords=10,10&allFamilies=1"):
            j = get(url, expect=400).get_json()
            assert j["error"] == "allFamilies must be true or false.", (url, j)
        for value in ("all", "ALL", "%20all"):
            j = get("/v4/specimens?family=" + value, expect=400).get_json()
            assert "allFamilies=true" in j["error"] and "ants only" in j["error"], j
        # the names somebody might try point to the parameter
        for name in ("antsOnly", "all_families", "allfamilies", "families"):
            j = get("/v4/specimens?%s=true" % name, expect=400).get_json()
            assert j["didYouMean"] == {name: ["allFamilies"]}, (name, j)
        # none of these was counted as a request or a record
        m = meta("/v4/specimens?country=Madagascar&limit=1", key="small")
        assert m["recordsLeftToday"] == 12 - 1, m


def test_v31_answers_for_every_family_as_before():
    """v3.1 is not changed: no family condition in its SQL, every family in its answers, no new parameter."""
    with other_families():
        del STATEMENTS[:]
        r = client.get("/specimens?country=Mexico&limit=100&up=1")
        assert r.status_code == 200, r.status_code
        j = r.get_json()
        assert [s["specimenCode"] for s in j["specimens"]] == sorted(ANTS + OTHERS), j["metaData"]
        assert "families" not in j["metaData"] and "X-AntWeb-Families" not in r.headers
        sql = " ".join(st for st, _p in STATEMENTS).lower()
        assert "family = " not in sql and "family is null" not in sql, sql[-300:]
        r = client.get("/geoSpecimens?coords=10,10&radius=1&limit=100&up=1")
        assert [s["specimenCode"] for s in r.get_json()["specimens"]] == sorted(ANTS + OTHERS)
        j = client.get("/distinctTaxa?country=Mexico&taxarank=genus&up=1").get_json()
        assert j["genera"] == ["beetlegenus", "pheidole", "stenamma", "waspgenus"] and "families" not in j["metaData"], j
        del STATEMENTS[:]
        j = client.get("/geoSpeciesStats?geolocaleName=Mexico&up=1").get_json()
        assert [x["taxonName"] for x in j["geoSpeciesStats"]] == [BEETLE_NAME, ANT_NAME, WASP_NAME], j
        assert "family" not in " ".join(st for st, _p in STATEMENTS if "caste" in st).lower(), "v3.1 /geoSpeciesStats SQL changed"
        j = client.get("/measurements?trait=WL&country=Mexico&up=1").get_json()
        assert [m["specimenCode"] for m in j["measurements"]] == ["mxant01", "mxnofam01", "mxbeetle01", "mxwasp01"], j
        for route in ("/specimens?", "/geoSpecimens?coords=10,10&", "/distinctTaxa?", "/geoSpeciesStats?geolocaleName=Mexico&",
                      "/measurements?"):
            j = client.get(route + "allFamilies=true&up=1")
            assert j.status_code == 400 and j.get_json()["unknownParameters"] == ["allFamilies"], (route, j.get_data(as_text=True)[:200])
        for endpoint in api_v4.FAMILY_ROUTES:
            assert "allFamilies" not in api.acceptedParams(endpoint), endpoint
        # and v4 through an admin key while v4 is closed is v4: ants only
        fresh_usage()
        assert codes_of(get(MEXICO + "&limit=100", key="admin")) == ANTS


def test_the_ants_only_condition_is_the_data_extracts():
    """The same test the data extract uses for "ants only", not one of the API's own: family Formicidae, or no family
    given (tools/export/specimen_extract.py ANTS; DataExtract.ANTS in the page's engine). Word for word."""
    ours = api_v4.ANTS.format(s="s")
    assert ours == "(s.family = 'formicidae' or s.family is null or s.family = '')", ours
    if REPO is None:
        print("  (repository files not mounted, skipped)")
        return
    with open(os.path.join(REPO, "tools", "export", "specimen_extract.py"), encoding="utf-8") as f:
        tool = f.read()
    assert ('ANTS = "' + ours + '"') in tool, "tools/export/specimen_extract.py ANTS is not " + ours
    engine = os.path.join(REPO, "src", "org", "calacademy", "antweb", "util", "DataExtract.java")
    if os.path.isfile(engine):      # the Data extract page, when it is in the repository
        with open(engine, encoding="utf-8") as f:
            assert ('ANTS = "' + ours + '"') in f.read(), "DataExtract.ANTS is not " + ours
    # the view the API reads gives the specimen's own family, and the SQL sent has the same words
    assert api.Specimen.__table__.c.family.name == "family" and api.Specimen.__tablename__ == "api3_1_specimen"
    fresh_usage()
    with opened():
        del STATEMENTS[:]
        get("/v4/specimens?country=Madagascar&limit=1")
        sent = [st for st, _p in STATEMENTS if "api3_1_specimen" in st and "count(" not in st.lower()]
        assert sent and api_v4.ANTS.format(s="api3_1_specimen") in sent[0], sent


def test_front_door_and_documentation_say_ants_only():
    j = client.get("/v4/").get_json()
    f = j["families"]
    assert f["routes"] == ["/v4/specimens", "/v4/geoSpecimens", "/v4/geoSpeciesStats", "/v4/distinctTaxa", "/v4/measurements"], f
    assert "/v4/images" in f["otherRoutes"] and "no family rule" in f["otherRoutes"], f
    assert "ants only" in f["antsOnly"] and "no family given" in f["antsOnly"] and "metaData.total" in f["antsOnly"], f
    assert "allFamilies=true" in f["allFamilies"] and "family=<name>" in f["family"], f
    assert "metaData.families" in f["metaData"] and "X-AntWeb-Families" in f["metaData"], f
    for value in ("antsOnly", "allFamilies", "family"):
        assert value in f["metaData"], value
    if REPO is None:
        print("  (repository files not mounted, skipped)")
        return
    for path in ("web/documentation/api/apiV3_1-body.jsp", "web/llms.txt", "docs/flows/api.md"):
        with open(os.path.join(REPO, path), encoding="utf-8") as fh:
            page = fh.read()
        for word in ("allFamilies=true", "metaData.families", "no family given", "geoSpeciesStats", "distinctTaxa", "measurements"):
            assert word in page, (path, word)
        assert "ants only" in page.lower(), path
        # no text says any more that v4 answers for every family by default
        for old in ("v4 returns the records of every family", "returns every family"):
            assert old not in page, (path, old)
        for line in page.splitlines():
            if "allFamilies" in line:
                assert chr(0x2014) not in line and "nightly" not in line.lower(), (path, line[:120])
    with open(os.path.join(REPO, "web/documentation/api/apiV3_1-body.jsp"), encoding="utf-8") as fh:
        jsp = fh.read()
    v4 = jsp[jsp.index('id="v4"'):]
    v4 = v4[:v4.index("</ul>")]
    assert 'id="v4-families"' in v4 and "<code>allFamilies=true</code>" in v4, "the Version 4 list says ants only"


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


# --- a count that takes too long (2026-11-13) ---------------------------------------------------------------------

class _TimeExceeded(Exception):
    def __init__(self):
        super().__init__("maximum statement execution time exceeded")
        self.orig = Exception(api_v4.MYSQL_TIME_EXCEEDED, "Query execution was interrupted")


class _FakeMysqlSession:
    """Enough of a session for count_within(): a MySQL dialect, a record of the statements, a rollback."""
    def __init__(self, raise_on_count, before=0):
        self.sql, self.rolled_back, self.raise_on_count, self.before = [], False, raise_on_count, before

    def get_bind(self):
        return type("B", (), {"dialect": type("D", (), {"name": "mysql"})()})()

    def execute(self, stmt):
        self.sql.append(str(stmt))
        return type("R", (), {"scalar": lambda _s: self.before})()

    def rollback(self):
        self.rolled_back = True


class _FakeCounted:
    def __init__(self, session):
        self.session = session

    def with_entities(self, *_a):
        return self

    def scalar(self):
        if self.session.raise_on_count:
            raise self.session.raise_on_count
        return 7

    count = scalar


def test_count_within_sets_the_limit_for_the_count_only():
    s = _FakeMysqlSession(None, before=5)
    assert api_v4.count_within(_FakeCounted(s), "k", 3000) == 7
    assert s.sql[1:] == ["SET SESSION max_execution_time = 3000", "SET SESSION max_execution_time = 5"], s.sql
    # stopped by the limit: no total, the session rolled back and the limit put back
    s = _FakeMysqlSession(_TimeExceeded())
    assert api_v4.count_within(_FakeCounted(s), None, 20000) is None
    assert s.rolled_back and s.sql[-1] == "SET SESSION max_execution_time = 0", s.sql
    # any other error is not hidden, and the limit is put back all the same
    s = _FakeMysqlSession(ValueError("other"))
    try:
        api_v4.count_within(_FakeCounted(s), None, 3000)
        raise AssertionError("the error was swallowed")
    except ValueError:
        pass
    assert not s.rolled_back and s.sql[-1] == "SET SESSION max_execution_time = 0", s.sql
    assert api_v4.COUNT_MS[False] < api_v4.COUNT_MS[True]


def test_a_count_that_takes_too_long_gives_the_records_without_a_total():
    """A filter that matches nearly every record passes the no-filter rule and was counted on every first page. When
    the count takes longer than COUNT_MS the page comes without a total and says how to go on; with a key the limit
    is longer and the text does not offer a key."""
    real = api_v4.count_within
    seen = []

    def too_long(counted, key, ms):
        seen.append(ms)
        return None
    api_v4.count_within = too_long
    try:
        fresh_usage()
        with opened():
            r = get(MADAGASCAR + "&limit=5", key=None)
            m = r.get_json()["metaData"]
            assert "total" not in m and m["returned"] == 5 and m["complete"] is False and m["nextAfterCode"], m
            assert m["totalNotGiven"].startswith("No total is given: counting the records that match these filters"
                                                 " took longer than 3 seconds."), m
            assert "apiKey.do" in m["totalNotGiven"]
            assert r.headers[api_v4.TOTAL_NOT_GIVEN_HEADER] == "the count took too long"
            m = meta(MADAGASCAR + "&limit=5")
            assert "took longer than 20 seconds" in m["totalNotGiven"] and "apiKey.do" not in m["totalNotGiven"], m
            assert seen == [api_v4.COUNT_MS[False], api_v4.COUNT_MS[True]], seen
            # a first page that holds every record: their number is the total, no count needed
            m = meta(MADAGASCAR + "&limit=500")
            assert m["total"] == N and "totalNotGiven" not in m, m
            # walking on by afterCode still gives every record once
            got, _pages = walk_after(MADAGASCAR, 7)
            assert got == ALL, got
    finally:
        api_v4.count_within = real


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
