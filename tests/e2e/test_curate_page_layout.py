"""Curate page layout, Curators page and About menu (Brian Fisher, 2026-10-22).

1. Curate page (second pass, same day; Brian: "remove the center align and go back to left align and
   import antcat name is still a short width column ... can you give it a more modern look"): every
   section is a full-width card in one left-aligned column (div.cp, styles in web/common/curate_page.css,
   loaded only by this page); no left/right columns, no centering, no 215px floated description cells,
   plain Submit buttons instead of the grey image buttons. Curator sections first, admin cards after.
2. The AntWeb Statistics box is gone from the Curate page (same numbers on the home page and
   statistics.do, linked under Administrator Functions).
3. The Site Warning box is about 3 lines tall (inline height; the site CSS gives textareas 200px).
4. Curators page: the column is "Upload group" (still sorted with sortBy=group), with a line saying it
   links to each group's page and a link to all upload groups (groups.do).
5. About menu: "Upload groups" (groups.do) right below "Curators"; groups.do is public and links
   each group to group.do?id=N.

Static checks run anywhere; the page checks are read-only GETs (anonymous, or the staging test curator
through the paced login helper).
"""
import re

import pytest

from antweb import BASE_URL, REPO_ROOT, TIMEOUT, classify, new_session
from staging_accounts import login, needs


def _src(path):
    return re.sub(r"<%--.*?--%>|<!--.*?-->", "", (REPO_ROOT / path).read_text(encoding="utf-8"), flags=re.S)


CURATOR_CARDS = ["curate-specimen-upload", "curate-image-upload", "curate-uploads", "curate-names", "curate-readonly",
                 "curate-account"]
ADMIN_CARDS = ["curate-admin-names", "curate-manager-tools", "curate-admin-functions", "curate-reports"]
CENTERED = re.compile(r"""align\s*=\s*["']?center|text-align\s*:\s*center""", re.I)
OLD_LAYOUT = ("admin_left", "admin_right", "action_desc", "action_dropdown", "action_browse", "align_right",
              "grey_submit.png", "<h3 align=left>", "<ul align=left>", "width:215")


def _cards(html):
    """The ids of the section cards, in page order."""
    return re.findall(r'<div class="cp-card[^"]*"[^>]*\bid="([\w-]+)"', html)


def _page_part(html):
    """The Curate page's own markup (div.cp), without the site header."""
    assert 'class="cp" id="curate-page"' in html, "no div.cp (the Curate page wrapper)"
    return html.split('class="cp" id="curate-page"', 1)[1]


def _layout_ok(html):
    """One column of full-width cards, nothing centered, no old two-column classes or image buttons; the
    curator cards come before the admin cards; Import AntCat names now is a card section with its button."""
    page = _page_part(html)
    assert not CENTERED.search(page), CENTERED.search(page).group(0)
    found = [x for x in OLD_LAYOUT if x in page]
    assert not found, f"old layout left on the Curate page: {found}"
    cards = _cards(page)
    present_admin = [c for c in cards if c in ADMIN_CARDS]
    present_curator = [c for c in cards if c in CURATOR_CARDS]
    assert present_curator, cards
    if present_admin:
        assert cards.index(present_admin[0]) > cards.index(present_curator[-1]), f"admin cards before curator ones: {cards}"
    assert [c for c in cards if c in CURATOR_CARDS] == [c for c in CURATOR_CARDS if c in cards], f"order: {cards}"
    # Every section card is a direct child of div.cp: no wrapper column narrows it.
    for m in re.finditer(r'<div class="cp-card', page):
        assert not re.search(r'style="[^"]*width:\s*\d', page[m.start():m.start() + 200]), "a card with a fixed width"
    help_line = re.search(r'<p id="curate-help"[^>]*>Need help\?.*?/documentation\.do', page, re.S)
    assert help_line and help_line.start() < page.index('<div class="cp-card'), "Need help? line missing or not at the top"
    return page


def _antcat_import_ok(page):
    """The AntCat import section: inside its card, full width (no floated or fixed-width cell), the text then
    the button."""
    block = re.split(r"</(?:html:)?form>", page.split('id="curate-antcat-import"', 1)[1], maxsplit=1)[0]
    assert "<h3>Import AntCat names now</h3>" in block
    assert "float" not in block and "width:" not in block, block[:400]
    assert block.index("Downloads the AntCat export") < block.index('<input class="cp-btn" type="submit" value="Import AntCat names now">')
    assert page.index('id="curate-admin-names"') < page.index('id="curate-antcat-import"')


def _no_statistics_box(html):
    assert "AntWeb Statistics" not in html
    assert "home_module_header" not in html and "statistics.jsp" not in html


# Static: the source says what the pages will show.

def test_source_curate_layout():
    src = _src("web/curate/curate-body.jsp") + _src("web/curate/curatorLinks.jsp")
    page = _layout_ok(src)
    assert _cards(page) == CURATOR_CARDS + ADMIN_CARDS, _cards(page)
    _antcat_import_ok(page)
    # The stylesheet is loaded by this page only.
    assert 'Asset.url(application, "/common/curate_page.css")' in src
    others = [p for p in (REPO_ROOT / "web").rglob("*.jsp") if "/common/curate_page.css\")" in p.read_text(encoding="utf-8", errors="replace")]
    assert [p.name for p in others] == ["curate-body.jsp"], others
    # Plain buttons: the upload, the museum recount, the AntCat import, the Species List Tool, the Site Warning.
    for value in ("Upload", "Recalculate", "Import AntCat names now", "Open the Species List Tool", "Save",
                  "Clear the warning"):
        assert re.search(r'<input class="cp-btn[^"]*" type="submit" value="%s"' % re.escape(value), src) or \
            re.search(r'<input id="upload-submit-btn" class="cp-btn" type="submit" value="%s"' % re.escape(value), src), value
    assert 'type="image"' not in src
    # The upload button still starts disabled when the server is busy.
    assert 'id="upload-submit-btn" class="cp-btn" type="submit" value="Upload" <%= active %>>' in src


def test_source_curate_stylesheet_is_scoped():
    css = (REPO_ROOT / "web/common/curate_page.css").read_text(encoding="utf-8")
    body = re.sub(r"/\*.*?\*/", "", css, flags=re.S)
    selectors = [sel.strip() for block in re.findall(r"([^{}]+)\{[^{}]*\}", re.sub(r"@media[^{]*\{", "", body))
                 for sel in block.split(",")]
    unscoped = [sel for sel in selectors if sel and not sel.startswith(".cp")]
    assert not unscoped, f"rules that would change other pages: {unscoped}"
    assert re.search(r"\.cp \{[^}]*text-align:\s*left", body)
    assert "text-align: center" not in body and "text-align:center" not in body
    assert "@media (max-width: 760px)" in body


def test_source_curate_has_no_statistics_box():
    _no_statistics_box(_src("web/curate/curate-body.jsp"))


def test_source_site_warning_box_three_lines():
    src = _src("web/curate/curatorLinks.jsp")
    m = re.search(r'<textarea name="text"[^>]*aria-label="Site warning text">', src)
    assert m, "no Site Warning textarea"
    assert re.search(r'style="[^"]*height:4\.5em', m.group(0)), m.group(0)
    assert "resize:none" not in m.group(0)


def test_source_curators_upload_group_column():
    src = _src("web/curators-body.jsp")
    assert "sortBy=group'>Upload group</a></th>" in src
    assert ">Group</a></th>" not in src
    note = re.search(r'<p id="curators-groups-note">(.*?)</p>', src, re.S)
    assert note and "links to each group's page" in note.group(1)
    assert '/groups.do">All upload groups</a>' in note.group(1)


def test_source_about_nav_links_upload_groups():
    src = _src("web/about_nav.jsp")
    assert re.search(r'/curators\.do">Curators</a></li>\s*<li><a href="<%= domainApp %>/groups\.do">Upload groups</a></li>',
                     src)


# Pages (anonymous, read-only).

def test_groups_page_is_public_and_links_groups():
    r = new_session().get(BASE_URL + "/groups.do", timeout=TIMEOUT)
    outcome = classify(r)
    assert r.status_code == 200 and outcome.category == "ok", outcome
    assert re.search(r"/group\.do\?id=\d+", r.text), "groups.do links no group page"


def test_curators_page_upload_group_column():
    r = new_session().get(BASE_URL + "/curators.do", timeout=TIMEOUT)
    assert r.status_code == 200 and classify(r).category == "ok", classify(r)
    assert "sortBy=group'>Upload group</a>" in r.text
    assert '/groups.do">All upload groups</a>' in r.text
    assert re.search(r"/group\.do\?id=\d+", r.text), "no upload group links"


def test_about_menu_links_upload_groups():
    r = new_session().get(BASE_URL + "/about.do", timeout=TIMEOUT)
    assert r.status_code == 200 and classify(r).category == "ok", classify(r)
    assert re.search(r'curators\.do">Curators</a></li>\s*<li><a href="[^"]*/groups\.do">Upload groups</a></li>',
                     r.text), "no Upload groups link below Curators in the About menu"


# The Curate page as the staging test curator.

@needs("uploader")
def test_curate_page_layout_as_curator():
    s, _token, _id = login("uploader")
    r = s.get(BASE_URL + "/curate.do", timeout=TIMEOUT)
    assert r.status_code == 200 and classify(r).category == "ok", classify(r)
    page = _layout_ok(r.text)
    assert not [c for c in _cards(page) if c in ADMIN_CARDS], "admin cards shown to a curator"
    assert "/common/curate_page.css?v=" in r.text
    css = s.get(BASE_URL + "/common/curate_page.css", timeout=TIMEOUT)
    assert css.status_code == 200 and ".cp-card" in css.text, css.status_code
    # The removed box (it sat in div.admin_right), anywhere on the page.
    assert not re.search(r'class="home_module_header">\s*AntWeb Statistics', r.text), "AntWeb Statistics box is back"
    assert "AntWeb Statistics" not in r.text.split('id="curate-help"', 1)[1]


@needs("admin")
def test_curate_page_layout_as_admin():
    s, _token, _id = login("admin")
    r = s.get(BASE_URL + "/curate.do", timeout=TIMEOUT)
    assert r.status_code == 200 and classify(r).category == "ok", classify(r)
    page = _layout_ok(r.text)
    cards = _cards(page)
    for card in ("curate-uploads", "curate-names", "curate-account") + tuple(ADMIN_CARDS):
        assert card in cards, f"{card} missing: {cards}"
    _antcat_import_ok(page)
