"""Place pages and their species lists: country, adm1, region, subregion, bioregion, museum and project pages,
their list pages, the species lists (taxonomicPage.do) and geolocale.do (road map step 2;
docs/flows/places.md).

Anonymous and read-only (GET only). One example of each page type is found on its list page. The place
statistics and image numbers are tested by test_region_counts.py, test_place_image_counts.py and
test_numbers_pages.py; the links of every page type by test_site_links.py; field guides and the compare
tool by test_field_guide_compare.py.
"""
import pytest

from public_pages import api_json, assert_message, assert_ok, fetch, links, params, title

# list page, its title, the page type it links to, the parameter that names the place
LISTS = {
    "country": ("/countries.do", "Countries - AntWeb", "name"),
    "region": ("/regions.do", "Regions - AntWeb", "name"),
    "subregion": ("/subregions.do", "Subregions - AntWeb", "name"),
    "adm1": ("/adm1s.do", "Adm1s - AntWeb", "name"),
    "bioregion": ("/bioregions.do", "BioRegions - AntWeb", "name"),
    "museum": ("/museums.do", "Museums - AntWeb", "museumCode"),
    "project": ("/projects.do", "Projects - AntWeb", "name"),
}
# How each place page's title starts (country.jsp, adm1.jsp, region.jsp ...).
TITLE_START = {"country": ("Country ", "Island "), "region": ("Region: ",), "subregion": ("Subregion: ",),
               "adm1": ("Adm1: ",), "bioregion": ("Browsing ",), "museum": ("",), "project": ("",)}


def _example(kind):
    """(list response, path of the first place page of this kind linked from its list page)."""
    path, _, key = LISTS[kind]
    r = fetch(path)
    assert_ok(r, path)
    pages = [p for p in links(r.text, kind) if params(p).get(key)]
    assert pages, f"{path}: no links to {kind}.do"
    return r, pages[0]


@pytest.mark.parametrize("kind", [pytest.param(k, marks=pytest.mark.xfail(strict=False, reason=(
    "F27: in the e2e session the region page (region.do?name=Africa) shows no species list links, while a browser "
    "sees them; under investigation (docs/flows/public-pages-findings.md)"))) if k == "region" else k for k in LISTS])
def test_list_and_place_page(kind):
    """The list page loads with its title and links to the places; the first place opens with its title,
    the statistics table and the links to its species lists."""
    lst, path = _example(kind)
    assert title(lst) == LISTS[kind][1], title(lst)
    r = fetch(path)
    assert_ok(r, path)
    t = title(r)
    assert t.startswith(TITLE_START[kind]) and t.lower().endswith("antweb"), f"{path}: title {t!r}"
    assert 'id="place-stats"' in r.text, f"{path}: no statistics table"
    lists = [p for p in links(r.text, "taxonomicPage") if params(p).get("rank") in ("subfamily", "genus", "species")]
    assert {params(p)["rank"] for p in lists} >= {"subfamily", "genus", "species"}, f"{path}: species list links {lists[:6]}"


@pytest.mark.parametrize("rank, plural", [("subfamily", "Subfamilies"), ("genus", "Genera"), ("species", "Species")])
def test_species_list_of_a_country(rank, plural):
    """The country page's list links: "<Place> Ants (<Rank plural>) - Antweb", one row per taxon linked to
    its taxon page at that rank."""
    r = fetch("/country.do?name=Madagascar")
    assert_ok(r)
    found = [p for p in links(r.text, "taxonomicPage") if params(p).get("rank") == rank and "images" not in params(p)]
    assert found, f"country.do?name=Madagascar: no {rank} list link"
    path = found[0]
    page = fetch(path)
    assert_ok(page, path)
    t = title(page)
    assert t.startswith("Madagascar") and t.endswith(f" Ants ({plural}) - Antweb"), t
    rows = [p for p in links(page.text, "description") if params(p).get("rank") == rank]
    assert rows, f"{path}: no {rank} rows"


def _genera(query):
    page = fetch(f"/taxonomicPage.do?rank=genus&{query}")
    assert_ok(page)
    return {params(p).get("genus") for p in links(page.text, "description") if params(p).get("rank") == "genus"}


def test_place_selects_the_species_list():
    """The place parameter selects the list: Comoros has fewer genera than Madagascar."""
    mada, comoros = _genera("countryName=Madagascar"), _genera("countryName=Comoros")
    assert comoros and mada and comoros != mada, (len(comoros), len(mada))
    assert len(comoros) < len(mada), (len(comoros), len(mada))


@pytest.mark.parametrize("path, expected", [
    ("/taxonomicPage.do?rank=zzbad&countryName=Madagascar", "invalid rank:zzbad"),
    ("/taxonomicPage.do?rank=subspecies&countryName=Madagascar", "Subspecies not supported for taxonomic page. Use species."),
    ("/taxonomicPage.do?rank=genus&countryName=Atlantis", "Country not found for countryName:Atlantis"),
    ("/country.do?name=Atlantis", "name:Atlantis not found."),
    ("/region.do?name=Atlantis", "name:Atlantis not found."),
    ("/subregion.do?name=Atlantis", "name:Atlantis not found."),
    ("/adm1.do?name=Atlantis&country=Madagascar", "name:Atlantis not found."),
    ("/adm1.do?name=Antananarivo", "Must include a country to uniquely specimen an Adm1 in adm1.do."),
    ("/bioregion.do?name=Atlantis", "Bioregion not found"),
    ("/museum.do?museumCode=ZZNOMUSEUM", "Museum not found"),
    ("/project.do?name=zznoproject", "Project:zznoproject not found."),
    ("/geolocale.do?id=99999999", "Geolocale:99999999 not found"),
    ("/geolocale.do", "Geolocale not found"),
])
def test_unknown_place_message(path, expected):
    assert_message(fetch(path), expected, path)


def test_geolocale_id_opens_the_place_page():
    """geolocale.do?id=<id> redirects to the place's own page (the id comes from the API)."""
    body = api_json("/v3.1/geolocales?geolocaleName=Madagascar&up=1") or {}
    ids = [g["geolocaleId"] for g in body.get("geolocales", []) if g.get("georank") == "country"]
    if not ids:
        pytest.skip("Madagascar not found through the API")
    r = fetch(f"/geolocale.do?id={ids[0]}")
    assert_ok(r)
    assert r.history and "/country.do" in r.url and params(r.url).get("name") == "Madagascar", r.url


def test_adm1_page_of_its_country():
    """The adm1 page names its country: adm1.do?name=<adm1>&country=<country> found on the country's page."""
    r = fetch("/country.do?name=Madagascar")
    adm1s = [p for p in links(r.text, "adm1") if params(p).get("country") == "Madagascar"]
    if not adm1s:
        pytest.skip("no adm1 links on the Madagascar page")
    page = fetch(adm1s[0])
    assert_ok(page, adm1s[0])
    t = title(page)
    assert t.startswith("Adm1: ") and params(adm1s[0])["name"] in t and t.endswith(" - Antweb"), t
