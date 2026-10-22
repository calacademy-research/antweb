"""The Specimen Upload Reports page (listSpecimenUploads.do) says which view it shows (Brian, 2026-10-22).

An admin who opened their group's uploads and then "view all" saw every group's uploads under a
heading that did not make that clear. Now:
- all groups (listSpecimenUploads.do): title "All groups' specimen uploads", the line "You are
  viewing every group's uploads." and a link back to the viewer's own group ("Show only <group>'s
  uploads");
- one group (listSpecimenUploads.do?groupId=N): title "Specimen uploads of <group name>", and for
  admins the link "View all groups' uploads".
The existing links (Home, Curator Tools, and for admins "Each group's last upload") stay.

Read-only. Staging test logins (admin, and the test uploader: a curator of the test group).
"""
import re
from html import unescape

import pytest

from antweb import BASE_URL, TIMEOUT
from staging_accounts import GROUP_NAME, group_of, login, needs

ALL_TITLE = "All groups' specimen uploads"
ALL_LINE = "You are viewing every group's uploads."
VIEW_ALL = "View all groups' uploads"


def _text(html):
    # Entities (&nbsp; &#39; &amp;) decoded; a no-break space counts as a space.
    return re.sub(r"\s+", " ", unescape(re.sub(r"<[^>]+>", " ", html)).replace("\xa0", " "))


def _h2(html):
    m = re.search(r"<h2[^>]*>(.*?)</h2>", html, re.S)
    assert m, "no title on the page"
    return _text(m.group(1)).strip()


def _get(s, path):
    r = s.get(BASE_URL + path, timeout=TIMEOUT)
    assert r.status_code == 200, (path, r.status_code)
    return r.text


def _own_group_link(html):
    m = re.search(r'<a class="uploads-own-group" href="[^"]*/listSpecimenUploads\.do\?groupId=(\d+)">(.*?)</a>', html)
    return (int(m.group(1)), _text(m.group(2)).strip()) if m else (None, None)


def _check_kept_links(html, admin):
    assert re.search(r'<a href = "https?://[^"]+/curate\.do">Curator Tools</a>', html), "Curator Tools link missing"
    assert re.search(r'<a href = "https?://[^"/]+[^"]*">Home</a>', html), "Home link missing"
    assert ("name=lastSpecimenUpload\">Each group's last upload</a>" in html) == admin


@needs("admin")
def test_admin_sees_which_view():
    s, _token, _login_id = login("admin")
    gid, _name = group_of(s)

    all_page = _get(s, "/listSpecimenUploads.do")
    assert _h2(all_page) == ALL_TITLE, _h2(all_page)
    assert ALL_LINE in _text(all_page)
    own_id, own_text = _own_group_link(all_page)
    assert own_id == gid, f"the all-groups view does not link back to the admin's group {gid}: {own_id}"
    assert own_text.startswith("Show only ") and own_text.endswith("'s uploads"), own_text
    assert VIEW_ALL not in _text(all_page)
    _check_kept_links(all_page, admin=True)

    group_page = _get(s, f"/listSpecimenUploads.do?groupId={gid}")
    group_name = own_text[len("Show only "):-len("'s uploads")]
    assert _h2(group_page) == "Specimen uploads of " + group_name, _h2(group_page)
    assert ALL_LINE not in _text(group_page)
    assert re.search(r'<a class="uploads-all-groups" href="[^"]*/listSpecimenUploads\.do">' + re.escape(VIEW_ALL) + "</a>",
                     group_page), "no 'View all groups' uploads' link for the admin"
    _check_kept_links(group_page, admin=True)


@needs("uploader")
def test_curator_sees_which_view():
    s, _token, _login_id = login("uploader")
    gid, name = group_of(s)
    assert name == GROUP_NAME, (gid, name)

    group_page = _get(s, f"/listSpecimenUploads.do?groupId={gid}")
    assert _h2(group_page) == "Specimen uploads of " + GROUP_NAME, _h2(group_page)
    assert VIEW_ALL not in _text(group_page), "the 'View all groups' uploads' link is for admins only"
    _check_kept_links(group_page, admin=False)

    all_page = _get(s, "/listSpecimenUploads.do")
    assert _h2(all_page) == ALL_TITLE, _h2(all_page)
    assert ALL_LINE in _text(all_page)
    own_id, own_text = _own_group_link(all_page)
    assert own_id == gid and own_text == f"Show only {GROUP_NAME}'s uploads", (own_id, own_text)
    _check_kept_links(all_page, admin=False)
