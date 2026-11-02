"""The views of lists, Brian Fisher 2026-11-02 (docs/numbers.md "Views of lists", StatusSet, ListView); they replace the
status choice of 2026-10-30 and the "Names from" selector.

List pages (taxonomicPage.do, browse.do, images.do) show four buttons, a segmented control (not a menu), the same on
every kind of list:

  valid   Valid               names AntCat calls valid ("N Valid species (of M species and morphospecies)", N = Valid
                              species / subspecies of the place page)
  vetted  Vetted (default)    Valid + vetted morphospecies ("N Species (valid and vetted)")
  all     All specimen names  Vetted + every other morphospecies and indet name ("N Species (all specimen names)")
  fossil  Fossil              valid fossil names ("N Fossil species")

The header counts the rows of the view, and the views nest (Valid <= Vetted <= All specimen names). Old values still
answer: validmorpho (Valid + morphospecies of 10-30), all determined, all indetermined, complete, type -> all; valid
extant -> valid; valid fossil -> fossil; specimen=true (the old Names from) -> all. A view is kept in the page's own links
(the Overview, Taxa and Images tabs, the buttons) and never in the session: a page opened without statusSet is Vetted.

For the day's place of every kind (numbers_sample): the default, each view's header against the place page's Valid
species and the rows listed, the tab links; on Madagascar: the old values; the genus list and the browse and images
pages of a genus. Read-only, anonymous, staging copy of production.
"""
import re
from html import unescape

import pytest

import numbers_sample as ns

PENDING = "the place numbers are those of the last nightly run; after an upload they are pending"
CHOICES = ("valid", "vetted", "all", "fossil")
LABELS = {"valid": "Valid", "vetted": "Vetted", "all": "All specimen names", "fossil": "Fossil"}
GEOLOCALE_KINDS = ("country", "adm1", "region", "subregion")
OLD_VALUES = {"valid extant": "valid", "valid fossil": "fossil", "all determined": "all", "validmorpho": "all",
              "all indetermined": "all", "complete": "all", "type": "all", "Valid (with fossils)": "valid"}
SWITCH = re.compile(r'<span id="change_status" class="view-buttons" data-status-set="([^"]*)">(.*?)</span>\s*</div>', re.S)
BUTTON = re.compile(r'<a class="view-btn( is-current)?" data-status-choice="([^"]+)"[^>]*href="([^"]+)">([^<]*)</a>')

_PLACES = {}


def places():
    if "all" not in _PLACES:
        _PLACES["all"] = ns.one_place_of_each_kind("status-choice")
    if not _PLACES["all"]:
        pytest.skip("no place found (API /geolocales, /bioregions, museums.do, projects.do) " + ns.day_note())
    return _PLACES["all"]


def default_of(p):
    """The default view of the place's lists (StatusSet.defaultChoice): Vetted everywhere (2026-11-02)."""
    return "vetted"


def buttons(html):
    """The view buttons of a page: [(choice, label, href, current)], [] when the page has none."""
    m = SWITCH.search(html or "")
    if not m:
        return []
    return [(unescape(b[1]), unescape(b[3]), unescape(b[2]), bool(b[0])) for b in BUTTON.findall(m.group(2))]


def selector(html):
    """(view, label) the switch marks as current, or (None, None) when the page has none."""
    m = SWITCH.search(html or "")
    if not m:
        return None, None
    current = [b for b in buttons(html) if b[3]]
    return unescape(m.group(1)), (current[0][1] if len(current) == 1 else None)


_STATS = {}


def stats_of(p):
    key = repr(p)
    if key not in _STATS:
        _STATS[key] = ns.place_stats(ns.fetch(p.page) or "")
    return _STATS[key]


def default_list(p, rank="species"):
    return f"/taxonomicPage.do?rank={rank}&{p.params}"


def test_lists_open_with_the_default_of_their_kind_of_place():
    """Without statusSet a list opens with the default view, Vetted, on every kind of place; its header equals the list
    with that view named. The switch is four buttons (not a menu), in the order Valid, Vetted, All specimen names,
    Fossil, one marked current; the "Names from" selector is gone."""
    wrong, checked = [], []
    for p in places():
        html = ns.fetch(default_list(p))
        if html is None:
            continue
        choice, label = selector(html)
        if choice != default_of(p) or label != LABELS[default_of(p)]:
            wrong.append(f"{p}: the list without statusSet shows {choice!r} ({label!r}), expected {default_of(p)!r}")
            continue
        if [b[0] for b in buttons(html)] != list(CHOICES) or [b[1] for b in buttons(html)] != [LABELS[c] for c in CHOICES]:
            wrong.append(f"{p}: the buttons are {[(b[0], b[1]) for b in buttons(html)]}")
        if 'id="status_choice"' in html or 'id="names_from_toggle"' in html:
            wrong.append(f"{p}: a menu (status or Names from) is still shown")
        explicit = ns.fetch(p.list_url("species", default_of(p)))
        if explicit is not None and ns.header(explicit) != ns.header(html):
            wrong.append(f"{p}: without statusSet {ns.header(html)[1]!r}, with statusSet={default_of(p)} {ns.header(explicit)[1]!r}")
        checked.append(repr(p))
    if not checked:
        pytest.skip("no place list read " + ns.day_note())
    assert not wrong, f"{len(checked)} places {ns.day_note()}:\n" + "\n".join(wrong)


def test_each_choice_header_equals_the_place_page_number():
    """Valid = Valid species / subspecies (and its "of M" = Species / subspecies / morphospecies); the views nest
    (Valid <= Vetted <= All specimen names); every header = the rows its page lists; the switch marks the view; its
    words say what the list shows."""
    wrong, stored, checked = [], [], []
    words = {"valid": "Valid species", "vetted": "(valid and vetted)", "all": "(all specimen names)",
             "fossil": "Fossil species"}
    for p in places():
        stats = stats_of(p)
        if "species" not in stats:
            continue
        headers = {}
        for choice in CHOICES:
            html = ns.fetch(p.list_url("species", choice))
            if html is None:
                continue
            n, text = ns.header(html)
            if n is None:
                wrong.append(f"{p} {choice}: no header ({text!r})")
                continue
            headers[choice] = n
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
        if {"valid", "vetted", "all"} <= set(headers) and not headers["valid"] <= headers["vetted"] <= headers["all"]:
            wrong.append(f"{p}: species headers {headers}: expected valid <= vetted <= all")
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
    """Addresses with the removed choices (and specimen=true, the old Names from) still answer 200 and show the mapped
    view; only the four views are offered."""
    p = ns.madagascar()
    wrong = []
    for old, new in OLD_VALUES.items():
        r = ns.session().get(ns.BASE_URL + p.list_url("species", old), timeout=ns.SLOW)
        if r.status_code != 200:
            wrong.append(f"statusSet={old!r}: HTTP {r.status_code}")
            continue
        if selector(r.text)[0] != new:
            wrong.append(f"statusSet={old!r}: the selector shows {selector(r.text)}, expected {new!r}")
        offered = [b[0] for b in buttons(r.text)]
        if offered != list(CHOICES):
            wrong.append(f"statusSet={old!r}: the switch offers {offered}")
    r = ns.session().get(ns.BASE_URL + f"/taxonomicPage.do?rank=species&{p.params}&specimen=true", timeout=ns.SLOW)
    if r.status_code != 200 or selector(r.text)[0] != "all":
        wrong.append(f"specimen=true: HTTP {r.status_code}, the switch shows {selector(r.text)}, expected 'all'")
    assert not wrong, "Madagascar species list:\n" + "\n".join(wrong)


def test_choice_is_kept_in_the_page_links_not_in_the_session():
    """The view is in the page's Taxa and Images tabs and its Overview link; each button links its view; the same
    session then opens a list without statusSet: it has the default again."""
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
        links = {b[0]: b[2] for b in buttons(html)}
        if sorted(links) != sorted(CHOICES):
            wrong.append(f"{p}: the switch offers {sorted(links)}")
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
    """Genus lists offer the same views on the genus rows; the views nest (Valid <= Vetted <= All specimen names: a genus
    known here only from specimens identified to genus is in All specimen names only, ListView.keepGenus); Vetted lists
    no more genera than the place page's Genera counts (valid genera and morphogenera of the list; Vetted leaves out
    placeholders such as "(myrmicinae)", genera known only from "(indet)" specimens and morphogenera with no vetted
    morphospecies); every header = the rows listed. The place's Genera is stored by the nightly run: pending until a run
    has counted by the genera and subfamilies rule of 2026-10-30."""
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
        if "vetted" in headers and "all" in headers:
            if headers["vetted"] > stats["genera"]:
                stored.append(f"{p}: place page Genera {stats['genera']}, Vetted list {headers['vetted']} (lists {headers})")
            if not headers.get("valid", 0) <= headers["vetted"] <= headers["all"]:
                wrong.append(f"{p}: genus list headers {headers}: expected valid <= vetted <= all")
        checked.append(repr(p))
    if not checked:
        pytest.skip("no genus list read " + ns.day_note())
    pending_rules = stored and (ns.counting_rules_counted() is False or ns.genera_rule_counted() is False)
    if not pending_rules:
        wrong += stored
    assert not wrong, f"{len(checked)} places {ns.day_note()} ({PENDING}):\n" + "\n".join(wrong)
    if pending_rules:
        pytest.skip(ns.rules_pending_note("counting rules of 2026-10-28 and the genera and subfamilies rule of 2026-10-30") + ":\n" + "\n".join(stored))


def test_genus_browse_and_images_pages_follow_the_choice():
    """The day's genus of Madagascar: browse.do and images.do open with the default (Vetted), each view changes the
    list (valid <= vetted <= all species), and browse.do's header in Vetted equals the genus row's Species on the
    Vetted list (its link keeps the view). A species page (its specimens) shows no switch."""
    p = ns.madagascar()
    html = ns.fetch(p.list_url("genus"))
    if html is None:
        pytest.skip("Madagascar's genus list not readable")
    rows = []
    for r in ns.list_rows(html):
        m = re.search(r"<a href='([^']*browse\.do\?[^']*)'><span class='numbers'>(\d+)</span>(?: Species)?</a>", r["html"])
        if m and 2 <= int(m.group(2)) <= 300:
            rows.append((re.sub(r"&statusSet=[^&]*", "", re.sub(r"^https?://[^/]+", "", unescape(m.group(1)))), int(m.group(2))))
    if not rows:
        pytest.skip("no genus of Madagascar with 2 to 300 species")
    link, count = ns.pick(rows, 1, "status-choice-genus")[0]
    wrong = []
    for page in ("browse", "images"):
        url = link.replace("/browse.do?", f"/{page}.do?")
        plain = ns.fetch(url)
        if plain is None:
            continue
        if selector(plain)[0] != "vetted":
            wrong.append(f"{url}: opens with {selector(plain)}, expected vetted")
        if page == "browse":
            n = {}
            for c in CHOICES:
                b = ns.fetch(url + "&statusSet=" + c)
                if b is None:
                    continue
                n[c] = ns.header(b)[0]
                if selector(b)[0] != c:
                    wrong.append(f"{url} {c}: the selector shows {selector(b)}")
            if {"valid", "vetted", "all"} <= set(n) and not n["valid"] <= n["vetted"] <= n["all"]:
                wrong.append(f"{url}: headers {n}: expected valid <= vetted <= all")
            if n.get("vetted") is not None and n["vetted"] != count:
                wrong.append(f"{url}: Vetted header {n['vetted']}, the genus row says {count} Species")
            species = re.search(r'href="[^"]*description\.do\?([^"]*rank=species[^"]*)"', ns.fetch(url + "&statusSet=vetted") or "")
            if species:
                sp = ns.fetch("/browse.do?" + unescape(species.group(1)))
                if sp is not None and selector(sp)[0] is not None:
                    wrong.append(f"species page /browse.do?{species.group(1)} shows a status selector")
    assert not wrong, f"Madagascar {link} {ns.day_note()}:\n" + "\n".join(wrong)
