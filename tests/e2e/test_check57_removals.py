"""Removals after the check of the 57 unclear catalog rows (2026-10-19, Brian's decisions).

Anonymous, read-only, any host:
- removed sub-actions answer 404 ("not found"): list.do and its duplicate path recentDescEdits.do,
  logMgr.do, query.do, schedule.do and utilData.do (checked before any login check, so nothing runs);
- the removed upload.do sub-actions are in test_curate_cleanup.py (REMOVED_UPLOAD_ACTIONS: log-in page
  for visitors, "Action not found." for the test curator); the removed pages (teamMember.do,
  donate.do, editorTrial.do, ...) in test_actions.py::test_removed_actions_stay_removed;
- thanks.jsp (the donation return page) is no longer on the direct JSP allow-list.
Static (any host): no page or class links the removed URLs; the removed classes, JSPs and the old
v3 API code are gone; the Caddy allow-lists no longer name thanks.jsp.
"""
import os

import pytest

from antweb import get, new_session

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")

REMOVED_SUB_ACTIONS = [
    "/list.do?action=getTaxonNames&taxonName=formica",
    "/list.do?action=recentCASPinnedPonerinae",
    "/list.do?action=specimensWithMorphoGenera&groupId=1",
    "/list.do?action=toUpload",
    "/recentDescEdits.do?action=getTaxonNames",
    "/recentDescEdits.do?action=recentCASPinnedPonerinae",
    "/recentDescEdits.do?action=specimensWithMorphoGenera",
    "/recentDescEdits.do?action=toUpload",
    "/logMgr.do?action=apacheLog",
    "/query.do?action=countryEndemic&field=1",
    "/schedule.do?action=justLog",
    "/utilData.do?action=bioregionTaxonCountCrawl",
    "/utilData.do?action=updateGroupCounts",
    "/utilData.do?action=updateGroupUploadStats",
]

REMOVED_FILES = [
    "src/org/calacademy/antweb/curate/team/TeamMemberAction.java",
    "src/org/calacademy/antweb/home/TeamMemberDb.java",
    "src/org/calacademy/antweb/TeamMember.java",
    "src/org/calacademy/antweb/data/AntWikiDataAction.java",
    "src/org/calacademy/antweb/curate/login/AntwebInviteAction.java",
    "src/org/calacademy/antweb/curate/EditorTrialAction.java",
    "src/org/calacademy/antweb/EditCreditAction.java",
    "src/org/calacademy/antweb/curate/NewBioRegionAction.java",
    "src/org/calacademy/antweb/upload/WorldAuthorityAction.java",
    "src/org/calacademy/antweb/util/SpeciesListAction.java",
    "web/donate.jsp", "web/thanks.jsp", "web/curate/editorTrial.jsp", "web/curate/teamMember.jsp",
    "web/documentation/api/apiV3.jsp", "web/search/basicResults.jsp", "web/world_authority_admin.jsp",
    "api/v3/api.py",
]

# Links that must not appear in any page or class.
REMOVED_LINKS = (
    "antwebInvite.do", "antWikiData.do", "apiV3.do", "basicSearch.do", "donate.do", "thanks.do",
    "editCredit.do", "editorTrial.do", "getcomparison.do", "taxonomicpage.do", "getSpeciesList.do",
    "newBioRegion.do", "teamMember.do", "teamMemberImgUpload.do", "teamMemberImgDownload.do",
    "worldAuthoritySave.do", "action=countryEndemic", "action=justLog", "action=apacheLog",
    "action=reloadSpeciesLists", "upload.do?action=toggleDownTime", "action=bioregionTaxonCountCrawl",
    "action=updateGroupCounts", "action=updateGroupUploadStats", "action=getTaxonNames",
    "action=recentCASPinnedPonerinae", "action=specimensWithMorphoGenera", "action=toUpload",
    "donate.jsp", "thanks.jsp", "api.antweb.org/v3/",
)


@pytest.mark.parametrize("url", REMOVED_SUB_ACTIONS)
def test_removed_sub_action_not_found(url):
    outcome = get(new_session(), url)
    assert outcome.status == 404 and outcome.category == "not_found", f"{url}: {outcome}"


def test_thanks_jsp_not_served():
    outcome = get(new_session(), "/thanks.jsp")
    assert outcome.status == 404, outcome


@pytest.mark.parametrize("path", REMOVED_FILES)
def test_removed_file_is_gone(path):
    assert not os.path.exists(os.path.join(ROOT, path)), path


def _sources():
    for top in ("web", "src", "WEB-INF"):
        for d, _, files in os.walk(os.path.join(ROOT, top)):
            if os.sep + "editors" + os.sep in d + os.sep:
                continue
            for f in files:
                if f.endswith((".jsp", ".java", ".xml", ".js", ".html", ".properties")):
                    yield os.path.join(d, f)


def test_no_links_to_removed_urls():
    hits = []
    for p in _sources():
        text = open(p, encoding="utf-8", errors="replace").read()
        for link in REMOVED_LINKS:
            if link in text:
                hits.append(f"{os.path.relpath(p, ROOT)}: {link}")
    assert not hits, hits


def test_caddy_allow_lists_drop_thanks():
    for f in ("Caddyfile", "Caddyfile.common"):
        assert "/thanks.jsp" not in open(os.path.join(ROOT, "docker", "caddy", f)).read(), f
