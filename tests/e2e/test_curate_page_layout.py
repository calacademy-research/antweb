"""Curate page layout, Curators page and About menu (Brian Fisher, 2026-10-22).

1. Curate page: the "Need Help?" line, Curator Information and Read-Only Tools run the full page width
   (they were squeezed into the 560px left column, div.admin_left); the Read-Only Tools descriptions
   are no longer 215px floated cells. The upload forms stay in the left column, the admin links in
   the right one.
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


def _full_width_sections_first(html):
    """Curator Information and Read-Only Tools come before div.admin_left (not inside it), each with
    width:auto, and the Read-Only descriptions are not floated."""
    left = html.index('class="admin_left"')
    for sid in ("curate-info", "curate-readonly"):
        m = re.search(r'<div class="admin_action_module curate-full" id="%s" style="width:auto">' % sid, html)
        assert m, f"{sid}: no full-width section"
        assert m.start() < left, f"{sid} is inside the narrow left column"
    ro = html.split('id="curate-readonly"', 1)[1].split('class="admin_left"', 1)[0]
    descs = re.findall(r'<div class="action_desc"([^>]*)>', ro)
    assert len(descs) == 2 and all('style="float:none;width:auto"' in d for d in descs), descs
    assert "/validateSpeciesList.do" in ro and "/specimenListLookup.do" in ro
    help_line = re.search(r'<p id="curate-help"[^>]*>Need Help\?.*?/documentation\.do', html, re.S)
    assert help_line and help_line.start() < left, "Need Help? line missing or not at the top"


def _no_statistics_box(html):
    assert "AntWeb Statistics" not in html
    assert "home_module_header" not in html and "statistics.jsp" not in html


# Static: the source says what the pages will show.

def test_source_curate_sections_full_width():
    src = _src("web/curate/curate-body.jsp")
    _full_width_sections_first(src)
    assert src.count('class="admin_left"') == 1 and src.count('class="admin_right"') == 1
    # The upload form stays in the left column.
    assert src.index('class="admin_left"') < src.index("function validateZipUpload") < src.index('class="admin_right"')


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
    assert ('<li><a href="<%= domainApp %>/curators.do">Curators</a></li>\n'
            '<li><a href="<%= domainApp %>/groups.do">Upload groups</a></li>') in src


# Pages (anonymous, read-only).

def test_groups_page_is_public_and_links_groups():
    r = new_session().get(BASE_URL + "/groups.do", timeout=TIMEOUT)
    outcome = classify(r)
    assert r.status_code == 200 and outcome.category == "ok", outcome
    assert re.search(r"/group\.do\?id=\d+'>", r.text), "groups.do links no group page"


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
    _full_width_sections_first(r.text)
    _no_statistics_box(r.text)
