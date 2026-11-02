package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

/**
 * Comparator of Taxon.sortTaxa for orderBy=images: the number in the Images cell (ImagesAnywhere.shownImages),
 * larger first, unknown last. Taxon.sortTaxa calls it when a list is sorted by a column heading: the list rows
 * (taxonReportBody.jsp: taxonomicPage.do, browse.do), the images view (taxonChildImages.jsp: images.do),
 * comparisons and field guides.
 */
public class SortTaxaByImages implements Comparator<Taxon> 
{
    public int compare(Taxon a, Taxon b) 
    { 
      // the number the Images cell shows (Brian Fisher 2026-10-28: the images of the name in AntWeb)
      TaxonSet aS = SortKeys.set(a), bS = SortKeys.set(b);
      return SortKeys.countDesc(aS == null ? null : ImagesAnywhere.shownImages(aS), bS == null ? null : ImagesAnywhere.shownImages(bS));
    } 
} 
