"""Endemic and Introduced: the place numbers, endemic.do and introduced.do (docs/numbers.md rows 24, 25, 78, 79),
2026-10-26.

What must agree, and how it is checked here (read-only, anonymous; staging or production):

  rows 24, 25  the Endemic and Introduced numbers of a place page (data-stat endemic, introduced) = the names
               numbered on its endemic.do and introduced.do (both read the flags of the place's list:
               PlaceTotals.countsFromFlagsSql, EndemicAction, IntroducedAction). Every bioregion on every run;
               Madagascar; the day's countries and states (adm1) among those with an Endemic or Introduced number.
  rows 78, 79  the names of endemic.do and introduced.do, recounted two other ways:
               (a) the marks on the place's species list (taxonomicPage.do): a name marked Endemic is on endemic.do,
                   and a name on endemic.do is marked Endemic or Introduced (the Introduced mark wins in
                   common/distStatus.jsp; bioregion lists show no Endemic mark); the Introduced mark is computed while the page is shown, from the name's
                   native bioregions (TaxonPropMgr), so introduced.do = the names marked Introduced that are on the
                   introduced-species list (project introducedants), as nightly calcIntroduced flags them;
               (b) the endemism rule, name by name, from the API (independent of the nightly flags): a name on
                   endemic.do is a valid species or subspecies, not fossil, an ant, identified to genus (/taxa;
                   PlaceTotals.endemicEligible: Brian Fisher, 2026-10-22 and 2026-10-28, valid names only, at every
                   kind of place); of a country it is on exactly one country's list (/geolocaleTaxa;
                   GeolocaleDb.COUNTRY_ENDEMIC_SQL); of an adm1: endemic to its country and on one adm1 list of that
                   country (ADM1_ENDEMIC_SQL); of a bioregion: on exactly one bioregion's list (BioregionDb.ENDEMIC_SQL).
                   And the other way: a valid name on the list that the rule says is endemic is on endemic.do.
                   Endemism counts native rows only (Q4: rows flagged introduced do not make a name non-endemic); the
                   API does not show the introduced flags, so names on the introduced-species list are left out of
                   the list half of (b).
               Countries, adm1s, subregions and regions: the count check also recounts every flag every day
               (inv:place.endemic.flags); bioregions only here.

The sample changes every day (numbers_sample.py; ANTWEB_SAMPLE_DAY repeats a day). A flag set by the last nightly
run can be out of date after an upload until the next one; the failure message says so.

Found while writing these tests (xfail until fixed, see docs/numbers.md section 3):
  B41  a country or adm1 without a bioregion gets every name of the introduced-species list on its list flagged
       introduced (TaxonPropMgr.isIntroduced(name, null) is true for every name with a native-bioregion map), while
       its page says "not native to this place's bioregion" and its species list marks none of them.
"""
import os
import re

import pytest

import numbers_sample as ns

PER_KIND = int(os.environ.get("ANTWEB_SAMPLE_PLACES", "3"))
NAMES = int(os.environ.get("ANTWEB_SAMPLE_NAMES", "6"))
ENDEMIC_STATUSES = {"valid"}   # what can be endemic (Brian Fisher, 2026-10-28; PlaceTotals.ENDEMIC_STATUSES)
PENDING = "a flag set by the last nightly run is out of date after an upload until the next nightly run"


def _with_numbers(rank, salt):
    rows = [r for r in ns.geolocales(rank) if (r.get("endemicSpeciesCount") or 0) + (r.get("introducedSpeciesCount") or 0) > 0]
    return [ns.geolocale_place(r) for r in ns.pick(rows, PER_KIND, salt)]


def _sample_places():
    places = [ns.madagascar()] + _with_numbers("country", "endemic") + _with_numbers("adm1", "endemic") + ns.bioregions()
    seen, out = set(), []
    for p in places:
        if p and (p.kind, p.name, p.parent) not in seen:
            seen.add((p.kind, p.name, p.parent))
            out.append(p)
    return out


_PAGES = {}


def _place(p):
    """(place numbers, endemic.do names, introduced.do names) of a place, or None when its page shows no Endemic."""
    key = (p.kind, p.name, p.parent)
    if key not in _PAGES:
        html = ns.fetch(p.page)
        stats = ns.place_stats(html or "")
        if "endemic" not in stats or "introduced" not in stats:
            _PAGES[key] = None
        else:
            if p.gid is None and p.kind != "bioregion":
                m = re.search(r"endemic\.do\?geolocaleId=(\d+)|introduced\.do\?geolocaleId=(\d+)", html)
                p.gid = int(m.group(1) or m.group(2)) if m else None
            endemic = ns.fetch(p.endemic_url("endemic")) if p.gid is not None or p.kind == "bioregion" else None
            introduced = ns.fetch(p.endemic_url("introduced")) if p.gid is not None or p.kind == "bioregion" else None
            _PAGES[key] = (stats, None if endemic is None else ns.numbered_names(endemic),
                           None if introduced is None else ns.numbered_names(introduced))
    return _PAGES[key]


def test_endemic_and_introduced_numbers_equal_their_lists():
    """Rows 24, 25 (and the row counts of 78, 79): on every bioregion, Madagascar and the day's countries and states,
    the page's Endemic number = the names numbered on endemic.do, Introduced = the names on introduced.do; for
    geolocales also = the API's endemicSpeciesCount, introducedSpeciesCount (the same stored numbers, row 110)."""
    places = _sample_places()
    wrong, checked = [], []
    for p in places:
        got = _place(p)
        if got is None:
            continue
        stats, endemic, introduced = got
        for label, names in (("endemic", endemic), ("introduced", introduced)):
            if names is None:
                if stats[label]:
                    wrong.append(f"{p}: page {label} {stats[label]}, {label}.do could not be read")
                continue
            if len(names) != stats[label]:
                wrong.append(f"{p}: page {label.capitalize()} {stats[label]}, {label}.do lists {len(names)} names")
            if len(set(names)) != len(names):
                wrong.append(f"{p}: {label}.do lists a name twice")
        if p.api_row is not None:
            for label, key in (("endemic", "endemicSpeciesCount"), ("introduced", "introducedSpeciesCount")):
                if p.api_row.get(key) is not None and int(p.api_row[key]) != stats[label]:
                    wrong.append(f"{p}: page {label} {stats[label]}, API /geolocales {key} {p.api_row[key]}")
        checked.append(repr(p))
    if not checked:
        pytest.skip("no place page with Endemic and Introduced numbers found " + ns.day_note())
    assert not wrong, f"{len(wrong)} differences in {len(checked)} places {ns.day_note()} ({PENDING}):\n" + "\n".join(wrong)


_INTRODUCED_LIST = {}


def introducedants():
    if "names" not in _INTRODUCED_LIST:
        html = ns.fetch("/taxonomicPage.do?rank=species&project=introducedants&statusSet=all")
        _INTRODUCED_LIST["names"] = None if html is None else {r["name"] for r in ns.list_rows(html) if r["name"]}
    return _INTRODUCED_LIST["names"]


def _list_places():
    """Madagascar, the day's country and adm1 with endemic names, and the day's bioregion."""
    out = [ns.madagascar()]
    for rank in ("country", "adm1"):
        rows = [r for r in ns.geolocales(rank) if (r.get("endemicSpeciesCount") or 0) > 0 and r.get("geolocaleName") != "Madagascar"]
        out += [ns.geolocale_place(r) for r in ns.pick(rows, 1, "list" + rank)]
    out += ns.pick(ns.bioregions(), 1, "list-bioregion")
    return [p for p in out if p]


def test_endemic_and_introduced_marks_on_the_species_list_agree():
    """Rows 78, 79: endemic.do = the names marked Endemic on the place's species list (plus endemic names marked
    Introduced, the mark that wins); introduced.do = the names marked Introduced (computed live from the names' native
    bioregions) that are on the introduced-species list. Madagascar, the day's country and adm1 with endemic names, the
    day's bioregion (Introduced only: bioregion lists show no Endemic mark). A place without a bioregion is left out of
    the Introduced half (B41, its own test)."""
    intro_list = introducedants()
    wrong, checked = [], []
    for p in _list_places():
        got = _place(p)
        if got is None or got[1] is None or got[2] is None:
            continue
        stats, endemic, introduced = got
        html = ns.fetch(p.list_url("species"))
        if html is None:
            continue
        rows = [r for r in ns.list_rows(html) if r["name"]]
        names = {r["name"] for r in rows}
        e_marked = {r["name"] for r in rows if r["endemic"]}
        i_marked = {r["name"] for r in rows if r["introduced"]}
        endemic, introduced = set(endemic), set(introduced)
        # Endemic names are valid names (Brian Fisher, 2026-10-28), and the species list shows every valid name: every
        # name on endemic.do is on the list.
        if p.kind == "bioregion":
            pass   # bioregion lists show no Endemic mark (BioregionTaxon does not read is_endemic); the rule test checks them
        elif e_marked - endemic:
            wrong.append(f"{p}: marked Endemic on the list but not on endemic.do: {sorted(e_marked - endemic)[:5]}")
        elif endemic - e_marked - i_marked:
            wrong.append(f"{p}: on endemic.do but not marked on the list: {sorted(endemic - e_marked - i_marked)[:5]}")
        if endemic - names:
            wrong.append(f"{p}: on endemic.do but not on the species list: {sorted(endemic - names)[:5]}")
        if p.kind != "bioregion" and not p.bioregion:
            pass   # B41: see test_a_place_without_a_bioregion_has_no_introduced_species
        elif intro_list is not None:
            expected = i_marked & intro_list
            if introduced != expected:
                wrong.append(f"{p}: introduced.do {len(introduced)} names, marked Introduced and on the introduced-species"
                             f" list {len(expected)}; only on introduced.do {sorted(introduced - expected)[:5]},"
                             f" only marked {sorted(expected - introduced)[:5]}")
        checked.append(repr(p))
    if not checked:
        pytest.skip("no place with a readable species list, endemic.do and introduced.do " + ns.day_note())
    assert not wrong, f"{len(checked)} places {ns.day_note()} ({PENDING}):\n" + "\n".join(wrong)


# ------------------------------------------------------------------------------------------- the rule, by name

_TAXA = {}


def taxon(name):
    if name not in _TAXA:
        rows = ns.api_rows(ns.api("taxa?taxonName=" + ns.quote_plus(name)))
        _TAXA[name] = next((r for r in rows if (r.get("taxonName") or "").lower() == name), None)
    return _TAXA[name]


def _identified_to_genus(genus):
    g = (genus or "").strip().lower()
    return not g.startswith("(") and g not in ("indet", "undet")


def _eligible(t, kind):
    """The taxon conditions of the endemism rule (PlaceTotals.endemicEligible, every kind of place): species or
    subspecies, not fossil, an ant, identified to genus, status valid (Brian Fisher, 2026-10-22 and 2026-10-28)."""
    status = (t.get("status") or "").lower()
    return ((t.get("taxarank") or "").lower() in ("species", "subspecies") and not t.get("fossil")
            and (t.get("family") or "").lower() == "formicidae" and _identified_to_genus(t.get("genus")) and status in ENDEMIC_STATUSES)


def _geo_lists(name, georank):
    """The geolocale rows of that rank whose list has the name (API /geolocaleTaxa; any validity, as the rule), or None."""
    body = ns.api(f"geolocaleTaxa?taxonName={ns.quote_plus(name)}&georank={georank}")
    return None if body is None else ns.api_rows(body)


def _endemic_by_rule(name, p):
    """Whether the rule says the (eligible) name is endemic to place p: True, False, or None when the API cannot say.
    Country: on exactly one country's list. Adm1: endemic to its country and on one adm1 list of that country.
    Bioregion: on exactly one bioregion's list. Only native rows count (Q4), and the API does not show the introduced
    flags: for a name on the introduced-species list the API cannot say."""
    intro = introducedants()
    if intro is None or name in intro:
        return None
    if p.kind == "bioregion":
        body = ns.api(f"bioregionTaxa?taxonName={ns.quote_plus(name)}")
        if body is None:
            return None
        return {r.get("bioregionName") for r in ns.api_rows(body)} == {p.name}
    countries = _geo_lists(name, "country")
    if countries is None:
        return None
    if p.kind == "country":
        return {r.get("geolocaleId") for r in countries} == {p.gid}
    if {r.get("geolocaleName") for r in countries} != {p.parent} or len(countries) != 1:
        return False
    adm1s = _geo_lists(name, "adm1")
    if adm1s is None:
        return None
    parents = {r.get("geolocaleId"): r.get("parent") for r in ns.geolocales("adm1")}
    if any(r.get("geolocaleId") not in parents for r in adm1s):
        return None   # an adm1 the API does not list (not valid): its country is unknown here, and the rule counts it
    return {r.get("geolocaleId") for r in adm1s if parents.get(r.get("geolocaleId")) == p.parent} == {p.gid}


def _own(p):
    return p.name if p.kind == "bioregion" else p.gid


def _valid_names_on(p):
    """Up to 750 valid species and subspecies on the place's list, from the API (a sample: the API has no order).
    (The API's geolocaleId filter of /geolocaleTaxa does not work, so countries and adm1s are asked by name and the
    rows of other places of that name dropped.)"""
    if p.kind == "bioregion":
        rows = ns.api_rows(ns.api(f"bioregionTaxa?bioregionName={ns.quote_plus(p.name)}&taxarank=species&status=valid&limit=750"))
    else:
        rows = ns.api_rows(ns.api(f"geolocaleTaxa?{p.kind}={ns.quote_plus(p.name)}&taxarank=species&status=valid&limit=750"))
        rows = [r for r in rows if r.get("geolocaleId") == p.gid]
    return sorted({(r.get("taxonName") or "").lower() for r in rows} - {""})


def _rule_places():
    out = [ns.madagascar()]
    rows = [r for r in ns.geolocales("country") if (r.get("endemicSpeciesCount") or 0) > 0 and r.get("geolocaleName") != "Madagascar"]
    out += [ns.geolocale_place(r) for r in ns.pick(rows, 1, "rule-country")]
    rows = [r for r in ns.geolocales("adm1") if (r.get("endemicSpeciesCount") or 0) > 0]
    out += [ns.geolocale_place(r) for r in ns.pick(rows, 1, "rule-adm1")]
    out += ns.pick(ns.bioregions(), 1, "rule-bioregion")
    return [p for p in out if p]


def test_endemic_names_follow_the_endemism_rule():
    """Rows 78 and 24, name by name: for Madagascar, the day's country and adm1 with endemic names and the day's
    bioregion, a rotating sample of the names on endemic.do is on exactly one list of its kind (API /geolocaleTaxa,
    /bioregionTaxa) and meets the taxon conditions (API /taxa); and a sample of valid names on the place's list that
    are not on endemic.do does not meet the rule (on two or more lists, or not eligible)."""
    wrong, checked = [], 0
    for p in _rule_places():
        got = _place(p)
        if got is None or got[1] is None or _own(p) is None:
            continue
        endemic = got[1]
        for name in ns.pick(endemic, NAMES, "flagged" + p.name):
            t = taxon(name)
            if t is None:
                continue
            checked += 1
            if not _eligible(t, p.kind):
                wrong.append(f"{p}: {name} is on endemic.do but is not eligible (status {t.get('status')}, rank"
                             f" {t.get('taxarank')}, fossil {t.get('fossil')}, family {t.get('family')}, genus {t.get('genus')})")
            elif _endemic_by_rule(name, p) is False:
                wrong.append(f"{p}: {name} is on endemic.do but the API's lists say it is not endemic here")
        flagged = set(endemic)
        for name in ns.pick([x for x in _valid_names_on(p) if x not in flagged], NAMES, "unflagged" + p.name):
            t = taxon(name)
            if t is None or not _eligible(t, p.kind):
                continue
            rule = _endemic_by_rule(name, p)
            if rule is None:
                continue
            checked += 1
            if rule:
                wrong.append(f"{p}: {name} is eligible and endemic here by the API's lists, but not on endemic.do")
    if not checked:
        pytest.skip("no endemic name could be checked through the API " + ns.day_note())
    assert not wrong, f"{checked} names checked {ns.day_note()} ({PENDING}):\n" + "\n".join(wrong)


@pytest.mark.xfail(reason="B41: a place without a bioregion gets every introduced-species-list name with a native-bioregion"
                          " map flagged introduced (TaxonPropMgr.isIntroduced(name, null) is true)", strict=False)
def test_a_place_without_a_bioregion_has_no_introduced_species():
    """Row 25 (B41): Introduced means "not native to this place's bioregion". A country or adm1 without a bioregion
    cannot have such names; the nightly flags give it every name of the introduced-species list that is on its list and
    has a native-bioregion map, and its species list marks none of them Introduced."""
    rows = [r for rank in ("country", "adm1") for r in ns.geolocales(rank)
            if not (r.get("bioregion") or "").strip() and (r.get("introducedSpeciesCount") or 0) > 0]
    if not rows:
        if not ns.geolocales("country"):
            pytest.skip("API /geolocales not readable")
        return
    shown = ", ".join(f"{r.get('geolocaleName')} ({r.get('georank')}, {r.get('introducedSpeciesCount')})" for r in rows[:10])
    assert not rows, f"{len(rows)} places without a bioregion have Introduced species: {shown}"


def test_endemic_names_of_countries_are_named_species():
    """Rows 24, 78 (Q3): the place pages define Endemic as "Valid species and subspecies"; Brian Fisher (2026-10-22 and
    2026-10-28): morphospecies, indetermined, unidentifiable and unrecognized names never count toward endemism, at any
    kind of place. A sample of endemic.do names of Madagascar, the day's country, adm1 and bioregion must all be valid."""
    bad, checked = [], 0
    for p in _rule_places():
        got = _place(p)
        if got is None or got[1] is None:
            continue
        for name in ns.pick(got[1], NAMES * 3, "named" + p.name):
            t = taxon(name)
            if t is None:
                continue
            checked += 1
            if (t.get("status") or "").lower() not in ENDEMIC_STATUSES:
                bad.append(f"{p}: {name} ({t.get('status')})")
    if not checked:
        pytest.skip("no endemic name could be checked through the API " + ns.day_note())
    assert not bad, f"endemic names that are not valid {ns.day_note()} ({PENDING}):\n" + "\n".join(bad)
