package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

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
