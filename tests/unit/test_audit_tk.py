"""Audit fixes of 2026-10-30 (triage tk) in scripts and pages, without a server.

  - antweb-fix-perms (root, at every container start) chowns with find -execdir, so a folder swapped for a link
    between find and chown is not followed;
  - bin/taxonSetBackup.sh keeps no empty or partial file when mysql fails, says "failed" and exits 1; with a working
    mysql it writes the four dated files and says "ok";
  - pages: specimen codes in onclick handlers are URL-encoded and escaped; genus and subfamily rows of the search by
    taxon are escaped; the red-flag issue is escaped in its title; the autocomplete answers are UTF-8 plain text;
    the recent images pager has no empty last page; the images page of a specimen without images does not fail.

Run: python3 tests/unit/test_audit_tk.py   (also in .github/workflows/tests.yaml)
"""
import os
import shutil
import subprocess
import tempfile

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def read(p):
    with open(os.path.join(ROOT, p), encoding="utf-8") as f:
        return f.read()


def test_fix_perms_execdir():
    s = read("docker/antweb/fix-perms.sh")
    assert "-execdir chown -h" in s, "fix-perms does not use find -execdir"
    assert "-exec chown" not in s, "fix-perms still chowns full paths"
    assert "\nPATH=/usr/local/sbin:" in s, "fix-perms needs an absolute PATH for -execdir"
    print("fix-perms: chown with find -execdir.")


def run_backup(mysql_body):
    with tempfile.TemporaryDirectory() as d:
        for sub in ("bin", "etc", "fakebin"):
            os.makedirs(os.path.join(d, sub))
        bak = os.path.join(d, "bak")
        for t in ("geolocale", "project", "museum", "bioregion"):
            os.makedirs(os.path.join(bak, t))
        shutil.copy(os.path.join(ROOT, "bin", "taxonSetBackup.sh"), os.path.join(d, "bin"))
        with open(os.path.join(d, "etc", "db.properties"), "w") as f:
            f.write("user=antweb\npassword=test\n")
        with open(os.path.join(d, "fakebin", "mysql"), "w") as f:
            f.write("#!/bin/sh\n" + mysql_body)
        os.chmod(os.path.join(d, "fakebin", "mysql"), 0o755)
        env = dict(os.environ, PATH=os.path.join(d, "fakebin") + os.pathsep + os.environ["PATH"], TAXONSET_BAK_DIR=bak)
        r = subprocess.run(["sh", os.path.join(d, "bin", "taxonSetBackup.sh")], env=env, capture_output=True, text=True,
                           timeout=60)
        files = sorted(os.path.join(t, n) for t in os.listdir(bak) for n in os.listdir(os.path.join(bak, t)))
        return r, files


def test_taxon_set_backup():
    if os.path.exists("/antweb/deploy/etc/db.properties"):
        print("skipped: a server's db.properties is here")
        return
    r, files = run_backup('echo "partial row"\nexit 1\n')
    assert r.returncode == 1, (r.returncode, r.stdout, r.stderr)
    assert "failed: geolocale project museum bioregion" in r.stdout, r.stdout
    assert files == [], files
    r, files = run_backup(': > /dev/null\nexit 0\n')   # succeeds but writes nothing
    assert r.returncode == 1 and files == [], (r.stdout, files)
    r, files = run_backup('echo "name\ttaxon_name"\necho "Madagascar\tx"\n')
    assert r.returncode == 0 and "ok" in r.stdout, (r.returncode, r.stdout, r.stderr)
    assert len(files) == 4 and not any(f.endswith(".tmp") for f in files), files
    print("taxonSetBackup: no file kept on a failure, \"failed\" and status 1; four files and \"ok\" otherwise.")


def test_pages():
    for p in ("web/specimen-body.jsp", "web/search/fieldGuide-body.jsp", "web/taxonPage-body.jsp", "web/taxonChildImageSet.jsp"):
        s = read(p)
        for raw in ("name=<%= specimen.getName() %>", "name=<%= image.getCode() %>", "getCode() %>&shot=h",
                    "name=<%= imageRoot %>", "name=<%= thisChild.getCode() %>", 'value="<%= thisChild.getName() %>"'):
            assert raw not in s, p + ": " + raw
    s = read("web/search/multiTaxaOneView-body.jsp")
    assert '"/bigPicture.do?name=" + Html.url(code)' in s and "<%= specimenUrl %>" not in s
    s = read("web/search/advancedSearchResultsByTaxon-body.jsp")
    assert "name=<%= genus %>" not in s and "name=<%= subfamily %>" not in s
    assert "Html.url(genus)" in s and "Html.url(subfamily)" in s
    assert "Html.escAttr(thisChild.getIssue())" in read("web/specimenReport.jsp")
    for p in ("web/search/autoCompleteKeys.jsp", "web/search/autoCompletePlaceKeys.jsp"):
        assert '<%@page contentType="text/plain; charset=UTF-8"%>' in read(p), p
    s = read("web/search/recentSearchResults-body.jsp")
    assert "(searchResults.getResults().size() + intLength - 1) / intLength" in s and "the_page < numNexts-1" not in s
    s = read("web/specimenImages-body.jsp")
    assert "if (imagesHash == null) imagesHash = new Hashtable();" in s
    assert "imagesHash.keySet().toArray" not in s
    s = read("web/taxonReportBody.jsp")
    assert "asterixTitle = asterixNote;" in s
    assert read("web/taxonPage-body.jsp").count("Image files (taxon.image_count, labels included)") == 1
    assert "<s:reset>" not in read("web/search/advancedSearch-body.jsp")
    print("Pages: codes and names escaped, autocomplete UTF-8, pager, images page without images.")


def main():
    test_fix_perms_execdir()
    test_taxon_set_backup()
    test_pages()
    print("OK")


if __name__ == "__main__":
    main()
