package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

public class SortTaxaBySource implements Comparator<Taxon> 
{ 
    public int compare(Taxon a, Taxon b) 
    { 
      try {
        //A.log("compare() a:" + a + " a.getTaxonSet():" + a.getTaxonSet() + " a.source:" + a.getTaxonSet().getSource());
        //A.log("compare() b:" + b + " b.getTaxonSet():" + b.getTaxonSet() + " b.source:" + b.getTaxonSet().getSource());
        String aSource = Source.getSourceDisplay(a.getTaxonSet().getSource());
        String bSource = Source.getSourceDisplay(b.getTaxonSet().getSource());
        
//        if (a == null || a.getSource() == null) return 1;
//        if (b == null || b.getSource() == null) return -1;

        int compareInt = aSource.compareTo(bSource);

        //A.log("compare() aSource:" + aSource + " bSource:" + bSource + " compareInt:" + compareInt);
        return compareInt;
      } catch (NullPointerException e) {
        A.log("compare() NPE a:" + a + " b:" + b);
      }
      return 0;
    } 
} 
