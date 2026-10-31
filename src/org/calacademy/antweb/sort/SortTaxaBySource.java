package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

/**
 * Comparator of Taxon.sortTaxa for orderBy=source: the source of the row as shown (Source.getSourceDisplay), text
 * in order, blank last. Taxon.sortTaxa calls it when a list is sorted by a column heading: the list rows
 * (taxonReportBody.jsp: taxonomicPage.do, browse.do), the images view (taxonChildImages.jsp: images.do),
 * comparisons and field guides.
 */
public class SortTaxaBySource implements Comparator<Taxon> 
{ 
    public int compare(Taxon a, Taxon b) 
    { 
      TaxonSet aS = SortKeys.set(a), bS = SortKeys.set(b);
      return SortKeys.textAsc(aS == null ? null : Source.getSourceDisplay(aS.getSource()), bS == null ? null : Source.getSourceDisplay(bS.getSource()));
    } 
} 
