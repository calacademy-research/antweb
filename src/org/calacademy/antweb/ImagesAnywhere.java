package org.calacademy.antweb;

import java.util.*;
import java.util.function.Function;

import org.calacademy.antweb.util.Html;

/**
 * Images help identification wherever the specimen came from (Brian Fisher, 2026-10-27).
 *
 * A user picks a place (a country, adm1, bioregion, museum) to find out what they collected. Specimen counts stay per
 * place (what has been recorded there), but the images of a name are shown from every specimen in AntWeb:
 *
 *  - images.do of a species or subspecies reached with a place shows all of the name's imaged specimens; those of the
 *    place come first and are marked ("from Indonesia"), then the representative specimens (the picked defaults and
 *    the specimen shown for the name), then the others. images.do?...&placeOnly=true shows the place's specimens only.
 *  - the Images column of a place's list shows the images of the ant of the name in AntWeb, from any specimen:
 *    "153 Images" (Brian Fisher, 2026-10-28; from 10-27 to 10-28 it also said "(0 from Comoros)", which wrapped to two
 *    lines and showed "( from Comoros)" where the number script blanks a 0). The link goes to the name's images page,
 *    which says how many imaged specimens are from the place and shows them first.
 *
 * Pure helpers, no database: tested by test.org.calacademy.antweb.ImagesAnywhereTest.
 */
public final class ImagesAnywhere {

    private ImagesAnywhere() {
    }

    /** Request parameter that narrows a species images page to the specimens of the chosen place. */
    public static final String PLACE_ONLY = "placeOnly";

    /**
     * The children in the order the species images page shows them: the place's specimens first, then the
     * representative specimens, then the rest; each group keeps the order it had (by caste, Caste.sortSpecimenByCasteSubcaste).
     * Codes are compared without regard to case. placeCodes null: the list is returned unchanged.
     */
    public static <T> ArrayList<T> placeFirst(List<T> children, Function<T, String> codeOf, Set<String> placeCodes, java.util.Collection<String> representatives) {
        ArrayList<T> out = new ArrayList<>();
        if (children == null) return out;
        if (placeCodes == null) {
            out.addAll(children);
            return out;
        }
        Set<String> place = lower(placeCodes);
        Set<String> reps = lower(representatives);
        ArrayList<T> repList = new ArrayList<>();
        ArrayList<T> rest = new ArrayList<>();
        for (T child : children) {
            String code = codeOf.apply(child);
            String key = code == null ? null : code.toLowerCase();
            if (key != null && place.contains(key)) out.add(child);
            else if (key != null && reps.contains(key)) repList.add(child);
            else rest.add(child);
        }
        out.addAll(repList);
        out.addAll(rest);
        return out;
    }

    /** True when the species images page can be narrowed to the place: the overview selects specimens (country,
     *  adm1, bioregion, museum; Overview.getOverviewCriteria). Regions, subregions and projects select none, so their
     *  species images pages always showed every specimen. */
    public static boolean hasPlaceSpecimens(Overview overview) {
        return overview != null && !"".equals(overview.getOverviewCriteria());
    }

    /** placeFirst for the children of a species page (specimens; another kind of child keeps its place in the rest). */
    public static ArrayList<Taxon> placeFirstSpecimens(List<Taxon> children, Set<String> placeCodes, java.util.Collection<String> representatives) {
        return placeFirst(children, t -> (t instanceof Specimen) ? ((Specimen) t).getCode() : null, placeCodes, representatives);
    }

    /** True when the code is one of the place's specimens (case ignored). */
    public static boolean isFromPlace(String code, Set<String> placeCodes) {
        if (code == null || placeCodes == null) return false;
        // placeCodes is stored in lower case (Taxon.setPlaceCodes), so no copy per call: this runs once per specimen.
        return placeCodes.contains(code) || placeCodes.contains(code.toLowerCase());
    }

    public static Set<String> lowerCodes(java.util.Collection<String> codes) {
        return lower(codes);
    }

    private static Set<String> lower(java.util.Collection<String> codes) {
        Set<String> out = new HashSet<>();
        if (codes == null) return out;
        for (String c : codes) if (c != null) out.add(c.toLowerCase());
        return out;
    }

    /**
     * The Images cell of a place's list (taxonReportBody.jsp), as text with HTML: the images of the ant of the name in
     * AntWeb, from any specimen (labels never count; Brian Fisher 2026-10-27, nightly set 4, CountDb.imageCountCrawl).
     *   total      images of the name in AntWeb (the allantwebants list row's Images; -1 when not known),
     *   place      the place's own list row Images, used only when the AntWeb-wide number is not known or lower
     *              (the two rows counted on different nights),
     *   fromPhrase "from Comoros", "in CASC": no longer shown (Brian Fisher 2026-10-28: one number, the cell never
     *              wraps; the name's images page says which images come from the place). Kept for the callers.
     * "N Images" with N in class "numbers", or "No Images" when N is 0.
     */
    public static String imagesColumn(int total, int place, String fromPhrase) {
        int n = Math.max(total, place);
        if (n <= 0) return "No Images";
        return "<span class='numbers'>" + n + "</span> Images";
    }

    /** The number the Images cell shows (imagesColumn): the AntWeb-wide images of the name, or the place's when that
     *  is not known or lower. Used to sort the list by Images (SortTaxaByImages) in the order the cells show. */
    public static int shownImages(TaxonSet ts) {
        if (ts == null) return 0;
        return Math.max(ts.getGlobalImageCount(), ts.getImageCount());
    }

    /**
     * The Images cell of a list row from its TaxonSet: imagesColumn(the allantwebants row's images, the place's row's
     * images, fromPhrase). Never throws (a list row is never lost over its Images cell): on any error "No Images".
     */
    public static String imagesColumn(TaxonSet ts, String fromPhrase) {
        if (ts == null) return "No Images";
        try {
            return imagesColumn(ts.getGlobalImageCount(), ts.getImageCount(), fromPhrase);
        } catch (RuntimeException e) {
            return "No Images";
        }
    }

    /**
     * "N Images" of a name on images.do and the taxonomicPage images view (docs/numbers.md row 56): the same as the
     * Images column of the place's list (imagesColumn: ant images of the name in AntWeb). When the list says No
     * Images but the page shows images of the name (the allantwebants row not read, or counted before the latest
     * images), the images of the ant of the specimen shown.
     */
    public static String childImages(Taxon child, Overview overview) {
        TaxonSet ts = child.getTaxonSet();
        String s = imagesColumn(ts, fromPhrase(overview));
        if (s.startsWith("No ")) {
            int shown = antImagesShown(child.getImages());
            if (shown > 0) return "<span class='numbers'>" + shown + "</span> Images";
        }
        return s;
    }

    /** The images of the ant among the images of a specimen shown (shot type not l, the label). */
    public static int antImagesShown(java.util.Map<String, SpecimenImage> images) {
        return ImageCounts.of(images)[0];
    }

    /** "from Indonesia" for a place (geolocale or bioregion), "in CASC" for a museum; null for a project (a project's
     *  list counts the images of its names' specimens from anywhere). */
    public static String fromPhrase(Overview overview) {
        if (overview == null || overview instanceof Project) return null;
        if (overview instanceof Museum) return "in " + ((Museum) overview).getCode();
        String name = overview.getDisplayName();
        if (name == null) name = overview.getName();
        return "from " + name;
    }
}
