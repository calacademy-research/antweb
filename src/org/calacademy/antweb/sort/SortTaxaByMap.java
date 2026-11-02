package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

/**
 * Comparator of Taxon.sortTaxa for orderBy=map: the same key as SortTaxaBySpecimens, the row's specimen count,
 * larger first. Taxon.sortTaxa calls it when a list is sorted by a column heading: the list rows
 * (taxonReportBody.jsp: taxonomicPage.do, browse.do), the images view (taxonChildImages.jsp: images.do),
 * comparisons and field guides.
 */
public class SortTaxaByMap implements Comparator<Taxon> 
{
    public int compare(Taxon a, Taxon b) 
    { 
      // Same key as SortTaxaBySpecimens (the specimens of the row).
      TaxonSet aS = SortKeys.set(a), bS = SortKeys.set(b);
      return SortKeys.countDesc(aS == null ? null : aS.getSpecimenCount(), bS == null ? null : bS.getSpecimenCount());
    } 
} 
