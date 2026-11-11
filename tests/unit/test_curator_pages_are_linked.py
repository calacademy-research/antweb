"""Every curator and admin page can be found from the Curator Tools page or the Technical page.

Brian Fisher, 2026-11-11: the Daily update page (dailyUpdate.do) existed for four days with no link on the Curator
Tools page; it was linked only from the header while the daily update ran. A page nobody can find is a page nobody
uses. The rule this test keeps: an action of docs/catalog/actions.csv that shows a page to curators, admins or
logged-in users is either linked from one of the two hub pages (web/curate/curate-body.jsp with curatorLinks.jsp,
web/curate/technical-body.jsp), or is listed in REACHED_FROM below with the page that links to it (a form, a
per-item page, a step of a tool), or is in NO_LINK with the reason. A new page fails here until it has one of the
three.

Run: python3 tests/unit/test_curator_pages_are_linked.py
"""
import csv
import pathlib

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
HUBS = ("web/curate/curate-body.jsp", "web/curate/curatorLinks.jsp", "web/curate/technical-body.jsp")

# Pages opened from another page: the file named here must contain the link.
REACHED_FROM = {
    "/countryDoc": "web/curate/geolocale/geolocaleMgr-body.jsp",
    "/dbStatus": "web/util/serverStatus-body.jsp",
    "/editProject": "web/curate/projectMgr-body.jsp",
    "/newGroup": "web/curate/viewGroups-body.jsp",
    "/newLogin": "web/curate/viewLogins-body.jsp",
    "/passwordResetCampaign": "web/curate/viewLogins-body.jsp",      # Login Manager (Brian Fisher, 2026-11-11)
    "/saveGroup": "web/curate/viewGroup-body.jsp",
    "/saveMuseum": "web/curate/viewMuseum.jsp",
    "/viewGroup": "web/curate/saveGroupSuccess-body.jsp",
    "/viewMuseum": "web/curate/manageMuseums-body.jsp",
    "/adm1Mgr": "web/curate/geolocale/geolocaleMgr-body.jsp",
    "/deleteImages": "web/search/recentSearchResults-body.jsp",
    "/descEditImageUpload": "web/curate/curatorFiles.jsp",
    "/disputeMgr": "web/curate/geolocale/geolocaleMgr-body.jsp",
    "/editGeolocale": "web/curate/geolocale/geolocaleMgr-body.jsp",
    "/fieldHistory": "src/org/calacademy/antweb/DescriptionAction.java",
    "/imageUploadReport": "web/imageUploader/listImageUploads-body.jsp",
    "/listUploads": "web/curate/viewGroup-body.jsp",
    "/speciesListHistory": "web/curate/speciesList/speciesListTool-body.jsp",
    "/speciesListTool": "web/curate/speciesList/speciesListSearch.jsp",
    "/speciesListToolSearch": "web/curate/speciesList/speciesListTool.jsp",
}

# Pages with no link, on purpose, and why. (None today.)
NO_LINK = {
}


def read(path):
    return (REPO_ROOT / path).read_text(encoding="utf-8", errors="replace")


def pages():
    with open(REPO_ROOT / "docs/catalog/actions.csv", encoding="utf-8", newline="") as f:
        for row in csv.DictReader(f):
            if (row["kind"] == "action" and row["access"] in ("curator", "admin", "login") and row["jsps"]
                    and not row["status"].startswith("remove")):
                yield row["key"]


def test_every_curator_and_admin_page_has_a_way_in():
    hubs = "".join(read(p) for p in HUBS)
    missing, stale = [], []
    for key in pages():
        link = key.lstrip("/") + ".do"
        if link in hubs:
            if key in REACHED_FROM or key in NO_LINK:
                stale.append(key + " is linked from a hub page now: take it out of the lists of this test")
            continue
        if key in REACHED_FROM:
            if link not in read(REACHED_FROM[key]):
                missing.append(key + ": " + REACHED_FROM[key] + " no longer links to it")
            continue
        if key not in NO_LINK:
            missing.append(key + ": no link on the Curator Tools or Technical page, and not in REACHED_FROM or NO_LINK")
    assert not missing and not stale, "\n".join(missing + stale)
    print("ok   test_every_curator_and_admin_page_has_a_way_in")


def test_the_daily_update_page_is_an_admin_report():
    """Brian Fisher, 2026-11-11: the daily update is an admin matter; its link is in the admins' Reports section of
    the Curator Tools page (first line), not in a section every curator sees, and on the Technical page."""
    links = read("web/curate/curatorLinks.jsp")
    card = links.split('id="curate-reports"', 1)[1].split("</div>", 1)[0]
    first = card.split("<li>", 2)[1]
    assert first.startswith('<a href="<%= domainApp %>/dailyUpdate.do">Daily update</a>'), first[:120]
    assert links.lstrip().startswith("<% if (accessLogin.isAdmin()) {") or "isAdmin" in links.split('id="curate-reports"', 1)[0], \
        "curatorLinks.jsp is no longer for admins only"
    assert "dailyUpdate.do" not in read("web/curate/curate-body.jsp"), "the link is in a section curators see"
    assert '/dailyUpdate.do">Daily update</a>' in read("web/curate/technical-body.jsp")
    print("ok   test_the_daily_update_page_is_an_admin_report")


def test_the_lists_name_real_pages():
    keys = set(pages())
    unknown = [k for k in list(REACHED_FROM) + list(NO_LINK) if k not in keys]
    assert not unknown, "not a curator or admin page of the catalog any more: " + ", ".join(unknown)
    print("ok   test_the_lists_name_real_pages")


if __name__ == "__main__":
    test_every_curator_and_admin_page_has_a_way_in()
    test_the_daily_update_page_is_an_admin_report()
    test_the_lists_name_real_pages()
    print("ok")
