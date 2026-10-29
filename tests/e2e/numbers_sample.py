"""Shared helpers of the number tests that use a rotating sample (docs/numbers.md, 2026-10-26).

A number shown on every place, list row or species page cannot be checked on every one of them by an end-to-end
test in reasonable time. These tests check every kind of place on every run, and choose which place of each kind
(and which genus or species within it) from the day: a different one each day, so that over the days the sample
covers every place. The choice is deterministic for a day, so a failure can be repeated: set ANTWEB_SAMPLE_DAY to
the day number printed in the failure message (a proleptic Gregorian ordinal, Python's date.toordinal()).

Places are discovered from the site itself (the API's /geolocales and /bioregions, museums.do, projects.do), never
hard-coded, so the tests follow the data. Read-only and anonymous.
"""
import datetime
import os
import re
import zlib
from html import unescape
from urllib.parse import quote_plus

import pytest
import requests

from antweb import BASE_URL, TIMEOUT, classify, new_session

SLOW = max(TIMEOUT, 180)
DAY = int(os.environ.get("ANTWEB_SAMPLE_DAY") or datetime.date.today().toordinal())

# Lists too large to render in a test (every name in AntWeb) or not a place (Port of Entry: a species list only).
SKIP_PROJECTS = {"allantwebants", "worldants", "fossilants", "poeants"}

_SESSION = None


def session():
    global _SESSION
    if _SESSION is None:
        _SESSION = new_session()
    return _SESSION


def day_note():
    return f"(sample of day {DAY}; repeat with ANTWEB_SAMPLE_DAY={DAY})"


def pick(items, n, salt):
    """n items of the list, a different run of them each day (all of them over len(items) / n days)."""
    items = list(items)
    if not items or n <= 0:
        return []
    if len(items) <= n:
        return items
    start = (DAY * n + zlib.crc32(salt.encode())) % len(items)
    return [items[(start + i) % len(items)] for i in range(n)]


def fetch(path, s=None):
    """The page's HTML, or None when it is not a normal page (error, login, not found) or too slow."""
    url = path if path.startswith("http") else BASE_URL + path
    try:
        r = (s or session()).get(url, timeout=SLOW)
    except requests.RequestException:
        return None
    if classify(r).category != "ok":
        return None
    return r.text


def get(path, s=None):
    """As fetch, but skips the test when the page cannot be read."""
    html = fetch(path, s)
    if html is None:
        pytest.skip(f"{path}: not a normal page (or slower than {SLOW:.0f} s)")
    return html


def api(path):
    """JSON of /v3.1/<path> (with up=1), or None."""
    url = BASE_URL + "/v3.1/" + path + ("&" if "?" in path else "?") + "up=1"
    try:
        r = session().get(url, timeout=SLOW)
    except requests.RequestException:
        return None
    if r.status_code != 200 or "json" not in r.headers.get("Content-Type", ""):
        return None
    try:
        return r.json()
    except ValueError:
        return None


def specimen_code(row):
    """The specimen code of a /specimens row (the field is specimenCode; /taxaImages calls it code), lower case."""
    return (row.get("specimenCode") or row.get("code") or "").strip().lower()


def api_rows(body):
    if not body:
        return []
    for k, v in body.items():
        if k not in ("metaData", "_metaData") and isinstance(v, list):
            return v
    return []


def api_all(path, max_pages=8):
    """Every row of an API list that is ordered by its key (/geolocales, /specimens), page by page; None when it
    needs more than max_pages requests or a request fails."""
    rows, offset = [], 0
    for _ in range(max_pages):
        body = api(path + ("&" if "?" in path else "?") + f"offset={offset}")
        if body is None:
            return None
        page = api_rows(body)
        meta = body.get("metaData") or body.get("_metaData") or {}
        limit = int(meta.get("limit") or 0)
        rows.extend(page)
        if not limit or len(page) < limit:
            return rows
        offset += limit
    return None


def n(text):
    return int(text.replace(",", ""))


def text_of(html):
    return re.sub(r"\s+", " ", unescape(re.sub(r"<[^>]+>", " ", html))).strip()


# ----------------------------------------------------------------------------------------------- places

class Place:
    """A place page and the parameters of its lists (taxonomicPage.do, endemic.do)."""

    def __init__(self, kind, name, page, params, gid=None, parent=None, bioregion=None, api_row=None):
        self.kind, self.name, self.page, self.params = kind, name, page, params
        self.gid, self.parent, self.bioregion, self.api_row = gid, parent, bioregion, api_row

    def __repr__(self):
        return f"{self.kind} {self.name}" + (f" ({self.parent})" if self.parent else "")

    def endemic_url(self, page):   # page: endemic or introduced
        if self.kind == "bioregion":
            return f"/{page}.do?bioregionName={quote_plus(self.name)}"
        return f"/{page}.do?geolocaleId={self.gid}"

    def list_url(self, rank, status="all", extra=""):
        return f"/taxonomicPage.do?rank={rank}&{self.params}&statusSet={quote_plus(status)}{extra}"


def geolocale_place(row):
    name, rank = row.get("geolocaleName") or "", row.get("georank")
    q = quote_plus(name)
    if rank == "adm1":
        parent = row.get("parent") or ""
        return Place("adm1", name, f"/adm1.do?name={q}&country={quote_plus(parent)}",
                     f"adm1Name={q}&countryName={quote_plus(parent)}", row.get("geolocaleId"), parent, row.get("bioregion"), row)
    if rank in ("country", "region", "subregion"):
        return Place(rank, name, f"/{rank}.do?name={q}", f"{rank}Name={q}", row.get("geolocaleId"), row.get("parent"),
                     row.get("bioregion"), row)
    return None


_GEO = {}


def geolocales(georank):
    """Valid geolocales of a rank, from the API (ordered by id), or [] when the API cannot list them."""
    if georank not in _GEO:
        rows = api_all(f"geolocales?georank={georank}", max_pages=20) or []
        _GEO[georank] = [r for r in rows if r.get("geolocaleName") and r.get("geolocaleId") is not None]
    return _GEO[georank]


def madagascar():
    for r in geolocales("country"):
        if r.get("geolocaleName") == "Madagascar":
            return geolocale_place(r)
    return Place("country", "Madagascar", "/country.do?name=Madagascar", "countryName=Madagascar")


def bioregions():
    rows = api_rows(api("bioregions"))
    names = sorted({r.get("bioregionName") or r.get("name") for r in rows} - {None, ""})
    if not names:
        html = fetch("/bioregions.do") or ""
        names = sorted(set(re.findall(r"bioregion\.do\?name=([A-Za-z]+)", html)))
    return [Place("bioregion", b, f"/bioregion.do?name={quote_plus(b)}", f"bioregionName={quote_plus(b)}", bioregion=b)
            for b in names]


def museums():
    html = fetch("/museums.do") or ""
    codes = sorted(set(re.findall(r"museum\.do\?museumCode=([A-Za-z0-9_.-]+)", html)))
    return [Place("museum", c, f"/museum.do?museumCode={c}", f"museumCode={c}") for c in codes]


def projects():
    html = fetch("/projects.do") or ""
    names = sorted(set(re.findall(r"project\.do\?name=([A-Za-z0-9_]+)", html)) - SKIP_PROJECTS)
    return [Place("project", p, f"/project.do?name={p}", f"project={p}") for p in names]


def one_place_of_each_kind(salt):
    """The day's place of every kind: country, adm1, region, subregion, bioregion, museum, project."""
    out = []
    for rank in ("country", "adm1", "region", "subregion"):
        out += [geolocale_place(r) for r in pick(geolocales(rank), 1, salt + rank)]
    out += pick(bioregions(), 1, salt + "bioregion")
    out += pick(museums(), 1, salt + "museum")
    out += pick(projects(), 1, salt + "project")
    return [p for p in out if p is not None]


def place_stats(html):
    """The numbers of a place page: data-stat name -> data-n."""
    return {k: int(v) for k, v in re.findall(r'<tr data-stat="([a-z-]+)" data-n="(\d+)"', html)}


# ----------------------------------------------------------------------------------------------- lists

def header(html):
    """The number of a list's header (h2 class display_count) and its text; (None, '') without one."""
    m = re.search(r'<h2 class="display_count"[^>]*>(.*?)</h2>', html, re.S)
    if not m:
        return None, ""
    text = text_of(m.group(1))
    num = re.match(r"([\d,]+)", text)
    if num:
        return n(num.group(1)), text
    return (0, text) if text.startswith("No ") else (None, text)


ROW_START = re.compile(r'<div class="specimen_layout( no_photos)?">')


def list_rows(html):
    """The rows of a list (taxonReportBody.jsp, specimenReport.jsp): one dict per row with the HTML of the row,
    whether it has photos (specimen lists), and the taxon name of its description.do link (taxon lists)."""
    starts = [(m.start(), bool(m.group(1))) for m in ROW_START.finditer(html)]
    rows = []
    for i, (pos, no_photos) in enumerate(starts):
        end = starts[i + 1][0] if i + 1 < len(starts) else len(html)
        chunk = html[pos:end]
        rows.append({"html": chunk, "no_photos": no_photos, "name": taxon_name_of(chunk),
                     "endemic": 'title="Endemic"' in chunk.split("description.do?")[0],
                     "introduced": 'title="Introduced"' in chunk.split("description.do?")[0]})
    return rows


def taxon_name_of(chunk):
    """The taxon name (taxon.taxon_name: subfamily + genus, space, species[, space, subspecies]) of a list row's
    description.do link."""
    m = re.search(r'description\.do\?([^"\'>]+)', chunk)
    if not m:
        return None
    q = unescape(m.group(1))
    tn = re.search(r"(?:^|&)taxonName=([^&]+)", q)
    if tn:
        return requests.utils.unquote(tn.group(1)).replace("+", " ").strip().lower()
    p = dict(kv.split("=", 1) for kv in q.split("&") if "=" in kv)
    if not p.get("genus") or not p.get("species"):
        return None
    name = (p.get("subfamily") or "") + p["genus"] + " " + p["species"]
    if p.get("subspecies") and p.get("rank") == "subspecies":
        name += " " + p["subspecies"]
    return requests.utils.unquote(name).replace("+", " ").strip().lower()


def numbered_names(html):
    """The names of endemic.do and introduced.do: numbered rows 'N. <a ...description.do?taxonName=X'>'."""
    return [requests.utils.unquote(t).strip().lower()
            for t in re.findall(r"<br>\d+\. <a href='[^']*/description\.do\?taxonName=([^']+)'", html)]


_COMPUTE_LOG = {}


def compute_log():
    """The last 20000 lines of the server's compute.log (logMgr.do, admin test login; every nightly run writes its lines
    again, and one night is far fewer lines), or None when it can't be read (production: no admin test login; or no
    "Begin run" in it). Read once per run."""
    if "v" not in _COMPUTE_LOG:
        value = None
        try:
            from staging_accounts import IS_STAGING, login
            if IS_STAGING:
                s, _token, _id = login("admin")
                r = s.get(f"{BASE_URL}/logMgr.do?action=get&fileName=compute&ext=log&lines=20000", timeout=120)
                if r.status_code == 200 and "Begin run" in r.text:
                    value = r.text
        except Exception:  # noqa: BLE001
            value = None
        _COMPUTE_LOG["v"] = value
    return _COMPUTE_LOG["v"]


def compute_log_has(*parts):
    """True when one line of compute.log contains every part; False when the log is readable and no line does; None
    when the log can't be read (then a test compares strictly, as on production)."""
    text = compute_log()
    if text is None:
        return None
    return any(all(p in line for p in parts) for line in text.splitlines())


# The lines the Java nightly writes (CountInvariants.RULES_LOG, COUNTING_RULES_LOG on the "Set 4 done" line;
# CountInvariants.ENDEMIC_RULE_LOG on "Set 2 done" and "Set 3 done"). Keep in step with the Java constants.
RULES_LOG = "numbers counted by the rules of 2026-10-21"
COUNTING_RULES_LOG = "counting rules of 2026-10-28"
ENDEMIC_RULE_LOG = "endemic rule of 2026-10-28"


# SpeciesListUpload.BIOREGION_VALID_LOG, on the "AntCat import done" line of nightly set 1. Keep in step with Java.
BIOREGION_VALID_LOG = "bioregion lists from AntCat: valid names only (2026-10-29)"


def bioregion_valid_names_imported():
    """True when a nightly AntCat import has rebuilt the bioregion lists with valid names only (2026-10-29); False when
    compute.log is readable and has no such line (pending); None when unreadable."""
    return compute_log_has("AntCat import done", BIOREGION_VALID_LOG)


def counting_rules_counted():
    """True when a nightly run has counted the place numbers by the counting rules of 2026-10-28 (docs/numbers.md
    section 5; the same test as CountInvariants.rulesCounted), False when compute.log is readable and has no such line
    (stored numbers differ from the live lists until then: pending), None when unreadable."""
    return compute_log_has("Set 4 done", RULES_LOG, COUNTING_RULES_LOG)


def endemic_rule_counted():
    """True when a nightly run has set the endemic flags of geolocales (set 2) and bioregions (set 3) by the endemic
    rule of 2026-10-28 (valid names, native rows); False when compute.log is readable and lacks either line; None when
    unreadable."""
    a = compute_log_has("Set 2 done", ENDEMIC_RULE_LOG)
    b = compute_log_has("Set 3 done", ENDEMIC_RULE_LOG)
    if a is None or b is None:
        return None
    return a and b


def rules_pending_note(what):
    """The skip message of a test whose stored numbers differ only because no nightly run has counted them by a new
    rule yet (counting_rules_counted() or endemic_rule_counted() is False)."""
    return (f"pending: no nightly run has counted these numbers by the {what} yet (compute.log); they compare strictly"
            f" after the next nightly run")


def allantweb_images_counted():
    """True when the server's nightly set 4 has counted the AntWeb-wide list's Images (compute.log line "AntWeb-wide
    list Images counted", 2026-10-27); False when the log is readable and has no such line (every list's Images cell
    then shows the place's own number until that night: pending); None when the log can't be read (production, no
    admin test login). Cached for the run."""
    return compute_log_has("AntWeb-wide list Images counted")

