"""Nightly name update and the Name updates page (2026-10).

After the AntCat import each night, AntWeb applies the specimen upload's own name rule
(CurrentValidNameRule) to every existing specimen and moves those whose name AntCat has since made a
synonym (or obsolete combination...) to the current valid name; each moved specimen is a row on the
Name updates page (nameUpdates.do). Switch: etc/name-update.properties (off / report / apply).
Database behaviour: test/org/calacademy/antweb/upload/SpecimenNameUpdateDbTest.java (CI, MySQL 8.4);
decisions: SpecimenNameUpdateTest.java.

Static checks (any host): the step runs in set 1 after the AntCat import and the TaxonWorks upload and
before the counts; the upload and the nightly step use the same rule. Live checks (read-only):
anonymous visitors are asked to log in; on staging, a curator sees only their own group and no
Preview button, an administrator sees the group filter and the Preview button; the Curate page links
to the page. Skipped where the page is not deployed yet. The Preview button itself (a report-mode run,
which writes only preview rows) is not pressed by the tests.
"""
import re
from html import unescape

import pytest

from antweb import BASE_URL, REPO_ROOT, TIMEOUT, classify, get, new_session
from staging_accounts import IS_STAGING, login, needs

SRC = REPO_ROOT / "src/org/calacademy/antweb"


def _code(path):
    text = path.read_text(encoding="utf-8", errors="replace")
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def test_step_runs_in_set1_after_antcat_and_taxonworks_before_counts():
    code = _code(SRC / "util/UtilDataAction.java")
    set1 = code[code.index('if ("set1".equals(action))'):code.index('if ("set2".equals(action))')]
    order = [set1.index('"worldantsFetchAndReload"'), set1.index("TaxonWorksNightly.run("),
             set1.index("nightlyNameUpdate("), set1.index("TaxonMgr.populate(")]
    assert order == sorted(order), order


def test_upload_and_nightly_share_the_rule():
    upload = _code(SRC / "upload/SpecimenUploadProcess.java")
    assert "CurrentValidNameRule.decide(taxonName, CurrentValidNameRule.uploadLookup(" in upload
    nightly = _code(SRC / "upload/SpecimenNameUpdate.java")
    assert "CurrentValidNameRule.decide(uploaded, lookup)" in nightly


def _deployed(outcome):
    if outcome.category == "not_found":
        pytest.skip("nameUpdates.do is not deployed on this server yet")


def test_page_needs_login():
    outcome = get(new_session(), "/nameUpdates.do")
    _deployed(outcome)
    assert outcome.category == "login", outcome


def test_preview_needs_admin():
    """The preview job exists and an anonymous visitor is asked to log in; nothing runs."""
    outcome = get(new_session(), "/utilData.do?action=specimenNameUpdatePreview")
    if "action not found" in (outcome.detail or "") or outcome.category == "not_found":
        pytest.skip("specimenNameUpdatePreview is not deployed on this server yet")
    assert outcome.category in ("login", "denied"), outcome


def _page(session, query=""):
    r = session.get(BASE_URL + "/nameUpdates.do" + query, timeout=120)
    outcome = classify(r)
    _deployed(outcome)
    assert outcome.category == "ok", outcome
    assert "What this shows:" in r.text, "the page explains itself (report-help box)"
    return r.text


@pytest.mark.skipif(not IS_STAGING, reason="test logins: staging only")
@needs("noupload")
def test_curator_sees_own_group_only():
    s, _token, _id = login("noupload")
    text = _page(s, "?groupId=1")
    assert 'name="groupId"' not in text, "curators get no group filter"
    assert "Preview now" not in text, "the Preview button is for administrators"
    groups = {unescape(g) for g in re.findall(r"<h4>(.*?)</h4>", text)}
    assert groups <= {"ZZ Test Group (staging only)"}, f"a curator sees other groups: {groups}"
    curate = s.get(BASE_URL + "/curate.do", timeout=TIMEOUT).text
    assert "/nameUpdates.do" in curate


@pytest.mark.skipif(not IS_STAGING, reason="test logins: staging only")
@needs("admin")
def test_admin_sees_filter_and_preview_button():
    s, _token, _id = login("admin")
    text = _page(s)
    assert 'name="groupId"' in text
    assert 'value="specimenNameUpdatePreview"' in text and "Preview now (report only)" in text
    assert "Setting on this server:" in text
    curate = s.get(BASE_URL + "/curate.do", timeout=TIMEOUT).text
    assert "/nameUpdates.do" in curate and "/countCheck.do" in curate
