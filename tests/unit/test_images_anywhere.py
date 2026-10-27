"""Source checks of images from anywhere (Brian Fisher, 2026-10-27) that need no server or database.

1. images.do of a species reached with a place fetches the name's specimens from anywhere (BrowseAction sets global
   unless placeOnly=true), keeps the codes of the place's specimens (Species.setChildren), shows them first and marks
   them (taxonChildImages.jsp, taxonChildImageSet.jsp); the old "See global set" link is gone.
3. The Images column of a place's list shows the images of the name in AntWeb and the place's, images of the ant only
   (labels never count; nightly set 4 counts the list rows that way, the count check recounts them so) (taxonReportBody.jsp,
   ImagesAnywhere.imagesColumn; the total is the allantwebants row's image_count, TaxonSet.globalImageCountColumn).
5. The species page shows which castes are imaged (BrowseAction, ImagedCastes, taxonPage-body.jsp).

The logic is tested by test/org/calacademy/antweb/ImagesAnywhereTest.java, the pages on staging by
tests/e2e/test_images_anywhere.py.

Run: python3 tests/unit/test_images_anywhere.py   (also in .github/workflows/tests.yaml)
"""
import os

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))


def read(path):
    with open(os.path.join(ROOT, path), encoding="utf-8", newline="") as f:
        return f.read()


def test_species_images_page_fetches_from_anywhere_unless_place_only():
    src = read("src/org/calacademy/antweb/BrowseAction.java")
    assert '"images".equals(cacheType) && taxon.isSpeciesOrSubspecies() && ImagesAnywhere.hasPlaceSpecimens(overview)' in src
    assert "if (imagesFromAnywhere) global = !placeOnly;" in src
    i = src.index("if (imagesFromAnywhere) global = !placeOnly;")
    assert src.index("taxon.setChildren(connection, overview, statusSet, getChildImages, getChildMaps, caste, global, subgenus)", i) > i
    assert 'request.setAttribute("imagesPlaceCodes", taxon.getPlaceCodes())' in src


def test_species_keeps_the_place_codes_and_puts_them_first():
    src = read("src/org/calacademy/antweb/Species.java")
    assert "if (global && ImagesAnywhere.hasPlaceSpecimens(overview))" in src
    assert "getPlaceCodes(connection, overview.getOverviewCriteria(), statusSet, caste)" in src
    assert "ImagesAnywhere.placeFirst(theseChildren, Specimen::getCode, this.placeCodes" in src


def test_images_page_orders_marks_and_narrows():
    jsp = read("web/taxonChildImages.jsp")
    assert "See global set" not in jsp, "the old global link is still there"
    assert "ImagesAnywhere.placeFirstSpecimens(theChildren, imagesPlaceCodes, representatives)" in jsp
    sort = jsp.index("Caste.sortSpecimenByCasteSubcaste(theChildren)")
    assert jsp.index("placeFirstSpecimens", sort) > sort, "the place's specimens must be put first after the caste sort"
    assert "taxon.getDefaultSpecimen(rc)" in jsp, "the picked representatives come after the place's specimens"
    assert "are imaged yet.</b>" in jsp and "No specimens <%= Html.esc(imagesFromNote) %>" in jsp
    assert 'class="images_place_only"' in jsp and "ImagesAnywhere.PLACE_ONLY + \"=true\"" in jsp
    cell = read("web/taxonChildImageSet.jsp")
    assert cell.count("<%= fromPlaceMark %>") == 2, "both views mark the place's specimens"
    assert 'class=\\"from_place\\"' in cell


def test_list_images_column_shows_both_numbers():
    jsp = read("web/taxonReportBody.jsp")
    assert "ImagesAnywhere.imagesColumn(childTaxonSet.getGlobalImageCount(), childTaxonSet.getImageCount(), imagesFromPhrase)" in jsp
    assert "childTaxonSet.getImageCountStr()" not in jsp, "the column still shows the place's number only"
    # the total is the allantwebants row's image_count, read with the place's row (not AllAntwebMgr's copy, which
    # is loaded once when the server starts)
    assert "g.project_name = '\" + Project.ALLANTWEBANTS" in read("src/org/calacademy/antweb/TaxonSet.java")
    for f in ("geolocale/OverviewTaxon.java", "geolocale/GeolocaleTaxon.java", "geolocale/BioregionTaxon.java"):
        src = read("src/org/calacademy/antweb/" + f)
        assert "+ globalImageCountColumn(taxonName)" in src and "readGlobalImageCount(rset);" in src, f


def test_list_images_are_ant_images_only():
    """Brian Fisher 2026-10-27: every Images column counts images of the ant; label images never count."""
    for f in ("Project.java", "Museum.java", "geolocale/Bioregion.java", "geolocale/Geolocale.java"):
        src = read("src/org/calacademy/antweb/" + f)
        q = src[src.index("public String getTaxonImageCountQuery()"):]
        q = q[:q.index("return query;")]
        assert "count(i.id) imageSum" in q and "StatisticsDb.ANT_IMAGES_OF_SPECIMEN" in q, f
        assert "sum(s.image_count)" not in q, f + " still sums every image file"
    stats = read("src/org/calacademy/antweb/home/StatisticsDb.java")
    assert 'ANT_IMAGES_OF_SPECIMEN = " left join image i on i.image_of_id = s.code and " + ANT_IMAGE' in stats
    check = read("src/org/calacademy/antweb/util/CountCheck.java")
    rows = check[check.index("int checkRowImages("):]
    rows = rows[:rows.index("return rows;")]
    assert "count(i.id)" in rows and "ANT_IMAGES_OF_SPECIMEN" in rows and "pending = ANT_IMAGES_PENDING" in rows
    for f in ("CountCheckJob.java", "CountCheckAction.java"):
        assert "countCheck.antImagesRuleCounted = CountCheck.antImagesRuleCounted(log);" in read("src/org/calacademy/antweb/util/" + f), f
    assert "StatisticsDb.ANT_IMAGES_RULE_LOG" in read("src/org/calacademy/antweb/util/UtilDataAction.java")
    jsp = read("web/taxonReportBody.jsp")
    assert "label images are not counted." in jsp and 'id="images-definition"' in jsp
    cell = read("web/taxonChildImageSet.jsp")
    assert cell.count("ImagesAnywhere.childImages(thisChild, overview)") == 2, "both views show the list's numbers"


def test_species_page_shows_imaged_castes():
    src = read("src/org/calacademy/antweb/BrowseAction.java")
    assert 'request.setAttribute("imagedCastes", ImagedCastes.toHtml(ImagedCastes.count(connection, taxon.getTaxonName())' in src
    assert '"description".equals(cacheType) && taxon.isSpeciesOrSubspecies()' in src
    assert 'request.getAttribute("imagedCastes")' in read("web/taxonPage-body.jsp")
    castes = read("src/org/calacademy/antweb/ImagedCastes.java")
    assert "StatisticsDb.ANT_IMAGE" in castes, "label images must not count"


if __name__ == "__main__":
    for name, fn in sorted(globals().items()):
        if name.startswith("test_") and callable(fn):
            fn()
            print("ok", name)
