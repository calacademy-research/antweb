"""Source checks for the page decisions Brian Fisher made on 2026-10-27 (AntWeb items 10 to 18, 28, 29).

Each test fails on the code before the change. The search rules and the field guide caste are also tested by
test/org/calacademy/antweb/search/SearchRulesTest.java, the legacy image rule by SecurityBatch7Test.

Run: python3 tests/unit/test_decided_pages.py   (also in .github/workflows/tests.yaml)
"""
import os
import re
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
SRC = os.path.join(ROOT, "src", "org", "calacademy", "antweb")


def read(*parts):
    with open(os.path.join(ROOT, *parts), encoding="utf-8", errors="replace") as f:
        return f.read()


def java(*parts):
    return read("src", "org", "calacademy", "antweb", *parts)


def method_body(text, signature):
    i = text.index(signature)
    j = text.index("{", i)
    depth = 0
    for k in range(j, len(text)):
        if text[k] == "{":
            depth += 1
        elif text[k] == "}":
            depth -= 1
            if depth == 0:
                return text[j:k + 1]
    raise AssertionError("no end of " + signature)


def test_upload_lists_are_for_curators_and_admins():
    """10: listSpecimenUploads.do, listUploads.do and listImageUploads.do: curators and admins only, in the action."""
    body = method_body(java("util", "ListUploadsAction.java"), "public ActionForward execute(")
    login = body.index("Check.initLogin(request, mapping)")
    curator = body.index("Check.curator(request, mapping)")
    assert login < curator < body.index("listImageUploads(request, form)"), "curator check before any list is read"
    assert curator < body.index("listSpecimenUploads(request, form)")


def test_curator_email_for_curators_and_admins():
    """11: curator.do shows the email and admin flag to curators and admins only (not to any logged-in account)."""
    page = read("web", "curator-body.jsp")
    m = re.search(r"<% if \((.*?)\) \{[^%]*%>\s*<div[^>]*>\s*<ul>\s*<li><b>Email:</b>", page, re.S)
    assert m, "email block not found"
    assert m.group(1).strip() == "LoginMgr.isCurator(request)", m.group(1)


def test_curators_may_recalculate_museums():
    """12: the museum Recalculate button and action for curators too (isRecalc already needs a curator and a POST)."""
    action = java("OverviewAction.java")
    assert re.search(r'boolean isRecalc = "recalc"\.equals\(action\) && LoginMgr\.isCurator\(request\) && HttpUtil\.isPost\(request\);', action)
    museum = action[action.index('uri.contains("/museum.do")'):]
    museum = museum[:museum.index("MuseumMgr.getMuseum(museumCode)")]
    assert "if (isRecalc) {" in museum and "isAdmin" not in museum.replace("administrators", "")
    page = read("web", "overview-body.jsp")
    block = page[page.index("if (overview instanceof Museum) { %>"):]
    block = block[:block.index("getRecalcButton(request)")]
    assert "isAdmin" not in block, "button shown to admins only"


def test_ownerless_image_needs_an_admin():
    """13: an existing image with no owning group: mayReplace asks whether the image exists, not 'no groups = new'."""
    body = method_body(java("imageUploader", "ImageUploadBatchAction.java"), "public static boolean mayReplace(")
    assert "if (ownerGroups.isEmpty()) return true;" not in body
    assert "imageDb.imageExists(" in body
    assert "public boolean imageExists(String code, String shotType, int shotNumber)" in java("home", "ImageDb.java")


def test_native_is_shown_as_recorded():
    """14: a recorded Native is shown as Native, with a separate note; never rewritten to 'Possibly introduced'."""
    page = read("web", "specimen-body.jsp")
    assert 'speciesDistStatus = "Possibly introduced"' not in page
    assert "<%= Html.esc(speciesDistStatus) %>" in page
    assert "distStatusNote" in page


def test_search_has_one_fixed_order():
    """15: no flipSort; the specimen search is ordered by code, the same every time."""
    search = java("search", "AdvancedSearch.java")
    assert "flipSort" not in search
    assert 'static final String SPECIMEN_ORDER = " sp.code";' in search
    assert '" order by " + sortString' in search and "String sortString = SPECIMEN_ORDER;" in search


def test_search_help_text_says_phrase_and_exact():
    """16: the form's help text describes the rules (commas, phrases, Not equals, Equals)."""
    help_text = read("web", "search", "instruct.jsp")
    assert "separated by commas or spaces" not in help_text
    for words in ("separated by commas", "one phrase", "Not equals", "Equals", "pseudorufa"):
        assert words in help_text, words
    adv = java("search", "AdvancedSearch.java")
    assert 'searchType = "ends"' not in adv, "taxon name equals is no longer a suffix match"
    assert "getTaxonNameEqualsString(searchType, name)" in adv


def test_field_guide_caste_from_the_search():
    """17: the field guide does not force queens; SearchAction keeps the search's caste for it."""
    fg = java("search", "FieldGuideResultsAction.java")
    assert "caste = Caste.QUEEN;" not in fg
    assert "fieldGuideCaste(" in fg
    sa = java("search", "SearchAction.java")
    assert sa.count("session.setAttribute(FieldGuideResultsAction.SEARCH_CASTE, searchParameters.getCaste())") == 2


def test_no_bot_blocking_by_share_of_traffic():
    """18: no percentage threshold (it was 101%, so it never fired); the admin switch stays."""
    tracker = java("util", "UserAgentTracker.java")
    assert "ALLOWED_PERCENT" not in tracker and "PERIOD_IN_SECONDS" not in tracker
    body = method_body(tracker, "private static boolean blockTest(")
    assert "getPercentage" not in body
    assert 'ServerDebug.isDebug("isBlockUnLoggedIn")' in body


def test_no_joke_menu_label():
    """28: the region menu shows the country's own name."""
    menu = read("web", "common", "georegionMenu.jsp")
    assert "Sociopaths" not in menu and "isMichele" not in menu
    assert "isMichele" not in java("util", "LoginMgr.java")


def test_no_universal_analytics():
    """29: the retired Universal Analytics script (ga.js, _getTracker) is on no page."""
    found = []
    for top in ("web", "WEB-INF"):
        for dirpath, _dirs, files in os.walk(os.path.join(ROOT, top)):
            for name in files:
                if not name.endswith((".jsp", ".jspf", ".html", ".htm", ".js", ".xml")):
                    continue
                path = os.path.join(dirpath, name)
                with open(path, encoding="utf-8", errors="replace") as f:
                    text = f.read()
                if "google-analytics.com/ga.js" in text or "_gat._getTracker" in text or "UA-4802256" in text:
                    found.append(os.path.relpath(path, ROOT))
    assert not found, found
    layout = read("web", "layouts", "layout.jsp")
    assert "cdnjs.cloudflare.com/ajax/libs/jquery/" in layout, "the other scripts stay"


def main():
    tests = [(n, f) for n, f in globals().items() if n.startswith("test_") and callable(f)]
    failed = 0
    for name, f in tests:
        try:
            f()
            print("ok  ", name)
        except Exception as e:  # AssertionError, or a ValueError when a line is not found
            failed += 1
            print("FAIL", name, e)
    print(f"{len(tests) - failed} of {len(tests)} passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
