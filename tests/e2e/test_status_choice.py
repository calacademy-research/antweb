"""The status choice of lists, Brian Fisher 2026-10-30 (docs/numbers.md "Status choice of lists", StatusSet).

The selector on list pages (taxonomicPage.do, browse.do, images.do) offers four plain choices, each the rows of a number
of the place page:

  valid        Valid species                  = Valid species / subspecies  ("N Valid species (of M species and
                                                morphospecies)", M = Species / subspecies / morphospecies)
  validmorpho  Valid species + morphospecies  = Species / subspecies / morphospecies ("N Species (valid species and
                                                morphospecies)")
  all          All names                      every counted status ("N Species (all names)")
  fossil       Fossil species                 valid fossil names ("N Fossil species")

Defaults (no statusSet in the address): country, adm1, region, subregion and curated projects Valid species; bioregions
and museums Valid species + morphospecies. Old values still answer: all determined -> validmorpho, all indetermined /
complete / type -> all, valid extant -> valid, valid fossil -> fossil. A choice is kept in the page's own links (the
Overview, Taxa and Images tabs, the selector) and never in the session: a page opened without statusSet has its default.

For the day's place of every kind (numbers_sample): the default of its species list (the selector's data-status-set),
each choice's header against the place page's numbers and the rows listed, the tab links; on the day's country: the old
values; the genus list and the browse and images pages of a genus. Read-only, anonymous, staging copy of production.
"""
import re
from html import unescape

import pytest

import numbers_sample as ns

PENDING = "the place numbers are those of the last nightly run; after an upload they are pending"
CHOICES = ("valid", "validmorpho", "all", "fossil")
LABELS = {"valid": "Valid species", "validmorpho": "Valid species + morphospecies", "all": "All names",
          "fossil": "Fossil species"}
GEOLOCALE_KINDS = ("country", "adm1", "region", "subregion")
OLD_VALUES = {"valid extant": "valid", "valid fossil": "fossil", "all determined": "validmorpho",
              "all indetermined": "all", "complete": "all", "type": "all", "Valid (with fossils)": "valid"}
SELECTOR = re.compile(r'<div id="change_status" class="has_options" data-status-set="([^"]*)">\s*'
                      r'<span id="which_status"><span>([^<]*)</span>')

_PLACES = {}


def places():
    if "all" not in _PLACES:
        _PLACES["all"] = ns.one_place_of_each_kind("status-choice")
    if not _PLACES["all"]:
        pytest.skip("no place found (API /geolocales, /bioregions, museums.do, projects.do) " + ns.day_note())
    return _PLACES["all"]


def default_of(p):
    """The default choice of the place's lists (StatusSet.defaultChoice)."""
    return "valid" if p.kind in GEOLOCALE_KINDS or p.kind == "project" else "validmorpho"


def selector(html):
    """(choice, label) the selector shows, or (None, None) when the page has none."""
    m = SELECTOR.search(html or "")
    return (unescape(m.group(1)), unescape(m.group(2))) if m else (None, None)


_STATS = {}


def stats_of(p):
    key = repr(p)
    if key not in _STATS:
        _STATS[key] = ns.place_stats(ns.fetch(p.page) or "")
    return _STATS[key]


def default_list(p, rank="species"):
    return f"/taxonomicPage.do?rank={rank}&{p.params}"


def test_lists_open_with_the_default_of_their_kind_of_place():
    """Without statusSet a list opens with its place's default: Valid species on geolocales and curated projects,
    Valid species + morphospecies on bioregions and museums; its header equals the list with that choice named."""
    wrong, checked = [], []
    for p in places():
        html = ns.fetch(default_list(p))
        if html is None:
            continue
        choice, label = selector(html)
        if choice != default_of(p) or label != LABELS[default_of(p)]:
            wrong.append(f"{p}: the list without statusSet shows {choice!r} ({label!r}), expected {default_of(p)!r}")
            continue
        explicit = ns.fetch(p.list_url("species", default_of(p)))
        if explicit is not None and ns.header(explicit) != ns.header(html):
            wrong.append(f"{p}: without statusSet {ns.header(html)[1]!r}, with statusSet={default_of(p)} {ns.header(explicit)[1]!r}")
        checked.append(repr(p))
    if not checked:
        pytest.skip("no place list read " + ns.day_note())
    assert not wrong, f"{len(checked)} places {ns.day_note()}:\n" + "\n".join(wrong)


def test_each_choice_header_equals_the_place_page_number():
    """Valid species = Valid species / subspecies (and its "of M" = Species / subspecies / morphospecies), Valid species
    + morphospecies = Species / subspecies / morphospecies; every header = the rows its page lists; the selector shows
    the choice; its words say what the list shows."""
    wrong, stored, checked = [], [], []
    words = {"valid": "Valid species", "validmorpho": "(valid species and morphospecies)", "all": "(all names)",
             "fossil": "Fossil species"}
    for p in places():
        stats = stats_of(p)
        if "species" not in stats:
            continue
        for choice in CHOICES:
            html = ns.fetch(p.list_url("species", choice))
            if html is None:
                continue
            n, text = ns.header(html)
            if n is None:
                wrong.append(f"{p} {choice}: no header ({text!r})")
                continue
            if selector(html)[0] != choice:
                wrong.append(f"{p} {choice}: the selector shows {selector(html)}")
            if n and words[choice] not in text:
                wrong.append(f"{p} {choice}: header {text!r} does not say {words[choice]!r}")
            rows = len(ns.list_rows(html))
            if rows != n:
                wrong.append(f"{p} {choice}: header {text!r}, {rows} rows listed")
            if choice == "valid":
                if n != stats.get("valid", 0):
                    stored.append(f"{p} valid: header {n}, place page Valid species {stats.get('valid', 0)}")
                of = re.search(r"\(of ([\d,]+) species and morphospecies\)", text)
                if n and (not of or ns.n(of.group(1)) != stats["species"]):
                    wrong.append(f"{p} valid: header {text!r}, place page Species {stats['species']}")
            if choice == "validmorpho" and n != stats["species"]:
                stored.append(f"{p} validmorpho: header {n}, place page Species {stats['species']}")
        checked.append(repr(p))
    if not checked:
        pytest.skip("no place page with data-stat numbers " + ns.day_note())
    pending_rules = stored and ns.counting_rules_counted() is False
    if not pending_rules:
        wrong += stored
    assert not wrong, f"{len(checked)} places {ns.day_note()} ({PENDING}):\n" + "\n".join(wrong)
    if pending_rules:
        pytest.skip(ns.rules_pending_note("counting rules of 2026-10-28") + ":\n" + "\n".join(stored))


def test_old_values_still_answer_with_the_nearest_choice():
    """Addresses with the removed choices still answer 200 and show the mapped choice; the removed labels are no longer
    offered."""
    p = ns.madagascar()
    wrong = []
    for old, new in OLD_VALUES.items():
        r = ns.session().get(ns.BASE_URL + p.list_url("species", old), timeout=ns.SLOW)
        if r.status_code != 200:
            wrong.append(f"statusSet={old!r}: HTTP {r.status_code}")
            continue
        if selector(r.text)[0] != new:
            wrong.append(f"statusSet={old!r}: the selector shows {selector(r.text)}, expected {new!r}")
        menu = re.search(r'<div id="status_choice"[^>]*>(.*?)</ul>', r.text, re.S)
        offered = ns.text_of(menu.group(1)).lower() if menu else ""
        for gone in ("all determined", "all indetermined", "complete", "type", "more options"):
            if gone in offered:
                wrong.append(f"statusSet={old!r}: the selector still offers {gone!r} ({offered!r})")
    assert not wrong, "Madagascar species list:\n" + "\n".join(wrong)


def test_choice_is_kept_in_the_page_links_not_in_the_session():
    """The choice is in the page's Taxa and Images tabs and its Overview link; the selector links each choice; the
    same session then opens a list without statusSet: it has the default again."""
    wrong, checked = [], []
    s = ns.new_session()
    for p in places():
        other = "fossil"
        html = ns.fetch(p.list_url("species", other), s)
        if html is None:
            continue
        # the Overview, Taxa and Images tabs of the place (overviewHeading.jsp, <div class="links">)
        block = re.search(r'<div class="links">(.*?)<div id="antcat_view">', html, re.S)
        page = block.group(1) if block else ""
        tabs = re.findall(r'href="([^"]*taxonomicPage\.do\?[^"]*)"', page)
        overview = re.search(r'<li><a href="([^"]*)">Overview</a></li>', page)
        if overview and "statusSet=" + other not in unescape(overview.group(1)):
            wrong.append(f"{p}: Overview link without the choice: {overview.group(1)}")
        if not tabs:
            wrong.append(f"{p}: no Taxa or Images tab links")
        for t in tabs:
            if "statusSet=" + other not in unescape(t):
                wrong.append(f"{p}: tab link without the choice: {t}")
        links = dict(re.findall(r'<a data-status-choice="([^"]+)" href="([^"]+)"', html))
        if sorted(links) != sorted(CHOICES):
            wrong.append(f"{p}: the selector offers {sorted(links)}")
        for c, u in links.items():
            if unescape(u).count("statusSet=") != 1 or "statusSet=" + c not in unescape(u):
                wrong.append(f"{p}: the {c} link is {u}")
        again = ns.fetch(default_list(p), s)
        if again is not None and selector(again)[0] != default_of(p):
            wrong.append(f"{p}: after statusSet={other}, the list without statusSet shows {selector(again)}")
        checked.append(repr(p))
    if not checked:
        pytest.skip("no place list read " + ns.day_note())
    assert not wrong, f"{len(checked)} places {ns.day_note()}:\n" + "\n".join(wrong)


def test_genus_list_choices():
    """Genus lists offer the same choices on the genus rows; the place page's Genera equals the header of Valid genera +
    morphogenera or of All names (the Genera definition is being changed separately, 2026-10-30); every header = the
    rows listed."""
    wrong, stored, checked = [], [], []
    for p in places():
        stats = stats_of(p)
        if "genera" not in stats:
            continue
        headers = {}
        for choice in CHOICES:
            html = ns.fetch(p.list_url("genus", choice))
            if html is None:
                continue
            n, text = ns.header(html)
            if n is None or len(ns.list_rows(html)) != n:
                wrong.append(f"{p} genus {choice}: header {text!r}, {len(ns.list_rows(html))} rows listed")
                continue
            headers[choice] = n
        if "validmorpho" in headers and "all" in headers:
            if stats["genera"] not in (headers["validmorpho"], headers["all"]):
                stored.append(f"{p}: place page Genera {stats['genera']}, genus lists {headers}")
            if not headers.get("valid", 0) <= headers["validmorpho"] <= headers["all"]:
                wrong.append(f"{p}: genus list headers {headers}: expected valid <= validmorpho <= all")
        checked.append(repr(p))
    if not checked:
        pytest.skip("no genus list read " + ns.day_note())
    pending_rules = stored and ns.counting_rules_counted() is False
    if not pending_rules:
        wrong += stored
    assert not wrong, f"{len(checked)} places {ns.day_note()} ({PENDING}):\n" + "\n".join(wrong)
    if pending_rules:
        pytest.skip(ns.rules_pending_note("counting rules of 2026-10-28") + ":\n" + "\n".join(stored))


def test_genus_browse_and_images_pages_follow_the_choice():
    """The day's genus of Madagascar: browse.do and images.do open with the country's default (Valid species), each
    choice changes the list (valid <= validmorpho <= all species), and browse.do's header with validmorpho equals the
    genus row's Species on the list. A species page (its specimens) shows no selector."""
    p = ns.madagascar()
    html = ns.fetch(p.list_url("genus"))
    if html is None:
        pytest.skip("Madagascar's genus list not readable")
    rows = []
    for r in ns.list_rows(html):
        m = re.search(r"<a href='([^']*browse\.do\?[^']*)'><span class='numbers'>(\d+)</span> Species", r["html"])
        if m and 2 <= int(m.group(2)) <= 300:
            rows.append((re.sub(r"^https?://[^/]+", "", unescape(m.group(1))), int(m.group(2))))
    if not rows:
        pytest.skip("no genus of Madagascar with 2 to 300 species")
    link, count = ns.pick(rows, 1, "status-choice-genus")[0]
    wrong = []
    for page in ("browse", "images"):
        url = link.replace("/browse.do?", f"/{page}.do?")
        plain = ns.fetch(url)
        if plain is None:
            continue
        if selector(plain)[0] != "valid":
            wrong.append(f"{url}: opens with {selector(plain)}, expected valid")
        if page == "browse":
            n = {}
            for c in CHOICES:
                b = ns.fetch(url + "&statusSet=" + c)
                if b is None:
                    continue
                n[c] = ns.header(b)[0]
                if selector(b)[0] != c:
                    wrong.append(f"{url} {c}: the selector shows {selector(b)}")
            if {"valid", "validmorpho", "all"} <= set(n) and not n["valid"] <= n["validmorpho"] <= n["all"]:
                wrong.append(f"{url}: headers {n}: expected valid <= validmorpho <= all")
            if n.get("validmorpho") is not None and n["validmorpho"] != count:
                wrong.append(f"{url}: validmorpho header {n['validmorpho']}, the genus row says {count} Species")
            species = re.search(r'href="[^"]*description\.do\?([^"]*rank=species[^"]*)"', ns.fetch(url + "&statusSet=validmorpho") or "")
            if species:
                sp = ns.fetch("/browse.do?" + unescape(species.group(1)))
                if sp is not None and selector(sp)[0] is not None:
                    wrong.append(f"species page /browse.do?{species.group(1)} shows a status selector")
    assert not wrong, f"Madagascar {link} {ns.day_note()}:\n" + "\n".join(wrong)
