package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

/**
 * Comparator of Taxon.sortTaxa for orderBy=authorDate: author and date text in order, names without one last.
 * Taxon.sortTaxa calls it when a list is sorted by a column heading: the list rows (taxonReportBody.jsp:
 * taxonomicPage.do, browse.do), the images view (taxonChildImages.jsp: images.do), comparisons and field guides.
 */
public class SortTaxaByAuthorDate implements Comparator<Taxon> 
{
    public int compare(Taxon a, Taxon b) 
    { 
      try {
        String aAuthorDate = a.getAuthorDate();
        String bAuthorDate = b.getAuthorDate();
        
        // Put the blank values at the end of the list.
        if (Utility.isBlank(aAuthorDate) && !Utility.isBlank(bAuthorDate)) return 1;
        if (Utility.isBlank(aAuthorDate) && Utility.isBlank(bAuthorDate)) return 0;
        if (!Utility.isBlank(aAuthorDate) && Utility.isBlank(bAuthorDate)) return -1;
                
        int compareInt = aAuthorDate.compareTo(bAuthorDate);

        //A.log("compare() a.AuthorDate:" + aAuthorDate + " bAuthorDate:" + bAuthorDate + " compareInt:" + compareInt);
        return compareInt;
      } catch (NullPointerException e) {
        A.log("compare() NPE a:" + a + " b:" + b);
      }
      return 0;
    } 
} 
