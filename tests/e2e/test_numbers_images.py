"""The image numbers of the image, comparison, browse and search pages (docs/numbers.md rows 28, 44, 49, 55 to 62, 64,
71), 2026-10-27.

Every kind of place (country, adm1, region, subregion, bioregion, museum, project) on every run, a different place of
each kind each day (numbers_sample.py), and in it the day's genus of its list (a genus with images and at most
MAX_CHILDREN species). The numbers of one page are compared with another page that counts the same children, or
recounted from the API's records:

  row 44  a species row's Images on the place's list (since 2026-10-28 one number, Brian Fisher: the images of the
          name in AntWeb, the allantwebants list row's image_count, nightly set 4; from 10-27 to 10-28 also the
          place's in brackets) = the images of the ant (labels never count, Brian Fisher 2026-10-27) of every
          specimen of that name in AntWeb, recounted from the API (/taxaImages of the name, shot types other than l,
          of the specimens /specimens files under the name, anywhere), and since 2026-10-28 (decision 3) of the
          specimens filed under its synonyms (/taxa?currentValidName; when the API cannot list them, Images >= the
          recount of the name only). The cell never shows a second number.
  row 55  images.do "I Species Imaged (M total)": M = browse.do's N = the rows it lists; I = the children shown in the
          All view (cookie thumbs=a) with a head, profile or dorsal picture (Taxon.getUniqueChildImagesCount("p",
          "h", "d")).
  row 56  images.do child Images (default view and All view) = the child's Images cell on the place's list (browse.do
          of the genus lists them; ant images of the name in AntWeb, one number since 2026-10-28) where the name has images in
          AntWeb; where it has none the page shows the images of the ant of the specimen it shows.
  row 49  genus browse hidden "M Imaged Species (out of N total)": M = the children with pictures (images.do All
          view; both use the children's pictures, Taxon.getHasImages).
  row 57  taxonomicPage.do images view "I X Imaged (out of M Total)": M = the list header of the same list (row 38),
          I = the children shown with pictures in the All view, I + U = M with U of "X with no image in AntWeb (U)".
  row 58  getComparison.do&shot=Head (one view): the children shown = the children images.do shows with a head
          picture (default view).
  row 59  mapComparison.do "(N X Mapped)": N = browse.do's N (every child); N = the children with a map is B19
          (xfail).
  row 60  getComparison.do: one checkbox per child with pictures = the children of images.do's All view.
  row 62  images.do "X with no image in AntWeb (U)" (until 2026-10-27 "with no image from this place", B45):
          U + the children with pictures = M; the names listed (at most 50) are children without pictures.
  row 61  browse.do of a species: each specimen's "N Images" = the specimen's images in the image table (API
          /images?specimenCode, live); "No Images" and the no-photos mark agree.
  row 64  specimenImages.do "N Images" = the API's /images count of the specimen (live), for the specimens with
          images of the species browse pages above.
  row 28  place page "(N additional specimens)" (data-label-only) + Imaged specimens (data-imaged, validated by the
          count check) = the place's specimens with any image (API /specimens hasImage=true, family formicidae:
          specimen.image_count, every image file), for the day's museum with at most MAX_IMAGED specimens with
          images; and Images (data-images) = the sum of their image counts.
  row 71  recentSearchResults.do?searchMethod=recentImageSearch&daysAgo=30 "N Images" (or "of N records") = the
          images uploaded in the last 30 days: between the API's /images?since=29 and since=31 (the two servers may
          count days in different time zones); every row is one image (B47, fixed 2026-10-27: grouped by name, shot,
          number, upload time and group, images of two specimens were one row).

Read-only, anonymous. A list row is stored by the nightly run; after an upload it can be pending until the next run.

Found while writing these tests (docs/numbers.md section 3):
  B45  images.do, the taxonomicPage images view and the comparison pages decide whether a child is imaged from its
       pictures, which come from any specimen of the name anywhere in AntWeb (Taxon.setImages: the picked default
       specimen, else any specimen of the names on the list); "X with no image from this place" therefore lists only
       children without a picture anywhere, and a child with no image from this place shows "N Images" = the number of
       pictures of that other specimen (taxonChildImageSet.jsp).
  B46  (fixed 2026-10-27) images.do All view: every name showed "0 Images" (getHasImagesCount, counted for specimens
       only).
  B47  (fixed 2026-10-27) recent images: one row per name, shot, number, upload second and group, not per image.
"""
import os
import re
from html import unescape
from urllib.parse import parse_qs

import pytest
import requests

import numbers_sample as ns
from antweb import BASE_URL, classify, new_session

PENDING = "list rows are those of the last nightly run (set 4); after an upload they are pending"
MAX_CHILDREN = int(os.environ.get("ANTWEB_SAMPLE_MAX_CHILDREN", "40"))
MAX_ROWS = int(os.environ.get("ANTWEB_SAMPLE_MAX_IMAGE_ROWS", "4"))
MAX_SPECIMENS = int(os.environ.get("ANTWEB_SAMPLE_MAX_SPECIMENS", "300"))
MAX_GENERA = int(os.environ.get("ANTWEB_SAMPLE_MAX_GENERA", "120"))
MAX_IMAGED = int(os.environ.get("ANTWEB_SAMPLE_MAX_IMAGED", "3000"))

_PLACES = {}


def places():
    if "all" not in _PLACES:
        _PLACES["all"] = ns.one_place_of_each_kind("images")
    if not _PLACES["all"]:
        pytest.skip("no place found (API /geolocales, /bioregions, museums.do, projects.do) " + ns.day_note())
    return _PLACES["all"]


def fetch_all_view(path):
    """The page with the All view chosen ("Browse Images By: All", cookie thumbs=a), or None."""
    s = new_session()
    s.cookies.set("thumbs", "a")
    return ns.fetch(path, s)


# ------------------------------------------------------------------------------------------- parsing

def _num(text):
    """'No Images' -> 0, "<span class='numbers'>12</span> Images" or '12 Images' -> 12, else None."""
    t = ns.text_of(text)
    if t.startswith("No "):
        return 0
    m = re.match(r"([\d,]+) Images", t)
    return ns.n(m.group(1)) if m else None


def images_cell(cell):
    """The number of a list row's Images cell (since 2026-10-28, Brian Fisher: one number, the images of the ant of
    the name in AntWeb): 'No Images' -> 0, 'N Images' -> N; None for anything else, such as the two-number form
    'T Images (P from Comoros)' of 10-27 (it wrapped, and the number script blanked a 0: "( from Comoros)")."""
    t = ns.text_of(cell)
    if t.startswith("No "):
        return 0
    m = re.fullmatch(r"([\d,]+) Images", t)
    return ns.n(m.group(1)) if m else None


# The two-number form is still matched, so a page that shows it again is caught (images_cell gives None).
_CHILD_IMAGES = (r"(No Images|(?:<span[^>]*>)?[\d,]+(?:</span>)? Images"
                 r"(?: \((?:<span[^>]*>)?[\d,]+(?:</span>)? (?:from|in) [^)<]*\))?)")


def images_by_code(taxon_name):
    """Images of each specimen of the name, from /taxaImages: (code -> images of the ant (shot type not l),
    code -> every image, labels included); None when the name has more than one page (500 images)."""
    body = ns.api(f"taxaImages?taxonName={ns.quote_plus(taxon_name)}&limit=500")
    if body is None:
        return None
    images = [((sp.get("code") or "").strip().lower(), (i.get("shotType") or "").strip().lower())
              for t in ns.api_rows(body) for sp in (t.get("specimen") or []) for i in (sp.get("images") or [])]
    if len(images) >= 500:
        return None
    ant, every = {}, {}
    for code, shot in images:
        every[code] = every.get(code, 0) + 1
        if shot != "l":
            ant[code] = ant.get(code, 0) + 1
    return ant, every


def ant_images_by_code(taxon_name):
    """Images of the ant (shot type not l) of each specimen of the name, from /taxaImages: code -> count; None when the
    name has more than one page (500 images). Since 2026-10-27 (Brian Fisher) every Images column counts these."""
    got = images_by_code(taxon_name)
    return got[0] if got else None


# The statuses the AntWeb-wide list (allantwebants) has rows for (StatusSet.getCountables): a name of another status with
# a current valid name (a synonym, an obsolete combination...) has no row; its specimens' images count under the valid
# name (decision 3, Brian Fisher 2026-10-28; PlaceTotals.allAntwebImagesSql).
LISTED_STATUSES = {"valid", "unrecognized", "morphotaxon", "indetermined", "unidentifiable"}


def synonyms_of(taxon_name):
    """The names whose images count under taxon_name (decision 3): /taxa?currentValidName (2026-10-28), the names with
    that current valid name, not themselves, of a status the AntWeb-wide list has no row for. None when the API cannot
    say (an API without the filter refuses it, HTTP 400, or returns other names too; or more than one page)."""
    body = ns.api(f"taxa?currentValidName={ns.quote_plus(taxon_name)}&limit=200")
    if body is None:
        return None
    rows = ns.api_rows(body)
    if len(rows) >= 200 or any((t.get("currentValidName") or "").lower() != taxon_name.lower() for t in rows):
        return None
    return [t for t in rows if (t.get("taxonName") or "").lower() != taxon_name.lower()
            and (t.get("status") or "").lower() not in LISTED_STATUSES]


def name_images(name, genus, species, max_pages=4):
    """The recount of a list row's Images (row 44): the images of every specimen in AntWeb filed under the name and, since
    2026-10-28 (decision 3), under its synonyms (synonyms_of). Returns (ant, every, codes, exact): images of the ant and
    every image by code, the specimens' codes, and exact False when the synonyms could not be listed (then the list
    number may be larger than the recount, never smaller); or a string saying why the name is not recounted."""
    names = [(name, genus, species)]
    syn = synonyms_of(name)
    exact = syn is not None
    for t in syn or []:
        if t.get("genus") and t.get("species"):
            names.append(((t.get("taxonName") or "").lower(), t["genus"], t["species"]))
        else:
            exact = False
    ant, every, codes = {}, {}, []
    for n, g, sp in names:
        got = ns.api_all(f"specimens?genus={ns.quote_plus(g)}&species={ns.quote_plus(sp)}", max_pages=max_pages)
        if got is None:
            return f"not recounted (more than {max_pages} API pages of {n})"
        both = images_by_code(n)
        if both is None:
            return f"not recounted (more than 500 images of {n})"
        for code, k in both[0].items():
            ant[code] = ant.get(code, 0) + k
        for code, k in both[1].items():
            every[code] = every.get(code, 0) + k
        codes += [ns.specimen_code(s) for s in got if (s.get("antwebTaxonName") or "").lower() == n]
    return ant, every, codes, exact


OLD_SET_4 = "the old count with label images (pending until nightly set 4 runs with the ant-only counts)"


def old_set_4(shown, ant, every, codes):
    """True when a list row's Images number is the count before 2026-10-27 (every image, labels included) and differs
    from the ant-only recount: the row is pending until the nightly set 4 recounts it with images of the ant only."""
    total = sum(every.get(c, 0) for c in codes)
    return shown == total and total != sum(ant.get(c, 0) for c in codes)


def _params(link):
    """The query of an images.do or browse.do link, unescaped, without the domain and page."""
    return unescape(link.split("?", 1)[1]) if link and "?" in link else None


def _key(params):
    """A comparable form of a child's browser parameters (order and statusSet ignored)."""
    q = parse_qs(params or "")
    return tuple(sorted((k, v[0].lower()) for k, v in q.items() if k != "statusSet"))


def list_row_cells(row_html):
    """A list row's (taxonReportBody.jsp) place count ("N Species", "N Specimens"; not the Global column), its browse
    link, its Images number (the images of the name in AntWeb, since 2026-10-28; None when the cell is not one
    number) and the browser parameters of its images.do link (None when it says No Images)."""
    count, link = None, None
    for cell in re.findall(r'<div class="list_extras specimens">(.*?)</div>', row_html, re.S):
        if "global=true" in cell:
            continue
        lk = re.search(r"href='([^']*browse\.do\?[^']*)'", cell)
        num = re.search(r"<span class='numbers'>(\d+)</span>", cell)
        if num:
            count, link = int(num.group(1)), lk and unescape(lk.group(1))
            break
        if "No " in cell:
            count = 0
            break
    images, params = None, None
    m = re.search(r'<div class="list_extras images">(.*?)</div>', row_html, re.S)
    if m:
        images = images_cell(m.group(1))
        lk = re.search(r"href='([^']*images\.do\?[^']*)'", m.group(1))
        params = _params(lk.group(1)) if lk else None
    return count, link, images, params


def images_header(html):
    m = re.search(r'<h2 class="display_count">\s*([\d,]+) .*?Imaged\s*\(([\d,]+) total\)\s*</h2>', html, re.S)
    return (ns.n(m.group(1)), ns.n(m.group(2))) if m else (None, None)


def default_view_children(html):
    """images.do default view (head pictures): one (browser params key, pretty name, N Images) per child shown."""
    out = []
    parts = re.split(r'<div class="slide medium', html)
    for chunk in parts[1:]:
        name = re.search(r'<div class="name">.*?<a href="[^"]*images\.do\?([^"]+)">(.*?)</a>', chunk, re.S)
        if not name:
            continue
        cnt = re.search(r'float:right; text-align:right;">\s*' + _CHILD_IMAGES, chunk)
        out.append((_key(unescape(name.group(1))), ns.text_of(name.group(2)), images_cell(cnt.group(1)) if cnt else None))
    return out


def all_view_children(html):
    """images.do or images view with the All view: one (key, pretty name, N Images, shot types shown) per child with
    pictures."""
    out = []
    parts = re.split(r'<div class="ratio_name">', html)
    for chunk in parts[1:]:
        name = re.search(r'<a href="[^"]*images\.do\?([^"]+)">(.*?)</a>', chunk, re.S)
        if not name:
            continue
        cnt = re.search(r'<div class="ratio_has_images">(.*?)</div>', chunk, re.S)
        shots = set(s.lower() for s in re.findall(r"bigPicture\.do\?name=[^&]+&shot=([^&]+)&number=", chunk))
        out.append((_key(unescape(name.group(1))), ns.text_of(name.group(2)), images_cell(cnt.group(1)) if cnt else None, shots))
    return out


def no_image_list(html):
    """'X with no image from this place (U):' -> (U, names listed), or (None, [])."""
    m = re.search(r"<b>(\w+) with no (?:image from this place|image in AntWeb) \(([\d,]+)\):</b>(.*?)(?:<br|</div|<div|$)", html, re.S)
    if not m:
        return None, []
    return ns.n(m.group(2)), [unescape(t).strip().lower() for t in re.findall(r"description\.do\?taxonName=([^'\"]+)['\"]", m.group(3))]


def browse_numbers(html):
    n, text = ns.header(html)
    hidden = re.search(r'<div id="imagecount"[^>]*><span class="numbers">([\d,]+)</span>.*?out of <span class="numbers">([\d,]+)</span>', html, re.S)
    return n, text, (ns.n(hidden.group(1)) if hidden else None), (ns.n(hidden.group(2)) if hidden else None)


# ------------------------------------------------------------------------------------------- the day's genus

_GENUS = {}


def genus_pages(p):
    """The day's genus of the place's list (with images, 1 to MAX_CHILDREN species) and its pages, or None."""
    key = repr(p)
    if key in _GENUS:
        return _GENUS[key]
    _GENUS[key] = None
    html = ns.fetch(p.list_url("genus"))
    if html is None:
        return None
    rows = []
    for r in ns.list_rows(html):
        count, link, images, params = list_row_cells(r["html"])
        if count and 1 <= count <= MAX_CHILDREN and images and params:
            rows.append((r, count, params))
    got = ns.pick(rows, 1, "images-genus" + key)
    if not got:
        return None
    row, count, params = got[0]
    q = params + "&statusSet=all"
    pages = {"row": row, "count": count, "params": params, "where": f"{p} {row['name'] or params}",
             "browse": ns.fetch("/browse.do?" + q),
             "images": ns.fetch("/images.do?" + q),
             "images_all": fetch_all_view("/images.do?" + q)}
    if not pages["browse"] or not pages["images"]:
        return None
    _GENUS[key] = pages
    return pages


def each_genus():
    got = [(p, g) for p in places() for g in [genus_pages(p)] if g]
    if not got:
        pytest.skip("no genus with images found on the lists of the day's places " + ns.day_note())
    return got


def browse_children(g):
    """The children rows of the genus' browse page: browser params key -> (name, Images in AntWeb as the list shows
    them)."""
    out = {}
    for r in ns.list_rows(g["browse"]):
        _c, link, _images, params = list_row_cells(r["html"])
        params = params or _params(link)   # both links carry the child's browser parameters
        cell = re.search(r'<div class="list_extras images">(.*?)</div>', r["html"], re.S)
        shown = images_cell(cell.group(1)) if cell else None
        if params and shown is not None:
            out[_key(params)] = (r["name"], shown)
    return out


# ------------------------------------------------------------------------------------------- rows 55, 49, 60, 58, 59

def test_images_page_header_counts_the_children():
    """Row 55: images.do "I Species Imaged (M total)": M = browse.do's N = the rows browse.do lists; I = the children of
    the All view with a head, profile or dorsal picture; I <= M."""
    wrong, checked = [], []
    for p, g in each_genus():
        imaged, total = images_header(g["images"])
        n, text, _m, _of = browse_numbers(g["browse"])
        if total is None:
            wrong.append(f"{g['where']}: images.do without 'I ... Imaged (M total)'")
            continue
        if total != n:
            wrong.append(f"{g['where']}: images.do {total} total, browse.do {text!r}")
        if n is not None and len(ns.list_rows(g["browse"])) != n:
            wrong.append(f"{g['where']}: browse.do {text!r}, {len(ns.list_rows(g['browse']))} rows listed")
        if imaged > total:
            wrong.append(f"{g['where']}: {imaged} imaged of {total}")
        if g["images_all"]:
            hpd = sum(1 for c in all_view_children(g["images_all"]) if c[3] & {"h", "p", "d"})
            if hpd != imaged:
                wrong.append(f"{g['where']}: images.do {imaged} imaged, the All view shows {hpd} children with a head, profile or dorsal picture")
        checked.append(g["where"])
    assert not wrong, f"{len(checked)} genera {ns.day_note()}:\n" + "\n".join(wrong)


def test_browse_imaged_count_equals_the_children_with_pictures():
    """Row 49: browse.do of the genus, hidden "M Imaged Species (out of N total)": N = the header's N, M = the children
    images.do's All view shows (both: children with pictures)."""
    wrong, checked = [], []
    for p, g in each_genus():
        n, text, m, of = browse_numbers(g["browse"])
        if m is None or not g["images_all"]:
            continue
        shown = len(all_view_children(g["images_all"]))
        if of != n:
            wrong.append(f"{g['where']}: hidden 'out of {of}', header {text!r}")
        if m != shown:
            wrong.append(f"{g['where']}: browse.do {m} imaged, images.do All view {shown} children with pictures")
        checked.append(g["where"])
    if not checked:
        pytest.skip("no browse page with the hidden imaged count " + ns.day_note())
    assert not wrong, f"{len(checked)} genera {ns.day_note()}:\n" + "\n".join(wrong)


def test_comparison_has_one_box_per_child_with_pictures():
    """Row 60: getComparison.do lists one checkbox per child with pictures = the children of images.do's All view."""
    wrong, checked = [], []
    for p, g in each_genus():
        html = ns.fetch("/getComparison.do?" + g["params"] + "&statusSet=all")
        if html is None or not g["images_all"]:
            continue
        boxes = len(re.findall(r'<input type="checkbox" name="chosen" value="', html))
        shown = len(all_view_children(g["images_all"]))
        if boxes != shown:
            wrong.append(f"{g['where']}: getComparison.do {boxes} checkboxes, images.do All view {shown} children with pictures")
        checked.append(g["where"])
    if not checked:
        pytest.skip("no comparison page read " + ns.day_note())
    assert not wrong, f"{len(checked)} genera {ns.day_note()}:\n" + "\n".join(wrong)


def test_one_view_shows_the_children_with_that_view():
    """Row 58: getComparison.do&shot=Head shows one picture per child with a head picture = the children images.do
    shows in its default (head) view."""
    wrong, checked = [], []
    for p, g in each_genus():
        html = ns.fetch("/getComparison.do?" + g["params"] + "&statusSet=all&shot=Head")
        if html is None:
            continue
        one = sorted(ns.text_of(t) for t in re.findall(r'<div class="name half">\s*<a [^>]*>(.*?)</a>', html, re.S))
        heads = sorted(c[1] for c in default_view_children(g["images"]))
        if one != heads:
            wrong.append(f"{g['where']}: one view {len(one)} head pictures, images.do {len(heads)};"
                         f" only in one view {sorted(set(one) - set(heads))[:5]}, only on images.do {sorted(set(heads) - set(one))[:5]}")
        checked.append(g["where"])
    if not checked:
        pytest.skip("no one-view page read " + ns.day_note())
    assert not wrong, f"{len(checked)} genera {ns.day_note()}:\n" + "\n".join(wrong)


def _maps(g):
    html = ns.fetch("/mapComparison.do?" + g["params"] + "&statusSet=all")
    if html is None:
        return None
    m = re.search(r"([\d,]+) [A-Za-z]+ Mapped\)", html)
    return (ns.n(m.group(1)) if m else None), html.count('<div class="small_map first">')


def test_map_comparison_counts_every_child():
    """Row 59: mapComparison.do "(N X Mapped)": N = browse.do's N (the page counts every child, mapped or not)."""
    wrong, checked = [], []
    for p, g in each_genus():
        got = _maps(g)
        if got is None:
            continue
        n, _maps_shown = got
        bn, text, _m, _of = browse_numbers(g["browse"])
        if n != bn:
            wrong.append(f"{g['where']}: mapComparison.do {n} Mapped, browse.do {text!r}")
        checked.append(g["where"])
    if not checked:
        pytest.skip("no map comparison read (two at a time for visitors) " + ns.day_note())
    assert not wrong, f"{len(checked)} genera {ns.day_note()}:\n" + "\n".join(wrong)


@pytest.mark.xfail(reason="B19: mapComparison.do 'N X Mapped' counts every child, mapped or not", strict=False)
def test_map_comparison_counts_the_children_mapped():
    """Row 59 (B19): "(N X Mapped)" = the children shown with a map."""
    wrong, checked = [], []
    for p, g in each_genus():
        got = _maps(g)
        if got is None:
            continue
        n, shown = got
        if n != shown:
            wrong.append(f"{g['where']}: {n} Mapped, {shown} maps shown")
        checked.append(g["where"])
    if not checked:
        pytest.skip("no map comparison read " + ns.day_note())
    assert not wrong, f"{ns.day_note()}:\n" + "\n".join(wrong)


# ------------------------------------------------------------------------------------------- rows 56, 62, 57

def test_images_page_child_counts_equal_the_list_rows():
    """Row 56: on images.do (default view and All view) a child whose row on the place's list has images in AntWeb
    shows the same number as the list: "N Images", the images of the ant of the name in AntWeb (one number since
    2026-10-28; ant images only since 2026-10-27; the All view showed "0 Images" for every name until 2026-10-27, B46)."""
    wrong, checked = [], 0
    for p, g in each_genus():
        rows = browse_children(g)
        views = [("default view", [(k, nm, c) for k, nm, c in default_view_children(g["images"])])]
        if g["images_all"]:
            views.append(("All view", [(k, nm, c) for k, nm, c, _s in all_view_children(g["images_all"])]))
        for view, children in views:
            for key, name, shown in children:
                listed = rows.get(key)
                if not listed or not listed[1]:
                    continue   # No Images on the list: the page may show the ant images of the specimen shown
                checked += 1
                if shown != listed[1]:
                    wrong.append(f"{g['where']} {view} {name}: images.do {shown} Images, list row {listed[1]}")
    if not checked:
        pytest.skip("no images.do child with images on the place's list " + ns.day_note())
    assert not wrong, f"{checked} children {ns.day_note()} ({PENDING}):\n" + "\n".join(wrong)


def test_no_image_list_names_the_children_without_pictures():
    """Row 62: images.do "X with no image from this place (U)": U + the children shown with pictures (All view) = M
    total; the names listed (at most 50) are none of the children shown."""
    wrong, checked = [], []
    for p, g in each_genus():
        if not g["images_all"]:
            continue
        _i, total = images_header(g["images"])
        u, names = no_image_list(g["images"])
        shown = all_view_children(g["images_all"])
        u = u or 0
        if total is not None and u + len(shown) != total:
            wrong.append(f"{g['where']}: {u} with no image + {len(shown)} with pictures, {total} total")
        if len(names) != min(u, 50):
            wrong.append(f"{g['where']}: with no image ({u}), {len(names)} names listed")
        checked.append(g["where"])
    if not checked:
        pytest.skip("no images.do All view read " + ns.day_note())
    assert not wrong, f"{len(checked)} genera {ns.day_note()}:\n" + "\n".join(wrong)


def test_images_view_header_counts_the_children_with_pictures():
    """Row 57: taxonomicPage.do images view of the place's genera "I Genera Imaged (out of M Total)": M = the header of
    the same list without images (validated by row 38); I = the children shown with pictures (All view); I + U = M."""
    wrong, checked = [], []
    for p in places():
        listed = ns.fetch(p.list_url("genus"))
        n, text = ns.header(listed or "")
        if n is None or n > MAX_GENERA:
            continue   # the images view picks a picture for every genus: too slow for a large list
        view = fetch_all_view(p.list_url("genus") + "&images=true")
        if view is None:
            continue
        m = re.search(r'class="display_count" data-imaged="(\d+)" data-total="(\d+)">', view)
        if not m:
            wrong.append(f"{p}: images view without data-imaged")
            continue
        imaged, total = int(m.group(1)), int(m.group(2))
        shown = len(all_view_children(view))
        u, _names = no_image_list(view)
        if n is not None and total != n:
            wrong.append(f"{p}: images view {total} Total, list header {text!r}")
        if imaged != shown:
            wrong.append(f"{p}: images view {imaged} Imaged, {shown} genera shown with pictures")
        if imaged + (u or 0) != total:
            wrong.append(f"{p}: {imaged} Imaged + {u or 0} with no image, {total} Total")
        checked.append(repr(p))
    if not checked:
        pytest.skip("no images view read " + ns.day_note())
    assert not wrong, f"{len(checked)} places {ns.day_note()}:\n" + "\n".join(wrong)


# ------------------------------------------------------------------------------------------- row 44

def test_list_images_column_equals_the_api_recount():
    """Row 44: the species rows (of the day's genus, at most MAX_ROWS) of the place's list: Images (one number since
    2026-10-28) = the images of the ant (labels left out, since 2026-10-27) of every specimen of that name in AntWeb,
    recounted from the API (/taxaImages of the name, the name's specimens of /specimens from anywhere). The place's
    own number is no longer shown: the cell never says "from" or "in" a place."""
    wrong, checked, left_out = [], [], []
    for p, g in each_genus():
        rows = []
        for r in ns.list_rows(g["browse"]):
            count, link, images, params = list_row_cells(r["html"])
            cell = re.search(r'<div class="list_extras images">(.*?)</div>', r["html"], re.S)
            text = ns.text_of(cell.group(1)) if cell else ""
            if re.search(r"\(|\bfrom |\bin [A-Z]", text):
                wrong.append(f"{p} {r['name']}: Images cell {text!r} shows a second number")
            if r["name"] and images is not None and link:
                rows.append((r["name"], images, parse_qs(_params(link) or "")))
        for name, images, q in ns.pick(rows, MAX_ROWS, "images-rows" + repr(p)):
            genus, species = (q.get("genus") or [""])[0], (q.get("species") or [""])[0]
            if not genus or not species:
                continue
            got = name_images(name, genus, species)
            if isinstance(got, str):
                left_out.append(f"{p} {name}: {got}")
                continue
            ant, every, codes, exact = got
            recount = sum(ant.get(c, 0) for c in codes)
            if not exact and images >= recount:
                left_out.append(f"{p} {name}: {images} Images >= {recount} (synonyms not listed by the API: '>=' only)")
                continue
            if recount != images and old_set_4(images, ant, every, codes):
                left_out.append(f"{p} {name}: {images} Images, {OLD_SET_4}")
                continue
            if recount > images and ns.allantweb_images_counted() is False:
                left_out.append(f"{p} {name}: {images} Images, the AntWeb-wide row is not counted yet on this server"
                                " (no compute.log line 'AntWeb-wide list Images counted'): pending")
                continue
            if recount != images:
                wrong.append(f"{p} {name}: list row {images} Images, the API's {len(codes)} specimens of the name and its synonyms in AntWeb have {recount} images of the ant")
            checked.append(f"{p} {name}")
    if not checked and not wrong:
        pytest.skip("no species row recounted " + ns.day_note() + "; left out: " + "; ".join(left_out[:10]))
    assert not wrong, (f"{len(checked)} rows {ns.day_note()} ({PENDING}); left out: {left_out[:10]}:\n" + "\n".join(wrong))


# ------------------------------------------------------------------------------------------- rows 61, 64

_SPECIES = {}


def species_browse(p):
    """The day's species row (1 to MAX_SPECIMENS specimens) of the place's list and its browse page, or None."""
    key = repr(p)
    if key not in _SPECIES:
        _SPECIES[key] = None
        html = ns.fetch(p.list_url("species"))
        rows = []
        for r in ns.list_rows(html or ""):
            count, link, images, _params = list_row_cells(r["html"])
            if count and count <= MAX_SPECIMENS and images and link:
                rows.append((r, link))
        got = ns.pick(rows, 1, "images-species" + key)
        if got:
            path = re.sub(r"^https?://[^/]+", "", got[0][1])
            page = ns.fetch(path + ("&" if "?" in path else "?") + "statusSet=all")
            if page:
                _SPECIES[key] = (got[0][0], page)
    return _SPECIES[key]


SPECIMEN_IMAGES = (r"(No Images|Label only(?: \([\d,]+ labels\))?|<span class='numbers'>[\d,]+</span> Images"
                   r"(?: \+ (?:label|[\d,]+ labels))?|<span class='numbers'>[\d,]+</span> image files?)")


def specimen_images(text):
    """A specimen's images as the pages show them since 2026-10-27 (ImageCounts.text; Brian Fisher: "5 images +
    label"): (images of the ant, label images). 'No Images' -> (0, 0), 'Label only' -> (0, 1), 'Label only (2
    labels)' -> (0, 2), '5 Images' -> (5, 0), '5 Images + label' -> (5, 1), '5 Images + 2 labels' -> (5, 2); None
    when the labels are not known ('N image files') or the text is none of these."""
    t = ns.text_of(text)
    if t == "No Images":
        return 0, 0
    m = re.fullmatch(r"Label only(?: \(([\d,]+) labels\))?", t)
    if m:
        return 0, ns.n(m.group(1)) if m.group(1) else 1
    m = re.fullmatch(r"([\d,]+) Images(?: \+ (label|([\d,]+) labels))?", t)
    if not m:
        return None
    return ns.n(m.group(1)), (0 if not m.group(2) else 1 if m.group(2) == "label" else ns.n(m.group(3)))


def specimen_rows(html):
    """(code, (images of the ant, label images) shown, no-photos mark) of each specimen of a browse page
    (specimenReport.jsp)."""
    out = []
    for r in ns.list_rows(html):
        code = re.search(r'<div class="sd_specimen_code">.*?specimen\.do\?code=([^"&]+)"', r["html"], re.S)
        cnt = re.search(r'<div class="sd_specimen_code">.*?' + SPECIMEN_IMAGES, r["html"], re.S)
        if code and cnt:
            out.append((requests.utils.unquote(code.group(1)).lower(), specimen_images(cnt.group(1)), r["no_photos"]))
    return out


def api_image_count(code):
    """The specimen's images in the image table (API /images, live), or None."""
    body = ns.api("images?specimenCode=" + ns.quote_plus(code))
    if body is None:
        return None
    return int((body.get("metaData") or {}).get("count") or 0)


def api_image_counts(code):
    """(images of the ant, label images) of the specimen in the image table (API /images shot types, live), or None."""
    body = ns.api("images?specimenCode=" + ns.quote_plus(code))
    if body is None:
        return None
    shots = [(i.get("shotType") or "").strip().lower() for sp in ns.api_rows(body) for i in (sp.get("images") or [])]
    return sum(1 for s in shots if s != "l"), sum(1 for s in shots if s == "l")


def test_specimen_rows_count_the_images_of_each_specimen():
    """Row 61: browse.do of the day's species of every kind of place: each specimen's "N Images + label" (since
    2026-10-27: images of the ant, label images apart) = its images in the image table (API /images shot types, at
    most 8 specimens a place); "No Images" exactly where the row has the no-photos mark."""
    wrong, checked = [], 0
    for p in places():
        got = species_browse(p)
        if not got:
            continue
        row, html = got
        rows = specimen_rows(html)
        for code, shown, no_photos in rows:
            if shown is None:
                wrong.append(f"{p} {code}: images not shown as images of the ant + labels")
            elif (shown == (0, 0)) != no_photos:
                wrong.append(f"{p} {code}: {shown} (images, labels), no-photos mark {no_photos}")
        rows = [r for r in rows if r[1] is not None]
        for code, shown, _np in ns.pick([r for r in rows if r[1] != (0, 0)], 6, "images-specimens" + repr(p)) + ns.pick([r for r in rows if r[1] == (0, 0)], 2, "images-none" + repr(p)):
            live = api_image_counts(code)
            if live is None:
                continue
            checked += 1
            if live != shown:
                wrong.append(f"{p} {code}: browse.do {shown} (images of the ant, labels), the image table {live}")
    if not checked:
        pytest.skip("no species browse page with specimens read " + ns.day_note())
    assert not wrong, f"{checked} specimens {ns.day_note()}:\n" + "\n".join(wrong)


def test_specimen_images_page_counts_the_images_of_the_specimen():
    """Row 64: specimenImages.do "N Images" = the specimen's images in the image table (API /images, live), for up to 3
    specimens with images of the day's species of every kind of place."""
    wrong, checked = [], 0
    for p in places():
        got = species_browse(p)
        if not got:
            continue
        rows = [r for r in specimen_rows(got[1]) if r[1] and r[1] != (0, 0)]
        for code, _shown, _np in ns.pick(rows, 3, "specimen-images" + repr(p)):
            html = ns.fetch("/specimenImages.do?code=" + ns.quote_plus(code))
            live = api_image_counts(code)
            if html is None or live is None:
                continue
            m = re.search(r'<h2 class="display_count" data-images="(\d+)" data-labels="(\d+)">(.*?)</h2>', html, re.S)
            if not m:
                wrong.append(f"{code}: specimenImages.do without 'N Images + label'")
                continue
            checked += 1
            shown = (int(m.group(1)), int(m.group(2)))
            if shown != live or specimen_images(m.group(3)) != shown:
                wrong.append(f"{p} {code}: specimenImages.do {ns.text_of(m.group(3))!r} {shown}, the image table {live}")
    if not checked:
        pytest.skip("no imaged specimen found on the day's species pages " + ns.day_note())
    assert not wrong, f"{checked} specimens {ns.day_note()}:\n" + "\n".join(wrong)


# ------------------------------------------------------------------------------------------- row 28

def _api_imaged_specimens(museum):
    """The museum's ant specimens with any image (API hasImage=true), or None when more than MAX_IMAGED or unreadable."""
    rows = ns.api_all(f"specimens?museum={ns.quote_plus(museum)}&family=formicidae&hasImage=true",
                      max_pages=max(1, MAX_IMAGED // 500 + 1))
    if rows is None or len(rows) > MAX_IMAGED:
        return None
    return rows


def test_label_only_specimens_are_the_specimens_with_only_label_images():
    """Row 28: museum page "(N additional specimens)" (data-label-only) + Imaged specimens (data-imaged) = the museum's
    ant specimens with any image (API, specimen.image_count > 0); Images (data-images) = their image count. The day's
    museum with 1 to MAX_IMAGED specimens with images (up to 8 museums tried)."""
    museums = ns.museums()
    if not museums:
        pytest.skip("museums.do lists no museum")
    tried = []
    for p in ns.pick(museums, 8, "label-only"):
        html = ns.fetch(p.page)
        m = re.search(r'<div id="place-stats" data-images="(\d+)" data-ant="(\d+)" data-labels="(\d+)" data-imaged="(\d+)" data-label-only="(\d+)"', html or "")
        if not m:
            tried.append(f"{p}: no image numbers")
            continue
        images, imaged, label_only = int(m.group(1)), int(m.group(4)), int(m.group(5))
        if not 0 < imaged + label_only <= MAX_IMAGED:
            tried.append(f"{p}: {imaged + label_only} specimens with images")
            continue
        rows = _api_imaged_specimens(p.name)
        if rows is None:
            tried.append(f"{p}: API not read")
            continue
        shown = re.search(r'<span data-label-only-shown="(\d+)">', html)
        wrong = []
        if label_only > 0 and (not shown or int(shown.group(1)) != label_only):
            wrong.append(f"the page says {shown and shown.group(1)} additional specimens, data-label-only {label_only}")
        if imaged + label_only != len(rows):
            wrong.append(f"Imaged specimens {imaged} + label only {label_only}, the API {len(rows)} ant specimens with images")
        total = sum(int(r.get("imageCount") or 0) for r in rows)
        if total != images:
            wrong.append(f"Images {images}, the API's specimens have {total}")
        assert not wrong, f"{p} {ns.day_note()} ({PENDING}):\n" + "\n".join(wrong)
        return
    pytest.skip("no museum with 1 to MAX_IMAGED specimens with images in the day's sample: " + "; ".join(tried) + " " + ns.day_note())


# ------------------------------------------------------------------------------------------- row 71

def _api_images_since(days, max_pages=40):
    """Images uploaded in the last `days` days (API /images?since, all pages), or None."""
    total, offset = 0, 0
    for _ in range(max_pages):
        body = ns.api(f"images?since={days}&offset={offset}")
        if body is None:
            return None
        meta = body.get("metaData") or {}
        count, limit = int(meta.get("count") or 0), int(meta.get("limit") or 0)
        total += count
        if not limit or count < limit:
            return total
        offset += limit
    return None


def test_recent_images_count_equals_the_images_uploaded():
    """Row 71: "N Images" (or "Showing records a to b of N records") of the images uploaded in the last 30 days = the
    images the API counts for that time (between since=29 and since=31: the servers may count days in different time
    zones; until B47 was fixed, images of two specimens of one name with the same shot, number and upload second were
    one row); the first page lists the rows its header says (a to b, recentSearchResults-body.jsp: pages of the
    `length` parameter, 50 by default; numToShow is not the page size)."""
    s = new_session()
    r = s.get(BASE_URL + "/recentSearchResults.do?searchMethod=recentImageSearch&daysAgo=30", timeout=ns.SLOW)
    if classify(r).category != "ok":
        pytest.skip("recentSearchResults.do: " + classify(r).category)
    html = r.text
    # since 2026-10-27: "N Images + M labels" (images of the ant, label images apart) when one page holds them all;
    # the number of image rows is images + labels
    both = re.search(r'<h2 data-images="(\d+)" data-labels="(\d+)">', html)
    paged = re.search(r'<h2>Showing records <span class="numbers">(\d+)</span> to <span class="numbers">(\d+)</span> '
                      r'of <span class="numbers">(\d+)</span> records</h2>', html)
    assert both or paged, "recentSearchResults.do: no 'N Images + labels' or 'Showing records a to b of N records'"
    rows = len(re.findall(r'<div class="adv_search_recent_image">', html))
    if both:
        n = int(both.group(1)) + int(both.group(2))
        assert rows == n, f"recent images: {n}, {rows} rows listed"
    else:
        a, b, n = (int(x) for x in paged.groups())
        assert (a, b) == (1, min(n, 50)), f"recent images: first page shows records {a} to {b} of {n} (pages of 50)"
        assert rows == b - a + 1, f"recent images: records {a} to {b} of {n}, {rows} rows on the first page"
    low, high = _api_images_since(29), _api_images_since(31)
    if low is None or high is None:
        pytest.skip("API /images?since not read")
    assert low <= n <= high, f"recent images (30 days): {n}; the API: {low} uploaded in 29 days, {high} in 31"
