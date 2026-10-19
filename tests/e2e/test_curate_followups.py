"""Curate page follow-ups (2026-10-19).

- Museum page "Recalculate": shown to administrators (was one login id); the museum.do recalc
  runs only for administrators. The login-id check (LoginMgr.isPeter) is gone.
- Orphaned images (orphanedImages.do): administrators only, linked from the admin part of the
  Curate page.
- Developer debug notes that administrators saw since the developer right became the admin role
  (statistics SQL, extra curators-list columns, map manager notes, "Dev Info", the footer page
  tracker, "Map Results (Dev)") are removed.
- "Import AntCat names now" no longer shows the internal export address.

Anonymous, read-only, any host; as the test curator on staging (reads only); static checks.
"""
import re

import pytest

from antweb import BASE_URL, IS_PRODUCTION, REPO_ROOT, TIMEOUT, classify, get, new_session
import test_page_editor as pe

staging_only = pytest.mark.skipif(
    IS_PRODUCTION or pe.HOST not in pe.ALLOWED_HOSTS or not pe.USER or not pe.PASSWORD,
    reason="curator checks run only on staging, with ANTWEB_TEST_USER / ANTWEB_TEST_PASSWORD")

MUSEUM_PAGE = "/museum.do?museumCode=BMNH"


def _text(path):
    return (REPO_ROOT / path).read_text(encoding="utf-8", errors="replace")


def _web_and_src():
    for top in ("src", "web"):
        for path in (REPO_ROOT / top).rglob("*"):
            if path.suffix in (".java", ".jsp") and path.is_file():
                yield path


# ---------- static ----------

def test_no_login_id_for_museum_recalc():
    bad = [str(p.relative_to(REPO_ROOT)) for p in _web_and_src() if "isPeter" in p.read_text(encoding="utf-8", errors="replace")]
    assert not bad, bad
    body = _text("web/overview-body.jsp")
    i = body.index("getRecalcButton(request)")
    assert "LoginMgr.isAdmin(accessLogin)" in body[max(0, i - 200):i]
    action = _text("src/org/calacademy/antweb/OverviewAction.java")
    museum = action[action.index('uri.contains("/museum.do")'):]
    assert "isRecalc && LoginMgr.isAdmin(request)" in museum[:600]


DEBUG_NOTES = ["<b>Dev:</b>", "Dev only", "<h2>Dev Info</h2>", "=== Developer Details ===", "Map Results (Dev)"]


def test_no_developer_debug_notes():
    found = []
    for p in _web_and_src():
        if p.suffix != ".jsp":
            continue
        t = p.read_text(encoding="utf-8", errors="replace")
        found += [f"{p.relative_to(REPO_ROOT)}: {n}" for n in DEBUG_NOTES if n in t]
        if "LoginMgr.isDeveloper(" in re.sub(r"//.*", "", t).replace("if (false && LoginMgr.isDeveloper", ""):
            found.append(f"{p.relative_to(REPO_ROOT)}: LoginMgr.isDeveloper")
    assert not found, found
    assert not (REPO_ROOT / "web/util/developerDetail.jsp").exists()


def test_antcat_import_shows_no_internal_address():
    body = _text("web/curate/curate-body.jsp")
    assert "fetchWorldantsUrl" not in body
    assert "Import AntCat names now" in body


def test_orphaned_images_admin_only_and_linked():
    links = _text("web/curate/curatorLinks.jsp")
    admin = links[links.index("if (accessLogin.isAdmin())"):]
    assert '/orphanedImages.do">Orphaned images</a> (images whose specimen record no longer exists)' in admin
    assert "Check.admin(request, mapping)" in _text("src/org/calacademy/antweb/OrphanedImagesAction.java")
    assert "/orphanedImages.do" in _text("docs/guides/curate-page.md")


def test_guide_explains_museum_recalculate():
    guide = _text("docs/guides/curate-page.md")
    assert "museum_taxon" in guide and "Recalculate" in guide


# ---------- anonymous ----------

def test_orphaned_images_needs_login():
    assert get(new_session(), "/orphanedImages.do").category == "login"


def test_museum_page_has_no_recalc_button_anonymous():
    r = new_session().get(BASE_URL + MUSEUM_PAGE, timeout=TIMEOUT)
    assert classify(r).category == "ok", classify(r)
    assert "action=recalc" not in r.text


# ---------- test curator ----------

@pytest.fixture(scope="module")
def curator():
    s = pe.login()
    info = s.get(BASE_URL + "/editSession.do", timeout=TIMEOUT).json()
    if not info.get("loggedIn"):
        pytest.skip("test login failed")
    return s


@staging_only
def test_orphaned_images_refused_for_curator(curator):
    r = curator.get(BASE_URL + "/orphanedImages.do", timeout=TIMEOUT)
    assert r.status_code < 500 and classify(r).category == "login", classify(r)


@staging_only
def test_museum_page_has_no_recalc_button_for_curator(curator):
    r = curator.get(BASE_URL + MUSEUM_PAGE, timeout=TIMEOUT)
    assert classify(r).category == "ok", classify(r)
    assert "Curator Info" in r.text
    assert "action=recalc" not in r.text

