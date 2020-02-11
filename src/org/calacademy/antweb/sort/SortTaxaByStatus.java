package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

public class SortTaxaByStatus implements Comparator<Taxon> 
{ 
    public int compare(Taxon a, Taxon b) 
    { 
      try {
        String aS = a.getStatus();
        String bS = b.getStatus();
        
        int compareInt = bS.compareTo(aS);

        //A.log("compare() a:" + a.getStatus() + " b:" + b.getStatus() + " aS:" + aS + " bS:" + bS + " compareInt:" + compareInt);
        return compareInt;
      } catch (NullPointerException e) {
        A.log("compare() NPE a:" + a + " b:" + b);
      }
      return 0;
    } 
} 
