"""An old place name in a link goes to the current name, and place names inside JavaScript strings are escaped
(2026-11-10, Brian Fisher: AntWeb takes the current country names; Côte d'Ivoire becomes the first valid country
with an apostrophe).

Read from the source, without a server:
  - every page request is looked at for old place names before its page runs (SessionRequestFilter calls
    OldPlaceNames.movedTo before the page, and answers 301 with the new address);
  - the three places where a place's parameters were written into a JavaScript string escape them (Html.js), so an
    apostrophe in a place name does not end the string;
  - the API resolves country= on every route that reads it.
The rule itself: test/org/calacademy/antweb/util/OldPlaceNamesTest.java; on staging: tests/e2e/test_old_place_names.py;
the API: tests/unit/test_api_country.py, test_api_v4.py.

Run: python3 tests/unit/test_old_place_names.py   (also in .github/workflows/tests.yaml)
"""
import os
import re

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def src(path):
    with open(os.path.join(ROOT, path), encoding="utf-8") as f:
        return f.read()


def test_the_filter_redirects_before_the_page_runs():
    text = src("src/org/calacademy/antweb/util/SessionRequestFilter.java")
    moved = text.index("OldPlaceNames.movedTo(request)")
    page = text.index("chain.doFilter(request, response)")
    assert moved < page, "the old-name redirect must come before the page"
    between = text[moved:page]
    assert "SC_MOVED_PERMANENTLY" in between and 'setHeader("Location", movedTo)' in between and "return;" in between, between
    # after the CSRF check, so a refused post is refused as before; only GET and HEAD are looked at
    assert text.index("Csrf.methodNeedsCheck") < moved
    helper = src("src/org/calacademy/antweb/util/OldPlaceNames.java")
    assert '!"GET".equals(method) && !"HEAD".equals(method)' in helper and "AntwebMgr.isPopulated()" in helper
    assert "HttpUtil.encode(current)" in helper, "the new name must be URL-encoded"
    for path in ("/editGeolocale.do", "/geolocaleMgr.do", "/adm1Mgr.do"):
        assert '"' + path + '"' in helper, path


def test_place_parameters_in_javascript_strings_are_escaped():
    js = "org.calacademy.antweb.util.Html.escAttr(org.calacademy.antweb.util.Html.js("
    tiles = src("web/taxonChildImageSet.jsp")
    assert "<%= overviewParams %>';" not in tiles and js + "overviewParams)) %>';" in tiles
    assert "<%= thisChild.getBrowserParams() %>';" not in tiles and js + "thisChild.getBrowserParams())) %>';" in tiles
    tools = src("web/common/pageToolLinks.jsp")
    assert "getSpecimenList.do?<%= overview.getParams() %>" not in tools
    assert "getSpecimenList.do?<%= " + js + "overview.getParams())) %>&taxonName=<%= " + js + "taxon.getTaxonName())) %>'" in tools
    # no other JSP writes a place's parameters raw into a JavaScript string of an onclick
    for folder, _, files in os.walk(os.path.join(ROOT, "web")):
        for name in files:
            if not name.endswith(".jsp"):
                continue
            with open(os.path.join(folder, name), encoding="utf-8", errors="replace") as f:
                for line in f:
                    for call in re.findall(r"onclick=\"[^\"]*'[^'\"]*<%=\s*([^%]*?)\s*%>", line):
                        assert not re.search(r"getParams\(\)|overviewParams|getSearchCriteria\(\)|getBrowserParams\(\)", call) \
                            or "Html.js(" in call, (name, call)


def test_the_api_resolves_country_on_every_route():
    api = src("api/v3.1/api.py")
    assert api.count("request.args.get('country'") == api.count("currentCountry(request.args.get('country'") == 4
    assert "validName = Column('valid_name', String)" in api and "kwargs[key]['countryName'] = used" in api


def main():
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
    for t in tests:
        t()
        print("ok " + t.__name__)
    print("OK (" + str(len(tests)) + " tests)")


if __name__ == "__main__":
    main()
