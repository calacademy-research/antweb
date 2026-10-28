"""Source checks of the 2026-10-27 upload and nightly fixes that need no server or database.

1. The upload flag: the nightly TaxonWorks job and the nightly name update claim it
   (UploadAction.claimUploadProcess) and release only their own (releaseUploadProcess); none of them
   sets or clears it directly. The TaxonWorks job claims it before it writes the group's working files.
2. A refused "upload as" is refused, never uploaded to the login's own group.
3. A specimen upload that saved no line, or that a line stopped, is rolled back (forward page set),
   not committed with the group's specimens removed.
4. The AntCat bioregion rows are removed only after the AntCat file has passed its check.
5. Deleting images never shows the exception text; the curator file upload links the saved file.
6. Removing a name from a country list deletes the taxon itself only for a morphotaxon.
7. While the nightly run is working, a data job started by hand is refused, also between two sets.
8. Smaller ones: ImageCountDb names no database, getProjectCount does not answer 0 on an error,
   the autocomplete links are escaped, future collection dates have their own report section.

Run: python3 tests/unit/test_upload_nightly_safety.py   (also in .github/workflows/tests.yaml)
"""
import os
import re

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
SRC = os.path.join(ROOT, "src", "org", "calacademy", "antweb")


def read(*parts):
    with open(os.path.join(SRC, *parts), encoding="utf-8", errors="replace") as f:
        return f.read()


def code(text):
    """The source without comments."""
    text = re.sub(r"/\*.*?\*/", " ", text, flags=re.S)
    return re.sub(r"//[^\n]*", " ", text)


def method(text, signature):
    start = text.index(signature)
    depth = 0
    for i in range(text.index("{", start), len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return text[start:i + 1]
    raise AssertionError("no end of " + signature)


def test_upload_flag_claimed_and_released():
    tw = code(read("upload", "TaxonWorksNightly.java"))
    nu = code(read("upload", "SpecimenNameUpdate.java"))
    for name, src in (("TaxonWorksNightly", tw), ("SpecimenNameUpdate", nu)):
        assert "setIsInUploadProcess" not in src, name + " sets the upload flag directly"
        assert "claimUploadProcess(" in src and "releaseUploadProcess(" in src, name
    run_once = method(tw, "private static String runOnce(")
    assert run_once.index("claimUploadProcess(") < run_once.index("prepareAndUpload("), \
        "TaxonWorksNightly must claim the upload flag before it writes the working files"
    assert "prepareFromArchive(" not in run_once
    assert "volatile String s_isInProcess" in read("upload", "UploadAction.java")


def test_refused_upload_as_is_refused():
    src = code(read("upload", "UploadAction.java"))
    sub = method(src, "private ActionForward submitSpecimenUpload(")
    block = sub[sub.index("if (!isAllowedUploadAs("):]
    block = block[:block.index("}") + 1]
    assert "curateAs = null" not in block
    assert 'findForward("message")' in block


def test_stopped_specimen_upload_rolls_back():
    src = code(read("upload", "SpecimenUpload.java"))
    m = re.search(r"catch \(AntwebException e\) \{(.*?)\n\s*\} catch \(TestException", src, re.S)
    assert m, "catch block not found"
    assert 'setForwardPage("message")' in m.group(1)
    assert "return getUploadDetails();" in m.group(1)


def test_antcat_bioregion_rows_removed_after_the_check():
    src = code(read("upload", "SpeciesListUpload.java"))
    imp = method(src, "private UploadDetails importSpeciesList(String project, UploadFile uploadFile, int accessGroupId, boolean singleUpload)")
    assert imp.index("validateWorldantsFile(") < imp.index('deleteSource("antcat")')
    reload = method(src, "public UploadDetails reloadSpeciesList(String project, int accessGroupId)")
    assert "project_name = '\" + project" not in reload and "setString(1, project)" in reload


def test_image_delete_and_curator_file_messages():
    src = code(read("upload", "DeleteImagesAction.java"))
    assert '"e:" + e' not in src
    src = code(read("upload", "DescEditImageUploadAction.java"))
    assert "String fileName = requestedName;" in src
    assert "makeDirTree" not in src


def test_country_list_removal_deletes_only_morphotaxa():
    src = code(read("home", "GeolocaleTaxonDb.java"))
    d = method(src, "public int delete(String speciesListName, String taxonName)")
    assert re.search(r"if \(rset2\.getInt\(1\) == 0 && Taxon\.isMorpho\(taxonName\)( && !taxonDb\.isOnAnyList\(taxonName\))?\)", d)
    ins = method(src, "private int insertGeolocaleTaxaFromSpecimens()")
    assert "insertCount += count" not in ins and "unfoundCountries.add(countryName)" in ins


def test_hand_jobs_refused_while_the_nightly_runs():
    src = code(read("util", "UtilDataAction.java"))
    assert re.search(r"!isScheduler && Scheduler\.isRunning\(\)", src)
    assert "volatile String m_inComputeProcess" in src


def test_smaller_fixes():
    assert "ant.specimen" not in code(read("home", "ImageCountDb.java"))
    pc = method(code(read("home", "ProjTaxonDb.java")), "private int getProjectCount(")
    catch = pc[pc.index("catch (SQLException e)"):]
    assert "return 1;" in catch[:catch.index("finally")]
    names = method(code(read("home", "TaxonDb.java")), "public List<String> getTaxonNames(String text, boolean asHtml)")
    assert "Html.url(taxonName)" in names and "Html.esc(taxonName)" in names
    vd = method(code(read("upload", "AntwebUpload.java")), "private boolean validDate(")
    assert "futureDateCollected" in vd


if __name__ == "__main__":
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
    for t in tests:
        t()
        print("ok", t.__name__)
    print("OK (%d tests)" % len(tests))
