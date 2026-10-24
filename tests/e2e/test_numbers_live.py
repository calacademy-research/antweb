"""Numbers computed when a page is shown (live numbers), each compared with a number another page shows that counts
the same thing, 2026-10-22 (docs/numbers.md, Brian Fisher: "every number checked").

The place page numbers (data-stat on /country.do) are checked against the records every day by the count check
(countCheck.do, CountInvariants), so a live number that must equal one of them is checked too:

  taxonomicPage.do  "N Species / Genera / Subfamilies" with every counted status (statusSet=all) = Species /
                    Genera / Subfamilies of the place page; with valid extant = Valid species (row 38)
                    isImaged=false: "U Unimaged Species (out of N)": U <= N, N = the place's Species (row 39)
  browse.do         "N Species (M imaged)": the hidden totals say the same N, M <= N; "Only the first a of N"
                    names the same N (rows 46, 47, 49)
  images.do         "I Species Imaged (N total)": I <= N, N = browse.do's N (row 55)
  getComparison.do  one checkbox per child with images: I <= boxes <= N (row 60)
  oneView.do        one picture per child with that view: <= N (row 58)
  mapComparison.do  "(N Species Mapped)" = browse.do's N: it counts every child, mapped or not (B19; row 59)
  description.do    unImagedTaxa "Species with no image from this place (U)": the names listed (up to 50) (row 62)
  specimenImages.do "N Images" = the API's imageCount of the specimen (validated against the image table by
                    the count check, taxon.images) (row 64)
  collection.do     "N specimens of M species": N = the specimens listed, M <= N (row 65)
  locality.do       "N collections" = the collections listed (row 66)
  advancedSearch.do "Showing a-b of N ... (M imaged on this page)": M <= the rows shown; by taxon "S Species
                    (from N specimens)": S <= N, N = the specimen search's N (rows 67, 68)
  recentSearchResults.do  "Showing records a to b of N records": 1 <= a <= b <= N (row 71)
  endemic.do, introduced.do  numbered rows = Endemic and Introduced of the place page (rows 78, 79)

Read-only, anonymous (test_comparison_of_chosen_specimens_counts_them posts the compare form, which only shows the
chosen specimens). Madagascar and the genus Pheidole (on staging and production). A page without the markup (not
deployed yet) skips.
"""
import re
from html import unescape

import pytest

from antweb import BASE_URL, TIMEOUT, classify, new_session

PLACE = "/country.do?name=Madagascar"
WHERE = "countryName=Madagascar"
GENUS = "pheidole"
SLOW = max(TIMEOUT, 180)


def _get(path, session=None):
    r = (session or new_session()).get(BASE_URL + path, timeout=SLOW)
    outcome = classify(r)
    if outcome.category != "ok":
        pytest.skip(f"{path}: {outcome.category}")
    return r.text


def _n(text):
    return int(text.replace(",", ""))


def _text(html):
    return re.sub(r"\s+", " ", unescape(re.sub(r"<[^>]+>", " ", html))).strip()


_STATS = {}


def place_stats():
    if not _STATS:
        html = _get(PLACE)
        _STATS.update({k: int(v) for k, v in re.findall(r'<tr data-stat="([a-z-]+)" data-n="(\d+)"', html)})
    if "species" not in _STATS:
        pytest.skip("Madagascar page without data-stat numbers (not deployed yet)")
    return _STATS


def header(html):
    """The number of the list header (h2 class display_count) and its words."""
    m = re.search(r'<h2 class="display_count"[^>]*>(.*?)</h2>', html, re.S)
    assert m, "no display_count header"
    text = _text(m.group(1))
    n = re.match(r"([\d,]+)", text)
    return (_n(n.group(1)) if n else 0), text


def test_list_headers_equal_place_numbers():
    stats = place_stats()
    wrong = []
    for rank, stat, status in (("subfamily", "subfamilies", "all"), ("genus", "genera", "all"), ("species", "species", "all"),
                               ("species", "valid", "valid%20extant")):
        if stat not in stats:
            wrong.append(f"the Madagascar page has no data-stat {stat}")
            continue
        n, text = header(_get(f"/taxonomicPage.do?rank={rank}&{WHERE}&statusSet={status}"))
        if n != stats[stat]:
            wrong.append(f"rank={rank} {status}: list {n}, place page {stat} {stats[stat]}")
    # One line (the GitHub annotation shows the start only). The place numbers are those of the last nightly run: before
    # a nightly run with the rules of 2026-10-21 (ants only, counted from the list) they can differ from the live list.
    assert not wrong, "Madagascar list header vs place page: " + "; ".join(wrong) + " (expected to agree after a nightly run with the 2026-10-21 rules)"


def test_unimaged_header_is_within_the_list():
    stats = place_stats()
    html = _get(f"/taxonomicPage.do?rank=species&{WHERE}&statusSet=all&isImaged=false")
    m = re.search(r'<span class="numbers">([\d,]+)</span> Unimaged Species \(out of <span class="numbers">([\d,]+)</span>\)', html)
    if not m:
        # no unimaged species: the header falls back to the list size (B21)
        n, text = header(html)
        assert n == stats["species"], f"isImaged=false header {text!r}, place Species {stats['species']}"
        return
    unimaged, total = _n(m.group(1)), _n(m.group(2))
    assert unimaged <= total, m.group(0)
    assert total == stats["species"], f"'out of {total}', place Species {stats['species']}"


def browse_numbers():
    html = _get(f"/browse.do?genus={GENUS}&rank=genus&{WHERE}&statusSet=all")
    n, text = header(html)
    m = re.search(r"\((?:<span class=\"numbers\">)?([\d,]+)(?:</span>)? imaged\)", text if "imaged" in text else "")
    imaged = _n(m.group(1)) if m else 0
    return html, n, imaged, text


def test_browse_header_and_hidden_totals_agree():
    html, n, imaged, text = browse_numbers()
    assert n > 0, f"browse.do Pheidole of Madagascar: {text!r}"
    hidden = re.search(r'<div id="totalcount"[^>]*><span class="numbers">([\d,]+)</span>', html)
    if hidden:
        assert _n(hidden.group(1)) == n, f"hidden total {hidden.group(1)}, header {text!r}"
    im = re.search(r'<div id="imagecount"[^>]*><span class="numbers">([\d,]+)</span>.*?out of <span class="numbers">([\d,]+)</span>', html, re.S)
    if im:
        assert _n(im.group(2)) == n, f"hidden 'out of {im.group(2)}', header {text!r}"
        assert _n(im.group(1)) <= n, im.group(0)
    assert imaged <= n, text
    first = re.search(r"Only the first ([\d,]+) of ([\d,]+) displayed", html)
    if first:
        assert _n(first.group(2)) == n and _n(first.group(1)) < n, first.group(0)


def test_image_and_comparison_pages_count_the_same_children():
    _html, n, _imaged, text = browse_numbers()
    wrong = []
    images = _get(f"/images.do?genus={GENUS}&rank=genus&{WHERE}")
    m = re.search(r'<h2 class="display_count">([\d,]+) .*?Imaged \(([\d,]+) total\)</h2>', images, re.S)
    imaged_children = None
    if m:
        imaged_children, total = _n(m.group(1)), _n(m.group(2))
        if imaged_children > total:
            wrong.append(f"images.do: {imaged_children} imaged of {total}")
        if total != n:
            wrong.append(f"images.do total {total}, browse.do {text!r}")
    comparison = _get(f"/getComparison.do?rank=genus&genus={GENUS}&{WHERE}")
    boxes = len(re.findall(r'<input type="checkbox" name="chosen"', comparison))
    if boxes:
        if boxes > n:
            wrong.append(f"getComparison.do: {boxes} children with images of {n}")
        if imaged_children is not None and boxes < imaged_children:
            wrong.append(f"getComparison.do: {boxes} children with images, images.do {imaged_children} with a head, profile or dorsal view")
    one = _get(f"/oneView.do?rank=genus&genus={GENUS}&{WHERE}&shot=h")
    pictures = len(set(re.findall(r'<div class="slide half[^"]*"[^>]*onclick="window.location=\'bigPicture\.do\?name=([^&]+)&', one)))
    if pictures > n:
        wrong.append(f"oneView.do: {pictures} head views of {n} children")
    maps = _get(f"/mapComparison.do?rank=genus&genus={GENUS}&{WHERE}")
    mm = re.search(r"([\d,]+) [A-Za-z]+ Mapped\)", maps)
    if mm and _n(mm.group(1)) != n:
        wrong.append(f"mapComparison.do: {mm.group(1)} Mapped, browse.do {n} children (it counts every child, B19)")
    assert not wrong, f"Pheidole of Madagascar ({text!r}):\n" + "\n".join(wrong)


def test_unimaged_names_listed_match_their_number():
    html = _get(f"/images.do?genus={GENUS}&rank=genus&{WHERE}")
    # per place, so labelled "with no image from this place" since 2026-10-22 (Q12); "Non-imaged" before
    m = (re.search(r"<b>(\w+) with no image from this place \(([\d,]+)\):</b>(.*?)(?:<br|</div|$)", html, re.S)
         or re.search(r"<b>Non-imaged (\w+) \(([\d,]+)\):</b>(.*?)(?:<br|</div|$)", html, re.S))
    if not m:
        pytest.skip("no non-imaged children listed")
    shown = _n(m.group(2))
    links = len(re.findall(r"description\.do\?taxonName=", m.group(3)))
    assert links == min(shown, 50), f"Non-imaged {m.group(1)} ({shown}): {links} names listed"


def _specimen_code():
    html = _get(f"/images.do?genus={GENUS}&rank=genus&{WHERE}")
    codes = re.findall(r"bigPicture\.do\?name=([a-z0-9_.-]+)&", html, re.I) or re.findall(r"specimen\.do\?code=([a-z0-9_.-]+)", html, re.I)
    if not codes:
        pytest.skip("no imaged Pheidole specimen of Madagascar found")
    return codes[0].lower()


def test_specimen_images_count_equals_the_api():
    code = _specimen_code()
    html = _get("/specimenImages.do?code=" + code)
    m = re.search(r'<h2 class="display_count">(\d+) Images</h2>', html)
    assert m, "specimenImages.do: no 'N Images'"
    api = new_session().get(BASE_URL + "/v3.1/specimens?up=1&specimenCode=" + code, timeout=SLOW)
    if api.status_code != 200 or "json" not in api.headers.get("Content-Type", ""):
        pytest.skip(f"API: HTTP {api.status_code}")
    rows = api.json().get("specimens") or []
    if not rows:
        pytest.skip("API has no row for " + code)
    assert int(m.group(1)) == int(rows[0].get("imageCount") or 0), (
        f"{code}: specimenImages.do {m.group(1)} Images, API imageCount {rows[0].get('imageCount')}")


def test_collection_and_locality_counts_equal_their_lists():
    code = _specimen_code()
    specimen = _get("/specimen.do?code=" + code)
    wrong = []
    c = re.search(r"collection\.do\?name=([^\"'&<> ]+)", specimen)
    loc = None
    if c:
        html = _get("/collection.do?name=" + c.group(1))
        loc = re.search(r"locality\.do\?code=([^\"'&<> ]+)", html)
        m = re.search(r"([\d,]+) specimens(?: of ([\d,]+) species)?", html)
        if m:
            n = _n(m.group(1))
            listed = len(set(re.findall(r"specimen\.do\?code=([^\"'&<>]+)", html, re.I)))
            if listed and listed != n:
                wrong.append(f"collection.do?name={c.group(1)}: {n} specimens, {listed} listed")
            if m.group(2) and _n(m.group(2)) > n:
                wrong.append(f"collection.do?name={c.group(1)}: {m.group(2)} species of {n} specimens")
    if loc:
        html = _get("/locality.do?code=" + loc.group(1))
        m = re.search(r"<h3>([\d,]+)&nbsp;collections?\s*</h3>", html)
        if m:
            listed = len(set(re.findall(r"collection\.do\?name=([^\"'&<>]+)", html)))
            if listed and listed != _n(m.group(1)):
                wrong.append(f"locality.do?code={loc.group(1)}: {m.group(1)} collections, {listed} listed")
    if not c and not loc:
        pytest.skip("specimen page without collection or locality links")
    assert not wrong, "\n".join(wrong)


SEARCH = "/advancedSearch.do?searchMethod=advancedSearch&advanced=true&family=formicidae&country=Madagascar&genus=" + GENUS
SHOWING = re.compile(r"Showing\s+([\d,]+)(?:&ndash;|–)([\d,]+)\s+of\s+([\d,]+)")


def test_search_counts_agree():
    s = new_session()
    html = _get(SEARCH + "&resultRank=specimen", s)
    m = SHOWING.search(html)
    if not m:
        pytest.skip("advanced search without 'Showing a-b of N' (not deployed yet)")
    first, last, total = (_n(g) for g in m.groups())
    imaged = re.search(r'\(<span class="numbers">(\d+)</span> imaged on this page\)', html)
    if imaged:
        assert int(imaged.group(1)) <= last - first + 1, imaged.group(0)
    by_taxon = _get(SEARCH + "&resultRank=species", s)
    t = re.search(r"([\d,]+) Species \(from ([\d,]+) specimens\)", _text(by_taxon))
    if t:
        species, specimens = _n(t.group(1)), _n(t.group(2))
        assert species <= specimens, t.group(0)
        if "truncated" not in by_taxon:
            assert specimens == total, f"search by species: from {specimens} specimens; specimen search: {total}"
    # Compare preparation of the same search (row 69): "M imaged specimens (from N total specimens)": M <= N, N = the
    # search's N.
    _get(SEARCH + "&resultRank=specimen", s)
    prep = s.get(BASE_URL + "/prepareCompareResults.do?resultRank=specimen", timeout=SLOW)
    p = re.search(r"([\d,]+) imaged specimens \(from ([\d,]+) total specimens\)", _text(prep.text)) if prep.status_code == 200 else None
    if p:
        assert _n(p.group(1)) <= _n(p.group(2)), p.group(0)
        assert _n(p.group(2)) == total, f"compare preparation: {p.group(0)}; specimen search: {total}"


def test_comparison_of_chosen_specimens_counts_them():
    """multiTaxaComparison (row 70): "N specimens to compare." = the specimens chosen on the compare page."""
    s = new_session()
    _get(SEARCH + "&resultRank=specimen", s)
    prep = s.get(BASE_URL + "/prepareCompareResults.do?resultRank=specimen", timeout=SLOW)
    form = re.search(r'<form[^>]*action="([^"]*compareResults[^"]*)"(.*?)</form>', prep.text, re.S)
    chosen = re.findall(r'<input type="checkbox" name="chosen" value="([^"]+)"', prep.text)
    csrf = re.search(r'name="_csrf" value="([^"]+)"', prep.text)
    if not (form and csrf and len(chosen) >= 3):
        pytest.skip("compare page without a compareResults form, its form token or three specimens to choose")
    action = unescape(form.group(1))
    url = action if action.startswith("http") else BASE_URL + (action if action.startswith("/") else "/" + action)
    done = s.post(url, data={"_csrf": csrf.group(1), "chosen": chosen[:3]}, timeout=SLOW)
    c = re.search(r"<h2>(\d+) specimens to compare\.</h2>", done.text)
    assert c, f"{url}: no 'N specimens to compare.' (HTTP {done.status_code})"
    assert int(c.group(1)) == 3, c.group(0)


def test_recent_images_count_equals_the_rows_shown():
    html = _get("/recentSearchResults.do?searchMethod=recentImageSearch&daysAgo=30&numToShow=100")
    m = re.search(r"Showing records <span class=\"numbers\">(\d+)</span> to <span class=\"numbers\">(\d+)</span> of <span class=\"numbers\">(\d+)</span> records", html)
    if m:
        a, b, n = (int(g) for g in m.groups())
        assert 1 <= a <= b <= n, m.group(0)
        return
    pytest.skip("fewer recent images than one page: no 'Showing records a to b of N'")


def test_endemic_and_introduced_lists_equal_place_numbers():
    stats = place_stats()
    place = _get(PLACE)
    wrong = []
    for page, stat in (("endemic", "endemic"), ("introduced", "introduced")):
        link = re.search(page + r"\.do\?geolocaleId=(\d+)", place)
        if stat not in stats or not link:
            continue   # no link when the number is 0
        html = _get(f"/{page}.do?geolocaleId={link.group(1)}")
        rows = len(re.findall(r"<br>\d+\. <a href='[^']*/description\.do\?taxonName=", html))
        if rows != stats[stat]:
            wrong.append(f"{page}.do Madagascar lists {rows} names; place page {stat} {stats[stat]}")
    assert not wrong, "\n".join(wrong)
