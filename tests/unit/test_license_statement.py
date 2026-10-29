#!/usr/bin/env python3
"""One licence for AntWeb images and data: CC BY 4.0 (decision of Brian Fisher, 2026-10-29).

The statement lives in src/org/calacademy/antweb/util/License.java and web/common/licenseStatement.jsp. Before
2026-10-29 the site said four different things: CC BY 3.0 (About, Citing), CC BY 4.0 (footer), CC BY-SA 3.0 (image
uploader) and BY-SA plus GFDL (upload report, image_upload.license), with a BY-NC-SA 1.0 RDF block in the footer.
This fails if an older Creative Commons version or variant comes back anywhere in src/ or web/, and checks that the
pages that state a licence use the shared statement.
"""
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
EXTS = (".java", ".jsp", ".jspf", ".html", ".htm", ".js", ".pl", ".py", ".properties", ".xml", ".txt", ".sh")
# Third-party code that carries its own (software) licences.
SKIP_DIRS = ("web/ckeditor", "web/common/editors", "src/com/thebuzzmedia")

OLD = [
    (r"creativecommons\.org/licenses/(?!by/4\.0/)[a-z-]+/\d", "a Creative Commons licence URL other than by/4.0"),
    (r"\bCC[ -]?BY[ -]?(SA|NC|ND)\b", "CC BY-SA / BY-NC / BY-ND"),
    (r"\bBY-(SA|NC|ND)\b", "BY-SA / BY-NC / BY-ND"),
    (r"(?i)Attribution-(ShareAlike|NonCommercial|NoDerivs)", "Attribution-ShareAlike / NonCommercial / NoDerivs"),
    (r"(?i)\bcc-by-sa", "cc-by-sa"),
    (r"(?i)Free Documentation License|\bGFDL\b", "GNU Free Documentation License"),
    (r"(?i)Creative Commons Attribution (License|[0-3]\.\d)", "an unversioned or older 'Creative Commons Attribution' name"),
    (r"(?i)\bCC[ -]?BY[ -]?[0-3]\.\d", "CC BY 1.0 to 3.0"),
]

STATEMENT = ("Images and data on AntWeb are licensed under \" + NAME + \" (\" + FULL_NAME\n"
             "            + \"). Cite AntWeb and the photographer or collection.")
INCLUDE = '<jsp:include page="/common/licenseStatement.jsp" flush="true"/>'
USERS = ["web/common/copyright.jsp", "web/about-body.jsp", "web/citing_antweb-body.jsp",
         "web/imageUploader/imageUploader-body.jsp", "web/imageUploader/imageUploadReport-body.jsp"]


def read(rel):
    with open(os.path.join(ROOT, rel), encoding="utf-8", errors="replace") as f:
        return f.read()


def files():
    for top in ("src", "web"):
        for d, dirs, names in os.walk(os.path.join(ROOT, top)):
            rel_d = os.path.relpath(d, ROOT).replace(os.sep, "/")
            if any(rel_d == s or rel_d.startswith(s + "/") for s in SKIP_DIRS):
                dirs[:] = []
                continue
            for n in names:
                if n.endswith(EXTS) and not n.endswith(".min.js"):
                    yield rel_d + "/" + n


def main():
    bad = []
    for rel in files():
        for i, line in enumerate(read(rel).splitlines(), 1):
            for pat, what in OLD:
                if re.search(pat, line):
                    bad.append(f"{rel}:{i}: {what}: {line.strip()[:120]}")
    assert not bad, "older licence wording remains (the site licence is CC BY 4.0):\n" + "\n".join(bad)

    java = read("src/org/calacademy/antweb/util/License.java")
    assert 'URL = "https://creativecommons.org/licenses/by/4.0/"' in java, "License.URL must be the CC BY 4.0 deed"
    assert 'NAME = "CC BY 4.0"' in java and 'FULL_NAME = "Creative Commons Attribution 4.0 International"' in java
    assert STATEMENT in java, "License.STATEMENT changed: update this test with the decided wording"
    assert "License.html()" in read("web/common/licenseStatement.jsp"), "licenseStatement.jsp must print License.html()"
    for rel in USERS:
        assert INCLUDE in read(rel), f"{rel} must state the licence with {INCLUDE}"
    assert "util.License.LINE" in read("src/org/calacademy/antweb/imageUploader/ImageUpload.java"), \
        "new image uploads must record License.LINE in image_upload.license"
    assert "License.LINE" in read("web/bigPicture-body.jsp"), "the image page's citation box must use License.LINE"
    print("One licence statement (CC BY 4.0); no older Creative Commons wording in src/ or web/.")


if __name__ == "__main__":
    try:
        main()
    except AssertionError as e:
        print("FAIL:", e)
        sys.exit(1)
