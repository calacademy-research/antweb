package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

/**
 * Comparator of Taxon.sortTaxa for orderBy=genera: the row's genus count, larger first. Taxon.sortTaxa calls it
 * when a list is sorted by a column heading: the list rows (taxonReportBody.jsp: taxonomicPage.do, browse.do), the
 * images view (taxonChildImages.jsp: images.do), comparisons and field guides.
 */
public class SortTaxaByGenera implements Comparator<Taxon> 
{
    public int compare(Taxon a, Taxon b) 
    { 
      try {
        int aC = a.getTaxonSet().getGenusCount();
        int bC = b.getTaxonSet().getGenusCount();

        int compareInt = 0;
        if (aC > bC) compareInt = -1;
        if (aC == bC) compareInt = 0;
        if (aC < bC) compareInt = 1;

        //A.log("compare() a:" + aC + " b:" + bC + " compareInt:" + compareInt);
        return compareInt;
      } catch (NullPointerException e) {
        A.log("compare() NPE a:" + a + " b:" + b);
      }
      return 0;
    } 
} 
