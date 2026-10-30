"""Taxon pages: description.do, browse.do, images.do, the header Taxa box and the place filter
(road map step 2; docs/flows/taxon-pages.md).

Anonymous and read-only (GET only). Real examples: a genus found on the Formicinae browse page, a
valid species with imaged, georeferenced specimens and a valid subspecies found through the API
(public_pages.py). Pins the current answers to unknown names and bad parameters: AntWeb's message
page with HTTP 200.
"""
import re

import pytest

from antweb import BASE_URL, new_session
from public_pages import (assert_message, assert_ok, fetch, genus_example, h1, links, params, species_example,
                          subspecies_example, title)

RANKS = ["subfamily", "genus", "species", "subspecies"]


def _taxon_path(rank):
    if rank == "subfamily":
        return "/description.do?subfamily=formicinae&rank=subfamily"
    if rank == "genus":
        return genus_example()
    if rank == "species":
        genus, species = species_example()
        return f"/description.do?genus={genus}&species={species}&rank=species"
    found = subspecies_example()
    if not found:
        pytest.skip("no valid subspecies found through the API")
    genus, species, subspecies = found
    # A valid AntCat name may have no AntWeb specimen: the World Ants project lists every valid name.
    return f"/description.do?genus={genus}&species={species}&subspecies={subspecies}&rank=subspecies&project=worldants"


@pytest.mark.parametrize("rank", RANKS)
def test_taxon_page_at_each_rank(rank):
    """Title "<Rank>: <Name> - AntWeb", the rank in the heading, the Overview / child list / Images links,
    the AntCat link and the taxonomic hierarchy."""
    path = _taxon_path(rank)
    r = fetch(path)
    assert_ok(r, path)
    t = title(r)
    assert t.startswith(rank.capitalize() + ": ") and t.endswith("- AntWeb"), f"{path}: title {t!r}"
    name = params(path).get(rank).replace("_", " ")
    assert name.lower() in t.lower(), f"{path}: {name!r} not in title {t!r}"
    assert any(h.startswith(rank.capitalize() + ":") for h in h1(r.text)), f"{path}: headings {h1(r.text)}"
    assert links(r.text, "browse"), f"{path}: no link to the child list (browse.do)"
    assert links(r.text, "images"), f"{path}: no link to the images page (images.do)"
    if name.startswith("("):
        # a placeholder not identified to genus has no AntCat entry: no View in AntCat link (2026-10-27)
        assert 'id="antcat_view"' not in r.text, f"{path}: View in AntCat link for a placeholder"
        return
    assert "antcat.org/catalog" in r.text, f"{path}: no View in AntCat link"
    assert 'id="antcat_view"' in r.text
    # Finding F11 (fixed 2026-10-26): https, and the name URL-encoded (a subspecies has spaces).
    m = re.search(r'href="(https?://www\.antcat\.org/catalog/search\?[^"]*)"', r.text)
    assert m and m.group(1).startswith("https://"), f"{path}: AntCat link {m and m.group(1)!r}"
    qq = re.search(r"[?&]qq=([^&]*)", m.group(1)).group(1)
    assert qq and " " not in qq, f"{path}: AntCat name not encoded: {qq!r}"


def test_placeholder_genus_has_no_antcat_link():
    """A genus not identified to genus, such as "(camponotini)", has no AntCat entry and an empty search name, so
    its page shows no View in AntCat link (it searched AntCat for nothing; staging run of PR #289)."""
    path = "/description.do?genus=(camponotini)&rank=genus"
    r = fetch(path)
    if r.status_code != 200 or not title(r).startswith("Genus:"):
        pytest.skip(f"{path}: no placeholder genus page on this server")
    assert 'id="antcat_view"' not in r.text, f"{path}: View in AntCat link for a placeholder"
    assert "qq=&" not in r.text, f"{path}: an AntCat search for an empty name"


def test_genus_page_has_taxonomic_history_and_tools():
    # A large valid AntCat genus: the first genus of a browse page can be a placeholder or fossil genus without a
    # Taxonomic History or specimens (first staging run, 2026-10-26).
    path = "/description.do?genus=camponotus&rank=genus"
    r = fetch(path)
    assert_ok(r, path)
    assert "Taxonomic history (from AntCat)" in r.text, f"{path}: no Taxonomic History section"
    for tool in ("getComparison", "mapComparison", "fieldGuide"):
        assert links(r.text, tool), f"{path}: no {tool}.do link in the page tools"
    assert "getSpecimenList.do?" in r.text, f"{path}: no Download Data box"


def test_species_page_links_its_map():
    """A species with georeferenced specimens has a Map link (bigMap.do?taxonName=...) next to Overview."""
    genus, species = species_example()
    path = f"/description.do?genus={genus}&species={species}&rank=species"
    r = fetch(path)
    assert_ok(r, path)
    maps = [p for p in links(r.text, "bigMap") if params(p).get("taxonName")]
    assert maps, f"{path}: no Map link"
    assert params(maps[0])["taxonName"].lower() == f"{genus} {species}" or species in maps[0].lower(), maps[0]


def test_species_browse_lists_specimens():
    """browse.do at species rank lists the specimens (specimenReport.jsp), each linked to specimen.do."""
    genus, species = species_example()
    path = f"/browse.do?genus={genus}&species={species}&rank=species"
    r = fetch(path)
    assert_ok(r, path)
    assert title(r).startswith("Browsing species "), title(r)
    assert links(r.text, "specimen"), f"{path}: no specimen links"


def test_genus_browse_lists_species():
    path = genus_example().replace("/description.do", "/browse.do")
    r = fetch(path)
    assert_ok(r, path)
    genus = params(path)["genus"]
    assert title(r).startswith("Browsing genus ") and genus.lower() in title(r).lower(), title(r)
    children = [p for p in links(r.text, "description") if params(p).get("rank") in ("species", "subspecies")]
    assert children, f"{path}: no species links"
    assert all(params(p).get("genus", genus).lower() == genus.lower() for p in children), children[:5]


def test_images_page():
    path = genus_example().replace("/description.do", "/images.do")
    r = fetch(path)
    assert_ok(r, path)
    assert title(r).startswith("Genus: "), title(r)
    assert links(r.text, "description"), f"{path}: no Overview link"


# ---------- the place filter (project, geolocale, country, bioregion ...) ----------

def _species_links(path, session=None):
    r = fetch(path, session=session)
    assert_ok(r, path)
    return r, {params(p).get("species") for p in links(r.text, "description")
               if params(p).get("rank") in ("species", "subspecies") and params(p).get("species")}


# Mystrium: a small genus (about 15 species) found in Madagascar and outside it, so the lists differ
# and the pages are quick.
def test_place_filter_selects_the_list():
    """countryName= narrows the genus's species list to the country, and the title names the place."""
    world, world_species = _species_links("/browse.do?genus=mystrium&rank=genus&project=allantwebants")
    mada, mada_species = _species_links("/browse.do?genus=mystrium&rank=genus&countryName=Madagascar")
    assert "Madagascar" in title(mada), title(mada)
    assert "Madagascar" not in title(world), title(world)
    assert mada_species, "no Mystrium species listed for Madagascar"
    assert mada_species != world_species, "the Madagascar filter did not change the list"


def test_bioregion_filter():
    r, species = _species_links("/browse.do?genus=mystrium&rank=genus&bioregionName=Malagasy")
    assert "Malagasy" in title(r), title(r)
    assert species


def test_old_bioregion_project_link_keeps_its_filter():
    """Finding F4 (fixed 2026-10-26): project=africanants is redirected with bioregionName=Afrotropical."""
    r = fetch("/browse.do?genus=mystrium&rank=genus&project=africanants")
    assert_ok(r)
    assert "Afrotropical" in title(r), (r.url, title(r))


def test_last_place_filter_stays_for_the_session():
    """Pinned (OverviewMgr.findOverview): a taxon page without a place parameter uses the place of the
    session's previous page. project=allantwebants resets it."""
    s = new_session()
    assert_ok(fetch("/country.do?name=Madagascar", session=s), "country.do?name=Madagascar")
    r = fetch("/browse.do?genus=mystrium&rank=genus", session=s)
    assert_ok(r)
    assert "Madagascar" in title(r), f"the place of the previous page was not kept: {title(r)!r}"
    r = fetch("/browse.do?genus=mystrium&rank=genus&project=allantwebants", session=s)
    assert "Madagascar" not in title(r), title(r)


@pytest.mark.parametrize("query, expected", [
    ("countryName=Atlantis", "Country not found for countryName:Atlantis"),
    ("project=zznoproject", "Not found:project overview"),
    ("bioregionName=Atlantis", "Bioregion not found bioregionName:Atlantis"),
    ("museumCode=ZZNOMUSEUM", "Museum not found for museumCode:ZZNOMUSEUM"),
])
def test_unknown_place_filter(query, expected):
    path = f"/description.do?genus=camponotus&rank=genus&{query}"
    assert_message(fetch(path), expected, path)


def test_geolocale_id_not_a_number_gets_a_message():
    """Finding F3 (fixed 2026-10-26): was the error page (NumberFormatException)."""
    path = "/description.do?genus=camponotus&rank=genus&geolocaleId=abc"
    assert_message(fetch(path), "Geolocale not found for geolocaleId:abc", path)


# ---------- the header Taxa box (quick search) and taxonName / antcatId links ----------

def test_taxon_name_redirects_to_the_taxon_page():
    """The header Taxa box sends description.do?taxonName=...; AntWeb redirects to the taxon's page."""
    r = fetch("/description.do?taxonName=Camponotus")
    assert_ok(r)
    assert r.history and r.history[0].status_code in (301, 302, 303), [h.status_code for h in r.history]
    q = params(r.url)
    assert q.get("genus", "").lower() == "camponotus" and q.get("rank") == "genus", r.url
    assert title(r).startswith("Genus: Camponotus"), title(r)


def test_taxon_name_keeps_the_page_type():
    r = fetch("/browse.do?taxonName=formicinae")
    assert_ok(r)
    assert "/browse.do" in r.url and params(r.url).get("rank") == "subfamily", r.url


def test_taxon_name_of_a_specimen_code_opens_the_specimen():
    r = fetch("/description.do?taxonName=casent0103190", allow_redirects=False)
    assert r.status_code in (301, 302, 303), r.status_code
    assert "/specimen.do?code=casent0103190" in r.headers.get("Location", ""), r.headers.get("Location")


def test_autocomplete_suggests_names():
    r = new_session().get(BASE_URL + "/search/autoCompleteKeys.jsp?q=campon", timeout=60)
    assert r.status_code == 200, r.status_code
    assert any(l.strip().startswith("Camponotus") for l in r.text.splitlines()), r.text[:300]


# ---------- unknown names and bad parameters (current answers, pinned) ----------

@pytest.mark.parametrize("path, expected", [
    ("/description.do?genus=zzznotagenus&rank=genus", "Taxon not found"),
    ("/browse.do?genus=zzznotagenus&rank=genus", "Taxon not found"),
    ("/images.do?genus=zzznotagenus&rank=genus", "Taxon not found"),
    ("/description.do?taxonName=zzznotataxon", "zzznotataxon does not exist in the database"),
    ("/description.do?genus=camponotus&rank=specimen", "Description.do requests are for taxa, not specimens."),
    ("/description.do?genus=camponotus&rank=zzbadrank", "Bad rank:zzbadrank"),
    ("/description.do?project=allantwebants", "Taxon not found."),
])
def test_unknown_taxon_or_rank_message(path, expected):
    assert_message(fetch(path), expected, path)


def test_antcat_id_opens_the_taxon_page():
    """description.do?antcatId=<id> redirects to the taxon page (the AntCat id comes from the API)."""
    r = new_session().get(BASE_URL + "/v3.1/taxa?taxonName=formicinae&up=1", timeout=120)
    taxa = r.json().get("taxa", []) if r.status_code == 200 else []
    antcat_id = next((t.get("antcatId") for t in taxa if t.get("antcatId")), None)
    if not antcat_id:
        pytest.skip("no AntCat id for Formicinae in the API")
    page = fetch(f"/description.do?antcatId={antcat_id}")
    assert_ok(page)
    assert title(page).startswith("Subfamily: Formicinae"), title(page)
    assert re.search(r"[?&]subfamily=formicinae", page.url), page.url
