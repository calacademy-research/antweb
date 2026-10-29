"""Source checks of images from anywhere (Brian Fisher, 2026-10-27) that need no server or database.

1. images.do of a species reached with a place fetches the name's specimens from anywhere (BrowseAction sets global
   unless placeOnly=true), keeps the codes of the place's specimens (Species.setChildren), shows them first and marks
   them (taxonChildImages.jsp, taxonChildImageSet.jsp); the old "See global set" link is gone.
3. The Images column of a place's list shows one number, the images of the name in AntWeb (Brian Fisher 2026-10-28;
   from 10-27 to 10-28 also the place's in brackets, which wrapped and showed "( from Comoros)"), images of the ant only
   (labels never count; nightly set 4 counts the list rows that way, the count check recounts them so) (taxonReportBody.jsp,
   ImagesAnywhere.imagesColumn; the total is the allantwebants row's image_count of every rank, TaxonSet.globalImageCountColumn,
   which nightly set 4 counts with the live projects since 2026-10-27, ProjTaxonCountDb.imageCrawlProjects).
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


def test_list_images_column_shows_the_antweb_number_only():
    jsp = read("web/taxonReportBody.jsp")
    assert "ImagesAnywhere.imagesColumn(childTaxonSet, imagesFromPhrase)" in jsp
    assert "childTaxonSet.getImageCountStr()" not in jsp, "the column shows the place's number only"
    # Brian Fisher 2026-10-28: one number, never "(0 from Comoros)" (it wrapped; the number script blanked the 0)
    src = read("src/org/calacademy/antweb/ImagesAnywhere.java")
    body = src[src.index("public static String imagesColumn(int total, int place, String fromPhrase) {"):]
    body = body[:body.index("\n    }\n")]
    assert "images_total" not in body and "fromPhrase" not in body.split("{", 1)[1] and "(" not in body.split('"')[1::2][-1], body
    assert "Math.max(total, place)" in body
    # the title and definition say what the number counts and where the place's own images are
    assert '"Images of the ant of the name in AntWeb, from any specimen (labels are not counted)."' in jsp
    # the Images note under the list (Brian Fisher 2026-10-28, list notes)
    assert '"Images: the images of the ant (head, profile, dorsal and other views) of that name in all of"' in jsp
    assert '" AntWeb, from specimens collected anywhere. Specimen label images are not counted."' in jsp
    assert '" Click the number to see the images; those " + imagesFromPhrase + " come first."' in jsp
    assert "in brackets" not in jsp and "The first number" not in jsp and "parenthes" not in jsp.lower()
    # sorting by Images follows the number shown
    assert "ImagesAnywhere.shownImages(a.getTaxonSet())" in read("src/org/calacademy/antweb/sort/SortTaxaByImages.java")
    ts = read("src/org/calacademy/antweb/TaxonSet.java")
    # every rank reads the allantwebants row with the place's row (one key lookup). PR #291 run 2 counted species rows
    # live from the image table, one count per row, and list, taxon and browse pages came back cut off on staging.
    assert "g.project_name = '\" + Project.ALLANTWEBANTS" in ts
    assert "join image" not in ts[ts.index("public String globalImageCountColumn"):ts.index("protected void readGlobalImageCount")]
    # reading the column never throws (a query without it, or a NULL, leaves -1)
    assert "protected void readGlobalImageCount(ResultSet rset) {" in ts and "catch (SQLException | RuntimeException e)" in ts
    # set 4 counts the allantwebants rows (they were set to 0 with the project rows and never counted again)
    crawl = read("src/org/calacademy/antweb/home/ProjTaxonCountDb.java")
    assert "imageCrawlProjects(ProjectMgr.getLiveProjects(), allAntweb)" in crawl
    assert "ALLANTWEB_IMAGES_LOG" in crawl
    for f in ("geolocale/OverviewTaxon.java", "geolocale/GeolocaleTaxon.java", "geolocale/BioregionTaxon.java",
              "ProjTaxon.java"):
        src = read("src/org/calacademy/antweb/" + f)
        assert "+ globalImageCountColumn(taxonName)" in src and "readGlobalImageCount(rset);" in src, f


def test_list_images_are_ant_images_only():
    """Brian Fisher 2026-10-27: every Images column counts images of the ant; label images never count."""
    for f in ("Project.java", "Museum.java", "geolocale/Bioregion.java", "geolocale/Geolocale.java"):
        src = read("src/org/calacademy/antweb/" + f)
        q = src[src.index("public String getTaxonImageCountQuery()"):]
        q = q[:q.index("return query;")]
        # 2026-10-28: museums, bioregions and curated projects count a synonym's images under the valid name, with the
        # query written once in PlaceTotals.imagesByListNameSql (checked below)
        assert ("count(i.id) imageSum" in q and "StatisticsDb.ANT_IMAGES_OF_SPECIMEN" in q) or "PlaceTotals.imagesByListNameSql(" in q, f
        assert "sum(s.image_count)" not in q, f + " still sums every image file"
    totals = read("src/org/calacademy/antweb/home/PlaceTotals.java")
    q = totals[totals.index("public static String imagesByListNameSql("):]
    q = q[:q.index("    }")]
    assert "count(i.id) imageSum" in q and "StatisticsDb.ANT_IMAGES_OF_SPECIMEN" in q
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
    assert "Specimen label images are not counted." in jsp and 'id="images-definition"' in jsp
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
