"""Images from anywhere (Brian Fisher, 2026-10-27; docs/numbers.md rows 44 and 120, docs/flows/taxon-pages.md).

AntWeb exists to help people identify what they found: a user picks a place and wants to see and identify its
species. Specimen counts stay per place; images help identification wherever the specimen came from.

  1. images.do of a species reached with a place (countryName, adm1Name, bioregionName, museumCode) shows every imaged
     specimen of the name in AntWeb; the place's specimens come first, marked "from <place>"; placeOnly=true shows the
     place's specimens only (the view before 2026-10-27). Checked on the case curator Michele Esposito reported
     (Leptogenys purpurea, Indonesia: the genus page showed CASENT0270569 from Papua New Guinea, the species page did
     not) against the API's specimen records, and on the day's species of the day's places of each of those kinds:
     the marked specimens are first, and they are exactly the specimens placeOnly=true shows.
  3. The Images column of a place's list: "T Images (P from Comoros)", images of the ant only (labels never count,
     Brian Fisher 2026-10-27): T = the images of the name in AntWeb (the allantwebants list row, nightly set 4) = the
     API recount of every specimen of the name (/taxaImages, shot types other than l), P = the place's own row (row
     44, test_numbers_images.py); "N Images" when P = T, "No Images" only when T = 0; the definition under the list.
  5. The species page's line "Imaged: worker (12 specimens), queen (2), male (1)" (row 120) = the API recount: the
     name's specimens with an image of the ant (/taxaImages, label images left out), by caste (/specimens).

Read-only, anonymous. The data on staging is a copy of production; a test skips with a reason when its sample has no
data. List rows are those of the last nightly run (set 4); after an upload they can be pending.
"""
import re
from html import unescape
from urllib.parse import quote_plus, parse_qs

import pytest

import numbers_sample as ns
from test_numbers_images import ant_images_by_code, fetch_all_view, images_cell, list_row_cells

PENDING = "list rows are those of the last nightly run (set 4); after an upload they are pending"
MICHELE = "/images.do?subfamily=ponerinae&genus=leptogenys&species=purpurea&rank=species&countryName=Indonesia"
MICHELE_SPECIES = "/description.do?subfamily=ponerinae&genus=leptogenys&species=purpurea&rank=species"
MICHELE_REP = "casent0270569"
MAX_ROWS = 4
PAGE = 500
CASTES = ("worker", "queen", "male", "other")


# ------------------------------------------------------------------------------------------- parsing

def shown_specimens(html):
    """The specimens of a species images page in the All view, in order: (code, marked "from <place>")."""
    out = []
    for chunk in html.split('<div class="data_overlay"><a href="')[1:]:
        m = re.match(r'[^"]*specimenImages\.do\?name=([^"&]+)[^"]*">.*?</a>(\s*<span class="from_place")?', chunk, re.S)
        if m:
            out.append((unescape(m.group(1)).strip().lower(), bool(m.group(2))))
    return out


def api_specimens(genus, species, max_pages=6):
    return ns.api_all(f"specimens?genus={quote_plus(genus)}&species={quote_plus(species)}", max_pages=max_pages)


def ant_imaged_codes(taxon_name):
    """Codes of the name's specimens with an image of the ant (/taxaImages, labels left out); None when over a page."""
    body = ns.api(f"taxaImages?taxonName={quote_plus(taxon_name)}&limit={PAGE}")
    if body is None:
        return None
    images = [(sp.get("code") or "", (i.get("shotType") or "").strip().lower())
              for t in ns.api_rows(body) for sp in (t.get("specimen") or []) for i in (sp.get("images") or [])]
    if len(images) >= PAGE:
        return None
    return {c.strip().lower() for c, shot in images if shot != "l"}


# ------------------------------------------------------------------------------------------- 1. species images page

def test_species_images_page_shows_images_from_anywhere_with_the_place_first():
    """Michele's case: images.do of Leptogenys purpurea with countryName=Indonesia shows images of specimens from
    outside Indonesia (the representative CASENT0270569 of Papua New Guinea when it has images); the Indonesian
    specimens come first and are marked; placeOnly=true shows Indonesian specimens only."""
    html = fetch_all_view(MICHELE)
    if not html:
        pytest.skip(MICHELE + ": not a normal page")
    shown = shown_specimens(html)
    if not shown:
        pytest.skip(MICHELE + ": no imaged specimen (data)")
    rows = api_specimens("leptogenys", "purpurea")
    if rows is None:
        pytest.skip("the API's specimens of Leptogenys purpurea: more than 6 pages")
    country = {(r.get("code") or "").lower(): (r.get("country") or "") for r in rows}
    wrong = []
    codes = [c for c, _ in shown]
    marked = [c for c, m in shown if m]
    if not any(country.get(c) and country.get(c) != "Indonesia" for c in codes):
        wrong.append(f"no specimen from outside Indonesia shown: {codes[:10]}")
    rep = [r for r in rows if (r.get("code") or "").lower() == MICHELE_REP and int(r.get("imageCount") or 0) > 0]
    if rep and MICHELE_REP not in codes:
        wrong.append(f"{MICHELE_REP} (the representative, {rep[0].get('country')}) is not shown")
    first_unmarked = next((i for i, (_, m) in enumerate(shown) if not m), len(shown))
    if any(m for _, m in shown[first_unmarked:]):
        wrong.append(f"a marked specimen after an unmarked one: {shown}")
    for c in marked:
        if country.get(c) not in (None, "Indonesia"):
            wrong.append(f"{c} marked from Indonesia, the API says {country.get(c)}")
    for c in set(codes) - set(marked):
        if country.get(c) == "Indonesia":
            wrong.append(f"{c} is from Indonesia (API) but not marked")
    if not marked and "No specimens from Indonesia are imaged yet" not in html:
        wrong.append('no Indonesian specimen shown, and no "No specimens from Indonesia are imaged yet"')
    if 'class="images_place_only"' not in html:
        wrong.append('no "Only specimens from Indonesia" link')
    only = fetch_all_view(MICHELE + "&placeOnly=true")
    if only:
        only_codes = [c for c, _ in shown_specimens(only)]
        foreign = [c for c in only_codes if country.get(c) not in (None, "Indonesia")]
        if foreign:
            wrong.append(f"placeOnly=true shows specimens from elsewhere: {foreign[:5]}")
        if set(only_codes) != set(marked):
            wrong.append(f"placeOnly=true shows {sorted(only_codes)}, the default view marks {sorted(marked)}")
    assert not wrong, f"{MICHELE}:\n" + "\n".join(wrong)


def _species_with_images(p):
    """A species row of the place's list with images of the place (1 to 300 specimens): (name, images.do params)."""
    html = ns.fetch(p.list_url("species"))
    rows = []
    for r in ns.list_rows(html or ""):
        count, _link, images, params = list_row_cells(r["html"])
        if r["name"] and count and count <= 300 and images and params:
            rows.append((r["name"], params))
    got = ns.pick(rows, 1, "anywhere-species" + repr(p))
    return got[0] if got else None


def test_place_specimens_first_and_place_only_shows_them():
    """The day's species of the day's country, adm1, bioregion and museum: the specimens marked "from <place>" come
    first and are exactly those images.do?...&placeOnly=true shows; the default view shows at least as many."""
    places = [p for p in ns.one_place_of_each_kind("anywhere") if p.kind in ("country", "adm1", "bioregion", "museum")]
    wrong, checked = [], []
    for p in places:
        got = _species_with_images(p)
        if not got:
            continue
        name, params = got
        path = "/images.do?" + params
        page, only = fetch_all_view(path), fetch_all_view(path + "&placeOnly=true")
        if not page or not only:
            continue
        shown, only_codes = shown_specimens(page), [c for c, _ in shown_specimens(only)]
        marked = [c for c, m in shown if m]
        first_unmarked = next((i for i, (_, m) in enumerate(shown) if not m), len(shown))
        if any(m for _, m in shown[first_unmarked:]):
            wrong.append(f"{p} {name}: a marked specimen after an unmarked one")
        if set(marked) != set(only_codes):
            wrong.append(f"{p} {name}: marked {sorted(marked)[:8]}, placeOnly=true shows {sorted(only_codes)[:8]}")
        if len(shown) < len(only_codes):
            wrong.append(f"{p} {name}: {len(shown)} specimens from anywhere, fewer than the place's {len(only_codes)}")
        checked.append(f"{p} {name}")
    if not checked:
        pytest.skip("no species with images on the lists of the day's places " + ns.day_note())
    assert not wrong, f"{len(checked)} species {ns.day_note()}:\n" + "\n".join(wrong)


# ------------------------------------------------------------------------------------------- 3. Images column

def _comoros():
    for r in ns.geolocales("country"):
        if r.get("geolocaleName") == "Comoros":
            return ns.geolocale_place(r)
    return None


def test_list_images_column_shows_the_images_in_antweb_and_of_the_place():
    """Row 44: on Comoros and the day's places of every kind, species rows "T Images (P from X)": T >= P, the format
    follows the numbers, and T = the API's images of every specimen of the name (sum of imageCount)."""
    places = [p for p in [_comoros()] + ns.one_place_of_each_kind("anywhere-column") if p is not None]
    wrong, checked, left_out = [], [], []
    for p in places:
        html = ns.fetch(p.list_url("species"))
        if not html:
            continue
        rows = []
        for r in ns.list_rows(html):
            cell = re.search(r'<div class="list_extras images">(.*?)</div>', r["html"], re.S)
            q = re.search(r"href='[^']*(?:images|browse)\.do\?([^']*)'", r["html"])
            if r["name"] and cell and q:
                rows.append((r["name"], cell.group(1), parse_qs(unescape(q.group(1)))))
        for name, cell, q in ns.pick(rows, MAX_ROWS, "anywhere-column" + repr(p)):
            total, place = images_cell(cell)
            text = ns.text_of(cell)
            if total is None:
                wrong.append(f"{p} {name}: Images cell {text!r} is neither 'No Images', 'N Images' nor 'T Images (P from X)'")
                continue
            if place > total:
                wrong.append(f"{p} {name}: {text!r}: the place has more than AntWeb")
            if total == place and "(" in text:
                wrong.append(f"{p} {name}: {text!r}: both numbers shown though equal")
            if p.kind == "project" and "(" in text:
                wrong.append(f"{p} {name}: {text!r}: a project's list counts images from anywhere")
            genus, species = (q.get("genus") or [""])[0], (q.get("species") or [""])[0]
            got = api_specimens(genus, species, max_pages=4) if genus and species else None
            if got is None:
                left_out.append(f"{p} {name}: not recounted (more than 4 API pages)")
                continue
            ant = ant_images_by_code(name)
            if ant is None:
                left_out.append(f"{p} {name}: not recounted (more than 500 images)")
                continue
            recount = sum(ant.get((s.get("code") or "").lower(), 0) for s in got if (s.get("antwebTaxonName") or "").lower() == name)
            if recount != total:
                wrong.append(f"{p} {name}: {text!r}, the API's specimens of the name have {recount} images of the ant")
            checked.append(f"{p} {name}")
    if not checked:
        pytest.skip("no species row recounted " + ns.day_note() + "; left out: " + "; ".join(left_out[:10]))
    assert not wrong, f"{len(checked)} rows {ns.day_note()} ({PENDING}); left out: {left_out[:10]}:\n" + "\n".join(wrong)


def test_list_says_what_the_images_column_counts():
    """The definition under a place's list: images of the ant, label images counted separately (Brian Fisher 2026-10-27)."""
    p = _comoros() or ns.madagascar()
    html = ns.get(p.list_url("species"))
    m = re.search(r'<span id="images-definition">(.*?)</span>', html, re.S)
    assert m, f"{p}: no definition of the Images column under the list"
    assert "images of the ant" in m.group(1) and "label images are counted separately" in m.group(1), m.group(1)


# ------------------------------------------------------------------------------------------- 5. imaged castes

def imaged_castes(html):
    """The data of the species page's "Imaged:" line: caste -> imaged specimens, or None without the line."""
    m = re.search(r'<div id="imaged_castes"([^>]*)>', html)
    if not m:
        return None
    return {k: int(v) for k, v in re.findall(r'data-([a-z]+)="(\d+)"', m.group(1))}


def _day_species():
    """The day's species of Madagascar's list with images (at most 300 specimens): (description.do path, taxon name,
    genus, species), or None."""
    p = ns.madagascar()
    html = ns.fetch(p.list_url("species")) or ""
    rows = []
    for r in ns.list_rows(html):
        count, _link, images, params = list_row_cells(r["html"])
        q = parse_qs(params or "")
        genus, species = (q.get("genus") or [""])[0], (q.get("species") or [""])[0]
        if r["name"] and count and count <= 300 and images and genus and species and r["name"].count(" ") == 1:
            rows.append(("/description.do?taxonName=" + quote_plus(r["name"]), r["name"], genus, species))
    got = ns.pick(rows, 1, "anywhere-castes")
    return got[0] if got else None


def test_species_page_imaged_castes_equal_a_recount():
    """Row 120: the species page's "Imaged:" line (Leptogenys purpurea and the day's species of Madagascar) = the
    name's specimens from anywhere with an image of the ant (/taxaImages, labels left out), by caste (/specimens)."""
    wrong, checked = [], []
    for case in [(MICHELE_SPECIES, "ponerinaeleptogenys purpurea", "leptogenys", "purpurea"), _day_species()]:
        if not case:
            continue
        path, name, genus, species = case
        html = ns.fetch(path)
        if not html:
            continue
        shown = imaged_castes(html)
        if shown is None:
            wrong.append(f"{path}: no 'Imaged:' line")
            continue
        rows = api_specimens(genus, species)
        imaged = ant_imaged_codes(name)
        if rows is None or imaged is None:
            continue
        recount = {c: 0 for c in CASTES + ("unknown",)}
        for r in rows:
            if (r.get("antwebTaxonName") or "").lower() != name or (r.get("code") or "").lower() not in imaged:
                continue
            caste = (r.get("caste") or "").strip().lower()
            recount[caste if caste in CASTES else "unknown"] += 1
        if shown != recount:
            wrong.append(f"{path} ({name}): the page says {shown}, the API recount {recount}")
        checked.append(name)
    if not checked:
        pytest.skip("no species page recounted (data, or more than one API page) " + ns.day_note())
    assert not wrong, f"{checked} {ns.day_note()}:\n" + "\n".join(wrong)

