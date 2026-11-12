"""The API: every case answers, and data endpoints return JSON. Requests are capped at 500
records (2026-10-21; with a registered key in v4 as well); asking for more gives 500 and a notice.

The requests are written as /v3.1/...&up=1. While v4 is closed they go to v3.1 with the admin switch; once v4 is
open (v3.1 has ended) the sessions of antweb.new_session() send them to /v4/ with the test key (antweb.py)."""
import json

import pytest

from antweb import API_KEY_ENV, API_KEY_HEADER, BASE_URL, IS_PRODUCTION, TIMEOUT, api_test_key, new_session, v4_open
from api_cases import API_CASES


@pytest.mark.parametrize("name", sorted(API_CASES))
def test_api(name):
    r = new_session().get(BASE_URL + API_CASES[name], timeout=max(TIMEOUT, 120))
    assert r.status_code == 200, f"{name}: HTTP {r.status_code}"
    if name == "index":
        assert "Antweb API" in r.text
        return
    assert "operational error" not in r.text, f"{name}: {r.text[:200]}"
    try:
        r.json()
    except ValueError:
        pytest.fail(f"{name}: not JSON: {r.text[:200]!r}")


def test_taxa_respects_limit():
    r = new_session().get(BASE_URL + "/v3.1/taxa?genus=camponotus&limit=3&up=1", timeout=120)
    assert len(r.json()["taxa"]) <= 3



def test_unknown_parameter_is_refused():
    # A misspelt filter used to be ignored and the reply was the first 10,000 specimens.
    r = new_session().get(BASE_URL + "/v3.1/specimens?up=1&code=casent0922626", timeout=120)
    assert r.status_code == 400, r.status_code
    body = r.json()
    assert body["unknownParameters"] == ["code"], body
    assert "specimenCode" in body["acceptedParameters"], body
    assert body["didYouMean"] == {"code": ["specimenCode"]}, body


def test_specimen_code_returns_one_specimen():
    r = new_session().get(BASE_URL + "/v3.1/specimens?up=1&specimenCode=casent0922626", timeout=120)
    assert r.status_code == 200, r.status_code
    specimens = r.json()["specimens"]
    assert [s["specimenCode"] for s in specimens] == ["casent0922626"], [s["specimenCode"] for s in specimens][:5]


LIMIT_NOTICE = ("Requests are limited to 500 records while AntWeb moves to registered API keys. "
                "For larger data needs contact antweb@antweb.org.")


V4_NOTICE = "Answers with your key hold at most 500 records"


def _is_cap_notice(text):
    """The notice of a reply cut at 500 records: v3.1's text, or v4's for a key once v4 is open."""
    if v4_open():
        # v4 names afterCode= for the specimen records of /specimens and /geoSpecimens (2026-11-10) and offset= for
        # the other lists; both texts hold the word offset=
        return bool(text) and text.startswith(V4_NOTICE) and "offset=" in text
    return text == LIMIT_NOTICE


def _get(path):
    r = new_session().get(BASE_URL + path, timeout=max(TIMEOUT, 120))
    assert r.status_code == 200, (path, r.status_code, r.text[:200])
    return r


def _skip_if_cap_not_deployed(meta):
    # The old code reported limit 10,000 (or, for /geolocales, no limit at all).
    if "limit" not in meta or meta["limit"] > 500:
        pytest.skip("500-record cap not deployed on this host yet")


def test_limit_1000_returns_500_and_the_notice():
    r = _get("/v3.1/specimens?genus=camponotus&limit=1000&up=1")
    body = r.json()
    _skip_if_cap_not_deployed(body["metaData"])
    assert body["metaData"]["limit"] == 500, body["metaData"]
    assert len(body["specimens"]) == 500, len(body["specimens"])
    assert _is_cap_notice(body["metaData"]["limitNotice"]), body["metaData"]
    assert _is_cap_notice(r.headers.get("X-AntWeb-Limit-Notice")), dict(r.headers)


def test_limit_within_the_cap_has_no_notice():
    r = _get("/v3.1/specimens?genus=camponotus&limit=5&up=1")
    body = r.json()
    assert len(body["specimens"]) == 5
    assert "limitNotice" not in body["metaData"], body["metaData"]
    assert "X-AntWeb-Limit-Notice" not in r.headers


def test_ndjson_is_capped_with_the_notice_header():
    r = _get("/v3.1/specimens?genus=camponotus&limit=1000&ndjson=true&up=1")
    lines = [x for x in r.text.split("\n") if x]
    if len(lines) > 1000:
        pytest.skip("500-record cap not deployed on this host yet")
    assert len(lines) == 1000, len(lines)   # an index line and a record line per specimen
    assert json.loads(lines[-1]).get("specimenCode"), lines[-1][:200]
    assert _is_cap_notice(r.headers.get("X-AntWeb-Limit-Notice")), dict(r.headers)


def test_offset_pages_past_the_cap():
    first = _get("/v3.1/specimens?genus=camponotus&limit=1000&up=1").json()
    _skip_if_cap_not_deployed(first["metaData"])
    second = _get("/v3.1/specimens?genus=camponotus&limit=500&offset=500&up=1").json()
    # /specimens is sorted by specimen code (since the cap), so pages follow on without overlap.
    assert second["metaData"]["offset"] == 500, second["metaData"]
    assert "limitNotice" not in second["metaData"], second["metaData"]
    codes1 = [s["specimenCode"] for s in first["specimens"]]
    codes2 = [s["specimenCode"] for s in second["specimens"]]
    assert codes2 and not set(codes1) & set(codes2), len(set(codes1) & set(codes2))


def test_geolocales_are_capped():
    # geolocales used to ignore limit and return every geolocale.
    body = _get("/v3.1/geolocales?georank=adm1&limit=1000&up=1").json()
    _skip_if_cap_not_deployed(body["metaData"])
    assert 0 < len(body["geolocales"]) <= 500, len(body["geolocales"])
    assert _is_cap_notice(body["metaData"]["limitNotice"]), body["metaData"]


# ---------- road map step 2 (docs/flows/api.md): every route's reply, limit and offset ----------

# route: (request, list key, metaData key that counts the list or None). Limits of 5 keep it quick.
ROUTES = {
    "specimens": ("/v3.1/specimens?genus=camponotus&limit=5&up=1", "specimens", "count"),
    "geoSpecimens": ("/v3.1/geoSpecimens?coords=37.77,-122.47&radius=10&limit=5&up=1", "specimens", "count"),
    "geoSpeciesStats": ("/v3.1/geoSpeciesStats?geolocaleName=California&limit=5&up=1", "geoSpeciesStats", "count"),
    "taxa": ("/v3.1/taxa?genus=camponotus&limit=5&up=1", "taxa", "taxonCount"),
    "distinctTaxa": ("/v3.1/distinctTaxa?country=Madagascar&taxarank=genus&limit=5&up=1", "genera", "count"),
    "images": ("/v3.1/images?limit=5&up=1", "images", None),
    "measurements": ("/v3.1/measurements?limit=5&up=1", "measurements", "count"),
    "taxaImages": ("/v3.1/taxaImages?genus=camponotus&limit=5&up=1", "taxaImages", None),
    "geolocales": ("/v3.1/geolocales?georank=country&limit=5&up=1", "geolocales", "geolocaleCount"),
    "geolocaleTaxa": ("/v3.1/geolocaleTaxa?geolocaleName=Madagascar&taxarank=genus&limit=5&up=1", "geolocaleTaxa", "geolocaleCount"),
    "bioregions": ("/v3.1/bioregions?limit=5&up=1", "bioregions", "bioregionCount"),
    "bioregionTaxa": ("/v3.1/bioregionTaxa?bioregionName=Malagasy&taxarank=genus&limit=5&up=1", "bioregionTaxa", "bioregionCount"),
    "unimagedGeolocaleTaxa": ("/v3.1/unimagedGeolocaleTaxa?geolocaleName=Madagascar&limit=5&up=1",
                              "unimagedGeolocaleTaxaDict", "unimagedGeolocaleTaxaCount"),
}

# Fields every record of a route carries (docs/flows/api.md lists them all).
RECORD_FIELDS = {
    "specimens": {"specimenCode", "scientificName", "subfamily", "genus", "species", "country", "adm1", "bioregion",
                  "decimalLatitude", "decimalLongitude", "collectionCode", "ownedBy", "status", "imageCount"},
    "taxa": {"taxonName", "taxarank", "subfamily", "genus", "status", "antcatId", "authorDate", "imageCount"},
    "geolocales": {"geolocaleId", "geolocaleName", "georank", "parent", "speciesCount", "specimenCount", "imageCount",
                   "imagedSpecimenCount"},  # isValid: not sent (/geolocales returns valid places only)
    "geolocaleTaxa": {"geolocaleId", "geolocaleName", "taxonName", "taxarank", "status", "specimenCount", "imageCount"},
    "bioregions": {"bioregionName", "speciesCount", "specimenCount", "imageCount"},
    "bioregionTaxa": {"bioregionName", "taxonName", "taxarank", "status", "specimenCount"},
    "unimagedGeolocaleTaxa": {"taxonName"},
}


@pytest.mark.parametrize("name", sorted(ROUTES))
def test_reply_shape_and_limit(name):
    """JSON with metaData and the route's list, at most `limit` records; the count in metaData is the
    number of records (docs/numbers.md #101, #105, #111, #113, #115); the licence (CC BY 4.0) is in the reply."""
    path, key, count_key = ROUTES[name]
    body = _get(path).json()
    meta = body.get("metaData")
    assert isinstance(meta, dict), f"{name}: no metaData: {list(body)}"
    assert isinstance(body.get(key), list), f"{name}: no {key!r} list: {list(body)}"
    # Licence CC BY 4.0 on every reply (2026-10-29): the top-level "license" and the Link header.
    assert (body.get("license") or {}).get("url") == "https://creativecommons.org/licenses/by/4.0/", f"{name}: license {body.get('license')}"
    records = body[key]
    assert len(records) <= 5, f"{name}: {len(records)} records for limit=5"
    if count_key:
        assert meta.get(count_key) == len(records), f"{name}: metaData {count_key}={meta.get(count_key)} for {len(records)} records"
    fields = set(RECORD_FIELDS.get(name, ()))
    if name == "specimens" and v4_open():
        # v4 field names (api_v4.py): the collecting event, and the locality code v3.1 did not give
        fields = fields - {"collectionCode"} | {"collectingEventCode", "localityCode"}
    for field in sorted(fields):
        missing = [r for r in records if field not in r]
        assert not missing, f"{name}: records without {field!r}: {missing[:1]}"


def test_images_records_name_their_specimen():
    """/images groups image rows by specimen: every record has specimenCode and its images (each with
    imageId, shotType, shotNumber and four urls). metaData count = images, specimenCount = records
    (numbers.md #106). Every record but the last was keyed 'spefimenCode' until 2026-10-22."""
    body = _get("/v3.1/images?limit=50&up=1").json()
    records = [r for r in body["images"] if r]
    if not records:
        pytest.skip("no images on this host")
    for rec in records:
        assert rec.get("specimenCode"), f"record without specimenCode: {sorted(rec)}"
        assert rec["images"] and all({"imageId", "shotType", "shotNumber", "urls"} <= set(i) for i in rec["images"]), rec
        assert all(len(i["urls"]) == 4 for i in rec["images"]), rec["images"][0]
    assert body["metaData"]["count"] == sum(len(r["images"]) for r in records), body["metaData"]
    assert body["metaData"]["specimenCount"] == len(records), body["metaData"]


@pytest.mark.parametrize("route, id_field", [("geolocales?georank=country", "geolocaleId"), ("bioregions?", "bioregionName"),
                                             ("taxa?genus=camponotus", "taxonName"),
                                             ("bioregionTaxa?bioregionName=Malagasy", "taxonName")])
def test_offset_pages_follow_on(route, id_field):
    """Routes with a fixed order (geolocales by id, bioregions by name, taxa and bioregionTaxa by key since
    2026-10-26, finding F8): offset gives the next page."""
    sep = "" if route.endswith("?") else "&"
    key = route.split("?")[0]
    first = _get(f"/v3.1/{route}{sep}limit=3&offset=0&up=1").json()[key]
    second = _get(f"/v3.1/{route}{sep}limit=3&offset=3&up=1").json()[key]
    both = _get(f"/v3.1/{route}{sep}limit=6&offset=0&up=1").json()[key]
    assert [r[id_field] for r in first + second] == [r[id_field] for r in both], (first, second, both)


def test_taxa_cap_sends_the_notice_header():
    """/taxa has no limit in its metaData: a request above the cap gets at most 500 records and the notice
    header."""
    r = _get("/v3.1/taxa?genus=camponotus&limit=1000&up=1")
    assert len(r.json()["taxa"]) <= 500, len(r.json()["taxa"])
    assert _is_cap_notice(r.headers.get("X-AntWeb-Limit-Notice")), dict(r.headers)


def test_without_up_the_reply_is_503_json():
    """Decided by Brian Fisher 2026-10-27 (api.py checkUp, apiDown): without up=1 every data route answers 503
    with a JSON message (was 200 with an empty body). The / route stays 200 for health checks. Once v4 is open,
    v3.1 has ended (2026-11-09): 410, see test_v31_has_ended_when_v4_is_open."""
    r = new_session().get(BASE_URL + "/v3.1/", timeout=max(TIMEOUT, 120))
    assert r.status_code == 200 and "Antweb API" in r.text, r.status_code
    if v4_open():
        pytest.skip("v4 is open here: v3.1 answers 410 (test_v31_has_ended_when_v4_is_open)")
    r = new_session().get(BASE_URL + "/v3.1/taxa?genus=camponotus&limit=1", timeout=max(TIMEOUT, 120))
    assert r.status_code == 503, r.status_code
    # wording of 2026-11-03 (Brian Fisher): closed while free registered API keys are added
    assert "closed while we add free registered API keys" in r.json()["error"], r.text[:200]


V31_ROUTES = sorted(ROUTES)


def test_v31_has_ended_when_v4_is_open():
    """Decided by Brian Fisher 2026-11-09: when v4 opens, v3.1 ends. Every v3.1 data route answers 410 Gone with a
    JSON signpost to v4, with or without up=1 or a key; /v3.1/ stays 200 and says "ended"; /v4/ says "open"."""
    if not v4_open():
        pytest.skip("v4 is closed here: v3.1 answers 503 without up=1 (test_without_up_the_reply_is_503_json)")
    import requests
    plain = requests.Session()   # not new_session(): these requests must reach v3.1 as they are
    plain.headers["User-Agent"] = new_session().headers["User-Agent"]
    for route in V31_ROUTES:
        for query in ("", "?up=1", "?limit=1&up=1"):
            r = plain.get(BASE_URL + "/v3.1/" + route + query, timeout=max(TIMEOUT, 120))
            if r.status_code == 403 and "json" not in r.headers.get("Content-Type", ""):
                # production: Cloudflare may still refuse up=1 before the API sees it (rule "API: admin switch closed")
                continue
            assert r.status_code == 410, (route + query, r.status_code, r.text[:200])
            j = r.json()
            assert j["status"] == "ended" and "has ended" in j["error"], j
            assert j["v4"] == "https://www.antweb.org/v4/" and j["getAKey"] == "https://www.antweb.org/apiKey.do", j
            for field in ("documentation", "terms", "citation", "license"):
                assert j.get(field), (route, field, j)
            assert "Retry-After" not in r.headers, dict(r.headers)
    j = plain.get(BASE_URL + "/v3.1/", timeout=max(TIMEOUT, 120)).json()
    assert j["status"] == "ended" and j["v4"]["status"] == "open", j
    r = plain.get(BASE_URL + "/v4/", timeout=max(TIMEOUT, 120))
    assert r.status_code == 200 and r.json()["status"] == "open", (r.status_code, r.text[:200])
    assert sorted(x.rsplit("/", 1)[1] for x in r.json()["routes"]) == V31_ROUTES, r.json()["routes"]


def test_the_tests_can_reach_the_api():
    """The suite's own way into the API (antweb.py): up=1 while v4 is closed; once v4 is open, the admin test key in
    the secret ANTWEB_TEST_API_KEY. Fails with what to do when the key is missing or refused, because the other API
    tests are then skipped."""
    if IS_PRODUCTION:
        pytest.skip("the test key is a staging key")
    if not v4_open():
        r = new_session().get(BASE_URL + "/v3.1/taxa?genus=camponotus&limit=1&up=1", timeout=max(TIMEOUT, 120))
        assert r.status_code == 200, (r.status_code, r.text[:200])
        return
    how = ("make a key on staging with tools/api/create-test-key.py and put it in the GitHub Actions secret "
           + API_KEY_ENV + " (docs/flows/api.md, \"The switch\")")
    assert api_test_key(), f"v4 is open on {BASE_URL} and {API_KEY_ENV} is not set: {how}"
    import requests
    r = requests.get(BASE_URL + "/v4/taxa?genus=camponotus&limit=1", timeout=max(TIMEOUT, 120),
                     headers={API_KEY_HEADER: api_test_key(), "User-Agent": new_session().headers["User-Agent"]},
                     allow_redirects=False)
    assert r.status_code != 401, f"the server refused the key in {API_KEY_ENV}: {how}"
    assert r.status_code == 200 and r.headers.get("X-AntWeb-Level") == "key", (r.status_code, r.text[:200])
    # the session of the tests gets the same answer from the v3.1 spelling
    r = new_session().get(BASE_URL + "/v3.1/taxa?genus=camponotus&limit=1&up=1", timeout=max(TIMEOUT, 120))
    assert r.status_code == 200 and len(r.json()["taxa"]) == 1, (r.status_code, r.text[:200])


def test_an_island_is_not_part_of_its_political_parent():
    """Brian Fisher, 2026-11-11: "An oceanic island or archipelago with its own ant fauna is its own country in AntWeb.
    Its specimens never count toward the political parent." country=Hawaii returns Hawaii's specimens, each with the
    island as its country; the same specimen asked for with country=United States is not there. Skips until the stored
    specimens are moved on the server (tools/place/island_countries.py --apply)."""
    s = new_session()
    for island, parent in (("Hawaii", "United States"), ("Galapagos Islands", "Ecuador")):
        r = s.get(BASE_URL + "/v3.1/specimens?up=1&limit=3&country=" + island.replace(" ", "%20"), timeout=max(TIMEOUT, 120))
        assert r.status_code == 200, (island, r.status_code, r.text[:200])
        specimens = r.json().get("specimens") or []
        if not specimens:
            pytest.skip(f"{island}: the stored specimens are not moved yet (tools/place/island_countries.py --apply)")
        for specimen in specimens:
            assert specimen["country"] == island, (specimen["specimenCode"], specimen["country"])
            code = specimen["specimenCode"]
            r = s.get(BASE_URL + f"/v3.1/specimens?up=1&specimenCode={code}&country=" + parent.replace(" ", "%20"),
                      timeout=max(TIMEOUT, 120))
            assert r.status_code == 200, (code, r.status_code, r.text[:200])
            assert (r.json().get("specimens") or []) == [], f"{code} of {island} is returned for country={parent}"


def test_unknown_route_is_json_404():
    r = new_session().get(BASE_URL + "/v3.1/noSuchRoute?up=1", timeout=max(TIMEOUT, 120))
    assert r.status_code == 404, r.status_code
    assert r.json() == {"error": "Not Found"}, r.text[:200]


def test_valid_genus_filter_keeps_valid_genera():
    """Finding F7 (fixed 2026-10-26): validGenus=true compares in lower case, in SQL before limit."""
    body = _get("/v3.1/specimens?genus=camponotus&validGenus=true&limit=5&up=1").json()
    assert len(body["specimens"]) == 5, body["metaData"]


# The documentation page does not describe /measurements yet (finding, docs/flows/public-pages-findings.md).
UNDOCUMENTED = {"geoSpeciesStats"}  # described to admins only; /measurements is described since 2026-10-26 (F16)


@pytest.mark.parametrize("path", ["/api.do", "/apiV3.1.do"])
def test_documentation_page_names_every_route(path):
    r = new_session().get(BASE_URL + path, timeout=max(TIMEOUT, 120))
    assert r.status_code == 200, r.status_code
    assert "<title>AntWeb API" in r.text or "AntWeb API" in r.text[:2000], r.text[:300]
    missing = [name for name in ROUTES if name not in UNDOCUMENTED and f"/{name}?" not in r.text]
    assert not missing, f"{path}: routes not described: {missing}"


def test_geo_species_stats_unknown_place_is_json_404():
    """Finding F23 (fixed 2026-10-26): was the plain text "Geolocale not found" with HTTP 200."""
    r = new_session().get(BASE_URL + "/v3.1/geoSpeciesStats?geolocaleName=zzAtlantis&up=1", timeout=max(TIMEOUT, 120))
    assert r.status_code == 404, (r.status_code, r.text[:200])
    assert r.json() == {"error": "Geolocale not found: zzAtlantis"}, r.text[:200]


def _km(lat1, lon1, lat2, lon2):
    import math
    p1, p2 = math.radians(lat1), math.radians(lat2)
    a = math.sin((p2 - p1) / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(math.radians(lon2 - lon1) / 2) ** 2
    return 6371 * 2 * math.asin(math.sqrt(a))


def test_geo_specimens_stay_within_the_radius():
    """Finding F22 (fixed 2026-10-26): radius is in kilometres."""
    body = _get("/v3.1/geoSpecimens?coords=37.77,-122.47&radius=10&limit=20&up=1").json()
    far = [(s["specimenCode"], s["decimalLatitude"], s["decimalLongitude"]) for s in body["specimens"]
           if s.get("decimalLatitude") not in (None, "") and _km(37.77, -122.47, float(s["decimalLatitude"]), float(s["decimalLongitude"])) > 15]
    assert not far, f"specimens more than 15 km from the point for radius=10: {far[:3]}"


def test_geo_specimens_distinct_uses_the_area():
    """Finding F22 (fixed 2026-10-26): the distinct lists are those of the area."""
    sf = _get("/v3.1/geoSpecimens?coords=37.77,-122.47&radius=25&distinct=genus&limit=500&up=1").json()["genera"]
    mg = _get("/v3.1/geoSpecimens?coords=-18.9,47.5&radius=25&distinct=genus&limit=500&up=1").json()["genera"]
    assert sf or mg, "no genus within 25 km of San Francisco or Antananarivo"
    assert sorted(sf) != sorted(mg), "the same genera near San Francisco and near Antananarivo"


def test_distinct_taxa_species_are_full_names():
    """Finding F24 (fixed 2026-10-27): taxarank=species gave bare epithets; now distinct "Genus species", leaving
    out names not identified to genus such as "(Formicidae) (indet)" (Brian Fisher's rule of 2026-10-22)."""
    names = _get("/v3.1/distinctTaxa?taxarank=species&country=Madagascar&limit=50&up=1").json()["taxarank"]
    assert names, "no species for Madagascar"
    bad = [n for n in names if not n or " " not in n or not n[:1].isupper()]
    assert not bad, f"species without a capitalised genus: {bad[:5]}"
    unidentified = [n for n in names if n.split(" ")[0].lower() in ("indet", "undet")]
    assert not unidentified, f"species not identified to genus: {unidentified[:5]}"
    assert len(names) == len(set(names)), "the species names repeat"


def test_country_matches_the_whole_name():
    """Finding F24 (fixed 2026-10-27): country matched by "contains", so country=Niger also returned Nigeria."""
    specimens = _get("/v3.1/specimens?country=Niger&limit=100&up=1").json()["specimens"]
    others = sorted({s["country"] for s in specimens if (s.get("country") or "").lower() != "niger"})
    assert not others, f"country=Niger returned other countries: {others}"
    lower = _get("/v3.1/specimens?country=madagascar&limit=5&up=1").json()["specimens"]
    assert lower and all(s["country"].lower() == "madagascar" for s in lower), "country=madagascar (lower case) found nothing"


def test_llms_txt_and_the_terms_on_the_api_page():
    """Step 6, API keys PR 1 (2026-11-08): /llms.txt (plain text for programs and AI agents) and the terms of use on
    the API page, both linked from the API's front door."""
    r = new_session().get(BASE_URL + "/llms.txt", timeout=TIMEOUT)
    assert r.status_code == 200 and r.headers.get("Content-Type", "").startswith("text/plain"), (r.status_code, r.headers.get("Content-Type"))
    for words in ("# AntWeb", "https://www.antweb.org/v4/", "https://www.antweb.org/apiKey.do", "X-API-Key",
                  "https://www.antweb.org/api.do", "CC BY 4.0", "antweb@antweb.org"):
        assert words in r.text, words
    r = new_session().get(BASE_URL + "/apiV3.1.do", timeout=max(TIMEOUT, 120))
    assert r.status_code == 200 and 'id="terms"' in r.text and "Access and terms of use" in r.text, r.status_code
    assert "since 2002" in r.text and "CC BY 4.0" in r.text, "the terms Brian approved (2026-11-08)"
    j = new_session().get(BASE_URL + "/v3.1/", timeout=max(TIMEOUT, 120)).json()
    assert j["terms"].endswith("/api.do#terms") and j["guideForPrograms"].endswith("/llms.txt"), j


def test_v4_front_door_and_refusals():
    """Step 6, API keys PR 3 (2026-11-08): /v4/ answers JSON with its status and limits; a data route without a key
    answers 503 while v4 is closed (or its keys are not configured) or 429, never data without limits; a key in the web
    address is refused. Skipped while Cloudflare challenges /v4/ (only /v3.1/ is excepted so far)."""
    r = new_session().get(BASE_URL + "/v4/", timeout=max(TIMEOUT, 120))
    if not r.headers.get("Content-Type", "").startswith("application/json"):
        pytest.skip(f"/v4/ is not answered by the API here (HTTP {r.status_code}; Cloudflare challenge?)")
    j = r.json()
    assert r.status_code == 200 and j["name"] == "Antweb API v4", (r.status_code, j)
    assert j["status"] in ("open", "closed", "not configured") and j["keyHeader"] == "X-API-Key", j
    assert j["limits"]["withoutKey"]["recordsPerAnswer"] == 25, j["limits"]
    r = new_session().get(BASE_URL + "/v4/specimens?limit=1", timeout=max(TIMEOUT, 120))
    if j["status"] != "open":
        assert r.status_code == 503 and "error" in r.json(), (r.status_code, r.text[:200])
    else:
        assert r.status_code in (200, 429), (r.status_code, r.text[:200])
    r = new_session().get(BASE_URL + "/v4/specimens?apiKey=aw_" + "x" * 40, timeout=max(TIMEOUT, 120))
    assert r.status_code == 400 and "X-API-Key header" in r.json()["error"] and "aw_x" not in r.text, r.text[:200]
