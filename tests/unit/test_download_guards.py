"""Download guards in the pages (Brian Fisher, 2026-10-21): no free bulk downloads of specimen data.

- The advanced search results page has no "See all" link (it asked for 10,000 records) and pages
  200 at a time with Previous / Next links (search/searchPager.jsp).
- Links that run a specimen search from place, museum, group and locality pages carry rel="nofollow".
- robots.txt keeps its rules and disallows the search and download actions.

Run: python3 tests/unit/test_download_guards.py   (also in .github/workflows/tests.yaml)
"""
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8", errors="replace") as f:
        return f.read()


def main():
    results = read("web/search/advancedSearchResults-body.jsp")
    assert "See all" not in results and "limit=10000" not in results, "advanced search results still offer See all"
    assert 'request.getParameter("limit")' not in results, "the list length still comes from the request"
    assert '<%@ include file="/search/searchPager.jsp" %>' in results
    pager = read("web/search/searchPager.jsp")
    assert 'rel="nofollow"' in pager and "Previous" in pager and "Next" in pager

    # Every line with a link that runs advancedSearch.do on these pages has rel nofollow.
    for page in ("web/overview-body.jsp", "web/taxonomicPage-body.jsp", "web/group-body.jsp", "web/locality-body.jsp"):
        text = read(page)
        lines = [l for l in text.splitlines() if "<a " in l]
        searching = [l.strip() for l in lines if "/advancedSearch.do?" in l or "SearchUrl + " in l]
        assert searching, page + ": no search links found"
        bad = [a for a in searching if "nofollow" not in a]
        assert not bad, page + ": search links without rel=nofollow: " + repr(bad)

    robots = read("web/robots.txt").splitlines()
    for rule in ("Disallow: /advancedSearch.do", "Disallow: /getSpecimenList.do", "Disallow: /getSearchList.do",
                 "Disallow: /speciesListDownload.do", "Disallow: /web/", "Disallow: /curate/"):
        assert rule in robots, "robots.txt: missing " + rule
    assert robots[0] == "User-agent: *"

    # The download box of a taxon page is offered for a genus or species only (the action refuses the rest).
    tools = read("web/common/pageToolLinks.jsp")
    assert 'if (Utility.notBlank(taxon.getGenus())) { %>' in tools
    print("download guards in the pages OK")


if __name__ == "__main__":
    main()
