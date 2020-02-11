package org.calacademy.antweb.sort;

import java.util.*;
import org.calacademy.antweb.*;
import org.calacademy.antweb.util.*;

public class SortTaxaBySpecimenNotes implements Comparator<Taxon> 
{ 
    public int compare(Taxon a, Taxon b) 
    { 
      try {
        //A.log("compare() a:" + a + " a.getTaxonSet():" + a.getTaxonSet() + " a.source:" + a.getTaxonSet().getSource());
        //A.log("compare() b:" + b + " b.getTaxonSet():" + b.getTaxonSet() + " b.source:" + b.getTaxonSet().getSource());
        Specimen a1 = (Specimen) a;
        Specimen b1 = (Specimen) b;
        String aSpecimenNotes = a1.getSpecimenNotes();
        String bSpecimenNotes = b1.getSpecimenNotes();
        
//        if (a == null || a.getSource() == null) return 1;
//        if (b == null || b.getSource() == null) return -1;

        int compareInt = aSpecimenNotes.compareTo(bSpecimenNotes);

        //A.log("compare() aSource:" + aSource + " bSource:" + bSource + " compareInt:" + compareInt);
        return compareInt;
      } catch (NullPointerException e) {
        A.log("compare() NPE a:" + a + " b:" + b);
      }
      return 0;
    } 
} 
