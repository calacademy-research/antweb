package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

public class SortTaxaByMedium implements Comparator<Taxon> 
{ 
    public int compare(Taxon a, Taxon b) 
    { 
      try {
        //A.log("compare() a:" + a + " a.getTaxonSet():" + a.getTaxonSet() + " a.source:" + a.getTaxonSet().getSource());
        //A.log("compare() b:" + b + " b.getTaxonSet():" + b.getTaxonSet() + " b.source:" + b.getTaxonSet().getSource());
        Specimen a1 = (Specimen) a;
        Specimen b1 = (Specimen) b;
        String aMedium = a1.getMedium();
        String bMedium = b1.getMedium();
        
//        if (a == null || a.getSource() == null) return 1;
//        if (b == null || b.getSource() == null) return -1;

        int compareInt = aMedium.compareTo(bMedium);

        //A.log("compare() aSource:" + aSource + " bSource:" + bSource + " compareInt:" + compareInt);
        return compareInt;
      } catch (NullPointerException e) {
        A.log("compare() NPE a:" + a + " b:" + b);
      }
      return 0;
    } 
} 
