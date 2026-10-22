"""Pages that answered differently when two requests came at once (found by the first parallel run, 2026-10-22).

1. upload.do checked "an upload is in process" / "a data job is running" before the login, so an anonymous
   visitor got that message instead of the login page while a curator's upload ran. The login and permission
   checks now come first (static check of UploadAction.execute).
2. artists.do and curators.do sorted the shared, in-memory list inside the page; two requests at once (two
   visitors sorting differently) ended on the error page (ConcurrentModificationException). The actions now
   pass a copy, and the Image Uploader and group pages sort a copy (static), and concurrent requests all
   answer normally (anonymous, read-only, any host).
"""
import re
from concurrent.futures import ThreadPoolExecutor

from antweb import BASE_URL, BROKEN, REPO_ROOT, TIMEOUT, classify, new_session

SRC = REPO_ROOT / "src" / "org" / "calacademy" / "antweb"


def _read(path):
    return path.read_text(encoding="utf-8")


def test_upload_action_checks_login_before_busy():
    src = _read(SRC / "upload" / "UploadAction.java")
    body = src[src.index("public ActionForward execute("):]
    login = body.index('findForward("goToLogin")')
    assert login < body.index("Check.upload(request, mapping)"), "upload.do: the busy check comes before the login"
    assert login < body.index("Check.compute(request, mapping)"), "upload.do: the data-job check comes before the login"
    assert body.index("ADMIN_ONLY_ACTIONS.contains") < body.index("Check.upload(request, mapping)")


def test_shared_lists_are_not_sorted_in_place():
    assert "new ArrayList<>(shared)" in _read(SRC / "ArtistAction.java")
    assert "new ArrayList<>(shared)" in _read(SRC / "CuratorAction.java")
    web = REPO_ROOT / "web"
    for jsp, shared in (("imageUploader/imageUploader-body.jsp", "ArtistMgr.getArtists()"),
                        ("curate/viewGroup-body.jsp", "LoginMgr.getCurators()")):
        text = _read(web / jsp)
        assert not re.search(r"(sort\(|Collections\.sort\()\s*" + re.escape(shared), text), jsp
        assert f"new ArrayList<>({shared})" in text, f"{jsp} sorts the shared list"


def test_concurrent_sorted_lists_answer_normally():
    urls = ["/artists.do", "/artists.do?sortBy=name", "/artists.do?sortBy=specimenCount",
            "/curators.do", "/curators.do?sortBy=name", "/curators.do?sortBy=group"] * 2

    def fetch(url):
        r = new_session().get(BASE_URL + url, timeout=TIMEOUT)
        return url, classify(r)

    with ThreadPoolExecutor(max_workers=6) as pool:
        results = list(pool.map(fetch, urls))
    bad = [(u, o) for u, o in results if o.category in BROKEN or o.status != 200]
    assert not bad, bad
