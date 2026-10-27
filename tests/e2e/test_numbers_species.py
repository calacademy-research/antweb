"""The specimen habitat summaries of species pages and the field guide (docs/numbers.md rows 50 to 53), 2026-10-26.

description.do of a species shows, under "Specimen Habitat Summary", up to 10 entries each of:

  row 50  "N times found in <habitat>"
  row 51  "N times <microhabitat>"
  row 52  "N times <method>"

where N is the number of collection events (distinct collection codes; since 2026-10-27, B72, each specimen without a
collection code is an event of its own) of the specimens filed under the name, with
that value (Species.setHabitats, setMicrohabitats, setMethods), largest first. Row 53: the field guide shows the
same summaries for each species.

Here they are recounted from the specimen records the API returns (/v3.1/specimens: every specimen of the species,
with its habitat, microhabitat, samplingMethod and collectionCode), independent of the page's SQL: the numbers shown
must be the largest N of the recount, in order, with "..." when there are more than 10 values; a value shown is
counted as the recount counts it. Values are compared as MySQL groups them (case, accents and trailing spaces
ignored).

Sample: the day's genus of Madagascar with 1 to 5 species (numbers_sample.py), and its species with at most
ANTWEB_SAMPLE_MAX_RECORDS specimens in AntWeb. Read-only, anonymous.

Found while writing these tests (xfail until fixed, docs/numbers.md section 3):
  B43  the field guide shows no microhabitat summary: FieldGuide.setSpecimenData reads habitats and methods only.
"""
import os
import re
import unicodedata
from html import unescape

import pytest
import requests

import numbers_sample as ns

MAX_SPECIMENS = int(os.environ.get("ANTWEB_SAMPLE_MAX_RECORDS", "1500"))
SHOWN = 10

KINDS = (
    ("habitat", "Found most commonly in these habitats: ", r"(\d+) times found in ", "habitat"),
    ("microhabitat", "Found most commonly in these microhabitats: ", r"(\d+) times ", "microhabitat"),
    ("method", "Collected most commonly using these methods: ", r"(\d+) times ", "samplingMethod"),
)


def _key(value):
    """A value as MySQL groups it (case- and accent-insensitive collation; trailing spaces ignored)."""
    v = unicodedata.normalize("NFKD", unescape(value or "")).encode("ascii", "ignore").decode()
    return re.sub(r"\s+", " ", v).strip().casefold()


def summaries(html):
    """kind -> (counts shown, descriptions shown, more than shown) of a Specimen Habitat Summary block."""
    out = {}
    for kind, title, count_re, _field in KINDS:
        m = re.search(r"<b>" + re.escape(title) + r"</b>(.*?)(?=<p>|</div>|<div|$)", html, re.S)
        if not m:
            continue
        text = ns.text_of(m.group(1)).strip()
        more = text.endswith(", ...")
        body = text[:-5] if more else text.rstrip(". ").rstrip(".")
        parts = re.split(r"(?:^|, )" + count_re, body)
        # parts: ['', count1, desc1, count2, desc2, ...]
        counts = [ns.n(c) for c in parts[1::2]]
        descs = [d.strip() for d in parts[2::2]]
        out[kind] = (counts, descs, more)
    return out


def recount(specimens, field):
    """value key -> collection events of the specimens with that value (none or empty values left out): distinct
    collection codes, and each specimen without a collection code as an event of its own (B72, Brian Fisher
    2026-10-27; Species.COLLECTION_EVENTS)."""
    groups = {}
    for i, s in enumerate(specimens):
        value = s.get(field)
        if value is None or not str(value).strip():
            continue
        code = s.get("collectionCode")
        bucket = groups.setdefault(_key(str(value)), set())
        if code is not None and str(code).strip():
            bucket.add(("code", _key(str(code))))
        else:
            bucket.add(("specimen", (s.get("specimenCode") or "").lower() or i))
    return {k: len(v) for k, v in groups.items()}


def _compare(where, shown, counted, wrong):
    for kind, _title, _re, field in KINDS:
        expected = counted[field]
        top = sorted(expected.values(), reverse=True)
        if kind not in shown:
            if top:
                wrong.append(f"{where}: no {kind} summary; the records have {len(top)} {kind} values (largest {top[:3]})")
            continue
        counts, descs, more = shown[kind]
        if counts != top[:len(counts)] or len(counts) != min(SHOWN, len(top)):
            wrong.append(f"{where} {kind}: shown {counts}, recount {top[:SHOWN]} ({len(top)} values)")
        if more != (len(top) > SHOWN):
            wrong.append(f"{where} {kind}: '...' {'shown' if more else 'not shown'} with {len(top)} values")
        for c, d in zip(counts, descs):
            if _key(d) in expected and expected[_key(d)] != c:
                wrong.append(f"{where} {kind}: {c} times {d!r}, recount {expected[_key(d)]}")


_SAMPLE = {}


def sample():
    """(genus browse link, place, [(taxon name, its specimens from the API)]) of the day's genus of Madagascar."""
    if "s" in _SAMPLE:
        return _SAMPLE["s"]
    p = ns.madagascar()
    html = ns.fetch(p.list_url("genus"))
    if html is None:
        pytest.skip("Madagascar's genus list not readable")
    genera = []
    for r in ns.list_rows(html):
        m = re.search(r"<a href='([^']*browse\.do\?[^']*)'><span class='numbers'>(\d+)</span> Species", r["html"])
        if m and 1 <= int(m.group(2)) <= 5:
            genera.append(m.group(1))
    if not genera:
        pytest.skip("no genus of Madagascar with 1 to 5 species")
    link = ns.pick(genera, 1, "habitats")[0]
    browse = ns.fetch(re.sub(r"^https?://[^/]+", "", link) + "&statusSet=all")
    species = []
    for r in ns.list_rows(browse or ""):
        name = r["name"]
        if not name:
            continue
        q = dict(kv.split("=", 1) for kv in re.findall(r"(?:^|[?&])((?:genus|species|subspecies|rank)=[^&\"'>]+)",
                                                     re.search(r'description\.do\?([^"\'>]+)', r["html"]).group(1)))
        path = f"specimens?genus={q.get('genus')}&species={q.get('species')}"
        rows = ns.api_all(path, max_pages=max(1, MAX_SPECIMENS // 500))
        if rows is None:
            continue
        mine = [s for s in rows if (s.get("antwebTaxonName") or "").lower() == name]
        species.append((name, mine))
    _SAMPLE["s"] = (link, p, species)
    return _SAMPLE["s"]


def test_species_page_habitat_summaries_equal_a_recount_of_the_specimens():
    """Rows 50, 51, 52: description.do of each species of the day's genus of Madagascar (at most
    ANTWEB_SAMPLE_MAX_RECORDS specimens): the habitat, microhabitat and method entries = the largest numbers of
    collection events per value, recounted from the API's specimen records."""
    link, _p, species = sample()
    wrong, checked = [], []
    for name, specimens in species:
        if not specimens:
            continue
        html = ns.fetch("/description.do?taxonName=" + ns.quote_plus(name))
        if html is None:
            continue
        counted = {field: recount(specimens, field) for _k, _t, _r, field in KINDS}
        _compare(name, summaries(html), counted, wrong)
        checked.append(name)
    if not checked:
        pytest.skip(f"no species of {link} with specimens in the API " + ns.day_note())
    assert not wrong, f"{len(checked)} species {ns.day_note()}:\n" + "\n".join(wrong)


def _field_guide(link):
    q = dict(kv.split("=", 1) for kv in re.findall(r"(?:^|[?&])((?:subfamily|genus|countryName)=[^&\"']+)", link))
    if not q.get("genus"):
        return None
    return ns.fetch(f"/fieldGuide.do?subfamily={q.get('subfamily', '')}&genus={q['genus']}&rank=genus&countryName=Madagascar")


def _field_guide_blocks(html):
    """taxon name -> the HTML of its block in a field guide."""
    out = {}
    heads = list(re.finditer(r"<h3>\w+: <a href=\"[^\"]*/description\.do\?taxonName=([^\"]+)\">", html))
    for i, h in enumerate(heads):
        end = heads[i + 1].start() if i + 1 < len(heads) else len(html)
        out[requests.utils.unquote(h.group(1)).strip().lower()] = html[h.start():end]
    return out


def test_field_guide_habitat_summaries_equal_a_recount_of_the_specimens():
    """Row 53: the field guide of the day's genus of Madagascar shows, for each species, the habitat and method
    entries recounted from the API's specimen records, as description.do does (microhabitats: B43, its own test)."""
    link, _p, species = sample()
    html = _field_guide(link)
    if html is None:
        pytest.skip("field guide not readable " + ns.day_note())
    blocks = _field_guide_blocks(html)
    wrong, checked = [], []
    for name, specimens in species:
        if not specimens or name not in blocks:
            continue
        shown = summaries(blocks[name])
        shown.pop("microhabitat", None)
        counted = {field: recount(specimens, field) for _k, _t, _r, field in KINDS}
        counted["microhabitat"] = {}
        _compare("field guide " + name, shown, counted, wrong)
        checked.append(name)
    if not checked:
        pytest.skip(f"no species of {link} in its field guide with specimens in the API " + ns.day_note())
    assert not wrong, f"{len(checked)} species {ns.day_note()}:\n" + "\n".join(wrong)


@pytest.mark.xfail(reason="B43: FieldGuide.setSpecimenData does not read microhabitats", strict=False)
def test_field_guide_shows_the_microhabitat_summary():
    """Row 53 (B43): the field guide shows the same three summaries as description.do, microhabitats included."""
    link, _p, species = sample()
    html = _field_guide(link)
    if html is None:
        pytest.skip("field guide not readable " + ns.day_note())
    blocks = _field_guide_blocks(html)
    missing, checked = [], 0
    for name, specimens in species:
        if name not in blocks or not recount(specimens, "microhabitat"):
            continue
        checked += 1
        if "microhabitat" not in summaries(blocks[name]):
            missing.append(name)
    if not checked:
        pytest.skip("no species of the day's genus with microhabitat records " + ns.day_note())
    assert not missing, f"field guide without the microhabitat summary for {missing} {ns.day_note()}"
