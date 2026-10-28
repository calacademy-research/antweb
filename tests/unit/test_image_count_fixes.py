"""Checks of the image-count fixes of 2026-10-27 (docs/numbers.md B46, B47) that need no server or database.

1. (B46) images.do with the All view ("Browse Images By: All") shows a name's Images of this place's list, as the default
   view does: taxonChildImageSet.jsp printed getHasImagesCount(), which is counted for specimens only, so every name
   showed "0 Images". A specimen keeps its own count.
2. (B47) Recent images: one row per image. RecentImageSearch grouped by name, shot type, shot number, upload time and
   group, so the images of two specimens of one name with the same shot, number and upload second were one row and
   the page's "N Images" was too low; an image of two groups was two rows.

The pages are checked against the API on staging by tests/e2e/test_numbers_images.py.

Run: python3 tests/unit/test_image_count_fixes.py   (also in .github/workflows/tests.yaml)
"""
import os
import re

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8", newline="") as f:
        return f.read()


def test_all_view_shows_the_list_images_of_a_name():
    jsp = read("web/taxonChildImageSet.jsp")
    block = jsp[jsp.index('<div class="ratio_has_images">'):]
    block = block[:block.index("</div>")]
    assert "thisChild.getHasImages() && !isSpecimen" in block, "the All view has no branch for names"
    names = block[block.index("!isSpecimen"):block.index("else if (thisChild.getHasImages())")]
    # since 2026-10-27 (Brian Fisher) the Images column's numbers: ant images in AntWeb and of this place's list
    assert "ImagesAnywhere.childImages(thisChild, overview)" in names, "names do not show their Images of this place's list"
    assert "getHasImagesCount" not in names, "names show getHasImagesCount (counted for specimens only)"
    # the default view shows the same number
    assert "isSpecimen ? org.calacademy.antweb.ImageCounts.text(thisChild.getImages()) : org.calacademy.antweb.ImagesAnywhere.childImages(thisChild, overview)" in jsp


def test_recent_images_one_row_per_image():
    src = read("src/org/calacademy/antweb/search/RecentImageSearch.java")
    group = re.search(r'"\s*group by ([^"]*)"', src)
    assert group, "RecentImageSearch has no group by"
    assert group.group(1).strip() == "image.id", "recent images grouped by " + group.group(1)


def main():
    test_all_view_shows_the_list_images_of_a_name()
    test_recent_images_one_row_per_image()


if __name__ == "__main__":
    main()
