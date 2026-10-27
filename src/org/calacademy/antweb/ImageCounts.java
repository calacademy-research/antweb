package org.calacademy.antweb;

import java.util.Map;

/**
 * One meaning of "Images" everywhere on AntWeb (Brian Fisher, 2026-10-27: "We need to carry our definitions to every
 * place: consistency will keep us sane"):
 *
 *   Images       images of the ant (head, profile, dorsal and other views);
 *   Label images images of a specimen's labels, counted separately: "5 Images + label", "5 Images + 2 labels";
 *   Image files  every uploaded file, label images included (upload lists and reports, photographers' totals).
 *
 * docs/numbers.md section 1 ("Definitions"); the line under every list or table with an Images number is
 * web/common/imagesDefinition.jsp. Tested by ImagesAnywhereTest (junit, no database) and
 * tests/unit/test_image_counts_defined.py (which getters the pages may print).
 */
public final class ImageCounts {

    private ImageCounts() {
    }

    /** Shot type of a label image. */
    public static final String LABEL = "l";

    public static final String DEFINITION = "Images: images of the ant (head, profile, dorsal and other views);"
        + " label images are counted separately.";
    public static final String FILES_DEFINITION = "Image files: every uploaded image file, label images included.";

    public static boolean isLabel(String shot) {
        return shot != null && LABEL.equals(shot.trim().toLowerCase());
    }

    /**
     * A specimen's (or a set of images') numbers: "No Images", "Label only" (label images only), "N Images",
     * "N Images + label", "N Images + 2 labels". N in a span of class "numbers" (the pages' tests read it).
     */
    public static String text(int antImages, int labelImages) {
        if (antImages <= 0 && labelImages <= 0) return "No Images";
        if (antImages <= 0) return "Label only" + (labelImages > 1 ? " (" + labelImages + " labels)" : "");
        return "<span class='numbers'>" + antImages + "</span> Images" + labels(labelImages);
    }

    /** " + label", " + N labels" or "". */
    public static String labels(int labelImages) {
        if (labelImages <= 0) return "";
        return labelImages == 1 ? " + label" : " + " + labelImages + " labels";
    }

    /** "N image files" (files uploaded, labels included). */
    public static String filesText(int files) {
        return "<span class='numbers'>" + files + "</span> image file" + (files == 1 ? "" : "s");
    }

    /** {images of the ant, label images} of a specimen's images (Taxon.getImages()). */
    public static int[] of(Map<String, SpecimenImage> images) {
        int[] out = new int[2];
        if (images == null) return out;
        for (SpecimenImage i : images.values()) {
            if (i == null || i.getShot() == null || "null".equals(i.getShot()) || i.getNumber() <= 0) continue;
            if (isLabel(i.getShot())) out[1]++; else out[0]++;
        }
        return out;
    }

    /** text() of a specimen's images. */
    public static String text(Map<String, SpecimenImage> images) {
        int[] n = of(images);
        return text(n[0], n[1]);
    }
}
