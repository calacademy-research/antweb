"""World Ant Collections pages (kept on 2026-10-19, Brian's decision) and their photos.

Anonymous, read-only, any host: the overview and the six museum pages load, and every photo they
show from /wac/ is served (no broken image).
Static (any host): every web/wac/ file the pages reference exists; the overview links the museum
pages through their .do actions; the Copenhagen profile photo links the specimen's large image in
AntWeb's image store; the Genoa drawer photo has no link to a missing large version; the missing
Berlin portrait (Julius Roger) is not shown.
"""
import os
import re

import pytest

from antweb import BASE_URL, TIMEOUT, classify, new_session

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
MUSEUMS = ("basel", "berlin", "copenhagen", "geneva", "genoa", "oxford")


def _src(path):
    return open(os.path.join(ROOT, path), encoding="utf-8", errors="replace").read()


def _visible(text):
    return re.sub(r"<%--.*?--%>", "", text, flags=re.S)


@pytest.mark.parametrize("name", ("worldants",) + MUSEUMS)
def test_page_loads_with_its_photos(name):
    s = new_session()
    r = s.get(f"{BASE_URL}/{name}.do", timeout=TIMEOUT)
    assert r.status_code == 200 and classify(r).category == "ok", (r.status_code, classify(r))
    srcs = sorted(set(re.findall(r"""<img[^>]+src=["']?(?:[^"' >]*/)?(wac/[^"' >]+)""", r.text)))
    assert srcs, f"{name}: no /wac/ photos found"
    for src in srcs:
        img = s.get(f"{BASE_URL}/{src}", timeout=TIMEOUT)
        assert img.status_code == 200 and img.headers.get("Content-Type", "").startswith("image/"), \
            f"{name}: {src} -> {img.status_code} {img.headers.get('Content-Type')}"


@pytest.mark.parametrize("name", ("worldants",) + MUSEUMS)
def test_referenced_wac_files_exist(name):
    path = "web/worldants.jsp" if name == "worldants" else f"web/{name}-body.jsp"
    for ref in sorted(set(re.findall(r"wac/[A-Za-z0-9_.-]+", _visible(_src(path))))):
        assert os.path.isfile(os.path.join(ROOT, "web", ref)), f"{path}: {ref} is missing"


def test_overview_links_the_museum_actions():
    text = _src("web/worldants.jsp")
    for m in MUSEUMS:
        assert f"/{m}.do\"" in text, m
        assert f'href="/{m}.jsp"' not in text, m


def test_copenhagen_large_profile_from_image_store():
    text = _visible(_src("web/copenhagen-body.jsp"))
    assert "static.antweb.org/images/casent0102533/casent0102533_p_1_high.jpg" in text
    assert "CASENT0102533_P_lg.jpg" not in text


def test_genoa_and_berlin_missing_photos_not_linked():
    assert "MSNG054_lg.jpg" not in _visible(_src("web/genoa-body.jsp"))
    assert "Roger-Julius" not in _visible(_src("web/berlin-body.jsp"))
