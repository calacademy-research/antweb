"""Checks of the 2026-10-27 fixes in the curator, admin and shared page fragments (no server or database).

1. Values from the request or from uploads are escaped where the pages show them: Page Tracker and Server
   Status addresses, the Species List Tool and History (list names, hidden fields, messages), the page text
   [Edit] links and image upload forms, the image upload confirmation pages, the status set label, the
   thumbnail view switch (a JavaScript string), the species list validator report, events, the login name.
2. Pages that failed with an error: Species List History with no logs, the Species List Tool with a list
   that is not found, the all-groups upload list with a deleted group, the statistics page with a status
   that has no imaged name, the Hawaii entry of the region menu.
3. Wrong output: Species List History's project footer showed the fixed numbers 2 and 3; the subgenus
   "Unassigned" choice vanished while it was chosen; the citation said "Academy of Science"; the Species List
   option of a list made of its children said "disable" (does nothing); the image numbers on the
   statistics page showed 0 right after a restart; the image upload link named the uploaded file, not the
   file as saved.
4. Access: the Species List Tool and History pages check for a curator; the not-logged-in redirect is
   absolute; Edit Project stops after its redirect; the Geolocale Manager shows its Save arrow to
   administrators only (the action refuses others).
5. The statistics tables reach the page as a request attribute, not the session (two tabs shared it).

Run: python3 tests/unit/test_curate_page_fixes.py   (also in .github/workflows/tests.yaml)
"""
import os
import re

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def read(rel):
    with open(os.path.join(ROOT, rel), encoding="utf-8", errors="replace") as f:
        return f.read()


def absent(rel, pattern, why):
    text = read(rel)
    m = re.search(pattern, text)
    assert m is None, f"{rel}: {why}: {m.group(0)!r}"


def present(rel, pattern, why):
    assert re.search(pattern, read(rel)), f"{rel}: {why}"


def test_escaped_outputs():
    absent("web/util/pageTracker-body.jsp", r"<%=\s*(this)?[tT]racker\.getTarget\(\)\s*%>", "visitor address shown raw")
    absent("web/util/serverStatus-body.jsp", r"<%=\s*PageTracker\.showRequests\(\)\s*%>", "visitor addresses shown raw")
    hist = "web/curate/speciesList/speciesListHistory-body.jsp"
    absent(hist, r'String message = speciesListName \+', "list name from the request in the message")
    absent(hist, r'value="<%= value %>"', "option value raw")
    tool = "web/curate/speciesList/speciesListTool-body.jsp"
    absent(tool, r'value="<%=\s*(mapSpeciesList[123]Name|displaySubfamily|refSpeciesListType|refSpeciesListName)\s*%>"',
           "request value in a hidden field raw")
    absent(tool, r'speciesListName=" \+ mapSpeciesList[123]Name', "list name in a link raw")
    present("src/org/calacademy/antweb/curate/speciesList/SpeciesListToolProps.java",
            r"Html\.escAttr\(getPermaLink\(\)\)", "permalink not escaped")
    for f in ("descEditFieldCK.jsp", "imageDescEditField.jsp"):
        absent("web/common/descEdit/" + f, r"<%=\s*thisPageTarget\s*%>", "page address raw")
    for f in ("taxonImageEditField.jsp", "taxonEditFieldCK.jsp", "taxonVideoEditField.jsp"):
        absent("web/common/descEdit/" + f, r"<%=\s*Utility\.stripParams\(browserParams", "request parameters raw")
    absent("web/common/descEdit/taxonVideoEditField.jsp", r'"/description\.do\?" \+ browserParams %>', "request parameters raw")
    for f in ("descEditImageConfirm-body.jsp", "overviewImageConfirmForm-body.jsp", "taxonImageConfirmForm-body.jsp"):
        absent("web/common/descEdit/" + f, r"<%=\s*imageUrl\s*%>|\+ imageUrl \+", "uploaded file address raw")
    absent("web/common/statusesDisplay.jsp", r"<%=\s*statusSet\s*%>", "status set from the request raw")
    present("web/common/imageViewsDisplay.jsp", r"changeThumbTarget = org\.calacademy\.antweb\.util\.Html\.escAttr\("
            r"org\.calacademy\.antweb\.util\.Html\.js\(changeThumbTarget\)\)", "address not escaped for the JavaScript string")
    val = "web/curate/speciesList/validateSpeciesList-body.jsp"
    for getter in ("getNormalizedName", "getMessage", "getSuggestion", "getCategory"):
        absent(val, r"<%=\s*item\." + getter + r"\(\)", getter + " raw")
    absent(val, r"replace\(\"<\", \"&lt;\"\)", "half escaping of the input column")
    absent("web/util/events.jsp", r'\+ event\.getName\(\) \+|\+ curator\.getName\(\) \+', "event or curator name raw")
    absent("web/common/loginPanel.jsp", r'"\\">" \+ displayName \+', "login name raw")
    absent("web/common/statusDisplayCore.jsp", r"<%=\s*statusNote\s*%>", "status note raw in a title")
    absent("web/curate/defaultSpecimen/defaultSpecimenConfirmPanel.jsp", r"<%=\s*specimen\.getCode\(\)\s*%>", "code raw")
    absent("web/util/serverDebug-body.jsp", r"<%=\s*ServerDebug\.getDebug\(\)\s*%>", "debug value raw")
    absent("web/util/statsPage-body.jsp", r"<td><%= Formatter\.initCap\(status\) %>|\+ Formatter\.initCap\(status\) \+",
           "status label raw")
    print("Request and upload values are escaped on the curator, admin and shared pages.")


def test_no_error_pages():
    hist = read("web/curate/speciesList/speciesListHistory-body.jsp")
    guard = hist.index("projTaxonLogs == null || projTaxonLogs.size() <= 1")
    first = hist.index("projTaxonLogs.get(0).getDetails()")
    assert guard < first, "Species List History reads the first log before checking there is one"
    present("web/curate/speciesList/speciesListHistory-body.jsp", r"geolocaleTaxonLogs\.isEmpty\(\)", "empty geolocale logs")
    tool = read("web/curate/speciesList/speciesListTool-body.jsp")
    assert "if (sl == null) {" in tool and ": not found)" in tool, "Species List Tool: a list not found stops the page"
    absent("web/curate/speciesList/speciesListTool-body.jsp", r"if \(sl == null\) A\.log", "old null check that went on")
    absent("web/util/listSpecimenUploads-body.jsp", r"GroupMgr\.getGroup\(upload\.getGroupId\(\)\)\.getAbbrev\(\)",
           "a deleted group stops the upload list")
    absent("web/util/statsPage-body.jsp", r"A\.commaFormat\(count\)", "a status with no imaged name stops the page")
    present("web/common/georegionMenu.jsp", r"if \(hawaii != null\)", "Hawaii entry without a check")
    print("Species List History and Tool, the upload list, the statistics page and the menu do not fail.")


def test_wrong_output():
    absent("web/curate/speciesList/speciesListHistory-body.jsp", r"<td>2</td><td>3</td>", "fixed numbers in the footer")
    present("web/curate/speciesList/speciesListHistory-body.jsp", r"projColCounts\[i\] = projColCounts\[i\] \+ 1",
            "project footer not counted")
    sub = "web/common/subgeneraDisplay.jsp"
    absent(sub, r"Unnassigned", "misspelt label")
    absent(sub, r'"none"\.equals\(displaySubgenera\)', "compares the label with none")
    present("web/common/citeInclude.jsp", r"California Academy of Sciences,", "citation names the institution wrongly")
    absent("web/curate/curate-body.jsp", r'" disable>', "'disable' is not an attribute")
    present("web/curate/curate-body.jsp", r'"\"\.equals\(value\) \? " disabled"', "list made of its children selectable")
    stats = read("web/util/statsPage-body.jsp")
    assert "A.commaFormat(AntwebMgr.getImaged" not in stats and "A.commaFormat(AntwebMgr.getTotalImages" not in stats, \
        "image numbers shown as 0 right after a restart"
    for n in ("liveImagedSpecies", "liveImagedSpecimens", "liveTotalImages"):
        assert "%s > 0 ? A.commaFormat(%s) : starting" % (n, n) in stats, n
    present("src/org/calacademy/antweb/upload/DescEditImageUploadAction.java",
            r'dirFileName = dir \+ "/" \+ outputFileName;', "the link names the uploaded file, not the saved one")
    print("Footer counts, subgenus choice, citation, species list choice, statistics and upload link are right.")


def test_access_checks():
    for f in ("speciesListTool.jsp", "speciesListHistory.jsp"):
        present("web/curate/speciesList/" + f, r'<%@include file="/curate/curatorCheck\.jsp" %>', "no curator check")
    for f in ("web/curate/curatorCheck.jsp", "web/common/loginCheck.jsp"):
        absent(f, r'sendRedirect\("notLoggedIn\.jsp"\)', "relative redirect")
    ep = read("web/curate/project/editProject.jsp")
    assert "getProjectNames().contains(thisProject)" not in ep, "compares names with a Project"
    assert re.search(r'sendRedirect\([^;]*permissionDenied\.jsp"\);\s*return;', ep), "Edit Project goes on after the redirect"
    gm = read("web/curate/geolocale/geolocaleMgr-body.jsp")
    i = gm.index('/image/upGreen.jpg')
    assert "LoginMgr.isAdmin(request)" in gm[max(0, i - 400):i], "Save arrow shown to curators"
    print("Curator checks, redirects, Edit Project and the Geolocale Manager Save arrow are right.")


def test_statistics_in_the_request():
    absent("src/org/calacademy/antweb/StatisticsAction.java", r'session\.setAttribute\("statistics"', "statistics in the session")
    present("web/util/statisticsStr.jsp", r'request\.getAttribute\("statistics"\)', "statisticsStr reads the session")
    present("web/util/statisticsRealTime-body.jsp", r'request\.getAttribute\("statistics"\)', "real-time page reads the session")
    present("web/util/statisticsRealTime-body.jsp", r'Boolean\.TRUE\.equals\(session\.getAttribute\("isLink"\)\)', "isLink unboxed")
    print("The statistics tables reach the page in the request.")


def main():
    test_escaped_outputs()
    test_no_error_pages()
    test_wrong_output()
    test_access_checks()
    test_statistics_in_the_request()


if __name__ == "__main__":
    main()
