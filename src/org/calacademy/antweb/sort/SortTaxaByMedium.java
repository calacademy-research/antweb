package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

/**
 * Comparator of Taxon.sortTaxa for orderBy=medium: the medium of specimen rows, text in order, blank last
 * (SortKeys). Taxon.sortTaxa calls it when a list is sorted by a column heading: the list rows
 * (taxonReportBody.jsp: taxonomicPage.do, browse.do), the images view (taxonChildImages.jsp: images.do),
 * comparisons and field guides.
 */
public class SortTaxaByMedium implements Comparator<Taxon> 
{ 
    public int compare(Taxon a, Taxon b) 
    { 
      Specimen a1 = SortKeys.specimen(a), b1 = SortKeys.specimen(b);
      return SortKeys.textAsc(a1 == null ? null : a1.getMedium(), b1 == null ? null : b1.getMedium());
    } 
} 
