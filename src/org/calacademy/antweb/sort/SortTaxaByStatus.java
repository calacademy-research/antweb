package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

/**
 * Comparator of Taxon.sortTaxa for orderBy=status: the status text in reverse order, so valid names come first.
 * Taxon.sortTaxa calls it when a list is sorted by a column heading: the list rows (taxonReportBody.jsp:
 * taxonomicPage.do, browse.do), the images view (taxonChildImages.jsp: images.do), comparisons and field guides.
 */
public class SortTaxaByStatus implements Comparator<Taxon> 
{ 
    public int compare(Taxon a, Taxon b) 
    { 
      return SortKeys.textDesc(a == null ? null : a.getStatus(), b == null ? null : b.getStatus());   // valid first, as before
    } 
} 
