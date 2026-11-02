package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

/**
 * Comparator of Taxon.sortTaxa for orderBy=type: names with type specimens first. Taxon.sortTaxa calls it when a
 * list is sorted by a column heading: the list rows (taxonReportBody.jsp: taxonomicPage.do, browse.do), the images
 * view (taxonChildImages.jsp: images.do), comparisons and field guides.
 */
public class SortTaxaByIsType implements Comparator<Taxon> 
{ 
    public int compare(Taxon a, Taxon b) 
    { 
      boolean aT = a != null && a.getIsType();
      boolean bT = b != null && b.getIsType();
      return Boolean.compare(bT, aT);   // types first
    } 
} 
