"""Source checks of the 2026-10-27 fixes to public pages, search, image upload and server scripts (no server needed).

Pages and search:
1. Pages filled by an action do not copy request values onto the taxon or specimen bean
   (jsp:setProperty property="*"): a link could change what a specimen or taxon page shows.
2. The advanced search results page does not write the last search's SQL into the page.
3. The comparison views accept only the five view names for "shot".
4. Values written into links and lists are escaped: search form select lists, the field guide title,
   the page's own address in links, group and artist names, uploaded file names.
5. imageUploader.jsp stops after sending a login without upload permission away.
6. DNA extraction notes use their own search type; an unknown group name finds nothing (it was an SQL error).
7. Bad numbers in a link (limit, offset, length) do not give an error page; a locality without coordinates
   shows none (not 0.0, 0.0); the determination date line copes with a date that did not parse.
8. The home page's recent images list is built in one query, holds at most 5 names, and replaces the old
   file only when it is complete.

Server scripts and CI:
9. The daily and monthly database dumps fail on a mysqldump error (pipefail) or an incomplete dump, and old
   dumps are deleted only after a good one (the behaviour itself: tests/unit/test_dump_status.py).
10. The Caddyfile check fails for either file; the Java test steps check JUnit's OK line.
11. The e2e queue has one name for staging; the dev Java debugger port is local only; server-only
    settings files are kept out of the image; a failed staging rollback leaves AntWeb stopped;
    admin.sh finds its scripts; the link scripts delete only links in a folder named links.

Run: python3 tests/unit/test_page_ops_fixes.py   (also in .github/workflows/tests.yaml)
"""
import os
import re

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8", errors="replace") as f:
        return f.read()


def code(src):
    """Java/JSP source without // line comments."""
    return "\n".join(l for l in src.splitlines() if not l.strip().startswith("//"))


BEAN_PAGES = [
    "web/specimen.jsp", "web/specimenImages.jsp", "web/showBrowse.jsp", "web/showBrowse-body.jsp",
    "web/taxonPage.jsp", "web/imagePage.jsp", "web/homonymPage.jsp", "web/homonymPage-body.jsp",
    "web/comparison-body.jsp", "web/mapComparison-body.jsp", "web/bigPicture-body.jsp", "web/oneView-body.jsp",
]


def test_no_request_values_on_beans():
    pat = re.compile(r'<jsp:setProperty\s+name="(taxon|specimen|theImageTaxon)"\s+property="\*"')
    for p in BEAN_PAGES:
        assert not pat.search(read(p)), p + ": request values are copied onto the taxon/specimen bean again"
    print("No jsp:setProperty property=* on the taxon and specimen beans of", len(BEAN_PAGES), "pages.")


def test_pages():
    assert "AdvancedSearch.getQuery()" not in read("web/search/advancedSearchResults-body.jsp")
    for p in ("web/oneView-body.jsp", "web/search/multiTaxaOneView-body.jsp"):
        assert "!labelMap.containsKey(shot)) shot = \"Head\"" in read(p), p + ": shot is not limited to the view names"
    jsp = read("web/imageUploader/imageUploader.jsp")
    assert re.search(r'sendRedirect\("permissionDenied\.jsp"\);\s*return;', jsp), "imageUploader.jsp goes on after the redirect"
    fg = read("web/search/fieldGuide-body.jsp")
    assert fg.index("if (fieldGuide == null)") < fg.index("fieldGuide.getShowTaxon()"), "field guide used before its null check"
    assert "Html.esc(fieldGuide.getTitle())" in fg
    sb = read("web/showBrowse-body.jsp")
    assert "Html.escAttr(allUrl)" in sb and "Html.escAttr(showAllLink)" in sb
    assert "Integer.parseInt(limitStr.trim())" in sb and "NumberFormatException" in sb
    assert "Html.escAttr(HttpUtil.removeParam(thisTarget, \"orderBy\"))" in read("web/taxonReportBody.jsp")
    tp = read("web/taxonomicPage-body.jsp")
    assert "<%= thisTarget %>" not in tp and "Html.url(overview.getName())" in tp
    rs = read("web/search/recentSearchResults-body.jsp")
    assert "if (intLength < 1) intLength = 50;" in rs and "NumberFormatException" in rs
    for p in ("web/locality-body.jsp", "web/collection-body.jsp"):
        assert "noCoordinates ? \"\"" in read(p), p + ": a locality without coordinates shows 0.0"
    assert "Species<a>" not in read("web/locality-body.jsp")
    assert "java.util.Objects.equals(specimen.getDateDetermined()" in read("web/specimen-body.jsp")
    assert "Html.esc(artist.getName())" in read("web/imageUploader/imageUploader-body.jsp")
    assert "Html.esc(group.getName())" in read("web/group-body.jsp") and "groupName=<%= group.getName()" not in read("web/group-body.jsp")
    assert "Html.esc(descEdit.getAccessLogin().getName())" in read("web/descEditReport-body.jsp")
    lu = read("web/imageUploader/listImageUploads-body.jsp")
    assert "list.isEmpty() && message.contains(\"curator.do\")" in lu and "</a></td></tr>\");" in lu
    print("Pages: no SQL in the page, view names only, values escaped, bad numbers handled.")


def test_search():
    sif = read("src/org/calacademy/antweb/search/SearchIncludeFactory.java")
    for name in ("bioregion", "country", "adm1"):
        assert "Html.escAttr(%s)" % name in sif and "Html.esc(%s)" % name in sif, name + " not escaped in the search form"
    assert "this.dnaExtractionNotesSearchType = dnaExtractionNotesSearchType;" in read("src/org/calacademy/antweb/search/AdvancedSearch.java")
    gs = code(read("src/org/calacademy/antweb/search/GenericSearch.java"))
    i = gs.index('property.contains("groupName")')
    assert 'returnStr = " 1 = 0 ";' in gs[i:i + 600], "an unknown group name is not 'no specimens'"
    print("Search: select lists escaped, DNA search type, unknown group.")


def test_recent_images():
    src = code(read("src/org/calacademy/antweb/imageUploader/ImageUploaderAction.java"))
    body = src[src.index("public void writeRecentImages("):]
    assert "currItems < homepageMaxRecent" in body and "currItems <= homepageMaxRecent" not in body
    assert "getRecentImagesList" not in src and "\"'\" + code" not in src, "codes pasted into SQL again"
    assert "ATOMIC_MOVE" in body and "new FileWriter" not in body, "the include is emptied before the list is built"
    print("Recent images: one query, at most 5, replaced only when complete.")


def test_dump_scripts():
    for p in ("bin/db-daily-dump.sh", "bin/db-monthly-dump.sh"):
        s = read(p)
        assert "set -o pipefail" in s and "exec bash" in s, p + ": a failed mysqldump is not noticed"
        assert "-- Dump completed" in s, p + ": an incomplete dump is not noticed"
        prune = s.index("find \"$backupdir\"")
        assert s.rfind("exit 1", 0, prune) > 0, p
    daily = read("bin/db-daily-dump.sh")
    assert "--single-transaction" in daily
    # The weekly prune is inside the branch that wrote a good dump.
    assert daily.index("find \"$backupdir\"") < daily.index("else\n  status failed")
    print("Dump scripts: pipefail, completeness check, prune after a good dump.")


def test_ci_and_server_scripts():
    tests = read(".github/workflows/tests.yaml")
    step = tests[tests.index("- name: Check Caddyfiles"):]
    step = step[:step.index("- name:", 10)]
    assert "|| { echo \"::error::$f" in step and "&& echo \"$f OK\"" not in step
    for cls in ("SecurityBatch7Test", "UploadSchedulerHardeningTest", "MountCheckTest", "ImageUploadSaveTest"):
        j = tests.index("TestRunner test.org.calacademy.antweb.", tests.index(cls + ".java"))
        after = tests[j:j + 400]
        assert "grep -q \"^OK (" in after, cls + ": the step passes without JUnit's OK line"
    e2e = read(".github/workflows/e2e.yaml")
    assert "group: e2e-${{ inputs.antweb_url || 'https://staging.antweb.org' }}" in e2e
    assert "ANTWEB_URL: ${{ inputs.antweb_url || 'https://staging.antweb.org' }}" in e2e
    for p in ("docker-compose.yml", "docker-compose.dev.yml", "docker-compose.prod.yml", "docker-compose.stage.yml"):
        for port in re.findall(r'^\s*-\s*"([^"]+)"', read(p), re.M):
            if re.fullmatch(r"\d+:\d+", port):
                assert port in ("80:80", "443:443"), p + ": port " + port + " open to the network"
    ignore = read("docker/antweb/Dockerfile.dockerignore").splitlines()
    for line in read(".gitignore").splitlines():
        m = re.fullmatch(r"/(etc/[\w.-]+\.properties)", line.strip())
        if m:
            assert m.group(1) in ignore, m.group(1) + " (server-only) would be copied into the image"
    refresh = read("tools/staging/refresh-from-prod.sh")
    rb = refresh[refresh.index("do_rollback() {"):]
    assert "if ! load_dump \"$SAFETY\"; then" in rb[:1500] and "AntWeb is left stopped" in rb[:1500]
    admin = read("bin/admin.sh")
    assert admin.index('cd "$(dirname "$0")"') < admin.index("unclosedConnections.sh\n")
    for p in ("bin/links.sh", "bin/linksDocker.sh"):
        s = read(p)
        assert not re.search(r"^rm \*", s, re.M) and '= links ]' in s, p + ": deletes the files of any folder"
    assert '--exclude="etc/*.properties"' in read("bin/srcBackup.sh")
    print("CI and server scripts: Caddy check, JUnit OK lines, e2e queue, local ports, image contents, rollback, admin.sh, links.")


def main():
    test_no_request_values_on_beans()
    test_pages()
    test_search()
    test_recent_images()
    test_dump_scripts()
    test_ci_and_server_scripts()


if __name__ == "__main__":
    main()
