package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

/**
 * Comparator of Taxon.sortTaxa for orderBy=species: the row's species count, larger first, unknown last.
 * Taxon.sortTaxa calls it when a list is sorted by a column heading: the list rows (taxonReportBody.jsp:
 * taxonomicPage.do, browse.do), the images view (taxonChildImages.jsp: images.do), comparisons and field guides.
 */
public class SortTaxaBySpecies implements Comparator<Taxon> 
{
    public int compare(Taxon a, Taxon b) 
    { 
      TaxonSet aS = SortKeys.set(a), bS = SortKeys.set(b);
      return SortKeys.countDesc(aS == null ? null : aS.getSpeciesCount(), bS == null ? null : bS.getSpeciesCount());
    } 
} 
