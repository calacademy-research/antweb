"""One meaning of "Images" on every page (Brian Fisher, 2026-10-27: "We need to carry our definitions to every place:
consistency will keep us sane").

  Images       images of the ant; label images are counted separately ("5 Images + label") or in their own row;
  Image files  every uploaded file, label images included.

A page that prints an image count must use one of the getters below for the meaning it shows. This test scans every
JSP for image-count getters and fails on one that is not listed with its meaning, and on a number printed right
before the word "Images" outside the helpers (ImageCounts.text, ImagesAnywhere.imagesColumn), so a new "N Images"
that counts label images fails here. The definition line (web/common/imagesDefinition.jsp) is under every list and
table with an Images number.

Run: python3 tests/unit/test_image_counts_defined.py   (also in .github/workflows/tests.yaml)
"""
import os
import re

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
WEB = os.path.join(ROOT, "web")

GETTERS = re.compile(r"\.(getImageCount|getImageCountStr|getTotalImagesCount|getHasImagesCount|getImagesUploadedCount|getImageUploadCount)\(\)")

# (file, getter) -> what it prints. Every one is images of the ant, or labelled "Image files" (or not an image count).
ALLOWED = {
    ("artists-body.jsp", "getImageCount"): "Image files column (every file credited to the photographer)",
    ("curator-body.jsp", "getImageUploadCount"): "Image Uploads: upload batches, not images",
    ("curator-body.jsp", "getImagesUploadedCount"): "Image files uploaded",
    ("homonymPage-body.jsp", "getImageCount"): "admin data: Image files (taxon.image_count, labels included)",
    ("homonymPage-body.jsp", "getHasImagesCount"): "admin data: HasImageCount (deprecated, image files)",
    ("taxonPage-body.jsp", "getImageCount"): "admin data: Image files (taxon.image_count, labels included)",
    ("taxonPage-body.jsp", "getHasImagesCount"): "admin data: HasImageCount (deprecated, image files)",
    ("imageUploader/imageUploadReport-body.jsp", "getImageCount"): "Image files of the upload batch",
    ("imageUploader/listImageUploads-body.jsp", "getImageCount"): "Image files column of the upload batches",
    ("overview-body.jsp", "getImageCount"): "Image files row (before the nightly breakdown)",
    ("overviews-body.jsp", "getImageCount"): "Image files column and pie of the place lists",
    ("specimenReport.jsp", "getImageCount"): "a comment, and the unreached taxon branch: the list row's images of the ant",
    ("specimenReport.jsp", "getImageCountStr"): "the unreached taxon branch: the list row's images of the ant (nightly set 4)",
    ("taxonChildImageSet.jsp", "getImageCountStr"): "a comment",
    ("taxonReportBody.jsp", "getHasImagesCount"): "a comment",
    ("util/statsPage-body.jsp", "getTotalImagesCount"): "total specimen images: images of the ant (+ labels apart)",
}

# A number printed right before "Images" (<%= n %> Images) prints a count that is not from the helpers.
PRINTED_IMAGES = re.compile(r"%>\s*</?[a-z]*>?\s*Images\b")
HELPER_FILES = {"common/imagesDefinition.jsp"}

# Lists and tables with an Images number carry the definition (web/common/imagesDefinition.jsp, or the list's own line).
DEFINED = {
    "specimenReport.jsp": "/common/imagesDefinition.jsp",
    "specimenImages-body.jsp": "/common/imagesDefinition.jsp",
    "taxonChildImages.jsp": "/common/imagesDefinition.jsp",
    "taxonomicPageImages-body.jsp": "/common/imagesDefinition.jsp",
    "search/recentSearchResults-body.jsp": "/common/imagesDefinition.jsp",
    "util/statsPage-body.jsp": "/common/imagesDefinition.jsp",
    "taxonReportBody.jsp": "Specimen label images are not counted.",   # the list's own Images note (2026-10-28)
    "imageUploader/listImageUploads-body.jsp": "/common/imagesDefinition.jsp",
    "artists-body.jsp": "/common/imagesDefinition.jsp",
    "overviews-body.jsp": "/common/imagesDefinition.jsp",
}


def jsps():
    for d, _subdirs, files in os.walk(WEB):
        for f in files:
            if f.endswith(".jsp"):
                path = os.path.join(d, f)
                yield os.path.relpath(path, WEB).replace(os.sep, "/"), open(path, encoding="utf-8", errors="replace").read()


def test_every_image_count_getter_on_a_page_is_defined():
    found, unknown = set(), []
    for rel, text in jsps():
        for m in GETTERS.finditer(text):
            key = (rel, m.group(1))
            found.add(key)
            if key not in ALLOWED:
                line = text[:m.start()].count("\n") + 1
                unknown.append(f"web/{rel}:{line} {m.group(1)}()")
    assert not unknown, ("image counts on pages without a defined meaning (Images = images of the ant, label images "
                         "apart: ImageCounts; every file: label it 'Image files' and add it to ALLOWED):\n" + "\n".join(unknown))
    stale = sorted(set(ALLOWED) - found)
    assert not stale, f"ALLOWED lists getters no page uses any more: {stale}"


def test_no_number_is_printed_before_images_outside_the_helpers():
    bad = []
    for rel, text in jsps():
        if rel in HELPER_FILES:
            continue
        for m in PRINTED_IMAGES.finditer(text):
            line = text[:m.start()].count("\n") + 1
            bad.append(f"web/{rel}:{line}: {text[max(0, m.start() - 60):m.end()].strip()!r}")
    assert not bad, "a number printed before 'Images' (use ImageCounts.text or ImagesAnywhere.imagesColumn):\n" + "\n".join(bad)


def test_lists_with_images_carry_the_definition():
    pages = dict(jsps())
    missing = [f"web/{rel}" for rel, needle in DEFINED.items() if needle not in pages.get(rel, "")]
    assert not missing, f"no definition of Images under: {missing}"
    fragment = pages["common/imagesDefinition.jsp"]
    assert "org.calacademy.antweb.ImageCounts.DEFINITION" in fragment and "FILES_DEFINITION" in fragment
    src = open(os.path.join(ROOT, "src/org/calacademy/antweb/ImageCounts.java"), encoding="utf-8").read()
    assert '"Images: images of the ant (head, profile, dorsal and other views);"' in src
    assert "label images are counted separately." in src
    assert '"Image files: every uploaded image file, label images included."' in src


def test_specimen_counts_show_labels_apart():
    report = pages_of("specimenReport.jsp")
    assert "getImagesText()" in report, "specimen rows: images of the ant + labels (Specimen.getImagesText)"
    assert "ImageCounts.text(specimen.getImages())" in pages_of("specimenImages-body.jsp")
    assert "specimen.getImagesText()" in pages_of("specimen-body.jsp")
    assert "ImageCounts.text(searchResults.getResults().size() - recentLabels, recentLabels)" in pages_of("search/recentSearchResults-body.jsp")
    stats = pages_of("util/statsPage-body.jsp")
    assert "<th>Label images</th>" in stats and "stats[5]" in stats


def pages_of(rel):
    return open(os.path.join(WEB, rel), encoding="utf-8", errors="replace").read()


if __name__ == "__main__":
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            print("ok", name)
