"""Checks of the 2026-10-21 fixes that need no server or database.

1. Every SQL update of the login table in src/ keeps login.created ("created = created"): the
   column is ON UPDATE CURRENT_TIMESTAMP in production unless
   db/upgrade/8.113/2026-10-21-login-created.sql has been run.
2. Nothing in src/ deletes all rows of object_edit or object_hist (page text and its history);
   the unused DescEditDb.populateObjectEdit() / emptyObjectEdit() are gone.
3. Page titles: no "Subegion" (subregion.jsp says "Subregion: ...").
4. The old login page copy web/curate/manageLogin.jsp and manageLogin-body.jsp are gone and
   nothing refers to them.
5. At phone width the admin pages' column of links (div.right next to div.admin_left) does not
   float: admLayout.jsp sets the viewport, admin_style.css and responsive.css have the rules.
6. (2026-10-26, public-pages finding F1) Every "Too busy for bots." answer is returned: the
   ActionForward of HttpUtil.sendMessage() was ignored and the page was built anyway.
7. (2026-10-26, finding F2) The limits on simultaneous maps, comparisons and field guides use
   ConcurrencyLimiter (thread-safe), skip logged-in users and release only a slot they took.
8. (2026-10-26, finding B44) The nightly writes the list totals of every valid adm1, also those that are not
   live (GeolocaleDb.updateAdm1Counts): their pages showed numbers years old next to a current list.

Run: python3 tests/unit/test_small_fixes.py   (also in .github/workflows/tests.yaml)
"""
import os
import re

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def files(top, extensions):
    for dirpath, _dirs, names in os.walk(os.path.join(ROOT, top)):
        for name in names:
            if name.endswith(extensions):
                yield os.path.join(dirpath, name)


def read(path):
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read()


def java_sources():
    return {os.path.relpath(p, ROOT): read(p) for p in files("src", (".java",))}


def sql_text(java):
    """The Java source with string literals joined ("a" + "b" -> "ab"), comments removed."""
    java = re.sub(r"/\*.*?\*/", " ", java, flags=re.S)
    java = re.sub(r"//[^\n]*", " ", java)
    return re.sub(r'"\s*\+\s*"', "", java)


def test_login_updates_keep_created(sources):
    checked = 0
    problems = []
    for path, src in sources.items():
        text = sql_text(src)
        for m in re.finditer(r'"[^"]*\bupdate\s+login\s[^"]*"', text, re.I):
            stmt = m.group(0)
            if re.search(r"update\s+login\s+(l\s+)?set\b", stmt, re.I) is None:
                continue
            checked += 1
            set_part = re.split(r"\bwhere\b", stmt, flags=re.I)[0]
            if not re.search(r"\b(l\.)?created\s*=\s*(l\.)?created\b", set_part, re.I):
                problems.append(f"{path}: {stmt[:160]}")
    assert checked >= 10, f"only {checked} login updates found"
    assert not problems, "update of login without 'created = created':\n" + "\n".join(problems)
    print(f"{checked} updates of login keep created.")


def test_no_delete_all_page_text(sources):
    problems = []
    for path, src in sources.items():
        text = sql_text(src)
        for m in re.finditer(r'"\s*(delete\s+from|truncate(\s+table)?)\s+(object_edit|object_hist)\b([^"]*)"', text, re.I):
            if not re.search(r"\bwhere\b", m.group(4), re.I):
                problems.append(f"{path}: {m.group(0)}")
    assert not problems, "deletes all page text:\n" + "\n".join(problems)
    desc = sources[os.path.join("src", "org", "calacademy", "antweb", "home", "DescEditDb.java")]
    for name in ("populateObjectEdit", "emptyObjectEdit", "insertObjectEdit"):
        assert name not in desc, name + " is back in DescEditDb"
    print("No statement deletes all of object_edit or object_hist.")


def test_titles():
    found = [p for p in files("web", (".jsp", ".jspf")) if "Subegion" in read(p)]
    assert not found, found
    assert 'String titleStr = "Subregion: "' in read(os.path.join(ROOT, "web", "subregion.jsp"))
    print("Page titles: no 'Subegion'.")


def test_old_manage_login_page_gone():
    for name in ("manageLogin.jsp", "manageLogin-body.jsp"):
        assert not os.path.exists(os.path.join(ROOT, "web", "curate", name)), name
    refs = []
    for top in ("src", "web", "WEB-INF", "docker"):
        for p in files(top, (".java", ".jsp", ".jspf", ".xml", ".properties", "Caddyfile", ".common", ".staging")):
            # A path in quotes (include, tiles value, action forward); old notes in comments do not count.
            if re.search(r"[\"'](/?curate/|/?web/curate/)?manageLogin(-body)?\.jsp", read(p)):
                refs.append(os.path.relpath(p, ROOT))
    assert not refs, refs
    print("manageLogin.jsp and manageLogin-body.jsp are gone and not referred to.")


def test_admin_links_column_stacks_on_phones():
    css = read(os.path.join(ROOT, "web", "common", "responsive.css"))
    start = css.index("@media (max-width: 760px)")
    phone = css[start:]
    for selector in (".admin_left ~ .right", ".right:has(~ .admin_left)"):
        m = re.search(re.escape(selector) + r"\s*\{([^}]*)\}", phone)
        assert m, selector + " missing in the phone rules"
        assert "float: none" in m.group(1), selector
    # The :has() rule is on its own: a browser without :has() would drop a whole selector list.
    assert not re.search(r",\s*\.right:has|\.right:has\([^)]*\)\s*,", phone)
    # The admin pages use admLayout.jsp (not layout.jsp): it must set the viewport to the device
    # width (else phones lay the page out 980 px wide and no max-width rule applies) and
    # admin_style.css must have the same rules.
    adm = read(os.path.join(ROOT, "web", "layouts", "admLayout.jsp"))
    assert '<meta name="viewport" content="width=device-width, initial-scale=1"/>' in adm
    assert "/common/admin_style.css" in adm
    admin_css = read(os.path.join(ROOT, "web", "common", "admin_style.css"))
    phone = admin_css[admin_css.index("@media (max-width: 760px)"):]
    for selector in (".admin_left ~ .right", ".right:has(~ .admin_left)", ".admin_left", "#admin_layout"):
        m = re.search(r"(^|\n)\s*" + re.escape(selector) + r"\s*\{([^}]*)\}", phone)
        assert m, selector + " missing in admin_style.css phone rules"
    for selector in (".admin_left ~ .right", ".right:has(~ .admin_left)", ".admin_left"):
        m = re.search(r"(^|\n)\s*" + re.escape(selector) + r"\s*\{([^}]*)\}", phone)
        assert "float:none" in m.group(2).replace(" ", ""), selector
    print("Admin link column stacks at phone width.")


def test_too_busy_for_bots_is_returned(sources):
    found = 0
    for path, src in sources.items():
        for line in src.splitlines():
            if "tooBusyForBots(" in line and "sendMessage(" in line:
                found += 1
                assert "return HttpUtil.sendMessage(" in line, f"{path}: {line.strip()}"
    assert found >= 11, found
    print(f"Too busy for bots: {found} answers returned.")


def test_simultaneous_limits(sources):
    for name in ("BigMapAction.java", "BrowseAction.java", "search/FieldGuideAction.java"):
        path = "src/org/calacademy/antweb/" + name
        src = sources[path]
        assert "ConcurrencyLimiter" in src, path
        assert "LoginMgr.getAccessLogin(request)" in src, path
        assert ".release()" in src and ".tryAcquire()" in src, path
        assert not re.search(r"(\+\+|--)\s*s_\w*(Count|Executes)\b", src), path
    print("Simultaneous limits are thread-safe and skip logged-in users.")


def test_adm1_totals_not_only_live(sources):
    src = sources["src/org/calacademy/antweb/home/GeolocaleDb.java"]
    body = src[src.index("public void updateAdm1Counts()"):]
    body = body[:body.index("public void updateCountryCounts()")]
    assert "isLive()" in body and "} else {" in body, "updateAdm1Counts: no branch for adm1s that are not live"
    other = body[body.index("} else {"):]
    assert "updateCountableTaxonData(" in other and "updateValidSpeciesCount(" in other, other[:400]
    print("Adm1s that are not live get their list totals.")


def main():
    sources = java_sources()
    assert len(sources) > 400, len(sources)
    test_login_updates_keep_created(sources)
    test_no_delete_all_page_text(sources)
    test_titles()
    test_old_manage_login_page_gone()
    test_admin_links_column_stacks_on_phones()
    test_too_busy_for_bots_is_returned(sources)
    test_simultaneous_limits(sources)
    test_adm1_totals_not_only_live(sources)


if __name__ == "__main__":
    main()
